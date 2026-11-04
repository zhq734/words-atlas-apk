package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 阅读页：分级短文列表与正文阅读，正文可点击生词跳转词条，并附 T/F/NG 判断题。
 *
 * <p>正文按需从官网下载并缓存到应用私有目录，读过的短文离线也能再次打开。</p>
 *
 * @author zhenghq
 */
public class ReadingActivity extends Activity {

    private static final String[] CEFR_NAMES = {"A1", "A2", "B1", "B2", "C1", "C2"};
    private static final String[] ANSWERS = {"T", "F", "NG"};

    private List<Models.ReadingIndex> all = new ArrayList<>();
    private final List<Models.ReadingIndex> shown = new ArrayList<>();

    private FrameLayout container;
    private TextView titleView;
    private TextView subtitleView;
    private View listView;
    private ScrollView detailView;
    private Adapter adapter;
    private int cefrFilter = -1;
    private boolean showZh = true;

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
        titleView = Theme.text(this, "阅读判断", 17, Theme.text(), true);
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

    /** 后台加载阅读索引。 */
    private void load() {
        new Thread(() -> {
            final List<Models.ReadingIndex> list = Repo.get(this).readings();
            runOnUiThread(() -> {
                all = list;
                showList();
            });
        }).start();
    }

    /** 显示短文列表。 */
    private void showList() {
        titleView.setText("阅读判断");
        detailView = null;
        if (listView == null) {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(Theme.dp(this, 16), Theme.dp(this, 4),
                    Theme.dp(this, 16), Theme.dp(this, 6));
            String[] names = {"全部", "A1", "A2", "B1", "B2", "C1", "C2"};
            for (int i = 0; i < names.length; i++) {
                final int value = i - 1;
                boolean on = cefrFilter == value;
                TextView chip = Theme.text(this, names[i], 11,
                        on ? (Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF) : Theme.textDim(), on);
                GradientDrawable bg = on ? Theme.round(Theme.accent(), 13, this)
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
                    showList();
                });
            }
            wrap.addView(chips);

