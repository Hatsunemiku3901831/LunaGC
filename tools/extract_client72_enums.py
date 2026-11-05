"""Target enum symbols from the native field-default table, without semantic guesses."""
import argparse
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import PE, HASHES, HEADER_VA, BODY, MASK32, number, span, read_verified, extract
except ImportError:
    from extract_client72 import PE, HASHES, HEADER_VA, BODY, MASK32, number, span, read_verified, extract

# Four reachable protocol enums and two independent platform-source enums.
SELECTED = (20469, 46236, 54603, 70906, 2411, 11253)
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


def recover(binary, metadata, records):
    pe=PE(binary);h=pe.offset(HEADER_VA)
    base=BODY+((number(binary,h+0x1d8)+0xde66f77a)&MASK32)
    size=number(binary,h+0x1f4)^0x159a5178
    data=BODY+((number(binary,h+0x1cc)+0x9c0bd319)&MASK32)
    table=defaults(metadata,base,size,sum(len(r['fields']) for r in records))
    by_id={r['index']:r for r in records}
    selected=[symbols(by_id[i],table,metadata,data) for i in SELECTED]
    return dict(schemaVersion=1,inputSHA256=HASHES,defaultTable=hex(base),defaultTableBytes=size,
                defaultData=hex(data),enums=selected,
                scope='Protocol enum and oneof symbols; platform-source enums are not network enum declarations')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);report=recover(b,m,records)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(dict(enums=len(report['enums']),symbols=sum(len(e['values']) for e in report['enums']))))


if __name__=='__main__':main()
