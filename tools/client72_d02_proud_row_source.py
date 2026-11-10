"""Pin-bound static Proud row cache/producer/parser/path provenance audit."""
import argparse
import hashlib
import json
import pickle
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-row-source'
ROW = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-row-window'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
PINS = {
    META: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    SIG: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'tools/extract_client72_literals.py': '865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed',
}
ROW_PINS = {
    'boundary.private.json': 'df1af335e8424367758b19b75ae817048eeb17b72bbae1d52fb0a95086e92e13',
    'cell.bin': '68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da',
    'code.asm.private.json': '8284a5db767d1403d74bb9695bd659387cf108a978f649905d3dc8529766caa2',
    'code.bin': 'f413cdae898d87d07d7c92b0998c7ca6af334be147b174bebd9b668897f090cf',
    'extraction.private.json': '2645f3381c8c6732327b2bc1db8131d2f0fada8be13f432c635d5ed2eebdf1a4',
    'pdata.bin': '4f63e93282ff41e393f967367ff96b4bed6c6138d33b318e9c151b54fd61aef4',
    'pre-body.private.json': '891fe5fe0e9d86adef0a4d5b6b2a2b64f980c64a951877f67c546fc6562ecd1e',
    'slot.bin': '63c4941e06f07f2532304886c995b5b1dc932b1aeac8faff6e7c50486f3fad8f',
}
PRODUCER_PINS = {
    'boundary.private.json': '44dc13ceb84e9d03016f3b651ec3975b354a14c493678b80db5221983d9bf31e',
    'cell.bin': '68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da',
    'code.asm.private.json': '696c6c48402af545d97d79edc10736543b095f711c631f0c8aff8f97d4a59250',
    'code.bin': 'c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d',
    'extraction.private.json': '1fb7231629dbe1406f1e4e072f1ebb453b42e276fa1aa2fee5966bc5095feea3',
    'pdata.bin': 'ba6ba0b17532ce27ef947a445218fc4b4f46f2e4b95de835eaf4f965d030bee6',
    'pre-body.private.json': '5dad4938697600c4df05576f4defe8e92556e9e0b9401c7b8d50f02328d370d9',
    'slot.bin': 'c78ab6e2624aefb6a10c48386b9733a87b658615f8d57917dde9b911a1a52dcf',
}
PRIVATE_PINS = {
    'collect_typed_producer.private.py': '0bcd93534eb52ef049a0ab4451a5c268425cfc3df6bf7f28a0bbe112a502aa18',
    'collect_reader_literal.private.py': 'eaefb23db004ee58fe3ba52b8f7ef2bc2eefddebd756706035555617ab6eeea8',
    'reader-literal1.private.json': '9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
    'reader-literal2.private.json': '9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
}


def pinned(path, digest):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError('Fixed source SHA differs: ' + str(path.relative_to(ROOT)))
    return raw


def window(files, entry, end, count):
    """Bind the full static instructions, unique pdata and original AOT slot."""
    report = json.loads(files['extraction.private.json'])
    instructions = json.loads(files['code.asm.private.json'])
    code = files['code.bin']
    begin_rva, end_rva, _ = struct.unpack('<III', files['pdata.bin'])
    if ((begin_rva+0x140000000, end_rva+0x140000000) != (entry,end)
            or report['entry'] != hex(entry) or report['endExclusive'] != hex(end)
            or report['codeBytes'] != end-entry or len(code) != end-entry
            or report['instructions'] != count or len(instructions) != count
            or report['nativeCPU'] or report['liveReads'] or report['xdataBytesRead']):
        raise ValueError('Static window extent, count or boundary differs')
    diagnostic = report['exceptionDirectory']['diagnostic']
    if len(diagnostic['exact']) != 1 or diagnostic['exact'] != diagnostic['containing']:
        raise ValueError('Static window lacks unique exact pdata')
    if (int.from_bytes(files['slot.bin'],'little') != entry
            or files['slot.bin'].hex() != report['aot']['slotHex']
            or files['cell.bin'].hex() != report['aot']['cellHex']):
        raise ValueError('Original AOT QWORD differs')
    cursor, decoded = entry, {}
    for line in instructions:
        raw = bytes.fromhex(line['hex'])
        address = int(line['address'],16)
        if address != cursor or code[address-entry:address-entry+len(raw)] != raw:
            raise ValueError('Static instruction bytes or order differs')
        decoded[address] = line
        cursor += len(raw)
    if cursor != end:
        raise ValueError('Static decode incomplete')
    return report, decoded


