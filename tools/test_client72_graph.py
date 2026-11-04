import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from tools.client72_graph import GraphReader72
from tools.client72_map_runtime import MapRuntime72
from tools.client72_graph_fixtures import fixtures
from tools.client72_reader import WireInput
from tools.client72_emulator import STREAM
from tools.observed_proto72 import result_text,render


class GraphContractTest(unittest.TestCase):
    def test_ambiguous_aot_binding_never_selects_first(self):
        model=object.__new__(MapRuntime72)
        model.bindings=[dict(owner=1,classInstance=2,method='Read',address='0x100')]
        self.assertEqual(0x100,model.binding(1,2,'Read'))
        with self.assertRaises(ValueError):model.binding(1,3,'Read')
        model.bindings.append(dict(owner=1,classInstance=2,method='Read',address='0x200'))
        with self.assertRaises(ValueError):model.binding(1,2,'Read')

    def test_readtag_consumes_cached_lookahead_without_reading_again(self):
        words={STREAM+0x25:1,STREAM+0x3c:16,STREAM+0x44:1,STREAM+0x40:2,STREAM+0x38:2}
        model=object.__new__(GraphReader72)
        model.e=SimpleNamespace(reg=lambda _:STREAM,read=lambda a,*_:words.get(a,0),write=lambda a,v,*_:words.__setitem__(a,v))
        model.streams={STREAM:WireInput(bytes.fromhex('1001'))}
        self.assertEqual(16,model.tag());self.assertEqual(1,words[STREAM+0x44]);self.assertEqual(0,words[STREAM+0x25])
        self.assertEqual(1,model.scalar(32));self.assertEqual(0,model.tag())

    def test_map_text_preserves_large_key_bytes_and_empty_message(self):
        common=dict(cardinality='singular',packed=False)
        child=dict(clientType='Child',fields=[dict(common,clientField='raw',type='bytes',tag=3)])
        parent=dict(clientType='Parent',fields=[dict(clientField='items',type='map',keyType='uint64',valueType='message',
                          nestedType='Child',nestedTypeIndex=2,cardinality='map',packed=False,tag=265)])
        text=result_text(1,{'items':{'9007199254740993':{'raw':{'hex':'00ff'}},'0':{}}},{1:parent,2:child})
        self.assertEqual('items { key: 9007199254740993 value { raw: "\\000\\377" } } items { key: 0 value {  } }',text)
        self.assertIn('map<uint64, Child> items = 265;',render([child,parent]))

    def test_runtime_string_keys_compare_content_not_object_address(self):
        model=object.__new__(MapRuntime72);model.g=SimpleNamespace(values={100:{'hex':'41'},200:{'hex':'41'}})
        self.assertEqual(model.canonical(100,{'key':'string'}),model.canonical(200,{'key':'string'}))
        self.assertEqual(9007199254740993,model.canonical(9007199254740993,{'key':'uint64'}))
        self.assertEqual(0xffffffff,model.canonical(-1,{'key':'uint32'}))

    def test_independent_regression_wire_and_transition_cases_present(self):
        cases={x['name']:x for x in fixtures()}
        self.assertEqual('b21c0808ac0210011a0141b21c051002220142',cases['token_nested_merge']['wire'])
        self.assertEqual(300,cases['token_nested_merge']['expected']['KMLEGLIGNPH']['JJLJMLEJIIK'])
        self.assertEqual({'1':{'HENCCAHJOKD':2}},cases['map_265_duplicate_value_replaces']['expected']['HIHJANHKAMO'])
        for name in ('oneof_same_message_merge','oneof_same_list_append','oneof_switch_and_return',
                     'oneof_pair_merge','oneof_bool_merge','second_oneof_same_message_merge','second_oneof_same_enum_overwrites'):
            self.assertIn(name,cases)
