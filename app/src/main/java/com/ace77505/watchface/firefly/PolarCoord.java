package com.ace77505.watchface.firefly;

import android.graphics.Rect;

/**
 * 极坐标工具类（支持复用、无分配输出）
 *
 * 约定：
 * - 角度以度为单位，0° 在正右（3点钟方向），顺时针为正。
 * - 可直接使用 -90f 表示顶端（12点钟方向），与现有代码兼容。
 *
 * 优化：
 * - 提供 update(...) 方法以复用实例，避免每帧 new。
 * - 提供 toCartesian*Out 方法，使用调用方提供的 float[] 输出，避免分配 PointF。
 */
public class PolarCoord {
    private float cx;
    private float cy;
    private float maxRadius;

    public PolarCoord(float centerX, float centerY, float maxRadius) {
        this.cx = centerX;
        this.cy = centerY;
        this.maxRadius = maxRadius;
    }

    /** 可复用地更新中心与半径，避免 new */
    public void update(float centerX, float centerY, float maxRadius) {
        this.cx = centerX;
        this.cy = centerY;
        this.maxRadius = maxRadius;
    }

    /**
     * 从表盘边界构造 PolarCoord，maxRadius = min(width, height) / 2
     */
    public static PolarCoord fromBounds(Rect bounds) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;
        return new PolarCoord(cx, cy, radius);
    }

    /**
     * 兼容旧用法：返回 PointF（已废弃，优先使用无分配版本）
     */
    @Deprecated
    public android.graphics.PointF toCartesianRatio(float angleDegrees, float ratio) {
        float r = maxRadius * ratio;
        return toCartesianDeg(angleDegrees, r);
    }

    @Deprecated
    public android.graphics.PointF toCartesianDeg(float angleDegrees, float absRadius) {
        double rad = Math.toRadians(angleDegrees);
        float x = cx + (float) Math.cos(rad) * absRadius;
        float y = cy + (float) Math.sin(rad) * absRadius;
        return new android.graphics.PointF(x, y);
    }

    /**
     * 无分配输出：极坐标（角度，比例） -> out[0]=x, out[1]=y
     * @param angleDegrees 角度（度）
     * @param ratio 比例（0..1）
     * @param out 长度至少为2的 float 数组
     */
    public void toCartesianRatioOut(float angleDegrees, float ratio, float[] out) {
        float r = maxRadius * ratio;
        toCartesianDegOut(angleDegrees, r, out);
    }

    /**
     * 无分配输出：极坐标（角度，像素半径） -> out[0]=x, out[1]=y
     * @param angleDegrees 角度（度）
     * @param absRadius 像素半径
     * @param out 长度至少为2的 float 数组
     */
    public void toCartesianDegOut(float angleDegrees, float absRadius, float[] out) {
        double rad = Math.toRadians(angleDegrees);
        out[0] = cx + (float) Math.cos(rad) * absRadius;
        out[1] = cy + (float) Math.sin(rad) * absRadius;
    }

    public float getCenterX() { return cx; }
    public float getCenterY() { return cy; }
    public float getMaxRadius() { return maxRadius; }
}