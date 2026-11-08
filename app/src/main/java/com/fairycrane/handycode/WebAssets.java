package com.fairycrane.handycode;

import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads bundled WebView scripts and markup as UTF-8. */
final class WebAssets {
    private WebAssets() {}

    static String readText(AssetManager assets, String name) throws IOException {
        try (InputStream input = assets.open(name)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
