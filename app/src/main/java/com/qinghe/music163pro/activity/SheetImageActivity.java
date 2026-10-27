package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.ZoomableImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 乐谱 (sheet music) full-screen viewer.
 *
 * - Opens from the sheet card in SongInfoActivity (image_url / title extras).
 * - Pinch to zoom, double-tap to toggle zoom, drag to pan (ZoomableImageView).
 * - Long-press shows a dialog offering to save the image to
 *   /sdcard/163Music/sheet/<title>_<timestamp>.jpg (visible in gallery).
 *
 * The source image may be http(s) or a local file:// path; decoding is
 * sampled so the longest edge stays <= 2048px (watch memory safety) while
 * staying sharp enough for on-screen zoom.
 */
public class SheetImageActivity extends AppCompatActivity {

    private static final String TAG = "SheetImageActivity";
    private static final int MAX_DECODE_DIM = 2048;
    private static final String PREFS_NAME = "music163_settings";

    private ZoomableImageView zoomView;
    private TextView tvStatus;
    private String imageUrl;
    private String title;
    /** Loaded bitmap kept for the save action. */
    private Bitmap loadedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        imageUrl = getIntent().getStringExtra("image_url");
        title = getIntent().getStringExtra("title");
        MusicLog.op(TAG, "打开乐谱", "url=" + imageUrl + " title=" + title);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        zoomView = new ZoomableImageView(this);
        root.addView(zoomView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        tvStatus = new TextView(this);
        tvStatus.setText(imageUrl == null || imageUrl.isEmpty() ? "缺少图片地址" : "加载中...");
        tvStatus.setTextColor(0x80FFFFFF);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        tvStatus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        statusLp.gravity = Gravity.CENTER;
        root.addView(tvStatus, statusLp);

        setContentView(root);

        zoomView.setOnLongClickListener(v -> {
            showSaveDialog();
            return true;
        });

        if (imageUrl != null && !imageUrl.isEmpty()) {
            loadSheetImage(imageUrl);
        }
    }

    private void loadSheetImage(final String url) {
        new Thread(() -> {
            try {
                byte[] data = fetchBytes(url);
                if (data == null || data.length == 0) {
                    postStatus("图片加载失败");
                    return;
                }
                Bitmap bm = decodeSampled(data, MAX_DECODE_DIM);
                if (bm == null) {
                    // Out of memory or corrupt — retry at lower resolution
                    bm = decodeSampled(data, MAX_DECODE_DIM / 2);
                }
                if (bm == null) {
                    postStatus("图片解码失败");
                    return;
                }
                final Bitmap result = bm;
                runOnUiThread(() -> {
                    loadedBitmap = result;
                    zoomView.setImageBitmap(result);
                    zoomView.post(() -> zoomView.fitCenter());
                    tvStatus.setVisibility(android.view.View.GONE);
                });
            } catch (Exception e) {
                MusicLog.w(TAG, "加载乐谱图片失败: " + url, e);
                postStatus("图片加载失败");
            }
        }).start();
    }

    private void postStatus(final String text) {
        runOnUiThread(() -> tvStatus.setText(text));
    }

    private static byte[] fetchBytes(String urlStr) throws Exception {
        if (urlStr.startsWith("file://")) {
            InputStream is = null;
            try {
                is = new FileInputStream(urlStr.substring(7));
                return readAll(is);
            } finally {
                try {
                    if (is != null) is.close();
                } catch (Exception ignored) {
                }
            }
        }
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.1.0) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            conn.connect();
            is = conn.getInputStream();
            return readAll(is);
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static Bitmap decodeSampled(byte[] data, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (OutOfMemoryError e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ─── 长按 → 保存对话框 ─────────────────────────────────────────────

    private void showSaveDialog() {
        if (loadedBitmap == null) {
            Toast.makeText(this, "图片尚未加载完成", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("乐谱图片")
                .setItems(new String[]{"保存图片"}, (dialog, which) -> saveImage())
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveImage() {
        final Bitmap bm = loadedBitmap;
        if (bm == null) return;
        final String dirPath = new File(
                Environment.getExternalStorageDirectory(), "163Music/sheet").getAbsolutePath();
        final String name = sanitizeFileName(title) + "_" + System.currentTimeMillis() + ".jpg";
        new Thread(() -> {
            try {
                File dir = new File(dirPath);
                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() ->
                            Toast.makeText(SheetImageActivity.this, "无法创建目录", Toast.LENGTH_SHORT).show());
                    return;
                }
                File out = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(out);
                bm.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                // 让图库扫描到新文件
                MediaScannerConnection.scanFile(SheetImageActivity.this,
                        new String[]{out.getAbsolutePath()},
                        new String[]{"image/jpeg"}, null);
                final String path = out.getAbsolutePath();
                runOnUiThread(() ->
                        Toast.makeText(SheetImageActivity.this, "已保存: " + path, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                MusicLog.w(TAG, "保存乐谱图片失败", e);
                runOnUiThread(() ->
                        Toast.makeText(SheetImageActivity.this, "保存失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static String sanitizeFileName(String s) {
        if (s == null || s.trim().isEmpty()) return "sheet";
        String clean = s.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return clean.length() > 40 ? clean.substring(0, 40) : clean;
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
