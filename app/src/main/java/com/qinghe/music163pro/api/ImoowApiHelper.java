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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * API helper for all 163.imoow.com endpoints used by the app.
 */
public final class ImoowApiHelper {

    private static final String BASE_URL = "https://163.imoow.com";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ImoowApiHelper() {
    }

    public interface CheckCallback {
        void onResult(boolean isLatest);
        void onError(String error);
    }

    public interface SourcesCallback {
        void onResult(List<String> urls);
        void onError(String error);
    }

    public interface QaCallback {
        void onResult(List<QaItem> items);
        void onError(String error);
    }

    public interface AboutCallback {
        void onResult(AboutContent aboutContent);
        void onError(String error);
    }

    public static class QaItem {
        private final String question;
        private final String answer;

        public QaItem(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }
    }

    public static class AboutContent {
        private final String overview;
        private final List<UpdateLogItem> updateContent;

        public AboutContent(String overview, List<UpdateLogItem> updateContent) {
            this.overview = overview;
            this.updateContent = updateContent;
        }

        public String getOverview() {
            return overview;
        }

        public List<UpdateLogItem> getUpdateContent() {
            return updateContent;
        }
    }

    public static class UpdateLogItem {
        private final String version;
        private final List<String> content;

        public UpdateLogItem(String version, List<String> content) {
            this.version = version;
            this.content = content;
        }

        public String getVersion() {
            return version;
        }

        public List<String> getContent() {
            return content;
        }
    }

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
                if (code != 200) {
                    postError(callback, "HTTP " + code);
                    return;
                }

                JSONObject resp = new JSONObject(readResponse(conn.getInputStream()));
                JSONArray arr = resp.getJSONArray("data");
                List<String> urls = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String downloadUrl = arr.optString(i, "").trim();
                    if (!downloadUrl.isEmpty()) {
                        urls.add(downloadUrl);
                    }
                }
                MAIN_HANDLER.post(() -> callback.onResult(urls));
            } catch (Exception e) {
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    public static void checkVersion(Context context, CheckCallback callback) {
        final int versionCode;
        try {
            versionCode = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            postError(callback, "获取版本号失败");
            return;
        }

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

                byte[] body = ("{\"version\":" + versionCode + "}").getBytes(StandardCharsets.UTF_8);
                OutputStream outputStream = conn.getOutputStream();
                try {
                    outputStream.write(body);
                    outputStream.flush();
                } finally {
                    outputStream.close();
                }

                int code = conn.getResponseCode();
                if (code != 200) {
                    postError(callback, "HTTP " + code);
                    return;
                }

                JSONObject resp = new JSONObject(readResponse(conn.getInputStream()));
                boolean isLatest = resp.getJSONObject("data").getBoolean("is_latest");
                MAIN_HANDLER.post(() -> callback.onResult(isLatest));
            } catch (Exception e) {
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    public static void fetchQaList(QaCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/qa.json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200) {
                    postError(callback, "HTTP " + code);
                    return;
                }

                JSONArray array = new JSONArray(readResponse(conn.getInputStream()));
                List<QaItem> items = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String question = item.optString("q", "").trim();
                    String answer = item.optString("a", "").trim();
                    if (!question.isEmpty()) {
                        items.add(new QaItem(question, answer));
                    }
                }
                MAIN_HANDLER.post(() -> callback.onResult(items));
            } catch (Exception e) {
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    public static void fetchAboutContent(AboutCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/about.json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200) {
                    postError(callback, "HTTP " + code);
                    return;
                }

                JSONObject object = new JSONObject(readResponse(conn.getInputStream()));
                String overview = object.optString("overview", "").trim();
                JSONArray updateArray = object.optJSONArray("updateContent");
                List<UpdateLogItem> updateItems = new ArrayList<>();
                if (updateArray != null) {
                    for (int i = 0; i < updateArray.length(); i++) {
                        JSONObject item = updateArray.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        String version = item.optString("version", "").trim();
                        JSONArray contentArray = item.optJSONArray("content");
                        List<String> contentItems = new ArrayList<>();
                        if (contentArray != null) {
                            for (int j = 0; j < contentArray.length(); j++) {
                                String content = contentArray.optString(j, "").trim();
                                if (!content.isEmpty()) {
                                    contentItems.add(content);
                                }
                            }
                        }
                        if (!version.isEmpty()) {
                            updateItems.add(new UpdateLogItem(version, contentItems));
                        }
                    }
                }

                AboutContent aboutContent = new AboutContent(overview, updateItems);
                MAIN_HANDLER.post(() -> callback.onResult(aboutContent));
            } catch (Exception e) {
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private static String readResponse(InputStream inputStream) throws Exception {
        try {
            byte[] buffer = new byte[1024];
            StringBuilder sb = new StringBuilder();
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } finally {
            inputStream.close();
        }
    }

    private static void postError(CheckCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onError(normalizeError(error)));
    }

    private static void postError(SourcesCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onError(normalizeError(error)));
    }

    private static void postError(QaCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onError(normalizeError(error)));
    }

    private static void postError(AboutCallback callback, String error) {
        MAIN_HANDLER.post(() -> callback.onError(normalizeError(error)));
    }

    private static String normalizeError(String error) {
        return error == null || error.trim().isEmpty() ? "网络错误" : error;
    }
}
