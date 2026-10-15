package com.qidian.QDReader.ui.viewholder.chaptercomment.list;
import com.qidian.QDReader.repository.entity.chaptercomment.NewParagraphCommentListBean;
public final class NewParagraphCommentListMainViewHolder {
    public void bind(NewParagraphCommentListBean.DataListBean data, NewParagraphCommentListBean.BookInfoBean book) {
        if (data.Content == null) throw new IllegalArgumentException("fixture content missing");
    }
}
