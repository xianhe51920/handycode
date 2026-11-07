package com.fairycrane.handycode;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.WebViewFeature;
import androidx.webkit.internal.WebViewGlueCommunicator;
import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;
import org.json.*;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Multi-platform pickup code aggregator + official login switcher (TB, PDD, JD, SF, Douyin). */
final class JdPage extends LinearLayout {
 static final int MODE_SUMMARY = -1;
 static final int PROVIDER_TB = 0, PROVIDER_PDD = 1, PROVIDER_JD = 2, PROVIDER_SF = 3, PROVIDER_DY = 4, PROVIDER_COUNT = 5;
 static final String[] PROVIDER_NAMES = {"淘宝", "拼多多", "京东", "顺丰", "抖音"};
 private static final int[] BADGE_COLORS = {0xFFFF5000, 0xFFE02E24, 0xFFE1251B, 0xFF2B313D, 0xFF1F2332};
 private static final String PREF_CACHE = "hc_pickup_cache";

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
 private final WebView[] webs = new WebView[PROVIDER_COUNT];
 private final String[] scripts = new String[PROVIDER_COUNT];
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
 private final List<String[]> dyApiHistory = new CopyOnWriteArrayList<>();

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

 JdPage(Context context, Consumer<Boolean> session) {
  this(context, session, p -> {}, () -> {});
 }

 JdPage(Context context, Consumer<Boolean> session, IntConsumer externalSwitch, Runnable onModeChanged) {
  super(context);
  this.session = session;
  this.externalSwitch = externalSwitch;
  this.onModeChanged = onModeChanged;
  setOrientation(VERTICAL);
  for (int i = 0; i < PROVIDER_COUNT; i++) providerOrders[i] = new JSONArray();
  loadCachedState();

  tools = new LinearLayout(context);
  tools.setGravity(Gravity.CENTER_VERTICAL);
  tools.setPadding(dp(12), dp(6), dp(12), dp(6));

  status = text("自动拉取并汇总已登录软件的取件码；点击左上角可切换软件登录。", 12);
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

  scripts[PROVIDER_JD] = loadAsset(context, "jd-orders.js");
  scripts[PROVIDER_SF] = loadAsset(context, "sf-orders.js");
  scripts[PROVIDER_DY] = loadAsset(context, "dy-orders.js");
  updateModeUi();
  renderSummary(true);
  setDark(false);
 }

