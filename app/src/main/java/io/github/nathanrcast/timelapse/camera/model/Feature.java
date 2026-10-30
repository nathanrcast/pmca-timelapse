package io.github.nathanrcast.timelapse.camera.model;

import static io.github.nathanrcast.timelapse.camera.model.SonyMethod.*;

import java.util.List;

// An optional behaviour the app can use. allowedBy() is the probe's half of the decision: every method the feature
// calls is present, and where the firmware reports supported values, the value the app sets is among them.
// Capabilities.supports() adds the model profile's overrides.
public enum Feature {
    // isSupportedSilentShutterMode() must answer true: calling the setter on a body without it is upstream issue 4.
    SILENT_SHUTTER(SET_SILENT_SHUTTER_MODE, IS_SUPPORTED_SILENT_SHUTTER_MODE),
    AE_LOCK(SET_AUTO_EXPOSURE_LOCK),
    BRACKET_3(SET_DRIVE_MODE, SET_BRACKET_MODE, SET_EXPOSURE_BRACKET_MODE, SET_EXPOSURE_BRACKET_PERIOD,
            SET_NUM_OF_BRACKET_PICTURE),
    BURST(SET_DRIVE_MODE, GET_SUPPORTED_BURST_DRIVE_SPEEDS, SET_BURST_DRIVE_SPEED, SET_BURST_DRIVE_BUTTON_RELEASE_BEHAVE),
    // Every call the holy-grail ramp makes; ShootActivity uses createEmptyParameters to set ISO alone.
    HOLY_GRAIL(SET_PROGRAM_LINE_RANGE_OVER_LISTENER, GET_SHUTTER_SPEED, GET_ISO_SENSITIVITY, SET_ISO_SENSITIVITY,
            INCREMENT_SHUTTER_SPEED, DECREMENT_SHUTTER_SPEED, CREATE_EMPTY_PARAMETERS),
    TOGGLE_FOCUS(SET_TOGGLE_FOCUS_MODE, IS_SUPPORTED_TOGGLE_FOCUS_MODE),
    // Camera.Parameters.setFocusMode("manual"): Android API, so no Sony method; gated on the focus-mode list only.
    FOCUS_MODE_MANUAL;

    // ParametersModifier constant values the app sets (DRIVE_MODE_BRACKET, AE_LOCK_ON, FOCUS_MODE_MANUAL, ...).
    public static final String DRIVE_MODE_BURST = "burst";
    public static final String DRIVE_MODE_BRACKET = "bracket";
    public static final String AE_LOCK_ON = "locked";
    public static final String BRACKET_MODE_EXPOSURE = "exposure";
    public static final String EXPOSURE_BRACKET_MODE_SINGLE = "single";
    public static final String BRACKET_PICTURES = "3";
    public static final String FOCUS_MODE_MANUAL_VALUE = "manual";

    private final SonyMethod[] required;

    Feature(SonyMethod... required) {
        this.required = required;
    }

    public SonyMethod[] requiredMethods() {
        return required.clone();
    }

    public boolean allowedBy(Capabilities caps) {
        for (SonyMethod m : required)
            if (!caps.has(m))
                return false;
        switch (this) {
            case SILENT_SHUTTER:
                return isTrue(caps, Query.SILENT_SHUTTER_SUPPORTED);
            case TOGGLE_FOCUS:
                return isTrue(caps, Query.TOGGLE_FOCUS_SUPPORTED);
            case AE_LOCK:
                return allows(caps, Query.AE_LOCKS, AE_LOCK_ON);
            case BRACKET_3:
                return allows(caps, Query.DRIVE_MODES, DRIVE_MODE_BRACKET)
                        && allows(caps, Query.BRACKET_MODES, BRACKET_MODE_EXPOSURE)
                        && allows(caps, Query.EXPOSURE_BRACKET_MODES, EXPOSURE_BRACKET_MODE_SINGLE)
                        && allows(caps, Query.BRACKET_PICTURE_COUNTS, BRACKET_PICTURES);
            case BURST:
                List<String> speeds = caps.values(Query.BURST_DRIVE_SPEEDS);
                return allows(caps, Query.DRIVE_MODES, DRIVE_MODE_BURST) && (speeds == null || !speeds.isEmpty());
            case FOCUS_MODE_MANUAL:
                return allows(caps, Query.FOCUS_MODES, FOCUS_MODE_MANUAL_VALUE);
            default:
                return true;
        }
    }

    // An isSupported* answer must be known and true.
    static boolean isTrue(Capabilities caps, Query q) {
        List<String> v = caps.values(q);
        return v != null && v.contains("true");
    }

    // A supported-value list, when the firmware reports one, must include the value; an unknown list doesn't veto.
    public static boolean allows(Capabilities caps, Query q, String value) {
        List<String> v = caps.values(q);
        return v == null || v.contains(value);
    }
}
