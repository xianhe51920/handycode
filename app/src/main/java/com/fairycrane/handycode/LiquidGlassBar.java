package com.fairycrane.handycode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;

/**
 * iOS-style translucent Liquid Glass bottom switcher with real-time backdrop
 * frosting, liquid droplet stretch/swell, optical lens magnification, and
 * rolling specular/refractive highlights during tab transitions.
 */
final class LiquidGlassBar extends SwipeFrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF shellBounds = new RectF();
    private final RectF innerSheenBounds = new RectF();
    private final RectF dropletBounds = new RectF();
    private final RectF meniscusBounds = new RectF();
    private final Path shellPath = new Path();
    private final Path dropletPath = new Path();

    private View backdropSource;
    private Bitmap blurBuffer;
    private Canvas blurCanvas;
    private int[] blurPixels;
    private boolean capturingBackdrop;

    private Button[] tabs = new Button[0];
    private float position;
    private float lastPosition;
    private long lastFrameTime;
    private float smoothedVelocity;
    private float glassEnergy;
    private ValueAnimator energyAnimator;
    private boolean dark;

    LiquidGlassBar(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        strokePaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    void bindTabs(Button[] buttons) {
        this.tabs = buttons != null ? buttons : new Button[0];
        invalidate();
    }

    float slotSpan() {
        if (tabs.length >= 2 && tabs[0] != null && tabs[1] != null && tabs[1].getLeft() > tabs[0].getLeft()) {
            return tabs[1].getLeft() - tabs[0].getLeft();
        }
        return Math.max(dp(64), (getWidth() - dp(32)) / Math.max(1, tabs.length));
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            setGlassActive(true);
        } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
            if (Math.abs(position - Math.round(position)) < 0.01f) {
                setGlassActive(false);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    void setBackdropSource(View source) {
        this.backdropSource = source;
        invalidate();
    }

    void setDark(boolean value) {
        if (dark == value) return;
        dark = value;
        invalidate();
    }

    void setGlassActive(boolean active) {
        float target = active ? 1f : 0f;
        if (energyAnimator != null) energyAnimator.cancel();
        if (Math.abs(glassEnergy - target) < 0.01f) {
            glassEnergy = target;
            invalidate();
            return;
        }
        energyAnimator = ValueAnimator.ofFloat(glassEnergy, target);
        energyAnimator.setDuration(active ? 180 : 380);
        if (!active) energyAnimator.setInterpolator(new OvershootInterpolator(1.4f));
        energyAnimator.addUpdateListener(anim -> {
            glassEnergy = Math.max(0f, Math.min(1.25f, (Float) anim.getAnimatedValue()));
            invalidate();
        });
        energyAnimator.start();
    }

    void setSwitchPosition(float value, boolean moving) {
        long now = SystemClock.uptimeMillis();
        long dt = now - lastFrameTime;
        if (dt > 0 && dt < 200) {
            float instVel = (value - lastPosition) * 1000f / dt;
            smoothedVelocity = smoothedVelocity * 0.65f + instVel * 0.35f;
        } else if (!moving) {
            smoothedVelocity *= 0.5f;
        }
        lastPosition = position;
        lastFrameTime = now;
        position = value;
        if (moving && glassEnergy < 0.85f && (energyAnimator == null || !energyAnimator.isRunning())) {
            setGlassActive(true);
        } else if (!moving && glassEnergy > 0.01f && (energyAnimator == null || !energyAnimator.isRunning())) {
            setGlassActive(false);
        }
        invalidate();
    }

    private void captureAndBlurBackdrop() {
        if (backdropSource == null || getWidth() <= 0 || getHeight() <= 0) return;
        int scale = 6;
        int bw = Math.max(16, (int) Math.ceil(shellBounds.width() / scale));
        int bh = Math.max(8, (int) Math.ceil(shellBounds.height() / scale));
        if (blurBuffer == null || blurBuffer.getWidth() != bw || blurBuffer.getHeight() != bh) {
            blurBuffer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            blurCanvas = new Canvas(blurBuffer);
            blurPixels = new int[bw * bh];
        }
        blurBuffer.eraseColor(AppTheme.bg(dark));
        int[] barLoc = new int[2];
        int[] srcLoc = new int[2];
        getLocationInWindow(barLoc);
        backdropSource.getLocationInWindow(srcLoc);
        float offsetX = (barLoc[0] - srcLoc[0]) + shellBounds.left;
        float offsetY = (barLoc[1] - srcLoc[1]) + shellBounds.top;
        capturingBackdrop = true;
        try {
            blurCanvas.save();
            blurCanvas.scale(1f / scale, 1f / scale);
            blurCanvas.translate(-offsetX, -offsetY);
            backdropSource.draw(blurCanvas);
            blurCanvas.restore();
            fastBoxBlur(blurBuffer, blurPixels, bw, bh, dark);
        } catch (RuntimeException ignored) {
        } finally {
            capturingBackdrop = false;
        }
    }

    private static void fastBoxBlur(Bitmap bitmap, int[] px, int w, int h, boolean dark) {
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        if (dark) {
            for (int i = 0, len = w * h; i < len; i++) {
                int c = px[i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                if (r > 36 || g > 42 || b > 54) {
                    r = 18 + ((r * 16) >> 8);
                    g = 22 + ((g * 18) >> 8);
                    b = 30 + ((b * 22) >> 8);
                    px[i] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 1; x < w - 1; x++) {
                    int c0 = px[row + x - 1], c1 = px[row + x], c2 = px[row + x + 1];
                    int r = (((c0 >> 16) & 0xff) + ((c1 >> 16) & 0xff) * 2 + ((c2 >> 16) & 0xff)) >> 2;
                    int g = (((c0 >> 8) & 0xff) + ((c1 >> 8) & 0xff) * 2 + ((c2 >> 8) & 0xff)) >> 2;
                    int b = ((c0 & 0xff) + (c1 & 0xff) * 2 + (c2 & 0xff)) >> 2;
                    px[row + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
            for (int x = 0; x < w; x++) {
                for (int y = 1; y < h - 1; y++) {
                    int c0 = px[(y - 1) * w + x], c1 = px[y * w + x], c2 = px[(y + 1) * w + x];
                    int r = (((c0 >> 16) & 0xff) + ((c1 >> 16) & 0xff) * 2 + ((c2 >> 16) & 0xff)) >> 2;
                    int g = (((c0 >> 8) & 0xff) + ((c1 >> 8) & 0xff) * 2 + ((c2 >> 8) & 0xff)) >> 2;
                    int b = ((c0 & 0xff) + (c1 & 0xff) * 2 + (c2 & 0xff)) >> 2;
                    px[y * w + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (capturingBackdrop) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        float padH = dp(12);
        float padTop = dp(5);
        float padBottom = dp(9);
        shellBounds.set(padH, padTop, w - padH, h - padBottom);
        float shellRadius = shellBounds.height() / 2f;
        shellPath.reset();
        shellPath.addRoundRect(shellBounds, shellRadius, shellRadius, Path.Direction.CW);

        // 1. Soft floating ambient shadow under the glass capsule
        paint.setShader(null);
        for (int s = 3; s >= 1; s--) {
            float spread = dp(s * 2.2f);
            float dy = dp(s * 1.4f);
            paint.setColor(dark ? (0x1e000000) : (0x10182438));
            RectF shadowRect = new RectF(
                shellBounds.left - spread * 0.4f,
                shellBounds.top + dy,
                shellBounds.right + spread * 0.4f,
                shellBounds.bottom + dy + spread * 0.5f
            );
            canvas.drawRoundRect(shadowRect, shellRadius + spread * 0.5f, shellRadius + spread * 0.5f, paint);
        }

        // 2. Frosted backdrop + translucent iOS Liquid Glass outer capsule
        captureAndBlurBackdrop();
        canvas.save();
        canvas.clipPath(shellPath);
        if (blurBuffer != null) {
            paint.setFilterBitmap(true);
            paint.setAlpha(255);
            canvas.drawBitmap(blurBuffer, null, shellBounds, paint);
        }
        // Translucent frosted glass tint (lets blurred content show through!)
        paint.setShader(new LinearGradient(
            0, shellBounds.top, 0, shellBounds.bottom,
            dark ? new int[]{0xb0212836, 0x96161b24, 0xa81c222e}
                 : new int[]{0xb8ffffff, 0x94f6f8fc, 0xa8ffffff},
            new float[]{0f, 0.55f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawRect(shellBounds, paint);

        // Top curved inner specular sheen on the outer bar
        innerSheenBounds.set(shellBounds.left + dp(2), shellBounds.top + dp(1),
            shellBounds.right - dp(2), shellBounds.top + shellBounds.height() * 0.48f);
        paint.setShader(new LinearGradient(
            0, innerSheenBounds.top, 0, innerSheenBounds.bottom,
            dark ? 0x26ffffff : 0x70ffffff,
            0x00ffffff,
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(innerSheenBounds, shellRadius, shellRadius, paint);
        canvas.restore();

        // Diagonal beveled glass rim stroke on the outer capsule
        strokePaint.setStrokeWidth(dp(1.15f));
        strokePaint.setShader(new LinearGradient(
            shellBounds.left, shellBounds.top, shellBounds.right, shellBounds.bottom,
            dark ? new int[]{0x66ffffff, 0x18ffffff, 0x24ffffff, 0x4affffff}
                 : new int[]{0xf2ffffff, 0x66ffffff, 0x99e2e8f2, 0xe6ffffff},
            new float[]{0f, 0.38f, 0.68f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(shellBounds, shellRadius, shellRadius, strokePaint);

        //Keep child buttons laid out for touch/accessibility
        super.dispatchDraw(canvas);

        if (tabs.length == 0 || tabs[0] == null || tabs[0].getWidth() <= 0) return;

        // Compute continuous droplet geometry across tabs
        int count = tabs.length;
        float clampedPos = Math.max(0f, Math.min(count - 1, position));
        int idx0 = Math.min(count - 1, (int) Math.floor(clampedPos));
        int idx1 = Math.min(count - 1, idx0 + 1);
        float frac = clampedPos - idx0;
        float inFlight = (float) Math.sin(Math.PI * frac);
        float effect = Math.min(1f, Math.max(inFlight, glassEnergy * 0.85f));

        View parentRow = (View) tabs[0].getParent();
        float rowLeft = parentRow != null ? parentRow.getLeft() : shellBounds.left;
        float rowTop = parentRow != null ? parentRow.getTop() : shellBounds.top;

        float left0 = rowLeft + tabs[idx0].getLeft();
        float width0 = tabs[idx0].getWidth();
        float left1 = rowLeft + tabs[idx1].getLeft();
        float width1 = tabs[idx1].getWidth();

        float baseLeft = left0 + (left1 - left0) * frac;
        float baseWidth = width0 + (width1 - width0) * frac;
        float centerX = baseLeft + baseWidth * 0.5f;
        float centerY = shellBounds.centerY();

        // Liquid droplet horizontal stretch & directional surface tension
        float speedFactor = Math.min(1f, Math.abs(smoothedVelocity) / 4.5f);
        float stretchX = 1f + 0.18f * inFlight + 0.06f * glassEnergy + 0.08f * speedFactor;
        float swellY = 1f + 0.11f * effect;
        float pillW = baseWidth * stretchX;
        float baseH = shellBounds.height() - dp(7);
        float pillH = baseH * swellY;

        float skewShift = Math.max(-dp(5), Math.min(dp(5), smoothedVelocity * dp(0.9f)));
        dropletBounds.set(
            centerX - pillW * 0.5f + skewShift * 0.35f,
            centerY - pillH * 0.5f,
            centerX + pillW * 0.5f + skewShift * 0.35f,
            centerY + pillH * 0.5f
        );
        // Clamp horizontally to stay inside the outer capsule with a 3dp margin during rest,
        // while allowing slight vertical lens swell like iOS 26 Liquid Glass.
        float minL = shellBounds.left + dp(3);
        float maxR = shellBounds.right - dp(3);
        if (dropletBounds.left < minL) {
            dropletBounds.offset(minL - dropletBounds.left, 0);
        } else if (dropletBounds.right > maxR) {
            dropletBounds.offset(maxR - dropletBounds.right, 0);
        }
        float dropletRadius = dropletBounds.height() / 2f;
        dropletPath.reset();
        dropletPath.addRoundRect(dropletBounds, dropletRadius, dropletRadius, Path.Direction.CW);

        // 4. Draw the active iOS Liquid Glass Droplet / Lens
        // Droplet ambient glow / shadow
        paint.setShader(null);
        paint.setColor(dark ? 0x28000000 : 0x16bd451f);
        RectF dropShadow = new RectF(dropletBounds.left, dropletBounds.top + dp(1.5f),
            dropletBounds.right, dropletBounds.bottom + dp(2.5f));
        canvas.drawRoundRect(dropShadow, dropletRadius, dropletRadius, paint);

        canvas.save();
        canvas.clipPath(dropletPath);

        // Glass lens body: more transparent in the center during switching (iOS Liquid Glass lens)
        float lensCx = dropletBounds.centerX() + (frac - 0.5f) * dropletBounds.width() * 0.25f;
        float lensCy = dropletBounds.centerY() - dropletBounds.height() * 0.15f;
        float lensRad = Math.max(dropletBounds.width(), dropletBounds.height()) * 0.75f;
        if (dark) {
            int centerColor = blendArgb(0x36ff784e, 0x1affffff, effect);
            int midColor = blendArgb(0x282a3446, 0x38364258, effect);
            int edgeColor = blendArgb(0x44ff784e, 0x66ffffff, effect * 0.7f);
            paint.setShader(new RadialGradient(
                lensCx, lensCy, lensRad,
                new int[]{centerColor, midColor, edgeColor},
                new float[]{0f, 0.68f, 1f},
                Shader.TileMode.CLAMP
            ));
        } else {
            int centerColor = blendArgb(0xd8fff5ef, 0x78ffffff, effect);
            int midColor = blendArgb(0xc8ffeee6, 0x98fff7f2, effect);
            int edgeColor = blendArgb(0xe6ffe2d4, 0xeeffffff, effect);
            paint.setShader(new RadialGradient(
                lensCx, lensCy, lensRad,
                new int[]{centerColor, midColor, edgeColor},
                new float[]{0f, 0.68f, 1f},
                Shader.TileMode.CLAMP
            ));
        }
        canvas.drawRect(dropletBounds, paint);

        // Dynamic rolling specular glint sweeping across the glass surface during motion
        float glintPhase = (clampedPos * 0.85f) % 1f;
        float glintCenter = dropletBounds.left + dropletBounds.width() * (0.2f + 0.6f * glintPhase);
        float glintWidth = dropletBounds.width() * (0.38f + 0.18f * effect);
        int glintPeak = dark
            ? Math.round((0x22 + 0x36 * effect)) << 24 | 0xffffff
            : Math.round((0x55 + 0x55 * effect)) << 24 | 0xffffff;
        paint.setShader(new LinearGradient(
            glintCenter - glintWidth, dropletBounds.top,
            glintCenter + glintWidth, dropletBounds.bottom,
            new int[]{0x00ffffff, glintPeak, 0x00ffffff},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawRect(dropletBounds, paint);

        // Top convex meniscus specular arc
        meniscusBounds.set(
            dropletBounds.left + dp(3),
            dropletBounds.top + dp(1f),
            dropletBounds.right - dp(3),
            dropletBounds.top + dropletBounds.height() * 0.46f
        );
        paint.setShader(new LinearGradient(
            0, meniscusBounds.top, 0, meniscusBounds.bottom,
            dark ? (effect > 0.1f ? 0x58ffffff : 0x38ffffff)
                 : (effect > 0.1f ? 0xd8ffffff : 0xa8ffffff),
            0x00ffffff,
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(meniscusBounds, dropletRadius, dropletRadius, paint);

        // Bottom warm caustic refraction glow
        meniscusBounds.set(
            dropletBounds.left + dp(6),
            dropletBounds.bottom - dropletBounds.height() * 0.32f,
            dropletBounds.right - dp(6),
            dropletBounds.bottom - dp(1f)
        );
        paint.setShader(new LinearGradient(
            0, meniscusBounds.top, 0, meniscusBounds.bottom,
            0x00ff784e,
            dark ? 0x38ff784e : 0x2eff784e,
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(meniscusBounds, dropletRadius, dropletRadius, paint);
        canvas.restore();

        // 5. Refractive prism rim border around the active glass droplet
        strokePaint.setStrokeWidth(dp(1.2f + 0.45f * effect));
        int rimTopLeft = dark ? 0x99ffffff : 0xffffffff;
        int rimPrism1 = blendArgb(dark ? 0x30ffffff : 0x80ffd8c8, dark ? 0x707ad7ff : 0xc0b8ecff, effect);
        int rimPrism2 = blendArgb(dark ? 0x40ff784e : 0x90ffb899, dark ? 0x88ff9468 : 0xd8ff9e75, effect);
        int rimBottomRight = dark ? 0x77ffffff : 0xf0ffffff;
        strokePaint.setShader(new LinearGradient(
            dropletBounds.left, dropletBounds.top,
            dropletBounds.right, dropletBounds.bottom,
            new int[]{rimTopLeft, rimPrism1, rimPrism2, rimBottomRight},
            new float[]{0f, 0.35f, 0.7f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(dropletBounds, dropletRadius, dropletRadius, strokePaint);

        // Keep titles on one stable layer above the moving glass.
        textPaint.setTextSize(dp(13.5f));
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = shellBounds.centerY() - (fm.ascent + fm.descent) * 0.5f;
        for (int i = 0; i < count; i++) {
            Button btn = tabs[i];
            if (btn == null) continue;
            float weight = Math.max(0f, 1f - Math.abs(clampedPos - i));
            textPaint.setColor(blendArgb(AppTheme.muted(dark), AppTheme.accentText(dark), weight));
            textPaint.setAlpha(255);
            canvas.drawText(btn.getText().toString(), rowLeft + btn.getLeft() + btn.getWidth() * 0.5f,
                baseline, textPaint);
        }

        if (effect > 0.02f && Math.abs(smoothedVelocity) > 0.05f) {
            smoothedVelocity *= 0.85f;
            postInvalidateOnAnimation();
        }
    }

    private static int blendArgb(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }
}