def reader_literal(literal):
    """Recheck the selected usage, prefix, cipher and UTF-8 without current PE."""
    if (literal['cell'] != '0x145b29788' or literal['headerBytesRead'] != 32
            or literal['nativeCPU'] or literal['newCodeWindows'] or not literal['onlyOneLiteralDecoded']):
        raise ValueError('Literal scope differs')
    reg = literal['registration']
    source = literal['source']
    encoded0,encoded1 = struct.unpack('<II',bytes.fromhex(reg['sourceRecordHex']))
    key = (reg['usageRow']*0x57c15286+0x26ddebda)&0xffffffff
    original_source = ((encoded0+0xabbdcabf)&0xffffffff)^key^0x2a357d90
    slot = encoded1^key^0x1855aa65
    if (original_source>>29 != 5 or original_source&0x1fffffff != reg['index']
            or slot != reg['slot'] or int(literal['slotTableBase'],16)+slot*8 != 0x145b29788):
        raise ValueError('Source index or destination slot registration differs')
    cipher = bytes.fromhex(source['encryptedHex'])
    if (len(cipher) != source['encryptedBytes'] or 16+len(cipher) != source['recordAndCipherBytes']
            or source['recordAndCipherBytes'] > 256
            or hashlib.sha256(cipher).hexdigest() != source['encryptedSHA256']):
        raise ValueError('Selected literal cipher or bound differs')
    from tools.extract_client72_literals import prefix, decrypt
    prefix_raw = bytes.fromhex(source['prefixHex'])
    begin = prefix(int.from_bytes(prefix_raw[:4],'little'),reg['index'])
    end = prefix(int.from_bytes(prefix_raw[4:],'little'),reg['index']+1)
    plain = decrypt(cipher,reg['index'])
    if (end-begin != len(cipher) or plain.hex() != literal['decoded']['hex']
            or plain.decode('utf8') != literal['decoded']['value']
            or hashlib.sha256(plain).hexdigest() != literal['decoded']['sha256']):
        raise ValueError('Selected literal decoded byte provenance differs')
    return literal['decoded']['value']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True,help='new task-local JSON basename')
    args = parser.parse_args()
    output = OUT/args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new task-local JSON basename')
    fixed = {p:pinned(ROOT/p,digest) for p,digest in PINS.items()}
    private = {p:pinned(OUT/p,digest) for p,digest in PRIVATE_PINS.items()}
    row_copies = [{p:pinned(ROW/folder/p,digest) for p,digest in ROW_PINS.items()}
                  for folder in ('main-third','final3','final4')]
    producer_copies = [{p:pinned(OUT/folder/p,digest) for p,digest in PRODUCER_PINS.items()}
                       for folder in ('typed-producer1','typed-producer2')]
    if (row_copies[0] != row_copies[1] or row_copies[0] != row_copies[2]
            or producer_copies[0] != producer_copies[1]
            or private['reader-literal1.private.json'] != private['reader-literal2.private.json']):
        raise ValueError('Frozen fresh files differ')
    query_report,query = window(row_copies[0],0x14d7adf70,0x14d7ae274,152)
    producer_report,producer = window(producer_copies[0],0x14d7ad8e0,0x14d7adb3e,145)
    literal = json.loads(private['reader-literal1.private.json'])
    path = reader_literal(literal)
    for report in (query_report,producer_report):
        for p,proof in report['sources'].items():
            pinned(ROOT/p,proof['sha256'])
    for p,digest in literal['pins'].items():
        pinned(ROOT/p,digest)
    records,cells = pickle.loads(fixed[META])
    signatures = json.loads(fixed[SIG])
    by_index = {m['index']:m for m in signatures['methods']}
    methods = []
    for entry,owner,index in [('0x14d7adf70',80063,663540),('0x14d7ad8e0',80063,663546),
                              ('0x14bb5b0b0',71907,595516),('0x149f0e540',63831,529218),
                              ('0x149f0c1f0',63831,529236)]:
        matches = [(r['index'],m) for r in records for m in r['methods'] if m['registeredAddress']==entry]
        if len(matches)!=1 or (matches[0][0],matches[0][1]['index']) != (owner,index):
            raise ValueError('Unique method provenance differs')
        sig = by_index.get(index)
        types = [signatures['typeReferences'][str(p['typeIndex'])] for p in sig['parameters']] if sig else []
        methods.append(dict(owner=owner,method=matches[0][1],signature=sig,parameterTypes=types))
    out_type = methods[1]['parameterTypes'][1]
    parser_types = methods[4]['parameterTypes']
    if (not out_type['byRef'] or out_type.get('typeDefinition')!=63831
            or [(t['kind'],t.get('typeDefinition')) for t in parser_types] != [('class',42005),('class',11476)]
            or [t['kind'] for t in methods[2]['parameterTypes']] != ['string','int32','int32']
            or cells[0x14589fa38]['type']['typeDefinition']!=63831):
        raise ValueError('Typed row/reader/parser declaration differs')
    query_anchors = {
        0x14d7adf77:('mov','ebx, edx'),0x14d7adf79:('mov','esi, ecx'),
        0x14d7adfc0:('mov','rcx, qword ptr [rax + 0x21f68]'),
        0x14d7adfd0:('movzx','edi, bl'),0x14d7adfe6:('mov','r9d, edi'),
        0x14d7adfe9:('call','0x154db6ef0'),0x14d7adff0:('jne','0x14d7ae137'),
        0x14d7ae013:('lea','rdx, [rsp + 0x38]'),0x14d7ae018:('mov','ecx, esi'),
        0x14d7ae01a:('call','0x14d7ad8e0'),0x14d7ae05a:('cmp','qword ptr [rax + 0x21f60], 0'),
        0x14d7ae0b5:('call','0x1578b66f0'),0x14d7ae0bc:('js','0x14d7ae0e1'),
        0x14d7ae0da:('mov','rax, qword ptr [rcx + rax*8 + 0x30]'),
        0x14d7ae0e1:('xor','eax, eax'),0x14d7ae12f:('mov','r9d, edi'),
        0x14d7ae132:('call','0x154db5800'),0x14d7ae137:('mov','rax, qword ptr [rsp + 0x30]'),
    }
    producer_anchors = {
        0x14d7ad904:('mov','qword ptr [rdx], 0'),0x14d7ad926:('mov','rcx, qword ptr [rax + 0x21f58]'),
        0x14d7ad93c:('mov','edx, edi'),0x14d7ad941:('call','0x157ffc4c0'),
        0x14d7ad948:('je','0x14d7ada97'),0x14d7ad962:('mov','rdi, qword ptr [rip - 0x7c841e1]'),
        0x14d7ad969:('mov','ebx, dword ptr [rbp - 0x30]'),0x14d7ad96c:('mov','esi, dword ptr [rbp - 0x2c]'),
        0x14d7ad98b:('call','0x14bb5b0b0'),0x14d7ad99d:('cmp','qword ptr [rax + 0x20], 0'),
        0x14d7ad9b6:('mov','ecx, dword ptr [rcx + 0x18]'),0x14d7ad9bd:('mov','rcx, qword ptr [rip - 0x7f0df8c]'),
        0x14d7ad9c4:('call','0x1405336c0'),0x14d7ad9cf:('call','0x149f0e540'),
        0x14d7ad9d4:('mov','qword ptr [r14], rdi'),0x14d7ad9e0:('mov','al, 1'),
        0x14d7ad9f3:('xor','r8d, r8d'),0x14d7ad9f6:('call','0x149f0c1f0'),
        0x14d7ada97:('mov','eax, esi'),
    }
    for decoded,anchors in ((query,query_anchors),(producer,producer_anchors)):
        if any((decoded[a]['mnemonic'],decoded[a]['operands'])!=expected for a,expected in anchors.items()):
            raise ValueError('Original source-flow anchor differs')
    fields = [f for f in records[80063]['fields'] if f['index'] in (400190,400191,400192)]
    result = dict(task='E-01/R3-PROUD-ROW-SOURCE',sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        sourcePins=PINS,rowWindowPins=ROW_PINS,producerWindowPins=PRODUCER_PINS,privatePins=PRIVATE_PINS,
        originalQueryEightFilesEqualAcrossThree=True,newProducerEightFilesEqualAcrossTwo=True,
        queryWindow=query_report,typedProducerWindow=producer_report,readerLiteral=literal,methodProvenance=methods,
        declaredCacheFields=fields,allocationClassCell=cells[0x14589fa38],rowLocator75077Fields=records[75077]['fields'],
        queryAnchors={hex(a):query[a] for a in query_anchors},producerAnchors={hex(a):producer[a] for a in producer_anchors},
        queryControl=dict(backendFlag='original byte14587c2c0, not the caller bool',
            zeroFlag='global+21f60 direct entry lookup; null dictionary/missing key returns0; helper bodies unexecuted',
            nonzeroFlag='global+21f68 first lookup; onALfalse calls typed U32/out63831 producer; successful nonnull out passed to subsequent same-cache call',
            callerBool='low byte copied and forwarded asR9D only to custom-cache calls; no use in the ordinary dictionary branch',
            zeroKey='no explicit key==0 branch in either complete body; downstream policy unknown',
            nullAndBounds='null cache/reader/array and out-of-range entry paths call original error helper addresses; no new execution'),
        rowProducer=dict(lookup='global+21f58 receives original U32 and8B out slot; declaration400190 constrains18192<U32,75077,47647,25030>; closed lookup target not audited',
            twoValues='raw first/second U32 toEDX/R8D of String/int32/int32 reader; signed interpretation/offset-size names unapproved',
            readerPath=path,readerEntry='0x14bb5b0b0',readerReturnType='not recovered; next parser expects typed42005',
            empty='nonzero reader+20 uses reader+28!=0; otherwise managed array reader+10 and its+18!=0; empty returnsfalse/outnull',
            object='allocate exact63831, invoke its ctor, assign out pointer, call parser529236/149f0c1f0(reader42005,INativeAllocator0)',
            returnFlow='successful nonempty branch presets true before parser; parser must return normally; EH/cleanup behavior not executed'),
        proudTablePathIdentityApproved=True,typedRowParserIdentityApproved=True,actualProudPayloadIdentityApproved=False,
        readerBodyAudited=False,rowLocatorIndexActualValuesObserved=False,parserExecuted=False,
        proudSkillGroupIdAliasApproved=False,secondU32LevelIdentityApproved=False,promoteThresholdApproved=False,
        gTimes100PlusLevelProven=False,resourceReady=False,completeAvatar=False,d02Complete=False,
        currentPEReadsByAudit=0,nativeCPU=0,liveReads=0,network=0,jvm=0,database=0)
    raw = (json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8')
    with output.open('xb') as f:f.write(raw)
    print(json.dumps(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),
                         path=path,staticInstructions=152+145,parserExecuted=False)))


if __name__=='__main__':
    sys.path.insert(0,str(ROOT))
    main()
