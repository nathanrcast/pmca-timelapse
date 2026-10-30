package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AwakeGuardTest {
    @Test
    public void acquireAndReleaseEachActOnce() {
        AwakeGuard g = new AwakeGuard();
        assertFalse(g.release());
        assertTrue(g.acquire(0));
        assertFalse(g.acquire(1));
        assertTrue(g.isHeld());
        assertTrue(g.release());
        assertFalse(g.release());
        assertFalse(g.isHeld());
        assertTrue(g.acquire(5));
    }

    @Test
    public void reassertsEveryTenMinutesWhileHeld() {
        AwakeGuard g = new AwakeGuard();
        assertFalse(g.reassertDue(AwakeGuard.REASSERT_MS * 2));
        g.acquire(1000);
        assertFalse(g.reassertDue(1000 + AwakeGuard.REASSERT_MS - 1));
        assertTrue(g.reassertDue(1000 + AwakeGuard.REASSERT_MS));
        assertFalse(g.reassertDue(1000 + AwakeGuard.REASSERT_MS + 5));
        assertTrue(g.reassertDue(1000 + 2 * AwakeGuard.REASSERT_MS));
        g.release();
        assertFalse(g.reassertDue(1000 + 10 * AwakeGuard.REASSERT_MS));
    }
}
