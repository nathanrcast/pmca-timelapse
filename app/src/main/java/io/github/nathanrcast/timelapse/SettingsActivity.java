package io.github.nathanrcast.timelapse;

import static io.github.nathanrcast.timelapse.core.Util.DEADBAND_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.ISO_VALUES;
import static io.github.nathanrcast.timelapse.core.Util.SHUTTER_SPEEDS;
import static io.github.nathanrcast.timelapse.core.Util.formatShutterSpeed;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import io.github.nathanrcast.timelapse.camera.SafeCamera;
import io.github.nathanrcast.timelapse.core.camera.Capabilities;
import io.github.nathanrcast.timelapse.core.camera.OptionVisibility;

import io.github.nathanrcast.timelapse.core.Settings;
import io.github.nathanrcast.timelapse.core.ShotPlan;
import io.github.nathanrcast.timelapse.core.SliderMapping;
import io.github.nathanrcast.timelapse.core.StartMode;
import io.github.nathanrcast.timelapse.core.StartTime;
import io.github.nathanrcast.timelapse.core.StepTables;
import io.github.nathanrcast.timelapse.core.Util;

public class SettingsActivity extends BaseActivity
{
    private SettingsActivity that = this;

    private Settings settings;
    private SharedPreferencesSettingsStore store;

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

    private Spinner spnStartMode;
    private View laDelay, laAt;
    private AdvancedSeekBar sbAtHour, sbAtMinute;
    private TextView tvAtHour, tvAtMinute;
    private CheckBox cbDisableLenr;

    private int fps;
    private Spinner spnFps;

    private CheckBox cbSilentShutter;
    private CheckBox cbAEL;
    private CheckBox cbBRS;
    private CheckBox cbMF;
    private CheckBox cbDOFF;

    // The capability probe opens the camera, so it runs on its own Looper thread (as CapsDump does).
    private HandlerThread probeThread;
    private boolean probing;
    // A Start or Camera info pressed while the probe holds the camera; launched when the probe finishes.
    private Intent pendingLaunch;
    private boolean burstUnavailable;

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

        spnStartMode = (Spinner) findViewById(R.id.spnStartMode);
        laDelay = findViewById(R.id.laDelay);
        laAt = findViewById(R.id.laAt);
        sbAtHour = (AdvancedSeekBar) findViewById(R.id.sbAtHour);
        sbAtMinute = (AdvancedSeekBar) findViewById(R.id.sbAtMinute);
        tvAtHour = (TextView) findViewById(R.id.tvAtHour);
        tvAtMinute = (TextView) findViewById(R.id.tvAtMinute);
        cbDisableLenr = (CheckBox) findViewById(R.id.cbDisableLenr);

