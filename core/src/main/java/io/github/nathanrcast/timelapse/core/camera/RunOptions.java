package io.github.nathanrcast.timelapse.core.camera;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.StepTables;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// What one run asks of the camera, after masking the options this body lacks. The single capability gate:
// SafeCamera.configure applies a RunOptions as given.
public final class RunOptions {
    // The interval a body without burst runs at instead (1 s, the shortest the pre-T10 slider offered).
    static final long FALLBACK_INTERVAL_MS = 1000;

    public final boolean burst;
    // null: the body has no silent shutter, so the run leaves it alone. Otherwise it is set either way.
    public final Boolean silentShutter;
    public final boolean aeLock;
    public final boolean bracket3;
    public final boolean holyGrail;
    // NONE when MF is off for the run.
    public final FocusControl focus;
    private final List<String> masked;

    private RunOptions(boolean burst, Boolean silentShutter, boolean aeLock, boolean bracket3, boolean holyGrail,
                       FocusControl focus, List<String> masked) {
        this.burst = burst;
        this.silentShutter = silentShutter;
        this.aeLock = aeLock;
        this.bracket3 = bracket3;
        this.holyGrail = holyGrail;
        this.focus = focus;
        this.masked = Collections.unmodifiableList(masked);
    }

    // Masks the run's copy of the settings (the one from the intent; the saved settings are never passed here) and
    // returns what the run asks for. Options the body lacks are turned off; burst becomes a 1 s interval.
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
            // Only the interval changes: the run's copy comes from the intent, whose indices are not the run's values.
            run.intervalIndex = StepTables.INTERVAL_MS.nearestIndex(FALLBACK_INTERVAL_MS);
            run.interval = FALLBACK_INTERVAL_MS / 1000.0;
            masked.add(Feature.BURST.name() + " (interval " + run.interval + " s)");
        }
        return new RunOptions(run.interval == 0,
                options.silentShutter() ? Boolean.valueOf(run.silentShutter) : null,
                run.ael, run.brs, run.holyGrail,
                run.mf ? options.focusControl() : FocusControl.NONE, masked);
    }

    // The options switched off for this run, for the log.
    public List<String> masked() {
        return masked;
    }
}
