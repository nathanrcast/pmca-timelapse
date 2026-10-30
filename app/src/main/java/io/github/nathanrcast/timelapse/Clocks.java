package io.github.nathanrcast.timelapse;

import android.os.SystemClock;

import io.github.nathanrcast.timelapse.core.Clock;

final class Clocks {
    // Handler's time base, so scheduler deadlines can go straight to postAtTime.
    static final Clock UPTIME = new Clock() {
        @Override
        public long nowMs() {
            return SystemClock.uptimeMillis();
        }
    };

    private Clocks() {}
}
