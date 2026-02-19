package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * PreferencesManager
 * 存取表盘各项偏好：
 * - 时间/日期/电量 元素的方向(direction)、距离(distance)、大小(size)、颜色(color)
 * - 电量环是否显示开关
 * - 背景图片文件名与每张图片的缩放百分比（每张图片独立保存）
 */
public class PreferencesManager {
    public static final String PREF_NAME = "watchface_prefs";

    // 颜色和电量环开关的原有键
    public static final String KEY_TIME_COLOR = "time_color";
    public static final String KEY_DATE_COLOR = "date_color";
    public static final String KEY_BATTERY_RING_ENABLED = "battery_ring_enabled";

    // 每个元素的方向/距离/大小/颜色键（time/date/battery）
    public static final String KEY_TIME_DIR = "time_dir";
    public static final String KEY_TIME_DIST = "time_dist";
    public static final String KEY_TIME_SIZE = "time_size";

    public static final String KEY_DATE_DIR = "date_dir";
    public static final String KEY_DATE_DIST = "date_dist";
    public static final String KEY_DATE_SIZE = "date_size";

    public static final String KEY_BATTERY_DIR = "battery_dir";
    public static final String KEY_BATTERY_DIST = "battery_dist";
    public static final String KEY_BATTERY_SIZE = "battery_size";
    public static final String KEY_BATTERY_COLOR = "battery_color"; // 电池元素（文本）颜色

    // 电量环（ring）颜色（单独键）
    public static final String KEY_BATTERY_RING_COLOR = "battery_ring_color";

    // 背景相关键
    public static final String KEY_BACKGROUND_FILENAME = "background_filename";
    // KEY_BACKGROUND_SCALE will be used as a prefix: KEY_BACKGROUND_SCALE + "_" + filename
    public static final String KEY_BACKGROUND_SCALE = "background_scale";

    public final SharedPreferences prefs;

    // 默认值
    public static final int DEFAULT_COLOR = android.graphics.Color.BLACK;
    public static final boolean DEFAULT_BATTERY_RING_ENABLED = true;

    // 默认布局参数（以你原有布局为参考）
    public static final float DEFAULT_TIME_DIR = 0f;      // 顶端（用户角度）
    public static final float DEFAULT_TIME_DIST = 0.18f;  // 原来使用 0.18R
    public static final float DEFAULT_TIME_SIZE = 1.0f;   // 100 -> 1.00x

    public static final float DEFAULT_DATE_DIR = 0f;
    public static final float DEFAULT_DATE_DIST = 0.30f;
    public static final float DEFAULT_DATE_SIZE = 1.0f;

    // 修改：电量默认位置改成正下方（6点钟 => 180°）
    public static final float DEFAULT_BATTERY_DIR = 180f;  // 底部（用户角度）
    public static final float DEFAULT_BATTERY_DIST = 0.9f;
    public static final float DEFAULT_BATTERY_SIZE = 1.0f;
    public static final int DEFAULT_BATTERY_COLOR = android.graphics.Color.WHITE;
    public static final int DEFAULT_BATTERY_RING_COLOR = android.graphics.Color.parseColor("#FFA04A"); // 原始环颜色

    // 背景默认
    public static final String DEFAULT_BACKGROUND_FILENAME = "119655138_sq.webp";
    public static final int DEFAULT_BACKGROUND_SCALE = 100;

    public PreferencesManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------
    // 原有颜色/开关接口
    // ---------------------
    public void setTimeColor(int color) { prefs.edit().putInt(KEY_TIME_COLOR, color).apply(); }
    public int getTimeColor() { return prefs.getInt(KEY_TIME_COLOR, DEFAULT_COLOR); }

    public void setDateColor(int color) { prefs.edit().putInt(KEY_DATE_COLOR, color).apply(); }
    public int getDateColor() { return prefs.getInt(KEY_DATE_COLOR, DEFAULT_COLOR); }

