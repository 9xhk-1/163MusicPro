package com.watch.music163;

import android.os.Handler;
import android.os.Looper;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MusicApiHelper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://music.163.com";

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

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

    public static void searchSongs(String keyword, String cookie, SearchCallback callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(keyword, "UTF-8");
                String apiUrl = "https://music.163.com/api/search/get/web?s=" + encoded
                        + "&type=1&offset=0&total=true&limit=20";
                String response = httpGet(apiUrl, cookie);
                JSONObject json = new JSONObject(response);
                JSONObject result = json.optJSONObject("result");
                List<Song> songs = new ArrayList<>();
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
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public static void getSongUrl(long songId, String cookie, UrlCallback callback) {
        executor.execute(() -> {
            try {
                String url = null;

                // Try VIP endpoint with POST if cookie is available
                if (cookie != null && !cookie.isEmpty()) {
                    url = fetchSongUrlWithPost(songId, cookie, "exhigh");
                    if (url == null) {
                        url = fetchSongUrlWithPost(songId, cookie, "standard");
                    }
                }

                // Fallback to free endpoint
                if (url == null) {
                    String apiUrl = "https://music.163.com/api/song/enhance/player/url?ids=["
                            + songId + "]&br=320000";
                    String response = httpGet(apiUrl, cookie);
                    url = extractUrlFromResponse(response);
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

    private static String fetchSongUrlWithPost(long songId, String cookie, String level) {
        try {
            String csrfToken = extractCsrfToken(cookie);
            String apiUrl = "https://music.163.com/api/song/enhance/player/url/v1?csrf_token=" + csrfToken;
            String body = "ids=[" + songId + "]&level=" + level + "&encodeType=mp3&csrf_token=" + csrfToken;
            String response = httpPost(apiUrl, body, cookie);
            return extractUrlFromResponse(response);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractUrlFromResponse(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        JSONArray data = json.optJSONArray("data");
        if (data != null && data.length() > 0) {
            JSONObject first = data.getJSONObject(0);
            String url = first.optString("url", null);
            if (url != null && !"null".equals(url) && !url.isEmpty()) {
                return url;
            }
        }
        return null;
    }

    private static String extractCsrfToken(String cookie) {
        if (cookie == null) return "";
        Matcher matcher = Pattern.compile("__csrf=([^;\\s]+)").matcher(cookie);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

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
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
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

    private static String httpPost(String urlStr, String body, String cookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", REFERER);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        try {
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
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
