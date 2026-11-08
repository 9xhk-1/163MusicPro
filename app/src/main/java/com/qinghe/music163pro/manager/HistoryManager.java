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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages play history saved to /sdcard/163Music/history.json.
 * Each entry contains song info and a timestamp, sorted newest first.
 *
 * v54 性能改造：
 * 旧实现每次 onSongChanged() 都在主线程「读 history.json → 读 history-ids.json
 * → 写 history.json → 写 history-ids.json」，连点切歌时磁盘 IO 直接把 UI 卡住。
 * 现在所有读写都收敛到单线程后台执行器，内存里保存整份快照：
 *   - 读（getHistory / getHistoryIds）直接取 volatile 快照，零 IO；
 *   - 写（addToHistory / clear / remove）排进同一个队列，只做一次读盘 + 一次写盘；
 *   - 切歌只做一次极轻的入队，不再触碰主线程文件 IO。
 * 磁盘写入改成 tmp + rename 原子替换，避免读线程读到半截 JSON。
 */
public class HistoryManager {

    private static final String TAG = "HistoryManager";
    private static final String DIR_NAME = "163Music";
    private static final String FILE_NAME = "history.json";
    private static final String IDS_FILE_NAME = "history-ids.json";
    private static final int MAX_HISTORY = 200;

    private static HistoryManager instance;

    /** 所有磁盘读写与快照修改都排在这一个线程里，天然保证顺序。 */
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** 快照整体替换后发布，读方只拿引用，不需要加锁。null = 冷启动尚未预热。 */
    private volatile List<JSONObject> memEntries;
    private volatile JSONArray memIds;
    /** clear / remove 会让在此之前排队的 add 作废，避免清空后又把歌加回来。 */
    private final AtomicLong epoch = new AtomicLong(0);

    private HistoryManager() {}

    public static synchronized HistoryManager getInstance() {
        if (instance == null) {
            instance = new HistoryManager();
        }
        return instance;
    }

    /** Application.onCreate 里调一次，后台预读历史，打开历史页时零 IO。 */
    public void warmUp() {
        io.execute(this::ensureLoadedOnIo);
    }

