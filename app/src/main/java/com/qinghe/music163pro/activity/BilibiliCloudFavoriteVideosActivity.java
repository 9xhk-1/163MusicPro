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

public class BilibiliCloudFavoriteVideosActivity extends BaseWatchActivity {

    private long mediaId;
    private String folderTitle;
    private TextView tvStatus;
    private TextView tvEmpty;
    private LinearLayout listContainer;
    private final List<BilibiliApiHelper.BilibiliSearchVideo> videos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaId = getIntent().getLongExtra("media_id", 0);
        folderTitle = getIntent().getStringExtra("folder_title");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(10));

        root.addView(createTitleBar());

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

        loadVideos();
    }

    private void loadVideos() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        String cookie = prefs.getString("bilibili_cookie", "");
        if (TextUtils.isEmpty(cookie) || !cookie.contains("SESSDATA")) {
            Toast.makeText(this, "请先登录B站", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tvStatus.setText("正在加载收藏夹视频...");
        tvEmpty.setVisibility(View.GONE);
        listContainer.removeAllViews();
        BilibiliApiHelper.getFavoriteFolderVideos(mediaId, cookie,
                new BilibiliApiHelper.FavoriteFolderVideosCallback() {
                    @Override
                    public void onResult(String actualFolderTitle, List<BilibiliApiHelper.BilibiliSearchVideo> result) {
                        if (!TextUtils.isEmpty(actualFolderTitle)) {
                            folderTitle = actualFolderTitle;
                        }
                        videos.clear();
                        videos.addAll(result);
                        tvStatus.setText((TextUtils.isEmpty(folderTitle) ? "收藏夹" : folderTitle)
                                + " · " + videos.size() + " 个视频");
                        renderVideos();
                    }

                    @Override
                    public void onError(String message) {
                        tvStatus.setText("加载失败: " + message);
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("无法获取收藏夹视频");
                    }
                });
    }

    private void renderVideos() {
        listContainer.removeAllViews();
        tvEmpty.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText("收藏夹里还没有视频");
        for (BilibiliApiHelper.BilibiliSearchVideo video : videos) {
            listContainer.addView(createVideoItem(video));
        }
    }

    private View createVideoItem(BilibiliApiHelper.BilibiliSearchVideo video) {
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
            Intent intent = new Intent(this, BilibiliPlaylistActivity.class);
            intent.putExtra("bvid", video.bvid);
            intent.putExtra("video_title", video.title);
            intent.putExtra("owner_name", video.ownerName);
            startActivity(intent);
        });

        TextView title = new TextView(this);
        title.setText(video.title);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        item.addView(title);

        TextView sub = new TextView(this);
        sub.setText(video.bvid + " · " + video.ownerName + " · " + formatDuration(video.duration));
        sub.setTextColor(getResources().getColor(R.color.text_secondary));
        sub.setTextSize(11);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        sub.setMarqueeRepeatLimit(-1);
        sub.setSelected(true);
        sub.setPadding(0, px(3), 0, 0);
        item.addView(sub);
        return item;
    }

    private LinearLayout createTitleBar() {
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
        title.setText(TextUtils.isEmpty(folderTitle) ? "收藏夹视频" : folderTitle);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        bar.addView(title);

        ImageView placeholder = new ImageView(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);
        return bar;
    }

    private String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
