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

    // A key written with another type (an older or forked build) reads as unset instead of crashing.
    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return prefs.getInt(key, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
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
