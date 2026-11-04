package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;

/**
 * 单词列表页：从「我的」页的统计格子进入，展示已学、较熟悉或收藏的单词。
 *
 * <p>点击任意一行进入词条详情。</p>
 *
 * @author zhenghq
 */
public class WordListActivity extends Activity {

    /** 列表内容类型。 */
    public static final String EXTRA_MODE = "mode";
    /** 已学。 */
    public static final String MODE_LEARNED = "learned";
    /** 较熟悉。 */
    public static final String MODE_MASTERED = "mastered";
    /** 收藏。 */
    public static final String MODE_FAV = "fav";

    private static final String[] CEFR_NAMES = {"A1", "A2", "B1", "B2", "C1", "C2"};

    private Store store;
    private String mode;

    private TextView subtitleView;
    private RecyclerView list;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyHint;

    private final List<Models.WordIndex> items = new ArrayList<>();
    private Adapter adapter;

    /** 首次 onResume 不刷新，避免与 onCreate 的加载重复。 */
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        store = Store.get(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_LEARNED;
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

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Theme.dp(this, 12), 0, 0, 0);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(titles);
        titles.addView(Theme.text(this, titleOf(mode), 17, Theme.text(), true));
        subtitleView = Theme.text(this, "正在载入…", 11, Theme.textDim(), false);
        titles.addView(subtitleView);

        FrameLayout content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setPadding(Theme.dp(this, 16), Theme.dp(this, 4),
                Theme.dp(this, 16), Theme.dp(this, 16));
        list.setClipToPadding(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = buildEmpty();
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        applyInsets(root, page);
        return root;
    }

    /**
     * 构建空态视图。
     *
     * @return 视图
     */
    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        emptyTitle = Theme.text(this, "", 16, Theme.text(), true);
        emptyTitle.setGravity(Gravity.CENTER);
        box.addView(emptyTitle);

        emptyHint = Theme.text(this, "", 12, Theme.textDim(), false);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setLineSpacing(0, 1.4f);
        emptyHint.setPadding(Theme.dp(this, 32), Theme.dp(this, 8),
                Theme.dp(this, 32), 0);
        box.addView(emptyHint);
        return box;
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

    /**
     * 根据当前模式收集并展示单词。
     *
     * <p>已学与较熟悉按最近学习时间倒序；收藏按收藏先后倒序，最近收藏排在最前。</p>
     */
    private void load() {
        Repo.get(this).loadIndex(index -> {
            if (index == null) {
                subtitleView.setText("词库载入失败");
                showEmpty();
                return;
            }
            List<Models.WordIndex> result = collect(index);
            items.clear();
            items.addAll(result);
            adapter.notifyDataSetChanged();
            subtitleView.setText(String.format(Locale.CHINA, "共 %d 个", items.size()));
            list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            if (items.isEmpty()) {
                emptyTitle.setText(emptyTitleOf(mode));
                emptyHint.setText(emptyHintOf(mode));
            }
        });
    }

    /**
     * 从全量词库中筛出当前模式需要的单词并排序。
     *
     * @param index 词库索引
     * @return 单词列表
     */
    private List<Models.WordIndex> collect(Repo.AtlasIndex index) {
        Map<String, Store.Progress> progress = store.allProgress();
        Set<String> favorites = store.favorites();

        // 收藏顺序表：末尾是最近收藏，展示时倒序
        Map<String, Integer> favOrder = new HashMap<>();
        int seq = 0;
        for (String id : favorites) {
            favOrder.put(id, seq++);
        }

        List<Models.WordIndex> result = new ArrayList<>();
        for (Models.WordIndex w : index.words) {
            if (MODE_LEARNED.equals(mode)) {
                if (progress.containsKey(w.id)) {
                    result.add(w);
                }
            } else if (MODE_MASTERED.equals(mode)) {
                Store.Progress p = progress.get(w.id);
                if (p != null && p.level >= 3) {
                    result.add(w);
                }
            } else if (favOrder.containsKey(w.id)) {
                result.add(w);
            }
        }

        if (MODE_FAV.equals(mode)) {
            Collections.sort(result, Comparator.comparingInt(
                    w -> -favOrder.getOrDefault(w.id, 0)));
        } else {
            Collections.sort(result, Comparator.comparingLong(
                    (Models.WordIndex w) -> {
                        Store.Progress p = progress.get(w.id);
                        return p == null ? 0L : p.last;
                    }).reversed());
        }
        return result;
    }

