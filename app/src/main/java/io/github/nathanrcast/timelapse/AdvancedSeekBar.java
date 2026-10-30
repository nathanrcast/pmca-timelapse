package io.github.nathanrcast.timelapse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.SeekBar;

/**
 * Created by jonas on 2/12/17.
 */

public class AdvancedSeekBar extends SeekBar {
    public AdvancedSeekBar(Context context) {
        super(context);
    }

    public AdvancedSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AdvancedSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private long lastHoldActionTime = 0;

    public void dialChanged(int value) {
        dialChanged(value, null);
    }

    public void dialChanged(int value, KeyEvent event) {
        if(!isFocused())
            return;

        long time = System.currentTimeMillis();
        boolean hold = event != null && event.getRepeatCount() > 0;

        if (!hold) {
            lastHoldActionTime = time;
            setProgress(getProgress() + value);
            return;
        }

        // Held key: jump to the next multiple of 10, at most 3 times a second
        if (time - lastHoldActionTime < 300)
            return;
        lastHoldActionTime = time;

        int current = getProgress();
        int next;
        if (value > 0)
            next = (current / 10 + 1) * 10;
        else
            next = current == 0 ? 0 : ((current - 1) / 10) * 10;
        setProgress(next);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            dialChanged(1, event);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            dialChanged(-1, event);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
