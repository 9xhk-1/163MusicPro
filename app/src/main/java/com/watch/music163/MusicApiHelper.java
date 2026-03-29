package com.watch.music163;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Music API helper using NeteaseCloudMusicApi server endpoints
 * (same API pattern as yumbo-music-utils library).
 * Requires a NeteaseCloudMusicApi server to be running.
 * Falls back to direct NetEase APIs if no server is configured.
 */
public class MusicApiHelper {

    private static final String TAG = "MusicApiHelper";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://music.163.com";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Configurable API server URL (NeteaseCloudMusicApi server)
    private static String apiServerUrl = "";

    public static void setApiServerUrl(String url) {
        if (url != null) {
            apiServerUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        } else {
            apiServerUrl = "";
        }
    }

    public static String getApiServerUrl() {
        return apiServerUrl;
    }

    private static boolean hasApiServer() {
        return apiServerUrl != null && !apiServerUrl.isEmpty();
    }

    public interface SearchCallback {
        void onResult(List<Song> songs);
        void onError(String message);
    }

    public interface UrlCallback {
        void onResult(String url);
        void onError(String message);
    }

    public interface QrKeyCallback {
        void onResult(String key);
        void onError(String message);
    }

    public interface QrCreateCallback {
        void onResult(String qrUrl, String qrBase64);
        void onError(String message);
    }

    public interface QrCheckCallback {
        void onResult(int code, String message, String cookie);
        void onError(String message);
    }

    // ==================== Search ====================

