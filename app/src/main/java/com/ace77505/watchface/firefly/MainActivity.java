package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableRecyclerView;

public class MainActivity extends Activity {

    private PreferencesManager prefsManager;
    private WearableRecyclerView recyclerView;
    private SettingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsManager = new PreferencesManager(this);

        recyclerView = findViewById(R.id.recycler_view);

        // 完全禁用弯曲特效 - 使用标准的 LinearLayoutManager
        recyclerView.setEdgeItemsCenteringEnabled(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SettingsAdapter();
        recyclerView.setAdapter(adapter);
    }

    private class SettingsAdapter extends WearableRecyclerView.Adapter<SettingsAdapter.ViewHolder> {

        private final String[] settings = {"时间颜色", "日期颜色", "电量环"};
        private final int TYPE_COLOR = 0;
        private final int TYPE_SWITCH = 1;

        @Override
        public int getItemViewType(int position) {
            if (position == 2) return TYPE_SWITCH;
            return TYPE_COLOR;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setting, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            // 设置名称 - 直接使用position是安全的，因为这是立即使用
            holder.settingName.setText(settings[position]);

            if (getItemViewType(position) == TYPE_SWITCH) {
                // 电量环开关
                setupSwitchItem(holder);
            } else {
                // 颜色选项
                setupColorItem(holder, position);
            }
        }

        private void setupSwitchItem(ViewHolder holder) {
            holder.colorPreview.setVisibility(View.GONE);
            holder.switchButton.setVisibility(View.VISIBLE);

            // 设置开关状态
            holder.switchButton.setChecked(prefsManager.isBatteryRingEnabled());

            // 移除之前的监听器
            holder.switchButton.setOnCheckedChangeListener(null);

            // 设置开关监听器
            holder.switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    // 直接保存设置，不需要位置信息
                    prefsManager.setBatteryRingEnabled(isChecked);
                    // 发送广播通知表盘更新
                    Intent intent = new Intent(PreferencesManager.PREF_CHANGED_ACTION);
                    sendBroadcast(intent);
                    Toast.makeText(MainActivity.this,
                            "电量环已" + (isChecked ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
                }
            });

            // 点击整个项切换开关
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && getItemViewType(pos) == TYPE_SWITCH) {
                        boolean newState = !holder.switchButton.isChecked();
                        holder.switchButton.setChecked(newState);
                    }
                }
            });
        }

        private void setupColorItem(ViewHolder holder, int position) {
            holder.colorPreview.setVisibility(View.VISIBLE);
            holder.switchButton.setVisibility(View.GONE);

            // 设置颜色预览
            int currentColor = position == 0 ?
                    prefsManager.getTimeColor() : prefsManager.getDateColor();
            holder.colorPreview.setBackgroundColor(currentColor);

            // 点击事件
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && getItemViewType(pos) == TYPE_COLOR) {
                        showColorPickerDialog(
                                pos == 0 ? "时间颜色" : "日期颜色",
                                pos
                        );
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return settings.length;
        }

        class ViewHolder extends WearableRecyclerView.ViewHolder {
            TextView settingName;
            View colorPreview;
            CheckBox switchButton;

            ViewHolder(View itemView) {
                super(itemView);
                settingName = itemView.findViewById(R.id.setting_name);
                colorPreview = itemView.findViewById(R.id.color_preview);
                switchButton = itemView.findViewById(R.id.switch_button);
            }
        }
    }

    private void showColorPickerDialog(String title, int settingIndex) {
        ColorOption[] colors = ColorOption.getPresetColors();
        String[] colorNames = new String[colors.length];
        for (int i = 0; i < colors.length; i++) {
            colorNames[i] = colors[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setItems(colorNames, (dialog, which) -> {
            ColorOption selected = colors[which];
            if (selected.isCustom()) {
                showCustomColorInput(settingIndex);
            } else {
                saveColor(settingIndex, selected.getColor());
            }
        });
        builder.show();
    }

    private void showCustomColorInput(int settingIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入十六进制颜色");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("#RRGGBB 或 #AARRGGBB");
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String colorStr = input.getText().toString().trim();
            try {
                int color = Color.parseColor(colorStr);
                saveColor(settingIndex, color);
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, "无效的颜色格式", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveColor(int settingIndex, int color) {
        if (settingIndex == 0) {
            prefsManager.setTimeColor(color);
        } else {
            prefsManager.setDateColor(color);
        }

        // 刷新UI
        adapter.notifyDataSetChanged();

        // 发送广播通知表盘更新
        Intent intent = new Intent(PreferencesManager.PREF_CHANGED_ACTION);
        sendBroadcast(intent);

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }
}