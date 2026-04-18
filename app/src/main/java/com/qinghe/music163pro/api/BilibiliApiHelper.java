package com.qinghe.music163pro.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bilibili API helper for fetching video audio streams, subtitles, and QR login.
 * Follows the same async pattern as MusicApiHelper (ExecutorService + Handler).
 */
public class BilibiliApiHelper {

    private static final String TAG = "BilibiliApiHelper";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.bilibili.com";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ======================== Callback Interfaces ========================

    public interface VideoInfoCallback {
        void onResult(List<BilibiliPage> pages);
        void onError(String message);
    }

    public interface AudioStreamCallback {
        void onResult(String audioUrl);
        void onError(String message);
    }

    public interface SubtitleCallback {
        void onResult(String lrcText);
        void onError(String message);
    }

    public interface QrCodeCallback {
        void onResult(String qrUrl, String qrcodeKey);
        void onError(String message);
    }

    public interface QrPollCallback {
        void onResult(int code, String message, String cookie);
        void onError(String message);
    }

    public interface SubtitleListCallback {
        void onResult(List<SubtitleInfo> subtitles);
        void onError(String message);
    }

    public interface SearchVideosCallback {
        void onResult(List<BilibiliSearchVideo> videos);
        void onError(String message);
    }

    public interface FavoriteFoldersCallback {
        void onResult(List<BilibiliFavoriteFolder> folders);
        void onError(String message);
    }

    public interface FavoriteFolderVideosCallback {
        void onResult(String folderTitle, List<BilibiliSearchVideo> videos);
        void onError(String message);
    }

    // ======================== Data Classes ========================

    public static class BilibiliPage {
        public long cid;
        public int page;
        public String part;
        public int duration;
        public String bvid;
        public String videoTitle;
        public String ownerName;

        public BilibiliPage(long cid, int page, String part, int duration,
                            String bvid, String videoTitle, String ownerName) {
            this.cid = cid;
            this.page = page;
            this.part = part;
            this.duration = duration;
            this.bvid = bvid;
            this.videoTitle = videoTitle;
            this.ownerName = ownerName;
        }
    }

    public static class SubtitleInfo {
        public String lan;
        public String lanDoc;
        public String subtitleUrl;

        public SubtitleInfo(String lan, String lanDoc, String subtitleUrl) {
            this.lan = lan;
            this.lanDoc = lanDoc;
            this.subtitleUrl = subtitleUrl;
        }
    }

    public static class BilibiliSearchVideo {
        public long aid;
        public String bvid;
        public String title;
        public String ownerName;
        public String description;
        public int duration;

        public BilibiliSearchVideo(long aid, String bvid, String title,
                                   String ownerName, String description, int duration) {
            this.aid = aid;
            this.bvid = bvid;
            this.title = title;
            this.ownerName = ownerName;
            this.description = description;
            this.duration = duration;
        }
    }

    public static class BilibiliFavoriteFolder {
        public long mediaId;
        public String title;
        public int mediaCount;

        public BilibiliFavoriteFolder(long mediaId, String title, int mediaCount) {
            this.mediaId = mediaId;
            this.title = title;
            this.mediaCount = mediaCount;
        }
    }

    // ======================== API Methods ========================

