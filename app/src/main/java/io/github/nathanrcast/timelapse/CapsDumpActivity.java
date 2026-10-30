package io.github.nathanrcast.timelapse;

import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.ma1co.openmemories.framework.DeviceInfo;
import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;
import com.sony.scalar.sysutil.ScalarProperties;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// T08 diagnostics: dumps what the body's Sony API offers to TIMELPS/CAPS-<model>.txt, then logs every key event.
// Every probe catches Throwable on its own, so a method missing on this firmware can't crash the camera.
public class CapsDumpActivity extends BaseActivity {
    private static final long MENU_HOLD_MS = 2000;
    private static final int SCREEN_LINES = 15;
    private static final String HINT = "Turn every dial and press every button; hold MENU 2 s to exit";

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

    // Sony methods the app calls today (ShootActivity, BaseActivity, Display), in CapsFormat.methodKey form.
    static final String[] USED_METHODS = {
            "CameraEx.open(int, CameraEx$OpenOptions)",
            "CameraEx.release()",
            "CameraEx.getNormalCamera()",
            "CameraEx.createParametersModifier(Camera$Parameters)",
            "CameraEx.createEmptyParameters()",
            "CameraEx.setShutterListener(CameraEx$ShutterListener)",
            "CameraEx.setAutoPictureReviewControl(CameraEx$AutoPictureReviewControl)",
            "CameraEx.setProgramLineRangeOverListener(CameraEx$ProgramLineRangeOverListener)",
            "CameraEx.burstableTakePicture()",
            "CameraEx.cancelTakePicture()",
            "CameraEx.incrementShutterSpeed()",
            "CameraEx.decrementShutterSpeed()",
            "CameraEx$ParametersModifier.setDriveMode(String)",
            "CameraEx$ParametersModifier.getSupportedBurstDriveSpeeds()",
            "CameraEx$ParametersModifier.setBurstDriveSpeed(String)",
            "CameraEx$ParametersModifier.setBurstDriveButtonReleaseBehave(String)",
            "CameraEx$ParametersModifier.setSilentShutterMode(boolean)",
            "CameraEx$ParametersModifier.setAutoExposureLock(String)",
            "CameraEx$ParametersModifier.setBracketMode(String)",
            "CameraEx$ParametersModifier.setExposureBracketMode(String)",
            "CameraEx$ParametersModifier.setExposureBracketPeriod(int)",
            "CameraEx$ParametersModifier.setNumOfBracketPicture(int)",
            "CameraEx$ParametersModifier.getShutterSpeed()",
            "CameraEx$ParametersModifier.getISOSensitivity()",
            "CameraEx$ParametersModifier.setISOSensitivity(int)",
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

    private static final String[] DEVICE_INFO_GETTERS = {
            "isCamera", "getBrand", "getModel", "getCategory", "getProductCode", "getFirmwareVersion",
            "getHardwareVersion", "getApiVersion", "getAndroidVersion", "getAndroidSdkVersion",
            "getAndroidIncrementalVersion",
    };

    private static final String[] CAMERA_EX_GETTERS = {"hasSupportedParameters", "getInhibitionInfo", "getLensInfo"};

    private final Handler handler = new Handler();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LinkedList<String> screenLines = new LinkedList<String>();
    private final long startedAt = SystemClock.uptimeMillis();

    private Map<Integer, String> keyNames = Collections.emptyMap();
    private TextView tvStatus, tvKeys;
    private boolean dumped;
    private long menuDownAt = -1;
    private File capsFile;

    private final Runnable dumpRunnable = new Runnable() {
        @Override
        public void run() {
            dumped = true;
            try {
                dump();
            } catch (Throwable t) {
                Logger.error("CapsDump: " + CapsFormat.failure(t));
                tvStatus.setText("Probe failed: " + CapsFormat.failure(t) + "\n" + HINT);
            }
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
        tvStatus.setText("Camera info: probing...");
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        setAutoPowerOffMode(false);
        if (!dumped)
            handler.post(dumpRunnable);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(dumpRunnable);
        handler.removeCallbacks(menuHeldRunnable);
        menuDownAt = -1;
        setAutoPowerOffMode(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    // Above BaseActivity's onKeyDown/onKeyUp switch, so every key, wheel and dial event is logged and none is acted on.
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
        String extra = event.getAction() == KeyEvent.ACTION_DOWN ? dialStatus(scan) : null;
        record(CapsFormat.keyLine(event.getEventTime() - startedAt,
                CapsFormat.actionName(event.getAction(), event.getRepeatCount()),
                event.getKeyCode(), scan, CapsFormat.keyName(keyNames, scan), extra));

        if (scan == ScalarInput.ISV_KEY_MENU || scan == ScalarInput.ISV_KEY_SK1 || event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                menuDownAt = event.getEventTime();
                handler.postDelayed(menuHeldRunnable, MENU_HOLD_MS);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(menuHeldRunnable);
                boolean held = menuDownAt >= 0 && event.getEventTime() - menuDownAt >= MENU_HOLD_MS;
                menuDownAt = -1;
                // Finish on release, so the MENU key-up can't reach SettingsActivity and close the app.
                if (held)
                    finish();
            }
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

    private void record(String line) {
        screenLines.add(line);
        while (screenLines.size() > SCREEN_LINES)
            screenLines.removeFirst();
        StringBuilder sb = new StringBuilder();
        for (String l : screenLines)
            sb.append(l).append('\n');
        tvKeys.setText(sb.toString());

        final String text = line + "\n";
        io.execute(new Runnable() {
            @Override
            public void run() {
                if (capsFile == null)
                    return;
                try {
                    write(capsFile, text, true);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private static void write(File f, String text, boolean append) throws IOException {
        File dir = f.getParentFile();
        if (dir != null)
            dir.mkdirs();
        Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, append), "UTF-8"));
        try {
            w.write(text);
        } finally {
            w.close();
        }
    }

    private static final class Out {
        final StringBuilder sb = new StringBuilder();
        int lines;

        void line(String s) {
            sb.append(s).append('\n');
            lines++;
        }

        void lines(List<String> list) {
            for (String s : list)
                line(s);
        }

        void section(String title) {
            line("");
            line("## " + title);
        }

        void fail(String what, Throwable t) {
            line(what + " !! " + CapsFormat.failure(t));
        }
    }

    private void dump() {
        Out out = new Out();
        String model = null;

        String version;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            version = "?";
        }
        out.line("# TimeLapse+ CapsDump " + version);
        out.line("# written " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        out.section("Device (serial number omitted)");
        try {
            DeviceInfo info = DeviceInfo.getInstance();
            for (String name : DEVICE_INFO_GETTERS)
                probeByName(out, "DeviceInfo.", info, name);
            model = info.getModel();
        } catch (Throwable t) {
            out.fail("DeviceInfo", t);
        }
        try {
            out.line("ScalarProperties.version.platform = " + CapsFormat.value(ScalarProperties.getString("version.platform")));
        } catch (Throwable t) {
            out.fail("ScalarProperties.version.platform", t);
        }
        out.line("Build.MODEL = " + CapsFormat.value(Build.MODEL));
        out.line("Build.DEVICE = " + CapsFormat.value(Build.DEVICE));
        out.line("Build.PRODUCT = " + CapsFormat.value(Build.PRODUCT));
        out.line("Build.MANUFACTURER = " + CapsFormat.value(Build.MANUFACTURER));
        out.line("Build.VERSION.RELEASE = " + CapsFormat.value(Build.VERSION.RELEASE));
        out.line("Build.VERSION.SDK_INT = " + Build.VERSION.SDK_INT);
        out.line("Build.VERSION.INCREMENTAL = " + CapsFormat.value(Build.VERSION.INCREMENTAL));
        out.line("Build.FINGERPRINT = " + CapsFormat.value(Build.FINGERPRINT));
        if (model == null || model.length() == 0)
            model = Build.MODEL;

        Map<String, Class<?>> classes = new TreeMap<String, Class<?>>();
        Out classLines = new Out();
        for (String name : PROBED_CLASSES)
            loadClass(classLines, classes, name);
        Class<?> cameraExClass = classes.get(CAMERA_EX);
        if (cameraExClass != null) {
            try {
                Set<String> nested = new TreeSet<String>();
                for (Class<?> c : cameraExClass.getClasses()) {
                    nested.add((c.isInterface() ? "interface " : "class ") + c.getName());
                    if (c.isInterface())
                        classes.put(c.getName(), c);
                }
                classLines.line("");
                classLines.line("CameraEx nested classes: " + nested.size());
                for (String n : nested)
                    classLines.line(n);
            } catch (Throwable t) {
                classLines.fail("CameraEx.getClasses", t);
            }
        }

        Set<String> present = new TreeSet<String>();
        for (Class<?> c : classes.values()) {
            try {
                present.addAll(CapsFormat.methodKeys(c));
            } catch (Throwable t) {
                classLines.fail(c.getName() + ".getMethods", t);
            }
        }
        out.section("Used methods (app calls today) vs this firmware");
        out.lines(CapsFormat.usedReport(USED_METHODS, present));

        out.section("Classes");
        out.sb.append(classLines.sb);
        out.lines += classLines.lines;

        try {
            Class<?> si = classes.get(SCALAR_INPUT);
            if (si != null) {
                Map<Integer, String> isv = CapsFormat.constantNames(si, "ISV_");
                out.section("ScalarInput ISV_* constants (" + isv.size() + ")");
                for (Map.Entry<Integer, String> e : isv.entrySet())
                    out.line(e.getKey() + " " + e.getValue());
            }
        } catch (Throwable t) {
            out.fail("ScalarInput constants", t);
        }

        dumpValues(out);

        for (Map.Entry<String, Class<?>> e : classes.entrySet()) {
            try {
                List<Method> methods = CapsFormat.methods(e.getValue());
                out.section("Methods " + e.getKey() + " (" + methods.size() + ")");
                for (Method m : methods)
                    out.line(CapsFormat.signature(m));
            } catch (Throwable t) {
                out.fail("Methods " + e.getKey(), t);
            }
        }

        for (String name : CONSTANT_CLASSES) {
            Class<?> c = classes.get(name);
            if (c == null)
                continue;
            try {
                List<String> constants = CapsFormat.constants(c);
                out.section("Constants " + name + " (" + constants.size() + ")");
                out.lines(constants);
            } catch (Throwable t) {
                out.fail("Constants " + name, t);
            }
        }

        out.section("Key log: seconds since start, action, keyCode, scanCode, ScalarInput name, dial status");
        writeDump(out.sb.toString(), out.lines, CapsFormat.fileSafe(model));
    }

    private static void loadClass(Out out, Map<String, Class<?>> classes, String name) {
        try {
            // initialize=false: enumerate without running the class's static initializer.
            classes.put(name, Class.forName(name, false, CapsDumpActivity.class.getClassLoader()));
            out.line("present  " + name);
        } catch (Throwable t) {
            out.line("ABSENT   " + name + " (" + CapsFormat.failure(t) + ")");
        }
    }

    private static void probeByName(Out out, String prefix, Object target, String name) {
        try {
            out.line(prefix + CapsFormat.probe(target, target.getClass().getMethod(name)));
        } catch (Throwable t) {
            out.fail(prefix + name, t);
        }
    }

    // Opens and releases CameraEx the way ShootActivity does, and only reads values: no setParameters, no set* on the modifier.
    private void dumpValues(Out out) {
        CameraEx cameraEx;
        try {
            cameraEx = CameraEx.open(0, null);
        } catch (Throwable t) {
            out.section("Values");
            out.fail("CameraEx.open", t);
            return;
        }
        if (cameraEx == null) {
            out.section("Values");
            out.line("CameraEx.open returned null");
            return;
        }
        try {
            out.section("Values CameraEx");
            for (String name : CAMERA_EX_GETTERS)
                probeByName(out, "", cameraEx, name);

            Camera.Parameters params = null;
            try {
                params = cameraEx.getNormalCamera().getParameters();
            } catch (Throwable t) {
                out.fail("getNormalCamera().getParameters", t);
            }
            if (params != null) {
                out.section("Values CameraEx$ParametersModifier on getNormalCamera().getParameters()");
                try {
                    out.lines(CapsFormat.probeGetters(cameraEx.createParametersModifier(params)));
                } catch (Throwable t) {
                    out.fail("createParametersModifier", t);
                }
                out.section("Values Camera$Parameters");
                try {
                    out.lines(CapsFormat.probeGetters(params));
                } catch (Throwable t) {
                    out.fail("Camera$Parameters getters", t);
                }
                out.section("Camera$Parameters.flatten() of getNormalCamera().getParameters()");
                try {
                    out.lines(CapsFormat.sortedLines(params.flatten()));
                } catch (Throwable t) {
                    out.fail("flatten", t);
                }
            }

            out.section("Camera$Parameters.flatten() of CameraEx.getSupportedParameters()");
            try {
                Object supported = cameraEx.getClass().getMethod("getSupportedParameters").invoke(cameraEx);
                if (supported instanceof Camera.Parameters)
                    out.lines(CapsFormat.sortedLines(((Camera.Parameters) supported).flatten()));
                else
                    out.line("getSupportedParameters = " + CapsFormat.value(supported));
            } catch (Throwable t) {
                out.fail("getSupportedParameters", t);
            }

            out.section("Values CameraEx$AutoPictureReviewControl (attached as in ShootActivity)");
            try {
                CameraEx.AutoPictureReviewControl review = new CameraEx.AutoPictureReviewControl();
                cameraEx.setAutoPictureReviewControl(review);
                try {
                    out.lines(CapsFormat.probeGetters(review));
                } finally {
                    cameraEx.setAutoPictureReviewControl(null);
                }
            } catch (Throwable t) {
                out.fail("AutoPictureReviewControl", t);
            }
        } finally {
            try {
                cameraEx.release();
            } catch (Throwable t) {
                out.fail("CameraEx.release", t);
            }
        }
    }

    private void writeDump(final String text, final int lines, String model) {
        final File dir = new File(Environment.getExternalStorageDirectory(), "TIMELPS");
        final File primary = new File(dir, "CAPS-" + model + ".txt");
        io.execute(new Runnable() {
            @Override
            public void run() {
                File written = null;
                String error = null;
                try {
                    write(primary, text, false);
                    written = primary;
                } catch (IOException e) {
                    error = primary.getPath() + ": " + CapsFormat.failure(e);
                    // Fall back to an 8.3 name in case the card's filesystem rejects the long one.
                    File fallback = new File(dir, "CAPS.TXT");
                    try {
                        write(fallback, text, false);
                        written = fallback;
                    } catch (IOException e2) {
                        error += "; " + fallback.getPath() + ": " + CapsFormat.failure(e2);
                    }
                }
                capsFile = written;
                if (written != null)
                    Logger.info("CapsDump: wrote " + written.getPath() + " (" + lines + " lines)");
                else
                    Logger.error("CapsDump: " + error);
                final String status = written != null
                        ? "Wrote " + written.getPath() + " (" + lines + " lines)\n" + HINT
                        : "Could not write " + error + "\n" + HINT;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText(status);
                    }
                });
            }
        });
    }
}
