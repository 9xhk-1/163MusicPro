package com.qinghe.music163pro.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.qinghe.music163pro.R;

public class ChorusMarkerView extends View {

    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long durationMs;
    private long[] markerTimesMs = new long[0];

    public ChorusMarkerView(Context context) {
        this(context, null);
    }

    public ChorusMarkerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChorusMarkerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(ContextCompat.getColor(context, R.color.chorus_marker_cyan));
        setWillNotDraw(false);
    }

    public void setMarkerTimes(long durationMs, long... markerTimesMs) {
        this.durationMs = Math.max(0L, durationMs);
        this.markerTimesMs = markerTimesMs != null ? markerTimesMs : new long[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (durationMs <= 0 || markerTimesMs.length == 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }
        float padding = dp(12f);
        float usableWidth = Math.max(0f, width - padding * 2f);
        if (usableWidth <= 0f) {
            return;
        }
        float centerY = height / 2f;
        float radius = Math.max(dp(2.6f), height * 0.12f);
        for (long markerTimeMs : markerTimesMs) {
            if (markerTimeMs < 0L || markerTimeMs > durationMs) {
                continue;
            }
            float fraction = (float) markerTimeMs / (float) durationMs;
            float x = padding + usableWidth * fraction;
            canvas.drawCircle(x, centerY, radius, markerPaint);
        }
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
