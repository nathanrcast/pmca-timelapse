package io.github.nathanrcast.timelapse.core;

/**
 * Created by jonas on 2/18/17.
 */

// The shoot options. Interval, shots and delay are indices into StepTables (what the seek bars hold) and
// interval/shotCount/delay are the values derived from them. They persist as values under the *_VALUE keys;
// installs that only hold the old raw slider positions are migrated on load.
public final class Settings {
    public static final int SHOTS_INFINITE = Integer.MAX_VALUE;

    static final String KEY_INTERVAL_MS = "intervalMs";
    static final String KEY_SHOTS = "shots";
    static final String KEY_DELAY_MIN = "delayMin";
    // Pre-T10 raw slider positions. Read once by load() to migrate, never written.
    static final String LEGACY_KEY_INTERVAL = "interval";
    static final String LEGACY_KEY_SHOT_COUNT = "shotCount";
    static final String LEGACY_KEY_DELAY = "delay";
    private static final int ABSENT = -1;
    static final String KEY_SILENT_SHUTTER = "silentShutter";
    static final String KEY_AEL = "ael";
    static final String KEY_FPS = "fps";
    static final String KEY_BRS = "brs";
    static final String KEY_MF = "mf";
    static final String KEY_DISPLAY_OFF = "displayOff";
    static final String KEY_HOLY_GRAIL = "holyGrail";
    static final String KEY_USE_CURRENT_EXPOSURE = "useCurrentExposure";
    static final String KEY_TARGET_EXPOSURE = "targetExposure";
    static final String KEY_MAX_SHUTTER_SPEED_INDEX = "maxShutterSpeedIndex";
    static final String KEY_MAX_ISO_INDEX = "maxIsoIndex";
    static final String KEY_COOLDOWN = "cooldown";
    static final String KEY_AVERAGE_EXPOSURE_AMOUNT = "averageExposureAmount";
    static final String KEY_DEADBAND_INDEX = "deadbandIndex";
    static final String KEY_HOLY_GRAIL_ALLOW_EXPOSURE_UP = "holyGrailAllowExposureUp";
    static final String KEY_HOLY_GRAIL_ALLOW_EXPOSURE_DOWN = "holyGrailAllowExposureDown";

    public double interval;     // seconds; 0 = burst
    public int delay;           // minutes
    public int shotCount;       // burst: the burst length in seconds
    // Positions in StepTables.INTERVAL_MS / SHOTS / DELAY_MIN; call deriveFromIndex() after changing one.
    public int intervalIndex, shotsIndex, delayIndex;
    public boolean displayOff;
    public boolean silentShutter;
    public boolean ael;
    public int fps;    // index
    public boolean brs;
    public boolean mf;
    public boolean holyGrail;
    public boolean useCurrentExposure;
    public int targetExposure;
    public int maxShutterSpeedIndex;
    public int maxISOIndex;
    public int cooldown;
    public int averageExposureAmount;
    public int deadbandIndex;
    public boolean holyGrailAllowExposureUp;
    public boolean holyGrailAllowExposureDown;

    public Settings() {
        intervalIndex = StepTables.INTERVAL_MS.nearestIndex(1000);
        shotsIndex = StepTables.SHOTS.nearestIndex(10);
        delayIndex = 0;
        deriveFromIndex();
        displayOff = false;
        silentShutter = true;
        ael = true;
        fps = 0;
        brs = true;
        mf = true;
        holyGrail = false;
        useCurrentExposure = true;
        targetExposure = 0; //0
        maxShutterSpeedIndex = Util.DEFAULT_MAX_SHUTTER_INDEX;
        maxISOIndex = Util.DEFAULT_MAX_ISO_INDEX;
        cooldown = 5;
        averageExposureAmount = 1;
        deadbandIndex = 0;
        holyGrailAllowExposureUp = true;
        holyGrailAllowExposureDown = false;
    }

    public void save(SettingsStore store) {
        putSteps(store.edit())
                .putBoolean(KEY_SILENT_SHUTTER, silentShutter)
                .putBoolean(KEY_AEL, ael)
                .putInt(KEY_FPS, fps)
                .putBoolean(KEY_BRS, brs)
                .putBoolean(KEY_MF, mf)
                .putBoolean(KEY_DISPLAY_OFF, displayOff)
                .putBoolean(KEY_HOLY_GRAIL, holyGrail)
                .putBoolean(KEY_USE_CURRENT_EXPOSURE, useCurrentExposure)
                .putInt(KEY_TARGET_EXPOSURE, targetExposure)
                .putInt(KEY_MAX_SHUTTER_SPEED_INDEX, maxShutterSpeedIndex)
                .putInt(KEY_MAX_ISO_INDEX, maxISOIndex)
                .putInt(KEY_COOLDOWN, cooldown)
                .putInt(KEY_AVERAGE_EXPOSURE_AMOUNT, averageExposureAmount)
                .putInt(KEY_DEADBAND_INDEX, deadbandIndex)
                .putBoolean(KEY_HOLY_GRAIL_ALLOW_EXPOSURE_UP, holyGrailAllowExposureUp)
                .putBoolean(KEY_HOLY_GRAIL_ALLOW_EXPOSURE_DOWN, holyGrailAllowExposureDown)
                .apply();
    }

