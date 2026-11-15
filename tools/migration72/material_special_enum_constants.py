"""Recover two pinned enum literal sets from verified offline client files."""
import hashlib
import json
import pickle
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPE = ROOT / 'debug/d02-skill-energy-search-20261116/post-depot-gap/enum-constants-scope.private.json'
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/material-special-enum-constants/run2'
SCOPE_SHA = 'fe0925f0a32da834eabb2c54d31db16006fde7554a1138dd6a03eaf879a9e54c'
TASK = 'R3-MATERIAL-SPECIAL-ENUM-CONSTANTS-STATIC-20261116'


def main():
    OUT.mkdir(parents=True, exist_ok=False)
    ledger = []
    partial = []
    phase = 'scope'
    try:
        scope_bytes = SCOPE.read_bytes()
        ledger.append({'source': 'scope', 'bytes': len(scope_bytes), 'sha256': hashlib.sha256(scope_bytes).hexdigest()})
        require(len(scope_bytes) == 10048 and ledger[-1]['sha256'] == SCOPE_SHA, 'scope pin mismatch')
        scope = json.loads(scope_bytes)
        require(scope['executionTask'] == TASK, 'scope task mismatch')
        phase = 'source pins'
        fixed = {}
        for name, pin in scope['pins'].items():
            data = (ROOT / pin['path']).read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            ledger.append({'source': name, 'path': pin['path'], 'bytes': len(data), 'sha256': digest})
            require(len(data) == pin['bytes'] and digest == pin['sha256'], f'{name} pin mismatch')
            fixed[name] = data
        phase = 'binary pins'
        game = Path(subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / scope['pins']['portableResolver']['path'])], check=True, capture_output=True, text=True).stdout.strip())
        binaries = {}
        for name, pin in scope['binaryPins'].items():
            data = (game / name).read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            ledger.append({'source': name, 'bytes': len(data), 'sha256': digest, 'fullReads': 1})
            require(len(data) == pin['bytes'] and digest == pin['sha256'], f'{name} pin mismatch')
            binaries[name] = data
        binary = binaries['YuanShen.exe']
        metadata = binaries['YuanShen_Data/Managed/Metadata/global-metadata.dat']
        phase = 'declaration identity'
        records, _ = pickle.loads(fixed['metadataCache'])
        declaration = json.loads(fixed['materialDeclaration'])
        special = json.loads(fixed['specialDeclaration'])
        control = json.loads(fixed['old74813Control'])
        require(isinstance(control['enumMap'], list) and len(control['enumMap']) > 0, 'old enumMap control missing')
        targets = scope['targets']
        material = next(x for x in declaration['targets'] if x['index'] == 34165)
        declared = {34165: [x['field'] for x in material['fields']], 61184: special['enum61184']['fields']}
        for target in targets:
            record = records[target['typeDefinition']]
            fields = record['fields']
            require(record['index'] == target['typeDefinition'] and record['name'] == target['clientType'], 'enum identity mismatch')
            require(record['parent']['namespace'] == 'System' and record['parent']['name'] == 'Enum', 'enum parent mismatch')
            require(fields == declared[record['index']], 'declaration fields mismatch')
            require([f['index'] for f in fields[:-1]] == target['literalFieldIndices'], 'literal indices mismatch')
            require(len(fields[:-1]) == target['literalCount'] and fields[-1] == target['underlyingField'], 'underlying/count mismatch')
            if 'names' in target:
                require([f['name'] for f in fields[:-1]] == target['names'], 'special names mismatch')
        phase = 'layout and recovery'
        sys.path.insert(0, str(ROOT))
        from tools import extract_client72 as core
        from tools import extract_client72_enums as enums
        # Count numerical PE reads made by the existing implementation and the layout gate.
        original_number = core.number
        pe_reads = []
        def tracked_number(data, offset, fmt='I'):
            if data is binary:
                import struct
                pe_reads.append({'offset': offset, 'bytes': struct.calcsize('<' + fmt)})
            return original_number(data, offset, fmt)
        core.number = enums.number = tracked_number
        pe = core.PE(binary)
        h = pe.offset(core.HEADER_VA)
        layout = {'base': core.BODY + ((core.number(binary, h + 0x1d8) + 0xde66f77a) & core.MASK32),
                  'size': core.number(binary, h + 0x1f4) ^ 0x159a5178,
                  'data': core.BODY + ((core.number(binary, h + 0x1cc) + 0x9c0bd319) & core.MASK32)}
        require(layout == json.loads(fixed['admittedLayout']) == scope['sourceMechanism']['admittedLayout'], 'layout mismatch')
        core.span(metadata, layout['base'], layout['size'])
        core.span(metadata, layout['data'], 0)
        require(layout['size'] == 915840 and layout['size'] % 12 == 0, 'table shape mismatch')
        result = enums.recover(binary, metadata, records, indices=(34165, 61184))
        require(result['defaultTable'] == hex(layout['base']) and result['defaultTableBytes'] == layout['size'] and result['defaultData'] == hex(layout['data']), 'recover layout mismatch')
        # PE parser also directly inspects signatures and section triples; bound those reads conservatively.
        pe_semantic_upper = sum(x['bytes'] for x in pe_reads) + 2 * (6 + 12 * len(pe.sections))
        require(pe_semantic_upper <= scope['budget']['peSemanticHeaderTypeReferenceBytesCap'], 'PE semantic budget exceeded')
        phase = 'literal comparison'
        selected = []
        for enum, target in zip(result['enums'], targets):
            require(enum['typeIndex'] == target['typeDefinition'] and enum['clientType'] == target['clientType'] and enum['underlyingType'] == 'int32', 'recovered enum mismatch')
            require([v['fieldIndex'] for v in enum['values']] == target['literalFieldIndices'], 'recovered literal indices mismatch')
            output_values = []
            for value in enum['values']:
                offset = layout['data'] + value['dataIndex']
                require(value['dataIndex'] >= 0, 'negative literal data index')
                core.span(metadata, offset, 4)
                raw = metadata[offset:offset + 4]
                numeric = int.from_bytes(raw, 'little', signed=True)
                require(value['value'] == numeric, 'literal numeric comparison mismatch')
                output_values.append({'name': value['symbol'], 'fieldIndex': value['fieldIndex'], 'presence': True,
                    'value': numeric, 'raw4B': raw.hex(), 'dataIndex': value['dataIndex'],
                    'dataSpan': {'offset': offset, 'bytes': 4}, 'defaultRow': value['defaultRow'],
                    'defaultRowSpan': {'offset': layout['base'] + value['defaultRow'] * 12, 'bytes': 12},
                    'typeRef': value['typeIndex']})
                partial.append({'typeDefinition': enum['typeIndex'], 'fieldIndex': value['fieldIndex']})
            require(len(output_values) == target['literalCount'], 'literal count mismatch')
            enum_output = {'typeDefinition': enum['typeIndex'], 'clientType': enum['clientType'],
                'underlying': target['underlyingField'], 'values': output_values}
            selected.append(enum_output)
        require(len(partial) == 97, 'total literal count mismatch')
        payload = {'task': TASK, 'scope': 'explicit enum literals only; no resource rows or Java projection', 'enums': selected}
        manifest = {'task': TASK, 'inputs': ledger, 'layout': layout, 'defaultRows': layout['size'] // 12,
            'tableStructuralBytes': layout['size'], 'literalCount': len(partial), 'uniqueLiteralNumericBytes': len(partial) * 4,
            'numericAccesses': 'recover reads each 4B once; independent int.from_bytes rereads the same in-memory 4B',
            'peNumberReadCalls': len(pe_reads), 'peNumberReadBytes': sum(x['bytes'] for x in pe_reads),
            'peSemanticBytesConservativeUpperBound': pe_semantic_upper,
            'old74813Control': {'enumMapEntries': len(control['enumMap']), 'readOnly': True},
            'binaryFullReads': 2, 'startupMetadataRead': False,
            'runHistory': {'sourceOnlyPreparationCacheReads': 1, 'run1CacheReads': 1, 'run1BinaryReads': 0,
                'run1FailureSha256': 'd946607bce45bd183645977ba7fda74b58b0612eec1893f62b2f8a3d652ecaf3',
                'run1DriverSha256': '0ebe7a243b611c04839469a64e18fabb7f51a80c3fe2914aaafbb03c1dc182b2',
                'run1Stderr': 'not retained; later same-environment diagnostic showed PowerShell script execution policy rejection',
                'priorIncorrectAttribution': 'missing Astrolabe was inferred without evidence and withdrawn',
                'diagnosticSha256': '6d74c957d78213b72c8ac96b2093fdb40d987b1368d0cea37f96b17309320b99'},
            'ioScope': 'Application reads recorded; import/resolver/OS loader IO is not fully measured.',
            'resultSha256': None}
        result_bytes = (json.dumps(payload, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        manifest['resultSha256'] = hashlib.sha256(result_bytes).hexdigest()
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        require(len(result_bytes) + len(manifest_bytes) <= scope['budget']['combinedOutputBytes'], 'output budget exceeded')
        (OUT / 'result.private.json').write_bytes(result_bytes)
        (OUT / 'manifest.private.json').write_bytes(manifest_bytes)
        print(json.dumps({'literalCount': len(partial), 'nonzero': sum(v['value'] != 0 for e in selected for v in e['values']),
            'special': {v['name']: v['value'] for v in selected[1]['values']},
            'resultSha256': manifest['resultSha256'], 'manifestSha256': hashlib.sha256(manifest_bytes).hexdigest(),
            'outputBytes': len(result_bytes) + len(manifest_bytes), 'peSemanticUpper': pe_semantic_upper}))
    except Exception as exc:
        failure = {'task': TASK, 'phase': phase, 'errorType': type(exc).__name__, 'error': str(exc),
                   'inputsRead': ledger, 'partial': partial}
        (OUT / 'failure.private.json').write_text(json.dumps(failure, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        raise


def require(condition, message):
    if not condition:
        raise ValueError(message)


if __name__ == '__main__':
    main()
