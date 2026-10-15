package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.api.NeteaseWatchApi;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;

/**
 * SMS Captcha Login Activity (Watch gateway).
 * Two-step flow using NeteaseWatchApi (docs/163watch.md):
 * 1. /eapi/sms/captcha/sent - send SMS verification code
 * 2. /eapi/login/cellphone  - login with phone + captcha
 *
 * On success: saves cookie, shows "成功登录：昵称" and returns to
 * the login main page which refreshes into the logged-in style.
 */
public class SmsLoginActivity extends AppCompatActivity {

    private static final String TAG = "SmsLoginActivity";
    private static final String PREFS_NAME = "music163_settings";

    private EditText etPhone;
    private EditText etCode;
    private TextView btnSendCode;
    private TextView btnLogin;
    private TextView tvStatus;

    private final Handler handler = new Handler();
    private int countdown = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_login);

        etPhone = findViewById(R.id.et_phone);
        etCode = findViewById(R.id.et_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnLogin = findViewById(R.id.btn_sms_login);
        tvStatus = findViewById(R.id.tv_sms_status);

        btnSendCode.setOnClickListener(v -> sendCode());
        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void sendCode() {
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            tvStatus.setText("请输入手机号");
            return;
        }

        btnSendCode.setEnabled(false);
        tvStatus.setText("正在发送验证码...");

        NeteaseWatchApi.sendSmsCaptcha(phone, "86", new NeteaseWatchApi.WatchSmsCallback() {
            @Override
            public void onResult(boolean success, String message) {
                tvStatus.setText(message);
                if (success) {
                    startCountdown();
                } else {
                    btnSendCode.setEnabled(true);
                }
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("发送失败: " + message);
                btnSendCode.setEnabled(true);
            }
        });
    }

    private void startCountdown() {
        countdown = 60;
        updateCountdownText();
        handler.postDelayed(countdownRunnable, 1000);
    }

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            countdown--;
            if (countdown > 0) {
                updateCountdownText();
                handler.postDelayed(this, 1000);
            } else {
                btnSendCode.setText(R.string.send_code);
                btnSendCode.setEnabled(true);
            }
        }
    };

    private void updateCountdownText() {
        btnSendCode.setText(countdown + "s 后重新发送");
    }

    private void doLogin() {
        String phone = etPhone.getText().toString().trim();
        String code = etCode.getText().toString().trim();

        if (phone.isEmpty()) {
            tvStatus.setText("请输入手机号");
            return;
        }
        if (code.isEmpty()) {
            tvStatus.setText("请输入验证码");
            return;
        }

        btnLogin.setEnabled(false);
        tvStatus.setText("正在登录...");

        NeteaseWatchApi.loginByCellphone(phone, code, "86",
                new NeteaseWatchApi.WatchLoginCallback() {
                    @Override
                    public void onResult(int resultCode, String message,
                                          String cookie, String nickname, long userId) {
                        if (resultCode == 200 && cookie != null && !cookie.isEmpty()) {
                            saveCookie(cookie, userId);
                            String tip = "成功登录：" + (nickname.isEmpty() ? "未知用户" : nickname);
                            tvStatus.setText(tip);
                            Toast.makeText(SmsLoginActivity.this, tip, Toast.LENGTH_LONG).show();
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

    private void saveCookie(String cookie, long userId) {
        if (cookie == null || cookie.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putString("cookie", cookie);
        if (userId > 0) {
            editor.putLong("current_user_id", userId);
        }
        editor.apply();
        MusicPlayerManager.getInstance().setCookie(cookie);
        MusicLog.op(TAG, "验证码登录成功", "uid=" + userId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(countdownRunnable);
    }
}
