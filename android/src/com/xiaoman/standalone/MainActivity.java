package com.xiaoman.standalone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 独立版主界面 + 设置抽屉（用老版 SettingsDrawer）
 *
 * 跟老版的区别：
 *   × 不连网桥，直接调 DeepSeek
 *   √ 界面完全一样：顶栏、抽屉、卡片、气泡
 */
public class MainActivity extends Activity {

    public static final String PREF = "xiaoman_standalone";
    public static final String K_APIKEY = "apikey";
    public static final String K_NAME = "her_name";
    public static final String K_SKIN = "skin";
    public static final String K_THEME = "theme_dark";
    public static final String K_STATUS = "status_bar_on";
    private static final int REQ_PERSONA = 0x51;

    private static final int REQ_AVATAR = 3001;
    private static final int REQ_COVER = 3002;
    private static final int REQ_STICKER_ADD = 3003;

    private Chat chat;
    private DeepSeek ds;
    private Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout rootBox;
    private LinearLayout rootLayout;
    private LinearLayout inputBar;
    private LinearLayout msgBox;
    private ScrollView scroll;
    private EditText input;
    private TextView sendBtn;
    private TextView nameView;
    private TextView dotView;
    private TextView statusBar;
    private ImageView avatarView;
    private SettingsDrawer drawer;
    private StickerPanel stickers;

    private Bubble streamingBubble = null;
    private StringBuilder streamingText = new StringBuilder();
    private boolean sending = false;
    private String lastSentText = "";
    private long lastSentAt = 0;
    private long tokenIn = 0, tokenOut = 0;

    private static class Bubble { TextView tv; Bubble(TextView t) { tv = t; } }

    // ================= 生命周期 =================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        // 读上次选的配色（没选过就用默认深蓝）
        Theme.dark = prefs().getBoolean(K_THEME, Theme.dark);

        String key = prefs().getString(K_APIKEY, "");
        if (key.isEmpty()) startActivity(new Intent(this, SetupActivity.class));

