"""Owned synthetic carrier and String graph tests; no process access."""
import struct
import unittest
from tools import collect72_string_consumer_inputs as card
from tools import test_collect72_avatar_warm_usages as old

M=old.MODULE
BASE=0x810000001000
OWNER=BASE+0x1000; GC=BASE+0x2000; INST=BASE+0x3000; ARGS=BASE+0x4000
TY1=BASE+0x5000; TY2=BASE+0x6000; RG=BASE+0x7000; DEF=BASE+0x8000
TRY=BASE+0x9000; FIND=BASE+0xa000

def fixture(derived=1243):
    m=old.string_fixture(derived)
    m.number(M+0x59ae108,TRY)
    for ptr,index in ((TRY,10200),(FIND,10194)):
        raw=bytearray(56);struct.pack_into('<Q',raw,0,OWNER);struct.pack_into('<I',raw,24,index);m.put(ptr,raw)
    h=bytearray(208);struct.pack_into('<I',h,0x18,21<<16);struct.pack_into('<Q',h,0x30,RG);struct.pack_into('<Q',h,0x68,GC);m.put(OWNER,h)
    m.put(GC,struct.pack('<I4xQQQ',1222,INST,0,OWNER));m.put(INST,struct.pack('<I4xQ',2,ARGS));m.put(ARGS,struct.pack('<QQ',TY1,TY2))
    m.put(TY1,struct.pack('<QII',0x8000000000000011,14<<16,0));m.put(TY2,struct.pack('<QII',0x8000000000000022,11<<16,0))
    m.put(RG,struct.pack('<I4xQ',37,DEF));m.number(RG+0x78,FIND);m.number(RG+0x20,old.STRING_GETTER)
    m.put(DEF+2*4,struct.pack('<I',0x0c08c554));m.put(DEF+13*4,struct.pack('<I',0x0c08c559))
    return m

def run(m,read=None):return card.collect(read or m.read,M,now=lambda:'owned')

class GraphTests(unittest.TestCase):
    def test_positive_and_fresh(self):
        for derived in (1243,1240,1235):
            a=run(fixture(derived));b=run(fixture(derived));self.assertEqual(a,b);self.assertEqual(a['status'],'candidate')
            self.assertEqual(a['string']['equals']['functionPointer'],old.STRING_MI_CODE)
            self.assertFalse(a['string']['equals']['functionEqualsSlot'])
            self.assertLessEqual(a['budget']['firstPassReads'],27);self.assertLessEqual(a['budget']['firstPassBytes'],1352)
            self.assertEqual(a['inputConsistency']['status'],'repeated-unchanged')
    def test_zero_and_prefix(self):
        for slot in (0x20,0x78):
            m=fixture();m.number(RG+slot,0);r=run(m);self.assertEqual(r['status'],'partial');self.assertEqual(r['carrier']['lazyDiagnostic']['words'],[0xc08c554,0xc08c559]);self.assertIsNone(r['string'])
            self.assertFalse(any(x['address']==0 for x in r['records']))
        m=fixture();m.number(RG+0x20,0);m.put(DEF+8,bytes(4));self.assertIn('definition-words-mismatch',run(m)['carrier']['failure'])
        m=fixture();m.number(RG+0x20,0);m.put(RG,struct.pack('<I4xQ',2,DEF));self.assertIn('prefix-mismatch',run(m)['carrier']['failure'])
    def test_bad_carrier(self):
        for address,raw in ((TRY+24,struct.pack('<I',0)),(FIND,struct.pack('<Q',OWNER+1)),(GC,struct.pack('<I',1)),(GC+16,struct.pack('<Q',1)),(INST,struct.pack('<I',1)),(TY1+8,struct.pack('<I',11<<16)),(TY2+8,struct.pack('<I',(11<<16)|0x40000000))):
            m=fixture();m.put(address,raw);self.assertEqual(run(m)['status'],'partial')
    def test_errors_and_drift(self):
        m=fixture()
        for read in (lambda a,n:(b'',None),lambda a,n:(_ for _ in ()).throw(OSError(5,'owned'))):
            self.assertEqual(run(m,read)['status'],'partial')
        calls={}
        def drift(a,n):
            raw,error=m.read(a,n);calls[a,n]=calls.get((a,n),0)+1
            return (bytes([raw[0]^1])+raw[1:],error) if calls[a,n]>1 and raw else (raw,error)
        r=run(m,drift);self.assertEqual(r['status'],'partial');self.assertEqual(r['inputConsistency']['status'],'failed')
    def test_successor_guards(self):
        cases=((old.STRING_GETTER+24,struct.pack('<I',1)),
            (old.STRING_OWNER+0x10000,struct.pack('<I',1)),
            (old.STRING_CONTEXT+0x200+8,struct.pack('<I',14<<16|0x40000000)),
            (old.STRING_SLOTS+0x10,struct.pack('<Q',old.STRING_DERIVED)),
            (old.STRING_DERIVED+0xbe,struct.pack('<H',8)),
            (old.STRING_EQUALS,struct.pack('<Q',old.STRING_OWNER)),
            (old.STRING_EQUALS+24,struct.pack('<I',10289)))
        for address,raw in cases:
            m=fixture();m.put(address,raw);r=run(m);self.assertEqual(r['status'],'partial');self.assertIsNotNone(r['string'])
    def test_overlap_conflict_and_invalid_address(self):
        values=iter([(b'abcd',None),(b'ZZ',None)])
        ledger=card.Ledger(lambda a,n:next(values),lambda:'owned')
        ledger.get('first',100,4)
        with self.assertRaises(card.ReadStop):ledger.get('overlap',101,2)
        self.assertTrue(ledger.conflicts)
        ledger=card.Ledger(lambda a,n:self.fail('invalid address called backend'),lambda:'owned')
        with self.assertRaises(card.ReadStop):ledger.get('invalid',2**64,8)
        self.assertFalse(ledger.records[0]['backendCalled'])

    def test_budget_and_invalid(self):
        l=card.Ledger(lambda a,n:(bytes(n),None),lambda:'owned')
        for i in range(27):l.get('x',0x1000+i*8,8)
        with self.assertRaises(card.ReadStop):l.get('overflow',0x2000,8)
        self.assertEqual(len(l.denied),1)
        with self.assertRaises(ValueError):card.collect(None,M)
        with self.assertRaises(ValueError):card.collect(lambda a,n:(b'',None),True)

if __name__=='__main__':unittest.main()
