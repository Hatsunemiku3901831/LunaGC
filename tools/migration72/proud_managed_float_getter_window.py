"""One bounded ordinary-AOT float getter source window; no target execution."""
import argparse
import hashlib
import importlib.util
import json
import pickle
import re
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-managed-row-remaining-triage/float-getter-window'
ADD=OUT/'spec-addendum.private.json'
ADD_SHA='d502d718d8718772f4b15d359501860595376320afecdbb3741a930362fbc66f'
REQUEST=OUT.parent/'next-float-getter-source-request.private.json'
ENTRY=0x14f905140
CELL=0x1422f9950
POINTER=0x1428a54d0
SLOT=0x142c1d9a0
FILES=('boundary.private.json','pre-body.private.json','aot-cell.bin','aot-slot.bin','pdata.bin','body.bin','body.asm.private.json','result.private.json')

def require(ok,message):
    if not ok: raise ValueError(message)
def sha(data): return hashlib.sha256(data).hexdigest()
def check(path,size,digest):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''): h.update(b); n+=len(b)
    require(n==size and h.hexdigest()==digest,'fixed pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}
def save(path,value):
    data=value if isinstance(value,bytes) else (json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode()
    with path.open('xb') as f: f.write(data)
class DataOnly(pickle.Unpickler):
    def find_class(self,*args): raise ValueError('pickle globals forbidden')

def verify_metadata(records):
    owner=records[54724]; methods=[m for m in owner['methods'] if m['index']==454810]
    require(owner['index']==54724 and owner['name']=='EBGBPCMHIGK' and len(methods)==1,'owner or method mismatch')
    m=methods[0]
    require((m['name'],m['registeredAddress'],m['flags'],m['parameterCount'],m['virtualSlot'])==('GOCKGKFKOID',hex(ENTRY),'0x886',0,65535),'ordinary declaration mismatch')
    fields=[f for f in owner['fields'] if f['index']==271929]
    require(len(fields)==1 and fields[0]['type']['kind']=='uint32' and fields[0]['offset']==16 and fields[0]['offsetKind']=='instance','stored field mismatch')
    aliases=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(aliases==[(54724,454810)],'ordinary address ambiguous')
    return {'owner':54724,'method':m,'storedField':fields[0],'unboxedOffset':0,'logicalReturn':'not-recovered','addressAliases':aliases}

def source_gate():
    check(ADD,ADD.stat().st_size,ADD_SHA); add=json.loads(ADD.read_bytes())
    pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(add['pins'].items())}
    req=json.loads(REQUEST.read_bytes()); profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    require((req['entry'],req['owner'],req['method'],req['flags'],req['parameterCount'])==(hex(ENTRY),54724,454810,'0x886',0),'request identity')
    require(POINTER+454810*8==SLOT,'AOT slot arithmetic')
    items={x['path']:x for x in profile['files'].values() if isinstance(x,dict) and 'path' in x}
    for p,v in req['stockIdentity'].items():
        require(p in items and items[p]['sha256']==v['sha256'] and items[p]['size']==v['bytes'],'profile stock mismatch')
    with (ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').open('rb') as f: records,_=DataOnly(f).load()
    metadata=verify_metadata(records)
    path=ROOT/'tools/migration72/proud_current_adoption_source_windows.py'; spec=importlib.util.spec_from_file_location('float_pinned_filepe',path); base=importlib.util.module_from_spec(spec); spec.loader.exec_module(base)
    return req,pins,metadata,base

def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata')
    all_rows=[]; exact=[]; containing=[]; t=ENTRY-base
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        if begin==t or begin<=t<end:
            row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
            if begin==t: exact.append(row)
            if begin<=t<end: containing.append(row)
    overlapping=[]
    if len(exact)==1:
        end=int(exact[0]['endExclusive'],16)-base
        for n,(a,b,u) in enumerate(struct.iter_unpack('<III',table)):
            if a<end and b>t:
                overlapping.append({'begin':hex(base+a),'endExclusive':hex(base+b),'size':b-a,'unwindRVA':hex(u),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()})
    return {'exact':exact,'containing':containing,'overlapping':overlapping}

def admit(diag,section,file_size):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping'],'pdata missing or ambiguous')
    row=diag['exact'][0]; size=row['size']; require(row['begin']==hex(ENTRY) and 0<size<=256 and int(row['endExclusive'],16)==ENTRY+size,'pdata extent invalid or over cap')
    s=section(ENTRY,size,True); offset=s['raw']+ENTRY-s['begin']; require(offset>=0 and offset+size<=file_size,'body outside file')
    return row

def decode(code,cs,cap=128):
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    rows=[]; cursor=ENTRY; instructions=cs.disasm(code,ENTRY,count=cap)
    try:
        while cursor<ENTRY+len(code) and len(rows)<cap:
            ins=next(instructions,None)
            if ins is None: return rows,'incomplete-decode'
            raw=bytes(ins.bytes)
            if ins.address!=cursor or raw!=code[cursor-ENTRY:cursor-ENTRY+len(raw)]: return rows,'decode-gap'
            call=ins.group(capstone.CS_GRP_CALL); jump=ins.group(capstone.CS_GRP_JUMP)
            direct=hex(ins.operands[0].imm) if (call or jump) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
            refs=[{'address':hex(ins.address+ins.size+op.mem.disp),'rawAccess':op.access} for op in ins.operands if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP]
            rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'jump':jump,'directTarget':direct,'ripReferences':refs}); cursor+=len(raw)
        return rows,('complete' if cursor==ENTRY+len(code) else 'instruction-cap')
    finally:
        instructions.close()

