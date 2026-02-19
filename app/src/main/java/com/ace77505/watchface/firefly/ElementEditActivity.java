package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.Nullable;

/**
 * 元素编辑 Activity（已更新：颜色预览边框 & 重置按钮居中）
 */
public class ElementEditActivity extends Activity {

    private static final int REQ_DIRECTION = 1001;

    private PreferencesManager prefs;
    private int elementIndex;

    private TextView titleView;
    private View rowDirection;
    private TextView valueDirection;
    private View rowDistance;
    private TextView valueDistance;
    private View rowSize;
    private TextView valueSize;

    // 颜色预览与容器
    private FrameLayout colorButtonContainer; // id: button_color (FrameLayout)
    private View colorPreviewFill;           // id: color_preview_fill (inner View)

    private Button resetButton;

    private int currentColor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_element_edit);

        prefs = new PreferencesManager(this);
        elementIndex = getIntent().getIntExtra("element_index", 0);

        titleView = findViewById(R.id.edit_title);
        rowDirection = findViewById(R.id.row_direction);
        valueDirection = findViewById(R.id.value_direction);
        rowDistance = findViewById(R.id.row_distance);
        valueDistance = findViewById(R.id.value_distance);
        rowSize = findViewById(R.id.row_size);
        valueSize = findViewById(R.id.value_size);

        colorButtonContainer = findViewById(R.id.button_color);
        colorPreviewFill = findViewById(R.id.color_preview_fill);

        resetButton = findViewById(R.id.button_reset);

        String title = elementIndex == 0 ? "时间配置" : elementIndex == 1 ? "日期配置" : "电量配置";
        titleView.setText(title);

        // 初始化显示值
        float dir = getElementDirection();
        float dist = getElementDistance();
        float size = getElementSize();
        currentColor = getElementColor();

        valueDirection.setText(Math.round(dir) + "°");
        valueDistance.setText(Math.round(dist * 100f) + "%");
        valueSize.setText(String.format("%.2fx", size));

        // 初始化颜色预览（直接设置填充 view 背景色，外层 drawable 提供边框）
        applyColorToPreview(currentColor);

        // 电量元素：方向可编辑（如需禁用请设置 GONE）
        rowDirection.setVisibility(View.VISIBLE);

        // 点击方向：打开 DirectionChooseActivity
        valueDirection.setOnClickListener(v -> {
            if (rowDirection.getVisibility() == View.VISIBLE) {
                Intent intent = new Intent(ElementEditActivity.this, DirectionChooseActivity.class);
                intent.putExtra("current_angle", Math.round(getElementDirection()));
                startActivityForResult(intent, REQ_DIRECTION);
            }
        });

        // 点击距离 / 大小 -> 数字输入
        valueDistance.setOnClickListener(v -> showNumberInputDialog(false));
        valueSize.setOnClickListener(v -> showNumberInputDialog(true));

        // 点击颜色预览容器 -> 打开颜色选择
        colorButtonContainer.setOnClickListener(v -> showColorPicker());

        // 重置按钮：短按提示，长按重置
        resetButton.setOnClickListener(v -> {
            Toast.makeText(ElementEditActivity.this, "请长按以重置该元素配置", Toast.LENGTH_SHORT).show();
        });

        resetButton.setOnLongClickListener(v -> {
            resetElementToDefaults();
            Toast.makeText(ElementEditActivity.this, "该元素已重置为默认值", Toast.LENGTH_SHORT).show();
            updateDisplayedValues();
            sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
            return true;
        });
    }

    private void updateDisplayedValues() {
        float dir = getElementDirection();
        float dist = getElementDistance();
        float size = getElementSize();
        currentColor = getElementColor();

        valueDirection.setText(Math.round(dir) + "°");
        valueDistance.setText(Math.round(dist * 100f) + "%");
        valueSize.setText(String.format("%.2fx", size));

        applyColorToPreview(currentColor);
    }

    private void resetElementToDefaults() {
        if (elementIndex == 0) {
            prefs.setTimeDirection(0f);
            prefs.setTimeDistance(0.18f);
            prefs.setTimeSize(1.0f);
            prefs.setTimeColor(Color.BLACK);
        } else if (elementIndex == 1) {
            prefs.setDateDirection(0f);
            prefs.setDateDistance(0.30f);
            prefs.setDateSize(1.0f);
            prefs.setDateColor(Color.BLACK);
        } else {
            prefs.setBatteryDirection(180f);
            prefs.setBatteryDistance(0.9f);
            prefs.setBatterySize(1.0f);
            prefs.setBatteryColor(Color.WHITE);
            // 电量环保持锁定（不在此处修改）
        }
    }

    /**
     * 颜色选择（仅文本颜色）
     */
    private void showColorPicker() {
        ColorOption[] colors = ColorOption.getPresetColors();
        String[] names = new String[colors.length];
        for (int i = 0; i < colors.length; i++) names[i] = colors[i].getName();

        new AlertDialog.Builder(this)
                .setTitle("选择文本颜色")
                .setItems(names, (dialog, which) -> {
                    ColorOption sel = colors[which];
                    if (sel.isCustom()) {
                        showCustomColorInput();
                    } else {
                        currentColor = sel.getColor();
                        setElementColor(currentColor);
                        applyColorToPreview(currentColor);
                        sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
                    }
                }).show();
    }

    private void showCustomColorInput() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入十六进制颜色");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("#RRGGBB 或 #AARRGGBB");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        builder.setView(input);

        builder.setPositiveButton("确定", (d, w) -> {
            String s = input.getText().toString().trim();
            try {
                int col = Color.parseColor(s);
                currentColor = col;
                setElementColor(currentColor);
                applyColorToPreview(currentColor);
                sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
            } catch (IllegalArgumentException e) {
                Toast.makeText(ElementEditActivity.this, "无效颜色格式", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 数字输入对话框（用于距离与大小）
     */
    private void showNumberInputDialog(boolean isSize) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isSize ? "输入大小 (例如 100 表示 1.00x)" : "输入距离百分比 (例如 90 表示 90%)");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setImeActionLabel("长按重置", EditorInfo.IME_ACTION_DONE);

        // 预填当前值
        if (isSize) {
            float size = getElementSize();
            int display = Math.round(size * 100f);
            input.setText(String.valueOf(display));
        } else {
            float dist = getElementDistance();
            int display = Math.round(dist * 100f);
            input.setText(String.valueOf(display));
        }

        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) s = "0";
            try {
                int n = Integer.parseInt(s);
                if (n < 0) n = 0;
                if (n > 999) n = 999;
                if (isSize) {
                    float newSize = n / 100f;
                    setElementSize(newSize);
                    valueSize.setText(String.format("%.2fx", newSize));
                } else {
                    float newDist = n / 100f;
                    setElementDistance(newDist);
                    valueDistance.setText(Math.round(newDist * 100f) + "%");
                }
                sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
            } catch (NumberFormatException e) {
                Toast.makeText(ElementEditActivity.this, "请输入有效整数", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            // 不保存
        });

        AlertDialog dlg = builder.create();
        dlg.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DIRECTION && resultCode == RESULT_OK && data != null) {
            int angle = data.getIntExtra("selected_angle", 0);
            setElementDirection(angle);
            valueDirection.setText(angle + "°");
            sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
        }
    }

    // helper：按 elementIndex 读写偏好
    private float getElementDirection() {
        if (elementIndex == 0) return prefs.getTimeDirection();
        if (elementIndex == 1) return prefs.getDateDirection();
        return prefs.getBatteryDirection();
    }
    private void setElementDirection(float deg) {
        if (elementIndex == 0) prefs.setTimeDirection(deg);
        else if (elementIndex == 1) prefs.setDateDirection(deg);
        else prefs.setBatteryDirection(deg);
    }

    private float getElementDistance() {
        if (elementIndex == 0) return prefs.getTimeDistance();
        if (elementIndex == 1) return prefs.getDateDistance();
        return prefs.getBatteryDistance();
    }
    private void setElementDistance(float ratio) {
        if (elementIndex == 0) prefs.setTimeDistance(ratio);
        else if (elementIndex == 1) prefs.setDateDistance(ratio);
        else prefs.setBatteryDistance(ratio);
    }

    private float getElementSize() {
        if (elementIndex == 0) return prefs.getTimeSize();
        if (elementIndex == 1) return prefs.getDateSize();
        return prefs.getBatterySize();
    }
    private void setElementSize(float s) {
        if (elementIndex == 0) prefs.setTimeSize(s);
        else if (elementIndex == 1) prefs.setDateSize(s);
        else prefs.setBatterySize(s);
    }

    private int getElementColor() {
        if (elementIndex == 0) return prefs.getTimeColor();
        if (elementIndex == 1) return prefs.getDateColor();
        return prefs.getBatteryColor();
    }
    private void setElementColor(int c) {
        if (elementIndex == 0) prefs.setTimeColor(c);
        else if (elementIndex == 1) prefs.setDateColor(c);
        else prefs.setBatteryColor(c);
    }

    /**
     * 将颜色应用到颜色预览（直接设置填充 view 背景色）
     * 外层容器的 drawable (color_preview_bg) 提供边框，不会被覆盖。
     */
    private void applyColorToPreview(int color) {
        if (colorPreviewFill == null) return;
        colorPreviewFill.setBackgroundColor(color);
    }
}