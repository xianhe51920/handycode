package com.fairycrane.handycode.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.fairycrane.handycode.SiteDataClearer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Only reserved synthetic origins: never reads or clears real platform data. */
public final class LogoutProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static final String TARGET = "https://logout-target.invalid/";
    private static final String OTHER = "https://logout-other.invalid/";
    private static final String[] SITES = {"logout-target.invalid", "logout-other.invalid"};
    private final WebView[] views = new WebView[2];
    private int checks;
    private void waitFor(CountDownLatch latch) throws Exception {
        if (!latch.await(12, TimeUnit.SECONDS)) throw new AssertionError("Fixture callback timeout");
    }
    private void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private String eval(WebView web, String js) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {null};
        runOnMainSync(() -> web.evaluateJavascript(js, value -> { result[0] = value; latch.countDown(); }));
        waitFor(latch); return result[0];
    }
    private void clear(String[] sites) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] failed = {false};
        runOnMainSync(() -> SiteDataClearer.clearSites(sites, latch::countDown, error -> { failed[0] = true; latch.countDown(); }));
        waitFor(latch); check(!failed[0], "Site deletion callback");
    }
    private void fixture(int index, String origin) throws Exception {
        CountDownLatch loaded = new CountDownLatch(1), cookie = new CountDownLatch(1);
        runOnMainSync(() -> {
            WebView web = new WebView(getTargetContext()); views[index] = web;
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String url) { loaded.countDown(); }
            });
            CookieManager.getInstance().setCookie(origin, "hc_logout_probe=fixture; Path=/; Secure; SameSite=Lax", ok -> cookie.countDown());
            web.loadDataWithBaseURL(origin, "<html><body>synthetic fixture<script>localStorage.setItem('hc_logout_probe','fixture');var r=indexedDB.open('hc_logout_probe',1);r.onupgradeneeded=function(){r.result.createObjectStore('probe')};r.onsuccess=function(){r.result.close();window.fixtureReady=true};</script></body></html>", "text/html", "UTF-8", null);
        });
        waitFor(loaded); waitFor(cookie);
        for (int i=0; i<30; i++) {
            if ("true".equals(eval(views[index], "window.fixtureReady===true"))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Fixture IndexedDB setup");
    }
    private boolean hasCookie(String origin) {
        String[] value = {null};
        runOnMainSync(() -> value[0] = CookieManager.getInstance().getCookie(origin));
        return value[0] != null && value[0].contains("hc_logout_probe=fixture");
    }
    private boolean hasDatabase(WebView web) throws Exception {
        eval(web, "window.dbCheck=null;var q=indexedDB.open('hc_logout_probe');q.onsuccess=function(){window.dbCheck=q.result.objectStoreNames.contains('probe');q.result.close()};true");
        for (int i=0; i<30; i++) {
            String result = eval(web, "window.dbCheck");
            if ("true".equals(result) || "false".equals(result)) return "true".equals(result);
            Thread.sleep(100);
        }
        throw new AssertionError("Fixture database callback");
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        boolean supported[] = {false};
        runOnMainSync(() -> supported[0] = SiteDataClearer.isSupported());
        if (!supported[0]) { result.putString("stream", "SKIP: WebView does not support site deletion\n"); finish(0, result); return; }
        try {
            clear(SITES);
            fixture(0, TARGET); fixture(1, OTHER);
            check(hasCookie(TARGET) && hasCookie(OTHER), "Fixture cookies seeded");
            check("true".equals(eval(views[0], "localStorage.getItem('hc_logout_probe')==='fixture'")), "Target storage seeded");
            check(hasDatabase(views[0]) && hasDatabase(views[1]), "Fixture databases seeded");
            clear(new String[]{SITES[0]});
            check(!hasCookie(TARGET), "Selected site cookie removed");
            check(hasCookie(OTHER), "Other site cookie retained");
            check("true".equals(eval(views[0], "localStorage.getItem('hc_logout_probe')===null")), "Selected local storage removed");
            check("true".equals(eval(views[1], "localStorage.getItem('hc_logout_probe')==='fixture'")), "Other local storage retained");
            check(!hasDatabase(views[0]), "Selected IndexedDB removed");
            check(hasDatabase(views[1]), "Other IndexedDB retained");
            result.putString("stream", "PASS: " + checks + " synthetic site-isolation checks; no real account sites touched\n");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            result.putString("failure", "synthetic site-isolation probe");
        } finally {
            try { clear(SITES); } catch (Exception ignored) {}
            runOnMainSync(() -> { for (WebView web : views) if (web != null) web.destroy(); });
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
