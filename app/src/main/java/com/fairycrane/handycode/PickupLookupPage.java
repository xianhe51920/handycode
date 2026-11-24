package com.fairycrane.handycode;

import android.app.AlertDialog;
import android.content.Context;
import android.net.http.SslError;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;

/** Hosts only the official JD Express and SF collection and tracking pages. */
final class PickupLookupPage extends LinearLayout {
    enum Entry {
        JD_EXPRESS("京东快递收件", "https://www.jdl.com/search/orderList/",
            "jdl-orders.js", PickupSummary.Source.JD_EXPRESS, "jdl.com"),
        JD_TRACKING("京东运单查询", "https://www.jdl.com/orderSearch/", null, null, null),
        SF_ORDERS("顺丰收件", "https://www.sf-express.com/chn/sc/waybill/list",
            "sf-orders.js", PickupSummary.Source.SF, "sf-express.com"),
        SF_TRACKING("顺丰运单查询",
            "https://www.sf-express.com/mobile/cn/sc/dynamic_functions/waybill/search?noHead=true&noFooter=true",
            null, null, null);

        final String label, url, asset, site;
        final PickupSummary.Source source;

        Entry(String label, String url, String asset, PickupSummary.Source source, String site) {
            this.label = label;
            this.url = url;
            this.asset = asset;
            this.source = source;
            this.site = site;
        }

        boolean allows(String address) {
            URI uri = UrlPolicy.httpsUri(address);
            if (uri == null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (this == SF_ORDERS || this == SF_TRACKING) {
                return host.equals("sf-express.com") || host.endsWith(".sf-express.com");
            }
            if (this == JD_TRACKING) {
                return host.equals("www.jdl.com") || host.equals("jdl.com")
                    || host.equals("www.jdl.cn") || host.equals("jdl.cn");
            }
            return host.equals("www.jdl.com") || host.equals("i.jdl.com")
                || host.equals("sso.jdl.com") || host.equals("passport.jdl.com")
                || host.equals("plogin.m.jd.com") || host.equals("passport.jd.com");
        }
    }

