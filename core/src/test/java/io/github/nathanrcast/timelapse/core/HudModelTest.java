package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.CAPTURED;
import static io.github.nathanrcast.timelapse.core.SchedulerRig.START;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HudModelTest {
    @Test
    public void fieldsThroughARun() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 4, 1, 1), CAPTURED).captureMs(300);
        Scheduler s = rig.scheduler;
        HudModel before = HudModel.of(s, rig.clock.nowMs(), 80, false, 2000000, 1000);
        assertEquals("Delay: 1m 0s", before.count);
        assertEquals("0", before.skips);
        assertEquals("1m 0s", before.next);
        assertEquals("0s", before.elapsed);
        assertEquals("1m 40s", before.remaining);
        assertEquals("80%", before.battery);
        assertEquals("~2000", before.cardLeft);

        rig.runUntil(START + 60000 + 15000);
        HudModel mid = HudModel.of(s, rig.clock.nowMs(), 79, true, 1000, 1000);
        assertEquals("2/4", mid.count);
        assertEquals("c 79%", mid.battery);
        assertEquals("~1", mid.cardLeft);
        assertEquals("15s", mid.elapsed);
        assertEquals("5s", mid.next);
        assertEquals("25s", mid.remaining);
    }

    @Test
    public void doneRunShowsNoNext() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 2, 1, 1), CAPTURED).captureMs(300);
        rig.run();
        HudModel done = HudModel.of(rig.scheduler, rig.clock.nowMs(), -1, false, -1, -1);
        assertEquals("\u2014", done.next);
        assertEquals("2/2", done.count);
        assertEquals("?", done.battery);
        assertEquals("?", done.cardLeft);
        assertEquals("2/2 shots, 0 skipped", HudModel.summary(rig.scheduler, rig.clock.nowMs()));
    }

    @Test
    public void overdueSlotReadsNow() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 4, 1, 1), CAPTURED);
        assertEquals("now", HudModel.next(rig.scheduler, START + 60000 + 5));
    }

    @Test
    public void cardEstimate() {
        assertEquals("?", HudModel.cardLeft(100, 0, 1));
        assertEquals("?", HudModel.cardLeft(-1, 10, 1));
        assertEquals("~10", HudModel.cardLeft(100, 10, 1));
        assertEquals("~3", HudModel.cardLeft(100, 10, 3));
        assertEquals("~0", HudModel.cardLeft(5, 10, 1));
        assertEquals("~10", HudModel.cardLeft(100, 10, 0));
    }

    @Test
    public void batteryText() {
        assertEquals("?", HudModel.battery(-1, true));
        assertEquals("5%", HudModel.battery(5, false));
        assertEquals("c 100%", HudModel.battery(100, true));
    }

    @Test
    public void burstShowsElapsedSecondsAndNoNext() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(0, 5, 0, 1), CAPTURED);
        rig.runUntil(START + 2500);
        HudModel burst = HudModel.of(rig.scheduler, rig.clock.nowMs(), 50, false, 0, 1);
        assertEquals("\u2014", burst.next);
        assertEquals("2/5", burst.count);
    }

    @Test
    public void noNextOnceEverySlotIsClaimedEvenWhileCapturing() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 2, 0, 1), CAPTURED).captureMs(5000);
        rig.runUntil(START + 10000 + 1000);
        assertEquals(true, rig.scheduler.isCapturing());
        assertEquals("\u2014", HudModel.next(rig.scheduler, rig.clock.nowMs()));
    }

    @Test
    public void elapsedKeepsPausedTime() {
        FakeClock clock = new FakeClock(1000);
        Scheduler s = new Scheduler(new ShotPlan(10, 4, 0, 1), clock, CAPTURED);
        s.start();
        clock.advance(30000);
        s.pause();
        clock.advance(600000);
        s.resume();
        clock.advance(10000);
        assertEquals("10m 40s", HudModel.elapsed(s, clock.nowMs()));
    }
}
