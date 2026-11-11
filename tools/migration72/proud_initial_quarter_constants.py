"""Read only the two approved file-image initialization spans."""
import argparse
import collections
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111c/proud-table-generation-semantics/constant-data'
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/proud-table-generation-semantics/constant-data-request.private.json'
BODY=ROOT/'local/migration72/d02-resume-20261111c/proud-callback-tail-semantics/tail-window/fresh1/code.bin'
ASM=ROOT/'local/migration72/d02-resume-20261111c/proud-callback-tail-semantics/tail-window/fresh1/code.asm.private.json'
BASE=0x1408b94a0
SPANS=((0x1422502f0,16),(0x1439725a0,240))
# Independent fixed instruction identities, offset order and vector-register pairing.
LITERAL=[
 (0x1408b95b7,0x1408b95be,'0f2805326d9901','410f11441d00','xmm0'),
 (0x1408b95c4,0x1408b95cb,'0f2805d58f0b03','410f11441d10','xmm0'),
 (0x1408b95d1,0x1408b95d8,'0f2805d88f0b03','410f11441d20','xmm0'),
 (0x1408b95de,0x1408b95e5,'0f2805db8f0b03','410f11441d30','xmm0'),
 (0x1408b95eb,0x1408b95f2,'0f2805de8f0b03','410f11441d40','xmm0'),
 (0x1408b95f8,0x1408b95ff,'0f2805e18f0b03','410f11441d50','xmm0'),
 (0x1408b9553,0x1408b9605,'440f283595900b03','450f11741d60','xmm14'),
 (0x1408b955b,0x1408b960b,'440f283d9d900b03','450f117c1d70','xmm15'),
 (0x1408b9563,0x1408b9611,'0f2835a6900b03','410f11b41d80000000','xmm6'),
 (0x1408b956a,0x1408b961a,'0f283daf900b03','410f11bc1d90000000','xmm7'),
 (0x1408b9571,0x1408b9623,'440f2805b7900b03','450f11841da0000000','xmm8'),
 (0x1408b9579,0x1408b962c,'440f280dbf900b03','450f118c1db0000000','xmm9'),
 (0x1408b9581,0x1408b9635,'440f2815c7900b03','450f11941dc0000000','xmm10'),
 (0x1408b9589,0x1408b963e,'440f281dcf900b03','450f119c1dd0000000','xmm11'),
 (0x1408b9591,0x1408b9647,'440f2825d7900b03','450f11a41de0000000','xmm12'),
 (0x1408b9599,0x1408b9650,'440f282ddf900b03','450f11ac1df0000000','xmm13')]


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


def validate_source(body,assembly,order):
    require(len(body)==717 and len(order)==16,'source/order length')
    instructions={int(x['address'],16):x for x in assembly['instructions']}
    for i,(load,store,loadhex,storehex,reg) in enumerate(LITERAL):
        expected_address=SPANS[0][0] if i==0 else SPANS[1][0]+16*(i-1)
        row=order[i]
        require(row=={'bytes':16,'dataAddress':hex(expected_address),'loadHex':loadhex,'loadSite':hex(load),'quarterByteOffset':i*16,'storeHex':storehex,'storeSite':hex(store)},'reference/order mismatch')
        lraw=bytes.fromhex(loadhex); sraw=bytes.fromhex(storehex)
        require(body[load-BASE:load-BASE+len(lraw)]==lraw and body[store-BASE:store-BASE+len(sraw)]==sraw,'source bytes mismatch')
        require(load+len(lraw)+struct.unpack('<i',lraw[-4:])[0]==expected_address,'RIP displacement mismatch')
        li,si=instructions[load],instructions[store]
        require(li['hex']==loadhex and si['hex']==storehex and li['size']==len(lraw) and si['size']==len(sraw),'instruction identity mismatch')
        require(li['mnemonic']=='movaps' and li['operands'].startswith(reg+', xmmword ptr [rip ') and si['mnemonic']=='movups' and si['operands'].startswith('xmmword ptr [r13 + rbx') and si['operands'].endswith('], '+reg),'register/width mismatch')
        require(len(li['ripReferences'])==1 and li['ripReferences'][0]['address']==hex(expected_address) and li['ripReferences'][0]['access']=='read','RIP index mismatch')
    require([int(x['storeSite'],16) for x in order]==sorted(int(x['storeSite'],16) for x in order),'store order')


