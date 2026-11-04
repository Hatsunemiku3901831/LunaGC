import unittest
from pathlib import Path
import tempfile

from tools.inventory_protocol import clean_source, declarations, handlers, calls, proto_closure, inventory, JAVA


class ProtocolInventoryTest(unittest.TestCase):
    def test_comments_do_not_create_packets_and_lines_are_preserved(self):
        source = '// @Opcodes(PacketOpcodes.Fake)\n/* two\nlines */\n@Opcodes(value = PacketOpcodes.Real, disabled = true)\nclass HandlerReal {}'
        cleaned = clean_source(source)
        self.assertEqual(source.count('\n'), cleaned.count('\n'))
        self.assertEqual([dict(expression='PacketOpcodes.Real', name='Real', value=11,
                               disabled=True, line=4, handler='HandlerReal')], handlers(source, {'Real': 11}))

    def test_java_strings_do_not_register_handlers_or_send_packets(self):
        source = 'String s = "@Opcodes(1) class Fake {} new BasePacket(9)";'
        self.assertEqual([], handlers(source, {}))
        self.assertEqual([], calls(source))

    def test_constants_include_alias_expressions_and_placeholders(self):
        source = 'public static final int A = 42; public static final int B = A; public static final int C = -10;'
        result = declarations(source)
        self.assertEqual({'A': 42, 'B': 42, 'C': -10}, {r['name']: r['value'] for r in result})

    def test_unknown_expression_is_not_guessed(self):
        result = declarations('public static final int A = B + 1;')
        self.assertIsNone(result[0]['value'])
        self.assertEqual('B + 1', result[0]['expression'])

    def test_disabled_and_numeric_handlers_remain_visible(self):
        rows = handlers('@Opcodes(99) class HandlerA {}\n@Opcodes(PacketOpcodes.Missing) class HandlerB {}', {})
        self.assertEqual(99, rows[0]['value'])
        self.assertIsNone(rows[1]['value'])

    def test_constructor_and_override_arguments_keep_nested_expression(self):
        rows = calls('super(PacketOpcodes.A, 1); new BasePacket(resolve(2, 3)); p.setOpcode(settings.cmdId);')
        self.assertEqual(['PacketOpcodes.A', 'resolve(2, 3)', 'settings.cmdId'], [r['expression'] for r in rows])

    def test_proto_closure_handles_cycles_and_reports_missing(self):
        result = proto_closure({'A.proto': ['B.proto'], 'B.proto': ['A.proto', 'Missing.proto']}, ['A.proto'])
        self.assertEqual(['A.proto', 'B.proto'], result['files'])
        self.assertEqual(['Missing.proto'], result['missing'])

    def test_send_annotation_is_not_a_registered_handler_collision(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in {
                f'{JAVA}/net/packet/PacketOpcodes.java': 'public static final int A = 22;',
                f'{JAVA}/server/packet/recv/HandlerA.java': '@Opcodes(PacketOpcodes.A) class HandlerA extends PacketHandler {}',
                f'{JAVA}/server/packet/send/PacketA.java': '@Opcodes(PacketOpcodes.A) class PacketA extends BasePacket { PacketA(){ super(22); }}',
                'src/main/proto/A.proto': 'syntax="proto3"; message A {}',
            }.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source, encoding='utf-8')
            report = inventory(root)
            self.assertEqual(1, report['counts']['activePositiveHandlers'])
            self.assertEqual({}, report['activeHandlerCollisions'])
            self.assertEqual(2, len(report['opcodeAnnotations']))
            self.assertEqual(report, inventory(root))


if __name__ == '__main__':
    unittest.main()
