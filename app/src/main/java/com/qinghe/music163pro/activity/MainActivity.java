package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchUiUtils;
import com.qinghe.music163pro.util.QualityPicker;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.manager.FavoritesManager;
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.AppVolumeManager;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.service.MusicPlaybackService;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.NetworkImageLoader;
import com.qinghe.music163pro.util.WatchConfirmDialog;
import com.qinghe.music163pro.util.BackgroundUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerCallback {

    private static final String TAG = "MainActivity";
    private static final String HEART_OUTLINE = "\u2661";
    private static final String HEART_FILLED = "\u2665";
    private static final int STORAGE_PERMISSION_REQUEST = 100;
    private static final int VOLUME_INDICATOR_SIDE_MARGIN_DP = 20;
    private static final int VOLUME_INDICATOR_MIN_WIDTH_DP = 132;
    private static final int VOLUME_INDICATOR_COMPACT_BREAKPOINT_DP = 152;
    private static final int VOLUME_INDICATOR_MAX_WIDTH_DP = 208;
    private static final int VOLUME_INDICATOR_TOP_MARGIN_DP = 10;
    private static final int VOLUME_INDICATOR_ANIM_DURATION_MS = 160;
    private static final float VOLUME_INDICATOR_INITIAL_SCALE = 0.96f;
    private static final int SAFE_VOLUME_PERCENT = 60;
    // 长按音量键连续调节：按住 350ms 后开始连发，每 130ms 走一级
    private static final int VOLUME_REPEAT_START_DELAY_MS = 350;
    private static final int VOLUME_REPEAT_INTERVAL_MS = 130;
    private static final int LYRIC_MODE_FOLLOW = 0;
    private static final int LYRIC_MODE_BLOCK = 1;
    private static final String QUALITY_TIER_UNAVAILABLE = "暂无";
    private static final String QUALITY_TIER_DOWNLOADED = "已下载";
    // 下载场景：当前会员等级（dlLevel）不够，选了也只会被接口降级成低码率
    private static final String QUALITY_TIER_LOCKED = "需会员";
    // 官方「当前歌曲音质」面板把档位分成黑胶 VIP 与黑胶 SVIP 两档，
    // 超出当前账号 plLevel/dlLevel 的档位分别标「需 VIP」「需 SVIP」并置灰
    private static final String QUALITY_TIER_NEED_VIP = "需 VIP";
    private static final String QUALITY_TIER_NEED_SVIP = "需 SVIP";

    private TextView tvSongName;
    private TextView tvArtist;
    private TextView tvPlayerVipBadge;
    private MaterialButton btnPlay;
    private MaterialButton btnFuncMore;
    private Slider seekBar;
    private View chorusMarkerDot;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private MusicPlayerManager playerManager;
    private FavoritesManager favoritesManager;
    private AudioManager audioManager;
    private final Handler seekHandler = new Handler();
    private boolean isUserSeeking = false;
    private boolean serviceStarted = false;
    /** 只有主界面在前台时才允许进度条轮询；退到后台/锁屏立刻停，避免常驻唤醒。 */
    private boolean uiResumed = false;

    // Playlist indicator (top-left)
    private MaterialButton btnPlaylistIndicator;
    private MaterialButton btnPlayMode;

    // Song ids whose fee detail lookup was already requested this session
    private final java.util.Set<Long> feeEnrichRequested = new java.util.HashSet<>();

    // Functions overlay
    private FrameLayout overlayContainer;
    private Handler overlayTimerHandler;
    private Runnable overlayTimerRunnable;

    // Volume indicator
    private View volumeIndicator;
    private com.google.android.material.progressindicator.LinearProgressIndicator volumeProgressBar;
    private TextView volumePercentView;
    private final Handler volumeHandler = new Handler();
    // 长按连续调节音量
    private final Handler volumeRepeatHandler = new Handler();
    private Runnable volumeRepeatRunnable;
    private boolean volumeRepeatUp;
    // 任何来源（手表按键 / 实体键 / 蓝牙耳机）改了等级都刷新音量浮层
    private AppVolumeManager.LevelChangeListener appVolumeListener;

    // Headphone volume protection: confirmed once above the safe threshold,
    // reset when volume drops back below it.
    private boolean volumeSafeConfirmed = false;

    // Song id whose cover is currently being fetched for the background (-1 = none)
    private long coverFetchInFlightId = -1;

    // Activity-level gesture detector for swipe handling
    private GestureDetector activityGestureDetector;

    // Page indicator (player/lyrics tabs at bottom)
    private android.widget.LinearLayout pageIndicatorLayout;
    private android.widget.ImageView ivDotPlayer;
    private android.widget.ImageView ivDotLyrics;

    // Main player content view - used to slide left/right when paging lyrics
    private View mainPlayerContentView;

    // Lyrics overlay state
    private boolean lyricsOverlayShowing = false;
    // Song id whose lyrics are currently parsed in memory; lyrics reload only
    // when this does not match the song being shown (-1 = no cache).
    private long lyricsCacheSongId = -1;
    private final java.util.List<LyricLine> lyricLines = new java.util.ArrayList<>();
    private final java.util.List<TextView> lyricViews = new java.util.ArrayList<>();
    private int currentHighlightIndex = -1;
    private ScrollView lyricsScrollView;
    private LinearLayout lyricsContainer;
    private final Handler lyricsScrollHandler = new Handler();
    private Runnable lyricsScrollRunnable;
    private GestureDetector lyricsGestureDetector;
    private int lyricScrollMode = 0;
    private int lyricResumeIntervalMs = 3000;
    private boolean lyricsUserScrolled = false;
    private long lyricsLastUserScrollTime = 0L;
    private boolean lyricsTouchStartedInScrollView = false;
    private float lyricsTouchDownRawX = 0f;
    private float lyricsTouchDownRawY = 0f;
    private int lyricsTouchSlop = 0;
    private TextView tvLyricsSongLabel;
    private TextView tvLyricsTimeRef;
    // Translation lyrics
    private String currentTlyricText; // Raw translated LRC text for current song
    private String currentRomalrcText; // Raw romanization LRC text for current song
    private int translateMode; // 0=off, 1=translation (意), 2=romanization (音)
    private MaterialButton btnTranslationToggle; // Toggle button in lyrics overlay
    private final java.util.Map<Long, String> translationMap = new java.util.HashMap<>(); // timeMs -> translated text
    private final java.util.Map<Long, String> romanizationMap = new java.util.HashMap<>(); // timeMs -> romanization text
    private long currentChorusSongId = -1L;
    private long currentChorusStartMs = -1L;
    private long currentChorusEndMs = -1L;
    private long chorusLoadingSongId = -1L;
    private boolean currentChorusLoaded = false;

    private static class LyricLine {
        long timeMs;
        String text;
        String translation; // Optional translated text
        String romanization; // Optional romanization (音译) text
        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainPlayerContentView = findViewById(R.id.main_player_layout);
        BackgroundUtil.applyBackground(this,
                getWindow().getDecorView().findViewById(android.R.id.content));

        // Initialize file logging
        MusicLog.init(new File("/sdcard/163Music"));

        tvSongName = findViewById(R.id.tv_song_name);
        tvArtist = findViewById(R.id.tv_artist);
        tvPlayerVipBadge = findViewById(R.id.tv_player_vip_badge);
        btnPlay = findViewById(R.id.btn_play);
        btnFuncMore = findViewById(R.id.btn_favorite);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        MaterialButton btnPrev = findViewById(R.id.btn_prev);
        MaterialButton btnNext = findViewById(R.id.btn_next);
        MaterialButton btnVolDown = findViewById(R.id.btn_vol_down);
        MaterialButton btnVolUp = findViewById(R.id.btn_vol_up);
        MaterialButton btnMore = findViewById(R.id.btn_more);
        btnPlaylistIndicator = findViewById(R.id.btn_playlist_indicator);
        btnPlayMode = findViewById(R.id.btn_play_mode);
        btnPlayMode.setOnClickListener(v -> cyclePlayMode());
        ensureChorusMarkerDot();

        playerManager = MusicPlayerManager.getInstance();
        playerManager.setContext(this);
        favoritesManager = new FavoritesManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 首次进入新版本时提示一次：音量已改为应用内精细分级
        SharedPreferences volumePrefs = getSharedPreferences("music163_app_volume", MODE_PRIVATE);
        if (!volumePrefs.getBoolean("volume_mode_hint_shown", false)) {
            volumePrefs.edit().putBoolean("volume_mode_hint_shown", true).apply();
            Toast.makeText(this, "音量已改为应用内 " + AppVolumeManager.MAX_LEVEL + " 级精细调节",
                    Toast.LENGTH_LONG).show();
        }

        // 音量是全局单一等级：不管从手表按键、实体键还是蓝牙耳机改的，
        // 这里都同步刷新指示条，保证两边显示的是同一个范围。
        AppVolumeManager volumeManager = AppVolumeManager.get();
        if (volumeManager != null) {
            appVolumeListener = level -> showVolumeIndicator();
            volumeManager.addLevelChangeListener(appVolumeListener);
        }

        // Load saved cookie
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("cookie", "");
        playerManager.setCookie(cookie);

        // Apply keep screen on setting
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Song alias display switch (default on)
        Song.setAliasDisplayEnabled(prefs.getBoolean("display_song_alias", true));

        // Load or generate persistent device ID
        String savedDeviceId = prefs.getString("device_id", "");
        if (savedDeviceId.isEmpty()) {
            savedDeviceId = MusicApiHelper.getDeviceId();
            prefs.edit().putString("device_id", savedDeviceId).apply();
        }
        MusicApiHelper.setDeviceId(savedDeviceId);

        // Load saved play mode
        String playModeStr = prefs.getString("play_mode", "LIST_LOOP");
        try {
            playerManager.setPlayMode(MusicPlayerManager.PlayMode.valueOf(playModeStr));
        } catch (Exception e) {
            playerManager.setPlayMode(MusicPlayerManager.PlayMode.LIST_LOOP);
        }

        // Load saved speed mode (0=音调不变, 1=音调改变但速度不变, 2=音调改变且速度改变)
        playerManager.setSpeedMode(prefs.getInt("speed_mode", 0));

        // Enable marquee
        tvSongName.setSelected(true);
        tvArtist.setSelected(true);

        btnPlay.setOnClickListener(v -> {
            if (playerManager.isPlaying()) {
                playerManager.pause();
            } else if (playerManager.getCurrentSong() != null) {
                if (playerManager.getDuration() > 0) {
                    // Song was loaded but paused, resume it
                    playerManager.resume();
                } else {
                    // Song info restored but never played, start playback
                    playerManager.playCurrent();
                }
            }
        });

        btnPrev.setOnClickListener(v -> playerManager.previous());
        btnNext.setOnClickListener(v -> playerManager.next());

        btnVolDown.setOnClickListener(v -> adjustVolumeDown());
        btnVolUp.setOnClickListener(v -> requestVolumeUp());
        attachVolumeRepeat(btnVolUp, true);
        attachVolumeRepeat(btnVolDown, false);

        // Changed: "more functions" overlay instead of toggle favorite
        btnFuncMore.setOnClickListener(v -> showFunctionsOverlay());

        seekBar.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    tvCurrentTime.setText(formatTime((int) ((long) value * duration / 1000)));
                }
            }
        });
        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    int seekPos = (int) ((long) slider.getValue() * duration / 1000);
                    playerManager.seekTo(seekPos);
                }
                isUserSeeking = false;
            }
        });

        btnMore.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MoreActivity.class)));

        // Playlist indicator: click to open playlist detail
        btnPlaylistIndicator.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (playerManager.hasSourcePlaylist()) {
                Intent plIntent = new Intent(MainActivity.this, PlaylistDetailActivity.class);
                plIntent.putExtra("playlist_id", playerManager.getSourcePlaylistId());
                plIntent.putExtra("playlist_name", playerManager.getSourcePlaylistName());
                plIntent.putExtra("track_count", playerManager.getSourcePlaylistTrackCount());
                plIntent.putExtra("creator", playerManager.getSourcePlaylistCreator());
                plIntent.putExtra("creator_user_id", playerManager.getSourcePlaylistCreatorUserId());
                plIntent.putExtra("is_liked_playlist", playerManager.getSourcePlaylistIsLiked());
                startActivity(plIntent);
            }
        });

        playerManager.setCallback(this);

        // Restore last played song (display only, no auto-play)
        if (playerManager.getCurrentSong() == null) {
            playerManager.restorePlaybackState();
        }

        updateUI();


        // Request storage permission for saving favorites to /sdcard/163Music/
        requestStoragePermission();

        lyricsTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        // Activity-level gesture detector:
        // - Right swipe: dismiss overlay if one is open; exit app on main player screen
        // - Left swipe: open lyrics overlay (only when no overlay is showing)
        activityGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = Math.abs(e2.getY() - e1.getY());

                if (Math.abs(diffX) > 80 && diffY < 200 && Math.abs(velocityX) > 200) {
                    if (diffX > 0) {
                        // Right swipe: dismiss overlay if open; exit app on main screen
                        if (overlayContainer != null || lyricsOverlayShowing) {
                            dismissOverlay();
                        } else {
                            finish();
                        }
                        return true;
                    } else {
                        // Left swipe: show lyrics (only when no overlay is showing)
                        if (overlayContainer == null && !lyricsOverlayShowing) {
                            showLyricsOverlay();
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        // Create persistent page indicator (player/lyrics) at the bottom
        ensurePageIndicator();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        handleLyricsOverlayTouch(event);
        if (activityGestureDetector != null) {
            activityGestureDetector.onTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    private void handleLyricsOverlayTouch(MotionEvent event) {
        if (!lyricsOverlayShowing || lyricsScrollView == null || lyricLines.isEmpty()) {
            return;
        }

        if (lyricsGestureDetector != null) {
            lyricsGestureDetector.onTouchEvent(event);
        }

        float rawX = event.getRawX();
        float rawY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lyricsTouchStartedInScrollView = isPointInsideView(rawX, rawY, lyricsScrollView);
                lyricsTouchDownRawX = rawX;
                lyricsTouchDownRawY = rawY;
                break;
            case MotionEvent.ACTION_MOVE:
                if (lyricScrollMode == LYRIC_MODE_BLOCK && lyricsTouchStartedInScrollView) {
                    float diffX = Math.abs(rawX - lyricsTouchDownRawX);
                    float diffY = Math.abs(rawY - lyricsTouchDownRawY);
                    if (diffY > lyricsTouchSlop && diffY >= diffX) {
                        lyricsUserScrolled = true;
                        lyricsLastUserScrollTime = System.currentTimeMillis();
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                lyricsTouchStartedInScrollView = false;
                break;
            default:
                break;
        }
    }

    private boolean isPointInsideView(float rawX, float rawY, View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private int findOverlayLyricIndexAtRawY(float rawY) {
        if (lyricsContainer == null) {
            return -1;
        }
        int nearestIndex = -1;
        float nearestDistance = Float.MAX_VALUE;
        int[] location = new int[2];
        for (int i = 0; i < lyricsContainer.getChildCount() && i < lyricLines.size(); i++) {
            View row = lyricsContainer.getChildAt(i);
            if (row == null || row.getVisibility() != View.VISIBLE) {
                continue;
            }
            row.getLocationOnScreen(location);
            float top = location[1];
            float bottom = top + row.getHeight();
            if (rawY >= top && rawY <= bottom) {
                return i;
            }
            float center = top + row.getHeight() / 2f;
            float distance = Math.abs(rawY - center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private void scrollOverlayToLine(int index) {
        if (lyricsScrollView == null || lyricsContainer == null
                || index < 0 || index >= lyricsContainer.getChildCount()) {
            return;
        }
        View row = lyricsContainer.getChildAt(index);
        if (row == null) {
            return;
        }
        row.post(() -> {
            if (lyricsScrollView == null) {
                return;
            }
            int scrollViewHeight = lyricsScrollView.getHeight();
            int targetTop = row.getTop();
            int targetHeight = row.getHeight();
            int scrollTo = targetTop - (scrollViewHeight / 2) + (targetHeight / 2);
            lyricsScrollView.smoothScrollTo(0, Math.max(0, scrollTo));
        });
    }

    private void clearCurrentLyricHighlight() {
        if (currentHighlightIndex >= 0 && currentHighlightIndex < lyricViews.size()) {
            TextView currentView = lyricViews.get(currentHighlightIndex);
            currentView.setTextColor(getResources().getColor(R.color.text_secondary));
            currentView.setTextSize(13);
        }
        currentHighlightIndex = -1;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    }, STORAGE_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiResumed = true;
        playerManager.setCallback(this);
        // Reload settings in case they changed
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        playerManager.setCookie(prefs.getString("cookie", ""));
        String playModeStr = prefs.getString("play_mode", "LIST_LOOP");
        try {
            playerManager.setPlayMode(MusicPlayerManager.PlayMode.valueOf(playModeStr));
        } catch (Exception e) {
            playerManager.setPlayMode(MusicPlayerManager.PlayMode.LIST_LOOP);
        }
        // 通知栏上的「播放顺序」按键也会改模式，回到界面时同步图标
        updatePlayModeButton();
        // Reapply keep screen on setting
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // Reload speed mode setting
        playerManager.setSpeedMode(prefs.getInt("speed_mode", 0));
        // Reapply custom background
        BackgroundUtil.applyBackground(this,
                getWindow().getDecorView().findViewById(android.R.id.content));
        // Keep the cover background in sync with the current song
        updateCoverBackground();
        // Preload cloud liked IDs cache so overlay shows correct favorite state
        if (prefs.getBoolean("fav_mode_cloud", false)) {
            refreshCloudLikedIds();
        }
        updateUI();
        if (playerManager.isPlaying()) {
            startSeekBarUpdate();
        }
        // Restart lyrics scroll sync if lyrics overlay is visible
        if (lyricsOverlayShowing && !lyricLines.isEmpty() && tvLyricsTimeRef != null) {
            startLyricsScrollSync(tvLyricsTimeRef);
        }
    }

    // ==================== Functions Overlay ====================

    private void showFunctionsOverlay() {
        Song song = playerManager.getCurrentSong();
        if (song == null) {
            Toast.makeText(this, "暂无歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create overlay container
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000); // Gray mask

        // Swipe right to dismiss + click to dismiss
        addSwipeToDismiss(overlayContainer);

        // Content layout - centered, scrollable
        ScrollView scrollView = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.gravity = Gravity.CENTER;
        scrollView.setLayoutParams(scrollParams);
        scrollView.setOnClickListener(v -> { /* consume click */ });

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Title bar with close button on the right
        contentLayout.addView(createOverlayTitleBar("更多功能"));

        // Function grid - 2 per row
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Row 1: 收藏 + 下载
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        boolean isCloudMode = prefs.getBoolean("fav_mode_cloud", false);
        boolean isFav;
        if (isCloudMode) {
            // In cloud mode, check cached cloud liked IDs
            isFav = isCloudLiked(song.getId());
        } else {
            isFav = favoritesManager.isFavorite(song.getId());
        }
        row1.addView(createFuncItem(isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border,
                isFav ? "取消收藏" : "收藏",
                v -> onFuncFavorite(song)));
        row1.addView(createFuncItem(R.drawable.ic_get_app, "下载",
                v -> onFuncDownload(song)));
        contentLayout.addView(row1);

        // Row 2: 定时关闭
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(4), 0, 0);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Sleep timer - show remaining time if active, with live updates
        MaterialButton timerItem = createFuncItem(R.drawable.ic_timer,
                playerManager.isSleepTimerActive() ? "定时..." : "定时关闭",
                v -> onFuncSleepTimer());
        row2.addView(timerItem);

        // 定时关闭与倍速播放必须同一行两个：删掉「设为铃声 / 播放模式」后
        // 若各占一行，weight=1 会让它们各自撑满一整行，看起来像两列居中单按钮。
        float currentSpeed = playerManager.getPlaybackSpeed();
        String speedLabel = currentSpeed == 1.0f ? "倍速播放" : String.format("%.1fx", currentSpeed);
        row2.addView(createFuncItem(R.drawable.ic_speed, speedLabel,
                v -> onFuncPlaybackSpeed()));

        final TextView timerLabelView = timerItem;
        if (playerManager.isSleepTimerActive()) {
            overlayTimerHandler = new Handler();
            overlayTimerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (overlayContainer == null) return;
                    long remainMs = playerManager.getSleepTimerRemainingMs();
                    if (remainMs > 0) {
                        int totalSec = (int) (remainMs / 1000);
                        int min = totalSec / 60;
                        int sec = totalSec % 60;
                        timerLabelView.setText(String.format("定时 %d:%02d", min, sec));
                        overlayTimerHandler.postDelayed(this, 1000);
                    } else {
                        timerLabelView.setText("定时关闭");
                    }
                }
            };
            overlayTimerRunnable.run();
        }
        contentLayout.addView(row2);

        // Row 3: 音乐信息 + 评论
        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setGravity(Gravity.CENTER);
        row4.setPadding(0, dp(4), 0, 0);
        row4.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row4.addView(createFuncItem(R.drawable.ic_info, "音乐信息",
                v -> onFuncSongInfo(song)));
        row4.addView(createFuncItem(R.drawable.ic_comment, "评论",
                v -> onFuncComments(song)));
        contentLayout.addView(row4);

        // Row 5: 播放列表 + 添加到歌单
        LinearLayout row5 = new LinearLayout(this);
        row5.setOrientation(LinearLayout.HORIZONTAL);
        row5.setGravity(Gravity.CENTER);
        row5.setPadding(0, dp(4), 0, 0);
        row5.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row5.addView(createFuncItem(R.drawable.ic_queue_music, "播放列表",
                v -> onFuncShowPlaylist()));
        row5.addView(createFuncItem(R.drawable.ic_add_box, "添加到歌单",
                v -> onFuncAddToPlaylist(song)));
        contentLayout.addView(row5);

        // Row 6: 音质
        LinearLayout row6 = new LinearLayout(this);
        row6.setOrientation(LinearLayout.HORIZONTAL);
        row6.setGravity(Gravity.CENTER);
        row6.setPadding(0, dp(4), 0, 0);
        row6.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        SharedPreferences qualityPrefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String curQuality = getCurrentQualityLabel(song,
                qualityPrefs.getString("preferred_quality", "exhigh"));
        String qualityLabel = "音质: " + getQualityShortName(curQuality);
        row6.addView(createFuncItem(R.drawable.ic_audio_quality, qualityLabel,
                v -> onFuncSelectQuality()));
        row6.addView(createFuncItem(R.drawable.ic_info, "播放器信息",
                v -> onFuncPlayerInfo()));
        contentLayout.addView(row6);

        // Row 7: 查看专辑
        if (song.getId() > 0) {
            LinearLayout row7 = new LinearLayout(this);
            row7.setOrientation(LinearLayout.HORIZONTAL);
            row7.setGravity(Gravity.START);
            row7.setPadding(0, dp(4), 0, 0);
            row7.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row7.addView(createFuncItem(R.drawable.ic_album, "查看专辑",
                    v -> onFuncViewAlbum(song)));
            contentLayout.addView(row7);
        }

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);
    }

    /**
     * Create a title bar with centered title and close button on the right.
     * Used in all overlay panels.
     */
    private FrameLayout createOverlayTitleBar(String titleText) {
        FrameLayout titleBar = new FrameLayout(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36));
        barParams.bottomMargin = dp(4);
        titleBar.setLayoutParams(barParams);

        // Centered title
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(11);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        titleParams.setMarginEnd(dp(36));
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        // Close button on the right
        MaterialButton btnClose = new MaterialButton(this);
        btnClose.setIconResource(R.drawable.ic_close);
        btnClose.setIconTint(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_primary)));
        btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        btnClose.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnClose.setIconSize(dp(18));
        btnClose.setIconPadding(0);
        btnClose.setInsetTop(0);
        btnClose.setInsetBottom(0);
        btnClose.setMinWidth(0);
        btnClose.setMinimumWidth(0);
        btnClose.setMinHeight(0);
        btnClose.setMinimumHeight(0);
        btnClose.setPadding(0, 0, 0, 0);
        btnClose.setCornerRadius(dp(24));
        btnClose.setContentDescription("关闭");
        int closeSize = dp(36);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSize, closeSize);
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        btnClose.setLayoutParams(closeParams);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        btnClose.setOnClickListener(v -> dismissOverlay());
        titleBar.addView(btnClose);

        return titleBar;
    }

    /**
     * Add swipe-right-to-dismiss gesture to an overlay container.
     */
    private void addSwipeToDismiss(FrameLayout container) {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true; // Required for onFling to work
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = Math.abs(e2.getY() - e1.getY());
                    if (diffX > 80 && diffY < 200 && Math.abs(velocityX) > 200) {
                        dismissOverlay();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                dismissOverlay();
                return true;
            }
        });
        container.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private MaterialButton createFuncItem(int iconRes, String label, View.OnClickListener listener) {
        MaterialButton item = new MaterialButton(this);
        item.setText(label);
        item.setTextColor(getResources().getColor(R.color.on_primary_container));
        item.setTextSize(11);
        item.setAllCaps(false);
        item.setSingleLine(true);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.setIconResource(iconRes);
        item.setIconTint(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.on_primary_container)));
        item.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_TOP);
        item.setIconSize(dp(22));
        item.setIconPadding(dp(3));
        item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primary_container)));
        item.setCornerRadius(dp(16));
        item.setInsetTop(0);
        item.setInsetBottom(0);
        item.setMinWidth(0);
        item.setMinimumWidth(0);
        item.setMinHeight(0);
        item.setMinimumHeight(0);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, dp(64), 1);
        itemParams.setMargins(dp(2), 0, dp(2), 0);
        item.setLayoutParams(itemParams);
        item.setOnClickListener(listener);
        return item;
    }

    private void dismissOverlay() {
        if (overlayTimerHandler != null) {
            overlayTimerHandler.removeCallbacksAndMessages(null);
            overlayTimerHandler = null;
            overlayTimerRunnable = null;
        }
        if (lyricsOverlayShowing && overlayContainer != null) {
            // Animate lyrics overlay sliding out to the right, player sliding back from left
            stopLyricsScrollSync();
            refreshChorusMarkerVisibility();
            updatePageIndicator(false);
            final FrameLayout container = overlayContainer;
            overlayContainer = null;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            if (mainPlayerContentView != null) {
                mainPlayerContentView.animate().translationX(0).setDuration(250).start();
            }
            container.animate().translationX(screenWidth).setDuration(250)
                    .withEndAction(() -> {
                        FrameLayout rootView = (FrameLayout) getWindow().getDecorView()
                                .findViewById(android.R.id.content);
                        rootView.removeView(container);
                    }).start();
        } else {
            if (lyricsOverlayShowing) {
                stopLyricsScrollSync();
                refreshChorusMarkerVisibility();
                updatePageIndicator(false);
                if (mainPlayerContentView != null) {
                    mainPlayerContentView.animate().translationX(0).setDuration(250).start();
                }
            }
            if (overlayContainer != null) {
                FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
                rootView.removeView(overlayContainer);
                overlayContainer = null;
            }
        }
    }

    /**
     * 拦截实体音量键：优先走应用内精细音量，不再让系统按 6 级跳变。
     * 这样无论用手表侧键还是界面上的 +/-，每按一次都只变化 1.2 dB（40 级）。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int repeat = event.getRepeatCount();
                if (repeat == 0) {
                    // 第一次按下走完整流程（含耳机听力保护确认）
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        requestVolumeUp();
                    } else {
                        adjustVolumeDown();
                    }
                } else if (repeat % 2 == 0) {
                    // 按住不放时连续步进，每两帧走一级（约 100ms 一级）
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        adjustVolumeUp();
                    } else {
                        adjustVolumeDown();
                    }
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isHeadphonesConnected() {
        try {
            return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 音量 + 入口：应用内音量不再跟随系统 6 级粗调，
     * 但保留耳机场景下的听力保护确认。
     */
    private void requestVolumeUp() {
        AppVolumeManager volumeManager = AppVolumeManager.get();
        if (volumeManager == null) {
            return;
        }
        if (volumeManager.getLevel() >= AppVolumeManager.MAX_LEVEL) {
            // 已经最大，仍然显示一次指示器作为反馈
            showVolumeIndicator();
            return;
        }
        if (!isHeadphonesConnected()) {
            volumeSafeConfirmed = false;
            adjustVolumeUp();
            return;
        }
        int percent = volumeManager.getPercent();
        if (percent < SAFE_VOLUME_PERCENT) {
            volumeSafeConfirmed = false;
            adjustVolumeUp();
        } else if (!volumeSafeConfirmed) {
            WatchConfirmDialog.show(this, "音量保护",
                    "当前音量已达 " + percent + "%，继续调大可能损伤听力。确定继续调大吗？",
                    () -> {
                        volumeSafeConfirmed = true;
                        adjustVolumeUp();
                    },
                    new WatchConfirmDialog.Options(0xFF1C1C1E, 0xFFF2F2F7, true));
        } else {
            adjustVolumeUp();
        }
    }

    /**
     * 给音量按钮挂上长按连发。40 级音量靠一下一下点太累，
     * 按住不放就连续步进，松手立即停止。
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void attachVolumeRepeat(View button, boolean up) {
        if (button == null) {
            return;
        }
        button.setOnLongClickListener(v -> {
            startVolumeRepeat(up);
            return true;
        });
        button.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                stopVolumeRepeat();
            }
            return false;
        });
    }

    private void startVolumeRepeat(boolean up) {
        stopVolumeRepeat();
        volumeRepeatUp = up;
        volumeRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (volumeRepeatUp) {
                    adjustVolumeUp();
                } else {
                    adjustVolumeDown();
                }
                volumeRepeatHandler.postDelayed(this, VOLUME_REPEAT_INTERVAL_MS);
            }
        };
        volumeRepeatHandler.postDelayed(volumeRepeatRunnable, VOLUME_REPEAT_START_DELAY_MS);
    }

    private void stopVolumeRepeat() {
        volumeRepeatHandler.removeCallbacksAndMessages(null);
        volumeRepeatRunnable = null;
    }

    private void adjustVolumeUp() {
        AppVolumeManager volumeManager = AppVolumeManager.get();
        if (volumeManager != null) {
            volumeManager.stepUp();
            playerManager.applyAppVolume();
        }
        showVolumeIndicator();
    }

    private void adjustVolumeDown() {
        AppVolumeManager volumeManager = AppVolumeManager.get();
        if (volumeManager != null) {
            volumeManager.stepDown();
            playerManager.applyAppVolume();
        }
        showVolumeIndicator();
    }

    private void showVolumeIndicator() {
        AppVolumeManager volumeManager = AppVolumeManager.get();
        if (volumeManager == null) {
            return;
        }
        int current = volumeManager.getLevel();
        int max = volumeManager.getMaxLevel();
        boolean hasVolumeInfo = max > 0;
        int percent = volumeManager.getPercent();
        ensureVolumeIndicator(hasVolumeInfo, current, max, percent);
        updateVolumeIndicator(hasVolumeInfo, current, max, percent);

        volumeHandler.removeCallbacksAndMessages(null);
        volumeHandler.postDelayed(this::dismissVolumeIndicator, 1500);
    }

    private void ensureVolumeIndicator(boolean hasVolumeInfo, int current, int max, int percent) {
        if (volumeIndicator != null) {
            return;
        }

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        int availableWidth = Math.max(
                getResources().getDisplayMetrics().widthPixels - dp(VOLUME_INDICATOR_SIDE_MARGIN_DP * 2),
                dp(VOLUME_INDICATOR_MIN_WIDTH_DP));
        int popupWidth = availableWidth > dp(VOLUME_INDICATOR_COMPACT_BREAKPOINT_DP)
                ? Math.min(availableWidth, dp(VOLUME_INDICATOR_MAX_WIDTH_DP))
                : availableWidth;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_volume_indicator));
        card.setElevation(dp(6));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setClickable(false);
        card.setFocusable(false);

        LinearProgressIndicator progressBar = new LinearProgressIndicator(this);
        progressBar.setTrackThickness(dp(8));
        progressBar.setTrackCornerRadius(dp(4));
        progressBar.setIndicatorColor(ContextCompat.getColor(this, R.color.colorPrimary));
        progressBar.setTrackColor(ContextCompat.getColor(this, R.color.surface_variant));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(hasVolumeInfo ? max : 1);
        progressBar.setProgress(hasVolumeInfo ? current : 0);

        TextView percentView = new TextView(this);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        percentParams.leftMargin = dp(10);
        percentView.setLayoutParams(percentParams);
        percentView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        percentView.setTextSize(13);
        percentView.setTypeface(percentView.getTypeface(), android.graphics.Typeface.BOLD);
        percentView.setText(hasVolumeInfo ? (percent + "%") : "--");

        card.addView(progressBar);
        card.addView(percentView);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                popupWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(VOLUME_INDICATOR_TOP_MARGIN_DP);
        card.setLayoutParams(params);

        rootView.addView(card);
        card.setAlpha(0f);
        card.setScaleX(VOLUME_INDICATOR_INITIAL_SCALE);
        card.setScaleY(VOLUME_INDICATOR_INITIAL_SCALE);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(VOLUME_INDICATOR_ANIM_DURATION_MS)
                .start();

        volumeIndicator = card;
        volumeProgressBar = progressBar;
        volumePercentView = percentView;
    }

    private void updateVolumeIndicator(boolean hasVolumeInfo, int current, int max, int percent) {
        if (volumeProgressBar == null || volumePercentView == null) {
            return;
        }
        volumeProgressBar.setMax(hasVolumeInfo ? max : 1);
        volumeProgressBar.setProgress(hasVolumeInfo ? current : 0);
        volumePercentView.setText(hasVolumeInfo ? (percent + "%") : "--");
    }

    private void dismissVolumeIndicator() {
        if (volumeIndicator == null) {
            return;
        }
        if (volumeIndicator.getParent() instanceof FrameLayout) {
            ((FrameLayout) volumeIndicator.getParent()).removeView(volumeIndicator);
        }
        volumeIndicator = null;
        volumeProgressBar = null;
        volumePercentView = null;
    }

    private void ensureChorusLoaded(Song song) {
        if (song == null) {
            clearChorusInfo();
            return;
        }
        if (song.getId() == currentChorusSongId && currentChorusLoaded) {
            updateChorusMarker(playerManager.getDuration());
            return;
        }
        if (song.getId() == chorusLoadingSongId) {
            return;
        }
        currentChorusSongId = song.getId();
        currentChorusStartMs = -1L;
        currentChorusEndMs = -1L;
        currentChorusLoaded = false;
        chorusLoadingSongId = song.getId();
        updateChorusMarker(playerManager.getDuration());
        MusicApiHelper.getSongChorus(song.getId(), playerManager.getCookie(), new MusicApiHelper.ChorusCallback() {
            @Override
            public void onResult(MusicApiHelper.ChorusInfo chorusInfo) {
                Song currentSong = playerManager.getCurrentSong();
                if (chorusLoadingSongId == song.getId()) {
                    chorusLoadingSongId = -1L;
                }
                if (currentSong == null || currentSong.getId() != song.getId()) {
                    return;
                }
                if (chorusInfo == null) {
                    currentChorusStartMs = -1L;
                    currentChorusEndMs = -1L;
                } else {
                    currentChorusStartMs = chorusInfo.startMs;
                    currentChorusEndMs = chorusInfo.endMs;
                }
                currentChorusLoaded = true;
                updateChorusMarker(playerManager.getDuration());
            }

            @Override
            public void onError(String message) {
                Song currentSong = playerManager.getCurrentSong();
                if (chorusLoadingSongId == song.getId()) {
                    chorusLoadingSongId = -1L;
                }
                if (currentSong == null || currentSong.getId() != song.getId()) {
                    return;
                }
                currentChorusStartMs = -1L;
                currentChorusEndMs = -1L;
                currentChorusLoaded = true;
                updateChorusMarker(playerManager.getDuration());
            }
        });
    }

    private void clearChorusInfo() {
        currentChorusSongId = -1L;
        currentChorusStartMs = -1L;
        currentChorusEndMs = -1L;
        chorusLoadingSongId = -1L;
        currentChorusLoaded = false;
        updateChorusMarker(0);
    }

    private void ensureChorusMarkerDot() {
        if (chorusMarkerDot != null) {
            return;
        }
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        View dot = new View(this);
        int dotSize = dp(5);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dotSize, dotSize);
        dot.setLayoutParams(params);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(ContextCompat.getColor(this, R.color.colorTertiary));
        dot.setBackground(background);
        dot.setVisibility(View.GONE);
        dot.setClickable(false);
        dot.setFocusable(false);
        rootView.addView(dot);
        chorusMarkerDot = dot;
    }

    private void updateChorusMarker(int durationMs) {
        if (chorusMarkerDot == null) {
            return;
        }
        if (durationMs <= 0 || currentChorusStartMs < 0L) {
            chorusMarkerDot.setVisibility(View.GONE);
            return;
        }
        seekBar.post(() -> positionChorusMarker(durationMs));
    }

    private void positionChorusMarker(int durationMs) {
        if (chorusMarkerDot == null || seekBar == null || durationMs <= 0 || currentChorusStartMs < 0L) {
            return;
        }
        int seekWidth = seekBar.getWidth();
        int seekHeight = seekBar.getHeight();
        if (seekWidth <= 0 || seekHeight <= 0) {
            chorusMarkerDot.setVisibility(View.GONE);
            return;
        }
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        int[] rootLocation = new int[2];
        int[] seekLocation = new int[2];
        rootView.getLocationInWindow(rootLocation);
        seekBar.getLocationInWindow(seekLocation);

        float fraction = Math.min(1f, Math.max(0f, (float) currentChorusStartMs / (float) durationMs));
        // The Slider draws its track inset from the view edges by the thumb
        // clearance (thumb radius plus an 8dp gap, ~14dp total) — unlike the
        // old SeekBar whose track spanned the full padded width. Using the
        // raw view bounds put the chorus marker dot visibly off the track.
        int thumbRadiusPx = seekBar.getThumbRadius();
        int trackInset = thumbRadiusPx + dp(8);
        float trackLeft = seekLocation[0] - rootLocation[0] + trackInset;
        float trackRight = seekLocation[0] - rootLocation[0] + seekWidth - trackInset;
        float markerCenterX = trackLeft + (trackRight - trackLeft) * fraction;
        float markerCenterY = seekLocation[1] - rootLocation[1] + seekHeight / 2f;
        chorusMarkerDot.setX(markerCenterX - chorusMarkerDot.getWidth() / 2f);
        chorusMarkerDot.setY(markerCenterY - chorusMarkerDot.getHeight() / 2f);
        refreshChorusMarkerVisibility();
    }

    /**
     * The chorus marker dot belongs to the player progress bar. It only hides
     * while the lyrics page (transparent overlay) covers the player — pausing
     * playback keeps it visible.
     */
    private void refreshChorusMarkerVisibility() {
        if (chorusMarkerDot == null) {
            return;
        }
        boolean show = hasChorusRange() && !lyricsOverlayShowing;
        chorusMarkerDot.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean hasChorusRange() {
        return currentChorusStartMs >= 0L && currentChorusEndMs > currentChorusStartMs;
    }

    private int millisToFloorSeconds(long ms) {
        return (int) Math.max(0L, ms / 1000L);
    }

    private int millisToCeilSeconds(long ms) {
        return (int) Math.max(0L, (ms + 999L) / 1000L);
    }

    private boolean applySmartClipRange(int totalSec,
                                        int[] startSec,
                                        int[] endSec,
                                        Slider sbStart,
                                        Slider sbEnd,
                                        TextView tvStart,
                                        TextView tvEnd,
                                        Runnable updateDuration,
                                        @androidx.annotation.Nullable TextView tvChorusRange) {
        if (!hasChorusRange()) {
            Toast.makeText(this, "暂无高潮数据", Toast.LENGTH_SHORT).show();
            return false;
        }
        int smartStart = millisToFloorSeconds(currentChorusStartMs);
        int smartEnd = Math.min(totalSec, millisToCeilSeconds(currentChorusEndMs));
        if (smartEnd <= smartStart) {
            smartEnd = Math.min(totalSec, smartStart + 1);
        }
        if (smartEnd <= smartStart) {
            Toast.makeText(this, "高潮范围无效", Toast.LENGTH_SHORT).show();
            return false;
        }
        startSec[0] = smartStart;
        endSec[0] = smartEnd;
        sbStart.setValue((float) smartStart);
        sbEnd.setValue((float) smartEnd);
        tvStart.setText("起始: " + smartStart + "s");
        tvEnd.setText("结束: " + smartEnd + "s");
        if (tvChorusRange != null) {
            tvChorusRange.setText("高潮: " + smartStart + "s - " + smartEnd + "s");
        }
        updateDuration.run();
        return true;
    }

    private MaterialButton createOverlayActionButton(String text, int backgroundColor, boolean primary) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setTextColor(androidx.core.graphics.ColorUtils.calculateLuminance(backgroundColor) > 0.4
                ? 0xFF1C1C1E : ContextCompat.getColor(this, R.color.text_primary));
        button.setCornerRadius(dp(24));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(8), dp(10), dp(8), dp(10));
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        if (!primary) {
            button.setStrokeWidth(0);
        }
        return button;
    }

    private EditText addOverlayNumberField(LinearLayout row, String hint, int inputType) {
        com.google.android.material.textfield.TextInputLayout field =
                new com.google.android.material.textfield.TextInputLayout(this);
        field.setHintEnabled(false);
        field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        fieldParams.setMarginEnd(dp(4));
        row.addView(field, fieldParams);

        com.google.android.material.textfield.TextInputEditText input =
                new com.google.android.material.textfield.TextInputEditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setPadding(dp(8), 0, dp(8), 0);
        field.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        return input;
    }

    private void onFuncFavorite(Song song) {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);

        if (isCloud) {
            // Cloud mode: use API to like/unlike
            boolean isCurrentlyLiked = isCloudLiked(song.getId());
            String cookie = playerManager.getCookie();
            if (cookie == null || cookie.isEmpty()) {
                Toast.makeText(this, "请先登录以使用云端收藏", Toast.LENGTH_SHORT).show();
                dismissOverlay();
                return;
            }
            MusicApiHelper.likeTrack(song.getId(), !isCurrentlyLiked, cookie,
                    new MusicApiHelper.LikeCallback() {
                @Override
                public void onResult(boolean success) {
                    if (success) {
                        // Update cache
                        if (cloudLikedIds == null) {
                            cloudLikedIds = new java.util.HashSet<>();
                        }
                        if (isCurrentlyLiked) {
                            cloudLikedIds.remove(song.getId());
                        } else {
                            cloudLikedIds.add(song.getId());
                        }
                        cloudLikedCacheTime = System.currentTimeMillis();
                        Toast.makeText(MainActivity.this,
                                isCurrentlyLiked ? "已取消云端收藏" : "已云端收藏",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "操作失败: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Local mode
            if (favoritesManager.isFavorite(song.getId())) {
                favoritesManager.removeFavorite(song);
                Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
            } else {
                favoritesManager.addFavorite(song);
                Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
            }
        }
        dismissOverlay();
    }

    private String getDownloadCookieForSong(Song song) {
        return playerManager.getCookie();
    }

    private void onFuncDownload(Song song) {
        dismissOverlay();
        // NetEase songs: show quality selector before downloading
        showDownloadQualityOptions(song);
    }

    private void showDownloadQualityOptions(Song song) {
        showQualityOptionsInternal(false, song, selectedQuality -> {
            String cookie = getDownloadCookieForSong(song);
            Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
            DownloadManager.downloadSong(song, cookie, selectedQuality,
                    new DownloadManager.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    Toast.makeText(MainActivity.this, "下载完成: " + filePath, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void cyclePlayMode() {
        MusicPlayerManager.PlayMode current = playerManager.getPlayMode();
        MusicPlayerManager.PlayMode next;
        switch (current) {
            case LIST_LOOP:
                next = MusicPlayerManager.PlayMode.SINGLE_REPEAT;
                break;
            case SINGLE_REPEAT:
                next = MusicPlayerManager.PlayMode.RANDOM;
                break;
            case RANDOM:
            default:
                next = MusicPlayerManager.PlayMode.LIST_LOOP;
                break;
        }
        playerManager.setPlayMode(next);
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putString("play_mode", next.name()).apply();
        updatePlayModeButton();
        String modeName;
        switch (next) {
            case SINGLE_REPEAT:
                modeName = "单曲循环";
                break;
            case RANDOM:
                modeName = "随机播放";
                break;
            case LIST_LOOP:
            default:
                modeName = "列表循环";
                break;
        }
        Toast.makeText(this, "播放模式: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void onFuncLyrics() {
        dismissOverlay();
        showLyricsOverlay();
    }

    private void onFuncSongInfo(Song song) {
        dismissOverlay();
        Intent intent = new Intent(this, SongInfoActivity.class);
        intent.putExtra("song_id", song.getId());
        intent.putExtra("song_name", song.getDisplayName());
        intent.putExtra("artist_name", song.getArtist());
        intent.putExtra("artist_id", 0L); // Will be extracted from wiki API
        intent.putExtra("cookie", playerManager.getCookie());
        startActivity(intent);
    }

    private void onFuncViewAlbum(Song song) {
        dismissOverlay();
        if (song.getAlbumId() > 0) {
            // Open album detail directly with known albumId
            Intent intent = new Intent(this, AlbumDetailActivity.class);
            intent.putExtra("album_id", song.getAlbumId());
            intent.putExtra("album_name", song.getAlbum());
            intent.putExtra("album_cover_url", song.getCoverUrl());
            startActivity(intent);
        } else {
            // No albumId stored: fetch song detail first to get albumId
            Toast.makeText(this, "正在获取专辑信息...", Toast.LENGTH_SHORT).show();
            String cookie = playerManager.getCookie();
            MusicApiHelper.getSongDetail(song.getId(), cookie, new MusicApiHelper.SongDetailCallback() {
                @Override
                public void onResult(JSONObject songDetail) {
                    JSONObject al = songDetail.optJSONObject("al");
                    if (al == null) al = songDetail.optJSONObject("album");
                    if (al != null) {
                        long albumId = al.optLong("id", 0);
                        String albumName = al.optString("name", song.getAlbum());
                        String albumCoverUrl = al.optString("picUrl", song.getCoverUrl() != null ? song.getCoverUrl() : "");
                        if (albumId > 0) {
                            song.setAlbumId(albumId);
                            Intent intent = new Intent(MainActivity.this, AlbumDetailActivity.class);
                            intent.putExtra("album_id", albumId);
                            intent.putExtra("album_name", albumName);
                            intent.putExtra("album_cover_url", albumCoverUrl);
                            startActivity(intent);
                        } else {
                            Toast.makeText(MainActivity.this, "无法获取专辑信息", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "无法获取专辑信息", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "获取专辑信息失败: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void onFuncComments(Song song) {
        dismissOverlay();
        Intent intent = new Intent(this, CommentActivity.class);
        intent.putExtra("song_id", song.getId());
        intent.putExtra("song_name", song.getDisplayName());
        intent.putExtra("cookie", playerManager.getCookie());
        startActivity(intent);
    }

    private void onFuncPlayerInfo() {
        dismissOverlay();
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        // Same structure as the download/quality dialog: compact centered
        // content on the dim scrim, outer scroll when lines overflow.
        ScrollView outerScroll = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.gravity = Gravity.CENTER;
        outerScroll.setLayoutParams(scrollParams);
        outerScroll.setOnClickListener(v -> { /* consume click */ });

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(20), dp(16), dp(20), dp(16));
        contentLayout.addView(createOverlayTitleBar("播放器信息"));

        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        for (String line : playerManager.getCurrentPlayerInfoLines()) {
            // Identical row structure to the download-quality options:
            // MaterialCardView (14dp radius, elevated surface, 4dp gap) with
            // a big value line and a small secondary label line.
            com.google.android.material.card.MaterialCardView card =
                    QualityPicker.createCard(this, false, false);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setMinimumHeight(dp(48));
            card.addView(row);

            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            int sep = line.indexOf(": ");
            TextView tvValue = new TextView(this);
            tvValue.setTextSize(14);
            tvValue.setTextColor(getResources().getColor(R.color.text_primary));
            TextView tvLabel = null;
            if (sep > 0) {
                tvValue.setText(line.substring(sep + 2));
                tvValue.setSingleLine(true);
                tvValue.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                tvValue.setMarqueeRepeatLimit(-1);
                tvValue.setSelected(true);
                tvLabel = new TextView(this);
                tvLabel.setText(line.substring(0, sep));
                tvLabel.setTextColor(0xFFAEAEB2);
                tvLabel.setTextSize(11);
            } else {
                tvValue.setText(line);
            }
            leftCol.addView(tvValue);
            if (tvLabel != null) {
                leftCol.addView(tvLabel);
            }
            row.addView(leftCol);

            lines.addView(card);
        }

        contentLayout.addView(lines, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        outerScroll.addView(contentLayout);
        overlayContainer.addView(outerScroll);
        rootView.addView(overlayContainer);
    }

    /**
     * Add current song to a user-created playlist (not "我喜欢的音乐").
     * Fetches user playlists, filters to only user-created (non-liked), shows picker.
     */
    private void onFuncAddToPlaylist(Song song) {
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在加载歌单...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.getUid(cookie, new MusicApiHelper.AccountCallback() {
            @Override
            public void onResult(org.json.JSONObject uidJson) {
                long myUid = uidJson.optLong("uid", -1);
                MusicApiHelper.getUserPlaylists(cookie, new MusicApiHelper.UserPlaylistsCallback() {
                    @Override
                    public void onResult(java.util.List<com.qinghe.music163pro.model.PlaylistInfo> playlists) {
                        // Filter: only my created playlists, exclude "我喜欢的音乐"
                        java.util.List<com.qinghe.music163pro.model.PlaylistInfo> eligible = new java.util.ArrayList<>();
                        for (com.qinghe.music163pro.model.PlaylistInfo p : playlists) {
                            if (p.getUserId() == myUid && !p.isLikedPlaylist()) {
                                eligible.add(p);
                            }
                        }
                        if (eligible.isEmpty()) {
                            Toast.makeText(MainActivity.this, "没有可用的自建歌单", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showPlaylistPicker(eligible, song);
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(MainActivity.this, "获取歌单失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "获取用户信息失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPlaylistPicker(java.util.List<com.qinghe.music163pro.model.PlaylistInfo> playlists, Song song) {
        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).getName() + " (" + playlists.get(i).getTrackCount() + "首)";
        }
        WatchUiUtils.showActionDialog(this, "添加到歌单", names, null, (dialog, which) -> {
            com.qinghe.music163pro.model.PlaylistInfo selected = playlists.get(which);
            addSongToPlaylist(song, selected);
        });
    }

    private void addSongToPlaylist(Song song, com.qinghe.music163pro.model.PlaylistInfo playlist) {
        String cookie = playerManager.getCookie();
        MusicApiHelper.playlistTracks("add", playlist.getId(), new long[]{song.getId()},
                cookie, new MusicApiHelper.PlaylistActionCallback() {
            @Override
            public void onResult(boolean success) {
                if (success) {
                    Toast.makeText(MainActivity.this,
                            "已添加到「" + playlist.getName() + "」", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "添加失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== Lyrics Overlay ====================

    /**
     * Show lyrics as an inline overlay on the player screen.
     * Left-swipe on player opens this; right-swipe (via dispatchTouchEvent) closes it.
     */
    private void showLyricsOverlay() {
        Song song = playerManager.getCurrentSong();
        if (song == null) {
            Toast.makeText(this, "暂无歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences lyricPrefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        lyricScrollMode = lyricPrefs.getInt("lyric_scroll_mode", LYRIC_MODE_BLOCK);
        int intervalSec = lyricPrefs.getInt("lyric_resume_interval", 3);
        if (intervalSec < 1) intervalSec = 1;
        lyricResumeIntervalMs = intervalSec * 1000;
        lyricsUserScrolled = false;
        lyricsLastUserScrollTime = 0L;
        lyricsTouchStartedInScrollView = false;
        lyricsGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (lyricScrollMode != LYRIC_MODE_BLOCK
                        || !isPointInsideView(e.getRawX(), e.getRawY(), lyricsScrollView)) {
                    return false;
                }
                int index = findOverlayLyricIndexAtRawY(e.getRawY());
                if (index < 0 || index >= lyricLines.size()) {
                    return false;
                }
                int seekMs = (int) lyricLines.get(index).timeMs;
                playerManager.seekTo(seekMs);
                lyricsUserScrolled = false;
                lyricsLastUserScrollTime = 0L;
                clearCurrentLyricHighlight();
                scrollOverlayToLine(index);
                return true;
            }
        });

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Transparent: the custom background lives on the fixed content root so it
        // stays put while the player/lyrics views slide over it.
        overlayContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        // clickable/focusable makes the full-screen container consume taps that miss
        // its children, while child views still receive their own touch dispatch.
        // dispatchTouchEvent continues to observe the full event stream for gestures.
        overlayContainer.setClickable(true);
        overlayContainer.setFocusable(true);
        // Don't use addSwipeToDismiss - dispatchTouchEvent handles swipe gestures

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Top bar: Song name + translation toggle button
        FrameLayout topBar = new FrameLayout(this);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Song name at top (leave right padding for the toggle button)
        tvLyricsSongLabel = new TextView(this);
        tvLyricsSongLabel.setText(song.getDisplayName() + " - " + song.getArtist());
        tvLyricsSongLabel.setTextColor(0xFFFFFFFF);
        tvLyricsSongLabel.setTextSize(13);
        tvLyricsSongLabel.setGravity(Gravity.CENTER);
        tvLyricsSongLabel.setPadding(dp(30), dp(6), dp(30), dp(4));
        tvLyricsSongLabel.setSingleLine(true);
        tvLyricsSongLabel.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tvLyricsSongLabel.setSelected(true);
        FrameLayout.LayoutParams songLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tvLyricsSongLabel.setLayoutParams(songLabelParams);
        topBar.addView(tvLyricsSongLabel);

        // Translation toggle button (top-right), hidden by default until we know translation exists
        SharedPreferences transPrefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (transPrefs.contains("lyrics_translate_mode")) {
            translateMode = transPrefs.getInt("lyrics_translate_mode", 0);
        } else {
            translateMode = transPrefs.getBoolean("lyrics_translation", false) ? 1 : 0;
        }
        btnTranslationToggle = new MaterialButton(this);
        btnTranslationToggle.setTextSize(12);
        btnTranslationToggle.setAllCaps(false);
        btnTranslationToggle.setMinWidth(0);
        btnTranslationToggle.setMinimumWidth(0);
        btnTranslationToggle.setMinHeight(0);
        btnTranslationToggle.setMinimumHeight(0);
        btnTranslationToggle.setInsetTop(0);
        btnTranslationToggle.setInsetBottom(0);
        btnTranslationToggle.setPadding(dp(10), 0, dp(10), 0);
        btnTranslationToggle.setCornerRadius(dp(16));
        btnTranslationToggle.setVisibility(View.GONE); // Hidden until translation is available
        updateTranslationButton();
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(32));
        toggleParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        toggleParams.setMarginEnd(dp(2));
        btnTranslationToggle.setLayoutParams(toggleParams);
        btnTranslationToggle.setOnClickListener(v -> toggleLyricsTranslation());
        topBar.addView(btnTranslationToggle);

        mainLayout.addView(topBar);

        // Time display - shown below title
        tvLyricsTimeRef = new TextView(this);
        tvLyricsTimeRef.setTextColor(0xFFAEAEB2);
        tvLyricsTimeRef.setTextSize(10);
        tvLyricsTimeRef.setGravity(Gravity.CENTER);
        tvLyricsTimeRef.setPadding(0, dp(2), 0, dp(4));
        tvLyricsTimeRef.setText("");
        mainLayout.addView(tvLyricsTimeRef);

        // Lyrics scroll view
        lyricsScrollView = new ScrollView(this);
        lyricsScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        lyricsScrollView.setFadingEdgeLength(dp(20));
        lyricsScrollView.setVerticalFadingEdgeEnabled(true);
        lyricsScrollView.setScrollBarSize(0);

        lyricsContainer = new LinearLayout(this);
        lyricsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        lyricsContainer.setPadding(dp(12), dp(40), dp(12), dp(56));
        lyricsScrollView.addView(lyricsContainer);
        mainLayout.addView(lyricsScrollView);

        overlayContainer.addView(mainLayout);
        // Slide in from right (lyrics), slide player out to left
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        overlayContainer.setTranslationX(screenWidth);
        rootView.addView(overlayContainer);
        overlayContainer.animate().translationX(0).setDuration(250).start();
        if (mainPlayerContentView != null) {
            mainPlayerContentView.animate().translationX(-screenWidth).setDuration(250).start();
        }
        lyricsOverlayShowing = true;
        refreshChorusMarkerVisibility();
        // Bring indicator on top and switch to lyrics page
        if (pageIndicatorLayout != null) {
            pageIndicatorLayout.bringToFront();
            updatePageIndicator(true);
        }

        // Load lyrics only the first time this song's overlay is opened (or
        // after a song switch); otherwise rebuild the view from cached parse.
        if (lyricsCacheSongId == song.getId()) {
            displayLyricsInOverlay();
            if (btnTranslationToggle != null) {
                boolean hasTl = currentTlyricText != null && !currentTlyricText.isEmpty();
                boolean hasRo = currentRomalrcText != null && !currentRomalrcText.isEmpty();
                if (hasTl || hasRo) {
                    btnTranslationToggle.setVisibility(View.VISIBLE);
                } else {
                    btnTranslationToggle.setVisibility(View.GONE);
                }
                updateTranslationButton();
            }
            startLyricsScrollSync(tvLyricsTimeRef);
        } else {
            loadLyricsForOverlay(song, tvLyricsTimeRef);
        }
    }

    private void loadLyricsForOverlay(Song song, TextView tvLyricsTime) {
        // Try local .lrc file first
        String localLrc = loadLocalLrc(song);
        if (localLrc != null && !localLrc.isEmpty()) {
            // Also try to load local translated / romanization lyrics
            String localTlyric = loadLocalTlyric(song);
            currentTlyricText = localTlyric;
            String localRomalrc = loadLocalRomalrc(song);
            currentRomalrcText = localRomalrc;
            parseLrc(localLrc);
            boolean hasTl = localTlyric != null && !localTlyric.isEmpty();
            boolean hasRo = localRomalrc != null && !localRomalrc.isEmpty();
            if (hasTl) {
                parseTranslationLrc(localTlyric);
            }
            if (hasRo) {
                parseRomanizationLrc(localRomalrc);
            }
            if (hasTl || hasRo) {
                applyTranslationsToLyrics();
                if (btnTranslationToggle != null) {
                    btnTranslationToggle.setVisibility(View.VISIBLE);
                    updateTranslationButton();
                }
            }
            displayLyricsInOverlay();
            startLyricsScrollSync(tvLyricsTime);
            lyricsCacheSongId = song.getId();
            return;
        }

        // Fetch from API (with translation)
        if (song.getId() <= 0) {
            lyricsCacheSongId = song.getId();
            showNoLyricsInOverlay();
            return;
        }

        // Show loading
        lyricsContainer.removeAllViews();
        TextView tvLoading = new TextView(this);
        tvLoading.setText("加载歌词中...");
        tvLoading.setTextColor(0xFFAEAEB2);
        tvLoading.setTextSize(13);
        tvLoading.setGravity(Gravity.CENTER);
        lyricsContainer.addView(tvLoading);

        String cookie = playerManager.getCookie();
        MusicApiHelper.getLyricsWithTranslation(song.getId(), cookie, new MusicApiHelper.LyricsFullCallback() {
            @Override
            public void onResult(String lrcText, String tlyricText, String romalrcText) {
                if (lrcText == null || lrcText.isEmpty()) {
                    lyricsCacheSongId = song.getId();
                    showNoLyricsInOverlay();
                    return;
                }
                currentTlyricText = tlyricText;
                currentRomalrcText = romalrcText;
                parseLrc(lrcText);
                boolean hasTl = tlyricText != null && !tlyricText.isEmpty();
                boolean hasRo = romalrcText != null && !romalrcText.isEmpty();
                if (hasTl) {
                    parseTranslationLrc(tlyricText);
                }
                if (hasRo) {
                    parseRomanizationLrc(romalrcText);
                }
                if (hasTl || hasRo) {
                    applyTranslationsToLyrics();
                    if (btnTranslationToggle != null) {
                        btnTranslationToggle.setVisibility(View.VISIBLE);
                        updateTranslationButton();
                    }
                } else {
                    if (btnTranslationToggle != null) {
                        btnTranslationToggle.setVisibility(View.GONE);
                    }
                }
                displayLyricsInOverlay();
                startLyricsScrollSync(tvLyricsTime);
                lyricsCacheSongId = song.getId();
            }

            @Override
            public void onError(String message) {
                showNoLyricsInOverlay();
            }
        });
    }

    private String loadLocalLrc(Song song) {
        try {
            String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
            String folderName = safeName + " - " + safeArtist;
            java.io.File lrcFile = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "163Music/Download/" + folderName + "/lyrics.lrc"
            );
            if (!lrcFile.exists()) return null;

            try (java.io.FileInputStream fis = new java.io.FileInputStream(lrcFile);
                 java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, "UTF-8")) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load translated lyrics from local download folder (tlyrics.lrc).
     */
    private String loadLocalTlyric(Song song) {
        try {
            String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
            String folderName = safeName + " - " + safeArtist;
            java.io.File tlyricFile = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "163Music/Download/" + folderName + "/tlyrics.lrc"
            );
            if (!tlyricFile.exists()) return null;

            try (java.io.FileInputStream fis = new java.io.FileInputStream(tlyricFile);
                 java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, "UTF-8")) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load romanization lyrics from local download folder (rlyrics.lrc).
     */
    private String loadLocalRomalrc(Song song) {
        try {
            String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
            String folderName = safeName + " - " + safeArtist;
            java.io.File romaFile = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "163Music/Download/" + folderName + "/rlyrics.lrc"
            );
            if (!romaFile.exists()) return null;

            try (java.io.FileInputStream fis = new java.io.FileInputStream(romaFile);
                 java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, "UTF-8")) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.regex.Pattern LRC_PATTERN =
            java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.?(\\d{0,3})\\](.*)");

    /**
     * Parse an LRC timestamp match into milliseconds, or -1 if invalid.
     */
    private static long parseTimeMs(java.util.regex.Matcher matcher) {
        int min = Integer.parseInt(matcher.group(1));
        int sec = Integer.parseInt(matcher.group(2));
        String msStr = matcher.group(3);
        int ms = 0;
        if (msStr != null && !msStr.isEmpty()) {
            int parsed = Integer.parseInt(msStr.substring(0, Math.min(msStr.length(), 3)));
            if (msStr.length() == 1) ms = parsed * 100;
            else if (msStr.length() == 2) ms = parsed * 10;
            else ms = parsed;
        }
        return (long) min * 60 * 1000 + (long) sec * 1000 + ms;
    }

    private void parseLrc(String lrcText) {
        lyricLines.clear();
        translationMap.clear();
        romanizationMap.clear();
        String[] lines = lrcText.split("\n");
        for (String line : lines) {
            java.util.regex.Matcher matcher = LRC_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                long timeMs = parseTimeMs(matcher);
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    lyricLines.add(new LyricLine(timeMs, text));
                }
            }
        }
    }

    /**
     * Parse translated lyrics LRC into the translationMap (timeMs -> translated text).
     */
    private void parseTranslationLrc(String tlyricText) {
        translationMap.clear();
        if (tlyricText == null || tlyricText.isEmpty()) return;
        String[] lines = tlyricText.split("\n");
        for (String line : lines) {
            java.util.regex.Matcher matcher = LRC_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                long timeMs = parseTimeMs(matcher);
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    translationMap.put(timeMs, text);
                }
            }
        }
    }

    /**
     * Parse romanization (音译) lyrics LRC into the romanizationMap (timeMs -> text).
     */
    private void parseRomanizationLrc(String romalrcText) {
        romanizationMap.clear();
        if (romalrcText == null || romalrcText.isEmpty()) return;
        String[] lines = romalrcText.split("\n");
        for (String line : lines) {
            java.util.regex.Matcher matcher = LRC_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                long timeMs = parseTimeMs(matcher);
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    romanizationMap.put(timeMs, text);
                }
            }
        }
    }

    /**
     * Apply translations / romanization from maps to each lyricLine.
     */
    private void applyTranslationsToLyrics() {
        for (LyricLine line : lyricLines) {
            line.translation = translationMap.get(line.timeMs); // may be null
            line.romanization = romanizationMap.get(line.timeMs); // may be null
        }
    }

    /**
     * Cycle lyrics subtitle mode: off -> translation (意) -> romanization (音) -> off.
     * When romanization is unavailable, behaves as the original on/off translation toggle.
     * Saves preference persistently.
     */
    private void toggleLyricsTranslation() {
        boolean hasTrans = !translationMap.isEmpty();
        boolean hasRoma = !romanizationMap.isEmpty();
        if (!hasTrans && !hasRoma) return;
        if (hasRoma) {
            // off(0) -> 意(1) -> 音(2) -> off(0); skip 意 if unavailable
            if (translateMode == 0) {
                translateMode = hasTrans ? 1 : 2;
            } else if (translateMode == 1) {
                translateMode = 2;
            } else {
                translateMode = 0;
            }
        } else {
            // Original on/off logic when no romanization exists
            translateMode = (translateMode == 1) ? 0 : 1;
        }
        // Save preference
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putInt("lyrics_translate_mode", translateMode).apply();
        // Update button appearance
        updateTranslationButton();
        // Re-display lyrics with the new mode
        displayLyricsInOverlay();
        // Restart sync to update highlighting
        if (tvLyricsTimeRef != null) {
            lyricsScrollHandler.removeCallbacksAndMessages(null);
            currentHighlightIndex = -1;
            startLyricsScrollSync(tvLyricsTimeRef);
        }
    }

    /**
     * Refresh the toggle button label/color for the current mode.
     * Without romanization: original "译" / "译✓". With romanization: cycle 关闭 -> 意✓ -> 音✓.
     */
    private void updateTranslationButton() {
        if (btnTranslationToggle == null) return;
        boolean hasRoma = !romanizationMap.isEmpty();
        boolean active;
        if (hasRoma) {
            if (translateMode == 1) {
                btnTranslationToggle.setText("意");
                active = true;
            } else if (translateMode == 2) {
                btnTranslationToggle.setText("音");
                active = true;
            } else {
                btnTranslationToggle.setText("译");
                active = false;
            }
        } else {
            active = translateMode == 1;
            btnTranslationToggle.setText("译");
        }
        btnTranslationToggle.setTextColor(getResources().getColor(active
                ? R.color.on_primary_container : R.color.text_secondary));
        btnTranslationToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(active ? R.color.primary_container : R.color.surface_elevated)));
    }

    private void displayLyricsInOverlay() {
        if (lyricsContainer == null) return;
        lyricsContainer.removeAllViews();
        lyricViews.clear();
        currentHighlightIndex = -1;

        if (lyricLines.isEmpty()) {
            showNoLyricsInOverlay();
            return;
        }

        for (LyricLine line : lyricLines) {
            // Container for each lyric line (original + optional translation)
            LinearLayout lineLayout = new LinearLayout(this);
            lineLayout.setOrientation(LinearLayout.VERTICAL);
            lineLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            lineLayout.setPadding(0, dp(5), 0, dp(5));

            // Original lyrics text
            TextView tv = new TextView(this);
            tv.setText(line.text);
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            lineLayout.addView(tv);
            lyricViews.add(tv);

            // Subtitle text (translation or romanization, if available and enabled)
            String subText = null;
            if (translateMode == 1 && line.translation != null && !line.translation.isEmpty()) {
                subText = line.translation;
            } else if (translateMode == 2 && line.romanization != null && !line.romanization.isEmpty()) {
                subText = line.romanization;
            }
            if (subText != null) {
                TextView tvTrans = new TextView(this);
                tvTrans.setText(subText);
                tvTrans.setTextColor(getResources().getColor(R.color.text_disabled));
                tvTrans.setTextSize(11);
                tvTrans.setGravity(Gravity.CENTER);
                tvTrans.setPadding(0, dp(1), 0, 0);
                lineLayout.addView(tvTrans);
            }

            lyricsContainer.addView(lineLayout);
        }
    }

    private void showNoLyricsInOverlay() {
        if (lyricsContainer == null) return;
        lyricsContainer.removeAllViews();
        lyricViews.clear();
        TextView tv = new TextView(this);
        tv.setText("暂无歌词");
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(40), 0, 0);
        lyricsContainer.addView(tv);
    }

    private void startLyricsScrollSync(final TextView tvLyricsTime) {
        if (lyricLines.isEmpty()) return;

        lyricsScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!lyricsOverlayShowing || overlayContainer == null) return;
                if (playerManager.isPlaying() || playerManager.getCurrentPosition() > 0) {
                    int currentPos = playerManager.getCurrentPosition();
                    int duration = playerManager.getDuration();

                    tvLyricsTime.setText(formatTime(currentPos) + " / " + formatTime(duration));

                    if (lyricScrollMode == LYRIC_MODE_BLOCK && lyricsUserScrolled) {
                        if (System.currentTimeMillis() - lyricsLastUserScrollTime >= lyricResumeIntervalMs) {
                            lyricsUserScrolled = false;
                        }
                    }

                    int newIndex = -1;
                    for (int i = 0; i < lyricLines.size(); i++) {
                        if (lyricLines.get(i).timeMs <= currentPos) {
                            newIndex = i;
                        } else {
                            break;
                        }
                    }

                    if (newIndex != currentHighlightIndex && newIndex >= 0) {
                        clearCurrentLyricHighlight();
                        currentHighlightIndex = newIndex;
                        if (currentHighlightIndex < lyricViews.size()) {
                            TextView currentView = lyricViews.get(currentHighlightIndex);
                            currentView.setTextColor(getResources().getColor(R.color.text_primary));
                            currentView.setTextSize(14);

                            if (lyricScrollMode == LYRIC_MODE_FOLLOW || !lyricsUserScrolled) {
                                scrollOverlayToLine(currentHighlightIndex);
                            }
                        }
                    }
                }
                lyricsScrollHandler.postDelayed(this, 300);
            }
        };
        lyricsScrollHandler.post(lyricsScrollRunnable);
    }

    private void stopLyricsScrollSync() {
        lyricsScrollHandler.removeCallbacksAndMessages(null);
        lyricsScrollRunnable = null;
        lyricsOverlayShowing = false;
        // Keep parsed lyrics (lyricLines / translationMap / romanizationMap /
        // currentTlyricText / currentRomalrcText) so reopening the overlay for
        // the same song rebuilds instantly without reloading from disk/network.
        lyricViews.clear();
        btnTranslationToggle = null;
        currentHighlightIndex = -1;
        lyricsGestureDetector = null;
        lyricsUserScrolled = false;
        lyricsLastUserScrollTime = 0L;
        lyricsTouchStartedInScrollView = false;
        lyricsScrollView = null;
        lyricsContainer = null;
        tvLyricsSongLabel = null;
        tvLyricsTimeRef = null;
    }

    // ==================== Playback Speed ====================

    private void onFuncPlaybackSpeed() {
        dismissOverlay();
        showSpeedOptions();
    }

    private void onFuncShowPlaylist() {
        dismissOverlay();
        showPlaylistOverlay();
    }

    /**
     * Show the current playlist as an overlay with song list.
     * Tapping a song plays it and dismisses the overlay.
     */
    private void showPlaylistOverlay() {
        java.util.List<Song> playlist = playerManager.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
        contentLayout.setBackgroundColor(getResources().getColor(R.color.surface));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        contentLayout.setLayoutParams(contentParams);
        contentLayout.setOnClickListener(v -> { /* consume click */ });

        // Title bar
        contentLayout.addView(createOverlayTitleBar("播放列表 (" + playlist.size() + "首)"));

        // Recycle rows instead of eagerly creating a view and image request for
        // every song in a potentially very large queue.
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setDividerHeight(0);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        int currentIndex = playerManager.getCurrentIndex();
        android.widget.ArrayAdapter<Song> adapter = new android.widget.ArrayAdapter<Song>(
                this, R.layout.item_song, R.id.tv_item_name, playlist) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View row = convertView != null ? convertView
                        : getLayoutInflater().inflate(R.layout.item_song, parent, false);
                Song song = getItem(position);
                TextView name = row.findViewById(R.id.tv_item_name);
                TextView artist = row.findViewById(R.id.tv_item_artist);
                ImageView cover = row.findViewById(R.id.iv_cover);
                View vip = row.findViewById(R.id.tv_vip_badge);
                name.setText((position == currentIndex ? "▶ " : "") + song.getDisplayName());
                name.setTextColor(getResources().getColor(position == currentIndex
                        ? R.color.colorPrimary : R.color.text_primary));
                name.setSelected(true);
                artist.setText(song.getArtist());
                artist.setSelected(true);
                vip.setVisibility(song.isVip() ? View.VISIBLE : View.GONE);
                NetworkImageLoader.load(cover, song.getCoverUrl());
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            playerManager.playFromCurrentPlaylist(position);
            dismissOverlay();
        });
        if (currentIndex > 0) listView.setSelection(currentIndex);
        contentLayout.addView(listView);
        overlayContainer.addView(contentLayout);
        rootView.addView(overlayContainer);
    }

    private void showSpeedOptions() {
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        ScrollView scrollView = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.gravity = Gravity.CENTER;
        scrollView.setLayoutParams(scrollParams);
        scrollView.setOnClickListener(v -> { /* consume click */ });

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(20), dp(20), dp(20), dp(20));

        // Title bar with close button
        contentLayout.addView(createOverlayTitleBar("倍速播放"));

        // Preset speed options
        float[] speeds = {0.8f, 0.9f, 1.0f, 1.1f, 1.2f};
        float currentSpeed = playerManager.getPlaybackSpeed();
        for (float speed : speeds) {
            String label = String.format("%.1fx", speed);
            if (speed == 1.0f) label = "1.0x (正常)";
            boolean selected = Math.abs(currentSpeed - speed) < 0.01f;
            MaterialButton btn = createOverlayActionButton(label,
                    getResources().getColor(selected ? R.color.colorPrimary : R.color.surface_elevated), selected);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.bottomMargin = dp(4);
            btn.setLayoutParams(btnParams);
            btn.setClickable(true);
            btn.setFocusable(true);
            float finalSpeed = speed;
            btn.setOnClickListener(v -> {
                playerManager.setPlaybackSpeed(finalSpeed);
                Toast.makeText(this, String.format("播放速度: %.1fx", finalSpeed), Toast.LENGTH_SHORT).show();
                dismissOverlay();
            });
            contentLayout.addView(btn);
        }

        // Custom speed input
        TextView customLabel = new TextView(this);
        customLabel.setText("自定义（0.1-5.0）");
        customLabel.setTextColor(0xFFAEAEB2);
        customLabel.setTextSize(13);
        customLabel.setGravity(Gravity.CENTER);
        customLabel.setPadding(0, dp(12), 0, dp(4));
        contentLayout.addView(customLabel);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText etSpeed = addOverlayNumberField(customRow, "倍速",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        MaterialButton btnApply = createOverlayActionButton("应用",
                getResources().getColor(R.color.colorPrimary), true);
        btnApply.setOnClickListener(v -> {
            String input = etSpeed.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入倍速值", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                float speed = Float.parseFloat(input);
                if (speed < 0.1f || speed > 5.0f) {
                    Toast.makeText(this, "请输入0.1-5.0之间的值", Toast.LENGTH_SHORT).show();
                    return;
                }
                playerManager.setPlaybackSpeed(speed);
                Toast.makeText(this, String.format("播放速度: %.1fx", speed), Toast.LENGTH_SHORT).show();
                dismissOverlay();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
        customRow.addView(btnApply);

        contentLayout.addView(customRow);

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);
    }

    // ==================== Audio Quality ====================

    interface QualitySelectCallback {
        void onSelected(String quality);
    }

    private void onFuncSelectQuality() {
        dismissOverlay();
        Song song = playerManager.getCurrentSong();
        showQualityOptionsInternal(true, song, quality -> {
            getSharedPreferences("music163_settings", MODE_PRIVATE)
                    .edit().putString("preferred_quality", quality).apply();
            if (song != null) {
                playerManager.switchCurrentSongQuality(quality);
            }
            Toast.makeText(this, "音质已设置: " + getQualityDisplayName(quality),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Show the quality selection overlay, fetching availability from the API.
     *
     * 三处（播放页三点菜单「播放音质」、单曲「下载音质」、歌单「批量下载音质」）
     * 统一走 {@link QualityPicker}，也就是原作者做的那套遮罩 + 卡片行浮层，
     * 档位名、码率描述、会员徽标、选中 / 置灰态完全一致。
     *
     * @param saveAsPreferred  true = 保存为播放音质，false = 一次性下载音质
     * @param song             the song to check quality availability for (may be null)
     */
    private void showQualityOptionsInternal(boolean saveAsPreferred, Song song,
                                             QualitySelectCallback callback) {
        final String title = saveAsPreferred ? "播放音质" : "下载音质";
        // 关闭统一交给 dismissOverlay()，这样 overlayContainer 字段能被正确清掉
        final QualityPicker.OnPickListener pick = row -> {
            if (callback != null) callback.onSelected(row.level);
        };
        List<QualityPicker.Row> loading = new ArrayList<>();
        loading.add(new QualityPicker.Row("", "正在检测可用音质…", "按当前账号权限",
                "", 0x60FFFFFF, false, false));
        overlayContainer = QualityPicker.show(this, title, null, loading, pick,
                this::dismissOverlay);

        final String currentQuality = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("preferred_quality", "exhigh");

        if (saveAsPreferred && song != null && song.isForceLocalPlayback()) {
            QualityPicker.update(overlayContainer, "本地已下载音质",
                    buildLocalQualityRows(song, currentQuality), pick);
            return;
        }

        long songId = (song != null && song.getId() > 0)
                ? song.getId() : 0;
        String cookie = playerManager.getCookie();
        if (songId > 0) {
            MusicApiHelper.getSongQualityInfo(songId, cookie,
                    new MusicApiHelper.SongQualityCallback() {
                        @Override
                        public void onResult(MusicApiHelper.SongQualityInfo info) {
                            runOnUiThread(() -> {
                                if (overlayContainer == null) return;
                                QualityPicker.update(overlayContainer,
                                        accountTierLabel(info),
                                        buildQualityRows(song, info, currentQuality,
                                                saveAsPreferred), pick);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                if (overlayContainer == null) return;
                                QualityPicker.update(overlayContainer,
                                        "检测失败，仅显示通用档位",
                                        buildQualityRows(song, null, currentQuality,
                                                saveAsPreferred), pick);
                            });
                        }
                    });
        } else {
            QualityPicker.update(overlayContainer, "音质权限以当前账号为准",
                    buildQualityRows(song, null, currentQuality, saveAsPreferred), pick);
        }
    }

    /** 音质浮层副标题：把当前账号是普通 / VIP / SVIP 说明清楚。 */
    private String accountTierLabel(MusicApiHelper.SongQualityInfo info) {
        if (info == null || !info.plLevelReliable) {
            return "音质权限以当前账号为准";
        }
        switch (info.accountTier()) {
            case 2:  return "当前账号：黑胶 SVIP · 臻品档已解锁";
            case 1:  return "当前账号：黑胶 VIP · 臻品档需 SVIP";
            default: return "当前账号：未开通会员 · 高音质需 VIP / SVIP";
        }
    }

    /** 本地已下载音质列表，同样用作者那套卡片行。 */
    private List<QualityPicker.Row> buildLocalQualityRows(Song song, String currentQuality) {
        List<QualityPicker.Row> rows = new ArrayList<>();
        List<String> localQualities = DownloadManager.getAvailableLocalQualities(song);
        String selectedLocalQuality = DownloadManager.detectLocalQualityFromPath(song.getUrl());
        if (selectedLocalQuality == null) {
            selectedLocalQuality = DownloadManager.getBestDownloadedQuality(song);
        }
        if (localQualities.isEmpty()) {
            rows.add(new QualityPicker.Row("", "暂无本地音质列表", "请先下载歌曲",
                    "", 0x60FFFFFF, false, false));
            return rows;
        }
        for (String quality : localQualities) {
            rows.add(new QualityPicker.Row(quality, getQualityShortName(quality),
                    "本地已下载", "本地", 0xFF4CAF50,
                    quality.equals(selectedLocalQuality), true));
        }
        return rows;
    }

    /**
     * 构造音质档位行。
     * @param info  接口返回的 privileges；null = 无检测结果，只按档位标 VIP / SVIP 不置灰
     */
    private List<QualityPicker.Row> buildQualityRows(Song song,
                                                     MusicApiHelper.SongQualityInfo info,
                                                     String currentQuality,
                                                     boolean saveAsPreferred) {
        List<String> downloadedQualities = !saveAsPreferred && song != null
                ? DownloadManager.getAvailableLocalQualities(song)
                : java.util.Collections.emptyList();
        // 档位表只有一份：QualityPicker.LEVELS（批量下载页也用它）
        List<QualityPicker.Row> rows = new ArrayList<>();
        for (String[] q : QualityPicker.LEVELS) {
            String level   = q[0];
            String name    = q[1];
            String bitrate = q[2];
            boolean isSelected = saveAsPreferred && level.equals(currentQuality);
            boolean alreadyDownloaded = !saveAsPreferred
                    && downloadedQualities.contains(level);

            String tier;
            if (info == null) {
                // 无检测结果：只按档位标注所需会员等级，不做禁用，避免误伤
                int need = MusicApiHelper.requiredMembership(level);
                tier = need == 2 ? "SVIP" : need == 1 ? "VIP" : "免费";
            } else {
                // 播放看 plLevel、下载看 dlLevel；VIP 与 SVIP 未解锁的档位分别标出
                String t = saveAsPreferred ? info.getTier(level) : info.getDownloadTier(level);
                switch (t) {
                    case "free":          tier = "免费"; break;
                    case "vip":           tier = "VIP";  break;
                    case "svip":          tier = "SVIP"; break;
                    case "locked":        tier = QUALITY_TIER_NEED_VIP; break;
                    case "locked_svip":   tier = QUALITY_TIER_NEED_SVIP; break;
                    case "unavailable":   tier = QUALITY_TIER_UNAVAILABLE; break;
                    default:              tier = "VIP";  break;
                }
            }
            if (alreadyDownloaded) {
                tier = QUALITY_TIER_DOWNLOADED;
            }

            boolean disabled = QUALITY_TIER_UNAVAILABLE.equals(tier)
                    || QUALITY_TIER_LOCKED.equals(tier)
                    || QUALITY_TIER_NEED_VIP.equals(tier)
                    || QUALITY_TIER_NEED_SVIP.equals(tier)
                    || QUALITY_TIER_DOWNLOADED.equals(tier);

            int tierColor;
            switch (tier) {
                case "VIP":
                case QUALITY_TIER_NEED_VIP:
                case QUALITY_TIER_LOCKED:      tierColor = QualityPicker.COLOR_VIP; break;
                case "SVIP":
                case QUALITY_TIER_NEED_SVIP:   tierColor = QualityPicker.COLOR_SVIP; break;
                case QUALITY_TIER_UNAVAILABLE: tierColor = QualityPicker.COLOR_DISABLED; break;
                case QUALITY_TIER_DOWNLOADED:  tierColor = QualityPicker.COLOR_DOWNLOADED; break;
                default:                       tierColor = QualityPicker.COLOR_FREE; break;
            }

            rows.add(new QualityPicker.Row(level, name, bitrate, tier,
                    tierColor, isSelected, !disabled));
        }
        return rows;
    }

    /** Short display name for current quality (shown on the button label). */
    private String getCurrentQualityLabel(Song song, String fallbackQuality) {
        if (song != null && (song.isForceLocalPlayback()
                || (song.getUrl() != null && song.getUrl().startsWith("/")))) {
            String localQuality = DownloadManager.detectLocalQualityFromPath(song.getUrl());
            if (localQuality == null) {
                localQuality = DownloadManager.getBestDownloadedQuality(song);
            }
            if (localQuality != null) {
                return localQuality;
            }
        }
        return fallbackQuality;
    }

    /** Short display name for current quality (shown on the button label). */
    private String getQualityShortName(String level) {
        // 档位中文名只有一份：QualityPicker.LEVELS，避免这里和音质浮层各写一套
        for (String[] q : QualityPicker.LEVELS) {
            if (q[0].equals(level)) return q[1];
        }
        return level;
    }

    /** Full display name for quality (shown in toasts). */
    private String getQualityDisplayName(String level) {
        for (String[] q : QualityPicker.LEVELS) {
            if (q[0].equals(level)) return q[1] + " (" + q[2].split(" · ")[0] + ")";
        }
        return level;
    }

    // ==================== Cloud Favorites Status Cache ====================

    private java.util.Set<Long> cloudLikedIds = null;
    private long cloudLikedCacheTime = 0;
    private static final long CLOUD_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

    /**
     * Check if a song is liked in cloud mode, using cached IDs.
     */
    private boolean isCloudLiked(long songId) {
        if (cloudLikedIds != null && System.currentTimeMillis() - cloudLikedCacheTime < CLOUD_CACHE_TTL) {
            return cloudLikedIds.contains(songId);
        }
        // If cache is stale/empty, refresh asynchronously
        refreshCloudLikedIds();
        return cloudLikedIds != null && cloudLikedIds.contains(songId);
    }

    /**
     * Refresh the cached set of cloud liked song IDs.
     */
    private void refreshCloudLikedIds() {
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) return;
        MusicApiHelper.getCloudLikedIds(cookie, new MusicApiHelper.CloudLikedIdsCallback() {
            @Override
            public void onResult(java.util.Set<Long> ids) {
                cloudLikedIds = ids;
                cloudLikedCacheTime = System.currentTimeMillis();
            }
            @Override
            public void onError(String message) {
                // Ignore errors silently; cache remains stale
            }
        });
    }

    // ==================== Sleep Timer ====================

    private void onFuncSleepTimer() {
        dismissOverlay();
        if (playerManager.isSleepTimerActive()) {
            // Timer is active - show remaining time and option to cancel
            showSleepTimerStatus();
        } else {
            // No timer - show options to set one
            showSleepTimerOptions();
        }
    }

    private void showSleepTimerOptions() {
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        ScrollView scrollView = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.gravity = Gravity.CENTER;
        scrollView.setLayoutParams(scrollParams);
        scrollView.setOnClickListener(v -> { /* consume click */ });

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(20), dp(20), dp(20), dp(20));

        // Title bar with close button
        contentLayout.addView(createOverlayTitleBar("定时关闭"));

        // Preset timer options
        int[] minutes = {5, 10, 20, 30};
        for (int min : minutes) {
            MaterialButton btn = createOverlayActionButton(min + " 分钟",
                    getResources().getColor(R.color.surface_elevated), false);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.bottomMargin = dp(4);
            btn.setLayoutParams(btnParams);
            btn.setClickable(true);
            btn.setFocusable(true);
            int finalMin = min;
            btn.setOnClickListener(v -> {
                playerManager.startSleepTimer(finalMin);
                Toast.makeText(this, finalMin + "分钟后自动停止播放", Toast.LENGTH_SHORT).show();
                dismissOverlay();
            });
            contentLayout.addView(btn);
        }

        // Custom seconds label
        TextView customLabel = new TextView(this);
        customLabel.setText("自定义（秒）");
        customLabel.setTextColor(0xFFAEAEB2);
        customLabel.setTextSize(13);
        customLabel.setGravity(Gravity.CENTER);
        customLabel.setPadding(0, dp(12), 0, dp(4));
        contentLayout.addView(customLabel);

        // Custom seconds input row
        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText etSeconds = addOverlayNumberField(customRow, "秒数", InputType.TYPE_CLASS_NUMBER);

        MaterialButton btnCustom = createOverlayActionButton("开始",
                getResources().getColor(R.color.colorPrimary), true);
        btnCustom.setOnClickListener(v -> {
            String input = etSeconds.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入秒数", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int seconds = Integer.parseInt(input);
                if (seconds <= 0) {
                    Toast.makeText(this, "请输入大于0的秒数", Toast.LENGTH_SHORT).show();
                    return;
                }
                playerManager.startSleepTimerSeconds(seconds);
                Toast.makeText(this, seconds + "秒后自动停止播放", Toast.LENGTH_SHORT).show();
                dismissOverlay();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
        customRow.addView(btnCustom);

        contentLayout.addView(customRow);

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);
    }

    private void showSleepTimerStatus() {
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        contentLayout.setOnClickListener(v -> { /* consume click */ });

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        contentLayout.setLayoutParams(contentParams);

        // Title bar with close button
        contentLayout.addView(createOverlayTitleBar("定时关闭"));

        // Remaining time display
        TextView tvRemaining = new TextView(this);
        tvRemaining.setTextColor(0xFFFFFFFF);
        tvRemaining.setTextSize(20);
        tvRemaining.setGravity(Gravity.CENTER);
        tvRemaining.setPadding(0, dp(8), 0, dp(16));
        contentLayout.addView(tvRemaining);

        // Update remaining time every second
        final Handler timerDisplayHandler = new Handler();
        Runnable timerDisplayRunnable = new Runnable() {
            @Override
            public void run() {
                if (overlayContainer == null) return;
                long remainMs = playerManager.getSleepTimerRemainingMs();
                if (remainMs > 0) {
                    int totalSec = (int) (remainMs / 1000);
                    int min = totalSec / 60;
                    int sec = totalSec % 60;
                    tvRemaining.setText(String.format("剩余 %d:%02d", min, sec));
                    timerDisplayHandler.postDelayed(this, 1000);
                } else {
                    tvRemaining.setText("定时已结束");
                }
            }
        };
        timerDisplayRunnable.run();

        // Cancel button
        MaterialButton btnCancel = createOverlayActionButton("取消定时",
                getResources().getColor(R.color.colorPrimary), true);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancel.setLayoutParams(cancelParams);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setOnClickListener(v -> {
            playerManager.cancelSleepTimer();
            timerDisplayHandler.removeCallbacksAndMessages(null);
            Toast.makeText(this, "已取消定时", Toast.LENGTH_SHORT).show();
            dismissOverlay();
        });
        contentLayout.addView(btnCancel);

        overlayContainer.addView(contentLayout);
        rootView.addView(overlayContainer);
    }

    // ==================== UI Updates ====================

    private void updateUI() {
        Song song = playerManager.getCurrentSong();
        if (song != null) {
            tvSongName.setText(song.getDisplayName());
            tvArtist.setText(song.getArtist());
            if (tvPlayerVipBadge != null) {
                tvPlayerVipBadge.setVisibility(song.isVip() ? View.VISIBLE : View.GONE);
            }
            ensureChorusLoaded(song);
        } else {
            tvSongName.setText(R.string.no_song);
            tvArtist.setText("");
            if (tvPlayerVipBadge != null) {
                tvPlayerVipBadge.setVisibility(View.GONE);
            }
            clearChorusInfo();
        }
        btnPlay.setIconResource(playerManager.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        btnPlay.setContentDescription(playerManager.isPlaying() ? "暂停" : "播放");
        // btnFuncMore image is static in layout
        updatePlayModeButton();
        // Update playlist indicator visibility
        updatePlaylistIndicator();
        updateChorusMarker(playerManager.getDuration());
    }

    /** 主界面右上角的播放模式按钮：图标跟着当前模式走。 */
    private void updatePlayModeButton() {
        if (btnPlayMode == null) {
            return;
        }
        int iconRes;
        String label;
        switch (playerManager.getPlayMode()) {
            case SINGLE_REPEAT:
                iconRes = R.drawable.ic_repeat_one;
                label = "单曲循环";
                break;
            case RANDOM:
                iconRes = R.drawable.ic_shuffle;
                label = "随机播放";
                break;
            case LIST_LOOP:
            default:
                iconRes = R.drawable.ic_repeat;
                label = "列表循环";
                break;
        }
        btnPlayMode.setIconResource(iconRes);
        btnPlayMode.setContentDescription(label);
    }

    private void updatePlaylistIndicator() {
        if (btnPlaylistIndicator != null) {
            Song currentSong = playerManager.getCurrentSong();
            if (playerManager.hasSourcePlaylist()) {
                btnPlaylistIndicator.setVisibility(View.VISIBLE);
            } else {
                btnPlaylistIndicator.setVisibility(View.GONE);
            }
            // Title/artist keep symmetric 48dp margins from the layout so they
            // stay centered and their marquee never slides under the top
            // playlist/more buttons; only visibility changes here.
        }
    }

    /**
     * Songs created without fee info (e.g. from the music-info page) cannot
     * show the VIP badge until their fee is fetched. Look it up once per song
     * and refresh the player badge if this song is still current.
     */
    private void enrichSongFee(final Song song) {
        if (!feeEnrichRequested.add(song.getId())) {
            return;
        }
        MusicApiHelper.fetchSongsDetails(java.util.Collections.singletonList(song.getId()),
                playerManager.getCookie(), new MusicApiHelper.BatchSongDetailsCallback() {
                    @Override
                    public void onResult(java.util.Map<Long, Song> songMap) {
                        Song detail = songMap != null ? songMap.get(song.getId()) : null;
                        if (detail == null || detail.getFee() == 0 || song.getFee() != 0) {
                            return;
                        }
                        song.setFee(detail.getFee());
                        if (tvPlayerVipBadge != null && isCurrentSong(song.getId())) {
                            tvPlayerVipBadge.setVisibility(song.isVip() ? View.VISIBLE : View.GONE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
    }

    @Override
    public void onSongChanged(Song song) {
        tvSongName.setText(song.getDisplayName());
        tvArtist.setText(song.getArtist());
        if (tvPlayerVipBadge != null) {
            tvPlayerVipBadge.setVisibility(song != null && song.isVip() ? View.VISIBLE : View.GONE);
        }
        if (song != null && song.getId() > 0 && song.getFee() == 0) {
            enrichSongFee(song);
        }
        ensureChorusLoaded(song);
        startPlaybackService(song.getDisplayName(), song.getArtist(), song.getCoverUrl(), true);
        // Save to play history
        HistoryManager.getInstance().addToHistory(song);
        // Refresh lyrics overlay if it is currently showing
        if (lyricsOverlayShowing && lyricsContainer != null && tvLyricsTimeRef != null) {
            // Stop current sync
            lyricsScrollHandler.removeCallbacksAndMessages(null);
            lyricsScrollRunnable = null;
            lyricLines.clear();
            lyricViews.clear();
            translationMap.clear();
            romanizationMap.clear();
            currentTlyricText = null;
            currentRomalrcText = null;
            translateMode = 0;
            lyricsCacheSongId = -1;
            currentHighlightIndex = -1;
            // Update song name label
            if (tvLyricsSongLabel != null) {
                tvLyricsSongLabel.setText(song.getDisplayName() + " - " + song.getArtist());
            }
            // Hide translation button until we know if new song has translation
            if (btnTranslationToggle != null) {
                btnTranslationToggle.setVisibility(View.GONE);
            }
            // Reload lyrics for new song
            loadLyricsForOverlay(song, tvLyricsTimeRef);
        }
        updateCoverBackground();
    }

    /**
     * In MODE_COVER the background always follows the current song's album
     * cover (kept in memory, never saved to disk). It is (re)loaded whenever
     * the screen is shown, regardless of playback state; if the cover cannot
     * be fetched the default background is used.
     */
    private void updateCoverBackground() {
        if (!BackgroundUtil.MODE_COVER.equals(BackgroundUtil.getMode(this))) {
            return;
        }
        // Whatever path we take below, any cover download still in flight for
        // a previously current song must no longer be allowed to commit.
        BackgroundUtil.invalidateCoverLoads();
        final View root = getWindow().getDecorView().findViewById(android.R.id.content);
        // Always follow the current song (not another playlist entry) so modes
        // like 私人漫游 switch the background on every song change.
        Song song = playerManager.getCurrentSong();
        final String cover = song != null && song.getCoverUrl() != null ? song.getCoverUrl() : "";
        if (!cover.isEmpty()) {
            if (BackgroundUtil.hasCoverBackground(cover)) {
                BackgroundUtil.applyBackground(this, root);
            } else {
                BackgroundUtil.loadCoverBackground(this, cover,
                        () -> BackgroundUtil.applyBackground(this, root));
            }
            return;
        }
        // No cover url yet: try to fetch it from the API by song id.
        if (song != null && song.getId() > 0) {
            fetchCoverForBackground(song);
        } else {
            BackgroundUtil.clearCoverBackground();
            BackgroundUtil.applyBackground(this, root);
        }
    }

    private void fetchCoverForBackground(final Song song) {
        final long songId = song.getId();
        if (coverFetchInFlightId == songId) {
            return;
        }
        coverFetchInFlightId = songId;
        final View root = getWindow().getDecorView().findViewById(android.R.id.content);
        MusicApiHelper.fetchSongsDetails(java.util.Collections.singletonList(songId),
                playerManager.getCookie(), new MusicApiHelper.BatchSongDetailsCallback() {
                    @Override
                    public void onResult(java.util.Map<Long, Song> songMap) {
                        if (coverFetchInFlightId == songId) {
                            coverFetchInFlightId = -1;
                        }
                        Song detail = songMap != null ? songMap.get(songId) : null;
                        String fetched = detail != null ? detail.getCoverUrl() : null;
                        if (fetched != null && !fetched.isEmpty()) {
                            song.setCoverUrl(fetched);
                            playerManager.savePlaybackState();
                            if (isCurrentSong(songId)) {
                                updateCoverBackground();
                            }
                        } else if (isCurrentSong(songId)) {
                            BackgroundUtil.clearCoverBackground();
                            BackgroundUtil.applyBackground(MainActivity.this, root);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (coverFetchInFlightId == songId) {
                            coverFetchInFlightId = -1;
                        }
                        if (isCurrentSong(songId)) {
                            BackgroundUtil.clearCoverBackground();
                            BackgroundUtil.applyBackground(MainActivity.this, root);
                        }
                    }
                });
    }

    private boolean isCurrentSong(long songId) {
        Song current = playerManager.getCurrentSong();
        return current != null && current.getId() == songId;
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        btnPlay.setIconResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        btnPlay.setContentDescription(isPlaying ? "暂停" : "播放");
        if (isPlaying) {
            startSeekBarUpdate();
        } else {
            stopSeekBarUpdate();
        }
        refreshChorusMarkerVisibility();
        // Always update notification with current play state
        Song song = playerManager.getCurrentSong();
        String name = song != null ? song.getDisplayName() : "";
        String artist = song != null ? song.getArtist() : "";
        String coverUrl = song != null ? song.getCoverUrl() : "";
        startPlaybackService(name, artist, coverUrl, isPlaying);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSleepTimerTriggered(boolean exitApp) {
        Toast.makeText(this,
                exitApp ? "定时结束，已退出应用" : "定时结束，已停止播放",
                Toast.LENGTH_SHORT).show();
        if (exitApp) {
            dismissOverlay();
            moveTaskToBack(true);
            finishAffinity();
        }
    }

    private static final long SEEK_BAR_UPDATE_INTERVAL_MS = 1000L;

    private final Runnable seekBarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            // 界面不可见或已暂停就到此为止，不再自循环排下一次唤醒
            if (!uiResumed || !playerManager.isPlaying()) return;
            if (!isUserSeeking) {
                int current = playerManager.getCurrentPosition();
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    seekBar.setValue((float) (1000L * current / duration));
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(duration));
                    // 高潮标记的位置只跟歌曲与布局有关，不随播放进度变化，
                    // 已在 onSongChanged / 高潮数据回来 / 布局变化时更新，不需要每轮重排。
                }
            }
            seekHandler.postDelayed(this, SEEK_BAR_UPDATE_INTERVAL_MS);
        }
    };

    private void startSeekBarUpdate() {
        if (!uiResumed) return;
        seekHandler.removeCallbacks(seekBarUpdateRunnable);
        seekHandler.post(seekBarUpdateRunnable);
    }

    private void stopSeekBarUpdate() {
        seekHandler.removeCallbacks(seekBarUpdateRunnable);
    }

    private void startPlaybackService(String songName, String artist, String coverUrl, boolean isPlaying) {
        Intent serviceIntent = new Intent(this, MusicPlaybackService.class);
        serviceIntent.putExtra("song_name", songName);
        serviceIntent.putExtra("artist", artist);
        serviceIntent.putExtra("cover_url", coverUrl != null ? coverUrl : "");
        serviceIntent.putExtra("is_playing", isPlaying);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        serviceStarted = true;
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ==================== Page Indicator ====================

    private void ensurePageIndicator() {
        if (pageIndicatorLayout != null) return;
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        pageIndicatorLayout = new android.widget.LinearLayout(this);
        pageIndicatorLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        pageIndicatorLayout.setGravity(Gravity.CENTER);
        pageIndicatorLayout.setClickable(false);
        pageIndicatorLayout.setFocusable(false);

        int iconSize = dp(9);
        int margin = dp(4);

        ivDotPlayer = new android.widget.ImageView(this);
        ivDotPlayer.setImageResource(R.drawable.ic_indicator_player);
        android.widget.LinearLayout.LayoutParams pParams =
                new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        pParams.setMarginEnd(margin);
        ivDotPlayer.setLayoutParams(pParams);
        ivDotPlayer.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        pageIndicatorLayout.addView(ivDotPlayer);

        ivDotLyrics = new android.widget.ImageView(this);
        ivDotLyrics.setImageResource(R.drawable.ic_indicator_lyrics);
        ivDotLyrics.setLayoutParams(new android.widget.LinearLayout.LayoutParams(iconSize, iconSize));
        ivDotLyrics.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        pageIndicatorLayout.addView(ivDotLyrics);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(4);
        pageIndicatorLayout.setLayoutParams(params);

        rootView.addView(pageIndicatorLayout);
        updatePageIndicator(false);
    }

    private void updatePageIndicator(boolean lyricsPage) {
        if (ivDotPlayer == null || ivDotLyrics == null) return;
        if (lyricsPage) {
            ivDotPlayer.setColorFilter(0x55FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);  // gray = inactive
            ivDotLyrics.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);  // white = active
        } else {
            ivDotPlayer.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);  // white = active
            ivDotLyrics.setColorFilter(0x55FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);  // gray = inactive
        }
    }

    /**
     * Override onBackPressed to intercept the system back gesture on watches.
     * On 小天才 watches, right-swipe triggers onBackPressed. We only allow exit
     * when no overlay is showing (i.e., on the main player screen).
     * When an overlay (lyrics, functions, etc.) is visible, dismiss it instead.
     */
    @Override
    public void onBackPressed() {
        if (overlayContainer != null || lyricsOverlayShowing) {
            dismissOverlay();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        uiResumed = false;
        // 进度条轮询只在界面可见时跑：界面上的时间/进度到后台没有任何意义，
        // 但 500ms 一次的自循环会一直把 CPU 从休眠里拽起来。
        stopSeekBarUpdate();
        super.onPause();
        stopVolumeRepeat();
        lyricsScrollHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVolumeRepeat();
        stopSeekBarUpdate();
        lyricsScrollHandler.removeCallbacksAndMessages(null);
        volumeHandler.removeCallbacksAndMessages(null);
        dismissVolumeIndicator();
        AppVolumeManager vm = AppVolumeManager.get();
        if (vm != null && appVolumeListener != null) {
            vm.removeLevelChangeListener(appVolumeListener);
            appVolumeListener = null;
        }
        NetworkImageLoader.cancelAll();
    }
}
