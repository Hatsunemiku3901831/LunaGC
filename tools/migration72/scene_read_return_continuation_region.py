"""Collect one non-overlapping original pdata region containing a fallthrough PC."""
import argparse
import hashlib
import json
import subprocess
import struct
import sys
from pathlib import Path

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
HELPER=ROOT/'tools/migration72/scene_mplevel_reader_factory_window.py'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!='fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9':raise ValueError('reviewed helper pin mismatch')
from tools.migration72 import scene_mplevel_reader_factory_window as base

REQUEST=ROOT/'local/migration72/d02-resume-20261111c/scene-read-continuation-semantics/return-continuation-region-request.private.json'
REQUEST_SHA='04e9bd904df2559bffda87b7b7d5d5c3281cf6edefbb27ee4633dbbca73705da'
OUT=ROOT/'local/migration72/d02-resume-20261111c/scene-read-return-continuation-region'
OLD_BEGIN,OLD_MID,PC,CONTEXT=0x141138c70,0x141138d3c,0x141138edb,0x141138ed0
FILES=('boundary.private.json','pre-body.private.json','predecessor-context.bin','pdata.bin','region.bin','region.asm.private.json','extraction.private.json')
require,sha,checked,save=base.require,base.sha,base.checked,base.save


def verify_predecessor(body,lines,report,context):
    require(len(body)==415 and sha(body)=='d837f527ce2dec86c4cbdf0464d772c989040d072cc8a232921114e193a865c2','predecessor raw differs')
    decoded=base.decode(body,OLD_MID,2048)
    require(decoded['lines']==lines and len(lines)==95 and b''.join(bytes.fromhex(x['hex']) for x in lines)==body,'predecessor decode differs')
    require(report['passed'] and report['begin']==hex(OLD_MID) and report['endExclusive']==hex(PC) and report['regionSHA256']==sha(body) and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'predecessor provenance differs')
    require(lines[-2]['address']=='0x141138ed0' and lines[-2]['hex']=='488bc3' and lines[-1]['address']=='0x141138ed3' and lines[-1]['hex']=='4c8bb42498000000' and int(lines[-1]['address'],16)+len(bytes.fromhex(lines[-1]['hex']))==PC and report['regionEndFallthrough']['possible'] and report['regionEndFallthrough']['target']==hex(PC),'last instruction/fallthrough differs')
    require(context.hex()=='488bc34c8bb42498000000' and context==body[CONTEXT-OLD_MID:PC-OLD_MID],'predecessor context differs')


def source_gate():
    checked(REQUEST,18700,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for pin in req['reuseTools']:
        p=ROOT/pin['path'];checked(p,p.stat().st_size,pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    item=req['predecessorBinding']['acceptedRegion'];body=(ROOT/item['rawSource']).read_bytes();lines=json.loads((ROOT/item['asmSource']).read_bytes());root=(ROOT/item['rawSource']).parent
    report=json.loads((root/'extraction.private.json').read_bytes());verify_predecessor(body,lines,report,body[CONTEXT-OLD_MID:])
    old=req['predecessorBinding']['knownOldRegions']
    for item_old in old:
        path=ROOT/item_old['pdataSource'];record=path.read_bytes();begin,end,_=struct.unpack('<III',record)
        require(base.BASE+begin==int(item_old['begin'],16) and base.BASE+end==int(item_old['endExclusive'],16) and end-begin==item_old['bytes'],'old pdata identity differs')
    older=(ROOT/'local/migration72/d02-resume-20261111c/scene-read-core-window/fresh1/body.bin').read_bytes()
    older_lines=json.loads((ROOT/'local/migration72/d02-resume-20261111c/scene-read-core-window/fresh1/body.asm.private.json').read_bytes())
    require(sha(older)==old[0]['sha256'] and len(older)==old[0]['bytes'] and base.decode(older,OLD_BEGIN,2048)['lines']==older_lines,'older region differs')
    for name in ('boundary.private.json','pre-body.private.json','predecessor-context.bin','pdata.bin','region.bin','region.asm.private.json','extraction.private.json'):
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
    intersections=[];ranges=[(begin,end)] if size>0 else []
    for old_begin,old_end in ((OLD_BEGIN,OLD_MID),(OLD_MID,PC)):
        lo=max(begin,old_begin);hi=min(end,old_end);count=max(0,hi-lo)
        intersections.append({'oldBegin':hex(old_begin),'oldEndExclusive':hex(old_end),'begin':hex(lo) if count else None,'endExclusive':hex(hi) if count else None,'bytes':count})
        remaining=[]
        for a,b in ranges:
            if old_end<=a or b<=old_begin:remaining.append((a,b))
            else:
                if a<old_begin:remaining.append((a,old_begin))
                if old_end<b:remaining.append((old_end,b))
        ranges=remaining
    overlap=sum(x['bytes'] for x in intersections)
    result['overlap']={'intersections':intersections,'bytes':overlap}
    result['distinctNew']={'ranges':[{'begin':hex(a),'endExclusive':hex(b),'bytes':b-a} for a,b in ranges],'bytes':sum(b-a for a,b in ranges)}
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
    by_address={x['address']:x for x in lines}
    conditional=[x for x in external if by_address[x['address']]['mnemonic']!='jmp']
    unconditional=[x for x in external if by_address[x['address']]['mnemonic']=='jmp']
    coverage={hex(addr):{'covered':begin<=addr<end,'instruction':next((x for x in lines if int(x['address'],16)==addr),None)} for addr in (0x141138f06,0x141138efa,0x141138f85)}
    return {**decoded,'externalBranches':external,'externalConditionalBranches':conditional,'externalUnconditionalBranches':unconditional,'selectedTargetCoverage':coverage,'regionEndFallthrough':{'possible':falls,'sourceLastInstruction':last['address'],'target':hex(end) if falls else None},'sourcePCInstruction':next(x for x in lines if int(x['address'],16)==PC)}


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READ-RETURN-CONTINUATION-REGION','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'logicalReadComplete':False,'aotApplicable':False,'aotBytes':0,'newRegions':0,'regionBytesRead':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,old,lines,old_report=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='predecessor-context'
            context=base.read_at(f,base.offset(sections,CONTEXT,11,True),11);save(dest/'predecessor-context.bin',context);verify_predecessor(old,lines,old_report,context)
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
            report.update(stage='complete',passed=True,begin=hex(begin),endExclusive=hex(begin+size),regionBytes=size,regionSHA256=sha(body),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],externalBranches=decoded['externalBranches'],externalConditionalBranches=decoded['externalConditionalBranches'],externalUnconditionalBranches=decoded['externalUnconditionalBranches'],selectedTargetCoverage=decoded['selectedTargetCoverage'],regionEndFallthrough=decoded['regionEndFallthrough'],sourcePCInstruction=decoded['sourcePCInstruction'],capstoneVersion=decoded['capstoneVersion'])
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
    require(b''.join(bytes.fromhex(x['hex']) for x in saved)==body and len(body)==size and sha(body)==report['regionSHA256'] and saved==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','externalBranches','externalConditionalBranches','externalUnconditionalBranches','selectedTargetCoverage','regionEndFallthrough','sourcePCInstruction','capstoneVersion')),'decode audit differs')
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
