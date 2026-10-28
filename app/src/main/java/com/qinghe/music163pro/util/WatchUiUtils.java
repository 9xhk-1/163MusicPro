package com.qinghe.music163pro.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.qinghe.music163pro.R;

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
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        if (screenWidth <= 0) {
            screenWidth = (int) DESIGN_WIDTH_PX;
        }
        return (int) (baseValue * screenWidth / DESIGN_WIDTH_PX + 0.5f);
    }

    /**
     * Material 3 filled text field sized for watch screens. The floating hint
     * label is disabled so the hint stays inside the box, keeping the control
     * compact enough for the 160x180dp display.
     */
    public static com.google.android.material.textfield.TextInputLayout createWatchTextInput(
            Context context, String hint) {
        android.content.res.Resources resources = context.getResources();
        float density = resources.getDisplayMetrics().density;
        com.google.android.material.textfield.TextInputLayout layout =
                new com.google.android.material.textfield.TextInputLayout(context);
        layout.setBoxBackgroundMode(
                com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_FILLED);
        layout.setBoxBackgroundColorResource(R.color.surface_elevated);
        float radius = 10 * density;
        layout.setBoxCornerRadii(radius, radius, radius, radius);
        layout.setBoxStrokeWidth(0);
        layout.setBoxStrokeWidthFocused(0);
        layout.setHintEnabled(false);
        layout.setPadding(0, 0, 0, 0);

        com.google.android.material.textfield.TextInputEditText edit =
                new com.google.android.material.textfield.TextInputEditText(context);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setTextColor(resources.getColor(R.color.text_primary));
        edit.setHintTextColor(resources.getColor(R.color.text_secondary));
        edit.setTextSize(13);
        edit.setBackground(null);
        edit.setPadding((int) (10 * density), (int) (4 * density),
                (int) (10 * density), (int) (4 * density));
        layout.addView(edit, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }
}
