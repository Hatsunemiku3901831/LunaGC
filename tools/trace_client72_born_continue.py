"""B-02e1: fixed-input born continuation, send entry and asynchronous boundaries.

Runs original instructions with explicit managed storage/UI observation sinks.
No timer/media/SDK success, live ordering, or complete world entry is simulated.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import tempfile
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, number, TypeResolver
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, GLOBAL, OBJECT, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_responses import Reader72
    from .trace_client72_born import response_wire
    from .trace_client72_token import varint, parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, number, TypeResolver
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, GLOBAL, OBJECT, STACK
    from client72_session_runtime import fragment
    from trace_client72_responses import Reader72
    from trace_client72_born import response_wire
    from trace_client72_token import varint, parse_wire

USAGES = ((263668,1,0x1458f4c90,36626), (263791,1,0x1458f4d18,18766),
          (698995,1,0x145887448,142), (700095,1,0x14588ca20,1255),
          (704573,1,0x1458a50e0,61311), (719253,1,0x1458f4d20,18759),
          (274844,1,0x1458f8820,23519),
          (263669,3,0x145a1fc28,142761), (263792,3,0x145a1fd38,142845),
          (263793,3,0x145a1fd40,142861), (263794,3,0x145a1fd48,142862))
CALLBACKS = {0x145a1fc28:0x14cd62270, 0x145a1fd38:0x14b68dcb0,
             0x145a1fd40:0x153d70f80, 0x145a1fd48:0x153d71010}
ROOT, CONTINUE, SEND = 0x14cd68860, 0x14cd62280, 0x15120d730
REJECT, IFIX = 0x1404c2be0, 0x14d8de310
MEDIA, MEDIA_LOOKUP = 0x1523f1120, 0x14c4f1ca0


def declarations(b, m, s, records):
    pe=PE(b);literal=Literals72(b,m);reg=pe.offset(USAGE_REGISTRATION)
    caches={1:number(b,reg+0x48,'Q'),3:number(b,reg+0x10,'Q')}
    resolver=TypeResolver(pe,s,records,pe.offset(HEADER_VA))
    byid={r['index']:r for r in records}
    methods={x['index']:(r,x) for r in records for x in r['methods']}
    bindings=[]
    for row,kind,cell,target in USAGES:
        key=(row*0x57c15286+0x26ddebda)&0xffffffff
        source=((number(m,literal.usages_base+row*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
        slot=number(m,literal.usages_base+row*8+4)^key^0x1855aa65
        if source>>29!=kind or caches[kind]+slot*8!=cell:raise ValueError('Continuation usage binding drift')
        index=source&0x1fffffff
        resolved=resolver.resolve_index(index) if kind==1 else None
        if (resolved.get('typeDefinition') if resolved else index)!=target:raise ValueError('Continuation usage source drift')
        value=dict(usageRow=row,kind=kind,cell=hex(cell),sourceIndex=index)
        if kind==1:value.update(typeIndex=target,name=resolved['name'],arguments=resolved.get('arguments',[]))
        else:
            owner,method=methods[index]
            if method['registeredAddress']!=hex(CALLBACKS[cell]) or method['parameterCount']!=0:raise ValueError('Callback method drift')
            value.update(ownerType=owner['index'],methodDefinition=index,target=method['registeredAddress'])
        bindings.append(value)
    # Field ownership prevents assigning meaning merely from a nearby instruction.
    fields=[]
    for owner,offset,kind,target in ((18753,0x48,'class',68442),(68442,0x10,'class',46311),
                                   (68442,0x18,'bool',None),(68442,0x19,'bool',None),(68442,0x1a,'bool',None),
                                   (18766,0x10,'string',None),(18766,0x18,'class',18753),
                                   (55558,0x63,'bool',None),(55558,0x48,'genericinst',2032),
                                   (61311,0x18,'uint32',None),(61311,0x1c,'uint32',None)):
        f=next(f for f in byid[owner]['fields'] if f.get('offsetKind')=='instance' and f['offset']==offset)
        if f['type']['kind']!=kind or f['type'].get('typeDefinition')!=target:raise ValueError('Continuation field drift')
        fields.append(dict(ownerType=owner,offset=hex(offset),clientField=f['name'],type=f['type']))
    targets=[]
    for entry in (ROOT,CONTINUE,0x14cd67260,0x1507cb3b0,MEDIA,0x1523f1350,0x1503536b0,0x15347b8f0):
        found=[(r,x) for r in records for x in r['methods'] if x['registeredAddress']==hex(entry)]
        if len(found)!=1:raise ValueError('Continuation native owner not unique')
        r,x=found[0];targets.append(dict(entry=hex(entry),ownerType=r['index'],owner=r['name'],method=x['name'],methodIndex=x['index']))
    getter=next(x for x in byid[61311]['methods'] if x.get('virtualSlot')==22)
    if getter['registeredAddress']!='0x150976530' or getter['literalU16Candidate']!=20353:raise ValueError('Stage message getter drift')
    literals=[dict(row,value=literal.decode(row['index'])) for row in literal.group(0x1a108)]
    if {row['index'] for row in literals}!={41326,41327,53062}:raise ValueError('Born movie literals drift')
    return dict(bindings=bindings,fields=fields,methods=targets,literals=literals,
                stageMessage=dict(typeIndex=61311,clientType='ALJAMAECHIP',cmdId=20353,getter='0x150976530',
                    semanticName=None,semanticStatus='unknown',runtimeStatus='not-tested',
                    fields=[dict(objectOffset=28,tag=11,type='uint32',semanticName=None),
                            dict(objectOffset=24,tag=14,type='uint32',semanticName=None)]),
                sceneObject=dict(typeIndex=46311,clientType='BBECFDMDFJC',cmdId=27748,getter='0x1523856a0',
                                 semanticName=None,semanticStatus='unknown',wireStatus='unknown',
                                 declaredMethods=[dict(name=x['name'],methodIndex=x['index'],entry=x['registeredAddress'],slot=x['virtualSlot']) for x in byid[46311]['methods']],
                                 declaredFields=[dict(clientField=f['name'],offset=f['offset'],offsetKind=f['offsetKind'],type=f['type']) for f in byid[46311]['fields']]))


class Continuation72:
    """Only storage, initialization and declared observation sinks are fixtures."""
    def __init__(self,e,decl,flag18=1,born=True,media=False,stage=True,page='none'):
        self.e=e;self.events=[];self.sent=[];self.actions=[];self.allocations=[]
        self.owner=e.alloc(512);self.born=e.alloc();self.ui=e.alloc(2048);self.network=e.alloc(1024)
        self.media=e.alloc();self.sender=e.alloc();self.data=e.alloc();self.preferences=e.alloc(512)
        self.pref_list=e.alloc();self.scene=e.alloc(512);self.cached_action=e.alloc()
        e.write(self.owner+0x48,self.born if born else 0);e.write(self.born+0x18,flag18,'B')
        e.write(self.born+0x19,1,'B');e.write(self.born+0x10,self.scene)
        for off,value in ((0x9e50,e.alloc()),(0x9e60,self.ui),(0x9e80,self.network),(0xbe90,self.owner),
                          (0x10f20,self.media),(0xa0d0,self.sender),(0xa070,self.data),
                          (0xa5f0,e.alloc()),(0x27a38,0),(0x27a48,self.cached_action)):
            e.write(GLOBAL+off,value)
        scene_service=e.alloc();e.write(scene_service+0xb8,e.alloc());e.write(GLOBAL+0x9e40,scene_service)
        e.write(self.network+0x150,e.alloc() if stage else 0);e.write(self.media+0x63,int(media),'B')
        e.write(self.data+0x28,self.preferences);e.write(self.preferences+0xd8,self.pref_list)
        self.lists={self.pref_list:[]}
        self.classes={}
        for bind in decl['bindings']:
            if bind['kind']==1:self.classes[e.synthetic_type(int(bind['cell'],16))]=bind['typeIndex']
        # Metadata/class initialization only. IFix flags remain the actual zero
        # bytes of this fixed image, unless an explicit diversion case changes one.
        for cell in (0x145b937c2,0x145b937e8):e.write(cell,1,'B')
        for cell,target in CALLBACKS.items():
            info=e.alloc(64);e.write(info+8,target);e.write(cell,info)
        for lit in decl['literals']:
            raw=lit['value'].encode('utf-16-le');p=e.alloc(32+len(raw));e.write(p+16,len(raw)//2,'I');e.uc.mem_write(p+20,raw);e.write(int(lit['cell'],16),p)
        self.empty=e.alloc(64);e.write(0x145b74628,self.empty)
        self.page=e.alloc(1024);e.write(self.page,e.read(0x1458f4c90) if page=='match' else e.alloc())
        dictionary=e.alloc();e.write(self.ui+0x1e8,0 if page=='none' else dictionary);e.write(dictionary+0x20,1,'I')
        e.models.update({0x1405336c0:self.allocate,0x14d4480c0:self.message,
                         0x15120d730:self.send,0x1567c69a0:lambda:self.page,
                         0x158820660:self.append,0x15881f800:self.insert,
                         0x14a2109b0:lambda:self.observe('ui-prepare'),
                         0x14a2104f0:lambda:self.observe('ui-mode',e.reg('edx')),
                         0x14d592b30:lambda:self.observe('media-resource-begin'),
                         0x147161b60:lambda:self.observe('missing-born-log'),
                         0x14cd2b920:lambda:self.observe('response-error',e.reg('edx')),
                         0x1532d6720:lambda:self.observe('error-event',e.read(e.reg('rdx'),'I'))})
        for entry in (ROOT,CONTINUE,0x1507d2820,0x1507cb3b0,0x14a42bd90,MEDIA,
                      0x15347b8f0,0x14a9ce8e0,0x14cd68070,0x14cd62270,
                      0x153d70f80,0x153d71010,0x14b68dcb0,0x14cd67260):e.allow(entry)

    def allocate(self):
        e=self.e;cls=e.reg('rcx')
        if cls not in self.classes:raise ValueError(f'Unbound managed allocation {cls:#x}')
        p=e.alloc(512);e.write(p,cls);index=self.classes[cls];self.allocations.append((index,p))
        if index==1255:self.lists[p]=[]
        if index==142:self.actions.append(p)
        return p

    def message(self):
        p=self.allocate();self.e.write(p+0x14,1,'I');return p

    def observe(self,name,value=None):
        self.events.append(dict(name=name,value=value));return 0

    def send(self):
        e=self.e;p=e.reg('rdx')
        if e.reg('rcx')!=self.sender or self.classes[e.read(p)]!=61311:raise ValueError('Stage send identity drift')
        self.sent.append(dict(typeIndex=61311,object18=e.read(p+0x18,'I'),object1c=e.read(p+0x1c,'I')));return 0

    def append(self):
        e=self.e;p=e.reg('rcx');value=e.reg('edx');self.lists[p].append(value)
        a=e.alloc(64);e.write(a+0x18,8,'I');e.write(a+0x20,value,'I');e.write(p+0x10,a);e.write(p+0x18,1,'I');return 0

    def insert(self):
        e=self.e;dest=e.reg('rcx');source=e.reg('r8');values=self.lists[source]
        if dest!=self.pref_list or e.reg('edx')!=0:raise ValueError('Local uint32 list destination drift')
        self.lists[dest][:]=values;e.write(dest+0x18,len(values),'I');return 0

    def action(self,p):
        e=self.e
        if e.read(p+0x10)!=e.read(p+0x18) or e.read(p+0x20)!=e.read(p+0x40):raise ValueError('Action layout drift')
        return dict(target=hex(e.read(p+0x18)),receiver=e.read(p+0x40),methodInfo=e.read(p+0x28))

    def run(self,entry,stops,receiver=None):
        e=self.e
        return fragment(e,entry,set(stops),dict(rsp=STACK+0x100008,rcx=self.owner if receiver is None else receiver,rdx=OBJECT))


def wire_probes(e,protoc_path):
    rows=[];e.basic_writers()
    schema='syntax="proto3"; message StageFields { uint32 field_1c = 11; uint32 field_18 = 14; }'
    with tempfile.TemporaryDirectory(prefix='born72-') as folder:
        proto=Path(folder)/'stage.proto';proto.write_text(schema,encoding='utf8')
        for a,b in ((0,0),(1,16),(0xffffffff,0xffffffff),(128,127),(0x80000000,0x10000000)):
            e.prepare();e.write(OBJECT+24,a,'I');e.write(OBJECT+28,b,'I')
            native=e.run(0x150976540);expected=(b'\x58'+varint(b) if b else b'')+(b'\x70'+varint(a) if a else b'')
            compiled=subprocess.run([str(protoc_path),f'--proto_path={folder}','--encode=StageFields','stage.proto'],
                input=f'field_18: {a}\nfield_1c: {b}'.encode(),capture_output=True)
            if compiled.returncode or native!=expected or parse_wire(native)!=parse_wire(compiled.stdout):raise ValueError('Stage native/independent wire mismatch')
            e.run(0x1509767b0)
            if e.reg('eax')!=len(native):raise ValueError('Stage native size mismatch')
            rows.append(dict(kind='stage-wire',object18=a,object1c=b,wire=native.hex(),size=len(native),protocMatch=True))
        e.prepare();e.run(0x150976530)
        if e.reg('ax')!=20353:raise ValueError('Stage native getter mismatch')
        e.run(0x1523856a0)
        if e.reg('ax')!=27748:raise ValueError('Deferred scene object native getter mismatch')
    return rows


def recover(b,m,s,records,libraries,protoc_path):
    decl=declarations(b,m,s,records);fixtures=[];e=Offline72(b,libraries,records,True)
    try:
        fixtures.extend(wire_probes(e,protoc_path))
        # The response reader and Handler are native. Media-disabled is a real
        # branch input, not a success model; the root may return without playback.
        cases=((0,1,True,False,True,'none'),(0,0,True,False,True,'none'),
               (0,1,False,False,True,'none'),(0,1,True,False,False,'none'),
               (0,1,True,False,True,'wrong'),(0,1,True,True,True,'none'),
               (0,0,True,True,True,'none'),(0,1,True,True,True,'match'),
               (116,1,True,False,True,'none'),(132,1,True,False,True,'none'),(-1,1,True,False,True,'none'))
        for ret,flag18,born,media,stage,page in cases:
            e.prepare();h=Continuation72(e,decl,flag18,born,media,stage,page)
            for cell in (0x145899180,0x145899610):e.synthetic_type(cell)
            rd=Reader72(e);reader=rd.run(34385,response_wire(ret),0x15374ac20);rd.close()
            trace=h.run(0x1507d2820,{0x1507d28ff,0x15347b9ae,MEDIA_LOOKUP,0x15245b6b0,REJECT})
            # Handler extent contains multiple returns: use the actual return
            # caller sentinel via a reviewed stop instruction, never assume END.
            row=dict(kind='response-continuation',retcode=ret,bornFlag18=flag18,bornPresent=born,mediaEnabled=media,
                     stageContextPresent=stage,page=page,reader=reader,trace=trace,
                     sends=h.sent,events=h.events,bornFlags=[e.read(h.born+i,'B') for i in (0x19,0x1a)],
                     localFlag=e.read(h.data+0x3d,'B'),localList=h.lists[h.pref_list])
            if trace['stop']==hex(MEDIA_LOOKUP):
                row['mediaKey']=e.reg('ecx')
                actions=[h.action(p) for p in h.actions]
                if [a['target'] for a in actions]!=['0x14b68dcb0','0x153d70f80','0x153d71010']:raise ValueError('Movie callback targets drift')
                closure=actions[1]['receiver']
                if actions[2]['receiver']!=closure or e.read(closure+0x18)!=h.owner or e.read(closure+0x10)!=e.reg('r15'):raise ValueError('Movie closure dataflow drift')
                row['actionTargets']=[a['target'] for a in actions]
                row['closureOwnerAndMoviePreserved']=True
                row['movie']=bytes(e.uc.mem_read(e.reg('r15')+20,e.read(e.reg('r15')+16,'I')*2)).decode('utf-16-le')
            row['stop']=trace['stop']
            fixtures.append(row)
        # Isolated suffix after the native ClosePage/helper return. This proves
        # timer arguments, without fabricating UI completion or firing a timer.
        for view_present,handle_present,duration in ((False,False,2.5),(True,False,2.5),(True,True,0.0),(True,True,2.5)):
            e.prepare();h=Continuation72(e,decl);view=e.alloc();timer=e.alloc();e.write(GLOBAL+0x9e90,timer)
            e.write(h.page+0x240,view if view_present else 0);e.write(view+0x10,e.alloc() if handle_present else 0);e.write(view+0x2c,duration,'f')
            trace=fragment(e,0x14cd6899d,{0x14ee38f40},dict(rsp=STACK+0x100008-0x48,rsi=h.owner,rbx=h.page))
            action=h.action(e.reg('r8'));actual=struct.unpack('<f',(e.reg('xmm1')&0xffffffff).to_bytes(4,'little'))[0]
            if e.reg('rcx')!=timer or e.reg('r9b')!=1 or action['target']!='0x14cd62270' or action['receiver']!=h.owner:raise ValueError('Timer dataflow drift')
            fixtures.append(dict(kind='timer-registration-suffix',viewPresent=view_present,handlePresent=handle_present,
                                 inputDuration=duration,duration=actual,callback=action['target'],receiverPreserved=True,
                                 trace=trace,stop=trace['stop'],timerExecuted=False))
        # Independent callback invocation does not assert that the real media
        # player dispatches it, nor its order relative to incoming messages.
        for callback in (0x153d70f80,0x153d71010):
            for missing in ('none','born','owner'):
                e.prepare();h=Continuation72(e,decl);closure=e.alloc();e.write(closure+0x18,h.owner if missing!='owner' else 0)
                e.write(h.born+0x1a,1,'B')
                if missing=='born':e.write(h.owner+0x48,0)
                e.allow(callback)
                trace=h.run(callback,{0x14cd67260,REJECT},closure)
                fixtures.append(dict(kind='movie-callback-prefix',callback=hex(callback),missing=missing,trace=trace,
                    stop=trace['stop'],
                    bornFlags=[e.read(h.born+i,'B') for i in (0x19,0x1a)],uiState=e.read(h.ui+0x33c,'I'),
                    scenePointerPreserved=e.reg('rdx')==h.scene if trace['stop']=='0x14cd67260' else False))
        for callback in (0x153d70f80,0x153d71010):
            for scene_present in (True,False):
                e.prepare();h=Continuation72(e,decl);closure=e.alloc();e.write(closure+0x18,h.owner)
                e.write(h.born+0x1a,1,'B')
                e.write(h.born+0x10,h.scene if scene_present else 0);e.synthetic_type(0x1458ad418)
                stages=[]
                if not scene_present:
                    e.models[0x14c8ae9a0]=lambda:stages.append(e.reg('ecx')) or 0
                    e.models[0x1531fdb80]=lambda:0
                trace=h.run(callback,{0x14c8ae9a0,REJECT} if scene_present else {REJECT},closure)
                fixtures.append(dict(kind='callback-scene-boundary',callback=hex(callback),scenePresent=scene_present,
                    stop=trace['stop'],trace=trace,stageValue=e.reg('ecx') if scene_present else stages[0],
                    stageBodyExecuted=False,bornFlags=[e.read(h.born+i,'B') for i in (0x19,0x1a)]))
                e.models.pop(0x14c8ae9a0,None);e.models.pop(0x1531fdb80,None)
        # A missing subtitle/config lookup is an explicit EMPTY resource table,
        # never a fabricated positive lookup. The real mapper returns null, and
        # native media task construction still retains all three Actions.
        for flag18 in (0,1):
            e.prepare();h=Continuation72(e,decl,flag18=flag18,media=True);queue=e.alloc();queued=[];lookups=[]
            e.write(h.media+0x48,queue)
            for cell in (0x145899180,0x145899610,0x1458a74f8):e.synthetic_type(cell)
            for cell in (0x145b94c37,0x145b845aa):e.write(cell,1,'B')
            for entry in (MEDIA_LOOKUP,0x1523f1350,0x1503536b0):e.allow(entry)
            e.models[0x1517355d0]=lambda:lookups.append(e.reg('ecx')) or 0
            def enqueue():
                if e.reg('rcx')!=queue:raise ValueError('Media queue receiver drift')
                queued.append(e.reg('rdx'));return 0
            e.models[0x1567aff90]=enqueue
            trace=h.run(ROOT,{0x15347b9ae,REJECT})
            if len(queued)!=1:raise ValueError('Native media task was not queued once')
            task=queued[0];movie_pointer=e.read(task+0x30)
            movie=bytes(e.uc.mem_read(movie_pointer+20,e.read(movie_pointer+16,'I')*2)).decode('utf-16-le')
            targets={hex(off):h.action(e.read(task+off))['target'] for off in (0x38,0x50,0x58)}
            if [e.read(task+off) for off in (0x58,0x50,0x38)]!=h.actions:raise ValueError('Media task did not preserve original Actions')
            fixtures.append(dict(kind='empty-media-config-queue',bornFlag18=flag18,trace=trace,stop=trace['stop'],lookups=lookups,
                movie=movie,subtitlePointerIsNull=e.read(task+0x28)==0,actionTargetsByOffset=targets,
                originalActionsPreserved=True,localFlag=e.read(h.data+0x3d,'B'),localList=h.lists[h.pref_list],playbackExecuted=False))
            e.models.pop(0x1517355d0);e.models.pop(0x1567aff90)
        # The enabled IFix path is observed at its wrapper and never forced to
        # return success. This is separate from the fixed-image absent fixtures.
        e.prepare();h=Continuation72(e,decl);patch_table=GLOBAL+0x300000
        e.write(GLOBAL+0x232a0,patch_table);e.write(patch_table+0x116f18,e.alloc());e.write(0x1457d952f,1,'B')
        trace=h.run(ROOT,{IFIX,REJECT});e.write(0x1457d952f,0,'B')
        fixtures.append(dict(kind='ifix-boundary',trace=trace,stop=trace['stop'],sends=h.sent,executed=False))
        validate_fixtures(fixtures)
        return dict(schemaVersion=1,inputSHA256=HASHES,declarations=decl,fixtures=fixtures,
                    fixtureCount=len(fixtures),runtimeStatus='not-tested',scope='bounded-native-born-continuation',
                    models=['managed allocation, initialized metadata/classes and IFix-absent inputs',
                            'wire varint/tag primitives and independent protoc oracle',
                            'UI/resource/error/log observation sinks; no success predicate',
                            'managed dictionary and uint32 list storage',
                            'send-entry observation; queue, socket and SDK not executed'],
                    outsideScope=['actual timer/media playback and callback scheduling','actual IFix/SDK state',
                                  'complete scene object schema and received initialization order','D-02 live acceptance'])
    finally:e.close()


def validate_fixtures(fixtures):
    """Assert native branch effects, especially branches that are not success."""
    expected={'stage-wire':5,'response-continuation':11,'timer-registration-suffix':4,
              'movie-callback-prefix':6,'callback-scene-boundary':4,'empty-media-config-queue':2,'ifix-boundary':1}
    if dict(Counter(r['kind'] for r in fixtures))!=expected:raise ValueError('Incomplete continuation fixture coverage')
    for row in fixtures:
        kind=row['kind']
        if kind=='response-continuation':
            ret=row['retcode'];direct=not ret and row['page']!='match';waiting=direct and row['mediaEnabled'] and row['bornPresent']
            stop='0x1507d28ff' if ret else ('0x15245b6b0' if row['page']=='match' else (hex(MEDIA_LOOKUP) if waiting else '0x15347b9ae'))
            sent=[dict(typeIndex=61311,object18=1,object1c=16)] if not ret and row['stageContextPresent'] else []
            if row['stop']!=stop or row['sends']!=sent:raise ValueError('Response branch/send mismatch')
            finished=direct and not waiting
            if row['localFlag']!=int(finished) or row['localList']!=([351] if finished else []):raise ValueError('Synchronous suffix state mismatch')
            if row['bornFlags']!=[1,int(direct and row['bornPresent'])]:raise ValueError('Born task state mismatch')
            if ret:
                events=[dict(name='response-error',value=ret&0xffffffff)]+([dict(name='error-event',value=850)] if ret==132 else [])
                if row['events']!=events:raise ValueError('Error path side effects mismatch')
            else:
                events=[] if not direct else ([dict(name='missing-born-log',value=None)] if not row['bornPresent'] else
                    [dict(name='ui-prepare',value=None),dict(name='ui-mode',value=3),dict(name='media-resource-begin',value=None)])
                if row['events']!=events:raise ValueError('Continuation observation boundary mismatch')
            if waiting:
                suffix='Boy' if row['bornFlag18'] else 'Girl'
                if row['mediaKey']!=300010+(not row['bornFlag18']) or row['movie']!=f'MDAQ001_OPNew_Part2_Player{suffix}.usm':raise ValueError('Movie branch mismatch')
        elif kind=='movie-callback-prefix':
            present=row['missing']=='none'
            if row['stop']!=('0x14cd67260' if present else hex(REJECT)) or row['scenePointerPreserved']!=present:raise ValueError('Callback receiver boundary mismatch')
            if row['bornFlags']!=([0,0] if present else [1,1]) or row['uiState']!=(3 if present else 0):raise ValueError('Callback effects mismatch')
        elif kind=='timer-registration-suffix':
            expected=row['inputDuration'] if row['viewPresent'] and row['handlePresent'] else 0.0
            if row['duration']!=expected or row['timerExecuted'] or row['stop']!='0x14ee38f40':raise ValueError('Timer boundary mismatch')
        elif kind=='callback-scene-boundary':
            if row['stop']!=('0x14c8ae9a0' if row['scenePresent'] else hex(REJECT)) or row['stageValue']!=10 or row['stageBodyExecuted'] or row['bornFlags']!=[0,0]:raise ValueError('Scene prerequisite boundary mismatch')
        elif kind=='ifix-boundary':
            if row['stop']!=hex(IFIX) or row['executed'] or row['sends']:raise ValueError('IFix boundary mismatch')
        elif kind=='empty-media-config-queue':
            expected_targets={'0x38':'0x153d71010','0x50':'0x153d70f80','0x58':'0x14b68dcb0'}
            if row['stop']!='0x15347b9ae' or row['lookups']!=[300010+(not row['bornFlag18'])] or not row['subtitlePointerIsNull'] or row['actionTargetsByOffset']!=expected_targets:raise ValueError('Media queue native dataflow mismatch')
            if row['localFlag']!=1 or row['localList']!=[351] or row['playbackExecuted'] or not row['originalActionsPreserved']:raise ValueError('Media queue completion overclaim')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--protoc',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--summary-output',type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for out in (a.output,a.summary_output):
        if out.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.protoc)
    report['protocSHA256']=hashlib.sha256(a.protoc.read_bytes()).hexdigest()
    summary={k:v for k,v in report.items() if k!='fixtures'}
    summary['fixtureKinds']=dict(Counter(f['kind'] for f in report['fixtures']))
    summary['observations']=[{k:v for k,v in f.items() if k not in ('trace','reader')} for f in report['fixtures']]
    summary['nativeEvidence']=[{k:f['trace'][k] for k in ('entry','stop','instructions','nativePathSHA256','models')}
                               for f in report['fixtures'] if 'trace' in f]
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],runtimeStatus=report['runtimeStatus'])))


if __name__=='__main__':main()
