package io.github.nathanrcast.timelapse.camera;

import static io.github.nathanrcast.timelapse.core.camera.SonyMethod.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Build;
import android.util.Pair;

import com.github.ma1co.openmemories.framework.DeviceInfo;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.CameraEx.ParametersModifier;

import io.github.nathanrcast.timelapse.Logger;
import io.github.nathanrcast.timelapse.core.LenrGuard;
import io.github.nathanrcast.timelapse.core.camera.BracketPlan;
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.Feature;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;
import io.github.nathanrcast.timelapse.core.camera.ProbedCapabilities;
import io.github.nathanrcast.timelapse.core.camera.Query;
import io.github.nathanrcast.timelapse.core.camera.RunOptions;
import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The only class outside CapsDump that calls CameraEx or ParametersModifier (with its bridges in this package).
// Method presence is resolved once per process; the supported values are read again at every open(), so a run is
// masked against the mode the body is in now. Every optional call is guarded: by caps.has() here, or for
// configure() by the RunOptions mask its caller applied. Every call also catches Throwable and returns a Result,
// and each failure is written to LOG.TXT once per session, not once per shot.
public final class SafeCamera {
    public interface ShutterCallback {
        void onShutter(int status);
    }

    public interface ExposureCallback {
        // Metered exposure in 1/3 EV steps (CameraEx.ProgramLineRangeOverListener.onEVRange).
        void onEvRange(int ev);
    }

    private static final String FOCUS_MODE_AUTO = "auto";
    private static final String PREFS = "safe_camera";
    // Set while the app holds the AF/MF toggle, so a run that never reached close() (crash, reboot, kill) can be
    // released by the next open().
    private static final String KEY_TOGGLE_ENGAGED = "toggleFocusEngagedByApp";
    // Set while the app holds long-exposure NR off for a run, so a run that never reached close() can be undone by
    // the next open(). Only ever set when LENR was on, so "set" means "turn it back on".
    private static final String KEY_LENR_OFF_BY_APP = "lenrOffByApp";
    // The body's drive and bracket state from before configure() changed it, "drive|bracketMode|exposureBracketMode|
    // count|period" with an empty field for a value that couldn't be read. Set while a run is active, so close() or,
    // after a crash, the next open() puts the user's own state back.
    private static final String KEY_ORIGINAL_DRIVE = "originalDriveState";
    // The settings-screen probe tries twice at most: once, then once more on the next onResume.
    private static final int PROBE_ATTEMPTS = 2;

    private static Map<SonyMethod, Method> methods;
    // The latest capabilities with values: from the settings-screen probe or the last open().
    private static ProbedCapabilities latest;
    private static int failedProbes;

    private final CameraEx cameraEx;
    private final Camera normal;
    private final ProbedCapabilities caps;
    private final SharedPreferences prefs;
    private final Set<String> reported = new HashSet<String>();
    private CameraEx.AutoPictureReviewControl reviewControl;
    private boolean toggleEngaged;
    private boolean lenrDisabled;
    private String savedFocusMode;
    private boolean released;

    private SafeCamera(CameraEx cameraEx, Camera normal, ProbedCapabilities caps, SharedPreferences prefs) {
        this.cameraEx = cameraEx;
        this.normal = normal;
        this.caps = caps;
        this.prefs = prefs;
    }

    // The latest capabilities, or null before the first probe or open() finished.
    public static synchronized Capabilities lastCapabilities() {
        return latest;
    }

    // True until a probe has read the values, or it has failed PROBE_ATTEMPTS times.
    public static synchronized boolean probeWanted() {
        return latest == null || (!latest.valuesRead() && failedProbes < PROBE_ATTEMPTS);
    }

