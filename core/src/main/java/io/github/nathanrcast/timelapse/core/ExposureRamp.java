package io.github.nathanrcast.timelapse.core;

import java.util.Locale;

/**
 * Holy-grail ramp decision: given the measured exposure level, the target and where shutter and ISO stand, picks
 * the next step. Pure; the caller owns the counters, the history and the camera. Run it once per scheduler slot.
 *
 * Brightening lengthens the shutter up to the limit (the setting, clamped to what the slot can hold, see
 * {@link Tables#maxShutterIndex}) and only then raises ISO. A shutter already past the limit is pulled back first.
 * One table step per call. Darkening lowers ISO first, then shortens the shutter. A shutter or ISO
 * the caller could not place in the table is {@link #UNKNOWN}: shutter unknown means no brightening (the order
 * cannot be kept) and ISO unknown means shutter-only ramping.
 */
public final class ExposureRamp {
    public static final int UNKNOWN = -1;
    private static final double WRITE_MARGIN_SEC = 0.5;
    private static final double SLOT_FILL = 0.8;
    private static final float ISO_MATCH_EV = 1f / 6f;
    private static final int MIN_ISO_VALUE = 100;

    public enum Mode {
        NONE, UP, DOWN, BOTH;

        public static Mode of(boolean allowUp, boolean allowDown) {
            if (allowUp && allowDown)
                return BOTH;
            return allowUp ? UP : allowDown ? DOWN : NONE;
        }

        boolean brightens() {
            return this == UP || this == BOTH;
        }

        boolean darkens() {
            return this == DOWN || this == BOTH;
        }
    }

    public enum Kind { NONE, SHUTTER_LONGER, SHUTTER_SHORTER, ISO_UP, ISO_DOWN }

    /** The shutter and ISO step tables; a camera model can supply its own. */
    public static final class Tables {
        public static final Tables DEFAULT = new Tables(Util.SHUTTER_SPEEDS, Util.ISO_VALUES);

        private final int[][] shutter;
        private final int[] iso;

        public Tables(int[][] shutter, int[] iso) {
            this.shutter = shutter;
            this.iso = iso;
        }

        public int shutterCount() {
            return shutter.length;
        }

        public int isoCount() {
            return iso.length;
        }

        public int[] shutterAt(int index) {
            return shutter[index];
        }

        public int isoAt(int index) {
            return iso[index];
        }

        public double seconds(int index) {
            return (double) shutter[index][0] / shutter[index][1];
        }

        /** Nearest table entry within 1/6 EV, else {@link #UNKNOWN} (BULB, unreadable, far off-table). */
        public int shutterIndex(int n, int d) {
            if (n <= 0 || d <= 0 || (n == 65535 && d == 1))
                return UNKNOWN;
            int best = UNKNOWN;
            double bestEv = ISO_MATCH_EV;
            double sec = (double) n / d;
            for (int i = 0; i < shutter.length; i++) {
                double ev = Math.abs(log2(sec / seconds(i)));
                if (ev <= bestEv) {
                    best = i;
                    bestEv = ev;
                }
            }
            return best;
        }

        /** Nearest table ISO within 1/6 EV, else {@link #UNKNOWN} (AUTO, no match). */
        public int isoIndex(int value) {
            if (value <= 0)
                return UNKNOWN;
            int best = UNKNOWN;
            double bestEv = ISO_MATCH_EV;
            for (int i = 0; i < iso.length; i++) {
                double ev = Math.abs(log2((double) value / iso[i]));
                if (ev <= bestEv) {
                    best = i;
                    bestEv = ev;
                }
            }
            return best;
        }

        /**
         * The longest table shutter index at or below {@code cap} that the slot can hold. Burst (interval 0) is not
         * clamped by time. Otherwise the exposure is at most half the interval, and the slot's frames, each
         * doubled by long-exposure NR if on, plus a write margin per frame fit in 80 % of the interval.
         */
        public int maxShutterIndex(int cap, double intervalSec, int framesPerInterval, boolean lenr) {
            int top = Math.max(0, Math.min(cap, shutter.length - 1));
            if (intervalSec <= 0)
                return top;
            int frames = Math.max(1, framesPerInterval);
            while (top > 0 && !fits(top, intervalSec, frames, lenr))
                top--;
            return top;
        }

        private boolean fits(int index, double intervalSec, int frames, boolean lenr) {
            double exposure = seconds(index);
            return exposure <= intervalSec / 2
                    && frames * exposure * (lenr ? 2 : 1) + frames * WRITE_MARGIN_SEC <= SLOT_FILL * intervalSec;
        }

        public int minIsoIndex() {
            for (int i = 0; i < iso.length; i++)
                if (iso[i] >= MIN_ISO_VALUE)
                    return i;
            return 0;
        }
    }

    public static final class Input {
        public float measuredEv;
        public float targetEv;
        public int shutterIndex = UNKNOWN;
        public int isoIndex = UNKNOWN;
        /** The setting; the interval clamp is applied on top. */
        public int maxShutterIndex;
        public int maxIsoIndex;
        /** 0 = burst: no clamp by time. */
        public double intervalSec;
        public int framesPerInterval = 1;
        public boolean lenr;
        public Mode mode = Mode.UP;
        public float deadbandEv;
        public int cooldownSlots;
        public int slotsSinceChange;
    }