    // Missing keys keep the current values.
    public void load(SettingsStore store) {
        boolean migrated = loadSteps(store);
        silentShutter = store.getBoolean(KEY_SILENT_SHUTTER, silentShutter);
        ael = store.getBoolean(KEY_AEL, ael);
        fps = store.getInt(KEY_FPS, fps);
        brs = store.getBoolean(KEY_BRS, brs);
        mf = store.getBoolean(KEY_MF, mf);
        displayOff = store.getBoolean(KEY_DISPLAY_OFF, displayOff);
        holyGrail = store.getBoolean(KEY_HOLY_GRAIL, holyGrail);
        useCurrentExposure = store.getBoolean(KEY_USE_CURRENT_EXPOSURE, useCurrentExposure);
        targetExposure = store.getInt(KEY_TARGET_EXPOSURE, targetExposure);
        maxShutterSpeedIndex = store.getInt(KEY_MAX_SHUTTER_SPEED_INDEX, maxShutterSpeedIndex);
        maxISOIndex = store.getInt(KEY_MAX_ISO_INDEX, maxISOIndex);
        cooldown = store.getInt(KEY_COOLDOWN, cooldown);
        averageExposureAmount = store.getInt(KEY_AVERAGE_EXPOSURE_AMOUNT, averageExposureAmount);
        deadbandIndex = store.getInt(KEY_DEADBAND_INDEX, deadbandIndex);
        holyGrailAllowExposureUp = store.getBoolean(KEY_HOLY_GRAIL_ALLOW_EXPOSURE_UP, holyGrailAllowExposureUp);
        holyGrailAllowExposureDown = store.getBoolean(KEY_HOLY_GRAIL_ALLOW_EXPOSURE_DOWN, holyGrailAllowExposureDown);
        clamp();
        deriveFromIndex();
        if (migrated)
            saveSteps(store);
    }

    // Each of the three comes from its value key, else from the old raw position (converted with the old mapping and
    // snapped to the nearest table entry), else keeps the current one. True if any came from an old key.
    private boolean loadSteps(SettingsStore store) {
        boolean migrated = false;
        int ms = store.getInt(KEY_INTERVAL_MS, ABSENT);
        int legacy = store.getInt(LEGACY_KEY_INTERVAL, ABSENT);
        if (ms != ABSENT) {
            intervalIndex = StepTables.INTERVAL_MS.nearestIndex(ms);
        } else if (legacy != ABSENT) {
            long legacyMs = Math.round(LegacySliderMapping.progressToInterval(legacy) * 1000);
            intervalIndex = StepTables.INTERVAL_MS.nearestIndex(legacyMs);
            migrated = true;
        }
        int shots = store.getInt(KEY_SHOTS, ABSENT);
        legacy = store.getInt(LEGACY_KEY_SHOT_COUNT, ABSENT);
        if (shots != ABSENT) {
            shotsIndex = StepTables.SHOTS.nearestIndex(shots);
        } else if (legacy != ABSENT) {
            shotsIndex = StepTables.SHOTS.nearestIndex(LegacySliderMapping.progressToShotCount(legacy));
            migrated = true;
        }
        int min = store.getInt(KEY_DELAY_MIN, ABSENT);
        legacy = store.getInt(LEGACY_KEY_DELAY, ABSENT);
        if (min != ABSENT) {
            delayIndex = StepTables.DELAY_MIN.nearestIndex(min);
        } else if (legacy != ABSENT) {
            delayIndex = StepTables.DELAY_MIN.nearestIndex(LegacySliderMapping.progressToDelay(legacy));
            migrated = true;
        }
        return migrated;
    }

    private void saveSteps(SettingsStore store) {
        putSteps(store.edit()).apply();
    }

    private SettingsStore.Editor putSteps(SettingsStore.Editor editor) {
        return editor
                .putInt(KEY_INTERVAL_MS, (int) StepTables.INTERVAL_MS.value(intervalIndex))
                .putInt(KEY_SHOTS, (int) StepTables.SHOTS.value(shotsIndex))
                .putInt(KEY_DELAY_MIN, (int) StepTables.DELAY_MIN.value(delayIndex));
    }

    public void deriveFromIndex() {
        interval = StepTables.INTERVAL_MS.value(intervalIndex) / 1000.0;
        shotCount = (int) StepTables.SHOTS.value(shotsIndex);
        delay = (int) StepTables.DELAY_MIN.value(delayIndex);
    }

    // Pulls the persisted non-table values into the ranges their sliders and step tables can show. Every value
    // the settings screen writes is already in range, so this only changes corrupt stores. The interval, shots and
    // delay indices come from nearestIndex() and are always in range.
    public void clamp() {
        int halfTarget = SliderMapping.TARGET_EXPOSURE_MAX / 2;
        targetExposure = SliderMapping.clamp(targetExposure, -halfTarget, halfTarget);
        maxShutterSpeedIndex = SliderMapping.clamp(maxShutterSpeedIndex, 0, Util.SHUTTER_SPEEDS.length - 1);
        maxISOIndex = SliderMapping.clamp(maxISOIndex, 0, Util.ISO_VALUES.length - 1);
        cooldown = SliderMapping.clamp(cooldown, 1, SliderMapping.COUNT_MAX + 1);
        averageExposureAmount = SliderMapping.clamp(averageExposureAmount, 1, SliderMapping.COUNT_MAX + 1);
        deadbandIndex = SliderMapping.clamp(deadbandIndex, 0, Util.DEADBAND_VALUES.length - 1);
    }
}
