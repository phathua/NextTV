package com.next.tv.launcher.widget;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Data model for NextTV Widget Addon.
 * Represents both built-in core widgets and external widgets.
 */
public final class WidgetAddon implements Serializable {
    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String manifestUrl;
    private final String icon;
    private final String endpoint;
    private final String template;
    private final int refreshIntervalSec;
    private final boolean isBuiltIn;
    private boolean isInstalled;
    private boolean isEnabled;

    public WidgetAddon(
            String id,
            String name,
            String version,
            String author,
            String description,
            String manifestUrl,
            String endpoint,
            String template,
            int refreshIntervalSec,
            boolean isBuiltIn,
            boolean isInstalled,
            boolean isEnabled
    ) {
        this(id, name, version, author, description, manifestUrl, endpoint, template,
                refreshIntervalSec, isBuiltIn, isInstalled, isEnabled, "");
    }

    public WidgetAddon(String id, String name, String version, String author, String description,
            String manifestUrl, String endpoint, String template, int refreshIntervalSec,
            boolean isBuiltIn, boolean isInstalled, boolean isEnabled, String icon) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
        this.version = version != null ? version : "1.0.0";
        this.author = author != null ? author : "NextTV";
        this.description = description != null ? description : "";
        this.manifestUrl = manifestUrl != null ? manifestUrl : "";
        this.icon = icon != null ? icon.trim() : "";
        this.endpoint = endpoint != null ? endpoint : "";
        this.template = template != null ? template : "hero_metric";
        this.refreshIntervalSec = refreshIntervalSec > 0 ? refreshIntervalSec : 3600;
        this.isBuiltIn = isBuiltIn;
        this.isInstalled = isInstalled;
        this.isEnabled = isEnabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getManifestUrl() {
        return manifestUrl;
    }

    public String getIcon() {
        return icon;
    }

    public String resolveIconUrl(String baseUrl) {
        if (icon.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(icon);
            if (!uri.isAbsolute()) {
                java.net.URI manifest = new java.net.URI(manifestUrl);
                if (!manifest.isAbsolute() && baseUrl != null && !baseUrl.isEmpty()) {
                    manifest = new java.net.URI(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/").resolve(manifest);
                }
                uri = manifest.resolve(uri);
            }
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    ? uri.toString() : "";
        } catch (java.net.URISyntaxException error) {
            return "";
        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getTemplate() {
        return template;
    }

    public int getRefreshIntervalSec() {
        return refreshIntervalSec;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("version", version);
            obj.put("author", author);
            obj.put("description", description);
            obj.put("manifestUrl", manifestUrl);
            obj.put("icon", icon);
            obj.put("endpoint", endpoint);
            obj.put("template", template);
            obj.put("refreshIntervalSec", refreshIntervalSec);
            obj.put("isBuiltIn", isBuiltIn);
            obj.put("isInstalled", isInstalled);
            obj.put("isEnabled", isEnabled);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static WidgetAddon fromJson(JSONObject obj) {
        if (obj == null) return null;
        return new WidgetAddon(
                obj.optString("id", ""),
                obj.optString("name", "Tiện ích"),
                obj.optString("version", "1.0.0"),
                obj.optString("author", "NextTV"),
                obj.optString("description", ""),
                obj.optString("manifestUrl", ""),
                obj.optString("endpoint", ""),
                obj.optString("template", "hero_metric"),
                obj.optInt("refreshIntervalSec", 3600),
                obj.optBoolean("isBuiltIn", false),
                obj.optBoolean("isInstalled", true),
                obj.optBoolean("isEnabled", true),
                obj.optString("icon", "")
        );
    }
}
