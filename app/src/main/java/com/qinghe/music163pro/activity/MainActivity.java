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
import android.widget.LinearLayout;
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

import java.io.File;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerCallback {

    private static final String TAG = "MainActivity";
    private static final String HEART_OUTLINE = "\u2661";
    private static final String HEART_FILLED = "\u2665";
    private static final int STORAGE_PERMISSION_REQUEST = 100;

    private TextView tvSongName;
    private TextView tvArtist;
    private TextView btnPlay;
    private TextView btnFuncMore;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private MusicPlayerManager playerManager;
    private FavoritesManager favoritesManager;
    private AudioManager audioManager;
    private final Handler seekHandler = new Handler();
    private boolean isUserSeeking = false;
    private boolean serviceStarted = false;

    // Functions overlay
    private FrameLayout overlayContainer;
    private Handler overlayTimerHandler;
    private Runnable overlayTimerRunnable;

    // Volume indicator
    private TextView volumeIndicator;
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

    private static class LyricLine {
        long timeMs;
        String text;
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
        TextView btnPrev = findViewById(R.id.btn_prev);
        TextView btnNext = findViewById(R.id.btn_next);
        TextView btnVolDown = findViewById(R.id.btn_vol_down);
        TextView btnVolUp = findViewById(R.id.btn_vol_up);
        TextView btnMore = findViewById(R.id.btn_more);

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

        // Load saved speed mode (pitch unchanged vs pitch changed)
        playerManager.setPitchWithSpeed(prefs.getBoolean("pitch_with_speed", false));

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
        playerManager.setPitchWithSpeed(prefs.getBoolean("pitch_with_speed", false));
        // Preload cloud liked IDs cache so overlay shows correct favorite state
        if (prefs.getBoolean("fav_mode_cloud", false)) {
            refreshCloudLikedIds();
        }
        updateUI();
        if (playerManager.isPlaying()) {
            startSeekBarUpdate();
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
        overlayContainer.setBackgroundColor(0xCC333333); // Gray mask

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
        row1.addView(createFuncItem(isFav ? "♥" : "♡", isFav ? "取消收藏" : "收藏",
                v -> onFuncFavorite(song)));
        row1.addView(createFuncItem("⬇", "下载",
                v -> onFuncDownload(song)));
        contentLayout.addView(row1);

        // Row 2: 设为铃声 + 定时关闭
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(4), 0, 0);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row2.addView(createFuncItem("🔔", "设为铃声",
                v -> onFuncSetRingtone(song)));

        // Sleep timer - show remaining time if active, with live updates
        LinearLayout timerItem = createFuncItem("⏱",
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

        String playModeIcon;
        String playModeLabel;
        switch (playerManager.getPlayMode()) {
            case SINGLE_REPEAT:
                playModeIcon = "🔂";
                playModeLabel = "单曲循环";
                break;
            case RANDOM:
                playModeIcon = "🔀";
                playModeLabel = "随机播放";
                break;
            case LIST_LOOP:
            default:
                playModeIcon = "🔁";
                playModeLabel = "列表循环";
                break;
        }
        row3.addView(createFuncItem(playModeIcon, playModeLabel,
                v -> onFuncCyclePlayMode()));
        float currentSpeed = playerManager.getPlaybackSpeed();
        String speedLabel = currentSpeed == 1.0f ? "倍速播放" : String.format("%.1fx", currentSpeed);
        row3.addView(createFuncItem("⚡", speedLabel,
                v -> onFuncPlaybackSpeed()));
        contentLayout.addView(row3);

        // Row 4: 播放列表
        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setGravity(Gravity.CENTER);
        row4.setPadding(0, dp(4), 0, 0);
        row4.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row4.addView(createFuncItem("📋", "播放列表",
                v -> onFuncShowPlaylist()));
        // Empty placeholder for alignment
        LinearLayout placeholder = new LinearLayout(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row4.addView(placeholder);
        contentLayout.addView(row4);

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
        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setTextSize(15);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
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

    private LinearLayout createFuncItem(String icon, String label, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        item.setLayoutParams(itemParams);
        item.setClickable(true);
        item.setFocusable(true);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
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

        // Build volume bar string
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < max; i++) {
            bar.append(i < current ? "█" : "░");
        }

        volumeIndicator = new TextView(this);
        volumeIndicator.setText("🔊  " + current + "/" + max + "\n" + bar.toString());
        volumeIndicator.setTextColor(0xFFFFFFFF);
        volumeIndicator.setTextSize(13);
        volumeIndicator.setGravity(Gravity.CENTER);
        volumeIndicator.setBackgroundColor(0xCC333333);
        volumeIndicator.setPadding(dp(16), dp(8), dp(16), dp(8));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(8);
        volumeIndicator.setLayoutParams(params);

        rootView.addView(volumeIndicator);

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
                modeName = "单曲循环 🔂";
                break;
            case RANDOM:
                modeName = "随机播放 🔀";
                break;
            case LIST_LOOP:
            default:
                modeName = "列表循环 🔁";
                break;
        }
        Toast.makeText(this, "播放模式: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void onFuncLyrics() {
        dismissOverlay();
        showLyricsOverlay();
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
        overlayContainer.setBackgroundColor(0xFF212121);
        // Don't use addSwipeToDismiss - dispatchTouchEvent handles it

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Song name at top
        tvLyricsSongLabel = new TextView(this);
        tvLyricsSongLabel.setText(song.getName() + " - " + song.getArtist());
        tvLyricsSongLabel.setTextColor(0xFFFFFFFF);
        tvLyricsSongLabel.setTextSize(13);
        tvLyricsSongLabel.setGravity(Gravity.CENTER);
        tvLyricsSongLabel.setPadding(dp(6), dp(6), dp(6), dp(4));
        tvLyricsSongLabel.setSingleLine(true);
        tvLyricsSongLabel.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tvLyricsSongLabel.setSelected(true);
        mainLayout.addView(tvLyricsSongLabel);

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
        tvLyricsTimeRef.setTextColor(0xFF757575);
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
            parseLrc(localLrc);
            displayLyricsInOverlay();
            startLyricsScrollSync(tvLyricsTime);
            return;
        }

        // Fetch from API
        if (song.getId() <= 0) {
            showNoLyricsInOverlay();
            return;
        }

        // Show loading
        lyricsContainer.removeAllViews();
        TextView tvLoading = new TextView(this);
        tvLoading.setText("加载歌词中...");
        tvLoading.setTextColor(0xFF888888);
        tvLoading.setTextSize(13);
        tvLoading.setGravity(Gravity.CENTER);
        lyricsContainer.addView(tvLoading);

        String cookie = playerManager.getCookie();
        MusicApiHelper.getLyrics(song.getId(), cookie, new MusicApiHelper.LyricsCallback() {
            @Override
            public void onResult(String lrcText) {
                if (lrcText == null || lrcText.isEmpty()) {
                    showNoLyricsInOverlay();
                    return;
                }
                parseLrc(lrcText);
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

    private static final java.util.regex.Pattern LRC_PATTERN =
            java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.?(\\d{0,3})\\](.*)");

    private void parseLrc(String lrcText) {
        lyricLines.clear();
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
            TextView tv = new TextView(this);
            tv.setText(line.text);
            tv.setTextColor(0xFF888888);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(6), 0, dp(6));
            lyricsContainer.addView(tv);
            lyricViews.add(tv);
        }
    }

    private void showNoLyricsInOverlay() {
        if (lyricsContainer == null) return;
        lyricsContainer.removeAllViews();
        lyricViews.clear();
        TextView tv = new TextView(this);
        tv.setText("暂无歌词");
        tv.setTextColor(0xFF888888);
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
                            lyricViews.get(currentHighlightIndex).setTextColor(0xFF888888);
                            lyricViews.get(currentHighlightIndex).setTextSize(13);
                        }
                        currentHighlightIndex = newIndex;
                        if (currentHighlightIndex < lyricViews.size()) {
                            TextView currentView = lyricViews.get(currentHighlightIndex);
                            currentView.setTextColor(0xFFFFFFFF);
                            currentView.setTextSize(14);

                            // Scroll to center the current line
                            currentView.post(() -> {
                                if (lyricsScrollView == null) return;
                                int scrollViewHeight = lyricsScrollView.getHeight();
                                int targetTop = currentView.getTop();
                                int targetHeight = currentView.getHeight();
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
        overlayContainer.setBackgroundColor(0xCC333333);
        addSwipeToDismiss(overlayContainer);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
        contentLayout.setBackgroundColor(0xFF212121);
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
                itemLayout.setBackgroundColor(0xFF333333);
            }

            TextView tvName = new TextView(this);
            String prefix = (i == currentIndex) ? "▶ " : (i + 1) + ". ";
            tvName.setText(prefix + song.getName());
            tvName.setTextColor(i == currentIndex ? 0xFFD32F2F : 0xFFFFFFFF);
            tvName.setTextSize(13);
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemLayout.addView(tvName);

            TextView tvArtist = new TextView(this);
            tvArtist.setText(song.getArtist());
            tvArtist.setTextColor(0xFF757575);
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
        overlayContainer.setBackgroundColor(0xCC333333);
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
            btn.setBackgroundColor(Math.abs(currentSpeed - speed) < 0.01f ? 0xFFD32F2F : 0xFF424242);
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
        customLabel.setTextColor(0xFFCCCCCC);
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
        etSpeed.setBackgroundColor(0xFF424242);
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
        btnApply.setBackgroundColor(0xFFD32F2F);
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
        overlayContainer.setBackgroundColor(0xCC333333);
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
        tvSong.setTextColor(0xFFCCCCCC);
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
        tvDuration.setTextColor(0xFFCCCCCC);
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
        btnPreview.setText("▶ 试听");
        btnPreview.setTextColor(0xFFFFFFFF);
        btnPreview.setTextSize(13);
        btnPreview.setGravity(Gravity.CENTER);
        btnPreview.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnPreview.setBackgroundColor(0xFF616161);
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
        btnConfirm.setText("✓ 确认");
        btnConfirm.setTextColor(0xFFFFFFFF);
        btnConfirm.setTextSize(13);
        btnConfirm.setGravity(Gravity.CENTER);
        btnConfirm.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnConfirm.setBackgroundColor(0xFFD32F2F);
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
        overlayContainer.setBackgroundColor(0xCC333333);
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
            btn.setBackgroundColor(0xFF424242);
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
        customLabel.setTextColor(0xFFCCCCCC);
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
        etSeconds.setBackgroundColor(0xFF424242);
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
        btnCustom.setBackgroundColor(0xFFD32F2F);
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
        overlayContainer.setBackgroundColor(0xCC333333);
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
        btnCancel.setBackgroundColor(0xFFD32F2F);
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
        btnPlay.setText(playerManager.isPlaying() ? "\u23F8" : "\u25B6");
        // btnFuncMore always shows "more" icon
        btnFuncMore.setText("⋯");
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
            currentHighlightIndex = -1;
            // Update song name label
            if (tvLyricsSongLabel != null) {
                tvLyricsSongLabel.setText(song.getName() + " - " + song.getArtist());
            }
            // Reload lyrics for new song
            loadLyricsForOverlay(song, tvLyricsTimeRef);
        }
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        btnPlay.setText(isPlaying ? "\u23F8" : "\u25B6");
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
