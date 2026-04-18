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

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;
import com.qinghe.music163pro.manager.BilibiliFavoritesManager;
import com.qinghe.music163pro.model.BilibiliFavorite;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

public class BilibiliPlaylistActivity extends BaseWatchActivity {

    private String bvid;
    private String videoTitle;
    private String ownerName;

    private TextView tvSubtitle;
    private TextView tvEmpty;
    private ImageView btnFavorite;
    private LinearLayout listContainer;
    private MaterialButton btnPlayAll;

    private final List<BilibiliApiHelper.BilibiliPage> fetchedPages = new ArrayList<>();
    private BilibiliFavoritesManager favoritesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favoritesManager = new BilibiliFavoritesManager(this);
        bvid = getIntent().getStringExtra("bvid");
        videoTitle = getIntent().getStringExtra("video_title");
        ownerName = getIntent().getStringExtra("owner_name");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(10), px(8), px(10), px(10));

        root.addView(createTitleBar());

        tvSubtitle = new TextView(this);
        tvSubtitle.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvSubtitle.setTextColor(getResources().getColor(R.color.text_secondary));
        tvSubtitle.setTextSize(11);
        tvSubtitle.setPadding(0, 0, 0, px(8));
        root.addView(tvSubtitle);

        btnPlayAll = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        btnPlayAll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(36)));
        btnPlayAll.setText("全部播放");
        btnPlayAll.setAllCaps(false);
        btnPlayAll.setTextSize(12);
        btnPlayAll.setOnClickListener(v -> playIndex(0));
        root.addView(btnPlayAll);

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

        updateFavoriteIcon();
        loadPages();
    }

    private LinearLayout createTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(34)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(px(20), px(20)));
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(getResources().getColor(R.color.text_primary));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        title.setGravity(Gravity.CENTER);
        title.setText(TextUtils.isEmpty(videoTitle) ? "BV视频列表" : videoTitle);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(title);

        btnFavorite = new ImageView(this);
        btnFavorite.setLayoutParams(new LinearLayout.LayoutParams(px(20), px(20)));
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        bar.addView(btnFavorite);
        return bar;
    }

    private void loadPages() {
        if (TextUtils.isEmpty(bvid)) {
            tvEmpty.setText("缺少 BV 号");
            btnPlayAll.setEnabled(false);
            return;
        }
        tvEmpty.setText("正在加载视频列表...");
        tvEmpty.setVisibility(View.VISIBLE);
        btnPlayAll.setEnabled(false);
        listContainer.removeAllViews();
        String cookie = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("bilibili_cookie", "");
        BilibiliApiHelper.getVideoInfo(bvid, cookie, new BilibiliApiHelper.VideoInfoCallback() {
            @Override
            public void onResult(List<BilibiliApiHelper.BilibiliPage> pages) {
                fetchedPages.clear();
                fetchedPages.addAll(pages);
                if (!pages.isEmpty()) {
                    videoTitle = pages.get(0).videoTitle;
                    ownerName = pages.get(0).ownerName;
                    tvSubtitle.setText(bvid + (TextUtils.isEmpty(ownerName) ? "" : " · " + ownerName));
                    btnPlayAll.setText("全部播放 (" + pages.size() + "集)");
                    btnPlayAll.setEnabled(true);
                    tvEmpty.setVisibility(View.GONE);
                    updateFavoriteIcon();
                    renderPageList();
                } else {
                    tvEmpty.setText("没有可播放分集");
                }
            }

            @Override
            public void onError(String message) {
                tvEmpty.setText("加载失败: " + message);
            }
        });
    }

    private void renderPageList() {
        listContainer.removeAllViews();
        int currentIndex = findCurrentIndexInPlayer();
        for (int i = 0; i < fetchedPages.size(); i++) {
            BilibiliApiHelper.BilibiliPage page = fetchedPages.get(i);
            final int index = i;

            LinearLayout item = new LinearLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = px(6);
            item.setLayoutParams(params);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(px(10), px(8), px(10), px(8));
            item.setBackgroundColor(getResources().getColor(
                    i == currentIndex ? R.color.colorPrimaryDark : R.color.surface_elevated));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> playIndex(index));

            TextView title = new TextView(this);
            String prefix = i == currentIndex ? "▶ " : (page.page + ". ");
            title.setText(prefix + (TextUtils.isEmpty(page.part) ? page.videoTitle : page.part));
            title.setTextColor(getResources().getColor(R.color.text_primary));
            title.setTextSize(13);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(title);

            TextView sub = new TextView(this);
            sub.setText(formatDuration(page.duration) + " · " + bvid);
            sub.setTextColor(getResources().getColor(R.color.text_secondary));
            sub.setTextSize(10);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, px(2), 0, 0);
            item.addView(sub);

            listContainer.addView(item);
        }
    }

    private int findCurrentIndexInPlayer() {
        Song currentSong = MusicPlayerManager.getInstance().getCurrentSong();
        if (currentSong == null || !currentSong.isBilibili() || !bvid.equalsIgnoreCase(currentSong.getBvid())) {
            return -1;
        }
        return MusicPlayerManager.getInstance().getCurrentIndex();
    }

    private void playIndex(int index) {
        if (index < 0 || index >= fetchedPages.size()) {
            return;
        }
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        if (canUseCurrentPlaylist()) {
            playerManager.playFromCurrentPlaylist(index);
            finishToPlayer();
            return;
        }

        Toast.makeText(this, "正在获取音频...", Toast.LENGTH_SHORT).show();
        List<Song> songs = buildSongsFromPages(fetchedPages);
        playerManager.setPlaylist(songs, index);
        BilibiliApiHelper.BilibiliPage page = fetchedPages.get(index);
        String cookie = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("bilibili_cookie", "");
        BilibiliApiHelper.getAudioStreamUrl(page.bvid, page.cid, cookie,
                new BilibiliApiHelper.AudioStreamCallback() {
                    @Override
                    public void onResult(String audioUrl) {
                        songs.get(index).setUrl(audioUrl);
                        playerManager.playCurrent();
                        finishToPlayer();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(BilibiliPlaylistActivity.this,
                                "获取音频失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean canUseCurrentPlaylist() {
        List<Song> playlist = MusicPlayerManager.getInstance().getPlaylist();
        if (playlist == null || playlist.size() != fetchedPages.size() || playlist.isEmpty()) {
            return false;
        }
        for (Song song : playlist) {
            if (!song.isBilibili() || !bvid.equalsIgnoreCase(song.getBvid())) {
                return false;
            }
        }
        return true;
    }

    private List<Song> buildSongsFromPages(List<BilibiliApiHelper.BilibiliPage> pages) {
        List<Song> songs = new ArrayList<>();
        for (BilibiliApiHelper.BilibiliPage page : pages) {
            Song song = new Song();
            // Use negative cid as id to avoid collision with NetEase song IDs.
            song.setId(-page.cid);
            song.setName(TextUtils.isEmpty(page.part) ? page.videoTitle : page.part);
            song.setArtist(page.ownerName);
            song.setAlbum(page.videoTitle);
            song.setSource("bilibili");
            song.setBvid(page.bvid);
            song.setCid(page.cid);
            songs.add(song);
        }
        return songs;
    }

    private void finishToPlayer() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void toggleFavorite() {
        if (TextUtils.isEmpty(bvid)) {
            return;
        }
        if (favoritesManager.isFavorite(bvid)) {
            favoritesManager.removeFavorite(bvid);
            Toast.makeText(this, "已取消收藏BV号", Toast.LENGTH_SHORT).show();
        } else {
            favoritesManager.addFavorite(new BilibiliFavorite(bvid, videoTitle, ownerName));
            Toast.makeText(this, "已收藏BV号", Toast.LENGTH_SHORT).show();
        }
        updateFavoriteIcon();
    }

    private void updateFavoriteIcon() {
        if (btnFavorite == null) {
            return;
        }
        boolean favorite = !TextUtils.isEmpty(bvid) && favoritesManager.isFavorite(bvid);
        btnFavorite.setImageResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        btnFavorite.setColorFilter(favorite
                ? getResources().getColor(R.color.favorite_red)
                : getResources().getColor(R.color.text_primary));
    }

    private String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
