package com.qinghe.music163pro.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Visibility preferences for entries in the more screen.
 */
public final class MoreMenuPreferences {

    public static final String PREFS_NAME = "music163_settings";

    public static final String KEY_FAVORITES = "more_show_favorites";
    public static final String KEY_MY_PLAYLISTS = "more_show_my_playlists";
    public static final String KEY_DAILY_RECOMMEND = "more_show_daily_recommend";
    public static final String KEY_RADAR_PLAYLIST = "more_show_radar_playlist";
    public static final String KEY_MUSIC_CLOUD = "more_show_music_cloud";
    public static final String KEY_SEARCH = "more_show_search";
    public static final String KEY_SONG_RECOGNITION = "more_show_song_recognition";
    public static final String KEY_DOWNLOADS = "more_show_downloads";
    public static final String KEY_RINGTONES = "more_show_ringtones";
    public static final String KEY_TOPLIST = "more_show_toplist";
    public static final String KEY_HISTORY = "more_show_history";
    public static final String KEY_PROFILE = "more_show_profile";
    public static final String KEY_PERSONAL_FM = "more_show_personal_fm";
    public static final String KEY_LOGIN = "more_show_login";

    private static final List<String> ALL_KEYS = Collections.unmodifiableList(Arrays.asList(
            KEY_FAVORITES,
            KEY_MY_PLAYLISTS,
            KEY_DAILY_RECOMMEND,
            KEY_RADAR_PLAYLIST,
            KEY_MUSIC_CLOUD,
            KEY_SEARCH,
            KEY_SONG_RECOGNITION,
            KEY_DOWNLOADS,
            KEY_RINGTONES,
            KEY_TOPLIST,
            KEY_HISTORY,
            KEY_PROFILE,
            KEY_PERSONAL_FM,
            KEY_LOGIN
    ));

    private MoreMenuPreferences() {
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(SharedPreferences prefs, String key) {
        return prefs == null || prefs.getBoolean(key, true);
    }

    public static void setEnabled(SharedPreferences prefs, String key, boolean enabled) {
        if (prefs == null) {
            return;
        }
        prefs.edit().putBoolean(key, enabled).apply();
    }

    public static List<String> allKeys() {
        return ALL_KEYS;
    }
}
