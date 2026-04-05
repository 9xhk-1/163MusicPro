package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.player.MusicPlayerManager;

/**
 * Toggle settings activity - contains boolean/toggle options:
 * - Keep screen on
 * - Favorites mode (local/cloud)
 * - Speed mode (pitch unchanged/changed)
 */
public class ToggleSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    private TextView btnKeepScreenOn;
    private TextView btnFavMode;
    private TextView btnSpeedMode;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toggle_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Apply keep screen on setting
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        btnKeepScreenOn = findViewById(R.id.btn_keep_screen_on);
        btnFavMode = findViewById(R.id.btn_fav_mode);
        btnSpeedMode = findViewById(R.id.btn_speed_mode);

        updateKeepScreenOnText();
        updateFavModeText();
        updateSpeedModeText();

        btnKeepScreenOn.setOnClickListener(v -> toggleKeepScreenOn());
        btnFavMode.setOnClickListener(v -> toggleFavMode());
        btnSpeedMode.setOnClickListener(v -> toggleSpeedMode());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeepScreenOnText();
        updateFavModeText();
        updateSpeedModeText();
    }

    private void toggleKeepScreenOn() {
        boolean current = prefs.getBoolean("keep_screen_on", false);
        boolean next = !current;
        prefs.edit().putBoolean("keep_screen_on", next).apply();
        if (next) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateKeepScreenOnText();
    }

    private void updateKeepScreenOnText() {
        boolean on = prefs.getBoolean("keep_screen_on", false);
        btnKeepScreenOn.setText(on ? "💡  屏幕常亮: 已开启" : "🌙  屏幕常亮: 已关闭");
    }

    private void toggleFavMode() {
        boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);
        boolean next = !isCloud;
        prefs.edit().putBoolean("fav_mode_cloud", next).apply();
        updateFavModeText();
        String mode = next ? "云端" : "本地";
        Toast.makeText(this, "收藏模式已切换为: " + mode, Toast.LENGTH_SHORT).show();
    }

    private void updateFavModeText() {
        boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);
        btnFavMode.setText(isCloud ? "☁  收藏模式: 云端" : "📱  收藏模式: 本地");
    }

    private void toggleSpeedMode() {
        int current = prefs.getInt("speed_mode", 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt("speed_mode", next).apply();
        MusicPlayerManager.getInstance().setSpeedMode(next);
        updateSpeedModeText();
        String[] modeNames = {"音调不变", "音调改变且速度改变", "音调改变但速度不变"};
        Toast.makeText(this, "变速模式已切换为: " + modeNames[next], Toast.LENGTH_SHORT).show();
        if (next == 2) {
            Toast.makeText(this, "注意：此模式变速幅度不能太大，否则播放可能会出问题（如有杂音等）", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSpeedModeText() {
        int mode = prefs.getInt("speed_mode", 0);
        String[] labels = {"音调不变", "音调改变且速度改变", "音调改变但速度不变"};
        btnSpeedMode.setText("🎵  变速模式: " + labels[mode]);
    }
}
