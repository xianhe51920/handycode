package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Fixed banner selectors/geometry only; never returns form or account values. */
public final class TaobaoLoginProbe extends Instrumentation {
    private boolean inspect, health;
    @Override public void onCreate(Bundle args) { super.onCreate(args); inspect="true".equals(args.getString("inspect")); health="true".equals(args.getString("health")); start(); }
    private String eval(WebView web,String script) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);String[] value={null};
        runOnMainSync(()->web.evaluateJavascript(script,result->{value[0]=result;latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JS callback timeout");return value[0];
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Intent launch=new Intent().setClassName("com.fairycrane.handycode","com.fairycrane.handycode.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);launch.putExtra("provider","taobao");
            Activity activity=startActivitySync(launch);
            Field field=activity.getClass().getDeclaredField("browser");field.setAccessible(true);WebView web=(WebView)field.get(activity);
            boolean login=false, authenticated=false;
            for(int i=0;i<65;i++) {
                if(health&&"true".equals(eval(web,"(function(){var a=document.getElementById('identity-content'),b=document.getElementById('parcels-content');return !!(document.getElementById('handycode-merged')&&a&&b&&a.dataset.ready==='true'&&b.dataset.ready==='true')})()"))){authenticated=true;break;}
                if("true".equals(eval(web,"!!(window.HandyCodeOfficialIdentity&&HandyCodeOfficialIdentity.loginVisible(document))"))){login=true;break;}
                Thread.sleep(300);
            }
            if(authenticated) {
                result.putString("stream","PASS: Taobao authenticated identity and parcel cards ready; login-only check inapplicable, no account change\n");
            } else if(!login)throw new AssertionError("Official login/content view not available; no account change attempted");
            else if(inspect) {
                String metadata=eval(web,"(function(){var m='您需要登录才能继续访问',w=document.createTreeWalker(document.body,4),n,out=[];while(n=w.nextNode()){if((n.nodeValue||'').replace(/\\s/g,'')!==m)continue;for(var p=n.parentElement,k=0;p&&k<4;p=p.parentElement,k++){var r=p.getBoundingClientRect();out.push({tag:p.tagName,classes:typeof p.className==='string'?p.className:'',height:Math.round(r.height),position:getComputedStyle(p).position,children:Array.from(p.children).map(function(c){return c.tagName}),frames:p.querySelectorAll('iframe').length});}}return out;})()");
                result.putString("stream","Fixed login-banner metadata: "+metadata+"\n");
            } else {
                String success=eval(web,"(function(){var w=document.createTreeWalker(document.body,4),n;while(n=w.nextNode()){if((n.nodeValue||'').replace(/\\s/g,'')!=='您需要登录才能继续访问')continue;var h=n.parentElement;if(h.getClientRects().length)return false;var p=h.parentElement,f=p&&p.querySelector('iframe');if(f&&f.getClientRects().length&&Math.abs(f.getBoundingClientRect().top-p.getBoundingClientRect().top)>2)return false;}return !!(window.HandyCodeTaobaoLoginChrome&&HandyCodeOfficialIdentity.loginVisible(document));})()");
                if(!"true".equals(success))throw new AssertionError("Login banner remains visible or login view changed");
                result.putString("stream","PASS: Taobao login banner removed; official login frame/form still visible; no fields read or submitted\n");
            }
        } catch(Throwable error) {
            result.putString("failure","taobao login banner probe");
            result.putString("stream","FAIL/SKIP: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");
        }
        finish(result.containsKey("failure")?0:-1,result);
    }
}
