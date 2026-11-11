"""Collect and independently audit one source-bound Scene reader refill window."""
import argparse
import hashlib
import struct
import io
import json
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / 'tools/migration72/scene_mplevel_reader_factory_window.py'
if hashlib.sha256(HELPER.read_bytes()).hexdigest() != 'fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9':
    raise ValueError('reviewed helper pin mismatch')
from tools.migration72 import scene_mplevel_reader_factory_window as base
REQUEST = ROOT / 'local/migration72/d02-resume-20261111c/scene-token-semantics/reader-refill-one-window-request.private.json'
REQUEST_SHA = 'ed0569bc4236b9bbd8c520104a0d444731571ba18cc09a502f6e68aabf161ada'
OUT = ROOT / 'local/migration72/d02-resume-20261111c/scene-reader-refill-window'
CALLER, SETUP, ENTRY = 0x14b6accb0, 0x14b6accc8, 0x14f614760
FILES = ('boundary.private.json','pre-body.private.json','caller-setup.bin','cell.bin','slot.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json')
require, sha, checked, save = base.require, base.sha, base.checked, base.save


def verify_metadata(records, signatures):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(hits==[(42005,350685)],'registered address not unique')
    owner=records[42005];methods=[m for m in owner['methods'] if m['index']==350685]
    require((owner['index'],owner['name'])==(42005,'OFMKHIMCGKE') and len(methods)==1,'owner/method differs')
    m=methods[0]
    require((m['name'],m['registeredAddress'],m['parameterCount'],m['flags'],m['virtualSlot'])==('MBCIMMBMGHJ',hex(ENTRY),1,'0x86',65535),'declaration differs')
    matches=[m for m in signatures['methods'] if m['index']==350685 or m['registeredAddress']==hex(ENTRY)]
    require(len(matches)==1,'signature/address not unique')
    m=matches[0]
    require((m['index'],m['declaringType'],m['name'],m['registeredAddress'])==(350685,42005,'MBCIMMBMGHJ',hex(ENTRY)),'signature differs')
    require([(x['index'],x['name'],x['typeIndex']) for x in m['parameters']]==[(307541,'OCADCOGOEPM',70)],'parameter identity differs')
    ref=signatures['typeReferences']['70']
    require((ref['kind'],ref['byRef'])==('int32',False),'parameter type differs')


def rel32_target(address,raw):
    require(len(raw)==5 and raw[0]==0xe8,'call is not E8 rel32')
    return address+5+struct.unpack('<i',raw[1:])[0]


def verify_caller(caller,lines,setup):
    require(len(caller)==131 and sha(caller)=='c576218f5e911876ad28cce91c7e55df4dcc7ac468b7adbbcd56757fbf230cbe','caller raw differs')
    decoded=base.decode(caller,CALLER,2048)
    require(lines==decoded['lines'] and len(lines)==42 and b''.join(bytes.fromhex(x['hex']) for x in lines)==caller,'caller decode differs')
    selected=[x for x in lines if x['address'] in (hex(SETUP),hex(SETUP+3),hex(SETUP+8))]
    require([(x['address'],x['hex']) for x in selected]==[(hex(SETUP),'4889f9'),(hex(SETUP+3),'ba01000000'),(hex(SETUP+8),'e88b7af603')],'caller setup/call boundary differs')
    require(setup.hex()=='4889f9ba01000000e88b7af603' and setup==caller[SETUP-CALLER:SETUP-CALLER+13] and rel32_target(SETUP+8,setup[8:])==ENTRY,'fresh setup/E8 differs')


def source_gate():
    checked(REQUEST,16756,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for pin in req['reuseTools']:
        path=ROOT/pin['path'];checked(path,path.stat().st_size,pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    records,_=base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load()
    verify_metadata(records,json.loads((ROOT/req['signatures']['file']).read_bytes()))
    source=req['callerBinding']['acceptedCaller'];caller=(ROOT/source['rawSource']).read_bytes();lines=json.loads((ROOT/source['asmSource']).read_bytes())
    verify_caller(caller,lines,caller[SETUP-CALLER:SETUP-CALLER+13])
    callerroot=(ROOT/source['rawSource']).parent
    for name in ('code.bin','code.asm.private.json','extraction.private.json'):
        require((callerroot/name).read_bytes()==(callerroot.parent/'fresh1'/name).read_bytes(),'caller fresh artifacts differ')
    priorroot=ROOT/'local/migration72/d02-resume-20261111c/scene-token-producer-window'
    for name in ('boundary.private.json','pre-body.private.json','caller-setup.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json'):
        require((priorroot/'fresh1'/name).read_bytes()==(priorroot/'fresh2'/name).read_bytes(),'prior fresh artifacts differ')
    audit=json.loads((priorroot/'audit.private.json').read_bytes())
    require(audit['passed'] and audit['twoFreshSevenFilesByteIdentical'] and audit['currentPEReads']==0,'prior audit differs')
    return req,caller,lines


def check_aot(cell,slot):
    require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+350685*8==0x142b523b8,'actual cell/table differs')
    require(slot.hex()=='6047614f01000000' and int.from_bytes(slot,'little')==ENTRY,'actual slot differs')


def check_row(diag):
    row=base.choose(diag)
    require(row['begin']==hex(ENTRY) and 0<row['size']<=8192 and int(row['endExclusive'],16)==ENTRY+row['size'],'body extent/budget differs')
    return row


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-REFILL-ONE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'newBodies':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,caller,lines=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='caller-setup'
            setup=base.read_at(f,base.offset(sections,SETUP,13,True),13);save(dest/'caller-setup.bin',setup);verify_caller(caller,lines,setup)
            report['stage']='actual-aot'
            cell=base.read_at(f,base.offset(sections,0x1422f9950,8),8);save(dest/'cell.bin',cell)
            require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+350685*8==0x142b523b8,'actual cell/table differs')
            slot=base.read_at(f,base.offset(sections,0x142b523b8,8),8);save(dest/'slot.bin',slot);check_aot(cell,slot)
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
    req,caller,lines=source_gate()
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(caller,lines,f['caller-setup.bin']);check_aot(f['cell.bin'],f['slot.bin'])
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
