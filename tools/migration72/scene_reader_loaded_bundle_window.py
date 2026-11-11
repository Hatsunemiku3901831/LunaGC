"""Collect and independently audit the pinned LoadedBundle source window."""
import argparse
import io
import json
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
from tools.migration72 import scene_reader_backend_identifier_window as previous
from tools.migration72 import scene_mplevel_reader_factory_window as base

ROOT = base.ROOT
REQUEST = ROOT / 'local/migration72/d02-resume-20261111c/scene-backend-semantics/loaded-bundle-one-window-request.private.json'
REQUEST_SHA = '0174f4ee26b991b3c417dd0d9e2dfba2d283dcb6a6eb25632e09eca92551af05'
OUT = ROOT / 'local/migration72/d02-resume-20261111c/scene-loaded-bundle-window'
CALLER, TAIL, ENTRY = 0x14713f4a0, 0x14713f57e, 0x146d43ca0
FILES = ('boundary.private.json','pre-body.private.json','caller-tail.bin','cell.bin','slot.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json')
require, sha, checked, save = base.require, base.sha, base.checked, base.save


def verify_metadata(records, signatures):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(hits==[(11120,78349)],'registered address not unique')
    owner=records[11120];methods=[m for m in owner['methods'] if m['index']==78349]
    require((owner['index'],owner['name'],owner['namespace'])==(11120,'LoadedBundle','MoleMole') and len(methods)==1,'owner/method differs')
    m=methods[0]
    require((m['name'],m['registeredAddress'],m['parameterCount'],m['flags'],m['virtualSlot'])==('LoadBinDataZipFile',hex(ENTRY),5,'0x86',65535),'declaration differs')
    matches=[m for m in signatures['methods'] if m['index']==78349 or m['registeredAddress']==hex(ENTRY)]
    require(len(matches)==1,'signature/address not unique')
    m=matches[0]
    require((m['index'],m['declaringType'],m['name'],m['registeredAddress'])==(78349,11120,'LoadBinDataZipFile',hex(ENTRY)),'signature differs')
    require([(x['index'],x['name'],x['typeIndex']) for x in m['parameters']]==list(zip(range(83275,83280),['hash','offset','length','compressionType','pageSize'],[70,70,70,469279,70])),'parameter identity differs')
    types=signatures['typeReferences']
    require([(types[str(i)]['kind'],types[str(i)]['byRef'],types[str(i)].get('typeDefinition'),types[str(i)].get('name')) for i in (70,70,70,469279,70)]==[('int32',False,None,None)]*3+[('valuetype',False,8024,'CompressionType'),('int32',False,None,None)],'parameter types differ')


def verify_caller(caller, lines, report, audit, tail):
    require(len(caller)==430 and sha(caller)=='a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1','caller raw differs')
    require(report['entry']==hex(CALLER) and report['endExclusive']=='0x14713f64e' and report['bodySHA256']==sha(caller) and report['passed'] and report['instructionCount']==110 and audit['passed'] and audit['twoFreshNineFilesByteIdentical'],'caller provenance differs')
    decoded=base.decode(caller,CALLER,2048)
    require(lines==decoded['lines'] and len(lines)==110 and b''.join(bytes.fromhex(x['hex']) for x in lines)==caller,'caller decode differs')
    require(all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'caller reference provenance differs')
    matches=[x for x in lines if x['address']==hex(TAIL)]
    require(len(matches)==1 and matches[0]['hex']=='e91d47c0ff' and tail==bytes.fromhex(matches[0]['hex']) and previous.rel32_target(TAIL,tail)==ENTRY,'caller tail/boundary differs')


