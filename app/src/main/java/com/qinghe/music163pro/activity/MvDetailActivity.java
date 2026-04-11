package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.NetworkImageLoader;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MV detail page for watch screens.
 */
public class MvDetailActivity extends BaseWatchActivity {

    private static final String TAG = "MvDetailActivity";

    private long mvId;
    private String mvName;
    private String cookie;

    private FrameLayout coverContainer;
    private ImageView ivCover;
    private TextView tvName;
    private TextView tvArtist;
    private TextView tvMeta;
    private TextView tvDescLabel;
    private TextView tvDesc;
    private TextView tvLoading;
    private MaterialButton btnPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mv_detail);

        mvId = getIntent().getLongExtra("mv_id", 0);
        mvName = getIntent().getStringExtra("mv_name");
        cookie = getIntent().getStringExtra("cookie");
        if (cookie == null || cookie.isEmpty()) {
            cookie = MusicPlayerManager.getInstance().getCookie();
        }

        coverContainer = findViewById(R.id.layout_mv_cover);
        ivCover = findViewById(R.id.iv_mv_cover);
        tvName = findViewById(R.id.tv_mv_name);
        tvArtist = findViewById(R.id.tv_mv_artist);
        tvMeta = findViewById(R.id.tv_mv_meta);
        tvDescLabel = findViewById(R.id.tv_mv_desc_label);
        tvDesc = findViewById(R.id.tv_mv_desc);
        tvLoading = findViewById(R.id.tv_mv_loading);
        btnPlay = findViewById(R.id.btn_mv_play);

        View.OnClickListener playClickListener = v -> openPlayer();
        coverContainer.setOnClickListener(playClickListener);
        btnPlay.setOnClickListener(playClickListener);

        if (mvId <= 0) {
            Toast.makeText(this, "MV信息无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MusicLog.op(TAG, "打开MV详情", "mvId=" + mvId + " mvName=" + mvName);
        tvName.setText(mvName != null && !mvName.isEmpty() ? mvName : "MV详情");
        fetchMvDetail();
    }

    private void fetchMvDetail() {
        showLoading(true);
        MusicApiHelper.getMvDetail(mvId, cookie, new MusicApiHelper.MvDetailCallback() {
            @Override
            public void onResult(JSONObject mvDetail) {
                showLoading(false);
                bindMvDetail(mvDetail);
            }

            @Override
            public void onError(String message) {
                showLoading(false);
                Toast.makeText(MvDetailActivity.this, "获取MV详情失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindMvDetail(JSONObject mvDetail) {
        if (mvDetail == null) {
            return;
        }
        String fetchedName = mvDetail.optString("name", "");
        if (fetchedName != null && !fetchedName.trim().isEmpty()) {
            mvName = fetchedName;
        }
        String artistName = mvDetail.optString("artistName", "");
        if (artistName.isEmpty()) {
            artistName = buildArtistText(mvDetail.optJSONArray("artists"));
        }
        String coverUrl = mvDetail.optString("cover", "");
        if (coverUrl.isEmpty()) {
            coverUrl = mvDetail.optString("coverUrl", "");
        }

        long durationMs = mvDetail.optLong("duration", 0);
        long playCount = mvDetail.optLong("playCount", 0);
        String publishTime = mvDetail.optString("publishTime", "");
        String briefDesc = mvDetail.optString("briefDesc", "");
        String desc = mvDetail.optString("desc", "");
        if (desc == null || desc.trim().isEmpty()) {
            desc = briefDesc;
        }

        tvName.setText(mvName != null && !mvName.isEmpty() ? mvName : "MV详情");
        tvArtist.setText(artistName.isEmpty() ? "未知作者" : artistName);
        tvMeta.setText(buildMetaText(durationMs, playCount, publishTime));
        if (desc == null || desc.trim().isEmpty()) {
            tvDescLabel.setVisibility(View.GONE);
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDescLabel.setVisibility(View.VISIBLE);
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(desc);
        }
        NetworkImageLoader.load(ivCover, coverUrl);
    }

    private String buildArtistText(JSONArray artists) {
        if (artists == null || artists.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < artists.length(); i++) {
            JSONObject artist = artists.optJSONObject(i);
            if (artist == null) {
                continue;
            }
            String name = artist.optString("name", "");
            if (name.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    private String buildMetaText(long durationMs, long playCount, String publishTime) {
        StringBuilder builder = new StringBuilder();
        if (durationMs > 0) {
            long totalSeconds = durationMs / 1000;
            builder.append(totalSeconds / 60).append(':');
            long seconds = totalSeconds % 60;
            if (seconds < 10) {
                builder.append('0');
            }
            builder.append(seconds);
        }
        if (playCount > 0) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("播放 ").append(formatCount(playCount));
        }
        if (publishTime != null && !publishTime.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(publishTime);
        }
        return builder.length() > 0 ? builder.toString() : "暂无更多信息";
    }

    private String formatCount(long count) {
        if (count >= 100000000L) {
            return String.format(java.util.Locale.getDefault(), "%.1f亿", count / 100000000.0);
        }
        if (count >= 10000L) {
            return String.format(java.util.Locale.getDefault(), "%.1f万", count / 10000.0);
        }
        return String.valueOf(count);
    }

    private void showLoading(boolean loading) {
        tvLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnPlay.setEnabled(!loading);
        coverContainer.setEnabled(!loading);
    }

    private void openPlayer() {
        Intent intent = new Intent(this, MvPlayerActivity.class);
        intent.putExtra("mv_id", mvId);
        intent.putExtra("mv_name", mvName);
        intent.putExtra("cookie", cookie);
        startActivity(intent);
    }
}
