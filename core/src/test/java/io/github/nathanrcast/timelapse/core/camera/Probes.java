package io.github.nathanrcast.timelapse.core.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Fake probe results for the capability tests.
final class Probes {
    private Probes() {}

    static Set<SonyMethod> core() {
        Set<SonyMethod> core = EnumSet.noneOf(SonyMethod.class);
        for (SonyMethod m : SonyMethod.values())
            if (m.core())
                core.add(m);
        return core;
    }

    static Set<SonyMethod> all() {
        return EnumSet.allOf(SonyMethod.class);
    }

    // What a fully featured body (an a6500, say) is expected to report.
    static Map<Query, List<String>> fullValues() {
        Map<Query, List<String>> v = new EnumMap<Query, List<String>>(Query.class);
        v.put(Query.SILENT_SHUTTER_SUPPORTED, list("true"));
        v.put(Query.TOGGLE_FOCUS_SUPPORTED, list("true"));
        v.put(Query.LONG_EXPOSURE_NR_SUPPORTED, list("true"));
        v.put(Query.DRIVE_MODES, list("single", "burst", "bracket"));
        v.put(Query.BURST_DRIVE_SPEEDS, list("low", "middle", "high"));
        v.put(Query.AE_LOCKS, list("unlocked", "locked"));
        v.put(Query.BRACKET_MODES, list("exposure", "white-balance"));
        v.put(Query.EXPOSURE_BRACKET_MODES, list("single", "continue"));
        v.put(Query.BRACKET_PICTURE_COUNTS, list("3", "5"));
        v.put(Query.ISO_SENSITIVITIES, list("100", "200", "400"));
        v.put(Query.FOCUS_MODES, list("auto", "manual"));
        return v;
    }

    static Map<Query, List<String>> noValues() {
        return new EnumMap<Query, List<String>>(Query.class);
    }

    static List<String> list(String... values) {
        return new ArrayList<String>(Arrays.asList(values));
    }

    static ProbedCapabilities caps(Set<SonyMethod> present, Map<Query, List<String>> values) {
        return caps(present, values, EnumSet.noneOf(Feature.class));
    }

    static ProbedCapabilities caps(Set<SonyMethod> present, Map<Query, List<String>> values, Set<Feature> disabled) {
        return new ProbedCapabilities("TEST", "default", present, values, disabled,
                Collections.<Quirk>emptySet(), true);
    }

    static Set<SonyMethod> without(Set<SonyMethod> methods, SonyMethod... removed) {
        Set<SonyMethod> out = EnumSet.noneOf(SonyMethod.class);
        out.addAll(methods);
        out.removeAll(Arrays.asList(removed));
        return out;
    }

    static Map<Query, List<String>> with(Map<Query, List<String>> values, Query q, List<String> v) {
        Map<Query, List<String>> out = new EnumMap<Query, List<String>>(Query.class);
        out.putAll(values);
        if (v == null)
            out.remove(q);
        else
            out.put(q, v);
        return out;
    }
}
