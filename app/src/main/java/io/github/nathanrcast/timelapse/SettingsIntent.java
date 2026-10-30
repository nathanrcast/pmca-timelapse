package io.github.nathanrcast.timelapse;

import android.content.Intent;

import io.github.nathanrcast.timelapse.core.Settings;

// Hands the derived shoot settings from SettingsActivity to ShootActivity.
final class SettingsIntent {
    private static final String EXTRA_INTERVAL = "io.github.nathanrcast.timelapse.INTERVAL";
    private static final String EXTRA_SHOTCOUNT = "io.github.nathanrcast.timelapse.SHOTCOUNT";
    private static final String EXTRA_DELAY = "io.github.nathanrcast.timelapse.DELAY";
    private static final String EXTRA_DISPLAYOFF = "io.github.nathanrcast.timelapse.DISPLAYOFF";
    private static final String EXTRA_REVIEWSECONDS = "io.github.nathanrcast.timelapse.REVIEWSECONDS";
    private static final String EXTRA_SILENTSHUTTER = "io.github.nathanrcast.timelapse.SILENTSHUTTER";
    private static final String EXTRA_AEL = "io.github.nathanrcast.timelapse.AEL";
    private static final String EXTRA_BRS = "io.github.nathanrcast.timelapse.BRS";
    private static final String EXTRA_MF = "io.github.nathanrcast.timelapse.MF";
    private static final String EXTRA_HOLYGRAIL = "io.github.nathanrcast.timelapse.HOLYGRAIL";
    private static final String EXTRA_USECURRENTEXPOSURE = "io.github.nathanrcast.timelapse.USECURRENTEXPOSURE";
    private static final String EXTRA_TARGETEXPOSURE = "io.github.nathanrcast.timelapse.TARGETEXPOSURE";
    private static final String EXTRA_MAXSHUTTERSPEEDINDEX = "io.github.nathanrcast.timelapse.MAXSHUTTERSPEEDINDEX";
    private static final String EXTRA_MAXISOINDEX = "io.github.nathanrcast.timelapse.MAXISOINDEX";
    private static final String EXTRA_COOLDOWN = "io.github.nathanrcast.timelapse.COOLDOWN";
    private static final String EXTRA_AVERAGEEXPOSUREAMOUNT = "io.github.nathanrcast.timelapse.AVERAGEEXPOSUREAMOUNT";
    private static final String EXTRA_DEADBANDINDEX = "io.github.nathanrcast.timelapse.DEADBANDINDEX";
    private static final String EXTRA_HOLYGRAILALLOWEXPOSUREUP = "io.github.nathanrcast.timelapse.HOLYGRAILALLOWEXPOSUREUP";
    private static final String EXTRA_HOLYGRAILALLOWEXPOSUREDOWN = "io.github.nathanrcast.timelapse.HOLYGRAILALLOWEXPOSUREDOWN";

    private SettingsIntent() {}

    static void put(Intent intent, Settings settings) {
        intent.putExtra(EXTRA_INTERVAL, settings.interval);
        intent.putExtra(EXTRA_SHOTCOUNT, settings.shotCount);
        intent.putExtra(EXTRA_DELAY, settings.delay);
        intent.putExtra(EXTRA_DISPLAYOFF, settings.displayOff);
        intent.putExtra(EXTRA_REVIEWSECONDS, settings.reviewSeconds);
        intent.putExtra(EXTRA_SILENTSHUTTER, settings.silentShutter);
        intent.putExtra(EXTRA_AEL, settings.ael);
        intent.putExtra(EXTRA_BRS, settings.brs);
        intent.putExtra(EXTRA_MF, settings.mf);
        intent.putExtra(EXTRA_HOLYGRAIL, settings.holyGrail);
        intent.putExtra(EXTRA_USECURRENTEXPOSURE, settings.useCurrentExposure);
        intent.putExtra(EXTRA_TARGETEXPOSURE, settings.targetExposure);
        intent.putExtra(EXTRA_MAXSHUTTERSPEEDINDEX, settings.maxShutterSpeedIndex);
        intent.putExtra(EXTRA_MAXISOINDEX, settings.maxISOIndex);
        intent.putExtra(EXTRA_COOLDOWN, settings.cooldown);
        intent.putExtra(EXTRA_AVERAGEEXPOSUREAMOUNT, settings.averageExposureAmount);
        intent.putExtra(EXTRA_DEADBANDINDEX, settings.deadbandIndex);
        intent.putExtra(EXTRA_HOLYGRAILALLOWEXPOSUREUP, settings.holyGrailAllowExposureUp);
        intent.putExtra(EXTRA_HOLYGRAILALLOWEXPOSUREDOWN, settings.holyGrailAllowExposureDown);
    }

    static Settings get(Intent intent) {
        Settings settings = new Settings();
        settings.interval = intent.getDoubleExtra(EXTRA_INTERVAL, 1);
        settings.shotCount = intent.getIntExtra(EXTRA_SHOTCOUNT, 1);
        settings.delay = intent.getIntExtra(EXTRA_DELAY, 1);
        settings.displayOff = intent.getBooleanExtra(EXTRA_DISPLAYOFF, false);
        settings.reviewSeconds = intent.getIntExtra(EXTRA_REVIEWSECONDS, 0);
        settings.silentShutter = intent.getBooleanExtra(EXTRA_SILENTSHUTTER, true);
        settings.ael = intent.getBooleanExtra(EXTRA_AEL, false);
        settings.brs = intent.getBooleanExtra(EXTRA_BRS, false);
        settings.mf = intent.getBooleanExtra(EXTRA_MF, true);
        settings.holyGrail = intent.getBooleanExtra(EXTRA_HOLYGRAIL, false);
        settings.useCurrentExposure = intent.getBooleanExtra(EXTRA_USECURRENTEXPOSURE, true);
        settings.targetExposure = intent.getIntExtra(EXTRA_TARGETEXPOSURE, 0);
        settings.maxShutterSpeedIndex = intent.getIntExtra(EXTRA_MAXSHUTTERSPEEDINDEX, 0);
        settings.maxISOIndex = intent.getIntExtra(EXTRA_MAXISOINDEX, 0);
        settings.cooldown = intent.getIntExtra(EXTRA_COOLDOWN, 5);
        settings.averageExposureAmount = intent.getIntExtra(EXTRA_AVERAGEEXPOSUREAMOUNT, 1);
        settings.deadbandIndex = intent.getIntExtra(EXTRA_DEADBANDINDEX, 0);
        settings.holyGrailAllowExposureUp = intent.getBooleanExtra(EXTRA_HOLYGRAILALLOWEXPOSUREUP, true);
        settings.holyGrailAllowExposureDown = intent.getBooleanExtra(EXTRA_HOLYGRAILALLOWEXPOSUREDOWN, false);
        return settings;
    }
}
