package com.qinghe.music163pro.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayerManager {

    private static final String TAG = "MusicPlayer";
    private static final String PREFS_NAME = "music163_playback_state";
    private static final String KEY_CURRENT_SONG_JSON = "current_song_json";
    private static final String KEY_PLAYLIST_JSON = "playlist_json";
    private static final String KEY_CURRENT_INDEX = "current_index";

    public enum PlayMode {
        LIST_LOOP,      // 列表循环
        SINGLE_REPEAT,  // 单曲循环
        RANDOM          // 随机播放
    }

    public interface PlayerCallback {
        void onSongChanged(Song song);
        void onPlayStateChanged(boolean isPlaying);
        void onError(String message);
    }

    private static MusicPlayerManager instance;
    private MediaPlayer mediaPlayer;
    private final List<Song> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private PlayerCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PlayMode playMode = PlayMode.LIST_LOOP;
    private final Random random = new Random();
    private long currentlyPlayingSongId = -1;
    private Context appContext;

    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setCallback(PlayerCallback callback) {
        this.callback = callback;
    }

    public void setPlaylist(List<Song> songs, int startIndex) {
        playlist.clear();
        playlist.addAll(songs);
        currentIndex = startIndex;
        savePlaybackState();
    }

    public List<Song> getPlaylist() {
        return playlist;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlayMode(PlayMode mode) {
        this.playMode = mode;
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    public void play(String url) {
        stop();
        mediaPlayer = new MediaPlayer();
        try {
            if (appContext != null) {
                mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            }
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlaying = true;
                notifyPlayStateChanged(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                isPlaying = false;
                notifyPlayStateChanged(false);
                Song song = getCurrentSong();
                if (song != null) {
                    song.setUrl(null);
                }
                // Retry without cached URL
                if (song != null) {
                    String cookie = getCookie();
                    MusicApiHelper.getSongUrl(song.getId(), cookie, false,
                            new MusicApiHelper.UrlCallback() {
                                @Override
                                public void onResult(String retryUrl) {
                                    if (retryUrl != null) {
                                        song.setUrl(retryUrl);
                                        play(retryUrl);
                                    } else if (callback != null) {
                                        mainHandler.post(() -> callback.onError(
                                                "播放错误: " + what));
                                    }
                                }

                                @Override
                                public void onError(String message) {
                                    if (callback != null) {
                                        mainHandler.post(() -> callback.onError(
                                                "播放错误: " + what));
                                    }
                                }
                            });
                } else if (callback != null) {
                    mainHandler.post(() -> callback.onError("播放错误: " + what));
                }
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            notifyPlayStateChanged(false);
        }
    }

    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            notifyPlayStateChanged(true);
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping player", e);
            }
            mediaPlayer = null;
            isPlaying = false;
            currentlyPlayingSongId = -1;
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(positionMs);
            } catch (Exception e) {
                Log.w(TAG, "Error seeking", e);
            }
        }
    }

    /**
     * Called when current song finishes playing.
     * Behavior depends on play mode.
     */
    private void onSongCompleted() {
        isPlaying = false;
        notifyPlayStateChanged(false);
        if (playlist.isEmpty()) return;
        switch (playMode) {
            case SINGLE_REPEAT:
                // Replay current song
                playCurrent();
                break;
            case RANDOM:
                // Pick a random song
                if (playlist.size() > 1) {
                    int newIndex;
                    do {
                        newIndex = random.nextInt(playlist.size());
                    } while (newIndex == currentIndex);
                    currentIndex = newIndex;
                }
                playCurrent();
                break;
            case LIST_LOOP:
            default:
                currentIndex = (currentIndex + 1) % playlist.size();
                playCurrent();
                break;
        }
    }

    public void next() {
        if (playlist.isEmpty()) return;
        if (playMode == PlayMode.RANDOM) {
            if (playlist.size() > 1) {
                int newIndex;
                do {
                    newIndex = random.nextInt(playlist.size());
                } while (newIndex == currentIndex);
                currentIndex = newIndex;
            }
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
        }
        playCurrent();
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        if (playMode == PlayMode.RANDOM) {
            if (playlist.size() > 1) {
                int newIndex;
                do {
                    newIndex = random.nextInt(playlist.size());
                } while (newIndex == currentIndex);
                currentIndex = newIndex;
            }
        } else {
            currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        }
        playCurrent();
    }

    public void playCurrent() {
        Song song = getCurrentSong();
        if (song == null) return;

        notifySongChanged(song);
        savePlaybackState();

        // For local files (downloaded songs with a local file path),
        // play directly without fetching URL from the API.
        // This covers both legacy (id=0) and new format (real id with local path).
        String url = song.getUrl();
        if (url != null && !url.isEmpty() && url.startsWith("/")) {
            currentlyPlayingSongId = song.getId();
            play(url);
            return;
        }

        // Always fetch a fresh URL to avoid expired URL issues.
        // NetEase song URLs are time-limited, so cached URLs may not work.
        String cookie = getCookie();
        MusicApiHelper.getSongUrl(song.getId(), cookie, new MusicApiHelper.UrlCallback() {
            @Override
            public void onResult(String freshUrl) {
                song.setUrl(freshUrl);
                currentlyPlayingSongId = song.getId();
                play(freshUrl);
            }

            @Override
            public void onError(String message) {
                // Fallback: try cached URL if available
                if (song.getUrl() != null && !song.getUrl().isEmpty()) {
                    currentlyPlayingSongId = song.getId();
                    play(song.getUrl());
                } else if (callback != null) {
                    mainHandler.post(() -> callback.onError("无法获取歌曲链接: " + message));
                }
            }
        });
    }

    private String cookieValue = "";

    public void setCookie(String cookie) {
        this.cookieValue = cookie != null ? cookie : "";
    }

    public String getCookie() {
        return cookieValue;
    }

    private void notifySongChanged(Song song) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSongChanged(song));
        }
    }

    private void notifyPlayStateChanged(boolean playing) {
        if (callback != null) {
            mainHandler.post(() -> callback.onPlayStateChanged(playing));
        }
    }

    // ==================== Sleep Timer ====================

    private long sleepTimerEndMs = 0;
    private Runnable sleepTimerRunnable;

    /**
     * Start sleep timer. Stops playback after the specified number of minutes.
     * @param minutes number of minutes until auto-stop
     */
    public void startSleepTimer(int minutes) {
        startSleepTimerSeconds(minutes * 60);
    }

    /**
     * Start sleep timer. Stops playback after the specified number of seconds.
     * @param seconds number of seconds until auto-stop
     */
    public void startSleepTimerSeconds(int seconds) {
        cancelSleepTimer();
        long delayMs = (long) seconds * 1000;
        sleepTimerEndMs = System.currentTimeMillis() + delayMs;
        sleepTimerRunnable = () -> {
            pause();
            sleepTimerEndMs = 0;
        };
        mainHandler.postDelayed(sleepTimerRunnable, delayMs);
    }

    /**
     * Cancel an active sleep timer.
     */
    public void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            mainHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        sleepTimerEndMs = 0;
    }

    /**
     * Check if a sleep timer is active.
     */
    public boolean isSleepTimerActive() {
        return sleepTimerEndMs > 0 && System.currentTimeMillis() < sleepTimerEndMs;
    }

    /**
     * Get remaining milliseconds on the sleep timer.
     * @return remaining ms, or 0 if no timer is active
     */
    public long getSleepTimerRemainingMs() {
        if (sleepTimerEndMs > 0) {
            long remaining = sleepTimerEndMs - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
        return 0;
    }

    // ==================== Save / Restore Playback State ====================

    /**
     * Save current song and playlist to SharedPreferences for restore on next launch.
     */
    public void savePlaybackState() {
        if (appContext == null) return;
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            Song current = getCurrentSong();
            if (current != null) {
                JSONObject songJson = new JSONObject();
                songJson.put("id", current.getId());
                songJson.put("name", current.getName());
                songJson.put("artist", current.getArtist());
                songJson.put("album", current.getAlbum());
                editor.putString(KEY_CURRENT_SONG_JSON, songJson.toString());
            } else {
                editor.remove(KEY_CURRENT_SONG_JSON);
            }

            // Save playlist
            JSONArray playlistArr = new JSONArray();
            for (Song s : playlist) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("name", s.getName());
                obj.put("artist", s.getArtist());
                obj.put("album", s.getAlbum());
                playlistArr.put(obj);
            }
            editor.putString(KEY_PLAYLIST_JSON, playlistArr.toString());
            editor.putInt(KEY_CURRENT_INDEX, currentIndex);
            editor.apply();
        } catch (Exception e) {
            Log.w(TAG, "Error saving playback state", e);
        }
    }

    /**
     * Restore playlist and current song from SharedPreferences.
     * Does NOT start playback - just restores state for UI display.
     * @return true if state was restored successfully
     */
    public boolean restorePlaybackState() {
        if (appContext == null) return false;
        // Don't restore if we already have a playlist loaded
        if (!playlist.isEmpty()) return false;
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String playlistJson = prefs.getString(KEY_PLAYLIST_JSON, "[]");
            int savedIndex = prefs.getInt(KEY_CURRENT_INDEX, -1);

            JSONArray arr = new JSONArray(playlistJson);
            if (arr.length() == 0) return false;

            List<Song> restoredList = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Song song = new Song(
                        obj.getLong("id"),
                        obj.optString("name", ""),
                        obj.optString("artist", ""),
                        obj.optString("album", "")
                );
                restoredList.add(song);
            }

            playlist.clear();
            playlist.addAll(restoredList);
            if (savedIndex >= 0 && savedIndex < playlist.size()) {
                currentIndex = savedIndex;
            } else {
                currentIndex = 0;
            }

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error restoring playback state", e);
            return false;
        }
    }
}
