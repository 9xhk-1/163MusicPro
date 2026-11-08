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

import com.google.android.material.button.MaterialButton;
import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.manager.DownloadManager;
import com.qinghe.music163pro.manager.PlaylistManager;
import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;
import com.qinghe.music163pro.util.PlaylistCache;
import com.qinghe.music163pro.util.QualityPicker;
import com.qinghe.music163pro.util.WatchConfirmDialog;
import com.qinghe.music163pro.util.WatchUiUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Playlist detail activity - shows songs in a playlist and allows playing them.
 * Supports cloud mode (subscribe/unsubscribe via API) and local mode (local save).
 * Rules:
 * - Cloud mode: "我喜欢的音乐" → no unsub/delete; my created → no unsub, can delete;
 *   others' → can unsub, no delete.
 * - Local mode: all can unsub (local remove), no delete.
 * Designed for watch screen (320x360 dpi).
 */
public class PlaylistDetailActivity extends BaseWatchActivity {

    private ListView lvSongs;
    private final List<Song> displayList = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private MusicPlayerManager playerManager;
    private PlaylistManager playlistManager;
    private TextView tvStatus;
    private TextView tvTitle;
    private TextView tvCreatorLabel;
    private MaterialButton btnFav;
    private MaterialButton btnDownload;
    private MaterialButton btnSelectAll;
    private long playlistId;
    private String playlistName;
    private int trackCount;
    private String creator;
    private long creatorUserId;
    private boolean isLikedPlaylist;
    private long currentUserId = -1;
    private boolean isCloudMode;
    private boolean isSubscribed = false;

    /** 歌单批量下载的选择模式：点右上角下载图标进入，左上角出现全选按钮。 */
    private boolean selectMode = false;
    private final Set<Long> selectedSongIds = new HashSet<>();
    /** 与播放页共用的音质选择浮层；检测中先显示占位行，接口回来原地替换。 */
    private FrameLayout qualityPickerOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("music163_settings", MODE_PRIVATE);
        isCloudMode = prefs.getBoolean("fav_mode_cloud", false);

        playlistId = getIntent().getLongExtra("playlist_id", 0);
        playlistName = getIntent().getStringExtra("playlist_name");
        trackCount = getIntent().getIntExtra("track_count", 0);
        creator = getIntent().getStringExtra("creator");
        creatorUserId = getIntent().getLongExtra("creator_user_id", 0);
        isLikedPlaylist = getIntent().getBooleanExtra("is_liked_playlist", false);

