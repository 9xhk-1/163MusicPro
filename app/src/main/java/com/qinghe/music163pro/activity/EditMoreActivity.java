package com.qinghe.music163pro.activity;

import android.animation.LayoutTransition;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
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
        scroll.setBackgroundColor(0xFF121212);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(8), px(8), px(8), px(8));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("编辑更多页面");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, px(8));
        root.addView(title);

        enabledContainer = new LinearLayout(this);
        enabledContainer.setOrientation(LinearLayout.VERTICAL);
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        lt.enableTransitionType(LayoutTransition.DISAPPEARING);
        lt.enableTransitionType(LayoutTransition.APPEARING);
        enabledContainer.setLayoutTransition(lt);

        disabledContainer = new LinearLayout(this);
        disabledContainer.setOrientation(LinearLayout.VERTICAL);
        disabledContainer.setLayoutTransition(new LayoutTransition());

        root.addView(makeSectionTitle("已加入（长按拖动排序）"));
        root.addView(enabledContainer);
        root.addView(makeSectionTitle("未加入（点击添加）"));
        root.addView(disabledContainer);

        rebuild();
    }

    private View makeSectionTitle(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFBB86FC);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
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
        FrameLayout row = new FrameLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E1E);
        bg.setCornerRadius(px(6));
        row.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52));
        params.bottomMargin = px(4);
        row.setLayoutParams(params);

        TextView label = new TextView(this);
        label.setText(itemLabels.get(key));
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setPadding(px(12), 0, px(32), 0);
        label.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(label);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(0xFFCF6679);
        remove.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(px(8), px(4), px(8), px(4));
        remove.setClickable(true);
        remove.setFocusable(true);
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                px(36), px(30));
        removeParams.gravity = Gravity.TOP | Gravity.END;
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(12), 0, px(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1A1A);
        bg.setCornerRadius(px(6));
        row.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52));
        params.bottomMargin = px(4);
        row.setLayoutParams(params);
        row.setClickable(true);
        row.setFocusable(true);

        TextView label = new TextView(this);
        label.setText(itemLabels.get(key));
        label.setTextColor(0x80FFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView check = new TextView(this);
        check.setText("＋");
        check.setTextColor(0xFFBB86FC);
        check.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
        check.setGravity(Gravity.CENTER);
        check.setPadding(px(6), 0, 0, 0);
        row.addView(check);

        row.setOnClickListener(v -> {
            MoreMenuPreferences.setEnabled(prefs, key, true);
            rebuild();
        });
        return row;
    }

    private void attachDrag(final View dragHandle, final View row, final String key) {
        final int touchSlop = px(8);
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float startRawY;
            boolean dragging = false;
            final Handler handler = new Handler();
            Runnable longPressRunnable;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = e.getRawY();
                        dragging = false;
                        longPressRunnable = () -> {
                            dragging = true;
                            row.animate().scaleX(1.03f).scaleY(1.03f)
                                    .setDuration(80).start();
                        };
                        handler.postDelayed(longPressRunnable, 350);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging) {
                            if (Math.abs(e.getRawY() - startRawY) > touchSlop) {
                                handler.removeCallbacks(longPressRunnable);
                            }
                            return true;
                        }
                        float dy = e.getRawY() - startRawY;
                        row.setTranslationY(dy);
                        int target = resolveTarget(row, dy);
                        int current = enabledContainer.indexOfChild(row);
                        if (target >= 0 && target != current) {
                            enabledContainer.removeView(row);
                            enabledContainer.addView(row, target);
                            enabledKeys.remove(key);
                            enabledKeys.add(target, key);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        if (dragging) {
                            dragging = false;
                            row.animate().translationY(0).scaleX(1f).scaleY(1f)
                                    .setDuration(120).start();
                            persistOrder();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private int resolveTarget(View row, float dy) {
        int count = enabledContainer.getChildCount();
        int current = enabledContainer.indexOfChild(row);
        int newCenter = row.getTop() + (int) row.getTranslationY() + row.getHeight() / 2;
        int target = current;
        for (int i = 0; i < count; i++) {
            View child = enabledContainer.getChildAt(i);
            if (child == row) continue;
            int childCenter = child.getTop() + child.getHeight() / 2;
            if (i < target && newCenter < childCenter) {
                target = i;
                break;
            }
            if (i > target && newCenter > childCenter) {
                target = i;
            }
        }
        return target;
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
