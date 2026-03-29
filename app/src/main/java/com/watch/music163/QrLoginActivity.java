package com.watch.music163;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * QR Code Login Activity.
 * Implements the same login flow as yumbo-music-utils:
 * 1. /login/qr/key - get unikey
 * 2. /login/qr/create - create QR code image
 * 3. /login/qr/check - poll for scan status
 */
public class QrLoginActivity extends AppCompatActivity {

    private ImageView ivQrCode;
    private TextView tvStatus;
    private TextView btnRefresh;

    private String qrKey = "";
    private final Handler pollHandler = new Handler();
    private boolean polling = false;

    private static final int POLL_INTERVAL_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_login);

        ivQrCode = findViewById(R.id.iv_qr_code);
        tvStatus = findViewById(R.id.tv_qr_status);
        btnRefresh = findViewById(R.id.btn_qr_refresh);

        btnRefresh.setOnClickListener(v -> startQrLogin());

        startQrLogin();
    }

    private void startQrLogin() {
        stopPolling();
        tvStatus.setText("正在获取二维码...");
        ivQrCode.setImageBitmap(null);

        // Step 1: Get QR key
        MusicApiHelper.loginQrKey(new MusicApiHelper.QrKeyCallback() {
            @Override
            public void onResult(String key) {
                qrKey = key;
                // Step 2: Create QR code
                MusicApiHelper.loginQrCreate(key, new MusicApiHelper.QrCreateCallback() {
                    @Override
                    public void onResult(String qrUrl, String qrBase64) {
                        displayQrCode(qrBase64);
                        tvStatus.setText("请使用网易云音乐App扫码登录");
                        // Step 3: Start polling
                        startPolling();
                    }

                    @Override
                    public void onError(String message) {
                        tvStatus.setText("创建二维码失败: " + message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("获取二维码失败: " + message);
            }
        });
    }

    private void displayQrCode(String base64Img) {
        try {
            // Remove data URI prefix if present
            String base64Data = base64Img;
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            if (bitmap != null) {
                ivQrCode.setImageBitmap(bitmap);
            } else {
                tvStatus.setText("二维码解析失败");
            }
        } catch (Exception e) {
            tvStatus.setText("二维码显示失败: " + e.getMessage());
        }
    }

    private void startPolling() {
        polling = true;
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        polling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling || qrKey.isEmpty()) return;

            MusicApiHelper.loginQrCheck(qrKey, new MusicApiHelper.QrCheckCallback() {
                @Override
                public void onResult(int code, String message, String cookie) {
                    switch (code) {
                        case 801:
                            tvStatus.setText("等待扫码...");
                            if (polling) {
                                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                            break;
                        case 802:
                            tvStatus.setText("已扫码，请在手机上确认登录");
                            if (polling) {
                                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                            break;
                        case 803:
                            // Login successful!
                            tvStatus.setText("登录成功!");
                            stopPolling();
                            saveCookie(cookie);
                            Toast.makeText(QrLoginActivity.this,
                                    "登录成功，Cookie已保存", Toast.LENGTH_LONG).show();
                            // Auto-close after brief delay
                            pollHandler.postDelayed(() -> finish(), 1500);
                            break;
                        case 800:
                            tvStatus.setText("二维码已过期，请刷新");
                            stopPolling();
                            break;
                        default:
                            tvStatus.setText("状态: " + code + " " + message);
                            if (polling) {
                                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                            break;
                    }
                }

                @Override
                public void onError(String errMsg) {
                    tvStatus.setText("检查状态失败: " + errMsg);
                    if (polling) {
                        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    }
                }
            });
        }
    };

    private void saveCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putString("cookie", cookie).apply();
        MusicPlayerManager.getInstance().setCookie(cookie);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
