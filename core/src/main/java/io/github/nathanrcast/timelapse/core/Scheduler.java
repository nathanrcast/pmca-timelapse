package io.github.nathanrcast.timelapse.core;

/**
 * Drift-free capture scheduler. Slot {@code n} is due at {@code t0 + n * interval}, where
 * {@code t0} is the start plus the plan's delay; deadlines never depend on when earlier shots
 * actually fired, so latency before a shot (holy-grail work, a late timer) never accumulates.
 *
 * <p>The caller arms one timer for {@link #nextWakeMs()} and calls {@link #onTimer()} when it
 * fires, and calls {@link #onFrameCaptured()} when the camera reports a frame done (onShutter).
 * Each slot resolves exactly once, as a fire or a skip:
 * <ul>
 * <li>A capture still running at a deadline makes that slot a {@link SkipReason#BUSY} skip; the
 *     next chance is the following deadline. There is never more than one capture in flight and
 *     never a catch-up shot.</li>
 * <li>A timer that wakes after a slot's window has ended makes it a {@link SkipReason#LATE} skip,
 *     one slot per call, until the current slot.</li>
 * <li>With brackets, a slot fires {@code framesPerInterval} frames back to back and completes
 *     after the last one is captured.</li>
 * <li>Burst mode (interval 0) is one slot: it fires at {@code t0} and completes at the end of the
 *     burst, found by the timer or by a poll through {@link #onFrameCaptured()}.</li>
 * </ul>
 * The run ends when {@code shotCount} slots are captured ({@link StopOn#CAPTURED}) or have
 * elapsed ({@link StopOn#SLOTS}), once no capture is in flight.
 */
public final class Scheduler {
    public static final long NEVER = Long.MAX_VALUE;

    public enum StopOn { CAPTURED, SLOTS }

    public enum SkipReason {
        /** An earlier slot's capture was still running at this slot's deadline. */
        BUSY,
        /** The timer woke after this slot's window had ended. */
        LATE,
        /** The run was paused while this slot was capturing. */
        INTERRUPTED
    }

    public enum Action {
        /** Nothing to do now. */
        WAIT,
        /** Take a frame now: the first of a slot, or the next bracket frame. */
        FIRE,
        /** The slot is skipped; see {@link Decision#skipReason}. */
        SKIP,
        /** The slot's last frame is captured. */
        SLOT_DONE
    }

    /** What to do, and for which slot; one per {@link #onTimer()} or {@link #onFrameCaptured()} call. */
    public static final class Decision {
        public final Action action;
        /** The slot, or -1 for {@link Action#WAIT}. */
        public final long slot;
        /** For FIRE and SLOT_DONE, the bracket frame (0-based); otherwise -1. */
        public final int frame;
        /** The slot's deadline. */
        public final long scheduledMs;
        public final long nowMs;
        /** Only for {@link Action#SKIP}. */
        public final SkipReason skipReason;

        Decision(Action action, long slot, int frame, long scheduledMs, long nowMs, SkipReason skipReason) {
            this.action = action;
            this.slot = slot;
            this.frame = frame;
            this.scheduledMs = scheduledMs;
            this.nowMs = nowMs;
            this.skipReason = skipReason;
        }

        /** How long after the slot's deadline this decision was made. */
        public long lateMs() {
            return nowMs - scheduledMs;
        }

        @Override
        public String toString() {
            if (action == Action.WAIT)
                return "WAIT";
            StringBuilder sb = new StringBuilder().append(action).append(" slot ").append(slot);
            if (action == Action.FIRE)
                sb.append(" frame ").append(frame);
            if (action == Action.SKIP)
                sb.append(' ').append(skipReason);
            return sb.append(" +").append(lateMs()).append(" ms").toString();
        }
    }

    private final ShotPlan plan;
    private final Clock clock;
    private final StopOn stopOn;
    private final long intervalMs;
    private final long slotTarget;

    private boolean started;
    private boolean paused;
    private boolean done;
    private long t0;
    private long pausedAtMs;

    private long nextSlot;
    private boolean capturing;
    private long captureSlot;
    private int captureFrame;

    private long captured;
    private long framesFired;
    private final long[] skips = new long[SkipReason.values().length];

    public Scheduler(ShotPlan plan, Clock clock, StopOn stopOn) {
        this.plan = plan;
        this.clock = clock;
        this.stopOn = stopOn;
        this.intervalMs = plan.intervalMs();
        this.slotTarget = plan.isBurst() ? 1 : plan.shotCount();
    }

    /** Starts the run now; the first slot is due after the plan's delay. */
    public void start() {
        if (started)
            throw new IllegalStateException("already started");
        started = true;
        t0 = clock.nowMs() + plan.delayMs();
    }

