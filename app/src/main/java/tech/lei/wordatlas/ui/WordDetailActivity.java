package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import tech.lei.wordatlas.R;
import tech.lei.wordatlas.data.Media;
import tech.lei.wordatlas.data.Models;
import tech.lei.wordatlas.data.Repo;
import tech.lei.wordatlas.data.Store;
import tech.lei.wordatlas.data.Voice;

/**
 * 词条详情页：大插画、音标与发音、义项、双语例句、搭配、词族与近反义词。
 *
 * @author zhenghq
 */
public class WordDetailActivity extends Activity {

    private Repo repo;
    private Store store;
    private String wordId;
    private Models.WordEntry entry;
    /** 是否由学习队列进入：此时认识 / 不认识要交回学习页推进下一词。 */
    private boolean fromLearn;

    private ScrollView scroll;
    private LinearLayout col;
    private String accent;
    private ImageView favButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.apply(!"light".equals(Store.get(this).theme()));
        Theme.bars(this);
        repo = Repo.get(this);
        store = Store.get(this);
        accent = store.accent();
        wordId = getIntent().getStringExtra("id");
        // 学习页会带上 learn 标记，点完认识 / 不认识后由它推进到下一张卡片
        fromLearn = getIntent().getBooleanExtra("learn", false);
        if (wordId == null || wordId.isEmpty()) {
            finish();
            return;
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

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Theme.dp(this, 32));
        scroll.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部返回与收藏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                Theme.dp(this, 12), Theme.dp(this, 8));
        col.addView(bar);

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

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        favButton = new ImageView(this);
        favButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        favButton.setBackground(Theme.round(Theme.surface(), 20, this));
        favButton.setPadding(bp, bp, bp, bp);
        favButton.setContentDescription("收藏");
        bar.addView(favButton, new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40)));
        favButton.setOnClickListener(v -> toggleFavorite());

        // 大插画
        FrameLayout pic = new FrameLayout(this);
        pic.setBackgroundColor(Theme.surfaceAlt());
        LinearLayout.LayoutParams picLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 230));
        picLp.topMargin = Theme.dp(this, 6);
        col.addView(pic, picLp);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pic.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pic.setTag(image);
        col.setTag(image);

        applyInsets(root);
        return root;
    }

    /**
     * 让内容避开系统栏。
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
            scroll.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    /**
     * 按 id 找到词条在索引中的位置并读取详情。
     */
    private void load() {
        repo.loadIndex(idx -> {
            Models.WordIndex wi = idx == null ? null : idx.find(wordId);
            if (wi == null) {
                Toast.makeText(this, "词条不存在", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            new Thread(() -> {
                Models.WordEntry e = repo.entry(wi.order);
                runOnUiThread(() -> {
                    if (e == null) {
                        Toast.makeText(this, "词条加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    entry = e;
                    render(e, wi);
                });
            }).start();
        });
    }

    /**
     * 渲染全部内容。
     *
     * @param e 词条
     * @param wi 索引项
     */
    private void render(Models.WordEntry e, Models.WordIndex wi) {
        boolean classic = "classic".equals(store.illustration());
        ImageView image = (ImageView) col.getTag();
        Media.get(this).loadImage(image,
                Repo.imageUrl(e.id.isEmpty() ? wi.id : e.id, classic, repo.hasClassic(wi.id)),
                Theme.surfaceAlt());

        updateFav();

        int pad = Theme.dp(this, 20);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(pad, Theme.dp(this, 18), pad, 0);
        col.addView(head);

        TextView word = Theme.word(this, e.w, 34, Theme.text());
        head.addView(word);

        // 标签
        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setPadding(0, Theme.dp(this, 8), 0, 0);
        head.addView(tags);
        if (!e.cefr.isEmpty()) {
            tags.addView(tag(e.cefr, true));
        }
        String topicName = topicName(e.topic);
        if (!topicName.isEmpty()) {
            tags.addView(tag(topicName, false));
        }

        // 音标与发音
        LinearLayout ipaRow = new LinearLayout(this);
        ipaRow.setOrientation(LinearLayout.HORIZONTAL);
        ipaRow.setPadding(0, Theme.dp(this, 14), 0, 0);
        head.addView(ipaRow);
        ipaRow.addView(playButton("英 " + safe(e.uk), "uk"));
        ipaRow.addView(playButton("美 " + safe(e.us), "us"));

        // 义项
        if (!e.senses.isEmpty()) {
            section("释义");
            for (Models.Sense s : e.senses) {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(pad, Theme.dp(this, 8), pad, Theme.dp(this, 8));
                col.addView(box);

                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(line);
                if (!s.pos.isEmpty()) {
                    TextView pos = Theme.text(this, s.pos, 12, Theme.accent(), true);
                    pos.setPadding(0, Theme.dp(this, 2), Theme.dp(this, 6), 0);
                    line.addView(pos);
                }
                TextView zh = Theme.text(this, s.zh, 15, Theme.text(), false);
                zh.setLineSpacing(0, 1.3f);
                line.addView(zh);

                if (!s.en.isEmpty()) {
                    TextView en = Theme.text(this, s.en, 12, Theme.textDim(), false);
                    en.setLineSpacing(0, 1.35f);
                    en.setPadding(0, Theme.dp(this, 5), 0, 0);
                    box.addView(en);
                }
            }
        }

        // 例句
        if (!e.examples.isEmpty()) {
            section("例句");
            for (Models.Example ex : e.examples) {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setPadding(pad, Theme.dp(this, 8), pad, Theme.dp(this, 8));
                col.addView(box);

                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                box.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView en = Theme.text(this, ex.en, 15, Theme.text(), false);
                en.setLineSpacing(0, 1.4f);
                // 例句里的每个单词都可点查释义
                Lookup.attach(this, en, ex.en);
                texts.addView(en);
                TextView zh = Theme.text(this, ex.zh, 13, Theme.textDim(), false);
                zh.setLineSpacing(0, 1.3f);
                zh.setPadding(0, Theme.dp(this, 6), 0, 0);
                texts.addView(zh);

                View speak = speakButton(ex.en);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        Theme.dp(this, 36), Theme.dp(this, 36));
                slp.leftMargin = Theme.dp(this, 12);
                box.addView(speak, slp);
            }
        }

        // 搭配
        if (!e.collocations.isEmpty()) {
            section("常用搭配");
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(pad, Theme.dp(this, 6), pad, 0);
            col.addView(wrap);
            for (String col2 : e.collocations) {
                String[] parts = col2.split("｜");
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 6));
                wrap.addView(row);
                TextView en = Theme.text(this, parts[0], 14, Theme.text(), false);
                en.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(en);
                if (parts.length > 1) {
                    TextView zh = Theme.text(this, parts[1], 13, Theme.textDim(), false);
                    row.addView(zh);
                }
            }
        }

        // 词族
        if (!e.family.isEmpty()) {
            section("词族");
            col.addView(chipList(e.family, false));
        }

        // 近反义
        if (!e.synonyms.isEmpty()) {
            section("近义词");
            col.addView(chipList(e.synonyms, false));
        }
        if (!e.antonyms.isEmpty()) {
            section("反义词");
            col.addView(chipList(e.antonyms, true));
        }

        // 提示
        if (!e.tip.isEmpty()) {
            LinearLayout tip = new LinearLayout(this);
            tip.setOrientation(LinearLayout.HORIZONTAL);
            tip.setBackground(Theme.roundStroke(Theme.accentSoft(), Theme.withAlpha(Theme.accent(), 50),
                    16, 1, this));
            tip.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                    Theme.dp(this, 16), Theme.dp(this, 14));
            LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tipLp.setMargins(pad, Theme.dp(this, 18), pad, 0);
            col.addView(tip, tipLp);
            TextView t = Theme.text(this, e.tip, 13, Theme.text(), false);
            t.setLineSpacing(0, 1.4f);
            tip.addView(t);
        }

        // 底部学习按钮
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.setMargins(pad, Theme.dp(this, 24), pad, 0);
        col.addView(actions, actLp);

        TextView know = Theme.text(this, "认识", 15, Theme.dark ? 0xFF1A1F2E : 0xFFFFFFFF, true);
        know.setGravity(Gravity.CENTER);
        know.setBackground(Theme.round(Theme.success(), 14, this));
        know.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        LinearLayout.LayoutParams knowLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        knowLp.rightMargin = Theme.dp(this, 10);
        actions.addView(know, knowLp);
        know.setOnClickListener(v -> mark(true));

        TextView unknown = Theme.text(this, "不认识", 15, Theme.text(), true);
        unknown.setGravity(Gravity.CENTER);
        unknown.setBackground(Theme.roundStroke(Theme.surfaceAlt(), Theme.border(), 14, 1, this));
        unknown.setPadding(0, Theme.dp(this, 13), 0, Theme.dp(this, 13));
        actions.addView(unknown, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        unknown.setOnClickListener(v -> mark(false));

        // 进入词条页自动朗读一遍标准发音，与学习卡片保持一致
        Voice.get(this).word(e.id.isEmpty() ? wi.id : e.id, e.w, accent);
    }

    /**
     * 记录一次认识或不认识。
     *
     * @param known 是否认识
     */
    private void mark(boolean known) {
        if (fromLearn) {
            // 从学习队列进来：结果交给学习页统一记录并切到下一张，避免同一次判断记两遍
            setResult(RESULT_OK, new Intent().putExtra("known", known));
            finish();
            return;
        }
        store.record(wordId, known);
        Toast.makeText(this, known ? "已记录：认识" : "已记录：不认识，稍后会再出现", Toast.LENGTH_SHORT).show();
    }

    /**
     * 切换收藏状态。
     */
    private void toggleFavorite() {
        boolean added = store.toggleFavorite(wordId);
        updateFav();
        Toast.makeText(this, added ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    /** 刷新收藏图标。 */
    private void updateFav() {
        if (favButton == null) {
            return;
        }
        boolean fav = store.isFavorite(wordId);
        favButton.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        favButton.setColorFilter(fav ? Theme.accent() : Theme.textDim());
    }

    /**
     * 创建一个发音按钮。
     *
     * @param label 按钮文字
     * @param which uk 或 us
     * @return 按钮视图
     */
    private View playButton(String label, String which) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Theme.round(Theme.surfaceAlt(), 12, this));
        box.setPadding(Theme.dp(this, 10), Theme.dp(this, 7),
                Theme.dp(this, 12), Theme.dp(this, 7));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_volume_up);
        icon.setColorFilter(Theme.accent());
        box.addView(icon, new LinearLayout.LayoutParams(
                Theme.dp(this, 15), Theme.dp(this, 15)));

        TextView text = Theme.text(this, label, 13, Theme.textDim(), false);
        text.setPadding(Theme.dp(this, 6), 0, 0, 0);
        box.addView(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, 10);
        box.setLayoutParams(lp);
        box.setOnClickListener(x -> Voice.get(this).word(wordId,
                entry == null ? "" : entry.w, which));
        Theme.pressable(box);
        return box;
    }

    /**
     * 创建例句的朗读按钮：官网没有例句音频，用 Edge TTS 合成，失败退回系统朗读。
     *
     * @param text 英文例句
     * @return 按钮视图
     */
    private View speakButton(String text) {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.ic_volume_up);
        v.setColorFilter(Theme.accent());
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setBackground(Theme.round(Theme.accentSoft(), 18, this));
        int p = Theme.dp(this, 8);
        v.setPadding(p, p, p, p);
        v.setContentDescription("朗读例句");
        v.setOnClickListener(x -> Voice.get(this).sentence(text, store.accent()));
        Theme.pressable(v);
        return v;
    }

    /**
     * 创建一个标签视图。
     *
     * @param text 文本
     * @param accent 是否使用强调色
     * @return 视图
     */
    private TextView tag(String text, boolean accent) {
        TextView v = Theme.text(this, text, 11, accent ? Theme.accent() : Theme.textDim(), true);
        v.setBackground(accent ? Theme.round(Theme.accentSoft(), 8, this)
                : Theme.round(Theme.surfaceAlt(), 8, this));
        v.setPadding(Theme.dp(this, 9), Theme.dp(this, 4),
                Theme.dp(this, 9), Theme.dp(this, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Theme.dp(this, 8);
        v.setLayoutParams(lp);
        return v;
    }

    /**
     * 添加一个分区标题。
     *
     * @param title 标题文字
     */
    private void section(String title) {
        TextView t = Theme.text(this, title, 13, Theme.textDim(), true);
        t.setPadding(Theme.dp(this, 20), Theme.dp(this, 22),
                Theme.dp(this, 20), Theme.dp(this, 2));
        col.addView(t);
    }

    /**
     * 生成一组词条胶囊。
     *
     * @param items 词列表
     * @param danger 是否使用警示色
     * @return 容器
     */
    private View chipList(List<String> items, boolean danger) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Theme.dp(this, 20), Theme.dp(this, 6), Theme.dp(this, 20), 0);
        for (String s : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Theme.dp(this, 5), 0, Theme.dp(this, 5));
            wrap.addView(row);
            TextView dot = Theme.text(this, "·", 16, danger ? Theme.danger() : Theme.accent(), true);
            dot.setPadding(0, 0, Theme.dp(this, 8), 0);
            row.addView(dot);
            TextView v = Theme.text(this, s, 14, Theme.text(), false);
            row.addView(v);
        }
        return wrap;
    }

    /**
     * 话题 id 转中文名。
     *
     * @param id 话题 id
     * @return 名称
     */
    private String topicName(String id) {
        Repo.AtlasIndex idx = repo.index();
        if (idx == null || id == null || id.isEmpty()) {
            return "";
        }
        for (String[] t : idx.topics) {
            if (t[0].equals(id)) {
                return t.length > 1 ? t[1] : id;
            }
        }
        return id;
    }

    /**
     * 空值兜底。
     *
     * @param s 原文
     * @return 非空文本
     */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Voice.get(this).stop();
    }
}
