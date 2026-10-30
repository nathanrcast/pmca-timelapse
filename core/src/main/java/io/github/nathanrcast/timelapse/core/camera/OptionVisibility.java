package io.github.nathanrcast.timelapse.core.camera;

// Which settings-screen options this body gets. An option that isn't visible is also forced off: SettingsActivity
// unchecks and hides it, and ShootActivity masks the setting before the run.
public final class OptionVisibility {
    // How MF is engaged: CameraEx.setToggleFocusMode, else Camera.Parameters.setFocusMode("manual"), else not at all.
    public enum FocusControl { TOGGLE, FOCUS_MODE, NONE }

    private final boolean silentShutter;
    private final boolean aeLock;
    private final boolean bracket3;
    private final boolean holyGrail;
    private final FocusControl focusControl;

    private OptionVisibility(boolean silentShutter, boolean aeLock, boolean bracket3, boolean holyGrail,
                             FocusControl focusControl) {
        this.silentShutter = silentShutter;
        this.aeLock = aeLock;
        this.bracket3 = bracket3;
        this.holyGrail = holyGrail;
        this.focusControl = focusControl;
    }

    public static OptionVisibility of(Capabilities caps) {
        FocusControl focus;
        if (caps.supports(Feature.TOGGLE_FOCUS))
            focus = FocusControl.TOGGLE;
        else if (caps.supports(Feature.FOCUS_MODE_MANUAL))
            focus = FocusControl.FOCUS_MODE;
        else
            focus = FocusControl.NONE;
        return new OptionVisibility(caps.supports(Feature.SILENT_SHUTTER), caps.supports(Feature.AE_LOCK),
                caps.supports(Feature.BRACKET_3), caps.supports(Feature.HOLY_GRAIL), focus);
    }

    public boolean silentShutter() {
        return silentShutter;
    }

    public boolean aeLock() {
        return aeLock;
    }

    public boolean bracket3() {
        return bracket3;
    }

    public boolean holyGrail() {
        return holyGrail;
    }

    public boolean manualFocus() {
        return focusControl != FocusControl.NONE;
    }

    public boolean toggleFocus() {
        return focusControl == FocusControl.TOGGLE;
    }

    public FocusControl focusControl() {
        return focusControl;
    }
}
