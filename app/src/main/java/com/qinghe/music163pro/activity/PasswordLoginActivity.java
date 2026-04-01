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

/**
 * Password Login Activity.
 * Login with phone number and password.
 * Cookie handling follows the same pattern as SmsLoginActivity.
 */
public class PasswordLoginActivity extends AppCompatActivity {

    private EditText etPhone;
    private EditText etPassword;
    private TextView btnLogin;
    private TextView tvStatus;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_login);

        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_password_login);
        tvStatus = findViewById(R.id.tv_password_status);

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (phone.isEmpty()) {
            tvStatus.setText("请输入手机号");
            return;
        }
        if (password.isEmpty()) {
            tvStatus.setText("请输入密码");
            return;
        }

        btnLogin.setEnabled(false);
        tvStatus.setText("正在登录...");

        MusicApiHelper.loginByPassword(phone, password, "86",
                new MusicApiHelper.LoginCallback() {
                    @Override
                    public void onResult(int resultCode, String message, String cookie) {
                        if (resultCode == 200) {
                            if (cookie == null || cookie.isEmpty()) {
                                tvStatus.setText("登录成功但未获取到Cookie");
                                btnLogin.setEnabled(true);
                                return;
                            }
                            tvStatus.setText("登录成功!");
                            saveCookie(cookie);
                            Toast.makeText(PasswordLoginActivity.this,
                                    "登录成功，Cookie已保存", Toast.LENGTH_LONG).show();
                            handler.postDelayed(() -> finish(), 1500);
                        } else {
                            tvStatus.setText("登录失败: " + message);
                            btnLogin.setEnabled(true);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        tvStatus.setText("登录失败: " + message);
                        btnLogin.setEnabled(true);
                    }
                });
    }

    private void saveCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putString("cookie", cookie).apply();
        MusicPlayerManager.getInstance().setCookie(cookie);
    }
}
