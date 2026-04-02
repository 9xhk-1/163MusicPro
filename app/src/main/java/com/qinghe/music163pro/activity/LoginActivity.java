package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.player.MusicPlayerManager;

/**
 * Login activity - contains Cookie input, QR login and SMS login.
 * Moved from the old SettingsActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    private EditText etCookie;
    private SharedPreferences prefs;
    private MusicPlayerManager playerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        playerManager = MusicPlayerManager.getInstance();

        // Apply keep screen on setting
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        etCookie = findViewById(R.id.et_cookie);
        TextView btnSave = findViewById(R.id.btn_save_cookie);
        TextView btnQrLogin = findViewById(R.id.btn_qr_login);

        // Load saved values
        etCookie.setText(prefs.getString("cookie", ""));

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh cookie field in case QR/SMS login updated it
        etCookie.setText(prefs.getString("cookie", ""));
    }
}
