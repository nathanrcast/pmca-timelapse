package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

public class UtilTest {
    @Test
    public void formatTime() {
        assertEquals("0s", Util.formatTime(0));
        assertEquals("59s", Util.formatTime(59));
        assertEquals("1m 0s", Util.formatTime(60));
        assertEquals("59m 59s", Util.formatTime(3599));
        assertEquals("1h 0m", Util.formatTime(3600));
        assertEquals("25h 1m", Util.formatTime(90061));
        assertEquals("", Util.formatTime(-1));
        assertEquals("596523h 0m", Util.formatTime(2147483647L / 3600 * 3600));
        assertEquals("2562047788015h 12m", Util.formatTime(Long.MAX_VALUE / 1000));
    }

    @Test
    public void formatShutterSpeed() {
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            assertEquals("1/8000", Util.formatShutterSpeed(1, 8000));
            assertEquals("1/3", Util.formatShutterSpeed(1, 3));
            assertEquals("0.5\"", Util.formatShutterSpeed(1, 2));
            assertEquals("1\"", Util.formatShutterSpeed(1, 1));
            assertEquals("0.4\"", Util.formatShutterSpeed(10, 25));
            assertEquals("1.3\"", Util.formatShutterSpeed(13, 10));
            assertEquals("30\"", Util.formatShutterSpeed(30, 1));
            assertEquals("BULB", Util.formatShutterSpeed(65535, 1));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    public void formatShutterSpeedIndexCoversTheTable() {
        assertEquals("1/8000", Util.formatShutterSpeedIndex(0));
        assertEquals("30\"", Util.formatShutterSpeedIndex(Util.SHUTTER_SPEEDS.length - 1));
    }
}
