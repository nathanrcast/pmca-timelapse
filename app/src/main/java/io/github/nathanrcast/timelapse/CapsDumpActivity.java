package io.github.nathanrcast.timelapse;

import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.ma1co.openmemories.framework.DeviceInfo;
import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;
import com.sony.scalar.sysutil.ScalarProperties;

import io.github.nathanrcast.timelapse.core.camera.SonyMethod;
import io.github.nathanrcast.timelapse.core.WallTime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// T08 diagnostics: probes what the body's Sony API offers into TIMELPS/CAPS-<model>.txt, then logs every key event.
// Every probe catches Throwable on its own, so a method missing on this firmware can't crash the camera. The file is
// written as the probe goes, and each call's name is flushed before the call, so a native crash leaves a file whose
// last line names the call that killed it.
public class CapsDumpActivity extends BaseActivity {
    private static final long MENU_HOLD_MS = 2000;
    private static final int SCREEN_LINES = 15;
    private static final String EXIT = "Hold MENU 2 s or press DELETE to exit.";
    private static final String HINT = "Turn every dial and press every button (DELETE last).\n" + EXIT;

    private static final String CAMERA_EX = "com.sony.scalar.hardware.CameraEx";
    private static final String SCALAR_INPUT = "com.sony.scalar.sysutil.ScalarInput";

    private static final String[] PROBED_CLASSES = {
            CAMERA_EX,
            CAMERA_EX + "$ParametersModifier",
            CAMERA_EX + "$AutoPictureReviewControl",
            CAMERA_EX + "$ShutterListener",
            CAMERA_EX + "$ProgramLineRangeOverListener",
            "com.sony.scalar.hardware.CameraSequence",
            "com.sony.scalar.hardware.avio.DisplayManager",
            "com.sony.scalar.hardware.avio.DisplayManager$DisplayEventListener",
            SCALAR_INPUT,
            "com.sony.scalar.sysutil.didep.Gpelibrary",
    };

    private static final String[] CONSTANT_CLASSES = {CAMERA_EX, CAMERA_EX + "$ParametersModifier"};

    // Sony methods the app uses besides SafeCamera's SonyMethod list: the listener callbacks camera/ implements and the
    // display and input calls from BaseActivity and Display.
    private static final String[] OTHER_USED_METHODS = {
            "CameraEx$ShutterListener.onShutter(int, CameraEx)",
            "CameraEx$ProgramLineRangeOverListener.onAERange(boolean, boolean, boolean, CameraEx)",
            "CameraEx$ProgramLineRangeOverListener.onEVRange(int, CameraEx)",
            "CameraEx$ProgramLineRangeOverListener.onMeteringRange(boolean, CameraEx)",
            "DisplayManager.setDisplayStatusListener(DisplayManager$DisplayEventListener)",
            "DisplayManager.releaseDisplayStatusListener()",
            "DisplayManager.getActiveDevice()",
            "DisplayManager.switchDisplayOutputTo(String)",
            "DisplayManager.finish()",
            "DisplayManager$DisplayEventListener.onDeviceStatusChanged(int)",
            "ScalarInput.getKeyStatus(int)",
            "Gpelibrary.changeFrameBufferPixel(Gpelibrary$GS_FRAMEBUFFER_TYPE)",
    };

    // Every Sony method the app calls or implements, in CapsFormat.methodKey form.
    static final String[] USED_METHODS = usedMethods();

    private static final String[] DEVICE_INFO_GETTERS = {
            "isCamera", "getBrand", "getModel", "getCategory", "getProductCode", "getFirmwareVersion",
            "getHardwareVersion", "getApiVersion", "getAndroidVersion", "getAndroidSdkVersion",
            "getAndroidIncrementalVersion",
    };

    private static final String[] CAMERA_EX_GETTERS = {"hasSupportedParameters", "getInhibitionInfo", "getLensInfo"};

    private final Handler ui = new Handler();
    private final LinkedList<String> screenLines = new LinkedList<String>();
    private final long startedAt = SystemClock.uptimeMillis();

    private Map<Integer, String> keyNames = Collections.emptyMap();
    private TextView tvStatus, tvKeys;
    private HandlerThread workerThread;
    private Handler worker;
    private boolean probeStarted;
    private long menuDownAt = -1;
    private boolean exitKeyDown;

    // Owned by the worker thread: the one CAPS writer, kept open for the key log until onDestroy.
    private CapsWriter caps;

    private final Runnable probeRunnable = new Runnable() {
        @Override
        public void run() {
            probe();
        }
    };

