package io.github.nathanrcast.timelapse.core;

/**
 * Duration, clip-length and remaining-time math for one run.
 *
 * <p>A shot is one interval. With bracketing every interval takes {@link #BRACKET_FRAMES} frames
 * back to back, so frame counts are {@code shots x framesPerInterval}. In burst mode (interval 0)
 * the shot count is the burst length in seconds. {@link Settings#SHOTS_INFINITE} means no end.
 */
public final class ShotPlan {
    public static final int BRACKET_FRAMES = 3;

    private final double intervalSeconds;
    private final int shotCount;
    private final int delayMinutes;
    private final int framesPerInterval;

    public ShotPlan(double intervalSeconds, int shotCount, int delayMinutes, int framesPerInterval) {
        if (framesPerInterval < 1)
            throw new IllegalArgumentException("framesPerInterval < 1: " + framesPerInterval);
        this.intervalSeconds = intervalSeconds;
        this.shotCount = shotCount;
        this.delayMinutes = delayMinutes;
        this.framesPerInterval = framesPerInterval;
    }

    public static ShotPlan of(Settings settings) {
        return new ShotPlan(settings.interval, settings.shotCount, settings.delay,
                settings.brs ? BRACKET_FRAMES : 1);
    }

    public boolean isBurst() {
        return intervalSeconds == 0;
    }

    public boolean isInfinite() {
        return shotCount == Settings.SHOTS_INFINITE;
    }

    public int framesPerInterval() {
        return framesPerInterval;
    }

    public long totalFrames() {
        return (long) shotCount * framesPerInterval;
    }

    public long delayMs() {
        return delayMinutes * 60000L;
    }

    /** Shoot duration as the settings screen shows it: interval x shots, or the burst length. -1 if infinite. */
    public long durationSeconds() {
        if (isInfinite())
            return -1;
        if (isBurst())
            return shotCount;
        return Math.round(intervalSeconds * shotCount);
    }

    /** Clip length at {@code fps}, one frame per shot. -1 for burst or infinite runs. */
    public long clipSeconds(int fps) {
        if (fps <= 0)
            throw new IllegalArgumentException("fps <= 0: " + fps);
        if (isInfinite() || isBurst())
            return -1;
        return shotCount / fps;
    }

    /** End of a run whose first shot is due at {@code startMs}: start + duration. Long.MAX_VALUE if infinite. */
    public long endMs(long startMs) {
        if (isInfinite())
            return Long.MAX_VALUE;
        if (isBurst())
            return startMs + shotCount * 1000L;
        return startMs + Math.round(intervalSeconds * shotCount * 1000);
    }

    /** Shots (intervals) still to take after {@code framesTaken} frames. -1 in burst mode. */
    public long shotsRemaining(int framesTaken) {
        if (isBurst())
            return -1;
        return shotCount - framesTaken / framesPerInterval;
    }

    /**
     * Whole seconds left of the start delay, rounded up. 0 once a frame is taken, or while the run
     * isn't armed yet ({@code startMs <= 0}).
     */
    public long delaySecondsRemaining(long startMs, long nowMs, int framesTaken) {
        if (framesTaken != 0 || startMs <= 0)
            return 0;
        long ms = startMs - nowMs;
        return ms > 0 ? (ms + 999) / 1000 : 0;
    }

    /** Seconds left in the run, including any start delay still to go. Negative once a burst has overrun. */
    public long secondsRemaining(long startMs, long nowMs, int framesTaken) {
        if (isBurst())
            return Math.round((shotCount * 1000L - nowMs + startMs) / 1000.0);
        return Math.round(shotsRemaining(framesTaken) * intervalSeconds)
                + delaySecondsRemaining(startMs, nowMs, framesTaken);
    }
}
