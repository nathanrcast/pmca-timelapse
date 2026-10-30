package io.github.nathanrcast.timelapse.core;

/** Millisecond time source, so timing logic can run against a fake clock in tests. */
public interface Clock {
    long nowMs();
}
