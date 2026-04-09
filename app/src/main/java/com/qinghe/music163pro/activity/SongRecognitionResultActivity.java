package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

public class SongRecognitionResultActivity extends BaseWatchActivity {

    public static final String EXTRA_RESULTS = "recognition_results";

    private final List<Song> resultSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_recognition_result);

        TextView tvSubtitle = findViewById(R.id.tv_result_subtitle);
        TextView tvEmpty = findViewById(R.id.tv_result_empty);
        ListView lvResults = findViewById(R.id.lv_result_songs);

        Object extra = getIntent().getSerializableExtra(EXTRA_RESULTS);
        if (extra instanceof ArrayList) {
            ArrayList<?> list = (ArrayList<?>) extra;
            for (Object item : list) {
                if (item instanceof Song) {
                    resultSongs.add((Song) item);
                }
            }
        }

        tvSubtitle.setText("共识别到 " + resultSongs.size() + " 首歌曲");

        ArrayAdapter<Song> adapter = new ArrayAdapter<Song>(this, R.layout.item_song,
                R.id.tv_item_name, resultSongs) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    tvName.setText(song.getName());
                    String artist = song.getArtist();
                    if (song.getAlbum() != null && !song.getAlbum().isEmpty()) {
                        artist += " · " + song.getAlbum();
                    }
                    tvArtist.setText(artist);
                }
                return view;
            }
        };
        lvResults.setAdapter(adapter);
        lvResults.setEmptyView(tvEmpty);

        lvResults.setOnItemClickListener((parent, view, position, id) -> {
            MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
            playerManager.setPlaylist(new ArrayList<>(resultSongs), position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
