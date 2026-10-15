package com.qinghe.music163pro.api;

import android.os.Handler;
import android.os.Looper;

import com.qinghe.music163pro.util.MusicLog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetEase watch/wearable client (儿童手表端) EAPI helper.
 *
 * Implements the watch captcha login flow described in docs/163watch.md:
 *   1. POST /eapi/sms/captcha/sent   (params: cellphone, ctcode, secrete=music_middleuser_andrwarelogin)
 *   2. POST /eapi/login/cellphone    (params: phone, captcha, countrycode)
 *
 * All requests use the watch EAPI protocol:
 *   - URL: https://interface3.music.163.com/eapi/...
 *   - Sign with the original /api/... path
 *   - AES-128-ECB (key e82ckenh8dichen8), payload
 *     {url}-36cd479b6b5-{text}-36cd479b6b5-{md5("nobody{url}use{text}md5forencrypt")}
 *   - Sent as form field "params" (upper-case hex)
 *   - Response body may be AES-128-ECB encrypted binary; decrypt with the same key.
 */
public final class NeteaseWatchApi {

    private static final String TAG = "NeteaseWatchApi";

    private static final String EAPI_HOST = "https://interface3.music.163.com";
    private static final String API_SMS_CAPTCHA_SENT = "/api/sms/captcha/sent";
    private static final String API_LOGIN_CELLPHONE = "/api/login/cellphone";

    /** Watch client secret required by the captcha-sent endpoint. */
    private static final String WATCH_SECRET = "music_middleuser_andrwarelogin";

    // Watch client identity (com.netease.xtc.cloudmusic 2.9.105)
    private static final String WATCH_OS = "android";
    private static final String WATCH_APPVER = "2.9.105";
    private static final String WATCH_VERSIONCODE = "29105";
    private static final String WATCH_RESOLUTION = "320x360";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static String deviceId = UUID.randomUUID().toString().replace("-", "");
    private static final SecureRandom secureRandom = new SecureRandom();

    private NeteaseWatchApi() {
    }

    public interface WatchSmsCallback {
        void onResult(boolean success, String message);
        void onError(String message);
    }

    public interface WatchLoginCallback {
        /**
         * @param code     server code (200 = success)
         * @param message  server message
         * @param cookie   cookie string containing MUSIC_U (empty on failure)
         * @param nickname nickname from login response profile (may be empty)
         * @param userId   user id from login response profile (0 if unknown)
         */
        void onResult(int code, String message, String cookie, String nickname, long userId);
        void onError(String message);
    }

    /** Result of one eapi round-trip. */
    private static final class EapiResult {
        final int httpCode;
        final JSONObject json;
        final String cookie;
        EapiResult(int httpCode, JSONObject json, String cookie) {
            this.httpCode = httpCode;
            this.json = json;
            this.cookie = cookie;
        }
    }

    // ==================== Public API ====================

