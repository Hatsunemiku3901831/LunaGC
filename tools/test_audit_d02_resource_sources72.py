import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import audit_d02_resource_sources72 as audit


def tree(entries):
    rows = [dict(path=name, type=kind, mode='040000' if kind == 'tree' else '100644', sha=sha,
                 **({'size': size} if kind == 'blob' else {})) for name, kind, sha, size in entries]
    payload = b''.join(row['mode'].lstrip('0').encode() + b' ' + row['path'].encode() + b'\0' + bytes.fromhex(row['sha'])
                       for row in sorted(rows, key=lambda row: row['path'].encode() + (b'/' if row['type'] == 'tree' else b'')))
    return dict(sha=audit.git_hash('tree', payload), truncated=False, tree=rows)


class ResourceSourceAuditTest(unittest.TestCase):
    def setUp(self):
        self.data = b'[{"id":1,"nonzero":4294967295}]'
        self.entry = dict(sha=audit.git_hash('blob', self.data), size=len(self.data))
        self.record = dict(remoteName='blocks/00/123.blk', md5='a' * 32, hash='b' * 16, fileSize=4294967295)

    def test_blob_identity_requires_bytes_and_size(self):
        audit.verify_blob(self.data, self.entry)
        for data in (self.data[:-1], self.data.replace(b'4294967295', b'4294967294')):
            with self.assertRaises(ValueError):
                audit.verify_blob(data, self.entry)

    def test_source_selection_does_not_accept_mixed_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            for label, data in (('cache', self.data), ('mixed', b'[]')):
                (base / label).mkdir()
                (base / label / 'table.json').write_bytes(data)
            data, origins = audit.obtain_input('table.json', self.entry,
                [('audit_cache', base / 'cache'), ('r3_mixed_fixture', base / 'mixed')], False)
            self.assertEqual(data, self.data)
            self.assertEqual([row['accepted'] for row in origins], [True, False])

    def test_only_wrong_local_copy_fails_without_fetch(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            (base / 'table.json').write_bytes(b'[]')
            with self.assertRaisesRegex(ValueError, 'No verified local input'):
                audit.obtain_input('table.json', self.entry, [('audit_cache', base)], False)

    def test_tree_chain_rejects_same_name_different_child(self):
        blob = ('303.json', 'blob', self.entry['sha'], self.entry['size'])
        quest = tree([blob])
        point = tree([('scene3_point.json', 'blob', self.entry['sha'], self.entry['size'])])
        scene = tree([('Point', 'tree', point['sha'], None)])
        binout = tree([('Quest', 'tree', quest['sha'], None), ('Scene', 'tree', scene['sha'], None)])
        excel = tree([(name, 'blob', self.entry['sha'], self.entry['size']) for name in audit.EXCEL])
        root = tree([('BinOutput', 'tree', binout['sha'], None), ('ExcelBinOutput', 'tree', excel['sha'], None),
                     ('README.md', 'blob', self.entry['sha'], self.entry['size'])])
        trees = {'': root, 'ExcelBinOutput': excel, 'BinOutput': binout,
                 'BinOutput/Quest': quest, 'BinOutput/Scene': scene, 'BinOutput/Scene/Point': point}
        commit = dict(commit=audit.COMMIT, treeSHA=root['sha'], repository='https://github.com/DimbreathBot/AnimeGameData',
                      versionLabel='CNRELWin7.2.0_R49086080_S49210135_D49230962')
        with patch.object(audit, 'ROOT_TREE', root['sha']):
            self.assertEqual(len(audit.verify_chain(commit, trees)), 13)
            trees['BinOutput/Quest'] = tree([('303.json', 'blob', 'a' * 40, self.entry['size'])])
            with self.assertRaisesRegex(ValueError, 'Tree chain mismatch'):
                audit.verify_chain(commit, trees)

    def test_manifest_keeps_large_size(self):
        self.assertEqual(audit.parse_manifest(json.dumps(self.record).encode()), [self.record])

    def test_manifest_rejects_duplicate_names_and_json_keys(self):
        line = json.dumps(self.record)
        for data in ((line + '\n' + line).encode(), b'{"remoteName":"x","remoteName":"y"}'):
            with self.assertRaises(ValueError):
                audit.parse_manifest(data)

    def test_manifest_rejects_bad_paths_and_types(self):
        for name in ('../x', '/x', 'C:/x', 'a\\b', 'a//b', './x'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                audit.parse_manifest(json.dumps(self.record | dict(remoteName=name)).encode())
        for fields in (dict(fileSize=True), dict(fileSize=-1), dict(md5=1), dict(hash='z' * 16)):
            with self.subTest(fields=fields), self.assertRaises(ValueError):
                audit.parse_manifest(json.dumps(self.record | fields).encode())

    def test_manifest_empty_input_and_wrong_record(self):
        self.assertEqual(audit.parse_manifest(b'\r\n'), [])
        with self.assertRaises(ValueError):
            audit.parse_manifest(b'[]')


if __name__ == '__main__':
    unittest.main()
