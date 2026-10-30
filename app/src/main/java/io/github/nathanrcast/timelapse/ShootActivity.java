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
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;

import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.Scheduler;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ShootActivity extends BaseActivity implements SurfaceHolder.Callback, CameraEx.ShutterListener
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
    private CameraEx cameraEx;
    private Camera camera;
    private CameraEx.AutoPictureReviewControl autoReviewControl;

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

    private String savedFocusMode;

    // One timer, re-armed for each deadline the scheduler hands out.
    private final Handler handler = new Handler();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            Scheduler.Decision decision = scheduler.onTimer();
            if (decision.action == Scheduler.Action.FIRE) {
                takeFrame();
                if (burstShooting)
                    handler.postDelayed(burstPollRunnable, BURST_POLL_MS);
            }
            if (decision.action != Scheduler.Action.WAIT)
                log(decision.toString());
            if (scheduler.isDone())
                finishRun();
            else
                armTimer();
        }
    };

    // The next frame of a bracket set, right after the previous one is captured.
    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            takeFrame();
        }
    };

    private final Runnable stopReviewRunnable = new Runnable() {
        @Override
        public void run() {
            stopReview();
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

    // Burst drive doesn't call onShutter per frame, so poll it while the burst runs.
    private final Runnable burstPollRunnable = new Runnable() {
        @Override
        public void run() {
            onShutter(0, cameraEx);
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
        cameraEx = CameraEx.open(0, null);
        cameraEx.setShutterListener(this);
        cameraSurfaceHolder.addCallback(this);
        autoReviewControl = new CameraEx.AutoPictureReviewControl();
        //autoReviewControl.setPictureReviewInfoHist(true);
        cameraEx.setAutoPictureReviewControl(autoReviewControl);

        final Camera.Parameters params = cameraEx.getNormalCamera().getParameters();

        try {
            if(settings.mf) {
                savedFocusMode = params.getFocusMode();
                params.setFocusMode(CameraEx.ParametersModifier.FOCUS_MODE_MANUAL);
            }
            else
                params.setFocusMode("auto");
        }
        catch(Exception ignored)
        {}

        final CameraEx.ParametersModifier modifier = cameraEx.createParametersModifier(params);

        if(burstShooting) {
            try {
                modifier.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_BURST);
                List driveSpeeds = modifier.getSupportedBurstDriveSpeeds();
                modifier.setBurstDriveSpeed(driveSpeeds.get(driveSpeeds.size() - 1).toString());
                modifier.setBurstDriveButtonReleaseBehave(CameraEx.ParametersModifier.BURST_DRIVE_BUTTON_RELEASE_BEHAVE_CONTINUE);
            } catch (Exception ignored) {
            }
        }
        else {
            modifier.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_SINGLE);
        }

        // setSilentShutterMode doesn't exist on all cameras
        try {
            modifier.setSilentShutterMode(settings.silentShutter);
        }
        catch(NoSuchMethodError ignored)
        {}

        try{
            //add also AEL if set
            if(settings.ael) {
                modifier.setAutoExposureLock(CameraEx.ParametersModifier.AE_LOCK_ON);
            }
        }
        catch (Exception e){
            //do nothing
        }

        if(settings.brs){
            try{
                modifier.setDriveMode(CameraEx.ParametersModifier.DRIVE_MODE_BRACKET);
                modifier.setBracketMode(CameraEx.ParametersModifier.BRACKET_MODE_EXPOSURE);
                modifier.setExposureBracketMode(CameraEx.ParametersModifier.EXPOSURE_BRACKET_MODE_SINGLE);
                modifier.setExposureBracketPeriod(30);
                modifier.setNumOfBracketPicture(3);
            }
            catch (Exception e){
                //do nothing
            }
        }

        cameraEx.getNormalCamera().setParameters(params);

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

        display = new Display(getDisplayManager());

        if(settings.displayOff) {
            display.turnAutoOff(5000);
        }

        setAutoPowerOffMode(false);

        // HG-only Sony calls: older bodies may lack them, so HG off must not touch them.
        if (settings.holyGrail) {
            // Exposure metering
            cameraEx.setProgramLineRangeOverListener(new CameraEx.ProgramLineRangeOverListener()
            {
                @Override
                public void onAERange(boolean b, boolean b1, boolean b2, CameraEx cameraEx)
                {
                    //log(String.format("onARRange b %b b1 %b b2 %b\n", Boolean.valueOf(b), Boolean.valueOf(b1), Boolean.valueOf(b2)));
                }

                @Override
                public void onEVRange(int ev, CameraEx cameraEx)
                {
                    exposureLevel = (float)ev / 3.0f;
                    if (settings.useCurrentExposure){
                        targetExposureLevel = exposureLevel;
                        settings.useCurrentExposure = false;
                        updateScreen();
                    }
                    tvExposureLevel.setText(String.format("%.2f", exposureLevel));
                    //log(String.format("onEVRange i %d %f\n", ev, (float)ev / 3.0f));
                }

                @Override
                public void onMeteringRange(boolean b, CameraEx cameraEx)
                {
                    //log(String.format("onMeteringRange b %b\n", Boolean.valueOf(b)));
                }
            });

            updateShutterSpeed();
            updateISOLevel();
        }
        updateScreen();
    }

    private void updateShutterSpeed(){
        log("updateShutterSpeed");
        final Camera.Parameters params = cameraEx.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = cameraEx.createParametersModifier(params);
        Pair<Integer, Integer> sp = paramsModifier.getShutterSpeed();

        //find the shutterspeed in the list (list of int[][])
        for(int i = 0; i < SHUTTER_SPEEDS.length; i++){
            if(SHUTTER_SPEEDS[i][0] == sp.first && SHUTTER_SPEEDS[i][1] == sp.second){
                shutterSpeedIndex = i;
                break;
            }
        }
        log("shutterSpeedIndex: " + shutterSpeedIndex);
    }

    private void updateISOLevel(){
        log("updateISOLevel");
        final Camera.Parameters params = cameraEx.getNormalCamera().getParameters();
        final CameraEx.ParametersModifier paramsModifier = cameraEx.createParametersModifier(params);
        int iso = paramsModifier.getISOSensitivity();
        for(int i = 0; i < ISO_VALUES.length; i++){
            if(ISO_VALUES[i] == iso){
                ISOLevelIndex = i;
                break;
            }
        }
        log("ISOLevelIndex: " + ISOLevelIndex);
    }

    private long delaySecondsRemaining() {
        return plan.delaySecondsRemaining(scheduler.t0Ms(), clock.nowMs(), shotCount);
    }

    private void updateScreen(){
        long delaySeconds = delaySecondsRemaining();
        if (delaySeconds > 0)
            tvCount.setText("Delay: " + Util.formatTime(delaySeconds));
        else
            tvCount.setText(Integer.toString(shotCount)+"/"+Long.toString(plan.totalFrames()));
        tvRemaining.setText(getRemainingTime());
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
        display.on();
        display.turnAutoOff(Display.NO_AUTO_OFF);

        super.onPause();

        log("on pause");

        handler.removeCallbacksAndMessages(null);
        if (scheduler != null)
            scheduler.pause();
        reviewShowing = false;
        finishing = false;

        // Restore the focus mode the camera had before MF was forced
        if (savedFocusMode != null && cameraEx != null) {
            try {
                Camera.Parameters params = cameraEx.getNormalCamera().getParameters();
                params.setFocusMode(savedFocusMode);
                cameraEx.getNormalCamera().setParameters(params);
            } catch (Exception ignored) {}
        }
        savedFocusMode = null;

        if(cameraSurfaceHolder == null)
            log("cameraSurfaceHolder == null");
        else {
            cameraSurfaceHolder.removeCallback(this);
        }

        autoReviewControl = null;

        if(camera == null)
            log("camera == null");
        else {
            camera.stopPreview();
            camera = null;
        }

        if(cameraEx == null)
            log("cameraEx == null");
        else {
            cameraEx.setAutoPictureReviewControl(null);
            cameraEx.release();
            cameraEx = null;
        }

        setAutoPowerOffMode(true);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = cameraEx.getNormalCamera();
            camera.setPreviewDisplay(holder);
        }
        catch (IOException e) {}
    }

    private void shoot() {
        if (settings.holyGrail){
            log("holyGrail shot " + shotCount + ", exposureLevel: " + exposureLevel + ", target:" + targetExposureLevel);
            holyGrailPrePhotoEvent();
        }

        cameraEx.burstableTakePicture();

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
                            cameraEx.decrementShutterSpeed();
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
                            cameraEx.incrementShutterSpeed();
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
            Camera.Parameters params = cameraEx.createEmptyParameters();
            cameraEx.createParametersModifier(params).setISOSensitivity(ISO_VALUES[ISOLevelIndex]);
            cameraEx.getNormalCamera().setParameters(params);
        } else {
            log("can't increase ISO");
        }
    }

    private void decrementISO(){
        log("decrementISO");
        ISOLevelIndex--;
        if (ISOLevelIndex >= 0){
            Camera.Parameters params = cameraEx.createEmptyParameters();
            cameraEx.createParametersModifier(params).setISOSensitivity(ISO_VALUES[ISOLevelIndex]);
            cameraEx.getNormalCamera().setParameters(params);
        } else {
            log("can't decrease ISO");
        }
    }

    // The camera calls this when a frame is captured; in burst mode burstPollRunnable does.
    @Override
    public void onShutter(int i, CameraEx cameraEx) {
        if(burstShooting) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateScreen();
                }
            });

            scheduler.onFrameCaptured();
            if (scheduler.isDone()) {
                finishRun();
            }
            else {
                handler.removeCallbacks(burstPollRunnable);
                handler.postDelayed(burstPollRunnable, BURST_POLL_MS);
            }
        }
        else {
            this.cameraEx.cancelTakePicture();

            //camera.startPreview();

            Scheduler.Decision decision = scheduler.onFrameCaptured();
            if (decision.action == Scheduler.Action.FIRE) {
                handler.post(frameRunnable);
            } else if (decision.action == Scheduler.Action.SLOT_DONE) {
                log(decision.toString());
                if (scheduler.isDone())
                    finishRun();
                else
                    showReview();
            }
        }
    }

    private void armTimer() {
        handler.removeCallbacks(tickRunnable);
        long wake = scheduler.nextWakeMs();
        if (wake != Scheduler.NEVER)
            handler.postAtTime(tickRunnable, wake);
    }

    private void takeFrame() {
        stopReview();
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

    private void stopReview() {
        handler.removeCallbacks(stopReviewRunnable);
        if (!reviewShowing)
            return;
        reviewShowing = false;
        if (camera != null)
            camera.stopPreview();
        reviewSurfaceView.setVisibility(View.GONE);
        if (settings.displayOff)
            display.off();
    }

    private void finishRun() {
        if (finishing)
            return;
        finishing = true;
        handler.removeCallbacks(tickRunnable);
        handler.removeCallbacks(burstPollRunnable);
        if (burstShooting) {
            this.cameraEx.cancelTakePicture();
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

    private String getRemainingTime() {
        return Util.formatTime(plan.secondsRemaining(scheduler.t0Ms(), clock.nowMs(), shotCount));
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
