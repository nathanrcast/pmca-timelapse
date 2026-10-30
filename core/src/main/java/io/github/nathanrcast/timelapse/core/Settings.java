package io.github.nathanrcast.timelapse.core;

/**
 * Created by jonas on 2/18/17.
 */

// The shoot options. Only the raw slider positions are persisted; SettingsActivity derives
// interval, shotCount and delay from them. The keys and encodings are what existing installs hold.
public final class Settings {
    public static final int SHOTS_INFINITE = Integer.MAX_VALUE;

    public static final int RAW_INTERVAL_MAX = 119;
    public static final int RAW_SHOT_COUNT_MAX = 130;
    public static final int RAW_DELAY_MAX = 39;

    static final String KEY_INTERVAL = "interval";
    static final String KEY_SHOT_COUNT = "shotCount";
    static final String KEY_DELAY = "delay";
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
    public int rawInterval, rawDelay;
    public int shotCount, rawShotCount;     // burst: shotCount is the burst length in seconds
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
        interval = 1;
        rawInterval = 1;
        delay = 0;
        rawDelay = 0;
        shotCount = 1;
        rawShotCount = 1;
        displayOff = false;
        silentShutter = true;
        ael = true;
        fps = 0;
        brs = true;
        mf = true;
        holyGrail = false;
        useCurrentExposure = true;
        targetExposure = 0; //0
        maxShutterSpeedIndex = 0;
        maxISOIndex = 0;
        cooldown = 5;
        averageExposureAmount = 1;
        deadbandIndex = 0;
        holyGrailAllowExposureUp = true;
        holyGrailAllowExposureDown = false;
    }

    public void save(SettingsStore store) {
        store.edit()
                .putInt(KEY_INTERVAL, rawInterval)
                .putInt(KEY_SHOT_COUNT, rawShotCount)
                .putInt(KEY_DELAY, rawDelay)
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
        rawInterval = store.getInt(KEY_INTERVAL, rawInterval);
        rawShotCount = store.getInt(KEY_SHOT_COUNT, rawShotCount);
        rawDelay = store.getInt(KEY_DELAY, rawDelay);
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
    }

    // Pulls persisted values into the ranges the sliders and step tables can show. Every value
    // the settings screen writes is already in range, so this only changes corrupt stores.
    public void clamp() {
        rawInterval = SliderMapping.clamp(rawInterval, 0, RAW_INTERVAL_MAX);
        rawShotCount = SliderMapping.clamp(rawShotCount, 0, RAW_SHOT_COUNT_MAX);
        rawDelay = SliderMapping.clamp(rawDelay, 0, RAW_DELAY_MAX);
        int halfTarget = SliderMapping.TARGET_EXPOSURE_MAX / 2;
        targetExposure = SliderMapping.clamp(targetExposure, -halfTarget, halfTarget);
        maxShutterSpeedIndex = SliderMapping.clamp(maxShutterSpeedIndex, 0, Util.SHUTTER_SPEEDS.length - 1);
        maxISOIndex = SliderMapping.clamp(maxISOIndex, 0, Util.ISO_VALUES.length - 1);
        cooldown = SliderMapping.clamp(cooldown, 1, SliderMapping.COUNT_MAX + 1);
        averageExposureAmount = SliderMapping.clamp(averageExposureAmount, 1, SliderMapping.COUNT_MAX + 1);
        deadbandIndex = SliderMapping.clamp(deadbandIndex, 0, Util.DEADBAND_VALUES.length - 1);
    }
}
