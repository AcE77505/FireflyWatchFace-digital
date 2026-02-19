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
 * DigitalRenderer - 使用 srcRect->dstRect 绘制背景以避免中间大图分配（更稳健）
 */
public class DigitalRenderer extends Renderer.CanvasRenderer {
    // 原始背景位图（直接从 assets 解码并保留）
    private Bitmap backgroundBitmap;

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

    // 背景当前使用的文件名与缩放（从 prefs 读取）
    private String backgroundFilename;
    private int backgroundScalePercent;

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
            try {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (level >= 0 && scale > 0) {
                        cachedBatteryLevel = (float) level / (float) scale;
                    }
                }
            } catch (Exception ignored) { }
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

    // 电池文本颜色（独立）
    private int batteryElementColor = Color.WHITE;

    // 电量环默认锁定颜色（硬编码为原始默认）
    private static final int LOCKED_BATTERY_RING_COLOR = Color.parseColor("#FFA04A");
    private static final float LOCKED_BATTERY_RING_INSET = 0.97f; // 97% 内缩（固定）
    private static final float LOCKED_BATTERY_RING_SIZE_SCALE = 1.0f; // 厚度不缩放（固定）

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

        prefsManager = new PreferencesManager(this.context);

        initPaints();

        this.batteryRing = new BatteryRing(this.context);

        try {
            timeColor = prefsManager.getTimeColor();
            dateColor = prefsManager.getDateColor();
            batteryRingEnabled = prefsManager.isBatteryRingEnabled();
            loadElementPrefs();
        } catch (Throwable t) {
            t.printStackTrace();
            backgroundBitmap = null;
        }

        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    if (PreferencesManager.PREF_CHANGED_ACTION.equals(intent.getAction())) {
                        timeColor = prefsManager.getTimeColor();
                        dateColor = prefsManager.getDateColor();
                        batteryRingEnabled = prefsManager.isBatteryRingEnabled();
                        loadElementPrefs();
                        invalidate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        IntentFilter filter = new IntentFilter(PreferencesManager.PREF_CHANGED_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.context.registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                this.context.registerReceiver(settingsReceiver, filter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.context.registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                this.context.registerReceiver(batteryReceiver, batteryFilter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPaints() {
        timePaint.setAntiAlias(true);
        timePaint.setDither(true);
        timePaint.setColor(timeColor);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timePaint.setStyle(Paint.Style.FILL);

        datePaint.setAntiAlias(true);
        datePaint.setDither(true);
        datePaint.setColor(dateColor);
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        datePaint.setStyle(Paint.Style.FILL);

        batteryTextPaint.setAntiAlias(true);
        batteryTextPaint.setDither(true);
        batteryTextPaint.setColor(batteryElementColor);
        batteryTextPaint.setTextAlign(Paint.Align.CENTER);
        batteryTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        batteryTextPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 从 prefs 读取 element 参数与背景文件/缩放并加载背景原图（不在此处做大图缩放）
     */
    private void loadElementPrefs() {
        try {
            // 时间
            timeDirDeg = prefsManager.getTimeDirection();
            timeDistRatio = prefsManager.getTimeDistance();
            timeSizeScale = prefsManager.getTimeSize();

            // 日期
            dateDirDeg = prefsManager.getDateDirection();
            dateDistRatio = prefsManager.getDateDistance();
            dateSizeScale = prefsManager.getDateSize();

            // 电池
            batteryDirDeg = prefsManager.getBatteryDirection();
            batteryDistRatio = prefsManager.getBatteryDistance();
            batterySizeScale = prefsManager.getBatterySize();
            batteryElementColor = prefsManager.getBatteryColor();

            batteryRing.setConfig(
                    LOCKED_BATTERY_RING_INSET,
                    LOCKED_BATTERY_RING_SIZE_SCALE,
                    LOCKED_BATTERY_RING_COLOR
            );

            String filename = prefsManager.getBackgroundFilename();
            int scalePct = prefsManager.getBackgroundScale(filename);

            backgroundFilename = filename;
            backgroundScalePercent = scalePct;

            // 直接加载原始背景位图（small memory), 解码失败会回退到默认或 null
            loadBackgroundBitmap(filename);
        } catch (Exception e) {
            e.printStackTrace();
            backgroundBitmap = null;
            backgroundFilename = null;
            backgroundScalePercent = PreferencesManager.DEFAULT_BACKGROUND_SCALE;
        }
    }

    /**
     * 从 assets 加载背景图片（原图）
     * 若失败，尝试回退到 DEFAULT_BACKGROUND_FILENAME
     */
    private void loadBackgroundBitmap(String filename) {
        backgroundBitmap = null;
        if (filename == null) return;

        try {
            // Prefer open() which works for compressed assets; openFd may fail for compressed assets
            try (InputStream is = context.getAssets().open(filename)) {
                backgroundBitmap = BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // fallback to default if not same
            if (!PreferencesManager.DEFAULT_BACKGROUND_FILENAME.equals(filename)) {
                try (InputStream is2 = context.getAssets().open(PreferencesManager.DEFAULT_BACKGROUND_FILENAME)) {
                    backgroundBitmap = BitmapFactory.decodeStream(is2);
                    backgroundFilename = PreferencesManager.DEFAULT_BACKGROUND_FILENAME;
                    backgroundScalePercent = prefsManager.getBackgroundScale(backgroundFilename);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    backgroundBitmap = null;
                }
            } else {
                backgroundBitmap = null;
            }
        }
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        try {
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;
            polar.update(cx, cy, radius);

            drawBackgroundDirect(canvas, bounds);

            if (batteryRingEnabled) {
                batteryRing.draw(canvas, polar, coordTmp, cachedBatteryLevel);
            }

            drawDigitalTime(canvas, polar, dateTime);
            drawBatteryText(canvas, polar);
        } catch (Exception e) {
            e.printStackTrace();
            canvas.drawColor(Color.WHITE);
        }
    }

    /**
     * 使用 bitmap 的 srcRect -> dstRect 绘制背景（以图片中心为基准裁切），避免创建大中间 Bitmap
     * 逻辑：
     *  - scalePct >= 100（我们保证用户输入范围），若 scalePct == 100 则使用整张图片 src
     *  - 当 scalePct > 100 时，裁切出原图中心区域：srcW = origW * 100 / scalePct, srcH = origH * 100 / scalePct
     *  - 将 srcRect 绘制到 dstRect(bounds)，实现“中心放大到 scalePct% 然后填满表盘”
     */
    private void drawBackgroundDirect(Canvas canvas, Rect bounds) {
        if (backgroundBitmap == null || backgroundBitmap.isRecycled()) {
            canvas.drawColor(Color.WHITE);
            return;
        }

        try {
            int bw = backgroundBitmap.getWidth();
            int bh = backgroundBitmap.getHeight();
            if (bw <= 0 || bh <= 0) {
                canvas.drawColor(Color.WHITE);
                return;
            }

            int pct = Math.max(1, backgroundScalePercent); // defensive, though prefs constrain to >=100
            // 当 pct == 100 时 srcW = bw, srcH = bh -> 映射整图到 bounds
            int srcW = Math.max(1, bw * 100 / pct);
            int srcH = Math.max(1, bh * 100 / pct);

            // 防止 src 大于原图（在极端数值或 pct<100 情况）
            if (srcW > bw) srcW = bw;
            if (srcH > bh) srcH = bh;

            int left = Math.max(0, (bw - srcW) / 2);
            int top = Math.max(0, (bh - srcH) / 2);

            Rect src = new Rect(left, top, left + srcW, top + srcH);
            Rect dst = new Rect(bounds.left, bounds.top, bounds.right, bounds.bottom);

            // Draw bitmap section -> stretch to dest bounds (efficient, no huge intermediate allocation)
            canvas.drawBitmap(backgroundBitmap, src, dst, null);
        } catch (Exception e) {
            e.printStackTrace();
            // fallback to draw whole bitmap stretched (最保险)
            try {
                Rect dst = new Rect(bounds.left, bounds.top, bounds.right, bounds.bottom);
                canvas.drawBitmap(backgroundBitmap, null, dst, null);
            } catch (Exception ex) {
                ex.printStackTrace();
                canvas.drawColor(Color.WHITE);
            }
        }
    }

    private void drawDigitalTime(Canvas canvas, PolarCoord polar, ZonedDateTime dateTime) {
        timePaint.setColor(timeColor);
        datePaint.setColor(dateColor);

        float baseTimeTextSize = polar.getMaxRadius() * 2f * 0.12f;
        float baseDateTextSize = polar.getMaxRadius() * 2f * 0.05f;

        timePaint.setTextSize(baseTimeTextSize * timeSizeScale);
        datePaint.setTextSize(baseDateTextSize * dateSizeScale);

        String timeString = dateTime.format(timeFormatter);
        String dateString = dateTime.format(dateFormatter);

        if (timeDistRatio <= 0f) {
            float timeX = polar.getCenterX();
            float timeY = polar.getCenterY();
            Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
            float drawY = timeY - (timeMetrics.ascent + timeMetrics.descent) / 2f;
            canvas.drawText(timeString, timeX, drawY, timePaint);
        } else {
            float userAngle = normalizeAngle(timeDirDeg);
            float polarAngle = userAngle - 90f;
            polar.toCartesianRatioOut(polarAngle, timeDistRatio, coordTmp);
            float timeX = coordTmp[0];
            float timeYcenter = coordTmp[1];
            Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
            float timeY = timeYcenter - (timeMetrics.ascent + timeMetrics.descent) / 2f;
            canvas.drawText(timeString, timeX, timeY, timePaint);
        }

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
            batteryTextPaint.setShadowLayer(3, 0, 0, Color.BLACK);
            canvas.drawText(batteryText, batteryX, batteryY, batteryTextPaint);
            batteryTextPaint.setShadowLayer(0, 0, 0, 0);
        }
    }

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
        try {
            if (settingsReceiver != null) {
                context.unregisterReceiver(settingsReceiver);
            }
        } catch (IllegalArgumentException ignored) {}

        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {}

        try {
            if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
                backgroundBitmap.recycle();
                backgroundBitmap = null;
            }
        } catch (Exception ignored) {}

        super.onDestroy();
    }
}