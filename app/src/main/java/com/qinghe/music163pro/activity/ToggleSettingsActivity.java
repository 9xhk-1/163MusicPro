package com.qinghe.music163pro.activity;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.player.MusicPlayerManager;

/**
 * Toggle settings activity - contains boolean/toggle options:
 * - Keep screen on (SwitchMaterial)
 * - Favorites mode local/cloud (SwitchMaterial)
 * - Speed mode 3-value cyclic row (tap to cycle)
 */
public class ToggleSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final String PREF_RECOGNITION_MODE = "song_recognition_mode";
    private static final String PREF_LYRIC_SCROLL_MODE = "lyric_scroll_mode";
    private static final String PREF_LYRIC_RESUME_INTERVAL = "lyric_resume_interval";
    private static final String PREF_SLEEP_TIMER_EXIT_APP = "sleep_timer_exit_app";
    private static final int MODE_MANUAL = 0;
    private static final int MODE_AUTO = 1;
    // Lyric scroll modes: 0=每行 (follow current line), 1=阻塞 (blocked by user scroll)
    private static final int LYRIC_MODE_FOLLOW = 0;
    private static final int LYRIC_MODE_BLOCK = 1;

    private SwitchMaterial switchKeepScreenOn;
    private SwitchMaterial switchSleepTimerExitApp;
    private SwitchMaterial switchFavMode;
    private TextView tvSpeedModeValue;
    private TextView tvRecognitionModeValue;
    private TextView tvLyricScrollModeValue;
    private TextView tvLyricResumeIntervalValue;
    private LinearLayout rowLyricResumeInterval;
    private SharedPreferences prefs;

    /** Suppress listener callbacks while programmatically setting switch state. */
    private boolean updatingSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toggle_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Apply keep screen on setting
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        switchKeepScreenOn = findViewById(R.id.switch_keep_screen_on);
        switchSleepTimerExitApp = findViewById(R.id.switch_sleep_timer_exit_app);
        switchFavMode = findViewById(R.id.switch_fav_mode);
        tvSpeedModeValue = findViewById(R.id.tv_speed_mode_value);
        tvRecognitionModeValue = findViewById(R.id.tv_recognition_mode_value);
        tvLyricScrollModeValue = findViewById(R.id.tv_lyric_scroll_mode_value);
        tvLyricResumeIntervalValue = findViewById(R.id.tv_lyric_resume_interval_value);
        LinearLayout rowSpeedMode = findViewById(R.id.row_speed_mode);
        LinearLayout rowRecognitionMode = findViewById(R.id.row_recognition_mode);
        LinearLayout rowLyricScrollMode = findViewById(R.id.row_lyric_scroll_mode);
        rowLyricResumeInterval = findViewById(R.id.row_lyric_resume_interval);

        // Initialise switch states from prefs
        syncSwitchStates();

        // Keep screen on: use checked-change listener
        switchKeepScreenOn.setOnCheckedChangeListener((btn, isChecked) -> {
            if (updatingSwitch) return;
            prefs.edit().putBoolean("keep_screen_on", isChecked).apply();
            if (isChecked) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        switchSleepTimerExitApp.setOnCheckedChangeListener((btn, isChecked) -> {
            if (updatingSwitch) return;
            prefs.edit().putBoolean(PREF_SLEEP_TIMER_EXIT_APP, isChecked).apply();
            Toast.makeText(this,
                    isChecked ? "定时结束后将退出应用" : "定时结束后仅停止播放",
                    Toast.LENGTH_SHORT).show();
        });

        // Favorites mode: use checked-change listener
        switchFavMode.setOnCheckedChangeListener((btn, isChecked) -> {
            if (updatingSwitch) return;
            prefs.edit().putBoolean("fav_mode_cloud", isChecked).apply();
            String mode = isChecked ? "云端" : "本地";
            Toast.makeText(this, "收藏模式已切换为: " + mode, Toast.LENGTH_SHORT).show();
        });

        // Speed mode: tap row to cycle through 3 values
        rowSpeedMode.setOnClickListener(v -> cycleSpeedMode());
        rowRecognitionMode.setOnClickListener(v -> cycleRecognitionMode());
        rowLyricScrollMode.setOnClickListener(v -> cycleLyricScrollMode());
        rowLyricResumeInterval.setOnClickListener(v -> editLyricResumeInterval());
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSwitchStates();
    }

    private void syncSwitchStates() {
        updatingSwitch = true;
        switchKeepScreenOn.setChecked(prefs.getBoolean("keep_screen_on", false));
        switchSleepTimerExitApp.setChecked(prefs.getBoolean(PREF_SLEEP_TIMER_EXIT_APP, false));
        switchFavMode.setChecked(prefs.getBoolean("fav_mode_cloud", false));
        updatingSwitch = false;
        updateSpeedModeValue();
        updateRecognitionModeValue();
        updateLyricScrollModeValue();
        updateLyricResumeIntervalValue();
    }

    private void cycleSpeedMode() {
        int current = prefs.getInt("speed_mode", 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt("speed_mode", next).apply();
        MusicPlayerManager.getInstance().setSpeedMode(next);
        updateSpeedModeValue();
        String[] modeNames = {"音调不变", "音调改变且速度改变", "音调改变但速度不变"};
        Toast.makeText(this, "变速模式: " + modeNames[next], Toast.LENGTH_SHORT).show();
        if (next == 2) {
            Toast.makeText(this, "注意：此模式变速幅度不能太大，否则播放可能会出问题（如有杂音等）", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSpeedModeValue() {
        int mode = prefs.getInt("speed_mode", 0);
        String[] labels = {"音调不变", "音调改变且速度改变", "音调改变但速度不变"};
        tvSpeedModeValue.setText(labels[mode]);
    }

    private void cycleRecognitionMode() {
        int current = prefs.getInt(PREF_RECOGNITION_MODE, MODE_AUTO);
        int next = (current + 1) % 2;
        prefs.edit().putInt(PREF_RECOGNITION_MODE, next).apply();
        updateRecognitionModeValue();
        String[] labels = {"手动暂停", "自动识别"};
        Toast.makeText(this, "听歌识曲模式: " + labels[next], Toast.LENGTH_SHORT).show();
    }

    private void updateRecognitionModeValue() {
        int mode = prefs.getInt(PREF_RECOGNITION_MODE, MODE_AUTO);
        String[] labels = {"手动暂停", "自动识别"};
        tvRecognitionModeValue.setText(labels[mode]);
    }

    private void cycleLyricScrollMode() {
        int current = prefs.getInt(PREF_LYRIC_SCROLL_MODE, LYRIC_MODE_BLOCK);
        int next = (current + 1) % 2;
        prefs.edit().putInt(PREF_LYRIC_SCROLL_MODE, next).apply();
        updateLyricScrollModeValue();
        String[] labels = {"每行", "阻塞"};
        Toast.makeText(this, "歌词滚动模式: " + labels[next], Toast.LENGTH_SHORT).show();
    }

    private void updateLyricScrollModeValue() {
        int mode = prefs.getInt(PREF_LYRIC_SCROLL_MODE, LYRIC_MODE_BLOCK);
        String[] labels = {"每行", "阻塞"};
        tvLyricScrollModeValue.setText(labels[mode]);
        // Only show resume interval row in 阻塞 mode
        if (rowLyricResumeInterval != null) {
            rowLyricResumeInterval.setVisibility(mode == LYRIC_MODE_BLOCK
                    ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void editLyricResumeInterval() {
        int current = prefs.getInt(PREF_LYRIC_RESUME_INTERVAL, 3);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(current));
        input.setSelectAllOnFocus(true);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        input.setPadding(pad * 3, pad * 2, pad * 3, pad * 2);

        new AlertDialog.Builder(this)
                .setTitle("歌词恢复间隔（秒）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    int seconds = 3;
                    try {
                        seconds = Integer.parseInt(val);
                        if (seconds < 1) seconds = 1;
                        if (seconds > 60) seconds = 60;
                    } catch (NumberFormatException ignored) {}
                    prefs.edit().putInt(PREF_LYRIC_RESUME_INTERVAL, seconds).apply();
                    updateLyricResumeIntervalValue();
                    Toast.makeText(this, "歌词恢复间隔: " + seconds + "秒", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateLyricResumeIntervalValue() {
        int seconds = prefs.getInt(PREF_LYRIC_RESUME_INTERVAL, 3);
        tvLyricResumeIntervalValue.setText(seconds + "秒无操作后恢复跟随");
    }
}
