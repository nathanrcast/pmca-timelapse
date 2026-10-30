package io.github.nathanrcast.timelapse.core.camera;

import static io.github.nathanrcast.timelapse.core.camera.Probes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class BracketPlanTest {
    private static Map<Query, List<String>> state(String drive, String count, String period) {
        Map<Query, List<String>> v = with(fullValues(), Query.BRACKET_MODE, list("exposure"));
        v = with(v, Query.DRIVE_MODE, drive == null ? null : list(drive));
        v = with(v, Query.NUM_OF_BRACKET_PICTURE, count == null ? null : list(count));
        v = with(v, Query.EXPOSURE_BRACKET_PERIOD, period == null ? null : list(period));
        return with(v, Query.EXPOSURE_BRACKET_PERIODS, list("3", "7", "10", "20", "30"));
    }

    private static BracketPlan resolve(Map<Query, List<String>> values, boolean requested) {
        return BracketPlan.resolve(caps(all(), values), true, requested);
    }

    @Test
    public void keepsTheCamerasOwnStep() {
        for (String p : new String[] {"3", "7", "10", "20", "30"}) {
            BracketPlan plan = resolve(state("single", null, p), true);
            assertTrue(plan.active);
            assertEquals(Integer.parseInt(p), plan.periodTenths);
        }
    }

    @Test
    public void fallsBackToPointSevenWhenTheStepCantBeRead() {
        assertEquals(7, resolve(state("single", null, null), true).periodTenths);
        assertEquals(7, resolve(state("single", null, "0"), true).periodTenths);
        assertEquals(7, resolve(state("single", null, "abc"), true).periodTenths);
    }

    @Test
    public void aStepOutsideTheSupportedListFallsBack() {
        assertEquals(7, resolve(state("single", null, "15"), true).periodTenths);
    }

    @Test
    public void anUnknownSupportedListTrustsTheCurrentStep() {
        Map<Query, List<String>> v = with(state("single", null, "20"), Query.EXPOSURE_BRACKET_PERIODS, null);
        assertEquals(20, resolve(v, true).periodTenths);
    }

    @Test
    public void bracketDriveWithThreePicturesCountsWithTheBoxOff() {
        BracketPlan plan = resolve(state("bracket", "3", "10"), false);
        assertTrue(plan.active);
        assertEquals(10, plan.periodTenths);
    }

    @Test
    public void theBoxOffAndNotInBracketDriveIsOff() {
        assertFalse(resolve(state("single", "3", "10"), false).active);
        assertFalse(resolve(state(null, null, null), false).active);
    }

    @Test
    public void theBoxTurnsItOnWhenTheCameraIsNotBracketing() {
        BracketPlan plan = resolve(state("single", "3", "10"), true);
        assertTrue(plan.active);
        
    }

    @Test
    public void stepsAreComparedAsNumbers() {
        Map<Query, List<String>> v = with(state("single", null, "7"), Query.EXPOSURE_BRACKET_PERIODS,
                list("03", " 7 ", "10"));
        BracketPlan plan = resolve(v, true);
        assertEquals(7, plan.periodTenths);
        assertNull(plan.note);
        v = with(state("single", null, "10"), Query.EXPOSURE_BRACKET_PERIODS, list("03", "7"));
        plan = resolve(v, true);
        assertEquals(7, plan.periodTenths);
        assertNotNull(plan.note);
    }

    @Test
    public void anUnparseableSupportedListTrustsTheCurrentStep() {
        Map<Query, List<String>> v = with(state("single", null, "20"), Query.EXPOSURE_BRACKET_PERIODS,
                list("0.3", "0.7", "1.0", "2.0"));
        assertEquals(20, resolve(v, true).periodTenths);
        v = with(state("single", null, "20"), Query.EXPOSURE_BRACKET_PERIODS, list());
        assertEquals(20, resolve(v, true).periodTenths);
    }

    @Test
    public void onlyAnExposureBracketIsAutoDetected() {
        for (String mode : new String[] {"white-balance", "dro", null}) {
            Map<Query, List<String>> v = with(state("bracket", "3", "10"), Query.BRACKET_MODE,
                    mode == null ? null : list(mode));
            BracketPlan off = resolve(v, false);
            assertFalse(off.active);
            assertNotNull(off.note);
            assertTrue(off.describe().contains("not auto-detected"));
            assertTrue("the box still turns it on", resolve(v, true).active);
        }
    }

    @Test
    public void bracketDriveWithAnotherCountFallsBackToSingleFrames() {
        for (boolean box : new boolean[] {false, true}) {
            BracketPlan plan = resolve(state("bracket", "5", "10"), box);
            assertFalse(plan.active);
            assertNotNull(plan.unsupportedReason);
        }
    }

    @Test
    public void bracketDriveWithAnUnreadableCountIsNotDetected() {
        assertFalse(resolve(state("bracket", null, "10"), false).active);
        assertTrue(resolve(state("bracket", null, "10"), true).active);
        assertEquals(10, resolve(state("bracket", null, "10"), true).periodTenths);
    }

    @Test
    public void aBodyWithoutBracketingIsOff() {
        assertFalse(BracketPlan.resolve(caps(all(), state("bracket", "3", "10")), false, true).active);
    }

    private static Settings settings(boolean brs, int interval) {
        Settings s = new Settings();
        s.brs = brs;
        s.intervalIndex = interval;
        s.deriveFromIndex();
        return s;
    }

    @Test
    public void maskSwitchesTheBoxOnAndTheShotPlanFollows() {
        Settings s = settings(false, 5);
        RunOptions run = RunOptions.mask(caps(all(), state("bracket", "3", "7")), s);
        assertTrue(run.bracket.active && s.brs);
        assertEquals(3, ShotPlan.of(s).framesPerInterval());
        assertTrue(run.masked().isEmpty());
    }

    @Test
    public void maskTurnsAnUnsupportedCountOffAndSaysWhy() {
        Settings s = settings(true, 5);
        RunOptions run = RunOptions.mask(caps(all(), state("bracket", "5", "7")), s);
        assertFalse(run.bracket.active || s.brs);
        assertEquals(1, ShotPlan.of(s).framesPerInterval());
        assertEquals(1, run.masked().size());
        assertTrue(run.masked().get(0).startsWith("BRACKET_3 (camera is set to 5"));
    }

    @Test
    public void aBurstRunIsNotAutoDetectedAsBracketed() {
        Settings s = settings(false, 0);
        RunOptions run = RunOptions.mask(caps(all(), state("bracket", "3", "7")), s);
        assertTrue(run.burst);
        assertFalse(run.bracket.active || s.brs);
        assertNull(run.bracket.unsupportedReason);
    }

    @Test
    public void describeNamesTheStep() {
        assertTrue(resolve(state("bracket", "3", "7"), false).describe().contains("0.7 EV"));
        assertTrue(resolve(state("single", null, "30"), true).describe().contains("3.0 EV"));
        assertEquals("off", BracketPlan.off().describe());
    }
}
