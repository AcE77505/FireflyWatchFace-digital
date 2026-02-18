package com.ace77505.watchface.firefly;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * 省电版燃烧特效类
 */
public class FlameEffect {
    // 火焰颜色常量
    public static final int FLAME_COLOR_CORE = Color.parseColor("#FFEB80");  // 核心亮黄色
    public static final int FLAME_COLOR_MID = Color.parseColor("#FFA726");   // 中间橙黄
    public static final int FLAME_COLOR_OUTER = Color.parseColor("#FF5722"); // 外缘橙红

    // 火焰参数
    public static final int FLAME_POINTS = 5;         // 火焰点数（减少点数以省电）

    // 动画循环周期（毫秒）
    public static final float CYCLE_DURATION = 4000; // 4秒一个完整循环，更自然的火焰节奏

    /**
     * 绘制燃烧特效
     * @param canvas 画布
     * @param paint 画笔（由调用方提供，避免重复创建）
     * @param x 中心X坐标
     * @param y 中心Y坐标
     * @param radius 表盘半径（用于计算火焰大小）
     * @param batteryLevel 当前电量（0-1，用于调整火焰强度）
     */
    public static void draw(Canvas canvas, Paint paint, float x, float y, float radius, float batteryLevel) {
        // 直接用当前时间计算相位
        long currentTime = System.currentTimeMillis();
        float normalizedTime = (currentTime % (long)CYCLE_DURATION) / CYCLE_DURATION;
        float flamePhase = normalizedTime * 2 * (float) Math.PI;

        // 根据电量调整火焰强度（低电量时火焰更旺）
        float intensity = calculateIntensity(batteryLevel);

        // 计算火焰基础尺寸
        float baseFlameSize = radius * 0.03f; // 表盘半径的3%

        // 保存画笔状态
        Paint.Style prevStyle = paint.getStyle();
        float prevStrokeWidth = paint.getStrokeWidth();

        // 切换到填充模式
        paint.setStyle(Paint.Style.FILL);

        // 预计算正弦值，减少重复计算
        float sinPhase = (float) Math.sin(flamePhase);
        float cosPhase = (float) Math.cos(flamePhase);

        // 绘制多层火焰
        drawFlameLayers(canvas, paint, x, y, baseFlameSize, intensity, sinPhase, cosPhase, flamePhase);

        // 添加中心高亮
        drawCenterHighlight(canvas, paint, x, y, baseFlameSize);

        // 恢复画笔状态
        paint.setStyle(prevStyle);
        paint.setStrokeWidth(prevStrokeWidth);
        paint.setAlpha(255);
    }

    /**
     * 根据电量计算火焰强度
     */
    public static float calculateIntensity(float batteryLevel) {
        // 电量越低，强度越大（范围0.5-1.0）
        float intensity = 1.0f - batteryLevel * 0.5f;
        return Math.max(0.5f, Math.min(1.0f, intensity));
    }

    /**
     * 绘制多层火焰
     */
    public static void drawFlameLayers(Canvas canvas, Paint paint, float x, float y,
                                 float baseSize, float intensity,
                                 float sinPhase, float cosPhase, float flamePhase) {
        for (int i = 0; i < FLAME_POINTS; i++) {
            // 计算每个火焰点的角度（均匀分布）
            float angleOffset = (float) (i * (2 * Math.PI / FLAME_POINTS));

            // 添加动态偏移，产生流动感
            float dynamicOffset = sinPhase * 0.3f + cosPhase * 0.2f * (i % 2 == 0 ? 1 : -1);

            // 计算火焰点位置（围绕中心轻微偏移）- 使用flamePhase创造流动感
            float flameX = x + (float) Math.cos(angleOffset + flamePhase * 0.5f + dynamicOffset * 0.2f) * (baseSize * 0.4f);
            float flameY = y + (float) Math.sin(angleOffset + flamePhase * 0.3f + dynamicOffset * 0.2f) * (baseSize * 0.4f);

            // 火焰大小随相位变化
            float flameSize = baseSize * (0.7f + 0.3f * (float) Math.sin(angleOffset + flamePhase * 2));
            flameSize *= intensity;

            // 根据位置绘制不同颜色的火焰层
            if (i == 0) {
                // 内层火焰（亮黄色）
                paint.setColor(FLAME_COLOR_CORE);
                paint.setAlpha(255);
                canvas.drawCircle(flameX, flameY, flameSize * 1.2f, paint);
            } else if (i < 3) {
                // 中层火焰（橙黄）
                paint.setColor(FLAME_COLOR_MID);
                paint.setAlpha(180);
                canvas.drawCircle(flameX * 1.1f - x * 0.1f,
                        flameY * 1.1f - y * 0.1f,
                        flameSize * 1.5f, paint);
            } else {
                // 外层火焰（橙红）
                paint.setColor(FLAME_COLOR_OUTER);
                paint.setAlpha(120);
                canvas.drawCircle(flameX * 1.2f - x * 0.2f,
                        flameY * 1.2f - y * 0.2f,
                        flameSize * 2.0f, paint);
            }
        }
    }

    /**
     * 绘制中心高亮点
     */
    public static void drawCenterHighlight(Canvas canvas, Paint paint, float x, float y, float baseSize) {
        paint.setColor(FLAME_COLOR_CORE);
        paint.setAlpha(255);
        canvas.drawCircle(x, y, baseSize * 0.8f, paint);
    }
}