package io.github.nathanrcast.timelapse;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

import io.github.nathanrcast.timelapse.core.ShotLogCsv;
import io.github.nathanrcast.timelapse.core.ShotRecord;

/**
 * Appends one row per scheduler decision to TIMELPS/LOG.CSV. Callers only post to a background
 * HandlerThread, which owns the file and its one BufferedWriter; sampling battery and free space,
 * formatting and I/O never run on the capture thread. Flushes every FLUSH_ROWS rows, on
 * {@link #flush()} and on {@link #close()}.
 *
 * <p>If the file can't be written, rows queue (at most MAX_PENDING, oldest dropped and counted)
 * while the next rows retry the open, at most MAX_REOPENS times per run. While the file is down
 * each decision goes to LOG.TXT as a terse line instead.
 */
final class ShotLog {
    static final int FLUSH_ROWS = 10;
    static final int MAX_PENDING = 50;
    static final int MAX_REOPENS = 3;
    static final int FREE_SPACE_EVERY_ROWS = 20;
    private static final String RUN_ID_FORMAT = "yyyyMMdd-HHmmss";

    final String runId;

    private final Context context;
    private final HandlerThread thread;
    private final Handler handler;
    private final File file;
    private final String header;
    private final ArrayDeque<ShotRecord> pending = new ArrayDeque<ShotRecord>();
    private BufferedWriter writer;
    private int unflushed;
    private int reopens;
    private int dropped;
    private int battery = ShotRecord.NONE;
    private long freeMb = ShotRecord.NONE;
    private int rowsSinceFreeSpace = FREE_SPACE_EVERY_ROWS;

    ShotLog(Context context, String model, String firmware, String appVersion, long uptimeNowMs,
            String settingsJson) {
        this.context = context.getApplicationContext();
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
                tryOpen();
            }
        });
    }

    /** Queues the row; returns at once. The writer fills in battery and free space. */
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
                flushNow();
                closeQuietly();
                thread.getLooper().quit();
            }
        });
    }

    private boolean tryOpen() {
        try {
            file.getParentFile().mkdirs();
            boolean isNew = !file.exists() || file.length() == 0;
            writer = new BufferedWriter(new FileWriter(file, true));
            if (isNew)
                writer.write(ShotLogCsv.columnHeader() + "\n");
            writer.write(header);
            if (dropped > 0)
                writer.write("# dropped_rows=" + dropped + "\n");
            writer.flush();
            return true;
        } catch (IOException e) {
            fail("open", e);
            return false;
        }
    }

    private void write(ShotRecord record) {
        ShotRecord row = record.withEnvironment(sampleBattery(record), sampleFreeMb());
        if (writer == null && reopens < MAX_REOPENS) {
            reopens++;
            tryOpen();
        }
        if (writer == null) {
            Logger.info("CSV down: " + ShotLogCsv.row(row));
            if (pending.size() >= MAX_PENDING) {
                pending.removeFirst();
                dropped++;
            }
            pending.addLast(row);
            return;
        }
        try {
            while (!pending.isEmpty())
                writeRow(pending.peekFirst(), true);
            writeRow(row, false);
        } catch (IOException e) {
            fail("write", e);
            queue(row);
        }
    }

    private void writeRow(ShotRecord r, boolean fromPending) throws IOException {
        writer.write(ShotLogCsv.row(r));
        writer.write('\n');
        if (fromPending)
            pending.removeFirst();
        if (++unflushed >= FLUSH_ROWS)
            flushNow();
    }

    private void queue(ShotRecord row) {
        if (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
            dropped++;
        }
        pending.addLast(row);
    }

    private int sampleBattery(ShotRecord record) {
        if (record.result == ShotRecord.Result.FIRE) {
            int pct = Battery.read(context).pct;
            if (pct >= 0)
                battery = pct;
        }
        return battery;
    }

    private long sampleFreeMb() {
        if (++rowsSinceFreeSpace >= FREE_SPACE_EVERY_ROWS) {
            rowsSinceFreeSpace = 0;
            try {
                StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
                freeMb = (long) fs.getAvailableBlocks() * fs.getBlockSize() / (1024 * 1024);
            } catch (RuntimeException e) {
                freeMb = ShotRecord.NONE;
            }
        }
        return freeMb;
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

    private void closeQuietly() {
        if (writer == null)
            return;
        try {
            writer.close();
        } catch (IOException e) {
        }
        writer = null;
    }

    // Rows still in the BufferedWriter are lost; count them. The next row retries the open.
    private void fail(String what, IOException e) {
        closeQuietly();
        dropped += unflushed;
        unflushed = 0;
        Logger.error("LOG.CSV " + what + " failed: " + e);
    }
}
