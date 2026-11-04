package tech.lei.wordatlas.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Store;

/**
 * 我的页面：学习统计、偏好设置、数据备份与重置。
 *
 * @author zhenghq
 */
public class MePage extends MainActivity.Page {

    private static final String[] CEFR_NAMES = {"A1", "A2", "B1", "B2", "C1", "C2"};

    private Store store;
    private LinearLayout col;
    private TextView statLearned;
    private TextView statMastered;
    private TextView statStreak;
    private TextView statFav;
    private TextView themeValue;
    private TextView accentValue;
    private TextView dailyValue;
    private TextView cefrValue;
    private TextView examValue;
    private TextView illusValue;

    /**
     * @param host 主界面
     */
    public MePage(MainActivity host) {
        super(host);
    }

    @Override
    protected void build() {
        store = Store.get(host);
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col = new LinearLayout(host);
        col.setOrientation(VERTICAL);
        col.setPadding(Theme.dp(host, 18), Theme.dp(host, 16),
                Theme.dp(host, 18), Theme.dp(host, 30));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(Theme.text(host, "我的", 24, Theme.text(), true));

        buildStats();

        sectionTitle("学习设置");
        LinearLayout group = card();
        themeValue = settingRow(group, "外观", "", () -> pickTheme());
        accentValue = settingRow(group, "发音", "", () -> pickAccent());
        dailyValue = settingRow(group, "每日新词", "", () -> pickDaily());
        cefrValue = settingRow(group, "学习起点", "", () -> pickStart());
        examValue = settingRow(group, "考试日期", "", () -> pickExam());
        illusValue = settingRow(group, "插画风格", "", () -> pickIllustration());

        sectionTitle("数据");
        LinearLayout dataGroup = card();
        settingRow(dataGroup, "导出学习数据", "复制到剪贴板", this::exportData);
        settingRow(dataGroup, "导入学习数据", "从剪贴板恢复", this::importData);
        settingRow(dataGroup, "清空学习记录", "保留设置", this::confirmReset);

        sectionTitle("关于");
        LinearLayout about = card();
        infoRow(about, "词库来源", "0xlei.tech/words");
        infoRow(about, "词库许可", "CC BY 4.0");
        infoRow(about, "版本", "2.0.7");

        TextView tip = Theme.text(host, "词库随安装包内置，联网时自动检查更新；插画与发音按需从官网加载并缓存。",
                11, Theme.textDim(), false);
        tip.setLineSpacing(0, 1.4f);
        tip.setPadding(0, Theme.dp(host, 16), 0, 0);
        col.addView(tip);

        refreshValues();
    }

