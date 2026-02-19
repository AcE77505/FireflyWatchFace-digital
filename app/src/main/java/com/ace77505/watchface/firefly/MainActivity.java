package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableRecyclerView;

public class MainActivity extends Activity {

    public PreferencesManager prefsManager;
    public WearableRecyclerView recyclerView;
    public SettingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefsManager = new PreferencesManager(this);

        recyclerView = findViewById(R.id.recycler_view);

        recyclerView.setEdgeItemsCenteringEnabled(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SettingsAdapter();
        recyclerView.setAdapter(adapter);
    }

    public class SettingsAdapter extends WearableRecyclerView.Adapter<SettingsAdapter.ViewHolder> {

        // 主界面显示：元素配置、 背景设置、 电量环（开关）
        private final String[] settings = {"元素配置", "背景设置", "电量环"};
        private final int TYPE_NAV = 0;
        private final int TYPE_SWITCH = 1;

        @Override
        public int getItemViewType(int position) {
            // 电量环仍为开关（position 2）
            if (position == 2) return TYPE_SWITCH;
            return TYPE_NAV;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setting, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.settingName.setText(settings[position]);

            if (getItemViewType(position) == TYPE_SWITCH) {
                holder.colorPreview.setVisibility(View.GONE);
                holder.switchButton.setVisibility(View.VISIBLE);

                holder.switchButton.setOnCheckedChangeListener(null);
                holder.switchButton.setChecked(prefsManager.isBatteryRingEnabled());
                holder.switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefsManager.setBatteryRingEnabled(isChecked);
                    sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));
                    Toast.makeText(MainActivity.this,
                            "电量环已" + (isChecked ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
                });

                holder.itemView.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && getItemViewType(pos) == TYPE_SWITCH) {
                        boolean newState = !holder.switchButton.isChecked();
                        holder.switchButton.setChecked(newState);
                    }
                });
            } else {
                holder.colorPreview.setVisibility(View.GONE);
                holder.switchButton.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos == 0) {
                        // 进入元素配置二级界面
                        Intent intent = new Intent(MainActivity.this, ElementConfigActivity.class);
                        startActivity(intent);
                    } else if (pos == 1) {
                        // 进入背景选择界面
                        Intent intent = new Intent(MainActivity.this, BackgroundChooseActivity.class);
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return settings.length;
        }

        public class ViewHolder extends WearableRecyclerView.ViewHolder {
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
}