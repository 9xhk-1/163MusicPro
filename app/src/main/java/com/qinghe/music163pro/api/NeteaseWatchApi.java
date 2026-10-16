package com.qinghe.music163pro.api;

import android.os.Handler;
import android.os.Looper;

import com.qinghe.music163pro.util.MusicLog;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetEase watch/wearable child-watch (儿童手表端) EAPI helper.
 *
 * Implements the watch captcha login flow per the reference Python client
 * (netease-music-source: result/netease_free_trial.py) and docs/163watch.md:
 *   1. POST /eapi/sms/captcha/sent  (cellphone, ctcode, secrete=music_middleuser_andrwarelogin)
 *   2. POST /eapi/login/cellphone   (phone, captcha, countrycode)
 *
 * Protocol details mirrored 1:1 from the working Python client:
 *   - Sign with the original /api/... path, POST to /eapi/... on interface3.music.163.com
 *   - AES-128-ECB (key e82ckenh8dichen8):
 *     params = hex(nobody{url}use{text}md5forencrypt -> md5 -> {url}-36cd479b6b5-{text}-36cd479b6b5-{digest})
 *   - payload["header"] is a NESTED JSON OBJECT holding the session cookies + requestId
 *     (NOT a serialized string)
 *   - payload["e_r"] = "true" (string) — server returns an AES-encrypted binary response
 *   - a session cookie jar is shared across requests (captcha-sent cookies carry over
 *     to the login request)
 *   - response decrypt: raw binary AES-ECB, fallback hex-encoded bytes
 */
public final class NeteaseWatchApi {

    private static final String TAG = "NeteaseWatchApi";

    private static final String EAPI_HOST = "https://interface3.music.163.com";
    private static final String API_SMS_CAPTCHA_SENT = "/api/sms/captcha/sent";
    private static final String API_LOGIN_CELLPHONE = "/api/login/cellphone";

    /** Watch client secret required by the captcha-sent endpoint. */
    private static final String WATCH_SECRET = "music_middleuser_andrwarelogin";

    // Exact UA captured from the real watch app request:
    // NeteaseMusic/2.9.105.1776866597(29105);Dalvik/2.1.0 (Linux; U; Android 13; Z9 Build/OPM1.171019.026)
    private static final String WATCH_USER_AGENT =
            "NeteaseMusic/2.9.105.1776866597(29105);Dalvik/2.1.0 "
            + "(Linux; U; Android 13; Z9 Build/OPM1.171019.026)";

