package com.qinghe.music163pro.util;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Shared confirm dialog builder adapted to the watch UI.
 */
public final class WatchConfirmDialog {

    private static final int DEFAULT_OVERLAY_COLOR = 0xCC000000;
    private static final int DEFAULT_TITLE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_MESSAGE_TEXT_COLOR = 0xB3FFFFFF;
    private static final int DEFAULT_CANCEL_BUTTON_COLOR = 0xFF49454F;
    private static final String OVERLAY_TAG = "watch_confirm_dialog_overlay";

    private WatchConfirmDialog() {
    }

    public static void show(Activity activity, String title, String message,
                            Runnable onConfirm, Options options) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        FrameLayout rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        Options resolvedOptions = options != null ? options : Options.defaultOptions();
        dismissExisting(rootView);

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setTag(OVERLAY_TAG);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(DEFAULT_OVERLAY_COLOR);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) rootView.removeView(overlay);
            return true;
        });

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(resolvedOptions.dialogBackgroundColor);
        card.setRadius(WatchUiUtils.px(activity, 20));
        card.setCardElevation(0);
        LinearLayout dialog = new LinearLayout(activity);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(
                WatchUiUtils.px(activity, 16),
                WatchUiUtils.px(activity, 12),
                WatchUiUtils.px(activity, 16),
                WatchUiUtils.px(activity, 12));
        FrameLayout.LayoutParams dialogParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        dialogParams.gravity = Gravity.CENTER;
        dialogParams.leftMargin = WatchUiUtils.px(activity, 16);
        dialogParams.rightMargin = WatchUiUtils.px(activity, 16);
        card.setLayoutParams(dialogParams);

        dialog.addView(createTitleView(activity, title));
        int contentHeight = rootView.getHeight() > 0 ? rootView.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;
        final int maxMessageHeight = Math.max(dp(activity, 32), contentHeight - dp(activity, 120));
        ScrollView messageScroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int limit = View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED
                        ? maxMessageHeight
                        : Math.min(maxMessageHeight, View.MeasureSpec.getSize(heightMeasureSpec));
                super.onMeasure(widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
            }
        };
        messageScroll.addView(createMessageView(activity, message));
        dialog.addView(messageScroll);
        dialog.addView(createButtonRow(activity, rootView, overlay, onConfirm, resolvedOptions));

        card.addView(dialog);
        overlay.addView(card);
        if (resolvedOptions.dismissOnOutsideTap) {
            overlay.setOnClickListener(v -> rootView.removeView(overlay));
        } else {
            overlay.setOnClickListener(v -> {
                // Consume outside taps without dismissing the dialog.
            });
        }
        card.setOnClickListener(v -> {
            // Consume dialog taps so they do not propagate to the overlay.
        });
        rootView.addView(overlay);
        overlay.requestFocus();
    }

    private static TextView createTitleView(Activity activity, String title) {
        TextView titleView = new TextView(activity);
        titleView.setText(title != null ? title : "");
        titleView.setTextColor(DEFAULT_TITLE_TEXT_COLOR);
        titleView.setTextSize(15);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, WatchUiUtils.px(activity, 6));
        return titleView;
    }

    private static TextView createMessageView(Activity activity, String message) {
        TextView messageView = new TextView(activity);
        messageView.setText(message != null ? message : "");
        messageView.setTextColor(DEFAULT_MESSAGE_TEXT_COLOR);
        messageView.setTextSize(13);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(0, 0, 0, WatchUiUtils.px(activity, 12));
        return messageView;
    }

    private static LinearLayout createButtonRow(Activity activity, FrameLayout rootView,
                                                FrameLayout overlay, Runnable onConfirm,
                                                Options options) {
        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.addView(createButton(activity, rootView, overlay, "取消",
                DEFAULT_CANCEL_BUTTON_COLOR, false, onConfirm));
        buttonRow.addView(createButton(activity, rootView, overlay, "确定",
                options.confirmButtonColor, true, onConfirm));
        return buttonRow;
    }

    private static MaterialButton createButton(Activity activity, FrameLayout rootView,
                                         FrameLayout overlay, String label,
                                         int backgroundColor, boolean isConfirm,
                                         Runnable onConfirm) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setTextColor(ColorUtils.calculateLuminance(backgroundColor) > 0.4
                ? 0xFF1D1B20 : DEFAULT_TITLE_TEXT_COLOR);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(activity, 48));
        button.setMinimumHeight(dp(activity, 48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(WatchUiUtils.px(activity, 20));
        button.setGravity(Gravity.CENTER);
        button.setPadding(
                WatchUiUtils.px(activity, 12),
                WatchUiUtils.px(activity, 8),
                WatchUiUtils.px(activity, 12),
                WatchUiUtils.px(activity, 8));
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (isConfirm) {
            params.leftMargin = WatchUiUtils.px(activity, 4);
            button.setOnClickListener(v -> {
                rootView.removeView(overlay);
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });
        } else {
            params.rightMargin = WatchUiUtils.px(activity, 4);
            button.setOnClickListener(v -> rootView.removeView(overlay));
        }
        button.setLayoutParams(params);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static void dismissExisting(FrameLayout rootView) {
        android.view.View existingOverlay = rootView.findViewWithTag(OVERLAY_TAG);
        if (existingOverlay != null) {
            rootView.removeView(existingOverlay);
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static final class Options {
        private final int dialogBackgroundColor;
        private final int confirmButtonColor;
        private final boolean dismissOnOutsideTap;

        public Options(int dialogBackgroundColor, int confirmButtonColor,
                       boolean dismissOnOutsideTap) {
            this.dialogBackgroundColor = dialogBackgroundColor;
            this.confirmButtonColor = confirmButtonColor;
            this.dismissOnOutsideTap = dismissOnOutsideTap;
        }

        public static Options defaultOptions() {
            return new Options(0xFF2B2930, 0xFFD0BCFF, true);
        }
    }
}
