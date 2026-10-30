package com.qinghe.music163pro.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
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
import com.qinghe.music163pro.util.WatchUiUtils;
import com.qinghe.music163pro.api.MusicApiHelper;
import com.qinghe.music163pro.model.Song;
import com.qinghe.music163pro.player.MusicPlayerManager;
import com.qinghe.music163pro.util.MusicLog;
import com.qinghe.music163pro.util.NetworkImageLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private static final int COLOR_BG = 0xFF141218;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE6E0E9;
    private static final int COLOR_TEXT_SECONDARY = 0xFFCAC4D0;
    private static final int COLOR_TEXT_DESC = 0xFFCAC4D0;
    private static final int COLOR_ACCENT = 0xFFD0BCFF;
    private static final int COLOR_DIVIDER = 0x1FFFFFFF;
    private static final int COLOR_CARD_BG = 0xFF2B2930;

    /** Card container for the section currently being filled. */
    private LinearLayout currentSectionCard;

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
    // VIP badge views paired with their song (fee may arrive asynchronously)
    private final java.util.LinkedHashMap<Song, TextView> feeBadges = new java.util.LinkedHashMap<>();
    private final java.util.Set<Long> feeRequested = new java.util.HashSet<>();

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
        sectionAdd(makeText("音乐信息", COLOR_TEXT_PRIMARY, px(13), true, Gravity.CENTER));
        sectionAdd(makeSpacer(px(8)));

        // Loading indicator
        tvLoading = makeText("加载中...", COLOR_TEXT_SECONDARY, px(13), false, Gravity.CENTER);
        sectionAdd(tvLoading);

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
        String name = text(song, "name");
        if (!name.isEmpty()) {
            sectionAdd(makeText(name, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
            sectionAdd(makeSpacer(px(4)));
        }

        // Album cover (al.picUrl) - shown below title, 300x300
        JSONObject al = song.optJSONObject("al");
        if (al != null) {
            String picUrl = text(al, "picUrl");
            if (!picUrl.isEmpty()) {
                // Square cover sized to the card's inner width: screen minus
                // the content padding (px(10) x2) and the card padding (px(10) x2).
                int coverSize = getResources().getDisplayMetrics().widthPixels - px(40);
                ImageView coverView = WatchUiUtils.createRoundedImage(this);
                coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable coverBg = new GradientDrawable();
                coverBg.setColor(COLOR_CARD_BG);
                coverBg.setCornerRadius(px(8));
                coverView.setBackground(coverBg);
                LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(coverSize, coverSize);
                coverParams.gravity = Gravity.CENTER_HORIZONTAL;
                coverParams.setMargins(0, px(4), 0, px(8));
                coverView.setLayoutParams(coverParams);
                NetworkImageLoader.load(coverView, picUrl);
                sectionAdd(coverView);
            }
        }

        // Song aliases (别名/副标题)
        JSONArray alia = song.optJSONArray("alia");
        if (alia != null && alia.length() > 0) {
            StringBuilder aliases = new StringBuilder();
            for (int i = 0; i < alia.length(); i++) {
                if (aliases.length() > 0) aliases.append(" / ");
                aliases.append(text(alia, i));
            }
            if (aliases.length() > 0) {
                addInfoRow("别名", aliases.toString());
            }
        }

        // Song translations (译名, e.g. "Normal No More" → "不再平庸")
        JSONArray tns = song.optJSONArray("tns");
        if (tns != null && tns.length() > 0) {
            StringBuilder translations = new StringBuilder();
            for (int i = 0; i < tns.length(); i++) {
                String t = text(tns, i);
                if (t.isEmpty()) continue;
                if (translations.length() > 0) translations.append(" / ");
                translations.append(t);
            }
            if (translations.length() > 0) {
                addInfoRow("译名", translations.toString());
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
                artistStr.append(text(a, "name"));
                // Extract first artist ID
                if (i == 0 && artistId <= 0) {
                    artistId = a.optLong("id", 0);
                }
            }
            addInfoRow("歌手", artistStr.toString());
        }

        // Album info
        if (al != null) {
            String albumName = text(al, "name");
            if (!albumName.isEmpty()) {
                addInfoRow("专辑", albumName);
            }
            long albumId = al.optLong("id", 0);
            if (albumId > 0) {
                addInfoRow("专辑ID", String.valueOf(albumId));
            }
            JSONArray alTns = al.optJSONArray("tns");
            if (alTns != null && alTns.length() > 0) {
                StringBuilder albumT = new StringBuilder();
                for (int i = 0; i < alTns.length(); i++) {
                    String t = text(alTns, i);
                    if (t.isEmpty()) continue;
                    if (albumT.length() > 0) albumT.append(" / ");
                    albumT.append(t);
                }
                if (albumT.length() > 0) {
                    addInfoRow("专辑译名", albumT.toString());
                }
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
        int sr = quality.optInt("sr", 0);
        StringBuilder sb = new StringBuilder();
        if (br > 0) sb.append(br / 1000).append("kbps");
        // 采样率: sr 单位 Hz (44100 → 44.1kHz, 48000 → 48kHz)
        if (sr >= 1000) {
            if (sb.length() > 0) sb.append(" / ");
            if (sr % 1000 == 0) {
                sb.append(sr / 1000).append("kHz");
            } else {
                sb.append(String.format(Locale.getDefault(), "%.1fkHz", sr / 1000f));
            }
        }
        if (size > 0) {
            if (sb.length() > 0) sb.append(" / ");
            double mb = size / (1024.0 * 1024.0);
            sb.append(String.format(Locale.getDefault(), "%.1fMB", mb));
        }
        return sb.toString();
    }

    // ─── Step 2: Fetch song wiki ──────────────────────────────────────

    private void fetchSongWiki() {
        final TextView tvWikiLoading = makeText("加载歌曲百科...", COLOR_TEXT_SECONDARY, px(12), false, Gravity.CENTER);
        sectionAdd(makeSpacer(px(4)));
        sectionAdd(tvWikiLoading);

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
        sectionAdd(makeSpacer(px(6)));
        sectionAdd(makeSpacer(px(6)));
        addSectionHeader(getBlockIconRes(blockCode), blockTitle);

        // Show block-level UI element if present (skip mainTitle that just
        // repeats the section header, e.g. 相似歌曲/相关歌单/回忆坐标)
        JSONObject blockUiElement = block.optJSONObject("uiElement");
        if (blockUiElement != null) {
            displayUiElement(blockUiElement, blockTitle);
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
        String headline = text(creative, "headline");
        String desc = text(creative, "desc");

        if (!headline.isEmpty()) {
            sectionAdd(makeText(headline, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
            sectionAdd(makeSpacer(px(2)));
        }
        if (!desc.isEmpty()) {
            sectionAdd(makeText(desc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            sectionAdd(makeSpacer(px(3)));
        }

        // Creative-level UI element
        JSONObject uiElement = creative.optJSONObject("uiElement");
        if (uiElement != null) {
            displayUiElement(uiElement);
        }

        // Parse resources. Text-only tag resources (曲风/推荐标签 — one tiny
        // title per resource) are joined into ONE flowing line: rendering each
        // on its own row made 2-char titles waste a line each and duplicated
        // the white bold title style onto what is really content.
        JSONArray resources = creative.optJSONArray("resources");
        if (resources != null && resources.length() > 0 && allInlineTags(resources)) {
            StringBuilder tags = new StringBuilder();
            for (int r = 0; r < resources.length(); r++) {
                JSONObject res = resources.optJSONObject(r);
                if (res == null) continue;
                JSONObject rUe = res.optJSONObject("uiElement");
                JSONObject rMt = rUe != null ? rUe.optJSONObject("mainTitle") : null;
                String t = rMt != null ? text(rMt, "title") : "";
                if (t.isEmpty()) continue;
                if (tags.length() > 0) tags.append(" · ");
                tags.append(t);
            }
            if (tags.length() > 0) {
                sectionAdd(makeText(tags.toString(), COLOR_TEXT_DESC, px(13), false, Gravity.START));
                sectionAdd(makeSpacer(px(3)));
            }
        } else if (resources != null) {
            for (int r = 0; r < resources.length(); r++) {
                JSONObject resource = resources.optJSONObject(r);
                if (resource == null) continue;
                displayResource(resource);
            }
        }
    }

    /**
     * True when every resource is a text-only tag (no id/cover/info): its
     * mainTitle IS the whole content — e.g. 曲风 → "流行-欧美流行",
     * 推荐标签 → 甜蜜 / 活力 / 放松 …
     */
    private static boolean allInlineTags(JSONArray resources) {
        for (int i = 0; i < resources.length(); i++) {
            JSONObject res = resources.optJSONObject(i);
            if (res == null) return false;
            if (res.optLong("id", 0) > 0 || !text(res, "resourceId").isEmpty()) return false;
            if (res.optJSONObject("resourceInfo") != null) return false;
            if (res.optJSONObject("resourceExt") != null) return false;
            if (res.optJSONObject("resourceExtInfo") != null) return false;
            if ("sheet_music".equals(text(res, "resourceType"))) return false;
            if (res.optJSONArray("images") != null) return false;
            JSONObject ue = res.optJSONObject("uiElement");
            if (ue == null) return false;
            JSONObject mt = ue.optJSONObject("mainTitle");
            if (mt == null || text(mt, "title").isEmpty()) return false;
            JSONArray sub = ue.optJSONArray("subTitles");
            if (sub != null && sub.length() > 0) return false;
            JSONArray tl = ue.optJSONArray("textLinks");
            if (tl != null && tl.length() > 0) return false;
            JSONArray im = ue.optJSONArray("images");
            if (im != null && im.length() > 0) return false;
            JSONArray ds = ue.optJSONArray("descriptions");
            if (ds != null && ds.length() > 0) return false;
        }
        return true;
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
                    String sName = text(song, "name");
                    // Try "ar" first (v3 format), then "artists" (v1 format)
                    JSONArray ar = song.optJSONArray("ar");
                    if (ar == null) ar = song.optJSONArray("artists");
                    String sArtist = "";
                    if (ar != null && ar.length() > 0) {
                        JSONObject a0 = ar.optJSONObject(0);
                        if (a0 != null) sArtist = text(a0, "name");
                    }
                    long sId = song.optLong("id", 0);
                    if (!sName.isEmpty() && sId > 0) {
                        Song blockSong = new Song(sId, sName, sArtist, "");
                        int fee = song.optInt("fee", -1);
                        if (fee >= 0) blockSong.setFee(fee);
                        blockSongList.add(blockSong);
                    }
                }
                if (isSongBlock) {
                    currentBlockSongs.addAll(blockSongList);
                }
                // Second pass: display
                for (int i = 0; i < blockSongList.size(); i++) {
                    Song s = blockSongList.get(i);
                    String display = s.getArtist().isEmpty() ? s.getDisplayName() : s.getDisplayName() + " - " + s.getArtist();
                    if (isSongBlock) {
                        final int idx = currentBlockSongs.size() - blockSongList.size() + i;
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        TextView tv = makeText("▶ " + display, COLOR_ACCENT, px(13), false, Gravity.START);
                        tv.setPadding(0, px(2), 0, px(2));
                        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        TextView badge = createVipBadge();
                        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        badgeLp.setMarginEnd(px(4));
                        row.addView(badge, badgeLp);
                        row.addView(tv);
                        feeBadges.put(s, badge);
                        row.setOnClickListener(v -> playSimilarSongs(currentBlockSongs, idx));
                        sectionAdd(row);
                    } else {
                        sectionAdd(makeText("• " + display, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                    }
                    sectionAdd(makeSpacer(px(2)));
                }
                if (isSongBlock) {
                    requestFeeEnrichment();
                }
                return;
            }

            // Some extInfo contains playlists array
            JSONArray playlists = extInfo.optJSONArray("playlists");
            if (playlists != null) {
                for (int i = 0; i < playlists.length(); i++) {
                    JSONObject pl = playlists.optJSONObject(i);
                    if (pl == null) continue;
                    String plName = text(pl, "name");
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
                            TextView tv = makeText("" + display, COLOR_ACCENT, px(13), false, Gravity.START);
                            tv.setPadding(0, px(2), 0, px(2));
                            if (plId > 0) {
                                tv.setOnClickListener(v -> openPlaylistDetail(plId, fPlName, fTrackCount, ""));
                            }
                            sectionAdd(tv);
                        } else {
                            sectionAdd(makeText("• " + display, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                        }
                        sectionAdd(makeSpacer(px(2)));
                    }
                }
                return;
            }

            // Some extInfo has direct text content (回忆坐标 etc.)
            String content = text(extInfo, "text");
            if (!content.isEmpty()) {
                sectionAdd(makeText(content, COLOR_TEXT_DESC, px(13), false, Gravity.START));
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
                sectionAdd(makeText(valStr, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                sectionAdd(makeSpacer(px(2)));
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

        // Extract cover URL for card thumbnail
        String coverUrlRes = "";
        String sheetImgTitle = "";
        if (resUiElement != null) {
            // images is an array in the API response
            JSONArray imgs = resUiElement.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) {
                JSONObject img0 = imgs.optJSONObject(0);
                if (img0 != null) {
                    coverUrlRes = text(img0, "imageUrl");
                    if (coverUrlRes.isEmpty()) coverUrlRes = text(img0, "url");
                    sheetImgTitle = text(img0, "title");
                }
            }
            // Fallback: legacy single-object form
            if (coverUrlRes.isEmpty()) {
                JSONObject img = resUiElement.optJSONObject("image");
                if (img != null) {
                    coverUrlRes = text(img, "imageUrl");
                    if (coverUrlRes.isEmpty()) coverUrlRes = text(img, "url");
                }
            }
        }
        if (coverUrlRes.isEmpty() && resInfo != null) {
            JSONObject al = resInfo.optJSONObject("al");
            if (al != null) {
                coverUrlRes = text(al, "picUrl");
            }
            if (coverUrlRes.isEmpty()) {
                coverUrlRes = text(resInfo, "picUrl");
            }
            if (coverUrlRes.isEmpty()) {
                coverUrlRes = text(resInfo, "coverImgUrl");
            }
        }

        // Song cards use the standard list row format (like other song lists)
        boolean songCard = currentBlockCode.contains("SIMILAR_SONG")
                || currentBlockCode.contains("REC_SONG");
        if (songCard) {
            displaySongCard(resource, resInfo, resUiElement, coverUrlRes);
            return;
        }

        // 乐谱 (sheet_music) cards download the image directly on tap.
        boolean sheetCard = "sheet_music".equals(resource.optString("resourceType", ""));

        // Create a horizontal card: [cover thumbnail] + [content column].
        // Pure-text resources (comments/awards/memory text...) render FLAT on
        // the page — no card background and no empty thumbnail placeholder.
        boolean flatText = coverUrlRes.isEmpty() && !sheetCard;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        if (flatText) {
            LinearLayout.LayoutParams flatLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            flatLp.setMargins(0, px(2), 0, px(2));
            card.setLayoutParams(flatLp);
        } else {
            card.setPadding(px(8), px(6), px(8), px(6));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(COLOR_CARD_BG);
            cardBg.setCornerRadius(px(6));
            card.setBackground(cardBg);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, px(3), 0, px(3));
            card.setLayoutParams(cardParams);

            // Cover thumbnail (40x40dp)
            ImageView ivCover = WatchUiUtils.createRoundedImage(this);
            int coverSize = px(40);
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(coverSize, coverSize);
            coverLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            ivCover.setLayoutParams(coverLp);
            ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable coverBg = new GradientDrawable();
            coverBg.setColor(0xFF2B2930);
            coverBg.setCornerRadius(px(3));
            ivCover.setBackground(coverBg);
            if (!coverUrlRes.isEmpty()) {
                NetworkImageLoader.load(ivCover, coverUrlRes);
            }
            card.addView(ivCover);
        }

        // Content column (right side)
        LinearLayout rightLayout = new LinearLayout(this);
        rightLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rightLp.setMarginStart(flatText ? 0 : px(8));
        rightLayout.setLayoutParams(rightLp);
        card.addView(rightLayout);

        // Display from uiElement if present (common in many block types)
        if (resUiElement != null) {
            JSONObject mainTitle = resUiElement.optJSONObject("mainTitle");
            if (mainTitle != null) {
                String title = text(mainTitle, "title");
                if (!title.isEmpty()) {
                    rightLayout.addView(makeText(title, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
                }
            }
            // subTitles is an array in the API response — join ALL entries
            // (e.g. songAward: "获" + "华语榜年度十大单曲"; first-only lost the award)
            JSONArray subTitles = resUiElement.optJSONArray("subTitles");
            if (subTitles != null && subTitles.length() > 0) {
                StringBuilder stJoined = new StringBuilder();
                for (int i = 0; i < subTitles.length(); i++) {
                    JSONObject st = subTitles.optJSONObject(i);
                    if (st == null) continue;
                    String title = text(st, "title");
                    if (title.isEmpty()) continue;
                    if (stJoined.length() > 0) stJoined.append(" ");
                    stJoined.append(title);
                }
                if (stJoined.length() > 0) {
                    rightLayout.addView(makeSmallLabel(stJoined.toString()));
                }
            } else {
                // Fallback: legacy single-object form
                JSONObject subTitle = resUiElement.optJSONObject("subTitle");
                if (subTitle != null) {
                    String title = text(subTitle, "title");
                    if (!title.isEmpty()) {
                        rightLayout.addView(makeSmallLabel(title));
                    }
                }
            }
            String ueDesc = text(resUiElement, "description");
            if (!ueDesc.isEmpty()) {
                rightLayout.addView(makeText(ueDesc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
            // descriptions array carries the actual text for 乐评 etc.
            // (singular "description" is always absent in real responses)
            JSONArray descs = resUiElement.optJSONArray("descriptions");
            if (descs != null && descs.length() > 0) {
                for (int i = 0; i < descs.length(); i++) {
                    JSONObject d = descs.optJSONObject(i);
                    if (d == null) continue;
                    String dText = text(d, "description");
                    if (!dText.isEmpty()) {
                        rightLayout.addView(makeText(dText, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                        rightLayout.addView(makeSpacer(px(3)));
                    }
                }
            }
            // Resource-level textLinks (values some cards put here)
            JSONArray resTextLinks = resUiElement.optJSONArray("textLinks");
            if (resTextLinks != null && resTextLinks.length() > 0) {
                StringBuilder rlValues = new StringBuilder();
                for (int i = 0; i < resTextLinks.length(); i++) {
                    JSONObject link = resTextLinks.optJSONObject(i);
                    if (link == null) continue;
                    String value = text(link, "text");
                    if (value.isEmpty()) continue;
                    if (rlValues.length() > 0) rlValues.append(" / ");
                    rlValues.append(value);
                }
                if (rlValues.length() > 0) {
                    rightLayout.addView(makeSmallLabel(rlValues.toString()));
                }
            }
            // Image text (used by some blocks)
            JSONArray imgArr = resUiElement.optJSONArray("images");
            if (imgArr != null && imgArr.length() > 0) {
                JSONObject img0 = imgArr.optJSONObject(0);
                if (img0 != null) {
                    String imageText = text(img0, "title");
                    if (!imageText.isEmpty()) {
                        rightLayout.addView(makeSmallLabel(imageText));
                    }
                }
            } else {
                JSONObject image = resUiElement.optJSONObject("image");
                if (image != null) {
                    String imageText = text(image, "title");
                    if (!imageText.isEmpty()) {
                        rightLayout.addView(makeSmallLabel(imageText));
                    }
                }
            }
        }

        // Display from resourceInfo if present
        if (resInfo != null) {
            // Name
            String name = text(resInfo, "name");
            if (!name.isEmpty()) {
                // Avoid duplicate if uiElement already showed a title
                if (resUiElement == null || resUiElement.optJSONObject("mainTitle") == null) {
                    rightLayout.addView(makeText(name, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
                }
            }

            // Artists - check `ar` first, then `artist`
            JSONArray artists = resInfo.optJSONArray("ar");
            if (artists == null) artists = resInfo.optJSONArray("artist");
            if (artists != null && artists.length() > 0) {
                StringBuilder artistStr = new StringBuilder();
                for (int a = 0; a < artists.length(); a++) {
                    JSONObject art = artists.optJSONObject(a);
                    if (art == null) continue;
                    if (artistStr.length() > 0) artistStr.append(" / ");
                    artistStr.append(text(art, "name"));
                    if (a == 0 && artistId <= 0) {
                        artistId = art.optLong("id", 0);
                    }
                }
                rightLayout.addView(makeSmallLabel("歌手: " + artistStr.toString()));
            }

            // Album
            JSONObject album = resInfo.optJSONObject("album");
            if (album == null) album = resInfo.optJSONObject("al");
            if (album != null) {
                String albumName = text(album, "name");
                if (!albumName.isEmpty()) {
                    rightLayout.addView(makeSmallLabel("专辑: " + albumName));
                }
            }

            // Description
            String desc = text(resInfo, "desc");
            if (!desc.isEmpty()) {
                rightLayout.addView(makeSpacer(px(3)));
                rightLayout.addView(makeText(desc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
        }

        // 回忆坐标 (memoryGrid) stores its content in resourceExt — not
        // resourceExtInfo: FIRST_LISTEN → {date, period, season},
        // TOTAL_PLAY (累计播放) → {playCount, text}
        JSONObject resourceExt = resource.optJSONObject("resourceExt");
        if (resourceExt != null) {
            JSONObject firstListen = resourceExt.optJSONObject("musicFirstListenDto");
            if (firstListen != null) {
                String date = text(firstListen, "date");
                String period = text(firstListen, "period");
                String season = text(firstListen, "season");
                StringBuilder memory = new StringBuilder();
                if (!date.isEmpty()) memory.append(date);
                if (!period.isEmpty()) {
                    if (memory.length() > 0) memory.append(" · ");
                    memory.append(period);
                }
                if (!season.isEmpty()) {
                    if (memory.length() > 0) memory.append(" · ");
                    memory.append(season);
                }
                if (memory.length() > 0) {
                    rightLayout.addView(makeSmallLabel(memory.toString()));
                }
            }
            JSONObject totalPlay = resourceExt.optJSONObject("musicTotalPlayDto");
            if (totalPlay != null) {
                long playCount = totalPlay.optLong("playCount", 0);
                String memText = text(totalPlay, "text");
                StringBuilder tp = new StringBuilder();
                if (playCount > 0) tp.append(playCount).append("次播放");
                if (!memText.isEmpty()) {
                    if (tp.length() > 0) tp.append(" · ");
                    tp.append(memText);
                }
                if (tp.length() > 0) {
                    rightLayout.addView(makeSmallLabel(tp.toString()));
                }
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
                    String sName = text(song, "name");
                    if (!sName.isEmpty()) {
                        rightLayout.addView(makeSmallLabel("• " + sName));
                    }
                }
            }
            // Playlist info
            String playlistName = text(extInfo, "name");
            if (!playlistName.isEmpty() && resInfo == null) {
                rightLayout.addView(makeSmallLabel(playlistName));
            }
            int playCount = extInfo.optInt("playCount", 0);
            if (playCount > 0) {
                rightLayout.addView(makeSmallLabel("播放: " + formatCount(playCount)));
            }
            int trackCount = extInfo.optInt("trackCount", 0);
            if (trackCount > 0) {
                rightLayout.addView(makeSmallLabel("歌曲数: " + trackCount));
            }
        }

        // Only add card if the content column has children
        if (rightLayout.getChildCount() > 0) {
            boolean isPlaylistBlock = currentBlockCode.contains("PLAYLIST");

            if (isPlaylistBlock) {

                long plId = resInfo != null ? resInfo.optLong("id", 0) : 0;
                if (plId <= 0 && extInfo != null) plId = extInfo.optLong("id", 0);
                if (plId <= 0) plId = parseResourceId(resource);
                String plName = "";
                if (resInfo != null) plName = text(resInfo, "name");
                if (plName.isEmpty() && resUiElement != null) {
                    JSONObject mt = resUiElement.optJSONObject("mainTitle");
                    if (mt != null) plName = text(mt, "title");
                }
                int plTrackCount = 0;
                if (extInfo != null) plTrackCount = extInfo.optInt("trackCount", 0);
                if (plTrackCount <= 0 && resInfo != null) plTrackCount = resInfo.optInt("trackCount", 0);
                rightLayout.addView(makeSmallLabel("点击查看歌单"));
                final long finalPlId = plId;
                final String finalPlName = plName;
                final int finalPlTrackCount = plTrackCount;
                if (finalPlId > 0) {
                    card.setClickable(true);
                    card.setOnClickListener(v -> openPlaylistDetail(finalPlId, finalPlName, finalPlTrackCount, ""));
                }
            }
            if (sheetCard) {
                rightLayout.addView(makeSmallLabel("点击下载乐谱"));
            }
            sectionAdd(card);
            if (sheetCard && !coverUrlRes.isEmpty()) {
                final String sheetUrl = coverUrlRes;
                final String sheetTitle = !sheetImgTitle.isEmpty() ? sheetImgTitle : "乐谱";
                card.setClickable(true);
                card.setOnClickListener(v -> downloadSheetImage(sheetUrl, sheetTitle));
            }
        }
    }

    /** Download the sheet music image to the configured save directory. */
    private void downloadSheetImage(final String imageUrl, final String title) {
        Toast.makeText(this, "开始下载乐谱...", Toast.LENGTH_SHORT).show();
        final String dirPath = getSharedPreferences("music163_settings", MODE_PRIVATE)
                .getString("image_save_dir",
                        new File(Environment.getExternalStorageDirectory(), "Pictures").getAbsolutePath());
        final String name = sanitizeFileName(title) + "_" + System.currentTimeMillis()
                + fileExtFromUrl(imageUrl);
        new Thread(() -> {
            try {
                byte[] data = httpGetBytes(imageUrl);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                if (bounds.outWidth <= 0) throw new IllegalStateException("not an image");
                File dir = new File(dirPath);
                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "无法创建目录", Toast.LENGTH_SHORT).show());
                    return;
                }
                File out = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(data);
                fos.close();
                MediaScannerConnection.scanFile(this,
                        new String[]{out.getAbsolutePath()}, null, null);
                final String path = out.getAbsolutePath();
                runOnUiThread(() ->
                        Toast.makeText(this, "已保存: " + path, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                MusicLog.w(TAG, "下载乐谱失败: " + imageUrl, e);
                runOnUiThread(() ->
                        Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            return bos.toByteArray();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String sanitizeFileName(String s) {
        if (s == null || s.trim().isEmpty()) return "sheet";
        String clean = s.trim().replaceAll("[\\/:*?\"<>|\\s]+", "_");
        return clean.length() > 40 ? clean.substring(0, 40) : clean;
    }

    private static String fileExtFromUrl(String url) {
        if (url == null) return ".jpg";
        String lower = url.toLowerCase(Locale.getDefault());
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".gif")) return ".gif";
        return ".jpg";
    }

    /**
     * Render a song resource card as a standard item_song list row
     * (cover + VIP badge before name + artist), matching other song lists.
     */
    private void displaySongCard(JSONObject resource, JSONObject resInfo,
                                  JSONObject resUiElement, String coverUrlRes) {
        long resId = resInfo != null ? resInfo.optLong("id", 0) : 0;
        if (resId <= 0) resId = parseResourceId(resource);
        String resName = resInfo != null ? text(resInfo, "name") : "";
        if (resName.isEmpty() && resUiElement != null) {
            JSONObject mt = resUiElement.optJSONObject("mainTitle");
            if (mt != null) resName = text(mt, "title");
        }
        String resArtist = "";
        if (resInfo != null) {
            JSONArray ars = resInfo.optJSONArray("ar");
            if (ars == null) ars = resInfo.optJSONArray("artist");
            if (ars == null) ars = resInfo.optJSONArray("artists");
            if (ars != null && ars.length() > 0) {
                JSONObject a0 = ars.optJSONObject(0);
                if (a0 != null) resArtist = text(a0, "name");
            }
            if (resArtist.isEmpty()) {
                resArtist = text(resInfo, "artistName");
            }
        }
        if (resArtist.isEmpty() && resUiElement != null) {
            JSONArray sts = resUiElement.optJSONArray("subTitles");
            if (sts != null && sts.length() > 0) {
                JSONObject st0 = sts.optJSONObject(0);
                if (st0 != null) resArtist = text(st0, "title");
            }
            if (resArtist.isEmpty()) {
                JSONObject st = resUiElement.optJSONObject("subTitle");
                if (st != null) resArtist = text(st, "title");
            }
        }

        View row = getLayoutInflater().inflate(R.layout.item_song, contentLayout, false);
        ImageView ivCover = row.findViewById(R.id.iv_cover);
        if (!coverUrlRes.isEmpty()) {
            NetworkImageLoader.load(ivCover, coverUrlRes);
        }
        TextView tvItemName = row.findViewById(R.id.tv_item_name);
        tvItemName.setText(resName);
        TextView tvItemArtist = row.findViewById(R.id.tv_item_artist);
        tvItemArtist.setText(resArtist);

        if (resId > 0 && !resName.isEmpty()) {
            Song cardSong = new Song(resId, resName, resArtist, "");
            int cardFee = resInfo != null ? resInfo.optInt("fee", -1) : -1;
            if (cardFee >= 0) cardSong.setFee(cardFee);
            currentBlockSongs.add(cardSong);
            final int songIdx = currentBlockSongs.size() - 1;
            feeBadges.put(cardSong, row.findViewById(R.id.tv_vip_badge));
            row.setOnClickListener(v -> playSimilarSongs(currentBlockSongs, songIdx));
        }
        sectionAdd(row);
        requestFeeEnrichment();
    }

    private String formatCount(int count) {
        if (count >= 100000000) {
            return String.format(Locale.getDefault(), "%.1f亿", count / 100000000.0);
        } else if (count >= 10000) {
            return String.format(Locale.getDefault(), "%.1f万", count / 10000.0);
        }
        return String.valueOf(count);
    }

    /**
     * optString that treats an explicit JSON null as empty. Android's
     * org.json optString returns the literal "null" for JSONObject.NULL
     * sentinel values — the wiki API returns many explicit nulls
     * (headline/desc/text...) which would otherwise render "null" on screen.
     */
    private static String text(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "");
    }

    private static String text(JSONArray a, int i) {
        if (a == null || a.isNull(i)) return "";
        return a.optString(i, "");
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
        displayUiElement(uiElement, null);
    }

    /**
     * @param skipTitle when non-null, a mainTitle identical to this string is
     *                  not re-rendered (block-level uiElement duplicates the
     *                  section header already added by displayBlock).
     */
    private void displayUiElement(JSONObject uiElement, String skipTitle) {
        JSONObject mainTitle = uiElement.optJSONObject("mainTitle");
        if (mainTitle != null) {
            String title = text(mainTitle, "title");
            if (!title.isEmpty() && !title.equals(skipTitle)) {
                sectionAdd(makeText(title, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
            }
        }
        JSONObject subTitle = uiElement.optJSONObject("subTitle");
        if (subTitle != null) {
            String title = text(subTitle, "title");
            if (!title.isEmpty()) {
                sectionAdd(makeText(title, COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
        }
        // textLinks carries the actual values for creatives like 语种/BPM —
        // the API puts them here, NOT in headline/desc (which are always null).
        JSONArray textLinks = uiElement.optJSONArray("textLinks");
        if (textLinks != null && textLinks.length() > 0) {
            StringBuilder values = new StringBuilder();
            for (int i = 0; i < textLinks.length(); i++) {
                JSONObject link = textLinks.optJSONObject(i);
                if (link == null) continue;
                String value = text(link, "text");
                if (value.isEmpty()) continue;
                if (values.length() > 0) values.append(" / ");
                values.append(value);
            }
            if (values.length() > 0) {
                sectionAdd(makeText(values.toString(), COLOR_TEXT_DESC, px(13), false, Gravity.START));
            }
        }
        String description = text(uiElement, "description");
        if (!description.isEmpty()) {
            sectionAdd(makeText(description, COLOR_TEXT_DESC, px(13), false, Gravity.START));
        }
    }

    // ─── Step 3: Fetch artist description ─────────────────────────────

    private void fetchArtistDesc() {
        if (artistId <= 0) {
            MusicLog.d(TAG, "无歌手ID，跳过歌手百科请求");
            fetchSongWiki();
            return;
        }

        sectionAdd(makeSpacer(px(6)));
        sectionAdd(makeSpacer(px(6)));
        addSectionHeader(R.drawable.ic_mic, "歌手百科");

        final TextView tvArtistLoading = makeText("加载歌手详情...", COLOR_TEXT_SECONDARY, px(12), false, Gravity.START);
        sectionAdd(tvArtistLoading);

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
            sectionAdd(makeText("暂无歌手百科信息", COLOR_TEXT_SECONDARY, px(12), false, Gravity.START));
            sectionAdd(makeSpacer(px(16)));
            return;
        }

        if (briefDesc != null && !briefDesc.isEmpty()) {
            sectionAdd(makeText("简介", COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
            sectionAdd(makeSpacer(px(3)));
            sectionAdd(makeText(briefDesc, COLOR_TEXT_DESC, px(13), false, Gravity.START));
        }

        if (introduction != null && introduction.length() > 0) {
            for (int i = 0; i < introduction.length(); i++) {
                JSONObject section = introduction.optJSONObject(i);
                if (section == null) continue;

                String ti = text(section, "ti");
                String txt = text(section, "txt");

                if (!ti.isEmpty()) {
                    sectionAdd(makeSpacer(px(8)));
                    sectionAdd(makeText(ti, COLOR_TEXT_PRIMARY, px(13), true, Gravity.START));
                }
                if (!txt.isEmpty()) {
                    sectionAdd(makeSpacer(px(3)));
                    sectionAdd(makeText(txt, COLOR_TEXT_DESC, px(13), false, Gravity.START));
                }
            }
        }

        sectionAdd(makeSpacer(px(16)));
    }

    // ── Playback helpers ────────────────────────────────────────────────

    private TextView createVipBadge() {
        TextView badge = new TextView(this);
        badge.setText(R.string.vip_badge);
        badge.setTextColor(getResources().getColor(R.color.colorError));
        badge.setTextSize(9);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setBackgroundResource(R.drawable.bg_vip_badge);
        badge.setPadding(px(3), px(1), px(3), px(1));
        badge.setVisibility(View.GONE);
        return badge;
    }

    private void requestFeeEnrichment() {
        refreshFeeBadges();
        List<Long> ids = new ArrayList<>();
        for (Song s : currentBlockSongs) {
            if (s.getFee() == 0 && s.getId() > 0 && feeRequested.add(s.getId())) {
                ids.add(s.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        MusicApiHelper.fetchSongsDetails(ids, cookie, new MusicApiHelper.BatchSongDetailsCallback() {
            @Override
            public void onResult(java.util.Map<Long, Song> songMap) {
                if (songMap != null) {
                    for (Song done : songMap.values()) {
                        if (done.getFee() == 0) continue;
                        for (Song s : currentBlockSongs) {
                            if (s.getId() == done.getId()) s.setFee(done.getFee());
                        }
                    }
                }
                refreshFeeBadges();
                requestFeeEnrichment();
            }

            @Override
            public void onError(String message) {
                refreshFeeBadges();
            }
        });
    }

    private void refreshFeeBadges() {
        for (java.util.Map.Entry<Song, TextView> e : feeBadges.entrySet()) {
            e.getValue().setVisibility(e.getKey().isVip() ? View.VISIBLE : View.GONE);
        }
    }

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
        // Close the previous section implicitly: start a fresh M3 card and
        // route subsequent content into it via sectionAdd().
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_rounded);
        card.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, px(6));
        contentLayout.addView(card, cardParams);
        currentSectionCard = card;

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
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(12));
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);

        sectionAdd(row);
        sectionAdd(makeSpacer(px(4)));
    }

    /** Add a view to the open section card, or the page root before any section. */
    private void sectionAdd(android.view.View v) {
        (currentSectionCard != null ? currentSectionCard : contentLayout).addView(v);
    }

    private void sectionAdd(android.view.View v, android.view.ViewGroup.LayoutParams params) {
        (currentSectionCard != null ? currentSectionCard : contentLayout).addView(v, params);
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
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(COLOR_TEXT_PRIMARY);
        tvValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(tvLabel);
        row.addView(tvValue);
        sectionAdd(row);
    }

    private TextView makeSmallLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        // Content style — same gray + size as every other body text so the
        // page has exactly two levels: white bold title / gray body.
        tv.setTextColor(COLOR_TEXT_DESC);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(13));
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
        return (int) (baseValue * getResources().getDisplayMetrics().density + 0.5f);
    }
}
