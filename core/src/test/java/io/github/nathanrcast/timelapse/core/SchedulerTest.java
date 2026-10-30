package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.Action.FIRE;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.SKIP;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.SLOT_DONE;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.WAIT;
import static io.github.nathanrcast.timelapse.core.Scheduler.NEVER;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.BUSY;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.INTERRUPTED;
import static io.github.nathanrcast.timelapse.core.Scheduler.SkipReason.LATE;
import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.CAPTURED;
import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.SLOTS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.nathanrcast.timelapse.core.Scheduler.Action;
import io.github.nathanrcast.timelapse.core.Scheduler.Decision;

public class SchedulerTest {
    private static final long START = 1000000;
    private static final int INF = Settings.SHOTS_INFINITE;

    @Test
    public void steadyStateFiresOnEveryDeadline() {
        Rig rig = new Rig(new ShotPlan(2, 100, 0, 1), CAPTURED).captureMs(300);
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
        Rig rig = new Rig(new ShotPlan(5, 3, 2, 1), CAPTURED).captureMs(300);
        assertEquals(START + 120000, rig.scheduler.t0Ms());
        assertEquals(START + 120000, rig.scheduler.nextWakeMs());
        rig.run();
        assertEquals(START + 120000, rig.only(FIRE).get(0).nowMs);
        assertEquals(START + 130000, rig.only(FIRE).get(2).nowMs);
    }

