package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
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

/**
 * Activity for opening Bilibili video by BV ID.
 * User inputs BV ID, app fetches video info, shows pages, adds to playlist.
 */
public class BilibiliBvidActivity extends BaseWatchActivity {

    private EditText etBvid;
    private LinearLayout llPagesList;
    private TextView tvStatus;
    private MaterialButton btnFetch;
    private MaterialButton btnFavorite;
    private BilibiliFavoritesManager favoritesManager;
    private final List<BilibiliApiHelper.BilibiliPage> fetchedPages = new ArrayList<>();
    private String currentBvid = "";
    private String currentTitle = "";
    private String currentOwner = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favoritesManager = new BilibiliFavoritesManager(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(px(10), px(8), px(10), px(10));

        container.addView(createTitleBar());

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        inputCard.setPadding(px(10), px(10), px(10), px(10));
        container.addView(inputCard);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvLabel.setText("输入BV号");
        tvLabel.setTextColor(getResources().getColor(R.color.text_secondary));
        tvLabel.setTextSize(11);
        tvLabel.setPadding(0, 0, 0, px(4));
        inputCard.addView(tvLabel);

        etBvid = new EditText(this);
        etBvid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(44)));
        etBvid.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        etBvid.setHint("BV1xxxxxxxxx");
        etBvid.setTextColor(getResources().getColor(R.color.text_primary));
        etBvid.setHintTextColor(getResources().getColor(R.color.text_disabled));
        etBvid.setTextSize(12);
        etBvid.setInputType(InputType.TYPE_CLASS_TEXT);
        etBvid.setPadding(px(8), px(4), px(8), px(4));
        etBvid.setSingleLine(true);
        inputCard.addView(etBvid);

        btnFetch = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(36));
        btnParams.topMargin = px(8);
        btnFetch.setLayoutParams(btnParams);
        btnFetch.setText("解析视频");
        btnFetch.setTextColor(getResources().getColor(R.color.text_primary));
        btnFetch.setTextSize(13);
        btnFetch.setAllCaps(false);
        btnFetch.setInsetTop(0);
        btnFetch.setInsetBottom(0);
        btnFetch.setMinHeight(0);
        btnFetch.setMinimumHeight(0);
        btnFetch.setOnClickListener(v -> fetchVideo());
        inputCard.addView(btnFetch);

        tvStatus = new TextView(this);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(11);
        tvStatus.setPadding(0, px(8), 0, px(4));
        tvStatus.setVisibility(View.GONE);
        container.addView(tvStatus);

        btnFavorite = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnFavorite.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(36)));
        ((LinearLayout.LayoutParams) btnFavorite.getLayoutParams()).topMargin = px(4);
        btnFavorite.setText("收藏BV号");
        btnFavorite.setTextColor(getResources().getColor(R.color.text_primary));
        btnFavorite.setTextSize(13);
        btnFavorite.setAllCaps(false);
        btnFavorite.setInsetTop(0);
        btnFavorite.setInsetBottom(0);
        btnFavorite.setMinHeight(0);
        btnFavorite.setMinimumHeight(0);
        btnFavorite.setVisibility(View.GONE);
        btnFavorite.setOnClickListener(v -> toggleFavoriteCurrentBvid());
        container.addView(btnFavorite);

        llPagesList = new LinearLayout(this);
        llPagesList.setOrientation(LinearLayout.VERTICAL);
        llPagesList.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(llPagesList);

        scrollView.addView(container);
        setContentView(scrollView);
    }

    private void fetchVideo() {
        String bvid = etBvid.getText().toString().trim();

        // Auto-prepend "BV" if not present
        if (!bvid.startsWith("BV") && !bvid.startsWith("bv")) {
            bvid = "BV" + bvid;
        }

        if (bvid.length() < 5) {
            Toast.makeText(this, "请输入有效的BV号", Toast.LENGTH_SHORT).show();
            return;
        }

        btnFetch.setEnabled(false);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在解析视频...");
        btnFavorite.setVisibility(View.GONE);
        llPagesList.removeAllViews();
        fetchedPages.clear();

        String cookie = getBilibiliCookie();
        final String finalBvid = bvid;

        BilibiliApiHelper.getVideoInfo(finalBvid, cookie, new BilibiliApiHelper.VideoInfoCallback() {
            @Override
            public void onResult(List<BilibiliApiHelper.BilibiliPage> pages) {
                btnFetch.setEnabled(true);
                if (pages.isEmpty()) {
                    tvStatus.setText("未找到视频分集");
                    return;
                }

                fetchedPages.clear();
                fetchedPages.addAll(pages);
                currentBvid = finalBvid;
                currentTitle = pages.get(0).videoTitle;
                currentOwner = pages.get(0).ownerName;

                tvStatus.setText(currentTitle + " - " + currentOwner + "\n共" + pages.size() + "集");

                MaterialButton btnPlayAll = new MaterialButton(
                        BilibiliBvidActivity.this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                LinearLayout.LayoutParams playAllParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, px(36));
                playAllParams.topMargin = px(4);
                btnPlayAll.setLayoutParams(playAllParams);
                btnPlayAll.setText("全部播放 (" + pages.size() + "集)");
                btnPlayAll.setTextColor(getResources().getColor(R.color.text_primary));
                btnPlayAll.setTextSize(13);
                btnPlayAll.setAllCaps(false);
                btnPlayAll.setInsetTop(0);
                btnPlayAll.setInsetBottom(0);
                btnPlayAll.setMinHeight(0);
                btnPlayAll.setMinimumHeight(0);
                btnPlayAll.setOnClickListener(v -> playAll(0));
                llPagesList.addView(btnPlayAll);

                updateFavoriteButton();
                btnFavorite.setVisibility(View.VISIBLE);

                for (int i = 0; i < pages.size(); i++) {
                    BilibiliApiHelper.BilibiliPage page = pages.get(i);
                    final int index = i;

                    LinearLayout row = new LinearLayout(BilibiliBvidActivity.this);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.topMargin = px(6);
                    row.setLayoutParams(rowParams);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(px(12), px(10), px(12), px(10));
                    row.setClickable(true);
                    row.setFocusable(true);
                    row.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
                    row.setOnClickListener(v -> playAll(index));

                    TextView tvNum = new TextView(BilibiliBvidActivity.this);
                    tvNum.setLayoutParams(new LinearLayout.LayoutParams(
                            px(24),
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    tvNum.setText(String.valueOf(page.page));
                    tvNum.setTextColor(getResources().getColor(R.color.text_secondary));
                    tvNum.setTextSize(11);
                    tvNum.setGravity(Gravity.CENTER);
                    row.addView(tvNum);

                    LinearLayout infoCol = new LinearLayout(BilibiliBvidActivity.this);
                    infoCol.setOrientation(LinearLayout.VERTICAL);
                    infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView tvPart = new TextView(BilibiliBvidActivity.this);
                    tvPart.setText(page.part.isEmpty() ? page.videoTitle : page.part);
                    tvPart.setTextColor(getResources().getColor(R.color.text_primary));
                    tvPart.setTextSize(13);
                    tvPart.setSingleLine(true);
                    tvPart.setEllipsize(TextUtils.TruncateAt.END);
                    infoCol.addView(tvPart);

                    TextView tvDur = new TextView(BilibiliBvidActivity.this);
                    tvDur.setText(formatDuration(page.duration) + " · " + currentBvid);
                    tvDur.setTextColor(getResources().getColor(R.color.text_secondary));
                    tvDur.setTextSize(11);
                    infoCol.addView(tvDur);

                    row.addView(infoCol);
                    llPagesList.addView(row);
                }
            }

            @Override
            public void onError(String message) {
                btnFetch.setEnabled(true);
                tvStatus.setText("解析失败: " + message);
            }
        });
    }

    private void playAll(int startIndex) {
        if (fetchedPages.isEmpty()) return;

        Toast.makeText(this, "正在获取音频...", Toast.LENGTH_SHORT).show();

        // Convert all pages to Songs
        List<Song> songs = new ArrayList<>();
        for (BilibiliApiHelper.BilibiliPage page : fetchedPages) {
            Song song = new Song();
            song.setId(buildBilibiliSongId(page.bvid, page.cid));
            song.setName(page.part.isEmpty() ? page.videoTitle : page.part);
            song.setArtist(page.ownerName);
            song.setAlbum(page.videoTitle);
            song.setSource("bilibili");
            song.setBvid(page.bvid);
            song.setCid(page.cid);
            songs.add(song);
        }

        // Set playlist and start playing
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        playerManager.setPlaylist(songs, startIndex);

        // Fetch audio URL for the first song, then start playing
        BilibiliApiHelper.BilibiliPage startPage = fetchedPages.get(startIndex);
        String cookie = getBilibiliCookie();

        BilibiliApiHelper.getAudioStreamUrl(startPage.bvid, startPage.cid, cookie,
                new BilibiliApiHelper.AudioStreamCallback() {
                    @Override
                    public void onResult(String audioUrl) {
                        Song currentSong = songs.get(startIndex);
                        currentSong.setUrl(audioUrl);
                        playerManager.playCurrent();

                        // Navigate back to main activity
                        Intent intent = new Intent(BilibiliBvidActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(BilibiliBvidActivity.this,
                                "获取音频失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getBilibiliCookie() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        return prefs.getString("bilibili_cookie", "");
    }

    private LinearLayout createTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(38)));
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
        title.setText("从BV号打开");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        bar.addView(title);

        ImageView placeholder = new ImageView(this);
        placeholder.setLayoutParams(new LinearLayout.LayoutParams(px(22), px(22)));
        bar.addView(placeholder);
        return bar;
    }

    private void updateFavoriteButton() {
        boolean favorite = !TextUtils.isEmpty(currentBvid) && favoritesManager.isFavorite(currentBvid);
        btnFavorite.setText(favorite ? "取消收藏BV号" : "收藏BV号");
    }

    private void toggleFavoriteCurrentBvid() {
        if (TextUtils.isEmpty(currentBvid)) {
            return;
        }
        if (favoritesManager.isFavorite(currentBvid)) {
            favoritesManager.removeFavorite(currentBvid);
            Toast.makeText(this, "已取消收藏BV号", Toast.LENGTH_SHORT).show();
        } else {
            favoritesManager.addFavorite(new BilibiliFavorite(currentBvid, currentTitle, currentOwner));
            Toast.makeText(this, "已收藏BV号", Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
    }

    private String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    private long buildBilibiliSongId(String bvid, long cid) {
        long mixed = ((((long) (bvid != null ? bvid.hashCode() : 0)) & 0xFFFFFFFFL) << 32)
                ^ (cid & 0xFFFFFFFFL);
        return -Math.abs(mixed == 0 ? cid + 1 : mixed);
    }
}