    /** 显示空态提示。 */
    private void showEmpty() {
        list.setVisibility(View.GONE);
        emptyTitle.setText(emptyTitleOf(mode));
        emptyHint.setText(emptyHintOf(mode));
        emptyView.setVisibility(View.VISIBLE);
    }

    /**
     * 页面标题。
     *
     * @param mode 模式
     * @return 标题
     */
    private static String titleOf(String mode) {
        if (MODE_MASTERED.equals(mode)) {
            return "较熟悉单词";
        }
        if (MODE_FAV.equals(mode)) {
            return "我的收藏";
        }
        return "已学单词";
    }

    /**
     * 空态标题。
     *
     * @param mode 模式
     * @return 文案
     */
    private static String emptyTitleOf(String mode) {
        if (MODE_MASTERED.equals(mode)) {
            return "还没有较熟悉的单词";
        }
        if (MODE_FAV.equals(mode)) {
            return "还没有收藏的单词";
        }
        return "还没有学过的单词";
    }

    /**
     * 空态说明。
     *
     * @param mode 模式
     * @return 文案
     */
    private static String emptyHintOf(String mode) {
        if (MODE_MASTERED.equals(mode)) {
            return "同一个词连续答对三次后会算作较熟悉";
        }
        if (MODE_FAV.equals(mode)) {
            return "在词条详情页点右上角爱心即可收藏";
        }
        return "在今日页开始学习，学过的词都会出现在这里";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        // 从详情页返回时可能取消了收藏，重新筛一遍
        load();
    }

    /** 单词列表适配器。 */
    private final class Adapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(rowView());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Models.WordIndex w = items.get(position);
            LinearLayout row = (LinearLayout) holder.itemView;
            LinearLayout texts = (LinearLayout) row.getChildAt(0);
            TextView word = (TextView) texts.getChildAt(0);
            TextView meta = (TextView) texts.getChildAt(1);
            TextView badge = (TextView) row.getChildAt(1);

            word.setText(w.word);
            StringBuilder sb = new StringBuilder();
            if (w.ipa != null && !w.ipa.isEmpty()) {
                sb.append(w.ipa).append("  ");
            }
            if (w.zh != null && !w.zh.isEmpty()) {
                sb.append(w.zh);
            }
            meta.setText(sb);
            badge.setText(w.cefr >= 0 && w.cefr < CEFR_NAMES.length ? CEFR_NAMES[w.cefr] : "");
            row.setOnClickListener(v -> {
                Intent intent = new Intent(WordListActivity.this, WordDetailActivity.class);
                intent.putExtra("id", w.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /**
     * 生成列表行视图。
     *
     * @return 行
     */
    private View rowView() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.round(Theme.surface(), 14, this));
        row.setPadding(Theme.dp(this, 16), Theme.dp(this, 13),
                Theme.dp(this, 14), Theme.dp(this, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, 8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        texts.addView(Theme.word(this, "", 17, Theme.text()));
        TextView meta = Theme.text(this, "", 12, Theme.textDim(), false);
        meta.setPadding(0, Theme.dp(this, 4), 0, 0);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(meta);

        TextView badge = Theme.text(this, "", 10, Theme.accent(), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Theme.round(Theme.accentSoft(), 8, this));
        badge.setPadding(Theme.dp(this, 7), Theme.dp(this, 3),
                Theme.dp(this, 7), Theme.dp(this, 3));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Theme.dp(this, 8);
        row.addView(badge, blp);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(Theme.textDim());
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                Theme.dp(this, 17), Theme.dp(this, 17));
        alp.leftMargin = Theme.dp(this, 6);
        row.addView(arrow, alp);
        return row;
    }

    /** 列表项持有者。 */
    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
