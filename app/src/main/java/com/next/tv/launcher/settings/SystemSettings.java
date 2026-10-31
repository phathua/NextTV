package com.next.tv.launcher.settings;
import com.next.tv.launcher.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/**
 * The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 * from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS).
 */
public final class SystemSettings {
    private SystemSettings() {}

    public static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
        // treat it as missing so the vendor fallback runs.
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;

        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return standard;
    }

    /**
     * Resolves and launches the best available Bluetooth / Accessories panel for Android TV.
     * Tiers: Slices Drawer (with extra slice_uri) -> CONNECTED_DEVICES -> CONNECT_INPUT -> Vendor Accessories -> Mobile BT -> General Settings.
     */
    public static void openBluetooth(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent[] candidates = new Intent[]{
            // Tier 1 & 2: Slices drawer (opens the full list of paired accessories)
            new Intent("android.settings.SLICE_SETTINGS")
                .setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.slice.SliceActivity"))
                .setData(Uri.parse("content://com.android.tv.settings.accessories.sliceprovider/general"))
                .putExtra("slice_uri", "content://com.android.tv.settings.accessories.sliceprovider/general")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),

            new Intent("android.settings.SLICE_SETTINGS")
                .setComponent(new ComponentName("com.google.android.tv.settings", "com.google.android.tv.settings.slice.SliceActivity"))
                .setData(Uri.parse("content://com.google.android.tv.settings.accessories.sliceprovider/general"))
                .putExtra("slice_uri", "content://com.google.android.tv.settings.accessories.sliceprovider/general")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),

            // Tier 3: Connected devices settings
            new Intent("com.android.tv.settings.CONNECTED_DEVICES")
                .setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")),
            new Intent("com.android.tv.settings.CONNECTED_DEVICES")
                .setComponent(new ComponentName("com.google.android.tv.settings", "com.google.android.tv.settings.MainSettings")),

            // Tier 4: Android TV standard CONNECT_INPUT
            new Intent("com.google.android.intent.action.CONNECT_INPUT"),

            // Tier 5: Direct accessories components
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity")),
            new Intent().setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.BluetoothDevicePickerActivity")),
            new Intent().setComponent(new ComponentName("com.google.android.tv.settings", "com.google.android.tv.settings.accessories.AddAccessoryActivity")),
            new Intent().setComponent(new ComponentName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.accessories.AddAccessoryActivity")),
            new Intent().setComponent(new ComponentName("com.amazon.tv.settings", "com.amazon.tv.settings.accessories.AddAccessoryActivity")),
            new Intent().setComponent(new ComponentName("com.amazon.device.bluetooth", "com.amazon.device.bluetooth.DevicePickerActivity")),
            new Intent().setComponent(new ComponentName("com.tcl.tv.settings", "com.tcl.tv.settings.accessories.AddAccessoryActivity")),

            // Tier 6: Mobile / tablet standard BT
            new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            new Intent("android.bluetooth.devicepicker.action.LAUNCH"),

            // Tier 7: General TV settings fallback
            intent(context, Settings.ACTION_SETTINGS)
        };

        for (Intent candidate : candidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (candidate.getComponent() != null) {
                    try {
                        pm.getPackageInfo(candidate.getComponent().getPackageName(), 0);
                        context.startActivity(candidate);
                        return;
                    } catch (PackageManager.NameNotFoundException ignored) {
                        continue;
                    }
                } else if (candidate.resolveActivity(pm) != null) {
                    context.startActivity(candidate);
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Final safety net: general TV settings
        try {
            Intent fallback = intent(context, Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        } catch (Exception e) {
            Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