    /** 构建统计卡片。 */
    private void buildStats() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(VERTICAL);
        card.setBackground(Theme.round(Theme.surface(), 18, host));
        card.setPadding(Theme.dp(host, 16), Theme.dp(host, 16),
                Theme.dp(host, 16), Theme.dp(host, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(host, 16);
        col.addView(card, lp);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        card.addView(row);
        statLearned = statCell(row, "已学", () -> openList(WordListActivity.MODE_LEARNED));
        statMastered = statCell(row, "较熟悉", () -> openList(WordListActivity.MODE_MASTERED));
        statStreak = statCell(row, "连续打卡", this::showCheckinCalendar);
        statFav = statCell(row, "收藏", () -> openList(WordListActivity.MODE_FAV));
    }

    /**
     * 打开单词列表页。
     *
     * @param mode 列表类型，见 {@link WordListActivity}
     */
    private void openList(String mode) {
        Intent intent = new Intent(host, WordListActivity.class);
        intent.putExtra(WordListActivity.EXTRA_MODE, mode);
        host.startActivity(intent);
    }

    /**
     * 生成统计格子。
     *
     * @param parent 父容器
     * @param label 标签
     * @param action 点击回调
     * @return 数值视图
     */
    private TextView statCell(LinearLayout parent, String label, Runnable action) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.setPadding(0, Theme.dp(host, 4), 0, Theme.dp(host, 4));

        TextView value = Theme.text(host, "0", 20, Theme.accent(), true);
        value.setGravity(Gravity.CENTER);
        box.addView(value);
        TextView tag = Theme.text(host, label, 10, Theme.textDim(), false);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, Theme.dp(host, 4), 0, 0);
        box.addView(tag);
        parent.addView(box);
        box.setOnClickListener(v -> action.run());
        Theme.pressable(box);
        return value;
    }

    /**
     * 添加分区标题。
     *
     * @param title 标题
     */
    private void sectionTitle(String title) {
        TextView v = Theme.text(host, title, 12, Theme.textDim(), true);
        v.setPadding(0, Theme.dp(host, 22), 0, Theme.dp(host, 6));
        col.addView(v);
    }

    /**
     * 生成一组设置卡片的容器。
     *
     * @return 容器
     */
    private LinearLayout card() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(VERTICAL);
        box.setBackground(Theme.round(Theme.surface(), 16, host));
        box.setPadding(Theme.dp(host, 16), Theme.dp(host, 2),
                Theme.dp(host, 16), Theme.dp(host, 2));
        col.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /**
     * 生成一行可点击设置项。
     *
     * @param parent 父容器
     * @param label 左侧文字
     * @param hint 右侧提示
     * @param action 点击回调
     * @return 右侧取值文本
     */
    private TextView settingRow(LinearLayout parent, String label, String hint, Runnable action) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(host, 14), 0, Theme.dp(host, 14));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = Theme.text(host, label, 14, Theme.text(), false);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = Theme.text(host, hint, 12, Theme.textDim(), false);
        row.addView(value);

        ImageView arrow = new ImageView(host);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(Theme.textDim());
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                Theme.dp(host, 17), Theme.dp(host, 17));
        arrowLp.leftMargin = Theme.dp(host, 6);
        row.addView(arrow, arrowLp);

        row.setOnClickListener(v -> action.run());
        return value;
    }

    /**
     * 生成一行只读信息。
     *
     * @param parent 父容器
     * @param label 标签
     * @param value 值
     */
    private void infoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(host, 14), 0, Theme.dp(host, 14));
        parent.addView(row);
        row.addView(Theme.text(host, label, 14, Theme.text(), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Theme.text(host, value, 12, Theme.textDim(), false));
    }

    /** 刷新右侧取值。 */
    private void refreshValues() {
        themeValue.setText("light".equals(store.theme()) ? "浅色" : "深色");
        accentValue.setText("us".equals(store.accent()) ? "美音" : "英音");
        dailyValue.setText(store.daily() + " 个");
        int start = store.startCefr();
        cefrValue.setText(start >= 0 && start < CEFR_NAMES.length ? CEFR_NAMES[start] : "A1");
        long exam = store.examDate();
        examValue.setText(exam > 0
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(exam)) : "未设置");
        illusValue.setText("classic".equals(store.illustration()) ? "经典线稿" : "完整插画");

        statLearned.setText(String.valueOf(store.learnedCount()));
        statMastered.setText(String.valueOf(store.masteredCount()));
        statStreak.setText(store.streak() + " 天");
        statFav.setText(String.valueOf(store.favorites().size()));
    }

    /** 选择主题。 */
    private void pickTheme() {
        String[] items = {"深色", "浅色"};
        boolean light = "light".equals(store.theme());
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("外观")
                .setSingleChoiceItems(items, light ? 1 : 0, (d, which) -> {
                    store.setTheme(which == 1 ? "light" : "dark");
                    d.dismiss();
                    host.onSettingsChanged();
                })
                .show();
    }

    /** 选择发音口音。 */
    private void pickAccent() {
        String[] items = {"英音", "美音"};
        boolean us = "us".equals(store.accent());
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("发音")
                .setSingleChoiceItems(items, us ? 1 : 0, (d, which) -> {
                    store.setAccent(which == 1 ? "us" : "uk");
                    d.dismiss();
                    refreshValues();
                })
                .show();
    }

    /** 选择每日新词数量。 */
    private void pickDaily() {
        String[] items = {"5 个", "10 个", "15 个", "20 个", "30 个"};
        int[] values = {5, 10, 15, 20, 30};
        int current = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == store.daily()) {
                current = i;
            }
        }
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("每日新词")
                .setSingleChoiceItems(items, current, (d, which) -> {
                    store.setDaily(values[which]);
                    d.dismiss();
                    refreshValues();
                })
                .show();
    }

    /** 选择学习起点难度。 */
    private void pickStart() {
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("学习起点")
                .setSingleChoiceItems(CEFR_NAMES, Math.max(0, Math.min(5, store.startCefr())),
                        (d, which) -> {
                            store.setStartCefr(which);
                            d.dismiss();
                            refreshValues();
                        })
                .show();
    }

    /** 选择考试日期。 */
    private void pickExam() {
        Calendar calendar = Calendar.getInstance();
        if (store.examDate() > 0) {
            calendar.setTimeInMillis(store.examDate());
        }
        DatePickerDialog dialog = new DatePickerDialog(host, Theme.dialogTheme(), (view, year, month, day) -> {
            calendar.set(year, month, day, 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            store.setExamDate(calendar.getTimeInMillis());
            refreshValues();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle("考试日期");
        dialog.show();
    }

    /** 选择插画风格。 */
    private void pickIllustration() {
        String[] items = {"完整插画", "经典线稿"};
        boolean classic = "classic".equals(store.illustration());
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("插画风格")
                .setSingleChoiceItems(items, classic ? 1 : 0, (d, which) -> {
                    store.setIllustration(which == 1 ? "classic" : "full");
                    d.dismiss();
                    refreshValues();
                })
                .show();
    }

    /** 导出学习数据到剪贴板。 */
    private void exportData() {
        String json = store.exportJson();
        ClipboardManager clipboard = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("word-atlas-backup", json));
            Toast.makeText(host, "学习数据已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    /** 从剪贴板导入学习数据。 */
    private void importData() {
        ClipboardManager clipboard = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(host, "剪贴板里没有备份数据", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(host, "剪贴板里没有备份数据", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(host);
        boolean ok = store.importJson(String.valueOf(text));
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("导入学习数据")
                .setMessage(ok ? "导入成功，页面将刷新。" : "导入失败，请确认剪贴板内容是有效的备份。")
                .setPositiveButton("知道了", (d, w) -> {
                    d.dismiss();
                    if (ok) {
                        host.rebuildUi();
                    }
                })
                .show();
    }

    /** 清空学习记录前的确认。 */
    private void confirmReset() {
        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("清空学习记录")
                .setMessage("将删除全部复习进度与收藏，设置会保留。此操作无法撤销。")
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .setPositiveButton("清空", (d, w) -> {
                    store.resetProgress();
                    d.dismiss();
                    refreshValues();
                    Toast.makeText(host, "学习记录已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * 打开打卡日历：有学习记录的日期画红圈。
     */
    private void showCheckinCalendar() {
        final Set<String> days = store.checkins();
        final Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Theme.dp(host, 14), Theme.dp(host, 6),
                Theme.dp(host, 14), Theme.dp(host, 2));

        // 月份切换条
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView prev = monthArrow("‹", "上一月");
        head.addView(prev);
        TextView monthText = Theme.text(host, "", 15, Theme.text(), true);
        monthText.setGravity(Gravity.CENTER);
        head.addView(monthText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView next = monthArrow("›", "下一月");
        head.addView(next);

        // 星期表头，周一为每周第一天
        LinearLayout weekHead = weekRow();
        box.addView(weekHead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (String w : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView t = Theme.text(host, w, 11, Theme.textDim(), false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Theme.dp(host, 4), 0, Theme.dp(host, 4));
            weekHead.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        // 日期网格
        LinearLayout grid = new LinearLayout(host);
        grid.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = Theme.text(host, "", 12, Theme.textDim(), false);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, Theme.dp(host, 10), 0, Theme.dp(host, 6));
        box.addView(summary);

        final Runnable render = () -> renderCalendar(grid, month, days, monthText, summary, next);
        prev.setOnClickListener(v -> {
            month.add(Calendar.MONTH, -1);
            render.run();
        });
        next.setOnClickListener(v -> {
            if (!next.isEnabled()) {
                return;
            }
            month.add(Calendar.MONTH, 1);
            render.run();
        });
        render.run();

        new AlertDialog.Builder(host, Theme.dialogTheme())
                .setTitle("打卡日历")
                .setView(box)
                .setNegativeButton("关闭", (d, w) -> d.dismiss())
                .show();
    }

    /**
     * 生成月份切换箭头。
     *
     * @param symbol 箭头字符
     * @param desc 无障碍描述
     * @return 视图
     */
    private TextView monthArrow(String symbol, String desc) {
        TextView v = Theme.text(host, symbol, 20, Theme.text(), false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.round(Theme.surfaceAlt(), 16, host));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                Theme.dp(host, 32), Theme.dp(host, 32)));
        v.setContentDescription(desc);
        return v;
    }

    /**
     * 渲染指定月份的日历网格。
     *
     * @param grid 网格容器
     * @param month 当前月份（月初）
     * @param days 打卡日期集合
     * @param monthText 月份文本
     * @param summary 底部统计文本
     * @param next 下一月按钮
     */
    private void renderCalendar(LinearLayout grid, Calendar month, Set<String> days,
                                TextView monthText, TextView summary, View next) {
        monthText.setText(String.format(Locale.CHINA, "%d 年 %d 月",
                month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));
        grid.removeAllViews();

        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        // Calendar 里周日是 1，换算成「周一为 0」的列下标
        int leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int max = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());

        LinearLayout row = weekRow();
        grid.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < leading; i++) {
            row.addView(blankCell());
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        for (int d = 1; d <= max; d++) {
            if (row.getChildCount() == 7) {
                row = weekRow();
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Calendar c = (Calendar) first.clone();
            c.set(Calendar.DAY_OF_MONTH, d);
            String key = fmt.format(c.getTime());
            row.addView(calendarDayCell(d, days.contains(key), key.equals(today)));
        }
        while (row.getChildCount() < 7) {
            row.addView(blankCell());
        }

        // 本月打卡天数
        String prefix = String.format(Locale.CHINA, "%04d-%02d-",
                month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1);
        int count = 0;
        for (String s : days) {
            if (s.startsWith(prefix)) {
                count++;
            }
        }
        summary.setText(String.format(Locale.CHINA, "连续打卡 %d 天 · 本月 %d 天",
                store.streak(), count));

        // 不允许翻到未来月份
        Calendar now = Calendar.getInstance();
        boolean atCurrent = month.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && month.get(Calendar.MONTH) == now.get(Calendar.MONTH);
        next.setEnabled(!atCurrent);
        next.setAlpha(atCurrent ? 0.35f : 1f);
    }

    /**
     * 生成一周的横向容器。
     *
     * @return 行容器
     */
    private LinearLayout weekRow() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /**
     * 生成空白占位格。
     *
     * @return 占位视图
     */
    private View blankCell() {
        View v = new View(host);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, Theme.dp(host, 42), 1f));
        return v;
    }

    /**
     * 生成一个日期格子，已打卡的日期画红圈。
     *
     * @param day 日期数字
     * @param checked 当天是否打卡
     * @param today 是否今天
     * @return 格子视图
     */
    private View calendarDayCell(int day, boolean checked, boolean today) {
        LinearLayout wrap = new LinearLayout(host);
        wrap.setGravity(Gravity.CENTER);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, Theme.dp(host, 42), 1f));

        TextView v = new TextView(host);
        v.setText(String.valueOf(day));
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setIncludeFontPadding(false);
        wrap.addView(v, new LinearLayout.LayoutParams(
                Theme.dp(host, 34), Theme.dp(host, 34)));

        if (checked) {
            // 打卡日期：红色圆环
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.TRANSPARENT);
            circle.setStroke(Theme.dp(host, 2), Theme.danger());
            v.setBackground(circle);
            v.setTextColor(Theme.danger());
            v.setTypeface(Typeface.DEFAULT_BOLD);
        } else if (today) {
            v.setBackground(Theme.round(Theme.accentSoft(), 17, host));
            v.setTextColor(Theme.accent());
            v.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            v.setTextColor(Theme.textDim());
        }
        return wrap;
    }

    @Override
    public void onShown() {
        if (store != null) {
            refreshValues();
        }
    }
}
