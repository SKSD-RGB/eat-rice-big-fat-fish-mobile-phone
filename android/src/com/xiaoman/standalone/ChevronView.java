package com.xiaoman.standalone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 实心圆角三角（2026-11-14 v8 · 最终版）
 *
 * 上一版不对称：我把切点接错了顺序。
 * 这版按「三角形 → 每个角切掉一段圆弧」的标准做法重写，保证左右镜像对称。
 *
 * 每一步都按顺时针走：
 *   A(顶点) → B(右下) → C(左下) → 回 A
 * 到每个角时，从进来的那条边退 r，画一段弧，再出去。
 */
public class ChevronView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean down = false;        // false = 尖朝上
    private int color = 0xFF1A1A1A;
    private float halfW = 0.17f;
    private float halfH = 0.13f;
    private float cornerRatio = 0.28f;   // 圆角占底边半宽的比例

    public ChevronView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setColor(int c) { color = c; invalidate(); }
    public void setDown(boolean d) { down = d; invalidate(); }
    public void setUp(boolean up) { down = !up; invalidate(); }
    public void setStrokeWidth(float dp) {}
    public void setShape(float w, float h) { halfW = w; halfH = h; invalidate(); }
    public void setCornerRatio(float r) { cornerRatio = r; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        float aw = w * halfW;
        float ah = h * halfH;
        float r = aw * cornerRatio;

        // 三个顶点
        float ax, ay, bx, by, cxx, cyy;
        if (!down) {
            // 尖朝上：顶点在上，底边在下
            ax = cx;       ay = cy - ah;   // A 顶点
            bx = cx + aw;  by = cy + ah;   // B 右下
            cxx = cx - aw; cyy = cy + ah;  // C 左下
        } else {
            // 尖朝下：顶点在下，底边在上
            ax = cx;       ay = cy + ah;
            bx = cx - aw;  by = cy - ah;
            cxx = cx + aw; cyy = cy - ah;
        }

        paint.setColor(color);
        canvas.drawPath(tri(ax, ay, bx, by, cxx, cyy, r), paint);
    }

    /** 顺时针 A→B→C→A，每个角切圆弧 */
    private Path tri(float ax, float ay, float bx, float by,
                     float cx, float cy, float r) {
        Path p = new Path();

        // A 角：进来是 C→A，出去是 A→B
        float[] aIn  = back(ax, ay, cx, cy, r);   // 从 A 朝 C 退 r
        float[] aOut = back(ax, ay, bx, by, r);   // 从 A 朝 B 退 r

        // B 角：进来 A→B，出去 B→C
        float[] bIn  = back(bx, by, ax, ay, r);
        float[] bOut = back(bx, by, cx, cy, r);

        // C 角：进来 B→C，出去 C→A
        float[] cIn  = back(cx, cy, bx, by, r);
        float[] cOut = back(cx, cy, ax, ay, r);

        p.moveTo(aOut[0], aOut[1]);        // 从 A 出
        p.lineTo(bIn[0], bIn[1]);          // 直线到 B 进
        p.quadTo(bx, by, bOut[0], bOut[1]);// B 处圆弧
        p.lineTo(cIn[0], cIn[1]);          // 直线到 C 进
        p.quadTo(cx, cy, cOut[0], cOut[1]);// C 处圆弧
        p.lineTo(aIn[0], aIn[1]);          // 直线回 A 进
        p.quadTo(ax, ay, aOut[0], aOut[1]);// A 处圆弧，闭合
        p.close();
        return p;
    }

    /** 从 (px,py) 沿朝 (tx,ty) 的方向退 r，返回该点 */
    private float[] back(float px, float py, float tx, float ty, float r) {
        float dx = tx - px, dy = ty - py;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return new float[]{px, py};
        float t = Math.min(0.45f, r / len);
        return new float[]{px + dx * t, py + dy * t};
    }
}
