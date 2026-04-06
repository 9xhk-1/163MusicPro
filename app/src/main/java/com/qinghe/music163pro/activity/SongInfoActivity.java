package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.R;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Shows comprehensive song information from multiple API sources:
 * 1. /api/v3/song/detail - Song metadata (duration, album, artists, etc.)
 * 2. /api/song/play/about/block/page - Song wiki / encyclopedia
 * 3. /api/artist/introduction - Artist biography
 *
 * Adapted for watch screen (320x360 DPI).
 */
public class SongInfoActivity extends AppCompatActivity {

    private static final String TAG = "SongInfoActivity";
    private static final String PREFS_NAME = "music163_settings";

    private static final int COLOR_BG = 0xFF121212;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0x80FFFFFF;
    private static final int COLOR_TEXT_DESC = 0xB3FFFFFF;
    private static final int COLOR_ACCENT = 0xFF03DAC6;
    private static final int COLOR_DIVIDER = 0xFF1E1E1E;
    private static final int COLOR_CARD_BG = 0xFF252525;

    private long songId;
    private String songName;
    private String artistName;
    private long artistId;
    private String cookie;

    private LinearLayout contentLayout;
    private TextView tvLoading;
    private String currentBlockCode = "";
    // Collected similar songs from the current block for playlist filling
    private final List<Song> currentBlockSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("keep_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        songId = getIntent().getLongExtra("song_id", 0);
        songName = getIntent().getStringExtra("song_name");
        artistName = getIntent().getStringExtra("artist_name");
        artistId = getIntent().getLongExtra("artist_id", 0);
        cookie = getIntent().getStringExtra("cookie");

        MusicLog.op(TAG, "打开音乐信息", "songId=" + songId + " songName=" + songName);

        buildUi();
        fetchSongDetail();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(px(10), px(8), px(10), px(16));
        scrollView.addView(contentLayout);

        // Title
        contentLayout.addView(makeText("音乐信息", COLOR_TEXT_PRIMARY, px(18), true, Gravity.CENTER));
        contentLayout.addView(makeSpacer(px(8)));

        // Loading indicator
        tvLoading = makeText("加载中...", COLOR_TEXT_SECONDARY, px(14), false, Gravity.CENTER);
        contentLayout.addView(tvLoading);

        setContentView(scrollView);
    }

    // ─── Step 1: Fetch song detail from /api/v3/song/detail ───────────

    private void fetchSongDetail() {
        MusicApiHelper.getSongDetail(songId, cookie, new MusicApiHelper.SongDetailCallback() {
            @Override
            public void onResult(JSONObject songDetail) {
                contentLayout.removeView(tvLoading);
                displaySongDetail(songDetail);
                // After detail, fetch artist desc (then wiki)
                fetchArtistDesc();
            }

            @Override
            public void onError(String message) {
                MusicLog.e(TAG, "获取歌曲详情失败: " + message);
                // Still show basic info and continue
                contentLayout.removeView(tvLoading);
                displayBasicInfo();
                fetchArtistDesc();
            }
        });
    }

    private void displayBasicInfo() {
        addSectionHeader(R.drawable.ic_info, "基本信息");
        if (songName != null && !songName.isEmpty()) {
            addInfoRow("歌曲名", songName);
        }
        if (artistName != null && !artistName.isEmpty()) {
            addInfoRow("歌手", artistName);
        }
        addInfoRow("歌曲ID", String.valueOf(songId));
    }

