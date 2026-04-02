package com.qinghe.music163pro.activity;

import android.app.Activity;
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
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;

/**
 * SMS Login Activity.
 * Two-step flow:
 * 1. Send SMS verification code to phone number
 * 2. Login with phone + verification code
 *
 * If server returns -462 (needs CAPTCHA), launches WebVerifyActivity and
 * retries login automatically after WebView verification completes.
 */
public class SmsLoginActivity extends AppCompatActivity {

    private static final String TAG = "SmsLoginActivity";
    private static final int REQ_WEB_VERIFY = 201;

    private EditText etPhone;
    private EditText etCode;
    private TextView btnSendCode;
    private TextView btnLogin;
    private TextView tvStatus;

    private final Handler handler = new Handler();
    private int countdown = 0;

    /** Saved phone/code for retry after WebView verification */
    private String pendingPhone;
    private String pendingCode;

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

        pendingPhone = phone;
        pendingCode = code;
        attemptLogin(phone, code);
    }

    private void attemptLogin(String phone, String code) {
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

                    @Override
                    public void onVerificationRequired(String verifyUrl) {
                        MusicLog.i(TAG, "需要滑块验证，打开WebView: " + verifyUrl);
                        tvStatus.setText("需要安全验证，请在浏览器中完成...");
                        if (verifyUrl == null || verifyUrl.isEmpty()) {
                            tvStatus.setText("验证地址无效，请稍后重试");
                            btnLogin.setEnabled(true);
                            return;
                        }
                        Intent intent = new Intent(SmsLoginActivity.this, WebVerifyActivity.class);
                        intent.putExtra(WebVerifyActivity.EXTRA_URL, verifyUrl);
                        startActivityForResult(intent, REQ_WEB_VERIFY);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_WEB_VERIFY) {
            if (resultCode == Activity.RESULT_OK && pendingPhone != null && pendingCode != null) {
                // WebView verification done — retry login
                tvStatus.setText("验证完成，正在重新登录...");
                btnLogin.setEnabled(false);
                MusicLog.i(TAG, "WebView验证完成，重新尝试登录");
                attemptLogin(pendingPhone, pendingCode);
            } else {
                tvStatus.setText("验证已取消");
                btnLogin.setEnabled(true);
            }
        }
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

