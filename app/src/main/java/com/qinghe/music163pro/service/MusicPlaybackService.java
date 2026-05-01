package com.qinghe.music163pro.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.qinghe.music163pro.activity.MainActivity;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.model.Song;

import java.util.List;

/**
 * Foreground service to keep the music player alive during background
 * playback and when the screen is off. Shows a persistent notification
 * with media controls (previous, play/pause, next).
 * 
 * [已改造]: 接入 MediaBrowserServiceCompat 以支持手表按键、语音与媒体栏控制。
 */
public class MusicPlaybackService extends MediaBrowserServiceCompat {

    private static final String CHANNEL_ID = "music163_playback";
    private static final int NOTIFICATION_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;

    private String currentSongName = "";
    private String currentArtist = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();

        // 1. 初始化标准设备互通的 MediaSession
        mMediaSession = new MediaSessionCompat(this, "Music163ProSession");
        
        // 挂载允许被外部设备的媒体键（手表的音量键/按钮）指挥的标志
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        mMediaSession.setPlaybackState(mStateBuilder.build());

        // 2. 这里是关键：接收小天才语音、外接硬件的广播命令，传导给实际的 PlayerManager
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                MusicPlayerManager.getInstance().resume();
                updatePlaybackState(true);
            }

            @Override
            public void onPause() {
                MusicPlayerManager.getInstance().pause();
                updatePlaybackState(false);
            }

            @Override
            public void onSkipToNext() {
                MusicPlayerManager.getInstance().next();
            }

            @Override
            public void onSkipToPrevious() {
                MusicPlayerManager.getInstance().previous();
            }
        });

        // 3. 关联服务与 Session
        setSessionToken(mMediaSession.getSessionToken());
        mMediaSession.setActive(true);

        // 4. 初始化一次占位服务，防止抛出 ANR
        startForeground(NOTIFICATION_ID, buildNotification(currentSongName, currentArtist, false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                // 如果是媒体按钮按下，交给原生的 MediaButtonReceiver 处理转换 (手表系统/耳机的标准广播)
                MediaButtonReceiver.handleIntent(mMediaSession, intent);
                return START_STICKY;
            }
        }

        // 常规通过 intent 开启的数据同步 (保持对旧版代码的兼容)
        if (intent != null) {
            String songName = intent.getStringExtra("song_name");
            String artist = intent.getStringExtra("artist");
            boolean isPlaying = intent.getBooleanExtra("is_playing", false);

            if (songName != null) currentSongName = songName;
            if (artist != null) currentArtist = artist;

            updateMetadata(currentSongName, currentArtist);
            updatePlaybackState(isPlaying);
        }

        return START_STICKY;
    }

    private void updateMetadata(String title, String artist) {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .build();
        mMediaSession.setMetadata(metadata);
    }

    private void updatePlaybackState(boolean isPlaying) {
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mMediaSession.setPlaybackState(mStateBuilder.build());

        // 刷新前台通知栏视图
        startForeground(NOTIFICATION_ID, buildNotification(currentSongName, currentArtist, isPlaying));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("音乐播放中");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String songName, String artist, boolean isPlaying) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 系统级的 MediaStyle 渲染器
        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mMediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);

        String title = (songName != null && !songName.isEmpty()) ? songName : "163音乐";
        String text = (artist != null && !artist.isEmpty()) ? artist : "音乐播放中";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(mediaStyle)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play)
                .setContentIntent(contentPendingIntent)
                .setOngoing(isPlaying) // 播放时不允许划掉，暂停时允许滑动关闭
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true);

        // 使用 MediaButtonReceiver 为 Notification 绑定标准的 MediaAction Intent（而不是写死 action）
        builder.addAction(android.R.drawable.ic_media_previous, "上一曲",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE));
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "播放",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY));
        }

        builder.addAction(android.R.drawable.ic_media_next, "下一曲",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

        return builder.build();
    }

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMediaSession.setActive(false);
        mMediaSession.release();
        releaseWakeLock();
    }

    // MediaBrowserServiceCompat 必须实现的连接方法
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("root_163pro", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }
}
