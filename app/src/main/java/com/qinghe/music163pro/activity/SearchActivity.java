package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.MvInfo;
import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;
import com.qinghe.music163pro.util.WatchConfirmDialog;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search activity - search songs, playlists and MVs.
 */
public class SearchActivity extends BaseWatchActivity {

    private static final String PREFS_NAME = "music163_settings";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY = 20;
    private static final int PAGE_SIZE = 20;

    private static final int TAB_SONGS = 0;
    private static final int TAB_PLAYLISTS = 1;
    private static final int TAB_MVS = 2;

    private EditText etSearch;
    private ListView lvSongs;
    private ListView lvPlaylists;
    private ListView lvMvs;
    private ListView lvHistory;
    private LinearLayout llSearchTabs;
    private TextView tabSongs;
    private TextView tabPlaylists;
    private TextView tabMvs;
    private ArrayAdapter<Song> songAdapter;
    private ArrayAdapter<PlaylistInfo> playlistAdapter;
    private ArrayAdapter<MvInfo> mvAdapter;
    private ArrayAdapter<String> historyAdapter;
    private final List<Song> songList = new ArrayList<>();
    private final List<PlaylistInfo> playlistList = new ArrayList<>();
    private final List<MvInfo> mvList = new ArrayList<>();
    private final List<String> historyList = new ArrayList<>();
    private MusicPlayerManager playerManager;
    private SharedPreferences prefs;

    private String currentKeyword = "";
    private int currentSongOffset = 0;
    private int currentPlaylistOffset = 0;
    private int currentMvOffset = 0;
    private boolean isLoadingMoreSongs = false;
    private boolean isLoadingMorePlaylists = false;
    private boolean isLoadingMoreMvs = false;
    private boolean hasMoreSongs = true;
    private boolean hasMorePlaylists = true;
    private boolean hasMoreMvs = true;
    private int currentTab = TAB_SONGS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = findViewById(R.id.et_search);
        lvSongs = findViewById(R.id.lv_songs);
        lvPlaylists = findViewById(R.id.lv_playlists);
        lvMvs = findViewById(R.id.lv_mvs);
        lvHistory = findViewById(R.id.lv_history);
        llSearchTabs = findViewById(R.id.ll_search_tabs);
        tabSongs = findViewById(R.id.tab_songs);
        tabPlaylists = findViewById(R.id.tab_playlists);
        tabMvs = findViewById(R.id.tab_mvs);
        TextView btnSearch = findViewById(R.id.btn_search);

        playerManager = MusicPlayerManager.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initAdapters();
        initListeners(btnSearch);
        loadSearchHistory();

