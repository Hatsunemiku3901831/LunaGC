"""Cross-check selected literals and enum constants against native target code.

Only stack probing and the final UTF-8 consumer are modeled for literals.
Enum field/class/type storage is synthetic; the native table lookup and signed
primitive load execute against the complete hash-bound original metadata.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, HEADER_VA, read_verified, extract
    from .extract_client72_literals import Literals72
    from .extract_client72_enums import recover
    from .client72_emulator import Offline72, STACK, END, HEAP
except ImportError:
    from extract_client72 import HASHES, HEADER_VA, read_verified, extract
    from extract_client72_literals import Literals72
    from extract_client72_enums import recover
    from client72_emulator import Offline72, STACK, END, HEAP


def invoke(e, entry, rcx, rdx, r8=0):
    e.prepare();e.allow_unwind(entry)
    for name,value in [('RSP',STACK+0x100008),('RCX',rcx),('RDX',rdx),('R8',r8)]:
        e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+name),value)
    e.uc.emu_start(entry,END,timeout=20_000_000,count=20_000_000)
    if e.failure or e.reg('rip')!=END:raise ValueError(e.failure or 'Execution budget exhausted')
    return e.reg('rax')


def check(e, binary, metadata, records):
    literals=Literals72(binary,metadata);literal_cases=[];enum_cases=[]
    e.models[0x141CC01A0]=lambda:e.reg('rax')  # __chkstk preserves requested stack size.
    captured=[]
    def consume():
        captured.append(bytes(e.uc.mem_read(e.reg('rcx'),e.reg('edx'))));return 0
    e.models[0x14052FBF0]=consume
    for row in literals.selected()['literals']:
        cipher=literals.payload(row['index']);pointer=e.alloc((len(cipher)+7)//8*8)
        e.uc.mem_write(pointer,cipher);captured.clear()
        invoke(e,0x14052F9F0,row['index'],pointer,len(cipher))
        if captured!=[row['value'].encode('utf8')]:raise ValueError('Native literal decode mismatch')
        literal_cases.append(dict(index=row['index'],nativeBytes=captured[0].hex(),passed=True))
    report=recover(binary,metadata,records)
    raw_metadata=0x630000000;e.uc.mem_map(raw_metadata,(len(metadata)+4095)&~4095);e.uc.mem_write(raw_metadata,metadata)
    e.write(0x145BC8F00,raw_metadata+0x210);e.write(0x145BC8EF8,HEADER_VA)
    runtime=e.alloc();primitive=e.alloc();e.write(runtime,HEAP);e.write(primitive+0x1a,8,'B')
    e.write(0x145BC8AD0,runtime)
    by_id={r['index']:r for r in records}
    for enum in report['enums']:
        # All selected target enums independently declare an Int32 underlying field.
        if enum['underlyingType']!='int32':raise ValueError('Native check supports int32 only')
        record=by_id[enum['typeIndex']];cls=e.alloc(256);fields=e.alloc(len(record['fields'])*32);definition=e.alloc(80)
        e.write(cls+0x60,fields);e.write(cls+0x58,definition);e.write(cls+0xac,primitive-HEAP,'I')
        e.write(definition+8,record['fieldStart']^0x72239b10,'I')
        # Selected endpoints plus aliases exercise the complete table search.
        choices=[enum['values'][0],enum['values'][-1]]
        if enum['typeIndex']==2411:choices.extend(v for v in enum['values'] if v['symbol'] in ('MetroPlayerX86','WSAPlayerX86'))
        if enum['typeIndex']==85949:choices.extend(v for v in enum['values'] if v['symbol'] in
            ('OnLoginReq','OnLoginRsp','OnPlayerTokenReq','OnPlayerTokenRsp'))
        for value in choices:
            field=fields+(value['fieldIndex']-record['fieldStart'])*32
            e.write(field+8,(cls-0xACD2443BA132B32E)&((1<<64)-1))
            actual=invoke(e,0x140524190,cls,field)
            if actual!=value['value']&((1<<64)-1):raise ValueError('Native enum constant mismatch')
            enum_cases.append(dict(clientType=enum['clientType'],symbol=value['symbol'],value=value['value'],passed=True))
    return dict(inputSHA256=HASHES,literals=literal_cases,enums=enum_cases,runtimeStatus='not-tested',
                models=['synthetic class/field/type objects','stack probe','capture UTF-8 before managed allocation'])


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    e=Offline72(b,a.libraries,records,False)
    try:result=check(e,b,m,records)
    finally:e.close()
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n','utf8')
    print(json.dumps(dict(nativeLiterals=len(result['literals']),nativeEnumValues=len(result['enums']))))


if __name__=='__main__':main()
