package io.github.nathanrcast.timelapse.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** In-memory {@link SettingsStore} that, like SharedPreferences, only commits on apply(). */
public final class MapSettingsStore implements SettingsStore {
    public final Map<String, Object> values = new TreeMap<String, Object>();
    public int applies;

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Integer) value;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : (Boolean) value;
    }

    @Override
    public Editor edit() {
        final Map<String, Object> pending = new HashMap<String, Object>();
        return new Editor() {
            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public void apply() {
                values.putAll(pending);
                applies++;
            }
        };
    }
}
