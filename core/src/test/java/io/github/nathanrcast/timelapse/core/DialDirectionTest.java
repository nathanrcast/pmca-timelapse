package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DialDirectionTest {
    // Events as logged: {clockwise, status}. a6500: scan 525 +22, 526 -22. NEX-5T: 525 -22, 526 +22, KURU 522 +22,
    // 523 -22.
    @Test
    public void a6500EventsStepByScanCode() {
        assertEquals(1, DialDirection.steps(true, 22));
        assertEquals(-1, DialDirection.steps(false, -22));
    }

    @Test
    public void nex5tEventsWithTheOppositeSignStepTheSameWay() {
        assertEquals(1, DialDirection.steps(true, -22));
        assertEquals(-1, DialDirection.steps(false, 22));
        assertEquals(1, DialDirection.steps(true, 22));
        assertEquals(-1, DialDirection.steps(false, -22));
    }

    @Test
    public void multiNotchEventsUseTheMagnitude() {
        assertEquals(3, DialDirection.steps(true, -66));
        assertEquals(-2, DialDirection.steps(false, 44));
    }

    @Test
    public void aZeroOrUnreadableStatusStillStepsOnce() {
        assertEquals(1, DialDirection.steps(true, 0));
        assertEquals(-1, DialDirection.steps(false, 0));
    }
}
