import contextlib
import io
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import meminfo_trend  # noqa: E402


def old_block(native, dalvik):
    return (
        "Applications Memory Usage (kB):\n"
        "Uptime: 100 Realtime: 100\n\n"
        "** MEMINFO in pid 1234 [io.github.nathanrcast.timelapse] **\n"
        "                    native   dalvik    other    total\n"
        "            size:    10240     8199      N/A    18439\n"
        "       allocated:     %d     %d      N/A    15163\n"
        "            free:      239     2968      N/A     3207\n" % (native, dalvik)
    )


def new_block(native, dalvik):
    return (
        "** MEMINFO in pid 1 [x] **\n"
        "                Pss     Pss  Shared Private  Shared Private    Heap    Heap    Heap\n"
        "              Total   Clean   Dirty   Dirty   Clean   Clean    Size   Alloc    Free\n"
        "Native Heap     9000       0    100    8000       0       0   10240 %6d     300\n"
        "Dalvik Heap     5000       0    100    4000       0       0    8000 %6d     800\n" % (native, dalvik)
    )


def log(blocks):
    out = ""
    for i, b in enumerate(blocks):
        out += "=== 2026-10-30T%02d:%02d:00Z ===\n%s" % (i // 2, (i % 2) * 30, b)
    return out


def write_temp(test, text):
    f = tempfile.NamedTemporaryFile("w", suffix=".log", delete=False)
    test.addCleanup(os.unlink, f.name)
    f.write(text)
    f.close()
    return f.name


def run_main(path, *extra):
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        rc = meminfo_trend.main([path] + list(extra))
    return rc, out.getvalue(), err.getvalue()


class ParseTests(unittest.TestCase):
    def test_android_23_allocated_row(self):
        self.assertEqual({"native": 9932, "dalvik": 5231}, meminfo_trend.parse_sample(old_block(9932, 5231).splitlines()))

    def test_later_heap_rows_use_alloc_column(self):
        self.assertEqual({"native": 7000, "dalvik": 3000}, meminfo_trend.parse_sample(new_block(7000, 3000).splitlines()))

    def test_samples_are_split_on_stamps(self):
        samples = meminfo_trend.parse_log(log([old_block(1, 2), old_block(3, 4)]))
        self.assertEqual(2, len(samples))
        self.assertEqual(4, samples[1][1]["dalvik"])

    def test_unreadable_block_has_no_heaps(self):
        self.assertEqual({}, meminfo_trend.parse_sample(["error: device offline"]))


class MainTests(unittest.TestCase):
    def test_flat_with_gc_noise_passes(self):
        path = write_temp(self, log([old_block(9000 + (i % 3) * 50, 5000 + (i % 4) * 100) for i in range(8)]))
        rc, out, _ = run_main(path)
        self.assertEqual(0, rc)
        self.assertIn("ok", out)

    def test_monotonic_growth_above_threshold_fails(self):
        path = write_temp(self, log([old_block(9000, 5000 + i * 600) for i in range(8)]))
        rc, out, _ = run_main(path)
        self.assertEqual(1, rc)
        self.assertIn("dalvik", out)
        self.assertIn("GROWTH", out)
        self.assertIn("kB/h", out)

    def test_monotonic_growth_below_threshold_passes(self):
        path = write_temp(self, log([old_block(9000 + i, 5000) for i in range(8)]))
        self.assertEqual(0, run_main(path)[0])

    def test_threshold_is_adjustable(self):
        path = write_temp(self, log([old_block(9000 + i * 10, 5000) for i in range(8)]))
        self.assertEqual(1, run_main(path, "--threshold-kb", "50")[0])

    def test_a_leak_with_one_gc_dip_still_fails(self):
        vals = [3000, 4500, 4400, 6000, 8000, 10000]
        path = write_temp(self, log([old_block(9000, v) for v in vals]))
        rc, out, _ = run_main(path)
        self.assertEqual(1, rc)
        self.assertIn("dalvik", out)

    def test_sawtooth_around_a_flat_level_passes(self):
        vals = [5000, 7000, 5100, 7100, 5000, 7200, 5100, 7000]
        path = write_temp(self, log([old_block(9000, v) for v in vals]))
        self.assertEqual(0, run_main(path)[0])

    def test_quartile_rule_catches_a_step_the_slope_underrates(self):
        vals = [5000] * 6 + [8000] * 2
        path = write_temp(self, log([old_block(9000, v) for v in vals]))
        self.assertEqual(1, run_main(path)[0])

    def test_newer_format_native_growth_fails(self):
        path = write_temp(self, log([new_block(4000 + i * 800, 3000) for i in range(6)]))
        rc, out, _ = run_main(path)
        self.assertEqual(1, rc)
        self.assertIn("native", out)

    def test_too_few_samples_exits_2(self):
        path = write_temp(self, log([old_block(1, 2), old_block(3, 4)]))
        rc, _, err = run_main(path)
        self.assertEqual(2, rc)
        self.assertIn("not enough data", err)

    def test_missing_file_exits_2(self):
        self.assertEqual(2, run_main("/nonexistent/soak.log")[0])


if __name__ == "__main__":
    unittest.main()
