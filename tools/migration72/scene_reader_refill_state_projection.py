"""Conditional source-bound reader field projection; external calls stay pending."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SPEC=ROOT/'local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/conditional-state-projection-spec.private.json'
SOURCE=ROOT/'local/migration72/d02-resume-20261111c/scene-reader-refill-window/fresh1/body.bin'
ASM=ROOT/'local/migration72/d02-resume-20261111c/scene-reader-refill-window/fresh1/body.asm.private.json'
OUT=ROOT/'local/migration72/d02-resume-20261111c/scene-refill-state-projection'
SPEC_SHA='b4d2dec7931b58e453f08edcc9e5d6a83f71d19e1e9a7dbb3cebd419ee5b4331'
BODY_SHA='dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999'
ASM_SHA='2f86e6324b5b82c4c3f5388144ed935d83e09180c8d1e006e30913184da18e48'
TOKEN=0xfedcba9876543210


def pin_sources():
    spec=SPEC.read_bytes();body=SOURCE.read_bytes();asm=ASM.read_bytes()
    if hashlib.sha256(spec).hexdigest()!=SPEC_SHA or len(spec)!=4870 or hashlib.sha256(body).hexdigest()!=BODY_SHA or hashlib.sha256(asm).hexdigest()!=ASM_SHA:
        raise ValueError('source/spec pin differs')
    parsed=json.loads(spec);lines=json.loads(asm);cursor=0x14f614760;raw=bytearray()
    for line in lines:
        if int(line['address'],16)!=cursor:raise ValueError('source discontinuity')
        code=bytes.fromhex(line['hex']);raw.extend(code);cursor+=len(code)
    if bytes(raw)!=body or cursor!=0x14f614946 or parsed['sourceSHA256']!=BODY_SHA:raise ValueError('source reconstruction differs')
    return parsed


def i32(value):
    value &= 0xffffffff
    return value-0x100000000 if value & 0x80000000 else value


def validate(data):
    if data.get('start')=='entry':
        if not set(data)<={'start','token','base','cursor','count','array','slot','field28'} or type(data.get('token')) is not int or data['token']!=0:
            raise ValueError('entry requires token zero')
        return data
    if set(data)!={'start','token','base','cursor','count','array','slot','field28'}:raise ValueError('input fields differ')
    if data['start'] not in ('entry','existing','adopt'):raise ValueError('start differs')
    if type(data['token']) is not int or not 0<=data['token']<=0xffffffffffffffff:raise ValueError('token must be U64')
    for key in ('base','cursor','count','field28'):
        v=data[key]
        if type(v) is not int or not -0x80000000<=v<=0xffffffff:raise ValueError(key+' must be I32 bits')
    for key in ('array','slot'):
        a=data[key]
        if a is not None and (not isinstance(a,dict) or set(a)!={'id','capacity'} or not isinstance(a['id'],str) or not a['id'] or type(a['capacity']) is not int or not 0<=a['capacity']<=0xffffffff):raise ValueError(key+' must be array identity/capacity')
    if data['start']=='existing' and (data['token']==0 or data['array'] is None or data['slot'] is None):raise ValueError('existing prefix requires token, slot and array')
    if data['start']=='adopt' and (data['token']==0 or data['array'] is not None):raise ValueError('adopt prefix requires token and null reader array')
    return {**data,'base':data['base']&0xffffffff,'cursor':data['cursor']&0xffffffff,'count':data['count']&0xffffffff,'field28':data['field28']&0xffffffff}


def project(data):
    state=validate(data)
    if state['start']=='entry':
        unknown='unread/unknown'
        before={'array':state.get('array',unknown),'baseBits':state.get('base',unknown),'cursorBits':state.get('cursor',unknown),'tokenU64':0,'field28Bits':state.get('field28',unknown)}
        return {'conditionalStart':'entry','naturalReachabilityProven':False,'naturalReady':False,'before':before,'after':dict(before),'unread':['array','base','cursor','count','slot','field28'],'unknown':['external call effects','valid byte count','array contents'],'sourcePCs':['0x14f61476b','0x14f614770','0x14f61492e'],'stop':{'kind':'local-return','pc':'0x14f61493a'},'readIntent':None}
    before={'array':state['array'],'baseBits':state['base'],'cursorBits':state['cursor'],'tokenU64':state['token'],'field28Bits':state['field28']}
    after=dict(before)
    result={'conditionalStart':state['start'],'naturalReachabilityProven':False,'naturalReady':False,'before':before,'after':after,'unknown':['external call effects','valid byte count','array contents'],'sourcePCs':[]}
    if state['start']=='existing':
        n=state['array']['capacity'];q=(state['cursor']+state['count'])&0xffffffff
        result['sourcePCs']=['0x14f6147b2','0x14f6147ca','0x14f6147cd','0x14f6147d1','0x14f6147d5','0x14f6147d8']
        result['comparison']={'sumBits':q,'sumSigned':i32(q),'capacitySigned':i32(n),'strictLess':i32(q)<i32(n)}
        if i32(q)<i32(n):
            result.update(stop={'kind':'local-return','pc':'0x14f61493a'},readIntent=None)
            return result
        result['sourcePCs']+=['0x14f6147de','0x14f6147e1']
        if i32(n)<i32(state['count']):
            result.update(stop={'kind':'pending-allocation','pc':'0x14f6147f1','requestedCountBits':state['count']},readIntent=None)
            return result
        after['baseBits']=(state['base']+state['cursor'])&0xffffffff;after['cursorBits']=0
        result['sourcePCs']+=['0x14f61490e','0x14f614911','0x14f614914','0x14f61491b','0x14f61491f']
    else:
        result['sourcePCs']=['0x14f6148ef','0x14f6148f3','0x14f6148f7','0x14f6148fa']
        after['array']=state['slot']
        if state['slot'] is None:
            result.update(stop={'kind':'pending-error-call','pc':'0x14f61493b'},readIntent=None)
            return result
        n=state['slot']['capacity']
        result['sourcePCs']+=['0x14f6148fc','0x14f614900','0x14f614903','0x14f614906','0x14f614908']
    offset=i32((after['baseBits']+after['cursorBits'])&0xffffffff)&0xffffffffffffffff
    result['sourcePCs']+=['0x14f614921','0x14f614926','0x14f614929']
    result.update(stop={'kind':'pending-native-read','pc':'0x14f614929'},readIntent={'tokenU64':state['token'],'arrayId':after['array']['id'],'arg3':0,'capacityBits':n,'offsetU64':offset})
    return result


def fixtures():
    A={'id':'A','capacity':8};common={'token':TOKEN,'base':100,'cursor':3,'count':4,'array':A,'slot':A,'field28':0x89abcdef}
    def case(name,start,**values):return {'name':name,'input':{**common,'start':start,**values}}
    return [{'name':'token_zero','input':{'start':'entry','token':0}},case('nonzero64_cache_less_than_capacity','existing'),case('exact_fit_refills','existing',count=5),case('count_exceeds_capacity','existing',count=9),case('adoption_preserves_initial_state','adopt',array=None,base=17,cursor=3,count=1),case('signed_offset','adopt',array=None,base=-2,cursor=1,count=1),case('adoption_null','adopt',array=None,slot=None),case('zero_count_at_end','existing',base=10,cursor=8,count=0),case('negative_count_bit_semantics','existing',base=10,cursor=3,count=-1),case('i32_wrap','existing',base=0,cursor=2147483646,count=4),case('base_wrap_sign_extension','existing',base=2147483647,cursor=1,count=8)]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);args=parser.parse_args()
    if Path(args.output).name!=args.output or (OUT/args.output).exists():raise ValueError('new task-local basename required')
    spec=pin_sources();cases=fixtures()
    if [x['name'] for x in cases]!=[x['name'] for x in spec['cases']]:raise ValueError('fixture names differ')
    result={'task':spec['task'],'specSHA256':SPEC_SHA,'sourceSHA256':BODY_SHA,'toolSHA256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'nativeExecution':False,'currentPEReads':0,'naturalReady':False,'cases':[{'name':x['name'],'result':project(x['input'])} for x in cases]}
    dest=OUT/args.output;dest.mkdir(parents=True);(dest/'projection.private.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n',encoding='utf8')
    print(json.dumps({'cases':len(cases),'naturalReady':False,'currentPEReads':0}))

if __name__=='__main__':main()
