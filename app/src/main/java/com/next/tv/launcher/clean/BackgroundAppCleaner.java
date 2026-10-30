package com.next.tv.launcher.clean;

import android.content.Context;

/**
 * Lightweight RAM & background process cleaner for constrained Android TV hardware.
 * Delegates to SmartRamGovernor for 4-stage smart cleaning.
 */
public final class BackgroundAppCleaner {

    public static void cleanRam(Context context) {
        cleanRam(context, null);
    }

    public static void cleanRam(Context context, SmartRamGovernor.CleanCallback callback) {
        if (context == null) return;
        SmartRamGovernor.cleanRamSmartAsync(context, true, false, callback);
    }
}