    /** Captured mconfig-info header value (static in the real client). */
    private static final String MCONFIG_INFO =
            "{\"IuRPVVmc3WWul9fT\":{\"version\":\"135168\",\"appver\":\"2.9.105\"}}";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    /**
     * Real XTC watch device identity cookies (captured from /sdcard/cookie.txt
     * on the watch). Merged into EVERY request: payload "header" object and
     * the Cookie request header. os=andrwear (not "android") is what the real
     * watch client sends — the gateway expects it.
     */
    private static final String[][] DEVICE_COOKIES = {
            {"osver", "13"},
            {"appver", "2.9.105"},
            {"versioncode", "29105"},
            {"mobilename", "Z9"},
            {"distributeChannel", "andrwear%24%7B%22channel%22%3A%22xtc-store%22%7D"},
            {"buildver", "1776866597"},
            {"resolution", "360x320"},
            {"brand", "XTC"},
            {"os", "andrwear"},
            {"channel", "xtc-store"},
    };

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Session cookie jar shared across requests (mirrors the Python requests.Session).
     * Cookies set by the server (e.g. during captcha-sent) are carried into the
     * subsequent login request.
     */
    private static final Map<String, String> cookieJar = new LinkedHashMap<>();

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
         * @param userId   user id from login response (0 if unknown)
         */
        void onResult(int code, String message, String cookie, String nickname, long userId);
        void onError(String message);
    }

    /** Result of one eapi round-trip. */
    private static final class EapiResult {
        final int httpCode;
        final JSONObject json;
        EapiResult(int httpCode, JSONObject json) {
            this.httpCode = httpCode;
            this.json = json;
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
                String nickname = "";
                long userId = 0;
                boolean hasSession = false;

                if (json != null) {
                    code = json.optInt("code", result.httpCode);
                    message = json.optString("message", json.optString("msg", ""));
                    if (message.isEmpty()) message = "code=" + code;

                    // account/profile as in the Python client:
                    // user_id = account.id or profile.userId; nickname = profile.nickname
                    JSONObject account = json.optJSONObject("account");
                    JSONObject profile = json.optJSONObject("profile");
                    if (profile != null) {
                        nickname = profile.optString("nickname", "");
                        userId = profile.optLong("userId", 0);
                    }
                    if (userId == 0 && account != null) {
                        userId = account.optLong("id", 0);
                    }
                    if (nickname.isEmpty() && userId > 0) {
                        nickname = "User_" + userId;
                    }
                    hasSession = true;
                }

                // Session cookie: ONLY server-issued values from the jar
                // (device identity items from DEVICE_COOKIES are excluded —
                // they are request-side context, not login credentials)
                String cookie = sessionCookieToString();
                if (!cookie.contains("MUSIC_U") && json != null) {
                    if (json.has("token")) {
                        String token = json.optString("token", "");
                        if (!token.isEmpty()) cookie = mergeCookie(cookie, "MUSIC_U=" + token);
                    }
                    if (!cookie.contains("MUSIC_U") && json.has("cookie")) {
                        String bodyCookie = json.optString("cookie", "");
                        if (bodyCookie.contains("MUSIC_U")) cookie = mergeCookie(cookie, bodyCookie);
                    }
                }

                final int okCode = code;
                final String okNick = nickname;
                final long okUid = userId;
                final String okCookie = code == 200 && cookie.contains("MUSIC_U") ? cookie : "";
                final String okMsg = message;
                final boolean okHasSession = hasSession;

                if (code == 200 && okCookie.isEmpty()) {
                    mainHandler.post(() -> callback.onResult(okCode,
                            okHasSession ? "登录成功但未获取到Cookie" : okMsg, "", okNick, okUid));
                    return;
                }
                mainHandler.post(() -> callback.onResult(okCode, okMsg, okCookie, okNick, okUid));
            } catch (Exception e) {
                MusicLog.e(TAG, "穿戴端验证码登录失败", e);
                mainHandler.post(() -> callback.onError(errText(e)));
            }
        });
    }

    /** Reset the session cookie jar (e.g. after logout). */
    public static void clearSession() {
        synchronized (cookieJar) {
            cookieJar.clear();
        }
    }

    // ==================== EAPI core ====================

    /**
     * Encrypt and POST one eapi request to the watch gateway, then decrypt the response.
     * Mirrors NeteaseWearableAPI.request() in the reference Python client.
     * @param apiPath original path starting with /api/
     */
    private static EapiResult eapiPost(String apiPath, JSONObject data) throws Exception {
        // Payload layout per watch163.py (the known-working client):
        //   payload["header"] = NESTED OBJECT: session cookies + requestId
        //   payload["e_r"]    = "true" (string)
        data.put("header", buildHeaderObject());
        data.put("e_r", "true");

        String params = NeteaseApiCrypto.eapi(apiPath, data.toString());
        String postBody = "params=" + URLEncoder.encode(params, "UTF-8");

        String urlStr = EAPI_HOST + apiPath.replaceFirst("^/api/", "/eapi/");
        MusicLog.i(TAG, "[REQ] POST(watch-eapi) " + urlStr + "\n  请求体(原文): " + data);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        // Exact header set captured from the real watch client (watch163.py FIXED_HEADERS)
        conn.setRequestProperty("User-Agent", WATCH_USER_AGENT);
        conn.setRequestProperty("cmpageid", "WatchMergePlayerActivity");
        conn.setRequestProperty("mconfig-info", MCONFIG_INFO);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Cookie", cookieJarToString());
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

            byte[] raw = readAllBytes(maybeGunzip(conn, stream));
            // Merge Set-Cookie into the session jar (like requests.Session)
            updateCookieJar(conn);
            JSONObject json = parseResponse(raw);

            String bodyPreview = json != null ? json.toString()
                    : ("(undecryptable " + raw.length + " bytes)");
            MusicLog.api(TAG, "POST(watch-eapi)", urlStr, httpCode, bodyPreview);

            return new EapiResult(httpCode, json);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Wrap the input stream with GZIPInputStream when the server responds
     * with Content-Encoding: gzip (the real client sends accept-encoding: gzip).
     */
    private static InputStream maybeGunzip(HttpURLConnection conn, InputStream stream) {
        try {
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase().contains("gzip")) {
                return new java.util.zip.GZIPInputStream(stream);
            }
        } catch (Exception ignored) {
        }
        return stream;
    }

    /**
     * Parse an eapi response: try plain JSON first, then AES-ECB decrypt
     * (raw binary, then hex-encoded fallback) — same order as eapi_decrypt() in Python.
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
        // 3) Hex-encoded ciphertext
        String trimmed;
        try {
            trimmed = new String(raw, "UTF-8").trim();
        } catch (Exception e) {
            return null;
        }
        if (isHex(trimmed) && trimmed.length() % 2 == 0) {
            try {
                byte[] bytes = new byte[trimmed.length() / 2];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) Integer.parseInt(
                            trimmed.substring(i * 2, i * 2 + 2), 16);
                }
                String dec = NeteaseApiCrypto.eapiDecrypt(bytes);
                if (dec != null) {
                    try {
                        return new JSONObject(dec);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Merge all Set-Cookie name=value pairs from the response into the session jar. */
    private static void updateCookieJar(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        if (headers == null) return;
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies == null) setCookies = headers.get("set-cookie");
        if (setCookies == null) return;
        synchronized (cookieJar) {
            for (String setCookie : setCookies) {
                String nameValue = setCookie.split(";")[0].trim();
                int eq = nameValue.indexOf('=');
                if (eq <= 0) continue;
                String name = nameValue.substring(0, eq).trim();
                String value = nameValue.substring(eq + 1).trim();
                cookieJar.put(name, value);
            }
        }
    }

    /**
     * payload["header"] — nested JSON object per watch163.py _header_obj():
     * current session cookies (device identity + server-issued values from the
     * jar) plus a fresh requestId. NOT a serialized string, NOT "{}".
     */
    private static JSONObject buildHeaderObject() throws org.json.JSONException {
        JSONObject header = new JSONObject();
        // Device identity (watch163.py FIXED_COOKIES)
        for (String[] kv : DEVICE_COOKIES) {
            header.put(kv[0], kv[1]);
        }
        // Server-issued session cookies (MUSIC_U, __csrf, NMTID ... collected
        // from Set-Cookie exactly like requests.Session) — these override
        // the static values when present.
        Map<String, String> snapshot;
        synchronized (cookieJar) {
            snapshot = new LinkedHashMap<>(cookieJar);
        }
        for (Map.Entry<String, String> e : snapshot.entrySet()) {
            header.put(e.getKey(), e.getValue());
        }
        // requestId: {millis}_{rand4}
        header.put("requestId", System.currentTimeMillis()
                + "_" + (1000 + secureRandom.nextInt(9000)));
        return header;
    }

    /**
     * Session-only cookie string: entries collected from Set-Cookie, with the
     * DEVICE_COOKIES keys filtered out. This is the persisted login credential
     * handed to the caller (MUSIC_U, __csrf, NMTID, ...), never the device
     * identity items (os, appver, brand ... which are re-attached per request).
     */
    private static String sessionCookieToString() {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> deviceKeys = new java.util.HashSet<>();
        for (String[] kv : DEVICE_COOKIES) {
            deviceKeys.add(kv[0]);
        }
        synchronized (cookieJar) {
            for (Map.Entry<String, String> e : cookieJar.entrySet()) {
                if (deviceKeys.contains(e.getKey())) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
        }
        return sb.toString();
    }

    /**
     * Render the Cookie request header: fixed device identity + everything
     * collected from Set-Cookie. No self-invented deviceId/__csrf/NMTID —
     * per watch163.py those are server-issued and carried over via the jar.
     */
    private static String cookieJarToString() {
        StringBuilder sb = new StringBuilder();
        Map<String, String> base = new LinkedHashMap<>();
        // Device identity from the capture (watch163.py FIXED_COOKIES)
        for (String[] kv : DEVICE_COOKIES) {
            base.put(kv[0], kv[1]);
        }
        // Server-issued session cookies (override static on key collision)
        synchronized (cookieJar) {
            base.putAll(cookieJar);
        }
        for (Map.Entry<String, String> e : base.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    /** Merge extra cookie pairs (e.g. MUSIC_U=xxx) into a cookie string. */
    private static String mergeCookie(String existing, String extra) {
        if (extra == null || extra.isEmpty()) return existing;
        StringBuilder sb = new StringBuilder(existing == null ? "" : existing);
        for (String pair : extra.split(";")) {
            String p = pair.trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(p);
        }
        return sb.toString();
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

    private static boolean isHex(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String errText(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "网络连接失败";
    }
}
