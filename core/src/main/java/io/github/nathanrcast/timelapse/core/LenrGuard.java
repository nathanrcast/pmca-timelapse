package io.github.nathanrcast.timelapse.core;

// Long-exposure noise reduction shoots a dark frame as long as the exposure after every shot of 1 s or more, so a
// run with an interval under the time its frames and dark frames take silently loses every other slot (upstream
// issues 26/34/50).
public final class LenrGuard {
    public static final long NR_MIN_SHUTTER_MS = 1000;
    // Writing one frame (and its dark frame) to the card, per frame.
    public static final long WRITE_MS_PER_FRAME = 1000;

    public enum Action {
        /** LENR is off or irrelevant (short shutter, or its state or the shutter is unknown). */
        NONE,
        /** Turn LENR off for this run and restore it afterwards. */
        TURN_OFF,
        /** Leave LENR alone: warn and raise the interval to the minimum. */
        WARN
    }

    private LenrGuard() {}

    // The exposure of bracket frame 0..frames-1: the shutter at -step, 0 and +step EV (tenths); a single frame is
    // the shutter itself.
    public static long frameMs(long shutterMs, int frames, int periodTenths, int frame) {
        if (frames < 3 || periodTenths <= 0)
            return shutterMs;
        double ev = (frame - 1) * periodTenths / 10.0;
        return Math.round(shutterMs * Math.pow(2, ev));
    }

    public static long longestFrameMs(long shutterMs, int frames, int periodTenths) {
        return frames < 3 || periodTenths <= 0 ? shutterMs : frameMs(shutterMs, frames, periodTenths, frames - 1);
    }

    // True when the camera applies NR to some frame: LENR is known to be on and the longest exposure is 1 s or more.
    public static boolean applies(Boolean lenrOn, long longestFrameMs) {
        return Boolean.TRUE.equals(lenrOn) && longestFrameMs >= NR_MIN_SHUTTER_MS;
    }

    // The shortest interval that fits every frame, its dark frame and writing it: 2 x the exposures plus 1 s a frame.
    // 0 when NR does not apply.
    public static long minIntervalMs(Boolean lenrOn, long shutterMs, int frames, int periodTenths) {
        if (!applies(lenrOn, longestFrameMs(shutterMs, frames, periodTenths)))
            return 0;
        int n = Math.max(1, frames);
        long sum = 0;
        for (int i = 0; i < n; i++)
            sum += frameMs(shutterMs, n, periodTenths, i);
        return 2 * sum + n * WRITE_MS_PER_FRAME;
    }

    // The interval to run at: the requested one, raised to the minimum. Burst (0) is left alone.
    public static long effectiveIntervalMs(long intervalMs, Boolean lenrOn, long shutterMs, int frames,
                                           int periodTenths) {
        if (intervalMs <= 0)
            return intervalMs;
        return Math.max(intervalMs, minIntervalMs(lenrOn, shutterMs, frames, periodTenths));
    }

    // burst: an interval-0 run, which has no slots to lose.
    public static Action decide(Boolean lenrOn, long longestFrameMs, boolean canSet, boolean wantOff,
                                boolean burst) {
        if (burst || !applies(lenrOn, longestFrameMs))
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
