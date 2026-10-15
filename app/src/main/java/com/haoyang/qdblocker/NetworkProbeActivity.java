package com.haoyang.qdblocker;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Fixed public endpoint only; never accepts a user-provided URL or reads response bodies. */
public final class NetworkProbeActivity extends Activity {
    private TextView output;
    private final StringBuilder report = new StringBuilder();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        output = new TextView(this); output.setTextSize(16); output.setPadding(36, 100, 36, 50);
        setContentView(output);
        line("网络诊断：只请求起点公开首页，不读取正文或账号信息。");
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        ProxyInfo info = manager.getDefaultProxy();
        line("System proxy: " + (info == null ? "none" : info.getHost() + ":" + info.getPort()));
        line("Java selection: " + ProxySelector.getDefault().select(URI.create("https://www.qidian.com/")));
        new Thread(() -> {
            request("system", null);
            request("explicit-usb", new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 18080)));
            line("诊断结束。");
        }, "QDBlocker-network-check").start();
    }
    private void request(String mode, Proxy proxy) {
        HttpsURLConnection connection = null;
        try {
            URL endpoint = new URL("https://www.qidian.com/?qdblocker_probe=" + mode + "-" + System.currentTimeMillis());
            connection = (HttpsURLConnection) (proxy == null ? endpoint.openConnection() : endpoint.openConnection(proxy));
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setUseCaches(false); connection.setRequestProperty("User-Agent", "QDBlocker-network-check");
            line(mode + ": HTTP " + connection.getResponseCode() + "; TLS validated");
        } catch (Exception error) {
            // Exception type is useful; messages may contain URLs so are deliberately excluded.
            line(mode + ": " + error.getClass().getSimpleName());
        } finally { if (connection != null) connection.disconnect(); }
    }
    private synchronized void line(String value) {
        report.append(value).append('\n');
        String snapshot = report.toString();
        Log.i("QDBlockerNet", value);
        try (java.io.FileOutputStream file = openFileOutput("network-probe.txt", MODE_PRIVATE)) {
            file.write(snapshot.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) { }
        runOnUiThread(() -> output.setText(snapshot));
    }
}
