package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.Action.FIRE;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.SKIP;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.SLOT_DONE;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.WAIT;
import static io.github.nathanrcast.timelapse.core.Scheduler.NEVER;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.BUSY;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.INTERRUPTED;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.LATE;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.LOST;
import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.CAPTURED;
import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.SLOTS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import io.github.nathanrcast.timelapse.core.Scheduler.Action;
import io.github.nathanrcast.timelapse.core.Scheduler.Decision;

public class SchedulerTest {
    private static final long START = 1000000;
    private static final int INF = Settings.SHOTS_INFINITE;

    @Test
    public void steadyStateFiresOnEveryDeadline() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 100, 0, 1), CAPTURED).captureMs(300);
        rig.run();

        List<Decision> fires = rig.only(FIRE);
        assertEquals(100, fires.size());
        for (int n = 0; n < fires.size(); n++) {
            assertEquals(n, fires.get(n).slot);
            assertEquals(START + n * 2000L, fires.get(n).scheduledMs);
            assertEquals(0, fires.get(n).lateMs());
        }
        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(0, s.skipped());
        assertEquals(100, s.captured());
        assertEquals(100, s.framesFired());
        assertEquals(100, s.slotsElapsed());
        assertEquals(NEVER, s.nextWakeMs());
        assertEquals(START + 99 * 2000L + 300, rig.last(SLOT_DONE).nowMs);
    }

    @Test
    public void firstSlotIsDueAfterTheDelay() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(5, 3, 2, 1), CAPTURED).captureMs(300);
        assertEquals(START + 120000, rig.scheduler.t0Ms());
        assertEquals(START + 120000, rig.scheduler.nextWakeMs());
        rig.run();
        assertEquals(START + 120000, rig.only(FIRE).get(0).nowMs);
        assertEquals(START + 130000, rig.only(FIRE).get(2).nowMs);
    }

    @Test
    public void startAtSetsTheFirstDeadlineAndIgnoresTheDelay() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(5, 3, 2, 1), clock, CAPTURED);
        s.start(START + 7200000L);
        assertEquals(START + 7200000L, s.t0Ms());
        assertEquals(START + 7200000L, s.nextWakeMs());
        assertEquals(7200, Progress.delaySecondsRemaining(s, START));
        clock.set(START + 7200000L);
        assertEquals(FIRE, s.onTimer().action);
        try {
            s.start(START);
            fail("second start");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void resumeKeepingT0HoldsAScheduledStartButShiftsOnceRunning() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(5, 3, 0, 1), clock, CAPTURED);
        s.start(START + 600000L);
        s.pause();
        clock.advance(120000);
        s.resume(true);
        assertEquals(START + 600000L, s.t0Ms());

        FakeClock clock2 = new FakeClock(START);
        Scheduler delayed = new Scheduler(new ShotPlan(5, 3, 0, 1), clock2, CAPTURED);
        delayed.start(START + 600000L);
        delayed.pause();
        clock2.advance(120000);
        delayed.resume();
        assertEquals(START + 720000L, delayed.t0Ms());

        FakeClock clock3 = new FakeClock(START);
        Scheduler running = new Scheduler(new ShotPlan(5, 3, 0, 1), clock3, CAPTURED);
        running.start(START);
        assertEquals(FIRE, running.onTimer().action);
        running.pause();
        clock3.advance(1000);
        running.resume(true);
        assertEquals(START + 1000, running.t0Ms());
    }

    @Test
    public void raiseIntervalKeepsTheNextDeadlineAndSpacesTheRestWider() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(3, 10, 0, 1), clock, CAPTURED);
        s.start();
        assertEquals(FIRE, s.onTimer().action);
        s.onFrameCaptured();
        long next = s.deadlineMs(1);
        s.raiseInterval(5000);
        assertEquals(next, s.deadlineMs(1));
        assertEquals(next + 5000, s.deadlineMs(2));
        assertEquals(5000, s.plan().intervalMs());
        s.raiseInterval(4000);
        assertEquals(5000, s.plan().intervalMs());
    }

    @Test
    public void raiseIntervalIgnoresBurstAndUnstartedRuns() {
        Scheduler burst = new Scheduler(new ShotPlan(0, 5, 0, 1), new FakeClock(START), CAPTURED);
        burst.start();
        burst.raiseInterval(5000);
        assertEquals(0, burst.plan().intervalMs());
        Scheduler idle = new Scheduler(new ShotPlan(3, 5, 0, 1), new FakeClock(START), CAPTURED);
        idle.raiseInterval(5000);
        assertEquals(3000, idle.plan().intervalMs());
    }

    @Test
    public void captureSlowerThanTheIntervalSkipsSlotsWithoutBursts() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 10, 0, 1), CAPTURED).captureMs(3000);
        rig.run();

        List<Decision> fires = rig.only(FIRE);
        assertEquals(10, fires.size());
        for (int n = 0; n < fires.size(); n++) {
            assertEquals(2 * n, fires.get(n).slot);
            assertEquals(0, fires.get(n).lateMs());
            if (n > 0)
                assertTrue(fires.get(n).nowMs - fires.get(n - 1).nowMs >= 2000);
        }
        for (Decision skip : rig.only(SKIP))
            assertEquals(BUSY, skip.skipReason);
        assertEquals(9, rig.scheduler.skipped(BUSY));
        assertEquals(9, rig.scheduler.skipped());
        assertEquals(19, rig.scheduler.slotsElapsed());
        assertEquals(10, rig.scheduler.captured());
    }

    @Test
    public void aSingleSlowCaptureIsNotCaughtUp() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 10, 0, 1), CAPTURED).captureMs(300).slowFrame(5, 5000);
        rig.run();

        long[] firedSlots = new long[10];
        for (int n = 0; n < 10; n++)
            firedSlots[n] = rig.only(FIRE).get(n).slot;
        assertArrayEquals(new long[]{0, 1, 2, 3, 4, 5, 8, 9, 10, 11}, firedSlots);
        Decision afterSlow = rig.only(FIRE).get(6);
        assertEquals(START + 16000, afterSlow.nowMs);
        assertEquals(0, afterSlow.lateMs());
        assertEquals(2, rig.scheduler.skipped(BUSY));
        assertEquals(6, rig.only(SKIP).get(0).slot);
        assertEquals(7, rig.only(SKIP).get(1).slot);
    }

    @Test
    public void aLateTimerSkipsTheSlotsItMissedAndFiresOnlyTheCurrentOne() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 8, 0, 1), CAPTURED).captureMs(300).lateWake(START + 6000, 5000);
        rig.run();

        List<Decision> skips = rig.only(SKIP);
        assertEquals(2, skips.size());
        assertEquals(3, skips.get(0).slot);
        assertEquals(LATE, skips.get(0).skipReason);
        assertEquals(4, skips.get(1).slot);
        assertEquals(LATE, skips.get(1).skipReason);

        Decision lateFire = rig.only(FIRE).get(3);
        assertEquals(5, lateFire.slot);
        assertEquals(START + 11000, lateFire.nowMs);
        assertEquals(1000, lateFire.lateMs());
        assertEquals(START + 12000, rig.only(FIRE).get(4).nowMs);
        assertEquals(2, rig.scheduler.skipped(LATE));
    }

    @Test
    public void pauseAndResumeShiftTheRemainingDeadlines() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 5, 0, 1), CAPTURED).captureMs(300);
        rig.runUntil(START + 15000);
        assertEquals(2, rig.only(FIRE).size());

        Scheduler s = rig.scheduler;
        assertEquals(WAIT, s.pause().action);
        assertTrue(s.isPaused());
        assertEquals(NEVER, s.nextWakeMs());
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(WAIT, s.pause().action);

        rig.clock.advance(60000);
        s.resume();
        assertFalse(s.isPaused());
        assertEquals(START + 60000, s.t0Ms());
        assertEquals(START + 80000, s.nextWakeMs());
        s.resume();
        assertEquals(START + 60000, s.t0Ms());

        rig.run();
        List<Decision> fires = rig.only(FIRE);
        assertEquals(5, fires.size());
        assertEquals(START + 80000, fires.get(2).nowMs);
        assertEquals(START + 100000, fires.get(4).nowMs);
        assertEquals(0, s.skipped());
    }

    @Test
    public void pausingDuringTheDelayPushesTheFirstSlotBack() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(2, 3, 1, 1), clock, CAPTURED);
        s.start();
        clock.advance(20000);
        s.pause();
        clock.advance(30000);
        s.resume();
        assertEquals(START + 90000, s.nextWakeMs());
        clock.set(START + 90000);
        assertEquals(FIRE, s.onTimer().action);
        assertEquals(0, s.skipped());
    }

    @Test
    public void pausingMidCaptureInterruptsThatSlot() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 3, 0, 1), CAPTURED).captureMs(5000);
        rig.runUntil(START + 1000);
        Scheduler s = rig.scheduler;
        assertTrue(s.isCapturing());

        Decision interrupted = s.pause();
        assertEquals(SKIP, interrupted.action);
        assertEquals(INTERRUPTED, interrupted.skipReason);
        assertEquals(0, interrupted.slot);
        assertEquals(0, interrupted.frame);
        assertEquals(START, interrupted.scheduledMs);
        assertEquals(START + 1000, interrupted.nowMs);
        assertFalse(s.isCapturing());
        assertEquals(1, s.skipped(INTERRUPTED));
        assertEquals(WAIT, s.onFrameCaptured().action);

        rig.clock.advance(1000);
        s.resume();
        rig.dropPendingShutter();
        rig.captureMs(300).run();

        assertEquals(3, s.captured());
        assertEquals(4, rig.only(FIRE).size());
        assertEquals(START + 11000, rig.only(FIRE).get(1).nowMs);
    }

    @Test
    public void pauseIsIgnoredBeforeStartAndAfterTheRunEnds() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(1, 1, 0, 1), clock, CAPTURED);
        assertEquals(WAIT, s.pause().action);
        assertFalse(s.isPaused());

        s.start();
        s.onTimer();
        s.onFrameCaptured();
        assertTrue(s.isDone());
        s.pause();
        assertFalse(s.isPaused());
    }

    @Test
    public void burstFiresOnceAndOnlyTheTimerEndsIt() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(0, 10, 0, 1), CAPTURED).pollMs(500);
        rig.run();

        Scheduler s = rig.scheduler;
        assertTrue("polls ran", rig.polls > 15);
        assertEquals(0, rig.pollDecisions);
        assertEquals(1, rig.only(FIRE).size());
        assertEquals(START, rig.only(FIRE).get(0).nowMs);
        assertEquals(START + 10000, rig.last(SLOT_DONE).nowMs);
        assertTrue(s.isDone());
        assertEquals(1, s.framesFired());
        assertEquals(1, s.captured());
        assertEquals(1, s.slotsElapsed());
        assertEquals(0, s.skipped());
    }

    @Test
    public void burstEndsOnTheTimerWithoutAPoll() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(0, 10, 1, 1), clock, CAPTURED);
        s.start();
        assertEquals(START + 60000, s.nextWakeMs());
        assertEquals(WAIT, s.onTimer().action);

        clock.set(s.nextWakeMs());
        assertEquals(FIRE, s.onTimer().action);
        assertEquals(START + 70000, s.nextWakeMs());
        clock.advance(4000);
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(WAIT, s.onFrameCaptured().action);

        clock.set(s.nextWakeMs());
        assertEquals(SLOT_DONE, s.onTimer().action);
        assertTrue(s.isDone());
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(WAIT, s.onFrameCaptured().action);
    }

    @Test
    public void infiniteBurstNeverEnds() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(0, INF, 0, 1), CAPTURED).pollMs(500);
        rig.runUntil(START + 3600000);
        assertFalse(rig.scheduler.isDone());
        assertTrue(rig.scheduler.isCapturing());
        assertEquals(NEVER, rig.scheduler.nextWakeMs());
        assertEquals(1, rig.only(FIRE).size());
    }

    @Test
    public void pausedBurstRestartsOnResumeAndKeepsItsLength() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(0, 10, 0, 1), clock, CAPTURED);
        s.start();
        assertEquals(FIRE, s.onTimer().action);
        clock.advance(3000);
        s.pause();
        assertEquals(1, s.skipped(INTERRUPTED));
        assertFalse(s.isDone());

        clock.advance(60000);
        s.resume();
        assertEquals(START + 60000, s.nextWakeMs());
        assertEquals(FIRE, s.onTimer().action);
        assertEquals(START + 70000, s.nextWakeMs());
        clock.set(START + 70000);
        assertEquals(WAIT, s.onFrameCaptured().action);
        assertEquals(SLOT_DONE, s.onTimer().action);
        assertTrue(s.isDone());
        assertEquals(2, s.framesFired());
    }

    @Test
    public void pausingAStopOnSlotsBurstEndsIt() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(0, 10, 0, 1), clock, SLOTS);
        s.start();
        s.onTimer();
        clock.advance(3000);
        s.pause();
        assertTrue(s.isDone());
        s.resume();
        assertEquals(NEVER, s.nextWakeMs());
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(1, s.framesFired());
    }

    @Test
    public void pausingDuringTheLastSlotOfAStopOnSlotsRunEndsIt() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 3, 0, 1), SLOTS).captureMs(1000);
        rig.runUntil(START + 4500);
        Scheduler s = rig.scheduler;
        assertTrue(s.isCapturing());
        assertEquals(3, s.slotsElapsed());

        s.pause();
        assertTrue(s.isDone());
        assertEquals(1, s.skipped(INTERRUPTED));
        rig.clock.advance(30000);
        s.resume();
        assertTrue(s.isDone());
        assertEquals(NEVER, s.nextWakeMs());
    }

    @Test
    public void pausingDuringTheLastCaptureOfACapturedRunTakesOneMoreSlot() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 3, 0, 1), CAPTURED).captureMs(1000);
        rig.runUntil(START + 4500);
        Scheduler s = rig.scheduler;
        s.pause();
        assertFalse(s.isDone());
        rig.clock.advance(30000);
        s.resume();
        rig.dropPendingShutter();
        rig.run();
        assertTrue(s.isDone());
        assertEquals(3, s.captured());
        assertEquals(4, s.framesFired());
        assertEquals(START + 30000 + 6000, rig.last(FIRE).nowMs);
    }

    @Test
    public void aLostCaptureIsClosedAfterTheTimeoutAndTheRunGoesOn() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 5, 0, 1), CAPTURED, 5000).captureMs(300).loseFrame(1);
        rig.run();

        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(5, s.captured());
        Decision lost = rig.only(SKIP).get(2);
        assertEquals(LOST, lost.skipReason);
        assertEquals(1, lost.slot);
        assertEquals(0, lost.frame);
        assertEquals(START + 7000, lost.nowMs);
        assertEquals(2, s.skipped(BUSY));
        assertEquals(1, s.skipped(LOST));
        assertEquals(START + 8000, rig.only(FIRE).get(2).nowMs);
        assertEquals(4, rig.only(FIRE).get(2).slot);
    }

    @Test
    public void losingTheLastNeededCaptureStillEndsACapturedRun() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 2, 0, 1), CAPTURED, 5000).captureMs(300).loseFrame(1);
        rig.run();

        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(2, s.captured());
        assertEquals(1, s.skipped(LOST));
        // Slots 2 and 3 fell due while the lost capture was open: BUSY, though resolved after it closed.
        assertEquals(2, s.skipped(BUSY));
        assertEquals(0, s.skipped(LATE));
        assertEquals(4, rig.last(FIRE).slot);
        assertEquals(START + 8000, rig.last(FIRE).nowMs);
    }

    @Test
    public void aLostLastSlotEndsAStopOnSlotsRun() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 2, 0, 1), SLOTS, 5000).captureMs(300).loseFrame(1);
        rig.run();
        assertTrue(rig.scheduler.isDone());
        assertEquals(1, rig.scheduler.captured());
        assertEquals(LOST, rig.last(SKIP).skipReason);
        assertEquals(START + 7000, rig.last(SKIP).nowMs);
    }

    @Test
    public void startedAtIsWhenStartRanAndNeverShifts() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(10, 3, 0, 1), clock, CAPTURED);
        s.start();
        clock.advance(500);
        s.pause();
        clock.advance(500);
        s.resume();
        assertEquals(START, s.startedAtMs());
    }

    @Test
    public void aRefusedFrameClosesTheSlotAtOnceAsLost() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(10, 3, 0, 1), clock, CAPTURED);
        s.start();
        assertEquals(FIRE, s.onTimer().action);
        clock.advance(40);
        Decision d = s.captureFailed();
        assertEquals(SKIP, d.action);
        assertEquals(LOST, d.skipReason);
        assertEquals(0, d.slot);
        assertEquals(0, d.frame);
        assertEquals(START + 40, d.nowMs);
        assertFalse(s.isCapturing());
        assertEquals(1, s.skipped(LOST));
        assertEquals(0, s.captured());
        assertEquals(START + 10000, s.nextWakeMs());
        clock.set(START + 10000);
        assertEquals(FIRE, s.onTimer().action);
        assertEquals(0, s.skipped(BUSY));
    }

    @Test
    public void aRefusedBracketFrameIsLostAtItsFrame() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(10, 3, 0, 3), clock, CAPTURED);
        s.start();
        s.onTimer();
        assertEquals(FIRE, s.onFrameCaptured().action);
        Decision d = s.captureFailed();
        assertEquals(LOST, d.skipReason);
        assertEquals(1, d.frame);
        assertEquals(0, s.captured());
    }

    @Test
    public void aRefusedLastSlotEndsAStopOnSlotsRun() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(10, 1, 0, 1), clock, SLOTS);
        s.start();
        s.onTimer();
        assertEquals(LOST, s.captureFailed().skipReason);
        assertTrue(s.isDone());
    }

    @Test
    public void captureFailedWithNothingInFlightIsIgnored() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(10, 3, 0, 1), clock, CAPTURED);
        s.start();
        assertSame(Scheduler.Decision.WAIT, s.captureFailed());
        assertEquals(0, s.skipped());
    }

    @Test
    public void aRefusedBurstEndsTheRunWithoutACaptureOrARefire() {
        for (Scheduler.StopOn stopOn : Scheduler.StopOn.values()) {
            FakeClock clock = new FakeClock(START);
            Scheduler burst = new Scheduler(new ShotPlan(0, 5, 0, 1), clock, stopOn);
            burst.start();
            assertEquals(FIRE, burst.onTimer().action);
            clock.advance(20);
            Decision d = burst.captureFailed();
            assertEquals(SKIP, d.action);
            assertEquals(LOST, d.skipReason);
            assertTrue(burst.isDone());
            assertFalse(burst.isCapturing());
            assertEquals(0, burst.captured());
            assertEquals(1, burst.skipped(LOST));
            assertEquals(NEVER, burst.nextWakeMs());
            clock.advance(60000);
            assertEquals(WAIT, burst.onTimer().action);
            assertEquals(0, burst.captured());
        }
    }

    @Test
    public void theTimeoutRunsFromEachBracketFrame() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(10, 2, 0, 3), CAPTURED, 5000).captureMs(400).loseFrame(2);
        rig.run();
        Decision lost = rig.only(SKIP).get(0);
        assertEquals(LOST, lost.skipReason);
        assertEquals(0, lost.slot);
        assertEquals(2, lost.frame);
        assertEquals(START + 800 + 5000, lost.nowMs);
        assertEquals("SKIP slot 0 frame 2 LOST +5800 ms", lost.toString());
        assertEquals(2, rig.scheduler.captured());
    }

    @Test
    public void theDefaultCaptureTimeoutIsThreeIntervalsButAtLeastTwoMinutes() {
        assertEquals(120000, Scheduler.defaultCaptureTimeoutMs(new ShotPlan(2, 10, 0, 1)));
        assertEquals(180000, Scheduler.defaultCaptureTimeoutMs(new ShotPlan(60, 10, 0, 1)));
        assertEquals(120000, Scheduler.defaultCaptureTimeoutMs(new ShotPlan(0, 10, 0, 1)));
        FakeClock clock = new FakeClock(START);
        assertEquals(6840000, new Scheduler(new ShotPlan(2280, 10, 0, 1), clock, CAPTURED).captureTimeoutMs());
        try {
            new Scheduler(new ShotPlan(2, 10, 0, 1), clock, CAPTURED, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void waitIsOneSharedInstance() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(2, 5, 0, 1), clock, CAPTURED);
        assertSame(Decision.WAIT, s.onTimer());
        s.start();
        clock.set(START - 1);
        assertSame(Decision.WAIT, s.onTimer());
        assertSame(Decision.WAIT, s.onFrameCaptured());
        assertEquals(-1, Decision.WAIT.slot);
        assertEquals(0, Decision.WAIT.lateMs());
    }

    // 10 000 slots with every timer wake-up 0-150 ms late: each shot is still timed from its own
    // deadline, so the last one is due exactly 9 999 intervals after the first.
    @Test
    public void tenThousandSlotsAccumulateNoDrift() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(0.5, 10000, 0, 1), CAPTURED).captureMs(200).jitterUpToMs(150);
        rig.run();

        List<Decision> fires = rig.only(FIRE);
        assertEquals(10000, fires.size());
        long maxLate = 0;
        for (int n = 0; n < fires.size(); n++) {
            Decision fire = fires.get(n);
            assertEquals(n, fire.slot);
            assertEquals(START + n * 500L, fire.scheduledMs);
            assertTrue(fire.lateMs() >= 0 && fire.lateMs() <= 150);
            maxLate = Math.max(maxLate, fire.lateMs());
        }
        assertTrue("jitter was exercised", maxLate > 100);
        assertEquals(9999 * 500L, fires.get(9999).scheduledMs - fires.get(0).scheduledMs);
        assertEquals(0, rig.scheduler.skipped());
        assertEquals(10000, rig.scheduler.captured());
    }

    @Test
    public void bracketsFireBackToBackAndCountAsOneSlot() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(5, 4, 0, 3), CAPTURED).captureMs(400);
        rig.run();

        Scheduler s = rig.scheduler;
        assertEquals(12, s.framesFired());
        assertEquals(4, s.captured());
        assertEquals(4, s.slotsElapsed());
        assertEquals(0, s.skipped());

        List<Decision> fires = rig.only(FIRE);
        assertEquals(12, fires.size());
        for (int n = 0; n < fires.size(); n++) {
            assertEquals(n / 3, fires.get(n).slot);
            assertEquals(n % 3, fires.get(n).frame);
            assertEquals(START + (n / 3) * 5000L + (n % 3) * 400L, fires.get(n).nowMs);
        }
        List<Decision> done = rig.only(SLOT_DONE);
        assertEquals(4, done.size());
        assertEquals(2, done.get(0).frame);
        assertEquals(START + 1200, done.get(0).nowMs);
    }

    @Test
    public void nBracketSlotsGiveThreeNFramesWithNoDoubleCounting() {
        for (int n : new int[] {1, 2, 7, 40}) {
            SchedulerRig rig = new SchedulerRig(new ShotPlan(5, n, 0, 3), CAPTURED).captureMs(400);
            long lastCaptured = 0;
            long lastSlots = 0;
            while (!rig.scheduler.isDone()) {
                rig.runUntil(rig.clock.nowMs() + 100);
                Scheduler s = rig.scheduler;
                assertTrue("captured never jumps", s.captured() - lastCaptured <= 1);
                assertTrue("slots never jump", s.slotsElapsed() - lastSlots <= 1);
                assertTrue(s.captured() <= n);
                lastCaptured = s.captured();
                lastSlots = s.slotsElapsed();
            }
            Scheduler s = rig.scheduler;
            assertEquals(3L * n, s.framesFired());
            assertEquals(n, s.captured());
            assertEquals(n, s.slotsElapsed());
            assertEquals(0, s.skipped());
            assertEquals(3 * n, rig.only(FIRE).size());
            assertEquals(n, rig.only(SLOT_DONE).size());
            assertEquals(n + "/" + n, Progress.count(s, rig.clock.nowMs()));
        }
    }

    @Test
    public void bracketsSlowerThanTheIntervalSkipTheNextSlot() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(1, 3, 0, 3), CAPTURED).captureMs(400);
        rig.run();
        assertEquals(9, rig.scheduler.framesFired());
        assertEquals(3, rig.scheduler.captured());
        assertEquals(2, rig.scheduler.skipped(BUSY));
        assertEquals(0, rig.only(FIRE).get(0).slot);
        assertEquals(2, rig.only(FIRE).get(3).slot);
        assertEquals(4, rig.only(FIRE).get(6).slot);
    }

    @Test
    public void stopOnSlotsEndsAfterTheLastSlotsCapture() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 6, 0, 1), SLOTS).captureMs(3000);
        rig.run();

        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(6, s.slotsElapsed());
        assertEquals(3, s.captured());
        assertEquals(3, s.skipped(BUSY));
        assertEquals(START + 11000, rig.last(SLOT_DONE).nowMs);
    }

    @Test
    public void stopOnSlotsCanEndOnASkip() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(2, 3, 0, 1), SLOTS).captureMs(300).lateWake(START + 4000, 2500);
        rig.run();
        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(LATE, rig.last(SKIP).skipReason);
        assertEquals(2, s.captured());
        assertEquals(NEVER, s.nextWakeMs());
    }

    @Test
    public void infiniteShotsKeepGoing() {
        SchedulerRig rig = new SchedulerRig(new ShotPlan(1, INF, 0, 1), CAPTURED).captureMs(300);
        rig.runUntil(START + 100000);
        assertFalse(rig.scheduler.isDone());
        assertEquals(100, rig.scheduler.captured());
        assertEquals(101, rig.scheduler.framesFired());
        assertTrue(rig.scheduler.isCapturing());
    }

    @Test
    public void callsOutsideARunChangeNothing() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(2, 5, 0, 1), clock, CAPTURED);
        assertEquals(NEVER, s.nextWakeMs());
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(WAIT, s.onFrameCaptured().action);

        s.start();
        try {
            s.start();
            fail();
        } catch (IllegalStateException expected) {
        }
        clock.set(START - 1);
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(WAIT, s.onFrameCaptured().action);
        assertEquals(0, s.slotsElapsed());
        assertEquals(0, s.framesFired());
        assertEquals(0, s.captured());
    }

    @Test
    public void aStaleTimerDuringTheLastCaptureIsIgnored() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(1, 2, 0, 1), clock, CAPTURED);
        s.start();
        s.onTimer();
        clock.advance(300);
        s.onFrameCaptured();
        clock.set(START + 1000);
        assertEquals(FIRE, s.onTimer().action);
        // No slot left to resolve; the timer only stays armed for the capture timeout.
        assertEquals(START + 1000 + s.captureTimeoutMs(), s.nextWakeMs());

        clock.set(START + 5000);
        assertEquals(WAIT, s.onTimer().action);
        assertEquals(0, s.skipped());
        assertEquals(2, s.slotsElapsed());
        assertEquals(SLOT_DONE, s.onFrameCaptured().action);
        assertTrue(s.isDone());
    }

    @Test
    public void reviewEndsAtTheNextDeadlineOrTheCap() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(2, 5, 0, 1), clock, CAPTURED);
        s.start();
        s.onTimer();
        clock.advance(300);
        s.onFrameCaptured();
        assertEquals(START + 2000, s.reviewUntilMs(2000));
        assertEquals(START + 1300, s.reviewUntilMs(1000));
        clock.set(START + 2000);
        assertEquals(-1, s.reviewUntilMs(2000));
    }

    @Test
    public void decisionsDescribeThemselves() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(1, 5, 0, 1), clock, CAPTURED);
        assertEquals("WAIT", s.onTimer().toString());
        s.start();
        clock.advance(12);
        assertEquals("FIRE slot 0 frame 0 +12 ms", s.onTimer().toString());
        clock.set(START + 1000);
        assertEquals("SKIP slot 1 BUSY +0 ms", s.onTimer().toString());
        clock.advance(300);
        assertEquals("SLOT_DONE slot 0 +1300 ms", s.onFrameCaptured().toString());
    }
}
