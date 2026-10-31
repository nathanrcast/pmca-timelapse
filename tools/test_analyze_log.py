import contextlib
import io
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import analyze_log  # noqa: E402

FIXTURES = os.path.join(HERE, "..", "tests", "fixtures")
COLS = "run_id,slot,scheduled_ms,actual_ms,capture_done_ms,result,skip_reason,ss,iso,ev,bracket_idx,battery_pct,free_mb\n"


def run_main(path, *extra):
    out = io.StringIO()
    err = io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        rc = analyze_log.main([path] + list(extra))
    return rc, out.getvalue(), err.getvalue()


def write_temp(test, text):
    f = tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False)
    test.addCleanup(os.unlink, f.name)
    f.write(text)
    f.close()
    return f.name


class AnalyzeLogTest(unittest.TestCase):
    def test_fixture_matches_expected_output(self):
        rc, out, _ = run_main(os.path.join(FIXTURES, "sample.csv"))
        with open(os.path.join(FIXTURES, "sample.expected.txt")) as f:
            self.assertEqual(f.read(), out)
        self.assertEqual(0, rc)

    def test_drift_over_threshold_fails(self):
        rc, out, _ = run_main(os.path.join(FIXTURES, "sample.csv"), "--max-drift-ms", "10")
        self.assertEqual(1, rc)
        self.assertIn("FAIL: max drift 12 ms exceeds 10 ms", out)

    def test_skip_without_reason_is_unexplained(self):
        path = write_temp(self, COLS + "r,0,1000,,,SKIP,,,,,,,\nr,1,2000,2001,,FIRE,,,,,0,,\n")
        rc, out, _ = run_main(path)
        self.assertEqual(1, rc)
        self.assertIn("1 unexplained skip", out)

    def test_explained_skips_pass(self):
        path = write_temp(self, COLS + "r,0,1000,,,SKIP,LOST,,,,0,,\nr,1,2000,2001,,FIRE,,,,,0,,\n")
        rc, out, _ = run_main(path)
        self.assertEqual(0, rc)
        self.assertIn("skips: 1 (LOST=1)", out)

    def test_bracket_followups_are_not_drift(self):
        path = write_temp(self, COLS + "r,0,1000,1005,,FIRE,,,,,0,,\nr,0,1000,1900,,FIRE,,,,,1,,\n"
                          "r,0,1000,,2200,SLOT_DONE,,,,,1,,\n")
        rc, out, _ = run_main(path)
        self.assertEqual(0, rc)
        self.assertIn("drift ms: max 5", out)
        self.assertIn("capture ms p95: 300", out)

    def test_missing_file_and_bad_row(self):
        rc, _, err = run_main(os.path.join(FIXTURES, "nope.csv"))
        self.assertEqual(2, rc)
        rc, _, err = run_main(write_temp(self, COLS + "r,0,1\n"))
        self.assertEqual(2, rc)
        self.assertIn("expected 13 fields", err)

    def test_quoted_fields_and_runs_are_grouped(self):
        path = write_temp(self, COLS + 'a,0,0,1,,FIRE,,"1/2,5",,,0,,\nb,0,0,2,,FIRE,,,,,0,,\na,1,10,12,,FIRE,,,,,0,,\n')
        rc, out, _ = run_main(path)
        self.assertEqual(0, rc)
        self.assertEqual(2, out.count("run "))

    def test_header_comments_are_raw_text(self):
        settings = '{"interval":1.0,"note":"a,b","q":"x"y,"}'
        text = ("# run_id=r\n# model=M,\"X\n# settings=" + settings + "\n" + COLS
                + "r,0,1000,1001,,FIRE,,,,,0,,\nr,1,2000,2002,,FIRE,,,,,0,,\n")
        runs = analyze_log.read_runs(io.StringIO(text))
        self.assertEqual(1, len(runs))
        self.assertEqual(settings, runs[0].meta["settings"])
        self.assertEqual('M,"X', runs[0].meta["model"])
        self.assertEqual(2, len(runs[0].rows))

    def test_stray_quote_in_header_does_not_swallow_rows(self):
        path = write_temp(self, '# run_id=r\n# settings={"a":1,"b":"\n' + COLS + "r,0,1000,1001,,FIRE,,,,,0,,\n")
        rc, out, _ = run_main(path)
        self.assertEqual(0, rc)
        self.assertIn("shots: 1 fired", out)

    def test_system_clock_header_is_flagged(self):
        path = write_temp(self, "# run_id=r\n# clock=system\n" + COLS + "r,0,1000,1001,,FIRE,,,,,0,,\n")
        self.assertIn("system clock", run_main(path)[1])
        path = write_temp(self, "# run_id=r\n# clock=camera\n# android=4.1.2/16\n" + COLS
                          + "r,0,1000,1001,,FIRE,,,,,0,,\n")
        self.assertNotIn("system clock", run_main(path)[1])

    def test_percentile(self):
        self.assertEqual(95, analyze_log.percentile(list(range(1, 101)), 95))
        self.assertIsNone(analyze_log.percentile([], 95))


if __name__ == "__main__":
    unittest.main()
