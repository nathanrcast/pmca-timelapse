package io.github.nathanrcast.timelapse.core.camera;

import static io.github.nathanrcast.timelapse.core.camera.Probes.list;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DriveReadbackTest {
    private static Map<Query, List<String>> values(String drive) {
        Map<Query, List<String>> v = new EnumMap<Query, List<String>>(Query.class);
        if (drive != null)
            v.put(Query.DRIVE_MODE, list(drive));
        v.put(Query.BRACKET_MODE, list("exposure"));
        v.put(Query.EXPOSURE_BRACKET_MODE, list("single"));
        v.put(Query.NUM_OF_BRACKET_PICTURE, list("3"));
        v.put(Query.EXPOSURE_BRACKET_PERIOD, list("3"));
        return v;
    }

    @Test
    public void acceptedBracketDriveIsNotRefused() {
        DriveReadback r = DriveReadback.of(values("bracket"));
        assertFalse(r.bracketRefused());
        assertEquals("drive=bracket bracket=exposure exposureBracket=single count=3 period=3", r.describe());
    }

    @Test
    public void aBodyThatKeptBurstDriveRefusedBracketing() {
        assertTrue(DriveReadback.of(values("burst")).bracketRefused());
    }

    @Test
    public void anUnreadableDriveIsNotCalledARefusal() {
        DriveReadback r = DriveReadback.of(values(null));
        assertFalse(r.bracketRefused());
        assertTrue(r.describe(), r.describe().startsWith("drive=? "));
    }
}
