"""Verify one approved 14-byte local producer/store candidate offline."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-window'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-request.private.json'
BEGIN=0x141126812
END=0x141126820
SLOT=0x145744ec8
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


def source_gate():
    check(REQUEST,12106,'f96b51ce2592315c77ce56daf0daf35991ee73f913b132d079f0bfd664a25c3b')
    req=json.loads(REQUEST.read_bytes())
    pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(req['pins'].items())}
    require(req['scope']['begin']==hex(BEGIN) and req['scope']['endExclusive']==hex(END) and req['scope']['bytes']==14 and req['scope']['requiresPdata'] is False,'scope drift')
    return req,pins


def select_mapping(sections):
    candidates=[s for s in sections if s['begin']<=BEGIN and END<=s['end']]
    require(len(candidates)==1 and candidates[0]['flags']&0x20000000,'missing/ambiguous executable file-backed slice')
    return candidates[0]


def metadata(f):
    # First read bounds the DOS/PE headers; second contains section metadata only.
    f.seek(0); h=f.read(4096); require(len(h)==4096 and h[:2]==b'MZ','DOS header')
    nt=struct.unpack_from('<I',h,0x3c)[0]; require(nt+64<=len(h) and h[nt:nt+4]==b'PE\0\0','PE header')
    require(struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'AMD64 PE32+ required')
    base=struct.unpack_from('<Q',h,nt+48)[0]; require(base==0x140000000,'preferred base drift')
    count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]
    offset=nt+24+opt; length=count*40; require(length>0 and 4096+length<=8192,'metadata read budget')
    f.seek(offset); table=f.read(length); require(len(table)==length,'short section metadata')
    sections=[]
    for i in range(count):
        pos=i*40; rva,size,raw=struct.unpack_from('<III',table,pos+12); flags=struct.unpack_from('<I',table,pos+36)[0]
        sections.append({'begin':base+rva,'end':base+rva+size,'raw':raw,'flags':flags})
    chosen=select_mapping(sections)
    intervals=[{'offset':0,'bytes':4096},{'offset':offset,'bytes':length}]
    overlap=max(0,min(4096,offset+length)-max(0,offset))
    return {'base':hex(base),'machine':'AMD64','format':'PE32+','section':{k:hex(v) for k,v in chosen.items()},'sliceFileOffset':chosen['raw']+BEGIN-chosen['begin'],'readIntervals':intervals,'readCalls':2,'bytesRead':4096+length,'distinctBytes':4096+length-overlap}


def oracle(raw):
    if len(raw)!=14: return {'matched':False,'reason':'slice length'}
    a,b=raw[:7],raw[7:]; problems=[]
    if a[0] not in (0x48,0x4c) or a[1]!=0x8d or a[2]&0xc7!=0x05: problems.append('producer encoding')
    if b[0] not in (0x48,0x4c) or b[1]!=0x89 or b[2]&0xc7!=0x05: problems.append('store encoding')
    ar=((a[2]>>3)&7)+((a[0]>>2)&1)*8; br=((b[2]>>3)&7)+((b[0]>>2)&1)*8
    producer=(BEGIN+7+int.from_bytes(a[3:7],'little',signed=True))&MASK
    slot=(END+int.from_bytes(b[3:7],'little',signed=True))&MASK
    if ar!=br: problems.append('register mismatch')
    if slot!=SLOT: problems.append('destination mismatch')
    return {'matched':not problems,'problems':problems,'producerRegisterNumber':ar,'storeRegisterNumber':br,'producerAddress':hex(producer),'storeAddress':hex(slot),'producerDisplacementSigned':int.from_bytes(a[3:7],'little',signed=True),'storeDisplacementSigned':int.from_bytes(b[3:7],'little',signed=True)}


def decode(raw):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_MEM,X86_OP_REG,X86_REG_RIP
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; rows=[]
    for ins in cs.disasm(raw,BEGIN):
        operands=[]
        for op in ins.operands:
            if op.type==X86_OP_REG: operands.append({'type':'register','name':ins.reg_name(op.reg),'bytes':op.size})
            elif op.type==X86_OP_MEM: operands.append({'type':'memory','base':ins.reg_name(op.mem.base),'bytes':op.size,'displacement':op.mem.disp,'ripTarget':hex((ins.address+ins.size+op.mem.disp)&MASK) if op.mem.base==X86_REG_RIP else None})
            else: operands.append({'type':'other'})
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':bytes(ins.bytes).hex(),'mnemonic':ins.mnemonic,'operandsText':ins.op_str,'operands':operands,'controlTransfer':ins.group(capstone.CS_GRP_CALL) or ins.group(capstone.CS_GRP_JUMP) or ins.group(capstone.CS_GRP_RET)})
    return rows


def assess(raw,rows):
    literal=oracle(raw); errors=[]
    if len(rows)!=2 or [x['address'] for x in rows]!=[hex(BEGIN),hex(BEGIN+7)] or [x['size'] for x in rows]!=[7,7] or b''.join(bytes.fromhex(x['hex']) for x in rows)!=raw:
        errors.append('decode coverage')
    else:
        a,b=rows
        if a['mnemonic']!='lea' or b['mnemonic']!='mov' or a['controlTransfer'] or b['controlTransfer'] or len(a['operands'])!=2 or len(b['operands'])!=2:
            errors.append('instruction form')
        else:
            x,y=a['operands']; z,w=b['operands']
            if not (x['type']=='register' and x['bytes']==8 and y['type']=='memory' and y['base']=='rip' and z['type']=='memory' and z['base']=='rip' and z['bytes']==8 and w['type']=='register' and w['bytes']==8 and x['name']==w['name']):
                errors.append('operand form')
            elif y['ripTarget']!=literal.get('producerAddress') or z['ripTarget']!=literal.get('storeAddress'):
                errors.append('decoder/oracle target mismatch')
            else:
                registers=['rax','rcx','rdx','rbx','rsp','rbp','rsi','rdi','r8','r9','r10','r11','r12','r13','r14','r15']
                if x['name']!=registers[literal['producerRegisterNumber']] or w['name']!=registers[literal['storeRegisterNumber']]: errors.append('decoder/oracle register mismatch')
    matched=literal['matched'] and not errors
    target=literal.get('producerAddress') if matched else None
    refused=target in ('0x149a75140','0x1405336c0')
    return {'status':'local-candidate-matched' if matched else 'not-found/inconclusive','localBindingProvenUnderFallthrough':matched,'globalBindingProven':False,'currentCallbackTarget':None,'naturalExecution':False,'producerAddress':target,'literalOracle':literal,'decoderMismatches':errors,'knownRefusedTarget':target if refused else None,'dependentSemanticsFrozen':refused}


def collect(dest):
    report={'task':'E-01d1/R3-PROUD-EC8-STORE-SLICE-WINDOW','stage':'source-gate','sliceRead':0,'sliceReadCalls':0,'exceptionDirectoryNewBytes':0,'pdataBytes':0,'callerBytes':0,'ordinaryAOTBytes':0,'neighborBytes':0,'retBytes':0,'globalCellBytes':0,'calleeBytes':0,'targetCPU':0,'live':0,'actualForward':None,'actualSeedEpoch':None,'actualEncoded':None,'actualGetterU32':None,'actualRow':False,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins']=source_gate()
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            report['stage']='pre-slice'; report['mapping']=metadata(f)
            require(report['mapping']['sliceFileOffset']+14<=req['stockIdentity']['YuanShen.exe']['bytes'],'slice exceeds file')
            save(dest/'pre-slice.private.json',report)
            report['stage']='slice'; f.seek(report['mapping']['sliceFileOffset']); raw=f.read(14); report['sliceRead']=len(raw); report['sliceReadCalls']=1
            (dest/'slice.bin').write_bytes(raw)
            rows=decode(raw); save(dest/'slice.asm.private.json',rows)
            report.update(stage='complete',sliceSHA256=sha(raw),assessment=assess(raw,rows)); save(dest/'result.private.json',report)
            print(json.dumps({'sliceBytes':len(raw),'status':report['assessment']['status'],'producerAddress':report['assessment']['producerAddress'],'sha256':sha(raw)})); return 0
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    require(set(names)=={'pre-slice.private.json','slice.bin','slice.asm.private.json','result.private.json'},'artifact set mismatch')
    req=json.loads(REQUEST.read_bytes()); pre=json.loads(raw['pre-slice.private.json']); result=json.loads(raw['result.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'pin report mismatch')
    require(pre['sliceRead']==0 and result['sliceRead']==14 and result['sliceReadCalls']==1 and pre['mapping']==result['mapping'] and result['mapping']['bytesRead']<=8192 and result['mapping']['readCalls']<=2,'slice gate mismatch')
    code=raw['slice.bin']; rows=decode(code)
    require(len(code)==14 and sha(code)==result['sliceSHA256'] and rows==json.loads(raw['slice.asm.private.json']) and assess(code,rows)==result['assessment'],'slice mismatch')
    require(not any(result[k] for k in ('exceptionDirectoryNewBytes','pdataBytes','callerBytes','ordinaryAOTBytes','neighborBytes','retBytes','globalCellBytes','calleeBytes','targetCPU','live','actualRow','resourceReady','d02r3Complete','d02Complete')) and all(result[k] is None for k in ('actualForward','actualSeedEpoch','actualEncoded','actualGetterU32')),'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'sameDecoderUsed':True,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output)
    else:
        require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); collect(dest)


if __name__=='__main__': main()
