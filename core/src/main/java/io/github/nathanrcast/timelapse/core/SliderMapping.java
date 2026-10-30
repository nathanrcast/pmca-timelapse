package io.github.nathanrcast.timelapse.core;

// Seek bar position <-> setting value.
// Interval, shots and delay persist the position and derive the value from it here.
// The HG sliders persist the value; load and save both go through here, so a load->save
// round trip is the identity.
public final class SliderMapping {
    public static final int INTERVAL_MAX = 119;
    public static final int SHOTS_MAX = 130;
    public static final int DELAY_MAX = 39;
    public static final int TARGET_EXPOSURE_MAX = 60;
    public static final int COUNT_MAX = 100;

    private SliderMapping() {}

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

    public static String intervalText(double interval) {
        if (interval == 0)
            return "burst";
        if (interval < 120)
            return Double.toString(interval);
        if (interval < 660)
            return Double.toString(interval / 60);
        return Integer.toString((int) (interval / 60));
    }

    public static String intervalUnit(double interval) {
        if (interval == 0)
            return "";
        return interval < 120 ? "s" : "min";
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

    public static String shotCountText(int shotCount) {
        return shotCount == Settings.SHOTS_INFINITE ? "inf" : Integer.toString(shotCount);
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

    public static String delayText(int delay) {
        return Double.toString(delay < 60 ? delay : delay / 60);
    }

    public static String delayUnit(int delay) {
        return delay < 60 ? "min" : "h";
    }

    // Target exposure is stored in 1/3-EV steps, -30..30.
    public static int targetExposureToProgress(int targetExposure) {
        return clamp(targetExposure + TARGET_EXPOSURE_MAX / 2, 0, TARGET_EXPOSURE_MAX);
    }

    public static int progressToTargetExposure(int progress) {
        return progress - TARGET_EXPOSURE_MAX / 2;
    }

    // Cooldown and average amount are stored as counts, 1..COUNT_MAX + 1.
    public static int countToProgress(int count) {
        return clamp(count - 1, 0, COUNT_MAX);
    }

    public static int progressToCount(int progress) {
        return progress + 1;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
