"""Collect one fixed FindEntry source window offline, with pre-body gates."""
import argparse
import hashlib
import importlib.util
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=0x140000000
ENTRY=0x1578b66f0
END=0x1578b6987
HASH='eb3bd9ba203b582c2e9b151b1ea8b369e0da775a6aeeccdf81f5b6450bff8b3b'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-nativewrapper-ctor-borrow-contract/findentry-source-request.private.json'
OUT=REQUEST.parent/'standard-findentry-window'
CALLER=ROOT/'local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/source-windows/fresh3'
OLD=ROOT/'tools/migration72/proud_current_adoption_source_windows.py'

def require(ok,message):
    if not ok: raise ValueError(message)
def sha(b): return hashlib.sha256(b).hexdigest()
def check(path,size,digest):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk); n+=len(chunk)
    require(n==size and h.hexdigest()==digest,'pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}
def save(path,value):
    with path.open('xb') as f: f.write((json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())
class DataOnly(pickle.Unpickler):
    def find_class(self,*args): raise ValueError('pickle globals forbidden')

def validate_caller(raw,asm):
    require(len(raw)==689 and asm['decodeState']=='complete','caller extent/decode')
    cursor=0x14d7ac240
    for row in asm['instructions']:
        b=bytes.fromhex(row['hex']); require(int(row['address'],16)==cursor and raw[cursor-0x14d7ac240:cursor-0x14d7ac240+len(b)]==b,'caller reassembly'); cursor+=len(b)
    require(cursor==0x14d7ac240+689,'caller coverage')
    setup=raw[0x158:0x162]; require(setup.hex()=='4889d989fae84ea3100a','caller setup')
    require(0x14d7ac39d+5+struct.unpack('<i',setup[-4:])[0]==ENTRY,'caller E8 target')
    rows=[(r['address'],r['mnemonic'],r['operands']) for r in asm['instructions'] if 0x14d7ac398<=int(r['address'],16)<0x14d7ac3a2]
    require(rows==[('0x14d7ac398','mov','rcx, rbx'),('0x14d7ac39b','mov','edx, edi'),('0x14d7ac39d','call','0x1578b66f0')],'caller physical arguments')

def declaration(records,req):
    typed=req['typedField']; owner=records[typed['owner']]
    require(owner['name']==typed['ownerName'] and [f for f in owner['fields'] if f['index']==400191]==[typed['field']],'typed field')
    generic=req['aotProvenance']['openGenericMethodDeclaration']
    require([m for m in records[1222]['methods'] if m['index']==10194]==[generic],'open generic declaration')
    require(not [m for r in records for m in r['methods'] if m.get('registeredAddress')==hex(ENTRY)],'ordinary target binding drift')

def source_gate():
    check(REQUEST,12006,'8bc6f7abafa33dce6df4010c226e212559323c564c310eb036ed999e14abb903')
    req=json.loads(REQUEST.read_bytes()); pins={}
    for p,v in sorted(req['pins'].items()): pins[p]=check(ROOT/p,v['bytes'],v['sha256'])
    check(OLD,OLD.stat().st_size,'48b094200d8f05be66c570412d47522eccdc3a0b23741e1eb61ffb9eb3bb16d6')
    for name in ('fresh3','fresh4'):
        root=CALLER.parent/name; validate_caller((root/'code-663528.bin').read_bytes(),json.loads((root/'asm-663528.private.json').read_bytes()))
    with (ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').open('rb') as f: records,_=DataOnly(f).load()
    declaration(records,req)
    hint=json.loads((ROOT/req['legacyHint']['source']).read_bytes())['approvedCodeManifest'][hex(ENTRY)]
    require(hint['end']==hex(END) and hint['sha256']==HASH and req['entry']==hex(ENTRY),'legacy hint drift')
    spec=importlib.util.spec_from_file_location('findentry_base',OLD); base=importlib.util.module_from_spec(spec); spec.loader.exec_module(base)
    return req,pins,base

def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]; overlapping=[]; target=ENTRY-base; end0=END-base
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        if begin==target or begin<=target<end or (begin<end0 and end>target):
            row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
            if begin==target: exact.append(row)
            if begin<=target<end: containing.append(row)
            if begin<end0 and end>target: overlapping.append(row)
    return {'exact':exact,'containing':containing,'overlapping':overlapping}
def admit(diag,section,file_size):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping'],'pdata missing/ambiguous')
    row=diag['exact'][0]; require(row['begin']==hex(ENTRY) and row['endExclusive']==hex(END) and row['size']==663,'fixed extent drift')
    s=section(ENTRY,663,True); require(s['raw']+ENTRY-s['begin']+663<=file_size,'file boundary'); return row

def decode(code):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; rows=[]; cursor=ENTRY
    for ins in cs.disasm(code,ENTRY,count=384):
        raw=bytes(ins.bytes)
        if ins.address!=cursor or raw!=code[cursor-ENTRY:cursor-ENTRY+len(raw)]: return rows,'decode-gap'
        call=ins.group(capstone.CS_GRP_CALL); jump=ins.group(capstone.CS_GRP_JUMP)
        target=hex(ins.operands[0].imm) if (call or jump) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
        refs=[{'address':hex(ins.address+ins.size+op.mem.disp),'rawAccess':op.access} for op in ins.operands if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP]
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'jump':jump,'directTarget':target,'ripReferences':refs}); cursor+=ins.size
    if cursor==ENTRY+len(code): return rows,'complete'
    return rows,('instruction-cap' if len(rows)==384 else 'incomplete-decode')
