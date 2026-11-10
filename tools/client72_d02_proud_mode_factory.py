"""Audit frozen static Proud slot9 and complete normal-mode prerequisites; no native execution."""
import argparse
import hashlib
import json
import pickle
import struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'local/migration72/d02-resume-20261110b/c-proud-mode-factory'
OLD=ROOT/'local/migration72/d02-resume-20261110b/c-proud-parser-contract'
META='local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
PINS={
 META:'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
 'local/migration72/d02-active/avatar/natural-tables/weapon-storage.json':'073578717ebdd192f58187e04532b2090824b370291c3a74747b2ff592aaec91',
 'local/migration72/d02-active/avatar/handler-closure/root-third.config.json':'58eaae01fac4019105543d685dc20e9f076abbe75411a86f83deeded64b55078',
 'local/migration72/d02-active/avatar/handler-closure/native-config-sampling-contract.json':'6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4',
}
WINDOWS=(
 ('mode-a',20540,158254,0x14f587b40,0x14f587bd6,37,'f749f135e5bb78c5ef9d98dd2ecff10c41514e09c4ffbbb750cb32ddf56366ee','d6af2bda0af80fc533dcae1f49adffbdc495ac5840857a715a4756f672b2ea5b'),
 ('mode-b',61440,509390,0x14d581600,0x14d5816c3,53,'e2f8f47e6a8f7d7296abf837f5fb11a8bd17a8bdb2a9ae94acfa334e84ed3c5f','a60cf5434c258a59a9c5c92e8853e5631e469cf47ade3a6233ac9ff2b170f2f7'),
)
FILES=('boundary.private.json','cell.bin','code.asm.private.json','code.bin','extraction.private.json','pdata.bin','pre-body.private.json','slot.bin')
SLOTFILES=('binding.private.json','cell.bin','method529228.bin','slot.bin','slot9-definition.bin','type63831.bin')
COLLECTORS={
 'collect_mode.private.py':'3e0a67eaa3a510c236e4dea1e58fcb32c9d877cfaaf9d173ddc771c4343332f7',
 'collect_slot9.private.py':'15d8d3d9f72439862eae9294f00294684c36c08cd7ce76285e0e9a32073da276',
}


def digest(raw):return hashlib.sha256(raw).hexdigest()


def pinned(path,sha):
 raw=path.read_bytes()
 if digest(raw)!=sha:raise ValueError('fixed source SHA differs: '+str(path.relative_to(ROOT)))
 return raw


def window(files,owner,method,entry,end,count):
 report=json.loads(files['extraction.private.json']);code=files['code.bin'];lines=json.loads(files['code.asm.private.json'])
 begin,finish,_=struct.unpack('<III',files['pdata.bin'])
 diag=report['exceptionDirectory']['diagnostic']
 if len(diag['exact'])!=1 or diag['exact']!=diag['containing']:raise ValueError('unique exact/containing pdata required')
 if (begin+0x140000000,finish+0x140000000)!=(entry,end) or not 0<end-entry<=2048:raise ValueError('approved extent differs')
 if report['entry']!=hex(entry) or report['endExclusive']!=hex(end) or len(code)!=end-entry:raise ValueError('full body extent differs')
 if (report['identity']['owner'],report['identity']['method']['index'])!=(owner,method):raise ValueError('method identity differs')
 if not report['passed'] or any(report[k] for k in ('nativeCPU','liveReads','xdataBytesRead','neighborCalleeBytesRead')):raise ValueError('static boundary differs')
 cell=int.from_bytes(files['cell.bin'],'little');target=int.from_bytes(files['slot.bin'],'little')
 if (target!=entry or report['aot']['slot']!=hex(cell+method*8)
     or report['aot']['cellHex']!=files['cell.bin'].hex() or report['aot']['slotHex']!=files['slot.bin'].hex()):raise ValueError('ordinary AOT source differs')
 if len(lines)!=count or len(lines)>512:raise ValueError('instruction bound/count differs')
 cursor=entry;decoded={}
 for line in lines:
  raw=bytes.fromhex(line['hex']);pc=int(line['address'],16)
  if pc!=cursor or raw!=code[cursor-entry:cursor-entry+len(raw)]:raise ValueError('complete continuous decode differs')
  decoded[pc]=line;cursor+=len(raw)
 if cursor!=end:raise ValueError('decode does not cover full body')
 return report,decoded


