package com.xiaoman.standalone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * 界面主题（2026-11-13 UI v2）
 * 所有颜色、圆角、渐变都从这里出，改风格只改这一个文件。
 */
public class Theme {

    // ---------- 浅色 ----------
    public static final int BG_TOP      = 0xFFEDF1F5;
    public static final int BG_BOTTOM   = 0xFFE4EAF0;
    public static final int BAR         = 0xFFFBFCFD;
    public static final int PANEL       = 0xFFFFFFFF;
    public static final int TEXT        = 0xFF1B1F24;
    public static final int MUTED       = 0xFF8E959E;
    public static final int LINE        = 0xFFE2E6EA;
    public static final int MINE_A      = 0xFF7FD4A0;
    public static final int MINE_B      = 0xFF5EC98A;
    public static final int MINE_TEXT   = 0xFF0B2A18;
    public static final int THEIRS      = 0xFFFFFFFF;
    public static final int ACCENT      = 0xFF4FB3A3;
    public static final int ONLINE      = 0xFF3EC98A;
    public static final int OFFLINE     = 0xFFC4C9CF;
    public static final int SHADOW      = 0x0F17242A;
    public static final int CHIP_BG     = 0xFFF0F3F6;

    // ---------- 深色 ----------
    public static final int BG_TOP_D    = 0xFF0B1B33;
    public static final int BG_BOTTOM_D = 0xFF102A4A;
    public static final int BAR_D       = 0xFF132E52;
    public static final int PANEL_D     = 0xFF17385F;
    public static final int TEXT_D      = 0xFFE9F2FF;
    public static final int MUTED_D     = 0xFF8FA6C4;
    public static final int LINE_D      = 0xFF2B4C78;
    public static final int MINE_A_D    = 0xFF1E5A8A;
    public static final int MINE_B_D    = 0xFF2A74AC;
    public static final int MINE_TEXT_D = 0xFFEAF6FF;
    public static final int THEIRS_D    = 0xFF1B3A63;
    public static final int ACCENT_D    = 0xFF6FB8FF;
    public static final int CHIP_BG_D   = 0xFF1A3A63;

    // ---------- 尺寸 ----------
    public static final int R_BUBBLE   = 18;
    public static final int R_SMALL    = 12;
    public static final int R_PILL     = 999;
    public static final int AVATAR     = 34;
    public static final int AVATAR_BIG = 42;

    // ---------- 皮肤 ----------
    public static final String[] SKIN_NAMES = {"薄荷", "蜜桃", "晴空", "葡萄", "奶油", "纸白"};
    public static final int[][] SKIN_COLORS = {
        {0xFF7FD4A0, 0xFF5EC98A, 0xFF0B2A18},
        {0xFFFFB3BA, 0xFFFF9AA8, 0xFF4A1420},
        {0xFF8EC9F0, 0xFF66AEE8, 0xFF0D2A42},
        {0xFFC3B0F0, 0xFFA891E8, 0xFF26194A},
        {0xFFFFE6A8, 0xFFF7D585, 0xFF453411},
        {0xFFE9E9E9, 0xFFD8D8D8, 0xFF1B1F24},
    };

    // ---------- 工具 ----------
    public static boolean dark = true;   // 默认深蓝主题

    public static int bgTop()     { return dark ? BG_TOP_D : BG_TOP; }
    public static int bgBottom()  { return dark ? BG_BOTTOM_D : BG_BOTTOM; }
    public static int bar()       { return dark ? BAR_D : BAR; }
    public static int panel()     { return dark ? PANEL_D : PANEL; }
    public static int text()      { return dark ? TEXT_D : TEXT; }
    public static int muted()     { return dark ? MUTED_D : MUTED; }
    public static int line()      { return dark ? LINE_D : LINE; }
    public static int theirs()    { return dark ? THEIRS_D : THEIRS; }
    public static int accent()    { return dark ? ACCENT_D : ACCENT; }
    public static int chipBg()    { return dark ? CHIP_BG_D : CHIP_BG; }
    public static int mineA()     { return dark ? MINE_A_D : MINE_A; }
    public static int mineB()     { return dark ? MINE_B_D : MINE_B; }
    public static int mineText()  { return dark ? MINE_TEXT_D : MINE_TEXT; }

    /** 背景渐变 */
    public static GradientDrawable background() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bgTop(), bgBottom()});
        return g;
    }

    /** 她的气泡（白/浅灰，左下角收一点） */
    public static GradientDrawable bubbleTheirs() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadii(new float[]{
                px(R_BUBBLE), px(R_BUBBLE),
                px(R_BUBBLE), px(R_BUBBLE),
                px(R_BUBBLE), px(R_BUBBLE),
                px(7), px(7)});
        g.setColor(theirs());
        return g;
    }

    /** 我的气泡（薄荷渐变，右下角收一点） */
    public static GradientDrawable bubbleMine() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{mineA(), mineB()});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadii(new float[]{
                px(R_BUBBLE), px(R_BUBBLE),
                px(R_BUBBLE), px(R_BUBBLE),
                px(7), px(7),
                px(R_BUBBLE), px(R_BUBBLE)});
        return g;
    }

    /** 胶囊（状态条、时间） */
    public static GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(R_PILL));
        g.setColor(color);
        return g;
    }

    /** 圆角矩形描边 */
    public static GradientDrawable roundRect(int fill, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radiusDp));
        g.setColor(fill);
        if (strokeColor != 0) g.setStroke(dp1(), strokeColor);
        return g;
    }

    /** 圆形头像底 */
    public static GradientDrawable circle(int c1, int c2) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** 发送按钮 */
    public static GradientDrawable sendButton() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{accent(), dark ? 0xFF3D9C8E : 0xFF3D9C8E});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** 输入框底 */
    public static GradientDrawable inputBg() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(16));
        g.setColor(dark ? 0xFF2A3038 : 0xFFF2F4F7);
        g.setStroke(dp1(), line());
        return g;
    }

    /** 变淡一点（没有 alpha 工具时用） */
    public static int alpha(int color, float a) {
        int al = (int) (255 * a);
        return Color.argb(al, Color.red(color), Color.green(color), Color.blue(color));
    }

    // dp 转换需要 Context，这里用静态缓存
    private static float density = 0f;

    public static void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
    }
    private static int dp1() { return Math.max(1, Math.round(density)); }
    private static int px(int dp) { return Math.round(dp * density); }
}
