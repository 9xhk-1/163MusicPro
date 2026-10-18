package com.qinghe.music163pro.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.qinghe.music163pro.activity.MainActivity;

/**
 * 应用内返回手势 (设置项 in_app_back_gesture, 默认关).
 *
 * 开启后, 除 MainActivity 外的每个 Activity 检测"从左往右"的横滑:
 * 起点在屏幕左半边、横向位移 >= 屏宽 1/4、且 |dx| >= 2|dy|,
 * 识别成功即执行 onBackPressed() 返回上一个 Activity.
 *
 * 实现方式: Application.ActivityLifecycleCallbacks 在每个 Activity
 * resume 时包装 Window.Callback — 该入口在所有子 View 之前观察触摸流,
 * 因此列表/进度条等可滑动控件上的手势同样能被识别 (仅靠判定条件区分,
 * 不依赖事件是否被子 View 消费). 识别成功时给子 View 补发 ACTION_CANCEL
 * 并吞掉 UP, 避免残留按下状态或误触发点击.
 */
public final class BackGesture implements Application.ActivityLifecycleCallbacks {

    private static final String PREFS_NAME = "music163_settings";
    /** 设置项: 是否开启应用内返回手势 (默认关). */
    public static final String KEY_ENABLED = "in_app_back_gesture";

    private static final BackGesture INSTANCE = new BackGesture();

    private BackGesture() {
    }

    /** 注册生命周期监听 (MusicApp.onCreate 调用). */
    public static void install(Application app) {
        if (app == null) return;
        app.registerActivityLifecycleCallbacks(INSTANCE);
    }

    /** 当前开关状态 (默认关). */
    public static boolean isEnabled(Context context) {
        return context != null && context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity == null || activity instanceof MainActivity) {
            return; // 主界面不启用
        }
        Window window = activity.getWindow();
        Window.Callback origin = window.getCallback();
        if (origin == null || origin instanceof GestureCallback) {
            return;
        }
        window.setCallback(new GestureCallback(activity, origin));
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    /**
     * Window.Callback 包装: 全量转发, 仅在 dispatchTouchEvent 里叠加
     * 左→右滑动手势判定.
     */
    private static final class GestureCallback implements Window.Callback {

        private final Activity activity;
        private final Window.Callback origin;

        private float startX;
        private float startY;
        private boolean tracking;

        GestureCallback(Activity activity, Window.Callback origin) {
            this.activity = activity;
            this.origin = origin;
        }

        private boolean matches(float endX, float endY, int width) {
            float dx = endX - startX;
            float dy = endY - startY;
            // 从左往右: 起点在左半屏, 位移足够大且明显横向
            return startX < width / 2f
                    && dx >= width / 4f
                    && Math.abs(dx) >= 2f * Math.abs(dy);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (!isEnabled(activity) || activity.isFinishing()) {
                return origin.dispatchTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    tracking = true;
                    break;
                case MotionEvent.ACTION_UP:
                    if (tracking) {
                        int width = activity.getWindow().getDecorView().getWidth();
                        if (width <= 0) {
                            width = activity.getResources().getDisplayMetrics().widthPixels;
                        }
                        if (matches(event.getX(), event.getY(), width)) {
                            // 先给子 View 补发 CANCEL, 清掉按下/拖动状态
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            origin.dispatchTouchEvent(cancel);
                            cancel.recycle();
                            tracking = false;
                            activity.onBackPressed();
                            return true; // 吞掉 UP, 不触发子 View 的 click
                        }
                    }
                    tracking = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    break;
                default:
                    break;
            }
            return origin.dispatchTouchEvent(event);
        }

        // 以下为全量转发 (未加 @Override 以兼容接口版本差异)

        public boolean dispatchKeyEvent(KeyEvent event) {
            return origin.dispatchKeyEvent(event);
        }

        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return origin.dispatchKeyShortcutEvent(event);
        }

        public boolean dispatchTrackballEvent(MotionEvent event) {
            return origin.dispatchTrackballEvent(event);
        }

        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return origin.dispatchGenericMotionEvent(event);
        }

        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return origin.dispatchPopulateAccessibilityEvent(event);
        }

        public View onCreatePanelView(int featureId) {
            return origin.onCreatePanelView(featureId);
        }

        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return origin.onCreatePanelMenu(featureId, menu);
        }

        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return origin.onPreparePanel(featureId, view, menu);
        }

        public boolean onMenuOpened(int featureId, Menu menu) {
            return origin.onMenuOpened(featureId, menu);
        }

        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return origin.onMenuItemSelected(featureId, item);
        }

        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            origin.onWindowAttributesChanged(attrs);
        }

        public void onContentChanged() {
            origin.onContentChanged();
        }

        public void onWindowFocusChanged(boolean hasFocus) {
            origin.onWindowFocusChanged(hasFocus);
        }

        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            return origin.onWindowStartingActionMode(callback);
        }

        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
            return origin.onWindowStartingActionMode(callback, type);
        }

        public void onAttachedToWindow() {
            origin.onAttachedToWindow();
        }

        public void onDetachedFromWindow() {
            origin.onDetachedFromWindow();
        }

        public void onPanelClosed(int featureId, Menu menu) {
            origin.onPanelClosed(featureId, menu);
        }

        public boolean onSearchRequested() {
            return origin.onSearchRequested();
        }

        public boolean onSearchRequested(SearchEvent event) {
            return origin.onSearchRequested(event);
        }

        public void onActionModeStarted(ActionMode mode) {
            origin.onActionModeStarted(mode);
        }

        public void onActionModeFinished(ActionMode mode) {
            origin.onActionModeFinished(mode);
        }
    }
}
