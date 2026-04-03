package com.qinghe.music163pro.api;

import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.util.MusicLog;

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
    private static final String SONG_COMMENT_THREAD_PREFIX = "R_SO_4_";

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
        /** Called when server requires CAPTCHA verification (code -462). Open verifyUrl in a WebView. */
        default void onVerificationRequired(String verifyUrl) {}
    }

    public interface LyricsCallback {
        void onResult(String lrcText);
        void onError(String message);
    }

    public interface CloudFavoritesCallback {
        void onResult(List<Song> songs);
        void onError(String message);
    }

    public interface LikeCallback {
        void onResult(boolean success);
        void onError(String message);
    }

    public interface CloudLikedIdsCallback {
        void onResult(java.util.Set<Long> ids);
        void onError(String message);
    }

    public interface AccountCallback {
        void onResult(JSONObject accountJson);
        void onError(String message);
    }

    public interface VipInfoCallback {
        void onResult(JSONObject vipJson);
        void onError(String message);
    }

    public interface TopListCallback {
        void onResult(JSONArray listArray);
        void onError(String message);
    }

    public interface PlaylistDetailCallback {
        void onResult(List<Song> songs);
        void onError(String message);
    }

    public interface PersonalFMCallback {
        void onResult(List<Song> songs);
        void onError(String message);
    }

    public interface RecognitionCallback {
        /** Called with matched song info: name, artist, album (any may be empty). */
        void onResult(String songName, String artist, String album, long songId);
        void onError(String message);
    }

    public interface SongWikiCallback {
        void onResult(JSONObject wikiJson);
        void onError(String message);
    }

    public interface SongDetailCallback {
        void onResult(JSONObject songDetail);
        void onError(String message);
    }

    public interface ArtistDescCallback {
        void onResult(String briefDesc, JSONArray introduction);
        void onError(String message);
    }

    public interface CommentCallback {
        void onResult(JSONObject commentData);
        void onError(String message);
    }

    public interface CommentActionCallback {
        void onResult(boolean success);
        void onError(String message);
    }

    public interface LyricsFullCallback {
        void onResult(String lrcText, String tlyricText);
        void onError(String message);
    }

    public interface SimiSongCallback {
        void onResult(JSONArray songs);
        void onError(String message);
    }

    public interface SimiPlaylistCallback {
        void onResult(JSONArray playlists);
        void onError(String message);
    }

    // ==================== Search ====================

    public static void searchSongs(String keyword, String cookie, SearchCallback callback) {
        searchSongs(keyword, 0, cookie, callback);
    }

    public static void searchSongs(String keyword, int offset, String cookie, SearchCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "搜索歌曲", "keyword=" + keyword + " offset=" + offset);
                List<Song> songs = searchDirect(keyword, offset, cookie);
                MusicLog.d(TAG, "搜索结果: " + songs.size() + " 首");
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                MusicLog.w(TAG, "搜索失败: " + keyword, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Search via weapi/cloudsearch/pc (same as NeteaseCloudMusicApiBackup module/cloudsearch.js)
     */
    private static List<Song> searchDirect(String keyword, int offset, String cookie) throws Exception {
        JSONObject data = new JSONObject();
        data.put("s", keyword);
        data.put("type", 1);      // 1 = songs
        data.put("limit", 20);
        data.put("offset", offset);
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
                MusicLog.op(TAG, "获取歌曲URL", "songId=" + songId + " tryVip=" + tryVip);

                // Try weapi with VIP quality levels
                if (tryVip && cookie != null && !cookie.isEmpty()) {
                    try {
                        url = fetchSongUrlWeapi(songId, cookie, "exhigh");
                        if (url != null) MusicLog.d(TAG, "获取exhigh URL成功: " + songId);
                    } catch (Exception e) {
                        MusicLog.w(TAG, "weapi exhigh 失败: " + songId, e);
                    }
                    if (url == null) {
                        try {
                            url = fetchSongUrlWeapi(songId, cookie, "standard");
                            if (url != null) MusicLog.d(TAG, "获取standard URL成功(vip): " + songId);
                        } catch (Exception e) {
                            MusicLog.w(TAG, "weapi standard(vip) 失败: " + songId, e);
                        }
                    }
                }

                // Fallback: weapi without VIP
                if (url == null) {
                    try {
                        url = fetchSongUrlWeapi(songId, cookie, "standard");
                        if (url != null) MusicLog.d(TAG, "获取standard URL成功(无vip): " + songId);
                    } catch (Exception e) {
                        MusicLog.w(TAG, "weapi standard(无vip) 失败: " + songId, e);
                    }
                }

                // Last resort: direct link
                if (url == null) {
                    url = "https://music.163.com/song/media/outer/url?id=" + songId + ".mp3";
                    MusicLog.w(TAG, "使用直连兜底URL: " + songId);
                }

                String finalUrl = url;
                mainHandler.post(() -> callback.onResult(finalUrl));
            } catch (Exception e) {
                MusicLog.e(TAG, "getSongUrl 异常: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
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
     * Login with phone number and SMS captcha.
     * Per NeteaseCloudMusic_PythonSDK: login_cellphone(phone, captcha=captcha, countrycode=ctcode)
     * → POST /weapi/w/login/cellphone with {type:'1', https:'true', phone, countrycode, captcha, rememberLogin:'true'}
     */
    public static void loginByCellphone(String phone, String captcha, String ctcode,
                                         LoginCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "短信登录", "phone=" + maskPhone(phone));

                JSONObject data = new JSONObject();
                data.put("type", "1");
                data.put("https", "true");
                data.put("phone", phone);
                data.put("countrycode", ctcode != null && !ctcode.isEmpty() ? ctcode : "86");
                data.put("captcha", captcha);
                data.put("rememberLogin", "true");

                String[] encrypted = NeteaseApiCrypto.weapi(data.toString());
                String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                        + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

                String urlStr = DOMAIN + "/weapi/w/login/cellphone";
                MusicLog.i(TAG, "[REQ] POST(sms-login) " + urlStr + "\n  请求体(原文): " + data);
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

                    int httpCode = conn.getResponseCode();

                    BufferedReader reader;
                    if (httpCode >= 200 && httpCode < 400) {
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    } else {
                        java.io.InputStream errStream = conn.getErrorStream();
                        if (errStream != null) {
                            reader = new BufferedReader(
                                    new InputStreamReader(errStream, "UTF-8"));
                        } else {
                            String errMsg = "HTTP " + httpCode;
                            MusicLog.api(TAG, "POST(sms-login)", urlStr, httpCode, errMsg);
                            mainHandler.post(() -> callback.onResult(httpCode, errMsg, ""));
                            return;
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String responseBody = sb.toString();
                    MusicLog.api(TAG, "POST(sms-login)", urlStr, httpCode, responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    int code = json.optInt("code", -1);
                    String message = json.optString("message", json.optString("msg", ""));
                    if (message.isEmpty()) message = "code=" + code;

                    // -462 = needs CAPTCHA verification before login can proceed
                    if (code == -462) {
                        JSONObject dataObj = json.optJSONObject("data");
                        String verifyUrl = dataObj != null ? dataObj.optString("url", dataObj.optString("verifyUrl", "")) : "";
                        MusicLog.w(TAG, "短信登录需要滑块验证，verifyUrl=" + verifyUrl);
                        MusicLog.d(TAG, "短信登录 -462 Set-Cookie: " + extractSetCookies(conn));
                        final String finalVerifyUrl = verifyUrl;
                        mainHandler.post(() -> callback.onVerificationRequired(finalVerifyUrl));
                        return;
                    }

                    String cookieStr = "";
                    if (code == 200) {
                        cookieStr = extractSetCookies(conn);
                        MusicLog.d(TAG, "短信登录成功，Set-Cookie: " + (cookieStr.isEmpty() ? "(空)" : "已获取"));
                        // Fallback: if Set-Cookie headers are empty, try token from JSON body
                        if ((cookieStr == null || cookieStr.isEmpty() || !cookieStr.contains("MUSIC_U"))
                                && json.has("token")) {
                            String token = json.optString("token", "");
                            if (!token.isEmpty()) {
                                cookieStr = "MUSIC_U=" + token;
                                MusicLog.d(TAG, "短信登录: 从token字段提取Cookie");
                            }
                        }
                        // Also try cookie field in JSON body
                        if ((cookieStr == null || cookieStr.isEmpty() || !cookieStr.contains("MUSIC_U"))
                                && json.has("cookie")) {
                            String bodyCookie = json.optString("cookie", "");
                            if (!bodyCookie.isEmpty() && bodyCookie.contains("MUSIC_U")) {
                                cookieStr = bodyCookie;
                                MusicLog.d(TAG, "短信登录: 从cookie字段提取Cookie");
                            }
                        }
                    } else {
                        MusicLog.w(TAG, "短信登录失败: code=" + code + " message=" + message);
                    }

                    final String finalCookie = cookieStr;
                    final String finalMsg = message;
                    mainHandler.post(() -> callback.onResult(code, finalMsg, finalCookie));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                MusicLog.e(TAG, "短信登录异常", e);
                String errMsg = e.getMessage() != null ? e.getMessage() : "网络连接失败";
                mainHandler.post(() -> callback.onError(errMsg));
            }
        });
    }

    // ==================== Password Login ====================

    /**
     * Login with phone number and password.
     * (same as NeteaseCloudMusicApiBackup module/login_cellphone.js with password mode)
     * Uses mobile cookie and weapi encryption, same as SMS login.
     */
    public static void loginByPassword(String phone, String password, String ctcode,
                                        LoginCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "密码登录", "phone=" + maskPhone(phone));

                // MD5 hash the password
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(password.getBytes("UTF-8"));
                StringBuilder hexString = new StringBuilder();
                for (byte b : digest) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                String md5Password = hexString.toString();

                JSONObject data = new JSONObject();
                data.put("type", "1");
                data.put("https", "true");
                data.put("phone", phone);
                data.put("countrycode", ctcode != null && !ctcode.isEmpty() ? ctcode : "86");
                data.put("password", md5Password);
                data.put("rememberLogin", "true");

                String[] encrypted = NeteaseApiCrypto.weapi(data.toString());
                String postBody = "params=" + URLEncoder.encode(encrypted[0], "UTF-8")
                        + "&encSecKey=" + URLEncoder.encode(encrypted[1], "UTF-8");

                String urlStr = DOMAIN + "/weapi/w/login/cellphone";
                // Log request (hide actual MD5 hash for security)
                MusicLog.i(TAG, "[REQ] POST(pwd-login) " + urlStr + "\n  请求体(原文): {phone=" + maskPhone(phone) + ", countrycode=86, password=[MD5已隐藏], rememberLogin=true}");
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

                    int httpCode = conn.getResponseCode();

                    BufferedReader reader;
                    if (httpCode >= 200 && httpCode < 400) {
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    } else {
                        java.io.InputStream errStream = conn.getErrorStream();
                        if (errStream != null) {
                            reader = new BufferedReader(
                                    new InputStreamReader(errStream, "UTF-8"));
                        } else {
                            String errMsg = "HTTP " + httpCode;
                            MusicLog.api(TAG, "POST(pwd-login)", urlStr, httpCode, errMsg);
                            mainHandler.post(() -> callback.onResult(httpCode, errMsg, ""));
                            return;
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String responseBody = sb.toString();
                    MusicLog.api(TAG, "POST(pwd-login)", urlStr, httpCode, responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    int code = json.optInt("code", -1);
                    String message = json.optString("message", json.optString("msg", ""));
                    if (message.isEmpty()) message = "code=" + code;

                    String cookieStr = "";
                    if (code == 200) {
                        cookieStr = extractSetCookies(conn);
                        MusicLog.d(TAG, "密码登录成功，Set-Cookie: " + (cookieStr.isEmpty() ? "(空)" : "已获取"));
                        // Fallback: if Set-Cookie headers are empty, try token from JSON body
                        if ((cookieStr == null || cookieStr.isEmpty() || !cookieStr.contains("MUSIC_U"))
                                && json.has("token")) {
                            String token = json.optString("token", "");
                            if (!token.isEmpty()) {
                                cookieStr = "MUSIC_U=" + token;
                                MusicLog.d(TAG, "密码登录: 从token字段提取Cookie");
                            }
                        }
                        // Also try cookie field in JSON body
                        if ((cookieStr == null || cookieStr.isEmpty() || !cookieStr.contains("MUSIC_U"))
                                && json.has("cookie")) {
                            String bodyCookie = json.optString("cookie", "");
                            if (!bodyCookie.isEmpty() && bodyCookie.contains("MUSIC_U")) {
                                cookieStr = bodyCookie;
                                MusicLog.d(TAG, "密码登录: 从cookie字段提取Cookie");
                            }
                        }
                    } else {
                        MusicLog.w(TAG, "密码登录失败: code=" + code + " message=" + message);
                    }

                    final String finalCookie = cookieStr;
                    final String finalMsg = message;
                    mainHandler.post(() -> callback.onResult(code, finalMsg, finalCookie));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                MusicLog.e(TAG, "密码登录异常", e);
                String errMsg = e.getMessage() != null ? e.getMessage() : "网络连接失败";
                mainHandler.post(() -> callback.onError(errMsg));
            }
        });
    }

    // ==================== Lyrics ====================

    /**
     * Fetch lyrics for a song by its ID.
     * (same as NeteaseCloudMusicApiBackup module/lyric_new.js)
     */
    public static void getLyrics(long songId, String cookie, LyricsCallback callback) {
        executor.execute(() -> {
            try {
                String lrc = fetchLyrics(songId, cookie);
                mainHandler.post(() -> callback.onResult(lrc));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌词失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Fetch lyrics synchronously (for use in download).
     */
    public static String fetchLyricsSync(long songId, String cookie) {
        try {
            return fetchLyrics(songId, cookie);
        } catch (Exception e) {
            MusicLog.w(TAG, "歌词同步获取失败: " + songId, e);
            return null;
        }
    }

    private static String fetchLyrics(long songId, String cookie) throws Exception {
        JSONObject data = new JSONObject();
        data.put("id", songId);
        data.put("cp", false);
        data.put("tv", 0);
        data.put("lv", 0);
        data.put("rv", 0);
        data.put("kv", 0);

        String csrfToken = extractCsrfToken(cookie);
        data.put("csrf_token", csrfToken);

        String response = weapiPost("/api/song/lyric/v1", data.toString(), cookie);
        JSONObject json = new JSONObject(response);

        // Extract LRC lyrics
        JSONObject lrcObj = json.optJSONObject("lrc");
        if (lrcObj != null) {
            String lyric = lrcObj.optString("lyric", "");
            if (!lyric.isEmpty()) {
                return lyric;
            }
        }
        return "";
    }

    // ==================== Cloud Favorites ====================

    /**
     * Get the user's "liked songs" playlist from the cloud.
     * Uses the user's liked playlist (first playlist from /api/user/playlist)
     * and fetches tracks via /api/v6/playlist/detail which returns them
     * in the correct time order (most recently liked first).
     */
    public static void getCloudFavorites(String cookie, CloudFavoritesCallback callback) {
        executor.execute(() -> {
            try {
                long uid = extractUidFromCookie(cookie);
                if (uid <= 0) {
                    mainHandler.post(() -> callback.onError("请先登录"));
                    return;
                }
                MusicLog.op(TAG, "获取云端收藏", "uid=" + uid);

                String csrfToken = extractCsrfToken(cookie);

                // Step 1: Get user's liked playlist ID (first playlist)
                JSONObject plData = new JSONObject();
                plData.put("uid", uid);
                plData.put("limit", 1);
                plData.put("offset", 0);
                plData.put("csrf_token", csrfToken);

                String plResponse = weapiPost("/api/user/playlist", plData.toString(), cookie);
                JSONObject plJson = new JSONObject(plResponse);
                JSONArray playlists = plJson.optJSONArray("playlist");

                if (playlists == null || playlists.length() == 0) {
                    // Fallback: use legacy method
                    getCloudFavoritesLegacy(cookie, uid, csrfToken, callback);
                    return;
                }

                long likedPlaylistId = playlists.getJSONObject(0).getLong("id");

                // Step 2: Get playlist tracks in correct time order
                JSONObject detailData = new JSONObject();
                detailData.put("id", likedPlaylistId);
                detailData.put("n", 200);
                detailData.put("csrf_token", csrfToken);

                String detailResponse = weapiPost("/api/v6/playlist/detail", detailData.toString(), cookie);
                JSONObject detailJson = new JSONObject(detailResponse);
                JSONObject playlist = detailJson.optJSONObject("playlist");

                if (playlist == null) {
                    getCloudFavoritesLegacy(cookie, uid, csrfToken, callback);
                    return;
                }

                JSONArray tracks = playlist.optJSONArray("tracks");
                if (tracks == null || tracks.length() == 0) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>()));
                    return;
                }

                List<Song> songs = new ArrayList<>();
                int limit = Math.min(tracks.length(), 200);
                for (int i = 0; i < limit; i++) {
                    JSONObject s = tracks.getJSONObject(i);
                    long id = s.getLong("id");
                    String name = s.getString("name");
                    String artist = "";
                    JSONArray ar = s.optJSONArray("ar");
                    if (ar != null && ar.length() > 0) {
                        artist = ar.getJSONObject(0).optString("name", "");
                    }
                    String album = "";
                    JSONObject al = s.optJSONObject("al");
                    if (al != null) {
                        album = al.optString("name", "");
                    }
                    songs.add(new Song(id, name, artist, album));
                }
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取云端收藏失败", e);
                mainHandler.post(() -> callback.onError("获取云端收藏失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误")));
            }
        });
    }

    /**
     * Legacy fallback: get cloud favorites via /api/song/like/get + /api/v3/song/detail.
     * Used when the playlist API is unavailable.
     */
    private static void getCloudFavoritesLegacy(String cookie, long uid, String csrfToken,
                                                  CloudFavoritesCallback callback) {
        try {
            JSONObject likeData = new JSONObject();
            likeData.put("uid", uid);
            likeData.put("csrf_token", csrfToken);

            String likeResponse = weapiPost("/api/song/like/get", likeData.toString(), cookie);
            JSONObject likeJson = new JSONObject(likeResponse);

            JSONArray idsArray = likeJson.optJSONArray("ids");
            if (idsArray == null || idsArray.length() == 0) {
                mainHandler.post(() -> callback.onResult(new ArrayList<>()));
                return;
            }

            int limit = Math.min(idsArray.length(), 200);
            JSONArray songIds = new JSONArray();
            for (int i = 0; i < limit; i++) {
                JSONObject idObj = new JSONObject();
                idObj.put("id", idsArray.getLong(i));
                songIds.put(idObj);
            }

            JSONObject detailData = new JSONObject();
            detailData.put("c", songIds.toString());
            detailData.put("csrf_token", csrfToken);

            String detailResponse = weapiPost("/api/v3/song/detail", detailData.toString(), cookie);
            JSONObject detailJson = new JSONObject(detailResponse);
            JSONArray songsArray = detailJson.optJSONArray("songs");

            java.util.Map<Long, Song> songMap = new java.util.LinkedHashMap<>();
            if (songsArray != null) {
                for (int i = 0; i < songsArray.length(); i++) {
                    JSONObject s = songsArray.getJSONObject(i);
                    long id = s.getLong("id");
                    String name = s.getString("name");
                    String artist = "";
                    JSONArray ar = s.optJSONArray("ar");
                    if (ar != null && ar.length() > 0) {
                        artist = ar.getJSONObject(0).optString("name", "");
                    }
                    String album = "";
                    JSONObject al = s.optJSONObject("al");
                    if (al != null) {
                        album = al.optString("name", "");
                    }
                    songMap.put(id, new Song(id, name, artist, album));
                }
            }

            List<Song> songs = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                long id = idsArray.getLong(i);
                Song song = songMap.get(id);
                if (song != null) {
                    songs.add(song);
                }
            }
            mainHandler.post(() -> callback.onResult(songs));
        } catch (Exception e) {
            MusicLog.w(TAG, "获取云端收藏(legacy)失败", e);
            mainHandler.post(() -> callback.onError("获取云端收藏失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误")));
        }
    }

    /**
     * Like or unlike a song on the cloud.
     * (same as NeteaseCloudMusicApiBackup module/like.js)
     */
    public static void likeTrack(long trackId, boolean like, String cookie, LikeCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, like ? "收藏歌曲" : "取消收藏", "trackId=" + trackId);
                JSONObject data = new JSONObject();
                data.put("trackId", trackId);
                data.put("like", like);
                data.put("alg", "itembased");
                data.put("time", 3);

                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/radio/like", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "收藏操作失败: " + trackId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get the set of liked song IDs from the cloud (for checking like status).
     */
    public static void getCloudLikedIds(String cookie, CloudLikedIdsCallback callback) {
        executor.execute(() -> {
            try {
                long uid = extractUidFromCookie(cookie);
                if (uid <= 0) {
                    mainHandler.post(() -> callback.onError("请先登录"));
                    return;
                }

                JSONObject likeData = new JSONObject();
                likeData.put("uid", uid);
                String csrfToken = extractCsrfToken(cookie);
                likeData.put("csrf_token", csrfToken);

                String likeResponse = weapiPost("/api/song/like/get", likeData.toString(), cookie);
                JSONObject likeJson = new JSONObject(likeResponse);

                JSONArray idsArray = likeJson.optJSONArray("ids");
                java.util.Set<Long> idSet = new java.util.HashSet<>();
                if (idsArray != null) {
                    for (int i = 0; i < idsArray.length(); i++) {
                        idSet.add(idsArray.getLong(i));
                    }
                }
                mainHandler.post(() -> callback.onResult(idSet));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取云端收藏ID失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== User Account ====================

    /**
     * Get user account info including profile and VIP details.
     */
    public static void getUserAccount(String cookie, AccountCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取用户账号信息", null);
                JSONObject data = new JSONObject();
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/w/nuser/account/get", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                mainHandler.post(() -> callback.onResult(json));
            } catch (Exception e) {
                MusicLog.e(TAG, "获取账号信息失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get VIP user info including expiry time.
     * (NeteaseCloudMusicApiBackup module/vip_info.js)
     * Endpoint: /api/music-vip-membership/front/vip/info
     */
    public static void getVipInfo(String cookie, VipInfoCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取VIP信息", null);
                JSONObject data = new JSONObject();
                data.put("userId", "");
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/music-vip-membership/front/vip/info", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                mainHandler.post(() -> callback.onResult(json));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取VIP信息失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Top List ====================

    /**
     * Get all top/chart lists.
     * (same as NeteaseCloudMusicApiBackup module/toplist.js)
     */
    public static void getTopList(String cookie, TopListCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/toplist", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONArray list = json.optJSONArray("list");
                if (list != null) {
                    mainHandler.post(() -> callback.onResult(list));
                } else {
                    mainHandler.post(() -> callback.onError("获取排行榜失败"));
                }
            } catch (Exception e) {
                MusicLog.w(TAG, "获取排行榜失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get playlist detail (tracks) by playlist ID.
     */
    public static void getPlaylistDetail(long playlistId, String cookie, PlaylistDetailCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("id", playlistId);
                data.put("n", 200);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/v6/playlist/detail", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONObject playlist = json.optJSONObject("playlist");
                if (playlist == null) {
                    mainHandler.post(() -> callback.onError("获取歌单详情失败"));
                    return;
                }

                JSONArray tracks = playlist.optJSONArray("tracks");
                List<Song> songs = new ArrayList<>();
                if (tracks != null) {
                    int limit = Math.min(tracks.length(), 200);
                    for (int i = 0; i < limit; i++) {
                        JSONObject s = tracks.getJSONObject(i);
                        long id = s.getLong("id");
                        String name = s.getString("name");
                        String artist = "";
                        JSONArray ar = s.optJSONArray("ar");
                        if (ar != null && ar.length() > 0) {
                            artist = ar.getJSONObject(0).optString("name", "");
                        }
                        String album = "";
                        JSONObject al = s.optJSONObject("al");
                        if (al != null) {
                            album = al.optString("name", "");
                        }
                        songs.add(new Song(id, name, artist, album));
                    }
                }
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌单详情失败: " + playlistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Personal FM ====================

    /**
     * Get personal FM songs.
     * (same as NeteaseCloudMusicApiBackup module/personal_fm.js)
     * Calls the API multiple times to accumulate more songs since each call returns ~3.
     */
    public static void getPersonalFM(String cookie, PersonalFMCallback callback) {
        executor.execute(() -> {
            try {
                String csrfToken = extractCsrfToken(cookie);
                List<Song> allSongs = new ArrayList<>();
                java.util.Set<Long> seenIds = new java.util.HashSet<>();

                // Call the API multiple times to get more songs (each returns ~3)
                for (int batch = 0; batch < 5 && allSongs.size() < 15; batch++) {
                    JSONObject data = new JSONObject();
                    data.put("csrf_token", csrfToken);

                    String response = weapiPost("/api/v1/radio/get", data.toString(), cookie);
                    JSONObject json = new JSONObject(response);
                    JSONArray dataArr = json.optJSONArray("data");
                    if (dataArr != null) {
                        for (int i = 0; i < dataArr.length(); i++) {
                            JSONObject s = dataArr.getJSONObject(i);
                            long id = s.getLong("id");
                            if (seenIds.contains(id)) continue;
                            seenIds.add(id);
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
                            allSongs.add(new Song(id, name, artist, album));
                        }
                    }
                }

                final List<Song> result = allSongs;
                MusicLog.d(TAG, "私人漫游获取完成: " + result.size() + " 首");
                mainHandler.post(() -> callback.onResult(result));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取私人漫游失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Extract user ID from cookie string (MUSIC_U contains user session).
     * Uses /api/w/nuser/account/get to get the user's account info.
     */
    private static long extractUidFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return -1;
        try {
            JSONObject data = new JSONObject();
            String csrfToken = extractCsrfToken(cookie);
            data.put("csrf_token", csrfToken);

            String response = weapiPost("/api/w/nuser/account/get", data.toString(), cookie);
            JSONObject json = new JSONObject(response);
            JSONObject account = json.optJSONObject("account");
            if (account != null) {
                return account.optLong("id", -1);
            }
        } catch (Exception e) {
            MusicLog.w(TAG, "提取UID失败", e);
        }
        return -1;
    }

    // ==================== Song Detail ====================

    /**
     * Get detailed song info via /api/v3/song/detail.
     * Returns full song metadata including duration, album info, artist info, etc.
     */
    public static void getSongDetail(long songId, String cookie, SongDetailCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取歌曲详情", "songId=" + songId);
                JSONArray songIds = new JSONArray();
                JSONObject idObj = new JSONObject();
                idObj.put("id", songId);
                songIds.put(idObj);

                JSONObject data = new JSONObject();
                data.put("c", songIds.toString());
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/v3/song/detail", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONArray songs = json.optJSONArray("songs");
                if (songs != null && songs.length() > 0) {
                    mainHandler.post(() -> callback.onResult(songs.optJSONObject(0)));
                } else {
                    mainHandler.post(() -> callback.onError("未找到歌曲信息"));
                }
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌曲详情失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Song Wiki ====================

    public static void getSongWikiSummary(long songId, String cookie, SongWikiCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取歌曲百科", "songId=" + songId);
                JSONObject data = new JSONObject();
                data.put("songId", songId);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/song/play/about/block/page", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                mainHandler.post(() -> callback.onResult(json));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌曲百科失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Artist Description ====================

    public static void getArtistDesc(long artistId, String cookie, ArtistDescCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取歌手百科", "artistId=" + artistId);
                JSONObject data = new JSONObject();
                data.put("id", artistId);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/artist/introduction", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                String briefDesc = json.optString("briefDesc", "");
                JSONArray introduction = json.optJSONArray("introduction");
                mainHandler.post(() -> callback.onResult(briefDesc, introduction != null ? introduction : new JSONArray()));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌手简介失败: " + artistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Similar Songs & Playlists ====================

    /**
     * Get similar songs via /api/v1/discovery/simiSong.
     * Returns JSONArray of song objects with id, name, artists[], album{}, duration, etc.
     */
    public static void getSimiSong(long songId, String cookie, SimiSongCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取相似歌曲", "songId=" + songId);
                JSONObject data = new JSONObject();
                data.put("songid", songId);
                data.put("limit", 10);
                data.put("offset", 0);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/v1/discovery/simiSong", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONArray songs = json.optJSONArray("songs");
                mainHandler.post(() -> callback.onResult(songs != null ? songs : new JSONArray()));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取相似歌曲失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get similar playlists via /api/discovery/simiPlaylist.
     * Returns JSONArray of playlist objects with id, name, playCount, trackCount, coverImgUrl, etc.
     */
    public static void getSimiPlaylist(long songId, String cookie, SimiPlaylistCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "获取相似歌单", "songId=" + songId);
                JSONObject data = new JSONObject();
                data.put("songid", songId);
                data.put("limit", 10);
                data.put("offset", 0);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/discovery/simiPlaylist", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONArray playlists = json.optJSONArray("playlists");
                mainHandler.post(() -> callback.onResult(playlists != null ? playlists : new JSONArray()));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取相似歌单失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Comments ====================

    /**
     * Get comments for a song.
     * sortType: 99=recommended, 2=hot, 3=newest
     */
    public static void getComments(long songId, int pageNo, int pageSize, int sortType,
                                    String cursor, String cookie, CommentCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                JSONObject data = new JSONObject();
                data.put("threadId", threadId);
                data.put("pageNo", pageNo);
                data.put("showInner", true);
                data.put("pageSize", pageSize);
                data.put("sortType", sortType);
                // Build cursor based on sortType
                String cursorVal;
                if (cursor != null && !cursor.isEmpty()) {
                    cursorVal = cursor;
                } else {
                    switch (sortType) {
                        case 2:
                            cursorVal = "normalHot#" + ((pageNo - 1) * pageSize);
                            break;
                        case 3:
                            cursorVal = "0";
                            break;
                        case 99:
                        default:
                            cursorVal = String.valueOf((pageNo - 1) * pageSize);
                            break;
                    }
                }
                data.put("cursor", cursorVal);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/v2/resource/comments", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                mainHandler.post(() -> callback.onResult(json));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取评论失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Post a new comment on a song.
     */
    public static void postComment(long songId, String content, String cookie, CommentActionCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                JSONObject data = new JSONObject();
                data.put("threadId", threadId);
                data.put("content", content);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/resource/comments/add", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "发送评论失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Reply to a comment on a song.
     */
    public static void replyComment(long songId, long commentId, String content, String cookie, CommentActionCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                JSONObject data = new JSONObject();
                data.put("threadId", threadId);
                data.put("commentId", commentId);
                data.put("content", content);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/resource/comments/reply", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "回复评论失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Like or unlike a comment.
     * @param like true to like, false to unlike
     */
    public static void likeComment(long songId, long commentId, boolean like, String cookie, CommentActionCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                String action = like ? "like" : "unlike";
                JSONObject data = new JSONObject();
                data.put("threadId", threadId);
                data.put("commentId", commentId);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/v1/comment/" + action, data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "点赞评论失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Delete a comment on a song.
     * Based on NeteaseCloudMusicApiBackup: /api/resource/comments/delete
     */
    public static void deleteComment(long songId, long commentId, String cookie, CommentActionCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                JSONObject data = new JSONObject();
                data.put("threadId", threadId);
                data.put("commentId", commentId);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                MusicLog.op(TAG, "删除评论", "songId=" + songId + ", commentId=" + commentId);
                String response = weapiPost("/api/resource/comments/delete", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "删除评论失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get floor (sub) comments for a parent comment.
     */
    public static void getFloorComments(long songId, long parentCommentId, int limit, long time,
                                         String cookie, CommentCallback callback) {
        executor.execute(() -> {
            try {
                String threadId = SONG_COMMENT_THREAD_PREFIX + songId;
                JSONObject data = new JSONObject();
                data.put("parentCommentId", parentCommentId);
                data.put("threadId", threadId);
                data.put("time", time);
                data.put("limit", limit);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/resource/comment/floor/get", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                mainHandler.post(() -> callback.onResult(json));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取楼层评论失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Lyrics with Translation ====================

    private static JSONObject buildLyricRequestData(long songId, String cookie) throws Exception {
        JSONObject data = new JSONObject();
        data.put("id", songId);
        data.put("cp", false);
        data.put("tv", 0);
        data.put("lv", 0);
        data.put("rv", 0);
        data.put("kv", 0);
        data.put("yv", 0);
        data.put("ytv", 0);
        data.put("yrv", 0);
        data.put("csrf_token", extractCsrfToken(cookie));
        return data;
    }

    /**
     * Fetch lyrics with translation for a song by its ID.
     * Returns both original LRC and translated LRC (tlyric).
     */
    public static void getLyricsWithTranslation(long songId, String cookie, LyricsFullCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = buildLyricRequestData(songId, cookie);
                String response = weapiPost("/api/song/lyric/v1", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                String lrc = "";
                JSONObject lrcObj = json.optJSONObject("lrc");
                if (lrcObj != null) {
                    lrc = lrcObj.optString("lyric", "");
                }
                String tlyric = "";
                JSONObject tlyricObj = json.optJSONObject("tlyric");
                if (tlyricObj != null) {
                    tlyric = tlyricObj.optString("lyric", "");
                }
                final String finalLrc = lrc;
                final String finalTlyric = tlyric;
                mainHandler.post(() -> callback.onResult(finalLrc, finalTlyric));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌词(含翻译)失败: " + songId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Fetch translated lyrics synchronously (for downloads).
     * Returns the tlyric LRC text, or null if unavailable.
     */
    public static String fetchTranslatedLyricsSync(long songId, String cookie) {
        try {
            JSONObject data = buildLyricRequestData(songId, cookie);
            String response = weapiPost("/api/song/lyric/v1", data.toString(), cookie);
            JSONObject json = new JSONObject(response);
            JSONObject tlyricObj = json.optJSONObject("tlyric");
            if (tlyricObj != null) {
                String tlyric = tlyricObj.optString("lyric", "");
                if (!tlyric.isEmpty()) return tlyric;
            }
            return null;
        } catch (Exception e) {
            MusicLog.w(TAG, "翻译歌词同步获取失败: " + songId, e);
            return null;
        }
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
        MusicLog.i(TAG, "[REQ] POST " + urlStr + "\n  请求体(原文): " + jsonData);

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
                java.io.InputStream errStream = conn.getErrorStream();
                reader = new BufferedReader(
                        new InputStreamReader(errStream != null ? errStream : conn.getInputStream(), "UTF-8"));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            String responseBody = sb.toString();
            MusicLog.api(TAG, "POST", urlStr, responseCode, responseBody);
            return responseBody;
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
        MusicLog.i(TAG, "[REQ] POST(mobile) " + urlStr + "\n  请求体(原文): " + jsonData);

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
                java.io.InputStream errStream = conn.getErrorStream();
                reader = new BufferedReader(
                        new InputStreamReader(errStream != null ? errStream : conn.getInputStream(), "UTF-8"));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            String responseBody = sb.toString();
            MusicLog.api(TAG, "POST(mobile)", urlStr, responseCode, responseBody);
            return responseBody;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Build a cookie string with Android mobile device identity.
     * Used for SMS login to avoid "环境不安全" error.
     * Based on NeteaseCloudMusicApiBackup util/request.js default cookies.
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
        sb.append("NMTID=").append(generateHexId(16)).append("; ");
        sb.append("_ntes_nuid=").append(generateHexId(16)).append("; ");
        sb.append("osver=13; ");
        sb.append("deviceId=").append(deviceId).append("; ");
        sb.append("os=android; ");
        sb.append("channel=").append(CHANNEL).append("; ");
        sb.append("appver=").append(APP_VER);
        return sb.toString();
    }

    /**
     * Generate a random hex string of the specified byte length.
     */
    private static String generateHexId(int byteLength) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
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
     * Mask phone number for safe logging (show first 3 and last 4 digits only).
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int maskCount = Math.max(0, phone.length() - 7);
        StringBuilder masks = new StringBuilder();
        for (int i = 0; i < maskCount; i++) masks.append('*');
        return phone.substring(0, 3) + masks + phone.substring(phone.length() - 4);
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

    // ==================== Song Recognition ====================

    /**
     * Recognize a song from PCM audio data (听歌识曲).
     * Per NeteaseCloudMusic_PythonSDK: audio_match(duration, audioFP) → /audio/match
     * POST https://interface.music.163.com/api/music/audio/match (POST to avoid 414 URI Too Long)
     * Body: sessionId=xxx&algorithmCode=shazam_v2&duration={sec}&rawdata={audioFP}&times=1&decrypt=1
     * Response: {code, data: {result: {songId, songName, artists:[{name}], album:{name}}}}
     *
     * @param pcmData  raw PCM bytes (16-bit LE, 16kHz, mono) — this is the audioFP/rawdata
     * @param cookie   user cookie (may be null/empty, not required)
     * @param callback result callback
     */
    public static void recognizeSong(byte[] pcmData, String cookie, RecognitionCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "听歌识曲", "pcm_bytes=" + pcmData.length);

                // Calculate duration: 16kHz * 2 bytes/sample (16-bit mono) = 32000 bytes/sec
                int durationSec = Math.max(1, pcmData.length / 32000);

                // Encode PCM as base64 (audioFP in Python SDK)
                String audioBase64 = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP);

                // Use POST to avoid 414 URI Too Long (base64 audio is very large)
                String apiUrl = "https://interface.music.163.com/api/music/audio/match";
                String postBody = "sessionId=0123456789abcdef"
                        + "&algorithmCode=shazam_v2"
                        + "&duration=" + durationSec
                        + "&rawdata=" + URLEncoder.encode(audioBase64, "UTF-8")
                        + "&times=1"
                        + "&decrypt=1";
                MusicLog.i(TAG, "[REQ] POST audio/match duration=" + durationSec + "s bytes=" + pcmData.length);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Referer", DOMAIN);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                if (cookie != null && !cookie.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookie);
                }
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(60000); // audio recognition may take longer
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
                        java.io.InputStream errStream = conn.getErrorStream();
                        reader = new BufferedReader(new InputStreamReader(
                                errStream != null ? errStream : conn.getInputStream(), "UTF-8"));
                    }
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    String response = sb.toString();
                    MusicLog.api(TAG, "POST", "audio/match", responseCode, response);

                    JSONObject json = new JSONObject(response);
                    // Response structure: {code, data: {result: {songId, songName, artists:[{name}], album:{name}}}}
                    JSONObject dataObj = json.optJSONObject("data");
                    JSONObject result = dataObj != null ? dataObj.optJSONObject("result") : null;
                    if (result == null) {
                        mainHandler.post(() -> callback.onError("未识别到歌曲"));
                        return;
                    }

                    String songName = result.optString("songName", "未知歌曲");
                    org.json.JSONArray artists = result.optJSONArray("artists");
                    String artist = (artists != null && artists.length() > 0)
                            ? artists.getJSONObject(0).optString("name", "未知歌手")
                            : "未知歌手";
                    JSONObject albumObj = result.optJSONObject("album");
                    String album = albumObj != null ? albumObj.optString("name", "") : "";
                    long songId = result.optLong("songId", 0);

                    MusicLog.i(TAG, "听歌识曲成功: " + songName + " - " + artist);
                    mainHandler.post(() -> callback.onResult(songName, artist, album, songId));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                MusicLog.e(TAG, "听歌识曲异常", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "识别失败，请重试"));
            }
        });
    }

    /**
     * Recognize a song from humming PCM audio data (哼歌识曲).
     * POST https://interface.music.163.com/api/music/audio/hum (POST to avoid 414 URI Too Long)
     *
     * @param pcmData  raw PCM bytes (16-bit LE, 16kHz, mono)
     * @param cookie   user cookie (may be null/empty, not required)
     * @param callback result callback
     */
    public static void recognizeHum(byte[] pcmData, String cookie, RecognitionCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "哼歌识曲", "pcm_bytes=" + pcmData.length);

                // Calculate duration: 16kHz * 2 bytes/sample (16-bit mono) = 32000 bytes/sec
                int durationSec = Math.max(1, pcmData.length / 32000);

                String audioBase64 = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP);

                // Use POST to avoid 414 URI Too Long
                String apiUrl = "https://interface.music.163.com/api/music/audio/hum";
                String postBody = "sessionId=0123456789abcdef"
                        + "&duration=" + durationSec
                        + "&rawdata=" + URLEncoder.encode(audioBase64, "UTF-8")
                        + "&times=1";
                MusicLog.i(TAG, "[REQ] POST audio/hum duration=" + durationSec + "s bytes=" + pcmData.length);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Referer", DOMAIN);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                if (cookie != null && !cookie.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookie);
                }
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(60000);
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
                        java.io.InputStream errStream = conn.getErrorStream();
                        reader = new BufferedReader(new InputStreamReader(
                                errStream != null ? errStream : conn.getInputStream(), "UTF-8"));
                    }
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    String response = sb.toString();
                    MusicLog.api(TAG, "POST", "audio/hum", responseCode, response);

                    JSONObject json = new JSONObject(response);
                    // Same response structure as audio/match: {code, data: {result: {songId, songName, artists, album}}}
                    JSONObject dataObj = json.optJSONObject("data");
                    JSONObject result = dataObj != null ? dataObj.optJSONObject("result") : null;
                    if (result == null) {
                        mainHandler.post(() -> callback.onError("未识别到歌曲"));
                        return;
                    }

                    String songName = result.optString("songName", "未知歌曲");
                    org.json.JSONArray artists = result.optJSONArray("artists");
                    String artist = (artists != null && artists.length() > 0)
                            ? artists.getJSONObject(0).optString("name", "未知歌手")
                            : "未知歌手";
                    JSONObject albumObj = result.optJSONObject("album");
                    String album = albumObj != null ? albumObj.optString("name", "") : "";
                    long songId = result.optLong("songId", 0);

                    MusicLog.i(TAG, "哼歌识曲成功: " + songName + " - " + artist);
                    mainHandler.post(() -> callback.onResult(songName, artist, album, songId));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                MusicLog.e(TAG, "哼歌识曲异常", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "识别失败，请重试"));
            }
        });
    }
}
