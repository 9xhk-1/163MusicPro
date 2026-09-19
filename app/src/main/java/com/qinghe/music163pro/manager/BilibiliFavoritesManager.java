package com.qinghe.music163pro.manager;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.qinghe.music163pro.model.BilibiliFavorite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class BilibiliFavoritesManager {

    private static final String TAG = "BiliFavorites";
    private static final String EXT_DIR_NAME = "163Music";
    private static final String EXT_FILE_NAME = "bilibili-favorites.json";
    private static final String IDS_FILE_NAME = "bilibili-favorite-ids.json";

    private final Context context;

    /** Lightweight ID-only record for Bilibili favorites. */
    public static class BilibiliId {
        public String bvid;
        public long cid;

        public BilibiliId(String bvid, long cid) {
            this.bvid = bvid;
            this.cid = cid;
        }
    }

    public BilibiliFavoritesManager(Context context) {
        this.context = context.getApplicationContext();
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
                // Migrate from bilibili-favorites.json
                File oldFile = getFavoritesFile();
                if (oldFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(oldFile);
                         InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[1024];
                        int len;
                        while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
                        JSONArray oldArr = new JSONArray(sb.toString());
                        for (int i = 0; i < oldArr.length(); i++) {
                            JSONObject obj = oldArr.getJSONObject(i);
                            String bvid = obj.optString("bvid", "");
                            if (!bvid.isEmpty()) {
                                JSONObject idEntry = new JSONObject();
                                idEntry.put("bvid", bvid);
                                idEntry.put("cid", obj.optLong("cid", 0));
                                idsArr.put(idEntry);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error migrating bilibili IDs", e);
                    }
                }
                saveIdsArray(idsArr);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error initializing bilibili-favorite-ids.json", e);
        }
    }

    private JSONArray loadIdsArray() {
        try {
            File idsFile = getIdsFile();
            if (!idsFile.exists()) return new JSONArray();
            try (FileInputStream fis = new FileInputStream(idsFile);
                 InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int len;
                while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
                return new JSONArray(sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading bilibili-favorite-ids.json", e);
            return new JSONArray();
        }
    }

    private void saveIdsArray(JSONArray arr) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME);
            if (!dir.exists()) dir.mkdirs();
            File idsFile = getIdsFile();
            try (FileOutputStream fos = new FileOutputStream(idsFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                writer.write(arr.toString());
                writer.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error saving bilibili-favorite-ids.json", e);
        }
    }

    public List<BilibiliId> getFavoriteIds() {
        List<BilibiliId> result = new ArrayList<>();
        try {
            JSONArray arr = loadIdsArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    result.add(new BilibiliId(obj.optString("bvid", ""), obj.optLong("cid", 0)));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading bilibili favorite IDs", e);
        }
        return result;
    }

    public void addFavoriteById(String bvid, long cid) {
        if (bvid == null || bvid.trim().isEmpty()) return;
        try {
            JSONArray arr = loadIdsArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && bvid.equalsIgnoreCase(obj.optString("bvid", ""))) return;
            }
            JSONArray newArr = new JSONArray();
            JSONObject idEntry = new JSONObject();
            idEntry.put("bvid", bvid);
            idEntry.put("cid", cid);
            newArr.put(idEntry);
            for (int i = 0; i < arr.length(); i++) newArr.put(arr.get(i));
            saveIdsArray(newArr);
        } catch (Exception e) {
            Log.w(TAG, "Error adding bilibili favorite ID", e);
        }
    }

    public void removeFavoriteById(String bvid) {
        if (bvid == null) return;
        try {
            JSONArray arr = loadIdsArray();
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && !bvid.equalsIgnoreCase(obj.optString("bvid", ""))) {
                    newArr.put(obj);
                }
            }
            saveIdsArray(newArr);
        } catch (Exception e) {
            Log.w(TAG, "Error removing bilibili favorite ID", e);
        }
    }

    public List<BilibiliFavorite> getFavorites() {
        List<BilibiliFavorite> list = new ArrayList<>();
        File file = getFavoritesFile();
        if (!file.exists()) {
            return list;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new BilibiliFavorite(
                        obj.optString("bvid", ""),
                        obj.optString("title", ""),
                        obj.optString("owner", "")
                ));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading bilibili favorites", e);
        }
        return list;
    }

    public boolean isFavorite(String bvid) {
        for (BilibiliFavorite favorite : getFavorites()) {
            if (favorite.getBvid().equalsIgnoreCase(bvid)) {
                return true;
            }
        }
        return false;
    }

    public void addFavorite(BilibiliFavorite favorite) {
        if (favorite == null || favorite.getBvid() == null || favorite.getBvid().trim().isEmpty()) {
            return;
        }
        List<BilibiliFavorite> list = getFavorites();
        for (BilibiliFavorite item : list) {
            if (item.getBvid().equalsIgnoreCase(favorite.getBvid())) {
                item.setTitle(favorite.getTitle());
                item.setOwner(favorite.getOwner());
                saveFavorites(list);
                return;
            }
        }
        list.add(0, favorite);
        saveFavorites(list);
        addFavoriteById(favorite.getBvid(), 0);
    }

    public void removeFavorite(String bvid) {
        List<BilibiliFavorite> list = getFavorites();
        List<BilibiliFavorite> updated = new ArrayList<>();
        for (BilibiliFavorite item : list) {
            if (!item.getBvid().equalsIgnoreCase(bvid)) {
                updated.add(item);
            }
        }
        saveFavorites(updated);
        removeFavoriteById(bvid);
    }

    private void saveFavorites(List<BilibiliFavorite> list) {
        try {
            File file = getFavoritesFile();
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create favorites dir: " + dir.getAbsolutePath());
                return;
            }
            JSONArray arr = new JSONArray();
            for (BilibiliFavorite favorite : list) {
                JSONObject obj = new JSONObject();
                obj.put("bvid", favorite.getBvid());
                obj.put("title", favorite.getTitle());
                obj.put("owner", favorite.getOwner());
                arr.put(obj);
            }
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                writer.write(arr.toString(2));
                writer.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error saving bilibili favorites", e);
        }
    }

    public File getFavoritesFile() {
        return new File(new File(Environment.getExternalStorageDirectory(), EXT_DIR_NAME), EXT_FILE_NAME);
    }
}
