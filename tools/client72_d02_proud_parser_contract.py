"""Frozen static parser/constructor representation contract; no native execution."""
import argparse
import hashlib
import json
import pickle
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'local/migration72/d02-resume-20261110b/c-proud-parser-contract'
LOCATOR = ROOT/'local/migration72/d02-resume-20261110b/c-proud-payload-locator'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
PINS = {
    META:'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    SIG:'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin':'c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d',
}
WINDOWS = (
    ('parser',0x149f0c1f0,0x149f0dfea,1778,'f8b7a4d652a17bcb60fe3d7f7e12dd8058d874256c42c994d0477cfa51d292ee',
     '949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b','18fad719b6c05e695ef3899958c72c34125f961832bdc10f38f17ea3b3474548','collect_parser.private.py'),
    ('constructor',0x149f0e540,0x149f0e6e2,96,'39adc5aacdd2415a955a4b00660d3349e47e0a88d4aaaf29df34306a4df9698f',
     '3a96a19587c2ab68070b78b6ee630de2f87133dbb815e82b04454a77e3ffa3d9','56c86067e2a3f57ab554ce0cbdcf8c71031b5eebc8c359948f9756cff60c25f7','collect_constructor.private.py'),
    ('base-constructor',0x14d5e24e0,0x14d5e2620,82,'aae4fdfd47488cbd1b398224caa2b65342cae508e8e64906da1709ff9db49475',
     '65adcc30d23cab15f28ebfd376f99d00a64b81cd3df0120d5b2ec351aea121e1','7da2d469e909fb4d1871a6bd61d37fee1606ab599828da83302ef6bcee4f5a72','collect_base_constructor.private.py'),
)
FILES = ('boundary.private.json','cell.bin','code.asm.private.json','code.bin','extraction.private.json',
         'pdata.bin','pre-body.private.json','slot.bin')
PAYLOAD_SHA = '82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835'
LOCATOR_SHA = 'e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839'


def pinned(path,digest):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=digest:
        raise ValueError('Fixed source SHA differs: '+str(path.relative_to(ROOT)))
    return raw


