"""Parent-only early ConfigSync startup adapter; fixed read-only code/DATA ledger."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import json
from pathlib import Path

from . import observe_client72_clean_loader as clean
from . import verify_sdk72_config_early_split_proxy as audit

ROOT, RAW = audit.ROOT, audit.RAW
ACCEPTED_PROOF = RAW/'owned-main1/proof.private.json'
# Parent fills these only after actual new release/locked/offline builds,
# two fresh complete 23-case Rust runs and independent review. None fails closed.
ACCEPTED_PROOF_SHA = '6ddcbb41a82fd63174b9db70a96bb0fe83dfcb8b700e7afcdd9f9789be1a79ae'
PRODUCTION_SHA = '946082a0c8bb29277ae29627d7791be9852b6d7f29fb120c36cb54f3e56a7f1a'
FIXTURE_SHA = '7f07ad5e122bb994d4bc41914ff9724642048eeb5e0cf1db7d9e86349ea6c176'
MANIFEST_SHA = '3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65'
MODULES = ('YuanShen.exe', 'Astrolabe.dll', 'Astrolabe_orig.dll')


def utc():
    return datetime.now(timezone.utc).isoformat()


def accepted_proof(path):
    audit.require(all(isinstance(value, str) and len(value) == 64
        and all(c in '0123456789abcdef' for c in value)
        for value in (ACCEPTED_PROOF_SHA, PRODUCTION_SHA, FIXTURE_SHA, MANIFEST_SHA)),
        'Split accepted artifact epoch is not bound')
    raw = audit.confined(path).read_bytes()
    audit.require(audit.digest(raw) == ACCEPTED_PROOF_SHA, 'Accepted main proof differs')
    proof = json.loads(raw)
    owned = proof['owned']
    audit.require(owned['passed'] is True and len(owned['runs']) == 2, 'Two accepted owned runs are required')
    for row in owned['runs']:
        audit.require(row['passed'] is True and row['status'] == 'owned-passed'
            and row['publicResult'] == audit.EXPECTED_OWNED and row['ownedEmitterVerified'] is True
            and audit.verify_owned_emitter(row['ownedEmitter']), 'Accepted owned result differs')
    pins = audit.frozen_sources()
    audit.require(pins == proof['frozenSourcePins'], 'Accepted frozen pin set differs')
    for name, expected in pins.items():
        audit.require(clean.sha256(ROOT/name) == expected, 'Frozen source differs')
    audit.require(audit.source_epoch() == proof['sources'], 'Accepted source epoch differs')
    audit.require(clean.sha256(ROOT/'patch/Cargo.toml') == MANIFEST_SHA, 'Accepted Cargo manifest differs')
    audit.require(proof['buildInputs'] == {name: audit.source_id(ROOT/name)
        for name in ('patch/Cargo.toml', 'patch/Cargo.lock')}, 'Accepted build epoch differs')
    audit.require(proof['definitionSHA256'] == audit.digest(audit.def_text().encode('ascii'))
        and proof['splitContract'] == audit.split_source_contract(), 'Accepted split contract differs')
    for name, expected in audit.PRIOR_PROOFS.items():
        audit.require(clean.sha256(audit.LEGACY_RAW.parent/name) == expected
            and proof['priorProofs'][name] == dict(sha256=expected), 'Accepted prior proof differs')
    fixture = audit.confined(ROOT/owned['fixture']['path'])
    audit.require(owned['fixture']['sha256'] == FIXTURE_SHA and audit.source_id(fixture) == owned['fixture'],
        'Accepted fixture differs')
    return proof


def preflight(client, artifact, proof_path=ACCEPTED_PROOF):
    """Verify installed-proxy inputs explicitly; the stock-only verifier stays frozen."""
    client = Path(client).resolve()
    proof = accepted_proof(proof_path)
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    fixed, contents = {}, {}
    audit.require(set(profile['files']) == set(clean.EXPECTED), 'Fixed profile file set differs')
    for name, row in profile['files'].items():
        accepted = proof['fixedInputs'][name]
        audit.require(row['sha256'] == clean.EXPECTED[name] == accepted['sha256']
            and row['path'] == accepted['path'] and row['size'] == accepted['bytes'], 'Fixed profile differs')
        path = client/row['path']
        if name == 'astrolabe':
            path = path.with_name('Astrolabe_orig.dll')
        raw = path.read_bytes()
        audit.require(len(raw) == row['size'] and audit.digest(raw) == row['sha256'], 'Fixed stock or alias differs')
        fixed[name] = dict(path=path.relative_to(client).as_posix(), bytes=len(raw), sha256=row['sha256'])
        if name in ('binary', 'astrolabe'):
            contents[name] = raw
    proxy = audit.confined(artifact).read_bytes()
    audit.require(audit.digest(proxy) == PRODUCTION_SHA and audit.require_artifact(proxy) == proof['artifact'],
        'Accepted production artifact differs')
    plugins = client/'YuanShen_Data/Plugins'
    audit.require((plugins/'Astrolabe.dll').read_bytes() == proxy, 'Installed proxy differs')
    exe = audit.peutil.PE(contents['binary'])
    binding = audit.binding_source(exe)
    audit.require((RAW/'binding.private.rs').read_bytes() == binding
        and audit.source_id(RAW/'binding.private.rs') == proof['bindingSource'], 'Accepted binding source differs')
    refs, reference_proof = audit.frozen_config.private_material(client)
    audit.require(refs == (RAW/'references.private.json').read_bytes()
        and reference_proof == proof['referenceProof'], 'Fresh private reference differs')
    audit.peutil.require_exports(audit.peutil.PE(contents['astrolabe']).exports(), audit.peutil.source_names())
    contract = audit.artifact_contract(proxy, contents['binary'])
    blobs = {'YuanShen.exe': contents['binary'], 'Astrolabe.dll': proxy, 'Astrolabe_orig.dll': contents['astrolabe']}
    identities = {name: dict(path=str(client/name if name == 'YuanShen.exe' else plugins/name),
        bytes=len(raw), sha256=audit.digest(raw), imageSize=audit.peutil.PE(raw).image_size)
        for name, raw in blobs.items()}
    original_rvas = [row['rva'] for row in audit.peutil.PE(contents['astrolabe']).exports()]
    inputs = dict(artifactAuditSHA256=ACCEPTED_PROOF_SHA, productionSHA256=PRODUCTION_SHA,
        fixtureSHA256=FIXTURE_SHA, manifestSHA256=MANIFEST_SHA, fixedInputs=fixed,
        installedProxy=dict(path='YuanShen_Data/Plugins/Astrolabe.dll', bytes=len(proxy), sha256=PRODUCTION_SHA),
        originalAstrolabeAlias=dict(logicalInput='astrolabe', profilePath=profile['files']['astrolabe']['path'],
            actualPath=fixed['astrolabe']['path'], sha256=fixed['astrolabe']['sha256']),
        frozenSourcePins=proof['frozenSourcePins'], sources=proof['sources'], bindingSource=proof['bindingSource'],
        referenceProof=reference_proof, referenceBytesEqual=True,
        profileSHA256=clean.sha256(ROOT/'protocol/cn-7.2/profile.json'))
    return inputs, identities, contract, original_rvas


class ReadOnlyBackend(clean.ModuleBackend):
    def __init__(self):
        super().__init__()
        self.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        self.k.ReadProcessMemory.restype = W.BOOL

    def open(self, pid):
        return self.k.OpenProcess(clean.PROCESS_READ_QUERY_WAIT, False, pid)

    def read(self, handle, address, width):
        output = C.create_string_buffer(width)
        count = C.c_size_t()
        ok = self.k.ReadProcessMemory(handle, address, output, width, C.byref(count))
        error = None if ok and count.value == width else dict(kind='read-failed', winerror=C.get_last_error() if not ok else None)
        return output.raw[:count.value], error


def process_metadata(backend, handle, pid):
    return dict(timeUtc=utc(), status=backend.status(handle), creationTime=backend.creation_time(handle),
        image=backend.image(handle), modules=backend.snapshot(8|16, pid, clean.MODULEENTRY32W, 'Module32FirstW', 'Module32NextW'))


def identity_failure(metadata, creation, client, prior, identities):
    if not metadata['status']['alive']:
        return 'target-exited'
    if metadata['creationTime'] != creation or creation['filetime'] is None or creation['winerror'] is not None:
        return 'process-creation-differs'
    if not Path(metadata['image']).samefile(client/'YuanShen.exe'):
        return 'process-image-differs'
    current = metadata['modules']
    if current['error'] is not None or prior['error'] is not None:
        return 'module-enumeration-failed'
    for name in MODULES:
        rows = [row for row in current['items'] if row['name'].casefold() == name.casefold()]
        old = [row for row in prior['items'] if row['name'].casefold() == name.casefold()]
        if len(rows) != 1 or len(old) != 1:
            return 'module-not-unique'
        row, previous, wanted = rows[0], old[0], identities[name]
        if not (Path(row['path']).samefile(wanted['path']) and Path(previous['path']).samefile(wanted['path'])
            and row['base'] == previous['base'] and row['size'] == previous['size'] == wanted['imageSize']
            and Path(row['path']).stat().st_size == wanted['bytes'] and clean.sha256(Path(row['path'])) == wanted['sha256']):
            return 'module-binding-differs'
    return None


def inspect_process(backend, client, evidence, identities, contract, original_rvas, raw_rows):
    """Only collect's same <=9 reads supply raw bytes; metadata adds no RPM."""
    result = dict(status='partial', d02Passed=False)
    if not evidence['finalStatus']['alive']:
        result['reason'] = 'target-exited'
        return result
    result['openUTC'] = utc()
    handle = backend.open(evidence['pid'])
    if not handle:
        result['reason'] = 'process-unavailable'
        return result
    requested = 0
    try:
        before = process_metadata(backend, handle, evidence['pid'])
        result['identityBefore'] = before
        reason = identity_failure(before, evidence['processCreationTime'], client,
            evidence['samples'][-1]['modules'], identities)
        def read(address, width):
            nonlocal requested
            audit.require(len(raw_rows) < audit.MAX_READS and 0 < width <= audit.MAX_SINGLE
                and requested+width <= audit.MAX_BYTES, 'Raw read budget exceeded')
            requested += width
            begin = utc()
            try:
                data, error = backend.read(handle, address, width)
            except OSError as exc:
                data, error = b'', dict(kind='os-read-failed', winerror=getattr(exc, 'winerror', None))
            raw_rows.append(dict(ordinal=len(raw_rows)+1, address=address, requested=width, bytesRead=len(data),
                error=error, bytesHex=data.hex(), beginUTC=begin, endUTC=utc()))
            return data, error

        if reason:
            result['reason'] = 'start-'+reason
        else:
            modules = {name: next(row for row in before['modules']['items'] if row['name'].casefold() == name.casefold())
                for name in MODULES}
            try:
                result.update(audit.collect(read, modules, contract, original_rvas))
            except (OSError, ValueError, KeyError, IndexError, TypeError):
                result.update(status='partial', reason='fixed-collection-failure')
        after = process_metadata(backend, handle, evidence['pid'])
        result['identityAfter'] = after
        reason = identity_failure(after, evidence['processCreationTime'], client, before['modules'], identities)
        if reason:
            result.update(status='partial', reason='end-'+reason)
        result['statusAfterValidation'] = backend.status(handle)
        if not result['statusAfterValidation']['alive']:
            result.update(status='partial', reason='end-target-exited')
        return result
    except (OSError, ValueError, KeyError, IndexError, TypeError):
        result.update(status='partial', reason='fixed-identity-failure')
        return result
    finally:
        backend.close(handle)
        result['closeUTC'] = utc()
        result['rawBudget'] = dict(reads=len(raw_rows), bytes=requested, maxReads=audit.MAX_READS,
            maxBytes=audit.MAX_BYTES, maxSingle=audit.MAX_SINGLE)


