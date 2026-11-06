package com.fairycrane.handycode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Presentation-only loading placeholder; never draws a real or mock barcode. */
final class ShimmerBlock extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Matrix translation = new Matrix();
    private final float radius;
    private LinearGradient highlight;
    private float phase, band;
    private boolean dark;

    ShimmerBlock(Context context, float radius) {
        super(context);
        this.radius = radius;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPhase(float value) { phase = value; invalidate(); }

    void setDark(boolean value) {
        if (dark == value) return;
        dark = value;
        rebuildGradient();
        invalidate();
    }

    private void rebuildGradient() {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        band = Math.max(width * 0.45f, 32 * getResources().getDisplayMetrics().density);
        int peak = AppTheme.shimmerPeak(dark);
        highlight = new LinearGradient(-band, 0, band, Math.max(1, height),
            new int[]{0x00ffffff, peak, 0x00ffffff}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        bounds.set(0, 0, width, height);
        rebuildGradient();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setShader(null);
        paint.setColor(AppTheme.shimmerBase(dark));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        if (highlight != null) {
            translation.setTranslate((getWidth() + 2 * band) * phase - band, 0);
            highlight.setLocalMatrix(translation);
            paint.setShader(highlight);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
    }
}