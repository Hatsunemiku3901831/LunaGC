"""Two approved compile-time literal registrations; no code windows or execution."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
OUT=ROOT/'local/migration72/d02-resume-20261110b/c-proud-index-literals'
PINS={
 'local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/next-literals-spec.zh.md':'7e79ee4975aa68c0e5fb69951f32cb41809505df72c535fa45c61796b548fd02',
 'local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/frozen-inputs.private.json':'e49509edc2d4ef89c4fbda496b5ffcce4a376020a87e9053118950049f4b8dbb',
 'local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor1/code.bin':'80ae3deeedfcc0287f634b115e03e22bb2a7d489621615890697a254394f06d9',
 'local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor2/code.bin':'80ae3deeedfcc0287f634b115e03e22bb2a7d489621615890697a254394f06d9',
 'local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor1/extraction.private.json':'c37d855fe8c7b0833ef014a82830eebb769b153a79aeaeac89f0b0288b3892e3',
 'local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor2/extraction.private.json':'c37d855fe8c7b0833ef014a82830eebb769b153a79aeaeac89f0b0288b3892e3',
 'local/migration72/d02-resume-20261110b/c-proud-row-source/collect_reader_literal.private.py':'eaefb23db004ee58fe3ba52b8f7ef2bc2eefddebd756706035555617ab6eeea8',
 'local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal1.private.json':'9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
 'local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal2.private.json':'9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
 'tools/extract_client72_literals.py':'865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed',
 'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
 'tools/game_path.ps1':'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
 'protocol/cn-7.2/profile.json':'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
}
CELLS={0x145b29758:(0x14d7adbc6,'488b358bbb37f8'),0x145b29760:(0x14d7adc10,'488b3549bb37f8')}
GROUP_SHA='cd57845c591f2da107930a0d88fbd7d86b3b6f06031f386f8e5db41540d03bb7'
MASK=0xffffffff

def sha(b): return hashlib.sha256(b).hexdigest()

def pin_sources():
    raw={p:(ROOT/p).read_bytes() for p in PINS}
    for p,b in raw.items():
        if sha(b)!=PINS[p]: raise ValueError('source mismatch: '+p)
    a=raw['local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor1/code.bin']
    b=raw['local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/ctor2/code.bin']
    if len(a)!=221 or a!=b: raise ValueError('original caller mismatch')
    for cell,(site,expected) in CELLS.items():
        ins=a[site-0x14d7adbc0:site-0x14d7adbc0+7]
        if ins.hex()!=expected or site+7+int.from_bytes(ins[3:7],'little',signed=True)!=cell:
            raise ValueError('original RIP load mismatch')
    one=json.loads(raw['local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal1.private.json'])
    two=json.loads(raw['local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal2.private.json'])
    if one!=two: raise ValueError('old literal reports differ')
    return one

def scan(group,start,slots_base,slot_count):
    if len(group)%8 or len(group)//8>100000: raise ValueError('registration budget exceeded')
    hits={c:[] for c in CELLS}
    for n,(enc0,enc1) in enumerate(struct.iter_unpack('<II',group)):
        i=start+n; key=(i*0x57c15286+0x26ddebda)&MASK
        source=((enc0+0xabbdcabf)&MASK)^key^0x2a357d90
        slot=enc1^key^0x1855aa65
        if source>>29!=5: continue
        if slot>=slot_count: raise ValueError('literal slot out of bounds')
        cell=slots_base+slot*8
        if cell in hits:
            hits[cell].append({'usageRow':i,'sourceIndex':source&0x1fffffff,'destinationSlot':slot,'cell':hex(cell),
                               'usageHex':group[n*8:n*8+8].hex(),'usageSHA256':sha(group[n*8:n*8+8])})
    if any(len(v)!=1 for v in hits.values()): raise ValueError('missing or duplicate target usage')
    return [hits[c][0] for c in CELLS]

def decode_header(headers):
    v=[int.from_bytes(bytes.fromhex(x['hex']),'little') for x in headers]
    return {'prefixBase':0x210+((v[0]+0x9deb89a5)&MASK),'dataBase':0x210+(v[1]^0x36c4f639),
            'groupsBase':0x210+(v[2]^0x25af313e),'usagesBase':0x210+(v[3]^0x43310f1f),
            'slotsBase':v[4],'slotCount':(v[5]+0x9e6f867a)&MASK,'globalGroup':(v[6]+0xd2070cd2)&MASK}

def boundaries(raw,g):
    if len(raw)!=8: raise ValueError('group boundary length')
    a,b=struct.unpack('<II',raw)
    return tuple(((x+0x8bff3e05)&MASK)^((i*0x5843eaa0-0x443c8630)&MASK) for i,x in [(g,a),(g+1,b)])

def output_path(name,suffix):
    p=OUT/name
    if p.parent.resolve()!=OUT.resolve() or not name.endswith(suffix): raise ValueError('output must be task-local basename')
    return p

def collect(name):
    out=output_path(name,'.json'); group_path=out.with_suffix('.group.bin'); fail=out.with_suffix('.failure.json')
    if any(p.exists() for p in (out,group_path,fail)): raise ValueError('output already exists')
    report={'task':'E-01/R3-PROUD-INDEX-LITERALS','stage':'source-gate','sourceSHA256':sha(Path(__file__).read_bytes()),'pins':PINS,
            'newCodeBytes':0,'pdataBytes':0,'xdataBytes':0,'nativeCPU':0,'liveReads':0,'inputChecks':{}}
    try:
        old=pin_sources()
        from tools.extract_client72 import PE, HASHES, HEADER_VA
        from tools.extract_client72_literals import USAGE_REGISTRATION, prefix, decrypt
        expected=HASHES|{'YuanShen_Data/Plugins/AccountPlatNative.dll':'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
                         'YuanShen_Data/Plugins/Astrolabe.dll':'87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442'}
        report['stage']='five-input-gate'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        keep={}
        for path,digest in expected.items():
            data=(client/path).read_bytes(); report['inputChecks'][path]={'bytes':len(data),'sha256':sha(data),'expectedSHA256':digest}
            if sha(data)!=digest: raise ValueError('input mismatch: '+path)
            if path=='YuanShen.exe' or path.endswith('global-metadata.dat'): keep[path]=data
        if report['inputChecks']!=old['inputChecks']: raise ValueError('five input sizes or hashes differ from old report')
        binary=keep['YuanShen.exe']; metadata=keep['YuanShen_Data/Managed/Metadata/global-metadata.dat']; pe=PE(binary)
        headers=[]
        for va,width in [(HEADER_VA+0x1e4,4),(HEADER_VA+0x124,4),(HEADER_VA+0x178,4),(HEADER_VA+0xf4,4),
                         (USAGE_REGISTRATION+0x58,8),(USAGE_REGISTRATION+4,4),(USAGE_REGISTRATION+0x54,4)]:
            off=pe.offset(va,width); headers.append({'address':hex(va),'hex':binary[off:off+width].hex()})
        if headers!=old['originalHeaderFields']: raise ValueError('header source differs')
        h=decode_header(headers); g=h['globalGroup']; group_raw=metadata[h['groupsBase']+g*4:h['groupsBase']+(g+2)*4]
        start,end=boundaries(group_raw,g)
        if (h['slotsBase'],h['slotCount'],g,start,end,group_raw.hex())!=(0x145ac9e20,87252,314557,698581,788696,old['globalGroupRecordHex']):
            raise ValueError('fixed registration bounds mismatch')
        if end<start or end-start>100000: raise ValueError('registration budget exceeded')
        lo=h['usagesBase']+start*8; hi=h['usagesBase']+end*8
        if hi>len(metadata): raise ValueError('registration outside metadata')
        group=metadata[lo:hi]
        if len(group)!=720920 or sha(group)!=GROUP_SHA or sha(group)!=old['globalGroupUsageSHA256']: raise ValueError('registration source mismatch')
        report.update(stage='unique-registration-gate',header=headers,headerBytes=32,decodedHeader=h,groupHex=group_raw.hex(),groupBoundaryBytes=8,headerSHA256=sha(b''.join(bytes.fromhex(v['hex']) for v in headers)),groupBoundarySHA256=sha(group_raw),
                      groupStart=start,groupEnd=end,registrationBytes=len(group),registrationSHA256=sha(group))
        hits=scan(group,start,h['slotsBase'],h['slotCount'])
        if [r['destinationSlot'] for r in hits]!=[48935,48936]: raise ValueError('destination mismatch')
        report['registrations']=hits; report['stage']='selected-prefix-gate'
        selected=[]
        for row in hits:
            idx=row['sourceIndex']; po=h['prefixBase']+idx*4
            if po+8>len(metadata): raise ValueError('prefix outside metadata')
            raw=metadata[po:po+8]; begin=prefix(int.from_bytes(raw[:4],'little'),idx); end=prefix(int.from_bytes(raw[4:],'little'),idx+1)
            if end<begin or 16+end-begin>256: raise ValueError('literal budget exceeded')
            if h['dataBase']+end>len(metadata): raise ValueError('cipher outside metadata')
            cipher=metadata[h['dataBase']+begin:h['dataBase']+end]; plain=decrypt(cipher,idx); value=plain.decode('utf8')
            if value.encode('utf8')!=plain: raise ValueError('UTF8 roundtrip mismatch')
            selected.append(dict(row,prefixOffset=hex(po),prefixHex=raw.hex(),prefixSHA256=sha(raw),cipherOffset=hex(h['dataBase']+begin),
                                 cipherHex=cipher.hex(),cipherSHA256=sha(cipher),plainHex=plain.hex(),plainSHA256=sha(plain),
                                 value=value,sourceBytes=16+len(cipher)))
        if sum(x['sourceBytes'] for x in selected)>512: raise ValueError('combined literal budget exceeded')
        report.update(stage='complete',passed=True,selected=selected,totalLiteralBytes=sum(x['sourceBytes'] for x in selected),
                      resourcePayloadRead=False,parserExecuted=False,actualIndexFilled=False,resourceReady=False)
        group_path.write_bytes(group)
        out.write_text(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf8')
        print(json.dumps({'reportSHA256':sha(out.read_bytes()),'groupSHA256':sha(group),'results':selected},ensure_ascii=False))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); fail.write_text(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf8')
        raise

def audit(first,second,name):
    old=pin_sources()
    a=output_path(first,'.json'); b=output_path(second,'.json'); out=output_path(name,'.json')
    if out.exists(): raise ValueError('output already exists')
    x=json.loads(a.read_bytes()); y=json.loads(b.read_bytes())
    if x!=y or x['sourceSHA256']!=sha(Path(__file__).read_bytes()) or x['pins']!=PINS or not x['passed']: raise ValueError('fresh reports differ')
    group=a.with_suffix('.group.bin').read_bytes(); group2=b.with_suffix('.group.bin').read_bytes()
    if group!=group2 or len(group)!=720920 or sha(group)!=GROUP_SHA: raise ValueError('fresh group differs')
    if x['inputChecks']!=old['inputChecks'] or x['header']!=old['originalHeaderFields']: raise ValueError('input/header source mismatch')
    h=decode_header(x['header']); raw=bytes.fromhex(x['groupHex']); start,end=boundaries(raw,h['globalGroup'])
    if (h!=x['decodedHeader'] or (start,end)!=(698581,788696) or x['groupHex']!=old['globalGroupRecordHex']
        or x['headerSHA256']!=sha(b''.join(bytes.fromhex(v['hex']) for v in x['header'])) or x['groupBoundarySHA256']!=sha(raw)):
        raise ValueError('header/group decode mismatch')
    hits=scan(group,start,h['slotsBase'],h['slotCount'])
    if hits!=x['registrations']: raise ValueError('registration mismatch')
    from tools.extract_client72_literals import prefix,decrypt
    for row,sel in zip(hits,x['selected']):
        for k,v in row.items():
            if sel[k]!=v: raise ValueError('selected row mismatch')
        idx=row['sourceIndex']; p=bytes.fromhex(sel['prefixHex']); cipher=bytes.fromhex(sel['cipherHex'])
        if len(p)!=8 or sel['prefixSHA256']!=sha(p) or sel['prefixOffset']!=hex(h['prefixBase']+idx*4): raise ValueError('prefix mismatch')
        begin=prefix(int.from_bytes(p[:4],'little'),idx); finish=prefix(int.from_bytes(p[4:],'little'),idx+1)
        plain=decrypt(cipher,idx)
        if (finish-begin!=len(cipher) or sel['cipherOffset']!=hex(h['dataBase']+begin) or sel['cipherSHA256']!=sha(cipher)
            or sel['plainHex']!=plain.hex() or sel['plainSHA256']!=sha(plain) or sel['value'].encode('utf8')!=plain
            or sel['sourceBytes']!=16+len(cipher) or sel['sourceBytes']>256): raise ValueError('literal decode mismatch')
    if sum(s['sourceBytes'] for s in x['selected'])!=x['totalLiteralBytes'] or x['totalLiteralBytes']>512: raise ValueError('total budget mismatch')
    result={'task':x['task'],'passed':True,'freshReportsSHA256':sha(a.read_bytes()),'registrationSHA256':sha(group),
            'selected':x['selected'],'offlineCurrentPEReads':0,'nativeCPU':0,'liveReads':0,'resourceReady':False}
    out.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'auditSHA256':sha(out.read_bytes()),'selected':x['selected']},ensure_ascii=False))

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2)
    a=p.parse_args()
    if a.audit: audit(a.audit[0],a.audit[1],a.output)
    else: collect(a.output)

if __name__=='__main__': main()
