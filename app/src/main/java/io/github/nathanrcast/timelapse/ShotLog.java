package io.github.nathanrcast.timelapse;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.nathanrcast.timelapse.core.ShotLogCsv;
import io.github.nathanrcast.timelapse.core.ShotRecord;

/**
 * Appends one row per scheduler decision to TIMELPS/LOG.CSV. Callers only post to a background
 * HandlerThread, which owns the file and its one BufferedWriter; formatting and I/O never run on the
 * capture thread. Flushes every FLUSH_ROWS rows, on {@link #flush()} and on {@link #close()}.
 */
final class ShotLog {
    static final int FLUSH_ROWS = 10;
    private static final String RUN_ID_FORMAT = "yyyyMMdd-HHmmss";

    final String runId;

    private final HandlerThread thread;
    private final Handler handler;
    private final File file;
    private final String header;
    private BufferedWriter writer;
    private int unflushed;
    private boolean failed;

    ShotLog(String model, String firmware, String appVersion, long uptimeNowMs, String settingsJson) {
        long wallNow = System.currentTimeMillis();
        runId = new SimpleDateFormat(RUN_ID_FORMAT, Locale.US).format(new Date(wallNow));
        file = new File(Environment.getExternalStorageDirectory(), "TIMELPS/LOG.CSV");
        header = ShotLogCsv.runHeader(runId, model, firmware, appVersion, wallNow - uptimeNowMs, settingsJson);
        thread = new HandlerThread("ShotLog");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                openFile();
            }
        });
    }

    /** Queues the row; returns at once. */
    void add(final ShotRecord record) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                write(record);
            }
        });
    }

    void flush() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                flushNow();
            }
        });
    }

    /** Flushes and closes after every queued row, then ends the thread. */
    void close() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                closeNow();
                thread.getLooper().quit();
            }
        });
    }

    private void openFile() {
        try {
            file.getParentFile().mkdirs();
            boolean isNew = !file.exists() || file.length() == 0;
            writer = new BufferedWriter(new FileWriter(file, true));
            if (isNew)
                writer.write(ShotLogCsv.columnHeader() + "\n");
            writer.write(header);
            writer.flush();
        } catch (IOException e) {
            fail("open", e);
        }
    }

    private void write(ShotRecord record) {
        if (writer == null)
            return;
        try {
            writer.write(ShotLogCsv.row(record));
            writer.write('\n');
            if (++unflushed >= FLUSH_ROWS)
                flushNow();
        } catch (IOException e) {
            fail("write", e);
        }
    }

    private void flushNow() {
        if (writer == null)
            return;
        try {
            writer.flush();
            unflushed = 0;
        } catch (IOException e) {
            fail("flush", e);
        }
    }

    private void closeNow() {
        if (writer == null)
            return;
        try {
            writer.close();
        } catch (IOException e) {
            fail("close", e);
        }
        writer = null;
    }

    private void fail(String what, IOException e) {
        writer = null;
        if (!failed) {
            failed = true;
            Logger.error("LOG.CSV " + what + " failed: " + e);
        }
    }
}
