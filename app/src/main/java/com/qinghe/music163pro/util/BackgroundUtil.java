package com.qinghe.music163pro.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Custom background image support for the main player and lyrics screens.
 * The selected image is copied into internal storage and a darkened copy is
 * applied as the screen background so text remains readable.
 */
public final class BackgroundUtil {

    private static final String FILE_NAME = "custom_background.jpg";
    private static final int DEFAULT_BG_COLOR = 0xFF121212;
    private static final int SCRIM_ALPHA = 150; // 0..255

    private BackgroundUtil() {
    }

    public static File getBackgroundFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean hasCustomBackground(Context context) {
        return getBackgroundFile(context).exists();
    }

    public static void saveBackground(Context context, Uri uri) {
        if (context == null || uri == null) return;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return;
            FileOutputStream fos = new FileOutputStream(getBackgroundFile(context));
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            fos.close();
            is.close();
        } catch (Exception ignored) {
        }
    }

    public static void clearBackground(Context context) {
        getBackgroundFile(context).delete();
    }

    /**
     * Apply the custom background (darkened) to a root view, or reset to the
     * default dark color when no background is set.
     */
    public static void applyBackground(Context context, View root) {
        if (root == null) return;
        if (!hasCustomBackground(context)) {
            root.setBackgroundColor(DEFAULT_BG_COLOR);
            return;
        }
        try {
            int reqW = context.getResources().getDisplayMetrics().widthPixels;
            int reqH = context.getResources().getDisplayMetrics().heightPixels;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(getBackgroundFile(context).getAbsolutePath(), opts);
            opts.inSampleSize = calculateSampleSize(opts, reqW, reqH);
            opts.inJustDecodeBounds = false;
            Bitmap src = BitmapFactory.decodeFile(getBackgroundFile(context).getAbsolutePath(), opts);
            if (src == null) return;
            Bitmap dimmed = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dimmed);
            canvas.drawBitmap(src, 0, 0, null);
            Paint scrim = new Paint();
            scrim.setColor(Color.argb(SCRIM_ALPHA, 0, 0, 0));
            canvas.drawRect(0, 0, dimmed.getWidth(), dimmed.getHeight(), scrim);
            root.setBackground(new BitmapDrawable(context.getResources(), dimmed));
            src.recycle();
        } catch (Exception ignored) {
            root.setBackgroundColor(DEFAULT_BG_COLOR);
        }
    }

    private static int calculateSampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
        int sampleSize = 1;
        while (opts.outWidth / sampleSize > reqW * 2 || opts.outHeight / sampleSize > reqH * 2) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }
}
