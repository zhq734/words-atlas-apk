package tech.lei.wordatlas.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Edge TTS 朗读器：调用微软 Edge「大声朗读」接口合成英文语音。
 *
 * <p>官网的例句没有预录音频（站点用浏览器的 speechSynthesis 朗读），
 * 官方又没有可直接下载的句子音频，因此在官网资源缺失时用 Edge TTS 现场合成 mp3，
 * 合成结果按文本缓存在私有目录，重复朗读不再联网。</p>
 *
 * <p>协议与开源项目 edge-tts 保持一致：wss 握手 + speech.config + ssml，
 * 鉴权用的 Sec-MS-GEC 令牌为 v2 摘要格式。</p>
 *
 * @author zhenghq
 */
public final class EdgeTts {

    /** 微软语音合成固定客户端令牌。 */
    private static final String TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    /** 协议版本号，微软更新客户端后需要同步调整。 */
    private static final String GEC_VERSION = "2-143.0.3650.75";
    private static final String HOST = "speech.platform.bing.com";
    private static final String PATH = "/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final long WIN_EPOCH = 11644473600L;

    private static EdgeTts instance;

    private final File cacheDir;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 合成回调，均在主线程触发。 */
    public interface Callback {
        /**
         * 合成完成。
         *
         * @param file 本地 mp3 文件
         */
        void onAudio(File file);

        /** 合成失败。 */
        void onError();
    }

    /**
     * 取得单例。
     *
     * @param context 任意上下文
     * @return Edge TTS 合成器
     */
    public static synchronized EdgeTts get(Context context) {
        if (instance == null) {
            instance = new EdgeTts(context.getApplicationContext());
        }
        return instance;
    }

    private EdgeTts(Context context) {
        // 缓存目录带版本号：旧版生成的音频音色与帧切分都有问题，升级后不能复用
        cacheDir = new File(context.getCacheDir(), "tts2");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        File legacy = new File(context.getCacheDir(), "tts");
        if (legacy.isDirectory()) {
            deleteTree(legacy);
        }
        trimCache();
    }

    /**
     * 递归删除目录，用于清理旧版本遗留的语音缓存。
     *
     * @param target 目标文件或目录
     */
    private static void deleteTree(File target) {
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        target.delete();
    }

