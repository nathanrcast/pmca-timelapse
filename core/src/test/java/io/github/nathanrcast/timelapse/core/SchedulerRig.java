package io.github.nathanrcast.timelapse.core;

import static io.github.nathanrcast.timelapse.core.Scheduler.Action.FIRE;
import static io.github.nathanrcast.timelapse.core.Scheduler.Action.WAIT;
import static io.github.nathanrcast.timelapse.core.Scheduler.NEVER;

import java.util.ArrayList;
import java.util.List;

import io.github.nathanrcast.timelapse.core.Scheduler.Action;
import io.github.nathanrcast.timelapse.core.Scheduler.Decision;

// Drives a Scheduler the way ShootActivity does: one timer armed for nextWakeMs(), possibly
// late, and the camera's onShutter some time after each FIRE (never, for a lost frame). In
// burst mode it is the poll's fake onShutter every pollMs instead, which the scheduler ignores.
// A capture that ends exactly at a deadline is reported before the timer.
final class SchedulerRig {
    static final long START = 1000000;

    final FakeClock clock = new FakeClock(START);
    final Scheduler scheduler;
    final List<Decision> decisions = new ArrayList<Decision>();

    private long captureMs = 300;
    private long pollMs;
    private int slowFrame = -1;
    private long slowFrameMs;
    private long lateWakeAt = -1;
    private long lateWakeBy;
    private long jitterMax;
    private int lostFrame = -1;
    private int framesFired;
    private long shutterAt = NEVER;
    int polls;
    int pollDecisions;

    SchedulerRig(ShotPlan plan, Scheduler.StopOn stopOn) {
        scheduler = new Scheduler(plan, clock, stopOn);
        scheduler.start();
    }

    SchedulerRig(ShotPlan plan, Scheduler.StopOn stopOn, long captureTimeoutMs) {
        scheduler = new Scheduler(plan, clock, stopOn, captureTimeoutMs);
        scheduler.start();
    }

    // That frame's onShutter never comes.
    SchedulerRig loseFrame(int frame) {
        lostFrame = frame;
        return this;
    }

    SchedulerRig captureMs(long ms) {
        captureMs = ms;
        return this;
    }

    SchedulerRig pollMs(long ms) {
        pollMs = ms;
        return this;
    }

    SchedulerRig slowFrame(int frame, long ms) {
        slowFrame = frame;
        slowFrameMs = ms;
        return this;
    }

    SchedulerRig lateWake(long wake, long byMs) {
        lateWakeAt = wake;
        lateWakeBy = byMs;
        return this;
    }

    SchedulerRig jitterUpToMs(long ms) {
        jitterMax = ms;
        return this;
    }

    void dropPendingShutter() {
        shutterAt = NEVER;
    }

    void run() {
        runUntil(NEVER);
    }

    void runUntil(long untilMs) {
        while (!scheduler.isDone()) {
            long wake = scheduler.nextWakeMs();
            long timerAt = wake == NEVER ? NEVER : Math.max(clock.nowMs(), wake + lateness(wake));
            long next = Math.min(timerAt, shutterAt);
            if (next == NEVER || next > untilMs)
                break;
            clock.set(next);
            if (shutterAt <= timerAt) {
                shutterAt = NEVER;
                Decision d = scheduler.onFrameCaptured();
                if (pollMs > 0) {
                    polls++;
                    if (d.action != WAIT)
                        pollDecisions++;
                }
                record(d);
            } else {
                record(scheduler.onTimer());
            }
        }
        if (untilMs != NEVER && clock.nowMs() < untilMs)
            clock.set(untilMs);
    }

    List<Decision> only(Action action) {
        List<Decision> out = new ArrayList<Decision>();
        for (Decision d : decisions)
            if (d.action == action)
                out.add(d);
        return out;
    }

    Decision last(Action action) {
        List<Decision> all = only(action);
        return all.get(all.size() - 1);
    }

    private void record(Decision d) {
        decisions.add(d);
        if (d.action == FIRE) {
            long ms = pollMs > 0 ? pollMs : framesFired == slowFrame ? slowFrameMs : captureMs;
            shutterAt = framesFired == lostFrame ? NEVER : clock.nowMs() + ms;
            framesFired++;
        } else if (d.action == WAIT && pollMs > 0 && scheduler.isCapturing() && shutterAt == NEVER) {
            shutterAt = clock.nowMs() + pollMs;
        }
    }

    private long lateness(long wake) {
        if (wake == lateWakeAt)
            return lateWakeBy;
        if (jitterMax == 0)
            return 0;
        long h = (wake * 6364136223846793005L + 1442695040888963407L) >>> 33;
        return h % (jitterMax + 1);
    }
}
