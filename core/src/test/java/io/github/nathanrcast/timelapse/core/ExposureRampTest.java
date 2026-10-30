package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.ExposureRamp.Action;
import io.github.nathanrcast.timelapse.core.ExposureRamp.History;
import io.github.nathanrcast.timelapse.core.ExposureRamp.Input;
import io.github.nathanrcast.timelapse.core.ExposureRamp.Kind;
import io.github.nathanrcast.timelapse.core.ExposureRamp.Mode;
import io.github.nathanrcast.timelapse.core.ExposureRamp.Tables;
import org.junit.Test;

public class ExposureRampTest {
    private static final Tables T = Tables.DEFAULT;
    private static final int SS_1_60 = T.shutterIndex(1, 60);
    private static final int SS_1_30 = T.shutterIndex(1, 30);
    private static final int ISO_100 = T.isoIndex(100);
    private static final int ISO_3200 = T.isoIndex(3200);

    private final ExposureRamp ramp = new ExposureRamp();

    private Input dark() {
        Input in = new Input();
        in.measuredEv = -1f;
        in.targetEv = 0f;
        in.shutterIndex = SS_1_60;
        in.isoIndex = ISO_100;
        in.maxShutterIndex = SS_1_30;
        in.maxIsoIndex = ISO_3200;
        in.intervalSec = 10;
        in.mode = Mode.BOTH;
        in.cooldownSlots = 3;
        in.slotsSinceChange = 3;
        return in;
    }

    private Input bright() {
        Input in = dark();
        in.measuredEv = 1f;
        return in;
    }

    @Test
    public void sunsetLengthensShutterFirst() {
        Action a = ramp.decide(dark());
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
        assertEquals(1, a.steps);
        assertTrue(a.reason, a.reason.contains("shutter"));
    }

    @Test
    public void sunsetRaisesIsoOnlyAtMaxShutter() {
        Input in = dark();
        in.shutterIndex = SS_1_30;
        Action a = ramp.decide(in);
        assertEquals(Kind.ISO_UP, a.kind);
        assertEquals(1, a.steps);
    }

    @Test
    public void sunsetStopsAtMaxShutterAndIso() {
        Input in = dark();
        in.shutterIndex = SS_1_30;
        in.isoIndex = ISO_3200;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
    }

    @Test
    public void sunriseLowersIsoFirstThenShutter() {
        Input in = bright();
        in.isoIndex = T.isoIndex(400);
        assertEquals(Kind.ISO_DOWN, ramp.decide(in).kind);
        in.isoIndex = ISO_100;
        assertEquals(Kind.SHUTTER_SHORTER, ramp.decide(in).kind);
        in.shutterIndex = 0;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
    }

    @Test
    public void sunriseIsoFloorIs100() {
        Input in = bright();
        in.isoIndex = T.isoIndex(64);
        assertEquals(Kind.SHUTTER_SHORTER, ramp.decide(in).kind);
    }

    @Test
    public void deadbandHoldsSmallErrors() {
        Input in = dark();
        in.deadbandEv = 0.5f;
        in.measuredEv = -0.5f;
        Action a = ramp.decide(in);
        assertEquals(Kind.NONE, a.kind);
        assertTrue(a.reason, a.reason.contains("deadband"));
        in.measuredEv = -0.6f;
        assertEquals(Kind.SHUTTER_LONGER, ramp.decide(in).kind);
    }

    @Test
    public void defaultStepCapIsOneStep() {
        Input in = dark();
        in.measuredEv = -5f;
        assertEquals(1, ramp.decide(in).steps);
    }

    @Test
    public void largerCapAllowsMoreStepsButNotPastTheError() {
        Input in = dark();
        in.measuredEv = -5f;
        in.maxStepEv = 1f;
        assertEquals(3, ramp.decide(in).steps);
        in.measuredEv = -0.2f;
        assertEquals(1, ramp.decide(in).steps);
    }

    @Test
    public void capStopsAtShutterLimit() {
        Input in = dark();
        in.measuredEv = -5f;
        in.maxStepEv = 3f;
        in.shutterIndex = SS_1_30 - 2;
        Action a = ramp.decide(in);
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
        assertEquals(2, a.steps);
    }

