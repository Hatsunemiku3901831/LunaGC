"""Project the frozen full-helper contracts into private C data; no target execution."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b'
CONTRACT = PRIVATE / 'a-clear48-full-helper-contract'
OUTPUT_ROOT = PRIVATE / 'a-clear48-full-helper-owned'
PINS = {
    'catalog.v1.private.json': '02d1803ec7afd6162c23584a76315adaedbeeab81fd83214a76a32a50ecc491e',
    'cases.v1.private.json': 'ae3fe4ec1634dd7b93e177b739ab9868e0dbaf4c7308e5ab563af8d6add8651a',
    'interface.v1.private.json': '5c513a0bf18bd84750b38bda40fd019140209673d2f74264305880fd9c761597',
}


def require(value, reason):
    if not value:
        raise ValueError(reason)


def cbytes(raw, width):
    require(len(raw) <= width, 'C byte array overflow')
    return '{' + ','.join(f'0x{x:02x}' for x in raw) + '}'


def generate(output):
    output = output.resolve()
    require(output.is_relative_to(OUTPUT_ROOT.resolve()) and not output.exists(), 'new owned output required')
    raw = {name: (CONTRACT / name).read_bytes() for name in PINS}
    for name, data in raw.items():
        require(hashlib.sha256(data).hexdigest() == PINS[name], 'frozen contract drift: ' + name)
    catalog, cases, interface = (json.loads(raw[n]) for n in PINS)
    rows = catalog['instructions']
    require(len(rows) == 176 and [r['ordinal'] for r in rows] == list(range(176)), 'catalog ordinals')
    source = b''.join(bytes.fromhex(r['bytesHex']) for r in rows)
    require(len(source) == 904 and hashlib.sha256(source).hexdigest() == interface['source904SHA256'], 'source catalog')
    require([c['id'] for c in cases['cases']] == [257, 258], 'case identity')
    # These are data records. Execution authorization remains in the parent and independent checker.
    cat = ['#ifndef C48F_CATALOG_GENERATED_H', '#define C48F_CATALOG_GENERATED_H', '#include <stdint.h>',
           '#define C48F_CATALOG_COUNT 176u',
           '#define C48F_FLOW_UNSUPPORTED 0u', '#define C48F_FLOW_SEQUENTIAL 1u',
           '#define C48F_FLOW_CONDITIONAL 2u', '#define C48F_FLOW_INDIRECT 3u', '#define C48F_FLOW_RET 4u',
           '#define C48F_COND_NONE 0u', '#define C48F_COND_A 1u', '#define C48F_COND_B 2u',
           '#define C48F_COND_BE 3u', '#define C48F_COND_NE 4u',
           '#define C48F_OPERAND_NONE 0u', '#define C48F_OPERAND_READ 1u', '#define C48F_OPERAND_WRITE 2u',
           'typedef struct C48FInstruction {',
           ' uint32_t ordinal,rva,size,caseMask,flowKind,condition,takenRva,fallthroughRva;',
           ' uint32_t operandDirection,operandWidth,operandRva; uint8_t code[15];',
           '} C48FInstruction;', 'static const C48FInstruction g_c48fInstructions[176] = {']
    flows = {'sequential': 1, 'conditional': 2, 'indirect': 3, 'ret': 4}
    conditions = {'': 0, 'CF=0 and ZF=0': 1, 'CF=1': 2, 'CF=1 or ZF=1': 3, 'ZF=0': 4}
    for r in rows:
        flow = r['flow']; active = bool(r['allowedCaseIds'])
        require(r['supported'] == active, 'supported mask')
        if active:
            require(flow['kind'] in flows and flow.get('condition', '') in conditions, 'active flow')
            require(len(r['dataOperands']) <= 1, 'active operand count')
        operand = r['dataOperands'][0] if active and r['dataOperands'] else {}
        kind = flows[flow['kind']] if active else 0
        condition = conditions[flow.get('condition', '')] if active else 0
        target = int(flow.get('takenOriginalPC', '0'), 16)
        fallthrough = int(flow.get('nextOriginalPC', flow.get('fallthroughOriginalPC', '0')), 16)
        values = [r['ordinal'], int(r['rva'], 16), r['size'],
                  sum(1 << (c - 257) for c in r['allowedCaseIds']), kind, condition,
                  target - 0x140000000 if target else 0,
                  fallthrough - 0x140000000 if fallthrough else 0,
                  {'read': 1, 'write': 2}.get(operand.get('direction'), 0),
                  operand.get('width', 0), int(operand.get('originalTargetRva', '0'), 16)]
        cat.append(' {' + ','.join(f'{v}u' for v in values) + ',' + cbytes(bytes.fromhex(r['bytesHex']), 15) + '},')
    cat.extend(['};', '#endif', ''])
    cs = ['#ifndef C48F_CASES_GENERATED_H', '#define C48F_CASES_GENERATED_H', '#include <stdint.h>',
          '#define C48F_CASE_COUNT 2u',
          f'#define C48F_SOURCE_SHA256_HEX "{interface["source904SHA256"]}"',
          f'#define C48F_CATALOG_SHA256_HEX "{PINS["catalog.v1.private.json"]}"',
          f'#define C48F_CASES_SHA256_HEX "{PINS["cases.v1.private.json"]}"',
          f'#define C48F_INTERFACE_SHA256_HEX "{PINS["interface.v1.private.json"]}"',
          f'#define C48F_EXIT_POLICY "{interface["exitPolicy"]}"',
          f'#define C48F_EXIT_CONTRACT_SHA256_HEX "{interface["exitPolicySHA256"]}"',
          f'#define C48F_EFLAGS_POLICY "{interface["eflagsPolicy"]}"',
          'typedef struct C48FControl { uint32_t rva,width; uint8_t bytes[8]; } C48FControl;',
          'typedef struct C48FStore { uint32_t ordinal,rva,headOffset,width; } C48FStore;',
          'typedef struct C48FCaseConfig {',
          ' uint32_t id,caseMask,helperCount,totalRetired,getMax,setMax,readCount,requestedReadBytes;',
          ' uint32_t controlCount,storeCount,tableRva,tailRva; uint32_t ordinals[40];',
          ' C48FControl controls[5]; C48FStore stores[3]; uint8_t initial128[128],final128[128];',
          '} C48FCaseConfig;', 'static const C48FCaseConfig g_c48fCases[2] = {']
    for c in cases['cases']:
        controls = c['controls']; stores = c['stores']; budget = c['budgets']
        require(len(controls) <= 5 and len(stores) <= 3 and len(c['instructionOrdinals']) <= 40, 'case array bound')
        require(all(rows[n]['supported'] and c['id'] in rows[n]['allowedCaseIds'] for n in c['instructionOrdinals']), 'case authorization')
        table = controls[-1]
        values = [c['id'], 1 << c['caseMaskBit'], c['expectedHelperRetired'], c['expectedTotalRetiredIncludingWrapperCALL'],
                  budget['getContextMax'], budget['setContextMax'], c['successfulInspectorReadCount'],
                  c['successfulInspectorRequestedBytes'], len(controls), len(stores), int(table['rva'], 16), table['value']]
        ctrl = ['{' + f'{int(x["rva"],16)}u,{x["width"]}u,' + cbytes(bytes.fromhex(x['bytesHex']), 8) + '}' for x in controls]
        st = ['{' + ','.join(f'{v}u' for v in (s['ordinal'], int(s['originalPC'], 16)-0x140000000, s['headOffset'], s['width'])) + '}' for s in stores]
        initial = bytes.fromhex(c['buffer']['initialHex']); final = bytes.fromhex(c['buffer']['expectedFinalHex'])
        require(len(initial) == len(final) == 128, 'known buffer width')
        cs.append(' {' + ','.join(f'{v}u' for v in values) + ',{' + ','.join(f'{n}u' for n in c['instructionOrdinals']) + '},{' + ','.join(ctrl) + '},{' + ','.join(st) + '},' + cbytes(initial, 128) + ',' + cbytes(final, 128) + '},')
    cs.extend(['};', '#endif', ''])
    output.mkdir(parents=True)
    files = {'catalog.generated.h': '\n'.join(cat).encode(), 'cases.generated.h': '\n'.join(cs).encode()}
    for name, data in files.items():
        with (output / name).open('xb') as handle:
            handle.write(data)
    manifest = {'schema': 'c48-full-helper-generated-data-v1', 'contractPins': PINS,
                'generatorSHA256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'files': {n: {'bytes': len(b), 'sha256': hashlib.sha256(b).hexdigest()} for n, b in files.items()},
                'nativeCPU': 0, 'implementedExecutor': False}
    with (output / 'generated-data.private.json').open('x', encoding='utf-8', newline='\n') as handle:
        json.dump(manifest, handle, sort_keys=True, indent=2); handle.write('\n')
    print(json.dumps(manifest['files']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    generate(parser.parse_args().output)
