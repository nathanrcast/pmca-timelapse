package io.github.nathanrcast.timelapse.core.camera;

import java.util.ArrayList;
import java.util.List;

// Every Sony method SafeCamera calls or queries. Types are simple names (CapsFormat.typeName form) so this file names
// no package; the app's camera.ReflectiveCapabilities maps them back to classes. Core methods are the ones a shooting
// session can't run without; every other one is optional and guarded by the probe before each call.
public enum SonyMethod {
    OPEN(true, T.CAMERA_EX, "open", "int", T.CAMERA_EX + "$OpenOptions"),
    RELEASE(true, T.CAMERA_EX, "release"),
    GET_NORMAL_CAMERA(true, T.CAMERA_EX, "getNormalCamera"),
    CREATE_PARAMETERS_MODIFIER(true, T.CAMERA_EX, "createParametersModifier", T.PARAMETERS),
    SET_SHUTTER_LISTENER(true, T.CAMERA_EX, "setShutterListener", T.CAMERA_EX + "$ShutterListener"),
    BURSTABLE_TAKE_PICTURE(true, T.CAMERA_EX, "burstableTakePicture"),
    CANCEL_TAKE_PICTURE(true, T.CAMERA_EX, "cancelTakePicture"),

    CREATE_EMPTY_PARAMETERS(false, T.CAMERA_EX, "createEmptyParameters"),
    SET_AUTO_PICTURE_REVIEW_CONTROL(false, T.CAMERA_EX, "setAutoPictureReviewControl",
            T.CAMERA_EX + "$AutoPictureReviewControl"),
    SET_PROGRAM_LINE_RANGE_OVER_LISTENER(false, T.CAMERA_EX, "setProgramLineRangeOverListener",
            T.CAMERA_EX + "$ProgramLineRangeOverListener"),
    INCREMENT_SHUTTER_SPEED(false, T.CAMERA_EX, "incrementShutterSpeed"),
    DECREMENT_SHUTTER_SPEED(false, T.CAMERA_EX, "decrementShutterSpeed"),
    SET_TOGGLE_FOCUS_MODE(false, T.CAMERA_EX, "setToggleFocusMode", "boolean"),

    SET_DRIVE_MODE(false, T.MODIFIER, "setDriveMode", T.STRING),
    GET_DRIVE_MODE(false, T.MODIFIER, "getDriveMode"),
    GET_SUPPORTED_DRIVE_MODES(false, T.MODIFIER, "getSupportedDriveModes"),
    GET_SUPPORTED_BURST_DRIVE_SPEEDS(false, T.MODIFIER, "getSupportedBurstDriveSpeeds"),
    SET_BURST_DRIVE_SPEED(false, T.MODIFIER, "setBurstDriveSpeed", T.STRING),
    SET_BURST_DRIVE_BUTTON_RELEASE_BEHAVE(false, T.MODIFIER, "setBurstDriveButtonReleaseBehave", T.STRING),
    IS_SUPPORTED_SILENT_SHUTTER_MODE(false, T.MODIFIER, "isSupportedSilentShutterMode"),
    SET_SILENT_SHUTTER_MODE(false, T.MODIFIER, "setSilentShutterMode", "boolean"),
    GET_SUPPORTED_AUTO_EXPOSURE_LOCKS(false, T.MODIFIER, "getSupportedAutoExposureLocks"),
    SET_AUTO_EXPOSURE_LOCK(false, T.MODIFIER, "setAutoExposureLock", T.STRING),
    GET_BRACKET_MODE(false, T.MODIFIER, "getBracketMode"),
    GET_SUPPORTED_BRACKET_MODES(false, T.MODIFIER, "getSupportedBracketModes"),
    SET_BRACKET_MODE(false, T.MODIFIER, "setBracketMode", T.STRING),
    GET_EXPOSURE_BRACKET_MODE(false, T.MODIFIER, "getExposureBracketMode"),
    GET_SUPPORTED_EXPOSURE_BRACKET_MODES(false, T.MODIFIER, "getSupportedExposureBracketModes"),
    SET_EXPOSURE_BRACKET_MODE(false, T.MODIFIER, "setExposureBracketMode", T.STRING),
    GET_EXPOSURE_BRACKET_PERIOD(false, T.MODIFIER, "getExposureBracketPeriod"),
    GET_SUPPORTED_EXPOSURE_BRACKET_PERIODS(false, T.MODIFIER, "getSupportedExposureBracketPeriods"),
    SET_EXPOSURE_BRACKET_PERIOD(false, T.MODIFIER, "setExposureBracketPeriod", "int"),
    GET_SUPPORTED_NUMS_OF_BRACKET_PICTURE(false, T.MODIFIER, "getSupportedNumsOfBracketPicture"),
    GET_NUM_OF_BRACKET_PICTURE(false, T.MODIFIER, "getNumOfBracketPicture"),
    SET_NUM_OF_BRACKET_PICTURE(false, T.MODIFIER, "setNumOfBracketPicture", "int"),
    GET_SHUTTER_SPEED(false, T.MODIFIER, "getShutterSpeed"),
    GET_ISO_SENSITIVITY(false, T.MODIFIER, "getISOSensitivity"),
    SET_ISO_SENSITIVITY(false, T.MODIFIER, "setISOSensitivity", "int"),
    GET_SUPPORTED_ISO_SENSITIVITIES(false, T.MODIFIER, "getSupportedISOSensitivities"),
    IS_SUPPORTED_LONG_EXPOSURE_NR(false, T.MODIFIER, "isSupportedLongExposureNR"),
    GET_LONG_EXPOSURE_NR(false, T.MODIFIER, "getLongExposureNR"),
    SET_LONG_EXPOSURE_NR(false, T.MODIFIER, "setLongExposureNR", "boolean"),
    IS_SUPPORTED_TOGGLE_FOCUS_MODE(false, T.MODIFIER, "isSupportedToggleFocusMode");

    private static final class T {
        static final String CAMERA_EX = "CameraEx";
        static final String MODIFIER = "CameraEx$ParametersModifier";
        static final String PARAMETERS = "Camera$Parameters";
        static final String STRING = "String";
    }

    private final boolean core;
    private final String owner;
    private final String methodName;
    private final String[] parameterTypes;

    SonyMethod(boolean core, String owner, String methodName, String... parameterTypes) {
        this.core = core;
        this.owner = owner;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    public boolean core() {
        return core;
    }

    public String owner() {
        return owner;
    }

    public String methodName() {
        return methodName;
    }

    public String[] parameterTypes() {
        return parameterTypes.clone();
    }

    // "CameraEx.open(int, CameraEx$OpenOptions)": CapsFormat.methodKey form, as listed in a CAPS file.
    public String key() {
        StringBuilder sb = new StringBuilder(owner).append('.').append(methodName).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(parameterTypes[i]);
        }
        return sb.append(')').toString();
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<String>();
        for (SonyMethod m : values())
            keys.add(m.key());
        return keys;
    }
}
