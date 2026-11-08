"""Same Rust supply core -> exact SDK caller/PEM/RSA/Base64, offline only.

The helper's public bytes and every authentication/padding/cipher byte stay in
memory. Its stdin/stdout must never be redirected to a report or log.
"""
from __future__ import annotations

import argparse
import base64
import gc
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT/'tools'), str(ROOT/'local/migration72/python')]
from client72_sdk_crypto_contract import (CryptoFixture, INPUT_MANIFEST_SHA,
    RAW as CRYPTO_RAW, Image, digest, model_summary, public_numbers, private_numbers)
from unicorn import x86_const as ux

RAW = ROOT/'local/migration72/d02-resume-20261108/r2-key-native'
CORE_SHA = '67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd'
SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
FIXED = {
    'patch/src/modules/sdk72_key_input.rs': CORE_SHA,
    'tools/client72_sdk_crypto_contract.py': '9b8c8f10f88260bbd3dfc2c62b76632c3fad9a0e0d9d7de2fb74e04f22b66395',
    'tools/test_client72_sdk_crypto_contract.py': '422d21c187e897d195fd3c754802e16fc8e4da8916845104fabdca70c83683ee',
    'local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json': 'c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313',
}
GPR_NAMES = ['RAX','RCX','RDX','RBX','RSP','RBP','RSI','RDI',
             'R8','R9','R10','R11','R12','R13','R14','R15']
GPRS = [getattr(ux,'UC_X86_REG_'+name) for name in GPR_NAMES]
XMMS = [getattr(ux,'UC_X86_REG_XMM'+str(i)) for i in range(16)]
NONVOLATILE = [3,5,6,7,12,13,14,15]
CALLERS = [
    {'start':0x34ee6b,'keyWrite':0x34eea2,'call':0x34eeb4,'return':0x34eeb7,
     'inputOffset':0x140},
    {'start':0x34ef3c,'keyWrite':0x34ef74,'call':0x34ef85,'return':0x34ef88,
     'inputOffset':0x120},
]


def require(condition, message):
    if not condition:
        raise ValueError(message)


class CorePipe:
    """Public snapshots only; no pipe transcript or host addresses in reports."""
    def __init__(self, helper, sdk_path, base, stock, local):
        self.process = subprocess.Popen([str(helper),str(sdk_path)],
            stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
            text=True,encoding='utf-8')
        self.count = 0
        self.hello = self.exchange({'moduleBase':base,'stock':list(stock),'local':list(local)})
        require(self.hello['coreSHA256']==CORE_SHA and self.hello['sdkSHA256']==SDK_SHA,
                'Helper/core identity differs')
        require(self.hello['callers']==[r['return'] for r in CALLERS]
                and self.hello['encryptRva']==0x3950c0 and self.hello['vtableRva']==0x6d1cb8,
                'Helper/core ABI constants differ')

    def exchange(self, request):
        self.process.stdin.write(json.dumps(request,separators=(',',':'))+'\n')
        self.process.stdin.flush()
        line = self.process.stdout.readline()
        if not line:
            self.process.wait(timeout=10)
            # The helper emits only fixed diagnostic text on failure. Never
            # include the request, PEM or any pipe transcript in an exception.
            raise ValueError('Rust snapshot helper exited before replying')
        return json.loads(line)

    def supply(self, request):
        result = self.exchange(request)
        self.count += 1
        return result

    def close(self):
        result = self.exchange({'finish':True})
        self.process.stdin.close()
        require(self.process.wait(timeout=10)==0,'Rust snapshot helper failed')
        require(result=={'coreInvocations':self.count,'finished':True},'Helper invocation ledger differs')
        require(self.process.stderr.read()=='','Rust helper produced unexpected diagnostics')
        self.process.stdout.close()
        self.process.stderr.close()


def snapshot(env):
    stack = env.uc.reg_read(ux.UC_X86_REG_RSP)
    return {'gpr':[env.uc.reg_read(r) for r in GPRS],
            'xmm':[env.uc.reg_read(r) for r in XMMS],
            'flags':env.uc.reg_read(ux.UC_X86_REG_EFLAGS),
            'stack':bytes(env.uc.mem_read(stack,128)),
            'lastError':env.last_error}


