package com.qinghe.music163pro.activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchUiUtils;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;
import com.qinghe.music163pro.util.PlaylistCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the user's playlists (created + subscribed) from the cloud.
 * Top-right "+" button to create a new playlist.
 * Clicking a playlist opens PlaylistDetailActivity with full ownership info.
 * Designed for watch screen (320x360 dpi).
 */
public class MyPlaylistsActivity extends AppCompatActivity {

    private ListView lvPlaylists;
    private final List<PlaylistInfo> displayList = new ArrayList<>();
    private ArrayAdapter<PlaylistInfo> adapter;
    private TextView tvStatus;
    private long currentUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title row with "+" button
        android.widget.RelativeLayout titleRow = new android.widget.RelativeLayout(this);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("我的歌单");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(px(28), 0, px(28), px(4));
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        tvTitle.setLayoutParams(titleParams);
        titleRow.addView(tvTitle);

        // "+" button (top-right)
        ImageView btnCreate = new ImageView(this);
        btnCreate.setImageResource(R.drawable.ic_add_box);
        btnCreate.setColorFilter(0xFFF2F2F7);
        btnCreate.setClickable(true);
        btnCreate.setFocusable(true);
        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(
                px(24), px(24));
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        btnParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnCreate.setLayoutParams(btnParams);
        btnCreate.setOnClickListener(v -> showCreatePlaylistDialog());
        titleRow.addView(btnCreate);

