package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ShotLogCsvTest {
    @Test
    public void columnHeaderIsStable() {
        assertEquals("run_id,slot,scheduled_ms,actual_ms,capture_done_ms,result,skip_reason,ss,iso,ev,"
                + "bracket_idx,battery_pct,free_mb", ShotLogCsv.columnHeader());
    }

    @Test
    public void escapeQuotesOnlyWhenNeeded() {
        assertEquals("plain", ShotLogCsv.escape("plain"));
        assertEquals("", ShotLogCsv.escape(null));
        assertEquals("\"a,b\"", ShotLogCsv.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ShotLogCsv.escape("say \"hi\""));
        assertEquals("\"a\nb\"", ShotLogCsv.escape("a\nb"));
        assertEquals("\"a\rb\"", ShotLogCsv.escape("a\rb"));
    }

    @Test
    public void rowKeepsColumnOrderAndEmptiesMissingFields() {
        ShotRecord fire = new ShotRecord("20260101-120000", 3, 30000, 30012, ShotRecord.NONE,
                ShotRecord.Result.FIRE, null, "1/250", "400", "-0.33", 0, 87, 12000);
        assertEquals("20260101-120000,3,30000,30012,,FIRE,,1/250,400,-0.33,0,87,12000", ShotLogCsv.row(fire));
        ShotRecord skip = new ShotRecord("r", 4, 40000, ShotRecord.NONE, ShotRecord.NONE, ShotRecord.Result.SKIP,
                "BUSY", null, null, null, ShotRecord.NONE, ShotRecord.NONE, ShotRecord.NONE);
        assertEquals("r,4,40000,,,SKIP,BUSY,,,,,,", ShotLogCsv.row(skip));
    }

    @Test
    public void rowEscapesTextFields() {
        ShotRecord r = new ShotRecord("a,b", 0, 0, 0, ShotRecord.NONE, ShotRecord.Result.FIRE, null, "1\"2", "", "",
                0, 0, 0);
        assertEquals("\"a,b\",0,0,0,,FIRE,,\"1\"\"2\",,,0,0,0", ShotLogCsv.row(r));
    }

    @Test
    public void withEnvironmentOnlyChangesBatteryAndFreeSpace() {
        ShotRecord r = new ShotRecord("r", 1, 2, 3, ShotRecord.NONE, ShotRecord.Result.FIRE, null, "1/60", "100", "",
                0, ShotRecord.NONE, ShotRecord.NONE);
        assertEquals("r,1,2,3,,FIRE,,1/60,100,,0,55,900", ShotLogCsv.row(r.withEnvironment(55, 900)));
    }

    @Test
    public void runHeaderIsCommentLinesOnOneLineEach() {
        String h = ShotLogCsv.runHeader("run1", "ILCE-6500", "fw\n1.0", "1.4.0 (5)", 1700000000000L, "{\"a\":1}");
        assertEquals("# run_id=run1\n# model=ILCE-6500\n# firmware=fw 1.0\n# app_version=1.4.0 (5)\n"
                + "# uptime_to_wall_ms=1700000000000\n# settings={\"a\":1}\n", h);
        for (String line : h.split("\n"))
            assertTrue(line.startsWith("# "));
    }

    @Test
    public void settingsJsonCoversDefaults() {
        String json = ShotLogCsv.settingsJson(new Settings());
        assertTrue(json.startsWith("{\"interval\":1.0,\"delay\":0,\"shotCount\":1,"));
        assertTrue(json.contains("\"holyGrail\":false"));
        assertTrue(json.endsWith("\"holyGrailAllowExposureDown\":false}"));
        Settings s = new Settings();
        s.shotCount = Settings.SHOTS_INFINITE;
        assertTrue(ShotLogCsv.settingsJson(s).contains("\"shotCount\":\"infinite\""));
    }

    @Test
    public void decisionsMapToRows() {
        Settings s = new Settings();
        s.interval = 10;
        s.shotCount = 3;
        SchedulerRig rig = new SchedulerRig(ShotPlan.of(s), Scheduler.StopOn.CAPTURED).captureMs(200);
        rig.run();
        Scheduler.Decision fire = rig.only(Scheduler.Action.FIRE).get(0);
        ShotRecord f = ShotRecord.of("r", fire, "1/60", "100", "", 90, 500);
        assertEquals(ShotRecord.Result.FIRE, f.result);
        assertEquals(fire.nowMs, f.actualMs);
        assertEquals(ShotRecord.NONE, f.captureDoneMs);
        assertEquals(0, f.bracketIdx);
        Scheduler.Decision done = rig.only(Scheduler.Action.SLOT_DONE).get(0);
        ShotRecord d = ShotRecord.of("r", done, null, null, null, 90, 500);
        assertEquals(ShotRecord.Result.SLOT_DONE, d.result);
        assertEquals(done.nowMs, d.captureDoneMs);
        assertEquals(ShotRecord.NONE, d.actualMs);
        assertEquals(done.scheduledMs, d.scheduledMs);
        assertNull(ShotRecord.of("r", Scheduler.Decision.WAIT, null, null, null, 0, 0));
    }

    @Test
    public void skipDecisionCarriesItsReason() {
        Settings s = new Settings();
        s.interval = 1;
        s.shotCount = 3;
        SchedulerRig rig = new SchedulerRig(ShotPlan.of(s), Scheduler.StopOn.CAPTURED).captureMs(1500);
        rig.run();
        List<Scheduler.Decision> skips = rig.only(Scheduler.Action.SKIP);
        assertTrue(!skips.isEmpty());
        ShotRecord r = ShotRecord.of("r", skips.get(0), null, null, null, 0, 0);
        assertEquals(ShotRecord.Result.SKIP, r.result);
        assertEquals(skips.get(0).skipReason.name(), r.skipReason);
        assertTrue(ShotLogCsv.row(r).contains(",SKIP," + r.skipReason + ","));
    }
}
