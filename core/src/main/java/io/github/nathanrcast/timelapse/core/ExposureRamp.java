package io.github.nathanrcast.timelapse.core;

import java.util.Locale;

/**
 * Holy-grail ramp decision: given the measured exposure level, the target and where shutter and ISO stand, picks
 * the next step. Pure; the caller owns the counters, the history and the camera. Run it once per scheduler slot.
 *
 * Brightening lengthens the shutter up to the limit (the setting, clamped so the exposure is at most half the
 * interval) and only then raises ISO. Darkening lowers ISO first, then shortens the shutter. A shutter or ISO
 * the caller could not place in the table is {@link #UNKNOWN}: shutter unknown means no brightening (the order
 * cannot be kept) and ISO unknown means shutter-only ramping.
 */
public final class ExposureRamp {
    public static final int UNKNOWN = -1;
    public static final float DEFAULT_MAX_STEP_EV = 1f / 3f;
    private static final float STEP_TOLERANCE_EV = 0.05f;
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

        /** Exact match, else {@link #UNKNOWN} (BULB, off-table). */
        public int shutterIndex(int n, int d) {
            for (int i = 0; i < shutter.length; i++)
                if (shutter[i][0] == n && shutter[i][1] == d)
                    return i;
            return UNKNOWN;
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

        /** The longest table shutter index at or below {@code cap} whose exposure is at most half the interval. */
        public int maxShutterIndex(int cap, double intervalSec) {
            int top = Math.max(0, Math.min(cap, shutter.length - 1));
            while (top > 0 && seconds(top) > intervalSec / 2)
                top--;
            return top;
        }

        public int minIsoIndex() {
            for (int i = 0; i < iso.length; i++)
                if (iso[i] >= MIN_ISO_VALUE)
                    return i;
            return 0;
        }

        double shutterStepEv(int from, int to) {
            return log2(seconds(to) / seconds(from));
        }

        double isoStepEv(int from, int to) {
            return log2((double) iso[to] / iso[from]);
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
        public double intervalSec;
        public Mode mode = Mode.UP;
        public float deadbandEv;
        public int cooldownSlots;
        public int slotsSinceChange;
        public float maxStepEv = DEFAULT_MAX_STEP_EV;
    }

    public static final class Action {

        public final Kind kind;
        public final int steps;
        public final String reason;

        Action(Kind kind, int steps, String reason) {
            this.kind = kind;
            this.steps = steps;
            this.reason = reason;
        }

        public boolean isNone() {
            return kind == Kind.NONE;
        }

        @Override
        public String toString() {
            return kind + (steps > 0 ? " x" + steps : "") + " (" + reason + ")";
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

        /** Mean of the newest {@code count} samples (all of them if fewer); 0 when empty. */
        public float average(int count) {
            int n = Math.min(Math.max(count, 1), size);
            if (n == 0)
                return 0;
            float sum = 0;
            for (int i = 1; i <= n; i++)
                sum += ring[(next - i + ring.length) % ring.length];
            return sum / n;
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
        float cap = in.maxStepEv;
        if (error < 0) {
            if (!in.mode.brightens())
                return none("too dark, brightening off");
            return brighten(in, excess, cap);
        }
        if (!in.mode.darkens())
            return none("too bright, darkening off");
        return darken(in, excess, cap);
    }

    private Action brighten(Input in, float excess, float cap) {
        String why = "dark by " + ev(excess + in.deadbandEv);
        if (in.shutterIndex == UNKNOWN)
            return none(why + ", shutter unknown");
        int limit = tables.maxShutterIndex(in.maxShutterIndex, in.intervalSec);
        if (in.shutterIndex >= tables.shutterCount())
            return none(why + ", shutter index out of table");
        if (in.shutterIndex < limit) {
            int steps = shutterSteps(in.shutterIndex, limit, 1, excess, cap);
            return new Action(Kind.SHUTTER_LONGER, steps, why + ", shutter " + shutter(in.shutterIndex) + " -> "
                    + shutter(in.shutterIndex + steps));
        }
        if (in.isoIndex == UNKNOWN)
            return none(why + ", shutter at max, ISO unknown");
        int isoMax = Math.max(0, Math.min(in.maxIsoIndex, tables.isoCount() - 1));
        if (in.isoIndex < isoMax) {
            int steps = isoSteps(in.isoIndex, isoMax, 1, excess, cap);
            return new Action(Kind.ISO_UP, steps, why + ", shutter at max, ISO " + tables.isoAt(in.isoIndex) + " -> "
                    + tables.isoAt(in.isoIndex + steps));
        }
        return none(why + ", at max shutter and ISO");
    }

    private Action darken(Input in, float excess, float cap) {
        String why = "bright by " + ev(excess + in.deadbandEv);
        int isoMin = tables.minIsoIndex();
        if (in.isoIndex != UNKNOWN && in.isoIndex < tables.isoCount() && in.isoIndex > isoMin) {
            int steps = isoSteps(in.isoIndex, isoMin, -1, excess, cap);
            return new Action(Kind.ISO_DOWN, steps, why + ", ISO " + tables.isoAt(in.isoIndex) + " -> "
                    + tables.isoAt(in.isoIndex - steps));
        }
        if (in.shutterIndex == UNKNOWN)
            return none(why + ", shutter unknown");
        if (in.shutterIndex > 0 && in.shutterIndex < tables.shutterCount()) {
            int steps = shutterSteps(in.shutterIndex, 0, -1, excess, cap);
            return new Action(Kind.SHUTTER_SHORTER, steps, why + (in.isoIndex == UNKNOWN ? ", ISO unknown" : "")
                    + ", shutter " + shutter(in.shutterIndex) + " -> " + shutter(in.shutterIndex - steps));
        }
        return none(why + ", at min ISO and shutter");
    }

    // Steps of one table in one direction: the first always, more while the total stays within the per-slot cap
    // and short of the error.
    private int shutterSteps(int from, int limit, int dir, float excess, float cap) {
        int steps = 1;
        double total = Math.abs(tables.shutterStepEv(from, from + dir));
        while (from + dir * steps != limit && total < excess) {
            double next = Math.abs(tables.shutterStepEv(from + dir * steps, from + dir * (steps + 1)));
            if (total + next > cap + STEP_TOLERANCE_EV)
                break;
            total += next;
            steps++;
        }
        return steps;
    }

    private int isoSteps(int from, int limit, int dir, float excess, float cap) {
        int steps = 1;
        double total = Math.abs(tables.isoStepEv(from, from + dir));
        while (from + dir * steps != limit && total < excess) {
            double next = Math.abs(tables.isoStepEv(from + dir * steps, from + dir * (steps + 1)));
            if (total + next > cap + STEP_TOLERANCE_EV)
                break;
            total += next;
            steps++;
        }
        return steps;
    }

    private String shutter(int index) {
        return Util.formatShutterSpeed(tables.shutterAt(index)[0], tables.shutterAt(index)[1]);
    }

    private static Action none(String reason) {
        return new Action(Kind.NONE, 0, reason);
    }

    private static String ev(float v) {
        return String.format(Locale.US, "%.2f EV", v);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
