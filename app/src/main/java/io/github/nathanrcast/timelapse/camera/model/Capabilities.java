package io.github.nathanrcast.timelapse.camera.model;

import java.util.List;

// What this body's Sony API offers, built once per session by the probe (camera/ReflectiveCapabilities) and merged
// with the body's camera/ModelProfile. Pure Java, so the UI decisions can be tested on a fake.
public interface Capabilities {
    String model();

    // The method resolved on this firmware. Nothing may call an optional SonyMethod unless this is true.
    boolean has(SonyMethod method);

    // The query's result as strings, or null when unknown (method absent, the call threw, or the camera wasn't open).
    List<String> values(Query query);

    // The probe allows the feature (Feature.allowedBy) and the model profile doesn't switch it off.
    boolean supports(Feature feature);

    boolean hasQuirk(Quirk quirk);
}
