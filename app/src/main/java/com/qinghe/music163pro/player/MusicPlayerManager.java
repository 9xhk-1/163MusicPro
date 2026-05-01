package com.qinghe.music163pro.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.qinghe.music163pro.api.BilibiliApiHelper;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MusicPlayerManager {

    private static final String TAG = "MusicPlayer";
    private static final String PREFS_NAME = "music163_playback_state";
    private static final String KEY_CURRENT_SONG_JSON = "current_song_json";
    private static final String KEY_PLAYLIST_JSON = "playlist_json";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final String KEY_SOURCE_PLAYLIST_ID = "source_playlist_id";
    private static final String KEY_SOURCE_PLAYLIST_NAME = "source_playlist_name";
    private static final String KEY_SOURCE_PLAYLIST_TRACK_COUNT = "source_playlist_track_count";
    private static final String KEY_SOURCE_PLAYLIST_CREATOR = "source_playlist_creator";
    private static final String KEY_SOURCE_PLAYLIST_CREATOR_USER_ID = "source_playlist_creator_user_id";
    private static final String KEY_SOURCE_PLAYLIST_IS_LIKED = "source_playlist_is_liked";
    private static final String KEY_PERSONAL_FM_MODE = "personal_fm_mode";

    public enum PlayMode {
        LIST_LOOP,      // 列表循环
        SINGLE_REPEAT,  // 单曲循环
        RANDOM          // 随机播放
    }

    public interface PlayerCallback {
        void onSongChanged(Song song);
        void onPlayStateChanged(boolean isPlaying);
        void onError(String message);
        default void onSleepTimerTriggered(boolean exitApp) {}
    }

    private static MusicPlayerManager instance;
    private MediaPlayer mediaPlayer;
    private final List<Song> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private PlayerCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PlayMode playMode = PlayMode.LIST_LOOP;
    private float playbackSpeed = 1.0f;
    /**
     * Speed mode:
     *  0 = 音调不变 (time-stretch: speed changes, pitch preserved)
     *  1 = 音调改变且速度改变 (sample rate: both speed and pitch change)
     *  2 = 音调改变但速度不变 (pitch-shift only: pitch changes, speed stays 1.0)
     */
    private int speedMode = 0;
    private final Random random = new Random();
    private long currentlyPlayingSongId = -1;
    private Context appContext;
    private long bilibiliRetrySongId = -1;
    private int bilibiliRetryCount = 0;

    // ExoPlayer for Bilibili audio (better seek/buffering than MediaPlayer)
    private ExoPlayer exoPlayer;
    private boolean usingExoPlayer = false;

    // Bilibili CDN URL pre-refresh timer (URLs expire after ~30 min)
    private static final long BILIBILI_REFRESH_INTERVAL_MS = 25 * 60 * 1000; // 25 minutes
    private Runnable bilibiliRefreshRunnable;
    private String prefetchedBilibiliUrl;

    // Playlist source tracking: set when playing from a playlist
    private long sourcePlaylistId = -1;
    private String sourcePlaylistName;
    private int sourcePlaylistTrackCount;
    private String sourcePlaylistCreator;
    private long sourcePlaylistCreatorUserId;
    private boolean sourcePlaylistIsLiked;
    private boolean personalFmMode = false;
    private boolean personalFmLoading = false;

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
        clearSourcePlaylistInfo();
        personalFmMode = false;
        personalFmLoading = false;
        savePlaybackState();
    }

    /**
     * Set playlist with source playlist info (for tracking which playlist is being played).
     */
    public void setPlaylistFromSource(List<Song> songs, int startIndex,
                                       long playlistId, String playlistName,
                                       int trackCount, String creator,
                                       long creatorUserId, boolean isLiked) {
        playlist.clear();
        playlist.addAll(songs);
        currentIndex = startIndex;
        sourcePlaylistId = playlistId;
        sourcePlaylistName = playlistName;
        sourcePlaylistTrackCount = trackCount;
        sourcePlaylistCreator = creator;
        sourcePlaylistCreatorUserId = creatorUserId;
        sourcePlaylistIsLiked = isLiked;
        personalFmMode = false;
        personalFmLoading = false;
        savePlaybackState();
    }

    public void setPersonalFmPlaylist(List<Song> songs, int startIndex) {
        playlist.clear();
        playlist.addAll(songs);
        currentIndex = startIndex;
        clearSourcePlaylistInfo();
        personalFmMode = true;
        personalFmLoading = false;
        savePlaybackState();
    }

    public long getSourcePlaylistId() { return sourcePlaylistId; }
    public String getSourcePlaylistName() { return sourcePlaylistName; }
    public int getSourcePlaylistTrackCount() { return sourcePlaylistTrackCount; }
    public String getSourcePlaylistCreator() { return sourcePlaylistCreator; }
    public long getSourcePlaylistCreatorUserId() { return sourcePlaylistCreatorUserId; }
    public boolean getSourcePlaylistIsLiked() { return sourcePlaylistIsLiked; }

    public boolean hasSourcePlaylist() { return sourcePlaylistId > 0; }

    public boolean isPersonalFmMode() {
        return personalFmMode;
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

    /**
     * Set playback speed. Requires API 23+ (Marshmallow).
     * @param speed playback speed multiplier (0.1 - 5.0)
     */
    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        applyPlaybackSpeed();
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    /**
     * Set speed mode.
     * @param speedMode 0=音调不变, 1=音调改变且速度改变, 2=音调改变但速度不变
     */
    public void setSpeedMode(int speedMode) {
        this.speedMode = speedMode;
        applyPlaybackSpeed();
    }

    public int getSpeedMode() {
        return speedMode;
    }

    private void applyPlaybackSpeed() {
        // ExoPlayer speed control
        if (usingExoPlayer && exoPlayer != null) {
            try {
                float speed, pitch;
                if (speedMode == 1) {
                    speed = playbackSpeed;
                    pitch = playbackSpeed;
                } else if (speedMode == 2) {
                    speed = 1.0f;
                    pitch = playbackSpeed;
                } else {
                    speed = playbackSpeed;
                    pitch = 1.0f;
                }
                exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, pitch));
            } catch (Exception e) {
                Log.w(TAG, "Error setting ExoPlayer playback speed", e);
            }
            return;
        }
        // MediaPlayer speed control
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                boolean wasPlaying = mediaPlayer.isPlaying();
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                if (speedMode == 1) {
                    // 音调改变且速度改变: sample rate mode, both change
                    params.setSpeed(playbackSpeed);
                    params.setPitch(playbackSpeed);
                } else if (speedMode == 2) {
                    // 音调改变但速度不变: pitch shifts, tempo stays at 1.0
                    params.setSpeed(1.0f);
                    params.setPitch(playbackSpeed);
                } else {
                    // 音调不变 (mode 0): time-stretch, pitch preserved
                    params.setSpeed(playbackSpeed);
                    params.setPitch(1.0f);
                }
                mediaPlayer.setPlaybackParams(params);
                // Workaround: setPlaybackParams may auto-start a paused MediaPlayer
                if (!wasPlaying && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error setting playback speed", e);
            }
        }
    }

    public void play(String url) {
        play(url, 0, true);
    }

    private void play(String url, int resumePositionMs, boolean shouldPlayWhenReady) {
        stop();
        cancelBilibiliRefreshTimer();
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
                if (resumePositionMs > 0) {
                    try {
                        mp.seekTo(resumePositionMs);
                    } catch (Exception e) {
                        Log.w(TAG, "Error restoring playback position", e);
                    }
                }
                applyPlaybackSpeed();
                if (shouldPlayWhenReady) {
                    mp.start();
                    isPlaying = true;
                    notifyPlayStateChanged(true);
                } else {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                // what==-38 is ENOSYS ("function not implemented") fired asynchronously
                // by setPlaybackParams() on watch devices (Android 7-8.1) that lack
                // time-stretch support.  Playback is unaffected; silently ignore it.
                if (what == -38) return true;
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
                                        play(retryUrl, resumePositionMs, shouldPlayWhenReady);
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

    /**
     * Play a local file directly. Unlike play(), the error handler doesn't
     * try to fetch a URL from NetEase API, preventing unnecessary API calls
     * for downloaded songs (including Bilibili downloads).
     */
    private void playLocalFile(String localPath, Song song) {
        playLocalFile(localPath, song, 0, true);
    }

    private void playLocalFile(String localPath, Song song, int resumePositionMs,
                               boolean shouldPlayWhenReady) {
        stop();
        cancelBilibiliRefreshTimer();
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
            mediaPlayer.setDataSource(localPath);
            mediaPlayer.setOnPreparedListener(mp -> {
                if (resumePositionMs > 0) {
                    try {
                        mp.seekTo(resumePositionMs);
                    } catch (Exception e) {
                        Log.w(TAG, "Error restoring local playback position", e);
                    }
                }
                applyPlaybackSpeed();
                if (shouldPlayWhenReady) {
                    mp.start();
                    isPlaying = true;
                    notifyPlayStateChanged(true);
                } else {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> onSongCompleted());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                // what==-38 is ENOSYS ("function not implemented") fired asynchronously
                // by setPlaybackParams() on watch devices (Android 7-8.1) that lack
                // time-stretch support.  Playback is unaffected; silently ignore it.
                if (what == -38) return true;
                isPlaying = false;
                notifyPlayStateChanged(false);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("本地文件播放错误: " + what));
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
        if (usingExoPlayer && exoPlayer != null) {
            exoPlayer.pause();
            isPlaying = false;
            notifyPlayStateChanged(false);
        } else if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            notifyPlayStateChanged(false);
        }
    }

    public void resume() {
        if (usingExoPlayer && exoPlayer != null) {
            exoPlayer.play();
            isPlaying = true;
            notifyPlayStateChanged(true);
        } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            notifyPlayStateChanged(true);
        }
    }

    public void stop() {
        cancelBilibiliRefreshTimer();
        if (exoPlayer != null) {
            try {
                exoPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping ExoPlayer", e);
            }
            exoPlayer = null;
        }
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
        }
        isPlaying = false;
        currentlyPlayingSongId = -1;
        usingExoPlayer = false;
    }

    public int getCurrentPosition() {
        if (usingExoPlayer && exoPlayer != null) {
            try {
                return (int) exoPlayer.getCurrentPosition();
            } catch (Exception e) {
                return 0;
            }
        }
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
        if (usingExoPlayer && exoPlayer != null) {
            try {
                long dur = exoPlayer.getDuration();
                return dur == C.TIME_UNSET ? 0 : (int) dur;
            } catch (Exception e) {
                return 0;
            }
        }
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
        if (usingExoPlayer && exoPlayer != null) {
            try {
                exoPlayer.seekTo(positionMs);
            } catch (Exception e) {
                Log.w(TAG, "Error seeking ExoPlayer", e);
            }
        } else if (mediaPlayer != null) {
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
                playNextSequential();
                break;
        }
    }

    public void next() {
        if (playlist.isEmpty()) return;
        playNextSequential();
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playCurrent();
    }

    public void playFromCurrentPlaylist(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
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
            // Verify local file still exists before playing
            if (new File(url).exists()) {
                song.setLocalQuality(DownloadManager.detectLocalQualityFromPath(url));
                currentlyPlayingSongId = song.getId();
                playLocalFile(url, song);
                return;
            } else {
                song.setUrl(null);
            }
        }

        // Check if the song is downloaded locally (even if URL wasn't pre-set).
        // This avoids API calls for downloaded songs after app restart.
        String localPath = DownloadManager.getDownloadedMp3Path(song);
        if (localPath != null) {
            song.setUrl(localPath);
            song.setLocalQuality(DownloadManager.detectLocalQualityFromPath(localPath));
            currentlyPlayingSongId = song.getId();
            playLocalFile(localPath, song);
            return;
        }

        // Handle Bilibili songs (stream from network)
        if (song.isBilibili()) {
            playBilibiliSong(song);
            return;
        }

        // Always fetch a fresh URL to avoid expired URL issues.
        // NetEase song URLs are time-limited, so cached URLs may not work.
        String cookie = getCookie();
        // Read preferred quality from SharedPreferences
        String preferredQuality = "exhigh";
        if (appContext != null) {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                    "music163_settings", android.content.Context.MODE_PRIVATE);
            preferredQuality = prefs.getString("preferred_quality", "exhigh");
        }
        String qualityForLambda = preferredQuality;
        MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, qualityForLambda,
                new MusicApiHelper.UrlCallback() {
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

    /**
     * Play a Bilibili song by fetching the audio stream URL.
     * Bilibili audio URLs are time-limited, so always fetch fresh.
     */
    private void playBilibiliSong(Song song) {
        bilibiliRetrySongId = song.getId();
        bilibiliRetryCount = 0;
        String bilibiliCookie = getBilibiliCookie();
        BilibiliApiHelper.getAudioStreamUrl(song.getBvid(), song.getCid(), bilibiliCookie,
                new BilibiliApiHelper.AudioStreamCallback() {
                    @Override
                    public void onResult(String audioUrl) {
                        song.setUrl(audioUrl);
                        currentlyPlayingSongId = song.getId();
                        playWithHeaders(song, audioUrl, 0);
                    }

                    @Override
                    public void onError(String message) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("B站音频获取失败: " + message));
                        }
                    }
                });
    }

    /**
     * Play audio with Bilibili-specific headers (Referer required).
     */
    private void playWithHeaders(String url) {
        playWithHeaders(getCurrentSong(), url, 0);
    }

    /**
     * Play Bilibili audio using ExoPlayer with custom buffer settings.
     * ExoPlayer provides much better seek performance and buffering than
     * MediaPlayer for HTTP streaming, especially on watch devices with
     * limited resources - preventing stuttering at large seek positions.
     */
    @OptIn(markerClass = UnstableApi.class)
    private void playWithHeaders(Song song, String url, int resumePositionMs) {
        stop();
        cancelBilibiliRefreshTimer();
        prefetchedBilibiliUrl = null;
        usingExoPlayer = true;

        if (appContext == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("播放器上下文未初始化"));
            }
            return;
        }

        try {
            // Buffer settings optimized for watch devices:
            // - Larger buffers to prevent stuttering at far seek positions
            // - Generous rebuffer threshold to avoid repeated micro-buffers
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            30_000,   // minBufferMs: 30 seconds
                            120_000,  // maxBufferMs: 2 minutes
                            5_000,    // bufferForPlaybackMs: 5s before starting
                            15_000    // bufferForPlaybackAfterRebufferMs: 15s after rebuffer
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            // HTTP DataSource with Bilibili headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://www.bilibili.com");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(30_000)
                    .setAllowCrossProtocolRedirects(true);

            DefaultDataSource.Factory dataSourceFactory =
                    new DefaultDataSource.Factory(appContext, httpFactory);

            exoPlayer = new ExoPlayer.Builder(appContext)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                    .build();

            exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
            exoPlayer.setAudioAttributes(
                    new androidx.media3.common.AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                    true  // handleAudioFocus
            );

            final Song targetSong = song;
            exoPlayer.addListener(new Player.Listener() {
                private boolean prepared = false;

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && !prepared) {
                        prepared = true;
                        applyPlaybackSpeed();
                        isPlaying = true;
                        if (targetSong != null && targetSong.isBilibili()) {
                            bilibiliRetrySongId = targetSong.getId();
                            startBilibiliRefreshTimer(targetSong);
                        }
                        notifyPlayStateChanged(true);
                    } else if (state == Player.STATE_ENDED) {
                        isPlaying = false;
                        cancelBilibiliRefreshTimer();
                        onSongCompleted();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                    cancelBilibiliRefreshTimer();

                    Song currentSong = getCurrentSong();
                    // Allow up to 3 retries for Bilibili CDN URL expiration
                    if (currentSong != null
                            && currentSong.isBilibili()
                            && currentSong.getId() == bilibiliRetrySongId
                            && bilibiliRetryCount < 3) {
                        bilibiliRetryCount++;
                        int pos = getCurrentPosition();
                        // Use prefetched URL if available (faster recovery)
                        if (prefetchedBilibiliUrl != null) {
                            String freshUrl = prefetchedBilibiliUrl;
                            prefetchedBilibiliUrl = null;
                            currentSong.setUrl(freshUrl);
                            currentlyPlayingSongId = currentSong.getId();
                            playWithHeaders(currentSong, freshUrl, Math.max(pos, 0));
                        } else {
                            currentSong.setUrl(null);
                            retryBilibiliPlayback(currentSong, pos);
                        }
                        return;
                    }
                    if (callback != null) {
                        String errMsg = error.getMessage() != null ? error.getMessage() : "unknown";
                        mainHandler.post(() -> callback.onError("B站播放错误: " + errMsg));
                    }
                }
            });

            exoPlayer.setMediaItem(MediaItem.fromUri(url));
            exoPlayer.prepare();

            if (resumePositionMs > 0) {
                exoPlayer.seekTo(resumePositionMs);
            }
            exoPlayer.setPlayWhenReady(true);
        } catch (Exception e) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }
    }

    private void retryBilibiliPlayback(Song song, int resumePositionMs) {
        String bilibiliCookie = getBilibiliCookie();
        BilibiliApiHelper.getAudioStreamUrl(song.getBvid(), song.getCid(), bilibiliCookie,
                new BilibiliApiHelper.AudioStreamCallback() {
                    @Override
                    public void onResult(String audioUrl) {
                        song.setUrl(audioUrl);
                        currentlyPlayingSongId = song.getId();
                        playWithHeaders(song, audioUrl, Math.max(resumePositionMs, 0));
                    }

                    @Override
                    public void onError(String message) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("B站播放重连失败: " + message));
                        }
                    }
                });
    }

    /**
     * Start a periodic timer that pre-fetches a fresh Bilibili audio URL
     * before the current one expires (~30 min CDN limit).
     * The prefetched URL is stored and used for seamless recovery.
     */
    private void startBilibiliRefreshTimer(Song song) {
        cancelBilibiliRefreshTimer();
        bilibiliRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                Song currentSong = getCurrentSong();
                if (currentSong == null || !currentSong.isBilibili()
                        || currentSong.getId() != song.getId()) {
                    return;
                }
                Log.d(TAG, "Bilibili URL pre-refresh timer fired");
                String cookie = getBilibiliCookie();
                BilibiliApiHelper.getAudioStreamUrl(
                        currentSong.getBvid(), currentSong.getCid(), cookie,
                        new BilibiliApiHelper.AudioStreamCallback() {
                            @Override
                            public void onResult(String audioUrl) {
                                prefetchedBilibiliUrl = audioUrl;
                                Log.d(TAG, "Bilibili URL pre-fetched successfully");
                                // Schedule proactive switch after 3 minutes if not used
                                mainHandler.postDelayed(() -> {
                                    Song cs = getCurrentSong();
                                    if (cs != null && cs.isBilibili()
                                            && cs.getId() == song.getId()
                                            && prefetchedBilibiliUrl != null
                                            && isPlaying && exoPlayer != null) {
                                        int pos = getCurrentPosition();
                                        if (pos > 60000) {
                                            String freshUrl = prefetchedBilibiliUrl;
                                            prefetchedBilibiliUrl = null;
                                            bilibiliRetryCount = 0;
                                            Log.d(TAG, "Proactive Bilibili URL switch at " + pos + "ms");
                                            cs.setUrl(freshUrl);
                                            currentlyPlayingSongId = cs.getId();
                                            playWithHeaders(cs, freshUrl, pos);
                                        }
                                    }
                                }, 3 * 60 * 1000);
                                // Re-schedule for next refresh cycle
                                mainHandler.postDelayed(bilibiliRefreshRunnable,
                                        BILIBILI_REFRESH_INTERVAL_MS);
                            }

                            @Override
                            public void onError(String message) {
                                Log.w(TAG, "Bilibili URL pre-refresh failed: " + message);
                                // Retry after 5 minutes
                                mainHandler.postDelayed(bilibiliRefreshRunnable,
                                        5 * 60 * 1000);
                            }
                        });
            }
        };
        mainHandler.postDelayed(bilibiliRefreshRunnable, BILIBILI_REFRESH_INTERVAL_MS);
    }

    /**
     * Cancel the Bilibili URL refresh timer.
     */
    private void cancelBilibiliRefreshTimer() {
        if (bilibiliRefreshRunnable != null) {
            mainHandler.removeCallbacks(bilibiliRefreshRunnable);
            bilibiliRefreshRunnable = null;
        }
    }

    /**
     * Get Bilibili cookie from SharedPreferences.
     */
    private String getBilibiliCookie() {
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("music163_settings",
                    Context.MODE_PRIVATE);
            return prefs.getString("bilibili_cookie", "");
        }
        return "";
    }

    public boolean isUsingExoPlayer() {
        return usingExoPlayer;
    }

    public void switchCurrentSongQuality(String quality) {
        Song song = getCurrentSong();
        if (song == null || song.isBilibili()) {
            return;
        }
        int resumePositionMs = getCurrentPosition();
        boolean wasPlaying = isPlaying;

        String localPath = DownloadManager.getDownloadedPathForQuality(song, quality);
        if (localPath != null) {
            song.setUrl(localPath);
            song.setLocalQuality(quality);
            currentlyPlayingSongId = song.getId();
            playLocalFile(localPath, song, resumePositionMs, wasPlaying);
            return;
        }

        String cookie = getCookie();
        MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, quality,
                new MusicApiHelper.UrlCallback() {
                    @Override
                    public void onResult(String freshUrl) {
                        song.setUrl(freshUrl);
                        song.setLocalQuality(null);
                        currentlyPlayingSongId = song.getId();
                        play(freshUrl, resumePositionMs, wasPlaying);
                    }

                    @Override
                    public void onError(String message) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("切换音质失败: " + message));
                        }
                    }
                });
    }

    public List<String> getCurrentPlayerInfoLines() {
        List<String> lines = new ArrayList<>();
        Song song = getCurrentSong();
        if (song == null) {
            lines.add("暂无播放信息");
            return lines;
        }
        String resolvedPath = song.getUrl();
        boolean isLocal = resolvedPath != null && resolvedPath.startsWith("/");
        lines.add("来源平台: " + (song.isBilibili() ? "Bilibili" : "网易云"));
        lines.add("播放方式: " + (isLocal ? "本地音频" : "URL播放"));
        lines.add("播放器内核: " + (usingExoPlayer ? "ExoPlayer" : "MediaPlayer"));

        String localQuality = song.getLocalQuality();
        if (localQuality == null && isLocal) {
            localQuality = DownloadManager.detectLocalQualityFromPath(resolvedPath);
        }
        if (!TextUtils.isEmpty(localQuality)) {
            lines.add("当前本地音质: " + formatQualityLabel(localQuality));
        } else if (!song.isBilibili() && appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("music163_settings",
                    Context.MODE_PRIVATE);
            lines.add("当前目标音质: "
                    + formatQualityLabel(prefs.getString("preferred_quality", "exhigh")));
        }

        if (song.isBilibili()) {
            if (!TextUtils.isEmpty(song.getBvid())) {
                lines.add("BV号: " + song.getBvid());
            }
            if (song.getCid() > 0) {
                lines.add("CID: " + song.getCid());
            }
        } else if (song.getId() > 0) {
            lines.add("歌曲ID: " + song.getId());
        }

        if (sourcePlaylistId > 0 && !TextUtils.isEmpty(sourcePlaylistName)) {
            lines.add("播放来源歌单: " + sourcePlaylistName);
        }
        if (!TextUtils.isEmpty(resolvedPath)) {
            lines.add((isLocal ? "本地路径: " : "播放地址: ") + resolvedPath);
        }
        return lines;
    }

    private String formatQualityLabel(String quality) {
        if (quality == null) {
            return "";
        }
        switch (quality) {
            case "standard": return "标准";
            case "higher": return "较高";
            case "exhigh": return "极高";
            case "lossless": return "无损";
            case "hires": return "Hi-Res";
            case "jyeffect": return "臻品声场";
            case "sky": return "全景声";
            case "jymaster": return "臻品母带";
            default: return quality;
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
            boolean exitApp = shouldExitAfterSleepTimer();
            if (exitApp) {
                stop();
            } else {
                pause();
            }
            sleepTimerEndMs = 0;
            if (callback != null) {
                boolean finalExitApp = exitApp;
                mainHandler.post(() -> callback.onSleepTimerTriggered(finalExitApp));
            }
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

    private boolean shouldExitAfterSleepTimer() {
        if (appContext == null) {
            return false;
        }
        SharedPreferences prefs = appContext.getSharedPreferences("music163_settings",
                Context.MODE_PRIVATE);
        return prefs.getBoolean("sleep_timer_exit_app", false);
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
                if (current.getSource() != null) songJson.put("source", current.getSource());
                if (current.getBvid() != null) songJson.put("bvid", current.getBvid());
                if (current.getCid() != 0) songJson.put("cid", current.getCid());
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
                if (s.getSource() != null) obj.put("source", s.getSource());
                if (s.getBvid() != null) obj.put("bvid", s.getBvid());
                if (s.getCid() != 0) obj.put("cid", s.getCid());
                playlistArr.put(obj);
            }
            editor.putString(KEY_PLAYLIST_JSON, playlistArr.toString());
            editor.putInt(KEY_CURRENT_INDEX, currentIndex);

            // Save source playlist info
            editor.putLong(KEY_SOURCE_PLAYLIST_ID, sourcePlaylistId);
            editor.putString(KEY_SOURCE_PLAYLIST_NAME, sourcePlaylistName);
            editor.putInt(KEY_SOURCE_PLAYLIST_TRACK_COUNT, sourcePlaylistTrackCount);
            editor.putString(KEY_SOURCE_PLAYLIST_CREATOR, sourcePlaylistCreator);
            editor.putLong(KEY_SOURCE_PLAYLIST_CREATOR_USER_ID, sourcePlaylistCreatorUserId);
            editor.putBoolean(KEY_SOURCE_PLAYLIST_IS_LIKED, sourcePlaylistIsLiked);
            editor.putBoolean(KEY_PERSONAL_FM_MODE, personalFmMode);

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
                String source = obj.optString("source", null);
                if (source != null && !source.isEmpty()) {
                    song.setSource(source);
                }
                String bvid = obj.optString("bvid", null);
                if (bvid != null && !bvid.isEmpty()) {
                    song.setBvid(bvid);
                }
                long cid = obj.optLong("cid", 0);
                if (cid != 0) {
                    song.setCid(cid);
                }
                restoredList.add(song);
            }

            playlist.clear();
            playlist.addAll(restoredList);
            if (savedIndex >= 0 && savedIndex < playlist.size()) {
                currentIndex = savedIndex;
            } else {
                currentIndex = 0;
            }

            // Restore source playlist info
            sourcePlaylistId = prefs.getLong(KEY_SOURCE_PLAYLIST_ID, -1);
            sourcePlaylistName = prefs.getString(KEY_SOURCE_PLAYLIST_NAME, null);
            sourcePlaylistTrackCount = prefs.getInt(KEY_SOURCE_PLAYLIST_TRACK_COUNT, 0);
            sourcePlaylistCreator = prefs.getString(KEY_SOURCE_PLAYLIST_CREATOR, null);
            sourcePlaylistCreatorUserId = prefs.getLong(KEY_SOURCE_PLAYLIST_CREATOR_USER_ID, 0);
            sourcePlaylistIsLiked = prefs.getBoolean(KEY_SOURCE_PLAYLIST_IS_LIKED, false);
            personalFmMode = prefs.getBoolean(KEY_PERSONAL_FM_MODE, false);
            personalFmLoading = false;

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error restoring playback state", e);
            return false;
        }
    }

    private void clearSourcePlaylistInfo() {
        sourcePlaylistId = -1;
        sourcePlaylistName = null;
        sourcePlaylistTrackCount = 0;
        sourcePlaylistCreator = null;
        sourcePlaylistCreatorUserId = -1;
        sourcePlaylistIsLiked = false;
    }

    private void playNextSequential() {
        if (playlist.isEmpty()) return;
        if (personalFmMode && currentIndex == playlist.size() - 1) {
            loadMorePersonalFmAndAdvance();
            return;
        }
        currentIndex = (currentIndex + 1) % playlist.size();
        playCurrent();
    }

    private void loadMorePersonalFmAndAdvance() {
        if (personalFmLoading) return;
        String cookie = getCookie();
        personalFmLoading = true;
        MusicApiHelper.getPersonalFM(cookie, new MusicApiHelper.PersonalFMCallback() {
            @Override
            public void onResult(List<Song> songs) {
                personalFmLoading = false;
                appendUniqueSongs(songs);
                currentIndex = (currentIndex + 1) % playlist.size();
                savePlaybackState();
                playCurrent();
            }

            @Override
            public void onError(String message) {
                personalFmLoading = false;
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("刷新私人漫游失败: " + message));
                }
                currentIndex = (currentIndex + 1) % playlist.size();
                playCurrent();
            }
        });
    }

    private int appendUniqueSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return 0;
        }
        HashSet<Long> existingIds = new HashSet<>();
        for (Song song : playlist) {
            existingIds.add(song.getId());
        }
        int appended = 0;
        for (Song song : songs) {
            if (song == null || existingIds.contains(song.getId())) {
                continue;
            }
            existingIds.add(song.getId());
            playlist.add(song);
            appended++;
        }
        return appended;
    }
}
