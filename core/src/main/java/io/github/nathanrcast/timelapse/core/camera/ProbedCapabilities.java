package io.github.nathanrcast.timelapse.core.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The probe's findings merged with a model profile. A profile can only switch a feature off: supports() is
// Feature.allowedBy(probe) and not disabled by the profile.
public final class ProbedCapabilities implements Capabilities {
    private final String model;
    private final String profile;
    private final Set<SonyMethod> present = EnumSet.noneOf(SonyMethod.class);
    private final Map<Query, List<String>> values = new EnumMap<Query, List<String>>(Query.class);
    private final Set<Feature> disabled = EnumSet.noneOf(Feature.class);
    private final Set<Quirk> quirks = EnumSet.noneOf(Quirk.class);
    private final boolean valuesRead;

    public ProbedCapabilities(String model, String profile, Collection<SonyMethod> present,
                              Map<Query, List<String>> values, Collection<Feature> disabled,
                              Collection<Quirk> quirks, boolean valuesRead) {
        this.model = model;
        this.profile = profile;
        this.present.addAll(present);
        for (Map.Entry<Query, List<String>> e : values.entrySet())
            if (e.getValue() != null)
                this.values.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        this.disabled.addAll(disabled);
        this.quirks.addAll(quirks);
        this.valuesRead = valuesRead;
    }

    @Override
    public String model() {
        return model;
    }

    public String profile() {
        return profile;
    }

    // False when the probe couldn't open the camera, so every Query is unknown.
    public boolean valuesRead() {
        return valuesRead;
    }

    @Override
    public boolean has(SonyMethod method) {
        return present.contains(method);
    }

    @Override
    public List<String> values(Query query) {
        return values.get(query);
    }

    @Override
    public boolean supports(Feature feature) {
        return !disabled.contains(feature) && feature.allowedBy(this);
    }

    @Override
    public boolean hasQuirk(Quirk quirk) {
        return quirks.contains(quirk);
    }

    // The same body with this session's values. Method presence, profile and quirks carry over; a read that succeeded
    // replaces every value (a query that failed this time becomes unknown), and a failed read keeps the old values.
    public ProbedCapabilities refreshed(Map<Query, List<String>> fresh, boolean read) {
        if (!read)
            return this;
        return new ProbedCapabilities(model, profile, present, fresh, disabled, quirks, true);
    }

    public boolean disabledByProfile(Feature feature) {
        return disabled.contains(feature);
    }

    // The session's LOG.TXT summary: one line each for methods, features, values and quirks.
    public List<String> describe() {
        List<String> lines = new ArrayList<String>();
        lines.add("model " + model + ", profile " + profile + (valuesRead ? "" : ", values NOT read (camera not opened)"));

        List<String> absent = new ArrayList<String>();
        for (SonyMethod m : SonyMethod.values())
            if (!present.contains(m))
                absent.add(m.key());
        lines.add("methods " + present.size() + "/" + SonyMethod.values().length + " present"
                + (absent.isEmpty() ? "" : "; absent: " + join(absent)));

        List<String> on = new ArrayList<String>();
        List<String> off = new ArrayList<String>();
        for (Feature f : Feature.values()) {
            if (supports(f))
                on.add(f.name());
            else
                off.add(f.name() + (disabled.contains(f) ? " (profile)" : ""));
        }
        lines.add("features on: " + join(on) + "; off: " + join(off));

        List<String> vals = new ArrayList<String>();
        for (Query q : Query.values()) {
            List<String> v = values.get(q);
            vals.add(q.name() + "=" + (v == null ? "?" : v.toString()));
        }
        lines.add("values: " + join(vals));
        if (!quirks.isEmpty())
            lines.add("quirks: " + quirks);
        return lines;
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty())
            return "-";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }
}
