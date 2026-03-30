package com.qinghe.music163pro.manager;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages downloading songs to /sdcard/163Music/Download/
 * Each song is saved in its own subfolder with song.mp3 and info.json.
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static final String DOWNLOAD_DIR = "163Music/Download";
    private static final String INFO_FILE = "info.json";
    private static final String SONG_FILE = "song.mp3";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DownloadCallback {
        void onSuccess(String filePath);
        void onError(String message);
    }

    /**
     * Download a song to /sdcard/163Music/Download/<folder>/
     * Saves both song.mp3 and info.json (with song id, name, artist, album).
     */
    public static void downloadSong(Song song, String cookie, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                // First get a fresh URL
                MusicApiHelper.getSongUrl(song.getId(), cookie, new MusicApiHelper.UrlCallback() {
                    @Override
                    public void onResult(String url) {
                        executor.execute(() -> doDownload(song, url, callback));
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> callback.onError("获取下载链接失败: " + message));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
            }
        });
    }

    private static void doDownload(Song song, String urlStr, DownloadCallback callback) {
        try {
            File songDir = getSongDir(song);
            if (!songDir.exists()) {
                if (!songDir.mkdirs()) {
                    mainHandler.post(() -> callback.onError("无法创建下载目录"));
                    return;
                }
            }

            File outputFile = new File(songDir, SONG_FILE);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            try {
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(outputFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
                fos.close();
                is.close();

                // Save song info JSON
                saveSongInfo(songDir, song);

                String filePath = outputFile.getAbsolutePath();
                mainHandler.post(() -> callback.onSuccess(filePath));
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Download error", e);
            mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
        }
    }

    /**
     * Save song metadata to info.json in the song's download folder.
     */
    private static void saveSongInfo(File songDir, Song song) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", song.getId());
            obj.put("name", song.getName());
            obj.put("artist", song.getArtist());
            obj.put("album", song.getAlbum());

            File infoFile = new File(songDir, INFO_FILE);
            FileOutputStream fos = new FileOutputStream(infoFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(obj.toString(2));
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            Log.w(TAG, "Error saving song info", e);
        }
    }

    /**
     * Load song metadata from info.json in a download folder.
     * @return Song with real id, name, artist, album set; or null on failure
     */
    public static Song loadSongInfo(File songDir) {
        File infoFile = new File(songDir, INFO_FILE);
        if (!infoFile.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(infoFile);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();
            fis.close();

            JSONObject obj = new JSONObject(sb.toString());
            Song song = new Song(
                    obj.optLong("id", 0),
                    obj.optString("name", ""),
                    obj.optString("artist", ""),
                    obj.optString("album", "")
            );
            // Set URL to the local mp3 path
            File mp3 = new File(songDir, SONG_FILE);
            if (mp3.exists()) {
                song.setUrl(mp3.getAbsolutePath());
            }
            return song;
        } catch (Exception e) {
            Log.w(TAG, "Error loading song info from " + songDir, e);
            return null;
        }
    }

    /**
     * Get the subfolder for a song inside the download directory.
     */
    private static File getSongDir(Song song) {
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
        String folderName = safeName + " - " + safeArtist;
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        return new File(dir, folderName);
    }

    /**
     * Get list of downloaded song directories from /sdcard/163Music/Download/
     * Each directory should contain song.mp3 and info.json.
     * Also supports legacy flat .mp3 files for backward compatibility.
     */
    public static List<File> getDownloadedSongDirs() {
        List<File> dirs = new ArrayList<>();
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] listing = dir.listFiles();
            if (listing != null) {
                for (File f : listing) {
                    if (f.isDirectory() && new File(f, SONG_FILE).exists()) {
                        dirs.add(f);
                    }
                }
            }
        }
        return dirs;
    }

    /**
     * Get list of downloaded song files (legacy flat .mp3 files).
     * Kept for backward compatibility.
     */
    public static List<File> getDownloadedFiles() {
        List<File> files = new ArrayList<>();
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] listing = dir.listFiles();
            if (listing != null) {
                for (File f : listing) {
                    if (f.isFile() && f.getName().endsWith(".mp3")) {
                        files.add(f);
                    }
                }
            }
        }
        return files;
    }

    /**
     * Get the download directory path
     */
    public static String getDownloadDirPath() {
        return new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR).getAbsolutePath();
    }

    /**
     * Get the mp3 file path for a downloaded song.
     * Returns null if not downloaded.
     */
    public static String getDownloadedMp3Path(Song song) {
        File songDir = getSongDir(song);
        File mp3 = new File(songDir, SONG_FILE);
        if (mp3.exists()) return mp3.getAbsolutePath();
        // Legacy flat file fallback
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = safeName + " - " + safeArtist + ".mp3";
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        File legacy = new File(dir, fileName);
        if (legacy.exists()) return legacy.getAbsolutePath();
        return null;
    }

    /**
     * Check if a song is already downloaded
     */
    public static boolean isDownloaded(Song song) {
        return getDownloadedMp3Path(song) != null;
    }
}
