"""Read-only guards for the single synthetic WinHTTP ETW run; never starts a session."""
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'local/migration72/d02-active/sdk-etw-observer/winhttp'

class WinHttpEvidenceTests(unittest.TestCase):
    def test_single_run_network_and_teardown(self):
        text=(BASE/'actual-once.log').read_text(encoding='ascii')
        self.assertEqual((BASE/'actual-once-exit.txt').read_text(encoding='ascii'),'0')
        self.assertRegex(text,r'loopback_port=\d+')
        port=int(re.search(r'loopback_port=(\d+)',text).group(1))
        self.assertNotIn(port,(8088,22101,27017,27027))
        for expected in ('start=0','enable=0','request_get_ok=1 status=207 error=0',
                         'request_post_ok=1 status=208 error=0','server_wait=0 served=2 request_checks=2 server_errors=0 listener_close_error=0',
                         'stop=0 query_after_stop=4201','port_rebind=ok error=0'):
            self.assertIn(expected,text)
        self.assertRegex(text,r'events=0 version0=0 version1=0')
        for forbidden in ('FAKE_QUERY','FAKE_HEADER','FAKE_BODY'):
            self.assertNotIn(forbidden,text)

    def test_zero_events_are_not_validated_field_observations(self):
        text=(BASE/'actual-once.log').read_text(encoding='ascii')
        self.assertIn('events=0',text)
        for key in ('path_query','field_header','field_body','status207','status208'):
            self.assertIn(key+'=0',text)
        # Those zeros are empty counters, not evidence that an event omitted data.

if __name__=='__main__': unittest.main()