    private File getHistoryFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    private File getHistoryIdsFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, IDS_FILE_NAME);
    }

    // ── 冷启动加载（只在 IO 线程执行） ──────────────────────────────────────

    private void ensureLoadedOnIo() {
        if (memEntries != null && memIds != null) return;
        List<JSONObject> entries = readEntriesFromDisk();
        File idsFile = getHistoryIdsFile();
        if (!idsFile.exists()) {
            // 老版本只有 history.json：迁移出 id/ts 索引
            JSONArray migrated = new JSONArray();
            for (JSONObject entry : entries) {
                JSONObject idEntry = new JSONObject();
                try {
                    idEntry.put("id", entry.optLong("id", 0));
                    idEntry.put("ts", entry.optLong("timestamp", System.currentTimeMillis()));
                    migrated.put(idEntry);
                } catch (Exception ignored) {}
            }
            writeIdsToDisk(migrated);
        }
        memEntries = new ArrayList<>(entries);
        memIds = readIdsFromDisk();
    }

    // ── 对外 API ────────────────────────────────────────────────────────────

    /** Returns history as List of {id, ts} JSONObjects, newest first. */
    public List<JSONObject> getHistoryIds() {
        JSONArray snapshot = memIds;
        if (snapshot == null) {
            // 冷启动兜底：只在还没预热时读一次，之后全走内存
            snapshot = readIdsFromDisk();
        }
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < snapshot.length(); i++) {
            JSONObject obj = snapshot.optJSONObject(i);
            if (obj != null) result.add(obj);
        }
        return result;
    }

    /**
     * Add a song to play history with current timestamp.
     * Removes duplicate (same song ID) if exists.
     * New entries are added at position 0 (top).
     *
     * 主线程只拷贝几个字符串字段，剩下的读盘 / 去重 / 裁剪 / 双文件写入全在后台。
     */
    public void addToHistory(Song song) {
        if (song == null || song.getId() <= 0) return;
        final long id = song.getId();
        final String name = song.getName();
        final String artist = song.getArtist();
        final String album = song.getAlbum();
        final int fee = song.getFee();
        final String alias = song.getAlias();
        final long timestamp = System.currentTimeMillis();
        final long gen = epoch.get();
        io.execute(() -> {
            if (epoch.get() != gen) return;  // 已被 clear/remove 作废
            try {
                applyAdd(gen, id, name, artist, album, fee, alias, timestamp);
            } catch (Exception e) {
                Log.w(TAG, "Error adding to history", e);
            }
        });
    }

    private void applyAdd(long gen, long id, String name, String artist, String album,
                          int fee, String alias, long timestamp) throws Exception {
        ensureLoadedOnIo();

        List<JSONObject> next = new ArrayList<>(memEntries);
        for (int i = next.size() - 1; i >= 0; i--) {
            if (next.get(i).optLong("id", -1) == id) {
                next.remove(i);
            }
        }
        JSONObject entry = new JSONObject();
        entry.put("id", id);
        entry.put("name", name);
        entry.put("artist", artist);
        entry.put("album", album);
        if (fee != 0) entry.put("fee", fee);
        if (alias != null && !alias.isEmpty()) entry.put("alias", alias);
        entry.put("timestamp", timestamp);
        next.add(0, entry);
        while (next.size() > MAX_HISTORY) {
            next.remove(next.size() - 1);
        }

        JSONArray nextIds = new JSONArray();
        JSONObject idEntry = new JSONObject();
        idEntry.put("id", id);
        idEntry.put("ts", timestamp);
        nextIds.put(idEntry);
        JSONArray oldIds = memIds;
        for (int i = 0; i < oldIds.length(); i++) {
            JSONObject existing = oldIds.optJSONObject(i);
            if (existing != null && existing.optLong("id", -1) != id) {
                nextIds.put(existing);
            }
        }
        JSONArray trimmed = new JSONArray();
        for (int i = 0; i < Math.min(nextIds.length(), MAX_HISTORY); i++) {
            trimmed.put(nextIds.get(i));
        }

        // 发布前再确认一次代次：clear/remove 可能刚好在本次读盘期间发生
        if (epoch.get() != gen) return;
        // 先发布内存快照，UI 立刻能看到最新记录，写盘稍后完成也不影响显示
        memEntries = next;
        memIds = trimmed;
        writeEntriesToDisk(next);
        writeIdsToDisk(trimmed);
    }

    /** Get all history entries as Song objects, sorted newest first. */
    public List<Song> getHistory() {
        List<JSONObject> snapshot = memEntries;
        if (snapshot == null) {
            snapshot = readEntriesFromDisk();
        } else {
            snapshot = new ArrayList<>(snapshot);
        }
        List<Song> songs = new ArrayList<>(snapshot.size());
        for (JSONObject entry : snapshot) {
            Song song = new Song(
                    entry.optLong("id", 0),
                    entry.optString("name", ""),
                    entry.optString("artist", ""),
                    entry.optString("album", "")
            );
            song.setFee(entry.optInt("fee", 0));
            song.setAlias(entry.optString("alias", ""));
            songs.add(song);
        }
        return songs;
    }

    /** Clear all history. */
    public void clearHistory() {
        long gen = epoch.incrementAndGet();
        List<JSONObject> empty = new ArrayList<>();
        JSONArray emptyIds = new JSONArray();
        memEntries = empty;
        memIds = emptyIds;
        io.execute(() -> {
            if (epoch.get() != gen) return;
            writeEntriesToDisk(empty);
            writeIdsToDisk(emptyIds);
        });
    }

    public void removeFromHistory(long songId) {
        long gen = epoch.incrementAndGet();
        io.execute(() -> {
            if (epoch.get() != gen) return;
            try {
                ensureLoadedOnIo();
                List<JSONObject> next = new ArrayList<>(memEntries);
                for (int i = next.size() - 1; i >= 0; i--) {
                    if (next.get(i).optLong("id", -1) == songId) {
                        next.remove(i);
                    }
                }
                JSONArray nextIds = new JSONArray();
                JSONArray oldIds = memIds;
                for (int i = 0; i < oldIds.length(); i++) {
                    JSONObject obj = oldIds.optJSONObject(i);
                    if (obj != null && obj.optLong("id", -1) != songId) {
                        nextIds.put(obj);
                    }
                }
                memEntries = next;
                memIds = nextIds;
                writeEntriesToDisk(next);
                writeIdsToDisk(nextIds);
            } catch (Exception e) {
                Log.w(TAG, "Error removing from history", e);
            }
        });
    }

    // ── 磁盘读写（原子替换，避免读线程拿到半截 JSON） ──────────────────────

    private List<JSONObject> readEntriesFromDisk() {
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

    private JSONArray readIdsFromDisk() {
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

    private void writeEntriesToDisk(List<JSONObject> entries) {
        JSONArray arr = new JSONArray();
        for (JSONObject entry : entries) {
            arr.put(entry);
        }
        atomicWrite(getHistoryFile(), arr.toString());
    }

    private void writeIdsToDisk(JSONArray arr) {
        atomicWrite(getHistoryIdsFile(), arr.toString());
    }

    private void atomicWrite(File target, String content) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            if (target.exists() && !target.delete()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error saving history: " + target.getName(), e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
