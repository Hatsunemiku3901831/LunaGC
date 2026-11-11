"""Collect and audit only the pinned Scene cluster getter pdata region."""
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
REQUEST = ROOT / 'local/migration72/d02-resume-20261111c/scene-reader-cluster-remaining/getter-one-window-request.private.json'
REQUEST_SHA = '68084243b272e0e3d255ddc9361f94fe9df6bc798c4eada67624b86052389ebf'
OUT = REQUEST.parent / 'getter-window'
ENTRY, CALLER = 0x146e52c30, 0x146ecd850
SITES = (0x146ecd910, 0x146ecd96c)
FILES = ('boundary.private.json', 'pre-body.private.json', 'caller-d910.bin', 'caller-d96c.bin', 'aot-cell.bin', 'aot-slot.bin', 'pdata.bin', 'body.bin', 'body.asm.private.json', 'extraction.private.json')
require, sha, checked, save = base.require, base.sha, base.checked, base.save


def verify_metadata(records, signatures):
    aliases = [(r['index'], m['index']) for r in records for m in r['methods'] if m['registeredAddress'] == hex(ENTRY)]
    owner = records[11009]
    methods = [m for m in owner['methods'] if m['index'] == 77588]
    require((owner['index'], owner['namespace'], owner['name']) == (11009, 'MoleMole', 'ObjectPoolNonGenericCluster') and len(methods) == 1, 'ordinary owner/method mismatch')
    m = methods[0]
    require((m['name'], m['nameIndex'], m['registeredAddress'], m['parameterCount'], m['flags'], m['virtualSlot']) == ('get_instance', '0xc0565af', hex(ENTRY), 0, '0x896', 65535), 'ordinary method mismatch')
    hits = [m for m in signatures['methods'] if m['index'] == 77588 or m['registeredAddress'] == hex(ENTRY)]
    require(not hits, 'signature subset hits changed')
    return {'owner': 11009, 'method': m, 'addressAliases': aliases, 'signatureSubsetHits': hits}


def verify_caller(triage, calls):
    rows = [x for x in triage['reusedCompleteProducerRaw'] if x['entry'] == hex(CALLER)]
    require(len(rows) == 1, 'old caller not unique')
    row = rows[0]; cursor = CALLER; raw = bytearray()
    for ins in row['instructions']:
        require(int(ins['address'], 16) == cursor, 'old caller gap/overlap')
        b = bytes.fromhex(ins['bytes']); raw.extend(b); cursor += len(b)
    require(cursor == 0x146ecd9b1 and len(raw) == 353 and sha(raw) == '76774a662e43845da5b04a05126434e58e3a45b5380deb763c29210dcec3dba0', 'old caller differs')
    for pc, call, expected in zip(SITES, calls, ('e81b53f8ff', 'e8bf52f8ff')):
        require(len(call) == 5 and call.hex() == expected and raw[pc-CALLER:pc-CALLER+5] == call, 'caller bytes differ')
        require(call[0] == 0xe8 and pc + 5 + struct.unpack('<i', call[1:])[0] == ENTRY, 'caller rel32 differs')


def check_aot(cell, slot):
    require(cell.hex() == 'd0548a4201000000' and int.from_bytes(cell, 'little') + 77588*8 == 0x14293cd70, 'AOT cell mismatch')
    require(slot.hex() == '302ce54601000000' and int.from_bytes(slot, 'little') == ENTRY, 'AOT slot mismatch')


def check_row(diag):
    row = base.choose(diag)
    require(row['begin'] == hex(ENTRY) and 0 < row['size'] <= 1024, 'pdata begin/size invalid or over budget')
    return row


def source_gate():
    checked(REQUEST, 12733, REQUEST_SHA); req = json.loads(REQUEST.read_bytes())
    for pin in req['frozenSources'] + req['reuseTools'] + list(req['sourceTools'].values()):
        checked(ROOT / pin.get('file', pin.get('path')), pin['bytes'], pin['sha256'])
    checked(ROOT/'tools/migration72/scene_mplevel_reader_factory_window.py', 15169, 'fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9')
    for name in ('metadata', 'signatures'):
        pin=req[name]; checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/req['profile']['file']).read_bytes()),req['stock'])
    records,_ = base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load()
    meta = verify_metadata(records,json.loads((ROOT/req['signatures']['file']).read_bytes()))
    triage = json.loads((ROOT/req['frozenSources'][0]['file']).read_bytes())
    verify_caller(triage,[bytes.fromhex(x['hex']) for x in req['callerBinding']['freshCallSites']])
    return req, meta, triage


