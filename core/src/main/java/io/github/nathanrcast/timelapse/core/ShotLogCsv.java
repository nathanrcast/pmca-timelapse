package io.github.nathanrcast.timelapse.core;

/** Formats LOG.CSV: the column header row, per-run {@code #} comment lines and the rows. */
public final class ShotLogCsv {
    public static final String[] COLUMNS = {
            "run_id", "slot", "scheduled_ms", "actual_ms", "capture_done_ms", "result", "skip_reason", "ss", "iso",
            "ev", "bracket_idx", "battery_pct", "free_mb"
    };

    private ShotLogCsv() {}

    public static String columnHeader() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(COLUMNS[i]);
        }
        return sb.toString();
    }

    /** Quotes a field that holds a comma, quote, CR or LF, doubling embedded quotes (RFC 4180). */
    public static String escape(String s) {
        if (s == null)
            return "";
        boolean quote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                quote = true;
                break;
            }
        }
        if (!quote)
            return s;
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"')
                sb.append('"');
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /** One row, without a line terminator. */
    public static String row(ShotRecord r) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(escape(r.runId)).append(',');
        sb.append(r.slot).append(',');
        sb.append(r.scheduledMs).append(',');
        appendNumber(sb, r.actualMs).append(',');
        appendNumber(sb, r.captureDoneMs).append(',');
        sb.append(r.result).append(',');
        sb.append(escape(r.skipReason)).append(',');
        sb.append(escape(r.ss)).append(',');
        sb.append(escape(r.iso)).append(',');
        sb.append(escape(r.ev)).append(',');
        appendNumber(sb, r.bracketIdx).append(',');
        appendNumber(sb, r.batteryPct).append(',');
        appendNumber(sb, r.freeMb);
        return sb.toString();
    }

    private static StringBuilder appendNumber(StringBuilder sb, long v) {
        return v < 0 ? sb : sb.append(v);
    }

    /**
     * The {@code #} lines that open a run, each ending in a newline. {@code uptimeToWallMs} is
     * wall-clock ms minus uptime ms, so wall = uptime + offset.
     */
    public static String runHeader(String runId, String model, String firmware, String appVersion,
                                   long uptimeToWallMs, String settingsJson) {
        return new StringBuilder()
                .append("# run_id=").append(comment(runId)).append('\n')
                .append("# model=").append(comment(model)).append('\n')
                .append("# firmware=").append(comment(firmware)).append('\n')
                .append("# app_version=").append(comment(appVersion)).append('\n')
                .append("# uptime_to_wall_ms=").append(uptimeToWallMs).append('\n')
                .append("# settings=").append(comment(settingsJson)).append('\n')
                .toString();
    }

    private static String comment(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ');
    }

    /** The run's settings as one JSON object, built by hand from the public fields. */
    public static String settingsJson(Settings s) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"interval\":").append(s.interval);
        sb.append(",\"delay\":").append(s.delay);
        sb.append(",\"shotCount\":").append(s.shotCount == Settings.SHOTS_INFINITE ? "\"infinite\"" : String.valueOf(s.shotCount));
        sb.append(",\"displayOff\":").append(s.displayOff);
        sb.append(",\"silentShutter\":").append(s.silentShutter);
        sb.append(",\"ael\":").append(s.ael);
        sb.append(",\"fps\":").append(s.fps);
        sb.append(",\"brs\":").append(s.brs);
        sb.append(",\"mf\":").append(s.mf);
        sb.append(",\"holyGrail\":").append(s.holyGrail);
        sb.append(",\"useCurrentExposure\":").append(s.useCurrentExposure);
        sb.append(",\"targetExposure\":").append(s.targetExposure);
        sb.append(",\"maxShutterSpeedIndex\":").append(s.maxShutterSpeedIndex);
        sb.append(",\"maxISOIndex\":").append(s.maxISOIndex);
        sb.append(",\"cooldown\":").append(s.cooldown);
        sb.append(",\"averageExposureAmount\":").append(s.averageExposureAmount);
        sb.append(",\"deadbandIndex\":").append(s.deadbandIndex);
        sb.append(",\"holyGrailAllowExposureUp\":").append(s.holyGrailAllowExposureUp);
        sb.append(",\"holyGrailAllowExposureDown\":").append(s.holyGrailAllowExposureDown);
        return sb.append('}').toString();
    }
}
