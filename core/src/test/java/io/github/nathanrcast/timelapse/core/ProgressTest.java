package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.CAPTURED;
import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.SLOTS;
import static io.github.nathanrcast.timelapse.core.SchedulerRig.START;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.nathanrcast.timelapse.core.Scheduler.Decision;

public class ProgressTest {
    @Test
    public void countAndRemainingThroughASteadyRun() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 4, 1, 1), CAPTURED).captureMs(300);
        Scheduler s = rig.scheduler;
        long now = rig.clock.nowMs();
        assertEquals("0/4", Progress.count(s, now));
        assertEquals(60 + 40, Progress.secondsRemaining(s, now));
        assertEquals("1m 40s", Progress.remaining(s, now));
        assertEquals(60, Progress.delaySecondsRemaining(s, now));

        rig.runUntil(START + 60000 + 15000);
        now = rig.clock.nowMs();
        assertEquals("2/4", Progress.count(s, now));
        assertEquals(25, Progress.secondsRemaining(s, now));
        assertEquals(0, Progress.delaySecondsRemaining(s, now));

        rig.run();
        now = rig.clock.nowMs();
        assertEquals("4/4", Progress.count(s, now));
        assertEquals(0, Progress.secondsRemaining(s, now));
        assertEquals("0s", Progress.remaining(s, now));
    }

    @Test
    public void delayCountdownRoundsUpAndEndsAtTheFirstSlot() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(5, 3, 2, 1), CAPTURED);
        Scheduler s = rig.scheduler;
        assertEquals(120, Progress.delaySecondsRemaining(s, START));
        assertEquals(1, Progress.delaySecondsRemaining(s, START + 119999));
        assertEquals(1, Progress.delaySecondsRemaining(s, START + 119000));
        assertEquals(2, Progress.delaySecondsRemaining(s, START + 118999));
        assertEquals(0, Progress.delaySecondsRemaining(s, START + 120000));
        rig.runUntil(START + 120000);
        assertEquals(0, Progress.delaySecondsRemaining(s, START + 1000));
    }

    // 40-shot run with a pause mid-capture and BUSY skips: 41 fires for 40 captured slots. The
    // count must never pass 40/40 and the remaining time must never go negative.
    @Test
    public void skipsAndInterruptsNeverOverrunTheCountOrGoNegative() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 40, 0, 1), CAPTURED).captureMs(300).slowFrame(10, 5000);
        Scheduler s = rig.scheduler;
        rig.runUntil(START + 4100);
        assertTrue(s.isCapturing());
        s.pause();
        rig.clock.advance(20000);
        s.resume();
        rig.dropPendingShutter();

        int checked = 0;
        long lastRemaining = Long.MAX_VALUE;
        long lastSkipped = s.skipped();
        while (!s.isDone()) {
            rig.runUntil(rig.clock.nowMs() + 250);
            long now = rig.clock.nowMs();
            String[] parts = Progress.count(s, now).split("/");
            assertEquals("40", parts[1]);
            assertTrue(Long.parseLong(parts[0]) <= 40);
            long remaining = Progress.secondsRemaining(s, now);
            assertTrue(remaining >= 0);
            assertTrue("remaining only moves up on a skip", remaining <= lastRemaining || s.skipped() > lastSkipped);
            lastRemaining = remaining;
            lastSkipped = s.skipped();
            checked++;
        }
        assertTrue(checked > 100);
        assertEquals(41, s.framesFired());
        assertEquals(1, s.skipped(Scheduler.SkipReason.INTERRUPTED));
        assertTrue(s.skipped(Scheduler.SkipReason.BUSY) >= 2);
        assertEquals("40/40", Progress.count(s, rig.clock.nowMs()));
        assertEquals(0, Progress.secondsRemaining(s, rig.clock.nowMs()));
    }

    @Test
    public void eachSkipPushesTheEndOutByOneInterval() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 10, 0, 1), CAPTURED).captureMs(300).slowFrame(0, 5000);
        Scheduler s = rig.scheduler;
        assertEquals(20, Progress.secondsRemaining(s, START));
        rig.runUntil(START + 6000);
        assertEquals(2, s.skipped());
        assertEquals("1/10", Progress.count(s, START + 6000));
        assertEquals(24 - 6, Progress.secondsRemaining(s, START + 6000));
    }

    @Test
    public void bracketsCountSetsNotFrames() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(5, 3, 0, 3), CAPTURED).captureMs(400);
        rig.runUntil(START + 5900);
        Scheduler s = rig.scheduler;
        assertEquals(6, s.framesFired());
        assertEquals("1/3", Progress.count(s, START + 5900));
        assertEquals(10, Progress.secondsRemaining(s, START + 5900));
    }

    @Test
    public void stopOnSlotsEndsAtAFixedTime() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 6, 0, 1), SLOTS).captureMs(3000);
        Scheduler s = rig.scheduler;
        rig.runUntil(START + 5000);
        assertEquals(7, Progress.secondsRemaining(s, START + 5000));
        assertEquals("1/6", Progress.count(s, START + 5000));
    }

    @Test
    public void burstShowsSecondsOfTheBurst() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(0, 10, 0, 1), CAPTURED).pollMs(500);
        Scheduler s = rig.scheduler;
        assertEquals("0/10", Progress.count(s, START - 1));
        assertEquals(10, Progress.secondsRemaining(s, START));
        rig.runUntil(START + 4200);
        assertEquals("4/10", Progress.count(s, START + 4200));
        assertEquals(6, Progress.secondsRemaining(s, START + 4200));
        rig.run();
        assertEquals("10/10", Progress.count(s, START + 10000));
        assertEquals(0, Progress.secondsRemaining(s, START + 12000));
    }

    @Test
    public void burstElapsedStaysInsideTheBurst() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(0, 10, 0, 1), clock, CAPTURED);
        s.start();
        s.onTimer();
        assertEquals("10/10", Progress.count(s, START + 60000));
        assertEquals("0/10", Progress.count(s, START - 5000));
        assertEquals(0, Progress.secondsRemaining(s, START + 60000));
    }

    @Test
    public void infiniteRunsShowInf() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(1, Settings.SHOTS_INFINITE, 0, 1), CAPTURED).captureMs(300);
        rig.runUntil(START + 5500);
        Scheduler s = rig.scheduler;
        assertEquals("6/inf", Progress.count(s, START + 5500));
        assertEquals(Long.MAX_VALUE, Progress.secondsRemaining(s, START + 5500));
        assertEquals("inf", Progress.remaining(s, START + 5500));

        SchedulerRig burst = new SchedulerRig(new ShotPlan(0, Settings.SHOTS_INFINITE, 0, 1), CAPTURED).pollMs(500);
        burst.runUntil(START + 3000);
        assertEquals("inf", Progress.remaining(burst.scheduler, START + 3000));
    }

    @Test
    public void beforeStartItIsTheWholePlan() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(2.5, 10, 1, 1), clock, CAPTURED);
        assertEquals(60 + 25, Progress.secondsRemaining(s, START));
        assertEquals(0, Progress.delaySecondsRemaining(s, START));
        assertEquals("0/10", Progress.count(s, START));
    }

    @Test
    public void aLostCaptureStretchesTheRunButStaysNonNegative() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 2, 0, 1), CAPTURED, 5000).captureMs(300).loseFrame(1);
        Scheduler s = rig.scheduler;
        rig.runUntil(START + 6900);
        assertEquals(0, Progress.secondsRemaining(s, START + 6900));
        rig.runUntil(START + 7000);
        Decision lost = rig.only(Scheduler.Action.SKIP).get(0);
        assertEquals(Scheduler.SkipReason.LOST, lost.skipReason);
        assertEquals(START + 7000, lost.nowMs);
        assertEquals("1/2", Progress.count(s, START + 7000));
        assertTrue(Progress.secondsRemaining(s, START + 7000) >= 0);
        rig.run();
        assertEquals("2/2", Progress.count(s, rig.clock.nowMs()));
    }
}