    private final Entry entry;
    private final Consumer<JSONArray> onOrders;
    private final TextView status;
    private final Button refresh, logout;
    private final FrameLayout body;
    private WebView web;
    private String script = "";
    private boolean active, committed, inFlight, resetting, destroyed;
    private int generation;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || destroyed || web == null || script.isEmpty()) return;
            sample();
            postDelayed(this, 850);
        }
    };

    PickupLookupPage(Context context, Entry entry, Runnable back, Consumer<JSONArray> onOrders) {
        super(context);
        this.entry = entry;
        this.onOrders = onOrders;
        setOrientation(VERTICAL);

        status = new TextView(context);
        status.setPadding(dp(12), dp(8), dp(12), 0);
        addView(status);
        LinearLayout controls = new LinearLayout(context);
        controls.setPadding(dp(8), 0, dp(8), 0);
        addView(controls, new LayoutParams(-1, -2));
        controls.addView(button("返回汇总", back), new LayoutParams(0, -2, 1f));
        refresh = button("刷新网页", this::open);
        controls.addView(refresh, new LayoutParams(0, -2, 1f));
        logout = button("退出登录", this::confirmLogout);
        controls.addView(logout, new LayoutParams(0, -2, 1f));
        logout.setVisibility(GONE);

        body = new FrameLayout(context);
        addView(body, new LayoutParams(-1, 0, 1f));
        if (entry.asset != null) {
            try {
                String reader = WebAssets.readText(context.getAssets(), entry.asset);
                if (entry == Entry.SF_ORDERS) {
                    reader = WebAssets.readText(context.getAssets(), "sf-mobile-layout.js") + "\n" + reader;
                }
                script = reader;
            } catch (IOException ignored) {
                status.setText("无法读取取件码资源");
            }
        }
        open();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Button button(String label, Runnable click) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    void setDark(boolean dark) {
        setBackgroundColor(AppTheme.bg(dark));
        status.setTextColor(AppTheme.ink(dark));
    }

    private void open() {
        if (destroyed || resetting) return;
        if (web == null) {
            web = new WebView(getContext());
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSafeBrowsingEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            });
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return request.isForMainFrame() && !entry.allows(request.getUrl().toString());
                }

                @Override public boolean shouldOverrideUrlLoading(WebView view, String address) {
                    return !entry.allows(address);
                }

                @Override public void onPageStarted(WebView view, String address, android.graphics.Bitmap icon) {
                    generation++;
                    committed = inFlight = false;
                    if (!entry.allows(address)) view.stopLoading();
                }

                @Override public void onPageCommitVisible(WebView view, String address) {
                    committed = entry.allows(address);
                    if (committed) sample();
                }

                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        committed = false;
                        status.setText(entry.label + "加载失败，请重试");
                    }
                }

                @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.cancel();
                }

                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    if (view == web) {
                        disposeWeb();
                        status.setText("网页停止运行，点击刷新重试");
                    }
                    return true;
                }
            });
            body.addView(web, new FrameLayout.LayoutParams(-1, -1));
        }
        if (entry.source == null) status.setText(entry.label + " · 输入运单号查看物流");
        else if (script.isEmpty()) status.setText(entry.label + " · 取件码读取资源不可用");
        else status.setText(entry.label + " · 在官方页面登录或查看收件");
        generation++;
        committed = inFlight = false;
        web.loadUrl(entry.url);
        setActive(active);
    }

    private void sample() {
        WebView expected = web;
        if (expected == null || !active || !committed || inFlight || resetting || script.isEmpty()) return;
        String page = expected.getUrl();
        if (!entry.allows(page)) return;
        int token = generation;
        inFlight = true;
        expected.evaluateJavascript(script, result -> {
            if (destroyed || expected != web || generation != token) return;
            inFlight = false;
            if (!active) return;
            try {
                JSONObject data = new JSONObject(result);
                if (!data.optString("href").equals(expected.getUrl())) return;
                if (data.optBoolean("login")) {
                    onOrders.accept(null);
                    logout.setVisibility(GONE);
                    status.setText(entry.label + " · 请在官方页面登录");
                } else if (data.optBoolean("ready")) {
                    onOrders.accept(data.optJSONArray("orders"));
                    logout.setVisibility(VISIBLE);
                    status.setText(entry.label + " · 已更新取件码");
                } else if (data.optBoolean("authenticated")) {
                    logout.setVisibility(VISIBLE);
                    status.setText(entry.label + " · 已登录，正在读取收件记录");
                }
            } catch (JSONException ignored) {
                // The official page may still be loading.
            }
        });
    }

    private void confirmLogout() {
        if (resetting || entry.site == null) return;
        if (!SiteDataClearer.isSupported()) {
            status.setText("请更新系统 WebView 后再退出登录");
            return;
        }
        new AlertDialog.Builder(getContext()).setTitle("退出" + entry.label + "登录")
            .setMessage("清除此平台在本应用中的网页登录和取件码。")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出", (dialog, which) -> {
                resetting = true;
                refresh.setEnabled(false);
                logout.setVisibility(GONE);
                onOrders.accept(null);
                disposeWeb();
                SiteDataClearer.clearSites(new String[] {entry.site}, () -> finishLogout(true),
                    error -> finishLogout(false));
            }).show();
    }

    private void finishLogout(boolean cleared) {
        if (destroyed) return;
        resetting = false;
        refresh.setEnabled(true);
        if (cleared) open();
        else status.setText("暂时无法清除登录状态，请稍后重试");
    }

    void setActive(boolean active) {
        this.active = active;
        removeCallbacks(tick);
        if (web == null) return;
        if (active) {
            web.onResume();
            post(tick);
        } else web.onPause();
    }

    boolean goBack() {
        if (web == null || !web.canGoBack()) return false;
        web.goBack();
        return true;
    }

    private void disposeWeb() {
        generation++;
        removeCallbacks(tick);
        committed = inFlight = false;
        if (web == null) return;
        web.stopLoading();
        body.removeView(web);
        web.destroy();
        web = null;
    }

    void destroyPage() {
        destroyed = true;
        disposeWeb();
    }
}
