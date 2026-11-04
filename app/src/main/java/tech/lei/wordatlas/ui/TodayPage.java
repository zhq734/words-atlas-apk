package tech.lei.wordatlas.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;

/**
 * 今日页：问候、考试倒计时、今日学习计划与最近学过的词。
 *
 * @author zhenghq
 */
public class TodayPage extends MainActivity.Page {

    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private TextView greeting;
    private TextView dateText;
    private TextView newCount;
    private TextView reviewCount;
    private TextView planHint;
    private TextView streakText;
    private TextView learnedText;
    private TextView masteredText;
    private LinearLayout examCard;
    private TextView examDays;
    private TextView examLabel;
    private LinearLayout recentBox;

    /**
     * @param host 主界面
     */
    public TodayPage(MainActivity host) {
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
        col.setOrientation(VERTICAL);
        int pad = Theme.dp(host, 20);
        col.setPadding(pad, Theme.dp(host, 18), pad, Theme.dp(host, 28));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部问候区
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(VERTICAL);
        col.addView(header);

        greeting = Theme.text(host, "早上好", 26, Theme.text(), true);
        header.addView(greeting);

        dateText = Theme.text(host, "", 13, Theme.textDim(), false);
        dateText.setPadding(0, Theme.dp(host, 6), 0, 0);
        header.addView(dateText);

        // 考试倒计时
        examCard = new LinearLayout(host);
        examCard.setOrientation(HORIZONTAL);
        examCard.setGravity(Gravity.CENTER_VERTICAL);
        examCard.setBackground(Theme.roundStroke(Theme.accentSoft(), Theme.withAlpha(Theme.accent(), 60),
                18, 1, host));
        examCard.setPadding(Theme.dp(host, 18), Theme.dp(host, 14),
                Theme.dp(host, 18), Theme.dp(host, 14));
        LinearLayout.LayoutParams examLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        examLp.topMargin = Theme.dp(host, 18);
        col.addView(examCard, examLp);

        LinearLayout examLeft = new LinearLayout(host);
        examLeft.setOrientation(VERTICAL);
        examLeft.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        examCard.addView(examLeft);

        examLabel = Theme.text(host, "距离雅思考试", 12, Theme.textDim(), false);
        examLeft.addView(examLabel);

        examDays = Theme.text(host, "--", 20, Theme.accent(), true);
        examDays.setPadding(0, Theme.dp(host, 3), 0, 0);
        examLeft.addView(examDays);

        TextView examSet = Theme.text(host, "设置 ›", 12, Theme.accent(), false);
        examSet.setOnClickListener(v -> host.select(4));
        examCard.addView(examSet);
        examCard.setOnClickListener(v -> host.select(4));

        // 今日计划卡
        LinearLayout plan = new LinearLayout(host);
        plan.setOrientation(VERTICAL);
        plan.setBackground(Theme.round(Theme.surface(), 20, host));
        plan.setPadding(Theme.dp(host, 20), Theme.dp(host, 18),
                Theme.dp(host, 20), Theme.dp(host, 18));
        LinearLayout.LayoutParams planLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        planLp.topMargin = Theme.dp(host, 16);
        col.addView(plan, planLp);

        TextView planTitle = Theme.text(host, "今日学习", 15, Theme.text(), true);
        plan.addView(planTitle);

        LinearLayout nums = new LinearLayout(host);
        nums.setOrientation(HORIZONTAL);
        nums.setPadding(0, Theme.dp(host, 14), 0, 0);
        plan.addView(nums);

        newCount = numberBlock(nums, "新词");
        reviewCount = numberBlock(nums, "待复习");

        planHint = Theme.text(host, "", 12, Theme.textDim(), false);
        planHint.setPadding(0, Theme.dp(host, 12), 0, 0);
        plan.addView(planHint);

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Theme.dp(host, 16);
        plan.addView(actions, actionsLp);

        TextView start = Theme.text(host, "开始学习", 15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Theme.round(Theme.accent(), 14, host));
        start.setPadding(0, Theme.dp(host, 13), 0, Theme.dp(host, 13));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        startLp.rightMargin = Theme.dp(host, 10);
        actions.addView(start, startLp);
        start.setOnClickListener(v -> host.openLearn("new"));
        Theme.pressable(start);

        TextView review = Theme.text(host, "复习", 15, Theme.text(), true);
        review.setGravity(Gravity.CENTER);
        review.setBackground(Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 14, 1, host));
        review.setPadding(0, Theme.dp(host, 13), 0, Theme.dp(host, 13));
        actions.addView(review, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        review.setOnClickListener(v -> host.openLearn("review"));
        Theme.pressable(review);

        // 打卡与统计
        LinearLayout stats = new LinearLayout(host);
        stats.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = Theme.dp(host, 16);
        col.addView(stats, statsLp);

        LinearLayout streakCard = smallCard(stats, "连续打卡", "天");
        streakText = (TextView) streakCard.getChildAt(0);
        LinearLayout learnedCard = smallCard(stats, "已学单词", "");
        learnedText = (TextView) learnedCard.getChildAt(0);
        LinearLayout masteredCard = smallCard(stats, "较熟悉", "");
        masteredText = (TextView) masteredCard.getChildAt(0);

        // 最近学习
        TextView recentTitle = Theme.text(host, "最近学习", 15, Theme.text(), true);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rtLp.topMargin = Theme.dp(host, 22);
        col.addView(recentTitle, rtLp);

        recentBox = new LinearLayout(host);
        recentBox.setOrientation(VERTICAL);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = Theme.dp(host, 10);
        col.addView(recentBox, rbLp);
    }

