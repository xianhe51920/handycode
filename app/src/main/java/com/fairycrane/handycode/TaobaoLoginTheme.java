package com.fairycrane.handycode;

import android.webkit.WebView;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Arrays;
import java.util.HashSet;

/** Theme the official login document, including its cross-origin login frame. */
final class TaobaoLoginTheme {
    private static final java.util.Set<String> ORIGINS = new HashSet<>(Arrays.asList(
        "https://login.m.taobao.com", "https://login.taobao.com",
        "https://pages-fast.m.taobao.com", "https://pages.m.taobao.com"));
    private final String source;
    private ScriptHandler registration;
    private Boolean registeredDark;
    private boolean frameReady;

    static void pageStarted(WebView view) {
        TaobaoLoginTheme theme = (TaobaoLoginTheme)view.getTag(R.id.taobao_login_theme);
        if (theme != null) theme.frameReady = false;
    }

    static boolean ready(WebView view) {
        TaobaoLoginTheme theme = (TaobaoLoginTheme)view.getTag(R.id.taobao_login_theme);
        return theme == null || theme.registration == null || theme.frameReady;
    }

    static void afterPaint(WebView view, boolean dark, Runnable done) {
        if (view == null) { done.run(); return; }
        awaitPaint(view, dark, android.os.SystemClock.uptimeMillis() + 700, done);
    }

    private static void awaitPaint(WebView view, boolean dark, long deadline, Runnable done) {
        if (!view.isAttachedToWindow()) { done.run(); return; }
        view.evaluateJavascript("!window.HandyCodeTaobaoLoginTheme || HandyCodeTaobaoLoginTheme.isSettled(" + dark + ")", result -> {
            if (!view.isAttachedToWindow()) { done.run(); return; }
            if (!"true".equals(result) && android.os.SystemClock.uptimeMillis() < deadline) {
                view.postDelayed(() -> awaitPaint(view, dark, deadline, done), 16);
            } else {
                view.postVisualStateCallback(0, new WebView.VisualStateCallback() {
                    @Override public void onComplete(long requestId) { done.run(); }
                });
            }
        });
    }

    static boolean acceptReady(WebView view, android.webkit.ConsoleMessage message) {
        if (!"__HANDYCODE_TAOBAO_LOGIN__:ready".equals(message.message())) return false;
        String sourceId = message.sourceId();
        // AndroidX document-start scripts have an empty console source URL.
        // This signal only releases the presentation cover; it never marks the
        // user authenticated or supplies any credential / business data.
        if (sourceId == null || sourceId.isEmpty()) {
            if (!UrlPolicy.isAllowed(view.getUrl())) return false;
        } else {
            android.net.Uri uri = android.net.Uri.parse(sourceId);
            if (!"https".equals(uri.getScheme()) || !("login.m.taobao.com".equals(uri.getHost())
                    || "login.taobao.com".equals(uri.getHost()))) return false;
        }
        TaobaoLoginTheme theme = (TaobaoLoginTheme)view.getTag(R.id.taobao_login_theme);
        if (theme != null) theme.frameReady = true;
        return true;
    }

    private TaobaoLoginTheme(WebView view) throws java.io.IOException {
        source = WebAssets.readText(view.getContext().getAssets(), "login-agreements.js") + "\n"
            + WebAssets.readText(view.getContext().getAssets(), "taobao-login-theme.js");
    }

    static void apply(WebView view, boolean dark) {
        if (view == null) return;
        TaobaoLoginTheme theme = (TaobaoLoginTheme)view.getTag(R.id.taobao_login_theme);
        if (theme == null) {
            try {
                theme = new TaobaoLoginTheme(view);
                view.setTag(R.id.taobao_login_theme, theme);
            } catch (java.io.IOException missing) { return; }
        }
        theme.update(view, dark);
    }

    private void update(WebView view, boolean dark) {
        // Register before loadUrl / popup transport. The same script runs inside
        // matching auth frames; no access to their credentials is required.
        if (registeredDark == null || registeredDark != dark) {
            if (registration != null) registration.remove();
            registration = null;
            if (WebViewFeature.isFeatureSupported("DOCUMENT_START_SCRIPT")) {
                registration = WebViewCompat.addDocumentStartJavaScript(view,
                    "window.__HC_TAOBAO_DARK__=" + dark + ";\n" + source, ORIGINS);
            }
            registeredDark = dark;
        }
        // Also updates an already loaded document and its registered auth frame.
        // Older WebViews retain the top-level theme without changing auth flow.
        view.evaluateJavascript("window.__HC_TAOBAO_DARK__=" + dark + ";\n" + source, null);
    }
}
