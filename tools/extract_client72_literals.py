"""Selected CN 7.2 literals and their real metadata-usage destinations.

The source-index and destination-slot are decoded separately; they are never
assumed equal. No client process, old profile or unrestricted string dump.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import PE, HASHES, HEADER_VA, BODY, MASK32, MASK64, number, span, read_verified
except ImportError:
    from extract_client72 import PE, HASHES, HEADER_VA, BODY, MASK32, MASK64, number, span, read_verified

USAGE_REGISTRATION = 0x1428A5460
SELECTED = (46063, 46128, 46129, 46140)


def prefix(encoded, index):
    if type(index) is not int or not 0 <= index <= 0x1fffffff:
        raise ValueError('Literal index outside 29 bits')
    key = ((((index * 0x84fd) ^ 0x44a4de7c) * 0x2a04e474 & MASK64) >> 23) ^ 0xc6a574cc
    return ((encoded ^ 0x732e7f05) + key + 0xc5e65791) & MASK32


def decrypt(cipher, index):
    prefix(0, index)  # Validate the integer domain, including bool.
    key = ((((index * 0x59d948494a127f4a + 0x9dd295036e0638ca) & MASK64)
            ^ 0x580c38b94622ef76) + 0x4165529c1f55fc4e) & MASK64
    return b''.join((int.from_bytes(cipher[p:p+8], 'little') ^
                    ((key + p//8 * 0x7233cad939d03cbc) & MASK64)).to_bytes(8, 'little')[:len(cipher[p:p+8])]
                    for p in range(0, len(cipher), 8))


class Literals72:
    def __init__(self, binary, metadata):
        self.metadata = metadata
        pe = PE(binary); h = pe.offset(HEADER_VA); reg = pe.offset(USAGE_REGISTRATION)
        self.prefix_base = BODY + ((number(binary, h+0x1e4) + 0x9deb89a5) & MASK32)
        self.data_base = BODY + (number(binary, h+0x124) ^ 0x36c4f639)
        self.groups_base = BODY + (number(binary, h+0x178) ^ 0x25af313e)
        self.usages_base = BODY + (number(binary, h+0xf4) ^ 0x43310f1f)
        self.slots_base = number(binary, reg+0x58, 'Q')
        self.slot_count = (number(binary, reg+4) + 0x9e6f867a) & MASK32
        self.global_group = (number(binary, reg+0x54) + 0xd2070cd2) & MASK32
        for base in (self.prefix_base, self.data_base, self.groups_base, self.usages_base):
            span(metadata, base, 0)

    def payload(self, index):
        # A selected source index is bounded against the actual metadata span.
        # slot_count is NOT asserted to be a source-string registration count.
        prefix(0, index)
        start = prefix(number(self.metadata, self.prefix_base+index*4), index)
        end = prefix(number(self.metadata, self.prefix_base+(index+1)*4), index+1)
        if end < start or end-start > 1 << 20:
            raise ValueError('Literal prefix range invalid or oversized')
        span(self.metadata, self.data_base+start, end-start)
        return self.metadata[self.data_base+start:self.data_base+end]

    def decode(self, index):
        return decrypt(self.payload(index), index).decode('utf8')

    def group(self, index):
        def first(i):
            return ((number(self.metadata, self.groups_base+i*4)+0x8bff3e05) & MASK32) ^ ((i*0x5843eaa0-0x443c8630) & MASK32)
        start, end = first(index), first(index+1)
        if end < start:
            raise ValueError('Usage group reversed')
        span(self.metadata, self.usages_base+start*8, (end-start)*8)
        rows = []
        for i in range(start, end):
            key = (i*0x57c15286+0x26ddebda) & MASK32
            source = ((number(self.metadata, self.usages_base+i*8)+0xabbdcabf) & MASK32) ^ key ^ 0x2a357d90
            slot = number(self.metadata, self.usages_base+i*8+4) ^ key ^ 0x1855aa65
            if source >> 29 != 5:
                continue
            if slot >= self.slot_count:
                raise ValueError('Literal destination slot out of bounds')
            rows.append(dict(usageRow=i, index=source & 0x1fffffff, slot=slot,
                             cell=hex(self.slots_base+slot*8)))
        return rows

    def selected(self, indices=SELECTED):
        selected = [dict(row, value=self.decode(row['index'])) for row in self.group(self.global_group)
                    if row['index'] in indices]
        if {r['index'] for r in selected} != set(indices):
            raise ValueError('Selected literals absent from global initialization group')
        if len({r['slot'] for r in selected}) != len(selected):
            raise ValueError('Duplicate selected literal destination')
        return dict(schemaVersion=1, inputSHA256=HASHES, globalGroup=self.global_group,
                    literalSlotCount=self.slot_count, literals=selected,
                    scope='Selected compile-time literals; not live client logs')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    a=p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()): p.error('Output must be outside client')
    b,m,_=[read_verified(a.client/n,h) for n,h in HASHES.items()]
    report=Literals72(b,m).selected()
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(dict(selectedLiterals=len(report['literals']),globalGroup=report['globalGroup'])))


if __name__=='__main__': main()
