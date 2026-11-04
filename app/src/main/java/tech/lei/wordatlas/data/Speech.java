package tech.lei.wordatlas.data;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/**
 * 例句朗读器：调用系统语音合成（TTS）朗读英文句子。
 *
 * <p>官网本身没有例句的预录音频，浏览器的朗读按钮用的是 speechSynthesis；
 * Android 端对应地使用系统 TTS。朗读口音跟随「我的 → 发音口音」设置，
 * 英音用 en-GB、美音用 en-US。系统未安装英文语音数据时静默失败，
 * 由调用方决定是否提示。</p>
 *
 * @author zhenghq
 */
public final class Speech {

    private static Speech instance;

    private TextToSpeech tts;
    private boolean ready;
    private boolean broken;
    private final Queue<Request> pending = new ArrayDeque<>();
    private Runnable onError;

    /** 一条待朗读的请求。 */
    private static final class Request {
        final String text;
        final String accent;

        Request(String text, String accent) {
            this.text = text;
            this.accent = accent;
        }
    }

    /**
     * 取得单例。
     *
     * @param context 任意上下文
     * @return 朗读器
     */
    public static synchronized Speech get(Context context) {
        if (instance == null) {
            instance = new Speech(context.getApplicationContext());
        }
        return instance;
    }

    private Speech(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status != TextToSpeech.SUCCESS) {
                broken = true;
                pending.clear();
                return;
            }
            tts.setSpeechRate(0.95f);
            tts.setPitch(1f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                }

                @Override
                public void onError(String utteranceId) {
                    notifyError();
                }
            });
            ready = true;
            while (!pending.isEmpty()) {
                speakNow(pending.poll());
            }
        });
    }

    /**
     * 用默认英音朗读一段英文。
     *
     * @param text 要朗读的文本
     */
    public void speak(String text) {
        speak(text, "uk");
    }

    /**
     * 按指定口音朗读一段英文。
     *
     * @param text 要朗读的文本
     * @param accent 口音：us 或 uk
     */
    public void speak(String text, String accent) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        stop();
        Request request = new Request(text, "us".equals(accent) ? "us" : "uk");
        if (broken) {
            notifyError();
            return;
        }
        if (!ready) {
            pending.add(request);
            return;
        }
        speakNow(request);
    }

    /**
     * 停止朗读。
     */
    public void stop() {
        pending.clear();
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 设置朗读失败时的回调，用于提示用户。
     *
     * @param callback 回调，可为 null
     */
    public void onError(Runnable callback) {
        this.onError = callback;
    }

    /**
     * 立即提交朗读请求。
     *
     * @param request 朗读请求
     */
    private void speakNow(Request request) {
        try {
            int result = tts.setLanguage("us".equals(request.accent) ? Locale.US : Locale.UK);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 没有对应口音的语音包时退回系统默认语言，仍可能读出英文
                tts.setLanguage(Locale.getDefault());
            }
            tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null,
                    "wa-" + request.text.hashCode());
        } catch (Exception e) {
            notifyError();
        }
    }

    /** 在主线程发出失败回调。 */
    private void notifyError() {
        Runnable callback = onError;
        if (callback != null) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(callback);
        }
    }
}
