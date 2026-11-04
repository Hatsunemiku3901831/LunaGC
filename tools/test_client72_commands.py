import unittest

from tools.extract_client72_commands import classify_commands, BASE_NAME, GETTER


class Commands72Test(unittest.TestCase):
    def setUp(self):
        def row(i, name, value, parent):
            return dict(index=i, name=name, namespace='', parent=parent, methods=[dict(
                index=i, name=GETTER, parameterCount=0, flags='0x8c4', virtualSlot=22,
                registeredAddress=hex(0x140001000 + i * 16), literalU16Candidate=value)])
        self.rows = [row(0, BASE_NAME, None, None), row(1, 'PacketA', 42, {'typeDefinition': 0}),
                     row(2, 'PacketB', 42, {'typeDefinition': 0})]

    def test_duplicate_values_preserved_and_not_turned_into_aliases(self):
        result = classify_commands(self.rows)
        self.assertEqual({'types': 2, 'distinctValues': 1}, result['counts'])
        self.assertEqual(['PacketA', 'PacketB'], result['duplicateValues'][0]['clientTypes'])
        self.assertEqual('unresolved', result['businessNamesStatus'])
        self.assertEqual('not-tested', result['runtimeStatus'])
        self.assertEqual(result, classify_commands(self.rows))

    def test_same_named_getter_without_packet_ancestry_rejected(self):
        self.rows[1]['parent'] = None
        with self.assertRaisesRegex(ValueError, 'ancestry'):
            classify_commands(self.rows)

    def test_wrong_slot_static_or_parameterized_getter_rejected(self):
        for key, value in (('virtualSlot', 21), ('flags', '0x8d4'), ('parameterCount', 1),
                           ('literalU16Candidate', 0), ('literalU16Candidate', None)):
            with self.subTest(key=key, value=value):
                old = self.rows[1]['methods'][0][key]
                self.rows[1]['methods'][0][key] = value
                with self.assertRaises(ValueError):
                    classify_commands(self.rows)
                self.rows[1]['methods'][0][key] = old

    def test_missing_and_cyclic_parent_graph_fail(self):
        for parent in (1, 99):
            self.rows[1]['parent'] = {'typeDefinition': parent}
            with self.assertRaises(ValueError):
                classify_commands(self.rows)


if __name__ == '__main__':
    unittest.main()
