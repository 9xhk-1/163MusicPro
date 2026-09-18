package com.qinghe.music163pro.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;

/**
 * Receives media keys sent by wired and Bluetooth headsets.
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

        String action;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                action = MusicPlaybackService.ACTION_PLAY;
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                action = MusicPlaybackService.ACTION_PAUSE;
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                action = MusicPlaybackService.ACTION_PLAY_PAUSE;
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                action = MusicPlaybackService.ACTION_NEXT;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                action = MusicPlaybackService.ACTION_PREVIOUS;
                break;
            default:
                return;
        }

        Intent serviceIntent = new Intent(context, MusicPlaybackService.class)
                .setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
