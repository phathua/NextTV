package com.next.tv.launcher.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates crisp, rounded monochrome QR code bitmaps for 10-foot TV display.
 */
public final class QrCodeGenerator {
    private static final String TAG = "NextTV.QrGen";

    private QrCodeGenerator() {}

    public static Bitmap generate(String content, int size) {
        if (content == null || content.isEmpty()) return null;
        if (size <= 0) size = 360;

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // White rounded container background
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.WHITE);
            bgPaint.setStyle(Paint.Style.FILL);

            float cornerRadius = size * 0.06f;
            RectF bgRect = new RectF(0f, 0f, size, size);
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

            // Draw QR modules
            Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fgPaint.setColor(Color.parseColor("#09090B"));
            fgPaint.setStyle(Paint.Style.FILL);

            int matrixWidth = matrix.getWidth();
            int matrixHeight = matrix.getHeight();
            float cellWidth = (float) size / matrixWidth;
            float cellHeight = (float) size / matrixHeight;

            for (int x = 0; x < matrixWidth; x++) {
                for (int y = 0; y < matrixHeight; y++) {
                    if (matrix.get(x, y)) {
                        canvas.drawRect(
                                x * cellWidth,
                                y * cellHeight,
                                (x + 1) * cellWidth,
                                (y + 1) * cellHeight,
                                fgPaint
                        );
                    }
                }
            }

            // Clip final bitmap with rounded corners
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas outputCanvas = new Canvas(output);
            Path clipPath = new Path();
            clipPath.addRoundRect(bgRect, cornerRadius, cornerRadius, Path.Direction.CW);
            outputCanvas.clipPath(clipPath);
            outputCanvas.drawBitmap(bitmap, 0f, 0f, null);
            bitmap.recycle();

            return output;

        } catch (Exception e) {
            Log.e(TAG, "QR code generation failed: " + e.getMessage());
            return null;
        }
    }
}
