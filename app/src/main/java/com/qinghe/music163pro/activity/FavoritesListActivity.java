package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.manager.FavoritesManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorites list activity - shows all favorited songs.
 */
public class FavoritesListActivity extends AppCompatActivity {

    private final List<Song> favoritesList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private FavoritesManager favoritesManager;
    private MusicPlayerManager playerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites_list);

        // Apply keep screen on setting
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        ListView lvFavorites = findViewById(R.id.lv_favorites);
        TextView tvEmpty = findViewById(R.id.tv_empty);

        favoritesManager = new FavoritesManager(this);
        playerManager = MusicPlayerManager.getInstance();

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, favoritesList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    tvName.setText(song.getName());
                    tvArtist.setText(song.getArtist());
                }
                return view;
            }
        };
        lvFavorites.setAdapter(adapter);

        lvFavorites.setOnItemClickListener((parent, view, position, id) -> {
            Song song = favoritesList.get(position);
            List<Song> playlist = new ArrayList<>(favoritesList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            // Navigate back to MainActivity (player screen)
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        loadFavorites();

        if (favoritesList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvFavorites.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvFavorites.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites() {
        favoritesList.clear();
        favoritesList.addAll(favoritesManager.getFavorites());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
