package com.fairycrane.handycode;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Multi-platform pickup code aggregator + official login switcher (TB, PDD, JD, SF, Douyin). */
final class PickupSummaryPage extends LinearLayout {
 static final int MODE_SUMMARY = -1;
 static final int PROVIDER_TB = 0, PROVIDER_PDD = 1, PROVIDER_JD = 2, PROVIDER_SF = 3, PROVIDER_DY = 4, PROVIDER_COUNT = 5;
 static final String[] PROVIDER_NAMES = {"淘宝", "拼多多", "京东", "顺丰", "抖音"};
 private static final int[] BADGE_COLORS = {0xFFFF5000, 0xFFE02E24, 0xFFE1251B, 0xFF2B313D, 0xFF1F2332};

 private final Consumer<Boolean> session;
 private final IntConsumer externalSwitch;
 private final Runnable onModeChanged;
 private final TextView status, pullBanner;
 private final LinearLayout tools, cards;
 private final FrameLayout stage, webContainer;
 private final ScrollView results;
 private float pullDownX, pullDownY, pullStartY, pullOffset;
 private int pullDirection;
 private final Button backToSummaryBtn, refreshBtn, switchSoftwareBtn;
 private final Button logoutButton;
 private IntConsumer logoutRequest = provider -> {};
 private final WebView[] webs = new WebView[PROVIDER_COUNT];
 private final String[] scripts = new String[PROVIDER_COUNT];
 private String providerTheme, jdLoginTheme;
 private final String[] lastSnapshots = {"", "", "", "", ""};
 private final boolean[] providerSignedIn = new boolean[PROVIDER_COUNT];
 private final boolean[] pendingAutoSummary = new boolean[PROVIDER_COUNT];
 private final boolean[] inFlight = new boolean[PROVIDER_COUNT];
 private final int[] generations = new int[PROVIDER_COUNT];
 private final long[] lastDySsoRedirect = new long[1];
 private final long[] lastJdLoginRedirect = new long[1];
 private final long[] lastJdFallback = new long[1];
 private static final String DY_DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
 private String dyMobileUa = "";
 private boolean jdPreferMyHome = false;
 private final String[] providerErrors = {"", "", "", "", ""};
 private final JSONArray[] providerOrders = new JSONArray[PROVIDER_COUNT];

 private int mode = MODE_SUMMARY;
 private boolean dark, paused = false, activeTab = false, destroyed, polling;
 private boolean dyLoginCompleted = false;
 private boolean sfPendingOpenLogin = false;
 private long dyLoginSuccessAt = 0L;
 private String lastSummarySignature = "";
 private final Runnable tick = new Runnable() {
  @Override public void run() {
   polling = false;
   if (paused || !activeTab || destroyed) return;
   sampleAll();
   schedule();
  }
 };

 PickupSummaryPage(Context context, Consumer<Boolean> session) {
  this(context, session, p -> {}, () -> {});
 }

 PickupSummaryPage(Context context, Consumer<Boolean> session, IntConsumer externalSwitch, Runnable onModeChanged) {
  super(context);
  this.session = session;
  this.externalSwitch = externalSwitch;
  this.onModeChanged = onModeChanged;
  setOrientation(VERTICAL);
  for (int i = 0; i < PROVIDER_COUNT; i++) providerOrders[i] = new JSONArray();

  tools = new LinearLayout(context);
  tools.setGravity(Gravity.CENTER_VERTICAL);
  tools.setPadding(dp(12), dp(6), dp(12), dp(6));

  status = text("汇总已登录平台的取件码；点「切换软件」选择官方登录页。", 12);
  LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, -2, 1f);
  statusLp.setMarginEnd(dp(8));
  tools.addView(status, statusLp);