    // The settings-screen probe; blocks, so call it from a Looper thread other than the UI thread. Opens the camera,
    // reads the values and releases it. A probe that couldn't read the values counts as failed.
    public static synchronized Capabilities probe() {
        if (!probeWanted())
            return latest;
        Map<SonyMethod, Method> m = methods();
        CameraEx cam = null;
        if (m.containsKey(OPEN) && m.containsKey(RELEASE)) {
            try {
                cam = CameraEx.open(0, null);
            } catch (Throwable t) {
                Logger.error("SafeCamera: probe: " + Result.failed(OPEN.key(), t));
            }
        }
        try {
            ProbedCapabilities probed = readSession(m, cam);
            if (!probed.valuesRead())
                failedProbes++;
            return probed;
        } finally {
            if (cam != null) {
                try {
                    cam.release();
                } catch (Throwable t) {
                    Logger.error("SafeCamera: probe: " + Result.failed(RELEASE.key(), t));
                }
            }
        }
    }

    // Opens the camera for a shooting session and reads this session's values. Fails (without throwing) if a core
    // method is missing or open fails. A toggle focus left on by an earlier run is released first.
    public static synchronized Result<SafeCamera> open(Context context, ShutterCallback callback) {
        Map<SonyMethod, Method> m = methods();
        for (SonyMethod c : SonyMethod.values())
            if (c.core() && !m.containsKey(c))
                return logged(Result.<SafeCamera>unsupported(c.key()));

        CameraEx cam;
        try {
            cam = CameraEx.open(0, null);
        } catch (Throwable t) {
            return logged(Result.<SafeCamera>failed(OPEN.key(), t));
        }
        if (cam == null)
            return logged(Result.<SafeCamera>failed(OPEN.key(), "returned null"));

        SafeCamera camera;
        try {
            Camera normal = cam.getNormalCamera();
            if (normal == null)
                throw new IllegalStateException("getNormalCamera() returned null");
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            // Before the values are read: a run that never reached close() left its bracket drive on the body.
            restoreDriveState(cam, normal, m.keySet(), prefs, "an earlier run");
            restoreStaleLenr(cam, normal, m.keySet(), prefs);
            camera = new SafeCamera(cam, normal, readSession(m, cam), prefs);
            cam.setShutterListener(new ShutterBridge(callback));
        } catch (Throwable t) {
            try {
                cam.release();
            } catch (Throwable ignored) {
            }
            return logged(Result.<SafeCamera>failed("SafeCamera.open", t));
        }
        camera.releaseStaleToggle();
        camera.enableAutoReview();
        return Result.ok(camera);
    }

    private static Map<SonyMethod, Method> methods() {
        if (methods == null)
            methods = ReflectiveCapabilities.resolve(SafeCamera.class.getClassLoader());
        return methods;
    }

    // This session's capabilities: cached method presence and profile, with the values read from cam now.
    private static ProbedCapabilities readSession(Map<SonyMethod, Method> m, CameraEx cam) {
        Object modifier = null;
        List<String> focusModes = null;
        boolean read = false;
        if (cam != null && m.containsKey(GET_NORMAL_CAMERA) && m.containsKey(CREATE_PARAMETERS_MODIFIER)) {
            try {
                Camera.Parameters params = cam.getNormalCamera().getParameters();
                try {
                    focusModes = params.getSupportedFocusModes();
                } catch (Throwable ignored) {
                }
                modifier = cam.createParametersModifier(params);
                read = true;
            } catch (Throwable t) {
                Logger.error("SafeCamera: read values: " + Result.failed("getParameters", t));
            }
        }
        Map<Query, List<String>> values = ReflectiveCapabilities.readValues(m, modifier, focusModes);
        ProbedCapabilities base = latest != null ? latest
                : ReflectiveCapabilities.build(model(), m.keySet(), new EnumMap<Query, List<String>>(Query.class),
                        false);
        ProbedCapabilities session = base.refreshed(values, read);
        if (latest == null || read)
            latest = session;
        for (String line : session.describe())
            Logger.info("Capabilities: " + line);
        return session;
    }

    private static String model() {
        try {
            String model = DeviceInfo.getInstance().getModel();
            if (model != null && model.trim().length() > 0)
                return model.trim();
        } catch (Throwable ignored) {
        }
        return Build.MODEL;
    }