    @Test
    public void largerCapRaisesIsoAndLowersIsoInSteps() {
        Input in = dark();
        in.measuredEv = -5f;
        in.maxStepEv = 1f;
        in.shutterIndex = SS_1_30;
        Action up = ramp.decide(in);
        assertEquals(Kind.ISO_UP, up.kind);
        assertEquals(3, up.steps);
        Input down = bright();
        down.measuredEv = 5f;
        down.maxStepEv = 1f;
        down.isoIndex = T.isoIndex(800);
        Action d = ramp.decide(down);
        assertEquals(Kind.ISO_DOWN, d.kind);
        assertEquals(3, d.steps);
        down.isoIndex = ISO_100;
        down.shutterIndex = SS_1_30;
        assertEquals(Kind.SHUTTER_SHORTER, ramp.decide(down).kind);
        assertEquals(3, ramp.decide(down).steps);
    }

    @Test
    public void shutterClampedToHalfInterval() {
        Input in = dark();
        in.maxShutterIndex = T.shutterIndex(30, 1);
        in.intervalSec = 2;
        in.shutterIndex = T.shutterIndex(1, 1);
        // 1 s is exactly interval/2: at the limit, so ISO takes over.
        assertEquals(Kind.ISO_UP, ramp.decide(in).kind);
        in.shutterIndex = T.shutterIndex(1, 2);
        assertEquals(Kind.SHUTTER_LONGER, ramp.decide(in).kind);
        assertEquals(T.shutterIndex(1, 1), T.maxShutterIndex(T.shutterCount() - 1, 2));
    }

    @Test
    public void settingBeyondTableIsClampedAtUse() {
        Input in = dark();
        in.maxShutterIndex = 999;
        in.maxIsoIndex = 999;
        in.intervalSec = 1000;
        in.shutterIndex = T.shutterCount() - 1;
        in.isoIndex = T.isoCount() - 1;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
        in.isoIndex = T.isoCount() - 2;
        assertEquals(Kind.ISO_UP, ramp.decide(in).kind);
        in.maxIsoIndex = -4;
        in.maxShutterIndex = -4;
        in.shutterIndex = 0;
        in.isoIndex = 0;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
    }

    @Test
    public void burstIntervalCannotLengthen() {
        Input in = dark();
        in.intervalSec = 0;
        assertEquals(Kind.ISO_UP, ramp.decide(in).kind);
        assertEquals(0, T.maxShutterIndex(SS_1_30, 0));
    }

    @Test
    public void unknownIsoRampsShutterOnly() {
        Input in = dark();
        in.isoIndex = ExposureRamp.UNKNOWN;
        assertEquals(Kind.SHUTTER_LONGER, ramp.decide(in).kind);
        in.shutterIndex = SS_1_30;
        Action a = ramp.decide(in);
        assertEquals(Kind.NONE, a.kind);
        assertTrue(a.reason, a.reason.contains("ISO unknown"));
        Input b = bright();
        b.isoIndex = ExposureRamp.UNKNOWN;
        Action s = ramp.decide(b);
        assertEquals(Kind.SHUTTER_SHORTER, s.kind);
        assertTrue(s.reason, s.reason.contains("ISO unknown"));
    }

    @Test
    public void unknownShutterNeverBrightensButMayLowerIso() {
        Input in = dark();
        in.shutterIndex = ExposureRamp.UNKNOWN;
        Action a = ramp.decide(in);
        assertEquals(Kind.NONE, a.kind);
        assertTrue(a.reason, a.reason.contains("shutter unknown"));
        Input b = bright();
        b.shutterIndex = ExposureRamp.UNKNOWN;
        b.isoIndex = T.isoIndex(800);
        assertEquals(Kind.ISO_DOWN, ramp.decide(b).kind);
        b.isoIndex = ISO_100;
        assertEquals(Kind.NONE, ramp.decide(b).kind);
    }

    @Test
    public void cooldownCountsSlots() {
        Input in = dark();
        in.cooldownSlots = 5;
        for (int slots = 0; slots < 5; slots++) {
            in.slotsSinceChange = slots;
            Action a = ramp.decide(in);
            assertEquals(Kind.NONE, a.kind);
            assertTrue(a.reason, a.reason.contains("cooldown"));
        }
        in.slotsSinceChange = 5;
        assertEquals(Kind.SHUTTER_LONGER, ramp.decide(in).kind);
    }