    private void displaySongDetail(JSONObject song) {
        addSectionHeader(R.drawable.ic_music_note, "歌曲详情");

        // Song name
        String name = song.optString("name", "");
        if (!name.isEmpty()) {
            contentLayout.addView(makeText(name, COLOR_TEXT_PRIMARY, px(17), true, Gravity.START));
            contentLayout.addView(makeSpacer(px(4)));
        }

        // Song aliases (别名/副标题)
        JSONArray alia = song.optJSONArray("alia");
        if (alia != null && alia.length() > 0) {
            StringBuilder aliases = new StringBuilder();
            for (int i = 0; i < alia.length(); i++) {
                if (aliases.length() > 0) aliases.append(" / ");
                aliases.append(alia.optString(i, ""));
            }
            if (aliases.length() > 0) {
                addInfoRow("别名", aliases.toString());
            }
        }

        // Song ID
        addInfoRow("歌曲ID", String.valueOf(song.optLong("id", songId)));

        // Artists (all)
        JSONArray ar = song.optJSONArray("ar");
        if (ar != null && ar.length() > 0) {
            StringBuilder artistStr = new StringBuilder();
            for (int i = 0; i < ar.length(); i++) {
                JSONObject a = ar.optJSONObject(i);
                if (a == null) continue;
                if (artistStr.length() > 0) artistStr.append(" / ");
                artistStr.append(a.optString("name", ""));
                // Extract first artist ID
                if (i == 0 && artistId <= 0) {
                    artistId = a.optLong("id", 0);
                }
            }
            addInfoRow("歌手", artistStr.toString());
        }

        // Album info
        JSONObject al = song.optJSONObject("al");
        if (al != null) {
            String albumName = al.optString("name", "");
            if (!albumName.isEmpty()) {
                addInfoRow("专辑", albumName);
            }
            long albumId = al.optLong("id", 0);
            if (albumId > 0) {
                addInfoRow("专辑ID", String.valueOf(albumId));
            }
        }

        // Duration
        long dt = song.optLong("dt", 0);
        if (dt > 0) {
            long totalSec = dt / 1000;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            addInfoRow("时长", String.format(Locale.getDefault(), "%d:%02d", min, sec));
        }

        // Publish time
        long publishTime = song.optLong("publishTime", 0);
        if (publishTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            addInfoRow("发布时间", sdf.format(new Date(publishTime)));
        }

        // MV ID
        long mv = song.optLong("mv", 0);
        if (mv > 0) {
            addInfoRow("MV ID", String.valueOf(mv));
        }

        // Disc number & track number
        int cd = song.optInt("cd", 0);
        if (cd > 0) {
            addInfoRow("碟片", "Disc " + cd);
        }
        int no = song.optInt("no", 0);
        if (no > 0) {
            addInfoRow("曲号", String.valueOf(no));
        }

        // Fee type (0=free, 1=VIP, 4=paid album, 8=low quality free)
        int fee = song.optInt("fee", -1);
        if (fee >= 0) {
            String feeStr;
            switch (fee) {
                case 0: feeStr = "免费"; break;
                case 1: feeStr = "VIP歌曲"; break;
                case 4: feeStr = "购买专辑"; break;
                case 8: feeStr = "免费(低音质)"; break;
                default: feeStr = String.valueOf(fee); break;
            }
            addInfoRow("收费类型", feeStr);
        }

        // Popularity
        double pop = song.optDouble("pop", 0);
        if (pop > 0) {
            addInfoRow("热度", String.valueOf(Math.round(pop)));
        }

        // Quality info
        JSONObject h = song.optJSONObject("h");
        JSONObject m = song.optJSONObject("m");
        JSONObject l = song.optJSONObject("l");
        JSONObject sq = song.optJSONObject("sq");
        JSONObject hr = song.optJSONObject("hr");
        StringBuilder quality = new StringBuilder();
        if (hr != null) quality.append("Hi-Res ");
        if (sq != null) quality.append("无损 ");
        if (h != null) quality.append("高品质 ");
        if (m != null) quality.append("标准 ");
        if (l != null) quality.append("低品质 ");
        if (quality.length() > 0) {
            addInfoRow("音质", quality.toString().trim());
        }
        // Show bitrate details
        if (hr != null) addInfoRow("  Hi-Res", formatBitrate(hr));
        if (sq != null) addInfoRow("  无损", formatBitrate(sq));
        if (h != null) addInfoRow("  高品质", formatBitrate(h));
        if (m != null) addInfoRow("  标准", formatBitrate(m));
        if (l != null) addInfoRow("  低品质", formatBitrate(l));
    }

    private String formatBitrate(JSONObject quality) {
        long br = quality.optLong("br", 0);
        long size = quality.optLong("size", 0);
        StringBuilder sb = new StringBuilder();
        if (br > 0) sb.append(br / 1000).append("kbps");
        if (size > 0) {
            if (sb.length() > 0) sb.append(" / ");
            double mb = size / (1024.0 * 1024.0);
            sb.append(String.format(Locale.getDefault(), "%.1fMB", mb));
        }
        return sb.toString();
    }