def core_request(env, state, wrong_binding=False):
    this = state['gpr'][1]
    key = int.from_bytes(state['stack'][0x28:0x30],'little')
    xmm = [word for x in state['xmm'][:4] for word in [x&0xffffffffffffffff,x>>64]]
    return {'gpr':state['gpr'],'xmmLow4':xmm,'flags':state['flags'],
            'stack':[int.from_bytes(state['stack'][i:i+8],'little') for i in range(0,128,8)],
            'thisVtable':0 if this==0 else env.get(this),
            'keySnapshot':[] if key==0 else list(env.uc.mem_read(key,269)),
            'wrongBinding':wrong_binding}


class BridgeFixture(CryptoFixture):
    """Original caller slices and CryptoFixture boundaries; only +0x28 supply."""
    LOCAL_PUBLIC = CryptoFixture.OBJECT+0x9000
    TEST_KEY = CryptoFixture.OBJECT+0xa000

    def __init__(self, pe, pipe, case, stock, local):
        super().__init__(pe)
        self.pipe,self.case,self.stock,self.local = pipe,case,stock,local
        self.entries = 0
        self.entry_before = None
        self.supply_proof = None
        self.input_pointer = None
        self.caller = None if case['caller'] is None else CALLERS[case['caller']]
        self.override_key = case['key']!='stock'
        current = {'stock':stock,'local':local,'malformed':b'not-a-key',
                   'empty':b'','truncated':stock[:-12],'long':stock+b'X','null':None}[case['key']]
        self.current_public = current
        if current is not None:
            self.uc.mem_write(self.TEST_KEY,current+b'\0'+bytes(300))

    def on_code(self, uc, address, size, context):
        rva = address-self.pe.base
        if self.caller and rva==self.caller['keyWrite'] and self.override_key:
            # An explicit negative fixture parameter, applied before the native
            # caller writes its fifth slot. It is identical in baseline/run.
            uc.reg_write(ux.UC_X86_REG_RDX,0 if self.current_public is None else self.TEST_KEY)
        if rva==0x3950c0:
            self.entries += 1
            before = snapshot(self)
            self.entry_before = before
            require(before['gpr'][1]==self.this and before['gpr'][2]==self.output
                    and before['gpr'][8]==self.input_pointer and before['gpr'][9]==self.length,
                    'Native caller parameter selection differs')
            expected_return = self.STOP if self.caller is None else self.pe.base+self.caller['return']
            require(self.get(before['gpr'][4])==expected_return,'Actual native caller return differs')
            if self.pipe is not None:
                length_before = bytes(uc.mem_read(self.length,8))
                input_before = bytes(uc.mem_read(self.input_pointer,len(self.case['plain'])+1))
                this_before = bytes(uc.mem_read(self.this,8))
                request = core_request(self,before,self.case.get('wrongBinding',False))
                result = self.pipe.supply(request)
                selected = bytes(result.pop('selectedPublic'))
                supplied = result['selection']=='core-local'
                require(supplied==self.case['supplied'],'Rust core supply differs from the case contract')
                if supplied:
                    require(selected==self.local+b'\0','Core-owned public output differs from fixed public material')
                    uc.mem_write(self.LOCAL_PUBLIC,selected)
                    self.put(before['gpr'][4]+0x28,self.LOCAL_PUBLIC)
                else:
                    require(selected==b'','Pass-through unexpectedly exported replacement material')
                after = snapshot(self)
                normalized = dict(after)
                normalized_stack = bytearray(after['stack'])
                normalized_stack[0x28:0x30] = before['stack'][0x28:0x30]
                normalized['stack'] = bytes(normalized_stack)
                require(normalized==before,'Bridge changed emulator state outside the fifth slot')
                require(length_before==bytes(uc.mem_read(self.length,8))
                        and input_before==bytes(uc.mem_read(self.input_pointer,len(self.case['plain'])+1))
                        and this_before==bytes(uc.mem_read(self.this,8)),
                        'Bridge changed native input/length/this storage')
                require(request['keySnapshot']==core_request(self,before)['keySnapshot'],
                        'Bridge changed original public source')
                require(all(result[key] for key in ['hostStackTranslated','hostThisTranslated',
                        'hostKeyTranslated','registerFieldsUnchanged','hostStackOnlyFifthChanged',
                        'hostThisUnchanged','hostPublicSourceUnchanged','opaqueInputPointerUnchanged'])
                        and result['inputBytesRead'] is False and result['coreInvocations']==1,
                        'Rust host snapshot preservation/translation failed')
                self.supply_proof = {**result,'emulatorGpr16Preserved':True,'emulatorXmm16Preserved':True,
                    'emulatorFlagsPreserved':True,'emulatorStack128OnlyFifthChanged':True,
                    'emulatorEntryLastErrorPreserved':True,'emulatorThisLengthInputAndPublicUnchanged':True,
                    'hostPointerNeverWrittenToEmulator':True,'copiedCorePublicIntoEmulator':supplied}
        return super().on_code(uc,address,size,context)

    def run_caller(self):
        plain,wide = self.case['plain'],self.case['length']
        obj,frame = self.OBJECT,self.OBJECT+0x20000
        self.this = obj
        self.put(obj,self.pe.base+0x6d1cb8)
        if self.case.get('wrongVtable',False):
            fake = obj+0x7000
            self.uc.mem_write(fake,bytes(self.uc.mem_read(self.pe.base+0x6d1cb8,0x40)))
            self.put(obj,fake)
        self.last_error = 0xfedc7654
        for i,r in enumerate(GPRS):
            self.uc.reg_write(r,0x1020304000000000+i*0x1010101)
        for i,r in enumerate(XMMS):
            self.uc.reg_write(r,((0xfedcba9876543210-i)<<64)|(0x1020304050607080+i))
        self.uc.reg_write(ux.UC_X86_REG_EFLAGS,self.case['flags'])
        self.uc.mem_write(self.STACK-0x10000,bytes(0x11000))
        if self.caller is None:
            self.output,self.length,self.input_pointer = obj+0x1000,obj+0x2000,obj+0x3000
            self.uc.mem_write(self.input_pointer,plain+b'\0')
            self.put(self.length,wide)
            self.put(self.STACK,self.STOP)
            self.put(self.STACK+0x28,self.TEST_KEY)
            for r,v in zip([ux.UC_X86_REG_RCX,ux.UC_X86_REG_RDX,ux.UC_X86_REG_R8,ux.UC_X86_REG_R9],
                           [obj,self.output,self.input_pointer,self.length]):
                self.uc.reg_write(r,v)
            start,end,expected_rsp = 0x3950c0,self.STOP,self.STACK+8
            self.uc.reg_write(ux.UC_X86_REG_RSP,self.STACK)
        else:
            caller_rsp = self.STACK-8
            account = self.case['caller']==0
            self.output = caller_rsp+0x60 if account else frame+0x30
            self.getter_output = frame+0x30 if account else caller_rsp+0x60
            self.length = caller_rsp+0x50
            input_string = frame+self.caller['inputOffset']
            self.put(self.length,wide)
            self.uc.mem_write(input_string,bytes(32))
            self.input_pointer = input_string if len(plain)<16 else obj+0x3000
            self.uc.mem_write(self.input_pointer,plain+b'\0')
            if len(plain)>=16:
                self.put(input_string,self.input_pointer)
            self.put(input_string+0x10,len(plain))
            self.put(input_string+0x18,max(15,len(plain)))
            for r,v in [(ux.UC_X86_REG_RSP,caller_rsp),(ux.UC_X86_REG_RBP,frame),
                        (ux.UC_X86_REG_RBX,obj),(ux.UC_X86_REG_RDI,obj)]:
                self.uc.reg_write(r,v)
            start,end,expected_rsp = self.caller['start'],self.pe.base+self.caller['return'],caller_rsp
        self.trace=[]
        self.uc.emu_start(self.pe.base+start,end,count=5000000)
        require(self.uc.reg_read(ux.UC_X86_REG_RIP)==end,'Native caller/Crypto instruction budget exhausted')
        require(self.uc.reg_read(ux.UC_X86_REG_RSP)==expected_rsp,'Native caller stack imbalance')
        require(self.entries==1 and self.uc.reg_read(ux.UC_X86_REG_RAX)==self.output,
                'Native encrypt call count/output-pointer return differs')


