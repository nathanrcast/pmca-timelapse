package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.ExposureRamp.Action;
import io.github.nathanrcast.timelapse.core.ExposureRamp.Kind;
import org.junit.Test;

public class RampControllerTest {
    private static final ExposureRamp.Tables T = ExposureRamp.Tables.DEFAULT;
    private static final int SS_1_60 = T.shutterIndex(1, 60);
    private static final int ISO_100 = T.isoIndex(100);

    private Settings settings() {
        Settings s = new Settings();
        s.holyGrail = true;
        s.interval = 10;
        s.cooldown = 3;
        s.averageExposureAmount = 1;
        s.holyGrailAllowExposureUp = true;
        s.holyGrailAllowExposureDown = true;
        s.deadbandIndex = 0;
        return s;
    }

    private RampController controller(Settings s) {
        return new RampController(new ExposureRamp(), s, 1, false);
    }

    @Test
    public void firstReadingBecomesTheTargetWhenUsingCurrent() {
        Settings s = settings();
        s.useCurrentExposure = true;
        RampController c = controller(s);
        c.onMeter(0.7f);
        c.onMeter(-2f);
        assertEquals(0.7f, c.targetEv(), 1e-6);
        assertEquals(-2f, c.measuredEv(), 1e-6);
    }

    @Test
    public void manualTargetIsConvertedFromThirds() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.targetExposure = -6;
        RampController c = controller(s);
        c.onMeter(5f);
        assertEquals(-2f, c.targetEv(), 1e-6);
    }

    @Test
    public void cooldownCountsSlotsAndResetsOnSuccess() {
        Settings s = settings();
        s.useCurrentExposure = false;
        RampController c = controller(s);
        Action a = null;
        for (int slot = 0; slot < 3; slot++) {
            a = c.onSlot(-1f, SS_1_60, ISO_100);
            assertEquals(Kind.NONE, a.kind);
        }
        a = c.onSlot(-1f, SS_1_60, ISO_100);
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
        c.onApplied(a, true);
        assertEquals(SS_1_60 + 1, c.shutterIndex());
        assertEquals(1, c.slotsSinceChange());
        assertEquals(Kind.NONE, c.onSlot(-1f, SS_1_60 + 1, ISO_100).kind);
        assertEquals(2, c.slotsSinceChange());
    }

    @Test
    public void failedApplyChangesNothing() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.cooldown = 1;
        RampController c = controller(s);
        c.onSlot(-1f, SS_1_60, ISO_100);
        Action a = c.onSlot(-1f, SS_1_60, ISO_100);
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
        c.onApplied(a, false);
        assertEquals(SS_1_60, c.shutterIndex());
        assertEquals(2, c.slotsSinceChange());
        c.onApplied(new Action(Kind.NONE, ""), true);
        assertEquals(2, c.slotsSinceChange());
        assertEquals(Kind.SHUTTER_LONGER, c.onSlot(-1f, SS_1_60, ISO_100).kind);
    }

    @Test
    public void isoStepsTrackAndGiveTheValueToSet() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.cooldown = 1;
        s.maxShutterSpeedIndex = SS_1_60;
        RampController c = controller(s);
        c.onSlot(-1f, SS_1_60, ISO_100);
        Action up = c.onSlot(-1f, SS_1_60, ISO_100);
        assertEquals(Kind.ISO_UP, up.kind);
        assertEquals(T.isoAt(ISO_100 + 1), c.isoValueAfter(up));
        c.onApplied(up, true);
        assertEquals(ISO_100 + 1, c.isoIndex());
        c.onSlot(1f, SS_1_60, ISO_100 + 1);
        Action down = c.onSlot(1f, SS_1_60, ISO_100 + 1);
        assertEquals(Kind.ISO_DOWN, down.kind);
        assertEquals(T.isoAt(ISO_100), c.isoValueAfter(down));
        c.onApplied(down, true);
        assertEquals(ISO_100, c.isoIndex());
    }

    @Test
    public void shutterDownTracks() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.cooldown = 1;
        RampController c = controller(s);
        c.onSlot(1f, SS_1_60, ISO_100);
        Action a = c.onSlot(1f, SS_1_60, ISO_100);
        assertEquals(Kind.SHUTTER_SHORTER, a.kind);
        c.onApplied(a, true);
        assertEquals(SS_1_60 - 1, c.shutterIndex());
    }

    @Test
    public void unknownShutterKeepsLastKnownButUnknownIsoDoesNot() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.cooldown = 1;
        RampController c = controller(s);
        c.onSlot(-1f, SS_1_60, ISO_100);
        assertEquals(SS_1_60, c.shutterIndex());
        Action a = c.onSlot(-1f, ExposureRamp.UNKNOWN, ExposureRamp.UNKNOWN);
        assertEquals(SS_1_60, c.shutterIndex());
        assertEquals(ExposureRamp.UNKNOWN, c.isoIndex());
        assertEquals(Kind.SHUTTER_LONGER, a.kind);
    }

    @Test
    public void neverSeenShutterStaysUnknown() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.cooldown = 1;
        RampController c = controller(s);
        c.onSlot(-1f, ExposureRamp.UNKNOWN, ISO_100);
        Action a = c.onSlot(-1f, ExposureRamp.UNKNOWN, ISO_100);
        assertEquals(Kind.NONE, a.kind);
        assertTrue(a.reason, a.reason.contains("shutter unknown"));
    }

    @Test
    public void historyAveragesAndLimitFollowsPlan() {
        Settings s = settings();
        s.useCurrentExposure = false;
        s.averageExposureAmount = 2;
        s.maxShutterSpeedIndex = T.shutterCount() - 1;
        s.interval = 4;
        RampController c = new RampController(new ExposureRamp(), s, 3, false);
        c.onSlot(1f, SS_1_60, ISO_100);
        c.onSlot(3f, SS_1_60, ISO_100);
        c.onSlot(5f, SS_1_60, ISO_100);
        assertEquals(4f, c.averageEv(), 1e-6);
        assertEquals(T.shutterIndex(1, 2), c.maxShutterLimit());
        assertEquals(T, c.tables());
    }
}
