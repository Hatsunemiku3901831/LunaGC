"""Join native seed cryptography, Token construction, wire and send handoff.

This removes the opaque seed result from this experiment. SDK/OS/environment
inputs and transport selection remain explicit offline fixtures.
"""
import argparse
import json
import math
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_seed_crypto import SeedString72
    from .trace_client72_token_constructor import TokenConstructor72, OPAQUE_HELPER
    from .trace_client72_send_flow import SendFlow72, declarations
    from .trace_client72_request_identity import self_cell
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_seed_crypto import SeedString72
    from trace_client72_token_constructor import TokenConstructor72, OPAQUE_HELPER
    from trace_client72_send_flow import SendFlow72, declarations
    from trace_client72_request_identity import self_cell
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire


def construct_native_token(e,ctor,binary,metadata,sample,initialized,tamper=None,*,seed=None,continue_send=None):
    """Reusable real seed helper + constructor, preserving prior source calls."""
    e.models.pop(OPAQUE_HELPER)
    if seed is None:
        seed=SeedString72(e,binary,metadata,sample,initialized,0,tamper)
        seed.close()  # Use the outer constructor's target-literal storage.
    else:
        # Reuse the already installed OS/crypto runtime after dispatch. Mapping
        # another NativeCrypto72 over the same emulator would destroy continuity.
        if seed.e is not e or e.read(0x145DCAEA8,'B')!=int(initialized):
            raise ValueError('Existing seed runtime/initialization fixture differs')
        seed.session.next_double_value=sample
        seed.tamper=tamper
        seed.native.entropy_calls=[]
        # Response crypto traces this hot region by block. Seed's independent
        # plaintext/padding oracle also needs these two exact instruction inputs.
        for address in (0x1406595F0,0x140659AC0):
            e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=address,end=address))
    returned=[];gates=[];sdk_start=len(ctor.sdk_calls);stage_start=len(ctor.stages);shell_start=len(ctor.shell_calls)
    def observe(address):
        seed.observe(address)
        if address==OPAQUE_HELPER:gates.append(e.read(e.reg('rsp')))
        if gates and address==gates[0]:
            p=e.reg('rax');text=bytes(e.uc.mem_read(p+20,e.read(p+16,'I')*2)).decode('utf-16-le')
            ctor.strings[p]=text;returned.append(p)
        if address==0x151214D20:e.uc.emu_stop()
    e.uc.context_restore(e.context);e.calls=[];e.failure=None;e.steps=0
    e.uc.reg_write(e.R.UC_X86_REG_RSP,STACK+0x100008);e.uc.reg_write(e.R.UC_X86_REG_RCX,ctor.sender)
    e.allowed.append((0x151214D20,0x151214D21));e.before_instruction=observe
    e.uc.emu_start(0x15121FF70,END,timeout=20_000_000,count=20_000_000)
    if e.failure or e.reg('rip')!=0x151214D20:raise ValueError(e.failure or 'Token constructor budget exhausted')
    if (len(gates),len(returned),ctor.opaque_calls)!=(1,1,0):raise ValueError('Seed helper did not execute exactly once')
    if (e.reg('rcx'),e.reg('rdx'),e.read(ctor.request+0x90))!=(ctor.sender,ctor.request,returned[0]):
        raise ValueError('Native seed return pointer was not preserved into Token request')
    if ctor.stages[stage_start:]!=[8] or ctor.sdk_calls[sdk_start:]!=['info_get_sub_channel_id'] or ctor.shell_calls[shell_start:]!=[5]:
        raise ValueError('Token source sequence changed')
    if continue_send is not None:continue_send()
    return seed.finish(returned[0],sample,initialized,tamper),returned[0]


