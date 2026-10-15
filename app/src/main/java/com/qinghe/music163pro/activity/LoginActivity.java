package com.qinghe.music163pro.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;

import org.json.JSONObject;

/**
 * Login activity (rewritten with logged-in detection).
 *
 * Logged in (saved cookie contains MUSIC_U):
 *   - show account user ID and nickname (fetched from server)
 *   - editable Cookie field with Save / Copy buttons (Save asks for confirm)
 *   - Logout button (asks for confirm, clears cookie)
 *
 * Not logged in:
 *   - QR login (original) or captcha login (Watch gateway, NeteaseWatchApi)
 *     each opens a sub-activity; on success it saves the cookie and returns
 *     here, which refreshes into the logged-in style.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";

    private LinearLayout llLoggedIn;
    private LinearLayout llNotLoggedIn;
    private TextView tvUserId;
    private TextView tvNickname;
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

        llLoggedIn = findViewById(R.id.ll_logged_in);
        llNotLoggedIn = findViewById(R.id.ll_not_logged_in);
        tvUserId = findViewById(R.id.tv_user_id);
        tvNickname = findViewById(R.id.tv_nickname);
        etCookie = findViewById(R.id.et_cookie);
        // Enable marquee scrolling for long values
        tvUserId.setSelected(true);
        tvNickname.setSelected(true);

        TextView btnSave = findViewById(R.id.btn_save_cookie);
        TextView btnCopy = findViewById(R.id.btn_copy_cookie);
        TextView btnLogout = findViewById(R.id.btn_logout);
        TextView btnQrLogin = findViewById(R.id.btn_qr_login);
        TextView btnCaptchaLogin = findViewById(R.id.btn_captcha_login);

        btnSave.setOnClickListener(v -> confirmSaveCookie());
        btnCopy.setOnClickListener(v -> copyCookie());
        btnLogout.setOnClickListener(v -> confirmLogout());
        btnQrLogin.setOnClickListener(v ->
                startActivity(new Intent(this, QrLoginActivity.class)));
        btnCaptchaLogin.setOnClickListener(v ->
                startActivity(new Intent(this, SmsLoginActivity.class)));

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh in case QR/captcha login or another screen updated the cookie
        refreshUi();
    }

    /** Switch between logged-in / not-logged-in styles based on saved cookie. */
    private void refreshUi() {
        String cookie = prefs.getString("cookie", "");
        boolean loggedIn = cookie.contains("MUSIC_U");
        llLoggedIn.setVisibility(loggedIn ? LinearLayout.VISIBLE : LinearLayout.GONE);
        llNotLoggedIn.setVisibility(loggedIn ? LinearLayout.GONE : LinearLayout.VISIBLE);
        if (loggedIn) {
            etCookie.setText(cookie);
            tvUserId.setText("检测中...");
            tvNickname.setText("检测中...");
            fetchAccount(cookie);
        }
    }

    /** Fetch user ID and nickname for the logged-in cookie. */
    private void fetchAccount(String cookie) {
        MusicApiHelper.getUserAccount(cookie, new MusicApiHelper.AccountCallback() {
            @Override
            public void onResult(JSONObject json) {
                runOnUiThread(() -> {
                    JSONObject profile = json != null ? json.optJSONObject("profile") : null;
                    if (profile == null) {
                        tvUserId.setText("获取失败");
                        tvNickname.setText("获取失败");
                        return;
                    }
                    long userId = profile.optLong("userId", 0);
                    String nickname = profile.optString("nickname", "");
                    tvUserId.setText(userId > 0 ? String.valueOf(userId) : "未知");
                    tvNickname.setText(nickname.isEmpty() ? "未设置" : nickname);
                    if (userId > 0) {
                        prefs.edit().putLong("current_user_id", userId).apply();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvUserId.setText("获取失败");
                    tvNickname.setText("获取失败");
                });
            }
        });
    }

    private void confirmSaveCookie() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_cookie_confirm_title)
                .setMessage(R.string.save_cookie_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> saveCookie())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveCookie() {
        String cookie = etCookie.getText().toString().trim();
        if (cookie.isEmpty()) {
            Toast.makeText(this, "Cookie为空，未保存", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("cookie", cookie).apply();
        playerManager.setCookie(cookie);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void copyCookie() {
        String cookie = etCookie.getText().toString().trim();
        if (cookie.isEmpty()) {
            Toast.makeText(this, "Cookie为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("cookie", cookie));
            Toast.makeText(this, "Cookie已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> logout())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void logout() {
        prefs.edit().remove("cookie").remove("current_user_id").apply();
        playerManager.setCookie("");
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
        refreshUi();
    }
}
