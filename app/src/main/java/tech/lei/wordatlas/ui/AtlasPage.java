package tech.lei.wordatlas.ui;

import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tech.lei.wordatlas.data.Media;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;

/**
 * 图鉴页：搜索、难度与话题筛选、学习状态筛选，以及双列词卡列表。
 *
 * @author zhenghq
 */
public class AtlasPage extends MainActivity.Page {

    private static final String[] CEFR_NAMES = {"A1", "A2", "B1", "B2", "C1", "C2"};
    private static final String[] STATE_NAMES = {"全部", "未学", "学习中", "较熟悉", "收藏"};

    private Repo.AtlasIndex index;
    private final List<Models.WordIndex> all = new ArrayList<>();
    private final List<Models.WordIndex> shown = new ArrayList<>();

    private RecyclerView list;
    private Adapter adapter;
    private LinearLayout cefrRow;
    private LinearLayout topicRow;
    private LinearLayout stateRow;
    private TextView summary;
    private TextView empty;

    private String query = "";
    private int cefr = -1;
    private String topic = null;
    private int stateFilter = 0;

    /**
     * @param host 主界面
     */
    public AtlasPage(MainActivity host) {
        super(host);
    }

    @Override
    protected void build() {
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(VERTICAL);
        int pad = Theme.dp(host, 18);
        head.setPadding(pad, Theme.dp(host, 16), pad, 0);
        addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(host);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(titleRow);

        titleRow.addView(Theme.text(host, "图鉴", 24, Theme.text(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        summary = Theme.text(host, "", 12, Theme.textDim(), false);
        titleRow.addView(summary);

        // 搜索框
        EditText search = new EditText(host);
        search.setHint("搜索单词或中文释义");
        search.setHintTextColor(Theme.textDim());
        search.setTextColor(Theme.text());
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setSingleLine(true);
        search.setBackground(Theme.round(Theme.surface(), 14, host));
        search.setPadding(Theme.dp(host, 14), Theme.dp(host, 10),
                Theme.dp(host, 14), Theme.dp(host, 10));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = Theme.dp(host, 14);
        head.addView(search, searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        // 筛选区（横向滚动）
        cefrRow = chipRow(head);
        topicRow = chipRow(head);
        stateRow = chipRow(head);

        // 列表
        FrameLayout listWrap = new FrameLayout(host);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        wrapLp.topMargin = Theme.dp(host, 8);
        addView(listWrap, wrapLp);

        list = new RecyclerView(host);
        list.setLayoutManager(new GridLayoutManager(host, 2));
        list.setClipToPadding(false);
        list.setPadding(pad, 0, pad, Theme.dp(host, 16));
        listWrap.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Theme.text(host, "没有找到匹配的单词", 14, Theme.textDim(), false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        listWrap.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adapter = new Adapter();
        list.setAdapter(adapter);

        loadData();
    }

    /**
     * 创建一行可横向滚动的筛选条。
     *
     * @param parent 父容器
     * @return 内部的横向容器
     */
    private LinearLayout chipRow(LinearLayout parent) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(host);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(host, 10);
        parent.addView(scroll, lp);

        LinearLayout row = new LinearLayout(host);
        row.setOrientation(HORIZONTAL);
        scroll.addView(row, new android.widget.HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /**
     * 生成一个筛选胶囊。
     *
     * @param text 文本
     * @param on 是否选中
     * @param action 点击回调
     * @return 视图
     */
    private TextView chip(String text, boolean on, Runnable action) {
        TextView v = Theme.text(host, text, 12, on ? (Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF) : Theme.textDim(), on);
        v.setGravity(Gravity.CENTER);
        GradientDrawable bg = on ? Theme.round(Theme.accent(), 14, host)
                : Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 14, 1, host);
        v.setBackground(bg);
        v.setPadding(Theme.dp(host, 12), Theme.dp(host, 7),
                Theme.dp(host, 12), Theme.dp(host, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(host, 8);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> action.run());
        return v;
    }

    /**
     * 异步加载词库索引。
     */
    private void loadData() {
        Repo.get(host).loadIndex(idx -> {
            index = idx;
            all.clear();
            if (idx != null) {
                all.addAll(idx.words);
            }
            buildFilters();
            applyFilter();
        });
    }

    /** 重建三行筛选胶囊。 */
    private void buildFilters() {
        cefrRow.removeAllViews();
        cefrRow.addView(chip("全部难度", cefr < 0, () -> {
            cefr = -1;
            buildFilters();
            applyFilter();
        }));
        if (index != null) {
            for (int i = 0; i < index.cefr.size(); i++) {
                final int value = i;
                cefrRow.addView(chip(index.cefr.get(i), cefr == i, () -> {
                    cefr = value;
                    buildFilters();
                    applyFilter();
                }));
            }
        }

        topicRow.removeAllViews();
        topicRow.addView(chip("全部话题", topic == null, () -> {
            topic = null;
            buildFilters();
            applyFilter();
        }));
        if (index != null) {
            for (String[] t : index.topics) {
                final String id = t[0];
                final String name = t.length > 1 ? t[1] : t[0];
                topicRow.addView(chip(name, id.equals(topic), () -> {
                    topic = id;
                    buildFilters();
                    applyFilter();
                }));
            }
        }

        stateRow.removeAllViews();
        for (int i = 0; i < STATE_NAMES.length; i++) {
            final int value = i;
            stateRow.addView(chip(STATE_NAMES[i], stateFilter == i, () -> {
                stateFilter = value;
                buildFilters();
                applyFilter();
            }));
        }
    }

    /** 按当前条件过滤词表。 */
    private void applyFilter() {
        if (index == null) {
            return;
        }
        Store store = Store.get(host);
        Set<String> favs = store.favorites();
        shown.clear();
        for (Models.WordIndex w : all) {
            if (cefr >= 0 && w.cefr != cefr) {
                continue;
            }
            if (topic != null) {
                if (w.topic < 0 || w.topic >= index.topics.size()) {
                    continue;
                }
                if (!index.topics.get(w.topic)[0].equals(topic)) {
                    continue;
                }
            }
            int st = store.state(w.id);
            if (stateFilter == 1 && st != 0) {
                continue;
            }
            if (stateFilter == 2 && st != 1) {
                continue;
            }
            if (stateFilter == 3 && st != 2) {
                continue;
            }
            if (stateFilter == 4 && !favs.contains(w.id)) {
                continue;
            }
            if (!query.isEmpty()) {
                String word = w.word.toLowerCase(Locale.ROOT);
                if (!word.contains(query) && !w.zh.toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
            }
            shown.add(w);
        }
        adapter.notifyDataSetChanged();
        summary.setText(String.format(Locale.CHINA, "%d / %d", shown.size(), all.size()));
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onShown() {
        if (index != null) {
            applyFilter();
        }
    }

    @Override
    public boolean onBack() {
        if (stateFilter != 0 || cefr != -1 || topic != null || !query.isEmpty()) {
            stateFilter = 0;
            cefr = -1;
            topic = null;
            query = "";
            buildFilters();
            applyFilter();
            return true;
        }
        return false;
    }

    /** 词卡列表适配器。 */
    private final class Adapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(new CardView());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CardView card = (CardView) holder.itemView;
            card.bind(shown.get(position));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    /** 列表项持有者。 */
    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }

    /** 词卡：缩略图 + 单词 + 释义。 */
    private final class CardView extends LinearLayout {

        private final ImageView cover;
        private final TextView word;
        private final TextView zh;
        private final TextView badge;

        CardView() {
            super(host);
            setOrientation(VERTICAL);
            setBackground(Theme.round(Theme.surface(), 16, host));
            setClipToOutline(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Theme.dp(host, 16));
                }
            });
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int gap = Theme.dp(host, 6);
            lp.setMargins(gap, gap, gap, gap);
            setLayoutParams(lp);

            FrameLayout pic = new FrameLayout(host);
            pic.setBackgroundColor(Theme.surfaceAlt());
            addView(pic, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(host, 108)));

            cover = new ImageView(host);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            badge = Theme.text(host, "", 10, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
            badge.setBackground(Theme.round(Theme.withAlpha(Theme.accent(), 220), 8, host));
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            badgeLp.topMargin = Theme.dp(host, 8);
            badgeLp.rightMargin = Theme.dp(host, 8);
            pic.addView(badge, badgeLp);

            LinearLayout body = new LinearLayout(host);
            body.setOrientation(VERTICAL);
            body.setPadding(Theme.dp(host, 12), Theme.dp(host, 10),
                    Theme.dp(host, 12), Theme.dp(host, 12));
            addView(body);

            word = Theme.word(host, "", 17, Theme.text());
            body.addView(word);

            zh = Theme.text(host, "", 11, Theme.textDim(), false);
            zh.setMaxLines(2);
            zh.setEllipsize(android.text.TextUtils.TruncateAt.END);
            zh.setPadding(0, Theme.dp(host, 4), 0, 0);
            body.addView(zh);
        }

        /**
         * 绑定词条数据。
         *
         * @param w 词索引
         */
        void bind(Models.WordIndex w) {
            word.setText(w.word);
            zh.setText(w.zh);
            badge.setText(w.cefr >= 0 && w.cefr < CEFR_NAMES.length ? CEFR_NAMES[w.cefr] : "");
            Media.get(host).loadImage(cover, Repo.thumbUrl(w.id), Theme.surfaceAlt());
            setOnClickListener(v -> host.openWord(w.id));
        }
    }
}
