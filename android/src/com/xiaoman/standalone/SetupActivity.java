package com.xiaoman.standalone;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 第一次打开要填的东西：只有 DeepSeek API Key。
 *
 * 没有 IP、没有端口、没有口令——独立版不需要电脑。
 */
public class SetupActivity extends Activity {

    private EditText keyInput;
    private EditText nameInput;
    private TextView status;
    private Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUI();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUI() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFFF5F6F8);
        sc.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(40), dp(22), dp(22));

        TextView t1 = new TextView(this);
        t1.setText("吃白饭的大肥鱼");
        t1.setTextSize(26);
        t1.setTextColor(0xFF1A1A1A);
        root.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("独立版 · 不连电脑");
        t2.setTextSize(13);
        t2.setTextColor(0xFF888888);
        t2.setPadding(0, dp(4), 0, dp(28));
        root.addView(t2);

        // API Key
        root.addView(label("DeepSeek API Key"));
        keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setTextSize(14);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(prefs().getString(MainActivity.K_APIKEY, ""));
        keyInput.setBackground(box());
        keyInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("在 platform.deepseek.com 创建一个，只存在本机，不上传也不会发到别处");
        hint.setTextSize(11);
        hint.setTextColor(0xFF999999);
        hint.setPadding(dp(2), dp(6), 0, dp(22));
        root.addView(hint);

        // 她的名字
        root.addView(label("她叫什么（可不填）"));
        nameInput = new EditText(this);
        nameInput.setHint("小鲸");
        nameInput.setTextSize(14);
        nameInput.setSingleLine(true);
        nameInput.setText(prefs().getString(MainActivity.K_NAME, ""));
        nameInput.setBackground(box());
        nameInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 按钮
        Button test = new Button(this);
        test.setText("测一下这个 Key 通不通");
        test.setTextSize(14);
        test.setOnClickListener(v -> testKey());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        blp.topMargin = dp(24);
        root.addView(test, blp);

        Button save = new Button(this);
        save.setText("保存，开始聊");
        save.setTextSize(16);
        save.setTextColor(0xFFFFFFFF);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(0xFF4F9BD8);
        save.setBackground(g);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        slp.topMargin = dp(12);
        root.addView(save, slp);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(dp(2), dp(14), 0, 0);
        root.addView(status);

        // 记忆统计（让人知道记忆存了多少）
        TextView mem = new TextView(this);
        mem.setTextSize(12);
        mem.setTextColor(0xFF888888);
        mem.setPadding(0, dp(26), 0, 0);
        try {
            Memory m = new Memory(this);
            mem.setText("记忆：" + m.stats().toString());
        } catch (Exception e) {
            mem.setText("");
        }
        root.addView(mem);

        // 重新导入预置记忆（一般不用点，首启自动导）
        Button reimport = new Button(this);
        reimport.setText("重新导入预置记忆");
        reimport.setTextSize(12);
        reimport.setOnClickListener(v -> {
            try {
                java.io.File md = new java.io.File(getFilesDir(), "memory");
                if (md.exists()) {
                    java.io.File[] fs = md.listFiles();
                    if (fs != null) for (java.io.File f : fs) {
                        if (f.isFile() && f.getName().endsWith(".md")) f.delete();
                    }
                }
                Memory m2 = new Memory(this);
                mem.setText("记忆：" + m2.stats().toString());
                Toast.makeText(this, "导入了", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        root.addView(reimport, rlp);

        sc.addView(root);
        setContentView(sc);
    }

    private TextView label(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(13);
        tv.setTextColor(0xFF666666);
        tv.setPadding(dp(2), 0, 0, dp(8));
        return tv;
    }

    private GradientDrawable box() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(0xFFFFFFFF);
        g.setStroke(dp(1), 0xFFE4E4E4);
        return g;
    }

    private void testKey() {
        String k = cleanKey(keyInput.getText().toString());
        if (k.isEmpty()) { status.setText("先填 Key"); status.setTextColor(0xFFE04A4A); return; }
        status.setText("测试中…");
        status.setTextColor(0xFF666666);
        new Thread(() -> {
            String r = DeepSeek.testKey(k);
            ui.post(() -> {
                if (r.startsWith("ok|")) {
                    status.setText("通：" + r.substring(3));
                    status.setTextColor(0xFF3B9D3B);
                } else {
                    status.setText("不通：" + r.substring(5));
                    status.setTextColor(0xFFE04A4A);
                }
            });
        }).start();
    }

    /** 清掉复制时带进来的引号、空格、换行、零宽字符 */
    private static String cleanKey(String raw) {
        if (raw == null) return "";
        String k = raw.replace("\uFEFF", "").replace("\u200B", "")
                     .replace("\u00A0", " ")
                     .replace("\"", "").replace("'", "")
                     .replace("\n", "").replace("\r", "").replace("\t", "");
        k = k.trim();
        if (k.startsWith("Bearer ")) k = k.substring(7).trim();
        if (k.startsWith("bearer ")) k = k.substring(7).trim();
        return k;
    }

    private void save() {
        String k = cleanKey(keyInput.getText().toString());
        if (k.isEmpty()) {
            Toast.makeText(this, "Key 不能空着", Toast.LENGTH_SHORT).show();
            return;
        }
        // sk- 后面应该是 32 位，一共 35
        if (!k.startsWith("sk-")) {
            Toast.makeText(this, "Key 应该以 sk- 开头（现在是 " + k.substring(0, Math.min(5, k.length())) + "…）",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (k.length() != 35) {
            Toast.makeText(this, "Key 长度不对：应该是 35 位，你现在是 " + k.length()
                    + " 位（多/少了 " + Math.abs(k.length() - 35) + " 位）", Toast.LENGTH_LONG).show();
            return;
        }
        String n = nameInput.getText().toString().trim();
        prefs().edit()
                .putString(MainActivity.K_APIKEY, k)
                .putString(MainActivity.K_NAME, n)
                .apply();
        Toast.makeText(this, "存好了", Toast.LENGTH_SHORT).show();
        finish();
    }
}
