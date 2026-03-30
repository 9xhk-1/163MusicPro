package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.QrCodeGenerator;

/**
 * QR Code Login Activity.
 * Implements the login flow by calling NetEase APIs directly:
 * 1. /api/login/qrcode/unikey (weapi) - get unikey
 * 2. Build QR URL locally: https://music.163.com/login?codekey=KEY
 * 3. /api/login/qrcode/client/login (weapi) - poll for scan status
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
                // Step 2: Build QR URL and generate QR image locally
                MusicApiHelper.loginQrCreate(key, new MusicApiHelper.QrCreateCallback() {
                    @Override
                    public void onResult(String qrUrl) {
                        Bitmap qrBitmap = generateQrBitmap(qrUrl, 300);
                        if (qrBitmap != null) {
                            ivQrCode.setImageBitmap(qrBitmap);
                            tvStatus.setText("请使用网易云音乐App扫码登录");
                            // Step 3: Start polling
                            startPolling();
                        } else {
                            tvStatus.setText("二维码生成失败");
                        }
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

    /**
     * Generate a simple QR code bitmap from a URL string.
     * Uses a minimal QR code generation algorithm (no external library needed).
     */
    private Bitmap generateQrBitmap(String content, int size) {
        try {
            boolean[][] matrix = QrCodeGenerator.encode(content);
            if (matrix == null) return null;

            int matrixSize = matrix.length;
            int cellSize = size / matrixSize;
            int bitmapSize = cellSize * matrixSize;

            Bitmap bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < matrixSize; y++) {
                for (int x = 0; x < matrixSize; x++) {
                    int color = matrix[y][x] ? Color.BLACK : Color.WHITE;
                    for (int dy = 0; dy < cellSize; dy++) {
                        for (int dx = 0; dx < cellSize; dx++) {
                            bitmap.setPixel(x * cellSize + dx, y * cellSize + dy, color);
                        }
                    }
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
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
                            stopPolling();
                            if (cookie == null || cookie.isEmpty()) {
                                tvStatus.setText("登录成功但未获取到Cookie");
                                break;
                            }
                            tvStatus.setText("登录成功!");
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
