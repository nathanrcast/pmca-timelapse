package io.github.nathanrcast.timelapse.core;

/** Primitive key-value persistence for {@link Settings}; the app backs it with SharedPreferences. */
public interface SettingsStore {
    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    Editor edit();

    /** Batches writes until {@link #apply()}. */
    interface Editor {
        Editor putInt(String key, int value);

        Editor putBoolean(String key, boolean value);

        void apply();
    }
}
