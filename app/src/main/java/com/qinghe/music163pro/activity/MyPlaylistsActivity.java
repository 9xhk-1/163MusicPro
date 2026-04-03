package com.qinghe.music163pro.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.player.MusicPlayerManager;

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
        root.setBackgroundColor(0xFF212121);
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title row with "+" button
        android.widget.RelativeLayout titleRow = new android.widget.RelativeLayout(this);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("我的歌单");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
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
        TextView btnCreate = new TextView(this);
        btnCreate.setText("＋");
        btnCreate.setTextColor(0xFFFF5252);
        btnCreate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
        btnCreate.setGravity(Gravity.CENTER);
        btnCreate.setPadding(px(4), 0, px(4), 0);
        btnCreate.setClickable(true);
        btnCreate.setFocusable(true);
        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(
                px(28), px(28));
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        btnParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnCreate.setLayoutParams(btnParams);
        btnCreate.setOnClickListener(v -> showCreatePlaylistDialog());
        titleRow.addView(btnCreate);

        root.addView(titleRow);

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setText("正在加载...");
        tvStatus.setTextColor(0xFFAAAAAA);
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
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
                android.R.layout.simple_list_item_2, android.R.id.text1, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                PlaylistInfo pl = getItem(position);
                if (pl != null) {
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);
                    String prefix = "";
                    if (pl.isLikedPlaylist()) {
                        prefix = "♥ ";
                    } else if (pl.getUserId() == currentUserId) {
                        prefix = "📝 ";
                    } else {
                        prefix = "📋 ";
                    }
                    text1.setText(prefix + pl.getName());
                    text1.setTextColor(0xFFFFFFFF);
                    text1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
                    text1.setSingleLine(true);
                    text1.setEllipsize(android.text.TextUtils.TruncateAt.END);

                    String detail = pl.getTrackCount() + "首";
                    if (pl.getCreator() != null && !pl.getCreator().isEmpty()) {
                        detail += " · " + pl.getCreator();
                    }
                    text2.setText(detail);
                    text2.setTextColor(0xFF888888);
                    text2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(11));
                }
                view.setBackgroundColor(0xFF2A2A2A);
                view.setPadding(px(8), px(4), px(8), px(4));
                return view;
            }
        };
        lvPlaylists.setAdapter(adapter);

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
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            tvStatus.setText("请先登录");
            return;
        }

        MusicApiHelper.getUserPlaylists(cookie, new MusicApiHelper.UserPlaylistsCallback() {
            @Override
            public void onResult(List<PlaylistInfo> playlists) {
                displayList.clear();
                displayList.addAll(playlists);
                // Detect current user ID from the first playlist's creator (if it's their "liked" playlist)
                if (!playlists.isEmpty() && playlists.get(0).isLikedPlaylist()) {
                    currentUserId = playlists.get(0).getUserId();
                }
                adapter.notifyDataSetChanged();
                if (playlists.isEmpty()) {
                    tvStatus.setText("暂无歌单");
                } else {
                    tvStatus.setText(playlists.size() + " 个歌单");
                    lvPlaylists.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("加载失败: " + message);
            }
        });
    }

    private void showCreatePlaylistDialog() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("输入歌单名称");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        input.setPadding(px(8), px(4), px(8), px(4));
        input.setBackgroundColor(0xFF333333);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(px(12), px(8), px(12), px(4));
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
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
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
