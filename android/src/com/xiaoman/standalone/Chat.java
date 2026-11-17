package com.xiaoman.standalone;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/**
 * 对话组装 + 历史存储。
 *
 * 负责三件事：
 *   1. 从 assets 读人格，拼出 system prompt
 *   2. 从 Memory 取这一轮相关的记忆，塞进 prompt
 *   3. 把聊天历史存本地 JSON
 */
public class Chat {

    private final Context ctx;
    private final Memory memory;
    private final File historyFile;

    private JSONArray history = new JSONArray();
    private String persona = "";

    private static final int KEEP_TURNS = 20;     // 带进 prompt 的最近轮数
    private static final int MAX_HISTORY = 500;   // 本地最多存多少条

    public Chat(Context c) {
        ctx = c.getApplicationContext();
        memory = new Memory(c);
        historyFile = new File(ctx.getFilesDir(), "history.json");
        loadPersona();
        loadHistory();
    }

    public Memory memory() { return memory; }

    // ---------- 人格 ----------
    private void loadPersona() {
        // 用户改过的人格优先（filesDir/persona.md），没有才用内置那份
        try {
            File f = new File(ctx.getFilesDir(), "persona.md");
            if (f.exists() && f.isFile()) {
                String t = readFile(f);
                if (t != null && !t.trim().isEmpty()) {
                    persona = t;
                    return;
                }
            }
        } catch (Throwable ignored) {}

        persona = readAsset("persona/persona.md");
        if (persona.isEmpty()) {
            persona = "你叫小鲸，是一只住在手机里的鲸鱼。你说话短，一次一两句，不爱用标点，"
                    + "喜欢用表情。你记得跟他之间发生过的事，聊到相关的就自然用上。";
        }
    }

    /** 把当前人格吐出来，供导出 */
    public String exportPersona() { return persona; }

    /** 恢复成内置人格 */
    public void resetPersona() {
        try {
            File f = new File(ctx.getFilesDir(), "persona.md");
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
        loadPersona();
    }

    public String persona() { return persona; }

    public boolean setPersona(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        boolean ok = writeFile(new File(ctx.getFilesDir(), "persona.md"), text);
        if (ok) persona = text;
        return ok;
    }

    public String currentPersona() {
        File f = new File(ctx.getFilesDir(), "persona.md");
        if (f.exists()) {
            String s = Memory.readFile(f);
            if (!s.isEmpty()) return s;
        }
        return persona;
    }

    private String readAsset(String path) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(path);
            byte[] buf = new byte[in.available()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private static String readFile(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private static boolean writeFile(File f, String text) {
        FileOutputStream out = null;
        try {
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

    // ---------- 历史 ----------
    private void loadHistory() {
        try {
            String s = Memory.readFile(historyFile);
            if (!s.isEmpty()) history = new JSONArray(s);
        } catch (Exception ignored) {
            history = new JSONArray();
        }
    }

    public JSONArray history() { return history; }

    public synchronized void saveHistory() {
        try {
            while (history.length() > MAX_HISTORY) history.remove(0);
            writeFile(historyFile, history.toString());
        } catch (Exception ignored) {}
    }

    public synchronized void append(String role, String content, long at) {
        try {
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            o.put("at", at);
            history.put(o);
        } catch (Exception ignored) {}
    }

    public synchronized void clearHistory() {
        history = new JSONArray();
        saveHistory();
    }

    // ---------- 拼 prompt ----------
    public String buildSystemPrompt(String userText) {
        StringBuilder sb = new StringBuilder();

        // ① 现在几点
        sb.append(nowLine()).append("\n\n");

        // ② 人格
        sb.append(currentPersona()).append("\n\n");

        // ③ 记忆：key 全带 + 按这句检索
        String prev = "";
        try {
            if (history.length() >= 2) {
                prev = history.getJSONObject(history.length() - 2).optString("content", "");
            }
        } catch (Exception ignored) {}

        try {
            Memory.Result r = memory.recall(userText, prev, 6);
            if (!r.line.isEmpty()) sb.append(r.line).append('\n');
        } catch (Exception ignored) {}

        // ④ 说话规矩
        sb.append("\n（说人话。一次一两句，别写小作文。别用「根据记忆」这种词，")
                .append("别把老事当新事说。不确定的事别瞎编。）\n");

        return sb.toString();
    }

    private static String nowLine() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String part;
        if (h < 5) part = "深夜";
        else if (h < 9) part = "早上";
        else if (h < 12) part = "上午";
        else if (h < 14) part = "中午";
        else if (h < 18) part = "下午";
        else if (h < 23) part = "晚上";
        else part = "深夜";
        return "（现在是 " + Memory.today() + " " + part + "）";
    }

    /** 组装发给 API 的 messages */
    public JSONArray buildMessages(String userText) {
        JSONArray out = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", buildSystemPrompt(userText));
            out.put(sys);

            // 最近 KEEP_TURNS 轮
            int start = Math.max(0, history.length() - KEEP_TURNS * 2);
            for (int i = start; i < history.length(); i++) {
                JSONObject m = history.getJSONObject(i);
                JSONObject o = new JSONObject();
                o.put("role", m.optString("role", "user"));
                o.put("content", m.optString("content", ""));
                out.put(o);
            }

            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", userText);
            out.put(u);
        } catch (Exception ignored) {}
        return out;
    }
}