def validate_native(env, private):
    case = env.case
    encoded = env.string(env.output)
    require(bool(encoded)==case['success'],'Native success/error contract differs for '+case['name'])
    require(env.get(env.length)==case['length'],'Native length storage changed')
    state = snapshot(env)
    before = env.entry_before
    require(all(state['gpr'][i]==before['gpr'][i] for i in NONVOLATILE),
            'Native nonvolatile GPR return contract failed')
    require(state['xmm'][6:]==before['xmm'][6:],'Native XMM6..15 return contract failed')
    row = {'encodedBytes':len(encoded),'originalCalls':env.entries,
        'originalOutputPointerReturned64':env.output>0xffffffff,'lengthStorage64':env.get(env.length),
        'nativeNonvolatileGprPreserved':True,'nativeXmm6to15Preserved':True,
        'nativeSteps':len(env.trace),'executedOnlySDKCode':all(0<=r<0xa00000 for r in env.trace),
        'nativeCalls':list(env.observed)}
    if case['success']:
        local_effective = case['supplied'] or case['key']=='local'
        numbers = tuple(private[1:3]) if local_effective else public_numbers(env.stock)
        n,e = numbers
        cipher = base64.b64decode(encoded,validate=True)
        block = env.padding_blocks[-1]
        width = (n.bit_length()+7)//8
        sep = block.index(0,2)
        valid = len(block)==width and block[:2]==b'\0\2' and sep>=10 and all(block[2:sep]) and block[sep+1:]==case['plain']
        require(valid,'Independent native padding oracle failed')
        require(pow(int.from_bytes(block,'big'),e,n).to_bytes(width,'big')==cipher,
                'Independent integer RSA oracle failed')
        require(env.rsa_results[-1]==cipher,'Native RSA differs from independently decoded Base64')
        decrypted = pow(int.from_bytes(cipher,'big'),private[3],private[1]).to_bytes(128,'big')
        local_equal = decrypted==block and decrypted[decrypted.index(0,2)+1:]==case['plain'] if decrypted.startswith(b'\0\2') else False
        require(local_equal==local_effective,'Independent selected-server private decrypt differs')
        row.update(pkcs1Type2Valid=True,integerRSAEqual=True,nativeRSAEqualsBase64Decoded=True,
                   localPrivateDecryptEqual=local_equal,cipherBytes=width,paddingNonzeroBytes=sep-2)
    native_trace = set(env.trace)
    row['nativeCryptoStagesExecuted'] = {hex(r):r in native_trace for r in
        [0x3950c0,0x395d00,0x4ba050,0x4ba010,0x4ed800,0x4c9dd0,0x3b6a00]}
    owner = env.get(env.output) if env.get(env.output+0x18)>=16 else None
    # The exact caller owns strings in its original stack window. Parent call()
    # clears its own call stack, so destructor calls need a disjoint stack.
    original_stack = env.STACK
    env.STACK -= 0x20000
    env.call(0x1380e0,env.output)
    cleanup_steps = len(env.trace)
    row['outputOwnedCopyReleased'] = owner is None or owner not in env.allocations
    if env.caller:
        getter_owner = env.get(env.getter_output)
        env.call(0x1380e0,env.getter_output)
        cleanup_steps += len(env.trace)
        row['getterOwnedCopyReleased'] = getter_owner not in env.allocations
    else:
        row['getterOwnedCopyReleased'] = None
    env.STACK = original_stack
    require(row['outputOwnedCopyReleased'] and row['getterOwnedCopyReleased'] is not False,
            'Original native string destructor did not release owned storage for '+case['name'])
    row['cleanupNativeSteps'] = cleanup_steps
    row['modelCalls'] = model_summary(env.events)
    row['processGlobalAllocationsRetained'] = len(env.allocations)
    return row,encoded


