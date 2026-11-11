"""Collect a globally gated, deduplicated set of original control-tail regions."""
import argparse
import hashlib
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

REQUEST=ROOT/'local/migration72/d02-resume-20261111c/scene-read-return-semantics/control-tail-regions-request.private.json'
REQUEST_SHA='0ed83005c2fee73f13e1e1bbf0b59f33df9dbe1d89b1ec92a25d4ace09fd5614'
OUT=ROOT/'local/migration72/d02-resume-20261111c/scene-read-control-tail-regions'
TARGETS=(0x141138f06,0x141138efa,0x141138f85)
OLD=((0x141138c70,0x141138d3c),(0x141138d3c,0x141138edb),(0x141138edb,0x141138efa))
require,sha,checked,save=base.require,base.sha,base.checked,base.save


def jcc_target(site,raw):
    require(len(raw)==6 and raw[0]==0x0f and 0x80<=raw[1]<=0x8f,'not near Jcc rel32')
    return site+6+struct.unpack('<i',raw[2:])[0]


def verify_sources(req,bodies,actual):
    decoded={}
    for (begin,end),body,info in zip(OLD,bodies,req['sourceBinding']['oldKnownRegions']):
        require(len(body)==end-begin and sha(body)==info['sha256'] and info['begin']==hex(begin) and info['endExclusive']==hex(end),'old region pin/range differs')
        decoded[begin]=base.decode(body,begin,2048)['lines']
    rows=req['sourceBinding']['branchSources'];require(len(rows)==8 and len(actual)==8,'branch count differs')
    for row,raw in zip(rows,actual):
        site=int(row['site'],16);target=int(row['target'],16);begin=OLD[0][0] if row['sourceKind']=='core' else OLD[1][0]
        lines=[x for x in decoded[begin] if x['address']==hex(site)]
        require(len(lines)==1 and lines[0]['hex']==row['hex'] and raw.hex()==row['hex'] and raw==bodies[0 if begin==OLD[0][0] else 1][site-begin:site-begin+6] and jcc_target(site,raw)==target,'branch identity/boundary differs')
    return decoded


def source_gate():
    checked(REQUEST,24276,REQUEST_SHA);req=json.loads(REQUEST.read_bytes())
    for group in ('frozenSources','sourceTools'):
        for path,pin in req[group].items():checked(ROOT/path,pin['bytes'],pin['sha256'])
    for pin in req['reuseTools']:
        p=ROOT/pin['path'];checked(p,p.stat().st_size,pin['sha256'])
    for key in ('metadata','signatures'):
        pin=req[key];checked(ROOT/pin['file'],pin['bytes'],pin['sha256'])
    base.verify_profile(json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes()),req['stock'])
    paths=('local/migration72/d02-resume-20261111c/scene-read-core-window/fresh1/body.bin','local/migration72/d02-resume-20261111c/scene-read-core-continuation-region/fresh1/region.bin','local/migration72/d02-resume-20261111c/scene-read-return-continuation-region/fresh1/region.bin')
    bodies=[(ROOT/p).read_bytes() for p in paths]
    rows=req['sourceBinding']['branchSources'];verify_sources(req,bodies,[bytes.fromhex(x['hex']) for x in rows])
    root=(ROOT/paths[2]).parent
    for name in ('boundary.private.json','pre-body.private.json','predecessor-context.bin','pdata.bin','region.bin','region.asm.private.json','extraction.private.json'):
        require((root/name).read_bytes()==(root.parent/'fresh2'/name).read_bytes(),'prior fresh artifacts differ')
    audit=json.loads((root.parent/'audit.private.json').read_bytes());require(audit['passed'] and audit['twoFreshSevenFilesByteIdentical'] and audit['currentPEReads']==0,'prior audit differs')
    return req,bodies


def merge_ranges(ranges):
    merged=[]
    for a,b in sorted(ranges):
        if a>=b:continue
        if merged and a<=merged[-1][1]:merged[-1]=(merged[-1][0],max(b,merged[-1][1]))
        else:merged.append((a,b))
    return merged


