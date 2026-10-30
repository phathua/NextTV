package com.next.tv.launcher.clean;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean storage multi-segment donut chart view.
 * Zero external libraries, memory footprint < 0.5MB heap.
 * Displays cleanable storage vs free space with smooth animated arcs.
 */
public final class StorageDonutChartView extends View {

    public static class ChartSegment {
        public final String title;
        public final long bytes;
        public final int color;
        public float sweepAngle;

        public ChartSegment(String title, long bytes, int color) {
            this.title = title;
            this.bytes = bytes;
            this.color = color;
        }
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF chartBounds = new RectF();
    private final List<ChartSegment> segments = new ArrayList<>();

    private long totalCleanableBytes = 0;
    private long totalStorageBytes = 1;
    private long currentFreeBytes = 0;
    private float strokeWidthPx = 28f;
    private float animProgress = 1.0f;

    // Segment colors matching Solar Bold Duotone Palette
    public static final int COLOR_APK = Color.parseColor("#F59E0B");     // Amber
    public static final int COLOR_LOG = Color.parseColor("#38BDF8");     // Sky Blue
    public static final int COLOR_THUMB = Color.parseColor("#10B981");   // Emerald Green
    public static final int COLOR_CORPSE = Color.parseColor("#F43F5E");  // Rose / Coral
    public static final int COLOR_FREE = Color.parseColor("#3F3F46");    // Dark Zinc / Free storage
    public static final int COLOR_BG_RING = Color.parseColor("#18181B"); // Pure Dark Zinc

    public StorageDonutChartView(Context context) {
        super(context);
        init();
    }

    public StorageDonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StorageDonutChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        strokeWidthPx = density * 14f; // 14dp stroke

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidthPx);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        bgArcPaint.setStyle(Paint.Style.STROKE);
        bgArcPaint.setStrokeWidth(strokeWidthPx);
        bgArcPaint.setColor(COLOR_BG_RING);
        bgArcPaint.setAntiAlias(true);

        textValuePaint.setColor(Color.WHITE);
        textValuePaint.setTextAlign(Paint.Align.CENTER);
        textValuePaint.setTextSize(density * 26f); // 26sp Bold
        textValuePaint.setFakeBoldText(true);

        textLabelPaint.setColor(Color.parseColor("#A1A1AA")); // Zinc 400
        textLabelPaint.setTextAlign(Paint.Align.CENTER);
        textLabelPaint.setTextSize(density * 12.5f); // 12.5sp
        textLabelPaint.setLetterSpacing(0.04f);

        textSubPaint.setColor(Color.parseColor("#10B981")); // Emerald 500
        textSubPaint.setTextAlign(Paint.Align.CENTER);
        textSubPaint.setTextSize(density * 11.5f); // 11.5sp
        textSubPaint.setFakeBoldText(true);
    }

    public void updateData(TvStorageCleaner.ScanBreakdown b, long freeBytes, long totalBytes) {
        this.totalCleanableBytes = b != null ? b.getTotalCleanableBytes() : 0;
        this.currentFreeBytes = freeBytes;
        this.totalStorageBytes = totalBytes > 0 ? totalBytes : (freeBytes + totalCleanableBytes);

        segments.clear();
        if (b != null) {
            if (b.apkBytes > 0) {
                segments.add(new ChartSegment("APK thừa", b.apkBytes, COLOR_APK));
            }
            if (b.logTmpBytes > 0) {
                segments.add(new ChartSegment("Nhật ký & tệp tạm", b.logTmpBytes, COLOR_LOG));
            }
            if (b.thumbBytes > 0) {
                segments.add(new ChartSegment("Ảnh cache", b.thumbBytes, COLOR_THUMB));
            }
            if (b.corpseBytes > 0) {
                segments.add(new ChartSegment("Ứng dụng mồ côi", b.corpseBytes, COLOR_CORPSE));
            }
        }

        // Relative angle calculation (scale up to 300 degrees if cleanable > 0, remainder for visual aesthetic)
        if (totalCleanableBytes > 0) {
            // Allocate 280 degrees proportionally across cleanable segments
            for (ChartSegment s : segments) {
                s.sweepAngle = ((float) s.bytes / (float) totalCleanableBytes) * 280f;
            }
        } else {
            // Clean state: full calm emerald ring
            segments.add(new ChartSegment("Tối ưu", 1, COLOR_THUMB));
            segments.get(0).sweepAngle = 360f;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokeWidthPx + 12f;
        chartBounds.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = chartBounds.centerX();
        float centerY = chartBounds.centerY();

        // 1. Draw base ring
        canvas.drawArc(chartBounds, 0, 360, false, bgArcPaint);

        // 2. Draw segments
        float startAngle = -90f;
        for (ChartSegment s : segments) {
            float angle = s.sweepAngle * animProgress;
            if (angle > 1.2f) {
                arcPaint.setColor(s.color);
                canvas.drawArc(chartBounds, startAngle, Math.max(1f, angle - 2f), false, arcPaint);
                startAngle += angle;
            }
        }

        // 3. Center labels
        float density = getResources().getDisplayMetrics().density;
        if (totalCleanableBytes > 0) {
            // Top: "CÓ THỂ DỌN"
            canvas.drawText("CÓ THỂ DỌN", centerX, centerY - (16f * density), textLabelPaint);

            // Center: "50.0 MB"
            String sizeStr = TvStorageCleaner.formatSize(totalCleanableBytes);
            canvas.drawText(sizeStr, centerX, centerY + (10f * density), textValuePaint);

            // Bottom: "Trống ~1.7 GB"
            String freeStr = "Trống ~" + TvStorageCleaner.formatSize(currentFreeBytes + totalCleanableBytes);
            canvas.drawText(freeStr, centerX, centerY + (30f * density), textSubPaint);
        } else {
            // All clean
            canvas.drawText("BỘ NHỚ", centerX, centerY - (14f * density), textLabelPaint);
            canvas.drawText("TỐI ƯU", centerX, centerY + (10f * density), textValuePaint);
            String freeStr = "Trống " + TvStorageCleaner.formatSize(currentFreeBytes);
            canvas.drawText(freeStr, centerX, centerY + (30f * density), textSubPaint);
        }
    }
}
