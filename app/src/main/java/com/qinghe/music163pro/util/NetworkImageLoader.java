package com.qinghe.music163pro.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal network image loader for watch UI covers.
 */
public final class NetworkImageLoader {

    private static final String TAG = "NetworkImageLoader";
    private static final int TARGET_WIDTH = 320;
    private static final int TARGET_HEIGHT = 180;
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
        EXECUTOR.execute(() -> {
            Bitmap bitmap = downloadBitmap(imageUrl);
            if (bitmap != null) {
                MEMORY_CACHE.put(imageUrl, bitmap);
            }
            imageView.post(() -> {
                Object tag = imageView.getTag();
                if (bitmap != null && imageUrl.equals(tag)) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap downloadBitmap(String imageUrl) {
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
}