    public void setBatteryRingEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_BATTERY_RING_ENABLED, enabled).apply(); }
    public boolean isBatteryRingEnabled() { return prefs.getBoolean(KEY_BATTERY_RING_ENABLED, DEFAULT_BATTERY_RING_ENABLED); }

    // ---------------------
    // 时间元素：direction/distance/size
    // ---------------------
    public void setTimeDirection(float deg) { prefs.edit().putFloat(KEY_TIME_DIR, deg).apply(); }
    public float getTimeDirection() { return prefs.getFloat(KEY_TIME_DIR, DEFAULT_TIME_DIR); }

    public void setTimeDistance(float ratio) { prefs.edit().putFloat(KEY_TIME_DIST, ratio).apply(); }
    public float getTimeDistance() { return prefs.getFloat(KEY_TIME_DIST, DEFAULT_TIME_DIST); }

    public void setTimeSize(float size) { prefs.edit().putFloat(KEY_TIME_SIZE, size).apply(); }
    public float getTimeSize() { return prefs.getFloat(KEY_TIME_SIZE, DEFAULT_TIME_SIZE); }

    // ---------------------
    // 日期元素：direction/distance/size
    // ---------------------
    public void setDateDirection(float deg) { prefs.edit().putFloat(KEY_DATE_DIR, deg).apply(); }
    public float getDateDirection() { return prefs.getFloat(KEY_DATE_DIR, DEFAULT_DATE_DIR); }

    public void setDateDistance(float ratio) { prefs.edit().putFloat(KEY_DATE_DIST, ratio).apply(); }
    public float getDateDistance() { return prefs.getFloat(KEY_DATE_DIST, DEFAULT_DATE_DIST); }

    public void setDateSize(float size) { prefs.edit().putFloat(KEY_DATE_SIZE, size).apply(); }
    public float getDateSize() { return prefs.getFloat(KEY_DATE_SIZE, DEFAULT_DATE_SIZE); }

    // ---------------------
    // 电量元素（文本）: direction/distance/size/color
    // ---------------------
    public void setBatteryDirection(float deg) { prefs.edit().putFloat(KEY_BATTERY_DIR, deg).apply(); }
    public float getBatteryDirection() { return prefs.getFloat(KEY_BATTERY_DIR, DEFAULT_BATTERY_DIR); }

    public void setBatteryDistance(float ratio) { prefs.edit().putFloat(KEY_BATTERY_DIST, ratio).apply(); }
    public float getBatteryDistance() { return prefs.getFloat(KEY_BATTERY_DIST, DEFAULT_BATTERY_DIST); }

    public void setBatterySize(float size) { prefs.edit().putFloat(KEY_BATTERY_SIZE, size).apply(); }
    public float getBatterySize() { return prefs.getFloat(KEY_BATTERY_SIZE, DEFAULT_BATTERY_SIZE); }

    public void setBatteryColor(int color) { prefs.edit().putInt(KEY_BATTERY_COLOR, color).apply(); }
    public int getBatteryColor() { return prefs.getInt(KEY_BATTERY_COLOR, DEFAULT_BATTERY_COLOR); }

    // ---------------------
    // 电环（ring）颜色（新增）
    // ---------------------
    public void setBatteryRingColor(int color) { prefs.edit().putInt(KEY_BATTERY_RING_COLOR, color).apply(); }
    public int getBatteryRingColor() { return prefs.getInt(KEY_BATTERY_RING_COLOR, DEFAULT_BATTERY_RING_COLOR); }

    // ---------------------
    // 背景图片 / 缩放（按图片单独保存缩放）
    // ---------------------
    public void setBackgroundFilename(String filename) { prefs.edit().putString(KEY_BACKGROUND_FILENAME, filename).apply(); }
    public String getBackgroundFilename() { return prefs.getString(KEY_BACKGROUND_FILENAME, DEFAULT_BACKGROUND_FILENAME); }

    /**
     * 为指定文件保存缩放百分比（整数 100..1000）
     */
    public void setBackgroundScale(String filename, int percent) {
        if (filename == null) return;
        prefs.edit().putInt(KEY_BACKGROUND_SCALE + "_" + filename, percent).apply();
    }

    /**
     * 获取指定文件的缩放值，没设置则返回 DEFAULT_BACKGROUND_SCALE
     */
    public int getBackgroundScale(String filename) {
        if (filename == null) return DEFAULT_BACKGROUND_SCALE;
        return prefs.getInt(KEY_BACKGROUND_SCALE + "_" + filename, DEFAULT_BACKGROUND_SCALE);
    }

    /**
     * 便捷：获取当前选中背景的缩放
     */
    public int getBackgroundScale() {
        return getBackgroundScale(getBackgroundFilename());
    }

    // ---------------------
    // 触发表盘更新（通过Broadcast）
    // ---------------------
    public static final String PREF_CHANGED_ACTION = "com.ace77505.watchface.firefly.PREFS_CHANGED";
}