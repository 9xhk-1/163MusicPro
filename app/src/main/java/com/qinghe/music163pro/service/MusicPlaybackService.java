package com.qinghe.music163pro.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.activity.MainActivity;
import com.qinghe.music163pro.player.AppVolumeManager;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.model.Song;


/**
 * Foreground service that keeps the music player alive during background
 * playback. It maintains two separate notifications:
 *
 * 1. Keep-alive foreground notification (ID=1, channel "keepalive") –
 *    a minimal notification required by Android to allow foreground service.
 *
 * 2. Playback card notification (ID=258) – a standard Android notification with
 *    a self-drawn 100dp RemoteViews: song name / artist on two separate lines,
 *    and five graded control keys. The seek row was removed in v54: refreshing
 *    it required a 5s periodic wake-up rebuilding the whole RemoteViews.
 *    Target device is 华为儿童手表 4X (Android 10, 320x360), so the card must NOT
 *    rely on 小天才 XTC's magic flag / extras: those are applied only when
 *    com.xtc.provider actually exists on the device.
 *
 * The card doubles as the foreground notification, so there is only one
 * user-visible notification and no "running in background" text.
 */
public class MusicPlaybackService extends Service {

    // ── Foreground keep-alive channel (minimal, not user-visible) ────────────
    // ── XTC watch music card channel ─────────────────────────────────────────
    private static final String XTC_CHANNEL_ID        = "1024";
    // 华为儿童手表 4X 等普通 Android 手表：走标准通知通道，前台服务不能用 IMPORTANCE_MIN
    private static final String STANDARD_CHANNEL_ID   = "music_playback";
    private static final int    XTC_NOTIFICATION_ID   = 258;

    // XTC system: special flag that tells the watch face to capture this notification
    private static final int    XTC_MUSIC_FLAG        = 0x08000000;
    // XTC ContentProvider URI used to detect if the XTC music flag should be applied
    private static final String XTC_PROVIDER_URI      =
            "content://com.xtc.provider/BaseDataProvider/music_notification_type/12";
    // PLAY_STATE values defined by the XTC SDK reverse-engineering
    private static final int    PLAY_STATE_PLAYING    = 16;
    private static final int    PLAY_STATE_PAUSED     = 32;

    public static final String ACTION_PREVIOUS    = "com.qinghe.music163pro.ACTION_PREVIOUS";
    public static final String ACTION_PLAY_PAUSE  = "com.qinghe.music163pro.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT        = "com.qinghe.music163pro.ACTION_NEXT";
    public static final String ACTION_PLAY        = "com.qinghe.music163pro.ACTION_PLAY";
    public static final String ACTION_PAUSE       = "com.qinghe.music163pro.ACTION_PAUSE";
    public static final String ACTION_CLOSE       = "com.qinghe.music163pro.ACTION_CLOSE";
    // 通知栏「播放顺序」按键：一个按钮循环 列表循环 → 单曲循环 → 随机播放，
    // 顺序与主界面右上角的 btn_play_mode 完全一致。
    private static final String ACTION_CYCLE_PLAY_MODE = "com.qinghe.music163pro.ACTION_CYCLE_PLAY_MODE";
    // v54：通知栏播放时间轴已移除。原先为了每 5 秒刷新进度，服务必须常驻一个
    // Handler 定时唤醒 + 整卡重建 RemoteViews（含 8 个透明点击区与 13 次
    // PendingIntent 绑定）。去掉后通知只在切歌 / 播放状态变化时刷新一次。
    // Internal: fired by AlarmManager to bring the service back after being killed
    private static final String ACTION_RESTART     = "com.qinghe.music163pro.ACTION_RESTART";
    // Internal: 外部状态变化（如耳机断开自动暂停）后刷新媒体会话与卡片
    private static final String ACTION_SYNC_STATE  = "com.qinghe.music163pro.ACTION_SYNC_STATE";

