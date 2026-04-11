package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.util.*;

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
        scrollView.setBackgroundColor(0xFF121212);
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
        String versionName = "20260411-2";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        content.addView(makeText("版本: " + versionName, 0xFFCCCCCC, px(16), false, Gravity.CENTER));

        // Developer
        content.addView(makeSpacer(px(4)));
        content.addView(makeText("开发者: Qinghe", 0xFFCCCCCC, px(16), false, Gravity.CENTER));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText("官网: ", 0xFFCCCCCC, px(16), false, Gravity.CENTER));
        TextView linkTv = makeText("https://163.imoow.com", 0xFF5599CC, px(16), false, Gravity.CENTER);
        Linkify.addLinks(linkTv, Linkify.WEB_URLS);
        content.addView(linkTv);

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

        // v20260411-2 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260411-2 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 增加MV功能",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260411 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260411 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复评论时间显示问题\n"
                + "• 修复私人漫游未登录无法使用",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260410-2 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260410-2 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复私人漫游不刷新歌曲的问题\n"
                + "• 修复随机播放时上下键未按照顺序切换的问题",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260410 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260410 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 增加了听歌识曲功能\n"
                + "• 修复版本号错误的问题\n"
                + "• 增加开源信息显示",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260409-fix1 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260409-fix1 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 优化音量显示卡片",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260409 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260409 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 重做音量提示弹窗，采用卡片式显示",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260407 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260407 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增下载路线选择，提升下载速度",
                0xFFAAAAAA, px(15), false, Gravity.START));
        
        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260406 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260406 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 全新 Material Design 2 暗色主题 UI 设计\n"
                + "• 所有 emoji 图标替换为 Material 矢量图标（VectorDrawable）\n"
                + "• 主题色改为紫色 #BB86FC，强调色改为青色 #03DAC6\n"
                + "• 设置页布尔选项改为 Android 官方 SwitchMaterial 开关样式\n"
                + "• 修复单曲循环图标、倍速选中颜色、译文按钮颜色\n"
                + "• 音乐信息栏目标题改为矢量图标+文字\n"
                + "• 我的歌单列表图标化，创建按钮改为主题色\n"
                + "• 历史记录及歌单详情标题居中对齐",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260405-2 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260405-2 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 变速模式升级为三档：音调不变 / 音调改变但速度不变 / 音调改变且速度改变\n"
                + "• 播放器适配三种变速模式，实时切换生效",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260405 update summary 
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260405 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增官网链接跳转",
                0xFFAAAAAA, px(15), false, Gravity.START));
        
        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260404-fix1 update summary 
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260404-fix1 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复首次安装时点击更新报错的问题：更新界面现在会在下载前请求存储权限",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260404 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260404 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增自动更新功能：每天首次打开应用自动检测新版本\n"
                + "• 发现新版本时弹出更新提示页面，支持一键下载安装\n"
                + "• 设置页面新增「检测更新」按钮，可手动检查最新版本",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260403-2 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260403-2 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 修复歌单加入播放列表只有200首的限制，现在有多少首可播放多少首\n"
                + "• 新增歌单系统：搜索界面支持搜索歌单（单曲/歌单标签切换）\n"
                + "• 新增歌单系统：收藏列表支持查看收藏歌单（单曲/歌单标签切换）\n"
                + "• 歌单搜索结果显示歌曲数量，支持无限滚动加载\n"
                + "• 歌单详情页面支持长按标题收藏/取消收藏歌单\n"
                + "• 单曲和歌单共用本地/云端模式开关\n"
                + "• 本地歌单保存到 /sdcard/163Music/playlists.json",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260403 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260403 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 评论界面字体全面加大，适配手表屏幕阅读\n"
                + "• 新增长按删除评论功能（带确认弹窗）\n"
                + "• 修复发送评论后不自动刷新的问题\n"
                + "• 音乐信息页面全面重写：显示歌曲详情（时长、专辑、音质、发布时间等所有字段）\n"
                + "• 音乐信息页面展示歌曲百科所有板块内容\n"
                + "• 新增歌手百科请求日志记录\n"
                + "• 修复歌词界面熄屏/回桌面后歌词不再滚动的问题",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260402-fix1 update summary
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("v20260402-fix1 更新内容", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText(
                "• 新增音乐信息功能：查看歌曲百科和歌手简介\n"
                + "• 新增评论功能：查看评论、发送评论、点赞、回复、查看楼层子评论\n"
                + "• 评论支持排序切换（推荐/最热/最新）\n"
                + "• 修复歌词界面点击右上角有概率进入更多选项的问题\n"
                + "• 修复歌词界面点击右下角有概率触发音量调节的问题\n"
                + "• 歌词界面新增翻译开关，支持中英双语歌词显示\n"
                + "• 翻译偏好持久化，开启/关闭后自动应用到后续歌曲\n"
                + "• 下载歌曲时同步下载翻译歌词",
                0xFFAAAAAA, px(15), false, Gravity.START));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // v20260402 update summary
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
        divider.setBackgroundColor(0xFF2D2D2D);
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
