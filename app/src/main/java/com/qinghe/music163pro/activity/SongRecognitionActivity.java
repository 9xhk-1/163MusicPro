package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
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
import com.qinghe.music163pro.util.AudioFingerprintHelper;
import com.qinghe.music163pro.util.MusicLog;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SongRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "SongRecognitionActivity";
    private static final String PREFS_NAME = "music163_settings";
    private static final String PREF_RECOGNITION_MODE = "song_recognition_mode";
    private static final int MODE_MANUAL = 0;
    private static final int MODE_AUTO = 1;
    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;
    private static final int MIN_SECONDS = 3;
    private static final int MANUAL_MAX_SECONDS = 10;
    private static final int AUTO_CHUNK_SECONDS = 3;
    private static final int AUTO_CHUNK_BYTES = BYTES_PER_SECOND * AUTO_CHUNK_SECONDS;
    private static final String MESSAGE_RECORD_TOO_SHORT = "录音不能小于三秒";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object audioLock = new Object();
    private final Object autoQueueLock = new Object();
    private final ArrayDeque<byte[]> autoChunkQueue = new ArrayDeque<>();

    private TextView tvMode;
    private TextView tvHint;
    private TextView tvStatus;
    private LinearLayout btnRecord;
    private TextView tvRecordLabel;
    private ImageView ivRecordIcon;

    private volatile boolean isRecording = false;
    private volatile boolean isRecognizing = false;
    private volatile int activeSessionId = 0;
    private volatile boolean autoChunkRequestRunning = false;
    private volatile boolean recognitionSucceeded = false;
    private AudioRecord audioRecord;
    private AcousticEchoCanceler acousticEchoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private Runnable countdownRunnable;
    private boolean pausedPlaybackForRecognition = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        buildUi();
        updateIdleUi();
        MusicLog.op(TAG, "打开识曲界面", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isRecording && !isRecognizing) {
            updateIdleUi();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF212121);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(px(14), px(12), px(14), px(18));

        TextView title = new TextView(this);
        title.setText("听歌识曲");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addSpacer(root, px(10));

        tvMode = new TextView(this);
        tvMode.setTextColor(0xFFBB86FC);
        tvMode.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvMode.setTypeface(tvMode.getTypeface(), Typeface.BOLD);
        tvMode.setGravity(Gravity.CENTER);
        root.addView(tvMode);

        addSpacer(root, px(18));

        btnRecord = new LinearLayout(this);
        btnRecord.setOrientation(LinearLayout.VERTICAL);
        btnRecord.setGravity(Gravity.CENTER);
        int diameter = Math.max(px(144), (int) (getResources().getDisplayMetrics().widthPixels * 0.5f));
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(diameter, diameter);
        btnRecord.setLayoutParams(circleParams);
        btnRecord.setClickable(true);
        btnRecord.setFocusable(true);
        btnRecord.setOnClickListener(v -> onRecordButtonClick());
        root.addView(btnRecord);

        ivRecordIcon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(px(42), px(42));
        ivRecordIcon.setLayoutParams(iconParams);
        ivRecordIcon.setImageResource(R.drawable.ic_watch_mic);
        btnRecord.addView(ivRecordIcon);

        addSpacer(btnRecord, px(10));

        tvRecordLabel = new TextView(this);
        tvRecordLabel.setTextColor(0xFFFFFFFF);
        tvRecordLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tvRecordLabel.setTypeface(tvRecordLabel.getTypeface(), Typeface.BOLD);
        tvRecordLabel.setGravity(Gravity.CENTER);
        btnRecord.addView(tvRecordLabel);

        addSpacer(root, px(18));

        tvHint = new TextView(this);
        tvHint.setTextColor(0xD9FFFFFF);
        tvHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        tvHint.setGravity(Gravity.CENTER);
        root.addView(tvHint);

        addSpacer(root, px(10));

        tvStatus = new TextView(this);
        tvStatus.setTextColor(0x99FFFFFF);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvStatus.setGravity(Gravity.CENTER);
        root.addView(tvStatus);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void onRecordButtonClick() {
        if (isRecognizing && !isRecording) {
            return;
        }
        if (isRecording) {
            stopRecognitionByUser();
        } else {
            startRecognition();
        }
    }

    private void startRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        int sessionId = ++activeSessionId;
        recognitionSucceeded = false;
        isRecognizing = false;
        isRecording = true;
        clearAutoQueue();
        autoChunkRequestRunning = false;
        pausePlaybackIfNeeded();

        if (isAutoMode()) {
            updateAutoRecordingUi();
            MusicLog.op(TAG, "开始自动识曲", null);
            new Thread(() -> runAutoRecordingLoop(sessionId)).start();
        } else {
            updateManualRecordingUi(MANUAL_MAX_SECONDS);
            MusicLog.op(TAG, "开始手动识曲录音", null);
            startManualCountdown();
            new Thread(() -> runManualRecording(sessionId)).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecognition();
            } else {
                Toast.makeText(this, "需要录音权限才能识别歌曲", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void runManualRecording(int sessionId) {
        byte[] pcm = recordPcmUntilStopped(MANUAL_MAX_SECONDS, sessionId, null);
        handler.removeCallbacks(countdownRunnable);
        if (sessionId != activeSessionId) {
            return;
        }
        if (pcm == null || pcm.length == 0) {
            runOnUiThread(() -> {
                if (sessionId != activeSessionId) return;
                finishRecognitionUi(true, false, "录音失败", false);
                Toast.makeText(this, "录音失败，请检查麦克风", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        int recordedSeconds = calculateRecordedSeconds(pcm.length);
        if (recordedSeconds < MIN_SECONDS) {
            runOnUiThread(() -> {
                if (sessionId != activeSessionId) return;
                finishRecognitionUi(true, false, MESSAGE_RECORD_TOO_SHORT, false);
                Toast.makeText(this, MESSAGE_RECORD_TOO_SHORT, Toast.LENGTH_SHORT).show();
            });
            return;
        }

        runOnUiThread(() -> beginRecognitionRequest(sessionId, pcm, true));
    }

    private void runAutoRecordingLoop(int sessionId) {
        ByteArrayOutputStream chunkStream = new ByteArrayOutputStream();
        final long[] totalBytes = {0L};
        recordPcmUntilStopped(0, sessionId, bytes -> {
            totalBytes[0] += bytes.length;
            try {
                chunkStream.write(bytes);
            } catch (Exception ignored) {
            }
            byte[] buffered = chunkStream.toByteArray();
            int offset = 0;
            while (buffered.length - offset >= AUTO_CHUNK_BYTES) {
                byte[] chunk = Arrays.copyOfRange(buffered, offset, offset + AUTO_CHUNK_BYTES);
                offset += AUTO_CHUNK_BYTES;
                enqueueAutoChunk(sessionId, chunk);
            }
            if (offset > 0) {
                chunkStream.reset();
                if (buffered.length > offset) {
                    chunkStream.write(buffered, offset, buffered.length - offset);
                }
            }
        });

        handler.post(() -> {
            if (sessionId != activeSessionId || recognitionSucceeded) {
                return;
            }
            int recordedSeconds = calculateRecordedSeconds(totalBytes[0]);
            if (recordedSeconds < MIN_SECONDS) {
                finishRecognitionUi(true, false, MESSAGE_RECORD_TOO_SHORT, false);
                Toast.makeText(this, MESSAGE_RECORD_TOO_SHORT, Toast.LENGTH_SHORT).show();
            } else {
                finishRecognitionUi(true, false, "已停止自动识别", false);
            }
        });
    }

    private byte[] recordPcmUntilStopped(int maxSeconds, int sessionId, ChunkCallback chunkCallback) {
        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize <= 0) {
            bufferSize = 4096;
        }

        ByteArrayOutputStream fullOutput = chunkCallback == null ? new ByteArrayOutputStream() : null;
        byte[] buf = new byte[bufferSize];
        long endTime = maxSeconds > 0 ? SystemClock.elapsedRealtime() + maxSeconds * 1000L : Long.MAX_VALUE;

        try {
            synchronized (audioLock) {
                // VOICE_RECOGNITION is preferred here over MIC because watch devices
                // may expose built-in preprocessing for recognition scenarios, which
                // helps reduce captured self-playback and ambient noise.
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release();
                    audioRecord = null;
                    return null;
                }
                // These effects are optional on Android watches, but when available
                // they further reduce echo/noise so the recorder is less likely to
                // pick up the watch speaker output during recognition.
                enableAudioEffects(audioRecord.getAudioSessionId());
                audioRecord.startRecording();
            }

            while (isRecording && sessionId == activeSessionId && SystemClock.elapsedRealtime() < endTime) {
                int read;
                synchronized (audioLock) {
                    read = audioRecord != null ? audioRecord.read(buf, 0, buf.length) : -1;
                }
                if (read > 0) {
                    if (fullOutput != null) {
                        fullOutput.write(buf, 0, read);
                    }
                    if (chunkCallback != null) {
                        byte[] chunk = Arrays.copyOf(buf, read);
                        chunkCallback.onChunk(chunk);
                    }
                }
            }
        } catch (Exception e) {
            MusicLog.e(TAG, "录音异常", e);
            return null;
        } finally {
            stopAudioRecordInternal();
            isRecording = false;
        }
        return fullOutput != null ? fullOutput.toByteArray() : null;
    }

    private void enqueueAutoChunk(int sessionId, byte[] chunk) {
        if (sessionId != activeSessionId || !isRecording) {
            return;
        }
        synchronized (autoQueueLock) {
            autoChunkQueue.add(chunk);
        }
        handler.post(() -> dispatchNextAutoChunk(sessionId));
    }

    private void dispatchNextAutoChunk(int sessionId) {
        if (sessionId != activeSessionId || !isRecording || recognitionSucceeded || !isAutoMode()) {
            return;
        }
        if (autoChunkRequestRunning) {
            return;
        }
        byte[] chunk;
        synchronized (autoQueueLock) {
            chunk = autoChunkQueue.poll();
        }
        if (chunk == null) {
            return;
        }
        autoChunkRequestRunning = true;
        beginRecognitionRequest(sessionId, chunk, false);
    }

    private void beginRecognitionRequest(int sessionId, byte[] pcm, boolean showErrors) {
        if (sessionId != activeSessionId) {
            return;
        }
        isRecognizing = true;
        if (showErrors) {
            updateProcessingUi();
        } else {
            tvStatus.setText("自动识别中，正在轮询...");
        }
        AudioFingerprintHelper.generateFingerprint(this, pcm, SAMPLE_RATE,
                new AudioFingerprintHelper.Callback() {
                    @Override
                    public void onSuccess(String fingerprintBase64, int durationSec) {
                        if (sessionId != activeSessionId) {
                            return;
                        }
                        String cookie = MusicPlayerManager.getInstance().getCookie();
                        MusicApiHelper.recognizeSong(fingerprintBase64, durationSec, cookie,
                                new MusicApiHelper.RecognitionCallback() {
                                    @Override
                                    public void onResult(List<Song> songs) {
                                        if (sessionId != activeSessionId) {
                                            return;
                                        }
                                        isRecognizing = false;
                                        autoChunkRequestRunning = false;
                                        recognitionSucceeded = true;
                                        finishRecognitionUi(false, true, "识别成功", true);
                                        openResultPage(songs);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        if (sessionId != activeSessionId) {
                                            return;
                                        }
                                        isRecognizing = false;
                                        autoChunkRequestRunning = false;
                                        if (showErrors) {
                                            finishRecognitionUi(true, false, "识别失败，请重试", false);
                                            Toast.makeText(SongRecognitionActivity.this,
                                                    "识别失败: " + message, Toast.LENGTH_SHORT).show();
                                        } else {
                                            MusicLog.w(TAG, "自动识曲未命中: " + message);
                                            tvStatus.setText("自动识别中，继续监听...");
                                            dispatchNextAutoChunk(sessionId);
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        if (sessionId != activeSessionId) {
                            return;
                        }
                        isRecognizing = false;
                        autoChunkRequestRunning = false;
                        if (showErrors) {
                            finishRecognitionUi(true, false, "指纹生成失败", false);
                            Toast.makeText(SongRecognitionActivity.this,
                                    "指纹生成失败: " + message, Toast.LENGTH_SHORT).show();
                        } else {
                            MusicLog.w(TAG, "自动识曲指纹失败: " + message);
                            tvStatus.setText("自动识别中，继续监听...");
                            dispatchNextAutoChunk(sessionId);
                        }
                    }
                });
    }

    private void stopRecognitionByUser() {
        int sessionId = activeSessionId;
        if (sessionId == 0) {
            return;
        }
        isRecording = false;
        isRecognizing = true;
        handler.removeCallbacks(countdownRunnable);
        stopAudioRecordAsync();
        setCircleButtonState(0xFF616161, false, "处理中");
        if (isAutoMode()) {
            tvHint.setText("正在停止自动识别");
            tvStatus.setText("请稍候...");
        } else {
            tvHint.setText("录音已结束");
            tvStatus.setText("正在处理...");
        }
    }

    private void pausePlaybackIfNeeded() {
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        if (playerManager.isPlaying()) {
            pausedPlaybackForRecognition = true;
            playerManager.pause();
        } else {
            pausedPlaybackForRecognition = false;
        }
    }

    private void resumePlaybackIfNeeded() {
        if (pausedPlaybackForRecognition) {
            pausedPlaybackForRecognition = false;
            MusicPlayerManager.getInstance().resume();
        }
    }

    private void finishRecognitionUi(boolean resumePlayback, boolean keepStopped,
                                     String statusText, boolean keepProcessingText) {
        handler.removeCallbacks(countdownRunnable);
        isRecording = false;
        isRecognizing = false;
        clearAutoQueue();
        stopAudioRecordAsync();
        if (!keepStopped) {
            ++activeSessionId;
        }
        if (resumePlayback) {
            resumePlaybackIfNeeded();
        } else {
            pausedPlaybackForRecognition = false;
        }
        updateIdleUi();
        if (statusText != null && !statusText.isEmpty()) {
            tvStatus.setText(statusText);
        }
        if (keepProcessingText) {
            tvHint.setText("识别成功，正在打开结果");
        }
    }

    private void openResultPage(List<Song> songs) {
        Intent intent = new Intent(this, SongRecognitionResultActivity.class);
        intent.putExtra(SongRecognitionResultActivity.EXTRA_RESULTS, new ArrayList<>(songs));
        startActivity(intent);
        finish();
    }

    private void updateIdleUi() {
        setCircleButtonState(0xFFBB86FC, true, "开始录音");
        tvMode.setText(isAutoMode() ? "当前模式：自动识别" : "当前模式：手动暂停");
        if (isAutoMode()) {
            tvHint.setText("点一下开始持续监听\n每三秒自动识别一次");
        } else {
            tvHint.setText("点一下开始录音\n录音结束后进行识别");
        }
        if (!isRecording && !isRecognizing) {
            tvStatus.setText("录音至少三秒");
        }
    }

    private void updateManualRecordingUi(int remainingSeconds) {
        setCircleButtonState(0xFFD32F2F, true, "结束录音");
        tvHint.setText("请让手表靠近声源\n录音时已暂停手表播放");
        tvStatus.setText("录音中 " + remainingSeconds + "s");
    }

    private void updateAutoRecordingUi() {
        setCircleButtonState(0xFFD32F2F, true, "停止识别");
        tvHint.setText("正在持续监听\n每三秒自动识别一次");
        tvStatus.setText("自动识别中，等待结果...");
    }

    private void updateProcessingUi() {
        setCircleButtonState(0xFF616161, false, "识别中");
        tvHint.setText("正在生成指纹并请求接口");
        tvStatus.setText("请稍候...");
    }

    private void setCircleButtonState(int color, boolean enabled, String label) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        btnRecord.setBackground(bg);
        btnRecord.setEnabled(enabled);
        btnRecord.setAlpha(enabled ? 1f : 0.82f);
        tvRecordLabel.setText(label);
        ivRecordIcon.setAlpha(enabled ? 1f : 0.85f);
    }

    private void startManualCountdown() {
        final int[] remaining = {MANUAL_MAX_SECONDS};
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRecording || isAutoMode()) {
                    return;
                }
                remaining[0]--;
                if (remaining[0] > 0) {
                    tvStatus.setText("录音中 " + remaining[0] + "s");
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(countdownRunnable, 1000);
    }

    private boolean isAutoMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(PREF_RECOGNITION_MODE, MODE_AUTO) == MODE_AUTO;
    }

    private void clearAutoQueue() {
        synchronized (autoQueueLock) {
            autoChunkQueue.clear();
        }
        autoChunkRequestRunning = false;
    }

    private void enableAudioEffects(int audioSessionId) {
        try {
            // Some watches/ROMs do not expose these effects even when the API exists,
            // so failures here are expected and should not break recording.
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId);
                if (acousticEchoCanceler != null) {
                    acousticEchoCanceler.setEnabled(true);
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                }
            }
        } catch (Exception e) {
            MusicLog.w(TAG, "启用录音降噪失败", e);
        }
    }

    private void stopAudioRecordAsync() {
        synchronized (audioLock) {
            stopAudioRecordInternal();
        }
    }

    private void stopAudioRecordInternal() {
        releaseAudioEffects();
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            try {
                audioRecord.release();
            } catch (Exception ignored) {
            }
            audioRecord = null;
        }
    }

    private void releaseAudioEffects() {
        if (acousticEchoCanceler != null) {
            try {
                acousticEchoCanceler.release();
            } catch (Exception ignored) {
            }
            acousticEchoCanceler = null;
        }
        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.release();
            } catch (Exception ignored) {
            }
            noiseSuppressor = null;
        }
    }

    private void addSpacer(LinearLayout parent, int heightPx) {
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        parent.addView(spacer);
    }

    private int px(int base) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (base * screenWidth / 320f + 0.5f);
    }

    private int calculateRecordedSeconds(long totalBytes) {
        return Math.max(1, (int) Math.ceil(totalBytes / (double) BYTES_PER_SECOND));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        isRecognizing = false;
        handler.removeCallbacksAndMessages(null);
        stopAudioRecordAsync();
        clearAutoQueue();
    }

    private interface ChunkCallback {
        void onChunk(byte[] bytes);
    }
}
