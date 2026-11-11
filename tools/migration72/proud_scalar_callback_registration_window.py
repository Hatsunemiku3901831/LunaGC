"""Collect only the approved registration function if complete pdata admits it."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-scalar-table-builder-semantics/callback-registration-window'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-scalar-table-builder-semantics/next-window-request.private.json'
ENTRY=0x1411267b0
EC8=0x145744ec8
EXTRA={'tools/game_path.ps1':(3506,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'), 'local/migration72/python/capstone/__init__.py':(43853,'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'), 'local/migration72/python/capstone/x86.py':(2816,'08cbb0a02ff152bcf1fbd7c4f8850b3ade38c25b9f0a6a9b900a55ee75e87c67'), 'local/migration72/python/capstone/x86_const.py':(45756,'83cf4550d9256f5f9b8e218b9b732e46d83ef602b463a01d6eeb778deb28e040'), 'local/migration72/python/capstone/lib/capstone.dll':(None,'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3')}


def require(ok,message):
    if not ok: raise ValueError(message)


def sha(data): return hashlib.sha256(data).hexdigest()


def check(path,size,digest):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk); n+=len(chunk)
    require((size is None or n==size) and h.hexdigest()==digest,'pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}


def save(path,value):
    with path.open('xb') as f: f.write((json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())


def source_gate():
    check(REQUEST,9091,'b2fd5057147a867aee5963b8295682935790a1a8864becdbd4ed44f93a248c23')
    req=json.loads(REQUEST.read_bytes()); pins=dict(req['pins']); pins.update({p:{'bytes':n,'sha256':h} for p,(n,h) in EXTRA.items()})
    verified={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(pins.items())}
    require(req['entry']==hex(ENTRY) and req['methodIndex'] is None and req['ordinaryAOTRequested'] is False and not req['caller']['directE8ToThisEntryRetained'],'entry basis drift')
    return req,verified


class FilePE:
    def __init__(self,f):
        self.f=f; h=self.read(0,4096); nt=struct.unpack_from('<I',h,0x3c)[0]
        require(h[:2]==b'MZ' and h[nt:nt+4]==b'PE\0\0' and struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'PE header')
        self.base=struct.unpack_from('<Q',h,nt+48)[0]; require(self.base==0x140000000,'PE base')
        count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]; table=self.read(nt+24+opt,count*40); self.sections=[]
        for i in range(count):
            off=i*40; rva,size,raw=struct.unpack_from('<III',table,off+12); flags=struct.unpack_from('<I',table,off+36)[0]
            self.sections.append({'begin':self.base+rva,'end':self.base+rva+size,'raw':raw,'flags':flags})
        self.pdata_rva,self.pdata_size=struct.unpack_from('<II',h,nt+24+0x70+3*8)
    def read(self,offset,size):
        self.f.seek(offset); raw=self.f.read(size); require(len(raw)==size,'short file read'); return raw
    def section(self,va,size,executable=False):
        rows=[x for x in self.sections if x['begin']<=va and va+size<=x['end'] and (not executable or x['flags']&0x20000000)]
        require(len(rows)==1,'file-backed executable section missing/ambiguous'); return rows[0]
    def at(self,va,size,executable=False):
        s=self.section(va,size,executable); return self.read(s['raw']+va-s['begin'],size)



def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        if begin==ENTRY-base or begin<=ENTRY-base<end:
            row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
            if begin==ENTRY-base: exact.append(row)
            if begin<=ENTRY-base<end: containing.append(row)
    overlapping=[]
    if len(exact)==1:
        selected_end=int(exact[0]['endExclusive'],16)-base
        for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
            if begin<selected_end and end>ENTRY-base:
                overlapping.append({'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()})
    return {'exact':exact,'containing':containing,'overlapping':overlapping}


def admit(diag,section):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping'],'pdata missing/ambiguous')
    row=diag['exact'][0]; end=int(row['endExclusive'],16)
    require(int(row['begin'],16)==ENTRY and end>ENTRY and row['size']==end-ENTRY,'invalid extent')
    section(ENTRY,row['size'],True)
    return row


def decode(code):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_OP_REG,X86_REG_RIP
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; cursor=ENTRY; rows=[]
    for ins in cs.disasm(code,ENTRY):
        raw=bytes(ins.bytes); require(ins.address==cursor and code[cursor-ENTRY:cursor-ENTRY+len(raw)]==raw,'decode gap')
        call=ins.group(capstone.CS_GRP_CALL); branch=ins.group(capstone.CS_GRP_JUMP)
        target=hex(ins.operands[0].imm) if (call or branch) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
        operands=[]
        for op in ins.operands:
            access='address-only' if ins.mnemonic=='lea' and op.type==X86_OP_MEM else ('read-write' if op.access==3 else 'read' if op.access==1 else 'write' if op.access==2 else 'unknown')
            if op.type==X86_OP_MEM:
                target_rip=ins.address+ins.size+op.mem.disp if op.mem.base==X86_REG_RIP else None
                operands.append({'type':'memory','base':ins.reg_name(op.mem.base) or None,'displacement':op.mem.disp,'ripTarget':hex(target_rip) if target_rip is not None else None,'access':access,'rawAccess':op.access})
            elif op.type==X86_OP_REG: operands.append({'type':'register','name':ins.reg_name(op.reg),'access':access,'rawAccess':op.access})
            elif op.type==X86_OP_IMM: operands.append({'type':'immediate','value':hex(op.imm),'access':access,'rawAccess':op.access})
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operandsText':ins.op_str,'operands':operands,'call':call,'branch':branch,'directTarget':target})
        cursor+=ins.size
    require(cursor==ENTRY+len(code),'incomplete decode'); require(len(rows)<=64,'instruction budget exceeded')
    return rows


def index(rows):
    stores=[]; refs=[]; edges=[]; refused=[]
    for i,row in enumerate(rows):
        if row['call'] or row['branch']:
            edge={'address':row['address'],'call':row['call'],'branch':row['branch'],'directTarget':row['directTarget']}; edges.append(edge)
            if row['directTarget'] in ('0x149a75140','0x1405336c0'): refused.append(edge)
        for operand in row['operands']:
            if operand['type']=='memory' and operand['ripTarget']:
                item={'address':row['address'],'instructionHex':row['hex'],'mnemonic':row['mnemonic'],'operand':operand}; refs.append(item)
                if operand['ripTarget'] in ('0x149a75140','0x1405336c0'): refused.append(item)
                if operand['ripTarget']==hex(EC8) and operand['access'] in ('write','read-write'):
                    stores.append({'instruction':row,'precedingInstructions':rows[max(0,i-3):i],'bindingRequiresRegisterFlowReview':True})
    return {'ripReferences':refs,'edges':edges,'refusedEdges':refused,'dependentSemanticsFrozen':bool(refused),'ec8Stores':stores,'ec8StoreStatus':'candidate-found' if stores else 'not-found','assignedValueProven':False}


def collect(dest):
    result={'task':'E-01d1/R3-PROUD-SCALAR-CALLBACK-REGISTRATION-WINDOW','stage':'source-gate','bodiesRead':0,'callerBytes':0,'ordinaryAotBytes':0,'calleeBodies':0,'neighborWindows':0,'xdataBytes':0,'globalContentBytes':0,'targetCPU':0,'live':0,'actualForward':None,'actualSeedEpoch':None,'actualEncoded':None,'actualGetterU32':None,'actualRow':False,'resourceReady':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,result['pins']=source_gate()
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stage']='five-stock'; result['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            pe=FilePE(f); result['stage']='pdata-before-body'; require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'directory drift')
            table=pe.at(pe.base+pe.pdata_rva,pe.pdata_size); require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory hash drift')
            diag=find_pdata(table,pe.base,pe.pdata_rva)
            result['exceptionDirectory']={'rva':hex(pe.pdata_rva),'bytesRead':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',result)
            if not (len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping']):
                result.update(stage='boundary-stop',passed=False,reason='pdata missing/ambiguous; no trace-span fallback',budget={'bodyMax':320,'instructionsMax':64,'admitted':False})
                save(dest/'pre-body.private.json',result); save(dest/'result.private.json',result)
                print(json.dumps({'stage':result['stage'],'bodiesRead':0,'diagnostic':diag})); return 2
            row=admit(diag,pe.section); (dest/'pdata.bin').write_bytes(bytes.fromhex(row['recordHex']))
            result['selected']=row; result['budget']={'bodyMax':320,'instructionsMax':64,'pdataBytes':12,'boundedEvidenceMax':332,'actualBodyBytes':row['size'],'admitted':row['size']<=320}
            save(dest/'pre-body.private.json',result)
            if row['size']>320:
                result.update(stage='budget-stop',passed=False,reason='complete body exceeds byte budget'); save(dest/'result.private.json',result)
                print(json.dumps({'stage':result['stage'],'bodiesRead':0,'size':row['size']})); return 2
            result['stage']='body'; code=pe.at(ENTRY,row['size'],True); (dest/'code.bin').write_bytes(code); result['bodiesRead']=1
            rows=decode(code); save(dest/'code.asm.private.json',rows)
            result.update(stage='complete',passed=True,body={'entry':hex(ENTRY),'bytes':len(code),'sha256':sha(code),'instructions':len(rows)},sourceIndex=index(rows)); save(dest/'result.private.json',result)
            print(json.dumps({'stage':'complete','bytes':len(code),'instructions':len(rows),'sha256':sha(code),'ec8StoreStatus':result['sourceIndex']['ec8StoreStatus']})); return 0
    except Exception as e:
        result.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',result); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    req=json.loads(REQUEST.read_bytes()); result=json.loads(raw['result.private.json']); pre=json.loads(raw['pre-body.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'source pin report mismatch')
    require(boundary['bodiesRead']==pre['bodiesRead']==0 and boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'prebody mismatch')
    diag=result['exceptionDirectory']['diagnostic']
    if result['stage']=='boundary-stop':
        require(not (len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping']) and result['bodiesRead']==0 and set(names)=={'boundary.private.json','pre-body.private.json','result.private.json'},'boundary stop mismatch')
    elif result['stage']=='budget-stop':
        row=admit(diag,lambda *args:None); require(row['size']>320 and result['bodiesRead']==0 and 'code.bin' not in raw and raw['pdata.bin'].hex()==row['recordHex'],'budget stop mismatch')
    else:
        row=admit(diag,lambda *args:None); rawcode=raw['code.bin']; rows=decode(rawcode)
        require(len(rawcode)==row['size']<=320 and raw['pdata.bin'].hex()==row['recordHex'] and sha(rawcode)==result['body']['sha256'] and rows==json.loads(raw['code.asm.private.json']) and index(rows)==result['sourceIndex'],'body mismatch')
    require(not any(result[k] for k in ('ordinaryAotBytes','callerBytes','calleeBodies','neighborWindows','xdataBytes','globalContentBytes','targetCPU','live','actualRow','resourceReady','d02Complete')) and all(result[k] is None for k in ('actualForward','actualSeedEpoch','actualEncoded','actualGetterU32')),'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'stage':result['stage'],'currentPEReads':0,'sameDecoderUsedIfBodyPresent':True,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output); return 0
    require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); return collect(dest)


if __name__=='__main__': sys.exit(main())
