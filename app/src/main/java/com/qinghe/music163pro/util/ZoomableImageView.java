package com.qinghe.music163pro.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
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
    /** Whether (lastX,lastY) is a valid anchor for the current single pointer. */
    private boolean anchorValid = false;
    /** Uptime of the last multi-pointer event — used to suppress the phantom
     *  double-tap a pinch can leave behind (the "image jumps after pinch" bug). */
    private long lastMultiTouchTime = -1000;
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
                        MusicLog.d("ZoomableImg", "onScale factor=" + factor
                                + " focus=(" + detector.getFocusX() + "," + detector.getFocusY()
                                + ") scaleFactor=" + scaleFactor + " min=" + minScale);
                        return true;
                    }

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        MusicLog.d("ZoomableImg", "onScaleBegin");
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        MusicLog.d("ZoomableImg", "onScaleEnd scaleFactor=" + scaleFactor);
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
                        // A pinch can look like a quick tap pair to the gesture
                        // detector — zooming right after a pinch makes the image
                        // jump. Ignore double-taps briefly after multi-touch.
                        long since = SystemClock.uptimeMillis() - lastMultiTouchTime;
                        if (since < 500) {
                            MusicLog.d("ZoomableImg", "doubleTap BLOCKED sinceMulti=" + since);
                            return true;
                        }
                        MusicLog.d("ZoomableImg", "doubleTap toggleZoomAt (" + e.getX() + "," + e.getY() + ")");
                        toggleZoomAt(e.getX(), e.getY());
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        MusicLog.d("ZoomableImg", "onLongPress -> performLongClick");
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
        MusicLog.d("ZoomableImg", "fixT rect=(" + rect.left + "," + rect.top + ","
                + rect.width() + "x" + rect.height() + ") view=" + getWidth() + "x" + getHeight()
                + " delta=(" + deltaX + "," + deltaY + ")");
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Fresh single-finger gesture: re-anchor and keep the parent
                // (e.g. a scroll view) from stealing the pan.
                anchorValid = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                MusicLog.d("ZoomableImg", "DOWN scaleFactor=" + scaleFactor + " min=" + minScale);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // A pinch just started/ended. Record it (double-tap suppression)
                // and invalidate the pan anchor so the first single-finger move
                // afterwards re-anchors instead of translating by a huge stale
                // delta (the "zoom offset" bug).
                lastMultiTouchTime = SystemClock.uptimeMillis();
                anchorValid = false;
                MusicLog.d("ZoomableImg", "POINTER action=" + event.getActionMasked()
                        + " count=" + event.getPointerCount());
                break;
            case MotionEvent.ACTION_CANCEL:
                anchorValid = false;
                MusicLog.d("ZoomableImg", "CANCEL");
                break;
            case MotionEvent.ACTION_MOVE:
                // Gate on pointer count only — ScaleGestureDetector may report
                // "in progress" until the final ACTION_UP, which would block
                // panning for the whole drag after a pinch.
                boolean canPan = anchorValid
                        && event.getPointerCount() == 1
                        && scaleFactor > minScale + 0.01f;
                if (canPan) {
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastX;
                    float dy = y - lastY;
                    matrix.postTranslate(dx, dy);
                    fixTranslation();
                    setImageMatrix(matrix);
                    lastX = x;
                    lastY = y;
                    MusicLog.d("ZoomableImg", "PAN dx=" + dx + " dy=" + dy
                            + " scaleFactor=" + scaleFactor);
                } else if (event.getPointerCount() == 1) {
                    // (Re)anchor without translating
                    lastX = event.getX();
                    lastY = event.getY();
                    anchorValid = true;
                    MusicLog.d("ZoomableImg", "ANCHOR only scaleFactor=" + scaleFactor
                            + " min=" + minScale + " zoomed=" + (scaleFactor > minScale + 0.01f));
                }
                break;
            default:
                break;
        }
        return true;
    }
}
