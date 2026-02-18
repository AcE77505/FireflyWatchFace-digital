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
 * DigitalRenderer：性能优化版本（加载 assets 优先使用 openFd）
 * - 复用 PolarCoord（update）避免每帧 new
 * - toCartesian*Out + coordTmp 避免分配 PointF
 * - 缓存并重用 scaled background（只在 bounds 尺寸变化时缩放）
 * - 优先尝试使用 AssetManager.openFd(...) 加载未压缩的 asset（可更高效），若失败再回退到 open(...)
 * - 注册 ACTION_BATTERY_CHANGED 广播更新 cachedBatteryLevel（避免每帧系统查询）
 * - 使用 BatteryRing.draw(..., batteryLevel) 接口避免重复查询
 */
public class DigitalRenderer extends Renderer.CanvasRenderer {
    private Bitmap backgroundBitmap;
    private Bitmap cachedScaledBackground = null;
    private int cachedBackgroundWidth = -1;
    private int cachedBackgroundHeight = -1;

    private final Paint timePaint = new Paint();
    private final Paint datePaint = new Paint();
    private final Paint batteryTextPaint = new Paint(); // 数字电池画笔

    // 电量环对象
    private final BatteryRing batteryRing;

    // 设置管理器
    private final PreferencesManager prefsManager;
    private final Context context;
    private final BroadcastReceiver settingsReceiver;

    // 当前颜色值
    private int timeColor = Color.BLACK;
    private int dateColor = Color.BLACK;
    private boolean batteryRingEnabled = true; // 电量环开关状态

    // 时间格式化
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d");

    // 复用的极坐标实例与临时数组（避免每帧分配）
    private final PolarCoord polar = new PolarCoord(0f, 0f, 1f);
    private final float[] coordTmp = new float[2];

    // 缓存电量（由广播接收器更新），volatile 保证跨线程更新安全
    private volatile float cachedBatteryLevel = 0.75f;

    // 电量广播接收器（只注册一次）
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

        // 初始化设置管理器
        prefsManager = new PreferencesManager(this.context);

        // 加载保存的颜色和开关状态
        timeColor = prefsManager.getTimeColor();
        dateColor = prefsManager.getDateColor();
        batteryRingEnabled = prefsManager.isBatteryRingEnabled();

        // 初始化画笔
        initPaints();

        // 加载背景图片（优先尝试 openFd）
        loadBackgroundBitmap(this.context);

        // 初始化电量环
        this.batteryRing = new BatteryRing(this.context);

