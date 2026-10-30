package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

public class SettingsTest {
    @Test
    public void defaults() {
        Settings s = new Settings();
        assertEquals(1.0, s.interval, 0);
        assertEquals(1, s.rawInterval);
        assertEquals(0, s.delay);
        assertEquals(0, s.rawDelay);
        assertEquals(1, s.shotCount);
        assertEquals(1, s.rawShotCount);
        assertFalse(s.displayOff);
        assertTrue(s.silentShutter);
        assertTrue(s.ael);
        assertEquals(0, s.fps);
        assertTrue(s.brs);
        assertTrue(s.mf);
        assertFalse(s.holyGrail);
        assertTrue(s.useCurrentExposure);
        assertEquals(0, s.targetExposure);
        assertEquals(0, s.maxShutterSpeedIndex);
        assertEquals(0, s.maxISOIndex);
        assertEquals(5, s.cooldown);
        assertEquals(1, s.averageExposureAmount);
        assertEquals(0, s.deadbandIndex);
        assertTrue(s.holyGrailAllowExposureUp);
        assertFalse(s.holyGrailAllowExposureDown);
    }

    // Existing installs hold these exact keys and types; renaming one silently resets that setting.
    @Test
    public void saveWritesTheExistingKeysAndEncodings() {
        Settings s = new Settings();
        s.rawInterval = 7;
        s.interval = 4.0;
        s.rawShotCount = 21;
        s.shotCount = 25;
        s.rawDelay = 3;
        s.delay = 3;
        s.silentShutter = false;
        s.ael = false;
        s.fps = 2;
        s.brs = false;
        s.mf = false;
        s.displayOff = true;
        s.holyGrail = true;
        s.useCurrentExposure = false;
        s.targetExposure = -6;
        s.maxShutterSpeedIndex = 40;
        s.maxISOIndex = 12;
        s.cooldown = 9;
        s.averageExposureAmount = 3;
        s.deadbandIndex = 4;
        s.holyGrailAllowExposureUp = false;
        s.holyGrailAllowExposureDown = true;

        MapSettingsStore store = new MapSettingsStore();
        s.save(store);

        Map<String, Object> expected = new TreeMap<String, Object>();
        expected.put("interval", 7);
        expected.put("shotCount", 21);
        expected.put("delay", 3);
        expected.put("silentShutter", false);
        expected.put("ael", false);
        expected.put("fps", 2);
        expected.put("brs", false);
        expected.put("mf", false);
        expected.put("displayOff", true);
        expected.put("holyGrail", true);
        expected.put("useCurrentExposure", false);
        expected.put("targetExposure", -6);
        expected.put("maxShutterSpeedIndex", 40);
        expected.put("maxIsoIndex", 12);
        expected.put("cooldown", 9);
        expected.put("averageExposureAmount", 3);
        expected.put("deadbandIndex", 4);
        expected.put("holyGrailAllowExposureUp", false);
        expected.put("holyGrailAllowExposureDown", true);
        assertEquals(expected, store.values);
        assertEquals(1, store.applies);
    }

    @Test
    public void loadRestoresWhatSaveWrote() {
        Settings saved = new Settings();
        saved.rawInterval = SliderMapping.INTERVAL_MAX;
        saved.rawShotCount = SliderMapping.SHOTS_MAX;
        saved.rawDelay = SliderMapping.DELAY_MAX;
        saved.fps = 6;
        saved.brs = false;
        saved.holyGrail = true;
        saved.targetExposure = 30;
        saved.maxShutterSpeedIndex = Util.SHUTTER_SPEEDS.length - 1;
        saved.maxISOIndex = Util.ISO_VALUES.length - 1;
        saved.cooldown = SliderMapping.COUNT_MAX + 1;
        saved.averageExposureAmount = 1;
        saved.deadbandIndex = Util.DEADBAND_VALUES.length - 1;
        saved.holyGrailAllowExposureDown = true;
        MapSettingsStore store = new MapSettingsStore();
        saved.save(store);

        Settings loaded = new Settings();
        loaded.load(store);
        MapSettingsStore again = new MapSettingsStore();
        loaded.save(again);
        assertEquals(store.values, again.values);
    }

    @Test
    public void loadFromAnEmptyStoreKeepsTheDefaults() {
        MapSettingsStore defaults = new MapSettingsStore();
        new Settings().save(defaults);

        Settings loaded = new Settings();
        loaded.load(new MapSettingsStore());
        MapSettingsStore after = new MapSettingsStore();
        loaded.save(after);
        assertEquals(defaults.values, after.values);
    }

