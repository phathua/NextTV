package com.next.tv.launcher.remote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Modern SaaS HTTP File Server on port 8888 (with dynamic fallback).
 * Allows responsive file browsing, drag & drop uploads with XHR progress,
 * storage metering, and PIN authentication. Operates under 1.5MB heap usage.
 */
public final class RemoteFileServer {
    private static final String TAG = "TvHome.RemoteFile";
    public static final int PORT = 8888;
    public static final int DEFAULT_PORT = 8888;

    public static final String PREFS_NAME = "remote_file_prefs";
    public static final String KEY_AUTH_ENABLED = "file_auth_enabled";
    public static final String KEY_PASSWORD_VALUE = "file_password_value";

    private static volatile RemoteFileServer instance;

    public interface OnFileUploadedListener {
        void onFileUploaded(File file);
    }

    private final Context context;
    private final File rootDir;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private int boundPort = DEFAULT_PORT;
    private OnFileUploadedListener uploadListener;

    public static RemoteFileServer getInstance(Context context) {
        if (instance == null) {
            synchronized (RemoteFileServer.class) {
                if (instance == null) {
                    instance = new RemoteFileServer(context);
                }
            }
        }
        return instance;
    }

    public RemoteFileServer(Context context) {
        this.context = context.getApplicationContext();
        File tvFiles = new File(Environment.getExternalStorageDirectory(), "TVFiles");
        if (!tvFiles.exists()) tvFiles.mkdirs();
        this.rootDir = Environment.getExternalStorageDirectory();
    }

    public int getPort() {
        return boundPort;
    }