    /**
     * Send SMS verification code via the watch gateway.
     * Endpoint: /eapi/sms/captcha/sent
     * Params: cellphone, ctcode, secrete=music_middleuser_andrwarelogin
     */
    public static void sendSmsCaptcha(String phone, String ctcode, WatchSmsCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("cellphone", phone);
                data.put("ctcode", ctcode != null && !ctcode.isEmpty() ? ctcode : "86");
                data.put("secrete", WATCH_SECRET);

                MusicLog.op(TAG, "穿戴端发送验证码", "phone=" + maskPhone(phone));
                EapiResult result = eapiPost(API_SMS_CAPTCHA_SENT, data);

                JSONObject json = result.json;
                int code = json != null ? json.optInt("code", -1) : -1;
                String message = json != null
                        ? json.optString("message", json.optString("msg", ""))
                        : "HTTP " + result.httpCode;
                if (message.isEmpty()) message = "code=" + code;

                final boolean ok = code == 200;
                final String msg = ok ? "验证码已发送" : message;
                mainHandler.post(() -> callback.onResult(ok, msg));
            } catch (Exception e) {
                MusicLog.e(TAG, "穿戴端发送验证码失败", e);
                mainHandler.post(() -> callback.onError(errText(e)));
            }
        });
    }

    /**
     * Login with phone + SMS captcha via the watch gateway.
     * Endpoint: /eapi/login/cellphone
     * Params: phone, captcha, countrycode
     */
    public static void loginByCellphone(String phone, String captcha, String countrycode,
                                        WatchLoginCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("phone", phone);
                data.put("captcha", captcha);
                data.put("countrycode", countrycode != null && !countrycode.isEmpty()
                        ? countrycode : "86");

                MusicLog.op(TAG, "穿戴端验证码登录", "phone=" + maskPhone(phone));
                EapiResult result = eapiPost(API_LOGIN_CELLPHONE, data);

                JSONObject json = result.json;
                int code = result.httpCode;
                String message = "";
                String cookie = result.cookie != null ? result.cookie : "";
                String nickname = "";
                long userId = 0;

                if (json != null) {
                    code = json.optInt("code", result.httpCode);
                    message = json.optString("message", json.optString("msg", ""));
                    if (message.isEmpty()) message = "code=" + code;

                    // Profile with nickname/userId is returned on success
                    JSONObject profile = json.optJSONObject("profile");
                    if (profile == null) {
                        JSONObject dataObj = json.optJSONObject("data");
                        if (dataObj != null) profile = dataObj.optJSONObject("profile");
                    }
                    if (profile != null) {
                        nickname = profile.optString("nickname", "");
                        userId = profile.optLong("userId", 0);
                    }

                    // Fallback: token / cookie fields in the body
                    if (!cookie.contains("MUSIC_U") && json.has("token")) {
                        String token = json.optString("token", "");
                        if (!token.isEmpty()) cookie = "MUSIC_U=" + token;
                    }
                    if (!cookie.contains("MUSIC_U") && json.has("cookie")) {
                        String bodyCookie = json.optString("cookie", "");
                        if (bodyCookie.contains("MUSIC_U")) cookie = bodyCookie;
                    }
                }

                final int okCode = code;
                final String okNick = nickname;
                final long okUid = userId;
                if (code == 200 && !cookie.contains("MUSIC_U")) {
                    // Success but no usable session
                    mainHandler.post(() -> callback.onResult(okCode, "登录成功但未获取到Cookie",
                            "", okNick, okUid));
                    return;
                }

                final int fCode = code;
                final String fMsg = message;
                final String fCookie = code == 200 ? cookie : "";
                final String fNick = nickname;
                final long fUid = userId;
                mainHandler.post(() -> callback.onResult(fCode, fMsg, fCookie, fNick, fUid));
            } catch (Exception e) {
                MusicLog.e(TAG, "穿戴端验证码登录失败", e);
                mainHandler.post(() -> callback.onError(errText(e)));
            }
        });
    }

    // ==================== EAPI core ====================

    /**
     * Encrypt and POST one eapi request to the watch gateway, then decrypt the response.
     * @param apiPath original path starting with /api/
     */
    private static EapiResult eapiPost(String apiPath, JSONObject data) throws Exception {
        // Watch header (serialized to a JSON string inside the payload)
        JSONObject header = new JSONObject();
        header.put("os", WATCH_OS);
        header.put("appver", WATCH_APPVER);
        header.put("versioncode", WATCH_VERSIONCODE);
        header.put("deviceId", deviceId);
        header.put("resolution", WATCH_RESOLUTION);
        header.put("buildver", String.valueOf(System.currentTimeMillis() / 1000L));
        data.put("header", header.toString());
        data.put("e_r", false);

        String params = NeteaseApiCrypto.eapi(apiPath, data.toString());
        String postBody = "params=" + URLEncoder.encode(params, "UTF-8");

        String urlStr = EAPI_HOST + apiPath.replaceFirst("^/api/", "/eapi/");
        MusicLog.i(TAG, "[REQ] POST(watch-eapi) " + urlStr + "\n  请求体(原文): " + data);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent",
                "NeteaseMusic " + WATCH_APPVER + " (Android; " + WATCH_RESOLUTION + "; zh_CN)");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Cookie", buildWatchCookie());
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try {
            OutputStream os = conn.getOutputStream();
            os.write(postBody.getBytes("UTF-8"));
            os.close();

            int httpCode = conn.getResponseCode();
            InputStream stream;
            if (httpCode >= 200 && httpCode < 400) {
                stream = conn.getInputStream();
            } else {
                InputStream err = conn.getErrorStream();
                stream = err != null ? err : conn.getInputStream();
            }

            byte[] raw = readAllBytes(stream);
            String setCookie = extractSetCookies(conn);
            JSONObject json = parseResponse(raw);

            String bodyPreview = json != null ? json.toString()
                    : ("(binary " + raw.length + " bytes)");
            MusicLog.api(TAG, "POST(watch-eapi)", urlStr, httpCode, bodyPreview);

            return new EapiResult(httpCode, json, setCookie);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parse an eapi response: try plain JSON first, then AES-ECB decrypt.
     */
    private static JSONObject parseResponse(byte[] raw) {
        if (raw == null || raw.length == 0) return null;
        // 1) Plain JSON
        try {
            return new JSONObject(new String(raw, "UTF-8"));
        } catch (Exception ignored) {
        }
        // 2) AES-128-ECB encrypted binary
        String decrypted = NeteaseApiCrypto.eapiDecrypt(raw);
        if (decrypted != null) {
            try {
                return new JSONObject(decrypted);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Cookie header for the watch gateway. */
    private static String buildWatchCookie() {
        return "__remember_me=true; "
                + "os=" + WATCH_OS + "; "
                + "appver=" + WATCH_APPVER + "; "
                + "deviceId=" + deviceId + "; "
                + "NMTID=" + randomHex(16) + "; "
                + "_ntes_nuid=" + randomHex(16);
    }

    /** Collect name=value pairs from all Set-Cookie headers. */
    private static String extractSetCookies(HttpURLConnection conn) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> headers = conn.getHeaderFields();
        if (headers == null) return "";
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies == null) setCookies = headers.get("set-cookie");
        if (setCookies == null) return "";
        for (String setCookie : setCookies) {
            String nameValue = setCookie.split(";")[0].trim();
            if (nameValue.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(nameValue);
        }
        return sb.toString();
    }

    private static String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String errText(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "网络连接失败";
    }
}
