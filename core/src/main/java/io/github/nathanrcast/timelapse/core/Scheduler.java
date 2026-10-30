package io.github.nathanrcast.timelapse.core;

/**
 * Drift-free capture scheduler. Slot {@code n} is due at {@code t0 + n * interval}, where
 * {@code t0} is the start plus the plan's delay; deadlines never depend on when earlier shots
 * actually fired, so latency before a shot (holy-grail work, a late timer) never accumulates.
 *
 * <p>The caller arms one timer for {@link #nextWakeMs()} and calls {@link #onTimer()} when it
 * fires, and calls {@link #onFrameCaptured()} when the camera reports a frame done (onShutter).
 * All calls must come from one thread. Each slot either is skipped at its deadline or fires and
 * then ends as {@link Action#SLOT_DONE}, a {@link SkipReason#LOST} or an
 * {@link SkipReason#INTERRUPTED} skip:
 * <ul>
 * <li>A capture still running at a deadline makes that slot a {@link SkipReason#BUSY} skip, even if
 *     the timer only gets to it after the capture ends; the next chance is the following deadline.
 *     There is never more than one capture in flight and never a catch-up shot.</li>
 * <li>A timer that wakes after a slot's window has ended makes it a {@link SkipReason#LATE} skip,
 *     one slot per call, until the current slot.</li>
 * <li>A frame with no onShutter within the capture timeout closes its slot as LOST, so a missing
 *     callback can't stall the run.</li>
 * <li>With brackets, a slot fires {@code framesPerInterval} frames back to back and completes
 *     after the last one is captured.</li>
 * <li>Burst mode (interval 0) is one slot: it fires at {@code t0} and the timer completes it at
 *     the end of the burst. onShutter plays no part in a burst.</li>
 * </ul>
 * The run ends when {@code shotCount} slots are captured ({@link StopOn#CAPTURED}) or have
 * elapsed ({@link StopOn#SLOTS}), once no capture is in flight.
 */
public final class Scheduler {
    public static final long NEVER = Long.MAX_VALUE;
    public static final long MIN_CAPTURE_TIMEOUT_MS = 120000;

    public enum StopOn { CAPTURED, SLOTS }

    public enum SkipReason {
        /** An earlier slot's capture was still running at this slot's deadline. */
        BUSY,
        /** The timer woke after this slot's window had ended. */
        LATE,
        /** The run was paused while this slot was capturing. */
        INTERRUPTED,
        /** The slot fired but no onShutter came within the capture timeout. */
        LOST
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
        /** Every WAIT is this one instance; its slot, frame and times are -1/0. */
        static final Decision WAIT = new Decision(Action.WAIT, -1, -1, 0, 0, null);

        public final Action action;
        /** The slot, or -1 for {@link Action#WAIT}. */
        public final long slot;
        /** The bracket frame (0-based) for FIRE, SLOT_DONE and LOST; otherwise -1. */
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
            if (frame >= 0 && action != Action.SLOT_DONE)
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
    private final long captureTimeoutMs;

    private boolean started;
    private boolean paused;
    private boolean done;
    private long t0;
    private long pausedAtMs;

    private long nextSlot;
    private boolean capturing;
    private long captureSlot;
    private int captureFrame;
    private long frameFiredAtMs;
    private long lastCaptureEndMs = Long.MIN_VALUE;

    private long captured;
    private long framesFired;
    private final long[] skips = new long[SkipReason.values().length];

    /** Uses the default capture timeout, {@link #defaultCaptureTimeoutMs(ShotPlan)}. */
    public Scheduler(ShotPlan plan, Clock clock, StopOn stopOn) {
        this(plan, clock, stopOn, defaultCaptureTimeoutMs(plan));
    }

    public Scheduler(ShotPlan plan, Clock clock, StopOn stopOn, long captureTimeoutMs) {
        if (captureTimeoutMs <= 0)
            throw new IllegalArgumentException("captureTimeoutMs <= 0: " + captureTimeoutMs);
        this.plan = plan;
        this.clock = clock;
        this.stopOn = stopOn;
        this.intervalMs = plan.intervalMs();
        this.slotTarget = plan.isBurst() ? 1 : plan.shotCount();
        this.captureTimeoutMs = captureTimeoutMs;
    }

    /** Three intervals, but at least {@link #MIN_CAPTURE_TIMEOUT_MS} for long exposures and noise reduction. */
    public static long defaultCaptureTimeoutMs(ShotPlan plan) {
        return Math.max(3 * plan.intervalMs(), MIN_CAPTURE_TIMEOUT_MS);
    }

    /** Starts the run now; the first slot is due after the plan's delay. */
    public void start() {
        start(clock.nowMs() + plan.delayMs());
    }

