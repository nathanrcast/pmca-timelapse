import os
import subprocess
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "soak_meminfo.sh")


def dry_run(*args):
    return subprocess.run(["bash", SCRIPT, *args, "--dry-run"], capture_output=True, text=True)


class SoakScriptTests(unittest.TestCase):
    def test_leading_zero_interval_is_decimal(self):
        for interval in ("08", "09", "010"):
            r = dry_run("camera", interval)
            self.assertEqual(0, r.returncode, r.stderr)
            self.assertIn("every %d min" % int(interval), r.stdout)

    def test_port_defaults_to_5555(self):
        self.assertIn("connect camera:5555", dry_run("camera").stdout)

    def test_bad_interval_is_rejected(self):
        self.assertEqual(2, dry_run("camera", "x").returncode)
        self.assertEqual(2, dry_run("camera", "0").returncode)


if __name__ == "__main__":
    unittest.main()
