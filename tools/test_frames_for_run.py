import contextlib
import io
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import frames_for_run  # noqa: E402

ROOT = os.path.join(HERE, "..")
FIXTURE_JPEGS = os.path.join(ROOT, "tests", "fixtures", "jpegs")
WALL = 1761814800000
HEADER = "run_id,slot,scheduled_ms,actual_ms,capture_done_ms,result,skip_reason,ss,iso,ev,bracket_idx,battery_pct,free_mb\n"


def row(run, slot, result, actual="", done="", sched=""):
    return "%s,%d,%s,%s,%s,%s,,,,,0,100,1000\n" % (run, slot, sched, actual, done, result)


def log_text():
    text = "# run_id=A\n# uptime_to_wall_ms=%d\n" % WALL + HEADER
    text += row("A", 0, "FIRE", 100000) + row("A", 0, "SLOT_DONE", done=100400)
    text += row("A", 1, "SKIP", sched=110000).replace(",,,,,SKIP", ",,,,,SKIP")
    text += row("A", 2, "FIRE", 120000) + row("A", 2, "SLOT_DONE", done=120300)
    text += "# run_id=B\n# uptime_to_wall_ms=%d\n" % (WALL + 1000000)
    text += row("B", 0, "FIRE", 50000)
    return text


class Tree(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        self.dcim = os.path.join(self.tmp, "DCIM", "100MSDCF")
        os.makedirs(self.dcim)
        self.log = os.path.join(self.tmp, "LOG.CSV")
        with open(self.log, "w") as f:
            f.write(log_text())

    def touch(self, name, wall_ms_offset_s, folder=None):
        path = os.path.join(folder or self.dcim, name)
        open(path, "wb").close()
        t = WALL / 1000.0 + wall_ms_offset_s
        os.utime(path, (t, t))
        return path

    def run_main(self, *args):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            rc = frames_for_run.main(list(args))
        return rc, out.getvalue().split()


class FramesForRun(Tree):
    def test_matches_only_run_a_frames(self):
        a0 = self.touch("DSC00001.JPG", 100.5)
        a2 = self.touch("DSC00002.JPG", 120.4)
        self.touch("DSC00003.JPG", 110.5)  # during the skipped slot
        self.touch("DSC00004.JPG", 1050.2)  # run B's time
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual(rc, 0)
        self.assertEqual(lines, [a0, a2])

    def test_wall_conversion_is_per_run(self):
        self.touch("DSC00001.JPG", 100.5)
        b0 = self.touch("DSC00002.JPG", 1050.5)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "B")
        self.assertEqual((rc, lines), (0, [b0]))

    def test_window_edges(self):
        inside = self.touch("DSC00001.JPG", 100.0 - 2.0)
        self.touch("DSC00002.JPG", 100.0 - 2.5)
        late = self.touch("DSC00003.JPG", 100.4 + 2.0)
        self.touch("DSC00004.JPG", 100.4 + 2.5)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A", "--window-s", "2")
        self.assertEqual(lines, [inside, late])

    def test_offset(self):
        a0 = self.touch("DSC00001.JPG", 100.5 + 3600)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[0], 3)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A", "--offset-s", "3600")
        self.assertEqual((rc, lines), (0, [a0]))

    def test_no_match_exit_3(self):
        self.touch("DSC00001.JPG", 5000)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A"), (3, []))

    def test_unknown_run_exit_2(self):
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "Z")[0], 2)

    def test_list_runs(self):
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--list-runs"), (0, ["A", "B"]))

    def test_without_run_lists_all_sorted_by_name_across_folders(self):
        other = os.path.join(self.tmp, "DCIM", "101MSDCF")
        os.makedirs(other)
        p2 = self.touch("DSC00002.JPG", 0)
        p1 = self.touch("DSC00001.jpeg", 0, other)
        open(os.path.join(self.dcim, "notes.txt"), "w").close()
        rc, lines = self.run_main(os.path.join(self.tmp, "DCIM"))
        self.assertEqual((rc, lines), (0, [p1, p2]))

    def test_missing_header_is_error(self):
        with open(self.log, "w") as f:
            f.write("# run_id=A\n" + HEADER + row("A", 0, "FIRE", 1000))
        self.touch("DSC00001.JPG", 1)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[0], 2)


@unittest.skipUnless(shutil.which("bash"), "bash not available")
class AssembleScript(Tree):
    script = os.path.join(HERE, "assemble.sh")

    def assemble(self, *args):
        return subprocess.run(["bash", self.script] + list(args), capture_output=True, text=True,
                              cwd=self.tmp)

    def test_fixture_dry_run_prints_ffmpeg_command(self):
        r = self.assemble(FIXTURE_JPEGS, "--dry-run")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("frames: 3", r.stdout)
        cmd = [l for l in r.stdout.splitlines() if l.startswith("ffmpeg ")][0]
        for part in ("-f concat", "libx264", "-crf 18", "yuv420p", "-r 24", "timelapse.mp4"):
            self.assertIn(part, cmd)
        self.assertNotIn("-vf", cmd)
        self.assertFalse(os.path.exists(os.path.join(self.tmp, "timelapse.mp4")))

    def test_filters_and_options(self):
        r = self.assemble(FIXTURE_JPEGS, "--dry-run", "--fps", "30", "--deflicker", "--crop", "16:9",
                          "--out", "x.mp4")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("deflicker=size=5", r.stdout)
        self.assertIn("16/9", r.stdout)
        self.assertIn("-r 30", r.stdout)
        self.assertIn("x.mp4", r.stdout)

    def test_run_restricts_frames(self):
        self.touch("DSC00001.JPG", 100.5)
        self.touch("DSC00002.JPG", 120.4)
        self.touch("DSC00003.JPG", 700)
        os.makedirs(os.path.join(self.tmp, "TIMELPS"))
        shutil.copy(self.log, os.path.join(self.tmp, "TIMELPS", "LOG.CSV"))
        r = self.assemble(os.path.join(self.tmp, "DCIM"), "--dry-run", "--run", "A")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("frames: 2", r.stdout)

    def test_run_without_log_fails(self):
        r = self.assemble(FIXTURE_JPEGS, "--dry-run", "--run", "A")
        self.assertEqual(r.returncode, 2)
        self.assertIn("LOG.CSV", r.stderr)

    def test_bad_args(self):
        self.assertEqual(self.assemble(FIXTURE_JPEGS, "--crop", "wide", "--dry-run").returncode, 2)
        self.assertEqual(self.assemble(FIXTURE_JPEGS, "--fps", "0", "--dry-run").returncode, 2)
        self.assertEqual(self.assemble(self.tmp + "/nope").returncode, 2)

    def test_missing_ffmpeg_hint(self):
        bindir = os.path.join(self.tmp, "bin")
        os.makedirs(bindir)
        for tool in ("bash", "dirname"):
            os.symlink(shutil.which(tool), os.path.join(bindir, tool))
        r = subprocess.run([shutil.which("bash"), self.script, FIXTURE_JPEGS], capture_output=True, text=True,
                           env={"PATH": bindir})
        self.assertEqual(r.returncode, 127)
        self.assertIn("install", r.stderr.lower())


if __name__ == "__main__":
    unittest.main()
