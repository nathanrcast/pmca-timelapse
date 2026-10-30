package io.github.nathanrcast.timelapse.core;

// The pre-T10 seek-bar mappings. Interval, shots and delay used to persist the raw slider position under
// "interval", "shotCount" and "delay"; this only converts those positions to values for the one-time migration
// (Settings.load). Frozen by LegacySliderMappingTest.
public final class LegacySliderMapping {
    public static final int INTERVAL_MAX = 119;
    public static final int SHOTS_MAX = 130;
    public static final int DELAY_MAX = 39;

    private LegacySliderMapping() {}

    // Seconds: burst (0), 1-20 by 0.5, 21-39 by 1, 40-115 by 5, 120-600 by 30, 660-2280 by 60.
    public static double progressToInterval(int progress) {
        int i = clamp(progress, 0, INTERVAL_MAX) + 1;
        if (i == 1)
            return 0;
        if (i < 41)
            return i * 0.5;
        if (i < 60)
            return (i - 40) + 20;
        if (i < 76)
            return (i - 60) * 5 + 40;
        if (i < 93)
            return (i - 76) * 30 + 120;
        return (i - 93) * 60 + 660;
    }

    // 1-20 by 1, 25-100 by 5, 120-1000 by 20, 1050-1950 by 50, 2100-5000 by 100, then infinite.
    public static int progressToShotCount(int progress) {
        int i = clamp(progress, 0, SHOTS_MAX) + 1;
        if (i <= 20)
            return i;
        if (i <= 36)
            return (i - 20) * 5 + 20;
        if (i <= 81)
            return (i - 36) * 20 + 100;
        if (i <= 100)
            return (i - 81) * 50 + 1000;
        if (i <= 130)
            return (i - 100) * 100 + 2000;
        return Settings.SHOTS_INFINITE;
    }

    // Minutes: 0-5 by 1, 10-55 by 5, then 1-24 h by 1 h.
    public static int progressToDelay(int progress) {
        int i = clamp(progress, 0, DELAY_MAX);
        if (i < 6)
            return i;
        if (i < 16)
            return (i - 5) * 5 + 5;
        return (i - 15) * 60;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
