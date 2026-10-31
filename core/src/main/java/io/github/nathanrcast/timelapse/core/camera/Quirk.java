package io.github.nathanrcast.timelapse.core.camera;

// Body behaviour reflection can't see, set by the app's camera.ModelProfile.
public enum Quirk {
    // The control wheel/dial isn't reported reliably; input must work with the arrow keys alone (T10). No profile
    // sets it now: the NEX-5T wheel reports (KURU 522/523). Kept for a body that proves otherwise.
    DIAL_UNRELIABLE,
}
