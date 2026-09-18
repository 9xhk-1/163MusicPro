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
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.util.NetworkImageLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Download list activity - shows all downloaded songs from /sdcard/163Music/Download/
 * Long press to delete with confirmation dialog.
 */
public class DownloadListActivity extends BaseWatchActivity {

    private final List<Song> downloadedSongs = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private ListView lvDownloads;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);

        // Apply keep screen on setting

        lvDownloads = findViewById(R.id.lv_downloads);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new ArrayAdapter<Song>(this, R.layout.item_song, R.id.tv_item_name, downloadedSongs) {
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
                        // coverUrl may be local file:// path or remote URL
                        NetworkImageLoader.load(ivCover, song.getCoverUrl());
                    }
                }
                return view;
            }
        };
        lvDownloads.setAdapter(adapter);

        lvDownloads.setOnItemClickListener((parent, view, position, id) -> {
            playDownloadedSong(position);
        });

        lvDownloads.setOnItemLongClickListener((parent, view, position, id) -> {
            Song song = downloadedSongs.get(position);
            showConfirmDialog("确认删除", "确定删除「" + song.getName() + "」？\n文件将被永久删除。", () -> {
                boolean deleted = DownloadManager.deleteDownload(song);
                if (deleted) {
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    loadDownloads();
                    updateEmptyState();
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        });

        loadDownloads();
        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
        updateEmptyState();
    }

    private void loadDownloads() {
        downloadedSongs.clear();

        // Load from new subfolder format (with info.json)
        List<File> songDirs = DownloadManager.getDownloadedSongDirs();
        for (File dir : songDirs) {
            Song song = DownloadManager.loadSongInfo(dir);
            if (song != null) {
                song.setForceLocalPlayback(true);
                downloadedSongs.add(song);
            }
        }

        // Load legacy flat .mp3 files (backward compatibility)
        List<File> legacyFiles = DownloadManager.getDownloadedFiles();
        for (File file : legacyFiles) {
            Song song = fileToSong(file);
            downloadedSongs.add(song);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // Fetch covers from network for songs without covers (legacy downloads)
        fetchMissingCovers();
    }

    /**
     * For songs in the download list that don't have a cover URL,
     * fetch their details from the network to get cover URLs.
     */
    private void fetchMissingCovers() {
        List<Long> missingIds = new ArrayList<>();
        for (Song song : downloadedSongs) {
            if (song.getId() > 0 && (song.getCoverUrl() == null || song.getCoverUrl().isEmpty())) {
                missingIds.add(song.getId());
            }
        }
        if (missingIds.isEmpty()) return;

        MusicPlayerManager pm = MusicPlayerManager.getInstance();
        String cookie = pm.getCookie();
        MusicApiHelper.fetchSongsDetails(missingIds, cookie,
                new MusicApiHelper.BatchSongDetailsCallback() {
                    @Override
                    public void onResult(Map<Long, Song> songMap) {
                        boolean changed = false;
                        for (Song song : downloadedSongs) {
                            if (song.getId() > 0 && (song.getCoverUrl() == null || song.getCoverUrl().isEmpty())) {
                                Song detail = songMap.get(song.getId());
                                if (detail != null && detail.getCoverUrl() != null && !detail.getCoverUrl().isEmpty()) {
                                    song.setCoverUrl(detail.getCoverUrl());
                                    changed = true;
                                }
                            }
                        }
                        if (changed && adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Network not available or error - just show without covers
                    }
                });
    }

    private void updateEmptyState() {
        if (downloadedSongs.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            lvDownloads.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            lvDownloads.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Build a full playlist from all downloaded songs and play the selected one.
     */
    private void playDownloadedSong(int position) {
        try {
            List<Song> playlist = new ArrayList<>(downloadedSongs);

            MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
            playerManager.setPlaylist(playlist, position);
            playerManager.playCurrent();

            // Navigate back to MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Convert a legacy flat .mp3 file to a Song (id=0, parsed from filename).
     */
    private Song fileToSong(File file) {
        String name = file.getName();
        if (name.endsWith(".mp3")) {
            name = name.substring(0, name.length() - 4);
        }
        String songName = name;
        String artist = "";
        int dashIdx = name.indexOf(" - ");
        if (dashIdx > 0) {
            songName = name.substring(0, dashIdx);
            artist = name.substring(dashIdx + 3);
        }
        Song song = new Song(0, songName, artist, "");
        song.setUrl(file.getAbsolutePath());
        song.setForceLocalPlayback(true);
        return song;
    }
    /**
     * Show a confirmation dialog adapted for watch (360x320 px screen).
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF424242, 0xFFBB86FC, true));
    }

}