    private static <T> Result<T> logged(Result<T> r) {
        Logger.error("SafeCamera: " + r);
        return r;
    }

    // ---- session ----

    // The values read when this session opened.
    public Capabilities capabilities() {
        return caps;
    }

    public Camera normalCamera() {
        return normal;
    }

    // Applies drive mode, silent shutter, AEL, bracketing and focus in one setParameters, exactly as the run asks:
    // the caller masked it with RunOptions.mask against capabilities(). Returns setParameters' result. Toggle focus
    // is engaged only after setParameters succeeded.
    public Result<Void> configure(RunOptions run) {
        if (released)
            return closed("configure");
        Camera.Parameters params;
        ParametersModifier modifier;
        try {
            params = normal.getParameters();
            modifier = cameraEx.createParametersModifier(params);
        } catch (Throwable t) {
            return failed("getParameters", t);
        }

        if (run.focus == FocusControl.FOCUS_MODE) {
            savedFocusMode = params.getFocusMode();
            params.setFocusMode(ParametersModifier.FOCUS_MODE_MANUAL);
        } else if (run.focus == FocusControl.NONE && Feature.allows(caps, Query.FOCUS_MODES, FOCUS_MODE_AUTO)) {
            params.setFocusMode(FOCUS_MODE_AUTO);
        }

        saveDriveState();
        if (run.burst)
            configureBurst(modifier);
        else if (!run.bracket.active)
            setDriveMode(modifier, ParametersModifier.DRIVE_MODE_SINGLE);

        if (run.silentShutter != null) {
            try {
                modifier.setSilentShutterMode(run.silentShutter.booleanValue());
            } catch (Throwable t) {
                failed(SET_SILENT_SHUTTER_MODE.key(), t);
            }
        }

        if (run.aeLock) {
            try {
                modifier.setAutoExposureLock(ParametersModifier.AE_LOCK_ON);
            } catch (Throwable t) {
                failed(SET_AUTO_EXPOSURE_LOCK.key(), t);
            }
        }

        if (run.bracket.active)
            configureBracket(modifier, run.bracket);

        Result<Void> applied = apply(params);
        if (applied.isOk() && run.focus == FocusControl.TOGGLE)
            engageToggleFocus();
        return applied;
    }

    // The EV step and picture count are the camera's own menu choice (run.bracket, resolved from the values read at
    // open()). Every bracketed run sets the whole bracket state, so none of it depends on what the body held before:
    // the app fires one frame per press, hence the exposure bracket mode is always single.
    private void configureBracket(ParametersModifier modifier, BracketPlan bracket) {
        Logger.info("SafeCamera: bracket: " + bracket.describe());
        try {
            modifier.setDriveMode(ParametersModifier.DRIVE_MODE_BRACKET);
            modifier.setBracketMode(ParametersModifier.BRACKET_MODE_EXPOSURE);
            modifier.setExposureBracketMode(ParametersModifier.EXPOSURE_BRACKET_MODE_SINGLE);
            modifier.setNumOfBracketPicture(BracketPlan.FRAMES);
            modifier.setExposureBracketPeriod(bracket.periodTenths);
        } catch (Throwable t) {
            failed("bracket", t);
        }
    }

    private String first(Query q) {
        List<String> v = caps.values(q);
        return v == null || v.isEmpty() ? "" : v.get(0);
    }

    // commit(), not apply(): it must be on the card before a crash could follow.
    private void saveDriveState() {
        String state = first(Query.DRIVE_MODE) + "|" + first(Query.BRACKET_MODE) + "|"
                + first(Query.EXPOSURE_BRACKET_MODE) + "|" + first(Query.NUM_OF_BRACKET_PICTURE) + "|"
                + first(Query.EXPOSURE_BRACKET_PERIOD);
        try {
            prefs.edit().putString(KEY_ORIGINAL_DRIVE, state).commit();
        } catch (Throwable t) {
            failed("save " + KEY_ORIGINAL_DRIVE, t);
        }
    }

