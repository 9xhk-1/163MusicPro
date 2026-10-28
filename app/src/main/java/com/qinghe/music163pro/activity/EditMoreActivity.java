package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.MoreMenuPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit visible entries for the more screen.
 * Entries are split into "已加入" (enabled, removable via top-right x,
 * long-press to drag-reorder) and "未加入" (disabled, tap whole row to add).
 */
public class EditMoreActivity extends BaseWatchActivity {

    private final Map<String, String> itemLabels = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private LinearLayout enabledContainer;
    private LinearLayout disabledContainer;
    private final List<String> enabledKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        itemLabels.put(MoreMenuPreferences.KEY_FAVORITES, "收藏列表");
        itemLabels.put(MoreMenuPreferences.KEY_MY_PLAYLISTS, "我的歌单");
        itemLabels.put(MoreMenuPreferences.KEY_DAILY_RECOMMEND, "每日推荐");
        itemLabels.put(MoreMenuPreferences.KEY_RADAR_PLAYLIST, "雷达歌单");
        itemLabels.put(MoreMenuPreferences.KEY_MUSIC_CLOUD, "音乐云盘");
        itemLabels.put(MoreMenuPreferences.KEY_SEARCH, "搜索");
        itemLabels.put(MoreMenuPreferences.KEY_SONG_RECOGNITION, "听歌识曲");
        itemLabels.put(MoreMenuPreferences.KEY_DOWNLOADS, "下载列表");
        itemLabels.put(MoreMenuPreferences.KEY_RINGTONES, "铃声管理");
        itemLabels.put(MoreMenuPreferences.KEY_TOPLIST, "排行榜");
        itemLabels.put(MoreMenuPreferences.KEY_HISTORY, "历史记录");
        itemLabels.put(MoreMenuPreferences.KEY_PROFILE, "个人中心");
        itemLabels.put(MoreMenuPreferences.KEY_PERSONAL_FM, "私人漫游");
        itemLabels.put(MoreMenuPreferences.KEY_LOGIN, "登录");