def decode(code, entry=ENTRY, max_instructions=256):
    sys.path.insert(0, str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);cs.detail=True
    lines=[];refs=[];returns=[];tails=[];cursor=entry
    instructions=cs.disasm(code,entry,count=max_instructions)
    try:
        while cursor < entry+len(code) and len(lines) < max_instructions:
            ins=next(instructions,None)
            require(ins is not None,'incomplete decode')
            raw=bytes(ins.bytes);require(ins.address==cursor and raw==code[cursor-entry:cursor-entry+len(raw)],'decode gap/overlap')
            line={'address':hex(ins.address),'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str};lines.append(line)
            if ins.group(capstone.CS_GRP_RET):returns.append(line)
            if ins.group(capstone.CS_GRP_JUMP) or ins.group(capstone.CS_GRP_CALL):
                direct=next((hex(op.imm) for op in ins.operands if op.type==X86_OP_IMM),None)
                kind='call' if ins.group(capstone.CS_GRP_CALL) else 'branch'
                refs.append({'address':hex(ins.address),'kind':kind,'target':direct})
                if ins.mnemonic=='jmp' and (direct is None or not entry<=int(direct,16)<entry+len(code)):tails.append(dict(line,target=direct))
            for op in ins.operands:
                if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:refs.append({'address':hex(ins.address),'kind':'rip','target':hex(ins.address+ins.size+op.mem.disp)})
            cursor+=len(raw)
        require(cursor==entry+len(code),'instruction budget reached with undecoded bytes')
    finally:
        instructions.close()
    return {'lines':lines,'references':refs,'returns':returns,'externalTails':tails,'capstoneVersion':capstone.__version__}


def read_body(f, offset, size, dest, report):
    f.seek(offset);body=f.read(size);report['newBodies']=1;report['bodyBytesRead']=len(body);save(dest/'body.bin',body)
    require(len(body)==size,'short body read')
    return body


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READER-CLUSTER-GET-INSTANCE-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'newBodies':0,'bodyBytesRead':0,'callerBytesRead':0,'aotBytesRead':0,'pdataSelectedBytes':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'globalContents':0}
    try:
        req, meta, triage = source_gate(); report['metadata'] = meta; report['stage'] = 'five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f); report['PEHeaderLedger']=ledger; report['stage']='caller'
            calls=[]
            for pc,name in zip(SITES,('caller-d910.bin','caller-d96c.bin')):
                call=base.read_at(f,base.offset(sections,pc,5,True),5); report['callerBytesRead']+=len(call);save(dest/name,call); calls.append(call)
            verify_caller(triage,calls);report['stage']='aot'
            cell=base.read_at(f,base.offset(sections,0x1422f9950,8),8);report['aotBytesRead']+=len(cell);save(dest/'aot-cell.bin',cell)
            require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+77588*8==0x14293cd70,'AOT cell mismatch')
            slot=base.read_at(f,base.offset(sections,0x14293cd70,8),8);report['aotBytesRead']+=len(slot);save(dest/'aot-slot.bin',slot);check_aot(cell,slot)
            report['stage']='pdata-before-body';require(directory==(0x1a4e6a40,14344080),'exception directory extent mismatch')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1])
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table)}
            require(sha(table)==req['pdata']['sha256'],'exception directory hash mismatch')
            diag=base.pdata_diagnostic(table,directory[0],ENTRY); report['exceptionDirectory']['diagnostic']=diag
            save(dest/'boundary.private.json',report)
            row=base.choose(diag);save(dest/'pdata.bin',bytes.fromhex(row['recordHex']));report['pdataSelectedBytes']=12
            report.update(entry=hex(ENTRY),endExclusive=row['endExclusive'],bodyBytes=row['size'])
            save(dest/'pre-body.private.json',report)
            row=check_row(diag); body_offset=base.offset(sections,ENTRY,row['size'],True)
            report['stage']='single-body';body=read_body(f,body_offset,row['size'],dest,report)
            decoded=decode(body,ENTRY,256);save(dest/'body.asm.private.json',decoded['lines'])
            report.update(stage='complete',passed=True,bodySHA256=sha(body),bodyFileOffset=hex(body_offset),instructionCount=len(decoded['lines']),references=decoded['references'],returns=decoded['returns'],externalTails=decoded['externalTails'],capstoneVersion=decoded['capstoneVersion'])
            save(dest/'extraction.private.json',report)
            print(json.dumps({'bodyBytes':len(body),'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first, second, output):
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)]
    require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    for pc,name,expected in zip(SITES,('caller-d910.bin','caller-d96c.bin'),('e81b53f8ff','e8bf52f8ff')):
        call=f[name];require(call.hex()==expected and pc+5+struct.unpack('<i',call[1:])[0]==ENTRY,'caller audit mismatch')
    check_aot(f['aot-cell.bin'],f['aot-slot.bin'])
    require(boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'],'boundary mismatch')
    ex=report['exceptionDirectory'];require(ex['bytes']==14344080 and ex['sha256']=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory pin mismatch')
    row=check_row(ex['diagnostic']);require(f['pdata.bin']==bytes.fromhex(row['recordHex']),'pdata mismatch')
    body=f['body.bin'];decoded=decode(body,ENTRY,256)
    require(len(body)==row['size'] and sha(body)==report['bodySHA256'] and decoded['lines']==json.loads(f['body.asm.private.json']) and all(decoded[k]==report[k] for k in ('references','returns','externalTails','capstoneVersion')),'decode mismatch')
    require(report['passed'] and report['requestSHA256']==REQUEST_SHA and report['toolSHA256']==sha(Path(__file__).read_bytes()) and (report['newBodies'],report['bodyBytesRead'],report['callerBytesRead'],report['aotBytesRead'],report['pdataSelectedBytes'])==(1,len(body),10,16,12) and not any(report[x] for x in ('targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','globalContents')),'scope mismatch')
    result={'passed':True,'twoFreshFilesByteIdentical':True,'currentPEReads':0,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'instructionCount':len(decoded['lines']),'references':len(decoded['references'])}
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