 private void loadCachedState() {
  try {
   clearLegacyDyFakeCookie();
   SharedPreferences prefs = getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE);
   for (int p = 0; p < PROVIDER_COUNT; p++) {
    providerSignedIn[p] = prefs.getBoolean("signed_" + p, false);
    String raw = prefs.getString("orders_" + p, "[]");
    try {
     providerOrders[p] = new JSONArray(raw);
    } catch (JSONException e) {
     providerOrders[p] = new JSONArray();
    }
   }
   if (prefs.getInt("jd_risk_clean_ver", 0) < 50) {
    clearJdRiskCookiesOnly();
    prefs.edit().putInt("jd_risk_clean_ver", 50).apply();
   }
   if (prefs.getInt("dy_sf_auth_clean_ver", 0) < 51) {
    clearStaleDySnsCookies();
    if (!hasDyWebSessionCookie()) {
     providerSignedIn[PROVIDER_DY] = false;
     providerOrders[PROVIDER_DY] = new JSONArray();
     dyLoginCompleted = false;
    }
    prefs.edit()
     .putInt("dy_sf_auth_clean_ver", 51)
     .putBoolean("signed_" + PROVIDER_DY, providerSignedIn[PROVIDER_DY])
     .putString("orders_" + PROVIDER_DY, providerOrders[PROVIDER_DY].toString())
     .apply();
   }
   restoreSfSessionCookie();
   if (providerSignedIn[PROVIDER_JD] && !hasJdSessionCookie()) {
    providerSignedIn[PROVIDER_JD] = false;
    providerOrders[PROVIDER_JD] = new JSONArray();
   }
   if (providerSignedIn[PROVIDER_SF] && !hasSfSessionCookie()) {
    providerSignedIn[PROVIDER_SF] = false;
    providerOrders[PROVIDER_SF] = new JSONArray();
   }
   if (hasDyWebSessionCookie() || hasDySessionCookie(buildDyCookieHeader())) {
    dyLoginCompleted = providerSignedIn[PROVIDER_DY];
   } else if (providerSignedIn[PROVIDER_DY]) {
    providerSignedIn[PROVIDER_DY] = false;
    providerOrders[PROVIDER_DY] = new JSONArray();
    dyLoginCompleted = false;
   }
  } catch (Exception ignored) {}
 }

 private void clearLegacyDyFakeCookie() {
  try {
   CookieManager cm = CookieManager.getInstance();
   cm.setCookie("https://ec.snssdk.com", "hc_dy_logged=; Path=/; Max-Age=0; Secure; SameSite=None");
   cm.setCookie("https://is.snssdk.com", "hc_dy_logged=; Path=/; Max-Age=0; Secure; SameSite=None");
   cm.setCookie("https://is.snssdk.com", "hc_dy_logged=; Path=/; Domain=.snssdk.com; Max-Age=0; Secure; SameSite=None");
   cm.setCookie("https://haohuo.jinritemai.com", "hc_dy_logged=; Path=/; Max-Age=0; Secure; SameSite=None");
  } catch (Exception ignored) {}
 }

 private void clearStaleDySnsCookies() {
  try {
   CookieManager cm = CookieManager.getInstance();
   String[] keys = {"sessionid", "sessionid_ss", "sid_tt", "uid_tt", "uid_tt_ss", "sid_guard", "sid_ucp_v1", "ssid_ucp_v1"};
   for (String k : keys) {
    cm.setCookie("https://is.snssdk.com", k + "=; Path=/; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://is.snssdk.com", k + "=; Path=/; Domain=.snssdk.com; Max-Age=0; Secure; SameSite=None");
   }
  } catch (Exception ignored) {}
 }

 private void clearAllDySessionCookies() {
  clearStaleDySnsCookies();
  try {
   CookieManager cm = CookieManager.getInstance();
   String[] keys = {"sessionid", "sessionid_ss", "sid_tt", "uid_tt", "uid_tt_ss", "sid_guard", "sid_ucp_v1", "ssid_ucp_v1", "X-Xiaodian-Token"};
   for (String k : keys) {
    cm.setCookie("https://www.douyin.com", k + "=; Path=/; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://www.douyin.com", k + "=; Path=/; Domain=.douyin.com; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://sso.douyin.com", k + "=; Path=/; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://ec.snssdk.com", k + "=; Path=/; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://haohuo.jinritemai.com", k + "=; Path=/; Max-Age=0; Secure; SameSite=None");
    cm.setCookie("https://haohuo.jinritemai.com", k + "=; Path=/; Domain=.jinritemai.com; Max-Age=0; Secure; SameSite=None");
   }
  } catch (Exception ignored) {}
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

 private void clearStaleSfCookies() {
  try {
   getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE).edit().remove("sf_login_user").apply();
   CookieManager cm = CookieManager.getInstance();
   String url = "https://www.sf-express.com";
   cm.setCookie(url, "loginUser=; Path=/; Max-Age=0");
   cm.setCookie(url, "loginUser=; Path=/; Secure; Max-Age=0");
   cm.setCookie(url, "loginUser=; Path=/; Domain=www.sf-express.com; Max-Age=0");
   cm.setCookie(url, "loginUser=; Path=/; Domain=www.sf-express.com; Secure; Max-Age=0");
   cm.setCookie(url, "loginUser=; Path=/; Domain=.sf-express.com; Max-Age=0");
   cm.setCookie(url, "loginUser=; Path=/; Domain=.sf-express.com; Secure; Max-Age=0");
   cm.flush();
  } catch (Exception ignored) {}
 }

 private void clearJdRiskCookiesOnly() {
  try {
   CookieManager cm = CookieManager.getInstance();
   String[] urls = {"https://trade.m.jd.com", "https://m.jd.com", "https://my.m.jd.com", "https://api.m.jd.com"};
   String[] riskKeys = {"cd_eid", "3AB9D23F7A4B3C9B", "gia_d", "_gia_d", "shshshfp", "shshshfpa", "shshshfpb"};
   for (String u : urls) {
    for (String k : riskKeys) {
     cm.setCookie(u, k + "=; Path=/; Max-Age=0");
     cm.setCookie(u, k + "=; Path=/; Domain=.jd.com; Max-Age=0");
    }
   }
  } catch (Exception ignored) {}
 }

 private void saveCachedState() {
  try {
   SharedPreferences.Editor ed = getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE).edit();
   for (int p = 0; p < PROVIDER_COUNT; p++) {
    ed.putBoolean("signed_" + p, providerSignedIn[p]);
    ed.putString("orders_" + p, providerOrders[p] != null ? providerOrders[p].toString() : "[]");
   }
   ed.apply();
   if (providerSignedIn[PROVIDER_SF]) {
    persistSfSessionCookie();
   } else {
    getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE).edit().remove("sf_login_user").apply();
   }
  } catch (Exception ignored) {}
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
   b.setBackground(roundBg(b == backToSummaryBtn ? AppTheme.ACCENT : AppTheme.subtleFill(dark), 10));
  }
  results.setBackgroundColor(AppTheme.bg(dark));
  for (WebView w : webs) if (w != null) w.setBackgroundColor(AppTheme.bg(dark));
  renderSummary(true);
 }

 int getMode() { return mode; }

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
   saveCachedState();
   notifySessionState();
   renderSummary(false);
  }
 }

 private void notifySessionState() {
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
    if (!providerSignedIn[PROVIDER_JD] && !hasJdSessionCookie()) {
     if (curUrl == null || !curUrl.contains("plogin.m.jd.com")) {
      webs[PROVIDER_JD].loadUrl(JdUrlPolicy.LOGIN);
     }
    } else if (!providerErrors[PROVIDER_JD].isEmpty()
      || (jdPreferMyHome && curUrl != null && curUrl.contains("trade.m.jd.com"))) {
     providerErrors[PROVIDER_JD] = "";
     loadProviderEntry(PROVIDER_JD, webs[PROVIDER_JD]);
    }
   } else if (provider == PROVIDER_SF && !providerSignedIn[PROVIDER_SF]) {
    if (curUrl == null || !curUrl.contains("sf-express.com")) {
     sfPendingOpenLogin = true;
     webs[PROVIDER_SF].loadUrl(JdUrlPolicy.SF_ORDERS);
    } else {
     webs[PROVIDER_SF].evaluateJavascript("if(window.HandySfOrders&&window.HandySfOrders.openLogin)window.HandySfOrders.openLogin();", null);
    }
   } else if (provider == PROVIDER_DY) {
    if (!providerSignedIn[PROVIDER_DY]) {
     dyApiHistory.clear();
     if (curUrl == null || !curUrl.contains("douyin.com/user/self")) {
      loadDyUrl(webs[PROVIDER_DY], JdUrlPolicy.DY_LOGIN);
     }
    } else if (curUrl == null || !curUrl.contains("haohuo.jinritemai.com")) {
     loadDyUrl(webs[PROVIDER_DY], JdUrlPolicy.DY_ORDERS);
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
  if (provider == PROVIDER_JD) return jdPreferMyHome ? JdUrlPolicy.MY_HOME : JdUrlPolicy.ORDERS;
  if (provider == PROVIDER_SF) return JdUrlPolicy.SF_ORDERS;
  return JdUrlPolicy.DY_ORDERS;
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
   headers.put("Referer", jdPreferMyHome ? "https://m.jd.com/" : JdUrlPolicy.MY_HOME);
   web.loadUrl(target, headers);
  } else if (provider == PROVIDER_DY) {
   String cur = web.getUrl();
   boolean onDyLogin = cur != null && cur.contains("douyin.com");
   if (!providerSignedIn[PROVIDER_DY] && (onDyLogin || (!hasDySessionCookie(buildDyCookieHeader()) && mode == PROVIDER_DY))) {
    loadDyUrl(web, JdUrlPolicy.DY_LOGIN);
   } else {
    loadDyUrl(web, JdUrlPolicy.DY_ORDERS);
   }
  } else if (provider == PROVIDER_SF) {
   restoreSfSessionCookie();
   web.loadUrl(entryUrl(provider));
  } else {
   web.loadUrl(entryUrl(provider));
  }
 }

 @SuppressWarnings({"rawtypes", "unchecked"})
 private void configureModernWebSettings(WebSettings s) {
  try {
   InvocationHandler handler = WebViewGlueCommunicator.getFactory()
    .getWebkitToCompatConverter().convertSettings(s);
   WebSettingsBoundaryInterface boundary =
    (WebSettingsBoundaryInterface) BoundaryInterfaceReflectionUtil.castToSuppLibClass(
     WebSettingsBoundaryInterface.class, handler);
   if (boundary == null) return;
   if (WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST")) {
    boundary.setRequestedWithHeaderOriginAllowList(Collections.emptySet());
   }
   if (WebViewFeature.isFeatureSupported("USER_AGENT_METADATA")) {
    Map cur = boundary.getUserAgentMetadataMap();
    if (cur != null) {
     Map<String, Object> patched = new HashMap<>();
     for (Object k : cur.keySet()) {
      if (k instanceof String) patched.put((String) k, cur.get(k));
     }
     Object bvl = patched.get("BRAND_VERSION_LIST");
     if (bvl instanceof String[][]) {
      String[][] orig = (String[][]) bvl;
      String[][] copy = new String[orig.length][];
      for (int i = 0; i < orig.length; i++) {
       if (orig[i] != null && orig[i].length >= 1 && "Android WebView".equalsIgnoreCase(orig[i][0])) {
        copy[i] = orig[i].clone();
        copy[i][0] = "Google Chrome";
       } else {
        copy[i] = orig[i];
       }
      }
      patched.put("BRAND_VERSION_LIST", copy);
      boundary.setUserAgentMetadataFromMap(patched);
     }
    }
   }
  } catch (Throwable ignored) {}
 }

 private boolean allowedUrl(int provider, String url) {
  if (provider == PROVIDER_JD) return JdUrlPolicy.allowed(url);
  if (provider == PROVIDER_SF) return JdUrlPolicy.allowedSf(url);
  return JdUrlPolicy.allowedDy(url);
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
  String defaultUa = s.getUserAgentString();
  if (defaultUa != null && !defaultUa.isEmpty()) {
   String cleanUa = defaultUa.replace("; wv)", ")").replace("; wv;", ";").replace("Version/4.0 ", "");
   s.setUserAgentString(cleanUa);
   if (provider == PROVIDER_DY) dyMobileUa = cleanUa;
  }
  configureModernWebSettings(s);
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
     dyApiHistory.clear();
    }
    if (!allowedUrl(provider, url)) {
     v.stopLoading();
     if (mode == provider) status.setText("已拦截非官方安全链接。");
     return;
    }
    if (provider == PROVIDER_DY && url != null && url.contains("douyin.com")) {
     v.evaluateJavascript("try{Object.defineProperty(navigator,'webdriver',{get:function(){return false;}});}catch(e){}", null);
    }
    v.evaluateJavascript(scripts[provider], null);
   }
   @Override public void onPageCommitVisible(WebView v, String url) {
    if (allowedUrl(provider, url)) {
     v.evaluateJavascript(scripts[provider], null);
    }
   }
   @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
    if (provider == PROVIDER_DY && r != null && r.getUrl() != null) {
     WebResourceResponse resp = interceptDyEcRequest(v, r);
     if (resp != null) return resp;
    }
    return super.shouldInterceptRequest(v, r);
   }
   @Override public void onPageFinished(WebView v, String url) {
    CookieManager.getInstance().flush();
    if (allowedUrl(provider, url)) {
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
   dyApiHistory.clear();
   clearStaleDySnsCookies();
   syncDyCookiesAcrossDomains();
   CookieManager.getInstance().flush();
   providerErrors[PROVIDER_DY] = "";
   pendingAutoSummary[PROVIDER_DY] = true;
   if (mode == PROVIDER_DY) {
    status.setText("已完成抖音账号验证，正在拉取抖音订单确认登录状态…");
   }
   if (webs[PROVIDER_DY] != null) {
    loadDyUrl(webs[PROVIDER_DY], JdUrlPolicy.DY_ORDERS);
   }
  });
 }

 private void onDySessionExpired() {
  if (destroyed) return;
  dyLoginCompleted = false;
  dyLoginSuccessAt = 0L;
  dyApiHistory.clear();
  boolean wasSigned = providerSignedIn[PROVIDER_DY];
  providerSignedIn[PROVIDER_DY] = false;
  providerErrors[PROVIDER_DY] = "";
  providerOrders[PROVIDER_DY] = new JSONArray();
  pendingAutoSummary[PROVIDER_DY] = true;
  if (wasSigned) {
   saveCachedState();
   notifySessionState();
   renderSummary(false);
  }
  if (mode == PROVIDER_DY && webs[PROVIDER_DY] != null) {
   status.setText("请在抖音官方页面完成登录，登录完后将自动拉取并汇总。");
   notifySessionState();
   redirectToDyLoginIfNeeded(webs[PROVIDER_DY]);
  }
 }

 private void syncDyCookiesAcrossDomains() {
  CookieManager cm = CookieManager.getInstance();
  String[] sources = {"https://www.douyin.com", "https://sso.douyin.com", "https://ec.snssdk.com"};
  String[] targets = {"https://ec.snssdk.com", "https://haohuo.jinritemai.com"};
  for (String src : sources) {
   String raw = cm.getCookie(src);
   if (raw == null || raw.isEmpty()) continue;
   for (String part : raw.split(";")) {
    String kv = part.trim();
    int eq = kv.indexOf('=');
    if (eq <= 0) continue;
    String k = kv.substring(0, eq).trim();
    String val = kv.substring(eq + 1).trim();
    if ("hc_dy_logged".equalsIgnoreCase(k) || val.isEmpty() || "deleted".equalsIgnoreCase(val) || "null".equalsIgnoreCase(val)) continue;
    for (String dst : targets) {
     cm.setCookie(dst, kv + "; Path=/; Secure; SameSite=None");
    }
   }
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
  v.loadUrl(JdUrlPolicy.LOGIN);
 }

 private void redirectToDyLoginIfNeeded(WebView v) {
  if (v == null) return;
  String cur = v.getUrl();
  if (cur != null && cur.contains("douyin.com/user/self")) return;
  long now = android.os.SystemClock.elapsedRealtime();
  if (now - lastDySsoRedirect[0] < 4000) return;
  lastDySsoRedirect[0] = now;
  dyApiHistory.clear();
  pendingAutoSummary[PROVIDER_DY] = true;
  loadDyUrl(v, JdUrlPolicy.DY_LOGIN);
 }

 private String buildProviderEvalScript(int provider) {
  if (provider != PROVIDER_DY || dyApiHistory.isEmpty()) return scripts[provider];
  StringBuilder sb = new StringBuilder();
  sb.append("(function(){var r=").append(scripts[provider]).append(";");
  sb.append("if(window.HandyDyOrders&&window.HandyDyOrders.ingest){");
  for (String[] entry : dyApiHistory) {
   sb.append("window.HandyDyOrders.ingest(")
     .append(JSONObject.quote(entry[1])).append(",")
     .append(JSONObject.quote(entry[0])).append(");");
  }
  sb.append("return window.HandyDyOrders.snapshot();}");
  sb.append("return r;})()");
  return sb.toString();
 }

 private String buildDyCookieHeader() {
  CookieManager cm = CookieManager.getInstance();
  String[] domains = {
   "https://www.douyin.com",
   "https://sso.douyin.com",
   "https://ec.snssdk.com",
   "https://haohuo.jinritemai.com",
   "https://is.snssdk.com"
  };
  Map<String, String> map = new LinkedHashMap<>();
  for (String d : domains) {
   String raw = cm.getCookie(d);
   if (raw == null || raw.isEmpty()) continue;
   for (String part : raw.split(";")) {
    String kv = part.trim();
    int eq = kv.indexOf('=');
    if (eq > 0) {
     String k = kv.substring(0, eq).trim();
     String val = kv.substring(eq + 1).trim();
     if ("hc_dy_logged".equalsIgnoreCase(k) || val.isEmpty() || "deleted".equalsIgnoreCase(val) || "null".equalsIgnoreCase(val)) continue;
     if (!map.containsKey(k)) map.put(k, kv);
    }
   }
  }
  StringBuilder sb = new StringBuilder();
  for (String kv : map.values()) {
   if (sb.length() > 0) sb.append("; ");
   sb.append(kv);
  }
  return sb.toString();
 }

 private boolean hasNonEmptyCookieKey(String raw, String... keys) {
  if (raw == null || raw.isEmpty()) return false;
  for (String part : raw.split(";")) {
   String kv = part.trim();
   int eq = kv.indexOf('=');
   if (eq <= 0) continue;
   String k = kv.substring(0, eq).trim();
   String val = kv.substring(eq + 1).trim();
   if (val.isEmpty() || "null".equalsIgnoreCase(val) || "undefined".equalsIgnoreCase(val) || "deleted".equalsIgnoreCase(val) || "0".equals(val)) continue;
   for (String target : keys) {
    if (target.equals(k)) return true;
   }
  }
  return false;
 }

 private boolean hasJdSessionCookie() {
  CookieManager cm = CookieManager.getInstance();
  String c1 = cm.getCookie("https://trade.m.jd.com");
  String c2 = cm.getCookie("https://m.jd.com");
  return hasNonEmptyCookieKey(c1, "pt_key", "wq_skey", "thor", "wskey")
   || hasNonEmptyCookieKey(c2, "pt_key", "wq_skey", "thor", "wskey");
 }

 private boolean hasDyWebSessionCookie() {
  CookieManager cm = CookieManager.getInstance();
  String c1 = cm.getCookie("https://www.douyin.com");
  String c2 = cm.getCookie("https://sso.douyin.com");
  return hasNonEmptyCookieKey(c1, "sessionid", "sessionid_ss", "sid_tt", "sid_guard", "sid_ucp_v1", "ssid_ucp_v1")
   || hasNonEmptyCookieKey(c2, "sessionid", "sessionid_ss", "sid_tt", "sid_guard", "sid_ucp_v1", "ssid_ucp_v1");
 }

 private boolean hasDySessionCookie(String cookieHeader) {
  return hasNonEmptyCookieKey(cookieHeader,
   "sessionid", "sessionid_ss", "sid_tt", "sid_guard", "sid_ucp_v1", "ssid_ucp_v1", "X-Xiaodian-Token");
 }

 private String getCookieValue(String raw, String key) {
  if (raw == null || raw.isEmpty() || key == null) return null;
  for (String part : raw.split(";")) {
   String kv = part.trim();
   int eq = kv.indexOf('=');
   if (eq <= 0) continue;
   String k = kv.substring(0, eq).trim();
   if (key.equals(k)) {
    String val = kv.substring(eq + 1).trim();
    if (!val.isEmpty() && !"null".equalsIgnoreCase(val) && !"undefined".equalsIgnoreCase(val) && !"deleted".equalsIgnoreCase(val) && !"0".equals(val)) {
     return val;
    }
   }
  }
  return null;
 }

 private boolean hasSfSessionCookie() {
  String raw = CookieManager.getInstance().getCookie("https://www.sf-express.com");
  return hasNonEmptyCookieKey(raw, "loginUser");
 }

 private void persistSfSessionCookie() {
  try {
   String raw = CookieManager.getInstance().getCookie("https://www.sf-express.com");
   String val = getCookieValue(raw, "loginUser");
   if (val != null && !val.isEmpty()) {
    getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE)
     .edit().putString("sf_login_user", val).apply();
    CookieManager cm = CookieManager.getInstance();
    cm.setCookie("https://www.sf-express.com", "loginUser=" + val + "; Path=/; Domain=.sf-express.com; Max-Age=2592000; Secure; SameSite=Lax");
    cm.setCookie("https://www.sf-express.com", "loginUser=" + val + "; Path=/; Domain=www.sf-express.com; Max-Age=2592000; Secure; SameSite=Lax");
    cm.flush();
   }
  } catch (Exception ignored) {}
 }

 private void restoreSfSessionCookie() {
  try {
   SharedPreferences prefs = getContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE);
   String val = prefs.getString("sf_login_user", "");
   if (val != null && !val.isEmpty()) {
    CookieManager cm = CookieManager.getInstance();
    cm.setCookie("https://www.sf-express.com", "loginUser=" + val + "; Path=/; Domain=.sf-express.com; Max-Age=2592000; Secure; SameSite=Lax");
    cm.setCookie("https://www.sf-express.com", "loginUser=" + val + "; Path=/; Domain=www.sf-express.com; Max-Age=2592000; Secure; SameSite=Lax");
    cm.flush();
   }
  } catch (Exception ignored) {}
 }

 private String rewriteDyEcUrl(String url, int bType, int aid) {
  String u = url.replaceAll("([?&])is_outside=1(&|$)", "$1")
                .replaceAll("\\?&", "?")
                .replaceAll("&&", "&")
                .replaceAll("[?&]$", "");
  if (u.contains("b_type_new=")) {
   u = u.replaceAll("([?&])b_type_new=\\d+", "$1b_type_new=" + bType);
  } else {
   u += (u.contains("?") ? "&" : "?") + "b_type_new=" + bType;
  }
  if (aid > 0) {
   if (u.contains("aid=")) {
    u = u.replaceAll("([?&])aid=\\d+", "$1aid=" + aid);
   } else {
    u += "&aid=" + aid;
   }
  }
  return u;
 }

 private String fetchDyEcJson(String urlStr, String cookieHeader, Map<String, String> reqHeaders, String customUa) {
  HttpURLConnection conn = null;
  try {
   conn = (HttpURLConnection) new URL(urlStr).openConnection();
   conn.setRequestMethod("GET");
   conn.setConnectTimeout(4500);
   conn.setReadTimeout(4500);
   conn.setInstanceFollowRedirects(true);
   conn.setRequestProperty("Accept", "application/json, text/plain, */*");
   conn.setRequestProperty("Origin", "https://haohuo.jinritemai.com");
   conn.setRequestProperty("Referer", "https://haohuo.jinritemai.com/views/neworder/center");
   String ua = customUa != null ? customUa : (reqHeaders != null ? reqHeaders.get("User-Agent") : null);
   if (ua != null && !ua.isEmpty()) conn.setRequestProperty("User-Agent", ua);
   if (reqHeaders != null) {
    String xdToken = reqHeaders.get("X-Xiaodian-Token");
    if (xdToken != null && !xdToken.isEmpty()) conn.setRequestProperty("X-Xiaodian-Token", xdToken);
   }
   if (cookieHeader != null && !cookieHeader.isEmpty()) {
    conn.setRequestProperty("Cookie", cookieHeader);
   }
   int code = conn.getResponseCode();
   List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
   if (setCookies != null) {
    CookieManager cm = CookieManager.getInstance();
    for (String sc : setCookies) {
     if (sc != null && !sc.isEmpty()) cm.setCookie("https://ec.snssdk.com", sc);
    }
   }
   InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
   if (in == null) return null;
   try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
    byte[] buf = new byte[4096];
    int n;
    while ((n = stream.read(buf)) != -1) out.write(buf, 0, n);
    return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
   }
  } catch (Exception e) {
   return null;
  } finally {
   if (conn != null) conn.disconnect();
  }
 }

 private boolean isDyOrderBodyOk(String b) {
  return b != null && (b.contains("\"st\":0") || b.contains("\"st\": 0")) && !b.contains("10001010A");
 }

 private WebResourceResponse interceptDyEcRequest(final WebView v, WebResourceRequest r) {
  String scheme = r.getUrl().getScheme();
  String host = r.getUrl().getHost();
  if (host != null && host.startsWith("10.")) {
   return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
  }
  if (!"https".equalsIgnoreCase(scheme) || host == null) return null;
  boolean isEc = "ec.snssdk.com".equalsIgnoreCase(host);
  boolean isHotsoon = "hotsoon.snssdk.com".equalsIgnoreCase(host);
  if (!isEc && !isHotsoon) return null;

  Map<String, String> reqHeaders = r.getRequestHeaders();
  String origin = reqHeaders != null ? reqHeaders.get("Origin") : null;
  if (origin == null || origin.isEmpty()) origin = "https://haohuo.jinritemai.com";
  Map<String, String> corsHeaders = new HashMap<>();
  corsHeaders.put("Access-Control-Allow-Origin", origin);
  corsHeaders.put("Access-Control-Allow-Credentials", "true");
  corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  corsHeaders.put("Access-Control-Allow-Headers", "Content-Type, X-Xiaodian-Token, X-Requested-With, Accept, Origin");
  corsHeaders.put("Cache-Control", "no-store");
  if ("OPTIONS".equalsIgnoreCase(r.getMethod())) {
   return new WebResourceResponse("text/plain", "utf-8", 200, "OK", corsHeaders, new ByteArrayInputStream(new byte[0]));
  }
  String origUrl = r.getUrl().toString();
  if (isHotsoon) {
   if (origUrl.contains("/hotsoon/find/banners")) {
    String emptyBanners = "{\"status_code\":0,\"st\":0,\"code\":0,\"msg\":\"\",\"data\":{\"banners\":[],\"total\":0}}";
    return new WebResourceResponse("application/json", "utf-8", 200, "OK", corsHeaders,
     new ByteArrayInputStream(emptyBanners.getBytes(StandardCharsets.UTF_8)));
   }
   return null;
  }
  if (!"GET".equalsIgnoreCase(r.getMethod())) return null;

  if (origUrl.contains("/index/getNewAbUiParams")) {
   String ab = "{\"st\":0,\"code\":0,\"msg\":\"\",\"data\":{},\"ecom_status_code\":0}";
   return new WebResourceResponse("application/json", "utf-8", 200, "OK", corsHeaders,
    new ByteArrayInputStream(ab.getBytes(StandardCharsets.UTF_8)));
  }

  String cookieHeader = buildDyCookieHeader();
  boolean hasSession = hasDySessionCookie(cookieHeader);
  String awemeUa = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36 aweme_280000 JsSdk/2.0 NetType/WIFI Channel/default AppName/aweme app_version/28.0.0";

  if (!hasSession) {
   if (origUrl.contains("/order/ordercount")) {
    String zeroCounts = "{\"st\":0,\"msg\":\"\",\"data\":{\"all\":0,\"not_pay\":0,\"stock_up\":0,\"delivery\":0,\"comment\":0},\"ecom_status_code\":0}";
    return new WebResourceResponse("application/json", "utf-8", 200, "OK", corsHeaders,
     new ByteArrayInputStream(zeroCounts.getBytes(StandardCharsets.UTF_8)));
   }
   String unauth = "{\"code\":\"10001010A\",\"st\":10001,\"msg\":\"请先登录抖音账号\",\"data\":null}";
   return new WebResourceResponse("application/json", "utf-8", 200, "OK", corsHeaders,
    new ByteArrayInputStream(unauth.getBytes(StandardCharsets.UTF_8)));
  }

  String body = null;
  if (origUrl.contains("/order/page")) {
   body = fetchDyEcJson(rewriteDyEcUrl(origUrl, 2, 1128), cookieHeader, reqHeaders, awemeUa);
   if (!isDyOrderBodyOk(body)) {
    String alt2 = fetchDyEcJson(rewriteDyEcUrl(origUrl, 2, 6383), cookieHeader, reqHeaders, awemeUa);
    if (isDyOrderBodyOk(alt2)) {
     body = alt2;
    } else {
     String alt3 = fetchDyEcJson(origUrl, cookieHeader, reqHeaders, null);
     if (isDyOrderBodyOk(alt3)) {
      body = alt3;
     } else if (body == null) {
      body = alt2 != null ? alt2 : alt3;
     }
    }
   }
   if (!isDyOrderBodyOk(body)) {
    boolean isUnauth = body != null && (body.contains("10001010A") || body.contains("用户未登录"));
    boolean inGrace = dyLoginSuccessAt > 0 && (android.os.SystemClock.elapsedRealtime() - dyLoginSuccessAt < 15000);
    if (isUnauth && !inGrace) {
     body = "{\"code\":\"10001010A\",\"st\":10001,\"msg\":\"抖音登录已过期，可刷新网页重新授权\",\"data\":null}";
    } else if (isUnauth) {
     body = "{\"st\":10005,\"code\":\"10005\",\"msg\":\"当前抖音会话暂未授权订单接口，请点击右上角「刷新网页」或重新扫码登录\",\"data\":null}";
    } else {
     body = "{\"st\":10005,\"code\":\"10005\",\"msg\":\"抖音订单同步失败，请点击右上角「刷新网页」重试\",\"data\":null}";
    }
   }
  } else if (origUrl.contains("/order/ordercount")) {
   body = fetchDyEcJson(rewriteDyEcUrl(origUrl, 2, 1128), cookieHeader, reqHeaders, awemeUa);
   if (!isDyOrderBodyOk(body)) {
    body = "{\"st\":0,\"msg\":\"\",\"data\":{\"all\":0,\"not_pay\":0,\"stock_up\":0,\"delivery\":0,\"comment\":0},\"ecom_status_code\":0}";
   }
  } else if (origUrl.contains("/logistics/")) {
   body = fetchDyEcJson(rewriteDyEcUrl(origUrl, 2, 1128), cookieHeader, reqHeaders, awemeUa);
   if (body == null || (!body.contains("\"status_code\":0") && !body.contains("\"st\":0"))) {
    String altLog = fetchDyEcJson(rewriteDyEcUrl(origUrl, 2, 6383), cookieHeader, reqHeaders, awemeUa);
    if (altLog != null && (altLog.contains("\"status_code\":0") || altLog.contains("\"st\":0"))) {
     body = altLog;
    } else {
     String origLog = fetchDyEcJson(origUrl, cookieHeader, reqHeaders, null);
     if (origLog != null && (origLog.contains("\"status_code\":0") || origLog.contains("\"st\":0"))) {
      body = origLog;
     } else if (body == null) {
      body = "{\"status_code\":1,\"st\":1,\"msg\":\"\"}";
     }
    }
   }
  } else {
   body = fetchDyEcJson(origUrl, cookieHeader, reqHeaders, null);
   if (body == null || body.isEmpty()) {
    body = "{\"st\":0,\"code\":0,\"msg\":\"\",\"data\":{}}";
   }
  }

  if (!body.contains("10001010A") && !body.contains("\"st\":10005")) {
   if (dyApiHistory.size() >= 24) dyApiHistory.remove(0);
   dyApiHistory.add(new String[]{origUrl, body});
  } else if (origUrl.contains("/order/page")) {
   if (dyApiHistory.size() >= 24) dyApiHistory.remove(0);
   dyApiHistory.add(new String[]{origUrl, body});
  }
  post(() -> sampleProvider(PROVIDER_DY));
  return new WebResourceResponse("application/json", "utf-8", 200, "OK", corsHeaders,
   new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
 }

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
     if (provider == PROVIDER_SF) {
      clearStaleSfCookies();
     }
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
      saveCachedState();
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
      clearJdRiskCookiesOnly();
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
      saveCachedState();
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
    if (provider == PROVIDER_SF) persistSfSessionCookie();
    JSONArray rows = data.optJSONArray("orders");
    if (rows != null && (rows.length() > 0 || providerOrders[provider] == null || providerOrders[provider].length() == 0 || data.optBoolean("ready"))) {
     providerOrders[provider] = rows;
    }
    String next = data.toString();
    if (!next.equals(lastSnapshots[provider]) || justLoggedIn) {
     lastSnapshots[provider] = next;
     saveCachedState();
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
   status.setText("尚未登录取件平台；请点左上角「切换软件」选择软件登录。");
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
   chip.setBackground(roundBg(AppTheme.subtleFill(dark), 12));
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
  if (provider == PROVIDER_DY) dyApiHistory.clear();
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
   clearStaleSfCookies();
  } else if (provider == PROVIDER_DY) {
   dyApiHistory.clear();
   dyLoginCompleted = false;
   dyLoginSuccessAt = 0L;
   clearLegacyDyFakeCookie();
   clearAllDySessionCookies();
  }
  generations[provider]++;
  inFlight[provider] = false;
  lastSnapshots[provider] = "";
  providerErrors[provider] = "";
  providerSignedIn[provider] = false;
  pendingAutoSummary[provider] = false;
  providerOrders[provider] = new JSONArray();
  saveCachedState();
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
