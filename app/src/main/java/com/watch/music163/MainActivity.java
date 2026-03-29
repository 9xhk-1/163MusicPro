package com.watch.music163;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
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
    private MusicPlayerManager playerManager;
    private FavoritesManager favoritesManager;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSongName = findViewById(R.id.tv_song_name);
        tvArtist = findViewById(R.id.tv_artist);
        btnPlay = findViewById(R.id.btn_play);
        btnFavorite = findViewById(R.id.btn_favorite);
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
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
