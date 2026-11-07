"""CN72 offline SDK action registration, request ownership and read-only roots."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT/'tools'), str(ROOT/'local/migration72/python')]
from client72_sdk_transport import Image, MODULE_HASHES, PROFILE_SHA, NATURAL_SHA, digest
import unicorn
from unicorn import x86_const as ux

PARENT_SOURCE = '43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237'
PARENT_TEST = 'e90bb87414ee3b5c7159938ec3d38960b78eefdfe2b58123346df2c5e793b3db'
PARENT_RAW = '3650c789f07778ee54a705d517e4ba88914231b0e426bfc53d9202af8edba9fb'
ROOT_CONTRACT = 'c2620c938c2d5be83c6e1dc96eeba862cc9a85886a8f1f2b08c678bdc97071da'
REA_FINAL = '94ff59a7900378914d1ee387616bf81412bc0be306090f6c9db563b5e0e091a6'


class NativeBoundary(Exception):
    pass


class Fixture:
    """No target process. Native trees, strings and ownership; named OS models."""
    STACK = 0x300080008
    OBJECT = 0x400000000
    HEAP = 0x600000000
    STOP = 0x500000000

    def __init__(self, pe):
        self.pe = pe
        self.uc = unicorn.Uc(unicorn.UC_ARCH_X86, unicorn.UC_MODE_64)
        self.uc.mem_map(pe.base, 0xa00000)
        for address, size, offset, _ in pe.sections:
            self.uc.mem_write(address, pe.data[offset:offset+size])
        for address, size in [(0x300000000,0x100000),(self.OBJECT,0x100000),
                              (self.HEAP,0x1000000),(self.STOP,0x1000),(0x700000000,0x10000)]:
            self.uc.mem_map(address,size)
        self.allocations = {}
        self.heap_next = self.HEAP
        self.events = []
        self.trace = []
        self.cut = {}
        self.uc.hook_add(unicorn.UC_HOOK_CODE,self.on_code)
        self.put(pe.base+0x764ca0,3,4)  # Fixture logging level; never a live write.
        self.uc.reg_write(ux.UC_X86_REG_GS_BASE,0x700000000)
        self.put(0x700000058,0x700001000)
        self.put(0x700001000,0x700002000)
        self.put(0x700002004,1,4)

    def put(self,address,value,width=8):
        self.uc.mem_write(address,(value & ((1 << (width*8))-1)).to_bytes(width,'little'))

    def get(self,address,width=8):
        return int.from_bytes(self.uc.mem_read(address,width),'little')

    def string(self,address):
        size = self.get(address+0x10)
        capacity = self.get(address+0x18)
        pointer = address if capacity < 16 else self.get(address)
        if size > 2048 or capacity < size:
            raise ValueError('Fixture string outside sample contract')
        return bytes(self.uc.mem_read(pointer,size))

    def allocate(self,size):
        if not 1 <= size <= 0x10000:
            raise ValueError('Fixture allocation size outside model')
        result = self.heap_next
        self.heap_next += (size+15)&~15
        self.allocations[result] = size
        return result

    def call(self,rva,*args):
        self.trace = []
        self.uc.mem_write(self.STACK-0x10000,bytes(0x11000))
        self.put(self.STACK,self.STOP)
        self.uc.reg_write(ux.UC_X86_REG_RSP,self.STACK)
        for reg,value in zip([ux.UC_X86_REG_RCX,ux.UC_X86_REG_RDX,ux.UC_X86_REG_R8,ux.UC_X86_REG_R9],args):
            self.uc.reg_write(reg,value)
        for i,value in enumerate(args[4:]):
            self.put(self.STACK+0x28+i*8,value)
        self.uc.emu_start(self.pe.base+rva,self.STOP,count=400000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP)!=self.STOP:
            raise ValueError('Instruction budget exhausted')
        if self.uc.reg_read(ux.UC_X86_REG_RSP)!=self.STACK+8:
            raise ValueError('Native stack imbalance')
        return self.uc.reg_read(ux.UC_X86_REG_RAX)

    def on_code(self,uc,address,size,_):
        rva = address-self.pe.base
        self.trace.append(rva)
        args = [uc.reg_read(r) for r in [ux.UC_X86_REG_RCX,ux.UC_X86_REG_RDX,ux.UC_X86_REG_R8,ux.UC_X86_REG_R9]]
        if rva in self.cut:
            result = self.cut[rva](args)
        elif rva in [0x4823a8,0x13a1b0,0x4978c0]:
            result = self.allocate(args[0])
            self.events.append({'model':'storage-allocate','rva':hex(rva),'bytes':args[0]})
        elif rva in [0x4823a0,0x48f4f0]:
            if args[0]:
                del self.allocations[args[0]]
            result = 0
            self.events.append({'model':'storage-free','rva':hex(rva),'null':args[0]==0})
        elif rva in [0x45e8f0,0x45e920,0x45e944,0x45ee7c,0x45eec8,0x45eef0,
                     0x45ee54,0x45ee6c,0x45e8dc]:
            result = 0
            self.events.append({'model':'single-thread-mutex-condition','rva':hex(rva)})
        elif rva in [0x482920,0x4828c0]:
            self.put(args[0],-1 if rva==0x482920 else 1,4)
            if rva==0x4828c0:
                self.put(0x700002004,1,4)
            result=0
            self.events.append({'model':'single-thread-static-init-epoch','rva':hex(rva)})
        elif rva in [0x45ddf8,0x45ff58,0x485540,0x45e570]:
            raise NativeBoundary(f'native-error-entry:{rva:x}')
        else:
            return
        stack = uc.reg_read(ux.UC_X86_REG_RSP)
        uc.reg_write(ux.UC_X86_REG_RAX,result)
        uc.reg_write(ux.UC_X86_REG_RIP,self.get(stack))
        uc.reg_write(ux.UC_X86_REG_RSP,stack+8)


def rtti_tables(pe,name):
    """MSVC COL self-RVA and exact TypeDescriptor bytes, not nearby naming."""
    def rva(offset):
        return next(start-pe.base+offset-raw for start,size,raw,_ in pe.sections
                    if raw<=offset<raw+size)
    needle=f'.?AV{name}@mihoyo_sdk@@'.encode()+b'\0'
    positions=[m.start() for m in re.finditer(re.escape(needle),pe.data)]
    if len(positions)!=1:
        raise ValueError('RTTI exact name is not unique: '+name)
    descriptor=rva(positions[0])-16
    result=[]
    for match in re.finditer(re.escape(struct.pack('<I',descriptor)),pe.data):
        offset=match.start()-12
        if offset<0:
            continue
        words=struct.unpack_from('<6I',pe.data,offset)
        if words[0]!=1 or words[3]!=descriptor or words[5]!=rva(offset):
            continue
        col=rva(offset)
        for pointer in re.finditer(re.escape(struct.pack('<Q',pe.base+col)),pe.data):
            table=rva(pointer.start())+8
            result.append({'type':name,'typeDescriptorRva':descriptor,'colRva':col,
                           'subobjectOffset':words[1],'vtableRva':table})
    return result


def action_fixture(pe):
    env=Fixture(pe)
    sdk=env.OBJECT
    combo=sdk+0x1000
    env.call(0x131ef0,sdk)
    env.put(combo+8,sdk+8)
    env.call(0x3bf960,combo)
    head=env.get(sdk+0x48)
    nodes=[]
    pending=[env.get(head+8)]
    while pending:
        node=pending.pop()
        if node==head:
            continue
        key=env.string(node+0x20).decode('ascii')
        callback=env.get(node+0x78)
        nodes.append({'key':key,'vtableRva':env.get(callback)-pe.base,
                      'targetRva':env.get(callback+8)-pe.base,'owner':env.get(callback+0x18)})
        pending += [env.get(node),env.get(node+0x10)]
    return env, sorted(nodes,key=lambda n:n['key'])


def action_dispatch(env):
    sdk=env.OBJECT
    combo=sdk+0x1000
    name=sdk+0x2000
    args=sdk+0x3000
    env.uc.mem_write(args,b'{"fixture":9007199254740993}\0')
    env.put(env.pe.base+0x7eab80,sdk)
    hits=[]
    for target in [0x3cbe30,0x3cc220]:
        def boundary(values,target=target):
            outer=env.get(values[2]+0x38)
            inner=env.get(outer+0x40)
            hits.append({'targetRva':target,'owner':values[0],'argsPointer':values[1],
                         'responseVtableRva':env.get(outer)-env.pe.base,
                         'callerCallbackVtableRva':env.get(inner)-env.pe.base,
                         'callerCallback64':env.get(inner+8),'callerIndex32':env.get(inner+0x10,4)})
            return 0
        env.cut[target]=boundary
    samples=[]
    for key,target,flag in [('login_login',0x3cbe30,0),('login_account_plat',0x3cc220,0),
                            ('unknown_fixture_action',None,0),('',None,0),
                            ('login_login',None,1)]:
        hits.clear()
        env.put(sdk+0xdb,flag,1)
        env.uc.mem_write(name,key.encode()+b'\0')
        env.call(0x13d0c0,name,args,0x123456789abcdef0,0x12345678)
        expected=[] if target is None else [{'targetRva':target,'owner':combo,
            'argsPointer':args,'responseVtableRva':0x694a80,'callerCallbackVtableRva':0x6a84b0,
            'callerCallback64':0x123456789abcdef0,'callerIndex32':0x12345678}]
        if hits!=expected:
            raise ValueError('Native action dispatch mismatch: '+key)
        samples.append({'key':key,'flagDB':flag,'hits':list(hits),'steps':len(env.trace)})
    return samples


def queue_fixture(pe,url_bytes=b'https://fixture.invalid/login?synthetic=1'):
    env=Fixture(pe)
    engine=env.OBJECT
    pool=engine+0x1000
    empty_name=engine+0x2000
    env.put(empty_name+0x18,15)
    env.call(0x13c7e0,pool,8,empty_name)
    env.put(engine,pe.base+0x6bf300)
    env.put(engine+0x70,pool)
    env.put(pool+0x12c,1,4)  # Explicit existing idle worker model; no thread creation.
    url=engine+0x3000
    body=engine+0x4000
    length=engine+0x5000
    callback=engine+0x6000
    env.uc.mem_write(url,url_bytes+b'\0')
    env.uc.mem_write(body,b'{"synthetic":true}\0')
    env.put(length,18)
    env.call(0x2f4290,engine,url,body,length,callback,callback,0,37,0,callback,callback)
    count=env.get(pool+0x88)
    table=env.get(pool+0x70)
    capacity=env.get(pool+0x78)
    first=env.get(pool+0x80)
    block=env.get(table+8*(first&(capacity-1)))
    task=env.get(block+8)
    state=env.get(task)
    request=env.get(state+0x108)
    expected=[pe.base+0x6bf258,pe.base+0x6a8450,pe.base+0x6a8418,pe.base+0x6bf1b0]
    actual=[env.get(block),env.get(task-0x10),env.get(state),env.get(request)]
    if actual!=expected or env.get(block+0x10)!=task-0x10 or env.get(block+0x38)!=block:
        raise ValueError('Native packaged-task type/ownership mismatch')
    if env.string(request+0x10)!=url_bytes:
        raise ValueError('Native queued URL differs from independent input')
    env.uc.mem_write(url,b'changed\0')
    if env.string(request+0x10)!=url_bytes:
        raise ValueError('Queued URL unexpectedly aliases caller buffer')
    return env, {'count':count,'capacity':capacity,'offset':first,'block':block,'task':task,
                 'state':state,'request':request,'urlBytes':len(url_bytes),'ownsUrlCopy':True,
                 'requestVtableRva':env.get(request)-pe.base}


def consume_queue(env,queued):
    pool=env.OBJECT+0x1000
    root=env.OBJECT+0x9000
    env.put(root,pool)
    hits=[]
    def transport_boundary(args):
        hits.append({'engine':args[0],'urlPointer':args[1],'bodyPointer':args[2],
                     'bodyBytes':env.get(args[3]),'queuedAtCall':env.get(pool+0x88)})
        env.put(pool+0x128,0,1)  # End this synthetic one-job worker loop.
        return 0
    env.cut[0x2f0940]=transport_boundary
    env.call(0x13ca70,root)
    if len(hits)!=1 or hits[0]['engine']!=env.OBJECT or hits[0]['queuedAtCall']!=0:
        raise ValueError('Native queue-to-transport forwarding mismatch')
    if env.get(pool+0x88)!=0 or queued['request'] in env.allocations:
        raise ValueError('Native worker did not pop/destroy its request')
    return {'hits':hits,'requestFreed':True,'finalQueueCount':env.get(pool+0x88),
            'steps':len(env.trace),'traceSHA256':digest(json.dumps(env.trace).encode())}


def singleton_fixture(pe):
    env=Fixture(pe)
    env.put(0x700002004,0x80000000,4)
    cold=env.call(0x13cdd0)
    allocations=len(env.allocations)
    first_trace=digest(json.dumps(env.trace).encode())
    warm=env.call(0x13cdd0)
    if cold!=warm or env.get(pe.base+0x7eab80)!=cold or len(env.allocations)!=allocations:
        raise ValueError('Native cold/warm SDK singleton mismatch')
    if env.get(cold)!=pe.base+0x694980 or env.get(cold+8)!=pe.base+0x6948e0:
        raise ValueError('Native SDK subobject vtables mismatch')
    return {'pointer':cold,'sameWarmPointer':True,'allocationCount':allocations,
            'coldTraceSHA256':first_trace,'warmSteps':len(env.trace),'modelEvents':env.events}


def selection_fixture(pe):
    env=Fixture(pe)
    service=env.OBJECT
    callback=service+0x1000
    env.put(service,pe.base+0x6c2ca8)
    hits=[]
    for target in [0x32efa0,0x32f460,0x342a10,0x32f930,0x331570]:
        def boundary(values,target=target):
            hits.append({'targetRva':target,'owner':values[0]})
            return 0
        env.cut[target]=boundary
    samples=[]
    for mode,target in [(0,0x32efa0),(1,0x32f460),(3,0x342a10),(11,0x32f930),
                        (99,0x331570),(0x100000001,0x32f460)]:
        hits.clear()
        env.call(0x3433e0,service,service+0x2000,mode,callback)
        if hits!=[{'targetRva':target,'owner':service}]:
            raise ValueError('Native service mode dispatch mismatch')
        samples.append({'mode64':mode,'targetRva':target,'steps':len(env.trace)})
    return samples


def stopped_queue_fixture(pe):
    env,queued=queue_fixture(pe)
    pool=env.OBJECT+0x1000
    env.put(pool+0x128,0,1)
    try:
        env.call(0x2f8530,pool,env.OBJECT+0xa000,env.OBJECT+0xb000)
    except NativeBoundary as error:
        if str(error)!='native-error-entry:485540' or env.get(pool+0x88)!=1:
            raise
        return {'errorBoundaryRva':0x485540,'queueUnchanged':True,'steps':len(env.trace),
                'exceptionObjectRuntime':'not-executed'}
    raise ValueError('Stopped native thread pool unexpectedly accepted a job')


def easy_cleanup_fixture(pe):
    env=Fixture(pe)
    handle=env.allocate(0x4d40)
    env.uc.mem_write(env.OBJECT,b'https://fixture.invalid/owned\0')
    status=env.call(0x41e6f0,handle,10002,env.OBJECT)
    owned_url=env.get(handle+0x628)
    env.put(handle+0x4d38,0xc0dedbad,4)
    status_cleanup=env.call(0x41e5e0,handle)
    if status!=0 or status_cleanup!=0 or env.allocations:
        raise ValueError('Limited native easy handle/URL cleanup did not release ownership')
    if not {0x41e5e0,0x427670,0x427cb0}.issubset(env.trace):
        raise ValueError('Native easy cleanup code was bypassed')
    return {'handle':handle,'ownedUrlPointer':owned_url,'setoptReturn':status,
            'cleanupReturn':status_cleanup,'remainingAllocations':0,'steps':len(env.trace),
            'traceSHA256':digest(json.dumps(env.trace).encode()),'modelEvents':env.events,
            'limit':'zeroed handle allocation plus native URL; no connections or real easy initialization'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to((ROOT/'local').resolve()):
        raise ValueError('Output must be new inside checkout local/')
    evidence=ROOT/'local/migration72/d02-active'
    fixed={ROOT/'tools/client72_sdk_transport.py':PARENT_SOURCE,
           ROOT/'tools/test_client72_sdk_transport.py':PARENT_TEST,
           ROOT/'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
           ROOT/'tools/extract_client72_signatures.py':'ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349',
           ROOT/'local/migration72/python/capstone/lib/capstone.dll':'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
           ROOT/'local/migration72/python/unicorn/lib/unicorn.dll':'010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1',
           ROOT/'protocol/cn-7.2/profile.json':PROFILE_SHA,
           evidence/'plain/run-elevated2.json':NATURAL_SHA,
           evidence/'sdk-transport-astra/final-first.json':PARENT_RAW,
           evidence/'sdk-login-chain-astra/rpm-roots-contract.json':ROOT_CONTRACT,
           evidence/'sdk-login-chain-astra/rea-final.json':REA_FINAL}
    for path,expected in fixed.items():
        if digest(path.read_bytes())!=expected:
            raise ValueError('Fixed source/evidence mismatch: '+path.name)
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    hashes={}
    for entry in profile['files'].values():
        sha=digest((args.client/entry['path']).read_bytes())
        if sha!=entry['sha256']:
            raise ValueError('Profile file mismatch: '+entry['path'])
        hashes[entry['path']]=sha
    parent=json.loads((evidence/'sdk-transport-astra/final-first.json').read_bytes())
    for module in parent['modules']:
        sha=digest((args.client/module['path']).read_bytes())
        if sha!=MODULE_HASHES[Path(module['path']).name]:
            raise ValueError('Natural module disk mismatch: '+module['path'])
        hashes[module['path']]=sha
    pe=Image((args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    env,nodes=action_fixture(pe)
    registered_count=env.get(env.OBJECT+0x50)
    if registered_count!=24 or len(nodes)!=24:
        raise ValueError('Native Combo registration cardinality mismatch')
    dispatch=action_dispatch(env)
    action_models=env.events
    queues=[]
    for label,url in [('empty',b''),('standard',b'https://fixture.invalid/login'),
                      ('sso15',b'a'*15),('heap16',b'a'*16),('long1024',b'a'*1024),
                      ('utf8','https://fixture.invalid/测试'.encode())]:
        env,queued=queue_fixture(pe,url)
        consumed=consume_queue(env,queued)
        queues.append({'name':label,'queued':queued,'consumed':consumed,'modelEvents':env.events})
    names=['MiHoYoSDKDll','MiHoYoSDKComboManager','LoginManager','MDKLoginService',
           'AccountPlatLoginService','AccountPlatAccountLoginJobber','MiHoYoSDKRequestManager',
           'MiHoYoSDKNetworkManager','HoYoNetworkEngine','MiHoYoSDKCrashManager']
    tables={name:rtti_tables(pe,name) for name in names}
    edges=[]
    for table,offset in [(0x694980,0x20),(0x6948e0,0x10),(0x6948e0,0x20),(0x6c15e0,0x60),
                         (0x6c2ca8,8),(0x6c2ca8,0x120),(0x6c2ca8,0xb0),(0x6c5390,8),
                         (0x6c5390,0x38),(0x6c5390,0x70),(0x6c5390,0x60),(0x6d2218,0xb8),
                         (0x6d1f48,0x18),(0x6bf300,0x58),(0x6bf300,0x48),(0x6bf1b0,0x10)]:
        slot=pe.offset(pe.base+table+offset,8)
        edges.append({'vtableRva':table,'slotByteOffset':offset,
                      'targetRva':int.from_bytes(pe.data[slot:slot+8],'little')-pe.base})
    windows={}
    for start,end in [(0x13cdd0,0x13ce5a),(0x131ef0,0x1320d1),(0x3bf960,0x3c0268),
                      (0x133810,0x13391b),(0x13d0c0,0x13d161),(0x133020,0x1336ba),
                      (0x3cbe30,0x3cc21c),(0x3cc220,0x3cc512),
                      (0x1bc610,0x1bc650),(0x3433e0,0x343460),(0x32f460,0x32f650),
                      (0x34ea20,0x34ec98),(0x34a67c,0x34a682),(0x35c190,0x35c2f0),
                      (0x34eca0,0x34f500),(0x3a57d0,0x3a59b0),(0x2fa3e0,0x2fa530),
                      (0x13ca70,0x13cd28),(0x2f1ae0,0x2f1fa3),(0x427670,0x4278db),
                      (0x1f95c0,0x1f9846)]:
        raw=pe.data[pe.offset(pe.base+start):pe.offset(pe.base+end-1)+1]
        windows[hex(start)]={'endRva':hex(end),'sha256':digest(raw),
            'instructions':[[hex(a-pe.base),m,o] for a,n,m,o in pe.instructions(start,end)]}
    rea=json.loads((evidence/'sdk-login-chain-astra/rea-final.json').read_bytes())
    records=[{'id':r['evidence_id'],'operation':r.get('operation'),'parameters':r.get('parameters'),
              'coverage':r.get('relationship_coverage'),'limitations':r.get('limitations',[])}
             for r in rea['records'] if r.get('operation') in ['procedure_pseudo_code','analyze_function']]
    result={'scope':'Offline native action/tree/queue segments and static roots; no live or network',
        'sourceSHA256':digest(Path(__file__).read_bytes()),'fixedInputs':{str(p.relative_to(ROOT)):s for p,s in fixed.items()},
        'moduleSHA256':hashes,'engines':{'unicorn':unicorn.__version__},'staticRTTI':tables,
        'staticVtableEdges':edges,
        'nativeSingleton':singleton_fixture(pe),'nativeRegistration':{'count':registered_count,'nodes':nodes},
        'nativeActionDispatch':dispatch,'actionModelEvents':action_models,'nativeServiceSelection':selection_fixture(pe),
        'nativeQueues':queues,'nativeStoppedQueue':stopped_queue_fixture(pe),'codeWindows':windows,
        'nativeEasyCleanup':easy_cleanup_fixture(pe),
        'readOnlyRootContract':json.loads((evidence/'sdk-login-chain-astra/rpm-roots-contract.json').read_bytes()),
        'reaProcedureEvidence':records,
        'remainingUnknowns':['Natural action invocation and selected service are not observed.',
            'No persistent completed-request easy handle or URL latch is established.',
            'Queue nodes can be popped/freed concurrently; no node/URL RPM sampler is implemented or approved.',
            'Password request route is config key loginUrl; its natural value and actual request are unobserved.',
            'The source-bound chain is conditional on AccountPlat service/mode and UI submit; full native login not executed.']}
    args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps({'registration':registered_count,'actionSamples':len(dispatch),
                      'queueSamples':len(queues),'sha256':digest(args.output.read_bytes())}))


if __name__=='__main__':
    main()
