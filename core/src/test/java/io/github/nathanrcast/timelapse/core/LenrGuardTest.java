package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.LenrGuard.Action.NONE;
import static io.github.nathanrcast.timelapse.core.LenrGuard.Action.TURN_OFF;
import static io.github.nathanrcast.timelapse.core.LenrGuard.Action.WARN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LenrGuardTest {
    @Test
    public void singleFrameMinimumIsTwiceTheShutterPlusOneSecond() {
        assertEquals(5000, LenrGuard.minIntervalMs(true, 2000, 1, 0));
        assertEquals(3000, LenrGuard.minIntervalMs(true, 1000, 1, 0));
        assertEquals(31000, LenrGuard.minIntervalMs(true, 15000, 1, 0));
    }

    @Test
    public void bracketOfThreeAt07EvSumsTheThreeExposures() {
        // 2 s at -0.7, 0, +0.7 EV: 1231 + 2000 + 3249 ms; twice that, plus 1 s per frame
        assertEquals(1231, LenrGuard.frameMs(2000, 3, 7, 0));
        assertEquals(2000, LenrGuard.frameMs(2000, 3, 7, 1));
        assertEquals(3249, LenrGuard.frameMs(2000, 3, 7, 2));
        assertEquals(15960, LenrGuard.minIntervalMs(true, 2000, 3, 7));
    }

    @Test
    public void bracketOfThreeAt3EvSumsTheThreeExposures() {
        // 2 s at -3, 0, +3 EV: 250 + 2000 + 16000 ms
        assertEquals(39500, LenrGuard.minIntervalMs(true, 2000, 3, 30));
    }

    @Test
    public void aShortBaseShutterStillCountsWhenABracketFrameIsLong() {
        // 0.5 s at +1 EV is 1 s
        assertEquals(1000, LenrGuard.longestFrameMs(500, 3, 10));
        assertEquals(2 * (250 + 500 + 1000) + 3000, LenrGuard.minIntervalMs(true, 500, 3, 10));
        assertEquals(0, LenrGuard.minIntervalMs(true, 500, 3, 7));
    }

    @Test
    public void noMinimumWhenNrDoesNotApply() {
        assertEquals(0, LenrGuard.minIntervalMs(false, 2000, 1, 0));
        assertEquals(0, LenrGuard.minIntervalMs(true, 999, 1, 0));
        assertEquals(0, LenrGuard.minIntervalMs(null, 2000, 1, 0));
        assertEquals(0, LenrGuard.minIntervalMs(true, -1, 1, 0));
    }

    @Test
    public void intervalIsRaisedOnlyWhenShorter() {
        assertEquals(5000, LenrGuard.effectiveIntervalMs(3000, true, 2000, 1, 0));
        assertEquals(5000, LenrGuard.effectiveIntervalMs(5000, true, 2000, 1, 0));
        assertEquals(8000, LenrGuard.effectiveIntervalMs(8000, true, 2000, 1, 0));
        assertEquals(3000, LenrGuard.effectiveIntervalMs(3000, false, 2000, 1, 0));
        assertEquals(15960, LenrGuard.effectiveIntervalMs(5000, true, 2000, 3, 7));
    }

    @Test
    public void burstIsNeverRaised() {
        assertEquals(0, LenrGuard.effectiveIntervalMs(0, true, 2000, 1, 0));
    }

    @Test
    public void shutterConversionRounds() {
        assertEquals(2000, Util.shutterMs(2, 1));
        assertEquals(500, Util.shutterMs(1, 2));
        assertEquals(333, Util.shutterMs(1, 3));
        assertEquals(-1, Util.shutterMs(1, 0));
    }

    @Test
    public void decision() {
        assertEquals(NONE, LenrGuard.decide(false, 2000, true, true, false));
        assertEquals(NONE, LenrGuard.decide(null, 2000, true, true, false));
        assertEquals(NONE, LenrGuard.decide(true, 500, true, true, false));
        assertEquals(TURN_OFF, LenrGuard.decide(true, 2000, true, true, false));
        assertEquals(WARN, LenrGuard.decide(true, 2000, false, true, false));
        assertEquals(WARN, LenrGuard.decide(true, 2000, true, false, false));
        assertEquals(NONE, LenrGuard.decide(true, 2000, false, true, true));
    }

    @Test
    public void staleRestoreNeedsTheFlagAndLenrNotAlreadyOn() {
        assertTrue(LenrGuard.shouldRestore(true, false));
        assertTrue(LenrGuard.shouldRestore(true, null));
        assertFalse(LenrGuard.shouldRestore(true, true));
        assertFalse(LenrGuard.shouldRestore(false, false));
        assertFalse(LenrGuard.shouldRestore(false, null));
    }

    @Test
    public void headerValue() {
        assertEquals("on", LenrGuard.headerValue(true));
        assertEquals("off", LenrGuard.headerValue(false));
        assertEquals("unknown", LenrGuard.headerValue(null));
    }
}
