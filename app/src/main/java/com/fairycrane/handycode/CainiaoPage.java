package com.fairycrane.handycode;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import org.json.JSONObject;

/** Keeps official authorization and the live barcode in the shared Taobao WebView store. */
final class CainiaoPage extends FrameLayout {
    private static final String PATH = "/cn-app-web/identity-code/index.html";
    private static final String ENTRY = "https://page.cainiao.com" + PATH + "#/";
    private final PickupLoadingView cover;
    private final LinearLayout errorPanel;
    private final TextView status;
    private final Button retry;
    private final ScrollView loginPanel;
    private final TextView loginHint;
    private final Button loginAction;
    private final String bootstrap;
    private final Runnable onLoginRequired;
    private final Runnable poll = this::inspect;
    private WebView web;
    private boolean dark, active, checking, installed, committed;
    private long generation, deadline, phoneSince;
    private String phase = "loading";
    private float geometryWidth, geometryTop, geometryBody, geometryHeading;
    private boolean geometryExpanded, restoringGeometry, refreshExpanded;

    CainiaoPage(Context context, Runnable openTaobao, Runnable onLoginRequired) {
        super(context);
        this.onLoginRequired = onLoginRequired;
        cover = new PickupLoadingView(context, false, true);
        addView(cover, new LayoutParams(-1, -1));
        errorPanel = new LinearLayout(context);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(24), 0, dp(24), 0);
        errorPanel.setClickable(true);
        status = new TextView(context);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14);
        errorPanel.addView(status);
        retry = new Button(context);
        retry.setText("重新打开网页");
        retry.setOnClickListener(view -> reload());
        errorPanel.addView(retry);
        errorPanel.setVisibility(GONE);
        addView(errorPanel, new LayoutParams(-1, -1));

        loginPanel = new ScrollView(context);
        loginPanel.setFillViewport(true);
        loginPanel.setVerticalScrollBarEnabled(false);
        loginPanel.setVisibility(GONE);
        LinearLayout loginContent = new LinearLayout(context);
        loginContent.setOrientation(LinearLayout.VERTICAL);
        loginContent.setGravity(Gravity.CENTER);
        loginContent.setPadding(dp(24), dp(28), dp(24), dp(28));
        loginPanel.addView(loginContent, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ic_cainiao_official);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loginContent.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

        loginHint = new TextView(context);
        loginHint.setText("如果需要使用菜鸟身份码，\n请先登录淘宝");
        loginHint.setGravity(Gravity.CENTER);
        loginHint.setTextSize(16);
        loginHint.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(24);
        loginContent.addView(loginHint, hintParams);

        loginAction = new Button(context);
        loginAction.setText("前往淘宝登录");
        loginAction.setTextSize(16);
        loginAction.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        loginAction.setTextColor(Color.WHITE);
        loginAction.setMinHeight(dp(48));
        loginAction.setMinimumHeight(dp(48));
        loginAction.setPadding(dp(16), dp(12), dp(16), dp(12));
        loginAction.setStateListAnimator(null);
        loginAction.setOnClickListener(view -> openTaobao.run());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(24);
        loginContent.addView(loginAction, actionParams);
        addView(loginPanel, new LayoutParams(-1, -1));

        String script = "";
        try {
            AssetManager assets = context.getAssets();
            script = WebAssets.readText(assets, "official-identity.js") + "\n"
                + WebAssets.readText(assets, "pickup-motion.js") + "\n"
                + WebAssets.readText(assets, "cainiao-layout.js");
        } catch (IOException unavailable) {
            fail("菜鸟页面资源读取失败，请重新安装。");
        }
        bootstrap = script;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private static boolean isEntry(String url) {
        if (!UrlPolicy.isAllowed(url)) return false;
        Uri uri = Uri.parse(url);
        return "page.cainiao.com".equals(uri.getHost()) && PATH.equals(uri.getPath());
    }

    private static boolean isLoginPage(String url) {
        if (!UrlPolicy.isAllowed(url)) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("login.") || host.startsWith("passport.")) return true;
        String path = uri.getPath();
        return path != null && path.matches(".*/login(?:[./].*)?");
    }

    boolean isLoginRequired() { return "login-required".equals(phase); }

    void setLoginRequired(boolean required) {
        if (required == isLoginRequired()) return;
        if (required) {
            boolean wasActive = active;
            resetSessionView();
            active = wasActive;
            phase = "login-required";
            cover.setVisibility(GONE);
            loginPanel.setVisibility(VISIBLE);
        } else {
            loading();
            if (active) showPage();
        }
    }

    void setDark(boolean value) {
        dark = value;
        setBackgroundColor(AppTheme.bg(dark));
        cover.setDark(dark);
        errorPanel.setBackgroundColor(AppTheme.bg(dark));
        status.setTextColor(AppTheme.ink(dark));
        retry.setTextColor(AppTheme.accentText(dark));
        loginPanel.setBackgroundColor(AppTheme.bg(dark));
        loginHint.setTextColor(AppTheme.ink(dark));
        GradientDrawable buttonFill = new GradientDrawable();
        buttonFill.setCornerRadius(dp(8));
        buttonFill.setColor(dark ? 0xff1b71df : 0xff0668de);
        loginAction.setBackground(new RippleDrawable(ColorStateList.valueOf(0x26ffffff), buttonFill, null));
        if (web != null) web.setBackgroundColor(AppTheme.bg(dark));
        if (active) inspect();
    }

    void showPage() {
        active = true;
        if (isLoginRequired()) return;
        if (bootstrap.isEmpty()) {
            fail("菜鸟页面资源读取失败，请重新安装。");
            return;
        }
        if (web == null) createWeb();
        if (web != null) web.onResume();
        cover.setAnimating(cover.getVisibility() == VISIBLE);
        inspect();
    }

    private void createWeb() {
        web = new WebView(getContext());
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(AppTheme.bg(dark));
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String prefix = "__HANDYCODE_CAINIAO_GEOMETRY__:";
                if (!isEntry(web == null ? null : web.getUrl()) || !message.message().startsWith(prefix)) return false;
                try {
                    JSONObject data = new JSONObject(message.message().substring(prefix.length()));
                    float width = (float)data.getDouble("width"), top = (float)data.getDouble("top");
                    float body = (float)data.getDouble("body"), heading = (float)data.getDouble("heading");
                    if (width >= 240 && width <= 2000 && top >= -10000 && top <= 10000
                            && body >= 32 && body <= 10000 && heading >= 20 && heading <= 200) {
                        boolean expanded = data.optBoolean("expanded");
                        if (data.optBoolean("refresh")) { refreshExpanded = expanded; restoringGeometry = true; }
                        if (restoringGeometry && expanded != refreshExpanded) return true;
                        geometryWidth = width; geometryTop = top; geometryBody = body; geometryHeading = heading;
                        geometryExpanded = expanded;
                        if ("ready".equals(phase) && cover.getVisibility() != VISIBLE)
                            cover.matchIdentityGeometry(width, top, body, heading);
                        if (data.optBoolean("refresh")) loading();
                    }
                } catch (Exception ignored) { /* Only dimensions and the zoom flag are accepted. */ }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !UrlPolicy.isAllowed(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !UrlPolicy.isAllowed(url);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                generation++;
                checking = installed = committed = false;
                phoneSince = 0;
                if (!UrlPolicy.isAllowed(url)) {
                    view.stopLoading();
                    fail("网页尝试打开其他地址，请重新打开菜鸟页面。");
                    return;
                }
                loading();
                schedule(350);
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                if (view == web && url.equals(view.getUrl())) { committed = true; inspect(); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (view == web && url.equals(view.getUrl())) { committed = true; inspect(); }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                fail("菜鸟安全连接验证失败，请检查网络后重试。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail("菜鸟网页暂时无法连接，请重试。");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (view == web) {
                    resetSessionView();
                    fail("菜鸟网页组件已停止，请重新打开网页。");
                }
                return true;
            }
        });
        addView(web, 0, new LayoutParams(-1, -1));
        loading();
        web.loadUrl(ENTRY);
    }

    private void schedule(long delay) {
        removeCallbacks(poll);
        if (active && web != null) postDelayed(poll, delay);
    }

    private void inspect() {
        removeCallbacks(poll);
        if (!active || web == null || checking) return;
        if (!committed) { waiting(); return; }
        if (isLoginPage(web.getUrl())) {
            onLoginRequired.run();
            return;
        }
        if (!isEntry(web.getUrl())) {
            if (UrlPolicy.isAllowed(web.getUrl()) && !"error".equals(phase)) reveal("workflow");
            return;
        }
        WebView expected = web;
        long token = generation;
        String update = "window.HandyCodeCainiaoLayout?HandyCodeCainiaoLayout.update(" + dark + "," + geometryExpanded + "):'install'";
        checking = true;
        web.evaluateJavascript(installed ? update : bootstrap + "\n;" + update, value -> {
            if (expected != web || token != generation) return;
            checking = false;
            installed = !"\"install\"".equals(value);
            if (!active) return;
            if ("\"login\"".equals(value)) {
                onLoginRequired.run();
            } else if ("\"ready\"".equals(value) || "\"workflow\"".equals(value)) {
                phoneSince = 0;
                boolean ready = "\"ready\"".equals(value);
                reveal(ready ? "ready" : "workflow");
                schedule(ready ? 700 : 350);
            } else if ("\"phone\"".equals(value)) {
                long now = SystemClock.elapsedRealtime();
                if (phoneSince == 0) phoneSince = now;
                if (now - phoneSince >= 1000) reveal("workflow");
                schedule(350);
            } else {
                phoneSince = 0;
                waiting();
            }
        });
    }

    private void waiting() {
        if (!"loading".equals(phase) && !"error".equals(phase)) loading();
        if ("loading".equals(phase) && SystemClock.elapsedRealtime() >= deadline)
            fail("菜鸟身份码暂未准备好，请重试。");
        schedule("error".equals(phase) ? 1200 : 350);
    }

    private void loading() {
        cover.animate().cancel();
        cover.stopMorph();
        cover.setAlpha(1f);
        if (geometryWidth > 0) cover.matchIdentityGeometry(geometryWidth, geometryTop, geometryBody, geometryHeading);
        phase = "loading";
        deadline = SystemClock.elapsedRealtime() + 18000;
        errorPanel.setVisibility(GONE);
        loginPanel.setVisibility(GONE);
        cover.setVisibility(VISIBLE);
        cover.setAnimating(active);
        if (web != null) web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void reveal(String next) {
        if (next.equals(phase)) return;
        phase = next;
        WebView expected = web;
        long token = generation;
        if ("ready".equals(next) && restoringGeometry) {
            web.evaluateJavascript("window.HandyCodeCainiaoLayout&&HandyCodeCainiaoLayout.restoreZoom(" + refreshExpanded + ")", ignored -> {
                if (web == expected && generation == token && next.equals(phase)) revealPrepared(expected, token, next);
            });
        } else revealPrepared(expected, token, next);
    }

    private void revealPrepared(WebView expected, long token, String next) {
        expected.postVisualStateCallback(token, new WebView.VisualStateCallback() {
            @Override public void onComplete(long requestId) {
                if (web != expected || generation != token || !next.equals(phase)) return;
                errorPanel.setVisibility(GONE);
                expected.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                if ("ready".equals(next) && geometryWidth > 0 && cover.getVisibility() == VISIBLE) {
                    cover.morphIdentityTo(geometryWidth, geometryTop, geometryBody, geometryHeading, () -> {
                     if (web != expected || generation != token || !next.equals(phase)) return;
                     cover.animate().alpha(0f).setDuration(PickupLoadingView.FADE_DURATION).withEndAction(() -> {
                        if (web != expected || generation != token || !next.equals(phase)) return;
                        cover.setAnimating(false);
                        cover.setVisibility(GONE);
                        cover.setAlpha(1f);
                        restoringGeometry = false;
                    }).start();
                    });
                } else {
                    cover.setAnimating(false);
                    cover.setVisibility(GONE);
                    restoringGeometry = false;
                }
            }
        });
    }

    private void fail(String message) {
        cover.animate().cancel();
        cover.stopMorph();
        phase = "error";
        status.setText(message);
        loginPanel.setVisibility(GONE);
        cover.setAnimating(false);
        cover.setVisibility(GONE);
        errorPanel.setVisibility(VISIBLE);
        if (web != null) web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void reload() {
        if (bootstrap.isEmpty()) return;
        if (web == null) { showPage(); return; }
        generation++;
        committed = installed = checking = false;
        loading();
        web.loadUrl(ENTRY);
        schedule(350);
    }

    void pausePage() {
        active = false;
        removeCallbacks(poll);
        cover.setAnimating(false);
        if (web != null) web.onPause();
    }

    void resetSessionView() {
        pausePage();
        generation++;
        checking = installed = committed = false;
        phoneSince = 0;
        geometryWidth = geometryTop = geometryBody = geometryHeading = 0;
        geometryExpanded = false;
        restoringGeometry = false;
        cover.resetGeometry();
        if (web != null) {
            WebView closed = web;
            web = null;
            closed.stopLoading();
            closed.setWebViewClient(new WebViewClient());
            closed.setWebChromeClient(null);
            removeView(closed);
            closed.destroy();
        }
        loading();
    }

    boolean goBack() {
        if (web == null || !"workflow".equals(phase) || !web.canGoBack()) return false;
        web.goBack();
        return true;
    }

    void scrollFocusedInput() {
        if (web != null && "workflow".equals(phase)) web.evaluateJavascript(
            "(function(){var e=document.activeElement;if(e&&e.matches('input,textarea'))e.scrollIntoView({block:'center'});})()", null);
    }

    void checkVisibleScanCode(ValueCallback<String> callback) {
        if (web == null || !"ready".equals(phase) || cover.getVisibility() == VISIBLE
                || errorPanel.getVisibility() == VISIBLE) callback.onReceiveValue("false");
        else web.evaluateJavascript(ScanCodeVisibility.SCRIPT, callback);
    }
}
