package com.xiaoman.standalone;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆引擎（照 memory.mjs 的分层设计搬到 Java）
 *
 * 五条轨，各自一个文本文件，存在 App 私有目录：
 *   key.md        关键记忆——永远带上
 *   user.md       用户档案——他是谁、在意什么
 *   memory.md     长期记忆——发生过的事
 *   project.md    项目日志
 *   daily/日期.md  每日日志
 *
 * 条目格式（§ 分隔）：
 *   ## 标题
 *   § 2026-11-16 18:20 | 0.90 | 标签
 *   正文
 *   ---
 *
 * 检索：中文两字滑窗 + 权重 + 30 天半衰
 */
public class Memory {

    public static final String KEY = "key";
    public static final String USER = "user";
    public static final String MEMORY = "memory";
    public static final String PROJECT = "project";
    public static final String DAILY = "daily";

    /** 一条记忆 */
    public static class Entry {
        public String title = "";
        public String body = "";
        public String at = "";
        public double w = 0.7;
        public List<String> tags = new ArrayList<>();

        public String text() {
            return title + (body.isEmpty() ? "" : "——" + body);
        }
    }

    private final Context ctx;
    private final File dir;
    private final File dailyDir;

    public Memory(Context c) {
        ctx = c.getApplicationContext();
        dir = new File(ctx.getFilesDir(), "memory");
        dailyDir = new File(dir, "daily");
        if (!dir.exists()) dir.mkdirs();
        if (!dailyDir.exists()) dailyDir.mkdirs();
        seedIfEmpty();
    }

    // ---------- 首次启动：把人设和初始记忆写进去 ----------
    private void seedIfEmpty() {
        File keyFile = new File(dir, "key.md");
        if (keyFile.exists() && keyFile.length() > 0) return;

        // 优先用 assets/seed/ 里预置的五轨记忆（从电脑搬过来的那 215 条）
        boolean any = false;
        for (String t : new String[]{KEY, USER, MEMORY, PROJECT}) {
            File dst = new File(dir, t + ".md");
            if (copyAssetIfExists("seed/" + t + ".md", dst)) any = true;
        }
        // 原话也一起搬（如果带了）
        copyAssetIfExists("seed/chatblocks.json", new File(dir, "chatblocks.json"));

        // 没预置的话，退回老的人格目录
        if (!any) {
            copyAssetIfExists("persona/memory_key.md", keyFile);
            copyAssetIfExists("persona/memory_user.md", new File(dir, "user.md"));
        }
    }