def source_gate():
    checked(REQUEST,15563,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    old_req=json.loads((ROOT/'local/migration72/d02-resume-20261111c/scene-reader-semantics/backend-identifier-one-window-request.private.json').read_bytes())
    helper=ROOT/old_req['reuseTool']['path'];checked(helper,helper.stat().st_size,old_req['reuseTool']['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    records,_=base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load()
    verify_metadata(records,json.loads((ROOT/req['signatures']['file']).read_bytes()))
    source=req['callerBinding']['acceptedCaller']
    caller=(ROOT/source['rawSource']).read_bytes();lines=json.loads((ROOT/source['asmSource']).read_bytes())
    caller_root=(ROOT/source['rawSource']).parent
    report=json.loads((caller_root/'extraction.private.json').read_bytes())
    audit=json.loads((caller_root.parent/'audit.private.json').read_bytes())
    verify_caller(caller,lines,report,audit,caller[TAIL-CALLER:TAIL-CALLER+5])
    for name in FILES:
        require((caller_root/name).read_bytes()==(caller_root.parent/'fresh2'/name).read_bytes(),'caller fresh artifacts differ')
    return req,caller,lines,report,audit


def check_aot(cell,slot):
    require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+78349*8==0x14293e538,'actual cell/table differs')
    require(slot.hex()=='a03cd44601000000' and int.from_bytes(slot,'little')==ENTRY,'actual slot differs')


def check_row(diag):
    row=base.choose(diag)
    require(row['begin']==hex(ENTRY) and 0<row['size']<=8192 and int(row['endExclusive'],16)==ENTRY+row['size'],'body extent/budget differs')
    return row


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-LOADED-BUNDLE-ONE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'newBodies':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,caller,lines,old_report,old_audit=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='caller-tail'
            tail=base.read_at(f,base.offset(sections,TAIL,5,True),5);save(dest/'caller-tail.bin',tail);verify_caller(caller,lines,old_report,old_audit,tail)
            report['stage']='actual-aot'
            cell=base.read_at(f,base.offset(sections,0x1422f9950,8),8);save(dest/'cell.bin',cell)
            require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+78349*8==0x14293e538,'actual cell/table differs')
            slot=base.read_at(f,base.offset(sections,0x14293e538,8),8);save(dest/'slot.bin',slot);check_aot(cell,slot)
            report['stage']='pdata-before-body'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1])
            require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diag=base.pdata_diagnostic(table,directory[0],ENTRY)
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',report);save(dest/'pre-body.private.json',report)
            row=check_row(diag);save(dest/'pdata.bin',bytes.fromhex(row['recordHex']))
            size=row['size'];body_offset=base.offset(sections,ENTRY,size,True)
            report['stage']='single-body';body=base.read_at(f,body_offset,size);report['newBodies']=1
            save(dest/'body.bin',body);decoded=base.decode(body,ENTRY,2048);save(dest/'body.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,entry=hex(ENTRY),endExclusive=row['endExclusive'],bodyBytes=size,bodySHA256=sha(body),bodyFileOffset=hex(body_offset),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bytes':size,'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,caller,lines,old_report,old_audit=source_gate()
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(caller,lines,old_report,old_audit,f['caller-tail.bin']);check_aot(f['cell.bin'],f['slot.bin'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'],'boundary audit differs')
    row=check_row(boundary['exceptionDirectory']['diagnostic'])
    require(bytes.fromhex(row['recordHex'])==f['pdata.bin'] and boundary['exceptionDirectory']['sha256']==req['pdata']['sha256'] and boundary['exceptionDirectory']['bytes']==req['pdata']['bytes'],'pdata differs')
    body=f['body.bin'];saved=json.loads(f['body.asm.private.json']);decoded=base.decode(body,ENTRY,2048)
    require(b''.join(bytes.fromhex(x['hex']) for x in saved)==body and len(body)==row['size'] and sha(body)==report['bodySHA256'] and saved==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'decode audit differs')
    require(report['inputs']==req['stock'] and report['passed'] and report['newBodies']==1 and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['bodyBytes']==len(body) and report['instructionCount']==len(saved) and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not any(report[x] for x in ('targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshNineFilesByteIdentical':True,'currentPEReads':0,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(saved),'references':len(decoded['references'])}
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
