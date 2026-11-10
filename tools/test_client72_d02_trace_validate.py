"""Synthetic-only fixture tests; no fixture is a native result or approval."""
import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools import client72_d02_trace_validate as v


def fixture(case, late_dlls=0, dll_before_last_step=False):
    epoch = dict(zip(v.EPOCH_KEYS,[0x1000,0x2010,0x2011,0x2014,0x2019,0x2100,0x2200,0x2300,0x2000]))
    epoch.update(childExeSHA256='11'*32,bootstrapSHA256='22'*32,sharedHeaderSHA256=v.HEADER,imageBytes='0000000000004000')
    pid,tid,base,stack = 200,201,0x100000,0x300000
    leaf = base+epoch[{1:'returnLeafRva',2:'ymmLeafRva',3:'faultLeafRva'}[case]]
    after = base+epoch['afterCallRva']; blocks = [0x400000+i*96 for i in range(3)]
    run = 'ab'*16
    rows=[]; count={k:0 for k in ('wait','get','set','continue','memory')}; current=[None,None,None]; stop=0
    def emit(kind, **kw):
        row={'kind':kind,'seq':len(rows)+1,'phase':'SYNTHETIC','eventPID':current[0],'eventTID':current[1],
             'eventType':current[2],'monotonicElapsedUs':len(rows)*1000}; row.update(kw); rows.append(row); return row
    def event(code, **kw):
        nonlocal stop
        current[:] = [None,None,None]; count['wait']+=1
        emit('wait',apiOrdinal=count['wait'],BOOL=True,lastErrorIfFalse=None,timeoutMs=50,stopEventSeq=len(rows)+2)
        current[:] = [pid,tid,code]
        rows[-1].update(eventPID=pid,eventTID=tid,eventType=code)
        defaults=dict(rawDebugEventCode=code,firstChance=None,exceptionCode=None,exceptionAddress=None,exceptionFlags=None,
                      exceptionParameters=None,exceptionParameterCount=None,continueStatus=None,ContinueDebugEventBOOL=None,continueRecordStatus='separate')
        defaults.update(kw); e=emit('event',**defaults); stop=e['seq']; return e
    def exception(code, address):
        return event(1,firstChance=True,exceptionCode=code,exceptionAddress=f'{address:016x}',exceptionFlags='00000000',exceptionParameters=[],exceptionParameterCount=0)
    def ctx(pc,rsp,flags=0,rax=0):
        g={k:'0000000000000000' for k in v.GPRS}; g.update(R11=f'{leaf:016x}',Rax=f'{rax:016x}')
        if case == 2: g.update(Rcx=f'{blocks[0]+32:016x}',Rdx=f'{blocks[1]+32:016x}',R8=f'{blocks[2]+32:016x}')
        return {'SegCs':51,'SegSs':43,'EFlags':f'{flags:08x}','Rsp':f'{rsp:016x}','Rip':f'{pc:016x}'},g
    def get(c,g):
        count['get']+=1
        return emit('get',apiOrdinal=count['get'],requestedGroups=['CONTROL','INTEGER'],BOOL=True,lastErrorIfFalse=None,
                    threadHandleOrigin='CreateProcess.PI.hThread',stopEventSeq=stop,control=copy.deepcopy(c),integer=copy.deepcopy(g))
    def set_tf(c,g,on):
        before=copy.deepcopy(c); submitted=copy.deepcopy(c)
        submitted['EFlags']=f'{(int(c["EFlags"],16)&~0x100)|(0x100 if on else 0):08x}'
        get(submitted,g); count['set']+=1
        row=emit('set',apiOrdinal=count['set'],stopEventSeq=stop,requestedGroups=['CONTROL'],beforeControl=before,
                 submittedControl=submitted,BOOL=True,lastErrorIfFalse=None,readbackGetOrdinal=count['get'],
                 readbackControl=copy.deepcopy(submitted),integerUnchanged=True,
                 eflagsReadbackPolicy=v.EFLAGS_READBACK_POLICY,eflagsReadbackXor='00000000',eflagsReadbackRepresentation='exact')
        return row
    def cont(clean=False):
        count['continue']+=1
        return emit('continue',apiOrdinal=count['continue'],stopEventSeq=stop,continueStatus='00010002',BOOL=True,lastErrorIfFalse=None,cleanupOnly=clean)
    def memory(purpose,addr,data):
        count['memory']+=1
        begin,end = (base,base+0x4000) if purpose in ('descriptor','sourceCode') else (addr,addr+len(data))
        return emit('memory',apiOrdinal=count['memory'],stopEventSeq=stop,purpose=purpose,address=f'{addr:016x}',width=len(data),
                    allowedRegion={'begin':f'{begin:016x}','endExclusive':f'{end:016x}'},ReadProcessMemoryBOOL=True,
                    lastErrorIfFalse=None,actualBytesRead=len(data),rawHex=data.hex())
    def cleanup(api,origin,ok=True,err=None):
        return emit('cleanup',api=api,handleOrigin=origin,BOOL=ok,lastErrorIfFalse=err)
    emit('epoch',**epoch)
    cleanup('CreateProcessW','newOwnedChild')
    emit('ownedChild',pid=pid,tid=tid,processHandleOrigin='CreateProcess.PI.hProcess',threadHandleOrigin='CreateProcess.PI.hThread',
         GetProcessTimesBOOL=True,lastErrorIfFalse=None,creationFileTime='0000000000000020')
    cleanup('DebugSetProcessKillOnExit','debuggerThread')
    event(3,moduleBase=f'{base:016x}',moduleImageBytes=epoch['imageBytes'],fileSHA256=epoch['childExeSHA256'],fileHandleValid=True,
          fileIdentityBOOL=True,fileCloseBOOL=True,debugProcessHandleOrigin='CREATE_PROCESS.borrowed',debugThreadHandleOrigin='CREATE_PROCESS.borrowed'); cont()
    nt=0x700000
    event(6,moduleBase=f'{nt:016x}',moduleImageBytes='0000000000010000',ntdllNameMatch=True,fileHandleValid=True,fileIdentityBOOL=True,fileCloseBOOL=True); cont()
    exception('80000003',nt+10); c,g=ctx(nt+11,stack); get(c,g)
    emit('calibration',name='systemBreakpoint',stopEventSeq=stop,exceptionAddress=f'{nt+10:016x}',contextRip=c['Rip'],moduleBase=f'{nt:016x}',exceptionRva='000000000000000a'); cont()
    exception('80000003',base+epoch['armRva']); c,g=ctx(base+epoch['callRva'],stack); get(c,g)
    emit('calibration',name='ownedInt3',stopEventSeq=stop,exceptionAddress=f'{base+epoch["armRva"]:016x}',contextRip=c['Rip'],moduleBase=f'{base:016x}',exceptionRva=f'{epoch["armRva"]:016x}')
    d=bytearray(256); d[:8]=b'C48TRC1\0'; struct.pack_into('<4I',d,8,1,256,case,1); d[24:40]=bytes.fromhex(run)
    struct.pack_into('<Q2I5I',d,40,base,pid,tid,epoch['armRva'],epoch['callRva'],epoch['afterCallRva'],leaf-base,len(v.LEAVES[case]))
    struct.pack_into('<2Q',d,104,stack-0x1000,stack+0x1000)
    initial=b''.join(bytes([a])*32+p+bytes([b])*32 for a,p,b in ((0xa1,bytes(range(1,33)),0xa2),(0xb1,bytes([0xcc])*32,0xb2),(0xc1,bytes([0xdd])*32,0xc2)))
    final=bytearray(initial); final[128:160]=bytes(range(1,33)); final[224:256]=bytes(range(1,17))*2
    if case==2:
        struct.pack_into('<I3Q',d,76,3,*blocks); struct.pack_into('<4Q',d,120,blocks[0],65536,blocks[0],4096)
        struct.pack_into('<3I',d,152,1,2,1); struct.pack_into('<Q',d,168,6); struct.pack_into('<I',d,176,1); struct.pack_into('<I',d,200,0x1c000000)
    memory('descriptor',base+epoch['descriptorRva'],bytes(d)); memory('sourceCode',base+epoch['wrapperBeginRva'],v.WRAPPER); memory('sourceCode',leaf,v.LEAVES[case])
    if case==2: memory('ownedBufferInitial',blocks[0],initial)
    offsets={1:[(0,10),(10,1)],2:[(0,4),(4,4),(8,6),(14,5),(19,3),(22,1)],3:[(0,2)]}[case]
    pcs=[(base+epoch['callRva'],v.WRAPPER[17:20])]+[(leaf+o,v.LEAVES[case][o:o+n]) for o,n in offsets]
    for i,(pc,code) in enumerate(pcs):
        pre_stop=stop; pre_c,pre_g=copy.deepcopy(c),copy.deepcopy(g); memory('sourceCode',pc,code)
        op=None
        if i==0: op=('write',stack-8,8,'CALLslotOld','CALLslotNew',b'\x77'*8,after.to_bytes(8,'little'))
        elif case!=3 and i==len(pcs)-1: op=('read',stack-8,8,'RETslot',None,after.to_bytes(8,'little'),None)
        elif case==2 and i in (1,2,4):
            b={1:0,2:1,4:2}[i]; old=(bytes(range(1,33)),bytes([0xcc])*32,bytes([0xdd])*32)[b]
            new=(None,bytes(range(1,33)),bytes(range(1,17))*2)[b]
            op=('read' if i==1 else 'write',blocks[b]+32,32,'operandRead' if i==1 else 'writeOld',None if i==1 else 'writeNew',old,new)
        ops=[]
        if op:
            direction,ea,width,purpose,postpurpose,old,new=op; m=memory(purpose,ea,old)
            ops=[dict(direction=direction,EA=f'{ea:016x}',width=width,allowedSpan={'begin':f'{ea:016x}','endExclusive':f'{ea+width:016x}'},
                      preReadOrdinal=m['apiOrdinal'],preHex=old.hex(),postReadOrdinal=None,postHex=None)]
        s=set_tf(c,g,True); co=cont(); fault=case==3 and i==1
        nextpc=pcs[i+1][0] if i+1<len(pcs) else after
        if dll_before_last_step and i==len(pcs)-1:
            event(6,moduleBase='0000000000800000',moduleImageBytes='0000000000010000',
                  ntdllNameMatch=False,fileHandleValid=True,fileIdentityBOOL=True,fileCloseBOOL=True); cont()
        exception('c000001d' if fault else '80000004',leaf if fault else nextpc)
        rsp=stack if not fault and i==len(pcs)-1 else stack-8
        rax=0xf123456789abcdef if case==1 and i>=1 else 0
        c,g=ctx(leaf if fault else nextpc,rsp,0x100,rax); get(c,g)
        if not fault:
            emit('calibration',name='singleStep',stopEventSeq=stop,exceptionAddress=f'{nextpc:016x}',contextRip=c['Rip'],moduleBase=f'{base:016x}',exceptionRva=f'{nextpc-base:016x}')
        if op and postpurpose:
            m=memory(postpurpose,ea,new); ops[0].update(postReadOrdinal=m['apiOrdinal'],postHex=new.hex())
        emit('transaction',txId=i+1,instructionOrigin='wrapper' if i==0 else 'leaf',preEventSeq=pre_stop,preRip=f'{pc:016x}',instructionBytes=code.hex(),
             preGPR=pre_g,preEFlags=pre_c['EFlags'],ops=ops,expectedNextPCs=[] if fault else [f'{nextpc:016x}'],tfSetOrdinal=s['apiOrdinal'],
             continueOrdinal=co['apiOrdinal'],postEventSeq=stop,postRip=c['Rip'],postGPR=copy.deepcopy(g),postEFlags=c['EFlags'],retired=not fault,status='unretired' if fault else 'retired')
    fault_stop=stop
    if case==2: memory('ownedBufferFinal',blocks[0],bytes(final))
    if case!=3: set_tf(c,g,False); cont()
    else: cleanup('TerminateProcess','CreateProcess.PI.hProcess'); cont(True)
    for i in range(late_dlls):
        event(6,moduleBase=f'{0x800000+i*0x10000:016x}',moduleImageBytes='0000000000010000',
              ntdllNameMatch=False,fileHandleValid=True,fileIdentityBOOL=True,fileCloseBOOL=True,
              filePath='synthetic.dll',fileIdentity={'volumeSerial':1,'indexHigh':0,'indexLow':1}); cont()
    event(5,exitCode=90 if case==3 else 0); cont(case==3)
    emit('cleanup',api='WaitForSingleObject',handleOrigin='CreateProcess.PI.hProcess',timeoutMs=1,result=0,lastErrorIfFailed=None)
    cleanup('GetExitCodeProcess','CreateProcess.PI.hProcess'); cleanup('CloseHandle','CreateProcess.PI.hThread'); cleanup('CloseHandle','CreateProcess.PI.hProcess')
    cleanup('GetFileAttributesW','childResult',case!=3,2 if case==3 else None)
    counts={'events':len([r for r in rows if r['kind']=='event']),'waits':count['wait'],'gets':count['get'],'sets':count['set'],
            'reads':count['memory'],'readRequestedBytes':sum(r['width'] for r in rows if r['kind']=='memory'),
            'continues':count['continue'],'retired':sum(r['retired'] for r in rows if r['kind']=='transaction')}
    summary=dict(schema='c48-parent-result-v1',caseId=case,runId=run,pid=pid,tid=tid,sourceProof=epoch,budgets=counts,
                 normalTracePassed=case!=3,firstFailureKind='expectedUD2' if case==3 else None,pendingTxId=2 if case==3 else None,
                 pendingRetired=False if case==3 else None,lastCompletedTx=1 if case==3 else len(pcs),TerminateProcessBOOL=True if case==3 else None,
                 terminationRequestedExit=90 if case==3 else None,cleanupContinues=2 if case==3 else 0,childExitCode=90 if case==3 else 0,
                 stopEventSeq=fault_stop if case==3 else None,rawCode='c000001d' if case==3 else None,
                 exceptionAddress=f'{leaf:016x}' if case==3 else None,contextRip=f'{leaf:016x}' if case==3 else None)
    summary.update({k:True for k in ('epochValidated','EXIT_PROCESSobserved','EXIT_PROCESScontinued','ownedProcessSignaled','GetExitCodeProcessBOOL','killOnDebuggerExitAccepted','cleanupComplete','PIThreadCloseBOOL','PIProcessCloseBOOL')})
    child=None if case==3 else dict(schema='c48-child-result-v1',caseId=case,runId=run,pid=pid,tid=tid,imageBase=f'{base:016x}',continuedToC=True,exit=0,
              cpuidCount=2 if case==2 else 0,xgetbvCount=1 if case==2 else 0,xcr0=f'{6 if case==2 else 0:016x}',
              cpuidLeaf0=[1,0,0,0] if case==2 else [0]*4,cpuidLeaf1=[0,0,0x1c000000,0] if case==2 else [0]*4,
              blocksBefore=initial.hex() if case==2 else '',blocksAfter=bytes(final).hex() if case==2 else '',returnValue='f123456789abcdef' if case==1 else '0000000000000000')
    receipt=dict(caseId=case,runId=run,parentPid=199,parentCreationFileTime='0000000000000010',parentExit=30 if case==3 else 0)
    return rows,summary,child,receipt,epoch


