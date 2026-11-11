"""Independent synthetic checks for the bounded family collector."""
import copy
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from contextlib import nullcontext

import proud_scalar_initializer_family_window as mod


class FamilyWindowTest(unittest.TestCase):
    def setUp(self):
        self.base = 0x140000000
        self.table = b"".join(struct.pack("<III", entry-self.base, entry-self.base+0x20, 0x1000+i) for i, entry in enumerate(mod.EXPECTED.values()))
        self.diagnostics = mod.find_pdata(self.table, self.base, 0x2000)
        self.section = lambda va, size, executable: {"flags": 0x20000000}

    def test_frozen_family_and_aot(self):
        self.assertEqual(list(mod.EXPECTED), [131980, 131983, 131984, 131985, 131986, 131987, 131989, 131991, 131993])
        self.assertNotIn(0x149a75140, mod.EXPECTED.values())
        slots = {i: v.to_bytes(8, "little") for i,v in mod.EXPECTED.items()}
        cell = (0x1428a54d0).to_bytes(8, "little")
        self.assertEqual(mod.aot_check(cell, slots), 0x1428a54d0)
        slots[131980] = bytes(8)
        with self.assertRaises(ValueError): mod.aot_check(cell, slots)
        with self.assertRaises(ValueError): mod.aot_check(cell, {})

    def test_pdata_rejection(self):
        self.assertEqual(len(mod.admit(self.diagnostics, self.section)), 9)
        for mode in ("missing", "duplicate", "containing", "overlap", "excluded", "nonexec"):
            d = copy.deepcopy(self.diagnostics); section = self.section
            first, second = list(mod.EXPECTED)[:2]
            if mode == "missing": d[first]["exact"] = []
            if mode == "duplicate": d[first]["exact"].append(d[first]["exact"][0])
            if mode == "containing": d[first]["containing"] = []
            if mode == "overlap":
                for x in d[first]["exact"] + d[first]["containing"]: x["endExclusive"] = hex(mod.EXPECTED[second]+1); x["size"] = mod.EXPECTED[second]+1-mod.EXPECTED[first]
            if mode == "excluded":
                for x in d[131984]["exact"] + d[131984]["containing"]: x["endExclusive"] = "0x149a75141"; x["size"] = 0x61
            if mode == "nonexec":
                def section(*args): raise ValueError("nonexecutable")
            with self.subTest(mode=mode), self.assertRaises(ValueError): mod.admit(d, section)

    def test_all_extents_budget_before_body(self):
        rows = mod.admit(self.diagnostics, self.section)
        self.assertTrue(mod.budget(rows))
        larger = copy.deepcopy(rows); larger[0]["size"] = 513
        self.assertFalse(mod.budget(larger))
        cumulative = copy.deepcopy(rows)
        for row in cumulative: row["size"] = 250
        self.assertFalse(mod.budget(cumulative))
        self.assertEqual(sum(r["size"] for r in cumulative), 2250)

    def test_synthetic_operand_classification_and_boundaries(self):
        entry = 0x145870000; seed = 0x145872d30
        for opcode, expected in [(b"\x8b\x05", "read"), (b"\x89\x05", "write"), (b"\x48\x8d\x05", "address-only")]:
            code = opcode + struct.pack("<i", seed-(entry+len(opcode)+4))
            rows = mod.decode(code, entry); hit = mod.index_rows(rows)["seed"]
            self.assertEqual(hit[0]["operand"]["access"], expected)
            self.assertEqual(b"".join(bytes.fromhex(r["hex"]) for r in rows), code)
        rows = mod.decode(bytes.fromhex("898170e90000"), entry)
        hit = mod.index_rows(rows)["e970"][0]["operand"]
        self.assertEqual((hit["base"], hit["access"], hit["rootBindingProven"]), ("rcx", "write", False))
        unknown = [{"address": "0x1", "mnemonic": "synthetic", "call": False, "branch": False, "directTarget": None, "memory": [{"base": "rip", "displacement": "0x0", "ripTarget": "0x145872d30", "access": "unknown", "rawAccess": 0, "rootBindingProven": False}]}]
        self.assertEqual(mod.index_rows(unknown)["seed"][0]["operand"]["access"], "unknown")
        with self.assertRaises(ValueError): mod.decode(b"\x0f", entry)

    def test_refused_edge_record_only(self):
        entry = 0x149a75020; target = 0x149a75140
        code = b"\xe8" + struct.pack("<i", target-entry-5)
        hits = mod.index_rows(mod.decode(code, entry))
        self.assertEqual(hits["refusedEdges"][0]["target"], "0x149a75140")
        self.assertEqual(len(hits["edges"]), 1)


class GateOrderTest(unittest.TestCase):
    def run_collector(self, sizes, decode_rows=None, expected_reads=1):
        with tempfile.TemporaryDirectory(dir=mod.OUT) as tmp:
            root = Path(tmp); (root / "YuanShen.exe").write_bytes(b"")
            dest = root / "output"; dest.mkdir()
            pointer = 0x1428a54d0
            entries = list(mod.EXPECTED.items())
            table = b"".join(struct.pack("<III", entry-0x140000000, entry-0x140000000+size, 0x1000+n) for n, ((_, entry), size) in enumerate(zip(entries, sizes)))
            class FakePE:
                base = 0x140000000; pdata_rva = 0x1a4e6a40; pdata_size = 14344080
                body_reads = 0
                def __init__(self, f): pass
                def section(self, *args): return {"flags": 0x20000000}
                def at(self, va, size, executable=False):
                    if va == 0x1422f9950: return pointer.to_bytes(8, "little")
                    for i, entry in entries:
                        if va == pointer+i*8: return entry.to_bytes(8, "little")
                    if va == self.base+self.pdata_rva: return table
                    FakePE.body_reads += 1
                    return bytes(size)
            req = {"fixedClientInputs": {}}
            selected = [{"size": size, "recordHex": table[n*12:n*12+12].hex()} for n, size in enumerate(sizes)]
            admission = patch.object(mod, "admit", return_value=selected) if decode_rows is None else nullcontext()
            with patch.object(mod, "source_gate", return_value=(req, {})), patch.object(mod.subprocess, "check_output", return_value=str(root)), patch.object(mod, "FilePE", FakePE), patch.object(mod, "decode", return_value=decode_rows or []), admission:
                if decode_rows is None:
                    self.assertEqual(mod.collect(dest), 2)
                    self.assertEqual(FakePE.body_reads, 0)
                    self.assertEqual(__import__("json").loads((dest/"result.private.json").read_bytes())["bodiesRead"], 0)
                    self.assertFalse(list(dest.glob("code-*")))
                else:
                    with self.assertRaisesRegex(ValueError, "instruction budget"): mod.collect(dest)
                    self.assertEqual(FakePE.body_reads, expected_reads)
                    self.assertTrue((dest/f"code-{entries[expected_reads-1][0]}.bin").exists())
                    self.assertIn("instruction budget", __import__("json").loads((dest/"failure.private.json").read_bytes())["error"])

    def test_per_body_budget_stops_before_read(self):
        self.run_collector([513]+[32]*8)

    def test_sum_budget_stops_before_read(self):
        self.run_collector([250]*9)

    def test_instruction_overflow_keeps_original(self):
        self.run_collector([32]*9, [{}]*129)

    def test_total_instruction_overflow_keeps_original(self):
        self.run_collector([32]*9, [{"memory": [], "call": False, "branch": False}]*110, expected_reads=5)


if __name__ == "__main__": unittest.main()