    @Test
    public void modeGatesDirection() {
        Input in = dark();
        in.mode = Mode.DOWN;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
        in = bright();
        in.mode = Mode.UP;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
        in.mode = Mode.NONE;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
        assertEquals(Mode.BOTH, Mode.of(true, true));
        assertEquals(Mode.UP, Mode.of(true, false));
        assertEquals(Mode.DOWN, Mode.of(false, true));
        assertEquals(Mode.NONE, Mode.of(false, false));
    }

    @Test
    public void shutterIndexAtEdgesIsSafe() {
        Input in = dark();
        in.shutterIndex = T.shutterCount() + 3;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
        Input b = bright();
        b.shutterIndex = T.shutterCount() + 3;
        b.isoIndex = ISO_100;
        assertEquals(Kind.NONE, ramp.decide(b).kind);
        b.isoIndex = T.isoCount() + 2;
        assertEquals(Kind.NONE, ramp.decide(b).kind);
    }

    @Test
    public void targetUnitsAreThirdsOfEv() {
        assertEquals(1f, ExposureRamp.thirdsToEv(3), 1e-6);
        assertEquals(-10f, ExposureRamp.thirdsToEv(-30), 1e-6);
        assertEquals(0f, ExposureRamp.thirdsToEv(0), 1e-6);
        // A stored -3 (one EV below) against a meter reading of -1 EV is on target, not three EV off.
        Input in = dark();
        in.targetEv = ExposureRamp.thirdsToEv(-3);
        in.measuredEv = -1f;
        assertEquals(Kind.NONE, ramp.decide(in).kind);
    }

    @Test
    public void historyIsABoundedRing() {
        History h = new History(3);
        assertEquals(0f, h.average(3), 0);
        h.add(1);
        h.add(2);
        assertEquals(2, h.size());
        assertEquals(1.5f, h.average(5), 1e-6);
        h.add(3);
        h.add(10);
        h.add(20);
        assertEquals(3, h.size());
        assertEquals(20f, h.average(1), 1e-6);
        assertEquals(15f, h.average(2), 1e-6);
        assertEquals(11f, h.average(99), 1e-6);
        assertEquals(0f, new History(0).average(1), 0);
    }

    @Test
    public void isoMatchesNearestOrIsUnknown() {
        assertEquals(ISO_100, T.isoIndex(100));
        assertEquals(T.isoIndex(3200), T.isoIndex(3190));
        assertEquals(ExposureRamp.UNKNOWN, T.isoIndex(0));
        assertEquals(ExposureRamp.UNKNOWN, T.isoIndex(-1));
        assertEquals(ExposureRamp.UNKNOWN, T.isoIndex(0xFFFFFF));
        assertEquals(ExposureRamp.UNKNOWN, T.isoIndex(300000));
    }

    @Test
    public void shutterLookupIsExactOrUnknown() {
        assertEquals(SS_1_60, T.shutterIndex(1, 60));
        assertEquals(ExposureRamp.UNKNOWN, T.shutterIndex(65535, 1));
        assertEquals(ExposureRamp.UNKNOWN, T.shutterIndex(1, 7));
    }

    @Test
    public void defaultLimitsAre1Over30AndIso3200() {
        assertEquals(SS_1_30, Util.DEFAULT_MAX_SHUTTER_INDEX);
        assertEquals(ISO_3200, Util.DEFAULT_MAX_ISO_INDEX);
    }

    @Test
    public void tablesAreInjectable() {
        Tables t = new Tables(new int[][] {{1, 100}, {1, 50}, {1, 25}}, new int[] {100, 200, 400});
        assertEquals(3, t.shutterCount());
        assertEquals(3, t.isoCount());
        assertArrayEquals(new int[] {1, 50}, t.shutterAt(1));
        assertEquals(200, t.isoAt(1));
        assertEquals(0, t.minIsoIndex());
        assertEquals(1, new Tables(new int[][] {{1, 1}}, new int[] {50, 100}).minIsoIndex());
        Input in = dark();
        in.shutterIndex = 0;
        in.isoIndex = 0;
        in.maxShutterIndex = 2;
        in.maxIsoIndex = 2;
        Action a = new ExposureRamp(t).decide(in);
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
        assertEquals(t, new ExposureRamp(t).tables());
        assertTrue(a.toString().contains("SHUTTER_LONGER"));
        assertTrue(a.isNone() == false);
    }
}
