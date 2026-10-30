package io.github.nathanrcast.timelapse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.nathanrcast.timelapse.core.camera.SonyMethod;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SonyMethodKeyTest {
    private static final Map<String, Class<?>> NON_SONY = new HashMap<String, Class<?>>();

    static {
        NON_SONY.put("int", int.class);
        NON_SONY.put("boolean", boolean.class);
        NON_SONY.put("String", String.class);
        NON_SONY.put("Camera$Parameters", android.hardware.Camera.Parameters.class);
    }

    // CapsDump's used-methods report matches SonyMethod.key() against CapsFormat.methodKey of the firmware's
    // methods, so every type name in a key must be what CapsFormat.typeName prints for that type.
    @Test
    public void keyTypeNamesAreCapsFormatTypeNames() {
        for (SonyMethod m : SonyMethod.values()) {
            assertTrue(m.owner(), m.owner().startsWith("CameraEx"));
            for (String t : m.parameterTypes()) {
                Class<?> c = NON_SONY.get(t);
                if (c != null)
                    assertEquals(t, CapsFormat.typeName(c));
                else
                    assertTrue(m.key(), t.startsWith("CameraEx$") && t.indexOf('.') < 0);
            }
        }
    }
}
