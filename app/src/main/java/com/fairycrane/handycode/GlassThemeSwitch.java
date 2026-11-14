package com.fairycrane.handycode;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;

/** Local Glass Light Toggle renderer, backed by stills from the same shader. */
final class GlassThemeSwitch extends FrameLayout {
    private final ImageView backup;
    private final Drawable[] backupFrames = new Drawable[2];
    private WebView renderer;
    private WebView touchRenderer;
    private boolean dark, ready;
    private float touchStart;
    private Preview preview;
    private String script;
    private int recoveryAttempts;
    private boolean closed;
    private final Runnable renderTimeout = () -> recoverRenderer(renderer);

    interface Preview {
        void begin();
        void blend(float light);
        void finish();
    }

    GlassThemeSwitch(Context context, boolean initialDark) {
        super(context);
        dark = initialDark;
        setClickable(true);
        setFocusable(true);
        backup = new ImageView(context);
        backup.setScaleType(ImageView.ScaleType.FIT_CENTER);
        backup.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(backup, new LayoutParams(-1, -1));
        updateLabel();
        try {
            script = WebAssets.readText(context.getAssets(), "glass-theme-switch.js");
            createRenderer(context, script);
        } catch (IOException | RuntimeException unavailable) {
            destroyRenderer();
        }
    }

    void setDark(boolean value) {
        if (dark == value) return;
        dark = value;
        updateLabel();
        updateRenderer();
    }

    void setPreview(Preview listener) { preview = listener; }

    private void updateLabel() {
        setContentDescription("切换外观模式，当前为" + (dark ? "深色" : "浅色"));
        int index = dark ? 0 : 1;
        if (backupFrames[index] == null) {
            // Rendered from glass-theme-switch.js, including the pixel icons and glow.
            try (InputStream image = getContext().getAssets().open("glass-theme-" + (dark ? "dark" : "light") + ".png")) {
                backupFrames[index] = Drawable.createFromStream(image, null);
            } catch (IOException unavailable) { /* The live renderer can still draw. */ }
        }
        backup.setImageDrawable(backupFrames[index]);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(dark);
    }

    private void createRenderer(Context context, String script) {
        WebView view = new WebView(context);
        renderer = view;
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setFocusable(false);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setOverScrollMode(OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setBlockNetworkLoads(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView target, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView target, String url) { return true; }
            @Override public boolean onRenderProcessGone(WebView target, RenderProcessGoneDetail detail) {
                recoverRenderer(target);
                return true;
            }
        });
        view.addJavascriptInterface(new Bridge(view), "HandyCodeGlassBridge");
        addView(view, new LayoutParams(-1, -1));
        view.loadDataWithBaseURL(null,
            "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<meta http-equiv=Content-Security-Policy content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'\">"
            + "<style>html,body{margin:0;overflow:hidden}canvas{display:block;width:100vw;height:100vh;touch-action:none}</style>"
            + "</head><body><canvas id=gl></canvas><script>window.HandyCodeGlassInitialDark=" + dark + ";\n"
            + "try {\n" + script + "\n} catch(error) { HandyCodeGlassBridge.unavailable(); }\n</script></body></html>", "text/html", "UTF-8", null);
        postDelayed(renderTimeout, 3000);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchRenderer = ready ? renderer : null;
            touchStart = event.getX();
            setPressed(true);
            if (touchRenderer != null && preview != null) preview.begin();
        } else if (!isPressed()) return true;

        if (event.getPointerCount() > 1) action = MotionEvent.ACTION_CANCEL;
        if (touchRenderer != null && touchRenderer == renderer) {
            touchRenderer.evaluateJavascript("window.HandyCodeGlassInput(" + action + ","
                + event.getX() / getWidth() + "," + event.getY() / getHeight() + ")", null);
        } else if (action == MotionEvent.ACTION_UP) {
            float delta = event.getX() - touchStart;
            if (Math.abs(delta) < ViewConfiguration.get(getContext()).getScaledTouchSlop() || (delta > 0) == dark) {
                performClick();
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchRenderer = null;
            setPressed(false);
        }
        return true;
    }

    private void updateRenderer() {
        if (ready) renderer.evaluateJavascript("window.HandyCodeSetGlassDark(" + dark + ")", null);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && ready) renderer.evaluateJavascript("window.HandyCodeRedrawGlassSwitch()", null);
        else if (visibility == VISIBLE && renderer == null && script != null && !closed) {
            recoveryAttempts = 0;
            createRenderer(getContext(), script);
        }
    }

    void destroyRenderer() {
        closed = true;
        disposeRenderer();
    }

    private void recoverRenderer(WebView owner) {
        if (closed || owner == null || renderer != owner) return;
        disposeRenderer();
        if (getWindowVisibility() == VISIBLE && recoveryAttempts++ < 2 && script != null) {
            post(() -> {
                if (!closed && renderer == null) createRenderer(getContext(), script);
            });
        }
    }

    private void disposeRenderer() {
        removeCallbacks(renderTimeout);
        WebView view = renderer;
        renderer = null;
        touchRenderer = null;
        ready = false;
        setPressed(false);
        if (view != null) {
            removeView(view);
            view.destroy();
        }
        if (preview != null) preview.finish();
    }

    public final class Bridge {
        private final WebView owner;
        Bridge(WebView owner) { this.owner = owner; }

        @JavascriptInterface public void ready() {
            post(() -> {
                if (renderer != owner) return;
                owner.postVisualStateCallback(0, new WebView.VisualStateCallback() {
                    @Override public void onComplete(long requestId) {
                        if (renderer != owner) return;
                        removeCallbacks(renderTimeout);
                        ready = true;
                        updateRenderer();
                        owner.evaluateJavascript("window.HandyCodeRedrawGlassSwitch()", null);
                    }
                });
            });
        }

        @JavascriptInterface public void unavailable() {
            post(() -> recoverRenderer(owner));
        }

        @JavascriptInterface public void choose(boolean value) {
            post(() -> {
                if (renderer == owner && ready && dark != value) performClick();
            });
        }

        @JavascriptInterface public void blend(double light) {
            post(() -> {
                if (renderer == owner && ready && preview != null) preview.blend((float)light);
            });
        }

        @JavascriptInterface public void settled() {
            post(() -> { if (renderer == owner && ready && preview != null) preview.finish(); });
        }
    }
}
