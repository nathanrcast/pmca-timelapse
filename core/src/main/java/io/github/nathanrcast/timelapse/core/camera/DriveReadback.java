package io.github.nathanrcast.timelapse.core.camera;

import java.util.List;
import java.util.Map;

/**
 * The drive and bracket state read back from the body after configure() applied the run. Whether the body took
 * bracket drive is only knowable from here: setParameters returns normally even when the body ignores a value.
 */
public final class DriveReadback {
    public final String drive;
    public final String bracketMode;
    public final String exposureBracketMode;
    public final String count;
    public final String period;

    public DriveReadback(String drive, String bracketMode, String exposureBracketMode, String count, String period) {
        this.drive = drive;
        this.bracketMode = bracketMode;
        this.exposureBracketMode = exposureBracketMode;
        this.count = count;
        this.period = period;
    }

    public static DriveReadback of(Map<Query, List<String>> values) {
        return new DriveReadback(first(values, Query.DRIVE_MODE), first(values, Query.BRACKET_MODE),
                first(values, Query.EXPOSURE_BRACKET_MODE), first(values, Query.NUM_OF_BRACKET_PICTURE),
                first(values, Query.EXPOSURE_BRACKET_PERIOD));
    }

    private static String first(Map<Query, List<String>> values, Query q) {
        List<String> v = values.get(q);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** True when the drive mode could be read and it is not bracket drive. An unreadable drive is not a refusal. */
    public boolean bracketRefused() {
        return drive != null && !Feature.DRIVE_MODE_BRACKET.equals(drive);
    }

    public String describe() {
        return "drive=" + show(drive) + " bracket=" + show(bracketMode) + " exposureBracket="
                + show(exposureBracketMode) + " count=" + show(count) + " period=" + show(period);
    }

    private static String show(String s) {
        return s == null ? "?" : s;
    }
}