    /**
     * Get video info including page list.
     */
    public static void getVideoInfo(String bvid, String cookie, VideoInfoCallback callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
                String response = httpGet(url, cookie);
                JSONObject json = new JSONObject(response);

                int code = json.optInt("code", -1);
                if (code != 0) {
                    String msg = json.optString("message", "Unknown error");
                    postError(callback, msg);
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                String title = data.optString("title", "");
                String ownerName = data.optJSONObject("owner") != null
                        ? data.getJSONObject("owner").optString("name", "") : "";

                JSONArray pagesArray = data.optJSONArray("pages");
                List<BilibiliPage> pages = new ArrayList<>();

                if (pagesArray != null) {
                    for (int i = 0; i < pagesArray.length(); i++) {
                        JSONObject p = pagesArray.getJSONObject(i);
                        long cid = p.optLong("cid", 0);
                        int pageNum = p.optInt("page", i + 1);
                        String part = p.optString("part", "");
                        int duration = p.optInt("duration", 0);
                        pages.add(new BilibiliPage(cid, pageNum, part, duration,
                                bvid, title, ownerName));
                    }
                }

                mainHandler.post(() -> callback.onResult(pages));
            } catch (Exception e) {
                Log.e(TAG, "getVideoInfo error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Get audio stream URL for a specific video page.
     */
    public static void getAudioStreamUrl(String bvid, long cid, String cookie,
                                         AudioStreamCallback callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                        + "&cid=" + cid
                        + "&fnval=16&fnver=0&fourk=0&platform=html5&high_quality=1";
                String response = httpGet(url, cookie);
                JSONObject json = new JSONObject(response);

                int code = json.optInt("code", -1);
                if (code != 0) {
                    String msg = json.optString("message", "Unknown error");
                    postError(callback, msg);
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                String audioUrl = null;

                // Try dash.audio first
                JSONObject dash = data.optJSONObject("dash");
                if (dash != null) {
                    JSONArray audioArray = dash.optJSONArray("audio");
                    if (audioArray != null && audioArray.length() > 0) {
                        JSONObject firstAudio = audioArray.getJSONObject(0);
                        audioUrl = firstAudio.optString("baseUrl", null);
                        if (audioUrl == null || audioUrl.isEmpty()) {
                            audioUrl = firstAudio.optString("base_url", null);
                        }
                    }
                }

                // Fallback to durl
                if (audioUrl == null || audioUrl.isEmpty()) {
                    JSONArray durl = data.optJSONArray("durl");
                    if (durl != null && durl.length() > 0) {
                        audioUrl = durl.getJSONObject(0).optString("url", null);
                    }
                }

                if (audioUrl == null || audioUrl.isEmpty()) {
                    postError(callback, "No audio stream found");
                    return;
                }

                final String resultUrl = audioUrl;
                mainHandler.post(() -> callback.onResult(resultUrl));
            } catch (Exception e) {
                Log.e(TAG, "getAudioStreamUrl error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Fetch subtitle and convert to LRC format.
     */
    public static void getSubtitle(String subtitleUrl, SubtitleCallback callback) {
        executor.execute(() -> {
            try {
                // Subtitle URLs sometimes start with "//" without scheme
                String fullUrl = subtitleUrl;
                if (fullUrl.startsWith("//")) {
                    fullUrl = "https:" + fullUrl;
                }

                String response = httpGet(fullUrl, null);
                JSONObject json = new JSONObject(response);
                JSONArray body = json.optJSONArray("body");

                if (body == null || body.length() == 0) {
                    mainHandler.post(() -> callback.onResult(""));
                    return;
                }

                StringBuilder lrc = new StringBuilder();
                for (int i = 0; i < body.length(); i++) {
                    JSONObject item = body.getJSONObject(i);
                    double from = item.optDouble("from", 0);
                    String content = item.optString("content", "");
                    lrc.append(formatLrcTime(from)).append(content).append("\n");
                }

                final String lrcText = lrc.toString();
                mainHandler.post(() -> callback.onResult(lrcText));
            } catch (Exception e) {
                Log.e(TAG, "getSubtitle error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Generate QR code for Bilibili login.
     */
    public static void generateQrCode(QrCodeCallback callback) {
        executor.execute(() -> {
            try {
                String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";
                String response = httpGet(url, null);
                JSONObject json = new JSONObject(response);

                int code = json.optInt("code", -1);
                if (code != 0) {
                    String msg = json.optString("message", "Unknown error");
                    postError(callback, msg);
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                String qrUrl = data.optString("url", "");
                String qrcodeKey = data.optString("qrcode_key", "");

                mainHandler.post(() -> callback.onResult(qrUrl, qrcodeKey));
            } catch (Exception e) {
                Log.e(TAG, "generateQrCode error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Poll QR login status.
     */
    public static void pollQrLogin(String qrcodeKey, QrPollCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key="
                        + qrcodeKey;
                conn = createConnection(url, null);
                conn.setRequestMethod("GET");
                conn.connect();

                int httpCode = conn.getResponseCode();
                if (httpCode != HttpURLConnection.HTTP_OK) {
                    postError(callback, "HTTP " + httpCode);
                    return;
                }

                String response = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(response);

                int apiCode = json.optInt("code", -1);
                if (apiCode != 0) {
                    String msg = json.optString("message", "Unknown error");
                    postError(callback, msg);
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                int loginCode = data.optInt("code", -1);
                String message = data.optString("message", "");

                String cookie = "";
                if (loginCode == 0) {
                    // Extract cookies from Set-Cookie headers
                    StringBuilder cookieBuilder = new StringBuilder();
                    Map<String, List<String>> headers = conn.getHeaderFields();
                    List<String> setCookies = headers.get("Set-Cookie");
                    if (setCookies != null) {
                        for (String sc : setCookies) {
                            // Take just the key=value part before the first ";"
                            String kv = sc.split(";")[0].trim();
                            if (cookieBuilder.length() > 0) {
                                cookieBuilder.append("; ");
                            }
                            cookieBuilder.append(kv);
                        }
                    }

                    // Also try to extract from data.url query params as fallback
                    String dataUrl = data.optString("url", "");
                    if (!dataUrl.isEmpty() && cookieBuilder.toString().isEmpty()) {
                        cookie = extractCookiesFromUrl(dataUrl);
                    } else {
                        cookie = cookieBuilder.toString();
                    }

                    // If Set-Cookie headers are available but also ensure key params exist
                    if (!cookie.isEmpty() && !dataUrl.isEmpty()) {
                        String urlCookies = extractCookiesFromUrl(dataUrl);
                        if (!urlCookies.isEmpty() && !cookie.contains("SESSDATA")) {
                            cookie = urlCookies;
                        }
                    }
                }

                final int resultCode = loginCode;
                final String resultMsg = message;
                final String resultCookie = cookie;
                mainHandler.post(() -> callback.onResult(resultCode, resultMsg, resultCookie));
            } catch (Exception e) {
                Log.e(TAG, "pollQrLogin error", e);
                postError(callback, e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Get subtitle list for a video page.
     */
    public static void getSubtitleList(String bvid, long cid, String cookie,
                                       SubtitleListCallback callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/player/wbi/v2?bvid=" + bvid
                        + "&cid=" + cid;
                String response = httpGet(url, cookie);
                JSONObject json = new JSONObject(response);

                int code = json.optInt("code", -1);
                if (code != 0) {
                    String msg = json.optString("message", "Unknown error");
                    postError(callback, msg);
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                JSONObject subtitle = data.optJSONObject("subtitle");
                List<SubtitleInfo> subtitles = new ArrayList<>();

                if (subtitle != null) {
                    JSONArray subtitlesArray = subtitle.optJSONArray("subtitles");
                    if (subtitlesArray != null) {
                        for (int i = 0; i < subtitlesArray.length(); i++) {
                            JSONObject s = subtitlesArray.getJSONObject(i);
                            String lan = s.optString("lan", "");
                            String lanDoc = s.optString("lan_doc", "");
                            String subUrl = s.optString("subtitle_url", "");
                            subtitles.add(new SubtitleInfo(lan, lanDoc, subUrl));
                        }
                    }
                }

                mainHandler.post(() -> callback.onResult(subtitles));
            } catch (Exception e) {
                Log.e(TAG, "getSubtitleList error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void searchVideos(String keyword, String cookie, SearchVideosCallback callback) {
        executor.execute(() -> {
            try {
                String normalizedKeyword = keyword != null ? keyword.trim() : "";
                if (normalizedKeyword.isEmpty()) {
                    postError(callback, "搜索关键词不能为空或仅包含空格");
                    return;
                }
                String requestCookie = ensureSearchCookie(cookie);
                String url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video"
                        + "&keyword=" + URLEncoder.encode(normalizedKeyword, "UTF-8");
                String response = httpGet(url, requestCookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code != 0) {
                    postError(callback, json.optString("message", "搜索失败"));
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                JSONArray resultArray = data != null ? data.optJSONArray("result") : null;
                List<BilibiliSearchVideo> videos = new ArrayList<>();
                if (resultArray != null) {
                    for (int i = 0; i < resultArray.length(); i++) {
                        JSONObject item = resultArray.getJSONObject(i);
                        videos.add(new BilibiliSearchVideo(
                                item.optLong("aid", 0),
                                item.optString("bvid", ""),
                                stripHtml(item.optString("title", "")),
                                stripHtml(item.optString("author", "")),
                                stripHtml(item.optString("description", "")),
                                parseDurationTextToSeconds(item.optString("duration", "0:00"))
                        ));
                    }
                }
                mainHandler.post(() -> callback.onResult(videos));
            } catch (Exception e) {
                Log.e(TAG, "searchVideos error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void getFavoriteFolders(String cookie, FavoriteFoldersCallback callback) {
        executor.execute(() -> {
            try {
                long mid = extractDedeUserId(cookie);
                if (mid <= 0) {
                    postError(callback, "未获取到B站账号信息");
                    return;
                }
                String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&up_mid=" + mid;
                String response = httpGet(url, cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code != 0) {
                    postError(callback, json.optString("message", "加载收藏夹失败"));
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                JSONArray listArray = data != null ? data.optJSONArray("list") : null;
                List<BilibiliFavoriteFolder> folders = new ArrayList<>();
                if (listArray != null) {
                    for (int i = 0; i < listArray.length(); i++) {
                        JSONObject item = listArray.getJSONObject(i);
                        folders.add(new BilibiliFavoriteFolder(
                                item.optLong("id", 0),
                                item.optString("title", ""),
                                item.optInt("media_count", 0)
                        ));
                    }
                }
                mainHandler.post(() -> callback.onResult(folders));
            } catch (Exception e) {
                Log.e(TAG, "getFavoriteFolders error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    public static void getFavoriteFolderVideos(long mediaId, String cookie,
                                               FavoriteFolderVideosCallback callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + mediaId
                        + "&pn=1&ps=20&platform=web";
                String response = httpGet(url, cookie);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code != 0) {
                    postError(callback, json.optString("message", "加载收藏夹视频失败"));
                    return;
                }

                JSONObject data = json.optJSONObject("data");
                JSONObject info = data != null ? data.optJSONObject("info") : null;
                String folderTitle = info != null ? info.optString("title", "") : "";
                JSONArray medias = data != null ? data.optJSONArray("medias") : null;
                List<BilibiliSearchVideo> videos = new ArrayList<>();
                if (medias != null) {
                    for (int i = 0; i < medias.length(); i++) {
                        JSONObject item = medias.getJSONObject(i);
                        JSONObject upper = item.optJSONObject("upper");
                        videos.add(new BilibiliSearchVideo(
                                item.optLong("id", 0),
                                item.optString("bvid", ""),
                                item.optString("title", ""),
                                upper != null ? upper.optString("name", "") : item.optString("upper", ""),
                                item.optString("intro", ""),
                                item.optInt("duration", 0)
                        ));
                    }
                }
                mainHandler.post(() -> callback.onResult(folderTitle, videos));
            } catch (Exception e) {
                Log.e(TAG, "getFavoriteFolderVideos error", e);
                postError(callback, e.getMessage());
            }
        });
    }

    // ======================== Helper Methods ========================

    private static String httpGet(String urlStr, String cookie) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = createConnection(urlStr, cookie);
            conn.setRequestMethod("GET");
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                // Try reading error stream
                InputStream errStream = conn.getErrorStream();
                String errBody = errStream != null ? readStream(errStream) : "";
                throw new Exception("HTTP " + code + ": " + errBody);
            }

            return readStream(conn.getInputStream());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection createConnection(String urlStr, String cookie)
            throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", REFERER);
        conn.setInstanceFollowRedirects(true);
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn;
    }

    private static String readStream(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String formatLrcTime(double seconds) {
        int totalMs = (int) (seconds * 1000);
        int min = totalMs / 60000;
        int sec = (totalMs % 60000) / 1000;
        int ms = (totalMs % 1000) / 10; // LRC uses centiseconds
        return String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, ms);
    }

    private static String extractCookiesFromUrl(String urlStr) {
        try {
            String query = urlStr.contains("?") ? urlStr.substring(urlStr.indexOf("?") + 1) : "";
            String sessdata = "";
            String biliJct = "";
            String dedeUserId = "";
            String dedeUserIdCkMd5 = "";

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0];
                String value = URLDecoder.decode(kv[1], "UTF-8");
                switch (key) {
                    case "SESSDATA":
                        sessdata = value;
                        break;
                    case "bili_jct":
                        biliJct = value;
                        break;
                    case "DedeUserID":
                        dedeUserId = value;
                        break;
                    case "DedeUserID__ckMd5":
                        dedeUserIdCkMd5 = value;
                        break;
                }
            }

            if (!sessdata.isEmpty()) {
                return "SESSDATA=" + sessdata
                        + "; bili_jct=" + biliJct
                        + "; DedeUserID=" + dedeUserId
                        + "; DedeUserID__ckMd5=" + dedeUserIdCkMd5;
            }
        } catch (Exception e) {
            Log.e(TAG, "extractCookiesFromUrl error", e);
        }
        return "";
    }

    private static long extractDedeUserId(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return 0;
        }
        try {
            String[] parts = cookie.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith("DedeUserID=")) {
                    return Long.parseLong(trimmed.substring("DedeUserID=".length()));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "extractDedeUserId error", e);
        }
        return 0;
    }

    private static String ensureSearchCookie(String cookie) throws Exception {
        if (cookie != null && !cookie.isEmpty() && cookie.contains("buvid3")) {
            return cookie;
        }
        HttpURLConnection conn = null;
        try {
            conn = createConnection("https://www.bilibili.com", cookie);
            conn.setRequestMethod("GET");
            conn.connect();
            Map<String, List<String>> headers = conn.getHeaderFields();
            List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies == null || setCookies.isEmpty()) {
                return cookie;
            }
            StringBuilder builder = new StringBuilder(cookie != null ? cookie.trim() : "");
            for (String rawCookie : setCookies) {
                String kv = rawCookie.split(";", 2)[0].trim();
                if (kv.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0 && !containsCookie(builder.toString(), kv)) {
                    builder.append("; ");
                }
                if (!containsCookie(builder.toString(), kv)) {
                    builder.append(kv);
                }
            }
            return builder.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static boolean containsCookie(String cookie, String keyValue) {
        if (cookie == null || cookie.isEmpty() || keyValue == null || keyValue.isEmpty()) {
            return false;
        }
        String key = keyValue.split("=", 2)[0];
        String[] parts = cookie.split(";");
        for (String part : parts) {
            if (part.trim().startsWith(key + "=")) {
                return true;
            }
        }
        return false;
    }

    private static String stripHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean insideTag = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                insideTag = true;
                continue;
            }
            if (ch == '>') {
                insideTag = false;
                continue;
            }
            if (!insideTag) {
                builder.append(ch);
            }
        }
        return builder.toString().replace("&amp;", "&").replace("&quot;", "\"");
    }

    private static int parseDurationTextToSeconds(String durationText) {
        if (durationText == null || durationText.isEmpty()) {
            return 0;
        }
        try {
            String[] parts = durationText.split(":");
            int total = 0;
            for (String part : parts) {
                total = total * 60 + Integer.parseInt(part.trim());
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    // Overloaded postError for each callback type
    private static void postError(VideoInfoCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(AudioStreamCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(SubtitleCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(QrCodeCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(QrPollCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(SubtitleListCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(SearchVideosCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(FavoriteFoldersCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }

    private static void postError(FavoriteFolderVideosCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg != null ? msg : "Unknown error"));
    }
}
