"""Collect one native reader API with a unique original pdata extent."""
import argparse
import hashlib
import io
import json
import struct
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT=Path(__file__).resolve().parents[2]
HELPER=ROOT/'tools/migration72/scene_mplevel_reader_factory_window.py'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!='fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9':
    raise ValueError('reviewed helper pin mismatch')
from tools.migration72 import scene_mplevel_reader_factory_window as base

REQUEST=ROOT/'local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/native-read-one-window-request.private.json'
REQUEST_SHA='213de70fa850bc4762869a177a62ae6e0ac79f5ccf91793b9923d8eed8c21f3f'
OUT=ROOT/'local/migration72/d02-resume-20261111c/scene-native-read-window'
CALLER,SETUP,ENTRY=0x14f614760,0x14f614921,0x141123190
FILES=('boundary.private.json','pre-body.private.json','caller-setup.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json')
require,sha,checked,save=base.require,base.sha,base.checked,base.save


def check_no_managed_target(records):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(not hits,'native target has ordinary metadata hits; AOT not assigned')


def verify_caller(caller,lines,report,setup):
    require(len(caller)==486 and sha(caller)=='dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999','caller raw differs')
    decoded=base.decode(caller,CALLER,2048)
    require(lines==decoded['lines'] and len(lines)==122 and b''.join(bytes.fromhex(x['hex']) for x in lines)==caller,'caller decode differs')
    require(report['passed'] and report['entry']==hex(CALLER) and report['endExclusive']=='0x14f614946' and report['bodySHA256']==sha(caller) and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'caller provenance differs')
    wanted={0x14f614900:'8b461c',0x14f614903:'034618',0x14f614906:'4898',0x14f614908:'448b4a18',0x14f61491b:'488b4e20',0x14f61491f:'4898',0x14f614921:'4889442420',0x14f614926:'4531c0',0x14f614929:'e862e8b0f1'}
    found={int(x['address'],16):x['hex'] for x in lines if int(x['address'],16) in wanted}
    require(found==wanted,'caller setup/context/boundary differs')
    require(setup.hex()=='48894424204531c0e862e8b0f1' and setup==caller[SETUP-CALLER:SETUP-CALLER+13] and rel32_target(SETUP+8,setup[8:])==ENTRY,'fresh setup/E8 differs')


def rel32_target(address,raw):
    require(len(raw)==5 and raw[0]==0xe8,'call is not E8 rel32')
    return address+5+struct.unpack('<i',raw[1:])[0]


def source_gate():
    checked(REQUEST,15024,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for pin in req['reuseTools']:
        path=ROOT/pin['path'];checked(path,path.stat().st_size,pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    records,_=base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load();check_no_managed_target(records)
    sourceinfo=req['callerBinding']['acceptedCaller'];caller=(ROOT/sourceinfo['rawSource']).read_bytes()
    lines=json.loads((ROOT/sourceinfo['asmSource']).read_bytes());root=(ROOT/sourceinfo['rawSource']).parent
    report=json.loads((root/'extraction.private.json').read_bytes())
    verify_caller(caller,lines,report,caller[SETUP-CALLER:SETUP-CALLER+13])
    for name in ('boundary.private.json','pre-body.private.json','caller-setup.bin','cell.bin','slot.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json'):
        require((root/name).read_bytes()==(root.parent/'fresh2'/name).read_bytes(),'prior fresh artifacts differ')
    prior=json.loads((root.parent/'audit.private.json').read_bytes())
    require(prior['passed'] and prior['twoFreshNineFilesByteIdentical'] and prior['currentPEReads']==0,'prior audit differs')
    return req,caller,lines,report


def check_row(diag):
    row=base.choose(diag)
    require(row['begin']==hex(ENTRY) and 0<row['size']<=8192 and int(row['endExclusive'],16)==ENTRY+row['size'],'body extent/budget differs')
    return row


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-NATIVE-READ-ONE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'ordinaryMethodHits':[],'aotApplicable':False,'aotBytes':0,'newBodies':0,'bodyBytesRead':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,caller,lines,prior_report=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='caller-setup'
            setup=base.read_at(f,base.offset(sections,SETUP,13,True),13);save(dest/'caller-setup.bin',setup);verify_caller(caller,lines,prior_report,setup)
            report['stage']='pdata-before-body'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1])
            require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diag=base.pdata_diagnostic(table,directory[0],ENTRY)
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',report);save(dest/'pre-body.private.json',report)
            row=check_row(diag);save(dest/'pdata.bin',bytes.fromhex(row['recordHex']))
            size=row['size'];body_offset=base.offset(sections,ENTRY,size,True)
            report['stage']='single-body';body=base.read_at(f,body_offset,size);report['newBodies']=1;report['bodyBytesRead']=len(body)
            save(dest/'body.bin',body);decoded=base.decode(body,ENTRY,2048);save(dest/'body.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,entry=hex(ENTRY),endExclusive=row['endExclusive'],bodyBytes=size,bodySHA256=sha(body),bodyFileOffset=hex(body_offset),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bytes':size,'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,caller,lines,prior_report=source_gate()
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(caller,lines,prior_report,f['caller-setup.bin'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'],'boundary audit differs')
    row=check_row(boundary['exceptionDirectory']['diagnostic'])
    require(bytes.fromhex(row['recordHex'])==f['pdata.bin'] and boundary['exceptionDirectory']['sha256']==req['pdata']['sha256'] and boundary['exceptionDirectory']['bytes']==req['pdata']['bytes'],'pdata differs')
    body=f['body.bin'];lines=json.loads(f['body.asm.private.json']);decoded=base.decode(body,ENTRY,2048)
    require(b''.join(bytes.fromhex(x['hex']) for x in lines)==body and len(body)==row['size'] and sha(body)==report['bodySHA256'] and lines==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'decode audit differs')
    require(report['inputs']==req['stock'] and report['passed'] and report['newBodies']==1 and report['bodyBytesRead']==len(body) and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['bodyBytes']==len(body) and report['instructionCount']==len(lines) and report['toolSHA256']==sha(Path(__file__).read_bytes()) and report['ordinaryMethodHits']==[] and not report['aotApplicable'] and not any(report[x] for x in ('aotBytes','targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshSevenFilesByteIdentical':True,'currentPEReads':0,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(lines),'references':len(decoded['references'])}
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
