package io.github.nathanrcast.timelapse;

import static io.github.nathanrcast.timelapse.core.Util.DEADBAND_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.ISO_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.SHUTTER_SPEEDS;
import static io.github.nathanrcast.timelapse.core.Util.formatShutterSpeedIndex;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.BatteryManager;
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

import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.Progress;
import io.github.nathanrcast.timelapse.core.Scheduler;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ShootActivity extends BaseActivity implements SurfaceHolder.Callback, SafeCamera.ShutterCallback
{
    private Settings settings;
    private ShotPlan plan;
    private Scheduler scheduler;
    private final Clock clock = Clocks.UPTIME;

    private int shotCount;

    private TextView tvCount, tvBattery, tvRemaining;

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

    private static final long REVIEW_MS = 2000;
    private static final long BURST_POLL_MS = 500;

    private boolean burstShooting;
    private boolean reviewShowing;
    private boolean finishing;

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
            Scheduler.Decision decision = scheduler.onTimer();
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
        plan = ShotPlan.of(settings);

        shotCount = 0;
        burstShooting = plan.isBurst();

        tvCount = (TextView) findViewById(R.id.tvCount);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvRemaining = (TextView) findViewById(R.id.tvRemaining);

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

        // Built after applyCapabilities, from the masked plan (BRC3 or burst may be off on this body).
        // A resumed run keeps its slots: the scheduler shifts every remaining deadline by the pause.
        if (scheduler == null) {
            scheduler = new Scheduler(plan, clock, Scheduler.StopOn.CAPTURED);
            scheduler.start();
        } else {
            scheduler.resume();
        }
        if (scheduler.isDone())
            finishRun();
        else
            armTimer();

        if (settings.delay > 0) {
            handler.post(delayCountdownRunnable);
        }

        if(settings.displayOff) {
            display.turnAutoOff(5000);
        }

        setAutoPowerOffMode(false);

        // applyCapabilities turned HG off if this body lacks any of its methods.
        if (settings.holyGrail) {
            safeCamera.setExposureListener(exposureCallback);
            updateShutterSpeed();
            updateISOLevel();
        }
        updateScreen();
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
        log("updateShutterSpeed");
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
        log("shutterSpeedIndex: " + shutterSpeedIndex);
    }

    private void updateISOLevel(){
        log("updateISOLevel");
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
        log("ISOLevelIndex: " + ISOLevelIndex);
    }

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
        if (scheduler != null)
            scheduler.pause();
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
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = safeCamera.normalCamera();
            camera.setPreviewDisplay(holder);
        }
        catch (IOException e) {}
    }

    private void shoot() {
        if (settings.holyGrail){
            log("holyGrail shot " + shotCount + ", exposureLevel: " + exposureLevel + ", target:" + targetExposureLevel);
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
                        log("trying to get higher exposure");

                        if (shutterSpeedIndex < settings.maxShutterSpeedIndex){
                            log("shutter speed is less than max shutter speed");
                            //make the shutter open longer
                            safeCamera.decrementShutterSpeed();
                            shotsSinceLastChange = 0;
                        } else if (ISOLevelIndex < settings.maxISOIndex){
                            log("ISO is less than max ISO");
                            //increase ISO
                            incrementISO();
                            shotsSinceLastChange = 0;
                        } else {
                            log("can't increase shutter speed or ISO");
                        }
                    }
                }

                if (usableExposureLevel > targetExposureLevel + deadbandSize){

                    if (settings.holyGrailAllowExposureDown) {
                        if (ISOLevelIndex > 3){
                            log("ISOLevelIdex, trying lower iso: " + ISOLevelIndex);
                            //decrease ISO
                            decrementISO();
                            shotsSinceLastChange = 0;
                        } else if (shutterSpeedIndex > 0){
                            log("shutterSpeedIndex, trying lower shutterspeed: " + shutterSpeedIndex);
                            //make the shutter open shorter
                            safeCamera.incrementShutterSpeed();
                            shotsSinceLastChange = 0;
                        } else {
                            log("can't decrease shutter speed or ISO");
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
        log("incrementISO");
        ISOLevelIndex++;
        if (ISOLevelIndex < ISO_VALUES.length){
            safeCamera.setIsoSensitivity(ISO_VALUES[ISOLevelIndex]);
        } else {
            log("can't increase ISO");
        }
    }

    private void decrementISO(){
        log("decrementISO");
        ISOLevelIndex--;
        if (ISOLevelIndex >= 0){
            safeCamera.setIsoSensitivity(ISO_VALUES[ISOLevelIndex]);
        } else {
            log("can't decrease ISO");
        }
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

    private void logDecision(Scheduler.Decision decision) {
        if (decision.action != Scheduler.Action.WAIT)
            log(decision.toString());
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
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        String s = "";
        if(isCharging)
            s = "c ";

        return s + (int)(level / (float)scale * 100) + "%";
    }

    @Override
    protected void onAnyKeyDown() {
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
        //add timestamp
        s = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " " + s;
        Logger.info(s);
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
