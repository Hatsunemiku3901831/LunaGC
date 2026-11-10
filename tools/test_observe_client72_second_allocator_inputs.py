"""Second-profile adapter tests using artificial handles and memory only."""

from copy import deepcopy
from itertools import product
import unittest
from unittest.mock import patch

from tools import client72_d02_second_allocator_inputs as second
from tools import client72_d02_tls_inputs as core
from tools import observe_client72_tls_inputs as adapter
from tools.test_client72_d02_second_allocator_inputs import D, SecondMemory
from tools.test_client72_d02_tls_inputs import TEB
from tools.test_observe_client72_tls_inputs import ArtificialBackend, basic, epoch


SOURCES = (
    "tools/client72_d02_tls_inputs.py",
    "tools/observe_client72_tls_inputs.py",
    "tools/client72_d02_tls_pool_inputs.py",
    "tools/client72_d02_avatar_block_inputs.py",
    "tools/client72_d02_avatar_ctor_inputs.py",
    "tools/client72_d02_second_allocator_inputs.py",
)
FLAGS = dict(include_pool=True, include_allocator_inputs=True,
             include_ctor_inputs=True, include_second_allocator_inputs=True)


class SecondAdapterArtificialTests(unittest.TestCase):
    def setUp(self):
        self.backend = ArtificialBackend()
        self.backend.memory = SecondMemory()
        self.epoch = epoch()
        self.epoch["implementationPins"] = {
            name: core.digest((core.ROOT / name).read_bytes()) for name in SOURCES
        }
        self.identities = [deepcopy({name: self.epoch[name] for name in
            ("pid", "image", "processCreationTime", "module")}) for _ in range(2)]
        self.identity_calls = []
        self.gate = patch.object(second, "verify_sources", return_value={"artificialFixture": True}).start()
        self.addCleanup(patch.stopall)

    def identity(self, process, pid):
        self.identity_calls.append((process, pid))
        return self.identities.pop(0)

    def run_sample(self, **flags):
        return adapter.sample_primary(self.backend, self.epoch, self.identity,
                                      **(flags if flags else FLAGS))

    def assert_entry_stop(self, result):
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["failureStage"], "entry")
        self.assertEqual(self.identity_calls, [])
        self.assertEqual(self.backend.calls, ["CloseHandle-primary"])
        self.assertEqual(self.backend.memory.calls, [])
        self.assertEqual(self.backend.closed, [818])

    def test_full_graph_uses_six_pins_full64_identity_and_api_budget(self):
        result = self.run_sample()
        self.assertEqual(result["status"], "candidate")
        self.assertEqual(result["profile"], "avatar-r1-pool-head-ctor-second")
        self.assertEqual(result["implementationPins"], self.epoch["implementationPins"])
        self.assertEqual(len(result["implementationPins"]), 6)
        self.gate.assert_called_once_with()
        self.assertEqual(self.identity_calls, [(919, 37), (919, 37)])
        self.assertTrue(result["endIdentityVerified"])
        self.assertEqual(result["apiCounts"], adapter.API_LIMITS)
        self.assertEqual(result["apiRequests"], adapter.API_LIMITS)
        self.assertEqual(result["threadBasicOutputBytes"], 96)
        self.assertEqual(result["threadBefore"]["decoded"]["teb"], TEB)
        self.assertEqual(result["collection"]["ctorInputs"]["default"]["value"], D)
        self.assertGreater(D, 0xffffffff)
        self.assertEqual(result["collection"]["budget"]["reads"], 88)
        self.assertEqual(result["collection"]["budget"]["requestedBytes"], 2368)
        self.assertEqual(len(self.backend.memory.calls), 88)
        self.assertEqual(self.backend.closed, [818])
        self.assertFalse(result["liveApproved"])
        self.assertFalse(result["canReplayPop"])

    def test_each_missing_or_drifted_pin_rejected_before_identity(self):
        pins = deepcopy(self.epoch["implementationPins"])
        for name in SOURCES:
            for change in ("missing", "drift"):
                with self.subTest(name=name, change=change):
                    self.setUp()
                    self.epoch["implementationPins"] = deepcopy(pins)
                    if change == "missing":
                        del self.epoch["implementationPins"][name]
                    else:
                        self.epoch["implementationPins"][name] = "0" * 64
                    self.assert_entry_stop(self.run_sample())
                    self.gate.assert_not_called()

    def test_source_gate_failure_precedes_identity_thread_and_read(self):
        self.gate.side_effect = ValueError("artificial source gate failure")
        self.assert_entry_stop(self.run_sample())
        self.gate.assert_called_once_with()

    def test_second_requires_each_prior_profile_and_explicit_boolean(self):
        for pool, head, ctor in product((False, True), repeat=3):
            if pool and head and ctor:
                continue
            with self.subTest(pool=pool, head=head, ctor=ctor):
                self.setUp()
                flags = dict(include_pool=pool, include_allocator_inputs=head,
                             include_ctor_inputs=ctor, include_second_allocator_inputs=True)
                self.assert_entry_stop(self.run_sample(**flags))
        for value in (1, None, "true"):
            with self.subTest(value=value):
                self.setUp()
                self.assert_entry_stop(self.run_sample(**{**FLAGS, "include_second_allocator_inputs": value}))

    def test_first_or_last_read_failure_is_partial_without_retry(self):
        for ordinal in (1, 88):
            with self.subTest(ordinal=ordinal):
                self.setUp()
                self.backend.memory.faults[ordinal] = (b"", {"winerror": 5})
                result = self.run_sample()
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["failureStage"], "collection")
                self.assertEqual(len(self.backend.memory.calls), ordinal)
                self.assertEqual(result["collection"]["budget"]["reads"], ordinal)
                self.assertEqual(self.backend.closed, [818])
                self.assertEqual(result["apiCounts"]["NtQueryInformationThread"], 2)
                self.assertEqual(self.identity_calls, [(919, 37), (919, 37)])

    def test_identity_before_after_teb_and_close_failures_are_partial(self):
        for scenario in ("identity-before", "identity-after", "teb-before", "teb-after", "close"):
            with self.subTest(scenario=scenario):
                self.setUp()
                if scenario.startswith("identity"):
                    self.identities[0 if scenario.endswith("before") else 1]["module"]["base"] += 1
                elif scenario == "teb-before":
                    self.backend.basics[0] = basic(teb=0)
                elif scenario == "teb-after":
                    self.backend.basics[1] = basic(teb=TEB + 0x1000)
                else:
                    self.backend.fail["CloseHandle-primary"] = {"attempted": True, "result": 0, "lastError": 6, "handle": 818}
                result = self.run_sample()
                self.assertEqual(result["status"], "partial")
                self.assertEqual(result["failureStage"], {
                    "identity-before": "identity-before", "identity-after": "identity-after",
                    "teb-before": "thread-before", "teb-after": "thread-after", "close": "cleanup"
                }[scenario])
                self.assertEqual(self.backend.closed, [818])
                self.assertLessEqual(len(self.backend.memory.calls), 88)
                self.assertLessEqual(result["apiCounts"]["NtQueryInformationThread"], 2)
                self.assertEqual(result["apiCounts"]["CloseHandle-primary"], 1)
                self.assertTrue(all(name in adapter.API_LIMITS for name in self.backend.calls))
                if scenario in ("identity-before", "teb-before"):
                    self.assertEqual(self.backend.memory.calls, [])
                else:
                    self.assertEqual(len(self.backend.memory.calls), 88)


if __name__ == "__main__":
    unittest.main()
