package com.qinghe.music163pro.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.widget.RemoteViews;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.activity.MainActivity;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.concurrent.TimeUnit;

/**
 * Foreground service that keeps the music player alive during background
 * playback. It maintains two separate notifications:
 *
 * 1. Keep-alive foreground notification (ID=1, channel "keepalive") –
 *    a minimal notification required by Android to allow foreground service.
 *
 * 2. XTC watch music card notification (ID=258, channel "1024") –
 *    sent via NotificationManager.notify() (NOT startForeground) with the
 *    magic extras and flags required by 小天才 system to show the lock-screen
 *    music card and quick-menu controls. Uses RemoteViews as the XTC SDK does.
 *
 * Separating them matches the real NetEase CloudMusic implementation discovered
 * through reverse engineering of the XTC SDK.
 */
public class MusicPlaybackService extends Service {

    // ── Foreground keep-alive channel (minimal, not user-visible) ────────────
    private static final String KEEPALIVE_CHANNEL_ID = "keepalive";
    private static final int    FOREGROUND_ID         = 1;

    // ── XTC watch music card channel ─────────────────────────────────────────
    private static final String XTC_CHANNEL_ID        = "1024";
    private static final int    XTC_NOTIFICATION_ID   = 258;

    // XTC system: special flag that tells the watch face to capture this notification
    private static final int    XTC_MUSIC_FLAG        = 0x08000000;
    // XTC ContentProvider URI used to detect if the XTC music flag should be applied
    private static final String XTC_PROVIDER_URI      =
            "content://com.xtc.provider/BaseDataProvider/music_notification_type/12";
    // PLAY_STATE values defined by the XTC SDK reverse-engineering
    private static final int    PLAY_STATE_PLAYING    = 16;
    private static final int    PLAY_STATE_PAUSED     = 32;
    // XTC system: auto-cancel the music card after 5 min of paused state
    private static final long   AUTO_CANCEL_DELAY_MS  = TimeUnit.MINUTES.toMillis(5);

    public static final String ACTION_PREVIOUS    = "com.qinghe.music163pro.ACTION_PREVIOUS";
    public static final String ACTION_PLAY_PAUSE  = "com.qinghe.music163pro.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT        = "com.qinghe.music163pro.ACTION_NEXT";
    public static final String ACTION_CLOSE       = "com.qinghe.music163pro.ACTION_CLOSE";
    // Internal: fired by AlarmManager after 5 min of paused state
    private static final String ACTION_AUTO_CANCEL = "com.qinghe.music163pro.ACTION_AUTO_CANCEL";

    private PowerManager.WakeLock wakeLock;
    private NotificationManager   notificationManager;
    private AlarmManager          alarmManager;
    private PendingIntent         autoCancelPi;

    private String  currentSongName  = "";
    private String  currentArtist    = "";
    private String  currentCoverUrl  = "";
    private boolean currentIsPlaying = false;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        alarmManager        = (AlarmManager) getSystemService(ALARM_SERVICE);
        createChannels();
        acquireWakeLock();
        // Start foreground with a minimal keep-alive notification (required by Android)
        startForeground(FOREGROUND_ID, buildKeepAliveNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PREVIOUS.equals(action)) {
                MusicPlayerManager.getInstance().previous();
                return START_STICKY;
            }
            if (ACTION_PLAY_PAUSE.equals(action)) {
                MusicPlayerManager p = MusicPlayerManager.getInstance();
                if (p.isPlaying()) p.pause(); else p.resume();
                return START_STICKY;
            }
            if (ACTION_NEXT.equals(action)) {
                MusicPlayerManager.getInstance().next();
                return START_STICKY;
            }
            if (ACTION_CLOSE.equals(action)) {
                MusicPlayerManager.getInstance().pause();
                cancelXtcCard();
                return START_STICKY;
            }
            if (ACTION_AUTO_CANCEL.equals(action)) {
                // Only cancel the XTC music card; the foreground service stays alive
                cancelXtcCard();
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

        // Post the XTC music card notification separately (not as foreground notification)
        notificationManager.notify(XTC_NOTIFICATION_ID,
                buildXtcCardNotification(currentSongName, currentArtist,
                        currentCoverUrl, currentIsPlaying));

        if (currentIsPlaying) {
            cancelAutoCancelAlarm();
        } else {
            scheduleAutoCancelAlarm();
        }

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
    }

