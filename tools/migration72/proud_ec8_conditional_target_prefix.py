"""Read the approved conditional entry one byte at a time to its first transfer."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/conditional-target-prefix'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/next-window-request.private.json'
ENTRY=0x1408b9780
MASK=(1<<64)-1


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


def anchor_check(raw, result):
    require(raw==bytes.fromhex('488d05672f79ff488905a8e66104'),'anchor bytes')
    require(0x141126819+struct.unpack('<i',raw[3:7])[0]==ENTRY and 0x141126820+struct.unpack('<i',raw[10:14])[0]==0x145744ec8,'anchor arithmetic')
    a=result['assessment']; require(a['localBindingProvenUnderFallthrough'] is True and a['producerAddress']==hex(ENTRY) and a['globalBindingProven'] is False and a['currentCallbackTarget'] is None and a['naturalExecution'] is False,'anchor conditions')


def source_gate():
    check(REQUEST,13430,'0b4db606420fa3935fd7128155a0580456ed0073af0f383e716816fcf2de2d60')
    req=json.loads(REQUEST.read_bytes()); pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(req['pins'].items())}
    base=ROOT/'local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-window/fresh1'
    anchor_check((base/'slice.bin').read_bytes(),json.loads((base/'result.private.json').read_bytes()))
    require(req['entry']==hex(ENTRY) and req['methodIndex'] is None and req['ordinaryAOTRequested'] is False,'entry basis mismatch')
    return req,pins


def metadata(f):
    f.seek(0); h=f.read(4096); require(len(h)==4096 and h[:2]==b'MZ','DOS header')
    nt=struct.unpack_from('<I',h,0x3c)[0]; require(nt+64<=len(h) and h[nt:nt+4]==b'PE\0\0','PE header')
    require(struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'AMD64 PE32+ required')
    base=struct.unpack_from('<Q',h,nt+48)[0]; require(base==0x140000000,'preferred base drift')
    count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]; offset=nt+24+opt; length=count*40
    require(length>0 and 4096+length<=8192,'metadata read budget')
    f.seek(offset); table=f.read(length); require(len(table)==length,'short section metadata'); sections=[]
    for i in range(count):
        pos=i*40; rva,size,raw=struct.unpack_from('<III',table,pos+12); flags=struct.unpack_from('<I',table,pos+36)[0]
        sections.append({'begin':base+rva,'end':base+rva+size,'raw':raw,'flags':flags})
    section_at(sections,ENTRY)
    intervals=[{'offset':0,'bytes':4096},{'offset':offset,'bytes':length}]
    overlap=max(0,min(4096,offset+length)-max(0,offset))
    return {'base':hex(base),'machine':'AMD64','format':'PE32+','sections':sections,'readIntervals':intervals,'readCalls':2,'bytesRead':4096+length,'distinctBytes':4096+length-overlap}


def section_at(sections,address):
    hits=[s for s in sections if s['begin']<=address<s['end']]
    require(len(hits)==1 and hits[0]['flags']&0x20000000,'missing/ambiguous executable file-backed mapping')
    return hits[0]


def decoder():
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; cs.skipdata=False
    return cs


def instruction(ins):
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    call=ins.group(capstone.CS_GRP_CALL); jump=ins.group(capstone.CS_GRP_JUMP); ret=ins.group(capstone.CS_GRP_RET)
    unsupported=ins.group(capstone.CS_GRP_INT) or ins.group(capstone.CS_GRP_IRET) or ins.mnemonic in ('hlt','ud0','ud1','ud2','syscall','sysenter','sysexit','sysret','int1','xbegin')
    direct=hex(ins.operands[0].imm&MASK) if (call or jump) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
    cells=[hex((ins.address+ins.size+op.mem.disp)&MASK) for op in ins.operands if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP]
    return {'address':hex(ins.address),'size':ins.size,'hex':bytes(ins.bytes).hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'jump':jump,'ret':ret,'unsupportedFlow':unsupported,'directTarget':direct,'ripOperandAddresses':cells}


def acquire(read_byte,allowed,cs):
    cursor=ENTRY; raw=bytearray(); pending=bytearray(); rows=[]; reads=[]; attempts=[]; stop=None
    while True:
        if len(raw)>=64: stop='byte-cap'; break
        if len(rows)>=16: stop='instruction-cap'; break
        address=cursor+len(pending)
        if not allowed(address): stop='mapping-end'; break
        byte=read_byte(address); reads.append({'address':hex(address),'requestedBytes':1,'bytes':len(byte),'hex':byte.hex()})
        if len(byte)!=1: stop='short-read'; break
        raw.extend(byte); pending.extend(byte)
        decoded=list(cs.disasm(bytes(pending),cursor,count=1))
        attempts.append({'address':hex(cursor),'pendingHex':pending.hex(),'decoded':bool(decoded)})
        if not decoded:
            if len(pending)>=15: stop='instruction-buffer-cap'; break
            continue
        ins=decoded[0]
        if ins.address!=cursor or ins.size!=len(pending) or bytes(ins.bytes)!=bytes(pending): stop='decode-gap-or-trailing'; break
        row=instruction(ins); rows.append(row); cursor+=len(pending); pending.clear()
        if row['directTarget'] in ('0x149a75140','0x1405336c0') or any(v in ('0x149a75140','0x1405336c0') for v in row['ripOperandAddresses']): stop='known-refused-dependency'; break
        if row['unsupportedFlow']: stop='unsupported-flow'; break
        if row['call'] or row['jump'] or row['ret']: stop='first-transfer'; break
    tail=bytes(pending); edge=rows[-1] if rows and stop in ('first-transfer','unsupported-flow','known-refused-dependency') else None
    frozen=bool(edge and (edge['directTarget'] in ('0x149a75140','0x1405336c0') or any(v in ('0x149a75140','0x1405336c0') for v in edge['ripOperandAddresses'])))
    return {'raw':bytes(raw),'instructions':rows,'pendingTailHex':tail.hex(),'reads':reads,'attempts':attempts,'stopReason':stop,'edge':edge,'dependentSemanticsFrozen':frozen}


def collect(dest):
    report={'task':'E-01d1/R3-PROUD-EC8-CONDITIONAL-TARGET-PREFIX','stage':'source-gate','prefixRead':0,'exceptionDirectoryNewBytes':0,'pdataBytes':0,'callerBytes':0,'ordinaryAOTBytes':0,'neighborWindowBytes':0,'globalCellBytes':0,'calleeBytes':0,'xdataBytes':0,'targetCPU':0,'live':0,'currentCallbackTarget':None,'globalBindingProven':False,'naturalExecution':False,'functionComplete':False,'dominanceProven':False,'actualForward':None,'actualSeedEpoch':None,'actualSeedWrite':None,'actualEncoded':None,'actualGetterU32':None,'actualRow':False,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins']=source_gate()
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            report['stage']='pre-prefix'; mapping=metadata(f); report['mapping']=mapping
            section=section_at(mapping['sections'],ENTRY); require(section['raw']+ENTRY-section['begin']<req['stockIdentity']['YuanShen.exe']['bytes'],'entry exceeds file')
            save(dest/'pre-prefix.private.json',report)
            def allowed(address):
                try: s=section_at(mapping['sections'],address)
                except ValueError: return False
                return s['raw']+address-s['begin']<req['stockIdentity']['YuanShen.exe']['bytes']
            def read_byte(address):
                s=section_at(mapping['sections'],address); f.seek(s['raw']+address-s['begin']); return f.read(1)
            report['stage']='prefix'; found=acquire(read_byte,allowed,decoder()); raw=found.pop('raw'); report['prefixRead']=len(raw)
            (dest/'prefix.bin').write_bytes(raw); save(dest/'prefix.asm.private.json',{'instructions':found['instructions'],'pendingTailHex':found['pendingTailHex']})
            report.update(stage='complete' if found['stopReason']=='first-transfer' else 'incomplete',prefixSHA256=sha(raw),prefixBegin=hex(ENTRY),prefixEndExclusive=hex(ENTRY+len(raw)),codeReadCalls=len(found['reads']),source=found)
            save(dest/'result.private.json',report)
            print(json.dumps({'stage':report['stage'],'bytes':len(raw),'instructions':len(found['instructions']),'stop':found['stopReason'],'edge':found['edge'],'sha256':sha(raw)})); return 0
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    require(set(names)=={'pre-prefix.private.json','prefix.bin','prefix.asm.private.json','result.private.json'},'artifact set mismatch')
    req=json.loads(REQUEST.read_bytes()); pre=json.loads(raw['pre-prefix.private.json']); result=json.loads(raw['result.private.json']); asm=json.loads(raw['prefix.asm.private.json']); source=result['source']
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'pin report mismatch')
    require(pre['prefixRead']==0 and pre['mapping']==result['mapping'] and result['mapping']['bytesRead']<=8192 and result['mapping']['readCalls']<=2,'pre gate mismatch')
    code=raw['prefix.bin']; require(len(code)==result['prefixRead']<=64 and result['codeReadCalls']==len(source['reads'])<=64 and sha(code)==result['prefixSHA256'],'raw mismatch')
    require(asm['instructions']==source['instructions'] and asm['pendingTailHex']==source['pendingTailHex'] and b''.join(bytes.fromhex(x['hex']) for x in source['instructions'])+bytes.fromhex(source['pendingTailHex'])==code,'rejoin mismatch')
    require(len(source['attempts'])==len(code) and len(source['reads'])==len(code)+(source['stopReason']=='short-read') and all(v['address']==hex(ENTRY+i) and v['requestedBytes']==1 and v['bytes']==1 and v['hex']==code[i:i+1].hex() for i,v in enumerate(source['reads'][:len(code)])),'ledger mismatch')
    if source['stopReason']=='first-transfer': require(source['edge']==source['instructions'][-1] and int(source['edge']['address'],16)+source['edge']['size']==ENTRY+len(code),'terminal mismatch')
    require(not any(result[k] for k in ('exceptionDirectoryNewBytes','pdataBytes','callerBytes','ordinaryAOTBytes','neighborWindowBytes','globalCellBytes','calleeBytes','xdataBytes','targetCPU','live','globalBindingProven','naturalExecution','functionComplete','dominanceProven','actualRow','resourceReady','d02r3Complete','d02Complete')) and all(result[k] is None for k in ('currentCallbackTarget','actualForward','actualSeedEpoch','actualSeedWrite','actualEncoded','actualGetterU32')),'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'decoderReexecuted':False,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output)
    else:
        require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); collect(dest)


if __name__=='__main__': main()
