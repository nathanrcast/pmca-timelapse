package io.github.nathanrcast.timelapse;

import android.os.SystemClock;

import com.github.ma1co.openmemories.framework.DateTime;

import java.util.Calendar;

import io.github.nathanrcast.timelapse.core.Clock;

final class Clocks {
    // Handler's time base, so scheduler deadlines can go straight to postAtTime.
    static final Clock UPTIME = new Clock() {
        @Override
        public long nowMs() {
            return SystemClock.uptimeMillis();
        }
    };

    // The camera's clock (OpenMemories DateTime), else the system one.
    static Calendar wallCalendar() {
        try {
            Calendar c = DateTime.getInstance().getCurrentTime();
            if (c != null)
                return c;
        } catch (Throwable ignored) {
        }
        return Calendar.getInstance();
    }

    private Clocks() {}
}
