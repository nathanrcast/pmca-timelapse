package io.github.nathanrcast.timelapse.core;

/** A {@link Clock} that only moves when a test moves it. */
public final class FakeClock implements Clock {
    private long nowMs;

    public FakeClock(long startMs) {
        nowMs = startMs;
    }

    @Override
    public long nowMs() {
        return nowMs;
    }

    public void advance(long ms) {
        nowMs += ms;
    }

    public void set(long ms) {
        nowMs = ms;
    }
}
