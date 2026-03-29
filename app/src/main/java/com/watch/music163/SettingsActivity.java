package com.watch.music163;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    private EditText etCookie;
    private EditText etApiServer;
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
        etApiServer = findViewById(R.id.et_api_server);
        TextView btnSave = findViewById(R.id.btn_save_cookie);
        TextView btnQrLogin = findViewById(R.id.btn_qr_login);
        btnPlayMode = findViewById(R.id.btn_play_mode);

        // Load saved values
        etCookie.setText(prefs.getString("cookie", ""));
        etApiServer.setText(prefs.getString("api_server", ""));

        updatePlayModeText();

        btnSave.setOnClickListener(v -> {
            String cookie = etCookie.getText().toString().trim();
            String apiServer = etApiServer.getText().toString().trim();
            prefs.edit()
                    .putString("cookie", cookie)
                    .putString("api_server", apiServer)
                    .apply();
            playerManager.setCookie(cookie);
            MusicApiHelper.setApiServerUrl(apiServer);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });

        btnQrLogin.setOnClickListener(v -> {
            String apiServer = etApiServer.getText().toString().trim();
            if (apiServer.isEmpty()) {
                Toast.makeText(this, "请先填写API服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }
            // Save server URL first
            prefs.edit().putString("api_server", apiServer).apply();
            MusicApiHelper.setApiServerUrl(apiServer);
            startActivity(new Intent(this, QrLoginActivity.class));
        });

        btnPlayMode.setOnClickListener(v -> cyclePlayMode());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh cookie field in case QR login updated it
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
