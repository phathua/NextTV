package com.next.tv.launcher.clean;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * High-performance 4-tier storage cleaner specifically designed for Android TV boxes.
 * Runs in under 1.5MB heap RAM, zero external libraries.
 */
public final class TvStorageCleaner {
    private static final String TAG = "TvHome.Cleaner";

    public static class CleanResult {
        public long junkBytesFreed = 0;
        public int deletedFilesCount = 0;
        public int deletedDirsCount = 0;
        public List<String> deletedSummaries = new ArrayList<>();
    }

    public static class ScanBreakdown {
        public long apkBytes = 0;
        public int apkCount = 0;
        public long logTmpBytes = 0;
        public int logTmpCount = 0;
        public long thumbBytes = 0;
        public int thumbCount = 0;
        public long corpseBytes = 0;
        public int corpseCount = 0;
        public int emptyDirCount = 0;

        public long getTotalCleanableBytes() {
            return apkBytes + logTmpBytes + thumbBytes + corpseBytes;
        }
    }

    public interface ScanProgressListener {
        void onScanComplete(ScanBreakdown breakdown);
    }

    public interface CleanProgressListener {
        void onProgress(String currentPhase, int percent);
        void onComplete(CleanResult result);
    }

    private final Context context;

    public TvStorageCleaner(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Fast pre-scan to calculate cleanable bytes by category without deleting anything.
     */
    public void startScan(ScanProgressListener listener) {
        new Thread(() -> {
            ScanBreakdown breakdown = new ScanBreakdown();
            try {
                scanJunkBreakdown(breakdown);
                scanThumbnailsBreakdown(breakdown);
                scanCorpseBreakdown(breakdown);
                scanEmptyDirsCount(Environment.getExternalStorageDirectory(), breakdown, 0);
            } catch (Exception e) {
                Log.w(TAG, "Scan error: " + e.getMessage());
            } finally {
                if (listener != null) {
                    listener.onScanComplete(breakdown);
                }
            }
        }, "TvStorageCleaner-Scanner").start();
    }

    private void scanJunkBreakdown(ScanBreakdown b) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;

        List<File> targetDirs = new ArrayList<>();
        targetDirs.add(sdcard);
        targetDirs.add(new File(sdcard, "Download"));
        targetDirs.add(new File(sdcard, "Download/Downloader"));
        targetDirs.add(new File(sdcard, "Movies"));
        targetDirs.add(new File(sdcard, "Pictures"));

        for (File dir : targetDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (!file.isFile()) continue;
                String name = file.getName().toLowerCase(Locale.US);
                long len = file.length();
                if (name.endsWith(".apk") && !dir.equals(new File(sdcard, "TVFiles"))) {
                    b.apkBytes += len;
                    b.apkCount++;
                } else if (name.endsWith(".tmp") || name.endsWith(".log") || name.endsWith(".bak") || name.endsWith(".exo") || name.endsWith(".crdownload")) {
                    b.logTmpBytes += len;
                    b.logTmpCount++;
                }
            }
        }
    }

    private void scanThumbnailsBreakdown(ScanBreakdown b) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;
        File[] thumbCandidates = new File[]{
                new File(sdcard, ".thumbnails"),
                new File(sdcard, "DCIM/.thumbnails"),
                new File(sdcard, "Pictures/.thumbnails")
        };
        for (File thumbDir : thumbCandidates) {
            if (thumbDir.exists() && thumbDir.isDirectory()) {
                measureFolderRecursive(thumbDir, b);
            }
        }
    }

    private void measureFolderRecursive(File dir, ScanBreakdown b) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                measureFolderRecursive(f, b);
            } else {
                b.thumbBytes += f.length();
                b.thumbCount++;
            }
        }
    }

    private void scanCorpseBreakdown(ScanBreakdown b) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;
        Set<String> installedPackages = getInstalledPackagesSet();
        File[] parentContainers = new File[]{
                new File(sdcard, "Android/media"),
                new File(sdcard, "Android/data"),
                new File(sdcard, "Android/obb")
        };
        for (File parent : parentContainers) {
            if (!parent.exists() || !parent.isDirectory()) continue;
            File[] packageDirs = parent.listFiles();
            if (packageDirs == null) continue;
            for (File pDir : packageDirs) {
                if (!pDir.isDirectory()) continue;
                String folderName = pDir.getName();
                if (folderName.equals("com.android.providers.media") || folderName.equals("com.google.android.providers.media.module")) {
                    continue;
                }
                if (folderName.contains(".") && !installedPackages.contains(folderName)) {
                    b.corpseBytes += calculateFolderSize(pDir);
                    b.corpseCount++;
                }
            }
        }
    }

    private void scanEmptyDirsCount(File currentDir, ScanBreakdown b, int depth) {
        if (currentDir == null || !currentDir.exists() || !currentDir.isDirectory() || depth > 4) return;
        File[] children = currentDir.listFiles();
        if (children == null) return;
        if (children.length == 0 && depth > 1) {
            String name = currentDir.getName();
            if (!name.equals("Download") && !name.equals("Movies") && !name.equals("Music") && !name.equals("Pictures") && !name.equals("Android") && !name.equals("TVFiles")) {
                b.emptyDirCount++;
            }
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                scanEmptyDirsCount(child, b, depth + 1);
            }
        }
    }

    /**
     * Executes 4-tier cleaning asynchronously.
     */
    public void startClean(CleanProgressListener listener) {
        new Thread(() -> {
            CleanResult result = new CleanResult();
            try {
                // Tier 1: Scan & delete junk/temp files in /sdcard/ and subdirectories
                if (listener != null) listener.onProgress("Quét tệp rác & tệp tạm", 20);
                cleanJunkFiles(result);

                // Tier 2: Scan & delete thumbnails and image caches
                if (listener != null) listener.onProgress("Dọn bộ nhớ đệm ảnh & thumbnails", 45);
                cleanThumbnails(result);

                // Tier 3: Corpse Finder - remove leftover data/media from uninstalled packages
                if (listener != null) listener.onProgress("Quét thư mục ứng dụng mồ côi", 70);
                cleanCorpseDirectories(result);

                // Tier 4: Clean empty directories & trigger system cache trim
                if (listener != null) listener.onProgress("Giải phóng bộ nhớ đệm hệ thống", 90);
                cleanEmptyDirectories(Environment.getExternalStorageDirectory(), result, 0);
                triggerSystemStorageAllocation(result);

            } catch (Exception e) {
                Log.w(TAG, "Storage cleaning encountered an issue: " + e.getMessage());
            } finally {
                if (listener != null) {
                    listener.onProgress("Hoàn tất dọn dẹp", 100);
                    listener.onComplete(result);
                }
            }
        }, "TvStorageCleaner-Worker").start();
    }

    private void cleanJunkFiles(CleanResult result) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;

        // Common download & temp target folders
        List<File> targetDirs = new ArrayList<>();
        targetDirs.add(sdcard);
        targetDirs.add(new File(sdcard, "Download"));
        targetDirs.add(new File(sdcard, "Download/Downloader"));
        targetDirs.add(new File(sdcard, "Movies"));
        targetDirs.add(new File(sdcard, "Pictures"));

        for (File dir : targetDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                if (!file.isFile()) continue;
                String name = file.getName().toLowerCase(Locale.US);

                boolean isJunk = name.endsWith(".tmp")
                        || name.endsWith(".log")
                        || name.endsWith(".bak")
                        || name.endsWith(".exo")
                        || name.endsWith(".crdownload")
                        || (name.endsWith(".apk") && !dir.equals(new File(sdcard, "TVFiles")));

                if (isJunk) {
                    long size = file.length();
                    String filePath = file.getAbsolutePath();
                    if (file.delete()) {
                        result.junkBytesFreed += size;
                        result.deletedFilesCount++;
                        result.deletedSummaries.add("Tệp rác: " + file.getName());
                        Log.i(TAG, "Da xoa tep rac (" + formatSize(size) + "): " + filePath);
                    } else {
                        Log.w(TAG, "Khong the xoa tep rac: " + filePath);
                    }
                }
            }
        }
    }

    private void cleanThumbnails(CleanResult result) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;

        File[] thumbCandidates = new File[]{
                new File(sdcard, ".thumbnails"),
                new File(sdcard, "DCIM/.thumbnails"),
                new File(sdcard, "Pictures/.thumbnails")
        };

        for (File thumbDir : thumbCandidates) {
            if (thumbDir.exists() && thumbDir.isDirectory()) {
                Log.i(TAG, "Dang don thu muc thumbnails: " + thumbDir.getAbsolutePath());
                deleteDirectoryContent(thumbDir, result);
            }
        }
    }

    private void cleanCorpseDirectories(CleanResult result) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard == null || !sdcard.exists()) return;

        Set<String> installedPackages = getInstalledPackagesSet();

        File[] parentContainers = new File[]{
                new File(sdcard, "Android/media"),
                new File(sdcard, "Android/data"),
                new File(sdcard, "Android/obb")
        };

        for (File parent : parentContainers) {
            if (!parent.exists() || !parent.isDirectory()) continue;
            File[] packageDirs = parent.listFiles();
            if (packageDirs == null) continue;

            for (File pDir : packageDirs) {
                if (!pDir.isDirectory()) continue;
                String folderName = pDir.getName();

                // Skip system/core packages
                if (folderName.equals("com.android.providers.media") || folderName.equals("com.google.android.providers.media.module")) {
                    continue;
                }

                // If looks like a package (contains a dot) and is NOT installed -> it's a corpse
                if (folderName.contains(".") && !installedPackages.contains(folderName)) {
                    long dirSize = calculateFolderSize(pDir);
                    String dirPath = pDir.getAbsolutePath();
                    if (deleteDirectoryRecursively(pDir)) {
                        result.junkBytesFreed += dirSize;
                        result.deletedDirsCount++;
                        result.deletedSummaries.add("Ứng dụng mồ côi: " + folderName);
                        Log.i(TAG, "Da xoa thu muc mo coi (" + formatSize(dirSize) + "): " + dirPath);
                    } else {
                        Log.w(TAG, "Khong the xoa thu muc mo coi: " + dirPath);
                    }
                }
            }
        }
    }

    private void cleanEmptyDirectories(File currentDir, CleanResult result, int depth) {
        if (currentDir == null || !currentDir.exists() || !currentDir.isDirectory() || depth > 4) return;
        File[] children = currentDir.listFiles();
        if (children == null) return;

        // If directory is empty and not protected root directories
        if (children.length == 0 && depth > 1) {
            String name = currentDir.getName();
            if (!name.equals("Download") && !name.equals("Movies") && !name.equals("Music") && !name.equals("Pictures") && !name.equals("Android") && !name.equals("TVFiles")) {
                if (currentDir.delete()) {
                    result.deletedDirsCount++;
                }
            }
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                cleanEmptyDirectories(child, result, depth + 1);
            }
        }
    }

    private void triggerSystemStorageAllocation(CleanResult result) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    UUID uuid = StorageManager.UUID_DEFAULT;
                    long freeBytesBefore = Environment.getExternalStorageDirectory().getFreeSpace();
                    // Request 500MB allocation to trigger Android cache reclaiming
                    sm.allocateBytes(uuid, 500L * 1024L * 1024L);
                    long freeBytesAfter = Environment.getExternalStorageDirectory().getFreeSpace();
                    if (freeBytesAfter > freeBytesBefore) {
                        long reclaimed = freeBytesAfter - freeBytesBefore;
                        result.junkBytesFreed += reclaimed;
                        result.deletedSummaries.add("Bộ nhớ đệm hệ thống: " + formatSize(reclaimed));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Set<String> getInstalledPackagesSet() {
        Set<String> set = new HashSet<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo ai : apps) {
                set.add(ai.packageName);
            }
        } catch (Exception ignored) {}
        return set;
    }

    private void deleteDirectoryContent(File dir, CleanResult result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                deleteDirectoryContent(f, result);
                f.delete();
            } else {
                long size = f.length();
                if (f.delete()) {
                    result.junkBytesFreed += size;
                    result.deletedFilesCount++;
                }
            }
        }
    }

    private boolean deleteDirectoryRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }

    private long calculateFolderSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    size += calculateFolderSize(f);
                } else {
                    size += f.length();
                }
            }
        }
        return size;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
