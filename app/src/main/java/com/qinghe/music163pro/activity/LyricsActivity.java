package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Displays scrolling lyrics synchronized with music playback.
 * Reads lyrics from local .lrc file if available, otherwise fetches from API.
 */
public class LyricsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.?(\\d{0,3})\\](.*)");

    private ScrollView svLyrics;
    private LinearLayout llContainer;
    private TextView tvSongName;
    private TextView tvTime;
    private MusicPlayerManager playerManager;

    private final List<LyricLine> lyricLines = new ArrayList<>();
    private final List<TextView> lyricViews = new ArrayList<>();
    private int currentHighlightIndex = -1;

    private final Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;

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
        setContentView(R.layout.activity_lyrics);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        svLyrics = findViewById(R.id.sv_lyrics);
        llContainer = findViewById(R.id.ll_lyrics_container);
        tvSongName = findViewById(R.id.tv_lyrics_song_name);
        tvTime = findViewById(R.id.tv_lyrics_time);

        tvSongName.setSelected(true); // enable marquee

        playerManager = MusicPlayerManager.getInstance();

        Song song = playerManager.getCurrentSong();
        if (song == null) {
            Toast.makeText(this, "暂无歌曲", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvSongName.setText(song.getName() + " - " + song.getArtist());

        loadLyrics(song);
    }

    private void loadLyrics(Song song) {
        // Try to load from local .lrc file first
        String localLrc = loadLocalLrc(song);
        if (localLrc != null && !localLrc.isEmpty()) {
            parseLrc(localLrc);
            displayLyrics();
            startScrollSync();
            return;
        }

        // Fetch from API
        if (song.getId() <= 0) {
            showNoLyrics();
            return;
        }

        String cookie = playerManager.getCookie();
        MusicApiHelper.getLyrics(song.getId(), cookie, new MusicApiHelper.LyricsCallback() {
            @Override
            public void onResult(String lrcText) {
                if (lrcText == null || lrcText.isEmpty()) {
                    showNoLyrics();
                    return;
                }
                parseLrc(lrcText);
                displayLyrics();
                startScrollSync();
            }

            @Override
            public void onError(String message) {
                showNoLyrics();
            }
        });
    }

    /**
     * Try to load lyrics from a local .lrc file in the download directory.
     */
    private String loadLocalLrc(Song song) {
        try {
            String safeName = song.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String safeArtist = song.getArtist().replaceAll("[\\\\/:*?\"<>|]", "_");
            String folderName = safeName + " - " + safeArtist;
            File lrcFile = new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "163Music/Download/" + folderName + "/lyrics.lrc"
            );
            if (!lrcFile.exists()) return null;

            FileInputStream fis = new FileInputStream(lrcFile);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();
            fis.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void parseLrc(String lrcText) {
        lyricLines.clear();
        String[] lines = lrcText.split("\n");
        for (String line : lines) {
            Matcher matcher = LRC_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                String msStr = matcher.group(3);
                int ms = 0;
                if (msStr != null && !msStr.isEmpty()) {
                    // Normalize to milliseconds
                    if (msStr.length() == 1) ms = Integer.parseInt(msStr) * 100;
                    else if (msStr.length() == 2) ms = Integer.parseInt(msStr) * 10;
                    else ms = Integer.parseInt(msStr);
                }
                long timeMs = (long) min * 60 * 1000 + (long) sec * 1000 + ms;
                String text = matcher.group(4).trim();
                if (!text.isEmpty()) {
                    lyricLines.add(new LyricLine(timeMs, text));
                }
            }
        }
    }

    private void displayLyrics() {
        llContainer.removeAllViews();
        lyricViews.clear();

        if (lyricLines.isEmpty()) {
            showNoLyrics();
            return;
        }

        for (LyricLine line : lyricLines) {
            TextView tv = new TextView(this);
            tv.setText(line.text);
            tv.setTextColor(0xFF888888);
            tv.setTextSize(13);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, dp(6), 0, dp(6));
            llContainer.addView(tv);
            lyricViews.add(tv);
        }
    }

    private void showNoLyrics() {
        llContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("暂无歌词");
        tv.setTextColor(0xFF888888);
        tv.setTextSize(14);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, dp(40), 0, 0);
        llContainer.addView(tv);
    }

    private void startScrollSync() {
        if (lyricLines.isEmpty()) return;

        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (playerManager.isPlaying() || playerManager.getCurrentPosition() > 0) {
                    int currentPos = playerManager.getCurrentPosition();
                    int duration = playerManager.getDuration();

                    // Update time display
                    tvTime.setText(formatTime(currentPos) + " / " + formatTime(duration));

                    // Find current lyric line
                    int newIndex = findCurrentLyricIndex(currentPos);
                    if (newIndex != currentHighlightIndex && newIndex >= 0) {
                        // Unhighlight previous
                        if (currentHighlightIndex >= 0 && currentHighlightIndex < lyricViews.size()) {
                            lyricViews.get(currentHighlightIndex).setTextColor(0xFF888888);
                            lyricViews.get(currentHighlightIndex).setTextSize(13);
                        }
                        // Highlight current
                        currentHighlightIndex = newIndex;
                        if (currentHighlightIndex < lyricViews.size()) {
                            TextView currentView = lyricViews.get(currentHighlightIndex);
                            currentView.setTextColor(0xFFFFFFFF);
                            currentView.setTextSize(14);

                            // Scroll to center the current line
                            scrollToLine(currentHighlightIndex);
                        }
                    }
                }
                scrollHandler.postDelayed(this, 300);
            }
        };
        scrollHandler.post(scrollRunnable);
    }

    private int findCurrentLyricIndex(int positionMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).timeMs <= positionMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private void scrollToLine(int index) {
        if (index < 0 || index >= lyricViews.size()) return;
        final TextView target = lyricViews.get(index);
        target.post(() -> {
            int scrollViewHeight = svLyrics.getHeight();
            int targetTop = target.getTop();
            int targetHeight = target.getHeight();
            int scrollTo = targetTop - (scrollViewHeight / 2) + (targetHeight / 2);
            svLyrics.smoothScrollTo(0, Math.max(0, scrollTo));
        });
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
    protected void onResume() {
        super.onResume();
        if (scrollRunnable != null) {
            scrollHandler.post(scrollRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        scrollHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scrollHandler.removeCallbacksAndMessages(null);
    }
}
