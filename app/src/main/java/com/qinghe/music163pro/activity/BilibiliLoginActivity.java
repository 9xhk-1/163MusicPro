package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;
import com.qinghe.music163pro.util.QrCodeGenerator;

/**
 * Bilibili QR code login activity.
 * Generates QR code for Bilibili web login and polls for scan status.
 */
public class BilibiliLoginActivity extends BaseWatchActivity {

    private ImageView ivQrCode;
    private TextView tvStatus;
    private MaterialButton btnRefresh;

    private String qrcodeKey = "";
    private final Handler pollHandler = new Handler();
    private boolean polling = false;

    private static final int POLL_INTERVAL_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(px(10), px(8), px(10), px(10));

        container.addView(createTitleBar());

        LinearLayout qrCard = new LinearLayout(this);
        qrCard.setOrientation(LinearLayout.VERTICAL);
        qrCard.setGravity(Gravity.CENTER_HORIZONTAL);
        qrCard.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        qrCard.setPadding(px(10), px(10), px(10), px(10));
        qrCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(qrCard);

        ivQrCode = new ImageView(this);
        int qrSize = px(140);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        ivQrCode.setLayoutParams(qrParams);
        ivQrCode.setBackgroundColor(getResources().getColor(R.color.qr_background));
        ivQrCode.setPadding(px(4), px(4), px(4), px(4));
        qrCard.addView(ivQrCode);

        tvStatus = new TextView(this);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(11);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, px(8), 0, px(4));
        qrCard.addView(tvStatus);

        btnRefresh = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRefresh.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(42)));
        btnRefresh.setText("刷新二维码");
        btnRefresh.setTextSize(12);
        btnRefresh.setAllCaps(false);
        btnRefresh.setOnClickListener(v -> startQrLogin());
        qrCard.addView(btnRefresh);

        scrollView.addView(container);
        setContentView(scrollView);

        startQrLogin();
    }

    private void startQrLogin() {
        stopPolling();
        tvStatus.setText("正在获取二维码...");
        ivQrCode.setImageBitmap(null);

        BilibiliApiHelper.generateQrCode(new BilibiliApiHelper.QrCodeCallback() {
            @Override
            public void onResult(String qrUrl, String key) {
                qrcodeKey = key;
                Bitmap qrBitmap = generateQrBitmap(qrUrl, 300);
                if (qrBitmap != null) {
                    ivQrCode.setImageBitmap(qrBitmap);
                    tvStatus.setText("请使用哔哩哔哩App扫码登录");
                    startPolling();
                } else {
                    tvStatus.setText("二维码生成失败");
                }
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("获取二维码失败: " + message);
            }
        });
    }

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
            if (!polling || qrcodeKey.isEmpty()) return;

            BilibiliApiHelper.pollQrLogin(qrcodeKey, new BilibiliApiHelper.QrPollCallback() {
                @Override
                public void onResult(int code, String message, String cookie) {
                    switch (code) {
                        case 86101:
                            tvStatus.setText("等待扫码...");
                            if (polling) {
                                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                            break;
                        case 86090:
                            tvStatus.setText("已扫码，请在手机上确认登录");
                            if (polling) {
                                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                            }
                            break;
                        case 0:
                            // Login successful
                            stopPolling();
                            if (cookie == null || cookie.isEmpty()) {
                                tvStatus.setText("登录成功但未获取到Cookie");
                                break;
                            }
                            tvStatus.setText("登录成功!");
                            saveBilibiliCookie(cookie);
                            Toast.makeText(BilibiliLoginActivity.this,
                                    "B站登录成功", Toast.LENGTH_LONG).show();
                            pollHandler.postDelayed(() -> finish(), 1500);
                            break;
                        case 86038:
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

    private void saveBilibiliCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putString("bilibili_cookie", cookie).apply();
    }

    private LinearLayout createTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(38)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(getResources().getColor(R.color.text_primary));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText("B站扫码登录");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        ImageView placeholder = new ImageView(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);
        return bar;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
