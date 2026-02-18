package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.wear.widget.WearableLinearLayoutManager;
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
        recyclerView.setEdgeItemsCenteringEnabled(true);
        recyclerView.setLayoutManager(new WearableLinearLayoutManager(this));

        adapter = new SettingsAdapter();
        recyclerView.setAdapter(adapter);
    }

    private class SettingsAdapter extends WearableRecyclerView.Adapter<SettingsAdapter.ViewHolder> {

        private final String[] settings = {"时间颜色", "日期颜色"};

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setting, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String setting = settings[position];
            holder.settingName.setText(setting);

            int currentColor = position == 0 ?
                    prefsManager.getTimeColor() : prefsManager.getDateColor();

            // 显示当前颜色的预览
            holder.colorPreview.setBackgroundColor(currentColor);

            holder.itemView.setOnClickListener(v -> {
                showColorPickerDialog(position == 0 ? "时间颜色" : "日期颜色", position);
            });
        }

        @Override
        public int getItemCount() {
            return settings.length;
        }

        class ViewHolder extends WearableRecyclerView.ViewHolder {
            TextView settingName;
            View colorPreview;

            ViewHolder(View itemView) {
                super(itemView);
                settingName = itemView.findViewById(R.id.setting_name);
                colorPreview = itemView.findViewById(R.id.color_preview);
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