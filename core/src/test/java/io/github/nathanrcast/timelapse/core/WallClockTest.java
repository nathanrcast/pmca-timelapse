package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallClockTest {
    // 2026-10-31 08:24:01 local, UTC-6: the instant is 14:24:01 UTC.
    private static final long EPOCH = 1793456641000L;
    private static final int OFFSET = -6 * 3600000;

    private static WallClock fixed(final WallTime t) {
        return new WallClock() {
            @Override
            public WallTime now() {
                return t;
            }
        };
    }

    private static final WallClock THROWING = new WallClock() {
        @Override
        public WallTime now() {
            throw new IllegalStateException("no rtc");
        }
    };

    @Test
    public void runIdAndLocalTimeUseTheReadingsOwnOffset() {
        WallTime t = new WallTime(EPOCH, OFFSET, WallTime.SOURCE_CAMERA);
        assertEquals("20261031-082401", t.runId());
        assertEquals("08:24:01.000", t.format("HH:mm:ss.SSS"));
        assertEquals(EPOCH + OFFSET, t.localMs());
        assertEquals(-360, t.utcOffsetMinutes());
        assertTrue(t.fromCamera());
    }

    @Test
    public void fileTimeIsLocalTimeReadThroughTheVmsZone() {
        WallTime t = new WallTime(EPOCH, OFFSET, WallTime.SOURCE_CAMERA);
        assertEquals(EPOCH + OFFSET, t.fileTimeMs(0));
        assertEquals(EPOCH, t.fileTimeMs(OFFSET));
    }

    @Test
    public void mtimeFloorAcceptsBothWaysTheCardTimeCanArrive() {
        WallTime west = new WallTime(EPOCH, OFFSET, WallTime.SOURCE_CAMERA);
        assertEquals(EPOCH + OFFSET, west.mtimeFloorMs(0));
        WallTime east = new WallTime(EPOCH, 3600000, WallTime.SOURCE_CAMERA);
        assertEquals(EPOCH, east.mtimeFloorMs(0));
    }

    @Test
    public void cameraClockIsUsedWhenItWorks() {
        WallTime cam = new WallTime(EPOCH, OFFSET, WallTime.SOURCE_CAMERA);
        WallTime sys = new WallTime(5000, 0, WallTime.SOURCE_SYSTEM);
        final int[] notices = {0};
        FallbackWallClock c = new FallbackWallClock(fixed(cam), fixed(sys), new FallbackWallClock.Notice() {
            @Override
            public void fellBack(Throwable cause) {
                notices[0]++;
            }
        });
        assertSame(cam, c.now());
        assertEquals(0, notices[0]);
    }

    @Test
    public void fallsBackToTheSystemClockAndSaysSoOnce() {
        WallTime sys = new WallTime(5000, 0, WallTime.SOURCE_SYSTEM);
        final int[] notices = {0};
        final Throwable[] cause = {null};
        FallbackWallClock c = new FallbackWallClock(THROWING, fixed(sys), new FallbackWallClock.Notice() {
            @Override
            public void fellBack(Throwable t) {
                notices[0]++;
                cause[0] = t;
            }
        });
        assertSame(sys, c.now());
        assertSame(sys, c.now());
        assertEquals(1, notices[0]);
        assertEquals("no rtc", cause[0].getMessage());
        assertFalse(c.now().fromCamera());
    }

    @Test
    public void onceFallenBackItStaysOnTheSystemClock() {
        final int[] calls = {0};
        WallClock flaky = new WallClock() {
            @Override
            public WallTime now() {
                if (calls[0]++ == 0)
                    throw new IllegalStateException("once");
                return new WallTime(EPOCH, OFFSET, WallTime.SOURCE_CAMERA);
            }
        };
        WallTime sys = new WallTime(5000, 0, WallTime.SOURCE_SYSTEM);
        FallbackWallClock c = new FallbackWallClock(flaky, fixed(sys), null);
        assertSame(sys, c.now());
        assertSame(sys, c.now());
        assertEquals(1, calls[0]);
    }

    @Test
    public void anErrorFromTheFrameworkIsCaughtToo() {
        WallClock bad = new WallClock() {
            @Override
            public WallTime now() {
                throw new AssertionError("firmware");
            }
        };
        WallTime sys = new WallTime(5000, 0, WallTime.SOURCE_SYSTEM);
        assertSame(sys, new FallbackWallClock(bad, fixed(sys), null).now());
    }

    @Test
    public void aNullReadingFallsBackToo() {
        WallTime sys = new WallTime(5000, 0, WallTime.SOURCE_SYSTEM);
        FallbackWallClock c = new FallbackWallClock(fixed(null), fixed(sys), null);
        assertSame(sys, c.now());
    }
}
