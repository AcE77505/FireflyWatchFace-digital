package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

/**
 * BackgroundPreviewActivity
 * - 预览选中 asset 图片
 * - 底部有圆形确认按钮（资源 btn_confirm）
 * - 单击图片隐藏/显示确认按钮
 * - 长按图片弹出输入框：只允许 100..1000 的正整数，默认 100，表示缩放 %
 * - 点击确认按钮：保存背景文件名与该图片的缩放百分比到 prefs，发送 PREF_CHANGED_ACTION，然后 finish()
 */
public class BackgroundPreviewActivity extends Activity {

    private ImageView imageView;
    private ImageButton btnConfirm;
    private PreferencesManager prefsManager;

    private String assetName;
    private int currentScalePercent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_preview);

        prefsManager = new PreferencesManager(this);

        imageView = findViewById(R.id.bg_preview_image);
        btnConfirm = findViewById(R.id.btn_confirm_bg);

        assetName = getIntent().getStringExtra("asset_name");
        if (assetName == null) {
            finish();
            return;
        }

        // 为当前 asset 使用独立的缩放值（若未设置则使用默认 100）
        currentScalePercent = prefsManager.getBackgroundScale(assetName);

        loadAndShowPreview();

        // 单击切换按钮显隐
        imageView.setOnClickListener(v -> {
            if (btnConfirm.getVisibility() == View.VISIBLE) {
                btnConfirm.setVisibility(View.GONE);
            } else {
                btnConfirm.setVisibility(View.VISIBLE);
            }
        });

        // 长按弹出数字输入框（100..1000）
        imageView.setOnLongClickListener(v -> {
            showScaleInputDialog();
            return true;
        });

        btnConfirm.setOnClickListener(v -> {
            // 保存所选背景文件名
            prefsManager.setBackgroundFilename(assetName);
            // 保存该文件对应的缩放（每个文件独立）
            prefsManager.setBackgroundScale(assetName, currentScalePercent);

            // 触发表盘刷新
            sendBroadcast(new Intent(PreferencesManager.PREF_CHANGED_ACTION));

            // 不再显示保存成功的 Toast；通过 setResult 通知上层
            setResult(RESULT_OK);
            finish();
        });
    }

    private void loadAndShowPreview() {
        try (InputStream is = getAssets().open(assetName)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) return;

            // 以 currentScalePercent 缩放（以图片中心为基准放大/缩小），并使用 CENTER_CROP 显示中心区域
            int scaledW = Math.max(1, bmp.getWidth() * currentScalePercent / 100);
            int scaledH = Math.max(1, bmp.getHeight() * currentScalePercent / 100);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true);

            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(scaled);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showScaleInputDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4) }); // 最多 4 位（1000）
        input.setHint(String.valueOf(currentScalePercent));
        input.setText(String.valueOf(currentScalePercent));

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("输入缩放（百分比）");
        b.setMessage("请输入 100 - 1000 的整数（默认 100），表示图片相对于原图的百分比，例如 250 表示放大到 250%)。");
        b.setView(input);
        b.setPositiveButton("确定", (dialog, which) -> {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) {
                Toast.makeText(this, "请输入数值", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int val = Integer.parseInt(s);
                if (val < 100 || val > 1000) {
                    Toast.makeText(this, "请输入 100 到 1000 之间的整数", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentScalePercent = val;
                loadAndShowPreview();
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "无效的数值", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        b.show();
    }
}