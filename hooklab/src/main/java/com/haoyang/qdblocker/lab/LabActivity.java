package com.haoyang.qdblocker.lab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.qidian.QDReader.repository.entity.chaptercomment.NewParagraphCommentListBean;
import com.qidian.QDReader.ui.viewholder.chaptercomment.list.NewParagraphCommentListMainViewHolder;
import com.qidian.QDReader.ui.viewholder.chaptercomment.list.NewParagraphCommentListReplyViewHolder;

/** Synthetic fixtures only: validates injection, never connects to Qidian servers. */
public final class LabActivity extends Activity {
    private TextView result;
    private int runs;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(48, 140, 48, 64);
        TextView title = new TextView(this); title.setText("免 Root Hook 实验室"); title.setTextSize(25); page.addView(title);
        TextView info = new TextView(this); info.setText("这里是模拟段评，不是真实起点数据。点击后核对 QDBlocker 中的 BIND 事件，验证 LSPatch 注入和诊断通道。"); page.addView(info);
        Button run = new Button(this); run.setText("运行模拟段评绑定"); page.addView(run);
        result = new TextView(this); result.setTextSize(17); page.addView(result);
        run.setOnClickListener(v -> simulate()); setContentView(page);
        page.postDelayed(this::simulate, 1500);
    }
    private void simulate() {
        NewParagraphCommentListBean.DataListBean comment = new NewParagraphCommentListBean.DataListBean();
        comment.UserId = 10001; comment.ReviewId = 20001; comment.NickName = "测试用户"; comment.Content = "模拟主评论，应保持原样";
        NewParagraphCommentListBean.BookInfoBean book = new NewParagraphCommentListBean.BookInfoBean();
        new NewParagraphCommentListMainViewHolder().bind(comment, book);
        NewParagraphCommentListBean.DataListBean reply = new NewParagraphCommentListBean.DataListBean();
        reply.UserId = 10002; reply.ReviewId = 20002; reply.NickName = "其他模拟用户"; reply.Content = "模拟回复，应保持原样";
        new NewParagraphCommentListReplyViewHolder().bind(reply, book);
        result.setText("第 " + (++runs) + " 轮\n" + comment.Content + "\n" + reply.Content + "\n\n通过标准：两条内容不变，并收到主评论和回复的 BIND 日志。");
    }
}
