package io.github.nathanrcast.timelapse.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.camera.Feature;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility;
import io.github.nathanrcast.timelapse.core.camera.ProbedCapabilities;
import io.github.nathanrcast.timelapse.core.camera.Query;
import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReflectiveCapabilitiesTest {
    // Shaped like a ParametersModifier on an old firmware: one query works, one throws.
    public static class FakeModifier {
        public List<Object> getSupportedNumsOfBracketPicture() {
            return Arrays.<Object>asList(3, 5);
        }

        public boolean isSupportedSilentShutterMode() {
            throw new NoSuchMethodError("isSupportedSilentShutterMode");
        }

        public boolean isSupportedToggleFocusMode() {
            return false;
        }
    }

    // The JVM descriptor of a SonyMethod's parameter list, through the same name mapping resolve() uses.
    private static String parameterDescriptor(SonyMethod m) {
        StringBuilder sb = new StringBuilder("(");
        for (String t : m.parameterTypes()) {
            if (t.equals("int"))
                sb.append('I');
            else if (t.equals("boolean"))
                sb.append('Z');
            else
                sb.append('L').append(ReflectiveCapabilities.className(t).replace('.', '/')).append(';');
        }
        return sb.append(')').toString();
    }

    private static String declared(SonyMethod m) throws Exception {
        String prefix = m.methodName() + parameterDescriptor(m);
        for (String s : StubsJar.read(ReflectiveCapabilities.className(m.owner())).publicMethods)
            if (s.startsWith(prefix))
                return s;
        return null;
    }

    @Test
    public void everySonyMethodIsDeclaredPublicInTheStubs() throws Exception {
        List<String> missing = new ArrayList<String>();
        for (SonyMethod m : SonyMethod.values())
            if (declared(m) == null)
                missing.add(m.key());
        assertEquals(Collections.emptyList(), missing);
    }

    @Test
    public void aClassTheVmRefusesCountsAsAbsent() throws Exception {
        // Defining a stub class throws ClassFormatError; resolve() must contain it like any missing method.
        assertTrue(ReflectiveCapabilities.resolve(StubsJar.loader()).isEmpty());
    }

    @Test
    public void firmwareWithoutSonyClassesHasNoMethodsAndProbeDoesNotThrow() {
        ClassLoader bare = new URLClassLoader(new URL[0], null);
        Map<SonyMethod, Method> found = ReflectiveCapabilities.resolve(bare);
        assertTrue(found.isEmpty());

        Map<Query, List<String>> values = ReflectiveCapabilities.readValues(found, new Object(), null);
        assertTrue(values.isEmpty());
        ProbedCapabilities caps = ReflectiveCapabilities.build("NEX-5T", found.keySet(), values, true);
        OptionVisibility v = OptionVisibility.of(caps);
        assertFalse(v.silentShutter() || v.aeLock() || v.bracket3() || v.holyGrail() || v.toggleFocus());
        assertFalse(caps.describe().isEmpty());
    }

    @Test
    public void queriesAreListOrBooleanGettersOnTheStubs() throws Exception {
        for (Query q : Query.values()) {
            if (q.method() == null)
                continue;
            String d = declared(q.method());
            assertTrue(q.name() + " is " + d, d.equals(q.method().methodName() + "()Z")
                    || d.equals(q.method().methodName() + "()Ljava/util/List;"));
        }
    }

    @Test
    public void readValuesKeepsWorkingQueriesAndDropsThrowingOnes() throws Exception {
        Map<SonyMethod, Method> methods = new EnumMap<SonyMethod, Method>(SonyMethod.class);
        methods.put(SonyMethod.GET_SUPPORTED_NUMS_OF_BRACKET_PICTURE,
                FakeModifier.class.getMethod("getSupportedNumsOfBracketPicture"));
        methods.put(SonyMethod.IS_SUPPORTED_SILENT_SHUTTER_MODE,
                FakeModifier.class.getMethod("isSupportedSilentShutterMode"));
        methods.put(SonyMethod.IS_SUPPORTED_TOGGLE_FOCUS_MODE,
                FakeModifier.class.getMethod("isSupportedToggleFocusMode"));
        // A Method invoked on an object of the wrong class throws IllegalArgumentException: also dropped.
        methods.put(SonyMethod.GET_SUPPORTED_DRIVE_MODES, String.class.getMethod("length"));

        Map<Query, List<String>> v = ReflectiveCapabilities.readValues(methods, new FakeModifier(),
                Arrays.asList("auto", "manual"));
        assertEquals(Arrays.asList("3", "5"), v.get(Query.BRACKET_PICTURE_COUNTS));
        assertEquals(Collections.singletonList("false"), v.get(Query.TOGGLE_FOCUS_SUPPORTED));
        assertEquals(Arrays.asList("auto", "manual"), v.get(Query.FOCUS_MODES));
        assertFalse(v.containsKey(Query.SILENT_SHUTTER_SUPPORTED));
        assertFalse(v.containsKey(Query.DRIVE_MODES));
        assertFalse(v.containsKey(Query.ISO_SENSITIVITIES));
    }

    @Test
    public void readValuesWithoutACameraReadsNothing() {
        Map<SonyMethod, Method> methods = new EnumMap<SonyMethod, Method>(SonyMethod.class);
        assertTrue(ReflectiveCapabilities.readValues(methods, null, null).isEmpty());
    }

    @Test
    public void asStringsComparesNumbersAsText() {
        assertNull(ReflectiveCapabilities.asStrings(null));
        assertEquals(Collections.singletonList("true"), ReflectiveCapabilities.asStrings(Boolean.TRUE));
        assertEquals(Arrays.asList("1", "2"), ReflectiveCapabilities.asStrings(new int[] {1, 2}));
        assertEquals(Arrays.asList("3", "null"), ReflectiveCapabilities.asStrings(Arrays.asList(3, null)));
    }

    @Test
    public void featureValuesMatchTheSonyConstants() throws Exception {
        Map<String, Object> c = StubsJar.read("com.sony.scalar.hardware.CameraEx$ParametersModifier").constants;
        assertEquals(c.get("DRIVE_MODE_BURST"), Feature.DRIVE_MODE_BURST);
        assertEquals(c.get("DRIVE_MODE_BRACKET"), Feature.DRIVE_MODE_BRACKET);
        assertEquals(c.get("AE_LOCK_ON"), Feature.AE_LOCK_ON);
        assertEquals(c.get("BRACKET_MODE_EXPOSURE"), Feature.BRACKET_MODE_EXPOSURE);
        assertEquals(c.get("EXPOSURE_BRACKET_MODE_SINGLE"), Feature.EXPOSURE_BRACKET_MODE_SINGLE);
        assertEquals(c.get("FOCUS_MODE_MANUAL"), Feature.FOCUS_MODE_MANUAL_VALUE);
    }

    @Test
    public void classNamesMapBackToPackages() {
        assertEquals("com.sony.scalar.hardware.CameraEx$ParametersModifier",
                ReflectiveCapabilities.className("CameraEx$ParametersModifier"));
        assertEquals("android.hardware.Camera$Parameters", ReflectiveCapabilities.className("Camera$Parameters"));
        assertEquals("java.lang.String", ReflectiveCapabilities.className("String"));
    }
}
