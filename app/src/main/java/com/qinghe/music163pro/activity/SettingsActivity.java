package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.player.MusicPlayerManager;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    private EditText etCookie;
    private TextView btnPlayMode;
    private SharedPreferences prefs;
    private MusicPlayerManager playerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        playerManager = MusicPlayerManager.getInstance();

        etCookie = findViewById(R.id.et_cookie);
        TextView btnSave = findViewById(R.id.btn_save_cookie);
        TextView btnQrLogin = findViewById(R.id.btn_qr_login);
        TextView btnSmsLogin = findViewById(R.id.btn_sms_login);
        btnPlayMode = findViewById(R.id.btn_play_mode);

        // Load saved values
        etCookie.setText(prefs.getString("cookie", ""));

        updatePlayModeText();

        btnSave.setOnClickListener(v -> {
            String cookie = etCookie.getText().toString().trim();
            if (cookie.isEmpty()) {
                Toast.makeText(this, "Cookie为空，未保存", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("cookie", cookie).apply();
            playerManager.setCookie(cookie);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });

        btnQrLogin.setOnClickListener(v ->
            startActivity(new Intent(this, QrLoginActivity.class))
        );

        btnSmsLogin.setOnClickListener(v ->
            startActivity(new Intent(this, SmsLoginActivity.class))
        );

        btnPlayMode.setOnClickListener(v -> cyclePlayMode());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh cookie field in case QR/SMS login updated it
        etCookie.setText(prefs.getString("cookie", ""));
        updatePlayModeText();
    }

    private void cyclePlayMode() {
        MusicPlayerManager.PlayMode current = playerManager.getPlayMode();
        MusicPlayerManager.PlayMode next;
        switch (current) {
            case LIST_LOOP:
                next = MusicPlayerManager.PlayMode.SINGLE_REPEAT;
                break;
            case SINGLE_REPEAT:
                next = MusicPlayerManager.PlayMode.RANDOM;
                break;
            case RANDOM:
            default:
                next = MusicPlayerManager.PlayMode.LIST_LOOP;
                break;
        }
        playerManager.setPlayMode(next);
        prefs.edit().putString("play_mode", next.name()).apply();
        updatePlayModeText();
    }

    private void updatePlayModeText() {
        MusicPlayerManager.PlayMode mode = playerManager.getPlayMode();
        int textResId;
        switch (mode) {
            case SINGLE_REPEAT:
                textResId = R.string.play_mode_single;
                break;
            case RANDOM:
                textResId = R.string.play_mode_random;
                break;
            case LIST_LOOP:
            default:
                textResId = R.string.play_mode_list_loop;
                break;
        }
        btnPlayMode.setText(textResId);
    }
}
