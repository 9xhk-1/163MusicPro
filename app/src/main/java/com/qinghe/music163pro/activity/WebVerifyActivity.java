package com.qinghe.music163pro.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qinghe.music163pro.util.MusicLog;

/**
 * Opens a WebView for Netease CAPTCHA / slider verification (code -462).
 * After the user completes the verification, cookies set on music.163.com
 * are captured and returned to the caller via Activity result.
 *
 * Extras (input):  EXTRA_URL = the verifyUrl from the -462 response
 * Result (output): EXTRA_COOKIE = cookie string for music.163.com (may be empty)
 */
public class WebVerifyActivity extends AppCompatActivity {

    public static final String EXTRA_URL    = "verify_url";
    public static final String EXTRA_COOKIE = "verify_cookie";

    private static final String TAG = "WebVerifyActivity";
    private static final String NETEASE_DOMAIN = "music.163.com";

    private WebView webView;
    private boolean resultDelivered = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String verifyUrl = getIntent().getStringExtra(EXTRA_URL);
        if (verifyUrl == null || verifyUrl.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Build layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF212121);

        // Header hint
        TextView hint = new TextView(this);
        hint.setText("请完成安全验证后自动继续登录");
        hint.setTextColor(0xFFAAAAAA);
        hint.setTextSize(12);
        hint.setPadding(12, 8, 12, 8);
        hint.setGravity(android.view.Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 8.1.0; Watch) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Mobile Safari/537.36");

        // Accept cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // let WebView handle all URLs
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                MusicLog.d(TAG, "页面开始加载: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                MusicLog.d(TAG, "页面加载完成: " + url);
                // After every page load, check if verification page is done
                // The verify page typically redirects back to the original domain or changes URL
                if (url != null && !url.contains("encrypt-pages") && !url.contains("st.music.163.com")) {
                    // Looks like verification completed — collect cookies
                    deliverResult();
                }
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Manual "验证完成" button — in case auto-detect doesn't trigger
        TextView btnDone = new TextView(this);
        btnDone.setText("✅  验证完成，继续登录");
        btnDone.setTextColor(0xFFFFFFFF);
        btnDone.setTextSize(13);
        btnDone.setGravity(android.view.Gravity.CENTER);
        btnDone.setPadding(12, 14, 12, 14);
        btnDone.setBackgroundColor(0xFF1565C0);
        btnDone.setClickable(true);
        btnDone.setFocusable(true);
        btnDone.setOnClickListener(v -> deliverResult());
        root.addView(btnDone, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        MusicLog.op(TAG, "打开验证WebView", verifyUrl);
        webView.loadUrl(verifyUrl);
    }

    private void deliverResult() {
        if (resultDelivered) return;
        resultDelivered = true;

        // Flush cookies to disk first
        CookieManager.getInstance().flush();

        // Collect cookies for netease domains
        String cookieStr = "";
        String[] domains = {
                "https://music.163.com",
                "https://www.music.163.com",
                "https://interface.music.163.com"
        };
        for (String domain : domains) {
            String c = CookieManager.getInstance().getCookie(domain);
            if (c != null && !c.isEmpty()) {
                if (cookieStr.isEmpty()) {
                    cookieStr = c;
                } else {
                    // Merge, avoiding duplicates
                    cookieStr = mergeCookies(cookieStr, c);
                }
            }
        }
        MusicLog.d(TAG, "验证完成，收集到Cookie: " + (cookieStr.isEmpty() ? "(空)" : "已获取"));

        Intent result = new Intent();
        result.putExtra(EXTRA_COOKIE, cookieStr);
        setResult(Activity.RESULT_OK, result);
        Toast.makeText(this, "验证完成，正在重新登录...", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Merge two cookie strings, second values override first for same names. */
    private String mergeCookies(String base, String extra) {
        // Simple append of extra parts not already in base
        String[] parts = extra.split(";");
        StringBuilder sb = new StringBuilder(base);
        for (String part : parts) {
            String name = part.trim().split("=")[0].trim();
            if (!base.contains(name + "=")) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
