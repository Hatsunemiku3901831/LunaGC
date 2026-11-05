"""Target enum symbols from the native field-default table, without semantic guesses."""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import PE, HASHES, HEADER_VA, METADATA_REGISTRATION, BODY, MASK32, KINDS, number, span, read_verified, extract
except ImportError:
    from extract_client72 import PE, HASHES, HEADER_VA, METADATA_REGISTRATION, BODY, MASK32, KINDS, number, span, read_verified, extract

# Protocol/oneof enums and platform/language/login-stage source domains.
SELECTED = (20469, 46236, 54603, 70906, 2411, 11253, 77368, 85949)
FORMATS = {'int8':'b','uint8':'B','int16':'h','uint16':'H','int32':'i','uint32':'I','int64':'q','uint64':'Q'}


def defaults(metadata, base, size, field_count):
    if size % 12:
        raise ValueError('Unaligned default table')
    span(metadata, base, size)
    rows = {}
    for index, (kind, data, field) in enumerate(struct.iter_unpack('<iii',metadata[base:base+size])):
        if kind < 0 or data < -1 or not 0 <= field < field_count or field in rows:
            raise ValueError('Invalid or duplicate field default')
        rows[field] = dict(defaultRow=index, typeIndex=kind, dataIndex=data)
    return rows


def symbols(row, table, metadata, data_base):
    underlying = [f for f in row['fields'] if f['name']=='value__']
    if len(underlying)!=1 or underlying[0]['type']['kind'] not in FORMATS:
        raise ValueError('Enum must have one supported underlying field')
    kind=underlying[0]['type']['kind'];fmt=FORMATS[kind];values=[]
    for f in row['fields']:
        if f['name']=='value__': continue
        if f['offsetKind']!='literal' or f['type'].get('typeDefinition')!=row['index']:
            raise ValueError('Unexpected nonliteral enum member')
        raw=table.get(f['index'])
        if raw is None or raw['dataIndex']<0:
            raise ValueError('Enum member lacks explicit constant')
        values.append(dict(symbol=f['name'],value=number(metadata,data_base+raw['dataIndex'],fmt),
                           fieldIndex=f['index'],**raw))
    if len({v['symbol'] for v in values})!=len(values): raise ValueError('Duplicate enum symbol')
    return dict(typeIndex=row['index'],clientType=row['name'],namespace=row['namespace'],
                underlyingType=kind,values=values,semanticStatus='target-symbols-only')


def recover(binary, metadata, records, *, indices=SELECTED):
    pe=PE(binary);h=pe.offset(HEADER_VA)
    base=BODY+((number(binary,h+0x1d8)+0xde66f77a)&MASK32)
    size=number(binary,h+0x1f4)^0x159a5178
    data=BODY+((number(binary,h+0x1cc)+0x9c0bd319)&MASK32)
    table=defaults(metadata,base,size,sum(len(r['fields']) for r in records))
    mr=pe.offset(METADATA_REGISTRATION);count=(number(binary,mr+0x8c)+0xce67355c)&MASK32
    refs=pe.offset(number(binary,mr,'Q'),count*16)
    if any(r['typeIndex']>=count for r in table.values()):raise ValueError('Default type reference out of bounds')
    by_id={r['index']:r for r in records}
    selected=[symbols(by_id[i],table,metadata,data) for i in indices]
    for enum in selected:
        for value in enum['values']:
            kind=(number(binary,refs+value['typeIndex']*16+8)>>16)&255
            if KINDS.get(kind)!=enum['underlyingType']:raise ValueError('Constant storage/enum underlying type disagree')
    bindings=[dict(typeIndex=i,clientType=by_id[i]['name'],clientField=f['name'],objectOffset=f['offset'],
                   enumTypeIndex=f['type']['typeDefinition'],enumClientType=f['type']['name'],
                   role='oneof-discriminator' if i in (54602,70905) else 'wire-enum-field')
              for i in (21921,31708,54602,70905) for f in by_id[i]['fields']
              if f['type']['kind']=='valuetype']
    return dict(schemaVersion=1,inputSHA256=HASHES,defaultTable=hex(base),defaultTableBytes=size,
                defaultData=hex(data),enums=selected,fieldBindings=bindings,
                scope='Protocol enum and oneof symbols; platform/language/stage sources are not automatically network enum declarations')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);report=recover(b,m,records)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(dict(enums=len(report['enums']),symbols=sum(len(e['values']) for e in report['enums']))))


if __name__=='__main__':main()
