package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class PreferencesManager {
    private static final String PREF_NAME = "watchface_prefs";
    private static final String KEY_TIME_COLOR = "time_color";
    private static final String KEY_DATE_COLOR = "date_color";
    private static final String KEY_BATTERY_RING_ENABLED = "battery_ring_enabled"; // 新增

    private final SharedPreferences prefs;

    // 默认颜色（黑色）
    private static final int DEFAULT_COLOR = Color.BLACK;
    // 电量环默认开启
    private static final boolean DEFAULT_BATTERY_RING_ENABLED = true;

    public PreferencesManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setTimeColor(int color) {
        prefs.edit().putInt(KEY_TIME_COLOR, color).apply();
    }

    public int getTimeColor() {
        return prefs.getInt(KEY_TIME_COLOR, DEFAULT_COLOR);
    }

    public void setDateColor(int color) {
        prefs.edit().putInt(KEY_DATE_COLOR, color).apply();
    }

    public int getDateColor() {
        return prefs.getInt(KEY_DATE_COLOR, DEFAULT_COLOR);
    }

    // 新增：电量环开关
    public void setBatteryRingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BATTERY_RING_ENABLED, enabled).apply();
    }

    public boolean isBatteryRingEnabled() {
        return prefs.getBoolean(KEY_BATTERY_RING_ENABLED, DEFAULT_BATTERY_RING_ENABLED);
    }

    // 触发表盘更新（通过SharedPreferences变化监听）
    public static final String PREF_CHANGED_ACTION = "com.ace77505.watchface.firefly.PREFS_CHANGED";
}