    // ── 蓝牙媒体键（AirPods / TWS）────────────────────────────────────────────
    // 接收器只转发原始键码，归一 / 去抖 / 连击判定都在这个常驻服务里做
    public static final String ACTION_MEDIA_KEY    = "com.qinghe.music163pro.ACTION_MEDIA_KEY";
    public static final String EXTRA_KEY_CODE      = "media_key_code";
    public static final String EXTRA_BLUETOOTH_KEY = "bluetooth_key";
    // 同一动作 220ms 内只认一次：有 ROM 会把一次按键同时投给接收器和活跃会话
    private static final long MEDIA_KEY_DEDUPE_MS  = 220L;
    // AirPods 双击 / 三击耳柄被部分蓝牙栈报成 2 / 3 个 PLAY_PAUSE(HEADSETHOOK)
    private static final long HOOK_MULTI_CLICK_MS  = 500L;
    private static final long HOOK_NEXT_DELAY_MS   = 420L;
    private String lastMediaKeyAction;
    private long   lastMediaKeyMs;
    private int    hookClickCount;
    private long   lastHookMs;
    private Runnable pendingHookAction;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager   notificationManager;
    private AlarmManager          alarmManager;
    private MediaSession          mediaSession;
    private AudioManager          audioManager;
    private ComponentName         mediaButtonReceiver;
    // 统一音量：媒体会话音量提供者 + 系统音量变化兜底
    private VolumeProvider        volumeProvider;
    private AppVolumeManager.LevelChangeListener volumeLevelListener;
    private BroadcastReceiver     volumeChangeReceiver;
    private boolean               volumeReceiverRegistered;

    private String  currentSongName  = "";
    private String  currentArtist    = "";
    private String  currentCoverUrl  = "";
    private boolean currentIsPlaying = false;
    // 只用于「合并同一帧的多次通知刷新」与媒体键连击判定，不再跑周期轮询
    private final Handler cardHandler = new Handler(Looper.getMainLooper());

    // v54：一次切歌会触发「onSongChanged → startPlaybackService」、
    // 「notifyPlayStateChanged(false) → startPlaybackService」、
    // 「notifyPlayStateChanged(true) → startPlaybackService」和通知按键的 postXtcCard，
    // 旧实现会把整张 RemoteViews 重建 3~5 次。这里合并成一帧一次。
    private boolean cardRefreshPending = false;
    private final Runnable cardRefreshTick = () -> {
        cardRefreshPending = false;
        currentIsPlaying = MusicPlayerManager.getInstance().isPlaying();
        notificationManager.notify(XTC_NOTIFICATION_ID,
                buildXtcCardNotification(currentSongName, currentArtist,
                        currentCoverUrl, currentIsPlaying));
    };
    /** 只在主线程调用：同一轮消息循环里的多次刷新合并成一次，不额外增加延迟。 */
    private void scheduleCardRefresh() {
        if (cardRefreshPending) return;
        cardRefreshPending = true;
        cardHandler.post(cardRefreshTick);
    }

    // 设备类型 / PendingIntent 在一次服务生命周期内不变，缓存掉重复的 binder 调用
    private Boolean xtcDeviceCached;
    private PendingIntent cachedContentPi;
    private PendingIntent cachedPrevPi;
    private PendingIntent cachedPlayPausePi;
    private PendingIntent cachedNextPi;
    private PendingIntent cachedClosePi;
    private PendingIntent cachedModePi;

    /**
     * v54 耗电修复：PARTIAL_WAKE_LOCK 只在真正播放时持有。
     * 原实现在 onCreate 里一次性 acquire(12 小时)，暂停、后台挂机、锁屏后
     * CPU 一直不能休眠；播放期间 MediaPlayer/ExoPlayer 自己已经持有唤醒锁，
     * 所以这里只做「跟着播放状态开关」这一件事。
     */
    private final Runnable syncPowerTick = this::syncPowerState;

