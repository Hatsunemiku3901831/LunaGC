"""Bounded static xrefs to one fixed ConfigMetaConfig MethodInfo cache cell."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'local/migration72/d02-resume-20261111b/scene-xref'
CELL = 0x145a5ee48
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
GENERIC = 'local/migration72/d02-resume-20261111b/scene/configmeta-generic-uses.private.json'
SPEC = 'local/migration72/d02-resume-20261111b/scene/configmeta-xref-spec.zh.md'
PINS = {
 SPEC: (None, '3a1e31ddbf8f57cf2ee841ffd57ba2d69e790d1dba3c9d786a561ab45200f85f'),
 'protocol/cn-7.2/profile.json': (1453, 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'),
 META: (117201003, 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f'),
 SIG: (78649549, '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe'),
 'local/migration72/d02-active/main/payload-index-third.json': (92384, 'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9'),
 'local/migration72/d02-active/main/locator-third.json': (1452522, 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda'),
 GENERIC: (1396, '18ed077f2fefd3e898dcf581041252aeb355b4d554f32a70f453b9d97468ce8d'),
 'tools/client72_avatar_deep_rgctx.py': (14729, 'd41197f942c3d812921a9e878884ce659233e96ccbf25ca87b462bd2676bba8c'),
 'tools/client72_scene_deep.py': (None, 'cee00748758607654e5728a624ebafc80a2b90e79b34e28a3dc6f5ed276a5ce1'),
 'tools/extract_client72.py': (21826, '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d'),
 'tools/extract_client72_literals.py': (5282, '865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed'),
 'tools/game_path.ps1': (None, '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'),
 'local/migration72/python/capstone/__init__.py': (None, '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'),
 'local/migration72/python/capstone/lib/capstone.dll': (None, '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'),
}
STOCK = {
 'YuanShen.exe': (450927000, '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
 'YuanShen_Data/Managed/Metadata/global-metadata.dat': (83644188, '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
 'YuanShen_Data/Managed/Metadata/startup-metadata.dat': (4116148, 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
 'YuanShen_Data/Plugins/AccountPlatNative.dll': (9360280, 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
 'YuanShen_Data/Plugins/Astrolabe.dll': (2614168, '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442'),
}
sys.path.insert(0, str(ROOT))


def digest(raw): return hashlib.sha256(raw).hexdigest()
def require(condition, message):
 if not condition: raise ValueError(message)
def checked(path, size, sha):
 raw = path.read_bytes()
 require((size is None or len(raw) == size) and digest(raw) == sha, 'source drift: ' + str(path))
 return raw

def save(path, value):
 with path.open('xb') as f:
  f.write((json.dumps(value, sort_keys=True, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))

class PureData(pickle.Unpickler):
 def find_class(self, module, name): raise ValueError('metadata contains global class')


def gate(raw):
 profile = json.loads(raw['protocol/cn-7.2/profile.json'])
 require({item['path']: (item['size'], item['sha256']) for item in profile['files'].values()} == STOCK, 'profile stock differs')
 records, cells = PureData(io.BytesIO(raw[META])).load()
 signatures = json.loads(raw[SIG]); prior = json.loads(raw[GENERIC])
 method = [m for m in records[11312]['methods'] if m['index'] == 80455]
 require((records[11312]['namespace'], records[11312]['name']) == ('MoleMole', 'ConfigUtil') and
         (records[77121]['namespace'], records[77121]['name']) == ('MoleMole.Config', 'ConfigMetaConfig'), 'declaring types differ')
 require(len(method) == 1 and (method[0]['name'], method[0]['flags'], method[0]['virtualSlot'],
         method[0]['registeredAddress'], method[0]['parameterCount']) == ('LoadConfig', '0x96', 65535, '0x0', 2), 'method declaration differs')
 signature = [m for m in signatures['methods'] if m['index'] == 80455]
 require(len(signature) == 1 and signature[0]['declaringType'] == 11312 and
         [(p['index'], p['name'], p['typeIndex']) for p in signature[0]['parameters']] == [(85597, 'filePath', 33), (85598, 'handle', 445227)], 'signature differs')
 require((signatures['typeReferences']['33']['kind'], signatures['typeReferences']['33']['byRef']) == ('string', False) and
         (signatures['typeReferences']['445227']['kind'], signatures['typeReferences']['445227']['byRef']) == ('uint32', True), 'parameter types differ')
 require(len(prior['hits']) == 1 and prior['hits'][0]['cell'] == hex(CELL) and
         (prior['hits'][0]['usageRow'], prior['hits'][0]['methodSpec'], prior['hits'][0]['methodDefinition'],
          prior['hits'][0]['classInstance'], prior['hits'][0]['methodInstance']) == (758408, 93696, 80455, -1, 66311), 'prior generic evidence differs')
 return records


def check_usage(source, slot, cache, spec_tuple, arg_count, kind, byref, definition):
 require(source >> 29 == 6 and (source & 0x1fffffff) == 93696 and cache + slot * 8 == CELL, 'usage tuple/cell differs')
 require(spec_tuple == (80455, -1, 66311), 'MethodSpec tuple differs')
 require(arg_count == 1 and kind == 18 and not byref and definition == 77121, 'generic argument type differs')


def bind_usage(binary, metadata, records):
 from tools.extract_client72 import PE, HEADER_VA, BODY, METADATA_REGISTRATION, MASK32, number
 from tools.extract_client72_literals import Literals72, USAGE_REGISTRATION
 pe = PE(binary); header = pe.offset(HEADER_VA); literal = Literals72(binary, metadata)
 reg = pe.offset(USAGE_REGISTRATION); cache = number(binary, reg + 0x10, 'Q')
 row_index = 758408
 def group_first(i):
  return ((number(metadata,literal.groups_base+i*4)+0x8bff3e05)&MASK32)^((i*0x5843eaa0-0x443c8630)&MASK32)
 require(group_first(literal.global_group) <= row_index < group_first(literal.global_group+1), 'usage outside global group')
 offset = literal.usages_base + row_index * 8
 raw = metadata[offset:offset+8]; require(len(raw) == 8, 'short usage')
 x, y = struct.unpack('<II', raw); key = (row_index * 0x57c15286 + 0x26ddebda) & MASK32
 source = ((x + 0xabbdcabf) & MASK32) ^ key ^ 0x2a357d90
 slot = y ^ key ^ 0x1855aa65
 spec_base = BODY + (number(binary, header + 0x170) ^ 0x6347382b)
 spec_offset = spec_base + 93696 * 12
 spec_raw = metadata[spec_offset:spec_offset+12]
 require(len(spec_raw) == 12, 'short MethodSpec')
 spec_tuple = struct.unpack('<iii', spec_raw)
 registration = pe.offset(METADATA_REGISTRATION); references = number(binary, registration, 'Q')
 count = (number(binary, registration + 0x8c) + 0xce67355c) & MASK32
 instances = number(binary, registration + 0x70, 'Q'); instance_va = instances + 66311 * 16
 instance_off = pe.offset(instance_va, 16); instance = binary[instance_off:instance_off+16]
 argument_count, pointer = struct.unpack('<QQ', instance)
 require(argument_count == 1, 'generic argument count differs')
 arg_off = pe.offset(pointer, 8); arg_raw = binary[arg_off:arg_off+8]; arg_ptr = int.from_bytes(arg_raw, 'little')
 require(references <= arg_ptr < references + count * 16 and (arg_ptr - references) % 16 == 0, 'generic arg pointer not registered')
 type_index = (arg_ptr - references) // 16; ref_off = pe.offset(arg_ptr, 16); ref_raw = binary[ref_off:ref_off+16]
 data, bits = struct.unpack_from('<QI', ref_raw)
 check_usage(source,slot,cache,spec_tuple,argument_count,(bits >> 16) & 0xff,bool(bits & 0x40000000),data)
 require((records[data]['namespace'], records[data]['name']) == ('MoleMole.Config', 'ConfigMetaConfig'), 'generic argument name differs')
 return {'usageRow': row_index, 'usageFileOffset': hex(offset), 'usageHex': raw.hex(), 'source': hex(source),
         'slot': slot, 'cacheBase': hex(cache), 'cell': hex(CELL), 'methodSpec': 93696,
         'specFileOffset': hex(spec_offset), 'specHex': spec_raw.hex(), 'tuple': [80455, -1, 66311],
         'instanceVA': hex(instance_va), 'instanceHex': instance.hex(), 'argumentPointerVA': hex(pointer),
         'argumentPointerHex': arg_raw.hex(), 'typeReferenceIndex': type_index, 'typeReferenceVA': hex(arg_ptr),
         'typeReferenceHex': ref_raw.hex(), 'typeDefinition': 77121}


def scan(data, start, cell=CELL):
 found = []
 for prefix in (0x48, 0x4c):
  for opcode in (0x8b, 0x8d, 0x89):
   needle = bytes((prefix, opcode)); position = -1
   while True:
    position = data.find(needle, position + 1)
    if position < 0: break
    if position + 7 > len(data) or data[position+2] & 0xc7 != 5: continue
    site = start + position
    if site + 7 + struct.unpack_from('<i', data, position+3)[0] == cell:
     found.append({'site': hex(site), 'bytes': data[position:position+7].hex(),
                   'kind': {0x8b:'load', 0x8d:'lea', 0x89:'store'}[opcode]})
 return sorted(found, key=lambda x:int(x['site'],16))


def locate_pdata(table, rva, base, candidates):
 require(len(table) % 12 == 0, 'unaligned pdata')
 locations = {int(x['site'], 16): [] for x in candidates}; begins = {}
 for off in range(0, len(table), 12):
  begin, end, unwind = struct.unpack_from('<III', table, off)
  va, limit = base + begin, base + end
  for site in locations:
   if va <= site < limit:
    locations[site].append({'begin': hex(va), 'endExclusive': hex(limit), 'size': end-begin,
       'recordRVA': hex(rva+off), 'recordOffset': off, 'rawHex': table[off:off+12].hex(), 'unwindRVA': hex(unwind)})
  begins[va] = begins.get(va, 0) + 1
 return [{'candidate': c, 'containing': locations[int(c['site'],16)],
          'beginMultiplicity': [begins[int(x['begin'],16)] for x in locations[int(c['site'],16)]]} for c in candidates]


def budget(candidates, located):
 if len(candidates) > 4: return 'raw-candidate-limit', []
 if any(len(x['containing']) != 1 or x['beginMultiplicity'] != [1] for x in located): return 'pdata-ambiguous-or-missing', []
 rows = {x['containing'][0]['begin']: x['containing'][0] for x in located}
 if len(rows) > 4 or any(not 0 < x['size'] <= 8192 for x in rows.values()) or sum(x['size'] for x in rows.values()) > 16384:
  return 'body-budget-exceeded', []
 return None, list(rows.values())


def decode(code, begin):
 sys.path.insert(0, str(ROOT / 'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_MEM, X86_REG_RIP
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True
 result=[]; cursor=begin
 for ins in cs.disasm(code, begin):
  raw=bytes(ins.bytes); require(ins.address == cursor and 0 < len(raw) <= 15 and code[cursor-begin:cursor-begin+len(raw)] == raw, 'decode gap')
  rip=[hex(ins.address+ins.size+op.mem.disp) for op in ins.operands if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP]
  result.append({'address':hex(ins.address),'bytes':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'ripTargets':rip})
  require(len(result)<=2048, 'instruction budget exceeded'); cursor+=len(raw)
 require(result and cursor==begin+len(code), 'incomplete decode')
 return result


def valid_refs(candidates, decoded):
 valid = []
 for candidate in candidates:
  for instructions in decoded.values():
   for ins in instructions:
    if ins['address'] == candidate['site'] and ins['bytes'] == candidate['bytes'] and hex(CELL) in ins['ripTargets']:
     valid.append(candidate)
 return valid


def outcome(valid, located):
 begins = {x['containing'][0]['begin'] for x in located if x['candidate'] in valid}
 if not begins: return 'zero-valid', None
 if len(begins) > 1: return 'multiple-valid-callers', None
 return 'unique-caller', next(iter(begins))


def check_aot(raw, caller):
 require(len(raw) == 8 and int.from_bytes(raw, 'little') == int(caller, 16), 'ordinary AOT pointer mismatch')


def collect(destination):
 result={'task':'F-03d3s2/CONFIGMETA-LOADCONFIG-XREF','toolSHA256':digest(Path(__file__).read_bytes()),
         'stage':'source-gate','nativeCPU':0,'live':0,'xdataBytes':0,'calleeBodies':0,
         'windows':0,'naturalCallObserved':False,'resourceReady':False}
 try:
  raw={path:checked(ROOT/path,size,sha) for path,(size,sha) in PINS.items()}; result['stage']='five-stock'
  client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')], text=True).strip())
  data={name:checked(client/name,size,sha) for name,(size,sha) in STOCK.items()}
  result['inputChecks']={name:{'bytes':len(value),'sha256':digest(value)} for name,value in data.items()}
  records=gate(raw); del raw
  binary=data['YuanShen.exe']; metadata=data['YuanShen_Data/Managed/Metadata/global-metadata.dat']
  from tools.extract_client72 import PE, CODE_REGISTRATION, number
  pe=PE(binary); result['stage']='typed-usage'; result['usage']=bind_usage(binary,metadata,records)
  nt=number(binary,0x3c); optional=number(binary,nt+20,'H'); table_offset=nt+24+optional
  candidates=[]; sections=[]
  for i,(address,size,raw,flags) in enumerate(pe.sections):
   if not flags & 0x20000000: continue
   name=binary[table_offset+i*40:table_offset+i*40+8].rstrip(b'\x00').decode('ascii',errors='replace')
   found=scan(binary[raw:raw+size],address); candidates.extend(found)
   sections.append({'name':name,'va':hex(address),'rva':hex(address-pe.base),'rawOffset':raw,'rawBytes':size,
                    'scanStart':raw,'scanEndExclusive':raw+size,'candidateCount':len(found)})
  candidates.sort(key=lambda x:int(x['site'],16)); result.update(stage='raw-candidates',sections=sections,
       scannedExecutableBytes=sum(x['rawBytes'] for x in sections),candidates=candidates)
  save(destination/'candidates.private.json',result)
  rva,size=struct.unpack_from('<II',binary,nt+24+0x70+3*8); off=pe.offset(pe.base+rva,size)
  table=binary[off:off+size]; located=locate_pdata(table,rva,pe.base,candidates)
  stop,rows=budget(candidates,located)
  result.update(stage='pdata-before-body',exceptionDirectory={'rva':hex(rva),'rawOffset':off,'bytesScanned':size,
       'entries':size//12,'sha256':digest(table)},located=located,budgetStop=stop,plannedBodies=rows)
  save(destination/'pre-body.private.json',result)
  for i,item in enumerate(located):
   if len(item['containing'])==1:
    (destination/('candidate-%d.pdata.bin'%i)).write_bytes(bytes.fromhex(item['containing'][0]['rawHex']))
  if stop:
   result.update(stage='stopped',reason=stop); save(destination/'extraction.private.json',result); print(json.dumps({'stop':stop,'rawCandidates':len(candidates)})); return
  decoded={}
  for row in rows:
   begin=int(row['begin'],16); code_offset=pe.offset(begin,row['size'],executable=True); code=binary[code_offset:code_offset+row['size']]
   result['windows']+=1; instructions=decode(code,begin); decoded[row['begin']]=instructions
   (destination/('body-'+row['begin']+'.bin')).write_bytes(code)
   save(destination/('body-'+row['begin']+'.asm.private.json'),instructions)
  valid=valid_refs(candidates,decoded); status,caller=outcome(valid,located)
  result.update(stage='boundary-checked',validRefs=valid,outcome=status,caller=caller,
        bodies=[{'begin':r['begin'],'bytes':r['size'],'sha256':digest((destination/('body-'+r['begin']+'.bin')).read_bytes()),
                 'instructions':len(decoded[r['begin']])} for r in rows])
  valid_begins={x['containing'][0]['begin'] for x in located if x['candidate'] in valid}
  registrations={begin:[{'owner':r['index'],'namespace':r['namespace'],'type':r['name'],'method':m} for r in records for m in r['methods'] if m['registeredAddress']==begin] for begin in valid_begins}
  result['validCallerRegistrations']=registrations
  if caller is not None:
   matches=registrations[caller]
   result['callerRegistrations']=matches
   if len(matches)==1:
    registration=pe.offset(CODE_REGISTRATION,0x78); pointer_base=number(binary,registration+0x70,'Q')
    pointer_slot=pointer_base+matches[0]['method']['index']*8; slot_off=pe.offset(pointer_slot,8); slot_raw=binary[slot_off:slot_off+8]
    check_aot(slot_raw, caller)
    (destination/'caller-slot.bin').write_bytes(slot_raw)
    result['callerAOT']={'registrationCell':hex(CODE_REGISTRATION+0x70),'pointerBase':hex(pointer_base),
                         'slot':hex(pointer_slot),'slotHex':slot_raw.hex(),'methodIndex':matches[0]['method']['index']}
   else: result['outcome']='unique-caller-unbound'
  result.update(stage='complete',passed=True)
  save(destination/'extraction.private.json',result)
  print(json.dumps({'rawCandidates':len(candidates),'validRefs':len(valid),'outcome':result['outcome'],'windows':len(rows)}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e)); save(destination/'failure.private.json',result); raise


def main():
 parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',required=True); args=parser.parse_args()
 require(Path(args.output).name == args.output and not (OUT/args.output).exists(),'new task-local basename required')
 OUT.mkdir(parents=True,exist_ok=True); dest=OUT/args.output; dest.mkdir(); collect(dest)

if __name__=='__main__': main()