def recover(binary,metadata,startup,records,libraries):
    transport=declarations(binary,metadata,startup,records)
    association=self_cell(binary,next(r for r in records if r['index']==82491),0x1458C5500,24604)
    seed_field=next(f for r in records if r['index']==82491 for f in r['fields']
                    if f.get('offsetKind')=='instance' and f['offset']==0x90)
    if seed_field['type']['kind']!='string':raise ValueError('Seed request field is not string')
    fixtures=[]
    cases=((0.0,True,None),(0.5,True,None),(math.nextafter(1.0,0.0),True,None),
           (0.5,False,None),(0.5,True,'signature'))
    for variant,(sample,initialized,tamper) in enumerate(cases):
        e=Offline72(binary,libraries,records,True);ctor=None
        try:
            e.prepare();ctor=TokenConstructor72(e,binary,metadata,variant,'456')
            crypto,seed_pointer=construct_native_token(e,ctor,binary,metadata,sample,initialized,tamper)
            flow=SendFlow72(e,ctor,82491,24604,0xffffffff,0x200000044,'connected')
            before=bytes(e.uc.mem_read(ctor.request,0xc8))
            send=fragment(e,0x151214D20,{0x14A447E00},
                          dict(rsp=STACK+0x100008,rcx=ctor.sender,rdx=ctor.request))
            if (e.reg('rcx'),e.read(flow.packet+0x10),e.read(flow.packet+0x22,'H'))!=(flow.packet,ctor.request,24604):
                raise ValueError('Token command/object changed at frame-builder handoff')
            if before!=bytes(e.uc.mem_read(ctor.request,0xc8)):raise ValueError('Send routing mutated Token request')
            writer=Writer72(e);wire=writer.run(82491,ctor.values(82491,ctor.request,writer))['wire']
            decoded={tag:(kind,value) for tag,kind,value in parse_wire(bytes.fromhex(wire))}
            expected=ctor.text(seed_pointer).encode('ascii')
            if decoded.get(623,(2,b''))!=(2,expected) or decoded.get(10)!=(0,456):
                raise ValueError('Native seed/SDK value did not reach independent wire tags')
            fixtures.append(dict(variant=variant,crypto=crypto,send=send,requestWire=wire,
                                 helperReturnPreserved=True,cmdId=24604,seedTag=623,seedWireLength=len(expected),
                                 constructorStillSendsOnEmptySeed=not bool(expected),passed=True))
        finally:
            if ctor:ctor.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                entry='0x15121ff70',helper='0x15121db40',helperNativeExecuted=True,
                constructorStop='0x151214d20',sendStop='0x14a447e00',frameBuilderExecuted=False,
                runtimeStatus='not-tested',semanticName='GetPlayerTokenReq',typeIndex=82491,cmdId=24604,
                getterAssociation=association,transportParameterBindings=transport,
                seedBinding=dict(objectOffset=0x90,tag=623,type='string',clientField=seed_field['name'],
                                 sourceSemantic='Base64 RSA-encrypted big-endian client seed',
                                 semanticField='client_rand_key',status='verified',
                                 businessNameBasis='Existing server clientRandKey contract: Base64 -> RSA/PKCS1 -> big-endian uint64'),
                results=[dict(variant=f['variant'],seedHex=f['crypto']['seedHex'],
                              initializedFixture=f['crypto']['initializedFixture'],tamperedInput=f['crypto']['tamperedInput'],
                              ioctlReturn=f['crypto']['ioctlReturn'],seedWireLength=f['seedWireLength'],
                              cmdId=f['cmdId'],constructorStillSendsOnEmptySeed=f['constructorStillSendsOnEmptySeed'],passed=True)
                         for f in fixtures],
                modelsFrom=['seed-crypto-observations.json','token-constructor-observations.json','send-flow-observations.json'],
                replacedModel='Only the opaque 15121DB40 result is removed; native helper executes',
                outsideScope=['actual SDK initialization/transport selection','response cryptography','remaining business semantics',
                              'frame builder body, encryption/socket','new Shell/IFix','real-client acceptance'])


def main():
    a=parse_args();b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);report=recover(b,m,s,records,a.libraries)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],nativeHelper=report['helperNativeExecuted'],stop=report['sendStop'])))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