    /**
     * 控制合成缓存的规模，文件过多时删掉较旧的一半。
     *
     * <p>单条语音约 20KB，保留 200 条足够覆盖常读的句子，也不会无限增长。</p>
     */
    private void trimCache() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (files == null || files.length <= 200) {
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - 100; i++) {
            files[i].delete();
        }
    }

    /**
     * 合成一段英文语音，命中缓存时直接回调。
     *
     * @param text 要朗读的文本
     * @param accent 口音：us 或 uk
     * @param callback 结果回调
     */
    public void synthesize(String text, String accent, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            main.post(callback::onError);
            return;
        }
        final String key = "us".equals(accent) ? "us" : "uk";
        final String source = text.trim();
        // 音色参与缓存键：换音色后旧音频自然失效，不会继续读出上一次的嗓音
        final File cached = new File(cacheDir, md5(voiceOf(key) + "|" + source) + ".mp3");
        if (cached.isFile() && cached.length() > 0) {
            main.post(() -> callback.onAudio(cached));
            return;
        }
        pool.execute(() -> {
            File tmp = null;
            try {
                // 临时文件唯一，避免同一句话并发合成时互相覆盖写成损坏的 mp3
                tmp = File.createTempFile("tts", ".part", cacheDir);
                fetch(source, key, tmp);
                if (tmp.length() == 0) {
                    throw new IOException("empty audio");
                }
                if (cached.exists() && !cached.delete()) {
                    throw new IOException("cache busy");
                }
                if (!tmp.renameTo(cached)) {
                    throw new IOException("rename failed");
                }
                main.post(() -> callback.onAudio(cached));
            } catch (Exception e) {
                if (tmp != null) {
                    tmp.delete();
                }
                main.post(callback::onError);
            }
        });
    }

    /**
     * 建立 WebSocket 连接并合成音频。
     *
     * @param text 文本
     * @param accent 口音
     * @param out 输出文件
     * @throws Exception 连接、协议或写入失败
     */
    private void fetch(String text, String accent, File out) throws Exception {
        String query = PATH + "?TrustedClientToken=" + TRUSTED_TOKEN
                + "&ConnectionId=" + randomHex(16)
                + "&Sec-MS-GEC=" + generateGec()
                + "&Sec-MS-GEC-Version=" + GEC_VERSION;

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        try {
            socket.connect(new InetSocketAddress(HOST, 443), 15000);
            socket.setSoTimeout(45000);
            SSLParameters params = socket.getSSLParameters();
            params.setServerNames(java.util.Collections.singletonList(new SNIHostName(HOST)));
            socket.setSSLParameters(params);
            socket.startHandshake();

            InputStream in = socket.getInputStream();
            OutputStream raw = socket.getOutputStream();

            String key = Base64.encodeToString(randomBytes(16), Base64.NO_WRAP);
            String request = "GET " + query + " HTTP/1.1\r\n"
                    + "Host: " + HOST + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Origin: " + ORIGIN + "\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Cookie: muid=" + randomHex(16).toUpperCase(Locale.ROOT) + ";\r\n"
                    + "Accept-Language: en-US,en;q=0.9\r\n\r\n";
            raw.write(request.getBytes(StandardCharsets.UTF_8));
            raw.flush();

            String head = readHttpHead(in);
            if (!head.startsWith("HTTP/1.1 101")) {
                throw new IOException("ws handshake failed: " + firstLine(head));
            }

            sendFrame(raw, 1, configJson().getBytes(StandardCharsets.UTF_8));
            sendFrame(raw, 1, ssml(text, accent).getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            byte[] pending = new byte[0];
            int fragmentOpcode = -1;
            long deadline = System.currentTimeMillis() + 60000;
            boolean finished = false;
            while (!finished && System.currentTimeMillis() < deadline) {
                Frame frame = readFrame(in);
                if (frame == null) {
                    break;
                }
                if (frame.opcode == 9) {
                    sendFrame(raw, 10, frame.payload);
                    continue;
                }
                if (frame.opcode == 8) {
                    break;
                }
                byte[] message;
                if (frame.opcode == 0) {
                    if (pending.length == 0) {
                        continue;
                    }
                    pending = concat(pending, frame.payload);
                    if (!frame.fin) {
                        continue;
                    }
                    message = pending;
                    pending = new byte[0];
                } else if (!frame.fin) {
                    fragmentOpcode = frame.opcode;
                    pending = frame.payload;
                    continue;
                } else {
                    message = frame.payload;
                }
                int opcode = frame.opcode == 0 ? fragmentOpcode : frame.opcode;
                if (opcode == 1) {
                    String msg = new String(message, StandardCharsets.UTF_8);
                    int split = msg.indexOf("\r\n\r\n");
                    String header = split >= 0 ? msg.substring(0, split) : msg;
                    if (header.contains("Path:turn.end")) {
                        finished = true;
                    }
                } else if (opcode == 2) {
                    if (message.length < 2) {
                        continue;
                    }
                    int headerLength = ((message[0] & 0xFF) << 8) | (message[1] & 0xFF);
                    // 二进制帧头只占前 2 + headerLength 字节，音频紧随其后
                    int start = 2 + headerLength;
                    if (start >= message.length) {
                        continue;
                    }
                    String header = new String(message, 2, headerLength, StandardCharsets.UTF_8);
                    if (isAudioHeader(header)) {
                        audio.write(message, start, message.length - start);
                    }
                }
            }

            if (audio.size() == 0) {
                throw new IOException("no audio received");
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                audio.writeTo(fos);
            }
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 读取 HTTP 响应头。
     *
     * @param in 输入流
     * @return 响应头文本
     * @throws IOException 读取失败
     */
    private static String readHttpHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (head.size() < 16384) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.write(b);
            if ((state == 0 || state == 2) && b == '\r') {
                state++;
            } else if ((state == 1 || state == 3) && b == '\n') {
                state++;
                if (state == 4) {
                    break;
                }
            } else {
                state = 0;
            }
        }
        return head.toString("UTF-8");
    }

    /**
     * 取文本第一行。
     *
     * @param text 原文
     * @return 第一行
     */
    private static String firstLine(String text) {
        int idx = text.indexOf("\r\n");
        return idx >= 0 ? text.substring(0, idx) : text;
    }

    /**
     * 发送一个 WebSocket 帧（客户端帧需要掩码）。
     *
     * @param out 输出流
     * @param opcode 操作码
     * @param payload 载荷
     * @throws IOException 写入失败
     */
    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        int length = payload.length;
        if (length < 126) {
            frame.write(0x80 | length);
        } else if (length <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write((length >>> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) (((long) length) >>> (i * 8)) & 0xFF);
            }
        }
        byte[] mask = randomBytes(4);
        frame.write(mask, 0, 4);
        byte[] data = payload.clone();
        for (int i = 0; i < data.length; i++) {
            data[i] ^= mask[i % 4];
        }
        out.write(frame.toByteArray());
        out.write(data);
        out.flush();
    }

    /**
     * 读取一个 WebSocket 帧。
     *
     * @param in 输入流
     * @return 帧；连接关闭返回 null
     * @throws IOException 读取失败
     */
    private static Frame readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) {
            return null;
        }
        int b1 = in.read();
        if (b1 < 0) {
            return null;
        }
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(in);
            }
        }
        if (length > 8 * 1024 * 1024) {
            throw new IOException("frame too large");
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            readFully(in, mask);
        }
        byte[] payload = new byte[(int) length];
        readFully(in, payload);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
        }
        return new Frame(fin, opcode, payload);
    }

    /**
     * 读取一个字节。
     *
     * @param in 输入流
     * @return 字节值
     * @throws IOException 读取失败
     */
    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new IOException("unexpected end of stream");
        }
        return b;
    }

    /**
     * 读满整个缓冲区。
     *
     * @param in 输入流
     * @param buffer 目标缓冲区
     * @throws IOException 读取失败
     */
    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int n = in.read(buffer, offset, buffer.length - offset);
            if (n < 0) {
                throw new IOException("unexpected end of stream");
            }
            offset += n;
        }
    }

    /**
     * 判断二进制帧头是否描述音频数据，与 edge-tts 的判定保持一致。
     *
     * @param header 帧头文本
     * @return 是音频帧返回 true
     */
    private static boolean isAudioHeader(String header) {
        boolean audio = false;
        boolean mpeg = true;
        for (String line : header.split("\r\n")) {
            if (line.startsWith("Path:")) {
                audio = "audio".equals(line.substring(5).trim());
            } else if (line.startsWith("Content-Type:")) {
                String type = line.substring(13).trim();
                mpeg = type.isEmpty() || "audio/mpeg".equals(type);
            }
        }
        return audio && mpeg;
    }

    /**
     * 拼接两个字节数组。
     *
     * @param a 前段
     * @param b 后段
     * @return 拼接结果
     */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * 生成 speech.config 消息。
     *
     * @return JSON 文本
     */
    private static String configJson() {
        return "X-Timestamp:" + dateString() + "Z\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
                + "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
    }

    /**
     * 取得口音对应的 Edge 神经网络音色。
     *
     * <p>与参考项目（translation）保持一致：美音用 Jenny，英音用 Sonia。</p>
     *
     * @param accent 口音
     * @return 简短音色名
     */
    private static String voiceOf(String accent) {
        return "us".equals(accent) ? "en-US-JennyNeural" : "en-GB-SoniaNeural";
    }

    /**
     * 把简短音色名转成浏览器实际发送的完整 SSML 音色名称。
     *
     * <p>例如 en-US-JennyNeural 会被展开成
     * {@code Microsoft Server Speech Text to Speech Voice (en-US, JennyNeural)}。</p>
     *
     * @param voice 简短音色名
     * @return 完整音色名；无法识别时原样返回
     */
    private static String ssmlVoiceName(String voice) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^([a-z]{2,})-([A-Z]{2,})-(.+Neural)$").matcher(voice);
        if (!matcher.matches()) {
            return voice;
        }
        return "Microsoft Server Speech Text to Speech Voice ("
                + matcher.group(1) + "-" + matcher.group(2) + ", " + matcher.group(3) + ")";
    }

    /**
     * 生成 SSML 合成请求。
     *
     * @param text 文本
     * @param accent 口音
     * @return SSML 消息
     */
    private static String ssml(String text, String accent) {
        boolean us = "us".equals(accent);
        String voice = ssmlVoiceName(voiceOf(accent));
        String lang = us ? "en-US" : "en-GB";
        String escaped = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
        return "X-RequestId:" + randomHex(16) + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + dateString() + "Z\r\n"
                + "Path:ssml\r\n\r\n"
                + "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\""
                + lang + "\"><voice name=\"" + voice + "\">"
                + "<prosody rate=\"+0%\" volume=\"+0%\" pitch=\"+0Hz\">" + escaped
                + "</prosody></voice></speak>";
    }

    /**
     * 生成 Edge 请求使用的时间戳字符串。
     *
     * @return 形如 Tue 04 Nov 2026 12:00:00 GMT+0000 (Coordinated Universal Time)
     */
    private static String dateString() {
        SimpleDateFormat format = new SimpleDateFormat(
                "EEE dd MMM yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    /**
     * 生成随机字节。
     *
     * @param count 字节数
     * @return 随机字节
     */
    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        new Random().nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成随机十六进制串。
     *
     * @param bytes 字节数
     * @return 十六进制串
     */
    private static String randomHex(int bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes(bytes)) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 计算字符串 MD5，用作缓存文件名。
     *
     * @param text 原文
     * @return 32 位十六进制串
     */
    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    /** WebSocket 帧。 */
    private static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    // ------------------------------------------------------------------
    // Sec-MS-GEC v2：Edge 语音接口的时钟令牌，移植自开源项目 edge-tts
    // ------------------------------------------------------------------

    private static final int[] SHA256_INITIAL = {
            0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
            0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19};
    private static final int[] SHA256_STATE = {
            0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A,
            0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2};
    private static final int[] SHA256_SCHEDULE = {
            0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA,
            0xA622EE8C, 0xD1DD9491, 0xC0DD25A0, 0xF9E70AB6,
            0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33,
            0xD258B060, 0x72766564};
    private static final int[] SHA256_ROUND_CONSTANTS = {
            0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5,
            0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
            0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
            0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
            0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC,
            0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
            0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
            0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
            0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
            0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
            0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3,
            0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
            0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
            0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
            0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
            0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2};
    private static final int SHA256_ADJUSTMENT = 0xC9DC06A7;
    private static final int SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80;
    private static final int SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0;
    private static final int[] SHA256_BLOCK_CONSTANTS;

    private static final int[][] CS64_COEFFICIENTS = {
            {0xE82E91B1, 0xB32700D0},
            {0x44DE24C6, 0x88DFF51D},
            {0x3BEB65BC, 0x29514038},
            {0xC89C5933, 0xB328CBDF},
            {0x8D1EC211, 0xD405B4D5},
            {0x314EF5D5, 0x26E55F3C},
            {0xE92491B1, 0x607801C6},
            {0xF0A5B9D5, 0x82B2C1FB},
            {0x06064CF3, 0xFA51A3DA},
            {0xDA79FA60, 0x207CC464},
            {0xBED20560, 0x3C3C74F3},
            {0x89F9C9F8, 0xD2D14B61},
            {0x35FA38C6, 0xBB04A2C1},
            {0xCB49E3C6, 0xC5626167}};
    private static final int CS64_FACTOR = 0x82893933;
    private static final int CS64_ADJUSTMENT0 = 0xF2F90B1A;
    private static final int CS64_ADJUSTMENT1 = 0x0B1A0000;

    static {
        SHA256_BLOCK_CONSTANTS = new int[64];
        System.arraycopy(SHA256_ROUND_CONSTANTS, 0, SHA256_BLOCK_CONSTANTS, 0, 14);
        SHA256_BLOCK_CONSTANTS[14] = SHA256_ADJUSTMENT;
        System.arraycopy(SHA256_ROUND_CONSTANTS, 15, SHA256_BLOCK_CONSTANTS, 15, 49);
    }

    /**
     * 生成当前五分钟窗口的 Sec-MS-GEC 令牌。
     *
     * @return 形如 <16 位 hex>-<64 位 hex>
     */
    private static String generateGec() {
        long ticks = System.currentTimeMillis() / 1000L + WIN_EPOCH;
        ticks -= ticks % 300;
        long nano = ticks * 10_000_000L;
        byte[] data = (nano + TRUSTED_TOKEN).getBytes(StandardCharsets.US_ASCII);
        byte[] digest = sha256Digest(data);
        int[] cs = cs64Digest(data, digest);
        ByteBuffer csb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csb.putInt(cs[0]);
        csb.putInt(cs[1]);
        StringBuilder sb = new StringBuilder();
        for (byte b : csb.array()) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        sb.append('-');
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 左旋 16 位。
     *
     * @param value 原值
     * @return 旋转结果
     */
    private static int rol16(int value) {
        return (value << 16) | (value >>> 16);
    }

    /**
     * SHA-256 小写 sigma0。
     *
     * @param value 原值
     * @return 结果
     */
    private static int sigma0(int value) {
        return (value >>> 7) ^ (value << 25) ^ (value >>> 18) ^ (value << 14) ^ (value >>> 3);
    }

    /**
     * SHA-256 小写 sigma1。
     *
     * @param value 原值
     * @return 结果
     */
    private static int sigma1(int value) {
        return (value >>> 17) ^ (value << 15) ^ (value >>> 19) ^ (value << 13) ^ (value >>> 10);
    }

    /**
     * 扩展 SHA-256 消息字。
     *
     * @param words 消息字数组
     * @param from 起始下标
     */
    private static void expandSchedule(int[] words, int from) {
        for (int index = from; index < 64; index++) {
            int x = words[index - 2];
            int y = words[index - 15];
            words[index] = sigma1(x) + words[index - 7] + sigma0(y) + words[index - 16];
        }
    }

    /**
     * 执行 SHA-256 轮函数。
     *
     * @param state 初始状态
     * @param words 消息字
     * @param firstRound 起始轮
     * @return 结果状态
     */
    private static int[] rounds(int[] state, int[] words, int firstRound) {
        int a = state[0], b = state[1], c = state[2], d = state[3];
        int e = state[4], f = state[5], g = state[6], h = state[7];
        int[] constants = firstRound == 14 ? SHA256_BLOCK_CONSTANTS : SHA256_ROUND_CONSTANTS;
        for (int i = firstRound; i < 64; i++) {
            int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
            int ch = g ^ (e & (f ^ g));
            int t1 = h + s1 + ch + constants[i] + words[i];
            int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
            int maj = (a & b) | (c & (a | b));
            int t2 = s0 + maj;
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        return new int[]{a, b, c, d, e, f, g, h};
    }

    /**
     * 计算定制 SHA-256 的初始状态。
     *
     * @param head 数据前 7 字节
     * @return 初始状态
     */
    private static int[] sha256Init(byte[] head) {
        int[] words = new int[64];
        words[14] = ((head[0] & 0xFF) << 16) | ((head[1] & 0xFF) << 8) | (head[2] & 0xFF);
        words[15] = ((head[3] & 0xFF) << 24) | ((head[4] & 0xFF) << 16)
                | ((head[5] & 0xFF) << 8) | (head[6] & 0xFF);
        for (int index = 16; index <= 29; index++) {
            int value = SHA256_SCHEDULE[index - 16];
            if (index - 2 >= 14) {
                int s = sigma1(words[index - 2]);
                if (index == 16) {
                    s ^= SHA256_SIGMA1_ADJUSTMENT;
                }
                value += s;
            }
            if (index - 7 >= 14) {
                value += words[index - 7];
            }
            if (index - 15 >= 14) {
                int s = sigma0(words[index - 15]);
                if (index == 29) {
                    s ^= SHA256_SIGMA0_ADJUSTMENT;
                }
                value += s;
            }
            words[index] = value;
        }
        words[30] = sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14]
                + SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14];
        expandSchedule(words, 31);
        int[] working = rounds(SHA256_STATE, words, 14);
        int[] result = new int[8];
        for (int i = 0; i < 8; i++) {
            result[i] = SHA256_INITIAL[i] + working[i];
        }
        return result;
    }

    /**
     * 计算定制 SHA-256 摘要。
     *
     * @param data 原始数据
     * @return 32 字节摘要
     */
    private static byte[] sha256Digest(byte[] data) {
        int length = 57 + data.length;
        int padLength = (55 - length) % 64;
        if (padLength < 0) {
            padLength += 64;
        }
        int plain = data.length - 7;
        byte[] padded = new byte[plain + 1 + padLength + 8];
        System.arraycopy(data, 7, padded, 0, plain);
        padded[plain] = (byte) 0x80;
        long bits = (long) length * 8;
        int p = plain + 1 + padLength;
        for (int i = 0; i < 8; i++) {
            padded[p + i] = (byte) (bits >>> (56 - i * 8));
        }
        int[] state = sha256Init(java.util.Arrays.copyOfRange(data, 0, 7));
        for (int off = 0; off < padded.length; off += 64) {
            int[] words = new int[64];
            for (int i = 0; i < 16; i++) {
                int b = off + i * 4;
                words[i] = ((padded[b] & 0xFF) << 24) | ((padded[b + 1] & 0xFF) << 16)
                        | ((padded[b + 2] & 0xFF) << 8) | (padded[b + 3] & 0xFF);
            }
            expandSchedule(words, 16);
            int[] working = rounds(state, words, 0);
            for (int i = 0; i < 8; i++) {
                state[i] += working[i];
            }
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[i * 4] = (byte) (state[i] >>> 24);
            out[i * 4 + 1] = (byte) (state[i] >>> 16);
            out[i * 4 + 2] = (byte) (state[i] >>> 8);
            out[i * 4 + 3] = (byte) state[i];
        }
        return out;
    }

    /**
     * 计算 CS64 摘要。
     *
     * @param data 原始数据
     * @param digest SHA-256 摘要
     * @return 两个 32 位摘要字
     */
    private static int[] cs64Digest(byte[] data, byte[] digest) {
        ByteBuffer db = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN);
        int keyA = db.getInt() | 1;
        int keyB = db.getInt() | 1;
        int factor0 = keyA * CS64_FACTOR;
        int factor1 = keyB * CS64_FACTOR;

        byte[] d = new byte[data.length + 1];
        System.arraycopy(data, 0, d, 1, data.length);
        int wordCount = (d.length + 3) / 4;
        if (wordCount % 2 == 1) {
            wordCount++;
        }
        byte[] padded = new byte[wordCount * 4];
        System.arraycopy(d, 0, padded, 0, d.length);
        ByteBuffer bb = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN);

        int total = CS64_COEFFICIENTS.length + wordCount;
        int[] terms = new int[total];
        int[] offsets = new int[total];
        for (int i = 0; i < CS64_COEFFICIENTS.length; i++) {
            terms[i] = CS64_COEFFICIENTS[i][0] * (i % 2 == 0 ? factor0 : factor1);
            offsets[i] = CS64_COEFFICIENTS[i][1];
        }
        for (int i = 0; i < wordCount; i++) {
            int word = bb.getInt();
            int term = word * (i % 2 == 0 ? keyA : keyB);
            int offset = rol16(word) * (i % 2 == 0 ? 0xEF0569FB : 0xC31713DB);
            if (i == 0) {
                term += CS64_ADJUSTMENT0 * factor0;
                offset += CS64_ADJUSTMENT1;
            }
            terms[CS64_COEFFICIENTS.length + i] = term;
            offsets[CS64_COEFFICIENTS.length + i] = offset;
        }

        int swapAcc = 0;
        int swapLast = 0;
        int state = 0;
        int acc = 0;
        for (int i = 0; i < total; i += 2) {
            int x = terms[i];
            int y = terms[i + 1];
            int t = rol16(offsets[i] + x);
            int q = t * 0x76F50F34;
            swapAcc += rol16(q) * 0xC3EFEA97 + q;
            t = rol16(offsets[i + 1] + y);
            q = t * 0x1881CE3C;
            swapLast = rol16(q) * 0x35BD1EC9 + q;
            swapAcc = swapAcc + swapLast;

            state = state * keyA + x;
            state = rol16(state) * 0xCF98B111;
            state = rol16(state) * 0x87085B9F;
            state = rol16(state) * 0x12CEB96D;
            state = rol16(state) * 0x257E1D83;
            acc += state;
            t = state * keyB + y;
            t = rol16(t) * 0xA27416F5;
            t = rol16(t) * 0xD38396FF;
            state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69;
            acc = acc + state;
        }
        return new int[]{swapLast ^ state, swapAcc ^ acc};
    }
}
