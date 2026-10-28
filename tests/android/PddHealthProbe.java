package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Fixed readiness flags only; no account content, cookies, code or API payloads. */
public final class PddHealthProbe extends Instrumentation {
    private boolean taobaoFirst, recovery;
    @Override public void onCreate(Bundle args){super.onCreate(args);taobaoFirst="true".equals(args.getString("taobaoFirst"));recovery="true".equals(args.getString("recovery"));start();}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private String eval(WebView web,String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] value={null};
        runOnMainSync(()->web.evaluateJavascript(script,result->{value[0]=result;latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JS callback timeout");return value[0];
    }
    private void waitForReveal(View cover)throws Exception{
        for(int i=0;i<24;i++){
            boolean[] gone={false};runOnMainSync(()->gone[0]=cover.getVisibility()==View.GONE);
            if(gone[0])return;Thread.sleep(250);
        }
        throw new AssertionError("Presentation fallback did not reveal the assembled page");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();
        try{
            Intent launch=new Intent().setClassName("com.fairycrane.handycode","com.fairycrane.handycode.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);launch.putExtra("provider",taobaoFirst?"taobao":"pinduoduo");
            Activity activity=startActivitySync(launch);
            if(taobaoFirst){
                Thread.sleep(1800);
                Method select=activity.getClass().getDeclaredMethod("selectPage",int.class);select.setAccessible(true);
                runOnMainSync(()->{try{select.invoke(activity,1);}catch(Exception e){throw new RuntimeException(e);}});
                Thread.sleep(450);
            }
            Object pdd=field(activity,"pddPage");WebView web=(WebView)field(pdd,"web");View cover=(View)field(pdd,"cover");
            boolean settled=false;long start=System.currentTimeMillis();String last="";
            for(int attempt=0;attempt<80;attempt++){
                String flags=eval(web,"(function(){var a=document.getElementById('hc-pdd-identity'),b=document.getElementById('hc-pdd-parcels');return {packagePage:location.pathname==='/mdkd/package',loginPage:location.pathname==='/login.html',complete:document.readyState==='complete',next:!!document.getElementById('__next'),routing:!!window.__handyPddSameOrigin,layout:!!window.HandyPddLayout,shell:!!document.getElementById('hc-pdd-shell'),identity:!!(a&&a.dataset.ready==='true'),parcels:!!(b&&b.dataset.ready==='true')};})()");
                JSONObject snapshot=new JSONObject(flags);
                boolean[] revealed={false};runOnMainSync(()->revealed[0]=cover.getVisibility()==View.GONE);
                snapshot.put("revealed",revealed[0]);snapshot.put("nativeCommitted",field(pdd,"pageCommitted"));snapshot.put("nativePackage",field(pdd,"packageNavigation"));snapshot.put("bundleAdjusted",field(pdd,"bundleAdjusted"));
                String state=snapshot.toString();
                if(!state.equals(last)){report.append(System.currentTimeMillis()-start).append("ms ").append(state).append('\n');last=state;}
                if(revealed[0]&&(snapshot.optBoolean("loginPage")||snapshot.optBoolean("identity")&&snapshot.optBoolean("parcels"))){settled=true;break;}
                Thread.sleep(300);
            }
            if(!settled)throw new AssertionError("PDD did not settle within bounded readiness observation");
            if(recovery){
                if(!"true".equals(eval(web,"location.pathname==='/mdkd/package'&&!!document.getElementById('hc-pdd-shell')")))throw new AssertionError("Authenticated package document unavailable for recovery fixture");
                final Object page=pdd;final WebView pageWeb=web;
                Method show=pdd.getClass().getDeclaredMethod("showCover");show.setAccessible(true);
                Field committed=pdd.getClass().getDeclaredField("pageCommitted");committed.setAccessible(true);
                long before=(Long)field(pdd,"navigation");
                // Reproduce a missing commit callback: only document finish
                // arrives. This must still request a rendered, assembled frame.
                runOnMainSync(()->{try{show.invoke(page);committed.set(page,false);pageWeb.getWebViewClient().onPageFinished(pageWeb,pageWeb.getUrl());}catch(Exception e){throw new RuntimeException(e);}});
                waitForReveal(cover);
                report.append("PASS: finish recovers omitted native commit callback\n");
                // Reproduce readiness arriving late without a console phase.
                // Only the app's own boolean/object hook is delayed, not data.
                eval(web,"window.__hcDelayedLayout=window.HandyPddLayout;window.HandyPddLayout=null;setTimeout(function(){window.HandyPddLayout=window.__hcDelayedLayout;delete window.__hcDelayedLayout;},1200);true");
                runOnMainSync(()->{try{show.invoke(page);pageWeb.getWebViewClient().onPageCommitVisible(pageWeb,pageWeb.getUrl());}catch(Exception e){throw new RuntimeException(e);}});
                waitForReveal(cover);
                if(before!=(Long)field(pdd,"navigation"))throw new AssertionError("Fallback caused an unwanted navigation");
                report.append("PASS: late readiness without console event recovers, no navigation or business refresh\n");
            }
            report.append("PASS: PDD readiness settled, taobaoFirst=").append(taobaoFirst).append("; flags only\n");
        }catch(Throwable error){result.putString("failure","pdd readiness probe");report.append("FAIL/SKIP: ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        result.putString("stream",report.toString());finish(result.containsKey("failure")?0:-1,result);
    }
}
