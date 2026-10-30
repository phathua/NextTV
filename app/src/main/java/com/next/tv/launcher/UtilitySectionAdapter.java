package com.next.tv.launcher;

import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.clean.BackgroundAppCleaner;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.widget.WidgetManagerDialog;

import java.util.Locale;

public class UtilitySectionAdapter extends RecyclerView.Adapter<UtilitySectionAdapter.UtilityViewHolder> {

    public static final int TYPE_SETTINGS = 0;
    public static final int TYPE_HIDE_APPS = 1;
    public static final int TYPE_KID_MODE = 2;
    public static final int TYPE_CLEAN_RAM = 3;
    public static final int TYPE_REMOTE_CONTROL = 4;
    public static final int TYPE_REMOTE_FILE = 5;
    public static final int TYPE_WALLPAPER = 6;

    private final java.util.List<Integer> activeTypes = new java.util.ArrayList<>();
    private int currentMode = 0;

    public interface OnUtilityFocusListener {
        void onUtilityFocused(View cardView);
    }

    public interface OnKidModeToggledListener {
        void onKidModeToggled(boolean isActive);
    }

    public interface OnKidModeRequestedListener {
        void onKidModeRequested(View triggerView);
    }

    public interface OnManageHiddenAppsRequestedListener {
        void onManageHiddenAppsRequested(View triggerView);
    }

    public interface OnRemoteControlRequestedListener {
        void onRemoteControlRequested(View triggerView);
    }

    public interface OnRemoteFileRequestedListener {
        void onRemoteFileRequested(View triggerView);
    }

    public interface OnWallpaperPickerRequestedListener {
        void onWallpaperPickerRequested(View triggerView);
    }

    public interface OnWidgetManagerRequestedListener {
        void onWidgetManagerRequested();
    }

    public interface OnReorderSectionsRequestedListener {
        void onReorderSectionsRequested();
    }

    public interface OnAdvancedSettingsRequestedListener {
        void onAdvancedSettingsRequested(View triggerView);
    }

    public interface OnMemoryCleanerRequestedListener {
        void onMemoryCleanerRequested(View triggerView);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final AppLockManager lockManager;
    private final OnUtilityFocusListener focusListener;
    private final OnKidModeToggledListener kidModeListener;
    private OnKidModeRequestedListener kidModeRequestListener;
    private final OnManageHiddenAppsRequestedListener hiddenAppsListener;
    private final OnRemoteControlRequestedListener remoteControlListener;
    private final OnRemoteFileRequestedListener remoteFileListener;
    private final OnWallpaperPickerRequestedListener wallpaperPickerListener;
    private final OnWidgetManagerRequestedListener widgetManagerListener;
    private final OnReorderSectionsRequestedListener reorderSectionsListener;
    private final OnAdvancedSettingsRequestedListener advancedSettingsListener;
    private OnMemoryCleanerRequestedListener memoryCleanerListener;

    public UtilitySectionAdapter(Context context,
                                 OnUtilityFocusListener focusListener,
                                 OnKidModeToggledListener kidModeListener,
                                 OnManageHiddenAppsRequestedListener hiddenAppsListener,
                                 OnRemoteControlRequestedListener remoteControlListener,
                                 OnRemoteFileRequestedListener remoteFileListener,
                                 OnWallpaperPickerRequestedListener wallpaperPickerListener) {
        this(context, focusListener, kidModeListener, hiddenAppsListener, remoteControlListener, remoteFileListener, wallpaperPickerListener, null, null, null);
    }

    public UtilitySectionAdapter(Context context,
                                 OnUtilityFocusListener focusListener,
                                 OnKidModeToggledListener kidModeListener,
                                 OnManageHiddenAppsRequestedListener hiddenAppsListener,
                                 OnRemoteControlRequestedListener remoteControlListener,
                                 OnRemoteFileRequestedListener remoteFileListener,
                                 OnWallpaperPickerRequestedListener wallpaperPickerListener,
                                 OnWidgetManagerRequestedListener widgetManagerListener,
                                 OnReorderSectionsRequestedListener reorderSectionsListener) {
        this(context, focusListener, kidModeListener, hiddenAppsListener, remoteControlListener, remoteFileListener, wallpaperPickerListener, widgetManagerListener, reorderSectionsListener, null);
    }