def diagnose(diags,sections):
    per=[];unique={};issues=[]
    for target,diag in zip(TARGETS,diags):
        exact,containing=diag['exact'],diag['containing'];selected=None
        if len(containing)!=1:issues.append('nonunique containing '+hex(target))
        else:
            row=containing[0]
            if exact and exact!=[row]:issues.append('exact/containing mismatch '+hex(target))
            key=(row['recordRVA'],row['recordHex']);selected=key
            if key not in unique:unique[key]={'row':row,'targets':[]}
            unique[key]['targets'].append(hex(target))
        per.append({'target':hex(target),'matchLists':diag,'selectedRecord':list(selected) if selected else None})
    regions=[];new_ranges=[]
    for key,value in unique.items():
        row=value['row'];begin=int(row['begin'],16);end=int(row['endExclusive'],16);size=end-begin;intersections=[];remaining=[(begin,end)] if size>0 else []
        for a,b in OLD:
            lo=max(begin,a);hi=min(end,b);n=max(0,hi-lo);intersections.append({'oldBegin':hex(a),'oldEndExclusive':hex(b),'begin':hex(lo) if n else None,'endExclusive':hex(hi) if n else None,'bytes':n})
            next_ranges=[]
            for x,y in remaining:
                if b<=x or y<=a:next_ranges.append((x,y))
                else:
                    if x<a:next_ranges.append((x,a))
                    if b<y:next_ranges.append((b,y))
            remaining=next_ranges
        new_ranges+=remaining
        try:
            off=base.offset(sections,begin,size,True);section={'executableFileBacked':True,'fileOffset':hex(off)}
        except ValueError as e:section={'executableFileBacked':False,'error':str(e)}
        overlap=sum(x['bytes'] for x in intersections)
        if overlap:issues.append('old overlap '+hex(begin))
        if not 0<size<=8192:issues.append('region budget '+hex(begin))
        if not section['executableFileBacked']:issues.append('section '+hex(begin))
        regions.append({'recordRVA':key[0],'recordHex':key[1],'begin':hex(begin),'endExclusive':hex(end),'size':size,'targets':value['targets'],'targetOffsets':{t:int(t,16)-begin for t in value['targets']},'oldIntersections':intersections,'oldOverlapBytes':overlap,'section':section})
    pairs=[]
    for i,x in enumerate(regions):
        for y in regions[i+1:]:
            lo=max(int(x['begin'],16),int(y['begin'],16));hi=min(int(x['endExclusive'],16),int(y['endExclusive'],16));n=max(0,hi-lo)
            pairs.append({'left':x['recordRVA'],'right':y['recordRVA'],'begin':hex(lo) if n else None,'endExclusive':hex(hi) if n else None,'bytes':n})
            if n:issues.append('distinct new rows overlap')
    merged=merge_ranges(new_ranges);total=sum(x['size'] for x in regions)
    if not 1<=len(regions)<=3:issues.append('selected region count')
    if total>8192:issues.append('aggregate region budget')
    old_union=merge_ranges([(int(x['begin'],16),int(x['endExclusive'],16)) for row in regions for x in row['oldIntersections'] if x['bytes']])
    return {'targets':per,'regions':regions,'pairwiseNewIntersections':pairs,'oldOverlapBytes':sum(b-a for a,b in old_union),'distinctNew':{'ranges':[{'begin':hex(a),'endExclusive':hex(b),'bytes':b-a} for a,b in merged],'bytes':sum(b-a for a,b in merged)},'budget':{'regionBytes':total,'maximumBytes':8192,'regionCount':len(regions)},'eligible':not issues,'issues':issues}


def decode_region(body,row,remaining):
    begin=int(row['begin'],16);end=begin+len(body);decoded=base.decode(body,begin,remaining);lines=decoded['lines'];boundaries={x['address']:x for x in lines}
    require(all(t in boundaries for t in row['targets']),'target not instruction boundary')
    external=[x for x in decoded['references'] if x['kind']=='branch' and (x['target'] is None or not begin<=int(x['target'],16)<end)]
    cond=[x for x in external if boundaries[x['address']]['mnemonic']!='jmp'];uncond=[x for x in external if boundaries[x['address']]['mnemonic']=='jmp']
    last=lines[-1];mnemonic=last['mnemonic'].lower();falls=not (mnemonic in ('jmp','ret','retf','int3','ud2','hlt') or mnemonic.startswith('iret'))
    return {**decoded,'externalConditionalBranches':cond,'externalUnconditionalBranches':uncond,'regionEndFallthrough':{'possible':falls,'sourceLastInstruction':last['address'],'target':hex(end) if falls else None},'targetInstructions':{t:boundaries[t] for t in row['targets']}}


