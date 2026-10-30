package io.github.nathanrcast.timelapse.core;

import java.util.ArrayList;
import java.util.List;

// The values the interval, shots and delay seek bars step through.
public final class StepTables {
    // Milliseconds. Index 0 is burst.
    public static final StepTable INTERVAL_MS = new StepTable(
            0, 500, 1000, 2000, 3000, 4000, 5000, 6000, 8000, 10000, 12000, 15000, 20000, 25000,
            30000, 45000, 60000, 90000, 120000, 180000, 300000);

    // 10-100 by 10, 100-1000 by 50, 1000-10000 by 500, then infinite (the last entry).
    public static final StepTable SHOTS = buildShots();

    // Minutes.
    public static final StepTable DELAY_MIN = new StepTable(0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 45, 60);

    private StepTables() {}

    private static StepTable buildShots() {
        List<Long> list = new ArrayList<Long>();
        for (int n = 10; n <= 100; n += 10)
            list.add((long) n);
        for (int n = 150; n <= 1000; n += 50)
            list.add((long) n);
        for (int n = 1500; n <= 10000; n += 500)
            list.add((long) n);
        list.add((long) Settings.SHOTS_INFINITE);
        long[] values = new long[list.size()];
        for (int i = 0; i < values.length; i++)
            values[i] = list.get(i);
        return new StepTable(values);
    }
}
