package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.BilibiliApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.WatchUiUtils;

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
    private final List<BilibiliApiHelper.BilibiliPage> fetchedPages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        scrollView.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(WatchUiUtils.px(this, 12), WatchUiUtils.px(this, 8),
                WatchUiUtils.px(this, 12), WatchUiUtils.px(this, 8));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvTitle.setText("从BV号打开");
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, WatchUiUtils.px(this, 8));
        container.addView(tvTitle);

        // BV ID label
        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvLabel.setText("输入BV号");
        tvLabel.setTextColor(getResources().getColor(R.color.text_secondary));
        tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tvLabel.setPadding(0, 0, 0, WatchUiUtils.px(this, 4));
        container.addView(tvLabel);

        // BV ID input
        etBvid = new EditText(this);
        etBvid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WatchUiUtils.px(this, 40)));
        etBvid.setBackgroundColor(getResources().getColor(R.color.surface_elevated));
        etBvid.setHint("BV1xxxxxxxxx");
        etBvid.setTextColor(getResources().getColor(R.color.text_primary));
        etBvid.setHintTextColor(getResources().getColor(R.color.text_disabled));
        etBvid.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        etBvid.setInputType(InputType.TYPE_CLASS_TEXT);
        etBvid.setPadding(WatchUiUtils.px(this, 8), WatchUiUtils.px(this, 4),
                WatchUiUtils.px(this, 8), WatchUiUtils.px(this, 4));
        etBvid.setSingleLine(true);
        container.addView(etBvid);

        // Fetch button
        btnFetch = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WatchUiUtils.px(this, 36));
        btnParams.topMargin = WatchUiUtils.px(this, 8);
        btnFetch.setLayoutParams(btnParams);
        btnFetch.setText("解析视频");
        btnFetch.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        btnFetch.setAllCaps(false);
        btnFetch.setOnClickListener(v -> fetchVideo());
        container.addView(btnFetch);

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tvStatus.setPadding(0, WatchUiUtils.px(this, 6), 0, WatchUiUtils.px(this, 4));
        tvStatus.setVisibility(View.GONE);
        container.addView(tvStatus);

        // Pages list container
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

                String title = pages.get(0).videoTitle;
                String owner = pages.get(0).ownerName;
                tvStatus.setText(title + " - " + owner + "\n共" + pages.size() + "集");

                // Show "Play All" button
                MaterialButton btnPlayAll = new MaterialButton(
                        BilibiliBvidActivity.this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                LinearLayout.LayoutParams playAllParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, WatchUiUtils.px(BilibiliBvidActivity.this, 36));
                playAllParams.topMargin = WatchUiUtils.px(BilibiliBvidActivity.this, 4);
                btnPlayAll.setLayoutParams(playAllParams);
                btnPlayAll.setText("全部播放 (" + pages.size() + "集)");
                btnPlayAll.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                btnPlayAll.setAllCaps(false);
                btnPlayAll.setOnClickListener(v -> playAll(0));
                llPagesList.addView(btnPlayAll);

                // List each page
                for (int i = 0; i < pages.size(); i++) {
                    BilibiliApiHelper.BilibiliPage page = pages.get(i);
                    final int index = i;

                    LinearLayout row = new LinearLayout(BilibiliBvidActivity.this);
                    row.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            WatchUiUtils.px(BilibiliBvidActivity.this, 44)));
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(WatchUiUtils.px(BilibiliBvidActivity.this, 4), 0,
                            WatchUiUtils.px(BilibiliBvidActivity.this, 4), 0);
                    row.setClickable(true);
                    row.setFocusable(true);
                    row.setBackgroundResource(android.R.drawable.list_selector_background);
                    row.setOnClickListener(v -> playAll(index));

                    // Page number
                    TextView tvNum = new TextView(BilibiliBvidActivity.this);
                    tvNum.setLayoutParams(new LinearLayout.LayoutParams(
                            WatchUiUtils.px(BilibiliBvidActivity.this, 28),
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    tvNum.setText(String.valueOf(page.page));
                    tvNum.setTextColor(getResources().getColor(R.color.text_secondary));
                    tvNum.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                    tvNum.setGravity(Gravity.CENTER);
                    row.addView(tvNum);

                    // Page title + duration
                    LinearLayout infoCol = new LinearLayout(BilibiliBvidActivity.this);
                    infoCol.setOrientation(LinearLayout.VERTICAL);
                    infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView tvPart = new TextView(BilibiliBvidActivity.this);
                    tvPart.setText(page.part.isEmpty() ? page.videoTitle : page.part);
                    tvPart.setTextColor(getResources().getColor(R.color.text_primary));
                    tvPart.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                    tvPart.setSingleLine(true);
                    tvPart.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    infoCol.addView(tvPart);

                    TextView tvDur = new TextView(BilibiliBvidActivity.this);
                    tvDur.setText(formatDuration(page.duration));
                    tvDur.setTextColor(getResources().getColor(R.color.text_secondary));
                    tvDur.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
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
            // Use negative cid as id to avoid collision with NetEase IDs
            song.setId(-page.cid);
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

    private String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
