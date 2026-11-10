import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'local/migration72'
PRIVATE = BASE / 'd02-resume-20261110b/c-proud-primary-index-locator'
PREFIX = 'local/migration72/d02-resume-20261110b/'
MANIFEST = PREFIX + 'c-proud-prepare-contract/frozen-inputs.private.json'
CONTRACT = PREFIX + 'c-proud-prepare-contract/{}.private.json'
LITERAL = PREFIX + 'c-proud-index-literals/{}.private.json'
OLD_RESULT = PREFIX + 'c-proud-payload-locator/final3/result.private.json'
SPEC = PREFIX + 'c-proud-prepare-contract/next-primary-index-locator-spec.zh.md'
LOCATOR = 'local/migration72/d02-active/resource-locator-astra/locator-final-{}.json'
INDEX = 'local/migration72/d02-active/resource-payload-index-astra/index-final-2.json'
PAYLOADS = 'local/migration72/d02-active/resource-payload-astra/payload-final-2/{}.object3.m_bytes.bin'
SIDECARS = 'local/migration72/d02-active/resource-payload-index-astra/index-final-2/{}.locations-le-i32.bin'
SERIALIZED = 'local/migration72/d02-active/resource-locator-astra/locator-final-1/25539185.serialized.bin'
EXTRA_PINS = {
 MANIFEST:'0e33c2b26fab21d557dd596f97aa918caa4c94e606331fdf24d745b34439003b',
 SPEC:'0409a7e3a5a83dd7ace77f89a299bac8943f1997f2b081eafdf7591d980a2e5b',
 CONTRACT.format('final1'):'9eb26c03c1ceb6f9cd9a000e7b9de688a2c9c8f2e2cf893902f8e119fbff5e27',
 CONTRACT.format('final2'):'9eb26c03c1ceb6f9cd9a000e7b9de688a2c9c8f2e2cf893902f8e119fbff5e27',
 CONTRACT.format('main-third'):'9eb26c03c1ceb6f9cd9a000e7b9de688a2c9c8f2e2cf893902f8e119fbff5e27',
 LITERAL.format('final1'):'ab5e41ca8b95a4d8ff610d5d96140583c9c5f8e8ad34399c93259817dc447c68',
 LITERAL.format('final2'):'ab5e41ca8b95a4d8ff610d5d96140583c9c5f8e8ad34399c93259817dc447c68',
 LITERAL.format('main-third'):'d6e1160cc1eccd1564df174798a7571a6feb41f048a3a105b161dcc07c3d9727',
 LOCATOR.format(1):'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
 LOCATOR.format(2):'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
 INDEX:'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9',
 OLD_RESULT:'e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839',
 'tools/client72_d02_proud_payload_locator.py':'d2983e92afc250ad46a6219d73c1d513420f9611dcf77ad72865dabf620014d7',
}
SECTIONS = {'31049740':(19661,12973844,925261,'b4164b4750852c03e1ce32b8768e0fc06ce8b82b6e288bcf85ba83830115d89b'),
            '31049741':(4,3607108,257650,'ade9b903cdc39d5719bf6fc6ea22006af40edbcf60db72b572759fc0b7ba4c3b')}
SERIALIZED_SHA = '0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7'
PATH_SHA = '058b4298fbe652d492fcc60b5c10a0214bb87e4ada40a4247a3f04c6a79f344a'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def checked(path, expected, size=None):
    data = (ROOT/path).read_bytes()
    if sha(data) != expected or (size is not None and len(data) != size):
        raise ValueError('source drift: '+path)
    return data