    // Puts the saved drive and bracket state back (drive mode last) and clears it once setParameters took it.
    private static void restoreDriveState(CameraEx cam, Camera normal, Set<SonyMethod> present,
                                          SharedPreferences prefs, String origin) {
        String saved = prefs.getString(KEY_ORIGINAL_DRIVE, null);
        if (saved == null)
            return;
        String[] f = (saved + "|x").split("\\|");
        try {
            Camera.Parameters params = normal.getParameters();
            ParametersModifier mod = cam.createParametersModifier(params);
            if (f[1].length() > 0 && present.contains(SET_BRACKET_MODE))
                mod.setBracketMode(f[1]);
            if (f[2].length() > 0 && present.contains(SET_EXPOSURE_BRACKET_MODE))
                mod.setExposureBracketMode(f[2]);
            if (f[3].length() > 0 && present.contains(SET_NUM_OF_BRACKET_PICTURE))
                mod.setNumOfBracketPicture(Integer.parseInt(f[3]));
            if (f[4].length() > 0 && present.contains(SET_EXPOSURE_BRACKET_PERIOD))
                mod.setExposureBracketPeriod(Integer.parseInt(f[4]));
            if (f[0].length() > 0 && present.contains(SET_DRIVE_MODE))
                mod.setDriveMode(f[0]);
            normal.setParameters(params);
            prefs.edit().remove(KEY_ORIGINAL_DRIVE).commit();
            Logger.info("SafeCamera: restored drive state " + saved + " from " + origin);
        } catch (Throwable t) {
            Logger.error("SafeCamera: restore drive state: " + Result.failed("setParameters", t));
        }
    }

    private void configureBurst(ParametersModifier modifier) {
        try {
            modifier.setDriveMode(ParametersModifier.DRIVE_MODE_BURST);
            List<?> speeds = modifier.getSupportedBurstDriveSpeeds();
            modifier.setBurstDriveSpeed(speeds.get(speeds.size() - 1).toString());
            modifier.setBurstDriveButtonReleaseBehave(ParametersModifier.BURST_DRIVE_BUTTON_RELEASE_BEHAVE_CONTINUE);
        } catch (Throwable t) {
            failed("burst drive", t);
        }
    }

    private void setDriveMode(ParametersModifier modifier, String mode) {
        if (!caps.has(SET_DRIVE_MODE)) {
            unsupported(SET_DRIVE_MODE.key());
            return;
        }
        try {
            modifier.setDriveMode(mode);
        } catch (Throwable t) {
            failed(SET_DRIVE_MODE.key(), t);
        }
    }

    private Result<Void> apply(Camera.Parameters params) {
        try {
            normal.setParameters(params);
            return Result.ok(null);
        } catch (Throwable t) {
            return failed("setParameters", t);
        }
    }

    private String focusMode() {
        try {
            return normal.getParameters().getFocusMode();
        } catch (Throwable t) {
            return null;
        }
    }

    // The firmware has no toggle-state getter, so the prior state is read from the focus mode: if the body is already
    // in manual focus the toggle is left alone, otherwise it is engaged now and released by restoreFocus(). If the
    // toggle call throws, fall back to setFocusMode("manual") with the old mode saved for restoreFocus().
    private void engageToggleFocus() {
        String before = focusMode();
        if (ParametersModifier.FOCUS_MODE_MANUAL.equals(before)) {
            Logger.info("SafeCamera: focus already manual; toggle focus left as set");
            return;
        }
        try {
            cameraEx.setToggleFocusMode(true);
            toggleEngaged = true;
            setToggleFlag(true);
            Logger.info("SafeCamera: toggle focus on (focus mode " + before + " -> " + focusMode() + ")");
            return;
        } catch (Throwable t) {
            failed(SET_TOGGLE_FOCUS_MODE.key(), t);
        }
        if (!caps.supports(Feature.FOCUS_MODE_MANUAL))
            return;
        try {
            Camera.Parameters params = normal.getParameters();
            String saved = params.getFocusMode();
            params.setFocusMode(ParametersModifier.FOCUS_MODE_MANUAL);
            normal.setParameters(params);
            savedFocusMode = saved;
            Logger.info("SafeCamera: toggle focus failed; focus mode " + saved + " -> manual");
        } catch (Throwable t) {
            failed("setFocusMode(manual)", t);
        }
    }

