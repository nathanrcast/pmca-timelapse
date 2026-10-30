package io.github.nathanrcast.timelapse.core;

// Long-exposure noise reduction shoots a dark frame as long as the exposure after every shot of 1 s or more, so a
// run with an interval under 2 x shutter + 1 s silently loses every other slot (upstream issues 26/34/50).
public final class LenrGuard {
    public static final long NR_MIN_SHUTTER_MS = 1000;
    public static final long MARGIN_MS = 1000;

    public enum Action {
        /** LENR is off or irrelevant (short shutter, burst, or its state or the shutter is unknown). */
        NONE,
        /** Turn LENR off for this run and restore it afterwards. */
        TURN_OFF,
        /** Leave LENR alone: warn and raise the interval to the minimum. */
        WARN
    }

    private LenrGuard() {}

    public static long shutterMs(int numerator, int denominator) {
        return denominator <= 0 ? -1 : Math.round(numerator * 1000.0 / denominator);
    }

    // True when the camera applies NR to this shot: LENR is known to be on and the shutter is 1 s or longer.
    public static boolean applies(Boolean lenrOn, long shutterMs) {
        return Boolean.TRUE.equals(lenrOn) && shutterMs >= NR_MIN_SHUTTER_MS;
    }

    // The shortest interval that fits exposure + dark frame + margin, or 0 when NR does not apply.
    public static long minIntervalMs(Boolean lenrOn, long shutterMs) {
        return applies(lenrOn, shutterMs) ? 2 * shutterMs + MARGIN_MS : 0;
    }

    // The interval to run at: the requested one, raised to the minimum. Burst (0) is left alone.
    public static long effectiveIntervalMs(long intervalMs, Boolean lenrOn, long shutterMs) {
        if (intervalMs <= 0)
            return intervalMs;
        return Math.max(intervalMs, minIntervalMs(lenrOn, shutterMs));
    }

    public static Action decide(Boolean lenrOn, long shutterMs, boolean canSet, boolean wantOff) {
        if (!applies(lenrOn, shutterMs))
            return Action.NONE;
        return canSet && wantOff ? Action.TURN_OFF : Action.WARN;
    }

    // After a run that never reached close(): put LENR back only if the app's flag is still set and LENR is not on.
    public static boolean shouldRestore(boolean flagSet, Boolean currentOn) {
        return flagSet && !Boolean.TRUE.equals(currentOn);
    }

    public static String headerValue(Boolean lenrOn) {
        return lenrOn == null ? "unknown" : lenrOn.booleanValue() ? "on" : "off";
    }
}
