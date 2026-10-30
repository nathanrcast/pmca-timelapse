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
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility;

import io.github.nathanrcast.timelapse.core.Clock;
import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.Util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ShootActivity extends BaseActivity implements SurfaceHolder.Callback, SafeCamera.ShutterCallback
{
    private Settings settings;
    private ShotPlan plan;
    private final Clock clock = Clocks.WALL;

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
    private int pictureReviewTime;

    private boolean burstShooting;
    private boolean stopPicturePreview;
    private boolean takingPicture;

    private long shootTime;
    private long shootStartTime;

    private float exposureLevel = 0;
    private int ISOLevelIndex = 0;
    private int shutterSpeedIndex = 0;
    private int shotsSinceLastChange = 0;
    private float targetExposureLevel = 0.0f;

    private Display display;

    private Handler shootRunnableHandler = new Handler();
    private final Runnable shootRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if(stopPicturePreview) {
                stopPicturePreview = false;
                camera.stopPreview();
                reviewSurfaceView.setVisibility(View.GONE);
                if(settings.displayOff)
                    display.off();
            }

            if(burstShooting) {
                shoot();
            }
            else if(shotCount < plan.totalFrames()) {
                long remainingTime = Math.round(shootTime + settings.interval * 1000 - clock.nowMs());
                if(brck.get()>0){
                    remainingTime = -1;
                }

                log("  Remaining Time: " + Long.toString(remainingTime));

                if (remainingTime <= 150) { // 300ms is vaguely the time this postDelayed is to slow
                    brck.getAndDecrement();
                    shoot();
                    display.on();
                } else {
                    shootRunnableHandler.postDelayed(this, remainingTime-150);
                }
            }
            else {
                display.on();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBackPressed();
                    }
                });
            }
        }
    };

    private final Runnable delayCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (delaySecondsRemaining() > 0) {
                updateScreen();
                shootRunnableHandler.postDelayed(this, 1000);
            }
        }
    };

    private Handler manualShutterCallbackCallRunnableHandler = new Handler();
    private final Runnable manualShutterCallbackCallRunnable = new Runnable() {
        @Override
        public void run() {
            onShutter(0);
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
        takingPicture = false;
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

        Result<SafeCamera> opened = SafeCamera.open(this);
        if (!opened.isOk()) {
            log("camera unavailable: " + opened);
            safeCamera = null;
            tvCount.setText("Camera unavailable");
            return;
        }
        safeCamera = opened.get();
        applyCapabilities(safeCamera.capabilities());
        cameraSurfaceHolder.addCallback(this);

        SafeCamera.Setup setup = new SafeCamera.Setup();
        setup.burst = burstShooting;
        setup.silentShutter = settings.silentShutter;
        setup.aeLock = settings.ael;
        setup.bracket3 = settings.brs;
        setup.manualFocus = settings.mf;
        safeCamera.configure(setup);

        pictureReviewTime = 2;
        //log(Integer.toString(pictureReviewTime));


        shotCount = 0;
        shootRunnableHandler.postDelayed(shootRunnable, plan.delayMs());
        shootStartTime = clock.nowMs() + plan.delayMs();

        if (settings.delay > 0) {
            shootRunnableHandler.post(delayCountdownRunnable);
        }

        if(burstShooting) {
            manualShutterCallbackCallRunnableHandler.postDelayed(manualShutterCallbackCallRunnable, 500);
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

    // Options this body lacks are forced off for the run, as SettingsActivity hides them.
    private void applyCapabilities(Capabilities caps) {
        OptionVisibility options = OptionVisibility.of(caps);
        settings.silentShutter &= options.silentShutter();
        settings.ael &= options.aeLock();
        settings.brs &= options.bracket3();
        settings.mf &= options.manualFocus();
        if (settings.holyGrail && !options.holyGrail()) {
            settings.holyGrail = false;
            findViewById(R.id.divider2).setVisibility(View.GONE);
            findViewById(R.id.textView4).setVisibility(View.GONE);
            findViewById(R.id.tlHolyGrail).setVisibility(View.GONE);
        }
        // Frames per interval follow BRC3, so the plan is rebuilt from the masked settings.
        plan = ShotPlan.of(settings);
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
        return plan.delaySecondsRemaining(shootStartTime, clock.nowMs(), shotCount);
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
        if (display != null) {
            display.on();
            display.turnAutoOff(Display.NO_AUTO_OFF);
        }

        super.onPause();

        log("on pause");

        shootRunnableHandler.removeCallbacks(shootRunnable);
        shootRunnableHandler.removeCallbacks(delayCountdownRunnable);
        manualShutterCallbackCallRunnableHandler.removeCallbacks(manualShutterCallbackCallRunnable);

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
        if(takingPicture)
            return;

        if (settings.holyGrail){
            log("holyGrail shot " + shotCount + ", exposureLevel: " + exposureLevel + ", target:" + targetExposureLevel);
            holyGrailPrePhotoEvent();
        }

        shootTime = clock.nowMs();
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

    private AtomicInteger brck = new AtomicInteger(0);


    // When burst shooting this method is not called automatically
    // Therefore we called it every second manually
    @Override
    public void onShutter(int status) {

        if(brck.get()<0){
            brck = new AtomicInteger(0);
            if(plan.framesPerInterval()>1) {
                brck = new AtomicInteger(2);
            }
        }

        if(burstShooting) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateScreen();
                }
            });

            // just keep shooting until we have all shots
            if (clock.nowMs() >= plan.endMs(shootStartTime)) {
                safeCamera.cancelTakePicture();
                stopPicturePreview = true;
                display.on();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBackPressed();
                    }
                });
            }
            else {
                manualShutterCallbackCallRunnableHandler.postDelayed(manualShutterCallbackCallRunnable, 500);
            }
        }
        else {
            safeCamera.cancelTakePicture();

            //camera.startPreview();

            if (shotCount < plan.totalFrames()) {

                // remaining time to the next shot
                double remainingTime = shootTime + settings.interval * 1000 - clock.nowMs();
                if (brck.get() > 0) {
                    remainingTime = -1;
                }

                log("Remaining Time: " + remainingTime);

                // if the remaining time is negative immediately take the next picture
                if (remainingTime < 0) {
                    stopPicturePreview = false;
                    shootRunnableHandler.post(shootRunnable);
                }
                // show the preview picture for some time
                else {
                    long previewPictureShowTime = Math.round(Math.min(remainingTime, pictureReviewTime * 1000));
                    //log("  Stop preview in: " + previewPictureShowTime);
                    reviewSurfaceView.setVisibility(View.VISIBLE);
                    stopPicturePreview = true;
                    shootRunnableHandler.postDelayed(shootRunnable, previewPictureShowTime);
                }
            } else {
                //stopPicturePreview = true;
                shootRunnableHandler.postDelayed(shootRunnable, pictureReviewTime * 1000);
            }
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
        return Util.formatTime(plan.secondsRemaining(shootStartTime, clock.nowMs(), shotCount));
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