    public UtilitySectionAdapter(Context context,
                                 OnUtilityFocusListener focusListener,
                                 OnKidModeToggledListener kidModeListener,
                                 OnManageHiddenAppsRequestedListener hiddenAppsListener,
                                 OnRemoteControlRequestedListener remoteControlListener,
                                 OnRemoteFileRequestedListener remoteFileListener,
                                 OnWallpaperPickerRequestedListener wallpaperPickerListener,
                                 OnWidgetManagerRequestedListener widgetManagerListener,
                                 OnReorderSectionsRequestedListener reorderSectionsListener,
                                 OnAdvancedSettingsRequestedListener advancedSettingsListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.lockManager = AppLockManager.getInstance(context);
        this.focusListener = focusListener;
        this.kidModeListener = kidModeListener;
        this.hiddenAppsListener = hiddenAppsListener;
        this.remoteControlListener = remoteControlListener;
        this.remoteFileListener = remoteFileListener;
        this.wallpaperPickerListener = wallpaperPickerListener;
        this.widgetManagerListener = widgetManagerListener;
        this.reorderSectionsListener = reorderSectionsListener;
        this.advancedSettingsListener = advancedSettingsListener;
        setMode(0);
    }

    public void setOnKidModeRequestedListener(OnKidModeRequestedListener listener) {
        this.kidModeRequestListener = listener;
    }

    public void setOnMemoryCleanerRequestedListener(OnMemoryCleanerRequestedListener listener) {
        this.memoryCleanerListener = listener;
    }
    public void setMode(int mode) {
        this.currentMode = mode;
        activeTypes.clear();
        if (mode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            activeTypes.add(TYPE_SETTINGS);
            activeTypes.add(TYPE_WALLPAPER);
        } else if (mode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_BALANCED) {
            activeTypes.add(TYPE_SETTINGS);
            activeTypes.add(TYPE_CLEAN_RAM);
            activeTypes.add(TYPE_REMOTE_CONTROL);
            activeTypes.add(TYPE_WALLPAPER);
        } else {
            activeTypes.add(TYPE_SETTINGS);
            activeTypes.add(TYPE_HIDE_APPS);
            activeTypes.add(TYPE_KID_MODE);
            activeTypes.add(TYPE_CLEAN_RAM);
            activeTypes.add(TYPE_REMOTE_CONTROL);
            activeTypes.add(TYPE_REMOTE_FILE);
            activeTypes.add(TYPE_WALLPAPER);
        }
        notifyDataSetChanged();
    }

    public int getPositionForType(int type) {
        return activeTypes.indexOf(type);
    }

    public void notifyTypeChanged(int type) {
        int index = activeTypes.indexOf(type);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }


