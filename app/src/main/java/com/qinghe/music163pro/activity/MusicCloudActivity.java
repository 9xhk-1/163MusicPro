package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.CloudItem;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.WatchConfirmDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Music cloud screen.
 */
public class MusicCloudActivity extends BaseWatchActivity {

    private final List<CloudItem> musicItems = new ArrayList<>();
    private final List<CloudItem> displayItems = new ArrayList<>();

    private ArrayAdapter<CloudItem> adapter;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_cloud);

        tvStatus = findViewById(R.id.tv_cloud_status);
        ListView listView = findViewById(R.id.lv_cloud_items);

        adapter = new ArrayAdapter<CloudItem>(this, R.layout.item_cloud_entry, R.id.tv_cloud_name, displayItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.item_cloud_entry, parent, false);
                }
                CloudItem item = getItem(position);
                if (item != null) {
                    ImageView icon = view.findViewById(R.id.iv_cloud_icon);
                    TextView name = view.findViewById(R.id.tv_cloud_name);
                    TextView detail = view.findViewById(R.id.tv_cloud_detail);
                    icon.setImageResource(item.isMusic() ? R.drawable.ic_music_note : R.drawable.ic_insert_drive_file);
                    icon.setColorFilter(item.isMusic() ? 0xFFD0BCFF : 0x80FFFFFF);
                    name.setSelected(true);
                    detail.setSelected(true);
                    String display = item.getDisplayName();
                    if (!android.text.TextUtils.equals(name.getText(), display)) {
                        name.setText(display);
                    }
                    String subtitle = item.getSubtitle();
                    if (!android.text.TextUtils.equals(detail.getText(), subtitle)) {
                        detail.setText(subtitle);
                    }
                }
                return view;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> onCloudItemClick(displayItems.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            CloudItem item = displayItems.get(position);
            showConfirmDialog("确认删除", "确定删除「" + item.getDisplayName() + "」？", () -> deleteCloudItem(item));
            return true;
        });

        applyMusicItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCloudItems();
    }

    private void loadCloudItems() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty() || !cookie.contains("MUSIC_U")) {
            tvStatus.setText("请先登录");
            displayItems.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        tvStatus.setText("正在加载...");
        MusicApiHelper.getCloudItems(cookie, new MusicApiHelper.CloudItemsCallback() {
            @Override
            public void onResult(List<CloudItem> items) {
                musicItems.clear();
                for (CloudItem item : items) {
                    if (item.isMusic()) {
                        musicItems.add(item);
                    }
                }
                applyMusicItems();
            }

            @Override
            public void onError(String message) {
                tvStatus.setText("加载失败: " + message);
            }
        });
    }

    private void applyMusicItems() {
        displayItems.clear();
        displayItems.addAll(musicItems);
        adapter.notifyDataSetChanged();
        if (displayItems.isEmpty()) {
            tvStatus.setText("暂无云盘音乐");
        } else {
            tvStatus.setText(displayItems.size() + " 首音乐");
        }
    }

    private void onCloudItemClick(CloudItem item) {
        if (item.isMusic()) {
            playCloudMusic(item);
        } else {
            downloadCloudFile(item);
        }
    }

    private void playCloudMusic(CloudItem item) {
        int index = musicItems.indexOf(item);
        if (index < 0) {
            index = 0;
        }
        ArrayList<Song> playlist = new ArrayList<>();
        for (CloudItem cloudItem : musicItems) {
            playlist.add(cloudItem.toSong());
        }
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance();
        playerManager.setPlaylist(playlist, index);
        playerManager.playCurrent();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void downloadCloudFile(CloudItem item) {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (item.getDownloadUrl() != null && !item.getDownloadUrl().isEmpty()) {
            doDownload(item.getFileName() != null && !item.getFileName().isEmpty()
                    ? item.getFileName() : item.getDisplayName(), item.getDownloadUrl());
            return;
        }
        Toast.makeText(this, "正在获取下载链接...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.getCloudItemDetail(item.getCloudSongId(), cookie, new MusicApiHelper.CloudItemCallback() {
            @Override
            public void onResult(CloudItem detail) {
                if (detail.getDownloadUrl() == null || detail.getDownloadUrl().isEmpty()) {
                    Toast.makeText(MusicCloudActivity.this, "文件暂无下载链接", Toast.LENGTH_SHORT).show();
                    return;
                }
                doDownload(item.getFileName() != null && !item.getFileName().isEmpty()
                        ? item.getFileName() : item.getDisplayName(), detail.getDownloadUrl());
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MusicCloudActivity.this, "获取失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doDownload(String name, String url) {
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), "Download");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("无法创建下载目录");
                }
                String safeName = sanitizeFileName(name);
                if (!hasExtension(safeName)) {
                    safeName += ".bin";
                }
                File outputFile = new File(dir, safeName);
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                }
                runOnUiThread(() -> Toast.makeText(this,
                        "已下载到 " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void deleteCloudItem(CloudItem item) {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        if (cookie == null || cookie.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在删除...", Toast.LENGTH_SHORT).show();
        MusicApiHelper.deleteCloudItem(item.getCloudSongId(), cookie, new MusicApiHelper.PlaylistActionCallback() {
            @Override
            public void onResult(boolean success) {
                if (success) {
                    musicItems.remove(item);
                    applyMusicItems();
                    Toast.makeText(MusicCloudActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MusicCloudActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MusicCloudActivity.this, "删除失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "cloud_file.bin";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean hasExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1;
    }

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0xFF2B2930, 0xFFD0BCFF, true));
    }
}
