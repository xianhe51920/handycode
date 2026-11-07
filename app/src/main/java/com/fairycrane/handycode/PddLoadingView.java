package com.fairycrane.handycode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Native loading skeleton matching the dual-card layout in Light and Dark modes. */
final class PddLoadingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float scale, identityHeight = 310, parcelsHeight = 140, headingHeight = 47;
    private ValueAnimator morph;
    private boolean animating;
    private boolean dark;
    private long animationStarted;
    private final String firstTitle, secondTitle;

    PddLoadingView(Context context) {
        this(context, "身份码", "待取包裹");
    }

    PddLoadingView(Context context, String firstTitle, String secondTitle) {
        super(context);
        this.firstTitle = firstTitle;
        this.secondTitle = secondTitle;
        density = context.getResources().getDisplayMetrics().density;
        resetGeometry();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setDark(boolean value) {
        dark = value;
        setBackgroundColor(AppTheme.bg(dark));
        invalidate();
    }

    void resetGeometry() {
        stopMorph();
        scale = density;
        identityHeight = 310;
        parcelsHeight = 140;
        headingHeight = 47;
        setBackgroundColor(AppTheme.bg(dark));
        invalidate();
    }

    void morphTo(float cssWidth, float identity, float parcels, float heading) {
        if (cssWidth <= 0 || getWidth() <= 0 || identity <= 0 || heading <= 0) return;
        stopMorph();
        final float oldScale = scale, oldIdentity = identityHeight, oldParcels = parcelsHeight, oldHeading = headingHeight;
        final float nextScale = getWidth() / cssWidth;
        if (nextScale <= 0) return;
        morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(180);
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

    void stopMorph() { if (morph != null) morph.cancel(); }

    void setAnimating(boolean value) {
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
        int peak = dark ? 0x30ffffff : 0xb3ffffff;
        paint.setShader(new LinearGradient(center - w * .35f, 0, center + w * .35f, 0,
                new int[]{0x00ffffff, peak, 0x00ffffff}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), 7, 7, paint);
        paint.setShader(null);
    }

    private void card(Canvas canvas, float y, float width, float bodyHeight, String title, boolean identity) {
        rect(canvas, 10, y, width - 20, bodyHeight + headingHeight, 16, AppTheme.border(dark));
        rect(canvas, 11, y + 1, width - 22, bodyHeight + headingHeight - 2, 15, AppTheme.surface(dark));
        paint.setColor(AppTheme.divider(dark));
        canvas.drawRect(11, y + headingHeight - 1, width - 11, y + headingHeight, paint);
        paint.setColor(AppTheme.ink(dark));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(16);
        canvas.drawText(title, 25, y + 29, paint);
        paint.setColor(dark ? 0xff636c7e : 0xffb6bec9);
        paint.setTextSize(12);
        canvas.drawText("刷新", width - 49, y + 29, paint);
        float inner = width - 56;
        if (identity) {
            skeleton(canvas, width * .22f, y + 72, width * .56f, 18);
            skeleton(canvas, 28, y + 112, inner, 100);
            skeleton(canvas, width * .19f, y + 236, width * .62f, 14);
            skeleton(canvas, width * .24f, y + 274, width * .52f, 12);
            skeleton(canvas, width * .15f, y + 310, width * .7f, 12);
        } else {
            skeleton(canvas, 28, y + 70, inner, 16);
            skeleton(canvas, width * .23f, y + 104, width * .54f, 13);
            skeleton(canvas, width * .24f, y + 140, width * .52f, 22);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(scale, scale);
        float width = getWidth() / scale;
        card(canvas, 12, width, identityHeight, firstTitle, true);
        float noteY = 12 + headingHeight + identityHeight + 26;
        if (noteY <= getHeight() / scale - 10) {
            paint.setShader(null);
            paint.setColor(AppTheme.muted(dark));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(12);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("正在准备身份码…", width / 2, noteY, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        canvas.restore();
        if (animating && isShown() && getAlpha() > 0) postInvalidateOnAnimation();
    }
}