    public boolean isAuthEnabled() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_AUTH_ENABLED, false);
    }

    public void setAuthEnabled(boolean enabled) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_AUTH_ENABLED, enabled).apply();
    }

    public String getPassword() {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_PASSWORD_VALUE, "");
    }

    public void setPassword(String password) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PASSWORD_VALUE, password != null ? password : "").apply();
    }

    public void setOnFileUploadedListener(OnFileUploadedListener listener) {
        this.uploadListener = listener;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        serverThread = new Thread(() -> {
            ServerSocket socket = null;
            int port = DEFAULT_PORT;
            try {
                socket = new ServerSocket(port);
            } catch (Exception ignored) {
                for (int p = 8889; p <= 8900; p++) {
                    try {
                        socket = new ServerSocket(p);
                        port = p;
                        break;
                    } catch (Exception ex) {}
                }
            }

            if (socket == null) {
                try {
                    socket = new ServerSocket(0);
                    port = socket.getLocalPort();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to bind file server socket", e);
                    isRunning = false;
                    return;
                }
            }

            this.serverSocket = socket;
            this.boundPort = port;
            Log.i(TAG, "RemoteFileServer started on port " + boundPort);

            while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client), "file-server-client").start();
                } catch (Exception e) {
                    if (!isRunning) break;
                }
            }
        }, "file-server-srv");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public synchronized void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public static String getIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return Formatter.formatIpAddress(ipInt);
                }
            }
        } catch (Exception ignored) {}

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedInputStream bis = new BufferedInputStream(in);
            String requestLine = readLine(bis);
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];

            // Parse headers
            int contentLength = 0;
            String contentType = "";
            String cookie = "";
            boolean isAjax = false;
            String line;
            while ((line = readLine(bis)) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                } else if (lower.startsWith("cookie:")) {
                    cookie = line.substring(7).trim();
                } else if (lower.startsWith("x-requested-with:")) {
                    isAjax = true;
                }
            }

            boolean authRequired = isAuthEnabled() && !getPassword().isEmpty();
            String expectedToken = "auth_" + Integer.toHexString(getPassword().hashCode());
            boolean isAuthenticated = !authRequired || cookie.contains("file_token=" + expectedToken);

            if ("POST".equalsIgnoreCase(method) && uri.startsWith("/login")) {
                byte[] bodyBytes = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = bis.read(bodyBytes, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String submittedPass = "";
                if (body.contains("pass=")) {
                    int start = body.indexOf("pass=") + 5;
                    int end = body.indexOf("&", start);
                    submittedPass = URLDecoder.decode(end > start ? body.substring(start, end) : body.substring(start), "UTF-8");
                }
                if (getPassword().equals(submittedPass.trim())) {
                    String redirect = "HTTP/1.1 303 See Other\r\nSet-Cookie: file_token=" + expectedToken + "; Path=/; HttpOnly\r\nLocation: /\r\n\r\n";
                    out.write(redirect.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } else {
                    sendLoginHtml(out, true);
                }
                return;
            }

            if (!isAuthenticated) {
                sendLoginHtml(out, false);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                if (uri.startsWith("/api/ping") || uri.startsWith("/ping")) {
                    sendOk(out);
                    return;
                } else if (uri.startsWith("/download?path=")) {
                    handleDownload(uri.substring(15), out);
                } else {
                    String subPath = "";
                    if (uri.startsWith("/?path=")) {
                        subPath = uri.substring(7);
                    }
                    sendHtmlResponse(out, subPath);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (uri.startsWith("/upload")) {
                    handleUpload(bis, contentType, contentLength, isAjax, out);
                } else if (uri.startsWith("/delete?path=")) {
                    handleDelete(uri.substring(13), isAjax, out);
                } else if (uri.startsWith("/install?path=")) {
                    handleInstall(uri.substring(14), isAjax, out);
                } else {
                    send404(out);
                }
            } else {
                send404(out);
            }
        } catch (Exception e) {
            Log.w(TAG, "Client handle error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void sendLoginHtml(OutputStream out, boolean hasError) throws Exception {
        String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Bảo vệ tệp TV</title>" +
                "<style>" +
                "body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background:#09090b;color:#f4f4f5;margin:0;padding:24px;display:flex;align-items:center;justify-content:center;min-height:100vh}" +
                ".card{background:#18181b;border:1px solid #27272a;border-radius:16px;padding:28px;max-width:360px;width:100%;text-align:center}" +
                "h2{margin:0 0 8px;font-size:18px;color:#fff}" +
                "p{color:#a1a1aa;font-size:13px;margin:0 0 20px}" +
                "input{width:100%;padding:12px 14px;background:#09090b;border:1px solid #3f3f46;border-radius:10px;color:#fff;font-size:16px;margin-bottom:16px;outline:none;text-align:center;letter-spacing:2px;box-sizing:border-box}" +
                "input:focus{border-color:#10b981}" +
                "button{width:100%;padding:12px;background:#10b981;color:#fff;border:none;border-radius:10px;font-size:14px;font-weight:600;cursor:pointer}" +
                "button:active{background:#059669}" +
                ".err{color:#f43f5e;font-size:12px;margin-bottom:12px}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>BẢO VỆ TỆP TV</h2>" +
                "<p>Nhập mã PIN hoặc mật khẩu</p>" +
                "<form method='post' action='/login'>" +
                "<input type='password' name='pass' placeholder='••••' autofocus required>" +
                (hasError ? "<div class='err'>Mật khẩu không chính xác</div>" : "") +
                "<button type='submit'>Mở khóa</button>" +
                "</form></div></body></html>";

        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + htmlBytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(htmlBytes);
        out.flush();
    }

    private void handleDownload(String encodedPath, OutputStream out) throws Exception {
        String decoded = URLDecoder.decode(encodedPath, "UTF-8");
        File target = new File(rootDir, decoded);
        if (!target.exists() || target.isDirectory() || !isSafePath(target)) {
            send404(out);
            return;
        }

        long length = target.length();
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Content-Disposition: attachment; filename=\"" + target.getName() + "\"\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));

        try (FileInputStream fis = new FileInputStream(target)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        out.flush();
    }

    private void handleDelete(String encodedPath, boolean isAjax, OutputStream out) throws Exception {
        String decoded = URLDecoder.decode(encodedPath, "UTF-8");
        File target = new File(rootDir, decoded);
        String parentRel = "";
        if (target.exists() && isSafePath(target)) {
            File parent = target.getParentFile();
            if (parent != null && !parent.equals(rootDir)) {
                try {
                    String rootCanon = rootDir.getCanonicalPath();
                    String pCanon = parent.getCanonicalPath();
                    if (pCanon.startsWith(rootCanon)) {
                        parentRel = pCanon.substring(rootCanon.length()).replace('\\', '/');
                        if (parentRel.startsWith("/")) parentRel = parentRel.substring(1);
                    }
                } catch (Exception ignored) {}
            }
            target.delete();
        }

        if (isAjax) {
            String json = "{\"status\":\"ok\"}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(b);
        } else {
            String redirect = "HTTP/1.1 303 See Other\r\nLocation: /" + (parentRel.isEmpty() ? "" : "?path=" + parentRel) + "\r\n\r\n";
            out.write(redirect.getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private void handleInstall(String encodedPath, boolean isAjax, OutputStream out) throws Exception {
        String decoded = URLDecoder.decode(encodedPath, "UTF-8");
        File target = new File(rootDir, decoded);
        if (target.exists() && isSafePath(target) && target.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            installApk(target);
        }

        if (isAjax) {
            String json = "{\"status\":\"ok\"}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(b);
        } else {
            String redirect = "HTTP/1.1 303 See Other\r\nLocation: /\r\n\r\n";
            out.write(redirect.getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private File getTvFilesDir() {
        File tvFiles = new File(rootDir, "TVFiles");
        if (!tvFiles.exists() && !tvFiles.mkdirs()) {
            File fallback = context.getExternalFilesDir("TVFiles");
            if (fallback != null) {
                if (!fallback.exists()) fallback.mkdirs();
                return fallback;
            }
        }
        return tvFiles;
    }

    private void handleUpload(BufferedInputStream bis, String contentType, int contentLength, boolean isAjax, OutputStream out) throws Exception {
        if (!contentType.contains("multipart/form-data") || !contentType.contains("boundary=")) {
            send404(out);
            return;
        }

        String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }

        File saveDir = getTvFilesDir();
        String filename = "uploaded_" + System.currentTimeMillis();

        // 1. Read preamble until first boundary
        String line;
        while ((line = readLine(bis)) != null) {
            if (line.contains(boundary)) break;
        }

        // 2. Read Part Headers
        while ((line = readLine(bis)) != null && !line.isEmpty()) {
            if (line.toLowerCase(Locale.US).contains("filename=\"")) {
                int start = line.indexOf("filename=\"") + 10;
                int end = line.indexOf("\"", start);
                if (end > start) {
                    filename = new File(line.substring(start, end)).getName();
                }
            }
        }

        // 3. Stream binary file body up to next boundary
        File destFile = new File(saveDir, filename);
        byte[] boundaryBytes = ("\r\n" + boundary).getBytes(StandardCharsets.US_ASCII);

        try (FileOutputStream fos = new FileOutputStream(destFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int tailLen = boundaryBytes.length + 64;
            byte[] window = new byte[tailLen];
            int windowSize = 0;

            int b;
            while ((b = bis.read()) != -1) {
                if (windowSize < tailLen) {
                    window[windowSize++] = (byte) b;
                } else {
                    // Check if boundary is in window
                    int matchIdx = findSequence(window, windowSize, boundaryBytes);
                    if (matchIdx >= 0) {
                        bos.write(window, 0, matchIdx);
                        break;
                    }
                    bos.write(window[0]);
                    System.arraycopy(window, 1, window, 0, windowSize - 1);
                    window[windowSize - 1] = (byte) b;
                }
            }
            if (b == -1 && windowSize > 0) {
                int matchIdx = findSequence(window, windowSize, boundaryBytes);
                if (matchIdx >= 0) {
                    bos.write(window, 0, matchIdx);
                } else {
                    bos.write(window, 0, windowSize);
                }
            }
            bos.flush();
        } catch (Exception e) {
            Log.e(TAG, "File write error: " + e.getMessage());
            sendErrorJson(out, "Lỗi ghi tệp trên TV");
            return;
        }

        // 4. Drain remaining bytes in request body to prevent TCP RST on socket close
        try {
            while (bis.available() > 0) {
                if (bis.read() == -1) break;
            }
        } catch (Exception ignored) {}

        // Upload successful: do not trigger automatic silent package installer prompts;
        // The user can explicitly click the Install action on the Web UI if intended.

        if (uploadListener != null) {
            uploadListener.onFileUploaded(destFile);
        }

        if (isAjax) {
            String json = "{\"status\":\"ok\",\"file\":\"" + destFile.getName() + "\"}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(b);
        } else {
            String redirect = "HTTP/1.1 303 See Other\r\nLocation: /?path=TVFiles\r\n\r\n";
            out.write(redirect.getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private static int findSequence(byte[] data, int dataLen, byte[] sequence) {
        if (sequence.length == 0 || dataLen < sequence.length) return -1;
        for (int i = 0; i <= dataLen - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (data[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private void sendErrorJson(OutputStream out, String msg) {
        try {
            String json = "{\"status\":\"error\",\"message\":\"" + msg + "\"}";
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            String resp = "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
        } catch (Exception ignored) {}
    }

    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Install APK trigger failed: " + e.getMessage());
        }
    }

    private void sendHtmlResponse(OutputStream out, String subPath) throws Exception {
        String decodedSub = URLDecoder.decode(subPath, "UTF-8").trim();
        File targetDir = new File(rootDir, decodedSub);
        if (!targetDir.exists() || !targetDir.isDirectory() || !isSafePath(targetDir)) {
            targetDir = rootDir;
            decodedSub = "";
        }

        File[] allEntries = targetDir.listFiles();
        List<File> dirList = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        if (allEntries != null) {
            for (File f : allEntries) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    dirList.add(f);
                } else {
                    fileList.add(f);
                }
            }
        }
        Collections.sort(dirList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        boolean hasPerm = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPerm = Environment.isExternalStorageManager();
        } else {
            hasPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        // Storage calculations
        long totalSpace = rootDir.getTotalSpace();
        long freeSpace = rootDir.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;
        double totalGb = (double) totalSpace / (1024.0 * 1024.0 * 1024.0);
        double usedGb = (double) usedSpace / (1024.0 * 1024.0 * 1024.0);
        int percentUsed = (totalSpace > 0) ? (int) ((usedSpace * 100) / totalSpace) : 0;

        // Build breadcrumb
        StringBuilder breadcrumbHtml = new StringBuilder();
        breadcrumbHtml.append("<a class='crumb-item' href='/'><svg viewBox='0 0 24 24' width='14' height='14' fill='currentColor'><path d='M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z'/></svg>Bộ nhớ trong</a>");
        if (!decodedSub.isEmpty()) {
            String[] segments = decodedSub.split("/");
            StringBuilder pathAccumulator = new StringBuilder();
            for (String seg : segments) {
                if (seg.isEmpty()) continue;
                if (pathAccumulator.length() > 0) pathAccumulator.append("/");
                pathAccumulator.append(seg);
                breadcrumbHtml.append("<span class='crumb-sep'>/</span>");
                breadcrumbHtml.append("<a class='crumb-item' href='/?path=").append(pathAccumulator).append("'>").append(seg).append("</a>");
            }
        }

        // Build Folders Grid Cards
        StringBuilder foldersHtml = new StringBuilder();
        if (!decodedSub.isEmpty()) {
            File parent = targetDir.getParentFile();
            String parentRel = "";
            if (parent != null && !parent.equals(rootDir) && isSafePath(parent)) {
                String rCanon = rootDir.getCanonicalPath();
                String pCanon = parent.getCanonicalPath();
                if (pCanon.startsWith(rCanon)) {
                    parentRel = pCanon.substring(rCanon.length()).replace('\\', '/');
                    if (parentRel.startsWith("/")) parentRel = parentRel.substring(1);
                }
            }
            foldersHtml.append("<div class='folder-card up-card' onclick=\"location.href='/")
                    .append(parentRel.isEmpty() ? "" : "?path=" + parentRel)
                    .append("'\">")
                    .append("<div class='folder-icon' style='color:#F59E0B'><svg viewBox='0 0 24 24' width='24' height='24' fill='currentColor'><path d='M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z'/></svg></div>")
                    .append("<div class='folder-info'><div class='folder-name'>Quay lại</div><div class='folder-count'>Thư mục cha</div></div></div>");
        }

        for (File f : dirList) {
            String name = f.getName();
            String relPath = decodedSub.isEmpty() ? name : decodedSub + "/" + name;
            File[] sub = f.listFiles();
            int subCount = sub != null ? sub.length : 0;
            foldersHtml.append("<div class='folder-card' data-name='").append(name.toLowerCase(Locale.US)).append("' onclick=\"location.href='/?path=").append(relPath).append("'\">")
                    .append("<div class='folder-icon' style='color:#F59E0B'><svg viewBox='0 0 24 24' width='26' height='26' fill='currentColor'><path d='M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg></div>")
                    .append("<div class='folder-info'>")
                    .append("<div class='folder-name'>").append(name).append("</div>")
                    .append("<div class='folder-count'>").append(subCount).append(" mục</div>")
                    .append("</div></div>");
        }

        // Build Files List & Grid
        StringBuilder filesHtml = new StringBuilder();
        for (File f : fileList) {
            String name = f.getName();
            String relPath = decodedSub.isEmpty() ? name : decodedSub + "/" + name;
            String size = formatFileSize(f.length());
            String date = sdf.format(new Date(f.lastModified()));

            String lowerName = name.toLowerCase(Locale.US);
            String typeBadge;
            String badgeBg;
            String badgeColor;
            String iconSvg;

            if (lowerName.endsWith(".apk")) {
                typeBadge = "Gói APK";
                badgeBg = "#10B98122";
                badgeColor = "#10B981";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h2v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-4.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 2.23 12.95 2 12 2c-.96 0-1.86.23-2.66.63L7.85 1.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 4.26 6 5.92 6 7.8h12c0-1.88-.97-3.54-2.47-4.64zM10 5H9V4h1v1zm5 0h-1V4h1v1z'/></svg>";
            } else if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov")) {
                typeBadge = "Video";
                badgeBg = "#A855F722";
                badgeColor = "#A855F7";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z'/></svg>";
            } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".jpeg")) {
                typeBadge = "Hình ảnh";
                badgeBg = "#3B82F622";
                badgeColor = "#3B82F6";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z'/></svg>";
            } else if (lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a")) {
                typeBadge = "Nhạc";
                badgeBg = "#EC489922";
                badgeColor = "#EC4899";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z'/></svg>";
            } else if (lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".txt") || lowerName.endsWith(".zip") || lowerName.endsWith(".rar")) {
                typeBadge = "Tài liệu";
                badgeBg = "#F9731622";
                badgeColor = "#F97316";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z'/></svg>";
            } else {
                typeBadge = "Tệp tin";
                badgeBg = "#71717A22";
                badgeColor = "#A1A1AA";
                iconSvg = "<svg viewBox='0 0 24 24' width='22' height='22' fill='currentColor'><path d='M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z'/></svg>";
            }

            filesHtml.append("<div class='file-item' data-name='").append(name.toLowerCase(Locale.US)).append("'>")
                    .append("<div class='file-left'>")
                    .append("<div class='file-icon' style='color:").append(badgeColor).append("'>").append(iconSvg).append("</div>")
                    .append("<div class='file-info'>")
                    .append("<div class='file-name' title='").append(name).append("'>").append(name).append("</div>")
                    .append("<div class='file-meta'>")
                    .append("<span class='file-badge' style='background:").append(badgeBg).append(";color:").append(badgeColor).append("'>").append(typeBadge).append("</span>")
                    .append("<span class='file-size'>").append(size).append("</span>")
                    .append("<span class='file-date'>").append(date).append("</span>")
                    .append("</div></div></div>")
                    .append("<div class='file-actions'>");
            if (lowerName.endsWith(".apk")) {
                filesHtml.append("<button class='btn-action btn-inst' onclick=\"installApk('").append(relPath).append("')\" title='Cài đặt lên TV'><svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2'><path d='M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12'/></svg></button>");
            }
            filesHtml.append("<a class='btn-action btn-dl' href='/download?path=").append(relPath).append("' title='Tải về'><svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2'><path d='M12 4v12m0 0l-4-4m4 4l4-4M4 20h16'/></svg></a>")
                    .append("<button class='btn-action btn-del' onclick=\"deleteFile('").append(relPath).append("')\" title='Xóa'><svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2'><path d='M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16'/></svg></button>")
                    .append("</div></div>");
        }

        String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<title>Quản lý tệp TV</title><style>" +
                "*{box-sizing:border-box}" +
                "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#09090b;color:#f4f4f5;margin:0;padding:20px;line-height:1.5}" +
                ".app-wrap{max-width:1120px;margin:0 auto}" +
                ".top-header{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:16px;padding-bottom:16px;border-bottom:1px solid #27272a;margin-bottom:20px}" +
                ".brand-wrap{display:flex;align-items:center;gap:12px}" +
                ".brand-logo{width:36px;height:36px;background:#18181b;border:1px solid #27272a;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#10b981}" +
                ".brand-title{font-size:18px;font-weight:600;color:#fff;letter-spacing:-0.01em}" +
                ".header-tools{display:flex;align-items:center;gap:12px;flex-wrap:wrap}" +
                ".storage-pill{background:#18181b;border:1px solid #27272a;border-radius:20px;padding:6px 14px;display:flex;align-items:center;gap:10px;font-size:12px;color:#a1a1aa}" +
                ".storage-bar{width:64px;height:6px;background:#27272a;border-radius:3px;overflow:hidden}" +
                ".storage-fill{height:100%;background:#10b981;border-radius:3px}" +
                ".search-box{display:flex;align-items:center;gap:8px;background:#18181b;border:1px solid #27272a;border-radius:10px;padding:7px 12px;width:220px}" +
                ".search-box input{background:transparent;border:none;outline:none;color:#fff;font-size:13px;width:100%}" +
                ".search-box svg{color:#71717a;flex-shrink:0}" +
                ".btn-upload{display:inline-flex;align-items:center;gap:8px;background:#10b981;color:#fff;border-radius:10px;padding:8px 16px;font-size:13px;font-weight:600;cursor:pointer;transition:background 0.15s;border:none}" +
                ".btn-upload:hover{background:#059669}" +
                ".nav-bar{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:13px;margin-bottom:24px}" +
                ".perm-banner{background:#18181b;border:1px solid #ef444455;border-radius:14px;padding:16px 20px;display:flex;align-items:center;gap:16px;margin-bottom:24px}" +
                ".perm-banner-icon{color:#ef4444;flex-shrink:0}" +
                ".perm-banner-text{flex:1}" +
                ".perm-banner-title{font-size:15px;font-weight:600;color:#fff;margin-bottom:4px}" +
                ".perm-banner-desc{font-size:13px;color:#a1a1aa;margin:0}" +
                ".crumb-item{color:#a1a1aa;text-decoration:none;display:flex;align-items:center;gap:6px;padding:4px 8px;border-radius:6px;transition:background 0.15s}" +
                ".crumb-item:hover{background:#18181b;color:#fff}" +
                ".crumb-sep{color:#52525b}" +
                ".offline-overlay{display:none;position:fixed;inset:0;background:rgba(9,9,11,0.85);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);z-index:9999;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:24px}" +
                ".offline-card{background:#18181b;border:1px solid #ef444455;border-radius:20px;padding:32px 28px;max-width:380px;width:100%;box-shadow:0 20px 40px rgba(0,0,0,0.7);display:flex;flex-direction:column;align-items:center;animation:cardPop .2s ease-out}" +
                "@keyframes cardPop{from{opacity:0;transform:scale(0.95)}to{opacity:1;transform:scale(1)}}" +
                ".offline-icon{color:#ef4444;margin-bottom:16px}" +
                ".offline-title{font-size:18px;font-weight:700;color:#fff;margin:0 0 8px}" +
                ".offline-desc{font-size:13px;color:#a1a1aa;margin:0 0 20px;line-height:1.5}" +
                ".offline-ping-tag{display:inline-flex;align-items:center;gap:8px;font-size:12px;color:#f43f5e;background:#e11d4815;padding:6px 14px;border-radius:999px;border:1px solid #e11d4833}" +
                ".offline-dot{width:8px;height:8px;background:#ef4444;border-radius:50%;animation:pulseDot 1.2s infinite ease-in-out}" +
                "@keyframes pulseDot{0%,100%{opacity:1;transform:scale(1)}50%{opacity:0.4;transform:scale(0.85)}}" +
                ".drop-zone{border:2px dashed #27272a;border-radius:14px;padding:18px;text-align:center;background:#18181b40;margin-bottom:24px;transition:border-color 0.2s,background 0.2s}" +
                ".drop-zone.active{border-color:#10b981;background:#10b98110}" +
                ".upload-progress{background:#18181b;border:1px solid #27272a;border-radius:12px;padding:14px;margin-bottom:20px;display:none}" +
                ".progress-info{display:flex;justify-content:space-between;font-size:13px;margin-bottom:8px}" +
                ".progress-track{height:6px;background:#27272a;border-radius:3px;overflow:hidden}" +
                ".progress-bar{height:100%;background:#10b981;width:0%;transition:width 0.15s}" +
                ".sec-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;margin-top:20px}" +
                ".sec-title{font-size:12px;font-weight:600;color:#71717a;letter-spacing:0.08em;text-transform:uppercase}" +
                ".grid-folders{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin-bottom:28px}" +
                ".folder-card{background:#18181b;border:1px solid #27272a;border-radius:12px;padding:14px;display:flex;align-items:center;gap:12px;cursor:pointer;transition:transform 0.12s,border-color 0.15s}" +
                ".folder-card:hover{border-color:#3f3f46;transform:translateY(-1px)}" +
                ".folder-info{overflow:hidden}" +
                ".folder-name{font-size:14px;font-weight:500;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
                ".folder-count{font-size:12px;color:#71717a;margin-top:2px}" +
                ".file-container{display:flex;flex-direction:column;gap:8px}" +
                ".file-container.grid-view{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}" +
                ".file-item{background:#18181b;border:1px solid #27272a;border-radius:10px;padding:10px 14px;display:flex;align-items:center;justify-content:space-between;transition:border-color 0.15s}" +
                ".file-item:hover{border-color:#3f3f46}" +
                ".file-left{display:flex;align-items:center;gap:12px;min-width:0}" +
                ".file-icon{flex-shrink:0}" +
                ".file-info{min-width:0}" +
                ".file-name{font-size:13px;font-weight:500;color:#fff;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:440px}" +
                ".file-meta{display:flex;align-items:center;gap:8px;font-size:11px;color:#71717a;margin-top:2px}" +
                ".file-badge{padding:1px 6px;border-radius:4px;font-weight:500;font-size:10px}" +
                ".file-actions{display:flex;align-items:center;gap:6px}" +
                ".btn-action{width:32px;height:32px;border-radius:8px;background:#27272a;border:none;display:inline-flex;align-items:center;justify-content:center;cursor:pointer;transition:background 0.15s;text-decoration:none}" +
                ".btn-inst{color:#10b981}.btn-inst:hover{background:#10b98122}" +
                ".btn-dl{color:#06b6d4}.btn-dl:hover{background:#0891b222}" +
                ".btn-del{color:#f43f5e}.btn-del:hover{background:#e11d4822}" +
                ".view-toggle-btn{background:#18181b;border:1px solid #27272a;color:#a1a1aa;padding:5px 8px;border-radius:6px;cursor:pointer;display:inline-flex;align-items:center}" +
                ".view-toggle-btn.active{background:#27272a;color:#fff}" +
                "@media(max-width:768px){" +
                ".top-header{flex-direction:column;align-items:stretch}" +
                ".header-tools{flex-direction:column;align-items:stretch}" +
                ".search-box{width:100%}" +
                ".storage-pill{justify-content:space-between}" +
                ".btn-upload{justify-content:center}" +
                ".grid-folders{grid-template-columns:repeat(2,1fr)}" +
                ".file-name{max-width:180px}" +
                "}" +
                "</style></head><body>" +
                "<div id='offlineOverlay' class='offline-overlay'>" +
                "<div class='offline-card'>" +
                "<div class='offline-icon'><svg width='54' height='54' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><g opacity='0.4'><path d='M6.50001 18L6.50001 17.9105C6.49991 17.0449 6.49981 16.2512 6.58661 15.6056C6.6822 14.8946 6.90709 14.1432 7.52514 13.5251C8.14319 12.9071 8.89464 12.6822 9.6056 12.5866C10.2512 12.4998 11.0449 12.4999 11.9105 12.5H12.0895C12.9551 12.4999 13.7488 12.4998 14.3944 12.5866C15.1054 12.6822 15.8568 12.9071 16.4749 13.5251C17.0929 14.1432 17.3178 14.8946 17.4134 15.6056C17.4989 16.2417 17.5001 17.0215 17.5 17.8722C20.0726 17.3221 22 15.0599 22 12.3529C22 9.88113 20.393 7.78024 18.1551 7.01498C17.8371 4.19371 15.4159 2 12.4762 2C9.32028 2 6.7619 4.52827 6.7619 7.64706C6.7619 8.33687 6.88706 8.9978 7.11616 9.60887C6.8475 9.55673 6.56983 9.52941 6.28571 9.52941C3.91878 9.52941 2 11.4256 2 13.7647C2 16.1038 3.91878 18 6.28571 18L6.50001 18Z' fill='currentColor'/></g><path fill-rule='evenodd' clip-rule='evenodd' d='M12 22C10.1144 22 9.17157 22 8.58579 21.4142C8 20.8284 8 19.8856 8 18C8 16.1144 8 15.1716 8.58579 14.5858C9.17157 14 10.1144 14 12 14C13.8856 14 14.8284 14 15.4142 14.5858C16 15.1716 16 16.1144 16 18C16 19.8856 16 20.8284 15.4142 21.4142C14.8284 22 13.8856 22 12 22ZM10.6936 15.7508C10.4333 15.4905 10.0112 15.4905 9.75082 15.7508C9.49047 16.0112 9.49047 16.4333 9.75082 16.6936L11.0572 18L9.75082 19.3064C9.49047 19.5667 9.49047 19.9888 9.75082 20.2492C10.0112 20.5095 10.4333 20.5095 10.6936 20.2492L12 18.9428L13.3064 20.2492C13.5667 20.5095 13.9888 20.5095 14.2492 20.2492C14.5095 19.9888 14.5095 19.5667 14.2492 19.3064L12.9428 18L14.2492 16.6936C14.5095 16.4333 14.5095 16.0112 14.2492 15.7508C13.9888 15.4905 13.5667 15.4905 13.3064 15.7508L12 17.0572L10.6936 15.7508Z' fill='currentColor'/></svg></div>" +
                "<h3 class='offline-title'>MẤT KẾT NỐI VỚI TV</h3>" +
                "<p class='offline-desc'>Không thể kết nối đến máy chủ TV. Vui lòng kiểm tra lại cùng mạng Wi-Fi hoặc mở lại màn hình trên TV.</p>" +
                "<div class='offline-ping-tag'><div class='offline-dot'></div>Đang tự động kết nối lại...</div>" +
                "</div></div>" +
                "<div class='app-wrap'>" +
                "<header class='top-header'>" +
                "<div class='brand-wrap'>" +
                "<div class='brand-logo'><svg viewBox='0 0 24 24' width='20' height='20' fill='currentColor'><path d='M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z'/></svg></div>" +
                "<div class='brand-title'>Quản lý tệp TV</div>" +
                "</div>" +
                "<div class='header-tools'>" +
                "<div class='search-box'>" +
                "<svg viewBox='0 0 24 24' width='14' height='14'><circle cx='11' cy='11' r='7' fill='none' stroke='currentColor' stroke-width='2'/><path d='M20 20l-4-4' stroke='currentColor' stroke-width='2'/></svg>" +
                "<input type='text' id='searchInput' placeholder='Tìm tệp...' oninput='filterItems(this.value)'>" +
                "</div>" +
                "<div class='storage-pill'>" +
                "<span>Đã dùng " + String.format(Locale.US, "%.1f", usedGb) + " / " + String.format(Locale.US, "%.1f", totalGb) + " GB</span>" +
                "<div class='storage-bar'><div class='storage-fill' style='width:" + percentUsed + "%'></div></div>" +
                "</div>" +
                "<label class='btn-upload' for='fileInput'>" +
                "<svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2'><path d='M12 4v12m0 0l-4-4m4 4l4-4M4 20h16'/></svg>" +
                "<span>Tải tệp lên</span>" +
                "</label>" +
                "<input type='file' id='fileInput' style='display:none' onchange='uploadFiles(this.files)'>" +
                "</div>" +
                "</header>" +
                "<nav class='nav-bar'>" + breadcrumbHtml + "</nav>" +
                (!hasPerm ? "<div class='perm-banner'>" +
                        "<div class='perm-banner-icon'><svg width='36' height='36' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><path opacity='0.4' d='M22 11.7979V14C22 17.7712 22 19.6569 20.8284 20.8284C19.6569 22 17.7712 22 14 22H10C6.22876 22 4.34315 22 3.17157 20.8284C2 19.6569 2 17.7712 2 14V6.94975C2 6.06722 2 5.62595 2.06935 5.25839C2.37464 3.64031 3.64031 2.37464 5.25839 2.06935C5.62595 2 6.06722 2 6.94975 2C7.33642 2 7.52976 2 7.71557 2.01738C8.51665 2.09229 9.27652 2.40704 9.89594 2.92051C10.0396 3.03961 10.1763 3.17633 10.4497 3.44975L11 4C11.8158 4.81578 12.2237 5.22367 12.7121 5.49543C12.9804 5.64471 13.2651 5.7626 13.5604 5.84678C14.0979 6 14.6747 6 15.8284 6H16.2021C18.8345 6 20.1506 6 21.0062 6.76946C21.0849 6.84024 21.1598 6.91514 21.2305 6.99383C22 7.84935 22 9.16554 22 11.7979Z' fill='currentColor'/><path fill-rule='evenodd' clip-rule='evenodd' d='M9.25 13C9.25 11.4812 10.4812 10.25 12 10.25C13.5188 10.25 14.75 11.4812 14.75 13C14.75 14.2588 13.9043 15.32 12.75 15.6465V17.5C12.75 17.9142 12.4142 18.25 12 18.25C11.5858 18.25 11.25 17.9142 11.25 17.5V15.6465C10.0957 15.32 9.25 14.2588 9.25 13ZM10.75 13C10.75 12.3096 11.3096 11.75 12 11.75C12.6904 11.75 13.25 12.3096 13.25 13C13.25 13.6904 12.6904 14.25 12 14.25C11.3096 14.25 10.75 13.6904 10.75 13Z' fill='currentColor'/></svg></div>" +
                        "<div class='perm-banner-text'>" +
                        "<div class='perm-banner-title'>Không thể đọc dữ liệu bộ nhớ TV do chưa cấp quyền Truy cập tệp</div>" +
                        "<p class='perm-banner-desc'>Hãy mở bảng điều khiển trên TV, vào mục 'Quyền truy cập tệp' và bấm cho phép để đọc đầy đủ các tệp tin.</p>" +
                        "</div></div>" : "") +
                "<div id='dropZone' class='drop-zone'>" +
                "<div style='font-size:13px;color:#a1a1aa;'>Kéo thả tệp tin vào đây hoặc bấm nút <strong style='color:#10b981;'>Tải tệp lên</strong></div>" +
                "</div>" +
                "<div id='uploadProgress' class='upload-progress'>" +
                "<div class='progress-info'><span id='uploadFileName'>Đang gửi...</span><span id='uploadPercent'>0%</span></div>" +
                "<div class='progress-track'><div id='progressBar' class='progress-bar'></div></div>" +
                "</div>" +
                "<div class='sec-head'><div class='sec-title'>THƯ MỤC (<span id='folderCount'>" + dirList.size() + "</span>)</div></div>" +
                "<div class='grid-folders' id='foldersGrid'>" + (foldersHtml.length() > 0 ? foldersHtml : "<div style='color:#71717a;font-size:13px;'>Không có thư mục</div>") + "</div>" +
                "<div class='sec-head'>" +
                "<div class='sec-title'>TẤT CẢ TỆP TIN (<span id='fileCount'>" + fileList.size() + "</span>)</div>" +
                "<div><button id='btnToggleView' class='view-toggle-btn' onclick='toggleView()'>Đổi dạng lưới</button></div>" +
                "</div>" +
                "<div class='file-container' id='filesContainer'>" + (filesHtml.length() > 0 ? filesHtml : "<div style='color:#71717a;font-size:13px;'>Chưa có tệp tin</div>") + "</div>" +
                "</div>" +
                "<script>" +
                "function filterItems(q){" +
                "  var val=(q||'').toLowerCase().trim();" +
                "  var fds=document.querySelectorAll('.folder-card');" +
                "  var fls=document.querySelectorAll('.file-item');" +
                "  var vc=0,fc=0;" +
                "  fds.forEach(function(el){" +
                "    var n=(el.getAttribute('data-name')||'');" +
                "    var m=!val||n.indexOf(val)!==-1;" +
                "    el.style.display=m?'flex':'none';" +
                "    if(m)vc++;" +
                "  });" +
                "  fls.forEach(function(el){" +
                "    var n=(el.getAttribute('data-name')||'');" +
                "    var m=!val||n.indexOf(val)!==-1;" +
                "    el.style.display=m?'flex':'none';" +
                "    if(m)fc++;" +
                "  });" +
                "  document.getElementById('folderCount').innerText=vc;" +
                "  document.getElementById('fileCount').innerText=fc;" +
                "}" +
                "function toggleView(){" +
                "  var c=document.getElementById('filesContainer');" +
                "  var btn=document.getElementById('btnToggleView');" +
                "  if(c.classList.contains('grid-view')){" +
                "    c.classList.remove('grid-view');" +
                "    btn.innerText='Đổi dạng lưới';" +
                "  }else{" +
                "    c.classList.add('grid-view');" +
                "    btn.innerText='Đổi dạng danh sách';" +
                "  }" +
                "}" +
                "function deleteFile(path){" +
                "  if(!confirm('Xác nhận xóa tệp này?'))return;" +
                "  var xhr=new XMLHttpRequest();" +
                "  xhr.open('POST','/delete?path='+encodeURIComponent(path),true);" +
                "  xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "  xhr.onload=function(){location.reload();};" +
                "  xhr.send();" +
                "}" +
                "function installApk(path){" +
                "  var xhr=new XMLHttpRequest();" +
                "  xhr.open('POST','/install?path='+encodeURIComponent(path),true);" +
                "  xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "  xhr.onload=function(){alert('Đang mở cài đặt lên TV!');};" +
                "  xhr.send();" +
                "}" +
                "var dz=document.getElementById('dropZone');" +
                "window.addEventListener('dragover',function(e){e.preventDefault();dz.classList.add('active');});" +
                "window.addEventListener('dragleave',function(e){if(e.clientX<=0||e.clientY<=0)dz.classList.remove('active');});" +
                "window.addEventListener('drop',function(e){" +
                "  e.preventDefault();dz.classList.remove('active');" +
                "  if(e.dataTransfer&&e.dataTransfer.files&&e.dataTransfer.files.length>0){" +
                "    uploadFiles(e.dataTransfer.files);" +
                "  }" +
                "});" +
                "function uploadFiles(files){" +
                "  if(!files||files.length===0)return;" +
                "  var file=files[0];" +
                "  var box=document.getElementById('uploadProgress');" +
                "  var bar=document.getElementById('progressBar');" +
                "  var nameEl=document.getElementById('uploadFileName');" +
                "  var pctEl=document.getElementById('uploadPercent');" +
                "  box.style.display='block';" +
                "  nameEl.innerText=file.name;" +
                "  pctEl.innerText='0%';" +
                "  bar.style.width='0%';" +
                "  var fd=new FormData();" +
                "  fd.append('file',file);" +
                "  var xhr=new XMLHttpRequest();" +
                "  xhr.open('POST','/upload',true);" +
                "  xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "  xhr.upload.onprogress=function(e){" +
                "    if(e.lengthComputable){" +
                "      var p=Math.round((e.loaded/e.total)*100);" +
                "      pctEl.innerText=p+'%';" +
                "      bar.style.width=p+'%';" +
                "    }" +
                "  };" +
                "  xhr.onload=function(){" +
                "    pctEl.innerText='Hoàn tất!';" +
                "    bar.style.width='100%';" +
                "    setTimeout(function(){location.reload();},600);" +
                "  };" +
                "  xhr.onerror=function(){alert('Tải lên thất bại');box.style.display='none';};" +
                "  xhr.send(fd);" +
                "}" +
                "(function startHeartbeat(){" +
                "  var overlay=document.getElementById('offlineOverlay');" +
                "  var failCount=0;" +
                "  function ping(){" +
                "    var controller=new AbortController();" +
                "    var timeoutId=setTimeout(function(){controller.abort();},2000);" +
                "    fetch('/api/ping?_='+Date.now(),{cache:'no-store',signal:controller.signal})" +
                "      .then(function(res){" +
                "        clearTimeout(timeoutId);" +
                "        if(res.ok){" +
                "          if(failCount>0&&overlay){overlay.style.display='none';}" +
                "          failCount=0;" +
                "        }else{" +
                "          handleFail();" +
                "        }" +
                "      })" +
                "      .catch(function(){" +
                "        clearTimeout(timeoutId);" +
                "        handleFail();" +
                "      });" +
                "  }" +
                "  function handleFail(){" +
                "    failCount++;" +
                "    if(failCount>=2&&overlay){overlay.style.display='flex';}" +
                "  }" +
                "  setInterval(ping,2500);" +
                "})();" +
                "</script></body></html>";

        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + htmlBytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(htmlBytes);
        out.flush();
    }

    private void sendOk(OutputStream out) throws Exception {
        String res = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
        out.write(res.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void send404(OutputStream out) throws Exception {
        String res = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(res.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String readLine(BufferedInputStream bis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = bis.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') baos.write(b);
        }
        if (baos.size() == 0 && b == -1) return null;
        return baos.toString("UTF-8");
    }

    private boolean isSafePath(File file) {
        try {
            String canonical = file.getCanonicalPath();
            return canonical.startsWith(rootDir.getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format(Locale.getDefault(), "%.1f %sB", (double) bytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
