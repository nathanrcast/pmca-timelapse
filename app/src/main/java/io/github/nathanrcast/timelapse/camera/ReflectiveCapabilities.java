package io.github.nathanrcast.timelapse.camera;

import io.github.nathanrcast.timelapse.core.camera.ProbedCapabilities;
import io.github.nathanrcast.timelapse.core.camera.Query;
import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The probe: resolves each SonyMethod with getMethod and reads each Query by Method.invoke. Nothing here calls a
// Sony method directly, and every lookup and read catches Throwable (NoSuchMethodException, NoClassDefFoundError,
// LinkageError, firmware exceptions), so it runs on any firmware and in JVM tests against the stubs jar.
public final class ReflectiveCapabilities {
    private static final String SONY_HARDWARE = "com.sony.scalar.hardware.";
    private static final String ANDROID_HARDWARE = "android.hardware.";

    private ReflectiveCapabilities() {}

    // The SonyMethods this firmware has. A method is absent if its class, any parameter type or the method itself
    // can't be resolved.
    public static Map<SonyMethod, Method> resolve(ClassLoader loader) {
        Map<SonyMethod, Method> found = new EnumMap<SonyMethod, Method>(SonyMethod.class);
        Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
        for (SonyMethod m : SonyMethod.values()) {
            try {
                Class<?> owner = load(loader, classes, m.owner());
                String[] names = m.parameterTypes();
                Class<?>[] types = new Class<?>[names.length];
                for (int i = 0; i < names.length; i++)
                    types[i] = load(loader, classes, names[i]);
                found.put(m, owner.getMethod(m.methodName(), types));
            } catch (Throwable ignored) {
            }
        }
        return found;
    }

    // Simple names as SonyMethod writes them back to class names: CameraEx* -> com.sony.scalar.hardware.,
    // Camera$* -> android.hardware., String -> java.lang.String, plus int and boolean.
    static String className(String simpleName) {
        if (simpleName.startsWith("CameraEx") || simpleName.startsWith("CameraSequence"))
            return SONY_HARDWARE + simpleName;
        if (simpleName.startsWith("Camera$"))
            return ANDROID_HARDWARE + simpleName;
        if (simpleName.equals("String"))
            return "java.lang.String";
        return simpleName;
    }

    private static Class<?> load(ClassLoader loader, Map<String, Class<?>> cache, String simpleName)
            throws ClassNotFoundException {
        if (simpleName.equals("int"))
            return int.class;
        if (simpleName.equals("boolean"))
            return boolean.class;
        Class<?> c = cache.get(simpleName);
        if (c == null) {
            c = Class.forName(className(simpleName), false, loader);
            cache.put(simpleName, c);
        }
        return c;
    }

    // Reads every Query whose getter is present by invoking it on the ParametersModifier. A query that is absent or
    // throws is left out (unknown). focusModes is Camera.Parameters.getSupportedFocusModes(), read by the caller.
    public static Map<Query, List<String>> readValues(Map<SonyMethod, Method> methods, Object modifier,
                                                      Collection<?> focusModes) {
        Map<Query, List<String>> values = new EnumMap<Query, List<String>>(Query.class);
        for (Query q : Query.values()) {
            if (q.method() == null) {
                if (focusModes != null)
                    values.put(q, asStrings(focusModes));
                continue;
            }
            Method m = methods.get(q.method());
            if (m == null || modifier == null)
                continue;
            try {
                List<String> v = asStrings(m.invoke(modifier));
                if (v != null)
                    values.put(q, v);
            } catch (Throwable ignored) {
            }
        }
        return values;
    }

    // A list or array as its elements' String.valueOf (so 3 and "3" compare equal), anything else as one string.
    static List<String> asStrings(Object v) {
        if (v == null)
            return null;
        List<String> out = new ArrayList<String>();
        if (v instanceof Collection) {
            for (Object o : (Collection<?>) v)
                out.add(String.valueOf(o));
        } else if (v.getClass().isArray()) {
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++)
                out.add(String.valueOf(Array.get(v, i)));
        } else {
            return Collections.singletonList(String.valueOf(v));
        }
        return out;
    }

    public static ProbedCapabilities build(String model, Collection<SonyMethod> present,
                                           Map<Query, List<String>> values, boolean valuesRead) {
        ModelProfile profile = ModelProfile.forModel(model);
        return new ProbedCapabilities(model, profile.name(), present, values, profile.disabled(), profile.quirks(),
                valuesRead);
    }
}
