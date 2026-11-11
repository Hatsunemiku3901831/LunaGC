"""Capture one conditionally relevant World value-factory source region."""
import argparse
import hashlib
import io
import json
import struct
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
HELPER=ROOT/'tools/migration72/scene_mplevel_reader_factory_window.py'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!='fb788634f6ed58436996aebf328ce756dd72a13b2c7eefb5d2a759389f615bd9':raise ValueError('reviewed helper pin mismatch')
from tools.migration72 import scene_mplevel_reader_factory_window as base
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/world-mplevel-parent-consumer/conditional-factory-one-window-request.private.json'
REQUEST_SHA='39f9b19b5fe5524d490389ee9e8f63665328cd8ba069aea98f7cb603bb27cc94'
OUT=ROOT/'local/migration72/d02-resume-20261111c/world-mplevel-type-factory-window'
CALLER,CONTEXT,ENTRY=0x14b135cc0,0x14b135cf8,0x14e8a84a0
FILES=('boundary.private.json','pre-body.private.json','caller-context.bin','aot-cell.bin','aot-slot.bin','pdata.bin','body.bin','body.asm.private.json','body.layout.private.json','extraction.private.json')
require,sha,checked,save=base.require,base.sha,base.checked,base.save


def verify_metadata(records,signatures):
    hits=[(r['index'],m['index']) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
    require(hits==[(62017,514570)],'registered address not unique')
    owner=records[62017];methods=[m for m in owner['methods'] if m['index']==514570]
    require((owner['index'],owner['name'])==(62017,'NPJMDHONOMP') and len(methods)==1,'owner/method differs')
    m=methods[0];require((m['name'],m['registeredAddress'],m['parameterCount'],m['flags'],m['virtualSlot'])==('PCKPBPDHLNB',hex(ENTRY),1,'0x93',65535),'declaration differs')
    found=[m for m in signatures['methods'] if m['index']==514570 or m['registeredAddress']==hex(ENTRY)]
    require(len(found)==1,'signature/address not unique');m=found[0]
    require((m['index'],m['declaringType'],m['name'],m['registeredAddress'])==(514570,62017,'PCKPBPDHLNB',hex(ENTRY)),'signature differs')
    require([(x['index'],x['typeIndex']) for x in m['parameters']]==[(410581,124)],'parameter identity differs')
    r=signatures['typeReferences']['124'];require((r['kind'],r['byRef'])==('uint32',False),'parameter type differs')


def rel32_target(site,raw):
    require(len(raw)==5 and raw[0]==0xe8,'not E8 rel32')
    return site+5+struct.unpack('<i',raw[1:])[0]


def verify_caller(raw,manifest,context):
    require(len(raw)==167 and sha(raw)=='9e635fb07bdbe93ddb1bb4b33c573d5d6eaa58d238dfbb2c2df9dc1f11379575','caller raw differs')
    require(manifest['start']==hex(CALLER) and manifest['end']=='0x14b135d67' and manifest['sha256']==sha(raw) and bytes.fromhex(manifest['bytesHex'])==raw,'caller manifest differs')
    decoded=base.decode(raw,CALLER,2048)['lines'];old=manifest['instructions']
    require(len(decoded)==49 and [(x['address'],x['hex'],x['mnemonic'],x['operands']) for x in decoded]==[(x['va'],x['bytesHex'],x['mnemonic'],x['opStr']) for x in old],'caller decode/boundaries differ')
    wanted={0x14b135cf8:'0fb6440820',0x14b135cfd:'ffc1',0x14b135cff:'894e1c',0x14b135d02:'0435',0x14b135d04:'0fb6c8',0x14b135d07:'e894277703'}
    require({int(x['address'],16):x['hex'] for x in decoded if int(x['address'],16) in wanted}==wanted,'caller context boundary differs')
    require(context.hex()=='0fb6440820ffc1894e1c04350fb6c8e894277703' and context==raw[CONTEXT-CALLER:CONTEXT-CALLER+20] and rel32_target(CONTEXT+15,context[15:])==ENTRY,'fresh context/E8 differs')


def source_gate():
    checked(REQUEST,8942,REQUEST_SHA);req=json.loads(REQUEST.read_bytes());pins={}
    def pin(path,size,digest):
        rec={'bytes':size,'sha256':digest}
        if path in pins:require(pins[path]==rec,'conflicting pin '+path)
        else:pins[path]=checked(ROOT/path,size,digest)
    for name,v in req['fixedArtifacts'].items():
        path=v.get('file','local/migration72/d02-resume-20261111c/world-mplevel-parent-consumer/'+name);pin(path,v['bytes'],v['sha256'])
    source=json.loads((REQUEST.parent/'source-and-first-entry.private.json').read_bytes())
    for path,v in source['sourcePins'].items():pin(path,v['bytes'],v['sha256'])
    pin('docs/CLIENT_72_D02_WORLD_MPLEVEL_PARENT_CONSUMER_20261111C.md',10000,'52eb50a301cfbbaa4e40f207ae46b04cc83aab3fe6f7a59fb959eaca746c9cda')
    spec_path='local/migration72/d02-resume-20261111c/world-type-factory-raw-review/collector-layout-fix-spec.private.json'
    review_path='local/migration72/d02-resume-20261111c/world-type-factory-raw-review/layout-review.private.json'
    pin(spec_path,9355,'802e711249e8cf5d75e30f20c7c582f4b3df1532c185c4a1b1944edfbe494e18')
    pin(review_path,39946,'ccaf52110596c89ad81155beb31a22c8cead8b592e9b68921d05b30ba319e368')
    pin('docs/CLIENT_72_D02_WORLD_TYPE_FACTORY_RAW_REVIEW_20261111C.md',9207,'dd05ed3fb84afab645cede435f586f2b0d74b511e19f37b2766ff8e9a10ccbe5')
    review=json.loads((ROOT/review_path).read_bytes());freeze='local/migration72/d02-resume-20261111c/world-mplevel-type-factory-window/layout-v1-freeze/'
    aliases={'tools/migration72/scene_world_mplevel_type_factory_window.py':freeze+'scene_world_mplevel_type_factory_window.py','tools/migration72/test_scene_world_mplevel_type_factory_window.py':freeze+'test_scene_world_mplevel_type_factory_window.py','docs/CLIENT_72_D02_WORLD_MPLEVEL_TYPE_FACTORY_WINDOW_20261111C.md':freeze+'CLIENT_72_D02_WORLD_MPLEVEL_TYPE_FACTORY_WINDOW_20261111C.md'}
    for path,v in review['sourcePins'].items():pin(aliases.get(path,path),v['bytes'],v['sha256'])
    for key in ('metadata','signatures','profile'):
        v=req[key];pin(v['file'],v['bytes'],v['sha256'])
    for path,size,digest in (('tools/game_path.ps1',3506,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'),('local/migration72/python/capstone/__init__.py',43853,'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'),('local/migration72/python/capstone/lib/capstone.dll',7576576,'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3')):pin(path,size,digest)
    base.verify_profile(json.loads((ROOT/req['profile']['file']).read_bytes()),req['stock'])
    records,_=base.PlainUnpickler(io.BytesIO((ROOT/req['metadata']['file']).read_bytes())).load();verify_metadata(records,json.loads((ROOT/req['signatures']['file']).read_bytes()))
    raw=(ROOT/req['fixedArtifacts']['oldCallerBody']['file']).read_bytes();manifest=json.loads((ROOT/req['fixedArtifacts']['oldCallerManifest']['file']).read_bytes())
    verify_caller(raw,manifest,raw[CONTEXT-CALLER:CONTEXT-CALLER+20])
    return req,pins,raw,manifest


def check_aot(cell,slot):
    require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+514570*8==0x142c92520,'actual cell/table differs')
    require(slot.hex()=='a0848a4e01000000' and int.from_bytes(slot,'little')==ENTRY,'actual slot differs')


def decide(diag,sections):
    decision={'diagnostic':diag,'selected':None,'section':None,'eligible':False,'reason':None}
    try:row=base.choose(diag)
    except ValueError as e:decision['reason']=str(e);return decision
    decision['selected']=row;size=row['size']
    if row['begin']!=hex(ENTRY) or row['endExclusive']!='0x14e8a8628' or size!=392:decision['reason']='body extent/budget differs';return decision
    try:off=base.offset(sections,ENTRY,size,True)
    except ValueError as e:decision['section']={'executableFileBacked':False,'error':str(e)};decision['reason']='section invalid';return decision
    decision['section']={'executableFileBacked':True,'fileOffset':hex(off)};decision['eligible']=True;return decision


def decode_body(body, *, _structural_test_only=False):
    require(len(body)==392,'exact body extent differs')
    if not _structural_test_only:require(sha(body)=='780ba856fa671de842d525ab9dc0684d7f5a0267332cf532bbb902af3f83c9b8','body source SHA differs')
    # Derive the table from the fixed instruction semantics, not from a decoder gap.
    entry=base.decode(body[:28],ENTRY,2048)['lines']
    expected=[('0x14e8a84a0','56','push','rsi'),('0x14e8a84a1','4883ec20','sub','rsp, 0x20'),('0x14e8a84a5','83f907','cmp','ecx, 7'),('0x14e8a84a8','7739','ja','0x14e8a84e3'),('0x14e8a84aa','89c8','mov','eax, ecx'),('0x14e8a84ac','488d0d55010000','lea','rcx, [rip + 0x155]'),('0x14e8a84b3','48630481','movsxd','rax, dword ptr [rcx + rax*4]'),('0x14e8a84b7','4801c8','add','rax, rcx'),('0x14e8a84ba','ffe0','jmp','rax')]
    require([(x['address'],x['hex'],x['mnemonic'],x['operands']) for x in entry]==expected,'dispatch guard/index/scale/transfer differs')
    guard=body[7];lea=entry[5];lea_va=int(lea['address'],16);lea_bytes=bytes.fromhex(lea['hex']);table_base=lea_va+len(lea_bytes)+struct.unpack_from('<i',lea_bytes,3)[0]
    table_count=guard+1;table_end=table_base+table_count*4;end=ENTRY+len(body)
    require((guard,table_count,table_base,table_end)==(7,8,0x14e8a8608,0x14e8a8628) and table_end==end,'derived table extent differs')
    table_offset=table_base-ENTRY;code_end=table_base-2;code=body[:table_offset-2];nop=body[table_offset-2:table_offset];table=body[table_offset:]
    require(nop==bytes.fromhex('6690') and body[-35]==0xcc and len(table)==32 and len(code)==358,'code/NOP/trap/table boundary differs')
    decoded=base.decode(code,ENTRY,2048);lines=decoded['lines'];require(len(lines)==81 and lines[-1]['address']=='0x14e8a8605' and lines[-1]['mnemonic']=='int3','code count/trap differs')
    nop_decoded=base.decode(nop,code_end,2)['lines'];require(len(nop_decoded)==1 and nop_decoded[0]['mnemonic']=='nop','NOP differs')
    starts={int(x['address'],16) for x in lines};rows=[]
    for index in range(table_count):
        raw=table[index*4:index*4+4];delta=struct.unpack('<i',raw)[0];target=table_base+delta
        require(target in starts,'switch target not code boundary')
        rows.append({'selector':index,'entryVA':hex(table_base+4*index),'hex':raw.hex(),'signed32':delta,'target':hex(target)})
    require([x['target'] for x in rows]==['0x14e8a84bc','0x14e8a84ea','0x14e8a84f6','0x14e8a8502','0x14e8a858e','0x14e8a85a7','0x14e8a85c2','0x14e8a85db'],'switch targets differ')
    references=decoded['references'];branch={int(x['address'],16):x for x in references if x['kind']=='branch'};calls={int(x['address'],16):x for x in references if x['kind']=='call'}
    line_by_addr={int(x['address'],16):x for x in lines};edges=[];queue=[ENTRY];reachable=set()
    while queue:
        address=queue.pop(0)
        if address in reachable:continue
        require(address in starts,'CFG reaches outside code boundary');reachable.add(address);line=line_by_addr[address];mnemonic=line['mnemonic'];nxt=address+len(bytes.fromhex(line['hex']))
        if address in branch:
            ref=branch[address]
            if ref['target'] is None:
                require(address==0x14e8a84ba and mnemonic=='jmp','unresolved computed branch')
                next_edges=[('computed-switch',int(x['target'],16)) for x in rows]
            else:
                target=int(ref['target'],16);next_edges=[('direct-jump' if mnemonic=='jmp' else 'conditional',target)]
                if mnemonic!='jmp':next_edges.append(('conditional-fallthrough',nxt))
        elif mnemonic in ('ret','retf','int3','ud2','hlt'):next_edges=[]
        else:next_edges=[('syntactic-normal-call-continuation-only' if address in calls else 'fallthrough',nxt)]
        for kind,target in next_edges:
            require(target in starts,'CFG edge enters NOP/table/outside/interior');edges.append({'kind':kind,'source':hex(address),'target':hex(target)});queue.append(target)
    require(reachable==starts,'unexplained/unreachable code bytes')
    require(len(code)+len(nop)+len(table)==len(body) and code+nop+table==body,'layout coverage differs')
    require([x['address'] for x in decoded['returns']]==['0x14e8a85fa'],'RET boundary differs')
    class_refs=[x for x in references if x['address']=='0x14e8a84ea' and x['kind']=='rip'];allocator=[x for x in references if x['address']=='0x14e8a8595' and x['kind']=='call']
    require(len(class_refs)==1 and class_refs[0]['target']=='0x14589b1b8' and len(allocator)==1 and allocator[0]['target']=='0x1405336c0','case1 source/A stop differs')
    layout={'layoutComplete':True,'logicalFunctionComplete':False,'coverageBytes':392,'overlapBytes':0,'uncoveredBytes':0,'code':{'begin':hex(ENTRY),'endExclusive':hex(code_end),'bytes':len(code),'sha256':sha(code),'instructionCount':len(lines),'boundaries':[x['address'] for x in lines]},'nop':{'begin':hex(code_end),'endExclusive':hex(table_base),'hex':nop.hex(),'instruction':nop_decoded[0],'entryDerivedReachable':False},'table':{'begin':hex(table_base),'endExclusive':hex(table_end),'widthBytes':4,'indexRange':[0,guard],'bytes':len(table),'sha256':sha(table),'rawHex':table.hex(),'rows':rows,'derivation':'unsigned JA guard; RIP LEA base; signed MOVSXD scale4; base addition; JMP RAX'},'cfgEdges':edges,'callContinuations':'syntactic only; return is not observed','case1':{'selector':1,'target':rows[1]['target'],'requestedClassCell':'0x14589b1b8','allocatorCallSite':'0x14e8a8595','allocatorTarget':'0x1405336c0','allocationReturnAndDependencies':'frozen'},'normalEndFallthroughIntoTable':False}
    return {**decoded,'layout':layout,'computedControlEdges':[{'address':'0x14e8a84ba','kind':'resolved-switch','targets':[x['target'] for x in rows]}],'externalBranches':[],'regionEndFallthrough':{'possible':False,'sourceLastInstruction':'0x14e8a8605','target':None},'linearDecodeLimit':'code/NOP/table classified from bounded dispatch; no callee execution or return asserted','logicalFunctionComplete':False}


def collect(dest):
    report={'task':'F-03d3s2/WORLD-MPLEVEL-CONDITIONAL-TYPE-FACTORY-WINDOW','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'conditionalSelector':1,'actualReaderFrame':False,'naturalMapProduced':False,'MPLevelObjectProduced':False,'logicalFunctionComplete':False,'newBodies':0,'bodyBytesRead':0,'targetCPU':0,'nativeExecution':0,'models':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'globalContents':0}
    try:
        req,pins,caller,manifest=source_gate();report['pins']=pins;report['stage']='five-stock'
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['stock']={name:checked(game/name,v['bytes'],v['sha256']) for name,v in req['stock'].items()}
        with (game/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger
            require(ledger['headerBytes']+ledger['sectionTableBytes']<=8192,'PE header budget exceeded')
            report['stage']='caller-context';context=base.read_at(f,base.offset(sections,CONTEXT,20,True),20);save(dest/'caller-context.bin',context);verify_caller(caller,manifest,context)
            report['stage']='aot';cell=base.read_at(f,base.offset(sections,0x1422f9950,8),8);save(dest/'aot-cell.bin',cell)
            require(cell.hex()=='d0548a4201000000' and int.from_bytes(cell,'little')+514570*8==0x142c92520,'actual cell/table differs')
            slot=base.read_at(f,base.offset(sections,0x142c92520,8),8);save(dest/'aot-slot.bin',slot);check_aot(cell,slot)
            report['stage']='pdata-before-body';require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1]);require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            decision=decide(base.pdata_diagnostic(table,directory[0],ENTRY),sections);report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'decision':decision}
            save(dest/'boundary.private.json',report)
            if decision['selected'] is not None:save(dest/'pdata.bin',bytes.fromhex(decision['selected']['recordHex']))
            save(dest/'pre-body.private.json',report);require(decision['eligible'],decision['reason'])
            row=decision['selected'];size=row['size'];off=int(decision['section']['fileOffset'],16)
            report['stage']='single-body';body=base.read_at(f,off,size);report['newBodies']=1;report['bodyBytesRead']=len(body);save(dest/'body.bin',body)
            decoded=decode_body(body);save(dest/'body.asm.private.json',decoded['lines']);save(dest/'body.layout.private.json',decoded['layout'])
            report.update(stage='complete',passed=True,entry=hex(ENTRY),endExclusive=row['endExclusive'],bodyBytes=size,bodySHA256=sha(body),instructionCount=len(decoded['lines']),**{k:decoded[k] for k in ('references','returns','externalTails','computedControlEdges','externalBranches','regionEndFallthrough','linearDecodeLimit','logicalFunctionComplete','capstoneVersion')},layoutComplete=decoded['layout']['layoutComplete'])
            save(dest/'extraction.private.json',report);print(json.dumps({'bytes':size,'sha256':sha(body),'instructions':len(decoded['lines']),'references':len(decoded['references']),'computedEdges':len(decoded['computedControlEdges'])}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,pins,caller,manifest=source_gate();copies=[{n:(p/n).read_bytes() for n in FILES} for p in (first,second)];require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    boundary,pre,report=(json.loads(f[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
    verify_caller(caller,manifest,f['caller-context.bin']);check_aot(f['aot-cell.bin'],f['aot-slot.bin'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'],'boundary audit differs')
    directory=boundary['exceptionDirectory'];decision=directory['decision'];row=decision['selected'];off=int(decision['section']['fileOffset'],16)
    require(decision['eligible'] and directory['bytes']==14344080 and directory['sha256']==req['pdata']['exceptionDirectorySHA256'] and bytes.fromhex(row['recordHex'])==f['pdata.bin'] and decide(decision['diagnostic'],[(ENTRY,row['size'],off,0x20000000)])==decision,'pdata/section differs')
    body=f['body.bin'];lines=json.loads(f['body.asm.private.json']);decoded=decode_body(body)
    layout=json.loads(f['body.layout.private.json']);reconstructed=b''.join(bytes.fromhex(x['hex']) for x in lines)+bytes.fromhex(layout['nop']['hex'])+bytes.fromhex(layout['table']['rawHex'])
    require(len(body)==row['size'] and sha(body)==report['bodySHA256'] and reconstructed==body and layout==decoded['layout'] and report['layoutComplete'] and lines==decoded['lines'] and all(decoded[k]==report[k] for k in ('references','returns','externalTails','computedControlEdges','externalBranches','regionEndFallthrough','linearDecodeLimit','logicalFunctionComplete','capstoneVersion')),'decode audit differs')
    require(report['pins']==pins and report['stock']==req['stock'] and report['passed'] and report['conditionalSelector']==1 and report['newBodies']==1 and report['bodyBytesRead']==len(body) and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['instructionCount']==len(lines) and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not any(report[k] for k in ('actualReaderFrame','naturalMapProduced','MPLevelObjectProduced','logicalFunctionComplete','targetCPU','nativeExecution','models','liveReads','xdataBytes','calleeBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshTenFilesByteIdentical':True,'currentPEReads':0,'targetCPU':0,'logicalFunctionComplete':False,'artifacts':{n:{'bytes':len(v),'sha256':sha(v)} for n,v in f.items()},'instructionCount':len(lines),'references':len(decoded['references'])}
    save(output,result);print(json.dumps(result))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args();require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'new task-local basename required');OUT.mkdir(parents=True,exist_ok=True)
    if a.audit:
        require(a.output.endswith('.json') and all(Path(x).name==x for x in a.audit),'audit inputs must be basenames');audit(*(OUT/x for x in a.audit),OUT/a.output)
    else:
        dest=OUT/a.output;dest.mkdir();collect(dest)
if __name__=='__main__':main()
