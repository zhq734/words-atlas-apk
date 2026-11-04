package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 易混词辨析页：按组展示形近义近词，组内提供要点、例句与小测。
 *
 * @author zhenghq
 */
public class ConfusableActivity extends Activity {

    private FrameLayout container;
    private TextView titleView;
    private TextView subtitleView;
    private View listView;
    private ScrollView detailView;

    private final List<Models.ConfusableGroup> groups = new ArrayList<>();
    private int cefrFilter = -1;
    private final List<Models.ConfusableGroup> shown = new ArrayList<>();
    private Adapter adapter;

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
        back.setOnClickListener(v -> onBackPressed());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Theme.dp(this, 12), 0, 0, 0);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(titles);
        titleView = Theme.text(this, "易混词辨析", 17, Theme.text(), true);
        titles.addView(titleView);
        subtitleView = Theme.text(this, "正在载入…", 11, Theme.textDim(), false);
        titles.addView(subtitleView);

        container = new FrameLayout(this);
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

    /** 后台加载易混词组。 */
    private void load() {
        new Thread(() -> {
            final List<Models.ConfusableGroup> list = Repo.get(this).confusables();
            runOnUiThread(() -> {
                groups.clear();
                groups.addAll(list);
                subtitleView.setText(String.format(Locale.CHINA, "共 %d 组", groups.size()));
                showList();
            });
        }).start();
    }

    /** 显示列表视图。 */
    private void showList() {
        titleView.setText("易混词辨析");
        detailView = null;
        shown.clear();
        shown.addAll(groups);
        adapter = new Adapter();
        adapter.notifyDataSetChanged();

        if (listView == null) {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(Theme.dp(this, 16), Theme.dp(this, 4),
                    Theme.dp(this, 16), Theme.dp(this, 6));
            wrap.addView(chips);
            String[] names = {"全部", "A1", "A2", "B1", "B2", "C1", "C2"};
            for (int i = 0; i < names.length; i++) {
                final int value = i - 1;
                TextView chip = Theme.text(this, names[i], 11,
                        cefrFilter == value ? (Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF) : Theme.textDim(),
                        cefrFilter == value);
                GradientDrawable bg = cefrFilter == value
                        ? Theme.round(Theme.accent(), 13, this)
                        : Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 13, 1, this);
                chip.setBackground(bg);
                chip.setPadding(Theme.dp(this, 11), Theme.dp(this, 6),
                        Theme.dp(this, 11), Theme.dp(this, 6));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Theme.dp(this, 7);
                chips.addView(chip, lp);
                chip.setOnClickListener(v -> {
                    cefrFilter = value;
                    listView = null;
                    container.removeAllViews();
                    showList();
                });
            }

            RecyclerView list = new RecyclerView(this);
            list.setLayoutManager(new LinearLayoutManager(this));
            list.setPadding(Theme.dp(this, 16), Theme.dp(this, 4),
                    Theme.dp(this, 16), Theme.dp(this, 16));
            list.setClipToPadding(false);
            list.setAdapter(adapter);
            wrap.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            listView = wrap;
        }
        applyFilter();
        container.removeAllViews();
        container.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 按难度过滤。 */
    private void applyFilter() {
        shown.clear();
        for (Models.ConfusableGroup g : groups) {
            if (cefrFilter < 0 || g.cefr == cefrFilter) {
                shown.add(g);
            }
        }
        adapter.notifyDataSetChanged();
        subtitleView.setText(String.format(Locale.CHINA, "%d / %d 组", shown.size(), groups.size()));
    }

    /**
     * 显示某个组的详情。
     *
     * @param g 组
     */
    private void showDetail(Models.ConfusableGroup g) {
        titleView.setText(titleOf(g));
        subtitleView.setText(g.core);
        container.removeAllViews();

        if (detailView == null) {
            detailView = new ScrollView(this);
            detailView.setFillViewport(true);
        }
        detailView.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(this, 20), Theme.dp(this, 8),
                Theme.dp(this, 20), Theme.dp(this, 28));
        detailView.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 组内单词
        for (Models.ConfusableWord w : g.words) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Theme.round(Theme.surface(), 14, this));
            row.setPadding(Theme.dp(this, 14), Theme.dp(this, 12),
                    Theme.dp(this, 14), Theme.dp(this, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Theme.dp(this, 8);
            col.addView(row, lp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(texts);
            texts.addView(Theme.word(this, w.w, 18, Theme.text()));
            TextView zh = Theme.text(this, (w.pos.isEmpty() ? "" : w.pos + " ") + w.zh, 12,
                    Theme.textDim(), false);
            zh.setPadding(0, Theme.dp(this, 4), 0, 0);
            texts.addView(zh);

            TextView open = Theme.text(this, "词条 ›", 12, Theme.accent(), false);
            row.addView(open);
            row.setOnClickListener(v -> {
                if (w.id != null && !w.id.isEmpty()) {
                    Intent intent = new Intent(this, WordDetailActivity.class);
                    intent.putExtra("id", w.id);
                    startActivity(intent);
                }
            });
        }

        // 辨析要点
        if (!g.points.isEmpty()) {
            section(col, "辨析要点");
            for (String p : g.points) {
                TextView t = Theme.text(this, "· " + p, 14, Theme.text(), false);
                t.setLineSpacing(0, 1.45f);
                t.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
                col.addView(t);
            }
        }

        // 例句
        if (!g.examples.isEmpty()) {
            section(col, "例句对照");
            for (Models.Example ex : g.examples) {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
                col.addView(box);

                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                box.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView en = Theme.text(this, ex.en, 14, Theme.text(), false);
                en.setLineSpacing(0, 1.4f);
                // 例句里的每个单词都可点查释义
                Lookup.attach(this, en, ex.en);
                texts.addView(en);
                if (!ex.zh.isEmpty()) {
                    TextView zh = Theme.text(this, ex.zh, 12, Theme.textDim(), false);
                    zh.setLineSpacing(0, 1.35f);
                    zh.setPadding(0, Theme.dp(this, 5), 0, 0);
                    texts.addView(zh);
                }

                ImageView speak = new ImageView(this);
                speak.setImageResource(R.drawable.ic_volume_up);
                speak.setColorFilter(Theme.accent());
                speak.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                speak.setBackground(Theme.round(Theme.accentSoft(), 16, this));
                int sp = Theme.dp(this, 7);
                speak.setPadding(sp, sp, sp, sp);
                speak.setContentDescription("朗读例句");
                speak.setOnClickListener(x -> Voice.get(this).sentence(ex.en, Store.get(this).accent()));
                LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(
                        Theme.dp(this, 32), Theme.dp(this, 32));
                speakLp.leftMargin = Theme.dp(this, 10);
                box.addView(speak, speakLp);
            }
        }

        // 小测
        if (!g.quiz.isEmpty()) {
            section(col, "小测");
            for (Models.ConfusableQuiz q : g.quiz) {
                col.addView(quizView(g, q));
            }
        }

        container.addView(detailView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * 用组内单词拼出标题，例如「bored / boring」。
     *
     * @param g 组
     * @return 标题
     */
    private static String titleOf(Models.ConfusableGroup g) {
        StringBuilder sb = new StringBuilder();
        for (Models.ConfusableWord w : g.words) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(w.w);
        }
        return sb.length() == 0 ? g.id : sb.toString();
    }

    /**
     * 添加分区标题。
     *
     * @param col 容器
     * @param title 标题
     */
    private void section(LinearLayout col, String title) {
        TextView t = Theme.text(this, title, 12, Theme.textDim(), true);
        t.setPadding(0, Theme.dp(this, 20), 0, Theme.dp(this, 2));
        col.addView(t);
    }

    /**
     * 生成一道填空题。
     *
     * @param g 组
     * @param q 题目
     * @return 视图
     */
    private View quizView(Models.ConfusableGroup g, Models.ConfusableQuiz q) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.round(Theme.surface(), 14, this));
        box.setPadding(Theme.dp(this, 14), Theme.dp(this, 12),
                Theme.dp(this, 14), Theme.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, 10);
        box.setLayoutParams(lp);

        String sentence = q.en;
        try {
            String replaced = sentence.replaceAll("(?i)\\b" + Pattern.quote(q.ans) + "\\b", "______");
            if (!replaced.equals(sentence)) {
                sentence = replaced;
            }
        } catch (Exception ignored) {
        }
        TextView text = Theme.text(this, sentence, 14, Theme.text(), false);
        text.setLineSpacing(0, 1.45f);
        box.addView(text);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.setPadding(0, Theme.dp(this, 10), 0, 0);
        box.addView(options);

        final TextView[] views = new TextView[g.words.size()];
        for (int i = 0; i < g.words.size(); i++) {
            String w = g.words.get(i).w;
            TextView opt = Theme.text(this, w, 13, Theme.text(), false);
            opt.setGravity(Gravity.CENTER);
            opt.setBackground(Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 10, 1, this));
            opt.setPadding(Theme.dp(this, 10), Theme.dp(this, 7),
                    Theme.dp(this, 10), Theme.dp(this, 7));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            olp.rightMargin = Theme.dp(this, 8);
            options.addView(opt, olp);
            views[i] = opt;
            opt.setOnClickListener(v -> {
                boolean right = w.equalsIgnoreCase(q.ans);
                for (int k = 0; k < views.length; k++) {
                    TextView other = views[k];
                    boolean isAnswer = g.words.get(k).w.equalsIgnoreCase(q.ans);
                    if (isAnswer) {
                        other.setBackground(Theme.round(Theme.withAlpha(Theme.success(), 60),
                                10, this));
                        other.setTextColor(Theme.success());
                    } else if (other == opt && !right) {
                        other.setBackground(Theme.round(Theme.withAlpha(Theme.danger(), 60),
                                10, this));
                        other.setTextColor(Theme.danger());
                    }
                    other.setClickable(false);
                }
                if (!q.zh.isEmpty()) {
                    TextView why = Theme.text(this, q.zh, 12, Theme.textDim(), false);
                    why.setLineSpacing(0, 1.35f);
                    why.setPadding(0, Theme.dp(this, 10), 0, 0);
                    box.addView(why);
                }
            });
        }
        return box;
    }

    @Override
    public void onBackPressed() {
        if (detailView != null) {
            showList();
            return;
        }
        super.onBackPressed();
    }

    /** 组列表适配器。 */
    private final class Adapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(rowView());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Models.ConfusableGroup g = shown.get(position);
            LinearLayout row = (LinearLayout) holder.itemView;
            TextView core = (TextView) row.getChildAt(0);
            TextView hook = (TextView) row.getChildAt(1);
            core.setText(titleOf(g));
            hook.setText(g.core);
            row.setOnClickListener(v -> showDetail(g));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    /**
     * 生成列表行视图。
     *
     * @return 行
     */
    private View rowView() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Theme.round(Theme.surface(), 14, this));
        row.setPadding(Theme.dp(this, 16), Theme.dp(this, 13),
                Theme.dp(this, 16), Theme.dp(this, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, 8);
        row.setLayoutParams(lp);

        row.addView(Theme.word(this, "", 16, Theme.text()));
        TextView hook = Theme.text(this, "", 12, Theme.textDim(), false);
        hook.setPadding(0, Theme.dp(this, 4), 0, 0);
        hook.setMaxLines(2);
        hook.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(hook);
        return row;
    }

    /** 列表项持有者。 */
    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Voice.get(this).stop();
    }
}
