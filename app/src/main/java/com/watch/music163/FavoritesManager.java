package com.watch.music163;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FavoritesManager {

    private static final String PREFS_NAME = "music163_favorites";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_CLOUD_SYNC = "cloud_sync";

    private final SharedPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addFavorite(Song song) {
        List<Song> list = getFavorites();
        for (Song s : list) {
            if (s.getId() == song.getId()) return;
        }
        list.add(song);
        saveFavorites(list);
    }

    public void removeFavorite(Song song) {
        List<Song> list = getFavorites();
        List<Song> updated = new ArrayList<>();
        for (Song s : list) {
            if (s.getId() != song.getId()) {
                updated.add(s);
            }
        }
        saveFavorites(updated);
    }

    public boolean isFavorite(long songId) {
        List<Song> list = getFavorites();
        for (Song s : list) {
            if (s.getId() == songId) return true;
        }
        return false;
    }

    public List<Song> getFavorites() {
        List<Song> list = new ArrayList<>();
        String json = prefs.getString(KEY_FAVORITES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Song song = new Song(
                        obj.getLong("id"),
                        obj.optString("name", ""),
                        obj.optString("artist", ""),
                        obj.optString("album", "")
                );
                String url = obj.has("url") && !obj.isNull("url") ? obj.getString("url") : null;
                song.setUrl(url);
                list.add(song);
            }
        } catch (Exception e) {
            android.util.Log.w("Favorites", "Error loading favorites", e);
        }
        return list;
    }

    private void saveFavorites(List<Song> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Song s : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("name", s.getName());
                obj.put("artist", s.getArtist());
                obj.put("album", s.getAlbum());
                if (s.getUrl() != null) {
                    obj.put("url", s.getUrl());
                }
                arr.put(obj);
            }
            prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("Favorites", "Error saving favorites", e);
        }
    }

    public void setCloudSync(boolean enabled) {
        prefs.edit().putBoolean(KEY_CLOUD_SYNC, enabled).apply();
    }

    public boolean isCloudSync() {
        return prefs.getBoolean(KEY_CLOUD_SYNC, false);
    }
}
