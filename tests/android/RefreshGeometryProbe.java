package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Measures refresh layout each frame; does not inspect codes, cookies or phone values. */
public final class RefreshGeometryProbe extends Instrumentation {
    private Activity activity;
    private boolean watching;
    private String failure = "";
    private int frames, checks;
    private boolean onlyCainiao;
    private String stage = "launch";
    private Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private Object call(Object owner,String name) throws Exception {
        Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(owner);
    }
    private void check(boolean value,String label) {checks++;if(!value)throw new AssertionError(label);}
    private void ui(Runnable r) {
        runOnMainSync(()->{
            if(!activity.hasWindowFocus()) {failure="Own app lost focus";return;}
            try{r.run();}catch(Throwable e){failure=e.toString();}
        });
        if(!failure.isEmpty())throw new AssertionError(failure);
    }
    private String js(WebView web,String script) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] result={"null"};
        ui(()->web.evaluateJavascript(script,v->{result[0]=v;done.countDown();}));
        check(done.await(5,TimeUnit.SECONDS),"JS callback deadline");return result[0];
    }
    private void open(String provider) throws Exception {
        if(activity!=null) {
            int page="jd".equals(provider)?3:0;
            ui(()->{try{Method m=activity.getClass().getDeclaredMethod("selectPage",int.class);m.setAccessible(true);m.invoke(activity,page);}
                catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(600);return;
        }
        Intent i=new Intent().setClassName("com.fairycrane.handycode","com.fairycrane.handycode.MainActivity");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);i.putExtra("provider",provider);
        ActivityMonitor monitor=addMonitor("com.fairycrane.handycode.MainActivity",null,false);
        getTargetContext().startActivity(i);
        activity=waitForMonitorWithTimeout(monitor,8000);removeMonitor(monitor);
        check(activity!=null,"Own activity launch deadline");Thread.sleep(600);
    }
    private void waitSettled(Object page,boolean cainiao) throws Exception {
        for(int i=0;i<100;i++) {
            boolean[] settled={false};ui(()->{
                try {settled[0]=cainiao ? "ready".equals(field(page,"phase"))
                    && ((View)field(page,"cover")).getVisibility()!=View.VISIBLE
                    : !(Boolean)field(page,"loading") && !(Boolean)field(page,"loginVisible");}
                catch(Exception e){throw new RuntimeException(e);}
            });
            if(settled[0])return;Thread.sleep(150);
        }
        throw new AssertionError("Official page not ready for geometry check");
    }
    private void watch(Runnable sample) {
        watching=true;
        ui(()->Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){
            @Override public void doFrame(long time) {
                if(!watching)return;
                if(!activity.hasWindowFocus()){failure="Own app lost focus";watching=false;return;}
                try{sample.run();frames++;}catch(Throwable e){failure=e.toString();watching=false;}
                if(watching)Choreographer.getInstance().postFrameCallback(this);
            }
        }));
    }
    private void stop() {ui(()->watching=false);}
    private void jd() throws Exception {
        stage="JD refresh";
        open("jd");Object page=field(activity,"jdPage");waitSettled(page,false);
        TextView refresh=(TextView)field(page,"refresh");
        int[] xy=new int[2], size=new int[1];ui(()->{refresh.getLocationOnScreen(xy);size[0]=refresh.getWidth();});
        int startFrames=frames;
        watch(()->{
            int[] now=new int[2];refresh.getLocationOnScreen(now);
            check(now[0]==xy[0]&&now[1]==xy[1]&&refresh.getWidth()==size[0],"JD refresh bounds changed during transition");
            check(Math.abs(refresh.getTranslationX())<.1f,"JD refresh translated");
            check(refresh.getAlpha()==1f && refresh.getVisibility()==View.VISIBLE,"JD refresh text faded or disappeared");
        });
        for(int round=0;round<2;round++) {
            ui(()->refresh.performClick());Thread.sleep(100);waitSettled(page,false);Thread.sleep(350);
        }
        stop();check(frames-startFrames>15,"JD sampled multiple frames");
    }
    private void cainiao(boolean expanded) throws Exception {
        stage="Cainiao initial expanded="+expanded;
        open("cainiao");Object page=field(activity,"cainiaoPage");waitSettled(page,true);
        WebView web=(WebView)field(page,"web");
        if(expanded) {js(web,"document.querySelector('#hc-cainiao-card [data-hc-barcode]').click()");Thread.sleep(600);}
        js(web,"HandyCodeCainiaoLayout.update(" + field(page,"dark") + ")");
        JSONObject before=new JSONObject(js(web,"(()=>{let c=document.querySelector('#hc-cainiao-card');return {height:c.getBoundingClientRect().height,top:c.getBoundingClientRect().top,expanded:c.querySelector('[data-hc-barcode]').getAttribute('aria-expanded')==='true'};})()"));
        check(before.getBoolean("expanded")==expanded,"Expected initial zoom state");
        Object cover=field(page,"cover");int[] visibleFrames={0};
        watch(()->{
            try {
                if(((View)cover).getVisibility()!=View.VISIBLE)return;
                visibleFrames[0]++;
                double actual=((Number)field(cover,"identityHeight")).doubleValue()+((Number)field(cover,"headingHeight")).doubleValue()+2;
                check(Math.abs(actual-before.optDouble("height"))<1.1,"Cainiao loading height: expanded="+expanded+",actual="+actual+",expected="+before.optDouble("height")+",cachedBody="+field(page,"geometryBody")+",cachedHeading="+field(page,"geometryHeading"));
                check(Math.abs(((Number)field(cover,"identityTop")).doubleValue()-before.optDouble("top"))<1.1,"Cainiao loading card top moved");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        stage="Cainiao refresh expanded="+expanded;
        js(web,"document.querySelector('#hc-cainiao-card header button').click()");
        Thread.sleep(150);waitSettled(page,true);Thread.sleep(350);stop();
        JSONObject after=new JSONObject(js(web,"(()=>{let c=document.querySelector('#hc-cainiao-card');return {height:c.getBoundingClientRect().height,expanded:c.querySelector('[data-hc-barcode]').getAttribute('aria-expanded')==='true'};})()"));
        check(visibleFrames[0]>5,"Cainiao sampled loading frames");
        check(Math.abs(before.getDouble("height")-after.getDouble("height"))<1.1,"Cainiao refresh changed final card height");
        check(after.getBoolean("expanded")==expanded,"Cainiao refresh retains zoom state");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);onlyCainiao=args!=null&&"true".equals(args.getString("only_cainiao"));start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(!onlyCainiao)jd();cainiao(false);cainiao(true);
            result.putString("stream","PASS: "+checks+" refresh geometry checks over "+frames+" frames; JD stable action bounds, Cainiao collapsed/expanded loading and final bounds\n");
        }catch(Throwable e){while(e.getCause()!=null)e=e.getCause();result.putString("failure",e.toString());result.putString("stream","FAIL/SKIP: "+stage+": "+e+"\n");}
        finally {runOnMainSync(()->watching=false);}
        finish(result.containsKey("failure")?0:-1,result);
    }
}
