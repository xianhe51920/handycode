package com.fairycrane.handycode;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** JD official login + bounded order projection. No credential extraction or JS bridge. */
final class JdPage extends LinearLayout {
 private final Consumer<Boolean> session;
 private final TextView status;
 private final LinearLayout tools,cards;
 private final FrameLayout stage;
 private final ScrollView results;
 private final Button login,refresh,summary;
 private WebView web;
 private boolean dark,paused=true,destroyed,polling,inFlight,showResults;
 private int generation;
 private long lastProbe;
 private boolean preferOfficial;
 private String script="",lastSnapshot="";
 private final Runnable tick=new Runnable(){public void run(){
  polling=false;
  if(paused||destroyed||web==null)return;
  sample();
  schedule();
 }};
 JdPage(Context context,Consumer<Boolean> session) {
  super(context);this.session=session;setOrientation(VERTICAL);
  tools=new LinearLayout(context);tools.setPadding(dp(10),dp(4),dp(10),dp(4));
  login=action("登录/官方页",()->official(true));refresh=action("刷新",()->reload());
  summary=action("取件列表",this::showSummary);
  tools.addView(login,new LinearLayout.LayoutParams(0,dp(42),1));
  tools.addView(refresh,new LinearLayout.LayoutParams(0,dp(42),1));
  tools.addView(summary,new LinearLayout.LayoutParams(0,dp(42),1));addView(tools);
  status=text("首次使用请在京东官方页面登录；仅展示当前账号订单中提供的取件码。",12);
  status.setPadding(dp(14),dp(8),dp(14),dp(8));addView(status);
  stage=new FrameLayout(context);addView(stage,new LinearLayout.LayoutParams(-1,0,1));
  cards=new LinearLayout(context);cards.setOrientation(VERTICAL);cards.setPadding(dp(12),dp(8),dp(12),dp(88));
  results=new ScrollView(context);results.addView(cards);results.setVisibility(GONE);stage.addView(results,new FrameLayout.LayoutParams(-1,-1));
  try(InputStream in=context.getAssets().open("jd-orders.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);script=new String(out.toByteArray(),StandardCharsets.UTF_8);
  }catch(IOException e){status.setText("京东页面资源读取失败，请重新安装。");}
  setDark(false);
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int size){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);return t;}
 private Button action(String s,Runnable run){Button b=new Button(getContext());b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setOnClickListener(v->run.run());return b;}
 void setDark(boolean value){
  dark=value;setBackgroundColor(AppTheme.bg(dark));status.setTextColor(AppTheme.muted(dark));
  for(Button b:new Button[]{login,refresh,summary}){b.setTextColor(AppTheme.ink(dark));b.setBackgroundColor(AppTheme.surface(dark));}
  results.setBackgroundColor(AppTheme.bg(dark));
  if(web!=null)web.setBackgroundColor(AppTheme.bg(dark));
  if(!lastSnapshot.isEmpty())try{render(new JSONObject(lastSnapshot));}catch(JSONException ignored){}
 }
 void showPage(){if(destroyed)return;paused=false;if(web==null)createWeb();web.onResume();schedule();}
 private void createWeb(){
  if(destroyed||script.isEmpty())return;
  web=new WebView(getContext());WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);
  s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest r){r.deny();}});
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !JdUrlPolicy.allowed(r.getUrl().toString());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !JdUrlPolicy.allowed(url);}
   @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
    generation++;inFlight=false;lastProbe=0;preferOfficial=false;lastSnapshot="";cards.removeAllViews();showResults=false;results.setVisibility(GONE);
    if(!JdUrlPolicy.allowed(url)){v.stopLoading();status.setText("已阻止非京东官方地址。");return;}
    status.setText("正在打开京东官方页面…");sample();
   }
   @Override public void onLoadResource(WebView v,String url){
    if(!paused&&android.os.SystemClock.elapsedRealtime()-lastProbe>=250)sample();
   }
   @Override public void onPageFinished(WebView v,String url){
    if(!JdUrlPolicy.allowed(url))return;
    CookieManager.getInstance().flush();if(lastSnapshot.isEmpty())status.setText("请完成官方登录；若列表未识别，可继续使用官方页面。");sample();
   }
   @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();status.setText("京东安全连接验证失败，请检查网络或手机日期后重试。");}
   @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText("京东页面暂时无法连接，请点刷新重试。");}
   @Override public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail d){
    if(web==v){generation++;stage.removeView(v);web=null;v.destroy();session.accept(false);status.setText("京东网页组件已停止，请点刷新重新打开。");}
    return true;
   }
  });
  stage.addView(web,0,new FrameLayout.LayoutParams(-1,-1));web.loadUrl(JdUrlPolicy.ORDERS);
 }
 private void schedule(){if(!polling&&!paused&&!destroyed){polling=true;postDelayed(tick,1000);}}
 private void sample(){
  final WebView expected=web;final int token=generation;
  if(expected==null||paused||destroyed||inFlight||!JdUrlPolicy.allowed(expected.getUrl()))return;
  inFlight=true;lastProbe=android.os.SystemClock.elapsedRealtime();
  expected.evaluateJavascript(script,result->{
   if(destroyed||web!=expected||generation!=token)return;
   inFlight=false;
   try{
    JSONObject data=new JSONObject(result);
    if(!data.optString("href").equals(expected.getUrl()))return;
    if(data.optBoolean("login")){session.accept(false);showResults=false;results.setVisibility(GONE);return;}
    if(!data.optBoolean("ready"))return;
    session.accept(true);
    String next=data.toString();
    if(!next.equals(lastSnapshot)){lastSnapshot=next;render(data);if(!preferOfficial){showResults=true;results.setVisibility(VISIBLE);}}
   }catch(JSONException ignored){}
  });
 }
 private void render(JSONObject data){
  cards.removeAllViews();JSONArray rows=data.optJSONArray("orders");
  int found=0;
  if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject o=rows.optJSONObject(i);if(o!=null&&!o.optString("code").isEmpty())found++;}
  status.setText("已识别 "+found+" 个含取件码订单；未返回取件码的订单不会生成码。");
  addCard("京东取件码","来自当前账号的京东商城订单，不包含所有京东快递收件。");
  if(found==0)addCard("暂无可识别的取件码","当前订单未提供取件码，或网页结构已变化。可点“登录/官方页”核对。");
  if(rows!=null)for(int i=0;i<rows.length();i++){
   JSONObject o=rows.optJSONObject(i);if(o==null||o.optString("code").isEmpty())continue;
   addCard(o.optString("name","京东订单"),"取件码："+o.optString("code")+"\n"+o.optString("status")+"\n"+o.optString("detail"));
  }
 }
 private void addCard(String title,String body){
  LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));
  GradientDrawable bg=new GradientDrawable();bg.setColor(AppTheme.surface(dark));bg.setCornerRadius(dp(16));card.setBackground(bg);
  TextView heading=text(title,16);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setTextColor(AppTheme.ink(dark));card.addView(heading);
  TextView detail=text(body,14);detail.setTextColor(AppTheme.muted(dark));detail.setPadding(0,dp(10),0,0);detail.setTextIsSelectable(true);card.addView(detail);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);cards.addView(card,lp);
 }
 private void showSummary(){preferOfficial=false;showResults=true;if(lastSnapshot.isEmpty()){cards.removeAllViews();addCard("正在读取订单","请先完成京东官方登录。若页面未返回取件码，可使用官方页面查看。");}results.setVisibility(VISIBLE);sample();}
 private void official(boolean explicit){showPage();preferOfficial=true;showResults=false;results.setVisibility(GONE);if(web!=null&&explicit)web.requestFocus();}
 private void reload(){showPage();preferOfficial=false;lastSnapshot="";showResults=false;results.setVisibility(GONE);if(web!=null)web.loadUrl(JdUrlPolicy.ORDERS);}
 void resetSessionView(){pausePage();generation++;lastSnapshot="";cards.removeAllViews();results.setVisibility(GONE);showResults=false;if(web!=null){web.stopLoading();web.setWebViewClient(new WebViewClient());stage.removeView(web);web.destroy();web=null;}}
 void pausePage(){paused=true;removeCallbacks(tick);polling=false;if(web!=null)web.onPause();}
 void resumePage(boolean active){if(active)showPage();else pausePage();}
 void scrollFocusedInput(){if(web!=null)web.evaluateJavascript("(function(){var e=document.activeElement;if(e&&e.matches('input,textarea'))e.scrollIntoView({block:'center'});})()",null);}
 boolean goBack(){if(showResults){official(false);return true;}if(web!=null&&web.canGoBack()){web.goBack();return true;}return false;}
 void destroyPage(){resetSessionView();destroyed=true;}
}
