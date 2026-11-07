"""Read a bounded initialized-type graph from an owned, uninstrumented client."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

try:
    from .observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256
except ImportError:
    from observe_client72_uninstrumented import ROOT, WindowsBackend, MODULEENTRY32W, verified_inputs, sha256


def collect(read, base, scope='all'):
    records = []

    def get(label, address, size):
        data, error = read(address, size)
        records.append(dict(label=label, address=address, length=size,
                            timeUtc=datetime.now(timezone.utc).isoformat(), error=error,
                            bytesHex=data.hex(), sha256=hashlib.sha256(data).hexdigest()))
        return data if error is None and len(data) == size else None

    def pointer(label, address):
        data = get(label, address, 8)
        return int.from_bytes(data, 'little') if data is not None else None

    def filename(label, address):
        data = bytearray(); error = None
        for offset in range(256):
            part, error = read(address + offset, 1)
            data.extend(part)
            if error is not None or len(part) != 1 or part == b'\0':
                break
        records.append(dict(label=label, address=address, length=len(data),
                            timeUtc=datetime.now(timezone.utc).isoformat(), error=error,
                            bytesHex=data.hex(), sha256=hashlib.sha256(data).hexdigest(),
                            readUnitBytes=1, terminated=data.endswith(b'\0')))

    def klass(label, address):
        if not address:
            return
        get(label + '.type', address + 0x10, 16)
        get(label + '.initialized', address + 0xc8, 1)

    def context(label, method):
        if not method:
            return None
        data = get(label + '.method', method, 16)
        if data is None:
            return None
        owner = int.from_bytes(data[:8], 'little')
        klass(label + '.class', owner)
        return pointer(label + '.rgctx', owner + 0x30) if owner else None

    if scope == 'archive-state':
        table = pointer('archiveDecodeTable.cell', base + 0x5eed350)
        if table:
            get('archiveDecodeTable.bytes', table, 256)
        return dict(reads=records, atomicSnapshot=False, archiveStateObserved=True,
                    archiveDecodeExecuted=False, targetProtocolReady=False)

    option = pointer('options.cell', base + 0x5bc8ef0)
    options = get('options.record', option, 8) if option else None
    canonical = pointer('canonicalEnum.cell', base + 0x5bc9638)
    klass('canonicalEnum.class', canonical)
    method = pointer('enumContext.cell', base + 0x5a30968)
    ctx = context('enumContext', method)
    getter = ctor = descriptor = None
    if ctx:
        getter = pointer('enumContext.getterSlot', ctx + 0x10)
        ctor = pointer('enumContext.ctorSlot', ctx + 0x18)
    ctor_data = get('enumConstructor.method', ctor, 16) if ctor else None
    getter_context = context('comparerGetter', getter)
    comparer = None
    if getter_context:
        comparer = pointer('comparer.classSlot', getter_context + 0x10)
        typed = pointer('comparer.typeSlot', getter_context + 0x18)
        descriptor = get('comparer.typeDescriptor', typed, 16) if typed else None
    klass('comparer.class', comparer)
    static = pointer('comparer.staticArea', comparer + 0x38) if comparer else None
    default = pointer('comparer.default', static) if static else None
    default_class = pointer('comparer.defaultObjectClass', default) if default else None
    klass('comparer.defaultObject', default_class)
    cache = pointer('runtimeTypeCache.cell', base + 0x5bc9068)
    header = get('runtimeTypeCache.header', cache, 0x68) if cache else None
    descriptor_source = 'getter-context' if descriptor is not None else None
    if descriptor is None:
        descriptor = get('registeredEnumDescriptor', base + 0x2f1e560, 16)
        descriptor_source = 'registered-file-descriptor' if descriptor is not None else None
    cache_result = 'header-not-readable-or-not-initialized' if header is None else 'table-readable-descriptor-not-sampled'
    if header is not None and descriptor is not None:
        type_id = int.from_bytes(descriptor[:4], 'little', signed=True)
        flags = int.from_bytes(descriptor[8:12], 'little')
        if type_id == 20060 and (flags >> 16) & 255 == 17:
            by_ref = (flags >> 30) & 1
            hashed = ((17 * 0x1cfaa2db + by_ref) * 0x1cfaa2db + type_id) & ((1 << 64) - 1)
            mask = int.from_bytes(header[8:16], 'little')
            blocks = int.from_bytes(header[0x28:0x30], 'little')
            values = int.from_bytes(header[0x60:0x68], 'little')
            bucket = hashed & mask
            block = get('runtimeTypeCache.firstBlock', blocks + (bucket >> 6) * 32, 32) if blocks else None
            if block is not None:
                occupied = int.from_bytes(block[8:16], 'little')
                bit = 1 << (bucket & 63)
                cache_result = 'first-bucket-not-occupied'
                if occupied & bit:
                    packed = int.from_bytes(block[:8], 'little')
                    rank = (occupied & (bit - 1)).bit_count()
                    entry = get('runtimeTypeCache.firstEntry', packed + rank * 16, 16) if packed else None
                    if entry is not None:
                        key = int.from_bytes(entry[:8], 'little')
                        get('runtimeTypeCache.firstKey', key, 16) if key else None
                        index = int.from_bytes(entry[8:16], 'little')
                        value = pointer('runtimeTypeCache.firstValue', values + index * 8) if values else None
                        runtime_object = get('runtimeTypeCache.firstRuntimeType', value, 0x20) if value else None
                        if runtime_object is not None:
                            klass('runtimeTypeCache.objectClass', int.from_bytes(runtime_object[:8], 'little'))
                        cache_result = 'first-candidate-recorded-not-an-exhaustive-lookup'
        else:
            cache_result = 'descriptor-not-confirmed-as-enum20060'
    class_table = pointer('enumClassCache.cell', base + 0x5bc8f18)
    enum_class = pointer('enumClassCache.entry20060', class_table + 20060 * 8) if class_table else None
    klass('enumClassCache.enum', enum_class)
    underlying_offset = None
    if enum_class:
        get('enumClassCache.enum.flagsCA', enum_class + 0xca, 1)
        offset_bytes = get('enumClassCache.enum.underlyingOffset', enum_class + 0xac, 4)
        underlying_offset = int.from_bytes(offset_bytes, 'little') if offset_bytes is not None else None
    registry_slot = pointer('underlyingRegistry.cell', base + 0x5bc8ad0)
    registry_base = pointer('underlyingRegistry.base', registry_slot) if registry_slot else None
    if underlying_offset and registry_base:
        klass('enumClassCache.underlying', registry_base + underlying_offset)
    router = pointer('fileRouter.cell', base + 0x5756990)
    router_header = get('fileRouter.header', router, 0x90) if router else None
    providers = []
    provider_count = None
    if router_header is not None:
        providers.append(('default', int.from_bytes(router_header[:8], 'little')))
        array = int.from_bytes(router_header[0x20:0x28], 'little')
        provider_count = int.from_bytes(router_header[0x30:0x38], 'little')
        if array and 0 < provider_count <= 64:
            count = min(provider_count, 4)
            tail = get('fileRouter.registeredTail', array + 8 * (provider_count - count), count * 8)
            if tail is not None:
                providers += [(f'registered-{provider_count-1-index}', int.from_bytes(tail[(count-1-index)*8:(count-index)*8], 'little'))
                              for index in range(count)]
        providers.append(('special', int.from_bytes(router_header[0x88:0x90], 'little')))
    for label, provider in providers:
        obj = get('fileRouter.' + label + '.object', provider, 0x10) if provider else None
        vtable = int.from_bytes(obj[:8], 'little') if obj else None
        methods = get('fileRouter.' + label + '.vtable', vtable, 0x90) if vtable else None
        if methods is not None:
            for offset, role in ((8, 'open'), (0x10, 'read'), (0x60, 'acceptPath')):
                address = int.from_bytes(methods[offset:offset+8], 'little')
                get('fileRouter.' + label + '.' + role + '.code', address, 32) if address else None
        if vtable == base + 0x220ba70:
            string = get('fileRouter.' + label + '.prefixHeader', provider + 0x30, 0x28)
            if string is not None:
                string_ptr = int.from_bytes(string[:8], 'little')
                string_len = int.from_bytes(string[0x18:0x20], 'little')
                if string_ptr and 0 < string_len <= 96:
                    get('fileRouter.' + label + '.prefixBytes', string_ptr, string_len)
    file_cache = pointer('fileCache.cell', base + 0x5756980)
    if file_cache:
        get('fileCache.routeField558', file_cache + 0x558, 8)
    get('resourceSelection.byte', base + 0x5766598, 1)
    get('resourceSelection.onceGuard', base + 0x576659c, 4)
    new_manager = get('newResourceManager.object', base + 0x55bc4b8, 0x20)
    if new_manager is not None:
        state = int.from_bytes(new_manager[8:16], 'little')
        if state:
            state_header = get('newResourceManager.state', state, 0x68)
            if state_header is not None:
                sentinel = int.from_bytes(state_header[0x10:0x18], 'little')
                count = int.from_bytes(state_header[0x18:0x20], 'little')
                sentinel_header = get('newResourceManager.sentinel', sentinel, 0x10) if sentinel and count else None
                node = int.from_bytes(sentinel_header[:8], 'little') if sentinel_header else None
                for index in range(min(count, 4)):
                    if not node or node == sentinel:
                        break
                    entry = get(f'newResourceManager.node{index}', node, 0x30)
                    if entry is None:
                        break
                    file_id = int.from_bytes(entry[0x10:0x14], 'little')
                    if file_id != 0xffffffff:
                        descriptor_address = base + 0x5756ec0 + (0xc0 if file_id & 0x80000000 else 0x48)
                        descriptor = get(f'newResourceManager.node{index}.descriptor', descriptor_address, 0x30)
                        if descriptor is not None:
                            array = int.from_bytes(descriptor[0x10:0x18], 'little')
                            map_size = int.from_bytes(descriptor[0x18:0x20], 'little')
                            start = int.from_bytes(descriptor[0x20:0x28], 'little')
                            size = int.from_bytes(descriptor[0x28:0x30], 'little')
                            item = file_id & 0x7fffffff
                            if array and map_size and not map_size & (map_size - 1) and item < size:
                                slot = ((start + item) >> 1) & (map_size - 1)
                                block = pointer(f'newResourceManager.node{index}.nameBlock', array + slot * 8)
                                name = pointer(f'newResourceManager.node{index}.namePointer', block + ((start + item) & 1) * 8) if block else None
                                if name:
                                    filename(f'newResourceManager.node{index}.filename', name)
                    node = int.from_bytes(entry[:8], 'little')
    storage = pointer('resourceStorage.cell', base + 0x586b600)
    if storage:
        for label, offset in (('avatar', 0x30870), ('depot', 0x212b8), ('skill', 0x16c70),
                              ('ornamentGroupPrimary', 0x13b70), ('ornamentGroupByAvatar', 0x13b78),
                              ('ornament', 0x1cd98), ('weapon', 0x34900)):
            dictionary = pointer('resourceStorage.' + label + '.cell', storage + offset)
            if dictionary:
                get('resourceStorage.' + label + '.dictionary', dictionary, 0x48)
        permutation = pointer('resourceDecode.permutation', storage + 0xe970)
        permutation_size = get('resourceDecode.length', permutation + 0x18, 4) if permutation else None
        if permutation_size is not None and int.from_bytes(permutation_size, 'little') >= 2048:
            for quarter in range(4):
                get(f'resourceDecode.quarter{quarter}', permutation + 0x420 + quarter * 256, 256)
    get('resourceDecode.seed', base + 0x5872d30, 4)
    return dict(reads=records, optionsMatchFixedRecord=(option == base + 0x22f9988 and options == bytes.fromhex('0101000007000000')),
                enumConstructorMatchesSharedBody=(int.from_bytes(ctor_data[8:16], 'little') == base + 0x16fbc0b0) if ctor_data else None,
                defaultComparerPointer=default, defaultComparerRead=default is not None,
                runtimeTypeCacheHeaderRead=header is not None, runtimeTypeDescriptorSource=descriptor_source,
                runtimeTypeCacheResult=cache_result, registeredFileProviderCount=provider_count,
                fileRouterRead=router_header is not None, actualResourceRouteObserved=False,
                atomicSnapshot=False, naturalConstructorExecuted=False, completeAvatarHandler=False, targetProtocolReady=False)


def execute(observation, scope):
    raw = observation.read_bytes()
    source = json.loads(raw.decode('utf-8-sig'))
    if source.get('status') != 'completed' or not source['finalStatus']['alive']:
        raise ValueError('A completed live uninstrumented observation is required')
    if source['sourceSHA256'] != sha256(ROOT / 'tools/observe_client72_uninstrumented.py'):
        raise ValueError('Observed monitor source changed')
    game = Path(source['launch']['cwd']).resolve()
    inputs = verified_inputs(ROOT, game)
    b = WindowsBackend()
    handle = b.k.OpenProcess(0x101010, False, source['pid'])
    if not handle:
        raise C.WinError(C.get_last_error())
    try:
        creation = b.creation_time(handle)
        if creation['filetime'] is None or creation != source['processCreationTime']:
            raise ValueError('Live process creation identity differs')
        if Path(b.image(handle)).resolve() != game / 'YuanShen.exe' or not b.status(handle)['alive']:
            raise ValueError('Live process image or lifetime differs')
        modules = b.snapshot(8 | 16, source['pid'], MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')
        main = [row for row in modules['items'] if Path(row['path']).resolve() == game / 'YuanShen.exe']
        if modules['error'] is not None or len(main) != 1:
            raise ValueError('Cannot identify the live main module')
        b.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        b.k.ReadProcessMemory.restype = W.BOOL

        def read(address, size):
            if not 0 < address < (1 << 64) or not 0 < size <= 256:
                return b'', 'address-or-size-outside-sampling-contract'
            buf = C.create_string_buffer(size); count = C.c_size_t()
            ok = b.k.ReadProcessMemory(handle, address, buf, size, C.byref(count))
            return buf.raw[:count.value], None if ok and count.value == size else dict(winerror=C.get_last_error(), bytesRead=count.value)

        result = collect(read, main[0]['base'], scope)
        result.update(pid=source['pid'], creation=creation, mainModule=main[0], inputChecks=inputs,
                      sourceObservationSHA256=hashlib.sha256(raw).hexdigest(), sourceSHA256=sha256(Path(__file__)),
                      finalStatus=b.status(handle), status='completed')
        return result
    finally:
        b.close(handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--observation', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scope', choices=('all', 'archive-state'), default='all')
    args = parser.parse_args()
    output = args.output.resolve(); local = (ROOT / 'local/migration72/d02-active/plain').resolve()
    if not output.is_relative_to(local) or output.exists():
        parser.error('Choose a new local plain-observation output')
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        result = execute(args.observation, args.scope)
    except (OSError, ValueError) as error:
        result = dict(status='error', error=str(error), winerror=getattr(error, 'winerror', None))
    output.write_bytes((json.dumps(result, indent=2) + '\n').encode())
    print(json.dumps({key: result.get(key) for key in ('status', 'optionsMatchFixedRecord', 'enumConstructorMatchesSharedBody', 'runtimeTypeCacheResult', 'winerror')}))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
