package io.github.nathanrcast.timelapse.core;

/**
 * Decides when the camera's auto power-off must be (re)suppressed or released for a run. The platform broadcast
 * is fire-and-forget and may lapse, so a held guard asks to re-assert it every {@link #REASSERT_MS}. Release
 * happens once. All calls come from one thread.
 */
public final class AwakeGuard {
    public static final long REASSERT_MS = 10 * 60 * 1000L;

    private boolean held;
    private long assertedAtMs;

    /** True when the caller must send "suppress" now: the first call, and after a release. */
    public boolean acquire(long nowMs) {
        if (held)
            return false;
        held = true;
        assertedAtMs = nowMs;
        return true;
    }

    /** True when the caller must send "suppress" again: held, and the last one is {@link #REASSERT_MS} old. */
    public boolean reassertDue(long nowMs) {
        if (!held || nowMs - assertedAtMs < REASSERT_MS)
            return false;
        assertedAtMs = nowMs;
        return true;
    }

    /** True when the caller must send "enable" now; false if nothing was held (idempotent). */
    public boolean release() {
        if (!held)
            return false;
        held = false;
        return true;
    }

    public boolean isHeld() {
        return held;
    }
}
