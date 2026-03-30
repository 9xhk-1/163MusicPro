package com.qinghe.music163pro.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.qinghe.music163pro.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class FavoritesManager {

    private static final String TAG = "FavoritesManager";
    private static final String PREFS_NAME = "music163_favorites";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_CLOUD_SYNC = "cloud_sync";

    private static final String EXT_DIR_NAME = "163Music";
    private static final String EXT_FILE_NAME = "favorites.json";

    private final SharedPreferences prefs;
    private final Context context;

    public FavoritesManager(Context context) {
        this.context = context;
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
            Log.w(TAG, "Error loading favorites", e);
        }

        // If SharedPreferences is empty, try to restore from external file backup
        if (list.isEmpty()) {
            List<Song> external = loadFromExternalFile();
            if (!external.isEmpty()) {
                Log.d(TAG, "Restoring " + external.size() + " favorites from external file");
                saveFavorites(external);
                return external;
            }
        }

        return list;
    }

    private void saveFavorites(List<Song> list) {
        try {
            JSONArray arr = buildJsonArray(list);
            // Save to SharedPreferences (primary)
            prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
            // Also save to external file
            saveToExternalFile(arr);
        } catch (Exception e) {
            Log.w(TAG, "Error saving favorites", e);
        }
    }

    private JSONArray buildJsonArray(List<Song> list) throws Exception {
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
        return arr;
    }

    /**
     * Save favorites JSON to /sdcard/163Music/favorites.json
     */
    private void saveToExternalFile(JSONArray arr) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.w(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                    return;
                }
            }
            File file = new File(dir, EXT_FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(arr.toString(2)); // Pretty-printed JSON
            writer.flush();
            writer.close();
            fos.close();
            Log.d(TAG, "Favorites saved to " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Error saving favorites to external file", e);
        }
    }

    /**
     * Load favorites from /sdcard/163Music/favorites.json
     * Can be used to restore from file backup.
     */
    public List<Song> loadFromExternalFile() {
        List<Song> list = new ArrayList<>();
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME);
            File file = new File(dir, EXT_FILE_NAME);
            if (!file.exists()) return list;

            FileInputStream fis = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();
            fis.close();

            JSONArray arr = new JSONArray(sb.toString());
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
            Log.w(TAG, "Error loading favorites from external file", e);
        }
        return list;
    }

    public void setCloudSync(boolean enabled) {
        prefs.edit().putBoolean(KEY_CLOUD_SYNC, enabled).apply();
    }

    public boolean isCloudSync() {
        return prefs.getBoolean(KEY_CLOUD_SYNC, false);
    }
}
