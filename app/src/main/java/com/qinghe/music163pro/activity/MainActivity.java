package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.manager.FavoritesManager;
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.manager.RingtoneManagerHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.service.MusicPlaybackService;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.UpdateChecker;

import java.io.File;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerCallback {

    private static final String TAG = "MainActivity";
    private static final String HEART_OUTLINE = "\u2661";
    private static final String HEART_FILLED = "\u2665";
    private static final int STORAGE_PERMISSION_REQUEST = 100;
    private static final int VOLUME_INDICATOR_SIDE_MARGIN_DP = 24;
    private static final int VOLUME_INDICATOR_MIN_WIDTH_DP = 96;
    private static final int VOLUME_INDICATOR_COMPACT_BREAKPOINT_DP = 132;
    private static final int VOLUME_INDICATOR_MAX_WIDTH_DP = 176;
    private static final int VOLUME_INDICATOR_TOP_MARGIN_DP = 10;
    private static final int VOLUME_INDICATOR_ANIM_DURATION_MS = 160;
    private static final float VOLUME_INDICATOR_INITIAL_SCALE = 0.96f;

    private TextView tvSongName;
    private TextView tvArtist;
    private ImageView btnPlay;
    private ImageView btnFuncMore;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private MusicPlayerManager playerManager;
    private FavoritesManager favoritesManager;
    private AudioManager audioManager;
    private final Handler seekHandler = new Handler();
    private boolean isUserSeeking = false;
    private boolean serviceStarted = false;

    // Playlist indicator (top-left)
    private ImageView btnPlaylistIndicator;

    // Functions overlay
    private FrameLayout overlayContainer;
    private Handler overlayTimerHandler;
    private Runnable overlayTimerRunnable;

    // Volume indicator
    private View volumeIndicator;
    private final Handler volumeHandler = new Handler();

    // Activity-level gesture detector for swipe handling
    private GestureDetector activityGestureDetector;

    // Lyrics overlay state
    private boolean lyricsOverlayShowing = false;
    private final java.util.List<LyricLine> lyricLines = new java.util.ArrayList<>();
    private final java.util.List<TextView> lyricViews = new java.util.ArrayList<>();
    private int currentHighlightIndex = -1;
    private ScrollView lyricsScrollView;
    private LinearLayout lyricsContainer;
    private final Handler lyricsScrollHandler = new Handler();
    private Runnable lyricsScrollRunnable;
    private TextView tvLyricsSongLabel;
    private TextView tvLyricsTimeRef;
    // Translation lyrics
    private String currentTlyricText; // Raw translated LRC text for current song
    private boolean translationEnabled; // Whether translation is currently showing
    private TextView btnTranslationToggle; // Toggle button in lyrics overlay
    private final java.util.Map<Long, String> translationMap = new java.util.HashMap<>(); // timeMs -> translated text

    private static class LyricLine {
        long timeMs;
        String text;
        String translation; // Optional translated text
        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize file logging
        MusicLog.init(new File("/sdcard/163Music"));

        tvSongName = findViewById(R.id.tv_song_name);
        tvArtist = findViewById(R.id.tv_artist);
        btnPlay = findViewById(R.id.btn_play);
        btnFuncMore = findViewById(R.id.btn_favorite);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        ImageView btnPrev = findViewById(R.id.btn_prev);
        ImageView btnNext = findViewById(R.id.btn_next);
        ImageView btnVolDown = findViewById(R.id.btn_vol_down);
        ImageView btnVolUp = findViewById(R.id.btn_vol_up);
        ImageView btnMore = findViewById(R.id.btn_more);
        btnPlaylistIndicator = findViewById(R.id.btn_playlist_indicator);

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
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                showVolumeIndicator();
        });

        // Changed: "more functions" overlay instead of toggle favorite
        btnFuncMore.setOnClickListener(v -> showFunctionsOverlay());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int duration = playerManager.getDuration();
                    if (duration > 0) {
                        tvCurrentTime.setText(formatTime((int) ((long) progress * duration / 1000)));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    int seekPos = (int) ((long) bar.getProgress() * duration / 1000);
                    playerManager.seekTo(seekPos);
                }
                isUserSeeking = false;
            }
        });

        btnMore.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MoreActivity.class)));

        // Playlist indicator: click to open playlist detail
        btnPlaylistIndicator.setOnClickListener(v -> {
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

        // Start foreground service to keep alive
        startPlaybackService("163音乐", "等待播放", false);

        // Request storage permission for saving favorites to /sdcard/163Music/
        requestStoragePermission();

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
                        if (overlayContainer != null) {
                            dismissOverlay();
                        } else {
                            finish();
                        }
                        return true;
                    } else {
                        // Left swipe: show lyrics (only when no overlay is showing)
                        if (overlayContainer == null) {
                            showLyricsOverlay();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (activityGestureDetector != null) {
            activityGestureDetector.onTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
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
        UpdateChecker.checkVersion(this, new UpdateChecker.CheckCallback() {
            @Override
            public void onResult(boolean isLatest) {
                if (!isLatest) {
                    startActivity(new Intent(MainActivity.this, UpdateActivity.class));
                }
            }

            @Override
            public void onError(String error) {
                // Silently ignore auto-check errors
            }
        });
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
        LinearLayout timerItem = createFuncItem(R.drawable.ic_timer,
                playerManager.isSleepTimerActive() ? "定时..." : "定时关闭",
                v -> onFuncSleepTimer());
        row2.addView(timerItem);

        // Find the label view in timerItem (second child) for live updates
        final TextView timerLabelView = (TextView) timerItem.getChildAt(1);
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.bottomMargin = dp(8);
        titleBar.setLayoutParams(barParams);

        // Centered title
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.CENTER;
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        // Close button on the right
        ImageView btnClose = new ImageView(this);
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setColorFilter(0xFFFFFFFF);
        int closeSize = dp(18);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSize, closeSize);
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        closeParams.setMarginEnd(dp(4));
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

    private LinearLayout createFuncItem(int iconRes, String label, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        item.setLayoutParams(itemParams);
        item.setClickable(true);
        item.setFocusable(true);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        int iconSize = dp(24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconView.setLayoutParams(iconParams);
        item.addView(iconView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFFFFFFFF);
        labelView.setTextSize(12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(4), 0, 0);
        item.addView(labelView);

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
        if (lyricsOverlayShowing) {
            stopLyricsScrollSync();
        }
        if (overlayContainer != null) {
            FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
            rootView.removeView(overlayContainer);
            overlayContainer = null;
        }
    }

    /**
     * Show a custom volume indicator overlay on the watch screen.
     * Displays current volume / max volume with a visual bar, auto-dismisses after 1.5 seconds.
     */
    private void showVolumeIndicator() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        if (volumeIndicator != null) {
            rootView.removeView(volumeIndicator);
        }

        boolean hasVolumeInfo = max > 0;
        int percent = hasVolumeInfo ? Math.round(current * 100f / max) : 0;
        int availableWidth = Math.max(
                getResources().getDisplayMetrics().widthPixels - dp(VOLUME_INDICATOR_SIDE_MARGIN_DP * 2),
                dp(VOLUME_INDICATOR_MIN_WIDTH_DP));
        int popupWidth = availableWidth > dp(VOLUME_INDICATOR_COMPACT_BREAKPOINT_DP)
                ? Math.min(availableWidth, dp(VOLUME_INDICATOR_MAX_WIDTH_DP))
                : availableWidth;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_volume_indicator));
        card.setElevation(dp(6));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setClickable(false);
        card.setFocusable(false);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout iconWrap = new FrameLayout(this);
        LinearLayout.LayoutParams iconWrapParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconWrap.setLayoutParams(iconWrapParams);
        iconWrap.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_volume_indicator_icon));

        ImageView iconView = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        iconView.setLayoutParams(iconParams);
        iconView.setImageResource(current == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        iconView.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
        iconWrap.addView(iconView);

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleWrapParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleWrapParams.leftMargin = dp(10);
        titleWrap.setLayoutParams(titleWrapParams);

        TextView titleView = new TextView(this);
        titleView.setText("当前音量");
        titleView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        titleView.setTextSize(11);

        TextView valueView = new TextView(this);
        valueView.setText(hasVolumeInfo ? (current + "/" + max) : "不可用");
        valueView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        valueView.setTextSize(16);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);

        titleWrap.addView(titleView);
        titleWrap.addView(valueView);

        TextView percentView = new TextView(this);
        percentView.setText(hasVolumeInfo ? (percent + "%") : "--");
        percentView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        percentView.setTextSize(14);
        percentView.setTypeface(percentView.getTypeface(), android.graphics.Typeface.BOLD);

        topRow.addView(iconWrap);
        topRow.addView(titleWrap);
        topRow.addView(percentView);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        progressParams.topMargin = dp(10);
        progressBar.setLayoutParams(progressParams);
        progressBar.setMax(hasVolumeInfo ? max : 1);
        progressBar.setProgress(hasVolumeInfo ? current : 0);
        progressBar.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.progress_volume_indicator));

        card.addView(topRow);
        card.addView(progressBar);
        volumeIndicator = card;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                popupWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(VOLUME_INDICATOR_TOP_MARGIN_DP);
        volumeIndicator.setLayoutParams(params);

        rootView.addView(volumeIndicator);
        volumeIndicator.setAlpha(0f);
        volumeIndicator.setScaleX(VOLUME_INDICATOR_INITIAL_SCALE);
        volumeIndicator.setScaleY(VOLUME_INDICATOR_INITIAL_SCALE);
        volumeIndicator.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(VOLUME_INDICATOR_ANIM_DURATION_MS)
                .start();

        // Auto-dismiss after 1.5 seconds
        volumeHandler.removeCallbacksAndMessages(null);
        volumeHandler.postDelayed(() -> {
            if (volumeIndicator != null) {
                rootView.removeView(volumeIndicator);
                volumeIndicator = null;
            }
        }, 1500);
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
        if (DownloadManager.isDownloaded(song)) {
            Toast.makeText(this, "歌曲已下载", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
        String cookie = playerManager.getCookie();
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
        intent.putExtra("song_name", song.getName());
        intent.putExtra("artist_name", song.getArtist());
        intent.putExtra("artist_id", 0L); // Will be extracted from wiki API
        intent.putExtra("cookie", playerManager.getCookie());
        startActivity(intent);
    }

    private void onFuncComments(Song song) {
        dismissOverlay();
        Intent intent = new Intent(this, CommentActivity.class);
        intent.putExtra("song_id", song.getId());
        intent.putExtra("song_name", song.getName());
        intent.putExtra("cookie", playerManager.getCookie());
        startActivity(intent);
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
        new android.app.AlertDialog.Builder(this)
                .setTitle("添加到歌单")
                .setItems(names, (dialog, which) -> {
                    com.qinghe.music163pro.model.PlaylistInfo selected = playlists.get(which);
                    addSongToPlaylist(song, selected);
                })
                .setNegativeButton("取消", null)
                .show();
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

        FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);

        overlayContainer = new FrameLayout(this);
        overlayContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.setBackgroundColor(0xFF1E1E1E);
        // Consume all touch events to prevent underlying buttons from receiving clicks
        overlayContainer.setOnTouchListener((v, event) -> true);
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
        tvLyricsSongLabel.setText(song.getName() + " - " + song.getArtist());
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
        translationEnabled = transPrefs.getBoolean("lyrics_translation", false);
        btnTranslationToggle = new TextView(this);
        btnTranslationToggle.setText(translationEnabled ? "译✓" : "译");
        btnTranslationToggle.setTextColor(translationEnabled ? 0xFFBB86FC : 0x80FFFFFF);
        btnTranslationToggle.setTextSize(12);
        btnTranslationToggle.setGravity(Gravity.CENTER);
        btnTranslationToggle.setPadding(dp(6), dp(4), dp(6), dp(4));
        btnTranslationToggle.setClickable(true);
        btnTranslationToggle.setFocusable(true);
        btnTranslationToggle.setVisibility(View.GONE); // Hidden until translation is available
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        toggleParams.setMarginEnd(dp(2));
        btnTranslationToggle.setLayoutParams(toggleParams);
        btnTranslationToggle.setOnClickListener(v -> toggleLyricsTranslation());
        topBar.addView(btnTranslationToggle);

        mainLayout.addView(topBar);

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
        lyricsContainer.setPadding(dp(12), dp(40), dp(12), dp(40));
        lyricsScrollView.addView(lyricsContainer);
        mainLayout.addView(lyricsScrollView);

        // Time display at bottom
        tvLyricsTimeRef = new TextView(this);
        tvLyricsTimeRef.setTextColor(0x80FFFFFF);
        tvLyricsTimeRef.setTextSize(10);
        tvLyricsTimeRef.setGravity(Gravity.CENTER);
        tvLyricsTimeRef.setPadding(0, dp(2), 0, dp(4));
        tvLyricsTimeRef.setText("← 右滑返回");
        mainLayout.addView(tvLyricsTimeRef);

        overlayContainer.addView(mainLayout);
        rootView.addView(overlayContainer);
        lyricsOverlayShowing = true;

        // Load lyrics
        loadLyricsForOverlay(song, tvLyricsTimeRef);
    }

    private void loadLyricsForOverlay(Song song, TextView tvLyricsTime) {
        // Try local .lrc file first
        String localLrc = loadLocalLrc(song);
        if (localLrc != null && !localLrc.isEmpty()) {
            // Also try to load local translated lyrics
            String localTlyric = loadLocalTlyric(song);
            currentTlyricText = localTlyric;
            parseLrc(localLrc);
            if (localTlyric != null && !localTlyric.isEmpty()) {
                parseTranslationLrc(localTlyric);
                applyTranslationsToLyrics();
                if (btnTranslationToggle != null) {
                    btnTranslationToggle.setVisibility(View.VISIBLE);
                }
            }
            displayLyricsInOverlay();
            startLyricsScrollSync(tvLyricsTime);
            return;
        }

        // Fetch from API (with translation)
        if (song.getId() <= 0) {
            showNoLyricsInOverlay();
            return;
        }

        // Show loading
        lyricsContainer.removeAllViews();
        TextView tvLoading = new TextView(this);
        tvLoading.setText("加载歌词中...");
        tvLoading.setTextColor(0xB3FFFFFF);
        tvLoading.setTextSize(13);
        tvLoading.setGravity(Gravity.CENTER);
        lyricsContainer.addView(tvLoading);

        String cookie = playerManager.getCookie();
        MusicApiHelper.getLyricsWithTranslation(song.getId(), cookie, new MusicApiHelper.LyricsFullCallback() {
            @Override
            public void onResult(String lrcText, String tlyricText) {
                if (lrcText == null || lrcText.isEmpty()) {
                    showNoLyricsInOverlay();
                    return;
                }
                currentTlyricText = tlyricText;
                parseLrc(lrcText);
                if (tlyricText != null && !tlyricText.isEmpty()) {
                    parseTranslationLrc(tlyricText);
                    applyTranslationsToLyrics();
                    if (btnTranslationToggle != null) {
                        btnTranslationToggle.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (btnTranslationToggle != null) {
                        btnTranslationToggle.setVisibility(View.GONE);
                    }
                }
                displayLyricsInOverlay();
                startLyricsScrollSync(tvLyricsTime);
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

    private static final java.util.regex.Pattern LRC_PATTERN =
            java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.?(\\d{0,3})\\](.*)");

    private void parseLrc(String lrcText) {
        lyricLines.clear();
        translationMap.clear();
        String[] lines = lrcText.split("\n");
        for (String line : lines) {
            java.util.regex.Matcher matcher = LRC_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
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
                long timeMs = (long) min * 60 * 1000 + (long) sec * 1000 + ms;
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
                long timeMs = (long) min * 60 * 1000 + (long) sec * 1000 + ms;
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    translationMap.put(timeMs, text);
                }
            }
        }
    }

    /**
     * Apply translations from translationMap to each lyricLine's translation field.
     */
    private void applyTranslationsToLyrics() {
        for (LyricLine line : lyricLines) {
            String trans = translationMap.get(line.timeMs);
            line.translation = trans; // may be null if no translation for this line
        }
    }

    /**
     * Toggle lyrics translation display on/off.
     * Saves preference persistently.
     */
    private void toggleLyricsTranslation() {
        translationEnabled = !translationEnabled;
        // Save preference
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        prefs.edit().putBoolean("lyrics_translation", translationEnabled).apply();
        // Update button appearance
        if (btnTranslationToggle != null) {
            btnTranslationToggle.setText(translationEnabled ? "译✓" : "译");
            btnTranslationToggle.setTextColor(translationEnabled ? 0xFFBB86FC : 0x80FFFFFF);
        }
        // Re-display lyrics with or without translation
        displayLyricsInOverlay();
        // Restart sync to update highlighting
        if (tvLyricsTimeRef != null) {
            lyricsScrollHandler.removeCallbacksAndMessages(null);
            currentHighlightIndex = -1;
            startLyricsScrollSync(tvLyricsTimeRef);
        }
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
            tv.setTextColor(0xB3FFFFFF);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            lineLayout.addView(tv);
            lyricViews.add(tv);

            // Translation text (if available and enabled)
            if (translationEnabled && line.translation != null && !line.translation.isEmpty()) {
                TextView tvTrans = new TextView(this);
                tvTrans.setText(line.translation);
                tvTrans.setTextColor(0x61FFFFFF);
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
        tv.setTextColor(0xB3FFFFFF);
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

                    tvLyricsTime.setText(formatTime(currentPos) + " / " + formatTime(duration) + "  ← 右滑返回");

                    int newIndex = -1;
                    for (int i = 0; i < lyricLines.size(); i++) {
                        if (lyricLines.get(i).timeMs <= currentPos) {
                            newIndex = i;
                        } else {
                            break;
                        }
                    }

                    if (newIndex != currentHighlightIndex && newIndex >= 0) {
                        if (currentHighlightIndex >= 0 && currentHighlightIndex < lyricViews.size()) {
                            lyricViews.get(currentHighlightIndex).setTextColor(0xB3FFFFFF);
                            lyricViews.get(currentHighlightIndex).setTextSize(13);
                        }
                        currentHighlightIndex = newIndex;
                        if (currentHighlightIndex < lyricViews.size()) {
                            TextView currentView = lyricViews.get(currentHighlightIndex);
                            currentView.setTextColor(0xFFFFFFFF);
                            currentView.setTextSize(14);

                            // Scroll to center the current line
                            // The lyric TextView is inside a lineLayout container,
                            // so use the parent container's position for scrolling
                            currentView.post(() -> {
                                if (lyricsScrollView == null) return;
                                int scrollViewHeight = lyricsScrollView.getHeight();
                                View parentLayout = (View) currentView.getParent();
                                int targetTop = parentLayout != null ? parentLayout.getTop() : currentView.getTop();
                                int targetHeight = parentLayout != null ? parentLayout.getHeight() : currentView.getHeight();
                                int scrollTo = targetTop - (scrollViewHeight / 2) + (targetHeight / 2);
                                lyricsScrollView.smoothScrollTo(0, Math.max(0, scrollTo));
                            });
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
        lyricLines.clear();
        lyricViews.clear();
        translationMap.clear();
        currentTlyricText = null;
        btnTranslationToggle = null;
        currentHighlightIndex = -1;
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
        contentLayout.setBackgroundColor(0xFF1E1E1E);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        contentLayout.setLayoutParams(contentParams);
        contentLayout.setOnClickListener(v -> { /* consume click */ });

        // Title bar
        contentLayout.addView(createOverlayTitleBar("播放列表 (" + playlist.size() + "首)"));

        // Song list in a ScrollView
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout);

        int currentIndex = playerManager.getCurrentIndex();
        for (int i = 0; i < playlist.size(); i++) {
            final int index = i;
            Song song = playlist.get(i);

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(dp(8), dp(6), dp(8), dp(6));
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);

            // Highlight current playing song
            if (i == currentIndex) {
                itemLayout.setBackgroundColor(0xFF1E1E1E);
            }

            TextView tvName = new TextView(this);
            String prefix = (i == currentIndex) ? "▶ " : (i + 1) + ". ";
            tvName.setText(prefix + song.getName());
            tvName.setTextColor(i == currentIndex ? 0xFFBB86FC : 0xFFFFFFFF);
            tvName.setTextSize(13);
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemLayout.addView(tvName);

            TextView tvArtist = new TextView(this);
            tvArtist.setText(song.getArtist());
            tvArtist.setTextColor(0x80FFFFFF);
            tvArtist.setTextSize(11);
            tvArtist.setSingleLine(true);
            tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemLayout.addView(tvArtist);

            itemLayout.setOnClickListener(v -> {
                playerManager.setPlaylist(new java.util.ArrayList<>(playlist), index);
                playerManager.playCurrent();
                dismissOverlay();
            });

            listLayout.addView(itemLayout);
        }

        contentLayout.addView(scrollView);
        overlayContainer.addView(contentLayout);

        // Scroll to current song
        if (currentIndex > 0) {
            scrollView.post(() -> {
                View child = listLayout.getChildAt(currentIndex);
                if (child != null) {
                    scrollView.smoothScrollTo(0, child.getTop());
                }
            });
        }

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
            TextView btn = new TextView(this);
            String label = String.format("%.1fx", speed);
            if (speed == 1.0f) label = "1.0x (正常)";
            btn.setText(label);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(10), 0, dp(10));
            btn.setBackgroundColor(Math.abs(currentSpeed - speed) < 0.01f ? 0xFFBB86FC : 0xFF2D2D2D);
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
        customLabel.setTextColor(0xB3FFFFFF);
        customLabel.setTextSize(13);
        customLabel.setGravity(Gravity.CENTER);
        customLabel.setPadding(0, dp(12), 0, dp(4));
        contentLayout.addView(customLabel);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText etSpeed = new EditText(this);
        etSpeed.setHint("倍速");
        etSpeed.setTextColor(0xFFFFFFFF);
        etSpeed.setHintTextColor(0xFF888888);
        etSpeed.setTextSize(14);
        etSpeed.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etSpeed.setBackgroundColor(0xFF2D2D2D);
        etSpeed.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        etParams.rightMargin = dp(4);
        etSpeed.setLayoutParams(etParams);
        customRow.addView(etSpeed);

        TextView btnApply = new TextView(this);
        btnApply.setText("应用");
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setTextSize(14);
        btnApply.setGravity(Gravity.CENTER);
        btnApply.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnApply.setBackgroundColor(0xFFBB86FC);
        btnApply.setClickable(true);
        btnApply.setFocusable(true);
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

        if (mp3Path == null) {
            // Download first, then show clip selection
            Toast.makeText(this, "正在下载歌曲...", Toast.LENGTH_SHORT).show();
            String cookie = playerManager.getCookie();
            DownloadManager.downloadSong(song, cookie, new DownloadManager.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    runOnUiThread(() -> showRingtoneClipOverlay(new File(filePath), song.getName()));
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "下载失败，无法设为铃声", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            showRingtoneClipOverlay(new File(mp3Path), song.getName());
        }
    }

    private MediaPlayer ringtonePreviewPlayer;

    private void showRingtoneClipOverlay(File file, String songTitle) {
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
        tvSong.setTextColor(0xB3FFFFFF);
        tvSong.setTextSize(12);
        tvSong.setGravity(Gravity.CENTER);
        tvSong.setPadding(0, 0, 0, dp(8));
        contentLayout.addView(tvSong);

        // Start seekbar
        final int[] startSec = {0};
        final int[] endSec = {Math.min(totalSec, 30)};

        TextView tvStart = new TextView(this);
        tvStart.setText("起始: " + startSec[0] + "s");
        tvStart.setTextColor(0xFFFFFFFF);
        tvStart.setTextSize(12);
        tvStart.setPadding(0, dp(4), 0, 0);
        contentLayout.addView(tvStart);

        SeekBar sbStart = new SeekBar(this);
        sbStart.setMax(totalSec);
        sbStart.setProgress(startSec[0]);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbStart.setLayoutParams(seekParams);
        contentLayout.addView(sbStart);

        // End seekbar
        TextView tvEnd = new TextView(this);
        tvEnd.setText("结束: " + endSec[0] + "s");
        tvEnd.setTextColor(0xFFFFFFFF);
        tvEnd.setTextSize(12);
        tvEnd.setPadding(0, dp(4), 0, 0);
        contentLayout.addView(tvEnd);

        SeekBar sbEnd = new SeekBar(this);
        sbEnd.setMax(totalSec);
        sbEnd.setProgress(endSec[0]);
        sbEnd.setLayoutParams(seekParams);
        contentLayout.addView(sbEnd);

        // Duration display
        TextView tvDuration = new TextView(this);
        tvDuration.setText("节选: " + startSec[0] + "s - " + endSec[0] + "s (" + (endSec[0] - startSec[0]) + "秒)");
        tvDuration.setTextColor(0xB3FFFFFF);
        tvDuration.setTextSize(12);
        tvDuration.setGravity(Gravity.CENTER);
        tvDuration.setPadding(0, dp(8), 0, dp(8));
        contentLayout.addView(tvDuration);

        Runnable updateDuration = () -> {
            int dur = endSec[0] - startSec[0];
            tvDuration.setText("节选: " + startSec[0] + "s - " + endSec[0] + "s (" + dur + "秒)");
        };

        sbStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress >= endSec[0]) progress = endSec[0] - 1;
                    if (progress < 0) progress = 0;
                    seekBar.setProgress(progress);
                    startSec[0] = progress;
                    tvStart.setText("起始: " + progress + "s");
                    updateDuration.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress <= startSec[0]) progress = startSec[0] + 1;
                    if (progress > totalSec) progress = totalSec;
                    seekBar.setProgress(progress);
                    endSec[0] = progress;
                    tvEnd.setText("结束: " + progress + "s");
                    updateDuration.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Button row: Preview + Confirm
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Preview button
        TextView btnPreview = new TextView(this);
        btnPreview.setText("试听");
        btnPreview.setTextColor(0xFFFFFFFF);
        btnPreview.setTextSize(13);
        btnPreview.setGravity(Gravity.CENTER);
        btnPreview.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnPreview.setBackgroundColor(0xFF3D3D3D);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        previewParams.rightMargin = dp(4);
        btnPreview.setLayoutParams(previewParams);
        btnPreview.setClickable(true);
        btnPreview.setFocusable(true);
        btnPreview.setOnClickListener(v -> {
            if (endSec[0] <= startSec[0]) {
                Toast.makeText(this, "请选择有效的时间范围", Toast.LENGTH_SHORT).show();
                return;
            }
            previewRingtoneClip(file, startSec[0] * 1000, endSec[0] * 1000);
        });
        btnRow.addView(btnPreview);

        // Confirm button
        TextView btnConfirm = new TextView(this);
        btnConfirm.setText("确认");
        btnConfirm.setTextColor(0xFFFFFFFF);
        btnConfirm.setTextSize(13);
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnConfirm.setBackgroundColor(0xFFBB86FC);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        confirmParams.leftMargin = dp(4);
        btnConfirm.setLayoutParams(confirmParams);
        btnConfirm.setClickable(true);
        btnConfirm.setFocusable(true);
        btnConfirm.setOnClickListener(v -> {
            if (endSec[0] <= startSec[0]) {
                Toast.makeText(this, "请选择有效的时间范围", Toast.LENGTH_SHORT).show();
                return;
            }
            stopRingtonePreview();
            String clipTitle = songTitle + " (" + startSec[0] + "s-" + endSec[0] + "s)";
            setRingtoneFromFile(file, clipTitle, startSec[0], endSec[0]);
            dismissOverlay();
        });
        btnRow.addView(btnConfirm);

        contentLayout.addView(btnRow);

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
            TextView btn = new TextView(this);
            btn.setText(min + " 分钟");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(10), 0, dp(10));
            btn.setBackgroundColor(0xFF2D2D2D);
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
        customLabel.setTextColor(0xB3FFFFFF);
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

        EditText etSeconds = new EditText(this);
        etSeconds.setHint("秒数");
        etSeconds.setTextColor(0xFFFFFFFF);
        etSeconds.setHintTextColor(0xFF888888);
        etSeconds.setTextSize(14);
        etSeconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSeconds.setBackgroundColor(0xFF2D2D2D);
        etSeconds.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        etParams.rightMargin = dp(4);
        etSeconds.setLayoutParams(etParams);
        customRow.addView(etSeconds);

        TextView btnCustom = new TextView(this);
        btnCustom.setText("开始");
        btnCustom.setTextColor(0xFFFFFFFF);
        btnCustom.setTextSize(14);
        btnCustom.setGravity(Gravity.CENTER);
        btnCustom.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnCustom.setBackgroundColor(0xFFBB86FC);
        btnCustom.setClickable(true);
        btnCustom.setFocusable(true);
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
        contentLayout.setPadding(dp(20), dp(20), dp(20), dp(20));
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
        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消定时");
        btnCancel.setTextColor(0xFFFFFFFF);
        btnCancel.setTextSize(14);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(0, dp(10), 0, dp(10));
        btnCancel.setBackgroundColor(0xFFBB86FC);
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
            tvSongName.setText(song.getName());
            tvArtist.setText(song.getArtist());
        } else {
            tvSongName.setText(R.string.no_song);
            tvArtist.setText("");
        }
        btnPlay.setImageResource(playerManager.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        // btnFuncMore image is static in layout
        // Update playlist indicator visibility
        updatePlaylistIndicator();
    }

    private void updatePlaylistIndicator() {
        if (btnPlaylistIndicator != null) {
            if (playerManager.hasSourcePlaylist()) {
                btnPlaylistIndicator.setVisibility(View.VISIBLE);
            } else {
                btnPlaylistIndicator.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onSongChanged(Song song) {
        tvSongName.setText(song.getName());
        tvArtist.setText(song.getArtist());
        startPlaybackService(song.getName(), song.getArtist(), true);
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
            currentTlyricText = null;
            currentHighlightIndex = -1;
            // Update song name label
            if (tvLyricsSongLabel != null) {
                tvLyricsSongLabel.setText(song.getName() + " - " + song.getArtist());
            }
            // Hide translation button until we know if new song has translation
            if (btnTranslationToggle != null) {
                btnTranslationToggle.setVisibility(View.GONE);
            }
            // Reload lyrics for new song
            loadLyricsForOverlay(song, tvLyricsTimeRef);
        }
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        if (isPlaying) {
            startSeekBarUpdate();
        } else {
            stopSeekBarUpdate();
        }
        // Always update notification with current play state
        Song song = playerManager.getCurrentSong();
        String name = song != null ? song.getName() : "";
        String artist = song != null ? song.getArtist() : "";
        startPlaybackService(name, artist, isPlaying);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final Runnable seekBarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isUserSeeking && playerManager.isPlaying()) {
                int current = playerManager.getCurrentPosition();
                int duration = playerManager.getDuration();
                if (duration > 0) {
                    seekBar.setMax(1000);
                    seekBar.setProgress((int) (1000L * current / duration));
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(duration));
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

    private void startPlaybackService(String songName, String artist, boolean isPlaying) {
        Intent serviceIntent = new Intent(this, MusicPlaybackService.class);
        serviceIntent.putExtra("song_name", songName);
        serviceIntent.putExtra("artist", artist);
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

    /**
     * Override onBackPressed to intercept the system back gesture on watches.
     * On 小天才 watches, right-swipe triggers onBackPressed. We only allow exit
     * when no overlay is showing (i.e., on the main player screen).
     * When an overlay (lyrics, functions, etc.) is visible, dismiss it instead.
     */
    @Override
    public void onBackPressed() {
        if (overlayContainer != null) {
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
    }
}
