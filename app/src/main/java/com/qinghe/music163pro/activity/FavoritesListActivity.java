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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.util.WatchConfirmDialog;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.FavoritesManager;
import com.qinghe.music163pro.manager.PlaylistManager;
import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Favorites list activity - shows favorited songs and playlists.
 * Supports both local and cloud modes, with tabs for songs and playlists.
 */
public class FavoritesListActivity extends BaseWatchActivity {

    private final List<Song> favoritesList = new ArrayList<>();
    private final List<PlaylistInfo> playlistsList = new ArrayList<>();
    private ArrayAdapter<Song> songAdapter;
    private ArrayAdapter<PlaylistInfo> playlistAdapter;
    private FavoritesManager favoritesManager;
    private PlaylistManager playlistManager;
    private MusicPlayerManager playerManager;
    private TextView tvEmpty;
    private ListView lvFavorites;
    private ListView lvFavPlaylists;
    private TextView tabFavSongs;
    private TextView tabFavPlaylists;

    private boolean isSongTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites_list);

        lvFavorites = findViewById(R.id.lv_favorites);
        lvFavPlaylists = findViewById(R.id.lv_fav_playlists);
        tvEmpty = findViewById(R.id.tv_empty);
        tabFavSongs = findViewById(R.id.tab_fav_songs);
        tabFavPlaylists = findViewById(R.id.tab_fav_playlists);

        favoritesManager = new FavoritesManager(this);
        playlistManager = new PlaylistManager();
        playerManager = MusicPlayerManager.getInstance();

        // Song adapter
        songAdapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, favoritesList) {
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
        lvFavorites.setAdapter(songAdapter);

        // Playlist adapter
        playlistAdapter = new ArrayAdapter<PlaylistInfo>(this, R.layout.item_playlist, R.id.tv_playlist_name, playlistsList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                PlaylistInfo pl = getItem(position);
                if (pl != null) {
                    TextView tvName = view.findViewById(R.id.tv_playlist_name);
                    TextView tvInfo = view.findViewById(R.id.tv_playlist_info);
                    ImageView ivCover = view.findViewById(R.id.iv_cover);
                    tvName.setText(pl.getName());
                    String info = pl.getTrackCount() + "\u9996";
                    if (pl.getCreator() != null && !pl.getCreator().isEmpty()) {
                        info += " \u00b7 " + pl.getCreator();
                    }
                    tvInfo.setText(info);
                    if (ivCover != null) {
                        NetworkImageLoader.load(ivCover, pl.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvFavPlaylists.setAdapter(playlistAdapter);

        // Tab click handlers
        tabFavSongs.setOnClickListener(v -> switchToSongTab());
        tabFavPlaylists.setOnClickListener(v -> switchToPlaylistTab());

        lvFavorites.setOnItemClickListener((parent, view, position, id) -> {
            Song song = favoritesList.get(position);
            List<Song> playlist = new ArrayList<>(favoritesList);
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        lvFavorites.setOnItemLongClickListener((parent, view, position, id) -> {
            Song song = favoritesList.get(position);
            showUnfavoriteDialog(song, position);
            return true;
        });

        lvFavPlaylists.setOnItemClickListener((parent, view, position, id) -> {
            PlaylistInfo pl = playlistsList.get(position);
            Intent intent = new Intent(this, PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", pl.getId());
            intent.putExtra("playlist_name", pl.getName());
            intent.putExtra("track_count", pl.getTrackCount());
            intent.putExtra("creator", pl.getCreator());
            intent.putExtra("creator_user_id", pl.getUserId());
            intent.putExtra("is_liked_playlist", pl.isLikedPlaylist());
            startActivity(intent);
        });

        // Long press to delete playlist from local (with confirmation)
        lvFavPlaylists.setOnItemLongClickListener((parent, view, position, id) -> {
            SharedPreferences settingsPrefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
            boolean isCloud = settingsPrefs.getBoolean("fav_mode_cloud", false);
            if (!isCloud) {
                PlaylistInfo pl = playlistsList.get(position);
                showConfirmDialog("确认删除", "确定删除歌单「" + pl.getName() + "」？", () -> {
                    playlistManager.removePlaylist(pl.getId());
                    playlistsList.remove(position);
                    playlistAdapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(this, "\u5df2\u5220\u9664\u6b4c\u5355", Toast.LENGTH_SHORT).show();
                });
            }
            return true;
        });

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void switchToSongTab() {
        isSongTab = true;
        // active tab: text color only
        tabFavSongs.setTextColor(0xFFBB86FC);
        //
        tabFavPlaylists.setTextColor(0xB3FFFFFF);
        lvFavPlaylists.setVisibility(View.GONE);
        loadData();
    }

    private void switchToPlaylistTab() {
        isSongTab = false;
        // active tab: text color only
        tabFavPlaylists.setTextColor(0xFFBB86FC);
        //
        tabFavSongs.setTextColor(0xB3FFFFFF);
        lvFavorites.setVisibility(View.GONE);
        loadPlaylists();
    }

    private void loadData() {
        if (isSongTab) {
            loadFavorites();
        } else {
            loadPlaylists();
        }
    }

    private void loadFavorites() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);

        if (isCloud) {
            loadCloudFavorites();
        } else {
            loadLocalFavorites();
        }
    }

    private void loadLocalFavorites() {
        // First, load from local storage immediately for fast display
        favoritesList.clear();
        favoritesList.addAll(favoritesManager.getFavorites());
        songAdapter.notifyDataSetChanged();
        updateEmptyState();

        // Then, fetch fresh details (including coverUrls) from API in background
        List<Long> ids = favoritesManager.getFavoriteIds();
        if (ids.isEmpty()) return;
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) return;

        MusicApiHelper.fetchSongsDetails(ids, cookie, new MusicApiHelper.BatchSongDetailsCallback() {
            @Override
            public void onResult(java.util.Map<Long, Song> songMap) {
                if (songMap.isEmpty()) return;
                // Build local lookup map for O(1) fallback access
                java.util.Map<Long, Song> localMap = new java.util.HashMap<>();
                for (Song ls : favoritesList) localMap.put(ls.getId(), ls);
                // Rebuild the list preserving original order
                List<Song> updated = new ArrayList<>();
                for (Long id : ids) {
                    Song s = songMap.get(id);
                    if (s != null) {
                        updated.add(s);
                    } else {
                        // Keep local song if API didn't return it
                        Song local = localMap.get(id);
                        if (local != null) updated.add(local);
                    }
                }
                favoritesList.clear();
                favoritesList.addAll(updated);
                songAdapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                // Keep local data displayed, silently ignore error
            }
        });
    }

    private void loadCloudFavorites() {
        favoritesList.clear();
        songAdapter.notifyDataSetChanged();
        tvEmpty.setText("\u6b63\u5728\u52a0\u8f7d\u4e91\u7aef\u6536\u85cf...");
        tvEmpty.setVisibility(View.VISIBLE);
        lvFavorites.setVisibility(View.GONE);

        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            tvEmpty.setText("\u8bf7\u5148\u767b\u5f55\u4ee5\u4f7f\u7528\u4e91\u7aef\u6536\u85cf");
            return;
        }

        MusicApiHelper.getCloudFavorites(cookie, new MusicApiHelper.CloudFavoritesCallback() {
            @Override
            public void onResult(List<Song> songs) {
                favoritesList.clear();
                favoritesList.addAll(songs);
                songAdapter.notifyDataSetChanged();
                tvEmpty.setText("\u6682\u65e0\u4e91\u7aef\u6536\u85cf");
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                tvEmpty.setText("\u52a0\u8f7d\u5931\u8d25: " + message);
                tvEmpty.setVisibility(View.VISIBLE);
                lvFavorites.setVisibility(View.GONE);
            }
        });
    }

    private void loadPlaylists() {
        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);

        if (isCloud) {
            loadCloudPlaylists();
        } else {
            loadLocalPlaylists();
        }
    }

    private void loadLocalPlaylists() {
        playlistsList.clear();
        playlistsList.addAll(playlistManager.getPlaylists());
        playlistAdapter.notifyDataSetChanged();
        updateEmptyState();

        // Refresh track counts, creator info, and coverUrl from API for each locally saved playlist
        String cookie = playerManager.getCookie();
        if (cookie != null && !cookie.isEmpty()) {
            for (int i = 0; i < playlistsList.size(); i++) {
                final long plId = playlistsList.get(i).getId();
                MusicApiHelper.getPlaylistMetaWithCover(plId, cookie, new MusicApiHelper.PlaylistMetaWithCoverCallback() {
                    @Override
                    public void onResult(int trackCount, String creator, long creatorUserId,
                                         int specialType, boolean subscribed, String coverUrl) {
                        for (int j = 0; j < playlistsList.size(); j++) {
                            if (playlistsList.get(j).getId() == plId) {
                                PlaylistInfo updated = playlistsList.get(j);
                                updated.setTrackCount(trackCount);
                                if (creator != null && !creator.isEmpty()) {
                                    updated.setCreator(creator);
                                }
                                if (coverUrl != null && !coverUrl.isEmpty()) {
                                    updated.setCoverUrl(coverUrl);
                                }
                                playlistAdapter.notifyDataSetChanged();
                                playlistManager.updatePlaylistMeta(plId, trackCount, creator);
                                break;
                            }
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Silently ignore - display local data as fallback
                    }
                });
            }
        }
    }

    private void loadCloudPlaylists() {
        playlistsList.clear();
        playlistAdapter.notifyDataSetChanged();
        tvEmpty.setText("\u6b63\u5728\u52a0\u8f7d\u4e91\u7aef\u6b4c\u5355...");
        tvEmpty.setVisibility(View.VISIBLE);
        lvFavPlaylists.setVisibility(View.GONE);

        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            tvEmpty.setText("\u8bf7\u5148\u767b\u5f55\u4ee5\u4f7f\u7528\u4e91\u7aef\u6b4c\u5355");
            return;
        }

        MusicApiHelper.getUserPlaylists(cookie, new MusicApiHelper.UserPlaylistsCallback() {
            @Override
            public void onResult(List<PlaylistInfo> playlists) {
                playlistsList.clear();
                playlistsList.addAll(playlists);
                playlistAdapter.notifyDataSetChanged();
                tvEmpty.setText("\u6682\u65e0\u4e91\u7aef\u6b4c\u5355");
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                tvEmpty.setText("\u52a0\u8f7d\u5931\u8d25: " + message);
                tvEmpty.setVisibility(View.VISIBLE);
                lvFavPlaylists.setVisibility(View.GONE);
            }
        });
    }

    private void updateEmptyState() {
        if (isSongTab) {
            if (favoritesList.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                lvFavorites.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                lvFavorites.setVisibility(View.VISIBLE);
            }
            lvFavPlaylists.setVisibility(View.GONE);
        } else {
            if (playlistsList.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                lvFavPlaylists.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                lvFavPlaylists.setVisibility(View.VISIBLE);
            }
            lvFavorites.setVisibility(View.GONE);
        }
    }
    private void showUnfavoriteDialog(Song song, int position) {
        showConfirmDialog("取消收藏", "确定取消收藏「" + song.getName() + "」？", () -> {
            SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
            boolean isCloud = prefs.getBoolean("fav_mode_cloud", false);
            if (isCloud) {
                String cookie = playerManager.getCookie();
                if (cookie == null || cookie.isEmpty()) {
                    Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                MusicApiHelper.likeTrack(song.getId(), false, cookie,
                        new MusicApiHelper.LikeCallback() {
                    @Override
                    public void onResult(boolean success) {
                        if (success) {
                            favoritesList.remove(position);
                            songAdapter.notifyDataSetChanged();
                            updateEmptyState();
                            Toast.makeText(FavoritesListActivity.this, "已取消云端收藏", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(FavoritesListActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(FavoritesListActivity.this, "操作失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                favoritesManager.removeFavorite(song);
                favoritesList.remove(position);
                songAdapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Show a confirmation dialog adapted for watch (360x320 px screen).
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF1E1E1E, 0xFFBB86FC, true));
    }
}