        root.addView(titleRow);

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setText("正在加载...");
        tvStatus.setTextColor(0xFFAEAEB2);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, px(4), 0, px(4));
        root.addView(tvStatus);

        lvPlaylists = new ListView(this);
        lvPlaylists.setDividerHeight(1);
        lvPlaylists.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvPlaylists.setLayoutParams(lvParams);
        lvPlaylists.setVisibility(View.GONE);
        root.addView(lvPlaylists);

        setContentView(root);

        adapter = new ArrayAdapter<PlaylistInfo>(this,
                R.layout.item_my_playlist, R.id.tv_name, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.item_my_playlist, parent, false);
                }
                PlaylistInfo pl = getItem(position);
                if (pl != null) {
                    ImageView icon = view.findViewById(R.id.iv_playlist_icon);
                    TextView tvName = view.findViewById(R.id.tv_name);
                    TextView tvDetail = view.findViewById(R.id.tv_detail);

                    // Load playlist cover image; fall back to static drawable if no cover URL
                    if (pl.getCoverUrl() != null && !pl.getCoverUrl().isEmpty()) {
                        icon.clearColorFilter();
                        NetworkImageLoader.load(icon, pl.getCoverUrl());
                    } else if (pl.isLikedPlaylist()) {
                        icon.setImageResource(R.drawable.ic_favorite);
                        icon.setColorFilter(0xFFFF4081);
                    } else if (pl.getUserId() == currentUserId) {
                        icon.setImageResource(R.drawable.ic_music_note);
                        icon.setColorFilter(0xFFF2F2F7);
                    } else {
                        icon.setImageResource(R.drawable.ic_queue_music);
                        icon.setColorFilter(0x80FFFFFF);
                    }

                    if (!android.text.TextUtils.equals(tvName.getText(), pl.getName())) {
                        tvName.setText(pl.getName());
                    }
                    tvName.setSelected(true);
                    tvName.setTextColor(0xFFFFFFFF);
                    tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));

                    String detail = pl.getTrackCount() + "首";
                    if (pl.getCreator() != null && !pl.getCreator().isEmpty()) {
                        detail += " · " + pl.getCreator();
                    }
                    if (!android.text.TextUtils.equals(tvDetail.getText(), detail)) {
                        tvDetail.setText(detail);
                    }
                    tvDetail.setSelected(true);
                    tvDetail.setTextColor(0xFFAEAEB2);
                    tvDetail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(10));
                }
                return view;
            }
        };
        lvPlaylists.setAdapter(adapter);
        NetworkImageLoader.attachListViewOptimization(lvPlaylists, adapter, position ->
                position >= 0 && position < displayList.size()
                        ? displayList.get(position).getCoverUrl() : null);

        lvPlaylists.setOnItemClickListener((parent, view, position, id) -> {
            PlaylistInfo pl = displayList.get(position);
            Intent intent = new Intent(this, PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", pl.getId());
            intent.putExtra("playlist_name", pl.getName());
            intent.putExtra("track_count", pl.getTrackCount());
            intent.putExtra("creator", pl.getCreator());
            intent.putExtra("creator_user_id", pl.getUserId());
            intent.putExtra("is_liked_playlist", pl.isLikedPlaylist());
            startActivity(intent);
        });

        loadPlaylists();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh list when returning from detail (e.g. after delete/unsub)
        loadPlaylists();
    }

    private void loadPlaylists() {
        // 秒开：内存快照直接渲染（零 IO），磁盘快照走后台线程，
        // 反序列化 500 首 Song 不再卡住打开页面那一下；接口回来后再对比校准。
        List<PlaylistInfo> cached = PlaylistCache.peekMyPlaylists();
        if (cached != null && !cached.isEmpty()) {
            showPlaylists(cached, true);
        }
        PlaylistCache.loadMyPlaylistsAsync(this, disk -> {
            if (isFinishing() || isDestroyed()) return;
            if (disk == null || disk.isEmpty() || !displayList.isEmpty()) return;
            showPlaylists(disk, true);
        });

        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            if (displayList.isEmpty()) {
                tvStatus.setText("请先登录");
            }
            return;
        }

        MusicApiHelper.getUserPlaylists(cookie, new MusicApiHelper.UserPlaylistsCallback() {
            @Override
            public void onResult(List<PlaylistInfo> playlists) {
                if (PlaylistCache.samePlaylists(displayList, playlists)) {
                    // 数据没变：只把"本地快照"字样去掉，不重建列表、不闪封面
                    if (!playlists.isEmpty()) {
                        tvStatus.setText(playlists.size() + " 个歌单");
                        lvPlaylists.setVisibility(View.VISIBLE);
                    }
                    return;
                }
                showPlaylists(playlists, false);
                PlaylistCache.saveMyPlaylists(MyPlaylistsActivity.this, playlists);
            }

            @Override
            public void onError(String message) {
                if (displayList.isEmpty()) {
                    tvStatus.setText("加载失败: " + message);
                } else {
                    // 有快照就别把列表盖成错误提示，标明是离线数据即可
                    tvStatus.setText(displayList.size() + " 个歌单（离线快照）");
                }
            }
        });
    }

    /** 统一渲染入口：缓存命中与网络返回都走这里，便于做差异对比。 */
    private void showPlaylists(List<PlaylistInfo> playlists, boolean fromCache) {
        displayList.clear();
        displayList.addAll(playlists);
        // Detect current user ID from the first playlist's creator (if it's their "liked" playlist)
        if (!playlists.isEmpty() && playlists.get(0).isLikedPlaylist()) {
            currentUserId = playlists.get(0).getUserId();
        }
        adapter.notifyDataSetChanged();
        if (playlists.isEmpty()) {
            tvStatus.setText("暂无歌单");
            lvPlaylists.setVisibility(View.GONE);
        } else {
            tvStatus.setText(playlists.size() + " 个歌单" + (fromCache ? "（本地快照）" : ""));
            lvPlaylists.setVisibility(View.VISIBLE);
        }
    }

    private void showCreatePlaylistDialog() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.android.material.textfield.TextInputLayout inputWrap =
                com.qinghe.music163pro.util.WatchUiUtils.createWatchTextInput(this, "输入歌单名称");
        EditText input = (com.google.android.material.textfield.TextInputEditText) inputWrap.getEditText();

        LinearLayout container = new LinearLayout(this);
        container.setPadding(px(12), px(8), px(12), px(4));
        container.addView(inputWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle("创建歌单")
                .setView(container)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请输入歌单名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createPlaylist(name, cookie);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createPlaylist(String name, String cookie) {
        Toast.makeText(this, "正在创建...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.createPlaylist(name, 0, cookie, new MusicApiHelper.PlaylistCreateCallback() {
            @Override
            public void onResult(long playlistId, String resultName) {
                Toast.makeText(MyPlaylistsActivity.this,
                        "已创建「" + resultName + "」", Toast.LENGTH_SHORT).show();
                // 新歌单已产生，旧快照失效，避免秒开时看不到它
                PlaylistCache.invalidateMyPlaylists(MyPlaylistsActivity.this);
                loadPlaylists();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MyPlaylistsActivity.this,
                        "创建失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int px(int baseValue) {
        return (int) (baseValue * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkImageLoader.cancelAll();
    }
}
