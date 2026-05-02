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
            "加载中...";
    private static final String UNKNOWN_VERSION = "unknown";

    private String currentVersionName = UNKNOWN_VERSION;
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
        currentVersionName = ImoowApiHelper.getAppVersionName(this, UNKNOWN_VERSION);
        content.addView(makeText("版本: " + currentVersionName, 0xFFCCCCCC, px(16), false, Gravity.CENTER));

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
                renderAboutContent(overview,
                        ImoowApiHelper.getAboutUpdateLogs(updateItems, currentVersionName));
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
        items.add(new ImoowApiHelper.UpdateLogItem("加载中...", Arrays.asList(
                "加载中...")));
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
