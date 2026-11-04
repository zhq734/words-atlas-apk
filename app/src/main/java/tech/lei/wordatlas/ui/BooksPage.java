package tech.lei.wordatlas.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Media;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;

/**
 * 书房页：展示书目封面、简介与全部章节目录，点击章节进入双语阅读。
 *
 * @author zhenghq
 */
public class BooksPage extends MainActivity.Page {

    private LinearLayout col;
    private boolean loading;

    /**
     * @param host 主界面
     */
    public BooksPage(MainActivity host) {
        super(host);
    }

    @Override
    protected void build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col = new LinearLayout(host);
        col.setOrientation(VERTICAL);
        col.setPadding(Theme.dp(host, 18), Theme.dp(host, 16),
                Theme.dp(host, 18), Theme.dp(host, 28));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(Theme.text(host, "书房", 24, Theme.text(), true));

        loadBook();
    }

    /** 后台读取书目信息。 */
    private void loadBook() {
        if (loading) {
            return;
        }
        loading = true;
        new Thread(() -> {
            final Models.BookInfo b = Repo.get(host).book();
            host.runOnUiThread(() -> {
                loading = false;
                renderBook(b);
            });
        }).start();
    }

    /**
     * 渲染书籍与章节。
     *
     * @param b 书目
     */
    private void renderBook(Models.BookInfo b) {
        while (col.getChildCount() > 1) {
            col.removeViewAt(1);
        }
        if (b == null) {
            TextView tip = Theme.text(host, "书库数据加载失败，请稍后再试。", 13, Theme.textDim(), false);
            tip.setPadding(0, Theme.dp(host, 24), 0, 0);
            col.addView(tip);
            return;
        }

        // 封面卡
        LinearLayout coverCard = new LinearLayout(host);
        coverCard.setOrientation(HORIZONTAL);
        coverCard.setBackground(Theme.round(Theme.surface(), 20, host));
        coverCard.setPadding(Theme.dp(host, 14), Theme.dp(host, 14),
                Theme.dp(host, 14), Theme.dp(host, 14));
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        coverLp.topMargin = Theme.dp(host, 16);
        col.addView(coverCard, coverLp);

        ImageView cover = new ImageView(host);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(Theme.surfaceAlt());
        cover.setClipToOutline(true);
        cover.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Theme.dp(host, 12));
            }
        });
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                Theme.dp(host, 96), Theme.dp(host, 132));
        coverCard.addView(cover, imgLp);
        String coverUrl = b.coverSmall != null && !b.coverSmall.isEmpty() ? b.coverSmall : b.cover;
        Media.get(host).loadImage(cover, Repo.site(coverUrl), Theme.surfaceAlt());

        LinearLayout info = new LinearLayout(host);
        info.setOrientation(VERTICAL);
        info.setPadding(Theme.dp(host, 14), Theme.dp(host, 2), 0, 0);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        coverCard.addView(info);

        info.addView(Theme.text(host, b.title, 18, Theme.text(), true));
        TextView zhTitle = Theme.text(host, b.titleZh + " · " + b.authorZh, 12, Theme.textDim(), false);
        zhTitle.setPadding(0, Theme.dp(host, 5), 0, 0);
        info.addView(zhTitle);

        LinearLayout meta = new LinearLayout(host);
        meta.setOrientation(HORIZONTAL);
        meta.setPadding(0, Theme.dp(host, 10), 0, 0);
        info.addView(meta);
        meta.addView(smallTag(b.level + " 阅读", true));
        meta.addView(smallTag(b.chapterCount + " 章", false));

        TextView first = Theme.text(host, "“" + b.firstLine + "”", 11, Theme.textDim(), false);
        first.setLineSpacing(0, 1.35f);
        first.setMaxLines(3);
        first.setEllipsize(android.text.TextUtils.TruncateAt.END);
        first.setPadding(0, Theme.dp(host, 10), 0, 0);
        info.addView(first);

        // 开始阅读按钮
        TextView start = Theme.text(host, "从第一章开始阅读", 15,
                Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Theme.round(Theme.accent(), 14, host));
        start.setPadding(0, Theme.dp(host, 13), 0, Theme.dp(host, 13));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startLp.topMargin = Theme.dp(host, 16);
        col.addView(start, startLp);
        start.setOnClickListener(v -> openChapter(b, 1));
        Theme.pressable(start);

        // 章节列表
        TextView sectionTitle = Theme.text(host, "目录", 14, Theme.text(), true);
        sectionTitle.setPadding(0, Theme.dp(host, 24), 0, Theme.dp(host, 6));
        col.addView(sectionTitle);

        for (Models.ChapterInfo c : b.chapters) {
            col.addView(chapterRow(b, c));
        }
    }

    /**
     * 创建一个标签。
     *
     * @param text 文本
     * @param accent 是否强调
     * @return 视图
     */
    private TextView smallTag(String text, boolean accent) {
        TextView v = Theme.text(host, text, 11, accent ? Theme.accent() : Theme.textDim(), true);
        v.setBackground(accent ? Theme.round(Theme.accentSoft(), 8, host)
                : Theme.round(Theme.surfaceAlt(), 8, host));
        v.setPadding(Theme.dp(host, 9), Theme.dp(host, 4),
                Theme.dp(host, 9), Theme.dp(host, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(host, 8);
        v.setLayoutParams(lp);
        return v;
    }

    /**
     * 生成一行章节。
     *
     * @param b 书目
     * @param c 章节信息
     * @return 行视图
     */
    private View chapterRow(Models.BookInfo b, Models.ChapterInfo c) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.round(Theme.surface(), 14, host));
        row.setPadding(Theme.dp(host, 14), Theme.dp(host, 12),
                Theme.dp(host, 14), Theme.dp(host, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(host, 8);
        row.setLayoutParams(lp);

        TextView num = Theme.text(host, String.valueOf(c.n), 13, Theme.accent(), true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Theme.round(Theme.accentSoft(), 10, host));
        row.addView(num, new LinearLayout.LayoutParams(Theme.dp(host, 30), Theme.dp(host, 30)));

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(VERTICAL);
        texts.setPadding(Theme.dp(host, 12), 0, 0, 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(texts);
        String label = c.labelZh != null && !c.labelZh.isEmpty() ? c.labelZh + " " : "";
        texts.addView(Theme.text(host, label + c.titleZh, 14, Theme.text(), false));

        String meta = String.format(Locale.CHINA, "%d 词 · %d 分钟", c.words, c.minutes);
        TextView metaView = Theme.text(host, meta, 11, Theme.textDim(), false);
        metaView.setPadding(0, Theme.dp(host, 3), 0, 0);
        texts.addView(metaView);

        if (c.ready) {
            ImageView arrow = new ImageView(host);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            arrow.setColorFilter(Theme.textDim());
            arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(arrow, new LinearLayout.LayoutParams(
                    Theme.dp(host, 18), Theme.dp(host, 18)));
        } else {
            TextView arrow = Theme.text(host, "…", 18, Theme.border(), false);
            row.addView(arrow);
        }

        if (c.ready) {
            row.setOnClickListener(v -> openChapter(b, c.n));
            Theme.pressable(row);
        } else {
            row.setAlpha(0.5f);
        }
        return row;
    }

    /**
     * 打开指定章节。
     *
     * @param b 书目
     * @param n 章节号
     */
    private void openChapter(Models.BookInfo b, int n) {
        Intent intent = new Intent(host, BookReaderActivity.class);
        intent.putExtra("book", b.id);
        intent.putExtra("chapter", n);
        host.startActivity(intent);
    }
}
