package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// Installs before T10 hold raw slider positions under "interval", "shotCount" and "delay".
public class SettingsMigrationTest {
    private static Settings loadLegacy(int interval, int shotCount, int delay, MapSettingsStore store) {
        store.values.put("interval", interval);
        store.values.put("shotCount", shotCount);
        store.values.put("delay", delay);
        Settings s = new Settings();
        s.load(store);
        return s;
    }

    @Test
    public void oldPositionsConvertAndSnapToTheTables() {
        MapSettingsStore store = new MapSettingsStore();
        // 5 -> 3.0 s, 21 -> 30 shots, 3 -> 3 min: all exactly in the new tables
        Settings s = loadLegacy(5, 21, 3, store);
        assertEquals(3.0, s.interval, 0);
        assertEquals(30, s.shotCount);
        assertEquals(3, s.delay);
    }

    @Test
    public void valuesBetweenEntriesSnapToTheNearest() {
        MapSettingsStore store = new MapSettingsStore();
        // 1.5 s sits between 1 and 2 (tie, lower); 120 shots -> 100 (nearer than 150); 2 h is in the table
        Settings s = loadLegacy(2, 36, 17, store);
        assertEquals(1.0, s.interval, 0);
        assertEquals(100, s.shotCount);
        assertEquals(120, s.delay);
    }

    @Test
    public void endsMapToBurstInfiniteAndTheLongestEntries() {
        MapSettingsStore store = new MapSettingsStore();
        Settings s = loadLegacy(0, LegacySliderMapping.SHOTS_MAX, 0, store);
        assertEquals(0.0, s.interval, 0);
        assertEquals(Settings.SHOTS_INFINITE, s.shotCount);

        store = new MapSettingsStore();
        s = loadLegacy(LegacySliderMapping.INTERVAL_MAX, 0, LegacySliderMapping.DELAY_MAX, store);
        assertEquals(2400.0, s.interval, 0);
        assertEquals(1, s.shotCount);
        assertEquals(1440, s.delay);
    }

    // Every old position lands on the nearest table entry, exact where the table has the value, never clamped.
    @Test
    public void everyOldPositionLandsOnTheNearestEntry() {
        for (int p = 0; p <= LegacySliderMapping.INTERVAL_MAX; p++) {
            MapSettingsStore store = new MapSettingsStore();
            store.values.put("interval", p);
            Settings s = new Settings();
            s.load(store);
            long old = Math.round(LegacySliderMapping.progressToInterval(p) * 1000);
            assertNearest("interval " + p, StepTables.INTERVAL_MS, old, Math.round(s.interval * 1000));
        }
        for (int p = 0; p <= LegacySliderMapping.SHOTS_MAX; p++) {
            MapSettingsStore store = new MapSettingsStore();
            store.values.put("shotCount", p);
            Settings s = new Settings();
            s.load(store);
            assertNearest("shots " + p, StepTables.SHOTS, LegacySliderMapping.progressToShotCount(p), s.shotCount);
        }
        for (int p = 0; p <= LegacySliderMapping.DELAY_MAX; p++) {
            MapSettingsStore store = new MapSettingsStore();
            store.values.put("delay", p);
            Settings s = new Settings();
            s.load(store);
            assertNearest("delay " + p, StepTables.DELAY_MIN, LegacySliderMapping.progressToDelay(p), s.delay);
        }
    }

    private static void assertNearest(String what, StepTable table, long old, long migrated) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < table.size(); i++)
            best = Math.min(best, Math.abs(table.value(i) - old));
        assertEquals(what, best, Math.abs(migrated - old));
        assertEquals(what, migrated, table.value(table.nearestIndex(migrated)));
    }

    @Test
    public void migrationWritesTheNewKeysAndLeavesTheOldOnes() {
        MapSettingsStore store = new MapSettingsStore();
        loadLegacy(5, 21, 3, store);
        assertEquals(3000, store.values.get("intervalMs"));
        assertEquals(30, store.values.get("shots"));
        assertEquals(3, store.values.get("delayMin"));
        assertEquals(5, store.values.get("interval"));
        assertEquals(21, store.values.get("shotCount"));
        assertEquals(3, store.values.get("delay"));
        assertEquals(1, store.applies);
    }

    @Test
    public void roundTripAfterMigrationIsStable() {
        MapSettingsStore store = new MapSettingsStore();
        Settings first = loadLegacy(40, 50, 8, store);
        Settings second = new Settings();
        second.load(store);
        assertEquals(first.interval, second.interval, 0);
        assertEquals(first.shotCount, second.shotCount);
        assertEquals(first.delay, second.delay);
        assertEquals(1, store.applies);

        MapSettingsStore saved = new MapSettingsStore();
        second.save(saved);
        assertEquals(store.values.get("intervalMs"), saved.values.get("intervalMs"));
        assertEquals(store.values.get("shots"), saved.values.get("shots"));
        assertEquals(store.values.get("delayMin"), saved.values.get("delayMin"));
    }

    @Test
    public void newKeysWinOverOldOnes() {
        MapSettingsStore store = new MapSettingsStore();
        store.values.put("interval", 0);
        store.values.put("shotCount", 0);
        store.values.put("delay", 0);
        store.values.put("intervalMs", 8000);
        store.values.put("shots", 500);
        store.values.put("delayMin", 20);
        Settings s = new Settings();
        s.load(store);
        assertEquals(8.0, s.interval, 0);
        assertEquals(500, s.shotCount);
        assertEquals(20, s.delay);
        assertEquals(0, store.applies);
    }

    @Test
    public void aPartialStoreMigratesOnlyWhatItHolds() {
        MapSettingsStore store = new MapSettingsStore();
        store.values.put("interval", 5);
        store.values.put("shots", 200);
        Settings s = new Settings();
        s.load(store);
        assertEquals(3.0, s.interval, 0);
        assertEquals(200, s.shotCount);
        assertEquals(0, s.delay);
    }

    @Test
    public void corruptOldPositionsClampBeforeSnapping() {
        MapSettingsStore store = new MapSettingsStore();
        Settings s = loadLegacy(500, -3, 99, store);
        assertEquals(2400.0, s.interval, 0);
        assertEquals(1, s.shotCount);
        assertEquals(1440, s.delay);
    }

    @Test
    public void aFreshInstallWritesNothing() {
        MapSettingsStore store = new MapSettingsStore();
        new Settings().load(store);
        assertEquals(0, store.applies);
        assertFalse(store.values.containsKey("intervalMs"));
        assertNull(store.values.get("shots"));
    }
}