    // Undoes what configure() did to focus: releases the toggle it engaged, or restores the saved focus mode.
    private void restoreFocus() {
        if (released)
            return;
        if (toggleEngaged) {
            toggleEngaged = false;
            try {
                cameraEx.setToggleFocusMode(false);
                setToggleFlag(false);
            } catch (Throwable t) {
                failed(SET_TOGGLE_FOCUS_MODE.key(), t);
            }
        }
        if (savedFocusMode != null) {
            String mode = savedFocusMode;
            savedFocusMode = null;
            try {
                Camera.Parameters params = normal.getParameters();
                params.setFocusMode(mode);
                normal.setParameters(params);
            } catch (Throwable t) {
                failed("restore focus mode", t);
            }
        }
    }

    // A run that engaged the toggle and never reached close() left the flag set: release the toggle before this run
    // reads the focus mode. The flag stays set if the release throws, so the next open() tries again.
    private void releaseStaleToggle() {
        if (!prefs.getBoolean(KEY_TOGGLE_ENGAGED, false))
            return;
        if (!caps.has(SET_TOGGLE_FOCUS_MODE)) {
            setToggleFlag(false);
            return;
        }
        try {
            cameraEx.setToggleFocusMode(false);
            setToggleFlag(false);
            Logger.info("SafeCamera: released toggle focus left on by an earlier run");
        } catch (Throwable t) {
            failed(SET_TOGGLE_FOCUS_MODE.key() + " (stale)", t);
        }
    }

    // commit(), not apply(): the flag must be on the card before a crash could follow.
    private void setToggleFlag(boolean engaged) {
        try {
            prefs.edit().putBoolean(KEY_TOGGLE_ENGAGED, engaged).commit();
        } catch (Throwable t) {
            failed("save " + KEY_TOGGLE_ENGAGED, t);
        }
    }

    // ---- long-exposure NR ----

    // Whether long-exposure NR was on when this session opened (the probe's one read, taken after any stale restore);
    // null if the body can't say. A run that turns it off knows the original from this.
    public Boolean longExposureNr() {
        List<String> v = caps.values(Query.LONG_EXPOSURE_NR);
        if (v == null || v.isEmpty())
            return null;
        return Boolean.valueOf("true".equals(v.get(0)));
    }

    // True if this body can switch long-exposure NR (Feature.SET_LENR).
    public boolean canSetLongExposureNr() {
        return caps.supports(Feature.SET_LENR);
    }

    // Turns long-exposure NR off for the run. The caller read it as on first. A flag in the preferences stays set
    // until close() turns it back on, so a crash or kill is undone by the next open().
    public Result<Void> disableLongExposureNr() {
        if (released)
            return closed(SET_LONG_EXPOSURE_NR.key());
        if (!canSetLongExposureNr())
            return unsupported(SET_LONG_EXPOSURE_NR.key());
        setLenrFlag(true);
        Result<Void> set = setLongExposureNr(false);
        if (set.isOk())
            lenrDisabled = true;
        else
            setLenrFlag(false);
        return set;
    }

    private Result<Void> setLongExposureNr(boolean on) {
        try {
            Camera.Parameters params = normal.getParameters();
            cameraEx.createParametersModifier(params).setLongExposureNR(on);
            normal.setParameters(params);
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(SET_LONG_EXPOSURE_NR.key(), t);
        }
    }

    private void restoreLenr() {
        if (!lenrDisabled)
            return;
        lenrDisabled = false;
        if (setLongExposureNr(true).isOk())
            setLenrFlag(false);
    }

