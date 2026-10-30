package io.github.nathanrcast.timelapse.core;

/**
 * Text for the shoot screen's HUD, from scheduler state plus the sampled battery and card figures.
 * Pure formatting: the Android side reads battery and card space off the main looper and passes the numbers in.
 */
public final class HudModel {
    /** Unknown battery, free space or file size. */
    public static final long UNKNOWN = -1;

    /** Done out of target, e.g. "12/40" (a burst shows seconds). */
    public final String count;
    /** Skipped slots, e.g. "0". */
    public final String skips;
    /** Time to the next shot: "12s", "now" or "\u2014" when nothing is armed (burst, finished). */
    public final String next;
    /** Time since the first slot's start, "0s" before it. */
    public final String elapsed;
    /** Time left, or "inf". */
    public final String remaining;
    /** "87%", "c 87%" while charging, "?" when unknown. */
    public final String battery;
    /** Estimated shots that fit on the card, "~1234", or "?" until a file size and free space are known. */
    public final String cardLeft;

    private HudModel(String count, String skips, String next, String elapsed, String remaining, String battery,
                     String cardLeft) {
        this.count = count;
        this.skips = skips;
        this.next = next;
        this.elapsed = elapsed;
        this.remaining = remaining;
        this.battery = battery;
        this.cardLeft = cardLeft;
    }

    public static HudModel of(Scheduler s, long nowMs, int batteryPct, boolean charging, long freeBytes,
                              long avgShotBytes) {
        long delay = Progress.delaySecondsRemaining(s, nowMs);
        return new HudModel(
                delay > 0 ? "Delay: " + Util.formatTime(delay) : Progress.count(s, nowMs),
                Long.toString(s.skipped()),
                next(s, nowMs),
                elapsed(s, nowMs),
                Progress.remaining(s, nowMs),
                battery(batteryPct, charging),
                cardLeft(freeBytes, avgShotBytes, s.plan().framesPerInterval()));
    }

    /** Time to the next slot, "now" when overdue, "\u2014" when none is left to wait for (burst, all claimed, done). */
    public static String next(Scheduler s, long nowMs) {
        long due = s.nextSlotDueMs();
        if (due == Scheduler.NEVER)
            return "\u2014";
        long ms = due - nowMs;
        return ms <= 0 ? "now" : Util.formatTime((ms + 999) / 1000);
    }

    /** Wall time since the first slot was due, pauses included; "0s" before it. */
    public static String elapsed(Scheduler s, long nowMs) {
        if (!s.isStarted())
            return "0s";
        long sinceFirstSlot = nowMs - s.startedAtMs() - s.plan().delayMs();
        return Util.formatTime(Math.max(0, sinceFirstSlot / 1000));
    }

    public static String battery(int pct, boolean charging) {
        if (pct < 0)
            return "?";
        return (charging ? "c " : "") + pct + "%";
    }

    /**
     * Shots (slots) that fit in {@code freeBytes} when each frame averages {@code avgFrameBytes}; a bracketed
     * slot takes {@code framesPerSlot} frames. "?" when either figure is unknown.
     */
    public static String cardLeft(long freeBytes, long avgFrameBytes, int framesPerSlot) {
        if (freeBytes < 0 || avgFrameBytes <= 0)
            return "?";
        long perSlot = avgFrameBytes * Math.max(1, framesPerSlot);
        return "~" + freeBytes / perSlot;
    }

    /** One-line wrap-up: "12/40 shots, 2 skipped". */
    public static String summary(Scheduler s, long nowMs) {
        return Progress.count(s, nowMs) + (s.plan().isBurst() ? " s" : " shots") + ", " + s.skipped() + " skipped";
    }
}
