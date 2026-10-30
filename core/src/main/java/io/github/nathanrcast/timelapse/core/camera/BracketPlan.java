package io.github.nathanrcast.timelapse.core.camera;

import java.util.List;

// How one run brackets, decided from the body's state as it was when the session opened. A run is bracketed when the
// BRC3 box is on, or when the body is already in 3-picture bracket drive. The EV step is the body's own menu choice
// (tenths of EV); the app falls back to FALLBACK_PERIOD only when it can't read one.
public final class BracketPlan {
    public static final int FRAMES = 3;
    // 0.7 EV, in tenths.
    public static final int FALLBACK_PERIOD = 7;

    private static final BracketPlan OFF = new BracketPlan(false, false, 0, null);

    // Frames per interval follow this: FRAMES when true, one otherwise.
    public final boolean active;
    // The body is already in bracket drive: its drive mode and picture count are left as they are.
    public final boolean cameraConfigured;
    // Tenths of EV to set when !cameraConfigured; 0 when not active.
    public final int periodTenths;
    // Why the run isn't bracketed although it could have been (a count other than 3), or null.
    public final String unsupportedReason;

    private BracketPlan(boolean active, boolean cameraConfigured, int periodTenths, String unsupportedReason) {
        this.active = active;
        this.cameraConfigured = cameraConfigured;
        this.periodTenths = periodTenths;
        this.unsupportedReason = unsupportedReason;
    }

    public static BracketPlan off() {
        return OFF;
    }

    // supported: Capabilities.supports(BRACKET_3). requested: the BRC3 box.
    public static BracketPlan resolve(Capabilities caps, boolean supported, boolean requested) {
        if (!supported)
            return OFF;
        boolean inBracket = Feature.DRIVE_MODE_BRACKET.equals(first(caps.values(Query.DRIVE_MODE)));
        Integer count = parse(first(caps.values(Query.NUM_OF_BRACKET_PICTURE)));
        if (inBracket && count != null && count.intValue() != FRAMES)
            return new BracketPlan(false, false, 0, "camera is set to " + count + " bracket pictures, only "
                    + FRAMES + " is supported");
        boolean detected = inBracket && count != null;
        if (!requested && !detected)
            return OFF;
        return new BracketPlan(true, inBracket, period(caps), null);
    }

    private static int period(Capabilities caps) {
        Integer current = parse(first(caps.values(Query.EXPOSURE_BRACKET_PERIOD)));
        if (current == null || current.intValue() <= 0)
            return FALLBACK_PERIOD;
        List<String> supported = caps.values(Query.EXPOSURE_BRACKET_PERIODS);
        if (supported != null && !supported.contains(current.toString()))
            return FALLBACK_PERIOD;
        return current.intValue();
    }

    // "0.7 EV" for 7.
    public static String evText(int tenths) {
        return (tenths / 10) + "." + (tenths % 10) + " EV";
    }

    // One log line saying what the run does.
    public String describe() {
        if (!active)
            return unsupportedReason != null ? "single frames: " + unsupportedReason : "off";
        return FRAMES + " frames per interval, " + (cameraConfigured ? "camera bracket settings kept, " : "set up by the app, ")
                + "step " + evText(periodTenths);
    }

    private static String first(List<String> v) {
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    private static Integer parse(String s) {
        if (s == null)
            return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