    // A run that turned LENR off and never reached close() left the flag set. Runs before the session's values are
    // read, so those show the restored state. The flag stays set if the restore fails, so the next open() tries again.
    private static void restoreStaleLenr(CameraEx cam, Camera normal, Set<SonyMethod> present,
                                         SharedPreferences prefs) {
        if (!prefs.getBoolean(KEY_LENR_OFF_BY_APP, false))
            return;
        try {
            if (present.contains(SET_LONG_EXPOSURE_NR) && present.contains(GET_LONG_EXPOSURE_NR)) {
                Camera.Parameters params = normal.getParameters();
                ParametersModifier mod = cam.createParametersModifier(params);
                if (LenrGuard.shouldRestore(true, Boolean.valueOf(mod.getLongExposureNR()))) {
                    mod.setLongExposureNR(true);
                    normal.setParameters(params);
                    Logger.info("SafeCamera: long-exposure NR restored after an earlier run");
                }
            }
            prefs.edit().putBoolean(KEY_LENR_OFF_BY_APP, false).commit();
        } catch (Throwable t) {
            Logger.error("SafeCamera: restore long-exposure NR: " + Result.failed(SET_LONG_EXPOSURE_NR.key(), t));
        }
    }

    private void setLenrFlag(boolean set) {
        try {
            prefs.edit().putBoolean(KEY_LENR_OFF_BY_APP, set).commit();
        } catch (Throwable t) {
            failed("save " + KEY_LENR_OFF_BY_APP, t);
        }
    }

    private void enableAutoReview() {
        if (!caps.has(SET_AUTO_PICTURE_REVIEW_CONTROL)) {
            unsupported(SET_AUTO_PICTURE_REVIEW_CONTROL.key());
            return;
        }
        try {
            CameraEx.AutoPictureReviewControl control = new CameraEx.AutoPictureReviewControl();
            cameraEx.setAutoPictureReviewControl(control);
            reviewControl = control;
        } catch (Throwable t) {
            failed(SET_AUTO_PICTURE_REVIEW_CONTROL.key(), t);
        }
    }

    public Result<Void> takePicture() {
        if (released)
            return closed(BURSTABLE_TAKE_PICTURE.key());
        try {
            cameraEx.burstableTakePicture();
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(BURSTABLE_TAKE_PICTURE.key(), t);
        }
    }

    public Result<Void> cancelTakePicture() {
        if (released)
            return closed(CANCEL_TAKE_PICTURE.key());
        try {
            cameraEx.cancelTakePicture();
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(CANCEL_TAKE_PICTURE.key(), t);
        }
    }

    public Result<Void> setExposureListener(ExposureCallback callback) {
        if (released)
            return closed(SET_PROGRAM_LINE_RANGE_OVER_LISTENER.key());
        if (!caps.has(SET_PROGRAM_LINE_RANGE_OVER_LISTENER))
            return unsupported(SET_PROGRAM_LINE_RANGE_OVER_LISTENER.key());
        try {
            cameraEx.setProgramLineRangeOverListener(new ExposureBridge(callback));
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(SET_PROGRAM_LINE_RANGE_OVER_LISTENER.key(), t);
        }
    }

    // {numerator, denominator}, e.g. {1, 250}.
    public Result<int[]> shutterSpeed() {
        if (released)
            return closed(GET_SHUTTER_SPEED.key());
        if (!caps.has(GET_SHUTTER_SPEED))
            return unsupported(GET_SHUTTER_SPEED.key());
        try {
            Pair<?, ?> sp = cameraEx.createParametersModifier(normal.getParameters()).getShutterSpeed();
            return Result.ok(new int[] {((Number) sp.first).intValue(), ((Number) sp.second).intValue()});
        } catch (Throwable t) {
            return failed(GET_SHUTTER_SPEED.key(), t);
        }
    }