        prefs = MoreMenuPreferences.getPrefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(8), px(8), px(8), px(8));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("编辑更多页面");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        root.addView(title);

        enabledContainer = new LinearLayout(this);
        enabledContainer.setOrientation(LinearLayout.VERTICAL);

        disabledContainer = new LinearLayout(this);
        disabledContainer.setOrientation(LinearLayout.VERTICAL);

        root.addView(makeSectionTitle("已加入（长按拖动排序）"));
        root.addView(enabledContainer);
        root.addView(makeSectionTitle("未加入（点击添加）"));
        root.addView(disabledContainer);

        rebuild();
    }

    private View makeSectionTitle(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.colorPrimary));
        tv.setTextSize(12);
        tv.setPadding(0, px(8), 0, px(4));
        return tv;
    }

    private void rebuild() {
        enabledContainer.removeAllViews();
        disabledContainer.removeAllViews();
        enabledKeys.clear();

        List<String> order = MoreMenuPreferences.getOrder(prefs);
        for (String key : order) {
            if (!itemLabels.containsKey(key)) continue;
            if (MoreMenuPreferences.isEnabled(prefs, key)) {
                enabledKeys.add(key);
                enabledContainer.addView(buildEnabledRow(key));
            } else {
                disabledContainer.addView(buildDisabledRow(key));
            }
        }
    }

    private View buildEnabledRow(final String key) {
        MaterialCardView row = new MaterialCardView(this);
        row.setCardBackgroundColor(getResources().getColor(R.color.surface_elevated));
        row.setRadius(dpToPx(16));
        row.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56));
        params.bottomMargin = dpToPx(4);
        row.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText(itemLabels.get(key));
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTextSize(13);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setPadding(dpToPx(12), 0, dpToPx(48), 0);
        label.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(label);

        MaterialButton remove = new MaterialButton(this);
        remove.setIconResource(R.drawable.ic_close);
        remove.setIconTint(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorError)));
        remove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.surface_elevated)));
        remove.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        remove.setIconSize(dpToPx(20));
        remove.setIconPadding(0);
        remove.setInsetTop(0);
        remove.setInsetBottom(0);
        remove.setMinWidth(0);
        remove.setMinimumWidth(0);
        remove.setPadding(0, 0, 0, 0);
        remove.setContentDescription("移除" + itemLabels.get(key));
        remove.setClickable(true);
        remove.setFocusable(true);
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                dpToPx(48), dpToPx(48));
        removeParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        remove.setLayoutParams(removeParams);
        remove.setOnClickListener(v -> {
            MoreMenuPreferences.setEnabled(prefs, key, false);
            rebuild();
        });
        row.addView(remove);

        attachDrag(label, row, key);
        return row;
    }

    private View buildDisabledRow(final String key) {
        // Same card structure as buildEnabledRow: label at the start, an
        // icon-only MaterialButton at the end — the plus mirrors the remove X.
        MaterialCardView row = new MaterialCardView(this);
        row.setCardBackgroundColor(getResources().getColor(R.color.surface_elevated));
        row.setRadius(dpToPx(16));
        row.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56));
        params.bottomMargin = dpToPx(4);
        row.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText(itemLabels.get(key));
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTextSize(13);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setPadding(dpToPx(12), 0, dpToPx(48), 0);
        label.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(label);

        MaterialButton add = new MaterialButton(this);
        add.setIconResource(R.drawable.ic_add_box);
        add.setIconTint(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.colorPrimary)));
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.surface_elevated)));
        add.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        add.setIconSize(dpToPx(20));
        add.setIconPadding(0);
        add.setInsetTop(0);
        add.setInsetBottom(0);
        add.setMinWidth(0);
        add.setMinimumWidth(0);
        add.setPadding(0, 0, 0, 0);
        add.setContentDescription("添加" + itemLabels.get(key));
        add.setClickable(true);
        add.setFocusable(true);
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
                dpToPx(48), dpToPx(48));
        addParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        add.setLayoutParams(addParams);
        add.setOnClickListener(v -> {
            MoreMenuPreferences.setEnabled(prefs, key, true);
            rebuild();
        });
        row.addView(add);

        // Tapping anywhere on the row also adds the entry.
        row.setOnClickListener(v -> {
            MoreMenuPreferences.setEnabled(prefs, key, true);
            rebuild();
        });
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private void attachDrag(final View dragHandle, final View row, final String key) {
        final int touchSlop = px(8);
        final int rowStep = dpToPx(56) + dpToPx(4);
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float fingerOffsetInRow;
            float downRawY;
            int containerScreenY;
            boolean dragging = false;
            int fromIndex = -1;
            int currentTarget = -1;
            final Handler handler = new Handler();
            Runnable longPressRunnable;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawY = e.getRawY();
                        int[] downLoc = new int[2];
                        enabledContainer.getLocationOnScreen(downLoc);
                        containerScreenY = downLoc[1];
                        fingerOffsetInRow = e.getRawY() - containerScreenY - row.getTop();
                        dragging = false;
                        longPressRunnable = () -> {
                            dragging = true;
                            fromIndex = enabledContainer.indexOfChild(row);
                            currentTarget = fromIndex;
                            row.getParent().requestDisallowInterceptTouchEvent(true);
                            row.animate().scaleX(1.03f).scaleY(1.03f)
                                    .setDuration(80).start();
                        };
                        handler.postDelayed(longPressRunnable, 350);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging) {
                            if (Math.abs(e.getRawY() - downRawY) > touchSlop) {
                                handler.removeCallbacks(longPressRunnable);
                            }
                            return true;
                        }
                        float fingerY = e.getRawY() - containerScreenY;
                        float rowTop = fingerY - fingerOffsetInRow;
                        row.setTranslationY(rowTop - row.getTop());
                        int count = enabledContainer.getChildCount();
                        int target = (int) Math.floor((rowTop + row.getHeight() / 2f) / rowStep);
                        target = Math.max(0, Math.min(target, count - 1));
                        if (target != currentTarget) {
                            currentTarget = target;
                            shiftOtherRows(row, fromIndex, target, rowStep);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        if (dragging) {
                            dragging = false;
                            row.getParent().requestDisallowInterceptTouchEvent(false);
                            if (currentTarget >= 0 && currentTarget != fromIndex) {
                                enabledContainer.removeView(row);
                                enabledContainer.addView(row, currentTarget);
                                enabledKeys.remove(key);
                                enabledKeys.add(currentTarget, key);
                            }
                            for (int i = 0; i < enabledContainer.getChildCount(); i++) {
                                View child = enabledContainer.getChildAt(i);
                                child.animate().translationY(0).scaleX(1f).scaleY(1f)
                                        .setDuration(120).start();
                            }
                            persistOrder();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void shiftOtherRows(View draggedRow, int fromIndex, int target, int rowStep) {
        int count = enabledContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = enabledContainer.getChildAt(i);
            if (child == draggedRow) continue;
            float ty = 0;
            if (target > fromIndex) {
                if (i > fromIndex && i <= target) ty = -rowStep;
            } else if (target < fromIndex) {
                if (i >= target && i < fromIndex) ty = rowStep;
            }
            child.setTranslationY(ty);
        }
    }

    private void persistOrder() {
        List<String> order = MoreMenuPreferences.getOrder(prefs);
        List<String> disabled = new ArrayList<>();
        for (String key : order) {
            if (!enabledKeys.contains(key) && itemLabels.containsKey(key)) {
                disabled.add(key);
            }
        }
        List<String> newOrder = new ArrayList<>(enabledKeys);
        newOrder.addAll(disabled);
        MoreMenuPreferences.setOrder(prefs, newOrder);
    }
}
