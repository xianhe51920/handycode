package com.fairycrane.handycode;

import android.content.Context;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Opaque first-frame cover. Never captures or retains the official page. */
final class PickupLoadingView extends View {
    static final int REVEAL_DURATION = 260;
    static final int FADE_DURATION = 180;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean pdd;
    private final boolean identityOnly;
    private float scale, identityHeight, parcelsHeight, headingHeight, identityTop = 12;
    private ValueAnimator morph;
    private boolean animating;
    private boolean dark;
    private boolean openingPhoneManagement;
    private long animationStarted;
    private boolean identityMatched;

    static float identityBodyHeight(float cssWidth) { return Math.max(202, Math.min(226, 274 - .2f * cssWidth)); }

    PickupLoadingView(Context context, boolean pdd) {
        this(context, pdd, false);
    }

    PickupLoadingView(Context context, boolean pdd, boolean identityOnly) {
        super(context);
        this.pdd = pdd;
        this.identityOnly = identityOnly;
        resetGeometry();
        setBackgroundColor(0xfff5f6f8);
        setClickable(true);
        setContentDescription(identityOnly ? "正在准备身份码" : "正在准备身份码和待取包裹");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void resetGeometry() {
        stopMorph();
        scale = getResources().getDisplayMetrics().density;
        identityMatched = false;
        float width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        identityHeight = identityBodyHeight(width / scale);
        parcelsHeight = pdd ? 140 : 160;
        headingHeight = 53;
        identityTop = 12;
        invalidate();
    }

    void morphTo(float cssWidth, float identity, float parcels, float heading) {
        if (cssWidth < 240 || cssWidth > 2000 || identity < 32 || identity > 10000
                || parcels < 32 || parcels > 10000 || heading < 20 || heading > 200) return;
        if (morph != null) morph.cancel();
        identityMatched = true;
        final float oldScale = scale, oldIdentity = identityHeight, oldParcels = parcelsHeight, oldHeading = headingHeight;
        final float nextScale = getWidth() / cssWidth;
        if (nextScale <= 0) return;
        // The official page can use a different WebView overview scale.
        // Match its non-sensitive geometry while fading, rather than snapping
        // between native density and CSS pixels. Retain only these dimensions.
        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(REVEAL_DURATION);
        morph.addUpdateListener(animation -> {
            float t = (Float)animation.getAnimatedValue();
            scale = oldScale + (nextScale - oldScale) * t;
            identityHeight = oldIdentity + (identity - oldIdentity) * t;
            parcelsHeight = oldParcels + (parcels - oldParcels) * t;
            headingHeight = oldHeading + (heading - oldHeading) * t;
            invalidate();
        });
        morph.start();
    }

    void setDark(boolean value) {
        dark = value;
        setBackgroundColor(dark ? AppTheme.bg(true) : 0xfff5f6f8);
        invalidate();
    }

    void morphTo(float width, float identity, float parcels, float heading, Runnable matched) {
        morphTo(width, identity, parcels, heading);
        afterMorph(matched);
    }

    void morphIdentityTo(float width, float top, float body, float heading, Runnable matched) {
        morphIdentityTo(width, top, body, heading);
        afterMorph(matched);
    }

    private void afterMorph(Runnable matched) {
        ValueAnimator expected = morph;
        if (expected == null || !expected.isRunning()) { post(matched); return; }
        expected.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled && morph == expected) matched.run();
            }
        });
    }

    void matchIdentityGeometry(float cssWidth, float top, float body, float heading) {
        if (!validIdentityGeometry(cssWidth, top, body, heading) || getWidth() <= 0) return;
        stopMorph();
        identityMatched = true;
        scale = getWidth() / cssWidth;
        identityTop = top;
        identityHeight = body;
        headingHeight = heading;
        invalidate();
    }

    void morphIdentityTo(float cssWidth, float top, float body, float heading) {
        if (!validIdentityGeometry(cssWidth, top, body, heading) || getWidth() <= 0) return;
        stopMorph();
        identityMatched = true;
        final float fromScale = scale, fromTop = identityTop, fromBody = identityHeight, fromHeading = headingHeight;
        final float targetScale = getWidth() / cssWidth;
        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(REVEAL_DURATION);
        morph.addUpdateListener(animation -> {
            float t = (Float) animation.getAnimatedValue();
            scale = fromScale + (targetScale - fromScale) * t;
            identityTop = fromTop + (top - fromTop) * t;
            identityHeight = fromBody + (body - fromBody) * t;
            headingHeight = fromHeading + (heading - fromHeading) * t;
            invalidate();
        });
        morph.start();
    }

    private boolean validIdentityGeometry(float width, float top, float body, float heading) {
        return identityOnly && width >= 240 && width <= 2000 && top >= -10000 && top <= 10000
            && body >= 32 && body <= 10000 && heading >= 20 && heading <= 200;
    }

    void stopMorph() { if (morph != null) morph.cancel(); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!identityMatched && width > 0 && scale > 0) identityHeight = identityBodyHeight(width / scale);
    }

    void setOpeningPhoneManagement(boolean opening) {
        openingPhoneManagement = opening;
        setContentDescription(opening ? "正在打开号码管理" : "正在准备身份码和待取包裹");
        invalidate();
    }

    void setAnimating(boolean value) {
        value = value && ValueAnimator.areAnimatorsEnabled();
        if (animating == value) return;
        animating = value;
        animationStarted = SystemClock.uptimeMillis();
        invalidate();
    }

    private void rect(Canvas canvas, float x, float y, float w, float h, float radius, int color) {
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), radius, radius, paint);
    }

    private void card(Canvas canvas, float y, float width, float bodyHeight, String title) {
        rect(canvas, 10, y, width - 20, bodyHeight + headingHeight + 2, 16, AppTheme.border(dark));
        rect(canvas, 11, y + 1, width - 22, bodyHeight + headingHeight, 15, AppTheme.surface(dark));
        paint.setColor(AppTheme.divider(dark));
        canvas.drawRect(11, y + headingHeight - 1, width - 11, y + headingHeight, paint);
        paint.setColor(AppTheme.ink(dark));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(16);
        canvas.drawText(title, 25, y + headingHeight / 2 + 6, paint);
        paint.setColor(0xffb6bec9);
        paint.setTextSize(12);
        canvas.drawText("刷新", width - 49, y + headingHeight / 2 + 5, paint);
        float top = y + 1 + headingHeight;
        float center = top + bodyHeight / 2;
        float progress = animating ? ((SystemClock.uptimeMillis() - animationStarted) % 1100L) / 1100f : 0f;
        LoadingDots.draw(canvas, paint, width / 2, center, 1, dark, progress);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(scale, scale);
        float width = getWidth() / scale;
        if (openingPhoneManagement) {
            paint.setColor(AppTheme.muted(dark));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(13);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("正在打开号码管理…", width / 2, getHeight() / scale / 2, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
            return;
        }
        card(canvas, identityTop, width, identityHeight, "身份码");
        float parcelsTop = identityTop + 16 + headingHeight + identityHeight;
        if (!identityOnly) card(canvas, parcelsTop, width, parcelsHeight, "待取包裹");
        canvas.restore();
        if (animating && isShown() && getAlpha() > 0) postInvalidateOnAnimation();
    }
}
