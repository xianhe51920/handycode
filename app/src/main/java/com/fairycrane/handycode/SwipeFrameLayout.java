package com.fairycrane.handycode;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.widget.FrameLayout;

/** Intercepts horizontal intent only; vertical and child-horizontal scrolling stay in child views. */
class SwipeFrameLayout extends FrameLayout {
    interface Listener {
        boolean isSettling();
        void onDragStarted();
        void onDrag(float deltaX);
        void onRelease(boolean cancelled, float velocityX, float distanceX);
    }
    private Listener listener;
    private final int slop;
    private boolean enabled = true, dragging;
    private boolean captureSettlingTouches;
    private int direction;
    private float downX, downY, originX;
    private long downAt;
    private VelocityTracker velocity;
    private final int maximumVelocity;

    SwipeFrameLayout(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        maximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        setClipChildren(true);
        setClipToPadding(true);
    }
    void setListener(Listener value) { listener = value; }
    void setCaptureSettlingTouches(boolean value) { captureSettlingTouches = value; }
    void setSwipeEnabled(boolean value) {
        enabled = value;
        if (!enabled && dragging) {
            dragging = false; direction = 2;
            recycleVelocity();
            if (listener != null) listener.onRelease(true, 0, 0);
        }
    }

    private void recycleVelocity() {
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    private boolean canChildScrollHorizontally(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                final View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                final int left = child.getLeft() + (int) child.getTranslationX();
                final int right = child.getRight() + (int) child.getTranslationX();
                final int top = child.getTop() + (int) child.getTranslationY();
                final int bottom = child.getBottom() + (int) child.getTranslationY();
                if (x + scrollX >= left && x + scrollX < right && y + scrollY >= top && y + scrollY < bottom
                        && canChildScrollHorizontally(child, true, dx, x + scrollX - left, y + scrollY - top)) {
                    return true;
                }
            }
        }
        return checkV && v.canScrollHorizontally(-dx);
    }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        if (!disallow || direction == 2 || !enabled) super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); downAt = event.getEventTime();
            direction = 0; dragging = false;
            recycleVelocity();
            velocity = VelocityTracker.obtain();
            velocity.addMovement(event);
            if (enabled && captureSettlingTouches && listener != null && listener.isSettling()) {
                originX = downX; direction = 1; dragging = true;
                listener.onDragStarted();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return false;
        }
        if (velocity != null) velocity.addMovement(event);
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !dragging) recycleVelocity();
        if (!enabled || listener == null) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() > 1) { direction = 2; return false; }
        if (action == MotionEvent.ACTION_MOVE && direction == 0) {
            if (event.getEventTime() - downAt >= ViewConfiguration.getLongPressTimeout()) { direction = 2; return false; }
            float dx = event.getX() - downX, dy = event.getY() - downY;
            int nextDir = SwitchMotion.direction(dx, dy, slop);
            if (nextDir == 1 && canChildScrollHorizontally(this, false, (int) dx, (int) event.getX(), (int) event.getY())) {
                direction = 2;
                super.requestDisallowInterceptTouchEvent(true);
                return false;
            }
            direction = nextDir;
            if (direction == 1) {
                originX = downX + Math.signum(dx) * slop;
                dragging = true;
                listener.onDragStarted();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (direction == 2) super.requestDisallowInterceptTouchEvent(true);
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!dragging || listener == null) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() > 1) {
            dragging = false; direction = 2; recycleVelocity(); listener.onRelease(true, 0, 0); return true;
        }
        if (velocity != null) velocity.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) listener.onDrag(event.getX() - originX);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP) listener.onDrag(event.getX() - originX);
            float speed = 0;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000, maximumVelocity);
                speed = velocity.getXVelocity();
            }
            recycleVelocity();
            dragging = false;
            listener.onRelease(action == MotionEvent.ACTION_CANCEL, speed, event.getX() - downX);
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        recycleVelocity();
        super.onDetachedFromWindow();
    }
}