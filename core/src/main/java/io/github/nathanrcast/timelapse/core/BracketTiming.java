package io.github.nathanrcast.timelapse.core;

/**
 * Warns when the interval is shorter than a bracket takes. A bracket is estimated at
 * {@code frames x (shutter + WRITE_MS_PER_FRAME)} until a slot has run; after that the last slot's measured time (its
 * first FIRE to its last capture) is used. The run isn't blocked: the scheduler skips a slot that is still busy.
 */
public final class BracketTiming {
    public static final long WRITE_MS_PER_FRAME = 600;

    private final long intervalMs;
    private final int frames;
    private long shutterMs;
    private long slotStartMs = -1;
    private long measuredMs = -1;

    public BracketTiming(ShotPlan plan) {
        this.intervalMs = plan.intervalMs();
        this.frames = plan.isBurst() ? 1 : plan.framesPerInterval();
    }

    /** The camera's current shutter time; 0 or less when unknown. */
    public void setShutterMs(long ms) {
        shutterMs = Math.max(0, ms);
    }

    /** Feed every scheduler decision. Returns true when the measured slot time was updated. */
    public boolean onDecision(Scheduler.Decision d) {
        if (frames < 2)
            return false;
        if (d.action == Scheduler.Action.FIRE && d.frame == 0) {
            slotStartMs = d.nowMs;
        } else if (d.action == Scheduler.Action.SLOT_DONE && slotStartMs >= 0) {
            measuredMs = d.nowMs - slotStartMs;
            slotStartMs = -1;
            return true;
        }
        return false;
    }

    public boolean isMeasured() {
        return measuredMs >= 0;
    }

    public long estimateMs() {
        return isMeasured() ? measuredMs : frames * (shutterMs + WRITE_MS_PER_FRAME);
    }

    /** The warning text, or null when the interval is long enough (or the run isn't bracketed). */
    public String warning() {
        if (frames < 2 || intervalMs <= 0 || estimateMs() <= intervalMs)
            return null;
        return String.format("Interval %.1f s < bracket %s%.1f s", intervalMs / 1000.0, isMeasured() ? "" : "~",
                estimateMs() / 1000.0);
    }
}
