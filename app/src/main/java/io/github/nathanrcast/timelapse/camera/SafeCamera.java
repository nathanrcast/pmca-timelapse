package io.github.nathanrcast.timelapse.camera;

import static io.github.nathanrcast.timelapse.core.camera.SonyMethod.*;

import android.hardware.Camera;
import android.os.Build;
import android.util.Pair;

import com.github.ma1co.openmemories.framework.DeviceInfo;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.hardware.CameraEx.ParametersModifier;

import io.github.nathanrcast.timelapse.Logger;
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.Feature;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility.FocusControl;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility;
import io.github.nathanrcast.timelapse.core.camera.ProbedCapabilities;
import io.github.nathanrcast.timelapse.core.camera.Query;
import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The only class outside CapsDump that calls CameraEx or ParametersModifier (with its bridges in this package).
// Every optional call is guarded by the session's probe: on a firmware without the method it is never made, so it
// can't throw NoSuchMethodError. Every call also catches Throwable and returns a Result, and each failure is written
// to LOG.TXT once per session, not once per shot.
public final class SafeCamera {
    public interface ShutterCallback {
        void onShutter(int status);
    }

    public interface ExposureCallback {
        // Metered exposure in 1/3 EV steps (CameraEx.ProgramLineRangeOverListener.onEVRange).
        void onEvRange(int ev);
    }

    // What ShootActivity asks for. Options the body lacks are skipped and logged.
    public static final class Setup {
        public boolean burst;
        public boolean silentShutter;
        public boolean aeLock;
        public boolean bracket3;
        public boolean manualFocus;
    }

    private static final String FOCUS_MODE_AUTO = "auto";

    private static Map<SonyMethod, Method> methods;
    private static ProbedCapabilities cached;

    private final CameraEx cameraEx;
    private final Camera normal;
    private final ProbedCapabilities caps;
    private final Set<String> reported = new HashSet<String>();
    private CameraEx.AutoPictureReviewControl reviewControl;
    private boolean toggleEngaged;
    private String savedFocusMode;
    private boolean released;

    private SafeCamera(CameraEx cameraEx, Camera normal, ProbedCapabilities caps) {
        this.cameraEx = cameraEx;
        this.normal = normal;
        this.caps = caps;
    }

    // The session's capabilities. The first call resolves every SonyMethod, then opens the camera to read the
    // supported values and releases it; the result is kept for the rest of the process. If the camera can't be
    // opened the values stay unknown (value-gated options hidden) and the next probe() or open() tries again.
    public static synchronized Capabilities probe() {
        if (cached != null)
            return cached;
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
            return probeWith(m, cam);
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

    // Opens the camera for a shooting session. Fails (without throwing) if a core method is missing or open fails.
    public static synchronized Result<SafeCamera> open(ShutterCallback callback) {
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
            camera = new SafeCamera(cam, normal, cached != null ? cached : probeWith(m, cam));
            cam.setShutterListener(new ShutterBridge(callback));
        } catch (Throwable t) {
            try {
                cam.release();
            } catch (Throwable ignored) {
            }
            return logged(Result.<SafeCamera>failed("SafeCamera.open", t));
        }
        camera.enableAutoReview();
        return Result.ok(camera);
    }

    private static Map<SonyMethod, Method> methods() {
        if (methods == null)
            methods = ReflectiveCapabilities.resolve(SafeCamera.class.getClassLoader());
        return methods;
    }

    private static ProbedCapabilities probeWith(Map<SonyMethod, Method> m, CameraEx cam) {
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
                Logger.error("SafeCamera: probe: " + Result.failed("getParameters", t));
            }
        }
        ProbedCapabilities probed = ReflectiveCapabilities.build(model(), m.keySet(),
                ReflectiveCapabilities.readValues(m, modifier, focusModes), read);
        if (read)
            cached = probed;
        for (String line : probed.describe())
            Logger.info("Capabilities: " + line);
        return probed;
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

    public Capabilities capabilities() {
        return caps;
    }

    public Camera normalCamera() {
        return normal;
    }

    // Applies drive mode, silent shutter, AEL, bracketing and focus in one setParameters, as ShootActivity did.
    // Returns setParameters' result; options the body lacks are skipped and logged once.
    public Result<Void> configure(Setup setup) {
        if (released)
            return closed("configure");
        FocusControl focus = setup.manualFocus ? OptionVisibility.of(caps).focusControl() : FocusControl.NONE;
        if (setup.manualFocus && focus == FocusControl.NONE)
            unsupported("manual focus");

        Camera.Parameters params;
        ParametersModifier modifier;
        try {
            params = normal.getParameters();
            modifier = cameraEx.createParametersModifier(params);
        } catch (Throwable t) {
            return failed("getParameters", t);
        }

        if (focus == FocusControl.FOCUS_MODE) {
            savedFocusMode = params.getFocusMode();
            params.setFocusMode(ParametersModifier.FOCUS_MODE_MANUAL);
        } else if (!setup.manualFocus && Feature.allows(caps, Query.FOCUS_MODES, FOCUS_MODE_AUTO)) {
            params.setFocusMode(FOCUS_MODE_AUTO);
        }

        if (setup.burst)
            configureBurst(modifier);
        else
            setDriveMode(modifier, ParametersModifier.DRIVE_MODE_SINGLE);

        // Set either way when supported, so an earlier run's silent shutter doesn't stick.
        if (caps.supports(Feature.SILENT_SHUTTER)) {
            try {
                modifier.setSilentShutterMode(setup.silentShutter);
            } catch (Throwable t) {
                failed(SET_SILENT_SHUTTER_MODE.key(), t);
            }
        } else if (setup.silentShutter) {
            unsupported(Feature.SILENT_SHUTTER.name());
        }

        if (setup.aeLock) {
            if (!caps.supports(Feature.AE_LOCK)) {
                unsupported(Feature.AE_LOCK.name());
            } else {
                try {
                    modifier.setAutoExposureLock(ParametersModifier.AE_LOCK_ON);
                } catch (Throwable t) {
                    failed(SET_AUTO_EXPOSURE_LOCK.key(), t);
                }
            }
        }

        if (setup.bracket3) {
            if (!caps.supports(Feature.BRACKET_3)) {
                unsupported(Feature.BRACKET_3.name());
            } else {
                try {
                    modifier.setDriveMode(ParametersModifier.DRIVE_MODE_BRACKET);
                    modifier.setBracketMode(ParametersModifier.BRACKET_MODE_EXPOSURE);
                    modifier.setExposureBracketMode(ParametersModifier.EXPOSURE_BRACKET_MODE_SINGLE);
                    modifier.setExposureBracketPeriod(30);
                    modifier.setNumOfBracketPicture(3);
                } catch (Throwable t) {
                    failed("bracket", t);
                }
            }
        }

        Result<Void> applied = apply(params);
        if (focus == FocusControl.TOGGLE)
            engageToggleFocus();
        return applied;
    }

    private void configureBurst(ParametersModifier modifier) {
        if (!caps.supports(Feature.BURST)) {
            unsupported(Feature.BURST.name());
            setDriveMode(modifier, ParametersModifier.DRIVE_MODE_SINGLE);
            return;
        }
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
    public void restoreFocus() {
        if (released)
            return;
        if (toggleEngaged) {
            toggleEngaged = false;
            try {
                cameraEx.setToggleFocusMode(false);
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
