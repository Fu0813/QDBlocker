package com.haoyang.qdblocker;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small bounded, private diagnostics store; never writes host comment bodies. */
public final class ProbeStore {
    private ProbeStore() {}
    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("qdblocker", Context.MODE_PRIVATE);
    }
    public static synchronized void append(Context context, String event) {
        String safe = event.replace('\r', ' ').replace('\n', ' ');
        if (safe.length() > 2400) safe = safe.substring(0, 2400);
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        SharedPreferences prefs = prefs(context);
        String[] lines = prefs.getString("events", "").split("\n");
        StringBuilder next = new StringBuilder();
        for (int i = Math.max(0, lines.length - 149); i < lines.length; i++) {
            if (!lines[i].isEmpty()) next.append(lines[i]).append('\n');
        }
        next.append(timestamp).append("  ").append(safe);
        // Hard cap by characters as well as event count.
        String value = next.toString();
        if (value.length() > 100_000) {
            int boundary = value.indexOf('\n', value.length() - 100_000);
            value = boundary < 0 ? value.substring(value.length() - 100_000) : value.substring(boundary + 1);
        }
        prefs.edit().putString("events", value).putLong("last_event", System.currentTimeMillis()).apply();
    }
}
