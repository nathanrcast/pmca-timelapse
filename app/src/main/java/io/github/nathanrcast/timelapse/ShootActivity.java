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
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.sysutil.ScalarInput;

import io.github.nathanrcast.timelapse.camera.Result;
import io.github.nathanrcast.timelapse.camera.SafeCamera;
import io.github.nathanrcast.timelapse.core.camera.BracketPlan;
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.DriveReadback;
import io.github.nathanrcast.timelapse.core.camera.RunOptions;

import io.github.nathanrcast.timelapse.core.AwakeGuard;
import io.github.nathanrcast.timelapse.core.BracketTiming;
import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.ContinuousBracket;
import io.github.nathanrcast.timelapse.core.LenrGuard;
import io.github.nathanrcast.timelapse.core.StartMode;
import io.github.nathanrcast.timelapse.core.StartTime;
import io.github.nathanrcast.timelapse.core.ExposureRamp;
import io.github.nathanrcast.timelapse.core.HudModel;
import io.github.nathanrcast.timelapse.core.RampController;
import io.github.nathanrcast.timelapse.core.Scheduler;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotLogCsv;
import io.github.nathanrcast.timelapse.core.ShotRecord;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;
import io.github.nathanrcast.timelapse.core.WallTime;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class ShootActivity extends BaseActivity implements SurfaceHolder.Callback, SafeCamera.ShutterCallback
{
    private Settings settings;
    private ShotPlan plan;
    private Scheduler scheduler;
    private final Clock clock = Clocks.UPTIME;

    private int shotCount;

    // LOG.CSV: one row per scheduler decision, written on a background thread.
    private ShotLog shotLog;

    // onShutter statuses seen this session: OK, CANCELED, ERROR (CameraEx.ShutterListener.STATUS_*), anything else.
    private final AtomicIntegerArray shutterStatuses = new AtomicIntegerArray(4);
    // Non-null when the body only has continuous bracketing: one press shoots all frames, and onShutter reports each.
    private volatile ContinuousBracket continuous;
    private long bracketShutterMs = -1;
    private String driveNote;

    private TextView tvCount, tvBattery, tvRemaining, tvStatus, tvHud;
    private CardSpace cardSpace;
    private long runStartWallMs;
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

    private static final long END_SCREEN_MS = 2000;
    private static final long KEY_WAKE_MS = 5000;
    private static final long HUD_MS = 1000;
    private static final long BURST_POLL_MS = 500;

    private boolean burstShooting;
    private boolean reviewShowing;
    private boolean finishing;
    private boolean stopping;
    private volatile boolean tornDown;
    // The panel is off (DOFF): nothing is drawn until a key or the next shot turns it on.
    private boolean panelOff;
    // A key woke the panel; automatic DOFF waits until this uptime.
    private long wakeUntilMs;
    private boolean buttonKey;

    private float exposureLevel = 0;
    private boolean isoUnknownLogged;

    private Display display;
    // Auto power-off is suppressed from onResume to teardown(), the only place it is re-enabled.
    private final AwakeGuard awake = new AwakeGuard();

    // One timer, re-armed for each deadline the scheduler hands out.
    private final Handler handler = new Handler();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (tornDown)
                return;
            Scheduler.Decision decision = scheduler.onTimer();
            trackBracket(decision);
            if (decision.action == Scheduler.Action.FIRE) {
                boolean closed = fire(decision);
                if (burstShooting && !closed && !tornDown) {
                    handler.removeCallbacks(burstPollRunnable);
                    handler.postDelayed(burstPollRunnable, BURST_POLL_MS);
                }
            } else {
                logDecision(decision);
            }
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
            leaveEndScreen();
        }
    };

    // The one screen refresh (HUD, count, delay countdown, burst seconds). It idles while the panel is off.
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (tornDown)
                return;
            if (awake.reassertDue(clock.nowMs()))
                setAutoPowerOffMode(false);
            if (!panelOff) {
                if (cardSpace != null)
                    cardSpace.request();
                updateScreen();
            }
            handler.postDelayed(this, HUD_MS);
        }
    };

    // A key woke the display under DOFF; put it back to sleep.
    private final Runnable wakeOffRunnable = new Runnable() {
        @Override
        public void run() {
            if (finishing || tornDown || display == null || !settings.displayOff)
                return;
            long wait = wakeUntilMs - clock.nowMs();
            if (wait > 0)
                handler.postDelayed(this, wait);
            else
                panelOff();
        }
    };

    // Burst drive doesn't call onShutter per frame: while the burst runs, refresh the screen and
    // feed the scheduler a stand-in onShutter. The burst's end comes from its own timer.
    private final Runnable burstPollRunnable = new Runnable() {
        @Override
        public void run() {
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
        tvHud = (TextView) findViewById(R.id.tvHud);

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
        tornDown = false;
        stopping = false;
        display = new Display(getDisplayManager(), this);

        Result<SafeCamera> opened = SafeCamera.open(this, this);
        if (!opened.isOk()) {
            log("camera unavailable: " + opened);
            safeCamera = null;
            tvCount.setText("Camera unavailable");
            return;
        }
        safeCamera = opened.get();
        // Held from here, before the run plan and the scheduler start: the delay/AT countdown, shots, review and
        // DOFF all run inside it. A failed open above leaves the camera free to power off.
        if (awake.acquire(clock.nowMs()))
            setAutoPowerOffMode(false);
        // The run plan is final before configure: capabilities mask, then the LENR decision (which may raise the
        // interval), then configure and the bracket timing, both from that plan.
        RunOptions run = applyCapabilities(safeCamera.capabilities());
        int[] shutter = readShutter();
        applyLenrGuard(run, shutterMs(shutter));
        cameraSurfaceHolder.addCallback(this);
        continuous = run.bracket.continuous() ? new ContinuousBracket(BracketPlan.FRAMES) : null;
        safeCamera.configure(run);
        checkDriveReadback(run);
        startBracketTiming(shutter);

        // Built after applyCapabilities, from the masked plan (BRC3 or burst may be off on this body).
        // A resumed run keeps its slots: the scheduler shifts every remaining deadline by the pause.
        if (scheduler == null) {
            scheduler = new Scheduler(plan, clock, Scheduler.StopOn.CAPTURED);
            WallTime wall = Clocks.wallNow();
            scheduler.start(clock.nowMs() + startDelayMs());
            runStartWallMs = wall.mtimeFloorMs(TimeZone.getDefault().getOffset(wall.epochMs));
            shotLog = new ShotLog(this, DeviceId.read(), appVersion(), wall, clock.nowMs(),
                    ShotLogCsv.settingsJson(settings), LenrGuard.headerValue(lenrAtStart));
        } else {
            // A start fixed on the wall clock (AT) must not slip by the time spent paused.
            scheduler.resume(settings.startMode == StartMode.AT);
        }
        if (scheduler.isDone())
            finishRun();
        else
            armTimer();

        panelOff = false;
        if (settings.displayOff) {
            wakeUntilMs = clock.nowMs() + KEY_WAKE_MS;
            handler.postDelayed(wakeOffRunnable, KEY_WAKE_MS);
        }

        if (settings.displayOff)
            display.arm();

        if (cardSpace == null)
            cardSpace = new CardSpace(runStartWallMs);
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, HUD_MS);

        // applyCapabilities turned HG off if this body lacks any of its methods.
        if (settings.holyGrail) {
            if (controller == null)
                controller = new RampController(ramp, settings, plan.framesPerInterval(), lenrStillOn);
            safeCamera.setExposureListener(exposureCallback);
        }
        updateScreen();
    }

    // How long after now slot 0 is due: a settle after configure() for NOW, the delay setting, or the next hh:mm on
    // the camera clock.
    private long startDelayMs() {
        long atWait = 0;
        if (settings.startMode == StartMode.AT) {
            atWait = StartTime.msUntilNext(Clocks.wallMsOfDay(), settings.atHour, settings.atMinute);
            log("start at " + StartTime.format(settings.atHour, settings.atMinute) + " in " + atWait / 1000 + " s");
        }
        return StartTime.firstSlotOffsetMs(settings.startMode, plan.delayMs(), atWait);
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
        if (sp != null && sp[1] > 0) {
            bracketShutterMs = Util.shutterMs(sp[0], sp[1]);
            bracketTiming.setShutterMs(bracketShutterMs);
        }
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
        StringBuilder text = new StringBuilder();
        String[] notes = {lenrNote, driveNote, bracketWarning};
        for (String note : notes) {
            if (note == null)
                continue;
            if (text.length() > 0)
                text.append('\n');
            text.append(note);
        }
        if (text.length() == 0) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setText(text.toString());
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    // Reads the drive and bracket state back after configure(): setParameters returns normally even when the body
    // ignores a value. A body that kept another drive mode while bracketing was asked for runs unbracketed.
    private void checkDriveReadback(RunOptions run) {
        DriveReadback rb = safeCamera.driveReadback();
        if (rb == null) {
            log("drive after configure: unreadable");
            return;
        }
        log("drive after configure: " + rb.describe() + (run.bracket.active ? " (asked for bracket, "
                + run.bracket.exposureMode + ", period " + run.bracket.periodTenths + ")" : ""));
        driveNote = null;
        if (!run.bracket.active || !rb.bracketRefused())
            return;
        driveNote = Boolean.TRUE.equals(run.silentShutter) ? "Bracketing unavailable with silent shutter"
                : "Bracketing unavailable: camera stayed in " + rb.drive + " drive";
        log("warning: " + driveNote + "; run is unbracketed");
        continuous = null;
        if (scheduler == null) {
            settings.brs = false;
            plan = ShotPlan.of(settings);
            burstShooting = plan.isBurst();
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


    private void updateScreen(){
        if (panelOff || tornDown)
            return;
        long now = clock.nowMs();
        Battery battery = Battery.read(this);
        HudModel hud = HudModel.of(scheduler, now, battery.pct, battery.charging,
                cardSpace == null ? HudModel.UNKNOWN : cardSpace.freeBytes(),
                cardSpace == null ? HudModel.UNKNOWN : cardSpace.avgBytes());
        tvCount.setText(hud.count);
        tvRemaining.setText(hud.remaining);
        tvBattery.setText(hud.battery);
        tvHud.setText("Skipped: " + hud.skips + "   Next: " + hud.next + "\nElapsed: " + hud.elapsed
                + "   Card: " + hud.cardLeft + " left");

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
        if (finishing)
            leaveEndScreen();
        else
            stopRun();
        return true;
    }

    // MENU: stop now, tear everything down, report, and return to the settings screen.
    private void stopRun() {
        if (stopping)
            return;
        stopping = true;
        String summary = scheduler == null ? "Stopped" : "Stopped: " + HudModel.summary(scheduler, clock.nowMs());
        log(summary + " (" + shutterSummary() + ")");
        teardown();
        Toast.makeText(this, summary, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onPause() {
        teardown();
        super.onPause();
        log("on pause");
    }

    // Reached from MENU, onPause (power switch, activity switch) and onDestroy; only the first call does anything
    // until onResume re-arms it.
    private void teardown() {
        if (tornDown)
            return;
        tornDown = true;

        handler.removeCallbacksAndMessages(null);
        if (display != null) {
            try {
                display.turnAutoOff(Display.NO_AUTO_OFF);
                if (display.on())
                    display.disarm();
                display.release();
            } catch (RuntimeException e) {
                log("display: " + e);
            }
        }

        if (scheduler != null) {
            if (scheduler.isCapturing() && safeCamera != null)
                safeCamera.cancelTakePicture();
            logDecision(scheduler.pause());
        }
        if (shotLog != null)
            shotLog.flush();
        stopReview(false);
        finishing = false;

        if (cameraSurfaceHolder != null)
            cameraSurfaceHolder.removeCallback(this);

        stopPreview();
        camera = null;

        if (cardSpace != null) {
            cardSpace.stop();
            cardSpace = null;
        }
        if (safeCamera != null)
            safeCamera.close();

        if (awake.release())
            setAutoPowerOffMode(true);
        // The camera's own power-off can kill the process right after this: get the queued log lines onto the card.
        Logger.flush();
    }

    private void stopPreview() {
        if (camera == null)
            return;
        try {
            camera.stopPreview();
        } catch (RuntimeException e) {
            log("stopPreview: " + e);
        }
    }

    @Override
    protected void onDestroy() {
        teardown();
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

    private boolean shoot(Scheduler.Decision decision) {
        // One ramp per slot, before its first frame: later bracket frames keep the exposure of the set.
        if (controller != null && settings.holyGrail && decision.frame == 0)
            holyGrailSlot(decision.slot);

        ContinuousBracket cont = continuous;
        if (cont != null && decision.frame > 0) {
            // The camera is already shooting this slot's remaining frames from the first press.
            shotCount++;
            updateScreen();
            return true;
        }
        if (cont != null)
            cont.startSlot();
        Result<Void> taken = safeCamera.takePicture();
        if (!taken.isOk()) {
            log("takePicture failed: " + taken);
            return false;
        }

        shotCount++;
        updateScreen();
        return true;
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
        if (tornDown)
            return;
        shutterStatuses.incrementAndGet(status >= 0 && status < 3 ? status : 3);
        ContinuousBracket cont = continuous;
        int n = cont == null ? 0 : cont.onCallback();
        if (cont != null)
            log("onShutter status=" + status + " callback " + n + " of " + BracketPlan.FRAMES);
        else if (status != 0)
            log("shutter_status=" + status);
        // A continuous bracket is one press: cancelling after the first callback would end the other frames.
        if (!burstShooting && (cont == null || cont.complete())) {
            safeCamera.cancelTakePicture();

            //camera.startPreview();
        }
        handler.post(shutterRunnable);
    }

    // "onShutter ok=N canceled=N error=N" for the run summary line.
    private String shutterSummary() {
        return "onShutter ok=" + shutterStatuses.get(0) + " canceled=" + shutterStatuses.get(1) + " error="
                + shutterStatuses.get(2) + (shutterStatuses.get(3) > 0 ? " other=" + shutterStatuses.get(3) : "");
    }

    // The body reported fewer callbacks than frames for a continuous bracket: close the slot with what it gave.
    private final Runnable continuousTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            ContinuousBracket cont = continuous;
            if (tornDown || cont == null || !cont.waiting())
                return;
            int missing = cont.missing();
            log("continuous bracket: " + (BracketPlan.FRAMES - missing) + " of " + BracketPlan.FRAMES
                    + " callbacks, closing the slot");
            safeCamera.cancelTakePicture();
            cont.abandon();
            for (int i = 0; i < missing && !tornDown; i++)
                frameCaptured();
        }
    };

    private void frameCaptured() {
        if (tornDown)
            return;
        armContinuousTimeout();
        Scheduler.Decision decision = scheduler.onFrameCaptured();
        trackBracket(decision);
        if (decision.action == Scheduler.Action.FIRE) {
            if (fire(decision))
                afterSlotClosed();
            return;
        }
        if (decision.action == Scheduler.Action.SLOT_DONE) {
            if (scheduler.isDone())
                finishRun();
            else
                showReview();
        }
        logDecision(decision);
    }

    private void armContinuousTimeout() {
        ContinuousBracket cont = continuous;
        handler.removeCallbacks(continuousTimeoutRunnable);
        if (cont != null && cont.waiting())
            handler.postDelayed(continuousTimeoutRunnable, ContinuousBracket.timeoutMs(bracketShutterMs, cont.missing()));
    }

    // Takes the frame and logs its FIRE row; if the camera refuses it at once, closes the slot as LOST right away
    // instead of at the capture timeout. Returns whether it closed the slot: the caller then finishes or re-arms.
    private boolean fire(Scheduler.Decision decision) {
        boolean taken = takeFrame(decision);
        logDecision(decision);
        if (taken)
            return false;
        Scheduler.Decision failed = scheduler.captureFailed();
        trackBracket(failed);
        logDecision(failed);
        // takeFrame lit the panel; under DOFF a refused frame must not leave it on (panelOff keeps the wake window).
        if (settings.displayOff && !burstShooting)
            panelOff();
        return failed.action == Scheduler.Action.SKIP;
    }

    // After a slot closed outside the timer: end the run or arm the next deadline.
    private void afterSlotClosed() {
        if (scheduler.isDone())
            finishRun();
        else
            armTimer();
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

    private boolean takeFrame(Scheduler.Decision decision) {
        stopReview(true);
        boolean taken = shoot(decision);
        if (!burstShooting)
            panelOn();
        return taken;
    }

    // Shows the last capture until the next deadline, for at most the review setting (default off: no review, and
    // DOFF applies straight away).
    private void showReview() {
        long reviewMs = settings.reviewSeconds * 1000L;
        long until = reviewMs > 0 ? scheduler.reviewUntilMs(reviewMs) : -1;
        if (until < 0) {
            if (settings.displayOff && !burstShooting)
                panelOff();
            return;
        }
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
        stopPreview();
        reviewSurfaceView.setVisibility(View.GONE);
        if (displayOff && settings.displayOff)
            panelOff();
    }

    // The run is over: stop the preview, show a summary for END_SCREEN_MS or until a key, then return to settings.
    private void finishRun() {
        if (finishing)
            return;
        finishing = true;
        log("Done: " + HudModel.summary(scheduler, clock.nowMs()) + " (" + shutterSummary() + ")");
        handler.removeCallbacks(tickRunnable);
        handler.removeCallbacks(burstPollRunnable);
        handler.removeCallbacks(wakeOffRunnable);
        handler.removeCallbacks(stopReviewRunnable);
        if (burstShooting)
            safeCamera.cancelTakePicture();
        reviewShowing = false;
        stopPreview();
        reviewSurfaceView.setVisibility(View.GONE);
        panelOn();
        tvStatus.setText("Done: " + HudModel.summary(scheduler, clock.nowMs()) + ". Press any key.");
        tvStatus.setVisibility(View.VISIBLE);
        updateScreen();
        handler.postDelayed(finishRunnable, END_SCREEN_MS);
    }

    private void leaveEndScreen() {
        if (stopping)
            return;
        stopping = true;
        teardown();
        finish();
    }

    private void panelOn() {
        panelOff = false;
        display.on();
    }

    // Not while a key wake is still running.
    private void panelOff() {
        if (clock.nowMs() < wakeUntilMs)
            return;
        panelOff = true;
        display.off();
    }

    private static boolean isButton(int scanCode) {
        switch (scanCode) {
            case ScalarInput.ISV_KEY_UP:
            case ScalarInput.ISV_KEY_DOWN:
            case ScalarInput.ISV_KEY_LEFT:
            case ScalarInput.ISV_KEY_RIGHT:
            case ScalarInput.ISV_KEY_ENTER:
            case ScalarInput.ISV_KEY_FN:
            case ScalarInput.ISV_KEY_AEL:
            case ScalarInput.ISV_KEY_MENU:
            case ScalarInput.ISV_KEY_SK1:
            case ScalarInput.ISV_KEY_S1_1:
            case ScalarInput.ISV_KEY_S1_2:
            case ScalarInput.ISV_KEY_S2:
            case ScalarInput.ISV_KEY_PLAY:
            case ScalarInput.ISV_KEY_STASTOP:
            case ScalarInput.ISV_KEY_CUSTOM1:
            case ScalarInput.ISV_KEY_DELETE:
            case ScalarInput.ISV_KEY_SK2:
                return true;
            default:
                return false;
        }
    }

    // BaseActivity calls onAnyKeyDown() for dials, the mode dial and lens events too; only buttons count.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        buttonKey = isButton(event.getScanCode());
        return super.onKeyDown(keyCode, event);
    }

    // A button wakes the display and never stops the run (MENU stops it, on key up). Under DOFF the display
    // goes back to sleep after KEY_WAKE_MS. Once the end screen shows, a button leaves it.
    @Override
    protected void onAnyKeyDown() {
        if (!buttonKey || display == null || tornDown)
            return;
        panelOn();
        updateScreen();
        if (finishing) {
            leaveEndScreen();
            return;
        }
        if (settings.displayOff) {
            wakeUntilMs = clock.nowMs() + KEY_WAKE_MS;
            handler.removeCallbacks(wakeOffRunnable);
            handler.postDelayed(wakeOffRunnable, KEY_WAKE_MS);
        }
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
        Logger.info(Clocks.wallNow().format("HH:mm:ss.SSS") + " " + s);
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
