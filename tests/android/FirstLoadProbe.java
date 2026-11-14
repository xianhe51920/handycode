package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import android.webkit.WebView;

/** First entry into all four providers; preserve account storage and sample only presentation state. */
public final class FirstLoadProbe extends Instrumentation {
 private Activity activity; private volatile boolean watching;
 private volatile String failure=""; private String stage="launch";
 private Object cover; private int frames,fadeFrames,checks,provider;
 private final double[] identityHeights={-1,-1,-1,-1};
 private final String[] keys={"cainiao","taobao","pinduoduo","jd"};
 private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 private void ui(Runnable task){runOnMainSync(()->{try{check(activity.hasWindowFocus(),"Own app lost focus");task.run();}catch(Throwable e){failure=e.toString();}});if(!failure.isEmpty())throw new AssertionError(failure);}
 private Object page(int p)throws Exception{return field(activity,p==0?"cainiaoPage":p==2?"pddPage":"jdPage");}
 private void select(int p){ui(()->{try{provider=p;cover=p==1?field(activity,"transitionPanel"):p==3?null:field(page(p),"cover");Method m=activity.getClass().getDeclaredMethod("selectPage",int.class);m.setAccessible(true);m.invoke(activity,p);}catch(Exception e){throw new RuntimeException(e);}});}
 private void watch(){watching=true;ui(()->Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){
  @Override public void doFrame(long time){if(!watching)return;try{
   if(!activity.hasWindowFocus())throw new AssertionError("Own app lost focus");
   if(cover instanceof View){View v=(View)cover;if(v.isShown()&&v.getWidth()>0){
    frames++;if(v.getAlpha()<.99f){fadeFrames++;ValueAnimator morph=(ValueAnimator)field(cover,"morph");check(morph==null||!morph.isRunning(),"Content exposed while card size still morphs");}
    check(((Number)field(cover,"identityHeight")).floatValue()>0,"Positive loading-card size");
    double actual=(((Number)field(cover,"identityHeight")).doubleValue()+((Number)field(cover,"headingHeight")).doubleValue()+2)*((Number)field(cover,"scale")).doubleValue();
    if(identityHeights[provider]<0)identityHeights[provider]=actual;
    check(Math.abs(identityHeights[provider]-actual)<=1.1,"Identity loading height changed: provider="+provider+",first="+identityHeights[provider]+",now="+actual+(provider==2?",targetWidth="+field(page(2),"coverWidth")+",targetIdentity="+field(page(2),"coverIdentity")+",targetHeading="+field(page(2),"coverHeading"):""));
   }}else if(provider==3){View card=(View)field(page(3),"identity");if(card.getHeight()>0){if(identityHeights[3]<0)identityHeights[3]=card.getHeight();check(Math.abs(identityHeights[3]-card.getHeight())<=1.1,"JD identity initial height changed");}frames++;}
  }catch(Throwable e){failure=e.toString();watching=false;}
  if(watching)Choreographer.getInstance().postFrameCallback(this);
 }}));}
 private void waitReady(int p)throws Exception{
  for(int attempt=0;attempt<160;attempt++){boolean[] done={false};ui(()->{try{
   if(p==0){Object a=page(p);check(!"login-required".equals(field(a,"phase")),"Cainiao needs official login");done[0]="ready".equals(field(a,"phase"))&&((View)field(a,"cover")).getVisibility()==View.GONE;}
   else if(p==1){check(!(Boolean)field(activity,"taobaoLoginVisible"),"Taobao needs official login");done[0]=(Boolean)field(activity,"combinedDisplayed")&&((View)field(activity,"transitionPanel")).getVisibility()==View.GONE;}
   else if(p==2){Object a=page(p);done[0]=((View)field(a,"cover")).getVisibility()==View.GONE;}
   else {Object a=page(p),identity=field(a,"identity");check(!(Boolean)field(a,"loginVisible"),"JD needs official login");done[0]=!(Boolean)field(a,"loading")&&(Boolean)field(identity,"signedIn")&&!(Boolean)field(identity,"loading");}
  }catch(Exception e){throw new RuntimeException(e);}});
   if(done[0]){Thread.sleep(300);return;}Thread.sleep(125);
  }throw new AssertionError("First-load deadline");
 }
 private String js(WebView web,String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);String[] value={"null"};ui(()->web.evaluateJavascript(code,v->{value[0]=v;done.countDown();}));check(done.await(5,TimeUnit.SECONDS),"JS measure deadline");return value[0];
 }
 private void verifyIdentity(int p)throws Exception{
  if(p==3){double actual=((View)field(page(3),"identity")).getHeight();check(identityHeights[3]>0&&Math.abs(actual-identityHeights[3])<=1.1,"JD loaded identity must equal initial loading height");return;}
  WebView web=(WebView)(p==1?field(activity,"browser"):field(page(p),"web"));
  String selector=p==0?"#hc-cainiao-card":p==1?".hc-card":".hc-pdd-card";
  String body=p==2?"#hc-pdd-identity":"#identity-content";
  JSONObject info=new JSONObject(js(web,"(()=>{let c=document.querySelector('"+selector+"'),m=document.querySelector('"+body+"'),r=m.getBoundingClientRect(),n=m.firstElementChild.getBoundingClientRect();return {height:c.getBoundingClientRect().height,cardWidth:c.getBoundingClientRect().width,width:innerWidth,fit:n.top>=r.top-.6&&n.bottom<=r.bottom+.6};})()"));
  double actual=info.getDouble("height")*((View)cover).getWidth()/info.getDouble("width");
  check(identityHeights[p]>0&&Math.abs(actual-identityHeights[p])<=1.1,"Loaded identity height differs from initial loading: provider="+p+",loading="+identityHeights[p]+",loaded="+actual);
  check(info.getBoolean("fit"),"Identity contents overflow reserved area");
  check(Math.abs(info.getDouble("cardWidth")-(info.getDouble("width")-20))<=1.1,"Identity card width escapes the device viewport");
  if(p==1){JSONObject empty=new JSONObject(js(web,"(()=>{let p=document.querySelector('.hc-parcel-empty');if(!p)return {present:false};let b=document.querySelector('#parcels-content').getBoundingClientRect(),a=Array.from(p.children).map(n=>n.getBoundingClientRect()),top=Math.min(...a.map(r=>r.top)),bottom=Math.max(...a.map(r=>r.bottom));return {present:true,centered:Math.abs((top+bottom)/2-(b.top+b.bottom)/2)<1.1&&a.every(r=>Math.abs((r.left+r.right)/2-(b.left+b.right)/2)<1.1)};})()"));check(empty.getBoolean("present")&&empty.getBoolean("centered"),"Taobao empty illustration and message are not centered as a group");}
 }
 private void screenshot(int p)throws Exception{
  Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Screenshot available");
  File file=new File(getTargetContext().getExternalFilesDir(null),"first-load-"+keys[p]+".png");
  try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Intent intent=new Intent().setClassName("com.fairycrane.handycode","com.fairycrane.handycode.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("provider","cainiao");
  ActivityMonitor monitor=addMonitor("com.fairycrane.handycode.MainActivity",null,false);getTargetContext().startActivity(intent);activity=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);check(activity!=null,"Activity launched");
  for(int i=0;i<80;i++){boolean[] focused={false};runOnMainSync(()->focused[0]=activity.hasWindowFocus());if(focused[0])break;Thread.sleep(25);}
  cover=field(page(0),"cover");watch();
  for(int p=0;p<4;p++){stage=keys[p];if(p>0)select(p);waitReady(p);verifyIdentity(p);screenshot(p);Bundle update=new Bundle();update.putString("stream","PASS: "+stage+" first entry, constant identity height "+identityHeights[p]+"px\n");sendStatus(1,update);}
  ui(()->watching=false);check(frames>20&&fadeFrames>5,"Captured loading and reveal frames");result.putString("stream","PASS: four first entries; "+frames+" loading/reveal frames, "+fadeFrames+" aligned fade frames, "+checks+" checks\n");
 }catch(Throwable e){while(e.getCause()!=null)e=e.getCause();result.putString("failure",stage);result.putString("stream","FAIL/SKIP: "+stage+": "+e+"\n");}
 finally{runOnMainSync(()->watching=false);}finish(result.containsKey("failure")?0:-1,result);}
}