    // {shutter numerator, denominator, ISO} from one getParameters() round-trip; a part this body can't read is -1.
    // Fails only when it can read neither.
    public Result<int[]> exposure() {
        if (released)
            return closed(GET_SHUTTER_SPEED.key());
        boolean ss = caps.has(GET_SHUTTER_SPEED);
        boolean iso = caps.has(GET_ISO_SENSITIVITY);
        if (!ss && !iso)
            return unsupported(GET_SHUTTER_SPEED.key());
        try {
            ParametersModifier pm = cameraEx.createParametersModifier(normal.getParameters());
            int[] out = {-1, -1, -1};
            if (ss) {
                Pair<?, ?> sp = pm.getShutterSpeed();
                out[0] = ((Number) sp.first).intValue();
                out[1] = ((Number) sp.second).intValue();
            }
            if (iso)
                out[2] = pm.getISOSensitivity();
            return Result.ok(out);
        } catch (Throwable t) {
            return failed(GET_SHUTTER_SPEED.key(), t);
        }
    }

    public Result<Integer> isoSensitivity() {
        if (released)
            return closed(GET_ISO_SENSITIVITY.key());
        if (!caps.has(GET_ISO_SENSITIVITY))
            return unsupported(GET_ISO_SENSITIVITY.key());
        try {
            return Result.ok(cameraEx.createParametersModifier(normal.getParameters()).getISOSensitivity());
        } catch (Throwable t) {
            return failed(GET_ISO_SENSITIVITY.key(), t);
        }
    }

    // Sets ISO alone, on empty parameters, so the rest of the setup isn't re-applied.
    public Result<Void> setIsoSensitivity(int iso) {
        if (released)
            return closed(SET_ISO_SENSITIVITY.key());
        if (!caps.has(SET_ISO_SENSITIVITY))
            return unsupported(SET_ISO_SENSITIVITY.key());
        if (!caps.has(CREATE_EMPTY_PARAMETERS))
            return unsupported(CREATE_EMPTY_PARAMETERS.key());
        try {
            Camera.Parameters params = cameraEx.createEmptyParameters();
            cameraEx.createParametersModifier(params).setISOSensitivity(iso);
            normal.setParameters(params);
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(SET_ISO_SENSITIVITY.key(), t);
        }
    }

    // One third-stop longer exposure.
    public Result<Void> decrementShutterSpeed() {
        if (released)
            return closed(DECREMENT_SHUTTER_SPEED.key());
        if (!caps.has(DECREMENT_SHUTTER_SPEED))
            return unsupported(DECREMENT_SHUTTER_SPEED.key());
        try {
            cameraEx.decrementShutterSpeed();
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(DECREMENT_SHUTTER_SPEED.key(), t);
        }
    }

    // One third-stop shorter exposure.
    public Result<Void> incrementShutterSpeed() {
        if (released)
            return closed(INCREMENT_SHUTTER_SPEED.key());
        if (!caps.has(INCREMENT_SHUTTER_SPEED))
            return unsupported(INCREMENT_SHUTTER_SPEED.key());
        try {
            cameraEx.incrementShutterSpeed();
            return Result.ok(null);
        } catch (Throwable t) {
            return failed(INCREMENT_SHUTTER_SPEED.key(), t);
        }
    }

    // Restores focus, detaches the review control and releases the camera. Later calls return FAILED.
    public void close() {
        if (released)
            return;
        restoreFocus();
        restoreLenr();
        restoreDriveState(cameraEx, normal, methods().keySet(), prefs, "this run");
        if (reviewControl != null) {
            reviewControl = null;
            try {
                cameraEx.setAutoPictureReviewControl(null);
            } catch (Throwable t) {
                failed(SET_AUTO_PICTURE_REVIEW_CONTROL.key(), t);
            }
        }
        released = true;
        try {
            cameraEx.release();
        } catch (Throwable t) {
            failed(RELEASE.key(), t);
        }
    }

    private <T> Result<T> unsupported(String call) {
        return report(Result.<T>unsupported(call));
    }

    private <T> Result<T> failed(String call, Throwable t) {
        return report(Result.<T>failed(call, t));
    }

    private <T> Result<T> closed(String call) {
        return report(Result.<T>failed(call, "camera released"));
    }

    private <T> Result<T> report(Result<T> r) {
        if (!r.isOk() && reported.add(r.status() + " " + r.call()))
            Logger.error("SafeCamera: " + r);
        return r;
    }
}
