package io.github.nathanrcast.timelapse;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.github.nathanrcast.timelapse.core.SettingsStore;

final class SharedPreferencesSettingsStore implements SettingsStore {
    private final SharedPreferences prefs;

    SharedPreferencesSettingsStore(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    @Override
    public Editor edit() {
        final SharedPreferences.Editor editor = prefs.edit();
        return new Editor() {
            @Override
            public Editor putInt(String key, int value) {
                editor.putInt(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                editor.putBoolean(key, value);
                return this;
            }

            @Override
            public void apply() {
                editor.apply();
            }
        };
    }
}
