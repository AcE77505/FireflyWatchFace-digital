package com.ace77505.watchface.firefly;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

/**
 * DirectionView - 可交互的角度选择视图（顶级类）
 * - 在中心绘制从中心到外圈的线段与箭头（箭头紧贴外圈）
 * - 用户点击屏幕时线段指向点击位置（除非点击落在被忽略的控件区域）
 * - 提供 setAngle(int) / getAngle() 方法以外部控制角度（用户语义：0=12点，顺时针为正）
 * - 支持注册要忽略触摸的子控件（例如覆盖在上方的按钮）
 * - 当角度变化时通过 OnAngleChangedListener 回调通知外部（Activity 会用它来更新顶部显示）
 */
public class DirectionView extends View {
    private Paint paint;
    private int angle = 0; // 用户角度，0=12点顺时针为正
    private float cx, cy, radius;
    private final Path arrowPath = new Path();

    // 要忽略触摸检测的 View（通常是覆盖在本 view 上的按钮）
    private android.view.View[] ignoreViews = null;
    private Rect[] ignoreRects = null;

    private OnAngleChangedListener angleChangedListener = null;

    public interface OnAngleChangedListener {
        void onAngleChanged(int newAngle);
    }

    public DirectionView(Context ctx) {
        super(ctx);
        init();
    }

    public DirectionView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    public DirectionView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(6f);
        paint.setStyle(Paint.Style.STROKE);

        // 使用 onTouchEvent 处理触摸，避免 setOnTouchListener 带来的优先级问题
        setClickable(true);
    }

    /**
     * 设置角度变化监听器
     */
    public void setOnAngleChangedListener(OnAngleChangedListener l) {
        this.angleChangedListener = l;
    }

    /**
     * 注册要忽略触摸的视图（例如覆盖在本 view 上的按钮）
     * 调用后 DirectionView 会在触摸事件中检查 raw coordinates 是否落入这些 view 的屏幕区域。
     */
    public void setIgnoreViews(android.view.View... views) {
        this.ignoreViews = views;
        if (views != null) {
            ignoreRects = new Rect[views.length];
            for (int i = 0; i < views.length; i++) ignoreRects[i] = new Rect();
            updateIgnoreRects();
        } else {
            ignoreRects = null;
        }
    }

    private void updateIgnoreRects() {
        if (ignoreViews == null) return;
        final int[] tmp = new int[2];
        for (int i = 0; i < ignoreViews.length; i++) {
            View v = ignoreViews[i];
            if (v == null) continue;
            v.getLocationOnScreen(tmp);
            int left = tmp[0];
            int top = tmp[1];
            int right = left + v.getWidth();
            int bottom = top + v.getHeight();
            ignoreRects[i].set(left, top, right, bottom);
        }
    }

    /**
     * 外部设置角度（用户语义 0..359）
     */
    public void setAngle(int deg) {
        angle = ((deg % 360) + 360) % 360;
        // 回调监听器
        if (angleChangedListener != null) angleChangedListener.onAngleChanged(angle);
        invalidate();
    }

    public int getAngle() {
        return angle;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w * 0.5f;
        cy = h * 0.5f;
        radius = Math.min(w, h) * 0.5f;
        updateIgnoreRects();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 仅处理按下事件
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 如果触点在任一忽略控件的屏幕区域内，则返回 false，让该控件处理事件
            if (ignoreRects != null) {
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                for (Rect r : ignoreRects) {
                    if (r != null && r.contains((int) rawX, (int) rawY)) {
                        return false;
                    }
                }
            }

            float x = event.getX();
            float y = event.getY();
            float dx = x - cx;
            float dy = y - cy;

            // 修复：在屏幕坐标系（y 向下为正）中使用 atan2(-dy, dx)
            double rad = Math.atan2(-dy, dx);
            double deg360 = Math.toDegrees(rad);
            int userAngle = (int) Math.round(90.0 - deg360);
            userAngle = ((userAngle % 360) + 360) % 360;

            angle = userAngle;
            if (angleChangedListener != null) angleChangedListener.onAngleChanged(angle);
            invalidate();

            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 背景保持黑色以便于查看（如需透明可删掉）
        canvas.drawColor(Color.BLACK);

        // 计算终点（箭头紧贴外圈，减去少量边距）
        double polarRad = Math.toRadians(angle - 90.0);
        float ex = cx + (float) Math.cos(polarRad) * (radius - 6f);
        float ey = cy + (float) Math.sin(polarRad) * (radius - 6f);

        // 画线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setColor(Color.YELLOW);
        canvas.drawLine(cx, cy, ex, ey, paint);

        // 箭头（三角形）
        float arrowSize = Math.max(10f, radius * 0.03f);
        arrowPath.reset();
        float ux = (ex - cx);
        float uy = (ey - cy);
        float len = (float) Math.hypot(ux, uy);
        if (len == 0) return;
        ux /= len; uy /= len;
        float vx = -uy;
        float vy = ux;
        float bx = ex - ux * arrowSize;
        float by = ey - uy * arrowSize;
        float leftX = bx + vx * (arrowSize * 0.6f);
        float leftY = by + vy * (arrowSize * 0.6f);
        float rightX = bx - vx * (arrowSize * 0.6f);
        float rightY = by - vy * (arrowSize * 0.6f);

        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setColor(Color.YELLOW);
        arrowPath.moveTo(ex, ey);
        arrowPath.lineTo(leftX, leftY);
        arrowPath.lineTo(rightX, rightY);
        arrowPath.close();
        canvas.drawPath(arrowPath, paint);

        // 注意：角度文本由 Activity 顶部 TextView 显示，此处不再绘制角度文本。
    }
}