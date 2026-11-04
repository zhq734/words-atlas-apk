package tech.lei.wordatlas.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Repo;

/**
 * 练习页：易混词辨析、同义替换与阅读判断三个训练入口。
 *
 * @author zhenghq
 */
public class PracticePage extends MainActivity.Page {

    private TextView confusableCount;
    private TextView paraphraseCount;
    private TextView readingCount;

    /**
     * @param host 主界面
     */
    public PracticePage(MainActivity host) {
        super(host);
    }

    @Override
    protected void build() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(host);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(host, 18), Theme.dp(host, 16),
                Theme.dp(host, 18), Theme.dp(host, 28));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(Theme.text(host, "练习", 24, Theme.text(), true));
        TextView sub = Theme.text(host, "把词表变成阅读与写作的能力", 12, Theme.textDim(), false);
        sub.setPadding(0, Theme.dp(host, 6), 0, 0);
        col.addView(sub);

        LinearLayout confusable = entry(col, "易", "易混词辨析",
                "形近、义近的词成组对比，配小测", 0xFF6C8EF5);
        confusableCount = (TextView) confusable.getTag();
        confusable.setOnClickListener(v -> start(ConfusableActivity.class));

        LinearLayout paraphrase = entry(col, "同", "同义替换",
                "雅思阅读的核心考点，选择正确的改写", 0xFFE3B96B);
        paraphraseCount = (TextView) paraphrase.getTag();
        paraphrase.setOnClickListener(v -> start(ParaphraseActivity.class));

        LinearLayout reading = entry(col, "读", "阅读判断",
                "分级短文配 T / F / NG 判断题", 0xFF7BC49A);
        readingCount = (TextView) reading.getTag();
        reading.setOnClickListener(v -> start(ReadingActivity.class));

        TextView tip = Theme.text(host, "提示：练习记录会计入今日学习统计，做错的词也会进入复习队列。",
                11, Theme.textDim(), false);
        tip.setLineSpacing(0, 1.4f);
        tip.setPadding(0, Theme.dp(host, 18), 0, 0);
        col.addView(tip);

        loadCounts();
    }

    /**
     * 生成一个练习入口卡片。
     *
     * @param parent 父容器
     * @param icon 图标文字
     * @param title 标题
     * @param desc 描述
     * @param color 图标底色
     * @return 卡片，tag 上挂着数量文本视图
     */
    private LinearLayout entry(LinearLayout parent, String icon, String title, String desc, int color) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.round(Theme.surface(), 18, host));
        card.setPadding(Theme.dp(host, 16), Theme.dp(host, 18),
                Theme.dp(host, 16), Theme.dp(host, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(host, 14);
        parent.addView(card, lp);

        TextView badge = Theme.text(host, icon, 18, 0xFFFFFFFF, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.round(color, 14, host));
        card.addView(badge, new LinearLayout.LayoutParams(Theme.dp(host, 46), Theme.dp(host, 46)));

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Theme.dp(host, 14), 0, 0, 0);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(texts);
        texts.addView(Theme.text(host, title, 16, Theme.text(), true));

        TextView d = Theme.text(host, desc, 11, Theme.textDim(), false);
        d.setPadding(0, Theme.dp(host, 4), 0, 0);
        texts.addView(d);

        LinearLayout right = new LinearLayout(host);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        card.addView(right);

        TextView count = Theme.text(host, "…", 12, Theme.accent(), true);
        count.setGravity(Gravity.END);
        right.addView(count);
        ImageView arrow = new ImageView(host);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(Theme.textDim());
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                Theme.dp(host, 20), Theme.dp(host, 20));
        arrowLp.topMargin = Theme.dp(host, 2);
        right.addView(arrow, arrowLp);

        card.setTag(count);
        Theme.pressable(card);
        return card;
    }

    /**
     * 打开练习页面。
     *
     * @param target 目标 Activity
     */
    private void start(Class<?> target) {
        host.startActivity(new Intent(host, target));
    }

    /** 后台统计各模块的条目数量。 */
    private void loadCounts() {
        new Thread(() -> {
            Repo repo = Repo.get(host);
            final int confusable = repo.confusables().size();
            final int paraphrase = repo.paraphrases().size();
            final int reading = repo.readings().size();
            host.runOnUiThread(() -> {
                confusableCount.setText(String.format(Locale.CHINA, "%d 组", confusable));
                paraphraseCount.setText(String.format(Locale.CHINA, "%d 题", paraphrase));
                readingCount.setText(String.format(Locale.CHINA, "%d 篇", reading));
            });
        }).start();
    }
}
