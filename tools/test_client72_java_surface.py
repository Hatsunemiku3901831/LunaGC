import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.client72_java_surface import compile_scanner,scan_sources,method_id,direct_packet_arguments,source_graph
from tools.review_client72_login_surface import check_review,validate_summary


class JavaSurfaceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).resolve().parents[1]
        homes=sorted((cls.root/'local/runtime').glob('jdk-17*'))
        if len(homes)!=1:raise RuntimeError('Research tests require the reviewed portable JDK17')
        cls.java_home=homes[0]
        cls.temp=tempfile.TemporaryDirectory(prefix='java-surface-tests-',dir=cls.root/'local/migration72/analysis')
        cls.work=Path(cls.temp.name);cls.classes=cls.work/'classes'
        compile_scanner(cls.java_home,cls.classes,cls.root/'tools/Client72JavaSurface.java')

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def parse(self,text):
        repo=self.work/self._testMethodName;repo.mkdir()
        (repo/'Fixture.java').write_text(text,'utf8')
        return scan_sources(repo,self.java_home,self.classes,['Fixture.java'])

    def test_real_source_summary_preserves_limits(self):
        r=json.loads((self.root/'protocol/cn-7.2/login-source-observations.json').read_text('utf8'))
        self.assertEqual(validate_summary(r),[])

    def test_comments_strings_utf16_offsets_and_record_switch(self):
        text='''package demo;
// 🚀 new PacketComment();
record Data(int value) {}
class Fixture {
 String ignored = "new PacketString()";
 void entry() { send(new PacketReal()); }
 Object pick(int n) { return switch(n) { case 1 -> new First(); default -> new Second(); }; }
}
'''
        rows=self.parse(text);entry=next(r for r in rows if r.get('name')=='entry')
        self.assertEqual([v['selector'] for v in entry['calls'] if v['kind']=='construct'],['PacketReal'])
        raw=(self.work/self._testMethodName/'Fixture.java').read_bytes().decode('utf8')
        start=raw.index('void entry')
        self.assertEqual(entry['startUTF16'],len(raw[:start].encode('utf-16-le'))//2)
        self.assertEqual(entry['sourceSHA256'],hashlib.sha256(b'void entry() { send(new PacketReal()); }').hexdigest())
        self.assertTrue(any(v['kind']=='class' and v['owner']=='demo.Data' for v in rows))
        pick=next(r for r in rows if r.get('name')=='pick')
        self.assertEqual([v['selector'] for v in pick['calls'] if v['kind']=='construct'],['First','Second'])

    def test_lambda_anonymous_and_field_scopes_are_separate(self):
        rows=self.parse('''package demo; class Fixture {
 Object initial = new PacketField();
 void entry() {
   queue(() -> send(new PacketLambda()));
   Object task = new Object() { void run() { send(new PacketAnonymous()); } };
 }
}''')
        entry=next(r for r in rows if r.get('name')=='entry')
        packets=direct_packet_arguments(entry)
        self.assertEqual([(v['constructedType'],v['lambdaDepth']) for v in packets],[('PacketLambda',1)])
        other=next(r for r in rows if r.get('name')=='run')
        self.assertIn('$anonymous@',other['owner'])
        self.assertEqual(direct_packet_arguments(other)[0]['constructedType'],'PacketAnonymous')
        field=next(r for r in rows if r.get('name')=='<field:initial>')
        self.assertEqual(field['calls'][0]['selector'],'PacketField')

    def test_indirect_arguments_do_not_become_direct_sends(self):
        rows=self.parse('''class Fixture { void entry() {
 Object packet = new PacketLocal(); send(packet);
 send(wrap(new PacketNested())); send(new PacketDirect());
}}''')
        entry=next(r for r in rows if r.get('name')=='entry')
        self.assertEqual([v['constructedType'] for v in direct_packet_arguments(entry)],['PacketDirect'])
        graph=source_graph(rows,['Fixture#entry()'],[])
        self.assertEqual([v['constructedType'] for v in graph['methods'][0]['packetConstructions']],
                         ['PacketLocal','PacketNested','PacketDirect'])

    def test_enum_constants_do_not_invent_source_constructor_spans(self):
        rows=self.parse('enum State { EMPTY, VALUE(value()); State(){} State(int x){} static int value(){return 1;} }')
        fields=[v for v in rows if v['kind']=='method' and v['name'].startswith('<field:')]
        self.assertEqual(len(fields),2)
        self.assertTrue(all(v['endUTF16']>=v['startUTF16'] for v in fields))
        self.assertEqual([c['selector'] for v in fields for c in v['calls']],['State','value'])
        self.assertTrue(all(c['endUTF16']>=c['startUTF16'] for v in fields for c in v['calls']))

    def test_overload_ambiguity_is_not_guessed(self):
        rows=self.parse('class Fixture { void entry(){ run(1); } void run(int x){} void run(String x){} }')
        graph=source_graph(rows,['Fixture#entry()'],[])
        self.assertEqual(graph['edges'],[])
        self.assertEqual(graph['unresolved'][0]['reason'],'ambiguous')
        self.assertEqual(graph['unresolved'][0]['candidates'],['Fixture#run(String)','Fixture#run(int)'])

    def test_reviewed_edge_requires_actual_occurrence(self):
        rows=self.parse('class Fixture { void entry(){ run(1); } void run(int x){} void run(String x){} }')
        edge={'from':'Fixture#entry()','to':'Fixture#run(int)','kind':'invoke','selector':'run',
              'argumentCount':1,'occurrences':1}
        graph=source_graph(rows,['Fixture#entry()'],[edge])
        self.assertEqual(graph['edges'][0]['basis'],'reviewed-source-edge')
        edge['occurrences']=2
        with self.assertRaisesRegex(ValueError,'occurrence drift'):source_graph(rows,['Fixture#entry()'],[edge])

    def test_source_drift_and_parser_errors_are_rejected(self):
        repo=self.work/'drift';repo.mkdir();path=repo/'Fixture.java';path.write_text('class Fixture {}','utf8')
        review=dict(schemaVersion=1,closureComplete=False,targetProtocolReady=False,openBoundaries=['open'],
                    sourceSHA256={'Fixture.java':hashlib.sha256(b'class Fixture {}').hexdigest()})
        check_review(repo,review);path.write_text('class Changed {}','utf8')
        with self.assertRaisesRegex(ValueError,'Source changed'):check_review(repo,review)
        import subprocess
        with self.assertRaises(subprocess.CalledProcessError):self.parse('class Fixture { void invalid( }')

    def test_summary_cannot_claim_complete_or_promote_candidate_edges(self):
        original=json.loads((self.root/'protocol/cn-7.2/login-source-observations.json').read_text('utf8'))
        for key,value in [('closureComplete',True),('targetProtocolReady',True),('runtimeStatus','passed'),
                          ('javaAttributionExecuted',True),('openBoundaries',[]),('unlinkedCallSiteCount',0)]:
            report=copy.deepcopy(original);report[key]=value;self.assertTrue(validate_summary(report),key)
        report=copy.deepcopy(original);report['sourceEdges'][0]['basis']='verified-runtime'
        self.assertTrue(validate_summary(report))


if __name__=='__main__':unittest.main()
