package com.qinghe.music163pro.manager;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.qinghe.music163pro.api.BilibiliApiHelper;
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
    private static final String SONG_FILE_MP3 = "song.mp3";
    private static final String SONG_FILE_FLAC = "song.flac";
    /** Supported audio file names, checked in priority order when looking up downloads. */
    private static final String[] AUDIO_FILE_NAMES = {SONG_FILE_MP3, SONG_FILE_FLAC};
    /** Legacy constant kept for backward-compatibility checks. */
    private static final String SONG_FILE = SONG_FILE_MP3;
    private static final String LYRICS_FILE = "lyrics.lrc";
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
        downloadSong(song, cookie, "exhigh", callback);
    }

    /**
     * Download a song with the specified audio quality level.
     * Quality: standard / higher / exhigh / lossless / hires / jyeffect / sky / jymaster
     * Falls back to best free quality if requested level is unavailable.
     */
    public static void downloadSong(Song song, String cookie, String quality,
                                    DownloadCallback callback) {
        executor.execute(() -> {
            try {
                if (song.isBilibili()) {
                    BilibiliApiHelper.getAudioStreamUrl(song.getBvid(), song.getCid(), cookie,
                            new BilibiliApiHelper.AudioStreamCallback() {
                                @Override
                                public void onResult(String url) {
                                    executor.execute(() -> doDownload(song, url, true, callback));
                                }

                                @Override
                                public void onError(String message) {
                                    mainHandler.post(() -> callback.onError("获取下载链接失败: " + message));
                                }
                            });
                    return;
                }
                // Get URL with requested quality
                MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, quality,
                        new MusicApiHelper.UrlCallback() {
                    @Override
                    public void onResult(String url) {
                        executor.execute(() -> doDownload(song, url, false, callback));
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

    /**
     * Download a song to a specific output file (used for caching, e.g. ringtone).
     * Does NOT save info.json or lyrics. Calls callback with the output file path.
     */
    public static void downloadSongToFile(Song song, String cookie, String quality,
                                          File outputFile, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                if (song.isBilibili()) {
                    BilibiliApiHelper.getAudioStreamUrl(song.getBvid(), song.getCid(), cookie,
                            new BilibiliApiHelper.AudioStreamCallback() {
                                @Override
                                public void onResult(String url) {
                                    executor.execute(() -> doDownloadToFile(url, true, outputFile, callback));
                                }

                                @Override
                                public void onError(String message) {
                                    mainHandler.post(() -> callback.onError("获取链接失败: " + message));
                                }
                            });
                    return;
                }
                MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, quality,
                        new MusicApiHelper.UrlCallback() {
                    @Override
                    public void onResult(String url) {
                        executor.execute(() -> doDownloadToFile(url, false, outputFile, callback));
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> callback.onError("获取链接失败: " + message));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
            }
        });
    }

    private static void doDownloadToFile(String urlStr, boolean bilibili,
                                         File outputFile, DownloadCallback callback) {
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (bilibili) {
                conn.setRequestProperty("Referer", "https://www.bilibili.com");
            }

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

                String filePath = outputFile.getAbsolutePath();
                mainHandler.post(() -> callback.onSuccess(filePath));
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Download to file error", e);
            mainHandler.post(() -> callback.onError("下载失败: " + e.getMessage()));
        }
    }

    private static void doDownload(Song song, String urlStr, boolean bilibili,
                                   DownloadCallback callback) {
        try {
            File songDir = getSongDir(song);
            if (!songDir.exists()) {
                if (!songDir.mkdirs()) {
                    mainHandler.post(() -> callback.onError("无法创建下载目录"));
                    return;
                }
            }

            // Determine the correct audio file name from the URL (song.flac for FLAC, song.mp3 otherwise)
            String audioFileName = getAudioFileNameFromUrl(urlStr);
            File outputFile = new File(songDir, audioFileName);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (bilibili) {
                conn.setRequestProperty("Referer", "https://www.bilibili.com");
            }

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

                // Download lyrics
                if (!bilibili) {
                    downloadLyrics(songDir, song);
                }

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
            obj.put("source", song.getSource());
            obj.put("bvid", song.getBvid());
            obj.put("cid", song.getCid());

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
     * Download lyrics for a song and save as lyrics.lrc in the song's download folder.
     * Also downloads translated lyrics as tlyrics.lrc if available.
     */
    private static void downloadLyrics(File songDir, Song song) {
        if (song.getId() <= 0) return;
        try {
            String lrcText = MusicApiHelper.fetchLyricsSync(song.getId(), null);
            if (lrcText != null && !lrcText.isEmpty()) {
                File lrcFile = new File(songDir, LYRICS_FILE);
                try (FileOutputStream fos = new FileOutputStream(lrcFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    writer.write(lrcText);
                    writer.flush();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error downloading lyrics", e);
        }
        // Also download translated lyrics
        try {
            String tlyricText = MusicApiHelper.fetchTranslatedLyricsSync(song.getId(), null);
            if (tlyricText != null && !tlyricText.isEmpty()) {
                File tlyricFile = new File(songDir, "tlyrics.lrc");
                try (FileOutputStream fos = new FileOutputStream(tlyricFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    writer.write(tlyricText);
                    writer.flush();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error downloading translated lyrics", e);
        }
    }

    /**
     * Find the audio file inside a download subdirectory.
     * Checks for song.mp3, song.flac, etc. Returns null if none found.
     */
    private static File findAudioFileInDir(File songDir) {
        if (!isManagedDownloadDir(songDir)) {
            return null;
        }
        for (String name : AUDIO_FILE_NAMES) {
            File f = new File(songDir, name);
            if (f.exists()) return f;
        }
        return null;
    }

    private static boolean isManagedDownloadDir(File songDir) {
        if (songDir == null || !songDir.isDirectory()) {
            return false;
        }
        try {
            File downloadRoot = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
            String rootPath = downloadRoot.getCanonicalPath();
            String dirPath = songDir.getCanonicalPath();
            return dirPath.equals(rootPath) || dirPath.startsWith(rootPath + File.separator);
        } catch (Exception e) {
            Log.w(TAG, "Error validating download directory", e);
            return false;
        }
    }

    /**
     * Determine the audio file name for a download URL.
     * Returns {@link #SONG_FILE_FLAC} for FLAC streams; falls back to {@link #SONG_FILE_MP3}.
     */
    private static String getAudioFileNameFromUrl(String url) {
        if (url == null) return SONG_FILE_MP3;
        // Strip query string before checking extension
        String path = url;
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) path = path.substring(0, qIdx);
        if (path.toLowerCase().endsWith(".flac")) return SONG_FILE_FLAC;
        return SONG_FILE_MP3;
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
            song.setSource(obj.optString("source", null));
            song.setBvid(obj.optString("bvid", ""));
            song.setCid(obj.optLong("cid", 0));
            // Set URL to the local audio file path
            File audioFile = findAudioFileInDir(songDir);
            if (audioFile != null) {
                song.setUrl(audioFile.getAbsolutePath());
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
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        if (song.isBilibili()) {
            String safeVideoTitle = sanitizeFileName(
                    !TextUtils.isEmpty(song.getAlbum()) ? song.getAlbum() : song.getName());
            String safePartTitle = sanitizeFileName(
                    !TextUtils.isEmpty(song.getName()) ? song.getName() : song.getAlbum());
            String folderName = safeVideoTitle;
            if (!safePartTitle.isEmpty() && !safePartTitle.equals(safeVideoTitle)) {
                folderName = safeVideoTitle + "_" + safePartTitle;
            }
            if (folderName.isEmpty()) {
                folderName = "bilibili_audio_" + song.getCid();
            }
            return new File(dir, folderName);
        }
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
        String folderName = safeName + " - " + safeArtist;
        return new File(dir, folderName);
    }

    private static String sanitizeFileName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
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
                    if (f.isDirectory() && findAudioFileInDir(f) != null) {
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
        File audioFile = findAudioFileInDir(songDir);
        if (audioFile != null) return audioFile.getAbsolutePath();
        // Legacy flat file fallback (song.mp3 directly in the download root)
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

    /**
     * Delete a downloaded song (removes the entire song directory or legacy file).
     * @return true if deletion was successful
     */
    public static boolean deleteDownload(Song song) {
        // Try new subfolder format first
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
        String folderName = safeName + " - " + safeArtist;
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        File songDir = new File(dir, folderName);
        if (songDir.exists() && songDir.isDirectory()) {
            return deleteDir(songDir);
        }
        // Try legacy flat file
        String fileName = safeName + " - " + safeArtist + ".mp3";
        File legacy = new File(dir, fileName);
        if (legacy.exists()) {
            return legacy.delete();
        }
        return false;
    }

    /**
     * Recursively delete a directory and its contents.
     */
    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        return dir.delete();
    }
}
