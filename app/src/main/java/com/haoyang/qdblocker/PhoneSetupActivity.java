package com.haoyang.qdblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;

/** System file picker exports code only; blacklist stays private in QDBlocker. */
public final class PhoneSetupActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PhoneBridge.initialize(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(40, 120, 40, 40);
        status = new TextView(this); status.setTextSize(17);
        status.setText("首次设置\n\n1. 安装官方 PCAPdroid 和 PCAPdroid mitm。\n2. 在 PCAPdroid 开启 TLS 解密并安装本地 CA。\n3. 将过滤脚本导出到 Documents/QDBlocker 文件夹。\n4. 在 mitm 的 Addons 中选择该文件夹，刷新并开启 QDBlocker。无需所有文件访问权限。\n5. Wi-Fi 代理设为无，回到 QDBlocker 开启手机屏蔽。\n\n以后可直接在首页修改名单，保存后刷新起点生效。"); page.addView(status);
        Button export = new Button(this); export.setText("导出 QDBlocker.py"); page.addView(export);
        export.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("text/x-python").putExtra(Intent.EXTRA_TITLE, "QDBlocker.py"), 301));
        Button plugin = new Button(this); plugin.setText("打开解密插件"); page.addView(plugin);
        plugin.setOnClickListener(v -> open("com.pcapdroid.mitm"));
        Button capture = new Button(this); capture.setText("打开 PCAPdroid"); page.addView(capture);
        capture.setOnClickListener(v -> open("com.emanuelef.remote_capture"));
        ScrollView scroll = new ScrollView(this); scroll.addView(page); setContentView(scroll);
    }
    private void open(String name) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(name);
        if (intent != null) startActivity(intent); else Toast.makeText(this, "请先安装官方应用", Toast.LENGTH_SHORT).show();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 301 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream input = getAssets().open("QDBlocker.py"); OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new java.io.IOException();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            status.setText("脚本已导出。请在 PCAPdroid mitm 的 Addons 中选择所在文件夹，刷新并开启 QDBlocker。更新脚本后需重启手机屏蔽。");
        } catch (Exception error) { status.setText("导出失败，请重新选择可写入的位置。"); }
    }
}
