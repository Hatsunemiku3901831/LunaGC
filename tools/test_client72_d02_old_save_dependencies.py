import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools.client72_d02_old_save_dependencies import bson_document, extract, read_input


class OldSaveDependenciesTest(unittest.TestCase):
    def test_inactive_items_do_not_create_equipment_dependencies(self):
        items = [{'ownerId': 7, 'itemId': 11101, 'count': 1, 'deleted': True, 'equipCharacter': 42},
                 {'ownerId': 7, 'itemId': 11201, 'count': 0, 'equipCharacter': 43}]
        result = extract({'_id': 7}, [], items)
        self.assertEqual(result['references'], [])
        self.assertEqual(result['unresolved'], [])
        self.assertEqual(len(result['items']), 2)

    def test_owner_filter_and_references(self):
        player = {'_id': 7, 'sceneId': 3, 'position': {'x': 1, 'y': 2, 'z': 3},
                  'teamManager': {'currentTeamIndex': 1, 'currentCharacterIndex': 0,
                                  'teams': {'1': {'avatars': [10000005, 42]}}}}
        avatars = [{'_id': {'$oid': 'a'}, 'ownerId': 7, 'avatarId': 10000005, 'skillDepotId': 504, 'level': 40, 'promoteLevel': 2,
                    'talentIdList': [71, 0], 'proudSkillList': [721], 'skillLevelMap': {'10068': 4}},
                   {'ownerId': 8, 'avatarId': 42}]
        items = [{'ownerId': 7, 'itemId': 11101, 'count': 1, 'deleted': False, 'equipCharacter': 10000005},
                 {'ownerId': 7, 'itemId': 30001, 'count': 1, 'equipCharacter': 99},
                 {'ownerId': 7, 'itemId': 30002, 'count': 0},
                 {'ownerId': 8, 'itemId': 999, 'count': 1}]
        result = extract(player, avatars, items)
        refs = {(r['type'], r['id'], r['scope']) for r in result['references']}
        self.assertIn(('Depot', 504, 'additional'), refs)
        self.assertIn(('Item', 11101, 'birth-id-only'), refs)
        self.assertIn(('Item', 30001, 'additional'), refs)
        self.assertNotIn(('Item', 30002, 'additional'), refs)
        self.assertEqual(result['excludedOtherOwner'], {'avatars': 1, 'items': 1})
        self.assertEqual({p['kind'] for p in result['unresolved']}, {'team-avatar-not-in-owner-avatars', 'equip-character-not-in-owner-avatars'})
        self.assertIn('level', result['items'][0]['missing'])
        self.assertEqual({r['id'] for r in result['savedConditionalReferences']}, {71, 721, 10068})
        self.assertTrue(all(r['semanticStatus'] == 'not-verified-by-this-driver' for r in result['references']))

    def test_bson_int64_nested_and_pinned_input(self):
        nested = struct.pack('<i', 12) + b'\x10x\x00' + struct.pack('<i', 2) + b'\x00'
        body = b'\x12n\x00' + struct.pack('<q', 1 << 40) + b'\x03p\x00' + nested
        raw = struct.pack('<i', len(body) + 5) + body + b'\x00'
        self.assertEqual(bson_document(raw)[0], {'n': 1 << 40, 'p': {'x': 2}})
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'snapshot.bson'; path.write_bytes(raw)
            row = {'path': str(path), 'format': 'bson', 'sha256': hashlib.sha256(raw).hexdigest()}
            self.assertEqual(read_input(row)[0]['n'], 1 << 40)
            row['sha256'] = '0' * 64
            with self.assertRaisesRegex(ValueError, 'SHA-256'): read_input(row)

    def test_extended_json_and_absent_fields(self):
        with tempfile.TemporaryDirectory() as d:
            raw = json.dumps({'_id': {'$numberInt': '7'}, 'sceneId': {'$numberLong': '3'}}).encode()
            path = Path(d) / 'player.json'; path.write_bytes(raw)
            player = read_input({'path': str(path), 'format': 'json', 'sha256': hashlib.sha256(raw).hexdigest()})[0]
            result = extract(player, [], [])
            self.assertEqual(result['player']['fields']['sceneId'], 3)
            self.assertEqual(result['player']['missing'], ['position', 'teamManager'])

    def test_bad_bson_is_not_accepted(self):
        with self.assertRaises(ValueError): bson_document(b'\x05\x00\x00\x00\x01')


if __name__ == '__main__': unittest.main()
