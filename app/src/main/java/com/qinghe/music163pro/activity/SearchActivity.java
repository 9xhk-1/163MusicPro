package com.qinghe.music163pro.activity;

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

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Search activity - search songs and play from results.
 */
public class SearchActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY = 20;

    private EditText etSearch;
    private ListView lvSongs;
    private ListView lvHistory;
    private ArrayAdapter<Song> adapter;
    private ArrayAdapter<String> historyAdapter;
    private final List<Song> displayList = new ArrayList<>();
    private final List<String> historyList = new ArrayList<>();
    private MusicPlayerManager playerManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = findViewById(R.id.et_search);
        lvSongs = findViewById(R.id.lv_songs);
        lvHistory = findViewById(R.id.lv_history);
        TextView btnSearch = findViewById(R.id.btn_search);

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

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            Song song = displayList.get(position);
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            // Navigate back to MainActivity (player screen)
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void doSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) return;

        addToSearchHistory(keyword);
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
                    Toast.makeText(SearchActivity.this, R.string.no_song, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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
        if (displayList.isEmpty() && !historyList.isEmpty()) {
            lvHistory.setVisibility(View.VISIBLE);
            lvSongs.setVisibility(View.GONE);
        } else {
            lvHistory.setVisibility(View.GONE);
            lvSongs.setVisibility(View.VISIBLE);
        }
    }
}
