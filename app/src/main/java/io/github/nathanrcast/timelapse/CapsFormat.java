package io.github.nathanrcast.timelapse;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// Pure Java (no android.*) so the CapsDump formatting runs in JVM unit tests.
final class CapsFormat {
    static final int ACTION_DOWN = 0; // KeyEvent.ACTION_DOWN
    static final int ACTION_UP = 1;   // KeyEvent.ACTION_UP

    private static final int MAX_DEPTH = 4;
    private static final int MAX_MESSAGE = 120;

    private CapsFormat() {}

    static String value(Object v) {
        return value(v, 0);
    }

    private static String value(Object v, int depth) {
        if (v == null)
            return "null";
        if (v instanceof String)
            return "\"" + ((String) v).replace("\n", "\\n") + "\"";
        if (v instanceof Number || v instanceof Boolean || v instanceof Character)
            return v.toString();
        if (v instanceof Enum)
            return ((Enum<?>) v).name();
        if (depth >= MAX_DEPTH)
            return "...";
        Class<?> c = v.getClass();
        if (c.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(value(Array.get(v, i), depth + 1));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : (Collection<?>) v) {
                if (!first)
                    sb.append(", ");
                first = false;
                sb.append(value(o, depth + 1));
            }
            return sb.append(']').toString();
        }
        Field first = publicField(c, "first");
        Field second = publicField(c, "second");
        if (first != null && second != null)
            return "(" + fieldValue(first, v, depth) + ", " + fieldValue(second, v, depth) + ")";
        if (!overridesToString(c))
            return fields(v, depth);
        return v.toString().replace("\n", "\\n");
    }

    private static Field publicField(Class<?> c, String name) {
        try {
            Field f = c.getField(name);
            return Modifier.isStatic(f.getModifiers()) ? null : f;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String fieldValue(Field f, Object target, int depth) {
        try {
            return value(f.get(target), depth + 1);
        } catch (Throwable t) {
            return "!! " + failure(t);
        }
    }

    private static boolean overridesToString(Class<?> c) {
        try {
            return c.getMethod("toString").getDeclaringClass() != Object.class;
        } catch (Throwable t) {
            return true;
        }
    }

    private static String fields(Object v, int depth) {
        List<Field> list = new ArrayList<Field>();
        for (Field f : v.getClass().getFields())
            if (!Modifier.isStatic(f.getModifiers()))
                list.add(f);
        Collections.sort(list, new Comparator<Field>() {
            @Override
            public int compare(Field a, Field b) {
                return a.getName().compareTo(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder(typeName(v.getClass())).append('{');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(list.get(i).getName()).append('=').append(fieldValue(list.get(i), v, depth));
        }
        return sb.append('}').toString();
    }

    static String failure(Throwable t) {
        while ((t instanceof InvocationTargetException || t instanceof ExceptionInInitializerError) && t.getCause() != null)
            t = t.getCause();
        String msg = t.getMessage();
        if (msg == null)
            return t.getClass().getName();
        msg = msg.replace("\n", " ");
        if (msg.length() > MAX_MESSAGE)
            msg = msg.substring(0, MAX_MESSAGE) + "...";
        return t.getClass().getName() + ": " + msg;
    }

    static String typeName(Class<?> c) {
        if (c.isArray())
            return typeName(c.getComponentType()) + "[]";
        String n = c.getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    private static String params(Method m) {
        StringBuilder sb = new StringBuilder();
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(typeName(types[i]));
        }
        return sb.toString();
    }

    // "CameraEx.open(int, CameraEx$OpenOptions)": the form USED_METHODS entries are written in.
    static String methodKey(Class<?> owner, Method m) {
        return typeName(owner) + "." + m.getName() + "(" + params(m) + ")";
    }

    static String signature(Method m) {
        return (Modifier.isStatic(m.getModifiers()) ? "static " : "")
                + typeName(m.getReturnType()) + " " + m.getName() + "(" + params(m) + ")";
    }

    // Every public method except java.lang.Object's, sorted by name then parameters.
    static List<Method> methods(Class<?> c) {
        List<Method> list = new ArrayList<Method>();
        for (Method m : c.getMethods())
            if (m.getDeclaringClass() != Object.class)
                list.add(m);
        Collections.sort(list, new Comparator<Method>() {
            @Override
            public int compare(Method a, Method b) {
                return (a.getName() + "(" + params(a)).compareTo(b.getName() + "(" + params(b));
            }
        });
        return list;
    }

    static Set<String> methodKeys(Class<?> c) {
        Set<String> keys = new TreeSet<String>();
        for (Method m : methods(c))
            keys.add(methodKey(c, m));
        return keys;
    }

    private static boolean hasPrefix(String name, String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
                && Character.isUpperCase(name.charAt(prefix.length()));
    }

    private static int getterRank(String name) {
        if (name.startsWith("getSupported"))
            return 0;
        if (name.startsWith("isSupported"))
            return 1;
        return name.startsWith("get") ? 2 : 3;
    }

    // Public no-arg instance get*/is* methods with a return value. Never set*, adjust* or anything taking arguments.
    static boolean isGetter(Method m) {
        int mod = m.getModifiers();
        String name = m.getName();
        return Modifier.isPublic(mod) && !Modifier.isStatic(mod)
                && m.getParameterTypes().length == 0 && m.getReturnType() != void.class
                && m.getDeclaringClass() != Object.class
                && (hasPrefix(name, "get") || hasPrefix(name, "is"));
    }

    // getSupported*, then isSupported*, then get*, then is*; alphabetical within each group.
    static List<Method> getters(Class<?> c) {
        List<Method> list = new ArrayList<Method>();
        for (Method m : c.getMethods())
            if (isGetter(m))
                list.add(m);
        Collections.sort(list, new Comparator<Method>() {
            @Override
            public int compare(Method a, Method b) {
                int ra = getterRank(a.getName()), rb = getterRank(b.getName());
                return ra != rb ? ra - rb : a.getName().compareTo(b.getName());
            }
        });
        return list;
    }

    // One line per call: "name = value" or "name !! <exception>". Never throws.
    static String probe(Object target, Method m) {
        try {
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return m.getName() + " = " + value(m.invoke(target));
        } catch (Throwable t) {
            return m.getName() + " !! " + failure(t);
        }
    }

    static List<String> probeGetters(Object target) {
        List<String> lines = new ArrayList<String>();
        for (Method m : getters(target.getClass()))
            lines.add(probe(target, m));
        return lines;
    }

    // "NAME = value" for each public static final primitive or String field, sorted by name.
    static List<String> constants(Class<?> c) {
        Map<String, String> sorted = new TreeMap<String, String>();
        for (Field f : c.getFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || !Modifier.isFinal(mod))
                continue;
            if (!f.getType().isPrimitive() && f.getType() != String.class)
                continue;
            String v;
            try {
                v = value(f.get(null));
            } catch (Throwable t) {
                v = "!! " + failure(t);
            }
            sorted.put(f.getName(), v);
        }
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<String, String> e : sorted.entrySet())
            lines.add(e.getKey() + " = " + e.getValue());
        return lines;
    }

    // Reverse map of public static int constants named prefix*; names sharing a value are joined with '|'.
    static Map<Integer, String> constantNames(Class<?> c, String prefix) {
        Map<Integer, Set<String>> byValue = new TreeMap<Integer, Set<String>>();
        for (Field f : c.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int.class || !f.getName().startsWith(prefix))
                continue;
            try {
                Integer v = Integer.valueOf(f.getInt(null));
                Set<String> names = byValue.get(v);
                if (names == null) {
                    names = new TreeSet<String>();
                    byValue.put(v, names);
                }
                names.add(f.getName());
            } catch (Throwable ignored) {
            }
        }
        Map<Integer, String> out = new TreeMap<Integer, String>();
        for (Map.Entry<Integer, Set<String>> e : byValue.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (String n : e.getValue()) {
                if (sb.length() > 0)
                    sb.append('|');
                sb.append(n);
            }
            out.put(e.getKey(), sb.toString());
        }
        return out;
    }

    static String keyName(Map<Integer, String> names, int code) {
        String n = names.get(Integer.valueOf(code));
        return n == null ? "?" : n;
    }

    static String actionName(int action, int repeatCount) {
        if (action == ACTION_DOWN)
            return repeatCount > 0 ? "repeat" : "down";
        if (action == ACTION_UP)
            return "up";
        return "action" + action;
    }

    static String keyLine(long millis, String action, int keyCode, int scanCode, String name, String extra) {
        return String.format(Locale.US, "%9.3f %-6s key=%-3d scan=%-4d %s%s",
                millis / 1000.0, action, keyCode, scanCode, name, extra == null ? "" : " " + extra);
    }

    static List<String> usedReport(String[] used, Set<String> present) {
        List<String> lines = new ArrayList<String>();
        int absent = 0;
        for (String u : used) {
            boolean ok = present.contains(u);
            if (!ok)
                absent++;
            lines.add((ok ? "present  " : "ABSENT   ") + u);
        }
        lines.add(absent + " of " + used.length + " absent");
        return lines;
    }

    static String fileSafe(String model) {
        if (model == null || model.trim().length() == 0)
            return "unknown";
        return model.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static List<String> sortedLines(String flattened) {
        List<String> lines = new ArrayList<String>(Arrays.asList(flattened.split(";")));
        Collections.sort(lines);
        return lines;
    }
}
