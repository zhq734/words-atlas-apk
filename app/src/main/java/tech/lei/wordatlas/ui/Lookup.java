package tech.lei.wordatlas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
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
 * 例句查词：把英文例句里的每个单词变成可点击的链接。
 *
 * <p>点按任意单词会弹出释义卡片，显示音标与中文释义并立即朗读；
 * 收录在词库中的词还可以跳转到完整词条。词库未收录的拼写仍然可以朗读，
 * 只是不显示释义。复数、过去式、进行时等常见变形会自动还原到原形再查。</p>
 *
 * @author zhenghq
 */
public final class Lookup {

    /** 英文单词匹配规则，允许内部出现连字符与撇号。 */
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'’-]*");

    private Lookup() {
    }

    /**
     * 把例句文本渲染成可点词的形式。
     *
     * @param activity 宿主页面
     * @param view 目标文本视图
     * @param sentence 英文句子
     */
    public static void attach(Activity activity, TextView view, String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return;
        }
        Repo repo = Repo.get(activity);
        Repo.AtlasIndex ready = repo.index();
        if (ready != null) {
            view.setText(linkify(activity, ready, sentence));
            config(view);
            return;
        }
        repo.loadIndex(idx -> {
            if (idx == null || view.getContext() == null) {
                return;
            }
            view.setText(linkify(activity, idx, sentence));
            config(view);
        });
    }

    /**
     * 配置视图的链接点击行为。
     *
     * @param view 文本视图
     */
    private static void config(TextView view) {
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Theme.withAlpha(Theme.accent(), 60));
        view.setLinksClickable(true);
    }

    /**
     * 生成带可点链接的文本。
     *
     * @param activity 宿主页面
     * @param idx 词库索引
     * @param sentence 英文句子
     * @return 带链接的文本
     */
    private static SpannableString linkify(Activity activity, Repo.AtlasIndex idx, String sentence) {
        SpannableString span = new SpannableString(sentence);
        Matcher matcher = WORD.matcher(sentence);
        // 下划线用强调色：明确提示单词可点，又不至于压过正文本身
        final int underline = Theme.withAlpha(Theme.accent(), 170);
        while (matcher.find()) {
            final String token = matcher.group();
            // 每个英文单词都可点：收录的词展示释义，未收录的提示并朗读
            span.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    show(activity, idx, token);
                }

                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    // 单词下方画下划线，让用户一眼看出这里可以点
                    ds.setUnderlineText(true);
                    ds.underlineColor = underline;
                }
            }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    /**
     * 弹出释义卡片并朗读单词。
     *
     * @param activity 宿主页面
     * @param idx 词库索引
     * @param token 被点击的单词
     */
    private static void show(Activity activity, Repo.AtlasIndex idx, String token) {
        final Models.WordIndex hit = resolve(idx, token);
        final String accent = Store.get(activity).accent();
        // 标题展示用户实际点到的词形，释义来自还原后的原形
        final String shown = normalize(token);
        final String original = hit == null ? "" : hit.word;
        final boolean inflected = hit != null && !original.equalsIgnoreCase(shown);

        int pad = Theme.dp(activity, 20);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Theme.dp(activity, 6), pad, 0);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(titleRow);
        titleRow.addView(Theme.word(activity, shown, 26, Theme.text()));

        if (hit != null && hit.cefr >= 0 && hit.cefr < 6) {
            TextView level = Theme.text(activity, cefrName(hit.cefr), 10, Theme.accent(), true);
            level.setBackground(Theme.round(Theme.accentSoft(), 8, activity));
            level.setPadding(Theme.dp(activity, 7), Theme.dp(activity, 3),
                    Theme.dp(activity, 7), Theme.dp(activity, 3));
            LinearLayout.LayoutParams levelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            levelLp.leftMargin = Theme.dp(activity, 10);
            titleRow.addView(level, levelLp);
        }

        if (hit != null && hit.ipa != null && !hit.ipa.isEmpty()) {
            TextView ipa = Theme.text(activity, hit.ipa, 13, Theme.textDim(), false);
            ipa.setTypeface(Typeface.DEFAULT);
            ipa.setPadding(0, Theme.dp(activity, 6), 0, 0);
            box.addView(ipa);
        }

        if (inflected) {
            TextView base = Theme.text(activity, "原形 " + original, 11, Theme.textDim(), false);
            base.setPadding(0, Theme.dp(activity, 6), 0, 0);
            box.addView(base);
        }

        TextView zh = Theme.text(activity,
                hit != null && hit.zh != null && !hit.zh.isEmpty()
                        ? hit.zh : "词库暂未收录，仍可听发音",
                14, hit != null ? Theme.text() : Theme.textDim(), false);
        zh.setLineSpacing(0, 1.35f);
        zh.setPadding(0, Theme.dp(activity, 10), 0, 0);
        box.addView(zh);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Theme.dp(activity, 20), 0, Theme.dp(activity, 4));
        box.addView(actions);

        View speak = pill(activity, "朗读", R.drawable.ic_volume_up, Theme.accent(), Theme.accentSoft());
        actions.addView(speak);
        speak.setOnClickListener(v -> speak(activity, hit, shown, inflected, accent));

        if (hit != null) {
            View open = pill(activity, "完整词条", R.drawable.ic_chevron_right,
                    Theme.text(), Theme.surfaceAlt());
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            openLp.leftMargin = Theme.dp(activity, 10);
            actions.addView(open, openLp);
            open.setOnClickListener(v -> {
                Intent intent = new Intent(activity, WordDetailActivity.class);
                intent.putExtra("id", hit.id);
                activity.startActivity(intent);
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(activity, Theme.dialogTheme())
                .setView(box)
                .create();
        dialog.show();

        // 打开即朗读：原形优先官网音频，变形则按实际拼写合成
        speak(activity, hit, shown, inflected, accent);
    }

    /**
     * 朗读被点击的单词。
     *
     * <p>点击的就是词典原形时走官网预录音频；点到的是变形词时，
     * 用实际拼写合成，避免把 borrowed 读成 borrow。</p>
     *
     * @param activity 宿主页面
     * @param hit 命中的索引项，可为 null
     * @param word 被点击的词形
     * @param inflected 是否为变形词
     * @param accent 口音
     */
    private static void speak(Activity activity, Models.WordIndex hit,
                              String word, boolean inflected, String accent) {
        if (hit != null && !inflected) {
            Voice.get(activity).word(hit.id, word, accent);
        } else {
            Voice.get(activity).sentence(word, accent);
        }
    }

    /**
     * 生成一个胶囊按钮。
     *
     * @param activity 宿主页面
     * @param label 文字
     * @param icon 左侧图标
     * @param color 文字与图标颜色
     * @param background 底色
     * @return 按钮视图
     */
    private static View pill(Activity activity, String label, int icon, int color, int background) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Theme.round(background, 12, activity));
        box.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 9),
                Theme.dp(activity, 14), Theme.dp(activity, 9));

        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setColorFilter(color);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        box.addView(image, new LinearLayout.LayoutParams(
                Theme.dp(activity, 15), Theme.dp(activity, 15)));

        TextView text = Theme.text(activity, label, 13, color, true);
        text.setPadding(Theme.dp(activity, 6), 0, 0, 0);
        box.addView(text);
        Theme.pressable(box);
        return box;
    }

    /**
     * 在词库中解析一个单词，找不到时尝试常见词形变化。
     *
     * @param idx 词库索引
     * @param token 原词
     * @return 索引项；未收录返回 null
     */
    private static Models.WordIndex resolve(Repo.AtlasIndex idx, String token) {
        String word = normalize(token);
        if (word.isEmpty()) {
            return null;
        }
        Models.WordIndex hit = idx.findWord(word);
        if (hit != null) {
            return hit;
        }
        for (String candidate : forms(word)) {
            hit = idx.findWord(candidate);
            if (hit != null) {
                return hit;
            }
        }
        // 连字符词逐段再试一次，例如 well-known
        if (word.indexOf('-') > 0) {
            for (String part : word.split("-")) {
                hit = resolve(idx, part);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 归一化单词拼写。
     *
     * @param token 原词
     * @return 小写、撇号统一后的词
     */
    private static String normalize(String token) {
        String word = token.toLowerCase(Locale.ROOT).replace('’', '\'');
        int from = 0;
        int to = word.length();
        while (from < to && !Character.isLetter(word.charAt(from))) {
            from++;
        }
        while (to > from && (word.charAt(to - 1) == '-' || word.charAt(to - 1) == '\'')) {
            to--;
        }
        return word.substring(from, to);
    }

    /**
     * 生成常见的词形还原候选。
     *
     * @param w 小写单词
     * @return 候选原形列表
     */
    private static List<String> forms(String w) {
        List<String> list = new ArrayList<>();
        int n = w.length();

        if (w.endsWith("'s") && n > 2) {
            list.add(w.substring(0, n - 2));
        }
        if (w.endsWith("ies") && n > 4) {
            list.add(w.substring(0, n - 3) + "y");
        }
        if (w.endsWith("es") && n > 3) {
            list.add(w.substring(0, n - 2));
        }
        if (w.endsWith("s") && n > 2) {
            list.add(w.substring(0, n - 1));
        }
        if (w.endsWith("ied") && n > 4) {
            list.add(w.substring(0, n - 3) + "y");
        }
        if (w.endsWith("ed") && n > 3) {
            list.add(w.substring(0, n - 2));
            list.add(w.substring(0, n - 1));
            if (n > 4 && w.charAt(n - 3) == w.charAt(n - 4)) {
                list.add(w.substring(0, n - 3));
            }
        }
        if (w.endsWith("ing") && n > 4) {
            list.add(w.substring(0, n - 3));
            list.add(w.substring(0, n - 3) + "e");
            if (n > 5 && w.charAt(n - 4) == w.charAt(n - 5)) {
                list.add(w.substring(0, n - 4));
            }
        }
        if (w.endsWith("est") && n > 4) {
            list.add(w.substring(0, n - 3));
        }
        if (w.endsWith("er") && n > 3) {
            list.add(w.substring(0, n - 2));
            list.add(w.substring(0, n - 1));
        }
        if (w.endsWith("ly") && n > 3) {
            list.add(w.substring(0, n - 2));
        }
        return list;
    }

    /**
     * 难度等级转名称。
     *
     * @param cefr 难度下标
     * @return 名称
     */
    private static String cefrName(int cefr) {
        String[] names = {"A1", "A2", "B1", "B2", "C1", "C2"};
        return cefr >= 0 && cefr < names.length ? names[cefr] : "";
    }
}
