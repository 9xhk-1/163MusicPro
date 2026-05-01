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

import com.qinghe.music163pro.api.ImoowApiHelper;
import com.qinghe.music163pro.util.UpdateChecker;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Shows a "new version available" dialog asking the user to update.
 * Launched when a newer version is detected.
 * Fetches available download sources and lets the user choose one.
 * Handles APK download and installation.
 */
public class UpdateActivity extends AppCompatActivity {

    private static final String APK_SAVE_PATH =
            Environment.getExternalStorageDirectory() + "/163Music/update.apk";
    private static final int STORAGE_PERMISSION_REQUEST = 200;
    private static final String UPDATE_BULLET = "•";

    private TextView tvProgress;
    private ProgressBar progressBar;
    private LinearLayout sourcesContainer;
    private TextView tvSourceHint;
    private TextView tvVersionInfo;
    private TextView tvUpdateLog;
    private boolean downloading = false;
    private String selectedUrl = null;
    private String targetVersionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            android.content.SharedPreferences prefs =
                    getSharedPreferences("music163_settings", MODE_PRIVATE);
            if (prefs.getBoolean("keep_screen_on", false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception ignored) {}

        targetVersionName = getIntent().getStringExtra("target_version_name");
        buildUI();
        loadTargetVersionInfo();
        loadSources();
    }

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF212121);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(px(16), px(24), px(16), px(20));

        TextView tvTitle = makeText("发现新版本", 0xFFFFFFFF, px(20), true, Gravity.CENTER);
        root.addView(tvTitle);

        root.addView(makeSpacer(px(10)));

        TextView tvMsg = makeText("请选择下载源进行更新", 0xFFCCCCCC, px(15), false, Gravity.CENTER);
        root.addView(tvMsg);

        root.addView(makeSpacer(px(6)));

        tvVersionInfo = makeText("最新版本：获取中...", 0xFFAAAAAA, px(13), false, Gravity.CENTER);
        root.addView(tvVersionInfo);

        root.addView(makeSpacer(px(10)));

        TextView tvUpdateTitle = makeText("更新日志", 0xFFFFFFFF, px(16), true, Gravity.START);
        root.addView(tvUpdateTitle);

        root.addView(makeSpacer(px(4)));

        tvUpdateLog = makeText("正在获取更新日志...", 0xFFAAAAAA, px(13), false, Gravity.START);
        root.addView(tvUpdateLog);

        root.addView(makeSpacer(px(10)));

        // Hint
        tvSourceHint = makeText("建议优先选择上面的下载源", 0xFF888888, px(12), false, Gravity.CENTER);
        tvSourceHint.setVisibility(android.view.View.GONE);
        root.addView(tvSourceHint);

        root.addView(makeSpacer(px(12)));

        // Sources list container
        sourcesContainer = new LinearLayout(this);
        sourcesContainer.setOrientation(LinearLayout.VERTICAL);
        sourcesContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sourcesContainer);

        // Loading placeholder
        TextView tvLoading = makeText("正在获取下载源...", 0xFF888888, px(14), false, Gravity.CENTER);
        tvLoading.setTag("loading");
        sourcesContainer.addView(tvLoading);

        root.addView(makeSpacer(px(12)));

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

        root.addView(makeSpacer(px(14)));

        // Cancel button
        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消");
        btnCancel.setTextColor(0xB3FFFFFF);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setBackgroundColor(0xFF424242);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancel.setLayoutParams(cancelParams);
        btnCancel.setPadding(0, px(12), 0, px(12));
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setOnClickListener(v -> finish());
        root.addView(btnCancel);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void loadTargetVersionInfo() {
        if (targetVersionName != null && !targetVersionName.trim().isEmpty()) {
            targetVersionName = targetVersionName.trim();
            tvVersionInfo.setText("最新版本：" + targetVersionName);
            loadUpdateLog();
            return;
        }
        UpdateChecker.checkVersionInfo(this, new UpdateChecker.VersionCheckCallback() {
            @Override
            public void onResult(UpdateChecker.VersionInfo versionInfo) {
                targetVersionName = versionInfo.getVersionName();
                if (targetVersionName == null) {
                    targetVersionName = "";
                }
                targetVersionName = targetVersionName.trim();
                if (targetVersionName.isEmpty()) {
                    tvVersionInfo.setText("最新版本：获取失败");
                    tvUpdateLog.setText("获取失败");
                    return;
                }
                tvVersionInfo.setText("最新版本：" + targetVersionName);
                loadUpdateLog();
            }

            @Override
            public void onError(String error) {
                tvVersionInfo.setText("最新版本：获取失败");
                tvUpdateLog.setText("获取失败");
            }
        });
    }

    private void loadUpdateLog() {
        ImoowApiHelper.fetchAboutContent(new ImoowApiHelper.AboutCallback() {
            @Override
            public void onResult(ImoowApiHelper.AboutContent aboutContent) {
                ImoowApiHelper.UpdateLogItem matchedLog = null;
                List<ImoowApiHelper.UpdateLogItem> items = aboutContent.getUpdateContent();
                if (items != null) {
                    for (ImoowApiHelper.UpdateLogItem item : items) {
                        if (item != null && targetVersionName.equals(item.getVersion())) {
                            matchedLog = item;
                            break;
                        }
                    }
                }
                if (matchedLog == null) {
                    tvUpdateLog.setText("获取失败");
                    return;
                }
                tvUpdateLog.setText(formatUpdateLog(matchedLog.getContent()));
            }

            @Override
            public void onError(String error) {
                tvUpdateLog.setText("获取失败");
            }
        });
    }

    private String formatUpdateLog(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "获取失败";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(item.startsWith(UPDATE_BULLET)
                    ? item : UPDATE_BULLET + " " + item);
        }
        return builder.length() == 0 ? "获取失败" : builder.toString();
    }

    private void loadSources() {
        UpdateChecker.fetchSources(new UpdateChecker.SourcesCallback() {
            @Override
            public void onResult(List<String> urls) {
                showSources(urls);
            }

            @Override
            public void onError(String error) {
                sourcesContainer.removeAllViews();
                TextView tvErr = makeText("获取下载源失败: " + error, 0xFFFF6666, px(13), false, Gravity.CENTER);
                sourcesContainer.addView(tvErr);
            }
        });
    }

    private void showSources(List<String> urls) {
        sourcesContainer.removeAllViews();
        if (urls == null || urls.isEmpty()) {
            TextView tvErr = makeText("暂无可用下载源", 0xFF888888, px(14), false, Gravity.CENTER);
            sourcesContainer.addView(tvErr);
            return;
        }

        tvSourceHint.setVisibility(android.view.View.VISIBLE);

        for (int i = 0; i < urls.size(); i++) {
            final String downloadUrl = urls.get(i);
            final int index = i;

            String domain = extractDomain(downloadUrl);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF2D2D2D);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, px(6));
            row.setLayoutParams(rowParams);
            row.setPadding(px(12), px(10), px(12), px(10));
            row.setClickable(true);
            row.setFocusable(true);

            TextView tvIndex = new TextView(this);
            tvIndex.setText(String.valueOf(index + 1));
            tvIndex.setTextColor(0xFFBB86FC);
            tvIndex.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
            tvIndex.setTypeface(tvIndex.getTypeface(), Typeface.BOLD);
            tvIndex.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams idxParams = new LinearLayout.LayoutParams(px(24), LinearLayout.LayoutParams.WRAP_CONTENT);
            tvIndex.setLayoutParams(idxParams);

            TextView tvDomain = new TextView(this);
            tvDomain.setText(domain);
            tvDomain.setTextColor(0xFFFFFFFF);
            tvDomain.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
            tvDomain.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            LinearLayout.LayoutParams domainParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            domainParams.setMarginStart(px(8));
            tvDomain.setLayoutParams(domainParams);

            TextView tvDownload = new TextView(this);
            tvDownload.setText("下载");
            tvDownload.setTextColor(0xFFBB86FC);
            tvDownload.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
            tvDownload.setGravity(Gravity.CENTER);

            row.addView(tvIndex);
            row.addView(tvDomain);
            row.addView(tvDownload);

            row.setOnClickListener(v -> {
                if (!downloading) {
                    selectedUrl = downloadUrl;
                    requestPermissionAndDownload();
                }
            });

            sourcesContainer.addView(row);
        }
    }

    private String extractDomain(String rawUrl) {
        try {
            URL u = new URL(rawUrl);
            return u.getHost();
        } catch (Exception e) {
            return rawUrl;
        }
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
        if (downloading || selectedUrl == null) return;
        downloading = true;

        // Disable all source rows
        for (int i = 0; i < sourcesContainer.getChildCount(); i++) {
            sourcesContainer.getChildAt(i).setClickable(false);
        }

        progressBar.setVisibility(android.view.View.VISIBLE);
        tvProgress.setVisibility(android.view.View.VISIBLE);
        tvProgress.setText("0%");

        File dir = new File(Environment.getExternalStorageDirectory(), "163Music");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            resetDownloadState();
            return;
        }

        UpdateChecker.downloadUpdate(selectedUrl, APK_SAVE_PATH, new UpdateChecker.DownloadCallback() {
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
                resetDownloadState();
                tvProgress.setText("下载失败: " + error);
                Toast.makeText(UpdateActivity.this, "下载失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetDownloadState() {
        downloading = false;
        for (int i = 0; i < sourcesContainer.getChildCount(); i++) {
            sourcesContainer.getChildAt(i).setClickable(true);
        }
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
            resetDownloadState();
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