def check(data):
    rows,summary,child,receipt,epoch=data
    records,events=v.check_records(rows,summary,receipt,epoch)
    v.check_witness(receipt['caseId'],receipt['runId'],records,events,summary,child,epoch,receipt)


class TraceTests(unittest.TestCase):
    def test_successful_wait_requires_returned_event_identity(self):
        # Actual main4 emitted null here despite a successful Wait and real event.
        for case in (1, 2, 3):
            for key in ('eventPID', 'eventTID', 'eventType'):
                with self.subTest(case=case, key=key):
                    data = fixture(case)
                    wait = next(r for r in data[0] if r['kind'] == 'wait')
                    wait[key] = None
                    failure = 'wait identity' if key == 'eventType' else 'event PID/TID'
                    with self.assertRaisesRegex(ValueError, failure):
                        check(data)

    def test_synthetic_three_cases(self):
        for case in (1,2,3):
            with self.subTest(case=case): check(fixture(case))

    def test_synthetic_late_dll_after_tf_restore(self):
        for case in (1,2):
            for late_dlls in (1,2):
                with self.subTest(case=case,late_dlls=late_dlls):
                    check(fixture(case,late_dlls))

    def test_synthetic_late_dll_rejections(self):
        def late(data): return [r for r in data[0] if r['kind']=='event' and r['rawDebugEventCode']==6][-1]
        def late_continue(data):
            e=late(data)
            return next(r for r in data[0] if r['kind']=='continue' and r['stopEventSeq']==e['seq'])
        def set_code(data, code):
            e=late(data)
            w=next(r for r in data[0] if r['kind']=='wait' and r['stopEventSeq']==e['seq'])
            c=late_continue(data)
            w['eventType']=e['eventType']=e['rawDebugEventCode']=c['eventType']=code
            if code == 1:
                e.update(firstChance=True,exceptionCode='80000004',exceptionAddress='0000000000102019',
                         exceptionFlags='00000000',exceptionParameters=[],exceptionParameterCount=0)
        def leave_tf_set(data):
            restore=[r for r in data[0] if r['kind']=='set'][-1]
            restore['submittedControl']['EFlags']='00000100'
            restore['readbackControl']['EFlags']='00000100'
            rb=next(r for r in data[0] if r['kind']=='get' and r['apiOrdinal']==restore['readbackGetOrdinal'])
            rb['control']['EFlags']='00000100'
        changes=[
            lambda d: late(d).update(fileIdentityBOOL=False),
            lambda d: late(d).pop('fileIdentityBOOL'),
            lambda d: late(d).update(fileCloseBOOL=False),
            lambda d: late(d).pop('fileCloseBOOL'),
            lambda d: late(d).update(eventPID=999),
            lambda d: late(d).update(eventTID=999),
            lambda d: late_continue(d).update(BOOL=False,lastErrorIfFalse=5),
            lambda d: late_continue(d).update(continueStatus='80010001'),
            lambda d: late_continue(d).update(cleanupOnly=True),
            lambda d: set_code(d,7),
            lambda d: set_code(d,1),
            lambda d: [r for r in d[0] if r['kind']=='transaction'][-1].update(retired=False,status='unretired'),
            leave_tf_set,
        ]
        for case in (1,2):
            for change in changes:
                with self.subTest(case=case,change=change):
                    data=fixture(case,1); change(data)
                    with self.assertRaises((ValueError,KeyError,IndexError)): check(data)
            with self.subTest(case=case,budget='128'):
                base_events=fixture(case)[1]['budgets']['events']
                check(fixture(case,128-base_events))
                with self.assertRaisesRegex(ValueError,'budget'):
                    check(fixture(case,129-base_events))
            with self.subTest(case=case,dll_before_last_step=True):
                with self.assertRaisesRegex(ValueError,'debug event sequence'):
                    check(fixture(case,dll_before_last_step=True))
        with self.assertRaisesRegex(ValueError,'debug event sequence'):
            check(fixture(3,1))

    def test_synthetic_eflags_manual_truth_table(self):
        # Fixed expected values are authored independently of the validator.
        cases = [
            (0, 0x202, 0x302, 0x302, '00000000', 'exact', True),
            (0, 0x202, 0x302, 0x300, '00000002', 'reserved_bit1_1_to_0', True),
            (-1, 0x302, 0x202, 0x202, '00000000', 'exact', True),
            (-1, 0x302, 0x202, 0x200, '00000002', 'reserved_bit1_1_to_0', True),
            (0, 0x200, 0x300, 0x302, '00000002', 'rejected', False),
            (0, 0x202, 0x300, 0x300, '00000000', 'exact', False),
            (0, 0x202, 0x302, 0x202, '00000100', 'rejected', False),
            (0, 0x202, 0x302, 0x200, '00000102', 'rejected', False),
            (0, 0x202, 0x302, 0x301, '00000003', 'rejected', False),
            (0, 0x202, 0x302, 0x100, '00000202', 'rejected', False),
            (0, 0x202, 0x302, 0x308, '0000000a', 'rejected', False),
        ]
        # Every bit other than bit1 is independently flipped in raw readback.
        cases += [(0, 0x202, 0x302, 0x302 ^ (1 << bit),
                   f'{1 << bit:08x}', 'rejected', False)
                  for bit in range(32) if bit != 1]
        for index, before, submitted, readback, xor, representation, accepted in cases:
            with self.subTest(index=index, before=before, submitted=submitted, readback=readback):
                data=fixture(1); rows,summary,child,receipt,epoch=data
                sets=[r for r in rows if r['kind']=='set']; target=sets[index]
                gets=[r for r in rows if r['kind']=='get']
                rb=next(g for g in gets if g['apiOrdinal']==target['readbackGetOrdinal'])
                pre=[g for g in gets if g['stopEventSeq']==target['stopEventSeq'] and g['seq']<rb['seq']][-1]
                pre['control']['EFlags']=target['beforeControl']['EFlags']=f'{before:08x}'
                target['submittedControl']['EFlags']=f'{submitted:08x}'
                rb['control']['EFlags']=target['readbackControl']['EFlags']=f'{readback:08x}'
                target.update(eflagsReadbackXor=xor, eflagsReadbackRepresentation=representation)
                if accepted:
                    v.check_records(rows,summary,receipt,epoch)
                else:
                    with self.assertRaises((ValueError,KeyError,IndexError)):
                        v.check_records(rows,summary,receipt,epoch)

    def test_synthetic_reserved_bit1_full_witness(self):
        data=fixture(1); rows=data[0]
        target=next(r for r in rows if r['kind']=='set')
        rb=next(r for r in rows if r['kind']=='get' and r['apiOrdinal']==target['readbackGetOrdinal'])
        pre=[r for r in rows if r['kind']=='get' and r['stopEventSeq']==target['stopEventSeq'] and r['seq']<rb['seq']][-1]
        pre['control']['EFlags']=target['beforeControl']['EFlags']='00000202'
        target['submittedControl']['EFlags']='00000302'
        rb['control']['EFlags']=target['readbackControl']['EFlags']='00000300'
        target.update(eflagsReadbackXor='00000002',eflagsReadbackRepresentation='reserved_bit1_1_to_0')
        next(r for r in rows if r['kind']=='transaction')['preEFlags']='00000202'
        check(data)

    def test_synthetic_eflags_negative_associations_and_audit(self):
        def set_row(d): return next(r for r in d[0] if r['kind']=='set')
        def readback(d):
            s=set_row(d)
            return next(r for r in d[0] if r['kind']=='get' and r['apiOrdinal']==s['readbackGetOrdinal'])
        def alter_readback_control(d, field, value):
            readback(d)['control'][field]=value
            set_row(d)['readbackControl'][field]=value
        changes = [
            lambda d: set_row(d).update(eflagsReadbackPolicy='old'),
            lambda d: set_row(d).update(eflagsReadbackXor='00000002'),
            lambda d: set_row(d).update(eflagsReadbackXor='0000000A'),
            lambda d: set_row(d).update(eflagsReadbackRepresentation='reserved_bit1_1_to_0'),
            lambda d: set_row(d).update(eflagsReadbackRepresentation='rejected'),
            lambda d: set_row(d).update(eflagsReadbackXor=None),
            lambda d: set_row(d).update(eflagsReadbackRepresentation=None),
            lambda d: set_row(d).update(readbackGetOrdinal=999),
            lambda d: set_row(d).update(stopEventSeq=1),
            lambda d: readback(d).update(stopEventSeq=1),
            lambda d: readback(d).update(eventTID=999),
            lambda d: readback(d).update(requestedGroups=['CONTROL']),
            lambda d: set_row(d).update(requestedGroups=['CONTROL','INTEGER']),
            lambda d: readback(d)['integer'].update(Rax='0000000000000001'),
            lambda d: set_row(d)['readbackControl'].update(EFlags='00000102'),
            lambda d: set_row(d)['submittedControl'].update(extra=1),
        ]
        for register in v.GPRS:
            changes.append(lambda d,register=register: readback(d)['integer'].update({register:'ffffffffffffffff'}))
        for field,value in [('SegCs',52),('SegSs',44),('Rip','0000000000000001'),('Rsp','0000000000000001')]:
            changes.append(lambda d,field=field,value=value: alter_readback_control(d,field,value))
        for field in ('eflagsReadbackPolicy','eflagsReadbackXor','eflagsReadbackRepresentation',
                      'beforeControl','submittedControl','readbackControl','readbackGetOrdinal'):
            changes.append(lambda d,field=field: set_row(d).pop(field))
        changes.append(lambda d: [r.pop('eflagsReadbackPolicy') for r in d[0] if r['kind']=='set'])
        for field in ('beforeControl','submittedControl','readbackControl'):
            changes.append(lambda d,field=field: set_row(d)[field].pop('EFlags'))
        for change in changes:
            with self.subTest(change=change):
                data=fixture(1); change(data)
                with self.assertRaises((ValueError,KeyError,IndexError)): check(data)

    def test_synthetic_reserved_bit1_forged_audit_is_rejected(self):
        for field,value in [('eflagsReadbackPolicy','old'), ('eflagsReadbackXor','00000000'),
                            ('eflagsReadbackRepresentation','exact'), ('eflagsReadbackRepresentation','rejected')]:
            with self.subTest(field=field,value=value):
                data=fixture(1); rows=data[0]
                target=next(r for r in rows if r['kind']=='set')
                rb=next(r for r in rows if r['kind']=='get' and r['apiOrdinal']==target['readbackGetOrdinal'])
                pre=[r for r in rows if r['kind']=='get' and r['stopEventSeq']==target['stopEventSeq'] and r['seq']<rb['seq']][-1]
                pre['control']['EFlags']=target['beforeControl']['EFlags']='00000202'
                target['submittedControl']['EFlags']='00000302'
                rb['control']['EFlags']=target['readbackControl']['EFlags']='00000300'
                target.update(eflagsReadbackXor='00000002',eflagsReadbackRepresentation='reserved_bit1_1_to_0')
                target[field]=value
                with self.assertRaises(ValueError): v.check_records(rows,data[1],data[3],data[4])

    def test_synthetic_w3_cleanup_is_not_transaction_authorization(self):
        data=fixture(3); rows,summary,child,receipt,epoch=data
        tx=next(r for r in rows if r['kind']=='transaction')
        cont=next(r for r in rows if r['kind']=='continue' and r['apiOrdinal']==tx['continueOrdinal'])
        cont['cleanupOnly']=True
        summary['cleanupContinues']+=1
        with self.assertRaises(ValueError): check(data)

    def test_mutations(self):
        def row(data, kind, index=0): return [r for r in data[0] if r['kind']==kind][index]
        changes = [
            (1,lambda d: row(d,'ownedChild').update(pid=999)),
            (1,lambda d: row(d,'transaction',1).update(txId=3)),
            (1,lambda d: row(d,'transaction',0).update(preRip='0000000000000001')),
            (1,lambda d: row(d,'transaction',0)['ops'][0].update(width=4)),
            (1,lambda d: row(d,'set').update(requestedGroups=['CONTROL','INTEGER'])),
            (1,lambda d: row(d,'get').update(requestedGroups=['CONTROL'])),
            (1,lambda d: row(d,'memory').update(actualBytesRead=255)),
            (1,lambda d: row(d,'transaction',1).update(postRip='0000000000000001')),
            (2,lambda d: row(d,'memory',3).update(rawHex='00'*288)),
            (3,lambda d: row(d,'transaction',1).update(retired=True)),
            (3,lambda d: d[3].update(parentExit=0)),
            (2,lambda d: d[1]['budgets'].update(gets=999)),
            (1,lambda d: row(d,'continue',3).update(cleanupOnly=True)),
            (1,lambda d: row(d,'set')['submittedControl'].update(Rsp='0000000000000000')),
            (1,lambda d: row(d,'event',0).update(eventTID=202)),
            (1,lambda d: row(d,'transaction',0)['ops'][0].update(preReadOrdinal=999)),
            (1,lambda d: row(d,'transaction',0)['ops'][0].update(postHex='00'*8)),
            (1,lambda d: row(d,'event',4).update(exceptionCode='80000003')),
            (2,lambda d: row(d,'transaction',2)['ops'][0].update(preHex='00'*32)),
            (2,lambda d: row(d,'transaction',4)['ops'][0].update(postHex='00'*32)),
            (3,lambda d: row(d,'event',5).update(exceptionAddress='0000000000000000')),
            (3,lambda d: row(d,'cleanup',2).update(BOOL=False)),
            (3,lambda d: d[1].update(normalTracePassed=True)),
        ]
        for case, change in changes:
            with self.subTest(case=case,change=change):
                data=fixture(case); change(data)
                with self.assertRaises((ValueError,KeyError,IndexError)): check(data)

    def test_exclusive_output(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'already.json'; path.write_text('unchanged')
            args=[]
            for name in ('header','bootstrap','bootstrap-mirror','contract-dir','events','parent-result','child-result','receipt','output'):
                args.extend(['--'+name,str(path)])
            with self.assertRaises(FileExistsError): v.main(args)
            self.assertEqual(path.read_text(),'unchanged')

    def test_complete_synthetic_cli(self):
        # All EXE/map/review files below are synthetic bytes inside a temporary directory.
        root=Path(__file__).resolve().parents[1]
        source_contract=root/'local/migration72/d02-resume-20261110b/a-clear48-trace-implementation-contract'
        for case in (1,2,3):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as directory:
                temp=Path(directory).resolve(); contracts=temp/'contracts'; contracts.mkdir()
                def put(path, content):
                    with path.open('xb') as out: out.write(content)
                    return path
                def encoded(value): return json.dumps(value,sort_keys=True).encode()
                def record(path):
                    content=path.read_bytes(); return {'path':str(path),'bytes':len(content),'sha256':v.sha(content)}
                header=put(temp/'shared.h',(root/'tools/client72_d02_native_trace/shared.h').read_bytes())
                for name in v.PINS: put(contracts/name,(source_contract/name).read_bytes())
                artifacts={name:put(temp/(name+'.synthetic'),('synthetic '+name).encode()) for name in ('parentExe','parentMap','childExe','childMap')}
                data=fixture(case,2 if case in (1,2) else 0); rows,summary,child,receipt,epoch=data
                epoch['childExeSHA256']=v.sha(artifacts['childExe'].read_bytes())
                b=struct.pack('<8sIIIIQ32s32s8I',b'C48BOOT1',1,128,epoch['descriptorRva'],7,int(epoch['imageBytes'],16),
                              bytes.fromhex(epoch['childExeSHA256']),bytes.fromhex(v.HEADER),
                              *[epoch[k] for k in v.EPOCH_KEYS[1:]])
                epoch['bootstrapSHA256']=v.sha(b)
                next(r for r in rows if r['kind']=='epoch').update(epoch)
                next(r for r in rows if r['kind']=='event' and r['rawDebugEventCode']==3)['fileSHA256']=epoch['childExeSHA256']
                boot=put(temp/'bootstrap.bin',b)
                mirror=put(temp/'bootstrap.json',encoded(dict(schema='c48-bootstrap-root-v1',imageBytes=int(epoch['imageBytes'],16),
                    childExeSHA256=epoch['childExeSHA256'],sharedHeaderSHA256=v.HEADER,bootstrapSHA256=epoch['bootstrapSHA256'],
                    labelsRva=dict(zip(v.LABELS,[epoch[k] for k in v.EPOCH_KEYS])))))
                review={'schema':'c48-trace-dependency-review-v1','independentlyReviewed':True,'actualBuildExit':0,'peAndCallerUnwindReviewed':True,
                        'artifacts':{k:record(p) for k,p in artifacts.items()}}
                source=[]
                for name in ('child.c','witness.asm','parent.c','build.ps1','build-epoch.h'):
                    source.append(record(put(temp/name,('synthetic source '+name).encode())))
                review['source']=[record(header)]+source
                for name in ('includes','libraries','compilerDlls','buildLogs'):
                    review[name]=[record(put(temp/(name+'.synthetic'),('synthetic '+name).encode()))]
                review_path=put(temp/'review.synthetic.json',encoded(review))
                paths=dict(artifacts,header=header,bootstrap=boot,bootstrapMirror=mirror,dependencyReview=review_path,
                           interface=contracts/'interface.v1.private.json',cases=contracts/'witness-cases.private.json',
                           eventSchema=contracts/'event-schema.v1.private.json',frozenInputs=contracts/'frozen-inputs.private.json')
                protected={str(p):v.sha(p.read_bytes()) for p in paths.values()}
                for key in ('source','includes','libraries','compilerDlls','buildLogs'):
                    for item in review[key]: protected[item['path']]=item['sha256']
                output=temp/'synthetic-run'; output.mkdir()
                receipt.update(schema='c48-root-run-v1',timedOut=False,startedUtc='2026-01-01T00:00:00Z',endedUtc='2026-01-01T00:00:01Z',
                               before=protected,after=copy.deepcopy(protected),artifacts={k:str(p) for k,p in paths.items()},
                               argv=[str(artifacts['parentExe']),'--child-exe',str(artifacts['childExe']),'--bootstrap',str(boot),
                                     '--case',str(case),'--run-id',receipt['runId'],'--output',str(output)])
                events=put(output/'events.jsonl',b''.join(encoded(r)+b'\n' for r in rows))
                parent=put(output/'parent-result.json',encoded(summary)); child_path=output/'child-result.json'
                if child is not None: put(child_path,encoded(child))
                receipt_path=put(temp/'receipt.synthetic.json',encoded(receipt)); report=temp/'synthetic-validation.json'
                argv=[]
                for name,path in [('header',header),('bootstrap',boot),('bootstrap-mirror',mirror),('contract-dir',contracts),('events',events),
                                  ('parent-result',parent),('child-result',child_path),('receipt',receipt_path),('output',report)]:
                    argv.extend(['--'+name,str(path)])
                status=v.main(argv)
                result=json.loads(report.read_text())
                self.assertEqual(status,0,result)
                self.assertTrue(result['pureArtifactValidationOnly'])
                self.assertEqual(result['eflagsReadbackPolicy'],v.EFLAGS_READBACK_POLICY)
                self.assertEqual(result['expectedNegativeAccepted'],case==3)
                if case == 1:
                    mutations = [
                        lambda x: x['after'].update({str(header):'00'*32}),
                        lambda x: x['before'].pop(str(header)),
                        lambda x: x['argv'].__setitem__(2,str(artifacts['parentExe'])),
                        lambda x: x['artifacts'].__setitem__('childMap',str(artifacts['parentMap'])),
                        lambda x: x.update(timedOut=True),
                    ]
                    for i, mutate in enumerate(mutations):
                        altered=copy.deepcopy(receipt); mutate(altered)
                        changed_receipt=put(temp/f'mutated-receipt-{i}.json',encoded(altered))
                        changed_report=temp/f'mutated-result-{i}.json'
                        alt=list(argv); alt[alt.index('--receipt')+1]=str(changed_receipt); alt[alt.index('--output')+1]=str(changed_report)
                        self.assertEqual(v.main(alt),1)
                        failed=json.loads(changed_report.read_text())
                        self.assertFalse(failed['accepted'])
                        self.assertEqual(failed['eflagsReadbackPolicy'],v.EFLAGS_READBACK_POLICY)


if __name__ == '__main__':
    unittest.main()
