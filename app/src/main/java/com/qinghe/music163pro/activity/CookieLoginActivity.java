package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;

import org.json.JSONObject;

/**
 * Cookie Login Activity.
 * Paste a full Cookie string (must contain MUSIC_U), verify it against the
 * server (/api/w/nuser/account/get), then save and show "成功登录：昵称".
 * Returns to the login main page which refreshes into the logged-in style.
 */
public class CookieLoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final String TAG = "CookieLoginActivity";

    private EditText etCookie;
    private TextView btnLogin;
    private TextView tvStatus;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cookie_login);

        etCookie = findViewById(R.id.et_cookie_input);
        btnLogin = findViewById(R.id.btn_cookie_login);
        tvStatus = findViewById(R.id.tv_cookie_status);

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String cookie = etCookie.getText().toString().trim();
        if (cookie.isEmpty()) {
            tvStatus.setText("请输入Cookie");
            return;
        }
        if (!cookie.contains("MUSIC_U")) {
            tvStatus.setText("Cookie缺少MUSIC_U，请检查是否完整");
            return;
        }

        btnLogin.setEnabled(false);
        tvStatus.setText("正在验证Cookie...");

        // Verify the cookie by fetching the account
        MusicApiHelper.getUserAccount(cookie, new MusicApiHelper.AccountCallback() {
            @Override
            public void onResult(JSONObject json) {
                JSONObject profile = json != null ? json.optJSONObject("profile") : null;
                if (profile == null) {
                    fail("Cookie无效或已过期");
                    return;
                }
                long userId = profile.optLong("userId", 0);
                String nickname = profile.optString("nickname", "");
                saveCookie(cookie, userId);
                String tip = "成功登录：" + (nickname.isEmpty() ? "未知用户" : nickname);
                tvStatus.setText(tip);
                Toast.makeText(CookieLoginActivity.this, tip, Toast.LENGTH_LONG).show();
                handler.postDelayed(() -> finish(), 1500);
            }

            @Override
            public void onError(String message) {
                fail("验证失败: " + message);
            }
        });
    }

    private void fail(String msg) {
        runOnUiThread(() -> {
            tvStatus.setText(msg);
            btnLogin.setEnabled(true);
        });
    }

    private void saveCookie(String cookie, long userId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putString("cookie", cookie);
        if (userId > 0) {
            editor.putLong("current_user_id", userId);
        }
        editor.apply();
        MusicPlayerManager.getInstance().setCookie(cookie);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
