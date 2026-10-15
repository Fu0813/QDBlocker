package com.haoyang.qdblocker.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/** Bounded structural inspection; deliberately avoids serializing host objects. */
public final class ProbeInspector {
    private ProbeInspector() {}
    public static boolean matchesExactName(Object value, String expected) {
        return expected != null && !expected.isEmpty() && matchesExactName(value, expected, 0);
    }
    private static boolean matchesExactName(Object value, String expected, int depth) {
        if (value == null || depth > 1) return false;
        int examined = 0;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (++examined > 80 || Modifier.isStatic(field.getModifiers())) continue;
                String key = field.getName().toLowerCase(Locale.ROOT).replace("_", "");
                boolean name = key.equals("username") || key.equals("nickname") || key.equals("authorname") ||
                        key.equals("sendername") || (depth > 0 && key.equals("name"));
                boolean nested = key.equals("user") || key.equals("userinfo") || key.equals("author") || key.equals("sender");
                if (!name && !nested) continue;
                try {
                    field.setAccessible(true);
                    Object item = field.get(value);
                    if (name && item instanceof String && expected.equals(item)) return true;
                    if (nested && matchesExactName(item, expected, depth + 1)) return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return false;
    }
    public static String schema(Object value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.getClass().getName()).append('{');
        int count = 0;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (++count > 80) return out.append("...}").toString();
                if (count > 1) out.append(',');
                out.append(field.getName()).append(':').append(field.getType().getSimpleName());
            }
        }
        return out.append('}').toString();
    }
    public static String candidateIds(Object value) {
        StringBuilder result = new StringBuilder();
        collect(value, "", 0, result);
        return result.length() == 0 ? "none (mapping unverified)" : result.toString();
    }
    private static void collect(Object value, String prefix, int depth, StringBuilder out) {
        if (value == null || depth > 1 || out.length() > 1500) return;
        int examined = 0;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (++examined > 80 || Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName().toLowerCase(Locale.ROOT).replace("_", "");
                boolean id = name.equals("id") || name.equals("uid") || name.equals("userid") ||
                        name.equals("authorid") || name.equals("senderid") || name.equals("commentid") ||
                        name.equals("reviewid") || name.equals("replyid") || name.equals("touserid");
                boolean nestedUser = name.equals("user") || name.equals("userinfo") || name.equals("author") || name.equals("sender");
                if (!id && !nestedUser) continue;
                try {
                    field.setAccessible(true);
                    Object item = field.get(value);
                    if (id && (item instanceof Number || item instanceof String)) {
                        String numeric = item.toString();
                        if (numeric.matches("[0-9]{1,20}")) {
                            if (out.length() > 0) out.append(',');
                            out.append(prefix).append(field.getName()).append('=').append(numeric);
                        }
                    } else if (nestedUser) collect(item, prefix + field.getName() + '.', depth + 1, out);
                } catch (ReflectiveOperationException | RuntimeException ignored) { /* Skip unavailable fields. */ }
            }
        }
    }
}