  backToSummaryBtn = action("‹ 返回汇总", this::showSummary);
  LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-2, dp(34));
  backLp.setMarginEnd(dp(6));
  tools.addView(backToSummaryBtn, backLp);

  switchSoftwareBtn = action("切换软件", () -> externalSwitch.accept(-1));
  LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(-2, dp(34));
  swLp.setMarginEnd(dp(6));
  tools.addView(switchSoftwareBtn, swLp);

  refreshBtn = action("刷新全部", this::reloadActiveOrAll);
  tools.addView(refreshBtn, new LinearLayout.LayoutParams(-2, dp(34)));
  addView(tools);

  stage = new FrameLayout(context);
  addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

  webContainer = new FrameLayout(context);
  stage.addView(webContainer, new FrameLayout.LayoutParams(-1, -1));

  cards = new LinearLayout(context);
  cards.setOrientation(VERTICAL);
  cards.setPadding(dp(12), dp(8), dp(12), dp(88));
  results = new ScrollView(context) {
   @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
    if (handlePullTouch(this, ev)) return true;
    return super.dispatchTouchEvent(ev);
   }
   private void cancelSuperTouch(android.view.MotionEvent ev) {
    android.view.MotionEvent cancel = android.view.MotionEvent.obtain(ev);
    cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
    super.dispatchTouchEvent(cancel);
    cancel.recycle();
   }
   private boolean handlePullTouch(ScrollView self, android.view.MotionEvent ev) {
    if (mode != MODE_SUMMARY) return false;
    int action = ev.getActionMasked();
    if (action == android.view.MotionEvent.ACTION_DOWN) {
     pullDownX = ev.getRawX();
     pullDownY = ev.getRawY();
     pullStartY = pullDownY;
     pullDirection = 0;
     pullOffset = 0f;
     super.dispatchTouchEvent(ev);
     return true;
    }
    if (action == android.view.MotionEvent.ACTION_MOVE) {
     float rawX = ev.getRawX();
     float rawY = ev.getRawY();
     float dx = rawX - pullDownX;
     float dy = rawY - pullDownY;
     if (pullDirection == 0) {
      if (dy > 0 && self.canScrollVertically(-1)) {
       pullDownX = rawX;
       pullDownY = rawY;
       return false;
      }
      if (dy < 0) {
       pullDownX = rawX;
       pullDownY = rawY;
       return false;
      }
      if (dy > dp(8) && Math.abs(dy) > Math.abs(dx) * 1.15f && !self.canScrollVertically(-1)) {
       pullDirection = 1;
       pullStartY = rawY - dp(4);
       cancelSuperTouch(ev);
      }
     }
     if (pullDirection == 1) {
      float delta = rawY - pullStartY;
      if (delta >= 0) {
       float maxPull = dp(96);
       pullOffset = Math.min(maxPull, delta * 0.48f);
       cards.setTranslationY(pullOffset);
       boolean ready = pullOffset >= dp(36);
       FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pullBanner.getLayoutParams();
       lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
       lp.bottomMargin = 0;
       lp.topMargin = dp(10);
       pullBanner.setText(ready ? "↓ 松开立即刷新全部取件码" : "↓ 继续下拉刷新取件码");
       pullBanner.setLayoutParams(lp);
       pullBanner.setTextColor(ready ? Color.WHITE : AppTheme.ink(dark));
       pullBanner.setBackground(borderedBg(ready ? AppTheme.ACCENT : AppTheme.surface(dark),
        ready ? AppTheme.ACCENT : AppTheme.border(dark), 999));
       pullBanner.setVisibility(VISIBLE);
       pullBanner.bringToFront();
       if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
       return true;
      } else {
       pullDirection = 0;
       pullOffset = 0f;
       pullDownX = rawX;
       pullDownY = rawY;
       cards.setTranslationY(0f);
       pullBanner.setVisibility(GONE);
      }
     }
     return false;
    }
    if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
     if (pullDirection == 1) {
      boolean shouldTrigger = action == android.view.MotionEvent.ACTION_UP && pullOffset >= dp(36);
      pullDirection = 0;
      pullOffset = 0f;
      cards.animate().translationY(0f).setDuration(180).start();
      pullBanner.setVisibility(GONE);
      cancelSuperTouch(ev);
      if (shouldTrigger) {
       reloadActiveOrAll();
      }
      return true;
     }
    }
    return false;
   }
  };
  results.setFillViewport(true);
  results.setOverScrollMode(View.OVER_SCROLL_NEVER);
  results.addView(cards);
  stage.addView(results, new FrameLayout.LayoutParams(-1, -1));

  pullBanner = text("", 12);
  pullBanner.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  pullBanner.setGravity(Gravity.CENTER);
  pullBanner.setPadding(dp(16), dp(8), dp(16), dp(8));
  pullBanner.setVisibility(GONE);
  FrameLayout.LayoutParams pullLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
  pullLp.topMargin = dp(12);
  stage.addView(pullBanner, pullLp);

  scripts[PROVIDER_JD] = loadAsset(context, "pickup-jd-orders.js");
  scripts[PROVIDER_SF] = loadAsset(context, "sf-orders.js");
  scripts[PROVIDER_DY] = loadAsset(context, "dy-orders.js");
  providerTheme = loadAsset(context, "pickup-provider-theme.js");
  jdLoginTheme = loadAsset(context, "login-agreements.js") + "\n" + loadAsset(context, "jd-login-theme.js");
  logoutButton = action("退出当前平台登录", () -> {
   if (mode >= PROVIDER_JD && mode <= PROVIDER_DY && providerSignedIn[mode]) logoutRequest.accept(mode);
  });
  LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(-1, dp(44));
  logoutLp.setMargins(dp(20), dp(8), dp(20), dp(12));
  addView(logoutButton, logoutLp);
  updateModeUi();
  renderSummary(true);
  setDark(false);
 }

 private static boolean samePagePath(String a, String b) {
  if (a == null || b == null) return false;
  if (a.equals(b)) return true;
  try {
   android.net.Uri ua = android.net.Uri.parse(a);
   android.net.Uri ub = android.net.Uri.parse(b);
   String sa = ua.getScheme(), sb = ub.getScheme();
   String ha = ua.getHost(), hb = ub.getHost();
   String pa = ua.getPath(), pb = ub.getPath();
   if (pa == null || pa.isEmpty()) pa = "/";
   if (pb == null || pb.isEmpty()) pb = "/";
   return sa != null && sa.equalsIgnoreCase(sb)
    && ha != null && ha.equalsIgnoreCase(hb)
    && pa.equals(pb);
  } catch (Exception e) {
   return false;
  }
 }

 private String loadAsset(Context context, String name) {
  try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
   byte[] b = new byte[4096];
   int n;
   while ((n = in.read(b)) != -1) out.write(b, 0, n);
   return new String(out.toByteArray(), StandardCharsets.UTF_8);
  } catch (IOException e) {
   return "";
  }
 }

 private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

 private TextView text(String s, int size) {
  TextView t = new TextView(getContext());
  t.setText(s);
  t.setTextSize(size);
  return t;
 }

 private Button action(String s, Runnable run) {
  Button b = new Button(getContext());
  b.setText(s);
  b.setAllCaps(false);
  b.setTextSize(12);
  b.setMinWidth(0);
  b.setMinimumWidth(0);
  b.setMinHeight(0);
  b.setMinimumHeight(0);
  b.setPadding(dp(10), 0, dp(10), 0);
  b.setOnClickListener(v -> run.run());
  return b;
 }

 private GradientDrawable roundBg(int color, int radiusDp) {
  GradientDrawable d = new GradientDrawable();
  d.setColor(color);
  d.setCornerRadius(dp(radiusDp));
  return d;
 }

 private GradientDrawable borderedBg(int fill, int stroke, int radiusDp) {
  GradientDrawable d = new GradientDrawable();
  d.setColor(fill);
  d.setCornerRadius(dp(radiusDp));
  d.setStroke(dp(1), stroke);
  return d;
 }

 void setDark(boolean value) {
  dark = value;
  setBackgroundColor(AppTheme.bg(dark));
  tools.setBackgroundColor(AppTheme.surface(dark));
  status.setTextColor(AppTheme.muted(dark));
  for (Button b : new Button[]{backToSummaryBtn, switchSoftwareBtn, refreshBtn}) {
   b.setTextColor(b == backToSummaryBtn ? Color.WHITE : AppTheme.ink(dark));
   b.setBackground(roundBg(b == backToSummaryBtn ? AppTheme.ACCENT : AppTheme.elevated(dark), 10));
  }
  results.setBackgroundColor(AppTheme.bg(dark));
  for (int p=PROVIDER_JD;p<=PROVIDER_DY;p++) if (webs[p] != null) {
   webs[p].setBackgroundColor(AppTheme.bg(dark));
   applyProviderTheme(webs[p],p);
  }
  logoutButton.setTextColor(AppTheme.ink(dark));
  logoutButton.setBackground(borderedBg(AppTheme.bg(dark), AppTheme.border(dark), 10));
  renderSummary(true);
 }

 int getMode() { return mode; }
 private void applyProviderTheme(WebView web,int provider) {
  if (provider == PROVIDER_JD) web.evaluateJavascript(jdLoginTheme + "\nif(window.HandyCodeJdLoginTheme)HandyCodeJdLoginTheme.setDark("+dark+");", null);
  else web.evaluateJavascript(providerTheme + "\nif(window.HandyPickupProviderTheme)HandyPickupProviderTheme.setDark("+dark+");", null);
 }
 void setLogoutRequest(IntConsumer action) { logoutRequest = action; }

 boolean isSignedIn(int provider) {
  return provider >= 0 && provider < PROVIDER_COUNT && providerSignedIn[provider];
 }

 boolean isAnyManagedSignedIn() {
  if (mode >= PROVIDER_JD && mode <= PROVIDER_DY) return providerSignedIn[mode];
  return providerSignedIn[PROVIDER_JD] || providerSignedIn[PROVIDER_SF] || providerSignedIn[PROVIDER_DY];
 }

 int countFor(int provider) {
  if (provider < 0 || provider >= PROVIDER_COUNT || !providerSignedIn[provider]) return 0;
  JSONArray arr = providerOrders[provider];
  if (arr == null) return 0;
  int count = 0;
  for (int i = 0; i < arr.length(); i++) {
   JSONObject o = arr.optJSONObject(i);
   if (o != null && !o.optString("code").isEmpty()) count++;
  }
  return count;
 }

 void updateExternalParcels(int provider, boolean signedIn, JSONArray orders) {
  if (provider != PROVIDER_TB && provider != PROVIDER_PDD) return;
  boolean changed = providerSignedIn[provider] != signedIn;
  providerSignedIn[provider] = signedIn;
  JSONArray next = signedIn && orders != null ? orders : new JSONArray();
  String prevStr = providerOrders[provider] != null ? providerOrders[provider].toString() : "[]";
  String nextStr = next.toString();
  if (!prevStr.equals(nextStr)) {
   providerOrders[provider] = next;
   changed = true;
  }
  if (changed) {

   notifySessionState();
   renderSummary(false);
  }
 }

 private void notifySessionState() {
  logoutButton.setVisibility(mode >= PROVIDER_JD && mode <= PROVIDER_DY && providerSignedIn[mode] ? VISIBLE : GONE);
  session.accept(isAnyManagedSignedIn());
 }

 void warmBackground() {
  if (destroyed) return;
  paused = false;
  if (webContainer.getWidth() <= 0 || webContainer.getHeight() <= 0) {
   postDelayed(() -> {
    if (!destroyed) {
     ensureWeb(PROVIDER_JD);
     ensureWeb(PROVIDER_SF);
     ensureWeb(PROVIDER_DY);
    }
   }, 250);
   return;
  }
  ensureWeb(PROVIDER_JD);
  ensureWeb(PROVIDER_SF);
  ensureWeb(PROVIDER_DY);
 }

 void showPage() {
  if (destroyed) return;
  paused = false;
  activeTab = true;
  ensureWeb(PROVIDER_JD);
  ensureWeb(PROVIDER_SF);
  ensureWeb(PROVIDER_DY);
  for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) {
   if (webs[p] != null) webs[p].onResume();
  }
  updateModeUi();
  sampleAll();
  schedule();
 }

 void openProvider(int provider) {
  if (destroyed) return;
  if (provider == PROVIDER_TB || provider == PROVIDER_PDD) {
   externalSwitch.accept(provider);
   return;
  }
  if (provider < PROVIDER_JD || provider > PROVIDER_DY) {
   showSummary();
   return;
  }
  showPage();
  mode = provider;
  pendingAutoSummary[provider] = !providerSignedIn[provider];
  ensureWeb(provider);
  if (webs[provider] != null) {
   String curUrl = webs[provider].getUrl();
   if (provider == PROVIDER_JD) {
    if (!providerSignedIn[PROVIDER_JD] && !providerSignedIn[PROVIDER_JD]) {
     if (curUrl == null || !curUrl.contains("plogin.m.jd.com")) {
      webs[PROVIDER_JD].loadUrl(PickupUrlPolicy.LOGIN);
     }
    } else if (!providerErrors[PROVIDER_JD].isEmpty()
      || (jdPreferMyHome && curUrl != null && curUrl.contains("trade.m.jd.com"))) {
     providerErrors[PROVIDER_JD] = "";
     loadProviderEntry(PROVIDER_JD, webs[PROVIDER_JD]);
    }
   } else if (provider == PROVIDER_SF && !providerSignedIn[PROVIDER_SF]) {
    if (curUrl == null || !curUrl.contains("sf-express.com")) {
     sfPendingOpenLogin = true;
     webs[PROVIDER_SF].loadUrl(PickupUrlPolicy.SF_ORDERS);
    } else {
     webs[PROVIDER_SF].evaluateJavascript("if(window.HandySfOrders&&window.HandySfOrders.openLogin)window.HandySfOrders.openLogin();", null);
    }
   } else if (provider == PROVIDER_DY) {
    if (!providerSignedIn[PROVIDER_DY]) {
     if (curUrl == null || !curUrl.contains("douyin.com/user/self")) {
      loadDyUrl(webs[PROVIDER_DY], PickupUrlPolicy.DY_LOGIN);
     }
    } else if (curUrl == null || !curUrl.contains("haohuo.jinritemai.com")) {
     loadDyUrl(webs[PROVIDER_DY], PickupUrlPolicy.DY_ORDERS);
    }
   }
   webs[provider].requestFocus();
  }
  updateModeUi();
  onModeChanged.run();
  sampleProvider(provider);
 }

 void showSummary() {
  mode = MODE_SUMMARY;
  updateModeUi();
  renderSummary(false);
  onModeChanged.run();
  sampleAll();
 }

 private void updateModeUi() {
  if (mode == MODE_SUMMARY) {
   results.setVisibility(VISIBLE);
   backToSummaryBtn.setVisibility(GONE);
   switchSoftwareBtn.setVisibility(VISIBLE);
   refreshBtn.setText("刷新全部");
   for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) {
    if (webs[p] != null) webs[p].setVisibility(INVISIBLE);
   }
   updateSummaryStatusText();
  } else {
   results.setVisibility(GONE);
   backToSummaryBtn.setVisibility(VISIBLE);
   switchSoftwareBtn.setVisibility(GONE);
   refreshBtn.setText("刷新网页");
   for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) {
    if (webs[p] != null) webs[p].setVisibility(p == mode ? VISIBLE : INVISIBLE);
   }
   String pName = PROVIDER_NAMES[mode];
   if (!providerErrors[mode].isEmpty()) {
    status.setText(providerErrors[mode]);
   } else if (providerSignedIn[mode]) {
    status.setText("已登录" + pName + "（已识别 " + countFor(mode) + " 个取件码），可点「‹ 返回汇总」。");
   } else {
    status.setText("请在" + pName + "官方页面完成登录，登录完后将自动拉取并汇总。");
   }
  }
  notifySessionState();
 }

 private String entryUrl(int provider) {
  if (provider == PROVIDER_JD) return jdPreferMyHome ? PickupUrlPolicy.MY_HOME : PickupUrlPolicy.ORDERS;
  if (provider == PROVIDER_SF) return PickupUrlPolicy.SF_ORDERS;
  return PickupUrlPolicy.DY_ORDERS;
 }

 private void loadDyUrl(WebView web, String url) {
  if (web == null || url == null) return;
  boolean useDesktop = url.contains("douyin.com");
  String targetUa = useDesktop ? DY_DESKTOP_UA : (dyMobileUa != null && !dyMobileUa.isEmpty() ? dyMobileUa : DY_DESKTOP_UA);
  WebSettings ws = web.getSettings();
  if (!targetUa.equals(ws.getUserAgentString())) {
   ws.setUserAgentString(targetUa);
  }
  web.loadUrl(url);
 }

 private void loadProviderEntry(int provider, WebView web) {
  if (web == null) return;
  if (provider == PROVIDER_JD) {
   String target = entryUrl(PROVIDER_JD);
   Map<String, String> headers = new HashMap<>();
   headers.put("Referer", jdPreferMyHome ? "https://m.jd.com/" : PickupUrlPolicy.MY_HOME);
   web.loadUrl(target, headers);
  } else if (provider == PROVIDER_DY) {
   String cur = web.getUrl();
   boolean onDyLogin = cur != null && cur.contains("douyin.com");
   if (!providerSignedIn[PROVIDER_DY] && (onDyLogin || (!providerSignedIn[PROVIDER_DY] && mode == PROVIDER_DY))) {
    loadDyUrl(web, PickupUrlPolicy.DY_LOGIN);
   } else {

    loadDyUrl(web, PickupUrlPolicy.DY_ORDERS);
   }
  } else if (provider == PROVIDER_SF) {

   web.loadUrl(entryUrl(provider));
  } else {
   web.loadUrl(entryUrl(provider));
  }
 }

 @SuppressWarnings({"rawtypes", "unchecked"})

 private boolean allowedUrl(int provider, String url) {
  if (provider == PROVIDER_JD) return PickupUrlPolicy.allowed(url);
  if (provider == PROVIDER_SF) return PickupUrlPolicy.allowedSf(url);
  return PickupUrlPolicy.allowedDy(url);
 }

 private void ensureWeb(final int provider) {
  if (destroyed || webs[provider] != null || scripts[provider] == null || scripts[provider].isEmpty()) return;
  WebView web = new WebView(getContext());
  webs[provider] = web;
  web.setBackgroundColor(AppTheme.bg(dark));
  WebSettings s = web.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setAllowFileAccess(false);
  s.setAllowContentAccess(false);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  s.setSafeBrowsingEnabled(true);
  s.setSupportZoom(true);
  s.setBuiltInZoomControls(true);
  s.setDisplayZoomControls(false);
  s.setUseWideViewPort(true);
  s.setLoadWithOverviewMode(true);
  s.setJavaScriptCanOpenWindowsAutomatically(false);
  s.setSupportMultipleWindows(false);
  if (androidx.webkit.WebViewFeature.isFeatureSupported("DOCUMENT_START_SCRIPT")) {
   java.util.Set<String> origins = new java.util.HashSet<>();
   origins.add(provider == PROVIDER_JD ? "https://*.jd.com" : provider == PROVIDER_SF ? "https://*.sf-express.com" : "https://*.douyin.com");
   if (provider == PROVIDER_DY) {
    origins.add("https://*.jinritemai.com"); origins.add("https://*.snssdk.com"); origins.add("https://*.toutiao.com");
   }
   androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web,
       "window.__HC_PICKUP_DARK__="+dark+";\n"+providerTheme+"\n"+scripts[provider], origins);
  }
  String defaultUa = s.getUserAgentString();
  if (defaultUa != null && !defaultUa.isEmpty()) {
   String cleanUa = defaultUa.replace("; wv)", ")").replace("; wv;", ";").replace("Version/4.0 ", "");
   s.setUserAgentString(cleanUa);
   if (provider == PROVIDER_DY) dyMobileUa = cleanUa;
  }

  CookieManager.getInstance().setAcceptCookie(true);
  CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
  web.setWebChromeClient(new WebChromeClient() {
   @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
   @Override public boolean onConsoleMessage(ConsoleMessage cm) {
    if (provider == PROVIDER_DY && cm != null && "__HANDY_DY_LOGIN_SUCCESS__".equals(cm.message())) {
     onDySsoLoginSuccess();
     return true;
    }
    return super.onConsoleMessage(cm);
   }
  });
  web.setWebViewClient(new WebViewClient() {
   @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
    if (r == null || r.getUrl() == null) return true;
    String u = r.getUrl().toString();
    if (!r.isForMainFrame()) {
     return !(u.startsWith("https://") || u.startsWith("about:"));
    }
    if (provider == PROVIDER_DY && (u.startsWith("snssdk") || u.startsWith("aweme")
      || u.contains("douyin.com/home") || u.contains("douyin.com/downloadpage"))) {
     redirectToDyLoginIfNeeded(v);
     return true;
    }
    return !allowedUrl(provider, u);
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
    if (provider == PROVIDER_DY && url != null && (url.startsWith("snssdk") || url.startsWith("aweme")
      || url.contains("douyin.com/home") || url.contains("douyin.com/downloadpage"))) {
     redirectToDyLoginIfNeeded(v);
     return true;
    }
    return !allowedUrl(provider, url);
   }
   @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
    generations[provider]++;
    inFlight[provider] = false;
    if (provider == PROVIDER_DY && url != null && (url.contains("/passport/") || url.contains("douyin.com"))) {
    }
    if (!allowedUrl(provider, url)) {
     v.stopLoading();
     if (mode == provider) status.setText("已拦截非官方安全链接。");
     return;
    }
    v.evaluateJavascript(scripts[provider], null);
   }
   @Override public void onPageCommitVisible(WebView v, String url) {
    if (allowedUrl(provider, url)) {
     v.evaluateJavascript(scripts[provider], null);
     applyProviderTheme(v,provider);
    }
   }
   @Override public void onPageFinished(WebView v, String url) {
    CookieManager.getInstance().flush();
    if (allowedUrl(provider, url)) {
     applyProviderTheme(v,provider);
     v.evaluateJavascript(buildProviderEvalScript(provider), null);
     if (provider == PROVIDER_SF && sfPendingOpenLogin) {
      sfPendingOpenLogin = false;
      postDelayed(() -> {
       if (mode == PROVIDER_SF && webs[PROVIDER_SF] != null) {
        webs[PROVIDER_SF].evaluateJavascript("if(window.HandySfOrders&&window.HandySfOrders.openLogin)window.HandySfOrders.openLogin();", null);
       }
      }, 400);
     }
     sampleProvider(provider);
     postDelayed(() -> sampleProvider(provider), 900);
     postDelayed(() -> sampleProvider(provider), 2200);
    }
   }
   @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
    h.cancel();
   }
  });
  web.setVisibility(mode == provider ? VISIBLE : INVISIBLE);
  webContainer.addView(web, new FrameLayout.LayoutParams(-1, -1));
  loadProviderEntry(provider, web);
 }

 private void onDySsoLoginSuccess() {
  post(() -> {
   if (destroyed) return;
   long now = android.os.SystemClock.elapsedRealtime();
   if (dyLoginSuccessAt > 0 && now - dyLoginSuccessAt < 3000) return;
   dyLoginCompleted = true;
   dyLoginSuccessAt = now;

   CookieManager.getInstance().flush();
   providerErrors[PROVIDER_DY] = "";
   pendingAutoSummary[PROVIDER_DY] = true;
   if (mode == PROVIDER_DY) {
    status.setText("已完成抖音账号验证，正在拉取抖音订单确认登录状态…");
   }
   if (webs[PROVIDER_DY] != null) {
    loadDyUrl(webs[PROVIDER_DY], PickupUrlPolicy.DY_ORDERS);
   }
  });
 }

 private void onDySessionExpired() {
  if (destroyed) return;
  dyLoginCompleted = false;
  dyLoginSuccessAt = 0L;
  boolean wasSigned = providerSignedIn[PROVIDER_DY];
  providerSignedIn[PROVIDER_DY] = false;
  providerErrors[PROVIDER_DY] = "";
  providerOrders[PROVIDER_DY] = new JSONArray();
  pendingAutoSummary[PROVIDER_DY] = true;
  if (wasSigned) {

   notifySessionState();
   renderSummary(false);
  }
  if (mode == PROVIDER_DY && webs[PROVIDER_DY] != null) {
   status.setText("请在抖音官方页面完成登录，登录完后将自动拉取并汇总。");
   notifySessionState();
   redirectToDyLoginIfNeeded(webs[PROVIDER_DY]);
  }
 }

 private void redirectToJdLoginIfNeeded(WebView v) {
  if (v == null) return;
  String cur = v.getUrl();
  if (cur != null && cur.contains("plogin.m.jd.com")) return;
  long now = android.os.SystemClock.elapsedRealtime();
  if (now - lastJdLoginRedirect[0] < 5000) return;
  lastJdLoginRedirect[0] = now;
  pendingAutoSummary[PROVIDER_JD] = true;
  v.loadUrl(PickupUrlPolicy.LOGIN);
 }

 private void redirectToDyLoginIfNeeded(WebView v) {
  if (v == null) return;
  String cur = v.getUrl();
  if (cur != null && cur.contains("douyin.com/user/self")) return;
  long now = android.os.SystemClock.elapsedRealtime();
  if (now - lastDySsoRedirect[0] < 4000) return;
  lastDySsoRedirect[0] = now;
  pendingAutoSummary[PROVIDER_DY] = true;
  loadDyUrl(v, PickupUrlPolicy.DY_LOGIN);
 }

 private String buildProviderEvalScript(int provider) { return scripts[provider]; }

 private void schedule() {
  if (paused || !activeTab || destroyed || polling) return;
  polling = true;
  postDelayed(tick, 900);
 }

 private void sampleAll() {
  for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) sampleProvider(p);
 }

 private void sampleProvider(final int provider) {
  final WebView expected = webs[provider];
  final int token = generations[provider];
  if (expected == null || paused || destroyed || inFlight[provider] || !allowedUrl(provider, expected.getUrl())) return;
  inFlight[provider] = true;
  expected.evaluateJavascript(buildProviderEvalScript(provider), result -> {
   if (destroyed || webs[provider] != expected || generations[provider] != token) return;
   inFlight[provider] = false;
   if (result == null || "null".equals(result)) return;
   try {
    JSONObject data = new JSONObject(result);
    if (!samePagePath(data.optString("href"), expected.getUrl())) return;
    if (provider == PROVIDER_DY && data.optBoolean("ssoDone")) {
     onDySsoLoginSuccess();
     return;
    }
    if (provider == PROVIDER_DY && data.optBoolean("needSso")) {
     onDySessionExpired();
     return;
    }
    if (data.optBoolean("login")) {
     providerErrors[provider] = "";
     if (provider == PROVIDER_DY) {
      dyLoginCompleted = false;
      dyLoginSuccessAt = 0L;
     }
     boolean wasSigned = providerSignedIn[provider];
     providerSignedIn[provider] = false;
     providerOrders[provider] = new JSONArray();
     pendingAutoSummary[provider] = true;
     if (wasSigned) {

      notifySessionState();
      renderSummary(false);
     }
     if (mode == provider) {
      status.setText("请在" + PROVIDER_NAMES[provider] + "官方页面完成登录，登录完后将自动拉取并汇总。");
      if (provider == PROVIDER_JD) redirectToJdLoginIfNeeded(expected);
      else if (provider == PROVIDER_DY) redirectToDyLoginIfNeeded(expected);
      notifySessionState();
     }
     return;
    }
    if (provider == PROVIDER_JD && data.optBoolean("needFallback")) {
     providerErrors[PROVIDER_JD] = "";
     jdPreferMyHome = true;
     long now = android.os.SystemClock.elapsedRealtime();
     if (now - lastJdFallback[0] > 6000) {
      lastJdFallback[0] = now;

      loadProviderEntry(PROVIDER_JD, expected);
     }
     if (mode == PROVIDER_JD) {
      status.setText("正在通过京东个人中心同步待收货与取件码…");
     }
     return;
    }
    String errMsg = data.optString("error", "");
    if (!errMsg.isEmpty()) {
     providerErrors[provider] = errMsg;
     if (provider == PROVIDER_DY && !data.optBoolean("ready") && providerSignedIn[PROVIDER_DY]) {
      providerSignedIn[PROVIDER_DY] = false;
      providerOrders[PROVIDER_DY] = new JSONArray();

      notifySessionState();
      renderSummary(false);
     }
     if (mode == provider) {
      status.setText(errMsg);
     }
     return;
    }
    providerErrors[provider] = "";
    if (!data.optBoolean("ready")) return;
    boolean justLoggedIn = !providerSignedIn[provider] || pendingAutoSummary[provider];
    providerSignedIn[provider] = true;
    if (provider == PROVIDER_DY) dyLoginCompleted = true;

    JSONArray rows = data.optJSONArray("orders");
    if (rows != null && (rows.length() > 0 || providerOrders[provider] == null || providerOrders[provider].length() == 0 || data.optBoolean("ready"))) {
     providerOrders[provider] = rows;
    }
    String next = data.toString();
    if (!next.equals(lastSnapshots[provider]) || justLoggedIn) {
     lastSnapshots[provider] = next;

     notifySessionState();
     renderSummary(false);
    }
    if (mode == provider && pendingAutoSummary[provider]) {
     pendingAutoSummary[provider] = false;
     Toast.makeText(getContext(), "已完成" + PROVIDER_NAMES[provider] + "登录，正在汇总取件码", Toast.LENGTH_SHORT).show();
     showSummary();
    } else if (mode == provider) {
     status.setText("已登录" + PROVIDER_NAMES[provider] + "（已识别 " + countFor(provider) + " 个取件码），可点「‹ 返回汇总」。");
    }
   } catch (JSONException ignored) {}
  });
 }

 private void updateSummaryStatusText() {
  if (mode != MODE_SUMMARY) return;
  int loggedInCount = 0;
  int totalCodes = 0;
  for (int p = 0; p < PROVIDER_COUNT; p++) {
   if (providerSignedIn[p]) {
    loggedInCount++;
    totalCodes += countFor(p);
   }
  }
  if (loggedInCount == 0) {
   status.setText("尚未登录取件平台；请点「切换软件」选择软件登录。");
  } else {
   status.setText("已登录 " + loggedInCount + " 个平台，共汇总 " + totalCodes + " 个待取件码。");
  }
 }

 private void renderSummary(boolean force) {
  updateSummaryStatusText();
  StringBuilder sig = new StringBuilder();
  sig.append(dark).append('|');
  for (int p = 0; p < PROVIDER_COUNT; p++) {
   sig.append(providerSignedIn[p]).append(':').append(providerOrders[p] != null ? providerOrders[p].toString() : "").append('|');
  }
  String nextSig = sig.toString();
  if (!force && nextSig.equals(lastSummarySignature)) return;
  lastSummarySignature = nextSig;

  cards.removeAllViews();
  int loggedInCount = 0;
  int totalCodes = 0;
  for (int p = 0; p < PROVIDER_COUNT; p++) {
   if (providerSignedIn[p]) {
    loggedInCount++;
    totalCodes += countFor(p);
   }
  }

  // Platform status & quick login card
  LinearLayout statusCard = new LinearLayout(getContext());
  statusCard.setOrientation(VERTICAL);
  statusCard.setPadding(dp(14), dp(14), dp(14), dp(14));
  statusCard.setBackground(borderedBg(AppTheme.surface(dark), AppTheme.border(dark), 16));

  LinearLayout headRow = new LinearLayout(getContext());
  headRow.setGravity(Gravity.CENTER_VERTICAL);
  TextView headTitle = text("各软件登录与同步状态", 15);
  headTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  headTitle.setTextColor(AppTheme.ink(dark));
  headRow.addView(headTitle, new LinearLayout.LayoutParams(0, -2, 1f));
  TextView headHint = text("点击软件可登录/查看", 12);
  headHint.setTextColor(AppTheme.muted(dark));
  headRow.addView(headHint);
  statusCard.addView(headRow);

  LinearLayout grid = new LinearLayout(getContext());
  grid.setOrientation(VERTICAL);
  grid.setPadding(0, dp(10), 0, 0);
  LinearLayout row1 = new LinearLayout(getContext());
  row1.setOrientation(HORIZONTAL);
  LinearLayout row2 = new LinearLayout(getContext());
  row2.setOrientation(HORIZONTAL);
  row2.setPadding(0, dp(8), 0, 0);
  for (int p = 0; p < PROVIDER_COUNT; p++) {
   final int provider = p;
   boolean signed = providerSignedIn[p];
   int cnt = countFor(p);
   TextView chip = text((signed ? "● " : "○ ") + PROVIDER_NAMES[p] + (signed ? " (" + cnt + ")" : " 去登录"), 12);
   chip.setGravity(Gravity.CENTER);
   chip.setSingleLine(true);
   chip.setTypeface(Typeface.DEFAULT, signed ? Typeface.BOLD : Typeface.NORMAL);
   chip.setTextColor(signed ? AppTheme.accentText(dark) : AppTheme.muted(dark));
   chip.setPadding(dp(8), dp(8), dp(8), dp(8));
   chip.setBackground(roundBg(AppTheme.elevated(dark), 12));
   chip.setOnClickListener(v -> openProvider(provider));
   LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
   if (p < 3) {
    if (p < 2) clp.setMarginEnd(dp(8));
    row1.addView(chip, clp);
   } else {
    if (p == 3) clp.setMarginEnd(dp(8));
    row2.addView(chip, clp);
   }
  }
  grid.addView(row1, new LinearLayout.LayoutParams(-1, -2));
  grid.addView(row2, new LinearLayout.LayoutParams(-1, -2));
  statusCard.addView(grid);

  LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
  cardLp.bottomMargin = dp(12);
  cards.addView(statusCard, cardLp);

  // Render aggregated pickup code cards across all 5 platforms
  for (int p = 0; p < PROVIDER_COUNT; p++) {
   if (!providerSignedIn[p] || providerOrders[p] == null) continue;
   JSONArray rows = providerOrders[p];
   for (int i = 0; i < rows.length(); i++) {
    JSONObject o = rows.optJSONObject(i);
    if (o == null) continue;
    String codeVal = o.optString("code", "").trim();
    if (codeVal.isEmpty()) continue;
    addPickupCard(p, o.optString("name", PROVIDER_NAMES[p] + "包裹"), codeVal,
     o.optString("status", "待取件"), o.optString("detail", ""));
   }
  }

  if (totalCodes == 0) {
   addInfoCard(
    loggedInCount == 0 ? "暂未登录任何软件" : "暂无待取件码",
    loggedInCount == 0
     ? "点击左上角「切换软件」或上方软件标签，即可登录淘宝、拼多多、京东、顺丰或抖音。登录完成后会自动拉取取件码并汇总在此处。"
     : "当前已登录的软件中没有待取包裹或订单未返回取件码。可下拉页面或点击右上角「刷新全部」重新拉取，也可点击左上角切换其他软件登录。"
   );
  }

  TextView pullFooter = text("↓ 下拉页面即可刷新全部取件码", 11);
  pullFooter.setGravity(Gravity.CENTER);
  pullFooter.setTextColor(AppTheme.muted(dark));
  pullFooter.setPadding(0, dp(6), 0, dp(8));
  cards.addView(pullFooter, new LinearLayout.LayoutParams(-1, -2));
 }

 private void addPickupCard(int provider, String title, String pickupCode, String statusText, String detailText) {
  LinearLayout card = new LinearLayout(getContext());
  card.setOrientation(VERTICAL);
  card.setPadding(dp(16), dp(14), dp(16), dp(14));
  card.setBackground(borderedBg(AppTheme.surface(dark), AppTheme.border(dark), 16));

  LinearLayout topRow = new LinearLayout(getContext());
  topRow.setGravity(Gravity.CENTER_VERTICAL);

  TextView badge = text(PROVIDER_NAMES[provider], 11);
  badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  badge.setTextColor(Color.WHITE);
  badge.setPadding(dp(7), dp(2), dp(7), dp(2));
  badge.setBackground(roundBg(BADGE_COLORS[provider], 6));
  LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
  badgeLp.setMarginEnd(dp(8));
  topRow.addView(badge, badgeLp);

  TextView heading = text(title, 15);
  heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  heading.setTextColor(AppTheme.ink(dark));
  heading.setSingleLine(true);
  topRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

  if (!statusText.isEmpty()) {
   TextView stView = text(statusText, 12);
   stView.setTextColor(AppTheme.muted(dark));
   topRow.addView(stView);
  }
  card.addView(topRow);

  TextView codeView = text("取件码：" + pickupCode, 20);
  codeView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  codeView.setTextColor(AppTheme.accentText(dark));
  codeView.setPadding(0, dp(10), 0, dp(4));
  codeView.setTextIsSelectable(true);
  card.addView(codeView);

  if (!detailText.isEmpty()) {
   TextView detail = text(detailText, 13);
   detail.setTextColor(AppTheme.muted(dark));
   detail.setPadding(0, dp(4), 0, 0);
   detail.setTextIsSelectable(true);
   card.addView(detail);
  }

  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
  lp.bottomMargin = dp(12);
  cards.addView(card, lp);
 }

 private void addInfoCard(String title, String body) {
  LinearLayout card = new LinearLayout(getContext());
  card.setOrientation(VERTICAL);
  card.setPadding(dp(16), dp(16), dp(16), dp(16));
  card.setBackground(borderedBg(AppTheme.surface(dark), AppTheme.border(dark), 16));
  TextView heading = text(title, 16);
  heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
  heading.setTextColor(AppTheme.ink(dark));
  card.addView(heading);
  TextView detail = text(body, 14);
  detail.setTextColor(AppTheme.muted(dark));
  detail.setPadding(0, dp(8), 0, 0);
  card.addView(detail);
  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
  lp.bottomMargin = dp(12);
  cards.addView(card, lp);
 }

 void reloadActiveOrAll() {
  showPage();
  if (mode >= PROVIDER_JD && mode <= PROVIDER_DY) {
   reloadProvider(mode);
  } else {
   for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) reloadProvider(p);
   externalSwitch.accept(-2); // Signal MainActivity to also refresh TB & PDD parcels
   Toast.makeText(getContext(), "正在刷新全部已登录平台的取件码…", Toast.LENGTH_SHORT).show();
  }
 }

 void reloadProvider(int provider) {
  if (provider < PROVIDER_JD || provider > PROVIDER_DY) return;
  generations[provider]++;
  inFlight[provider] = false;
  lastSnapshots[provider] = "";
  providerErrors[provider] = "";
  if (webs[provider] != null) loadProviderEntry(provider, webs[provider]);
 }

 void resetProviderSession(int provider) {
  if (provider < PROVIDER_JD || provider > PROVIDER_DY) return;
  if (provider == PROVIDER_JD) {
   jdPreferMyHome = false;
   lastJdFallback[0] = 0L;
  } else if (provider == PROVIDER_SF) {

  } else if (provider == PROVIDER_DY) {
   dyLoginCompleted = false;
   dyLoginSuccessAt = 0L;

  }
  generations[provider]++;
  inFlight[provider] = false;
  lastSnapshots[provider] = "";
  providerErrors[provider] = "";
  providerSignedIn[provider] = false;
  pendingAutoSummary[provider] = false;
  providerOrders[provider] = new JSONArray();

  if (webs[provider] != null) {
   webs[provider].stopLoading();
   webs[provider].setWebViewClient(new WebViewClient());
   webContainer.removeView(webs[provider]);
   webs[provider].destroy();
   webs[provider] = null;
  }
  notifySessionState();
  renderSummary(true);
 }

 void resetSessionView() {
  pausePage();
  for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) resetProviderSession(p);
  mode = MODE_SUMMARY;
  updateModeUi();
  renderSummary(true);
 }

 void idlePage() {
  activeTab = false;
  removeCallbacks(tick);
  polling = false;
 }

 void pausePage() {
  paused = true;
  idlePage();
  for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) {
   if (webs[p] != null) webs[p].onPause();
  }
 }

 void resumePage(boolean active) {
  if (!active) { pausePage(); return; }
  paused = false;
  for (int p = PROVIDER_JD; p <= PROVIDER_DY; p++) {
   if (webs[p] != null) webs[p].onResume();
  }
  if (active) showPage();
  else idlePage();
 }

 void scrollFocusedInput() {
  if (mode >= PROVIDER_JD && mode <= PROVIDER_DY && webs[mode] != null) {
   webs[mode].evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.matches('input,textarea'))e.scrollIntoView({block:'center'});})()", null);
  }
 }

 boolean goBack() {
  if (mode >= PROVIDER_JD && mode <= PROVIDER_DY) {
   WebView w = webs[mode];
   if (w != null && w.canGoBack()) {
    w.goBack();
    return true;
   }
   showSummary();
   return true;
  }
  return false;
 }

 void destroyPage() {
  resetSessionView();
  destroyed = true;
 }
}
