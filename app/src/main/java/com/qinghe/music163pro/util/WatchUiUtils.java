package com.qinghe.music163pro.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Shared UI helpers for watch-sized screens.
 */
public final class WatchUiUtils {

    public static final String SETTINGS_PREFERENCES = "music163_settings";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final float DESIGN_WIDTH_PX = 320f;

    private WatchUiUtils() {
    }

    public static void applyKeepScreenOnPreference(Activity activity) {
        if (activity == null) {
            return;
        }
        SharedPreferences preferences = activity.getSharedPreferences(
                SETTINGS_PREFERENCES, Context.MODE_PRIVATE);
        boolean keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, false);
        if (keepScreenOn) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public static int px(Context context, int baseValue) {
        if (context == null || baseValue == 0) {
            return baseValue;
        }
        DisplayMetrics metrics = context.getResources() != null
                ? context.getResources().getDisplayMetrics() : null;
        int screenWidth = metrics != null ? metrics.widthPixels : 0;
        if (screenWidth <= 0) {
            screenWidth = (int) DESIGN_WIDTH_PX;
        }
        return (int) (baseValue * screenWidth / DESIGN_WIDTH_PX + 0.5f);
    }
}
