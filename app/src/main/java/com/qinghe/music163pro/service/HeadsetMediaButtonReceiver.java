package com.qinghe.music163pro.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyEvent;

/**
 * 有线 / 蓝牙（含 AirPods）耳机媒体键入口。
 *
 * Android 8+ 已经不会可靠地把 ACTION_MEDIA_BUTTON 投递给清单里静态注册的接收器，
 * 所以 MusicPlaybackService 会额外用 MediaSession.setMediaButtonReceiver()
 * 把 PendingIntent 指到本类；两条路径都保留，兼容华为儿童手表 4X (Android 10)
 * 的旧 AVRCP 栈和较新的蓝牙栈。
 *
 * 这里只负责把原始 KeyEvent 的键码转发给常驻服务，不做键码归一、去抖或连击判定：
 * BroadcastReceiver 进程在 onReceive 返回后就可能被系统回收，任何延时逻辑都必须
 * 放在 MusicPlaybackService 里。
 */
public class HeadsetMediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }

        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() > 0) {
            return;
        }

        Intent serviceIntent = new Intent(context, MusicPlaybackService.class)
                .setAction(MusicPlaybackService.ACTION_MEDIA_KEY)
                .putExtra(MusicPlaybackService.EXTRA_KEY_CODE, event.getKeyCode())
                .putExtra(MusicPlaybackService.EXTRA_BLUETOOTH_KEY, isBluetoothAudio(context));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    /** AirPods 的连击兜底只在蓝牙音频路由上启用，避免影响手表本体的按键语义。 */
    private static boolean isBluetoothAudio(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isBluetoothA2dpOn();
        } catch (Exception ignored) {
            return false;
        }
    }
}
