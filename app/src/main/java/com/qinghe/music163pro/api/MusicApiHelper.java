package com.qinghe.music163pro.api;

import com.qinghe.music163pro.model.Song;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Music API helper that calls NetEase Cloud Music APIs directly
 * using weapi encryption (ported from NeteaseCloudMusicApiBackup).
 * No external API server needed.
 */
public class MusicApiHelper {

    private static final String TAG = "MusicApiHelper";

    private static final String WEAPI_BASE = "https://music.163.com/weapi";
    private static final String DOMAIN = "https://music.163.com";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0";

    // Mobile User-Agent for SMS login to avoid "环境不安全" error
    private static final String MOBILE_USER_AGENT =
            "NeteaseMusic/9.0.90 (Android 13; Pixel 6)";

    // Device/version info to prevent "version too old" errors
    private static final String OS_VER = "16.2";
    private static final String APP_VER = "9.0.90";
    private static final String VERSION_CODE = "140";
    private static final String CHANNEL = "distribution";
    private static final String OS_TYPE = "iPhone OS";
    private static String deviceId = UUID.randomUUID().toString().replace("-", "");

    /**
     * Set a persistent device ID (should be called at app startup from SharedPreferences).
     */
    public static void setDeviceId(String id) {
        if (id != null && !id.isEmpty()) {
            deviceId = id;
        }
    }