def source_gate():
    check(REQUEST,11946,'87e71a4105dce31f2ab9d5ac91c5b290c948e81186443d42f86ec548335ea996')
    req=json.loads(REQUEST.read_bytes()); pins={p:check(ROOT/p,v['bytes'],v['sha256']) for p,v in sorted(req['pins'].items())}
    pins['tools/game_path.ps1']=check(ROOT/'tools/game_path.ps1',3506,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b')
    require([(int(s['begin'],16),s['bytes']) for s in req['spans']]==list(SPANS),'span mismatch')
    validate_source(BODY.read_bytes(),json.loads(ASM.read_bytes()),req['originalLoadStoreOrder'])
    return req,pins


class FilePE:
    def __init__(self,f):
        self.f=f; h=self.read(0,4096); nt=struct.unpack_from('<I',h,0x3c)[0]
        require(h[:2]==b'MZ' and nt+64<=len(h) and h[nt:nt+4]==b'PE\0\0' and struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'PE header')
        self.base=struct.unpack_from('<Q',h,nt+48)[0]; require(self.base==0x140000000,'PE base')
        count=struct.unpack_from('<H',h,nt+6)[0]; opt=struct.unpack_from('<H',h,nt+20)[0]; off=nt+24+opt; size=count*40
        require(size>0 and 4096+size<=8192,'metadata budget'); table=self.read(off,size); self.sections=[]
        for i in range(count):
            pos=i*40; rva,n,raw=struct.unpack_from('<III',table,pos+12); flags=struct.unpack_from('<I',table,pos+36)[0]
            self.sections.append({'begin':self.base+rva,'end':self.base+rva+n,'raw':raw,'flags':flags})
        overlap=max(0,min(4096,off+size)-max(0,off))
        self.meta={'intervals':[{'offset':0,'bytes':4096},{'offset':off,'bytes':size}],'readCalls':2,'bytesRead':4096+size,'distinctBytes':4096+size-overlap}
    def read(self,offset,size):
        self.f.seek(offset); raw=self.f.read(size); require(len(raw)==size,'short metadata'); return raw
    def mapping(self,va,size,file_size):
        rows=[s for s in self.sections if s['begin']<va+size and va<s['end']]
        require(len(rows)==1 and rows[0]['begin']<=va and va+size<=rows[0]['end'] and rows[0]['flags']&0x40000000,'readable file-backed mapping missing/ambiguous')
        s=rows[0]; offset=s['raw']+va-s['begin']; require(offset+size<=file_size,'span exceeds file')
        return {'begin':hex(va),'bytes':size,'fileOffset':offset,'sectionBegin':hex(s['begin']),'sectionEndExclusive':hex(s['end']),'sectionFlags':hex(s['flags'])}


def analyze(data):
    require(len(data)==256,'constant length')
    counts=collections.Counter(data); histogram=[counts[i] for i in range(256)]
    return {'length':len(data),'sha256':sha(data),'histogram':histogram,'duplicates':[{'byte':i,'count':n} for i,n in enumerate(histogram) if n>1],'missing':[i for i,n in enumerate(histogram) if n==0],'fileImageInitialBytesArePermutation':sorted(data)==list(range(256)),'identity':data==bytes(range(256))}


def collect(dest):
    report={'task':'E-01d1/R3-PROUD-INITIAL-QUARTER-CONSTANT-DATA','stage':'source-gate','staticDataRead':0,'staticDataReadCalls':0,'newCodeBytes':0,'exceptionDirectoryBytes':0,'pdataBytes':0,'AOTBytes':0,'WBytes':0,'seedBytes':0,'cookieBytes':0,'otherGlobalCellBytes':0,'providerBodyBytes':0,'targetCPU':0,'live':0,'runtimeConstantsUnmodified':None,'selected':None,'current':None,'natural':None,'providerPurity':None,'actualEncoded':None,'actualGetterU32':None,'actualRow':False,'resourceReady':False,'d02r3Complete':False,'d02Complete':False,'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        req,report['pins']=source_gate(); game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stage']='five-stock'; report['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['stockIdentity'].items()}
        with (game/'YuanShen.exe').open('rb',buffering=0) as f:
            report['stage']='pre-data'; pe=FilePE(f); report['metadata']=pe.meta
            report['mappings']=[pe.mapping(va,n,req['stockIdentity']['YuanShen.exe']['bytes']) for va,n in SPANS]
            report['sourceOrder']=[{'loadSite':row['loadSite'],'dataAddress':row['dataAddress'],'storeSite':row['storeSite'],'offset':row['quarterByteOffset'],'bytes':row['bytes']} for row in req['originalLoadStoreOrder']]
            save(dest/'pre-data.private.json',report)
            chunks=[]
            for mapping,name in zip(report['mappings'],('span16.bin','span240.bin')):
                f.seek(mapping['fileOffset']); data=f.read(mapping['bytes']); report['staticDataReadCalls']+=1; report['staticDataRead']+=len(data); (dest/name).write_bytes(data)
                require(len(data)==mapping['bytes'],'short static data read'); chunks.append(data)
            c=b''.join(chunks); (dest/'C.bin').write_bytes(c)
            report.update(stage='complete',spans=[{'bytes':len(data),'sha256':sha(data)} for data in chunks],predicate=analyze(c)); save(dest/'result.private.json',report)
            print(json.dumps({'bytes':len(c),'permutation':report['predicate']['fileImageInitialBytesArePermutation'],'identity':report['predicate']['identity'],'sha256':sha(c)})); return 0
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise


def audit(first,second,name):
    a,b=OUT/first,OUT/second; names=sorted(p.name for p in a.iterdir()); require(names==sorted(p.name for p in b.iterdir()),'artifact names differ')
    raw={n:(a/n).read_bytes() for n in names}; require(all(raw[n]==(b/n).read_bytes() for n in names),'fresh differs')
    require(set(names)=={'pre-data.private.json','span16.bin','span240.bin','C.bin','result.private.json'},'artifact set mismatch')
    req=json.loads(REQUEST.read_bytes()); pre=json.loads(raw['pre-data.private.json']); result=json.loads(raw['result.private.json'])
    require(result['stock']==req['stockIdentity'] and all(result['pins'][p]==v for p,v in req['pins'].items()),'source pin report mismatch')
    validate_source(BODY.read_bytes(),json.loads(ASM.read_bytes()),req['originalLoadStoreOrder'])
    require(pre['staticDataRead']==pre['staticDataReadCalls']==0 and pre['mappings']==result['mappings'] and pre['metadata']==result['metadata'] and result['staticDataRead']==256 and result['staticDataReadCalls']==2,'read gate mismatch')
    require(len(raw['span16.bin'])==16 and len(raw['span240.bin'])==240 and raw['C.bin']==raw['span16.bin']+raw['span240.bin'] and analyze(raw['C.bin'])==result['predicate'],'data predicate mismatch')
    require(not any(result[k] for k in ('newCodeBytes','exceptionDirectoryBytes','pdataBytes','AOTBytes','WBytes','seedBytes','cookieBytes','otherGlobalCellBytes','providerBodyBytes','targetCPU','live','actualRow','resourceReady','d02r3Complete','d02Complete')) and all(result[k] is None for k in ('runtimeConstantsUnmodified','selected','current','natural','providerPurity','actualEncoded','actualGetterU32')),'scope mismatch')
    save(OUT/name,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in raw.items()}})


def main():
    p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); args=p.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'fresh basename required'); OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.startswith('audit-') and args.output.endswith('.private.json') and all(x in ('fresh1','fresh2','main-third') for x in args.audit),'audit basename'); audit(*args.audit,args.output); return 0
    require(args.output in ('fresh1','fresh2','main-third'),'output basename'); dest=OUT/args.output; dest.mkdir(); return collect(dest)


if __name__=='__main__': main()
