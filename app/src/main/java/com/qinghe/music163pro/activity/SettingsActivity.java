package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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
        View btnHelp = findViewById(R.id.btn_settings_help);
        View btnAbout = findViewById(R.id.btn_settings_about);
        View btnOpenSource = findViewById(R.id.btn_settings_open_source);
        View btnXtcModule = findViewById(R.id.btn_settings_xtc_module);
        View btnBackground = findViewById(R.id.btn_settings_background);
        View btnBattery = findViewById(R.id.btn_settings_battery);

        btnToggle.setOnClickListener(v ->
                startActivity(new Intent(this, ToggleSettingsActivity.class)));

        btnEditMore.setOnClickListener(v ->
                startActivity(new Intent(this, EditMoreActivity.class)));

        btnCheckUpdate.setOnClickListener(v -> checkUpdateManually());

        btnHelp.setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        btnOpenSource.setOnClickListener(v ->
                startActivity(new Intent(this, OpenSourceActivity.class)));

        btnXtcModule.setOnClickListener(v ->
                startActivity(new Intent(this, XtcModuleActivity.class)));

        btnBackground.setOnClickListener(v ->
                startActivity(new Intent(this, BackgroundSettingsActivity.class)));

        btnBattery.setOnClickListener(v -> openBatterySettings());
    }

    /**
     * 后台保活：申请把本应用加入电池优化白名单。
     * 华为/荣耀儿童手表还会有一层自家"应用启动管理"，系统弹窗之外需要手动允许后台活动。
     */
    private void openBatterySettings() {
        boolean ignoring = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        if (ignoring) {
            Toast.makeText(this, "已在电池优化白名单中。若仍被杀，请在系统「应用启动管理」里允许本应用后台活动",
                    Toast.LENGTH_LONG).show();
            openAppDetails();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请选择「允许」，再到系统设置里允许本应用后台活动", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                openAppDetails();
            }
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "请到系统设置里手动允许本应用后台运行", Toast.LENGTH_LONG).show();
        }
    }

    private void checkUpdateManually() {
        Toast.makeText(this, "正在检测更新...", Toast.LENGTH_SHORT).show();
        UpdateChecker.checkVersionInfo(this, new UpdateChecker.VersionCheckCallback() {
            @Override
            public void onResult(UpdateChecker.VersionInfo versionInfo) {
                if (versionInfo.isLatest()) {
                    Toast.makeText(SettingsActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(SettingsActivity.this, UpdateActivity.class);
                    if (versionInfo.getVersionName() != null
                            && !versionInfo.getVersionName().trim().isEmpty()) {
                        intent.putExtra("target_version_name", versionInfo.getVersionName().trim());
                    }
                    startActivity(intent);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SettingsActivity.this, "检测失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
