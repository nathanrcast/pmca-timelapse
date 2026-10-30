package io.github.nathanrcast.timelapse;

import static org.junit.Assert.assertEquals;

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
}
