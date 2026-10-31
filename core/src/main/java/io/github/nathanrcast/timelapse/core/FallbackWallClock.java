package io.github.nathanrcast.timelapse.core;

/**
 * The primary clock (camera RTC) with a fallback (system) for when the primary throws or returns null. The switch is
 * reported once, through {@link Notice}, and the clock then stays on the fallback for good: a run never mixes camera
 * and boot-based times, and a broken primary costs one failed call, not one per log line.
 */
public final class FallbackWallClock implements WallClock {
    public interface Notice {
        void fellBack(Throwable cause);
    }

    private final WallClock primary;
    private final WallClock fallback;
    private final Notice notice;
    private boolean latched;

    public FallbackWallClock(WallClock primary, WallClock fallback, Notice notice) {
        this.primary = primary;
        this.fallback = fallback;
        this.notice = notice;
    }

    @Override
    public synchronized WallTime now() {
        if (!latched) {
            Throwable cause;
            try {
                WallTime t = primary.now();
                if (t != null)
                    return t;
                cause = new IllegalStateException("camera clock returned null");
            } catch (Throwable e) {
                cause = e;
            }
            latched = true;
            if (notice != null)
                notice.fellBack(cause);
        }
        return fallback.now();
    }
}
