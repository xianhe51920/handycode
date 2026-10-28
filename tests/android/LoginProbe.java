package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import com.fairycrane.handycode.UrlPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** No login submission or real logout. Checks status UI and intercepts launch intent. */
public final class LoginProbe extends Instrumentation {
    private Activity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object field(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private String eval(WebView web,String script) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);String[] result={null};
        runOnMainSync(()->web.evaluateJavascript(script,value->{result[0]=value;latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JS callback timeout");
        return result[0];
    }
    private void guard(){
        boolean[] focused={false};runOnMainSync(()->focused[0]=activity.hasWindowFocus());
        if(!focused[0])throw new IllegalStateException("Own app lost focus; skipped");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();ActivityMonitor monitor=null;
        try {
            Intent launch=new Intent().setClassName("com.fairycrane.handycode","com.fairycrane.handycode.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);launch.putExtra("provider","pinduoduo");
            activity=startActivitySync(launch);Thread.sleep(500);
            Object pdd=field(activity,"pddPage");WebView web=(WebView)field(pdd,"web");
            boolean login=false;
            for(int i=0;i<65;i++){
                guard();
                if("true".equals(eval(web,"location.pathname==='/login.html'&&window.HandyPddLoginClient===true"))){login=true;break;}
                Thread.sleep(300);
            }
            check(login,"Official login page and delegated app-button hook ready");
            Button status=(Button)field(activity,"logoutButton");
            runOnMainSync(()->{
                check("未登录".contentEquals(status.getText()),"Logged-out status label");
                check(!status.isEnabled(),"Logged-out status cannot trigger logout");
                check(status.getCompoundDrawablesRelative()[2]==null,"No logout icon when signed out");
            });
            View actions=(View)field(pdd,"actions");
            runOnMainSync(()->check(actions.getVisibility()==View.GONE,"No native toolbar above login"));
            Method set=activity.getClass().getDeclaredMethod("setSignedIn",int.class,boolean.class);set.setAccessible(true);
            boolean[] states=(boolean[])field(activity,"signedIn");final boolean oldTao=states[0],oldPdd=states[1];
            runOnMainSync(()->{
                try {
                    set.invoke(activity,0,true);
                    check("未登录".contentEquals(status.getText()),"Other platform login does not change active status");
                    set.invoke(activity,1,true);
                    check("退出登录".contentEquals(status.getText())&&status.isEnabled(),"Signed-in state restores logout action");
                    check(status.getCompoundDrawablesRelative()[2]!=null,"Signed-in state restores vector icon");
                    set.invoke(activity,0,false);
                    check("退出登录".contentEquals(status.getText()),"Other platform logout does not change active status");
                } catch(Exception error){throw new RuntimeException(error);}
                finally{try{set.invoke(activity,0,oldTao);set.invoke(activity,1,oldPdd);}catch(Exception error){throw new RuntimeException(error);}}
            });
            final int[] launched={0};final boolean[] correct={false};
            monitor=new ActivityMonitor(){
                @Override public ActivityResult onStartActivity(Intent intent){
                    launched[0]++;
                    correct[0]=Intent.ACTION_VIEW.equals(intent.getAction())&&"com.xunmeng.pinduoduo".equals(intent.getPackage())
                        &&UrlPolicy.PDD_IDENTITY_APP_LINK.equals(intent.getDataString());
                    // Verify dispatch without leaving the login screen or opening an account page.
                    return new ActivityResult(Activity.RESULT_CANCELED,null);
                }
            };
            addMonitor(monitor);
            String bounds=eval(web,"(function(){var all=document.querySelectorAll('button,a,div,span');for(var i=0;i<all.length;i++){var e=all[i],t=(e.textContent||'').replace(/[\\s\\u200b]/g,'').replace(/^[\\ue000-\\uf8ff]+/,'');if(!/^打开拼多多app$/i.test(t))continue;var r=e.getBoundingClientRect();if(r.width>0&&r.height>0)return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth};}return null;})()");
            JSONObject geometry=new JSONObject(bounds);guard();
            int[] pos=new int[2];float[] scale={0};
            runOnMainSync(()->{web.getLocationOnScreen(pos);scale[0]=web.getWidth()/(float)geometry.optDouble("width");});
            float x=pos[0]+(float)geometry.getDouble("x")*scale[0],y=pos[1]+(float)geometry.getDouble("y")*scale[0];
            long down=SystemClock.uptimeMillis();
            MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);
            press.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(press);press.recycle();Thread.sleep(80);
            MotionEvent release=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);
            release.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(release);release.recycle();
            Thread.sleep(700);
            check(launched[0]==1,"Exactly one launch after tapping official app button");
            check(correct[0],"Explicit PDD package and identity-code URI; no browser intermediate");
            result.putString("stream","PASS: "+checks+" login status/button/intent checks; no login submitted or account cleared\n");
        } catch(Throwable error){
            result.putString("failure","login UI/launch probe");
            result.putString("stream","FAIL/SKIP: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");
        } finally {if(monitor!=null)removeMonitor(monitor);}
        finish(result.containsKey("failure")?0:-1,result);
    }
}