        chat = new Chat(this);
        ds = new DeepSeek();
        buildUI();
        redraw();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("due", false)) {
            ui.postDelayed(this::onDue, 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs().getString(K_APIKEY, "").isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        if (nameView != null) nameView.setText(herName());
        applyAvatar();
        applyBg();
        applyStatusBar();
        refreshStatusLine();
        redraw();
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private String apiKey() { return prefs().getString(K_APIKEY, ""); }
    private String herName() {
        String n = prefs().getString(K_NAME, "");
        return n == null || n.isEmpty() ? "小鲸" : n;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ================= 界面 =================
    private void buildUI() {
        rootBox = new FrameLayout(this);
        rootBox.setBackground(Theme.background());

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);

        // ===== 顶栏 =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(12), dp(10));
        top.setBackgroundColor(Theme.bar());

        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable cir = new android.graphics.drawable.GradientDrawable();
        cir.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cir.setColor(Theme.dark ? 0xFF2A3340 : 0xFFDCEAF5);
        avatarView.setBackground(cir);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        avlp.rightMargin = dp(11);
        top.addView(avatarView, avlp);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        top.addView(meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(nameRow);

        nameView = new TextView(this);
        nameView.setText(herName());
        nameView.setTextSize(16);
        nameView.setTextColor(Theme.text());
        nameRow.addView(nameView);

        dotView = new TextView(this);
        dotView.setText("●");
        dotView.setTextSize(9);
        dotView.setTextColor(0xFF4CAF50);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(6);
        nameRow.addView(dotView, dlp);

        statusBar = new TextView(this);
        statusBar.setTextSize(11.5f);
        statusBar.setTextColor(Theme.muted());
        meta.addView(statusBar);
        refreshStatusLine();

        // 菜单按钮（开抽屉）
        TextView menu = new TextView(this);
        menu.setText("☰");
        menu.setTextSize(20);
        menu.setTextColor(Theme.text());
        menu.setPadding(dp(10), dp(4), dp(4), dp(4));
        menu.setOnClickListener(v -> openDrawer());
        top.addView(menu);

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View div = new View(this);
        div.setBackgroundColor(Theme.line());
        root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ===== 消息区 =====
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        msgBox = new LinearLayout(this);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        msgBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.addView(msgBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ===== 输入栏 =====
        LinearLayout bar = new LinearLayout(this);
        inputBar = bar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(12));
        bar.setBackgroundColor(Theme.bar());

        input = new EditText(this);
        input.setHint("说点什么…");
        input.setTextSize(15);
        input.setMaxLines(4);
        input.setHintTextColor(Theme.muted());
        input.setTextColor(Theme.text());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(Theme.inputBg());
        input.setPadding(dp(16), dp(11), dp(16), dp(11));
        bar.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 表情包按钮
        TextView emojiBtn = new TextView(this);
        emojiBtn.setText("☺");
        emojiBtn.setTextSize(22);
        emojiBtn.setGravity(Gravity.CENTER);
        emojiBtn.setTextColor(Theme.muted());
        emojiBtn.setOnClickListener(v -> showStickers());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(40), dp(46));
        elp.leftMargin = dp(4);
        bar.addView(emojiBtn, elp);

        sendBtn = new TextView(this);
        sendBtn.setText("↑");
        sendBtn.setTextSize(18);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setTextColor(0xFFFFFFFF);
        sendBtn.setBackground(Theme.sendButton());
        sendBtn.setOnClickListener(v -> doSend());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(46), dp(46));
        blp.leftMargin = dp(9);
        bar.addView(sendBtn, blp);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rootBox.addView(root, rootLp);

        // 点击消息区空白处 → 收键盘（点气泡本身不触发，因为气泡是子视图）
        scroll.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) hideKeyboard();
            return false;
        });
        // 顶栏空白也收
        top.setOnClickListener(v -> hideKeyboard());
        msgBox.setOnClickListener(v -> hideKeyboard());

        setContentView(rootBox);
        setupKeyboardFollow();
    }

    /** 键盘弹起时滚到底；不要碰输入框的焦点（之前那版把焦点滚掉了） */
    private void setupKeyboardFollow() {
        if (scroll == null) return;
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) {
                ui.post(this::scrollDownQuiet);
                ui.postDelayed(this::scrollDownQuiet, 120);
            }
        });
        // 只有在用户手指真的点进输入框时才保证键盘出来
        if (input != null) {
            input.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    if (stickers != null && stickers.isShown()) stickers.hide();
                    ui.post(this::showKeyboard);
                }
            });
            // 点输入框也收表情（有时候焦点没变，只是点了）
            input.setOnClickListener(v -> {
                if (stickers != null && stickers.isShown()) stickers.hide();
            });
        }
    }

    private boolean keyboardShown = false;

    /** 滚到底，但不动输入框焦点 */
    private void scrollDownQuiet() {
        try {
            if (scroll == null) return;
            boolean inputHadFocus = input != null && input.hasFocus();
            scroll.post(() -> {
                try {
                    scroll.scrollTo(0, Math.max(0,
                            msgBox.getMeasuredHeight() - scroll.getHeight()));
                } catch (Throwable ignored) {}
            });
            // 如果原来有焦点，滚完还回去
            if (inputHadFocus && input != null) {
                ui.post(() -> { if (!input.hasFocus()) input.requestFocus(); });
            }
        } catch (Throwable ignored) {}
    }

    private void showKeyboard() {
        try {
            if (input == null) return;
            input.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                keyboardShown = true;
            }
        } catch (Throwable ignored) {}
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && input != null) {
                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
            if (input != null) input.clearFocus();
            if (rootLayout != null) rootLayout.requestFocus();
        } catch (Throwable ignored) {}
    }

    private void refreshStatusLine() {
        if (statusBar == null) return;
        String[] on = statusFieldState();
        StringBuilder sb = new StringBuilder();
        try {
            Memory m = new Memory(this);
            JSONObject o = m.stats();
            if ("1".equals(on[0])) sb.append("记忆 ").append(o.optInt("total")).append(" 条");
        } catch (Exception ignored) {}
        if ("1".equals(on[1])) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("↑").append(tokenIn).append(" ↓").append(tokenOut);
        }
        if ("1".equals(on[2])) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("历史 ").append(chat.history().length()).append(" 条");
        }
        if (sb.length() == 0) sb.append("直连 DeepSeek");
        else sb.append(" · 直连");
        statusBar.setText(sb.toString());
    }

    // ================= 抽屉 =================
    private boolean drawerAdded = false;

    private void openDrawer() {
        // 已经开着就先关掉，等它彻底关了再重建
        if (drawer != null) {
            final SettingsDrawer old = drawer;
            drawer = null;
            try {
                old.getView().animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    try {
                        View ov = old.getView();
                        if (ov.getParent() instanceof ViewGroup) {
                            ((ViewGroup) ov.getParent()).removeView(ov);
                        }
                    } catch (Throwable ignored) {}
                    buildAndShowDrawer();
                }).start();
                return;
            } catch (Throwable ignored) {}
        }
        buildAndShowDrawer();
    }

    private void buildAndShowDrawer() {
        try {
            drawer = new SettingsDrawer(this, hostImpl);
            View dv = drawer.getView();
            rootBox.addView(dv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            dv.bringToFront();
            drawerAdded = true;
            drawer.show();
        } catch (Throwable e) {
            toast("设置打不开：" + e.getMessage());
        }
    }

    // ================= Host 实现 =================
    private final SettingsDrawer.Host hostImpl = new SettingsDrawer.Host() {
        public String getHost() { return "独立直连"; }
        public String getPort() { return ""; }
        public String getToken() { return apiKey(); }

        public String getHerName() { return MainActivity.this.herName(); }
        public void setHerName(String n) {
            prefs().edit().putString(K_NAME, n).apply();
            if (nameView != null) nameView.setText(n);
        }

        public int getSkin() { return prefs().getInt(K_SKIN, 0); }
        public void setSkin(int idx) { prefs().edit().putInt(K_SKIN, idx).apply(); }

        public void onChangeAvatar() { pickImage(REQ_AVATAR); }
        public void onCover() { pickImage(REQ_COVER); }

        public void onClearChat() {
            MDialog.card(MainActivity.this, "清空聊天记录？",
                    "屏幕上的记录会没，但她记住的东西不丢。",
                    "清空", () -> {
                        chat.clearHistory();
                        redraw();
                        if (drawer != null) drawer.hide();
                        toast("清空了");
                    }, "算了", null).show();
        }

        public void onExportChat() { exportAll(); }

        public void onTokenStats() {
            MDialog.card(MainActivity.this, "Token 统计",
                    "本轮输入约 " + tokenIn + " · 输出约 " + tokenOut +
                            "\n历史 " + chat.history().length() + " 条",
                    "知道了", null, null, null).show();
        }

        public void onSchedule() { showScheduleDialog(); }

        public void onPersona() {
            EditText e = new EditText(MainActivity.this);
            e.setText(chat.currentPersona());
            e.setTextSize(13);
            e.setSingleLine(false);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setTextColor(Theme.text());
            e.setBackground(Theme.inputBg());
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
            ScrollView sw = new ScrollView(MainActivity.this);
            sw.addView(e);
            sw.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(sw);
            box.addView(personaTools(e));

            MDialog.card(MainActivity.this, "编辑人格",
                    "改完立刻生效，下一句就按新的人格说。", box,
                    "保存", () -> {
                        if (chat.setPersona(e.getText().toString())) toast("存好了");
                        else toast("存不下（不能是空的）");
                    }, "算了", null).show();
        }

        /** 人格：导入 / 导出 / 恢复内置 三个小按钮 */
        private View personaTools(EditText e) {
            LinearLayout bar = new LinearLayout(MainActivity.this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(0, dp(8), 0, 0);

            TextView imp = miniBtn("导入 .md");
            TextView exp = miniBtn("导出");
            TextView rst = miniBtn("恢复内置");

            imp.setOnClickListener(v -> pickPersonaFile());
            exp.setOnClickListener(v -> exportPersona());
            rst.setOnClickListener(v -> {
                chat.resetPersona();
                e.setText(chat.persona());
                toast("已恢复内置人格");
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(imp, lp);
            bar.addView(exp, lp);
            bar.addView(rst, lp);
            return bar;
        }

        private TextView miniBtn(String s) {
            TextView t = new TextView(MainActivity.this);
            t.setText(s);
            t.setTextSize(13f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(10), dp(9), dp(10), dp(9));
            t.setTextColor(Theme.accent());
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setColor(Theme.dark ? 0x332A74AC : 0x1A4FB3A3);
            t.setBackground(g);
            return t;
        }



        public void onKeys() { toast("独立版没有钥匙功能"); }

        public void onAiConfig() { editKey(); }

        public void onTheme() {
            boolean d = prefs().getBoolean(K_THEME, Theme.dark);
            prefs().edit().putBoolean(K_THEME, !d).apply();
            Theme.dark = !d;
            recreate();
        }

        public void onStatusToggle() {
            boolean now = !prefs().getBoolean(K_STATUS, true);
            prefs().edit().putBoolean(K_STATUS, now).apply();
            applyStatusBar();
            toast(now ? "状态栏已开" : "状态栏已关");
        }

        public void onStatusFields() { showStatusFields(); }

        public void onMemories() { showMemory(); }

        public void onAnalyze() { doAnalyze(); }

        public void onChatBg() { showBgPicker(); }

        public boolean isStatusOn() { return prefs().getBoolean(K_STATUS, true); }

        public void onClose() {
            if (drawer != null) drawer.hide();
        }
    };

    // ---------- 改 Key ----------
    private void editKey() {
        EditText e = new EditText(this);
        String cur = apiKey();
        e.setText(cur);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setHint("sk-...");
        e.setHintTextColor(Theme.muted());
        e.setTextColor(Theme.text());
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setBackground(Theme.inputBg());
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        final android.app.Dialog dlg = MDialog.cardView(this, "DeepSeek API Key", e,
                cur.isEmpty() ? "存" : "换一个");
        // 关键：不管按回车还是点按钮，关掉弹窗那一刻都保存
        dlg.setOnDismissListener(dd -> {
            try {
                String val = e.getText().toString().trim();
                // 去掉可能的 "Bearer " 前缀和首尾空白
                if (val.startsWith("Bearer ")) val = val.substring(7).trim();
                if (val.isEmpty()) return;
                if (!val.startsWith("sk-")) {
                    toast("Key 应该是 sk- 开头，这个不对");
                    return;
                }
                prefs().edit().putString(K_APIKEY, val).apply();
                toast("存好了");
                recreate();
            } catch (Throwable ignored) {}
        });
        dlg.show();
    }

    // ---------- 头像 / 背景 ----------
    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "选一张图"), req); }
        catch (Exception e) { toast("打不开相册"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        // 加表情包走单独一条路（可能是多选）
        if (req == REQ_STICKER_ADD) { importStickers(data); return; }
        if (req == REQ_PERSONA) { importPersonaFrom(data.getData()); return; }

        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp == null) { toast("读不了这张图"); return; }

            File dst = new File(getFilesDir(), req == REQ_AVATAR ? "avatar.png" : "bg.png");
            int max = req == REQ_AVATAR ? 256 : 1080;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w > max || h > max) {
                float sc2 = Math.min((float) max / w, (float) max / h);
                bmp = Bitmap.createScaledBitmap(bmp, (int) (w * sc2), (int) (h * sc2), true);
            }
            FileOutputStream out = new FileOutputStream(dst);
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
            out.flush();
            out.close();

            if (req == REQ_AVATAR) {
                prefs().edit().putString("avatar", dst.getAbsolutePath()).apply();
            } else {
                prefs().edit().putString("bg", dst.getAbsolutePath())
                        .putString("chat_bg_preset", "").apply();
            }
            if (req == REQ_AVATAR) applyAvatar(); else applyBg();
            toast("换好了");
        } catch (Exception e) { toast("存不下：" + e.getMessage()); }
    }

    private void applyAvatar() {
        try {
            File f = new File(getFilesDir(), "avatar.png");
            if (f.exists() && avatarView != null)
                avatarView.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
        } catch (Throwable ignored) {}
    }

    /** 手动导出：把所有数据打包到 /sdcard/Download/ 下一个文件夹 */
    private void exportAll() {
        try {
            File base = getExternalFilesDir(null);
            File dst;
            if (base != null) {
                dst = new File(base, "export");
            } else {
                dst = new File(android.os.Environment.getExternalStorageDirectory(),
                        "Download/大肥鱼备份");
            }
            if (!dst.exists()) dst.mkdirs();

            // 历史
            Filer.copyTo(new File(getFilesDir(), "history.json"), new File(dst, "history.json"));
            // 记忆
            Filer.copyDir(new File(getFilesDir(), "memory"), new File(dst, "memory"));
            // 头像 / 背景
            Filer.copyTo(new File(getFilesDir(), "avatar.png"), new File(dst, "avatar.png"));
            Filer.copyTo(new File(getFilesDir(), "bg.png"), new File(dst, "bg.png"));
            // 设置
            java.util.Map<String, ?> all = prefs().getAll();
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
            Filer.writeString(new File(dst, "settings.txt"), sb.toString());

            toast("备份好了：" + dst.getAbsolutePath());
        } catch (Throwable e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    /** 挑一个 .md 当人格 */
    private void pickPersonaFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "选人格文件(.md/.txt)"), REQ_PERSONA); }
        catch (Exception e) { toast("打不开文件选择器"); }
    }

    /** 把当前人格导到外部目录，方便发给别人 */
    private void exportPersona() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) { toast("没外部目录"); return; }
            File out = new File(dir, "persona.md");
            Filer.writeString(out, chat.persona());
            toast("导好了：" + out.getAbsolutePath());
        } catch (Throwable e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    /** 读 .md 文件塞进人格 */
    private void importPersonaFrom(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String text = new String(bos.toByteArray(), "UTF-8");
            if (text.trim().isEmpty()) { toast("这文件是空的"); return; }
            if (chat.setPersona(text)) {
                toast("人格换好了（" + text.length() + " 字）");
                recreate();
            } else {
                toast("存不下");
            }
        } catch (Throwable e) {
            toast("读不了：" + e.getMessage());
        }
    }

    /** 每次聊天后自动往外部目录写一份备份，卸载/崩溃都能救回来 */
    private void autoBackup() {
        try {
            File base = getExternalFilesDir(null);
            if (base == null) return;
            File dir = new File(base, "backup");
            if (!dir.exists()) dir.mkdirs();

            // 1. 聊天历史
            File hf = new File(getFilesDir(), "history.json");
            File mem = new File(getFilesDir(), "memory");
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
            String stamp = f.format(new java.util.Date());

            // 每次聊天都覆盖一份 latest，防住"最新那条"
            Filer.copyTo(hf, new File(dir, "history-latest.json"));
            Filer.copyDir(mem, new File(dir, "memory-latest"));

            // 每隔一段时间留一份带时间戳的
            long last = prefs().getLong("last_backup", 0L);
            if (System.currentTimeMillis() - last > 6 * 3600 * 1000L) {
                Filer.copyTo(hf, new File(dir, "history-" + stamp + ".json"));
                Filer.copyDir(mem, new File(dir, "memory-" + stamp));
                prefs().edit().putLong("last_backup", System.currentTimeMillis()).apply();
            }
        } catch (Throwable ignored) {}
    }

    /** 状态栏那一行显不显示（开关改了立刻生效，重启也记住） */
    private void applyStatusBar() {
        try {
            boolean on = prefs().getBoolean(K_STATUS, true);
            if (statusBar != null) {
                ui.post(() -> {
                    try {
                        statusBar.setVisibility(on ? View.VISIBLE : View.GONE);
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    private void applyBg() {
        try {
            // 先看有没有选内置背景
            String preset = prefs().getString("chat_bg_preset", "");
            if (!preset.isEmpty() && rootBox != null) {
                int rid = BgPicker.drawableId(this, preset);
                if (rid != 0) {
                    final android.graphics.drawable.Drawable d =
                            getResources().getDrawable(rid);
                    ui.post(() -> {
                        try {
                            rootBox.setBackground(d);
                            if (scroll != null) scroll.setBackgroundColor(0x00000000);
                        } catch (Throwable ignored) {}
                    });
                    return;
                }
            }
            File f = new File(getFilesDir(), "bg.png");
            if (!f.exists()) return;
            final Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp == null || rootBox == null) return;
            ui.post(() -> {
                try {
                    // 挂在最外层铺满全屏，键盘弹起时也不会跟着缩
                    android.graphics.drawable.BitmapDrawable bd =
                            new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                    bd.setGravity(Gravity.FILL);
                    rootBox.setBackground(bd);
                    if (scroll != null) scroll.setBackgroundColor(0x00000000);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    // ---------- 导出 ----------
    private void exportChat() {
        try {
            File f = new File(getExternalFilesDir(null), "chat-" + Memory.today() + ".txt");
            StringBuilder sb = new StringBuilder();
            JSONArray h = chat.history();
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.getJSONObject(i);
                boolean mine = "user".equals(m.optString("role"));
                sb.append(mine ? "他：" : herName() + "：").append(m.optString("content")).append("\n\n");
            }
            FileOutputStream out = new FileOutputStream(f);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
            toast("导出到 " + f.getAbsolutePath());
        } catch (Exception e) { toast("导出失败：" + e.getMessage()); }
    }

    // ---------- 记忆 ----------
    private void showMemory() {
        try {
            Memory m = new Memory(this);
            StringBuilder sb = new StringBuilder();
            sb.append("关键记忆（每轮都带）：\n");
            List<Memory.Entry> ks = m.list(Memory.KEY);
            for (int i = 0; i < Math.min(10, ks.size()); i++)
                sb.append("· ").append(ks.get(i).title).append('\n');
            sb.append("\n用户档案：\n");
            List<Memory.Entry> us = m.list(Memory.USER);
            for (int i = 0; i < Math.min(8, us.size()); i++)
                sb.append("· ").append(us.get(i).title).append('\n');
            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(13);
            tv.setTextColor(Theme.text());
            tv.setLineSpacing(0, 1.35f);
            tv.setPadding(dp(4), dp(4), dp(4), dp(4));
            ScrollView sw = new ScrollView(this);
            sw.addView(tv);
            sw.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
            MDialog.cardView(this, "记忆", sw, "知道了").show();
        } catch (Exception e) { toast("读不出来"); }
    }

    // ================= 表情包 =================
    /** 聊天背景：九宫格内置图 + 从相册 */
    private BgPicker bgPicker;

    private void showBgPicker() {
        // 上次收起时把视图摘掉了，这里重新挂回去
        if (bgPicker != null && bgPicker.getView().getParent() == null) {
            rootBox.addView(bgPicker.getView(), bgPicker.defaultParams());
        }
        if (bgPicker == null) {
            bgPicker = new BgPicker(this, prefs().getString("chat_bg_preset", ""),
                    new BgPicker.OnPick() {
                        @Override
                        public void onPreset(String key) {
                            String resName = "";
                            for (String[] p : BgPicker.PRESETS) {
                                if (p[0].equals(key)) { resName = p[2]; break; }
                            }
                            prefs().edit().putString("chat_bg_preset", resName).apply();
                            applyBg();
                            toast("背景换好了");
                        }
                        @Override
                        public void onAlbum() { pickImage(REQ_COVER); }
                    });
            rootBox.addView(bgPicker.getView(), bgPicker.defaultParams());
        }
        // 关键：提到最上层，不然被设置抽屉压住看不见
        try {
            View v = bgPicker.getView();
            v.bringToFront();
            if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(2000);
        } catch (Throwable ignored) {}
        bgPicker.show();
    }

    private void showStickers() {
        // 表情和键盘互斥：开表情先收键盘（跟微信一样）
        hideKeyboardNow();

        if (stickers == null) {
            stickers = new StickerPanel(this, asset -> sendSticker(asset));
            stickers.setOnAdd(() -> pickStickerImages());
            stickers.setAfterHide(() -> ui.postDelayed(this::showKeyboard, 80));
            rootBox.addView(stickers.getView(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        try {
            View sv = stickers.getView();
            sv.bringToFront();
            if (android.os.Build.VERSION.SDK_INT >= 21) sv.setElevation(1500);
        } catch (Throwable ignored) {}
        stickers.show();
    }

    /** 立刻收键盘：先卸焦点再收，最后把输入类型临时改成不可输入（对第三方输入法最硬） */
    private void hideKeyboardNow() {
        try {
            if (input == null) return;
            input.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }
            // 关键：临时置为不可输入，逼输入法关闭（很多第三方输入法只认这一下）
            input.setInputType(InputType.TYPE_NULL);
            input.setShowSoftInputOnFocus(false);
            ui.postDelayed(() -> {
                try {
                    if (input == null) return;
                    input.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    input.setShowSoftInputOnFocus(true);
                } catch (Throwable ignored) {}
            }, 300);
        } catch (Throwable ignored) {}
    }

    /** 从相册挑图加进表情包（可以一次挑好几张） */
    private void pickStickerImages() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "挑要加的表情"), REQ_STICKER_ADD);
        } catch (Exception e) {
            toast("打不开相册");
        }
    }

    /** 把选中的图存进 files/stickers/ */
    private void importStickers(Intent data) {
        try {
            java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;

            File dir = StickerPanel.userDir(this);
            int ok = 0;
            for (android.net.Uri u : uris) {
                try {
                    InputStream in = getContentResolver().openInputStream(u);
                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    in.close();
                    if (bmp == null) continue;
                    // 超过 512 的缩一下，省地方
                    int w = bmp.getWidth(), h = bmp.getHeight(), max = 512;
                    if (w > max || h > max) {
                        float s = Math.min((float) max / w, (float) max / h);
                        bmp = Bitmap.createScaledBitmap(bmp, (int) (w * s), (int) (h * s), true);
                    }
                    File dst = new File(dir, "u_" + System.currentTimeMillis() + "_" + ok + ".png");
                    FileOutputStream out = new FileOutputStream(dst);
                    bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
                    out.flush();
                    out.close();
                    ok++;
                } catch (Exception ignored) {}
            }
            if (ok > 0) {
                toast("加了 " + ok + " 张");
                if (stickers != null) stickers.refresh();
            } else {
                toast("一张都没读进来");
            }
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    /** 发一张表情：记成 [[stickers/xxx.png]] 或 [[user/xxx.png]]，画的时候还原成图 */
    private void sendSticker(String asset) {
        String mark = "[[" + asset + "]]";
        addOneBubble(mark, true);
        chat.append("user", mark, System.currentTimeMillis());
        chat.saveHistory();
        autoBackup();
        scrollDown();

        // 也让她看见（当成一句「发了个表情」），保持上下文连贯
        doSend("（我发了个表情）");
    }

    // ---------- 画消息 ----------
    private void redraw() {
        msgBox.removeAllViews();
        sanitizeHistoryOnce();
        JSONArray h = chat.history();
        if (h.length() == 0) {
            TextView hi = new TextView(this);
            hi.setText("（还没说过话）");
            hi.setTextSize(12);
            hi.setTextColor(Theme.muted());
            hi.setGravity(Gravity.CENTER);
            hi.setPadding(0, dp(30), 0, 0);
            msgBox.addView(hi);
            return;
        }
        for (int i = 0; i < h.length(); i++) {
            try {
                JSONObject m = h.getJSONObject(i);
                addBubble(m.optString("content", ""), "user".equals(m.optString("role")));
            } catch (Exception ignored) {}
        }
        scrollDown();
    }

    /**
     * 一条消息可能有好几行，一行画一个气泡 —— 跟老版一样。
     * 她本来就是一短句一行写的，挤在一个气泡里就不像在聊天了。
     */
    private Bubble addBubble(String text, boolean mine) {
        Bubble last = null;
        String[] lines = (text == null ? "" : text).split("\n");
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            last = addOneBubble(s, mine);
        }
        if (last == null) last = addOneBubble(text == null ? "" : text.trim(), mine);
        return last;
    }

    private Bubble addOneBubble(String text, boolean mine) {
        // [[stickers/xxx.png]] → 画成图，不画成文字
        if (text != null && text.startsWith("[[") && text.endsWith("]]")) {
            String asset = text.substring(2, text.length() - 2);
            return addImageBubble(asset, mine);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(mine ? Theme.mineText() : Theme.text());
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        tv.setTextIsSelectable(true);
        tv.setBackground(mine ? Theme.bubbleMine() : Theme.bubbleTheirs());
        tv.setLineSpacing(0, 1.2f);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = mine ? dp(52) : 0;
        tlp.rightMargin = mine ? 0 : dp(52);
        row.addView(tv, tlp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(7);
        msgBox.addView(row, rlp);
        return new Bubble(tv);
    }

    /** 表情包那种图，不带气泡底，直接贴图 */
    private Bubble addImageBubble(String asset, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = dp(120);
        row.addView(iv, new LinearLayout.LayoutParams(size, size));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(7);
        msgBox.addView(row, rlp);

        final TextView dummy = new TextView(this);   // Bubble 需要个 TextView，占位
        new Thread(() -> {
            try {
                final Bitmap b;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                if (asset.startsWith("user/")) {
                    // 用户自己加的，在 files/stickers/ 里
                    File f = new File(StickerPanel.userDir(this), asset.substring(5));
                    b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                } else {
                    java.io.InputStream in = getAssets().open(asset);
                    b = BitmapFactory.decodeStream(in, null, o);
                    in.close();
                }
                if (b != null) ui.post(() -> iv.setImageBitmap(b));
                else throw new Exception("空图");
            } catch (Throwable e) {
                ui.post(() -> {
                    dummy.setText("[图]");
                    row.removeView(iv);
                    row.addView(dummy);
                });
            }
        }).start();

        return new Bubble(dummy);
    }

    /**
     * 老版本存下的历史里可能混着 <状态>{...}</状态>，启动时洗一遍。
     * 洗过就不再重复做（存个标记）。
     */
    private void sanitizeHistoryOnce() {
        if (prefs().getBoolean("history_cleaned", false)) return;
        try {
            JSONArray h = chat.history();
            boolean changed = false;
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.getJSONObject(i);
                if (!"assistant".equals(m.optString("role"))) continue;
                String raw = m.optString("content", "");
                String clean = Sanitize.clean(raw);
                if (!clean.equals(raw)) {
                    m.put("content", clean.isEmpty() ? "嗯" : clean);
                    changed = true;
                }
            }
            if (changed) chat.saveHistory();
            prefs().edit().putBoolean("history_cleaned", true).apply();
        } catch (Exception ignored) {}
    }

    private void scrollDown() {
        ui.post(() -> { try { scroll.fullScroll(View.FOCUS_DOWN); } catch (Exception ignored) {} });
    }

    private void doSend() {
        // 点一下立刻锁，防连点两次发出两条
        if (sending) return;
        sending = true;
        sendBtn.setAlpha(0.5f);

        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            sending = false;
            sendBtn.setAlpha(1f);
            return;
        }
        input.setText("");
        doSend(text);
    }

    /** 真正发送（发送锁已在 doSend() 里拿到，这里不再抢） */
    private void doSend(String text) {
        if (text == null) { sending = false; sendBtn.setAlpha(1f); return; }
        text = text.trim();
        if (text.isEmpty()) { sending = false; sendBtn.setAlpha(1f); return; }
        if (apiKey().isEmpty()) {
            sending = false;
            sendBtn.setAlpha(1f);
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }

        if (msgBox.getChildCount() == 1) redraw();

        // 兜底：同一句话 600ms 内不许再进一次（防任何路径的双发）
        long now = System.currentTimeMillis();
        if (text.equals(lastSentText) && now - lastSentAt < 600) {
            sending = false;
            sendBtn.setAlpha(1f);
            return;
        }
        lastSentText = text;
        lastSentAt = now;

        addBubble(text, true);
        chat.append("user", text, System.currentTimeMillis());
        chat.saveHistory();
        autoBackup();
        scrollDown();

        streamingText.setLength(0);
        streamingBubble = addBubble("…", false);
        scrollDown();

        sending = true;
        sendBtn.setAlpha(0.5f);

        JSONArray messages = chat.buildMessages(text);
        tokenIn = messages.toString().length() / 2;

        new Thread(() -> {
            ds.chat(apiKey(), messages, new DeepSeek.Callback() {
                public void onDelta(String piece) {
                    streamingText.append(piece);
                    // 流式也要过一遍过滤，不然会先闪出 <状态>{...}
                    final String now = Sanitize.cleanStreaming(streamingText.toString());
                    ui.post(() -> {
                        if (streamingBubble != null) {
                            streamingBubble.tv.setText(now.isEmpty() ? "…" : now);
                            scrollDown();
                        }
                    });
                }
                public void onError(String message) {
                    ui.post(() -> {
                        if (streamingBubble != null) streamingBubble.tv.setText("（出错了：" + message + "）");
                        sending = false;
                        sendBtn.setAlpha(1f);
                    });
                }
                public void onDone(String full) {
                    ui.post(() -> {
                        String t2 = Sanitize.clean(full == null ? "" : full);
                        if (t2.isEmpty()) t2 = "嗯";
                        // 收完才知道有几行，这时候按行拆成多个气泡
                        int idx = msgBox.indexOfChild((LinearLayout) streamingBubble.tv.getParent());
                        if (idx >= 0) msgBox.removeViewAt(idx);
                        addBubble(t2, false);
                        chat.append("assistant", t2, System.currentTimeMillis());
                        chat.saveHistory();
        autoBackup();
                        tokenOut += t2.length() / 2;
                        sending = false;
                        sendBtn.setAlpha(1f);
                        streamingBubble = null;
                        refreshStatusLine();
                        scrollDown();
                    });
                }
            });
        }).start();
    }

    // ================= 定时消息 =================
    /** 记一个「过多久她来找你」，到点用闹钟提醒 + 本地生成一句话 */
    private void showScheduleDialog() {
        final String[] opts = {"5 分钟后", "15 分钟后", "30 分钟后", "1 小时后", "2 小时后"};
        final int[] mins = {5, 15, 30, 60, 120};
        MDialog.sheet(this, "让她过一会找你", opts, i -> {
            long at = System.currentTimeMillis() + mins[i] * 60_000L;
            prefs().edit().putLong("due_at", at).apply();
            scheduleAlarm(at);
            toast("好，" + opts[i] + "她来找你");
        }).show();
    }

    private void scheduleAlarm(long at) {
        try {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(this, DueReceiver.class);
            int flags = android.os.Build.VERSION.SDK_INT >= 23
                    ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                    : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            android.app.PendingIntent pi =
                    android.app.PendingIntent.getBroadcast(this, 1001, i, flags);
            if (am != null) {
                am.set(android.app.AlarmManager.RTC_WAKEUP, at, pi);
                prefs().edit().putLong("alarm_set_at", at).apply();
            }
        } catch (Throwable e) { toast("设定时失败：" + e.getMessage()); }
    }

    /** 闹钟到点：本地造一句，不需要联网 */
    public void onDue() {
        long due = prefs().getLong("due_at", 0);
        if (due == 0) return;
        prefs().edit().remove("due_at").apply();
        String[] lines = {
                "在干嘛",
                "突然想起你",
                "你还没睡啊",
                "我刚想起来一件事",
                "喂",
        };
        String line = lines[(int) (Math.random() * lines.length)];
        // 真有 apiKey 的话，让她用自己的人格说一句
        if (!apiKey().isEmpty()) {
            sparkProactive();
        } else {
            chat.append("assistant", line, System.currentTimeMillis());
            chat.saveHistory();
        autoBackup();
            redraw();
        }
        toast("她来找你了");
    }

    /** 让她主动开口：发一个隐藏提示让模型自己起头 */
    private void sparkProactive() {
        JSONArray messages = chat.buildMessages("（现在没人说话，你自己想句话找他。只回一句，不要解释）");
        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            ds.chat(apiKey(), messages, new DeepSeek.Callback() {
                public void onDelta(String piece) { sb.append(piece); }
                public void onError(String m) {}
                public void onDone(String full) {
                    final String t = Sanitize.clean(full == null ? "" : full);
                    if (t.isEmpty()) return;
                    ui.post(() -> {
                        chat.append("assistant", t, System.currentTimeMillis());
                        chat.saveHistory();
        autoBackup();
                        redraw();
                    });
                }
            });
        }).start();
    }

    // ================= 状态字段 =================
    private void showStatusFields() {
        final String[] keys = {"记忆条数", "Token 用量", "历史条数"};
        final String[] now = statusFieldState();
        MDialog.sheet(this, "状态栏显示什么", keys, i -> {
            boolean cur = "1".equals(now[i]);
            String[] arr = now.clone();
            arr[i] = cur ? "0" : "1";
            prefs().edit().putString("status_fields", join(arr)).apply();
            refreshStatusLine();
            toast(keys[i] + (cur ? " 隐藏" : " 显示"));
        }).show();
    }

    private String[] statusFieldState() {
        String raw = prefs().getString("status_fields", "1,0,1");
        String[] p = raw.split(",");
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) out[i] = i < p.length ? p[i].trim() : "0";
        return out;
    }

    private String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.toString();
    }

    // ================= AI 分析偏好 =================
    private void doAnalyze() {
        if (apiKey().isEmpty()) { toast("先填 Key"); return; }
        JSONArray h = chat.history();
        if (h.length() < 6) { toast("先多聊几句再分析"); return; }

        StringBuilder convo = new StringBuilder();
        int from = Math.max(0, h.length() - 40);
        for (int i = from; i < h.length(); i++) {
            try {
                JSONObject m = h.getJSONObject(i);
                convo.append("user".equals(m.optString("role")) ? "他：" : "她：")
                     .append(m.optString("content")).append('\n');
            } catch (Exception ignored) {}
        }

        toast("分析中…");
        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是记忆整理助手。读对话，提炼出关于「他」的偏好、习惯、忌讳，"
                    + "每条一行，不要编号不要解释，最多 3 条，没有就回空。");
            msgs.put(sys);
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", convo.toString());
            msgs.put(u);
        } catch (Exception ignored) {}

        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            ds.chat(apiKey(), msgs, new DeepSeek.Callback() {
                public void onDelta(String piece) { sb.append(piece); }
                public void onError(String m) {
                    ui.post(() -> toast("分析失败：" + m));
                }
                public void onDone(String full) {
                    final String t = full == null ? "" : full.trim();
                    ui.post(() -> {
                        if (t.isEmpty() || t.equals("空")) { toast("这次没分析出新东西"); return; }
                        String[] lines = t.split("\\n");
                        int n = 0;
                        for (String ln : lines) {
                            String c = ln.replaceAll("^[\\s\\-·•\\d\\.、]+", "").trim();
                            if (c.length() < 4) continue;
                            if (n >= 3) break;
                            try { new Memory(MainActivity.this).addUser(c); n++; }
                            catch (Exception ignored) {}
                        }
                        toast("存了 " + n + " 条到用户档案");
                        refreshStatusLine();
                    });
                }
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onBackPressed() {
        if (bgPicker != null && bgPicker.isShown()) { bgPicker.hide(); return; }
        if (stickers != null && stickers.isShown()) {
            stickers.hide();
            ui.postDelayed(this::showKeyboard, 200);   // 收表情后把键盘叫回来
            return;
        }
        if (drawer != null && drawer.isShown()) {
            // 抽屉里有二级页先退一级
            if (drawer.isAtSubPage() && drawer.popPage()) return;
            drawer.hide();
            return;
        }
        super.onBackPressed();
    }
}
