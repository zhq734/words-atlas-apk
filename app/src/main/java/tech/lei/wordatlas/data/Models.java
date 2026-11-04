package tech.lei.wordatlas.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 词光的数据模型集合。
 *
 * <p>所有模型都提供 {@code parse} 静态方法，把站点发布的 JSON 结构映射为 Java 对象，
 * 字段命名与站点保持一一对应，便于后续数据更新时零改动。</p>
 *
 * @author zhenghq
 */
public final class Models {

    private Models() {
    }

    /** 词条索引项，对应 atlas/index.json 中 words 数组的一行。 */
    public static final class WordIndex {
        /** 单词 id，同时是媒体文件名。 */
        public final String id;
        /** 展示用单词。 */
        public final String word;
        /** 首条中文释义。 */
        public final String zh;
        /** 音标。 */
        public final String ipa;
        /** 难度下标，对应 cefr 数组。 */
        public final int cefr;
        /** 话题下标，对应 topics 数组。 */
        public final int topic;
        /** 索引顺序，用于定位分片。 */
        public final int order;

        /**
         * @param id 单词 id
         * @param word 展示单词
         * @param zh 中文释义
         * @param ipa 音标
         * @param cefr 难度下标
         * @param topic 话题下标
         * @param order 索引顺序
         */
        public WordIndex(String id, String word, String zh, String ipa, int cefr, int topic, int order) {
            this.id = id;
            this.word = word;
            this.zh = zh;
            this.ipa = ipa;
            this.cefr = cefr;
            this.topic = topic;
            this.order = order;
        }
    }

    /** 词条的一个义项。 */
    public static final class Sense {
        /** 词性，例如 n. / v. / adj.。 */
        public final String pos;
        /** 中文释义。 */
        public final String zh;
        /** 英文释义。 */
        public final String en;

        /**
         * @param pos 词性
         * @param zh 中文释义
         * @param en 英文释义
         */
        public Sense(String pos, String zh, String en) {
            this.pos = pos;
            this.zh = zh;
            this.en = en;
        }
    }

    /** 双语例句。 */
    public static final class Example {
        /** 英文例句。 */
        public final String en;
        /** 中文翻译。 */
        public final String zh;

        /**
         * @param en 英文例句
         * @param zh 中文翻译
         */
        public Example(String en, String zh) {
            this.en = en;
            this.zh = zh;
        }
    }

    /** 完整词条，对应 atlas/entries/N.json 中的一项。 */
    public static final class WordEntry {
        public String id = "";
        public String w = "";
        public String uk = "";
        public String us = "";
        public String cefr = "";
        public String topic = "";
        public String tip = "";
        public final List<Sense> senses = new ArrayList<>();
        public final List<Example> examples = new ArrayList<>();
        public final List<String> collocations = new ArrayList<>();
        public final List<String> family = new ArrayList<>();
        public final List<String> synonyms = new ArrayList<>();
        public final List<String> antonyms = new ArrayList<>();

        /**
         * 解析单个词条。
         *
         * @param o 词条 JSON 对象
         * @return 词条实例；解析异常时返回空对象
         */
        public static WordEntry parse(JSONObject o) {
            WordEntry e = new WordEntry();
            e.id = o.optString("id");
            e.w = o.optString("w");
            e.uk = o.optString("uk");
            e.us = o.optString("us");
            e.cefr = o.optString("cefr");
            e.topic = o.optString("topic");
            e.tip = o.optString("tip");
            JSONArray senses = o.optJSONArray("senses");
            if (senses != null) {
                for (int i = 0; i < senses.length(); i++) {
                    JSONObject s = senses.optJSONObject(i);
                    if (s != null) {
                        e.senses.add(new Sense(s.optString("pos"), s.optString("zh"), s.optString("en")));
                    }
                }
            }
            JSONArray ex = o.optJSONArray("ex");
            if (ex != null) {
                for (int i = 0; i < ex.length(); i++) {
                    JSONObject s = ex.optJSONObject(i);
                    if (s != null) {
                        e.examples.add(new Example(s.optString("en"), s.optString("zh")));
                    }
                }
            }
            fill(o.optJSONArray("col"), e.collocations);
            fill(o.optJSONArray("fam"), e.family);
            fill(o.optJSONArray("syn"), e.synonyms);
            fill(o.optJSONArray("ant"), e.antonyms);
            return e;
        }

