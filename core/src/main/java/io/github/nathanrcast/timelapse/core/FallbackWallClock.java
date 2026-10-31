package io.github.nathanrcast.timelapse.core;

/**
 * The primary clock (camera RTC) with a fallback (system) for when the primary throws or returns null. The switch is
 * reported once, through {@link Notice}, and the fallback then serves that call only: the primary is tried again.
 */
public final class FallbackWallClock implements WallClock {
    public interface Notice {
        void fellBack(Throwable cause);
    }

    private final WallClock primary;
    private final WallClock fallback;
    private final Notice notice;
    private boolean reported;

    public FallbackWallClock(WallClock primary, WallClock fallback, Notice notice) {
        this.primary = primary;
        this.fallback = fallback;
        this.notice = notice;
    }

    @Override
    public synchronized WallTime now() {
        Throwable cause;
        try {
            WallTime t = primary.now();
            if (t != null)
                return t;
            cause = new IllegalStateException("camera clock returned null");
        } catch (RuntimeException e) {
            cause = e;
        } catch (LinkageError e) {
            cause = e;
        }
        if (!reported) {
            reported = true;
            if (notice != null)
                notice.fellBack(cause);
        }
        return fallback.now();
    }
}
