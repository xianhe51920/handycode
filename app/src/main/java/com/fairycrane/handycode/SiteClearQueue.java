package com.fairycrane.handycode;

import java.util.function.Consumer;

/** Sequential site deletion; each async callback is accepted at most once. */
public final class SiteClearQueue {
    public interface Deleter { void delete(String site, Runnable complete); }
    private final String[] sites;
    private final Deleter deleter;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private int next;
    private boolean done;
    private SiteClearQueue(String[] sites, Deleter deleter, Runnable success, Consumer<Throwable> failure) {
        this.sites = sites.clone(); this.deleter = deleter; this.success = success; this.failure = failure;
    }
    public static void run(String[] sites, Deleter deleter, Runnable success, Consumer<Throwable> failure) {
        new SiteClearQueue(sites, deleter, success, failure).advance();
    }
    private void advance() {
        if (done) return;
        if (next == sites.length) { done = true; success.run(); return; }
        final int step = ++next;
        final boolean[] called = {false};
        try {
            deleter.delete(sites[step - 1], () -> {
                if (done || called[0]) return;
                called[0] = true; advance();
            });
        } catch (RuntimeException | LinkageError error) {
            if (!done && !called[0]) { done = true; failure.accept(error); }
        }
    }
}
