package com.fairycrane.handycode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import java.util.function.Consumer;

/** Fades the old content over the new palette while leaving the title bar live. */
final class ThemeTransition {
    private static final long DURATION_MS = 320;
    private static final long COPY_TIMEOUT_MS = 75;
    private final Window window;
    private final View root, content;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private ValueAnimator fade;
    private BitmapDrawable previous;
    private float position;
    private boolean prepared;
    private Progress progress;
    private Runnable completion;

    interface Progress { void apply(float value); }
    interface Palette { void apply(Runnable painted); }

    ThemeTransition(Window window, View root, View content) {
        this.window = window;
        this.root = root;
        this.content = content;
    }

    void start(Palette applyPalette, Progress progress) {
        if (!ValueAnimator.areAnimatorsEnabled() || !root.isShown() || root.getWidth() == 0) {
            cancel();
            applyPalette.apply(() -> progress.apply(1f));
            return;
        }
        begin(applyPalette, progress, this::animate);
    }

    private void animate() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        fade = animator;
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(value -> value * value * (3f - 2f * value));
        animator.addUpdateListener(animation -> seek((float)animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (fade != animation) return;
                fade = null;
                finish(() -> {});
            }
        });
        animator.start();
    }

    void begin(Palette applyPalette, Progress listener) {
        begin(applyPalette, listener, () -> {});
    }

    private void begin(Palette applyPalette, Progress listener, Runnable onPrepared) {
        generation++;
        stopFade();
        position = 0f;
        prepared = false;
        completion = null;
        progress = listener;
        capture(frame -> {
            clearFrame();
            previous = frame;
            if (frame != null) root.getOverlay().add(frame);
            final int token = generation;
            applyPalette.apply(() -> {
                if (generation != token) return;
                prepared = true;
                seek(position);
                Runnable pending = completion;
                completion = null;
                if (pending != null) pending.run();
                else onPrepared.run();
            });
        });
    }

    private void capture(Consumer<BitmapDrawable> done) {
        int token = generation;
        int[] rootLocation = new int[2];
        int[] contentLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        content.getLocationInWindow(contentLocation);
        Rect windowArea = new Rect(rootLocation[0], contentLocation[1],
            rootLocation[0] + root.getWidth(), rootLocation[1] + root.getHeight() - root.getPaddingBottom());
        if (windowArea.isEmpty()) {
            done.accept(null);
            return;
        }
        int width = windowArea.width();
        int height = windowArea.height();
        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Runnable timeout = () -> {
            if (generation == token) {
                generation++;
                done.accept(null);
            }
        };
        main.postDelayed(timeout, COPY_TIMEOUT_MS);
        try {
            PixelCopy.request(window, new Rect(windowArea), frame, result -> {
                main.removeCallbacks(timeout);
                if (generation != token) return;
                BitmapDrawable snapshot = null;
                if (result == PixelCopy.SUCCESS) {
                    snapshot = new BitmapDrawable(root.getResources(), frame);
                    snapshot.setTargetDensity(frame.getDensity());
                    snapshot.setGravity(Gravity.FILL);
                    snapshot.setFilterBitmap(false);
                    Rect overlayArea = new Rect(windowArea);
                    overlayArea.offset(-rootLocation[0], -rootLocation[1]);
                    snapshot.setBounds(overlayArea);
                }
                done.accept(snapshot);
            }, main);
        } catch (IllegalArgumentException unavailable) {
            main.removeCallbacks(timeout);
            timeout.run();
        }
    }

    void seek(float value) {
        position = Math.max(0f, Math.min(1f, value));
        if (!prepared) return;
        if (previous != null) previous.setAlpha(Math.round(255f * (1f - position)));
        progress.apply(position);
    }

    void finish(Runnable action) {
        if (!prepared) {
            completion = () -> finish(action);
            return;
        }
        action.run();
        cancel();
    }

    void finish(Palette palette) {
        if (!prepared) { completion = () -> finish(palette); return; }
        final int token = generation;
        palette.apply(() -> { if (generation == token) cancel(); });
    }

    void cancel() {
        generation++;
        stopFade();
        clearFrame();
        prepared = false;
        progress = null;
        completion = null;
    }

    private void stopFade() {
        ValueAnimator current = fade;
        fade = null;
        if (current != null) current.cancel();
    }

    private void clearFrame() {
        if (previous != null) {
            root.getOverlay().remove(previous);
            previous = null;
        }
    }
}
