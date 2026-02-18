package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 交互式方向选择器（使用独立的 DirectionView）
 *
 * - 顶部显示当前角度（TextView）
 * - 确定按钮位于屏幕中央
 * - + 和 - 按钮放置在屏幕底部
 * - 点击屏幕（非按钮区域）将把线段指向点击位置
 * - + / - 按钮对当前方向微调 +/-1°
 * - 确定按钮返回选中角度（以 directionView.getAngle() 为准）
 */
public class DirectionChooseActivity extends Activity {

    private DirectionView directionView;
    private Button btnPlus, btnMinus, btnConfirm;
    private TextView tvAngleTop;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_direction_choose);

        directionView = findViewById(R.id.direction_view);
        btnPlus = findViewById(R.id.btn_plus);
        btnMinus = findViewById(R.id.btn_minus);
        btnConfirm = findViewById(R.id.btn_confirm);
        tvAngleTop = findViewById(R.id.tv_angle_top);

        int initAngle = getIntent().getIntExtra("current_angle", 0);
        initAngle = normalize(initAngle);
        directionView.setAngle(initAngle);
        tvAngleTop.setText(initAngle + "°");

        // 注册需要被忽略的覆盖按钮（DirectionView 在这些区域不处理触摸）
        directionView.post(() -> directionView.setIgnoreViews(btnPlus, btnMinus, btnConfirm, tvAngleTop));

        // 监听 angle 改变并更新顶部显示
        directionView.setOnAngleChangedListener(newAngle -> runOnUiThread(() -> tvAngleTop.setText(newAngle + "°")));

        btnPlus.setOnClickListener(v -> {
            int a = directionView.getAngle();
            a = normalize(a + 1);
            directionView.setAngle(a);
            // setAngle 会回调 listener 更新 tvAngleTop
        });

        btnMinus.setOnClickListener(v -> {
            int a = directionView.getAngle();
            a = normalize(a - 1);
            directionView.setAngle(a);
        });

        btnConfirm.setOnClickListener(v -> {
            int selected = directionView.getAngle();
            Intent out = new Intent();
            out.putExtra("selected_angle", selected);
            setResult(RESULT_OK, out);
            finish();
        });
    }

    private int normalize(int a) {
        a %= 360;
        if (a < 0) a += 360;
        return a;
    }
}