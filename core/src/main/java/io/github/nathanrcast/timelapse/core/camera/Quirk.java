package io.github.nathanrcast.timelapse.core.camera;

// Body behaviour reflection can't see, set by the app's camera.ModelProfile.
public enum Quirk {
    // The control wheel/dial isn't reported reliably; input must work with the arrow keys alone (T10).
    DIAL_UNRELIABLE,
}
