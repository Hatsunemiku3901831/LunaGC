"""Hash-bound map generic method registrations used by the offline graph harness."""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import PE, HASHES, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, span, read_verified, extract
except ImportError:
    from extract_client72 import PE, HASHES, HEADER_VA, CODE_REGISTRATION, TypeResolver, number, span, read_verified, extract


def recover(binary, metadata, startup, records):
    pe=PE(binary); h=pe.offset(HEADER_VA); cr=pe.offset(CODE_REGISTRATION)
    specs=0x210+(number(binary,h+0x170)^0x6347382B)
    spec_count=(number(binary,h+0x24)^0x323BAE70)//12
    table=0x210+(number(binary,h+0xEC)^0x3CF0720C)
    size=(number(binary,h+0x110)+0xA3D9CC8B)&0xffffffff
    if not size or (size//14*14+3)&~3 != size: raise ValueError('Generic function table aligned stride')
    count=(number(binary,cr+0x64)+0x9EA91E3B)&0xffffffff
    pointers=pe.offset(number(binary,cr+0x30,'Q'),count*8)
    span(metadata,specs,spec_count*12);span(metadata,table,size)
    methods={m['index']:(r,m) for r in records for m in r['methods']}
    resolver=TypeResolver(pe,startup,records,h)
    selected=[]
    for i in range(size//14):
        pointer_index,spec_index=struct.unpack_from('<ii',metadata,table+i*14)
        if not 0<=pointer_index<count or not 0<=spec_index<spec_count: raise ValueError('Generic function reference out of bounds')
        method_index,cls,method_args=struct.unpack_from('<iii',metadata,specs+spec_index*12)
        if method_index not in methods or cls < -1 or method_args < -1: raise ValueError('Invalid MethodSpec')
        owner,method=methods[method_index]
        wanted=((owner['index']==17961 and cls in (314,146)) or (owner['index']==17964 and cls==146) or
                (owner['index']==66210 and cls in (32153,32160,59,1541)) or (owner['index']==66214 and cls in (59,1541)))
        if not wanted or method['name'] not in ('.ctor','MOMMACBEMEK','FNNADLCNGFO','set_Item'): continue
        # The selected concrete registrations use class arguments, not method args.
        if method_args != -1: raise ValueError('Unexpected generic method arguments')
        if cls not in resolver.arguments: raise ValueError('Unknown selected class instance')
        address=number(binary,pointers+pointer_index*8,'Q'); pe.offset(address,executable=True)
        selected.append(dict(owner=owner['index'],ownerType=owner['name'],method=method['name'],methodDefinition=method_index,
            classInstance=cls,arguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]],
            methodSpec=spec_index,tableIndex=i,pointerIndex=pointer_index,address=hex(address)))
    return dict(schemaVersion=1,inputSHA256=HASHES,tableRows=size//14,methodSpecCapacity=spec_count,
        pointerCount=count,bindings=selected,scope='Selected static AOT registrations; synthetic RGCTX is not live runtime ABI')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(dict(bindings=len(report['bindings']),tableRows=report['tableRows'])))


if __name__=='__main__':main()
