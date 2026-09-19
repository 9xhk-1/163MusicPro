package com.qinghe.music163pro.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal network image loader for watch UI covers.
 * - Memory cache with size cap.
 * - Request de-duplication per URL (multiple views share one download).
 * - Cancellable requests; cancelAll() stops everything (called on list exit).
 * - List scroll optimization: during fast fling no downloads are started,
 *   visible rows are (re)loaded when scrolling settles, and a few rows below
 *   the visible area are preloaded.
 */
public final class NetworkImageLoader {

    private static final String TAG = "NetworkImageLoader";
    private static final int TARGET_WIDTH = 320;
    private static final int TARGET_HEIGHT = 180;
    private static final int PRELOAD_BELOW = 3;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> MEMORY_CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 8 * 1024 * 1024)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value != null ? value.getByteCount() : 0;
                }
            };

    /** One download per URL; views waiting for it are tracked for rebinding. */
    private static class LoadRequest {
        final List<ImageView> views = new ArrayList<>();
        volatile boolean cancelled = false;
    }

    private static final Map<String, LoadRequest> ACTIVE_REQUESTS =
            Collections.synchronizedMap(new HashMap<String, LoadRequest>());

    /** True while a list is flinging fast: new downloads are skipped. */
    private static volatile boolean listScrollingFast = false;

    private NetworkImageLoader() {
    }

    /** Resolves the cover url of the item at a list position (may return null). */
    public interface CoverUrlProvider {
        String getUrl(int position);
    }

    public static void setListScrollingFast(boolean fast) {
        listScrollingFast = fast;
    }

    public static boolean isListScrollingFast() {
        return listScrollingFast;
    }

    /** Stop every pending download (used when leaving a list activity). */
    public static void cancelAll() {
        synchronized (ACTIVE_REQUESTS) {
            for (LoadRequest request : ACTIVE_REQUESTS.values()) {
                request.cancelled = true;
            }
            ACTIVE_REQUESTS.clear();
        }
    }

    public static void load(ImageView imageView, String imageUrl) {
        if (imageView == null) {
            return;
        }
        imageView.setTag(imageUrl);
        imageView.setImageDrawable(null);
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        Bitmap cached = MEMORY_CACHE.get(imageUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        synchronized (ACTIVE_REQUESTS) {
            LoadRequest existing = ACTIVE_REQUESTS.get(imageUrl);
            if (existing != null) {
                if (!existing.views.contains(imageView)) {
                    existing.views.add(imageView);
                }
                return;
            }
            if (listScrollingFast) {
                // 快速翻动越过的不加载
                return;
            }
            LoadRequest request = new LoadRequest();
            request.views.add(imageView);
            ACTIVE_REQUESTS.put(imageUrl, request);
            EXECUTOR.execute(() -> {
                Bitmap bitmap = null;
                if (!request.cancelled) {
                    bitmap = downloadBitmap(imageUrl, request);
                }
                final Bitmap finalBitmap = bitmap;
                List<ImageView> views;
                synchronized (ACTIVE_REQUESTS) {
                    views = new ArrayList<>(request.views);
                    ACTIVE_REQUESTS.remove(imageUrl);
                }
                if (finalBitmap != null) {
                    MEMORY_CACHE.put(imageUrl, finalBitmap);
                }
                for (ImageView view : views) {
                    final ImageView target = view;
                    target.post(() -> {
                        if (finalBitmap != null && imageUrl.equals(target.getTag())) {
                            target.setImageBitmap(finalBitmap);
                        }
                    });
                }
            });
        }
    }

    /** Download and cache only (no view binding), used to preload rows below. */
    public static void preload(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        if (MEMORY_CACHE.get(imageUrl) != null) {
            return;
        }
        synchronized (ACTIVE_REQUESTS) {
            if (ACTIVE_REQUESTS.containsKey(imageUrl)) {
                return;
            }
            if (listScrollingFast) {
                return;
            }
            LoadRequest request = new LoadRequest();
            ACTIVE_REQUESTS.put(imageUrl, request);
            EXECUTOR.execute(() -> {
                Bitmap bitmap = null;
                if (!request.cancelled) {
                    bitmap = downloadBitmap(imageUrl, request);
                }
                ACTIVE_REQUESTS.remove(imageUrl);
                if (bitmap != null) {
                    MEMORY_CACHE.put(imageUrl, bitmap);
                }
            });
        }
    }

    /**
     * Attach scroll optimization to a cover list:
     * - fast fling: pause downloads
     * - settled: rebind visible rows + preload a few below
     * Note: replaces any existing OnScrollListener on the list.
     */
    public static void attachListViewOptimization(final AbsListView listView,
                                                  final CoverUrlProvider provider) {
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                onListScrollStateChanged(view, scrollState, provider);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                onListScrolled(firstVisibleItem, visibleItemCount, totalItemCount, provider);
            }
        });
    }

    /** For lists with their own OnScrollListener: call from onScrollStateChanged. */
    public static void onListScrollStateChanged(AbsListView view, int scrollState,
                                                CoverUrlProvider provider) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
            setListScrollingFast(true);
        } else {
            setListScrollingFast(false);
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                reloadVisible(view, provider);
            }
        }
    }

    /** For lists with their own OnScrollListener: call from onScroll. */
    public static void onListScrolled(int firstVisibleItem, int visibleItemCount,
                                      int totalItemCount, CoverUrlProvider provider) {
        if (listScrollingFast || provider == null) {
            return;
        }
        int end = Math.min(firstVisibleItem + visibleItemCount + PRELOAD_BELOW, totalItemCount);
        for (int i = firstVisibleItem + visibleItemCount; i < end; i++) {
            preload(provider.getUrl(i));
        }
    }

    /** Re-trigger loading for the rows currently on screen. */
    private static void reloadVisible(AbsListView listView, CoverUrlProvider provider) {
        if (provider == null) return;
        int first = listView.getFirstVisiblePosition();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int position = first + i;
            if (position < 0) continue;
            String url = provider.getUrl(position);
            if (url == null || url.isEmpty()) continue;
            ImageView cover = findCoverView(child);
            if (cover != null) {
                load(cover, url);
            }
        }
    }

    /** Find the cover ImageView inside a bound list row, if present. */
    public static ImageView findCoverView(View row) {
        if (row == null) return null;
        ImageView iv = row.findViewById(com.qinghe.music163pro.R.id.iv_cover);
        if (iv == null) {
            iv = row.findViewById(com.qinghe.music163pro.R.id.iv_playlist_icon);
        }
        return iv;
    }

    private static Bitmap downloadBitmap(String imageUrl, LoadRequest request) {
        if (imageUrl.startsWith("file://")) {
            return loadLocalBitmap(imageUrl.substring(7));
        }
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.1.0) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            conn.connect();
            inputStream = conn.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                if (request != null && request.cancelled) {
                    return null;
                }
                outputStream.write(buffer, 0, count);
            }
            byte[] imageBytes = outputStream.toByteArray();
            if (imageBytes.length == 0) {
                return null;
            }

            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, boundsOptions);

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions, TARGET_WIDTH, TARGET_HEIGHT);
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, decodeOptions);
        } catch (Exception e) {
            MusicLog.w(TAG, "加载图片失败: " + imageUrl, e);
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        while ((height / inSampleSize) > reqHeight * 2 || (width / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private static Bitmap loadLocalBitmap(String filePath) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, opts);
            opts.inSampleSize = calculateInSampleSize(opts, 128, 128);
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(filePath, opts);
        } catch (Exception e) {
            return null;
        }
    }
}
