package io.github.nathanrcast.timelapse.core;

/**
 * One row of LOG.CSV. Times are {@code Clocks.UPTIME} ms; a field that doesn't apply to the row
 * is {@link #NONE} (numbers) or empty (strings) and is written as an empty CSV field.
 */
public final class ShotRecord {
    public static final int NONE = -1;

    public enum Result { FIRE, SKIP, SLOT_DONE }

    public final String runId;
    public final long slot;
    public final long scheduledMs;
    /** FIRE rows only. */
    public final long actualMs;
    /** SLOT_DONE rows only. */
    public final long captureDoneMs;
    public final Result result;
    /** SKIP rows only: a {@link Scheduler.SkipReason} name. */
    public final String skipReason;
    /** Shutter speed such as {@code 1/250}, or empty when unknown. */
    public final String ss;
    public final String iso;
    /** Holy-grail metered exposure in EV, or empty. */
    public final String ev;
    public final int bracketIdx;
    public final int batteryPct;
    public final long freeMb;

    public ShotRecord(String runId, long slot, long scheduledMs, long actualMs, long captureDoneMs, Result result,
                      String skipReason, String ss, String iso, String ev, int bracketIdx, int batteryPct,
                      long freeMb) {
        this.runId = runId;
        this.slot = slot;
        this.scheduledMs = scheduledMs;
        this.actualMs = actualMs;
        this.captureDoneMs = captureDoneMs;
        this.result = result;
        this.skipReason = skipReason == null ? "" : skipReason;
        this.ss = ss == null ? "" : ss;
        this.iso = iso == null ? "" : iso;
        this.ev = ev == null ? "" : ev;
        this.bracketIdx = bracketIdx;
        this.batteryPct = batteryPct;
        this.freeMb = freeMb;
    }

    /** The row for a scheduler decision, or null for WAIT (which has no row). */
    public static ShotRecord of(String runId, Scheduler.Decision d, String ss, String iso, String ev,
                                int batteryPct, long freeMb) {
        switch (d.action) {
            case FIRE:
                return new ShotRecord(runId, d.slot, d.scheduledMs, d.nowMs, NONE, Result.FIRE, null, ss, iso, ev,
                        d.frame, batteryPct, freeMb);
            case SLOT_DONE:
                return new ShotRecord(runId, d.slot, d.scheduledMs, NONE, d.nowMs, Result.SLOT_DONE, null, ss, iso,
                        ev, d.frame, batteryPct, freeMb);
            case SKIP:
                return new ShotRecord(runId, d.slot, d.scheduledMs, NONE, NONE, Result.SKIP,
                        d.skipReason == null ? null : d.skipReason.name(), ss, iso, ev, d.frame, batteryPct, freeMb);
            default:
                return null;
        }
    }
}
