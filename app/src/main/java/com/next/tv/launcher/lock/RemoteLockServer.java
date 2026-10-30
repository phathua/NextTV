package com.next.tv.launcher.lock;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Lightweight local HTTP server running on port 8765 to enable remote parent control via LAN.
 */
public final class RemoteLockServer {
    private static final String TAG = "TvHome.RemoteLock";
    public static final int PORT = 8765;

    private final Context context;
    private final AppLockManager lockManager;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    public RemoteLockServer(Context context) {
        this.context = context.getApplicationContext();
        this.lockManager = AppLockManager.getInstance(this.context);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        if (!running) break;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Server socket error: " + e.getMessage());
            }
        }, "remote-lock-srv");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public synchronized void stop() {
        running = false;
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

    private void handleClient(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }

            String[] parts = line.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String uriStr = parts[1];
            Uri uri = Uri.parse("http://localhost" + uriStr);
            String path = uri.getPath();

            OutputStream out = socket.getOutputStream();

            if ("/api/status".equals(path)) {
                sendJsonResponse(out, buildStatusJson());
            } else if ("/api/toggle-kid-mode".equals(path)) {
                String pin = uri.getQueryParameter("pin");
                if (lockManager.verifyPin(pin)) {
                    boolean current = lockManager.isKidModeActive();
                    lockManager.setKidModeActive(!current);
                    sendJsonResponse(out, "{\"success\":true,\"kidMode\":" + (!current) + "}");
                } else {
                    sendJsonResponse(out, "{\"success\":false,\"error\":\"PIN không đúng\"}");
                }
            } else if ("/api/lock-app".equals(path)) {
                String pin = uri.getQueryParameter("pin");
                String pkg = uri.getQueryParameter("pkg");
                String lock = uri.getQueryParameter("lock");
                if (lockManager.verifyPin(pin)) {
                    if (pkg != null) {
                        lockManager.setAppLocked(pkg, "1".equals(lock));
                    }
                    sendJsonResponse(out, "{\"success\":true}");
                } else {
                    sendJsonResponse(out, "{\"success\":false,\"error\":\"PIN không đúng\"}");
                }
            } else if ("/api/set-pin".equals(path)) {
                String oldPin = uri.getQueryParameter("old_pin");
                String newPin = uri.getQueryParameter("new_pin");
                if (lockManager.setPin(oldPin, newPin)) {
                    sendJsonResponse(out, "{\"success\":true}");
                } else {
                    sendJsonResponse(out, "{\"success\":false,\"error\":\"PIN cũ sai hoặc PIN mới không hợp lệ\"}");
                }
            } else {
                sendHtmlResponse(out, renderDashboardHtml());
            }

            socket.close();
        } catch (Exception e) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private String buildStatusJson() {
        boolean kidMode = lockManager.isKidModeActive();
        long lockout = lockManager.getRemainingLockoutSeconds();
        Set<String> locked = lockManager.getLockedPackages();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"kidMode\":").append(kidMode)
                .append(",\"lockoutSec\":").append(lockout)
                .append(",\"lockedApps\":[");
        int count = 0;
        for (String p : locked) {
            if (count > 0) sb.append(",");
            sb.append("\"").append(p).append("\"");
            count++;
        }
        sb.append("]}");
        return sb.toString();
    }

    private void sendJsonResponse(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void sendHtmlResponse(OutputStream out, String html) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "X-Frame-Options: SAMEORIGIN\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String renderDashboardHtml() {
        boolean kidMode = lockManager.isKidModeActive();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        StringBuilder appRows = new StringBuilder();
        Set<String> lockedSet = lockManager.getLockedPackages();

        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                continue;
            }
            if (ai.packageName.equals(context.getPackageName())) continue;
            String name = pm.getApplicationLabel(ai).toString();
            boolean isLocked = lockedSet.contains(ai.packageName);
            appRows.append("<div style=\"display:flex;justify-content:space-between;align-items:center;padding:12px;background:#18181b;border:1px solid #27272a;border-radius:10px;margin-bottom:8px;\">")
                    .append("<div><div style=\"font-weight:600;color:#f4f4f5;\">").append(escapeHtml(name)).append("</div>")
                    .append("<div style=\"font-size:12px;color:#71717a;\">").append(escapeHtml(ai.packageName)).append("</div></div>")
                    .append("<div>")
                    .append("<button onclick=\"toggleLock('").append(escapeHtml(ai.packageName)).append("', ").append(isLocked ? 0 : 1).append(")\" style=\"padding:6px 14px;background:").append(isLocked ? "#3f3f46" : "#27272a").append(";color:#f4f4f5;border:1px solid #3f3f46;border-radius:8px;cursor:pointer;\">")
                    .append(isLocked ? "Mở khóa" : "Khóa app")
                    .append("</button>")
                    .append("</div></div>");
        }

        return "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Quản lý TV từ xa</title>"
                + "<style>"
                + "body{background:#09090b;color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;margin:0;padding:20px;display:flex;justify-content:center;}"
                + ".container{width:100%;max-width:480px;}"
                + ".card{background:#18181b;border:1px solid #27272a;border-radius:14px;padding:20px;margin-bottom:16px;}"
                + ".btn{display:inline-block;padding:10px 18px;border-radius:10px;font-weight:600;cursor:pointer;border:none;background:#27272a;color:#f4f4f5;}"
                + ".btn-active{background:#f4f4f5;color:#09090b;}"
                + "input{background:#09090b;border:1px solid #27272a;color:#f4f4f5;padding:8px 12px;border-radius:8px;width:100%;box-sizing:border-box;margin-top:6px;margin-bottom:12px;}"
                + "</style></head><body>"
                + "<div class=\"container\">"
                + "<h2 style=\"margin-top:0;font-size:20px;\">Quản lý từ xa</h2>"
                + "<div class=\"card\">"
                + "<div style=\"font-size:13px;color:#a1a1aa;\">Chế độ trẻ em</div>"
                + "<div style=\"display:flex;justify-content:space-between;align-items:center;margin-top:8px;\">"
                + "<div style=\"font-size:18px;font-weight:600;\">" + (kidMode ? "Đang bật" : "Đang tắt") + "</div>"
                + "<button class=\"btn " + (kidMode ? "btn-active" : "") + "\" onclick=\"toggleKidMode()\">" + (kidMode ? "Tắt chế độ" : "Bật chế độ") + "</button>"
                + "</div></div>"
                + "<div class=\"card\">"
                + "<div style=\"font-size:14px;font-weight:600;margin-bottom:12px;\">Đổi mã PIN</div>"
                + "<label style=\"font-size:12px;color:#a1a1aa;\">Mã PIN cũ (4 số)</label><input type=\"password\" id=\"oldPin\" maxlength=\"4\">"
                + "<label style=\"font-size:12px;color:#a1a1aa;\">Mã PIN mới (4 số)</label><input type=\"password\" id=\"newPin\" maxlength=\"4\">"
                + "<button class=\"btn\" onclick=\"changePin()\">Cập nhật PIN</button>"
                + "</div>"
                + "<div style=\"font-size:14px;font-weight:600;margin:16px 0 8px 4px;\">Danh sách ứng dụng</div>"
                + appRows.toString()
                + "</div>"
                + "<script>"
                + "function askPin(){return prompt('Nhập mã PIN 4 số của TV:');}"
                + "function toggleKidMode(){var p=askPin();if(!p)return;fetch('/api/toggle-kid-mode?pin='+p).then(r=>r.json()).then(d=>{if(d.success)location.reload();else alert(d.error||'Thất bại');});}"
                + "function toggleLock(pkg,lock){var p=askPin();if(!p)return;fetch('/api/lock-app?pkg='+encodeURIComponent(pkg)+'&lock='+lock+'&pin='+p).then(r=>r.json()).then(d=>{if(d.success)location.reload();else alert(d.error||'Thất bại');});}"
                + "function changePin(){var o=document.getElementById('oldPin').value;var n=document.getElementById('newPin').value;if(!o||!n){alert('Vui lòng điền đủ PIN');return;}fetch('/api/set-pin?old_pin='+o+'&new_pin='+n).then(r=>r.json()).then(d=>{if(d.success){alert('Đã đổi mã PIN thành công');location.reload();}else alert(d.error||'Thất bại');});}"
                + "</script></body></html>";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
