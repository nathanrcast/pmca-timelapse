package io.github.nathanrcast.timelapse;

import static io.github.nathanrcast.timelapse.core.Util.DEADBAND_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.ISO_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.SHUTTER_SPEEDS;
import static io.github.nathanrcast.timelapse.core.Util.formatShutterSpeed;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;

import com.github.ma1co.pmcademo.app.BaseActivity;
import com.sony.scalar.hardware.CameraEx;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.SliderMapping;
import io.github.nathanrcast.timelapse.core.Util;

public class SettingsActivity extends BaseActivity
{
    private SettingsActivity that = this;

    private Settings settings;
    private SharedPreferencesSettingsStore store;
    private CameraEx cameraEx;

    private TabHost tabHost;

    private Button bnStart, bnClose;

    private AdvancedSeekBar sbInterval;
    private TextView tvIntervalValue, tvIntervalUnit;

    private AdvancedSeekBar sbShots;
    private TextView tvShotsValue;
    private TextView lblShots;

    private TextView tvDurationValue, tvDurationUnit;
    private TextView tvVideoTimeValue, tvVideoTimeUnit;

    private AdvancedSeekBar sbDelay;
    private TextView tvDelayValue, tvDelayUnit;

    //Holy grail
    private LinearLayout laHolyGrailSection;
    private CheckBox cbHolyGrail;
    private RelativeLayout laTargetExposure;
    private CheckBox cbUseCurrentExposure;
    private AdvancedSeekBar sbTargetExposure;
    private TextView tvTargetExposureValue, tvTargetExposureUnit;
    private AdvancedSeekBar sbMaxShutterSpeed;
    private TextView tvMaxShutterSpeedValue, tvMaxShutterSpeedUnit;
    private CheckBox cbHolyGrailAllowExposureUp;
    private CheckBox cbHolyGrailAllowExposureDown;
    private AdvancedSeekBar sbMaxISO;
    private TextView tvMaxISOValue, tvMaxISOUnit;
    private AdvancedSeekBar sbCooldown;
    private TextView tvCooldownValue, tvCooldownUnit;
    private AdvancedSeekBar sbAverageAmount;
    private TextView tvAverageAmountValue, tvAverageAmountUnit;
    private AdvancedSeekBar sbDeadbandIndex;
    private TextView tvDeadbandIndexValue, tvDeadbandIndexUnit;

    private int fps;
    private Spinner spnFps;

