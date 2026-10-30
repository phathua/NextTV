package com.next.tv.launcher.search.assistant;

import android.content.Context;
import com.next.tv.launcher.widget.WidgetAddon;
import com.next.tv.launcher.widget.WidgetAddonManager;

import java.util.List;

/**
 * Gatekeeper checking addon requirements before fulfilling smart information requests.
 * Uses native WidgetAddonManager ecosystem with 1-click in-place install/enable logic.
 */
public final class WidgetAddonGatekeeper {

    public static final int STATUS_READY = 1;
    public static final int STATUS_DISABLED = 2;
    public static final int STATUS_NOT_INSTALLED = 3;

    public static final class RequirementResult {
        public final int status;
        public final String addonId;
        public final String displayName;

        public RequirementResult(int status, String addonId, String displayName) {
            this.status = status;
            this.addonId = addonId;
            this.displayName = displayName;
        }

        public boolean isReady() {
            return status == STATUS_READY;
        }
    }

    public interface InstallResultCallback {
        void onResult(boolean success, String message);
    }

    private final Context context;
    private final WidgetAddonManager addonManager;

    public WidgetAddonGatekeeper(Context context) {
        this.context = context.getApplicationContext();
        this.addonManager = WidgetAddonManager.getInstance(this.context);
    }

    /**
     * Checks if the required widget addon for the given intent is ready.
     */
    public RequirementResult checkRequirement(int intentType) {
        String addonId;
        String displayName;

        switch (intentType) {
            case TvAssistantDispatcher.INTENT_WEATHER:
                addonId = WidgetAddonManager.ID_WEATHER;
                displayName = "Thời tiết";
                break;
            case TvAssistantDispatcher.INTENT_GOLD:
                addonId = WidgetAddonManager.ID_GOLD;
                displayName = "Giá vàng";
                break;
            case TvAssistantDispatcher.INTENT_NEWS:
                addonId = WidgetAddonManager.ID_NEWS;
                displayName = "Tin tức nóng";
                break;
            case TvAssistantDispatcher.INTENT_SPORTS:
                addonId = WidgetAddonManager.ID_SPORTS;
                displayName = "Tỉ số thể thao";
                break;
            default:
                return new RequirementResult(STATUS_READY, "", "");
        }

        boolean installed = addonManager.isInstalled(addonId);
        if (!installed) {
            return new RequirementResult(STATUS_NOT_INSTALLED, addonId, displayName);
        }

        boolean enabled = addonManager.isAddonEnabled(addonId);
        if (!enabled) {
            return new RequirementResult(STATUS_DISABLED, addonId, displayName);
        }

        return new RequirementResult(STATUS_READY, addonId, displayName);
    }

    /**
     * Installs or enables addon in-place (1-click) without showing additional sub-dialogs,
     * strictly preventing BadTokenException when executing inside TYPE_APPLICATION_OVERLAY windows.
     */
    public void installOrEnableAddon(String addonId, InstallResultCallback callback) {
        if (addonId == null || addonId.isEmpty()) {
            if (callback != null) callback.onResult(false, "Mã tiện ích không hợp lệ");
            return;
        }

        if (addonManager.isInstalled(addonId)) {
            // Already installed, simply enable
            addonManager.toggleAddon(addonId, true);
            if (callback != null) callback.onResult(true, "Đã bật tiện ích");
            return;
        }

        // Find addon in default catalog or construct addon definition
        List<WidgetAddon> defaultCatalog = addonManager.buildDefaultStoreCatalog();
        WidgetAddon targetAddon = null;
        for (WidgetAddon a : defaultCatalog) {
            if (a.getId().equalsIgnoreCase(addonId)) {
                targetAddon = a;
                break;
            }
        }

        if (targetAddon == null) {
            String name = "Tiện ích mở rộng";
            if (WidgetAddonManager.ID_WEATHER.equalsIgnoreCase(addonId)) name = "Thời tiết";
            else if (WidgetAddonManager.ID_GOLD.equalsIgnoreCase(addonId)) name = "Giá vàng";
            else if (WidgetAddonManager.ID_NEWS.equalsIgnoreCase(addonId)) name = "Tin tức nóng";
            else if (WidgetAddonManager.ID_SPORTS.equalsIgnoreCase(addonId)) name = "Tỉ số thể thao";

            targetAddon = new WidgetAddon(addonId, name, "1.0.0", "NextTV Labs", "Tiện ích cho NextTV", "", "", "hero_metric", 3600, false, true, true);
        }

        addonManager.installFromStore(targetAddon, (success, message, installed) -> {
            if (callback != null) {
                callback.onResult(success, message);
            }
        });
    }
}
