"""Bounded wire input for reviewed reader helpers, independent of any schema.

This is an explicit synthetic input model, not a claim to reproduce every error
accepted or thrown by the client's CodedInputStream implementation.
"""
import struct


class WireInput:
    def __init__(self, data):
        self.data, self.position, self.last_key = bytes(data), 0, 0
        self.limit = len(self.data)

    def take(self, length):
        if not 0 <= self.position <= self.limit <= len(self.data) or length < 0 or length > self.limit - self.position:
            raise ValueError('Truncated or invalid length')
        start = self.position; self.position += length
        return self.data[start:self.position]

    def varint(self):
        value = 0
        for index in range(10):
            byte = self.take(1)[0]
            if index == 9 and byte > 1:
                raise ValueError('Varint exceeds uint64')
            value |= (byte & 127) << (7 * index)
            if byte < 128: return value
        raise ValueError('Unterminated varint')

    def tag(self):
        if self.position == self.limit: self.last_key = 0; return 0
        key = self.varint()
        if not 0 < key >> 3 < 1 << 29 or key & 7 not in (0, 1, 2, 5):
            raise ValueError('Unsupported or invalid tag')
        self.last_key = key
        return key

    def blob(self):
        return self.take(self.varint())

    def skip(self):
        wire = self.last_key & 7
        if not self.last_key: raise ValueError('No current tag')
        if wire == 0: self.varint()
        elif wire == 1: self.take(8)
        elif wire == 2: self.blob()
        elif wire == 5: self.take(4)
        else: raise ValueError('Unsupported wire type')

    def double_bits(self):
        return struct.unpack('<Q', self.take(8))[0]
