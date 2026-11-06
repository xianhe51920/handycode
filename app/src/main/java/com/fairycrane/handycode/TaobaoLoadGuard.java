package com.fairycrane.handycode;

/** Tracks the remaining foreground time without depending on the WebView. */
public final class TaobaoLoadGuard {
    private static final long TIMEOUT_MS = 30_000;
    private long remaining;
    private long resumedAt = -1;
    private boolean waiting;

    public void begin() {
        if (waiting) return;
        waiting = true;
        remaining = TIMEOUT_MS;
    }

    public void stop() {
        waiting = false;
        resumedAt = -1;
    }

    public void pause(long now) {
        if (resumedAt < 0) return;
        remaining = remainingAt(now);
        resumedAt = -1;
    }

    public void resume(long now) {
        if (waiting && resumedAt < 0) resumedAt = now;
    }

    /** Returns -1 while inactive, 0 at the deadline, or the time until it. */
    public long delay(long now) {
        return resumedAt < 0 ? -1 : remainingAt(now);
    }

    private long remainingAt(long now) {
        return Math.max(0, remaining - (now - resumedAt));
    }
}