def slot_definition(files):
 report=json.loads(files['binding.private.json']);v=report['vtableDefinition'];m=report['method'];raw=files['type63831.bin']
 if len(raw)!=70 or (raw[0x44]^0xf4)!=v['count'] or v['count']<=9:raise ValueError('selected class count differs')
 start=(int.from_bytes(raw[0x28:0x2c],'little')+0x9af313c0)&0xffffffff
 word=int.from_bytes(files['slot9-definition.bin'],'little')
 if (start,v['startIndex'],v['ordinal'],v['index'],word)!=(773617,773617,9,773626,0x6008134c):raise ValueError('selected vtable word/index differs')
 if (m['owner'],m['declaration']['index'],m['virtualSlot'],v['methodDefinition'])!=(63831,529228,9,529228):raise ValueError('static slot owner differs')
 table=int.from_bytes(files['cell.bin'],'little')
 if report['aot']['slot']!=hex(table+529228*8) or int.from_bytes(files['slot.bin'],'little')!=0x149f0bb40:raise ValueError('factory AOT differs')
 if report['naturalVtableObserved'] or report['naturalMethodInfoObserved'] or report['bodyTaken']:raise ValueError('static slot boundary differs')
 if (files['method529228.bin'].hex()!=m['raw']['hex'] or files['type63831.bin'].hex()!=report['typeRecord']['hex']
     or files['slot9-definition.bin'].hex()!=v['word']['hex']):raise ValueError('raw definition provenance differs')
 return report


