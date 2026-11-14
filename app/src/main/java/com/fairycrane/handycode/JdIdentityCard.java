package com.fairycrane.handycode;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.IOException;
import java.util.function.IntSupplier;

/** A live official identity document, measured into the single JD scroll view. */
final class JdIdentityCard extends LinearLayout {
    private final Runnable changed;
    private final IntSupplier availableHeight;
    private final LinearLayout heading;
    private final TextView title, refresh, notice;
    private final View line;
    private final FrameLayout content;
    private final JdPage.Skeleton skeleton;
    private WebView web;
    private String script = "", loginTheme = "", error = "", presentation = "";
    private boolean dark, active, destroyed, inFlight, loginRequired, loginOpen, loginReady, signedIn, loading = true;
    private boolean verification, codeShown;
    private int generation, contentHeight;
    private long deadline, loadingStarted;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || destroyed) return;
            sample();
            removeCallbacks(this);
            postDelayed(this, 400);
        }
    };

    JdIdentityCard(Context context, Runnable changed, IntSupplier availableHeight, JdPage.Skeleton skeleton) {
        super(context);
        this.changed = changed;
        this.availableHeight = availableHeight;
        this.skeleton = skeleton;
        setOrientation(VERTICAL);
        setClipToOutline(true);
        heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), dp(12), dp(14), dp(12));
        title = new TextView(context);
        title.setText("身份码");
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = new TextView(context);
        refresh.setTextSize(12);
        refresh.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        refresh.setMinWidth((int)Math.ceil(refresh.getPaint().measureText("刷新中")));
        refresh.setPadding(0, dp(5), 0, dp(5));
        refresh.setOnClickListener(v -> reload());
        heading.addView(refresh);
        addView(heading);
        line = new View(context);
        addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        content = new FrameLayout(context);
        contentHeight = reservedHeight();
        addView(content, new LinearLayout.LayoutParams(-1, contentHeight));
        notice = new TextView(context);
        notice.setGravity(Gravity.CENTER);
        notice.setTextSize(14);
        notice.setPadding(dp(20), dp(20), dp(20), dp(20));
        notice.setOnClickListener(v -> {
            if (loginRequired) { loginOpen = true; present(); changed.run(); }
            else reload();
        });
        content.addView(skeleton, new FrameLayout.LayoutParams(-1, -1));
        content.addView(notice, new FrameLayout.LayoutParams(-1, -1));
        try {
            script = WebAssets.readText(context.getAssets(), "jd-identity.js");
            loginTheme = WebAssets.readText(context.getAssets(), "login-agreements.js") + "\n"
                + WebAssets.readText(context.getAssets(), "jd-login-theme.js");
        } catch (IOException failure) { error = "身份码页面资源读取失败，请重新安装。"; loading = false; }
        setDark(false);
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private int reservedHeight() {
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth() > 0 ? getWidth() / density + 20 : getResources().getDisplayMetrics().widthPixels / density;
        return Math.round(PickupLoadingView.identityBodyHeight(width) * density);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth && width > 0 && loading && !signedIn && !loginOpen) {
            contentHeight = reservedHeight();
            content.setLayoutParams(new LinearLayout.LayoutParams(-1, contentHeight));
        }
    }
    private static boolean loginUrl(String url) {
        return url != null && "plogin.m.jd.com".equalsIgnoreCase(android.net.Uri.parse(url).getHost());
    }
    boolean wantsLogin() { return loginOpen; }
    boolean isSignedIn() { return signedIn; }
    boolean blocksPageSwipe() { return loginOpen && verification; }

    void setDark(boolean value) {
        dark = value;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(loginOpen ? AppTheme.bg(dark) : AppTheme.surface(dark));
        bg.setCornerRadius(loginOpen ? 0 : dp(16));
        bg.setStroke(loginOpen ? 0 : dp(1), AppTheme.border(dark));
        setBackground(bg);
        title.setTextColor(AppTheme.ink(dark));
        line.setBackgroundColor(AppTheme.divider(dark));
        notice.setTextColor(AppTheme.muted(dark));
        if (web != null) {
            web.setBackgroundColor(loginOpen ? AppTheme.bg(dark) : Color.TRANSPARENT);
            web.evaluateJavascript(themeUpdate(), null);
        }
        present();
    }

    private String themeUpdate() {
        return "window.HandyJdIdentity&&window.HandyJdIdentity.setDark(" + dark + ");"
            + "window.HandyCodeJdLoginTheme&&window.HandyCodeJdLoginTheme.setDark(" + dark + ");";
    }

    void resumePage() {
        if (destroyed) return;
        active = true;
        if (web == null && !script.isEmpty()) createWeb();
        if (web != null) web.onResume();
        removeCallbacks(tick);
        post(tick);
        present();
    }

    private void createWeb() {
        web = new WebView(getContext());
        web.setOverScrollMode(OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        WebView expected = web;
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                if (web != expected) return false;
                String text = message.message(), prefix = "__HANDYCODE_JD_IDENTITY_HEIGHT__:";
                if (JdUrlPolicy.isIdentity(web.getUrl()) && text.startsWith(prefix)) {
                    try { resize(Integer.parseInt(text.substring(prefix.length()))); } catch (NumberFormatException ignored) {}
                    return true;
                }
                if (!loginUrl(web.getUrl())) return false;
                if ("__HANDYCODE_JD_VERIFICATION__:open".equals(text)) verification = true;
                else if ("__HANDYCODE_JD_VERIFICATION__:closed".equals(text)) verification = false;
                else return false;
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !JdUrlPolicy.allowed(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !JdUrlPolicy.allowed(url); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                generation++; inFlight = false; loginReady = false; verification = false;
                if (!JdUrlPolicy.allowed(url)) { view.stopLoading(); fail("已阻止非京东官方地址。"); return; }
                loading = true; error = ""; loadingStarted = SystemClock.elapsedRealtime(); deadline = loadingStarted + 18000;
                present(); sample();
            }
            @Override public void onPageFinished(WebView view, String url) { CookieManager.getInstance().flush(); sample(); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError problem) {
                handler.cancel(); fail("身份码安全连接验证失败，请检查网络或手机日期。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError problem) {
                if (request.isForMainFrame()) fail("身份码暂时无法连接，点此重试。");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (web == view) {
                    generation++; inFlight = false;
                    content.removeView(view); web = null; view.destroy();
                    loginOpen = false; loginReady = false;
                    fail("身份码网页组件已停止，点此重新打开。"); changed.run();
                }
                return true;
            }
        });
        web.setVisibility(INVISIBLE);
        web.setBackgroundColor(Color.TRANSPARENT);
        content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        loadingStarted = SystemClock.elapsedRealtime(); deadline = loadingStarted + 18000;
        web.loadUrl(JdUrlPolicy.IDENTITY);
    }

    private void resize(int cssHeight) {
        if (loginOpen || cssHeight < 120 || cssHeight > 2200) return;
        int next = dp(cssHeight);
        if (contentHeight == next) return;
        contentHeight = next;
        if (!loading) content.setLayoutParams(new LinearLayout.LayoutParams(-1, contentHeight));
    }

    private void sample() {
        final WebView expected = web;
        final int token = generation;
        if (!active || destroyed || expected == null || inFlight) return;
        String url = expected.getUrl();
        if (!JdUrlPolicy.allowed(url)) return;
        if (loginUrl(url)) {
            inFlight = true;
            expected.evaluateJavascript(loginTheme + "\n" + themeUpdate(), ignored -> {
                if (destroyed || web != expected || generation != token) return;
                inFlight = false; signedIn = false; loginRequired = true; loginReady = true; loading = false;
                present(); changed.run();
            });
            return;
        }
        if (JdUrlPolicy.isIdentity(url)) {
            inFlight = true;
            int available = Math.max(180, (int)((availableHeight.getAsInt() - heading.getMeasuredHeight()) / getResources().getDisplayMetrics().density));
            String probe = script + ";\n" + themeUpdate()
                + "window.HandyJdIdentity&&(window.HandyJdIdentity.setAvailable(" + available + "),window.HandyJdIdentity.snapshot());";
            expected.evaluateJavascript(probe, result -> {
                if (destroyed || web != expected || generation != token) return;
                inFlight = false;
                try {
                    JSONObject data = new JSONObject(result);
                    if (!data.optString("href").equals(expected.getUrl())) return;
                    if (data.optBoolean("failed")) { fail("身份码刷新未完成，点此重试。"); return; }
                    if (data.optBoolean("refreshing") && !loading) {
                        loading = true; loadingStarted = SystemClock.elapsedRealtime(); deadline = loadingStarted + 18000; present();
                    }
                    if (data.optBoolean("ready") && !data.optBoolean("refreshing")) {
                        long hold = loading ? Math.max(0, JdPage.MIN_REFRESH_MS - (SystemClock.elapsedRealtime() - loadingStarted)) : 0;
                        if (hold > 0) {
                            inFlight = true;
                            postDelayed(() -> {
                                if (destroyed || web != expected || generation != token) return;
                                inFlight = false; acceptReady(data);
                            }, hold);
                        } else acceptReady(data);
                    }
                } catch (Exception incomplete) { /* Navigation is retried within the deadline. */ }
            });
        }
        if (loading && deadline > 0 && SystemClock.elapsedRealtime() > deadline) fail("身份码暂未加载完成，点此重试。");
    }

    private void acceptReady(JSONObject data) {
        boolean notify = !signedIn || loginOpen;
        signedIn = true; loginRequired = false; loginOpen = false; loading = false; error = "";
        resize(data.optInt("height")); present();
        if (notify) { setDark(dark); changed.run(); }
    }

    private void fail(String message) { generation++; inFlight = false; loading = false; error = message; present(); }

    private void present() {
        boolean showLogin = loginOpen && loginReady;
        boolean showCode = signedIn && !loginRequired && !loading && error.isEmpty();
        String next = loginOpen + ":" + showLogin + ":" + showCode + ":" + loading + ":" + loginRequired + ":" + error + ":" + dark + ":" + active;
        if (next.equals(presentation)) return;
        presentation = next;
        heading.setVisibility(loginOpen ? GONE : VISIBLE);
        line.setVisibility(loginOpen ? GONE : VISIBLE);
        content.setLayoutParams(loginOpen ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(-1, contentHeight));
        refresh.setText(loading ? "刷新中" : "刷新");
        refresh.setEnabled(!loading);
        refresh.setTextColor(loading ? AppTheme.muted(dark) : AppTheme.accentText(dark));
        if (web != null) {
            web.animate().cancel();
            web.setAlpha(showCode && !codeShown && active ? 0f : 1f);
            web.setVisibility(showLogin || showCode ? VISIBLE : INVISIBLE);
            web.setImportantForAccessibility(showLogin || showCode ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (showCode && !codeShown && active) web.animate().alpha(1f).setDuration(200).start();
        }
        codeShown = showCode;
        boolean showNotice = !loading && !showLogin && !showCode;
        notice.setVisibility(showNotice ? VISIBLE : GONE);
        notice.setText(loginRequired ? "请在京东官方页面登录身份码\n点击继续登录" : error);
        skeleton.setVisibility(loading ? VISIBLE : GONE);
        skeleton.setAnimating(active && loading);
    }

    private void reload() {
        if (loading || destroyed) return;
        if (loginRequired) { loginOpen = true; present(); changed.run(); return; }
        loading = true; error = ""; loadingStarted = SystemClock.elapsedRealtime(); deadline = loadingStarted + 18000; present();
        if (web == null) createWeb();
        else if (signedIn && JdUrlPolicy.isIdentity(web.getUrl()))
            web.evaluateJavascript("window.HandyJdIdentity&&window.HandyJdIdentity.refresh();", null);
        else web.loadUrl(JdUrlPolicy.IDENTITY);
    }

    void retryAfterSharedLogin() {
        if (destroyed || !loginRequired || loginOpen || web == null) return;
        loginRequired = false; loading = true; error = "";
        loadingStarted = SystemClock.elapsedRealtime(); deadline = loadingStarted + 18000;
        present(); web.loadUrl(JdUrlPolicy.IDENTITY);
    }

    void checkVisibleScanCode(android.webkit.ValueCallback<String> callback) {
        if (!active || !signedIn || loading || loginOpen || !error.isEmpty() || web == null || !web.isShown() || web.getAlpha() < .95f) {
            callback.onReceiveValue("false"); return;
        }
        Rect visible = new Rect();
        if (!web.getGlobalVisibleRect(visible)) { callback.onReceiveValue("false"); return; }
        int[] position = new int[2]; web.getLocationOnScreen(position);
        float density = getResources().getDisplayMetrics().density;
        float top = (visible.top - position[1]) / density, bottom = (visible.bottom - position[1]) / density;
        web.evaluateJavascript("(function(){var s=window.HandyJdIdentity&&window.HandyJdIdentity.snapshot();"
            + "if(!s||!s.ready)return false;var e=document.querySelector('.hc-jd-barcode'),r=e&&e.getBoundingClientRect();"
            + "return !!r&&r.width>40&&r.height>20&&r.left>=0&&r.right<=innerWidth&&r.top>=" + top + "&&r.bottom<=" + bottom + ";})()", callback);
    }

    void scrollFocusedInput() {
        if (loginOpen && web != null) web.evaluateJavascript("document.activeElement&&document.activeElement.scrollIntoView({block:'center'})",null);
    }

    boolean goBack() {
        if (!loginOpen) return false;
        if (web != null && web.canGoBack() && !loginUrl(web.getUrl())) web.goBack();
        else { loginOpen = false; present(); changed.run(); }
        return true;
    }

    void pausePage() { active = false; removeCallbacks(tick); skeleton.setAnimating(false); if (web != null) web.onPause(); }
    void resetSessionView() {
        pausePage(); generation++; inFlight = false; signedIn = false; loginRequired = false; loginOpen = false; loginReady = false; loading = true; error = "";
        if (web != null) { web.stopLoading(); web.setWebViewClient(new WebViewClient()); content.removeView(web); web.destroy(); web = null; }
        contentHeight = reservedHeight(); presentation = ""; codeShown = false; present();
    }
    void destroyPage() { destroyed = true; resetSessionView(); }
}
