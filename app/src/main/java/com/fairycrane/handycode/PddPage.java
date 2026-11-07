package com.fairycrane.handycode;

import android.content.Context;
import android.graphics.Color;
import android.net.http.SslError;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Official H5 login and identity page, using its existing first-party proxy. */
final class PddPage extends LinearLayout {
    static final String ENTRY = "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE";
    private WebView web;
    private final TextView status;
    private final LinearLayout actions;
    private final FrameLayout content;
    private final PddLoadingView cover;
    private final Button retryBtn, clientBtn;
    private long navigation;
    private boolean packageNavigation;
    private boolean pageCommitted;
    private boolean coverFading;
    private Runnable presentationWatch;
    private float coverWidth, coverIdentity, coverParcels, coverHeading;
    private String compatibilityScript;
    private String loginClientScript;
    private final Runnable openClient;
    private final java.util.function.Consumer<Boolean> sessionChanged;
    private final Map<String, byte[]> cachedAssets = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean destroyed;
    private volatile boolean bundleAdjusted;
    private boolean sawLogin, returnedAfterLogin;
    private boolean layoutWorkflow;
    private boolean dark;

    void checkVisibleScanCode(android.webkit.ValueCallback<String> callback) {
        if (web == null || layoutWorkflow || cover.getVisibility() == View.VISIBLE || !pageCommitted) {
            callback.onReceiveValue("false");
            return;
        }
        web.evaluateJavascript(ScanCodeVisibility.SCRIPT, callback);
    }

    PddPage(Context context, Runnable openClient, java.util.function.Consumer<Boolean> sessionChanged) {
        super(context);
        this.openClient = openClient;
        this.sessionChanged = sessionChanged;
        setOrientation(VERTICAL);
        dark = AppTheme.isDark(context, AppTheme.MODE_SYSTEM);
        setBackgroundColor(AppTheme.bg(dark));
        actions = new LinearLayout(context);
        actions.setPadding(dp(12), dp(4), dp(12), dp(4));
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        status = new TextView(context);
        status.setTextSize(12);
        status.setTextColor(0xff697386);
        status.setText("正在打开官方身份码网页…");
        actions.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        retryBtn = action("重试");
        retryBtn.setOnClickListener(view -> reload());
        actions.addView(retryBtn);
        clientBtn = action("打开客户端");
        clientBtn.setOnClickListener(view -> openClient.run());
        actions.addView(clientBtn);
        addView(actions);
        actions.setVisibility(View.GONE);
        content = new FrameLayout(context);
        content.setBackgroundColor(AppTheme.bg(dark));
        cover = new PddLoadingView(context);
        cover.setDark(dark);
        content.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        try {
            compatibilityScript = readScript("pdd-same-origin.js") + "\n" + readScript("pdd-section-refresh.js") + "\n" + readScript("pdd-layout.js");
            loginClientScript = readScript("pdd-login-client.js");
        } catch (java.io.IOException unavailable) {
            actions.setVisibility(View.VISIBLE);
            status.setText("无法读取网页兼容资源，请重新安装");
        }
    }

    void setDark(boolean value) {
        dark = value;
        setBackgroundColor(AppTheme.bg(dark));
        if (content != null) content.setBackgroundColor(AppTheme.bg(dark));
        actions.setBackgroundColor(AppTheme.surface(dark));
        status.setTextColor(AppTheme.muted(dark));
        retryBtn.setTextColor(AppTheme.accentText(dark));
        clientBtn.setTextColor(AppTheme.accentText(dark));
        cover.setDark(dark);
        if (web != null) {
            web.setBackgroundColor(AppTheme.bg(dark));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            syncDark(web);
        }
    }

