package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.service.MusicPlaybackService;

import java.io.File;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerCallback {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        btnVolDown.setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI));

        btnVolUp.setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI));

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
        startPlaybackService("163音乐", "等待播放");

        // Request storage permission for saving favorites to /sdcard/163Music/
        requestStoragePermission();
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

        // Close overlay on background click
        overlayContainer.setOnClickListener(v -> dismissOverlay());

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
        contentLayout.setPadding(dp(16), dp(20), dp(16), dp(20));

        // Title
        TextView title = new TextView(this);
        title.setText("更多功能");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        contentLayout.addView(title);

        // Function grid - 2 per row
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Row 1: 收藏 + 下载
        boolean isFav = favoritesManager.isFavorite(song.getId());
        row1.addView(createFuncItem(isFav ? "♥" : "♡", isFav ? "取消收藏" : "收藏",
                v -> onFuncFavorite(song)));
        row1.addView(createFuncItem("⬇", "下载",
                v -> onFuncDownload(song)));
        contentLayout.addView(row1);

        // Row 2: 设为铃声 + 定时关闭
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, dp(8), 0, 0);
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

        scrollView.addView(contentLayout);
        overlayContainer.addView(scrollView);
        rootView.addView(overlayContainer);
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
        if (overlayTimerHandler != null) {
            overlayTimerHandler.removeCallbacksAndMessages(null);
            overlayTimerHandler = null;
            overlayTimerRunnable = null;
        }
        if (overlayContainer != null) {
            FrameLayout rootView = (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
            rootView.removeView(overlayContainer);
            overlayContainer = null;
        }
    }

    private void onFuncFavorite(Song song) {
        if (favoritesManager.isFavorite(song.getId())) {
            favoritesManager.removeFavorite(song);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favoritesManager.addFavorite(song);
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
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

    private void onFuncSetRingtone(Song song) {
        dismissOverlay();
        // Check if song is downloaded first
        String mp3Path = DownloadManager.getDownloadedMp3Path(song);

        if (mp3Path == null) {
            // Download first, then set as ringtone
            Toast.makeText(this, "正在下载歌曲...", Toast.LENGTH_SHORT).show();
            String cookie = playerManager.getCookie();
            DownloadManager.downloadSong(song, cookie, new DownloadManager.DownloadCallback() {
                @Override
                public void onSuccess(String filePath) {
                    setRingtoneFromFile(new File(filePath), song.getName());
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "下载失败，无法设为铃声", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            setRingtoneFromFile(new File(mp3Path), song.getName());
        }
    }

    private void setRingtoneFromFile(File file, String title) {
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

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
            values.put(MediaStore.MediaColumns.TITLE, title);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_MUSIC, false);

            Uri uri = MediaStore.Audio.Media.getContentUriForPath(file.getAbsolutePath());

            // Delete existing entry if any
            getContentResolver().delete(uri,
                    MediaStore.MediaColumns.DATA + "=?",
                    new String[]{file.getAbsolutePath()});

            Uri newUri = getContentResolver().insert(uri, values);
            if (newUri != null) {
                RingtoneManager.setActualDefaultRingtoneUri(this,
                        RingtoneManager.TYPE_RINGTONE, newUri);
                Toast.makeText(this, "已设为铃声", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "设置铃声失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "设置铃声失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
        overlayContainer.setOnClickListener(v -> dismissOverlay());

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

        // Title
        TextView title = new TextView(this);
        title.setText("定时关闭");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        contentLayout.addView(title);

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
        overlayContainer.setOnClickListener(v -> dismissOverlay());

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setPadding(dp(20), dp(20), dp(20), dp(20));
        contentLayout.setOnClickListener(v -> { /* consume click */ });

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        contentLayout.setLayoutParams(contentParams);

        // Title
        TextView title = new TextView(this);
        title.setText("定时关闭");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        contentLayout.addView(title);

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
        startPlaybackService(song.getName(), song.getArtist());
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        btnPlay.setText(isPlaying ? "\u23F8" : "\u25B6");
        if (isPlaying) {
            startSeekBarUpdate();
            if (!serviceStarted) {
                Song song = playerManager.getCurrentSong();
                String name = song != null ? song.getName() : "";
                String artist = song != null ? song.getArtist() : "";
                startPlaybackService(name, artist);
            }
        } else {
            stopSeekBarUpdate();
        }
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

    private void startPlaybackService(String songName, String artist) {
        Intent serviceIntent = new Intent(this, MusicPlaybackService.class);
        serviceIntent.putExtra("song_name", songName);
        serviceIntent.putExtra("artist", artist);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
    }
}
