package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;
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
 *
 * Supports two scroll modes (configurable in settings):
 *  - 每行 (LYRIC_MODE_FOLLOW=0): always auto-scrolls to current line
 *  - 阻塞 (LYRIC_MODE_BLOCK=1): pauses auto-scroll when user scrolls;
 *    resumes after configured interval seconds; double-tap to seek to that line
 */
public class LyricsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final Pattern LRC_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})\\.?(\\d{0,3})\\](.*)");

    private static final int LYRIC_MODE_FOLLOW = 0;
    private static final int LYRIC_MODE_BLOCK  = 1;

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

    // Blocking mode fields
    private int lyricScrollMode = LYRIC_MODE_FOLLOW;
    private int lyricResumeIntervalMs = 3000;
    private volatile boolean userScrolled = false;
    private volatile long lastUserScrollTime = 0L;

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

        // Read lyric scroll settings
        lyricScrollMode = prefs.getInt("lyric_scroll_mode", LYRIC_MODE_FOLLOW);
        int intervalSec = prefs.getInt("lyric_resume_interval", 3);
        if (intervalSec < 1) intervalSec = 1;
        lyricResumeIntervalMs = intervalSec * 1000;

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

        // In blocking mode, attach a single touch listener on the ScrollView to
        // detect user scrolls (ACTION_MOVE) and double-taps for seeking.
        if (lyricScrollMode == LYRIC_MODE_BLOCK) {
            setupBlockingModeDetection();
        }

        loadLyrics(song);
    }

    /**
     * Attach a touch listener directly on svLyrics (the ScrollView).
     *
     * Why on the ScrollView instead of on individual TextViews:
     *   ScrollView.onInterceptTouchEvent() steals ACTION_MOVE from children, so a
     *   GestureDetector on a child TextView never receives a complete gesture stream
     *   for double-tap detection. The ScrollView's own touch listener fires BEFORE
     *   children and before interception, giving us a complete event stream.
     *
     * ACTION_MOVE → user is deliberately scrolling → set userScrolled flag
     * onDoubleTap → calculate which lyric row was tapped → seek player to that time
     */
    private void setupBlockingModeDetection() {
        GestureDetector blockGd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDown(MotionEvent e) {
                        // Must return true so the detector keeps tracking this gesture.
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // Convert event Y (relative to ScrollView viewport) to content Y
                        int tapYInContent = (int) e.getY() + svLyrics.getScrollY();
                        int idx = findLyricViewAtY(tapYInContent);
                        if (idx >= 0) {
                            long seekMs = lyricLines.get(idx).timeMs;
                            playerManager.seekTo((int) seekMs);
                            // Release the user-scroll block immediately
                            userScrolled = false;
                            // Force re-highlight on next tick
                            currentHighlightIndex = -1;
                            Toast.makeText(LyricsActivity.this,
                                    "跳转到: " + formatTime((int) seekMs),
                                    Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        return false;
                    }
                });

        svLyrics.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            // Any finger movement = user is scrolling → block auto-follow
            if (action == MotionEvent.ACTION_MOVE) {
                userScrolled = true;
                lastUserScrollTime = System.currentTimeMillis();
            }
            // Feed every event to the gesture detector so double-tap works
            blockGd.onTouchEvent(event);
            // Return false so the ScrollView still handles normal scrolling
            return false;
        });
    }

    /**
     * Find the index of the lyric TextView that contains the given y-coordinate
     * in content space (scrolled coordinates, relative to llContainer).
     */
    private int findLyricViewAtY(int yInContent) {
        int containerTop = llContainer.getTop();
        for (int i = 0; i < lyricViews.size(); i++) {
            TextView tv = lyricViews.get(i);
            int tvTop  = containerTop + tv.getTop();
            int tvBottom = containerTop + tv.getBottom();
            if (yInContent >= tvTop && yInContent <= tvBottom) {
                return i;
            }
        }
        return -1;
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

        // Handle Bilibili songs - fetch subtitle from Bilibili API
        if (song.isBilibili()) {
            loadBilibiliSubtitle(song);
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
     * Load subtitle for a Bilibili video.
     */
    private void loadBilibiliSubtitle(Song song) {
        String bilibiliCookie = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("bilibili_cookie", "");

        BilibiliApiHelper.getSubtitleList(song.getBvid(), song.getCid(), bilibiliCookie,
                new BilibiliApiHelper.SubtitleListCallback() {
                    @Override
                    public void onResult(List<BilibiliApiHelper.SubtitleInfo> subtitles) {
                        if (subtitles.isEmpty()) {
                            showNoLyrics();
                            return;
                        }

                        BilibiliApiHelper.SubtitleInfo chosen = subtitles.get(0);
                        for (BilibiliApiHelper.SubtitleInfo info : subtitles) {
                            if (info.lan.startsWith("zh")) {
                                chosen = info;
                                break;
                            }
                        }

                        BilibiliApiHelper.getSubtitle(chosen.subtitleUrl,
                                new BilibiliApiHelper.SubtitleCallback() {
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

                    @Override
                    public void onError(String message) {
                        showNoLyrics();
                    }
                });
    }

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

            try (FileInputStream fis = new FileInputStream(lrcFile);
                 InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
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
            tv.setTextColor(0xB3FFFFFF);
            tv.setTextSize(13);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, dp(6), 0, dp(6));
            // No per-TextView touch listeners: double-tap and scroll detection
            // are handled at the ScrollView level in setupBlockingModeDetection().
            llContainer.addView(tv);
            lyricViews.add(tv);
        }
    }

    private void showNoLyrics() {
        llContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("暂无歌词");
        tv.setTextColor(0xB3FFFFFF);
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

                    tvTime.setText(formatTime(currentPos) + " / " + formatTime(duration));

                    // In blocking mode, auto-unblock after the configured interval
                    if (lyricScrollMode == LYRIC_MODE_BLOCK && userScrolled) {
                        if (System.currentTimeMillis() - lastUserScrollTime >= lyricResumeIntervalMs) {
                            userScrolled = false;
                        }
                    }

                    int newIndex = findCurrentLyricIndex(currentPos);
                    if (newIndex != currentHighlightIndex && newIndex >= 0) {
                        // Unhighlight previous
                        if (currentHighlightIndex >= 0 && currentHighlightIndex < lyricViews.size()) {
                            lyricViews.get(currentHighlightIndex).setTextColor(0xB3FFFFFF);
                            lyricViews.get(currentHighlightIndex).setTextSize(13);
                        }
                        // Highlight current
                        currentHighlightIndex = newIndex;
                        if (currentHighlightIndex < lyricViews.size()) {
                            TextView currentView = lyricViews.get(currentHighlightIndex);
                            currentView.setTextColor(0xFFFFFFFF);
                            currentView.setTextSize(14);

                            // Auto-scroll: always in follow mode; only when not blocked in block mode
                            if (lyricScrollMode == LYRIC_MODE_FOLLOW || !userScrolled) {
                                scrollToLine(currentHighlightIndex);
                            }
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