    public static String getDeviceId() {
        return deviceId;
    }

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        /** Returns the QR URL (to be encoded as QR image by the caller) */
        void onResult(String qrUrl);
        void onError(String message);
    }

    public interface QrCheckCallback {
        void onResult(int code, String message, String cookie);
        void onError(String message);
    }

    public interface SmsCallback {
        void onResult(boolean success, String message);
        void onError(String message);
    }

    public interface LoginCallback {
        void onResult(int code, String message, String cookie);
        void onError(String message);
    }

    // ==================== Search ====================

    public static void searchSongs(String keyword, String cookie, SearchCallback callback) {
        executor.execute(() -> {
            try {
                List<Song> songs = searchDirect(keyword, cookie);
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                Log.w(TAG, "Search error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Search via weapi/cloudsearch/pc (same as NeteaseCloudMusicApiBackup module/cloudsearch.js)
     */
    private static List<Song> searchDirect(String keyword, String cookie) throws Exception {
        JSONObject data = new JSONObject();
        data.put("s", keyword);
        data.put("type", 1);      // 1 = songs
        data.put("limit", 20);
        data.put("offset", 0);
        data.put("total", true);

        String csrfToken = extractCsrfToken(cookie);
        data.put("csrf_token", csrfToken);

        String response = weapiPost("/api/cloudsearch/pc", data.toString(), cookie);
        return parseCloudsearchResult(response);
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
                    // cloudsearch uses "ar" for artists
                    JSONArray ar = s.optJSONArray("ar");
                    if (ar != null && ar.length() > 0) {
                        artist = ar.getJSONObject(0).optString("name", "");
                    }
                    String album = "";
                    // cloudsearch uses "al" for album
                    JSONObject al = s.optJSONObject("al");
                    if (al != null) {
                        album = al.optString("name", "");
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

                // Try weapi with VIP quality levels
                if (tryVip && cookie != null && !cookie.isEmpty()) {
                    try {
                        url = fetchSongUrlWeapi(songId, cookie, "exhigh");
                    } catch (Exception e) {
                        Log.w(TAG, "weapi exhigh failed", e);
                    }
                    if (url == null) {
                        try {
                            url = fetchSongUrlWeapi(songId, cookie, "standard");
                        } catch (Exception e) {
                            Log.w(TAG, "weapi standard failed", e);
                        }
                    }
                }

                // Fallback: weapi without VIP
                if (url == null) {
                    try {
                        url = fetchSongUrlWeapi(songId, cookie, "standard");
                    } catch (Exception e) {
                        Log.w(TAG, "weapi standard (no-vip) failed", e);
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

    /**
     * Fetch song URL via weapi (same as NeteaseCloudMusicApiBackup module/song_url_v1.js)
     */
    private static String fetchSongUrlWeapi(long songId, String cookie, String level)
            throws Exception {
        JSONObject data = new JSONObject();
        data.put("ids", "[" + songId + "]");
        data.put("level", level);
        data.put("encodeType", "flac");

        String csrfToken = extractCsrfToken(cookie);
        data.put("csrf_token", csrfToken);

        String response = weapiPost("/api/song/enhance/player/url/v1", data.toString(), cookie);
        return extractSongUrlFromResponse(response);
    }

    private static String extractSongUrlFromResponse(String response) throws Exception {
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

    // ==================== QR Login ====================

    /**
     * Step 1: Get QR login key via weapi
     * (same as NeteaseCloudMusicApiBackup module/login_qr_key.js)
     */
    public static void loginQrKey(QrKeyCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("type", 3);

                String response = weapiPost("/api/login/qrcode/unikey", data.toString(), null);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                String unikey = json.optString("unikey", "");
                if (code == 200 && !unikey.isEmpty()) {
                    mainHandler.post(() -> callback.onResult(unikey));
                } else {
                    mainHandler.post(() -> callback.onError("获取二维码Key失败: code=" + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Step 2: Build QR URL from key (no server call needed)
     * (same as NeteaseCloudMusicApiBackup module/login_qr_create.js)
     */
    public static void loginQrCreate(String key, QrCreateCallback callback) {
        String qrUrl = "https://music.163.com/login?codekey=" + key;
        mainHandler.post(() -> callback.onResult(qrUrl));
    }

    /**
     * Step 3: Check QR login status via weapi
     * (same as NeteaseCloudMusicApiBackup module/login_qr_check.js)
     * Captures Set-Cookie headers to build the cookie string.
     */
    public static void loginQrCheck(String key, QrCheckCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("key", key);
                data.put("type", 3);

                String[] encrypted = NeteaseApiCrypto.weapi(data.toString());

                String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                        + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

                String urlStr = WEAPI_BASE + "/login/qrcode/client/login";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Referer", DOMAIN);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", buildAnonymousCookie(""));
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);

                try {
                    OutputStream os = conn.getOutputStream();
                    os.write(postBody.getBytes("UTF-8"));
                    os.close();

                    // Read response body
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    int code = json.optInt("code", -1);
                    String message = json.optString("message", "");

                    // Extract cookies from Set-Cookie headers
                    String cookieStr = "";
                    if (code == 803) {
                        cookieStr = extractSetCookies(conn);
                    }

                    final String finalCookie = cookieStr;
                    mainHandler.post(() -> callback.onResult(code, message, finalCookie));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ==================== SMS Login ====================

    /**
     * Send SMS verification code to phone number
     * (same as NeteaseCloudMusicApiBackup module/captcha_sent.js)
     * Uses os=android cookie to avoid "环境不安全" error.
     */
    public static void sendSmsCode(String phone, String ctcode, SmsCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("ctcode", ctcode != null && !ctcode.isEmpty() ? ctcode : "86");
                data.put("cellphone", phone);
                data.put("checkToken", "");

                String response = weapiPostMobile("/api/sms/captcha/sent", data.toString(), null);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    mainHandler.post(() -> callback.onResult(true, "验证码已发送"));
                } else {
                    String msg = json.optString("message", "发送失败: code=" + code);
                    mainHandler.post(() -> callback.onResult(false, msg));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Login with phone number and SMS captcha
     * (same as NeteaseCloudMusicApiBackup module/login_cellphone.js)
     * Uses os=android cookie to avoid "环境不安全" error.
     */
    public static void loginByCellphone(String phone, String captcha, String ctcode,
                                         LoginCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("type", "1");
                data.put("https", "true");
                data.put("phone", phone);
                data.put("countrycode", ctcode != null && !ctcode.isEmpty() ? ctcode : "86");
                data.put("captcha", captcha);
                data.put("rememberLogin", "true");
                data.put("checkToken", "");

                String[] encrypted = NeteaseApiCrypto.weapi(data.toString());
                String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                        + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

                String urlStr = DOMAIN + "/weapi/w/login/cellphone";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT);
                conn.setRequestProperty("Referer", DOMAIN);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", buildMobileCookie(""));
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);

                try {
                    OutputStream os = conn.getOutputStream();
                    os.write(postBody.getBytes("UTF-8"));
                    os.close();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    int code = json.optInt("code", -1);
                    String message = json.optString("message",
                            json.optString("msg", ""));

                    String cookieStr = "";
                    if (code == 200) {
                        cookieStr = extractSetCookies(conn);
                    }

                    final String finalCookie = cookieStr;
                    mainHandler.post(() -> callback.onResult(code, message, finalCookie));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ==================== weapi POST ====================

    /**
     * Send a weapi-encrypted POST request to NetEase.
     * @param apiPath The API path (e.g. "/api/cloudsearch/pc")
     * @param jsonData The JSON data to encrypt
     * @param cookie The user cookie string (can be null)
     * @return The response body as string
     */
    private static String weapiPost(String apiPath, String jsonData, String cookie) throws Exception {
        String[] encrypted = NeteaseApiCrypto.weapi(jsonData);

        String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

        // weapi URL: replace /api/ with /weapi/
        String weapiPath = apiPath.replaceFirst("^/api/", "/weapi/");
        String urlStr = DOMAIN + weapiPath;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", DOMAIN);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Cookie", buildAnonymousCookie(cookie));
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try {
            OutputStream os = conn.getOutputStream();
            os.write(postBody.getBytes("UTF-8"));
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

    // ==================== Utility ====================

    /**
     * Send a weapi-encrypted POST request using mobile device identity.
     * Used for SMS login operations to avoid "环境不安全" error.
     */
    private static String weapiPostMobile(String apiPath, String jsonData, String cookie) throws Exception {
        String[] encrypted = NeteaseApiCrypto.weapi(jsonData);

        String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

        String weapiPath = apiPath.replaceFirst("^/api/", "/weapi/");
        String urlStr = DOMAIN + weapiPath;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT);
        conn.setRequestProperty("Referer", DOMAIN);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Cookie", buildMobileCookie(cookie));
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try {
            OutputStream os = conn.getOutputStream();
            os.write(postBody.getBytes("UTF-8"));
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

    /**
     * Build a cookie string with Android mobile device identity.
     * Used for SMS login to avoid "环境不安全" error.
     */
    private static String buildMobileCookie(String existingCookie) {
        StringBuilder sb = new StringBuilder();
        if (existingCookie != null && !existingCookie.isEmpty()) {
            sb.append(existingCookie);
            if (!existingCookie.endsWith("; ")) {
                sb.append("; ");
            }
        }
        sb.append("__remember_me=true; ");
        sb.append("ntes_kaola_ad=1; ");
        sb.append("WEVNSM=1.0.0; ");
        sb.append("osver=13; ");
        sb.append("deviceId=").append(deviceId).append("; ");
        sb.append("os=android; ");
        sb.append("channel=").append(CHANNEL).append("; ");
        sb.append("appver=").append(APP_VER);
        return sb.toString();
    }

    /**
     * Build a cookie string with proper version/device info.
     * This prevents "version too old" errors from the NetEase server.
     * Based on NeteaseCloudMusicApiBackup util/request.js cookie construction.
     */
    private static String buildAnonymousCookie(String existingCookie) {
        StringBuilder sb = new StringBuilder();
        // Preserve existing cookie values
        if (existingCookie != null && !existingCookie.isEmpty()) {
            sb.append(existingCookie);
            if (!existingCookie.endsWith("; ")) {
                sb.append("; ");
            }
        }
        // Add version/device info that the server expects
        sb.append("__remember_me=true; ");
        sb.append("ntes_kaola_ad=1; ");
        sb.append("WEVNSM=1.0.0; ");
        sb.append("osver=").append(URLEncoder_safe(OS_VER)).append("; ");
        sb.append("deviceId=").append(deviceId).append("; ");
        sb.append("os=").append(URLEncoder_safe(OS_TYPE)).append("; ");
        sb.append("channel=").append(CHANNEL).append("; ");
        sb.append("appver=").append(APP_VER);
        return sb.toString();
    }

    private static String URLEncoder_safe(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * Extract __csrf token from cookie string
     */
    private static String extractCsrfToken(String cookie) {
        if (cookie == null || cookie.isEmpty()) return "";
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("__csrf=")) {
                return trimmed.substring("__csrf=".length());
            }
        }
        return "";
    }

    /**
     * Extract Set-Cookie headers and build a simplified cookie string
     * containing the important values (MUSIC_U, __csrf, etc.)
     */
    private static String extractSetCookies(HttpURLConnection conn) {
        StringBuilder cookieBuilder = new StringBuilder();
        Map<String, List<String>> headers = conn.getHeaderFields();
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies == null) {
            setCookies = headers.get("set-cookie");
        }
        if (setCookies != null) {
            for (String setCookie : setCookies) {
                // Each Set-Cookie contains "name=value; path=...; ..."
                // Extract just the name=value part
                String nameValue = setCookie.split(";")[0].trim();
                if (cookieBuilder.length() > 0) {
                    cookieBuilder.append("; ");
                }
                cookieBuilder.append(nameValue);
            }
        }
        return cookieBuilder.toString();
    }
}
