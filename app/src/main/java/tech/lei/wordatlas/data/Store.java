package tech.lei.wordatlas.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 本地学习状态存储：设置、收藏、复习进度与打卡统计。
 *
 * <p>全部数据保存在 SharedPreferences 中，导出时序列化为一段 JSON，便于备份与迁移。
 * 复习采用简化间隔序列：1、2、4、7、15、30 天。</p>
 *
 * @author zhenghq
 */
public final class Store {

    /** 复习间隔（天）。 */
    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};

    private static Store instance;

    private final SharedPreferences prefs;

    /** 学习进度内存缓存，避免列表滚动时反复读盘与解析。 */
    private final java.util.HashMap<String, Progress> cache = new java.util.HashMap<>();

    /**
     * 取得单例。
     *
     * @param context 任意上下文
     * @return 存储实例
     */
    public static synchronized Store get(Context context) {
        if (instance == null) {
            instance = new Store(context.getApplicationContext());
        }
        return instance;
    }

    private Store(Context context) {
        prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE);
        reloadCache();
    }

    /**
     * 把持久化的 JSON 解析为进度对象。
     *
     * @param raw JSON 文本
     * @return 进度；解析失败返回 null
     */
    private static Progress parseProgress(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            Progress p = new Progress();
            p.level = o.optInt("lv");
            p.due = o.optLong("due");
            p.correct = o.optInt("ok");
            p.wrong = o.optInt("bad");
            p.last = o.optLong("ts");
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 设置

    /**
     * 主题模式。
     *
     * @return dark 或 light
     */
    public String theme() {
        return prefs.getString("theme", "dark");
    }

    /** @param mode dark 或 light */
    public void setTheme(String mode) {
        prefs.edit().putString("theme", mode).apply();
    }

    /**
     * 发音口音。
     *
     * @return uk 或 us
     */
    public String accent() {
        return prefs.getString("accent", "uk");
    }

    /** @param accent uk 或 us */
    public void setAccent(String accent) {
        prefs.edit().putString("accent", accent).apply();
    }

    /**
     * 每日新词数量。
     *
     * @return 数量
     */
    public int daily() {
        return prefs.getInt("daily", 10);
    }

    /** @param n 每日新词数量 */
    public void setDaily(int n) {
        prefs.edit().putInt("daily", n).apply();
    }

    /**
     * 学习起点难度下标。
     *
     * @return 0-5
     */
    public int startCefr() {
        return prefs.getInt("startCefr", 0);
    }

    /** @param cefr 起点难度下标 */
    public void setStartCefr(int cefr) {
        prefs.edit().putInt("startCefr", cefr).apply();
    }

    /**
     * 考试日期（毫秒时间戳）。
     *
     * @return 时间戳，0 表示未设置
     */
    public long examDate() {
        return prefs.getLong("examDate", 0L);
    }

    /** @param millis 考试日期时间戳，0 表示清除 */
    public void setExamDate(long millis) {
        prefs.edit().putLong("examDate", millis).apply();
    }

    /**
     * 插画风格。
     *
     * @return full 或 classic
     */
    public String illustration() {
        return prefs.getString("illus", "full");
    }

    /** @param style full 或 classic */
    public void setIllustration(String style) {
        prefs.edit().putString("illus", style).apply();
    }

    /**
     * 是否已完成首次设置。
     *
     * @return 已完成返回 true
     */
    public boolean onboarded() {
        return prefs.getBoolean("onboarded", false);
    }

    /** 标记首次设置完成。 */
    public void setOnboarded() {
        prefs.edit().putBoolean("onboarded", true).apply();
    }

    // ---------------------------------------------------------------- 词状态

    /** 单个词的学习记录。 */
    public static final class Progress {
        /** 掌握等级，0 表示刚接触。 */
        public int level;
        /** 下次复习时间。 */
        public long due;
        /** 答对次数。 */
        public int correct;
        /** 答错次数。 */
        public int wrong;
        /** 最近学习时间。 */
        public long last;
    }

    /**
     * 读取某个词的学习记录。
     *
     * @param id 词 id
     * @return 记录；未学过返回 null
     */
    public Progress progress(String id) {
        return cache.get(id);
    }

    /**
     * 记录一次学习结果并推进或回退复习间隔。
     *
     * @param id 词 id
     * @param known 是否认识
     */
    public void record(String id, boolean known) {
        Progress p = progress(id);
        if (p == null) {
            p = new Progress();
        }
        long now = System.currentTimeMillis();
        if (known) {
            p.level = Math.min(p.level + 1, INTERVALS.length);
            p.correct++;
        } else {
            p.level = 0;
            p.wrong++;
        }
        p.last = now;
        int days = INTERVALS[Math.max(0, Math.min(p.level - 1, INTERVALS.length - 1))];
        p.due = now + (long) days * 24 * 60 * 60 * 1000;
        try {
            JSONObject o = new JSONObject();
            o.put("lv", p.level);
            o.put("due", p.due);
            o.put("ok", p.correct);
            o.put("bad", p.wrong);
            o.put("ts", p.last);
            cache.put(id, p);
            prefs.edit().putString("p:" + id, o.toString()).apply();
        } catch (Exception ignored) {
        }
        touchToday();
    }

    /**
     * 词的学习状态：0 未学、1 学习中、2 较熟悉。
     *
     * @param id 词 id
     * @return 状态码
     */
    public int state(String id) {
        Progress p = progress(id);
        if (p == null) {
            return 0;
        }
        return p.level >= 3 ? 2 : 1;
    }

    /**
     * 是否到期待复习。
     *
     * @param id 词 id
     * @return 到期返回 true
     */
    public boolean isDue(String id) {
        Progress p = progress(id);
        return p != null && p.due <= System.currentTimeMillis();
    }

    /**
     * 全部学习记录，供统计与最近学习列表使用。
     *
     * @return 不可变视图
     */
    public Map<String, Progress> allProgress() {
        return java.util.Collections.unmodifiableMap(cache);
    }

    /**
     * 已学习词数。
     *
     * @return 数量
     */
    public int learnedCount() {
        return cache.size();
    }

    /**
     * 较熟悉的词数。
     *
     * @return 数量
     */
    public int masteredCount() {
        int n = 0;
        for (Progress p : cache.values()) {
            if (p.level >= 3) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 收藏

    /**
     * 切换收藏状态。
     *
     * @param id 词 id
     * @return 切换后是否已收藏
     */
    public boolean toggleFavorite(String id) {
        Set<String> set = favorites();
        boolean added;
        if (set.contains(id)) {
            set.remove(id);
            added = false;
        } else {
            set.add(id);
            added = true;
        }
        prefs.edit().putString("fav", String.join(",", set)).apply();
        return added;
    }

    /**
     * 收藏的词 id 集合。
     *
     * @return 集合
     */
    public Set<String> favorites() {
        String raw = prefs.getString("fav", "");
        Set<String> set = new LinkedHashSet<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                if (!s.isEmpty()) {
                    set.add(s);
                }
            }
        }
        return set;
    }

    /**
     * 是否已收藏。
     *
     * @param id 词 id
     * @return 已收藏返回 true
     */
    public boolean isFavorite(String id) {
        return favorites().contains(id);
    }

    // ---------------------------------------------------------------- 统计

    /**
     * 记录今天有学习行为。
     */
    private void touchToday() {
        String today = dayKey(System.currentTimeMillis());
        // 先补打卡历史：今天已经打过卡的也要确保写进记录
        addCheckin(today);
        String last = prefs.getString("lastDay", "");
        if (today.equals(last)) {
            return;
        }
        int streak = prefs.getInt("streak", 0);
        String yesterday = dayKey(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        streak = yesterday.equals(last) ? streak + 1 : 1;
        prefs.edit().putString("lastDay", today).putInt("streak", streak).apply();
    }

    /**
     * 打卡日期集合，格式 yyyy-MM-dd。
     *
     * <p>旧版本只保存最后打卡日与连续天数，这里按其连续天数向前反推补齐，
     * 让升级后的打卡日历也能看到已有记录。</p>
     *
     * @return 日期集合
     */
    public Set<String> checkins() {
        Set<String> set = new LinkedHashSet<>();
        String raw = prefs.getString("checkins", "");
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                if (!s.isEmpty()) {
                    set.add(s);
                }
            }
        }
        if (set.isEmpty()) {
            String last = prefs.getString("lastDay", "");
            int streak = prefs.getInt("streak", 0);
            if (!last.isEmpty() && streak > 0) {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                    Calendar c = Calendar.getInstance();
                    c.setTime(f.parse(last));
                    for (int i = 0; i < streak; i++) {
                        set.add(f.format(c.getTime()));
                        c.add(Calendar.DAY_OF_MONTH, -1);
                    }
                } catch (Exception ignored) {
                }
                // 迁移结果立即落盘，之后不必再重复反推
                if (!set.isEmpty()) {
                    prefs.edit().putString("checkins", String.join(",", set)).apply();
                }
            }
        }
        return set;
    }

    /**
     * 写入某天的打卡记录。
     *
     * @param day 日期键 yyyy-MM-dd
     */
    private void addCheckin(String day) {
        Set<String> set = checkins();
        if (set.add(day)) {
            prefs.edit().putString("checkins", String.join(",", set)).apply();
        }
    }

    /**
     * 连续打卡天数。
     *
     * @return 天数
     */
    public int streak() {
        String last = prefs.getString("lastDay", "");
        String today = dayKey(System.currentTimeMillis());
        String yesterday = dayKey(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        if (!today.equals(last) && !yesterday.equals(last)) {
            return 0;
        }
        return prefs.getInt("streak", 0);
    }

    /**
     * 今天已学习的新词数。
     *
     * @return 数量
     */
    public int todayLearned() {
        String today = dayKey(System.currentTimeMillis());
        if (!today.equals(prefs.getString("learnDay", ""))) {
            return 0;
        }
        return prefs.getInt("learnToday", 0);
    }

    /** 累加今天学习的新词数。 */
    public void bumpTodayLearned() {
        String today = dayKey(System.currentTimeMillis());
        if (!today.equals(prefs.getString("learnDay", ""))) {
            prefs.edit().putString("learnDay", today).putInt("learnToday", 0).apply();
        }
        prefs.edit().putInt("learnToday", todayLearned() + 1).apply();
    }

    /**
     * 距离考试的天数。
     *
     * @return 剩余天数；未设置考试日期返回 -1
     */
    public int daysToExam() {
        long exam = examDate();
        if (exam <= 0) {
            return -1;
        }
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(exam);
        a.set(Calendar.HOUR_OF_DAY, 0);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);
        return (int) ((b.getTimeInMillis() - a.getTimeInMillis()) / (24 * 60 * 60 * 1000));
    }

    /**
     * 生成日期键。
     *
     * @param millis 时间戳
     * @return yyyy-MM-dd
     */
    private static String dayKey(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(millis));
    }

    /**
     * 统计指定前缀的键数量。
     *
     * @param prefix 键前缀
     * @return 数量
     */
    private int countPrefixed(String prefix) {
        int n = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 备份

    /**
     * 导出全部学习数据为 JSON 文本。
     *
     * @return JSON 文本
     */
    public String exportJson() {
        JSONObject root = new JSONObject();
        try {
            JSONObject settings = new JSONObject();
            settings.put("theme", theme());
            settings.put("accent", accent());
            settings.put("daily", daily());
            settings.put("startCefr", startCefr());
            settings.put("examDate", examDate());
            settings.put("illus", illustration());
            root.put("settings", settings);

            JSONObject progress = new JSONObject();
            JSONArray favorites = new JSONArray();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                String key = e.getKey();
                if (key.startsWith("p:")) {
                    progress.put(key.substring(2), e.getValue());
                } else if (key.equals("fav")) {
                    for (String id : favorites()) {
                        favorites.put(id);
                    }
                }
            }
            root.put("progress", progress);
            root.put("favorites", favorites);
            // 打卡历史一并备份，恢复后日历不会缺记录
            JSONArray checkins = new JSONArray();
            for (String day : checkins()) {
                checkins.put(day);
            }
            root.put("checkins", checkins);
            root.put("exportedAt", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    /**
     * 从 JSON 文本恢复学习数据。
     *
     * @param text 备份文本
     * @return 是否成功
     */
    public boolean importJson(String text) {
        try {
            JSONObject root = new JSONObject(text);
            SharedPreferences.Editor edit = prefs.edit();
            JSONObject settings = root.optJSONObject("settings");
            if (settings != null) {
                edit.putString("theme", settings.optString("theme", "dark"));
                edit.putString("accent", settings.optString("accent", "uk"));
                edit.putInt("daily", settings.optInt("daily", 10));
                edit.putInt("startCefr", settings.optInt("startCefr", 0));
                edit.putLong("examDate", settings.optLong("examDate", 0));
                edit.putString("illus", settings.optString("illus", "full"));
            }
            JSONObject progress = root.optJSONObject("progress");
            if (progress != null) {
                for (java.util.Iterator<String> it = progress.keys(); it.hasNext(); ) {
                    String id = it.next();
                    edit.putString("p:" + id, progress.optString(id));
                }
            }
            JSONArray favorites = root.optJSONArray("favorites");
            if (favorites != null) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < favorites.length(); i++) {
                    list.add(favorites.optString(i));
                }
                edit.putString("fav", String.join(",", list));
            }
            JSONArray checkins = root.optJSONArray("checkins");
            if (checkins != null) {
                List<String> days = new ArrayList<>();
                for (int i = 0; i < checkins.length(); i++) {
                    String day = checkins.optString(i);
                    if (!day.isEmpty()) {
                        days.add(day);
                    }
                }
                edit.putString("checkins", String.join(",", days));
            }
            edit.apply();
            reloadCache();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清空全部学习数据，保留设置。
     */
    public void resetProgress() {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("p:")) {
                edit.remove(key);
            }
        }
        edit.remove("fav");
        edit.remove("streak");
        edit.remove("lastDay");
        edit.remove("checkins");
        edit.remove("learnToday");
        edit.remove("learnDay");
        edit.apply();
        cache.clear();
    }

    /**
     * 从持久化数据重新加载进度缓存，导入备份后调用。
     */
    private void reloadCache() {
        cache.clear();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("p:") && entry.getValue() instanceof String) {
                Progress p = parseProgress((String) entry.getValue());
                if (p != null) {
                    cache.put(key.substring(2), p);
                }
            }
        }
    }
}
