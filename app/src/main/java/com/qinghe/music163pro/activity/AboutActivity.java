package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;

/**
 * About page - shows app name, version, developer, description, update summary.
 * Adapted for watch DPI with scrollable content.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Build UI programmatically for DPI adaptation
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF212121);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(12), px(10), px(12), px(16));

        // Title
        content.addView(makeText("关于", 0xFFFFFFFF, px(22), true, Gravity.CENTER));

        // App name
        content.addView(makeSpacer(px(10)));
        content.addView(makeText("163音乐Pro", 0xFFFFFFFF, px(20), true, Gravity.CENTER));

        // Version
        content.addView(makeSpacer(px(4)));
        String versionName = "20260402";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        content.addView(makeText("版本: " + versionName, 0xFFCCCCCC, px(16), false, Gravity.CENTER));

        // Developer
        content.addView(makeSpacer(px(4)));
        content.addView(makeText("开发者: Qinghe", 0xFFCCCCCC, px(16), false, Gravity.CENTER));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // Description
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("软件概述", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "适用于小天才手表的网易云音乐播放器。支持在线搜索、播放、下载、收藏、歌词显示、铃声设置等功能。"
                + "支持扫码登录和Cookie登录，可播放VIP音乐。",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260402 update summary (latest)
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260402 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 移除不可用功能入口：听歌识曲、短信登录、密码登录\n"
                + "• 修复歌词页面切歌后歌词不刷新的问题\n"
                + "• 切歌时歌词页自动加载新歌词并更新歌曲名称",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260401 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260401 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增日志系统（API调用/功能操作全记录，写入/sdcard/163Music/app.log）\n"
                + "• 修复VIP到期时间不显示（API字段expireTime）\n"
                + "• 修复短信/密码登录「登录失败:null」（正确读取HTTP错误流）\n"
                + "• 更多页面新增「识别歌曲」功能（听歌识曲 / 哼歌识曲）",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260401 (old entry – right-swipe fix)
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260401（右滑修复版）更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复右滑退出被全局禁用导致所有界面无法右划的问题\n"
                + "• 仅 MainActivity 禁用系统右滑退出，其余所有界面恢复正常右滑退出\n"
                + "• 歌词界面/更多功能界面右滑正确关闭对应面板，主播放界面右滑直接退出应用",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260331-fix1 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260331-fix1 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复返回播放界面时音乐意外自动播放\n"
                + "• 关于页面文字放大适配手表屏幕\n"
                + "• 设置页面移除登录入口（仅保留更多中的登录）",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260331 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260331 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增变速模式设置（音调不变/音调改变）\n"
                + "• 登录功能移至更多 > 登录\n"
                + "• 设置页面重构为平铺列表风格\n"
                + "• 新增开关选项页（屏幕常亮、收藏模式、变速模式）\n"
                + "• 新增关于页面\n"
                + "• 修复铃声管理名称秒数重复显示",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Previous version
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v2.0 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 自定义应用图标\n"
                + "• 修复短信登录\u201C环境不安全\u201D错误\n"
                + "• 修复二维码显示不完整问题\n"
                + "• 修复切歌后无法自动播放问题\n"
                + "• 收藏列表删除重装后自动恢复\n"
                + "• 新增前台服务保活机制\n"
                + "• 登录返回空Cookie时不再覆盖已有Cookie",
                0xFFAAAAAA, px(15), false, Gravity.START));

        scrollView.addView(content);
        setContentView(scrollView);
    }

    private TextView makeText(String text, int color, int sizePx, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(gravity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        return tv;
    }

    private android.view.View makeSpacer(int heightPx) {
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }

    private android.view.View makeDivider() {
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFF424242);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1)));
        return divider;
    }

    /**
     * Convert a value scaled for a 320px-wide watch screen to actual pixels.
     */
    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
