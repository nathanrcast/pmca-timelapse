package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StepTableTest {
    private static final StepTable INTERVAL = StepTables.INTERVAL_MS;
    private static final StepTable SHOTS = StepTables.SHOTS;
    private static final StepTable DELAY = StepTables.DELAY_MIN;

    @Test
    public void intervalTableIsTheSpecifiedOne() {
        double[] seconds = {0, 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 45, 60, 90, 120, 180, 300,
                600, 900, 1200, 1800, 2400};
        assertEquals(seconds.length, INTERVAL.size());
        for (int i = 0; i < seconds.length; i++)
            assertEquals(seconds[i], INTERVAL.value(i) / 1000.0, 0);
    }

    @Test
    public void shotsTableIsTheSpecifiedOne() {
        assertEquals(1, SHOTS.value(0));
        assertEquals(2, SHOTS.value(1));
        assertEquals(3, SHOTS.value(2));
        assertEquals(5, SHOTS.value(3));
        assertEquals(10, SHOTS.value(4));
        assertEquals(20, SHOTS.value(5));
        assertEquals(100, SHOTS.value(SHOTS.nearestIndex(100)));
        assertEquals(150, SHOTS.value(SHOTS.nearestIndex(100) + 1));
        assertEquals(1000, SHOTS.value(SHOTS.nearestIndex(1000)));
        assertEquals(1500, SHOTS.value(SHOTS.nearestIndex(1000) + 1));
        assertEquals(10000, SHOTS.value(SHOTS.maxIndex() - 1));
        assertEquals(Settings.SHOTS_INFINITE, SHOTS.value(SHOTS.maxIndex()));
        assertEquals(4 + 10 + 18 + 18 + 1, SHOTS.size());
        for (int i = 5; i < SHOTS.size() - 1; i++) {
            long step = SHOTS.value(i) - SHOTS.value(i - 1);
            long v = SHOTS.value(i - 1);
            assertEquals("after " + v, v < 100 ? 10 : v < 1000 ? 50 : 500, step);
        }
    }

    @Test
    public void delayTableIsTheSpecifiedOne() {
        long[] minutes = {0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440};
        assertEquals(minutes.length, DELAY.size());
        for (int i = 0; i < minutes.length; i++)
            assertEquals(minutes[i], DELAY.value(i));
    }

    @Test
    public void stepMovesOneEntryAtATime() {
        assertEquals(1, INTERVAL.step(0, 1));
        assertEquals(2, INTERVAL.step(3, -1));
        assertEquals(5, SHOTS.step(2, 3));
    }

    @Test
    public void stepClampsAtBothEndsWithoutWrapping() {
        assertEquals(0, INTERVAL.step(0, -1));
        assertEquals(0, INTERVAL.step(1, -5));
        assertEquals(INTERVAL.maxIndex(), INTERVAL.step(INTERVAL.maxIndex(), 1));
        assertEquals(SHOTS.maxIndex(), SHOTS.step(SHOTS.maxIndex() - 1, 7));
        assertEquals(0, DELAY.step(0, -1));
        assertEquals(DELAY.maxIndex(), DELAY.step(DELAY.maxIndex(), 1));
    }

    @Test
    public void burstIsTheFirstIntervalAndInfiniteTheLastShotCount() {
        assertEquals(0, INTERVAL.value(0));
        assertEquals(Settings.SHOTS_INFINITE, SHOTS.value(SHOTS.step(SHOTS.maxIndex(), 1)));
        assertEquals(INTERVAL.value(1), INTERVAL.value(INTERVAL.step(0, 1)));
        assertTrue(INTERVAL.value(1) > 0);
    }

    @Test
    public void indexOutsideTheTableReadsAsTheNearestEnd() {
        assertEquals(INTERVAL.value(0), INTERVAL.value(-4));
        assertEquals(INTERVAL.value(INTERVAL.maxIndex()), INTERVAL.value(500));
        assertEquals(0, SHOTS.clamp(-1));
        assertEquals(SHOTS.maxIndex(), SHOTS.clamp(9999));
    }

    @Test
    public void nearestIndexSnapsToTheClosestEntry() {
        assertEquals(INTERVAL.nearestIndex(500), INTERVAL.nearestIndex(600));
        assertEquals(8000, INTERVAL.value(INTERVAL.nearestIndex(7500)));
        assertEquals(0, INTERVAL.nearestIndex(100));
        assertEquals(INTERVAL.maxIndex(), INTERVAL.nearestIndex(9999999));
        assertEquals(1, SHOTS.value(SHOTS.nearestIndex(0)));
        assertEquals(5, SHOTS.value(SHOTS.nearestIndex(6)));
        assertEquals(10000, SHOTS.value(SHOTS.nearestIndex(20000)));
        assertEquals(Settings.SHOTS_INFINITE, SHOTS.value(SHOTS.nearestIndex(Settings.SHOTS_INFINITE)));
        assertEquals(1440, DELAY.value(DELAY.nearestIndex(5000)));
    }

    @Test
    public void nearestIndexTiesGoToTheLowerEntry() {
        assertEquals(1000, INTERVAL.value(INTERVAL.nearestIndex(1500)));
        assertEquals(20, SHOTS.value(SHOTS.nearestIndex(25)));
    }

    @Test
    public void everyEntrySnapsToItself() {
        for (int i = 0; i < INTERVAL.size(); i++)
            assertEquals(i, INTERVAL.nearestIndex(INTERVAL.value(i)));
        for (int i = 0; i < SHOTS.size(); i++)
            assertEquals(i, SHOTS.nearestIndex(SHOTS.value(i)));
        for (int i = 0; i < DELAY.size(); i++)
            assertEquals(i, DELAY.nearestIndex(DELAY.value(i)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void tableMustBeAscending() {
        new StepTable(1, 1);
    }
}
