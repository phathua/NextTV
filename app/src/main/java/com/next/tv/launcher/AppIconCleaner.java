package com.next.tv.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import java.util.ArrayDeque;

/**
 * Utility to extract clean, transparent background-stripped application logo marks.
 * Strips squircle/rectangular container backdrops and trims transparent padding
 * for TV UI cards and overlays.
 */
public final class AppIconCleaner {
    private static final int MAX_CACHE_SIZE_BYTES = 4 * 1024 * 1024; // 4 MB memory budget for TV
    private static final LruCache<String, Bitmap> ICON_CACHE = new LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private AppIconCleaner() {}

    public static Drawable getCleanAppIcon(Context context, String packageName, float density) {
        if (context == null || packageName == null || packageName.isEmpty()) return null;
        Bitmap cached = ICON_CACHE.get(packageName);
        if (cached != null && !cached.isRecycled()) {
            return new BitmapDrawable(context.getResources(), cached);
        }

        try {
            PackageManager pm = context.getPackageManager();
            Drawable raw = pm.getApplicationIcon(packageName);
            Bitmap cleanBitmap = extractCleanLogoBitmap(context, raw, density);
            if (cleanBitmap != null) {
                ICON_CACHE.put(packageName, cleanBitmap);
                return new BitmapDrawable(context.getResources(), cleanBitmap);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static Drawable extractCleanLogo(Context context, Drawable drawable, float density) {
        Bitmap bmp = extractCleanLogoBitmap(context, drawable, density);
        return bmp != null ? new BitmapDrawable(context.getResources(), bmp) : null;
    }

    public static Bitmap extractCleanLogoBitmap(Context context, Drawable drawable, float density) {
        if (drawable == null || context == null) return null;

        // Extract foreground from adaptive icon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable instanceof AdaptiveIconDrawable) {
            Drawable fg = ((AdaptiveIconDrawable) drawable).getForeground();
            if (fg != null) drawable = fg;
        }

        int size = (int) (64 * density);
        if (size <= 0) size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);

        int c00 = bmp.getPixel(0, 0);
        int c10 = bmp.getPixel(size - 1, 0);
        int c01 = bmp.getPixel(0, size - 1);
        int c11 = bmp.getPixel(size - 1, size - 1);

        int a00 = Color.alpha(c00);
        int a10 = Color.alpha(c10);
        int a01 = Color.alpha(c01);
        int a11 = Color.alpha(c11);

        // If corners are already transparent, simply trim excessive transparent borders
        if (a00 == 0 && a10 == 0 && a01 == 0 && a11 == 0) {
            return trimTransparentPadding(bmp);
        }

        // If corners have an opaque solid background, remove it via border flood fill
        if (a00 > 200 && a10 > 200 && a01 > 200 && a11 > 200 && isColorSimilar(c00, c10) && isColorSimilar(c00, c01)) {
            bmp = removeBackgroundColor(bmp, c00);
        }

        return trimTransparentPadding(bmp);
    }

    private static Bitmap removeBackgroundColor(Bitmap src, int targetColor) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        boolean[][] visited = new boolean[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            if (isColorSimilar(out.getPixel(x, 0), targetColor)) { queue.add(new int[]{x, 0}); visited[x][0] = true; }
            if (isColorSimilar(out.getPixel(x, h - 1), targetColor)) { queue.add(new int[]{x, h - 1}); visited[x][h - 1] = true; }
        }
        for (int y = 0; y < h; y++) {
            if (!visited[0][y] && isColorSimilar(out.getPixel(0, y), targetColor)) { queue.add(new int[]{0, y}); visited[0][y] = true; }
            if (!visited[w - 1][y] && isColorSimilar(out.getPixel(w - 1, y), targetColor)) { queue.add(new int[]{w - 1, y}); visited[w - 1][y] = true; }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int x = pt[0], y = pt[1];
            out.setPixel(x, y, Color.TRANSPARENT);

            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    if (isColorSimilar(out.getPixel(nx, ny), targetColor)) {
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return out;
    }

    private static boolean isColorSimilar(int c1, int c2) {
        int rDiff = Math.abs(Color.red(c1) - Color.red(c2));
        int gDiff = Math.abs(Color.green(c1) - Color.green(c2));
        int bDiff = Math.abs(Color.blue(c1) - Color.blue(c2));
        return (rDiff + gDiff + bDiff) < 45;
    }

    private static Bitmap trimTransparentPadding(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int minX = w, maxX = -1, minY = h, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Color.alpha(bmp.getPixel(x, y)) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (minX >= maxX || minY >= maxY) return bmp;

        int pad = 2;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        return Bitmap.createBitmap(bmp, minX, minY, cropW, cropH);
    }
}
