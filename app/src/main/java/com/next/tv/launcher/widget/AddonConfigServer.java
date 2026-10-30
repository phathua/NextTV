package com.next.tv.launcher.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Embedded local HTTP server providing mobile web configuration and remote addon management.
 */
public final class AddonConfigServer extends NanoHTTPD {
    private static final String TAG = "NextTV.AddonServer";
    public static final int DEFAULT_PORT = 8888;

    public interface OnServerEventListener {
        void onServerActionCompleted(String action);
    }

    private final Context context;
    private final WidgetAddonManager addonManager;
    private final Handler mainHandler;
    private OnServerEventListener eventListener;
    private List<WidgetAddon> cachedStoreCatalog = new ArrayList<>();

    public AddonConfigServer(Context context) {
        this(context, DEFAULT_PORT);
    }

    public AddonConfigServer(Context context, int port) {
        super(port);
        this.context = context.getApplicationContext();
        this.addonManager = WidgetAddonManager.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setEventListener(OnServerEventListener listener) {
        this.eventListener = listener;
    }

    public void startSafe() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.i(TAG, "AddonConfigServer started on port " + getListeningPort());
            // Pre-warm store catalog
            addonManager.fetchStoreCatalog((catalog, success) -> {
                if (success && catalog != null) {
                    cachedStoreCatalog = catalog;
                }
            });
        } catch (IOException e) {
            Log.w(TAG, "Failed starting server on default port, retrying: " + e.getMessage());
        }
    }

    public void stopSafe() {
        try {
            stop();
            Log.i(TAG, "AddonConfigServer stopped");
        } catch (Throwable ignored) {}
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method)) {
            if ("/".equals(uri) || "/index.html".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", AddonWebPage.getHtml());
            } else if ("/api/state".equals(uri)) {
                return serveState();
            }
        } else if (Method.POST.equals(method)) {
            Map<String, String> files = new HashMap<>();
            try {
                session.parseBody(files);
                String postData = files.get("postData");
                JSONObject body = (postData != null && !postData.isEmpty()) ? new JSONObject(postData) : new JSONObject();

                if ("/api/addons/toggle".equals(uri)) {
                    String id = body.optString("id", "");
                    boolean enabled = body.optBoolean("enabled", true);
                    if (!id.isEmpty()) {
                        addonManager.toggleAddon(id, enabled);
                        notifyAction("toggle");
                        return jsonResponse("{\"success\":true}");
                    }
                } else if ("/api/addons/reorder".equals(uri)) {
                    JSONArray arr = body.optJSONArray("order");
                    if (arr != null) {
                        List<WidgetAddon> current = addonManager.getInstalledAddons();
                        Map<String, WidgetAddon> map = new HashMap<>();
                        for (WidgetAddon a : current) map.put(a.getId(), a);

                        List<WidgetAddon> reordered = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            String id = arr.optString(i, "");
                            if (map.containsKey(id)) {
                                reordered.add(map.remove(id));
                            }
                        }
                        reordered.addAll(map.values());
                        addonManager.saveOrderFromList(reordered);
                        notifyAction("reorder");
                        return jsonResponse("{\"success\":true}");
                    }
                } else if ("/api/addons/uninstall".equals(uri)) {
                    String id = body.optString("id", "");
                    if (!id.isEmpty()) {
                        boolean ok = addonManager.uninstallAddon(id);
                        notifyAction("uninstall");
                        return jsonResponse("{\"success\":" + ok + "}");
                    }
                } else if ("/api/addons/install".equals(uri)) {
                    WidgetAddon addon = WidgetAddon.fromJson(body);
                    if (addon != null) {
                        addonManager.installFromStore(addon, (success, msg, a) -> notifyAction("install"));
                        return jsonResponse("{\"success\":true}");
                    }
                } else if ("/api/addons/url".equals(uri)) {
                    String url = body.optString("url", "");
                    if (!url.isEmpty()) {
                        final boolean[] result = new boolean[]{false};
                        final String[] message = new String[]{"Đang xử lý"};
                        final Object lock = new Object();

                        addonManager.installFromUrl(url, (success, msg, addon) -> {
                            synchronized (lock) {
                                result[0] = success;
                                message[0] = msg;
                                lock.notifyAll();
                            }
                            if (success) notifyAction("install_url");
                        });

                        synchronized (lock) {
                            try {
                                lock.wait(5000);
                            } catch (InterruptedException ignored) {}
                        }

                        JSONObject res = new JSONObject();
                        res.put("success", result[0]);
                        res.put("message", message[0]);
                        return jsonResponse(res.toString());
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Error processing POST " + uri + ": " + e.getMessage());
                return jsonResponse("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
    }

    private Response serveState() {
        try {
            JSONObject root = new JSONObject();

            JSONArray installedArr = new JSONArray();
            for (WidgetAddon a : addonManager.getInstalledAddons()) {
                installedArr.put(a.toJson());
            }
            root.put("installed", installedArr);

            JSONArray storeArr = new JSONArray();
            for (WidgetAddon a : cachedStoreCatalog) {
                storeArr.put(a.toJson());
            }
            root.put("store", storeArr);

            return jsonResponse(root.toString());
        } catch (Exception e) {
            return jsonResponse("{\"installed\":[],\"store\":[]}");
        }
    }

    private Response jsonResponse(String json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json);
    }

    private void notifyAction(String action) {
        mainHandler.post(() -> {
            if (eventListener != null) {
                try {
                    eventListener.onServerActionCompleted(action);
                } catch (Throwable ignored) {}
            }
        });
    }
}
