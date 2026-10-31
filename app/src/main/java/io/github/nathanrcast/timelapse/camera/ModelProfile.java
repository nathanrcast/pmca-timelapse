package io.github.nathanrcast.timelapse.camera;

import io.github.nathanrcast.timelapse.core.camera.Feature;
import io.github.nathanrcast.timelapse.core.camera.Quirk;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

// Static per-body overrides for what reflection can't see, keyed on DeviceInfo.getModel(). Conservative by design:
// a profile can only switch a feature off or add a quirk, never enable something the probe didn't find. Entries are
// expected behaviour until the body's CAPS file and a camera session confirm them (docs/cameras.md).
public final class ModelProfile {
    public static final String DEFAULT_NAME = "default";

    private static final ModelProfile DEFAULT = new ModelProfile(DEFAULT_NAME, DEFAULT_NAME, false,
            EnumSet.noneOf(Feature.class), EnumSet.noneOf(Quirk.class));

    private static final ModelProfile[] KNOWN = {
            // Every model starting with NEX-. No NEX body has a silent shutter (upstream issue 5 reports it for the
            // NEX-5R/5T; the NEX-5T CAPS confirms it). The NEX-5T wheel does report (KURU 522/523), so no dial quirk.
            new ModelProfile("NEX", "NEX-", true, EnumSet.of(Feature.SILENT_SHUTTER), EnumSet.noneOf(Quirk.class)),
            new ModelProfile("ILCE-6500", "ILCE-6500", false, EnumSet.noneOf(Feature.class),
                    EnumSet.noneOf(Quirk.class)),
    };

    private final String name;
    private final String match;
    private final boolean prefix;
    private final Set<Feature> disabled;
    private final Set<Quirk> quirks;

    private ModelProfile(String name, String match, boolean prefix, Set<Feature> disabled, Set<Quirk> quirks) {
        this.name = name;
        this.match = match;
        this.prefix = prefix;
        this.disabled = Collections.unmodifiableSet(disabled);
        this.quirks = Collections.unmodifiableSet(quirks);
    }

    // Case and surrounding whitespace are ignored. An exact match wins over a prefix match.
    public static ModelProfile forModel(String model) {
        if (model == null)
            return DEFAULT;
        String key = model.trim().toUpperCase(Locale.US);
        for (ModelProfile p : KNOWN)
            if (!p.prefix && p.match.equals(key))
                return p;
        for (ModelProfile p : KNOWN)
            if (p.prefix && key.startsWith(p.match) && key.length() > p.match.length())
                return p;
        return DEFAULT;
    }

    public String name() {
        return name;
    }

    public Set<Feature> disabled() {
        return disabled;
    }

    public Set<Quirk> quirks() {
        return quirks;
    }
}