    // ── Channel creation ──────────────────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Keep-alive channel: invisible to the user
            NotificationChannel keepalive = new NotificationChannel(
                    KEEPALIVE_CHANNEL_ID, "keepalive", NotificationManager.IMPORTANCE_MIN);
            keepalive.setShowBadge(false);
            notificationManager.createNotificationChannel(keepalive);

            // XTC channel: required to be "1024" / IMPORTANCE_MIN
            NotificationChannel xtc = new NotificationChannel(
                    XTC_CHANNEL_ID, "notification", NotificationManager.IMPORTANCE_MIN);
            xtc.setShowBadge(false);
            notificationManager.createNotificationChannel(xtc);
        }
    }

    // ── Keep-alive foreground notification (minimal) ──────────────────────────

    private Notification buildKeepAliveNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, KEEPALIVE_CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("163音乐")
                .setOngoing(true)
                .setShowWhen(false);
        return b.build();
    }

    // ── XTC watch music card notification ────────────────────────────────────

    private Notification buildXtcCardNotification(String songName, String artist,
                                                   String coverUrl, boolean isPlaying) {
        String title = TextUtils.isEmpty(songName) ? "163音乐"  : songName;
        String text  = TextUtils.isEmpty(artist)   ? "音乐播放中" : artist;

        // PendingIntents for watch controls
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi   = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevPi      = buildServicePi(ACTION_PREVIOUS, 1);
        PendingIntent playPausePi = buildServicePi(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPi      = buildServicePi(ACTION_NEXT, 3);

        // RemoteViews (matches XTC SDK reverse-engineered layout contract)
        RemoteViews rv = new RemoteViews(getPackageName(), R.layout.notification_remote_view);
        rv.setTextViewText(R.id.tv_song_name,   title);
        rv.setTextViewText(R.id.tv_singer_name, text);
        rv.setImageViewResource(R.id.iv_play_pause,
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        rv.setOnClickPendingIntent(R.id.tv_song_name, contentPi);
        rv.setOnClickPendingIntent(R.id.iv_previous,  prevPi);
        rv.setOnClickPendingIntent(R.id.iv_play_pause, playPausePi);
        rv.setOnClickPendingIntent(R.id.iv_next,       nextPi);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, XTC_CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContent(rv)
                .setContentIntent(contentPi)
                .setSmallIcon(R.drawable.ic_music_note)
                .setAutoCancel(false);

        Notification n = b.build();

        // ── XTC watch system protocol extras ──────────────────────────────────
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

        n.tickerText = title;
        n.flags = Notification.FLAG_ONGOING_EVENT;
        n.extras.putAll(extras);

        // Apply XTC system capture flag (always needed on API 24+ / Android 7+)
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
        // ──────────────────────────────────────────────────────────────────────

        return n;
    }

    private PendingIntent buildServicePi(String action, int reqCode) {
        Intent i = new Intent(this, MusicPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ── XTC card helpers ─────────────────────────────────────────────────────

    private void cancelXtcCard() {
        cancelAutoCancelAlarm();
        notificationManager.cancel(XTC_NOTIFICATION_ID);
    }

    // ── 5-minute auto-cancel alarm ────────────────────────────────────────────

    private void scheduleAutoCancelAlarm() {
        cancelAutoCancelAlarm();
        // Delivered back to the service itself; no BroadcastReceiver needed
        autoCancelPi = buildServicePi(ACTION_AUTO_CANCEL, 4);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + AUTO_CANCEL_DELAY_MS, autoCancelPi);
    }

    private void cancelAutoCancelAlarm() {
        if (autoCancelPi != null) {
            alarmManager.cancel(autoCancelPi);
            autoCancelPi = null;
        }
    }

    // ── WakeLock ─────────────────────────────────────────────────────────────

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
}


