package com.qinghe.music163pro.model;

import java.io.Serializable;

/**
 * Model for playlist info (used in search results, favorites, etc.)
 */
public class PlaylistInfo implements Serializable {
    private static final long serialVersionUID = 2L;

    private long id;
    private String name;
    private int trackCount;
    private String creator;
    private long userId;        // creator's user ID
    private boolean subscribed; // whether current user subscribed to this playlist
    private String specialType; // "5" = "我喜欢的音乐"
    private String coverUrl;    // playlist cover image URL

    public PlaylistInfo() {}

    public PlaylistInfo(long id, String name, int trackCount, String creator) {
        this.id = id;
        this.name = name;
        this.trackCount = trackCount;
        this.creator = creator;
    }

    public PlaylistInfo(long id, String name, int trackCount, String creator,
                        long userId, boolean subscribed, String specialType) {
        this.id = id;
        this.name = name;
        this.trackCount = trackCount;
        this.creator = creator;
        this.userId = userId;
        this.subscribed = subscribed;
        this.specialType = specialType;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTrackCount() { return trackCount; }
    public void setTrackCount(int trackCount) { this.trackCount = trackCount; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public boolean isSubscribed() { return subscribed; }
    public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }

    public String getSpecialType() { return specialType; }
    public void setSpecialType(String specialType) { this.specialType = specialType; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    /** Check if this is the default "我喜欢的音乐" playlist */
    public boolean isLikedPlaylist() { return "5".equals(specialType); }
}
