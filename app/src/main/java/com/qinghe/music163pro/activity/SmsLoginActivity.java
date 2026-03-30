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
 * SMS Login Activity.
 * Two-step flow:
 * 1. Send SMS verification code to phone number
 * 2. Login with phone + verification code
 */
public class SmsLoginActivity extends AppCompatActivity {

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

        MusicApiHelper.sendSmsCode(phone, "86", new MusicApiHelper.SmsCallback() {
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

        MusicApiHelper.loginByCellphone(phone, code, "86",
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
                            Toast.makeText(SmsLoginActivity.this,
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(countdownRunnable);
    }
}
