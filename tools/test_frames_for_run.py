import contextlib
import io
import os
import struct
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
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = frames_for_run.main(list(args))
        self.last_err = err.getvalue()
        return rc, out.getvalue().split()


class FramesForRun(Tree):
    def test_span_selects_run_a_not_run_b(self):
        a0 = self.touch("DSC00001.JPG", 100.5)
        a1 = self.touch("DSC00002.JPG", 110.5)
        a2 = self.touch("DSC00003.JPG", 120.4)
        self.touch("DSC00004.JPG", 1050.2)  # run B's time
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual((rc, lines), (0, [a0, a1, a2]))

    def test_late_bracket_frames_are_kept(self):
        a0 = self.touch("DSC00001.JPG", 100.5)
        late = self.touch("DSC00002.JPG", 120.3 + 20)  # written well after the last callback
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual(lines, [a0, late])

    def test_wall_conversion_is_per_run(self):
        self.touch("DSC00001.JPG", 100.5)
        b0 = self.touch("DSC00002.JPG", 1050.5)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "B")
        self.assertEqual((rc, lines), (0, [b0]))

    def test_drift_slack_edges(self):
        # span A is 100.0 .. 120.3 s, slack = 60 + 1 % of 20.3 s
        slack = 60 + 0.01 * 20.3
        inside = self.touch("DSC00001.JPG", 100.0 - slack + 0.5)
        self.touch("DSC00002.JPG", 100.0 - slack - 0.5)
        late = self.touch("DSC00003.JPG", 120.3 + slack - 0.5)
        self.touch("DSC00004.JPG", 120.3 + slack + 0.5)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual(lines, [inside, late])

    def test_offset(self):
        a0 = self.touch("DSC00001.JPG", 100.5 + 3600)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[0], 3)
        self.assertIn("--offset-s", self.last_err)
        self.assertIn("cp -p", self.last_err)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A", "--offset-s", "3600")
        self.assertEqual((rc, lines), (0, [a0]))

    def test_count_mismatch_warns_with_both_numbers(self):
        self.touch("DSC00001.JPG", 100.5)
        rc, lines = self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual(rc, 0)
        self.assertIn("2 FIRE rows but 1 frames", self.last_err)

    def test_matching_count_is_quiet(self):
        self.touch("DSC00001.JPG", 100.5)
        self.touch("DSC00002.JPG", 120.4)
        self.run_main(self.tmp, "--log", self.log, "--run", "A")
        self.assertEqual(self.last_err, "")

    def test_rollover_across_folders_keeps_order(self):
        other = os.path.join(self.tmp, "DCIM", "101MSDCF")
        first = os.path.join(self.tmp, "DCIM", "100MSDCF")
        os.makedirs(other)
        p2 = self.touch("DSC09999.JPG", 100.5, first)
        p1 = self.touch("DSC00001.JPG", 120.4, other)
        rc, lines = self.run_main(os.path.join(self.tmp, "DCIM"), "--log", self.log, "--run", "A")
        self.assertEqual(lines, [p2, p1])

    def test_no_match_exit_3(self):
        self.touch("DSC00001.JPG", 5000)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A"), (3, []))

    def test_unknown_run_exit_2(self):
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "Z")[0], 2)

    def test_list_runs(self):
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--list-runs"), (0, ["A", "B"]))

    def test_without_run_lists_all_absolute_by_folder_then_name(self):
        other = os.path.join(self.tmp, "DCIM", "101MSDCF")
        os.makedirs(other)
        p2 = self.touch("DSC00002.JPG", 0)
        p1 = self.touch("DSC00001.jpeg", 0, other)
        open(os.path.join(self.dcim, "notes.txt"), "w").close()
        rc, lines = self.run_main(os.path.join(self.tmp, "DCIM"))
        self.assertEqual((rc, lines), (0, [p2, p1]))
        self.assertTrue(all(os.path.isabs(l) for l in lines))

    def test_missing_header_is_error(self):
        with open(self.log, "w") as f:
            f.write("# run_id=A\n" + HEADER + row("A", 0, "FIRE", 1000))
        self.touch("DSC00001.JPG", 1)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[0], 2)


def tiff_blob(stamp, endian="<"):
    """Minimal TIFF: IFD0 with an ExifIFD pointer, whose IFD holds DateTimeOriginal (ASCII, 20 bytes)."""
    e = endian
    head = (b"II" if e == "<" else b"MM") + struct.pack(e + "HI", 42, 8)
    ifd0 = struct.pack(e + "H", 1) + struct.pack(e + "HHI", 0x8769, 4, 1) + struct.pack(e + "I", 26) + struct.pack(e + "I", 0)
    exif = struct.pack(e + "H", 1) + struct.pack(e + "HHI", 0x9003, 2, 20) + struct.pack(e + "I", 44) + struct.pack(e + "I", 0)
    return head + ifd0 + exif + stamp.encode("ascii") + b"\x00"


def jpeg_blob(stamp, endian="<"):
    app1 = b"Exif\x00\x00" + tiff_blob(stamp, endian)
    return (b"\xff\xd8" + b"\xff\xe0" + struct.pack(">H", 4) + b"JF" + b"\xff\xe1" + struct.pack(">H", len(app1) + 2)
            + app1 + b"\xff\xd9")


