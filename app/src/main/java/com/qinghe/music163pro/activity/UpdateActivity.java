package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.qinghe.music163pro.util.UpdateChecker;

import java.io.File;

/**
 * Shows a "new version available" dialog asking the user to update.
 * Launched when a newer version is detected.
 * Handles APK download and installation.
 */
public class UpdateActivity extends AppCompatActivity {

    private static final String APK_SAVE_PATH =
            Environment.getExternalStorageDirectory() + "/163Music/update.apk";
    private static final int STORAGE_PERMISSION_REQUEST = 200;

    private TextView tvProgress;
    private ProgressBar progressBar;
    private TextView btnUpdate;
    private boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply keep screen on setting
        try {
            android.content.SharedPreferences prefs =
                    getSharedPreferences("music163_settings", MODE_PRIVATE);
            if (prefs.getBoolean("keep_screen_on", false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception ignored) {}

        buildUI();
    }

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF212121);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(px(16), px(24), px(16), px(20));

        // Icon / header
        TextView tvTitle = makeText("🎵 发现新版本", 0xFFFFFFFF, px(20), true, Gravity.CENTER);
        root.addView(tvTitle);

        root.addView(makeSpacer(px(14)));

        // Message
        TextView tvMsg = makeText("有新版本可用，是否需要更新？", 0xFFCCCCCC, px(15), false, Gravity.CENTER);
        root.addView(tvMsg);

        root.addView(makeSpacer(px(20)));

        // Progress bar (hidden initially)
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(8));
        progressBar.setLayoutParams(pbParams);
        progressBar.setVisibility(android.view.View.GONE);
        root.addView(progressBar);

        root.addView(makeSpacer(px(4)));

        // Progress text (hidden initially)
        tvProgress = makeText("", 0xFF888888, px(13), false, Gravity.CENTER);
        tvProgress.setVisibility(android.view.View.GONE);
        root.addView(tvProgress);

        root.addView(makeSpacer(px(16)));

        // Buttons row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRow.setLayoutParams(rowParams);

        // Cancel button
        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消");
        btnCancel.setTextColor(0xFFCCCCCC);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackgroundColor(0xFF424242);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cancelParams.setMarginEnd(px(6));
        btnCancel.setLayoutParams(cancelParams);
        btnCancel.setPadding(0, px(12), 0, px(12));
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setOnClickListener(v -> finish());

        // Update button
        btnUpdate = new TextView(this);
        btnUpdate.setText("更新");
        btnUpdate.setTextColor(0xFFFFFFFF);
        btnUpdate.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        btnUpdate.setTypeface(btnUpdate.getTypeface(), Typeface.BOLD);
        btnUpdate.setGravity(Gravity.CENTER);
        btnUpdate.setBackgroundColor(0xFFD32F2F);
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnUpdate.setLayoutParams(updateParams);
        btnUpdate.setPadding(0, px(12), 0, px(12));
        btnUpdate.setClickable(true);
        btnUpdate.setFocusable(true);
        btnUpdate.setOnClickListener(v -> requestPermissionAndDownload());

        btnRow.addView(btnCancel);
        btnRow.addView(btnUpdate);
        root.addView(btnRow);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void requestPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        }, STORAGE_PERMISSION_REQUEST);
                return;
            }
        }
        startDownload();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload();
            } else {
                Toast.makeText(this, "需要存储权限才能下载更新", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startDownload() {
        if (downloading) return;
        downloading = true;

        btnUpdate.setEnabled(false);
        btnUpdate.setText("下载中...");
        btnUpdate.setBackgroundColor(0xFF888888);
        progressBar.setVisibility(android.view.View.VISIBLE);
        tvProgress.setVisibility(android.view.View.VISIBLE);
        tvProgress.setText("0%");

        // Ensure output directory exists
        File dir = new File(Environment.getExternalStorageDirectory(), "163Music");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            downloading = false;
            btnUpdate.setEnabled(true);
            btnUpdate.setText("更新");
            btnUpdate.setBackgroundColor(0xFFD32F2F);
            return;
        }

        UpdateChecker.downloadUpdate(APK_SAVE_PATH, new UpdateChecker.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                progressBar.setProgress(percent);
                tvProgress.setText(percent + "%");
            }

            @Override
            public void onComplete(String filePath) {
                tvProgress.setText("下载完成，正在安装...");
                installApk(filePath);
            }

            @Override
            public void onError(String error) {
                downloading = false;
                btnUpdate.setEnabled(true);
                btnUpdate.setText("重试");
                btnUpdate.setBackgroundColor(0xFFD32F2F);
                tvProgress.setText("下载失败: " + error);
                Toast.makeText(UpdateActivity.this, "下载失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void installApk(String filePath) {
        try {
            File apkFile = new File(filePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile),
                        "application/vnd.android.package-archive");
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            downloading = false;
            btnUpdate.setEnabled(true);
            btnUpdate.setText("重试");
            btnUpdate.setBackgroundColor(0xFFD32F2F);
        }
    }

    private TextView makeText(String text, int color, int sizePx, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(gravity);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private android.view.View makeSpacer(int heightPx) {
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
