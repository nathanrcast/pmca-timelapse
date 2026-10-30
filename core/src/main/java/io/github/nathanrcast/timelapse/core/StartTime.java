package io.github.nathanrcast.timelapse.core;

// Wall-clock start (StartMode.AT): how long until the next occurrence of hh:mm. Works on the time of day only, so a
// DST change inside the wait is not accounted for.
public final class StartTime {
    public static final long DAY_MS = 24L * 60 * 60 * 1000;

    private StartTime() {}

    public static long msOfDay(int hour, int minute, int second, int millis) {
        return ((hour * 60L + minute) * 60 + second) * 1000 + millis;
    }

    // Milliseconds from nowMsOfDay until the next hh:mm: 0 at exactly that time, tomorrow's once it has passed.
    public static long msUntilNext(long nowMsOfDay, int hour, int minute) {
        long wait = msOfDay(hour, minute, 0, 0) - nowMsOfDay;
        return wait < 0 ? wait + DAY_MS : wait;
    }

    public static String format(int hour, int minute) {
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }
}
