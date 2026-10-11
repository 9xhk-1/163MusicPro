package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;

import java.util.ArrayList;
import java.util.List;

public class BilibiliCloudFavoritesActivity extends BaseWatchActivity {

    private TextView tvStatus;
    private TextView tvEmpty;
    private LinearLayout listContainer;
    private final List<BilibiliApiHelper.BilibiliFavoriteFolder> folders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(10));

        root.addView(createTitleBar("云端收藏"));

        tvStatus = new TextView(this);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(11);
        tvStatus.setPadding(0, 0, 0, px(8));
        root.addView(tvStatus);

        tvEmpty = new TextView(this);
        tvEmpty.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary));
        tvEmpty.setTextSize(12);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, px(24), 0, 0);
        root.addView(tvEmpty);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        scrollView.addView(root);
        setContentView(scrollView);

        loadFolders();
    }

    private void loadFolders() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("bilibili_cookie", "");
        if (TextUtils.isEmpty(cookie) || !cookie.contains("SESSDATA")) {
            Toast.makeText(this, "请先登录B站", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tvStatus.setText("正在加载云端收藏夹...");
        tvEmpty.setVisibility(View.GONE);
        listContainer.removeAllViews();
        BilibiliApiHelper.getFavoriteFolders(cookie, new BilibiliApiHelper.FavoriteFoldersCallback() {
            @Override
            public void onResult(List<BilibiliApiHelper.BilibiliFavoriteFolder> result) {
                folders.clear();
                folders.addAll(result);
                tvStatus.setText("共 " + folders.size() + " 个收藏夹");
                renderFolders();
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("加载失败: " + message);
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("无法获取云端收藏夹");
            }
        });
    }

    private void renderFolders() {
        listContainer.removeAllViews();
        tvEmpty.setVisibility(folders.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText("暂无云端收藏夹");
        for (BilibiliApiHelper.BilibiliFavoriteFolder folder : folders) {
            listContainer.addView(createFolderItem(folder));
        }
    }

    private View createFolderItem(BilibiliApiHelper.BilibiliFavoriteFolder folder) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = px(8);
        item.setLayoutParams(params);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(px(12), px(10), px(12), px(10));
        item.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, BilibiliCloudFavoriteVideosActivity.class);
            intent.putExtra("media_id", folder.mediaId);
            intent.putExtra("folder_title", folder.title);
            startActivity(intent);
        });

        TextView title = new TextView(this);
        title.setText(folder.title);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        item.addView(title);

        TextView sub = new TextView(this);
        sub.setText(folder.mediaCount + " 个视频");
        sub.setTextColor(getResources().getColor(R.color.text_secondary));
        sub.setTextSize(11);
        sub.setPadding(0, px(3), 0, 0);
        item.addView(sub);
        return item;
    }

    private LinearLayout createTitleBar(String titleText) {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(36)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(getResources().getColor(R.color.text_primary));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText(titleText);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        ImageView placeholder = new ImageView(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);
        return bar;
    }
}
