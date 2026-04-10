package com.qinghe.music163pro.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

public final class AudioFingerprintHelper {

    private static final String TAG = "AudioFingerprintHelper";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int PCM16_BYTES_PER_SAMPLE = 2;

    public interface Callback {
        void onSuccess(String fingerprintBase64, int durationSec);
        void onError(String message);
    }

    private AudioFingerprintHelper() {
    }

    public static void generateFingerprint(Activity activity, byte[] pcmData, int pcm16SampleRate,
                                           Callback callback) {
        if (activity == null || pcmData == null || pcmData.length == 0) {
            callback.onError("录音数据为空");
            return;
        }
        int durationSec = Math.max(1,
                (int) Math.ceil(pcmData.length / (pcm16SampleRate * (double) PCM16_BYTES_PER_SAMPLE)));
        String pcmBase64 = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP);
        MAIN_HANDLER.post(() -> new FingerprintSession(activity, pcmBase64, durationSec, callback).start());
    }

    private static final class FingerprintSession {
        private final WeakReference<Activity> activityRef;
        private final String pcmBase64;
        private final int durationSec;
        private final Callback callback;
        private WebView webView;
        private boolean finished;

        private FingerprintSession(Activity activity, String pcmBase64, int durationSec, Callback callback) {
            this.activityRef = new WeakReference<>(activity);
            this.pcmBase64 = pcmBase64;
            this.durationSec = durationSec;
            this.callback = callback;
        }

        @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
        private void start() {
            Activity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                deliverError("界面已关闭");
                return;
            }

            webView = new WebView(activity);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            // Local fingerprint assets are loaded from app assets, so file access is
            // required for this isolated WebView even though network/content access stays blocked.
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(false);
            settings.setDomStorageEnabled(true);
            settings.setBlockNetworkLoads(true);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 8.1.0; Watch) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/95.0.4638.69 Mobile Safari/537.36");
            webView.addJavascriptInterface(new Bridge(this), "AndroidFingerprintBridge");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (finished) {
                        return;
                    }
                    evaluateFingerprint();
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request,
                                            WebResourceError error) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && request != null
                            && request.isForMainFrame()) {
                        CharSequence description = error != null ? error.getDescription() : null;
                        deliverError(description != null ? description.toString() : "指纹页面加载失败");
                    }
                }
            });
            webView.loadUrl("file:///android_asset/audio_fingerprint.html");
        }

        private void evaluateFingerprint() {
            if (webView == null || finished) {
                return;
            }
            // Local assets (audio_fingerprint.html + afp.js + afp.wasm.js) perform
            // the NetEase-compatible fingerprint generation inside an isolated WebView.
            String script = "window.generateFingerprintFromPcm(" + JSONObject.quote(pcmBase64) + ");";
            webView.evaluateJavascript(script, null);
        }

        private void deliverSuccess(String fingerprintBase64) {
            if (finished) {
                return;
            }
            finished = true;
            cleanup();
            callback.onSuccess(fingerprintBase64, durationSec);
        }

        private void deliverError(String message) {
            if (finished) {
                return;
            }
            finished = true;
            cleanup();
            callback.onError(message == null || message.isEmpty() ? "指纹生成失败" : message);
        }

        private void cleanup() {
            if (webView == null) {
                return;
            }
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeJavascriptInterface("AndroidFingerprintBridge");
                webView.destroy();
            } catch (Exception e) {
                MusicLog.w(TAG, "WebView cleanup failed: " + e.getMessage());
            } finally {
                webView = null;
            }
        }
    }

    private static final class Bridge {
        private final WeakReference<FingerprintSession> sessionRef;

        private Bridge(FingerprintSession session) {
            this.sessionRef = new WeakReference<>(session);
        }

        @JavascriptInterface
        public void onSuccess(String fingerprintBase64) {
            MAIN_HANDLER.post(() -> {
                FingerprintSession session = sessionRef.get();
                if (session != null) {
                    session.deliverSuccess(fingerprintBase64);
                }
            });
        }

        @JavascriptInterface
        public void onError(String message) {
            MAIN_HANDLER.post(() -> {
                FingerprintSession session = sessionRef.get();
                if (session != null) {
                    session.deliverError(message);
                }
            });
        }
    }
}
