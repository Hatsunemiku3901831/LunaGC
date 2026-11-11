"""Acquire two approved current-adoption source windows after one batch gate."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/source-windows'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/source-request.private.json'
TARGETS={663528:0x14d7ac240,138266:0x151846d90,138165:0x151847f70}
CALLERS=((0x1518488be,0x1518488ca,0x14d7ac240),(0x151848a8b,0x151848a97,0x151846d90))


def require(ok,message):
    if not ok: raise ValueError(message)


def sha(data): return hashlib.sha256(data).hexdigest()


def check(path,size,digest):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk); n+=len(chunk)
    require(n==size and h.hexdigest()==digest,'pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}


def save(path,value):
    with path.open('xb') as f: f.write((json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())


class DataOnly(pickle.Unpickler):
    def find_class(self,module,name): raise ValueError('pickle globals forbidden')


def source_gate():
    check(REQUEST,14264,'f80391509e6cc161cd1269d618b0bb2369d51be7fceebf2334cef7d07f7f630d')
    req=json.loads(REQUEST.read_bytes()); pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(req['pins'].items())}
    review=json.loads((ROOT/'local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/plan-review.private.json').read_bytes())
    with (ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').open('rb') as f: records,_=DataOnly(f).load()
    sig=json.loads((ROOT/'local/migration72/evidence/client72-signatures.json').read_bytes())
    require(sig['report']['returnTypesStatus']=='not-recovered','signature return status')
    for index,owner in ((663528,80063),(138266,18163),(138165,18163),(663552,80063)):
        matches=[m for m in records[owner]['methods'] if m['index']==index]; require(len(matches)==1,'method identity')
        expected=review['declarations'][str(index)]; actual=dict(matches[0],owner=owner); require(actual==expected,'declaration drift')
        sm=[m for m in sig['methods'] if m['index']==index]; require(len(sm)==1,'signature identity')
        resolved=[sig['typeReferences'][str(p['typeIndex'])] for p in sm[0]['parameters']]
        require(dict(sm[0],resolvedParameters=resolved)==review['signatures'][str(index)],'signature drift')
    require(len(set(TARGETS.values()))==3 and [(x['method'],int(x['entry'],16)) for x in req['targets']]==[(663528,TARGETS[663528]),(138266,TARGETS[138266])],'target drift')
    return req,pins


class FilePE:
    def __init__(self,f):
        self.f=f; h=self.read(0,4096); nt=struct.unpack_from('<I',h,0x3c)[0]
        require(h[:2]==b'MZ' and nt+64<=len(h) and h[nt:nt+4]==b'PE\0\0' and struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'PE header')
        self.base=struct.unpack_from('<Q',h,nt+48)[0]; require(self.base==0x140000000,'PE base')
        count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]; offset=nt+24+opt; length=count*40; require(length>0 and 4096+length<=8192,'metadata budget')
        table=self.read(offset,length); self.sections=[]
        for i in range(count):
            pos=i*40; rva,n,raw=struct.unpack_from('<III',table,pos+12); flags=struct.unpack_from('<I',table,pos+36)[0]
            self.sections.append({'begin':self.base+rva,'end':self.base+rva+n,'raw':raw,'flags':flags})
        self.pdata_rva,self.pdata_size=struct.unpack_from('<II',h,nt+24+0x70+3*8); overlap=max(0,min(4096,offset+length)-max(0,offset))
        self.meta={'intervals':[{'offset':0,'bytes':4096},{'offset':offset,'bytes':length}],'readCalls':2,'bytesRead':4096+length,'distinctBytes':4096+length-overlap}
    def read(self,offset,size):
        self.f.seek(offset); raw=self.f.read(size); require(len(raw)==size,'short metadata'); return raw
    def section(self,va,size,executable=False):
        rows=[s for s in self.sections if s['begin']<va+size and va<s['end']]
        require(len(rows)==1 and rows[0]['begin']<=va and va+size<=rows[0]['end'] and (not executable or rows[0]['flags']&0x20000000),'file-backed section missing/ambiguous'); return rows[0]
    def at(self,va,size,executable=False):
        s=self.section(va,size,executable); offset=s['raw']+va-s['begin']; self.f.seek(offset); raw=self.f.read(size); return raw,offset


def decoder():
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; return cs


def caller_check(raw,which,cs):
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_OP_REG,X86_REG_RBP
    begin,site,target=CALLERS[which]; require(len(raw)==17,'caller length')
    rows=list(cs.disasm(raw,begin)); require(len(rows)==4 and b''.join(bytes(x.bytes) for x in rows)==raw,'caller decode coverage')
    expected_pcs=[[0x1518488be,0x1518488c0,0x1518488c7,0x1518488ca],[0x151848a8b,0x151848a92,0x151848a94,0x151848a97]][which]
    require([x.address for x in rows]==expected_pcs and [x.size for x in rows]==[2,7,3,5] if which==0 else [x.address for x in rows]==expected_pcs and [x.size for x in rows]==[7,2,3,5],'caller boundary')
    def reg(op,name,size): return op.type==X86_OP_REG and rows[0].reg_name(op.reg)==name and op.size==size
    a,b,c,call=rows
    if which==0:
        require(a.mnemonic=='mov' and len(a.operands)==2 and reg(a.operands[0],'ecx',4) and reg(a.operands[1],'esi',4),'caller U32')
        require(b.mnemonic=='lea' and reg(b.operands[0],'rdx',8) and b.operands[1].type==X86_OP_MEM and b.operands[1].mem.base==X86_REG_RBP and b.operands[1].mem.index==0 and b.operands[1].mem.disp==0xb0,'caller out address')
        require(c.mnemonic=='xor' and all(reg(x,'r8d',4) for x in c.operands),'caller R8D zero')
    else:
        require(a.mnemonic=='mov' and reg(a.operands[0],'rcx',8) and a.operands[1].type==X86_OP_MEM and a.operands[1].mem.base==X86_REG_RBP and a.operands[1].mem.index==0 and a.operands[1].mem.disp==0x100,'caller Avatar')
        require(b.mnemonic=='mov' and reg(b.operands[0],'edx',4) and reg(b.operands[1],'eax',4),'caller group')
        require(c.mnemonic=='mov' and reg(c.operands[0],'r8b',1) and c.operands[1].type==X86_OP_IMM and c.operands[1].imm==1,'caller R8B one')
    call_raw=bytes(call.bytes); require(call.address==site and call.group(capstone.CS_GRP_CALL) and call_raw[0]==0xe8 and site+5+struct.unpack('<i',call_raw[1:])[0]==target,'caller direct E8 target')
    return [{'address':hex(x.address),'size':x.size,'hex':bytes(x.bytes).hex(),'mnemonic':x.mnemonic,'operands':x.op_str} for x in rows]


def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata'); result={i:{'exact':[],'containing':[]} for i in TARGETS}
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        for i,target in TARGETS.items():
            if begin==target-base or begin<=target-base<end:
                row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
                if begin==target-base: result[i]['exact'].append(row)
                if begin<=target-base<end: result[i]['containing'].append(row)
    for i,target in TARGETS.items():
        d=result[i]; d['overlapping']=[]
        if len(d['exact'])==1:
            end0=int(d['exact'][0]['endExclusive'],16)-base
            for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
                if begin<end0 and end>target-base:
                    d['overlapping'].append({'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()})
    return result


def admit(diag,section,file_size):
    chosen={}
    for i,target in TARGETS.items():
        d=diag[i]; require(len(d['exact'])==1 and d['exact']==d['containing']==d['overlapping'],'pdata missing/ambiguous '+str(i))
        row=d['exact'][0]; end=int(row['endExclusive'],16); require(int(row['begin'],16)==target and end>target and row['size']==end-target,'invalid extent')
        s=section(target,row['size'],True); require(s['raw']+target-s['begin']+row['size']<=file_size,'file boundary'); chosen[i]=row
    caller=chosen[138165]; require(caller['recordHex']=='707f8411ce948411640f8704' and caller['recordRVA']=='0x1abf8400','caller pdata drift')
    require(all(int(caller['begin'],16)<=start and start+17<=int(caller['endExclusive'],16) for start,_,_ in CALLERS),'caller containment')
    intervals=sorted((int(x['begin'],16),int(x['endExclusive'],16)) for x in chosen.values()); require(all(a[1]<=b[0] for a,b in zip(intervals,intervals[1:])),'cross-body overlap')
    require(chosen[138266]['size']==556,'selector pdata hint mismatch')
    sizes=[chosen[663528]['size'],chosen[138266]['size']]
    require(sizes[0]<=4096 and sizes[1]<=1024 and sum(sizes)<=4652 and sum(sizes)+34+32+36<=4754,'body/cumulative budget')
    return chosen


def distinct_intervals(intervals):
    ordered=sorted(intervals,key=lambda x:x['fileOffset'])
    require(all(a['fileOffset']+a['bytes']<=b['fileOffset'] for a,b in zip(ordered,ordered[1:])),'source evidence file intervals overlap')


def decode(code,entry,cap,cs):
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    rows=[]; cursor=entry
    for ins in cs.disasm(code,entry):
        raw=bytes(ins.bytes)
        if ins.address!=cursor or raw!=code[cursor-entry:cursor-entry+len(raw)]: return rows,'decode-gap'
        call=ins.group(capstone.CS_GRP_CALL); jump=ins.group(capstone.CS_GRP_JUMP); target=hex(ins.operands[0].imm) if (call or jump) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
        refs=[]
        for op in ins.operands:
            if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
                access='address-only' if ins.mnemonic=='lea' else ('read-write' if op.access==3 else 'read' if op.access==1 else 'write' if op.access==2 else 'unknown')
                refs.append({'address':hex(ins.address+ins.size+op.mem.disp),'access':access,'rawAccess':op.access})
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'jump':jump,'directTarget':target,'ripReferences':refs}); cursor+=ins.size
        if len(rows)>cap: return rows,'instruction-cap'
    return rows,('complete' if cursor==entry+len(code) else 'incomplete-decode')


def source_index(rows):
    edges=[]; rip=[]; refused=[]
    for row in rows:
        if row['call'] or row['jump']:
            edge={'address':row['address'],'call':row['call'],'jump':row['jump'],'directTarget':row['directTarget']}; edges.append(edge)
            if row['directTarget'] in ('0x149a75140','0x1405336c0'): refused.append(edge)
        for ref in row['ripReferences']:
            item={'site':row['address'],'mnemonic':row['mnemonic'],**ref}; rip.append(item)
            if ref['address'] in ('0x149a75140','0x1405336c0'): refused.append(item)
    return {'edges':edges,'ripReferences':rip,'refusedEdges':refused,'dependentSemanticsFrozen':bool(refused)}


def collect(dest):
    report={'task':'E-01d1/R3-PROUD-CURRENT-ADOPTION-TWO-SOURCE-WINDOWS','stage':'source-gate','bodyRead':0,'bodyReadCalls':0,'callerBytes':0,'ordinaryAOTBytes':0,'pdataEvidenceBytes':0,'calleeBytes':0,'globalCellBytes':0,'xdataBytes':0,'targetCPU':0,'live':0,'actualCurrentAdoption':False,'actualEncoded':None,'actualGetter':None,'actualPairMapHit':False,'actualRow':False,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins']=source_gate(); game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}; cs=decoder()
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            pe=FilePE(f); report['metadata']=pe.meta; report['stage']='aot'; cell,co=pe.at(0x1422f9950,8); require(len(cell)==8,'short AOT cell'); pointer=int.from_bytes(cell,'little'); (dest/'aot-cell.bin').write_bytes(cell); report['ordinaryAOTBytes']=8
            slots={}; aot={'cellVA':'0x1422f9950','cellFileOffset':co,'pointer':hex(pointer),'slots':{}}
            for i,target in TARGETS.items():
                slot,offset=pe.at(pointer+i*8,8); report['ordinaryAOTBytes']+=len(slot); require(len(slot)==8 and int.from_bytes(slot,'little')==target,'AOT target mismatch '+str(i))
                slots[i]=slot; (dest/f'slot-{i}.bin').write_bytes(slot); aot['slots'][str(i)]={'va':hex(pointer+i*8),'fileOffset':offset,'hex':slot.hex(),'target':hex(target)}
            report['aot']=aot; report['stage']='callers'; callers=[]
            for i,(start,site,target) in enumerate(CALLERS):
                raw,offset=pe.at(start,17,True); report['callerBytes']+=len(raw); (dest/f'caller-{i}.bin').write_bytes(raw); rows=caller_check(raw,i,cs); save(dest/f'caller-{i}.asm.private.json',rows)
                callers.append({'begin':hex(start),'bytes':len(raw),'fileOffset':offset,'hex':raw.hex(),'callSite':hex(site),'target':hex(target),'onlyR8BLowByteSet':i==1})
            report['callers']=callers; report['stage']='pdata-before-body'; require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'directory drift')
            table,offset=pe.at(pe.base+pe.pdata_rva,pe.pdata_size); require(len(table)==pe.pdata_size and sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory identity')
            diag=find_pdata(table,pe.base,pe.pdata_rva); report['exceptionDirectory']={'rva':hex(pe.pdata_rva),'fileOffset':offset,'bytesRead':len(table),'sha256':sha(table),'diagnostics':diag}; save(dest/'boundary.private.json',report)
            try: chosen=admit(diag,pe.section,req['stockIdentity']['YuanShen.exe']['bytes'])
            except ValueError as e:
                report.update(stage='batch-gate-stop',passed=False,reason=str(e)); save(dest/'pre-body.private.json',report); save(dest/'result.private.json',report); print(json.dumps({'stage':report['stage'],'bodyRead':0,'reason':str(e)})); return 2
            for i,row in chosen.items(): (dest/f'pdata-{i}.bin').write_bytes(bytes.fromhex(row['recordHex']))
            intervals=[{'kind':'aot-cell','fileOffset':co,'bytes':8}]+[{'kind':'aot-slot-'+str(i),'fileOffset':aot['slots'][str(i)]['fileOffset'],'bytes':8} for i in TARGETS]+[{'kind':'caller-'+str(i),'fileOffset':row['fileOffset'],'bytes':17} for i,row in enumerate(callers)]
            for i in (663528,138266):
                sec=pe.section(TARGETS[i],chosen[i]['size'],True)
                intervals.append({'kind':'body-'+str(i),'fileOffset':sec['raw']+TARGETS[i]-sec['begin'],'bytes':chosen[i]['size']})
            report['sourceReadIntervals']=intervals; report['pdataEvidenceBytes']=36
            try: distinct_intervals(intervals)
            except ValueError as e:
                report.update(stage='batch-gate-stop',passed=False,reason=str(e)); save(dest/'pre-body.private.json',report); save(dest/'result.private.json',report); print(json.dumps({'stage':report['stage'],'bodyRead':0,'reason':str(e)})); return 2
            report['pdataEvidenceBytes']=36; report['selected']=chosen; total=chosen[663528]['size']+chosen[138266]['size']; report['budget']={'bodyBytes':total,'bodyBytesMax':4652,'callerBytes':34,'aotBytes':32,'pdataEvidenceBytes':36,'boundedEvidenceBytes':total+102,'boundedEvidenceMax':4754,'admitted':True}; save(dest/'pre-body.private.json',report)
            bodies=[]
            for i,cap in ((663528,1024),(138266,256)):
                entry=TARGETS[i]; row=chosen[i]; report['stage']='body'; code,offset=pe.at(entry,row['size'],True); report['bodyRead']+=len(code); report['bodyReadCalls']+=1; (dest/f'code-{i}.bin').write_bytes(code)
                if len(code)!=row['size']:
                    report.update(stage='short-body',passed=False,reason='short body read',lastBody=i,lastBodyBytes=len(code)); save(dest/'result.private.json',report); return 2
                rows,state=decode(code,entry,cap,cs); save(dest/f'asm-{i}.private.json',{'instructions':rows,'decodeState':state})
                body={'method':i,'entry':hex(entry),'fileOffset':offset,'bytes':len(code),'sha256':sha(code),'instructions':len(rows),'decodeState':state,'sourceIndex':source_index(rows)}; bodies.append(body)
                if state!='complete':
                    report.update(stage='decode-stop',passed=False,bodies=bodies,reason=state); save(dest/'result.private.json',report); print(json.dumps({'stage':report['stage'],'bodyRead':report['bodyRead'],'reason':state})); return 2
            report.update(stage='complete',passed=True,bodies=bodies); save(dest/'result.private.json',report)
            print(json.dumps({'stage':'complete','bodyRead':report['bodyRead'],'bodies':[(x['method'],x['bytes'],x['instructions'],x['sha256']) for x in bodies]})); return 0
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    req=json.loads(REQUEST.read_bytes()); pre=json.loads(raw['pre-body.private.json']); result=json.loads(raw['result.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'source pin report mismatch')
    require(boundary['bodyRead']==pre['bodyRead']==0 and boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'prebody mismatch')
    cell=raw['aot-cell.bin']; pointer=int.from_bytes(cell,'little'); require(len(cell)==8 and result['aot']['pointer']==hex(pointer),'AOT cell')
    for i,target in TARGETS.items(): require(int.from_bytes(raw[f'slot-{i}.bin'],'little')==target and result['aot']['slots'][str(i)]['va']==hex(pointer+i*8),'AOT slot')
    cs=decoder()
    for i in (0,1): require(caller_check(raw[f'caller-{i}.bin'],i,cs)==json.loads(raw[f'caller-{i}.asm.private.json']),'caller artifact')
    diag={int(k):v for k,v in result['exceptionDirectory']['diagnostics'].items()}
    if result['stage']=='batch-gate-stop':
        try: admit(diag,lambda *args:{'raw':0,'begin':args[0]},1<<50)
        except ValueError: pass
        else:
            try: distinct_intervals(result['sourceReadIntervals'])
            except ValueError: pass
            else: raise ValueError('batch gate falsely stopped')
        require(result['bodyRead']==0 and not any(n.startswith('code-') for n in names),'body read at gate stop')
    else:
        selected=admit(diag,lambda *args:{'raw':0,'begin':args[0]},1<<50)
        for i,row in selected.items(): require(raw[f'pdata-{i}.bin'].hex()==row['recordHex'],'pdata artifact')
        total=sum(len(v) for n,v in raw.items() if n.startswith('code-') and n.endswith('.bin')); require(total==result['bodyRead'],'body read count')
        if result['stage']=='complete':
            for body in result['bodies']:
                i=body['method']; code=raw[f'code-{i}.bin']; rows,state=decode(code,TARGETS[i],1024 if i==663528 else 256,cs)
                require(len(code)==selected[i]['size'] and sha(code)==body['sha256'] and {'instructions':rows,'decodeState':state}==json.loads(raw[f'asm-{i}.private.json']) and source_index(rows)==body['sourceIndex'],'body artifact')
    require(not any(result[k] for k in ('calleeBytes','globalCellBytes','xdataBytes','targetCPU','live','actualCurrentAdoption','actualPairMapHit','actualRow','resourceReady','d02r3Complete','d02Complete')) and result['actualEncoded'] is None and result['actualGetter'] is None,'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'stage':result['stage'],'currentPEReads':0,'sameDecoderUsed':True,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','fresh3','fresh4','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output); return 0
    require(args.output in ('fresh1','fresh2','fresh3','fresh4','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); return collect(dest)


if __name__=='__main__': sys.exit(main())
