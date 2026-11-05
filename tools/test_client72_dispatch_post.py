import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_dispatch_post import validate_summary


class DispatchPostTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/dispatch-post-observations.json').read_text('utf8'))

    def test_native_evidence(self):
        self.assertEqual(validate_summary(self.report), [])

    def test_rejects_unearned_completion_and_wrong_class(self):
        for key,value in [('wholeDispatchExecuted', True), ('semanticStatus', 'verified'),
                          ('runtimeStatus', 'passed'), ('regionTypeIndex', 30677)]:
            report = copy.deepcopy(self.report); report[key] = value
            self.assertTrue(validate_summary(report), key)

    def test_rejects_lost_merge_bytes_or_unverified_crypto(self):
        mutations = [(0,'configInputs',dict(receiverPreserved=True,mode=1,arrays=['',''])),
                     (3,'regionAllocations',2), (3,'regionReaderCalls',1),
                     (2,'region',dict(typeIndex=67961,uint32=0,text='')),
                     (0,'sessionB0Unchanged',False), (0,'keyChecks',[])]
        for index,key,value in mutations:
            report = copy.deepcopy(self.report); report['results'][index][key] = value
            self.assertTrue(validate_summary(report), key)

    def test_rejects_error_paths_claiming_success(self):
        for index,key,value in [(4,'configInputs',{}), (5,'postParsePointerPreserved',False),
                                (6,'postParsePointerPreserved',True), (7,'wireError',None)]:
            report = copy.deepcopy(self.report); report['results'][index][key] = value
            self.assertTrue(validate_summary(report), key)


if __name__ == '__main__': unittest.main()