        playerManager = MusicPlayerManager.getInstance();
        playlistManager = new PlaylistManager();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg_dark));
        root.setPadding(px(6), px(6), px(6), px(6));

        // Title row：左上角全选（选择模式）/ 右上角收藏 + 批量下载
        android.widget.RelativeLayout titleRow = new android.widget.RelativeLayout(this);
        titleRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Title
        tvTitle = new TextView(this);
        updateTitleText();
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary));
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(dpToPx(44), 0, dpToPx(64), px(2));
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        tvTitle.setMarqueeRepeatLimit(-1);
        tvTitle.setSelected(true);
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        tvTitle.setLayoutParams(titleParams);
        titleRow.addView(tvTitle);

        // 右上角：批量下载入口。第一次点进入选择模式，再点一次确认并选音质。
        btnDownload = createTitleIconButton(R.drawable.ic_get_app, "批量下载");
        btnDownload.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams downloadParams = new android.widget.RelativeLayout.LayoutParams(
                dpToPx(32), dpToPx(32));
        downloadParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        downloadParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnDownload.setLayoutParams(downloadParams);
        btnDownload.setOnClickListener(v -> onDownloadButtonClick());
        titleRow.addView(btnDownload);

        // Heart button - left of download
        btnFav = createTitleIconButton(R.drawable.ic_favorite_border, "收藏歌单");
        android.widget.RelativeLayout.LayoutParams favParams = new android.widget.RelativeLayout.LayoutParams(
                dpToPx(32), dpToPx(32));
        favParams.addRule(android.widget.RelativeLayout.LEFT_OF, btnDownload.getId());
        favParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnFav.setLayoutParams(favParams);
        btnFav.setOnClickListener(v -> togglePlaylistFav());
        titleRow.addView(btnFav);

        // 左上角：全选按钮。只在批量下载的选择模式里出现。
        btnSelectAll = new MaterialButton(this);
        btnSelectAll.setText("全选");
        btnSelectAll.setTextSize(9);
        btnSelectAll.setAllCaps(false);
        btnSelectAll.setTextColor(getResources().getColor(R.color.text_secondary));
        btnSelectAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.TRANSPARENT));
        btnSelectAll.setMinWidth(0);
        btnSelectAll.setMinimumWidth(0);
        btnSelectAll.setMinHeight(0);
        btnSelectAll.setMinimumHeight(0);
        btnSelectAll.setInsetTop(0);
        btnSelectAll.setInsetBottom(0);
        btnSelectAll.setPadding(0, 0, 0, 0);
        btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        android.widget.RelativeLayout.LayoutParams selectAllParams =
                new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, dpToPx(30));
        selectAllParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
        selectAllParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnSelectAll.setLayoutParams(selectAllParams);
        btnSelectAll.setVisibility(View.GONE);
        titleRow.addView(btnSelectAll);

        root.addView(titleRow);

        // Creator label (always created, visibility depends on data)
        tvCreatorLabel = new TextView(this);
        tvCreatorLabel.setTextColor(getResources().getColor(R.color.text_secondary));
        tvCreatorLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(11));
        tvCreatorLabel.setGravity(Gravity.CENTER);
        tvCreatorLabel.setPadding(0, 0, 0, px(1));
        updateCreatorLabel();
        root.addView(tvCreatorLabel);

        // Status text
        tvStatus = new TextView(this);
        tvStatus.setText("正在加载...");
        tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        tvStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, px(2), 0, px(1));
        root.addView(tvStatus);

        lvSongs = new ListView(this);
        lvSongs.setDividerHeight(1);
        lvSongs.setDivider(getResources().getDrawable(android.R.color.transparent));
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lvSongs.setLayoutParams(lvParams);
        lvSongs.setVisibility(View.GONE);
        root.addView(lvSongs);

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
                    // 选择模式：勾选前缀 + 高亮底色。convertView 会复用，所以每次都要重设。
                    boolean checked = selectMode && selectedSongIds.contains(song.getId());
                    String displayName = song.getDisplayName();
                    String shownName = selectMode
                            ? (checked ? "✓ " : "○ ") + displayName
                            : displayName;
                    if (!android.text.TextUtils.equals(tvName.getText(), shownName)) {
                        tvName.setText(shownName);
                    }
                    if (selectMode) {
                        view.setBackgroundColor(checked ? 0x33FFFFFF : 0x00000000);
                    } else {
                        view.setBackgroundResource(R.drawable.bg_card_rounded_ripple);
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
        lvSongs.setAdapter(adapter);
        NetworkImageLoader.attachListViewOptimization(lvSongs, adapter, position ->
                position >= 0 && position < displayList.size()
                        ? displayList.get(position).getCoverUrl() : null);

        lvSongs.setOnItemClickListener((parent, view, position, id) -> {
            if (selectMode) {
                // 选择模式下点歌只切换勾选，不切歌
                if (position >= 0 && position < displayList.size()) {
                    long songId = displayList.get(position).getId();
                    if (selectedSongIds.contains(songId)) {
                        selectedSongIds.remove(songId);
                    } else {
                        selectedSongIds.add(songId);
                    }
                    adapter.notifyDataSetChanged();
                    updateSelectUi();
                }
                return;
            }
            List<Song> playlist = new ArrayList<>(displayList);
            playerManager.setPlaylistFromSource(playlist, position,
                    playlistId, playlistName, trackCount, creator,
                    creatorUserId, isLikedPlaylist);
            playerManager.playCurrent();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Long-press to delete song from user's created playlist (not liked playlist)
        // Works in both cloud and local mode (playlist lives on server regardless)
        lvSongs.setOnItemLongClickListener((parent, view, position, id) -> {
            if (selectMode) return false;
            if (isLikedPlaylist) return false;
            if (creatorUserId <= 0 || creatorUserId != currentUserId) return false;

            Song song = displayList.get(position);
            showConfirmDialog("确认删除", "确定从歌单中删除「" + song.getDisplayName() + "」？", () -> {
                String cookie = playerManager.getCookie();
                if (cookie == null || cookie.isEmpty()) {
                    Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, "正在删除...", Toast.LENGTH_SHORT).show();
                MusicApiHelper.playlistTracks("del", playlistId,
                        new long[]{song.getId()}, cookie,
                        new MusicApiHelper.PlaylistActionCallback() {
                            @Override
                            public void onResult(boolean success) {
                                if (success) {
                                    displayList.remove(position);
                                    adapter.notifyDataSetChanged();
                                    trackCount = displayList.size();
                                    updateTitleText();
                                    tvStatus.setText(trackCount + " 首歌曲");
                                    // 同步快照，否则下次秒开又会把已删的那首显示出来
                                    PlaylistCache.savePlaylist(PlaylistDetailActivity.this,
                                            playlistId, new PlaylistCache.Snapshot(
                                                    PlaylistCache.trimForCache(displayList),
                                                    isLikedPlaylist ? 5 : 0, creator,
                                                    creatorUserId, isSubscribed));
                                    Toast.makeText(PlaylistDetailActivity.this,
                                            "已删除", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(PlaylistDetailActivity.this,
                                            "删除失败", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(PlaylistDetailActivity.this,
                                        "删除失败: " + message, Toast.LENGTH_SHORT).show();
                            }
                        });
            });
            return true;
        });

        // Always get current user ID for playlist type identification (liked/created/others')
        String cookie = playerManager.getCookie();
        if (cookie != null && !cookie.isEmpty()) {
            MusicApiHelper.getUid(cookie, new MusicApiHelper.AccountCallback() {
                @Override
                public void onResult(JSONObject json) {
                    currentUserId = json.optLong("uid", -1);
                    updateActionButtons();
                }
                @Override
                public void onError(String message) {
                    updateActionButtons();
                }
            });
        }

        updateFavButton();
        updateActionButtons();

        if (playlistId > 0) {
            loadPlaylistDetail(playlistId);
        }
    }

    private void updateTitleText() {
        String titleText = playlistName != null ? playlistName : "歌单";
        if (trackCount > 0) {
            titleText += " (" + trackCount + "首)";
        }
        tvTitle.setText(titleText);
    }

    private void updateCreatorLabel() {
        if (creator != null && !creator.isEmpty()) {
            tvCreatorLabel.setText("创建者: " + creator);
            tvCreatorLabel.setVisibility(View.VISIBLE);
        } else {
            tvCreatorLabel.setVisibility(View.GONE);
        }
    }

    /** 把一份本地快照渲染成列表；内存命中与磁盘异步命中都走这里。 */
    private void applyCachedSnapshot(PlaylistCache.Snapshot cached) {
        if (cached == null || cached.songs.isEmpty()) return;
        displayList.clear();
        displayList.addAll(cached.songs);
        adapter.notifyDataSetChanged();
        trackCount = displayList.size();
        if (cached.creatorUserId > 0) {
            creatorUserId = cached.creatorUserId;
        }
        isLikedPlaylist = (cached.specialType == 5);
        isSubscribed = cached.subscribed;
        if (cached.creator != null && !cached.creator.isEmpty()) {
            creator = cached.creator;
        }
        updateTitleText();
        updateCreatorLabel();
        lvSongs.setVisibility(View.VISIBLE);
        updateActionButtons();
        updateSelectUi();
    }

    private void loadPlaylistDetail(long playlistId) {
        // 秒开：内存快照立即渲染（零 IO），磁盘反序列化放后台线程，
        // 接口回来后再用 sameSongs 做差异校准，完全一致就不重绘。
        applyCachedSnapshot(PlaylistCache.peekPlaylist(playlistId));
        PlaylistCache.loadPlaylistAsync(this, playlistId, disk -> {
            if (isFinishing() || isDestroyed()) return;
            if (!displayList.isEmpty()) return;  // 内存或网络已经渲染过，别重复刷新
            applyCachedSnapshot(disk);
        });

        String cookie = playerManager.getCookie();
        MusicApiHelper.getPlaylistDetailWithMeta(playlistId, cookie, new MusicApiHelper.PlaylistDetailWithMetaCallback() {
            @Override
            public void onResult(List<Song> songs, int apiTrackCount, String apiCreator,
                                 long apiCreatorUserId, int specialType, boolean subscribed) {
                boolean changed = !PlaylistCache.sameSongs(displayList, songs);
                if (changed) {
                    displayList.clear();
                    displayList.addAll(songs);
                    adapter.notifyDataSetChanged();
                }
                // 完全一致就不重建列表、不重新拉封面，只校准元信息与状态

                // Update metadata from API response (self-correct for all entry points)
                trackCount = songs.size();
                if (apiCreatorUserId > 0) {
                    creatorUserId = apiCreatorUserId;
                }
                isLikedPlaylist = (specialType == 5);
                isSubscribed = subscribed;
                if (apiCreator != null && !apiCreator.isEmpty()) {
                    creator = apiCreator;
                }

                updateTitleText();
                updateCreatorLabel();
                if (songs.isEmpty()) {
                    tvStatus.setText("暂无歌曲");
                    lvSongs.setVisibility(View.GONE);
                } else {
                    tvStatus.setText(songs.size() + " 首歌曲");
                    lvSongs.setVisibility(View.VISIBLE);
                }

                // Refresh buttons with corrected metadata
                updateActionButtons();
                updateSelectUi();

                // 写回快照：下次进这个歌单先出列表，再走上面的对比
                PlaylistCache.savePlaylist(PlaylistDetailActivity.this, playlistId,
                        new PlaylistCache.Snapshot(PlaylistCache.trimForCache(songs),
                                specialType, apiCreator, apiCreatorUserId, subscribed));
            }

            @Override
            public void onError(String message) {
                if (displayList.isEmpty()) {
                    tvStatus.setText("加载失败: " + message);
                } else {
                    // 有快照就保留列表，别用错误提示盖掉秒开的内容
                    tvStatus.setText(displayList.size() + " 首歌曲（离线快照）");
                }
            }
        });
    }

    /**
     * Update button visibility based on playlist type (always identified regardless of mode):
     *   - "我喜欢的音乐" (specialType=5) → no unsub, no delete
     *   - My created playlist → no unsub, can delete
     *   - Others' playlist → can sub/unsub (cloud) or local save/remove, no delete
     */
    private void updateActionButtons() {
        // 歌单级删除/改名已移除；右上角只保留收藏与批量下载
        if (isLikedPlaylist || (creatorUserId > 0 && creatorUserId == currentUserId)) {
            btnFav.setVisibility(View.GONE);
        } else {
            btnFav.setVisibility(View.VISIBLE);
            updateFavButton();
        }
    }

    private void updateFavButton() {
        if (playlistId <= 0) {
            btnFav.setVisibility(View.GONE);
            return;
        }
        if (isCloudMode) {
            btnFav.setIconResource(isSubscribed ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            btnFav.setIconTint(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(isSubscribed ? R.color.favorite_red : R.color.text_secondary)));
        } else {
            boolean saved = playlistManager.isPlaylistSaved(playlistId);
            btnFav.setIconResource(saved ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            btnFav.setIconTint(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(saved ? R.color.favorite_red : R.color.text_secondary)));
        }
    }

    private MaterialButton createTitleIconButton(int icon, String description) {
        MaterialButton button = new MaterialButton(this);
        button.setIconResource(icon);
        button.setIconTint(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconSize(dpToPx(20));
        button.setIconPadding(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        return button;
    }

    private void togglePlaylistFav() {
        if (playlistId <= 0) return;

        if (isCloudMode) {
            // Cloud mode: toggle between subscribe/unsubscribe based on current state
            String cookie = playerManager.getCookie();
            if (cookie == null || cookie.isEmpty()) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isSubscribed) {
                Toast.makeText(this, "正在取消收藏...", Toast.LENGTH_SHORT).show();
                MusicApiHelper.subscribePlaylist(playlistId, false, cookie,
                        new MusicApiHelper.PlaylistActionCallback() {
                    @Override
                    public void onResult(boolean success) {
                        if (success) {
                            isSubscribed = false;
                            playlistManager.removePlaylist(playlistId);
                            updateFavButton();
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "已取消云端收藏", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "取消收藏失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(PlaylistDetailActivity.this,
                                "取消收藏失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, "正在收藏...", Toast.LENGTH_SHORT).show();
                MusicApiHelper.subscribePlaylist(playlistId, true, cookie,
                        new MusicApiHelper.PlaylistActionCallback() {
                    @Override
                    public void onResult(boolean success) {
                        if (success) {
                            isSubscribed = true;
                            updateFavButton();
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "已收藏到云端", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlaylistDetailActivity.this,
                                    "收藏失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(PlaylistDetailActivity.this,
                                "收藏失败: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            // Local mode: toggle local save
            boolean saved = playlistManager.isPlaylistSaved(playlistId);
            if (saved) {
                playlistManager.removePlaylist(playlistId);
                Toast.makeText(this, "已取消收藏歌单", Toast.LENGTH_SHORT).show();
            } else {
                PlaylistInfo info = new PlaylistInfo(playlistId,
                        playlistName != null ? playlistName : "",
                        trackCount, creator != null ? creator : "",
                        creatorUserId, true, isLikedPlaylist ? "5" : "0");
                playlistManager.addPlaylist(info);
                Toast.makeText(this, "已收藏歌单", Toast.LENGTH_SHORT).show();
            }
            updateFavButton();
        }
    }

    // ───────────────────── 歌单批量下载 ─────────────────────

    /** 右上角下载图标：第一次进入选择模式，第二次确认已选歌曲并选音质。 */
    private void onDownloadButtonClick() {
        if (!selectMode) {
            if (displayList.isEmpty()) {
                Toast.makeText(this, "歌单还没有歌曲", Toast.LENGTH_SHORT).show();
                return;
            }
            selectMode = true;
            selectedSongIds.clear();
            Toast.makeText(this, "勾选要下载的歌曲，再点右上角下载图标", Toast.LENGTH_SHORT).show();
            updateSelectUi();
            adapter.notifyDataSetChanged();
            return;
        }
        if (selectedSongIds.isEmpty()) {
            // 空选时再点一次 = 退出选择模式
            exitSelectMode();
            return;
        }
        showQualityPicker();
    }

    private void exitSelectMode() {
        selectMode = false;
        selectedSongIds.clear();
        updateSelectUi();
        adapter.notifyDataSetChanged();
    }

    private void toggleSelectAll() {
        boolean all = allSelected();
        selectedSongIds.clear();
        if (!all) {
            for (Song song : displayList) {
                selectedSongIds.add(song.getId());
            }
        }
        updateSelectUi();
        adapter.notifyDataSetChanged();
    }

    private boolean allSelected() {
        if (displayList.isEmpty()) {
            return false;
        }
        for (Song song : displayList) {
            if (!selectedSongIds.contains(song.getId())) {
                return false;
            }
        }
        return true;
    }

    /** 同步左上角全选按钮、状态提示与右上角图标高亮。 */
    private void updateSelectUi() {
        if (btnSelectAll == null || btnDownload == null) {
            return;
        }
        btnSelectAll.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        btnSelectAll.setText(allSelected() ? "取消全选" : "全选");
        btnDownload.setIconTint(android.content.res.ColorStateList.valueOf(
                getResources().getColor(selectMode ? R.color.colorPrimary : R.color.text_secondary)));
        if (tvStatus == null) {
            return;
        }
        if (selectMode) {
            tvStatus.setText("已选 " + selectedSongIds.size() + "/" + displayList.size()
                    + " 首 · 点右上角图标选音质");
        } else if (displayList.isEmpty()) {
            tvStatus.setText("暂无歌曲");
        } else {
            tvStatus.setText(displayList.size() + " 首歌曲");
        }
    }

    // 档位表与播放页 / 单曲下载页共用 QualityPicker.LEVELS，不再各自维护一份

    private List<Song> collectSelectedTargets() {
        List<Song> targets = new ArrayList<>();
        for (Song song : displayList) {
            if (selectedSongIds.contains(song.getId())) {
                targets.add(song);
            }
        }
        return targets;
    }

    /**
     * 选音质前先检测：不同歌曲本身压到的最高码率不同，不同会员等级（VIP / SVIP）
     * 的 dlLevel 也不同，直接给一张静态档位表会把「根本下不到」的档位也列出来。
     *
     * UI 统一走 {@link QualityPicker}（原作者做的那套播放音质浮层），
     * 每档标出 N/M 可下 + 所需会员等级（免费 / VIP / SVIP），0 首可下的档位置灰。
     */
    private void showQualityPicker() {
        final List<Song> targets = collectSelectedTargets();
        if (targets.isEmpty()) {
            Toast.makeText(this, "请先勾选歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            Toast.makeText(this, "请先登录后再下载", Toast.LENGTH_SHORT).show();
            return;
        }

        // 先显示「检测中」占位行，接口回来后原地替换，遮罩不闪、不重建
        List<QualityPicker.Row> loading = new ArrayList<>();
        loading.add(new QualityPicker.Row("", "正在检测可下载音质…", "按当前账号权限",
                "", QualityPicker.COLOR_DISABLED, false, false));
        qualityPickerOverlay = QualityPicker.show(this, "下载音质",
                "已选 " + targets.size() + " 首 · 正在检测", loading, null,
                () -> {
                    QualityPicker.dismiss(this, qualityPickerOverlay);
                    qualityPickerOverlay = null;
                });

        List<Long> neteaseIds = new ArrayList<>();
        for (Song song : targets) {
            if (song.getId() > 0) {
                neteaseIds.add(song.getId());
            }
        }
        final int neteaseCount = neteaseIds.size();
        if (neteaseIds.isEmpty()) {
            applyBatchQualityRows(targets, null, 0);
            return;
        }

        MusicApiHelper.getSongsQualityInfo(neteaseIds, cookie,
                new MusicApiHelper.SongsQualityCallback() {
                    @Override
                    public void onResult(Map<Long, MusicApiHelper.SongQualityInfo> info) {
                        if (isFinishing() || isDestroyed()) return;
                        applyBatchQualityRows(targets, info, neteaseCount);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(PlaylistDetailActivity.this,
                                "音质检测失败，只显示通用档位", Toast.LENGTH_SHORT).show();
                        applyBatchQualityRows(targets, null, neteaseCount);
                    }
                });
    }

    /** 把检测结果转成与播放音质页完全一致的卡片行。 */
    private void applyBatchQualityRows(final List<Song> targets,
                                       final Map<Long, MusicApiHelper.SongQualityInfo> info,
                                       int neteaseCount) {
        if (qualityPickerOverlay == null) return;
        final int total = neteaseCount;
        final Map<String, List<Song>> allowedByLevel = new HashMap<>();
        List<QualityPicker.Row> rows = new ArrayList<>();

        for (int i = 0; i < QualityPicker.LEVELS.length; i++) {
            String[] q = QualityPicker.LEVELS[i];
            final String level = q[0];
            List<Song> allowed = new ArrayList<>();
            int neteaseAllowed = 0;
            for (Song song : targets) {
                if (info == null) {
                    // 网络失败兜底：只放开三个通用档，不假装高档位可用
                    if (i < 3) {
                        allowed.add(song);
                        neteaseAllowed++;
                    }
                    continue;
                }
                MusicApiHelper.SongQualityInfo qi = info.get(song.getId());
                if (qi == null) {
                    // 接口没返回这首歌的权限时按免费档处理
                    if (i < 3) {
                        allowed.add(song);
                        neteaseAllowed++;
                    }
                } else if (qi.isDownloadable(level)) {
                    allowed.add(song);
                    neteaseAllowed++;
                }
            }
            allowedByLevel.put(level, allowed);

            int need = MusicApiHelper.requiredMembership(level);
            String member = need == 2 ? "SVIP" : need == 1 ? "VIP" : "免费";
            String count;
            if (total == 0 || neteaseAllowed == total) {
                count = "全部可下";
            } else if (neteaseAllowed > 0) {
                count = neteaseAllowed + "/" + total + " 可下";
            } else {
                count = "不可下";
            }
            // 全是 B 站音源时没有网易档位概念，别把每一行都置灰
            int color = (total > 0 && neteaseAllowed == 0)
                    ? QualityPicker.COLOR_DISABLED
                    : need == 2 ? QualityPicker.COLOR_SVIP
                    : need == 1 ? QualityPicker.COLOR_VIP
                    : QualityPicker.COLOR_FREE;
            boolean enabled = !allowed.isEmpty();
            rows.add(new QualityPicker.Row(level, q[1], q[2] + " · " + count,
                    member, color, false, enabled));
        }

        String subtitle = "已选 " + targets.size() + " 首 · " + batchAccountTierLabel(info);
        QualityPicker.update(qualityPickerOverlay, subtitle, rows, row -> {
            List<Song> allowed = allowedByLevel.get(row.level);
            if (allowed == null || allowed.isEmpty()) {
                Toast.makeText(this, "当前账号没有歌曲可下载该音质",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            int skipped = targets.size() - allowed.size();
            if (skipped > 0) {
                Toast.makeText(this, "已跳过 " + skipped + " 首不支持该音质的歌曲",
                        Toast.LENGTH_SHORT).show();
            }
            startBatchDownload(row.level, allowed);
        });
    }

    /** 副标题里说明当前账号是普通 / 黑胶 VIP / 黑胶 SVIP。 */
    private String batchAccountTierLabel(Map<Long, MusicApiHelper.SongQualityInfo> info) {
        if (info == null) return "检测失败，仅显示通用档位";
        for (MusicApiHelper.SongQualityInfo qi : info.values()) {
            if (qi == null || !qi.plLevelReliable) continue;
            switch (qi.accountTier()) {
                case 2:  return "当前账号：黑胶 SVIP";
                case 1:  return "当前账号：黑胶 VIP";
                default: return "当前账号：未开通会员";
            }
        }
        return "音质权限以当前账号为准";
    }
    private void startBatchDownload(String quality, List<Song> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        String cookie = playerManager.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            Toast.makeText(this, "请先登录后再下载", Toast.LENGTH_SHORT).show();
            return;
        }
        exitSelectMode();
        Toast.makeText(this, "开始下载 " + targets.size() + " 首...", Toast.LENGTH_SHORT).show();
        downloadNext(targets, quality, 0, 0);
    }

    /** 串行下载：手表只有 0.78GB 内存，不并发开多个网络流。 */
    private void downloadNext(final List<Song> targets, final String quality,
                              final int index, final int successCount) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (index >= targets.size()) {
            Toast.makeText(this, "下载完成：成功 " + successCount + "/" + targets.size(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Song song = targets.get(index);
        DownloadManager.downloadSong(song, downloadCookieFor(song), quality,
                new DownloadManager.DownloadCallback() {
                    @Override
                    public void onSuccess(String filePath) {
                        downloadNext(targets, quality, index + 1, successCount + 1);
                    }

                    @Override
                    public void onError(String message) {
                        // 单首失败（无版权 / 该音质已下载）不阻断整批
                        downloadNext(targets, quality, index + 1, successCount);
                    }
                });
    }

    private String downloadCookieFor(Song song) {
        return playerManager.getCookie();
    }

    @Override
    public void onBackPressed() {
        if (selectMode) {
            exitSelectMode();
            return;
        }
        super.onBackPressed();
    }

    /**
     * Show a confirmation dialog adapted for watch (360x320 px screen).
     */
    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        WatchConfirmDialog.show(this, title, message, onConfirm,
                new WatchConfirmDialog.Options(0x33FFFFFF, 0xFFF2F2F7, true));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkImageLoader.cancelAll();
    }
}
