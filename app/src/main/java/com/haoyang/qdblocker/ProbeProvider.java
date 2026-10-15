package com.haoyang.qdblocker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.Arrays;

/** Narrow IPC channel: only this app and the original QDReader UID may call it. */
public final class ProbeProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://com.haoyang.qdblocker.probe");
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        boolean own = uid == Process.myUid();
        boolean phone = packages != null && Arrays.asList(packages).contains(PhoneBridge.PACKAGE) && PhoneBridge.isOfficial(getContext());
        if ("network_config".equals(method) || "network_event".equals(method)) {
            if (!own && !phone) throw new SecurityException("Only the official phone plugin may access network rules");
            Bundle result = new Bundle();
            if ("network_config".equals(method)) {
                result.putString("rules", ProbeStore.prefs(getContext()).getString("blacklist", ""));
                result.putBoolean("enabled", ProbeStore.prefs(getContext()).getBoolean("network_enabled", true));
                result.putBoolean("paragraphs", ProbeStore.prefs(getContext()).getBoolean("paragraph_enabled", true));
            } else if (extras != null) {
                String kind = extras.getString("kind", "");
                int removed = Math.max(0, Math.min(2000, extras.getInt("removed", 0)));
                if (Arrays.asList("ready", "filtered", "paragraph_filtered", "alive", "tls_ready", "tls_failed", "upstream_failed", "message_request").contains(kind)) {
                    long now = System.currentTimeMillis();
                    android.content.SharedPreferences prefs = ProbeStore.prefs(getContext());
                    android.content.SharedPreferences.Editor edit = prefs.edit().putLong("network_last_event", now);
                    if (removed > 0) edit.putLong("network_filtered_total", prefs.getLong("network_filtered_total", 0) + removed);
                    edit.apply();
                    if (!"alive".equals(kind)) ProbeStore.append(getContext(), "PHONE_FILTER " + kind + " removed=" + removed);
                }
            }
            return result;
        }
        if (uid != Process.myUid() && (packages == null ||
                (!Arrays.asList(packages).contains("com.qidian.QDReader") &&
                 !Arrays.asList(packages).contains("com.haoyang.qdblocker.lab")))) {
            throw new SecurityException("Only QDReader may access diagnostics");
        }
        Bundle result = new Bundle();
        if ("config".equals(method)) {
            result.putBoolean("diagnostics", ProbeStore.prefs(getContext()).getBoolean("diagnostics", true));
            result.putString("rules", ProbeStore.prefs(getContext()).getString("blacklist", ""));
            // Blacklist is stored for the next phase. No filtering until identity is verified.
            result.putBoolean("filtering", false);
        } else if ("event".equals(method)) {
            if (ProbeStore.prefs(getContext()).getBoolean("diagnostics", true) && arg != null) {
                ProbeStore.append(getContext(), arg);
            }
        } else throw new IllegalArgumentException("Unsupported method");
        return result;
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
