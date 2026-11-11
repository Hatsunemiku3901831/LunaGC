"""Collect the approved existing-handle NativeWrapper constructor source offline."""
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
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-querywrapper-borrow-semantics/nativewrapper-ctor-window'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-querywrapper-borrow-semantics/nativewrapper-ctor-source-request.private.json'
SEMANTICS=ROOT/'docs/CLIENT_72_D02_PROUD_QUERYWRAPPER_BORROW_SEMANTICS_20261111C.md'
OLD_TOOL=ROOT/'tools/migration72/proud_querywrapper_borrow_window.py'
CALLER=ROOT/'local/migration72/d02-resume-20261111c/proud-current-adoption-contract/querywrapper-borrow-window/v2-fresh1'
ENTRY=0x146e03c70
CALLER_ENTRY=0x146e031f0
SETUP=0x146e032c1
SITE=0x146e032d9


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


def validate_caller(raw,assembly):
    require(len(raw)==544 and assembly['decodeState']=='complete','caller length/decode')
    cursor=CALLER_ENTRY
    for row in assembly['instructions']:
        data=bytes.fromhex(row['hex']); require(int(row['address'],16)==cursor and raw[cursor-CALLER_ENTRY:cursor-CALLER_ENTRY+len(data)]==data,'saved caller reassembly'); cursor+=len(data)
    require(cursor==CALLER_ENTRY+544,'caller coverage')
    setup=raw[SETUP-CALLER_ENTRY:SETUP-CALLER_ENTRY+29]
    require(setup==bytes.fromhex('0f57c00f294424400f29442430488d4c24304189f84189c1e892090000'),'saved caller setup')
    require(SITE+5+struct.unpack('<i',setup[-4:])[0]==ENTRY,'saved E8 target')
    selected=[x for x in assembly['instructions'] if SETUP<=int(x['address'],16)<SITE+5]
    require([(x['address'],x['mnemonic'],x['operands']) for x in selected]==[('0x146e032c1','xorps','xmm0, xmm0'),('0x146e032c4','movaps','xmmword ptr [rsp + 0x40], xmm0'),('0x146e032c9','movaps','xmmword ptr [rsp + 0x30], xmm0'),('0x146e032ce','lea','rcx, [rsp + 0x30]'),('0x146e032d3','mov','r8d, edi'),('0x146e032d6','mov','r9d, eax'),('0x146e032d9','call','0x146e03c70')],'physical ctor arguments')
    earlier=next(x for x in assembly['instructions'] if x['address']=='0x146e032b3')
    require(earlier['hex']=='488b542428' and earlier['mnemonic']=='mov' and earlier['operands']=='rdx, qword ptr [rsp + 0x28]','saved page reload')


def declaration(records,sig):
    owner=records[11712]; require(owner['index']==11712 and owner['name']=='NativeWrapper' and owner['namespace']=='MoleMole.NativeConfig','owner identity')
    hits=[(r['index'],m) for r in records for m in r['methods'] if m['index']==83916]; require(len(hits)==1 and hits[0][0]==11712,'method unique owner')
    m=hits[0][1]; require(m['name']=='.ctor' and m['registeredAddress']==hex(ENTRY) and m['flags']=='0x1886' and m['parameterCount']==3 and m['virtualSlot']==65535,'ordinary declaration')
    methods=[m for m in sig['methods'] if m['index']==83916]; require(len(methods)==1,'signature unique')
    m=methods[0]; require(m['declaringType']==11712 and m['name']=='.ctor' and m['registeredAddress']==hex(ENTRY) and len(m['parameters'])==3 and sig['report']['returnTypesStatus']=='not-recovered','signature identity')
    types=[sig['typeReferences'][str(x['typeIndex'])] for x in m['parameters']]
    require(types[0]['kind']=='class' and types[0]['name']=='INativePage' and types[0]['namespace']=='MoleMole.NativeConfig' and types[0]['typeDefinition']==11466 and types[0]['byRef'] is False and all(x['kind']=='int32' and x['byRef'] is False for x in types[1:]),'logical ctor parameter types')