    private CheckBox cbSilentShutter;
    private CheckBox cbAEL;
    private CheckBox cbBRS;
    private CheckBox cbMF;
    private CheckBox cbDOFF;




    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CustomExceptionHandler))
            Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

        Logger.info("Hello World");

        store = new SharedPreferencesSettingsStore(this);
        settings = new Settings();
        settings.load(store);
        // The fps choices are a resource, so :core can't clamp the stored index.
        int fpsCount = getResources().getStringArray(R.array.fps).length;
        settings.fps = Math.max(0, Math.min(fpsCount - 1, settings.fps));
        fps = 24;


        bnStart = (Button) findViewById(R.id.bnStart);
        bnStart.setOnClickListener(bnStartOnClickListener);

        bnClose = (Button) findViewById(R.id.bnClose);
        bnClose.setOnClickListener(bnCloseOnClickListener);

        findViewById(R.id.bnCameraInfo).setOnClickListener(bnCameraInfoOnClickListener);

        tvIntervalValue = (TextView) findViewById(R.id.tvIntervalValue);
        tvIntervalUnit = (TextView) findViewById(R.id.tvIntervalUnit);

        tvDurationValue = (TextView) findViewById(R.id.tvDurationValue);
        tvDurationUnit = (TextView) findViewById(R.id.tvDurationUnit);
        tvVideoTimeValue = (TextView) findViewById(R.id.tvVideoTimeValue);
        tvVideoTimeUnit = (TextView) findViewById(R.id.tvVideoTimeUnit);
        sbInterval = (AdvancedSeekBar) findViewById(R.id.sbInterval);
        tvShotsValue = (TextView) findViewById(R.id.tvShotsValue);
        sbShots = (AdvancedSeekBar) findViewById(R.id.sbShots);
        lblShots = (TextView) findViewById(R.id.lblShots);
        spnFps = (Spinner) findViewById(R.id.spnFps);

        sbDelay = (AdvancedSeekBar) findViewById(R.id.sbDelay);
        tvDelayValue = (TextView) findViewById(R.id.tvDelayValue);
        tvDelayUnit = (TextView) findViewById(R.id.tvDelayUnit);

        //Holy Grail
        laHolyGrailSection = (LinearLayout) findViewById(R.id.laHolyGrailSection);
        cbHolyGrail = (CheckBox) findViewById(R.id.cbHolyGrail);
        cbHolyGrail.setChecked(settings.holyGrail);
        cbHolyGrail.setOnCheckedChangeListener(cbHolyGrailOnCheckListener);
        cbHolyGrailOnCheckListener.onCheckedChanged(cbHolyGrail, settings.holyGrail);

        laTargetExposure = (RelativeLayout) findViewById(R.id.laTargetExposure);

        sbTargetExposure = (AdvancedSeekBar) findViewById(R.id.sbTargetExposure);
        tvTargetExposureValue = (TextView) findViewById(R.id.tvTargetExposureValue);
        tvTargetExposureUnit = (TextView) findViewById(R.id.tvTargetExposureUnit);
        sbTargetExposure.setMax(SliderMapping.TARGET_EXPOSURE_MAX);
        sbTargetExposure.setOnSeekBarChangeListener(sbTargetExposureOnSeekBarChangeListener);
        int targetExposureProgress = SliderMapping.targetExposureToProgress(settings.targetExposure);
        sbTargetExposure.setProgress(targetExposureProgress);
        sbTargetExposureOnSeekBarChangeListener.onProgressChanged(sbTargetExposure, targetExposureProgress, false);

        cbUseCurrentExposure = (CheckBox) findViewById(R.id.cbUseCurrentExposure);
        cbUseCurrentExposure.setChecked(settings.useCurrentExposure);
        cbUseCurrentExposure.setOnCheckedChangeListener(cbUseCurrentExposureOnCheckListener);

        sbMaxShutterSpeed = (AdvancedSeekBar) findViewById(R.id.sbMaxShutterSpeed);
        tvMaxShutterSpeedValue = (TextView) findViewById(R.id.tvMaxShutterSpeedValue);
        tvMaxShutterSpeedUnit = (TextView) findViewById(R.id.tvMaxShutterSpeedUnit);
        sbMaxShutterSpeed.setMax(SHUTTER_SPEEDS.length - 1);
        sbMaxShutterSpeed.setOnSeekBarChangeListener(sbMaxShutterSpeedOnSeekBarChangeListener);
        sbMaxShutterSpeed.setProgress(settings.maxShutterSpeedIndex);
        sbMaxShutterSpeedOnSeekBarChangeListener.onProgressChanged(sbMaxShutterSpeed, settings.maxShutterSpeedIndex, false);

        cbHolyGrailAllowExposureUp = (CheckBox) findViewById(R.id.cbHolyGrailAllowExposureUp);
        cbHolyGrailAllowExposureUp.setChecked(settings.holyGrailAllowExposureUp);
        cbHolyGrailAllowExposureUp.setOnCheckedChangeListener(cbHolyGrailAllowExposureUpOnCheckListener);

        cbHolyGrailAllowExposureDown = (CheckBox) findViewById(R.id.cbHolyGrailAllowExposureDown);
        cbHolyGrailAllowExposureDown.setChecked(settings.holyGrailAllowExposureDown);
        cbHolyGrailAllowExposureDown.setOnCheckedChangeListener(cbHolyGrailAllowExposureDownOnCheckListener);

        sbMaxISO = (AdvancedSeekBar) findViewById(R.id.sbMaxISO);
        tvMaxISOValue = (TextView) findViewById(R.id.tvMaxISOValue);
        tvMaxISOUnit = (TextView) findViewById(R.id.tvMaxISOUnit);
        sbMaxISO.setMax(ISO_VALUES.length - 1);
        sbMaxISO.setOnSeekBarChangeListener(sbMaxISOOnSeekBarChangeListener);
        sbMaxISO.setProgress(settings.maxISOIndex);
        sbMaxISOOnSeekBarChangeListener.onProgressChanged(sbMaxISO, settings.maxISOIndex, false);

        sbCooldown = (AdvancedSeekBar) findViewById(R.id.sbCooldown);
        tvCooldownValue = (TextView) findViewById(R.id.tvCooldownValue);
        tvCooldownUnit = (TextView) findViewById(R.id.tvCooldownUnit);
        sbCooldown.setMax(SliderMapping.COUNT_MAX);
        sbCooldown.setOnSeekBarChangeListener(sbCooldownOnSeekBarChangeListener);
        int cooldownProgress = SliderMapping.countToProgress(settings.cooldown);
        sbCooldown.setProgress(cooldownProgress);
        sbCooldownOnSeekBarChangeListener.onProgressChanged(sbCooldown, cooldownProgress, false);

        sbAverageAmount = (AdvancedSeekBar) findViewById(R.id.sbAverageAmount);
        tvAverageAmountValue = (TextView) findViewById(R.id.tvAverageAmountValue);
        tvAverageAmountUnit = (TextView) findViewById(R.id.tvAverageAmountUnit);
        sbAverageAmount.setMax(SliderMapping.COUNT_MAX);
        sbAverageAmount.setOnSeekBarChangeListener(sbAverageAmountOnSeekBarChangeListener);
        int averageAmountProgress = SliderMapping.countToProgress(settings.averageExposureAmount);
        sbAverageAmount.setProgress(averageAmountProgress);
        sbAverageAmountOnSeekBarChangeListener.onProgressChanged(sbAverageAmount, averageAmountProgress, false);

        sbDeadbandIndex = (AdvancedSeekBar) findViewById(R.id.sbDeadbandIndex);
        tvDeadbandIndexValue = (TextView) findViewById(R.id.tvDeadbandIndexValue);
        tvDeadbandIndexUnit = (TextView) findViewById(R.id.tvDeadbandIndexUnit);
        sbDeadbandIndex.setMax(DEADBAND_VALUES.length - 1);
        sbDeadbandIndex.setOnSeekBarChangeListener(sbDeadbandIndexOnSeekBarChangeListener);
        sbDeadbandIndex.setProgress(settings.deadbandIndex);
        sbDeadbandIndexOnSeekBarChangeListener.onProgressChanged(sbDeadbandIndex, settings.deadbandIndex, false);


        //End Holy Grail


        cbSilentShutter = (CheckBox) findViewById(R.id.cbSilentShutter);
        cbAEL  = (CheckBox) findViewById(R.id.cbAEL);
        cbBRS  = (CheckBox) findViewById(R.id.cbBRC);
        cbMF   = (CheckBox) findViewById(R.id.cbMF);
        cbDOFF = (CheckBox) findViewById(R.id.cbDOFF);

        sbInterval.setMax(SliderMapping.INTERVAL_MAX);
        sbInterval.setOnSeekBarChangeListener(sbIntervalOnSeekBarChangeListener);
        sbInterval.setProgress(settings.rawInterval);
        sbIntervalOnSeekBarChangeListener.onProgressChanged(sbInterval, settings.rawInterval, false);

        sbShots.setMax(SliderMapping.SHOTS_MAX);
        sbShots.setOnSeekBarChangeListener(sbShotsOnSeekBarChangeListener);
        sbShots.setProgress(settings.rawShotCount);
        sbShotsOnSeekBarChangeListener.onProgressChanged(sbShots, settings.rawShotCount, false);

        sbDelay.setMax(SliderMapping.DELAY_MAX);
        sbDelay.setOnSeekBarChangeListener(sbDelayOnSeekBarChangeListener);
        sbDelay.setProgress(settings.rawDelay);
        sbDelayOnSeekBarChangeListener.onProgressChanged(sbDelay, settings.rawDelay, false);

        spnFps.setSelection(settings.fps);
        spnFps.setOnItemSelectedListener(spnFpsOnItemSelectedListener);

        cbSilentShutter.setChecked(settings.silentShutter);
        cbSilentShutter.setOnCheckedChangeListener(cbSilentShutterOnCheckListener);
        //cbSilentShutter.setVisibility(View.INVISIBLE);

        cbAEL.setChecked(settings.ael);
        cbAEL.setOnCheckedChangeListener(cbAELOnCheckListener);

        cbBRS.setChecked(settings.brs);
        cbBRS.setOnCheckedChangeListener(cbBRSOnCheckListener);

        cbMF.setChecked(settings.mf);
        cbMF.setOnCheckedChangeListener(cbMFOnCheckListener);

        cbDOFF.setChecked(settings.displayOff);
        cbDOFF.setOnCheckedChangeListener(cbDOFFOnCheckListener);

        //try {
            //CameraEx cameraEx = CameraEx.open(0, null);
            //final CameraEx.ParametersModifier modifier = cameraEx.createParametersModifier(cameraEx.getNormalCamera().getParameters());
            //if(modifier.isSupportedSilentShutterMode())
            //    cbSilentShutter.setVisibility(View.VISIBLE);
        /*}
        catch(Exception ignored)
        {}*/




    }

    View.OnClickListener bnStartOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            settings.save(store);
            Intent intent = new Intent(that, ShootActivity.class);
            SettingsIntent.put(intent, settings);
            startActivity(intent);
        }
    },
    bnCloseOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            settings.save(store);
            finish();
        }
    };

    View.OnClickListener bnCameraInfoOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(new Intent(that, CapsDumpActivity.class));
        }
    };

    SeekBar.OnSeekBarChangeListener sbMaxShutterSpeedOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            //set the shutter speed to the index of the list, show the value
            settings.maxShutterSpeedIndex = i;

            tvMaxShutterSpeedValue.setText( formatShutterSpeed(SHUTTER_SPEEDS[i][0], SHUTTER_SPEEDS[i][1]) );
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbMaxISOOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            //get the ISO value from the list
            settings.maxISOIndex = i;

            tvMaxISOValue.setText( Integer.toString(ISO_VALUES[i]) );
            tvMaxISOUnit.setText("ISO");

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbCooldownOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            //should always be > 0
            settings.cooldown = SliderMapping.progressToCount(i);
            tvCooldownValue.setText(Integer.toString(settings.cooldown));
            tvCooldownUnit.setText("pics");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbAverageAmountOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            settings.averageExposureAmount = SliderMapping.progressToCount(i);
            tvAverageAmountValue.setText(Integer.toString(settings.averageExposureAmount));
            tvAverageAmountUnit.setText("pics");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbDeadbandIndexOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            settings.deadbandIndex = i;
            tvDeadbandIndexValue.setText(String.format("%.2f",DEADBAND_VALUES[i]));
            tvDeadbandIndexUnit.setText("EV");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
    ,
    sbTargetExposureOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            //the seekbar is from 0 to 60, but we want -30 to 30
            settings.targetExposure = SliderMapping.progressToTargetExposure(i);
            //Float.toString(settings.targetExposure/3.0f) show only 2 decimals
            tvTargetExposureValue.setText(String.format("%.2f", settings.targetExposure/3.0f));
            tvTargetExposureUnit.setText("EV");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbIntervalOnSeekBarChangeListener
            = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
            settings.rawInterval = i;
            settings.interval = SliderMapping.progressToInterval(i);
            tvIntervalValue.setText(SliderMapping.intervalText(settings.interval));
            tvIntervalUnit.setText(SliderMapping.intervalUnit(settings.interval));

            if(settings.interval == 0)
                lblShots.setText("Dur. (s)");
            else
                lblShots.setText("Shots");

            updateTimes();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbShotsOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
            settings.rawShotCount = i;
            settings.shotCount = SliderMapping.progressToShotCount(i);
            tvShotsValue.setText(SliderMapping.shotCountText(settings.shotCount));
            updateTimes();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    },
    sbDelayOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
            settings.rawDelay = i;
            settings.delay = SliderMapping.progressToDelay(i);
            tvDelayValue.setText(SliderMapping.delayText(settings.delay));
            tvDelayUnit.setText(SliderMapping.delayUnit(settings.delay));

            updateTimes();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    AdapterView.OnItemSelectedListener spnFpsOnItemSelectedListener
            = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            String sfps = getResources().getStringArray(R.array.fps)[i];
            fps = Integer.parseInt(sfps);
            settings.fps = i;
            updateTimes();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {}
    };

    CheckBox.OnCheckedChangeListener cbSilentShutterOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.silentShutter = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbAELOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.ael = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbBRSOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.brs = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbMFOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.mf = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbDOFFOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.displayOff = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbHolyGrailOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.holyGrail = b;
            //if holy grail is checked, enable the holy grail settings
            //else hide them
            if(b) {
                laHolyGrailSection.setVisibility(View.VISIBLE);
            }
            else {
                laHolyGrailSection.setVisibility(View.GONE);
            }
        }
    };

    CheckBox.OnCheckedChangeListener cbUseCurrentExposureOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.useCurrentExposure = b;
            if (b) {
                laTargetExposure.setVisibility(View.GONE);
            } else {
                laTargetExposure.setVisibility(View.VISIBLE);
            }
        }
    };

    CheckBox.OnCheckedChangeListener cbHolyGrailAllowExposureUpOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.holyGrailAllowExposureUp = b;
        }
    };

    CheckBox.OnCheckedChangeListener cbHolyGrailAllowExposureDownOnCheckListener = new CheckBox.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            settings.holyGrailAllowExposureDown = b;
        }
    };

    void updateTimes() {
        ShotPlan plan = ShotPlan.of(settings);
        if(plan.isInfinite())
        {
            tvDurationValue.setText("inf");
            tvDurationUnit.setText("");
            tvVideoTimeValue.setText("inf");
            tvVideoTimeUnit.setText("");
            return;
        }

        long videoTime = plan.clipSeconds(fps);

        tvDurationValue.setText(Util.formatTime(plan.durationSeconds()));
        tvDurationUnit.setText("");

        if(videoTime == -1) {
            tvVideoTimeValue.setText("");
            tvVideoTimeUnit.setText("");
        }
        else if(videoTime < 120) {
            tvVideoTimeValue.setText("" + videoTime);
            tvVideoTimeUnit.setText("s");
        }
        else {
            tvVideoTimeValue.setText("" + (videoTime / 60));
            tvVideoTimeUnit.setText("min");
        }
    }

    protected boolean onUpperDialChanged(int value) {
        sbInterval.dialChanged(value);
        sbShots.dialChanged(value);
        sbDelay.dialChanged(value);
        sbTargetExposure.dialChanged(value);
        sbMaxShutterSpeed.dialChanged(value);
        sbMaxISO.dialChanged(value);
        sbCooldown.dialChanged(value);
        sbAverageAmount.dialChanged(value);
        sbDeadbandIndex.dialChanged(value);
        return true;
    }

    protected boolean onLowerDialChanged(int value) {
        sbInterval.dialChanged(value);
        sbShots.dialChanged(value);
        sbDelay.dialChanged(value);
        sbTargetExposure.dialChanged(value);
        sbMaxShutterSpeed.dialChanged(value);
        sbMaxISO.dialChanged(value);
        sbCooldown.dialChanged(value);
        sbAverageAmount.dialChanged(value);
        sbDeadbandIndex.dialChanged(value);
        return true;
    }

    protected boolean onThirdDialChanged(int value) {
        sbInterval.dialChanged(value);
        sbShots.dialChanged(value);
        sbDelay.dialChanged(value);
        sbTargetExposure.dialChanged(value);
        sbMaxShutterSpeed.dialChanged(value);
        sbMaxISO.dialChanged(value);
        sbCooldown.dialChanged(value);
        sbAverageAmount.dialChanged(value);
        sbDeadbandIndex.dialChanged(value);
        return true;
    }

    protected boolean onKuruDialChanged(int value) {
        sbInterval.dialChanged(value);
        sbShots.dialChanged(value);
        sbDelay.dialChanged(value);
        sbTargetExposure.dialChanged(value);
        sbMaxShutterSpeed.dialChanged(value);
        sbMaxISO.dialChanged(value);
        sbCooldown.dialChanged(value);
        sbAverageAmount.dialChanged(value);
        sbDeadbandIndex.dialChanged(value);
        return true;
    }

    @Override
    protected boolean onMenuKeyUp()
    {
        onBackPressed();
        return true;
    }
}
