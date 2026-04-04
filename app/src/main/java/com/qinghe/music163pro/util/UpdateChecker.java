package com.qinghe.music163pro.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Utility for checking app updates and downloading the latest APK.
 * API base: https://api.163-music-pro.imoow.com
 */
public class UpdateChecker {

    private static final String BASE_URL = "https://api.163-music-pro.imoow.com";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CheckCallback {
        void onResult(boolean isLatest);
        void onError(String error);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(String filePath);
        void onError(String error);
    }

    /**
     * POST /check with the app's versionCode.
     * Calls callback on main thread.
     */
    public static void checkVersion(Context context, CheckCallback callback) {
        int versionCode;
        try {
            versionCode = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            mainHandler.post(() -> callback.onError("获取版本号失败"));
            return;
        }
        final int vc = versionCode;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/check");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                byte[] body = ("{\"version\":" + vc + "}").getBytes(Charset.forName("UTF-8"));
                OutputStream reqOs = conn.getOutputStream();
                try {
                    reqOs.write(body);
                    reqOs.flush();
                } finally {
                    reqOs.close();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    try {
                        byte[] buf = new byte[1024];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            sb.append(new String(buf, 0, n, Charset.forName("UTF-8")));
                        }
                    } finally {
                        is.close();
                    }
                    JSONObject resp = new JSONObject(sb.toString());
                    boolean isLatest = resp.getJSONObject("data").getBoolean("is_latest");
                    mainHandler.post(() -> callback.onResult(isLatest));
                } else {
                    mainHandler.post(() -> callback.onError("HTTP " + code));
                }
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(() -> callback.onError(msg != null ? msg : "网络错误"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * GET /download — streams the APK to savePath.
     * Progress and completion callbacks are posted to the main thread.
     */
    public static void downloadUpdate(String savePath, DownloadCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/download");
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
