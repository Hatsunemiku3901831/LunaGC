"""Collect one non-overlapping original pdata region containing a fallthrough PC."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
HELPER=ROOT/'tools/migration72/scene_mplevel_reader_factory_window.py'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!='fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9':raise ValueError('reviewed helper pin mismatch')
from tools.migration72 import scene_mplevel_reader_factory_window as base

REQUEST=ROOT/'local/migration72/d02-resume-20261111c/scene-read-core-semantics/normal-continuation-region-request.private.json'
REQUEST_SHA='7c91fa80af6ac8beecdff1f6e531470b861a51f42182ae71e30c802ffed08a89'
OUT=ROOT/'local/migration72/d02-resume-20261111c/scene-read-core-continuation-region'
OLD_BEGIN,PC,CONTEXT=0x141138c70,0x141138d3c,0x141138d22
FILES=('boundary.private.json','pre-body.private.json','predecessor-context.bin','pdata.bin','region.bin','region.asm.private.json','extraction.private.json')
require,sha,checked,save=base.require,base.sha,base.checked,base.save


def verify_predecessor(body,lines,report,context):
    require(len(body)==204 and sha(body)=='ba5cd6de5a0f86c0d054b57c769670a3c97b8b11c6fffeefcd80601ecb5eafa0','predecessor raw differs')
    decoded=base.decode(body,OLD_BEGIN,2048)
    require(decoded['lines']==lines and len(lines)==52 and b''.join(bytes.fromhex(x['hex']) for x in lines)==body,'predecessor decode differs')
    require(report['passed'] and report['entry']==hex(OLD_BEGIN) and report['endExclusive']==hex(PC) and report['bodySHA256']==sha(body) and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'predecessor provenance differs')
    require(lines[-1]['address']=='0x141138d3a' and lines[-1]['hex']=='8b0f' and lines[-1]['mnemonic']=='mov' and lines[-1]['operands']=='ecx, dword ptr [rdi]' and int(lines[-1]['address'],16)+len(bytes.fromhex(lines[-1]['hex']))==PC,'last instruction/fallthrough differs')
    require(any(x['address']==hex(CONTEXT) for x in lines) and context.hex()=='33d2498bc548f777284c8bfd488bf04885c00f884b0200008b0f' and context==body[CONTEXT-OLD_BEGIN:PC-OLD_BEGIN],'predecessor context differs')


def source_gate():
    checked(REQUEST,17425,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for pin in req['reuseTools']:
        p=ROOT/pin['path'];checked(p,p.stat().st_size,pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    item=req['predecessorBinding']['acceptedRegion'];body=(ROOT/item['rawSource']).read_bytes();lines=json.loads((ROOT/item['asmSource']).read_bytes());root=(ROOT/item['rawSource']).parent
    report=json.loads((root/'extraction.private.json').read_bytes());verify_predecessor(body,lines,report,body[CONTEXT-OLD_BEGIN:])
    require((ROOT/item['pdataSource']).read_bytes().hex()==item['pdataHex'],'predecessor pdata differs')
    for name in ('boundary.private.json','pre-body.private.json','caller-setup.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json'):
        require((root/name).read_bytes()==(root.parent/'fresh2'/name).read_bytes(),'predecessor fresh artifacts differ')
    audit=json.loads((root.parent/'audit.private.json').read_bytes())
    require(audit['passed'] and audit['twoFreshSevenFilesByteIdentical'] and audit['currentPEReads']==0,'predecessor audit differs')
    return req,body,lines,report


def diagnose(diag,sections):
    exact,containing=diag['exact'],diag['containing']
    result={'sourcePC':hex(PC),'predecessorEndExclusive':hex(PC),'matchLists':diag,'selected':None,'overlap':None,'distinctNew':None,'section':None,'budget':None,'eligible':False,'reason':None}
    if len(containing)!=1:
        result['reason']='containing record not unique';return result
    row=containing[0];begin=int(row['begin'],16);end=int(row['endExclusive'],16);size=end-begin
    result['selected']={'begin':hex(begin),'endExclusive':hex(end),'size':size,'sourcePCOffset':PC-begin,'coversSourcePC':begin<=PC<end,'recordRVA':row['recordRVA'],'recordHex':row['recordHex']}
    lo=max(begin,OLD_BEGIN);hi=min(end,PC);overlap=max(0,hi-lo)
    ranges=[]
    if overlap:
        if begin<lo:ranges.append({'begin':hex(begin),'endExclusive':hex(lo),'bytes':lo-begin})
        if hi<end:ranges.append({'begin':hex(hi),'endExclusive':hex(end),'bytes':end-hi})
    elif size>0:ranges.append({'begin':hex(begin),'endExclusive':hex(end),'bytes':size})
    result['overlap']={'begin':hex(lo) if overlap else None,'endExclusive':hex(hi) if overlap else None,'bytes':overlap}
    result['distinctNew']={'ranges':ranges,'bytes':sum(x['bytes'] for x in ranges)}
    result['budget']={'fullRegionBytes':size,'maximumBytes':8192,'positiveAndWithinBudget':0<size<=8192}
    try:
        off=base.offset(sections,begin,size,True);result['section']={'executableFileBacked':True,'fileOffset':hex(off)}
    except ValueError as e:
        result['section']={'executableFileBacked':False,'error':str(e)}
    if exact and exact!=[row]:result['reason']='exact and containing records disagree'
    elif overlap:result['reason']='selected region overlaps predecessor'
    elif not result['budget']['positiveAndWithinBudget']:result['reason']='region extent/budget differs'
    elif not result['section']['executableFileBacked']:result['reason']='region not executable/file-backed'
    else:result['eligible']=True
    return result


def decode_region(body,begin):
    decoded=base.decode(body,begin,2048);lines=decoded['lines'];end=begin+len(body)
    require(any(int(x['address'],16)==PC for x in lines),'sourcePC not instruction boundary')
    external=[x for x in decoded['references'] if x['kind']=='branch' and (x['target'] is None or not begin<=int(x['target'],16)<end)]
    last=lines[-1];mnemonic=last['mnemonic'].lower()
    falls=not (mnemonic in ('jmp','ret','retf','int3','ud2','hlt') or mnemonic.startswith('iret'))
    return {**decoded,'externalBranches':external,'regionEndFallthrough':{'possible':falls,'sourceLastInstruction':last['address'],'target':hex(end) if falls else None},'sourcePCInstruction':next(x for x in lines if int(x['address'],16)==PC)}


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READ-NORMAL-CONTINUATION-REGION','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'logicalReadComplete':False,'aotApplicable':False,'aotBytes':0,'newRegions':0,'regionBytesRead':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,old,lines,old_report=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='predecessor-context'
            context=base.read_at(f,base.offset(sections,CONTEXT,26,True),26);save(dest/'predecessor-context.bin',context);verify_predecessor(old,lines,old_report,context)
            report['stage']='pdata-before-region'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1])
            require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diag=base.pdata_diagnostic(table,directory[0],PC);decision=diagnose(diag,sections)
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'decision':decision}
            save(dest/'boundary.private.json',report)
            if decision['selected'] is not None:save(dest/'pdata.bin',bytes.fromhex(decision['selected']['recordHex']))
            save(dest/'pre-body.private.json',report)
            require(decision['eligible'],decision['reason'])
            begin=int(decision['selected']['begin'],16);size=decision['selected']['size'];off=int(decision['section']['fileOffset'],16)
            report['stage']='single-region';body=base.read_at(f,off,size);report['newRegions']=1;report['regionBytesRead']=len(body)
            save(dest/'region.bin',body);decoded=decode_region(body,begin);save(dest/'region.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,begin=hex(begin),endExclusive=hex(begin+size),regionBytes=size,regionSHA256=sha(body),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],externalBranches=decoded['externalBranches'],regionEndFallthrough=decoded['regionEndFallthrough'],sourcePCInstruction=decoded['sourcePCInstruction'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bytes':size,'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,old,lines,old_report=source_gate();copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_predecessor(old,lines,old_report,f['predecessor-context.bin'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'],'boundary audit differs')
    directory=boundary['exceptionDirectory'];decision=directory['decision'];selected=decision['selected']
    require(decision['eligible'] and directory['sha256']==req['pdata']['sha256'] and directory['bytes']==req['pdata']['bytes'] and bytes.fromhex(selected['recordHex'])==f['pdata.bin'],'pdata differs')
    begin=int(selected['begin'],16);size=selected['size'];off=int(decision['section']['fileOffset'],16)
    synthetic_sections=[(begin,size,off,0x20000000)]
    require(diagnose(decision['matchLists'],synthetic_sections)==decision,'coverage/section audit differs')
    body=f['region.bin'];saved=json.loads(f['region.asm.private.json']);decoded=decode_region(body,begin)
    require(b''.join(bytes.fromhex(x['hex']) for x in saved)==body and len(body)==size and sha(body)==report['regionSHA256'] and saved==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','externalBranches','regionEndFallthrough','sourcePCInstruction','capstoneVersion')),'decode audit differs')
    require(report['inputs']==req['stock'] and report['passed'] and report['newRegions']==1 and report['regionBytesRead']==len(body) and report['begin']==hex(begin) and report['endExclusive']==hex(begin+size) and report['instructionCount']==len(saved) and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not report['logicalReadComplete'] and not report['aotApplicable'] and not any(report[x] for x in ('aotBytes','targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshSevenFilesByteIdentical':True,'currentPEReads':0,'logicalReadComplete':False,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(saved),'references':len(decoded['references'])}
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
