package com.next.tv.launcher.remote;

import com.next.tv.launcher.Config;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.LruCache;
import android.view.KeyEvent;

import com.next.tv.launcher.rec.LiveTvChannelProvider;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.clean.BackgroundAppCleaner;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Ultra-lightweight, zero-dependency Web Remote HTTP Server running on a random port (10000-65000).
 * Serves a mobile-friendly Dark Mode TV Remote interface.
 * Memory footprint strictly under 1MB.
 */
public final class WebRemoteServer {
    private static final String TAG = "TvHome.WebRemote";

    private static volatile WebRemoteServer instance;
    private final Context context;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean isRunning = false;
    private int boundPort = 0;

    // Cache app icons (max ~512KB)
    private final LruCache<String, byte[]> iconCache = new LruCache<String, byte[]>(512 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value.length;
        }
    };

    private WebRemoteServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public static WebRemoteServer getInstance(Context context) {
        if (instance == null) {
            synchronized (WebRemoteServer.class) {
                if (instance == null) {
                    instance = new WebRemoteServer(context);
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (isRunning) return;

        // Find available random port between 10000 and 65000
        Random random = new Random();
        ServerSocket socket = null;
        int port = 0;
        for (int i = 0; i < 20; i++) {
            port = 10000 + random.nextInt(55000);
            try {
                socket = new ServerSocket(port);
                break;
            } catch (Exception ignored) {}
        }

        if (socket == null) {
            try {
                socket = new ServerSocket(0); // system picks available port
                port = socket.getLocalPort();
            } catch (Exception e) {
                Log.w(TAG, "Failed to bind server socket", e);
                return;
            }
        }

        this.serverSocket = socket;
        this.boundPort = port;
        this.isRunning = true;

        serverThread = new Thread(() -> {
            Log.i(TAG, "WebRemoteServer running on port " + boundPort);
            while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client), "web-remote-client").start();
                } catch (Exception e) {
                    if (!isRunning) break;
                }
            }
        }, "web-remote-srv");
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
        boundPort = 0;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return boundPort;
    }

    public String getUrl() {
        String ip = RemoteFileServer.getIpAddress(context);
        return "http://" + ip + ":" + boundPort;
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

            // Drain headers
            String line;
            while ((line = readLine(bis)) != null && !line.isEmpty()) {}

            if ("GET".equalsIgnoreCase(method)) {
                if ("/".equals(uri) || uri.startsWith("/?")) {
                    sendHtmlRemote(out);
                } else if ("/api/ping".equals(uri) || uri.startsWith("/api/ping?")) {
                    sendOk(out);
                } else if ("/api/apps".equals(uri)) {
                    sendAppsJson(out);
                } else if ("/api/channels".equals(uri)) {
                    sendChannelsJson(out);
                } else if (uri.startsWith("/api/app-icon?pkg=")) {
                    String pkg = uri.substring(18);
                    sendAppIcon(pkg, out);
                } else {
                    send404(out);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (uri.startsWith("/api/key?code=")) {
                    String code = uri.substring(14);
                    handleKeyCommand(code);
                    sendOk(out);
                } else if (uri.startsWith("/api/launch?pkg=")) {
                    String pkg = uri.substring(16);
                    handleLaunchApp(pkg);
                    sendOk(out);
                } else if (uri.startsWith("/api/channel?id=")) {
                    String id = uri.substring(16);
                    handleLaunchChannel(id);
                    sendOk(out);
                } else {
                    send404(out);
                }
            } else {
                send404(out);
            }
        } catch (Exception e) {
            // client disconnected or closed
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void handleKeyCommand(String code) {
        if ("UP".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_DPAD_UP);
        else if ("DOWN".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_DPAD_DOWN);
        else if ("LEFT".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_DPAD_LEFT);
        else if ("RIGHT".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        else if ("OK".equalsIgnoreCase(code) || "CENTER".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_DPAD_CENTER);
        else if ("BACK".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_BACK);
        else if ("HOME".equalsIgnoreCase(code)) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);
        }
        else if ("MENU".equalsIgnoreCase(code)) injectKey(KeyEvent.KEYCODE_MENU);
        else if ("VOL_UP".equalsIgnoreCase(code)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        }
        else if ("VOL_DOWN".equalsIgnoreCase(code)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
        else if ("MUTE".equalsIgnoreCase(code)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        }
        else if ("CLEAN_RAM".equalsIgnoreCase(code)) {
            new Handler(Looper.getMainLooper()).post(() -> BackgroundAppCleaner.cleanRam(context));
        }
    }

    private void injectKey(int keyCode) {
        new Handler(Looper.getMainLooper()).post(() -> {
            MainActivity activity = MainActivity.activeInstance;
            if (activity != null && MainActivity.isResumed) {
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                activity.dispatchKeyEvent(down);
                activity.dispatchKeyEvent(up);
            } else {
                new Thread(() -> {
                    try {
                        com.next.tv.launcher.clean.LocalAdbBridge adb = com.next.tv.launcher.clean.LocalAdbBridge.getInstance(context);
                        if (adb.isConnected()) {
                            adb.executeShell("input keyevent " + keyCode);
                            return;
                        }
                    } catch (Exception ignored) {}
                    try {
                        new Instrumentation().sendKeyDownUpSync(keyCode);
                    } catch (Exception ignored) {}
                }).start();
            }
        });
    }

    private void handleLaunchApp(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Exception ignored) {}
    }

    private void handleLaunchChannel(String id) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("xemtv://tv/channel/" + id));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void sendAppIcon(String pkg, OutputStream out) throws Exception {
        if (pkg == null || pkg.isEmpty()) {
            send404(out);
            return;
        }

        byte[] cached = iconCache.get(pkg);
        if (cached != null) {
            String header = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: " + cached.length + "\r\nCache-Control: public, max-age=86400\r\nConnection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(cached);
            out.flush();
            return;
        }

        PackageManager pm = context.getPackageManager();
        Drawable icon = null;
        try {
            icon = pm.getApplicationBanner(pkg);
            if (icon == null) {
                icon = pm.getApplicationIcon(pkg);
            }
        } catch (Exception ignored) {}

        if (icon == null) {
            send404(out);
            return;
        }

        int size = 64;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos);
        byte[] pngBytes = baos.toByteArray();
        bitmap.recycle();

        iconCache.put(pkg, pngBytes);

        String header = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: " + pngBytes.length + "\r\nCache-Control: public, max-age=86400\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(pngBytes);
        out.flush();
    }

    private void sendAppsJson(OutputStream out) throws Exception {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        if (apps == null || apps.isEmpty()) {
            Intent fallback = new Intent(Intent.ACTION_MAIN, null);
            fallback.addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(fallback, 0);
        }

        StringBuilder sb = new StringBuilder("[");
        if (apps != null) {
            boolean first = true;
            for (ResolveInfo ri : apps) {
                if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(context.getPackageName())) continue;
                String label = ri.loadLabel(pm).toString().replace("\"", "\\\"");
                if (!first) sb.append(",");
                sb.append("{\"pkg\":\"").append(pkg).append("\",\"name\":\"").append(label).append("\"}");
                first = false;
            }
        }
        sb.append("]");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void sendChannelsJson(OutputStream out) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (LiveTvChannelProvider.ChannelDef def : LiveTvChannelProvider.getChannels()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":\"").append(def.id)
              .append("\",\"title\":\"").append(def.title)
              .append("\",\"logo\":\"").append(def.posterUrl != null ? def.posterUrl : "")
              .append("\",\"sub\":\"").append(def.subtitle != null ? def.subtitle.replace("\"", "\\\"") : "")
              .append("\",\"cat\":\"").append(def.category != null ? def.category : "")
              .append("\"}");
            first = false;
        }
        sb.append("]");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private void sendOk(OutputStream out) throws Exception {
        String res = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
        out.write(res.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void send404(OutputStream out) throws Exception {
        String res = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(res.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendHtmlRemote(OutputStream out) throws Exception {
        String html = "<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no,viewport-fit=cover'>" +
                "<title>Điều khiển TV</title><style>" +
                "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}" +
                "html,body{margin:0;padding:0;width:100%;min-height:100vh;background:#09090b;color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;" +
                "touch-action:manipulation;-webkit-touch-callout:none;-webkit-user-select:none;user-select:none;overscroll-behavior:none}" +
                "button{touch-action:manipulation;font-family:inherit;cursor:pointer;outline:none;border:none}" +
                ".app-layout{max-width:1080px;margin:0 auto;padding:16px;display:flex;flex-direction:column;gap:20px;overflow-x:hidden}" +
                "@media(min-width:768px){.app-layout{flex-direction:row;align-items:flex-start;padding:28px 24px;gap:32px}" +
                ".remote-col{width:340px;flex-shrink:0;position:sticky;top:24px}" +
                ".content-col{flex:1;min-width:0}}" +
                "@media(max-width:767px){.remote-col{width:100%}" +
                ".content-col{width:100%}}" +
                ".header{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}" +
                ".brand{font-size:16px;font-weight:700;letter-spacing:0.5px;color:#fff;display:flex;align-items:center;gap:8px}" +
                ".badge-online{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:#10b981;background:#052e16;padding:4px 10px;border-radius:999px;border:1px solid #065f46}" +
                ".badge-dot{width:7px;height:7px;background:#10b981;border-radius:50%}" +
                ".dpad-box{width:220px;height:220px;margin:8px auto 16px;position:relative;background:#18181b;border-radius:50%;border:2px solid #27272a;box-shadow:0 12px 32px rgba(0,0,0,0.6)}" +
                ".dpad-btn{position:absolute;background:#27272a;color:#f4f4f5;border:none;border-radius:14px;display:flex;align-items:center;justify-content:center;transition:background .08s,transform .08s}" +
                ".dpad-btn:active{background:#3f3f46;transform:scale(0.94)}" +
                ".dpad-btn svg{width:22px;height:22px;stroke:currentColor;stroke-width:2.5;fill:none;stroke-linecap:round;stroke-linejoin:round}" +
                ".btn-up{top:10px;left:70px;width:80px;height:50px}" +
                ".btn-down{bottom:10px;left:70px;width:80px;height:50px}" +
                ".btn-left{top:70px;left:10px;width:50px;height:80px}" +
                ".btn-right{top:70px;right:10px;width:50px;height:80px}" +
                ".btn-ok{top:70px;left:70px;width:80px;height:80px;border-radius:50%;background:#10b981;font-weight:700;font-size:17px;color:#fff;box-shadow:0 4px 16px rgba(16,185,129,0.35)}" +
                ".btn-ok:active{background:#059669;transform:scale(0.92)}" +
                ".btn-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:10px}" +
                ".fn-btn{height:44px;background:#18181b;border:1px solid #27272a;border-radius:10px;color:#e4e4e7;font-size:12.5px;font-weight:500;display:flex;align-items:center;justify-content:center;gap:6px;transition:background .08s,transform .08s}" +
                ".fn-btn:active{background:#27272a;transform:scale(0.96)}" +
                ".fn-btn svg{width:16px;height:16px;stroke:currentColor;stroke-width:2;fill:none;stroke-linecap:round;stroke-linejoin:round}" +
                ".btn-clean{grid-column:span 3;color:#34d399;background:#064e3b22;border-color:#065f46}" +
                ".btn-clean:active{background:#064e3b44}" +
                ".tabs-wrap{display:flex;gap:6px;background:#18181b;padding:4px;border-radius:10px;border:1px solid #27272a;margin-bottom:12px}" +
                ".tab-btn{flex:1;padding:7px;border-radius:7px;font-size:13px;font-weight:600;color:#a1a1aa;background:transparent;text-align:center;transition:all .15s}" +
                ".tab-btn.active{background:#27272a;color:#fff}" +
                ".search-input{width:100%;height:38px;background:#18181b;border:1px solid #27272a;border-radius:8px;padding:0 12px;color:#fff;font-size:13px;outline:none;margin-bottom:12px}" +
                ".search-input:focus{border-color:#10b981}" +
                ".channel-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(84px,1fr));gap:8px;width:100%;padding:1px}" +
                ".channel-card{background:#18181b;border:1px solid #27272a;border-radius:10px;padding:8px 6px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;cursor:pointer;transition:border-color .12s,background .12s;aspect-ratio:1/1}" +
                ".channel-card:active{background:#27272a;border-color:#10b981}" +
                ".channel-logo-box{width:46px;height:46px;background:transparent;display:flex;align-items:center;justify-content:center;overflow:hidden}" +
                ".channel-logo{width:100%;height:100%;object-fit:contain}" +
                ".channel-title{font-size:11px;font-weight:600;color:#e4e4e7;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%}" +
                ".app-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:8px;width:100%;padding:1px}" +
                ".app-card{background:#18181b;border:1px solid #27272a;border-radius:10px;padding:8px 10px;color:#fff;font-size:12.5px;font-weight:500;display:flex;align-items:center;gap:8px;cursor:pointer;transition:border-color .12s,background .12s;min-width:0}" +
                ".app-card:active{background:#27272a;border-color:#10b981}" +
                ".app-card-icon{width:32px;height:32px;border-radius:6px;object-fit:contain;background:#27272a;flex-shrink:0}" +
                ".app-card-name{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-align:left}" +
                ".offline-overlay{display:none;position:fixed;inset:0;background:rgba(9,9,11,0.88);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);z-index:9999;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:24px}" +
                ".offline-card{background:#18181b;border:1px solid #ef444455;border-radius:20px;padding:32px 24px;max-width:360px;width:100%;box-shadow:0 20px 40px rgba(0,0,0,0.7);display:flex;flex-direction:column;align-items:center}" +
                ".offline-icon{color:#ef4444;margin-bottom:16px}" +
                ".offline-title{font-size:17px;font-weight:700;color:#fff;margin:0 0 8px}" +
                ".offline-desc{font-size:13px;color:#a1a1aa;margin:0 0 20px;line-height:1.5}" +
                ".offline-ping-tag{display:inline-flex;align-items:center;gap:8px;font-size:12px;color:#f43f5e;background:#e11d4815;padding:6px 14px;border-radius:999px;border:1px solid #e11d4833}" +
                ".offline-dot{width:8px;height:8px;background:#ef4444;border-radius:50%;animation:pulseDot 1.2s infinite ease-in-out}" +
                "@keyframes pulseDot{0%,100%{opacity:1;transform:scale(1)}50%{opacity:0.4;transform:scale(0.85)}}" +
                "</style></head><body>" +
                "<div id='offlineOverlay' class='offline-overlay'>" +
                "<div class='offline-card'>" +
                "<div class='offline-icon'><svg width='54' height='54' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><g opacity='0.4'><path d='M6.50001 18L6.50001 17.9105C6.49991 17.0449 6.49981 16.2512 6.58661 15.6056C6.6822 14.8946 6.90709 14.1432 7.52514 13.5251C8.14319 12.9071 8.89464 12.6822 9.6056 12.5866C10.2512 12.4998 11.0449 12.4999 11.9105 12.5H12.0895C12.9551 12.4999 13.7488 12.4998 14.3944 12.5866C15.1054 12.6822 15.8568 12.9071 16.4749 13.5251C17.0929 14.1432 17.3178 14.8946 17.4134 15.6056C17.4989 16.2417 17.5001 17.0215 17.5 17.8722C20.0726 17.3221 22 15.0599 22 12.3529C22 9.88113 20.393 7.78024 18.1551 7.01498C17.8371 4.19371 15.4159 2 12.4762 2C9.32028 2 6.7619 4.52827 6.7619 7.64706C6.7619 8.33687 6.88706 8.9978 7.11616 9.60887C6.8475 9.55673 6.56983 9.52941 6.28571 9.52941C3.91878 9.52941 2 11.4256 2 13.7647C2 16.1038 3.91878 18 6.28571 18L6.50001 18Z' fill='currentColor'/></g><path fill-rule='evenodd' clip-rule='evenodd' d='M12 22C10.1144 22 9.17157 22 8.58579 21.4142C8 20.8284 8 19.8856 8 18C8 16.1144 8 15.1716 8.58579 14.5858C9.17157 14 10.1144 14 12 14C13.8856 14 14.8284 14 15.4142 14.5858C16 15.1716 16 16.1144 16 18C16 19.8856 16 20.8284 15.4142 21.4142C14.8284 22 13.8856 22 12 22ZM10.6936 15.7508C10.4333 15.4905 10.0112 15.4905 9.75082 15.7508C9.49047 16.0112 9.49047 16.4333 9.75082 16.6936L11.0572 18L9.75082 19.3064C9.49047 19.5667 9.49047 19.9888 9.75082 20.2492C10.0112 20.5095 10.4333 20.5095 10.6936 20.2492L12 18.9428L13.3064 20.2492C13.5667 20.5095 13.9888 20.5095 14.2492 20.2492C14.5095 19.9888 14.5095 19.5667 14.2492 19.3064L12.9428 18L14.2492 16.6936C14.5095 16.4333 14.5095 16.0112 14.2492 15.7508C13.9888 15.4905 13.5667 15.4905 13.3064 15.7508L12 17.0572L10.6936 15.7508Z' fill='currentColor'/></svg></div>" +
                "<h3 class='offline-title'>MẤT KẾT NỐI VỚI TV</h3>" +
                "<p class='offline-desc'>Không thể kết nối đến điều khiển TV. Vui lòng kiểm tra lại cùng mạng Wi-Fi.</p>" +
                "<div class='offline-ping-tag'><div class='offline-dot'></div>Đang tự động kết nối lại...</div>" +
                "</div></div>" +
                "<div class='app-layout'>" +
                "<div class='remote-col'>" +
                "<div class='header'><div class='brand'>ĐIỀU KHIỂN TV</div><div class='badge-online'><div class='badge-dot'></div>Trực tuyến</div></div>" +
                "<div class='dpad-box'>" +
                "<button class='dpad-btn btn-up' onclick=\"k('UP')\"><svg viewBox='0 0 24 24'><path d='m18 15-6-6-6 6'/></svg></button>" +
                "<button class='dpad-btn btn-left' onclick=\"k('LEFT')\"><svg viewBox='0 0 24 24'><path d='m15 18-6-6 6-6'/></svg></button>" +
                "<button class='dpad-btn btn-ok' onclick=\"k('OK')\">OK</button>" +
                "<button class='dpad-btn btn-right' onclick=\"k('RIGHT')\"><svg viewBox='0 0 24 24'><path d='m9 6 6 6-6 6'/></svg></button>" +
                "<button class='dpad-btn btn-down' onclick=\"k('DOWN')\"><svg viewBox='0 0 24 24'><path d='m6 9 6 6 6-6'/></svg></button>" +
                "</div>" +
                "<div class='btn-grid'>" +
                "<button class='fn-btn' onclick=\"k('BACK')\"><svg viewBox='0 0 24 24'><path d='M9 14 4 9l5-5'/><path d='M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5v0a5.5 5.5 0 0 1-5.5 5.5H11'/></svg><span>Quay lại</span></button>" +
                "<button class='fn-btn' onclick=\"k('HOME')\"><svg viewBox='0 0 24 24'><path d='m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/><polyline points='9 22 9 12 15 12 15 22'/></svg><span>Trang chủ</span></button>" +
                "<button class='fn-btn' onclick=\"k('MENU')\"><svg viewBox='0 0 24 24'><line x1='4' x2='20' y1='12' y2='12'/><line x1='4' x2='20' y1='6' y2='6'/><line x1='4' x2='20' y1='18' y2='18'/></svg><span>Tùy chọn</span></button>" +
                "</div>" +
                "<div class='btn-grid'>" +
                "<button class='fn-btn' onclick=\"k('VOL_DOWN')\"><svg viewBox='0 0 24 24'><polygon points='11 5 6 9 2 9 2 15 6 15 11 19 11 5'/><line x1='15' x2='21' y1='12' y2='12'/></svg><span>Giảm âm</span></button>" +
                "<button class='fn-btn' onclick=\"k('MUTE')\"><svg viewBox='0 0 24 24'><polygon points='11 5 6 9 2 9 2 15 6 15 11 19 11 5'/><line x1='23' x2='17' y1='9' y2='15'/><line x1='17' x2='23' y1='9' y2='15'/></svg><span>Tắt tiếng</span></button>" +
                "<button class='fn-btn' onclick=\"k('VOL_UP')\"><svg viewBox='0 0 24 24'><polygon points='11 5 6 9 2 9 2 15 6 15 11 19 11 5'/><line x1='19' x2='19' y1='9' y2='15'/><line x1='16' x2='22' y1='12' y2='12'/></svg><span>Tăng âm</span></button>" +
                "<button class='fn-btn btn-clean' onclick=\"k('CLEAN_RAM')\"><svg viewBox='0 0 24 24'><path d='m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z'/><path d='m9 12-4.5 4.5a2.12 2.12 0 0 0 3 3L12 15'/><path d='M4.5 16.5 2 22l5.5-2.5'/></svg><span>Dọn dẹp RAM</span></button>" +
                "</div>" +
                "</div>" +
                "<div class='content-col'>" +
                "<div class='tabs-wrap'>" +
                "<button class='tab-btn active' id='tabChannels' onclick=\"switchTab('channels')\">Kênh truyền hình</button>" +
                "<button class='tab-btn' id='tabApps' onclick=\"switchTab('apps')\">Ứng dụng TV</button>" +
                "</div>" +
                "<input type='text' class='search-input' id='searchBox' placeholder='Tìm kênh hoặc ứng dụng...' oninput='filterItems()'>" +
                "<div class='channel-grid' id='channelGrid'></div>" +
                "<div class='app-grid' id='appGrid' style='display:none'></div>" +
                "</div>" +
                "</div><script>" +
                "let channels=[],apps=[],curTab='channels';" +
                "function k(c){if(navigator.vibrate)navigator.vibrate(25);fetch('/api/key?code='+c,{method:'POST'}).catch(()=>{})}" +
                "function launch(p){if(navigator.vibrate)navigator.vibrate(25);fetch('/api/launch?pkg='+encodeURIComponent(p),{method:'POST'}).catch(()=>{})}" +
                "function tune(i){if(navigator.vibrate)navigator.vibrate(25);fetch('/api/channel?id='+encodeURIComponent(i),{method:'POST'}).catch(()=>{})}" +
                "function switchTab(t){curTab=t;" +
                "document.getElementById('tabChannels').className='tab-btn '+(t==='channels'?'active':'');" +
                "document.getElementById('tabApps').className='tab-btn '+(t==='apps'?'active':'');" +
                "document.getElementById('channelGrid').style.display=t==='channels'?'grid':'none';" +
                "document.getElementById('appGrid').style.display=t==='apps'?'grid':'none';" +
                "filterItems();}" +
                "function renderChannels(list){" +
                "const g=document.getElementById('channelGrid');g.innerHTML='';" +
                "list.forEach(c=>{" +
                "const d=document.createElement('div');d.className='channel-card';" +
                "d.title=c.title||'';" +
                "d.onclick=()=>tune(c.id);" +
                "d.innerHTML=\"<div class='channel-logo-box'><img class='channel-logo' src='\"+(c.logo||\"\")+\"' loading='lazy' onerror=\\\"this.parentElement.style.opacity='0.25'\\\"></div>\"+" +
                "\"<div class='channel-title'>\"+(c.title||\"\")+\"</div>\";" +
                "g.appendChild(d);});}" +
                "function renderApps(list){" +
                "const g=document.getElementById('appGrid');g.innerHTML='';" +
                "list.forEach(a=>{" +
                "const d=document.createElement('div');d.className='app-card';" +
                "d.onclick=()=>launch(a.pkg);" +
                "d.innerHTML=\"<img class='app-card-icon' src='/api/app-icon?pkg=\"+encodeURIComponent(a.pkg)+\"' loading='lazy' onerror=\\\"this.style.opacity='0.2'\\\">\"+" +
                "\"<span class='app-card-name'>\"+a.name+\"</span>\";" +
                "g.appendChild(d);});}" +
                "function filterItems(){" +
                "const q=(document.getElementById('searchBox').value||'').trim().toLowerCase();" +
                "if(curTab==='channels'){" +
                "const filtered=channels.filter(c=>(c.title&&c.title.toLowerCase().includes(q))||(c.sub&&c.sub.toLowerCase().includes(q))||(c.cat&&c.cat.toLowerCase().includes(q)));" +
                "renderChannels(filtered);" +
                "}else{" +
                "const filtered=apps.filter(a=>(a.name&&a.name.toLowerCase().includes(q))||(a.pkg&&a.pkg.toLowerCase().includes(q)));" +
                "renderApps(filtered);}}" +
                "fetch('/api/channels').then(r=>r.json()).then(d=>{channels=d;renderChannels(channels);}).catch(()=>{});" +
                "fetch('/api/apps').then(r=>r.json()).then(d=>{apps=d;renderApps(apps);}).catch(()=>{});" +
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

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
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
}
