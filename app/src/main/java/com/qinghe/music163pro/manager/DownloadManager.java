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
 * Each song is saved in its own subfolder with info.json.
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static final String DOWNLOAD_DIR = "163Music/Download";
    private static final String INFO_FILE = "info.json";
    private static final String SONGS_DIR = "songs";
    private static final String SONG_FILE_MP3 = "song.mp3";
    private static final String SONG_FILE_FLAC = "song.flac";
    /** Supported audio file names, checked in priority order when looking up downloads. */
    private static final String[] AUDIO_FILE_NAMES = {SONG_FILE_MP3, SONG_FILE_FLAC};
    /** Legacy constant kept for backward-compatibility checks. */
    private static final String SONG_FILE = SONG_FILE_MP3;
    private static final String LYRICS_FILE = "lyrics.lrc";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final class LocalTrackInfo {
        final String quality;
        final File audioFile;

        LocalTrackInfo(String quality, File audioFile) {
            this.quality = quality;
            this.audioFile = audioFile;
        }
    }

    public interface DownloadCallback {
        void onSuccess(String filePath);
        void onError(String message);
    }

    /**
     * Download a song to /sdcard/163Music/Download/<folder>/
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
                    if (isDownloaded(song)) {
                        mainHandler.post(() -> callback.onError("歌曲已下载"));
                        return;
                    }
                    BilibiliApiHelper.getAudioStreamUrl(song.getBvid(), song.getCid(), cookie,
                            new BilibiliApiHelper.AudioStreamCallback() {
                                @Override
                                public void onResult(String url) {
                                    executor.execute(() -> doDownload(song, url, null, true, callback));
                                }

                                @Override
                                public void onError(String message) {
                                    mainHandler.post(() -> callback.onError("获取下载链接失败: " + message));
                                }
                            });
                    return;
                }
                if (hasDownloadedQuality(song, quality)) {
                    mainHandler.post(() -> callback.onError("该音质已下载"));
                    return;
                }
                MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, quality,
                        new MusicApiHelper.UrlCallback() {
                            @Override
                            public void onResult(String url) {
                                executor.execute(() -> doDownload(song, url, quality, false, callback));
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

    private static void doDownload(Song song, String urlStr, String quality, boolean bilibili,
                                   DownloadCallback callback) {
        try {
            File songDir = getSongDir(song);
            if (!songDir.exists() && !songDir.mkdirs()) {
                mainHandler.post(() -> callback.onError("无法创建下载目录"));
                return;
            }

            File outputParent = bilibili ? songDir : getQualityDir(songDir, quality);
            if (!outputParent.exists() && !outputParent.mkdirs()) {
                mainHandler.post(() -> callback.onError("无法创建音质目录"));
                return;
            }
            if (!bilibili && findAudioFileInDir(outputParent) != null) {
                mainHandler.post(() -> callback.onError("该音质已下载"));
                return;
            }

            String audioFileName = getAudioFileNameFromUrl(urlStr);
            File outputFile = new File(outputParent, audioFileName);

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

                saveSongInfo(songDir, song);
                if (!bilibili) {
                    downloadLyrics(songDir, song);
                }

                long now = System.currentTimeMillis();
                outputFile.setLastModified(now);
                outputParent.setLastModified(now);
                songDir.setLastModified(now);

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

    private static File getQualityDir(File songDir, String quality) {
        String safeQuality = TextUtils.isEmpty(quality) ? "exhigh" : sanitizeFileName(quality);
        return new File(new File(songDir, SONGS_DIR), safeQuality);
    }

    private static List<LocalTrackInfo> getStructuredTracks(File songDir) {
        List<LocalTrackInfo> tracks = new ArrayList<>();
        if (!isManagedDownloadDir(songDir)) {
            return tracks;
        }
        File songsDir = new File(songDir, SONGS_DIR);
        File[] qualityDirs = songsDir.listFiles();
        if (qualityDirs == null) {
            return tracks;
        }
        for (File qualityDir : qualityDirs) {
            if (qualityDir == null || !qualityDir.isDirectory()) {
                continue;
            }
            File audioFile = findAudioFileInDir(qualityDir);
            if (audioFile != null) {
                tracks.add(new LocalTrackInfo(qualityDir.getName(), audioFile));
            }
        }
        tracks.sort((left, right) -> {
            int rankCompare = Integer.compare(
                    MusicApiHelper.qualityLevelRank(right.quality),
                    MusicApiHelper.qualityLevelRank(left.quality));
            if (rankCompare != 0) {
                return rankCompare;
            }
            return Long.compare(right.audioFile.lastModified(), left.audioFile.lastModified());
        });
        return tracks;
    }

    private static LocalTrackInfo getBestStructuredTrack(File songDir) {
        List<LocalTrackInfo> tracks = getStructuredTracks(songDir);
        return tracks.isEmpty() ? null : tracks.get(0);
    }

    private static LocalTrackInfo getTrackForQuality(File songDir, String quality) {
        if (TextUtils.isEmpty(quality)) {
            return null;
        }
        for (LocalTrackInfo track : getStructuredTracks(songDir)) {
            if (quality.equals(track.quality)) {
                return track;
            }
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

    private static long getDownloadEntryModifiedTime(File songDir) {
        long latest = songDir.lastModified();
        File infoFile = new File(songDir, INFO_FILE);
        if (infoFile.exists()) {
            latest = Math.max(latest, infoFile.lastModified());
        }
        File rootAudio = findAudioFileInDir(songDir);
        if (rootAudio != null) {
            latest = Math.max(latest, rootAudio.lastModified());
        }
        for (LocalTrackInfo track : getStructuredTracks(songDir)) {
            latest = Math.max(latest, track.audioFile.lastModified());
        }
        return latest;
    }

    /**
     * Determine the audio file name for a download URL.
     * Returns {@link #SONG_FILE_FLAC} for FLAC streams; falls back to {@link #SONG_FILE_MP3}.
     */
    private static String getAudioFileNameFromUrl(String url) {
        if (url == null) return SONG_FILE_MP3;
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
            LocalTrackInfo bestTrack = getBestStructuredTrack(songDir);
            if (bestTrack != null) {
                song.setUrl(bestTrack.audioFile.getAbsolutePath());
                song.setLocalQuality(bestTrack.quality);
            } else {
                File audioFile = findAudioFileInDir(songDir);
                if (audioFile != null) {
                    song.setUrl(audioFile.getAbsolutePath());
                    song.setLocalQuality(null);
                }
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
     */
    public static List<File> getDownloadedSongDirs() {
        List<File> dirs = new ArrayList<>();
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] listing = dir.listFiles();
            if (listing != null) {
                for (File f : listing) {
                    if (f.isDirectory() && (findAudioFileInDir(f) != null || getBestStructuredTrack(f) != null)) {
                        dirs.add(f);
                    }
                }
            }
        }
        dirs.sort((left, right) -> Long.compare(
                getDownloadEntryModifiedTime(right),
                getDownloadEntryModifiedTime(left)));
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
        files.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
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
        LocalTrackInfo bestTrack = getBestStructuredTrack(songDir);
        if (bestTrack != null) {
            song.setLocalQuality(bestTrack.quality);
            return bestTrack.audioFile.getAbsolutePath();
        }
        File audioFile = findAudioFileInDir(songDir);
        if (audioFile != null) {
            song.setLocalQuality(null);
            return audioFile.getAbsolutePath();
        }
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = safeName + " - " + safeArtist + ".mp3";
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        File legacy = new File(dir, fileName);
        if (legacy.exists()) {
            song.setLocalQuality(null);
            return legacy.getAbsolutePath();
        }
        return null;
    }

    public static String getDownloadedPathForQuality(Song song, String quality) {
        LocalTrackInfo track = getTrackForQuality(getSongDir(song), quality);
        return track != null ? track.audioFile.getAbsolutePath() : null;
    }

    public static boolean hasDownloadedQuality(Song song, String quality) {
        return getDownloadedPathForQuality(song, quality) != null;
    }

    public static List<String> getAvailableLocalQualities(Song song) {
        List<String> qualities = new ArrayList<>();
        for (LocalTrackInfo track : getStructuredTracks(getSongDir(song))) {
            qualities.add(track.quality);
        }
        return qualities;
    }

    public static String getBestDownloadedQuality(Song song) {
        LocalTrackInfo bestTrack = getBestStructuredTrack(getSongDir(song));
        return bestTrack != null ? bestTrack.quality : null;
    }

    public static String detectLocalQualityFromPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        String marker = "/" + SONGS_DIR + "/";
        int markerIndex = normalized.lastIndexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String remainder = normalized.substring(markerIndex + marker.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return remainder.substring(0, slashIndex);
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
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIR);
        File songDir = getSongDir(song);
        if (songDir.exists() && songDir.isDirectory()) {
            return deleteDir(songDir);
        }
        String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
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
