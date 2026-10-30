package io.github.nathanrcast.timelapse.core;

// Seek bar position <-> setting value, and the labels for them.
// Interval, shots and delay seek bars are indices into StepTables; this class only formats their values.
// The HG sliders persist the value; load and save both go through here, so a load->save
// round trip is the identity.
public final class SliderMapping {
    public static final int TARGET_EXPOSURE_MAX = 60;
    public static final int COUNT_MAX = 100;

    private SliderMapping() {}

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

    public static String shotCountText(int shotCount) {
        return shotCount == Settings.SHOTS_INFINITE ? "inf" : Integer.toString(shotCount);
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
