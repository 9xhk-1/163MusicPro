package com.qinghe.music163pro;

import android.app.Application;
import android.content.SharedPreferences;

import com.qinghe.music163pro.api.NeteaseWatchApi;
import com.qinghe.music163pro.util.MusicLog;

/**
 * App-wide singletons that must be ready before ANY activity/service issues
 * a network request. Currently just the watch-gateway deviceId: generated
 * once per install (random) and persisted forever so the gateway sees a
 * stable, unique device identity — /eapi/login/anon/device rejects requests
 * without it (400 "more device info is needed").
 */
public class MusicApp extends Application {

    private static final String TAG = "MusicApp";

    public static final String PREFS_NAME = "music163_settings";
    public static final String KEY_WATCH_DEVICE_ID = "watch_device_id";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String id = prefs.getString(KEY_WATCH_DEVICE_ID, "");
        if (id == null || id.trim().isEmpty()) {
            id = NeteaseWatchApi.generateDeviceId();
            prefs.edit().putString(KEY_WATCH_DEVICE_ID, id).apply();
            MusicLog.i(TAG, "已生成并永久保存 watch deviceId");
        }
        NeteaseWatchApi.setDeviceId(id);
        // 应用内返回手势 (in_app_back_gesture, 默认关) — 生命周期回调
        // 自动挂到除 MainActivity 外的每个 Activity
        com.qinghe.music163pro.util.BackGesture.install(this);
    }
}
