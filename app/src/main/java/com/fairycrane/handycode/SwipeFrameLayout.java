package com.fairycrane.handycode;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.widget.FrameLayout;

/** Intercepts horizontal intent only; vertical scrolling stays in WebView. */
final class SwipeFrameLayout extends FrameLayout {
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

    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        // WebView can request exclusivity on DOWN, before direction is known.
        // Keep observing until intent is clear; preserve long-press, IME and
        // multi-touch interactions rather than stealing their input streams.
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
            direction = SwitchMotion.direction(dx, dy, slop);
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
