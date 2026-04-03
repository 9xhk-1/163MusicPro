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

    public void addPlaylist(PlaylistInfo playlist) {
        List<PlaylistInfo> list = getPlaylists();
        for (PlaylistInfo p : list) {
            if (p.getId() == playlist.getId()) return; // already exists
        }
        list.add(0, playlist);
        savePlaylists(list);
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
    }

    public boolean isPlaylistSaved(long playlistId) {
        List<PlaylistInfo> list = getPlaylists();
        for (PlaylistInfo p : list) {
            if (p.getId() == playlistId) return true;
        }
        return false;
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
