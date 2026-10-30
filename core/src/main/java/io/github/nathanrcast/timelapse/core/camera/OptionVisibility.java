package io.github.nathanrcast.timelapse.core.camera;

// Which options this body gets. SettingsActivity hides the rest without touching their saved values;
// RunOptions.mask forces them off for the run.
public final class OptionVisibility {
    // How MF is engaged: CameraEx.setToggleFocusMode, else Camera.Parameters.setFocusMode("manual"), else not at all.
    public enum FocusControl { TOGGLE, FOCUS_MODE, NONE }

    private final boolean silentShutter;
    private final boolean aeLock;
    private final boolean bracket3;
    private final boolean holyGrail;
    private final boolean burst;
    private final FocusControl focusControl;
    private final boolean lenrOff;

    private OptionVisibility(boolean silentShutter, boolean aeLock, boolean bracket3, boolean holyGrail,
                             boolean burst, FocusControl focusControl, boolean lenrOff) {
        this.lenrOff = lenrOff;
        this.silentShutter = silentShutter;
        this.aeLock = aeLock;
        this.bracket3 = bracket3;
        this.holyGrail = holyGrail;
        this.burst = burst;
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
                caps.supports(Feature.BRACKET_3), caps.supports(Feature.HOLY_GRAIL), caps.supports(Feature.BURST),
                focus, caps.supports(Feature.SET_LENR));
    }

    // The body can turn long-exposure NR off and back on, so "Turn LENR off during runs" is offered.
    public boolean lenrOff() {
        return lenrOff;
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

    // Interval 0. Without it the slider's burst position is labelled unavailable and runs at the shortest interval.
    public boolean burst() {
        return burst;
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
