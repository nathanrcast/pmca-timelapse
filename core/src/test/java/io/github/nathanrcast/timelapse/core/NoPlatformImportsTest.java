package io.github.nathanrcast.timelapse.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

// :core must stay a plain JVM library: no Android framework or Sony firmware classes.
public class NoPlatformImportsTest {
    private static final Pattern FORBIDDEN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?(android|com\\.sony)\\.");

    @Test
    public void coreSourcesImportNoAndroidOrSonyClasses() throws IOException {
        File root = new File("src/main/java");
        assertTrue("run from the core project dir: " + root.getAbsolutePath(), root.isDirectory());

        List<File> sources = new ArrayList<File>();
        collectJava(root, sources);
        assertTrue("no sources found under " + root, !sources.isEmpty());

        List<String> offending = new ArrayList<String>();
        for (File source : sources) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), "UTF-8"));
            try {
                String line;
                int number = 0;
                while ((line = reader.readLine()) != null) {
                    number++;
                    if (FORBIDDEN.matcher(line).find())
                        offending.add(source.getPath() + ":" + number + ": " + line.trim());
                }
            } finally {
                reader.close();
            }
        }
        if (!offending.isEmpty())
            fail("android.*/com.sony.* imports in :core:\n" + join(offending));
    }

    @Test
    public void patternCatchesPlainAndStaticImports() {
        assertTrue(FORBIDDEN.matcher("import android.content.Context;").find());
        assertTrue(FORBIDDEN.matcher("  import static android.os.SystemClock.uptimeMillis;").find());
        assertTrue(FORBIDDEN.matcher("import com.sony.scalar.hardware.CameraEx;").find());
        assertTrue(!FORBIDDEN.matcher("import java.util.List;").find());
        assertTrue(!FORBIDDEN.matcher("import com.sonyx.Foo;").find());
    }

    private static void collectJava(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            if (child.isDirectory())
                collectJava(child, out);
            else if (child.getName().endsWith(".java"))
                out.add(child);
        }
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
            sb.append(line).append('\n');
        return sb.toString();
    }
}