    private void syncDark(WebView view) {
        if (view == null || destroyed) return;
        String script = "(function(d){"
            + "window.__HC_DARK__=d;"
            + "var root=document.documentElement;"
            + "var bg=d?'#0f1218':'#f5f6f8';"
            + "if(root){"
            + "root.setAttribute('data-hc-theme',d?'dark':'light');"
            + "root.style.setProperty('background',bg,'important');"
            + "root.style.setProperty('background-color',bg,'important');"
            + "root.style.setProperty('min-height','100vh','important');"
            + "}"
            + "if(document.body){"
            + "document.body.style.setProperty('background',bg,'important');"
            + "document.body.style.setProperty('background-color',bg,'important');"
            + "document.body.style.setProperty('min-height','100vh','important');"
            + "}"
            + "var st=document.getElementById('hc-pdd-early-theme');"
            + "if(!st&&(document.head||root)){"
            + "st=document.createElement('style');st.id='hc-pdd-early-theme';"
            + "(document.head||root).appendChild(st);"
            + "}"
            + "if(st){"
            + "st.textContent='html,body{min-height:100vh!important}'"
            + "+'html[data-hc-theme=\"dark\"],html[data-hc-theme=\"dark\"] body,html[data-hc-theme=\"dark\"] #main,html[data-hc-theme=\"dark\"] #login,html[data-hc-theme=\"dark\"] #login .main,html[data-hc-theme=\"dark\"] #login .container,html[data-hc-theme=\"dark\"] .qr-code-login-container,html[data-hc-theme=\"dark\"] .phone-bind-ui{background:#0f1218!important;background-color:#0f1218!important;color:#e6eaf2!important;color-scheme:dark}'"
            + "+'html[data-hc-theme=\"dark\"] #login .container .login-ui-form .login-input,html[data-hc-theme=\"dark\"] ._3W0iZ9uG,html[data-hc-theme=\"dark\"] .phone-bind-ui .back-btn,html[data-hc-theme=\"dark\"] #login .container .login-ui-form .submit-button-login,html[data-hc-theme=\"dark\"] #login .container .login-ui-form .login-input .input-group #code-button{background:#181c24!important;background-color:#181c24!important;color:#e6eaf2!important}'"
            + "+'html[data-hc-theme=\"dark\"] #login .container .login-ui-form .login-input .input-group input,html[data-hc-theme=\"dark\"] .phone-bind-ui input,html[data-hc-theme=\"dark\"] .OsyyMIcJ,html[data-hc-theme=\"dark\"] .qr-code-login-container .title,html[data-hc-theme=\"dark\"] .phone-bind-ui .verify-code .title{color:#e6eaf2!important;background:transparent!important}'"
            + "+'html[data-hc-theme=\"dark\"] ._1Z07n9Tg,html[data-hc-theme=\"dark\"] .agreement-wrap .link,html[data-hc-theme=\"dark\"] .qr-code-login-container .tip-line,html[data-hc-theme=\"dark\"] .phone-bind-ui .verify-code .desc{color:#9aa4b5!important}';"
            + "}"
            + "if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(d);"
            + "})(" + dark + ");";
        view.evaluateJavascript(script, null);
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private Button action(String title) {
        Button button = new Button(getContext());
        button.setText(title);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        return button;
    }

    private String readScript(String name) throws java.io.IOException {
        try (InputStream input = getContext().getAssets().open(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void showCover() {
        stopPresentationWatch();
        coverFading = false;
        cover.animate().cancel();
        cover.stopMorph();
        cover.setAlpha(1f);
        cover.setVisibility(View.VISIBLE);
        cover.invalidate();
    }

    private void revealPreparedPage(WebView view) {
        if (view == null || web != view || !pageCommitted || coverFading || cover.getVisibility() != View.VISIBLE) return;
        final long expected = navigation;
        view.postVisualStateCallback(expected, new WebView.VisualStateCallback() {
            @Override public void onComplete(long requestId) {
                if (destroyed || web != view || navigation != expected || coverFading || cover.getVisibility() != View.VISIBLE) return;
                coverFading = true;
                stopPresentationWatch();
                if (packageNavigation) cover.morphTo(coverWidth, coverIdentity, coverParcels, coverHeading);
                cover.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    if (!destroyed && navigation == expected) cover.setVisibility(View.GONE);
                }).start();
            }
        });
    }

    private void revealAssembledPage(WebView view) {
        if (view == null || web != view) return;
        syncDark(view);
        if (!pageCommitted || coverFading || cover.getVisibility() != View.VISIBLE) return;
        final long expected = navigation;
        view.evaluateJavascript("(function(){var s=document.getElementById('hc-pdd-shell');if(!window.HandyPddLayout||!s||!document.querySelector('[data-hc-pdd-source]'))return null;return {width:innerWidth,identity:document.getElementById('hc-pdd-identity').getBoundingClientRect().height,parcels:document.getElementById('hc-pdd-parcels').getBoundingClientRect().height,heading:s.querySelector('.hc-pdd-heading').getBoundingClientRect().height};})()", ready -> {
            if (destroyed || web != view || navigation != expected) return;
            try {
                org.json.JSONObject geometry = new org.json.JSONObject(ready);
                coverWidth = (float)geometry.getDouble("width");
                coverIdentity = (float)geometry.getDouble("identity");
                coverParcels = (float)geometry.getDouble("parcels");
                coverHeading = (float)geometry.getDouble("heading");
                revealPreparedPage(view);
            } catch (org.json.JSONException notReady) { /* Keep the opaque cover. */ }
        });
    }

    private boolean isPackageUrl(String url) {
        return UrlPolicy.isPddAllowed(url) && "/mdkd/package".equals(android.net.Uri.parse(url).getPath());
    }

    private void stopPresentationWatch() {
        if (presentationWatch != null) removeCallbacks(presentationWatch);
        presentationWatch = null;
    }

    private void watchPresentation(final WebView view) {
        stopPresentationWatch();
        final long expected = navigation;
        presentationWatch = new Runnable() {
            private int attempts;
            @Override public void run() {
                if (presentationWatch != this) return;
                if (destroyed || web != view || navigation != expected || coverFading
                        || cover.getVisibility() != View.VISIBLE || attempts++ >= 60) {
                    stopPresentationWatch(); return;
                }
                if (isPackageUrl(view.getUrl())) { packageNavigation = true; revealAssembledPage(view); }
                else if (UrlPolicy.isPddAllowed(view.getUrl())) revealPreparedPage(view);
                if (presentationWatch == this) postDelayed(this, 500);
            }
        };
        postDelayed(presentationWatch, 250);
    }

    void showPage() {
        if (destroyed || compatibilityScript == null) return;
        cover.setAnimating(true);
        if (web != null) { web.onResume(); syncDark(web); return; }
        web = new WebView(getContext());
        web.setBackgroundColor(AppTheme.bg(dark));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (UrlPolicy.opensPddIdentity(view.getUrl(), request.getUrl().toString(), request.isForMainFrame(), request.hasGesture())) {
                    openClient.run();
                    return true;
                }
                if (!request.isForMainFrame() || UrlPolicy.isPddAllowed(request.getUrl().toString())) return false;
                status.setText("网页请求打开其他地址，请使用官方登录页");
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (UrlPolicy.isPddAllowed(url)) return false;
                status.setText("网页请求打开其他地址，请使用官方登录页");
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                boolean appBundle = UrlPolicy.isPddAppBundle(url);
                String refreshPart = UrlPolicy.pddRefreshPart(url);
                if (!"GET".equals(request.getMethod()) || (!appBundle && refreshPart == null)) return null;
                byte[] bytes = adjustedBundle(url);
                if (bytes == null) {
                    if (appBundle) post(() -> { if (!destroyed) { actions.setVisibility(View.VISIBLE); status.setText("网页兼容加载失败，请点重试"); } });
                    return null;
                }
                if (appBundle) bundleAdjusted = true;
                Map<String, String> headers = new HashMap<>();
                headers.put("Cache-Control", "no-store");
                headers.put("Access-Control-Allow-Origin", "https://m.pinduoduo.net");
                InputStream stream = new ByteArrayInputStream(bytes);
                if (appBundle) {
                    byte[] prefix = ("window.__HC_DARK__=" + dark + ";\n").getBytes(StandardCharsets.UTF_8);
                    stream = new java.io.SequenceInputStream(new ByteArrayInputStream(prefix), stream);
                }
                return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, stream);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                final long expected = ++navigation;
                pageCommitted = false;
                if (UrlPolicy.isPddLoginPage(url)) sessionChanged.accept(false);
                showCover();
                layoutWorkflow = false;
                boolean packagePage = isPackageUrl(url);
                packageNavigation = packagePage;
                actions.setVisibility(View.GONE);
                if (packagePage) postDelayed(() -> {
                    if (!destroyed && navigation == expected && cover.getVisibility() == View.VISIBLE && cover.getAlpha() == 1f) {
                        actions.setVisibility(View.VISIBLE);
                        status.setText("页面暂未就绪，请点重试");
                    }
                }, 18000);
                if (!UrlPolicy.isPddAllowed(url)) {
                    view.stopLoading();
                    status.setText("请留在拼多多官方网页完成登录");
                } else {
                    boolean identity = android.net.Uri.parse(url).getPath().startsWith("/mdkd/");
                    if (android.net.Uri.parse(url).getPath().contains("login")) sawLogin = true;
                    status.setText(identity ? "正在加载官方身份码…" : "请在官方网页完成登录或验证");
                }
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                if (destroyed || web != view || !UrlPolicy.isPddAllowed(url)) return;
                pageCommitted = true;
                packageNavigation = isPackageUrl(url);
                prepareLoginClientButton(view, url);
                syncDark(view);
                if (packageNavigation) revealAssembledPage(view);
                else if (UrlPolicy.isPddAllowed(url)) revealPreparedPage(view);
                watchPresentation(view);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (destroyed || web != view || !UrlPolicy.isPddAllowed(url) || !url.equals(view.getUrl())) return;
                CookieManager.getInstance().flush();
                prepareLoginClientButton(view, url);
                syncDark(view);
                if (UrlPolicy.isPddAllowed(url) && "/index.html".equals(android.net.Uri.parse(url).getPath())
                        && sawLogin && !returnedAfterLogin) {
                    returnedAfterLogin = true;
                    view.post(() -> { if (!destroyed && web == view) view.loadUrl(ENTRY); });
                    return;
                }
                pageCommitted = true;
                packageNavigation = isPackageUrl(url);
                if (packageNavigation) revealAssembledPage(view);
                else revealPreparedPage(view);
                watchPresentation(view);
                if (UrlPolicy.isPddAllowed(url) && android.net.Uri.parse(url).getPath().startsWith("/mdkd/")) {
                    status.setText(bundleAdjusted ? "官方身份码网页" : "网页兼容未确认，请点重试");
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) { actions.setVisibility(View.VISIBLE); status.setText("网页未打开，请检查网络后重试"); }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) { actions.setVisibility(View.VISIBLE); status.setText("官方网页暂时不可用，请稍后重试"); }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                actions.setVisibility(View.VISIBLE);
                status.setText("安全连接验证失败，请检查网络或手机日期");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (web == view) {
                    content.removeView(view);
                    view.destroy();
                    web = null;
                    showCover();
                    actions.setVisibility(View.VISIBLE);
                    status.setText("网页组件已停止，请点重试");
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                String value = message.message();
                if (web != null && isPackageUrl(web.getUrl()) && "__HANDYCODE_PDD_AUTH__:signed-in".equals(value)) {
                    sessionChanged.accept(true);
                    return true;
                }
                if (value == null || !value.startsWith("__HANDYCODE_PDD_LAYOUT__:")) return false;
                if (web == null || !isPackageUrl(web.getUrl())) return true;
                packageNavigation = true;
                syncDark(web);
                String phase = value.substring("__HANDYCODE_PDD_LAYOUT__:".length());
                layoutWorkflow = "source".equals(phase);
                if ("loading".equals(phase) || "cards".equals(phase) || layoutWorkflow) {
                    actions.setVisibility(View.GONE);
                    if (cover.getVisibility() == View.VISIBLE) revealAssembledPage(web);
                }
                else if ("unavailable".equals(phase)) {
                    actions.setVisibility(View.VISIBLE);
                    status.setText("暂未整理官方内容，请点重试");
                }
                return true;
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) { request.deny(); }
        });
        content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl(ENTRY);
    }

    private void prepareLoginClientButton(WebView view, String url) {
        if (web == view && loginClientScript != null && UrlPolicy.isPddLoginPage(url)) {
            view.evaluateJavascript(loginClientScript, null);
        }
    }

    private byte[] adjustedBundle(String address) {
        byte[] cached = cachedAssets.get(address);
        if (cached != null) return cached;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(address).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (connection.getResponseCode() != 200) return null;
            String type = connection.getContentType();
            if (type == null || !(type.contains("javascript") || type.contains("ecmascript"))) return null;
            InputStream raw = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) raw = new GZIPInputStream(raw);
            try (InputStream input = raw; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 6 * 1024 * 1024) return null;
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                String refreshPart = UrlPolicy.pddRefreshPart(address);
                if (refreshPart != null) {
                    String patched = PddBundleAdapter.patch(refreshPart, new String(bytes, StandardCharsets.UTF_8));
                    if (patched == null) return null;
                    bytes = patched.getBytes(StandardCharsets.UTF_8);
                } else {
                    output.reset();
                    output.write(compatibilityScript.getBytes(StandardCharsets.UTF_8));
                    output.write('\n');
                    output.write(bytes);
                    bytes = output.toByteArray();
                }
                if (cachedAssets.size() >= 3) cachedAssets.clear();
                cachedAssets.put(address, bytes);
                return bytes;
            }
        } catch (java.io.IOException unavailable) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void reload() {
        sawLogin = false;
        returnedAfterLogin = false;
        bundleAdjusted = false;
        if (web == null) showPage();
        else web.loadUrl(ENTRY);
    }

    boolean goBack() {
        if (layoutWorkflow && web != null) {
            web.evaluateJavascript("window.HandyPddLayout&&window.HandyPddLayout.back()", null);
            return true;
        }
        if (web == null || !web.canGoBack()) return false;
        web.goBack();
        return true;
    }

    void pollParcels(android.webkit.ValueCallback<String> callback) {
        if (web == null || destroyed || !isPackageUrl(web.getUrl())) {
            callback.onReceiveValue("null");
            return;
        }
        web.evaluateJavascript("(function(){return window.HandyPddLayout&&window.HandyPddLayout.parcelsSnapshot?window.HandyPddLayout.parcelsSnapshot():null;})()", callback);
    }

    void refreshParcels() {
        if (web == null || destroyed || !isPackageUrl(web.getUrl())) return;
        web.evaluateJavascript("(function(){if(window.HandyPddRefresh)window.HandyPddRefresh.run('parcels').catch(function(){});})()", null);
    }

    void scrollFocusedInput() {
        if (web != null) web.evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.matches('input,textarea'))e.scrollIntoView({block:'center'});})()", null);
    }

    void idlePage() { cover.setAnimating(false); }
    void pausePage() { idlePage(); if (web != null) web.onPause(); }
    void resumePage(boolean active) { cover.setAnimating(active); if (web != null) web.onResume(); }
    void resetSessionView() {
        navigation++;
        sessionChanged.accept(false);
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(new WebViewClient());
            content.removeView(web);
            web.destroy();
            web = null;
        }
        pageCommitted = packageNavigation = layoutWorkflow = false;
        sawLogin = returnedAfterLogin = bundleAdjusted = false;
        actions.setVisibility(View.GONE);
        showCover();
        idlePage();
    }
    void destroyPage() {
        destroyed = true;
        stopPresentationWatch();
        cover.setAnimating(false);
        cover.animate().cancel();
        cover.stopMorph();
        if (web != null) { content.removeView(web); web.destroy(); web = null; }
        cachedAssets.clear();
    }
}