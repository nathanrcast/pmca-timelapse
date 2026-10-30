package io.github.nathanrcast.timelapse.camera.model;

// Body behaviour reflection can't see, set by camera/ModelProfile.
public enum Quirk {
    // The control wheel/dial isn't reported reliably; input must work with the arrow keys alone (T10).
    DIAL_UNRELIABLE,
}
