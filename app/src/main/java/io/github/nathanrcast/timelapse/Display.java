package io.github.nathanrcast.timelapse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import com.sony.scalar.hardware.avio.DisplayManager;

/**
 * Created by jonas on 2/18/17.
 *
 * <p>DOFF safety: while a run may switch the panel off, a prefs flag is set (commit, once per run, never per
 * shot), so a process that dies with the panel dark is repaired by {@link #restoreIfLeftOff} at the next start.
 * {@link #emergencyRestore()} does the same from the crash handler. {@link #on()} and {@link #off()} never throw.
 */
public class Display {
    public static final int NO_AUTO_OFF = 0;

    private static final String PREFS = "display_state";
    private static final String KEY_OFF_ARMED = "displayOffArmed";

    // The manager of the live run, for the crash handler. Cleared by release().
    private static volatile DisplayManager live;

    private final DisplayManager displayManager;
    private final SharedPreferences prefs;
    private int autoOffDelay;
    // The output that was active before the panel was switched off, so on() puts that one back.
    private String origin;
    private boolean off;
    private boolean armed;

    Display(DisplayManager displayManager, Context context) {
        this.displayManager = displayManager;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        autoOffDelay = 0;
        live = displayManager;
    }

    private Handler turnOffRunnableHandler = new Handler();
    private final Runnable turnOffRunnable = new Runnable() {
        @Override
        public void run() {
            off();
        }
    };

    public void turnAutoOff(int delay) {
        autoOffDelay = delay;

        if(delay == 0) {
            turnOffRunnableHandler.removeCallbacks(turnOffRunnable);
        }
        if(delay > 0) {
            turnOffRunnableHandler.postDelayed(turnOffRunnable, delay);
        }
    }

    public int getAutoOffDelay() {
        return autoOffDelay;
    }

    /** Marks the panel as possibly dark until {@link #disarm()}; idempotent. */
    public void arm() {
        if (armed)
            return;
        armed = true;
        prefs.edit().putBoolean(KEY_OFF_ARMED, true).commit();
    }

    /** Clears the mark. Only after the panel is confirmed back on. */
    public void disarm() {
        if (!armed)
            return;
        armed = false;
        prefs.edit().remove(KEY_OFF_ARMED).commit();
    }

    /** Forgets the live manager (the activity is releasing it). */
    public void release() {
        if (live == displayManager)
            live = null;
    }

    public void off() {
        if (!off) {
            try {
                String active = displayManager.getActiveDevice();
                if (active != null && !DisplayManager.DEVICE_ID_NONE.equals(active))
                    origin = active;
            } catch (Throwable t) {
                origin = null;
            }
        }
        try {
            displayManager.switchDisplayOutputTo(DisplayManager.DEVICE_ID_NONE);
            off = true;
        } catch (Throwable t) {
            Logger.error("display off failed: " + t);
            on();
        }
    }

    /** True if the panel is confirmed on. Retries once; never throws. */
    public boolean on() {
        String target = origin != null ? origin : DisplayManager.DEVICE_ID_PANEL;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                displayManager.switchDisplayOutputTo(target);
                off = false;
                return true;
            } catch (Throwable t) {
                Logger.error("display on failed: " + t);
                target = DisplayManager.DEVICE_ID_PANEL;
            }
        }
        return false;
    }

    public void on(boolean autoOff) {
        on();

        if(autoOff && autoOffDelay > 0) {
            turnOffRunnableHandler.postDelayed(turnOffRunnable, autoOffDelay);
        }
    }

    /** Crash handler: put the panel back with whatever manager the run had. Best effort. */
    static void emergencyRestore() {
        DisplayManager dm = live;
        if (dm == null)
            return;
        try {
            dm.switchDisplayOutputTo(DisplayManager.DEVICE_ID_PANEL);
        } catch (Throwable t) {
            // nothing left to try; the next start repairs it from the flag
        }
    }

    /** First thing at app start: if an earlier process died with DOFF armed, switch the panel on. */
    static void restoreIfLeftOff(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_OFF_ARMED, false))
            return;
        DisplayManager dm = null;
        try {
            dm = new DisplayManager();
            dm.switchDisplayOutputTo(DisplayManager.DEVICE_ID_PANEL);
            prefs.edit().remove(KEY_OFF_ARMED).commit();
            Logger.info("display restored after an earlier run ended with DOFF");
        } catch (Throwable t) {
            Logger.error("display restore failed: " + t);
        } finally {
            if (dm != null) {
                try {
                    dm.finish();
                } catch (Throwable t) {
                    // already gone
                }
            }
        }
    }
}
