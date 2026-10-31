package io.github.nathanrcast.timelapse;

import android.os.SystemClock;

import com.github.ma1co.openmemories.framework.DateTime;

import java.util.Calendar;
import java.util.TimeZone;

import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.FallbackWallClock;
import io.github.nathanrcast.timelapse.core.StartTime;
import io.github.nathanrcast.timelapse.core.WallClock;
import io.github.nathanrcast.timelapse.core.WallTime;

final class Clocks {
    // Handler's time base, so scheduler deadlines can go straight to postAtTime.
    static final Clock UPTIME = new Clock() {
        @Override
        public long nowMs() {
            return SystemClock.uptimeMillis();
        }
    };

    // The camera's RTC (OpenMemories DateTime). On the bodies System.currentTimeMillis() counts from boot, so it is
    // only the fallback when the framework call fails.
    private static final WallClock CAMERA = new WallClock() {
        @Override
        public WallTime now() {
            Calendar c = DateTime.getInstance().getCurrentTime();
            if (c == null)
                return null;
            long ms = c.getTimeInMillis();
            return new WallTime(ms, c.getTimeZone().getOffset(ms), WallTime.SOURCE_CAMERA);
        }
    };

    private static final WallClock SYSTEM = new WallClock() {
        @Override
        public WallTime now() {
            long ms = System.currentTimeMillis();
            return new WallTime(ms, TimeZone.getDefault().getOffset(ms), WallTime.SOURCE_SYSTEM);
        }
    };

    // The single wall-clock source for run ids, LOG.CSV, CAPS headers, the AT start and the card comparison.
    static final WallClock WALL = new FallbackWallClock(CAMERA, SYSTEM, new FallbackWallClock.Notice() {
        @Override
        public void fellBack(Throwable cause) {
            Logger.error("Clock: camera DateTime unavailable, using System.currentTimeMillis(): " + cause);
        }
    });

    static WallTime wallNow() {
        return WALL.now();
    }

    // Milliseconds since local midnight on the wall clock, for the AT start.
    static long wallMsOfDay() {
        long local = wallNow().localMs();
        return ((local % StartTime.DAY_MS) + StartTime.DAY_MS) % StartTime.DAY_MS;
    }

    private Clocks() {}
}
