package io.github.nathanrcast.timelapse.core.camera;

import static io.github.nathanrcast.timelapse.core.camera.Probes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

public class OptionVisibilityTest {
    // A Capabilities that knows nothing: every method absent, every value unknown, nothing supported.
    private static final Capabilities NOTHING = new Capabilities() {
        @Override
        public String model() {
            return null;
        }

        @Override
        public boolean has(SonyMethod method) {
            return false;
        }

        @Override
        public List<String> values(Query query) {
            return null;
        }

        @Override
        public boolean supports(Feature feature) {
            return false;
        }

        @Override
        public boolean hasQuirk(Quirk quirk) {
            return false;
        }
    };

    @Test
    public void missingEveryOptionalMethodHidesEveryCameraOption() {
        OptionVisibility v = OptionVisibility.of(caps(core(), fullValues()));
        assertFalse(v.silentShutter());
        assertFalse(v.aeLock());
        assertFalse(v.bracket3());
        assertFalse(v.holyGrail());
        assertFalse(v.toggleFocus());
        // MF falls back to Camera.Parameters.setFocusMode, which needs no Sony method.
        assertEquals(FocusControl.FOCUS_MODE, v.focusControl());
    }

    @Test
    public void missingEveryOptionalMethodAndNoValuesStillDecidesWithoutThrowing() {
        OptionVisibility v = OptionVisibility.of(caps(core(), noValues()));
        assertFalse(v.silentShutter() || v.aeLock() || v.bracket3() || v.holyGrail() || v.toggleFocus());
        assertTrue(v.manualFocus());
    }

    @Test
    public void aFakeThatSupportsNothingHidesEverythingIncludingMf() {
        OptionVisibility v = OptionVisibility.of(NOTHING);
        assertFalse(v.silentShutter() || v.aeLock() || v.bracket3() || v.holyGrail() || v.manualFocus());
        assertEquals(FocusControl.NONE, v.focusControl());
    }

    @Test
    public void fullBodyShowsEverythingAndUsesToggleFocus() {
        OptionVisibility v = OptionVisibility.of(caps(all(), fullValues()));
        assertTrue(v.silentShutter());
        assertTrue(v.aeLock());
        assertTrue(v.bracket3());
        assertTrue(v.holyGrail());
        assertTrue(v.toggleFocus());
        assertEquals(FocusControl.TOGGLE, v.focusControl());
    }

    @Test
    public void silentShutterNeedsTheSupportQueryToAnswerTrue() {
        // Upstream issue 4: the setter alone isn't enough, the query must exist and say yes.
        assertFalse(OptionVisibility.of(caps(without(all(), SonyMethod.IS_SUPPORTED_SILENT_SHUTTER_MODE),
                fullValues())).silentShutter());
        assertFalse(OptionVisibility.of(caps(all(),
                with(fullValues(), Query.SILENT_SHUTTER_SUPPORTED, list("false")))).silentShutter());
        assertFalse(OptionVisibility.of(caps(all(),
                with(fullValues(), Query.SILENT_SHUTTER_SUPPORTED, null))).silentShutter());
        assertFalse(OptionVisibility.of(caps(without(all(), SonyMethod.SET_SILENT_SHUTTER_MODE),
                fullValues())).silentShutter());
    }

    @Test
    public void holyGrailHiddenWhenAnyRampMethodIsMissing() {
        for (SonyMethod m : Feature.HOLY_GRAIL.requiredMethods())
            assertFalse(m.name(), OptionVisibility.of(caps(without(all(), m), fullValues())).holyGrail());
        assertTrue(OptionVisibility.of(caps(all(), noValues())).holyGrail());
    }

    @Test
    public void bracketNeedsEveryCallAndListedValues() {
        for (SonyMethod m : Feature.BRACKET_3.requiredMethods())
            assertFalse(m.name(), OptionVisibility.of(caps(without(all(), m), fullValues())).bracket3());
        assertFalse(OptionVisibility.of(caps(all(),
                with(fullValues(), Query.DRIVE_MODES, list("single", "burst")))).bracket3());
        assertFalse(OptionVisibility.of(caps(all(),
                with(fullValues(), Query.BRACKET_PICTURE_COUNTS, list("5", "9")))).bracket3());
        // An unknown list doesn't veto: the method check decides.
        assertTrue(OptionVisibility.of(caps(all(), with(fullValues(), Query.DRIVE_MODES, null))).bracket3());
    }

    @Test
    public void aelHiddenWhenLockIsNotListed() {
        assertFalse(OptionVisibility.of(caps(all(), with(fullValues(), Query.AE_LOCKS, list("unlocked")))).aeLock());
        assertTrue(OptionVisibility.of(caps(all(), with(fullValues(), Query.AE_LOCKS, null))).aeLock());
    }

    @Test
    public void toggleFocusFallsBackToFocusModeThenToNone() {
        OptionVisibility noToggle = OptionVisibility.of(caps(all(),
                with(fullValues(), Query.TOGGLE_FOCUS_SUPPORTED, list("false"))));
        assertEquals(FocusControl.FOCUS_MODE, noToggle.focusControl());

        OptionVisibility noManual = OptionVisibility.of(caps(without(all(), SonyMethod.SET_TOGGLE_FOCUS_MODE),
                with(fullValues(), Query.FOCUS_MODES, list("auto", "af-s"))));
        assertEquals(FocusControl.NONE, noManual.focusControl());
        assertFalse(noManual.manualFocus());
    }

    @Test
    public void profileDisabledFeatureIsHiddenEvenWhenTheProbeFindsIt() {
        ProbedCapabilities caps = caps(all(), fullValues(), EnumSet.of(Feature.SILENT_SHUTTER, Feature.TOGGLE_FOCUS));
        OptionVisibility v = OptionVisibility.of(caps);
        assertFalse(v.silentShutter());
        assertEquals(FocusControl.FOCUS_MODE, v.focusControl());
        assertTrue(v.aeLock() && v.bracket3() && v.holyGrail());
    }
}
