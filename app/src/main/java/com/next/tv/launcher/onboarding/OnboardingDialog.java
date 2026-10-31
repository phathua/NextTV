package com.next.tv.launcher.onboarding;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.apps.AppAdapter;
import com.next.tv.launcher.util.FastPosterLoader;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.apps.Order;
import com.next.tv.launcher.R;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.KidTimeQuotaTracker;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.widget.UsageStatsTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;

public class OnboardingDialog extends Dialog {
    public static final String PREF_ONBOARDING = "onboarding_prefs";
    public static final String KEY_COMPLETED = "onboarding_completed";

    public interface OnOnboardingFinishedListener {
        void onFinished();
    }

    private final Activity activity;
    private final OnOnboardingFinishedListener finishedListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentStep = 1; // 1 to 8

    public static final int MODE_FULL = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_SUPER_LIGHT = 2;

    // Compatibility aliases
    public static final int MODE_STANDARD = MODE_FULL;
    public static final int MODE_FAMILY = MODE_BALANCED;
    public static final int MODE_LOW_RAM = MODE_SUPER_LIGHT;

    private int selectedMode = MODE_FULL;

    // Left Column Hero Views
    private View heroModeContainer, heroMicContainer, heroStatsContainer, heroWifiContainer;
    private View heroPinAppsContainer, heroWallpaperContainer, heroPinContainer, heroReadyContainer;
    private ImageView ivHeroModeIcon, ivHeroWallpaperLive;
    private TextView tvHeroModeBadge, tvHeroModeSub;
    private TextView tvHeroPinBadge, tvHeroPinSub;
    private FrameLayout heroModeIconFrame;

    // Dynamic Step 5 & Step 8 elements
    private LinearLayout containerMiniDockIcons;
    private ImageView ivHeroReadyWallpaper;
    private TextView tvHeroReadyModeBadge;
    private LinearLayout rowReadyMiniWidgets;
    private LinearLayout containerReadyMiniDock;
    private LinearLayout rowFeature1, rowFeature2, rowFeature3;
    private FrameLayout frameFeature1Icon, frameFeature2Icon, frameFeature3Icon;
    private ImageView ivFeature1Icon, ivFeature2Icon, ivFeature3Icon;
    private TextView tvFeature1Title, tvFeature1Sub, tvFeature2Title, tvFeature2Sub, tvFeature3Title, tvFeature3Sub;

    // Stepper Indicators (8 steps)
    private View stepDot1, stepDot2, stepDot3, stepDot4, stepDot5, stepDot6, stepDot7, stepDot8;

    // Right Column Controls
    private TextView tvStepTitle, tvStepDesc;
    private LinearLayout step1Container, step2Container, step3Container, step4Container;
    private LinearLayout step5Container;
    private HorizontalScrollView step6Container;
    private LinearLayout step7Container, step8Container;
    private Button btnPrev, btnSkip, btnNext;

    // Step 1: Modes
    private View cardModeFull, cardModeBalanced, cardModeSuperLight;
    private View checkModeFull, checkModeBalanced, checkModeSuperLight;

    // Step 2, 3, 4: Permissions
    private View cardPermMic, cardPermUsage, cardPermWifi;
    private TextView badgePermMic, badgePermUsage, badgePermWifi;

    // Step 5: Pin Apps
    private RecyclerView rvPinApps;
    private PinAppsAdapter pinAppsAdapter;
    private final List<PinAppItem> pinAppList = new ArrayList<>();
    private final List<String> selectedPinnedKeys = new ArrayList<>();

    // Step 6: Wallpapers (7 models)
    private static final String[] WALLPAPER_ASSETS = {
            null, // 0: Deep Black
            "wallpapers/01-aurora.webp",
            "wallpapers/02-dusk-peaks.webp",
            "wallpapers/03-moon-lake.webp",
            "wallpapers/04-tea-hills.webp",
            "wallpapers/05-ruska.webp",
            "wallpapers/06-balloon-sunrise.webp"
    };
    private final View[] cardWalls = new View[7];
    private final ImageView[] checkWalls = new ImageView[7];
    private int selectedWallpaper = 0;

    // Step 7: Kids & PIN
    private View cardTimeNoLimit, cardTime60m, cardTime120m;
    private ImageView checkTimeNoLimit, checkTime60m, checkTime120m;
    private View cardSetPin;
    private TextView tvPinOnboardingStatus;

