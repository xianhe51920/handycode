package com.fairycrane.handycode;

/** One normalized position drives page, title and tab indicator together. */
public final class SwitchMotion {
    private final int maxPage;
    private float position;
    private int selected;

    public SwitchMotion(int initial) { this(initial, 2); }
    public SwitchMotion(int initial, int pageCount) {
        this.maxPage = Math.max(1, pageCount - 1);
        commit(initial);
    }
    public int pageCount() { return maxPage + 1; }
    public float position() { return position; }
    public int selected() { return selected; }
    public void position(float value) { position = Math.max(0f, Math.min(maxPage, value)); }
    public void drag(float start, float delta, float width) {
        if (width <= 0) { position(start); return; }
        float raw = start - delta / width;
        float min = Math.max(0f, selected - 1f);
        float max = Math.min((float) maxPage, selected + 1f);
        position(Math.max(min, Math.min(max, raw)));
    }
    public void scrub(float start, float delta, float stepWidth) {
        position(stepWidth > 0 ? start + delta / stepWidth : start);
    }
    public int target(boolean cancelled) {
        if (cancelled || Math.abs(position - selected) == .5f) return selected;
        return Math.max(0, Math.min(maxPage, Math.round(position)));
    }
    public int target(boolean cancelled, float velocityDp, float distanceDp) {
        if (cancelled) return selected;
        if (Math.abs(velocityDp) >= 600 && Math.abs(distanceDp) >= 24) {
            return velocityDp < 0
                ? Math.min(maxPage, (int) Math.floor(position) + 1)
                : Math.max(0, (int) Math.ceil(position) - 1);
        }
        return target(false);
    }
    public void commit(int page) {
        selected = Math.max(0, Math.min(maxPage, page));
        position = selected;
    }
    public float offset(int page, float extent) { return (page - position) * extent; }
    public static int direction(float dx, float dy, float slop) {
        float x = Math.abs(dx), y = Math.abs(dy);
        if (y > slop && y >= x) return 2;
        if (x > slop && x > y * 1.3f) return 1;
        return 0;
    }
    public static long duration(float distance) { return Math.round(140 + 180 * Math.min(1.4f, Math.abs(distance))); }
}