package com.haoyang.qdblocker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.util.LinkedHashSet;

public final class MainActivity extends Activity {
    private static final int RED = 0xFFC92C45, INK = 0xFF222633, MUTED = 0xFF69707E;
    private TextView status, events;
    private EditText blacklist;
    private LinearLayout page;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PhoneBridge.initialize(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xFFF6F5F3);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF6F5F3);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(24)); scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        TextView eyebrow = label("QDBLOCKER  /  手机本地版", 12, RED); page.addView(eyebrow);
        TextView title = label("让阅读回归清净", 29, INK); title.setTypeface(null, Typeface.BOLD); add(title, 14);
        add(label("黑名单管理 · 阅读段评 · 评论与 @", 14, MUTED), 8);

        LinearLayout probe = card("01   手机屏蔽");
        status = label("", 14, INK); probe.addView(status);
        TextView intro = label("通过 PCAPdroid 和手机端插件处理原版起点，无需电脑或 Root。支持评论和 @ 消息，以及已适配的阅读段评。回复者命中黑名单时保留主评论；主评论作者命中时隐藏其讨论串。整页被屏蔽时可能提前停止加载。", 14, MUTED);
        intro.setPadding(0, dp(10), 0, dp(6)); probe.addView(intro);
        Switch enabled = new Switch(this); enabled.setText("启用黑名单过滤");
        enabled.setChecked(ProbeStore.prefs(this).getBoolean("network_enabled", true));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            ProbeStore.prefs(this).edit().putBoolean("network_enabled", checked).apply();
            Toast.makeText(this, "已保存，约 2 秒后刷新起点页面生效", Toast.LENGTH_SHORT).show();
        }); probe.addView(enabled);
        Switch paragraphs = new Switch(this); paragraphs.setText("屏蔽阅读中的黑名单段评");
        paragraphs.setChecked(ProbeStore.prefs(this).getBoolean("paragraph_enabled", true));
        paragraphs.setOnCheckedChangeListener((button, checked) -> {
            ProbeStore.prefs(this).edit().putBoolean("paragraph_enabled", checked).apply();
            Toast.makeText(this, "已保存，约 2 秒后重新打开段评生效", Toast.LENGTH_SHORT).show();
        }); probe.addView(paragraphs);
        Button control = button("开启 / 检查 / 停止屏蔽"); probe.addView(control);
        control.setOnClickListener(v -> startActivity(new Intent(this, CaptureControlActivity.class)));
        Button setup = button("插件设置与导出脚本"); probe.addView(setup);
        setup.setOnClickListener(v -> startActivity(new Intent(this, PhoneSetupActivity.class)));
        Switch diagnostics = new Switch(this); diagnostics.setText("记录诊断信息");
        diagnostics.setChecked(ProbeStore.prefs(this).getBoolean("diagnostics", true));
        diagnostics.setOnCheckedChangeListener((button, checked) -> {
            ProbeStore.prefs(this).edit().putBoolean("diagnostics", checked).apply();
            Toast.makeText(this, "已保存，诊断开关会在约 5 秒内同步", Toast.LENGTH_SHORT).show();
        }); probe.addView(diagnostics);
        Button open = button("打开起点读书"); probe.addView(open);
        open.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.qidian.QDReader");
            if (intent != null) startActivity(intent);
            else Toast.makeText(this, "未检测到起点读书", Toast.LENGTH_SHORT).show();
        });

        LinearLayout rules = card("02   用户黑名单");
        TextView target = label("名单仅保存在你的手机中", 14, RED);
        target.setPadding(0, 0, 0, dp(8)); rules.addView(target);
        rules.addView(label("每行一条：name:昵称 或 uid:数字ID。任一规则匹配即屏蔽；昵称可能受同名、改名影响，建议使用 UID。保存后约 2 秒刷新起点页面生效。", 14, MUTED));
        blacklist = new EditText(this); blacklist.setTextSize(15);
        blacklist.setHint("name:测试用户\nuid:123456"); blacklist.setMinLines(3);
        blacklist.setGravity(android.view.Gravity.TOP);
        blacklist.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        blacklist.setText(ProbeStore.prefs(this).getString("blacklist", "")); rules.addView(blacklist);
        Button save = button("保存名单"); rules.addView(save);
        save.setOnClickListener(v -> saveBlacklist());

        LinearLayout logs = card("03   最近诊断");
        logs.addView(label("记录方法、字段结构和候选 ID，不记录评论正文、Cookie 或请求内容。最多保留 150 条。", 13, MUTED));
        LinearLayout actions = new LinearLayout(this);
        Button refresh = button("刷新"); Button share = button("导出"); Button clear = button("清空");
        actions.addView(refresh); actions.addView(share); actions.addView(clear); logs.addView(actions);
        refresh.setOnClickListener(v -> refresh());
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "QDBlocker 0.1.0 / observation only\n" + ProbeStore.prefs(this).getString("events", ""));
            startActivity(Intent.createChooser(send, "导出诊断"));
        });
        clear.setOnClickListener(v -> {
            ProbeStore.prefs(this).edit().remove("events").remove("last_event").apply(); refresh();
        });
        events = label("", 12, MUTED); events.setTypeface(Typeface.MONOSPACE);
        events.setTextIsSelectable(true); logs.addView(events);
        add(label("仅修改本机收到的消息列表，不删除服务器上的评论。", 12, MUTED), 20);
    }
    @Override public void onResume() { super.onResume(); if (status != null) refresh(); }
    private void saveBlacklist() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String entry : blacklist.getText().toString().split("\\R")) {
            String token = entry.trim();
            if (token.isEmpty()) continue;
            if (token.matches("[1-9][0-9]{0,19}")) token = "uid:" + token;
            if (!(token.matches("uid:[1-9][0-9]{0,19}") || (token.startsWith("name:") &&
                    token.length() > 5 && token.length() <= 85 && token.substring(5).trim().equals(token.substring(5))))) {
                blacklist.setError("请每行输入 name:昵称 或 uid:数字ID，昵称不能留空或带首尾空格"); return;
            }
            ids.add(token);
        }
        String normalized = String.join("\n", ids);
        ProbeStore.prefs(this).edit().putString("blacklist", normalized).apply();
        blacklist.setText(normalized);
        Toast.makeText(this, "已保存 " + ids.size() + " 条规则，约 2 秒后刷新起点生效", Toast.LENGTH_SHORT).show();
    }
    private void refresh() {
        String text = ProbeStore.prefs(this).getString("events", "");
        long last = ProbeStore.prefs(this).getLong("network_last_event", 0);
        long total = ProbeStore.prefs(this).getLong("network_filtered_total", 0);
        status.setText((last == 0 ? "尚未收到手机过滤插件连接" : "手机插件最近通信：" + Math.max(0, (System.currentTimeMillis() - last) / 1000) + " 秒前")
                + "\n累计屏蔽 " + total + " 条（重复刷新也计数）");
        events.setText(text.isEmpty() ? "暂无记录。开启手机屏蔽后，进入起点刷新消息，再回到这里查看。" : text);
    }
    private LinearLayout card(String title) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE); background.setCornerRadius(dp(18)); box.setBackground(background);
        TextView heading = label(title, 16, INK); heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(14)); box.addView(heading); add(box, 20); return box;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(4), 1); return view;
    }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(RED); b.setAllCaps(false); return b; }
    private void add(View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); page.addView(view, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