    /** 复制 assets 里某个文件到 dst，成功返回 true */
    private boolean copyAssetIfExists(String asset, File dst) {
        if (dst.exists() && dst.length() > 0) return false;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = ctx.getAssets().open(asset);
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    // ---------- 文件路径 ----------
    private File trackFile(String track) {
        if (DAILY.equals(track)) return new File(dailyDir, today() + ".md");
        return new File(dir, track + ".md");
    }

    public static String today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format("%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    static String nowStamp() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return today() + String.format(" %02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }

    // ---------- 解析 ----------
    public static List<Entry> parse(String text) {
        List<Entry> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String[] lines = text.split("\n", -1);
        Entry cur = null;
        StringBuilder body = new StringBuilder();
        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("## ")) {
                if (cur != null) { cur.body = body.toString().trim(); out.add(cur); }
                cur = new Entry();
                cur.title = line.substring(3).trim();
                body.setLength(0);
            } else if (cur != null && line.startsWith("§ ")) {
                String meta = line.substring(2).trim();
                String[] parts = meta.split("\\|");
                if (parts.length > 0) cur.at = parts[0].trim();
                if (parts.length > 1) {
                    try { cur.w = Double.parseDouble(parts[1].trim()); } catch (Exception e) { cur.w = 0.7; }
                }
                if (parts.length > 2) {
                    for (String t : parts[2].split(",")) {
                        String x = t.trim();
                        if (!x.isEmpty()) cur.tags.add(x);
                    }
                }
            } else if (cur != null && line.trim().equals("---")) {
                cur.body = body.toString().trim();
                out.add(cur);
                cur = null;
                body.setLength(0);
            } else if (cur != null) {
                body.append(line).append('\n');
            }
        }
        if (cur != null) { cur.body = body.toString().trim(); out.add(cur); }
        return out;
    }

    /** 往某一轨追加一条（用户档案 / 长期记忆 / 关键记忆） */
    public synchronized boolean append(String track, String title, String body, double weight) {
        try {
            File f = new File(dir, track + ".md");
            Entry e = new Entry();
            e.title = title == null ? "" : title.trim();
            if (e.title.isEmpty()) return false;
            e.body = body == null ? "" : body.trim();
            e.at = nowStamp();
            e.w = weight;
            e.tags.add(track);
            String piece = format(e);

            StringBuilder sb = new StringBuilder();
            if (f.exists()) {
                sb.append(readText(f));
                String cur = sb.toString();
                if (!cur.isEmpty() && !cur.endsWith("\n")) sb.append('\n');
            }
            sb.append(piece);

            FileOutputStream out = new FileOutputStream(f, false);
            out.write(sb.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 便捷：往用户档案追加一条偏好 */
    public boolean addUser(String text) {
        return append(USER, text.length() > 40 ? text.substring(0, 40) : text, text, 0.7);
    }

    private static String readText(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            in.close();
            return new String(buf, 0, off, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static String format(Entry e) {        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(e.title.replace("\n", " ")).append('\n');
        sb.append("§ ").append(e.at.isEmpty() ? nowStamp() : e.at)
                .append(" | ").append(String.format("%.2f", e.w)).append(" | ")
                .append(joinTags(e.tags)).append('\n');
        sb.append(e.body.trim()).append('\n');
        sb.append("---\n");
        return sb.toString();
    }

    private static String joinTags(List<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    // ---------- 读 ----------
    public static String readFile(File f) {
        if (f == null || !f.exists()) return "";
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    public List<Entry> list(String track) {
        return parse(readFile(trackFile(track)));
    }

    // ---------- 写 ----------
    public synchronized boolean add(String track, String title, String body, double w, List<String> tags) {
        if (title == null || title.trim().isEmpty()) return false;
        Entry e = new Entry();
        e.title = title.trim();
        e.body = body == null ? "" : body.trim();
        e.w = w <= 0 ? 0.7 : Math.min(1.0, w);
        e.at = nowStamp();
        if (tags != null) e.tags.addAll(tags);

        File f = trackFile(track);
        String old = readFile(f);
        String head = old.isEmpty() ? ("# " + trackName(track) + "\n\n") : old;
        if (!head.endsWith("\n")) head += "\n";
        return writeFile(f, head + format(e));
    }

    private static boolean writeFile(File f, String text) {
        FileOutputStream out = null;
        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            out = new FileOutputStream(f);
            out.write(text.getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    private static String trackName(String track) {
        if (KEY.equals(track)) return "关键记忆";
        if (USER.equals(track)) return "用户档案";
        if (MEMORY.equals(track)) return "长期记忆";
        if (PROJECT.equals(track)) return "项目日志";
        return track;
    }

    // ---------- 检索 ----------
    private static Set<String> tokenize(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String str = s.toLowerCase();
        for (String w : str.split("[\\s，。、？！,.?!：:；;“”\"'（）()【】\\[\\]]+")) {
            if (w.isEmpty()) continue;
            if (w.matches("^[a-z0-9]+$")) {
                if (w.length() >= 2) out.add(w);
                continue;
            }
            if (w.length() == 1) { out.add(w); continue; }
            for (int i = 0; i + 2 <= w.length(); i++) out.add(w.substring(i, i + 2));
        }
        return out;
    }

    private static double score(Set<String> q, String text) {
        Set<String> t = tokenize(text);
        double s = 0;
        for (String x : q) {
            if (t.contains(x)) s += x.length() >= 2 ? 1.0 : 0.3;
        }
        return s;
    }

    /** 艾宾浩斯式衰减：30 天半衰，权重不低于 30% */
    private static double decay(Entry e) {
        double base = e.w > 0 ? e.w : 0.7;
        long t = parseTime(e.at);
        if (t <= 0) return base;
        double days = (System.currentTimeMillis() - t) / 86400000.0;
        if (days < 0) days = 0;
        double factor = Math.pow(0.5, days / 30.0);
        return base * (0.3 + 0.7 * factor);
    }

    private static long parseTime(String at) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            f.setTimeZone(java.util.TimeZone.getDefault());
            return f.parse(at).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 检索结果 */
    public static class Result {
        public List<Entry> key = new ArrayList<>();
        public List<Entry> picked = new ArrayList<>();
        public String line = "";
    }

    public Result recall(String query, String prev, int limit) {
        Result r = new Result();
        r.key = list(KEY);           // key 轨永远全带

        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            r.line = buildLine(r.key, r.picked);
            return r;
        }

        Set<String> qTok = tokenize(q);
        Set<String> exTok = (q.length() < 12 && prev != null && !prev.isEmpty())
                ? tokenize(prev) : new HashSet<>();

        List<Object[]> pool = new ArrayList<>();
        for (Entry e : list(USER)) pool.add(new Object[]{e, "user"});
        for (Entry e : list(MEMORY)) pool.add(new Object[]{e, "memory"});
        for (Entry e : list(PROJECT)) pool.add(new Object[]{e, "project"});
        for (Entry e : list(DAILY)) pool.add(new Object[]{e, "today"});

        List<Object[]> scored = new ArrayList<>();
        for (Object[] o : pool) {
            Entry e = (Entry) o[0];
            String from = (String) o[1];
            String text = e.title + " " + e.body;
            double s = score(qTok, text);
            if (!exTok.isEmpty()) s = Math.max(s, score(exTok, text) * 0.6);
            if (s <= 0) continue;
            s *= decay(e);
            if ("user".equals(from)) s *= 1.15;   // 关于他的事优先
            scored.add(new Object[]{e, s});
        }

        Collections.sort(scored, new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) {
                return Double.compare((Double) b[1], (Double) a[1]);
            }
        });

        int n = Math.min(limit, scored.size());
        for (int i = 0; i < n; i++) r.picked.add((Entry) scored.get(i)[0]);

        r.line = buildLine(r.key, r.picked);
        return r;
    }

    private static String buildLine(List<Entry> keys, List<Entry> picked) {
        StringBuilder sb = new StringBuilder();
        if (!keys.isEmpty()) {
            sb.append("（这些是你一直记着、不能忘的：");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append('；');
                sb.append(keys.get(i).text());
            }
            sb.append("）\n");
        }
        if (!picked.isEmpty()) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < picked.size(); i++) {
                if (i > 0) body.append('；');
                body.append(picked.get(i).text());
            }
            String s = body.toString();
            if (s.length() > 400) s = s.substring(0, 400) + "…";
            sb.append("（你记得这些事，聊到相关的就自然用上，别把老事当新事说，也别说「根据记忆」：")
                    .append(s).append("）\n");
        }
        return sb.toString();
    }

    // ---------- 统计 ----------
    public int count(String track) {
        return list(track).size();
    }

    public JSONObject stats() {
        JSONObject o = new JSONObject();
        try {
            o.put("key", count(KEY));
            o.put("user", count(USER));
            o.put("memory", count(MEMORY));
            o.put("project", count(PROJECT));
            o.put("daily", count(DAILY));
            o.put("total", count(KEY) + count(USER) + count(MEMORY) + count(PROJECT));
        } catch (Exception ignored) {}
        return o;
    }

    /** 给界面用：列出某条轨的条目 */
    public JSONArray listJson(String track) {
        JSONArray arr = new JSONArray();
        for (Entry e : list(track)) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", e.title);
                o.put("body", e.body);
                o.put("at", e.at);
                o.put("w", e.w);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr;
    }
}
