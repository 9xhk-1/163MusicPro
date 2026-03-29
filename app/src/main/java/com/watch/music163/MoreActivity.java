package com.watch.music163;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class MoreActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY = 20;

    private EditText etSearch;
    private ListView lvSongs;
    private ListView lvHistory;
    private TextView tabSearch;
    private TextView tabFavorites;
    private ArrayAdapter<Song> adapter;
    private ArrayAdapter<String> historyAdapter;
    private final List<Song> displayList = new ArrayList<>();
    private final List<String> historyList = new ArrayList<>();
    private boolean showingFavorites = false;
    private FavoritesManager favoritesManager;
    private MusicPlayerManager playerManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);

        etSearch = findViewById(R.id.et_search);
        lvSongs = findViewById(R.id.lv_songs);
        lvHistory = findViewById(R.id.lv_history);
        tabSearch = findViewById(R.id.tab_search);
        tabFavorites = findViewById(R.id.tab_favorites);
        TextView btnSearch = findViewById(R.id.btn_search);
        TextView tabSettings = findViewById(R.id.tab_settings);

        favoritesManager = new FavoritesManager(this);
        playerManager = MusicPlayerManager.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, displayList) {
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
        lvSongs.setAdapter(adapter);

        // Set up search history
        loadSearchHistory();
        historyAdapter = new ArrayAdapter<>(this, R.layout.item_history, R.id.tv_history_text, historyList);
        lvHistory.setAdapter(historyAdapter);
        updateHistoryVisibility();

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            String keyword = historyList.get(position);
            etSearch.setText(keyword);
            etSearch.setSelection(keyword.length());
            doSearch();
        });

        lvHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            historyList.remove(position);
            saveSearchHistory();
            historyAdapter.notifyDataSetChanged();
            updateHistoryVisibility();
            return true;
        });

        btnSearch.setOnClickListener(v -> doSearch());

        tabSearch.setOnClickListener(v -> switchToSearch());
        tabFavorites.setOnClickListener(v -> switchToFavorites());
        tabSettings.setOnClickListener(v ->
                startActivity(new Intent(MoreActivity.this, SettingsActivity.class)));

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            finish();
        });
    }

    private void doSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) return;

        addToSearchHistory(keyword);
        switchToSearch();
        String cookie = playerManager.getCookie();
        MusicApiHelper.searchSongs(keyword, cookie, new MusicApiHelper.SearchCallback() {
            @Override
            public void onResult(List<Song> songs) {
                displayList.clear();
                displayList.addAll(songs);
                adapter.notifyDataSetChanged();
                lvSongs.setVisibility(View.VISIBLE);
                lvHistory.setVisibility(View.GONE);
                if (songs.isEmpty()) {
                    Toast.makeText(MoreActivity.this, R.string.no_song, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MoreActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchToSearch() {
        showingFavorites = false;
        tabSearch.setBackgroundColor(getResources().getColor(R.color.colorPrimary, getTheme()));
        tabSearch.setTextColor(getResources().getColor(R.color.white, getTheme()));
        tabFavorites.setBackgroundColor(getResources().getColor(R.color.bg_dark, getTheme()));
        tabFavorites.setTextColor(getResources().getColor(R.color.gray_text, getTheme()));
        updateHistoryVisibility();
    }

    private void switchToFavorites() {
        showingFavorites = true;
        tabFavorites.setBackgroundColor(getResources().getColor(R.color.colorPrimary, getTheme()));
        tabFavorites.setTextColor(getResources().getColor(R.color.white, getTheme()));
        tabSearch.setBackgroundColor(getResources().getColor(R.color.bg_dark, getTheme()));
        tabSearch.setTextColor(getResources().getColor(R.color.gray_text, getTheme()));

        displayList.clear();
        displayList.addAll(favoritesManager.getFavorites());
        adapter.notifyDataSetChanged();
        lvSongs.setVisibility(View.VISIBLE);
        lvHistory.setVisibility(View.GONE);
    }

    private void loadSearchHistory() {
        historyList.clear();
        String json = prefs.getString(KEY_SEARCH_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                historyList.add(arr.getString(i));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void saveSearchHistory() {
        JSONArray arr = new JSONArray();
        for (String s : historyList) {
            arr.put(s);
        }
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply();
    }

    private void addToSearchHistory(String keyword) {
        historyList.remove(keyword);
        historyList.add(0, keyword);
        if (historyList.size() > MAX_HISTORY) {
            historyList.remove(historyList.size() - 1);
        }
        saveSearchHistory();
        historyAdapter.notifyDataSetChanged();
    }

    private void updateHistoryVisibility() {
        if (!showingFavorites && displayList.isEmpty() && !historyList.isEmpty()) {
            lvHistory.setVisibility(View.VISIBLE);
            lvSongs.setVisibility(View.GONE);
        } else {
            lvHistory.setVisibility(View.GONE);
            lvSongs.setVisibility(View.VISIBLE);
        }
    }
}
