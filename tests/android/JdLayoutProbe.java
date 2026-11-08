package com.fairycrane.handycode.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Checks presentation and navigation flags only, without reading order or login values. */
public final class JdLayoutProbe extends Instrumentation {
    private Activity activity;
    private Object page;
    private int checks;
    private String stage = "initial load";
    private int orderResults, errorResults;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private Object field(Object owner, String name) throws Exception {
        Field value = owner.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.get(owner);
    }

    private Object call(Object owner, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(owner, arguments);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void ui(Runnable task) {
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try {
                if (!activity.hasWindowFocus() || activity.isFinishing()) throw new IllegalStateException("Own app lost focus");
                task.run();
            } catch (Throwable problem) { failure[0] = problem; }
        });
        if (failure[0] != null) throw new AssertionError("JD UI check failed", failure[0]);
    }

    private void cardsOnly() throws Exception {
        View results = (View)field(page, "results");
        WebView web = (WebView)field(page, "web");
        check(results.getVisibility() == View.VISIBLE, "Cards cover the official document");
        check(web != null && web.getVisibility() == View.INVISIBLE, "Official document remains hidden");
    }

    private void waitForOrders() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] settled = {false};
            ui(() -> {
                try {
                    if (!(Boolean)field(page, "loginVisible")) cardsOnly();
                    settled[0] = !(Boolean)field(page, "loading") && !((Boolean)field(page, "loginVisible"))
                        && (!((String)field(page, "lastSnapshot")).isEmpty() || !((String)field(page, "error")).isEmpty());
                } catch (Exception problem) { throw new RuntimeException(problem); }
            });
            if (settled[0]) {
                if (!((String)field(page, "error")).isEmpty()) errorResults++; else orderResults++;
                return;
            }
            Thread.sleep(200);
        }
        boolean[] flags = new boolean[4];
        ui(() -> {
            try {
                flags[0] = (Boolean)field(page, "loading");
                flags[1] = (Boolean)field(page, "loginVisible");
                flags[2] = !((String)field(page, "lastSnapshot")).isEmpty();
                flags[3] = !((String)field(page, "error")).isEmpty();
            } catch (Exception problem) { throw new RuntimeException(problem); }
        });
        throw new AssertionError(stage + " did not settle; loading=" + flags[0] + ",login=" + flags[1]
            + ",snapshot=" + flags[2] + ",error=" + flags[3]);
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent().setClassName("com.fairycrane.handycode", "com.fairycrane.handycode.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("provider", "jd");
            activity = startActivitySync(intent);
            Thread.sleep(500);
            page = field(activity, "jdPage");
            waitForOrders();
            for (int round = 0; round < 3; round++) {
                stage = "refresh " + (round + 1);
                ui(() -> {
                    try {
                        View body = (View)field(page, "body");
                        check(!body.hasOnClickListeners() && !body.performClick(), "Empty body does not navigate");
                        call(page, "reload", new Class<?>[0]);
                        check((Boolean)field(page, "loading"), "Refresh enters loading state");
                        cardsOnly();
                        check(!((TextView)field(page, "refresh")).isEnabled(), "Refresh action is disabled while pending");
                        int generation = (Integer)field(page, "generation");
                        call(page, "reload", new Class<?>[0]);
                        check(generation == (Integer)field(page, "generation"), "Repeated refresh is coalesced");
                    } catch (Exception problem) { throw new RuntimeException(problem); }
                });
                waitForOrders();
            }
            ui(() -> {
                try {
                    View heading = (View)field(page, "heading");
                    check(((android.view.ViewGroup)heading).getChildCount() == 2, "Header contains only title and refresh");
                    check(!(Boolean)call(page, "goBack", new Class<?>[0]), "Card back action does not reveal the source document");
                    cardsOnly();
                } catch (Exception problem) { throw new RuntimeException(problem); }
            });
            Object retained = field(page, "web");
            ui(() -> {
                try { call(activity, "selectPage", new Class<?>[]{int.class}, 2); }
                catch (Exception problem) { throw new RuntimeException(problem); }
            });
            Thread.sleep(500);
            ui(() -> {
                try { call(activity, "selectPage", new Class<?>[]{int.class}, 3); }
                catch (Exception problem) { throw new RuntimeException(problem); }
            });
            Thread.sleep(500);
            waitForOrders();
            ui(() -> {
                try {
                    check(field(page, "web") == retained, "Tab switching retains the official document");
                    cardsOnly();
                } catch (Exception problem) { throw new RuntimeException(problem); }
            });
            result.putString("stream", "PASS: " + checks + " JD checks covering refresh occlusion, repeated actions, compact header, back and tab retention; orderResults="
                + orderResults + ",errorResults=" + errorResults + "\n");
        } catch (Throwable problem) {
            while (problem.getCause() != null) problem = problem.getCause();
            result.putString("failure", "JD presentation probe");
            result.putString("stream", "FAIL/SKIP: " + problem.getClass().getSimpleName() + ": " + problem.getMessage() + "\n");
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
