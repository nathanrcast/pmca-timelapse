package io.github.nathanrcast.timelapse.core;

/**
 * Callback bookkeeping for a body that only offers continuous exposure bracketing: one takePicture, the camera
 * shoots all the frames, and onShutter is expected once per frame. The slot is complete after the last expected
 * callback, or {@link #timeoutMs} after the first when the body reports only once. Thread-safe: callbacks arrive on
 * the camera thread, the timeout on the main looper.
 */
public final class ContinuousBracket {
    private final int frames;
    private int received;
    private int generation;
    private int strays;

    public ContinuousBracket(int frames) {
        this.frames = frames;
    }

    /** A new slot is firing; returns its generation (1-based). */
    public synchronized int startSlot() {
        received = 0;
        return ++generation;
    }

    /** The generation of the slot that is firing or was last fired. */
    public synchronized int generation() {
        return generation;
    }

    /**
     * Counts one callback and returns the number received in this slot so far (1-based), or 0 for a stray: a
     * callback after the slot already completed or was abandoned (a CANCELED one from cancelTakePicture, or frames
     * the body reported late). Strays are counted and must not reach the scheduler.
     */
    public synchronized int onCallback() {
        if (received >= frames) {
            strays++;
            return 0;
        }
        return ++received;
    }

    /** Callbacks ignored as strays so far. */
    public synchronized int strays() {
        return strays;
    }

    /** True when the callback just counted was the last one expected. */
    public synchronized boolean complete() {
        return received >= frames;
    }

    /** True while the slot has begun reporting but not finished: a timeout should be armed. */
    public synchronized boolean waiting() {
        return received > 0 && received < frames;
    }

    /**
     * Gives up on the callbacks still missing: the slot counts as complete, no timeout is re-armed, and callbacks
     * that still arrive before the next {@link #startSlot()} are strays.
     */
    public synchronized void abandon() {
        received = frames;
    }

    /** Frames the camera has not reported yet. */
    public synchronized int missing() {
        return Math.max(0, frames - received);
    }

    /** Wait after a callback for the next one: a generous floor plus the exposure time of the remaining frames. */
    public static long timeoutMs(long shutterMs, int missing) {
        return 2000 + Math.max(shutterMs, 0) * Math.max(missing, 1);
    }
}
