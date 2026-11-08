"""Static-only class+80 source preparation; native execution is not enabled."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT));__package__='tools'
from . import extract_client72 as static

RAW=ROOT/'local/migration72/d02-resume-20261109/r1-class80'
PARENT='local/migration72/d02-resume-20261109/r1-hierarchy/first.json'
CONTRACT='local/migration72/d02-resume-20261109/r1-hierarchy/first.next-input.private.json'
PINS={
    'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/probe72_avatar_hierarchy_storage.py':'50522dd602d7930735c2d42982ffb06f31f601fc37378f614cc6b85919e33a4e',
    PARENT:'7026c7f3b287e38c4640ed293a412442b6ce85c932f471bdbdfc2e9ce254d344',
    CONTRACT:'c231c5b8131e2fa65f3bb07ef81ea537cf408458ef08e118af086956cd73826c'}
PRODUCER=(0x14051c9d0,0x14051e00d,'d73f483a6f520517421e29303ed51adae405c3e29a59befe484dbc4e6b16a076')


def sha(raw):return hashlib.sha256(raw).hexdigest()


def signed32(value):return value if value<0x80000000 else value-(1<<32)


def offsets_rows(metadata,table,encoded_start,count):
    """Original16-bit row-index wrap, six-byte records and signed fields."""
    if type(count) is not int or not 0<=count<=2:
        raise ValueError('Outside selected two class+80 rows')
    rows=[]
    for ordinal in range(count):
        index=(encoded_start+0xe42a+ordinal)&0xffff
        file=static.BODY+table+index*6
        static.span(metadata,file,6)
        raw=metadata[file:file+6]
        reference,offset=struct.unpack('<ih',raw)
        if reference < -1:
            raise ValueError('Invalid negative type reference')
        rows.append(dict(ordinal=ordinal,index=index,fileOffset=hex(file),
            metadataBodyOffset=hex(table+index*6),bytes=6,bytesHex=raw.hex(),sha256=sha(raw),
            typeRef=reference,signedOffset=offset,storedOffsetDword=hex(offset&0xffffffff)))
    return rows


class FocusedTypes:
    """Resolve only the two selected references and their one-argument records."""
    def __init__(self,binary,metadata,startup,pe,header):
        self.binary,self.metadata,self.startup,self.pe,self.header=binary,metadata,startup,pe,header
        reg=pe.offset(static.METADATA_REGISTRATION,0x90)
        self.references=static.number(binary,reg,'Q')
        self.reference_count=(static.number(binary,reg+0x8c)+0xce67355c)&0xffffffff
        self.instances=static.number(binary,reg+0x70,'Q')
        self.generic_base=(static.number(binary,header+0xd0)+0xe1492096)&0xffffffff
        self.generic_bytes=(static.number(binary,header+0x44)+0xe42e197f)&0xffffffff
        static.span(startup,self.generic_base,self.generic_bytes)
        self.class_table=(static.number(binary,header+0x64)+0xa7932555)&0xffffffff
        strings=static.BODY+(static.number(binary,header+0x70)^0x18202ccc)
        self.names=static.Names(metadata,strings)
        self.seen={}

    def named_definition(self,index):
        file=static.BODY+self.class_table+index*70
        static.span(self.metadata,file,70)
        row=self.metadata[file:file+70]
        return dict(index=index,name=self.names.decode((static.number(row,0x18)+0xa285a06d)&0xffffffff),
            namespace=self.names.decode(static.number(row,0x14)^0x149e96f1))

    def resolve(self,index,path=()):
        if index==-1:return dict(typeRef=-1,status='source-null-branch',inflationOrClassReturnProved=False)
        if not 0<=index<self.reference_count or index in path or len(path)>=3:
            raise ValueError('Type reference outside selected bounded source graph')
        if index in self.seen:return self.seen[index]
        if len(self.seen)>=4:raise ValueError('More than four selected type descriptors')
        address=self.references+index*16
        file=self.pe.offset(address,16)
        raw=self.binary[file:file+16]
        data,bits,reserved=struct.unpack('<QII',raw)
        kind=(bits>>16)&255
        result=dict(typeRef=index,descriptorRVA=hex(address-self.pe.base),descriptorHex=raw.hex(),
            descriptorSHA256=sha(raw),kind=kind,kindName=static.KINDS.get(kind,'unknown'),data=data,
            attributes=bits&0xffff,byRef=bool(bits&0x40000000),reserved=reserved,
            inflationOrClassReturnProved=False)
        self.seen[index]=result
        if kind==21:
            if data*8+8>self.generic_bytes:raise ValueError('Generic source pair outside fixed startup table')
            pair_file=self.generic_base+data*8
            instance,definition=struct.unpack_from('<II',self.startup,pair_file)
            inst=self.instances+instance*16
            inst_file=self.pe.offset(inst,16)
            count,array=struct.unpack_from('<QQ',self.binary,inst_file)
            if count!=1:raise ValueError('Selected interface is not one-argument generic source')
            pointer=static.number(self.binary,self.pe.offset(array,8),'Q')
            child=(pointer-self.references)//16
            if pointer<self.references or (pointer-self.references)%16:
                raise ValueError('Argument is not an exact registered type descriptor')
            result.update(genericIndex=data,startupPairFileOffset=hex(pair_file),
                startupPairHex=self.startup[pair_file:pair_file+8].hex(),instanceIndex=instance,
                registeredInstanceRVA=hex(inst-self.pe.base),argumentCount=1,
                definition=self.named_definition(definition),arguments=[self.resolve(child,path+(index,))])
        elif kind in (19,30):
            table=(static.number(self.binary,self.header+0x94)+0xd97e8a5c)&0xffffffff
            param_file=static.BODY+table+data*14
            static.span(self.metadata,param_file,14)
            parameter=self.metadata[param_file:param_file+14]
            encoded=static.number(parameter,6,'H')
            seed=((data*0x42bb72a)&0xffffffff)>>12
            position=(((seed*0xffffe680+0x5c80)&0xffffffff)^((encoded+0xffffbe10)&0xffffffff))&0xffff
            result.update(parameterIndex=data,parameterFileOffset=hex(param_file),
                parameterHex=parameter.hex(),parameterSHA256=sha(parameter),
                encodedArgumentPosition=encoded,argumentPosition=position,
                positionSource='Original1404eda0d/13/27/2c/32/38/3e/40, fixed reused inflation window; no native execution.')
        elif kind in (17,18):
            result['definition']=self.named_definition(data)
        else:
            result['status']='unresolved-kind-not-followed'
        return result


def prepare(binary,metadata,startup,parent,contract):
    if contract != parent['nextInputContract']:
        raise ValueError('Frozen next contract differs from parent projection')
    request=contract['currentStop']
    if (request['callerReturn'],request['width'],request['count'],request['templateCount'],
            request['fieldValueBefore'],request['newStorageSupplied'])!=('0x14051cb6d',32,2,2,'0x0',False):
        raise ValueError('Exact class+80 parent stop differs')
    pe=static.PE(binary)
    begin,end,expected=PRODUCER
    file=pe.offset(begin,end-begin,executable=True)
    if sha(binary[file:file+end-begin])!=expected:raise ValueError('Precise existing producer window drift')
    optional=static.number(binary,0x3c)+24
    rva,width=struct.unpack_from('<II',binary,optional+112+3*8)
    file=pe.offset(pe.base+rva,width)
    extents={pe.base+a:pe.base+z for a,z,_ in struct.iter_unpack('<III',binary[file:file+width])}
    if extents[begin]!=end:raise ValueError('Existing producer exception extent differs')
    inflation=parent['continuous']['functions']['0x1404ed990']
    inflation_end=int(inflation['end'],16)
    file=pe.offset(0x1404ed990,inflation_end-0x1404ed990,executable=True)
    if sha(binary[file:file+inflation_end-0x1404ed990])!=inflation['sha256']:
        raise ValueError('Reused parameter-position decoder window drift')
    header=pe.offset(static.HEADER_VA,static.BODY)
    class_table=(static.number(binary,header+0x64)+0xa7932555)&0xffffffff
    file=static.BODY+class_table+1234*70
    static.span(metadata,file,70)
    definition=metadata[file:file+70]
    if sha(definition)!='00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7':
        raise ValueError('Exact ordinary1234 metadata row differs')
    table=signed32((static.number(binary,header+0x54)+0x8054c847)&0xffffffff)
    rows=offsets_rows(metadata,table,static.number(definition,0x30,'H'),2)
    resolver=FocusedTypes(binary,metadata,startup,pe,header)
    for row in rows:row['type']=resolver.resolve(row['typeRef'])
    owned=next(row for row in parent['continuous']['newClasses'] if row['pointer']==request['ownedClass'])
    metadata_pointer=static.number(bytes.fromhex(owned['headerHex']),0x58,'Q')
    body=metadata_pointer-class_table-1234*70
    candidates=[dict(address=hex(body+int(row['metadataBodyOffset'],16)),bytes=6,
                     bytesHex=row['bytesHex'],sha256=row['sha256'],sourceFileOffset=row['fileOffset']) for row in rows]
    return dict(task='R1-CLASS80-STORAGE-static-preparation',
        producer=dict(start=hex(begin),end=hex(end),sha256=expected,newNativeWindow=False),
        parameterConsumer=dict(start='0x1404ed990',end=inflation['end'],sha256=inflation['sha256'],newNativeWindow=False),
        parentContractSHA256=PINS[CONTRACT],definition=1234,count=2,storageBytes=32,
        ordinaryMetadata=dict(fileOffset=hex(file),bytes=70,sha256=sha(definition)),
        rowTableBodyOffset=hex(table),encodedStart=static.number(definition,0x30,'H'),
        rows=rows,selectedDescriptorCount=len(resolver.seen),
        controlledInputCandidates=candidates,modelMetadataBody=hex(body),
        modelAddressIsNaturalEpochClaimed=False,fullFiveStockGateRun=False,nativeCpuRun=False,
        hierarchyThirdAccepted=False,newModelCalls=0,newNativeBytesRead=0,
        runtimeActivation='Disabled until main explicitly confirms stock restore and hierarchy third acceptance.',
        completeClass80Rows=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW.resolve()):parser.error('Output must stay in r1-class80')
    if args.output.exists() or args.output.with_suffix('.source.py').exists():parser.error('Prior preparation evidence is immutable')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    for path,expected in PINS.items():static.read_verified(ROOT/path,expected)
    binary,metadata,startup=[static.read_verified(args.client/path,expected) for path,expected in static.HASHES.items()]
    parent=json.loads((ROOT/PARENT).read_bytes());contract=json.loads((ROOT/CONTRACT).read_bytes())
    result=prepare(binary,metadata,startup,parent,contract)
    result.update(sourceSHA256=sha(Path(__file__).read_bytes()),sourcePins=PINS,staticInputSHA256=static.HASHES)
    args.output.write_bytes((json.dumps(result,indent=2)+'\n').encode())
    print(json.dumps(dict(task=result['task'],rows=[{k:row[k] for k in ('typeRef','signedOffset')} for row in result['rows']],
        fullFiveStockGateRun=False,nativeCpuRun=False,hierarchyThirdAccepted=False)))


if __name__=='__main__':main()
