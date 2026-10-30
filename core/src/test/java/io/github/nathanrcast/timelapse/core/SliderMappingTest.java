package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SliderMappingTest {
    @Test
    public void targetExposureRoundTripIsIdentity() {
        for (int stored = -30; stored <= 30; stored++)
            assertEquals(stored, SliderMapping.progressToTargetExposure(SliderMapping.targetExposureToProgress(stored)));
        for (int progress = 0; progress <= SliderMapping.TARGET_EXPOSURE_MAX; progress++)
            assertEquals(progress, SliderMapping.targetExposureToProgress(SliderMapping.progressToTargetExposure(progress)));
    }

    @Test
    public void countRoundTripIsIdentity() {
        for (int stored = 1; stored <= SliderMapping.COUNT_MAX + 1; stored++)
            assertEquals(stored, SliderMapping.progressToCount(SliderMapping.countToProgress(stored)));
        for (int progress = 0; progress <= SliderMapping.COUNT_MAX; progress++)
            assertEquals(progress, SliderMapping.countToProgress(SliderMapping.progressToCount(progress)));
    }

    @Test
    public void defaultsLoadAtTheirOwnPosition() {
        assertEquals(30, SliderMapping.targetExposureToProgress(0));
        assertEquals(4, SliderMapping.countToProgress(5));
        assertEquals(0, SliderMapping.countToProgress(1));
    }

    @Test
    public void outOfRangeValuesClampToTheSlider() {
        assertEquals(0, SliderMapping.targetExposureToProgress(-90));
        assertEquals(SliderMapping.TARGET_EXPOSURE_MAX, SliderMapping.targetExposureToProgress(99));
        assertEquals(0, SliderMapping.countToProgress(0));
        assertEquals(SliderMapping.COUNT_MAX, SliderMapping.countToProgress(500));
    }
}
