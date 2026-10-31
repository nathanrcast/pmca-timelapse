package io.github.nathanrcast.timelapse.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A wall-clock reading: true epoch milliseconds, the zone's UTC offset at that instant and where it came from.
 * The camera RTC is the only real wall time on the bodies; {@code System.currentTimeMillis()} counts from boot there.
 */
public final class WallTime {
    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_SYSTEM = "system";

    public final long epochMs;
    public final int utcOffsetMs;
    public final String source;

    public WallTime(long epochMs, int utcOffsetMs, String source) {
        this.epochMs = epochMs;
        this.utcOffsetMs = utcOffsetMs;
        this.source = source;
    }

    public boolean fromCamera() {
        return SOURCE_CAMERA.equals(source);
    }

    /** The reading as local wall-clock milliseconds: the epoch plus the zone's offset. */
    public long localMs() {
        return epochMs + utcOffsetMs;
    }

    /**
     * This instant as the value File.lastModified() has for a file written now, given the JVM's own zone offset:
     * the card stores local time (exFAT/FAT), which the VM reads back through its default zone.
     */
    public long fileTimeMs(int jvmOffsetMs) {
        return localMs() - jvmOffsetMs;
    }

    /**
     * The lowest File.lastModified() a file written at or after this reading can show. The card's local time reaches
     * the VM either as the true instant or as local time read in the mount's zone, so the lower of the two is used.
     */
    public long mtimeFloorMs(int jvmOffsetMs) {
        return Math.min(epochMs, fileTimeMs(jvmOffsetMs));
    }

    /** yyyyMMdd-HHmmss in the reading's own (camera local) time. */
    public String runId() {
        return format("yyyyMMdd-HHmmss");
    }

    /** Local time formatted with a SimpleDateFormat pattern. */
    public String format(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("GMT"));
        return f.format(new Date(localMs()));
    }

    /** The offset in whole minutes, as written in the LOG.CSV header. */
    public int utcOffsetMinutes() {
        return utcOffsetMs / 60000;
    }
}
