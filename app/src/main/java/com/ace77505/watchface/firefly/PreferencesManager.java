package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class PreferencesManager {
    private static final String PREF_NAME = "watchface_prefs";
    private static final String KEY_TIME_COLOR = "time_color";
    private static final String KEY_DATE_COLOR = "date_color";

    private final SharedPreferences prefs;

    // 默认颜色（黑色）
    private static final int DEFAULT_COLOR = Color.BLACK;

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

    // 触发表盘更新（通过SharedPreferences变化监听）
    public static final String PREF_CHANGED_ACTION = "com.ace77505.watchface.firefly.PREFS_CHANGED";
}