    @Test
    public void captureSlowerThanTheIntervalSkipsSlotsWithoutBursts() {
        Rig rig = new Rig(new ShotPlan(2, 10, 0, 1), CAPTURED).captureMs(3000);
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
        Rig rig = new Rig(new ShotPlan(2, 10, 0, 1), CAPTURED).captureMs(300).slowFrame(5, 5000);
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
        Rig rig = new Rig(new ShotPlan(2, 8, 0, 1), CAPTURED).captureMs(300).lateWake(START + 6000, 5000);
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
        Rig rig = new Rig(new ShotPlan(10, 5, 0, 1), CAPTURED).captureMs(300);
        rig.runUntil(START + 15000);
        assertEquals(2, rig.only(FIRE).size());

        Scheduler s = rig.scheduler;
        s.pause();
        assertTrue(s.isPaused());
        assertEquals(NEVER, s.nextWakeMs());
        assertEquals(WAIT, s.onTimer().action);
        s.pause();

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
    public void pausingMidCaptureInterruptsThatSlot() {
        Rig rig = new Rig(new ShotPlan(10, 3, 0, 1), CAPTURED).captureMs(5000);
        rig.runUntil(START + 1000);
        Scheduler s = rig.scheduler;
        assertTrue(s.isCapturing());

        s.pause();
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
        s.pause();
        assertFalse(s.isPaused());

        s.start();
        s.onTimer();
        s.onFrameCaptured();
        assertTrue(s.isDone());
        s.pause();
        assertFalse(s.isPaused());
    }

    @Test
    public void burstFiresOnceAndEndsWhenThePollSeesTheEnd() {
        Rig rig = new Rig(new ShotPlan(0, 10, 0, 1), CAPTURED).pollMs(500);
        rig.run();

        Scheduler s = rig.scheduler;
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
        Rig rig = new Rig(new ShotPlan(0, INF, 0, 1), CAPTURED).pollMs(500);
        rig.runUntil(START + 3600000);
        assertFalse(rig.scheduler.isDone());
        assertTrue(rig.scheduler.isCapturing());
        assertEquals(NEVER, rig.scheduler.nextWakeMs());
        assertEquals(1, rig.only(FIRE).size());
    }

    @Test
    public void pausedBurstRestartsOnResumeAndKeepsItsLength() {
        FakeClock clock = new FakeClock(START);
        Scheduler s = new Scheduler(new ShotPlan(0, 10, 0, 1), clock, SLOTS);
        s.start();
        assertEquals(FIRE, s.onTimer().action);
        clock.advance(3000);
        s.pause();
        assertEquals(1, s.skipped(INTERRUPTED));

        clock.advance(60000);
        s.resume();
        assertEquals(START + 60000, s.nextWakeMs());
        assertEquals(FIRE, s.onTimer().action);
        assertEquals(START + 70000, s.nextWakeMs());
        clock.set(START + 70000);
        assertEquals(SLOT_DONE, s.onFrameCaptured().action);
        assertTrue(s.isDone());
        assertEquals(2, s.framesFired());
    }

    // 10 000 slots with every timer wake-up 0-150 ms late: each shot is still timed from its own
    // deadline, so the last one is due exactly 9 999 intervals after the first.
    @Test
    public void tenThousandSlotsAccumulateNoDrift() {
        Rig rig = new Rig(new ShotPlan(0.5, 10000, 0, 1), CAPTURED).captureMs(200).jitterUpToMs(150);
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
        Rig rig = new Rig(new ShotPlan(5, 4, 0, 3), CAPTURED).captureMs(400);
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
    public void bracketsSlowerThanTheIntervalSkipTheNextSlot() {
        Rig rig = new Rig(new ShotPlan(1, 3, 0, 3), CAPTURED).captureMs(400);
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
        Rig rig = new Rig(new ShotPlan(2, 6, 0, 1), SLOTS).captureMs(3000);
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
        Rig rig = new Rig(new ShotPlan(2, 3, 0, 1), SLOTS).captureMs(300).lateWake(START + 4000, 2500);
        rig.run();
        Scheduler s = rig.scheduler;
        assertTrue(s.isDone());
        assertEquals(LATE, rig.last(SKIP).skipReason);
        assertEquals(2, s.captured());
        assertEquals(NEVER, s.nextWakeMs());
    }

    @Test
    public void infiniteShotsKeepGoing() {
        Rig rig = new Rig(new ShotPlan(1, INF, 0, 1), CAPTURED).captureMs(300);
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
        assertEquals(NEVER, s.nextWakeMs());

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

    // Drives a Scheduler the way ShootActivity does: one timer armed for nextWakeMs(), possibly
    // late, and the camera's onShutter some time after each FIRE. In burst mode onShutter is a
    // poll every pollMs while the burst runs. A capture that ends exactly at a deadline is
    // reported before the timer.
    private static final class Rig {
        final FakeClock clock = new FakeClock(START);
        final Scheduler scheduler;
        final List<Decision> decisions = new ArrayList<Decision>();

        private long captureMs = 300;
        private long pollMs;
        private int slowFrame = -1;
        private long slowFrameMs;
        private long lateWakeAt = -1;
        private long lateWakeBy;
        private long jitterMax;
        private int framesFired;
        private long shutterAt = NEVER;

        Rig(ShotPlan plan, Scheduler.StopOn stopOn) {
            scheduler = new Scheduler(plan, clock, stopOn);
            scheduler.start();
        }

        Rig captureMs(long ms) {
            captureMs = ms;
            return this;
        }

        Rig pollMs(long ms) {
            pollMs = ms;
            return this;
        }

        Rig slowFrame(int frame, long ms) {
            slowFrame = frame;
            slowFrameMs = ms;
            return this;
        }

        Rig lateWake(long wake, long byMs) {
            lateWakeAt = wake;
            lateWakeBy = byMs;
            return this;
        }

        Rig jitterUpToMs(long ms) {
            jitterMax = ms;
            return this;
        }

        void dropPendingShutter() {
            shutterAt = NEVER;
        }

        void run() {
            runUntil(NEVER);
        }

        void runUntil(long untilMs) {
            while (!scheduler.isDone()) {
                long wake = scheduler.nextWakeMs();
                long timerAt = wake == NEVER ? NEVER : Math.max(clock.nowMs(), wake + lateness(wake));
                long next = Math.min(timerAt, shutterAt);
                if (next == NEVER || next > untilMs)
                    break;
                clock.set(next);
                if (shutterAt <= timerAt) {
                    shutterAt = NEVER;
                    record(scheduler.onFrameCaptured());
                } else {
                    record(scheduler.onTimer());
                }
            }
            if (untilMs != NEVER && clock.nowMs() < untilMs)
                clock.set(untilMs);
        }

        List<Decision> only(Action action) {
            List<Decision> out = new ArrayList<Decision>();
            for (Decision d : decisions)
                if (d.action == action)
                    out.add(d);
            return out;
        }

        Decision last(Action action) {
            List<Decision> all = only(action);
            return all.get(all.size() - 1);
        }

        private void record(Decision d) {
            decisions.add(d);
            if (d.action == FIRE) {
                long ms = pollMs > 0 ? pollMs : framesFired == slowFrame ? slowFrameMs : captureMs;
                framesFired++;
                shutterAt = clock.nowMs() + ms;
            } else if (d.action == WAIT && pollMs > 0 && scheduler.isCapturing() && shutterAt == NEVER) {
                shutterAt = clock.nowMs() + pollMs;
            }
        }

        private long lateness(long wake) {
            if (wake == lateWakeAt)
                return lateWakeBy;
            if (jitterMax == 0)
                return 0;
            long h = (wake * 6364136223846793005L + 1442695040888963407L) >>> 33;
            return h % (jitterMax + 1);
        }
    }
}
