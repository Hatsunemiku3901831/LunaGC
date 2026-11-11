"""Independent literal regression for the conditional two-field projection."""
import copy
import json
import unittest
from pathlib import Path

import proud_conditional_scalar_projection as projection


class ProjectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bridge, cls.forward, cls.inverse, cls.seed = projection.load_inputs()

    def test_fields_and_actual_unknown(self):
        result = projection.project(self.bridge, self.forward, self.inverse, self.seed)
        expected = [
            (41105, 203376, "0x90", "0x30", [218, 222], "P", 67108864, True, "2db5fdf6", 221, 221, "403d555f", 221, None, None, False),
            (41105, 203373, "0x84", "0x38", [226, 230], "rawH", 2048, True, "7c7c4543", 1, 1, "403d55c4", 1, None, None, False),
        ]
        keys = ("owner", "fieldIndex", "managedOffset", "nativeOffset", "span", "presenceSource", "presenceMask", "present", "rawHex", "setterInputU32", "conditionalDecodedU32", "fixtureEncodedHex", "fixtureGetterU32", "actualEncoded", "actualGetterU32", "actualManagedObject")
        self.assertEqual(result["key"], 22101)
        self.assertEqual([tuple(f[k] for k in keys) for f in result["fields"]], expected)
        self.assertEqual(result["scope"], {"actualEncoded": None, "actualGetterU32": None, "actualManagedObject": False, "actualQueryWrapperBaseOrLifetimeObserved": False, "actualPairMapHit": False, "currentAdoptionProven": False, "resourceReady": False, "d02Complete": False, "targetCPU": 0, "live": 0, "PERead": 0, "globalRead": 0})

    def test_invalid_bridge_and_pin_rejected(self):
        changes = [lambda b: b.update(key=1), lambda b: b["fields"][0]["managedField"].update(index=203373), lambda b: b["fields"][0]["fileToken"].update(span=[218, 223]), lambda b: b["fields"][0]["fileToken"].update(rawHex="00000000"), lambda b: b["fields"][0]["fileToken"]["presence"].update(present=False), lambda b: b["fields"][0]["fileToken"]["presence"].pop("present"), lambda b: b["fields"][1]["fileToken"]["presence"].update(source="P"), lambda b: b["fields"][1]["fileToken"]["presence"].update(mask=0)]
        for change in changes:
            bridge = copy.deepcopy(self.bridge)
            change(bridge)
            with self.subTest(change=change), self.assertRaises((KeyError, ValueError)):
                projection.validate_bridge(bridge)
        path = projection.BRIDGE
        data = (projection.ROOT / path).read_bytes()
        with self.assertRaises(ValueError):
            projection.verify_blob(data + b"x", projection.PINS[path])
        with self.assertRaises(ValueError):
            projection.verify_blob(b"x" + data[1:], projection.PINS[path])

    def test_literal_oracle_vectors(self):
        vectors = [(0, "403d5515"), (1, "403d55c4"), (221, "403d555f"), (0x100, "403dac15"), (0x10000, "40155515"), (0x1000000, "043d5515"), (0x12345678, "7c6fd922"), (0x80000000, "d03d5515"), (0x80000001, "d03d55c4"), (0xffffffff, "9afd1426")]
        oracle = json.loads((projection.ROOT / "local/migration72/d02-resume-20261111c/proud-setter-semantics/fixture-oracle.private.json").read_bytes())
        self.assertEqual([(v["inputU32"], v["fixtureEncodedHex"]) for v in oracle["vectors"]], vectors)
        for value, expected in vectors:
            with self.subTest(value=value):
                encoded = projection.setter(value, self.forward, self.seed)
                self.assertEqual(f"{encoded:08x}", expected)
                self.assertEqual(projection.getter(encoded, self.inverse, self.seed), value)
        self.assertTrue(all(self.inverse[j][self.forward[j][b]] == b for j in range(4) for b in range(256)))

    def test_failed_inverse_and_seed_conditions(self):
        changed = list(self.forward)
        quarter = bytearray(changed[3])
        quarter[221] ^= 1
        changed[3] = bytes(quarter)
        self.assertNotEqual(self.inverse[3][changed[3][221]], 221)
        self.assertNotEqual(projection.getter(projection.setter(221, changed, self.seed), self.inverse, self.seed), 221)
        encoded = projection.setter(221, self.forward, self.seed)
        self.assertNotEqual(self.seed, self.seed ^ 1)
        self.assertNotEqual(projection.getter(encoded, self.inverse, self.seed ^ 1), 221)

    def test_static_stop_boundaries(self):
        for length, pc in [(0, "0x14aefa0b7"), (0x100, "0x14aefa0cc"), (0x200, "0x14aefa0db"), (0x300, "0x14aefa0ea")]:
            self.assertEqual(projection.static_stop("setter", 0, length), {"stopPC": pc, "payloadRead": False, "seedRead": False, "storedRead": False, "targetWrite": False, "errorCall": "0x1404c2650"})
        for length, pc in [(0x400, "0x14aef9de7"), (0x500, "0x14aef9dfb"), (0x600, "0x14aef9e0a"), (0x700, "0x14aef9e19")]:
            self.assertEqual(projection.static_stop("getter", 0, length), {"stopPC": pc, "payloadRead": False, "seedRead": True, "storedRead": True, "targetWrite": False, "errorCall": "0x1404c2650"})
        self.assertEqual(projection.static_stop("setter", 0, 0x301)["stopPC"], None)
        self.assertEqual(projection.static_stop("getter", 0, 0x701)["stopPC"], None)
        self.assertEqual(projection.static_stop("setter", 0xffffffff, 0x3ff)["stopPC"], "0x14aefa0ea")
        self.assertEqual(projection.static_stop("setter", 0xffffffff, 0x400)["stopPC"], None)
        self.assertEqual(projection.static_stop("getter", 0xffffffff, 0x7ff)["stopPC"], "0x14aef9e19")
        self.assertEqual(projection.static_stop("getter", 0xffffffff, 0x800)["stopPC"], None)
        for kind, pc in [("setter", "0x14aefa127"), ("getter", "0x14aef9e4e")]:
            self.assertEqual(projection.static_stop(kind, 0, None), {"stopPC": pc, "payloadRead": False, "seedRead": False, "storedRead": False, "targetWrite": False, "errorCall": "0x1404c2be0"})


if __name__ == "__main__":
    unittest.main()
