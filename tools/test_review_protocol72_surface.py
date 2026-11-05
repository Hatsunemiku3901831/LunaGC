import copy
import tempfile
import unittest
from pathlib import Path

from tools.inventory_protocol import JAVA, inventory
from tools.review_protocol72_surface import check_review, numeric_cases, text_hash


class SurfaceReviewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.opcodes = f'{JAVA}/net/packet/PacketOpcodes.java'
        self.packet = f'{JAVA}/server/packet/send/PacketA.java'
        self.write(self.opcodes, 'public static final int A = 42;\n')
        self.write(self.packet, 'class PacketA extends BasePacket { PacketA(int id) { super(id); } }\n')
        current = inventory(self.root)
        self.review = dict(
            schemaVersion=1, scope='server-source-baseline',
            sourceSHA256={p: text_hash(self.root/p) for p in [self.opcodes, self.packet]},
            dynamicSites=[dict(file=self.packet, line=1, kind='super', expression='id', group='external')],
            groups={'external': dict(classification='external-api', finding='caller supplies ID',
                                     sources=[self.packet], legacyConstants={'A': 42})},
            numericCases=[], numericDomains={}, numericSends=[],
            configurationCandidates=current['configurationCandidates'],
            openBoundaries=[dict(sources=[self.packet], finding='external caller', nextTask='C-01')])

    def write(self, name, text):
        path = self.root/name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, 'utf-8')

    def errors(self, review=None):
        report = check_review(self.root, self.review if review is None else review)
        self.assertFalse(report['protocolReady'])
        return '\n'.join(report['errors'])

    def test_review_passes_deterministically_without_protocol_approval(self):
        self.assertEqual('', self.errors())
        self.assertEqual(check_review(self.root, self.review), check_review(self.root, self.review))
        path = self.root/self.packet
        path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
        self.assertEqual('', self.errors())

    def test_same_site_count_with_changed_expression_fails(self):
        self.write(self.packet, 'class PacketA extends BasePacket { PacketA(int changed) { super(changed); } }\n')
        errors = self.errors()
        self.assertIn('reviewed source changed', errors)
        self.assertIn('dynamic send surface changed', errors)

    def test_new_unreviewed_file_and_literal_send_fail(self):
        self.write(f'{JAVA}/server/Other.java', 'new BasePacket(444); new BasePacket(config.cmdId);')
        errors = self.errors()
        self.assertIn('dynamic send surface changed', errors)
        self.assertIn('numeric send arguments changed', errors)

    def test_numeric_switch_masks_strings_comments_and_detects_new_domain(self):
        name = f'{JAVA}/server/Other.java'
        self.write(name, '// case 3:\nString s = "case 4 ->";\nswitch(x) { case 0xA, 11 -> call(); }')
        self.assertEqual([dict(file=name, line=3, expression='0xA, 11')], numeric_cases(self.root))
        self.assertIn('numeric switch surface changed', self.errors())
        self.assertIn('domain review is incomplete', self.errors())

    def test_legacy_value_change_fails_even_if_fingerprint_was_updated(self):
        self.write(self.opcodes, 'public static final int A = 99;')
        self.review['sourceSHA256'][self.opcodes] = text_hash(self.root/self.opcodes)
        self.assertIn('legacy constant binding changed', self.errors())

    def test_duplicate_site_missing_fingerprint_and_runtime_boundaries_fail(self):
        review = copy.deepcopy(self.review)
        review['dynamicSites'] *= 2
        del review['sourceSHA256'][self.packet]
        review['openBoundaries'] = []
        errors = self.errors(review)
        self.assertIn('duplicate reviewed dynamic site', errors)
        self.assertIn('dynamic site lacks source fingerprint', errors)
        self.assertIn('boundaries must remain explicit', errors)

    def test_escaping_source_path_is_rejected(self):
        self.review['sourceSHA256']['../outside.java'] = '0'*64
        self.assertIn('relative POSIX paths', self.errors())


if __name__ == '__main__':
    unittest.main()
