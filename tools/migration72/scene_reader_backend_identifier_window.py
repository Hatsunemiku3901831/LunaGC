"""Collect and independently audit one pinned Scene backend identifier window."""
import argparse
import io
import json
import struct
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
from tools.migration72 import scene_mplevel_reader_factory_window as base

ROOT = base.ROOT
REQUEST = ROOT / 'local/migration72/d02-resume-20261111c/scene-reader-semantics/backend-identifier-one-window-request.private.json'
REQUEST_SHA = '4d64dbb5a6189096ef413f0ea7f8380a45b09fd451d705ea4517cd65a68f6f7f'
OUT = ROOT / 'local/migration72/d02-resume-20261111c/scene-backend-identifier-window'
ENTRY, END, CALLER, TAIL = 0x14713f4a0, 0x14713f64e, 0x14713f3c0, 0x14713f430
BODY_SHA = 'a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1'
FILES = ('boundary.private.json','pre-body.private.json','caller-tail.bin','cell.bin','slot.bin','pdata.bin','body.bin','body.asm.private.json','extraction.private.json')
require, sha, checked, save = base.require, base.sha, base.checked, base.save


def verify_metadata(records, signatures):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(hits==[(11132,78586)],'registered address not unique')
    owner=records[11132]; methods=[m for m in owner['methods'] if m['index']==78586]
    require((owner['index'],owner['name'],owner['namespace'])==(11132,'AssetBundleExternalResourceProvider','MoleMole') and len(methods)==1,'owner/method differs')
    m=methods[0]
    require((m['name'],m['registeredAddress'],m['parameterCount'],m['flags'],m['virtualSlot'])==('LoadBinDataZipFile',hex(ENTRY),5,'0x86',65535),'declaration differs')
    matches=[x for x in signatures['methods'] if x['index']==78586 or x['registeredAddress']==hex(ENTRY)]
    require(len(matches)==1,'signature/address not unique')
    m=matches[0]
    require((m['index'],m['declaringType'],m['name'],m['registeredAddress'])==(78586,11132,'LoadBinDataZipFile',hex(ENTRY)),'signature differs')
    require([(x['index'],x['name'],x['typeIndex']) for x in m['parameters']]==list(zip(range(83514,83519),['request','offset','length','compressionType','pageSize'],[27066,70,70,469279,70])),'parameter identity differs')
    types=signatures['typeReferences']
    require([(types[str(i)]['kind'],types[str(i)]['byRef'],types[str(i)].get('typeDefinition'),types[str(i)].get('name')) for i in (27066,70,70,469279,70)]==[('valuetype',False,11130,'ResourceIdentifier'),('int32',False,None,None),('int32',False,None,None),('valuetype',False,8024,'CompressionType'),('int32',False,None,None)],'parameter types differ')


def rel32_target(address, raw):
    require(len(raw)==5 and raw[0]==0xe9,'tail is not E9 rel32')
    return address+5+struct.unpack('<i',raw[1:])[0]


def verify_caller(caller, source, tail):
    rows=[x for x in source['static']['functions'] if x['entry']==hex(CALLER)]
    require(len(rows)==1,'caller provenance not unique')
    row=rows[0]; cursor=CALLER; rebuilt=bytearray()
    for line in row['instructions']:
        require(int(line['address'],16)==cursor,'caller source gap/overlap')
        raw=bytes.fromhex(line['bytes']); rebuilt.extend(raw); cursor+=len(raw)
    require(cursor==0x14713f499 and row['end']==hex(cursor) and row['sha256']==sha(caller) and bytes(rebuilt)==caller and len(caller)==217,'caller source differs')
    decoded=base.decode(caller,CALLER,256)['lines']
    require(len(decoded)==65 and [(x['address'],x['hex'],x['mnemonic'],x['operands']) for x in decoded]==[(x['address'],x['bytes'],x['mnemonic'],x['operands']) for x in row['instructions']],'caller decode differs')
    matching=[x for x in decoded if x['address']==hex(TAIL)]
    require(len(matching)==1 and matching[0]['hex']=='e96b000000' and tail==bytes.fromhex(matching[0]['hex']) and rel32_target(TAIL,tail)==ENTRY,'caller tail/boundary differs')


