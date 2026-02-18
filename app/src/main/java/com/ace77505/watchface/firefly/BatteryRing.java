package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.BatteryManager;

/**
 * 电量环：实例可配置颜色 / 厚度缩放 / 内缩（位置）
 *
 * 默认行为与原来一致，但通过 setConfig(...) 可覆盖 color / size / inset。
 */
public class BatteryRing {
    private final Context context;
    private final Paint paint;

    // 默认颜色（之前的常量）
    private static final int DEFAULT_COLOR = Color.parseColor("#FFA04A");

    // 默认厚度与内缩比例（如原来）
    public static final float DEFAULT_THICKNESS_RATIO = 0.0125f;
    public static final float DEFAULT_INSET_RATIO = 0.96f;

    // 可配置字段（实例级）
    private int ringColor = DEFAULT_COLOR;
    private float thicknessRatio = DEFAULT_THICKNESS_RATIO;
    private float insetRatio = DEFAULT_INSET_RATIO;

    public BatteryRing(Context context) {
        this.context = context.getApplicationContext();
        this.paint = new Paint();
        this.paint.setAntiAlias(true);
        this.paint.setDither(true);
    }

    /**
     * 配置电量环参数：
     * @param insetRatio 内缩比例（0..1），越小环越靠内侧
     * @param sizeScale 厚度缩放（乘于默认 thicknessRatio）
     * @param color 环颜色
     */
    public void setConfig(float insetRatio, float sizeScale, int color) {
        if (insetRatio > 0f && insetRatio <= 1f) this.insetRatio = insetRatio;
        this.thicknessRatio = DEFAULT_THICKNESS_RATIO * Math.max(0.1f, sizeScale);
        this.ringColor = color;
    }

    // 兼容性方法：由内部查询电量并绘制（保留以兼容旧调用）
    public void draw(Canvas canvas, PolarCoord polar, float[] tmp) {
        float batteryLevel = getBatteryLevel();
        draw(canvas, polar, tmp, batteryLevel);
    }

    /**
     * 主绘制接口：使用外部传入的 batteryLevel（避免每帧系统查询）
     */
    public void draw(Canvas canvas, PolarCoord polar, float[] tmp, float batteryLevel) {
        float cx = polar.getCenterX();
        float cy = polar.getCenterY();
        float radius = polar.getMaxRadius();

        batteryLevel = Math.max(0f, Math.min(1f, batteryLevel));

        float outerRadius = radius * insetRatio;
        float ringThickness = radius * thicknessRatio;
        float ringCenterRadius = outerRadius - ringThickness / 2f;

        // 可选：背景环（保持默认关闭）
        // 绘制电量填充
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringThickness);
        paint.setColor(ringColor);
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

        // 终点燃烧点（和燃烧特效）
        if (batteryLevel > 0f && sweepAngle > 0f) {
            float endAngleDeg = startAngle + sweepAngle;
            polar.toCartesianDegOut(endAngleDeg, ringCenterRadius, tmp);
            float dotX = tmp[0];
            float dotY = tmp[1];

            FlameEffect.draw(canvas, paint, dotX, dotY, radius, batteryLevel);
        }

        paint.setStyle(Paint.Style.STROKE);
    }

    public float getBatteryLevel() {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity > 0 && capacity <= 100) {
                    return capacity / 100f;
                }
            }
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
            // ignore
        }
        return 0.75f;
    }
}