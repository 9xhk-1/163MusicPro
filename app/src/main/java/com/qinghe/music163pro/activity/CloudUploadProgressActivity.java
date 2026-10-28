package com.qinghe.music163pro.activity;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Upload progress screen for music cloud.
 */
public class CloudUploadProgressActivity extends BaseWatchActivity {

    private LinearProgressIndicator progressBar;
    private TextView tvProgress;
    private TextView tvStatus;
    private File cacheFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_upload_progress);

        progressBar = findViewById(R.id.progress_upload);
        tvProgress = findViewById(R.id.tv_upload_progress);
        tvStatus = findViewById(R.id.tv_upload_status);
        TextView tvType = findViewById(R.id.tv_upload_type);
        TextView tvName = findViewById(R.id.tv_upload_name);
        MaterialButton btnDone = findViewById(R.id.btn_upload_done);

        Uri uri = getIntent().getData();
        String uploadName = getIntent().getStringExtra("upload_name");
        boolean musicFile = getIntent().getBooleanExtra("upload_is_music", true);
        String resolvedName = (uploadName != null && !uploadName.isEmpty()) ? uploadName : "未命名文件";
        tvType.setText(musicFile ? "类型：音乐" : "类型：文件");
        tvName.setText(resolvedName);
        btnDone.setOnClickListener(v -> finish());

        if (uri == null) {
            Toast.makeText(this, "文件无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new Thread(() -> {
            try {
                cacheFile = copyUriToCache(uri, resolvedName);
                MusicApiHelper.uploadCloudFile(cacheFile, resolvedName, musicFile, cookie,
                        new MusicApiHelper.UploadProgressCallback() {
                            @Override
                            public void onProgress(int progress, String message) {
                                progressBar.setProgress(progress);
                                tvProgress.setText(progress + "%");
                                tvStatus.setText(message);
                            }

                            @Override
                            public void onSuccess(String message) {
                                progressBar.setProgress(100);
                                tvProgress.setText("100%");
                                tvStatus.setText(message);
                                btnDone.setVisibility(android.view.View.VISIBLE);
                                setResult(RESULT_OK);
                            }

                            @Override
                            public void onError(String message) {
                                tvStatus.setText(message);
                                btnDone.setVisibility(android.view.View.VISIBLE);
                            }
                        });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("准备失败: " + e.getMessage());
                    btnDone.setVisibility(android.view.View.VISIBLE);
                });
            }
        }).start();
    }

    private File copyUriToCache(Uri uri, String fileName) throws Exception {
        String resolvedName = fileName;
        if (resolvedName == null || resolvedName.isEmpty()) {
            resolvedName = queryDisplayName(uri);
        }
        File outFile = new File(getCacheDir(), resolvedName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            if (is == null) {
                throw new IllegalStateException("无法读取文件");
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
        }
        return outFile;
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "upload.bin";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cacheFile != null && cacheFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cacheFile.delete();
        }
    }
}
