package io.github.nathanrcast.timelapse.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.camera.Feature;
import io.github.nathanrcast.timelapse.core.camera.ProbedCapabilities;
import io.github.nathanrcast.timelapse.core.camera.Query;
import io.github.nathanrcast.timelapse.core.camera.Quirk;
import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class ModelProfileTest {
    private static Map<Query, List<String>> silentShutterSupported() {
        Map<Query, List<String>> v = new EnumMap<Query, List<String>>(Query.class);
        v.put(Query.SILENT_SHUTTER_SUPPORTED, Arrays.asList("true"));
        return v;
    }

    @Test
    public void nex5tHasNoSilentShutterAndAnUnreliableDial() {
        ModelProfile p = ModelProfile.forModel("NEX-5T");
        assertEquals("NEX-5T", p.name());
        assertEquals(EnumSet.of(Feature.SILENT_SHUTTER), p.disabled());
        assertEquals(EnumSet.of(Quirk.DIAL_UNRELIABLE), p.quirks());
    }

    @Test
    public void a6500AndUnknownBodiesHaveNoOverrides() {
        ModelProfile a6500 = ModelProfile.forModel("ILCE-6500");
        assertEquals("ILCE-6500", a6500.name());
        assertTrue(a6500.disabled().isEmpty() && a6500.quirks().isEmpty());

        for (String model : new String[] {"ILCE-6000", "", null}) {
            ModelProfile p = ModelProfile.forModel(model);
            assertEquals(ModelProfile.DEFAULT_NAME, p.name());
            assertTrue(p.disabled().isEmpty() && p.quirks().isEmpty());
        }
    }

    @Test
    public void modelMatchIgnoresCaseAndWhitespace() {
        assertEquals("NEX-5T", ModelProfile.forModel(" nex-5t ").name());
    }

    @Test
    public void profileOverridesTheProbe() {
        EnumSet<SonyMethod> all = EnumSet.allOf(SonyMethod.class);
        ProbedCapabilities nex = ReflectiveCapabilities.build("NEX-5T", all, silentShutterSupported(), true);
        assertFalse(nex.supports(Feature.SILENT_SHUTTER));
        assertTrue(nex.disabledByProfile(Feature.SILENT_SHUTTER));
        assertTrue(nex.hasQuirk(Quirk.DIAL_UNRELIABLE));
        assertTrue(nex.supports(Feature.HOLY_GRAIL));
        assertEquals("NEX-5T", nex.profile());

        ProbedCapabilities a6500 = ReflectiveCapabilities.build("ILCE-6500", all, silentShutterSupported(), true);
        assertTrue(a6500.supports(Feature.SILENT_SHUTTER));
        assertFalse(a6500.hasQuirk(Quirk.DIAL_UNRELIABLE));
    }

    @Test
    public void noProfileEnablesAFeatureTheProbeLacks() {
        EnumSet<SonyMethod> noSilent = EnumSet.allOf(SonyMethod.class);
        noSilent.remove(SonyMethod.SET_SILENT_SHUTTER_MODE);
        for (String model : new String[] {"NEX-5T", "ILCE-6500", "other"})
            assertFalse(model, ReflectiveCapabilities.build(model, noSilent, silentShutterSupported(), true)
                    .supports(Feature.SILENT_SHUTTER));
    }

    @Test
    public void profileSetsAreReadOnly() {
        try {
            ModelProfile.forModel("ILCE-6500").disabled().add(Feature.AE_LOCK);
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("profile set was mutable");
    }
}