    // ─── Step 2: Fetch song wiki ──────────────────────────────────────

    private void fetchSongWiki() {
        final TextView tvWikiLoading = makeText("加载歌曲百科...", COLOR_TEXT_SECONDARY, px(13), false, Gravity.CENTER);
        contentLayout.addView(makeSpacer(px(4)));
        contentLayout.addView(tvWikiLoading);

        MusicApiHelper.getSongWikiSummary(songId, cookie, new MusicApiHelper.SongWikiCallback() {
            @Override
            public void onResult(JSONObject wikiJson) {
                contentLayout.removeView(tvWikiLoading);
                parseSongWiki(wikiJson);
            }

            @Override
            public void onError(String message) {
                MusicLog.e(TAG, "歌曲百科加载失败: " + message);
                tvWikiLoading.setText("歌曲百科加载失败");
            }
        });
    }

    private void parseSongWiki(JSONObject wikiJson) {
        try {
            if (wikiJson.optInt("code") != 200) {
                return;
            }

            JSONObject data = wikiJson.optJSONObject("data");
            if (data == null) return;

            JSONArray blocks = data.optJSONArray("blocks");
            if (blocks == null || blocks.length() == 0) return;

            // Clear accumulated songs before processing all blocks
            currentBlockSongs.clear();

            // Iterate through ALL blocks and display their content
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;
                displayBlock(block);
            }

        } catch (Exception e) {
            MusicLog.e(TAG, "解析歌曲百科失败", e);
        }
    }

    private void displayBlock(JSONObject block) {
        // API returns "code" field (not "blockCode")
        String blockCode = block.optString("code", "");
        currentBlockCode = blockCode;
        // Note: don't clear currentBlockSongs here - accumulate all similar songs
        // so click handlers can reference the full list even after other blocks process

        // Get a user-friendly title for the block
        String blockTitle = getBlockTitle(blockCode);
        contentLayout.addView(makeSpacer(px(6)));
        contentLayout.addView(makeDivider());
        contentLayout.addView(makeSpacer(px(6)));
        addSectionHeader(getBlockIconRes(blockCode), blockTitle);

        // Show block-level UI element if present
        JSONObject blockUiElement = block.optJSONObject("uiElement");
        if (blockUiElement != null) {
            displayUiElement(blockUiElement);
        }

        // Parse creatives
        JSONArray creatives = block.optJSONArray("creatives");
        if (creatives != null) {
            for (int c = 0; c < creatives.length(); c++) {
                JSONObject creative = creatives.optJSONObject(c);
                if (creative == null) continue;
                displayCreative(creative);
            }
        }

        // Some blocks store data directly in extInfo (e.g. 回忆坐标, 歌单)
        JSONObject extInfo = block.optJSONObject("extInfo");
        if (extInfo != null && (creatives == null || creatives.length() == 0)) {
            displayExtInfo(extInfo);
        }
    }

    private String getBlockTitle(String blockCode) {
        if (blockCode == null || blockCode.isEmpty()) return "其他信息";
        switch (blockCode) {
            case "SONG_PLAY_ABOUT_SONG_BASIC":
            case "SONG_PLAY_ABOUT_SONG_BLOCK":      return "歌曲简介";
            case "SONG_PLAY_ABOUT_SIMILAR_SONG":
            case "SONG_PLAY_ABOUT_SIMILAR_SONG_BLOCK": return "相似歌曲";
            case "SONG_PLAY_ABOUT_RELATED_PLAYLIST":
            case "SONG_PLAY_ABOUT_PLAYLIST_BLOCK":  return "相关歌单";
            case "SONG_PLAY_ABOUT_MUSIC_MEMORY":
            case "SONG_PLAY_ABOUT_MEMORY_BLOCK":    return "回忆坐标";
            case "SONG_PLAY_ABOUT_MUSIC_SONG_GRADE": return "歌曲评分";
            case "SONG_PLAY_ABOUT_ARTIST":
            case "SONG_PLAY_ABOUT_ARTIST_BLOCK":    return "相关歌手";
            case "SONG_PLAY_ABOUT_ALBUM":
            case "SONG_PLAY_ABOUT_ALBUM_BLOCK":     return "所属专辑";
            case "SONG_PLAY_ABOUT_REC_SONG":        return "推荐歌曲";
            case "SONG_PLAY_ABOUT_TOPIC":           return "相关话题";
            case "SONG_PLAY_ABOUT_SONG_WIKI":       return "歌曲百科";
            case "SONG_PLAY_ABOUT_RELATED_CREATION": return "相关创作";
            default:
                return blockCode.replace("SONG_PLAY_ABOUT_", "")
                        .replace("_BLOCK", "")
                        .replace("_", " ");
        }
    }

    private int getBlockIconRes(String blockCode) {
        if (blockCode == null) return R.drawable.ic_info;
        switch (blockCode) {
            case "SONG_PLAY_ABOUT_SONG_BASIC":
            case "SONG_PLAY_ABOUT_SONG_BLOCK":
            case "SONG_PLAY_ABOUT_SONG_WIKI":       return R.drawable.ic_music_note;
            case "SONG_PLAY_ABOUT_SIMILAR_SONG":
            case "SONG_PLAY_ABOUT_SIMILAR_SONG_BLOCK":
            case "SONG_PLAY_ABOUT_REC_SONG":        return R.drawable.ic_queue_music;
            case "SONG_PLAY_ABOUT_RELATED_PLAYLIST":
            case "SONG_PLAY_ABOUT_PLAYLIST_BLOCK":  return R.drawable.ic_bookmark;
            case "SONG_PLAY_ABOUT_MUSIC_MEMORY":
            case "SONG_PLAY_ABOUT_MEMORY_BLOCK":    return R.drawable.ic_pin;
            case "SONG_PLAY_ABOUT_ARTIST":
            case "SONG_PLAY_ABOUT_ARTIST_BLOCK":    return R.drawable.ic_mic;
            case "SONG_PLAY_ABOUT_ALBUM":
            case "SONG_PLAY_ABOUT_ALBUM_BLOCK":     return R.drawable.ic_album;
            default:                                return R.drawable.ic_info;
        }
    }

    private void displayCreative(JSONObject creative) {
        String headline = creative.optString("headline", "");
        String desc = creative.optString("desc", "");

        if (!headline.isEmpty()) {
            contentLayout.addView(makeText(headline, COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
            contentLayout.addView(makeSpacer(px(2)));
        }
        if (!desc.isEmpty()) {
            contentLayout.addView(makeText(desc, COLOR_TEXT_DESC, px(14), false, Gravity.START));
            contentLayout.addView(makeSpacer(px(3)));
        }

        // Creative-level UI element
        JSONObject uiElement = creative.optJSONObject("uiElement");
        if (uiElement != null) {
            displayUiElement(uiElement);
        }

        // Parse resources
        JSONArray resources = creative.optJSONArray("resources");
        if (resources != null) {
            for (int r = 0; r < resources.length(); r++) {
                JSONObject resource = resources.optJSONObject(r);
                if (resource == null) continue;
                displayResource(resource);
            }
        }
    }

    /**
     * Display extInfo data from a block (used by 回忆坐标, 歌单 etc.)
     */
    private void displayExtInfo(JSONObject extInfo) {
        try {
            boolean isSongBlock = currentBlockCode.contains("SIMILAR_SONG")
                    || currentBlockCode.contains("REC_SONG");
            boolean isPlaylistBlock = currentBlockCode.contains("PLAYLIST");

            // Some extInfo contains songs array
            JSONArray songs = extInfo.optJSONArray("songs");
            if (songs != null) {
                // First pass: collect all songs for playlist
                List<Song> blockSongList = new ArrayList<>();
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject song = songs.optJSONObject(i);
                    if (song == null) continue;
                    String sName = song.optString("name", "");
                    // Try "ar" first (v3 format), then "artists" (v1 format)
                    JSONArray ar = song.optJSONArray("ar");
                    if (ar == null) ar = song.optJSONArray("artists");
                    String sArtist = "";
                    if (ar != null && ar.length() > 0) {
                        sArtist = ar.optJSONObject(0).optString("name", "");
                    }
                    long sId = song.optLong("id", 0);
                    if (!sName.isEmpty() && sId > 0) {
                        blockSongList.add(new Song(sId, sName, sArtist, ""));
                    }
                }
                if (isSongBlock) {
                    currentBlockSongs.addAll(blockSongList);
                }
                // Second pass: display
                for (int i = 0; i < blockSongList.size(); i++) {
                    Song s = blockSongList.get(i);
                    String display = s.getArtist().isEmpty() ? s.getName() : s.getName() + " - " + s.getArtist();
                    if (isSongBlock) {
                        final int idx = currentBlockSongs.size() - blockSongList.size() + i;
                        TextView tv = makeText("▶ " + display, COLOR_ACCENT, px(14), false, Gravity.START);
                        tv.setPadding(0, px(2), 0, px(2));
                        tv.setOnClickListener(v -> playSimilarSongs(currentBlockSongs, idx));
                        contentLayout.addView(tv);
                    } else {
                        contentLayout.addView(makeText("• " + display, COLOR_TEXT_DESC, px(14), false, Gravity.START));
                    }
                    contentLayout.addView(makeSpacer(px(2)));
                }
                return;
            }

            // Some extInfo contains playlists array
            JSONArray playlists = extInfo.optJSONArray("playlists");
            if (playlists != null) {
                for (int i = 0; i < playlists.length(); i++) {
                    JSONObject pl = playlists.optJSONObject(i);
                    if (pl == null) continue;
                    String plName = pl.optString("name", "");
                    int playCount = pl.optInt("playCount", 0);
                    if (!plName.isEmpty()) {
                        String display = plName;
                        if (playCount > 0) {
                            display += "  ▶ " + formatCount(playCount);
                        }
                        if (isPlaylistBlock) {
                            final long plId = pl.optLong("id", 0);
                            final String fPlName = plName;
                            final int fTrackCount = pl.optInt("trackCount", 0);
                            TextView tv = makeText("" + display, COLOR_ACCENT, px(14), false, Gravity.START);
                            tv.setPadding(0, px(2), 0, px(2));
                            if (plId > 0) {
                                tv.setOnClickListener(v -> openPlaylistDetail(plId, fPlName, fTrackCount, ""));
                            }
                            contentLayout.addView(tv);
                        } else {
                            contentLayout.addView(makeText("• " + display, COLOR_TEXT_DESC, px(14), false, Gravity.START));
                        }
                        contentLayout.addView(makeSpacer(px(2)));
                    }
                }
                return;
            }

            // Some extInfo has direct text content (回忆坐标 etc.)
            String text = extInfo.optString("text", "");
            if (!text.isEmpty()) {
                contentLayout.addView(makeText(text, COLOR_TEXT_DESC, px(14), false, Gravity.START));
                return;
            }

            // Try to display any top-level string values
            Iterator<String> keys = extInfo.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = extInfo.opt(key);
                if (val == null) continue;
                String valStr = val.toString();
                if (valStr.isEmpty() || "null".equals(valStr)) continue;
                if (valStr.startsWith("{") || valStr.startsWith("[")) continue;
                if (valStr.length() > 300) continue;
                contentLayout.addView(makeText(valStr, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                contentLayout.addView(makeSpacer(px(2)));
            }
        } catch (Exception e) {
            MusicLog.e(TAG, "解析extInfo失败", e);
        }
    }

    private void displayResource(JSONObject resource) {
        // Try resourceInfo first (used by song/artist/album blocks)
        JSONObject resInfo = resource.optJSONObject("resourceInfo");

        // Also check for resource-level uiElement (used by 回忆坐标, 相似歌曲, 歌单 etc.)
        JSONObject resUiElement = resource.optJSONObject("uiElement");

        // If neither exists, nothing to show
        if (resInfo == null && resUiElement == null) return;

        // Create a card for each resource
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(8), px(6), px(8), px(6));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD_BG);
        cardBg.setCornerRadius(px(6));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, px(3), 0, px(3));
        card.setLayoutParams(cardParams);

        // Display from uiElement if present (common in many block types)
        if (resUiElement != null) {
            JSONObject mainTitle = resUiElement.optJSONObject("mainTitle");
            if (mainTitle != null) {
                String title = mainTitle.optString("title", "");
                if (!title.isEmpty()) {
                    card.addView(makeText(title, COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
                }
            }
            JSONObject subTitle = resUiElement.optJSONObject("subTitle");
            if (subTitle != null) {
                String title = subTitle.optString("title", "");
                if (!title.isEmpty()) {
                    card.addView(makeSmallLabel(title));
                }
            }
            String ueDesc = resUiElement.optString("description", "");
            if (!ueDesc.isEmpty()) {
                card.addView(makeText(ueDesc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
            // Image text (used by some blocks)
            JSONObject image = resUiElement.optJSONObject("image");
            if (image != null) {
                String imageText = image.optString("title", "");
                if (!imageText.isEmpty()) {
                    card.addView(makeSmallLabel(imageText));
                }
            }
        }

        // Display from resourceInfo if present
        if (resInfo != null) {
            // Name
            String name = resInfo.optString("name", "");
            if (!name.isEmpty()) {
                // Avoid duplicate if uiElement already showed a title
                if (resUiElement == null || resUiElement.optJSONObject("mainTitle") == null) {
                    card.addView(makeText(name, COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
                }
            }

            // Artists
            JSONArray artists = resInfo.optJSONArray("artist");
            if (artists != null && artists.length() > 0) {
                StringBuilder artistStr = new StringBuilder();
                for (int a = 0; a < artists.length(); a++) {
                    JSONObject art = artists.optJSONObject(a);
                    if (art == null) continue;
                    if (artistStr.length() > 0) artistStr.append(" / ");
                    artistStr.append(art.optString("name", ""));
                    if (a == 0 && artistId <= 0) {
                        artistId = art.optLong("id", 0);
                    }
                }
                card.addView(makeSmallLabel("歌手: " + artistStr.toString()));
            }

            // Album
            JSONObject album = resInfo.optJSONObject("album");
            if (album != null) {
                String albumName = album.optString("name", "");
                if (!albumName.isEmpty()) {
                    card.addView(makeSmallLabel("专辑: " + albumName));
                }
            }

            // Description
            String desc = resInfo.optString("desc", "");
            if (!desc.isEmpty()) {
                card.addView(makeSpacer(px(3)));
                card.addView(makeText(desc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
        }

        // Also check for extInfo at the resource level (some blocks put data here)
        JSONObject extInfo = resource.optJSONObject("resourceExtInfo");
        if (extInfo != null) {
            // Some blocks store artists/songs/playlists here
            JSONArray songData = extInfo.optJSONArray("songs");
            if (songData != null) {
                for (int s = 0; s < songData.length(); s++) {
                    JSONObject song = songData.optJSONObject(s);
                    if (song == null) continue;
                    String sName = song.optString("name", "");
                    if (!sName.isEmpty()) {
                        card.addView(makeSmallLabel("• " + sName));
                    }
                }
            }
            // Playlist info
            String playlistName = extInfo.optString("name", "");
            if (!playlistName.isEmpty() && resInfo == null) {
                card.addView(makeSmallLabel(playlistName));
            }
            int playCount = extInfo.optInt("playCount", 0);
            if (playCount > 0) {
                card.addView(makeSmallLabel("播放: " + formatCount(playCount)));
            }
            int trackCount = extInfo.optInt("trackCount", 0);
            if (trackCount > 0) {
                card.addView(makeSmallLabel("歌曲数: " + trackCount));
            }
        }

        // Only add card if it has children
        if (card.getChildCount() > 0) {
            // Add click handler for song blocks
            boolean isSongBlock = currentBlockCode.contains("SIMILAR_SONG")
                    || currentBlockCode.contains("REC_SONG");
            boolean isPlaylistBlock = currentBlockCode.contains("PLAYLIST");

            if (isSongBlock) {
                long resId = resInfo != null ? resInfo.optLong("id", 0) : 0;
                if (resId <= 0) resId = parseResourceId(resource);
                String resName = resInfo != null ? resInfo.optString("name", "") : "";
                if (resName.isEmpty() && resUiElement != null) {
                    JSONObject mt = resUiElement.optJSONObject("mainTitle");
                    if (mt != null) resName = mt.optString("title", "");
                }
                String resArtist = "";
                if (resInfo != null) {
                    JSONArray ars = resInfo.optJSONArray("artist");
                    if (ars == null) ars = resInfo.optJSONArray("artists");
                    if (ars != null && ars.length() > 0) {
                        JSONObject a0 = ars.optJSONObject(0);
                        if (a0 != null) resArtist = a0.optString("name", "");
                    }
                }
                if (resId > 0) {
                    currentBlockSongs.add(new Song(resId, resName, resArtist, ""));
                }
                card.addView(makeSmallLabel("▶ 点击播放"));
                final int songIdx = currentBlockSongs.size() - 1;
                if (resId > 0) {
                    card.setClickable(true);
                    card.setOnClickListener(v -> playSimilarSongs(currentBlockSongs, songIdx));
                }
            } else if (isPlaylistBlock) {
                long plId = resInfo != null ? resInfo.optLong("id", 0) : 0;
                if (plId <= 0 && extInfo != null) plId = extInfo.optLong("id", 0);
                if (plId <= 0) plId = parseResourceId(resource);
                String plName = "";
                if (resInfo != null) plName = resInfo.optString("name", "");
                if (plName.isEmpty() && resUiElement != null) {
                    JSONObject mt = resUiElement.optJSONObject("mainTitle");
                    if (mt != null) plName = mt.optString("title", "");
                }
                int plTrackCount = 0;
                if (extInfo != null) plTrackCount = extInfo.optInt("trackCount", 0);
                if (plTrackCount <= 0 && resInfo != null) plTrackCount = resInfo.optInt("trackCount", 0);
                card.addView(makeSmallLabel("点击查看歌单"));
                final long finalPlId = plId;
                final String finalPlName = plName;
                final int finalPlTrackCount = plTrackCount;
                if (finalPlId > 0) {
                    card.setClickable(true);
                    card.setOnClickListener(v -> openPlaylistDetail(finalPlId, finalPlName, finalPlTrackCount, ""));
                }
            }
            contentLayout.addView(card);
        }
    }

    private String formatCount(int count) {
        if (count >= 100000000) {
            return String.format(Locale.getDefault(), "%.1f亿", count / 100000000.0);
        } else if (count >= 10000) {
            return String.format(Locale.getDefault(), "%.1f万", count / 10000.0);
        }
        return String.valueOf(count);
    }

    /** Parse resource ID trying numeric 'id' field first, then 'resourceId' string. */
    private long parseResourceId(JSONObject resource) {
        // Try numeric id first
        long id = resource.optLong("id", 0);
        if (id > 0) return id;
        // Try resourceId string field (common in wiki API responses)
        String rid = resource.optString("resourceId", "");
        if (!rid.isEmpty()) {
            try { return Long.parseLong(rid); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private void displayUiElement(JSONObject uiElement) {
        JSONObject mainTitle = uiElement.optJSONObject("mainTitle");
        if (mainTitle != null) {
            String title = mainTitle.optString("title", "");
            if (!title.isEmpty()) {
                contentLayout.addView(makeText(title, COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
            }
        }
        JSONObject subTitle = uiElement.optJSONObject("subTitle");
        if (subTitle != null) {
            String title = subTitle.optString("title", "");
            if (!title.isEmpty()) {
                contentLayout.addView(makeText(title, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
        }
        String description = uiElement.optString("description", "");
        if (!description.isEmpty()) {
            contentLayout.addView(makeText(description, COLOR_TEXT_DESC, px(13), false, Gravity.START));
        }
    }

    // ─── Step 3: Fetch artist description ─────────────────────────────

    private void fetchArtistDesc() {
        if (artistId <= 0) {
            MusicLog.d(TAG, "无歌手ID，跳过歌手百科请求");
            fetchSongWiki();
            return;
        }

        contentLayout.addView(makeSpacer(px(6)));
        contentLayout.addView(makeDivider());
        contentLayout.addView(makeSpacer(px(6)));
        addSectionHeader(R.drawable.ic_mic, "歌手百科");

        final TextView tvArtistLoading = makeText("加载歌手详情...", COLOR_TEXT_SECONDARY, px(13), false, Gravity.START);
        contentLayout.addView(tvArtistLoading);

        MusicLog.op(TAG, "请求歌手百科", "artistId=" + artistId);

        MusicApiHelper.getArtistDesc(artistId, cookie, new MusicApiHelper.ArtistDescCallback() {
            @Override
            public void onResult(String briefDesc, JSONArray introduction) {
                MusicLog.d(TAG, "歌手百科返回: briefDesc长度=" + (briefDesc != null ? briefDesc.length() : 0)
                        + " introduction段数=" + (introduction != null ? introduction.length() : 0));
                contentLayout.removeView(tvArtistLoading);
                displayArtistDesc(briefDesc, introduction);
                fetchSongWiki();
            }

            @Override
            public void onError(String message) {
                MusicLog.e(TAG, "歌手百科获取失败: " + message);
                tvArtistLoading.setText("歌手详情加载失败: " + message);
                fetchSongWiki();
            }
        });
    }

    private void displayArtistDesc(String briefDesc, JSONArray introduction) {
        if ((briefDesc == null || briefDesc.isEmpty())
                && (introduction == null || introduction.length() == 0)) {
            contentLayout.addView(makeText("暂无歌手百科信息", COLOR_TEXT_SECONDARY, px(13), false, Gravity.START));
            contentLayout.addView(makeSpacer(px(16)));
            return;
        }

        if (briefDesc != null && !briefDesc.isEmpty()) {
            contentLayout.addView(makeText("简介", COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
            contentLayout.addView(makeSpacer(px(3)));
            contentLayout.addView(makeText(briefDesc, COLOR_TEXT_DESC, px(14), false, Gravity.START));
        }

        if (introduction != null && introduction.length() > 0) {
            for (int i = 0; i < introduction.length(); i++) {
                JSONObject section = introduction.optJSONObject(i);
                if (section == null) continue;

                String ti = section.optString("ti", "");
                String txt = section.optString("txt", "");

                if (!ti.isEmpty()) {
                    contentLayout.addView(makeSpacer(px(8)));
                    contentLayout.addView(makeText(ti, COLOR_TEXT_PRIMARY, px(15), true, Gravity.START));
                }
                if (!txt.isEmpty()) {
                    contentLayout.addView(makeSpacer(px(3)));
                    contentLayout.addView(makeText(txt, COLOR_TEXT_DESC, px(14), false, Gravity.START));
                }
            }
        }

        contentLayout.addView(makeSpacer(px(16)));
    }

    // ── Playback helpers ────────────────────────────────────────────────

    /**
     * Play a song from a captured similar songs list, filling the playlist with all similar songs.
     */
    private void playSimilarSongs(List<Song> songList, int index) {
        if (songList.isEmpty() || index < 0 || index >= songList.size()) return;
        MusicPlayerManager.getInstance().setPlaylist(new ArrayList<>(songList), index);
        MusicPlayerManager.getInstance().playCurrent();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openPlaylistDetail(long playlistId, String name, int trackCount, String creator) {
        Intent intent = new Intent(this, PlaylistDetailActivity.class);
        intent.putExtra("playlist_id", playlistId);
        intent.putExtra("playlist_name", name != null ? name : "");
        intent.putExtra("track_count", trackCount);
        intent.putExtra("creator", creator != null ? creator : "");
        // creator_user_id and is_liked_playlist not available from simiPlaylist API;
        // PlaylistDetailActivity will self-correct from API response
        startActivity(intent);
    }

    // ── UI helpers ──────────────────────────────────────────────────────

    private void addSectionHeader(String title) {
        addSectionHeader(R.drawable.ic_info, title);
    }

    private void addSectionHeader(int iconRes, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, px(2), 0, 0);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(COLOR_ACCENT);
        int iconSize = px(15);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(px(5));
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(COLOR_ACCENT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);

        contentLayout.addView(row);
        contentLayout.addView(makeSpacer(px(4)));
    }

    private void addInfoRow(String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, px(1), 0, px(1));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + "：");
        tvLabel.setTextColor(COLOR_TEXT_SECONDARY);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(COLOR_TEXT_PRIMARY);
        tvValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(tvLabel);
        row.addView(tvValue);
        contentLayout.addView(row);
    }

    private TextView makeSmallLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT_SECONDARY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(12));
        tv.setPadding(0, px(1), 0, 0);
        return tv;
    }

    private TextView makeText(String text, int color, int sizePx, boolean bold, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(gravity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        return tv;
    }

    private View makeSpacer(int heightPx) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return spacer;
    }

    private View makeDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1)));
        return divider;
    }

    private int px(int baseValue) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return (int) (baseValue * screenWidth / 320f + 0.5f);
    }
}
