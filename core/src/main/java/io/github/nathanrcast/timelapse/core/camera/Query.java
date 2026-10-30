package io.github.nathanrcast.timelapse.core.camera;

// A supported-value read the probe makes once per session. Each value is kept as a list of strings: a list query's
// elements, or "true"/"false" for an isSupported* query. Capabilities.values() is null when the query is absent, threw
// or wasn't run (camera not opened).
public enum Query {
    SILENT_SHUTTER_SUPPORTED(SonyMethod.IS_SUPPORTED_SILENT_SHUTTER_MODE),
    TOGGLE_FOCUS_SUPPORTED(SonyMethod.IS_SUPPORTED_TOGGLE_FOCUS_MODE),
    LONG_EXPOSURE_NR_SUPPORTED(SonyMethod.IS_SUPPORTED_LONG_EXPOSURE_NR),
    // The current state: "true" when long-exposure NR is on.
    LONG_EXPOSURE_NR(SonyMethod.GET_LONG_EXPOSURE_NR),
    DRIVE_MODES(SonyMethod.GET_SUPPORTED_DRIVE_MODES),
    BURST_DRIVE_SPEEDS(SonyMethod.GET_SUPPORTED_BURST_DRIVE_SPEEDS),
    AE_LOCKS(SonyMethod.GET_SUPPORTED_AUTO_EXPOSURE_LOCKS),
    BRACKET_MODES(SonyMethod.GET_SUPPORTED_BRACKET_MODES),
    EXPOSURE_BRACKET_MODES(SonyMethod.GET_SUPPORTED_EXPOSURE_BRACKET_MODES),
    BRACKET_PICTURE_COUNTS(SonyMethod.GET_SUPPORTED_NUMS_OF_BRACKET_PICTURE),
    ISO_SENSITIVITIES(SonyMethod.GET_SUPPORTED_ISO_SENSITIVITIES),
    // Android's Camera.Parameters.getSupportedFocusModes(), not a Sony method.
    FOCUS_MODES(null);

    private final SonyMethod method;

    Query(SonyMethod method) {
        this.method = method;
    }

    // The no-arg ParametersModifier getter behind this query, or null for FOCUS_MODES.
    public SonyMethod method() {
        return method;
    }
}
