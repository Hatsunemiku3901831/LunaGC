"""Capture the approved 203B named getter as static source; never execute it."""
import ast
import hashlib
import json
import struct
import subprocess
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / 'debug/d02-skill-energy-search-20261116/local-analysis/special-energy-getter-source-spec.private.json'
SPEC_SHA = 'a1b0390f13e7134a4557c3549fb495927e955caf4e7d410604c41a66cccf1f14'
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/special-energy-named-getter-source'
ENTRY, END, METHOD = 0x14ced3cb0, 0x14ced3d7b, 494903


def main():
    OUT.mkdir(parents=True, exist_ok=False)
    report = {'task': 'R3-SKILL-SPECIAL-ENERGY-NAMED-GETTER-SOURCE-WINDOW-20261116', 'stage': 'pins', 'inputReads': [], 'stockHashReads': [], 'peReads': [], 'bodyReadBytes': 0, 'bodyReadCalls': 0, 'targetCPU': 0, 'processRPMBytes': 0, 'calleeGlobalXdataBytes': 0, 'resourcePayloadBytes': 0, 'resourceReady': False}
    meter = None
    try:
        spec = json.loads(read_fixed(SPEC, SPEC_SHA, report['inputReads']))
        require(spec['proposedTask'] == report['task'], 'task identity changed')
        inputs = spec['sourceInputs']
        report['toolSHA256'] = sha(Path(__file__).read_bytes())
        for pin in spec['reviewPins'].values():
            read_fixed(ROOT / pin['path'], pin['sha256'], report['inputReads'])
        metadata = json.loads(read_fixed(ROOT / inputs['structures']['path'], inputs['structures']['sha256'], report['inputReads']))
        declaration = spec['selected']['declaration']
        owner = metadata[59617]
        method = next(method for method in owner['methods'] if method['index'] == METHOD)
        aliases = [(owner['index'], method['index']) for owner in metadata for method in owner.get('methods', []) if method.get('registeredAddress') == hex(ENTRY)]
        require((owner['namespace'], owner['name']) == ('MoleMole', 'MonoBattleBtn') and method == declaration['method'] and aliases == [(59617, METHOD)], 'exact ordinary method declaration changed')
        report['identity'] = declaration
        del metadata
        env = {'ROOT': ROOT, 'ENTRY': ENTRY, 'struct': struct, 'sys': sys, 'require': require}
        for key, names in (('FilePE', ('FilePE',)), ('pdataHelper', ('locate_pdata',)), ('strictDecoder', ('decode',))):
            pin = inputs[key]
            raw = read_fixed(ROOT / pin['path'], pin['sha256'], report['inputReads'])
            if key == 'strictDecoder':
                require(raw.count(b'cs.detail=True') == 1, 'strict decoder source changed')
                raw = raw.replace(b'cs.detail=True', b'cs.detail=True;cs.skipdata=False')
            extract_definitions(raw, names, env)
        for key in ('capstoneBinding', 'capstoneX86', 'capstoneConstants', 'capstoneLibrary', 'portableResolver', 'profile'):
            pin = inputs[key]
            read_fixed(ROOT / pin['path'], pin['sha256'], report['inputReads'])
        report['stage'] = 'current-stock'
        resolved = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / inputs['portableResolver']['path'])], capture_output=True, text=True, encoding='utf-8')
        require(resolved.returncode == 0, 'current GAME_PATH resolver failed')
        game = Path(resolved.stdout.strip())
        report['resolvedGamePathHash'] = sha(str(game.resolve()).encode('utf-8'))
        for relative, pin in inputs['fiveStock'].items():
            report['stockHashReads'].append(hash_fixed(game / relative, relative, pin))
        report['stage'] = 'header-AOT-boundary'
        with (game / 'YuanShen.exe').open('rb', buffering=0) as stream:
            meter = Meter(stream)
            pe = env['FilePE'](meter)
            require(pe.meta['readCalls'] == 2 and pe.meta['bytesRead'] <= 8192, 'header budget changed')
            report['header'] = pe.meta
            root_raw, root_offset = pe.at(0x1422f9950, 8)
            require(len(root_raw) == 8, 'short AOT root')
            root = int.from_bytes(root_raw, 'little')
            slot, slot_offset = pe.at(root + METHOD * 8, 8)
            require(len(slot) == 8 and int.from_bytes(slot, 'little') == ENTRY, 'fresh method slot changed')
            save_bytes('aot-root.bin', root_raw)
            save_bytes('aot-slot.bin', slot)
            report['aot'] = {'cell': '0x1422f9950', 'rootOffset': root_offset, 'pointer': hex(root), 'slotVA': hex(root + METHOD * 8), 'slotOffset': slot_offset, 'entry': hex(ENTRY), 'bytes': 16}
            require((pe.pdata_rva, pe.pdata_size) == (0x1a4e6a40, 14344080), 'exception directory extent changed')
            table, directory_offset = pe.at(pe.base + pe.pdata_rva, pe.pdata_size)
            require(len(table) == 14344080 and sha(table) == '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d', 'exception directory hash changed')
            diagnostic = env['locate_pdata'](table, pe.pdata_rva, pe.base, [{'site': hex(ENTRY)}])[0]
            report['boundaryDiagnostic'] = diagnostic
            require(len(diagnostic['containing']) == 1 and diagnostic['beginMultiplicity'] == [1], 'selected containing/multiplicity changed')
            selected = diagnostic['containing'][0]
            expected = spec['selected']['boundary']['selectedPdata']
            require(selected == expected and (int(selected['begin'], 16), int(selected['endExclusive'], 16), selected['size']) == (ENTRY, END, 203), 'exact selected pdata changed')
            overlap = [offset for offset in range(0, len(table), 12) if pe.base + struct.unpack_from('<I', table, offset)[0] < END and ENTRY < pe.base + struct.unpack_from('<I', table, offset + 4)[0]]
            require(overlap == [selected['recordOffset']], 'selected pdata overlap changed')
            section = pe.section(ENTRY, 203, True)
            offset = section['raw'] + ENTRY - section['begin']
            require(offset == 208127152 and offset + 203 <= inputs['fiveStock']['YuanShen.exe']['bytes'], 'exact raw body mapping changed')
            save_bytes('pdata.bin', bytes.fromhex(selected['rawHex']))
            report['exceptionDirectory'] = {'rva': hex(pe.pdata_rva), 'bytes': len(table), 'sha256': sha(table), 'offset': directory_offset}
            report['bodyPlan'] = {'entry': hex(ENTRY), 'endExclusive': hex(END), 'bytes': 203, 'fileOffset': offset, 'sourceEvidenceBytes': 231}
            save_json('pre-body.private.json', report)
            report['stage'] = 'one-body-read'
            body, actual_offset = pe.at(ENTRY, 203, True)
            report.update(bodyReadBytes=len(body), bodyReadCalls=1, bodyFileOffset=actual_offset, bodySHA256=sha(body))
            save_bytes('body.bin', body)
            require(len(body) == 203 and actual_offset == offset, 'short or mislocated body read')
            report['peReads'] = meter.calls
        report['stage'] = 'strict-source-decode'
        decoded = env['decode'](body, entry=ENTRY, max_instructions=203)
        require(b''.join(bytes.fromhex(line['hex']) for line in decoded['lines']) == body, 'strict decode does not cover complete body')
        save_json('body.asm.private.json', decoded)
        guards = {int(value, 16) for value in spec['freeze']['preserveExistingDriverExactGuards']}
        frozen = [ref for ref in decoded['references'] if ref['kind'] in ('call', 'branch') and (int(ref['address'], 16) in guards or ref['target'] is not None and (int(ref['target'], 16) in guards or 0x14cbdd560 <= int(ref['target'], 16) < 0x14cbddd52))]
        save_json('references.private.json', {'references': decoded['references'], 'frozenDirectSitesOrTargets': frozen, 'externalTails': decoded['externalTails'], 'meaning': 'Direct static sites/targets only; no callee bodies, indirect target resolution or refused return/construction semantics.'})
        report.update(passed=True, stage='complete-static-source', instructions=len(decoded['lines']), referenceCount=len(decoded['references']), frozenDirectSitesOrTargets=frozen, sourceEvidenceBytes=231, decoder={'skipdata': False, 'maxInstructions': 203, 'version': decoded['capstoneVersion']})
        save_json('result.private.json', report)
        print(json.dumps({key: report[key] for key in ('passed', 'bodyReadBytes', 'bodySHA256', 'instructions', 'referenceCount', 'frozenDirectSitesOrTargets')}))
    except Exception as exc:
        if meter:
            report['peReads'] = meter.calls
        report.update(passed=False, errorType=type(exc).__name__, error=str(exc), traceback=traceback.format_exc())
        save_json('failure.private.json', report)
        raise
    finally:
        save_json('manifest.private.json', {'files': {path.name: {'bytes': path.stat().st_size, 'sha256': sha(path.read_bytes())} for path in sorted(OUT.iterdir()) if path.is_file()}})


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read_fixed(path, expected, ledger):
    raw = path.read_bytes()
    actual = sha(raw)
    ledger.append({'path': str(path.relative_to(ROOT)), 'bytes': len(raw), 'sha256': actual})
    require(actual == expected, 'fixed input changed: ' + str(path.relative_to(ROOT)))
    return raw


