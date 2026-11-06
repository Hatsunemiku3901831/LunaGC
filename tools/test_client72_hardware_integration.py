"""Execute the real observer's nested g-mode selection with local API doubles.

The actual function AST is used so a return-name/branch regression is visible;
no debugger/client imports inside execute(), process or command is run.
"""
import ast
import copy
from pathlib import Path
from types import SimpleNamespace
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parent))
import observe_client72_startup as observer

GPR = ('rax','rbx','rcx','rdx','rsi','rdi','rbp','rsp','r8','r9','r10','r11','r12','r13','r14','r15')


def actual_nested(name, environment):
    path=Path(observer.__file__)
    outer=next(n for n in ast.parse(path.read_text('utf8')).body if isinstance(n,ast.FunctionDef) and n.name=='execute')
    function=next(n for n in outer.body if isinstance(n,ast.FunctionDef) and n.name==name)
    module=ast.Module(body=[copy.deepcopy(function)],type_ignores=[])
    exec(compile(ast.fix_missing_locations(module),str(path),'exec'),environment)
    return environment[name]


class HardwareObserverIntegrationTests(unittest.TestCase):
    def test_real_g_snapshot_captures_all_gprs_and_selected_identity(self):
        values={n:0x10000+i*16 for i,n in enumerate(GPR)}
        values.update(rip=0x5000,eflags=0x246,**{n:0 for n in observer.DR_NAMES})
        client=SimpleNamespace(get_regs=lambda:SimpleNamespace(context=SimpleNamespace(**values)),
                               get_reg=lambda n:values['rip' if n=='cip' else n],
                               eval_sync=lambda expr:(7 if expr=='tid()' else 0x80000,True))
        def read(address,size):
            return (100).to_bytes(8,'little')+(7).to_bytes(8,'little') if address==0x80040 else bytes(size)
        snapshot=actual_nested('snapshot',dict(c=client,read=read,DR_NAMES=observer.DR_NAMES,
                                args=SimpleNamespace(main_observations='global-dispatch-hardware')))()
        self.assertTrue(set(GPR)<=snapshot['registers'].keys())
        self.assertEqual({n:snapshot['registers'][n] for n in GPR},{n:values[n] for n in GPR})
        self.assertEqual(snapshot['registers']['cip'],values['rip'])
        self.assertEqual((snapshot['selectedPid'],snapshot['selectedTid'],snapshot['tid']),(100,7,7))
        self.assertEqual(set(snapshot['registerExpressions']),set(('cip','rsp','eflags')+observer.DR_NAMES))

    def return_case(self, tid=7, rsp=0x9008):
        calls=[]
        hardware=SimpleNamespace(owned=dict(address=0x5000,name='cn72_r2_return_hw'))
        def clear(reason,event):
            calls.append((reason,event));hardware.owned=None
        hardware.clear=clear
        def forbidden(*_):
            self.fail('HW void return must neither read managed RAX nor clear a software point')
        pending=[dict(kind='global_dispatch',address=0x5000,expectedRsp=0x9008,tid=7,inputHex=None)]
        report=dict(observations=[])
        row=dict(snapshot=dict(registers=dict(cip=0x5000,rsp=rsp,rax=0xDEADBEEF),tid=tid),
                 event=dict(event='EVENT_BREAKPOINT',data=dict(type=2,addr=0x5000,name='cn72_r2_return_hw',slot=0)))
        observe=actual_nested('observe',dict(hardware=hardware,points={},pending=pending,report=report,
                              managed_string=forbidden,remove=forbidden,
                              matching_returns=observer.matching_returns,return_observation=observer.return_observation))
        return observe(row),calls,pending,report,hardware

    def test_actual_hw_return_branch_matches_clears_and_stops_without_reading_rax(self):
        reason,calls,pending,report,hardware=self.return_case()
        self.assertEqual(reason,'natural-call-return-observed')
        self.assertEqual(len(calls),1)
        self.assertEqual(calls[0][0],'matched-natural-return')
        self.assertEqual(pending,[])
        self.assertIsNone(hardware.owned)
        self.assertTrue(report['observations'][0]['returnMatched'])
        self.assertIn('void',report['observations'][0]['returnValueSemantics'])
        self.assertNotIn('resultPointer',report['observations'][0])

    def test_unrelated_hw_return_thread_or_stack_keeps_pending_and_does_not_clear(self):
        for tid,rsp in ((8,0x9008),(7,0x9010)):
            reason,calls,pending,report,hardware=self.return_case(tid,rsp)
            self.assertEqual(reason,'return-unrelated-thread-or-stack')
            self.assertEqual(calls,[])
            self.assertEqual(len(pending),1)
            self.assertEqual(report['observations'],[])
            self.assertIsNotNone(hardware.owned)


if __name__=='__main__':
    unittest.main()
