package com.qinghe.music163pro.api;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * API helper for app-level server calls (update check, download sources, feedback).
 * API base: https://163.imoow.com
 */
public class AppApiHelper {

    private static final String BASE_URL = "https://163.imoow.com";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CheckCallback {
        void onResult(boolean isLatest);
        void onError(String error);
    }

    public interface SourcesCallback {
        void onResult(List<String> urls);
        void onError(String error);
    }

    public interface SuggestCallback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * GET /source — returns list of download URLs.
     * Response: {code:200, data:[url1,url2,...]}
     * Calls callback on main thread.
     */
    public static void fetchSources(SourcesCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/source");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

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
                    JSONArray arr = resp.getJSONArray("data");
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        urls.add(arr.getString(i));
                    }
                    mainHandler.post(() -> callback.onResult(urls));
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
     * POST /check with the app's versionCode.
     * Response: {code:200, data:{is_latest:bool}}
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
     * POST /suggest with JSON body {content: string}.
     * Calls callback on main thread.
     */
    public static void postSuggest(String content, SuggestCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/suggest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject bodyJson = new JSONObject();
                bodyJson.put("content", content);
                byte[] body = bodyJson.toString().getBytes(Charset.forName("UTF-8"));
                OutputStream reqOs = conn.getOutputStream();
                try {
                    reqOs.write(body);
                    reqOs.flush();
                } finally {
                    reqOs.close();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    mainHandler.post(callback::onSuccess);
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
}
