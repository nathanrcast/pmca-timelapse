package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContinuousBracketTest {
    @Test
    public void completesAfterTheThirdCallback() {
        ContinuousBracket b = new ContinuousBracket(3);
        b.startSlot();
        assertFalse(b.waiting());
        assertEquals(1, b.onCallback());
        assertFalse(b.complete());
        assertTrue(b.waiting());
        assertEquals(2, b.missing());
        b.onCallback();
        assertEquals(1, b.missing());
        b.onCallback();
        assertTrue(b.complete());
        assertFalse(b.waiting());
        assertEquals(0, b.missing());
    }

    @Test
    public void aBodyThatReportsOnceStaysWaitingForTheTimeout() {
        ContinuousBracket b = new ContinuousBracket(3);
        b.startSlot();
        b.onCallback();
        assertTrue(b.waiting());
        assertEquals(2, b.missing());
    }

    @Test
    public void abandoningACutShortSlotStopsTheWait() {
        ContinuousBracket b = new ContinuousBracket(3);
        b.startSlot();
        b.onCallback();
        b.abandon();
        assertFalse(b.waiting());
        assertEquals(0, b.missing());
    }

    @Test
    public void callbacksAfterAnAbandonedSlotAreStraysNotTheNextSlots() {
        ContinuousBracket b = new ContinuousBracket(3);
        assertEquals(1, b.startSlot());
        b.onCallback();
        b.abandon();
        assertEquals(0, b.onCallback());
        assertEquals(0, b.onCallback());
        assertEquals(2, b.strays());
        assertEquals(2, b.startSlot());
        assertEquals(1, b.onCallback());
        assertEquals(2, b.missing());
    }

    @Test
    public void aCallbackAfterACompleteSlotIsAStray() {
        ContinuousBracket b = new ContinuousBracket(3);
        b.startSlot();
        b.onCallback();
        b.onCallback();
        b.onCallback();
        assertEquals(0, b.onCallback());
        assertEquals(1, b.strays());
    }

    @Test
    public void aNewSlotResetsTheCount() {
        ContinuousBracket b = new ContinuousBracket(3);
        b.startSlot();
        b.onCallback();
        b.onCallback();
        b.onCallback();
        b.startSlot();
        assertFalse(b.complete());
        assertEquals(3, b.missing());
    }

    @Test
    public void timeoutCoversTheRemainingExposures() {
        assertEquals(2000, ContinuousBracket.timeoutMs(-1, 2));
        assertEquals(2000 + 2 * 1000, ContinuousBracket.timeoutMs(1000, 2));
        assertEquals(3000, ContinuousBracket.timeoutMs(1000, 0));
    }
}
