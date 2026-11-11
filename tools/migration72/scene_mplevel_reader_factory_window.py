"""Collect one source-bound Scene reader factory body, offline and read-only."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
REQUEST = ROOT / 'local/migration72/d02-resume-20261111b/scene/reader-factory-window-request.private.json'
REQUEST_SHA = '41586a3a0e27c90c8e47f1952022330258b51c6b0dfd150e1e08d91294992312'
OUT = ROOT / 'local/migration72/d02-resume-20261111b/scene-reader-factory-window'
BASE, ENTRY, SETUP, CALLER = 0x140000000, 0x14d6f9210, 0x14aa99c0d, 0x14aa99a50
CALLER_SOURCE = 'local/migration72/d02-resume-20261110b/scene-mplevel-parser-window/fresh2/caller.bin'
CHAIN_SOURCE = 'local/migration72/d02-resume-20261110b/scene-mplevel-group-source/main-third.private.json'
FILES = ('boundary.private.json','pre-body.private.json','caller-setup.bin','cell.bin','slot.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json')


def require(ok, message):
    if not ok: raise ValueError(message)


def sha(data): return hashlib.sha256(data).hexdigest()


def checked(path, size, digest):
    h=hashlib.sha256(); count=0
    with path.open('rb') as f:
        while chunk := f.read(1024*1024): h.update(chunk); count+=len(chunk)
    require(count==size and h.hexdigest()==digest, 'pin mismatch: '+str(path))
    return {'bytes':count,'sha256':h.hexdigest()}


def save(path, data):
    if not isinstance(data,bytes):data=(json.dumps(data,sort_keys=True,indent=2,ensure_ascii=False)+'\n').encode('utf8')
    with path.open('xb') as f:f.write(data)


class PlainUnpickler(pickle.Unpickler):
    def find_class(self, module, name):
        raise pickle.UnpicklingError('globals not allowed')


def verify_metadata(records, signatures):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(hits==[(60257,500134)],'registered address not unique')
    owner=records[60257]; methods=[m for m in owner['methods'] if m['index']==500134]
    require(owner['index']==60257 and owner['name']=='HALCDKCLBAO' and len(methods)==1,'owner/method differs')
    method=methods[0]
    require((method['name'],method['registeredAddress'],method['parameterCount'],method['flags'],method['virtualSlot'])==('GBPNKANHNCH',hex(ENTRY),3,'0x96',65535),'declaration differs')
    matches=[m for m in signatures['methods'] if m['index']==500134 or m['registeredAddress']==hex(ENTRY)]
    require(len(matches)==1,'signature/address not unique')
    signature=matches[0]
    require((signature['index'],signature['declaringType'],signature['name'],signature['registeredAddress'])==(500134,60257,'GBPNKANHNCH',hex(ENTRY)),'signature differs')
    params=signature['parameters']
    require([(x['index'],x['typeIndex']) for x in params]==[(401957,471),(401958,445811),(401959,445811)],'parameter identity differs')
    require([(signatures['typeReferences'][str(x['typeIndex'])]['kind'],signatures['typeReferences'][str(x['typeIndex'])]['byRef']) for x in params]==[('uint64',False),('int32',False),('int32',False)],'parameter types differ')


def verify_caller(setup, original, chain):
    wanted=bytes.fromhex('4c89f931d24531c0e8f6f5c502')
    require(len(original)==817 and original[SETUP-CALLER:SETUP-CALLER+13]==wanted and setup==wanted,'caller bytes differ')
    require(setup[8]==0xe8 and SETUP+13+struct.unpack('<i',setup[9:13])[0]==ENTRY,'caller E8 target differs')
    evidence=[x for x in chain['frozenCallerEvidence'] if x['entry']==hex(CALLER)]
    require(len(evidence)==1 and evidence[0]['sha256']=='991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569' and evidence[0]['end']=='0x14aa99d81','caller evidence differs')
    lines=evidence[0]['asm']
    for expected in ('14aa99bfb mov r15, qword ptr [rbx]','14aa99c0d mov rcx, r15','14aa99c10 xor edx, edx','14aa99c12 xor r8d, r8d','14aa99c15 call 0x14d6f9210','14aa99c2e mov rdx, rax'):
        require(lines.count(expected)==1,'caller instruction boundary differs')


def verify_profile(profile, stock):
    profile_stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile['files'].values()}
    require(profile_stock==stock,'profile stock differs')


def source_gate():
    checked(REQUEST,11339,REQUEST_SHA)
    req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items(): checked(ROOT/path,pin['bytes'],pin['sha256'])
    for key in ('metadata','signatures'):
        p=req[key];checked(ROOT/p['file'],p['bytes'],p['sha256'])
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    verify_profile(profile,req['stock'])
    records,_=PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load()
    signatures=json.loads((ROOT/req['signatures']['file']).read_bytes())
    verify_metadata(records,signatures)
    caller=(ROOT/CALLER_SOURCE).read_bytes();chain=json.loads((ROOT/CHAIN_SOURCE).read_bytes())
    verify_caller(caller[SETUP-CALLER:SETUP-CALLER+13],caller,chain)
    return req,caller,chain


def read_at(f, offset, length):
    f.seek(offset); data=f.read(length)
    require(len(data)==length,'short read')
    return data


def pe_info(f):
    header=read_at(f,0,64);require(header[:2]==b'MZ','missing MZ')
    pe=struct.unpack_from('<I',header,0x3c)[0];coff=read_at(f,pe,24)
    require(coff[:4]==b'PE\0\0' and struct.unpack_from('<H',coff,4)[0]==0x8664,'not AMD64 PE')
    count,opt_size=struct.unpack_from('<H',coff,6)[0],struct.unpack_from('<H',coff,20)[0]
    opt=read_at(f,pe+24,opt_size)
    require(len(opt)>=0x90 and struct.unpack_from('<H',opt)[0]==0x20b and struct.unpack_from('<Q',opt,24)[0]==BASE,'image base/PE32+ differs')
    directory=struct.unpack_from('<II',opt,0x70+3*8);sections=[]
    for i in range(count):
        row=read_at(f,pe+24+opt_size+40*i,40);rva,size,raw=struct.unpack_from('<III',row,12);flags=struct.unpack_from('<I',row,36)[0]
        sections.append((BASE+rva,size,raw,flags))
    return directory,sections,{'headerBytes':64+24+opt_size,'sectionTableBytes':40*count,'sections':count}


def offset(sections,address,length,executable=False):
    hits=[raw+address-start for start,size,raw,flags in sections if start<=address and address+length<=start+size and (not executable or flags&0x20000000)]
    require(len(hits)==1,'extent not uniquely file-backed executable' if executable else 'extent not uniquely file-backed')
    return hits[0]


def pdata_diagnostic(table,rva,entry=ENTRY):
    require(len(table)%12==0,'unaligned exception directory')
    exact=[];containing=[]
    for pos in range(0,len(table),12):
        begin,end,unwind=struct.unpack_from('<III',table,pos)
        if begin==entry-BASE or begin<=entry-BASE<end:
            row={'recordRVA':hex(rva+pos),'recordHex':table[pos:pos+12].hex(),'begin':hex(BASE+begin),'endExclusive':hex(BASE+end),'unwindRVA':hex(unwind),'size':end-begin}
            if begin==entry-BASE:exact.append(row)
            if begin<=entry-BASE<end:containing.append(row)
    return {'exact':exact,'containing':containing}


def choose(diag):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing'],'pdata exact/containing ambiguous')
    return diag['exact'][0]


def check_budget(size):
    require(0<size<=8192,'body budget exceeded')


def check_aot(cell,slot):
    require(len(cell)==8 and cell.hex()=='d0548a4201000000','actual cell differs')
    table=int.from_bytes(cell,'little');require(table+500134*8==0x142c76200,'slot arithmetic differs')
    require(len(slot)==8 and slot.hex()=='10926f4d01000000' and int.from_bytes(slot,'little')==ENTRY,'actual slot differs')


def decode(code, entry=ENTRY, max_instructions=2048):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);cs.detail=True
    lines=[];refs=[];returns=[];tails=[];cursor=entry
    for ins in cs.disasm(code,entry):
        raw=bytes(ins.bytes);require(ins.address==cursor and raw==code[cursor-entry:cursor-entry+len(raw)],'decode gap/overlap')
        line={'address':hex(ins.address),'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str};lines.append(line)
        require(len(lines)<=max_instructions,'instruction budget exceeded')
        if ins.group(capstone.CS_GRP_RET):returns.append(line)
        if ins.group(capstone.CS_GRP_JUMP) or ins.group(capstone.CS_GRP_CALL):
            direct=next((hex(op.imm) for op in ins.operands if op.type==X86_OP_IMM),None)
            kind='call' if ins.group(capstone.CS_GRP_CALL) else 'branch'
            refs.append({'address':hex(ins.address),'kind':kind,'target':direct})
            if ins.mnemonic=='jmp' and (direct is None or not entry<=int(direct,16)<entry+len(code)):tails.append(dict(line,target=direct))
        for op in ins.operands:
            if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:refs.append({'address':hex(ins.address),'kind':'rip','target':hex(ins.address+ins.size+op.mem.disp)})
        cursor+=len(raw)
    require(cursor==entry+len(code),'incomplete decode')
    return {'lines':lines,'references':refs,'returns':returns,'externalTails':tails,'capstoneVersion':capstone.__version__}


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-FACTORY-ONE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'newBodies':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,caller,chain=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='caller-setup'
            setup=read_at(f,offset(sections,SETUP,13,True),13);save(dest/'caller-setup.bin',setup);verify_caller(setup,caller,chain)
            report['stage']='actual-aot'
            cell=read_at(f,offset(sections,0x1422f9950,8),8);save(dest/'cell.bin',cell)
            require(cell.hex()=='d0548a4201000000','actual cell differs')
            slot_va=int.from_bytes(cell,'little')+500134*8;require(slot_va==0x142c76200,'slot arithmetic differs')
            slot=read_at(f,offset(sections,slot_va,8),8);save(dest/'slot.bin',slot);check_aot(cell,slot)
            report['stage']='pdata-before-body'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=read_at(f,offset(sections,BASE+directory[0],directory[1]),directory[1]);require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diag=pdata_diagnostic(table,directory[0]);report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',report)
            row=choose(diag);save(dest/'pdata.bin',bytes.fromhex(row['recordHex']))
            report.update(entry=hex(ENTRY),endExclusive=row['endExclusive'],bodyBytes=row['size'],slotVA=hex(slot_va),callerSetupHex=setup.hex())
            save(dest/'pre-body.private.json',report)
            check_budget(row['size'])
            body_offset=offset(sections,ENTRY,row['size'],True)
            report['stage']='single-body';body=read_at(f,body_offset,row['size']);report['newBodies']=1
            save(dest/'body.bin',body);decoded=decode(body);save(dest/'body.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,bodySHA256=sha(body),bodyFileOffset=hex(body_offset),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bytes':len(body),'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references']),'returns':len(decoded['returns']),'externalTails':len(decoded['externalTails'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,caller,chain=source_gate()
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(f['caller-setup.bin'],caller,chain);check_aot(f['cell.bin'],f['slot.bin'])
    diag=report['exceptionDirectory']['diagnostic'];row=choose(diag)
    require(boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'] and bytes.fromhex(row['recordHex'])==f['pdata.bin'],'boundary audit differs')
    body=f['body.bin'];decoded=decode(body)
    require(len(body)==row['size'] and len(body)<=8192 and sha(body)==report['bodySHA256'] and decoded['lines']==json.loads(f['body.asm.private.json']) and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'decode audit differs')
    require(report['inputs']==req['stock'] and report['passed'] and report['newBodies']==1 and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not any(report[x] for x in ('targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshNineFilesByteIdentical':True,'currentPEReads':0,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(decoded['lines']),'references':len(decoded['references'])}
    save(output,result);print(json.dumps(result))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);parser.add_argument('--audit',nargs=2);args=parser.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'new task-local basename required')
    OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.endswith('.json') and all(Path(x).name==x for x in args.audit),'audit inputs must be basenames')
        audit(*(OUT/x for x in args.audit),OUT/args.output)
    else:
        dest=OUT/args.output;dest.mkdir();collect(dest)

if __name__=='__main__':main()
