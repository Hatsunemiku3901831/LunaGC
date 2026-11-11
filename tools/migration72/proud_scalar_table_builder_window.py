"""Collect exactly the approved table-builder window, offline."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-scalar-initializer-semantics/table-builder-window'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-scalar-initializer-semantics/next-window-request.private.json'
ENTRY=0x14146af10
CALLER=0x149a752b0
SETUP=0x149a752bd
SITE=0x149a752c4
SETUP_HEX='488d0d6cdadffbe8475c9ff7488b0d3063dffb48898170e90000'
EXTRA={'tools/game_path.ps1':(3506,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'), 'local/migration72/python/capstone/__init__.py':(43853,'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'), 'local/migration72/python/capstone/x86.py':(2816,'08cbb0a02ff152bcf1fbd7c4f8850b3ade38c25b9f0a6a9b900a55ee75e87c67'), 'local/migration72/python/capstone/x86_const.py':(45756,'83cf4550d9256f5f9b8e218b9b732e46d83ef602b463a01d6eeb778deb28e040'), 'local/migration72/python/capstone/lib/capstone.dll':(None,'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3')}


def require(ok, message):
    if not ok: raise ValueError(message)


def sha(data): return hashlib.sha256(data).hexdigest()


def check(path, size, digest):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk); n+=len(chunk)
    require((size is None or n==size) and h.hexdigest()==digest,'pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}


def save(path, value):
    with path.open('xb') as f: f.write((json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())


class DataOnly(pickle.Unpickler):
    def find_class(self,module,name): raise ValueError('pickle globals forbidden')


def caller_check(setup, site):
    require(setup.hex()==SETUP_HEX and len(site)==5 and site.hex()=='e8475c9ff7' and setup[SITE-SETUP:SITE-SETUP+5]==site,'caller bytes mismatch')
    require(site[0]==0xe8 and SITE+5+struct.unpack('<i',site[1:])[0]==ENTRY,'E8 target mismatch')


def source_gate():
    check(REQUEST,8457,'a73aa4be02ede49fb25a29625d37594a92226747e5565845b4b03be4b9ecac59')
    req=json.loads(REQUEST.read_bytes())
    pins=dict(req['fixedSourcePins'])
    pins.update({p:{'bytes':n,'sha256':h} for p,(n,h) in EXTRA.items()})
    verified={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(pins.items())}
    source='local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-source-window/fresh1/'
    raw=(ROOT/(source+'code-131985.bin')).read_bytes(); asm=json.loads((ROOT/(source+'asm-131985.private.json')).read_bytes())
    setup=raw[SETUP-CALLER:SETUP-CALLER+26]; site=raw[SITE-CALLER:SITE-CALLER+5]; caller_check(setup,site)
    selected=[x for x in asm if SETUP<=int(x['address'],16)<SETUP+26]; cursor=SETUP
    for row in selected:
        data=bytes.fromhex(row['hex']); require(int(row['address'],16)==cursor and raw[cursor-CALLER:cursor-CALLER+len(data)]==data,'caller instruction gap'); cursor+=len(data)
    require(cursor==SETUP+26,'caller boundary incomplete')
    with (ROOT/'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl').open('rb') as f: records,_=DataOnly(f).load()
    require(not [(r['index'],m['index']) for r in records for m in r['methods'] if int(m['registeredAddress'],16)==ENTRY],'ordinary metadata target exists')
    return req,verified


class FilePE:
    def __init__(self,f):
        self.f=f; h=self.read(0,4096); nt=struct.unpack_from('<I',h,0x3c)[0]
        require(h[:2]==b'MZ' and h[nt:nt+4]==b'PE\0\0' and struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'PE header')
        self.base=struct.unpack_from('<Q',h,nt+48)[0]; require(self.base==0x140000000,'PE base')
        count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]; table=self.read(nt+24+opt,count*40); self.sections=[]
        for i in range(count):
            off=i*40; rva,size,raw=struct.unpack_from('<III',table,off+12); flags=struct.unpack_from('<I',table,off+36)[0]
            self.sections.append({'begin':self.base+rva,'end':self.base+rva+size,'raw':raw,'flags':flags})
        self.pdata_rva,self.pdata_size=struct.unpack_from('<II',h,nt+24+0x70+3*8)
    def read(self,offset,size):
        self.f.seek(offset); raw=self.f.read(size); require(len(raw)==size,'short file read'); return raw
    def section(self,va,size,executable=False):
        rows=[x for x in self.sections if x['begin']<=va and va+size<=x['end'] and (not executable or x['flags']&0x20000000)]
        require(len(rows)==1,'file-backed executable section missing/ambiguous'); return rows[0]
    def at(self,va,size,executable=False):
        s=self.section(va,size,executable); return self.read(s['raw']+va-s['begin'],size)


def find_pdata(table,base,rva):
    require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]
    for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
        if begin==ENTRY-base or begin<=ENTRY-base<end:
            row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()}
            if begin==ENTRY-base: exact.append(row)
            if begin<=ENTRY-base<end: containing.append(row)
    overlapping=[]
    if len(exact)==1:
        selected_end=int(exact[0]['endExclusive'],16)-base
        for n,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
            if begin<selected_end and end>ENTRY-base:
                overlapping.append({'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+n*12),'recordHex':table[n*12:n*12+12].hex()})
    return {'exact':exact,'containing':containing,'overlapping':overlapping}


def admit(diag,section):
    require(len(diag['exact'])==1 and diag['exact']==diag['containing']==diag['overlapping'],'pdata missing/ambiguous')
    row=diag['exact'][0]; end=int(row['endExclusive'],16)
    require(int(row['begin'],16)==ENTRY and end>ENTRY and row['size']==end-ENTRY,'invalid extent')
    section(ENTRY,row['size'],True)
    return row


def decode(code):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
    require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'capstone library')
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True; cursor=ENTRY; rows=[]
    for ins in cs.disasm(code,ENTRY):
        raw=bytes(ins.bytes); require(ins.address==cursor and raw==code[cursor-ENTRY:cursor-ENTRY+len(raw)],'decode gap')
        call=ins.group(capstone.CS_GRP_CALL); branch=ins.group(capstone.CS_GRP_JUMP)
        target=hex(ins.operands[0].imm) if (call or branch) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
        refs=[]
        for op in ins.operands:
            if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
                access='address-only' if ins.mnemonic=='lea' else ('read-write' if op.access==3 else 'read' if op.access==1 else 'write' if op.access==2 else 'unknown')
                refs.append({'target':hex(ins.address+ins.size+op.mem.disp),'access':access,'rawAccess':op.access})
        rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'branch':branch,'directTarget':target,'ripReferences':refs})
        cursor+=ins.size
    require(cursor==ENTRY+len(code),'incomplete decode'); require(len(rows)<=128,'instruction budget exceeded')
    return rows


def index(rows):
    edges=[]; rip=[]; refused=[]
    for row in rows:
        if row['call'] or row['branch']:
            edge={'address':row['address'],'call':row['call'],'branch':row['branch'],'directTarget':row['directTarget']}; edges.append(edge)
            if row['directTarget'] in ('0x149a75140','0x1405336c0'): refused.append(edge)
        for ref in row['ripReferences']:
            item={'address':row['address'],'mnemonic':row['mnemonic'],**ref}; rip.append(item)
            if ref['target'] in ('0x149a75140','0x1405336c0'): refused.append(item)
    return {'edges':edges,'ripReferences':rip,'refusedEdges':refused,'dependentSemanticsFrozen':bool(refused)}


def collect(dest):
    result={'task':'E-01d1/R3-PROUD-SCALAR-TABLE-BUILDER-ONE-WINDOW','stage':'source-gate','bodiesRead':0,'ordinaryAotBytes':0,'calleeBodies':0,'neighborWindows':0,'xdataBytes':0,'globalContentBytes':0,'targetCPU':0,'live':0,'actualEncoded':None,'actualGetterU32':None,'actualRow':False,'tableInitialized':False,'resourceReady':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,result['pins']=source_gate()
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stage']='five-stock'; result['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['fixedClientInputs'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            pe=FilePE(f); result['stage']='caller'; setup=pe.at(SETUP,26,True); site=setup[SITE-SETUP:SITE-SETUP+5]; caller_check(setup,site)
            (dest/'caller-setup.bin').write_bytes(setup); (dest/'caller-site.bin').write_bytes(site)
            result['caller']={'setupBegin':hex(SETUP),'setupHex':setup.hex(),'site':hex(SITE),'siteHex':site.hex(),'target':hex(ENTRY),'distinctBytesRead':26}
            result['stage']='pdata-before-body'; require((pe.pdata_rva,pe.pdata_size)==(0x1a4e6a40,14344080),'directory drift')
            table=pe.at(pe.base+pe.pdata_rva,pe.pdata_size); require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','directory hash drift')
            diag=find_pdata(table,pe.base,pe.pdata_rva)
            result['exceptionDirectory']={'rva':hex(pe.pdata_rva),'bytesRead':len(table),'sha256':sha(table),'diagnostic':diag}
            save(dest/'boundary.private.json',result)
            row=admit(diag,pe.section); (dest/'pdata.bin').write_bytes(bytes.fromhex(row['recordHex']))
            result['selected']=row; result['budget']={'bodyMax':256,'instructionsMax':128,'distinctCallerBytes':26,'pdataBytes':12,'boundedEvidenceMax':294,'actualBodyBytes':row['size'],'admitted':row['size']<=256}
            save(dest/'pre-body.private.json',result)
            require(row['size']<=256,'complete body budget exceeded')
            result['stage']='body'; code=pe.at(ENTRY,row['size'],True); (dest/'code.bin').write_bytes(code); result['bodiesRead']=1
            rows=decode(code); save(dest/'code.asm.private.json',rows)
            result.update(stage='complete',passed=True,body={'entry':hex(ENTRY),'bytes':len(code),'sha256':sha(code),'instructions':len(rows)},sourceIndex=index(rows))
            save(dest/'result.private.json',result)
            print(json.dumps({'stage':'complete','bytes':len(code),'instructions':len(rows),'sha256':sha(code)})); return 0
    except Exception as e:
        result.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',result); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir())
    require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    require(set(names)=={'boundary.private.json','caller-site.bin','caller-setup.bin','pdata.bin','pre-body.private.json','code.bin','code.asm.private.json','result.private.json'},'artifact set mismatch')
    req=json.loads(REQUEST.read_bytes()); result=json.loads(raw['result.private.json']); pre=json.loads(raw['pre-body.private.json']); boundary=json.loads(raw['boundary.private.json'])
    require(result['stock']==req['fixedClientInputs'] and all(result['pins'][p]==v for p,v in req['fixedSourcePins'].items()),'source pin report mismatch')
    caller_check(raw['caller-setup.bin'],raw['caller-site.bin'])
    require(pre['bodiesRead']==0 and boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'prebody mismatch')
    row=admit(result['exceptionDirectory']['diagnostic'],lambda *args:None); pdata=raw['pdata.bin']; begin,end,unwind=struct.unpack('<III',pdata)
    require(pdata.hex()==row['recordHex'] and begin+0x140000000==ENTRY and end-begin==row['size'] and hex(unwind)==row['unwindRVA'],'pdata mismatch')
    code=raw['code.bin']; rows=decode(code)
    require(len(code)==row['size']<=256 and sha(code)==result['body']['sha256'] and rows==json.loads(raw['code.asm.private.json']) and index(rows)==result['sourceIndex'],'body/indices mismatch')
    require(result['stage']=='complete' and result['bodiesRead']==1 and not any(result[k] for k in ('ordinaryAotBytes','calleeBodies','neighborWindows','xdataBytes','globalContentBytes','targetCPU','live','actualRow','tableInitialized','resourceReady','d02Complete')) and result['actualEncoded'] is None and result['actualGetterU32'] is None,'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'sameDecoderUsed':True,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output)
    else:
        require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); collect(dest)


if __name__=='__main__': main()
