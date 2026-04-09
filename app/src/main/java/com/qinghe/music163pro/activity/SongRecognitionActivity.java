package com.qinghe.music163pro.activity;

import android.Manifest;
import android.content.Intent;
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

import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.AudioFingerprintHelper;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.api.MusicApiHelper;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public class SongRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "SongRecognitionActivity";
    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 10;

    private TextView tvStatus;
    private TextView tvHint;
    private TextView btnRecord;

    private volatile boolean isRecording = false;
    private volatile boolean isRecognizing = false;
    private AudioRecord audioRecord;
    private final Handler handler = new Handler();
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getBoolean("keep_screen_on", false)) {
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
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(px(14), px(12), px(14), px(16));

        TextView title = new TextView(this);
        title.setText("听歌识曲");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        addSpacer(root, px(10));

        tvHint = new TextView(this);
        tvHint.setText("开始录音后靠近声源\n录完后自动识别");
        tvHint.setTextColor(0xB3FFFFFF);
        tvHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvHint.setGravity(Gravity.CENTER);
        root.addView(tvHint);

        addSpacer(root, px(18));

        btnRecord = new TextView(this);
        btnRecord.setTextColor(0xFFFFFFFF);
        btnRecord.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        btnRecord.setTypeface(btnRecord.getTypeface(), android.graphics.Typeface.BOLD);
        btnRecord.setGravity(Gravity.CENTER);
        btnRecord.setPadding(px(12), px(26), px(12), px(26));
        btnRecord.setClickable(true);
        btnRecord.setFocusable(true);
        btnRecord.setOnClickListener(v -> {
            if (isRecognizing) {
                return;
            }
            if (isRecording) {
                stopRecording();
            } else {
                startRecognition();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRecord.setLayoutParams(buttonParams);
        root.addView(btnRecord);

        addSpacer(root, px(14));

        tvStatus = new TextView(this);
        tvStatus.setTextColor(0x80FFFFFF);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvStatus.setGravity(Gravity.CENTER);
        root.addView(tvStatus);

        addSpacer(root, px(10));

        TextView tip = new TextView(this);
        tip.setText("最长录音 " + RECORD_SECONDS + " 秒\n识别失败会停留在当前界面");
        tip.setTextColor(0x66FFFFFF);
        tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(11));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        scroll.addView(root);
        setContentView(scroll);

        updateIdleUi();
    }

    private void startRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        doRecord();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doRecord();
            } else {
                Toast.makeText(this, "需要录音权限才能识别歌曲", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void doRecord() {
        MusicLog.op(TAG, "开始录音", null);
        isRecognizing = false;
        setRecordButtonState("结束录音", 0xFFD32F2F, true);
        tvHint.setText("正在录音，请保持环境安静");

        final int[] remaining = {RECORD_SECONDS};
        tvStatus.setText("录音中 " + RECORD_SECONDS + "s...");
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                remaining[0]--;
                if (remaining[0] > 0 && isRecording) {
                    tvStatus.setText("录音中 " + remaining[0] + "s...");
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
                    updateIdleUi();
                    tvStatus.setText("录音失败");
                    Toast.makeText(this, "录音失败，请检查麦克风", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            runOnUiThread(() -> beginRecognition(pcm));
        }).start();
    }

    private void beginRecognition(byte[] pcm) {
        isRecognizing = true;
        setRecordButtonState("识别中...", 0xFF616161, false);
        tvHint.setText("正在生成指纹并请求接口");
        tvStatus.setText("请稍候...");
        MusicLog.d(TAG, "录音完成，开始生成指纹 bytes=" + pcm.length);

        AudioFingerprintHelper.generateFingerprint(this, pcm, SAMPLE_RATE, new AudioFingerprintHelper.Callback() {
            @Override
            public void onSuccess(String fingerprintBase64, int durationSec) {
                String cookie = MusicPlayerManager.getInstance().getCookie();
                MusicApiHelper.recognizeSong(fingerprintBase64, durationSec, cookie,
                        new MusicApiHelper.RecognitionCallback() {
                            @Override
                            public void onResult(java.util.List<Song> songs) {
                                isRecognizing = false;
                                updateIdleUi();
                                openResultPage(songs);
                            }

                            @Override
                            public void onError(String message) {
                                isRecognizing = false;
                                updateIdleUi();
                                Toast.makeText(SongRecognitionActivity.this,
                                        "识别失败: " + message, Toast.LENGTH_SHORT).show();
                                tvStatus.setText("识别失败，请重试");
                                MusicLog.w(TAG, "识别失败: " + message);
                            }
                        });
            }

            @Override
            public void onError(String message) {
                isRecognizing = false;
                updateIdleUi();
                Toast.makeText(SongRecognitionActivity.this,
                        "指纹生成失败: " + message, Toast.LENGTH_SHORT).show();
                tvStatus.setText("指纹生成失败");
                MusicLog.w(TAG, "指纹生成失败: " + message);
            }
        });
    }

    private void openResultPage(java.util.List<Song> songs) {
        Intent intent = new Intent(this, SongRecognitionResultActivity.class);
        intent.putExtra(SongRecognitionResultActivity.EXTRA_RESULTS, new ArrayList<>(songs));
        startActivity(intent);
    }

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

            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            audioRecord.release();
            audioRecord = null;
            isRecording = false;
            return baos.toByteArray();
        } catch (Exception e) {
            MusicLog.e(TAG, "录音异常", e);
            isRecording = false;
            if (audioRecord != null) {
                try {
                    audioRecord.release();
                } catch (Exception ignored) {
                }
                audioRecord = null;
            }
            return null;
        }
    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }
        isRecording = false;
        handler.removeCallbacks(countdownRunnable);
        setRecordButtonState("识别中...", 0xFF616161, false);
        tvHint.setText("录音结束，开始识别");
        tvStatus.setText("处理中...");
    }

    private void updateIdleUi() {
        setRecordButtonState("开始录音", 0xFFBB86FC, true);
        tvHint.setText("开始录音后靠近声源\n录完后自动识别");
        if (!isRecording && !isRecognizing) {
            tvStatus.setText("点击按钮开始识别");
        }
    }

    private void setRecordButtonState(String text, int backgroundColor, boolean enabled) {
        btnRecord.setText(text);
        btnRecord.setBackgroundColor(backgroundColor);
        btnRecord.setEnabled(enabled);
        btnRecord.setAlpha(enabled ? 1f : 0.75f);
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
        isRecognizing = false;
        handler.removeCallbacksAndMessages(null);
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
}
