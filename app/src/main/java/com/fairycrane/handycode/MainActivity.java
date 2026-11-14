package com.fairycrane.handycode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
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
    private static final String TAOBAO_PACKAGE = "com.taobao.taobao";
    private static final int TAB_CAINIAO = 0, TAB_TAOBAO = 1, TAB_PDD = 2, TAB_JD = 3, TAB_PICKUP = 4, PAGE_COUNT = 5;
    private static final String[] TAB_KEYS = {"cainiao", "taobao", "pinduoduo", "jd", "pickup"};
    private static final String[] TAB_NAMES = {"菜鸟", "淘宝", "拼多多", "京东", "取件汇总"};
    private static final String PREF_UI = "ui_state", KEY_LAST_TAB = "last_tab", KEY_THEME_MODE = "theme_mode";
    private static final String KEY_TAOBAO_LOGIN_REQUIRED = "taobao_login_required";

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
        return activityResumed && switchMotion != null && hasWindowFocus() && !pageDragging && switchAnimation == null
            && Math.abs(switchMotion.position() - selected) < 0.001f
            && popupView == null && !SiteDataClearer.isBusy()
            && (selected == TAB_CAINIAO || selected == TAB_PDD || selected == TAB_JD || (selected == TAB_TAOBAO && combinedDisplayed
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
        else if (page == TAB_CAINIAO) cainiaoPage.checkVisibleScanCode(callback);
        else if (page == TAB_JD) jdPage.checkVisibleScanCode(callback);
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
    private PickupSummaryPage pickupPage;
    private int clearingPickupProvider = -1, returnToPickupFromTab = -1, parcelSyncGeneration;
    private final Runnable parcelSyncWatch = this::syncExternalParcels;
    private CainiaoPage cainiaoPage;
    private String combinedMarkup;
    private String combinedGateScript;
    private String officialIdentityScript;
    private String taobaoLoginChromeScript;
    private String phoneThemeScript;
    private int combineGeneration;
    private int popupGeneration;
    private boolean combinedDisplayed;
    private final Handler taobaoHandler = new Handler(Looper.getMainLooper());
    private final TaobaoLoadGuard taobaoLoad = new TaobaoLoadGuard();
    private final Runnable taobaoDeadline = this::checkTaobaoDeadline;
    private boolean taobaoLoadFailed, taobaoLoginVisible;
    private int selected = 0;
    private int themeMode;
    private float chromeLight;
    private ThemeTransition themeTransition;
    private boolean themePreview;
    private LinearLayout rootLayout;
    private LinearLayout headerLayout;
    private View headerDivider;
    private SwipeFrameLayout viewport;
    private View[] platformPages;
    private LiquidGlassBar bottomBar;
    private FrameLayout titleViewport;
    private final View[] titleRows = new View[PAGE_COUNT];
    private final TextView[] titleLabels = new TextView[PAGE_COUNT];
    private GlassThemeSwitch themeButton;
    private final boolean[] signedIn = new boolean[PAGE_COUNT];
    private SwitchMotion switchMotion;
    private android.animation.ValueAnimator switchAnimation;
    private boolean selectionInitialized, pageDragging, activityResumed;
    private final boolean[] opened = new boolean[PAGE_COUNT];
    private float dragStart;
    private PickupLoadingView transitionPanel;
    private View errorPanelView;
    private long transitionStarted;
    private int transitionGeneration;
    private boolean transitionRevealQueued;
    private WebView popupView;
    private WebView phoneAddView;
    private boolean phoneAddPageStarted;
    private boolean popupInlineWorkflow;
    private long lastBlockedNotice = 0;

    private final SiteDataClearer.Observer logoutListener = new SiteDataClearer.Observer() {
        @Override public void onStarted(int page) {
            parcelSyncGeneration++;
            if (pickupPage != null && (page == TAB_TAOBAO || page == TAB_PDD)) {
                pickupPage.updateExternalParcels(page == TAB_TAOBAO ? PickupSummaryPage.PROVIDER_TB : PickupSummaryPage.PROVIDER_PDD, false, null);
            }
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
                jdPage.resetSessionView();
                if (pickupPage != null) pickupPage.resetProviderSession(PickupSummaryPage.PROVIDER_JD);
            } else if (page == TAB_PICKUP) {
                if (clearingPickupProvider >= PickupSummaryPage.PROVIDER_JD) {
                    pickupPage.resetProviderSession(clearingPickupProvider);
                    if (clearingPickupProvider == PickupSummaryPage.PROVIDER_JD) {
                        jdPage.resetSessionView(); signedIn[TAB_JD] = false; opened[TAB_JD] = false;
                    }
                }
                clearingPickupProvider = -1;
            } else {
                opened[TAB_CAINIAO] = false;
                setTaobaoLoginRequired(true);
                ++combineGeneration;
                ++popupGeneration;
                resetTaobaoLoad();
                combinedMarkup = null;
                combinedDisplayed = false;
                closePopup();
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
            updateNavigationEnabled();
            if (!success) android.widget.Toast.makeText(MainActivity.this,
                "退出未完成，请检查 WebView 更新后重试", android.widget.Toast.LENGTH_LONG).show();
            if (activityResumed && !isFinishing() && !isDestroyed()) completeSelection(selected);
        }
    };

    private boolean isDark() {
        return themeMode == AppTheme.MODE_DARK;
    }

    private void toggleThemeMode() {
        themeMode = isDark() ? AppTheme.MODE_LIGHT : AppTheme.MODE_DARK;
        getSharedPreferences(PREF_UI, MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, themeMode).apply();
        themeButton.setDark(isDark());
        if (themePreview) return;
        if (!activityResumed || themeTransition == null) {
            applyTheme();
            return;
        }
        float start = chromeLight;
        float end = isDark() ? 0f : 1f;
        themeTransition.start(painted -> applyThemeContentAndWait(isDark(), painted),
            value -> applyThemeChrome(start + (end - start) * value));
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTheme();
    }

    private void updateSystemBars(boolean dark) {
        Window window = getWindow();
        if (window == null) return;
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
        themePreview = false;
        if (themeTransition != null) themeTransition.cancel();
        applyThemeChrome(isDark() ? 0f : 1f);
        if (themeButton != null) themeButton.setDark(isDark());
        applyThemeContent(isDark());
    }

    private void applyThemeChrome(float light) {
        boolean darkInk = light >= .5f;
        boolean changedInk = darkInk != (chromeLight >= .5f);
        chromeLight = light;
        int bg = blendThemeColor(AppTheme.bg(true), AppTheme.bg(false), light);
        int surface = blendThemeColor(AppTheme.surface(true), AppTheme.surface(false), light);
        if (rootLayout != null) rootLayout.setBackgroundColor(bg);
        if (headerLayout != null) headerLayout.setBackgroundColor(surface);
        if (headerDivider != null) {
            headerDivider.setBackgroundColor(blendThemeColor(AppTheme.divider(true), AppTheme.divider(false), light));
        }
        Window window = getWindow();
        window.setStatusBarColor(surface);
        window.setNavigationBarColor(bg);
        if (changedInk || light == 0f || light == 1f) {
            for (TextView label : titleLabels) {
                if (label != null) label.setTextColor(AppTheme.ink(!darkInk));
            }
            updateSystemBars(!darkInk);
        }
    }

    private static int blendThemeColor(int dark, int light, float amount) {
        return Color.rgb(
            Math.round(Color.red(dark) + (Color.red(light) - Color.red(dark)) * amount),
            Math.round(Color.green(dark) + (Color.green(light) - Color.green(dark)) * amount),
            Math.round(Color.blue(dark) + (Color.blue(light) - Color.blue(dark)) * amount));
    }

    private void applyThemeContent(boolean dark) {
        int bg = AppTheme.bg(dark);
        int surface = AppTheme.surface(dark);
        int ink = AppTheme.ink(dark);
        int muted = AppTheme.muted(dark);

        if (viewport != null) viewport.setBackgroundColor(bg);
        if (browserFrame != null) browserFrame.setBackgroundColor(bg);
        if (pinduoduoFrame != null) pinduoduoFrame.setBackgroundColor(bg);

        if (transitionPanel != null) transitionPanel.setDark(dark);

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
        if (popupInlineWorkflow) {
            syncInlineTheme(popupView, dark);
            syncInlineTheme(phoneAddView, dark);
        }
        TaobaoLoginTheme.apply(browser, dark);
        TaobaoLoginTheme.apply(popupView, dark);
        TaobaoLoginTheme.apply(phoneAddView, dark);
        if (pddPage != null) pddPage.setDark(dark);
        if (jdPage != null) jdPage.setDark(dark);
        if (pickupPage != null) pickupPage.setDark(dark);
        if (cainiaoPage != null) cainiaoPage.setDark(dark);
        if (bottomBar != null) bottomBar.setDark(dark);
        updateNavigationEnabled();
    }

    private void applyThemeContentAndWait(boolean dark, Runnable painted) {
        applyThemeContent(dark);
        // Keep the captured source palette until the visible WebView, including
        // official auth frames, has committed the target palette to its surface.
        if (selected == TAB_TAOBAO && browser != null) {
            TaobaoLoginTheme.afterPaint(browser, dark, painted);
        } else viewport.postOnAnimation(painted);
    }

    private void logoutPage(int page) {
        if (selected != page || !signedIn[page] || SiteDataClearer.isBusy() || switchAnimation != null || pageDragging) return;
        if (!SiteDataClearer.isSupported()) {
            android.widget.Toast.makeText(this, "请先更新 Android System WebView / Chrome，再退出登录",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        SiteDataClearer.start(page);
    }

    private void updateNavigationEnabled() {
        boolean busy = SiteDataClearer.isBusy();
        for (Button tab : navigation) if (tab != null) tab.setEnabled(!busy);
        if (viewport != null) viewport.setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0);
        if (bottomBar != null) bottomBar.setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0);
    }

    private void setSignedIn(int page, boolean value) {
        if (value && SiteDataClearer.isBusy()) return;
        if (page >= 0 && page < PAGE_COUNT) {
            if (signedIn[page] != value) parcelSyncGeneration++;
            signedIn[page] = value;
            if (pickupPage != null && (page == TAB_TAOBAO || page == TAB_PDD) && !value) {
                pickupPage.updateExternalParcels(page == TAB_TAOBAO ? PickupSummaryPage.PROVIDER_TB : PickupSummaryPage.PROVIDER_PDD, false, null);
            }
            if (value && returnToPickupFromTab == page) {
                returnToPickupFromTab = -1;
                if (selected == page) scanHandler.postDelayed(() -> { if (!isFinishing() && !isDestroyed()) selectPage(TAB_PICKUP); }, 450);
            }
            if (page == TAB_TAOBAO) {
                setTaobaoLoginRequired(!value);
                if (combinedDisplayed && browser != null) {
                    browser.evaluateJavascript("document.getElementById('hc-logout')?.toggleAttribute('hidden',"
                        + !value + ")", null);
                }
            }
        }
    }

    private void setTaobaoLoginRequired(boolean required) {
        if (cainiaoPage.isLoginRequired() == required) return;
        getSharedPreferences(PREF_UI, MODE_PRIVATE).edit().putBoolean(KEY_TAOBAO_LOGIN_REQUIRED, required).apply();
        cainiaoPage.setLoginRequired(required);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences ui = getSharedPreferences(PREF_UI, MODE_PRIVATE);
        themeMode = ui.getInt(KEY_THEME_MODE, 0);
        if (themeMode != AppTheme.MODE_LIGHT && themeMode != AppTheme.MODE_DARK) {
            themeMode = AppTheme.systemDefault(this);
            ui.edit().putInt(KEY_THEME_MODE, themeMode).apply();
        }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        WebView.setWebContentsDebuggingEnabled(
            (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        int restored = state == null ? -1 : tabIndexForKey(state.getString(KEY_LAST_TAB));
        selected = restored >= 0 ? restored : initialTab(getIntent());
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
        view.setBackground(background(primary ? AppTheme.ACCENT : AppTheme.elevated(dark), 12));
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
        int[] icons = {R.drawable.ic_cainiao_official, R.drawable.ic_taobao, R.drawable.ic_pinduoduo, R.drawable.ic_jd, R.drawable.ic_pickup};
        for (int i = 0; i < PAGE_COUNT; i++) {
            titleRows[i] = platformTitle(i, TAB_NAMES[i], icons[i]);
            titleViewport.addView(titleRows[i], new FrameLayout.LayoutParams(-1, -1));
        }
        header.addView(titleViewport, new LinearLayout.LayoutParams(0, dp(40), 1f));

        themeButton = new GlassThemeSwitch(this, dark);
        themeButton.setOnClickListener(v -> toggleThemeMode());
        themeButton.setPreview(new GlassThemeSwitch.Preview() {
            private boolean sourceDark;

            @Override public void begin() {
                if (themePreview || themeTransition == null || !activityResumed) return;
                sourceDark = isDark();
                themePreview = true;
                themeTransition.begin(painted -> applyThemeContentAndWait(!sourceDark, painted),
                    value -> applyThemeChrome(sourceDark ? value : 1f - value));
            }

            @Override public void blend(float light) {
                if (themePreview) themeTransition.seek(sourceDark ? light : 1f - light);
            }

            @Override public void finish() {
                if (themePreview) {
                    themePreview = false;
                    themeTransition.finish(painted -> {
                        applyThemeChrome(isDark() ? 0f : 1f);
                        themeButton.setDark(isDark());
                        applyThemeContentAndWait(isDark(), painted);
                    });
                }
            }
        });
        LinearLayout.LayoutParams themeLp = size(74, 44);
        themeLp.setMarginStart(dp(4));
        header.addView(themeButton, themeLp);
        root.addView(header);

        headerDivider = new View(this);
        headerDivider.setBackgroundColor(AppTheme.divider(dark));
        root.addView(headerDivider, size(-1, 1));

        viewport = new SwipeFrameLayout(this);
        viewport.setListener(pageSwipeListener());
        viewport.setCaptureSettlingTouches(true);
        viewport.setBackgroundColor(AppTheme.bg(dark));
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1f));

        browserFrame = new FrameLayout(this);
        browserFrame.setBackgroundColor(AppTheme.bg(dark));
        buildErrorPanel();
        buildTransitionPanel();
        viewport.addView(browserFrame, new FrameLayout.LayoutParams(-1, -1));

        pinduoduoFrame = new FrameLayout(this);
        pddPage = new PddPage(this, this::openPinduoduoIdentity, value -> setSignedIn(TAB_PDD, value),
            () -> logoutPage(TAB_PDD));
        pinduoduoFrame.addView(pddPage, new FrameLayout.LayoutParams(-1, -1));
        viewport.addView(pinduoduoFrame, new FrameLayout.LayoutParams(-1, -1));
        jdPage = new JdPage(this, value -> setSignedIn(TAB_JD, value), () -> logoutPage(TAB_JD));
        viewport.addView(jdPage, new FrameLayout.LayoutParams(-1, -1));
        cainiaoPage = new CainiaoPage(this, () -> selectPage(TAB_TAOBAO), () -> setSignedIn(TAB_TAOBAO, false));
        cainiaoPage.setLoginRequired(getSharedPreferences(PREF_UI, MODE_PRIVATE).getBoolean(KEY_TAOBAO_LOGIN_REQUIRED, false));
        viewport.addView(cainiaoPage, new FrameLayout.LayoutParams(-1, -1));
        pickupPage = new PickupSummaryPage(this, value -> setSignedIn(TAB_PICKUP, value),
            this::handlePickupExternalAction, this::updateNavigationEnabled);
        pickupPage.setLogoutRequest(this::logoutPickupProvider);
        viewport.addView(pickupPage, new FrameLayout.LayoutParams(-1, -1));
        platformPages = new View[] {cainiaoPage, browserFrame, pinduoduoFrame, jdPage, pickupPage};

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
            navigation[i].setContentDescription("切换到" + TAB_NAMES[i]);
            navigation[i].setOnClickListener(v -> selectPage(page));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabRow.addView(navigation[i], lp);
        }
        bottomBar.addView(tabRow, new FrameLayout.LayoutParams(-1, dp(48)));
        bottomBar.bindTabs(navigation);

        root.addView(bottomBar, size(-1, 64));
        themeTransition = new ThemeTransition(getWindow(), root, viewport);

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
        return row;
    }


    private void logoutPickupProvider(int provider) {
        if (selected != TAB_PICKUP || SiteDataClearer.isBusy() || provider < PickupSummaryPage.PROVIDER_JD
                || provider > PickupSummaryPage.PROVIDER_DY || !pickupPage.isSignedIn(provider)) return;
        if (!SiteDataClearer.isSupported()) {
            android.widget.Toast.makeText(this, "请先更新 Android System WebView / Chrome，再退出登录", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        clearingPickupProvider = provider;
        parcelSyncGeneration++;
        SiteDataClearer.startWithSites(TAB_PICKUP, LogoutSites.forPickupProvider(provider));
    }

    private void handlePickupExternalAction(int action) {
        if (action == -1) {
            new AlertDialog.Builder(this).setTitle("切换取件平台")
                .setItems(new String[]{"全部取件汇总", "淘宝", "拼多多", "京东订单", "顺丰速运（测试）", "抖音商城（测试）"}, (dialog, which) -> {
                    if (which == 0) { pickupPage.showSummary(); syncExternalParcels(); }
                    else if (which <= 2) handlePickupExternalAction(which - 1);
                    else pickupPage.openProvider(which - 1);
                }).show();
        } else if (action == -2) {
            refreshExternalParcels(TAB_TAOBAO); refreshExternalParcels(TAB_PDD);
        } else if (action == PickupSummaryPage.PROVIDER_TB || action == PickupSummaryPage.PROVIDER_PDD) {
            int tab = action == PickupSummaryPage.PROVIDER_TB ? TAB_TAOBAO : TAB_PDD;
            if (!signedIn[tab]) { returnToPickupFromTab = tab; selectPage(tab); }
            else { refreshExternalParcels(tab); pickupPage.showSummary(); }
        }
    }

    private void refreshExternalParcels(int tab) {
        if (tab == TAB_TAOBAO && browser != null && combinedDisplayed) {
            browser.evaluateJavascript("window.HandyCodeRefreshParcels && HandyCodeRefreshParcels()", null);
        } else if (tab == TAB_PDD) pddPage.refreshParcels();
        scanHandler.removeCallbacks(parcelSyncWatch);
        scanHandler.postDelayed(parcelSyncWatch, 600);
    }

    private void syncExternalParcels() {
        scanHandler.removeCallbacks(parcelSyncWatch);
        if (!activityResumed || pickupPage == null || selected != TAB_PICKUP || SiteDataClearer.isBusy()) return;
        final int expected = parcelSyncGeneration;
        if (browser != null && combinedDisplayed && signedIn[TAB_TAOBAO]) {
            browser.evaluateJavascript("window.HandyCodeParcelsSnapshot ? HandyCodeParcelsSnapshot() : null", value -> {
                if (expected != parcelSyncGeneration || selected != TAB_PICKUP || !signedIn[TAB_TAOBAO]) return;
                applyExternalSnapshot(PickupSummaryPage.PROVIDER_TB, value);
            });
        }
        if (signedIn[TAB_PDD]) pddPage.pollParcels(value -> {
            if (expected != parcelSyncGeneration || selected != TAB_PICKUP || !signedIn[TAB_PDD]) return;
            applyExternalSnapshot(PickupSummaryPage.PROVIDER_PDD, value);
        });
        scanHandler.postDelayed(parcelSyncWatch, 1500);
    }

    private void applyExternalSnapshot(int provider, String value) {
        if (!activityResumed || isFinishing() || isDestroyed() || pickupPage == null || value == null || value.length() > 65536 || "null".equals(value)) return;
        try {
            org.json.JSONObject snapshot = new org.json.JSONObject(value);
            if (snapshot.optBoolean("ready")) pickupPage.updateExternalParcels(provider, true, snapshot.optJSONArray("orders"));
        } catch (org.json.JSONException ignored) {}
    }

    private void applyKeyboardPadding(int keyboardBottom, int systemBottom) {
        if (viewport == null) return;
        int footer = bottomBar == null || bottomBar.getHeight() == 0 ? dp(64) : bottomBar.getHeight();
        int padding = Math.max(0, keyboardBottom - systemBottom - footer - dp(1));
        if (viewport.getPaddingBottom() == padding) return;
        boolean imeOpen = padding > 0;
        viewport.setSwipeEnabled(!imeOpen && !SiteDataClearer.isBusy());
        if (bottomBar != null) {
            bottomBar.setSwipeEnabled(!imeOpen && !SiteDataClearer.isBusy());
        }
        viewport.setPadding(0, 0, 0, padding);
        if (padding > 0 && selected == TAB_PDD) pddPage.post(pddPage::scrollFocusedInput);
        if (imeOpen && selected == TAB_JD) jdPage.post(jdPage::scrollFocusedInput);
        if (imeOpen && selected == TAB_PICKUP) pickupPage.post(pickupPage::scrollFocusedInput);
        if (imeOpen && selected == TAB_CAINIAO) cainiaoPage.post(cainiaoPage::scrollFocusedInput);
        final WebView web = taobaoInputView();
        if (padding > 0 && selected == TAB_TAOBAO && web != null) {
            web.post(() -> {
                if (isFinishing() || isDestroyed() || taobaoInputView() != web || selected != TAB_TAOBAO) return;
                web.evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.matches('input,textarea,[contenteditable=true]'))e.scrollIntoView({block:'center',inline:'nearest'});})()", null);
            });
        }
    }

    private WebView taobaoInputView() {
        if (phoneAddView != null && phoneAddView.getVisibility() == View.VISIBLE) return phoneAddView;
        if (popupInlineWorkflow && popupView != null && popupView.getVisibility() == View.VISIBLE) return popupView;
        return combinedDisplayed ? null : browser;
    }

    private void openTaobaoIdentity() {
        android.content.Intent link = new android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(UrlPolicy.TAOBAO_ENTRY));
        link.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
        link.setPackage(TAOBAO_PACKAGE);
        try {
            startActivity(link);
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            android.widget.Toast.makeText(this, "无法打开淘宝身份码，请确认已安装或更新淘宝 App",
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void openPinduoduoIdentity() {
        android.content.Intent direct = new android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(UrlPolicy.PDD_IDENTITY_APP_LINK));
        direct.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
        direct.setPackage(PDD_PACKAGE);
        try {
            startActivity(direct);
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            android.widget.Toast.makeText(this, "无法打开身份码，请先安装或更新拼多多 App",
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void buildTransitionPanel() {
        transitionPanel = new PickupLoadingView(this, false);
        transitionPanel.setDark(isDark());
        transitionPanel.setVisibility(View.GONE);
        browserFrame.addView(transitionPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    private void startShimmer() {
        transitionPanel.setAnimating(activityResumed && selected == TAB_TAOBAO && opened[TAB_TAOBAO]);
    }

    private void stopShimmer() {
        transitionPanel.setAnimating(false);
    }

    private void showTransition() {
        if (transitionPanel == null || combinedMarkup == null || taobaoLoadFailed) return;
        if (!taobaoLoginVisible) {
            taobaoLoad.begin();
            updateTaobaoDeadline();
        }
        transitionGeneration++;
        transitionRevealQueued = false;
        transitionPanel.animate().cancel();
        transitionPanel.stopMorph();
        if (transitionPanel.getVisibility() != View.VISIBLE) transitionPanel.resetGeometry();
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
        transitionPanel.animate().alpha(0f).setDuration(PickupLoadingView.FADE_DURATION).withEndAction(() -> {
            if (token == transitionGeneration) { stopShimmer(); transitionPanel.setVisibility(View.GONE); transitionPanel.setAlpha(1f); }
        }).start();
    }

    private void waitForMergedPresentation(WebView view, final int generation) {
        if (isFinishing() || isDestroyed() || generation != combineGeneration || browser != view || combinedMarkup == null) return;
        String ready = "(function(){var a=document.getElementById('identity-content'),b=document.getElementById('parcels-content');"
            + "if(!a||!b||!window.HandyPickupMotion)return null;"
            + "return {width:innerWidth,identity:a.getBoundingClientRect().height,parcels:b.getBoundingClientRect().height,"
            + "heading:document.querySelector('.hc-heading').getBoundingClientRect().height,ready:!!(a.dataset.ready&&b.dataset.ready),"
            + "settled:HandyPickupMotion.isSettled(a)&&HandyPickupMotion.isSettled(b)};})()";
        view.evaluateJavascript(ready, complete -> {
            if (generation != combineGeneration || browser != view || isFinishing() || isDestroyed()) return;
            boolean readyCards = false;
            try {
                org.json.JSONObject geometry = new org.json.JSONObject(complete);
                readyCards = geometry.getBoolean("ready");
                if (!transitionRevealQueued && transitionPanel.getVisibility() == View.VISIBLE
                        && (readyCards && geometry.optBoolean("settled") || SystemClock.elapsedRealtime() - transitionStarted >= 12000)) {
                    final int token = transitionGeneration;
                    transitionRevealQueued = true;
                    view.postVisualStateCallback(generation, new WebView.VisualStateCallback() {
                        @Override public void onComplete(long requestId) {
                            if (generation != combineGeneration || browser != view || token != transitionGeneration
                                    || isFinishing() || isDestroyed()) return;
                            transitionPanel.morphTo((float)geometry.optDouble("width"), (float)geometry.optDouble("identity"),
                                (float)geometry.optDouble("parcels"), (float)geometry.optDouble("heading"), () -> {
                                    if (generation == combineGeneration && browser == view && token == transitionGeneration)
                                        hideTransition(true);
                                });
                        }
                    });
                }
            } catch (org.json.JSONException notReady) { /* The merged document is still being prepared. */ }
            if (transitionRevealQueued || SystemClock.elapsedRealtime() - transitionStarted >= 12000) {
                stopTaobaoDeadline();
                if (!transitionRevealQueued && transitionPanel.getVisibility() == View.VISIBLE) hideTransition(true);
            } else view.postDelayed(() -> waitForMergedPresentation(view, generation), 200);
        });
    }

    private void updateTaobaoDeadline() {
        taobaoHandler.removeCallbacks(taobaoDeadline);
        long now = SystemClock.elapsedRealtime();
        if (activityResumed && selected == TAB_TAOBAO && popupView == null) taobaoLoad.resume(now);
        else taobaoLoad.pause(now);
        long delay = taobaoLoad.delay(now);
        if (delay >= 0) taobaoHandler.postDelayed(taobaoDeadline, delay);
    }

    private void stopTaobaoDeadline() {
        taobaoHandler.removeCallbacks(taobaoDeadline);
        taobaoLoad.stop();
    }

    private void resetTaobaoLoad() {
        stopTaobaoDeadline();
        taobaoLoadFailed = taobaoLoginVisible = false;
    }

    private void checkTaobaoDeadline() {
        if (taobaoLoad.delay(SystemClock.elapsedRealtime()) != 0) {
            updateTaobaoDeadline();
            return;
        }
        failTaobaoLoad("淘宝取件信息暂未加载完成",
            "请先在淘宝 App 确认已开通取件身份码，再点“重新打开网页”重试。重新打开会保留已有登录状态。", true);
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
        Button openIdentity = button("打开淘宝身份码", false);
        openIdentity.setTag("error_open_identity");
        openIdentity.setVisibility(View.GONE);
        openIdentity.setOnClickListener(v -> openTaobaoIdentity());
        LinearLayout.LayoutParams actionSize = size(-1, 48);
        actionSize.bottomMargin = dp(10);
        errorPanel.addView(openIdentity, actionSize);
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
        if (!opened[TAB_TAOBAO]) {
            transitionPanel.setVisibility(View.VISIBLE);
            transitionPanel.setAlpha(1f);
            transitionPanel.resetGeometry();
        }
        if (!opened[TAB_PDD]) pddPage.idlePage();
    }

    private SwipeFrameLayout.Listener pageSwipeListener() {
        return new SwipeFrameLayout.Listener() {
            @Override public boolean allowsSwipe() { return (selected != TAB_JD || !jdPage.blocksPageSwipe()) && (selected != TAB_PICKUP || pickupPage.getMode() == PickupSummaryPage.MODE_SUMMARY); }
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
        updateNavigationEnabled();
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
        int titleHeight = titleViewport.getHeight() > 0 ? titleViewport.getHeight() : dp(40);
        int nearest = Math.round(position);
        for (int i = 0; i < PAGE_COUNT; i++) {
            View page = platformPages[i];
            page.setTranslationX(switchMotion.offset(i, width));
            page.setVisibility(opened[i] || (moving && width > 0 && Math.abs(i - position) <= 1.25f) || i == nearest
                ? View.VISIBLE : View.INVISIBLE);
            page.setImportantForAccessibility(!moving && i == selected
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
        updateNavigationEnabled();
    }
    private void completeSelection(int target) {
        selectionInitialized = true;
        boolean changed = selected != target;
        selected = target;
        updateTaobaoDeadline();
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
        if (selected != TAB_JD) jdPage.pausePage();
        if (selected != TAB_PICKUP) { pickupPage.pausePage(); scanHandler.removeCallbacks(parcelSyncWatch); }
        if (selected != TAB_CAINIAO) cainiaoPage.pausePage();
        try {
            if (selected == TAB_TAOBAO) {
                pddPage.idlePage();
                if (combinedDisplayed && cainiaoPage.isLoginRequired()) loadCombined();
                else ensureBrowser();
                opened[TAB_TAOBAO] = true;
                if (browser != null) browser.onResume();
                if (transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
            } else if (selected == TAB_JD) {
                stopShimmer();
                pddPage.idlePage();
                jdPage.showPage();
                opened[TAB_JD] = true;
            } else if (selected == TAB_PICKUP) {
                stopShimmer(); pddPage.idlePage(); pickupPage.showPage(); opened[TAB_PICKUP] = true; syncExternalParcels();
            } else if (selected == TAB_CAINIAO) {
                stopShimmer();
                pddPage.idlePage();
                cainiaoPage.showPage();
                opened[TAB_CAINIAO] = true;
            } else {
                stopShimmer();
                pddPage.showPage();
                opened[TAB_PDD] = true;
            }
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this).setTitle("无法启动网页组件")
                .setMessage("请在手机系统或应用商店更新 Android System WebView / Chrome 后重试。")
                .setPositiveButton("知道了", (d, w) -> finish()).show();
        }
    }

    private void ensureBrowser() {
        if (browser != null) return;
        browser = createBrowser(false);
        browserFrame.addView(browser, 0, new FrameLayout.LayoutParams(-1, -1));
        loadCombined();
    }

    private void loadCombined() {
        transitionStarted = SystemClock.elapsedRealtime();
        combineGeneration++;
        resetTaobaoLoad();
        hideError();
        updateNavigationEnabled();
        combinedMarkup = null;
        try {
            AssetManager assets = getAssets();
            officialIdentityScript = WebAssets.readText(assets, "official-identity.js");
            taobaoLoginChromeScript = WebAssets.readText(assets, "taobao-login-chrome.js");
            phoneThemeScript = WebAssets.readText(assets, "phone-theme.js");
            combinedGateScript = WebAssets.readText(assets, "login-ready.js").trim();
            String rawHtml = WebAssets.readText(assets, "taobao.html");
            rawHtml = rawHtml.replace("<html lang=\"zh-CN\">", "<html lang=\"zh-CN\" data-hc-theme=\"" + (isDark() ? "dark" : "light") + "\">");
            combinedMarkup = rawHtml
                .replace("@@REFRESH_CONTROLLER@@", WebAssets.readText(assets, "refresh-controller.js"))
                .replace("@@OFFICIAL_IDENTITY@@", officialIdentityScript)
                .replace("@@PICKUP_MOTION@@", WebAssets.readText(assets, "pickup-motion.js"))
                .replace("@@IDENTITY_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_ENTRY))
                .replace("@@ADD_PACKAGE_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_ADD_PACKAGE_ENTRY))
                .replace("@@PARCELS_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_PARCELS_ENTRY));
            combinedDisplayed = false;
            showTransition();
            browser.loadUrl(UrlPolicy.TAOBAO_ENTRY);
        } catch (java.io.IOException error) {
            loadFailure(false, "无法读取本地取件页面，请重新安装应用后重试。");
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
        if (view != browser || taobaoLoadFailed || combinedDisplayed || combinedMarkup == null
                || combinedGateScript == null || !UrlPolicy.isAllowed(url)) return;
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
            + "document.open();document.write(" + org.json.JSONObject.quote(combinedMarkup)
            + ");document.close();"
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
        TaobaoLoginTheme.apply(web, isDark());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (UrlPolicy.LOCAL_LOGOUT_ACTION.equals(request.getUrl().toString())) {
                    if (request.isForMainFrame() && request.hasGesture() && view == browser && combinedDisplayed) {
                        view.post(() -> { if (view == browser && combinedDisplayed) logoutPage(TAB_TAOBAO); });
                    }
                    return true;
                }
                if (request.isForMainFrame() && rejectPhoneAddNavigation(view, request.getUrl().toString())) return true;
                if (UrlPolicy.isAllowed(request.getUrl().toString())) return false;
                if (request.isForMainFrame()) blockedNavigation();
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (UrlPolicy.LOCAL_LOGOUT_ACTION.equals(url)) return true;
                if (rejectPhoneAddNavigation(view, url)) return true;
                if (UrlPolicy.isAllowed(url)) return false;
                blockedNavigation();
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                TaobaoLoginTheme.pageStarted(view);
                if ("about:blank".equals(url) && popup) return;
                if (rejectPhoneAddNavigation(view, url)) { view.stopLoading(); return; }
                if (!UrlPolicy.isAllowed(url)) {
                    view.stopLoading();
                    blockedNavigation();
                    return;
                }
                if (view == phoneAddView) {
                    phoneAddPageStarted = true;
                } else if (!popup) {
                    if (view != browser || taobaoLoadFailed) return;
                    combinedDisplayed = false;
                    combineGeneration++;
                    taobaoLoginVisible = false;
                    updateNavigationEnabled();
                    if (combinedMarkup != null) showTransition();
                } else {
                    popupGeneration++;
                    if (popupView == view) {
                        // The transport only supplies the destination after the window is created.
                        // Keep it hidden until we know whether it belongs inline or in the login dialog.
                        popupInlineWorkflow = UrlPolicy.isTaobaoPhonePage(url) || UrlPolicy.isTaobaoAddPackagePage(url);
                    }
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                TaobaoLoginTheme.apply(view, isDark());
                prepareInlinePage(view, url);
                if (popup && !popupInlineWorkflow) installPresentationObserver(view);
                if (!popup) {
                    installPresentationObserver(view);
                    renderCombined(view, url);
                } else waitForPopupIdentity(view, ++popupGeneration);
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                TaobaoLoginTheme.apply(view, isDark());
                prepareInlinePage(view, url);
                if (!popup || !popupInlineWorkflow) installPresentationObserver(view);
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                if (combinedMarkup != null) {
                    if (popup) waitForPopupIdentity(view, ++popupGeneration);
                    else if (!combinedDisplayed) renderCombined(view, url);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) loadFailure(popup, "请检查网络后重试，或稍后重新打开网页。");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) loadFailure(popup, "官方页面返回 HTTP " + response.getStatusCode() + "，请稍后重试。");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                loadFailure(popup, "页面的安全连接验证失败。请检查手机日期、网络或稍后重试。");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                view.post(() -> recoverBrowser(view, popup));
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (TaobaoLoginTheme.acceptReady(web, message)) {
                    if (!popup && taobaoLoginVisible) revealOfficialLogin(web);
                    return true;
                }
                String value = message.message();
                if ((popup ? popupView != web || popupInlineWorkflow : browser != web) || combinedMarkup == null
                        || value == null || !value.startsWith("__HANDYCODE_PHASE__:")) return false;
                String phase = value.substring("__HANDYCODE_PHASE__:".length());
                if (popup) {
                    if ("login".equals(phase)) setSignedIn(TAB_TAOBAO, false);
                    else if ("ready".equals(phase) || "merged".equals(phase)) setSignedIn(TAB_TAOBAO, true);
                } else if (!taobaoLoadFailed) {
                    if ("login".equals(phase)) revealOfficialLogin(web);
                    else if (!combinedDisplayed && ("loading".equals(phase) || "identity-loading".equals(phase)
                            || "ready".equals(phase))) {
                        taobaoLoginVisible = false;
                        if ("ready".equals(phase)) setSignedIn(TAB_TAOBAO, true);
                        showTransition();
                        if ("ready".equals(phase)) renderCombined(web, web.getUrl());
                    } else if ("merged".equals(phase) && !combinedDisplayed) {
                        combinedDisplayed = true;
                        setSignedIn(TAB_TAOBAO, true);
                        waitForMergedPresentation(web, combineGeneration);
                    }
                }
                return true;
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message message) {
                if (!userGesture) return false;
                if (popup) {
                    if (view != popupView || !popupInlineWorkflow || phoneAddView != null
                            || !UrlPolicy.isTaobaoPhonePage(view.getUrl())) return false;
                    openPhoneAdd(message);
                    return true;
                }
                if (popupView != null) return false;
                openPopup(message);
                return true;
            }
            @Override public void onCloseWindow(WebView window) {
                if (window == phoneAddView) closePhoneAdd(true);
                else if (window == popupView) closePopup();
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.deny();
            }
        });
        return web;
    }

    private void openPopup(Message message) {
        popupInlineWorkflow = false;
        popupView = createBrowser(true);
        popupView.setVisibility(View.INVISIBLE);
        browserFrame.addView(popupView, new FrameLayout.LayoutParams(-1, -1));
        updateTaobaoDeadline();
        attachPopup(message, popupView);
    }

    private void prepareInlinePage(WebView view, String url) {
        if ((view != popupView && view != phoneAddView) || !UrlPolicy.isAllowed(url)) return;
        if (!UrlPolicy.isTaobaoPhonePage(url) && !UrlPolicy.isTaobaoAddPhonePage(url) && !UrlPolicy.isTaobaoAddPackagePage(url)) {
            revealInlinePage(view);
            return;
        }
        // In WebView the official quick menu is empty, but still reserves its header height.
        view.evaluateJavascript(phoneThemeScript + "\nHandyCodePhoneTheme.setDark(" + isDark() + ");"
            + "(function(){if(document.getElementById('hc-phone-spacing'))return;"
            + "var style=document.createElement('style');style.id='hc-phone-spacing';"
            + "style.textContent=\"#quick-menu-container:empty,#quick-menu-container:empty + div[class^='space--']{display:none!important}\";"
            + "(document.head||document.documentElement).appendChild(style);})()", ignored -> {
                if (url.equals(view.getUrl())) revealInlinePage(view);
            });
    }

    private void syncInlineTheme(WebView view, boolean dark) {
        if (view == null || phoneThemeScript == null) return;
        view.setBackgroundColor(AppTheme.bg(dark));
        view.evaluateJavascript(phoneThemeScript + "\nHandyCodePhoneTheme.setDark(" + dark + ");", null);
    }

    private void revealInlinePage(WebView view) {
        if (view == phoneAddView && phoneAddPageStarted) {
            popupView.setVisibility(View.INVISIBLE);
        } else if (view != popupView || phoneAddView != null && phoneAddView.getVisibility() == View.VISIBLE) {
            return;
        }
        if (browser != null) browser.setVisibility(View.INVISIBLE);
        view.setVisibility(View.VISIBLE);
    }

    private void closePopup() {
        disposePopup();
    }

    private void disposePopup() {
        if (popupView == null) return;
        popupGeneration++;
        final boolean inlineWorkflow = popupInlineWorkflow;
        closePhoneAdd(false);
        WebView closed = popupView;
        popupView = null; popupInlineWorkflow = false;
        closed.stopLoading();
        ((ViewGroup) closed.getParent()).removeView(closed);
        closed.destroy();
        updateTaobaoDeadline();
        if (browser != null) browser.setVisibility(View.VISIBLE);
        if (browser != null && combinedMarkup != null && !taobaoLoadFailed) {
            final WebView parent = browser;
            final int generation = combineGeneration;
            CookieManager.getInstance().flush();
            parent.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || browser != parent || generation != combineGeneration || popupView != null) return;
                if (inlineWorkflow && combinedDisplayed) parent.evaluateJavascript("window.HandyCodeRefreshParcels?.()", null);
                else if (combinedDisplayed) refreshAfterLogin();
                else parent.loadUrl(UrlPolicy.TAOBAO_ENTRY);
            }, 300);
        }
    }

    private void openPhoneAdd(Message message) {
        phoneAddView = createBrowser(true);
        phoneAddView.setVisibility(View.INVISIBLE);
        browserFrame.addView(phoneAddView, new FrameLayout.LayoutParams(-1, -1));
        attachPopup(message, phoneAddView);
    }

    private boolean rejectPhoneAddNavigation(WebView view, String url) {
        if (view != phoneAddView || phoneAddPageStarted || UrlPolicy.isTaobaoAddPhonePage(url)) return false;
        view.post(() -> { if (view == phoneAddView) closePhoneAdd(false); });
        return true;
    }

    private void closePhoneAdd(boolean reloadManager) {
        WebView child = phoneAddView;
        if (child == null) return;
        phoneAddView = null;
        boolean opened = phoneAddPageStarted;
        phoneAddPageStarted = false;
        child.stopLoading();
        ((ViewGroup) child.getParent()).removeView(child);
        child.destroy();
        popupView.setVisibility(View.VISIBLE);
        if (opened && reloadManager) {
            CookieManager.getInstance().flush();
            popupView.reload();
        }
    }

    private void attachPopup(Message message, WebView view) {
        WebView.WebViewTransport transport = (WebView.WebViewTransport) message.obj;
        transport.setWebView(view);
        message.sendToTarget();
    }

    private void waitForPopupIdentity(WebView view, final int generation) {
        if (isFinishing() || isDestroyed() || generation != popupGeneration || popupView != view
                || popupInlineWorkflow || combinedMarkup == null || combinedGateScript == null || !UrlPolicy.isAllowed(view.getUrl())) return;
        view.evaluateJavascript(officialIdentityScript + "\n" + combinedGateScript, ready -> {
            if (generation != popupGeneration || popupView != view || isFinishing() || isDestroyed()) return;
            if ("true".equals(ready) && sameIdentityPage(view.getUrl())) {
                CookieManager.getInstance().flush();
                closePopup();
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
                    if (popup ? generation != popupGeneration || view != popupView
                            : generation != combineGeneration || view != browser || combinedDisplayed) return;
                    if (!popup && "\"login\"".equals(status)) revealOfficialLogin(view);
                    view.postDelayed(next, 650);
                });
        } else view.postDelayed(next, 650);
    }

    private void installPresentationObserver(WebView view) {
        if (combinedMarkup == null || officialIdentityScript == null || !UrlPolicy.isAllowed(view.getUrl())) return;
        view.evaluateJavascript(taobaoLoginChromeScript + "\n" + officialIdentityScript + "\nHandyCodeOfficialIdentity.watch(document)", null);
    }

    private void revealOfficialLogin(WebView view) {
        if (browser != view || taobaoLoadFailed) return;
        if (!taobaoLoginVisible) {
            taobaoLoginVisible = true;
            stopTaobaoDeadline();
            setSignedIn(TAB_TAOBAO, false);
            final int waitingGeneration = combineGeneration;
            view.postDelayed(() -> {
                if (browser == view && waitingGeneration == combineGeneration && taobaoLoginVisible
                        && !TaobaoLoginTheme.ready(view) && !isFinishing() && !isDestroyed()) {
                    failTaobaoLoad("登录页暂未就绪", "请检查网络后重新打开网页。", false);
                }
            }, 12000);
        }
        if (!TaobaoLoginTheme.ready(view)) return;
        if (taobaoLoginChromeScript == null || transitionPanel.getVisibility() != View.VISIBLE) return;
        final int expected = combineGeneration;
        view.evaluateJavascript(taobaoLoginChromeScript, ignored -> {
            if (browser != view || expected != combineGeneration || isFinishing() || isDestroyed()) return;
            view.postVisualStateCallback(expected, new WebView.VisualStateCallback() {
                @Override public void onComplete(long requestId) {
                    if (browser == view && expected == combineGeneration && taobaoLoginVisible
                            && !isFinishing() && !isDestroyed()) hideTransition(false);
                }
            });
        });
    }

    private void loadFailure(boolean popup, String description) {
        if (popup) {
            new AlertDialog.Builder(this).setTitle("登录网页暂时无法打开")
                .setMessage(description).setPositiveButton("知道了", null).show();
        } else {
            failTaobaoLoad("网页暂时无法打开", description, false);
        }
    }

    private void failTaobaoLoad(String title, String detail, boolean offerIdentityLink) {
        if (taobaoLoadFailed) return;
        taobaoLoadFailed = true;
        taobaoLoginVisible = false;
        combineGeneration++;
        stopTaobaoDeadline();
        hideTransition(false);
        showError(title, detail, offerIdentityLink);
        updateNavigationEnabled();
    }

    private void recoverBrowser(WebView view, boolean popup) {
        if (popup) {
            if (view == popupView || view == phoneAddView) closePopup();
            return;
        }
        if (browser != view) return;
        failTaobaoLoad("网页组件已停止", "点击“重新打开网页”即可恢复。", false);
        browserFrame.removeView(view);
        view.destroy();
        browser = null;
    }

    private void blockedNavigation() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBlockedNotice < 2500) return;
        lastBlockedNotice = now;
        new AlertDialog.Builder(this).setTitle("无法打开此链接")
            .setMessage("此网页链接需要其他应用，或不在淘宝官方网页范围内。")
            .setPositiveButton("留在当前页面", null).show();
    }

    private void hideError() {
        View overlay = browserFrame.findViewWithTag("error_overlay");
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void showError(String title, String detail, boolean offerIdentityLink) {
        ((TextView) browserFrame.findViewWithTag("error_title")).setText(title);
        ((TextView) browserFrame.findViewWithTag("error_detail")).setText(detail);
        browserFrame.findViewWithTag("error_open_identity").setVisibility(offerIdentityLink ? View.VISIBLE : View.GONE);
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
        if (selected == TAB_TAOBAO && popupView != null) {
            if (phoneAddView != null) closePhoneAdd(true);
            else if (popupView.canGoBack()) popupView.goBack();
            else closePopup();
        } else if (selected == TAB_PICKUP && pickupPage.goBack()) {
            return;
        } else if (selected == TAB_JD && jdPage.goBack()) {
            return;
        } else if (selected == TAB_CAINIAO && cainiaoPage.goBack()) {
            return;
        } else if (selected == TAB_PDD && pddPage.goBack()) {
            return;
        } else if (selected == TAB_TAOBAO && browser != null && browser.canGoBack()) {
            if (taobaoLoadFailed) resetTaobaoLoad();
            hideError();
            browser.goBack();
        } else finish();
    }

    @Override public void onBackPressed() { goBack(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(KEY_LAST_TAB, TAB_KEYS[selected]);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        activityResumed = false;
        themePreview = false;
        if (themeTransition != null) themeTransition.cancel();
        updateTaobaoDeadline();
        ++scanGeneration;
        scanHandler.removeCallbacks(scanWatch);
        setScanBrightness(false);
        cancelSwitchAnimation(); pageDragging = false;
        switchMotion.commit(selected); renderSwitchPosition();
        stopShimmer();
        CookieManager.getInstance().flush();
        if (browser != null) browser.onPause();
        if (popupView != null) popupView.onPause();
        if (phoneAddView != null) phoneAddView.onPause();
        pddPage.pausePage();
        jdPage.pausePage();
        pickupPage.pausePage();
        scanHandler.removeCallbacks(parcelSyncWatch);
        cainiaoPage.pausePage();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateTaobaoDeadline();
        scanHandler.removeCallbacks(scanWatch);
        scanHandler.post(scanWatch);
        applyTheme();
        if (SiteDataClearer.isBusy()) { updateNavigationEnabled(); return; }
        if (browser != null) browser.onResume();
        if (popupView != null) popupView.onResume();
        if (phoneAddView != null) phoneAddView.onResume();
        pddPage.resumePage(selected == TAB_PDD);
        jdPage.resumePage(selected == TAB_JD);
        pickupPage.resumePage(selected == TAB_PICKUP);
        if (selected == TAB_PICKUP) syncExternalParcels();
        if (selected == TAB_CAINIAO && opened[TAB_CAINIAO]) cainiaoPage.showPage();
        if (!opened[selected]) { completeSelection(selected); return; }
        if (transitionPanel != null && transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
    }
    @Override protected void onDestroy() {
        if (themeTransition != null) themeTransition.cancel();
        if (themeButton != null) themeButton.destroyRenderer();
        scanHandler.removeCallbacks(scanWatch);
        ++scanGeneration;
        setScanBrightness(false);
        SiteDataClearer.removeObserver(logoutListener);
        cancelSwitchAnimation();
        stopShimmer();
        stopTaobaoDeadline();
        combineGeneration++;
        popupGeneration++;
        pddPage.destroyPage();
        jdPage.destroyPage();
        pickupPage.destroyPage();
        scanHandler.removeCallbacks(parcelSyncWatch);
        cainiaoPage.resetSessionView();
        closePopup();
        if (browser != null) {
            browserFrame.removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