def source_gate():
    checked(REQUEST,12248,REQUEST_SHA); req=json.loads(REQUEST.read_bytes())
    checked(ROOT/req['reuseTool']['path'],(ROOT/req['reuseTool']['path']).stat().st_size,req['reuseTool']['sha256'])
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items(): checked(ROOT/path,pin['bytes'],pin['sha256'])
    for key in ('metadata','signatures'):
        p=req[key];checked(ROOT/p['file'],p['bytes'],p['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    records,_=base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load()
    verify_metadata(records,json.loads((ROOT/req['signatures']['file']).read_bytes()))
    caller=(ROOT/req['callerBinding']['acceptedCaller']['rawSource']).read_bytes()
    source=json.loads((ROOT/'local/migration72/d02-active/resources/index/native-final-1.json').read_bytes())
    verify_caller(caller,source,caller[TAIL-CALLER:TAIL-CALLER+5])
    report=json.loads((ROOT/'local/migration72/d02-active/resource-payload-index-astra/index-final-1.json').read_bytes())
    hits=[x for x in report['code'] if x['entry']==hex(ENTRY)]
    require(len(hits)==1 and hits[0]['end']==hex(END) and hits[0]['exactPdata'] and hits[0]['sha256']==BODY_SHA,'old code provenance differs')
    return req,caller,source


def check_aot(cell,slot):
    require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+78586*8==0x14293eca0,'actual cell/table differs')
    require(slot.hex()=='a0f4134701000000' and int.from_bytes(slot,'little')==ENTRY,'actual slot differs')


def check_row(diag):
    row=base.choose(diag)
    require(row['begin']==hex(ENTRY) and row['endExclusive']==hex(END) and row['size']==430,'body extent differs')
    return row


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-BACKEND-IDENTIFIER-ONE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'newBodies':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,caller,source=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            report['stage']='caller-tail'
            tail=base.read_at(f,base.offset(sections,TAIL,5,True),5);save(dest/'caller-tail.bin',tail);verify_caller(caller,source,tail)
            report['stage']='actual-aot'
            cell=base.read_at(f,base.offset(sections,0x1422f9950,8),8);save(dest/'cell.bin',cell)
            require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+78586*8==0x14293eca0,'actual cell/table differs')
            slot=base.read_at(f,base.offset(sections,0x14293eca0,8),8);save(dest/'slot.bin',slot);check_aot(cell,slot)
            report['stage']='pdata-before-body'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1])
            require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diag=base.pdata_diagnostic(table,directory[0],ENTRY)
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',report)
            save(dest/'pre-body.private.json',report)
            row=check_row(diag);save(dest/'pdata.bin',bytes.fromhex(row['recordHex']))
            body_offset=base.offset(sections,ENTRY,430,True)
            report['stage']='single-body';body=base.read_at(f,body_offset,430);report['newBodies']=1
            save(dest/'body.bin',body);require(sha(body)==BODY_SHA,'body SHA differs')
            decoded=base.decode(body,ENTRY,256);save(dest/'body.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,entry=hex(ENTRY),endExclusive=hex(END),bodyBytes=len(body),bodySHA256=sha(body),bodyFileOffset=hex(body_offset),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bytes':len(body),'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,caller,source=source_gate()
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(caller,source,f['caller-tail.bin']);check_aot(f['cell.bin'],f['slot.bin'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'],'boundary audit differs')
    row=check_row(boundary['exceptionDirectory']['diagnostic'])
    require(bytes.fromhex(row['recordHex'])==f['pdata.bin'] and boundary['exceptionDirectory']['sha256']==req['pdata']['sha256'] and boundary['exceptionDirectory']['bytes']==req['pdata']['bytes'],'pdata differs')
    body=f['body.bin'];lines=json.loads(f['body.asm.private.json']);decoded=base.decode(body,ENTRY,256)
    require(b''.join(bytes.fromhex(x['hex']) for x in lines)==body and len(body)==430 and sha(body)==BODY_SHA and lines==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'decode audit differs')
    require(report['inputs']==req['stock'] and report['passed'] and report['newBodies']==1 and report['bodySHA256']==BODY_SHA and report['instructionCount']==len(lines) and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not any(report[x] for x in ('targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshNineFilesByteIdentical':True,'currentPEReads':0,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(lines),'references':len(decoded['references'])}
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

