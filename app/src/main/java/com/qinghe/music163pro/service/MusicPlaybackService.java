package com.qinghe.music163pro.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import com.qinghe.music163pro.activity.MainActivity;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.concurrent.TimeUnit;

/**
 * Foreground service to keep the music player alive during background
 * playback and when the screen is off. Shows a persistent notification
 * with media controls (previous, play/pause, next).
 *
 * Integrates with 小天才 (XTC) watch system protocol so that the
 * lock-screen music card and quick-menu controls work correctly.
 */
public class MusicPlaybackService extends Service {

    // XTC system requires channel "1024" with IMPORTANCE_MIN
    private static final String CHANNEL_ID = "1024";
    // XTC SDK hardcoded notification ID
    private static final int NOTIFICATION_ID = 258;
    // XTC system: auto-cancel notification after 5 min of pause (system strategy)
    private static final long AUTO_CANCEL_DELAY_MS = TimeUnit.MINUTES.toMillis(5);
    // XTC system: special flag that tells the watch face to capture this notification
    private static final int XTC_MUSIC_FLAG = 0x08000000;
    // XTC ContentProvider URI used to detect if the XTC music flag should be applied
    private static final String XTC_PROVIDER_URI =
            "content://com.xtc.provider/BaseDataProvider/music_notification_type/12";
    // PLAY_STATE values defined by the XTC SDK reverse-engineering
    private static final int PLAY_STATE_PLAYING = 16;
    private static final int PLAY_STATE_PAUSED  = 32;

    public static final String ACTION_PREVIOUS   = "com.qinghe.music163pro.ACTION_PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "com.qinghe.music163pro.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT       = "com.qinghe.music163pro.ACTION_NEXT";
    public static final String ACTION_CLOSE      = "com.qinghe.music163pro.ACTION_CLOSE";

    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private AlarmManager alarmManager;
    private PendingIntent autoCancelPendingIntent;

    // Held instance so AutoCancelReceiver can call cancel()
    static MusicPlaybackService instance;

