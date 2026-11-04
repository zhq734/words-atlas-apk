package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 双语阅读页：逐段展示英文原文与中文译文，点击段落朗读，支持前后章节跳转。
 *
 * @author zhenghq
 */
public class BookReaderActivity extends Activity {

    private Repo repo;
    private String bookId;
    private int chapterNo;
    private Models.BookInfo book;
    private Models.Chapter chapter;
    private boolean showZh = true;

    private LinearLayout content;
    private ScrollView scroll;
    private TextView labelView;
    private TextView titleView;
    private TextView zhToggle;
    private View prevButton;
    private View nextButton;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        repo = Repo.get(this);
        bookId = getIntent().getStringExtra("book");
        chapterNo = getIntent().getIntExtra("chapter", 1);
        if (bookId == null) {
            bookId = "the-little-prince";
        }
        setContentView(buildShell());
        load();
    }

    /**
     * 构建页面骨架。
     *
     * @return 根视图
     */
    private View buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setTag(page);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                Theme.dp(this, 14), Theme.dp(this, 8));
        bar.setBackgroundColor(Theme.bg());
        page.addView(bar);

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(Theme.text());
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setBackground(Theme.round(Theme.surface(), 20, this));
        int bp = Theme.dp(this, 9);
        back.setPadding(bp, bp, bp, bp);
        back.setContentDescription("返回");
        bar.addView(back, new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40)));
        back.setOnClickListener(v -> finish());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Theme.dp(this, 12), 0, 0, 0);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(titles);

        labelView = Theme.text(this, "", 10, Theme.textDim(), false);
        titles.addView(labelView);
        titleView = Theme.text(this, "正在载入…", 15, Theme.text(), true);
        titles.addView(titleView);

        zhToggle = Theme.text(this, "中", 13, Theme.accent(), true);
        zhToggle.setGravity(Gravity.CENTER);
        zhToggle.setBackground(Theme.round(Theme.accentSoft(), 12, this));
        bar.addView(zhToggle, new LinearLayout.LayoutParams(Theme.dp(this, 36), Theme.dp(this, 36)));
        zhToggle.setOnClickListener(v -> {
            showZh = !showZh;
            updateToggle();
            render();
        });

        // 正文
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Theme.dp(this, 20), 0, Theme.dp(this, 20), Theme.dp(this, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 底栏
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(Theme.dp(this, 16), Theme.dp(this, 8),
                Theme.dp(this, 16), Theme.dp(this, 12));
        page.addView(bottom);

        prevButton = navButton("‹ 上一章");
        prevButton.setOnClickListener(v -> go(-1));
        bottom.addView(prevButton, weightLp(true));

        nextButton = navButton("下一章 ›");
        nextButton.setOnClickListener(v -> go(1));
        bottom.addView(nextButton, weightLp(false));

        updateToggle();
        applyInsets(root, page);
        return root;
    }

    /**
     * 让顶栏与底栏避开系统栏。
     *
     * @param view 根视图
     * @param page 内容页
     */
    private void applyInsets(View view, View page) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            page.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    /**
     * 生成底部导航按钮。
     *
     * @param text 文本
     * @return 视图
     */
    private View navButton(String text) {
        TextView v = Theme.text(this, text, 14, Theme.text(), true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.roundStroke(Theme.surface(), Theme.border(), 13, 1, this));
        v.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
        return v;
    }

    /**
     * 生成导航按钮的布局参数。
     *
     * @param left 是否左按钮
     * @return 参数
     */
    private LinearLayout.LayoutParams weightLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (left) {
            lp.rightMargin = Theme.dp(this, 10);
        }
        return lp;
    }

    /** 刷新译文开关外观。 */
    private void updateToggle() {
        zhToggle.setText(showZh ? "中" : "EN");
        zhToggle.setTextColor(showZh ? Theme.accent() : Theme.textDim());
        zhToggle.setBackground(showZh ? Theme.round(Theme.accentSoft(), 12, this)
                : Theme.round(Theme.surfaceAlt(), 12, this));
    }

    /** 后台加载书目与章节正文。 */
    private void load() {
        titleView.setText("正在载入…");
        new Thread(() -> {
            final Models.BookInfo b = repo.book();
            final Models.Chapter c = repo.chapter(bookId, chapterNo);
            runOnUiThread(() -> {
                book = b;
                chapter = c;
                if (c == null) {
                    titleView.setText("章节不存在");
                    content.removeAllViews();
                    return;
                }
                render();
            });
        }).start();
    }

    /** 渲染章节内容。 */
    private void render() {
        if (chapter == null) {
            return;
        }
        Models.ChapterInfo info = null;
        if (book != null) {
            for (Models.ChapterInfo ci : book.chapters) {
                if (ci.n == chapterNo) {
                    info = ci;
                    break;
                }
            }
        }
        labelView.setText(info != null ? info.label + " · " + info.labelZh
                : String.format(Locale.CHINA, "第 %d 章", chapterNo));
        titleView.setText(chapter.titleZh == null || chapter.titleZh.isEmpty()
                ? (info != null ? info.titleZh : "") : chapter.titleZh);

        scroll.scrollTo(0, 0);
        content.removeAllViews();

        // 章节插图
        if (info != null && info.plate != null && !info.plate.isEmpty()) {
            ImageView plate = new ImageView(this);
            plate.setScaleType(ImageView.ScaleType.CENTER_CROP);
            plate.setBackgroundColor(Theme.surfaceAlt());
            plate.setClipToOutline(true);
            plate.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Theme.dp(BookReaderActivity.this, 16));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 170));
            lp.bottomMargin = Theme.dp(this, 20);
            content.addView(plate, lp);
            Media.get(this).loadImage(plate, Repo.site(info.plate), Theme.surfaceAlt());
        }

        for (Models.Paragraph p : chapter.paras) {
            content.addView(paragraphView(p));
        }

        updateNav();
    }

    /**
     * 生成一个段落的视图。
     *
     * @param p 段落
     * @return 视图
     */
    private View paragraphView(Models.Paragraph p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if ("epigraph".equals(p.kind)) {
            box.setGravity(Gravity.CENTER);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, "epigraph".equals(p.kind) ? 14 : 18);
        box.setLayoutParams(lp);

        TextView en = Theme.text(this, p.en, 16, Theme.text(), false);
        en.setLineSpacing(Theme.dp(this, 3), 1.45f);
        if ("epigraph".equals(p.kind)) {
            en.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            en.setGravity(Gravity.CENTER);
        }
        box.addView(en);

        if (showZh && p.zh != null && !p.zh.isEmpty()) {
            TextView zh = Theme.text(this, p.zh, 13, Theme.textDim(), false);
            zh.setLineSpacing(Theme.dp(this, 2), 1.4f);
            zh.setPadding(0, Theme.dp(this, 7), 0, 0);
            if ("epigraph".equals(p.kind)) {
                zh.setGravity(Gravity.CENTER);
            }
            box.addView(zh);
        }

        if (p.audio != null && !p.audio.isEmpty()) {
            box.setOnClickListener(v -> Voice.get(this)
                    .source(Repo.site(p.audio), p.en, Store.get(this).accent()));
        }
        return box;
    }

    /** 更新前后章按钮可用状态。 */
    private void updateNav() {
        int total = book == null ? 27 : book.chapterCount;
        setEnabled(prevButton, chapterNo > 1);
        setEnabled(nextButton, chapterNo < total);
    }

    /**
     * 设置导航按钮状态。
     *
     * @param view 按钮
     * @param enabled 是否可用
     */
    private void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.35f);
    }

    /**
     * 切换章节。
     *
     * @param delta +1 下一章，-1 上一章
     */
    private void go(int delta) {
        int total = book == null ? 27 : book.chapterCount;
        int target = chapterNo + delta;
        if (target < 1 || target > total) {
            return;
        }
        chapterNo = target;
        chapter = null;
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Voice.get(this).stop();
    }
}