def window(files,entry,end,count):
    """Validate complete original code, unique pdata, AOT and contiguous decode."""
    report = json.loads(files['extraction.private.json'])
    code = files['code.bin']
    lines = json.loads(files['code.asm.private.json'])
    begin_rva,end_rva,_ = struct.unpack('<III',files['pdata.bin'])
    if ((begin_rva+0x140000000,end_rva+0x140000000)!=(entry,end)
            or report['entry']!=hex(entry) or report['endExclusive']!=hex(end)
            or report['codeBytes']!=end-entry or len(code)!=end-entry
            or report['instructions']!=count or len(lines)!=count):
        raise ValueError('Original parser window extent or instruction count differs')
    diagnostic = report['exceptionDirectory']['diagnostic']
    if len(diagnostic['exact'])!=1 or diagnostic['exact']!=diagnostic['containing']:
        raise ValueError('Original parser window lacks unique exact pdata')
    if (report['nativeCPU'] or report['liveReads'] or report['xdataBytesRead']
            or report['neighborCalleeBytesRead'] or not report['passed']):
        raise ValueError('Static-only boundary differs')
    if (int.from_bytes(files['slot.bin'],'little')!=entry
            or files['slot.bin'].hex()!=report['aot']['slotHex']
            or files['cell.bin'].hex()!=report['aot']['cellHex']):
        raise ValueError('Original parser AOT QWORD differs')
    cursor,decoded = entry,{}
    for line in lines:
        address = int(line['address'],16)
        raw = bytes.fromhex(line['hex'])
        if address!=cursor or code[address-entry:address-entry+len(raw)]!=raw:
            raise ValueError('Original parser decode bytes or order differs')
        decoded[address]=line
        cursor+=len(raw)
    if cursor!=end:
        raise ValueError('Original parser decode is incomplete')
    return report,decoded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True,help='new task-local JSON basename')
    args = parser.parse_args()
    output = OUT/args.output
    if output.parent.resolve()!=OUT.resolve() or output.suffix!='.json' or output.exists():
        parser.error('output must be a new task-local JSON basename')
    fixed = {p:pinned(ROOT/p,digest) for p,digest in PINS.items()}
    windows,decoded = [],{}
    for stem,entry,end,count,report_sha,code_sha,source_sha,source_name in WINDOWS:
        pinned(OUT/source_name,source_sha)
        copies=[]
        for number in (1,2):
            folder=OUT/(stem+str(number))
            pinned(folder/'extraction.private.json',report_sha)
            pinned(folder/'code.bin',code_sha)
            files={name:(folder/name).read_bytes() for name in FILES}
            report,instructions=window(files,entry,end,count)
            if report['sourceSHA256']!=source_sha:
                raise ValueError('Static collector source provenance differs')
            for p,proof in report['sources'].items():
                pinned(ROOT/p,proof['sha256'])
            copies.append(files)
        if copies[0]!=copies[1]:
            raise ValueError('Fresh static evidence files differ')
        windows.append(dict(stem=stem,extraction=report,
            artifacts={p:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for p,raw in copies[0].items()}))
        decoded[stem]=instructions
    payloads=[pinned(LOCATOR/folder/'payload.bin',PAYLOAD_SHA) for folder in ('final3','final4')]
    locator_reports=[pinned(LOCATOR/folder/'result.private.json',LOCATOR_SHA) for folder in ('final3','final4')]
    if payloads[0]!=payloads[1] or locator_reports[0]!=locator_reports[1] or len(payloads[0])!=2132778:
        raise ValueError('Frozen Proud payload evidence differs')
    records,cells=pickle.loads(fixed[META])
    signatures=json.loads(fixed[SIG]); by_index={m['index']:m for m in signatures['methods']}
    identities=[]
    for owner,index,address in [(63831,529236,'0x149f0c1f0'),(63831,529218,'0x149f0e540'),
                                 (59500,493916,'0x14d5e24e0'),(41105,343631,'0x14f6f13c0'),
                                 (63831,529228,'0x149f0bb40'),(59500,493924,'0x14d5e2fe0')]:
        matches=[(r['index'],m) for r in records for m in r['methods'] if m['registeredAddress']==address]
        if len(matches)!=1 or (matches[0][0],matches[0][1]['index'])!=(owner,index):
            raise ValueError('Unique parser/mode method identity differs')
        sig=by_index.get(index)
        params=[signatures['typeReferences'][str(p['typeIndex'])] for p in sig['parameters']] if sig else []
        identities.append(dict(owner=owner,method=matches[0][1],signature=sig,parameterTypes=params))
    field40=next(f for f in records[63831]['fields'] if f['index']==316249)
    field20=next(f for f in records[59500]['fields'] if f['index']==295067)
    if ((field40['offset'],field40['type'].get('typeDefinition'))!=(0x40,41105)
            or (field20['offset'],field20['type'].get('typeDefinition'))!=(0x20,26072)
            or records[41105]['parent']['typeDefinition']!=26072
            or cells[0x145900658]['type']['typeDefinition']!=41105
            or records[63831]['parent']['typeDefinition']!=59500):
        raise ValueError('Typed representation source fields differ')
    anchors={
        'parser':{
            0x149f0c219:('mov','rcx, qword ptr [rcx + 0x40]'),0x149f0c220:('je','0x149f0c23d'),
            0x149f0c222:('mov','rdx, rdi'),0x149f0c238:('jmp','0x14f6f13c0'),
            0x149f0c243:('call','0x14d5e2680'),0x149f0c2f2:('mov','dword ptr [r14], ecx'),
            0x149f0c32b:('call','0x146e031f0')},
        'constructor':{
            0x149f0e549:('call','0x14d5e24e0'),0x149f0e562:('call','0x14f587b40'),
            0x149f0e58d:('call','0x14d581600'),0x149f0e5ac:('mov','rax, qword ptr [rdi + 0x20]'),
            0x149f0e5b5:('mov','rdx, qword ptr [rip - 0x460df64]'),
            0x149f0e5d1:('mov','qword ptr [rdi + 0x40], rcx'),
            0x149f0e5e5:('cmove','rcx, rax'),0x149f0e5e9:('mov','qword ptr [rdi + 0x40], rcx'),
            0x149f0e663:('mov','rax, qword ptr [rdi + 8]')},
        'base-constructor':{
            0x14d5e24f9:('call','0x14f587b40'),0x14d5e2520:('call','0x14d581600'),
            0x14d5e2532:('mov','rax, qword ptr [rsi]'),0x14d5e2535:('mov','rcx, rsi'),
            0x14d5e2538:('xor','edx, edx'),0x14d5e253a:('call','qword ptr [rax + 0x118]'),
            0x14d5e2540:('mov','qword ptr [rsi + 0x20], rax'),
            0x14d5e25ae:('call','qword ptr [rax + 0x120]')},
    }
    for stem,expected in anchors.items():
        for address,pair in expected.items():
            line=decoded[stem][address]
            if (line['mnemonic'],line['operands'])!=pair:
                raise ValueError('Original representation-flow anchor differs')
    child=decoded['parser'][0x149f0c238]
    raw=bytes.fromhex(child['hex'])
    if raw[0]!=0xe9 or 0x149f0c238+5+int.from_bytes(raw[1:],'little',signed=True)!=0x14f6f13c0:
        raise ValueError('Exact tail child edge differs')
    result=dict(task='E-01/R3-PROUD-PARSER-CONTRACT',sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        fixedPins=PINS,originalWindows=windows,allThreeWindowsEightFilesEqualAcrossTwo=True,
        payload=dict(bytes=len(payloads[0]),sha256=PAYLOAD_SHA,locatorReportSHA256=LOCATOR_SHA,
                     source='fixed host-located originalm_bytes; this audit does not rerun locator or parse payload'),
        methodIdentities=identities,typedSourceFields=[field20,field40],
        configFields=[f for f in records[41105]['fields'] if f['index'] in (203373,203376)],
        nativeConfigExpectedClassCell=cells[0x145900658],reader42005Fields=records[42005]['fields'],
        anchors={stem:{hex(a):decoded[stem][a] for a in expected} for stem,expected in anchors.items()},
        wrapperContract=dict(managed='existingthis+40nonnull -> exact E9 to41105.parser343631, same42005 reader',
            native='this+40null -> base parser493918/14d5e2680 with incomingallocator, native header/QueryWrapperInternal andnative storage body',
            allocatorZero='not a representation selector or proof thatthis+40 was created',
            childEntry='0x14f6f13c0',childOwner=41105,childMethod=343631,childBodyTaken=False),
        constructorContract=dict(baseField='base59500.field295067/+20 is assigned from virtual callresult',
            derivedField='63831.field316249/+40 receivesbase+20 onlyafter actual41105 class-hierarchy checks; null/incompatible writes0',
            managedFactoryCall='14d5e253a: call[actualklass+118], RCX=this, EDX0; RAX->this+20',
            staticFactoryCandidate=dict(owner=63831,method=529228,slot=9,address='0x149f0bb40',
                actualVtableBound=False,bodyTaken=False),
            nativeAlternative='separate branch ensures tagged native headerthis+8 and uses virtualsize atklass+120',
            modePredicate='at each constructor: native path iff AL14f587b40 nonzero AND byte14586ceb2 nonzero AND AL14d581600(6506) zero',
            modeValuesObserved=False,dispatchFlagsObserved=False),
        minimumInputs=dict(representation='sourced mode/dispatch state and exact factory/vtable-definition binding or separately declared controlled class fixture; no forced+20/+40',
            row='actual400190 index key and its twoU32 values or independent original whole-reader row span; fullpayload offset0 is not a row',
            managed='only after representation source is complete, exact child343631 body and required reader/alloc/code/model manifest',
            native='requires original base parser/native allocation and dependency contracts; no defaultTLS or successfulunknown helper'),
        managedRepresentationProvenForActualNewObject=False,parserExecuted=False,rowLocationValuesObserved=False,
        childConditionalPermissionConsumed=False,proudSkillGroupIdAliasApproved=False,secondU32LevelIdentityApproved=False,
        promoteThresholdApproved=False,gTimes100PlusLevelProven=False,resourceReady=False,completeAvatar=False,d02Complete=False,
        auditCurrentPEReads=0,nativeCPU=0,liveReads=0,network=0,jvm=0,database=0)
    raw=(json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8')
    with output.open('xb') as f:f.write(raw)
    print(json.dumps(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),
                         staticWindows=3,staticCodeBytes=8412,staticInstructions=1956,childBodyTaken=False,parserExecuted=False)))


if __name__=='__main__':
    main()