    @Test
    public void clampLeavesEveryValueTheScreenCanWrite() {
        Settings s = new Settings();
        for (int raw = 0; raw <= SliderMapping.INTERVAL_MAX; raw++) {
            s.rawInterval = raw;
            s.clamp();
            assertEquals(raw, s.rawInterval);
        }
        for (int raw = 0; raw <= SliderMapping.SHOTS_MAX; raw++) {
            s.rawShotCount = raw;
            s.clamp();
            assertEquals(raw, s.rawShotCount);
        }
        for (int raw = 0; raw <= SliderMapping.DELAY_MAX; raw++) {
            s.rawDelay = raw;
            s.clamp();
            assertEquals(raw, s.rawDelay);
        }
        for (int p = 0; p <= SliderMapping.TARGET_EXPOSURE_MAX; p++) {
            s.targetExposure = SliderMapping.progressToTargetExposure(p);
            s.clamp();
            assertEquals(SliderMapping.progressToTargetExposure(p), s.targetExposure);
        }
        for (int p = 0; p <= SliderMapping.COUNT_MAX; p++) {
            s.cooldown = SliderMapping.progressToCount(p);
            s.averageExposureAmount = SliderMapping.progressToCount(p);
            s.clamp();
            assertEquals(SliderMapping.progressToCount(p), s.cooldown);
            assertEquals(SliderMapping.progressToCount(p), s.averageExposureAmount);
        }
        for (int i = 0; i < Util.SHUTTER_SPEEDS.length; i++) {
            s.maxShutterSpeedIndex = i;
            s.clamp();
            assertEquals(i, s.maxShutterSpeedIndex);
        }
        for (int i = 0; i < Util.ISO_VALUES.length; i++) {
            s.maxISOIndex = i;
            s.clamp();
            assertEquals(i, s.maxISOIndex);
        }
        for (int i = 0; i < Util.DEADBAND_VALUES.length; i++) {
            s.deadbandIndex = i;
            s.clamp();
            assertEquals(i, s.deadbandIndex);
        }
    }

    @Test
    public void loadClampsCorruptValuesIntoRange() {
        MapSettingsStore store = new MapSettingsStore();
        store.values.put("interval", 500);
        store.values.put("shotCount", -3);
        store.values.put("delay", 40);
        store.values.put("targetExposure", -90);
        store.values.put("maxShutterSpeedIndex", 99);
        store.values.put("maxIsoIndex", -1);
        store.values.put("cooldown", 0);
        store.values.put("averageExposureAmount", 1000);
        store.values.put("deadbandIndex", 11);

        Settings s = new Settings();
        s.load(store);
        assertEquals(SliderMapping.INTERVAL_MAX, s.rawInterval);
        assertEquals(0, s.rawShotCount);
        assertEquals(SliderMapping.DELAY_MAX, s.rawDelay);
        assertEquals(-30, s.targetExposure);
        assertEquals(Util.SHUTTER_SPEEDS.length - 1, s.maxShutterSpeedIndex);
        assertEquals(0, s.maxISOIndex);
        assertEquals(1, s.cooldown);
        assertEquals(SliderMapping.COUNT_MAX + 1, s.averageExposureAmount);
        assertEquals(Util.DEADBAND_VALUES.length - 1, s.deadbandIndex);
        assertEquals(2280.0, s.interval, 0);
        assertEquals(1, s.shotCount);
        assertEquals(1440, s.delay);
    }

    @Test
    public void loadDerivesIntervalShotsAndDelayFromTheSliderPositions() {
        MapSettingsStore store = new MapSettingsStore();
        store.values.put("interval", 0);
        store.values.put("shotCount", SliderMapping.SHOTS_MAX);
        store.values.put("delay", 6);
        Settings s = new Settings();
        s.load(store);
        assertEquals(0.0, s.interval, 0);
        assertEquals(Settings.SHOTS_INFINITE, s.shotCount);
        assertEquals(10, s.delay);

        s.rawInterval = 92;
        s.rawShotCount = 36;
        s.rawDelay = 39;
        s.deriveFromRaw();
        assertEquals(660.0, s.interval, 0);
        assertEquals(120, s.shotCount);
        assertEquals(1440, s.delay);
    }
}
