package io.github.nathanrcast.timelapse;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import io.github.nathanrcast.timelapse.core.HudModel;

/**
 * Free card space and the average size of this run's pictures, read on a background thread so the capture
 * looper never waits on the card. {@link #request()} is throttled; the main thread reads the volatile results.
 */
final class CardSpace {
    private static final long MIN_GAP_MS = 15000;
    private static final int NEWEST_FILES = 30;

    private volatile long freeBytes = HudModel.UNKNOWN;
    private volatile long avgBytes = HudModel.UNKNOWN;

    private final long runStartWallMs;
    private final HandlerThread thread;
    private final Handler handler;
    private long lastMs;
    private boolean queued;
    private boolean stopped;

    CardSpace(long runStartWallMs) {
        this.runStartWallMs = runStartWallMs;
        thread = new HandlerThread("CardSpace");
        thread.start();
        handler = new Handler(thread.getLooper());
        request();
    }

    long freeBytes() {
        return freeBytes;
    }

    long avgBytes() {
        return avgBytes;
    }

    /** Queues a read unless one is queued or the last was under MIN_GAP_MS ago. Main thread only. */
    void request() {
        long now = System.currentTimeMillis();
        if (stopped || queued || (lastMs != 0 && now - lastMs < MIN_GAP_MS))
            return;
        queued = true;
        lastMs = now;
        handler.post(new Runnable() {
            @Override
            public void run() {
                sample();
                queued = false;
            }
        });
    }

    /** Idempotent. */
    void stop() {
        if (stopped)
            return;
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        thread.getLooper().quit();
    }

    private void sample() {
        File root = Environment.getExternalStorageDirectory();
        try {
            StatFs fs = new StatFs(root.getPath());
            freeBytes = (long) fs.getAvailableBlocks() * fs.getBlockSize();
        } catch (RuntimeException e) {
            freeBytes = HudModel.UNKNOWN;
        }
        long total = 0;
        int n = 0;
        File[] dirs = new File(root, "DCIM").listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                String[] names = dir.list();
                if (names == null)
                    continue;
                Arrays.sort(names);
                for (int i = names.length - 1, seen = 0; i >= 0 && seen < NEWEST_FILES; i--, seen++) {
                    String lower = names[i].toLowerCase(Locale.US);
                    if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".arw")))
                        continue;
                    File f = new File(dir, names[i]);
                    if (f.lastModified() >= runStartWallMs) {
                        total += f.length();
                        n++;
                    }
                }
            }
        }
        if (n > 0)
            avgBytes = total / n;
    }
}
