package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartTimeTest {
    private static final long MIN = 60000L;

    private static long now(int h, int m, int s) {
        return StartTime.msOfDay(h, m, s, 0);
    }

    @Test
    public void laterTodayWaitsUntilThen() {
        assertEquals(90 * MIN, StartTime.msUntilNext(now(10, 0, 0), 11, 30));
    }

    @Test
    public void aTimeThatHasPassedTodayRollsToTomorrow() {
        assertEquals(22 * 60 * MIN, StartTime.msUntilNext(now(10, 0, 0), 8, 0));
    }

    @Test
    public void thePassedMinuteRollsEvenByASecond() {
        assertEquals(StartTime.DAY_MS - 20000, StartTime.msUntilNext(now(10, 30, 20), 10, 30));
    }

    @Test
    public void exactlyNowStartsNow() {
        assertEquals(0, StartTime.msUntilNext(now(10, 30, 0), 10, 30));
    }

    @Test
    public void midnightEdges() {
        assertEquals(MIN, StartTime.msUntilNext(now(23, 59, 0), 0, 0));
        assertEquals(StartTime.DAY_MS - MIN, StartTime.msUntilNext(now(0, 1, 0), 0, 0));
        assertEquals(StartTime.DAY_MS - 1, StartTime.msUntilNext(StartTime.msOfDay(0, 0, 0, 1), 0, 0));
    }

    @Test
    public void formatPadsBothFields() {
        assertEquals("07:05", StartTime.format(7, 5));
        assertEquals("23:59", StartTime.format(23, 59));
    }

    @Test
    public void startModeFallsBackOnAnUnknownOrdinal() {
        assertEquals(StartMode.AT, StartMode.fromOrdinal(2, StartMode.NOW));
        assertEquals(StartMode.NOW, StartMode.fromOrdinal(7, StartMode.NOW));
        assertEquals(StartMode.DELAY, StartMode.fromOrdinal(-1, StartMode.DELAY));
    }

    @Test
    public void nowSettlesBeforeSlotZeroAndTheOtherModesDoNot() {
        assertEquals(500, StartTime.firstSlotOffsetMs(StartMode.NOW, 0, 0));
        assertEquals(60000, StartTime.firstSlotOffsetMs(StartMode.DELAY, 60000, 99));
        assertEquals(99, StartTime.firstSlotOffsetMs(StartMode.AT, 60000, 99));
    }

    @Test
    public void theSettleMovesSlotZeroNotTheLaterDeadlines() {
        FakeClock clock = new FakeClock(10000);
        Settings s = new Settings();
        Scheduler sched = new Scheduler(ShotPlan.of(s), clock, Scheduler.StopOn.CAPTURED);
        sched.start(clock.nowMs() + StartTime.firstSlotOffsetMs(StartMode.NOW, 0, 0));
        assertEquals(10500, sched.nextWakeMs());
        clock.set(10500);
        assertEquals(Scheduler.Action.FIRE, sched.onTimer().action);
    }
}