        // 注册设置变化监听
        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (PreferencesManager.PREF_CHANGED_ACTION.equals(intent.getAction())) {
                    // 更新颜色值和开关状态
                    timeColor = prefsManager.getTimeColor();
                    dateColor = prefsManager.getDateColor();
                    batteryRingEnabled = prefsManager.isBatteryRingEnabled();
                    // 刷新表盘
                    invalidate();
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
        // 时间画笔 - 大号、粗体
        timePaint.setAntiAlias(true);
        timePaint.setDither(true);
        timePaint.setColor(timeColor);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timePaint.setStyle(Paint.Style.FILL);

        // 日期画笔 - 中号、普通
        datePaint.setAntiAlias(true);
        datePaint.setDither(true);
        datePaint.setColor(dateColor);
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        datePaint.setStyle(Paint.Style.FILL);

        // 数字电池画笔 - 小号、粗体
        batteryTextPaint.setAntiAlias(true);
        batteryTextPaint.setDither(true);
        batteryTextPaint.setColor(Color.WHITE); // 初始颜色
        batteryTextPaint.setTextAlign(Paint.Align.CENTER);
        batteryTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        batteryTextPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 加载背景图片：优先尝试 AssetManager.openFd（需要 asset 在 APK 中未压缩），
     * 若 openFd 或读取失败则回退到 assets.open(...) 流式加载。
     */
    private void loadBackgroundBitmap(Context context) {
        final String assetName = "119655138_sq.webp";
        backgroundBitmap = null;

        // 尝试 openFd -> createInputStream -> decodeStream（兼容且稳健）
        try {
            try (AssetFileDescriptor afd = context.getAssets().openFd(assetName)) {
                try (InputStream is = afd.createInputStream()) {
                    backgroundBitmap = BitmapFactory.decodeStream(is);
                }
            }
        } catch (IOException e) {
            // openFd 不可用或读取失败（通常 asset 被压缩），回退到流式加载
            try (InputStream is = context.getAssets().open(assetName)) {
                backgroundBitmap = BitmapFactory.decodeStream(is);
            } catch (IOException ex) {
                ex.printStackTrace();
                backgroundBitmap = null;
            }
        }
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 更新复用 polar（不 new）
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;
        polar.update(cx, cy, radius);

        // 绘制背景（只在尺寸变化时重新缩放并缓存）
        drawBackgroundCached(canvas, bounds);

        // 根据开关状态绘制电量环，使用 cachedBatteryLevel：避免每帧系统查询
        if (batteryRingEnabled) {
            batteryRing.draw(canvas, polar, coordTmp, cachedBatteryLevel);
        }

        // 绘制数字时间
        drawDigitalTime(canvas, polar, dateTime);

        // 绘制数字电池（使用 cachedBatteryLevel）
        drawBatteryText(canvas, polar);
    }

    private void drawBackgroundCached(Canvas canvas, Rect bounds) {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            if (cachedScaledBackground == null ||
                    cachedBackgroundWidth != bounds.width() ||
                    cachedBackgroundHeight != bounds.height()) {

                // 仅在尺寸变化时重新缩放并替换缓存
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
            canvas.drawColor(Color.WHITE);
        }
    }

    private void drawDigitalTime(Canvas canvas, PolarCoord polar, ZonedDateTime dateTime) {
        // 更新画笔颜色（确保使用最新设置）
        timePaint.setColor(timeColor);
        datePaint.setColor(dateColor);

        // 计算字体大小（基于表盘尺寸）
        float timeTextSize = polar.getMaxRadius() * 2f * 0.12f; // 等价于 bounds.height() * 0.12f
        float dateTextSize = polar.getMaxRadius() * 2f * 0.05f;

        // 设置时间文字大小
        timePaint.setTextSize(timeTextSize);

        // 格式化并绘制时间
        String timeString = dateTime.format(timeFormatter);

        // 时间：顶端偏上（角度 -90），比例 0.18R
        polar.toCartesianRatioOut(-90f, 0.18f, coordTmp);
        float timeX = coordTmp[0];
        float timeYcenter = coordTmp[1];
        Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
        float timeY = timeYcenter - (timeMetrics.ascent + timeMetrics.descent) / 2f;
        canvas.drawText(timeString, timeX, timeY, timePaint);

        // 日期
        datePaint.setTextSize(dateTextSize);
        String dateString = dateTime.format(dateFormatter);
        Paint.FontMetrics dateMetrics = datePaint.getFontMetrics();
        polar.toCartesianRatioOut(-90f, 0.30f, coordTmp);
        float dateX = coordTmp[0];
        float dateYcenter = coordTmp[1];
        float dateY = dateYcenter - (dateMetrics.ascent + dateMetrics.descent) / 2f;
        canvas.drawText(dateString, dateX, dateY, datePaint);
    }

    /**
     * 绘制数字电池（使用 cachedBatteryLevel）
     */
    private void drawBatteryText(Canvas canvas, PolarCoord polar) {
        float batteryLevel = cachedBatteryLevel;
        String batteryText = Math.round(batteryLevel * 100) + "%";

        // 设置文字大小 - 较小比例
        float batteryTextSize = polar.getMaxRadius() * 2f * 0.035f;
        batteryTextPaint.setTextSize(batteryTextSize);

        // 根据时间颜色计算文字颜色（简单启发式）
        batteryTextPaint.setColor(calculateTextColor());

        // 使用极坐标将电池文字放在底部中央（角度 90，接近边缘）
        polar.toCartesianRatioOut(90f, 0.9f, coordTmp);
        float batteryX = coordTmp[0];
        float batteryYcenter = coordTmp[1];

        // 添加阴影提高可读性
        batteryTextPaint.setShadowLayer(3, 0, 0, Color.BLACK);

        // 调整基线使文本以 batteryPos 为中心点
        Paint.FontMetrics bm = batteryTextPaint.getFontMetrics();
        float batteryY = batteryYcenter - (bm.ascent + bm.descent) / 2f;

        canvas.drawText(batteryText, batteryX, batteryY, batteryTextPaint);

        // 移除阴影避免影响其他绘制
        batteryTextPaint.setShadowLayer(0, 0, 0, 0);
    }

    /**
     * 计算文字颜色 - 根据当前时间颜色亮度决定使用黑色或白色
     */
    private int calculateTextColor() {
        int bgColor = timeColor;
        double luminance = (0.299 * Color.red(bgColor) +
                0.587 * Color.green(bgColor) +
                0.114 * Color.blue(bgColor)) / 255;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    @Override
    public void renderHighlightLayer(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 暂不需要高亮层
    }

    @Override
    public void onDestroy() {
        // 取消注册广播接收器
        try {
            if (settingsReceiver != null) {
                context.unregisterReceiver(settingsReceiver);
            }
        } catch (IllegalArgumentException ignored) {}

        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {}

        // 回收背景图片与缓存缩放图
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