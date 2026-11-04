package tech.lei.wordatlas.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 媒体加载器：插画的内存与磁盘缓存、发音播放。
 *
 * <p>插画与音频都来自官方站点，本地只缓存已经下载过的文件，因此离线时仍能看到
 * 之前浏览过的图片、听到之前播放过的发音。</p>
 *
 * @author zhenghq
 */
public final class Media {

    private static Media instance;

    private final File imageDir;
    private final File audioDir;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory;

    private MediaPlayer player;

    /**
     * 取得单例。
     *
     * @param context 任意上下文
     * @return 媒体加载器
     */
    public static synchronized Media get(android.content.Context context) {
        if (instance == null) {
            instance = new Media(context.getApplicationContext());
        }
        return instance;
    }

    private Media(android.content.Context context) {
        imageDir = new File(context.getCacheDir(), "img");
        audioDir = new File(context.getCacheDir(), "audio");
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memory = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /**
     * 把图片地址绑定到 ImageView；命中缓存时同步显示，否则后台下载。
     *
     * @param view 目标视图
     * @param url 完整地址，可为 null
     * @param placeholder 占位色
     */
    public void loadImage(ImageView view, String url, int placeholder) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            view.setBackgroundColor(placeholder);
            return;
        }
        view.setTag(url);
        Bitmap cached = memory.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        view.setBackgroundColor(placeholder);
        pool.execute(() -> {
            Bitmap bitmap = loadBitmap(url);
            if (bitmap == null) {
                return;
            }
            memory.put(url, bitmap);
            main.post(() -> {
                if (url.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                    view.setBackgroundColor(0);
                }
            });
        });
    }

    /**
     * 读取位图：内存 → 磁盘 → 网络。
     *
     * @param url 图片地址
     * @return 位图；失败返回 null
     */
    private Bitmap loadBitmap(String url) {
        File file = new File(imageDir, md5(url));
        if (file.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (b != null) {
                return b;
            }
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "WordAtlasApp/2.0");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                FileOutputStream out = new FileOutputStream(file);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.close();
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 播放发音。
     *
     * <p>优先使用上次播放时缓存到磁盘的音频文件，没有缓存再走网络流式播放，
     * 并在后台顺手把音频存下来，之后离线也能听。传入本地文件路径时直接播放，
     * 不参与网络缓存。</p>
     *
     * @param url 音频地址，可以是 http 地址或本地文件路径
     * @param onError 播放失败回调，可为 null
     */
    public void play(String url, Runnable onError) {
        if (url == null || url.isEmpty()) {
            return;
        }
        stop();
        boolean remote = url.startsWith("http");
        File cached = remote ? new File(audioDir, md5(url)) : null;
        boolean local = cached != null && cached.isFile() && cached.length() > 0;
        try {
            player = new MediaPlayer();
            player.setDataSource(local ? cached.getAbsolutePath() : url);
            if (remote && !local) {
                cacheAudio(url);
            }
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> stop());
            player.setOnErrorListener((mp, what, extra) -> {
                stop();
                if (onError != null) {
                    main.post(onError);
                }
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            stop();
            if (onError != null) {
                main.post(onError);
            }
        }
    }

    /** 停止当前播放。 */
    public void stop() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    /**
     * 在后台把音频下载到磁盘缓存，供下次离线播放。
     *
     * @param url 音频地址
     */
    private void cacheAudio(String url) {
        pool.execute(() -> {
            File file = new File(audioDir, md5(url));
            if (file.isFile() && file.length() > 0) {
                return;
            }
            HttpURLConnection conn = null;
            File tmp = new File(audioDir, md5(url) + ".tmp");
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "WordAtlasApp/2.0");
                if (conn.getResponseCode() != 200) {
                    return;
                }
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete();
                }
            } catch (Exception ignored) {
                tmp.delete();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * 计算字符串的 MD5，作为缓存文件名。
     *
     * @param text 原文
     * @return 32 位十六进制串
     */
    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
