package com.next.tv.launcher.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.next.tv.launcher.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quản lý cập nhật phiên bản mới từ GitHub repository phathua/NextTV.
 * Hoạt động độc lập, không sử dụng secret key nào (Public GitHub REST API).
 * Bản Sideload: hỗ trợ tải APK và kích hoạt trình cài đặt hệ thống.
 * Bản Google Play: chuyển hướng mở Google Play Store để cập nhật.
 */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String PREFS_NAME = "nexttv_update_prefs";

    private static final String KEY_HAS_UPDATE = "has_update";
    private static final String KEY_LATEST_VERSION = "latest_version";
    private static final String KEY_RELEASE_TITLE = "release_title";
    private static final String KEY_RELEASE_BODY = "release_body";
    private static final String KEY_APK_URL = "apk_download_url";
    private static final String KEY_APK_NAME = "apk_name";
    private static final String KEY_APK_SIZE = "apk_size";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";
    private static final String KEY_CHANGELOG_JSON = "changelog_json";

    // Public GitHub repository endpoints - KHÔNG dùng API token hay bí mật nào
    public static final String GITHUB_OWNER = "phathua";
    public static final String GITHUB_REPO = "NextTV";
    private static final String RELEASES_API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";
    private static final String LATEST_RELEASE_API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String COMMITS_API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/commits?per_page=12";

    public static final String PLAY_STORE_MARKET_URI = "market://details?id=com.next.tv.launcher";
    public static final String PLAY_STORE_WEB_URL = "https://play.google.com/store/apps/details?id=com.next.tv.launcher";

    private static final long CHECK_COOLDOWN_MS = 15 * 60 * 1000L; // 15 phút giữa các lần kiểm tra ngầm tự động

    public interface UpdateCheckCallback {
        void onCheckCompleted(boolean hasUpdate, String latestVersion, List<String> changelog);
        void onCheckFailed(String error);
    }

    public interface DownloadProgressListener {
        void onProgress(long downloadedBytes, long totalBytes, int percent);
        void onSuccess(File downloadedApk);
        void onError(String errorMessage);
    }

    public interface UpdateStateListener {
        void onUpdateStateChanged(boolean hasUpdate);
    }

    private static volatile AppUpdateManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<UpdateStateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean isChecking = false;
    private volatile boolean isDownloading = false;

    private AppUpdateManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AppUpdateManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AppUpdateManager.class) {
                if (instance == null) {
                    instance = new AppUpdateManager(context);
                }
            }
        }
        return instance;
    }

    public void registerListener(UpdateStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(UpdateStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyStateChanged(boolean hasUpdate) {
        mainHandler.post(() -> {
            for (UpdateStateListener listener : listeners) {
                try {
                    listener.onUpdateStateChanged(hasUpdate);
                } catch (Exception ignored) {}
            }
        });
    }

    public boolean isUpdateAvailable() {
        return prefs.getBoolean(KEY_HAS_UPDATE, false);
    }

    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : BuildConfig.VERSION_NAME;
        } catch (Exception e) {
            return BuildConfig.VERSION_NAME;
        }
    }

    public String getLatestVersion() {
        String latest = prefs.getString(KEY_LATEST_VERSION, "");
        if (latest == null || latest.isEmpty()) {
            return getCurrentVersion();
        }
        return latest;
    }

    public String getReleaseTitle() {
        return prefs.getString(KEY_RELEASE_TITLE, "");
    }

    public String getApkDownloadUrl() {
        return prefs.getString(KEY_APK_URL, "");
    }

    public String getApkName() {
        return prefs.getString(KEY_APK_NAME, "NextTV-update.apk");
    }

    public long getApkSize() {
        return prefs.getLong(KEY_APK_SIZE, 0L);
    }

    public boolean isSideload() {
        return BuildConfig.ALLOW_APK_INSTALL;
    }

    public boolean isChecking() {
        return isChecking;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public List<String> getCleanChangelog() {
        String json = prefs.getString(KEY_CHANGELOG_JSON, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String line = arr.getString(i);
                if (line != null && !line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            String body = prefs.getString(KEY_RELEASE_BODY, "");
            if (body != null && !body.isEmpty()) {
                list = parseChangelogLines(body);
            }
        }
        return list;
    }

    /**
     * Tự động kiểm tra bản cập nhật mới từ GitHub.
     * @param force Nếu true, bỏ qua thời gian cooldown kiểm tra ngầm.
     * @param callback Callback tùy chọn nhận thông báo kết quả.
     */
    public void checkForUpdates(boolean force, UpdateCheckCallback callback) {
        if (isChecking) {
            if (callback != null) {
                mainHandler.post(() -> callback.onCheckFailed("Đang kiểm tra cập nhật..."));
            }
            return;
        }

        long lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L);
        long now = System.currentTimeMillis();
        if (!force && (now - lastCheck) < CHECK_COOLDOWN_MS) {
            if (callback != null) {
                mainHandler.post(() -> callback.onCheckCompleted(isUpdateAvailable(), getLatestVersion(), getCleanChangelog()));
            }
            return;
        }

        isChecking = true;
        executor.execute(() -> {
            try {
                UpdateInfo info = fetchUpdateInfoFromGitHub();
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply();

                if (info != null && info.hasUpdate) {
                    saveUpdateInfo(info);
                    notifyStateChanged(true);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onCheckCompleted(true, info.versionName, info.changelog));
                    }
                } else if (info != null) {
                    // Đã kiểm tra và đang ở phiên bản mới nhất
                    prefs.edit()
                            .putBoolean(KEY_HAS_UPDATE, false)
                            .putString(KEY_LATEST_VERSION, info.versionName)
                            .putString(KEY_RELEASE_TITLE, info.title)
                            .putString(KEY_CHANGELOG_JSON, new JSONArray(info.changelog).toString())
                            .apply();
                    notifyStateChanged(false);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onCheckCompleted(false, info.versionName, info.changelog));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onCheckFailed("Không tìm thấy thông tin phiên bản mới"));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Lỗi kiểm tra cập nhật GitHub: " + e.getMessage());
                if (callback != null) {
                    mainHandler.post(() -> callback.onCheckFailed("Lỗi kết nối máy chủ GitHub: " + e.getMessage()));
                }
            } finally {
                isChecking = false;
            }
        });
    }

    private void saveUpdateInfo(UpdateInfo info) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_HAS_UPDATE, info.hasUpdate);
        editor.putString(KEY_LATEST_VERSION, info.versionName);
        editor.putString(KEY_RELEASE_TITLE, info.title);
        editor.putString(KEY_RELEASE_BODY, info.body);
        editor.putString(KEY_APK_URL, info.apkDownloadUrl != null ? info.apkDownloadUrl : "");
        editor.putString(KEY_APK_NAME, info.apkName != null ? info.apkName : "NextTV-update.apk");
        editor.putLong(KEY_APK_SIZE, info.apkSize);
        if (info.changelog != null) {
            editor.putString(KEY_CHANGELOG_JSON, new JSONArray(info.changelog).toString());
        }
        editor.apply();
    }

    private UpdateInfo fetchUpdateInfoFromGitHub() throws Exception {
        String currentVersion = getCurrentVersion();
        JSONObject releaseObj = null;

        // 1. Kiểm tra Release mới nhất qua GitHub Releases API
        try {
            String releasesJson = httpGet(RELEASES_API_URL);
            if (releasesJson != null && !releasesJson.trim().isEmpty()) {
                JSONArray releasesArray = new JSONArray(releasesJson);
                if (releasesArray.length() > 0) {
                    releaseObj = releasesArray.getJSONObject(0);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Không thể lấy danh sách releases: " + e.getMessage());
        }

        if (releaseObj == null) {
            try {
                String latestJson = httpGet(LATEST_RELEASE_API_URL);
                if (latestJson != null && !latestJson.trim().isEmpty()) {
                    releaseObj = new JSONObject(latestJson);
                }
            } catch (Exception e) {
                Log.d(TAG, "Không tìm thấy latest release: " + e.getMessage());
            }
        }

        UpdateInfo info = new UpdateInfo();

        if (releaseObj != null) {
            String tagName = releaseObj.optString("tag_name", "");
            String title = releaseObj.optString("name", tagName);
            String body = releaseObj.optString("body", "");
            String cleanVersion = extractVersionName(tagName.isEmpty() ? title : tagName);

            info.versionName = cleanVersion;
            info.title = title;
            info.body = body;
            info.hasUpdate = compareVersions(cleanVersion, currentVersion) > 0;

            // Xử lý changelog từ release body
            List<String> changes = parseChangelogLines(body);
            // Nếu release body trống hoặc ít thông tin, bổ sung từ danh sách commits
            if (changes.isEmpty()) {
                changes = fetchRecentCommitMessages();
            }
            info.changelog = changes;

            // Tìm asset APK phù hợp
            JSONArray assets = releaseObj.optJSONArray("assets");
            if (assets != null && assets.length() > 0) {
                selectBestApkAsset(assets, info);
            }
            return info;
        }

        // 2. Nếu repo chưa tạo Release chính thức, lấy thông tin commit gần nhất để người dùng luôn xem được thay đổi
        List<String> commits = fetchRecentCommitMessages();
        info.versionName = currentVersion;
        info.title = "Phiên bản hiện tại: v" + currentVersion;
        info.changelog = commits;
        info.hasUpdate = false;
        return info;
    }

    private List<String> fetchRecentCommitMessages() {
        List<String> list = new ArrayList<>();
        try {
            String commitsJson = httpGet(COMMITS_API_URL);
            if (commitsJson != null && !commitsJson.trim().isEmpty()) {
                JSONArray array = new JSONArray(commitsJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    JSONObject commitObj = item.optJSONObject("commit");
                    if (commitObj != null) {
                        String rawMessage = commitObj.optString("message", "");
                        String cleaned = cleanCommitMessage(rawMessage);
                        if (!cleaned.isEmpty() && !list.contains(cleaned)) {
                            list.add(cleaned);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Lỗi lấy commits GitHub: " + e.getMessage());
        }
        return list;
    }

    private void selectBestApkAsset(JSONArray assets, UpdateInfo info) {
        String preferredAbi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0] : "";

        JSONObject chosenAsset = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            if (name.endsWith(".apk")) {
                if (!preferredAbi.isEmpty() && name.contains(preferredAbi.toLowerCase())) {
                    chosenAsset = asset;
                    break;
                }
                if (name.contains("universal") || name.contains("sideload")) {
                    chosenAsset = asset;
                } else if (chosenAsset == null) {
                    chosenAsset = asset;
                }
            }
        }

        if (chosenAsset != null) {
            info.apkName = chosenAsset.optString("name", "NextTV-update.apk");
            info.apkDownloadUrl = chosenAsset.optString("browser_download_url", "");
            info.apkSize = chosenAsset.optLong("size", 0L);
        }
    }

    /**
     * Tải tệp APK và tiến hành cài đặt cho bản Sideload.
     */
    public void downloadAndInstall(Activity activity, DownloadProgressListener listener) {
        if (!isSideload()) {
            openPlayStore(activity);
            return;
        }

        String apkUrl = getApkDownloadUrl();
        if (apkUrl == null || apkUrl.isEmpty()) {
            if (listener != null) {
                listener.onError("Chưa có liên kết tải APK trong bản phát hành này");
            }
            return;
        }

        if (isDownloading) {
            if (listener != null) {
                listener.onError("Đang có tiến trình tải tệp khác...");
            }
            return;
        }

        isDownloading = true;
        executor.execute(() -> {
            File targetDir = new File(activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir(), "updates");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File apkFile = new File(targetDir, getApkName());

            downloadFileWithRedirects(apkUrl, apkFile, new DownloadProgressListener() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes, int percent) {
                    if (listener != null) {
                        mainHandler.post(() -> listener.onProgress(downloadedBytes, totalBytes, percent));
                    }
                }

                @Override
                public void onSuccess(File downloadedApk) {
                    isDownloading = false;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onSuccess(downloadedApk));
                    }
                    mainHandler.post(() -> installDownloadedApk(activity, downloadedApk));
                }

                @Override
                public void onError(String errorMessage) {
                    isDownloading = false;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(errorMessage));
                    }
                }
            });
        });
    }

    private void downloadFileWithRedirects(String downloadUrl, File destinationFile, DownloadProgressListener listener) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            String currentUrl = downloadUrl;
            int redirects = 0;
            while (redirects < 6) {
                URL url = new URL(currentUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "NextTV-AndroidLauncher");
                conn.setInstanceFollowRedirects(false);

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307
                        || status == 308) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location != null && !location.isEmpty()) {
                        currentUrl = location;
                        redirects++;
                        continue;
                    }
                }
                break;
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                if (listener != null) {
                    listener.onError("Máy chủ trả về mã HTTP " + conn.getResponseCode());
                }
                return;
            }

            long totalBytes = conn.getContentLength();
            if (destinationFile.exists()) {
                destinationFile.delete();
            }

            in = new BufferedInputStream(conn.getInputStream());
            out = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[16384];
            long downloaded = 0;
            int read;
            long lastReportTime = 0;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                long now = System.currentTimeMillis();
                if (now - lastReportTime > 250 || downloaded == totalBytes) {
                    lastReportTime = now;
                    int percent = totalBytes > 0 ? (int) (downloaded * 100 / totalBytes) : -1;
                    if (listener != null) {
                        listener.onProgress(downloaded, totalBytes, percent);
                    }
                }
            }
            out.flush();

            if (listener != null) {
                listener.onSuccess(destinationFile);
            }
        } catch (Exception e) {
            if (destinationFile.exists()) destinationFile.delete();
            if (listener != null) {
                listener.onError("Lỗi tải tệp: " + e.getMessage());
            }
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Kích hoạt cài đặt tệp APK thông qua FileProvider và trình cài đặt gói chuẩn của Android TV.
     */
    public void installDownloadedApk(Activity activity, File apkFile) {
        if (!isSideload()) {
            openPlayStore(activity);
            return;
        }

        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) {
            Toast.makeText(activity, "Tệp cài đặt không tồn tại hoặc bị lỗi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra quyền cài đặt ứng dụng không rõ nguồn trên Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Vui lòng cho phép quyền 'Cài đặt ứng dụng không rõ nguồn' cho NextTV", Toast.LENGTH_LONG).show();
                try {
                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settingsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(settingsIntent);
                } catch (Exception ignored) {}
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + ".fileprovider",
                        apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Không thể mở trình cài đặt gói: " + e.getMessage());
            Toast.makeText(activity, "Lỗi khởi chạy cài đặt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Mở Google Play Store cho bản Play flavor.
     */
    public void openPlayStore(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_MARKET_URI));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e1) {
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_WEB_URL));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(webIntent);
            } catch (Exception e2) {
                Toast.makeText(activity, "Không thể mở Google Play Store", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "NextTV-AndroidLauncher");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================================================================
    // XỬ LÝ NỘI DUNG COMMIT VÀ SO SÁNH PHIÊN BẢN (KHÔNG HIỂN THỊ MÃ SỐ COMMIT)
    // =========================================================================

    /**
     * Làm sạch thông điệp commit / release note:
     * - Loại bỏ hoàn toàn mã hash SHA commit (7 đến 40 ký tự hexa).
     * - Loại bỏ URL commit GitHub.
     * - Giữ lại câu từ mô tả rõ ràng, thân thiện cho người dùng.
     */
    public static String cleanCommitMessage(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // Lấy dòng đầu tiên nếu commit message gồm nhiều dòng
        int firstNewLine = s.indexOf('\n');
        if (firstNewLine > 0) {
            s = s.substring(0, firstNewLine).trim();
        }

        // Loại bỏ text author sinh tự động của GitHub trước khi loại link: by @author in https://...
        s = s.replaceAll("(?i)\\s*by\\s+@[a-zA-Z0-9_-]+(?:\\s+in\\s+\\S+)?", "");

        // Loại bỏ markdown commit link: [abc1234](https://...)
        s = s.replaceAll("\\[[0-9a-fA-F]{7,40}\\]\\([^\\)]*\\)", "");

        // Loại bỏ đường link commit GitHub hoặc compare GitHub
        s = s.replaceAll("https?://github\\.com/[^/]+/[^/]+/(?:commit|compare)/\\S+", "");

        // Loại bỏ hash commit đặt trong ngoặc đơn hoặc ngoặc vuông: (b72c018...) hoặc [b72c018...]
        s = s.replaceAll("\\([0-9a-fA-F]{7,40}\\)", "");
        s = s.replaceAll("\\[[0-9a-fA-F]{7,40}\\]", "");

        // Loại bỏ mã hash ở đầu dòng: e.g. "b72c018: " hoặc "* b72c018 - "
        s = s.replaceAll("^[\\s*\\-\\u2022]*[0-9a-fA-F]{7,40}[:\\s\\-]+", "");

        // Loại bỏ bất kỳ chuỗi hash độc lập nào (từ 7 đến 40 ký tự hex có chứa ít nhất 1 chữ số và 1 chữ cái)
        s = s.replaceAll("\\b(?=[0-9a-fA-F]{7,40}\\b)(?=[0-9a-fA-F]*[0-9])(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{7,40}\\b", "");

        // Loại bỏ các cặp ngoặc rỗng sót lại
        s = s.replaceAll("\\(\\s*\\)", "");
        s = s.replaceAll("\\[\\s*\\]", "");

        // Loại bỏ ký tự bullet thừa ở đầu dòng
        s = s.replaceAll("^[\\s*\\-\\u2022]+\\s*", "");

        // Thu gọn khoảng trắng liên tiếp
        s = s.replaceAll("\\s{2,}", " ");

        return s.trim();
    }

    public static List<String> parseChangelogLines(String body) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        String[] lines = body.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")
                    || trimmed.toLowerCase().contains("what's changed")
                    || trimmed.toLowerCase().contains("full changelog")) {
                continue;
            }
            String cleaned = cleanCommitMessage(trimmed);
            if (!cleaned.isEmpty() && !result.contains(cleaned)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    public static String extractVersionName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1).trim();
        }
        // Lấy đoạn đầu có dạng X.Y.Z
        int end = 0;
        while (end < s.length()) {
            char c = s.charAt(end);
            if (Character.isDigit(c) || c == '.') {
                end++;
            } else {
                break;
            }
        }
        if (end > 0) {
            String v = s.substring(0, end);
            if (v.endsWith(".")) v = v.substring(0, v.length() - 1);
            return v;
        }
        return s;
    }

    /**
     * So sánh phiên bản dạng semantic:
     * Trả về > 0 nếu v1 > v2, < 0 nếu v1 < v2, = 0 nếu v1 == v2.
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        v1 = extractVersionName(v1);
        v2 = extractVersionName(v2);

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLen = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLen; i++) {
            int num1 = i < parts1.length ? parseSafeInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseSafeInt(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private static int parseSafeInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static class UpdateInfo {
        boolean hasUpdate;
        String versionName;
        String title;
        String body;
        String apkDownloadUrl;
        String apkName;
        long apkSize;
        List<String> changelog;
    }
}
