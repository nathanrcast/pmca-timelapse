package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ShotPlanTest {
    private static final int[] FPS = {24, 25, 30, 50, 60, 100, 120};
    private static final int INF = Settings.SHOTS_INFINITE;

    @Test
    public void ofTakesTheShootSettings() {
        Settings s = new Settings();
        s.interval = 2.5;
        s.shotCount = 40;
        s.delay = 3;
        s.brs = true;
        ShotPlan plan = ShotPlan.of(s);
        assertEquals(ShotPlan.BRACKET_FRAMES, plan.framesPerInterval());
        assertEquals(120, plan.totalFrames());
        assertEquals(100, plan.durationSeconds());
        assertEquals(180000, plan.delayMs());
        assertEquals(2500, plan.intervalMs());
        assertEquals(40, plan.shotCount());
        assertFalse(plan.isBurst());
        assertFalse(plan.isInfinite());

        s.brs = false;
        assertEquals(1, ShotPlan.of(s).framesPerInterval());
        assertEquals(40, ShotPlan.of(s).totalFrames());
    }

    @Test
    public void rejectsFewerThanOneFramePerInterval() {
        try {
            new ShotPlan(1, 10, 0, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void durationIsIntervalTimesShotsRoundedHalfUp() {
        assertEquals(2, new ShotPlan(0.5, 3, 0, 1).durationSeconds());
        assertEquals(1, new ShotPlan(0.5, 2, 0, 1).durationSeconds());
        assertEquals(11400000, new ShotPlan(2280, 5000, 0, 1).durationSeconds());
        assertEquals(30, new ShotPlan(1, 30, 0, ShotPlan.BRACKET_FRAMES).durationSeconds());
    }

    @Test
    public void clipLengthIsOneFramePerShot() {
        assertEquals(10, new ShotPlan(5, 240, 0, 1).clipSeconds(24));
        assertEquals(9, new ShotPlan(5, 239, 0, 1).clipSeconds(24));
        assertEquals(10, new ShotPlan(5, 240, 0, ShotPlan.BRACKET_FRAMES).clipSeconds(24));
        assertEquals(0, new ShotPlan(5, 1, 0, 1).clipSeconds(120));
        try {
            new ShotPlan(5, 240, 0, 1).clipSeconds(0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void burstModeCountsSecondsNotShots() {
        ShotPlan burst = new ShotPlan(0, 15, 0, 1);
        assertTrue(burst.isBurst());
        assertEquals(0, burst.intervalMs());
        assertEquals(15, burst.durationSeconds());
        assertEquals(-1, burst.clipSeconds(24));
        assertEquals(-1, burst.clipSeconds(0));
        assertEquals(1000 + 15000, burst.endMs(1000));
    }

    @Test
    public void infiniteRunsHaveNoEndAndDoNotOverflow() {
        ShotPlan plan = new ShotPlan(2, INF, 0, ShotPlan.BRACKET_FRAMES);
        assertTrue(plan.isInfinite());
        assertEquals(-1, plan.durationSeconds());
        assertEquals(-1, plan.clipSeconds(24));
        assertEquals(-1, plan.clipSeconds(0));
        assertEquals(Long.MAX_VALUE, plan.endMs(1000));
        assertEquals(3L * Integer.MAX_VALUE, plan.totalFrames());

        ShotPlan burst = new ShotPlan(0, INF, 0, 1);
        assertTrue(burst.isInfinite());
        assertEquals(-1, burst.durationSeconds());
        assertEquals(Long.MAX_VALUE, burst.endMs(1000));
    }

    @Test
    public void endTimeIsStartPlusDuration() {
        assertEquals(10000 + 25000, new ShotPlan(2.5, 10, 0, 1).endMs(10000));
        assertEquals(10000 + 1500, new ShotPlan(0.5, 3, 0, ShotPlan.BRACKET_FRAMES).endMs(10000));
    }

    @Test
    public void bracketSetsCountAsOneShot() {
        ShotPlan plan = new ShotPlan(5, 10, 0, ShotPlan.BRACKET_FRAMES);
        assertEquals(30, plan.totalFrames());
        assertEquals(50, plan.durationSeconds());
    }

    @Test
    public void ofAfterLoadUsesTheStoredSliderPositions() {
        MapSettingsStore store = new MapSettingsStore();
        store.values.put("interval", 75);
        store.values.put("shotCount", 35);
        store.values.put("delay", 16);
        store.values.put("brs", true);
        Settings settings = new Settings();
        settings.load(store);

        ShotPlan plan = ShotPlan.of(settings);
        assertEquals(12000, plan.durationSeconds());
        assertEquals(4, plan.clipSeconds(24));
        assertEquals(300, plan.totalFrames());
        assertEquals(3600000, plan.delayMs());
    }

    // Every interval x shots x fps position the settings screen offers (through the shipped
    // SliderMapping) gives the same duration and clip length as the pre-T05 inline math in
    // SettingsActivity.updateTimes.
    @Test
    public void settingsScreenMathMatchesTheOldInlineCode() {
        for (int rawInterval = 0; rawInterval <= LegacySliderMapping.INTERVAL_MAX; rawInterval++) {
            double interval = LegacySliderMapping.progressToInterval(rawInterval);
            for (int rawShots = 0; rawShots <= LegacySliderMapping.SHOTS_MAX; rawShots++) {
                int shots = LegacySliderMapping.progressToShotCount(rawShots);
                for (boolean brs : new boolean[]{false, true}) {
                    ShotPlan plan = new ShotPlan(interval, shots, 0, brs ? 3 : 1);
                    assertEquals(shots == Integer.MAX_VALUE, plan.isInfinite());
                    if (plan.isInfinite())
                        continue;
                    int legacyDuration = interval == 0 ? shots : (int) Math.round(interval * shots);
                    assertEquals(legacyDuration, plan.durationSeconds());
                    for (int fps : FPS) {
                        int legacyVideo = interval == 0 ? -1 : shots / fps;
                        assertEquals(legacyVideo, plan.clipSeconds(fps));
                    }
                }
            }
        }
    }

    @Test
    public void halfSecondIntervalPlansWithoutRoundingError() {
        Settings s = new Settings();
        s.intervalIndex = StepTables.INTERVAL_MS.nearestIndex(500);
        s.shotsIndex = StepTables.SHOTS.nearestIndex(100);
        s.deriveFromIndex();
        s.brs = false;
        ShotPlan plan = ShotPlan.of(s);
        assertEquals(0.5, s.interval, 0);
        assertEquals(500, plan.intervalMs());
        assertEquals(50, plan.durationSeconds());
        assertEquals(50000, plan.endMs(0));
        assertEquals(4, plan.clipSeconds(25));
    }
}
