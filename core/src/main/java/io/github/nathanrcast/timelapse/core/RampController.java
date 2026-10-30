package io.github.nathanrcast.timelapse.core;

/**
 * The holy-grail state between slots: the exposure history, the cooldown counter, the target and the tracked
 * shutter and ISO indexes. The caller reads the camera, calls {@link #onSlot} once per slot (frame 0), applies the
 * returned action and reports the outcome with {@link #onApplied}. No camera access.
 */
public final class RampController {
    private final ExposureRamp ramp;
    private final ExposureRamp.History history;
    private final int maxShutterIndex;
    private final int maxIsoIndex;
    private final double intervalSec;
    private final int framesPerInterval;
    private final boolean lenr;
    private final ExposureRamp.Mode mode;
    private final float deadbandEv;
    private final int cooldownSlots;

    private float targetEv;
    private boolean targetPending;
    private float lastMeasuredEv;
    private int slotsSinceChange;
    private int shutterIndex = ExposureRamp.UNKNOWN;
    private int isoIndex = ExposureRamp.UNKNOWN;

    public RampController(ExposureRamp ramp, Settings s, int framesPerInterval, boolean lenr) {
        this.ramp = ramp;
        this.history = new ExposureRamp.History(s.averageExposureAmount);
        this.maxShutterIndex = s.maxShutterSpeedIndex;
        this.maxIsoIndex = s.maxISOIndex;
        this.intervalSec = s.interval;
        this.framesPerInterval = framesPerInterval;
        this.lenr = lenr;
        this.mode = ExposureRamp.Mode.of(s.holyGrailAllowExposureUp, s.holyGrailAllowExposureDown);
        this.deadbandEv = Util.DEADBAND_VALUES[s.deadbandIndex];
        this.cooldownSlots = s.cooldown;
        this.targetPending = s.useCurrentExposure;
        this.targetEv = ExposureRamp.thirdsToEv(s.targetExposure);
    }

    /** A meter reading; with "use current exposure" the first one becomes the target. */
    public void onMeter(float ev) {
        lastMeasuredEv = ev;
        if (targetPending) {
            targetPending = false;
            targetEv = ev;
        }
    }

    /**
     * One slot. {@code readShutterIdx} and {@code readIsoIdx} are the table indexes from this slot's camera read,
     * or {@link ExposureRamp#UNKNOWN}. An unknown shutter keeps the last known index; an unknown ISO does not.
     */
    public ExposureRamp.Action onSlot(float measuredEv, int readShutterIdx, int readIsoIdx) {
        onMeter(measuredEv);
        history.add(measuredEv);
        if (readShutterIdx != ExposureRamp.UNKNOWN)
            shutterIndex = readShutterIdx;
        isoIndex = readIsoIdx;

        ExposureRamp.Input in = new ExposureRamp.Input();
        in.measuredEv = history.average();
        in.targetEv = targetEv;
        in.shutterIndex = shutterIndex;
        in.isoIndex = isoIndex;
        in.maxShutterIndex = maxShutterIndex;
        in.maxIsoIndex = maxIsoIndex;
        in.intervalSec = intervalSec;
        in.framesPerInterval = framesPerInterval;
        in.lenr = lenr;
        in.mode = mode;
        in.deadbandEv = deadbandEv;
        in.cooldownSlots = cooldownSlots;
        in.slotsSinceChange = slotsSinceChange;
        ExposureRamp.Action action = ramp.decide(in);
        slotsSinceChange++;
        return action;
    }

    /** The camera outcome of the action {@link #onSlot} returned. A failure changes nothing: the next slot re-reads. */
    public void onApplied(ExposureRamp.Action action, boolean success) {
        if (!success || action.isNone())
            return;
        switch (action.kind) {
            case SHUTTER_LONGER:
                shutterIndex++;
                break;
            case SHUTTER_SHORTER:
                shutterIndex--;
                break;
            case ISO_UP:
                isoIndex++;
                break;
            default:
                isoIndex--;
                break;
        }
        slotsSinceChange = 1;
    }

    /** The ISO value to set for an ISO action. */
    public int isoValueAfter(ExposureRamp.Action action) {
        return ramp.tables().isoAt(action.kind == ExposureRamp.Kind.ISO_UP ? isoIndex + 1 : isoIndex - 1);
    }

    public ExposureRamp.Tables tables() {
        return ramp.tables();
    }

    public float targetEv() {
        return targetEv;
    }

    public float measuredEv() {
        return lastMeasuredEv;
    }

    public float averageEv() {
        return history.average();
    }

    public int shutterIndex() {
        return shutterIndex;
    }

    public int isoIndex() {
        return isoIndex;
    }

    public int slotsSinceChange() {
        return slotsSinceChange;
    }

    public int maxShutterLimit() {
        return ramp.tables().maxShutterIndex(maxShutterIndex, intervalSec, framesPerInterval, lenr);
    }
}
