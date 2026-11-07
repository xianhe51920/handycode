package com.fairycrane.handycode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String PDD_PACKAGE = "com.xunmeng.pinduoduo";
    private static final int TAB_TAOBAO = 0, TAB_PDD = 1, TAB_JD = 2, PAGE_COUNT = 3;
    private static final String[] TAB_KEYS = {"taobao", "pinduoduo", "pickup"};
    private static final String[] TAB_NAMES = {"\u6dd8\u5b9d", "\u62fc\u591a\u591a", "\u53d6\u4ef6\u7801"};
    private static final String PREF_UI = "ui_state", KEY_LAST_TAB = "last_tab", KEY_THEME_MODE = "theme_mode";

    private final android.os.Handler scanHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean scanBright;
    private float previousBrightness;
    private int scanGeneration;
    private final Runnable scanWatch = this::checkScanBrightness;

    private void setScanBrightness(boolean enabled) {
        if (scanBright == enabled) return;
        android.view.WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (enabled) previousBrightness = attrs.screenBrightness;
        attrs.screenBrightness = enabled ? 1f : previousBrightness;
        scanBright = enabled;
        getWindow().setAttributes(attrs);
    }

    private boolean canShowScanCode() {
        return activityResumed && hasWindowFocus() && !pageDragging && switchAnimation == null
            && Math.abs(switchMotion.position() - selected) < 0.001f
            && popupDialog == null && !SiteDataClearer.isBusy()
            && (selected == TAB_PDD || (selected == TAB_TAOBAO && combinedDisplayed
                && transitionPanel != null && transitionPanel.getVisibility() != View.VISIBLE));
    }

    private void checkScanBrightness() {
        scanHandler.removeCallbacks(scanWatch);
        final int generation = ++scanGeneration;
        final int page = selected;
        if (!canShowScanCode()) {
            setScanBrightness(false);
            if (activityResumed) scanHandler.postDelayed(scanWatch, 500);
            return;
        }
        android.webkit.ValueCallback<String> callback = value -> {
            if (generation != scanGeneration) return;
            setScanBrightness(page == selected && canShowScanCode() && "true".equals(value));
            if (activityResumed) scanHandler.postDelayed(scanWatch, 500);
        };
        if (page == TAB_PDD) pddPage.checkVisibleScanCode(callback);
        else if (browser != null) browser.evaluateJavascript(ScanCodeVisibility.SCRIPT, callback);
        else callback.onReceiveValue("false");
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) { ++scanGeneration; setScanBrightness(false); }
        if (activityResumed) { scanHandler.removeCallbacks(scanWatch); scanHandler.post(scanWatch); }
    }

    private WebView browser;
    private final Button[] navigation = new Button[PAGE_COUNT];
    private FrameLayout browserFrame, pinduoduoFrame;
    private PddPage pddPage;
    private JdPage jdPage;
    private String combinedMarkup;
    private String combinedGateScript;
    private String officialIdentityScript;
    private String taobaoLoginChromeScript;
    private int combineGeneration;
    private int popupGeneration;
    private boolean combinedDisplayed;
    private int selected = 0;
    private int themeMode = AppTheme.MODE_SYSTEM;
    private LinearLayout rootLayout;
    private LinearLayout headerLayout;
    private View headerDivider;
    private SwipeFrameLayout viewport;
    private LiquidGlassBar bottomBar;
    private FrameLayout titleViewport;
    private final View[] titleRows = new View[PAGE_COUNT];
    private final TextView[] titleLabels = new TextView[PAGE_COUNT];
    private ImageView pickupTitleIcon;
    private TextView pickupSwitchBadge;
    private int returnToPickupFromTab = -1;
    private int clearingPickupProvider = -1;
    private final Runnable parcelSyncWatch = this::syncExternalParcels;
    private Button themeButton;
    private Button logoutButton;
    private android.graphics.drawable.Drawable logoutIcon;
    private final boolean[] signedIn = new boolean[PAGE_COUNT];
    private SwitchMotion switchMotion;
    private android.animation.ValueAnimator switchAnimation;
    private boolean selectionInitialized, pageDragging, activityResumed;
    private final boolean[] opened = new boolean[PAGE_COUNT];
    private float dragStart;
    private View transitionPanel;
    private final java.util.List<View> transitionCards = new java.util.ArrayList<>();
    private final java.util.List<TextView> transitionTitles = new java.util.ArrayList<>();
    private TextView transitionNote;
    private View errorPanelView;
    private long transitionStarted;
    private int transitionGeneration;
    private final java.util.List<ShimmerBlock> shimmerBlocks = new java.util.ArrayList<>();
    private android.animation.ValueAnimator shimmerAnimation;
    private Dialog popupDialog;
    private WebView popupView;
    private long lastBlockedNotice = 0;

    private final SiteDataClearer.Observer logoutListener = new SiteDataClearer.Observer() {
        @Override public void onStarted(int page) {
            cancelSwitchAnimation();
            pageDragging = false;
            switchMotion.commit(selected);
            if (page >= 0 && page < PAGE_COUNT) {
                opened[page] = false;
                signedIn[page] = false;
            }
            if (page == TAB_PDD) {
                pddPage.resetSessionView();
            } else if (page == TAB_JD) {
                if (clearingPickupProvider >= JdPage.PROVIDER_JD && clearingPickupProvider <= JdPage.PROVIDER_DY) {
                    jdPage.resetProviderSession(clearingPickupProvider);
                } else {
                    jdPage.resetSessionView();
                }
                clearingPickupProvider = -1;
                updatePickupHeader();
            } else {
                ++combineGeneration;
                ++popupGeneration;
                combinedMarkup = null;
                combinedDisplayed = false;
                if (popupDialog != null) popupDialog.dismiss();
                if (browser != null) {
                    browser.stopLoading();
                    browser.setWebViewClient(new WebViewClient());
                    browser.setWebChromeClient(null);
                    browserFrame.removeView(browser);
                    browser.destroy();
                    browser = null;
                }
                hideError();
            }
            stopShimmer();
            preparePreview();
            renderSwitchPosition();
        }
        @Override public void onFinished(int page, boolean success) {
            updateLogoutControls();
            if (!success) android.widget.Toast.makeText(MainActivity.this,
                "\u9000\u51fa\u672a\u5b8c\u6210\uff0c\u8bf7\u68c0\u67e5 WebView \u66f4\u65b0\u540e\u91cd\u8bd5", android.widget.Toast.LENGTH_LONG).show();
            if (activityResumed && !isFinishing() && !isDestroyed()) completeSelection(selected);
        }
    };

    private boolean isDark() {
        return AppTheme.isDark(this, themeMode);
    }

    private void cycleThemeMode() {
        themeMode = AppTheme.nextMode(themeMode);
        getSharedPreferences(PREF_UI, MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, themeMode).apply();
        applyTheme();
        String modeDesc = themeMode == AppTheme.MODE_SYSTEM ? "\u591c\u95f4\u6a21\u5f0f\uff1a\u8ddf\u968f\u7cfb\u7edf"
            : themeMode == AppTheme.MODE_DARK ? "已切换为深色模式" : "已切换为浅色模式";
        android.widget.Toast.makeText(this, modeDesc, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String themeButtonText() {
        if (themeMode == AppTheme.MODE_DARK) return "\u263e \u6df1\u8272";
        if (themeMode == AppTheme.MODE_LIGHT) return "☀ 浅色";
        return "\u25d0 \u81ea\u52a8";
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTheme();
    }

    private void updateSystemBars(boolean dark) {
        Window window = getWindow();
        if (window == null) return;
        window.setStatusBarColor(AppTheme.surface(dark));
        window.setNavigationBarColor(AppTheme.bg(dark));
        View decor = window.getDecorView();
        if (decor == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        }
        int systemUi = dark ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (android.os.Build.VERSION.SDK_INT < 30) {
            systemUi |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        decor.setSystemUiVisibility(systemUi);
    }

    private void applyTheme() {
        boolean dark = isDark();
        updateSystemBars(dark);
        int bg = AppTheme.bg(dark);
        int surface = AppTheme.surface(dark);
        int ink = AppTheme.ink(dark);
        int muted = AppTheme.muted(dark);

        if (rootLayout != null) rootLayout.setBackgroundColor(bg);
        if (headerLayout != null) headerLayout.setBackgroundColor(surface);
        if (headerDivider != null) headerDivider.setBackgroundColor(AppTheme.divider(dark));
        for (TextView label : titleLabels) {
            if (label != null) label.setTextColor(ink);
        }
        if (themeButton != null) {
            themeButton.setText(themeButtonText());
            themeButton.setTextColor(ink);
            themeButton.setBackground(background(AppTheme.subtleFill(dark), 10));
            themeButton.setContentDescription("切换外观模式，当前为" + AppTheme.modeLabel(themeMode));
        }
        if (logoutIcon != null) {
            logoutIcon.setTint(ink);
        }
        if (viewport != null) viewport.setBackgroundColor(bg);
        if (browserFrame != null) browserFrame.setBackgroundColor(bg);
        if (pinduoduoFrame != null) pinduoduoFrame.setBackgroundColor(bg);

        if (transitionPanel != null) transitionPanel.setBackgroundColor(bg);
        for (View card : transitionCards) card.setBackground(background(surface, 16));
        for (TextView title : transitionTitles) title.setTextColor(ink);
        if (transitionNote != null) transitionNote.setTextColor(muted);
        for (ShimmerBlock block : shimmerBlocks) block.setDark(dark);

        if (errorPanelView != null && browserFrame != null) {
            errorPanelView.setBackgroundColor(surface);
            TextView errTitle = browserFrame.findViewWithTag("error_title");
            TextView errDetail = browserFrame.findViewWithTag("error_detail");
            if (errTitle != null) errTitle.setTextColor(ink);
            if (errDetail != null) errDetail.setTextColor(muted);
        }

        if (browser != null) {
            browser.setBackgroundColor(bg);
            browser.evaluateJavascript(
                "(function(d){if(document.documentElement)document.documentElement.setAttribute('data-hc-theme',d?'dark':'light');"
                + "if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(d);})(" + dark + ")",
                null
            );
        }
        if (pddPage != null) pddPage.setDark(dark);
        if (jdPage != null) jdPage.setDark(dark);
        if (bottomBar != null) bottomBar.setDark(dark);
        updatePickupHeader();
        updateLogoutControls();
    }

    private void logoutCurrentPage() {
        if (!signedIn[selected] || SiteDataClearer.isBusy() || switchAnimation != null || pageDragging) return;
        if (!SiteDataClearer.isSupported()) {
            android.widget.Toast.makeText(this, "\u8bf7\u5148\u66f4\u65b0 Android System WebView / Chrome\uff0c\u518d\u9000\u51fa\u767b\u5f55",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (selected == TAB_JD && jdPage != null && jdPage.getMode() >= JdPage.PROVIDER_JD) {
            clearingPickupProvider = jdPage.getMode();
            SiteDataClearer.startWithSites(TAB_JD, LogoutSites.forPickupProvider(clearingPickupProvider));
            return;
        }
        clearingPickupProvider = -1;
        SiteDataClearer.start(selected);
    }

    private void updateLogoutControls() {
        if (logoutButton == null) return;
        boolean dark = isDark();
        int ink = AppTheme.ink(dark);
        int muted = AppTheme.muted(dark);
        boolean busy = SiteDataClearer.isBusy();
        boolean authenticated = signedIn[selected];
        boolean enabled = authenticated && !busy && !pageDragging && switchAnimation == null
            && switchMotion.position() == selected;
        logoutButton.setEnabled(enabled);
        logoutButton.setAlpha(!authenticated && !busy || enabled ? 1f : .45f);
        logoutButton.setText(busy ? "\u9000\u51fa\u4e2d" : authenticated ? "\u9000\u51fa\u767b\u5f55" : "\u672a\u767b\u5f55");
        logoutButton.setTextColor(authenticated ? ink : muted);
        if (logoutIcon != null) logoutIcon.setTint(authenticated ? ink : muted);
        logoutButton.setCompoundDrawablesRelative(null, null, authenticated || busy ? logoutIcon : null, null);
        String platform = TAB_NAMES[Math.max(0, Math.min(PAGE_COUNT - 1, selected))];
        logoutButton.setContentDescription(busy ? "\u6b63\u5728\u9000\u51fa" + platform + "\u767b\u5f55"
            : authenticated ? "\u9000\u51fa" + platform + "\u767b\u5f55" : platform + "\u672a\u767b\u5f55");
        for (Button tab : navigation) if (tab != null) tab.setEnabled(!busy);
        boolean inSubWeb = selected == TAB_JD && jdPage != null && jdPage.getMode() != JdPage.MODE_SUMMARY;
        if (viewport != null) viewport.setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0 && !inSubWeb);
        if (bottomBar != null) bottomBar.setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0);
    }

    private void setSignedIn(int page, boolean value) {
        if (value && SiteDataClearer.isBusy()) return;
        if (page >= 0 && page < PAGE_COUNT) {
            signedIn[page] = value;
            if (!value && jdPage != null) {
                if (page == TAB_TAOBAO) jdPage.updateExternalParcels(JdPage.PROVIDER_TB, false, null);
                else if (page == TAB_PDD) jdPage.updateExternalParcels(JdPage.PROVIDER_PDD, false, null);
            }
            updateLogoutControls();
            if (value && returnToPickupFromTab == page) {
                returnToPickupFromTab = -1;
                scanHandler.postDelayed(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    syncExternalParcels();
                    selectPage(TAB_JD);
                    if (jdPage != null) jdPage.showSummary();
                    android.widget.Toast.makeText(this, "\u5df2\u5b8c\u6210" + TAB_NAMES[page] + "\u767b\u5f55\uff0c\u6b63\u5728\u81ea\u52a8\u62c9\u53d6\u5e76\u6c47\u603b\u53d6\u4ef6\u7801",
                        android.widget.Toast.LENGTH_SHORT).show();
                }, 450);
            }
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        themeMode = getSharedPreferences(PREF_UI, MODE_PRIVATE).getInt(KEY_THEME_MODE, AppTheme.MODE_SYSTEM);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        WebView.setWebContentsDebuggingEnabled(false);
        if (state != null) {
            int restored = state.getInt("selected", TAB_TAOBAO);
            selected = Math.max(0, Math.min(PAGE_COUNT - 1, restored));
        } else {
            selected = initialTab(getIntent());
        }
        switchMotion = new SwitchMotion(selected, PAGE_COUNT);
        buildUi();
        applyTheme();
        SiteDataClearer.observe(logoutListener);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        selectPage(selected);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("provider")) {
            selectPage(initialTab(intent));
        }
    }

    private int initialTab(Intent intent) {
        String requested = intent != null ? intent.getStringExtra("provider") : null;
        int fromIntent = tabIndexForKey(requested);
        if (fromIntent >= 0) return fromIntent;
        int fromSaved = tabIndexForKey(lastTab());
        return fromSaved >= 0 ? fromSaved : TAB_TAOBAO;
    }

    private static int tabIndexForKey(String key) {
        if (key == null) return -1;
        if ("jd".equals(key)) return TAB_JD;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            if (TAB_KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    private String lastTab() {
        return getSharedPreferences(PREF_UI, MODE_PRIVATE).getString(KEY_LAST_TAB, null);
    }

    private void rememberTab(int tab) {
        int clamped = Math.max(0, Math.min(PAGE_COUNT - 1, tab));
        getSharedPreferences(PREF_UI, MODE_PRIVATE).edit()
            .putString(KEY_LAST_TAB, TAB_KEYS[clamped]).apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private Button button(String text, boolean primary) {
        boolean dark = isDark();
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTextColor(primary ? Color.WHITE : AppTheme.ink(dark));
        view.setBackground(background(primary ? AppTheme.ACCENT : AppTheme.subtleFill(dark), 12));
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(10), 0, dp(10), 0);
        return view;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private void buildUi() {
        boolean dark = isDark();
        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(dark));
        root.setFitsSystemWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                applyKeyboardPadding(keyboard, bars.bottom);
                return insets;
            });
        } else {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(insets.getStableInsetLeft(), insets.getStableInsetTop(),
                    insets.getStableInsetRight(), insets.getStableInsetBottom());
                return insets;
            });
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                android.graphics.Rect visible = new android.graphics.Rect();
                root.getWindowVisibleDisplayFrame(visible);
                int[] position = new int[2];
                root.getLocationOnScreen(position);
                applyKeyboardPadding(Math.max(0, position[1] + root.getHeight() - visible.bottom), root.getPaddingBottom());
            });
        }
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        headerLayout = header;
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setMinimumHeight(dp(56));
        header.setBackgroundColor(AppTheme.surface(dark));
        titleViewport = new FrameLayout(this);
        titleViewport.setClipChildren(true);
        int[] icons = {R.drawable.ic_taobao, R.drawable.ic_pinduoduo, R.drawable.ic_pickup};
        for (int i = 0; i < PAGE_COUNT; i++) {
            titleRows[i] = platformTitle(i, TAB_NAMES[i], icons[i]);
            titleViewport.addView(titleRows[i], new FrameLayout.LayoutParams(-1, -1));
        }
        header.addView(titleViewport, new LinearLayout.LayoutParams(0, dp(40), 1f));

        themeButton = button(themeButtonText(), false);
        themeButton.setTextSize(12);
        themeButton.setMinWidth(0);
        themeButton.setMinimumWidth(0);
        themeButton.setPadding(dp(10), 0, dp(10), 0);
        themeButton.setOnClickListener(v -> cycleThemeMode());
        LinearLayout.LayoutParams themeLp = size(-2, 34);
        themeLp.setMarginEnd(dp(6));
        header.addView(themeButton, themeLp);

        logoutButton = button("\u672a\u767b\u5f55", false);
        logoutButton.setTextSize(13);
        logoutButton.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x14000000), background(Color.TRANSPARENT, 10), null));
        logoutIcon = getDrawable(R.drawable.ic_logout);
        if (logoutIcon != null) {
            logoutIcon = logoutIcon.mutate();
            logoutIcon.setBounds(0, 0, dp(18), dp(18));
        }
        logoutButton.setCompoundDrawablesRelative(null, null, logoutIcon, null);
        logoutButton.setCompoundDrawablePadding(dp(5));
        logoutButton.setMinWidth(0);
        logoutButton.setMinimumWidth(0);
        logoutButton.setOnClickListener(v -> logoutCurrentPage());
        header.addView(logoutButton, size(-2, 40));
        root.addView(header);

        headerDivider = new View(this);
        headerDivider.setBackgroundColor(AppTheme.divider(dark));
        root.addView(headerDivider, size(-1, 1));

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        viewport = new SwipeFrameLayout(this);
        viewport.setListener(pageSwipeListener());
        viewport.setCaptureSettlingTouches(true);
        viewport.setBackgroundColor(AppTheme.bg(dark));
        stage.addView(viewport, new FrameLayout.LayoutParams(-1, -1));

        browserFrame = new FrameLayout(this);
        browserFrame.setBackgroundColor(AppTheme.bg(dark));
        buildErrorPanel();
        buildTransitionPanel();
        viewport.addView(browserFrame, new FrameLayout.LayoutParams(-1, -1));

        pinduoduoFrame = new FrameLayout(this);
        pddPage = new PddPage(this, this::openPinduoduoIdentity, value -> setSignedIn(TAB_PDD, value));
        pinduoduoFrame.addView(pddPage, new FrameLayout.LayoutParams(-1, -1));
        viewport.addView(pinduoduoFrame, new FrameLayout.LayoutParams(-1, -1));
        jdPage = new JdPage(this, value -> setSignedIn(TAB_JD, value), this::handlePickupExternalAction, this::updatePickupHeader);
        viewport.addView(jdPage, new FrameLayout.LayoutParams(-1, -1));

        bottomBar = new LiquidGlassBar(this);
        bottomBar.setListener(barScrubListener());
        bottomBar.setCaptureSettlingTouches(true);
        bottomBar.setBackdropSource(viewport);
        bottomBar.setPadding(dp(16), dp(5), dp(16), dp(9));
        bottomBar.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < PAGE_COUNT; i++) {
            final int page = i;
            navigation[i] = button(TAB_NAMES[i], false);
            navigation[i].setBackgroundColor(Color.TRANSPARENT);
            navigation[i].setTextColor(Color.TRANSPARENT);
            navigation[i].setStateListAnimator(null);
            navigation[i].setElevation(0);
            navigation[i].setContentDescription("\u5207\u6362\u5230" + TAB_NAMES[i]);
            navigation[i].setOnClickListener(v -> selectPage(page));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabRow.addView(navigation[i], lp);
        }
        bottomBar.addView(tabRow, new FrameLayout.LayoutParams(-1, dp(48)));
        bottomBar.bindTabs(navigation);

        FrameLayout.LayoutParams barLayout = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        stage.addView(bottomBar, barLayout);

        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        titleViewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        tabRow.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        bottomBar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (b - t != ob - ot) root.requestApplyInsets();
        });
        root.post(root::requestApplyInsets);
    }
    private View platformTitle(int index, String name, int icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(7));
                outline.setAlpha(1f);
            }
        });
        image.setClipToOutline(true);
        LinearLayout.LayoutParams iconLayout = size(28, 28);
        iconLayout.setMarginEnd(dp(8));
        row.addView(image, iconLayout);
        TextView title = label(name, 18, AppTheme.ink(isDark()), true);
        title.setSingleLine(true);
        titleLabels[index] = title;
        row.addView(title);
        if (index == TAB_JD) {
            pickupTitleIcon = image;
            TextView badge = label("\u5207\u6362\u8f6f\u4ef6 \u25be", 12, AppTheme.accentText(isDark()), true);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(background(AppTheme.subtleFill(isDark()), 10));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
            badgeLp.setMarginStart(dp(8));
            row.addView(badge, badgeLp);
            pickupSwitchBadge = badge;
            row.setOnClickListener(v -> {
                if (selected == TAB_JD && !SiteDataClearer.isBusy()) showPickupSwitcherDialog();
            });
        }
        return row;
    }

    private void updatePickupHeader() {
        if (pickupTitleIcon == null || titleLabels[TAB_JD] == null || pickupSwitchBadge == null || jdPage == null) return;
        boolean dark = isDark();
        pickupSwitchBadge.setTextColor(AppTheme.accentText(dark));
        pickupSwitchBadge.setBackground(background(AppTheme.subtleFill(dark), 10));
        int mode = jdPage.getMode();
        if (mode == JdPage.PROVIDER_JD) {
            pickupTitleIcon.setImageResource(R.drawable.ic_jd);
            titleLabels[TAB_JD].setText("京东");
            pickupSwitchBadge.setText("\u5207\u6362/\u6c47\u603b \u25be");
        } else if (mode == JdPage.PROVIDER_SF) {
            pickupTitleIcon.setImageResource(R.drawable.ic_sf);
            titleLabels[TAB_JD].setText("顺丰");
            pickupSwitchBadge.setText("\u5207\u6362/\u6c47\u603b \u25be");
        } else if (mode == JdPage.PROVIDER_DY) {
            pickupTitleIcon.setImageResource(R.drawable.ic_douyin);
            titleLabels[TAB_JD].setText("抖音");
            pickupSwitchBadge.setText("\u5207\u6362/\u6c47\u603b \u25be");
        } else {
            pickupTitleIcon.setImageResource(R.drawable.ic_pickup);
            titleLabels[TAB_JD].setText("\u53d6\u4ef6\u7801");
            pickupSwitchBadge.setText("\u5207\u6362\u8f6f\u4ef6 \u25be");
        }
        updateLogoutControls();
    }

    private void handlePickupExternalAction(int action) {
        if (action == -1) {
            showPickupSwitcherDialog();
            return;
        }
        if (action == -2) {
            refreshExternalParcels(TAB_TAOBAO);
            refreshExternalParcels(TAB_PDD);
            return;
        }
        if (action == JdPage.PROVIDER_TB || action == JdPage.PROVIDER_PDD) {
            if (!signedIn[action]) {
                returnToPickupFromTab = action;
                selectPage(action);
                android.widget.Toast.makeText(this, "\u8bf7\u5b8c\u6210" + TAB_NAMES[action] + "\u767b\u5f55\uff0c\u767b\u5f55\u5b8c\u540e\u5c06\u81ea\u52a8\u8fd4\u56de\u53d6\u4ef6\u7801\u6c47\u603b",
                    android.widget.Toast.LENGTH_SHORT).show();
            } else {
                refreshExternalParcels(action);
                if (jdPage != null) jdPage.showSummary();
                android.widget.Toast.makeText(this, "\u6b63\u5728\u540c\u6b65" + TAB_NAMES[action] + "\u53d6\u4ef6\u7801\u2026",
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showPickupSwitcherDialog() {
        if (jdPage == null) return;
        boolean dark = isDark();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(AppTheme.surface(dark));
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), AppTheme.border(dark));
        card.setBackground(cardBg);

        TextView titleView = label("切换软件登录 / 取件码汇总", 17, AppTheme.ink(dark), true);
        card.addView(titleView);
        TextView subView = label("选择软件进行登录，登录完后自动拉取并汇总待取件码", 12, AppTheme.muted(dark), false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(12);
        card.addView(subView, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1f));

        int[] icons = {
            R.drawable.ic_pickup, R.drawable.ic_taobao, R.drawable.ic_pinduoduo,
            R.drawable.ic_jd, R.drawable.ic_sf, R.drawable.ic_douyin
        };
        String[] titles = {"全部取件码汇总", "淘宝 / 菜鸟", "拼多多", "京东", "顺丰速运", "抖音商城"};
        boolean[] logged = {
            true,
            signedIn[TAB_TAOBAO],
            signedIn[TAB_PDD],
            jdPage.isSignedIn(JdPage.PROVIDER_JD),
            jdPage.isSignedIn(JdPage.PROVIDER_SF),
            jdPage.isSignedIn(JdPage.PROVIDER_DY)
        };
        int[] counts = {
            0,
            jdPage.countFor(JdPage.PROVIDER_TB),
            jdPage.countFor(JdPage.PROVIDER_PDD),
            jdPage.countFor(JdPage.PROVIDER_JD),
            jdPage.countFor(JdPage.PROVIDER_SF),
            jdPage.countFor(JdPage.PROVIDER_DY)
        };
        int currentMode = jdPage.getMode();

        for (int i = 0; i < titles.length; i++) {
            final int which = i;
            boolean active = (i == 0 && currentMode == JdPage.MODE_SUMMARY)
                || (i >= 3 && currentMode == i - 1);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(14));
            rowBg.setColor(active ? (dark ? 0xff261d1c : 0xfffff3ee) : AppTheme.subtleFill(dark));
            rowBg.setStroke(dp(1), active ? AppTheme.accent(dark) : AppTheme.border(dark));
            row.setBackground(rowBg);

            ImageView iconView = new ImageView(this);
            iconView.setImageResource(icons[i]);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(7));
                }
            });
            iconView.setClipToOutline(true);
            LinearLayout.LayoutParams iconLp = size(30, 30);
            iconLp.setMarginEnd(dp(11));
            row.addView(iconView, iconLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            TextView itemTitle = label(titles[i], 15, AppTheme.ink(dark), true);
            itemTitle.setSingleLine(true);
            textCol.addView(itemTitle);
            String descText = i == 0
                ? "自动拉取所有已登录软件的待取包裹"
                : (logged[i] ? "已登录 · 已汇总 " + counts[i] + " 个待取件码" : "未登录 · 点击前往官方页面登录");
            TextView itemDesc = label(descText, 11, AppTheme.muted(dark), false);
            itemDesc.setSingleLine(true);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
            descLp.topMargin = dp(2);
            textCol.addView(itemDesc, descLp);
            row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

            String badgeStr = i == 0 ? (active ? "当前视图" : "查看汇总 ›") : (logged[i] ? "● 已登录" : "去登录 ›");
            boolean highlightBadge = i == 0 || logged[i];
            TextView badge = label(badgeStr, 11,
                highlightBadge ? AppTheme.accentText(dark) : AppTheme.ink(dark), true);
            badge.setSingleLine(true);
            badge.setPadding(dp(9), dp(4), dp(9), dp(4));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(999));
            badgeBg.setColor(highlightBadge ? (dark ? 0x26ff784e : 0x1eeb6238) : AppTheme.surface(dark));
            badge.setBackground(badgeBg);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
            badgeLp.setMarginStart(dp(8));
            row.addView(badge, badgeLp);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) {
                    jdPage.showSummary();
                    syncExternalParcels();
                    updatePickupHeader();
                } else if (which == 1) {
                    handlePickupExternalAction(JdPage.PROVIDER_TB);
                } else if (which == 2) {
                    handlePickupExternalAction(JdPage.PROVIDER_PDD);
                } else {
                    jdPage.openProvider(which - 1);
                    updatePickupHeader();
                }
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            if (i < titles.length - 1) rowLp.bottomMargin = dp(8);
            list.addView(row, rowLp);
        }

        Button closeBtn = button("取消", false);
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(AppTheme.ink(dark));
        closeBtn.setBackground(background(AppTheme.subtleFill(dark), 12));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(42));
        closeLp.topMargin = dp(12);
        card.addView(closeBtn, closeLp);

        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int screenW = getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(screenW - dp(32), dp(380)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
    private void refreshExternalParcels(int tab) {
        if (tab == TAB_TAOBAO) {
            ensureBrowser();
            if (browser != null && combinedDisplayed) {
                browser.evaluateJavascript("(function(){if(typeof window.HandyCodeRefreshParcels==='function')window.HandyCodeRefreshParcels();else if(typeof window.HandyCodeRefresh==='function')window.HandyCodeRefresh();})()", null);
            }
        } else if (tab == TAB_PDD && pddPage != null) {
            pddPage.refreshParcels();
        }
        scanHandler.postDelayed(this::syncExternalParcels, 600);
    }

    private void syncExternalParcels() {
        scanHandler.removeCallbacks(parcelSyncWatch);
        if (!activityResumed || jdPage == null) return;
        if (browser != null && combinedDisplayed) {
            browser.evaluateJavascript("(function(){return window.HandyCodeParcelsSnapshot?window.HandyCodeParcelsSnapshot():null;})()", res -> {
                if (jdPage == null || res == null || "null".equals(res)) return;
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(res);
                    if (obj.optBoolean("ready")) {
                        jdPage.updateExternalParcels(JdPage.PROVIDER_TB, true, obj.optJSONArray("orders"));
                    }
                } catch (org.json.JSONException ignored) {}
            });
        }
        if (signedIn[TAB_PDD] && pddPage != null) {
            pddPage.pollParcels(res -> {
                if (jdPage == null || res == null || "null".equals(res)) return;
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(res);
                    if (obj.optBoolean("ready")) {
                        jdPage.updateExternalParcels(JdPage.PROVIDER_PDD, true, obj.optJSONArray("orders"));
                    }
                } catch (org.json.JSONException ignored) {}
            });
        }
        if (activityResumed) scanHandler.postDelayed(parcelSyncWatch, 1500);
    }

    private void applyKeyboardPadding(int keyboardBottom, int systemBottom) {
        if (viewport == null) return;
        int padding = Math.max(0, keyboardBottom - systemBottom);
        if (viewport.getPaddingBottom() == padding) return;
        boolean imeOpen = padding > 0;
        viewport.setSwipeEnabled(!imeOpen && !SiteDataClearer.isBusy());
        if (bottomBar != null) {
            bottomBar.setSwipeEnabled(!imeOpen && !SiteDataClearer.isBusy());
            bottomBar.setVisibility(imeOpen ? View.GONE : View.VISIBLE);
        }
        viewport.setPadding(0, 0, 0, padding);
        if (imeOpen && selected == TAB_PDD) pddPage.post(pddPage::scrollFocusedInput);
        if (imeOpen && selected == TAB_JD) jdPage.post(jdPage::scrollFocusedInput);
        if (imeOpen && selected == TAB_TAOBAO && browser != null && !combinedDisplayed) {
            final WebView web = browser;
            web.post(() -> {
                if (isFinishing() || isDestroyed() || browser != web || selected != TAB_TAOBAO) return;
                web.evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.matches('input,textarea,[contenteditable=true]'))e.scrollIntoView({block:'center',inline:'nearest'});})()", null);
            });
        }
    }

    private void openPinduoduoIdentity() {
        android.content.Intent link = new android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(PddPage.ENTRY));
        link.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
        try {
            android.content.Intent direct = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(UrlPolicy.PDD_IDENTITY_APP_LINK));
            direct.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
            direct.setPackage(PDD_PACKAGE);
            startActivity(direct);
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            try { startActivity(link); }
            catch (android.content.ActivityNotFoundException | SecurityException noHandler) {
                android.widget.Toast.makeText(this, "无法打开身份码链接，请先安装或更新拼多多", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void buildTransitionPanel() {
        boolean dark = isDark();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(12), dp(10), dp(76));
        content.setBackgroundColor(AppTheme.bg(dark));
        content.setClickable(true);
        content.setContentDescription("\u6b63\u5728\u51c6\u5907\u8eab\u4efd\u7801");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(background(AppTheme.surface(dark), 16));
        card.setPadding(dp(16), dp(18), dp(16), dp(18));
        transitionCards.add(card);
        TextView title = label("\u8eab\u4efd\u7801", 16, AppTheme.ink(dark), true);
        transitionTitles.add(title);
        card.addView(title);
        LinearLayout placeholders = new LinearLayout(this);
        placeholders.setOrientation(LinearLayout.VERTICAL);
        placeholders.setGravity(Gravity.CENTER);
        for (int j = 0; j < 3; j++) {
            ShimmerBlock block = new ShimmerBlock(this, dp(j == 1 ? 12 : 6));
            block.setDark(dark);
            shimmerBlocks.add(block);
            LinearLayout.LayoutParams shape = new LinearLayout.LayoutParams(
                j == 1 ? -1 : dp(j == 0 ? 112 : 148), dp(j == 1 ? 84 : 14));
            shape.setMargins(dp(6), dp(14), dp(6), dp(14));
            placeholders.addView(block, shape);
        }
        card.addView(placeholders, new LinearLayout.LayoutParams(-1, dp(240)));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView note = label("\u6b63\u5728\u51c6\u5907\u8eab\u4efd\u7801\u2026", 12, AppTheme.muted(dark), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(12), 0, 0);
        transitionNote = note;
        content.addView(note);
        content.setVisibility(View.GONE);
        transitionPanel = content;
        browserFrame.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private void startShimmer() {
        if (!activityResumed || selected != TAB_TAOBAO || !opened[TAB_TAOBAO]) return;
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        if (shimmerAnimation == null) {
            shimmerAnimation = android.animation.ValueAnimator.ofFloat(0f, 1f);
            shimmerAnimation.setDuration(1400);
            shimmerAnimation.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            shimmerAnimation.setInterpolator(new android.view.animation.LinearInterpolator());
            shimmerAnimation.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                for (ShimmerBlock block : shimmerBlocks) block.setPhase(value);
            });
        }
        if (!shimmerAnimation.isStarted()) shimmerAnimation.start();
    }

    private void stopShimmer() {
        if (shimmerAnimation != null) shimmerAnimation.cancel();
    }

    private void showTransition() {
        if (transitionPanel == null || combinedMarkup == null) return;
        transitionGeneration++;
        transitionPanel.animate().cancel();
        if (transitionPanel.getVisibility() != View.VISIBLE) transitionStarted = android.os.SystemClock.elapsedRealtime();
        transitionPanel.setAlpha(1f);
        transitionPanel.setVisibility(View.VISIBLE);
        transitionPanel.bringToFront();
        startShimmer();
    }

    private void hideTransition(boolean fade) {
        if (transitionPanel == null) return;
        final int token = ++transitionGeneration;
        transitionPanel.animate().cancel();
        if (!fade) { stopShimmer(); transitionPanel.setVisibility(View.GONE); transitionPanel.setAlpha(1f); return; }
        transitionPanel.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (token == transitionGeneration) { stopShimmer(); transitionPanel.setVisibility(View.GONE); transitionPanel.setAlpha(1f); }
        }).start();
    }

    private void waitForMergedPresentation(WebView view, final int generation) {
        if (isFinishing() || isDestroyed() || generation != combineGeneration || browser != view || combinedMarkup == null) return;
        String ready = "(function(){var a=document.getElementById('identity-content');return !!(a&&a.dataset.ready==='true');})()";
        view.evaluateJavascript(ready, complete -> {
            if (generation != combineGeneration || browser != view || isFinishing() || isDestroyed()) return;
            if ("true".equals(complete) || android.os.SystemClock.elapsedRealtime() - transitionStarted >= 12000) hideTransition(true);
            else view.postDelayed(() -> waitForMergedPresentation(view, generation), 60);
        });
    }

    private void buildErrorPanel() {
        boolean dark = isDark();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout errorPanel = new LinearLayout(this);
        errorPanelView = errorPanel;
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setPadding(dp(28), dp(24), dp(28), dp(80));
        errorPanel.setBackgroundColor(AppTheme.surface(dark));
        TextView errorTitle = label("", 16, AppTheme.ink(dark), true);
        errorTitle.setTag("error_title");
        errorTitle.setGravity(Gravity.CENTER);
        errorPanel.addView(errorTitle, size(-1, -2));
        TextView errorDetail = label("", 12, AppTheme.muted(dark), false);
        errorDetail.setTag("error_detail");
        errorDetail.setPadding(0, dp(16), 0, dp(24));
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setLineSpacing(dp(5), 1f);
        errorPanel.addView(errorDetail, size(-1, -2));
        Button retry = button("重新打开网页", true);
        retry.setOnClickListener(v -> reloadTaobao());
        errorPanel.addView(retry, size(-1, 48));
        scroll.addView(errorPanel);
        scroll.setTag("error_overlay");
        scroll.setVisibility(View.GONE);
        browserFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void selectPage(int index) {
        if (SiteDataClearer.isBusy()) return;
        if (popupDialog != null) popupDialog.dismiss();
        int target = Math.max(0, Math.min(PAGE_COUNT - 1, index));
        preparePreview();
        if (!selectionInitialized) {
            selectionInitialized = true;
            completeSelection(target);
        } else {
            if (bottomBar != null) bottomBar.setGlassActive(true);
            animateSwitch(target);
        }
    }

    private void preparePreview() {
        if (!opened[TAB_TAOBAO] && !combinedDisplayed) {
            transitionPanel.setVisibility(View.VISIBLE);
            transitionPanel.setAlpha(1f);
            for (ShimmerBlock block : shimmerBlocks) block.setPhase(0f);
        }
        if (!opened[TAB_PDD]) pddPage.idlePage();
    }

    private SwipeFrameLayout.Listener pageSwipeListener() {
        return new SwipeFrameLayout.Listener() {
            @Override public boolean isSettling() { return switchAnimation != null; }
            @Override public void onDragStarted() {
                cancelSwitchAnimation();
                preparePreview();
                pageDragging = true;
                dragStart = switchMotion.position();
                if (bottomBar != null) bottomBar.setGlassActive(true);
            }
            @Override public void onDrag(float deltaX) {
                if (!pageDragging) return;
                switchMotion.drag(dragStart, deltaX, viewport.getWidth());
                renderSwitchPosition();
            }
            @Override public void onRelease(boolean cancelled, float velocityX, float distanceX) {
                if (!pageDragging) return;
                pageDragging = false;
                float density = getResources().getDisplayMetrics().density;
                animateSwitch(switchMotion.target(cancelled, velocityX / density, distanceX / density));
            }
        };
    }

    private SwipeFrameLayout.Listener barScrubListener() {
        return new SwipeFrameLayout.Listener() {
            @Override public boolean isSettling() { return switchAnimation != null; }
            @Override public void onDragStarted() {
                cancelSwitchAnimation();
                preparePreview();
                pageDragging = true;
                dragStart = switchMotion.position();
                if (bottomBar != null) bottomBar.setGlassActive(true);
            }
            @Override public void onDrag(float deltaX) {
                if (!pageDragging) return;
                switchMotion.scrub(dragStart, deltaX, bottomBar.slotSpan());
                renderSwitchPosition();
            }
            @Override public void onRelease(boolean cancelled, float velocityX, float distanceX) {
                if (!pageDragging) return;
                pageDragging = false;
                float density = getResources().getDisplayMetrics().density;
                animateSwitch(switchMotion.target(cancelled, -velocityX / density, -distanceX / density));
            }
        };
    }

    private void cancelSwitchAnimation() {
        android.animation.ValueAnimator old = switchAnimation;
        switchAnimation = null;
        if (old != null) old.cancel();
    }

    private void animateSwitch(final int target) {
        cancelSwitchAnimation();
        pageDragging = false;
        if (switchMotion.position() == target || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            completeSelection(target);
            return;
        }
        final android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofFloat(switchMotion.position(), target);
        switchAnimation = animation;
        updateLogoutControls();
        animation.setDuration(SwitchMotion.duration(target - switchMotion.position()));
        animation.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        animation.addUpdateListener(value -> {
            switchMotion.position((float) value.getAnimatedValue());
            renderSwitchPosition();
        });
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator value) {
                if (switchAnimation != animation) return;
                switchAnimation = null;
                completeSelection(target);
            }
        });
        animation.start();
    }

    private void renderSwitchPosition() {
        if (switchMotion == null || viewport == null || bottomBar == null) return;
        float position = switchMotion.position();
        boolean moving = pageDragging || switchAnimation != null || Math.abs(position - Math.round(position)) > 0.001f;
        float width = viewport.getWidth();
        View[] pages = {browserFrame, pinduoduoFrame, jdPage};
        int titleHeight = titleViewport.getHeight() > 0 ? titleViewport.getHeight() : dp(40);
        int nearest = Math.round(position);
        for (int i = 0; i < PAGE_COUNT; i++) {
            pages[i].setTranslationX(switchMotion.offset(i, width));
            pages[i].setVisibility(opened[i] || (i == TAB_TAOBAO && browser != null)
                || (moving && width > 0 && Math.abs(i - position) <= 1.25f) || i == nearest
                ? View.VISIBLE : View.INVISIBLE);
            pages[i].setImportantForAccessibility(!moving && i == selected
                ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            titleRows[i].setTranslationY(switchMotion.offset(i, titleHeight));
            titleRows[i].setAlpha(Math.max(0f, 1f - Math.abs(i - position)));
            titleRows[i].setImportantForAccessibility(!moving && i == selected
                ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        if (moving) {
            ++scanGeneration;
            setScanBrightness(false);
            scanHandler.removeCallbacks(scanWatch);
            if (activityResumed) scanHandler.postDelayed(scanWatch, 500);
        }
        bottomBar.setSwitchPosition(position, moving);
        updateLogoutControls();
    }
    private void completeSelection(int target) {
        selectionInitialized = true;
        boolean changed = selected != target;
        selected = target;
        rememberTab(target);
        switchMotion.commit(target);
        for (int i = 0; i < navigation.length; i++) {
            boolean active = i == selected;
            navigation[i].setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            navigation[i].setSelected(active);
        }
        if (bottomBar != null) bottomBar.setGlassActive(false);
        renderSwitchPosition();
        if (SiteDataClearer.isBusy()) return;
        if (changed) {
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(viewport.getWindowToken(), 0);
        }
        jdPage.resumePage(selected == TAB_JD);
        try {
            if (titleRows[TAB_JD] != null) titleRows[TAB_JD].setClickable(selected == TAB_JD);
            if (selected == TAB_TAOBAO) {
                pddPage.idlePage();
                ensureBrowser();
                opened[TAB_TAOBAO] = true;
                if (browser != null) {
                    browser.onResume();
                    if (combinedDisplayed) {
                        waitForMergedPresentation(browser, combineGeneration);
                    } else {
                        waitForOfficialIdentity(browser, combineGeneration);
                    }
                }
                if (transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
                scanHandler.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || !activityResumed) return;
                    pddPage.showPage();
                    if (selected != TAB_PDD) pddPage.idlePage();
                    jdPage.warmBackground();
                    syncExternalParcels();
                }, 1400);
            } else if (selected == TAB_JD) {
                stopShimmer();
                ensureBrowser();
                if (browser != null) browser.onResume();
                pddPage.showPage();
                pddPage.idlePage();
                jdPage.showPage();
                opened[TAB_JD] = true;
                syncExternalParcels();
            } else {
                stopShimmer();
                pddPage.showPage();
                opened[TAB_PDD] = true;
                scanHandler.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || !activityResumed) return;
                    ensureBrowser();
                    if (browser != null) browser.onResume();
                    jdPage.warmBackground();
                    syncExternalParcels();
                }, 700);
            }
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this).setTitle("无法启动网页组件")
                .setMessage("\u8bf7\u5728\u624b\u673a\u7cfb\u7edf\u6216\u5e94\u7528\u5546\u5e97\u66f4\u65b0 Android System WebView / Chrome \u540e\u91cd\u8bd5\u3002")
                .setPositiveButton("\u77e5\u9053\u4e86", (d, w) -> finish()).show();
        }
    }

    private void ensureBrowser() {
        if (browser != null) return;
        browser = createBrowser(false);
        browserFrame.addView(browser, 0, new FrameLayout.LayoutParams(-1, -1));
        loadCombined();
    }

    private String readAssetText(String name) throws java.io.IOException {
        try (java.io.InputStream stream = getAssets().open(name)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void loadCombined() {
        combineGeneration++;
        combinedMarkup = null;
        try {
            officialIdentityScript = readAssetText("official-identity.js");
            taobaoLoginChromeScript = readAssetText("taobao-login-chrome.js");
            combinedGateScript = readAssetText("login-ready.js").trim();
            String rawHtml = readAssetText("taobao.html");
            if (isDark()) {
                rawHtml = rawHtml.replace("<html lang=\"zh-CN\">", "<html lang=\"zh-CN\" data-hc-theme=\"dark\">");
            }
            combinedMarkup = rawHtml
                .replace("@@REFRESH_CONTROLLER@@", readAssetText("refresh-controller.js"))
                .replace("@@OFFICIAL_IDENTITY@@", officialIdentityScript)
                .replace("@@IDENTITY_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_ENTRY))
                .replace("@@PARCELS_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_PARCELS_ENTRY));
            combinedDisplayed = false;
            showTransition();
            browser.loadUrl(UrlPolicy.TAOBAO_ENTRY);
        } catch (java.io.IOException error) {
            loadFailure(false, "\u65e0\u6cd5\u8bfb\u53d6\u672c\u5730\u53d6\u4ef6\u9875\u9762\uff0c\u8bf7\u91cd\u65b0\u5b89\u88c5\u5e94\u7528\u540e\u91cd\u8bd5\u3002");
        }
    }

    private boolean sameIdentityPage(String url) {
        if (!UrlPolicy.isAllowed(url)) return false;
        android.net.Uri actual = android.net.Uri.parse(url);
        android.net.Uri expected = android.net.Uri.parse(UrlPolicy.TAOBAO_ENTRY);
        return java.util.Objects.equals(actual.getHost(), expected.getHost())
            && java.util.Objects.equals(actual.getPath(), expected.getPath());
    }

    private void renderCombined(WebView view, String url) {
        if (combinedMarkup == null || combinedGateScript == null || !UrlPolicy.isAllowed(url)) return;
        waitForOfficialIdentity(view, ++combineGeneration);
    }

    private void waitForOfficialIdentity(WebView view, final int generation) {
        if (isFinishing() || isDestroyed() || generation != combineGeneration
                || browser != view || combinedMarkup == null || !UrlPolicy.isAllowed(view.getUrl())) return;
        view.evaluateJavascript(officialIdentityScript + "\n" + combinedGateScript, ready -> {
            if (generation != combineGeneration || browser != view || isFinishing() || isDestroyed()) return;
            if ("true".equals(ready) && sameIdentityPage(view.getUrl())) applyCombined(view, generation);
            else retryOfficialIdentity(view, generation, false);
        });
    }

    private void applyCombined(WebView view, final int generation) {
        if (combinedMarkup == null || !sameIdentityPage(view.getUrl())) return;
        String script = officialIdentityScript + "\n(function(){if(document.getElementById('handycode-merged'))return true;"
            + "if(!(" + combinedGateScript + "))return false;"
            + "var seed=HandyCodeOfficialIdentity.captureInitial?HandyCodeOfficialIdentity.captureInitial(document):'';"
            + "document.open();document.write(" + org.json.JSONObject.quote(combinedMarkup)
            + ");document.close();"
            + "if(seed&&typeof window.HandyCodeSeedIdentity==='function')window.HandyCodeSeedIdentity(seed);"
            + "if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(" + isDark() + ");"
            + "return true;})()";
        view.evaluateJavascript(script, result -> {
            if (generation != combineGeneration || browser != view || isFinishing() || isDestroyed()) return;
            combinedDisplayed = "true".equals(result);
            if (combinedDisplayed) setSignedIn(TAB_TAOBAO, true);
            if (combinedDisplayed) waitForMergedPresentation(view, generation);
            if (!combinedDisplayed) retryOfficialIdentity(view, generation, false);
        });
    }

    private void refreshAfterLogin() {
        final WebView view = browser;
        final int generation = combineGeneration;
        view.evaluateJavascript("(function(){if(typeof window.HandyCodeRefresh==='function'){window.HandyCodeRefresh();return true;}return false;})()", handled -> {
            if (view != browser || generation != combineGeneration || isFinishing() || isDestroyed()) return;
            if (!"true".equals(handled)) loadCombined();
        });
    }

    private WebView createBrowser(boolean popup) {
        WebView web = new WebView(this);
        web.setBackgroundColor(AppTheme.bg(isDark()));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (UrlPolicy.isAllowed(request.getUrl().toString())) return false;
                if (request.isForMainFrame()) blockedNavigation();
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (UrlPolicy.isAllowed(url)) return false;
                blockedNavigation();
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if ("about:blank".equals(url) && popup) return;
                if (!UrlPolicy.isAllowed(url)) {
                    view.stopLoading();
                    blockedNavigation();
                    return;
                }
                if (!popup) {
                    combinedDisplayed = false;
                    combineGeneration++;
                    if (combinedMarkup != null) showTransition();
                    hideError();
                } else popupGeneration++;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (popup) installPresentationObserver(view);
                if (!popup) {
                    if (!combinedDisplayed) {
                        installPresentationObserver(view);
                        renderCombined(view, url);
                    }
                } else waitForPopupIdentity(view, ++popupGeneration);
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                installPresentationObserver(view);
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                if (combinedMarkup != null) {
                    if (popup) waitForPopupIdentity(view, ++popupGeneration);
                    else if (!combinedDisplayed) renderCombined(view, url);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) loadFailure(popup, "\u8bf7\u68c0\u67e5\u7f51\u7edc\u540e\u91cd\u8bd5\uff0c\u6216\u7a0d\u540e\u91cd\u65b0\u6253\u5f00\u7f51\u9875\u3002");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) loadFailure(popup, "\u5b98\u65b9\u9875\u9762\u8fd4\u56de HTTP " + response.getStatusCode() + "\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                loadFailure(popup, "\u9875\u9762\u7684\u5b89\u5168\u8fde\u63a5\u9a8c\u8bc1\u5931\u8d25\u3002\u8bf7\u68c0\u67e5\u624b\u673a\u65e5\u671f\u3001\u7f51\u7edc\u6216\u7a0d\u540e\u91cd\u8bd5\u3002");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                view.post(() -> recoverBrowser(view, popup));
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                String value = message.message();
                if ((popup ? popupView == web : browser == web) && combinedMarkup != null
                        && value != null && value.startsWith("__HANDYCODE_PHASE__:")) {
                    String phase = value.substring("__HANDYCODE_PHASE__:".length());
                    if ("login".equals(phase)) setSignedIn(TAB_TAOBAO, false);
                    else if ("ready".equals(phase) || "merged".equals(phase)) setSignedIn(TAB_TAOBAO, true);
                    if (popup) return true;
                }
                if (!popup && browser == web && combinedMarkup != null
                        && value != null && value.startsWith("__HANDYCODE_PHASE__:")) {
                    String phase = value.substring("__HANDYCODE_PHASE__:".length());
                    if ("login".equals(phase)) revealOfficialLogin(web);
                    else if ("loading".equals(phase) && !combinedDisplayed) showTransition();
                    else if ("ready".equals(phase)) { if (!combinedDisplayed) { showTransition(); renderCombined(web, web.getUrl()); } }
                    else if ("merged".equals(phase)) {
                        combinedDisplayed = true;
                        if (isDark()) {
                            web.evaluateJavascript("if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(true);", null);
                        }
                        waitForMergedPresentation(web, combineGeneration);
                    } else if ("parcels".equals(phase)) {
                        syncExternalParcels();
                    }
                    return true;
                }
                return false;
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message message) {
                if (!userGesture || popup || popupDialog != null) return false;
                openPopup(message);
                return true;
            }
            @Override public void onCloseWindow(WebView window) {
                if (popupDialog != null && window == popupView) popupDialog.dismiss();
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.deny();
            }
        });
        return web;
    }

    private void openPopup(Message message) {
        boolean dark = isDark();
        popupDialog = new Dialog(this);
        popupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(AppTheme.surface(dark));
        TextView title = label("官方网页登录", 18, AppTheme.ink(dark), true);
        title.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.addView(title);
        TextView trustedDomains = label("淘宝官方网页登录", 12, AppTheme.muted(dark), false);
        trustedDomains.setPadding(dp(16), 0, dp(16), dp(10));
        body.addView(trustedDomains);
        popupView = createBrowser(true);
        body.addView(popupView, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button close = button("关闭登录窗口", false);
        close.setOnClickListener(v -> popupDialog.dismiss());
        body.addView(close, size(-1, 48));
        popupDialog.setContentView(body);
        popupDialog.setOnDismissListener(d -> {
            popupGeneration++;
            CookieManager.getInstance().flush();
            if (popupView != null) {
                popupView.stopLoading();
                ((ViewGroup) popupView.getParent()).removeView(popupView);
                popupView.destroy();
            }
            popupView = null; popupDialog = null;
            if (browser != null && combinedMarkup != null) {
                final WebView parent = browser;
                final int generation = combineGeneration;
                parent.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || browser != parent || generation != combineGeneration) return;
                    if (combinedDisplayed) refreshAfterLogin();
                    else parent.loadUrl(UrlPolicy.TAOBAO_ENTRY);
                }, 300);
            }
        });
        popupDialog.show();
        Window window = popupDialog.getWindow();
        if (window != null) window.setLayout(-1, -1);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) message.obj;
        transport.setWebView(popupView);
        message.sendToTarget();
    }

    private void waitForPopupIdentity(WebView view, final int generation) {
        if (isFinishing() || isDestroyed() || generation != popupGeneration || popupView != view
                || combinedMarkup == null || combinedGateScript == null || !UrlPolicy.isAllowed(view.getUrl())) return;
        view.evaluateJavascript(officialIdentityScript + "\n" + combinedGateScript, ready -> {
            if (generation != popupGeneration || popupView != view || isFinishing() || isDestroyed()) return;
            if ("true".equals(ready) && sameIdentityPage(view.getUrl())) {
                CookieManager.getInstance().flush();
                if (popupDialog != null) popupDialog.dismiss();
            } else retryOfficialIdentity(view, generation, true);
        });
    }

    private void retryOfficialIdentity(WebView view, final int generation, boolean popup) {
        Runnable next = () -> {
            if (popup) waitForPopupIdentity(view, generation);
            else waitForOfficialIdentity(view, generation);
        };
        if (UrlPolicy.isAllowed(view.getUrl()) && officialIdentityScript != null) {
            view.evaluateJavascript(officialIdentityScript + "\nHandyCodeOfficialIdentity.recover(document,Date.now())",
                status -> {
                    if (!popup && generation == combineGeneration && "\"login\"".equals(status)) revealOfficialLogin(view);
                    view.postDelayed(next, 220);
                });
        } else view.postDelayed(next, 220);
    }

    private void installPresentationObserver(WebView view) {
        if (combinedMarkup == null || officialIdentityScript == null || !UrlPolicy.isAllowed(view.getUrl())) return;
        view.evaluateJavascript(taobaoLoginChromeScript + "\n" + officialIdentityScript + "\nHandyCodeOfficialIdentity.watch(document)", null);
    }

    private void revealOfficialLogin(WebView view) {
        if (browser != view || taobaoLoginChromeScript == null || transitionPanel.getVisibility() != View.VISIBLE) return;
        final int expected = combineGeneration;
        view.evaluateJavascript(taobaoLoginChromeScript, ignored -> {
            if (browser != view || expected != combineGeneration || isFinishing() || isDestroyed()) return;
            view.postVisualStateCallback(expected, new WebView.VisualStateCallback() {
                @Override public void onComplete(long requestId) {
                    if (browser == view && expected == combineGeneration && !isFinishing() && !isDestroyed()) hideTransition(false);
                }
            });
        });
    }

    private void loadFailure(boolean popup, String description) {
        if (popup) {
            new AlertDialog.Builder(this).setTitle("登录网页暂时无法打开")
                .setMessage(description).setPositiveButton("\u77e5\u9053\u4e86", null).show();
        } else {
            hideTransition(false);
            showError("网页暂时无法打开", description);
        }
    }

    private void recoverBrowser(WebView view, boolean popup) {
        if (popup) {
            if (popupDialog != null) popupDialog.dismiss();
            return;
        }
        if (browser != view) return;
        combineGeneration++;
        hideTransition(false);
        browserFrame.removeView(view);
        view.destroy();
        browser = null;
        showError("\u7f51\u9875\u7ec4\u4ef6\u5df2\u505c\u6b62", "\u70b9\u51fb\u201c\u91cd\u65b0\u6253\u5f00\u7f51\u9875\u201d\u5373\u53ef\u6062\u590d\u3002");
    }

    private void blockedNavigation() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBlockedNotice < 2500) return;
        lastBlockedNotice = now;
        new AlertDialog.Builder(this).setTitle("\u65e0\u6cd5\u6253\u5f00\u6b64\u94fe\u63a5")
            .setMessage("\u6b64\u7f51\u9875\u94fe\u63a5\u9700\u8981\u5176\u4ed6\u5e94\u7528\uff0c\u6216\u4e0d\u5728\u6dd8\u5b9d\u5b98\u65b9\u7f51\u9875\u8303\u56f4\u5185\u3002")
            .setPositiveButton("留在当前页面", null).show();
    }

    private void hideError() {
        View overlay = browserFrame.findViewWithTag("error_overlay");
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void showError(String title, String detail) {
        ((TextView) browserFrame.findViewWithTag("error_title")).setText(title);
        ((TextView) browserFrame.findViewWithTag("error_detail")).setText(detail);
        View overlay = browserFrame.findViewWithTag("error_overlay");
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
    }

    private void reloadTaobao() {
        hideError();
        if (browser == null) ensureBrowser();
        else loadCombined();
    }

    private void goBack() {
        if (SiteDataClearer.isBusy()) return;
        if (switchAnimation != null || pageDragging) {
            cancelSwitchAnimation(); pageDragging = false;
            switchMotion.commit(selected); renderSwitchPosition(); return;
        }
        if (popupView != null) {
            if (popupView.canGoBack()) popupView.goBack();
            else if (popupDialog != null) popupDialog.dismiss();
        } else if (selected == TAB_JD && jdPage.goBack()) {
            return;
        } else if (selected == TAB_PDD && pddPage.goBack()) {
            return;
        } else if (selected == TAB_TAOBAO && browser != null && browser.canGoBack()) {
            hideError();
            browser.goBack();
        } else finish();
    }

    @Override public void onBackPressed() { goBack(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("selected", selected);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        activityResumed = false;
        ++scanGeneration;
        scanHandler.removeCallbacks(scanWatch);
        scanHandler.removeCallbacks(parcelSyncWatch);
        setScanBrightness(false);
        cancelSwitchAnimation(); pageDragging = false;
        switchMotion.commit(selected); renderSwitchPosition();
        stopShimmer();
        CookieManager.getInstance().flush();
        if (browser != null) browser.onPause();
        pddPage.pausePage();
        jdPage.pausePage();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        scanHandler.removeCallbacks(scanWatch);
        scanHandler.post(scanWatch);
        scanHandler.removeCallbacks(parcelSyncWatch);
        scanHandler.postDelayed(parcelSyncWatch, 800);
        applyTheme();
        if (SiteDataClearer.isBusy()) { updateLogoutControls(); return; }
        if (browser != null) browser.onResume();
        pddPage.resumePage(selected == TAB_PDD);
        jdPage.resumePage(selected == TAB_JD);
        if (!opened[selected]) { completeSelection(selected); return; }
        if (transitionPanel != null && transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
    }
    @Override protected void onDestroy() {
        scanHandler.removeCallbacks(scanWatch);
        scanHandler.removeCallbacks(parcelSyncWatch);
        ++scanGeneration;
        setScanBrightness(false);
        SiteDataClearer.removeObserver(logoutListener);
        cancelSwitchAnimation();
        stopShimmer();
        combineGeneration++;
        popupGeneration++;
        pddPage.destroyPage();
        jdPage.destroyPage();
        if (popupDialog != null) popupDialog.dismiss();
        if (browser != null) {
            browserFrame.removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}