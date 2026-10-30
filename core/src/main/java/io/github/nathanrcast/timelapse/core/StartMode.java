package io.github.nathanrcast.timelapse.core;

// When the first shot is due: right away, after the delay setting, or at a wall-clock time. Persisted as the ordinal.
public enum StartMode {
    NOW, DELAY, AT;

    public static StartMode fromOrdinal(int ordinal, StartMode fallback) {
        StartMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : fallback;
    }
}
