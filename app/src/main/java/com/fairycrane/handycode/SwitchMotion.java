package com.fairycrane.handycode;

/** One normalized position drives page, title and tab indicator together. */
public final class SwitchMotion {
    private float position;
    private int selected;

    public SwitchMotion(int initial) { commit(initial); }
    public float position() { return position; }
    public int selected() { return selected; }
    public void position(float value) { position = Math.max(0f, Math.min(1f, value)); }
    public void drag(float start, float delta, float width) {
        position(width > 0 ? start - delta / width : start);
    }
    public int target(boolean cancelled) {
        if (cancelled || position == .5f) return selected;
        return position > .5f ? 1 : 0;
    }
    public int target(boolean cancelled, float velocityDp, float distanceDp) {
        if (cancelled) return selected;
        if (Math.abs(velocityDp) >= 600 && Math.abs(distanceDp) >= 24) return velocityDp < 0 ? 1 : 0;
        return target(false);
    }
    public void commit(int page) { selected = page == 1 ? 1 : 0; position = selected; }
    public float offset(int page, float extent) { return (page - position) * extent; }
    public static int direction(float dx, float dy, float slop) {
        float x = Math.abs(dx), y = Math.abs(dy);
        if (y > slop && y >= x) return 2;
        if (x > slop && x > y * 1.3f) return 1;
        return 0;
    }
    public static long duration(float distance) { return Math.round(140 + 180 * Math.min(1f, Math.abs(distance))); }
}
