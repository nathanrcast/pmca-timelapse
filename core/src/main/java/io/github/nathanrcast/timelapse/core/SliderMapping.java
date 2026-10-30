package io.github.nathanrcast.timelapse.core;

// Seek bar position <-> stored setting for the HG sliders whose stored value isn't the position.
// Load and save both go through here, so a load->save round trip is the identity.
public final class SliderMapping {
    public static final int TARGET_EXPOSURE_MAX = 60;
    public static final int COUNT_MAX = 100;

    private SliderMapping() {}

    // Target exposure is stored in 1/3-EV steps, -30..30.
    public static int targetExposureToProgress(int targetExposure) {
        return clamp(targetExposure + TARGET_EXPOSURE_MAX / 2, TARGET_EXPOSURE_MAX);
    }

    public static int progressToTargetExposure(int progress) {
        return progress - TARGET_EXPOSURE_MAX / 2;
    }

    // Cooldown and average amount are stored as counts, 1..COUNT_MAX + 1.
    public static int countToProgress(int count) {
        return clamp(count - 1, COUNT_MAX);
    }

    public static int progressToCount(int progress) {
        return progress + 1;
    }

    private static int clamp(int progress, int max) {
        return Math.max(0, Math.min(max, progress));
    }
}
