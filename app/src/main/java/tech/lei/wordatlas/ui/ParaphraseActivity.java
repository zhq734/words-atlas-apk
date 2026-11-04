package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 同义替换训练：给出原句与改写句，选出改写句中替换关键词的表达。
 *
 * <p>每轮随机抽取 10 题，作答后立即给出解析与得分。</p>
 *
 * @author zhenghq
 */
public class ParaphraseActivity extends Activity {

    private static final int ROUND_SIZE = 10;

    private List<Models.Paraphrase> all = new ArrayList<>();
    private final List<Models.Paraphrase> round = new ArrayList<>();

    private LinearLayout container;
    private TextView progressText;
    private int index;
    private int correct;
    private int cefrFilter = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        setContentView(buildShell());
        load();
    }

    /**
     * 构建页面骨架。
     *
     * @return 根视图
     */
    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                Theme.dp(this, 16), Theme.dp(this, 8));
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

        TextView title = Theme.text(this, "同义替换", 17, Theme.text(), true);
        title.setPadding(Theme.dp(this, 12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressText = Theme.text(this, "", 12, Theme.textDim(), false);
        bar.addView(progressText);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        page.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        applyInsets(root, page);
        return root;
    }

    /**
     * 让内容避开系统栏。
     *
     * @param root 根视图
     * @param page 内容页
     */
    private void applyInsets(View root, View page) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
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

    /** 后台加载全部题目。 */
    private void load() {
        new Thread(() -> {
            final List<Models.Paraphrase> list = Repo.get(this).paraphrases();
            runOnUiThread(() -> {
                all = list;
                startRound();
            });
        }).start();
    }

    /** 开始一轮练习。 */
    private void startRound() {
        round.clear();
        index = 0;
        correct = 0;

        List<Models.Paraphrase> pool = new ArrayList<>();
        for (Models.Paraphrase p : all) {
            if (cefrFilter < 0 || p.cefr == cefrFilter) {
                pool.add(p);
            }
        }
        if (pool.isEmpty()) {
            showEmpty();
            return;
        }
        Collections.shuffle(pool);
        for (int i = 0; i < pool.size() && i < ROUND_SIZE; i++) {
            round.add(pool.get(i));
        }
        renderQuestion();
    }

    /** 显示无可练习内容的提示。 */
    private void showEmpty() {
        progressText.setText("");
        container.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Theme.dp(this, 24), Theme.dp(this, 60),
                Theme.dp(this, 24), Theme.dp(this, 24));
        container.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(Theme.text(this, "没有该难度的题目", 16, Theme.text(), true));
        TextView again = Theme.text(this, "看全部难度", 13, Theme.accent(), false);
        again.setPadding(0, Theme.dp(this, 14), 0, 0);
        box.addView(again);
        again.setOnClickListener(v -> {
            cefrFilter = -1;
            startRound();
        });
    }

    /** 渲染当前题目。 */
    private void renderQuestion() {
        if (index >= round.size()) {
            showResult();
            return;
        }
        progressText.setText(String.format(Locale.CHINA, "%d / %d", index + 1, round.size()));
        container.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(this, 20), Theme.dp(this, 10),
                Theme.dp(this, 20), Theme.dp(this, 24));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Models.Paraphrase p = round.get(index);

        TextView label1 = Theme.text(this, "原句", 11, Theme.textDim(), true);
        col.addView(label1);

        // 原句与改写句都支持点击整段朗读，与书房段落一致
        col.addView(speakable(highlight(p.src, p.key), p.src, 16, Theme.dp(this, 16)));

        TextView label2 = Theme.text(this, "改写句", 11, Theme.textDim(), true);
        col.addView(label2);

        col.addView(speakable(p.para, p.para, 15, Theme.dp(this, 18)));

        TextView question = Theme.text(this, String.format(Locale.CHINA,
                "改写句中，替换了「%s」的是：", p.key), 13, Theme.accent(), true);
        col.addView(question);

        // 选项：正确答案 + 三个干扰项后打乱
        List<String> options = new ArrayList<>(p.opts);
        options.add(p.ans);
        Collections.shuffle(options);
        final List<TextView> optionViews = new ArrayList<>();
        for (String option : options) {
            TextView v = Theme.text(this, option, 15, Theme.text(), false);
            v.setBackground(Theme.roundStroke(Theme.surface(), Theme.border(), 13, 1, this));
            v.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                    Theme.dp(this, 16), Theme.dp(this, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Theme.dp(this, 9);
            col.addView(v, lp);
            optionViews.add(v);
            v.setOnClickListener(x -> answer(p, option, optionViews, col));
        }
    }

    /**
     * 生成一段可点击朗读的文本：正文加右侧喇叭图标，点文字或图标都朗读整段。
     *
     * @param display 显示的文本，可以带关键词高亮
     * @param plain 用于朗读的纯文本
     * @param size 字号
     * @param bottom 段落下方间距（像素）
     * @return 段落视图
     */
    private View speakable(CharSequence display, String plain, int size, int bottom) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(this, 6), 0, bottom);

        TextView body = Theme.text(this, display, size, Theme.text(), false);
        body.setLineSpacing(Theme.dp(this, 3), 1.5f);
        row.addView(body, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView speak = new ImageView(this);
        speak.setImageResource(R.drawable.ic_volume_up);
        speak.setColorFilter(Theme.accent());
        speak.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        speak.setBackground(Theme.round(Theme.accentSoft(), 17, this));
        int p = Theme.dp(this, 7);
        speak.setPadding(p, p, p, p);
        speak.setContentDescription("朗读本段");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                Theme.dp(this, 34), Theme.dp(this, 34));
        slp.leftMargin = Theme.dp(this, 10);
        row.addView(speak, slp);

        View.OnClickListener play = v -> Voice.get(this).sentence(plain, Store.get(this).accent());
        body.setOnClickListener(play);
        speak.setOnClickListener(play);
        Theme.pressable(speak);
        return row;
    }

    /**
     * 处理作答。
     *
     * @param p 题目
     * @param chosen 选中项
     * @param views 全部选项视图
     * @param col 内容容器
     */
    private void answer(Models.Paraphrase p, String chosen, List<TextView> views, LinearLayout col) {
        boolean right = chosen.equalsIgnoreCase(p.ans);
        if (right) {
            correct++;
        }
        for (TextView v : views) {
            String text = String.valueOf(v.getText());
            boolean isAnswer = text.equalsIgnoreCase(p.ans);
            boolean isChosen = text.equalsIgnoreCase(chosen);
            if (isAnswer) {
                v.setBackground(Theme.round(Theme.withAlpha(Theme.success(), 55), 13, this));
                v.setTextColor(Theme.success());
            } else if (isChosen) {
                v.setBackground(Theme.round(Theme.withAlpha(Theme.danger(), 55), 13, this));
                v.setTextColor(Theme.danger());
            }
            v.setClickable(false);
        }

        LinearLayout feedback = new LinearLayout(this);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setBackground(Theme.round(Theme.surfaceAlt(), 13, this));
        feedback.setPadding(Theme.dp(this, 16), Theme.dp(this, 13),
                Theme.dp(this, 16), Theme.dp(this, 13));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Theme.dp(this, 14);
        col.addView(feedback, flp);

        feedback.addView(Theme.text(this, right ? "回答正确" : "答案是 " + p.ans, 13,
                right ? Theme.success() : Theme.danger(), true));
        if (p.note != null && !p.note.isEmpty()) {
            TextView note = Theme.text(this, p.note, 13, Theme.text(), false);
            note.setLineSpacing(0, 1.4f);
            note.setPadding(0, Theme.dp(this, 6), 0, 0);
            feedback.addView(note);
        }

        TextView next = Theme.text(this, index + 1 >= round.size() ? "查看结果" : "下一题",
                15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        next.setGravity(Gravity.CENTER);
        next.setBackground(Theme.round(Theme.accent(), 14, this));
        next.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Theme.dp(this, 18);
        col.addView(next, nlp);
        next.setOnClickListener(v -> {
            index++;
            renderQuestion();
        });
    }

    /** 显示本轮结果。 */
    private void showResult() {        progressText.setText(String.format(Locale.CHINA, "%d / %d", round.size(), round.size()));
        container.removeAllViews();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Theme.dp(this, 24), Theme.dp(this, 56),
                Theme.dp(this, 24), Theme.dp(this, 24));
        container.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView score = Theme.text(this, correct + " / " + round.size(), 42, Theme.accent(), true);
        score.setGravity(Gravity.CENTER);
        box.addView(score);

        String comment = correct >= round.size() * 0.8 ? "同义替换掌握得不错。"
                : correct >= round.size() * 0.5 ? "继续保持，重点关注错题解析。" : "建议先到图鉴里过一遍相关词汇。";
        TextView c = Theme.text(this, comment, 13, Theme.textDim(), false);
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, Theme.dp(this, 8), 0, 0);
        box.addView(c);

        TextView again = Theme.text(this, "再来一轮", 15,
                Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        again.setGravity(Gravity.CENTER);
        again.setBackground(Theme.round(Theme.accent(), 14, this));
        again.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Theme.dp(this, 220), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 26);
        box.addView(again, lp);
        again.setOnClickListener(v -> startRound());

        TextView close = Theme.text(this, "返回", 13, Theme.textDim(), false);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, Theme.dp(this, 16), 0, 0);
        box.addView(close);
        close.setOnClickListener(v -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面时停止朗读
        Voice.get(this).stop();
    }

    /**
     * 高亮原句中的关键词。
     *
     * @param text 原句
     * @param key 关键词
     * @return 带样式的文本
     */
    private CharSequence highlight(String text, String key) {
        SpannableString span = new SpannableString(text);
        if (key == null || key.isEmpty()) {
            return span;
        }
        int start = -1;
        int end = -1;
        try {
            Matcher m = Pattern.compile("(?i)\\b" + Pattern.quote(key) + "\\b").matcher(text);
            if (m.find()) {
                start = m.start();
                end = m.end();
            }
        } catch (Exception ignored) {
        }
        if (start < 0) {
            int i = text.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT));
            if (i >= 0) {
                start = i;
                end = i + key.length();
            }
        }
        if (start >= 0) {
            span.setSpan(new BackgroundColorSpan(Theme.accentSoft()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(Theme.accent()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }
}
