package io.github.nathanrcast.timelapse;

import static io.github.nathanrcast.timelapse.core.Util.DEADBAND_VALUES;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;

import io.github.nathanrcast.timelapse.camera.Result;
import io.github.nathanrcast.timelapse.camera.SafeCamera;
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.RunOptions;

import io.github.nathanrcast.timelapse.core.BracketTiming;
import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.LenrGuard;
import io.github.nathanrcast.timelapse.core.StartMode;
import io.github.nathanrcast.timelapse.core.StartTime;
import io.github.nathanrcast.timelapse.core.ExposureRamp;
import io.github.nathanrcast.timelapse.core.Progress;
import io.github.nathanrcast.timelapse.core.RampController;
import io.github.nathanrcast.timelapse.core.Scheduler;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotLogCsv;
import io.github.nathanrcast.timelapse.core.ShotRecord;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShootActivity extends BaseActivity implements SurfaceHolder.Callback, SafeCamera.ShutterCallback
{
    private Settings settings;
    private ShotPlan plan;
    private Scheduler scheduler;
    private final Clock clock = Clocks.UPTIME;

    private int shotCount;

    // LOG.CSV: one row per scheduler decision, written on a background thread.
    private ShotLog shotLog;
    private final SimpleDateFormat logTime = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private TextView tvCount, tvBattery, tvRemaining, tvStatus;
    private BracketTiming bracketTiming;
    private String bracketWarning;
    // LENR guard: the state found at the first start (null: unknown) and the status-line note for this run.
    private Boolean lenrAtStart;
    private boolean lenrChecked;
    private String lenrNote;
    // LENR is on and stays on for the run: the holy-grail ramp then budgets a dark frame per exposure.
    private boolean lenrStillOn;

    //Holy Grail
    private TextView tvExposureLevel;
    private TextView tvAverageExposure;
    private TextView tvTargetExposureLevel;
    private TextView tvShutterspeed;
    private TextView tvMaxShutterspeed;
    private TextView tvLastPictureISO;
    private TextView tvMaxISO;
    private TextView tvShotsSinceLastChange;
    private TextView tvCooldown;
    private TextView tvDeadband;
    //End Holy Grail

    private final ExposureRamp ramp = new ExposureRamp();
    private RampController controller;

    private SurfaceView reviewSurfaceView;
    private SurfaceHolder cameraSurfaceHolder;
    private SafeCamera safeCamera;
    private Camera camera;

    private static final long REVIEW_MS = 2000;
    private static final long BURST_POLL_MS = 500;

    private boolean burstShooting;
    private boolean reviewShowing;
    private boolean finishing;

    private float exposureLevel = 0;
    private boolean isoUnknownLogged;

    private Display display;

    // One timer, re-armed for each deadline the scheduler hands out.
    private final Handler handler = new Handler();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            Scheduler.Decision decision = scheduler.onTimer();
            trackBracket(decision);
            if (decision.action == Scheduler.Action.FIRE) {
                takeFrame(decision);
                if (burstShooting) {
                    handler.removeCallbacks(burstPollRunnable);
                    handler.postDelayed(burstPollRunnable, BURST_POLL_MS);
                }
            }
            logDecision(decision);
            if (scheduler.isDone())
                finishRun();
            else
                armTimer();
        }
    };

    // onShutter's scheduler work, moved onto the main looper so every Scheduler call runs there.
    private final Runnable shutterRunnable = new Runnable() {
        @Override
        public void run() {
            frameCaptured();
        }
    };

    private final Runnable stopReviewRunnable = new Runnable() {
        @Override
        public void run() {
            stopReview(true);
        }
    };

    private final Runnable finishRunnable = new Runnable() {
        @Override
        public void run() {
            display.on();
            onBackPressed();
        }
    };

    private final Runnable delayCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (delaySecondsRemaining() > 0) {
                updateScreen();
                handler.postDelayed(this, 1000);
            }
        }
    };

    // Burst drive doesn't call onShutter per frame: while the burst runs, refresh the screen and
    // feed the scheduler a stand-in onShutter. The burst's end comes from its own timer.
    private final Runnable burstPollRunnable = new Runnable() {
        @Override
        public void run() {
            updateScreen();
            frameCaptured();
            handler.postDelayed(this, BURST_POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate");



        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shoot);

        Intent intent = getIntent();
        settings = SettingsIntent.get(intent);
        // The delay setting only counts in DELAY mode; AT works out its own wait when the run starts.
        if (settings.startMode != StartMode.DELAY)
            settings.delay = 0;
        plan = ShotPlan.of(settings);

        shotCount = 0;
        burstShooting = plan.isBurst();

        tvCount = (TextView) findViewById(R.id.tvCount);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvRemaining = (TextView) findViewById(R.id.tvRemaining);
        tvStatus = (TextView) findViewById(R.id.tvStatus);

        //Holy Grail
        tvExposureLevel = (TextView) findViewById(R.id.tvExposureLevel);
        tvAverageExposure = (TextView) findViewById(R.id.tvAverageExposure);
        tvTargetExposureLevel = (TextView) findViewById(R.id.tvTargetExposureLevel);
        tvShutterspeed = (TextView) findViewById(R.id.tvShutterspeed);
        tvMaxShutterspeed = (TextView) findViewById(R.id.tvMaxShutterspeed);
        tvLastPictureISO = (TextView) findViewById(R.id.tvLastPictureISO);
        tvMaxISO = (TextView) findViewById(R.id.tvMaxISO);
        tvShotsSinceLastChange = (TextView) findViewById(R.id.tvShotsSinceLastChange);
        tvCooldown = (TextView) findViewById(R.id.tvCooldown);
        tvDeadband = (TextView) findViewById(R.id.tvDeadband);
        if (!settings.holyGrail) {
            findViewById(R.id.divider2).setVisibility(View.GONE);
            findViewById(R.id.textView4).setVisibility(View.GONE);
            findViewById(R.id.tlHolyGrail).setVisibility(View.GONE);
        }
        //end Holy Grail

        tvDeadband.setText(DEADBAND_VALUES[settings.deadbandIndex] + " EV");

        reviewSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        reviewSurfaceView.setZOrderOnTop(false);
        cameraSurfaceHolder = reviewSurfaceView.getHolder();
        cameraSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    @Override
    protected void onResume() {
        log("onResume");

        super.onResume();
        display = new Display(getDisplayManager());

        Result<SafeCamera> opened = SafeCamera.open(this, this);
        if (!opened.isOk()) {
            log("camera unavailable: " + opened);
            safeCamera = null;
            tvCount.setText("Camera unavailable");
            return;
        }
        safeCamera = opened.get();
        // The run plan is final before configure: capabilities mask, then the LENR decision (which may raise the
        // interval), then configure and the bracket timing, both from that plan.
        RunOptions run = applyCapabilities(safeCamera.capabilities());
        int[] shutter = readShutter();
        applyLenrGuard(run, shutterMs(shutter));
        cameraSurfaceHolder.addCallback(this);
        safeCamera.configure(run);
        startBracketTiming(shutter);

        // Built after applyCapabilities, from the masked plan (BRC3 or burst may be off on this body).
        // A resumed run keeps its slots: the scheduler shifts every remaining deadline by the pause.
        if (scheduler == null) {
            scheduler = new Scheduler(plan, clock, Scheduler.StopOn.CAPTURED);
            scheduler.start(clock.nowMs() + startDelayMs());
            shotLog = new ShotLog(this, Build.MODEL, Build.VERSION.INCREMENTAL, appVersion(), clock.nowMs(),
                    ShotLogCsv.settingsJson(settings), LenrGuard.headerValue(lenrAtStart));
        } else {
            // A start fixed on the wall clock (AT) must not slip by the time spent paused.
            scheduler.resume(settings.startMode == StartMode.AT);
        }
        if (scheduler.isDone())
            finishRun();
        else
            armTimer();

        if (delaySecondsRemaining() > 0) {
            handler.post(delayCountdownRunnable);
        }

        if(settings.displayOff) {
            display.turnAutoOff(5000);
        }

        setAutoPowerOffMode(false);

        // applyCapabilities turned HG off if this body lacks any of its methods.
        if (settings.holyGrail) {
            if (controller == null)
                controller = new RampController(ramp, settings, plan.framesPerInterval(), lenrStillOn);
            safeCamera.setExposureListener(exposureCallback);
        }
        updateScreen();
    }

    // How long after now the first slot is due: nothing, the delay setting, or the next hh:mm on the camera clock.
    private long startDelayMs() {
        switch (settings.startMode) {
            case DELAY:
                return plan.delayMs();
            case AT:
                Calendar c = Clocks.wallCalendar();
                long wait = StartTime.msUntilNext(StartTime.msOfDay(c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND)),
                        settings.atHour, settings.atMinute);
                log("start at " + StartTime.format(settings.atHour, settings.atMinute) + " in " + wait / 1000 + " s");
                return wait;
            default:
                return 0;
        }
    }

    // The shutter time in ms, read once per resume for the LENR guard and the bracket timing; -1 if unreadable.
    private static long shutterMs(int[] sp) {
        return sp == null ? -1 : Util.shutterMs(sp[0], sp[1]);
    }

    // Long-exposure NR makes the camera shoot a dark frame after every frame of 1 s or more, which loses slots when
    // the interval is shorter than the frames, dark frames and writes take (LenrGuard.minIntervalMs). If the body
    // can switch it off (and "Disable LENR during runs" is on) it is off for the run and SafeCamera restores it on
    // close; else the status line warns and the interval is raised to the minimum. The camera is re-opened on every
    // resume, so this runs each time: on the first start the plan is raised before the scheduler exists, later the
    // running scheduler is raised. Only the header's LENR state is taken once.
    private void applyLenrGuard(RunOptions run, long shutterMs) {
        Boolean lenr = safeCamera.longExposureNr();
        if (!lenrChecked) {
            lenrChecked = true;
            lenrAtStart = lenr;
            log("LENR " + LenrGuard.headerValue(lenr));
        }
        // The holy-grail ramp may reach its max shutter, so that decides whether LENR matters; but the ramp clamps
        // itself to the interval (lenr budgeted), so the interval is only raised for the shutter the camera has now.
        long base = shutterMs;
        long reach = base;
        if (settings.holyGrail) {
            int[] max = ramp.tables().shutterAt(Math.min(settings.maxShutterSpeedIndex,
                    ramp.tables().shutterCount() - 1));
            reach = Math.max(reach, Util.shutterMs(max[0], max[1]));
        }
        int frames = plan.framesPerInterval();
        int period = run.bracket.active ? run.bracket.periodTenths : 0;
        LenrGuard.Action action = LenrGuard.decide(lenr, LenrGuard.longestFrameMs(reach, frames, period),
                safeCamera.canSetLongExposureNr(), settings.disableLenr, plan.isBurst());
        lenrStillOn = Boolean.TRUE.equals(lenr);
        if (action == LenrGuard.Action.TURN_OFF) {
            if (safeCamera.disableLongExposureNr().isOk()) {
                lenrStillOn = false;
                lenrNote = "LENR off for this run";
                showStatus();
                return;
            }
            action = LenrGuard.Action.WARN;
        }
        lenrNote = null;
        if (action == LenrGuard.Action.WARN) {
            long min = LenrGuard.minIntervalMs(lenr, base, frames, period);
            long effective = LenrGuard.effectiveIntervalMs(plan.intervalMs(), lenr, base, frames, period);
            if (effective > plan.intervalMs()) {
                settings.interval = effective / 1000.0;
                plan = ShotPlan.of(settings);
                if (scheduler != null)
                    scheduler.raiseInterval(effective);
                log("LENR on: interval raised to " + effective + " ms");
            }
            lenrNote = "LENR on: min interval " + Util.formatTime((min + 999) / 1000);
        }
        showStatus();
    }

    // The warning is estimated from the shutter time at start and replaced by the measured slot time after each
    // bracket. It only informs: the scheduler skips a slot that is still busy.
    private void startBracketTiming(int[] shutter) {
        BracketTiming previous = bracketTiming;
        bracketTiming = new BracketTiming(plan);
        bracketTiming.inherit(previous);
        updateBracketShutter(shutter);
    }

    // The one camera read of the shutter; null when it can't be read.
    private int[] readShutter() {
        Result<int[]> sp = safeCamera.shutterSpeed();
        return sp.isOk() ? sp.get() : null;
    }

    private void updateBracketShutter(int[] sp) {
        if (bracketTiming == null)
            return;
        if (sp != null && sp[1] > 0)
            bracketTiming.setShutterMs(Util.shutterMs(sp[0], sp[1]));
        showBracketWarning();
    }

    private void trackBracket(Scheduler.Decision decision) {
        if (bracketTiming != null && bracketTiming.onDecision(decision))
            showBracketWarning();
    }

    private void showBracketWarning() {
        String warning = bracketTiming.warning();
        if (warning == null ? bracketWarning != null : !warning.equals(bracketWarning)) {
            bracketWarning = warning;
            if (warning != null)
                log("warning: " + warning);
        }
        showStatus();
    }

    private void showStatus() {
        String text = lenrNote == null ? bracketWarning : bracketWarning == null ? lenrNote : lenrNote + "\n" + bracketWarning;
        if (text == null) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setText(text);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    // Masks this run's settings (the intent's copy; the saved ones are untouched) against the values the camera
    // reported on open. Frames per interval follow BRC3 and burst follows the interval, so the plan is rebuilt.
    private RunOptions applyCapabilities(Capabilities caps) {
        RunOptions run = RunOptions.mask(caps, settings);
        if (!run.masked().isEmpty())
            log("not on this body, off for this run: " + run.masked());
        if (!settings.holyGrail) {
            findViewById(R.id.divider2).setVisibility(View.GONE);
            findViewById(R.id.textView4).setVisibility(View.GONE);
            findViewById(R.id.tlHolyGrail).setVisibility(View.GONE);
        }
        plan = ShotPlan.of(settings);
        burstShooting = plan.isBurst();
        return run;
    }

    // Holy-grail exposure metering
    private final SafeCamera.ExposureCallback exposureCallback = new SafeCamera.ExposureCallback() {
        @Override
        public void onEvRange(int ev) {
            exposureLevel = ExposureRamp.thirdsToEv(ev);
            if (controller != null) {
                float before = controller.targetEv();
                controller.onMeter(exposureLevel);
                if (controller.targetEv() != before)
                    updateScreen();
            }
            tvExposureLevel.setText(String.format("%.2f", exposureLevel));
        }
    };

    private long delaySecondsRemaining() {
        return Progress.delaySecondsRemaining(scheduler, clock.nowMs());
    }

    private void updateScreen(){
        long now = clock.nowMs();
        long delaySeconds = Progress.delaySecondsRemaining(scheduler, now);
        if (delaySeconds > 0)
            tvCount.setText("Delay: " + Util.formatTime(delaySeconds));
        else
            tvCount.setText(Progress.count(scheduler, now));
        tvRemaining.setText(Progress.remaining(scheduler, now));
        tvBattery.setText(getBatteryPercentage());

        //Holy Grail
        if (controller != null) {
            tvAverageExposure.setText(String.format("%.2f", controller.averageEv()));
            tvTargetExposureLevel.setText(String.format("%.2f", controller.targetEv()));
            tvShutterspeed.setText(shutterText(controller.shutterIndex()));
            tvMaxShutterspeed.setText(shutterText(controller.maxShutterLimit()));
            tvLastPictureISO.setText(isoText(controller.isoIndex()));
            tvMaxISO.setText(isoText(Math.min(settings.maxISOIndex, ramp.tables().isoCount() - 1)));
            tvShotsSinceLastChange.setText(Integer.toString(controller.slotsSinceChange()));
        }
        tvCooldown.setText(Integer.toString(settings.cooldown));


    }

    private String shutterText(int index) {
        if (index < 0 || index >= ramp.tables().shutterCount())
            return "?";
        int[] ss = ramp.tables().shutterAt(index);
        return Util.formatShutterSpeed(ss[0], ss[1]);
    }

    private String isoText(int index) {
        return index < 0 || index >= ramp.tables().isoCount() ? "?" : Integer.toString(ramp.tables().isoAt(index));
    }

    @Override
    protected boolean onMenuKeyUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause() {
        if (display != null) {
            display.on();
            display.turnAutoOff(Display.NO_AUTO_OFF);
        }

        super.onPause();

        log("on pause");

        handler.removeCallbacksAndMessages(null);
        if (scheduler != null) {
            logDecision(scheduler.pause());
            if (shotLog != null)
                shotLog.flush();
        }
        stopReview(false);
        finishing = false;

        if(cameraSurfaceHolder == null)
            log("cameraSurfaceHolder == null");
        else {
            cameraSurfaceHolder.removeCallback(this);
        }

        if(camera == null)
            log("camera == null");
        else {
            camera.stopPreview();
            camera = null;
        }

        if(safeCamera == null)
            log("safeCamera == null");
        else
            safeCamera.close();

        setAutoPowerOffMode(true);
    }


    @Override
    protected void onDestroy() {
        if (shotLog != null) {
            shotLog.close();
            shotLog = null;
        }
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = safeCamera.normalCamera();
            camera.setPreviewDisplay(holder);
        }
        catch (IOException e) {}
    }

    private void shoot(Scheduler.Decision decision) {
        // One ramp per slot, before its first frame: later bracket frames keep the exposure of the set.
        if (controller != null && settings.holyGrail && decision.frame == 0)
            holyGrailSlot(decision.slot);

        safeCamera.takePicture();

        shotCount++;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateScreen();
            }
        });
    }

    private void holyGrailSlot(long slot) {
        ExposureRamp.Tables tables = ramp.tables();
        int[] sp = readShutter();
        updateBracketShutter(sp);
        int ssIdx = sp == null ? ExposureRamp.UNKNOWN : tables.shutterIndex(sp[0], sp[1]);
        Result<Integer> isoResult = safeCamera.isoSensitivity();
        int isoIdx = isoResult.isOk() ? tables.isoIndex(isoResult.get()) : ExposureRamp.UNKNOWN;
        if (isoIdx == ExposureRamp.UNKNOWN && !isoUnknownLogged) {
            isoUnknownLogged = true;
            log("HG: ISO " + (isoResult.isOk() ? isoResult.get() : "unreadable") + " not in table, ramping shutter only");
        }

        ExposureRamp.Action action = controller.onSlot(exposureLevel, ssIdx, isoIdx);
        if (action.isNone())
            return;
        log("HG slot " + slot + ": " + action);
        boolean ok = applyRamp(action);
        controller.onApplied(action, ok);
        if (ok && (action.kind == ExposureRamp.Kind.SHUTTER_LONGER || action.kind == ExposureRamp.Kind.SHUTTER_SHORTER))
            updateBracketShutter(tables.shutterAt(controller.shutterIndex()));
    }

    private boolean applyRamp(ExposureRamp.Action action) {
        Result<Void> r;
        switch (action.kind) {
            case SHUTTER_LONGER:
                r = safeCamera.decrementShutterSpeed();
                break;
            case SHUTTER_SHORTER:
                r = safeCamera.incrementShutterSpeed();
                break;
            default:
                r = safeCamera.setIsoSensitivity(controller.isoValueAfter(action));
                break;
        }
        if (!r.isOk())
            log("HG: " + action.kind + " failed: " + r);
        return r.isOk();
    }

    // The camera calls this when a frame is captured, possibly off the main looper.
    @Override
    public void onShutter(int status) {
        if(!burstShooting) {
            safeCamera.cancelTakePicture();

            //camera.startPreview();
        }
        handler.post(shutterRunnable);
    }

    private void frameCaptured() {
        Scheduler.Decision decision = scheduler.onFrameCaptured();
        trackBracket(decision);
        if (decision.action == Scheduler.Action.FIRE) {
            takeFrame(decision);
        } else if (decision.action == Scheduler.Action.SLOT_DONE) {
            if (scheduler.isDone())
                finishRun();
            else
                showReview();
        }
        logDecision(decision);
    }

    // Every non-WAIT decision becomes a LOG.CSV row; LOG.TXT keeps only a lost frame, which is an error.
    private void logDecision(Scheduler.Decision decision) {
        if (decision.action == Scheduler.Action.WAIT || shotLog == null)
            return;
        if (decision.skipReason == Scheduler.SkipReason.LOST)
            log(decision.toString());
        String ss = "", iso = "", ev = "";
        boolean idle = decision.action == Scheduler.Action.SLOT_DONE;
        if (settings.holyGrail && (idle || decision.action == Scheduler.Action.FIRE)) {
            ss = controller == null || controller.shutterIndex() < 0 ? "" : shutterText(controller.shutterIndex());
            iso = controller == null || controller.isoIndex() < 0 ? "" : isoText(controller.isoIndex());
            ev = String.format(Locale.US, "%.2f", exposureLevel);
        } else if (idle && !burstShooting && safeCamera != null) {
            // One read, once the camera is idle: never while a capture or a burst is in flight.
            Result<int[]> exposure = safeCamera.exposure();
            if (exposure.isOk()) {
                int[] e = exposure.get();
                if (e[0] >= 0)
                    ss = Util.formatShutterSpeed(e[0], e[1]);
                if (e[2] >= 0)
                    iso = Integer.toString(e[2]);
            }
        }
        // The writer thread adds battery and free space.
        ShotRecord row = ShotRecord.of(shotLog.runId, decision, ss, iso, ev, ShotRecord.NONE, ShotRecord.NONE);
        if (row != null)
            shotLog.add(row);
        if (scheduler.isDone())
            shotLog.flush();
    }

    private String appVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) {
            return "?";
        }
    }

    private void armTimer() {
        handler.removeCallbacks(tickRunnable);
        long wake = scheduler.nextWakeMs();
        if (wake != Scheduler.NEVER)
            handler.postAtTime(tickRunnable, wake);
    }

    private void takeFrame(Scheduler.Decision decision) {
        stopReview(true);
        shoot(decision);
        if (!burstShooting)
            display.on();
    }

    // Shows the last capture until the next deadline, for at most REVIEW_MS.
    private void showReview() {
        long until = scheduler.reviewUntilMs(REVIEW_MS);
        if (until < 0)
            return;
        reviewSurfaceView.setVisibility(View.VISIBLE);
        reviewShowing = true;
        handler.postAtTime(stopReviewRunnable, until);
    }

    // displayOff: apply DOFF afterwards; not on pause, where the display must stay on.
    private void stopReview(boolean displayOff) {
        handler.removeCallbacks(stopReviewRunnable);
        if (!reviewShowing)
            return;
        reviewShowing = false;
        if (camera != null)
            camera.stopPreview();
        reviewSurfaceView.setVisibility(View.GONE);
        if (displayOff && settings.displayOff)
            display.off();
    }

    private void finishRun() {
        if (finishing)
            return;
        finishing = true;
        handler.removeCallbacks(tickRunnable);
        handler.removeCallbacks(burstPollRunnable);
        if (burstShooting) {
            safeCamera.cancelTakePicture();
            handler.post(finishRunnable);
        } else {
            handler.postDelayed(finishRunnable, REVIEW_MS);
        }
    }

    private String getBatteryPercentage()
    {
        Battery battery = Battery.read(this);
        if (battery.pct < 0)
            return "?";
        return (battery.charging ? "c " : "") + battery.pct + "%";
    }

    @Override
    protected void onAnyKeyDown() {
        // During the start countdown nothing else would turn the screen off again.
        if (settings.displayOff && delaySecondsRemaining() > 0)
            display.on(true);
        else
            display.on();
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void setColorDepth(boolean highQuality)
    {
        super.setColorDepth(false);
    }


    private void log(String s) {
        Logger.info(logTime.format(new Date()) + " " + s);
    }

    private void dumpList(List list, String name) {
        log(name);
        log(": ");
        if (list != null)
        {
            for (Object o : list)
            {
                log(o.toString());
                log(" ");
            }
        }
        else
            log("null");
        log("\n");
    }
}
