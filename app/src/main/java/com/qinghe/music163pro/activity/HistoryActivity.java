package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.HistoryManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * History activity - shows cloud play history (recent records), newest first.
 * Designed for watch screen (320x360 dpi).
 */
public class HistoryActivity extends BaseWatchActivity {

    private ListView lvHistory;
    private final List<Song> displayList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private MusicPlayerManager playerManager;
    private TextView tvEmpty;
    private int loadSeq = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playerManager = MusicPlayerManager.getInstance();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title bar
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(this);
        titleBar.setPadding(0, 0, 0, px(6));
        root.addView(titleBar);

        TextView title = new TextView(this);
        title.setText("历史记录");
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        tvEmpty = new TextView(this);
        tvEmpty.setText("暂无播放记录");
        tvEmpty.setTextColor(getResources().getColor(R.color.text_secondary));
        tvEmpty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, px(40), 0, 0);
        tvEmpty.setVisibility(View.GONE);
        root.addView(tvEmpty);

        lvHistory = new ListView(this);
        lvHistory.setDividerHeight(1);
        lvHistory.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvHistory.setLayoutParams(lvParams);
        root.addView(lvHistory);

        setContentView(root);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.item_song, parent, false);
                }
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setSelected(true);
                    tvArtist.setSelected(true);
                    View vipBadge = view.findViewById(R.id.tv_vip_badge);
                    if (vipBadge != null) {
                        vipBadge.setVisibility(song.isVip() ? View.VISIBLE : View.GONE);
                    }
                    if (!android.text.TextUtils.equals(tvName.getText(), song.getDisplayName())) {
                        tvName.setText(song.getDisplayName());
                    }
                    if (!android.text.TextUtils.equals(tvArtist.getText(), song.getArtist())) {
                        tvArtist.setText(song.getArtist());
                    }
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, song.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvHistory.setAdapter(adapter);
        NetworkImageLoader.attachListViewOptimization(lvHistory, adapter, position ->
                position >= 0 && position < displayList.size()
                        ? displayList.get(position).getCoverUrl() : null);

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        final int seq = ++loadSeq;
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            applyHistory(HistoryManager.getInstance().getHistory(), false);
            return;
        }
        tvEmpty.setText("加载中...");
        tvEmpty.setVisibility(View.VISIBLE);
        lvHistory.setVisibility(View.GONE);
        MusicApiHelper.fetchCloudHistory(cookie, new MusicApiHelper.CloudHistoryCallback() {
            @Override
            public void onResult(List<Song> songs) {
                if (seq != loadSeq) return;
                applyHistory(songs, true);
            }

            @Override
            public void onError(String message) {
                if (seq != loadSeq) return;
                applyHistory(HistoryManager.getInstance().getHistory(), false);
            }
        });
    }

    private void applyHistory(List<Song> songs, boolean fromCloud) {
        displayList.clear();
        displayList.addAll(songs);
        adapter.notifyDataSetChanged();
        tvEmpty.setText("暂无播放记录");

        if (displayList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvHistory.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvHistory.setVisibility(View.VISIBLE);
        }

        // Local fallback entries lack covers; refresh them in background
        if (!fromCloud && !displayList.isEmpty()) {
            String cookie = playerManager.getCookie();
            if (cookie != null && !cookie.isEmpty()) {
                List<Long> ids = new ArrayList<>();
                for (Song s : displayList) {
                    ids.add(s.getId());
                }
                MusicApiHelper.fetchSongsDetails(ids, cookie,
                        new MusicApiHelper.BatchSongDetailsCallback() {
                    @Override
                    public void onResult(java.util.Map<Long, Song> songMap) {
                        if (songMap.isEmpty()) return;
                        for (int i = 0; i < displayList.size(); i++) {
                            Song local = displayList.get(i);
                            Song fresh = songMap.get(local.getId());
                            if (fresh != null && fresh.getCoverUrl() != null && !fresh.getCoverUrl().isEmpty()) {
                                local.setCoverUrl(fresh.getCoverUrl());
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(String message) {
                        // Keep local data, silently ignore
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkImageLoader.cancelAll();
    }
}