def output_paths(path):
    output = audit.confined(path)
    raw = output.with_name(output.name+'.reads.private.json')
    audit.require(not output.exists() and not raw.exists(), 'Preserve earlier evidence; choose fresh outputs')
    return output, raw


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--production', required=True, type=Path)
    parser.add_argument('--artifact-audit', type=Path, default=ACCEPTED_PROOF)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--user-ready', action='store_true')
    args = parser.parse_args(argv)
    if not args.user_ready:
        parser.error('New user-ready is required for this visible startup')
    output, raw_output = output_paths(args.output)
    result = dict(scope='Early EXE ConfigSync fixed code/72B DATA only', sourceSHA256=clean.sha256(Path(__file__)),
        startUtc=utc(), status='preflight', rawLedger=raw_output.relative_to(ROOT).as_posix(), d02Passed=False)
    raw_rows = []
    try:
        inputs, identities, contract, original_rvas = preflight(args.client, args.production, args.artifact_audit)
        result['inputs'] = inputs
        backend = ReadOnlyBackend()
        # observe itself has no stock-file gate; preflight above owns installed-state verification.
        clean.observe(backend, args.client.resolve(), result, 90)
        result['binding'] = inspect_process(backend, args.client.resolve(), result, identities, contract, original_rvas, raw_rows)
        result['status'] = 'completed' if result['binding']['status'] == 'installed-early-code-bound' else 'partial'
    except (OSError, RuntimeError, ValueError, KeyError, IndexError, TypeError):
        result.update(status='partial', reason='fixed-preflight-or-collection-failure')
    finally:
        result['endUtc'] = utc()
        output.parent.mkdir(parents=True, exist_ok=True)
        with raw_output.open('xb') as stream:
            stream.write(audit.json_bytes(dict(schema=1, scope='same fixed reads; no managed payload/reference reads', reads=raw_rows)))
        with output.open('xb') as stream:
            stream.write(audit.json_bytes(result))
    print(json.dumps(dict(status=result['status'], event=result.get('binding', {}).get('event'), d02Passed=False)))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
