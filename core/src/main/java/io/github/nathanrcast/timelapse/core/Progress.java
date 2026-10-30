package io.github.nathanrcast.timelapse.core;

/**
 * Shoot-screen progress, from the scheduler's state rather than frames fired: skips stretch the
 * remaining time, and the count can't pass the target.
 */
public final class Progress {
    private Progress() {}

    /**
     * Captured shots out of the target, e.g. "12/40" or "12/inf". A burst shows elapsed out of
     * total seconds instead.
     */
    public static String count(Scheduler s, long nowMs) {
        ShotPlan plan = s.plan();
        String target = plan.isInfinite() ? "inf" : Integer.toString(plan.shotCount());
        long done = plan.isBurst() ? burstSecondsElapsed(s, nowMs) : s.captured();
        return done + "/" + target;
    }

    /**
     * Seconds until the run ends if no more slots are skipped: the end of the window of the last
     * slot still to fire. Never negative; 0 once done, Long.MAX_VALUE for an infinite run.
     */
    public static long secondsRemaining(Scheduler s, long nowMs) {
        ShotPlan plan = s.plan();
        if (s.isDone())
            return 0;
        if (plan.isInfinite())
            return Long.MAX_VALUE;
        if (!s.isStarted())
            return ceilSeconds(plan.delayMs()) + plan.durationSeconds();
        long endMs = plan.isBurst()
                ? plan.endMs(s.t0Ms())
                : s.deadlineMs(s.slotsElapsed() + slotsStillToFire(s));
        return ceilSeconds(endMs - nowMs);
    }

    /** {@link #secondsRemaining} as h/m/s text, or "inf". */
    public static String remaining(Scheduler s, long nowMs) {
        long seconds = secondsRemaining(s, nowMs);
        return seconds == Long.MAX_VALUE ? "inf" : Util.formatTime(seconds);
    }

    /** Whole seconds until the first slot is due, rounded up; 0 once it is. */
    public static long delaySecondsRemaining(Scheduler s, long nowMs) {
        if (!s.isStarted() || s.slotsElapsed() > 0)
            return 0;
        return ceilSeconds(s.t0Ms() - nowMs);
    }

    private static long slotsStillToFire(Scheduler s) {
        long target = s.plan().shotCount();
        long left = s.stopOn() == Scheduler.StopOn.CAPTURED
                ? target - s.captured() - (s.isCapturing() ? 1 : 0)
                : target - s.slotsElapsed();
        return Math.max(0, left);
    }

    private static long burstSecondsElapsed(Scheduler s, long nowMs) {
        if (s.isDone())
            return s.plan().shotCount();
        if (!s.isCapturing())
            return 0;
        long seconds = (nowMs - s.t0Ms()) / 1000;
        return Math.max(0, Math.min(s.plan().shotCount(), seconds));
    }

    private static long ceilSeconds(long ms) {
        return ms > 0 ? (ms + 999) / 1000 : 0;
    }
}
