package io.github.nathanrcast.timelapse.camera;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// The vendored OpenMemories stubs (compileOnly, so not on the test classpath). Their methods have no Code attribute,
// so a JVM refuses to define the classes; tests read signatures and constants straight from the class files instead.
public final class StubsJar {
    private static final String JAR = "libs/openmemories-stubs-f8df350.jar";

    private StubsJar() {}

    static File file() {
        File jar = new File(JAR);
        if (!jar.exists())
            jar = new File("app", JAR);
        assertTrue("stubs jar not found from " + new File(".").getAbsolutePath(), jar.exists());
        return jar;
    }

    // A loader that finds the stub classes, but defining any of them throws ClassFormatError.
    public static ClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[] {file().toURI().toURL()}, StubsJar.class.getClassLoader());
    }

    // One stub class: its public methods as name + descriptor, and its String/int constants.
    public static final class ClassInfo {
        public final Set<String> publicMethods = new HashSet<String>();
        public final Map<String, Object> constants = new HashMap<String, Object>();
    }

    public static ClassInfo read(String className) throws IOException {
        ZipFile zip = new ZipFile(file());
        try {
            ZipEntry entry = zip.getEntry(className.replace('.', '/') + ".class");
            assertNotNull(className, entry);
            DataInputStream in = new DataInputStream(zip.getInputStream(entry));
            try {
                return parse(in);
            } finally {
                in.close();
            }
        } finally {
            zip.close();
        }
    }

    private static ClassInfo parse(DataInputStream in) throws IOException {
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        Object[] pool = new Object[count];
        int[] stringRefs = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: pool[i] = in.readUTF(); break;
                case 3: pool[i] = in.readInt(); break;
                case 4: in.readFloat(); break;
                case 5: in.readLong(); i++; break;
                case 6: in.readDouble(); i++; break;
                case 8: stringRefs[i] = in.readUnsignedShort(); break;
                case 7: case 16: case 19: case 20: in.readUnsignedShort(); break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                case 9: case 10: case 11: case 12: case 17: case 18: in.readInt(); break;
                default: throw new IOException("constant pool tag " + tag);
            }
        }
        for (int i = 1; i < count; i++)
            if (stringRefs[i] != 0)
                pool[i] = pool[stringRefs[i]];

        ClassInfo info = new ClassInfo();
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++)
            in.readUnsignedShort();
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.readUnsignedShort();
            String name = (String) pool[in.readUnsignedShort()];
            in.readUnsignedShort();
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                String attr = (String) pool[in.readUnsignedShort()];
                int length = in.readInt();
                if (attr.equals("ConstantValue")) {
                    info.constants.put(name, pool[in.readUnsignedShort()]);
                } else {
                    in.skipBytes(length);
                }
            }
        }
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            int access = in.readUnsignedShort();
            String name = (String) pool[in.readUnsignedShort()];
            String descriptor = (String) pool[in.readUnsignedShort()];
            if ((access & 0x0001) != 0)
                info.publicMethods.add(name + descriptor);
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                in.readUnsignedShort();
                in.skipBytes(in.readInt());
            }
        }
        return info;
    }
}
