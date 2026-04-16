package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.UpdateChecker;

/**
 * Settings activity - flat tile list style matching MoreActivity.
 * Contains: 开关选项, 检测更新, 关于
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        View btnToggle = findViewById(R.id.btn_settings_toggle);
        View btnEditMore = findViewById(R.id.btn_settings_edit_more);
        View btnCheckUpdate = findViewById(R.id.btn_settings_check_update);
        View btnAbout = findViewById(R.id.btn_settings_about);
        View btnOpenSource = findViewById(R.id.btn_settings_open_source);
        View btnXtcModule = findViewById(R.id.btn_settings_xtc_module);

        btnToggle.setOnClickListener(v ->
                startActivity(new Intent(this, ToggleSettingsActivity.class)));

        btnEditMore.setOnClickListener(v ->
                startActivity(new Intent(this, EditMoreActivity.class)));

        btnCheckUpdate.setOnClickListener(v -> checkUpdateManually());

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        btnOpenSource.setOnClickListener(v ->
                startActivity(new Intent(this, OpenSourceActivity.class)));

        btnXtcModule.setOnClickListener(v ->
                startActivity(new Intent(this, XtcModuleActivity.class)));
    }

    private void checkUpdateManually() {
        Toast.makeText(this, "正在检测更新...", Toast.LENGTH_SHORT).show();
        UpdateChecker.checkVersion(this, new UpdateChecker.CheckCallback() {
            @Override
            public void onResult(boolean isLatest) {
                if (isLatest) {
                    Toast.makeText(SettingsActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                } else {
                    startActivity(new Intent(SettingsActivity.this, UpdateActivity.class));
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SettingsActivity.this, "检测失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
