package com.qinghe.music163pro.manager;

import android.os.Environment;
import android.util.Log;

import com.qinghe.music163pro.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages play history saved to /sdcard/163Music/history.json.
 * Each entry contains song info and a timestamp, sorted newest first.
 */
public class HistoryManager {

    private static final String TAG = "HistoryManager";
    private static final String DIR_NAME = "163Music";
    private static final String FILE_NAME = "history.json";
    private static final int MAX_HISTORY = 200;

    private static HistoryManager instance;

    private HistoryManager() {}

    public static synchronized HistoryManager getInstance() {
        if (instance == null) {
            instance = new HistoryManager();
        }
        return instance;
    }

    private File getHistoryFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    /**
     * Add a song to play history with current timestamp.
     * Removes duplicate (same song ID) if exists.
     * New entries are added at position 0 (top).
     */
    public void addToHistory(Song song) {
        if (song == null || song.getId() <= 0) return;
        try {
            List<JSONObject> entries = loadEntries();

            // Remove existing entry with same song ID
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).optLong("id", -1) == song.getId()) {
                    entries.remove(i);
                }
            }

            // Create new entry
            JSONObject entry = new JSONObject();
            entry.put("id", song.getId());
            entry.put("name", song.getName());
            entry.put("artist", song.getArtist());
            entry.put("album", song.getAlbum());
            entry.put("timestamp", System.currentTimeMillis());

            // Add at position 0 (newest first)
            entries.add(0, entry);

            // Trim to max size
            while (entries.size() > MAX_HISTORY) {
                entries.remove(entries.size() - 1);
            }

            // Save
            saveEntries(entries);
        } catch (Exception e) {
            Log.w(TAG, "Error adding to history", e);
        }
    }

    /**
     * Get all history entries as Song objects, sorted newest first.
     */
    public List<Song> getHistory() {
        List<Song> songs = new ArrayList<>();
        try {
            List<JSONObject> entries = loadEntries();
            for (JSONObject entry : entries) {
                Song song = new Song(
                        entry.optLong("id", 0),
                        entry.optString("name", ""),
                        entry.optString("artist", ""),
                        entry.optString("album", "")
                );
                songs.add(song);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading history", e);
        }
        return songs;
    }

    /**
     * Clear all history.
     */
    public void clearHistory() {
        try {
            saveEntries(new ArrayList<>());
        } catch (Exception e) {
            Log.w(TAG, "Error clearing history", e);
        }
    }

    /**
     * Remove a single song from history by its ID.
     */
    public void removeFromHistory(long songId) {
        try {
            List<JSONObject> entries = loadEntries();
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).optLong("id", -1) == songId) {
                    entries.remove(i);
                }
            }
            saveEntries(entries);
        } catch (Exception e) {
            Log.w(TAG, "Error removing from history", e);
        }
    }

    private List<JSONObject> loadEntries() {
        List<JSONObject> entries = new ArrayList<>();
        File file = getHistoryFile();
        if (!file.exists()) return entries;
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                entries.add(arr.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading history file", e);
        }
        return entries;
    }

    private void saveEntries(List<JSONObject> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject entry : entries) {
                arr.put(entry);
            }
            File file = getHistoryFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(arr.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            Log.w(TAG, "Error saving history file", e);
        }
    }
}