    public static final class Action {
        public final Kind kind;
        public final String reason;

        Action(Kind kind, String reason) {
            this.kind = kind;
            this.reason = reason;
        }

        public boolean isNone() {
            return kind == Kind.NONE;
        }

        @Override
        public String toString() {
            return kind + " (" + reason + ")";
        }
    }

    /** The last N samples, one per slot. */
    public static final class History {
        private final float[] ring;
        private int next;
        private int size;

        public History(int capacity) {
            ring = new float[Math.max(1, capacity)];
        }

        public void add(float ev) {
            ring[next] = ev;
            next = (next + 1) % ring.length;
            if (size < ring.length)
                size++;
        }

        public int size() {
            return size;
        }

        /** Mean of the samples held (at most the capacity); 0 when empty. */
        public float average() {
            if (size == 0)
                return 0;
            float sum = 0;
            for (int i = 0; i < size; i++)
                sum += ring[i];
            return sum / size;
        }
    }

    /** Settings store the target in 1/3-EV steps; the meter reports 1/3-EV steps too. This is the one conversion. */
    public static float thirdsToEv(int thirds) {
        return thirds / 3.0f;
    }

    private final Tables tables;

    public ExposureRamp() {
        this(Tables.DEFAULT);
    }

    public ExposureRamp(Tables tables) {
        this.tables = tables;
    }

    public Tables tables() {
        return tables;
    }

    public Action decide(Input in) {
        float error = in.measuredEv - in.targetEv;
        float excess = Math.abs(error) - in.deadbandEv;
        if (in.mode == Mode.NONE)
            return none("ramp direction off");
        if (excess <= 0)
            return none("within deadband");
        if (in.slotsSinceChange < in.cooldownSlots)
            return none("cooldown " + in.slotsSinceChange + "/" + in.cooldownSlots + " slots");
        int limit = tables.maxShutterIndex(in.maxShutterIndex, in.intervalSec, in.framesPerInterval, in.lenr);
        if (in.shutterIndex > limit && in.shutterIndex < tables.shutterCount())
            return new Action(Kind.SHUTTER_SHORTER, "shutter " + shutter(in.shutterIndex) + " past limit "
                    + shutter(limit) + ", pulling back");
        if (error < 0) {
            if (!in.mode.brightens())
                return none("too dark, brightening off");
            return brighten(in, excess, limit);
        }
        if (!in.mode.darkens())
            return none("too bright, darkening off");
        return darken(in, excess);
    }

    private Action brighten(Input in, float excess, int limit) {
        String why = "dark by " + ev(excess + in.deadbandEv);
        if (in.shutterIndex == UNKNOWN)
            return none(why + ", shutter unknown");
        if (in.shutterIndex >= tables.shutterCount())
            return none(why + ", shutter index out of table");
        if (in.shutterIndex < limit)
            return new Action(Kind.SHUTTER_LONGER, why + ", shutter " + shutter(in.shutterIndex) + " -> "
                    + shutter(in.shutterIndex + 1));
        if (in.isoIndex == UNKNOWN)
            return none(why + ", shutter at max, ISO unknown");
        int isoMax = Math.max(0, Math.min(in.maxIsoIndex, tables.isoCount() - 1));
        if (in.isoIndex < isoMax)
            return new Action(Kind.ISO_UP, why + ", shutter at max, ISO " + tables.isoAt(in.isoIndex) + " -> "
                    + tables.isoAt(in.isoIndex + 1));
        return none(why + ", at max shutter and ISO");
    }

    private Action darken(Input in, float excess) {
        String why = "bright by " + ev(excess + in.deadbandEv);
        int isoMin = tables.minIsoIndex();
        if (in.isoIndex != UNKNOWN && in.isoIndex < tables.isoCount() && in.isoIndex > isoMin)
            return new Action(Kind.ISO_DOWN, why + ", ISO " + tables.isoAt(in.isoIndex) + " -> "
                    + tables.isoAt(in.isoIndex - 1));
        if (in.shutterIndex == UNKNOWN)
            return none(why + ", shutter unknown");
        if (in.shutterIndex > 0 && in.shutterIndex < tables.shutterCount())
            return new Action(Kind.SHUTTER_SHORTER, why + (in.isoIndex == UNKNOWN ? ", ISO unknown" : "")
                    + ", shutter " + shutter(in.shutterIndex) + " -> " + shutter(in.shutterIndex - 1));
        return none(why + ", at min ISO and shutter");
    }

    private String shutter(int index) {
        return Util.formatShutterSpeed(tables.shutterAt(index)[0], tables.shutterAt(index)[1]);
    }

    private static Action none(String reason) {
        return new Action(Kind.NONE, reason);
    }

    private static String ev(float v) {
        return String.format(Locale.US, "%.2f EV", v);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
