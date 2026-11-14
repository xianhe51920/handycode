package com.fairycrane.handycode;

import android.content.Context;
import android.content.res.Configuration;

/** Centralized palette and initial appearance. */
public final class AppTheme {
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int ACCENT = 0xffeb6238;

    private AppTheme() {}

    public static int systemDefault(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES ? MODE_DARK : MODE_LIGHT;
    }

    public static int bg(boolean dark) { return dark ? 0xff0f1218 : 0xfff7f8fb; }
    public static int surface(boolean dark) { return dark ? 0xff181c24 : 0xffffffff; }
    public static int elevated(boolean dark) { return dark ? 0xff232936 : 0xffedf0f5; }
    public static int ink(boolean dark) { return dark ? 0xffeceff4 : 0xff242b37; }
    public static int muted(boolean dark) { return dark ? 0xff949eb0 : 0xff697386; }
    public static int border(boolean dark) { return dark ? 0xff262c36 : 0xffe9ecf0; }
    public static int divider(boolean dark) { return dark ? 0xff232833 : 0xfff0f2f5; }
    public static int accentText(boolean dark) { return dark ? 0xffff8e6b : 0xffbd451f; }
}
