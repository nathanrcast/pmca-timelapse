package io.github.nathanrcast.timelapse.camera;

import io.github.nathanrcast.timelapse.camera.model.Feature;
import io.github.nathanrcast.timelapse.camera.model.Quirk;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

// Static per-body overrides for what reflection can't see, keyed on DeviceInfo.getModel(). Conservative by design:
// a profile can only switch a feature off or add a quirk, never enable something the probe didn't find. Entries are
// expected behaviour until the body's CAPS file and a camera session confirm them (docs/cameras.md).
public final class ModelProfile {
    public static final String DEFAULT_NAME = "default";

    private static final ModelProfile DEFAULT = new ModelProfile(DEFAULT_NAME,
            EnumSet.noneOf(Feature.class), EnumSet.noneOf(Quirk.class));

    private static final ModelProfile[] KNOWN = {
            // Upstream issue 5: the NEX-5R/5T have no silent shutter, and their wheel isn't read reliably (T10).
            new ModelProfile("NEX-5T", EnumSet.of(Feature.SILENT_SHUTTER), EnumSet.of(Quirk.DIAL_UNRELIABLE)),
            new ModelProfile("ILCE-6500", EnumSet.noneOf(Feature.class), EnumSet.noneOf(Quirk.class)),
    };

    private final String name;
    private final Set<Feature> disabled;
    private final Set<Quirk> quirks;

    private ModelProfile(String name, Set<Feature> disabled, Set<Quirk> quirks) {
        this.name = name;
        this.disabled = Collections.unmodifiableSet(disabled);
        this.quirks = Collections.unmodifiableSet(quirks);
    }

    public static ModelProfile forModel(String model) {
        if (model != null) {
            String key = model.trim().toUpperCase(Locale.US);
            for (ModelProfile p : KNOWN)
                if (p.name.equals(key))
                    return p;
        }
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
