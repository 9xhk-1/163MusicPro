package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Song recognition activity.
 * Provides two modes:
 * - 听歌识曲: listen to music playing and identify the song
 * - 哼歌识曲: hum a melody and identify the song
 *
 * Records 10 seconds of 16kHz mono PCM, then sends to NetEase API.
 * Designed for watch screen 320×360 dpi.
 */
public class SongRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "SongRecognitionActivity";
    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 10;

    private TextView tvStatus;
    private TextView btnListen;
    private TextView btnHum;
    private TextView btnStop;
    private TextView tvResult;

    private volatile boolean isRecording = false;
    private AudioRecord audioRecord;
    private final Handler handler = new Handler();
    private Runnable countdownRunnable;

    /** null = no mode selected yet; true = listen; false = hum */
    private Boolean pendingHumMode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        buildUi();
        MusicLog.op(TAG, "打开识曲界面", null);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF212121);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(12));

        // Title
        TextView title = new TextView(this);
        title.setText("🎵  识别歌曲");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        root.addView(title);

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setText("点击下方按钮开始识别");
        tvStatus.setTextColor(0xFFAAAAAA);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, px(10));
        root.addView(tvStatus);

        // 听歌识曲 button
        btnListen = makeTileButton("🎧  听歌识曲\n播放音乐中识别歌曲");
        btnListen.setOnClickListener(v -> startRecognition(false));
        root.addView(btnListen);

        addSpacer(root, px(4));

        // 哼歌识曲 button
        btnHum = makeTileButton("🎤  哼歌识曲\n哼唱旋律识别歌曲");
        btnHum.setOnClickListener(v -> startRecognition(true));
        root.addView(btnHum);

        addSpacer(root, px(8));

        // Stop recording button — visible only during recording
        btnStop = makeTileButton("⏹  停止录音");
        btnStop.setBackgroundColor(0xFFC62828);
        btnStop.setVisibility(android.view.View.GONE);
        btnStop.setOnClickListener(v -> stopRecording());
        root.addView(btnStop);

        addSpacer(root, px(8));
        tvResult = new TextView(this);
        tvResult.setText("");
        tvResult.setTextColor(0xFFFFFFFF);
        tvResult.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvResult.setPadding(px(6), px(6), px(6), px(6));
        tvResult.setBackgroundColor(0xFF333333);
        tvResult.setVisibility(android.view.View.GONE);
        root.addView(tvResult);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void startRecognition(boolean humMode) {
        if (isRecording) {
            Toast.makeText(this, "正在录音中...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingHumMode = humMode;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        doRecord(humMode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingHumMode != null) {
                    doRecord(pendingHumMode);
                    pendingHumMode = null;
                }
            } else {
                Toast.makeText(this, "需要录音权限才能识别歌曲", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void doRecord(boolean humMode) {
        String modeLabel = humMode ? "哼歌识曲" : "听歌识曲";
        MusicLog.op(TAG, "开始录音", modeLabel);
        setButtonsEnabled(false);
        btnStop.setVisibility(android.view.View.VISIBLE);

        final int[] remaining = {RECORD_SECONDS};
        tvResult.setVisibility(android.view.View.GONE);
        tvStatus.setText(modeLabel + "：录音中 " + RECORD_SECONDS + "s...");

        // Countdown UI updater
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                remaining[0]--;
                if (remaining[0] > 0) {
                    tvStatus.setText(modeLabel + "：录音中 " + remaining[0] + "s...");
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(countdownRunnable, 1000);

        new Thread(() -> {
            byte[] pcm = recordPcm(RECORD_SECONDS);
            handler.removeCallbacks(countdownRunnable);

            if (pcm == null || pcm.length == 0) {
                runOnUiThread(() -> {
                    tvStatus.setText("录音失败，请检查麦克风权限");
                    btnStop.setVisibility(android.view.View.GONE);
                    setButtonsEnabled(true);
                });
                return;
            }

            runOnUiThread(() -> {
                tvStatus.setText("正在识别...");
                btnStop.setVisibility(android.view.View.GONE);
            });
            MusicLog.d(TAG, "录音完成，开始识别 " + modeLabel + " bytes=" + pcm.length);

            String cookie = MusicPlayerManager.getInstance().getCookie();
            MusicApiHelper.RecognitionCallback cb = new MusicApiHelper.RecognitionCallback() {
                @Override
                public void onResult(String songName, String artist, String album, long songId) {
                    String info = songName + "\n" + artist + (album.isEmpty() ? "" : "\n" + album);
                    tvStatus.setText(modeLabel + " 识别成功！");
                    tvResult.setText("🎵 " + info);
                    tvResult.setVisibility(android.view.View.VISIBLE);
                    setButtonsEnabled(true);
                    MusicLog.i(TAG, "识别成功: " + info + " id=" + songId);

                    // Offer to play the identified song
                    if (songId > 0) {
                        tvResult.setOnClickListener(v -> playSong(songId, songName, artist, album));
                        tvResult.append("\n\n点击此处播放");
                    }
                }

                @Override
                public void onError(String message) {
                    tvStatus.setText("识别失败: " + message);
                    tvResult.setVisibility(android.view.View.GONE);
                    btnStop.setVisibility(android.view.View.GONE);
                    setButtonsEnabled(true);
                    MusicLog.w(TAG, modeLabel + " 识别失败: " + message);
                }
            };

            if (humMode) {
                MusicApiHelper.recognizeHum(pcm, cookie, cb);
            } else {
                MusicApiHelper.recognizeSong(pcm, cookie, cb);
            }
        }).start();
    }

    /** Record raw 16-bit PCM at 16kHz mono for the given number of seconds. */
    private byte[] recordPcm(int seconds) {
        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize <= 0) bufferSize = 4096;

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                MusicLog.e(TAG, "音频硬件初始化失败，无法录音");
                audioRecord.release();
                audioRecord = null;
                return null;
            }

            audioRecord.startRecording();
            isRecording = true;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[bufferSize];
            long endTime = System.currentTimeMillis() + (long) seconds * 1000;

            while (System.currentTimeMillis() < endTime && isRecording) {
                int read = audioRecord.read(buf, 0, buf.length);
                if (read > 0) {
                    baos.write(buf, 0, read);
                }
            }

            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            isRecording = false;
            return baos.toByteArray();
        } catch (Exception e) {
            MusicLog.e(TAG, "录音异常", e);
            isRecording = false;
            if (audioRecord != null) {
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            }
            return null;
        }
    }

    /** Stop the current recording early and proceed to recognition. */
    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            handler.removeCallbacks(countdownRunnable);
            tvStatus.setText("录音已停止，正在识别...");
            btnStop.setVisibility(android.view.View.GONE);
        }
    }

    private void playSong(long songId, String name, String artist, String album) {
        Song song = new Song(songId, name, artist, album);
        java.util.List<Song> list = new ArrayList<>();
        list.add(song);
        MusicPlayerManager.getInstance().setPlaylist(list, 0);
        MusicPlayerManager.getInstance().playCurrent();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnListen.setEnabled(enabled);
        btnHum.setEnabled(enabled);
        btnListen.setAlpha(enabled ? 1f : 0.5f);
        btnHum.setAlpha(enabled ? 1f : 0.5f);
    }

    private TextView makeTileButton(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tv.setPadding(px(12), px(10), px(12), px(10));
        tv.setBackgroundColor(0xFF1565C0);
        tv.setClickable(true);
        tv.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void addSpacer(LinearLayout parent, int heightPx) {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        parent.addView(v);
    }

    private int px(int base) {
        int w = getResources().getDisplayMetrics().widthPixels;
        return (int) (base * w / 320f + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        handler.removeCallbacksAndMessages(null);
        if (audioRecord != null) {
            try { audioRecord.stop(); audioRecord.release(); } catch (Exception ignored) {}
            audioRecord = null;
        }
    }
}