    private final Runnable menuHeldRunnable = new Runnable() {
        @Override
        public void run() {
            tvStatus.setText("Release MENU to exit");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(10, 10, 10, 10);
        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.WHITE);
        tvStatus.setTextSize(16);
        tvStatus.setText("Camera info: starting...");
        tvKeys = new TextView(this);
        tvKeys.setTextColor(Color.LTGRAY);
        tvKeys.setTextSize(12);
        tvKeys.setTypeface(Typeface.MONOSPACE);
        root.addView(tvStatus);
        root.addView(tvKeys);
        setContentView(root);

        try {
            keyNames = CapsFormat.constantNames(Class.forName(SCALAR_INPUT), "ISV_");
        } catch (Throwable t) {
            Logger.error("CapsDump: ScalarInput constants: " + CapsFormat.failure(t));
        }

        // A Looper thread, so any Handler the Sony classes create while the probe runs has a queue to bind to.
        workerThread = new HandlerThread("CapsDump");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        setAutoPowerOffMode(false);
        if (!probeStarted) {
            probeStarted = true;
            worker.post(probeRunnable);
        }
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(menuHeldRunnable);
        menuDownAt = -1;
        exitKeyDown = false;
        setAutoPowerOffMode(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Queued behind the probe and any pending key lines; the probe releases the camera itself.
        worker.post(new Runnable() {
            @Override
            public void run() {
                if (caps != null)
                    caps.close();
                caps = null;
                Looper.myLooper().quit();
            }
        });
        super.onDestroy();
    }

    // Above BaseActivity's onKeyDown/onKeyUp switch, so every key, wheel and dial event is logged. Only the exits act.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            logKey(event);
        } catch (Throwable t) {
            Logger.error("CapsDump: key " + CapsFormat.failure(t));
        }
        return true;
    }

    private void logKey(KeyEvent event) {
        int scan = event.getScanCode();
        int code = event.getKeyCode();
        int action = event.getAction();
        String extra = action == KeyEvent.ACTION_DOWN ? dialStatus(scan) : null;
        record(CapsFormat.keyLine(event.getEventTime() - startedAt,
                CapsFormat.actionName(action, event.getRepeatCount()),
                code, scan, CapsFormat.keyName(keyNames, scan), extra));

        // Both exits finish on key-up, so the release can't reach SettingsActivity (whose MENU/DELETE key-up closes the app).
        if (scan == ScalarInput.ISV_KEY_MENU || scan == ScalarInput.ISV_KEY_SK1 || code == KeyEvent.KEYCODE_MENU) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                menuDownAt = event.getEventTime();
                ui.postDelayed(menuHeldRunnable, MENU_HOLD_MS);
            } else if (action == KeyEvent.ACTION_UP) {
                ui.removeCallbacks(menuHeldRunnable);
                boolean held = menuDownAt >= 0 && event.getEventTime() - menuDownAt >= MENU_HOLD_MS;
                menuDownAt = -1;
                if (held)
                    finish();
            }
        } else if (scan == ScalarInput.ISV_KEY_DELETE || scan == ScalarInput.ISV_KEY_SK2 || code == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN)
                exitKeyDown = true;
            else if (action == KeyEvent.ACTION_UP && exitKeyDown)
                finish();
        }
    }

    private String dialStatus(int scan) {
        int statusKey;
        switch (scan) {
            case ScalarInput.ISV_DIAL_1_CLOCKWISE:
            case ScalarInput.ISV_DIAL_1_COUNTERCW:
                statusKey = ScalarInput.ISV_DIAL_1_STATUS;
                break;
            case ScalarInput.ISV_DIAL_2_CLOCKWISE:
            case ScalarInput.ISV_DIAL_2_COUNTERCW:
                statusKey = ScalarInput.ISV_DIAL_2_STATUS;
                break;
            case ScalarInput.ISV_DIAL_3_CLOCKWISE:
            case ScalarInput.ISV_DIAL_3_COUNTERCW:
                statusKey = ScalarInput.ISV_DIAL_3_STATUS;
                break;
            case ScalarInput.ISV_DIAL_KURU_CLOCKWISE:
            case ScalarInput.ISV_DIAL_KURU_COUNTERCW:
                statusKey = ScalarInput.ISV_DIAL_KURU_STATUS;
                break;
            case ScalarInput.ISV_RING_CLOCKWISE:
            case ScalarInput.ISV_RING_COUNTERCW:
                statusKey = ScalarInput.ISV_RING_STATUS;
                break;
            case ScalarInput.ISV_RING_LENS_APERTURE_CLOCKWISE:
            case ScalarInput.ISV_RING_LENS_APERTURE_COUNTERCW:
                statusKey = ScalarInput.ISV_RING_LENS_APERTURE_STATUS;
                break;
            case ScalarInput.ISV_KEY_MODE_DIAL:
                statusKey = ScalarInput.ISV_KEY_MODE_DIAL;
                break;
            default:
                return null;
        }
        try {
            return "status=" + getDialStatus(statusKey);
        } catch (Throwable t) {
            return "status!!" + CapsFormat.failure(t);
        }
    }

    private void record(final String line) {
        screenLines.add(line);
        while (screenLines.size() > SCREEN_LINES)
            screenLines.removeFirst();
        StringBuilder sb = new StringBuilder();
        for (String l : screenLines)
            sb.append(l).append('\n');
        tvKeys.setText(sb.toString());

        worker.post(new Runnable() {
            @Override
            public void run() {
                if (caps != null) {
                    caps.line(line);
                    caps.flush();
                }
            }
        });
    }

    private void status(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(text);
            }
        });
    }

    // Never throws: an IOException is kept in `error` and further writes are skipped.
    private static final class CapsWriter {
        final File file;
        private final FileOutputStream fos;
        private final Writer w;
        int lines;
        String error;

        CapsWriter(File file) throws IOException {
            this.file = file;
            fos = new FileOutputStream(file, false);
            w = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
        }

        private void write(String s) {
            if (error != null)
                return;
            try {
                w.write(s);
            } catch (IOException e) {
                error = CapsFormat.failure(e);
            }
        }

        void line(String s) {
            write(s);
            write("\n");
            lines++;
        }

        // Writes and flushes the call's name before the call; end() completes the line with the result.
        void begin(String label) {
            write(label);
            flush();
        }

        void end(String result) {
            line(result);
        }

        void flush() {
            if (error != null)
                return;
            try {
                w.flush();
            } catch (IOException e) {
                error = CapsFormat.failure(e);
            }
        }

        // Flush and fsync at the end of each probe group, so a camera reboot still keeps the groups that finished.
        void sync() {
            flush();
            if (error != null)
                return;
            try {
                fos.getFD().sync();
            } catch (IOException e) {
                error = CapsFormat.failure(e);
            }
        }

        void close() {
            sync();
            try {
                w.close();
            } catch (IOException ignored) {
            }
        }
    }

    // Worker thread from here on.

    private void probe() {
        String model = DeviceId.read().model;

        String openError = openCaps(CapsFormat.capsFileName(model));
        if (caps == null) {
            Logger.error("CapsDump: " + openError);
            status("Could not write " + openError + "\nKey log NOT being saved.\n" + EXIT);
            return;
        }

        String aborted = null;
        try {
            dump(caps);
        } catch (Throwable t) {
            aborted = CapsFormat.failure(t);
            caps.line("!! probe aborted: " + aborted);
        }
        caps.line("");
        caps.line("## Key log: seconds since start, action, keyCode, scanCode, ScalarInput name, dial status");
        caps.sync();

        String path = caps.file.getPath();
        if (caps.error != null) {
            Logger.error("CapsDump: " + path + ": " + caps.error);
            status("Write failed: " + path + ": " + caps.error + "\nKey log NOT being saved.\n" + EXIT);
        } else if (aborted != null) {
            Logger.error("CapsDump: probe aborted: " + aborted);
            status("Probe aborted: " + aborted + "\nKey log is being saved to " + path + "\n" + HINT);
        } else {
            Logger.info("CapsDump: wrote " + path + " (" + caps.lines + " lines)");
            status("Wrote " + path + " (" + caps.lines + " lines)\n" + HINT);
        }
    }

    private String openCaps(String name) {
        File dir = Logger.getFile().getParentFile();
        dir.mkdirs();
        File primary = new File(dir, name);
        try {
            caps = new CapsWriter(primary);
            return null;
        } catch (IOException e) {
            String error = primary.getPath() + ": " + CapsFormat.failure(e);
            Logger.error("CapsDump: could not open " + error + " (dir exists=" + dir.isDirectory() + ", can write="
                    + dir.canWrite() + ", file exists=" + primary.exists() + "); falling back to CAPS.TXT");
            // Fall back to an 8.3 name in case the card's filesystem rejects the long one.
            File fallback = new File(dir, "CAPS.TXT");
            try {
                caps = new CapsWriter(fallback);
                return null;
            } catch (IOException e2) {
                return error + "; " + fallback.getPath() + ": " + CapsFormat.failure(e2);
            }
        }
    }

    private void section(CapsWriter out, String title) {
        out.line("");
        out.line("## " + title);
        out.flush();
        status("Probing: " + title + "\nWriting " + out.file.getPath());
    }

    private void dump(CapsWriter out) {
        String version;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            version = "?";
        }
        out.line("# TimeLapse+ CapsDump " + version);
        WallTime wall = Clocks.wallNow();
        out.line("# written " + wall.format("yyyy-MM-dd HH:mm:ss") + " (clock=" + wall.source + ", utc_offset_min="
                + wall.utcOffsetMinutes() + ")");
        out.line("# A line with a call name but no ' = ' or ' !! ' is the call that crashed the probe.");

        section(out, "Device (serial number omitted)");
        DeviceInfo info = null;
        out.begin("DeviceInfo.getInstance");
        try {
            info = DeviceInfo.getInstance();
            out.end(" = ok");
        } catch (Throwable t) {
            out.end(" !! " + CapsFormat.failure(t));
        }
        if (info != null)
            for (String name : DEVICE_INFO_GETTERS)
                probeByName(out, "DeviceInfo.", info, name);
        out.begin("ScalarProperties.version.platform");
        try {
            out.end(" = " + CapsFormat.value(ScalarProperties.getString("version.platform")));
        } catch (Throwable t) {
            out.end(" !! " + CapsFormat.failure(t));
        }
        out.line("Build.MODEL = " + CapsFormat.value(Build.MODEL));
        out.line("Build.DEVICE = " + CapsFormat.value(Build.DEVICE));
        out.line("Build.PRODUCT = " + CapsFormat.value(Build.PRODUCT));
        out.line("Build.MANUFACTURER = " + CapsFormat.value(Build.MANUFACTURER));
        out.line("Build.VERSION.RELEASE = " + CapsFormat.value(Build.VERSION.RELEASE));
        out.line("Build.VERSION.SDK_INT = " + Build.VERSION.SDK_INT);
        out.line("Build.VERSION.INCREMENTAL = " + CapsFormat.value(Build.VERSION.INCREMENTAL));
        out.line("Build.FINGERPRINT = " + CapsFormat.value(Build.FINGERPRINT));
        out.sync();

        // Listing methods needs no initialization, so classes load with initialize=false; reading constants does
        // initialize, which is why constants are read only from CameraEx and ParametersModifier, after CameraEx.open.
        section(out, "Classes");
        Map<String, Class<?>> classes = new TreeMap<String, Class<?>>();
        for (String name : PROBED_CLASSES)
            loadClass(out, classes, name);
        Class<?> cameraExClass = classes.get(CAMERA_EX);
        if (cameraExClass != null) {
            try {
                Set<String> nested = new TreeSet<String>();
                for (Class<?> c : cameraExClass.getClasses()) {
                    nested.add((c.isInterface() ? "interface " : "class ") + c.getName());
                    if (c.isInterface())
                        classes.put(c.getName(), c);
                }
                out.line("");
                out.line("CameraEx nested classes: " + nested.size());
                for (String n : nested)
                    out.line(n);
            } catch (Throwable t) {
                out.line("CameraEx.getClasses !! " + CapsFormat.failure(t));
            }
        }

        Set<String> present = new TreeSet<String>();
        for (Class<?> c : classes.values()) {
            try {
                present.addAll(CapsFormat.methodKeys(c));
            } catch (Throwable t) {
                out.line(c.getName() + ".getMethods !! " + CapsFormat.failure(t));
            }
        }
        section(out, "Used methods (app calls today) vs this firmware");
        for (String l : CapsFormat.usedReport(USED_METHODS, present))
            out.line(l);

        section(out, "ScalarInput ISV_* constants (" + keyNames.size() + ")");
        for (Map.Entry<Integer, String> e : keyNames.entrySet())
            out.line(e.getKey() + " " + e.getValue());
        out.sync();

        dumpValues(out);

        for (Map.Entry<String, Class<?>> e : classes.entrySet()) {
            try {
                List<Method> methods = CapsFormat.methods(e.getValue());
                section(out, "Methods " + e.getKey() + " (" + methods.size() + ")");
                for (Method m : methods)
                    out.line(CapsFormat.signature(m));
            } catch (Throwable t) {
                out.line("Methods " + e.getKey() + " !! " + CapsFormat.failure(t));
            }
        }
        out.sync();

        for (String name : CONSTANT_CLASSES) {
            Class<?> c = classes.get(name);
            if (c == null)
                continue;
            section(out, "Constants " + name);
            try {
                for (String l : CapsFormat.constants(c))
                    out.line(l);
            } catch (Throwable t) {
                out.line("constants !! " + CapsFormat.failure(t));
            }
            out.sync();
        }
    }

    private static String[] usedMethods() {
        List<String> used = new ArrayList<String>(SonyMethod.keys());
        Collections.addAll(used, OTHER_USED_METHODS);
        return used.toArray(new String[used.size()]);
    }

    private static void loadClass(CapsWriter out, Map<String, Class<?>> classes, String name) {
        try {
            classes.put(name, Class.forName(name, false, CapsDumpActivity.class.getClassLoader()));
            out.line("present  " + name);
        } catch (Throwable t) {
            out.line("ABSENT   " + name + " (" + CapsFormat.failure(t) + ")");
        }
    }

    private static void probeByName(CapsWriter out, String prefix, Object target, String name) {
        Method m;
        try {
            m = target.getClass().getMethod(name);
        } catch (Throwable t) {
            out.line(prefix + name + " !! " + CapsFormat.failure(t));
            return;
        }
        out.begin(prefix + name);
        out.end(CapsFormat.result(target, m));
    }

    private static void probeGetters(CapsWriter out, Object target) {
        List<Method> getters;
        try {
            getters = CapsFormat.getters(target.getClass());
        } catch (Throwable t) {
            out.line("getters !! " + CapsFormat.failure(t));
            return;
        }
        for (Method m : getters) {
            out.begin(m.getName());
            out.end(CapsFormat.result(target, m));
        }
    }

    private static void flatten(CapsWriter out, Camera.Parameters params) {
        out.begin("flatten");
        List<String> lines;
        try {
            lines = CapsFormat.sortedLines(params.flatten());
            out.end(" = " + lines.size() + " keys");
        } catch (Throwable t) {
            out.end(" !! " + CapsFormat.failure(t));
            return;
        }
        for (String l : lines)
            out.line(l);
    }

    // Opens and releases CameraEx as ShootActivity does, and only reads: no set*, no setParameters, nothing with arguments.
    private void dumpValues(CapsWriter out) {
        section(out, "Values CameraEx");
        CameraEx cameraEx;
        out.begin("CameraEx.open(0, null)");
        try {
            cameraEx = CameraEx.open(0, null);
            out.end(cameraEx == null ? " = null" : " = ok");
        } catch (Throwable t) {
            out.end(" !! " + CapsFormat.failure(t));
            return;
        }
        if (cameraEx == null)
            return;
        try {
            for (String name : CAMERA_EX_GETTERS)
                probeByName(out, "", cameraEx, name);
            out.sync();

            Camera.Parameters params = null;
            out.begin("getNormalCamera().getParameters()");
            try {
                params = cameraEx.getNormalCamera().getParameters();
                out.end(" = ok");
            } catch (Throwable t) {
                out.end(" !! " + CapsFormat.failure(t));
            }
            if (params != null) {
                section(out, "Values CameraEx$ParametersModifier on getNormalCamera().getParameters()");
                Object modifier = null;
                out.begin("createParametersModifier");
                try {
                    modifier = cameraEx.createParametersModifier(params);
                    out.end(" = ok");
                } catch (Throwable t) {
                    out.end(" !! " + CapsFormat.failure(t));
                }
                if (modifier != null)
                    probeGetters(out, modifier);
                out.sync();

                section(out, "Values Camera$Parameters");
                probeGetters(out, params);
                out.sync();

                section(out, "Camera$Parameters.flatten() of getNormalCamera().getParameters()");
                flatten(out, params);
                out.sync();
            }

            section(out, "Camera$Parameters.flatten() of CameraEx.getSupportedParameters()");
            Object supported = null;
            out.begin("getSupportedParameters");
            try {
                supported = cameraEx.getClass().getMethod("getSupportedParameters").invoke(cameraEx);
                out.end(supported instanceof Camera.Parameters ? " = ok" : " = " + CapsFormat.value(supported));
            } catch (Throwable t) {
                out.end(" !! " + CapsFormat.failure(t));
            }
            if (supported instanceof Camera.Parameters)
                flatten(out, (Camera.Parameters) supported);
            out.sync();
        } finally {
            out.begin("CameraEx.release()");
            try {
                cameraEx.release();
                out.end(" = ok");
            } catch (Throwable t) {
                out.end(" !! " + CapsFormat.failure(t));
            }
            out.sync();
        }
    }
}
