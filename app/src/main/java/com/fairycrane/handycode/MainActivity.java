package com.fairycrane.handycode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int INK = 0xff242b37, MUTED = 0xff697386;
    private static final int BG = 0xfff7f8fb, ACCENT = 0xffeb6238;
    private static final String PDD_PACKAGE = "com.xunmeng.pinduoduo";
    private static final int TAB_TAOBAO = 0, TAB_PDD = 1;
    private WebView browser;
    private final Button[] navigation = new Button[2];
    private FrameLayout browserFrame, pinduoduoFrame;
    private PddPage pddPage;
    private String combinedMarkup;
    private String combinedGateScript;
    private String officialIdentityScript;
    private String taobaoLoginChromeScript;
    private int combineGeneration;
    private int popupGeneration;
    private boolean combinedDisplayed;
    private int selected = 0;
    private SwipeFrameLayout viewport;
    private View bottomNavigation;
    private FrameLayout titleViewport;
    private final View[] titleRows = new View[2];
    private View tabIndicator;
    private Button logoutButton;
    private android.graphics.drawable.Drawable logoutIcon;
    private final boolean[] signedIn = new boolean[2];
    private SwitchMotion switchMotion;
    private android.animation.ValueAnimator switchAnimation;
    private boolean selectionInitialized, pageDragging, activityResumed;
    private final boolean[] opened = new boolean[2];
    private float dragStart;
    private View transitionPanel;
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
            opened[page] = false;
            signedIn[page] = false;
            if (page == TAB_PDD) pddPage.resetSessionView();
            else {
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
                "退出未完成，请检查 WebView 更新后重试", android.widget.Toast.LENGTH_LONG).show();
            if (activityResumed && !isFinishing() && !isDestroyed()) completeSelection(selected);
        }
    };

    private void logoutCurrentPage() {
        if (!signedIn[selected] || SiteDataClearer.isBusy() || switchAnimation != null || pageDragging) return;
        if (!SiteDataClearer.isSupported()) {
            android.widget.Toast.makeText(this, "请先更新 Android System WebView / Chrome，再退出登录",
                android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // Capture the settled platform; clearing must never follow a later tab.
        SiteDataClearer.start(selected);
    }

    private void updateLogoutControls() {
        if (logoutButton == null) return;
        boolean busy = SiteDataClearer.isBusy();
        boolean authenticated = signedIn[selected];
        boolean enabled = authenticated && !busy && !pageDragging && switchAnimation == null
            && switchMotion.position() == selected;
        logoutButton.setEnabled(enabled);
        logoutButton.setAlpha(!authenticated && !busy || enabled ? 1f : .45f);
        logoutButton.setText(busy ? "退出中" : authenticated ? "退出登录" : "未登录");
        logoutButton.setTextColor(authenticated ? INK : MUTED);
        logoutButton.setCompoundDrawablesRelative(null, null, authenticated || busy ? logoutIcon : null, null);
        String platform = selected == TAB_PDD ? "拼多多" : "淘宝";
        logoutButton.setContentDescription(busy ? "正在退出" + platform + "登录"
            : authenticated ? "退出" + platform + "登录" : platform + "未登录");
        for (Button tab : navigation) if (tab != null) tab.setEnabled(!busy);
        if (viewport != null) viewport.setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0);
        if (bottomNavigation != null) ((SwipeFrameLayout)bottomNavigation).setSwipeEnabled(!busy && viewport.getPaddingBottom() == 0);
    }

    private void setSignedIn(int page, boolean value) {
        if (value && SiteDataClearer.isBusy()) return;
        signedIn[page] = value;
        updateLogoutControls();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        int systemUi = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else systemUi |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        WebView.setWebContentsDebuggingEnabled(false);
        if (state != null) {
            int restored = state.getInt("selected", TAB_TAOBAO);
            selected = restored == 1 || restored == 2 ? TAB_PDD : TAB_TAOBAO;
        } else if ("pinduoduo".equals(getIntent().getStringExtra("provider"))) selected = TAB_PDD;
        switchMotion = new SwitchMotion(selected);
        buildUi();
        SiteDataClearer.observe(logoutListener);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        selectPage(selected);
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
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setBackground(background(primary ? ACCENT : 0xffedf0f5, 12));
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                // Only the content viewport avoids IME. The root and bottom
                // navigation keep their physical screen position under it.
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                applyKeyboardPadding(keyboard, bars.bottom);
                return insets;
            });
        }
        else {
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
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setMinimumHeight(dp(56));
        header.setBackgroundColor(Color.WHITE);
        titleViewport = new FrameLayout(this);
        titleViewport.setClipChildren(true);
        titleRows[0] = platformTitle("淘宝", R.drawable.ic_taobao);
        titleRows[1] = platformTitle("拼多多", R.drawable.ic_pinduoduo);
        for (View title : titleRows) titleViewport.addView(title, new FrameLayout.LayoutParams(-1, -1));
        header.addView(titleViewport, new LinearLayout.LayoutParams(0, dp(40), 1f));
        logoutButton = button("未登录", false);
        logoutButton.setTextSize(13);
        logoutButton.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x14000000), background(Color.TRANSPARENT, 10), null));
        logoutIcon = getDrawable(R.drawable.ic_logout);
        logoutIcon.setBounds(0, 0, dp(18), dp(18));
        logoutButton.setCompoundDrawablesRelative(null, null, logoutIcon, null);
        logoutButton.setCompoundDrawablePadding(dp(5));
        logoutButton.setMinWidth(0);
        logoutButton.setMinimumWidth(0);
        logoutButton.setOnClickListener(v -> logoutCurrentPage());
        header.addView(logoutButton, size(-2, 40));
        root.addView(header);

        viewport = new SwipeFrameLayout(this);
        viewport.setListener(swipeListener());
        viewport.setCaptureSettlingTouches(true);
        viewport.setBackgroundColor(Color.WHITE);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1f));
        browserFrame = new FrameLayout(this);
        browserFrame.setBackgroundColor(Color.WHITE);
        buildErrorPanel();
        buildTransitionPanel();
        viewport.addView(browserFrame, new FrameLayout.LayoutParams(-1, -1));
        pinduoduoFrame = new FrameLayout(this);
        pddPage = new PddPage(this, this::openPinduoduoIdentity, value -> setSignedIn(TAB_PDD, value));
        pinduoduoFrame.addView(pddPage, new FrameLayout.LayoutParams(-1, -1));
        viewport.addView(pinduoduoFrame, new FrameLayout.LayoutParams(-1, -1));

        View divider = new View(this);
        divider.setBackgroundColor(0xffe9edf3);
        root.addView(divider, size(-1, 1));
        SwipeFrameLayout bottomBar = new SwipeFrameLayout(this);
        bottomBar.setListener(swipeListener());
        bottomNavigation = bottomBar;
        bottomBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bottomBar.setBackgroundColor(Color.WHITE);
        tabIndicator = new View(this);
        tabIndicator.setBackground(background(0xffffeee6, 12));
        tabIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bottomBar.addView(tabIndicator, new FrameLayout.LayoutParams(1, dp(48)));
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] destinations = {"淘宝", "拼多多"};
        for (int i = 0; i < navigation.length; i++) {
            final int page = i;
            navigation[i] = button(destinations[i], false);
            navigation[i].setBackgroundColor(Color.TRANSPARENT);
            navigation[i].setStateListAnimator(null);
            navigation[i].setElevation(0);
            navigation[i].setContentDescription("切换到" + destinations[i]);
            navigation[i].setOnClickListener(v -> selectPage(page));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabRow.addView(navigation[i], lp);
        }
        bottomBar.addView(tabRow, new FrameLayout.LayoutParams(-1, dp(48)));
        root.addView(bottomBar);
        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        titleViewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        tabRow.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderSwitchPosition());
        bottomBar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (b - t != ob - ot) root.requestApplyInsets();
        });
        root.post(root::requestApplyInsets);
    }

    private View platformTitle(String name, int icon) {
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
        TextView title = label(name, 18, INK, true);
        title.setSingleLine(true);
        row.addView(title);
        return row;
    }

    private void applyKeyboardPadding(int keyboardBottom, int systemBottom) {
        if (viewport == null) return;
        int footer = bottomNavigation == null || bottomNavigation.getHeight() == 0
            ? dp(60) : bottomNavigation.getHeight();
        int padding = Math.max(0, keyboardBottom - systemBottom - footer - dp(1));
        if (viewport.getPaddingBottom() == padding) return;
        viewport.setSwipeEnabled(padding == 0 && !SiteDataClearer.isBusy());
        ((SwipeFrameLayout) bottomNavigation).setSwipeEnabled(padding == 0 && !SiteDataClearer.isBusy());
        viewport.setPadding(0, 0, 0, padding);
        if (padding > 0 && selected == TAB_PDD) pddPage.post(pddPage::scrollFocusedInput);
        if (padding > 0 && selected == TAB_TAOBAO && browser != null && !combinedDisplayed) {
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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(12), dp(10), dp(16));
        content.setBackgroundColor(BG);
        content.setClickable(true);
        content.setContentDescription("正在准备身份码和待取包裹");
        for (int i = 0; i < 2; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(background(Color.WHITE, 16));
            card.setPadding(dp(16), dp(18), dp(16), dp(18));
            TextView title = label(i == 0 ? "身份码" : "待取包裹", 16, INK, true);
            card.addView(title);
            LinearLayout placeholders = new LinearLayout(this);
            placeholders.setOrientation(LinearLayout.VERTICAL);
            placeholders.setGravity(Gravity.CENTER);
            for (int j = 0; j < 3; j++) {
                ShimmerBlock block = new ShimmerBlock(this, dp(j == 1 ? 12 : 6));
                shimmerBlocks.add(block);
                LinearLayout.LayoutParams shape = new LinearLayout.LayoutParams(
                    j == 1 ? -1 : dp(j == 0 ? 112 : 148), dp(j == 1 ? 64 : 12));
                shape.setMargins(dp(6), dp(12), dp(6), dp(12));
                placeholders.addView(block, shape);
            }
            card.addView(placeholders, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams section = new LinearLayout.LayoutParams(-1, 0, 1f);
            if (i == 0) section.bottomMargin = dp(14);
            content.addView(card, section);
        }
        TextView note = label("正在准备取件信息…", 12, MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(12), 0, 0);
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
        String ready = "(function(){var a=document.getElementById('identity-content'),b=document.getElementById('parcels-content');return !!(a&&b&&a.dataset.ready==='true'&&b.dataset.ready==='true');})()";
        view.evaluateJavascript(ready, complete -> {
            if (generation != combineGeneration || browser != view || isFinishing() || isDestroyed()) return;
            if ("true".equals(complete) || android.os.SystemClock.elapsedRealtime() - transitionStarted >= 12000) hideTransition(true);
            else view.postDelayed(() -> waitForMergedPresentation(view, generation), 200);
        });
    }

    private void buildErrorPanel() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout errorPanel = new LinearLayout(this);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setPadding(dp(28), dp(24), dp(28), dp(24));
        errorPanel.setBackgroundColor(Color.WHITE);
        TextView errorTitle = label("", 16, INK, true);
        errorTitle.setTag("error_title");
        errorTitle.setGravity(Gravity.CENTER);
        errorPanel.addView(errorTitle, size(-1, -2));
        TextView errorDetail = label("", 12, MUTED, false);
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
        int target = index == TAB_PDD ? TAB_PDD : TAB_TAOBAO;
        preparePreview();
        if (!selectionInitialized) {
            selectionInitialized = true;
            completeSelection(target);
        } else animateSwitch(target);
    }

    private void preparePreview() {
        if (!opened[TAB_TAOBAO]) {
            transitionPanel.setVisibility(View.VISIBLE);
            transitionPanel.setAlpha(1f);
            for (ShimmerBlock block : shimmerBlocks) block.setPhase(0f);
        }
        if (!opened[TAB_PDD]) pddPage.idlePage();
    }

    private SwipeFrameLayout.Listener swipeListener() {
        return new SwipeFrameLayout.Listener() {
            @Override public boolean isSettling() { return switchAnimation != null; }
            @Override public void onDragStarted() {
                cancelSwitchAnimation();
                preparePreview();
                pageDragging = true;
                dragStart = switchMotion.position();
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

    private void cancelSwitchAnimation() {
        android.animation.ValueAnimator old = switchAnimation;
        switchAnimation = null;
        if (old != null) old.cancel();
    }

    private void animateSwitch(final int target) {
        cancelSwitchAnimation();
        pageDragging = false;
        if (switchMotion.position() == target || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            completeSelection(target); return;
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
        if (switchMotion == null || viewport == null || tabIndicator == null) return;
        float position = switchMotion.position();
        boolean moving = position > 0f && position < 1f;
        float width = viewport.getWidth();
        View[] pages = {browserFrame, pinduoduoFrame};
        int titleHeight = titleViewport.getHeight() > 0 ? titleViewport.getHeight() : dp(40);
        for (int i = 0; i < 2; i++) {
            pages[i].setTranslationX(switchMotion.offset(i, width));
            // Keep visited WebViews attached and visible offscreen. Toggling
            // INVISIBLE/onPause discards Chromium's retained content layer and
            // can leave only the card headings visible during the next swipe.
            pages[i].setVisibility(opened[i] || (moving && width > 0) || i == Math.round(position) ? View.VISIBLE : View.INVISIBLE);
            pages[i].setImportantForAccessibility(!moving && i == selected ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            titleRows[i].setTranslationY(switchMotion.offset(i, titleHeight));
            titleRows[i].setAlpha(Math.max(0f, 1f - Math.abs(i - position)));
            titleRows[i].setImportantForAccessibility(!moving && i == selected ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            float weight = i == 0 ? 1f - position : position;
            navigation[i].setTextColor(blendColor(MUTED, 0xffbd451f, weight));
        }
        if (navigation[0].getWidth() > 0) {
            ViewGroup.LayoutParams shape = tabIndicator.getLayoutParams();
            if (shape.width != navigation[0].getWidth()) { shape.width = navigation[0].getWidth(); tabIndicator.setLayoutParams(shape); }
            tabIndicator.setTranslationX(navigation[0].getLeft() + (navigation[1].getLeft() - navigation[0].getLeft()) * position);
        }
        updateLogoutControls();
    }

    private int blendColor(int from, int to, float amount) {
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
            Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
            Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private void completeSelection(int target) {
        selectionInitialized = true;
        boolean changed = selected != target;
        selected = target;
        switchMotion.commit(target);
        for (int i = 0; i < navigation.length; i++) {
            boolean active = i == selected;
            navigation[i].setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            navigation[i].setSelected(active);
        }
        renderSwitchPosition();
        if (SiteDataClearer.isBusy()) return;
        if (changed) {
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(viewport.getWindowToken(), 0);
        }
        try {
            if (selected == TAB_TAOBAO) {
                pddPage.idlePage();
                ensureBrowser();
                opened[TAB_TAOBAO] = true;
                if (browser != null) browser.onResume();
                if (transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
            } else {
                stopShimmer();
                pddPage.showPage();
                opened[TAB_PDD] = true;
            }
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this).setTitle("无法启动网页组件")
                .setMessage("请在手机系统或应用商店更新 Android System WebView / Chrome 后重试。")
                .setPositiveButton("知道了", (d, w) -> finish()).show();
            return;
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
            combinedMarkup = readAssetText("taobao.html")
                .replace("@@REFRESH_CONTROLLER@@", readAssetText("refresh-controller.js"))
                .replace("@@OFFICIAL_IDENTITY@@", officialIdentityScript)
                .replace("@@IDENTITY_URL@@", android.text.TextUtils.htmlEncode(UrlPolicy.TAOBAO_ENTRY))
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
        // Keep the real official login/verification page visible until it has
        // produced a usable identity code; recheck inside the replacement too.
        String script = officialIdentityScript + "\n(function(){if(document.getElementById('handycode-merged'))return true;"
            + "if(!(" + combinedGateScript + "))return false;"
            + "document.open();document.write(" + org.json.JSONObject.quote(combinedMarkup)
            + ");document.close();return true;})()";
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
        web.setBackgroundColor(Color.WHITE);
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
                    installPresentationObserver(view);
                    renderCombined(view, url);
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
                    else if ("ready".equals(phase)) { showTransition(); renderCombined(web, web.getUrl()); }
                    else if ("merged".equals(phase)) { combinedDisplayed = true; waitForMergedPresentation(web, combineGeneration); }
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
        popupDialog = new Dialog(this);
        popupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.WHITE);
        TextView title = label("官方网页登录", 18, INK, true);
        title.setPadding(dp(16), dp(16), dp(16), dp(16));
        body.addView(title);
        TextView trustedDomains = label("淘宝官方网页登录", 12, MUTED, false);
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
                    view.postDelayed(next, 650);
                });
        } else view.postDelayed(next, 650);
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
                .setMessage(description).setPositiveButton("知道了", null).show();
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
        showError("网页组件已停止", "点击“重新打开网页”即可恢复。");
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
        cancelSwitchAnimation(); pageDragging = false;
        switchMotion.commit(selected); renderSwitchPosition();
        stopShimmer();
        CookieManager.getInstance().flush();
        if (browser != null) browser.onPause();
        pddPage.pausePage();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (SiteDataClearer.isBusy()) { updateLogoutControls(); return; }
        if (browser != null) browser.onResume();
        pddPage.resumePage(selected == TAB_PDD);
        if (!opened[selected]) { completeSelection(selected); return; }
        if (transitionPanel != null && transitionPanel.getVisibility() == View.VISIBLE) startShimmer();
    }
    @Override protected void onDestroy() {
        SiteDataClearer.removeObserver(logoutListener);
        cancelSwitchAnimation();
        stopShimmer();
        combineGeneration++;
        popupGeneration++;
        pddPage.destroyPage();
        if (popupDialog != null) popupDialog.dismiss();
        if (browser != null) {
            browserFrame.removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