def collect(dest):
    report={'task':'F-03d3s2/SCENE-READ-CONTROL-TAIL-REGIONS','stage':'source-gate','requestSHA256':REQUEST_SHA,'toolSHA256':sha(Path(__file__).read_bytes()),'logicalReadComplete':False,'aotApplicable':False,'aotBytes':0,'freshBranchBytes':0,'freshBranchReadCount':0,'newRegions':0,'regionBytesRead':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
    try:
        req,bodies=source_gate();report['stage']='five-stock'
        client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        report['inputs']={name:checked(client/name,pin['bytes'],pin['sha256']) for name,pin in req['stock'].items()}
        with (client/'YuanShen.exe').open('rb') as f:
            directory,sections,ledger=base.pe_info(f);report['PEHeaderLedger']=ledger;report['stage']='branch-sources'
            rows=req['sourceBinding']['branchSources'];raws=[base.read_at(f,base.offset(sections,int(x['site'],16),6,True),6) for x in rows]
            report['freshBranchBytes']=sum(map(len,raws));report['freshBranchReadCount']=len(raws);verify_sources(req,bodies,raws);branches=[{**x,'freshHex':raw.hex(),'computedTarget':hex(jcc_target(int(x['site'],16),raw))} for x,raw in zip(rows,raws)]
            save(dest/'branch-sources.private.json',branches);report['stage']='pdata-before-regions'
            require(directory==(0x1a4e6a40,14344080),'exception directory extent differs')
            table=base.read_at(f,base.offset(sections,base.BASE+directory[0],directory[1]),directory[1]);require(sha(table)=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory hash differs')
            diags=[base.pdata_diagnostic(table,directory[0],t) for t in TARGETS];decision=diagnose(diags,sections)
            report['exceptionDirectory']={'rva':hex(directory[0]),'bytes':len(table),'sha256':sha(table),'decision':decision}
            save(dest/'boundary.private.json',report)
            for row in decision['regions']:save(dest/('pdata-'+row['begin']+'.bin'),bytes.fromhex(row['recordHex']))
            save(dest/'pre-body.private.json',report);require(decision['eligible'],'batch gates failed: '+', '.join(decision['issues']))
            report['stage']='regions';results=[];count=0
            for row in decision['regions']:
                name=row['begin'];body=base.read_at(f,int(row['section']['fileOffset'],16),row['size']);report['newRegions']+=1;report['regionBytesRead']+=len(body);save(dest/('region-'+name+'.bin'),body)
                decoded=decode_region(body,row,2048-count);count+=len(decoded['lines']);save(dest/('region-'+name+'.asm.private.json'),decoded['lines'])
                results.append({'begin':name,'endExclusive':row['endExclusive'],'bytes':len(body),'sha256':sha(body),'instructionCount':len(decoded['lines']),**{k:decoded[k] for k in ('references','returns','externalTails','externalConditionalBranches','externalUnconditionalBranches','regionEndFallthrough','targetInstructions','capstoneVersion')}})
            report.update(stage='complete',passed=True,branchSources=branches,regions=results,instructionCount=count,incomingFrameByTarget=req['sourceBinding']['incomingFrameByTarget'])
            save(dest/'extraction.private.json',report);print(json.dumps({'regions':len(results),'bytes':report['regionBytesRead'],'instructions':count,'regionHashes':[x['sha256'] for x in results]}))
    except Exception as e:
        report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise


def audit(first,second,output):
    req,bodies=source_gate();boundary=json.loads((first/'boundary.private.json').read_bytes());decision=boundary['exceptionDirectory']['decision']
    names=['boundary.private.json','pre-body.private.json','branch-sources.private.json','extraction.private.json']
    for row in decision['regions']:
        name=row['begin'];names+=['pdata-'+name+'.bin','region-'+name+'.bin','region-'+name+'.asm.private.json']
    copies=[{name:(p/name).read_bytes() for name in names} for p in (first,second)];require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    pre=json.loads(f['pre-body.private.json']);report=json.loads(f['extraction.private.json']);branches=json.loads(f['branch-sources.private.json'])
    require(boundary==pre and boundary['exceptionDirectory']==report['exceptionDirectory'] and decision['eligible'],'boundary audit differs')
    verify_sources(req,bodies,[bytes.fromhex(x['freshHex']) for x in branches]);require(branches==report['branchSources'] and report['incomingFrameByTarget']==req['sourceBinding']['incomingFrameByTarget'],'branch provenance differs')
    synthetic=[(int(r['begin'],16),r['size'],int(r['section']['fileOffset'],16),0x20000000) for r in decision['regions']]
    require(diagnose([x['matchLists'] for x in decision['targets']],synthetic)==decision and boundary['exceptionDirectory']['sha256']==req['pdata']['sha256'] and boundary['exceptionDirectory']['bytes']==req['pdata']['bytes'],'boundary coverage differs')
    results=[];count=0;total=0
    for row in decision['regions']:
        name=row['begin'];require(bytes.fromhex(row['recordHex'])==f['pdata-'+name+'.bin'],'pdata differs')
        body=f['region-'+name+'.bin'];saved=json.loads(f['region-'+name+'.asm.private.json']);decoded=decode_region(body,row,2048-count);count+=len(saved);total+=len(body)
        require(len(body)==row['size'] and b''.join(bytes.fromhex(x['hex']) for x in saved)==body and saved==decoded['lines'],'region reconstruction differs')
        results.append({'begin':name,'endExclusive':row['endExclusive'],'bytes':len(body),'sha256':sha(body),'instructionCount':len(saved),**{k:decoded[k] for k in ('references','returns','externalTails','externalConditionalBranches','externalUnconditionalBranches','regionEndFallthrough','targetInstructions','capstoneVersion')}})
    require(results==report['regions'] and report['instructionCount']==count and report['regionBytesRead']==total and report['newRegions']==len(results) and report['freshBranchBytes']==48 and report['freshBranchReadCount']==8 and report['inputs']==req['stock'] and report['passed'] and report['toolSHA256']==sha(Path(__file__).read_bytes()) and not report['logicalReadComplete'] and not report['aotApplicable'] and not any(report[x] for x in ('aotBytes','targetCPU','nativeExecution','liveReads','xdataBytes','calleeBodies','constructorBodies','globalContents')),'scope/input audit differs')
    result={'passed':True,'twoFreshAllFilesByteIdentical':True,'currentPEReads':0,'logicalReadComplete':False,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'regionCount':len(results),'instructionCount':count}
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