def save(path, report):
    path.write_text(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf8')


def source_gate(report):
    manifest = json.loads(checked(MANIFEST, EXTRA_PINS[MANIFEST], 37159))
    sources = manifest['sources']
    if len(sources) != 185 or sum(v['bytes'] for v in sources.values()) != 302489408:
        raise ValueError('frozen source count or size differs')
    checked_sources = {}
    for path, value in sources.items():
        checked(path, value['sha256'], value['bytes'])
        checked_sources[path] = value
    for path, digest in EXTRA_PINS.items():
        checked(path,digest)
    sys.path.insert(0,str(ROOT))
    from tools import client72_d02_proud_payload_locator as old
    for path, digest in old.PINS.items():
        checked(path,digest)
    contract = json.loads((ROOT/CONTRACT.format('final1')).read_bytes())
    if not contract['passed'] or contract['fixedSourceCount'] != 185 or contract['frozenManifestSHA256'] != EXTRA_PINS[MANIFEST]:
        raise ValueError('prepare contract differs')
    anchors = {'0x157ffc664':'488b4920','0x157ffc67b':'31d2','0x157ffc67d':'4531c0','0x157ffc680':'e8eb4e6ef5'}
    if any(contract['originalAnchors'][k]['hex'] != v for k,v in anchors.items()):
        raise ValueError('prepare original instruction differs')
    loader=contract['loaderTypeAndSuffix']
    if (loader['generic']['cell'],loader['generic']['usageRow'],loader['generic']['methodSpec'],loader['generic']['methodDefinition'],loader['generic']['methodInstantiation'],loader['generic']['type']['typeDefinition'],loader['suffix']) != ('0x145983438',735837,16871,80127,58257,8026,'.MiHoYoBinData'):
        raise ValueError('loader identity differs')
    if loader['oldLoaderCodeIdentity']['sha256'] != 'd6851231b0cec0ca2a2bae158bebaa924ace7afddc6604906b18a93342d06c87':
        raise ValueError('loader code identity differs')
    lit = json.loads((ROOT/LITERAL.format('final1')).read_bytes())
    primary = [x for x in lit['selected'] if x['cell']=='0x145b29758']
    if len(primary)!=1 or primary[0] != contract['literalSources'][0]:
        raise ValueError('primary literal identity differs')
    decoded=bytes.fromhex(primary[0]['plainHex'])
    if len(decoded)!=58 or sha(decoded)!=PATH_SHA or decoded.decode('utf8')!=primary[0]['value']:
        raise ValueError('primary literal bytes differ')
    report.update(existingArtifactHashReads=dict(count=len(sources),bytes=sum(v['bytes'] for v in sources.values()),sources=checked_sources),
                  additionalPins=EXTRA_PINS, oldToolPins=old.PINS, literal=primary[0], prepareAnchors=anchors,
                  loaderTypeAndSuffix=loader)
    return decoded.decode('utf8'), old


def scan_assets(data, name, section, key, reader_type, parse_record):
    start,end,count,digest=SECTIONS[name]
    if (section['start'],section['end'],section['count'],section['sha256'],section['bytes']) != (start,end,count,digest,end-start):
        raise ValueError('section metadata differs: '+name)
    if sha(data[start:end])!=digest:
        raise ValueError('section SHA differs: '+name)
    reader=reader_type(data); reader.pos=start
    if reader.read('i')!=count: raise ValueError('section count differs: '+name)
    hits=[]
    for _ in range(count):
        row=parse_record(reader)
        if row['end']>end: raise ValueError('asset record crosses section end')
        if row['key']==key:
            raw=data[row['offset']:row['end']]
            row.update(index=name,recordSHA256=sha(raw),recordHex=raw.hex())
            hits.append(row)
    if reader.pos!=end: raise ValueError('section end differs: '+name)
    return hits


def locations(data, bundle):
    if len(data)%24: raise ValueError('location extent differs')
    hits=[]
    for offset in range(0,len(data),24):
        if struct.unpack_from('<i',data,offset+4)[0]==bundle:
            raw=data[offset:offset+24]
            hits.append(dict(offset=offset,end=offset+24,hex=raw.hex(),sha256=sha(raw),
                             values=dict(zip(('blockID','bundleID','valueA','valueB','groupID','categoryBits'),struct.unpack('<6i',raw)))))
    return hits


def run(dest):
    report=dict(task='E-01/R3-PROUD-PRIMARY-INDEX-LOCATOR',stage='source-gate',passed=False,
                sourceSHA256=sha(Path(__file__).read_bytes()),targetHashNativeExecuted=False,nativeSerializedLookupExecuted=False,
                actualIndexFilled=False,parserExecuted=False,actualRowSpanApproved=False,backendProven=False,factoryProven=False,
                proudSkillGroupIdAliasApproved=False,secondU32LevelIdentityApproved=False,thresholdProven=False,javaFormulaProven=False,
                resourceReady=False,completeAvatar=False,d02Complete=False,targetCPU=0,liveReads=0,network=0,newCodeWindows=0)
    try:
        path, old = source_gate(report)
        from tools import client72_resource_locator as locator
        prior=json.loads((ROOT/LOCATOR.format(1)).read_bytes()); index=json.loads((ROOT/INDEX).read_bytes())
        types=index['indexes'][0]['types']
        suffixes=[x[1] for x in types if x[0]=='UnityEngine.MiHoYoBinData']
        if suffixes != ['.MiHoYoBinData'] or prior['genericType']['type']['name']!='MiHoYoBinData' or prior['genericType']['type']['namespace']!='UnityEngine':
            raise ValueError('type map or old native type differs')
        suffix=suffixes[0]; checks=[]
        if len(prior['targets'])!=9: raise ValueError('old native target count differs')
        for item in prior['targets']:
            row=old.unique_record([item['indexRecord']]); key=locator.reference_hash(item['path'],suffix)
            packed=locator.reference_pack(key,row['variants'][0][1]); name=f'{packed>>32:08x}'
            if (key,packed,packed&0xffffffff,packed>>32,name,item['selectorIndependent']) != (item['key'],item['packedIdentifier'],item['bundleID'],item['assetPathHash'],item['nativeEntryName'],True):
                raise ValueError('old native target reference differs: '+item['name'])
            checks.append(dict(name=item['name'],key=key,packed=packed,entryName=name))
        report['oldNineReferenceChecks']=checks
        key=locator.reference_hash(path,suffix)
        report.update(stage='index-scan',suffix=suffix,hostKey=key,hostKeyHex=f'{key:016x}')
        rows=[]; sections=[]
        if [x['name'] for x in index['indexes']] != ['31049740','31049741']: raise ValueError('index set differs')
        for item in index['indexes']:
            name=item['name']; data=(ROOT/PAYLOADS.format(name)).read_bytes()
            rows.extend(scan_assets(data,name,item['sections']['assets'],key,locator.Reader,locator.asset_record))
            start,end,count,digest=SECTIONS[name]
            sections.append(dict(index=name,start=start,end=end,bytes=end-start,count=count,sha256=digest))
        report.update(sections=sections,matches=rows,assetScanBytes=16561287,assetScanRecords=1182911)
        save(dest/'index-diagnostic.private.json',report)
        row=old.unique_record(rows)
        packed=locator.reference_pack(key,row['variants'][0][1]); bundle=packed&0xffffffff; name=f'{packed>>32:08x}'
        sidecar=(ROOT/SIDECARS.format(row['index'])).read_bytes()
        hits=locations(sidecar,bundle)
        report.update(stage='location',selectorIndependent=True,packedIdentifier=packed,bundleID=bundle,assetPathHash=packed>>32,
                      entryName=name,locationScanBytes=len(sidecar),locationScanRecords=len(sidecar)//24,locations=hits)
        save(dest/'location-diagnostic.private.json',report)
        if len(hits)!=1: raise ValueError('location count is %d, expected one'%len(hits))
        location=hits[0]; expected=dict(blockID=25539185,bundleID=178,valueA=0,valueB=0,groupID=0,categoryBits=7)
        if location['values']!=expected or any(t['location']!=expected for t in prior['targets']):
            raise ValueError('location differs from frozen serialized provenance')
        if prior['payload']['artifact']!='25539185.serialized.bin' or prior['payload']['outputSHA256']!=SERIALIZED_SHA:
            raise ValueError('serialized provenance differs')
        report['stage']='serialized'
        contents=checked(SERIALIZED,SERIALIZED_SHA,71555980)
        parsed=locator.serialized_objects(contents)
        raw,entry=old.extract_entry(contents,parsed,name)
        (dest/'payload.bin').write_bytes(raw)
        report.update(stage='complete',passed=True,actualPayloadExtracted=True,location=location,
                      serializedSource=dict(path=SERIALIZED,bytes=len(contents),sha256=SERIALIZED_SHA),
                      serializedHeader={k:parsed[k] for k in ('version','dataOffset','metadataBytes','types')},entry=entry,
                      sourceDataDiskBytes=48614214+3621204+len(sidecar)+71555980)
        save(dest/'result.private.json',report)
        print(json.dumps(dict(hostKey=report['hostKeyHex'],bundleID=bundle,entryName=name,payloadBytes=len(raw),payloadSHA256=sha(raw))))
    except Exception as error:
        report.update(passed=False,errorType=type(error).__name__,error=str(error))
        save(dest/'failure.private.json',report)
        raise


def audit(first,second,out):
    names=['index-diagnostic.private.json','location-diagnostic.private.json','payload.bin','result.private.json']
    entries={}
    for name in names:
        a=(PRIVATE/first/name).read_bytes(); b=(PRIVATE/second/name).read_bytes()
        if a!=b: raise ValueError('fresh artifacts differ: '+name)
        entries[name]=dict(bytes=len(a),sha256=sha(a))
    result=json.loads((PRIVATE/first/'result.private.json').read_bytes())
    if not result['passed'] or result['stage']!='complete' or result['targetHashNativeExecuted'] or result['parserExecuted']:
        raise ValueError('result state differs')
    save(PRIVATE/out,dict(passed=True,artifacts=entries,offlineAudit=True))


def main():
    p=argparse.ArgumentParser(description='Offline exact PrimaryIndex payload locator')
    p.add_argument('--output'); p.add_argument('--audit',nargs=3,metavar=('FIRST','SECOND','REPORT'))
    a=p.parse_args()
    if bool(a.output)==bool(a.audit): p.error('choose output or audit')
    if a.audit:
        if any(Path(s).name!=s for s in a.audit) or (PRIVATE/a.audit[2]).exists(): p.error('audit needs basenames and new report')
        audit(*a.audit); return
    if Path(a.output).name!=a.output or (PRIVATE/a.output).exists(): p.error('output must be a new basename')
    (PRIVATE/a.output).mkdir(parents=True); run(PRIVATE/a.output)

if __name__=='__main__': main()
