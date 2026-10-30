package io.github.nathanrcast.timelapse.core.camera;

import static io.github.nathanrcast.timelapse.core.camera.Probes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.SliderMapping;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;

public class RunOptionsTest {
    private static Settings everythingOn(boolean burst) {
        Settings s = new Settings();
        s.silentShutter = true;
        s.ael = true;
        s.brs = true;
        s.mf = true;
        s.holyGrail = true;
        s.rawInterval = burst ? 0 : 5;
        s.interval = SliderMapping.progressToInterval(s.rawInterval);
        return s;
    }

    @Test
    public void fullBodyKeepsEveryOption() {
        Settings s = everythingOn(true);
        RunOptions run = RunOptions.mask(caps(all(), fullValues()), s);
        assertTrue(run.masked().isEmpty());
        assertTrue(run.burst && run.aeLock && run.bracket3 && run.holyGrail);
        assertEquals(Boolean.TRUE, run.silentShutter);
        assertEquals(FocusControl.TOGGLE, run.focus);
        assertTrue(s.silentShutter && s.ael && s.brs && s.mf && s.holyGrail);
        assertEquals(0, s.interval, 0);
    }

    @Test
    public void missingEveryOptionalMethodMasksEverythingButFocusModeMf() {
        Settings s = everythingOn(true);
        RunOptions run = RunOptions.mask(caps(core(), noValues()), s);
        assertFalse(s.silentShutter || s.ael || s.brs || s.holyGrail);
        assertFalse(run.burst || run.aeLock || run.bracket3 || run.holyGrail);
        assertNull(run.silentShutter);
        assertTrue(s.mf);
        assertEquals(FocusControl.FOCUS_MODE, run.focus);
        assertEquals(Arrays.asList("SILENT_SHUTTER", "AE_LOCK", "BRACKET_3", "HOLY_GRAIL", "BURST (interval 1.0 s)"),
                run.masked());
    }

    @Test
    public void burstBecomesTheShortestIntervalWhenUnsupported() {
        Settings s = everythingOn(true);
        RunOptions run = RunOptions.mask(caps(all(), with(fullValues(), Query.DRIVE_MODES, list("single"))), s);
        assertFalse(run.burst);
        assertEquals(1, s.rawInterval);
        assertEquals(SliderMapping.progressToInterval(1), s.interval, 0);
        assertTrue(s.interval > 0);
        assertFalse(ShotPlan.of(s).isBurst());
    }

    @Test
    public void unsupportedBracketDropsTheFramesPerInterval() {
        Settings s = everythingOn(false);
        assertEquals(3, ShotPlan.of(s).framesPerInterval());
        RunOptions.mask(caps(without(all(), SonyMethod.SET_NUM_OF_BRACKET_PICTURE), fullValues()), s);
        assertEquals(1, ShotPlan.of(s).framesPerInterval());
    }

    @Test
    public void silentShutterIsSetEitherWayWhenSupportedAndLeftAloneWhenNot() {
        Settings off = everythingOn(false);
        off.silentShutter = false;
        assertEquals(Boolean.FALSE, RunOptions.mask(caps(all(), fullValues()), off).silentShutter);
        assertNull(RunOptions.mask(caps(all(), fullValues(), EnumSet.of(Feature.SILENT_SHUTTER)), off).silentShutter);
    }

    @Test
    public void mfOffMeansNoFocusControl() {
        Settings s = everythingOn(false);
        s.mf = false;
        assertEquals(FocusControl.NONE, RunOptions.mask(caps(all(), fullValues()), s).focus);
    }

    @Test
    public void optionsTheUserTurnedOffAreNotReportedAsMasked() {
        Settings s = new Settings();
        s.silentShutter = false;
        s.ael = false;
        s.brs = false;
        s.mf = false;
        s.holyGrail = false;
        s.rawInterval = 5;
        s.interval = SliderMapping.progressToInterval(5);
        assertTrue(RunOptions.mask(caps(core(), noValues()), s).masked().isEmpty());
    }

    @Test
    public void maskingIsIdempotent() {
        Settings s = everythingOn(true);
        RunOptions.mask(caps(core(), noValues()), s);
        assertTrue(RunOptions.mask(caps(core(), noValues()), s).masked().isEmpty());
    }
}