    @NonNull
    @Override
    public UtilityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_utility_card, parent, false);
        return new UtilityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UtilityViewHolder holder, int position) {
        holder.progressBar.setVisibility(View.GONE);
        if (position < 0 || position >= activeTypes.size()) return;
        int type = activeTypes.get(position);

        boolean kidActive = lockManager.isKidModeActive();
        boolean isLocked = kidActive && type != TYPE_KID_MODE;
        holder.itemView.setAlpha(isLocked ? 0.38f : 1.0f);

        switch (type) {
            case TYPE_SETTINGS:
                if (currentMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
                    holder.icon.setImageResource(R.drawable.ic_leaf);
                    holder.title.setText("Chế độ Siêu gọn");
                    holder.subtitle.setText("Quản lý nâng cao");
                } else if (currentMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_BALANCED) {
                    holder.icon.setImageResource(R.drawable.ic_mode_gauge);
                    holder.title.setText("Chế độ Cân bằng");
                    holder.subtitle.setText("Quản lý nâng cao");
                } else {
                    holder.icon.setImageResource(R.drawable.ic_layers);
                    holder.title.setText("Chế độ Đầy đủ");
                    holder.subtitle.setText("Quản trị tiện ích");
                }
                break;
            case TYPE_HIDE_APPS:
                holder.icon.setImageResource(R.drawable.ic_hide_apps);
                holder.title.setText(R.string.hide_apps_title);
                holder.subtitle.setText(R.string.hide_apps_subtitle);
                break;
            case TYPE_KID_MODE:
                holder.icon.setImageResource(R.drawable.ic_kid_mode);
                holder.title.setText(R.string.kid_mode_title);
                holder.subtitle.setText(kidActive ? R.string.kid_mode_subtitle_on : R.string.kid_mode_subtitle_off);
                break;
            case TYPE_CLEAN_RAM:
                holder.icon.setImageResource(R.drawable.ic_clean_ram);
                holder.title.setText(R.string.clean_ram_title);
                holder.progressBar.setVisibility(View.VISIBLE);
                bindRamInfo(holder);
                break;
            case TYPE_REMOTE_CONTROL:
                holder.icon.setImageResource(R.drawable.ic_remote_control);
                holder.title.setText("Điều khiển");
                if (com.next.tv.launcher.remote.WebRemoteServer.getInstance(context).isRunning()) {
                    holder.subtitle.setText("Đang bật Web Remote");
                } else {
                    holder.subtitle.setText("Tùy chỉnh remote");
                }
                break;
            case TYPE_REMOTE_FILE:
                holder.icon.setImageResource(R.drawable.ic_remote_file);
                holder.title.setText("Quản lý tệp");
                if (com.next.tv.launcher.remote.RemoteFileServer.getInstance(context).isRunning()) {
                    int port = com.next.tv.launcher.remote.RemoteFileServer.getInstance(context).getPort(); holder.subtitle.setText("Đang mở cổng " + port);
                } else {
                    holder.subtitle.setText("Thông qua điện thoại");
                }
                break;
            case TYPE_WALLPAPER:
                holder.icon.setImageResource(R.drawable.ic_wallpaper_picker);
                holder.title.setText("Đổi hình nền");
                holder.subtitle.setText("Kho ảnh TV");
                break;
        }

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(160).start();
                if (focusListener != null) focusListener.onUtilityFocused(v);
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
            }
        });

        holder.itemView.setOnClickListener(v -> handleItemClick(position, holder));

        if (type == TYPE_CLEAN_RAM) {
            holder.itemView.setOnLongClickListener(v -> {
                if (memoryCleanerListener != null) {
                    memoryCleanerListener.onMemoryCleanerRequested(holder.itemView);
                    return true;
                }
                return false;
            });
            holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_MENU && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (memoryCleanerListener != null) {
                        memoryCleanerListener.onMemoryCleanerRequested(holder.itemView);
                        return true;
                    }
                }
                return false;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setOnKeyListener(null);
        }
    }

    private void bindRamInfo(UtilityViewHolder holder) {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(mi);
                long total = mi.totalMem;
                long avail = mi.availMem;
                long used = total - avail;
                int percent = (int) (used * 100 / (total > 0 ? total : 1));
                float usedGb = used / (1024f * 1024f * 1024f);
                float totalGb = total / (1024f * 1024f * 1024f);
                holder.subtitle.setText(String.format(Locale.getDefault(), "Đã dùng %.1f / %.1f GB (%d%%)", usedGb, totalGb, percent));
                holder.progressBar.setProgress(percent);
                return;
            }
        } catch (Exception ignored) {}
        holder.subtitle.setText(R.string.clean_ram_subtitle);
        holder.progressBar.setProgress(65);
    }

    private void handleItemClick(int position, UtilityViewHolder holder) {
        if (position < 0 || position >= activeTypes.size()) return;
        int type = activeTypes.get(position);

        if (lockManager.isKidModeActive() && type != TYPE_KID_MODE) {
            Toast.makeText(context, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (type) {
            case TYPE_SETTINGS:
                if (advancedSettingsListener != null) {
                    advancedSettingsListener.onAdvancedSettingsRequested(holder.itemView);
                } else {
                    showManageChoiceDialog();
                }
                break;
            case TYPE_HIDE_APPS:
                if (hiddenAppsListener != null) hiddenAppsListener.onManageHiddenAppsRequested(holder.itemView);
                break;
            case TYPE_KID_MODE:
                if (!lockManager.hasPin()) {
                    new PinLockDialog(context, PinLockDialog.MODE_CREATE, "Cài đặt mã PIN", "Thiết lập mã cho trẻ em", new PinLockDialog.PinResultListener() {
                        @Override
                        public void onSuccess() {
                            if (kidModeRequestListener != null) {
                                kidModeRequestListener.onKidModeRequested(holder.itemView);
                            }
                        }

                        @Override
                        public void onCancel() {}
                    }).show();
                } else {
                    new PinLockDialog(context, PinLockDialog.MODE_VERIFY, "Mã PIN phụ huynh", "Nhập mã để mở cài đặt", new PinLockDialog.PinResultListener() {
                        @Override
                        public void onSuccess() {
                            if (kidModeRequestListener != null) {
                                kidModeRequestListener.onKidModeRequested(holder.itemView);
                            }
                        }

                        @Override
                        public void onCancel() {}
                    }).show();
                }
                break;
            case TYPE_CLEAN_RAM:
                com.next.tv.launcher.clean.SmartRamGovernor.cleanRamSmartAsync(context, true, true, res -> {
                    String msg = res != null && res.killedCount > 0
                            ? ("Đã dọn " + res.freedMb + " MB RAM")
                            : "RAM đã ở trạng thái tối ưu";
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                    notifyItemChanged(position);
                });
                break;
            case TYPE_REMOTE_CONTROL:
                if (remoteControlListener != null) {
                    remoteControlListener.onRemoteControlRequested(holder.itemView);
                }
                break;
            case TYPE_REMOTE_FILE:
                if (remoteFileListener != null) {
                    remoteFileListener.onRemoteFileRequested(holder.itemView);
                }
                break;
            case TYPE_WALLPAPER:
                if (wallpaperPickerListener != null) {
                    wallpaperPickerListener.onWallpaperPickerRequested(holder.itemView);
                }
                break;
        }
    }

    private void showManageChoiceDialog() {
        Dialog dialog = new Dialog(context, R.style.Theme_TvHome);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        View panel = inflater.inflate(R.layout.popup_inputs, null);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setPadding(20, 20, 20, 20);

        LinearLayout rows = panel.findViewById(R.id.rows);

        TextView titleView = new TextView(context);
        titleView.setText("Quản lý giao diện");
        titleView.setTextColor(Color.parseColor("#F4F4F5"));
        titleView.setTextSize(17f);
        titleView.setPadding(16, 6, 16, 12);
        rows.addView(titleView);

        String[] titles = new String[]{"Quản lý tiện ích", "Sắp xếp màn hình", "Cài đặt ứng dụng"};
        Runnable[] actions = new Runnable[]{
                () -> {
                    if (widgetManagerListener != null) {
                        widgetManagerListener.onWidgetManagerRequested();
                    } else {
                        new WidgetManagerDialog(context, null).show();
                    }
                },
                () -> {
                    if (reorderSectionsListener != null) {
                        reorderSectionsListener.onReorderSectionsRequested();
                    }
                },
                () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(fallback);
                        } catch (Exception ignored) {}
                    }
                }
        };

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.item_input, rows, false);
            row.findViewById(R.id.icon).setVisibility(View.GONE);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            row.findViewById(R.id.status).setVisibility(View.GONE);
            TextView rowTitle = row.findViewById(R.id.title);
            rowTitle.setText(titles[i]);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                actions[index].run();
            });

            row.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(120).start();
            });

            rows.addView(row);
        }

        float density = context.getResources().getDisplayMetrics().density;
        dialog.setContentView(panel, new ViewGroup.LayoutParams((int) (260 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
    }

    @Override
    public int getItemCount() {
        return activeTypes.size();
    }

    static class UtilityViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        final ProgressBar progressBar;

        UtilityViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.utilityIcon);
            title = itemView.findViewById(R.id.utilityTitle);
            subtitle = itemView.findViewById(R.id.utilitySubtitle);
            progressBar = itemView.findViewById(R.id.utilityProgressBar);
        }
    }
}