    /** When the caller's timer should next call {@link #onTimer()}; {@link #NEVER} if no timer is needed. */
    public long nextWakeMs() {
        if (!started || paused || done)
            return NEVER;
        if (plan.isBurst())
            return capturing ? burstEndMs() : t0;
        if (allSlotsClaimed())
            return NEVER;
        return deadlineMs(nextSlot);
    }

    public Decision onTimer() {
        long now = clock.nowMs();
        if (!started || paused || done)
            return waitAt(now);
        if (plan.isBurst())
            return burstTimer(now);
        if (allSlotsClaimed())
            return waitAt(now);
        long deadline = deadlineMs(nextSlot);
        if (now < deadline)
            return waitAt(now);

        long slot = nextSlot++;
        Decision decision;
        if (capturing)
            decision = skip(slot, deadline, now, SkipReason.BUSY);
        else if (now >= deadline + intervalMs)
            decision = skip(slot, deadline, now, SkipReason.LATE);
        else {
            capturing = true;
            captureSlot = slot;
            captureFrame = 0;
            framesFired++;
            decision = new Decision(Action.FIRE, slot, 0, deadline, now, null);
        }
        updateDone();
        return decision;
    }

    public Decision onFrameCaptured() {
        long now = clock.nowMs();
        if (!capturing)
            return waitAt(now);
        if (plan.isBurst()) {
            if (now < burstEndMs())
                return waitAt(now);
            return completeSlot(now);
        }
        if (captureFrame + 1 < plan.framesPerInterval()) {
            captureFrame++;
            framesFired++;
            return new Decision(Action.FIRE, captureSlot, captureFrame, deadlineMs(captureSlot), now, null);
        }
        return completeSlot(now);
    }

    /**
     * Stops the clock on the run. A capture in flight can't be trusted after the camera is
     * released, so its slot is counted as an {@link SkipReason#INTERRUPTED} skip.
     */
    public void pause() {
        if (!started || paused || done)
            return;
        paused = true;
        pausedAtMs = clock.nowMs();
        if (capturing) {
            capturing = false;
            skips[SkipReason.INTERRUPTED.ordinal()]++;
        }
    }

    /** Shifts {@code t0} by the paused time, so every remaining deadline keeps its place in the run. */
    public void resume() {
        if (!paused)
            return;
        paused = false;
        t0 += clock.nowMs() - pausedAtMs;
    }

    /**
     * When to stop showing the review of the last capture: the next deadline or
     * {@code maxReviewMs} from now, whichever is first. -1 if the next deadline has already passed.
     */
    public long reviewUntilMs(long maxReviewMs) {
        long now = clock.nowMs();
        long next = nextWakeMs();
        if (next <= now)
            return -1;
        return Math.min(next, now + maxReviewMs);
    }

    public long deadlineMs(long slot) {
        return t0 + slot * intervalMs;
    }

    public long t0Ms() {
        return t0;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /** Slots resolved so far (fired or skipped). */
    public long slotsElapsed() {
        return nextSlot;
    }

    public long captured() {
        return captured;
    }

    public long framesFired() {
        return framesFired;
    }

    public long skipped() {
        long total = 0;
        for (long n : skips)
            total += n;
        return total;
    }

    public long skipped(SkipReason reason) {
        return skips[reason.ordinal()];
    }

    private Decision burstTimer(long now) {
        if (!capturing) {
            if (now < t0)
                return waitAt(now);
            capturing = true;
            captureSlot = 0;
            captureFrame = 0;
            nextSlot = 1;
            framesFired++;
            return new Decision(Action.FIRE, 0, 0, t0, now, null);
        }
        if (now < burstEndMs())
            return waitAt(now);
        return completeSlot(now);
    }

    private long burstEndMs() {
        return plan.endMs(t0);
    }

    private Decision completeSlot(long now) {
        capturing = false;
        captured++;
        updateDone();
        return new Decision(Action.SLOT_DONE, captureSlot, captureFrame, deadlineMs(captureSlot), now, null);
    }

    private Decision skip(long slot, long deadline, long now, SkipReason reason) {
        skips[reason.ordinal()]++;
        return new Decision(Action.SKIP, slot, -1, deadline, now, reason);
    }

    private Decision waitAt(long now) {
        return new Decision(Action.WAIT, -1, -1, now, now, null);
    }

    // No slot left to resolve: the target is reached, or will be once the capture in flight is done.
    private boolean allSlotsClaimed() {
        if (stopOn == StopOn.SLOTS)
            return nextSlot >= slotTarget;
        return captured + (capturing ? 1 : 0) >= slotTarget;
    }

    private void updateDone() {
        if (capturing)
            return;
        if (stopOn == StopOn.CAPTURED ? captured >= slotTarget : nextSlot >= slotTarget)
            done = true;
    }
}
