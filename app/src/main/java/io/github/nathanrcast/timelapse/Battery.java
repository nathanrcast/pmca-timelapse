package io.github.nathanrcast.timelapse;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/** The battery level and charging state from the sticky ACTION_BATTERY_CHANGED broadcast. */
final class Battery {
    final int pct;
    final boolean charging;

    private Battery(int pct, boolean charging) {
        this.pct = pct;
        this.charging = charging;
    }

    /** pct is -1 when the system has no sticky intent or a bad level. */
    static Battery read(Context context) {
        Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null)
            return new Battery(-1, false);
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || plug == BatteryManager.BATTERY_PLUGGED_USB
                || plug == BatteryManager.BATTERY_PLUGGED_AC;
        int pct = level >= 0 && scale > 0 ? (int) (level * 100L / scale) : -1;
        return new Battery(pct, charging);
    }
}
