import struct
import unittest
from tools import collect72_avatar_string_successors as card
from tools import test_collect72_string_consumer_inputs as old
from tools import test_collect72_avatar_warm_usages as warm
M=old.M
ROOT=0x770000000000

def fixture(derived=1243):
    m=old.fixture(derived)
    m.put(warm.LIST+0xca,b'\x20')
    m.number(M+0x588ca20,warm.LIST)
    m.number(M+0x5bc8f18,ROOT);m.number(ROOT+0x2738,0x770000002000)
    m.number(M+0x5bc96f8,0x780000001000)
    return m

def run(m,read=None):return card.collect(read or m.read,M,now=lambda:'owned')

class CombinedTests(unittest.TestCase):
    def test_full_and_find_zero_paths(self):
        for derived in (1243,1240):
            for find in (0,old.FIND):
                m=fixture(derived);m.number(old.RG+0x78,find);r=run(m)
                self.assertEqual(r['status'],'candidate',r)
                self.assertEqual(r['carrier']['findEntryStatus'],'unverified')
                self.assertFalse(any(x['address']==old.FIND for x in r['records']))
                self.assertEqual(r['string']['equals']['functionPointer'],warm.STRING_MI_CODE)
                self.assertFalse(r['string']['equals']['functionEqualsSlot'])
                self.assertEqual(r['inputConsistency']['status'],'repeated-unchanged')
                phases=[x['phase'] for x in r['records']];self.assertNotIn('new',phases[phases.index('final'):])
                self.assertLessEqual(r['budget']['firstPassReads'],38);self.assertLessEqual(r['budget']['firstPassBytes'],1632)
                self.assertEqual(r,run(fixture_adjusted(derived,find)))
    def test_branch_independence(self):
        m=fixture();m.number(M+0x5bc8f18,0);r=run(m)
        self.assertEqual(r['string']['status'],'collected');self.assertEqual(r['ordinary']['failure'],'ordinary:root-null');self.assertFalse(any(x['address']==0x2738 for x in r['records']))
        m=fixture();m.number(M+0x588ca20,0);r=run(m)
        self.assertEqual(r['string']['status'],'collected');self.assertEqual(r['comparison']['status'],'collected');self.assertEqual(r['ordinary']['root'],ROOT)
        m=fixture();m.put(warm.LIST+0xca,b'\x00');r=run(m)
        self.assertEqual(r['list']['failure'],'list:allocator-branch-flag-clear');self.assertEqual(r['ordinary']['status'],'collected')
        m=fixture();m.number(old.RG+0x20,0);r=run(m);self.assertIsNone(r['string']);self.assertEqual(r['list']['status'],'collected')
    def test_guards(self):
        cases=[(old.TRY+24,struct.pack('<I',1)),(old.TY1+8,struct.pack('<I',11<<16)),(old.TY2+8,struct.pack('<I',(11<<16)|0x40000000)),
            (warm.LIST+0xc8,b'\x00'),(warm.LIST+0x10000+16,struct.pack('<Q',1)),(warm.LIST+0x10000+24,struct.pack('<Q',1)),(warm.U32_CONTEXT+0x200+8,struct.pack('<I',(9<<16)|0x40000000)),
            (warm.STRING_GETTER+24,struct.pack('<I',1)),(warm.STRING_OWNER+0x10000,struct.pack('<I',1)),(warm.STRING_CONTEXT+0x200+8,struct.pack('<I',(14<<16)|0x40000000)),
            (warm.STRING_SLOTS+0x10,struct.pack('<Q',warm.STRING_DERIVED)),(warm.STRING_DERIVED+0xbe,struct.pack('<H',8)),(warm.STRING_EQUALS+24,struct.pack('<I',1))]
        for address,raw in cases:
            m=fixture();m.put(address,raw);self.assertEqual(run(m)['status'],'partial',hex(address))
        m=fixture(1240);m.put(warm.STRING_MI_OWNER+0x10000,struct.pack('<I',1235));self.assertEqual(run(m)['status'],'partial')
    def test_drift_errors_overlap_and_budget(self):
        m=fixture();calls={}
        def drift(a,n):
            raw,error=m.read(a,n);calls[a,n]=calls.get((a,n),0)+1
            return (bytes([raw[0]^1])+raw[1:],error) if calls[a,n]>1 and raw else (raw,error)
        self.assertEqual(run(m,drift)['inputConsistency']['status'],'failed')
        def bad(a,n):return (b'',None)
        self.assertEqual(run(fixture(),bad)['status'],'partial')
        values=iter([(b'abcd',None),(b'ZZ',None)]);ledger=card.Ledger(lambda a,n:next(values),lambda:'owned');ledger.get('a',100,4)
        with self.assertRaises(card.ReadStop):ledger.get('b',101,2)
        self.assertTrue(ledger.conflicts)
        ledger=card.Ledger(lambda a,n:(bytes(n),None),lambda:'owned')
        for i in range(38):ledger.get('x',0x1000+i*8,8)
        with self.assertRaises(card.ReadStop):ledger.get('overflow',0x2000,8)
        self.assertTrue(ledger.denied)
        m=fixture();m.number(M+0x5bc8f18,0)
        def root_error(a,n):
            if a==M+0x5bc8f18:return b'',{'kind':'owned-error'}
            return m.read(a,n)
        r=run(m,root_error);self.assertEqual(r['string']['status'],'collected');self.assertEqual(r['ordinary']['status'],'partial')
        self.assertTrue(any(x['address']==M+0x5bc8f18 and x['error'] for x in r['records']))
        m=fixture();m.number(old.RG+0x20,warm.LIST);r=run(m)
        self.assertEqual(r['status'],'partial');self.assertEqual(r['list']['status'],'collected')
    def test_contract_pins(self):self.assertEqual(card.verify_contract()['parentPins'],18)

def fixture_adjusted(derived,find):
    m=fixture(derived);m.number(old.RG+0x78,find);return m

if __name__=='__main__':unittest.main()
