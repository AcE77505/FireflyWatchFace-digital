package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;

public class BatteryRing {
    private final Context context;
    private final Paint paint;

    // ===== 电量环颜色（固定为黄色）=====
    public static final int COLOR_BATTERY_RING = Color.parseColor("#FFA04A"); // 电量环固定为黄色

    // ===== 电量环厚度（占半径比例）=====
    public static final float BATTERY_RING_THICKNESS_RATIO = 0.0125f; // 厚度 = 0.0125R（更细）

    // ===== 电量环内缩比例（新增）=====
    public static final float BATTERY_RING_INSET_RATIO = 0.96f; // 内缩

    // ===== 是否显示电量环背景（新增）=====
    public static final boolean SHOW_BATTERY_RING_BACKGROUND = false; // 设为false表示透明背景

    public BatteryRing(Context context) {
        this.context = context.getApplicationContext();
        this.paint = new Paint();
        this.paint.setAntiAlias(true);
        this.paint.setDither(true);
    }

    /**
     * 绘制电量环
     * @param canvas 画布
     * @param bounds 表盘边界
     */
    public void draw(Canvas canvas, Rect bounds) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;

        // 获取当前电量（0..1）
        float batteryLevel = getBatteryLevel();
        batteryLevel = Math.max(0f, Math.min(1f, batteryLevel));

        float outerRadius = radius * BATTERY_RING_INSET_RATIO;
        float ringThickness = radius * BATTERY_RING_THICKNESS_RATIO;
        float ringCenterRadius = outerRadius - ringThickness / 2f;

        // 绘制电量环背景（如果需要）
        if (SHOW_BATTERY_RING_BACKGROUND) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ringThickness);
            paint.setColor(Color.parseColor("#333333"));
            paint.setAlpha(150);
            canvas.drawCircle(cx, cy, ringCenterRadius, paint);
        }

        // 绘制电量填充部分
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringThickness);
        paint.setColor(COLOR_BATTERY_RING);
        paint.setAlpha(255);

        float startAngle = -90f;
        float sweepAngle = 360f * batteryLevel;

        Paint.Cap prevCap = paint.getStrokeCap();
        paint.setStrokeCap(Paint.Cap.BUTT);

        canvas.drawArc(
                cx - ringCenterRadius,
                cy - ringCenterRadius,
                cx + ringCenterRadius,
                cy + ringCenterRadius,
                startAngle,
                sweepAngle,
                false,
                paint
        );

        paint.setStrokeCap(prevCap);

        // 绘制电量环终点燃烧特效
        if (batteryLevel > 0f && sweepAngle > 0f) {
            double endAngleRad = Math.toRadians(startAngle + sweepAngle);
            float dotX = cx + (float) Math.cos(endAngleRad) * ringCenterRadius;
            float dotY = cy + (float) Math.sin(endAngleRad) * ringCenterRadius;

            // 使用 FlameEffect 类绘制燃烧特效
            FlameEffect.draw(canvas, paint, dotX, dotY, radius, batteryLevel);
        }

        paint.setStyle(Paint.Style.STROKE);
    }

    /**
     * 返回当前电量，范围 0..1
     * 优先使用 BatteryManager.BATTERY_PROPERTY_CAPACITY（API 21+），若不可用则使用 ACTION_BATTERY_CHANGED 的粘性 Intent 计算（level/scale）
     * 若两者均失败，则回退到 0.75（保守默认值）
     */
    public float getBatteryLevel() {
        try {
            // 优先使用 BatteryManager 的 BATTERY_PROPERTY_CAPACITY（百分比，0..100）
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity > 0 && capacity <= 100) {
                    return capacity / 100f;
                }
            }

            // 回退：使用粘性广播 Intent.ACTION_BATTERY_CHANGED
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (float) level / (float) scale;
                }
            }
        } catch (Exception e) {
            // 安全降级：不抛异常，仅使用默认值
        }

        // 最后回退默认值（可根据需要改为 0f）
        return 0.75f;
    }
}