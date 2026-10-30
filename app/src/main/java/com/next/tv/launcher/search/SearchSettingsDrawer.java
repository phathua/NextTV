package com.next.tv.launcher.search;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;

public final class SearchSettingsDrawer {
    public static final String PREFS_NAME = "global_search_prefs";
    public static final String KEY_MIC_OVERRIDE = "mic_button_override_enabled";
    public static final String KEY_NUVIO_ENABLED = "search_nuvio_enabled";
    public static final String KEY_STREMIO_ENABLED = "search_stremio_enabled";
    public static final String KEY_YOUTUBE_ENABLED = "search_youtube_enabled";
    public static final String KEY_CLOUDSTREAM_ENABLED = "search_cloudstream_enabled";
    public static final String KEY_BRAVE_ENABLED = "search_brave_enabled";

    private final Activity activity;
    private final SharedPreferences prefs;

    private View drawerView;
    private boolean isShowing = false;

    private View rowMic;
    private View headerPermissions;
    private View rowPermAudio;
    private View rowPermOverlay;
    private View rowPermAccess;
    private TextView txtBadgeAudio;
    private TextView txtBadgeOverlay;
    private TextView txtBadgeAccess;
    private TextView btnAutoGrant;

    private View rowNuvio;
    private View rowStremio;
    private View rowYoutube;
    private View rowCloudstream;
    private View rowBraveNews;

    public SearchSettingsDrawer(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        if (isShowing) return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_search_settings, null);

        bindViews(drawerView);

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        isShowing = true;

        View drawerContent = drawerView.findViewById(R.id.searchSettingsContent);
        if (drawerContent != null) {
            drawerContent.setTranslationX(400f);
            drawerContent.animate().translationX(0f).setDuration(220).start();
        }

        View rowMic = drawerView.findViewById(R.id.rowMicOverride);
        if (rowMic != null) {
            rowMic.post(() -> rowMic.requestFocus());
        }

