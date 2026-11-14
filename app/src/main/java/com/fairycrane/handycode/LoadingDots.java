package com.fairycrane.handycode;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Same neutral loading indicator in native covers and JD cards. No content-shaped skeleton. */
final class LoadingDots {
    private LoadingDots() {}
    static void draw(Canvas canvas, Paint paint, float x, float y, float unit, boolean dark, float progress) {
        paint.setShader(null);
        paint.setColor(AppTheme.muted(dark));
        for (int i = 0; i < 3; i++) {
            double wave = Math.cos((progress - i * .14f) * Math.PI * 2);
            paint.setAlpha((int)(130 + 95 * (wave + 1) / 2));
            canvas.drawCircle(x + (i - 1) * 12 * unit, y - 8 * unit, (2.7f + .4f * (float)wave) * unit, paint);
        }
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(12 * unit);
        canvas.drawText("正在加载…", x, y + 20 * unit, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