def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);args=parser.parse_args()
 output=OUT/args.output
 if output.parent.resolve()!=OUT.resolve() or output.suffix!='.json' or output.exists():parser.error('new task-local JSON basename required')
 fixed={name:pinned(ROOT/name,sha) for name,sha in PINS.items()}
 for name,sha in COLLECTORS.items():pinned(OUT/name,sha)
 proofs=[];decoded={}
 for stem,owner,method,entry,end,count,code_sha,report_sha in WINDOWS:
  copies=[]
  for ordinal in (1,2):
   folder=OUT/(stem+str(ordinal));pinned(folder/'code.bin',code_sha);pinned(folder/'extraction.private.json',report_sha)
   files={name:(folder/name).read_bytes() for name in FILES};report,lines=window(files,owner,method,entry,end,count)
   if report['sourceSHA256']!=COLLECTORS['collect_mode.private.py']:raise ValueError('mode collector provenance differs')
   for name,source in report['sources'].items():pinned(ROOT/name,source['sha256'])
   copies.append(files)
  if copies[0]!=copies[1]:raise ValueError('two fresh mode windows differ')
  decoded[stem]=lines
  proofs.append(dict(stem=stem,extraction=report,artifacts={n:dict(bytes=len(raw),sha256=digest(raw)) for n,raw in copies[0].items()}))
 copies=[]
 for ordinal in (1,2):
  folder=OUT/('slot9-'+str(ordinal));pinned(folder/'binding.private.json','52e038ed268dbcab4e46f0484f39b3e22ff885b609fd89e9f200d3c14eabf23e')
  files={n:(folder/n).read_bytes() for n in SLOTFILES};binding=slot_definition(files)
  if binding['sourceSHA256']!=COLLECTORS['collect_slot9.private.py']:raise ValueError('slot collector provenance differs')
  for name,source in binding['sources'].items():pinned(ROOT/name,source['sha256'])
  copies.append(files)
 if copies[0]!=copies[1]:raise ValueError('two fresh static slot definitions differ')
 records,cells=pickle.loads(fixed[META]);fields=[f for f in records[11325]['fields'] if f['index'] in (53821,53824,53913)]
 if len(fields)!=3 or any(f['type'].get('arguments',[{}])[0].get('kind')!='int32' for f in fields if f['index']!=53913):raise ValueError('GlobalVars requires typed Int32 collections')
 oldconfig=json.loads(fixed['local/migration72/d02-active/avatar/handler-closure/root-third.config.json'])
 h=json.loads(fixed['local/migration72/d02-active/avatar/natural-tables/weapon-storage.json'])['functions']['0x14d5b9fa0']
 if h['sha256']!=oldconfig['nativeSources']['0x14d5b9fa0']['sha256']:raise ValueError('frozen serialized helper source differs')
 if fields!=oldconfig['fields']:raise ValueError('frozen GlobalVars typed source differs')
 anchors={'mode-a':{0x14f587b62:'0x14d5b9fa0',0x14f587bbd:'0x14d581600'},
          'mode-b':{0x14d581650:'dword ptr [rcx + rdx*4 + 0x20], r8d'}}
 for stem,expected in anchors.items():
  for pc,operand in expected.items():
   if decoded[stem][pc]['operands']!=operand:raise ValueError('mode flow anchor differs')
 scalar=[
  dict(name='A dispatch',address='0x1457dd17e',width=1,pcs=['0x14f587b44'],normalValue=0),
  dict(name='serialized dispatch1',address='0x145832d09',width=1,pcs=['0x14d5b9fa6'],normalValue=0),
  dict(name='serialized dispatch2',address='0x1457ca1bc',width=1,pcs=['0x14d5b9faf'],normalValue=0),
  dict(name='B dispatch',address='0x145832d1e',width=1,pcs=['0x14d581607'],normalValue=0),
  dict(name='base ctor dispatch',address='0x14582f0b3',width=1,pcs=['0x14d5e2529'],normalValue=0),
  dict(name='derived ctor dispatch',address='0x145837aa8',width=1,pcs=['0x149f0e5a3','0x149f0e617'],normalValue=0),
  dict(name='F original bool byte',address='0x14586ceb2',width=1,pcs=['0x14d5e2502','0x149f0e56b'],normalValue=None),
  dict(name='derived usage-init guard',address='0x145b977f7',width=1,pcs=['0x149f0e59a'],normalValue=None),
 ]
 classes=[dict(cell='0x1458981a0',width=8,owner=20540,guardOffset='0xc7',guardWidth=1,
   sourcePCs=['0x14d5e24e9','0x149f0e54e'],zeroCalls='0x140525e00'),
  dict(cell='0x1458971c0',width=8,owner=61440,guardOffset='0xc7',guardWidth=1,
   sourcePCs=['0x14d5e250b','0x149f0e574','0x14f587b4d','0x14f587ba8'],zeroCalls='0x140525e00')]
 dispatch=[dict(flag=x['name'],flagAddress=x['address'],action='capture nonzero and stop original-mode fixture; no dispatcher tree') for x in scalar[:6]]
 result=dict(task='E-01/R3-PROUD-MODE-FACTORY-SOURCE',sourceSHA256=digest(Path(__file__).read_bytes()),fixedPins=PINS,
  modeWindows=proofs,twoFreshEightFilesEachEqual=True,staticSlot9=binding,twoFreshSixSlotFilesEqual=True,
  slotArtifacts={n:dict(bytes=len(raw),sha256=digest(raw)) for n,raw in copies[0].items()},
  existingSerializedHelper=dict(source=h,priorOriginalExecutionSource='local/migration72/d02-active/avatar/handler-closure/root-third.config.json',
      originalContains=oldconfig['nativeSources']['0x157485660'],thisCardExecutesIt=False),
  typedGlobalVarsFields=fields,classCells=[cells[int(x['cell'],16)] for x in classes],
  modeContract=dict(normalA='!serializedHashSetInt32.Contains(3521) && !codeSwitchesListInt32.Contains(3521)',
   normalB='codeSwitchesListInt32 contains same int32 bit pattern as incoming ECX; direct ctor query6506, A query3521',
   nativePerConstructor='A && F!=0 && !B(6506), evaluated separately by each original constructor',
   managedCandidate='other normal branch; base still calls slot9 and derived still checks actual41105 hierarchy before assigning+40',
   nullList='B returns AL0 for null list or size0; nonzero size with null items and invalid bounds use original error branches',
   nullSerializedSet='H ordinary branch calls original null exception, never invented false',
   valuesObserved=False,allocatorZeroSelectsMode=False),
  readLedger=dict(scalars=scalar,classGuards=classes,dispatchPolicy=dispatch,
   root=dict(address='0x14586b600',width=8,symbol='S',sameEpochRequired=True),
   serialized=dict(fieldIndex=53821,relative='S+0x60e8',width=8,consumerPC='0x14d5b9fbf',
    headerFields=[['+0x00',8,'class (identity alone does not validate generic tuple)'],['+0x10',8,'Slot<Int32>[]'],['+0x18',8,'IEqualityComparer<Int32>'],['+0x20',8,'Int32[] buckets']],
    completeOriginalInput='typed Int32 HashSet storage plus original Contains/RGCTX/comparer manifest from frozen HandlerClosure; header-only97B sampler is insufficient for3521 membership'),
   list=dict(fieldIndex=53824,relative='S+0x6100',width=8,consumerPC='0x14d581617',
    headerFields=[['+0x10',8,'items pointer'],['+0x18',4,'signed logical size']],
    itemsLength=['items+0x18',4],itemsSpan='items+0x20, exact4*logicalSize bytes after original0<=size<=arrayLength checks',
    sourcePCs=['0x14d581623','0x14d58162b','0x14d581634','0x14d581650']),
   usageInit=dict(guard='0x145b977f7',zeroPath='149f0e606 ECX0x1e117 ->14052d0f0, then149f0e610 writes1',
                 noDefault=True,ownedReadyUsageNeedsExplicitMetadataBinding=True),
   fixedReadBudget=dict(uniqueOrdinaryScalarAndClassStateReads=13,uniqueOrdinaryScalarAndClassStateBytes=34,
    stage0Scalars8Bytes=8,classCells2Reads16Bytes=16,classGuards2Reads2Bytes=2,root1Read8Bytes=8,
    collectionPointerReads=2,collectionPointerBytes=16,
    variableCollectionBytes='data-dependent; no natural content read in this card, no new live-read budget approved',
    uniqueSetNotNativeReadCount=True,initializationCanChangeState=True)),
  ownedModelPrerequisites=dict(modeScalars='explicit supplied values for all8 scalar bytes and two class-ready guards; six ordinary dispatch flags0 is an owned fixture choice, never natural observation',
   collections='reuse real typed Int32 GlobalVars, original HashSet constructor/Add/Contains and Int32 comparer/RGCTX, List<Int32> storage; no host membership success stub',
   factory='static vtable-definition binds slot9 to529228; original factory body/extent still requires root approval, natural MethodInfo/klass not supplied',
   branch='mode contract selects representation candidate; cannot write+20/+40 or assume factory return type without original producer'),
  naturalVtableObserved=False,naturalMethodInfoObserved=False,naturalModeObserved=False,factoryBodyTaken=False,
  factoryExecuted=False,managedRepresentationProvenForActualNewObject=False,parserExecuted=False,rowLocationValuesObserved=False,
  child14f6f13c0Approved=False,proudSkillGroupIdAliasApproved=False,secondU32LevelIdentityApproved=False,
  promoteThresholdApproved=False,gTimes100PlusLevelProven=False,resourceReady=False,completeAvatar=False,d02Complete=False,
  currentPEReads=0,nativeCPU=0,liveReads=0,network=0,jvm=0,database=0)
 raw=(json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8')
 with output.open('xb') as f:f.write(raw)
 print(json.dumps(dict(bytes=len(raw),sha256=digest(raw),newStaticWindows=2,codeBytes=345,instructions=90,staticSlot9Bound=True,naturalModeObserved=False)))


if __name__=='__main__':main()
