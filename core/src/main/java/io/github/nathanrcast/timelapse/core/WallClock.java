package io.github.nathanrcast.timelapse.core;

/** Source of real wall time. The camera implementation lives in the app; this keeps the policy testable. */
public interface WallClock {
    WallTime now();
}
