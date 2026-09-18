package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Album detail activity - shows album name, cover, and track list.
 * Designed for watch screen (320x360 dpi).
 */
public class AlbumDetailActivity extends AppCompatActivity {

    private ListView lvSongs;
    private final List<Song> displayList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private MusicPlayerManager playerManager;
    private TextView tvStatus;
    private ImageView ivAlbumCover;
    private TextView tvAlbumName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        long albumId = getIntent().getLongExtra("album_id", 0);
        String albumName = getIntent().getStringExtra("album_name");
        String albumCoverUrl = getIntent().getStringExtra("album_cover_url");

        playerManager = MusicPlayerManager.getInstance();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title bar
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("专辑");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        tvTitle.setLayoutParams(titleParams);
        titleBar.addView(tvTitle);
        root.addView(titleBar);

        // Album info row: cover + name
        LinearLayout albumInfoRow = new LinearLayout(this);
        albumInfoRow.setOrientation(LinearLayout.HORIZONTAL);
        albumInfoRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        albumInfoRow.setPadding(0, px(8), 0, px(8));
        albumInfoRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(albumInfoRow);

        ivAlbumCover = new ImageView(this);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(px(52), px(52));
        coverParams.setMarginEnd(px(8));
        ivAlbumCover.setLayoutParams(coverParams);
        ivAlbumCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivAlbumCover.setBackgroundColor(0xFF252525);
        albumInfoRow.addView(ivAlbumCover);

        tvAlbumName = new TextView(this);
        tvAlbumName.setText(albumName != null ? albumName : "");
        tvAlbumName.setTextColor(0xFFFFFFFF);
        tvAlbumName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        tvAlbumName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAlbumName.setMaxLines(2);
        tvAlbumName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvAlbumName.setLayoutParams(nameParams);
        albumInfoRow.addView(tvAlbumName);

        // Load initial cover if provided via intent
        if (albumCoverUrl != null && !albumCoverUrl.isEmpty()) {
            NetworkImageLoader.load(ivAlbumCover, albumCoverUrl);
        }

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setText("正在加载...");
        tvStatus.setTextColor(0x80FFFFFF);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, px(4), 0, px(4));
        root.addView(tvStatus);

        lvSongs = new ListView(this);
        lvSongs.setDividerHeight(1);
        lvSongs.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvSongs.setLayoutParams(lvParams);
        lvSongs.setVisibility(View.GONE);
        root.addView(lvSongs);

        setContentView(root);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText((position + 1) + ". " + song.getName());
                    tvArtist.setText(song.getArtist());
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, song.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvSongs.setAdapter(adapter);

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        if (albumId > 0) {
            loadAlbumDetail(albumId);
        } else {
            tvStatus.setText("专辑信息不可用");
        }
    }

    private void loadAlbumDetail(long albumId) {
        String cookie = playerManager.getCookie();
        MusicApiHelper.getAlbumDetail(albumId, cookie, new MusicApiHelper.AlbumDetailCallback() {
            @Override
            public void onResult(String albumName, String coverUrl, List<Song> songs) {
                if (!albumName.isEmpty()) {
                    tvAlbumName.setText(albumName);
                }
                if (!coverUrl.isEmpty()) {
                    NetworkImageLoader.load(ivAlbumCover, coverUrl);
                }
                displayList.clear();
                displayList.addAll(songs);
                adapter.notifyDataSetChanged();
                if (songs.isEmpty()) {
                    tvStatus.setText("暂无歌曲");
                    lvSongs.setVisibility(View.GONE);
                } else {
                    tvStatus.setText(songs.size() + " 首歌曲");
                    lvSongs.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("加载失败: " + message);
            }
        });
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float scale = screenWidth / 320f;
        return Math.round(baseValue * scale);
    }
}
