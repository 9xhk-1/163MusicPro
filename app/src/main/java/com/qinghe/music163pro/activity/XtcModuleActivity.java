package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.UpdateChecker;

import java.io.File;

public class XtcModuleActivity extends BaseWatchActivity {

    private static final String MODULE_DOWNLOAD_URL =
            "https://gitee.com/xtcqinghe/jump-to-pro/raw/master/app.apk";
    private static final String MODULE_SAVE_PATH =
            Environment.getExternalStorageDirectory() + "/163Music/jump-to-pro.apk";
    private static final int STORAGE_PERMISSION_REQUEST = 301;

    private MaterialButton btnDownload;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private boolean downloading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xtc_module);

        btnDownload = findViewById(R.id.btn_download_xtc_module);
        progressBar = findViewById(R.id.progress_download_xtc_module);
        tvStatus = findViewById(R.id.tv_xtc_module_status);

        btnDownload.setOnClickListener(v -> requestPermissionAndDownload());
    }

    private void requestPermissionAndDownload() {
        if (downloading) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        startDownload();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload();
        } else {
            Toast.makeText(this, "需要存储权限才能下载模块", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), "163Music");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            return;
        }

        downloading = true;
        btnDownload.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        tvStatus.setText("正在下载模块…");

        UpdateChecker.downloadUpdate(MODULE_DOWNLOAD_URL, MODULE_SAVE_PATH, new UpdateChecker.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                progressBar.setProgress(percent);
                tvStatus.setText("正在下载模块… " + percent + "%");
            }

            @Override
            public void onComplete(String filePath) {
                downloading = false;
                btnDownload.setEnabled(true);
                progressBar.setProgress(100);
                tvStatus.setText("下载完成，正在打开安装界面…");
                installApk(filePath);
            }

            @Override
            public void onError(String error) {
                downloading = false;
                btnDownload.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("下载失败: " + error);
                Toast.makeText(XtcModuleActivity.this, "下载失败: " + error, Toast.LENGTH_SHORT).show();
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
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            startActivity(intent);
        } catch (Exception e) {
            tvStatus.setText("下载完成: " + filePath);
            Toast.makeText(this, "无法自动安装，请手动安装", Toast.LENGTH_SHORT).show();
        }
    }
}
