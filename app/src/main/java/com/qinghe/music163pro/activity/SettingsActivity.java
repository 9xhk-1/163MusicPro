package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;

/**
 * Settings activity - flat tile list style matching MoreActivity.
 * Contains: 开关选项, 关于
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

        TextView btnToggle = findViewById(R.id.btn_settings_toggle);
        TextView btnAbout = findViewById(R.id.btn_settings_about);

        btnToggle.setOnClickListener(v ->
                startActivity(new Intent(this, ToggleSettingsActivity.class)));

        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }
}
