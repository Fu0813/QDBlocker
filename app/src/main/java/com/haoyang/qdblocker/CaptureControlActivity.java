package com.haoyang.qdblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fixed app-scoped capture configuration; no API key or bypass of user consent. */
public final class CaptureControlActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        PhoneBridge.initialize(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(40, 120, 40, 48);
        status = new TextView(this); status.setTextSize(17);
        status.setText("手机本地屏蔽\n\n使用 PCAPdroid 和手机端插件，仅处理起点读书。先安装插件证书并启用 QDBlocker 脚本。无需电脑或 USB，Wi-Fi 代理应设为无。"); page.addView(status);
        Button start = new Button(this); start.setText("开启手机屏蔽"); page.addView(start);
        start.setOnClickListener(v -> command("status"));
        Button check = new Button(this); check.setText("检查转发状态"); page.addView(check);
        check.setOnClickListener(v -> command("check"));
        Button stop = new Button(this); stop.setText("停止手机屏蔽"); page.addView(stop);
        stop.setOnClickListener(v -> command("stop"));
        setContentView(page);
        if (savedState == null) {
            String action = getIntent().getStringExtra("action");
            command("stop".equals(action) ? "stop" : "check".equals(action) ? "check" : "status");
        }
    }
    private void command(String action) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName("com.emanuelef.remote_capture", "com.emanuelef.remote_capture.activities.CaptureCtrl");
        intent.putExtra("action", ("check".equals(action) || "status".equals(action)) ? "get_status" : action);
        if ("start".equals(action)) {
            intent.putExtra("pcap_dump_mode", "none");
            intent.putExtra("app_filter", "com.qidian.QDReader");
            intent.putExtra("root_capture", "false");
            intent.putExtra("socks5_enabled", "false");
            intent.putExtra("tls_decryption", "true");
            intent.putExtra("full_payload", "true");
            intent.putExtra("decryption_rules", "[{\"type\":\"APP\",\"value\":\"com.qidian.QDReader\"}]");
            intent.putExtra("mitmproxy_opts", "--allow-hosts ^druidv6\\.if\\.qidian\\.com(:[0-9]+)?$ --set connection_strategy=eager");
            intent.putExtra("ip_mode", "both");
            intent.putExtra("block_quic", "always");
            intent.putExtra("auto_block_private_dns", "false");
        }
        try { startActivityForResult(intent, "start".equals(action) ? 101 : "stop".equals(action) ? 102 : "check".equals(action) ? 104 : 103); }
        catch (RuntimeException error) { status.setText("未能打开 PCAPdroid：" + error.getClass().getSimpleName()); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 103 || requestCode == 104) {
            if (resultCode != RESULT_OK || data == null) {
                status.setText("未能读取 VPN 状态，请确认 PCAPdroid 控制授权。");
                return;
            }
            boolean running = data.getBooleanExtra("running", false);
            if (requestCode == 103 && !running) { command("start"); return; }
            String details = "VPN " + (running ? "运行中" : "已停止")
                    + "，发送 " + data.getLongExtra("bytes_sent", 0)
                    + " 字节，接收 " + data.getLongExtra("bytes_rcvd", 0) + " 字节";
            status.setText(details);
            ProbeStore.append(this, "CAPTURE_STATUS " + details);
            return;
        }
        String result = (requestCode == 101 ? "启动" : "停止") + (resultCode == RESULT_OK ? "成功" : "未完成或未获授权");
        status.setText(result + "\n仅针对起点，流量在手机本地处理。请进入起点刷新“评论和 @”，再回首页检查过滤计数。启动成功不代表过滤脚本已生效。");
        ProbeStore.append(this, "CAPTURE_CONTROL " + result);
    }
}
