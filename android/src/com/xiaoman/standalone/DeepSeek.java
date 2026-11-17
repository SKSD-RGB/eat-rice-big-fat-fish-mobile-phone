package com.xiaoman.standalone;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 直接调 DeepSeek，不走网桥。
 *
 * 为什么：独立版就是为了不依赖电脑。
 * 所有请求从手机直接发到 https://api.deepseek.com
 */
public class DeepSeek {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-chat";

    /** 回调 */
    public interface Callback {
        /** 流式来的一小段 */
        void onDelta(String text);
        /** 出错了 */
        void onError(String message);
        /** 全说完了，给完整文本 */
        void onDone(String full);
    }

    private volatile boolean cancelled = false;

    public void cancel() {
        cancelled = true;
    }

    /**
     * 发一轮对话，流式返回。
     * 在后台线程调用。
     */
    public void chat(String apiKey, JSONArray messages, Callback cb) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(API_URL);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");

            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("temperature", 0.9);
            body.put("max_tokens", 1200);

            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readAll(conn.getErrorStream());
                if (code == 401) {
                    cb.onError("Key 不对（401）—— 去设置里重新填一遍 API Key，填完按「存」");
                } else if (code == 402) {
                    cb.onError("余额不足（402）—— 去 platform.deepseek.com 充值");
                } else if (code == 429) {
                    cb.onError("太快了（429）—— 等几秒再说");
                } else {
                    cb.onError("接口返回 " + code + (err.isEmpty() ? "" : "：" + err));
                }
                return;
            }

            InputStream in = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder full = new StringBuilder();
            String line;
            while (!cancelled && (line = br.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JSONObject j = new JSONObject(data);
                    JSONArray choices = j.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;
                    String piece = delta.optString("content", "");
                    if (!piece.isEmpty()) {
                        full.append(piece);
                        cb.onDelta(piece);
                    }
                } catch (Exception ignored) {
                    // 半截 JSON 或心跳，跳过
                }
            }
            br.close();
            cb.onDone(full.toString());

        } catch (Exception e) {
            if (!cancelled) cb.onError(e.getClass().getSimpleName() + "：" + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 试一下 key 通不通（给设置页用） */
    public static String testKey(String apiKey) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL("https://api.deepseek.com/user/balance");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            int code = conn.getResponseCode();
            if (code == 200) {
                String s = readAll(conn.getInputStream());
                try {
                    JSONObject j = new JSONObject(s);
                    JSONArray arr = j.optJSONArray("balance_infos");
                    if (arr != null && arr.length() > 0) {
                        String bal = arr.getJSONObject(0).optString("total_balance", "?");
                        return "ok|余额 ¥" + bal;
                    }
                } catch (Exception ignored) {}
                return "ok|通";
            }
            return "fail|HTTP " + code;
        } catch (Exception e) {
            return "fail|" + e.getClass().getSimpleName();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