def body_gate(code):
    require(len(code)==663,'short body'); require(sha(code)==HASH,'body hash drift')
def index(rows):
    return {'calls':[{'site':r['address'],'target':r['directTarget']} for r in rows if r['call']], 'rip':[{'site':r['address'],**ref} for r in rows for ref in r['ripReferences']]}

def collect(dest):
    report={'task':'R3-PROUD-STANDARD-CACHE-SINGLE-KEY-FINDENTRY-SOURCE','stage':'source-gate','bodyRead':0,'bodyReadCalls':0,'pdataEvidenceBytes':0,'aotBytes':0,'freshCallerBytes':0,'globalOrComparerBytes':0,'otherBodyBytes':0,'targetCPU':0,'live':0,'actualk':None,'actualP':None,'actualEncoded':None,'actualCurrentAdoption':None,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins'],base=source_gate(); game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={}
        for p,v in req['stockIdentity'].items(): report['stock'][p]=check(game/p,v['bytes'],v['sha256'])
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            pe=base.FilePE(f); report['metadata']=pe.meta; report['stage']='pdata-before-body'
            require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'directory drift')
            table,offset=pe.at(pe.base+pe.pdata_rva,pe.pdata_size)
            report['exceptionDirectory']={'rva':hex(pe.pdata_rva),'fileOffset':offset,'bytesRead':len(table),'sha256':sha(table)}
            require(len(table)==pe.pdata_size and sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory identity')
            diag=find_pdata(table,pe.base,pe.pdata_rva); report['exceptionDirectory']['diagnostic']=diag; save(dest/'boundary.private.json',report)
            try: row=admit(diag,pe.section,req['stockIdentity']['YuanShen.exe']['bytes'])
            except ValueError as e:
                report.update(stage='boundary-stop',passed=False,reason=str(e)); save(dest/'pre-body.private.json',report); save(dest/'result.private.json',report); return 2
            (dest/'pdata.bin').write_bytes(bytes.fromhex(row['recordHex'])); report['pdataEvidenceBytes']=12; report['selected']=row
            report['budget']={'bodyMax':663,'instructionMax':384,'pdataBytes':12,'boundedEvidenceMax':675,'aotBytes':0,'freshCallerBytes':0,'admitted':True}; save(dest/'pre-body.private.json',report)
            report['stage']='body'; code,offset=pe.at(ENTRY,663,True); report['bodyRead']=len(code); report['bodyReadCalls']=1; (dest/'code.bin').write_bytes(code)
            report['body']={'entry':hex(ENTRY),'fileOffset':offset,'bytes':len(code),'sha256':sha(code)}
            try: body_gate(code)
            except ValueError as e:
                report.update(stage='body-stop',passed=False,reason=str(e)); save(dest/'result.private.json',report); return 2
            rows,state=decode(code); save(dest/'code.asm.private.json',{'instructions':rows,'decodeState':state})
            report['body'].update(instructions=len(rows),decodeState=state); report.update(stage='complete' if state=='complete' else 'decode-stop',passed=state=='complete',sourceIndex=index(rows)); save(dest/'result.private.json',report)
            print(json.dumps({'stage':report['stage'],'body':report['body']})); return 0 if report['passed'] else 2
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(x.name for x in a.iterdir()); require(names==sorted(x.name for x in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    req=json.loads(REQUEST.read_bytes()); result=json.loads(raw['result.private.json']); pre=json.loads(raw['pre-body.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'pin report')
    validate_caller((CALLER/'code-663528.bin').read_bytes(),json.loads((CALLER/'asm-663528.private.json').read_bytes()))
    require(result['stage']=='complete' and boundary['bodyRead']==pre['bodyRead']==0 and boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'prebody/result')
    row=admit(result['exceptionDirectory']['diagnostic'],lambda *args:{'raw':0,'begin':ENTRY},663); require(raw['pdata.bin'].hex()==row['recordHex'],'pdata artifact')
    code=raw['code.bin']; body_gate(code); rows,state=decode(code)
    require(state=='complete' and {'instructions':rows,'decodeState':state}==json.loads(raw['code.asm.private.json']) and result['sourceIndex']==index(rows),'body artifact')
    require(result['bodyRead']==663 and result['bodyReadCalls']==1 and result['pdataEvidenceBytes']==12 and all(result[x]==0 for x in ('aotBytes','freshCallerBytes','globalOrComparerBytes','otherBodyBytes','targetCPU','live')),'read accounting')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'sameDecoderUsed':True,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})

def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','v2-fresh1','v2-fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output); return 0
    require(args.output in ('fresh1','fresh2','v2-fresh1','v2-fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); return collect(dest)
if __name__=='__main__': sys.exit(main())
