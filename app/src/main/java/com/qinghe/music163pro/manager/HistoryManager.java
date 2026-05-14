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
    private static final String IDS_FILE_NAME = "history-ids.json";
    private static final int MAX_HISTORY = 200;

    private static HistoryManager instance;
    private boolean idsFileInitialized = false;

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

    private File getHistoryIdsFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, IDS_FILE_NAME);
    }

    private synchronized void ensureIdsFileInitialized() {
        if (idsFileInitialized) return;
        idsFileInitialized = true;
        initIdsFile();
    }

    private void initIdsFile() {
        try {
            File idsFile = getHistoryIdsFile();
            if (!idsFile.exists()) {
                // Migrate from history.json
                JSONArray idsArr = new JSONArray();
                List<JSONObject> entries = loadEntries();
                for (JSONObject entry : entries) {
                    JSONObject idEntry = new JSONObject();
                    idEntry.put("id", entry.optLong("id", 0));
                    idEntry.put("ts", entry.optLong("timestamp", System.currentTimeMillis()));
                    idsArr.put(idEntry);
                }
                saveIdsArray(idsArr);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error initializing history-ids.json", e);
        }
    }

    private JSONArray loadIdsArray() {
        ensureIdsFileInitialized();
        try {
            File idsFile = getHistoryIdsFile();
            if (!idsFile.exists()) return new JSONArray();
            FileInputStream fis = new FileInputStream(idsFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "Error loading history-ids.json", e);
            return new JSONArray();
        }
    }

    private void saveIdsArray(JSONArray arr) {
        try {
            File idsFile = getHistoryIdsFile();
            FileOutputStream fos = new FileOutputStream(idsFile);
            fos.write(arr.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            Log.w(TAG, "Error saving history-ids.json", e);
        }
    }

    /** Returns history as List of {id, ts} JSONObjects, newest first. */
    public List<JSONObject> getHistoryIds() {
        ensureIdsFileInitialized();
        List<JSONObject> result = new ArrayList<>();
        try {
            JSONArray arr = loadIdsArray();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading history IDs", e);
        }
        return result;
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

            long timestamp = System.currentTimeMillis();

            // Create new entry
            JSONObject entry = new JSONObject();
            entry.put("id", song.getId());
            entry.put("name", song.getName());
            entry.put("artist", song.getArtist());
            entry.put("album", song.getAlbum());
            entry.put("timestamp", timestamp);

            // Add at position 0 (newest first)
            entries.add(0, entry);

            // Trim to max size
            while (entries.size() > MAX_HISTORY) {
                entries.remove(entries.size() - 1);
            }

            // Save to history.json
            saveEntries(entries);

            // Also update history-ids.json
            ensureIdsFileInitialized();
            JSONArray idsArr = loadIdsArray();
            JSONArray newIdsArr = new JSONArray();
            JSONObject idEntry = new JSONObject();
            idEntry.put("id", song.getId());
            idEntry.put("ts", timestamp);
            newIdsArr.put(idEntry);
            for (int i = 0; i < idsArr.length(); i++) {
                JSONObject existing = idsArr.optJSONObject(i);
                if (existing != null && existing.optLong("id", -1) != song.getId()) {
                    newIdsArr.put(existing);
                }
            }
            // Trim ids array
            JSONArray trimmedArr = new JSONArray();
            for (int i = 0; i < Math.min(newIdsArr.length(), MAX_HISTORY); i++) {
                trimmedArr.put(newIdsArr.get(i));
            }
            saveIdsArray(trimmedArr);
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
            saveIdsArray(new JSONArray());
        } catch (Exception e) {
            Log.w(TAG, "Error clearing history", e);
        }
    }

    public void removeFromHistory(long songId) {
        try {
            List<JSONObject> entries = loadEntries();
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).optLong("id", -1) == songId) {
                    entries.remove(i);
                }
            }
            saveEntries(entries);
            // Also remove from ids file
            ensureIdsFileInitialized();
            JSONArray arr = loadIdsArray();
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && obj.optLong("id", -1) != songId) {
                    newArr.put(obj);
                }
            }
            saveIdsArray(newArr);
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
