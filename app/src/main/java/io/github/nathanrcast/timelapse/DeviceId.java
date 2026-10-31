package io.github.nathanrcast.timelapse;

import android.os.Build;

import com.github.ma1co.openmemories.framework.DeviceInfo;

// What the run header says about the body. DeviceInfo has the real model and firmware; Build.* on the cameras
// reports "ScalarA" and an internal build number.
final class DeviceId {
    final String model;
    final String firmware;
    // "<release>/<sdk>", e.g. 4.1.2/16.
    final String android;

    private DeviceId(String model, String firmware, String android) {
        this.model = model;
        this.firmware = firmware;
        this.android = android;
    }

    static DeviceId read() {
        String model = null, firmware = null;
        try {
            DeviceInfo info = DeviceInfo.getInstance();
            model = clean(info.getModel());
            firmware = clean(info.getFirmwareVersion());
        } catch (Throwable ignored) {
        }
        if (model == null)
            model = Build.MODEL;
        if (firmware == null)
            firmware = Build.VERSION.INCREMENTAL;
        return new DeviceId(model, firmware, Build.VERSION.RELEASE + "/" + Build.VERSION.SDK_INT);
    }

    private static String clean(String s) {
        return s == null || s.trim().length() == 0 ? null : s.trim();
    }
}
