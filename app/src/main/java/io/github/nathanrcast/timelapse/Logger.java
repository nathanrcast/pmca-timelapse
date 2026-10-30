package io.github.nathanrcast.timelapse;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Logger
{
    public static File getFile() {
        return new File(Environment.getExternalStorageDirectory(), "TIMELPS/LOG.TXT");
    }

    // synchronized: CapsDumpActivity logs from its worker thread as well as the UI thread.
    protected static synchronized void log(String msg) {
        try {
            getFile().getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(getFile(), true));
            writer.append(msg);
            writer.newLine();
            writer.close();
        } catch (IOException e) {}
    }
    protected static void log(String type, String msg) { log("[" + type + "] " + msg); }

    public static void info(String msg) { log("INFO", msg); }
    public static void error(String msg) { log("ERROR", msg); }
}
