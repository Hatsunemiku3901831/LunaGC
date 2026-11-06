"""New single-slot/thread/status guards, independent of any live debug target."""
import copy
import unittest
from tools.client72_single_hardware import hardware_guard, unchanged_cpu


class SingleHardwareTests(unittest.TestCase):
    def setUp(self):
        self.regs = dict(cip=0x5000, rsp=0x9008, rax=17, rcx=1, eflags=0x344,
                         dr0=0x5000, dr1=0, dr2=0, dr3=0, dr6=0, dr7=1)
        self.snap = dict(registers=self.regs, registerExpressions={k:self.regs[k] for k in ('cip','rsp','eflags','dr0','dr1','dr2','dr3','dr6','dr7')}, tid=7, selectedPid=100, selectedTid=7)
        self.point = dict(address=0x5000, name='owned', module='fixture.exe', slot=0)
        self.bp = dict(type=2,addr=0x5000,name='owned',mod='fixture.exe',slot=0,typeEx=2,hwSize=0,
                       enabled=True,active=True,singleshoot=False,fastResume=False,breakCondition='',commandText='')
        self.event = dict(event='EVENT_BREAKPOINT',data=dict(self.bp))

    def test_owned_execute_status_is_limited_to_slot_zero(self):
        for dr7 in (1,0x401):
            for dr6 in (0,1,0xffff0ff0,0xffff0ff1):
                for group in ('registers','registerExpressions'):
                    self.snap[group].update(dr6=dr6,dr7=dr7)
                self.assertIsNone(hardware_guard(self.snap,self.event,self.point,[self.bp],100))
        for group in ('registers','registerExpressions'):
            self.snap[group]['dr6']=2
        self.assertEqual(hardware_guard(self.snap,self.event,self.point,[self.bp],100),'stop-hardware-hit-status')

    def test_hit_status_without_matching_event_is_not_consumed(self):
        for group in ('registers','registerExpressions'):
            self.snap[group]['dr6']=1
        self.assertEqual(hardware_guard(self.snap,dict(event='EVENT_EXCEPTION',data={}),self.point,[self.bp],100),'stop-hardware-status-without-owned-hit')

    def test_point_and_event_identity_both_required(self):
        for key,value in (('slot',1),('addr',0x5001),('type',1),('typeEx',3),('hwSize',1)):
            event=copy.deepcopy(self.event);event['data'][key]=value
            self.assertEqual(hardware_guard(self.snap,event,self.point,[self.bp],100),'stop-hardware-event-identity')
        self.assertEqual(hardware_guard(self.snap,self.event,self.point,[self.bp,self.bp],100),'stop-hardware-table-count')

    def test_return_requires_selected_event_thread_and_exact_stack(self):
        point=dict(self.point,expectedTid=7,expectedRsp=0x9008)
        self.assertIsNone(hardware_guard(self.snap,self.event,point,[self.bp],100))
        for group in ('registers','registerExpressions'):
            self.snap[group]['rsp']+=8
        self.assertEqual(hardware_guard(self.snap,self.event,point,[self.bp],100),'stop-hardware-return-thread-or-stack')
        self.snap['selectedTid']=8
        self.assertEqual(hardware_guard(self.snap,self.event,point,[self.bp],100),'stop-hardware-selected-thread-mismatch')

    def test_no_owned_point_rejects_residual_or_mismatched_api(self):
        self.assertEqual(hardware_guard(self.snap,None,None,[],100),'stop-unowned-hardware-state')
        self.snap['registerExpressions']['dr0']=0
        self.assertEqual(hardware_guard(self.snap,None,self.point,[self.bp],100),'stop-hardware-register-api-mismatch')

    def test_control_changes_only_allow_known_reserved_flag_bit(self):
        after=copy.deepcopy(self.snap);after['registers'].update(dr0=0,dr6=0,dr7=0)
        after['registers']['eflags']^=2
        self.assertTrue(unchanged_cpu(self.snap,after))
        after['registers']['eflags']^=1
        self.assertFalse(unchanged_cpu(self.snap,after))
        after=copy.deepcopy(self.snap);after['registers']['rax']+=1
        self.assertFalse(unchanged_cpu(self.snap,after))


if __name__=='__main__':
    unittest.main()
