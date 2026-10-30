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
    public void minimumIsTwiceTheShutterPlusOneSecond() {
        assertEquals(5000, LenrGuard.minIntervalMs(true, 2000));
        assertEquals(3000, LenrGuard.minIntervalMs(true, 1000));
        assertEquals(31000, LenrGuard.minIntervalMs(true, 15000));
    }

    @Test
    public void noMinimumWhenNrDoesNotApply() {
        assertEquals(0, LenrGuard.minIntervalMs(false, 2000));
        assertEquals(0, LenrGuard.minIntervalMs(true, 999));
        assertEquals(0, LenrGuard.minIntervalMs(null, 2000));
        assertEquals(0, LenrGuard.minIntervalMs(true, -1));
    }

    @Test
    public void intervalIsRaisedOnlyWhenShorter() {
        assertEquals(5000, LenrGuard.effectiveIntervalMs(3000, true, 2000));
        assertEquals(5000, LenrGuard.effectiveIntervalMs(5000, true, 2000));
        assertEquals(8000, LenrGuard.effectiveIntervalMs(8000, true, 2000));
        assertEquals(3000, LenrGuard.effectiveIntervalMs(3000, false, 2000));
    }

    @Test
    public void burstIsNeverRaised() {
        assertEquals(0, LenrGuard.effectiveIntervalMs(0, true, 2000));
    }

    @Test
    public void shutterConversion() {
        assertEquals(2000, LenrGuard.shutterMs(2, 1));
        assertEquals(1000, LenrGuard.shutterMs(1, 1));
        assertEquals(500, LenrGuard.shutterMs(1, 2));
        assertEquals(-1, LenrGuard.shutterMs(1, 0));
    }

    @Test
    public void decision() {
        assertEquals(NONE, LenrGuard.decide(false, 2000, true, true));
        assertEquals(NONE, LenrGuard.decide(null, 2000, true, true));
        assertEquals(NONE, LenrGuard.decide(true, 500, true, true));
        assertEquals(TURN_OFF, LenrGuard.decide(true, 2000, true, true));
        assertEquals(WARN, LenrGuard.decide(true, 2000, false, true));
        assertEquals(WARN, LenrGuard.decide(true, 2000, true, false));
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