def listing_compare(rows,listing):
    listed={}
    for line in listing.splitlines():
        m=re.match(r'^([0-9a-fA-F]+):\s*(.*)$',line)
        if m: listed[int(m.group(1),16)]=m.group(2).strip()
    normal=lambda s: re.sub(r'\s+',' ',s.strip().lower())
    common=[]; mismatches=[]
    for row in rows:
        pc=int(row['address'],16)
        if pc in listed:
            actual=(row['mnemonic']+' '+row['operands']).strip(); match=normal(actual)==normal(listed[pc]); common.append(pc)
            if not match: mismatches.append({'address':hex(pc),'listing':listed[pc],'actual':actual})
    return {'listingRows':len(listed),'commonRows':len(common),'textMismatches':mismatches,
            'listingOnly':[hex(x) for x in listed if x not in {int(r['address'],16) for r in rows}],
            'rawOnly':[r['address'] for r in rows if int(r['address'],16) not in listed],
            'listingIsNotBoundary':True}

def read_at(pe,address,size,kind,report,executable=False):
    record={'kind':kind,'address':hex(address),'requested':size,'returned':0,'offset':None}; report['reads'].append(record)
    value=pe.at(address,size,executable)
    require(isinstance(value,tuple) and len(value)==2 and isinstance(value[0],bytes) and isinstance(value[1],int),'FilePE.at shape mismatch')
    raw,offset=value; record.update(returned=len(raw),offset=offset)
    if kind in ('aot-cell','aot-slot'):
        report['aotBytes']+=len(raw); record['hex']=raw.hex()
    if kind=='body':
        report['bodyRead']+=len(raw); record['hex']=raw.hex()
    require(len(raw)==size,'short '+kind); return raw

