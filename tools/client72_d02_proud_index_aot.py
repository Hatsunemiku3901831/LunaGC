"""Bounded closed-AOT identity join for field 400190; no target code or execution."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-index-aot'
PINS = {
    'local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin': 'c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/trace_client72_avatar_core.py': '5fdaa689fdf1737c63a9fd039dd73344873d49265c0225e3177e80f0ffe7cda3',
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl': 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    'local/migration72/evidence/client72-signatures.json': '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/collect_closed_aot.private.py': 'a43824fab5674d69d85a04bdb2a348cb0634ba8f32a78cf49a022f2e916144aa',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/closed-aot-final1.private.json': '6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/closed-aot-final2.private.json': '6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e',
    'local/migration72/d02-resume-20261110b/c-proud-row-reader/next-input-spec.zh.md': 'b9073f9e4955f531c4504f2665e87dd6bd039a3bfeaf452adebbb664c8ed0123',
    'local/migration72/d02-resume-20261110b/c-proud-row-reader/final1.private.json': '90c3920795c14fcb1ef2058eddd8e3692ca189e59703cd08b372fcd675f2aeee',
}
FIVE = {
    'YuanShen.exe':'8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
    'YuanShen_Data/Managed/Metadata/global-metadata.dat':'45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a',
    'YuanShen_Data/Managed/Metadata/startup-metadata.dat':'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105',
    'YuanShen_Data/Plugins/AccountPlatNative.dll':'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
    'YuanShen_Data/Plugins/Astrolabe.dll':'87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442',
}
HEADER = [('specBase',0x142806bf0,4,'5fb55063'),('specSize',0x142806aa4,4,'c9d15132'),
          ('tableBase',0x142806b6c,4,'2cbf7e3f'),('tableSize',0x142806b90,4,'7946a05c'),
          ('pointerCount',0x1422f9944,4,'7f0d5e61'),('pointerBase',0x1422f9910,8,'b00c474201000000')]
TABLE_HASHES = ('94c576e22b94971933613fef1855ac9223b505e74e2c78974d370e0109df57c8',
                '1af68b22c3f8f66ac55e64440f9a83d0a4aabd112bb587c427fbd0442c707b96')

def digest(data):
    return hashlib.sha256(data).hexdigest()

def pick_candidates(records, signatures):
    field = next(f for f in records[80063]['fields'] if f['index'] == 400190)
    t = field['type']
    if (field['rawOffset'],t['attributes'],t['typeDefinition'],t['instanceIndex'],
        [a.get('typeDefinition',a['kind']) for a in t['arguments']]) != ('0x4021f58','0x31',18192,5071,['uint32',75077,47647,25030]):
        raise ValueError('field identity mismatch')
    sigs = {m['index']:m for m in signatures['methods']}
    chosen = []
    for m in records[18192]['methods']:
        params = sigs.get(m['index'],{}).get('parameters',[])
        types = [signatures['typeReferences'][str(p['typeIndex'])] for p in params]
        if len(types)==2 and [(x['kind'],x.get('genericParameterPosition'),x['byRef']) for x in types] == [('var',0,False),('var',1,True)]:
            chosen.append(m['index'])
    return field, chosen

def select_rows(table, specs, methods, candidates, pointer_count):
    selected = []
    tuple_rows = []
    for i in range(len(table)//14):
        pointer, spec = struct.unpack_from('<ii',table,i*14)
        if not 0 <= pointer < pointer_count or not 0 <= spec < len(specs)//12:
            raise ValueError('table reference out of bounds')
        method, cls, inst = struct.unpack_from('<iii',specs,spec*12)
        owner = methods[method][0]
        if owner == 18192 and cls == 5071:
            row=(i,pointer,spec,method,cls,inst)
            tuple_rows.append(row)
            if method in candidates:
                selected.append(row)
    if len({r[1] for r in selected}) > 32:
        raise ValueError('pointer budget exceeds 32 QWORD')
    return selected, tuple_rows

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--output',required=True); args=p.parse_args()
    output=OUT / args.output
    if output.parent.resolve()!=OUT.resolve() or output.suffix!='.json' or output.exists() or output.with_suffix('.failure.json').exists():
        p.error('output must be a new task-local JSON basename')
    report={'task':'E-01/R3-PROUD-INDEX-AOT','sourceSHA256':digest(Path(__file__).read_bytes()),'pins':PINS,
            'stage':'source-gate','pointerQWORDCount':0,'targetCPU':0,'liveReads':0,'newCodeBytes':0,'pdataBytes':0,'xdataBytes':0}
    try:
        frozen={name:(ROOT/name).read_bytes() for name in PINS}
        for name,raw in frozen.items():
            if digest(raw)!=PINS[name]: raise ValueError('source mismatch: '+name)
        caller=frozen['local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin']
        call=caller[0x61:0x66]
        if len(caller)!=606 or call.hex()!='e87aeb840a' or 0x14d7ad946+int.from_bytes(call[1:],'little',signed=True)!=0x157ffc4c0:
            raise ValueError('original caller E8 mismatch')
        report['caller']={'entry':'0x14d7ad8e0','site':'0x14d7ad941','hex':call.hex(),'target':'0x157ffc4c0','sha256':digest(caller)}
        from tools.extract_client72 import PE
        records,_=pickle.loads(frozen[next(n for n in PINS if n.endswith('.pkl'))])
        signatures=json.loads(frozen[next(n for n in PINS if n.endswith('client72-signatures.json'))])
        field,candidates=pick_candidates(records,signatures)
        report.update(field=field,signatureCandidates=candidates,stage='five-input-gate',inputs={})
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        data={}
        for name,expected in FIVE.items():
            raw=(client/name).read_bytes(); actual=digest(raw)
            report['inputs'][name]={'bytes':len(raw),'sha256':actual}
            if actual!=expected: raise ValueError('client input mismatch: '+name)
            if name=='YuanShen.exe' or name.endswith('global-metadata.dat'): data[name]=raw
        binary=data['YuanShen.exe']; metadata=data['YuanShen_Data/Managed/Metadata/global-metadata.dat']; pe=PE(binary)
        vals={}; header=[]
        for name,va,width,expected in HEADER:
            raw=binary[pe.offset(va,width):][:width]
            if raw.hex()!=expected: raise ValueError('header mismatch: '+name)
            vals[name]=int.from_bytes(raw,'little'); header.append({'name':name,'address':hex(va),'hex':raw.hex()})
        sb=0x210+(vals['specBase']^0x6347382b); ss=vals['specSize']^0x323bae70
        tb=0x210+(vals['tableBase']^0x3cf0720c); ts=(vals['tableSize']+0xa3d9cc8b)&0xffffffff
        pc=(vals['pointerCount']+0x9ea91e3b)&0xffffffff; pb=vals['pointerBase']
        if (sb,ss,tb,ts,pc,pb)!=(0x178f84,6979513,0x38ecf30,8000260,469946,0x142470cb0): raise ValueError('decoded header mismatch')
        specs=metadata[sb:sb+ss]; table=metadata[tb:tb+ts]
        if (digest(specs),digest(table))!=TABLE_HASHES: raise ValueError('table SHA mismatch')
        report.update(stage='table-join',header=header,headerBytes=28,decoded={'specBase':hex(sb),'specSize':ss,'tableBase':hex(tb),'tableSize':ts,'pointerBase':hex(pb),'pointerCount':pc},tableSHA256=list(TABLE_HASHES))
        methods={m['index']:(r['index'],m) for r in records for m in r['methods']}
        selected,tuple_rows=select_rows(table,specs,methods,candidates,pc)
        report['allTupleRows']=[{'tableIndex':i,'pointerIndex':ptr,'methodSpec':sp,'methodDefinition':m,'method':methods[m][1]['name'],'classInstance':cl,'methodInstance':inst,'tableRecord14':table[i*14:i*14+14].hex(),'specRecord12':specs[sp*12:sp*12+12].hex()} for i,ptr,sp,m,cl,inst in tuple_rows]
        report['selectedRowsBeforePointerRead']=[{'tableIndex':i,'pointerIndex':ptr,'methodSpec':sp,'methodDefinition':m,'classInstance':cl,'methodInstance':inst} for i,ptr,sp,m,cl,inst in selected]
        pointers={}
        for ptr in sorted({r[1] for r in selected}):
            slot=pb+ptr*8; raw=binary[pe.offset(slot,8):][:8]
            pointers[ptr]={'slot':hex(slot),'qword':raw.hex(),'address':hex(int.from_bytes(raw,'little'))}
            report['pointerQWORDCount']=len(pointers)
        report['pointers']=pointers
        target={ptr for ptr,v in pointers.items() if v['address']=='0x157ffc4c0'}
        sigs={m['index']:m for m in signatures['methods']}
        shared=[]
        for i in range(len(table)//14):
            ptr,sp=struct.unpack_from('<ii',table,i*14)
            if ptr not in target: continue
            m,cl,inst=struct.unpack_from('<iii',specs,sp*12); owner,decl=methods[m]
            params=sigs.get(m,{}).get('parameters',[])
            shared.append({'tableIndex':i,'pointerIndex':ptr,'methodSpec':sp,'owner':owner,'methodDefinition':m,'method':decl['name'],
                'classInstance':cl,'methodInstance':inst,'parameters':[dict(x,resolved=signatures['typeReferences'][str(x['typeIndex'])]) for x in params],
                'tableRecord14':table[i*14:i*14+14].hex(),'specRecord12':specs[sp*12:sp*12+12].hex(),'pointer':pointers[ptr]})
        report.update(stage='complete',targetPointerIndices=sorted(target),samePointerIndexRows=shared,
                      crossPointerIndexSameCodeComplete=False,identityOnly=True,methodSemanticsProven=False,actualMapExecuted=False,
                      identityUnique=(len(candidates)==1 and len(target)==1 and len(selected)==1),partial=True,pointerBytesRead=8*len(pointers))
        raw=(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8'); output.write_bytes(raw)
        print(json.dumps({'sha256':digest(raw),'bytes':len(raw),'selected':len(selected),'pointers':len(pointers),'target':sorted(target),'shared':len(shared),'partial':report['partial']}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e))
        output.with_suffix('.failure.json').write_text(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf8')
        raise

if __name__=='__main__': main()
