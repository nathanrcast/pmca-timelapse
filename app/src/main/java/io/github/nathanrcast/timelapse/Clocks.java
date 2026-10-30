package io.github.nathanrcast.timelapse;

import android.os.SystemClock;

import io.github.nathanrcast.timelapse.core.Clock;

final class Clocks {
    // Wall time, what the capture loop has always used.
    static final Clock WALL = new Clock() {
        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }
    };

    // Handler's time base (postAtTime), for deadline scheduling.
    static final Clock UPTIME = new Clock() {
        @Override
        public long nowMs() {
            return SystemClock.uptimeMillis();
        }
    };

    private Clocks() {}
}
