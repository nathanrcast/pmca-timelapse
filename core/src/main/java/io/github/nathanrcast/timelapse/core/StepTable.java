package io.github.nathanrcast.timelapse.core;

// An ascending table of the values a seek bar can take. The bar's progress is an index into it.
public final class StepTable {
    private final long[] values;

    public StepTable(long... values) {
        if (values.length == 0)
            throw new IllegalArgumentException("empty table");
        for (int i = 1; i < values.length; i++)
            if (values[i] <= values[i - 1])
                throw new IllegalArgumentException("not ascending at " + i);
        this.values = values.clone();
    }

    public int size() {
        return values.length;
    }

    public int maxIndex() {
        return values.length - 1;
    }

    public long value(int index) {
        return values[clamp(index)];
    }

    public int clamp(int index) {
        return Math.max(0, Math.min(values.length - 1, index));
    }

    // Moves delta entries from index and stops at the ends (no wrap).
    public int step(int index, int delta) {
        return clamp(clamp(index) + delta);
    }

    // The entry closest to value; on a tie the lower one.
    public int nearestIndex(long value) {
        int best = 0;
        long bestDistance = Math.abs(value - values[0]);
        for (int i = 1; i < values.length; i++) {
            long distance = Math.abs(value - values[i]);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }
}
