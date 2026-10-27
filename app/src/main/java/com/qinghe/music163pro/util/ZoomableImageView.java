package com.qinghe.music163pro.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * Pinch-to-zoom image view for the sheet music (乐谱) viewer.
 *
 * - Pinch zooms around the focal point, clamped between fit-center and 8x.
 * - Single-finger pan when zoomed (translation is clamped to image bounds).
 * - Double-tap toggles between fit-center and 2.5x at the tap point.
 * - Long-press is forwarded via performLongClick() (used for save dialog).
 *
 * All gestures work through a single onTouchEvent; no parent interception
 * while scaling (via requestDisallowInterceptTouchEvent).
 */
public class ZoomableImageView extends ImageView {

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    /** Current total scale (equals fit scale right after fitCenter()). */
    private float scaleFactor = 1f;
    private float minScale = 1f;
    private float maxScale = 8f;

    private float lastX;
    private float lastY;
    /** Whether fitCenter() has been applied for the current drawable/size. */
    private boolean fitted = false;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float target = scaleFactor * factor;
                        if (target < minScale) {
                            factor = minScale / scaleFactor;
                        } else if (target > maxScale) {
                            factor = maxScale / scaleFactor;
                        }
                        if (factor == 1f) return true;
                        matrix.postScale(factor, factor,
                                detector.getFocusX(), detector.getFocusY());
                        scaleFactor *= factor;
                        fixTranslation();
                        setImageMatrix(matrix);
                        return true;
                    }

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        toggleZoomAt(e.getX(), e.getY());
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        performLongClick();
                    }
                });
    }

    /** Reset to fit-center for the current drawable. */
    public void fitCenter() {
        Drawable d = getDrawable();
        if (d == null) return;
        float vw = getWidth() - getPaddingLeft() - getPaddingRight();
        float vh = getHeight() - getPaddingTop() - getPaddingBottom();
        float iw = d.getIntrinsicWidth();
        float ih = d.getIntrinsicHeight();
        if (vw <= 0 || vh <= 0 || iw <= 0 || ih <= 0) return;
        float scale = Math.min(vw / iw, vh / ih);
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate((vw - iw * scale) / 2f + getPaddingLeft(),
                (vh - ih * scale) / 2f + getPaddingTop());
        scaleFactor = scale;
        minScale = scale;
        maxScale = scale * 8f;
        fitted = true;
        setImageMatrix(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Bitmap may arrive before first layout — (re)fit once we have a size.
        if (!fitted && getDrawable() != null) {
            fitCenter();
        }
    }

    private void toggleZoomAt(float x, float y) {
        float target;
        if (scaleFactor > minScale * 1.2f) {
            target = minScale;                       // zoomed in → fit back
        } else {
            target = Math.min(minScale * 2.5f, maxScale); // fit → zoom in
        }
        float factor = target / scaleFactor;
        matrix.postScale(factor, factor, x, y);
        scaleFactor = target;
        fixTranslation();
        setImageMatrix(matrix);
    }

    /** Clamp translation so the (zoomed) image never shows out-of-bounds gaps. */
    private void fixTranslation() {
        RectF rect = new RectF();
        matrix.mapRect(rect);
        float deltaX = 0;
        float deltaY = 0;
        if (rect.width() >= getWidth()) {
            deltaX = clamp(rect.left, getWidth() - rect.width(), 0);
        } else {
            deltaX = (getWidth() - rect.width()) / 2f - rect.left;
        }
        if (rect.height() >= getHeight()) {
            deltaY = clamp(rect.top, getHeight() - rect.height(), 0);
        } else {
            deltaY = (getHeight() - rect.height()) / 2f - rect.top;
        }
        if (deltaX != 0 || deltaY != 0) {
            matrix.postTranslate(deltaX, deltaY);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        // Single-finger pan when zoomed beyond fit-center
        if (!scaleDetector.isInProgress() && scaleFactor > minScale + 0.01f) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 1) {
                        float x = event.getX();
                        float y = event.getY();
                        matrix.postTranslate(x - lastX, y - lastY);
                        fixTranslation();
                        setImageMatrix(matrix);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                default:
                    break;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
        }
        return true;
    }
}
