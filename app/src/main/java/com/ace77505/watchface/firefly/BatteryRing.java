package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;

/**
 * 电量环：优化为接收 PolarCoord 与复用的临时数组以减少分配。
 * 提供两个 draw 重载：一个使用内部查询（兼容），一个使用外部传入的 batteryLevel（推荐，高性能）。
 */
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
     * 兼容性方法：由内部查询电量并绘制（保留以兼容旧调用）
     */
    public void draw(Canvas canvas, PolarCoord polar, float[] tmp) {
        float batteryLevel = getBatteryLevel();
        draw(canvas, polar, tmp, batteryLevel);
    }

    /**
     * 高���能绘制：使用外部传入的 batteryLevel（推荐，避免每帧系统查询）
     * @param canvas 画布
     * @param polar  复用的极坐标实例
     * @param tmp    复用的长度至少为2的临时数组（out）
     * @param batteryLevel 电量（0..1）
     */
    public void draw(Canvas canvas, PolarCoord polar, float[] tmp, float batteryLevel) {
        float cx = polar.getCenterX();
        float cy = polar.getCenterY();
        float radius = polar.getMaxRadius();

        // 保证电量在 [0,1]
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

        // 绘制电量环终点燃烧特效（使用极坐标计算终点位置，输出到 tmp）
        if (batteryLevel > 0f && sweepAngle > 0f) {
            float endAngleDeg = startAngle + sweepAngle;
            polar.toCartesianDegOut(endAngleDeg, ringCenterRadius, tmp);
            float dotX = tmp[0];
            float dotY = tmp[1];

            // 使用 FlameEffect 类绘制燃烧特效（FlameEffect 不再需要改动）
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