        historyAdapter = new ArrayAdapter<>(this, R.layout.item_history, R.id.tv_history_text, historyList);
        lvHistory.setAdapter(historyAdapter);
        updateHistoryVisibility();
        updateTabAppearance();
    }

    private void initAdapters() {
        songAdapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, songList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Song song = getItem(position);
                if (song != null) {
                    TextView tvName = view.findViewById(R.id.tv_item_name);
                    TextView tvArtist = view.findViewById(R.id.tv_item_artist);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText(song.getName());
                    tvArtist.setText(song.getArtist());
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, song.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvSongs.setAdapter(songAdapter);

        playlistAdapter = new ArrayAdapter<PlaylistInfo>(this, R.layout.item_playlist, R.id.tv_playlist_name, playlistList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                PlaylistInfo playlistInfo = getItem(position);
                if (playlistInfo != null) {
                    TextView tvName = view.findViewById(R.id.tv_playlist_name);
                    TextView tvInfo = view.findViewById(R.id.tv_playlist_info);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText(playlistInfo.getName());
                    String info = playlistInfo.getTrackCount() + "首";
                    if (playlistInfo.getCreator() != null && !playlistInfo.getCreator().isEmpty()) {
                        info += " · " + playlistInfo.getCreator();
                    }
                    tvInfo.setText(info);
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, playlistInfo.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvPlaylists.setAdapter(playlistAdapter);

        mvAdapter = new ArrayAdapter<MvInfo>(this, R.layout.item_mv, R.id.tv_mv_name, mvList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                MvInfo mvInfo = getItem(position);
                if (mvInfo != null) {
                    TextView tvName = view.findViewById(R.id.tv_mv_name);
                    TextView tvInfo = view.findViewById(R.id.tv_mv_info);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText(mvInfo.getName());
                    StringBuilder info = new StringBuilder();
                    if (mvInfo.getArtist() != null && !mvInfo.getArtist().isEmpty()) {
                        info.append(mvInfo.getArtist());
                    }
                    if (mvInfo.getDurationMs() > 0) {
                        if (info.length() > 0) {
                            info.append(" · ");
                        }
                        info.append(formatDuration(mvInfo.getDurationMs()));
                    }
                    tvInfo.setText(info.length() > 0 ? info.toString() : getString(R.string.tap_to_view_detail));
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, mvInfo.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvMvs.setAdapter(mvAdapter);
    }

    private void initListeners(TextView btnSearch) {
        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            String keyword = historyList.get(position);
            etSearch.setText(keyword);
            etSearch.setSelection(keyword.length());
            doSearch();
        });

        lvHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            String keyword = historyList.get(position);
            showConfirmDialog("确认删除", "确定删除搜索记录「" + keyword + "」？", () -> {
                historyList.remove(position);
                saveSearchHistory();
                historyAdapter.notifyDataSetChanged();
                updateHistoryVisibility();
            });
            return true;
        });

        btnSearch.setOnClickListener(v -> doSearch());
        tabSongs.setOnClickListener(v -> switchTab(TAB_SONGS));
        tabPlaylists.setOnClickListener(v -> switchTab(TAB_PLAYLISTS));
        tabMvs.setOnClickListener(v -> switchTab(TAB_MVS));

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            Song song = songList.get(position);
            List<Song> playlist = new ArrayList<>(songList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        lvPlaylists.setOnItemClickListener((parent, view, position, id) -> {
            PlaylistInfo playlistInfo = playlistList.get(position);
            Intent intent = new Intent(this, PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", playlistInfo.getId());
            intent.putExtra("playlist_name", playlistInfo.getName());
            intent.putExtra("track_count", playlistInfo.getTrackCount());
            intent.putExtra("creator", playlistInfo.getCreator());
            intent.putExtra("creator_user_id", playlistInfo.getUserId());
            intent.putExtra("is_liked_playlist", playlistInfo.isLikedPlaylist());
            startActivity(intent);
        });

        lvMvs.setOnItemClickListener((parent, view, position, id) -> {
            MvInfo mvInfo = mvList.get(position);
            Intent intent = new Intent(this, MvDetailActivity.class);
            intent.putExtra("mv_id", mvInfo.getId());
            intent.putExtra("mv_name", mvInfo.getName());
            intent.putExtra("cookie", playerManager.getCookie());
            startActivity(intent);
        });

        lvSongs.setOnScrollListener(buildScrollListener(() -> {
            if (currentTab == TAB_SONGS && !isLoadingMoreSongs && hasMoreSongs) {
                loadMoreSongs();
            }
        }));

        lvPlaylists.setOnScrollListener(buildScrollListener(() -> {
            if (currentTab == TAB_PLAYLISTS && !isLoadingMorePlaylists && hasMorePlaylists) {
                loadMorePlaylists();
            }
        }));

        lvMvs.setOnScrollListener(buildScrollListener(() -> {
            if (currentTab == TAB_MVS && !isLoadingMoreMvs && hasMoreMvs) {
                loadMoreMvs();
            }
        }));
    }

    private AbsListView.OnScrollListener buildScrollListener(Runnable loadMoreAction) {
        return new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount) {
                    loadMoreAction.run();
                }
            }
        };
    }

    private void switchTab(int targetTab) {
        currentTab = targetTab;
        updateTabAppearance();
        showCurrentResultList();
        if (currentKeyword.isEmpty()) {
            updateHistoryVisibility();
            return;
        }
        if (currentTab == TAB_SONGS && songList.isEmpty()) {
            doSearchSongs();
        } else if (currentTab == TAB_PLAYLISTS && playlistList.isEmpty()) {
            doSearchPlaylists();
        } else if (currentTab == TAB_MVS && mvList.isEmpty()) {
            doSearchMvs();
        }
    }

    private void updateTabAppearance() {
        tabSongs.setTextColor(currentTab == TAB_SONGS ? 0xFFBB86FC : 0xB3FFFFFF);
        tabPlaylists.setTextColor(currentTab == TAB_PLAYLISTS ? 0xFFBB86FC : 0xB3FFFFFF);
        tabMvs.setTextColor(currentTab == TAB_MVS ? 0xFFBB86FC : 0xB3FFFFFF);
    }

    private void showCurrentResultList() {
        lvSongs.setVisibility(currentTab == TAB_SONGS && !songList.isEmpty() ? View.VISIBLE : View.GONE);
        lvPlaylists.setVisibility(currentTab == TAB_PLAYLISTS && !playlistList.isEmpty() ? View.VISIBLE : View.GONE);
        lvMvs.setVisibility(currentTab == TAB_MVS && !mvList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void doSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) {
            return;
        }

        currentKeyword = keyword;
        addToSearchHistory(keyword);

        llSearchTabs.setVisibility(View.VISIBLE);
        lvHistory.setVisibility(View.GONE);

        songList.clear();
        playlistList.clear();
        mvList.clear();
        songAdapter.notifyDataSetChanged();
        playlistAdapter.notifyDataSetChanged();
        mvAdapter.notifyDataSetChanged();
        showCurrentResultList();

        currentSongOffset = 0;
        currentPlaylistOffset = 0;
        currentMvOffset = 0;
        hasMoreSongs = true;
        hasMorePlaylists = true;
        hasMoreMvs = true;

        if (currentTab == TAB_SONGS) {
            doSearchSongs();
        } else if (currentTab == TAB_PLAYLISTS) {
            doSearchPlaylists();
        } else {
            doSearchMvs();
        }
    }

    private void doSearchSongs() {
        currentSongOffset = 0;
        hasMoreSongs = true;
        MusicApiHelper.searchSongs(currentKeyword, 0, playerManager.getCookie(), new MusicApiHelper.SearchCallback() {
            @Override
            public void onResult(List<Song> songs) {
                songList.clear();
                songList.addAll(songs);
                songAdapter.notifyDataSetChanged();
                currentSongOffset = songs.size();
                hasMoreSongs = songs.size() >= PAGE_SIZE;
                showCurrentResultList();
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

    private void doSearchPlaylists() {
        currentPlaylistOffset = 0;
        hasMorePlaylists = true;
        MusicApiHelper.searchPlaylists(currentKeyword, 0, playerManager.getCookie(), new MusicApiHelper.SearchPlaylistCallback() {
            @Override
            public void onResult(List<PlaylistInfo> playlists) {
                playlistList.clear();
                playlistList.addAll(playlists);
                playlistAdapter.notifyDataSetChanged();
                currentPlaylistOffset = playlists.size();
                hasMorePlaylists = playlists.size() >= PAGE_SIZE;
                showCurrentResultList();
                if (playlists.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "暂无歌单", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doSearchMvs() {
        currentMvOffset = 0;
        hasMoreMvs = true;
        MusicApiHelper.searchMvs(currentKeyword, 0, playerManager.getCookie(), new MusicApiHelper.SearchMvCallback() {
            @Override
            public void onResult(List<MvInfo> mvs) {
                mvList.clear();
                mvList.addAll(mvs);
                mvAdapter.notifyDataSetChanged();
                currentMvOffset = mvs.size();
                hasMoreMvs = mvs.size() >= PAGE_SIZE;
                showCurrentResultList();
                if (mvs.isEmpty()) {
                    Toast.makeText(SearchActivity.this, R.string.no_mv, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMoreSongs() {
        if (currentKeyword.isEmpty() || isLoadingMoreSongs || !hasMoreSongs) {
            return;
        }
        isLoadingMoreSongs = true;
        MusicApiHelper.searchSongs(currentKeyword, currentSongOffset, playerManager.getCookie(), new MusicApiHelper.SearchCallback() {
            @Override
            public void onResult(List<Song> songs) {
                isLoadingMoreSongs = false;
                if (songs.isEmpty()) {
                    hasMoreSongs = false;
                    return;
                }
                songList.addAll(songs);
                songAdapter.notifyDataSetChanged();
                currentSongOffset += songs.size();
                hasMoreSongs = songs.size() >= PAGE_SIZE;
            }

            @Override
            public void onError(String message) {
                isLoadingMoreSongs = false;
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMorePlaylists() {
        if (currentKeyword.isEmpty() || isLoadingMorePlaylists || !hasMorePlaylists) {
            return;
        }
        isLoadingMorePlaylists = true;
        MusicApiHelper.searchPlaylists(currentKeyword, currentPlaylistOffset, playerManager.getCookie(), new MusicApiHelper.SearchPlaylistCallback() {
            @Override
            public void onResult(List<PlaylistInfo> playlists) {
                isLoadingMorePlaylists = false;
                if (playlists.isEmpty()) {
                    hasMorePlaylists = false;
                    return;
                }
                playlistList.addAll(playlists);
                playlistAdapter.notifyDataSetChanged();
                currentPlaylistOffset += playlists.size();
                hasMorePlaylists = playlists.size() >= PAGE_SIZE;
            }

            @Override
            public void onError(String message) {
                isLoadingMorePlaylists = false;
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMoreMvs() {
        if (currentKeyword.isEmpty() || isLoadingMoreMvs || !hasMoreMvs) {
            return;
        }
        isLoadingMoreMvs = true;
        MusicApiHelper.searchMvs(currentKeyword, currentMvOffset, playerManager.getCookie(), new MusicApiHelper.SearchMvCallback() {
            @Override
            public void onResult(List<MvInfo> mvs) {
                isLoadingMoreMvs = false;
                if (mvs.isEmpty()) {
                    hasMoreMvs = false;
                    return;
                }
                mvList.addAll(mvs);
                mvAdapter.notifyDataSetChanged();
                currentMvOffset += mvs.size();
                hasMoreMvs = mvs.size() >= PAGE_SIZE;
            }

            @Override
            public void onError(String message) {
                isLoadingMoreMvs = false;
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void loadSearchHistory() {
        historyList.clear();
        String json = prefs.getString(KEY_SEARCH_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                historyList.add(arr.getString(i));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveSearchHistory() {
        JSONArray arr = new JSONArray();
        for (String keyword : historyList) {
            arr.put(keyword);
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
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
    }

    private void updateHistoryVisibility() {
        boolean noSearchResults = songList.isEmpty() && playlistList.isEmpty() && mvList.isEmpty();
        if (noSearchResults && !historyList.isEmpty() && currentKeyword.isEmpty()) {
            lvHistory.setVisibility(View.VISIBLE);
            lvSongs.setVisibility(View.GONE);
            lvPlaylists.setVisibility(View.GONE);
            lvMvs.setVisibility(View.GONE);
            llSearchTabs.setVisibility(View.GONE);
        } else {
            lvHistory.setVisibility(View.GONE);
        }
    }

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF1E1E1E, 0xFFBB86FC, true));
    }
}
