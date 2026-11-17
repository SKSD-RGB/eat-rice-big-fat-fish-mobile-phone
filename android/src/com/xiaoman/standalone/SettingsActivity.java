package com.xiaoman.standalone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 设置页：独立版所有能调的东西都在这儿。
 *
 * 第一批：
 *   改名字 / 换头像 / 换背景 / 改人格 / 清空聊天记录
 */
public class SettingsActivity extends Activity {

    private static final int REQ_AVATAR = 3001;
    private static final int REQ_BG = 3002;

    private Chat chat;
    private ImageView avatarView;
    private ImageView bgView;
    private TextView memText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        chat = new Chat(this);
        buildUI();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String herName() {
        String n = prefs().getString(MainActivity.K_NAME, "");
        return n == null || n.isEmpty() ? "小鲸" : n;
    }

    private File avatarFile() { return new File(getFilesDir(), "avatar.png"); }
    private File bgFile() { return new File(getFilesDir(), "bg.png"); }

    private void buildUI() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFFF5F6F8);
        sc.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(22);
        title.setTextColor(0xFF1A1A1A);
        title.setPadding(dp(4), 0, 0, dp(16));
        root.addView(title);

        // ---------- 外观 ----------
        root.addView(section("外观"));

        // 头像
        LinearLayout avRow = row();
        LinearLayout avLeft = new LinearLayout(this);
        avLeft.setOrientation(LinearLayout.VERTICAL);
        avLeft.setGravity(Gravity.CENTER);
        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFFDCEAF5);
        avatarView.setBackground(circle);
        avLeft.addView(avatarView, new LinearLayout.LayoutParams(dp(56), dp(56)));
        avRow.addView(avLeft, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        avRow.addView(itemText("换头像", "她长什么样"), weight());
        avRow.addView(arrow());
        avRow.setOnClickListener(v -> pickImage(REQ_AVATAR));
        root.addView(avRow);
        loadAvatar();

        // 背景
        LinearLayout bgRow = row();
        bgView = new ImageView(this);
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable sq = new GradientDrawable();
        sq.setCornerRadius(dp(8));
        sq.setColor(0xFFEEF2F6);
        bgView.setBackground(sq);
        LinearLayout bgWrap = new LinearLayout(this);
        bgWrap.setGravity(Gravity.CENTER);
        bgWrap.addView(bgView, new LinearLayout.LayoutParams(dp(48), dp(56)));
        bgRow.addView(bgWrap, new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        bgRow.addView(itemText("聊天背景", "挑一张图当底"), weight());
        bgRow.addView(arrow());
        bgRow.setOnClickListener(v -> pickImage(REQ_BG));
        root.addView(bgRow);
        loadBg();

        // 改名字
        LinearLayout nameRow = row();
        nameRow.addView(itemText("她叫什么", herName()), weight());
        nameRow.addView(arrow());
        nameRow.setOnClickListener(v -> editName());
        root.addView(nameRow);

        // ---------- 接口 ----------
        root.addView(section("接口"));
        LinearLayout keyRow = row();
        String k = prefs().getString(MainActivity.K_APIKEY, "");
        String keyDesc = k.isEmpty() ? "还没填，点这里填" : "已填 · " + mask(k);
        keyRow.addView(itemText("DeepSeek API Key", keyDesc), weight());
        keyRow.addView(arrow());
        keyRow.setOnClickListener(v -> editKey());
        root.addView(keyRow);

        LinearLayout testRow = row();
        testRow.addView(itemText("测一下 Key 通不通", "顺便看余额"), weight());
        testRow.addView(arrow());
        testRow.setOnClickListener(v -> testKey());
        root.addView(testRow);

        // ---------- 人格 ----------
        root.addView(section("人格"));
        LinearLayout perRow = row();
        perRow.addView(itemText("编辑人格", chat.currentPersona().length() + " 字"), weight());
        perRow.addView(arrow());
        perRow.setOnClickListener(v -> editPersona());
        root.addView(perRow);

        // ---------- 记忆 ----------
        root.addView(section("记忆"));
        LinearLayout memRow = row();
        memText = itemText("记忆", "");
        memRow.addView(memText, weight());
        memRow.addView(arrow());
        memRow.setOnClickListener(v -> showMemory());
        root.addView(memRow);
        refreshMemText();

        // ---------- 聊天 ----------
        root.addView(section("聊天"));
        LinearLayout clearRow = row();
        TextView ct = new TextView(this);
        ct.setText("清空聊天记录");
        ct.setTextSize(15);
        ct.setTextColor(0xFFE04A4A);
        ct.setPadding(0, dp(14), 0, dp(14));
        clearRow.addView(ct, weight());
        clearRow.setOnClickListener(v -> confirmClear());
        root.addView(clearRow);

        // ---------- 关于 ----------
        root.addView(section("关于"));
        LinearLayout aboutRow = row();
        aboutRow.addView(itemText("版本", "1.3 · 独立版"), weight());
        root.addView(aboutRow);

        sc.addView(root);
        setContentView(sc);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(card());
        r.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(0xFFFFFFFF);
        return g;
    }

    private TextView section(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(12);
        tv.setTextColor(0xFF888888);
        tv.setPadding(dp(6), dp(14), 0, dp(8));
        return tv;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView itemText(String main, String sub) {
        TextView tv = new TextView(this);
        String s = main + (sub == null || sub.isEmpty() ? "" : "\n" + sub);
        tv.setText(s);
        tv.setTextSize(15);
        tv.setTextColor(0xFF1A1A1A);
        tv.setLineSpacing(0, 1.15f);
        tv.setPadding(0, dp(14), 0, dp(14));
        return tv;
    }

    private TextView arrow() {
        TextView a = new TextView(this);
        a.setText("›");
        a.setTextSize(20);
        a.setTextColor(0xFFCCCCCC);
        return a;
    }

    // ---------- 头像 / 背景 ----------
    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选一张图"), req);
        } catch (Exception e) {
            toast("打不开相册：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        try {
            Uri u = data.getData();
            InputStream in = getContentResolver().openInputStream(u);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp == null) { toast("读不了这张图"); return; }

            File dst = req == REQ_AVATAR ? avatarFile() : bgFile();
            FileOutputStream out = new FileOutputStream(dst);
            // 头像缩到 256，背景缩到 1080 宽，省空间
            int max = req == REQ_AVATAR ? 256 : 1080;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w > max || h > max) {
                float sc2 = Math.min((float) max / w, (float) max / h);
                bmp = Bitmap.createScaledBitmap(bmp, (int) (w * sc2), (int) (h * sc2), true);
            }
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
            out.flush();
            out.close();

            prefs().edit().putString(req == REQ_AVATAR ? "avatar" : "bg", dst.getAbsolutePath()).apply();
            if (req == REQ_AVATAR) loadAvatar(); else loadBg();
            toast("换好了");
        } catch (Exception e) {
            toast("存不下：" + e.getMessage());
        }
    }

    private void loadAvatar() {
        try {
            if (avatarFile().exists()) {
                avatarView.setImageBitmap(BitmapFactory.decodeFile(avatarFile().getAbsolutePath()));
            }
        } catch (Exception ignored) {}
    }

    private void loadBg() {
        try {
            if (bgFile().exists()) {
                bgView.setImageBitmap(BitmapFactory.decodeFile(bgFile().getAbsolutePath()));
            }
        } catch (Exception ignored) {}
    }

    // ---------- Key ----------
    private static String mask(String k) {
        if (k == null || k.length() < 8) return "****";
        return k.substring(0, 5) + "…" + k.substring(k.length() - 4);
    }

    private void editKey() {
        EditText e = new EditText(this);
        e.setText(prefs().getString(MainActivity.K_APIKEY, ""));
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setHint("sk-...");
        e.setHintTextColor(Theme.muted());
        e.setTextColor(Theme.text());
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setBackground(Theme.inputBg());
        e.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView tip = new TextView(this);
        tip.setText("在 platform.deepseek.com 申请。只存本机，不上传。");
        tip.setTextSize(12);
        tip.setTextColor(Theme.muted());
        tip.setPadding(0, 0, 0, dp(10));
        box.addView(tip);
        box.addView(e);

        // 保存按钮放进卡片里
        Button save = new Button(this);
        save.setText("保存");
        save.setTextSize(15);
        save.setTextColor(0xFFFFFFFF);
        save.setBackground(Theme.sendButton());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        slp.topMargin = dp(14);
        box.addView(save, slp);

        android.app.Dialog dlg = MDialog.cardView(this, "DeepSeek API Key", box, "关");
        save.setOnClickListener(v -> {
            String val = e.getText().toString().trim();
            if (val.isEmpty()) { toast("不能空着"); return; }
            prefs().edit().putString(MainActivity.K_APIKEY, val).apply();
            toast("存好了");
            try { dlg.dismiss(); } catch (Exception ignored) {}
            recreate();
        });
        dlg.show();
    }

    private void testKey() {
        String k = prefs().getString(MainActivity.K_APIKEY, "");
        if (k.isEmpty()) { toast("先填 Key"); return; }
        toast("测试中…");
        new Thread(() -> {
            final String r = DeepSeek.testKey(k);
            runOnUiThread(() -> {
                if (r.startsWith("ok|")) toast("通：" + r.substring(3));
                else toast("不通：" + r.substring(5));
            });
        }).start();
    }

    // ---------- 名字 ----------
    private void editName() {
        EditText e = styledInput(herName(), true);
        android.app.Dialog dlg = MDialog.cardView(this, "她叫什么", wrapWithSave(e, () -> {
            prefs().edit().putString(MainActivity.K_NAME, e.getText().toString().trim()).apply();
            toast("改好了");
            recreate();
        }), "关");
        dlg.show();
    }

    /** 统一样式的输入框 */
    private EditText styledInput(String text, boolean singleLine) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(14);
        e.setTextColor(Theme.text());
        e.setBackground(Theme.inputBg());
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (singleLine) {
            e.setSingleLine(true);
        } else {
            e.setSingleLine(false);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    /** 把输入框包一层「取消/保存」按钮 */
    private LinearLayout wrapWithSave(View content, Runnable onSave) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(content);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        bp.topMargin = dp(14);
        box.addView(btns, bp);

        TextView cancel = new TextView(this);
        cancel.setText("算了");
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(Theme.muted());
        cancel.setBackground(Theme.roundRect(Theme.chipBg(), 0, 23));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = dp(8);
        btns.addView(cancel, clp);

        TextView ok = new TextView(this);
        ok.setText("保存");
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setTextColor(0xFFFFFFFF);
        ok.setBackground(Theme.sendButton());
        btns.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        cancel.setOnClickListener(v -> {
            try {
                android.view.ViewParent pp = box.getParent();
                while (pp != null && !(pp instanceof android.app.Dialog)) pp = pp.getParent();
                if (pp instanceof android.app.Dialog) ((android.app.Dialog) pp).dismiss();
            } catch (Exception ignored) {}
        });
        ok.setOnClickListener(v -> {
            onSave.run();
            try {
                android.view.ViewParent pp = box.getParent();
                while (pp != null && !(pp instanceof android.app.Dialog)) pp = pp.getParent();
                if (pp instanceof android.app.Dialog) ((android.app.Dialog) pp).dismiss();
            } catch (Exception ignored) {}
        });
        return box;
    }

    // ---------- 人格 ----------
    private void editPersona() {
        EditText e = styledInput(chat.currentPersona(), false);
        e.setMinLines(12);
        ScrollView sw = new ScrollView(this);
        sw.addView(e);
        android.view.ViewGroup.LayoutParams lp = sw.getLayoutParams();
        sw.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        android.app.Dialog dlg = MDialog.cardView(this, "编辑人格", wrapWithSave(sw, () -> {
            if (chat.setPersona(e.getText().toString())) toast("存好了");
            else toast("存不下（不能是空的）");
        }), "关");
        dlg.show();
    }

    // ---------- 记忆 ----------
    private void refreshMemText() {
        try {
            Memory m = new Memory(this);
            org.json.JSONObject o = m.stats();
            memText.setText("记忆\n" +
                    "关键 " + o.optInt("key") + " · 用户 " + o.optInt("user") +
                    " · 长期 " + o.optInt("memory") + " · 共 " + o.optInt("total"));
        } catch (Exception ignored) {}
    }

    private void showMemory() {
        try {
            Memory m = new Memory(this);
            StringBuilder sb = new StringBuilder();
            sb.append("关键记忆（每轮都带）：\n");
            for (Memory.Entry e : m.list(Memory.KEY).subList(0, Math.min(10, m.list(Memory.KEY).size()))) {
                sb.append("· ").append(e.title).append('\n');
            }
            sb.append("\n用户档案：\n");
            java.util.List<Memory.Entry> us = m.list(Memory.USER);
            for (int i = 0; i < Math.min(8, us.size()); i++) {
                sb.append("· ").append(us.get(i).title).append('\n');
            }
            sb.append("\n完整记忆存在 App 私有目录的 memory/ 里。");

            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(13);
            tv.setTextColor(Theme.text());
            tv.setPadding(dp(4), dp(4), dp(4), dp(4));
            tv.setLineSpacing(0, 1.35f);
            ScrollView sw = new ScrollView(this);
            sw.addView(tv);
            sw.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
            MDialog.cardView(this, "记忆", sw, "知道了").show();
        } catch (Exception e) {
            toast("读不出来：" + e.getMessage());
        }
    }

    // ---------- 清空 ----------
    private void confirmClear() {
        MDialog.card(this, "清空聊天记录？",
                "屏幕上的记录会没，但她记住的东西不丢。",
                "清空", () -> {
                    chat.clearHistory();
                    toast("清空了");
                },
                "算了", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
