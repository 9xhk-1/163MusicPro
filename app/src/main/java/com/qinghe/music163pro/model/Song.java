package com.qinghe.music163pro.model;

import java.io.Serializable;

public class Song implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private String artist;
    private String album;
    private String url;
    /** Source type: null or "netease" for NetEase, "bilibili" for Bilibili */
    private String source;
    /** For Bilibili: bvid of the video */
    private String bvid;
    /** For Bilibili: cid of the video page */
    private long cid;
    /** Local downloaded quality when playing from the structured songs/<quality>/ folder. */
    private String localQuality;
    /** True when the song was opened from the download list and must stay on local audio only. */
    private boolean forceLocalPlayback;
    /** Album cover image URL (al.picUrl from NetEase API). May be null or empty. */
    private String coverUrl;
    /** Album ID (al.id from NetEase API). 0 if unknown. */
    private long albumId;
    /** NetEase fee flag: 1 = VIP song, 0 = free, etc. */
    private int fee;
    /** NetEase song alias (alia[0]); null/empty when the song has none. */
    private String alias;

    /** Global switch: show alias after the name in UI (default on). */
    private static volatile boolean aliasDisplayEnabled = true;

    public Song() {}

    public Song(long id, String name, String artist, String album) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.album = album;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public static void setAliasDisplayEnabled(boolean enabled) {
        aliasDisplayEnabled = enabled;
    }

    public static boolean isAliasDisplayEnabled() {
        return aliasDisplayEnabled;
    }

    /**
     * UI text for the song name. When the "show alias" switch is on and the
     * song has a NetEase alias (alia), it renders as 名称（别名）.
     * File paths / persistence must keep using {@link #getName()}.
     */
    public String getDisplayName() {
        if (aliasDisplayEnabled && alias != null && !alias.isEmpty()
                && name != null && !name.isEmpty()) {
            return name + "（" + alias + "）";
        }
        return name;
    }

    @Override
    public String toString() { return name != null ? name : ""; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getBvid() { return bvid; }
    public void setBvid(String bvid) { this.bvid = bvid; }

    public long getCid() { return cid; }
    public void setCid(long cid) { this.cid = cid; }

    public String getLocalQuality() { return localQuality; }
    public void setLocalQuality(String localQuality) { this.localQuality = localQuality; }

    public boolean isForceLocalPlayback() { return forceLocalPlayback; }
    public void setForceLocalPlayback(boolean forceLocalPlayback) {
        this.forceLocalPlayback = forceLocalPlayback;
    }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public long getAlbumId() { return albumId; }
    public void setAlbumId(long albumId) { this.albumId = albumId; }

    public int getFee() { return fee; }
    public void setFee(int fee) { this.fee = fee; }

    /** Whether this is a VIP song (NetEase fee == 1). */
    public boolean isVip() { return fee == 1; }

    /** Check if this song is from Bilibili */
    public boolean isBilibili() {
        return "bilibili".equals(source);
    }
}
