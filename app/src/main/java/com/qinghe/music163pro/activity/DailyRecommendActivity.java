package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Daily recommend songs screen.
 */
public class DailyRecommendActivity extends BaseWatchActivity {

    private final List<Song> songs = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private TextView tvStatus;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_recommend);

        tvStatus = findViewById(R.id.tv_status);
        listView = findViewById(R.id.lv_daily_songs);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, songs) {
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
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> playFromDailyRecommend(position));

        loadDailyRecommend();
    }

    private void loadDailyRecommend() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            tvStatus.setText("请先登录");
            listView.setVisibility(View.GONE);
            return;
        }
        tvStatus.setText("正在加载...");
        MusicApiHelper.getDailyRecommendSongs(cookie, new MusicApiHelper.SearchCallback() {
            @Override
            public void onResult(List<Song> result) {
                songs.clear();
                songs.addAll(result);
                adapter.notifyDataSetChanged();
                tvStatus.setText(result.isEmpty() ? "暂无推荐歌曲" : result.size() + " 首歌曲");
                listView.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("加载失败: " + message);
                listView.setVisibility(View.GONE);
            }
        });
    }

    private void playFromDailyRecommend(int index) {
        if (songs.isEmpty() || index < 0 || index >= songs.size()) {
            return;
        }
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        playerManager.setPlaylist(new ArrayList<>(songs), index);
        playerManager.playCurrent();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
