package io.github.nathanrcast.timelapse;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Logger
{
    public static File getFile() {
        return new File(Environment.getExternalStorageDirectory(), "TIMELPS/LOG.TXT");
    }

    // Lines are queued to one idle-expiring daemon thread: the capture looper never touches the card for a log line.
    // The queue is bounded and drops the oldest line.
    private static final ThreadPoolExecutor IO = newExecutor();

    private static ThreadPoolExecutor newExecutor() {
        ThreadPoolExecutor e = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(256),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "Logger");
                        t.setDaemon(true);
                        return t;
                    }
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
        e.allowCoreThreadTimeOut(true);
        return e;
    }

    protected static void log(final String msg) {
        try {
            IO.execute(new Runnable() {
                @Override
                public void run() {
                    write(msg);
                }
            });
        } catch (RuntimeException e) {
            // never let logging take the run down
        }
    }

    /** Writes on the calling thread; for the crash handler, which has no time to queue. */
    static void logNow(String type, String msg) {
        write("[" + type + "] " + msg);
    }

    // synchronized: the worker and logNow can both write.
    private static synchronized void write(String msg) {
        try {
            getFile().getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(getFile(), true));
            writer.append(msg);
            writer.newLine();
            writer.close();
        } catch (Throwable e) {}
    }
    protected static void log(String type, String msg) { log("[" + type + "] " + msg); }

    public static void info(String msg) { log("INFO", msg); }
    public static void error(String msg) { log("ERROR", msg); }
}
