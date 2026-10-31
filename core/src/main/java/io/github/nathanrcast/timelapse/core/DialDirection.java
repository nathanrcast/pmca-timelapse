package io.github.nathanrcast.timelapse.core;

/**
 * Dial steps from a key event. The direction comes from which scan code arrived (clockwise or counter-clockwise),
 * never from the sign of the dial status: the NEX-5T reports -22 for DIAL_1_CLOCKWISE and +22 for COUNTERCW, the
 * a6500 the opposite. The status only supplies the size of a multi-notch event.
 */
public final class DialDirection {
    /** One notch as the cameras report it. */
    public static final int NOTCH = 22;

    private DialDirection() {}

    /** +n for clockwise, -n for counter-clockwise; n is 1 unless |status| exceeds one notch. */
    public static int steps(boolean clockwise, int status) {
        int magnitude = Math.abs(status);
        int n = magnitude > NOTCH ? magnitude / NOTCH : 1;
        return clockwise ? n : -n;
    }
}
