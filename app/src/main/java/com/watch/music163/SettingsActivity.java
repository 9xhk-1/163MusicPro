package com.watch.music163;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EditText etCookie = findViewById(R.id.et_cookie);
        TextView btnSave = findViewById(R.id.btn_save_cookie);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        etCookie.setText(prefs.getString("cookie", ""));

        btnSave.setOnClickListener(v -> {
            String cookie = etCookie.getText().toString().trim();
            prefs.edit().putString("cookie", cookie).apply();
            MusicPlayerManager.getInstance().setCookie(cookie);
            Toast.makeText(this, "Cookie saved", Toast.LENGTH_SHORT).show();
        });
    }
}
