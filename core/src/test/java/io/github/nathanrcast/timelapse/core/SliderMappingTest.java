package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SliderMappingTest {
    @Test
    public void targetExposureRoundTripIsIdentity() {
        for (int stored = -30; stored <= 30; stored++)
            assertEquals(stored, SliderMapping.progressToTargetExposure(SliderMapping.targetExposureToProgress(stored)));
        for (int progress = 0; progress <= SliderMapping.TARGET_EXPOSURE_MAX; progress++)
            assertEquals(progress, SliderMapping.targetExposureToProgress(SliderMapping.progressToTargetExposure(progress)));
    }

    @Test
    public void countRoundTripIsIdentity() {
        for (int stored = 1; stored <= SliderMapping.COUNT_MAX + 1; stored++)
            assertEquals(stored, SliderMapping.progressToCount(SliderMapping.countToProgress(stored)));
        for (int progress = 0; progress <= SliderMapping.COUNT_MAX; progress++)
            assertEquals(progress, SliderMapping.countToProgress(SliderMapping.progressToCount(progress)));
    }

    @Test
    public void defaultsLoadAtTheirOwnPosition() {
        assertEquals(30, SliderMapping.targetExposureToProgress(0));
        assertEquals(4, SliderMapping.countToProgress(5));
        assertEquals(0, SliderMapping.countToProgress(1));
    }

    @Test
    public void outOfRangeValuesClampToTheSlider() {
        assertEquals(0, SliderMapping.targetExposureToProgress(-90));
        assertEquals(SliderMapping.TARGET_EXPOSURE_MAX, SliderMapping.targetExposureToProgress(99));
        assertEquals(0, SliderMapping.countToProgress(0));
        assertEquals(SliderMapping.COUNT_MAX, SliderMapping.countToProgress(500));
    }

    // Frozen from the pre-T05 SettingsActivity slider listeners: the first, second and last
    // position of every step segment, with the value and the text/unit the screen showed.
    private static final Object[][] INTERVALS = {
            {0, 0.0, "burst", ""},
            {1, 1.0, "1.0", "s"},
            {2, 1.5, "1.5", "s"},
            {39, 20.0, "20.0", "s"},
            {40, 21.0, "21.0", "s"},
            {41, 22.0, "22.0", "s"},
            {58, 39.0, "39.0", "s"},
            {59, 40.0, "40.0", "s"},
            {60, 45.0, "45.0", "s"},
            {74, 115.0, "115.0", "s"},
            {75, 120.0, "2.0", "min"},
            {76, 150.0, "2.5", "min"},
            {91, 600.0, "10.0", "min"},
            {92, 660.0, "11", "min"},
            {93, 720.0, "12", "min"},
            {119, 2280.0, "38", "min"},
    };

    private static final Object[][] SHOTS = {
            {0, 1, "1"},
            {1, 2, "2"},
            {19, 20, "20"},
            {20, 25, "25"},
            {21, 30, "30"},
            {35, 100, "100"},
            {36, 120, "120"},
            {37, 140, "140"},
            {80, 1000, "1000"},
            {81, 1050, "1050"},
            {82, 1100, "1100"},
            {99, 1950, "1950"},
            {100, 2100, "2100"},
            {101, 2200, "2200"},
            {129, 5000, "5000"},
            {130, Settings.SHOTS_INFINITE, "inf"},
    };

    private static final Object[][] DELAYS = {
            {0, 0, "0.0", "min"},
            {1, 1, "1.0", "min"},
            {5, 5, "5.0", "min"},
            {6, 10, "10.0", "min"},
            {7, 15, "15.0", "min"},
            {15, 55, "55.0", "min"},
            {16, 60, "1.0", "h"},
            {17, 120, "2.0", "h"},
            {39, 1440, "24.0", "h"},
    };

    @Test
    public void intervalSliderMatchesTheFrozenTable() {
        for (Object[] row : INTERVALS) {
            int progress = (Integer) row[0];
            double interval = SliderMapping.progressToInterval(progress);
            assertEquals("progress " + progress, (Double) row[1], interval, 0);
            assertEquals("progress " + progress, row[2], SliderMapping.intervalText(interval));
            assertEquals("progress " + progress, row[3], SliderMapping.intervalUnit(interval));
        }
        assertEquals(SliderMapping.INTERVAL_MAX, INTERVALS[INTERVALS.length - 1][0]);
    }

    @Test
    public void shotsSliderMatchesTheFrozenTable() {
        for (Object[] row : SHOTS) {
            int progress = (Integer) row[0];
            int shotCount = SliderMapping.progressToShotCount(progress);
            assertEquals("progress " + progress, row[1], shotCount);
            assertEquals("progress " + progress, row[2], SliderMapping.shotCountText(shotCount));
        }
        assertEquals(SliderMapping.SHOTS_MAX, SHOTS[SHOTS.length - 1][0]);
    }

    @Test
    public void delaySliderMatchesTheFrozenTable() {
        for (Object[] row : DELAYS) {
            int progress = (Integer) row[0];
            int delay = SliderMapping.progressToDelay(progress);
            assertEquals("progress " + progress, row[1], delay);
            assertEquals("progress " + progress, row[2], SliderMapping.delayText(delay));
            assertEquals("progress " + progress, row[3], SliderMapping.delayUnit(delay));
        }
        assertEquals(SliderMapping.DELAY_MAX, DELAYS[DELAYS.length - 1][0]);
    }

    // With the frozen rows at the first, second and last position of each segment, constant
    // steps in between pin every slider position.
    @Test
    public void slidersStepEvenlyBetweenTheFrozenRows() {
        assertEvenSteps(INTERVALS, new Stepper() {
            public double at(int progress) {
                return SliderMapping.progressToInterval(progress);
            }
        });
        assertEvenSteps(SHOTS, new Stepper() {
            public double at(int progress) {
                return SliderMapping.progressToShotCount(progress);
            }
        });
        assertEvenSteps(DELAYS, new Stepper() {
            public double at(int progress) {
                return SliderMapping.progressToDelay(progress);
            }
        });
    }

    @Test
    public void positionsOutsideTheSliderClamp() {
        assertEquals(0.0, SliderMapping.progressToInterval(-1), 0);
        assertEquals(2280.0, SliderMapping.progressToInterval(500), 0);
        assertEquals(1, SliderMapping.progressToShotCount(-5));
        assertEquals(Settings.SHOTS_INFINITE, SliderMapping.progressToShotCount(200));
        assertEquals(0, SliderMapping.progressToDelay(-1));
        assertEquals(1440, SliderMapping.progressToDelay(99));
    }

    private interface Stepper {
        double at(int progress);
    }

    // Between two frozen rows more than one position apart, every position advances by the same step.
    private static void assertEvenSteps(Object[][] rows, Stepper stepper) {
        for (int r = 0; r + 1 < rows.length; r++) {
            int from = (Integer) rows[r][0];
            int to = (Integer) rows[r + 1][0];
            if (to - from < 2)
                continue;
            double step = stepper.at(from + 1) - stepper.at(from);
            for (int p = from + 1; p < to; p++)
                assertEquals("progress " + p, step, stepper.at(p + 1) - stepper.at(p), 1e-9);
        }
    }
}
