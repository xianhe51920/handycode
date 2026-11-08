package com.fairycrane.handycode;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Opaque first-frame cover. Never captures or retains the official page. */
final class PickupLoadingView extends View {
    static final int REVEAL_DURATION = 260;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean pdd;
    private final boolean identityOnly;
    private float scale, identityHeight, parcelsHeight, headingHeight;
    private ValueAnimator morph;
    private boolean animating;
    private boolean dark;
    private boolean openingPhoneManagement;
    private long animationStarted;

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
        identityHeight = pdd ? 174 : 140;
        parcelsHeight = pdd ? 140 : 160;
        headingHeight = 53;
        invalidate();
    }

    void morphTo(float cssWidth, float identity, float parcels, float heading) {
        if (cssWidth < 240 || cssWidth > 2000 || identity < 32 || identity > 10000
                || parcels < 32 || parcels > 10000 || heading < 20 || heading > 200) return;
        if (morph != null) morph.cancel();
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

    void stopMorph() { if (morph != null) morph.cancel(); }

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

    private void skeleton(Canvas canvas, float x, float y, float w, float h) {
        rect(canvas, x, y, w, h, 7, AppTheme.shimmerBase(dark));
        float progress = animating ? ((SystemClock.uptimeMillis() - animationStarted) % 1400L) / 1400f : 0f;
        float center = x - w * .4f + progress * w * 1.8f;
        paint.setShader(new LinearGradient(center - w * .35f, 0, center + w * .35f, 0,
                new int[]{0x00ffffff, AppTheme.shimmerPeak(dark), 0x00ffffff}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), 7, 7, paint);
        paint.setShader(null);
    }

    private void card(Canvas canvas, float y, float width, float bodyHeight, String title, boolean identity) {
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
        float inner = width - 58, top = y + 1 + headingHeight;
        float center = top + bodyHeight / 2;
        if (identity) {
            bar(canvas, width, inner * .32f, pdd ? top + 10 : center - 49, 16);
            bar(canvas, width, inner, pdd ? top + 38 : center - 23, pdd ? 100 : 72);
            if (pdd) bar(canvas, width, inner * .70f, top + 150, 14);
        } else if (pdd) {
            bar(canvas, width, inner * .94f, center - 35, 14);
            bar(canvas, width, inner * .94f, center - 11, 14);
            bar(canvas, width, inner * .78f, center + 13, 22);
        } else {
            bar(canvas, width, inner * .68f, center - 46, 14);
            bar(canvas, width, inner * .50f, center - 22, 44);
            bar(canvas, width, inner * .68f, center + 32, 14);
        }
    }

    private void bar(Canvas canvas, float width, float size, float y, float height) {
        if (size > 0) skeleton(canvas, (width - size) / 2, y, size, height);
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
        card(canvas, 12, width, identityHeight, "身份码", true);
        float parcelsTop = 28 + headingHeight + identityHeight;
        if (!identityOnly) card(canvas, parcelsTop, width, parcelsHeight, "待取包裹", false);
        // Mirrors the Taobao first-load note so both tabs explain the wait.
        // Drawn only when the cards leave room, so it never covers content.
        float noteY = identityOnly ? parcelsTop + 10 : parcelsTop + headingHeight + parcelsHeight + 26;
        if (noteY <= getHeight() / scale - 10) {
            paint.setShader(null);
            paint.setColor(AppTheme.muted(dark));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(12);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(identityOnly ? "正在准备身份码…" : "正在准备取件信息…", width / 2, noteY, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        canvas.restore();
        if (animating && isShown() && getAlpha() > 0) postInvalidateOnAnimation();
    }
}
