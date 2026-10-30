package io.github.nathanrcast.timelapse;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import io.github.nathanrcast.timelapse.core.HudModel;

/**
 * Free card space and the average size of this run's pictures, read on a background thread so the capture
 * looper never waits on the card. {@link #request()} is throttled; the main thread reads the volatile results.
 */
final class CardSpace {
    private static final long MIN_GAP_MS = 15000;
    private static final int NEWEST_FILES = 60;
    // The folder listing grows with the card (thousands of names on a big SDXC); the size average needs it far
    // less often than free space does.
    private static final long AVG_GAP_MS = 120000;

    private volatile long freeBytes = HudModel.UNKNOWN;
    private volatile long avgBytes = HudModel.UNKNOWN;

    private final long runStartWallMs;
    private final HandlerThread thread;
    private final Handler handler;
    private long lastMs;
    private long lastAvgMs;
    private volatile boolean queued;
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

    // Never lets an exception out: this runs on its own thread, and a pulled card must not kill the run.
    private void sample() {
        try {
            File root = Environment.getExternalStorageDirectory();
            try {
                StatFs fs = new StatFs(root.getPath());
                freeBytes = (long) fs.getAvailableBlocks() * fs.getBlockSize();
            } catch (RuntimeException e) {
                freeBytes = HudModel.UNKNOWN;
            }
            long now = System.currentTimeMillis();
            if (avgBytes == HudModel.UNKNOWN || now - lastAvgMs >= AVG_GAP_MS) {
                lastAvgMs = now;
                double avg = averageFrameBytes(newestFolder(new File(root, "DCIM")));
                if (avg > 0)
                    avgBytes = (long) avg;
            }
        } catch (Throwable t) {
            Logger.error("CardSpace: " + t);
        } finally {
            queued = false;
        }
    }

    // Camera folders are numbered (100MSDCF, 101MSDCF...): this run's pictures are in the highest one.
    private static File newestFolder(File dcim) {
        String[] names = dcim.list();
        if (names == null)
            return null;
        Arrays.sort(names);
        for (int i = names.length - 1; i >= 0; i--) {
            File dir = new File(dcim, names[i]);
            if (dir.isDirectory())
                return dir;
        }
        return null;
    }

    // Bytes per frame: a RAW+JPEG frame is two files with one basename, so files are grouped by stem.
    private double averageFrameBytes(File dir) {
        if (dir == null)
            return -1;
        String[] names = dir.list();
        if (names == null)
            return -1;
        Arrays.sort(names);
        Map<String, Long> frames = new HashMap<String, Long>();
        int files = 0;
        for (int i = names.length - 1; i >= 0 && files < NEWEST_FILES; i--) {
            String lower = names[i].toLowerCase(Locale.US);
            if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".arw")))
                continue;
            files++;
            File f = new File(dir, names[i]);
            if (f.lastModified() < runStartWallMs)
                continue;
            String stem = names[i].substring(0, names[i].lastIndexOf('.'));
            Long sum = frames.get(stem);
            frames.put(stem, (sum == null ? 0 : sum) + f.length());
        }
        if (frames.isEmpty())
            return -1;
        long total = 0;
        for (long b : frames.values())
            total += b;
        return (double) total / frames.size();
    }
}
