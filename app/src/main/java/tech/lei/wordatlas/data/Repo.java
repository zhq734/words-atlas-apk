package tech.lei.wordatlas.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 词库数据仓库。
 *
 * <p>负责读取 APK 内置的站点数据，并在有网络时把站点的更新版词库下载到应用私有目录；
 * 读取时优先使用私有目录中的更新版，缺失时回退到 assets。</p>
 *
 * @author zhenghq
 */
public final class Repo {

    /** 站点根地址，用于媒体与在线更新。 */
    public static final String SITE = "https://0xlei.tech/words/";

    /** 更新目录相对于 filesDir 的位置。 */
    private static final String UPDATE_DIR = "update";

    /** 更新检查的最小间隔（毫秒）。 */
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    /** 分片缓存条目上限。 */
    private static final int CHUNK_CACHE = 6;

    private static Repo instance;

    private final Context context;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private volatile AtlasIndex index;
    private final Map<Integer, List<Models.WordEntry>> chunkCache =
            Collections.synchronizedMap(new LinkedHashMap<Integer, List<Models.WordEntry>>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<Models.WordEntry>> eldest) {
                    return size() > CHUNK_CACHE;
                }
            });

    private volatile List<Models.Paraphrase> paraphrases;
    private volatile List<Models.ConfusableGroup> confusables;
    private volatile List<Models.ReadingIndex> readingIndex;
    private volatile Models.BookInfo book;
    private volatile List<Models.Scene> scenes;
    private volatile Set<String> classicIds;

    /**
     * 取得单例。
     *
     * @param context 任意上下文，内部使用应用上下文
     * @return 仓库实例
     */
    public static synchronized Repo get(Context context) {
        if (instance == null) {
            instance = new Repo(context.getApplicationContext());
        }
        return instance;
    }

    private Repo(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("repo", Context.MODE_PRIVATE);
    }

    /** 词库索引，包含词表与维度定义。 */
    public static final class AtlasIndex {
        public int version = 1;
        public String name = "";
        public String license = "";
        public int count;
        public int chunk = 100;
        public final List<Models.WordIndex> words = new ArrayList<>();
        public final List<String[]> topics = new ArrayList<>();
        public final List<String> cefr = new ArrayList<>();

        /** 小写单词到索引项的反查表，首次查询时构建。 */
        private Map<String, Models.WordIndex> byWord;

        /**
         * 按词 id 查索引项。
         *
         * @param id 词 id
         * @return 索引项；不存在返回 null
         */
        public Models.WordIndex find(String id) {
            for (Models.WordIndex w : words) {
                if (w.id.equals(id)) {
                    return w;
                }
            }
            return null;
        }

        /**
         * 按单词拼写查索引项，大小写不敏感。
         *
         * @param word 单词
         * @return 索引项；未收录返回 null
         */
        public synchronized Models.WordIndex findWord(String word) {
            if (word == null || word.isEmpty()) {
                return null;
            }
            if (byWord == null) {
                byWord = new java.util.HashMap<>(words.size() * 2);
                for (Models.WordIndex w : words) {
                    String key = w.word.toLowerCase(java.util.Locale.ROOT);
                    if (!byWord.containsKey(key)) {
                        byWord.put(key, w);
                    }
                }
            }
            return byWord.get(word.toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** 数据加载回调。 */
    public interface Callback<T> {
        /**
         * @param value 加载结果
         */
        void onResult(T value);
    }

    /**
     * 异步加载词库索引，已加载时直接回调。
     *
     * @param cb 回调
     */
    public void loadIndex(Callback<AtlasIndex> cb) {
        AtlasIndex ready = index;
        if (ready != null) {
            cb.onResult(ready);
            return;
        }
        io.execute(() -> {
            AtlasIndex built = null;
            try {
                built = parseIndex(readText("atlas/index.json"));
            } catch (Exception ignored) {
            }
            index = built;
            AtlasIndex result = built;
            main.post(() -> cb.onResult(result));
        });
    }

    /**
     * 拼接站点资源地址。
     *
     * @param rel 相对路径
     * @return 完整地址
     */
    public static String site(String rel) {
        return SITE + rel;
    }

    /**
     * 词条列表缩略图地址。
     *
     * @param id 词 id
     * @return 完整地址
     */
    public static String thumbUrl(String id) {
        return SITE + "atlas/thumb/" + id + ".webp";
    }

    /**
     * 词条大插画地址。
     *
     * @param id 词 id
     * @param classic 是否使用经典线稿
     * @param hasClassic 该词是否有经典线稿
     * @return 完整地址
     */
    public static String imageUrl(String id, boolean classic, boolean hasClassic) {
        if (classic && hasClassic) {
            return SITE + "atlas/classic/img/" + id + ".webp";
        }
        return SITE + "atlas/img/" + id + ".webp";
    }

    /**
     * 发音地址。
     *
     * @param id 词 id
     * @param accent uk 或 us
     * @return 完整地址
     */
    public static String audioUrl(String id, String accent) {
        return SITE + "atlas/audio/" + ("us".equals(accent) ? "us" : "uk") + "/" + id + ".mp3";
    }

    /**
     * 判断某个词是否提供经典线稿插画。
     *
     * @param id 词 id
     * @return 有则返回 true
     */
    public boolean hasClassic(String id) {
        Set<String> ids = classicIds;
        if (ids == null) {
            ids = new java.util.HashSet<>();
            try {
                JSONObject o = new JSONObject(readText("atlas/classic/index.json"));
                JSONArray arr = o.optJSONArray("ids");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        ids.add(arr.optString(i));
                    }
                }
            } catch (Exception ignored) {
            }
            classicIds = ids;
        }
        return ids.contains(id);
    }

    /**
     * 已加载的索引。
     *
     * @return 索引；尚未加载完成时返回 null
     */
    public AtlasIndex index() {
        return index;
    }

    /**
     * 读取指定顺序的词条完整数据。
     *
     * @param order 词在索引中的顺序
     * @return 词条；越界或解析失败返回 null
     */
    public Models.WordEntry entry(int order) {
        int chunk = 100;
        AtlasIndex idx = index;
        if (idx != null) {
            chunk = idx.chunk;
        }
        int chunkIndex = order / chunk;
        List<Models.WordEntry> list = chunkCache.get(chunkIndex);
        if (list == null) {
            list = parseEntries(readText("atlas/entries/" + chunkIndex + ".json"));
            if (list.isEmpty()) {
                return null;
            }
            chunkCache.put(chunkIndex, list);
        }
        int offset = order % chunk;
        return offset < list.size() ? list.get(offset) : null;
    }

    /**
     * 全部同义替换题。
     *
     * @return 题目列表
     */
    public List<Models.Paraphrase> paraphrases() {
        List<Models.Paraphrase> local = paraphrases;
        if (local != null) {
            return local;
        }
        List<Models.Paraphrase> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readText("atlas/extras/paraphrase.json"));
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        list.add(Models.Paraphrase.parse(o));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        paraphrases = list;
        return list;
    }

    /**
     * 全部易混词组。
     *
     * @return 组列表
     */
    public List<Models.ConfusableGroup> confusables() {
        List<Models.ConfusableGroup> local = confusables;
        if (local != null) {
            return local;
        }
        List<Models.ConfusableGroup> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readText("atlas/extras/confusables.json"));
            JSONArray arr = root.optJSONArray("groups");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        list.add(Models.ConfusableGroup.parse(o));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        confusables = list;
        return list;
    }

    /**
     * 阅读短文索引。
     *
     * @return 索引列表
     */
    public List<Models.ReadingIndex> readings() {
        List<Models.ReadingIndex> local = readingIndex;
        if (local != null) {
            return local;
        }
        List<Models.ReadingIndex> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readText("atlas/extras/readings/index.json"));
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    Models.ReadingIndex r = new Models.ReadingIndex();
                    r.id = o.optString("id");
                    r.title = o.optString("title");
                    r.titleZh = o.optString("titleZh");
                    r.topic = o.optString("topic");
                    r.cefr = o.optInt("cefr");
                    JSONArray ids = o.optJSONArray("ids");
                    if (ids != null) {
                        for (int k = 0; k < ids.length(); k++) {
                            r.wordIds.add(ids.optString(k));
                        }
                    }
                    list.add(r);
                }
            }
        } catch (Exception ignored) {
        }
        readingIndex = list;
        return list;
    }

    /**
     * 获取一篇阅读短文的正文，优先本地缓存，其次在线下载。
     *
     * @param id 短文 id
     * @return 短文；失败返回 null
     */
    public Models.Reading reading(String id) {
        String rel = "atlas/extras/readings/" + id + ".json";
        String text = readText(rel);
        if (text == null) {
            text = downloadToCache(rel);
        }
        if (text == null) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(text);
            Models.Reading r = new Models.Reading();
            r.id = o.optString("id");
            r.title = o.optString("title");
            r.titleZh = o.optString("titleZh");
            r.cefr = o.optInt("cefr");
            JSONArray paras = o.optJSONArray("paras");
            if (paras != null) {
                for (int i = 0; i < paras.length(); i++) {
                    r.paras.add(paras.optString(i));
                }
            }
            JSONArray zh = o.optJSONArray("zh");
            if (zh != null) {
                for (int i = 0; i < zh.length(); i++) {
                    r.zh.add(zh.optString(i));
                }
            }
            JSONArray q = o.optJSONArray("q");
            if (q != null) {
                for (int i = 0; i < q.length(); i++) {
                    JSONObject x = q.optJSONObject(i);
                    if (x == null) {
                        continue;
                    }
                    Models.ReadingQuestion rq = new Models.ReadingQuestion();
                    rq.s = x.optString("s");
                    rq.a = x.optString("a", "T");
                    rq.why = x.optString("why");
                    r.questions.add(rq);
                }
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 书房书目。
     *
     * @return 书目信息；失败返回 null
     */
    public Models.BookInfo book() {
        Models.BookInfo local = book;
        if (local != null) {
            return local;
        }
        try {
            JSONObject root = new JSONObject(readText("books/index.json"));
            JSONArray books = root.optJSONArray("books");
            if (books == null || books.length() == 0) {
                return null;
            }
            JSONObject o = books.optJSONObject(0);
            Models.BookInfo b = new Models.BookInfo();
            b.id = o.optString("id");
            b.title = o.optString("title");
            b.titleZh = o.optString("titleZh");
            b.author = o.optString("author");
            b.authorZh = o.optString("authorZh");
            b.year = o.optInt("year");
            b.level = o.optString("level");
            b.accent = o.optString("accent", "uk");
            b.chapterCount = o.optInt("chapterCount");
            b.cover = o.optString("cover");
            b.coverSmall = o.optString("coverSmall");
            b.firstLine = o.optString("firstLine");
            b.firstLineZh = o.optString("firstLineZh");
            b.blurbZh = o.optString("blurbZh");
            b.tone = o.optString("tone", "#f9ebd2");
            JSONObject keyLine = o.optJSONObject("keyLine");
            if (keyLine != null) {
                String en = keyLine.optString("en");
                String zh = keyLine.optString("zh");
                b.keyLine = en.isEmpty() ? zh : en + "｜" + zh;
            } else {
                b.keyLine = o.optString("keyLine");
            }
            JSONArray chs = o.optJSONArray("chapters");
            if (chs != null) {
                for (int i = 0; i < chs.length(); i++) {
                    JSONObject c = chs.optJSONObject(i);
                    if (c == null) {
                        continue;
                    }
                    Models.ChapterInfo ci = new Models.ChapterInfo();
                    ci.n = c.optInt("n");
                    ci.label = c.optString("label");
                    ci.labelZh = c.optString("labelZh");
                    ci.titleZh = c.optString("titleZh");
                    ci.ready = c.optBoolean("ready");
                    ci.words = c.optInt("words");
                    ci.minutes = c.optInt("minutes");
                    ci.audio = c.optBoolean("audio");
                    ci.plate = c.optString("plate");
                    b.chapters.add(ci);
                }
            }
            book = b;
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取指定章节正文。
     *
     * @param bookId 书 id
     * @param n 章节号
     * @return 章节；失败返回 null
     */
    public Models.Chapter chapter(String bookId, int n) {
        try {
            JSONObject o = new JSONObject(readText("books/" + bookId + "/ch-" + n + ".json"));
            Models.Chapter c = new Models.Chapter();
            c.book = o.optString("book");
            c.n = o.optInt("n");
            c.titleZh = o.optString("titleZh");
            JSONArray paras = o.optJSONArray("paras");
            if (paras != null) {
                for (int i = 0; i < paras.length(); i++) {
                    JSONObject p = paras.optJSONObject(i);
                    if (p == null) {
                        continue;
                    }
                    Models.Paragraph para = new Models.Paragraph();
                    para.id = p.optString("id");
                    para.kind = p.optString("kind", "p");
                    para.en = p.optString("en");
                    para.zh = p.optString("zh");
                    para.audio = p.optString("audio");
                    para.audioZh = p.optString("audioZh");
                    c.paras.add(para);
                }
            }
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 书房场景插画。
     *
     * @return 场景列表
     */
    public List<Models.Scene> scenes() {
        List<Models.Scene> local = scenes;
        if (local != null) {
            return local;
        }
        List<Models.Scene> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readText("books/the-little-prince/scenes.json"));
            JSONArray arr = root.optJSONArray("scenes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    Models.Scene s = new Models.Scene();
                    s.n = o.optInt("n");
                    s.pid = o.optString("pid");
                    s.img = o.optString("img");
                    s.small = o.optString("small");
                    s.captionZh = o.optString("captionZh");
                    s.tone = o.optString("tone", "#f9ebd2");
                    list.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        scenes = list;
        return list;
    }

    /**
     * 后台检查词库更新，更新版写入应用私有目录。
     *
     * <p>检查间隔为 24 小时；只在远端 version 大于本地时下载。</p>
     *
     * @param notify 有更新时的回调，运行在主线程
     */
    public void checkUpdate(Runnable notify) {
        long last = prefs.getLong("lastCheck", 0L);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            return;
        }
        io.execute(() -> {
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply();
            try {
                String remote = httpGet(SITE + "atlas/index.json");
                if (remote == null) {
                    return;
                }
                AtlasIndex remoteIdx = parseIndex(remote);
                int localVersion = prefs.getInt("dataVersion", 1);
                if (remoteIdx == null || remoteIdx.version <= localVersion) {
                    return;
                }
                writeUpdate("atlas/index.json", remote.getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i * remoteIdx.chunk < remoteIdx.count; i++) {
                    String rel = "atlas/entries/" + i + ".json";
                    String body = httpGet(SITE + rel);
                    if (body != null) {
                        writeUpdate(rel, body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                copyExtra("atlas/extras/paraphrase.json");
                copyExtra("atlas/extras/confusables.json");
                copyExtra("atlas/extras/readings/index.json");
                copyExtra("atlas/classic/index.json");
                prefs.edit().putInt("dataVersion", remoteIdx.version).apply();
                chunkCache.clear();
                index = null;
                if (notify != null) {
                    main.post(notify);
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 下载一个附带数据文件到更新目录。
     *
     * @param rel 相对路径
     */
    private void copyExtra(String rel) {
        String body = httpGet(SITE + rel);
        if (body != null) {
            writeUpdate(rel, body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 读取文本资源：私有更新目录优先，其次 assets。
     *
     * @param rel 相对于 words 根的路径
     * @return 文本；不存在返回 null
     */
    public String readText(String rel) {
        File updated = new File(new File(context.getFilesDir(), UPDATE_DIR), rel);
        if (updated.isFile()) {
            try (InputStream in = new java.io.FileInputStream(updated)) {
                return readAll(in);
            } catch (IOException ignored) {
            }
        }
        try (InputStream in = context.getAssets().open("words/" + rel)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 把内容写入私有更新目录。
     *
     * @param rel 相对路径
     * @param data 字节内容
     */
    private void writeUpdate(String rel, byte[] data) {
        File dest = new File(new File(context.getFilesDir(), UPDATE_DIR), rel);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(data);
        } catch (IOException ignored) {
        }
    }

    /**
     * 在线下载并缓存一个数据文件。
     *
     * @param rel 相对路径
     * @return 文本；失败返回 null
     */
    private String downloadToCache(String rel) {
        String body = httpGet(SITE + rel);
        if (body == null) {
            return null;
        }
        writeUpdate(rel, body.getBytes(StandardCharsets.UTF_8));
        return body;
    }

    /**
     * 解析词库索引 JSON。
     *
     * @param text 原文
     * @return 索引；解析失败返回 null
     */
    private static AtlasIndex parseIndex(String text) {
        if (text == null) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(text);
            AtlasIndex idx = new AtlasIndex();
            idx.version = o.optInt("version", 1);
            idx.name = o.optString("name");
            idx.license = o.optString("license");
            idx.count = o.optInt("count");
            idx.chunk = o.optInt("chunk", 100);
            JSONArray cefr = o.optJSONArray("cefr");
            if (cefr != null) {
                for (int i = 0; i < cefr.length(); i++) {
                    idx.cefr.add(cefr.optString(i));
                }
            }
            JSONArray topics = o.optJSONArray("topics");
            if (topics != null) {
                for (int i = 0; i < topics.length(); i++) {
                    JSONArray t = topics.optJSONArray(i);
                    if (t != null) {
                        idx.topics.add(new String[]{t.optString(0), t.optString(1)});
                    }
                }
            }
            JSONArray words = o.optJSONArray("words");
            if (words != null) {
                for (int i = 0; i < words.length(); i++) {
                    JSONArray w = words.optJSONArray(i);
                    if (w == null) {
                        continue;
                    }
                    idx.words.add(new Models.WordIndex(
                            w.optString(0), w.optString(1), w.optString(2),
                            w.optString(3), w.optInt(4), w.optInt(5), i));
                }
            }
            return idx;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析一个词条分片。
     *
     * @param text 分片原文
     * @return 词条列表
     */
    private static List<Models.WordEntry> parseEntries(String text) {
        List<Models.WordEntry> list = new ArrayList<>();
        if (text == null) {
            return list;
        }
        try {
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    list.add(Models.WordEntry.parse(o));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * 简易 GET 请求。
     *
     * @param url 地址
     * @return 响应文本；失败返回 null
     */
    public static String httpGet(String url) {
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
                return readAll(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 读取输入流全部内容（UTF-8）。
     *
     * @param in 输入流
     * @return 文本
     * @throws IOException 读取失败
     */
    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}
