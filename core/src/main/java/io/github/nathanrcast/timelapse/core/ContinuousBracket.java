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

    public ContinuousBracket(int frames) {
        this.frames = frames;
    }

    /** A new slot is firing. */
    public synchronized void startSlot() {
        received = 0;
    }

    /** Counts one callback; returns the number received in this slot so far (1-based). */
    public synchronized int onCallback() {
        return ++received;
    }

    /** True when the callback just counted was the last one expected. */
    public synchronized boolean complete() {
        return received >= frames;
    }

    /** True while the slot has begun reporting but not finished: a timeout should be armed. */
    public synchronized boolean waiting() {
        return received > 0 && received < frames;
    }

    /** Gives up on the callbacks still missing, so the slot counts as complete and no timeout is re-armed. */
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
