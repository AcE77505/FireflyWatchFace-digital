package com.ace77505.watchface.firefly;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.BatteryManager;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.wear.watchface.CanvasType;
import androidx.wear.watchface.Renderer;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DigitalRenderer - 完整版本
 *
 * 功能要点：
 * - 使用 PreferencesManager 中的扩展偏好（方向/距离/大小/颜色）来定位并缩放时间、日期、以及电池文字。
 * - 电量环与电池文字的颜色分离：从 prefs 读取 battery ring color (getBatteryRingColor())
 *   与 battery element color (getBatteryColor()) 并分别应用。
 * - 复用 PolarCoord 与临时数组 coordTmp 避免每帧分配。
 * - 缓存并按需缩放 backgroundBitmap。
 * - 通过注册 ACTION_BATTERY_CHANGED 的广播缓存电量（cachedBatteryLevel），避免每帧查询系统。
 * - 在 Preferences 变化时（PREF_CHANGED_ACTION）重新加载偏好并 invalidate()。
 *
 * 注意：
 * - 这个类依赖 PreferencesManager 已扩展的方法：
 *     getTimeDirection(), getTimeDistance(), getTimeSize()
 *     getDateDirection(), getDateDistance(), getDateSize()
 *     getBatteryDirection(), getBatteryDistance(), getBatterySize()
 *     getBatteryColor()            // 电池元素颜色（文本）
 *     getBatteryRingColor()        // 电量环颜色（环）
 *   如果 PreferencesManager 尚未包含 getBatteryRingColor()，请添加对应的键与 getter/setter。
 */
public class DigitalRenderer extends Renderer.CanvasRenderer {
    // 背景位图与缓存缩放图
    private Bitmap backgroundBitmap;
    private Bitmap cachedScaledBackground = null;
    private int cachedBackgroundWidth = -1;
    private int cachedBackgroundHeight = -1;

    // 画笔
    private final Paint timePaint = new Paint();
    private final Paint datePaint = new Paint();
    private final Paint batteryTextPaint = new Paint();

    // 电量环
    private final BatteryRing batteryRing;

    // 偏好/上下文/接收器
    private final PreferencesManager prefsManager;
    private final Context context;
    private final BroadcastReceiver settingsReceiver;

    // 当前颜色值与开关
    private int timeColor = Color.BLACK;
    private int dateColor = Color.BLACK;
    private boolean batteryRingEnabled = true;

    // 时间/日期格式
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d");

    // 复用的极坐标实例与临时数组（避免每帧分配）
    private final PolarCoord polar = new PolarCoord(0f, 0f, 1f);
    private final float[] coordTmp = new float[2];

    // 缓存电量（由广播更新）
    private volatile float cachedBatteryLevel = 0.75f;

