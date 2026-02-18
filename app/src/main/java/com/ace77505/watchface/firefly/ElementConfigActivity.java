package com.ace77505.watchface.firefly;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * 元素配置二级界面：列出 时间 / 日期 / 电量，点击进入编辑界面
 *
 * 请在 AndroidManifest.xml 中注册此 Activity。
 */
public class ElementConfigActivity extends Activity {

    private static final String[] ELEMENTS = {"时间", "日期", "电量"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_element_config);

        ListView listView = findViewById(R.id.element_list);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, ELEMENTS);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            // 启动编辑页面，传入 element index（0=time,1=date,2=battery）
            Intent intent = new Intent(ElementConfigActivity.this, ElementEditActivity.class);
            intent.putExtra("element_index", position);
            startActivity(intent);
        });
    }
}