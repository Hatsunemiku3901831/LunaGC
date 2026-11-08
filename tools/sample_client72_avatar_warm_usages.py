"""Read-only warm usage sampler for one fresh, explicitly observed proxy epoch."""
import argparse
import hashlib
import json
from pathlib import Path

from . import collect72_avatar_warm_usages as warm
from . import observe_client72_config_early_proxy as early

ROOT = Path(__file__).resolve().parents[1]
RAW = warm.RAW/'live'
SOURCE_PINS = {
    'tools/collect72_avatar_warm_usages.py': '667fa5948f1d645feb923f5b166efa7bb1667cb12ad6b8135d2911bffd4e26b7',
    'tools/observe_client72_config_early_proxy.py': 'd2f9a6f0a8d69e6eacba65c838fd9f25bfe4c12167000349dbc05a162ab5702b',
}


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def source_gate(client, production, observation):
    if len(SOURCE_PINS) != 2:
        raise ValueError('The reviewed sampling source epoch is not frozen')
    for path, expected in SOURCE_PINS.items():
        if digest((ROOT/path).read_bytes()) != expected:
            raise ValueError('Reviewed sampling source differs')
    raw = observation.read_bytes()
    evidence = json.loads(raw)
    if (evidence.get('elapsedSeconds', 0) < 90 or not evidence['finalStatus']['alive']
            or not evidence.get('samples') or not evidence.get('pid')
            or evidence['sourceSHA256'] != SOURCE_PINS['tools/observe_client72_config_early_proxy.py']):
        raise ValueError('A fresh completed 90-second early-proxy monitor is required')
    inputs, identities, _, _ = early.preflight(client, production)
    if inputs != evidence['inputs']:
        raise ValueError('Fresh installed-proxy inputs differ from monitor')
    initial = json.loads((ROOT/'local/migration72/d02-resume-20261109/initial-inventory.private.json').read_bytes())
    protected = []
    for row in initial['protectedFiles']:
        actual = digest(Path(row['path']).read_bytes())
        if actual != row['sha256']:
            raise ValueError('Protected file differs')
        protected.append(dict(path=row['path'], sha256=actual))
    if (ROOT/'.env.local').exists() != initial['envLocalExists']:
        raise ValueError('Configuration presence differs')
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    binary, metadata, startup = [(client/profile['files'][name]['path']).read_bytes()
                                 for name in ('binary', 'metadata', 'startup')]
    cache_pins, _, _ = warm.cache.verify_sources(binary, metadata)
    static = warm.verify_sources(binary, metadata, startup, verified_sources=cache_pins)
    return evidence, identities, dict(inputChecks=inputs, protectedFiles=protected,
        sourceObservationSHA256=digest(raw), sourceChecks=static,
        sourceSHA256=digest(Path(__file__).read_bytes()), verifiedSources=SOURCE_PINS)


def sample(backend, client, evidence, identities, *, collector=None):
    """No launch, hook, remote call or write; all raw bytes are the same backend reads."""
    collect = collector or warm.collect
    result = dict(task='R1-AVATAR-WARM-USAGES-live', status='partial', backendRecords=[],
        endIdentityVerified=False, naturalCallerObserved=False, completeAvatarHandler=False,
        d02Complete=False, openedUTC=early.utc())
    if not evidence['finalStatus']['alive']:
        result['reason'] = 'target-exited-before-open'
        return result
    handle = backend.open(evidence['pid'])
    if not handle:
        result['reason'] = 'process-unavailable'
        return result
    requested = 0
    try:
        before = early.process_metadata(backend, handle, evidence['pid'])
        result['identityBefore'] = before
        reason = early.identity_failure(before, evidence['processCreationTime'], client,
            evidence['samples'][-1]['modules'], identities)

        def read(address, width):
            nonlocal requested
            if (len(result['backendRecords']) >= warm.MAX_READS or not 0 < width <= warm.MAX_SINGLE
                    or requested+width > warm.MAX_BYTES):
                raise ValueError('Actual backend read budget exceeded')
            requested += width
            begin = early.utc()
            try:
                raw, error = backend.read(handle, address, width)
            except OSError as failure:
                code = getattr(failure, 'winerror', None)
                raw, error = b'', dict(kind='read-os-error', winerror=code if code is not None else failure.errno)
            result['backendRecords'].append(dict(ordinal=len(result['backendRecords'])+1,
                address=address, requested=width, bytesRead=len(raw), bytesHex=raw.hex(), error=error,
                beginUTC=begin, endUTC=early.utc()))
            return raw, error

        if reason:
            result['reason'] = 'start-'+reason
        else:
            main = next(row for row in before['modules']['items'] if row['name'].casefold() == 'yuanshen.exe')
            result['mainModule'] = main
            try:
                collected = collect(read, main['base'])
                result['collection'] = collected
                result['status'] = collected['status']
            except (OSError, ValueError, KeyError, IndexError):
                result.update(status='partial', reason='fixed-collection-failure')
        after = early.process_metadata(backend, handle, evidence['pid'])
        result['identityAfter'] = after
        reason = early.identity_failure(after, evidence['processCreationTime'], client, before['modules'], identities)
        # Repeat the lifetime metadata after identity's file-hash work; no extra RPM.
        result['finalStatus'] = backend.status(handle)
        if reason or not result['finalStatus']['alive']:
            result.update(status='partial', reason='end-'+(reason or 'target-exited'))
        else:
            result['endIdentityVerified'] = True
        return result
    except (OSError, ValueError, KeyError, IndexError):
        result.update(status='partial', reason='fixed-identity-failure')
        return result
    finally:
        backend.close(handle)
        result.update(closedUTC=early.utc(), backendBudget=dict(reads=len(result['backendRecords']),
            requestedBytes=requested, maxReads=warm.MAX_READS, maxBytes=warm.MAX_BYTES,
            maxSingleRead=warm.MAX_SINGLE), pid=evidence['pid'], creation=evidence['processCreationTime'])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--production', type=Path, required=True)
    parser.add_argument('--observation', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--user-ready', action='store_true')
    args = parser.parse_args(argv)
    if not args.user_ready:
        parser.error('A new real-time readiness reply is required for this prepared experiment')
    output = args.output.resolve()
    snapshot = output.with_suffix('.source.py')
    if not output.is_relative_to(RAW.resolve()) or output.exists() or snapshot.exists():
        parser.error('A fresh output in the warm-usage live directory is required')
    output.parent.mkdir(parents=True, exist_ok=True)
    snapshot.write_bytes(Path(__file__).read_bytes())
    result = dict(task='R1-AVATAR-WARM-USAGES-live', status='partial', d02Complete=False,
        sourceSHA256=digest(Path(__file__).read_bytes()))
    try:
        evidence, identities, source = source_gate(args.client.resolve(), args.production.resolve(), args.observation.resolve())
        result.update(sample(early.ReadOnlyBackend(), args.client.resolve(), evidence, identities), **source)
    except (OSError, ValueError, KeyError, IndexError):
        result.update(status='partial', reason='fixed-source-gate-failure')
    with output.open('x', encoding='utf8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status=result['status'], reads=result.get('backendBudget', {}).get('reads'),
        bytes=result.get('backendBudget', {}).get('requestedBytes'), d02Complete=False)))
    return 0 if result['status'] == 'completed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
