package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchUiUtils;

/**
 * Bilibili feature menu - lists available functions:
 * - 从BV号打开 (open by BV ID)
 * - 登录 (Bilibili QR login)
 */
public class BilibiliActivity extends BaseWatchActivity {

    private MaterialButton cloudFavoritesItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(px(10), px(8), px(10), px(10));

        container.addView(createTitleBar());

        container.addView(createMenuItem(R.drawable.ic_search, "搜索视频",
                () -> startActivity(new Intent(this, BilibiliSearchActivity.class))));

        // Menu item: 从BV号打开
        container.addView(createMenuItem(R.drawable.ic_video_library, "从BV号打开",
                () -> startActivity(new Intent(this, BilibiliBvidActivity.class))));

        container.addView(createMenuItem(R.drawable.ic_favorite_border, "收藏",
                () -> startActivity(new Intent(this, BilibiliFavoritesActivity.class))));

        // Menu item: 登录
        container.addView(createMenuItem(R.drawable.ic_qr_code, "登录B站",
                () -> startActivity(new Intent(this, BilibiliLoginActivity.class))));

        cloudFavoritesItem = createMenuItem(R.drawable.ic_cloud, "云端收藏",
                () -> startActivity(new Intent(this, BilibiliCloudFavoritesActivity.class)));
        container.addView(cloudFavoritesItem);

        // Show login status
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = px(8);
        statusRow.setLayoutParams(statusParams);
        statusRow.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        statusRow.setPadding(px(10), px(8), px(10), px(8));

        TextView tvLoginStatus = new TextView(this);
        tvLoginStatus.setId(android.R.id.text1);
        tvLoginStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvLoginStatus.setTextSize(11);
        updateLoginStatus(tvLoginStatus);
        updateCloudFavoritesVisibility();
        statusRow.addView(tvLoginStatus);
        container.addView(statusRow);

        scrollView.addView(container);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tvStatus = findViewById(android.R.id.text1);
        if (tvStatus != null) {
            updateLoginStatus(tvStatus);
        }
        updateCloudFavoritesVisibility();
    }

    private void updateLoginStatus(TextView tv) {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("bilibili_cookie", "");
        if (!TextUtils.isEmpty(cookie) && cookie.contains("SESSDATA")) {
            tv.setText("已登录B站");
        } else {
            tv.setText("未登录B站（不影响大部分功能）");
        }
    }

    private void updateCloudFavoritesVisibility() {
        if (cloudFavoritesItem == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("bilibili_cookie", "");
        cloudFavoritesItem.setVisibility(
                !TextUtils.isEmpty(cookie) && cookie.contains("SESSDATA") ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private LinearLayout createTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton back = new MaterialButton(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)));
        back.setIconResource(R.drawable.ic_arrow_back);
        back.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        back.setIconSize(dpToPx(20));
        back.setIconPadding(0);
        back.setInsetTop(0);
        back.setInsetBottom(0);
        back.setMinWidth(0);
        back.setMinimumWidth(0);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText("听bilibili");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        return bar;
    }

    private MaterialButton createMenuItem(int iconRes, String label, Runnable onClick) {
        return createWatchMenuButton(iconRes, label, v -> onClick.run());
    }
}
