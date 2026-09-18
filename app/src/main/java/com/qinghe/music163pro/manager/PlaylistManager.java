package com.qinghe.music163pro.manager;

import android.os.Environment;
import android.util.Log;

import com.qinghe.music163pro.model.PlaylistInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages locally saved playlists.
 * Stores playlist info in /sdcard/163Music/playlists.json.
 */
public class PlaylistManager {

    private static final String TAG = "PlaylistManager";
    private static final String EXT_DIR_NAME = "163Music";
    private static final String EXT_FILE_NAME = "playlists.json";
    private static final String IDS_FILE_NAME = "playlist-ids.json";

    public PlaylistManager() {
        initIdsFile();
    }

    private File getIdsFile() {
        return new File(new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME), IDS_FILE_NAME);
    }

    private void initIdsFile() {
        try {
            File idsFile = getIdsFile();
            if (!idsFile.exists()) {
                JSONArray idsArr = new JSONArray();
                // Migrate from playlists.json
                List<PlaylistInfo> existing = getPlaylists();
                for (PlaylistInfo p : existing) {
                    idsArr.put(p.getId());
                }
                saveIdsArray(idsArr);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error initializing playlist-ids.json", e);
        }
    }

    private JSONArray loadIdsArray() {
        try {
            File idsFile = getIdsFile();
            if (!idsFile.exists()) return new JSONArray();
            FileInputStream fis = new FileInputStream(idsFile);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            reader.close();
            fis.close();
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "Error loading playlist-ids.json", e);
            return new JSONArray();
        }
    }

    private void saveIdsArray(JSONArray arr) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME);
            if (!dir.exists()) dir.mkdirs();
            File idsFile = getIdsFile();
            FileOutputStream fos = new FileOutputStream(idsFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(arr.toString());
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            Log.w(TAG, "Error saving playlist-ids.json", e);
        }
    }

    public List<Long> getPlaylistIds() {
        List<Long> ids = new ArrayList<>();
        try {
            JSONArray arr = loadIdsArray();
            for (int i = 0; i < arr.length(); i++) {
                ids.add(arr.getLong(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading playlist IDs", e);
        }
        return ids;
    }

    public void addPlaylist(PlaylistInfo playlist) {
        List<PlaylistInfo> list = getPlaylists();
        for (PlaylistInfo p : list) {
            if (p.getId() == playlist.getId()) return; // already exists
        }
        list.add(0, playlist);
        savePlaylists(list);
        // Also add to ids file
        try {
            JSONArray arr = loadIdsArray();
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getLong(i) == playlist.getId()) return;
            }
            JSONArray newArr = new JSONArray();
            newArr.put(playlist.getId());
            for (int i = 0; i < arr.length(); i++) newArr.put(arr.getLong(i));
            saveIdsArray(newArr);
        } catch (Exception e) {
            Log.w(TAG, "Error adding playlist ID", e);
        }
    }

    public void removePlaylist(long playlistId) {
        List<PlaylistInfo> list = getPlaylists();
        List<PlaylistInfo> updated = new ArrayList<>();
        for (PlaylistInfo p : list) {
            if (p.getId() != playlistId) {
                updated.add(p);
            }
        }
        savePlaylists(updated);
        // Also remove from ids file
        try {
            JSONArray arr = loadIdsArray();
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getLong(i) != playlistId) newArr.put(arr.getLong(i));
            }
            saveIdsArray(newArr);
        } catch (Exception e) {
            Log.w(TAG, "Error removing playlist ID", e);
        }
    }

    public boolean isPlaylistSaved(long playlistId) {
        List<PlaylistInfo> list = getPlaylists();
        for (PlaylistInfo p : list) {
            if (p.getId() == playlistId) return true;
        }
        return false;
    }

    /**
     * Update a locally saved playlist's metadata (track count, creator).
     * Does nothing if the playlist is not in local storage.
     */
    public void updatePlaylistMeta(long playlistId, int trackCount, String creator) {
        List<PlaylistInfo> list = getPlaylists();
        boolean changed = false;
        for (PlaylistInfo p : list) {
            if (p.getId() == playlistId) {
                p.setTrackCount(trackCount);
                if (creator != null && !creator.isEmpty()) {
                    p.setCreator(creator);
                }
                changed = true;
                break;
            }
        }
        if (changed) {
            savePlaylists(list);
        }
    }

    public List<PlaylistInfo> getPlaylists() {
        List<PlaylistInfo> list = new ArrayList<>();
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
                PlaylistInfo p = new PlaylistInfo(
                        obj.getLong("id"),
                        obj.optString("name", ""),
                        obj.optInt("trackCount", 0),
                        obj.optString("creator", ""),
                        obj.optLong("userId", 0),
                        obj.optBoolean("subscribed", false),
                        obj.optString("specialType", "0")
                );
                list.add(p);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading playlists", e);
        }
        return list;
    }

    private void savePlaylists(List<PlaylistInfo> list) {
        try {
            JSONArray arr = new JSONArray();
            for (PlaylistInfo p : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.getId());
                obj.put("name", p.getName());
                obj.put("trackCount", p.getTrackCount());
                obj.put("creator", p.getCreator());
                obj.put("userId", p.getUserId());
                obj.put("subscribed", p.isSubscribed());
                obj.put("specialType", p.getSpecialType());
                arr.put(obj);
            }

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
            writer.write(arr.toString(2));
            writer.flush();
            writer.close();
            fos.close();
            Log.d(TAG, "Playlists saved to " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Error saving playlists", e);
        }
    }
}