def collect(dest):
    report={'task':'R3-PROUD-MANAGED-FLOAT-GETTER-ONE-SOURCE','stage':'source-gate','passed':False,'toolSHA256':sha(Path(__file__).read_bytes()),'addendumSHA256':ADD_SHA,'bodyRead':0,'bodyReadCalls':0,'aotBytes':0,'pdataBytes':0,'freshCallerBytes':0,'globalTableSeedBytes':0,'otherCalleeBytes':0,'targetCPU':0,'nativeExecution':0,'live':0,'rawCallerAvailable':False,'logicalReturn':'not-recovered','actualGetter':None,'actualRow':False,'resourceReady':False,'reads':[]}
    try:
        req,report['pins'],report['typedMetadata'],base=source_gate()
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True,encoding='utf-8').strip()).resolve()
        report['stage']='stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            pe=base.FilePE(f); report['header']=pe.meta; require(pe.meta['bytesRead']<=8192,'header budget')
            report['stage']='aot'; cell=read_at(pe,CELL,8,'aot-cell',report); save(dest/'aot-cell.bin',cell)
            require(int.from_bytes(cell,'little')==POINTER and POINTER+454810*8==SLOT,'AOT cell mismatch')
            slot=read_at(pe,SLOT,8,'aot-slot',report); save(dest/'aot-slot.bin',slot); require(int.from_bytes(slot,'little')==ENTRY,'AOT slot mismatch')
            report['stage']='pdata'; require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'exception directory drift')
            table=read_at(pe,pe.base+pe.pdata_rva,pe.pdata_size,'exception-directory',report); require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory mismatch')
            diag=find_pdata(table,pe.base,pe.pdata_rva); report['pdataDiagnostic']=diag; save(dest/'boundary.private.json',report)
            try:
                row=admit(diag,pe.section,req['stockIdentity']['YuanShen.exe']['bytes'])
            except ValueError:
                report['stage']='pre-body-stop'; save(dest/'pre-body.private.json',report); raise
            save(dest/'pdata.bin',bytes.fromhex(row['recordHex'])); report['pdataBytes']=12; report['selected']=row
            require(report['aotBytes']+report['pdataBytes']+row['size']<=284,'evidence budget')
            report['stage']='pre-body'; save(dest/'pre-body.private.json',report)
            report['stage']='body'; report['bodyReadCalls']=1
            code=read_at(pe,ENTRY,row['size'],'body',report,True); save(dest/'body.bin',code)
            rows,state=decode(code,base.decoder()); save(dest/'body.asm.private.json',{'state':state,'instructions':rows})
            require(state=='complete','decode '+state)
            report['body']={'bytes':len(code),'sha256':sha(code),'instructions':len(rows),'state':state}
            report['references']={'directControl':[{'site':r['address'],'target':r['directTarget'],'mnemonic':r['mnemonic']} for r in rows if r['directTarget'] is not None], 'rip':[{'site':r['address'],**ref} for r in rows for ref in r['ripReferences']], 'returns':[r['address'] for r in rows if r['mnemonic'].startswith('ret')], 'movdXMM0':[r for r in rows if r['mnemonic']=='movd' and r['operands'].startswith('xmm0,')]}
            report['listingComparison']=listing_compare(rows,(ROOT/'local/migration72/analysis/disasm-14f905140.txt').read_text(encoding='utf-8'))
            report.update(stage='complete',passed=True)
        save(dest/'result.private.json',report); print(json.dumps({'body':report['body'],'references':report['references']})); return 0
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,name):
    a,b=OUT/first,OUT/second
    require(sorted(x.name for x in a.iterdir())==sorted(FILES)==sorted(x.name for x in b.iterdir()),'artifact file inventory')
    raw={n:(a/n).read_bytes() for n in FILES}; require(all(raw[n]==(b/n).read_bytes() for n in FILES),'fresh artifacts differ')
    report=json.loads(raw['result.private.json']); pre=json.loads(raw['pre-body.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(report['passed'] and pre['bodyRead']==boundary['bodyRead']==0 and pre['selected']==report['selected'],'prebody/result')
    require(int.from_bytes(raw['aot-cell.bin'],'little')==POINTER and int.from_bytes(raw['aot-slot.bin'],'little')==ENTRY and raw['pdata.bin'].hex()==report['selected']['recordHex'],'AOT/pdata')
    code=raw['body.bin']; asm=json.loads(raw['body.asm.private.json']); require(len(code)==report['selected']['size'] and sha(code)==report['body']['sha256'] and len(asm['instructions'])==report['body']['instructions'] and asm['state']=='complete','body identity')
    require(b''.join(bytes.fromhex(x['hex']) for x in asm['instructions'])==code and report['aotBytes']==16 and report['pdataBytes']==12 and report['bodyRead']==len(code) and report['bodyReadCalls']==1,'ledger')
    require(all(report[k]==0 for k in ('freshCallerBytes','globalTableSeedBytes','otherCalleeBytes','targetCPU','nativeExecution','live')),'scope drift')
    save(OUT/name,{'passed':True,'mode':'offline artifact equality; no current PE or target','artifacts':{n:{'bytes':len(data),'sha256':sha(data)} for n,data in raw.items()}})

def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); a=p.parse_args()
    require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'new basename required')
    if a.audit:
        require(a.output.startswith('audit-') and a.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in a.audit),'audit name'); audit(*a.audit,a.output); return 0
    require(a.output in ('fresh1','fresh2','main-third'),'fresh name'); dest=OUT/a.output; dest.mkdir(); return collect(dest)
if __name__=='__main__': sys.exit(main())