    /** Starts the run with the first slot due at {@code t0Ms} (the clock's time base), e.g. a scheduled start. */
    public void start(long t0Ms) {
        if (started)
            throw new IllegalStateException("already started");
        started = true;
        t0 = t0Ms;
    }

    /** When the caller's timer should next call {@link #onTimer()}; {@link #NEVER} if no timer is needed. */
    public long nextWakeMs() {
        if (!started || paused || done)
            return NEVER;
        if (plan.isBurst())
            return capturing ? burstEndMs() : t0;
        long wake = allSlotsClaimed() ? NEVER : deadlineMs(nextSlot);
        if (capturing)
            wake = Math.min(wake, frameFiredAtMs + captureTimeoutMs);
        return wake;
    }

    public Decision onTimer() {
        long now = clock.nowMs();
        if (!started || paused || done)
            return Decision.WAIT;
        if (plan.isBurst())
            return burstTimer(now);
        if (capturing && now >= frameFiredAtMs + captureTimeoutMs)
            return closeCapture(now, SkipReason.LOST);
        if (allSlotsClaimed())
            return Decision.WAIT;
        long deadline = deadlineMs(nextSlot);
        if (now < deadline)
            return Decision.WAIT;

        long slot = nextSlot++;
        Decision decision;
        if (capturing || deadline < lastCaptureEndMs)
            decision = skip(slot, -1, deadline, now, SkipReason.BUSY);
        else if (now >= deadline + intervalMs)
            decision = skip(slot, -1, deadline, now, SkipReason.LATE);
        else {
            capturing = true;
            captureSlot = slot;
            captureFrame = 0;
            frameFiredAtMs = now;
            framesFired++;
            decision = new Decision(Action.FIRE, slot, 0, deadline, now, null);
        }
        updateDone();
        return decision;
    }

    /** A frame is captured. Ignored in burst mode, and when no frame is in flight (a late or duplicate callback). */
    public Decision onFrameCaptured() {
        if (!capturing || plan.isBurst())
            return Decision.WAIT;
        long now = clock.nowMs();
        if (captureFrame + 1 < plan.framesPerInterval()) {
            captureFrame++;
            frameFiredAtMs = now;
            framesFired++;
            return new Decision(Action.FIRE, captureSlot, captureFrame, deadlineMs(captureSlot), now, null);
        }
        return completeSlot(now);
    }

    /**
     * Stops the clock on the run. A capture in flight can't be trusted after the camera is
     * released, so its slot is counted as an {@link SkipReason#INTERRUPTED} skip; if that was the
     * last slot of a {@link StopOn#SLOTS} run, the run is done.
     *
     * @return the INTERRUPTED SKIP decision for the slot that was capturing, otherwise WAIT
     */
    public Decision pause() {
        if (!started || paused || done)
            return Decision.WAIT;
        paused = true;
        pausedAtMs = clock.nowMs();
        if (capturing)
            return closeCapture(pausedAtMs, SkipReason.INTERRUPTED);
        updateDone();
        return Decision.WAIT;
    }

    /** Shifts {@code t0} by the paused time, so every remaining deadline keeps its place in the run. */
    public void resume() {
        if (!paused)
            return;
        paused = false;
        long shift = clock.nowMs() - pausedAtMs;
        t0 += shift;
        if (lastCaptureEndMs != Long.MIN_VALUE)
            lastCaptureEndMs += shift;
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

    public ShotPlan plan() {
        return plan;
    }

    public StopOn stopOn() {
        return stopOn;
    }

    public long captureTimeoutMs() {
        return captureTimeoutMs;
    }

    public long t0Ms() {
        return t0;
    }

    public boolean isStarted() {
        return started;
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

    /** Slots resolved so far (fired or skipped at their deadline). */
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
                return Decision.WAIT;
            capturing = true;
            captureSlot = 0;
            captureFrame = 0;
            nextSlot = 1;
            framesFired++;
            return new Decision(Action.FIRE, 0, 0, t0, now, null);
        }
        if (now < burstEndMs())
            return Decision.WAIT;
        return completeSlot(now);
    }

    private long burstEndMs() {
        return plan.endMs(t0);
    }

    private Decision completeSlot(long now) {
        capturing = false;
        lastCaptureEndMs = now;
        captured++;
        updateDone();
        return new Decision(Action.SLOT_DONE, captureSlot, captureFrame, deadlineMs(captureSlot), now, null);
    }

    private Decision closeCapture(long now, SkipReason reason) {
        capturing = false;
        lastCaptureEndMs = now;
        Decision decision = skip(captureSlot, captureFrame, deadlineMs(captureSlot), now, reason);
        updateDone();
        return decision;
    }

    private Decision skip(long slot, int frame, long deadline, long now, SkipReason reason) {
        skips[reason.ordinal()]++;
        return new Decision(Action.SKIP, slot, frame, deadline, now, reason);
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
