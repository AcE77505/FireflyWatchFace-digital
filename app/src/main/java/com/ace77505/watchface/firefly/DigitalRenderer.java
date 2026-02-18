package com.ace77505.watchface.firefly;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.text.format.DateFormat;
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

public class DigitalRenderer extends Renderer.CanvasRenderer {
    private Bitmap backgroundBitmap;
    private final Paint timePaint = new Paint();
    private final Paint datePaint = new Paint();
    private final Paint batteryTextPaint = new Paint(); // 新增：数字电池画笔

    // 电量环对象
    private final BatteryRing batteryRing;

    // 设置管理器
    private final PreferencesManager prefsManager;
    private final Context context;
    private final BroadcastReceiver settingsReceiver;

    // 当前颜色值
    private int timeColor = Color.BLACK;
    private int dateColor = Color.BLACK;
    private boolean batteryRingEnabled = true; // 新增：电量环开关状态

    // 时间格式化
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d");

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
        batteryRingEnabled = prefsManager.isBatteryRingEnabled(); // 新增

        // 初始化画笔
        initPaints();

        // 加载背景图片
        loadBackgroundBitmap(this.context);

        // 初始化电量环
        this.batteryRing = new BatteryRing(this.context);

        // 注册设置变化监听
        settingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (PreferencesManager.PREF_CHANGED_ACTION.equals(intent.getAction())) {
                    // 更新颜色值和开关状态
                    timeColor = prefsManager.getTimeColor();
                    dateColor = prefsManager.getDateColor();
                    batteryRingEnabled = prefsManager.isBatteryRingEnabled(); // 新增
                    // 刷新表盘
                    invalidate();
                }
            }
        };

        IntentFilter filter = new IntentFilter(PreferencesManager.PREF_CHANGED_ACTION);
        // Android 13+ 要求为动态注册的接收器显式指定导出属性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(settingsReceiver, filter);
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
        batteryTextPaint.setColor(Color.WHITE); // 暂时使用白色，可根据需要调整
        batteryTextPaint.setTextAlign(Paint.Align.CENTER);
        batteryTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        batteryTextPaint.setStyle(Paint.Style.FILL);
    }

    private void loadBackgroundBitmap(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("119655138_sq.webp");
            backgroundBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            backgroundBitmap = null;
        }
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 绘制背景（图片或纯色）
        drawBackground(canvas, bounds);

        // 根据开关状态绘制电量环
        if (batteryRingEnabled) {
            batteryRing.draw(canvas, bounds);
        }

        // 绘制数字时间
        drawDigitalTime(canvas, bounds, dateTime);

        // 绘制数字电池
        drawBatteryText(canvas, bounds);
    }

    private void drawBackground(Canvas canvas, Rect bounds) {
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            // 绘制背景图片，适配屏幕大小
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    backgroundBitmap,
                    bounds.width(),
                    bounds.height(),
                    true
            );
            canvas.drawBitmap(scaledBitmap, bounds.left, bounds.top, null);

            // 避免内存泄漏，使用后回收临时位图
            if (scaledBitmap != backgroundBitmap) {
                scaledBitmap.recycle();
            }
        } else {
            // 后备方案：白色背景
            canvas.drawColor(Color.WHITE);
        }
    }

    private void drawDigitalTime(Canvas canvas, Rect bounds, ZonedDateTime dateTime) {
        float centerX = bounds.exactCenterX();
        float centerY = bounds.exactCenterY();

        // 更新画笔颜色（确保使用最新设置）
        timePaint.setColor(timeColor);
        datePaint.setColor(dateColor);

        // 计算字体大小（基于屏幕尺寸）- 调整比例让时间稍微上移
        float timeTextSize = bounds.height() * 0.12f; // 时间文字高度占比12%
        float dateTextSize = bounds.height() * 0.05f; // 日期文字高度占比5%

        // 设置时间文字大小
        timePaint.setTextSize(timeTextSize);

        // 格式化并绘制时间 - 位置上移，为数字电池留出空间
        String timeString = dateTime.format(timeFormatter);

        // 计算位置，使时间整体居中，但稍微上移
        Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
        float timeY = centerY - (timeMetrics.ascent + timeMetrics.descent) / 2f - dateTextSize * 0.8f - bounds.height() * 0.02f; // 上移2%
        canvas.drawText(timeString, centerX, timeY, timePaint);

        // 设置日期文字大小
        datePaint.setTextSize(dateTextSize);

        // 格式化并绘制日期 - 位置相应调整
        String dateString = dateTime.format(dateFormatter);
        Paint.FontMetrics dateMetrics = datePaint.getFontMetrics();
        float dateY = centerY + (dateMetrics.descent - dateMetrics.ascent) / 2f + timeTextSize * 0.3f - bounds.height() * 0.02f; // 上移2%
        canvas.drawText(dateString, centerX, dateY, datePaint);
    }

    /**
     * 绘制数字电池
     */
    private void drawBatteryText(Canvas canvas, Rect bounds) {
        float batteryLevel = batteryRing.getBatteryLevel();
        String batteryText = Math.round(batteryLevel * 100) + "%";

        // 设置文字大小 - 较小比例
        float batteryTextSize = bounds.height() * 0.035f;
        batteryTextPaint.setTextSize(batteryTextSize);

        // 根据背景亮度调整文字颜色
        batteryTextPaint.setColor(calculateTextColor());

        // 计算位置 - 放在底部中央
        float centerX = bounds.exactCenterX();
        float bottomY = bounds.bottom - bounds.height() * 0.05f; // 距离底部5%高度

        // 添加阴影提高可读性
        batteryTextPaint.setShadowLayer(3, 0, 0, Color.BLACK);

        canvas.drawText(batteryText, centerX, bottomY, batteryTextPaint);

        // 移除阴影避免影响其他绘制
        batteryTextPaint.setShadowLayer(0, 0, 0, 0);
    }

    /**
     * 计算文字颜色 - 根据当前背景亮度决定使用黑色或白色
     */
    private int calculateTextColor() {
        // 简单起见，这里根据时间颜色取反色
        // 更复杂的实现可以根据背景图片的亮度决定
        int bgColor = timeColor;
        // 计算亮度
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
        if (settingsReceiver != null) {
            try {
                context.unregisterReceiver(settingsReceiver);
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
        }

        // 回收背景图片
        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            backgroundBitmap.recycle();
            backgroundBitmap = null;
        }

        super.onDestroy();
    }
}