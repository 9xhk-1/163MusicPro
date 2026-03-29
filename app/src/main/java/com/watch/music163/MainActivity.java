package com.watch.music163;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlayerCallback {

    private static final String HEART_OUTLINE = "\u2661";
    private static final String HEART_FILLED = "\u2665";

    private TextView tvSongName;
    private TextView tvArtist;
    private TextView btnPlay;
    private TextView btnFavorite;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private MusicPlayerManager playerManager;
    private FavoritesManager favoritesManager;
    private AudioManager audioManager;
    private final Handler seekHandler = new Handler();
    private boolean isUserSeeking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSongName = findViewById(R.id.tv_song_name);
        tvArtist = findViewById(R.id.tv_artist);
        btnPlay = findViewById(R.id.btn_play);
        btnFavorite = findViewById(R.id.btn_favorite);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        TextView btnPrev = findViewById(R.id.btn_prev);
        TextView btnNext = findViewById(R.id.btn_next);
        TextView btnVolDown = findViewById(R.id.btn_vol_down);
        TextView btnVolUp = findViewById(R.id.btn_vol_up);
        TextView btnMore = findViewById(R.id.btn_more);

        playerManager = MusicPlayerManager.getInstance();
        favoritesManager = new FavoritesManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Load saved cookie
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("cookie", "");
        playerManager.setCookie(cookie);

        // Enable marquee
        tvSongName.setSelected(true);

        btnPlay.setOnClickListener(v -> {
            if (playerManager.isPlaying()) {
                playerManager.pause();
            } else if (playerManager.getCurrentSong() != null) {
                playerManager.resume();
            }
        });

        btnPrev.setOnClickListener(v -> playerManager.previous());
        btnNext.setOnClickListener(v -> playerManager.next());

        btnVolDown.setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI));

        btnVolUp.setOnClickListener(v ->
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI));

        btnFavorite.setOnClickListener(v -> toggleFavorite());

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
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerManager.setCallback(this);
        // Reload cookie in case it changed
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        playerManager.setCookie(prefs.getString("cookie", ""));
        updateUI();
        if (playerManager.isPlaying()) {
            startSeekBarUpdate();
        }
    }

    private void toggleFavorite() {
        Song song = playerManager.getCurrentSong();
        if (song == null) return;
        if (favoritesManager.isFavorite(song.getId())) {
            favoritesManager.removeFavorite(song);
            btnFavorite.setText(HEART_OUTLINE); // ♡
        } else {
            favoritesManager.addFavorite(song);
            btnFavorite.setText(HEART_FILLED); // ♥
        }
    }

    private void updateUI() {
        Song song = playerManager.getCurrentSong();
        if (song != null) {
            tvSongName.setText(song.getName());
            tvArtist.setText(song.getArtist());
            btnFavorite.setText(
                    favoritesManager.isFavorite(song.getId()) ? HEART_FILLED : HEART_OUTLINE);
        } else {
            tvSongName.setText(R.string.no_song);
            tvArtist.setText("");
            btnFavorite.setText(HEART_OUTLINE);
        }
        btnPlay.setText(playerManager.isPlaying() ? "\u23F8" : "\u25B6");
    }

    @Override
    public void onSongChanged(Song song) {
        tvSongName.setText(song.getName());
        tvArtist.setText(song.getArtist());
        btnFavorite.setText(
                favoritesManager.isFavorite(song.getId()) ? HEART_FILLED : HEART_OUTLINE);
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        btnPlay.setText(isPlaying ? "\u23F8" : "\u25B6");
        if (isPlaying) {
            startSeekBarUpdate();
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

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
    }
}
