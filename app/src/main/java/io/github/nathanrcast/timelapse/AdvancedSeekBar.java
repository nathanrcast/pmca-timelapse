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

        if (event == null || event.getRepeatCount() == 0) {
            lastHoldActionTime = System.currentTimeMillis();
            setProgress(getProgress() + value);
            return;
        }

        jump(value);
    }

    // Held key: one step per repeat, at most 6 times a second. The first press is one step too (SeekBar's own).
    private void jump(int value) {
        long time = System.currentTimeMillis();
        if (time - lastHoldActionTime < 160)
            return;
        lastHoldActionTime = time;
        setProgress(getProgress() + (value > 0 ? 1 : -1));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean right = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if ((right || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) && event.getRepeatCount() > 0) {
            int p = getProgress();
            if ((right && p >= getMax()) || (!right && p <= 0))
                return false;
            jump(right ? 1 : -1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
