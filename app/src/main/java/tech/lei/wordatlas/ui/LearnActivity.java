package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Media;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 学习页：按队列逐张展示单词卡片，记录认识与不认识并推进复习计划。
 *
 * <p>支持三种队列：新词、到期复习、收藏。学习结果写入 {@link Store}，
 * 由简化的间隔重复算法安排下一次出现时间。</p>
 *
 * @author zhenghq
 */
public class LearnActivity extends Activity {

    /** 打开完整词条页的请求码，用于接收那里的认识 / 不认识判断。 */
    private static final int REQ_DETAIL = 1001;

    private Repo repo;
    private Store store;

    private final List<Models.WordIndex> queue = new ArrayList<>();
    private int cursor;
    private int knownCount;
    private int unknownCount;
    private String mode = "new";

    private TextView counter;
    private ProgressBar progress;
    private LinearLayout cardBox;
    private LinearLayout actionBar;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        repo = Repo.get(this);
        store = Store.get(this);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) {
            mode = "new";
        }
        setContentView(buildShell());
        loadQueue();
    }

    /**
     * 构建页面骨架。
     *
     * @return 根视图
     */
    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.bg());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Theme.dp(this, 14), Theme.dp(this, 10),
                Theme.dp(this, 18), Theme.dp(this, 6));
        root.addView(header);

        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(Theme.text());
        close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        close.setBackground(Theme.round(Theme.surface(), 18, this));
        int cp = Theme.dp(this, 9);
        close.setPadding(cp, cp, cp, cp);
        close.setContentDescription("关闭");
        header.addView(close, new LinearLayout.LayoutParams(Theme.dp(this, 36), Theme.dp(this, 36)));
        close.setOnClickListener(v -> finish());

        TextView title = Theme.text(this, modeTitle(), 16, Theme.text(), true);
        title.setPadding(Theme.dp(this, 14), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        counter = Theme.text(this, "0 / 0", 13, Theme.textDim(), false);
        header.addView(counter);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(Theme.accent()));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.surfaceAlt()));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 4));
        barLp.setMargins(Theme.dp(this, 18), Theme.dp(this, 4), Theme.dp(this, 18), 0);
        root.addView(progress, barLp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setPadding(Theme.dp(this, 16), Theme.dp(this, 10),
                Theme.dp(this, 16), Theme.dp(this, 16));
        scroll.addView(cardBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(Theme.dp(this, 16), Theme.dp(this, 6),
                Theme.dp(this, 16), Theme.dp(this, 18));
        root.addView(actionBar);

        applyInsets(root);
        return root;
    }

    /**
     * 让顶栏与底栏避开系统栏。
     *
     * @param root 根视图
     */
    private void applyInsets(View root) {
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
            root.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    /**
     * 顶部标题。
     *
     * @return 标题文字
     */
    private String modeTitle() {
        switch (mode) {
            case "review":
                return "复习";
            case "fav":
                return "收藏学习";
            default:
                return "学习新词";
        }
    }

    /**
     * 组装本次学习的单词队列。
     */
    private void loadQueue() {
        repo.loadIndex(idx -> {
            queue.clear();
            if (idx == null) {
                finish();
                return;
            }
            if ("review".equals(mode)) {
                buildReviewQueue(idx);
            } else if ("fav".equals(mode)) {
                buildFavQueue(idx);
            } else {
                buildNewQueue(idx);
            }
            if (queue.isEmpty()) {
                showEmpty();
                return;
            }
            progress.setMax(queue.size());
            counter.setText(String.format(Locale.CHINA, "0 / %d", queue.size()));
            nextCard();
        });
    }

    /**
     * 取到期待复习的词。
     *
     * @param idx 索引
     */
    private void buildReviewQueue(Repo.AtlasIndex idx) {
        long now = System.currentTimeMillis();
        List<Models.WordIndex> due = new ArrayList<>();
        for (Models.WordIndex w : idx.words) {
            Store.Progress p = store.progress(w.id);
            if (p != null && p.due <= now) {
                due.add(w);
            }
        }
        Collections.sort(due, new Comparator<Models.WordIndex>() {
            @Override
            public int compare(Models.WordIndex a, Models.WordIndex b) {
                Store.Progress pa = store.progress(a.id);
                Store.Progress pb = store.progress(b.id);
                return Long.compare(pa == null ? 0 : pa.due, pb == null ? 0 : pb.due);
            }
        });
        for (int i = 0; i < due.size() && i < 30; i++) {
            queue.add(due.get(i));
        }
    }

    /**
     * 取收藏的词。
     *
     * @param idx 索引
     */
    private void buildFavQueue(Repo.AtlasIndex idx) {
        Set<String> favs = store.favorites();
        for (Models.WordIndex w : idx.words) {
            if (favs.contains(w.id)) {
                queue.add(w);
            }
        }
        Collections.shuffle(queue);
    }

    /**
     * 按起点难度取未学的新词。
     *
     * @param idx 索引
     */
    private void buildNewQueue(Repo.AtlasIndex idx) {
        int target = Math.max(1, store.daily());
        int startCefr = store.startCefr();
        Set<String> picked = new HashSet<>();
        // 先从起点难度起依次取未学词
        for (int cefr = startCefr; cefr < 6 && queue.size() < target; cefr++) {
            for (Models.WordIndex w : idx.words) {
                if (queue.size() >= target) {
                    break;
                }
                if (w.cefr != cefr || store.progress(w.id) != null) {
                    continue;
                }
                queue.add(w);
                picked.add(w.id);
            }
        }
        // 仍不足时放宽到任意难度
        if (queue.size() < target) {
            for (Models.WordIndex w : idx.words) {
                if (queue.size() >= target) {
                    break;
                }
                if (store.progress(w.id) == null && !picked.contains(w.id)) {
                    queue.add(w);
                }
            }
        }
    }

    /** 显示队列为空时的提示。 */
    private void showEmpty() {
        counter.setText("0 / 0");
        progress.setMax(1);
        progress.setProgress(0);
        cardBox.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Theme.dp(this, 24), Theme.dp(this, 80),
                Theme.dp(this, 24), Theme.dp(this, 24));
        cardBox.addView(box);
        box.addView(Theme.text(this, emptyTitle(), 18, Theme.text(), true));
        TextView hint = Theme.text(this, emptyHint(), 13, Theme.textDim(), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Theme.dp(this, 10), 0, 0);
        box.addView(hint);

        actionBar.removeAllViews();
        TextView done = Theme.text(this, "返回", 15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Theme.round(Theme.accent(), 14, this));
        done.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        actionBar.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        done.setOnClickListener(v -> finish());
    }

    /**
     * 空队列标题。
     *
     * @return 文字
     */
    private String emptyTitle() {
        switch (mode) {
            case "review":
                return "暂时没有待复习的词";
            case "fav":
                return "还没有收藏的单词";
            default:
                return "今天的词已经学完了";
        }
    }

    /**
     * 空队列补充提示。
     *
     * @return 文字
     */
    private String emptyHint() {
        switch (mode) {
            case "review":
                return "学过的词会按照 1、2、4、7、15、30 天的间隔安排复习。";
            case "fav":
                return "在词条详情页点右上角的爱心即可收藏。";
            default:
                return "可以到「复习」里巩固一下，或把每日词数调高。";
        }
    }

    /** 展示下一张卡片。 */
    private void nextCard() {
        if (cursor >= queue.size()) {
            showSummary();
            return;
        }
        Models.WordIndex w = queue.get(cursor);
        counter.setText(String.format(Locale.CHINA, "%d / %d", cursor + 1, queue.size()));
        progress.setProgress(cursor);
        new Thread(() -> {
            final Models.WordEntry e = repo.entry(w.order);
            runOnUiThread(() -> bindCard(w, e));
        }).start();
    }

    /**
     * 渲染一张词卡。
     *
     * @param w 索引项
     * @param e 词条，可为 null
     */
    private void bindCard(Models.WordIndex w, Models.WordEntry e) {
        scroll.scrollTo(0, 0);
        cardBox.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.round(Theme.surface(), 22, this));
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Theme.dp(LearnActivity.this, 22));
            }
        });
        cardBox.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Theme.surfaceAlt());
        card.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 200)));
        boolean classic = "classic".equals(store.illustration());
        Media.get(this).loadImage(image, Repo.imageUrl(w.id, classic, repo.hasClassic(w.id)),
                Theme.surfaceAlt());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Theme.dp(this, 20), Theme.dp(this, 16),
                Theme.dp(this, 20), Theme.dp(this, 20));
        card.addView(body);

        body.addView(Theme.word(this, w.word, 32, Theme.text()));

        LinearLayout ipaRow = new LinearLayout(this);
        ipaRow.setOrientation(LinearLayout.HORIZONTAL);
        ipaRow.setGravity(Gravity.CENTER_VERTICAL);
        ipaRow.setPadding(0, Theme.dp(this, 6), 0, 0);
        body.addView(ipaRow);

        String ipa = e != null && !e.uk.isEmpty() ? e.uk : w.ipa;
        TextView ipaText = Theme.text(this, ipa, 14, Theme.textDim(), false);
        ipaRow.addView(ipaText);

        ImageView sound = new ImageView(this);
        sound.setImageResource(R.drawable.ic_volume_up);
        sound.setColorFilter(Theme.accent());
        sound.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams soundLp = new LinearLayout.LayoutParams(
                Theme.dp(this, 20), Theme.dp(this, 20));
        soundLp.leftMargin = Theme.dp(this, 8);
        ipaRow.addView(sound, soundLp);
        sound.setContentDescription("朗读单词");
        sound.setOnClickListener(v -> Voice.get(this).word(w.id, w.word, store.accent()));
        ipaRow.setOnClickListener(v -> Voice.get(this).word(w.id, w.word, store.accent()));

        if (e != null && !e.senses.isEmpty()) {
            for (int i = 0; i < Math.min(2, e.senses.size()); i++) {
                Models.Sense s = e.senses.get(i);
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, Theme.dp(this, 12), 0, 0);
                body.addView(line);
                if (!s.pos.isEmpty()) {
                    TextView pos = Theme.text(this, s.pos, 12, Theme.accent(), true);
                    pos.setPadding(0, Theme.dp(this, 2), Theme.dp(this, 6), 0);
                    line.addView(pos);
                }
                TextView zh = Theme.text(this, s.zh, 15, Theme.text(), false);
                zh.setLineSpacing(0, 1.3f);
                line.addView(zh);
            }
        } else {
            TextView zh = Theme.text(this, w.zh, 15, Theme.text(), false);
            zh.setPadding(0, Theme.dp(this, 12), 0, 0);
            body.addView(zh);
        }

        if (e != null && !e.examples.isEmpty()) {
            Models.Example ex = e.examples.get(0);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Theme.dp(this, 16), 0, 0);
            body.addView(row);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView en = Theme.text(this, ex.en, 14, Theme.text(), false);
            en.setLineSpacing(0, 1.4f);
            // 例句里的每个单词都可点查释义
            Lookup.attach(this, en, ex.en);
            texts.addView(en);
            TextView zh = Theme.text(this, ex.zh, 12, Theme.textDim(), false);
            zh.setLineSpacing(0, 1.3f);
            zh.setPadding(0, Theme.dp(this, 6), 0, 0);
            texts.addView(zh);

            View speak = speakButton(ex.en);
            LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(
                    Theme.dp(this, 32), Theme.dp(this, 32));
            speakLp.leftMargin = Theme.dp(this, 10);
            row.addView(speak, speakLp);
        }

        if (e != null && !e.tip.isEmpty()) {
            TextView tip = Theme.text(this, e.tip, 12, Theme.textDim(), false);
            tip.setLineSpacing(0, 1.35f);
            tip.setPadding(0, Theme.dp(this, 14), 0, 0);
            body.addView(tip);
        }

        TextView open = Theme.text(this, "查看完整词条 ›", 12, Theme.accent(), false);
        open.setPadding(0, Theme.dp(this, 16), 0, 0);
        body.addView(open);
        open.setOnClickListener(v -> {
            Intent intent = new Intent(this, WordDetailActivity.class);
            intent.putExtra("id", w.id);
            // 让详情页知道这是学习队列里的词：点认识 / 不认识后回来直接切下一张
            intent.putExtra("learn", true);
            startActivityForResult(intent, REQ_DETAIL);
        });

        card.setOnClickListener(v -> Voice.get(this).word(w.id, w.word, store.accent()));

        buildActions(w);

        // 每次展示新词时自动播放一遍标准发音；官网音频不可用时回退合成
        Voice.get(this).word(w.id, w.word, store.accent());
    }

    /**
     * 创建例句朗读按钮：官网没有例句音频，用 Edge TTS 合成，失败退回系统朗读。
     *
     * @param text 英文例句
     * @return 按钮视图
     */
    private View speakButton(String text) {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.ic_volume_up);
        v.setColorFilter(Theme.accent());
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setBackground(Theme.round(Theme.accentSoft(), 16, this));
        int p = Theme.dp(this, 7);
        v.setPadding(p, p, p, p);
        v.setContentDescription("朗读例句");
        v.setOnClickListener(x -> Voice.get(this).sentence(text, store.accent()));
        Theme.pressable(v);
        return v;
    }

    /**
     * 构建底部认识/不认识按钮。
     *
     * @param w 当前词
     */
    private void buildActions(Models.WordIndex w) {
        actionBar.removeAllViews();

        TextView unknown = Theme.text(this, "不认识", 15, Theme.text(), true);
        unknown.setGravity(Gravity.CENTER);
        unknown.setBackground(Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 14, 1, this));
        unknown.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = Theme.dp(this, 10);
        actionBar.addView(unknown, lp1);
        unknown.setOnClickListener(v -> answer(w, false));

        TextView known = Theme.text(this, "认识", 15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        known.setGravity(Gravity.CENTER);
        known.setBackground(Theme.round(Theme.accent(), 14, this));
        known.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        actionBar.addView(known, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        known.setOnClickListener(v -> answer(w, true));
    }

    /**
     * 记录答案并进入下一张。
     *
     * @param w 词
     * @param known 是否认识
     */
    private void answer(Models.WordIndex w, boolean known) {
        boolean first = store.progress(w.id) == null;
        store.record(w.id, known);
        if (first) {
            store.bumpTodayLearned();
        }
        if (known) {
            knownCount++;
        } else {
            unknownCount++;
        }
        cursor++;
        progress.setProgress(cursor);
        nextCard();
    }

    /**
     * 接收完整词条页返回的认识 / 不认识判断，按学习页原有流程记录并推进到下一张。
     *
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 详情页带回的判断结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_DETAIL || resultCode != RESULT_OK || data == null) {
            return;
        }
        if (cursor >= queue.size()) {
            return;
        }
        // 队列在详情页打开期间不会变，cursor 仍指向刚才那个词
        answer(queue.get(cursor), data.getBooleanExtra("known", true));
    }

    /** 显示学习小结。 */
    private void showSummary() {
        cardBox.removeAllViews();
        counter.setText(String.format(Locale.CHINA, "%d / %d", queue.size(), queue.size()));
        progress.setProgress(queue.size());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Theme.dp(this, 24), Theme.dp(this, 70),
                Theme.dp(this, 24), Theme.dp(this, 24));
        cardBox.addView(box);

        box.addView(Theme.text(this, "本轮完成", 22, Theme.text(), true));

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        nums.setPadding(0, Theme.dp(this, 26), 0, 0);
        box.addView(nums);

        nums.addView(summaryBlock("认识", String.valueOf(knownCount), Theme.success()));
        nums.addView(summaryBlock("不认识", String.valueOf(unknownCount), Theme.danger()));
        nums.addView(summaryBlock("连续打卡", store.streak() + " 天", Theme.accent()));

        actionBar.removeAllViews();
        TextView done = Theme.text(this, "完成", 15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Theme.round(Theme.accent(), 14, this));
        done.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        actionBar.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        done.setOnClickListener(v -> finish());
    }

    /**
     * 生成小结中的一个数字块。
     *
     * @param label 标签
     * @param value 数值
     * @param color 颜色
     * @return 视图
     */
    private View summaryBlock(String label, String value, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(lp);

        TextView v = Theme.text(this, value, 22, color, true);
        v.setGravity(Gravity.CENTER);
        box.addView(v);

        TextView l = Theme.text(this, label, 11, Theme.textDim(), false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Theme.dp(this, 4), 0, 0);
        box.addView(l);
        return box;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Voice.get(this).stop();
    }
}
