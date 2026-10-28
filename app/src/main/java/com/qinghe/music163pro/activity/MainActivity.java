package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
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
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.manager.FavoritesManager;
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.manager.RingtoneManagerHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.service.MusicPlaybackService;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.NetworkImageLoader;
import com.qinghe.music163pro.util.UpdateChecker;
import com.qinghe.music163pro.util.WatchConfirmDialog;
import com.qinghe.music163pro.util.BackgroundUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import java.io.File;

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
    private static final int LYRIC_MODE_FOLLOW = 0;
    private static final int LYRIC_MODE_BLOCK = 1;
    private static final String QUALITY_TIER_UNAVAILABLE = "暂无";
    private static final String QUALITY_TIER_DOWNLOADED = "已下载";

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

    // Playlist indicator (top-left)
    private MaterialButton btnPlaylistIndicator;

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
        ensureChorusMarkerDot();

        playerManager = MusicPlayerManager.getInstance();
        playerManager.setContext(this);
        favoritesManager = new FavoritesManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

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

        btnVolDown.setOnClickListener(v -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                showVolumeIndicator();
        });

        btnVolUp.setOnClickListener(v -> {
                if (!isHeadphonesConnected()) {
                    volumeSafeConfirmed = false;
                    adjustVolumeUp();
                    return;
                }
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int percent = max > 0 ? Math.round(current * 100f / max) : 0;
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
                            new WatchConfirmDialog.Options(0xFF2B2930, 0xFFD0BCFF, true));
                } else {
                    adjustVolumeUp();
                }
        });

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
            if (currentSong != null && currentSong.isBilibili()) {
                openBilibiliPlaylist(currentSong);
            } else if (playerManager.hasSourcePlaylist()) {
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

        // Check for updates once per day on first launch
        checkUpdateIfNeeded();

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

    private void checkUpdateIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyyMMdd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        String lastCheck = prefs.getString("last_update_check_date", "");
        if (today.equals(lastCheck)) return;
        prefs.edit().putString("last_update_check_date", today).apply();
        UpdateChecker.checkVersionInfo(this, new UpdateChecker.VersionCheckCallback() {
            @Override
            public void onResult(UpdateChecker.VersionInfo versionInfo) {
                if (!versionInfo.isLatest()) {
                    openUpdateActivity(versionInfo.getVersionName());
                }
            }

            @Override
            public void onError(String error) {
                // Silently ignore auto-check errors
            }
        });
    }

    private void openUpdateActivity(String targetVersionName) {
        Intent intent = new Intent(this, UpdateActivity.class);
        if (targetVersionName != null && !targetVersionName.trim().isEmpty()) {
            intent.putExtra("target_version_name", targetVersionName.trim());
        }
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (song.isBilibili()) {
            showBilibiliFunctionsOverlay(song);
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

        // Row 2: 设为铃声 + 定时关闭
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(4), 0, 0);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row2.addView(createFuncItem(R.drawable.ic_notifications, "设为铃声",
                v -> onFuncSetRingtone(song)));

        // Sleep timer - show remaining time if active, with live updates
        MaterialButton timerItem = createFuncItem(R.drawable.ic_timer,
                playerManager.isSleepTimerActive() ? "定时..." : "定时关闭",
                v -> onFuncSleepTimer());
        row2.addView(timerItem);

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

        // Row 3: 播放模式 + 倍速播放
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER);
        row3.setPadding(0, dp(4), 0, 0);
        row3.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int playModeIconRes;
        String playModeLabel;
        switch (playerManager.getPlayMode()) {
            case SINGLE_REPEAT:
                playModeIconRes = R.drawable.ic_repeat_one;
                playModeLabel = "单曲循环";
                break;
            case RANDOM:
                playModeIconRes = R.drawable.ic_shuffle;
                playModeLabel = "随机播放";
                break;
            case LIST_LOOP:
            default:
                playModeIconRes = R.drawable.ic_repeat;
                playModeLabel = "列表循环";
                break;
        }
        row3.addView(createFuncItem(playModeIconRes, playModeLabel,
                v -> onFuncCyclePlayMode()));
        float currentSpeed = playerManager.getPlaybackSpeed();
        String speedLabel = currentSpeed == 1.0f ? "倍速播放" : String.format("%.1fx", currentSpeed);
        row3.addView(createFuncItem(R.drawable.ic_speed, speedLabel,
                v -> onFuncPlaybackSpeed()));
        contentLayout.addView(row3);

        // Row 4: 音乐信息 + 评论
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

    private void showBilibiliFunctionsOverlay(Song song) {
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
        scrollView.setOnClickListener(v -> { });

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(16), dp(12), dp(16), dp(12));
        contentLayout.addView(createOverlayTitleBar("B站功能"));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row1.addView(createFuncItem(R.drawable.ic_queue_music, "视频列表",
                v -> onFuncShowBilibiliPlaylist(song)));
        row1.addView(createFuncItem(R.drawable.ic_get_app, "下载",
                v -> onFuncDownload(song)));
        contentLayout.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(4), 0, 0);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        MaterialButton timerItem = createFuncItem(R.drawable.ic_timer,
                playerManager.isSleepTimerActive() ? "定时..." : "定时关闭",
                v -> onFuncSleepTimer());
        row2.addView(timerItem);

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
        int playModeIconRes;
        String playModeLabel;
        switch (playerManager.getPlayMode()) {
            case SINGLE_REPEAT:
                playModeIconRes = R.drawable.ic_repeat_one;
                playModeLabel = "单曲循环";
                break;
            case RANDOM:
                playModeIconRes = R.drawable.ic_shuffle;
                playModeLabel = "随机播放";
                break;
            case LIST_LOOP:
            default:
                playModeIconRes = R.drawable.ic_repeat;
                playModeLabel = "列表循环";
                break;
        }
        row2.addView(createFuncItem(playModeIconRes, playModeLabel,
                v -> onFuncCyclePlayMode()));
        contentLayout.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(Gravity.CENTER);
        row3.setPadding(0, dp(4), 0, 0);
        row3.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        float currentSpeed = playerManager.getPlaybackSpeed();
        String speedLabel = currentSpeed == 1.0f ? "倍速播放" : String.format("%.1fx", currentSpeed);
        row3.addView(createFuncItem(R.drawable.ic_speed, speedLabel,
                v -> onFuncPlaybackSpeed()));
        row3.addView(createFuncItem(R.drawable.ic_info, "播放器信息",
                v -> onFuncPlayerInfo()));
        contentLayout.addView(row3);

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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        barParams.bottomMargin = dp(8);
        titleBar.setLayoutParams(barParams);

        // Centered title
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFE6E0E9);
        title.setTextSize(12);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        titleParams.setMarginEnd(dp(48));
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
        int closeSize = dp(48);
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
        stopRingtonePreview();
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

    private boolean isHeadphonesConnected() {
        try {
            return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        } catch (Exception e) {
            return false;
        }
    }

    private void adjustVolumeUp() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        showVolumeIndicator();
    }

    private void showVolumeIndicator() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        boolean hasVolumeInfo = max > 0;
        int percent = hasVolumeInfo ? Math.round(current * 100f / max) : 0;
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
        background.setColor(ContextCompat.getColor(this, R.color.colorAccent));
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
        float trackLeft = seekLocation[0] - rootLocation[0] + seekBar.getPaddingLeft();
        float trackRight = seekLocation[0] - rootLocation[0] + seekWidth - seekBar.getPaddingRight();
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
                ? 0xFF1D1B20 : ContextCompat.getColor(this, R.color.text_primary));
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

    private void onFuncDownload(Song song) {
        dismissOverlay();
        if (song.isBilibili() && DownloadManager.isDownloaded(song)) {
            Toast.makeText(this, "歌曲已下载", Toast.LENGTH_SHORT).show();
            return;
        }
        if (song.isBilibili()) {
            // Bilibili doesn't have quality tiers, download directly
            String cookie = getDownloadCookieForSong(song);
            Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
            DownloadManager.downloadSong(song, cookie, new DownloadManager.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    Toast.makeText(MainActivity.this, "下载完成: " + filePath, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Show quality selector before downloading
            showDownloadQualityOptions(song);
        }
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

    private void onFuncCyclePlayMode() {
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
        dismissOverlay();
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

        // Static rounded panel; only the lines inside scroll, so the panel's
        // rounded corners stay visible while scrolling.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_panel_rounded);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.CENTER;
        panelParams.setMargins(dp(16), dp(24), dp(16), dp(24));
        panel.setLayoutParams(panelParams);
        panel.setPadding(dp(16), dp(4), dp(16), dp(16));
        panel.addView(createOverlayTitleBar("播放器信息"));

        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        for (String line : playerManager.getCurrentPlayerInfoLines()) {
            TextView textView = new TextView(this);
            textView.setText(line);
            textView.setTextColor(0xFFE6E0E9);
            textView.setTextSize(12);
            textView.setPadding(dp(10), dp(6), dp(10), dp(6));
            lines.addView(textView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(lines);
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        overlayContainer.addView(panel);
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
        tvLyricsSongLabel.setTextColor(0xFFE6E0E9);
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
        tvLyricsTimeRef.setTextColor(0xFFCAC4D0);
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
        tvLoading.setTextColor(0xFFCAC4D0);
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

    private void onFuncShowBilibiliPlaylist(Song song) {
        dismissOverlay();
        openBilibiliPlaylist(song);
    }

    private void openBilibiliPlaylist(Song song) {
        if (song == null || !song.isBilibili()) {
            return;
        }
        Intent intent = new Intent(this, BilibiliPlaylistActivity.class);
        intent.putExtra("bvid", song.getBvid());
        intent.putExtra("video_title", song.getAlbum());
        intent.putExtra("owner_name", song.getArtist());
        startActivity(intent);
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
        customLabel.setTextColor(0xFFCAC4D0);
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
            if (song != null && !song.isBilibili()) {
                playerManager.switchCurrentSongQuality(quality);
            }
            Toast.makeText(this, "音质已设置: " + getQualityDisplayName(quality),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Show the quality selection overlay, fetching availability from the API.
     * @param saveAsPreferred  true = save selection to SharedPreferences (playback quality),
     *                         false = just call callback for one-time use (download quality)
     * @param song             the song to check quality availability for (may be null)
     */
    private void showQualityOptionsInternal(boolean saveAsPreferred, Song song,
                                             QualitySelectCallback callback) {
        FrameLayout rootView = (FrameLayout) getWindow().getDecorView()
                .findViewById(android.R.id.content);

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
        contentLayout.setPadding(dp(20), dp(16), dp(20), dp(16));

        String title = saveAsPreferred ? "播放音质" : "下载音质";
        contentLayout.addView(createOverlayTitleBar(title));

        // Placeholder for quality rows — filled once API returns
        LinearLayout qualityListLayout = new LinearLayout(this);
        qualityListLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.addView(qualityListLayout);

        // Loading indicator
        com.google.android.material.progressindicator.CircularProgressIndicator loading =
                new com.google.android.material.progressindicator.CircularProgressIndicator(this);
        loading.setIndicatorSize(dp(32));
        loading.setIndicatorColor(ContextCompat.getColor(this, R.color.colorPrimary));
        loading.setTrackColor(ContextCompat.getColor(this, R.color.surface_variant));
        loading.setIndeterminate(true);
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                dp(32), dp(32));
        loadParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadParams.topMargin = dp(8);
        loadParams.bottomMargin = dp(8);
        loading.setLayoutParams(loadParams);
        qualityListLayout.addView(loading);

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);

        String currentQuality = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("preferred_quality", "exhigh");
        if (saveAsPreferred && song != null && song.isForceLocalPlayback()) {
            qualityListLayout.removeAllViews();
            buildLocalQualityRows(qualityListLayout, song, currentQuality, callback);
            return;
        }

        // Fetch quality info from API (only for NetEase songs with a valid ID)
        long songId = (song != null && !song.isBilibili() && song.getId() > 0) ? song.getId() : 0;
        String cookie = playerManager.getCookie();

        if (songId > 0) {
            MusicApiHelper.getSongQualityInfo(songId, cookie, new MusicApiHelper.SongQualityCallback() {
                @Override
                public void onResult(MusicApiHelper.SongQualityInfo info) {
                    runOnUiThread(() -> {
                        qualityListLayout.removeAllViews();
                        buildQualityRows(qualityListLayout, song, info, currentQuality,
                                saveAsPreferred, callback);
                    });
                }
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        qualityListLayout.removeAllViews();
                        // Fallback: show all levels without availability info
                        buildQualityRows(qualityListLayout, song, null, currentQuality,
                                saveAsPreferred, callback);
                    });
                }
            });
        } else {
            qualityListLayout.removeAllViews();
            buildQualityRows(qualityListLayout, song, null, currentQuality, saveAsPreferred, callback);
        }
    }

    private void buildLocalQualityRows(LinearLayout container, Song song, String currentQuality,
                                       QualitySelectCallback callback) {
        java.util.List<String> localQualities = DownloadManager.getAvailableLocalQualities(song);
        String selectedLocalQuality = DownloadManager.detectLocalQualityFromPath(song.getUrl());
        if (selectedLocalQuality == null) {
            selectedLocalQuality = DownloadManager.getBestDownloadedQuality(song);
        }
        if (localQualities.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无本地音质列表");
            empty.setTextColor(0xFFCAC4D0);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(8), 0, dp(8));
            container.addView(empty);
            return;
        }
        for (String quality : localQualities) {
            boolean isSelected = quality.equals(selectedLocalQuality);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setMinimumHeight(dp(48));
            com.google.android.material.card.MaterialCardView card = createQualityCard(isSelected, true);
            card.addView(row);

            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(this);
            tvName.setText(getQualityShortName(quality));
            tvName.setTextColor(getResources().getColor(isSelected ? R.color.on_primary_container : R.color.text_primary));
            tvName.setTextSize(14);
            leftCol.addView(tvName);

            TextView tvDesc = new TextView(this);
            tvDesc.setText("本地已下载");
            tvDesc.setTextColor(0xFFCAC4D0);
            tvDesc.setTextSize(11);
            leftCol.addView(tvDesc);
            row.addView(leftCol);

            TextView badge = new TextView(this);
            badge.setText("本地");
            badge.setTextColor(0xFF4CAF50);
            badge.setTextSize(11);
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            bg.setColor(0x22FFFFFF);
            badge.setBackground(bg);
            row.addView(badge);

            final String selectedQuality = quality;
            card.setOnClickListener(v -> {
                callback.onSelected(selectedQuality);
                dismissOverlay();
            });
            container.addView(card);
        }
    }

    /**
     * Build quality option rows into the given layout.
     * @param info          song-specific quality info from API; null = show all without availability
     */
    private void buildQualityRows(LinearLayout container,
                                   Song song,
                                    MusicApiHelper.SongQualityInfo info,
                                    String currentQuality,
                                    boolean saveAsPreferred,
                                    QualitySelectCallback callback) {
        java.util.List<String> downloadedQualities = !saveAsPreferred && song != null
                ? DownloadManager.getAvailableLocalQualities(song)
                : java.util.Collections.emptyList();
        // level, shortName, bitrate description
        String[][] qualities = {
            {"standard", "标准",     "128K MP3"},
            {"higher",   "较高",     "192K MP3"},
            {"exhigh",   "极高",     "320K MP3"},
            {"lossless", "无损",     "FLAC"},
            {"hires",    "Hi-Res",   "Hi-Res FLAC"},
            {"jyeffect", "臻品声场", "高清环绕声"},
            {"sky",      "全景声",   "沉浸环绕声"},
            {"jymaster", "臻品母带", "超清母带"},
        };

        for (String[] q : qualities) {
            String level   = q[0];
            String name    = q[1];
            String bitrate = q[2];

            boolean isSelected = saveAsPreferred && level.equals(currentQuality);
            boolean alreadyDownloaded = !saveAsPreferred
                    && downloadedQualities.contains(level);

            // Determine tier badge from API info
            String tier;
            if (info == null) {
                // No API data — show static defaults based on commonly free levels
                int rank = MusicApiHelper.qualityLevelRank(level);
                if (rank <= 3) tier = "免费";        // standard(1)/higher(2)/exhigh(3)
                else tier = "VIP";
            } else {
                String t = info.getTier(level);
                switch (t) {
                    case "free":        tier = "免费"; break;
                    case "vip":         tier = "VIP";  break;
                    case "unavailable": tier = QUALITY_TIER_UNAVAILABLE; break;
                    default:            tier = "VIP";  break;
                }
            }

            if (alreadyDownloaded) {
                tier = QUALITY_TIER_DOWNLOADED;
            }

            boolean disabledForSelection = QUALITY_TIER_UNAVAILABLE.equals(tier)
                    || QUALITY_TIER_DOWNLOADED.equals(tier);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setMinimumHeight(dp(48));
            com.google.android.material.card.MaterialCardView card =
                    createQualityCard(isSelected, !disabledForSelection);
            card.addView(row);

            // Left column: name + bitrate
            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(this);
            tvName.setText(name);
            int nameColor = getResources().getColor(disabledForSelection ? R.color.text_disabled
                    : isSelected ? R.color.on_primary_container : R.color.text_primary);
            tvName.setTextColor(nameColor);
            tvName.setTextSize(14);
            leftCol.addView(tvName);

            TextView tvBitrate = new TextView(this);
            tvBitrate.setText(bitrate);
            tvBitrate.setTextColor(disabledForSelection ? 0x30FFFFFF : 0x80FFFFFF);
            tvBitrate.setTextSize(11);
            leftCol.addView(tvBitrate);

            row.addView(leftCol);

            // Right: tier badge
            int tierColor;
            switch (tier) {
                case "VIP":  tierColor = 0xFFFFAA00; break;
                case QUALITY_TIER_UNAVAILABLE: tierColor = 0x60FFFFFF; break;
                case QUALITY_TIER_DOWNLOADED: tierColor = 0xFF4CAF50; break;
                default:     tierColor = 0xFF4CAF50; break; // 免费
            }
            TextView tvTier = new TextView(this);
            tvTier.setText(tier);
            tvTier.setTextColor(tierColor);
            tvTier.setTextSize(11);
            tvTier.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable tierBg = new GradientDrawable();
            tierBg.setShape(GradientDrawable.RECTANGLE);
            tierBg.setCornerRadius(dp(4));
            tierBg.setColor(0x22FFFFFF);
            tvTier.setBackground(tierBg);
            row.addView(tvTier);

            if (!disabledForSelection) {
                card.setOnClickListener(v -> {
                    callback.onSelected(level);
                    dismissOverlay();
                });
            }

            container.addView(card);
        }
    }

    private com.google.android.material.card.MaterialCardView createQualityCard(boolean selected, boolean enabled) {
        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(this);
        card.setRadius(dp(14));
        card.setCardElevation(0);
        card.setCardBackgroundColor(getResources().getColor(selected ? R.color.primary_container : R.color.surface_elevated));
        card.setClickable(enabled);
        card.setFocusable(enabled);
        card.setEnabled(enabled);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        card.setLayoutParams(params);
        return card;
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
        switch (level) {
            case "standard": return "标准";
            case "higher":   return "较高";
            case "exhigh":   return "极高";
            case "lossless": return "无损";
            case "hires":    return "Hi-Res";
            case "jyeffect": return "臻品声场";
            case "sky":      return "全景声";
            case "jymaster": return "臻品母带";
            default:         return level;
        }
    }

    /** Full display name for quality (shown in toasts). */
    private String getQualityDisplayName(String level) {
        switch (level) {
            case "standard": return "标准 (128K)";
            case "higher":   return "较高 (192K)";
            case "exhigh":   return "极高 (320K)";
            case "lossless": return "无损 (FLAC)";
            case "hires":    return "Hi-Res";
            case "jyeffect": return "臻品声场";
            case "sky":      return "臻品全景声";
            case "jymaster": return "臻品母带";
            default:         return level;
        }
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

    private void onFuncSetRingtone(Song song) {
        dismissOverlay();
        // Check if song is downloaded first
        String mp3Path = DownloadManager.getDownloadedMp3Path(song);

        if (mp3Path != null) {
            // Already downloaded locally — use it directly
            showRingtoneClipOverlay(new File(mp3Path), song.getName());
        } else {
            // Cache audio to a temp file (don't save to downloads)
            Toast.makeText(this, "正在缓存歌曲...", Toast.LENGTH_SHORT).show();
            String cookie = getDownloadCookieForSong(song);
            SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
            String quality = prefs.getString("preferred_quality", "exhigh");

            File cacheDir = new File(android.os.Environment.getExternalStorageDirectory(),
                    "163Music/cache");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File tempFile = new File(cacheDir, "ringtone_temp_" + song.getId() + ".mp3");

            DownloadManager.downloadSongToFile(song, cookie, quality, tempFile,
                    new DownloadManager.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    runOnUiThread(() -> showRingtoneClipOverlay(new File(filePath), song.getName(),
                            true /* deleteAfterSet */));
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "缓存失败，无法设为铃声", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private MediaPlayer ringtonePreviewPlayer;

    private String getDownloadCookieForSong(Song song) {
        if (song != null && song.isBilibili()) {
            SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
            return prefs.getString("bilibili_cookie", "");
        }
        return playerManager.getCookie();
    }

    private void showRingtoneClipOverlay(File file, String songTitle) {
        showRingtoneClipOverlay(file, songTitle, false);
    }

    private void showRingtoneClipOverlay(File file, String songTitle, boolean deleteTempAfterSet) {
        // Get song duration
        int durationMs;
        try {
            MediaPlayer tmp = new MediaPlayer();
            tmp.setDataSource(file.getAbsolutePath());
            tmp.prepare();
            durationMs = tmp.getDuration();
            tmp.release();
        } catch (Exception e) {
            Toast.makeText(this, "无法读取歌曲时长", Toast.LENGTH_SHORT).show();
            return;
        }
        final int totalSec = durationMs / 1000;
        if (totalSec <= 0) {
            Toast.makeText(this, "歌曲时长无效", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xCC000000);
        addSwipeToDismiss(overlayContainer);

        // Wrap content in ScrollView for small watch screens
        ScrollView scrollView = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setFillViewport(true);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(16), dp(12), dp(16), dp(12));
        contentLayout.setOnClickListener(v -> { /* consume click */ });

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        contentLayout.setLayoutParams(contentParams);

        // Title bar with close button
        contentLayout.addView(createOverlayTitleBar("节选铃声"));

        // Song name
        TextView tvSong = new TextView(this);
        tvSong.setText(songTitle);
        tvSong.setTextColor(0xFFCAC4D0);
        tvSong.setTextSize(12);
        tvSong.setGravity(Gravity.CENTER);
        tvSong.setPadding(0, 0, 0, dp(8));
        contentLayout.addView(tvSong);

        // Start seekbar
        final int[] startSec = {0};
        final int[] endSec = {Math.min(totalSec, 30)};

        TextView tvStart = new TextView(this);
        tvStart.setText("起始: " + startSec[0] + "s");
        tvStart.setTextColor(0xFFE6E0E9);
        tvStart.setTextSize(12);
        tvStart.setPadding(0, dp(4), 0, 0);
        contentLayout.addView(tvStart);

        Slider sbStart = new Slider(this);
        sbStart.setValueFrom(0f);
        sbStart.setValueTo((float) totalSec);
        sbStart.setStepSize(1f);
        sbStart.setValue((float) startSec[0]);
        sbStart.setLabelBehavior(LabelFormatter.LABEL_GONE);
        sbStart.setTickVisible(false);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbStart.setLayoutParams(seekParams);
        contentLayout.addView(sbStart);

        // End seekbar
        TextView tvEnd = new TextView(this);
        tvEnd.setText("结束: " + endSec[0] + "s");
        tvEnd.setTextColor(0xFFE6E0E9);
        tvEnd.setTextSize(12);
        tvEnd.setPadding(0, dp(4), 0, 0);
        contentLayout.addView(tvEnd);

        Slider sbEnd = new Slider(this);
        sbEnd.setValueFrom(0f);
        sbEnd.setValueTo((float) totalSec);
        sbEnd.setStepSize(1f);
        sbEnd.setValue((float) endSec[0]);
        sbEnd.setLabelBehavior(LabelFormatter.LABEL_GONE);
        sbEnd.setTickVisible(false);
        sbEnd.setLayoutParams(seekParams);
        contentLayout.addView(sbEnd);

        // Duration display
        TextView tvDuration = new TextView(this);
        tvDuration.setText("节选: " + startSec[0] + "s - " + endSec[0] + "s (" + (endSec[0] - startSec[0]) + "秒)");
        tvDuration.setTextColor(0xFFCAC4D0);
        tvDuration.setTextSize(12);
        tvDuration.setGravity(Gravity.CENTER);
        tvDuration.setPadding(0, dp(8), 0, dp(8));
        contentLayout.addView(tvDuration);

        TextView tvChorusRange = new TextView(this);
        tvChorusRange.setText(hasChorusRange()
                ? "高潮: " + millisToFloorSeconds(currentChorusStartMs) + "s - " + millisToCeilSeconds(currentChorusEndMs) + "s"
                : "高潮: 暂无数据");
        tvChorusRange.setTextColor(ContextCompat.getColor(this,
                hasChorusRange() ? R.color.colorAccent : R.color.text_secondary));
        tvChorusRange.setTextSize(11);
        tvChorusRange.setGravity(Gravity.CENTER);
        tvChorusRange.setPadding(0, 0, 0, dp(8));
        contentLayout.addView(tvChorusRange);

        Runnable updateDuration = () -> {
            int dur = endSec[0] - startSec[0];
            tvDuration.setText("节选: " + startSec[0] + "s - " + endSec[0] + "s (" + dur + "秒)");
        };

        sbStart.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int progress = Math.round(value);
                if (progress >= endSec[0]) progress = endSec[0] - 1;
                if (progress < 0) progress = 0;
                if (Math.round(slider.getValue()) != progress) {
                    slider.setValue((float) progress);
                }
                startSec[0] = progress;
                tvStart.setText("起始: " + progress + "s");
                updateDuration.run();
            }
        });

        sbEnd.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int progress = Math.round(value);
                if (progress <= startSec[0]) progress = startSec[0] + 1;
                if (progress > totalSec) progress = totalSec;
                if (Math.round(slider.getValue()) != progress) {
                    slider.setValue((float) progress);
                }
                endSec[0] = progress;
                tvEnd.setText("结束: " + progress + "s");
                updateDuration.run();
            }
        });

        int grayButtonColor = ContextCompat.getColor(this, R.color.surface_elevated);
        int primaryButtonColor = ContextCompat.getColor(this, R.color.colorPrimary);

        LinearLayout topButtonRow = new LinearLayout(this);
        topButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        topButtonRow.setGravity(Gravity.CENTER);
        topButtonRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialButton btnSmartClip = createOverlayActionButton("智能截取", grayButtonColor, false);
        LinearLayout.LayoutParams smartClipParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        smartClipParams.rightMargin = dp(4);
        btnSmartClip.setLayoutParams(smartClipParams);
        btnSmartClip.setOnClickListener(v -> applySmartClipRange(totalSec, startSec, endSec, sbStart, sbEnd,
                tvStart, tvEnd, updateDuration, tvChorusRange));
        topButtonRow.addView(btnSmartClip);

        MaterialButton btnPreview = createOverlayActionButton("试听", grayButtonColor, false);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        previewParams.leftMargin = dp(4);
        btnPreview.setLayoutParams(previewParams);
        btnPreview.setOnClickListener(v -> {
            if (endSec[0] <= startSec[0]) {
                Toast.makeText(this, "请选择有效的时间范围", Toast.LENGTH_SHORT).show();
                return;
            }
            previewRingtoneClip(file, startSec[0] * 1000, endSec[0] * 1000);
        });
        topButtonRow.addView(btnPreview);
        contentLayout.addView(topButtonRow);

        MaterialButton btnConfirm = createOverlayActionButton("确认", primaryButtonColor, true);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmParams.topMargin = dp(8);
        btnConfirm.setLayoutParams(confirmParams);
        btnConfirm.setOnClickListener(v -> {
            if (endSec[0] <= startSec[0]) {
                Toast.makeText(this, "请选择有效的时间范围", Toast.LENGTH_SHORT).show();
                return;
            }
            stopRingtonePreview();
            String clipTitle = songTitle + " (" + startSec[0] + "s-" + endSec[0] + "s)";
            setRingtoneFromFile(file, clipTitle, startSec[0], endSec[0]);
            if (deleteTempAfterSet) {
                // Delete temp cached audio file after setting ringtone
                try { file.delete(); } catch (Exception ignored) {}
            }
            dismissOverlay();
        });
        contentLayout.addView(btnConfirm);

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);
    }

    private void previewRingtoneClip(File file, int startMs, int endMs) {
        stopRingtonePreview();
        // Pause current music playback if playing
        if (playerManager.isPlaying()) {
            playerManager.pause();
        }
        try {
            ringtonePreviewPlayer = new MediaPlayer();
            ringtonePreviewPlayer.setDataSource(file.getAbsolutePath());
            ringtonePreviewPlayer.prepare();
            ringtonePreviewPlayer.seekTo(startMs);
            ringtonePreviewPlayer.start();
            // Stop at endMs
            final Handler previewHandler = new Handler();
            previewHandler.postDelayed(() -> stopRingtonePreview(), endMs - startMs);
        } catch (Exception e) {
            Toast.makeText(this, "试听失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRingtonePreview() {
        if (ringtonePreviewPlayer != null) {
            try {
                if (ringtonePreviewPlayer.isPlaying()) {
                    ringtonePreviewPlayer.stop();
                }
                ringtonePreviewPlayer.release();
            } catch (Exception ignored) {}
            ringtonePreviewPlayer = null;
        }
    }

    private void setRingtoneFromFile(File file, String title, int startSec, int endSec) {
        try {
            // Check WRITE_SETTINGS permission on Android M+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.System.canWrite(this)) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    Toast.makeText(this, "请授予修改系统设置权限后重试", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Create a clipped copy if valid range, otherwise use original
            File clipFile = file;
            if (endSec > startSec) {
                File clipped = createClippedAudio(file, startSec * 1000, endSec * 1000, title);
                if (clipped != null) {
                    clipFile = clipped;
                }
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, clipFile.getAbsolutePath());
            values.put(MediaStore.MediaColumns.TITLE, title);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_MUSIC, false);

            Uri uri = MediaStore.Audio.Media.getContentUriForPath(clipFile.getAbsolutePath());

            // Delete existing entry if any
            getContentResolver().delete(uri,
                    MediaStore.MediaColumns.DATA + "=?",
                    new String[]{clipFile.getAbsolutePath()});

            Uri newUri = getContentResolver().insert(uri, values);
            if (newUri != null) {
                RingtoneManager.setActualDefaultRingtoneUri(this,
                        RingtoneManager.TYPE_RINGTONE, newUri);
                // Save ringtone info for management
                RingtoneManagerHelper ringtoneHelper = new RingtoneManagerHelper(this);
                ringtoneHelper.addRingtone(title, clipFile.getAbsolutePath(), startSec, endSec);
                Toast.makeText(this, "已设为铃声", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "设置铃声失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "设置铃声失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Create a clipped audio file using MediaExtractor.
     * For MP3 files, writes raw frames directly (MediaMuxer doesn't support MP3 in MPEG4 container).
     * For other formats (AAC/M4A), uses MediaMuxer with MPEG4 container.
     * Returns null if clipping fails.
     */
    private File createClippedAudio(File sourceFile, int startMs, int endMs, String title) {
        try {
            File ringtoneDir = new File(android.os.Environment.getExternalStorageDirectory(),
                    "163Music/Ringtones");
            if (!ringtoneDir.exists()) ringtoneDir.mkdirs();

            // Sanitize title for filename
            String safeName = title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5()\\-_ ]", "_");

            android.media.MediaExtractor extractor = new android.media.MediaExtractor();
            extractor.setDataSource(sourceFile.getAbsolutePath());

            int audioTrack = -1;
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    break;
                }
            }

            if (audioTrack < 0) {
                extractor.release();
                return null;
            }

            extractor.selectTrack(audioTrack);

            // For MP3 audio, write raw frames directly since MediaMuxer MPEG4 doesn't support MP3
            if ("audio/mpeg".equals(mime)) {
                return createClippedMp3Raw(extractor, safeName, ringtoneDir, startMs, endMs);
            }

            // For other formats (AAC etc.), use MediaMuxer with M4A container
            android.media.MediaFormat format = extractor.getTrackFormat(audioTrack);
            File outputFile = new File(ringtoneDir, safeName + ".m4a");

            android.media.MediaMuxer muxer = new android.media.MediaMuxer(
                    outputFile.getAbsolutePath(),
                    android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerTrack = muxer.addTrack(format);
            muxer.start();

            // Seek to start position
            extractor.seekTo(startMs * 1000L, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 256);
            android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                long sampleTimeUs = extractor.getSampleTime();
                if (sampleTimeUs > endMs * 1000L) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = sampleTimeUs - startMs * 1000L;
                bufferInfo.flags = extractor.getSampleFlags();

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo);
                extractor.advance();
            }

            muxer.stop();
            muxer.release();
            extractor.release();

            return outputFile;
        } catch (Exception e) {
            Log.w(TAG, "Audio clipping failed", e);
            return null;
        }
    }

    /**
     * Create a clipped MP3 file by writing raw audio frames directly.
     * MP3 frames are self-contained, so concatenating them produces a valid MP3 file.
     */
    private File createClippedMp3Raw(android.media.MediaExtractor extractor,
                                      String safeName, File ringtoneDir,
                                      int startMs, int endMs) {
        File outputFile = new File(ringtoneDir, safeName + ".mp3");
        try {
            extractor.seekTo(startMs * 1000L, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 256);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile);

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                long sampleTimeUs = extractor.getSampleTime();
                if (sampleTimeUs > endMs * 1000L) break;

                byte[] data = new byte[sampleSize];
                buffer.position(0);
                buffer.get(data, 0, sampleSize);
                fos.write(data);
                extractor.advance();
            }

            fos.close();
            extractor.release();
            return outputFile;
        } catch (Exception e) {
            Log.w(TAG, "MP3 raw clipping failed", e);
            extractor.release();
            if (outputFile.exists()) outputFile.delete();
            return null;
        }
    }

    // Keep the old method for backward compatibility
    private void setRingtoneFromFile(File file, String title) {
        setRingtoneFromFile(file, title, 0, 0);
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
        customLabel.setTextColor(0xFFCAC4D0);
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
        tvRemaining.setTextColor(0xFFE6E0E9);
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
        // Update playlist indicator visibility
        updatePlaylistIndicator();
        updateChorusMarker(playerManager.getDuration());
    }

    private void updatePlaylistIndicator() {
        if (btnPlaylistIndicator != null) {
            Song currentSong = playerManager.getCurrentSong();
            if (playerManager.hasSourcePlaylist()
                    || (currentSong != null && currentSong.isBilibili())) {
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
        if (song != null && song.getId() > 0 && song.getFee() == 0 && !song.isBilibili()) {
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

    private final Runnable seekBarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isUserSeeking && playerManager.isPlaying()) {
                int current = playerManager.getCurrentPosition();
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    seekBar.setValue((float) (1000L * current / duration));
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(duration));
                    updateChorusMarker(duration);
                }
            }
            seekHandler.postDelayed(this, 500);
        }
    };

    private void startSeekBarUpdate() {
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
        super.onPause();
        stopRingtonePreview();
        lyricsScrollHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtonePreview();
        stopSeekBarUpdate();
        lyricsScrollHandler.removeCallbacksAndMessages(null);
        volumeHandler.removeCallbacksAndMessages(null);
        dismissVolumeIndicator();
        NetworkImageLoader.cancelAll();
    }
}