            RecyclerView list = new RecyclerView(this);
            list.setLayoutManager(new LinearLayoutManager(this));
            list.setPadding(Theme.dp(this, 16), Theme.dp(this, 4),
                    Theme.dp(this, 16), Theme.dp(this, 16));
            list.setClipToPadding(false);
            adapter = new Adapter();
            list.setAdapter(adapter);
            wrap.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            listView = wrap;
        }
        filter();
        container.removeAllViews();
        container.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 按难度过滤列表。 */
    private void filter() {
        shown.clear();
        for (Models.ReadingIndex r : all) {
            if (cefrFilter < 0 || r.cefr == cefrFilter) {
                shown.add(r);
            }
        }
        if (adapter == null) {
            adapter = new Adapter();
        }
        adapter.notifyDataSetChanged();
        subtitleView.setText(String.format(Locale.CHINA, "%d / %d 篇", shown.size(), all.size()));
    }

    /**
     * 打开一篇短文。
     *
     * @param index 索引项
     */
    private void open(Models.ReadingIndex index) {
        titleView.setText(index.title);
        subtitleView.setText(index.titleZh);
        container.removeAllViews();

        detailView = new ScrollView(this);
        detailView.setFillViewport(true);
        detailView.setVerticalScrollBarEnabled(false);
        container.addView(detailView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout loading = new LinearLayout(this);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, Theme.dp(this, 60), 0, 0);
        loading.addView(Theme.text(this, "正在加载短文…", 13, Theme.textDim(), false));
        detailView.addView(loading);

        new Thread(() -> {
            final Models.Reading r = Repo.get(this).reading(index.id);
            runOnUiThread(() -> {
                if (r == null) {
                    detailView.removeAllViews();
                    LinearLayout fail = new LinearLayout(this);
                    fail.setGravity(Gravity.CENTER);
                    fail.setPadding(Theme.dp(this, 24), Theme.dp(this, 60),
                            Theme.dp(this, 24), 0);
                    fail.addView(Theme.text(this, "短文加载失败，请检查网络后重试。", 13, Theme.textDim(), false));
                    detailView.addView(fail);
                    return;
                }
                renderReading(r, index);
            });
        }).start();
    }

    /**
     * 渲染短文正文与判断题。
     *
     * @param r 短文
     * @param index 索引项
     */
    private void renderReading(Models.Reading r, Models.ReadingIndex index) {
        detailView.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(this, 20), Theme.dp(this, 6),
                Theme.dp(this, 20), Theme.dp(this, 30));
        detailView.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Theme.text(this, r.title, 22, Theme.text(), true);
        col.addView(title);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, Theme.dp(this, 8), 0, 0);
        col.addView(meta);
        TextView zhTitle = Theme.text(this, r.titleZh, 12, Theme.textDim(), false);
        meta.addView(zhTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView toggle = Theme.text(this, showZh ? "隐藏译文" : "显示译文", 11, Theme.accent(), false);
        toggle.setBackground(Theme.round(Theme.accentSoft(), 9, this));
        toggle.setPadding(Theme.dp(this, 10), Theme.dp(this, 5),
                Theme.dp(this, 10), Theme.dp(this, 5));
        toggle.setOnClickListener(v -> {
            showZh = !showZh;
            renderReading(r, index);
        });
        meta.addView(toggle);

        for (int i = 0; i < r.paras.size(); i++) {
            col.addView(paragraphBlock(r.paras.get(i)));

            if (showZh && i < r.zh.size()) {
                TextView zh = Theme.text(this, r.zh.get(i), 12, Theme.textDim(), false);
                zh.setLineSpacing(0, 1.4f);
                zh.setPadding(0, Theme.dp(this, 6), 0, 0);
                col.addView(zh);
            }
        }

        if (!r.questions.isEmpty()) {
            TextView sectionTitle = Theme.text(this, "判断题", 14, Theme.text(), true);
            sectionTitle.setPadding(0, Theme.dp(this, 26), 0, Theme.dp(this, 4));
            col.addView(sectionTitle);
            TextView hint = Theme.text(this, "T 正确 · F 错误 · NG 未提及", 11, Theme.textDim(), false);
            col.addView(hint);

            for (Models.ReadingQuestion q : r.questions) {
                col.addView(questionView(q));
            }
        }
    }

    /**
     * 生成一道判断题。
     *
     * @param q 题目
     * @return 视图
     */
    private View questionView(Models.ReadingQuestion q) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.round(Theme.surface(), 14, this));
        box.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                Theme.dp(this, 16), Theme.dp(this, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(this, 10);
        box.setLayoutParams(lp);

        TextView text = Theme.text(this, q.s, 14, Theme.text(), false);
        text.setLineSpacing(0, 1.4f);
        box.addView(text);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.setPadding(0, Theme.dp(this, 12), 0, 0);
        box.addView(options);

        final TextView[] views = new TextView[ANSWERS.length];
        for (int i = 0; i < ANSWERS.length; i++) {
            final String value = ANSWERS[i];
            TextView opt = Theme.text(this, value, 13, Theme.text(), true);
            opt.setGravity(Gravity.CENTER);
            opt.setBackground(Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 10, 1, this));
            opt.setPadding(0, Theme.dp(this, 7), 0, Theme.dp(this, 7));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            olp.rightMargin = Theme.dp(this, 8);
            options.addView(opt, olp);
            views[i] = opt;
            opt.setOnClickListener(v -> {
                boolean right = value.equalsIgnoreCase(q.a);
                for (int k = 0; k < views.length; k++) {
                    TextView other = views[k];
                    boolean isAnswer = ANSWERS[k].equalsIgnoreCase(q.a);
                    if (isAnswer) {
                        other.setBackground(Theme.round(Theme.withAlpha(Theme.success(), 55), 10, this));
                        other.setTextColor(Theme.success());
                    } else if (other == opt && !right) {
                        other.setBackground(Theme.round(Theme.withAlpha(Theme.danger(), 55), 10, this));
                        other.setTextColor(Theme.danger());
                    }
                    other.setClickable(false);
                }
                if (q.why != null && !q.why.isEmpty()) {
                    TextView why = Theme.text(this, q.why, 12, Theme.textDim(), false);
                    why.setLineSpacing(0, 1.35f);
                    why.setPadding(0, Theme.dp(this, 10), 0, 0);
                    box.addView(why);
                }
            });
        }
        return box;
    }

    /**
     * 生成一段可朗读的正文：段落文字加右侧喇叭图标，点文字或图标都朗读整段。
     *
     * <p>段落里的生词链接仍然优先响应跳转词条，点到其余位置才播放整段。</p>
     *
     * @param raw 带 [[id|surface]] 标记的原始段落
     * @return 段落视图
     */
    private View paragraphBlock(String raw) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.CENTER_VERTICAL);
        block.setPadding(0, Theme.dp(this, 16), 0, 0);

        TextView para = Theme.text(this, richText(raw), 16, Theme.text(), false);
        para.setLineSpacing(Theme.dp(this, 3), 1.55f);
        para.setMovementMethod(LinkMovementMethod.getInstance());
        para.setHighlightColor(0x00000000);
        block.addView(para, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String plain = plainText(raw);
        View.OnClickListener play = v -> Voice.get(this).sentence(plain, Store.get(this).accent());
        // 生词链接会先消费点击，其余位置落到文本自身，用于朗读整段
        para.setOnClickListener(play);

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
        block.addView(speak, slp);
        speak.setOnClickListener(play);
        Theme.pressable(speak);
        return block;
    }

    /**
     * 把带 [[id|surface]] 标记的段落还原成纯文本，用于整段朗读。
     *
     * @param raw 原文
     * @return 纯文本
     */
    private static String plainText(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf("[[", i);
            if (open < 0) {
                sb.append(raw.substring(i));
                break;
            }
            sb.append(raw.substring(i, open));
            int close = raw.indexOf("]]", open);
            if (close < 0) {
                sb.append(raw.substring(open));
                break;
            }
            String inner = raw.substring(open + 2, close);
            String[] parts = inner.split("\\|", 2);
            sb.append(parts.length > 1 ? parts[1] : parts[0]);
            i = close + 2;
        }
        return sb.toString();
    }

    /**
     * 把带 [[id|surface]] 标记的段落转为可点击的生词文本。
     *
     * @param raw 原文
     * @return 富文本
     */
    private CharSequence richText(String raw) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf("[[", i);
            if (open < 0) {
                sb.append(raw.substring(i));
                break;
            }
            sb.append(raw.substring(i, open));
            int close = raw.indexOf("]]", open);
            if (close < 0) {
                sb.append(raw.substring(open));
                break;
            }
            String inner = raw.substring(open + 2, close);
            String[] parts = inner.split("\\|", 2);
            final String id = parts[0];
            String surface = parts.length > 1 ? parts[1] : parts[0];
            int start = sb.length();
            sb.append(surface);
            int end = sb.length();
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openWord(id);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(Theme.text());
                    ds.setUnderlineText(false);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(Theme.accent()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = close + 2;
        }
        return sb;
    }

    /**
     * 打开生词详情。
     *
     * @param id 词 id
     */
    private void openWord(String id) {
        Intent intent = new Intent(this, WordDetailActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (detailView != null) {
            detailView = null;
            showList();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面或跳转词条时停止整段朗读
        Voice.get(this).stop();
    }

    /** 短文列表适配器。 */
    private final class Adapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(rowView());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Models.ReadingIndex r = shown.get(position);
            LinearLayout row = (LinearLayout) holder.itemView;
            LinearLayout texts = (LinearLayout) row.getChildAt(0);
            TextView title = (TextView) texts.getChildAt(0);
            TextView sub = (TextView) texts.getChildAt(1);
            TextView badge = (TextView) row.getChildAt(1);
            title.setText(r.title);
            sub.setText(r.titleZh + " · " + topicName(r.topic));
            badge.setText(r.cefr >= 0 && r.cefr < CEFR_NAMES.length ? CEFR_NAMES[r.cefr] : "");
            row.setOnClickListener(v -> open(r));
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.round(Theme.surface(), 14, this));
        row.setPadding(Theme.dp(this, 16), Theme.dp(this, 13),
                Theme.dp(this, 16), Theme.dp(this, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(this, 8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(texts);
        texts.addView(Theme.text(this, "", 15, Theme.text(), true));
        TextView sub = Theme.text(this, "", 11, Theme.textDim(), false);
        sub.setPadding(0, Theme.dp(this, 4), 0, 0);
        texts.addView(sub);

        TextView badge = Theme.text(this, "", 10, Theme.accent(), true);
        badge.setBackground(Theme.round(Theme.accentSoft(), 8, this));
        badge.setPadding(Theme.dp(this, 8), Theme.dp(this, 4),
                Theme.dp(this, 8), Theme.dp(this, 4));
        row.addView(badge);

        return row;
    }

    /**
     * 话题 id 转中文名。
     *
     * @param id 话题 id
     * @return 名称
     */
    private String topicName(String id) {
        Repo.AtlasIndex idx = Repo.get(this).index();
        if (idx == null || id == null) {
            return "";
        }
        for (String[] t : idx.topics) {
            if (t[0].equals(id)) {
                return t.length > 1 ? t[1] : id;
            }
        }
        return id;
    }

    /** 列表项持有者。 */
    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