    /**
     * 生成一个数字区块。
     *
     * @param parent 父容器
     * @param label 标签
     * @return 数字视图
     */
    private TextView numberBlock(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(box);

        TextView num = Theme.text(host, "0", 30, Theme.text(), true);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(num);

        TextView tag = Theme.text(host, label, 12, Theme.textDim(), false);
        tag.setPadding(0, Theme.dp(host, 4), 0, 0);
        box.addView(tag);
        return num;
    }

    /**
     * 生成统计小卡片。
     *
     * @param parent 父容器
     * @param label 说明文字
     * @param unit 单位后缀
     * @return 卡片容器
     */
    private LinearLayout smallCard(LinearLayout parent, String label, String unit) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Theme.round(Theme.surface(), 16, host));
        card.setPadding(Theme.dp(host, 6), Theme.dp(host, 14),
                Theme.dp(host, 6), Theme.dp(host, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Theme.dp(host, 10);
        parent.addView(card, lp);

        TextView num = Theme.text(host, "0", 22, Theme.accent(), true);
        card.addView(num);
        card.addView(Theme.text(host, label, 11, Theme.textDim(), false));
        return card;
    }

    @Override
    public void onShown() {
        refresh();
    }

    /**
     * 重新读取学习数据并刷新界面。
     */
    private void refresh() {
        Store store = Store.get(host);
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String hello = hour < 6 ? "夜深了" : hour < 12 ? "早上好" : hour < 18 ? "下午好" : "晚上好";
        greeting.setText(hello);

        String date = String.format(Locale.CHINA, "%d 月 %d 日 · %s", now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), WEEK[now.get(Calendar.DAY_OF_WEEK) - 1]);
        dateText.setText(date);

        int days = store.daysToExam();
        if (days < 0) {
            examCard.setVisibility(View.GONE);
        } else {
            examCard.setVisibility(View.VISIBLE);
            if (days == 0) {
                examDays.setText("就是今天，加油");
            } else {
                examDays.setText(days + " 天");
            }
            examLabel.setText(days >= 0 && days <= 14 ? "考期临近，保持节奏" : "距离雅思考试");
        }

        int daily = store.daily();
        int done = store.todayLearned();
        int fresh = Math.max(0, daily - done);
        int due = countDue(store);
        newCount.setText(String.valueOf(fresh));
        reviewCount.setText(String.valueOf(due));
        streakText.setText(String.valueOf(store.streak()));
        learnedText.setText(String.valueOf(store.learnedCount()));
        masteredText.setText(String.valueOf(store.masteredCount()));

        if (done >= daily) {
            planHint.setText("今日目标已完成，可以再复习巩固一下。");
        } else if (due > 0) {
            planHint.setText(String.format(Locale.CHINA, "今天已学 %d 个新词，还有 %d 个词到期待复习。", done, due));
        } else {
            planHint.setText(String.format(Locale.CHINA, "今天已学 %d 个新词，继续保持。", done));
        }

        refreshRecent(store);
    }

    /**
     * 统计到期待复习的词数。
     *
     * @param store 存储
     * @return 数量
     */
    private int countDue(Store store) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Store.Progress p : store.allProgress().values()) {
            if (p.due <= now) {
                n++;
            }
        }
        return n;
    }

    /**
     * 刷新最近学习列表。
     *
     * @param store 存储
     */
    private void refreshRecent(Store store) {
        recentBox.removeAllViews();
        Repo.AtlasIndex idx = Repo.get(host).index();
        if (idx == null) {
            Repo.get(host).loadIndex(loaded -> refresh());
            recentBox.addView(Theme.text(host, "正在载入词库…", 13, Theme.textDim(), false));
            return;
        }

        List<Models.WordIndex> recent = new ArrayList<>();
        for (Map.Entry<String, Store.Progress> e : store.allProgress().entrySet()) {
            Models.WordIndex w = idx.find(e.getKey());
            if (w != null) {
                recent.add(w);
            }
        }
        if (recent.isEmpty()) {
            recentBox.addView(Theme.text(host, "还没有学过的词，点上面的「开始学习」试试。", 13, Theme.textDim(), false));
            return;
        }
        Collections.sort(recent, new Comparator<Models.WordIndex>() {
            @Override
            public int compare(Models.WordIndex a, Models.WordIndex b) {
                long ta = store.progress(a.id) == null ? 0 : store.progress(a.id).last;
                long tb = store.progress(b.id) == null ? 0 : store.progress(b.id).last;
                return Long.compare(tb, ta);
            }
        });
        int limit = Math.min(6, recent.size());
        for (int i = 0; i < limit; i++) {
            Models.WordIndex w = recent.get(i);
            recentBox.addView(recentRow(w));
        }
    }

    /**
     * 生成最近学习的一行。
     *
     * @param w 词索引
     * @return 行视图
     */
    private View recentRow(Models.WordIndex w) {
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

        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(texts);
        texts.addView(Theme.word(host, w.word, 16, Theme.text()));
        TextView zh = Theme.text(host, w.zh, 12, Theme.textDim(), false);
        zh.setPadding(0, Theme.dp(host, 3), 0, 0);
        texts.addView(zh);

        TextView cefr = Theme.text(host, cefrName(w.cefr), 11, Theme.accent(), true);
        cefr.setBackground(Theme.round(Theme.accentSoft(), 8, host));
        cefr.setPadding(Theme.dp(host, 8), Theme.dp(host, 4), Theme.dp(host, 8), Theme.dp(host, 4));
        row.addView(cefr);

        row.setOnClickListener(v -> host.openWord(w.id));
        Theme.pressable(row);
        return row;
    }

    /**
     * 难度下标转名称。
     *
     * @param cefr 难度下标
     * @return 名称
     */
    private static String cefrName(int cefr) {
        String[] names = {"A1", "A2", "B1", "B2", "C1", "C2"};
        return cefr >= 0 && cefr < names.length ? names[cefr] : "A1";
    }
}
