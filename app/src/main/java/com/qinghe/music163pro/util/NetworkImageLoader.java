package com.qinghe.music163pro.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal network image loader for watch UI covers.
 * Supports memory cache + disk cache.
 * Disk cache filenames are derived from the URL path with '/' replaced by '_'.
 */
public final class NetworkImageLoader {

    private static final String TAG = "NetworkImageLoader";
    private static final int TARGET_WIDTH = 320;
    private static final int TARGET_HEIGHT = 180;
    private static final String DISK_CACHE_DIR = "163Music/cache/images";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> MEMORY_CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 8 * 1024 * 1024)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value != null ? value.getByteCount() : 0;
                }
            };

    private NetworkImageLoader() {
    }

    /** Derive disk cache filename from URL: use the URL path portion, replace '/' with '_'. */
    private static String urlToCacheFileName(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            String path = url.getPath(); // e.g. /NT9MwL8YhIxU0xFajzhMZQ==/109951168957906132.jpg
            if (path == null || path.isEmpty()) return null;
            // Replace '/' with '_', strip leading '_'
            String name = path.replace('/', '_');
            if (name.startsWith("_")) name = name.substring(1);
            // Ensure it has a reasonable extension
            if (!name.contains(".")) name = name + ".jpg";
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private static File getDiskCacheDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), DISK_CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Load bitmap from disk cache, or null if not cached. */
    private static Bitmap loadFromDiskCache(String cacheFileName) {
        if (cacheFileName == null) return null;
        try {
            File file = new File(getDiskCacheDir(), cacheFileName);
            if (!file.exists()) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            opts.inSampleSize = calculateInSampleSize(opts, TARGET_WIDTH, TARGET_HEIGHT);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    /** Save raw bytes to disk cache. */
    private static void saveToDiskCache(String cacheFileName, byte[] data) {
        if (cacheFileName == null || data == null || data.length == 0) return;
        try {
            File file = new File(getDiskCacheDir(), cacheFileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
        } catch (Exception ignored) {
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
        // 1. Memory cache hit
        Bitmap cached = MEMORY_CACHE.get(imageUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            final String cacheFileName = imageUrl.startsWith("file://") ? null : urlToCacheFileName(imageUrl);

            // 2. Disk cache hit
            if (cacheFileName != null) {
                bitmap = loadFromDiskCache(cacheFileName);
                if (bitmap != null) {
                    MEMORY_CACHE.put(imageUrl, bitmap);
                }
            }

            // 3. Network download
            if (bitmap == null) {
                bitmap = downloadBitmap(imageUrl, cacheFileName);
                if (bitmap != null) {
                    MEMORY_CACHE.put(imageUrl, bitmap);
                }
            }

            final Bitmap finalBitmap = bitmap;
            imageView.post(() -> {
                Object tag = imageView.getTag();
                if (finalBitmap != null && imageUrl.equals(tag)) {
                    imageView.setImageBitmap(finalBitmap);
                }
            });
        });
    }

    private static Bitmap downloadBitmap(String imageUrl, String cacheFileName) {
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
            byte[] imageBytes = readAllBytes(inputStream);
            if (imageBytes.length == 0) {
                return null;
            }

            // Save raw bytes to disk cache
            saveToDiskCache(cacheFileName, imageBytes);

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

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
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