def cases():
    rows = [
        ('account-one',0,b'A',1,'stock',True,True),
        ('password-nonzero',1,bytes(range(1,38)),37,'stock',True,True),
        ('account-boundary117',0,bytes(range(1,118)),117,'stock',True,True),
        ('password-oversize118',1,bytes(range(1,119)),118,'stock',True,False),
        ('account-high64',0,b'A',0x100000001,'stock',True,True),
        ('password-high64-boundary117',1,bytes(range(1,118)),0xfedcba9800000075,'stock',True,True),
        ('account-empty',0,b'',0,'stock',True,True),
        ('password-embeddedNul',1,b'\x01\0\x02',3,'stock',True,True),
        ('account-utf8',0,chr(0x4e2d).encode()+chr(0x1f600).encode(),7,'stock',True,True),
        ('unknown-caller',None,b'A',1,'stock',False,True),
        ('unknown-binding',0,b'A',1,'stock',False,True),
        ('unknown-vtable',1,b'A',1,'stock',False,True),
        ('malformed-key',0,b'A',1,'malformed',False,False),
        ('empty-key',1,b'A',1,'empty',False,False),
        ('null-key',0,b'A',1,'null',False,False),
        ('truncated-key',1,b'A',1,'truncated',False,False),
        ('stock-followed-by-extra-byte',0,b'A',1,'long',False,False),
        ('already-local-key',1,b'A',1,'local',False,True),
    ]
    return [{'name':name,'caller':caller,'plain':plain,'length':length,'key':key,
             'supplied':supplied,'success':success,'flags':0x202 if i%2==0 else 0xad7,
             'wrongBinding':name=='unknown-binding','wrongVtable':name=='unknown-vtable'}
            for i,(name,caller,plain,length,key,supplied,success) in enumerate(rows)]


