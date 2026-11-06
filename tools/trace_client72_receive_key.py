"""One offline runtime: native Token key install/send, Login receive and dispatch.

The three invocations preserve memory, not the real client's thread scheduling.
The native factory dictionary lookup, scalar/body readers and callback execute;
registry initialization, pooled storage/lifecycle and the queue sink are models.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK, STREAM, OBJECT, END
    from .client72_graph import GraphReader72
    from .client72_graph_fixtures import message
    from .trace_client72_frame import RETURN, expected_frame
    from .client72_session_runtime import fragment
    from .trace_client72_handler_login_frame import (LoginFrame72, HandlerLoginFrame72,
        ContinuousConstructor72, BASE, TailReader72)
    from .trace_client72_response_crypto import discover_private_key, response_wire
    from .client72_rsa_fixture import private_numbers, encrypt_seed, sign_sha256
    from .trace_client72_token import varint
    from .trace_client72_session import java_keys
    from .trace_client72_login_semantics import verify_role
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK, STREAM, OBJECT, END
    from client72_graph import GraphReader72
    from client72_graph_fixtures import message
    from trace_client72_frame import RETURN, expected_frame
    from client72_session_runtime import fragment
    from trace_client72_handler_login_frame import (LoginFrame72, HandlerLoginFrame72,
        ContinuousConstructor72, BASE, TailReader72)
    from trace_client72_response_crypto import discover_private_key, response_wire
    from client72_rsa_fixture import private_numbers, encrypt_seed, sign_sha256
    from trace_client72_token import varint
    from trace_client72_session import java_keys
    from trace_client72_login_semantics import verify_role

SELECTOR = 0x145878FC0
CALLBACK, RECEIVE, FACTORY, LOOKUP = 0x1472CF280, 0x14A446CC0, 0x151A853D0, 0x15585B760
READER, DISPATCH, HANDLER = 0x14B89EC90, 0x1507CC240, 0x1507B7BE0
NATIVE = (CALLBACK, RECEIVE, FACTORY, LOOKUP, READER)
KEY_SHA256 = '5d517d6c52800a32b573b136c7a469131670b869881157c2fa013f714b796df5'
ERRORS = {0x1509CB360, 0x1509CB150, 0x1509CAFA0, 0x1404C2650}


def fixture_cases():
    child = bytes.fromhex('082a1a01432201442a0145')
    resource = message(61, child) + message(1337, bytes.fromhex('3a015a'))
    scalar64 = bytes.fromhex('28818080808080801040ffffffffffffffffff01')
    listed = bytes.fromhex('d23f0a6dffffffff7d2c010000d23f056d01000000f2220408011002f222020803')
    specs = [
        ('nonzero-64-error', scalar64, {'KHJKGGNMAGJ': 9007199254740993, 'LDLIEKOAFBP': -1}, None),
        ('resource-consumer', resource, {'FHCEHICKAIP': dict(KDMKODCHHIP=42, MFHJIGAMNFK='C',
            GFCEGMNDCKH='D', DIDEGEOAEJN='E'), 'MONEPNFKIML': dict(LMMCPBMDBKP='Z')}, None),
        ('message-list', listed, {'LCILAPOPGFA': [dict(PGEMKKCNEMJ=-1, EMBPDEHFOJK=300), dict(PGEMKKCNEMJ=1)],
            'FJJDOOJKBDN': [dict(OGAGAEJHBCA=1, ILJLGHBLKMP=2), dict(OGAGAEJHBCA=3)]}, None),
        ('key-wrap-string', message(437, b'W' * 4200), {'CCDEHJGJFBD': 'W' * 4200}, None),
        ('empty-response', b'', {}, None),
        ('dispatch-key-rejected', scalar64, None, 'dispatch-key'),
        ('bad-start', scalar64, None, 'start'),
        ('truncated-frame', scalar64, None, 'truncate'),
        ('bad-end-after-body', scalar64, {'KHJKGGNMAGJ': 9007199254740993, 'LDLIEKOAFBP': -1}, 'end'),
        ('invalid-body-tag', b'\x00\x01', None, 'body'),
        ('session-after-errors', resource, {'FHCEHICKAIP': dict(KDMKODCHHIP=42, MFHJIGAMNFK='C',
            GFCEGMNDCKH='D', DIDEGEOAEJN='E'), 'MONEPNFKIML': dict(LMMCPBMDBKP='Z')}, None),
    ]
    return [dict(name=n, body=b, expected=v, mutation=m) for n, b, v, m in specs]


class ReceiveLogin72:
    def __init__(self, e, framer, directory):
        self.e, self.framer = e, framer
        self.graph = g = GraphReader72(e, directory)
        g.objects, g.values, g.containers, g.arrays = {}, {}, {}, {}
        g.values.update(g.maps.static_values); g.maps.reset()
        self.packet, self.header = e.alloc(), e.alloc()
        e.write(self.header + 0x14, 1, 'I')
        self.packet_type = e.read(0x14589F4F8)
        self.header_type = e.synthetic_type(0x1458C97A8)
        self.reader_type = e.synthetic_type(0x1458AD6D0)
        self.pool = e.read(GLOBAL + 0x5D60)
        e.write(e.read(self.pool) + 0xF0, e.model_stub(self.rent))
        e.write(e.read(self.pool) + 0xF8, e.model_stub(self.release))
        e.models[0x14D4480C0] = self.allocate_message
        # Decode tags/scalars/unknown fields with the actual native byte reader.
        for entry in (0x14A446A70, 0x1474D8100, 0x147520110, 0x1475202D0,
                      0x14751FD30, 0x1474D7BB0, *ERRORS):
            e.models.pop(entry, None)
        # The graph model already materializes managed fields. Refcount traversal
        # of the complete runtime object pool remains an explicit boundary.
        e.models[0x14B8A05F0] = self.lifecycle
        e.write(g.classes[40503] + 0x168, READER)
        e.write(g.classes[40503] + 0x128, 0x14B8A05F0)
        registry, dictionary, entries, buckets = e.alloc(), e.alloc(), e.alloc(56), e.alloc(100)
        e.write(entries + 24, 1, 'I'); e.write(entries + 32, 20835, 'I')
        e.write(entries + 36, -1, 'i'); e.write(entries + 40, 20835, 'H')
        e.write(entries + 48, g.classes[40503]); e.write(buckets + 24, 17, 'I')
        e.write(buckets + 32 + 4 * (20835 % 17), 1, 'I')
        e.write(dictionary + 0x18, entries); e.write(dictionary + 0x30, buckets)
        e.write(registry + 0x18, dictionary); e.write(GLOBAL + 0xD3C0, registry)
        self.transport, self.queue = e.alloc(), e.alloc()
        e.write(self.transport + 0x78, self.queue)
        e.models[0x159AF2F20] = self.monitor_enter
        e.models[0x1567AFF90] = self.enqueue
        e.write(0x145B7E9A8, 1, 'B')
        for entry in (CALLBACK, RECEIVE, FACTORY, DISPATCH, HANDLER, 0x14A446A70,
                      0x1483D2A00, 0x1483D28C0, 0x14CD81610, 0x14A42BD90,
                      0x1474D8100, 0x147520110, 0x1475202D0, 0x14751FDB0,
                      0x1475211E0, 0x14751FD30, 0x1475215C0, 0x1495AA480,
                      0x1495C9D60, 0x159B548E0, 0x1474D7BB0):
            e.allow(entry)
        e.allow_unwind(LOOKUP)

    def rent(self):
        e = self.e
        if e.reg('rcx') != self.pool: raise ValueError('Unexpected receive pool')
        if e.reg('rdx') == self.packet_type: return self.packet
        if e.reg('rdx') == self.reader_type: return STREAM
        raise ValueError('Unreviewed receive allocation')

    def release(self):
        if (self.e.reg('rcx'), self.e.reg('rdx')) != (self.pool, STREAM):
            raise ValueError('Unexpected reader release')
        self.released = True
        return 0

    def allocate_message(self):
        if self.e.reg('rcx') == self.header_type: return self.header
        return self.graph.allocate()

    def lifecycle(self):
        if self.graph.objects.get(self.e.reg('rcx')) != 40503:
            raise ValueError('Unexpected modeled message lifecycle')
        return 0

    def monitor_enter(self):
        if self.e.reg('rcx') != self.queue: raise ValueError('Unexpected queue lock')
        return 0

    def enqueue(self):
        if (self.e.reg('rcx'), self.e.reg('rdx')) != (self.queue, self.packet):
            raise ValueError('Unexpected received packet queue identity')
        self.enqueued.append(self.e.reg('rdx'))
        return 0

    def execute(self, entry, stops, registers):
        """Observe graph ABI and native call boundaries without replacing calls."""
        e = self.e; e.uc.context_restore(e.context)
        e.calls, e.failure, e.steps = [], None, 0
        e.write(STACK + 0x100008, RETURN)
        for name, value in dict(rsp=STACK + 0x100008, **registers).items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
        counts, digest, reached = Counter(), hashlib.sha256(), []
        for stop in stops: e.allowed.append((stop, stop + 1))
        def observe(address):
            self.graph.observe_entry(address)
            counts[address] += 1; digest.update(struct.pack('<Q', address))
            if address == 0x1472CF38F: self.receive_code = e.reg('eax')
            if address == HANDLER: self.handler_message = e.reg('rdx')
            if address in stops: reached.append(address); e.uc.emu_stop()
        e.before_instruction = observe
        try: e.uc.emu_start(entry, END, timeout=20_000_000, count=20_000_000)
        finally: e.before_instruction = None
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Receive boundary not reached')
        return dict(entry=hex(entry), stop=hex(reached[0]), instructions=e.steps,
            nativePathSHA256=digest.hexdigest(), executedAddresses={hex(k): v for k, v in sorted(counts.items())},
            models=[c['address'] for c in e.calls])

    def receive(self, cipher):
        e, g = self.e, self.graph
        e.write(STACK + 0x100008, RETURN)
        fragment(e, 0x159B548E0, {RETURN}, dict(rsp=STACK + 0x100008))
        g.events, g.nested_calls, g.entered, g.map_stores = [], [], [], []
        g.streams, g.stream, g.active = {STREAM: None}, STREAM, True
        self.enqueued, self.released, self.receive_code = [], False, None
        data = self.framer.array(cipher); reference = e.alloc(); e.write(reference, data)
        trace = self.execute(CALLBACK, {RETURN, *ERRORS},
            dict(rcx=self.transport, rdx=reference, r8=len(cipher), r9=0))
        g.active = False
        pointer = e.read(self.packet + 0x10)
        result = g.snapshot(pointer) if pointer and self.receive_code in (1, 7) else None
        return trace, result, bytes(e.uc.mem_read(data + 32, len(cipher)))

    def consume(self, manager, error):
        e = self.e; self.handler_message = None
        e.write(0x145B8E499, 1, 'B'); e.synthetic_type(0x1458AD418)
        e.write(GLOBAL + 0x26790, e.alloc())
        e.models[0x14C8AE9A0] = lambda: 0
        e.models[0x147603350] = lambda: 0
        e.models[0x159AD6130] = lambda: 0x1234
        e.models[0x159C1AEB0] = lambda: e.alloc()
        trace = self.execute(DISPATCH, {0x1531DC5E0 if error else 0x1507B8368},
            dict(rcx=manager, rdx=self.packet))
        pointer = e.read(self.packet + 0x10)
        return trace, dict(entry=hex(DISPATCH), handler=hex(HANDLER), stop=trace['stop'],
            messagePointerPreserved=self.handler_message == pointer,
            nativeHandlerExecuted=hex(HANDLER) in trace['executedAddresses'])


def recover(binary, metadata, startup, records, libraries, signing_key, java_home, root):
    role = dict(typeIndex=40503, cmdId=20835, handler=hex(HANDLER), registration='0x1507c29e5')
    declaration = verify_role(binary, metadata, startup, records, role)
    private, _, _ = discover_private_key(binary, metadata, records, libraries)
    signer = private_numbers(signing_key.read_bytes()); plain = bytes.fromhex('fedcba9876543210')
    seed = 0x0123456789ABCDEF
    keys, java_sources = java_keys(java_home, [int.from_bytes(plain, 'big') ^ seed], root)
    wire = response_wire(encrypt_seed(private_numbers(private), plain), sign_sha256(signer, plain))
    wire += b'\x48' + varint(BASE['uid']) + message(6, BASE['token'].encode()) + message(658, BASE['secret'].encode())
    e = Offline72(binary, libraries, records, True); reader = runtime = receiver = None
    writes, slot_reads = [], []
    hooks = []
    try:
        e.prepare(); framer = LoginFrame72(e)
        alternate = bytes((i * 19 + 7) % 256 for i in range(4096))
        e.write(GLOBAL + 0x19588, framer.array(alternate))
        reader = TailReader72(e); token_read = reader.run(37481, wire)
        ctor = ContinuousConstructor72(e, binary, metadata, 0, '123', '456'); ctor.close()
        e.models.pop(0x1531D02B0); e.models.pop(0x159A8D050)
        source = e.read(e.read(e.read(GLOBAL + 0x9E80) + 0x180) + 0x50)
        runtime = HandlerLoginFrame72(e, binary, metadata, seed, signer, ctor, source, BASE, 'send', framer=framer)
        hooks.append(e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,
            lambda uc, access, address, size, value, data: writes.append(dict(instruction=hex(e.reg('rip')), value=value)),
            begin=SELECTOR, end=SELECTOR))
        hooks.append(e.uc.hook_add(e.U.UC_HOOK_MEM_READ,
            lambda uc, access, address, size, value, data: slot_reads.append(dict(instruction=hex(e.reg('rip')), offset=hex(address-GLOBAL))),
            begin=GLOBAL + 0x19580, end=GLOBAL + 0x1958F))
        handler_trace, key = runtime.run_handler()
        key_pointer = e.read(GLOBAL + 0x19580)
        if key != keys[0] or hashlib.sha256(key).hexdigest() != KEY_SHA256 or e.read(SELECTOR, 'B') != 0:
            raise ValueError('Native installed key differs from independent Java')
        if runtime.stop != runtime.frame_return or runtime.frame_return != 0x1472CD364 or e.reg('al') != 1 or runtime.stages != [9, 6]:
            raise ValueError('Native Handler did not complete the Login frame')
        install_writes = list(writes)
        receiver = ReceiveLogin72(e, framer, root / 'protocol/cn-7.2')
        if e.read(GLOBAL + 0x19580) != key_pointer or e.read(SELECTOR, 'B') != 0:
            raise ValueError('Receiver setup replaced key state')
        results, fixtures = [], []
        for case in fixture_cases():
            writes.clear(); slot_reads.clear()
            mutation, body = case['mutation'], case['body']
            raw = expected_frame(20835, body, 0xFFFFFFFF, 0x200000044, None)
            if mutation == 'start': raw = b'\x44' + raw[1:]
            elif mutation == 'end': raw = raw[:-1] + b'\xaa'
            elif mutation == 'truncate': raw = raw[:-1]
            encryption_key = alternate if mutation == 'dispatch-key' else key
            cipher = bytes(v ^ encryption_key[i % 4096] for i, v in enumerate(raw))
            before = e.read(SELECTOR, 'B')
            trace, actual, decrypted = receiver.receive(cipher)
            expected_code = {'dispatch-key': 6, 'start': 6, 'truncate': 2, 'end': 7, 'body': None}.get(mutation, 1)
            if receiver.receive_code != expected_code:
                raise ValueError(f"{case['name']}: native receive code {receiver.receive_code}")
            if actual != case['expected']:
                raise ValueError(f"{case['name']}: independent object mismatch: {actual}")
            consumer_trace = consumer = None
            if expected_code == 1:
                consumer_trace, consumer = receiver.consume(runtime.manager, case['name'] == 'nonzero-64-error')
                if case['name'] in ('resource-consumer', 'session-after-errors'):
                    p = e.read(receiver.packet + 0x10); current, following = e.read(p+0x68), e.read(p+0x48)
                    checks = [(GLOBAL+0x61F0, current+0x38), (GLOBAL+0x60B0, current+0x30),
                              (GLOBAL+0x60F8, current+0x20), (GLOBAL+0x6320, following+0x18)]
                    consumer['resourcePointersPreserved'] = all(e.read(a) == e.read(b) for a, b in checks)
                    consumer['resourceVersion'] = e.read(0x14586B610+0x192C, 'I')
                    if not consumer['resourcePointersPreserved'] or consumer['resourceVersion'] != 42:
                        raise ValueError('Received resource graph lost at native consumer')
            pointer_preserved = e.read(GLOBAL + 0x19580) == key_pointer
            bytes_preserved = bytes(e.uc.mem_read(key_pointer + 32, 4096)) == key
            row = dict(name=case['name'], returnCode=receiver.receive_code, stop=trace['stop'],
                enqueued=receiver.enqueued == [receiver.packet], readerReleased=receiver.released,
                selectorBefore=before, selectorAfter=e.read(SELECTOR, 'B'), selectorWrites=list(writes),
                keyPointerPreserved=pointer_preserved, keyBytesPreserved=bytes_preserved,
                slotReads=list(slot_reads), frameLength=len(cipher), bodyLength=len(body),
                xorIterations=trace['executedAddresses'].get('0x14a446dbe', 0),
                nativeEntries=[hex(a) for a in NATIVE if hex(a) in trace['executedAddresses']],
                models=trace['models'],
                independentObjectMatch=True, independentDecryptMatch=decrypted == raw,
                bodySHA256=hashlib.sha256(body).hexdigest(), objectSHA256=hashlib.sha256(
                    json.dumps(actual, sort_keys=True).encode()).hexdigest(), consumer=consumer,
                nativePathSHA256=trace['nativePathSHA256'], passed=True)
            results.append(row); fixtures.append(dict(**row, trace=trace, consumerTrace=consumer_trace,
                inputHex=cipher.hex(), decodedObject=actual, payloadHex=body.hex()))
            print(json.dumps(dict(case=case['name'], returnCode=row['returnCode'], enqueued=row['enqueued'])), flush=True)
        return dict(schemaVersion=1, inputSHA256=HASHES, fixtureCount=len(results), results=results,
            fixtures=fixtures, runtimeStatus='not-tested', sameRuntimeState=True, continuousControlFlow=False,
            wholeHandlerReturned=False, keySuppliedToReceiver=False, sessionKeySHA256=KEY_SHA256,
            nativeInstallWrites=install_writes, javaSourceSHA256=java_sources, declaration=declaration,
            nativeKeyChecks=runtime.key_checks, loginFrameReturned=True,
            signingFixtureSHA256=hashlib.sha256(signing_key.read_bytes()).hexdigest(),
            handlerTrace=handler_trace, tokenReader=token_read,
            models=['Existing Handler crypto trust/SDK/clock/managed storage fixtures',
                'Registry initialized with independently verified 20835 -> type40503; native dictionary lookup executes',
                'Managed graph allocation, ASCII/blob storage, pooled packet/header/reader storage and concrete lifecycle boundary',
                'Receive queue lock and enqueue sink; consumer explicitly invoked with the same queued packet',
                'Handler stage/cache reset and error formatting boundaries'],
            outsideScope=['Socket/real client/thread scheduling/IFix', 'Complete Token/Login Handler return',
                'Whole registry and real object-pool startup', 'All possible selector writers/reconnect branches'])
    finally:
        for hook in hooks: e.uc.hook_del(hook)
        if receiver: receiver.graph.close()
        if runtime: runtime.close()
        if reader: reader.close()
        e.close()


def validate_summary(report):
    errors = []
    expected = dict(schemaVersion=1, inputSHA256=HASHES, fixtureCount=11, runtimeStatus='not-tested',
        sameRuntimeState=True, continuousControlFlow=False, wholeHandlerReturned=False,
        keySuppliedToReceiver=False, loginFrameReturned=True, sessionKeySHA256=KEY_SHA256)
    if any(report.get(k) != v for k, v in expected.items()) or not report.get('models') or not report.get('javaSourceSHA256'):
        errors.append('Receive key scope or independent input mismatch')
    if not any(w.get('instruction') == '0x15a4e0d3b' and w.get('value') == 0 for w in report.get('nativeInstallWrites', [])):
        errors.append('Native selector installation evidence missing')
    if [(v.get('operation'), v.get('oracle'), v.get('native')) for v in report.get('nativeKeyChecks', [])] != [(101, True, True), (103, True, True)] or len(report.get('signingFixtureSHA256', '')) != 64:
        errors.append('Native signature verification or explicit signing input evidence missing')
    rows = report.get('results', [])
    if [r.get('name') for r in rows] != [c['name'] for c in fixture_cases()]:
        errors.append('Receive boundary sample set changed')
    for row, case in zip(rows, fixture_cases()):
        mutation = case['mutation']; code = {'dispatch-key': 6, 'start': 6, 'truncate': 2, 'end': 7, 'body': None}.get(mutation, 1)
        decoded = code in (1, 7)
        if (row.get('returnCode') != code or row.get('enqueued') != (code == 1) or
            row.get('selectorBefore') != 0 or row.get('selectorAfter') != 0 or row.get('selectorWrites') != [] or
            any(row.get(k) is not True for k in ('keyPointerPreserved', 'keyBytesPreserved', 'independentObjectMatch', 'passed'))):
            errors.append('Receive outcome or preserved native key changed')
        if row.get('independentDecryptMatch') != (mutation != 'dispatch-key') or row.get('xorIterations') != row.get('frameLength'):
            errors.append('Receive native XOR evidence changed')
        entries = [hex(a) for a in NATIVE] if decoded or mutation == 'body' else [hex(CALLBACK), hex(RECEIVE)]
        if row.get('nativeEntries') != entries or row.get('readerReleased') != (code == 1):
            errors.append('Native callback/factory/body or lifecycle evidence changed')
        if not any(v == dict(instruction='0x14a446d65', offset='0x19580') for v in row.get('slotReads', [])):
            errors.append('Receiver did not read installed session key slot')
        expected_stop = '0x1509cb150' if mutation == 'body' else hex(RETURN)
        if row.get('stop') != expected_stop or row.get('bodySHA256') != hashlib.sha256(case['body']).hexdigest():
            errors.append('Body or native error boundary changed')
        if (row.get('bodyLength') != len(case['body']) or
            row.get('frameLength') != len(case['body']) + 24 - int(mutation == 'truncate') or
            row.get('objectSHA256') != hashlib.sha256(json.dumps(case['expected'], sort_keys=True).encode()).hexdigest()):
            errors.append('Independent body, decoded object or boundary length changed')
        forbidden_models = {hex(a) for a in (*NATIVE, 0x14A446A70, 0x1474D8100, 0x147520110, 0x1475202D0)}
        if set(row.get('models', [])) & forbidden_models:
            errors.append('Native receive operation replaced with a model')
        consumer = row.get('consumer')
        if code == 1:
            stop = '0x1531dc5e0' if case['name'] == 'nonzero-64-error' else '0x1507b8368'
            if not consumer or any(consumer.get(k) != v for k, v in dict(entry=hex(DISPATCH), handler=hex(HANDLER),
                stop=stop, messagePointerPreserved=True, nativeHandlerExecuted=True).items()):
                errors.append('Queued response did not reach native typed consumer')
            if case['name'] in ('resource-consumer', 'session-after-errors') and (
                consumer.get('resourcePointersPreserved') is not True or consumer.get('resourceVersion') != 42):
                errors.append('Resource consumer source pointer changed')
        elif consumer is not None:
            errors.append('Rejected frame reached consumer')
    return errors


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'java-home', 'output', 'summary-output'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    p.add_argument('--signing-key', type=Path, default=Path('src/main/resources/keys/SigningKey.der'))
    a = p.parse_args(); outputs = (a.output, a.summary_output)
    if len({v.resolve() for v in outputs}) != 2 or any(v.resolve().is_relative_to(a.client.resolve()) for v in outputs):
        p.error('Distinct outputs must be outside the client')
    binary, metadata, startup = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    root = Path(__file__).resolve().parents[1]
    report = recover(binary, metadata, startup, records, a.libraries, a.signing_key, a.java_home, root)
    report['sourceSHA256'] = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
        'tools/trace_client72_receive_key.py', 'tools/client72_graph.py', 'tools/trace_client72_handler_login_frame.py',
        'protocol/cn-7.2/response-wire-observations.json', 'protocol/cn-7.2/response-leaf-wire-observations.json')}
    errors = validate_summary(report)
    if errors: raise ValueError(errors)
    summary = {k: v for k, v in report.items() if k not in ('fixtures', 'handlerTrace', 'tokenReader')}
    for path, value in ((a.output, report), (a.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')


if __name__ == '__main__':
    main()
