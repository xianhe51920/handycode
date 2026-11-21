package com.fairycrane.handycode;

import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Reads and caches bundled WebView scripts and markup as UTF-8. */
final class WebAssets {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static final String[] PRELOAD_FILES = {
        "official-identity.js", "taobao-login-chrome.js", "phone-theme.js",
        "login-ready.js", "taobao.html", "refresh-controller.js", "pickup-motion.js",
        "login-agreements.js", "taobao-login-theme.js", "cainiao-layout.js",
        "pdd-same-origin.js", "pdd-section-refresh.js", "pdd-layout.js",
        "pdd-login-client.js", "pdd-login-notice.js", "jd-orders.js",
        "jd-identity.js", "jd-login-theme.js"
    };

    private WebAssets() {}

    static void preload(AssetManager assets) {
        Thread thread = new Thread(() -> {
            for (String name : PRELOAD_FILES) {
                try {
                    readText(assets, name);
                } catch (IOException ignored) {}
            }
        }, "hc-assets-preload");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    static String readText(AssetManager assets, String name) throws IOException {
        String cached = CACHE.get(name);
        if (cached != null) return cached;
        try (InputStream input = assets.open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            CACHE.putIfAbsent(name, text);
            return text;
        }
    }
}