def hash_fixed(path, relative, pin):
    digest, size, calls = hashlib.sha256(), 0, 0
    with path.open('rb', buffering=0) as stream:
        while True:
            raw = stream.read(1048576)
            calls += 1
            if not raw:
                break
            size += len(raw)
            digest.update(raw)
    require(size == pin['bytes'] and digest.hexdigest() == pin['sha256'], 'current stock changed: ' + relative)
    return {'path': relative, 'bytes': size, 'readCalls': calls, 'sha256': digest.hexdigest()}


def extract_definitions(raw, names, env):
    nodes = [node for node in ast.parse(raw).body if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name in names]
    require({node.name for node in nodes} == set(names), 'bounded source helper extraction changed')
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes, type_ignores=[])), '<pinned-source-helper>', 'exec'), env)


def save_bytes(name, raw):
    with (OUT / name).open('xb') as stream:
        stream.write(raw)


def save_json(name, value):
    with (OUT / name).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


class Meter:
    def __init__(self, stream):
        self.stream, self.calls = stream, []

    def seek(self, offset):
        return self.stream.seek(offset)

    def read(self, count):
        offset = self.stream.tell()
        raw = self.stream.read(count)
        self.calls.append({'offset': offset, 'requested': count, 'returned': len(raw)})
        return raw


if __name__ == '__main__':
    main()