class ExifParsing(unittest.TestCase):
    def read(self, data, suffix):
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d)
        path = os.path.join(d, "x" + suffix)
        with open(path, "wb") as f:
            f.write(data)
        return frames_for_run.exif_time(path)

    def test_tiff_arw_little_and_big_endian(self):
        want = frames_for_run.calendar.timegm((2026, 10, 31, 8, 24, 1))
        self.assertEqual(self.read(tiff_blob("2026:10:31 08:24:01"), ".ARW"), want)
        self.assertEqual(self.read(tiff_blob("2026:10:31 08:24:01", ">"), ".ARW"), want)

    def test_jpeg_app1_after_another_segment(self):
        want = frames_for_run.calendar.timegm((2026, 10, 31, 8, 24, 5))
        self.assertEqual(self.read(jpeg_blob("2026:10:31 08:24:05"), ".JPG"), want)
        self.assertEqual(self.read(jpeg_blob("2026:10:31 08:24:05", ">"), ".JPG"), want)

    def test_unreadable_files_give_none(self):
        self.assertIsNone(self.read(b"", ".JPG"))
        self.assertIsNone(self.read(b"\xff\xd8\xff\xd9", ".JPG"))
        self.assertIsNone(self.read(b"not an image at all", ".JPG"))
        self.assertIsNone(self.read(tiff_blob("garbage garbage garb"), ".ARW"))
        self.assertIsNone(self.read(tiff_blob("2026:10:31 08:24:01")[:30], ".ARW"))


class ExifMatching(Tree):
    # Camera local time is UTC-6; the log header says so. The host sees mtimes 7 h away from the EXIF time.
    def setUp(self):
        super().setUp()
        with open(self.log, "w") as f:
            f.write("# run_id=A\n# utc_offset_min=-360\n# uptime_to_wall_ms=%d\n" % WALL + HEADER
                    + row("A", 0, "FIRE", 100000) + row("A", 0, "SLOT_DONE", done=100400)
                    + row("A", 1, "FIRE", 101000) + row("A", 1, "SLOT_DONE", done=101400))

    def shot(self, name, local_offset_s, mtime_off_s):
        """A file whose EXIF says local time = run time + local_offset_s, with its own mtime offset."""
        t = int(WALL / 1000) + 100 + local_offset_s - 6 * 3600
        stamp = frames_for_run.time.strftime("%Y:%m:%d %H:%M:%S", frames_for_run.time.gmtime(t + 0))
        path = os.path.join(self.dcim, name)
        with open(path, "wb") as f:
            f.write(jpeg_blob(stamp))
        m = WALL / 1000.0 + mtime_off_s
        os.utime(path, (m, m))
        return path

    def test_exif_wins_over_a_wrong_mtime(self):
        a = self.shot("DSC00001.JPG", 0, 7 * 3600)
        b = self.shot("DSC00002.JPG", 1, 7 * 3600)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A"), (0, [a, b]))

    def test_no_exif_flag_uses_mtime(self):
        self.shot("DSC00001.JPG", 0, 7 * 3600)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A", "--no-exif")[0], 3)

    def test_files_without_exif_fall_back_to_mtime(self):
        a = self.touch("DSC00001.JPG", 100.5)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[1], [a])

    def test_exif_outside_the_run_is_left_out(self):
        self.shot("DSC00001.JPG", 5000, 100)
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[0], 3)

    def test_raw_files_only_with_include_raw(self):
        j = self.shot("DSC00001.JPG", 0, 0)
        raw = os.path.join(self.dcim, "DSC00001.ARW")
        t = int(WALL / 1000) + 100 - 6 * 3600
        with open(raw, "wb") as f:
            f.write(tiff_blob(frames_for_run.time.strftime("%Y:%m:%d %H:%M:%S", frames_for_run.time.gmtime(t))))
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A")[1], [j])
        self.assertEqual(self.run_main(self.tmp, "--log", self.log, "--run", "A", "--include-raw")[1], [raw, j])


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

    def test_log_two_levels_up_and_passthrough(self):
        folder = os.path.join(self.tmp, "DCIM", "100MSDCF")
        os.makedirs(os.path.join(self.tmp, "TIMELPS"))
        shutil.copy(self.log, os.path.join(self.tmp, "TIMELPS", "LOG.CSV"))
        self.touch("DSC00001.JPG", 100.5 + 3600)
        self.assertEqual(self.assemble(folder, "--dry-run", "--run", "A").returncode, 1)
        r = self.assemble(folder, "--dry-run", "--run", "A", "--offset-s", "3600")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("frames: 1", r.stdout)

    def test_unreadable_log_without_run_continues(self):
        with open(os.path.join(self.dcim, "LOG.CSV"), "w") as f:
            f.write("run_id,slot\nA,1,2,3\n")
        self.touch("DSC00001.JPG", 0)
        r = self.assemble(self.dcim, "--dry-run")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn("warning", r.stderr)

    def test_help_mentions_timestamps(self):
        r = self.assemble("--help")
        self.assertEqual(r.returncode, 0)
        self.assertIn("timestamps", r.stdout)

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
