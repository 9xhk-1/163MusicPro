package com.qinghe.music163pro.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.util.Linkify;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.ImoowApiHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * About page - shows app name, version, developer, description, update summary.
 * Adapted for watch DPI with scrollable content.
 */
public class AboutActivity extends AppCompatActivity {

    private static final String DEFAULT_OVERVIEW =
            "适用于小天才手表的网易云音乐播放器。支持在线搜索、播放、下载、收藏、歌词显示、铃声设置等功能。"
                    + "支持扫码登录和Cookie登录，可播放VIP音乐。";

    private TextView overviewTextView;
    private LinearLayout updateContainer;

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
        String versionName = "20260429";
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
        overviewTextView = makeText(DEFAULT_OVERVIEW, 0xFFAAAAAA, px(15), false, Gravity.START);
        content.addView(overviewTextView);

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        // Feedback section
        content.addView(makeSpacer(px(8)));
        content.addView(makeText("问题反馈", 0xFFFFFFFF, px(18), true, Gravity.START));
        content.addView(makeSpacer(px(4)));
        content.addView(makeText("遇到问题或有建议，欢迎通过以下方式反馈：",
                0xFFAAAAAA, px(14), false, Gravity.START));
        content.addView(makeSpacer(px(6)));
        // Email row
        content.addView(makeIconTextRow(R.drawable.ic_email, "邮箱：mail@9x.hk",
                0xFF5599CC, px(14), true));
        content.addView(makeSpacer(px(4)));
        // QQ row
        content.addView(makeIconTextRow(R.drawable.ic_chat_bubble, "QQ：3686072365",
                0xFF5599CC, px(14), false));

        // Divider
        content.addView(makeSpacer(px(8)));
        content.addView(makeDivider());

        updateContainer = new LinearLayout(this);
        updateContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(updateContainer);
        renderAboutContent(DEFAULT_OVERVIEW, getDefaultUpdateLogs());

