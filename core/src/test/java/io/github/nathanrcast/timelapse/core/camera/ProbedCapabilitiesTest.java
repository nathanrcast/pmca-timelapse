package io.github.nathanrcast.timelapse.core.camera;

import static io.github.nathanrcast.timelapse.core.camera.Probes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProbedCapabilitiesTest {
    @Test
    public void coreOnlyBodySupportsNoSonyFeature() {
        ProbedCapabilities caps = caps(core(), fullValues());
        for (Feature f : Feature.values())
            assertEquals(f.name(), f == Feature.FOCUS_MODE_MANUAL, caps.supports(f));
    }

    @Test
    public void profileCanSwitchOffButNeverOn() {
        ProbedCapabilities off = caps(all(), fullValues(), EnumSet.of(Feature.AE_LOCK));
        assertFalse(off.supports(Feature.AE_LOCK));
        assertTrue(off.disabledByProfile(Feature.AE_LOCK));
        assertTrue(off.supports(Feature.SILENT_SHUTTER));

        // Not disabling anything can't make up for a missing method.
        ProbedCapabilities missing = caps(without(all(), SonyMethod.SET_AUTO_EXPOSURE_LOCK), fullValues());
        assertFalse(missing.supports(Feature.AE_LOCK));
    }

    @Test
    public void valuesAreCopiedAndNullListsStayUnknown() {
        Map<Query, List<String>> values = with(fullValues(), Query.ISO_SENSITIVITIES, null);
        List<String> drive = values.get(Query.DRIVE_MODES);
        ProbedCapabilities caps = caps(all(), values);
        drive.clear();
        assertEquals(list("single", "burst", "bracket"), caps.values(Query.DRIVE_MODES));
        assertNull(caps.values(Query.ISO_SENSITIVITIES));
    }

    @Test
    public void describeNamesAbsentMethodsAndOffFeatures() {
        ProbedCapabilities caps = new ProbedCapabilities("NEX-5T", "NEX-5T",
                without(all(), SonyMethod.SET_TOGGLE_FOCUS_MODE), noValues(),
                EnumSet.of(Feature.SILENT_SHUTTER), EnumSet.of(Quirk.DIAL_UNRELIABLE), false);
        String text = caps.describe().toString();
        assertTrue(text, text.contains("values NOT read"));
        assertTrue(text, text.contains("absent: CameraEx.setToggleFocusMode(boolean)"));
        assertTrue(text, text.contains("SILENT_SHUTTER (profile)"));
        assertTrue(text, text.contains("DRIVE_MODES=?"));
        assertTrue(text, text.contains("DIAL_UNRELIABLE"));
        assertTrue(caps.hasQuirk(Quirk.DIAL_UNRELIABLE));
        assertFalse(caps.valuesRead());
    }

    @Test
    public void sonyMethodKeysAreUniqueAndQueriesAreNoArgModifierGetters() {
        Set<String> keys = new HashSet<String>(SonyMethod.keys());
        assertEquals(SonyMethod.values().length, keys.size());
        assertTrue(keys.contains("CameraEx.open(int, CameraEx$OpenOptions)"));
        assertTrue(keys.contains("CameraEx$ParametersModifier.setSilentShutterMode(boolean)"));
        for (Query q : Query.values()) {
            if (q.method() == null)
                continue;
            assertEquals(q.name(), "CameraEx$ParametersModifier", q.method().owner());
            assertEquals(q.name(), 0, q.method().parameterTypes().length);
            assertFalse(q.name(), q.method().core());
        }
    }
}
