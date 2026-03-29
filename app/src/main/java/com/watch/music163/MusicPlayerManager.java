package com.watch.music163;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayerManager {

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

    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    public void setCallback(PlayerCallback callback) {
        this.callback = callback;
    }

    public void setPlaylist(List<Song> songs, int startIndex) {
        playlist.clear();
        playlist.addAll(songs);
        currentIndex = startIndex;
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
                                    if (retryUrl != null && !retryUrl.equals(url)) {
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
                android.util.Log.w("MusicPlayer", "Error stopping player", e);
            }
            mediaPlayer = null;
            isPlaying = false;
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
                android.util.Log.w("MusicPlayer", "Error seeking", e);
            }
        }
    }

    /**
     * Called when current song finishes playing.
     * Behavior depends on play mode.
     */
    private void onSongCompleted() {
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
        if (song.getUrl() != null && !song.getUrl().isEmpty()) {
            play(song.getUrl());
        } else {
            // Need to fetch URL first
            String cookie = getCookie();
            MusicApiHelper.getSongUrl(song.getId(), cookie, new MusicApiHelper.UrlCallback() {
                @Override
                public void onResult(String url) {
                    song.setUrl(url);
                    play(url);
                }

                @Override
                public void onError(String message) {
                    if (callback != null) {
                        callback.onError("无法获取歌曲链接: " + message);
                    }
                }
            });
        }
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
}