    private void syncPowerState() {
        boolean playing = MusicPlayerManager.getInstance().isPlaying();
        currentIsPlaying = playing;
        if (playing) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        alarmManager        = (AlarmManager) getSystemService(ALARM_SERVICE);
        createChannels();
        initializeMediaSession();
        // 前台通知直接用播放控制卡片：通知栏只保留一条，不再有单独的"后台运行"提示
        startForeground(XTC_NOTIFICATION_ID,
                buildXtcCardNotification(currentSongName, currentArtist,
                        currentCoverUrl, currentIsPlaying));
        syncPowerState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 放到本轮消息循环末尾执行：所有 action 分支都是同步改播放状态，
        // 等它们执行完再统一同步唤醒锁，暂停时就能立刻释放。
        cardHandler.removeCallbacks(syncPowerTick);
        cardHandler.post(syncPowerTick);
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_MEDIA_KEY.equals(action)) {
                handleMediaKey(intent.getIntExtra(EXTRA_KEY_CODE, -1),
                        intent.getBooleanExtra(EXTRA_BLUETOOTH_KEY, false));
                return START_STICKY;
            }
            if (ACTION_PREVIOUS.equals(action)) {
                MusicPlayerManager.getInstance().previous();
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_PLAY_PAUSE.equals(action)) {
                MusicPlayerManager p = MusicPlayerManager.getInstance();
                if (p.isPlaying()) p.pause(); else p.resume();
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_PLAY.equals(action)) {
                MusicPlayerManager.getInstance().resume();
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_PAUSE.equals(action)) {
                MusicPlayerManager.getInstance().pause();
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_NEXT.equals(action)) {
                MusicPlayerManager.getInstance().next();
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_CYCLE_PLAY_MODE.equals(action)) {
                MusicPlayerManager p = MusicPlayerManager.getInstance();
                MusicPlayerManager.PlayMode next;
                switch (p.getPlayMode()) {
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
                p.setPlayMode(next);
                savePlayMode(next);
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_CLOSE.equals(action)) {
                closeApp();
                return START_NOT_STICKY;
            }
            if (ACTION_SYNC_STATE.equals(action)) {
                updateMediaSession();
                postXtcCard();
                return START_STICKY;
            }
            if (ACTION_RESTART.equals(action)) {
                // 只负责把服务拉回前台；歌曲信息以当前播放器状态为准
                currentIsPlaying = MusicPlayerManager.getInstance().isPlaying();
                scheduleCardRefresh();
                return START_STICKY;
            }

            // Normal start/update: extract song info and play state
            String songName  = intent.getStringExtra("song_name");
            String artist    = intent.getStringExtra("artist");
            String coverUrl  = intent.getStringExtra("cover_url");
            boolean isPlaying = intent.getBooleanExtra("is_playing", false);

            if (songName != null) currentSongName  = songName;
            if (artist   != null) currentArtist    = artist;
            if (coverUrl != null) currentCoverUrl  = coverUrl;
            currentIsPlaying = isPlaying;
        }