    // 电量广播接收器（仅一次注册）
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    cachedBatteryLevel = (float) level / (float) scale;
                }
            }
        }
    };

    // 元素参数（从 prefs 读取并缓存）
    private float timeDirDeg;
    private float timeDistRatio;
    private float timeSizeScale;

    private float dateDirDeg;
    private float dateDistRatio;
    private float dateSizeScale;

    private float batteryDirDeg;
    private float batteryDistRatio;
    private float batterySizeScale;

    // 颜色分离：电池文字颜色 (element) 与 电量环颜色 (ring)
    private int batteryElementColor = Color.WHITE;
    private int batteryRingColor = Color.parseColor("#FFA04A"); // 默认

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public DigitalRenderer(
            SurfaceHolder surfaceHolder,
            CurrentUserStyleRepository currentUserStyleRepository,
            WatchState watchState,
            long frameDelayMillis,
            Context context
    ) {
        super(
                surfaceHolder,
                currentUserStyleRepository,
                watchState,
                CanvasType.HARDWARE,
                frameDelayMillis
        );

        this.context = context.getApplicationContext();

        // 初始化 prefs 管理器
        prefsManager = new PreferencesManager(this.context);

        // 初始化画笔
        initPaints();

        // 加载背景图（优先使用 openFd）
        loadBackgroundBitmap(this.context);

        // 初始化电量环实例
        this.batteryRing = new BatteryRing(this.context);

        // 加载基础设置（颜色、开关）
        timeColor = prefsManager.getTimeColor();
        dateColor = prefsManager.getDateColor();
        batteryRingEnabled = prefsManager.isBatteryRingEnabled();

        // 加载元素偏好（方向/距离/大小/颜色 等），并把电量环视觉配置应用到 batteryRing 实例
        loadElementPrefs();

        // 注册设置变化监听：在 prefs 改变时重新读取偏好并触发 invalidate()
        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (PreferencesManager.PREF_CHANGED_ACTION.equals(intent.getAction())) {
                    // 重新加载颜色、开关、元素偏好
                    timeColor = prefsManager.getTimeColor();
                    dateColor = prefsManager.getDateColor();
                    batteryRingEnabled = prefsManager.isBatteryRingEnabled();
                    loadElementPrefs();
                    invalidate(); // 请求重绘
                }
            }
        };

        IntentFilter filter = new IntentFilter(PreferencesManager.PREF_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(settingsReceiver, filter);
        }

        // 注册电量广播（用于缓存电量值）
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(batteryReceiver, batteryFilter);
        }
    }

    private void initPaints() {
        // 时间画笔
        timePaint.setAntiAlias(true);
        timePaint.setDither(true);
        timePaint.setColor(timeColor);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timePaint.setStyle(Paint.Style.FILL);

        // 日期画笔
        datePaint.setAntiAlias(true);
        datePaint.setDither(true);
        datePaint.setColor(dateColor);
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        datePaint.setStyle(Paint.Style.FILL);

        // 数字电池画笔
        batteryTextPaint.setAntiAlias(true);
        batteryTextPaint.setDither(true);
        batteryTextPaint.setColor(batteryElementColor);
        batteryTextPaint.setTextAlign(Paint.Align.CENTER);
        batteryTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        batteryTextPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 从 assets 加载背景图片（openFd 优先）
     */
    private void loadBackgroundBitmap(Context context) {
        final String assetName = "119655138_sq.webp";
        backgroundBitmap = null;

        try {
            try (AssetFileDescriptor afd = context.getAssets().openFd(assetName)) {
                try (InputStream is = afd.createInputStream()) {
                    backgroundBitmap = BitmapFactory.decodeStream(is);
                }
            }
        } catch (IOException e) {
            try (InputStream is = context.getAssets().open(assetName)) {
                backgroundBitmap = BitmapFactory.decodeStream(is);
            } catch (IOException ex) {
                ex.printStackTrace();
                backgroundBitmap = null;
            }
        }
    }

    /**
     * 从 prefs 读取并缓存元素参数；同时把可视化配置应用到 batteryRing 实例。
     *
     * 说明：这里将电量环颜色与电池文字颜色分离：
     * - batteryRingColor 来自 prefsManager.getBatteryRingColor()
     * - batteryElementColor 来自 prefsManager.getBatteryColor()
     *
     * 电量环的 inset 与 厚度缩放仍沿用 battery element 的 distance/size 偏好（便于快速起步）；
     * 若你希望分别独立控制它们，可以在 PreferencesManager 再新增键并在此读取。
     */
    private void loadElementPrefs() {
        // 时间
        timeDirDeg = prefsManager.getTimeDirection();
        timeDistRatio = prefsManager.getTimeDistance();
        timeSizeScale = prefsManager.getTimeSize();

        // 日期
        dateDirDeg = prefsManager.getDateDirection();
        dateDistRatio = prefsManager.getDateDistance();
        dateSizeScale = prefsManager.getDateSize();

        // 电池（元素）
        batteryDirDeg = prefsManager.getBatteryDirection();
        batteryDistRatio = prefsManager.getBatteryDistance();
        batterySizeScale = prefsManager.getBatterySize();

        // 颜色（分离）
        batteryElementColor = prefsManager.getBatteryColor();
        // 需要 PreferencesManager 提供此方法；若不存在，请新增对应键值与 getter/setter
        try {
            batteryRingColor = prefsManager.getBatteryRingColor();
        } catch (Exception e) {
            // 兼容性：若 prefsManager 尚未实现 getBatteryRingColor(), 使用默认 ring color
            batteryRingColor = Color.parseColor("#FFA04A");
        }

        // 将视觉参数应用到 batteryRing 实例：
        // 这里把 batteryDistRatio 用作 insetRatio（0..1），batterySizeScale 用作厚度缩放
        batteryRing.setConfig(
                /* insetRatio = */ Math.max(0.1f, Math.min(1.0f, batteryDistRatio <= 0f ? 0.96f : batteryDistRatio)),
                /* sizeScale = */ Math.max(0.1f, batterySizeScale),
                /* color = */ batteryRingColor
        );
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 更新复用 polar
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;
        polar.update(cx, cy, radius);

        // 绘制背景（缓存缩���）
        drawBackgroundCached(canvas, bounds);

        // 绘制电量环（如果已启用）
        if (batteryRingEnabled) {
            batteryRing.draw(canvas, polar, coordTmp, cachedBatteryLevel);
        }

        // 绘制时间/日期（使用 element prefs）
        drawDigitalTime(canvas, polar, dateTime);

        // 绘制电池文本（使用 element prefs）
        drawBatteryText(canvas, polar);
    }

    private void drawBackgroundCached(Canvas canvas, Rect bounds) {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            if (cachedScaledBackground == null ||
                    cachedBackgroundWidth != bounds.width() ||
                    cachedBackgroundHeight != bounds.height()) {

                if (cachedScaledBackground != null && !cachedScaledBackground.isRecycled()) {
                    cachedScaledBackground.recycle();
                }
                cachedScaledBackground = Bitmap.createScaledBitmap(
                        backgroundBitmap, bounds.width(), bounds.height(), true
                );
                cachedBackgroundWidth = bounds.width();
                cachedBackgroundHeight = bounds.height();
            }
            canvas.drawBitmap(cachedScaledBackground, bounds.left, bounds.top, null);
        } else {
            // 备用白底
            canvas.drawColor(Color.WHITE);
        }
    }

    private void drawDigitalTime(Canvas canvas, PolarCoord polar, ZonedDateTime dateTime) {
        // 更新画笔颜色
        timePaint.setColor(timeColor);
        datePaint.setColor(dateColor);

        // 基础字体大小
        float baseTimeTextSize = polar.getMaxRadius() * 2f * 0.12f;
        float baseDateTextSize = polar.getMaxRadius() * 2f * 0.05f;

        // 应用用户配置的大小比例
        timePaint.setTextSize(baseTimeTextSize * timeSizeScale);
        datePaint.setTextSize(baseDateTextSize * dateSizeScale);

        // 格式化文字
        String timeString = dateTime.format(timeFormatter);
        String dateString = dateTime.format(dateFormatter);

        // 绘制时间
        if (timeDistRatio <= 0f) {
            // 距离为 0 -> 居中显示（无视方向）
            float timeX = polar.getCenterX();
            float timeY = polar.getCenterY();
            Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
            float drawY = timeY - (timeMetrics.ascent + timeMetrics.descent) / 2f;
            canvas.drawText(timeString, timeX, drawY, timePaint);
        } else {
            // 用户角度定义为：0 = 12 点，顺时针为正
            float userAngle = normalizeAngle(timeDirDeg);
            float polarAngle = userAngle - 90f; // 转换为 PolarCoord 约定
            polar.toCartesianRatioOut(polarAngle, timeDistRatio, coordTmp);
            float timeX = coordTmp[0];
            float timeYcenter = coordTmp[1];
            Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
            float timeY = timeYcenter - (timeMetrics.ascent + timeMetrics.descent) / 2f;
            canvas.drawText(timeString, timeX, timeY, timePaint);
        }

        // 绘制日期
        if (dateDistRatio <= 0f) {
            float dateX = polar.getCenterX();
            float dateY = polar.getCenterY();
            Paint.FontMetrics dateMetrics = datePaint.getFontMetrics();
            float drawY = dateY - (dateMetrics.ascent + dateMetrics.descent) / 2f;
            canvas.drawText(dateString, dateX, drawY, datePaint);
        } else {
            float userAngle = normalizeAngle(dateDirDeg);
            float polarAngle = userAngle - 90f;
            polar.toCartesianRatioOut(polarAngle, dateDistRatio, coordTmp);
            float dateX = coordTmp[0];
            float dateYcenter = coordTmp[1];
            Paint.FontMetrics dateMetrics = datePaint.getFontMetrics();
            float dateY = dateYcenter - (dateMetrics.ascent + dateMetrics.descent) / 2f;
            canvas.drawText(dateString, dateX, dateY, datePaint);
        }
    }

    private void drawBatteryText(Canvas canvas, PolarCoord polar) {
        float batteryLevel = cachedBatteryLevel;
        String batteryText = Math.round(batteryLevel * 100) + "%";

        float baseBatteryTextSize = polar.getMaxRadius() * 2f * 0.035f;
        batteryTextPaint.setTextSize(baseBatteryTextSize * batterySizeScale);

        // 使用独立的电池元素颜色（文本）
        batteryTextPaint.setColor(batteryElementColor);

        if (batteryDistRatio <= 0f) {
            float batteryX = polar.getCenterX();
            float batteryYcenter = polar.getCenterY();
            Paint.FontMetrics bm = batteryTextPaint.getFontMetrics();
            float batteryY = batteryYcenter - (bm.ascent + bm.descent) / 2f;
            canvas.drawText(batteryText, batteryX, batteryY, batteryTextPaint);
        } else {
            float userAngle = normalizeAngle(batteryDirDeg);
            float polarAngle = userAngle - 90f;
            polar.toCartesianRatioOut(polarAngle, batteryDistRatio, coordTmp);
            float batteryX = coordTmp[0];
            float batteryYcenter = coordTmp[1];
            Paint.FontMetrics bm = batteryTextPaint.getFontMetrics();
            float batteryY = batteryYcenter - (bm.ascent + bm.descent) / 2f;

            // 添加阴影提高可读性
            batteryTextPaint.setShadowLayer(3, 0, 0, Color.BLACK);
            canvas.drawText(batteryText, batteryX, batteryY, batteryTextPaint);
            batteryTextPaint.setShadowLayer(0, 0, 0, 0);
        }
    }

    /**
     * 确保角度在 [0,360) 范围内
     */
    private float normalizeAngle(float deg) {
        float a = deg % 360f;
        if (a < 0f) a += 360f;
        return a;
    }

    @Override
    public void renderHighlightLayer(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 暂不需要高亮层
    }

    @Override
    public void onDestroy() {
        // 取消注册接收器
        try {
            if (settingsReceiver != null) {
                context.unregisterReceiver(settingsReceiver);
            }
        } catch (IllegalArgumentException ignored) {}

        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {}

        // 回收背景图与缓存图
        if (cachedScaledBackground != null && !cachedScaledBackground.isRecycled()) {
            cachedScaledBackground.recycle();
            cachedScaledBackground = null;
        }
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
            backgroundBitmap = null;
        }

        super.onDestroy();
    }
}