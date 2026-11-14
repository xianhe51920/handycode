package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Distinguishes successful empty results, cached emptiness and unavailable results. */
public final class JdEmptyStateProbe extends Instrumentation {
    private Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private void call(Object owner, String name, Class<?> type, Object argument) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(owner, argument);
    }

    private boolean has(View view, String label) {
        if (view instanceof TextView && label.equals(((TextView)view).getText().toString())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++)
            if (has(((ViewGroup)view).getChildAt(i), label)) return true;
        return false;
    }

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        StringBuilder report = new StringBuilder();
        try {
            Intent intent = new Intent().setClassName("com.fairycrane.handycode", "com.fairycrane.handycode.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("provider", "jd");
            Activity activity = startActivitySync(intent);
            Thread.sleep(1000);
            Object live = field(activity, "jdPage");
            for (int attempt = 0; attempt < 100; attempt++) {
                boolean[] loading = {true};
                runOnMainSync(() -> {
                    try { loading[0] = (Boolean)field(live, "loading"); }
                    catch (Exception problem) { throw new RuntimeException(problem); }
                });
                if (!loading[0]) break;
                Thread.sleep(200);
            }
            WebView web = (WebView)field(live, "web");
            CountDownLatch latch = new CountDownLatch(1);
            runOnMainSync(() -> web.evaluateJavascript(
                "(()=>{const s=window.HandyJdOrders?.snapshot();return {ready:s?.ready===true,problem:s?.problem||'',hasCodes:s?.orders.some(o=>!!o.code)||false};})()",
                value -> { report.append("live official flags ").append(value).append('\n'); latch.countDown(); }));
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Official flag check timed out");
            Throwable[] failure = {null};
            runOnMainSync(() -> {
                try {
                    String cached = (String)field(live, "lastSnapshot");
                    JSONArray rows = cached.isEmpty() ? null : new JSONObject(cached).optJSONArray("orders");
                    boolean cachedCodes = false;
                    if (rows != null) for (int i = 0; i < rows.length(); i++)
                        if (!rows.optJSONObject(i).optString("code").isEmpty()) cachedCodes = true;
                    report.append("live native flags snapshot=").append(!cached.isEmpty()).append(",hasCodes=").append(cachedCodes)
                        .append(",error=").append(!((String)field(live, "error")).isEmpty()).append('\n');
                    Class<?> type = live.getClass();
                    Constructor<?> constructor = type.getDeclaredConstructor(Context.class, Consumer.class, Runnable.class);
                    constructor.setAccessible(true);
                    Object isolated = constructor.newInstance(activity, (Consumer<Boolean>)value -> {}, (Runnable)() -> {});
                    set(isolated, "lastSnapshot", "{\"orders\":[]}");
                    set(isolated, "loading", false);
                    call(isolated, "setDark", boolean.class, true);
                    View body = (View)field(isolated, "body");
                    if (!has(body, "暂无待取包裹")) throw new AssertionError("Successful emptiness should use a normal empty state");
                    call(isolated, "fail", String.class, "网络连接中断，请重试。");
                    if (!has(body, "暂无待取包裹")) throw new AssertionError("Refresh failure must preserve cached emptiness");
                    if (!has(body, "上次查询暂无待取包裹，本次刷新未完成，请稍后重试。")) throw new AssertionError("Cached emptiness needs a separate refresh warning");
                    set(isolated, "lastSnapshot", "");
                    call(isolated, "fail", String.class, "网络连接中断，请重试。");
                    if (has(body, "暂无待取包裹")) throw new AssertionError("Unavailable results must not claim no parcels");
                    call(isolated, "fail", String.class, field(isolated, "ORDERS_BUSY"));
                    if (!has(body, "暂无待取包裹")) throw new AssertionError("Busy empty result uses the requested empty-state label");
                    if (has(body, "暂时无法读取订单") || has(body, "取件信息暂未更新") || has(body, "orders_busy"))
                        throw new AssertionError("Busy result must not display failure labels or internal status codes");
                    report.append("PASS: successful empty, cached empty after refresh failure, and unavailable result stay distinct\n");
                } catch (Throwable problem) { failure[0] = problem; }
            });
            if (failure[0] != null) throw failure[0];
        } catch (Throwable problem) {
            while (problem.getCause() != null) problem = problem.getCause();
            result.putString("failure", "JD empty state probe");
            report.append("FAIL/SKIP: ").append(problem.getClass().getSimpleName()).append(": ").append(problem.getMessage()).append('\n');
        }
        result.putString("stream", report.toString());
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