        scrollView.addView(content);
        setContentView(scrollView);
        loadAboutContent();
    }

    private void loadAboutContent() {
        ImoowApiHelper.fetchAboutContent(new ImoowApiHelper.AboutCallback() {
            @Override
            public void onResult(ImoowApiHelper.AboutContent aboutContent) {
                String overview = aboutContent.getOverview();
                List<ImoowApiHelper.UpdateLogItem> updateItems = aboutContent.getUpdateContent();
                if (overview == null || overview.trim().isEmpty()) {
                    overview = DEFAULT_OVERVIEW;
                }
                if (updateItems == null || updateItems.isEmpty()) {
                    updateItems = getDefaultUpdateLogs();
                }
                renderAboutContent(overview, updateItems);
            }

            @Override
            public void onError(String error) {
                renderAboutContent(DEFAULT_OVERVIEW, getDefaultUpdateLogs());
            }
        });
    }

    private void renderAboutContent(String overview, List<ImoowApiHelper.UpdateLogItem> updateItems) {
        overviewTextView.setText(overview);
        updateContainer.removeAllViews();

        if (updateItems == null || updateItems.isEmpty()) {
            updateContainer.addView(makeSpacer(px(8)));
            updateContainer.addView(makeText("暂无更新日志", 0xFFAAAAAA, px(15), false, Gravity.START));
            return;
        }

        for (int i = 0; i < updateItems.size(); i++) {
            ImoowApiHelper.UpdateLogItem item = updateItems.get(i);
            if (i > 0) {
                updateContainer.addView(makeSpacer(px(8)));
                updateContainer.addView(makeDivider());
            }
            updateContainer.addView(makeSpacer(px(8)));
            updateContainer.addView(makeText(item.getVersion() + " 更新内容",
                    0xFFFFFFFF, px(18), true, Gravity.START));
            updateContainer.addView(makeSpacer(px(4)));
            updateContainer.addView(makeText(formatUpdateContent(item.getContent()),
                    0xFFAAAAAA, px(15), false, Gravity.START));
        }
    }

    private String formatUpdateContent(List<String> contentItems) {
        if (contentItems == null || contentItems.isEmpty()) {
            return "• 暂无详细内容";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contentItems.size(); i++) {
            String item = contentItems.get(i);
            if (item == null || item.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (item.startsWith("•")) {
                builder.append(item);
            } else {
                builder.append("• ").append(item);
            }
        }
        return builder.length() == 0 ? "• 暂无详细内容" : builder.toString();
    }

    private List<ImoowApiHelper.UpdateLogItem> getDefaultUpdateLogs() {
        List<ImoowApiHelper.UpdateLogItem> items = new ArrayList<>();
        items.add(new ImoowApiHelper.UpdateLogItem("v20260429", Arrays.asList(
                "增加歌词滚动模式设置（每行/阻塞），阻塞模式下可双击歌词跳转到对应时间",
                "增加歌词恢复间隔设置（阻塞模式下，无操作若干秒后自动恢复跟随）",
                "评论头像改为显示真实头像",
                "歌曲信息页面增加专辑封面显示（300×300）")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260427", Arrays.asList(
                "修复无损及以上音质无法正常播放/下载的问题（切换为eapi接口以正确获取FLAC地址）",
                "修复下载无损音质时文件后缀错误的问题（现在FLAC文件会正确以.flac后缀保存）")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260426", Arrays.asList(
                "增加音质选择功能（播放/下载可分别选择音质，支持标准/较高/极高/无损/Hi-Res/臻品声场/全景声/臻品母带）",
                "增加问题反馈方式",
                "修复设置铃声时自动下载音乐的问题，改为临时缓存，设置完成后自动清除")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260418-2", Arrays.asList(
                "增加听bilibili",
                "支持搜索视频、BV号打开、本地收藏、B站扫码登录、云端收藏",
                "优化听bilibili按钮显示，修复云端收藏夹视频只能加载20个的问题")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260418", Arrays.asList(
                "增加歌曲高潮显示",
                "增加智能截取铃声",
                "优化铃声截取界面排版")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260416", Arrays.asList(
                "增加小天才网易云音乐链接跳转（需配合XP模块实现）")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260412", Arrays.asList(
                "增加每日推荐功能",
                "增加雷达歌单功能",
                "增加音乐云盘功能",
                "增加编辑更多页面功能")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260411-2", Arrays.asList("增加MV功能")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260411", Arrays.asList(
                "修复评论时间显示问题",
                "修复私人漫游未登录无法使用")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260410-2", Arrays.asList(
                "修复私人漫游不刷新歌曲的问题",
                "修复随机播放时上下键未按照顺序切换的问题")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260410", Arrays.asList(
                "增加了听歌识曲功能",
                "修复版本号错误的问题",
                "增加开源信息显示")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260409-fix1", Arrays.asList("优化音量显示卡片")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260409", Arrays.asList("重做音量提示弹窗，采用卡片式显示")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260407", Arrays.asList("新增下载路线选择，提升下载速度")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260406", Arrays.asList(
                "全新 Material Design 2 暗色主题 UI 设计",
                "所有 emoji 图标替换为 Material 矢量图标（VectorDrawable）",
                "主题色改为紫色 #BB86FC，强调色改为青色 #03DAC6",
                "设置页布尔选项改为 Android 官方 SwitchMaterial 开关样式",
                "修复单曲循环图标、倍速选中颜色、译文按钮颜色",
                "音乐信息栏目标题改为矢量图标+文字",
                "我的歌单列表图标化，创建按钮改为主题色",
                "历史记录及歌单详情标题居中对齐")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260405-2", Arrays.asList(
                "变速模式升级为三档：音调不变 / 音调改变但速度不变 / 音调改变且速度改变",
                "播放器适配三种变速模式，实时切换生效")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260405", Arrays.asList("新增官网链接跳转")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260404-fix1", Arrays.asList(
                "修复首次安装时点击更新报错的问题：更新界面现在会在下载前请求存储权限")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260404", Arrays.asList(
                "新增自动更新功能：每天首次打开应用自动检测新版本",
                "发现新版本时弹出更新提示页面，支持一键下载安装",
                "设置页面新增「检测更新」按钮，可手动检查最新版本")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260403-2", Arrays.asList(
                "修复歌单加入播放列表只有200首的限制，现在有多少首可播放多少首",
                "新增歌单系统：搜索界面支持搜索歌单（单曲/歌单标签切换）",
                "新增歌单系统：收藏列表支持查看收藏歌单（单曲/歌单标签切换）",
                "歌单搜索结果显示歌曲数量，支持无限滚动加载",
                "歌单详情页面支持长按标题收藏/取消收藏歌单",
                "单曲和歌单共用本地/云端模式开关",
                "本地歌单保存到 /sdcard/163Music/playlists.json")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260403", Arrays.asList(
                "评论界面字体全面加大，适配手表屏幕阅读",
                "新增长按删除评论功能（带确认弹窗）",
                "修复发送评论后不自动刷新的问题",
                "音乐信息页面全面重写：显示歌曲详情（时长、专辑、音质、发布时间等所有字段）",
                "音乐信息页面展示歌曲百科所有板块内容",
                "新增歌手百科请求日志记录",
                "修复歌词界面熄屏/回桌面后歌词不再滚动的问题")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260402-fix1", Arrays.asList(
                "新增音乐信息功能：查看歌曲百科和歌手简介",
                "新增评论功能：查看评论、发送评论、点赞、回复、查看楼层子评论",
                "评论支持排序切换（推荐/最热/最新）",
                "修复歌词界面点击右上角有概率进入更多选项的问题",
                "修复歌词界面点击右下角有概率触发音量调节的问题",
                "歌词界面新增翻译开关，支持中英双语歌词显示",
                "翻译偏好持久化，开启/关闭后自动应用到后续歌曲",
                "下载歌曲时同步下载翻译歌词")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260402", Arrays.asList(
                "移除不可用功能入口：听歌识曲、短信登录、密码登录",
                "修复歌词页面切歌后歌词不刷新的问题",
                "切歌时歌词页自动加载新歌词并更新歌曲名称")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260401", Arrays.asList(
                "新增日志系统（API调用/功能操作全记录，写入/sdcard/163Music/app.log）",
                "修复VIP到期时间不显示（API字段expireTime）",
                "修复短信/密码登录“登录失败:null”（正确读取HTTP错误流）",
                "更多页面新增「识别歌曲」功能（听歌识曲 / 哼歌识曲）")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260401（右滑修复版）", Arrays.asList(
                "修复右滑退出被全局禁用导致所有界面无法右划的问题",
                "仅 MainActivity 禁用系统右滑退出，其余所有界面恢复正常右滑退出",
                "歌词界面/更多功能界面右滑正确关闭对应面板，主播放界面右滑直接退出应用")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260331-fix1", Arrays.asList(
                "修复返回播放界面时音乐意外自动播放",
                "关于页面文字放大适配手表屏幕",
                "设置页面移除登录入口（仅保留更多中的登录）")));
        items.add(new ImoowApiHelper.UpdateLogItem("v20260331", Arrays.asList(
                "新增变速模式设置（音调不变/音调改变）",
                "登录功能移至更多 > 登录",
                "设置页面重构为平铺列表风格",
                "新增开关选项页（屏幕常亮、收藏模式、变速模式）",
                "新增关于页面",
                "修复铃声管理名称秒数重复显示")));
        items.add(new ImoowApiHelper.UpdateLogItem("v2.0", Arrays.asList(
                "自定义应用图标",
                "修复短信登录“环境不安全”错误",
                "修复二维码显示不完整问题",
                "修复切歌后无法自动播放问题",
                "收藏列表删除重装后自动恢复",
                "新增前台服务保活机制",
                "登录返回空Cookie时不再覆盖已有Cookie")));
        return items;
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

    /**
     * Creates a horizontal row with a small vector icon on the left and text on the right.
     * @param iconRes       drawable resource id (vector icon)
     * @param text          text to display
     * @param textColor     text color
     * @param textSizePx    text size in pixels
     * @param linkify       whether to auto-linkify the text (email/URL)
     */
    private LinearLayout makeIconTextRow(int iconRes, String text, int textColor,
                                         int textSizePx, boolean linkify) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowParams);

        int iconSize = px(18);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(ContextCompat.getDrawable(this, iconRes));
        icon.setColorFilter(textColor);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.rightMargin = px(6);
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (linkify) Linkify.addLinks(tv, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
        row.addView(tv);

        return row;
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
