package tech.lei.wordatlas.data;

import android.content.Context;

import java.io.File;

/**
 * 统一发音入口：把三种音源按优先级串起来。
 *
 * <p>优先级与用户可见的效果一致：</p>
 * <ul>
 *   <li>单词：官网预录 mp3 → Edge TTS 合成 → 系统语音合成</li>
 *   <li>例句：官网没有例句音频，直接 Edge TTS 合成 → 系统语音合成</li>
 * </ul>
 *
 * <p>任何一级失败都会自动降级到下一级，调用方只需要给文本，不用关心网络与语音包。</p>
 *
 * @author zhenghq
 */
public final class Voice {

    private static Voice instance;

    private final Context context;

    /** 请求代号，用于丢弃已经过期的合成结果。 */
    private int generation;

    /**
     * 取得单例。
     *
     * @param context 任意上下文
     * @return 发音入口
     */
    public static synchronized Voice get(Context context) {
        if (instance == null) {
            instance = new Voice(context.getApplicationContext());
        }
        return instance;
    }

    private Voice(Context context) {
        this.context = context;
    }

    /**
     * 朗读一个单词：官网音频 → Edge TTS → 系统 TTS。
     *
     * @param id 词条 id，可为空；为空时跳过官网音频
     * @param word 单词拼写，用于合成兜底
     * @param accent 口音：us 或 uk
     */
    public void word(String id, String word, String accent) {
        stop();
        final int token = generation;
        final String key = normalize(accent);
        if (id != null && !id.isEmpty()) {
            Media.get(context).play(Repo.audioUrl(id, key),
                    () -> synthesize(word, key, token));
        } else {
            synthesize(word, key, token);
        }
    }

    /**
     * 朗读一段英文，例如例句：Edge TTS → 系统 TTS。
     *
     * @param text 文本
     * @param accent 口音：us 或 uk
     */
    public void sentence(String text, String accent) {
        stop();
        synthesize(text, normalize(accent), generation);
    }

    /**
     * 播放一段带官网音频的文本：官网音频 → Edge TTS → 系统 TTS。
     *
     * <p>用于书房段落这类官网已经提供 mp3 的内容；官网音频缺失或加载失败时
     * 自动改用合成朗读，不会没有声音。</p>
     *
     * @param url 官网音频地址，可为空
     * @param text 文本
     * @param accent 口音：us 或 uk
     */
    public void source(String url, String text, String accent) {
        stop();
        final int token = generation;
        final String key = normalize(accent);
        if (url != null && !url.isEmpty()) {
            Media.get(context).play(url, () -> synthesize(text, key, token));
        } else {
            synthesize(text, key, token);
        }
    }

    /** 停止当前播放与尚未完成的合成结果。 */
    public void stop() {
        generation++;
        Media.get(context).stop();
        Speech.get(context).stop();
    }

    /**
     * 用 Edge TTS 合成，失败退回系统语音合成。
     *
     * @param text 文本
     * @param accent 口音
     * @param token 请求代号
     */
    private void synthesize(String text, String accent, int token) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        final String value = text.trim();
        EdgeTts.get(context).synthesize(value, accent, new EdgeTts.Callback() {
            @Override
            public void onAudio(File file) {
                if (token != generation) {
                    return;
                }
                Media.get(context).play(file.getAbsolutePath(), () -> {
                    if (token == generation) {
                        Speech.get(context).speak(value, accent);
                    }
                });
            }

            @Override
            public void onError() {
                if (token == generation) {
                    Speech.get(context).speak(value, accent);
                }
            }
        });
    }

    /**
     * 归一化口音代码。
     *
     * @param accent 口音
     * @return us 或 uk
     */
    private static String normalize(String accent) {
        return "us".equals(accent) ? "us" : "uk";
    }
}
