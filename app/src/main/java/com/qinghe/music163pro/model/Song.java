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

    /** Check if this song is from Bilibili */
    public boolean isBilibili() {
        return "bilibili".equals(source);
    }
}