        /**
         * 把字符串数组填入列表。
         *
         * @param arr 源数组，可为 null
         * @param out 目标列表
         */
        private static void fill(JSONArray arr, List<String> out) {
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i);
                if (s != null && !s.isEmpty()) {
                    out.add(s);
                }
            }
        }
    }

    /** 同义替换题目。 */
    public static final class Paraphrase {
        /** 目标词。 */
        public String w = "";
        /** 原句。 */
        public String src = "";
        /** 原句中被替换的词。 */
        public String key = "";
        /** 改写句。 */
        public String para = "";
        /** 正确答案。 */
        public String ans = "";
        /** 干扰项。 */
        public final List<String> opts = new ArrayList<>();
        /** 解析。 */
        public String note = "";
        /** 难度下标。 */
        public int cefr;

        /**
         * 解析一道题。
         *
         * @param o 题目 JSON 对象
         * @return 题目实例
         */
        public static Paraphrase parse(JSONObject o) {
            Paraphrase p = new Paraphrase();
            p.w = o.optString("w");
            p.src = o.optString("src");
            p.key = o.optString("key");
            p.para = o.optString("para");
            p.ans = o.optString("ans");
            p.note = o.optString("note");
            p.cefr = o.optInt("cefr");
            JSONArray arr = o.optJSONArray("opts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    p.opts.add(arr.optString(i));
                }
            }
            return p;
        }
    }

    /** 易混词组中的一个词。 */
    public static final class ConfusableWord {
        public String w = "";
        public String pos = "";
        public String zh = "";
        public String id = "";
    }

    /** 易混词组的小测题。 */
    public static final class ConfusableQuiz {
        public String en = "";
        public String ans = "";
        public String zh = "";
    }

    /** 易混词组。 */
    public static final class ConfusableGroup {
        public String id = "";
        public String core = "";
        public String hook = "";
        public int cefr;
        public final List<ConfusableWord> words = new ArrayList<>();
        public final List<String> points = new ArrayList<>();
        public final List<Example> examples = new ArrayList<>();
        public final List<ConfusableQuiz> quiz = new ArrayList<>();

        /**
         * 解析一组易混词。
         *
         * @param o 组 JSON 对象
         * @return 组实例
         */
        public static ConfusableGroup parse(JSONObject o) {
            ConfusableGroup g = new ConfusableGroup();
            g.id = o.optString("id");
            g.core = o.optString("core");
            g.hook = o.optString("hook");
            g.cefr = o.optInt("cefr");
            JSONArray ws = o.optJSONArray("words");
            if (ws != null) {
                for (int i = 0; i < ws.length(); i++) {
                    JSONObject w = ws.optJSONObject(i);
                    if (w == null) {
                        continue;
                    }
                    ConfusableWord cw = new ConfusableWord();
                    cw.w = w.optString("w");
                    cw.pos = w.optString("pos");
                    cw.zh = w.optString("zh");
                    cw.id = w.optString("id", cw.w);
                    g.words.add(cw);
                }
            }
            JSONArray pts = o.optJSONArray("points");
            if (pts != null) {
                for (int i = 0; i < pts.length(); i++) {
                    g.points.add(pts.optString(i));
                }
            }
            JSONArray ex = o.optJSONArray("ex");
            if (ex != null) {
                for (int i = 0; i < ex.length(); i++) {
                    JSONObject e = ex.optJSONObject(i);
                    if (e != null) {
                        g.examples.add(new Example(e.optString("en"), e.optString("zh")));
                    }
                }
            }
            JSONArray q = o.optJSONArray("quiz");
            if (q != null) {
                for (int i = 0; i < q.length(); i++) {
                    JSONObject x = q.optJSONObject(i);
                    if (x == null) {
                        continue;
                    }
                    ConfusableQuiz cq = new ConfusableQuiz();
                    cq.en = x.optString("en");
                    cq.ans = x.optString("ans");
                    cq.zh = x.optString("zh");
                    g.quiz.add(cq);
                }
            }
            return g;
        }
    }

    /** 阅读短文索引项。 */
    public static final class ReadingIndex {
        public String id = "";
        public String title = "";
        public String titleZh = "";
        public String topic = "";
        public int cefr;
        public final List<String> wordIds = new ArrayList<>();
    }

    /** 阅读短文正文（含题目）。 */
    public static final class Reading {
        public String id = "";
        public String title = "";
        public String titleZh = "";
        public int cefr;
        public final List<String> paras = new ArrayList<>();
        public final List<String> zh = new ArrayList<>();
        public final List<ReadingQuestion> questions = new ArrayList<>();
    }

    /** 阅读判断题。 */
    public static final class ReadingQuestion {
        public String s = "";
        /** T / F / NG。 */
        public String a = "T";
        public String why = "";
    }

    /** 书目信息。 */
    public static final class BookInfo {
        public String id = "";
        public String title = "";
        public String titleZh = "";
        public String author = "";
        public String authorZh = "";
        public int year;
        public String level = "";
        public String accent = "uk";
        public int chapterCount;
        public String cover = "";
        public String coverSmall = "";
        public String firstLine = "";
        public String firstLineZh = "";
        public String blurbZh = "";
        public String tone = "#f9ebd2";
        public String keyLine = "";
        public final List<ChapterInfo> chapters = new ArrayList<>();
    }

    /** 章节概要。 */
    public static final class ChapterInfo {
        public int n;
        public String label = "";
        public String labelZh = "";
        public String titleZh = "";
        public boolean ready;
        public int words;
        public int minutes;
        public boolean audio;
        public String plate = "";
    }

    /** 章节正文。 */
    public static final class Chapter {
        public String book = "";
        public int n;
        public String titleZh = "";
        public final List<Paragraph> paras = new ArrayList<>();
    }

    /** 章节段落。 */
    public static final class Paragraph {
        public String id = "";
        public String kind = "p";
        public String en = "";
        public String zh = "";
        public String audio = "";
        public String audioZh = "";
    }

    /** 场景插画。 */
    public static final class Scene {
        public int n;
        public String pid = "";
        public String img = "";
        public String small = "";
        public String captionZh = "";
        public String tone = "#f9ebd2";
    }
}
