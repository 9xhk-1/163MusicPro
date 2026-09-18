package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.NetworkImageLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Artist page - shows artist info, description and hot songs.
 * Songs are playable directly.
 */
public class ArtistActivity extends BaseWatchActivity {

    private long artistId;
    private String artistName;
    private String coverUrl;

    private LinearLayout contentLayout;
    private final List<Song> hotSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        artistId = getIntent().getLongExtra("artist_id", 0);
        artistName = getIntent().getStringExtra("artist_name");
        coverUrl = getIntent().getStringExtra("cover_url");

        if (artistName == null || artistName.isEmpty()) artistName = "歌手";

        buildUI();
        loadArtistData();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.addView(buildTitleBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(px(10), px(8), px(10), px(8));
        scroll.addView(contentLayout);
        root.addView(scroll);

        setContentView(root);
    }

    private View buildTitleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF1A1A1A);
        bar.setPadding(px(8), px(8), px(12), px(8));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(0xFFBB86FC);
        back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(22));
        back.setPadding(px(4), 0, px(8), 0);
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("歌手");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        bar.addView(title);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(px(24), 1));
        bar.addView(spacer);
        return bar;
    }

    private void loadArtistData() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        MusicApiHelper.getArtistTopSongs(artistId, cookie,
                new MusicApiHelper.ArtistTopSongCallback() {
                    @Override
                    public void onResult(JSONArray songs, JSONObject artist) {
                        runOnUiThread(() -> showArtistHeader(artist));
                        parseAndShowSongs(songs);
                        runOnUiThread(() -> loadArtistDesc());
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(ArtistActivity.this,
                                    "获取歌手信息失败", Toast.LENGTH_SHORT).show();
                            loadArtistDesc();
                        });
                    }
                });
    }

    private void showArtistHeader(JSONObject artist) {
        if (artist != null) {
            String name = artist.optString("name", "");
            String pic = artist.optString("picUrl", artist.optString("cover", ""));
            if (!name.isEmpty()) artistName = name;
            if (!pic.isEmpty()) coverUrl = pic;
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, px(8));

        ImageView avatar = new ImageView(this);
        int avatarSize = px(64);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundColor(0xFF2D2D2D);
        if (coverUrl != null && !coverUrl.isEmpty()) {
            NetworkImageLoader.load(avatar, coverUrl);
        }
        header.addView(avatar);

        TextView nameView = new TextView(this);
        nameView.setText(artistName);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
        nameView.setPadding(px(10), 0, 0, 0);
        nameView.setSingleLine(true);
        nameView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(nameView);

        contentLayout.addView(header, 0);
    }

    private void parseAndShowSongs(JSONArray songs) {
        hotSongs.clear();
        if (songs != null) {
            for (int i = 0; i < songs.length(); i++) {
                Song s = MusicApiHelper.parseSong(songs.optJSONObject(i));
                if (s != null && s.getId() > 0) {
                    hotSongs.add(s);
                }
            }
        }
        runOnUiThread(this::showSongsList);
    }

    private void showSongsList() {
        addSectionTitle("热门歌曲");
        if (hotSongs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无歌曲");
            empty.setTextColor(0x80FFFFFF);
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, px(10), 0, px(10));
            contentLayout.addView(empty);
            return;
        }
        for (int i = 0; i < hotSongs.size(); i++) {
            final int index = i;
            Song song = hotSongs.get(i);
            contentLayout.addView(buildSongRow(song, index));
        }
    }

    private View buildSongRow(Song song, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(8), px(8), px(8), px(8));
        row.setClickable(true);
        row.setFocusable(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF1E1E1E);
        bg.setCornerRadius(px(4));
        row.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(4);
        row.setLayoutParams(params);

        TextView indexView = new TextView(this);
        indexView.setText(String.valueOf(index + 1));
        indexView.setTextColor(0x80FFFFFF);
        indexView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        indexView.setGravity(Gravity.CENTER);
        indexView.setLayoutParams(new LinearLayout.LayoutParams(px(22), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(indexView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView nameView = new TextView(this);
        nameView.setText(song.getName());
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(nameView);
        if (!song.getAlbum().isEmpty()) {
            TextView albumView = new TextView(this);
            albumView.setText(song.getAlbum());
            albumView.setTextColor(0x80FFFFFF);
            albumView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(11));
            albumView.setSingleLine(true);
            albumView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(albumView);
        }
        row.addView(textCol);

        row.setOnClickListener(v -> playSong(index));
        return row;
    }

    private void playSong(int index) {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        manager.setPlaylist(new ArrayList<>(hotSongs), index);
        manager.playCurrent();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void loadArtistDesc() {
        String cookie = MusicPlayerManager.getInstance().getCookie();
        MusicApiHelper.getArtistDesc(artistId, cookie, new MusicApiHelper.ArtistDescCallback() {
            @Override
            public void onResult(String briefDesc, JSONArray introduction) {
                runOnUiThread(() -> showDescription(briefDesc, introduction));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void showDescription(String briefDesc, JSONArray introduction) {
        addSectionTitle("歌手简介");
        boolean shown = false;
        if (briefDesc != null && !briefDesc.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(briefDesc);
            tv.setTextColor(0xCCFFFFFF);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
            tv.setLineSpacing(px(2), 1f);
            contentLayout.addView(tv);
            shown = true;
        }
        if (introduction != null) {
            for (int i = 0; i < introduction.length(); i++) {
                JSONObject obj = introduction.optJSONObject(i);
                if (obj == null) continue;
                String title = obj.optString("ti", "");
                String txt = obj.optString("txt", "");
                if (!txt.isEmpty()) {
                    if (!title.isEmpty()) {
                        TextView titleView = new TextView(this);
                        titleView.setText(title);
                        titleView.setTextColor(0xFFBB86FC);
                        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
                        titleView.setPadding(0, px(6), 0, px(2));
                        contentLayout.addView(titleView);
                    }
                    TextView txtView = new TextView(this);
                    txtView.setText(txt);
                    txtView.setTextColor(0xCCFFFFFF);
                    txtView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
                    txtView.setLineSpacing(px(2), 1f);
                    contentLayout.addView(txtView);
                    shown = true;
                }
            }
        }
        if (!shown) {
            TextView empty = new TextView(this);
            empty.setText("暂无简介");
            empty.setTextColor(0x80FFFFFF);
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, px(10), 0, px(10));
            contentLayout.addView(empty);
        }
    }

    private void addSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText("── " + title + " ──");
        tv.setTextColor(0xFFBB86FC);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, px(8), 0, px(4));
        contentLayout.addView(tv);
    }
}
