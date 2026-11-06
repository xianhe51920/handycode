package com.fairycrane.handycode;

import android.content.Context;
import android.content.res.Configuration;

/** Centralized palette and dark-mode resolution (follows system by default). */
public final class AppTheme {
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int ACCENT = 0xffeb6238;

    private AppTheme() {}

    public static boolean isDark(Context context, int mode) {
        if (mode == MODE_DARK) return true;
        if (mode == MODE_LIGHT) return false;
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int nextMode(int mode) {
        if (mode == MODE_SYSTEM) return MODE_DARK;
        if (mode == MODE_DARK) return MODE_LIGHT;
        return MODE_SYSTEM;
    }

    public static String modeLabel(int mode) {
        if (mode == MODE_DARK) return "深色";
        if (mode == MODE_LIGHT) return "浅色";
        return "自动";
    }

    public static String modeDescription(int mode) {
        if (mode == MODE_DARK) return "夜间模式：始终深色（点击切换为始终浅色）";
        if (mode == MODE_LIGHT) return "夜间模式：始终浅色（点击切换为跟随系统）";
        return "夜间模式：跟随系统（点击切换为始终深色）";
    }

    public static int bg(boolean dark) { return dark ? 0xff0f1218 : 0xfff7f8fb; }
    public static int surface(boolean dark) { return dark ? 0xff181c24 : 0xffffffff; }
    public static int headerBg(boolean dark) { return dark ? 0xff151921 : 0xffffffff; }
    public static int elevated(boolean dark) { return dark ? 0xff232936 : 0xffedf0f5; }
    public static int subtleFill(boolean dark) { return elevated(dark); }
    public static int ink(boolean dark) { return dark ? 0xffeceff4 : 0xff242b37; }
    public static int muted(boolean dark) { return dark ? 0xff949eb0 : 0xff697386; }
    public static int border(boolean dark) { return dark ? 0xff262c36 : 0xffe9ecf0; }
    public static int divider(boolean dark) { return dark ? 0xff232833 : 0xfff0f2f5; }
    public static int accent(boolean dark) { return dark ? 0xffff784e : 0xffeb6238; }
    public static int accentText(boolean dark) { return dark ? 0xffff8e6b : 0xffbd451f; }
    public static int shimmerBase(boolean dark) { return dark ? 0xff232936 : 0xffedf0f4; }
    public static int shimmerPeak(boolean dark) { return dark ? 0x38ffffff : 0xccffffff; }
}