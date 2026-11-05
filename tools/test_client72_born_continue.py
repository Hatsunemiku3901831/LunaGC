"""Regress branch boundaries against the checked native continuation evidence."""
import copy
import json
from pathlib import Path
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_born_continue import validate_fixtures
from tools.client72_reader import WireInput

ROOT=Path(__file__).resolve().parents[1]


class BornContinuationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report=json.loads((ROOT/'protocol/cn-7.2/born-continuation-observations.json').read_text('utf8'))

    def rows(self):
        return copy.deepcopy(self.report['observations'])

    def test_complete_evidence_and_unpromoted_scope(self):
        self.assertEqual(self.report['inputSHA256'],HASHES)
        self.assertEqual(self.report['runtimeStatus'],'not-tested')
        self.assertEqual(self.report['fixtureCount'],33)
        validate_fixtures(self.rows())
        message=self.report['declarations']['stageMessage']
        self.assertEqual((message['typeIndex'],message['cmdId']),(61311,20353))
        self.assertIsNone(message['semanticName'])
        self.assertEqual(message['semanticStatus'],'unknown')
        self.assertEqual(self.report['declarations']['sceneObject']['wireStatus'],'unknown')

    def test_independent_native_stage_wire_vectors(self):
        vectors={r['wire'] for r in self.rows() if r['kind']=='stage-wire'}
        self.assertIn('58107001',vectors)
        self.assertIn('58ffffffff0f70ffffffff0f',vectors)
        for row in self.rows():
            if row['kind']!='stage-wire':continue
            wire=bytes.fromhex(row['wire']);reader=WireInput(wire);fields={}
            while key:=reader.tag():
                self.assertEqual(key&7,0);fields[key>>3]=reader.varint()
            self.assertEqual(fields,{k:v for k,v in ((11,row['object1c']),(14,row['object18'])) if v})
            self.assertEqual(len(wire),row['size']);self.assertTrue(row['protocMatch'])

    def test_error_cannot_enter_success_send_or_state(self):
        for field,value in (('sends',[{'typeIndex':61311,'object18':1,'object1c':16}]),('localFlag',1),('localList',[351])):
            rows=self.rows();row=next(r for r in rows if r.get('retcode')==116);row[field]=value
            with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_missing_stage_context_cannot_send(self):
        rows=self.rows();row=next(r for r in rows if r.get('stageContextPresent') is False)
        self.assertEqual(row['sends'],[])
        row['sends']=[dict(typeIndex=61311,object18=1,object1c=16)]
        with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_missing_callback_owner_is_not_success(self):
        rows=self.rows();row=next(r for r in rows if r.get('missing')=='owner')
        row.update(bornFlags=[0,0],uiState=3,scenePointerPreserved=True,stop='0x14cd67260')
        with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_timer_fallback_and_no_automatic_invocation(self):
        rows=self.rows();row=next(r for r in rows if r['kind']=='timer-registration-suffix' and not r['viewPresent'])
        self.assertEqual(row['duration'],0)
        row['duration']=2.5
        with self.assertRaises(ValueError):validate_fixtures(rows)
        rows=self.rows();next(r for r in rows if r['kind']=='timer-registration-suffix')['timerExecuted']=True
        with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_missing_scene_and_ifix_remain_boundaries(self):
        for kind,key in (('callback-scene-boundary','stageBodyExecuted'),('ifix-boundary','executed')):
            rows=self.rows();next(r for r in rows if r['kind']==kind)[key]=True
            with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_media_queue_retains_callbacks_without_playback_claim(self):
        rows=self.rows();row=next(r for r in rows if r['kind']=='empty-media-config-queue')
        self.assertTrue(row['subtitlePointerIsNull'])
        row['actionTargetsByOffset']['0x50']='0x153d71010'
        with self.assertRaises(ValueError):validate_fixtures(rows)
        rows=self.rows();next(r for r in rows if r['kind']=='empty-media-config-queue')['playbackExecuted']=True
        with self.assertRaises(ValueError):validate_fixtures(rows)

    def test_missing_branch_evidence_is_rejected(self):
        rows=self.rows();rows.pop()
        with self.assertRaises(ValueError):validate_fixtures(rows)


if __name__=='__main__':unittest.main()
