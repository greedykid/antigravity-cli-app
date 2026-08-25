package com.greedykid.codexremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, hardware-accelerated sparkline & area chart for the
 * Server Operations live metrics dashboard.
 */
public class SparklineChartView extends View {
    private final List<Float> points = new ArrayList<>();
    private float minVal = 0f;
    private float maxVal = 100f;
    private boolean autoScale = false;

    private int lineColor = 0xFFD96B43; // Theme.ACCENT default
    private int fillColor = 0x33D96B43;
    private int gridColor = 0x22FFFFFF;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public SparklineChartView(Context context) {
        super(context);
        init();
    }

    public SparklineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.8f));
        gridPaint.setColor(gridColor);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));

        dotPaint.setStyle(Paint.Style.FILL);
        dotGlowPaint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int line, int fill) {
        this.lineColor = line;
        this.fillColor = fill;
        invalidate();
    }

    public void setRange(float min, float max, boolean autoScale) {
        this.minVal = min;
        this.maxVal = max;
        this.autoScale = autoScale;
        invalidate();
    }

    public void setData(List<Float> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        invalidate();
    }

    public void addPoint(float value) {
        points.add(value);
        if (points.size() > 40) {
            points.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padT = dp(8);
        float padB = dp(8);
        float padL = dp(8);
        float padR = dp(8);
        float drawW = w - padL - padR;
        float drawH = h - padT - padB;

        // Draw 3 horizontal grid lines (0%, 50%, 100%)
        canvas.drawLine(padL, padT, padL + drawW, padT, gridPaint);
        canvas.drawLine(padL, padT + drawH * 0.5f, padL + drawW, padT + drawH * 0.5f, gridPaint);
        canvas.drawLine(padL, padT + drawH, padL + drawW, padT + drawH, gridPaint);

        if (points.size() < 2) {
            return;
        }

        float curMin = minVal;
        float curMax = maxVal;
        if (autoScale) {
            curMin = Float.MAX_VALUE;
            curMax = Float.MIN_VALUE;
            for (float p : points) {
                if (p < curMin) curMin = p;
                if (p > curMax) curMax = p;
            }
            if (curMax <= curMin) {
                curMax = curMin + 1f;
            }
        }

        float range = (curMax - curMin) <= 0 ? 1f : (curMax - curMin);

        linePath.reset();
        fillPath.reset();

        int n = points.size();
        float stepX = drawW / (float) (n - 1);

        float firstX = padL;
        float firstY = padT + drawH - ((points.get(0) - curMin) / range) * drawH;
        firstY = Math.max(padT, Math.min(padT + drawH, firstY));

        linePath.moveTo(firstX, firstY);
        fillPath.moveTo(firstX, padT + drawH);
        fillPath.lineTo(firstX, firstY);

        float lastX = firstX;
        float lastY = firstY;

        for (int i = 1; i < n; i++) {
            float x = padL + i * stepX;
            float rawY = points.get(i);
            float y = padT + drawH - ((rawY - curMin) / range) * drawH;
            y = Math.max(padT, Math.min(padT + drawH, y));

            // Smooth cubic bezier spline
            float prevX = padL + (i - 1) * stepX;
            float midX = (prevX + x) / 2f;
            linePath.cubicTo(midX, lastY, midX, y, x, y);
            fillPath.cubicTo(midX, lastY, midX, y, x, y);

            lastX = x;
            lastY = y;
        }

        fillPath.lineTo(lastX, padT + drawH);
        fillPath.close();

        // Fill gradient
        fillPaint.setShader(new LinearGradient(0, padT, 0, padT + drawH,
                fillColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // Stroke line
        linePaint.setColor(lineColor);
        canvas.drawPath(linePath, linePaint);

        // Last point glowing dot
        dotGlowPaint.setColor((lineColor & 0x00FFFFFF) | 0x44000000);
        canvas.drawCircle(lastX, lastY, dp(6), dotGlowPaint);

        dotPaint.setColor(lineColor);
        canvas.drawCircle(lastX, lastY, dp(3.5f), dotPaint);
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }
}