def validate_sources(client):
    for path,expected in FIXED.items():
        require(digest((ROOT/path).read_bytes())==expected,'Frozen bridge source/evidence changed: '+path)
    manifest = CRYPTO_RAW/'input-manifest-v2.json'
    require(digest(manifest.read_bytes())==INPUT_MANIFEST_SHA,'Frozen Crypto input manifest changed')
    fixed = json.loads(manifest.read_bytes())
    for path,row in fixed.items():
        require(digest((ROOT/path).read_bytes())==row['sha256'],'Frozen Crypto input changed: '+path)
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    modules = {}
    for row in profile['files'].values():
        require(digest((client/row['path']).read_bytes())==row['sha256'],'Fixed CN72 client input changed')
        modules[row['path']] = row['sha256']
    require(len(modules)==5,'Fixed profile no longer contains five inputs')
    return modules


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--helper',type=Path,required=True)
    parser.add_argument('--local-public',type=Path,default=ROOT/'local/migration72/d02-active/sdk-key-input-sol/passport_1024.public.spki.pem')
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    require(not args.output.exists() and args.output.resolve().is_relative_to(RAW.resolve()),
            'Output must be new inside this bridge private directory')
    require(args.helper.resolve().is_relative_to(RAW.resolve()) and args.helper.is_file(),
            'Use the main-built bridge helper inside this card private directory')
    modules = validate_sources(args.client)
    pe = Image((args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    getter = CryptoFixture(pe)
    obj,out = getter.OBJECT,getter.OBJECT+0x1000
    getter.put(obj,pe.base+0x6d1cb8)
    require(getter.call(0x3951c0,obj,out,0)==out,'Native stock key producer failed')
    stock = getter.string(out)
    getter.call(0x1380e0,out)
    local = args.local_public.read_bytes()
    private = private_numbers((ROOT/'src/main/resources/keys/passport_1024.der').read_bytes())
    require(public_numbers(local)==tuple(private[1:3]),'Chosen fixed public material differs from server private public numbers')
    pipe = CorePipe(args.helper,args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll',pe.base,stock,local)
    rows = []
    for case in cases():
        # Baseline must retain stock for all known stock samples. The expected
        # effective key differs only for the actual core-selected second run.
        baseline_case = {**case,'supplied':False}
        baseline = BridgeFixture(pe,None,baseline_case,stock,local)
        baseline.run_caller()
        base_entry = baseline.entry_before
        base_row,base_encoded = validate_native(baseline,private)
        env = BridgeFixture(pe,pipe,case,stock,local)
        env.run_caller()
        require(env.entry_before==base_entry,'Native entry state differs from independent baseline')
        row,encoded = validate_native(env,private)
        require(row['originalOutputPointerReturned64'] and row['lengthStorage64']==case['length'],
                'Native return/length differs from contract')
        if not case['supplied']:
            require(encoded==base_encoded,'Pass-through changed the complete native output')
        require(env.last_error==baseline.last_error,'Bridge changed the native LastError outcome')
        row.update(name=case['name'],callerRva=None if case['caller'] is None else hex(CALLERS[case['caller']]['return']),
            callerSlice=None if case['caller'] is None else hex(CALLERS[case['caller']]['start']),
            inputBytes=len(case['plain']),supplied=case['supplied'],nativeSuccess=case['success'],
            nativeEntryEqualsBaseline=True,nativeReturnLastErrorEqualsBaseline=True,
            passThroughNativeOutputEqualsBaseline=None if case['supplied'] else True,
            baseline={'encodedBytes':base_row['encodedBytes'],
                      'localPrivateDecryptEqual':base_row.get('localPrivateDecryptEqual'),
                      'originalOutputPointerReturned64':base_row['originalOutputPointerReturned64']},
            translation=env.supply_proof)
        rows.append(row)
        del env,baseline,base_encoded,encoded,base_entry
        gc.collect()
    # A null-this call is outside the original Crypto ABI; validate only the
    # same core's rejection, never manufacture a native success for that call.
    gpr = [0]*16
    gpr[4] = 0x300080008
    stack = [0]*16
    stack[0],stack[5] = pe.base+CALLERS[0]['return'],0x40000a000
    rejected = pipe.supply({'gpr':gpr,'xmmLow4':[0]*8,'flags':0x202,'stack':stack,
        'thisVtable':0,'keySnapshot':list(stock+b'\0'),'wrongBinding':False})
    require(rejected.pop('selectedPublic')==[] and rejected['selection']=='original',
            'Core accepted null this')
    pipe.close()
    windows = {}
    for row in CALLERS:
        data = pe.data[pe.offset(pe.base+row['start']):pe.offset(pe.base+row['return']-1)+1]
        call = list(pe.instructions(row['call'],row['return']))
        require(len(call)==1 and call[0][2:] == ('call','qword ptr [rax + 0x18]'),
                'Exact native indirect CALL bytes changed')
        windows[hex(row['start'])] = {'endRva':hex(row['return']),'bytes':len(data),'sha256':digest(data),
            'callRva':hex(row['call']),'returnRva':hex(row['return']),'instruction':call[0][2:]}
    result = {'schemaVersion':1,'scope':'same Rust core + original SDK instructions, offline Unicorn only',
        'sourceSHA256':digest(Path(__file__).read_bytes()),
        'helperSourceSHA256':digest((ROOT/'patch/examples/sdk72_key_native_bridge.rs').read_bytes()),
        'helperArtifactSHA256':digest(args.helper.read_bytes()),'fixedSources':FIXED,
        'inputManifestSHA256':INPUT_MANIFEST_SHA,'fiveDiskInputs':modules,'helperHandshake':pipe.hello,
        'helperCoreInvocations':pipe.count,'nativeCases':rows,'coreOnlyNullThisRejection':rejected,
        'codeWindows':windows,'publicNumbersEqualSelectedServer':True,
        'models':['Original SDK PE mapping in Unicorn; no OS loader, client process, hook or network.',
            'Frozen CryptoFixture allocation/free, CRT exit registration, single-thread Windows/TLS/mutex/time state.',
            'Frozen synthetic BCrypt entropy; original SDK DRBG/padding/RSA/Base64 still execute.',
            'Caller inputs/length/SSO and optional bad-key/fake-vtable are explicit fixture state; full jobber preconditions not executed.',
            'Host stack/this/public-key snapshots copied via pipe; original Rust classifier directly invoked.',
            'Only the host-selected public bytes are copied into an independent emulator buffer; only fifth slot is translated.'],
        'notCovered':['Natural caller/route/configuration selection','Actual loaded SDK and original-entry identity binding',
            'Legal loader and integrity effects','Production hook install/unload/concurrency/exception unwind',
            'YMM/ZMM upper state, x87/MXCSR and stack beyond the owned 128-byte window',
            'Complete login jobber, real OS RNG/threads and OpenSSL process teardown','D-02 client acceptance'],
        'clientLoaded':False,'hookInstalled':False,'productionRegistered':False}
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'nativeCases':len(rows),'helperCoreInvocations':pipe.count,
        'reportBytes':args.output.stat().st_size,'reportSHA256':digest(args.output.read_bytes())}))


if __name__=='__main__':
    main()
