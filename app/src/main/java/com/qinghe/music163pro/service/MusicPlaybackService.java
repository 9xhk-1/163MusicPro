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
import com.qinghe.music163pro.R;  
import com.qinghe.music163pro.activity.MainActivity;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.model.Song;
import android.widget.RemoteViews;
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

    

// ...

private Notification buildNotification(String songName, String artist, boolean isPlaying) {
    Intent contentIntent = new Intent(this, MainActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    String title = (songName != null && !songName.isEmpty()) ? songName : "163音乐";
    String text = (artist != null && !artist.isEmpty()) ? artist : "音乐播放中";

    // 核心更改：放弃系统的 MediaStyle，强制使用 RemoteViews 进行装配渲染
    RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_music_card);
    customView.setTextViewText(R.id.tv_song_name, title);
    customView.setTextViewText(R.id.tv_artist, text);
    
    customView.setImageViewResource(R.id.btn_play_pause,
            isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

    // 为按钮绑定对应的底层服务动作 (必须使用明确的 Action 给手表系统触发)
    customView.setOnClickPendingIntent(R.id.btn_prev, 
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));
        
    customView.setOnClickPendingIntent(R.id.btn_play_pause, 
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, isPlaying ? PlaybackStateCompat.ACTION_PAUSE : PlaybackStateCompat.ACTION_PLAY));
        
    customView.setOnClickPendingIntent(R.id.btn_next, 
        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

    // 使用较低级的兼容通知结构防止小天才系统去拦截、阉割视图
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setCustomContentView(customView)      // 注入折叠卡片UI
            .setCustomBigContentView(customView)   // 注入展开卡片UI
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)                 // 防止没暂停的时候被划掉
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX) // 小天才需要强行置顶保证卡片高度
            .setOnlyAlertOnce(true);

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
