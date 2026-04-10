package com.qinghe.music163pro.api;

import com.qinghe.music163pro.model.PlaylistInfo;
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
import java.util.LinkedHashSet;
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
        void onResult(List<Song> songs);
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

    public interface SearchPlaylistCallback {
        void onResult(List<PlaylistInfo> playlists);
        void onError(String message);
    }

    public interface UserPlaylistsCallback {
        void onResult(List<PlaylistInfo> playlists);
        void onError(String message);
    }

    public interface PlaylistActionCallback {
        void onResult(boolean success);
        void onError(String message);
    }

    public interface PlaylistCreateCallback {
        void onResult(long playlistId, String name);
        void onError(String message);
    }

    /** Callback for lightweight playlist metadata fetch */
    public interface PlaylistMetaCallback {
        void onResult(int trackCount, String creator, long creatorUserId, int specialType, boolean subscribed);
        void onError(String message);
    }

    /** Callback for playlist detail that also returns metadata */
    public interface PlaylistDetailWithMetaCallback {
        void onResult(List<Song> songs, int trackCount, String creator, long creatorUserId, int specialType, boolean subscribed);
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

    // ==================== Search Playlists ====================

    /**
     * Search playlists via cloudsearch (type=1000).
     * Supports pagination with offset.
     */
    public static void searchPlaylists(String keyword, int offset, String cookie, SearchPlaylistCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "搜索歌单", "keyword=" + keyword + " offset=" + offset);
                JSONObject data = new JSONObject();
                data.put("s", keyword);
                data.put("type", 1000);  // 1000 = playlists
                data.put("limit", 20);
                data.put("offset", offset);
                data.put("total", true);

                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/cloudsearch/pc", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                List<PlaylistInfo> playlists = new ArrayList<>();
                JSONObject result = json.optJSONObject("result");
                if (result != null) {
                    JSONArray playlistsArray = result.optJSONArray("playlists");
                    if (playlistsArray != null) {
                        for (int i = 0; i < playlistsArray.length(); i++) {
                            JSONObject p = playlistsArray.getJSONObject(i);
                            long id = p.getLong("id");
                            String name = p.getString("name");
                            int trackCount = p.optInt("trackCount", 0);
                            String creator = "";
                            long creatorUserId = 0;
                            JSONObject creatorObj = p.optJSONObject("creator");
                            if (creatorObj != null) {
                                creator = creatorObj.optString("nickname", "");
                                creatorUserId = creatorObj.optLong("userId", 0);
                            }
                            int specialType = p.optInt("specialType", 0);
                            boolean subscribed = p.optBoolean("subscribed", false);
                            playlists.add(new PlaylistInfo(id, name, trackCount, creator,
                                    creatorUserId, subscribed, String.valueOf(specialType)));
                        }
                    }
                }
                MusicLog.d(TAG, "搜索歌单结果: " + playlists.size() + " 个");
                mainHandler.post(() -> callback.onResult(playlists));
            } catch (Exception e) {
                MusicLog.w(TAG, "搜索歌单失败: " + keyword, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== User Playlists ====================

    /**
     * Get user's playlists from the cloud.
     * Returns all playlists (created + subscribed).
     */
    public static void getUserPlaylists(String cookie, UserPlaylistsCallback callback) {
        executor.execute(() -> {
            try {
                long uid = extractUidFromCookie(cookie);
                if (uid <= 0) {
                    mainHandler.post(() -> callback.onError("请先登录"));
                    return;
                }
                MusicLog.op(TAG, "获取用户歌单", "uid=" + uid);

                String csrfToken = extractCsrfToken(cookie);
                List<PlaylistInfo> allPlaylists = new ArrayList<>();

                // Fetch playlists in pages
                int offset = 0;
                int limit = 50;
                boolean hasMore = true;
                while (hasMore) {
                    JSONObject plData = new JSONObject();
                    plData.put("uid", uid);
                    plData.put("limit", limit);
                    plData.put("offset", offset);
                    plData.put("csrf_token", csrfToken);

                    String plResponse = weapiPost("/api/user/playlist", plData.toString(), cookie);
                    JSONObject plJson = new JSONObject(plResponse);
                    JSONArray playlists = plJson.optJSONArray("playlist");

                    if (playlists == null || playlists.length() == 0) {
                        break;
                    }

                    for (int i = 0; i < playlists.length(); i++) {
                        JSONObject p = playlists.getJSONObject(i);
                        long id = p.getLong("id");
                        String name = p.getString("name");
                        int trackCount = p.optInt("trackCount", 0);
                        String creator = "";
                        long creatorId = 0;
                        JSONObject creatorObj = p.optJSONObject("creator");
                        if (creatorObj != null) {
                            creator = creatorObj.optString("nickname", "");
                            creatorId = creatorObj.optLong("userId", 0);
                        }
                        boolean subscribed = p.optBoolean("subscribed", false);
                        int specialType = p.optInt("specialType", 0);
                        allPlaylists.add(new PlaylistInfo(id, name, trackCount, creator,
                                creatorId, subscribed, String.valueOf(specialType)));
                    }

                    hasMore = plJson.optBoolean("more", false);
                    offset += playlists.length();
                }

                MusicLog.d(TAG, "用户歌单: " + allPlaylists.size() + " 个");
                mainHandler.post(() -> callback.onResult(allPlaylists));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取用户歌单失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
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

                // Step 2: Get playlist detail - tracks array is limited to ~n tracks,
                // but trackIds contains ALL song IDs. Use trackIds to fetch all songs.
                JSONObject detailData = new JSONObject();
                detailData.put("id", likedPlaylistId);
                detailData.put("n", 0);  // Don't need full tracks, we'll use trackIds
                detailData.put("csrf_token", csrfToken);

                String detailResponse = weapiPost("/api/v6/playlist/detail", detailData.toString(), cookie);
                JSONObject detailJson = new JSONObject(detailResponse);
                JSONObject playlist = detailJson.optJSONObject("playlist");

                if (playlist == null) {
                    getCloudFavoritesLegacy(cookie, uid, csrfToken, callback);
                    return;
                }

                JSONArray trackIds = playlist.optJSONArray("trackIds");
                if (trackIds == null || trackIds.length() == 0) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>()));
                    return;
                }

                // Fetch song details in batches of 200 using trackIds
                List<Song> songs = fetchSongDetailsByTrackIds(trackIds, csrfToken, cookie);
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

            // Fetch all songs in batches of 200
            int total = idsArray.length();
            java.util.Map<Long, Song> songMap = new java.util.LinkedHashMap<>();
            for (int batchStart = 0; batchStart < total; batchStart += 200) {
                int batchEnd = Math.min(batchStart + 200, total);
                JSONArray songIds = new JSONArray();
                for (int i = batchStart; i < batchEnd; i++) {
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
            }

            List<Song> songs = new ArrayList<>();
            for (int i = 0; i < total; i++) {
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
     * Fetch song details in batches of 200 using trackIds from a playlist.
     * trackIds is a JSONArray of objects with at least an "id" field.
     * Returns songs in the same order as trackIds.
     */
    private static List<Song> fetchSongDetailsByTrackIds(JSONArray trackIds, String csrfToken, String cookie) throws Exception {
        int total = trackIds.length();
        java.util.Map<Long, Song> songMap = new java.util.LinkedHashMap<>();

        for (int batchStart = 0; batchStart < total; batchStart += 200) {
            int batchEnd = Math.min(batchStart + 200, total);
            JSONArray batchIds = new JSONArray();
            for (int i = batchStart; i < batchEnd; i++) {
                JSONObject idObj = new JSONObject();
                long songId = trackIds.getJSONObject(i).getLong("id");
                idObj.put("id", songId);
                batchIds.put(idObj);
            }

            JSONObject detailData = new JSONObject();
            detailData.put("c", batchIds.toString());
            detailData.put("csrf_token", csrfToken);

            String detailResponse = weapiPost("/api/v3/song/detail", detailData.toString(), cookie);
            JSONObject detailJson = new JSONObject(detailResponse);
            JSONArray songsArray = detailJson.optJSONArray("songs");

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
        }

        // Preserve original order from trackIds
        List<Song> result = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            long id = trackIds.getJSONObject(i).getLong("id");
            Song song = songMap.get(id);
            if (song != null) {
                result.add(song);
            }
        }
        return result;
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
                data.put("n", 0);  // Don't need full tracks in response, we'll use trackIds
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/v6/playlist/detail", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONObject playlist = json.optJSONObject("playlist");
                if (playlist == null) {
                    mainHandler.post(() -> callback.onError("获取歌单详情失败"));
                    return;
                }

                JSONArray trackIds = playlist.optJSONArray("trackIds");
                if (trackIds == null || trackIds.length() == 0) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>()));
                    return;
                }

                // Fetch all song details in batches using trackIds
                List<Song> songs = fetchSongDetailsByTrackIds(trackIds, csrfToken, cookie);
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌单详情失败: " + playlistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get playlist detail with metadata (tracks + creator/specialType info).
     * Used by PlaylistDetailActivity to self-correct metadata from API.
     */
    public static void getPlaylistDetailWithMeta(long playlistId, String cookie, PlaylistDetailWithMetaCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("id", playlistId);
                data.put("n", 0);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/v6/playlist/detail", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONObject playlist = json.optJSONObject("playlist");
                if (playlist == null) {
                    mainHandler.post(() -> callback.onError("获取歌单详情失败"));
                    return;
                }

                // Extract metadata
                int apiTrackCount = playlist.optInt("trackCount", 0);
                String creatorName = "";
                long creatorUserId = 0;
                JSONObject creatorObj = playlist.optJSONObject("creator");
                if (creatorObj != null) {
                    creatorName = creatorObj.optString("nickname", "");
                    creatorUserId = creatorObj.optLong("userId", 0);
                }
                int specialType = playlist.optInt("specialType", 0);
                boolean subscribed = playlist.optBoolean("subscribed", false);

                // Extract songs
                JSONArray trackIds = playlist.optJSONArray("trackIds");
                List<Song> songs;
                if (trackIds == null || trackIds.length() == 0) {
                    songs = new ArrayList<>();
                } else {
                    songs = fetchSongDetailsByTrackIds(trackIds, csrfToken, cookie);
                }

                final String cn = creatorName;
                final long cuid = creatorUserId;
                mainHandler.post(() -> callback.onResult(songs, apiTrackCount, cn, cuid, specialType, subscribed));
            } catch (Exception e) {
                MusicLog.w(TAG, "获取歌单详情失败: " + playlistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get playlist metadata only (lightweight, no tracks).
     * Used by FavoritesListActivity to refresh track counts for locally saved playlists.
     */
    public static void getPlaylistMeta(long playlistId, String cookie, PlaylistMetaCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("id", playlistId);
                data.put("n", 0);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);

                String response = weapiPost("/api/v6/playlist/detail", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                JSONObject playlist = json.optJSONObject("playlist");
                if (playlist == null) {
                    mainHandler.post(() -> callback.onError("获取歌单信息失败"));
                    return;
                }

                int trackCount = playlist.optInt("trackCount", 0);
                String creatorName = "";
                long creatorUserId = 0;
                JSONObject creatorObj = playlist.optJSONObject("creator");
                if (creatorObj != null) {
                    creatorName = creatorObj.optString("nickname", "");
                    creatorUserId = creatorObj.optLong("userId", 0);
                }
                int specialType = playlist.optInt("specialType", 0);
                boolean subscribed = playlist.optBoolean("subscribed", false);

                final String cn = creatorName;
                final long cuid = creatorUserId;
                mainHandler.post(() -> callback.onResult(trackCount, cn, cuid, specialType, subscribed));
            } catch (Exception e) {
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
     * Recognize songs from a NetEase audio fingerprint generated on-device.
     *
     * @param fingerprintBase64 base64 fingerprint generated from 8k mono float PCM
     * @param durationSec audio duration in seconds
     * @param cookie user cookie (may be null/empty)
     * @param callback result callback
     */
    public static void recognizeSong(String fingerprintBase64, int durationSec, String cookie,
                                     RecognitionCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "听歌识曲", "duration=" + durationSec + "s");

                String apiUrl = "https://interface.music.163.com/api/music/audio/match";
                String postBody = "sessionId=" + UUID.randomUUID().toString().replace("-", "")
                        + "&algorithmCode=shazam_v2"
                        + "&duration=" + Math.max(1, durationSec)
                        + "&rawdata=" + URLEncoder.encode(fingerprintBase64, "UTF-8")
                        + "&times=1"
                        + "&decrypt=1";
                MusicLog.i(TAG, "[REQ] POST audio/match duration=" + durationSec + "s");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Referer", DOMAIN);
                conn.setRequestProperty("Origin", DOMAIN);
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
                    JSONObject dataObj = json.optJSONObject("data");
                    JSONArray resultArray = dataObj != null ? dataObj.optJSONArray("result") : null;
                    List<Song> matches = new ArrayList<>();
                    LinkedHashSet<Long> seenSongIds = new LinkedHashSet<>();

                    if (resultArray != null) {
                        for (int i = 0; i < resultArray.length(); i++) {
                            JSONObject matchObj = resultArray.optJSONObject(i);
                            if (matchObj == null) continue;
                            Song song = parseRecognitionSong(matchObj.optJSONObject("song"));
                            if (song == null) {
                                song = parseRecognitionSong(matchObj);
                            }
                            if (song == null) continue;
                            long songId = song.getId();
                            if (songId > 0 && !seenSongIds.add(songId)) {
                                continue;
                            }
                            matches.add(song);
                        }
                    } else {
                        Song single = parseRecognitionSong(dataObj != null ? dataObj.optJSONObject("result") : null);
                        if (single != null) {
                            matches.add(single);
                        }
                    }

                    if (matches.isEmpty()) {
                        String message = json.optString("message", "未识别到歌曲");
                        mainHandler.post(() -> callback.onError(message));
                        return;
                    }

                    MusicLog.i(TAG, "听歌识曲成功: matches=" + matches.size());
                    mainHandler.post(() -> callback.onResult(matches));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                MusicLog.e(TAG, "听歌识曲异常", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "识别失败，请重试"));
            }
        });
    }

    private static Song parseRecognitionSong(JSONObject songObj) {
        if (songObj == null) {
            return null;
        }

        long songId = songObj.optLong("id", songObj.optLong("songId", 0));
        String songName = songObj.optString("name", songObj.optString("songName", ""));
        JSONArray artists = songObj.optJSONArray("artist");
        if (artists == null) {
            artists = songObj.optJSONArray("artists");
        }
        String artist = "未知歌手";
        if (artists != null && artists.length() > 0) {
            JSONObject artistObj = artists.optJSONObject(0);
            if (artistObj != null) {
                artist = artistObj.optString("name", artist);
            }
        } else if (!songObj.optString("artistName").isEmpty()) {
            artist = songObj.optString("artistName");
        }
        JSONObject albumObj = songObj.optJSONObject("album");
        if (albumObj == null) {
            albumObj = songObj.optJSONObject("al");
        }
        String album = albumObj != null ? albumObj.optString("name", "") : "";
        if (songName.isEmpty()) {
            return null;
        }
        return new Song(songId, songName, artist, album);
    }

    // ==================== Playlist Subscribe / Unsubscribe ====================

    /**
     * Subscribe (collect) or unsubscribe a playlist.
     * @param subscribe true to subscribe, false to unsubscribe
     */
    public static void subscribePlaylist(long playlistId, boolean subscribe, String cookie,
                                          PlaylistActionCallback callback) {
        executor.execute(() -> {
            try {
                String action = subscribe ? "subscribe" : "unsubscribe";
                MusicLog.op(TAG, "歌单" + (subscribe ? "收藏" : "取消收藏"), "id=" + playlistId);
                JSONObject data = new JSONObject();
                data.put("id", playlistId);
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/playlist/" + action, data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "歌单收藏操作失败: " + playlistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Playlist Create ====================

    /**
     * Create a new playlist.
     * @param name playlist name
     * @param privacy 0=normal, 10=private
     */
    public static void createPlaylist(String name, int privacy, String cookie,
                                       PlaylistCreateCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "创建歌单", "name=" + name);
                JSONObject data = new JSONObject();
                data.put("name", name);
                data.put("privacy", String.valueOf(privacy));
                data.put("type", "NORMAL");
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/playlist/create", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    JSONObject pl = json.optJSONObject("playlist");
                    long newId = pl != null ? pl.optLong("id", 0) : 0;
                    String newName = pl != null ? pl.optString("name", name) : name;
                    mainHandler.post(() -> callback.onResult(newId, newName));
                } else {
                    String msg = json.optString("message", "创建失败");
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                MusicLog.w(TAG, "创建歌单失败: " + name, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Playlist Delete ====================

    /**
     * Delete a playlist (completely remove from cloud).
     */
    public static void deletePlaylist(long playlistId, String cookie,
                                       PlaylistActionCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "删除歌单", "id=" + playlistId);
                JSONObject data = new JSONObject();
                data.put("ids", "[" + playlistId + "]");
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/playlist/remove", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                mainHandler.post(() -> callback.onResult(code == 200));
            } catch (Exception e) {
                MusicLog.w(TAG, "删除歌单失败: " + playlistId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    // ==================== Add/Remove Tracks to Playlist ====================

    /**
     * Add or remove tracks from a playlist.
     * @param op "add" or "del"
     * @param playlistId target playlist ID
     * @param trackIds song IDs to add/remove
     */
    public static void playlistTracks(String op, long playlistId, long[] trackIds,
                                       String cookie, PlaylistActionCallback callback) {
        executor.execute(() -> {
            try {
                MusicLog.op(TAG, "歌单曲目操作", "op=" + op + " pid=" + playlistId);
                JSONObject data = new JSONObject();
                data.put("op", op);
                data.put("pid", playlistId);
                // Build trackIds JSON array string
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < trackIds.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(trackIds[i]);
                }
                sb.append("]");
                data.put("trackIds", sb.toString());
                data.put("imme", "true");
                String csrfToken = extractCsrfToken(cookie);
                data.put("csrf_token", csrfToken);
                String response = weapiPost("/api/playlist/manipulate/tracks", data.toString(), cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    mainHandler.post(() -> callback.onResult(true));
                } else if (code == 512) {
                    // Retry with duplicated trackIds (NeteaseCloudMusic workaround)
                    StringBuilder sb2 = new StringBuilder("[");
                    for (int i = 0; i < trackIds.length; i++) {
                        if (i > 0) sb2.append(",");
                        sb2.append(trackIds[i]);
                    }
                    for (long tid : trackIds) {
                        sb2.append(",").append(tid);
                    }
                    sb2.append("]");
                    data.put("trackIds", sb2.toString());
                    String response2 = weapiPost("/api/playlist/manipulate/tracks", data.toString(), cookie);
                    JSONObject json2 = new JSONObject(response2);
                    int code2 = json2.optInt("code", -1);
                    mainHandler.post(() -> callback.onResult(code2 == 200));
                } else {
                    String msg = json.optString("message", "操作失败");
                    mainHandler.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                MusicLog.w(TAG, "歌单曲目操作失败", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }

    /**
     * Get UID from cookie (public access for use in activities).
     */
    public static void getUid(String cookie, AccountCallback callback) {
        executor.execute(() -> {
            try {
                long uid = extractUidFromCookie(cookie);
                JSONObject result = new JSONObject();
                result.put("uid", uid);
                mainHandler.post(() -> callback.onResult(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        });
    }
}
