package com.fairycrane.handycode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.SystemClock;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
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
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.function.Consumer;

/** Official JD login and order capture, presented in the shared pickup-card style. */
final class JdPage extends FrameLayout {
    private static final String ORDERS_BUSY = "orders_busy";
    private final Consumer<Boolean> session;
    private final ScrollView results;
    private final LinearLayout card, body, heading;
    private final TextView title, refresh;
    private final View divider;
    private final Skeleton skeleton;
    private WebView web;
    private boolean dark, paused = true, destroyed, polling, inFlight, loading = true;
    private boolean loginVisible;
    private int generation;
    private long lastProbe, deadline;
    private String script = "", lastSnapshot = "", error = "";
    private final Runnable tick = () -> {
        polling = false;
        if (paused || destroyed || web == null) return;
        sample();
        schedule();
    };
    private final Runnable timeout = () -> {
        if (!paused && loading) fail("读取订单超时，请稍后重试。");
    };

    JdPage(Context context, Consumer<Boolean> session) {
        super(context);
        this.session = session;
        results = new ScrollView(context);
        results.setClipToPadding(false);
        results.setPadding(dp(10), dp(12), dp(10), dp(24));
        results.setFillViewport(false);
        addView(results, new FrameLayout.LayoutParams(-1, -1));
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipToOutline(true);
        results.addView(card, new ScrollView.LayoutParams(-1, -2));
        heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), dp(12), dp(14), dp(12));
        title = text("待取包裹", 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = action("刷新", this::reload);
        heading.addView(refresh);
        card.addView(heading);
        divider = new View(context);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.setMinimumHeight(dp(140));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        skeleton = new Skeleton(context);
        try {
            script = WebAssets.readText(context.getAssets(), "jd-orders.js");
        } catch (IOException unavailable) {
            loading = false;
            error = "京东页面资源读取失败，请重新安装。";
        }
        setDark(AppTheme.isDark(context, AppTheme.MODE_SYSTEM));
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String value, int size) {
        TextView label = new TextView(getContext());
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(AppTheme.ink(dark));
        label.setFontFeatureSettings("tnum");
        return label;
    }

    private TextView action(String label, Runnable run) {
        TextView button = text(label, 12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, dp(5), 0, dp(5));
        button.setOnClickListener(view -> run.run());
        return button;
    }

    void setDark(boolean value) {
        dark = value;
        setBackgroundColor(AppTheme.bg(dark));
        results.setBackgroundColor(AppTheme.bg(dark));
        GradientDrawable background = new GradientDrawable();
        background.setColor(AppTheme.surface(dark));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), AppTheme.border(dark));
        card.setBackground(background);
        title.setTextColor(AppTheme.ink(dark));
        divider.setBackgroundColor(AppTheme.divider(dark));
        if (web != null) web.setBackgroundColor(AppTheme.bg(dark));
        renderBody(false);
    }

    void showPage() {
        if (destroyed) return;
        paused = false;
        if (web == null) createWeb();
        if (web != null) {
            web.onResume();
            sample();
            schedule();
            if (loading) armTimeout();
        }
        updatePresentation();
    }

    private void createWeb() {
        if (destroyed || script.isEmpty()) return;
        web = new WebView(getContext());
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
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !JdUrlPolicy.allowed(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !JdUrlPolicy.allowed(url);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                generation++;
                inFlight = false;
                lastProbe = 0;
                if (!JdUrlPolicy.allowed(url)) {
                    view.stopLoading();
                    fail("已阻止非京东官方地址。");
                    return;
                }
                beginLoading();
                sample();
            }
            @Override public void onLoadResource(WebView view, String url) {
                if (!paused && SystemClock.elapsedRealtime() - lastProbe >= 250) sample();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!JdUrlPolicy.allowed(url)) return;
                CookieManager.getInstance().flush();
                sample();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError problem) {
                handler.cancel();
                fail("京东安全连接验证失败，请检查网络或手机日期后重试。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError problem) {
                if (request.isForMainFrame()) fail("京东页面暂时无法连接，请重试。");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (web == view) {
                    generation++;
                    inFlight = false;
                    removeView(view);
                    web = null;
                    view.destroy();
                    session.accept(false);
                    loginVisible = false;
                    fail("京东网页组件已停止，请点刷新重新打开。");
                }
                return true;
            }
        });
        web.setVisibility(INVISIBLE);
        web.setBackgroundColor(AppTheme.bg(dark));
        addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        beginLoading();
        web.loadUrl(JdUrlPolicy.ORDERS);
    }

    private void schedule() {
        if (!polling && !paused && !destroyed) {
            polling = true;
            postDelayed(tick, 1000);
        }
    }

    private void armTimeout() {
        removeCallbacks(timeout);
        postDelayed(timeout, Math.max(1, deadline - SystemClock.elapsedRealtime()));
    }

    private void beginLoading() {
        loading = true;
        loginVisible = false;
        error = "";
        deadline = SystemClock.elapsedRealtime() + 18000;
        if (!paused) armTimeout();
        renderBody(false);
        updatePresentation();
    }

    private void sample() {
        final WebView expected = web;
        final int token = generation;
        if (expected == null || paused || destroyed || inFlight || !JdUrlPolicy.allowed(expected.getUrl())) return;
        inFlight = true;
        lastProbe = SystemClock.elapsedRealtime();
        expected.evaluateJavascript(script, result -> {
            if (destroyed || web != expected || generation != token) return;
            inFlight = false;
            try {
                JSONObject data = new JSONObject(result);
                if (!data.optString("href").equals(expected.getUrl())) return;
                if (data.optBoolean("login")) {
                    session.accept(false);
                    loginVisible = true;
                    lastSnapshot = "";
                    loading = false;
                    removeCallbacks(timeout);
                    updatePresentation();
                    return;
                }
                String problem = data.optString("problem");
                if (!problem.isEmpty()) {
                    String message = "busy".equals(problem) ? ORDERS_BUSY : "京东官方订单页暂时无法加载，请稍后重试。";
                    if (loading || !message.equals(error)) fail(message);
                    return;
                }
                if (!data.optBoolean("ready")) return;
                session.accept(true);
                String next = data.toString();
                boolean changed = !next.equals(lastSnapshot) || loading || loginVisible || !error.isEmpty();
                lastSnapshot = next;
                loading = false;
                loginVisible = false;
                error = "";
                removeCallbacks(timeout);
                if (changed) renderBody(true);
                updatePresentation();
            } catch (JSONException ignored) {
                // Incomplete navigation is sampled again until the bounded timeout.
            }
        });
    }

    private void fail(String message) {
        loading = false;
        loginVisible = false;
        error = message;
        removeCallbacks(timeout);
        renderBody(true);
        updatePresentation();
    }

    private void updatePresentation() {
        boolean showWeb = web != null && loginVisible;
        if (web != null) {
            web.setVisibility(showWeb ? VISIBLE : INVISIBLE);
            web.setImportantForAccessibility(showWeb ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        results.setVisibility(showWeb ? GONE : VISIBLE);
        skeleton.setAnimating(loading && !paused && !showWeb);
    }

    private void renderBody(boolean animate) {
        TransitionManager.endTransitions(card);
        if (animate && !paused && results.isShown() && ValueAnimator.areAnimatorsEnabled()) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220);
            transition.excludeTarget(skeleton, true);
            TransitionManager.beginDelayedTransition(card, transition);
        }
        int oldHeight = body.getHeight();
        body.removeAllViews();
        body.setMinimumHeight(loading ? Math.max(dp(140), oldHeight) : dp(140));
        body.setGravity(loading ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        refresh.setText(loading ? "刷新中" : "刷新");
        refresh.setEnabled(!loading);
        refresh.setTextColor(loading ? AppTheme.muted(dark) : AppTheme.accentText(dark));
        if (loading) {
            int availableHeight = oldHeight - body.getPaddingTop() - body.getPaddingBottom();
            body.addView(skeleton, new LinearLayout.LayoutParams(-1, Math.max(dp(108), availableHeight)));
            skeleton.invalidate();
            return;
        }
        JSONArray rows = null;
        try { rows = new JSONObject(lastSnapshot).optJSONArray("orders"); } catch (JSONException ignored) {}
        int count = 0;
        boolean busy = ORDERS_BUSY.equals(error);
        if (rows != null) for (int index = 0; index < rows.length(); index++) {
            JSONObject order = rows.optJSONObject(index);
            if (order == null || order.optString("code").isEmpty()) continue;
            if (count++ > 0) {
                View gap = new View(getContext());
                body.addView(gap, new LinearLayout.LayoutParams(-1, dp(22)));
            }
            TextView name = text(order.optString("name", "京东订单"), 13);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            body.addView(name);
            TextView code = text(order.optString("code"), 26);
            code.setPadding(0, dp(8), 0, dp(4));
            code.setTextIsSelectable(true);
            body.addView(code);
            String detail = order.optString("status");
            if (!order.optString("detail").isEmpty()) detail += (detail.isEmpty() ? "" : "\n") + order.optString("detail");
            TextView description = text(detail, 12);
            description.setTextColor(AppTheme.muted(dark));
            description.setLineSpacing(dp(3), 1);
            body.addView(description);
        }
        if (count == 0) {
            body.setGravity(Gravity.CENTER);
            boolean hasSnapshot = !lastSnapshot.isEmpty();
            TextView empty = text(busy || hasSnapshot ? "暂无待取包裹" : "取件信息暂未更新", 14);
            empty.setGravity(Gravity.CENTER);
            body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            String explanation = error;
            if (hasSnapshot) explanation = error.isEmpty() ? "包裹到站后，可在这里查看取件码。"
                : "上次查询暂无待取包裹，本次刷新未完成，请稍后重试。";
            if (!busy) {
                TextView detail = text(explanation, 12);
                detail.setPadding(0, dp(8), 0, 0);
                detail.setGravity(Gravity.CENTER);
                detail.setTextColor(AppTheme.muted(dark));
                body.addView(detail, new LinearLayout.LayoutParams(-1, -2));
            }
        } else if (!error.isEmpty() && !busy) {
            TextView warning = text(error, 12);
            warning.setPadding(0, dp(12), 0, 0);
            warning.setTextColor(AppTheme.muted(dark));
            body.addView(warning);
        }
    }

    private void reload() {
        if (loading || destroyed) return;
        if (web == null) { showPage(); return; }
        generation++;
        inFlight = false;
        beginLoading();
        web.loadUrl(JdUrlPolicy.ORDERS);
    }

    void resetSessionView() {
        pausePage();
        generation++;
        inFlight = false;
        lastSnapshot = "";
        loginVisible = false;
        error = "";
        loading = true;
        if (web != null) {
            web.stopLoading();
            web.setWebViewClient(new WebViewClient());
            removeView(web);
            web.destroy();
            web = null;
        }
        renderBody(false);
        updatePresentation();
    }

    void pausePage() {
        paused = true;
        removeCallbacks(tick);
        removeCallbacks(timeout);
        polling = false;
        skeleton.setAnimating(false);
        if (web != null) web.onPause();
    }

    void resumePage(boolean active) { if (active) showPage(); else pausePage(); }

    void scrollFocusedInput() {
        if (web != null && loginVisible) web.evaluateJavascript(
            "(function(){var e=document.activeElement;if(e&&e.matches('input,textarea'))e.scrollIntoView({block:'center'});})()", null);
    }

    boolean goBack() {
        if (loginVisible && web != null && web.canGoBack()) { web.goBack(); return true; }
        return false;
    }

    void destroyPage() {
        destroyed = true;
        resetSessionView();
    }

    private final class Skeleton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean animating;
        private long started;

        Skeleton(Context context) { super(context); setContentDescription("正在准备取件信息"); }

        void setAnimating(boolean value) {
            value = value && ValueAnimator.areAnimatorsEnabled();
            if (animating == value) return;
            animating = value;
            started = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), center = getHeight() / 2f;
            bar(canvas, width * .68f, center - dp(48), dp(14));
            bar(canvas, width, center - dp(24), dp(54));
            bar(canvas, width * .68f, center + dp(40), dp(14));
            if (animating && isShown()) postInvalidateOnAnimation();
        }

        private void bar(Canvas canvas, float width, float top, float height) {
            float left = (getWidth() - width) / 2;
            RectF bounds = new RectF(left, top, left + width, top + height);
            paint.setShader(null);
            paint.setColor(AppTheme.shimmerBase(dark));
            canvas.drawRoundRect(bounds, dp(7), dp(7), paint);
            float progress = animating ? ((SystemClock.uptimeMillis() - started) % 1400L) / 1400f : 0f;
            float center = left - width * .4f + progress * width * 1.8f;
            paint.setShader(new LinearGradient(center - width * .35f, 0, center + width * .35f, 0,
                new int[]{0x00ffffff, AppTheme.shimmerPeak(dark), 0x00ffffff}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bounds, dp(7), dp(7), paint);
            paint.setShader(null);
        }
    }
}
