package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import com.fairycrane.handycode.SwitchMotion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Retention assertions return booleans/counts only; no account values or images. */
public final class PagerProbe extends Instrumentation {
    private Activity activity;
    private int checks;
    private boolean holding;
    private float endX, y;
    private long downAt;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private Object field(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner);
    }
    private void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private void waitFor(CountDownLatch latch) throws Exception {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("UI callback timeout");
    }
    private void guard() {
        boolean[] focused = {false};
        runOnMainSync(() -> focused[0] = activity.hasWindowFocus() && !activity.isFinishing());
        if (!focused[0]) throw new IllegalStateException("Own app lost focus; skipped");
    }
    private String eval(WebView web, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); String[] result = {null};
        runOnMainSync(() -> web.evaluateJavascript(script, value -> { result[0] = value; latch.countDown(); }));
        waitFor(latch); return result[0];
    }
    private void select(int page) throws Exception {
        guard();
        Method select = activity.getClass().getDeclaredMethod("selectPage", int.class); select.setAccessible(true);
        runOnMainSync(() -> { try { select.invoke(activity, page); } catch (Exception e) { throw new RuntimeException(e); } });
        Thread.sleep(450); waitForIdleSync();
    }
    private void touch(int action, float x) {
        MotionEvent event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(event); event.recycle();
    }
    private void peek() throws Exception {
        guard();
        View viewport = (View)field(activity, "viewport");
        int[] loc = new int[2], size = new int[2];
        runOnMainSync(() -> { viewport.getLocationOnScreen(loc); size[0] = viewport.getWidth(); size[1] = viewport.getHeight(); });
        float start = loc[0] + size[0] * .85f; endX = loc[0] + size[0] * .43f; y = loc[1] + size[1] * .5f;
        holding = true; downAt = SystemClock.uptimeMillis(); touch(MotionEvent.ACTION_DOWN, start);
        for (int i=1; i<=8; i++) { Thread.sleep(35); touch(MotionEvent.ACTION_MOVE, start+(endX-start)*i/8); }
        Thread.sleep(200);
    }
    private int buttonCount(View view) {
        int total = view instanceof Button && ("退出登录".contentEquals(((Button)view).getText())
            || "未登录".contentEquals(((Button)view).getText()) || "退出中".contentEquals(((Button)view).getText())) ? 1 : 0;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) total += buttonCount(((ViewGroup)view).getChildAt(i));
        return total;
    }
    private void checkPreviewPixels() throws Exception {
        guard();
        View viewport = (View)field(activity,"viewport"), incoming = (View)field(activity,"pinduoduoFrame");
        Rect[] box = {null};
        runOnMainSync(() -> {
            int[] pos = new int[2]; viewport.getLocationInWindow(pos);
            int left = pos[0] + (int)incoming.getTranslationX();
            // Only the incoming identity-card body; skip its title and border.
            int inset = (int)(18*activity.getResources().getDisplayMetrics().density);
            box[0] = new Rect(left+inset, pos[1]+(int)(90*activity.getResources().getDisplayMetrics().density),
                pos[0]+viewport.getWidth()-inset, pos[1]+(int)(280*activity.getResources().getDisplayMetrics().density));
        });
        Bitmap pixels = Bitmap.createBitmap(box[0].width(),box[0].height(),Bitmap.Config.ARGB_8888);
        CountDownLatch latch = new CountDownLatch(1); int[] status = {-1};
        try {
            runOnMainSync(() -> PixelCopy.request(activity.getWindow(),box[0],pixels,result -> { status[0]=result; latch.countDown(); },new Handler(Looper.getMainLooper())));
            waitFor(latch); guard(); check(status[0]==PixelCopy.SUCCESS,"Own-window compositor capture");
            int ink=0;
            for(int row=0;row<pixels.getHeight();row+=2) for(int column=0;column<pixels.getWidth();column+=2) {
                int color=pixels.getPixel(column,row);
                if(Color.red(color)<110 && Color.green(color)<110 && Color.blue(color)<110) ink++;
            }
            check(ink>100,"Incoming card body contains rendered content during drag");
        } finally { pixels.recycle(); }
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent launch = new Intent().setClassName("com.fairycrane.handycode", "com.fairycrane.handycode.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); launch.putExtra("provider", "taobao");
            activity = startActivitySync(launch); Thread.sleep(700); guard();
            int[] buttons={0};runOnMainSync(()->buttons[0]=buttonCount(activity.getWindow().getDecorView()));
            check(buttons[0]==1,"Exactly one shared logout button");
            select(1);
            Object pdd = field(activity,"pddPage"); WebView web = (WebView)field(pdd,"web");
            boolean ready=false;
            for(int i=0;i<60;i++) {
                guard();
                if("true".equals(eval(web,"(function(){var a=document.getElementById('hc-pdd-identity'),b=document.getElementById('hc-pdd-parcels');return !!(a&&b&&a.dataset.ready==='true'&&b.dataset.ready==='true'&&getComputedStyle(a.firstElementChild).opacity==='1'&&getComputedStyle(b.firstElementChild).opacity==='1')})()"))){ready=true;break;}
                Thread.sleep(350);
            }
            check(ready,"Official PDD cards ready before retention test");
            long navigation=(Long)field(pdd,"navigation");
            eval(web,"window.__hcRetainProbe=[document.getElementById('hc-pdd-identity').firstElementChild,document.getElementById('hc-pdd-parcels').firstElementChild];true");
            for(int round=0;round<3;round++) {
                select(0); peek();
                SwitchMotion motion=(SwitchMotion)field(activity,"switchMotion");
                check(motion.position()>.2f&&motion.position()<.5f,"Held partial swipe");
                check(web==field(pdd,"web"),"Same PDD WebView retained");
                check(navigation==(Long)field(pdd,"navigation"),"No tab-triggered navigation");
                check("true".equals(eval(web,"(function(){var a=document.getElementById('hc-pdd-identity'),b=document.getElementById('hc-pdd-parcels');return !!(a.firstElementChild===window.__hcRetainProbe[0]&&b.firstElementChild===window.__hcRetainProbe[1]&&getComputedStyle(a.firstElementChild).opacity==='1'&&getComputedStyle(b.firstElementChild).opacity==='1')})()")),"Retained nodes remain opaque during peek");
                checkPreviewPixels();
                touch(MotionEvent.ACTION_CANCEL,endX); holding=false; Thread.sleep(400);
                select(1);
                Button logout=(Button)field(activity,"logoutButton");
                check("退出拼多多登录".contentEquals(logout.getContentDescription()),"Logout follows settled platform");
            }
            eval(web,"delete window.__hcRetainProbe;true");
            result.putString("stream","PASS: "+checks+" live retention/UI checks, including three held previews; pixels analyzed in memory only\n");
        } catch(Throwable error) {
            result.putString("failure","pager retention probe");
            result.putString("stream","FAIL/SKIP: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");
        } finally {
            if(holding) try{guard();touch(MotionEvent.ACTION_CANCEL,endX);}catch(Exception ignored){}
        }
        finish(result.containsKey("failure")?0:-1,result);
    }
}