    private String currentSongName = "";
    private String currentArtist   = "";
    private String currentCoverUrl = "";
    private boolean currentIsPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        createNotificationChannel();
        acquireWakeLock();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PREVIOUS.equals(action)) {
                MusicPlayerManager.getInstance().previous();
                return START_STICKY;
            } else if (ACTION_PLAY_PAUSE.equals(action)) {
                MusicPlayerManager player = MusicPlayerManager.getInstance();
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.resume();
                }
                return START_STICKY;
            } else if (ACTION_NEXT.equals(action)) {
                MusicPlayerManager.getInstance().next();
                return START_STICKY;
            } else if (ACTION_CLOSE.equals(action)) {
                MusicPlayerManager.getInstance().pause();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }

            // Normal start/update: extract song info and play state
            String songName  = intent.getStringExtra("song_name");
            String artist    = intent.getStringExtra("artist");
            String coverUrl  = intent.getStringExtra("cover_url");
            boolean isPlaying = intent.getBooleanExtra("is_playing", false);

            if (songName != null) currentSongName = songName;
            if (artist   != null) currentArtist   = artist;
            if (coverUrl != null) currentCoverUrl = coverUrl;
            currentIsPlaying = isPlaying;
        }

        Notification notification = buildNotification(
                currentSongName, currentArtist, currentCoverUrl, currentIsPlaying);
        startForeground(NOTIFICATION_ID, notification);
        manageAutoCancelAlarm(currentIsPlaying);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAutoCancelAlarm();
        releaseWakeLock();
        instance = null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification building
    // ──────────────────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // XTC system requires channel ID "1024" with IMPORTANCE_MIN
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "notification", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String songName, String artist,
                                            String coverUrl, boolean isPlaying) {
        // Content intent: tap notification → open player
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPi      = createServicePendingIntent(ACTION_PREVIOUS, 1);
        PendingIntent playPausePi = createServicePendingIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPi      = createServicePendingIntent(ACTION_NEXT, 3);
        PendingIntent closePi     = createServicePendingIntent(ACTION_CLOSE, 4);

        String title = (songName != null && !songName.isEmpty()) ? songName : "163音乐";
        String text  = (artist  != null && !artist.isEmpty())   ? artist   : "音乐播放中";

        int playPauseIcon  = isPlaying
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String playPauseLabel = isPlaying ? "暂停" : "播放";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(playPauseIcon)
                .setContentIntent(contentPi)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous, "上一曲", prevPi)
                .addAction(playPauseIcon, playPauseLabel, playPausePi)
                .addAction(android.R.drawable.ic_media_next, "下一曲", nextPi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setStyle(new Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2));
        }

        Notification notification = builder.build();

        // ── XTC watch system protocol ──────────────────────────────────────
        // Inject magic extras required by 小天才 system to sync to lock-screen
        // music card and quick-menu controls.
        Bundle extras = new Bundle();
        extras.putBoolean("IS_MUSIC", true);
        extras.putInt("PLAY_STATE", isPlaying ? PLAY_STATE_PLAYING : PLAY_STATE_PAUSED);
        extras.putString("SONG_NAME",   title);
        extras.putString("SINGER_NAME", text);
        if (!TextUtils.isEmpty(coverUrl)) {
            extras.putString("ALBUM_COVER_URL", coverUrl);
        }
        extras.putParcelable("PLAY_PAUSE_INTENT", playPausePi);
        extras.putParcelable("PREVIOUS_INTENT",   prevPi);
        extras.putParcelable("NEXT_INTENT",        nextPi);
        extras.putParcelable("SONG_NAME_INTENT",   contentPi);

        notification.tickerText = title;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        if (notification.extras == null) {
            notification.extras = extras;
        } else {
            notification.extras.putAll(extras);
        }

        // Apply XTC special flag: needed for the watch face to capture this notification.
        // The flag is always set on API 24+ (Android 7+), which covers all XTC watches.
        // On older APIs it requires a query to the XTC ContentProvider.
        try {
            boolean applyFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
            if (!applyFlag) {
                String type = getContentResolver().getType(Uri.parse(XTC_PROVIDER_URI));
                applyFlag = TextUtils.equals(type, "header");
            }
            if (applyFlag) {
                notification.flags |= XTC_MUSIC_FLAG;
            }
        } catch (Exception ignored) {}
        // ──────────────────────────────────────────────────────────────────

        return notification;
    }

    private PendingIntent createServicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // XTC 5-minute auto-cancel alarm (required by XTC system kill strategy)
    // ──────────────────────────────────────────────────────────────────────────

    private void manageAutoCancelAlarm(boolean isPlaying) {
        if (isPlaying) {
            cancelAutoCancelAlarm();
        } else {
            scheduleAutoCancelAlarm();
        }
    }

    private void scheduleAutoCancelAlarm() {
        cancelAutoCancelAlarm();
        Intent intent = new Intent(this, AutoCancelReceiver.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        autoCancelPendingIntent = PendingIntent.getBroadcast(this, 1024, intent, piFlags);
        long triggerAtMs = System.currentTimeMillis() + AUTO_CANCEL_DELAY_MS;
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, autoCancelPendingIntent);
    }

    private void cancelAutoCancelAlarm() {
        if (autoCancelPendingIntent != null) {
            alarmManager.cancel(autoCancelPendingIntent);
            autoCancelPendingIntent = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WakeLock helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "music163:playback");
            wakeLock.acquire(12 * 60 * 60 * 1000L); // 12 hours max
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AutoCancelReceiver: fired by AlarmManager after 5 min of paused state
    // ──────────────────────────────────────────────────────────────────────────

    public static class AutoCancelReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (instance != null) {
                instance.stopForeground(true);
                instance.stopSelf();
            }
        }
    }
}