    public static void searchSongs(String keyword, String cookie, SearchCallback callback) {
        executor.execute(() -> {
            try {
                List<Song> songs;
                if (hasApiServer()) {
                    songs = searchViaApiServer(keyword, cookie);
                } else {
                    songs = searchViaDirect(keyword, cookie);
                }
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                Log.w(TAG, "Search error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static List<Song> searchViaApiServer(String keyword, String cookie) throws Exception {
        String encoded = URLEncoder.encode(keyword, "UTF-8");
        long ts = System.currentTimeMillis();
        String apiUrl = apiServerUrl + "/cloudsearch?keywords=" + encoded
                + "&limit=20&type=1&timestamp=" + ts;
        String response = httpGet(apiUrl, cookie);
        return parseCloudsearchResult(response);
    }

    private static List<Song> searchViaDirect(String keyword, String cookie) throws Exception {
        String encoded = URLEncoder.encode(keyword, "UTF-8");
        String apiUrl = "https://music.163.com/api/search/get/web?s=" + encoded
                + "&type=1&offset=0&total=true&limit=20";
        String response = httpGet(apiUrl, cookie);
        return parseDirectSearchResult(response);
    }

    private static List<Song> parseCloudsearchResult(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        List<Song> songs = new ArrayList<>();
        JSONObject result = json.optJSONObject("result");
        if (result != null) {
            JSONArray songsArray = result.optJSONArray("songs");
            if (songsArray != null) {
                for (int i = 0; i < songsArray.length(); i++) {
                    JSONObject s = songsArray.getJSONObject(i);
                    long id = s.getLong("id");
                    String name = s.getString("name");
                    String artist = "";
                    // cloudsearch uses "ar" instead of "artists"
                    JSONArray ar = s.optJSONArray("ar");
                    if (ar != null && ar.length() > 0) {
                        artist = ar.getJSONObject(0).optString("name", "");
                    }
                    if (artist.isEmpty()) {
                        JSONArray artists = s.optJSONArray("artists");
                        if (artists != null && artists.length() > 0) {
                            artist = artists.getJSONObject(0).optString("name", "");
                        }
                    }
                    String album = "";
                    // cloudsearch uses "al" instead of "album"
                    JSONObject al = s.optJSONObject("al");
                    if (al != null) {
                        album = al.optString("name", "");
                    }
                    if (album.isEmpty()) {
                        JSONObject albumObj = s.optJSONObject("album");
                        if (albumObj != null) {
                            album = albumObj.optString("name", "");
                        }
                    }
                    songs.add(new Song(id, name, artist, album));
                }
            }
        }
        return songs;
    }

    private static List<Song> parseDirectSearchResult(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        List<Song> songs = new ArrayList<>();
        JSONObject result = json.optJSONObject("result");
        if (result != null) {
            JSONArray songsArray = result.optJSONArray("songs");
            if (songsArray != null) {
                for (int i = 0; i < songsArray.length(); i++) {
                    JSONObject s = songsArray.getJSONObject(i);
                    long id = s.getLong("id");
                    String name = s.getString("name");
                    String artist = "";
                    JSONArray artists = s.optJSONArray("artists");
                    if (artists != null && artists.length() > 0) {
                        artist = artists.getJSONObject(0).optString("name", "");
                    }
                    String album = "";
                    JSONObject albumObj = s.optJSONObject("album");
                    if (albumObj != null) {
                        album = albumObj.optString("name", "");
                    }
                    songs.add(new Song(id, name, artist, album));
                }
            }
        }
        return songs;
    }

    // ==================== Song URL ====================

    public static void getSongUrl(long songId, String cookie, UrlCallback callback) {
        getSongUrl(songId, cookie, true, callback);
    }

    public static void getSongUrl(long songId, String cookie, boolean tryVip, UrlCallback callback) {
        executor.execute(() -> {
            try {
                String url = null;

                // Try API server first (supports VIP with proper cookie)
                if (hasApiServer()) {
                    try {
                        url = getSongUrlViaApiServer(songId, cookie, "exhigh");
                    } catch (Exception e) {
                        Log.w(TAG, "API server exhigh failed", e);
                    }
                    if (url == null) {
                        try {
                            url = getSongUrlViaApiServer(songId, cookie, "standard");
                        } catch (Exception e) {
                            Log.w(TAG, "API server standard failed", e);
                        }
                    }
                }

                // Fallback: try direct NetEase API (free endpoint)
                if (url == null) {
                    try {
                        String apiUrl = "https://music.163.com/api/song/enhance/player/url?ids=["
                                + songId + "]&br=320000";
                        String response = httpGet(apiUrl, null);
                        url = extractUrlFromDirectResponse(response);
                    } catch (Exception e) {
                        Log.w(TAG, "Direct API free failed", e);
                    }
                }

                // Last resort: direct link
                if (url == null) {
                    url = "https://music.163.com/song/media/outer/url?id=" + songId + ".mp3";
                }

                String finalUrl = url;
                mainHandler.post(() -> callback.onResult(finalUrl));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static String getSongUrlViaApiServer(long songId, String cookie, String level)
            throws Exception {
        long ts = System.currentTimeMillis();
        String apiUrl = apiServerUrl + "/song/url/v1?id=" + songId
                + "&level=" + level + "&timestamp=" + ts;
        String response = httpGet(apiUrl, cookie);
        JSONObject json = new JSONObject(response);
        JSONArray data = json.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject first = data.getJSONObject(0);
            int code = first.optInt("code", -1);
            if (code == 200) {
                String url = first.optString("url", null);
                if (url != null && !"null".equals(url) && !url.isEmpty()) {
                    return url;
                }
            }
        }
        return null;
    }

    private static String extractUrlFromDirectResponse(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        JSONArray data = json.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject first = data.getJSONObject(0);
            int code = first.optInt("code", -1);
            if (code != 200) {
                return null;
            }
            String url = first.optString("url", null);
            if (url != null && !"null".equals(url) && !url.isEmpty()) {
                return url;
            }
        }
        return null;
    }

    // ==================== QR Login ====================

    /**
     * Step 1: Get QR login key
     */
    public static void loginQrKey(QrKeyCallback callback) {
        executor.execute(() -> {
            try {
                if (!hasApiServer()) {
                    mainHandler.post(() -> callback.onError("请先设置API服务器地址"));
                    return;
                }
                long ts = System.currentTimeMillis();
                String apiUrl = apiServerUrl + "/login/qr/key?timestamp=" + ts;
                String response = httpGet(apiUrl, null);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        String unikey = data.optString("unikey", "");
                        if (!unikey.isEmpty()) {
                            mainHandler.post(() -> callback.onResult(unikey));
                            return;
                        }
                    }
                }
                mainHandler.post(() -> callback.onError("获取二维码Key失败"));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Step 2: Create QR code image
     */
    public static void loginQrCreate(String key, QrCreateCallback callback) {
        executor.execute(() -> {
            try {
                if (!hasApiServer()) {
                    mainHandler.post(() -> callback.onError("请先设置API服务器地址"));
                    return;
                }
                long ts = System.currentTimeMillis();
                String encodedKey = URLEncoder.encode(key, "UTF-8");
                String apiUrl = apiServerUrl + "/login/qr/create?key=" + encodedKey
                        + "&qrimg=true&timestamp=" + ts;
                String response = httpGet(apiUrl, null);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        String qrUrl = data.optString("qrurl", "");
                        String qrImg = data.optString("qrimg", "");
                        mainHandler.post(() -> callback.onResult(qrUrl, qrImg));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onError("创建二维码失败"));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Step 3: Check QR login status (poll this)
     * code: 801=waiting, 802=scanned waiting confirm, 803=authorized
     */
    public static void loginQrCheck(String key, QrCheckCallback callback) {
        executor.execute(() -> {
            try {
                if (!hasApiServer()) {
                    mainHandler.post(() -> callback.onError("请先设置API服务器地址"));
                    return;
                }
                long ts = System.currentTimeMillis();
                String encodedKey = URLEncoder.encode(key, "UTF-8");
                String apiUrl = apiServerUrl + "/login/qr/check?key=" + encodedKey
                        + "&timestamp=" + ts + "&noCookie=true";
                String response = httpGet(apiUrl, null);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                String message = json.optString("message", "");
                String cookie = json.optString("cookie", "");
                mainHandler.post(() -> callback.onResult(code, message, cookie));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ==================== HTTP Methods ====================

    private static String httpGet(String urlStr, String cookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", REFERER);
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        try {
            int responseCode = conn.getResponseCode();
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 400) {
                reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
            } else {
                reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String httpPost(String urlStr, String jsonBody, String cookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", REFERER);
        conn.setRequestProperty("Content-Type", "application/json");
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        try {
            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.getBytes("UTF-8"));
            os.close();
            int responseCode = conn.getResponseCode();
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 400) {
                reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
            } else {
                reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
