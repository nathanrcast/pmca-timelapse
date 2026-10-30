package io.github.nathanrcast.timelapse.core;

import java.util.ArrayList;
import java.util.List;

// The values the interval, shots and delay seek bars step through.
public final class StepTables {
    // Milliseconds, up to 40 min. Index 0 is burst.
    public static final StepTable INTERVAL_MS = new StepTable(
            0, 500, 1000, 2000, 3000, 4000, 5000, 6000, 8000, 10000, 12000, 15000, 20000, 25000,
            30000, 45000, 60000, 90000, 120000, 180000, 300000, 600000, 900000, 1200000, 1800000, 2400000);

    // 1, 2, 3, 5, then 10-100 by 10, 100-1000 by 50, 1000-10000 by 500, then infinite (the last entry).
    // For a burst the value is the length in seconds.
    public static final StepTable SHOTS = buildShots();

    // Minutes.
    public static final StepTable DELAY_MIN = new StepTable(
            0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440);

    private StepTables() {}

    private static StepTable buildShots() {
        List<Long> list = new ArrayList<Long>();
        list.add(1L);
        list.add(2L);
        list.add(3L);
        list.add(5L);
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
