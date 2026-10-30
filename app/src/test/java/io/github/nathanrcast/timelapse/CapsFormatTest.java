package io.github.nathanrcast.timelapse;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class CapsFormatTest {
    // Shaped like android.util.Pair: public first/second fields, no toString.
    public static class FakePair {
        public final Object first;
        public final Object second;

        FakePair(Object first, Object second) {
            this.first = first;
            this.second = second;
        }
    }

    // Shaped like CameraEx$LensInfo: public fields, no toString.
    public static class FakeLensInfo {
        public String LensName = "E 16-50mm";
        public boolean FixedAperture;
        public FakePair FocalLength = new FakePair(16, 50);
        public static int IGNORED = 1;
    }

    public enum FakeMode { SINGLE, BURST }

    // Shaped like CameraEx$ParametersModifier: getters mixed with setters and other methods.
    public static class FakeModifier {
        public static final String DRIVE_MODE_BURST = "burst";
        public static final int MAX = 30;
        public static final Object NOT_A_CONSTANT = new Object();

        public List<String> getSupportedDriveModes() { return Arrays.asList("single", "burst"); }
        public boolean isSupportedSilentShutterMode() { throw new NoSuchMethodError("isSupportedSilentShutterMode"); }
        public FakePair getShutterSpeed() { return new FakePair(1, 250); }
        public int[] getRGBMatrix() { return new int[] {1, 2}; }
        public boolean isSilentShutterMode() { return false; }
        public String getNothing() { return null; }
        public void setDriveMode(String mode) {}
        public int getAt(int index) { return index; }
        public void getVoid() {}
        public static int getStatic() { return 0; }
        public void tryIgnoreInhibit() {}
        public int issue() { return 0; }
    }

    public interface FakeListener {
        void onShutter(int status, FakeModifier camera);
    }

    public static class FakeScalarInput {
        public static final int ISV_KEY_UP = 103;
        public static final int ISV_KEY_FOCUS_HOLD = 603;
        public static final int ISV_KEY_ISO_2 = 603;
        public static final int STATUS_ON = 1;
        public static final String ISV_NOT_INT = "x";
    }

    private static List<String> names(List<Method> methods) {
        List<String> out = new ArrayList<String>();
        for (Method m : methods)
            out.add(m.getName());
        return out;
    }

    @Test
    public void valuesPrintReadably() {
        assertEquals("null", CapsFormat.value(null));
        assertEquals("\"burst\"", CapsFormat.value("burst"));
        assertEquals("\"a\\nb\"", CapsFormat.value("a\nb"));
        assertEquals("42", CapsFormat.value(42));
        assertEquals("true", CapsFormat.value(true));
        assertEquals("BURST", CapsFormat.value(FakeMode.BURST));
        assertEquals("[\"single\", \"burst\"]", CapsFormat.value(Arrays.asList("single", "burst")));
        assertEquals("[]", CapsFormat.value(new ArrayList<String>()));
        assertEquals("[1, 2, 3]", CapsFormat.value(new int[] {1, 2, 3}));
        assertEquals("[[1, 2], null]", CapsFormat.value(new Object[] {new int[] {1, 2}, null}));
        assertEquals("(1, 250)", CapsFormat.value(new FakePair(1, 250)));
        assertEquals("[(1, 4000), (30, 1)]",
                CapsFormat.value(Arrays.asList(new FakePair(1, 4000), new FakePair(30, 1))));
        assertEquals("CapsFormatTest$FakeLensInfo{FixedAperture=false, FocalLength=(16, 50), LensName=\"E 16-50mm\"}",
                CapsFormat.value(new FakeLensInfo()));
        assertEquals("[[[[...]]]]", CapsFormat.value(Arrays.asList(Arrays.asList(Arrays.asList(Arrays.asList(Arrays.asList(1)))))));
    }

    @Test
    public void typeNamesDropThePackage() {
        assertEquals("int", CapsFormat.typeName(int.class));
        assertEquals("String", CapsFormat.typeName(String.class));
        assertEquals("int[]", CapsFormat.typeName(int[].class));
        assertEquals("String[][]", CapsFormat.typeName(String[][].class));
        assertEquals("CapsFormatTest$FakePair", CapsFormat.typeName(FakePair.class));
    }

    @Test
    public void signaturesAndKeys() throws Exception {
        Method set = FakeModifier.class.getMethod("setDriveMode", String.class);
        Method stat = FakeModifier.class.getMethod("getStatic");
        Method on = FakeListener.class.getMethod("onShutter", int.class, FakeModifier.class);
        assertEquals("void setDriveMode(String)", CapsFormat.signature(set));
        assertEquals("static int getStatic()", CapsFormat.signature(stat));
        assertEquals("CapsFormatTest$FakeModifier.setDriveMode(String)", CapsFormat.methodKey(FakeModifier.class, set));
        assertEquals("CapsFormatTest$FakeListener.onShutter(int, CapsFormatTest$FakeModifier)",
                CapsFormat.methodKey(FakeListener.class, on));
    }

    @Test
    public void methodsSkipObjectAndSort() {
        List<String> n = names(CapsFormat.methods(FakeModifier.class));
        assertEquals(Arrays.asList("getAt", "getNothing", "getRGBMatrix", "getShutterSpeed", "getStatic",
                "getSupportedDriveModes", "getVoid", "isSilentShutterMode", "isSupportedSilentShutterMode",
                "issue", "setDriveMode", "tryIgnoreInhibit"), n);
    }

    @Test
    public void gettersAreNoArgReadersGroupedSupportedFirst() {
        assertEquals(Arrays.asList("getSupportedDriveModes", "isSupportedSilentShutterMode",
                "getNothing", "getRGBMatrix", "getShutterSpeed", "isSilentShutterMode"),
                names(CapsFormat.getters(FakeModifier.class)));
    }

    @Test
    public void probeCatchesWhatTheGetterThrows() {
        assertEquals(Arrays.asList(
                "getSupportedDriveModes = [\"single\", \"burst\"]",
                "isSupportedSilentShutterMode !! java.lang.NoSuchMethodError: isSupportedSilentShutterMode",
                "getNothing = null",
                "getRGBMatrix = [1, 2]",
                "getShutterSpeed = (1, 250)",
                "isSilentShutterMode = false"),
                CapsFormat.probeGetters(new FakeModifier()));
    }

    @Test
    public void constantsListPrimitivesAndStrings() {
        assertEquals(Arrays.asList("DRIVE_MODE_BURST = \"burst\"", "MAX = 30"), CapsFormat.constants(FakeModifier.class));
    }

    @Test
    public void keyNamesReverseLookup() {
        Map<Integer, String> names = CapsFormat.constantNames(FakeScalarInput.class, "ISV_");
        assertEquals(2, names.size());
        assertEquals("ISV_KEY_UP", CapsFormat.keyName(names, 103));
        assertEquals("ISV_KEY_FOCUS_HOLD|ISV_KEY_ISO_2", CapsFormat.keyName(names, 603));
        assertEquals("?", CapsFormat.keyName(names, 1));
    }

    @Test
    public void keyLines() {
        assertEquals("down", CapsFormat.actionName(CapsFormat.ACTION_DOWN, 0));
        assertEquals("repeat", CapsFormat.actionName(CapsFormat.ACTION_DOWN, 3));
        assertEquals("up", CapsFormat.actionName(CapsFormat.ACTION_UP, 0));
        assertEquals("action2", CapsFormat.actionName(2, 0));
        assertEquals("   12.345 down   key=0   scan=522  ISV_DIAL_KURU_CLOCKWISE status=3",
                CapsFormat.keyLine(12345, "down", 0, 522, "ISV_DIAL_KURU_CLOCKWISE", "status=3"));
        assertEquals("    0.050 up     key=82  scan=514  ISV_KEY_MENU",
                CapsFormat.keyLine(50, "up", 82, 514, "ISV_KEY_MENU", null));
    }

    @Test
    public void usedReportMarksAbsent() {
        String[] used = {"CameraEx.release()", "CameraEx$ParametersModifier.setSilentShutterMode(boolean)"};
        assertEquals(Arrays.asList(
                "present  CameraEx.release()",
                "ABSENT   CameraEx$ParametersModifier.setSilentShutterMode(boolean)",
                "1 of 2 absent"),
                CapsFormat.usedReport(used, new HashSet<String>(Arrays.asList("CameraEx.release()"))));
    }

    @Test
    public void failureUnwrapsInvocationTargetException() {
        Throwable t = new java.lang.reflect.InvocationTargetException(new IllegalStateException("boom"));
        assertEquals("java.lang.IllegalStateException: boom", CapsFormat.failure(t));
        assertEquals("java.lang.NullPointerException", CapsFormat.failure(new NullPointerException()));
    }

    @Test
    public void fileSafeModelNames() {
        assertEquals("ILCE-6500", CapsFormat.fileSafe("ILCE-6500"));
        assertEquals("NEX-5T", CapsFormat.fileSafe(" NEX-5T "));
        assertEquals("a_b_c", CapsFormat.fileSafe("a b/c"));
        assertEquals("unknown", CapsFormat.fileSafe(null));
        assertEquals("unknown", CapsFormat.fileSafe(""));
    }

    @Test
    public void flattenedParametersSortOnePerLine() {
        assertEquals(Arrays.asList("focus-mode=auto", "iso=100", "sony-drive-mode=single"),
                CapsFormat.sortedLines("sony-drive-mode=single;focus-mode=auto;iso=100"));
    }
}
