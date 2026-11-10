"""Original-process B adapter. All String raw stays pending through outer gates."""
import ntpath

from . import client72_sdk_managed_route_inputs as core
from . import observe_client72_uninstrumented as monitor

TOOLS = ('tools/client72_sdk_managed_route_inputs.py', 'tools/observe_client72_sdk_managed_route_inputs.py')


class PendingObservation:
    def __init__(self):
        self.capture = None
        self.report = {'schema': 'client72-managed-route-adapter-v1', 'status': 'partial', 'outcome': 'entry-refused',
            'identityCallbacks': 0, 'sourceChecks': 0, 'endIdentityVerified': False, 'sourceFinalVerified': False,
            'identityReceipts': [],
            'newThreadAPIs': 0, 'newOpenProcessAPIs': 0, 'handlesClosed': 0,
            'successNativeFormula': '14 + M_before + M_after', 'nativeCallHardCapClaimed': False,
            'liveApproved': False, 'nativeCPUExecuted': False}

    def __repr__(self):
        return '<PendingManagedRouteObservation: memory-only>'

    def seal(self, outer_ok=False):
        result = dict(self.report)
        accepted = (outer_ok is True and result['endIdentityVerified'] and result['sourceFinalVerified']
                    and result['outcome'] == 'checked')
        if self.capture is not None:
            result['collection'] = self.capture.seal(accepted)
            result['status'] = result['collection']['status']
        result['outerFinalVerified'] = outer_ok is True
        return result


def prepare(backend, epoch, identity, *, now=core.utc, source_check=core.verify_sources):
    pending = PendingObservation()
    report = pending.report
    report['beginUTC'] = now()
    try:
        _entry(backend, epoch, identity)
        report.update(epochId=epoch['epochId'], implementationPins=dict(epoch['implementationPins']),
            identityExpected={key:epoch[key] for key in ('pid','image','processCreationTime','module')})
        _source(epoch,source_check,report)
        _identity(backend, epoch, identity, report,now)
    except Exception:
        report.update(outcome='before-gate-failed', endUTC=now())
        return pending
    try:
        pending.capture = core.collect(lambda address,width: backend.read(backend.primary['processHandle'],address,width),
                                      epoch['module']['base'], now=now)
        _identity(backend, epoch, identity, report,now)
        report['endIdentityVerified'] = True
        _source(epoch,source_check,report)
        report.update(sourceFinalVerified=True, outcome='checked')
    except Exception:
        # Never expose repr/str of callbacks or provisional String failures.
        report['outcome'] = 'after-gate-failed'
    report['endUTC'] = now()
    return pending


def _entry(backend, epoch, identity):
    primary = backend.primary
    if (primary is None or primary['source'] != 'launch-primary-thread' or primary['creationFlags'] != 0
            or epoch['pid'] != primary['pid'] or not callable(identity) or not epoch['epochId']
            or epoch['stockInputPins'] != monitor.EXPECTED
            or type(epoch['processCreationTime']) is not int or not 0 < epoch['processCreationTime'] <= core.U64):
        raise ValueError('entry')
    norm = lambda value: ntpath.normcase(ntpath.normpath(value))
    module = epoch['module']
    if (norm(epoch['image']) != norm(primary['target']) or norm(module['path']) != norm(primary['target'])
            or module['name'].casefold() != 'yuanshen.exe' or type(module['base']) is not int
            or not 0 < module['base'] <= core.U64-0x5893d58 or type(module['size']) is not int
            or module['size'] < 0x5893d58):
        raise ValueError('module')


def _source(epoch,callback,report):
    report['sourceChecks'] += 1
    expected = {path: core.file_sha(core.ROOT/path) for path in TOOLS}
    if epoch['implementationPins'] != expected or callback().get('passed') is not True:
        raise ValueError('source')


def _identity(backend, epoch, callback, report, now):
    report['identityCallbacks'] += 1
    row={'ordinal':report['identityCallbacks'],'beginUTC':now(),'matched':False}
    report['identityReceipts'].append(row)
    try:
        observed = callback(backend.primary['processHandle'],backend.primary['pid'])
    finally:
        row['endUTC']=now()
    expected = {key: epoch[key] for key in ('pid','image','processCreationTime','module')}
    if observed != expected:
        raise ValueError('identity')
    row['matched']=True
