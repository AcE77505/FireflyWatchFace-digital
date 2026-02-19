package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BackgroundChooseActivity
 * - 列出 assets 根目录下的图片（不显示预览），仅显示文件名
 * - 当前选中的文件在右侧使用对号标记
 * - 点击某项进入 BackgroundPreviewActivity 进行预览与确认
 */
public class BackgroundChooseActivity extends Activity {

    private RecyclerView recyclerView;
    private PrefsAdapter adapter;
    private PreferencesManager prefsManager;
    private final List<String> assetImages = new ArrayList<>();

    private static final int REQUEST_PREVIEW = 1001;

    private static final List<String> IMAGE_EXTS = Arrays.asList(".png", ".jpg", ".jpeg", ".webp", ".gif");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_choose);

        prefsManager = new PreferencesManager(this);

        recyclerView = findViewById(R.id.bg_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PrefsAdapter();
        recyclerView.setAdapter(adapter);

        loadAssetImages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重新读取 prefs 中的选中项并刷新列表显示（确保从预览返回后 UI 更新）
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW) {
            // 预览页如果保存了设置会通过 RESULT_OK 返回，刷新显示
            if (resultCode == RESULT_OK) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void loadAssetImages() {
        AssetManager am = getAssets();
        assetImages.clear(); // 防止重复添加
        try {
            String[] list = am.list("");
            if (list != null) {
                for (String name : list) {
                    String lower = name.toLowerCase();
                    for (String ext : IMAGE_EXTS) {
                        if (lower.endsWith(ext)) {
                            assetImages.add(name);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        adapter.notifyDataSetChanged();
    }

    private class PrefsAdapter extends RecyclerView.Adapter<PrefsAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_background, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String name = assetImages.get(position);
            holder.name.setText(name);

            String selected = prefsManager.getBackgroundFilename();
            if (selected != null && selected.equals(name)) {
                holder.check.setVisibility(View.VISIBLE);
            } else {
                holder.check.setVisibility(View.INVISIBLE);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(BackgroundChooseActivity.this, BackgroundPreviewActivity.class);
                intent.putExtra("asset_name", name);
                // 使用 startActivityForResult，以便在预览保存后可以刷新
                startActivityForResult(intent, REQUEST_PREVIEW);
            });
        }

        @Override
        public int getItemCount() {
            return assetImages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name;
            ImageView check;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.bg_name);
                check = itemView.findViewById(R.id.bg_check);
            }
        }
    }
}