        spnStartMode.setSelection(settings.startMode.ordinal());
        spnStartMode.setOnItemSelectedListener(spnStartModeOnItemSelectedListener);
        showStartMode();
        sbAtHour.setMax(23);
        sbAtHour.setOnSeekBarChangeListener(sbAtOnSeekBarChangeListener);
        sbAtHour.setProgress(settings.atHour);
        sbAtMinute.setMax(59);
        sbAtMinute.setOnSeekBarChangeListener(sbAtOnSeekBarChangeListener);
        sbAtMinute.setProgress(settings.atMinute);
        sbAtOnSeekBarChangeListener.onProgressChanged(sbAtHour, settings.atHour, false);
        cbDisableLenr.setChecked(settings.disableLenr);
        cbDisableLenr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                settings.disableLenr = b;
            }
        });

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

        // The three bars are indices into core.StepTables.
        sbInterval.setMax(StepTables.INTERVAL_MS.maxIndex());
        sbInterval.setOnSeekBarChangeListener(sbIntervalOnSeekBarChangeListener);
        sbInterval.setProgress(settings.intervalIndex);
        sbIntervalOnSeekBarChangeListener.onProgressChanged(sbInterval, settings.intervalIndex, false);

        sbShots.setMax(StepTables.SHOTS.maxIndex());
        sbShots.setOnSeekBarChangeListener(sbShotsOnSeekBarChangeListener);
        sbShots.setProgress(settings.shotsIndex);
        sbShotsOnSeekBarChangeListener.onProgressChanged(sbShots, settings.shotsIndex, false);

        sbDelay.setMax(StepTables.DELAY_MIN.maxIndex());
        sbDelay.setOnSeekBarChangeListener(sbDelayOnSeekBarChangeListener);
        sbDelay.setProgress(settings.delayIndex);
        sbDelayOnSeekBarChangeListener.onProgressChanged(sbDelay, settings.delayIndex, false);

        spnFps.setSelection(settings.fps);
        spnFps.setOnItemSelectedListener(spnFpsOnItemSelectedListener);

        cbSilentShutter.setChecked(settings.silentShutter);
        cbSilentShutter.setOnCheckedChangeListener(cbSilentShutterOnCheckListener);

        cbAEL.setChecked(settings.ael);
        cbAEL.setOnCheckedChangeListener(cbAELOnCheckListener);

        cbBRS.setChecked(settings.brs);
        cbBRS.setOnCheckedChangeListener(cbBRSOnCheckListener);

        cbMF.setChecked(settings.mf);
        cbMF.setOnCheckedChangeListener(cbMFOnCheckListener);

        cbDOFF.setChecked(settings.displayOff);
        cbDOFF.setOnCheckedChangeListener(cbDOFFOnCheckListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Capabilities known = SafeCamera.lastCapabilities();
        if (known != null)
            applyCapabilities(known);
        if (!probing && SafeCamera.probeWanted())
            startProbe();
    }

    @Override
    protected void onDestroy() {
        if (probeThread != null)
            probeThread.quit();
        super.onDestroy();
    }

    private void startProbe() {
        if (probeThread == null) {
            probeThread = new HandlerThread("CapabilityProbe");
            probeThread.start();
        }
        probing = true;
        new Handler(probeThread.getLooper()).post(new Runnable() {
            @Override
            public void run() {
                final Capabilities caps = SafeCamera.probe();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        probing = false;
                        if (isFinishing()) {
                            pendingLaunch = null;
                            return;
                        }
                        applyCapabilities(caps);
                        if (pendingLaunch != null) {
                            Intent launch = pendingLaunch;
                            pendingLaunch = null;
                            startActivity(launch);
                        }
                    }
                });
            }
        });
    }

    // Shows only the options this body supports (core.camera.OptionVisibility). Visual only: a hidden option keeps
    // its saved value, and ShootActivity masks it off for the run.
    private void applyCapabilities(Capabilities caps) {
        OptionVisibility options = OptionVisibility.of(caps);
        showIf(cbSilentShutter, options.silentShutter());
        showIf(cbMF, options.manualFocus());
        showIf(cbAEL, options.aeLock());
        showIf(cbBRS, options.bracket3());
        showIf(cbHolyGrail, options.holyGrail());
        showIf(cbDisableLenr, options.lenrOff());
        laHolyGrailSection.setVisibility(options.holyGrail() && cbHolyGrail.isChecked() ? View.VISIBLE : View.GONE);
        burstUnavailable = !options.burst();
        showIntervalText();
    }

    private static void showIf(View option, boolean supported) {
        option.setVisibility(supported ? View.VISIBLE : View.GONE);
    }

    // The burst position stays on the slider; on a body without burst it reads "n/a" and runs at the shortest
    // interval (core.camera.RunOptions).
    private void showIntervalText() {
        boolean noBurst = settings.interval == 0 && burstUnavailable;
        tvIntervalValue.setText(noBurst ? "n/a" : SliderMapping.intervalText(settings.interval));
        tvIntervalUnit.setText(noBurst ? "" : SliderMapping.intervalUnit(settings.interval));
    }

    // Waits for a running probe, so the next activity's camera open doesn't collide with it.
    private void launch(Intent intent) {
        if (probing)
            pendingLaunch = intent;
        else
            startActivity(intent);
    }

    View.OnClickListener bnStartOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            settings.save(store);
            Intent intent = new Intent(that, ShootActivity.class);
            SettingsIntent.put(intent, settings);
            launch(intent);
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
            launch(new Intent(that, CapsDumpActivity.class));
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
            settings.intervalIndex = i;
            settings.deriveFromIndex();
            showIntervalText();

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
            settings.shotsIndex = i;
            settings.deriveFromIndex();
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
            settings.delayIndex = i;
            settings.deriveFromIndex();
            tvDelayValue.setText(SliderMapping.delayText(settings.delay));
            tvDelayUnit.setText(SliderMapping.delayUnit(settings.delay));

            updateTimes();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    private void showStartMode() {
        laDelay.setVisibility(settings.startMode == StartMode.DELAY ? View.VISIBLE : View.GONE);
        laAt.setVisibility(settings.startMode == StartMode.AT ? View.VISIBLE : View.GONE);
    }

    AdapterView.OnItemSelectedListener spnStartModeOnItemSelectedListener
            = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            settings.startMode = StartMode.fromOrdinal(i, StartMode.NOW);
            showStartMode();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {}
    };

    SeekBar.OnSeekBarChangeListener sbAtOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
            if (seekBar == sbAtHour)
                settings.atHour = i;
            else
                settings.atMinute = i;
            tvAtHour.setText(StartTime.format(settings.atHour, 0).substring(0, 2));
            tvAtMinute.setText(StartTime.format(0, settings.atMinute).substring(3));
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

    // Every dial and the control wheel does the same thing: next/previous value of the focused setting. Up/down
    // move focus through the default focus search, and left/right step a focused bar through AdvancedSeekBar,
    // so all of them work on a body whose wheel is unreliable.
    protected boolean onUpperDialChanged(int value) {
        return dialStep(value);
    }

    protected boolean onLowerDialChanged(int value) {
        return dialStep(value);
    }

    protected boolean onThirdDialChanged(int value) {
        return dialStep(value);
    }

    protected boolean onKuruDialChanged(int value) {
        return dialStep(value);
    }

    private boolean dialStep(int value) {
        if (value == 0)
            return true;
        int direction = value > 0 ? 1 : -1;
        View focused = getCurrentFocus();
        if (focused instanceof AdvancedSeekBar) {
            ((AdvancedSeekBar) focused).dialChanged(direction);
        } else if (focused instanceof Spinner) {
            Spinner spinner = (Spinner) focused;
            int next = spinner.getSelectedItemPosition() + direction;
            if (next >= 0 && next < spinner.getCount())
                spinner.setSelection(next);
        } else if (focused instanceof CheckBox) {
            ((CheckBox) focused).toggle();
        }
        return true;
    }

    @Override
    protected boolean onMenuKeyUp()
    {
        onBackPressed();
        return true;
    }
}
