package com.qinghe.music163pro.activity;

import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.util.WatchConfirmDialog;

import java.io.File;

/**
 * Cache management activity - shows cache sizes and provides a clear button.
 * Designed for watch screen (320x360 dpi).
 */
public class CacheManageActivity extends AppCompatActivity {

    private static final String BASE_DIR = "163Music";

    private TextView tvCacheSize;
    private TextView tvDownloadSize;
    private TextView tvImageCacheInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF212121);

        // Title bar
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setPadding(px(12), px(8), px(12), px(8));
        titleBar.setBackgroundColor(0xFF1A1A1A);
        root.addView(titleBar);

        TextView tvBack = new TextView(this);
        tvBack.setText("‹");
        tvBack.setTextColor(0xFFBB86FC);
        tvBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(22));
        tvBack.setPadding(0, 0, px(8), 0);
        tvBack.setClickable(true);
        tvBack.setFocusable(true);
        tvBack.setOnClickListener(v -> finish());
        android.widget.RelativeLayout.LayoutParams backParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        backParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
        backParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        tvBack.setLayoutParams(backParams);
        titleBar.addView(tvBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("缓存管理");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tvTitle.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        tvTitle.setLayoutParams(titleParams);
        titleBar.addView(tvTitle);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollView.setLayoutParams(svParams);
        root.addView(scrollView);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(px(14), px(12), px(14), px(12));
        scrollView.addView(contentLayout);

        // Image cache info row
        tvImageCacheInfo = makeInfoRow(contentLayout, "图片缓存", "内存中（不占磁盘）");

        // Disk cache size row
        tvCacheSize = makeInfoRow(contentLayout, "磁盘缓存", "计算中...");

        // Download size row
        tvDownloadSize = makeInfoRow(contentLayout, "已下载音乐", "计算中...");

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divParams.setMargins(0, px(12), 0, px(12));
        divider.setLayoutParams(divParams);
        contentLayout.addView(divider);

        // Clear cache button
        TextView btnClearCache = new TextView(this);
        btnClearCache.setText("清理磁盘缓存");
        btnClearCache.setTextColor(0xFFBB86FC);
        btnClearCache.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        btnClearCache.setGravity(Gravity.CENTER);
        btnClearCache.setPadding(px(12), px(14), px(12), px(14));
        btnClearCache.setClickable(true);
        btnClearCache.setFocusable(true);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(0xFF2D2D2D);
        btnBg.setCornerRadius(px(6));
        btnClearCache.setBackground(btnBg);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, 0, px(8));
        btnClearCache.setLayoutParams(btnParams);
        btnClearCache.setOnClickListener(v -> showClearCacheDialog());
        contentLayout.addView(btnClearCache);

        setContentView(root);

        // Calculate sizes in background
        refreshSizes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSizes();
    }

    private void refreshSizes() {
        new Thread(() -> {
            File baseDir = new File(Environment.getExternalStorageDirectory(), BASE_DIR);
            File cacheDir = new File(baseDir, "cache");
            File downloadDir = new File(baseDir, "Download");

            long cacheBytes = getDirSize(cacheDir);
            long downloadBytes = getDirSize(downloadDir);

            String cacheStr = formatSize(cacheBytes);
            String downloadStr = formatSize(downloadBytes);

            runOnUiThread(() -> {
                tvCacheSize.setText(cacheStr);
                tvDownloadSize.setText(downloadStr);
            });
        }).start();
    }

    private void showClearCacheDialog() {
        WatchConfirmDialog.show(this, "清理缓存", "确定清理磁盘缓存？下载的音乐不会被删除。", () -> {
            new Thread(() -> {
                File cacheDir = new File(new File(Environment.getExternalStorageDirectory(), BASE_DIR), "cache");
                boolean success = deleteDir(cacheDir);
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "清理完成", Toast.LENGTH_SHORT).show();
                    }
                    refreshSizes();
                });
            }).start();
        }, new WatchConfirmDialog.Options(0xFF1E1E1E, 0xFFBB86FC, true));
    }

    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return true;
        boolean ok = true;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    ok &= deleteDir(f);
                }
            }
        }
        return dir.delete() && ok;
    }

    private long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    size += getDirSize(f);
                }
            }
        } else {
            size = dir.length();
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Creates a labeled info row and returns the value TextView. */
    private TextView makeInfoRow(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, px(10));
        row.setLayoutParams(rowParams);
        row.setPadding(px(12), px(12), px(12), px(12));
        android.graphics.drawable.GradientDrawable rowBg = new android.graphics.drawable.GradientDrawable();
        rowBg.setColor(0xFF2D2D2D);
        rowBg.setCornerRadius(px(6));
        row.setBackground(rowBg);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xCCFFFFFF);
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvLabel.setLayoutParams(labelParams);
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(initialValue);
        tvValue.setTextColor(0x80FFFFFF);
        tvValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvValue.setGravity(Gravity.END);
        row.addView(tvValue);

        parent.addView(row);
        return tvValue;
    }

    private int px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
