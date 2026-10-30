package io.github.nathanrcast.timelapse.core.camera;

import java.util.List;

// How one run brackets, decided from the body's state as it was when the session opened (SafeCamera restores that
// state on close, so it is the user's own, never the app's leftovers). A run is bracketed when the BRC3 box is on, or
// when the body is already in 3-picture exposure-bracket drive. The EV step is the body's own menu choice (tenths of
// EV); the app falls back to FALLBACK_PERIOD only when it can't read one. A bracketed run always sets the whole
// bracket state explicitly (SafeCamera.configure), so nothing here depends on what the body already holds.
public final class BracketPlan {
    public static final int FRAMES = 3;
    // 0.7 EV, in tenths.
    public static final int FALLBACK_PERIOD = 7;

    private static final BracketPlan OFF = new BracketPlan(false, 0, null, null);

    // Frames per interval follow this: FRAMES when true, one otherwise.
    public final boolean active;
    // Tenths of EV to set; 0 when not active.
    public final int periodTenths;
    // Why the run isn't bracketed although the body is in bracket drive (a count other than 3), or null.
    public final String unsupportedReason;
    // Why the step isn't the camera's own, or why a bracket drive wasn't auto-detected; null otherwise. For the log.
    public final String note;

    private BracketPlan(boolean active, int periodTenths, String unsupportedReason, String note) {
        this.active = active;
        this.periodTenths = periodTenths;
        this.unsupportedReason = unsupportedReason;
        this.note = note;
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
            return new BracketPlan(false, 0, "camera is set to " + count + " bracket pictures, only "
                    + FRAMES + " is supported", null);
        String note = null;
        boolean detected = false;
        if (inBracket && count != null) {
            String mode = first(caps.values(Query.BRACKET_MODE));
            if (Feature.BRACKET_MODE_EXPOSURE.equals(mode))
                detected = true;
            else
                note = "camera is in bracket drive but its bracket mode is "
                        + (mode == null ? "unreadable" : mode) + ", not an exposure bracket: not auto-detected";
        }
        if (!requested && !detected)
            return new BracketPlan(false, 0, null, note);
        return period(caps, note);
    }

    private static BracketPlan period(Capabilities caps, String note) {
        Integer current = parse(first(caps.values(Query.EXPOSURE_BRACKET_PERIOD)));
        if (current == null || current.intValue() <= 0)
            return new BracketPlan(true, FALLBACK_PERIOD, null, join(note, "step unreadable, using "
                    + evText(FALLBACK_PERIOD)));
        List<String> supported = caps.values(Query.EXPOSURE_BRACKET_PERIODS);
        if (supported != null) {
            boolean parsed = !supported.isEmpty();
            boolean found = false;
            for (String s : supported) {
                Integer p = parse(s);
                if (p == null) {
                    parsed = false;
                    break;
                }
                found |= p.intValue() == current.intValue();
            }
            if (parsed && !found)
                return new BracketPlan(true, FALLBACK_PERIOD, null, join(note, "step " + current
                        + " is not in the supported list " + supported + ", using " + evText(FALLBACK_PERIOD)));
        }
        return new BracketPlan(true, current.intValue(), null, note);
    }

    private static String join(String a, String b) {
        return a == null ? b : a + "; " + b;
    }

    // "0.7 EV" for 7.
    public static String evText(int tenths) {
        return (tenths / 10) + "." + (tenths % 10) + " EV";
    }

    // One log line saying what the run does.
    public String describe() {
        String text;
        if (!active)
            text = unsupportedReason != null ? "single frames: " + unsupportedReason : "off";
        else
            text = FRAMES + " frames per interval, step " + evText(periodTenths);
        return note == null ? text : text + " (" + note + ")";
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
