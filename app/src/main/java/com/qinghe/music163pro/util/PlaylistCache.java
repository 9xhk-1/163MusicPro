package com.qinghe.music163pro.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.qinghe.music163pro.model.PlaylistInfo;
import com.qinghe.music163pro.model.Song;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 歌单 / 歌曲列表的本地快照，用来做「先显示、后校准」的秒开。
 *
 * 手表网络慢，每次进歌单都要等接口回来才出列表。这里做两级缓存：
 *   1. 进程内内存缓存：同一个歌单来回切时零延迟；
 *   2. 磁盘缓存（对象序列化）：杀进程 / 重启后仍能立刻出列表。
 *
 * 打开页面时先用快照渲染，接口返回后由调用方用 sameSongs / samePlaylists
 * 对比：完全一致就完全不动 UI（不闪烁、不重建封面），有差异才整体替换并写回快照。
 * 后写磁盘走单线程后台，不阻塞主线程；快照损坏自动丢弃，下次重新缓存。
 *
 * 大歌单只缓存前 {@link #MAX_CACHE_SONGS} 首，控制反序列化时间，
 * 避免 0.78GB 内存的手表在打开页面时被一次大对象读取卡住。
 */
public final class PlaylistCache {

    private static final String TAG = "PlaylistCache";
    /** 常规歌单都能整份缓存；超大歌单只留前 500 首，避免一次反序列化卡住主线程。 */
    public static final int MAX_CACHE_SONGS = 500;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Long, Snapshot> MEM_SONGS = new HashMap<>();
    private static List<PlaylistInfo> MEM_PLAYLISTS;

    public interface CacheCallback<T> {
        /** 主线程回调；读不到快照时 value 为 null。 */
        void onLoaded(T value);
    }

    /** 歌单详情快照：歌曲列表 + 接口一起返回的元信息。 */
    public static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        public final List<Song> songs;
        public final int specialType;
        public final String creator;
        public final long creatorUserId;
        public final boolean subscribed;

        public Snapshot(List<Song> songs, int specialType, String creator,
                        long creatorUserId, boolean subscribed) {
            this.songs = new ArrayList<>(songs);
            this.specialType = specialType;
            this.creator = creator;
            this.creatorUserId = creatorUserId;
            this.subscribed = subscribed;
        }
    }

    private PlaylistCache() {
    }

    // ── v54：纯内存快速路径（零 IO，主线程可安全调用） ──────────────────────

    /** 只取进程内快照，绝不读盘；冷启动返回 null。 */
    public static synchronized List<PlaylistInfo> peekMyPlaylists() {
        return MEM_PLAYLISTS == null ? null : new ArrayList<>(MEM_PLAYLISTS);
    }

    /** 只取进程内快照，绝不读盘；冷启动返回 null。 */
    public static synchronized Snapshot peekPlaylist(long playlistId) {
        return MEM_SONGS.get(playlistId);
    }

    /**
     * 磁盘快照异步读取：ObjectInputStream 反序列化最多 500 首 Song，
     * 旧实现放在主线程会把打开歌单的那一下卡住。现在放 IO 线程，
     * 读到的结果回填内存缓存后回主线程，调用方自行决定是否渲染。
     */
    public static void loadMyPlaylistsAsync(Context context,
                                            CacheCallback<List<PlaylistInfo>> callback) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<PlaylistInfo> out = null;
            synchronized (PlaylistCache.class) {
                if (MEM_PLAYLISTS != null) {
                    out = new ArrayList<>(MEM_PLAYLISTS);
                }
            }
            if (out == null) {
                Object obj = readObject(app, "my_playlists.cache");
                if (obj instanceof List) {
                    List<PlaylistInfo> list = new ArrayList<>();
                    for (Object item : (List<?>) obj) {
                        if (item instanceof PlaylistInfo) list.add((PlaylistInfo) item);
                    }
                    if (!list.isEmpty()) {
                        synchronized (PlaylistCache.class) {
                            if (MEM_PLAYLISTS == null) MEM_PLAYLISTS = new ArrayList<>(list);
                            out = new ArrayList<>(MEM_PLAYLISTS);
                        }
                    }
                }
            }
            final List<PlaylistInfo> result = out;
            MAIN.post(() -> callback.onLoaded(result));
        });
    }

    public static void loadPlaylistAsync(Context context, long playlistId,
                                         CacheCallback<Snapshot> callback) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            Snapshot out;
            synchronized (PlaylistCache.class) {
                out = MEM_SONGS.get(playlistId);
            }
            if (out == null) {
                Object obj = readObject(app, "playlist_" + playlistId + ".cache");
                if (obj instanceof Snapshot) {
                    Snapshot disk = (Snapshot) obj;
                    synchronized (PlaylistCache.class) {
                        out = MEM_SONGS.get(playlistId);
                        if (out == null) {
                            MEM_SONGS.put(playlistId, disk);
                            out = disk;
                        }
                    }
                }
            }
            final Snapshot result = out;
            MAIN.post(() -> callback.onLoaded(result));
        });
    }

    /** 读不到返回 null，调用方照常走网络。（保留兼容；新代码请用 peek* + *Async） */
    public static synchronized List<PlaylistInfo> loadMyPlaylists(Context context) {
        if (MEM_PLAYLISTS != null) {
            return new ArrayList<>(MEM_PLAYLISTS);
        }
        Object obj = readObject(context, "my_playlists.cache");
        if (obj instanceof List) {
            List<PlaylistInfo> list = new ArrayList<>();
            for (Object item : (List<?>) obj) {
                if (item instanceof PlaylistInfo) {
                    list.add((PlaylistInfo) item);
                }
            }
            MEM_PLAYLISTS = list;
            return new ArrayList<>(list);
        }
        return null;
    }

    public static synchronized void saveMyPlaylists(Context context, List<PlaylistInfo> playlists) {
        MEM_PLAYLISTS = new ArrayList<>(playlists);
        final ArrayList<PlaylistInfo> copy = new ArrayList<>(playlists);
        final Context app = context.getApplicationContext();
        IO.execute(() -> writeObject(app, "my_playlists.cache", copy));
    }

    public static synchronized Snapshot loadPlaylist(Context context, long playlistId) {
        Snapshot mem = MEM_SONGS.get(playlistId);
        if (mem != null) {
            return mem;
        }
        Object obj = readObject(context, "playlist_" + playlistId + ".cache");
        if (obj instanceof Snapshot) {
            Snapshot snapshot = (Snapshot) obj;
            MEM_SONGS.put(playlistId, snapshot);
            return snapshot;
        }
        return null;
    }

    public static synchronized void savePlaylist(Context context, long playlistId, Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        MEM_SONGS.put(playlistId, snapshot);
        final Context app = context.getApplicationContext();
        IO.execute(() -> writeObject(app, "playlist_" + playlistId + ".cache", snapshot));
    }

    /** 歌单内单曲删除 / 创建歌单后，让快照失效，避免下次秒开显示旧数据。 */
    public static synchronized void invalidatePlaylist(Context context, long playlistId) {
        MEM_SONGS.remove(playlistId);
        // 同步删除：调用方紧接着就会重新读缓存，异步删会读到刚作废旧文件
        File f = new File(context.getApplicationContext().getCacheDir(),
                "playlist_" + playlistId + ".cache");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    public static synchronized void invalidateMyPlaylists(Context context) {
        MEM_PLAYLISTS = null;
        File f = new File(context.getApplicationContext().getCacheDir(), "my_playlists.cache");
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** 只缓存前若干首，避免超大歌单拖慢反序列化。 */
    public static List<Song> trimForCache(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(songs.subList(0, Math.min(songs.size(), MAX_CACHE_SONGS)));
    }

    /** 顺序 + 主要展示字段全一致，就不需要刷新列表。 */
    public static boolean sameSongs(List<Song> a, List<Song> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Song x = a.get(i);
            Song y = b.get(i);
            if (x.getId() != y.getId()
                    || !eq(x.getDisplayName(), y.getDisplayName())
                    || !eq(x.getArtist(), y.getArtist())
                    || !eq(x.getCoverUrl(), y.getCoverUrl())
                    || x.getFee() != y.getFee()) {
                return false;
            }
        }
        return true;
    }

    public static boolean samePlaylists(List<PlaylistInfo> a, List<PlaylistInfo> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            PlaylistInfo x = a.get(i);
            PlaylistInfo y = b.get(i);
            if (x.getId() != y.getId()
                    || !eq(x.getName(), y.getName())
                    || x.getTrackCount() != y.getTrackCount()
                    || !eq(x.getCreator(), y.getCreator())
                    || x.getUserId() != y.getUserId()
                    || !eq(x.getCoverUrl(), y.getCoverUrl())
                    || x.isSubscribed() != y.isSubscribed()) {
                return false;
            }
        }
        return true;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static Object readObject(Context context, String name) {
        File f = new File(context.getApplicationContext().getCacheDir(), name);
        if (!f.exists()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return in.readObject();
        } catch (Exception e) {
            Log.w(TAG, "快照损坏，丢弃后重新缓存: " + name, e);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
    }

    private static void writeObject(Context app, String name, Serializable value) {
        File dir = app.getCacheDir();
        File tmp = new File(dir, name + ".tmp");
        File target = new File(dir, name);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmp))) {
            out.writeObject(value);
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "快照写入失败: " + name, e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