def source_gate():
    check(REQUEST,11557,'8cb0d9981a3ba8d845e4140d99ca031be3112a2ab5eb320aafebffa813700eb0')
    check(SEMANTICS,10266,'1d0d86315aa7cb0c528156a1be5b85055437be2428c42fce567578894bd55fc4')
    req=json.loads(REQUEST.read_bytes()); pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(req['pins'].items())}
    pins['tools/migration72/proud_querywrapper_borrow_window.py']=check(OLD_TOOL,18168,'2d9a5bb612a62299b858e20efedc8dd082bf7bca0eb3db43fe5cc4f43b0d9f6a')
    validate_caller((CALLER/'code.bin').read_bytes(),json.loads((CALLER/'code.asm.private.json').read_bytes()))
    with (ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').open('rb') as f: records,_=DataOnly(f).load()
    declaration(records,json.loads((ROOT/'local/migration72/evidence/client72-signatures.json').read_bytes()))
    require(req['entry']==hex(ENTRY) and req['owner']==11712 and req['method']==83916 and req['caller']['freshCallerBytes']==0,'request identity')
    spec=importlib.util.spec_from_file_location('validated_querywrapper_filepe',OLD_TOOL); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return req,pins,module.FilePE


def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        if begin==ENTRY-base or begin<=ENTRY-base<end:
            row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
            if begin==ENTRY-base: exact.append(row)
            if begin<=ENTRY-base<end: containing.append(row)
    overlapping=[]
    if len(exact)==1:
        end0=int(exact[0]['endExclusive'],16)-base
        for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
            if begin<end0 and end>ENTRY-base:
                overlapping.append({'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()})
    return {'exact':exact,'containing':containing,'overlapping':overlapping}


def admit(diag,section,file_size):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping'],'pdata missing/ambiguous')
    row=diag['exact'][0]; end=int(row['endExclusive'],16); require(int(row['begin'],16)==ENTRY and end>ENTRY and row['size']==end-ENTRY,'invalid extent')
    sec=section(ENTRY,row['size'],True); require(sec['raw']+ENTRY-sec['begin']+row['size']<=file_size,'extent exceeds file')
    return row


def decode(code):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; rows=[]; cursor=ENTRY
    for ins in cs.disasm(code,ENTRY):
        raw=bytes(ins.bytes)
        if ins.address!=cursor or raw!=code[cursor-ENTRY:cursor-ENTRY+len(raw)]: return rows,'decode-gap'
        call=ins.group(capstone.CS_GRP_CALL); jump=ins.group(capstone.CS_GRP_JUMP); target=hex(ins.operands[0].imm) if (call or jump) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
        refs=[]
        for op in ins.operands:
            if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
                access='address-only' if ins.mnemonic=='lea' else ('read-write' if op.access==3 else 'read' if op.access==1 else 'write' if op.access==2 else 'unknown')
                refs.append({'address':hex(ins.address+ins.size+op.mem.disp),'access':access,'rawAccess':op.access})
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'jump':jump,'directTarget':target,'ripReferences':refs}); cursor+=ins.size
        if len(rows)>256: return rows,'instruction-cap'
    return rows,('complete' if cursor==ENTRY+len(code) else 'incomplete-decode')


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
    report={'task':'R3-PROUD-EXISTING-HANDLE-NATIVEWRAPPER-CTOR-SOURCE','stage':'source-gate','bodyRead':0,'bodyReadCalls':0,'freshCallerBytes':0,'ordinaryAOTBytes':0,'pdataEvidenceBytes':0,'globalContentsBytes':0,'otherSuccessorBodyBytes':0,'actualPageOrHandleBytes':0,'targetCPU':0,'live':0,'actualB':None,'actualO':None,'actualLease':None,'actualCurrentAdoption':None,'actualEncoded':None,'actualGetterU32':None,'actualPairMapHit':None,'actualRow':None,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins'],FilePE=source_gate(); game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            pe=FilePE(f); report['metadata']=pe.meta; report['stage']='aot'; cell,co=pe.at(0x1422f9950,8); report['ordinaryAOTBytes']+=len(cell); require(len(cell)==8,'short AOT cell'); pointer=int.from_bytes(cell,'little'); (dest/'aot-cell.bin').write_bytes(cell)
            slot,so=pe.at(pointer+83916*8,8); report['ordinaryAOTBytes']+=len(slot); require(len(slot)==8 and int.from_bytes(slot,'little')==ENTRY,'AOT slot mismatch'); (dest/'aot-slot.bin').write_bytes(slot)
            report['aot']={'cellVA':'0x1422f9950','cellFileOffset':co,'pointer':hex(pointer),'slotVA':hex(pointer+83916*8),'slotFileOffset':so,'slotHex':slot.hex()}
            report['stage']='pdata-before-body'; require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'directory drift'); table,offset=pe.at(pe.base+pe.pdata_rva,pe.pdata_size)
            require(len(table)==pe.pdata_size and sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory identity')
            diag=find_pdata(table,pe.base,pe.pdata_rva); report['exceptionDirectory']={'rva':hex(pe.pdata_rva),'fileOffset':offset,'bytesRead':len(table),'sha256':sha(table),'diagnostic':diag}; save(dest/'boundary.private.json',report)
            try: row=admit(diag,pe.section,req['stockIdentity']['YuanShen.exe']['bytes'])
            except ValueError as e:
                report.update(stage='boundary-stop',passed=False,reason=str(e)); save(dest/'pre-body.private.json',report); save(dest/'result.private.json',report); print(json.dumps({'stage':report['stage'],'bodyRead':0,'reason':str(e)})); return 2
            (dest/'pdata.bin').write_bytes(bytes.fromhex(row['recordHex'])); report['pdataEvidenceBytes']=12; report['selected']=row
            report['budget']={'bodyMax':512,'instructionMax':256,'AOTBytes':16,'pdataBytes':12,'boundedEvidenceMax':540,'actualBodyBytes':row['size'],'admitted':row['size']<=512}; save(dest/'pre-body.private.json',report)
            if row['size']>512:
                report.update(stage='budget-stop',passed=False,reason='complete body exceeds byte budget'); save(dest/'result.private.json',report); print(json.dumps({'stage':report['stage'],'bodyRead':0,'size':row['size']})); return 2
            report['stage']='body'; code,offset=pe.at(ENTRY,row['size'],True); report['bodyRead']=len(code); report['bodyReadCalls']=1; (dest/'code.bin').write_bytes(code)
            if len(code)!=row['size']:
                report.update(stage='short-body',passed=False,reason='short body read',bodySHA256=sha(code)); save(dest/'result.private.json',report); return 2
            rows,state=decode(code); save(dest/'code.asm.private.json',{'instructions':rows,'decodeState':state})
            report.update(stage='complete' if state=='complete' else 'decode-stop',passed=state=='complete',body={'entry':hex(ENTRY),'fileOffset':offset,'bytes':len(code),'sha256':sha(code),'instructions':len(rows),'decodeState':state},sourceIndex=source_index(rows)); save(dest/'result.private.json',report)
            print(json.dumps({'stage':report['stage'],'bytes':len(code),'instructions':len(rows),'sha256':sha(code),'decodeState':state})); return 0 if state=='complete' else 2
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    req=json.loads(REQUEST.read_bytes()); pre=json.loads(raw['pre-body.private.json']); result=json.loads(raw['result.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'source pin report mismatch')
    validate_caller((CALLER/'code.bin').read_bytes(),json.loads((CALLER/'code.asm.private.json').read_bytes()))
    require(boundary['bodyRead']==pre['bodyRead']==0 and boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'prebody mismatch')
    cell=raw['aot-cell.bin']; slot=raw['aot-slot.bin']; pointer=int.from_bytes(cell,'little'); require(len(cell)==len(slot)==8 and int.from_bytes(slot,'little')==ENTRY and result['aot']['slotVA']==hex(pointer+83916*8),'AOT mismatch')
    diag=result['exceptionDirectory']['diagnostic']
    if result['stage']=='boundary-stop':
        try: admit(diag,lambda *args:{'raw':0,'begin':ENTRY},1<<50)
        except ValueError: pass
        else: require(result['reason'] in ('file-backed executable section missing/ambiguous','extent exceeds file'),'boundary reason mismatch')
        require(result['bodyRead']==0 and 'code.bin' not in raw,'body read at boundary stop')
    else:
        row=admit(diag,lambda *args:{'raw':0,'begin':ENTRY},1<<50); require(raw['pdata.bin'].hex()==row['recordHex'] and result['pdataEvidenceBytes']==12,'pdata mismatch')
        if result['stage']=='budget-stop': require(row['size']>512 and result['bodyRead']==0 and 'code.bin' not in raw,'budget stop mismatch')
        else:
            code=raw['code.bin']; require(len(code)==result['bodyRead'],'body read count mismatch')
            if result['stage']=='short-body': require(len(code)<row['size'] and sha(code)==result['bodySHA256'],'short mismatch')
            else:
                rows,state=decode(code); require(len(code)==row['size'] and sha(code)==result['body']['sha256'] and {'instructions':rows,'decodeState':state}==json.loads(raw['code.asm.private.json']) and source_index(rows)==result['sourceIndex'],'body artifact mismatch')
    require(not any(result[k] for k in ('freshCallerBytes','globalContentsBytes','otherSuccessorBodyBytes','actualPageOrHandleBytes','targetCPU','live','resourceReady','d02r3Complete','d02Complete')) and all(result[k] is None for k in ('actualB','actualO','actualLease','actualCurrentAdoption','actualEncoded','actualGetterU32','actualPairMapHit','actualRow')),'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'stage':result['stage'],'currentPEReads':0,'sameDecoderUsedIfBodyPresent':True,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output); return 0
    require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); return collect(dest)


if __name__=='__main__': sys.exit(main())
