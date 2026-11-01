package com.next.tv.launcher.widget;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetAddonTest {
    private WidgetAddon addon(String manifest, String icon) {
        return new WidgetAddon("sample", "Mẫu", "1", "Tác giả", "", manifest, "", "hero_metric",
                300, false, true, false, icon);
    }

    @Test public void oldManifestKeepsDefaultsWithoutIcon() throws Exception {
        WidgetAddon old = WidgetAddon.fromJson(new JSONObject("{\"id\":\"old\",\"name\":\"Cũ\"}"));
        assertEquals("", old.getIcon());
        assertEquals("", old.resolveIconUrl("https://example.com"));
        assertTrue(old.isEnabled());
    }

    @Test public void persistedIconAndStateSurviveRoundTrip() {
        WidgetAddon restored = WidgetAddon.fromJson(addon("https://example.com/a/manifest.json", "images/icon.png").toJson());
        assertEquals("images/icon.png", restored.getIcon());
        assertFalse(restored.isEnabled());
        assertEquals("https://example.com/a/images/icon.png", restored.resolveIconUrl(""));
    }

    @Test public void absoluteIconDoesNotDependOnManifest() {
        assertEquals("https://cdn.example.com/icon.png", addon("", "https://cdn.example.com/icon.png").resolveIconUrl(""));
    }

    @Test public void relativeManifestUsesStoreHost() {
        assertEquals("https://store.example.com/addons/gold/icon.png",
                addon("/addons/gold/manifest.json", "icon.png").resolveIconUrl("https://store.example.com"));
    }

    @Test public void rootRelativeIconUsesManifestHost() {
        assertEquals("https://example.com/icons/sample.png",
                addon("https://example.com/a/manifest.json", "/icons/sample.png").resolveIconUrl(""));
    }

    @Test public void unsafeMalformedOrUnresolvableIconsUseFallback() {
        assertEquals("", addon("", "file:///private/icon.png").resolveIconUrl(""));
        assertEquals("", addon("", "bad icon.png").resolveIconUrl(""));
        assertEquals("", addon("", "https:bad").resolveIconUrl(""));
        assertEquals("", addon("", "relative.png").resolveIconUrl(""));
    }
}
