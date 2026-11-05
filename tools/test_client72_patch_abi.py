import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_patch_abi import TARGETS, check_declaration


class PatchAbiTests(unittest.TestCase):
    def setUp(self):
        path=Path(__file__).resolve().parents[1]/'protocol/cn-7.2/patch-abi-observations.json'
        self.rows=json.loads(path.read_text('utf8'))['declarations']

    def test_current_target_declarations_match_native_prefix_contracts(self):
        for row,target in zip(self.rows,TARGETS):check_declaration(row,*target)

    def test_static_vs_instance_receiver_cannot_be_interchanged(self):
        for row,target in zip(self.rows,TARGETS):
            altered=copy.deepcopy(row);altered['static']=not row['static']
            with self.assertRaises(ValueError):check_declaration(altered,*target)

    def test_rsa_content_and_key_cannot_be_swapped(self):
        row=copy.deepcopy(self.rows[2]);row['parameters'].reverse()
        with self.assertRaises(ValueError):check_declaration(row,*TARGETS[2])

    def test_browser_url_cannot_use_receiver_register(self):
        row=copy.deepcopy(self.rows[1]);row['parameters'][0]['register']='rcx'
        with self.assertRaises(ValueError):check_declaration(row,*TARGETS[1])

    def test_wrong_overload_address_owner_or_parameter_type_is_rejected(self):
        for key,value in [('entry','0x159c21cc0'),('ownerTypeIndex',1533),('methodIndex',9393)]:
            row=copy.deepcopy(self.rows[3]);row[key]=value
            with self.assertRaises(ValueError):check_declaration(row,*TARGETS[3])
        row=copy.deepcopy(self.rows[3]);row['parameters'][0]['kind']='string'
        with self.assertRaises(ValueError):check_declaration(row,*TARGETS[3])
        row=copy.deepcopy(self.rows[3]);row['parameters']*=2
        with self.assertRaises(ValueError):check_declaration(row,*TARGETS[3])


if __name__=='__main__':unittest.main()
