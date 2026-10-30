package io.github.nathanrcast.timelapse.core.camera;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.SliderMapping;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// What one run asks of the camera, after masking the options this body lacks. The single capability gate:
// SafeCamera.configure applies a RunOptions as given.
public final class RunOptions {
    // Slider position 1, the shortest interval that isn't burst.
    static final int SHORTEST_INTERVAL_PROGRESS = 1;

    public final boolean burst;
    // null: the body has no silent shutter, so the run leaves it alone. Otherwise it is set either way.
    public final Boolean silentShutter;
    public final boolean aeLock;
    public final boolean bracket3;
    public final BracketPlan bracket;
    public final boolean holyGrail;
    // NONE when MF is off for the run.
    public final FocusControl focus;
    private final List<String> masked;

    private RunOptions(boolean burst, Boolean silentShutter, boolean aeLock, BracketPlan bracket, boolean holyGrail,
                       FocusControl focus, List<String> masked) {
        this.burst = burst;
        this.silentShutter = silentShutter;
        this.aeLock = aeLock;
        this.bracket = bracket;
        this.bracket3 = bracket.active;
        this.holyGrail = holyGrail;
        this.focus = focus;
        this.masked = Collections.unmodifiableList(masked);
    }

    // Masks the run's copy of the settings (the one from the intent; the saved settings are never passed here) and
    // returns what the run asks for. Options the body lacks are turned off; burst becomes the shortest interval.
    public static RunOptions mask(Capabilities caps, Settings run) {
        OptionVisibility options = OptionVisibility.of(caps);
        List<String> masked = new ArrayList<String>();
        if (run.silentShutter && !options.silentShutter()) {
            run.silentShutter = false;
            masked.add(Feature.SILENT_SHUTTER.name());
        }
        if (run.ael && !options.aeLock()) {
            run.ael = false;
            masked.add(Feature.AE_LOCK.name());
        }
        if (run.brs && !options.bracket3()) {
            run.brs = false;
            masked.add(Feature.BRACKET_3.name());
        }
        if (run.mf && !options.manualFocus()) {
            run.mf = false;
            masked.add("MANUAL_FOCUS");
        }
        if (run.holyGrail && !options.holyGrail()) {
            run.holyGrail = false;
            masked.add(Feature.HOLY_GRAIL.name());
        }
        if (run.interval == 0 && !options.burst()) {
            run.rawInterval = SHORTEST_INTERVAL_PROGRESS;
            run.interval = SliderMapping.progressToInterval(SHORTEST_INTERVAL_PROGRESS);
            masked.add(Feature.BURST.name() + " (interval " + run.interval + " s)");
        }
        // A burst run is never auto-detected as bracketed; the box alone still asks for it.
        BracketPlan bracket = run.interval == 0 && !run.brs ? BracketPlan.off()
                : BracketPlan.resolve(caps, options.bracket3(), run.brs);
        if (run.brs != bracket.active) {
            run.brs = bracket.active;
            if (bracket.unsupportedReason != null)
                masked.add(Feature.BRACKET_3.name() + " (" + bracket.unsupportedReason + ")");
        }
        return new RunOptions(run.interval == 0,
                options.silentShutter() ? Boolean.valueOf(run.silentShutter) : null,
                run.ael, bracket, run.holyGrail,
                run.mf ? options.focusControl() : FocusControl.NONE, masked);
    }

    // The options switched off for this run, for the log.
    public List<String> masked() {
        return masked;
    }
}
