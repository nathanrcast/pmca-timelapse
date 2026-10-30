package io.github.nathanrcast.timelapse;

import static io.github.nathanrcast.timelapse.core.Util.DEADBAND_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.ISO_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.SHUTTER_SPEEDS;
import static io.github.nathanrcast.timelapse.core.Util.formatShutterSpeedIndex;

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
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.RunOptions;

import io.github.nathanrcast.timelapse.core.BracketTiming;
import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.HudModel;
import io.github.nathanrcast.timelapse.core.Scheduler;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotLogCsv;
import io.github.nathanrcast.timelapse.core.ShotRecord;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    private TextView tvCount, tvBattery, tvRemaining, tvStatus, tvHud;
    private CardSpace cardSpace;
    private long runStartWallMs;
    private BracketTiming bracketTiming;
    private String bracketWarning;

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

    private List<Float> exposureLevelList = new ArrayList<Float>();

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
    private int ISOLevelIndex = 0;
    private int shutterSpeedIndex = 0;
    private int shotsSinceLastChange = 0;
    private float targetExposureLevel = 0.0f;

    private Display display;

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
                takeFrame();
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
            leaveEndScreen();
        }
    };

    // The one screen refresh (HUD, count, delay countdown, burst seconds). It idles while the panel is off.
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (tornDown)
                return;
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

        if (settings.useCurrentExposure){
            targetExposureLevel = exposureLevel;
        } else {
            targetExposureLevel = settings.targetExposure;
        }

        tvDeadband.setText(DEADBAND_VALUES[settings.deadbandIndex] + " EV");

        if (settings.holyGrailAllowExposureUp && !settings.holyGrailAllowExposureDown){
            ISOLevelIndex = 0;
        }

        if (settings.holyGrailAllowExposureDown && !settings.holyGrailAllowExposureUp){
            ISOLevelIndex = ISO_VALUES.length - 1;
        }

        if (settings.holyGrailAllowExposureDown && settings.holyGrailAllowExposureUp){
            ISOLevelIndex = ISO_VALUES.length / 2;
        }

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
        display = new Display(getDisplayManager());

        Result<SafeCamera> opened = SafeCamera.open(this, this);
        if (!opened.isOk()) {
            log("camera unavailable: " + opened);
            safeCamera = null;
            tvCount.setText("Camera unavailable");
            return;
        }
        safeCamera = opened.get();
        RunOptions run = applyCapabilities(safeCamera.capabilities());
        cameraSurfaceHolder.addCallback(this);
        safeCamera.configure(run);

        startBracketTiming();

        // Built after applyCapabilities, from the masked plan (BRC3 or burst may be off on this body).
        // A resumed run keeps its slots: the scheduler shifts every remaining deadline by the pause.
        if (scheduler == null) {
            scheduler = new Scheduler(plan, clock, Scheduler.StopOn.CAPTURED);
            scheduler.start();
            runStartWallMs = System.currentTimeMillis();
            shotLog = new ShotLog(this, Build.MODEL, Build.VERSION.INCREMENTAL, appVersion(), clock.nowMs(),
                    ShotLogCsv.settingsJson(settings));
        } else {
            scheduler.resume();
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

        setAutoPowerOffMode(false);

        if (cardSpace == null)
            cardSpace = new CardSpace(runStartWallMs);
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, HUD_MS);

        // applyCapabilities turned HG off if this body lacks any of its methods.
        if (settings.holyGrail) {
            safeCamera.setExposureListener(exposureCallback);
            updateShutterSpeed();
            updateISOLevel();
        }
        updateScreen();
    }

    // The warning is estimated from the shutter time at start and replaced by the measured slot time after each
    // bracket. It only informs: the scheduler skips a slot that is still busy.
    private void startBracketTiming() {
        BracketTiming previous = bracketTiming;
        bracketTiming = new BracketTiming(plan);
        bracketTiming.inherit(previous);
        updateBracketShutter();
    }

    private void updateBracketShutter() {
        if (bracketTiming == null)
            return;
        Result<int[]> sp = safeCamera.shutterSpeed();
        if (sp.isOk() && sp.get()[1] > 0)
            bracketTiming.setShutterMs(sp.get()[0] * 1000L / sp.get()[1]);
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
        if (bracketWarning == null) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setText(bracketWarning);
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
            exposureLevel = (float)ev / 3.0f;
            if (settings.useCurrentExposure){
                targetExposureLevel = exposureLevel;
                settings.useCurrentExposure = false;
                updateScreen();
            }
            tvExposureLevel.setText(String.format("%.2f", exposureLevel));
        }
    };

    private void updateShutterSpeed(){
        Result<int[]> sp = safeCamera.shutterSpeed();
        if (!sp.isOk())
            return;

        //find the shutterspeed in the list (list of int[][])
        for(int i = 0; i < SHUTTER_SPEEDS.length; i++){
            if(SHUTTER_SPEEDS[i][0] == sp.get()[0] && SHUTTER_SPEEDS[i][1] == sp.get()[1]){
                shutterSpeedIndex = i;
                break;
            }
        }
        updateBracketShutter();
    }

    private void updateISOLevel(){
        Result<Integer> isoResult = safeCamera.isoSensitivity();
        if (!isoResult.isOk())
            return;
        int iso = isoResult.get();
        for(int i = 0; i < ISO_VALUES.length; i++){
            if(ISO_VALUES[i] == iso){
                ISOLevelIndex = i;
                break;
            }
        }
    }

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
        tvAverageExposure.setText(String.format("%.2f", (getAverageExposure(settings.averageExposureAmount))));
        tvTargetExposureLevel.setText(String.format("%.2f", targetExposureLevel));
        tvShutterspeed.setText(formatShutterSpeedIndex(shutterSpeedIndex));
        tvMaxShutterspeed.setText(formatShutterSpeedIndex(settings.maxShutterSpeedIndex));
        tvLastPictureISO.setText(Integer.toString(ISO_VALUES[ISOLevelIndex]));
        tvMaxISO.setText(Integer.toString(ISO_VALUES[settings.maxISOIndex]));
        tvShotsSinceLastChange.setText(Integer.toString(shotsSinceLastChange));
        tvCooldown.setText(Integer.toString(settings.cooldown));


    }

    private float getAverageExposure(int shotCount){
        //get the average value of the last "shotCount" values in the exposureLevelList, or list.size() if list.size() < shotCount
        float sum = 0;
        int count = 0;
        for(int i = exposureLevelList.size() - 1; i >= 0 && count < shotCount; i--){
            sum += exposureLevelList.get(i);
            count++;
        }
        if (count == 0){
            return 0;
        } else {
            return sum / count;
        }
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
        log(summary);
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
                display.on();
                display.turnAutoOff(Display.NO_AUTO_OFF);
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

        setAutoPowerOffMode(true);
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

    private void shoot() {
        if (settings.holyGrail){
            holyGrailPrePhotoEvent();
        }

        safeCamera.takePicture();

        shotCount++;
        shotsSinceLastChange++;

        if (settings.holyGrail)
            exposureLevelList.add(exposureLevel);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateScreen();
            }
        });
    }

    private void holyGrailPrePhotoEvent(){
        float usableExposureLevel = getAverageExposure(settings.averageExposureAmount);
        int cooldown = settings.cooldown;
        float deadbandSize = DEADBAND_VALUES[settings.deadbandIndex];


        if (settings.holyGrail){

            if (shotsSinceLastChange >= cooldown){

                if (usableExposureLevel < targetExposureLevel - deadbandSize){

                    if (settings.holyGrailAllowExposureUp) {
                        if (shutterSpeedIndex < settings.maxShutterSpeedIndex){
                            //make the shutter open longer
                            safeCamera.decrementShutterSpeed();
                            shotsSinceLastChange = 0;
                        } else if (ISOLevelIndex < settings.maxISOIndex){
                            //increase ISO
                            incrementISO();
                            shotsSinceLastChange = 0;
                        }
                    }
                }

                if (usableExposureLevel > targetExposureLevel + deadbandSize){

                    if (settings.holyGrailAllowExposureDown) {
                        if (ISOLevelIndex > 3){
                            //decrease ISO
                            decrementISO();
                            shotsSinceLastChange = 0;
                        } else if (shutterSpeedIndex > 0){
                            //make the shutter open shorter
                            safeCamera.incrementShutterSpeed();
                            shotsSinceLastChange = 0;
                        }
                    }
                }
            }
        }
        updateShutterSpeed();
        updateISOLevel();
        updateScreen();
    }

    private void incrementISO(){
        ISOLevelIndex++;
        if (ISOLevelIndex < ISO_VALUES.length){
            safeCamera.setIsoSensitivity(ISO_VALUES[ISOLevelIndex]);
        }
    }

    private void decrementISO(){
        ISOLevelIndex--;
        if (ISOLevelIndex >= 0){
            safeCamera.setIsoSensitivity(ISO_VALUES[ISOLevelIndex]);
        }
    }

    // The camera calls this when a frame is captured, possibly off the main looper.
    @Override
    public void onShutter(int status) {
        if (tornDown)
            return;
        if(!burstShooting) {
            safeCamera.cancelTakePicture();

            //camera.startPreview();
        }
        handler.post(shutterRunnable);
    }

    private void frameCaptured() {
        if (tornDown)
            return;
        Scheduler.Decision decision = scheduler.onFrameCaptured();
        trackBracket(decision);
        if (decision.action == Scheduler.Action.FIRE) {
            takeFrame();
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
            ss = formatShutterSpeedIndex(shutterSpeedIndex);
            iso = Integer.toString(ISO_VALUES[ISOLevelIndex]);
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

    private void takeFrame() {
        stopReview(true);
        shoot();
        if (!burstShooting)
            panelOn();
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
