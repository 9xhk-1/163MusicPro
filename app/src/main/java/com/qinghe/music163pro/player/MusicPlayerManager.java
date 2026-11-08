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
import android.os.SystemClock;
import android.text.TextUtils;
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

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.api.NeteaseWatchApi;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.service.MusicPlaybackService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String KEY_FORCE_LOCAL_PLAYBACK = "force_local_playback";
    private static final String KEY_PERSONAL_FM_MODE = "personal_fm_mode";
    private static final String KEY_PERSONAL_FM_MODE_STR = "personal_fm_mode_str";
    private static final String KEY_PERSONAL_FM_SUBMODE_STR = "personal_fm_submode_str";

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
    /** MediaPlayer error-retry guard: at most ONE re-fetch per song, otherwise a
     *  dead URL loops forever (error -> getSongUrl -> same URL -> error ...). */
    private long errorRetrySongId = -1;

    /**
     * 切歌请求令牌：每次 playCurrent() 自增。
     *
     * 取播放链接、手表网关、B 站取流、换音质、错误重试全是异步的。
     * 回调回来时必须确认"还是不是当初那次切歌"，否则上一首的链接晚到，
     * 会把已经切走的歌重新放出来 —— 表现就是歌名和实际声音对不上。
     */
    private int playRequestSeq = 0;

    /**
     * 播放器代次：v54 开始 MediaPlayer 复用（reset 而不是 new/release），
     * 「mp != mediaPlayer」再也无法区分新旧回调 —— 它们都来自同一个对象。
     * 每次切歌递增代次，所有异步 listener 回包时校验代次，过期的直接丢弃。
     */
    private volatile int playbackGeneration = 0;

    // ── 切歌性能：URL 预取缓存 / 节流 / 回调收敛 / 异步落盘 ─────────────────────
    /** 网易云播放链接有时效，10 分钟足够覆盖连续听歌，又不会让切歌拿到过期链接。 */
    private static final long URL_CACHE_TTL_MS    = 10 * 60 * 1000L;
    /** 每首开播后顺手预取后面 2 首的链接，切歌时直接用，省掉一次取链 RTT。 */
    private static final int  PREFETCH_AHEAD      = 2;
    /** 连续切歌节流：首次零延迟，窗口内的重复点击忽略，避免连点堆积请求与播放器重建。 */
    private static final long SWITCH_THROTTLE_MS  = 180L;
    private final Map<String, CachedUrl> urlCache    = new ConcurrentHashMap<>();
    // 声明成 ConcurrentHashMap 而不是 Map：putIfAbsent 是 Map 的 API 24 默认方法，
    // 但 ConcurrentHashMap 自 API 1 就自带，minSdk 23 也能用（否则 lint NewApi 报错）
    private final ConcurrentHashMap<String, Boolean> urlFetching = new ConcurrentHashMap<>();
    private long lastSwitchAt = 0L;
    private Runnable pendingSongChangedPost;
    private final ExecutorService stateIo = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stateSaveQueued = new AtomicBoolean(false);
    private volatile StateSnapshot pendingStateSnapshot;

    private static final class CachedUrl {
        final String url;
        final boolean trialOnly;
        final long timestamp;
        CachedUrl(String url, boolean trialOnly, long timestamp) {
            this.url = url; this.trialOnly = trialOnly; this.timestamp = timestamp;
        }
    }

    /** 主线程只做引用/标量快照；拼 JSON 和写盘全部放到后台线程。 */
    private static final class StateSnapshot {
        final Song current;
        final ArrayList<Song> songs;
        final int index;
        final long sourcePlaylistId;
        final String sourcePlaylistName;
        final int sourcePlaylistTrackCount;
        final String sourcePlaylistCreator;
        final long sourcePlaylistCreatorUserId;
        final boolean sourcePlaylistIsLiked;
        final boolean personalFmMode;
        final String personalFmModeStr;
        final String personalFmSubModeStr;
        StateSnapshot(Song current, ArrayList<Song> songs, int index,
                      long sourcePlaylistId, String sourcePlaylistName,
                      int sourcePlaylistTrackCount, String sourcePlaylistCreator,
                      long sourcePlaylistCreatorUserId, boolean sourcePlaylistIsLiked,
                      boolean personalFmMode, String personalFmModeStr,
                      String personalFmSubModeStr) {
            this.current = current; this.songs = songs; this.index = index;
            this.sourcePlaylistId = sourcePlaylistId;
            this.sourcePlaylistName = sourcePlaylistName;
            this.sourcePlaylistTrackCount = sourcePlaylistTrackCount;
            this.sourcePlaylistCreator = sourcePlaylistCreator;
            this.sourcePlaylistCreatorUserId = sourcePlaylistCreatorUserId;
            this.sourcePlaylistIsLiked = sourcePlaylistIsLiked;
            this.personalFmMode = personalFmMode;
            this.personalFmModeStr = personalFmModeStr;
            this.personalFmSubModeStr = personalFmSubModeStr;
        }
    }

    private int beginPlayRequest() {
        return ++playRequestSeq;
    }

    private boolean isPlayRequestCurrent(int requestId) {
        return requestId == playRequestSeq;
    }

    private static String urlCacheKey(long songId, String quality) {
        return songId + "|" + quality;
    }

    private String getPreferredQuality() {
        if (appContext == null) return "exhigh";
        return appContext.getSharedPreferences("music163_settings", Context.MODE_PRIVATE)
                .getString("preferred_quality", "exhigh");
    }

    private String getCachedUrl(long songId, String quality) {
        String key = urlCacheKey(songId, quality);
        CachedUrl c = urlCache.get(key);
        if (c == null) return null;
        if (System.currentTimeMillis() - c.timestamp > URL_CACHE_TTL_MS) {
            urlCache.remove(key);
            return null;
        }
        return c.url;
    }

    private void cacheUrl(long songId, String quality, String url, boolean trialOnly) {
        if (url == null) return;
        // 试听片段 / 直连兜底需要走手表网关，缓存它们会把错误内容当成正式链接反复用
        if (trialOnly || url.contains("/song/media/outer/url")) return;
        urlCache.put(urlCacheKey(songId, quality),
                new CachedUrl(url, trialOnly, System.currentTimeMillis()));
    }

    /**
     * 当前歌开播后预取后面几首的播放链接。回调在 mainHandler，写 ConcurrentHashMap 安全。
     * 只预取普通网易云歌曲；本地/B 站/私人漫游不走这条链路。
     */
    private void prefetchUpcoming() {
        if (appContext == null || playlist.isEmpty() || personalFmMode) return;
        final String quality = getPreferredQuality();
        final String cookie = getCookie();
        final int size = playlist.size();
        for (int ahead = 1; ahead <= PREFETCH_AHEAD; ahead++) {
            int idx = (currentIndex + ahead) % size;
            Song s = playlist.get(idx);
            if (s == null || s.getId() <= 0 || s.isForceLocalPlayback()) {
                continue;
            }
            final long songId = s.getId();
            final String key = urlCacheKey(songId, quality);
            if (getCachedUrl(songId, quality) != null) continue;
            if (urlFetching.putIfAbsent(key, Boolean.TRUE) != null) continue;
            MusicApiHelper.getSongUrlInfo(songId, cookie, quality,
                    new MusicApiHelper.SongUrlInfoCallback() {
                        @Override public void onResult(String url, boolean trialOnly) {
                            urlFetching.remove(key);
                            cacheUrl(songId, quality, url, trialOnly);
                        }
                        @Override public void onError(String message) {
                            urlFetching.remove(key);
                        }
                    });
        }
    }

    // ExoPlayer field retained for forward-compatibility; not used by current NetEase-only playback.
    private ExoPlayer exoPlayer;
    private boolean usingExoPlayer = false;

    // 蓝牙耳机输出监听（接入/断开时调整音量与播放状态）
    private boolean audioRouteListenerRegistered = false;
    private boolean lastKnownBluetoothOutput = false;

    // Playlist source tracking: set when playing from a playlist
    private long sourcePlaylistId = -1;
    private String sourcePlaylistName;
    private int sourcePlaylistTrackCount;
    private String sourcePlaylistCreator;
    private long sourcePlaylistCreatorUserId;
    private boolean sourcePlaylistIsLiked;
    private boolean personalFmMode = false;
    private boolean personalFmLoading = false;
    private String personalFmModeStr = "DEFAULT";
    private String personalFmSubModeStr = "";

    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
        // 音量等级一变就立刻刷到当前播放器：不这样做的话，耳机上调完音量要等
        // 切下一首歌（重建播放器）才生效，听感上就是"音量又跳回去了"。
        AppVolumeManager.init(this.appContext).setGainApplier(this::applyAppVolume);
        registerAudioRouteListener();
    }

    /**
     * 监听音频输出设备变化（蓝牙耳机接入 / 断开 / 切换）。
     *
     * 耳机断开时必须暂停：此时系统媒体音量被顶在最大，不暂停的话声音会直接
     * 从手表扬声器以满音量外放，在儿童手表上很容易吓到人。
     */
    private void registerAudioRouteListener() {
        if (audioRouteListenerRegistered || appContext == null) {
            return;
        }
        AppVolumeManager manager = AppVolumeManager.init(appContext);
        if (manager == null) {
            return;
        }
        audioRouteListenerRegistered = true;
        lastKnownBluetoothOutput = manager.isBluetoothOutput();
        manager.addOutputChangeListener(bluetoothNow -> {
            final boolean wasBluetooth = lastKnownBluetoothOutput;
            lastKnownBluetoothOutput = bluetoothNow;
            mainHandler.post(() -> {
                final boolean shouldPause = wasBluetooth && !bluetoothNow && isPlaying;
                if (shouldPause) {
                    Log.i(TAG, "蓝牙耳机已断开，暂停播放以防扬声器外放");
                    pause();
                }
                // 设备切换后重新套用增益；正在播放时才重建音量锚点，
                // 避免暂停状态下无谓地把系统音量顶满（会影响其他应用）
                applyAppVolume();
                if (isPlaying) {
                    AppVolumeManager current = AppVolumeManager.get();
                    if (current != null) {
                        current.anchorSystemVolume();
                    }
                }
                if (shouldPause) {
                    MusicPlaybackService.notifyExternalStateChange(appContext);
                }
            });
        });
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
        setPersonalFmPlaylist(songs, startIndex, "DEFAULT", "");
    }

    public void setPersonalFmPlaylist(List<Song> songs, int startIndex, String mode, String subMode) {
        playlist.clear();
        playlist.addAll(songs);
        currentIndex = startIndex;
        clearSourcePlaylistInfo();
        personalFmMode = true;
        personalFmLoading = false;
        personalFmModeStr = (mode != null && !mode.isEmpty()) ? mode : "DEFAULT";
        personalFmSubModeStr = (subMode != null) ? subMode : "";
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

    /**
     * Best song to derive the background cover from: the current song if it has
     * a cover, otherwise the first playlist song with a cover (so the background
     * still loads when nothing is playing yet).
     */
    public Song getBackgroundSong() {
        Song current = getCurrentSong();
        if (current != null && current.getCoverUrl() != null && !current.getCoverUrl().isEmpty()) {
            return current;
        }
        for (Song song : playlist) {
            if (song != null && song.getCoverUrl() != null && !song.getCoverUrl().isEmpty()) {
                return song;
            }
        }
        return current;
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
        // 切歌用 reset 复用播放器，不再每次 new/release（4 核手表上编解码器创建/销毁很重）
        preparePlayerForSwitch();
        final int generation = playbackGeneration;
        final int requestId = playRequestSeq;
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        try {
            if (appContext != null) {
                mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            }
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
            applyAppVolumeForPlayback();
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                // 这个回调可能来自已经被换掉的播放器（快速连续切歌），必须丢弃
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return;
                }
                // prepare 完成后再压一次：蓝牙切路由后 ROM 可能把音量重置成满音量
                restoreAppVolumeOnPrepared();
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
                    // 已经出声，再预取后面几首；不抢当前歌的缓冲带宽
                    prefetchUpcoming();
                } else {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return;
                }
                onSongCompleted();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return true;
                }
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
                // Retry without cached URL — but only ONCE per song: without a
                // guard a dead URL loops forever (error -> getSongUrl -> same
                // dead URL -> error ...), hammering the API every ~300ms.
                if (song != null && song.getId() != errorRetrySongId) {
                    errorRetrySongId = song.getId();
                    String cookie = getCookie();
                    MusicApiHelper.getSongUrl(song.getId(), cookie, false,
                            new MusicApiHelper.UrlCallback() {
                                @Override
                                public void onResult(String retryUrl) {
                                    if (!isPlayRequestCurrent(requestId)) {
                                        return;
                                    }
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
     * for downloaded songs.
     */
    private void playLocalFile(String localPath, Song song) {
        playLocalFile(localPath, song, 0, true);
    }

    private void playLocalFile(String localPath, Song song, int resumePositionMs,
                               boolean shouldPlayWhenReady) {
        preparePlayerForSwitch();
        final int generation = playbackGeneration;
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        try {
            if (appContext != null) {
                mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
            }
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
            applyAppVolumeForPlayback();
            mediaPlayer.setDataSource(localPath);
            mediaPlayer.setOnPreparedListener(mp -> {
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return;
                }
                restoreAppVolumeOnPrepared();
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
                    // 已经出声，再预取后面几首；不抢当前歌的缓冲带宽
                    prefetchUpcoming();
                } else {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return;
                }
                onSongCompleted();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (mp != mediaPlayer || generation != playbackGeneration) {
                    return true;
                }
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
        // 从后台/耳机暂停恢复时，系统音量可能已被蓝牙绝对音量改过
        restoreAppVolumeOnPrepared();
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

    /**
     * 把应用内精细音量（30 级）作用到当前播放器。
     * 音量被改成不跟随系统分级后，每次调音量都需要调用一次。
     */
    public void applyAppVolume() {
        float gain = AppVolumeManager.gainOf();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(gain, gain);
            } catch (Exception e) {
                Log.w(TAG, "应用内音量设置失败(MediaPlayer)", e);
            }
        }
        if (exoPlayer != null) {
            try {
                exoPlayer.setVolume(gain);
            } catch (Exception e) {
                Log.w(TAG, "应用内音量设置失败(ExoPlayer)", e);
            }
        }
    }

    /**
     * 每次开始播放前调用：先套用应用内增益，再把系统媒体音量固定到最大（满刻度锚点）。
     * 顺序不能反：先压低数字增益，再抬高系统音量，避免中间出现一瞬间的响度突跳。
     *
     * 这里绝不做任何"按输出设备改等级"的事：等级全局只有一份，否则每次切歌都会
     * 把用户刚调好的音量重置回去（连蓝牙后切第二首尤其明显）。
     */
    private void applyAppVolumeForPlayback() {
        applyAppVolume();
        AppVolumeManager manager = AppVolumeManager.get();
        if (manager != null) {
            manager.anchorSystemVolume();
        }
    }

    /**
     * prepare / STATE_READY 之后重新压一次增益并拉回系统锚点。
     * 部分 ROM（尤其蓝牙 A2DP 路由刚切换完）会在 prepare 或 start 时把
     * MediaPlayer 的音量重置成 1.0，只在 setDataSource 前设一次是不够的。
     */
    private void restoreAppVolumeOnPrepared() {
        AppVolumeManager manager = AppVolumeManager.get();
        if (manager != null) {
            manager.refreshGain();
        } else {
            applyAppVolume();
        }
    }

    public void stop() {
        // stop() 只在「关闭应用 / 真正结束播放」时调用，此时把挂起的后台状态写同步收尾，
        // 避免刚切完歌就被系统杀掉、下次启动恢复到上一首。
        flushPlaybackStateNow();
        // 定时关闭的「联网模式」是 500ms 自轮询；真正结束播放时必须一起撤掉，
        // 否则 stop() 之后这条链还在后台空转唤醒 CPU
        cancelSleepTimer();
        if (exoPlayer != null) {
            try {
                exoPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping ExoPlayer", e);
            }
            exoPlayer = null;
        }
        if (mediaPlayer != null) {
            // release() 会自行停掉播放；不必先 isPlaying()（IDLE / Error 状态会抛异常，
            // 原实现因此可能跳过 release，泄漏一个播放器实例）
            playbackGeneration++;
            try {
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

    /**
     * 切歌专用：把播放器清到 IDLE 但**不释放对象**，下一次 setDataSource/prepareAsync 直接复用。
     * stop() 仍然完整 release，留给「关闭应用 / 停止播放」这类真正要结束播放的场景。
     */
    private void preparePlayerForSwitch() {
        // 先让上一代所有在途的 prepare / completion / error 回调作废。
        // 复用同一个 MediaPlayer 对象后，靠对象引用已经分不清新旧回调了。
        playbackGeneration++;
        if (exoPlayer != null) {
            try {
                exoPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing ExoPlayer", e);
            }
            exoPlayer = null;
        }
        if (mediaPlayer != null) {
            try {
                // reset() 可从任意状态（含 IDLE / 正在播放 / Error）直接回到 IDLE，
                // 且会自动停掉播放。绝对不能先调 isPlaying()：IDLE 下它会抛
                // IllegalStateException，导致复用退化成每次都 new MediaPlayer。
                mediaPlayer.reset();
            } catch (Exception e) {
                // reset 失败说明状态机已损坏，退回新建，别让后续切歌一直报错
                Log.w(TAG, "reset 失败，重建 MediaPlayer", e);
                try {
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }
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
        reportScrobble(true);
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

    /**
     * 手动切歌节流：第一次立刻执行（保证「点下去就切」零延迟），
     * 之后 180ms 内的重复点击忽略 —— 连点不会堆积取链请求、播放器 reset 和通知重建。
     * 自然播放结束走 playNextSequential()，不经过这里，不受节流影响。
     */
    private boolean throttleSwitch() {
        long now = SystemClock.uptimeMillis();
        if (now - lastSwitchAt < SWITCH_THROTTLE_MS) {
            return true;
        }
        lastSwitchAt = now;
        return false;
    }

    public void next() {
        if (playlist.isEmpty()) return;
        if (throttleSwitch()) return;
        reportScrobble(false);
        // 随机播放 + 切歌顺序=随机: 上下键均随机切歌 (默认列表顺序时走顺序切换)
        if (useRandomJumpForKeys()) {
            playRandomOther();
            return;
        }
        playNextSequential();
    }

    public void previous() {
        if (playlist.isEmpty()) return;
        if (throttleSwitch()) return;
        reportScrobble(false);
        if (useRandomJumpForKeys()) {
            playRandomOther();
            return;
        }
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playCurrent();
    }

    /** 随机播放模式且开启了随机切歌 (仅手动上下键, 私人FM除外). */
    private boolean useRandomJumpForKeys() {
        return playMode == PlayMode.RANDOM && !personalFmMode
                && isRandomSkipRandom() && playlist.size() > 1;
    }

    /** 随机播放列表内的另一首歌 (排除当前). */
    private void playRandomOther() {
        int newIndex;
        do {
            newIndex = random.nextInt(playlist.size());
        } while (newIndex == currentIndex);
        currentIndex = newIndex;
        playCurrent();
    }

    public void playFromCurrentPlaylist(int index) {
        if (index < 0 || index >= playlist.size()) return;
        reportScrobble(false);
        currentIndex = index;
        playCurrent();
    }

    public void playCurrent() {
        Song song = getCurrentSong();
        if (song == null) return;

        // 每次切歌先作废所有在途请求
        final int requestId = beginPlayRequest();

        // 关键：先把上一首停掉。否则新歌链接还没回来时旧歌会继续播，
        // 而界面已经换成新歌名 —— 这就是"歌名和声音错乱"的直接来源。
        // 用 reset 复用播放器，不 release，切歌更快。
        preparePlayerForSwitch();

        // User-initiated (or next/prev) play: allow one error retry again
        // for this song. The MediaPlayer error retry never reaches here —
        // it retries through play() directly — so this cannot re-open the loop.
        errorRetrySongId = -1;
        currentlyPlayingSongId = song.getId();

        // UI 回调带序号：连点时只把最后一次切歌事件送到界面，旧回调直接作废
        notifySongChanged(song, requestId);
        savePlaybackState();

        if (song.isForceLocalPlayback()) {
            String localPath = DownloadManager.getDownloadedMp3Path(song);
            if (!TextUtils.isEmpty(localPath)) {
                song.setUrl(localPath);
                startLocalPlayback(song, localPath);
                return;
            }
            notifyLocalPlaybackMissing(song);
            return;
        }

        // 预取命中就跳过整整一次取链 RTT —— 「切下去就有声音」主要靠这一步。
        // 缓存是 10 分钟内的新鲜链接，未命中/过期才走网络，回来后回填缓存。
        final String quality = getPreferredQuality();
        String cachedUrl = getCachedUrl(song.getId(), quality);
        if (cachedUrl != null) {
            Log.d(TAG, "预取链接命中，直接开播 songId=" + song.getId());
            currentlyPlayingSongId = song.getId();
            reportScrobbleStart(song);
            play(cachedUrl, 0, true);
            return;
        }

        // Always fetch a fresh URL to avoid expired URL issues.
        // NetEase song URLs are time-limited, so cached URLs may not work.
        String cookie = getCookie();
        MusicApiHelper.getSongUrlInfo(song.getId(), cookie, quality,
                new MusicApiHelper.SongUrlInfoCallback() {
            @Override
            public void onResult(String freshUrl, boolean trialOnly) {
                if (!isPlayRequestCurrent(requestId)) {
                    Log.d(TAG, "丢弃过期的取链接回调 songId=" + song.getId());
                    return;
                }
                // Route through the watch gateway when the main API produced a
                // trial clip OR could not produce any URL at all. In the latter
                // case freshUrl is the outer/url direct fallback: anonymous eapi
                // answers 404 (no freeTrialInfo!) for VIP songs, so logged-out
                // playback would otherwise never reach the gateway and the user
                // only hears the trial snippet the outer link redirects to.
                boolean needsGateway = trialOnly
                        || (freshUrl != null && freshUrl.contains("/song/media/outer/url"));
                if (needsGateway && isWatchParseEnabled()) {
                    // Watch gateway enabled: try it for a full (non-trial) URL
                    resolveViaWatchGateway(song, freshUrl, cookie, requestId);
                    return;
                }
                // 当前这首也回填：单曲循环 / 上一首回来时同样能秒开
                cacheUrl(song.getId(), quality, freshUrl, trialOnly);
                finishPlay(song, freshUrl, trialOnly, requestId);
            }

            @Override
            public void onError(String message) {
                if (!isPlayRequestCurrent(requestId)) {
                    return;
                }
                // Fallback: try cached URL if available
                if (song.getUrl() != null && !song.getUrl().isEmpty()) {
                    currentlyPlayingSongId = song.getId();
                    reportScrobbleStart(song);
                    play(song.getUrl());
                } else {
                    isPlaying = false;
                    notifyPlayStateChanged(false);
                    if (callback != null) {
                        String name = song.getDisplayName();
                        mainHandler.post(() -> callback.onError(
                                "无法获取歌曲链接: " + name + " (" + message + ")"));
                    }
                }
            }
        });
    }

    /**
     * Trial link received and "use watch parse" is on: ask the watch gateway
     * (NeteaseWatchApi, eapi trialMode) for a full URL.
     *   - full URL returned  -> play it
     *   - empty / trial      -> fall back to the original trial URL, then run
     *                            the usual skip-trial check
     */
    private void resolveViaWatchGateway(Song song, String trialUrl, String sessionCookie,
                                        int requestId) {
        MusicLog.i("MusicPlayerManager", "试听链接，手表网关解析 songId=" + song.getId());
        boolean loggedIn = sessionCookie != null && sessionCookie.contains("MUSIC_U");
        if (loggedIn) {
            // Logged in: authenticate gateway requests with the saved session
            NeteaseWatchApi.setSessionCookie(sessionCookie);
            requestWatchPlayUrl(song, trialUrl, requestId);
            return;
        }
        // Not logged in: reuse the anonymous "guest" cookie persisted on first
        // use (stored forever, deleted by nothing, used ONLY by NeteaseWatchApi
        // play-URL requests — never for the main API).
        String guest = readPersistedGuestCookie();
        if (!guest.isEmpty()) {
            NeteaseWatchApi.setSessionCookie(guest);
            requestWatchPlayUrl(song, trialUrl, requestId);
            return;
        }
        // First ever use: mint an anonymous session via the guest endpoint
        NeteaseWatchApi.fetchGuestSession(new NeteaseWatchApi.GuestSessionCallback() {
            @Override
            public void onResult(String guestCookie) {
                if (!guestCookie.isEmpty()) {
                    persistGuestCookie(guestCookie);
                    NeteaseWatchApi.setSessionCookie(guestCookie);
                    MusicLog.i("MusicPlayerManager", "已获取并保存游客会话");
                }
                requestWatchPlayUrl(song, trialUrl, requestId);
            }

            @Override
            public void onError(String message) {
                MusicLog.w("MusicPlayerManager", "获取游客会话失败: " + message);
                requestWatchPlayUrl(song, trialUrl, requestId);
            }
        });
    }

    /** 游客 cookie (watch_guest_cookie) — 长期储存，任何情况都不删除. */
    private static final String PREF_WATCH_GUEST_COOKIE = "watch_guest_cookie";

    private String readPersistedGuestCookie() {
        return appContext == null ? "" : appContext.getSharedPreferences("music163_settings",
                Context.MODE_PRIVATE).getString(PREF_WATCH_GUEST_COOKIE, "");
    }

    private void persistGuestCookie(String guestCookie) {
        if (appContext == null || guestCookie == null || guestCookie.isEmpty()) return;
        appContext.getSharedPreferences("music163_settings", Context.MODE_PRIVATE)
                .edit().putString(PREF_WATCH_GUEST_COOKIE, guestCookie).apply();
    }

    /** Second half of resolveViaWatchGateway: fire the gateway and honour the fallback. */
    private void requestWatchPlayUrl(Song song, String trialUrl, int requestId) {
        NeteaseWatchApi.getPlayUrl(song.getId(), new NeteaseWatchApi.WatchUrlCallback() {
            @Override
            public void onResult(String url, boolean trialOnly) {
                if (!isPlayRequestCurrent(requestId)) {
                    return;
                }
                if (!trialOnly && url != null && !url.isEmpty()) {
                    MusicLog.i("MusicPlayerManager", "手表网关返回完整链接 songId=" + song.getId());
                    finishPlay(song, url, false, requestId);
                } else {
                    // No full link from the gateway: fall back to trial URL
                    finishPlay(song, trialUrl, true, requestId);
                }
            }

            @Override
            public void onError(String message) {
                if (!isPlayRequestCurrent(requestId)) {
                    return;
                }
                MusicLog.w("MusicPlayerManager", "手表网关解析失败，回退试听: " + message);
                finishPlay(song, trialUrl, true, requestId);
            }
        });
    }

    /**
     * Common tail of playCurrent: honour the skip-trial switch, then play.
     */
    private void finishPlay(Song song, String url, boolean trialOnly, int requestId) {
        if (!isPlayRequestCurrent(requestId)) {
            Log.d(TAG, "丢弃过期的播放回调 songId=" + song.getId());
            return;
        }
        if (trialOnly && isSkipTrialEnabled()) {
            onTrialOnlySong(song);
            return;
        }
        trialSkipStreak = 0;
        song.setUrl(url);
        currentlyPlayingSongId = song.getId();
        reportScrobbleStart(song);
        play(url);
    }

    private int trialSkipStreak = 0;

    private boolean isSkipTrialEnabled() {
        return appContext != null && appContext.getSharedPreferences("music163_settings",
                Context.MODE_PRIVATE).getBoolean("skip_trial_songs", false);
    }

    /** 开关选项: 是否使用手表解析接口 (默认开). */
    private boolean isWatchParseEnabled() {
        return appContext == null || appContext.getSharedPreferences("music163_settings",
                Context.MODE_PRIVATE).getBoolean("use_watch_parse", true);
    }

    /** 开关选项: 随机播放模式下上下键随机切歌 (默认关=按列表顺序). */
    private boolean isRandomSkipRandom() {
        return appContext != null && appContext.getSharedPreferences("music163_settings",
                Context.MODE_PRIVATE).getBoolean("random_skip_random", false);
    }

    /**
     * The URL API returned a free-trial-only link and the user enabled
     * "skip trial songs". Advance past it (ads-watched VIP songs return a
     * full link and never reach this path).
     */
    private void onTrialOnlySong(Song song) {
        trialSkipStreak++;
        // 静默跳过 (不再 Toast "已跳过试听歌曲")
        Log.d(TAG, "试听歌曲跳过: " + song.getDisplayName());
        if (playlist.isEmpty()) {
            return;
        }
        if (trialSkipStreak >= playlist.size()) {
            // Full lap without any playable song: stop instead of looping forever
            trialSkipStreak = 0;
            if (callback != null) {
                mainHandler.post(() -> callback.onError("列表歌曲仅可试听，已停止"));
            }
            stop();
            notifyPlayStateChanged(false);
            return;
        }
        if (personalFmMode && currentIndex == playlist.size() - 1) {
            playNextSequential();
            return;
        }
        if (playMode == PlayMode.RANDOM && playlist.size() > 1) {
            int newIndex;
            do {
                newIndex = random.nextInt(playlist.size());
            } while (newIndex == currentIndex);
            currentIndex = newIndex;
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
        }
        playCurrent();
    }

    // ==================== Play History (scrobble) ====================

    private boolean isCurrentLocalPlayback() {
        Song song = getCurrentSong();
        if (song == null) {
            return false;
        }
        String url = song.getUrl();
        return song.isForceLocalPlayback() || (url != null && url.startsWith("/"));
    }

    private final ConcurrentHashMap<String, Long> scrobbleThrottle = new ConcurrentHashMap<>();

    /** Min interval (ms) between identical scrobble reports of the same song. */
    private static final long SCROBBLE_THROTTLE_MS = 60_000L;

    /**
     * Collapse bursts of identical reports (e.g. replay loops after a playback
     * failure). key = report kind + song id (+ seconds for end reports).
     */
    private boolean shouldThrottleScrobble(String key) {
        long now = System.currentTimeMillis();
        Long last = scrobbleThrottle.get(key);
        if (last != null && now - last < SCROBBLE_THROTTLE_MS) {
            Log.d(TAG, "听歌打卡节流跳过: " + key);
            return true;
        }
        scrobbleThrottle.put(key, now);
        for (Map.Entry<String, Long> e : scrobbleThrottle.entrySet()) {
            if (now - e.getValue() >= SCROBBLE_THROTTLE_MS) {
                scrobbleThrottle.remove(e.getKey());
            }
        }
        return false;
    }

    /**
     * sourceid is required by /scrobble: prefer the source playlist, fall back
     * to the song's album id, else 0.
     */
    private long resolveScrobbleSourceId(Song song) {
        if (hasSourcePlaylist()) {
            return sourcePlaylistId;
        }
        return song.getAlbumId() > 0 ? song.getAlbumId() : 0L;
    }

    /**
     * Report playback start immediately (time = 0) so the song appears in
     * NetEase recent plays even when the session ends abruptly.
     */
    private void reportScrobbleStart(Song song) {
        if (song == null || song.getId() <= 0) {
            return;
        }
        if (shouldThrottleScrobble("start:" + song.getId())) {
            return;
        }
        MusicApiHelper.scrobble(song.getId(), resolveScrobbleSourceId(song), 0,
                isCurrentLocalPlayback(), true, getCookie());
    }

    /**
     * Report current song playback to NetEase (/scrobble). When naturalEnd is
     * true the song played to the end (time = duration); otherwise the manual
     * skip counts only if at least 30 seconds were listened to.
     */
    private void reportScrobble(boolean naturalEnd) {
        Song song = getCurrentSong();
        if (song == null || song.getId() <= 0) {
            return;
        }
        int seconds;
        if (naturalEnd) {
            seconds = Math.max(1, getDuration() / 1000);
        } else {
            seconds = Math.max(0, getCurrentPosition() / 1000);
            if (seconds < 30) {
                return;
            }
        }
        if (shouldThrottleScrobble("end:" + song.getId() + ":" + seconds)) {
            return;
        }
        MusicApiHelper.scrobble(song.getId(), resolveScrobbleSourceId(song), seconds,
                isCurrentLocalPlayback(), false, getCookie());
    }

    public boolean isUsingExoPlayer() {
        return usingExoPlayer;
    }

    public void switchCurrentSongQuality(String quality) {
        Song song = getCurrentSong();
        if (song == null) {
            return;
        }
        final int requestId = beginPlayRequest();
        int resumePositionMs = getCurrentPosition();
        boolean wasPlaying = isPlaying;

        if (song.isForceLocalPlayback()) {
            String localPath = DownloadManager.getDownloadedPathForQuality(song, quality);
            if (localPath != null) {
                song.setUrl(localPath);
                song.setLocalQuality(quality);
                currentlyPlayingSongId = song.getId();
                playLocalFile(localPath, song, resumePositionMs, wasPlaying);
            } else if (callback != null) {
                mainHandler.post(() -> callback.onError("下载列表歌曲仅支持本地音质，请先下载该音质"));
            }
            return;
        }

        String cookie = getCookie();
        MusicApiHelper.getSongUrlWithQuality(song.getId(), cookie, quality,
                new MusicApiHelper.UrlCallback() {
                    @Override
                    public void onResult(String freshUrl) {
                        if (!isPlayRequestCurrent(requestId)) {
                            return;
                        }
                        song.setUrl(freshUrl);
                        song.setLocalQuality(null);
                        currentlyPlayingSongId = song.getId();
                        play(freshUrl, resumePositionMs, wasPlaying);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isPlayRequestCurrent(requestId)) {
                            return;
                        }
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
        lines.add("来源平台: 网易云");
        lines.add("播放方式: " + (isLocal ? "本地音频" : "URL播放"));
        lines.add("播放器内核: " + (usingExoPlayer ? "ExoPlayer" : "MediaPlayer"));

        String localQuality = song.getLocalQuality();
        if (localQuality == null && isLocal) {
            localQuality = DownloadManager.detectLocalQualityFromPath(resolvedPath);
        }
        if (!TextUtils.isEmpty(localQuality)) {
            lines.add("当前本地音质: " + formatQualityLabel(localQuality));
        } else if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("music163_settings",
                    Context.MODE_PRIVATE);
            lines.add("当前目标音质: "
                    + formatQualityLabel(prefs.getString("preferred_quality", "exhigh")));
        }

        if (song.getId() > 0) {
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
        // 与音质浮层共用同一份档位中文名（官方 VIP / SVIP 面板口径）
        for (String[] q : com.qinghe.music163pro.util.QualityPicker.LEVELS) {
            if (q[0].equals(quality)) return q[1];
        }
        return quality;
    }

    private String cookieValue = "";

    public void setCookie(String cookie) {
        this.cookieValue = cookie != null ? cookie : "";
    }

    public String getCookie() {
        return cookieValue;
    }

    private void notifySongChanged(Song song) {
        notifySongChanged(song, playRequestSeq);
    }

    /**
     * 带序号的 UI 通知：连点时把之前排队但还没执行的 onSongChanged 直接撤掉，
     * 并在真正 post 执行时再校验一次令牌，杜绝「旧回调把歌名覆盖回去」。
     */
    private void notifySongChanged(Song song, int requestId) {
        if (!isPlayRequestCurrent(requestId)) return;
        if (callback == null) return;
        if (pendingSongChangedPost != null) {
            mainHandler.removeCallbacks(pendingSongChangedPost);
        }
        pendingSongChangedPost = () -> {
            pendingSongChangedPost = null;
            if (isPlayRequestCurrent(requestId) && callback != null) {
                callback.onSongChanged(song);
            }
        };
        mainHandler.post(pendingSongChangedPost);
    }

    private void notifyPlayStateChanged(boolean playing) {
        if (callback != null) {
            mainHandler.post(() -> callback.onPlayStateChanged(playing));
        }
    }

    // ==================== Sleep Timer ====================

    private long sleepTimerEndMs = 0;
    private Runnable sleepTimerRunnable;

    // Net playback timer (counts only while playing)
    private long sleepTimerNetRemainingMs = 0;
    private boolean sleepTimerNetMode = false;
    private Runnable sleepTimerNetRunnable;

    /**
     * Start sleep timer. Stops playback after the specified number of minutes.
     * @param minutes number of minutes until auto-stop
     */
    public void startSleepTimer(int minutes) {
        startSleepTimerSeconds(minutes * 60);
    }

    /**
     * Start sleep timer using the current mode from settings (total or net playback).
     * @param seconds total seconds for the timer
     */
    public void startSleepTimerSeconds(int seconds) {
        // Check current timer mode preference
        boolean netMode = false;
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("music163_settings",
                    Context.MODE_PRIVATE);
            netMode = prefs.getInt("sleep_timer_mode", 0) == 1;
        }
        if (netMode) {
            startSleepTimerNetSeconds(seconds);
        } else {
            startSleepTimerTotalSeconds(seconds);
        }
    }

    /**
     * Start total-duration sleep timer (normal countdown regardless of playback state).
     */
    private void startSleepTimerTotalSeconds(int seconds) {
        cancelSleepTimer();
        long delayMs = (long) seconds * 1000;
        sleepTimerEndMs = System.currentTimeMillis() + delayMs;
        sleepTimerNetMode = false;
        sleepTimerRunnable = () -> {
            boolean exitApp = shouldExitAfterSleepTimer();
            if (exitApp) {
                stop();
            } else {
                pause();
            }
            sleepTimerEndMs = 0;
            if (callback != null) {
                mainHandler.post(() -> callback.onSleepTimerTriggered(exitApp));
            }
        };
        mainHandler.postDelayed(sleepTimerRunnable, delayMs);
    }

    /**
     * Start net playback sleep timer (counts down only while music is playing).
     */
    private void startSleepTimerNetSeconds(int seconds) {
        cancelSleepTimer();
        sleepTimerNetRemainingMs = (long) seconds * 1000;
        sleepTimerNetMode = true;
        sleepTimerEndMs = 1; // mark as active (non-zero)
        scheduleNetTimerTick();
    }

    private void scheduleNetTimerTick() {
        if (sleepTimerNetRunnable != null) {
            mainHandler.removeCallbacks(sleepTimerNetRunnable);
        }
        sleepTimerNetRunnable = () -> {
            if (!sleepTimerNetMode || sleepTimerNetRemainingMs <= 0) return;
            if (isPlaying) {
                sleepTimerNetRemainingMs -= 500;
            }
            if (sleepTimerNetRemainingMs <= 0) {
                sleepTimerNetRemainingMs = 0;
                sleepTimerEndMs = 0;
                sleepTimerNetMode = false;
                boolean exitApp = shouldExitAfterSleepTimer();
                if (exitApp) {
                    stop();
                } else {
                    pause();
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onSleepTimerTriggered(exitApp));
                }
                return;
            }
            scheduleNetTimerTick();
        };
        mainHandler.postDelayed(sleepTimerNetRunnable, 500);
    }

    /**
     * Cancel an active sleep timer.
     */
    public void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            mainHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        if (sleepTimerNetRunnable != null) {
            mainHandler.removeCallbacks(sleepTimerNetRunnable);
            sleepTimerNetRunnable = null;
        }
        sleepTimerEndMs = 0;
        sleepTimerNetRemainingMs = 0;
        sleepTimerNetMode = false;
    }

    /**
     * Check if a sleep timer is active.
     */
    public boolean isSleepTimerActive() {
        if (sleepTimerNetMode) {
            return sleepTimerNetRemainingMs > 0;
        }
        return sleepTimerEndMs > 0 && System.currentTimeMillis() < sleepTimerEndMs;
    }

    /**
     * Get remaining milliseconds on the sleep timer.
     * @return remaining ms, or 0 if no timer is active
     */
    public long getSleepTimerRemainingMs() {
        if (sleepTimerNetMode) {
            return sleepTimerNetRemainingMs > 0 ? sleepTimerNetRemainingMs : 0;
        }
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
     *
     * v54：主线程只做一次浅拷贝 + 标量快照（O(n) 但没有任何 JSON / IO），
     * 拼 JSON 与落盘放到单线程后台。连点时 pendingStateSnapshot 只保留最后一次，
     * 后台 drain 会跳过中间状态 —— 切一次歌最多写一次盘，且完全不卡 UI。
     */
    public void savePlaybackState() {
        if (appContext == null) return;
        final StateSnapshot snap;
        try {
            snap = new StateSnapshot(
                    getCurrentSong(),
                    new ArrayList<>(playlist),
                    currentIndex,
                    sourcePlaylistId, sourcePlaylistName, sourcePlaylistTrackCount,
                    sourcePlaylistCreator, sourcePlaylistCreatorUserId, sourcePlaylistIsLiked,
                    personalFmMode, personalFmModeStr, personalFmSubModeStr);
        } catch (Exception e) {
            Log.w(TAG, "Error snapshotting playback state", e);
            return;
        }
        pendingStateSnapshot = snap;
        if (stateSaveQueued.compareAndSet(false, true)) {
            stateIo.execute(this::drainStateSnapshots);
        }
    }

    /** 关闭应用 / stop() 前把最后一次快照同步写完，避免刚切歌就被杀导致状态回退。 */
    public void flushPlaybackStateNow() {
        StateSnapshot snap = pendingStateSnapshot;
        pendingStateSnapshot = null;
        if (snap != null && appContext != null) {
            writeStateSnapshot(appContext, snap);
        }
    }

    /** 后台单线程：只写最新快照，写入期间新来的快照再补排一轮。 */
    private void drainStateSnapshots() {
        try {
            StateSnapshot snap;
            while ((snap = pendingStateSnapshot) != null) {
                pendingStateSnapshot = null;
                if (appContext != null) {
                    writeStateSnapshot(appContext, snap);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error flushing playback state", e);
        } finally {
            stateSaveQueued.set(false);
            if (pendingStateSnapshot != null
                    && stateSaveQueued.compareAndSet(false, true)) {
                stateIo.execute(this::drainStateSnapshots);
            }
        }
    }

    private final Object stateWriteLock = new Object();

    private void writeStateSnapshot(Context ctx, StateSnapshot s) {
        synchronized (stateWriteLock) {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                Song current = s.current;
                if (current != null) {
                    editor.putString(KEY_CURRENT_SONG_JSON, songToStateJson(current).toString());
                } else {
                    editor.remove(KEY_CURRENT_SONG_JSON);
                }

                JSONArray playlistArr = new JSONArray();
                for (Song song : s.songs) {
                    playlistArr.put(songToStateJson(song));
                }
                editor.putString(KEY_PLAYLIST_JSON, playlistArr.toString());
                editor.putInt(KEY_CURRENT_INDEX, s.index);

                editor.putLong(KEY_SOURCE_PLAYLIST_ID, s.sourcePlaylistId);
                editor.putString(KEY_SOURCE_PLAYLIST_NAME, s.sourcePlaylistName);
                editor.putInt(KEY_SOURCE_PLAYLIST_TRACK_COUNT, s.sourcePlaylistTrackCount);
                editor.putString(KEY_SOURCE_PLAYLIST_CREATOR, s.sourcePlaylistCreator);
                editor.putLong(KEY_SOURCE_PLAYLIST_CREATOR_USER_ID, s.sourcePlaylistCreatorUserId);
                editor.putBoolean(KEY_SOURCE_PLAYLIST_IS_LIKED, s.sourcePlaylistIsLiked);
                editor.putBoolean(KEY_PERSONAL_FM_MODE, s.personalFmMode);
                editor.putString(KEY_PERSONAL_FM_MODE_STR, s.personalFmModeStr);
                editor.putString(KEY_PERSONAL_FM_SUBMODE_STR, s.personalFmSubModeStr);

                editor.apply();
            } catch (Exception e) {
                Log.w(TAG, "Error saving playback state", e);
            }
        }
    }

    /** 字段与旧实现逐项一致，restorePlaybackState() 无需改动。 */
    private static JSONObject songToStateJson(Song s) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("id", s.getId());
        obj.put("name", s.getName());
        obj.put("artist", s.getArtist());
        obj.put("album", s.getAlbum());
        if (s.getCoverUrl() != null && !s.getCoverUrl().isEmpty()) {
            obj.put("coverUrl", s.getCoverUrl());
        }
        if (s.getAlbumId() > 0) obj.put("albumId", s.getAlbumId());
        if (s.getFee() != 0) obj.put("fee", s.getFee());
        if (s.getAlias() != null && !s.getAlias().isEmpty()) obj.put("alias", s.getAlias());
        if (s.isForceLocalPlayback()) obj.put(KEY_FORCE_LOCAL_PLAYBACK, true);
        return obj;
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
                String cover = obj.optString("coverUrl", null);
                if (cover != null && !cover.isEmpty()) {
                    song.setCoverUrl(cover);
                }
                long albumId = obj.optLong("albumId", 0);
                if (albumId > 0) {
                    song.setAlbumId(albumId);
                }
                song.setFee(obj.optInt("fee", 0));
                song.setAlias(obj.optString("alias", ""));
                song.setForceLocalPlayback(obj.optBoolean(KEY_FORCE_LOCAL_PLAYBACK, false));
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
            personalFmModeStr = prefs.getString(KEY_PERSONAL_FM_MODE_STR, "DEFAULT");
            personalFmSubModeStr = prefs.getString(KEY_PERSONAL_FM_SUBMODE_STR, "");
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

    private void startLocalPlayback(Song song, String localPath) {
        song.setLocalQuality(DownloadManager.detectLocalQualityFromPath(localPath));
        currentlyPlayingSongId = song.getId();
        playLocalFile(localPath, song);
    }

    private void notifyLocalPlaybackMissing(Song song) {
        if (callback == null) {
            return;
        }
        String songName = song != null && !TextUtils.isEmpty(song.getDisplayName()) ? song.getDisplayName() : "当前歌曲";
        mainHandler.post(() -> callback.onError(
                songName + " 仅支持本地播放，但本地文件未找到。请在下载列表中重新下载"));
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
        final int requestId = playRequestSeq;
        String cookie = getCookie();
        personalFmLoading = true;
        MusicApiHelper.getPersonalFMWithMode(personalFmModeStr, personalFmSubModeStr, cookie, new MusicApiHelper.PersonalFMCallback() {
            @Override
            public void onResult(List<Song> songs) {
                personalFmLoading = false;
                if (!isPlayRequestCurrent(requestId)) {
                    return;
                }
                appendUniqueSongs(songs);
                currentIndex = (currentIndex + 1) % playlist.size();
                savePlaybackState();
                playCurrent();
            }

            @Override
            public void onError(String message) {
                personalFmLoading = false;
                if (!isPlayRequestCurrent(requestId)) {
                    return;
                }
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