        // 卡片刷新合并到本轮消息循环末尾：切一次歌只重建一次 RemoteViews
        scheduleCardRefresh();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 从最近任务里划掉应用时，很多 ROM 会顺手把服务也停掉。
        // 正在播放就 1.2 秒后把服务拉回来（stopWithTask=false 已经写在清单里）。
        if (!MusicPlayerManager.getInstance().isPlaying()) {
            return;
        }
        try {
            Intent restart = new Intent(this, MusicPlaybackService.class)
                    .setAction(ACTION_RESTART);
            // Android 8+ 后台不允许 startService，必须走 startForegroundService 的 PendingIntent
            PendingIntent pi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? PendingIntent.getForegroundService(this, 11, restart,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.getService(this, 11, restart,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (alarmManager != null) {
                long triggerAt = System.currentTimeMillis() + 1200L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 通知卡片右上角「关闭」：停掉播放、撤掉通知、结束前台服务。
     * 用 stopSelf 而不是 START_STICKY，避免被系统或闹钟再拉起来。
     */
    private void closeApp() {
        try {
            MusicPlayerManager player = MusicPlayerManager.getInstance();
            // 只调用 stop()：pause() 会回调界面，界面那边又会 startPlaybackService 把服务拉起来，
            // 形成"关了又开"的循环。
            player.stop();
        } catch (Exception e) {
            Log.w("MusicPlaybackService", "关闭播放失败", e);
        }
        releaseWakeLock();
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        try {
            notificationManager.cancelAll();
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    /** 供播放器在外部状态变化后刷新通知与媒体会话（如耳机断开自动暂停）。 */
    public static void notifyExternalStateChange(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(context, MusicPlaybackService.class)
                    .setAction(ACTION_SYNC_STATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.w("MusicPlaybackService", "刷新播放通知失败", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 进度轴移除后已无周期任务；一次性清掉全部待执行回调（含合并刷新与媒体键连击）
        cardHandler.removeCallbacksAndMessages(null);
        pendingHookAction = null;
        cardRefreshPending = false;
        AppVolumeManager volume = AppVolumeManager.get();
        if (volume != null && volumeLevelListener != null) {
            volume.removeLevelChangeListener(volumeLevelListener);
            volumeLevelListener = null;
        }
        if (volumeReceiverRegistered && volumeChangeReceiver != null) {
            try {
                unregisterReceiver(volumeChangeReceiver);
            } catch (Exception ignored) {}
            volumeReceiverRegistered = false;
        }
        volumeChangeReceiver = null;
        volumeProvider = null;
        if (audioManager != null && mediaButtonReceiver != null) {
            try {
                audioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiver);
            } catch (Exception ignored) {}
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        releaseWakeLock();
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSession(this, "163MusicPro");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Android 8+ 不再可靠地把 ACTION_MEDIA_BUTTON 投递给清单里静态注册的接收器。
        // 用 setMediaButtonReceiver 把 PendingIntent 显式指向 HeadsetMediaButtonReceiver，
        // 应用被回收后按耳机键也能重新拉起前台服务；setSessionActivity 则让 AVRCP
        // 控制器知道点通知该打开哪个页面。
        try {
            Intent mbrIntent = new Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(new ComponentName(this, HeadsetMediaButtonReceiver.class));
            PendingIntent mbrPi = PendingIntent.getBroadcast(this, 21, mbrIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mediaSession.setMediaButtonReceiver(mbrPi);
            PendingIntent sessionPi = PendingIntent.getActivity(this, 22,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mediaSession.setSessionActivity(sessionPi);
        } catch (Exception e) {
            Log.w("MusicPlaybackService", "设置媒体键接收器失败", e);
        }

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                        || event.getRepeatCount() > 0) {
                    return true;
                }
                if (transportActionForKey(event.getKeyCode()) == null) {
                    return false;
                }
                handleMediaKey(event.getKeyCode(), isBluetoothAudioOn());
                return true;
            }

            @Override public void onPlay() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, isBluetoothAudioOn());
            }

            @Override public void onPause() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, isBluetoothAudioOn());
            }

            @Override public void onStop() {
                dispatchTransport(ACTION_PAUSE);
            }

            @Override public void onSkipToNext() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, isBluetoothAudioOn());
            }

            @Override public void onSkipToPrevious() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, isBluetoothAudioOn());
            }

            @Override public void onFastForward() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, isBluetoothAudioOn());
            }

            @Override public void onRewind() {
                handleMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND, isBluetoothAudioOn());
            }

            @Override public void onSeekTo(long pos) {
                MusicPlayerManager.getInstance().seekTo((int) pos);
                updateMediaSession();
                postXtcCard();
            }
        });
        // 让蓝牙耳机的 AVRCP 音量键也走应用内 40 级，而不是系统 STREAM_MUSIC
        setupRemoteVolumeControl();
        mediaSession.setActive(true);

        // Android 7 routes classic Bluetooth AVRCP keys through AudioManager's
        // registered media-button receiver, as done by the original Kugou app.
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mediaButtonReceiver = new ComponentName(this, HeadsetMediaButtonReceiver.class);
        if (audioManager != null) {
            try {
                audioManager.registerMediaButtonEventReceiver(mediaButtonReceiver);
            } catch (Exception ignored) {}
        }
        updateMediaSession();
    }

    /**
     * AVRCP 键码归一化。AirPods 在不同 Android 蓝牙栈上，双击/三击耳柄可能报成
     * MEDIA_NEXT / FAST_FORWARD / STEP_FORWARD / SKIP_FORWARD 中的任意一种，
     * 旧代码只认 MEDIA_NEXT，于是"只能暂停、不能切歌"。
     */
    private static String transportActionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return ACTION_PLAY;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return ACTION_PAUSE;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return ACTION_PLAY_PAUSE;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                return ACTION_NEXT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                return ACTION_PREVIOUS;
            default:
                return null;
        }
    }

    private boolean isBluetoothAudioOn() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am != null && am.isBluetoothA2dpOn();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 媒体键统一入口：键码归一 → 去抖 → （蓝牙时）连击判定 → 执行。 */
    private void handleMediaKey(int keyCode, boolean bluetooth) {
        String action = transportActionForKey(keyCode);
        if (action == null) return;
        boolean hook = keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
        long now = System.currentTimeMillis();

        // 有 ROM 会把一次按键同时投给静态接收器和活跃会话，220ms 内同动作只认一次
        if (!hook && action.equals(lastMediaKeyAction)
                && now - lastMediaKeyMs < MEDIA_KEY_DEDUPE_MS) {
            return;
        }
        if (!hook) {
            lastMediaKeyAction = action;
            lastMediaKeyMs = now;
        }
        if (hook && bluetooth) {
            handleBluetoothHook(now);
        } else {
            dispatchTransport(action);
        }
    }

    private void dispatchTransport(String action) {
        MusicPlayerManager p = MusicPlayerManager.getInstance();
        if (ACTION_NEXT.equals(action)) {
            p.next();
        } else if (ACTION_PREVIOUS.equals(action)) {
            p.previous();
        } else if (ACTION_PLAY.equals(action)) {
            p.resume();
        } else if (ACTION_PAUSE.equals(action)) {
            p.pause();
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            if (p.isPlaying()) p.pause(); else p.resume();
        } else {
            return;
        }
        updateMediaSession();
        postXtcCard();
    }

    /**
     * 部分 Android 蓝牙栈不把 AirPods 双击/三击翻译成 MEDIA_NEXT / MEDIA_PREVIOUS，
     * 而是连发 2 / 3 个 PLAY_PAUSE(HEADSETHOOK)。在常驻服务里识别连击：
     *   1 击：立刻播放/暂停（单次按压零延迟）
     *   2 击：抵消第 1 击的播放状态，420ms 后切下一首（留时间等第 3 击）
     *   3 击：取消待执行的下一首，改切上一首
     * 这样单击不卡顿，双击/三击也不会因为半路 toggle 而停在暂停态。
     * 判定只在蓝牙 A2DP 路由上启用，手表本体按键语义不受影响。
     */
    private void handleBluetoothHook(long now) {
        if (pendingHookAction != null) {
            cardHandler.removeCallbacks(pendingHookAction);
            pendingHookAction = null;
        }
        if (now - lastHookMs > HOOK_MULTI_CLICK_MS) {
            hookClickCount = 0;
        }
        lastHookMs = now;
        hookClickCount++;

        switch (hookClickCount) {
            case 1:
                dispatchTransport(ACTION_PLAY_PAUSE);
                break;
            case 2:
                dispatchTransport(ACTION_PLAY_PAUSE);
                pendingHookAction = () -> {
                    dispatchTransport(ACTION_NEXT);
                    hookClickCount = 0;
                    pendingHookAction = null;
                };
                cardHandler.postDelayed(pendingHookAction, HOOK_NEXT_DELAY_MS);
                break;
            case 3:
                dispatchTransport(ACTION_PREVIOUS);
                hookClickCount = 0;
                break;
            default:
                hookClickCount = 0;
                break;
        }
    }

    /**
     * 把蓝牙耳机 / 媒体会话的音量键收编到应用内同一套 40 级。
     *
     * 不给 MediaSession 挂 VolumeProvider 时，TWS / AirPods 的 AVRCP 绝对音量
     * 会直接改系统 STREAM_MUSIC（手表只有 6 格，蓝牙又是另一套刻度），
     * 于是"耳机上调音量"和"手表上调音量"变成两个互不相干的范围。
     * 挂上之后，系统会把绝对音量事件交给这里，统一换算成 AppVolumeManager 的等级。
     */
    private void setupRemoteVolumeControl() {
        final AppVolumeManager volume = AppVolumeManager.init(getApplicationContext());
        if (volume == null || mediaSession == null) {
            return;
        }

        volumeProvider = new VolumeProvider(
                VolumeProvider.VOLUME_CONTROL_ABSOLUTE,
                AppVolumeManager.MAX_LEVEL,
                volume.getLevel()) {
            @Override
            public void onAdjustVolume(int direction) {
                AppVolumeManager vm = AppVolumeManager.get();
                if (vm == null) return;
                if (direction > 0) {
                    vm.stepUp();
                } else if (direction < 0) {
                    vm.stepDown();
                }
                setCurrentVolume(vm.getLevel());
            }

            @Override
            public void onSetVolumeTo(int target) {
                AppVolumeManager vm = AppVolumeManager.get();
                if (vm == null) return;
                vm.setLevel(target);
                setCurrentVolume(vm.getLevel());
            }
        };
        mediaSession.setPlaybackToRemote(volumeProvider);

        // 手表上改音量时，把等级经 VolumeProvider 推到耳机物理音量（与 UI 监听分离）
        volumeLevelListener = level -> {
            AppVolumeManager vm = AppVolumeManager.get();
            if (vm != null) {
                vm.markBtVolumePush();
            }
            if (volumeProvider != null) {
                volumeProvider.setCurrentVolume(level);
            }
        };
        volume.setHeadphonePushListener(volumeLevelListener);
        registerVolumeAnchorReceiver();
    }

    /**
     * 兜底：部分 ROM 的蓝牙绝对音量仍会绕过 VolumeProvider 直接改 STREAM_MUSIC。
     * 收到系统音量变化广播就把锚点拉回最大值，保证两种输出的满刻度一致。
     */
    private void registerVolumeAnchorReceiver() {
        if (volumeReceiverRegistered) {
            return;
        }
        volumeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                if (stream != -1 && stream != AudioManager.STREAM_MUSIC) {
                    return;
                }
                // 只在播放时处理，暂停时不去动别的应用的系统音量
                if (!MusicPlayerManager.getInstance().isPlaying()) {
                    return;
                }
                AppVolumeManager vm = AppVolumeManager.get();
                if (vm == null) {
                    return;
                }
                if (vm.isBluetoothOutput()) {
                    // 蓝牙绝对音量：AirPods 耳柄 / 耳机按键直接改系统 STREAM_MUSIC，
                    // 不经过 VolumeProvider。按事件方向相对步进 2 级，让手表显示精准跟随，
                    // 不把等级反推回耳机（避免整数档位四舍五入把调大的音量又压回去）。
                    vm.stepLevelFromBtVolume();
                } else {
                    // 手表扬声器：维持锚点（系统媒体音量顶满），由数字增益控制响度。
                    vm.anchorSystemVolume();
                }
            }
        };
        try {
            registerReceiver(volumeChangeReceiver,
                    new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
            volumeReceiverRegistered = true;
        } catch (Exception e) {
            Log.w("MusicPlaybackService", "注册系统音量监听失败", e);
        }
    }

    private void savePlayMode(MusicPlayerManager.PlayMode mode) {
        getSharedPreferences("music163_settings", MODE_PRIVATE)
                .edit().putString("play_mode", mode.name()).apply();
    }

    private void updateMediaSession() {
        if (mediaSession == null) return;
        MusicPlayerManager player = MusicPlayerManager.getInstance();
        // AVRCP 控制器（AirPods / 车机 / 手表蓝牙）会先读 PlaybackState.actions
        // 再决定发 onSkipToNext 还是 FAST_FORWARD；少声明一个动作就等于不支持该按键。
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND
                | PlaybackState.ACTION_SEEK_TO;
        int state = player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        currentIsPlaying = player.isPlaying();
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, player.getCurrentPosition(), 1.0f)
                .build());
        Song currentSong = player.getCurrentSong();
        if (currentSong != null) {
            currentSongName = currentSong.getDisplayName();
            currentArtist = currentSong.getArtist();
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, currentSong.getDisplayName())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, currentSong.getArtist())
                    .build());
        }
    }

    private void postXtcCard() {
        scheduleCardRefresh();
    }

    // ── Channel creation ──────────────────────────────────────────────────────

    /**
     * 只有真正存在 com.xtc.provider 的设备才走小天才音乐卡片协议。
     * 华为儿童手表 4X 是标准 Android 10，套 XTC 的 magic flag / extras 反而会让
     * 系统把通知当成它不认识的卡片，contentView 变成一片灰色空白。
     */
    private boolean isXtcDevice() {
        Boolean cached = xtcDeviceCached;
        if (cached != null) return cached;
        boolean value;
        try {
            value = getPackageManager().resolveContentProvider("com.xtc.provider", 0) != null;
        } catch (Exception ignored) {
            value = false;
        }
        xtcDeviceCached = value;
        return value;
    }

    /** PendingIntent 的动作 / requestCode 永远是常量，整张卡片只创建一次。 */
    private void ensureCardPendingIntents() {
        if (cachedContentPi != null) return;
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        cachedContentPi = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        cachedPrevPi = buildServicePi(ACTION_PREVIOUS, 1);
        cachedPlayPausePi = buildServicePi(ACTION_PLAY_PAUSE, 2);
        cachedNextPi = buildServicePi(ACTION_NEXT, 3);
        cachedClosePi = buildServicePi(ACTION_CLOSE, 5);
        cachedModePi = buildServicePi(ACTION_CYCLE_PLAY_MODE, 6);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 标准手表（华为 4X）：IMPORTANCE_LOW 才不会被系统折叠成静默通知，
            // 前台服务通知要求至少 LOW，否则 ROM 会替换成系统自带提示。
            NotificationChannel standard = new NotificationChannel(
                    STANDARD_CHANNEL_ID, "音乐播放控制", NotificationManager.IMPORTANCE_LOW);
            standard.setShowBadge(false);
            standard.setSound(null, null);
            standard.enableVibration(false);
            standard.setDescription("播放控制卡片");
            notificationManager.createNotificationChannel(standard);

            // 小天才 XTC：SDK 要求 channel 固定为 "1024" / IMPORTANCE_MIN
            NotificationChannel xtc = new NotificationChannel(
                    XTC_CHANNEL_ID, "notification", NotificationManager.IMPORTANCE_MIN);
            xtc.setShowBadge(false);
            notificationManager.createNotificationChannel(xtc);
        }
    }

    // ── Keep-alive foreground notification (minimal) ──────────────────────────

    // ── XTC watch music card notification ────────────────────────────────────

    private Notification buildXtcCardNotification(String songName, String artist,
                                                   String coverUrl, boolean isPlaying) {
        String title = TextUtils.isEmpty(songName) ? "网易云音乐Pro" : songName;
        String text  = TextUtils.isEmpty(artist)   ? "音乐播放中" : artist;

        // PendingIntents for watch controls（进程内缓存，避免每次刷新都重复创建）
        ensureCardPendingIntents();
        PendingIntent contentPi   = cachedContentPi;
        PendingIntent prevPi      = cachedPrevPi;
        PendingIntent playPausePi = cachedPlayPausePi;
        PendingIntent nextPi      = cachedNextPi;
        PendingIntent closePi     = cachedClosePi;
        PendingIntent modePi      = cachedModePi;

        // RemoteViews = 卡片内容区：歌名 / 歌手各占一行 + 分级控制键，共 100dp
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_remote_view);
        rv.setTextViewText(R.id.tv_notification_song, title);
        rv.setTextViewText(R.id.tv_notification_artist, text);
        rv.setImageViewResource(R.id.iv_play_pause,
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        // 一个按钮表达三态：列表循环 / 单曲循环 / 随机播放，图标与主界面一致
        MusicPlayerManager.PlayMode mode = MusicPlayerManager.getInstance().getPlayMode();
        int modeIcon;
        switch (mode) {
            case SINGLE_REPEAT:
                modeIcon = R.drawable.ic_repeat_one;
                break;
            case RANDOM:
                modeIcon = R.drawable.ic_shuffle;
                break;
            case LIST_LOOP:
            default:
                modeIcon = R.drawable.ic_repeat;
                break;
        }
        rv.setImageViewResource(R.id.iv_play_mode, modeIcon);

        // 点击绑在等宽 FrameLayout 上，触控区比小图标本身大得多
        rv.setOnClickPendingIntent(R.id.fl_previous,   prevPi);
        rv.setOnClickPendingIntent(R.id.fl_play_pause, playPausePi);
        rv.setOnClickPendingIntent(R.id.fl_next,       nextPi);
        rv.setOnClickPendingIntent(R.id.fl_play_mode,  modePi);
        rv.setOnClickPendingIntent(R.id.fl_close,      closePi);

        final boolean xtc = isXtcDevice();
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, xtc ? XTC_CHANNEL_ID : STANDARD_CHANNEL_ID)
                : new Notification.Builder(this);

        // 标准通知：标题/副标题也填上，万一 ROM 不展开自定义布局，至少还有歌曲信息
        b.setContent(rv)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentPi)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(false);

        // 100dp 三行卡片（歌名 / 歌手 / 控制键）：展开态也用同一套布局，
        // 避免被 ROM 折叠成 64dp 裁掉
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            b.setCustomBigContentView(rv);
        }

        Notification n = b.build();
        n.tickerText = title;
        n.flags = Notification.FLAG_ONGOING_EVENT;

        // ── 仅小天才 XTC 设备才套用系统音乐卡片协议 ──────────────────────────
        if (xtc) {
            Bundle extras = new Bundle();
            extras.putBoolean("IS_MUSIC", true);
            extras.putInt("PLAY_STATE",    isPlaying ? PLAY_STATE_PLAYING : PLAY_STATE_PAUSED);
            extras.putString("SONG_NAME",   title);
            extras.putString("SINGER_NAME", text);
            if (!TextUtils.isEmpty(coverUrl)) {
                extras.putString("ALBUM_COVER_URL", coverUrl);
            }
            extras.putParcelable("PLAY_PAUSE_INTENT", playPausePi);
            extras.putParcelable("PREVIOUS_INTENT",   prevPi);
            extras.putParcelable("NEXT_INTENT",        nextPi);
            extras.putParcelable("SONG_NAME_INTENT",   contentPi);
            n.extras.putAll(extras);

            try {
                boolean applyFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
                if (!applyFlag) {
                    String type = getContentResolver().getType(Uri.parse(XTC_PROVIDER_URI));
                    applyFlag = TextUtils.equals(type, "header");
                }
                if (applyFlag) {
                    n.flags |= XTC_MUSIC_FLAG;
                }
            } catch (Exception ignored) {}
        }
        // ──────────────────────────────────────────────────────────────────────

        return n;
    }

    private PendingIntent buildServicePi(String action, int reqCode) {
        Intent i = new Intent(this, MusicPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ── WakeLock ─────────────────────────────────────────────────────────────

    private void acquireWakeLock() {
        // syncPowerState() 每次状态变化都会调，不能重复 acquire
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "music163:playback");
            wakeLock.setReferenceCounted(false);
            // 播放期间持有；暂停由 syncPowerState() 立即 release。
            // 兜底超时设 2 小时，防止状态回调丢失导致永久持锁。
            wakeLock.acquire(2 * 60 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
