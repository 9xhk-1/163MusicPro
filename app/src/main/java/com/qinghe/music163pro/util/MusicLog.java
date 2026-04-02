package com.qinghe.music163pro.util;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized logger for 163Music Pro.
 * - Writes to Android LogCat
 * - Writes to /sdcard/163Music/app.log (max ~500 KB, then rotated)
 */
public class MusicLog {

    private static final String APP_TAG = "Music163Pro";
    private static final long MAX_LOG_SIZE = 500 * 1024; // 500 KB

    private static File logFile;
    private static boolean fileLoggingReady = false;

    // ──────────────────────────────────────────────────────
    //  Init
    // ──────────────────────────────────────────────────────

    /**
     * Call once at app startup (e.g. MainActivity.onCreate) to enable file logging.
     * @param sdcardBaseDir  the app's log directory, e.g. new File("/sdcard/163Music")
     */
    public static void init(File sdcardBaseDir) {
        try {
            if (!sdcardBaseDir.exists()) {
                sdcardBaseDir.mkdirs();
            }
            logFile = new File(sdcardBaseDir, "app.log");
            fileLoggingReady = true;
            i("MusicLog", "=== 日志初始化完成，文件: " + logFile.getAbsolutePath() + " ===");
        } catch (Exception e) {
            Log.e(APP_TAG, "日志文件初始化失败", e);
        }
    }

    // ──────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────

    public static void d(String tag, String msg) {
        Log.d(APP_TAG + "/" + tag, msg);
        writeToFile("D", tag, msg, null);
    }

    public static void i(String tag, String msg) {
        Log.i(APP_TAG + "/" + tag, msg);
        writeToFile("I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(APP_TAG + "/" + tag, msg);
        writeToFile("W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        Log.w(APP_TAG + "/" + tag, msg, t);
        writeToFile("W", tag, msg, t);
    }

    public static void e(String tag, String msg) {
        Log.e(APP_TAG + "/" + tag, msg);
        writeToFile("E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(APP_TAG + "/" + tag, msg, t);
        writeToFile("E", tag, msg, t);
    }

    /**
     * Log a full API call (URL, status code, full response body).
     */
    public static void api(String tag, String method, String url, int httpCode, String responseBody) {
        String msg = String.format("[API] %s %s → HTTP %d\n  响应: %s",
                method, url, httpCode,
                responseBody != null ? responseBody : "(empty)");
        i(tag, msg);
    }

    /**
     * Log a functional operation event.
     */
    public static void op(String tag, String operation, String detail) {
        i(tag, "[OP] " + operation + (detail != null ? " | " + detail : ""));
    }

    // ──────────────────────────────────────────────────────
    //  Internal
    // ──────────────────────────────────────────────────────

    private static synchronized void writeToFile(String level, String tag, String msg, Throwable t) {
        if (!fileLoggingReady || logFile == null) return;
        try {
            // Rotate if too large
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                File backup = new File(logFile.getParent(), "app.log.bak");
                if (backup.exists()) backup.delete();
                logFile.renameTo(backup);
            }
            // Create a new formatter per call to avoid SimpleDateFormat thread-safety issues
            SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
            String line = fmt.format(new Date()) + " " + level + "/" + tag + ": " + msg;
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                bw.write(line);
                bw.newLine();
                if (t != null) {
                    bw.write("  Exception: " + t.getClass().getName() + ": " + t.getMessage());
                    bw.newLine();
                    for (StackTraceElement el : t.getStackTrace()) {
                        bw.write("    at " + el.toString());
                        bw.newLine();
                    }
                }
            }
        } catch (IOException ignored) {
            // File logging is best-effort; never crash the app
        }
    }
}