        View.OnKeyListener dismissOnBackOrLeft = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    dismiss();
                    return true;
                }
            }
            return false;
        };

        for (int id : new int[]{
                R.id.rowMicOverride,
                R.id.rowPermAudio,
                R.id.rowPermOverlay,
                R.id.rowPermAccess,
                R.id.btnAutoGrant,
                R.id.rowNuvio,
                R.id.rowStremio,
                R.id.rowYoutube,
                R.id.rowCloudstream,
                R.id.rowBraveNews}) {
            View item = drawerView.findViewById(id);
            if (item != null) {
                item.setOnKeyListener(dismissOnBackOrLeft);
            }
        }

        View root = drawerView.findViewById(R.id.searchSettingsRoot);
        if (root != null) {
            root.setOnClickListener(v -> dismiss());
        }
    }

    private void bindViews(View root) {
        PackageManager pm = activity.getPackageManager();

        Switch switchMic = root.findViewById(R.id.switchMicOverride);
        boolean micOverride = prefs.getBoolean(KEY_MIC_OVERRIDE, true);
        switchMic.setChecked(micOverride);

        rowMic = root.findViewById(R.id.rowMicOverride);
        rowMic.setOnClickListener(v -> {
            boolean next = !switchMic.isChecked();
            switchMic.setChecked(next);
            prefs.edit().putBoolean(KEY_MIC_OVERRIDE, next).apply();
        });

        headerPermissions = root.findViewById(R.id.headerPermissions);
        rowPermAudio = root.findViewById(R.id.rowPermAudio);
        rowPermOverlay = root.findViewById(R.id.rowPermOverlay);
        rowPermAccess = root.findViewById(R.id.rowPermAccess);

        txtBadgeAudio = root.findViewById(R.id.txtBadgeAudio);
        txtBadgeOverlay = root.findViewById(R.id.txtBadgeOverlay);
        txtBadgeAccess = root.findViewById(R.id.txtBadgeAccess);
        btnAutoGrant = root.findViewById(R.id.btnAutoGrant);

        rowPermAudio.setOnClickListener(v -> requestAudioPermission());
        rowPermOverlay.setOnClickListener(v -> openOverlaySettings());
        rowPermAccess.setOnClickListener(v -> openAccessibilitySettings());
        btnAutoGrant.setOnClickListener(v -> autoGrantNext());

        // Setup App Rows
        rowNuvio = root.findViewById(R.id.rowNuvio);
        ImageView imgNuvio = root.findViewById(R.id.imgIconNuvio);
        TextView txtNuvio = root.findViewById(R.id.txtTitleNuvio);
        Switch switchNuvio = root.findViewById(R.id.switchNuvio);
        setupAppRow(pm, "com.nuvio.tv", rowNuvio, imgNuvio, txtNuvio, switchNuvio, KEY_NUVIO_ENABLED, "Nuvio");

        rowStremio = root.findViewById(R.id.rowStremio);
        ImageView imgStremio = root.findViewById(R.id.imgIconStremio);
        TextView txtStremio = root.findViewById(R.id.txtTitleStremio);
        Switch switchStremio = root.findViewById(R.id.switchStremio);
        setupAppRow(pm, "com.stremio.one", rowStremio, imgStremio, txtStremio, switchStremio, KEY_STREMIO_ENABLED, "Stremio");

        rowYoutube = root.findViewById(R.id.rowYoutube);
        ImageView imgYoutube = root.findViewById(R.id.imgIconYoutube);
        TextView txtYoutube = root.findViewById(R.id.txtTitleYoutube);
        Switch switchYoutube = root.findViewById(R.id.switchYoutube);
        String ytPkg = detectYoutubePackage(pm);
        setupAppRow(pm, ytPkg, rowYoutube, imgYoutube, txtYoutube, switchYoutube, KEY_YOUTUBE_ENABLED,
                ytPkg != null && ytPkg.contains("smarttube") ? "SmartTube" : "YouTube");

        rowCloudstream = root.findViewById(R.id.rowCloudstream);
        ImageView imgCloudstream = root.findViewById(R.id.imgIconCloudstream);
        TextView txtCloudstream = root.findViewById(R.id.txtTitleCloudstream);
        Switch switchCloudstream = root.findViewById(R.id.switchCloudstream);
        setupAppRow(pm, "com.lagradost.cloudstream3", rowCloudstream, imgCloudstream, txtCloudstream, switchCloudstream, KEY_CLOUDSTREAM_ENABLED, "Cloudstream");

        // Generic Web Search
        rowBraveNews = root.findViewById(R.id.rowBraveNews);
        Switch switchBrave = root.findViewById(R.id.switchBraveNews);
        if (rowBraveNews != null && switchBrave != null) {
            rowBraveNews.setVisibility(View.VISIBLE);
            switchBrave.setChecked(prefs.getBoolean(KEY_BRAVE_ENABLED, true));
            rowBraveNews.setOnClickListener(v -> {
                boolean next = !switchBrave.isChecked();
                switchBrave.setChecked(next);
                prefs.edit().putBoolean(KEY_BRAVE_ENABLED, next).apply();
            });
        }

        refreshPermissions();
    }

    private String detectYoutubePackage(PackageManager pm) {
        String[] candidates = new String[]{
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv",
            "com.google.android.youtube.tv",
            "app.revanced.android.youtube",
            "org.schabi.newpipe"
        };
        for (String pkg : candidates) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void setupAppRow(PackageManager pm, String pkg, View row, ImageView iconView,
                             TextView titleView, Switch switchView, String prefKey, String fallbackTitle) {
        if (row == null) return;
        boolean installed = false;
        Drawable icon = null;
        String title = fallbackTitle;

        if (pkg != null) {
            try {
                pm.getPackageInfo(pkg, 0);
                installed = true;
                icon = pm.getApplicationIcon(pkg);
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                if (label != null && label.length() > 0) {
                    title = label.toString();
                }
            } catch (Exception ignored) {}
        }

        if (installed) {
            row.setVisibility(View.VISIBLE);
            if (iconView != null && icon != null) {
                iconView.setImageDrawable(icon);
                iconView.setVisibility(View.VISIBLE);
            }
            if (titleView != null) {
                titleView.setText(title);
            }
            if (switchView != null) {
                switchView.setChecked(prefs.getBoolean(prefKey, true));
            }
            row.setOnClickListener(v -> {
                if (switchView != null) {
                    boolean next = !switchView.isChecked();
                    switchView.setChecked(next);
                    prefs.edit().putBoolean(prefKey, next).apply();
                }
            });
        } else {
            row.setVisibility(View.GONE);
        }
    }

    public void requestAudioPermission() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.REQUEST_CODE_AUDIO);
        } else {
            Toast.makeText(activity, "Đã cấp quyền", Toast.LENGTH_SHORT).show();
        }
    }

    public void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e1) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                activity.startActivity(intent);
            } catch (Exception e2) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                } catch (Exception e3) {
                    Toast.makeText(activity, "Lỗi mở cài đặt", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Lỗi mở cài đặt", Toast.LENGTH_SHORT).show();
        }
    }

    public void autoGrantNext() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission();
            return;
        }
        if (!Settings.canDrawOverlays(activity)) {
            openOverlaySettings();
            return;
        }
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        Toast.makeText(activity, "Đã đủ quyền", Toast.LENGTH_SHORT).show();
    }

    public void refreshPermissions() {
        if (drawerView == null) return;

        boolean hasAudio = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean hasOverlay = Settings.canDrawOverlays(activity);
        boolean hasAccess = isAccessibilityServiceEnabled();

        if (rowPermAudio != null) {
            rowPermAudio.setVisibility(hasAudio ? View.GONE : View.VISIBLE);
        }
        if (rowPermOverlay != null) {
            rowPermOverlay.setVisibility(hasOverlay ? View.GONE : View.VISIBLE);
        }
        if (rowPermAccess != null) {
            rowPermAccess.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        }

        boolean allGranted = hasAudio && hasOverlay && hasAccess;
        if (headerPermissions != null) {
            headerPermissions.setVisibility(allGranted ? View.GONE : View.VISIBLE);
        }
        if (btnAutoGrant != null) {
            btnAutoGrant.setVisibility(allGranted ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String prefString = Settings.Secure.getString(
                activity.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return prefString != null && prefString.contains(activity.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;

        View drawerContent = drawerView.findViewById(R.id.searchSettingsContent);
        if (drawerContent != null) {
            drawerContent.animate().translationX(400f).setDuration(180).withEndAction(() -> {
                try {
                    ViewGroup parent = (ViewGroup) drawerView.getParent();
                    if (parent != null) parent.removeView(drawerView);
                } catch (Exception ignored) {}
                drawerView = null;
                isShowing = false;
            }).start();
        } else {
            try {
                ViewGroup parent = (ViewGroup) drawerView.getParent();
                if (parent != null) parent.removeView(drawerView);
            } catch (Exception ignored) {}
            drawerView = null;
            isShowing = false;
        }
    }
}
