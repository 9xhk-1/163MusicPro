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

    /**
     * Compact action menu for watch screens. A standard Material list dialog
     * (title + items + button bar) cannot fit in the ~132dp-tall dialog window
     * on a 160x180dp display, so action menus render as a custom view of
     * stacked text buttons instead. The dialog is shown immediately and
     * returned so callers can dismiss it if needed.
     *
     * @param itemColors optional per-item text colors; a 0 entry falls back to
     *                   the default menu color.
     */
    public static androidx.appcompat.app.AlertDialog showActionDialog(
            Activity activity,
            String title,
            String[] items,
            int[] itemColors,
            final android.content.DialogInterface.OnClickListener listener) {
        android.content.res.Resources resources = activity.getResources();
        float density = resources.getDisplayMetrics().density;
        int padSide = px(activity, 8);
        int padRow = px(activity, 9);

        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        if (title != null && !title.isEmpty()) {
            android.widget.TextView titleView = new android.widget.TextView(activity);
            titleView.setText(title);
            titleView.setTextColor(resources.getColor(R.color.text_secondary));
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            titleView.setSingleLine(true);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setPadding(padSide, px(activity, 6), padSide, px(activity, 2));
            root.addView(titleView, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        android.widget.LinearLayout rows = new android.widget.LinearLayout(activity);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        rows.setPadding(padSide, 0, padSide, px(activity, 4));
        final androidx.appcompat.app.AlertDialog[] holder = new androidx.appcompat.app.AlertDialog[1];
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            com.google.android.material.button.MaterialButton row =
                    new com.google.android.material.button.MaterialButton(activity);
            row.setText(items[i]);
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            row.setTextColor(itemColors != null && itemColors[i] != 0
                    ? itemColors[i] : resources.getColor(R.color.text_primary));
            row.setAllCaps(false);
            row.setGravity(android.view.Gravity.CENTER);
            row.setInsetTop(0);
            row.setInsetBottom(0);
            row.setMinWidth(0);
            row.setMinimumWidth(0);
            row.setMinHeight(0);
            row.setMinimumHeight(0);
            row.setPadding(padSide, padRow, padSide, padRow);
            row.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT));
            row.setOnClickListener(v -> {
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                listener.onClick(holder[0], index);
            });
            rows.addView(row, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        holder[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        holder[0].show();
        return holder[0];
    }
}