    public static boolean shouldShow(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE);
        return !sp.getBoolean(KEY_COMPLETED, false);
    }

    public static void setCompleted(Context context) {
        context.getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_COMPLETED, true).apply();
    }

    public OnboardingDialog(@NonNull Activity activity, OnOnboardingFinishedListener finishedListener) {
        super(activity, R.style.Theme_TvHome);
        this.activity = activity;
        this.finishedListener = finishedListener;
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_onboarding);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(0xF209090B));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        bindViews();
        setupStepListeners();
        loadAppsForPinning();
        updateStepUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updatePermissionBadges();
        }
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        if (currentStep == 1) {
            dismiss();
            return;
        }

        if (selectedMode == MODE_SUPER_LIGHT) {
            if (currentStep == 8) {
                currentStep = 6;
            } else if (currentStep == 6) {
                currentStep = 5;
            } else if (currentStep == 5) {
                currentStep = 1;
            } else {
                currentStep = 1;
            }
        } else {
            currentStep--;
        }
        updateStepUi();
    }

    private void bindViews() {
        // Hero views
        heroModeContainer = findViewById(R.id.heroModeContainer);
        heroMicContainer = findViewById(R.id.heroMicContainer);
        heroStatsContainer = findViewById(R.id.heroStatsContainer);
        heroWifiContainer = findViewById(R.id.heroWifiContainer);
        heroPinAppsContainer = findViewById(R.id.heroPinAppsContainer);
        heroWallpaperContainer = findViewById(R.id.heroWallpaperContainer);
        heroPinContainer = findViewById(R.id.heroPinContainer);
        heroReadyContainer = findViewById(R.id.heroReadyContainer);

        ivHeroModeIcon = findViewById(R.id.ivHeroModeIcon);
        tvHeroModeBadge = findViewById(R.id.tvHeroModeBadge);
        tvHeroModeSub = findViewById(R.id.tvHeroModeSub);
        heroModeIconFrame = findViewById(R.id.heroModeIconFrame);
        ivHeroWallpaperLive = findViewById(R.id.ivHeroWallpaperLive);
        tvHeroPinBadge = findViewById(R.id.tvHeroPinBadge);
        tvHeroPinSub = findViewById(R.id.tvHeroPinSub);
        containerMiniDockIcons = findViewById(R.id.containerMiniDockIcons);
        ivHeroReadyWallpaper = findViewById(R.id.ivHeroReadyWallpaper);
        tvHeroReadyModeBadge = findViewById(R.id.tvHeroReadyModeBadge);
        rowReadyMiniWidgets = findViewById(R.id.rowReadyMiniWidgets);
        containerReadyMiniDock = findViewById(R.id.containerReadyMiniDock);
        rowFeature1 = findViewById(R.id.rowFeature1);
        rowFeature2 = findViewById(R.id.rowFeature2);
        rowFeature3 = findViewById(R.id.rowFeature3);
        frameFeature1Icon = findViewById(R.id.frameFeature1Icon);
        frameFeature2Icon = findViewById(R.id.frameFeature2Icon);
        frameFeature3Icon = findViewById(R.id.frameFeature3Icon);
        ivFeature1Icon = findViewById(R.id.ivFeature1Icon);
        ivFeature2Icon = findViewById(R.id.ivFeature2Icon);
        ivFeature3Icon = findViewById(R.id.ivFeature3Icon);
        tvFeature1Title = findViewById(R.id.tvFeature1Title);
        tvFeature1Sub = findViewById(R.id.tvFeature1Sub);
        tvFeature2Title = findViewById(R.id.tvFeature2Title);
        tvFeature2Sub = findViewById(R.id.tvFeature2Sub);
        tvFeature3Title = findViewById(R.id.tvFeature3Title);
        tvFeature3Sub = findViewById(R.id.tvFeature3Sub);


        // Stepper dots
        stepDot1 = findViewById(R.id.stepDot1);
        stepDot2 = findViewById(R.id.stepDot2);
        stepDot3 = findViewById(R.id.stepDot3);
        stepDot4 = findViewById(R.id.stepDot4);
        stepDot5 = findViewById(R.id.stepDot5);
        stepDot6 = findViewById(R.id.stepDot6);
        stepDot7 = findViewById(R.id.stepDot7);
        stepDot8 = findViewById(R.id.stepDot8);

        // Right column typography
        tvStepTitle = findViewById(R.id.tvStepTitle);
        tvStepDesc = findViewById(R.id.tvStepDesc);

        // Containers
        step1Container = findViewById(R.id.step1Container);
        step2Container = findViewById(R.id.step2Container);
        step3Container = findViewById(R.id.step3Container);
        step4Container = findViewById(R.id.step4Container);
        step5Container = findViewById(R.id.step5Container);
        step6Container = findViewById(R.id.step6Container);
        step7Container = findViewById(R.id.step7Container);
        step8Container = findViewById(R.id.step8Container);

        // Bottom buttons
        btnPrev = findViewById(R.id.btnOnboardingPrev);
        btnSkip = findViewById(R.id.btnOnboardingSkip);
        btnNext = findViewById(R.id.btnOnboardingNext);

        btnPrev.setCompoundDrawableTintList(activity.getColorStateList(R.color.search_pill_text_color));
        if (btnSkip != null) btnSkip.setCompoundDrawableTintList(activity.getColorStateList(R.color.search_pill_text_color));
        btnNext.setCompoundDrawableTintList(activity.getColorStateList(R.color.search_pill_text_color));

        // Step 1: Modes
        cardModeFull = findViewById(R.id.cardModeFull);
        cardModeBalanced = findViewById(R.id.cardModeBalanced);
        cardModeSuperLight = findViewById(R.id.cardModeSuperLight);
        checkModeFull = findViewById(R.id.checkModeFull);
        checkModeBalanced = findViewById(R.id.checkModeBalanced);
        checkModeSuperLight = findViewById(R.id.checkModeSuperLight);

        // Step 2, 3, 4: Permissions
        cardPermMic = findViewById(R.id.cardPermMic);
        badgePermMic = findViewById(R.id.badgePermMic);
        cardPermUsage = findViewById(R.id.cardPermUsage);
        badgePermUsage = findViewById(R.id.badgePermUsage);
        cardPermWifi = findViewById(R.id.cardPermWifi);
        badgePermWifi = findViewById(R.id.badgePermWifi);

        // Step 5: Pin Apps
        rvPinApps = findViewById(R.id.rvPinApps);
        if (rvPinApps != null) {
            rvPinApps.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
            pinAppsAdapter = new PinAppsAdapter();
            rvPinApps.setAdapter(pinAppsAdapter);
        }

        // Step 6: Wallpapers
        cardWalls[0] = findViewById(R.id.cardWall0);
        cardWalls[1] = findViewById(R.id.cardWall1);
        cardWalls[2] = findViewById(R.id.cardWall2);
        cardWalls[3] = findViewById(R.id.cardWall3);
        cardWalls[4] = findViewById(R.id.cardWall4);
        cardWalls[5] = findViewById(R.id.cardWall5);
        cardWalls[6] = findViewById(R.id.cardWall6);

        checkWalls[0] = findViewById(R.id.checkWall0);
        checkWalls[1] = findViewById(R.id.checkWall1);
        checkWalls[2] = findViewById(R.id.checkWall2);
        checkWalls[3] = findViewById(R.id.checkWall3);
        checkWalls[4] = findViewById(R.id.checkWall4);
        checkWalls[5] = findViewById(R.id.checkWall5);
        checkWalls[6] = findViewById(R.id.checkWall6);

        // Load wallpaper preview thumbnails
        ImageView ivWall1 = findViewById(R.id.ivWall1);
        ImageView ivWall2 = findViewById(R.id.ivWall2);
        ImageView ivWall3 = findViewById(R.id.ivWall3);
        ImageView ivWall4 = findViewById(R.id.ivWall4);
        ImageView ivWall5 = findViewById(R.id.ivWall5);
        ImageView ivWall6 = findViewById(R.id.ivWall6);

        if (ivWall1 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/01-aurora.webp"), ivWall1, 0);
        if (ivWall2 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/02-dusk-peaks.webp"), ivWall2, 0);
        if (ivWall3 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/03-moon-lake.webp"), ivWall3, 0);
        if (ivWall4 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/04-tea-hills.webp"), ivWall4, 0);
        if (ivWall5 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/05-ruska.webp"), ivWall5, 0);
        if (ivWall6 != null) FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/wallpapers/06-balloon-sunrise.webp"), ivWall6, 0);

        // Step 7: Kids
        cardTimeNoLimit = findViewById(R.id.cardTimeNoLimit);
        cardTime60m = findViewById(R.id.cardTime60m);
        cardTime120m = findViewById(R.id.cardTime120m);
        checkTimeNoLimit = findViewById(R.id.checkTimeNoLimit);
        checkTime60m = findViewById(R.id.checkTime60m);
        checkTime120m = findViewById(R.id.checkTime120m);
        cardSetPin = findViewById(R.id.cardSetPin);
        tvPinOnboardingStatus = findViewById(R.id.tvPinOnboardingStatus);
    }

    private void setupStepListeners() {
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> {
                if (currentStep >= 2 && currentStep <= 4) {
                    currentStep = 5;
                    updateStepUi();
                } else if (currentStep == 5) {
                    currentStep = 6;
                    updateStepUi();
                } else if (currentStep == 6) {
                    currentStep = (selectedMode == MODE_SUPER_LIGHT) ? 8 : 7;
                    updateStepUi();
                } else if (currentStep == 7) {
                    currentStep = 8;
                    updateStepUi();
                } else {
                    finishOnboarding();
                }
            });
            btnSkip.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(140).start();
            });
        }

        btnPrev.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(140).start();
        });

        btnNext.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(140).start();
        });

        btnPrev.setOnClickListener(v -> navigateBack());
        btnNext.setOnClickListener(v -> onPrimaryActionClicked());

        // Step 1: Modes setup
        setupCardFocus(cardModeFull, () -> selectMode(MODE_FULL), () -> updateHeroMode(MODE_FULL));
        setupCardFocus(cardModeBalanced, () -> selectMode(MODE_BALANCED), () -> updateHeroMode(MODE_BALANCED));
        setupCardFocus(cardModeSuperLight, () -> selectMode(MODE_SUPER_LIGHT), () -> updateHeroMode(MODE_SUPER_LIGHT));

        // Step 2: Mic
        if (cardPermMic != null) {
            setupCardFocus(cardPermMic, this::requestMicrophonePermission, null);
        }

        // Step 3: Usage
        if (cardPermUsage != null) {
            setupCardFocus(cardPermUsage, this::requestUsageStatsPermission, null);
        }

        // Step 4: Wi-Fi
        if (cardPermWifi != null) {
            if (!com.next.tv.launcher.BuildConfig.USE_MANAGE_STORAGE) {
                cardPermWifi.setVisibility(View.GONE);
            } else {
                setupCardFocus(cardPermWifi, this::requestWifiPermission, null);
            }
        }

        // Step 6: Wallpapers
        for (int i = 0; i < cardWalls.length; i++) {
            final int index = i;
            if (cardWalls[i] != null) {
                cardWalls[i].setOnClickListener(v -> selectWallpaper(index));
                cardWalls[i].setOnFocusChangeListener((v, hasFocus) -> {
                    v.animate().scaleX(hasFocus ? 1.06f : 1.0f).scaleY(hasFocus ? 1.06f : 1.0f).setDuration(140).start();
                    if (hasFocus) {
                        previewWallpaperItem(index);
                    }
                });
            }
        }

        // Step 7: Kids
        setupCardFocus(cardTimeNoLimit, () -> selectKidQuota(0), null);
        setupCardFocus(cardTime60m, () -> selectKidQuota(60), null);
        setupCardFocus(cardTime120m, () -> selectKidQuota(120), null);

        if (cardSetPin != null) {
            setupCardFocus(cardSetPin, this::openPinSetupDialog, null);
        }
    }

    private void onPrimaryActionClicked() {
        if (currentStep == 2 && !isMicGranted()) {
            requestMicrophonePermission();
            return;
        }
        if (currentStep == 3 && !isUsageGranted()) {
            requestUsageStatsPermission();
            return;
        }
        if (currentStep == 4 && !isWifiGranted()) {
            requestWifiPermission();
            return;
        }

        if (selectedMode == MODE_SUPER_LIGHT) {
            if (currentStep == 1) {
                currentStep = 5;
            } else if (currentStep == 5) {
                currentStep = 6;
            } else if (currentStep == 6) {
                currentStep = 8;
            } else {
                finishOnboarding();
                return;
            }
        } else {
            if (currentStep < 8) {
                currentStep++;
            } else {
                finishOnboarding();
                return;
            }
        }
        updateStepUi();
    }

    private boolean isMicGranted() {
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isUsageGranted() {
        return new UsageStatsTracker(activity).hasUsageStatsPermission();
    }

    private boolean isWifiGranted() {
        if (!com.next.tv.launcher.BuildConfig.USE_MANAGE_STORAGE) {
            return true;
        }
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void schedulePermissionCheckPolling() {
        handler.postDelayed(this::updatePermissionBadges, 400);
        handler.postDelayed(this::updatePermissionBadges, 800);
        handler.postDelayed(this::updatePermissionBadges, 1500);
        handler.postDelayed(this::updatePermissionBadges, 2500);
    }

    private void requestMicrophonePermission() {
        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        schedulePermissionCheckPolling();
    }

    private void requestUsageStatsPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(activity, "Không thể mở cài đặt thống kê", Toast.LENGTH_SHORT).show();
            }
        }
        schedulePermissionCheckPolling();
    }

    private void requestWifiPermission() {
        activity.requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, 102);
        schedulePermissionCheckPolling();
    }

    private void selectMode(int mode) {
        this.selectedMode = mode;
        if (checkModeFull != null) checkModeFull.setVisibility(mode == MODE_FULL ? View.VISIBLE : View.GONE);
        if (checkModeBalanced != null) checkModeBalanced.setVisibility(mode == MODE_BALANCED ? View.VISIBLE : View.GONE);
        if (checkModeSuperLight != null) checkModeSuperLight.setVisibility(mode == MODE_SUPER_LIGHT ? View.VISIBLE : View.GONE);
        updateHeroMode(mode);
        updateIndicators();

        // Note: applyUserMode is deferred until finishOnboarding to prevent wasteful reloads during browsing
    }

    private void updateHeroMode(int mode) {
        if (tvHeroModeBadge == null || tvHeroModeSub == null || ivHeroModeIcon == null || heroModeIconFrame == null) return;
        if (mode == MODE_FULL) {
            tvHeroModeBadge.setText("Chế độ Đầy đủ");
            tvHeroModeSub.setText("Trải nghiệm phong phú, toàn diện");
            ivHeroModeIcon.setImageResource(R.drawable.ic_sparkles);
            ivHeroModeIcon.setColorFilter(null);
            heroModeIconFrame.setBackgroundResource(R.drawable.accent_circle_purple);
        } else if (mode == MODE_BALANCED) {
            tvHeroModeBadge.setText("Chế độ Cân bằng");
            tvHeroModeSub.setText("Mượt mà 60fps, tối ưu RAM");
            ivHeroModeIcon.setImageResource(R.drawable.ic_timer);
            ivHeroModeIcon.setColorFilter(0xFFF4F4F5);
            heroModeIconFrame.setBackgroundResource(R.drawable.accent_circle_amber);
        } else {
            tvHeroModeBadge.setText("Chế độ Siêu nhẹ");
            tvHeroModeSub.setText("Chỉ hiện app, tối đa hiệu năng");
            ivHeroModeIcon.setImageResource(R.drawable.ic_leaf);
            ivHeroModeIcon.setColorFilter(null);
            heroModeIconFrame.setBackgroundResource(R.drawable.accent_circle_green);
        }
    }

    private void selectWallpaper(int index) {
        this.selectedWallpaper = index;
        for (int i = 0; i < checkWalls.length; i++) {
            if (checkWalls[i] != null) {
                checkWalls[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
        }
        previewWallpaperItem(index);
    }

    private void previewWallpaperItem(int index) {
        String assetPath = (index >= 0 && index < WALLPAPER_ASSETS.length) ? WALLPAPER_ASSETS[index] : null;

        // 1. Update left hero TV miniature
        if (ivHeroWallpaperLive != null) {
            if (assetPath == null) {
                ivHeroWallpaperLive.setImageDrawable(new ColorDrawable(0xFF09090B));
            } else {
                FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/" + assetPath), ivHeroWallpaperLive, 0);
            }
        }

        // 2. Update real launcher background live
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).previewWallpaper(assetPath);
        }
    }

    private void selectKidQuota(int minutes) {
        KidTimeQuotaTracker.getInstance(activity).setDailyQuotaMinutes(minutes);
        updateKidQuotaChecks(minutes);
    }

    private void updateKidQuotaChecks(int quotaMinutes) {
        if (checkTimeNoLimit != null) checkTimeNoLimit.setVisibility(quotaMinutes == 0 ? View.VISIBLE : View.GONE);
        if (checkTime60m != null) checkTime60m.setVisibility(quotaMinutes == 60 ? View.VISIBLE : View.GONE);
        if (checkTime120m != null) checkTime120m.setVisibility(quotaMinutes == 120 ? View.VISIBLE : View.GONE);
    }

    private void openPinSetupDialog() {
        PinLockDialog dialog = new PinLockDialog(activity, PinLockDialog.MODE_CREATE,
                "Cài đặt mã PIN", "Nhập 4 số bảo vệ",
                new PinLockDialog.PinResultListener() {
                    @Override
                    public void onSuccess() {
                        updatePinStatus();
                    }

                    @Override
                    public void onCancel() {
                        updatePinStatus();
                    }
                });
        dialog.show();
    }

    private void updatePinStatus() {
        if (tvPinOnboardingStatus != null) {
            boolean hasPin = AppLockManager.getInstance(activity).hasPin();
            tvPinOnboardingStatus.setText(hasPin ? "Đã bật bảo vệ 4 số" : "Chưa thiết lập");
        }
    }

    private static int getAppPinPriority(String key, String label) {
        String lowerKey = key != null ? key.toLowerCase(Locale.ROOT) : "";
        String lowerLabel = label != null ? label.toLowerCase(Locale.ROOT) : "";

        // Tier 0: Streaming, TV, Movies, Music (highest priority for ATV)
        if (lowerKey.contains("youtube") || lowerKey.contains("netflix") || lowerKey.contains("stremio")
                || lowerKey.contains("smarttube") || lowerKey.contains("cloudstream") || lowerKey.contains("kodi")
                || lowerKey.contains("phim") || lowerKey.contains("tv360") || lowerKey.contains("vtv")
                || lowerKey.contains("fptplay") || lowerKey.contains("vieon") || lowerKey.contains("mytv")
                || lowerKey.contains("xemtv") || lowerKey.contains("sctv") || lowerKey.contains("zing")
                || lowerKey.contains("spotify") || lowerKey.contains("cherry") || lowerKey.contains("voh")
                || lowerKey.contains("nuvio") || lowerKey.contains("televizo") || lowerKey.contains("hbo")
                || lowerLabel.contains("phim") || lowerLabel.contains("truyền hình") || lowerLabel.contains("tivi")
                || lowerLabel.contains("nhạc") || lowerLabel.contains("video") || lowerLabel.contains("cinema")) {
            return 0;
        }

        // Tier 2: System Settings, Play Store, Assistant, input methods (lowest priority)
        if (lowerKey.contains("settings") || lowerKey.contains("vending") || lowerKey.contains("katniss")
                || lowerKey.contains("systemui") || lowerKey.contains("keyboard") || lowerKey.contains("launcher")
                || lowerKey.contains("droidlogic") || lowerKey.contains("mediatek")
                || lowerLabel.contains("cài đặt") || lowerLabel.contains("ch play") || lowerLabel.contains("cửa hàng")
                || lowerLabel.contains("bàn phím") || lowerLabel.contains("trợ lý")) {
            return 2;
        }

        // Tier 1: User-installed third party apps (browsers, file managers, tools)
        return 1;
    }

    private void loadAppsForPinning() {
        pinAppList.clear();
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            AppAdapter adapter = main.getAdapter();
            if (adapter != null && adapter.movableCount() > 0) {
                for (int i = 0; i < adapter.movableCount(); i++) {
                    String key = adapter.keyAt(i);
                    String label = adapter.labelAt(i);
                    Drawable icon = adapter.artworkAt(i);
                    if (key != null && label != null && !label.isEmpty()) {
                        pinAppList.add(new PinAppItem(key, label, icon));
                    }
                }
            }
        }

        if (pinAppList.isEmpty()) {
            PackageManager pm = activity.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            if (list == null || list.isEmpty()) {
                intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                list = pm.queryIntentActivities(intent, 0);
            }
            if (list != null) {
                for (ResolveInfo info : list) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(activity.getPackageName())) continue;
                    String label = info.loadLabel(pm).toString();
                    Drawable icon = info.loadIcon(pm);
                    ComponentName comp = new ComponentName(pkg, info.activityInfo.name);
                    pinAppList.add(new PinAppItem(comp.flattenToShortString(), label, icon));
                }
            }
        }

        Collections.sort(pinAppList, (a, b) -> {
            int pA = getAppPinPriority(a.key, a.label);
            int pB = getAppPinPriority(b.key, b.label);
            if (pA != pB) {
                return Integer.compare(pA, pB);
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        if (pinAppsAdapter != null) {
            pinAppsAdapter.notifyDataSetChanged();
        }
    }

    private void updateStepUi() {
        // Step containers visibility
        if (step1Container != null) step1Container.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        if (step2Container != null) step2Container.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        if (step3Container != null) step3Container.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        if (step4Container != null) step4Container.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        if (step5Container != null) step5Container.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);
        if (step6Container != null) step6Container.setVisibility(currentStep == 6 ? View.VISIBLE : View.GONE);
        if (step7Container != null) step7Container.setVisibility(currentStep == 7 ? View.VISIBLE : View.GONE);
        if (step8Container != null) step8Container.setVisibility(currentStep == 8 ? View.VISIBLE : View.GONE);

        // Hero stage visibility
        if (heroModeContainer != null) heroModeContainer.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        if (heroMicContainer != null) heroMicContainer.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        if (heroStatsContainer != null) heroStatsContainer.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        if (heroWifiContainer != null) heroWifiContainer.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        if (heroPinAppsContainer != null) heroPinAppsContainer.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);
        if (heroWallpaperContainer != null) heroWallpaperContainer.setVisibility(currentStep == 6 ? View.VISIBLE : View.GONE);
        if (heroPinContainer != null) heroPinContainer.setVisibility(currentStep == 7 ? View.VISIBLE : View.GONE);
        if (heroReadyContainer != null) heroReadyContainer.setVisibility(currentStep == 8 ? View.VISIBLE : View.GONE);

        // Bottom buttons setup
        btnPrev.setVisibility(currentStep > 1 ? View.VISIBLE : View.GONE);
        if (btnSkip != null) {
            btnSkip.setVisibility(currentStep == 8 ? View.GONE : View.VISIBLE);
        }

        // Stepper dots
        updateIndicators();

        // Step-specific typography and button states
        switch (currentStep) {
            case 1:
                tvStepTitle.setText("Chọn chế độ");
                tvStepDesc.setText("Cấu hình phù hợp nhất với nhu cầu sử dụng của bạn");
                btnNext.setText("Tiếp tục");
                btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, activity.getDrawable(R.drawable.ic_arrow_right), null);
                updateHeroMode(selectedMode);
                break;
            case 2:
                tvStepTitle.setText("Quyền Micro");
                tvStepDesc.setText("Cho phép tìm kiếm nội dung bằng giọng nói trực tiếp trên điều khiển");
                updatePermissionBadges();
                break;
            case 3:
                tvStepTitle.setText("Thống kê sử dụng");
                tvStepDesc.setText("Theo dõi thời gian xem TV để quản lý giờ giải trí của gia đình");
                updatePermissionBadges();
                break;
            case 4:
                tvStepTitle.setText("Kết nối Wi-Fi");
                tvStepDesc.setText("Cho phép hiển thị trạng thái kết nối mạng trên thanh thông tin");
                updatePermissionBadges();
                break;
            case 5:
                tvStepTitle.setText("Ghim ứng dụng");
                tvStepDesc.setText("Chọn các ứng dụng thường dùng để đưa lên hàng đầu màn hình chính");
                btnNext.setText("Tiếp tục");
                btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, activity.getDrawable(R.drawable.ic_arrow_right), null);
                updateHeroPinApps();
                break;
            case 6:
                tvStepTitle.setText("Chọn phong cách");
                tvStepDesc.setText("Tùy biến hình nền TV sống động với hiệu ứng xem trước trực tiếp");
                btnNext.setText("Tiếp tục");
                btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, activity.getDrawable(R.drawable.ic_arrow_right), null);
                previewWallpaperItem(selectedWallpaper);
                break;
            case 7:
                tvStepTitle.setText("Chế độ trẻ em");
                tvStepDesc.setText("Cài đặt hạn mức thời gian xem và mã bảo vệ phụ huynh 4 số");
                btnNext.setText("Tiếp tục");
                btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, activity.getDrawable(R.drawable.ic_arrow_right), null);
                updatePinStatus();
                updateKidQuotaChecks(KidTimeQuotaTracker.getInstance(activity).getDailyQuotaMinutes());
                break;
            case 8:
                tvStepTitle.setText("Sẵn sàng sử dụng");
                tvStepDesc.setText("Mọi thiết lập đã hoàn tất, chúc bạn xem TV thật trọn vẹn");
                btnNext.setText("Bắt đầu dùng");
                btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null, activity.getDrawable(R.drawable.ic_check_green), null);
                updateStep8Ui();
                break;
        }

        btnNext.setCompoundDrawableTintList(activity.getColorStateList(R.color.search_pill_text_color));

        // Smart Focus to top element of the new step
        rvPinApps.post(() -> {
            switch (currentStep) {
                case 1:
                    View targetModeCard = (selectedMode == MODE_FULL) ? cardModeFull :
                                          (selectedMode == MODE_BALANCED) ? cardModeBalanced : cardModeSuperLight;
                    if (targetModeCard != null) targetModeCard.requestFocus();
                    break;
                case 2:
                    if (cardPermMic != null) cardPermMic.requestFocus();
                    break;
                case 3:
                    if (cardPermUsage != null) cardPermUsage.requestFocus();
                    break;
                case 4:
                    if (cardPermWifi != null) cardPermWifi.requestFocus();
                    break;
                case 5:
                    if (rvPinApps != null && rvPinApps.getChildCount() > 0) {
                        View first = rvPinApps.getChildAt(0);
                        if (first != null) first.requestFocus();
                    } else if (btnNext != null) {
                        btnNext.requestFocus();
                    }
                    break;
                case 6:
                    if (selectedWallpaper >= 0 && selectedWallpaper < cardWalls.length && cardWalls[selectedWallpaper] != null) {
                        cardWalls[selectedWallpaper].requestFocus();
                    }
                    break;
                case 7:
                    if (cardTimeNoLimit != null) cardTimeNoLimit.requestFocus();
                    break;
                case 8:
                    if (btnNext != null) btnNext.requestFocus();
                    break;
            }
        });
    }

    private void setupMiniDock(LinearLayout container) {
        if (container == null) return;
        container.removeAllViews();
        float density = activity.getResources().getDisplayMetrics().density;
        int iconSize = (int) (28 * density);
        int marginEnd = (int) (6 * density);
        int pad = (int) (3 * density);

        int maxSlots = 5;
        int count = 0;
        for (String key : selectedPinnedKeys) {
            if (count >= maxSlots) break;
            PinAppItem found = null;
            for (PinAppItem item : pinAppList) {
                if (item.key.equals(key)) {
                    found = item;
                    break;
                }
            }
            if (found != null) {
                ImageView iv = new ImageView(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
                lp.setMarginEnd(marginEnd);
                iv.setLayoutParams(lp);
                iv.setBackgroundResource(R.drawable.bg_wallpaper_card_inner);
                iv.setPadding(pad, pad, pad, pad);
                iv.setImageDrawable(found.icon);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                container.addView(iv);
                count++;
            }
        }

        while (count < 4) {
            View empty = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
            lp.setMarginEnd(marginEnd);
            empty.setLayoutParams(lp);
            empty.setBackgroundResource(R.drawable.bg_hero_numpad_key);
            container.addView(empty);
            count++;
        }
    }

    private void updateStep8Ui() {
        if (ivHeroReadyWallpaper != null) {
            if (selectedWallpaper == 0) {
                ivHeroReadyWallpaper.setImageDrawable(new ColorDrawable(0xFF09090B));
            } else if (selectedWallpaper > 0 && selectedWallpaper < WALLPAPER_ASSETS.length) {
                FastPosterLoader.getInstance().load(
                        Uri.parse("file:///android_asset/" + WALLPAPER_ASSETS[selectedWallpaper]),
                        ivHeroReadyWallpaper,
                        0
                );
            }
        }

        if (tvHeroReadyModeBadge != null) {
            if (selectedMode == MODE_FULL) {
                tvHeroReadyModeBadge.setText("ĐẦY ĐỦ");
                tvHeroReadyModeBadge.setTextColor(0xFF22C55E);
                tvHeroReadyModeBadge.setBackgroundResource(R.drawable.badge_perm_granted);
            } else if (selectedMode == MODE_BALANCED) {
                tvHeroReadyModeBadge.setText("CÂN BẰNG");
                tvHeroReadyModeBadge.setTextColor(0xFFF59E0B);
                tvHeroReadyModeBadge.setBackgroundResource(R.drawable.accent_circle_amber);
            } else {
                tvHeroReadyModeBadge.setText("SIÊU GỌN");
                tvHeroReadyModeBadge.setTextColor(0xFF38BDF8);
                tvHeroReadyModeBadge.setBackgroundResource(R.drawable.accent_circle_blue);
            }
        }

        if (rowReadyMiniWidgets != null) {
            rowReadyMiniWidgets.setVisibility(selectedMode == MODE_SUPER_LIGHT ? View.GONE : View.VISIBLE);
        }

        setupMiniDock(containerReadyMiniDock);

        // Mode Features summary with icons
        if (selectedMode == MODE_FULL) {
            if (frameFeature1Icon != null) frameFeature1Icon.setBackgroundResource(R.drawable.accent_circle_purple);
            if (ivFeature1Icon != null) ivFeature1Icon.setImageResource(R.drawable.ic_remote_control);
            if (tvFeature1Title != null) tvFeature1Title.setText("Điều khiển TV từ xa");
            if (tvFeature1Sub != null) tvFeature1Sub.setText("Bàn phím, chuột và remote mượt mà qua trình duyệt điện thoại");

            if (frameFeature2Icon != null) frameFeature2Icon.setBackgroundResource(R.drawable.accent_circle_blue);
            if (ivFeature2Icon != null) ivFeature2Icon.setImageResource(R.drawable.ic_remote_file);
            if (tvFeature2Title != null) tvFeature2Title.setText("Quản lý tệp không dây");
            if (tvFeature2Sub != null) tvFeature2Sub.setText("Truyền tải phim ảnh, nhạc và cài APK không dây từ máy tính");

            if (frameFeature3Icon != null) frameFeature3Icon.setBackgroundResource(R.drawable.accent_circle_green);
            if (ivFeature3Icon != null) ivFeature3Icon.setImageResource(R.drawable.ic_search_voice);
            if (tvFeature3Title != null) tvFeature3Title.setText("Tìm kiếm giọng nói");
            if (tvFeature3Sub != null) tvFeature3Sub.setText("Hỗ trợ tiếng Việt siêu tốc trực tiếp trên điều khiển TV");
        } else if (selectedMode == MODE_BALANCED) {
            if (frameFeature1Icon != null) frameFeature1Icon.setBackgroundResource(R.drawable.accent_circle_amber);
            if (ivFeature1Icon != null) ivFeature1Icon.setImageResource(R.drawable.ic_weather_sun);
            if (tvFeature1Title != null) tvFeature1Title.setText("Tiện ích thiết yếu");
            if (tvFeature1Sub != null) tvFeature1Sub.setText("Thời tiết trực quan, dọn dẹp RAM và dock ứng dụng yêu thích");

            if (frameFeature2Icon != null) frameFeature2Icon.setBackgroundResource(R.drawable.accent_circle_green);
            if (ivFeature2Icon != null) ivFeature2Icon.setImageResource(R.drawable.ic_timer);
            if (tvFeature2Title != null) tvFeature2Title.setText("Khóa 60fps mượt mà");
            if (tvFeature2Sub != null) tvFeature2Sub.setText("Cắt giảm hoạt ảnh thừa, giữ Android TV luôn mát và nhẹ");

            if (frameFeature3Icon != null) frameFeature3Icon.setBackgroundResource(R.drawable.accent_circle_purple);
            if (ivFeature3Icon != null) ivFeature3Icon.setImageResource(R.drawable.ic_search_voice);
            if (tvFeature3Title != null) tvFeature3Title.setText("Tìm kiếm giọng nói");
            if (tvFeature3Sub != null) tvFeature3Sub.setText("Hỗ trợ tiếng Việt siêu tốc trực tiếp trên điều khiển TV");
        } else { // MODE_SUPER_LIGHT
            if (frameFeature1Icon != null) frameFeature1Icon.setBackgroundResource(R.drawable.accent_circle_green);
            if (ivFeature1Icon != null) ivFeature1Icon.setImageResource(R.drawable.ic_clean_ram);
            if (tvFeature1Title != null) tvFeature1Title.setText("Siêu nhẹ & Tiết kiệm RAM");
            if (tvFeature1Sub != null) tvFeature1Sub.setText("Chiếm dưới 40MB RAM, không tải ngầm mạng hay RSS tin tức");

            if (frameFeature2Icon != null) frameFeature2Icon.setBackgroundResource(R.drawable.accent_circle_blue);
            if (ivFeature2Icon != null) ivFeature2Icon.setImageResource(R.drawable.ic_apps);
            if (tvFeature2Title != null) tvFeature2Title.setText("Mở là chạy ngay");
            if (tvFeature2Sub != null) tvFeature2Sub.setText("Giao diện phẳng thuần ứng dụng, phản hồi tức thì");

            if (frameFeature3Icon != null) frameFeature3Icon.setBackgroundResource(R.drawable.accent_circle_purple);
            if (ivFeature3Icon != null) ivFeature3Icon.setImageResource(R.drawable.ic_sparkles);
            if (tvFeature3Title != null) tvFeature3Title.setText("Tự động dọn bộ nhớ");
            if (tvFeature3Sub != null) tvFeature3Sub.setText("Tối ưu dung lượng RAM mỗi khi thoát ứng dụng về trang chủ");
        }
    }

    private void updateHeroPinApps() {
        if (tvHeroPinSub != null) {
            int count = selectedPinnedKeys.size();
            tvHeroPinSub.setText(count > 0 ? "ĐÃ GHIM " + count + " ỨNG DỤNG" : "HÀNG ỨNG DỤNG ĐÃ GHIM");
        }
        setupMiniDock(containerMiniDockIcons);
    }

    private void updatePermissionBadges() {
        if (currentStep == 2) {
            boolean mic = isMicGranted();
            if (badgePermMic != null) {
                badgePermMic.setText(mic ? "Đã cấp" : "Cấp quyền");
                badgePermMic.setTextColor(mic ? 0xFF22C55E : 0xFFF4F4F5);
                badgePermMic.setBackgroundResource(mic ? R.drawable.badge_perm_granted : R.drawable.search_pill_btn_bg);
            }
            btnNext.setText(mic ? "Tiếp tục" : "Cấp quyền");
            btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    activity.getDrawable(mic ? R.drawable.ic_arrow_right : R.drawable.ic_search_voice), null);
        } else if (currentStep == 3) {
            boolean usage = isUsageGranted();
            if (badgePermUsage != null) {
                badgePermUsage.setText(usage ? "Đã cấp" : "Cấp quyền");
                badgePermUsage.setTextColor(usage ? 0xFF22C55E : 0xFFF4F4F5);
                badgePermUsage.setBackgroundResource(usage ? R.drawable.badge_perm_granted : R.drawable.search_pill_btn_bg);
            }
            btnNext.setText(usage ? "Tiếp tục" : "Cấp quyền");
            btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    activity.getDrawable(usage ? R.drawable.ic_arrow_right : R.drawable.ic_clock), null);
        } else if (currentStep == 4) {
            boolean wifi = isWifiGranted();
            if (badgePermWifi != null) {
                badgePermWifi.setText(wifi ? "Đã cấp" : "Cấp quyền");
                badgePermWifi.setTextColor(wifi ? 0xFF22C55E : 0xFFF4F4F5);
                badgePermWifi.setBackgroundResource(wifi ? R.drawable.badge_perm_granted : R.drawable.search_pill_btn_bg);
            }
            btnNext.setText(wifi ? "Tiếp tục" : "Cấp quyền");
            btnNext.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    activity.getDrawable(wifi ? R.drawable.ic_arrow_right : R.drawable.ic_wifi), null);
        }
        btnNext.setCompoundDrawableTintList(activity.getColorStateList(R.color.search_pill_text_color));
    }

    private void updateIndicators() {
        View[] dots = {stepDot1, stepDot2, stepDot3, stepDot4, stepDot5, stepDot6, stepDot7, stepDot8};
        float density = activity.getResources().getDisplayMetrics().density;

        if (selectedMode == MODE_SUPER_LIGHT) {
            // In Super Light, hide skipped permission dots (2, 3, 4) and kid mode dot (7)
            if (stepDot2 != null) stepDot2.setVisibility(View.GONE);
            if (stepDot3 != null) stepDot3.setVisibility(View.GONE);
            if (stepDot4 != null) stepDot4.setVisibility(View.GONE);
            if (stepDot7 != null) stepDot7.setVisibility(View.GONE);

            int[] visibleSteps = {1, 5, 6, 8};
            View[] visibleDots = {stepDot1, stepDot5, stepDot6, stepDot8};
            for (int i = 0; i < visibleDots.length; i++) {
                if (visibleDots[i] != null) {
                    visibleDots[i].setVisibility(View.VISIBLE);
                    boolean isCurrent = visibleSteps[i] == currentStep;
                    visibleDots[i].setBackgroundColor(isCurrent ? 0xFFFFFFFF : 0xFF3F3F46);
                    ViewGroup.LayoutParams lp = visibleDots[i].getLayoutParams();
                    lp.width = (int) ((isCurrent ? 28 : 8) * density);
                    visibleDots[i].setLayoutParams(lp);
                }
            }
        } else {
            for (int i = 0; i < dots.length; i++) {
                if (dots[i] != null) {
                    dots[i].setVisibility(View.VISIBLE);
                    boolean isCurrent = (i + 1) == currentStep;
                    dots[i].setBackgroundColor(isCurrent ? 0xFFFFFFFF : 0xFF3F3F46);
                    ViewGroup.LayoutParams lp = dots[i].getLayoutParams();
                    lp.width = (int) ((isCurrent ? 28 : 8) * density);
                    dots[i].setLayoutParams(lp);
                }
            }
        }
    }

    private void finishOnboarding() {
        // 1. Save selected wallpaper permanently
        String assetPath = (selectedWallpaper >= 0 && selectedWallpaper < WALLPAPER_ASSETS.length) ? WALLPAPER_ASSETS[selectedWallpaper] : null;
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            main.saveWallpaperChoice(assetPath);
            main.applyUserMode(selectedMode);

            // 2. Save pinned apps into Order
            if (!selectedPinnedKeys.isEmpty()) {
                List<String> dockApps = main.getDockApps();
                Order existingOrder = Order.load(Order.file(main));
                List<String> allKeys = new ArrayList<>();
                // Selected pinned apps come first
                for (String k : selectedPinnedKeys) {
                    if (!allKeys.contains(k)) allKeys.add(k);
                }
                // Followed by rest of apps
                if (existingOrder != null && existingOrder.keys != null) {
                    for (String k : existingOrder.keys) {
                        if (!allKeys.contains(k)) allKeys.add(k);
                    }
                } else if (main.getAdapter() != null) {
                    for (String k : main.getAdapter().movableKeys()) {
                        if (!allKeys.contains(k)) allKeys.add(k);
                    }
                }

                int dockedCount = selectedPinnedKeys.size();
                Order newOrder = new Order(Order.dockKey(dockApps), allKeys, dockedCount);
                Order.save(Order.file(main), newOrder);

                if (main.getAdapter() != null) {
                    main.getAdapter().setSavedOrder(Order.ranks(newOrder, dockApps), Order.docked(newOrder, dockApps));
                    main.reloadTiles();
                }
            }
        }

        setCompleted(activity);
        dismiss();
        if (finishedListener != null) {
            finishedListener.onFinished();
        }
    }

    private void setupCardFocus(View card, Runnable onClick, Runnable onFocus) {
        if (card == null) return;
        card.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(140).start();
            if (hasFocus && onFocus != null) {
                onFocus.run();
            }
        });
    }

    // ---- Pinned Apps Support ----------------------------------------------------

    static class PinAppItem {
        final String key;
        final String label;
        final Drawable icon;

        PinAppItem(String key, String label, Drawable icon) {
            this.key = key;
            this.label = label;
            this.icon = icon;
        }
    }

    private class PinAppsAdapter extends RecyclerView.Adapter<PinAppsAdapter.PinAppViewHolder> {
        @NonNull
        @Override
        public PinAppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(activity).inflate(R.layout.item_onboarding_pin_app, parent, false);
            return new PinAppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PinAppViewHolder holder, int position) {
            PinAppItem item = pinAppList.get(position);
            holder.tvLabel.setText(item.label);
            if (item.icon != null) {
                holder.ivIcon.setImageDrawable(item.icon);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_apps);
            }

            int rankIndex = selectedPinnedKeys.indexOf(item.key);
            boolean isSelected = rankIndex >= 0;
            holder.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.tvRank.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            if (isSelected) {
                holder.tvRank.setText("#" + (rankIndex + 1));
            }

            holder.itemView.setOnClickListener(v -> {
                boolean wasSelected = selectedPinnedKeys.contains(item.key);
                if (wasSelected) {
                    selectedPinnedKeys.remove(item.key);
                } else {
                    selectedPinnedKeys.add(item.key);
                }
                for (int i = 0; i < pinAppList.size(); i++) {
                    PinAppItem it = pinAppList.get(i);
                    if (it.key.equals(item.key) || selectedPinnedKeys.contains(it.key)) {
                        notifyItemChanged(i);
                    }
                }
                updateHeroPinApps();
            });

            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.06f : 1.0f).scaleY(hasFocus ? 1.06f : 1.0f).setDuration(140).start();
            });
        }

        @Override
        public int getItemCount() {
            return pinAppList.size();
        }

        class PinAppViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvLabel;
            final TextView tvRank;
            final ImageView ivCheck;

            PinAppViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivPinAppIcon);
                tvLabel = itemView.findViewById(R.id.tvPinAppLabel);
                tvRank = itemView.findViewById(R.id.tvPinRank);
                ivCheck = itemView.findViewById(R.id.ivPinCheck);
            }
        }
    }
}
