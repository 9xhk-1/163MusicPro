package com.qinghe.music163pro.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.qinghe.music163pro.api.AppApiHelper;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Utility for checking app updates and downloading the latest APK.
 * API calls are delegated to AppApiHelper.
 */
public class UpdateChecker {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CheckCallback {
        void onResult(boolean isLatest);
        void onError(String error);
    }

    public interface SourcesCallback {
        void onResult(List<String> urls);
        void onError(String error);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(String filePath);
        void onError(String error);
    }

    /**
     * GET /source — returns list of download URLs.
     * Delegates to AppApiHelper.fetchSources().
     */
    public static void fetchSources(SourcesCallback callback) {
        AppApiHelper.fetchSources(new AppApiHelper.SourcesCallback() {
            @Override
            public void onResult(List<String> urls) {
                callback.onResult(urls);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * POST /check with the app's versionCode.
     * Delegates to AppApiHelper.checkVersion().
     */
    public static void checkVersion(Context context, CheckCallback callback) {
        AppApiHelper.checkVersion(context, new AppApiHelper.CheckCallback() {
            @Override
            public void onResult(boolean isLatest) {
                callback.onResult(isLatest);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Downloads an APK from the given downloadUrl to savePath.
     * Progress and completion callbacks are posted to the main thread.
     */
    public static void downloadUpdate(String downloadUrl, String savePath, DownloadCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(downloadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.connect();

                int totalLength = conn.getContentLength();
                InputStream is = new BufferedInputStream(conn.getInputStream());
                OutputStream os = new FileOutputStream(savePath);

                try {
                    byte[] buf = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    int lastPercent = -1;
                    while ((bytesRead = is.read(buf)) != -1) {
                        os.write(buf, 0, bytesRead);
                        totalRead += bytesRead;
                        if (totalLength > 0) {
                            int percent = (int) (totalRead * 100L / totalLength);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                mainHandler.post(() -> callback.onProgress(p));
                            }
                        }
                    }
                } finally {
                    os.close();
                    is.close();
                }
                final String path = savePath;
                mainHandler.post(() -> callback.onComplete(path));
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> callback.onError(msg != null ? msg : "下载失败"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
