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
        assertEquals(-1, burst.shotsRemaining(4));
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
        assertEquals(Integer.MAX_VALUE, plan.shotsRemaining(0));
        assertEquals(2L * Integer.MAX_VALUE, plan.secondsRemaining(1000, 1000, 0));

        ShotPlan burst = new ShotPlan(0, INF, 0, 1);
        assertTrue(burst.isInfinite());
        assertEquals(-1, burst.durationSeconds());
        assertEquals(Long.MAX_VALUE, burst.endMs(1000));
        assertEquals(Integer.MAX_VALUE, burst.secondsRemaining(1000, 1000, 1));
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
        assertEquals(10, plan.shotsRemaining(0));
        assertEquals(10, plan.shotsRemaining(2));
        assertEquals(9, plan.shotsRemaining(3));
        assertEquals(1, plan.shotsRemaining(29));
        assertEquals(0, plan.shotsRemaining(30));
        assertEquals(45, plan.secondsRemaining(1, 1, 3));
    }

    @Test
    public void delayCountdownRoundsUpAndStopsAtTheFirstFrame() {
        ShotPlan plan = new ShotPlan(5, 10, 2, 1);
        assertEquals(120000, plan.delayMs());
        assertEquals(0, plan.delaySecondsRemaining(0, 5000, 0));
        assertEquals(0, plan.delaySecondsRemaining(-1, 5000, 0));
        assertEquals(1, plan.delaySecondsRemaining(10000, 9999, 0));
        assertEquals(1, plan.delaySecondsRemaining(10000, 9000, 0));
        assertEquals(2, plan.delaySecondsRemaining(10000, 8999, 0));
        assertEquals(0, plan.delaySecondsRemaining(10000, 10000, 0));
        assertEquals(0, plan.delaySecondsRemaining(10000, 20000, 0));
        assertEquals(0, plan.delaySecondsRemaining(10000, 5000, 1));
    }

    @Test
    public void remainingTimeThroughADelayedRun() {
        FakeClock clock = new FakeClock(1000000);
        ShotPlan plan = new ShotPlan(5, 10, 2, 1);
        long start = clock.nowMs() + plan.delayMs();

        assertEquals(50 + 120, plan.secondsRemaining(start, clock.nowMs(), 0));
        clock.advance(30500);
        assertEquals(50 + 90, plan.secondsRemaining(start, clock.nowMs(), 0));
        clock.set(start);
        assertEquals(50, plan.secondsRemaining(start, clock.nowMs(), 0));
        clock.advance(20000);
        assertEquals(30, plan.secondsRemaining(start, clock.nowMs(), 4));
        assertEquals(0, plan.secondsRemaining(start, clock.nowMs(), 10));
    }

    @Test
    public void remainingTimeThroughABurst() {
        FakeClock clock = new FakeClock(500000);
        ShotPlan plan = new ShotPlan(0, 10, 0, 1);
        long start = clock.nowMs() + plan.delayMs();

        assertEquals(10, plan.secondsRemaining(start, clock.nowMs(), 0));
        clock.advance(2400);
        assertEquals(8, plan.secondsRemaining(start, clock.nowMs(), 1));
        clock.advance(100);
        assertEquals(8, plan.secondsRemaining(start, clock.nowMs(), 1));
        assertTrue(clock.nowMs() < plan.endMs(start));
        clock.set(start + 10000);
        assertEquals(0, plan.secondsRemaining(start, clock.nowMs(), 1));
        assertTrue(clock.nowMs() >= plan.endMs(start));
        clock.advance(2000);
        assertEquals(-2, plan.secondsRemaining(start, clock.nowMs(), 1));
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
        assertEquals(12000 + 3600, plan.secondsRemaining(1000 + plan.delayMs(), 1000, 0));
    }

    // Every interval x shots x fps position the settings screen offers (through the shipped
    // SliderMapping) gives the same duration and clip length as the pre-T05 inline math in
    // SettingsActivity.updateTimes.
    @Test
    public void settingsScreenMathMatchesTheOldInlineCode() {
        for (int rawInterval = 0; rawInterval <= SliderMapping.INTERVAL_MAX; rawInterval++) {
            double interval = SliderMapping.progressToInterval(rawInterval);
            for (int rawShots = 0; rawShots <= SliderMapping.SHOTS_MAX; rawShots++) {
                int shots = SliderMapping.progressToShotCount(rawShots);
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

    // Same check for ShootActivity: frame target, burst end, delay countdown and remaining time.
    @Test
    public void shootScreenMathMatchesTheOldInlineCode() {
        int[] rawIntervals = {0, 1, 2, 39, 40, 41, 58, 59, 60, 74, 75, 76, 91, 92, 93, 119};
        int[] rawShots = {0, 1, 19, 20, 35, 36, 80, 81, 99, 100, 129, 130};
        int[] delays = {0, 1, 5, 60, 1440};
        long[] offsets = {-200000, -60001, -1000, -999, -1, 0, 1, 999, 1000, 4321, 90000, 86400000};
        for (int ri : rawIntervals) {
            double interval = SliderMapping.progressToInterval(ri);
            for (int rs : rawShots) {
                int shots = SliderMapping.progressToShotCount(rs);
                for (int delay : delays) {
                    for (boolean brs : new boolean[]{false, true}) {
                        int cnt = brs ? 3 : 1;
                        ShotPlan plan = new ShotPlan(interval, shots, delay, cnt);
                        long now0 = 1700000000000L;
                        long shootStartTime = now0 + delay * 1000 * 60;
                        assertEquals(now0 + plan.delayMs(), shootStartTime);
                        assertEquals((long) shots * cnt, plan.totalFrames());
                        assertEquals(interval == 0, plan.isBurst());
                        for (long offset : offsets) {
                            long now = shootStartTime + offset;
                            if (interval == 0)
                                assertEquals(now >= shootStartTime + (long) shots * 1000, now >= plan.endMs(shootStartTime));
                            for (int taken : new int[]{0, 1, 2, 3, 4, 7}) {
                                assertEquals(legacyDelaySeconds(taken, shootStartTime, now),
                                        plan.delaySecondsRemaining(shootStartTime, now, taken));
                                assertEquals(legacyRemaining(interval, shots, cnt, taken, shootStartTime, now),
                                        plan.secondsRemaining(shootStartTime, now, taken));
                            }
                        }
                    }
                }
            }
        }
    }

    // Frozen copy of the removed pre-T05 ShootActivity.delaySecondsRemaining.
    private static long legacyDelaySeconds(int shotCount, long shootStartTime, long now) {
        if (shotCount != 0 || shootStartTime <= 0)
            return 0;
        long ms = shootStartTime - now;
        return ms > 0 ? (ms + 999) / 1000 : 0;
    }

    // Frozen copy of the removed pre-T05 ShootActivity.getRemainingTime.
    private static long legacyRemaining(double interval, int settingsShotCount, int cnt, int shotCount,
                                        long shootStartTime, long now) {
        if (interval == 0)
            return Math.round(((long) settingsShotCount * 1000 - now + shootStartTime) / 1000.0);
        long intervalsLeft = settingsShotCount - shotCount / cnt;
        return Math.round(intervalsLeft * interval) + legacyDelaySeconds(shotCount, shootStartTime, now);
    }
}
