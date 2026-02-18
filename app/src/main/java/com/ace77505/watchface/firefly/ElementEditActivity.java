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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * 元素编辑（无滑动条版本）
 *
 * 调整说明：
 * - 不再显示或编辑电量环颜色（电量环保持默认颜色且不可修改）
 * - 电量配置仅影响数字电量（方向/距离/大小/文本颜色）
 */
public class ElementEditActivity extends Activity {

    private PreferencesManager prefs;
    private int elementIndex;

    private TextView titleView;
    private LinearLayout rowDirection;
    private TextView valueDirection;
    private LinearLayout rowDistance;
    private TextView valueDistance;
    private LinearLayout rowSize;
    private TextView valueSize;
    private Button colorButton;
    private Button resetButton;

    // 临时颜色变量（只用于元素文本颜色）
    private int currentColor;

    // request code
    private static final int REQ_DIRECTION = 1001;

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
        colorButton = findViewById(R.id.button_color);
        resetButton = findViewById(R.id.button_reset);

        String title = elementIndex == 0 ? "时间配置" : elementIndex == 1 ? "日期配置" : "电量配置";
        titleView.setText(title);

        // 读取当前设置并显示
        float dir = getElementDirection();
        float dist = getElementDistance();
        float size = getElementSize();
        currentColor = getElementColor();

        // 显示
        valueDirection.setText(Math.round(dir) + "°");
        valueDistance.setText(Math.round(dist * 100f) + "%");
        valueSize.setText(String.format("%.2fx", size));

        colorButton.setBackgroundColor(currentColor);

        // 电量元素：方向可编辑（你之前要求允许电量方向可修改），但电量环本身不允许修改
        // 如果你之前希望电量方向不可改，请把下面行设置为 GONE
        rowDirection.setVisibility(View.VISIBLE);

        // 点击事件（方向：打开 DirectionChooseActivity）
        valueDirection.setOnClickListener(v -> {
            // 仅当 direction 行可见时才允许点击
            if (rowDirection.getVisibility() == View.VISIBLE) {
                Intent intent = new Intent(ElementEditActivity.this, DirectionChooseActivity.class);
                // 传入当前角度
                intent.putExtra("current_angle", Math.round(getElementDirection()));
                startActivityForResult(intent, REQ_DIRECTION);
            }
        });

        // 距离/大小：打开数字输入对话框
        valueDistance.setOnClickListener(v -> showNumberInputDialog(/*isSize=*/ false));
        valueSize.setOnClickListener(v -> showNumberInputDialog(/*isSize=*/ true));

        // 颜色选择（文本）
        colorButton.setOnClickListener(v -> showColorPicker());

        // 重置按钮：短按提示，长按重置该元素的所有配置
        resetButton.setOnClickListener(v -> {
            Toast.makeText(ElementEditActivity.this, "请长按以重置该元素配置", Toast.LENGTH_SHORT).show();
        });

        resetButton.setOnLongClickListener(v -> {
            resetElementToDefaults();
            Toast.makeText(ElementEditActivity.this, "该元素已重置为默认值", Toast.LENGTH_SHORT).show();
            // 更新显示并广播
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

        colorButton.setBackgroundColor(currentColor);
    }

    private void resetElementToDefaults() {
        if (elementIndex == 0) {
            // 时间默认
            prefs.setTimeDirection(0f);
            prefs.setTimeDistance(0.18f);
            prefs.setTimeSize(1.0f);
            prefs.setTimeColor(Color.BLACK);
        } else if (elementIndex == 1) {
            // 日期默认
            prefs.setDateDirection(0f);
            prefs.setDateDistance(0.30f);
            prefs.setDateSize(1.0f);
            prefs.setDateColor(Color.BLACK);
        } else {
            // 电量默认（方向 180°, distance/size/color）
            prefs.setBatteryDirection(180f);
            prefs.setBatteryDistance(0.9f);
            prefs.setBatterySize(1.0f);
            prefs.setBatteryColor(Color.WHITE);
            // 注意：电量环颜色不在此处修改（保持默认）
        }
    }

    /**
     * 显示颜色选择（仅文本颜色）
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
                        colorButton.setBackgroundColor(currentColor);
                        setElementColor(currentColor);
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
                int col = android.graphics.Color.parseColor(s);
                currentColor = col;
                colorButton.setBackgroundColor(currentColor);
                setElementColor(currentColor);
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
     * isSize == true -> 大小（用户输入 n -> size = n / 100f）
     * isSize == false -> 距离（用户输入 n -> dist = n / 100f）
     *
     * 只允许输入正整数和 0，最多 3 位
     * 输入法按钮文本设置为 IME_ACTION_DONE（标签 "长按重置"）
     *
     * 自动保存：在对话框确认时保存，避免每次变动保存
     */
    private void showNumberInputDialog(boolean isSize) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isSize ? "输入大小 (例如 100 表示 1.00x)" : "输入距离百分比 (例如 90 表示 90%)");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)}); // 最多 3 位
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setImeActionLabel("长按重置", EditorInfo.IME_ACTION_DONE);

        // 预填当前值（去单位）
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
                // 自动保存已通过 setter 写入 prefs
                sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
            } catch (NumberFormatException e) {
                Toast.makeText(ElementEditActivity.this, "请输入有效整数", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            // 取消则不保存
        });

        AlertDialog dlg = builder.create();
        dlg.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DIRECTION && resultCode == RESULT_OK && data != null) {
            int angle = data.getIntExtra("selected_angle", 0);
            // 保存
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
}