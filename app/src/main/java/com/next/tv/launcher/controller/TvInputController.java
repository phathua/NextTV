package com.next.tv.launcher.controller;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.CoocaaSources;
import com.next.tv.launcher.Motion;
import com.next.tv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Controller for TV input sources (HDMI, AV, Tuner, Coocaa, OEM Source Panel)
 * and Live TV launch logic.
 */
public final class TvInputController {

    public interface PopupListener {
        void onPopupOpened(PopupWindow popup);
        void onPopupDismissed(PopupWindow popup);
    }

    private final Activity activity;
    private final Handler mainHandler;
    private final PopupListener popupListener;

    private boolean showInputsPill = true;
    private boolean showInputsMenuRow = true;
    private boolean topMenu;
    private boolean bootToLiveTv;
    private boolean coocaaSources;
    private boolean reducedMotion;

    private String liveTvPkg = "";
    private String liveTvAction = "";
    private String liveTvInputId = "";
    private String sourcePanelService = "";
    private String sourcePanelAction = "";
    private String sourcePanelExtra = "";
    private String inputAction = "";
    private String inputUriScheme = "";

    private PopupWindow openPopup;

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            notifyInputsChanged();
        }

        @Override
        public void onInputRemoved(String inputId) {
            notifyInputsChanged();
        }

        @Override
        public void onInputUpdated(String inputId) {
            notifyInputsChanged();
        }

        @Override
        public void onInputStateChanged(String inputId, int state) {
            notifyInputsChanged();
        }
    };

    private Runnable onInputsChangedCallback;

    public TvInputController(Activity activity, Handler mainHandler, PopupListener popupListener) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.popupListener = popupListener;
    }

    public void setConfig(boolean showInputsPill,
                          boolean showInputsMenuRow,
                          boolean topMenu,
                          boolean bootToLiveTv,
                          boolean coocaaSources,
                          boolean reducedMotion,
                          String liveTvPkg,
                          String liveTvAction,
                          String liveTvInputId,
                          String sourcePanelService,
                          String sourcePanelAction,
                          String sourcePanelExtra,
                          String inputAction,
                          String inputUriScheme) {
        this.showInputsPill = showInputsPill;
        this.showInputsMenuRow = showInputsMenuRow;
        this.topMenu = topMenu;
        this.bootToLiveTv = bootToLiveTv;
        this.coocaaSources = coocaaSources;
        this.reducedMotion = reducedMotion;
        this.liveTvPkg = liveTvPkg != null ? liveTvPkg : "";
        this.liveTvAction = liveTvAction != null ? liveTvAction : "";
        this.liveTvInputId = liveTvInputId != null ? liveTvInputId : "";
        this.sourcePanelService = sourcePanelService != null ? sourcePanelService : "";
        this.sourcePanelAction = sourcePanelAction != null ? sourcePanelAction : "";
        this.sourcePanelExtra = sourcePanelExtra != null ? sourcePanelExtra : "";
        this.inputAction = inputAction != null ? inputAction : "";
        this.inputUriScheme = inputUriScheme != null ? inputUriScheme : "";
    }

    public void setOnInputsChangedCallback(Runnable callback) {
        this.onInputsChangedCallback = callback;
    }

    public void register(Handler handler) {
        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            tim.registerCallback(inputCallback, handler != null ? handler : mainHandler);
        }
    }

    public void unregister() {
        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            tim.unregisterCallback(inputCallback);
        }
    }

    private void notifyInputsChanged() {
        if (onInputsChangedCallback != null) {
            mainHandler.post(onInputsChangedCallback);
        }
    }

    public boolean isShowInputsPill() {
        return showInputsPill;
    }

    public void setShowInputsPill(boolean show) {
        this.showInputsPill = show;
    }

    public boolean isShowInputsMenuRow() {
        return showInputsMenuRow;
    }

    public void updateInputsButton(View btnInputs) {
        if (btnInputs == null) return;
        if (!showInputsPill) {
            btnInputs.setVisibility(View.GONE);
            return;
        }
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty()
                || (coocaaSources && !CoocaaSources.list(activity).isEmpty());
        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(activity)) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    public boolean openLiveTvAfterBoot() {
        if (!bootToLiveTv) return false;
        int boot = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = activity.getSharedPreferences("boot", Context.MODE_PRIVATE);
        if (boot < 0 || prefs.getInt("live_tv_boot", -1) == boot) return false;
        prefs.edit().putInt("live_tv_boot", boot).apply();
        Intent i = liveTvIntent();
        if (i == null) return false;
        open(i);
        return true;
    }

    public Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        PackageManager pm = activity.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return pm.resolveActivity(byAction, 0) != null ? byAction : null;
    }

    public void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        int eq = sourcePanelExtra.indexOf('=');
        if (eq > 0) {
            try {
                i.putExtra(sourcePanelExtra.substring(0, eq).trim(),
                        Integer.parseInt(sourcePanelExtra.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            if (activity.getPackageManager().resolveActivity(i, 0) != null) {
                activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            activity.startService(i);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    public Integer mappedSource(String inputId) {
        if (inputId == null) return null;
        String hw = inputId.substring(inputId.lastIndexOf('/') + 1);
        for (String m : activity.getResources().getStringArray(R.array.input_sources)) {
            int eq = m.indexOf('=');
            if (eq <= 0 || !m.substring(0, eq).trim().equals(hw)) continue;
            String v = m.substring(eq + 1).trim();
            if (v.equals("-")) return -1;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public void openInput(String inputId) {
        String sourceId = inputId;
        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                TvInputInfo info = tim.getTvInputInfo(inputId);
                if (info != null && info.getParentId() != null) sourceId = info.getParentId();
            } catch (RuntimeException ignored) {
            }
        }
        Integer source = mappedSource(sourceId);
        if (source != null && source >= 0 && !inputAction.isEmpty()) {
            try {
                activity.startActivity(new Intent(inputAction).putExtra("input", source.intValue())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // fall through to standard way
            }
        }
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = (inputUriScheme.isEmpty()
                ? new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                : new Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(inputUriScheme).build()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(view);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    public void showInputs(View anchor, ViewGroup root) {
        if (anchor == null || root == null) return;
        List<CoocaaSources.Source> coocaa = coocaaSources ? CoocaaSources.list(activity) : Collections.emptyList();
        if (coocaa.isEmpty() && !sourcePanelService.isEmpty() && liveTvIntent() == null) {
            openSourcePanel();
            return;
        }

        boolean underGear = topMenu;
        LayoutInflater inflater = LayoutInflater.from(activity);
        View panel = inflater.inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) activity.getSystemService(Context.TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        boolean tunerIsAnalog = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(activity)).compareToIgnoreCase(String.valueOf(b.loadLabel(activity)));
            });

            Set<String> inputIds = new HashSet<>();
            for (TvInputInfo info : inputs) inputIds.add(info.getId());
            Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
            for (TvInputInfo info : inputs) {
                String parent = info.getParentId();
                if (parent == null || !inputIds.contains(parent)) continue;
                if (!info.isPassthroughInput() || info.isHidden(activity)) continue;
                List<TvInputInfo> onPort = cecDevices.get(parent);
                if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
                onPort.add(info);
            }

            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;

            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (liveTvInputId.isEmpty() || liveTvInputId.equals(info.getId()))) {
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
                    boolean better = tunerId == null
                            || (hw && !tunerIsHw)
                            || (hw == tunerIsHw && tunerIsAnalog && !analog)
                            || (hw == tunerIsHw && analog == tunerIsAnalog
                            && info.getId().compareTo(tunerId) < 0);
                    if (better) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                        tunerIsAnalog = analog;
                    }
                }
                if (!info.isPassthroughInput() || info.isHidden(activity)) continue;
                if (Integer.valueOf(-1).equals(mappedSource(info.getId()))) continue;
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(activity);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                CharSequence custom = info.loadCustomLabel(activity);
                boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows, density);
                if (!hdmi) sawNonHdmi = true;
                List<TvInputInfo> devices = cecDevices.get(info.getId());
                if (devices != null) {
                    for (TvInputInfo device : devices) {
                        CharSequence name = device.loadCustomLabel(activity);
                        if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(activity);
                        String deviceLabel = name == null || name.length() == 0 ? label : name + " \u00B7 " + label;
                        final String deviceId = device.getId();
                        int state = hdmiStateBogus ? -1 : tim.getInputState(deviceId);
                        addInputRow(inflater, rows, popup, R.drawable.ic_input, deviceLabel, state, () -> openInput(deviceId));
                    }
                    continue;
                }
                if (named) label = custom + " \u00B7 " + label;
                final String id = info.getId();
                int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
                addInputRow(inflater, rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }

        if (rows.getChildCount() == 0) {
            for (CoocaaSources.Source s : coocaa) {
                int icon = "HDMI".equals(s.name) ? R.drawable.ic_input
                        : "VOD".equals(s.name) ? R.drawable.ic_livetv : R.drawable.ic_av;
                addInputRow(inflater, rows, popup, icon, s.label, -1, () -> {
                    if (!CoocaaSources.open(activity, s)) {
                        Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(inflater, rows, popup, R.drawable.ic_input, activity.getString(R.string.inputs), -1, this::openSourcePanel);
        }

        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            boolean configured = !liveTvInputId.isEmpty();
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(configured ? TvContract.buildChannelsUriForInput(tunerId)
                                    : TvContract.buildChannelUriForPassthroughInput(tunerId),
                            configured ? TvContract.Channels.CONTENT_TYPE : TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg);
            if (tuned.resolveActivity(activity.getPackageManager()) != null) liveTv = tuned;
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows, density);
            final Intent liveTvIntent = liveTv;
            addInputRow(inflater, rows, popup, R.drawable.ic_livetv, activity.getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(activity, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, Math.min(panel.getMeasuredWidth(), (int) (360 * density))));

        int alignment = activity.getResources().getBoolean(R.bool.rtl_menu_bar) != underGear ? Gravity.END : Gravity.START;
        dropIn(popup, panel, anchor, alignment, density);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
            if (popupListener != null) {
                popupListener.onPopupDismissed(popup);
            }
        });
        openPopup = popup;
        if (popupListener != null) {
            popupListener.onPopupOpened(popup);
        }
        return popup;
    }

    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity, float density) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows, float density) {
        View line = new View(activity);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LayoutInflater inflater, LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = inflater.inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);

        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }

        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
