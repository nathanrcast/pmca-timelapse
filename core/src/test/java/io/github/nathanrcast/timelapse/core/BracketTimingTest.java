package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.StopOn.CAPTURED;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.FIRE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BracketTimingTest {
    @Test
    public void estimateIsThreeShuttersPlusWriteMargin() {
        BracketTiming t = new BracketTiming(new ShotPlan(2, 10, 0, 3));
        t.setShutterMs(100);
        assertEquals(3 * (100 + BracketTiming.WRITE_MS_PER_FRAME), t.estimateMs());
        assertNotNull(t.warning());
        assertTrue(t.warning().contains("~"));
    }

    @Test
    public void noWarningWhenTheIntervalIsLongEnough() {
        BracketTiming t = new BracketTiming(new ShotPlan(5, 10, 0, 3));
        t.setShutterMs(100);
        assertNull(t.warning());
    }

    @Test
    public void singleFramesBurstAndZeroIntervalNeverWarn() {
        BracketTiming single = new BracketTiming(new ShotPlan(1, 10, 0, 1));
        single.setShutterMs(5000);
        assertNull(single.warning());
        BracketTiming burst = new BracketTiming(new ShotPlan(0, 10, 0, 3));
        burst.setShutterMs(5000);
        assertNull(burst.warning());
    }

    @Test
    public void measuredSlotTimeReplacesTheEstimate() {
        ShotPlan plan = new ShotPlan(5, 3, 0, 3);
        SchedulerRig rig = new SchedulerRig(plan, CAPTURED).captureMs(2000);
        BracketTiming t = new BracketTiming(plan);
        t.setShutterMs(0);
        assertNull(t.warning());
        boolean updated = false;
        for (int step = 0; step < 6 && !t.isMeasured(); step++) {
            rig.runUntil(rig.clock.nowMs() + 1000);
            for (Scheduler.Decision d : rig.decisions)
                updated |= t.onDecision(d);
        }
        assertTrue(updated);
        assertEquals(6000, t.estimateMs());
        assertNotNull(t.warning());
        assertFalse(t.warning().contains("~"));
    }

    @Test
    public void aFastSlotClearsTheWarning() {
        ShotPlan plan = new ShotPlan(5, 3, 0, 3);
        BracketTiming t = new BracketTiming(plan);
        t.setShutterMs(2000);
        assertNotNull(t.warning());
        SchedulerRig rig = new SchedulerRig(plan, CAPTURED).captureMs(300);
        rig.run();
        for (Scheduler.Decision d : rig.decisions)
            t.onDecision(d);
        assertEquals(900, t.estimateMs());
        assertNull(t.warning());
        assertEquals(3, rig.only(FIRE).size() / 3);
    }

    @Test
    public void aShutterChangeMovesTheMeasuredEstimate() {
        ShotPlan plan = new ShotPlan(5, 3, 0, 3);
        BracketTiming t = new BracketTiming(plan);
        t.setShutterMs(100);
        SchedulerRig rig = new SchedulerRig(plan, CAPTURED).captureMs(700);
        rig.run();
        for (Scheduler.Decision d : rig.decisions)
            t.onDecision(d);
        assertEquals(2100, t.estimateMs());
        assertNull(t.warning());
        t.setShutterMs(1100);
        assertEquals(2100 + 3 * 1000, t.estimateMs());
        assertNotNull(t.warning());
    }

    @Test
    public void aResumedRunKeepsTheMeasurementOnlyForTheSameBracketSize() {
        ShotPlan plan = new ShotPlan(5, 3, 0, 3);
        BracketTiming old = new BracketTiming(plan);
        SchedulerRig rig = new SchedulerRig(plan, CAPTURED).captureMs(700);
        rig.run();
        for (Scheduler.Decision d : rig.decisions)
            old.onDecision(d);
        BracketTiming same = new BracketTiming(plan);
        same.inherit(old);
        assertTrue(same.isMeasured());
        BracketTiming other = new BracketTiming(new ShotPlan(5, 3, 0, 1));
        other.inherit(old);
        assertFalse(other.isMeasured());
    }
}
