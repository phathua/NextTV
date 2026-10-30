package com.next.tv.launcher.search;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.AppIconCleaner;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.security.NativeSecurityBridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import com.next.tv.launcher.widget.NewsDataManager;
import com.next.tv.launcher.widget.FootballDataManager;
import android.widget.FrameLayout;
import com.next.tv.launcher.search.assistant.AssistantSpeechSpeaker;
import com.next.tv.launcher.search.assistant.TvAssistantActionExecutor;
import com.next.tv.launcher.search.assistant.TvAssistantDispatcher;
import com.next.tv.launcher.search.assistant.WidgetAddonGatekeeper;
import com.next.tv.launcher.widget.WeatherDataManager;
import com.next.tv.launcher.widget.WidgetAddon;
import com.next.tv.launcher.widget.WidgetAddonManager;
import com.next.tv.launcher.widget.WidgetProtocolEngine;

public final class GlobalSearchOverlayManager {
    private static final String TAG = "TvHome.Search";
    private static final String PREFS_NAME = "global_search_prefs";
    public static final String KEY_MIC_OVERRIDE = "mic_button_override_enabled";
    public static final String KEY_BRAVE_ENABLED = "search_brave_enabled";

    public static final int FILTER_ALL = 0;
    public static final int FILTER_MOVIES = 1;
    public static final int FILTER_VIDEOS = 2;
    public static final int FILTER_WEB = 3;

    private static final ExecutorService SEARCH_EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "search-worker-" + count.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;
    private WeakReference<Activity> activeActivityRef;

    private final Runnable autoDismissRunnable = () -> {
        if (isShowing) {
            dismiss();
        }
    };

    private void scheduleAutoDismiss(long delayMs) {
        mainHandler.removeCallbacks(autoDismissRunnable);
        mainHandler.postDelayed(autoDismissRunnable, delayMs);
    }

    // 1. Listening Container
    private View searchDismissBackdrop;
    private View searchBottomScrim;
    private View searchListeningContainer;
    private View voiceBubbleContainer;
    private ImageView searchListeningMic;
    private TextView voiceListeningPrompt;
    private TextView voiceListeningSubtitle;
    private View btnListeningRetry;

    // 2. Bottom Results Panel
    private View searchResultsBottomPanel;
    private TextView searchResultHeaderTitle;
    private ViewGroup searchCategoryPills;
    private TextView pillFilterAll;
    private TextView pillFilterMovies;
    private TextView pillFilterVideos;
    private TextView pillFilterWeb;
    private RecyclerView searchHorizontalRecycler;
    private SearchCardAdapter mainAdapter;
    private ImageView searchBottomAssistantIcon;
    private TextView searchBottomQueryText;
    private View searchVoiceRetryBtn;
    private TextView btnSearchInNuvio;
    private TextView btnSearchInStremio;
    private TextView btnSearchInCloudStream;
    private FrameLayout assistantCardContainer;
    private WidgetAddonGatekeeper addonGatekeeper;
    private TvAssistantActionExecutor actionExecutor;

    private float smoothedRmsScale = 1.0f;
    private ObjectAnimator voicePulseAnimX;
    private ObjectAnimator voicePulseAnimY;
    private boolean moviesLoading = false;
    private boolean videosLoading = false;
    private boolean webLoading = false;
    private boolean hasFocusedRealResult = false;
    private WeakReference<View> previousBackgroundFocusRef;
    private WeakReference<ViewGroup> backgroundContainerRef;
    private BroadcastReceiver homeKeyReceiver;

    // Cached search items
    private final List<SearchResultItem> cachedMovies = new ArrayList<>();
    private final List<SearchResultItem> cachedVideos = new ArrayList<>();
    private final List<SearchResultItem> cachedNews = new ArrayList<>();

    private int currentFilter = FILTER_ALL;
    private String currentQuery = "";
    private SpeechRecognizer speechRecognizer;
    private static volatile GlobalSearchOverlayManager sInstance;

    public static GlobalSearchOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (GlobalSearchOverlayManager.class) {
                if (sInstance == null) {
                    sInstance = new GlobalSearchOverlayManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    public GlobalSearchOverlayManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isMicOverrideEnabled() {
        return prefs.getBoolean(KEY_MIC_OVERRIDE, true);
    }

    public void setMicOverrideEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MIC_OVERRIDE, enabled).apply();
    }

    public boolean isShowing() {
        return isShowing;
    }

    public synchronized void show(Context targetContext, boolean startVoiceImmediately) {
        if (isShowing && overlayView != null) {
            AssistantSpeechSpeaker.getInstance(context).stop();
            if (assistantCardContainer != null) {
                assistantCardContainer.removeAllViews();
                assistantCardContainer.setVisibility(View.GONE);
            }
            if (startVoiceImmediately) {
                startVoiceRecognition();
            }
            return;
        }

        try {
            LayoutInflater inflater = LayoutInflater.from(targetContext);
            overlayView = inflater.inflate(R.layout.overlay_global_search, null);

            bindViews(overlayView);
            setupInteractions();

            if (searchDismissBackdrop != null) {
                searchDismissBackdrop.setBackgroundColor(0x66000000);
            }

            if (targetContext instanceof Activity) {
                Activity act = (Activity) targetContext;
                activeActivityRef = new WeakReference<>(act);
                View prevFocus = act.getCurrentFocus();
                previousBackgroundFocusRef = (prevFocus != null) ? new WeakReference<>(prevFocus) : null;
                View bg = act.findViewById(R.id.root);
                if (bg instanceof ViewGroup) {
                    ViewGroup bgGroup = (ViewGroup) bg;
                    backgroundContainerRef = new WeakReference<>(bgGroup);
                    bgGroup.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                } else {
                    backgroundContainerRef = null;
                }
                act.addContentView(overlayView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                activeActivityRef = null;
                previousBackgroundFocusRef = null;
                backgroundContainerRef = null;
                windowManager = (WindowManager) targetContext.getSystemService(Context.WINDOW_SERVICE);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.CENTER;
                windowManager.addView(overlayView, params);
                overlayView.setFocusable(true);
                overlayView.setFocusableInTouchMode(true);
                overlayView.requestFocus();
            }

            isShowing = true;

            if (homeKeyReceiver == null) {
                homeKeyReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                            dismissImmediate();
                        }
                    }
                };
                IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(homeKeyReceiver, filter, Context.RECEIVER_EXPORTED);
                    } else {
                        context.registerReceiver(homeKeyReceiver, filter);
                    }
                } catch (Exception ignored) {}
            }

            if (startVoiceImmediately) {
                if (searchListeningContainer != null) {
                    searchListeningContainer.setAlpha(0f);
                    searchListeningContainer.setTranslationY(24f);
                    searchListeningContainer.animate().alpha(1f).translationY(0f).setDuration(220).start();
                }
                startVoiceRecognition();
            } else {
                if (pillFilterAll != null) {
                    pillFilterAll.requestFocus();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate search overlay: " + e.getMessage(), e);
            dismiss();
        }
    }

    private void bindViews(View root) {
        searchDismissBackdrop = root.findViewById(R.id.searchDismissBackdrop);
        searchBottomScrim = root.findViewById(R.id.searchBottomScrim);

        searchListeningContainer = root.findViewById(R.id.searchListeningContainer);
        voiceBubbleContainer = root.findViewById(R.id.voiceBubbleContainer);
        searchListeningMic = root.findViewById(R.id.searchListeningMic);
        voiceListeningPrompt = root.findViewById(R.id.voiceListeningPrompt);
        voiceListeningSubtitle = root.findViewById(R.id.voiceListeningSubtitle);
        btnListeningRetry = root.findViewById(R.id.btnListeningRetry);

        searchResultsBottomPanel = root.findViewById(R.id.searchResultsBottomPanel);
        searchResultHeaderTitle = root.findViewById(R.id.searchResultHeaderTitle);
        searchCategoryPills = root.findViewById(R.id.searchCategoryPills);
        pillFilterAll = root.findViewById(R.id.pillFilterAll);
        pillFilterMovies = root.findViewById(R.id.pillFilterMovies);
        pillFilterVideos = root.findViewById(R.id.pillFilterVideos);
        pillFilterWeb = root.findViewById(R.id.pillFilterWeb);

        searchHorizontalRecycler = root.findViewById(R.id.searchHorizontalRecycler);
        searchBottomAssistantIcon = root.findViewById(R.id.searchBottomAssistantIcon);
        searchBottomQueryText = root.findViewById(R.id.searchBottomQueryText);
        searchVoiceRetryBtn = root.findViewById(R.id.searchVoiceRetryBtn);
        btnSearchInNuvio = root.findViewById(R.id.btnSearchInNuvio);
        btnSearchInStremio = root.findViewById(R.id.btnSearchInStremio);
        btnSearchInCloudStream = root.findViewById(R.id.btnSearchInCloudStream);
        assistantCardContainer = root.findViewById(R.id.assistantCardContainer);
        if (addonGatekeeper == null) addonGatekeeper = new WidgetAddonGatekeeper(context);
        if (actionExecutor == null) actionExecutor = new TvAssistantActionExecutor(context);

        applyPillOutline(btnListeningRetry);
        applyPillOutline(pillFilterAll);
        applyPillOutline(pillFilterMovies);
        applyPillOutline(pillFilterVideos);
        applyPillOutline(pillFilterWeb);
        applyPillOutline(searchVoiceRetryBtn);
        applyPillOutline(btnSearchInNuvio);
        applyPillOutline(btnSearchInStremio);
        applyPillOutline(btnSearchInCloudStream);

        Context ctx = root.getContext();
        searchHorizontalRecycler.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
        searchHorizontalRecycler.setItemAnimator(null);
        mainAdapter = new SearchCardAdapter(ctx, SearchResultItem.TYPE_MOVIE);
        mainAdapter.setNavigationListener(new SearchCardAdapter.NavigationListener() {
            @Override
            public void onNavigateUpFromCard() {
                if (searchCategoryPills != null) {
                    searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                }
                TextView pill = getActiveFilterPill();
                if (pill != null) pill.requestFocus();
            }

            @Override
            public void onNavigateDownFromCard() {
                View target = getFirstVisibleBottomButton();
                if (target != null) {
                    target.requestFocus();
                }
            }
        });
        searchHorizontalRecycler.setAdapter(mainAdapter);
    }

    private TextView getActiveFilterPill() {
        if (currentFilter == FILTER_MOVIES) return pillFilterMovies;
        if (currentFilter == FILTER_VIDEOS) return pillFilterVideos;
        if (currentFilter == FILTER_WEB) return pillFilterWeb;
        return pillFilterAll;
    }

    private void setupInteractions() {
        overlayView.setFocusable(true);
        overlayView.setFocusableInTouchMode(true);
        overlayView.requestFocus();
        if (overlayView instanceof SearchOverlayRootView) {
            ((SearchOverlayRootView) overlayView).setDismissListener(this::dismiss);
        }
        overlayView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        if (searchDismissBackdrop != null) {
            searchDismissBackdrop.setOnClickListener(v -> dismiss());
        }

        if (btnListeningRetry != null) {
            btnListeningRetry.setOnClickListener(v -> startVoiceRecognition());
            btnListeningRetry.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    btnListeningRetry.setScaleX(1.06f);
                    btnListeningRetry.setScaleY(1.06f);
                } else {
                    btnListeningRetry.setScaleX(1.0f);
                    btnListeningRetry.setScaleY(1.0f);
                }
            });
        }

        if (searchVoiceRetryBtn != null) {
            searchVoiceRetryBtn.setOnClickListener(v -> startVoiceRecognition());
        }

        setupBottomBarKeyNavigation();

        setupFilterPill(pillFilterAll, FILTER_ALL);
        setupFilterPill(pillFilterMovies, FILTER_MOVIES);
        setupFilterPill(pillFilterVideos, FILTER_VIDEOS);
        setupFilterPill(pillFilterWeb, FILTER_WEB);
    }

    private View getFirstVisibleBottomButton() {
        if (searchVoiceRetryBtn != null && searchVoiceRetryBtn.getVisibility() == View.VISIBLE) {
            return searchVoiceRetryBtn;
        }
        if (btnSearchInNuvio != null && btnSearchInNuvio.getVisibility() == View.VISIBLE) {
            return btnSearchInNuvio;
        }
        if (btnSearchInStremio != null && btnSearchInStremio.getVisibility() == View.VISIBLE) {
            return btnSearchInStremio;
        }
        if (btnSearchInCloudStream != null && btnSearchInCloudStream.getVisibility() == View.VISIBLE) {
            return btnSearchInCloudStream;
        }
        return null;
    }

    private void updateBottomActionPills(String query) {
        if (overlayView == null) return;
        PackageManager pm = context.getPackageManager();

        boolean nuvioEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_NUVIO_ENABLED, true);
        boolean stremioEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_STREMIO_ENABLED, true);
        boolean csEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_CLOUDSTREAM_ENABLED, true);

        boolean hasNuvio = false;
        if (nuvioEnabled) {
            try { pm.getPackageInfo("com.nuvio.tv", 0); hasNuvio = true; } catch (Exception ignored) {}
        }

        boolean hasStremio = false;
        if (stremioEnabled) {
            try { pm.getPackageInfo("com.stremio.one", 0); hasStremio = true; } catch (Exception ignored) {}
        }

        boolean hasCloudStream = false;
        if (csEnabled) {
            try { pm.getPackageInfo("com.lagradost.cloudstream3", 0); hasCloudStream = true; } catch (Exception ignored) {}
        }

        AppLockManager lockManager = AppLockManager.getInstance(context);
        boolean isKidMode = lockManager.isKidModeActive();

        if (isKidMode ? lockManager.isRestrictedInKidMode("com.nuvio.tv") : lockManager.isVaultHidden("com.nuvio.tv")) {
            hasNuvio = false;
        }
        if (isKidMode ? lockManager.isRestrictedInKidMode("com.stremio.one") : lockManager.isVaultHidden("com.stremio.one")) {
            hasStremio = false;
        }
        if (isKidMode ? lockManager.isRestrictedInKidMode("com.lagradost.cloudstream3") : lockManager.isVaultHidden("com.lagradost.cloudstream3")) {
            hasCloudStream = false;
        }

        final String safeQuery = (query != null) ? query.trim() : "";

        if (btnSearchInNuvio != null) {
            btnSearchInNuvio.setVisibility(hasNuvio ? View.VISIBLE : View.GONE);
            btnSearchInNuvio.setOnClickListener(v -> {
                Runnable launch = () -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("nuvio:///search?search=" + Uri.encode(safeQuery)));
                        i.setPackage("com.nuvio.tv");
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        dismiss();
                    } catch (Exception ignored) {}
                };
                if (lockManager.isAppLocked("com.nuvio.tv")) {
                    new PinLockDialog(v.getContext(), "Khóa ứng dụng", "Nhập mã để mở Nuvio", new PinLockDialog.PinResultListener() {
                        @Override public void onSuccess() { launch.run(); }
                        @Override public void onCancel() {}
                    }).show();
                } else {
                    launch.run();
                }
            });
        }

        if (btnSearchInStremio != null) {
            btnSearchInStremio.setVisibility(hasStremio ? View.VISIBLE : View.GONE);
            btnSearchInStremio.setOnClickListener(v -> {
                Runnable launch = () -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///search?search=" + Uri.encode(safeQuery)));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        dismiss();
                    } catch (Exception ignored) {}
                };
                if (lockManager.isAppLocked("com.stremio.one")) {
                    new PinLockDialog(v.getContext(), "Khóa ứng dụng", "Nhập mã để mở Stremio", new PinLockDialog.PinResultListener() {
                        @Override public void onSuccess() { launch.run(); }
                        @Override public void onCancel() {}
                    }).show();
                } else {
                    launch.run();
                }
            });
        }

        if (btnSearchInCloudStream != null) {
            btnSearchInCloudStream.setVisibility(hasCloudStream ? View.VISIBLE : View.GONE);
            btnSearchInCloudStream.setOnClickListener(v -> {
                Runnable launch = () -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("cloudstream:///search?query=" + Uri.encode(safeQuery)));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        dismiss();
                    } catch (Exception ignored) {}
                };
                if (lockManager.isAppLocked("com.lagradost.cloudstream3")) {
                    new PinLockDialog(v.getContext(), "Khóa ứng dụng", "Nhập mã để mở CloudStream", new PinLockDialog.PinResultListener() {
                        @Override public void onSuccess() { launch.run(); }
                        @Override public void onCancel() {}
                    }).show();
                } else {
                    launch.run();
                }
            });
        }

        setupBottomBarKeyNavigation();
    }

    private void setupBottomBarKeyNavigation() {
        List<View> visibleButtons = new ArrayList<>();
        if (searchVoiceRetryBtn != null && searchVoiceRetryBtn.getVisibility() == View.VISIBLE) {
            visibleButtons.add(searchVoiceRetryBtn);
        }
        if (btnSearchInNuvio != null && btnSearchInNuvio.getVisibility() == View.VISIBLE) {
            visibleButtons.add(btnSearchInNuvio);
        }
        if (btnSearchInStremio != null && btnSearchInStremio.getVisibility() == View.VISIBLE) {
            visibleButtons.add(btnSearchInStremio);
        }
        if (btnSearchInCloudStream != null && btnSearchInCloudStream.getVisibility() == View.VISIBLE) {
            visibleButtons.add(btnSearchInCloudStream);
        }

        for (int i = 0; i < visibleButtons.size(); i++) {
            final int index = i;
            View btn = visibleButtons.get(i);
            btn.setBackgroundResource(R.drawable.search_pill_btn_bg);
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                btn.setBackgroundResource(R.drawable.search_pill_btn_bg);
                if (hasFocus) {
                    if (btn instanceof TextView) ((TextView) btn).setTextColor(0xFF111111);
                    btn.setScaleX(1.06f);
                    btn.setScaleY(1.06f);
                } else {
                    if (btn instanceof TextView) ((TextView) btn).setTextColor(0xFFF4F4F5);
                    btn.setScaleX(1.0f);
                    btn.setScaleY(1.0f);
                }
            });

            btn.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (mainAdapter != null && mainAdapter.getItemCount() > 0) {
                            focusRecycler(searchHorizontalRecycler);
                        } else {
                            TextView p = getActiveFilterPill();
                            if (p != null) p.requestFocus();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (index > 0) {
                            visibleButtons.get(index - 1).requestFocus();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (index < visibleButtons.size() - 1) {
                            visibleButtons.get(index + 1).requestFocus();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                        dismiss();
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void setupFilterPill(TextView pill, int filter) {
        if (pill == null) return;
        pill.setOnClickListener(v -> setFilter(filter));
        pill.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setFilter(filter);
            }
            updateFilterPillsUI();
        });
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (mainAdapter != null && mainAdapter.getItemCount() > 0) {
                        focusRecycler(searchHorizontalRecycler);
                    } else {
                        View target = getFirstVisibleBottomButton();
                        if (target != null) target.requestFocus();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && pill == pillFilterAll) {
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && pill == pillFilterWeb) {
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });
    }

    private void setFilter(int filter) {
        if (this.currentFilter == filter) {
            updateFilterPillsUI();
            return;
        }
        this.currentFilter = filter;
        updateFilterPillsUI();
        updateDisplayedItemsImmediately();
    }

    private void updateFilterPillsUI() {
        stylePill(pillFilterAll, currentFilter == FILTER_ALL);
        stylePill(pillFilterMovies, currentFilter == FILTER_MOVIES);
        stylePill(pillFilterVideos, currentFilter == FILTER_VIDEOS);
        stylePill(pillFilterWeb, currentFilter == FILTER_WEB);
    }

    private void stylePill(TextView pill, boolean isSelected) {
        if (pill == null) return;
        pill.setSelected(isSelected);
        boolean focused = pill.isFocused();
        pill.setBackgroundResource(R.drawable.search_pill_btn_bg);
        if (focused) {
            pill.setTextColor(0xFF111111);
            pill.setScaleX(1.06f);
            pill.setScaleY(1.06f);
        } else if (isSelected) {
            pill.setTextColor(0xFFFFFFFF);
            pill.setScaleX(1.0f);
            pill.setScaleY(1.0f);
        } else {
            pill.setTextColor(0xFFA1A1AA);
            pill.setScaleX(1.0f);
            pill.setScaleY(1.0f);
        }
    }

    private void applyPillOutline(View v) {
        if (v == null) return;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
        v.setClipToOutline(true);
    }

    private void startVoiceBubblePulse() {
        if (voiceBubbleContainer == null) return;
        stopVoiceBubblePulse();
        voicePulseAnimX = ObjectAnimator.ofFloat(voiceBubbleContainer, "scaleX", 0.95f, 1.08f);
        voicePulseAnimX.setDuration(600);
        voicePulseAnimX.setRepeatCount(ValueAnimator.INFINITE);
        voicePulseAnimX.setRepeatMode(ValueAnimator.REVERSE);

        voicePulseAnimY = ObjectAnimator.ofFloat(voiceBubbleContainer, "scaleY", 0.95f, 1.08f);
        voicePulseAnimY.setDuration(600);
        voicePulseAnimY.setRepeatCount(ValueAnimator.INFINITE);
        voicePulseAnimY.setRepeatMode(ValueAnimator.REVERSE);

        voicePulseAnimX.start();
        voicePulseAnimY.start();
    }

    private void stopVoiceBubblePulse() {
        if (voicePulseAnimX != null) {
            voicePulseAnimX.cancel();
            voicePulseAnimX = null;
        }
        if (voicePulseAnimY != null) {
            voicePulseAnimY.cancel();
            voicePulseAnimY = null;
        }
        if (voiceBubbleContainer != null) {
            voiceBubbleContainer.setScaleX(1.0f);
            voiceBubbleContainer.setScaleY(1.0f);
        }
    }

    private boolean isCurrentFilterLoading() {
        if (currentFilter == FILTER_ALL) {
            return moviesLoading || videosLoading || webLoading;
        } else if (currentFilter == FILTER_MOVIES) {
            return moviesLoading;
        } else if (currentFilter == FILTER_VIDEOS) {
            return videosLoading;
        } else if (currentFilter == FILTER_WEB) {
            return webLoading;
        }
        return false;
    }

    private final Runnable updateDisplayedRunnable = this::doUpdateDisplayedItems;

    private void updateDisplayedItemsImmediately() {
        mainHandler.removeCallbacks(updateDisplayedRunnable);
        doUpdateDisplayedItems();
    }

    private void updateDisplayedItems() {
        mainHandler.removeCallbacks(updateDisplayedRunnable);
        mainHandler.postDelayed(updateDisplayedRunnable, 50);
    }

    private void doUpdateDisplayedItems() {
        if (mainAdapter == null) return;
        List<SearchResultItem> toDisplay = new ArrayList<>();
        if (currentFilter == FILTER_ALL) {
            toDisplay.addAll(cachedMovies);
            toDisplay.addAll(cachedVideos);
            toDisplay.addAll(cachedNews);
        } else if (currentFilter == FILTER_MOVIES) {
            toDisplay.addAll(cachedMovies);
        } else if (currentFilter == FILTER_VIDEOS) {
            toDisplay.addAll(cachedVideos);
        } else if (currentFilter == FILTER_WEB) {
            toDisplay.addAll(cachedNews);
        }

        boolean isKid = AppLockManager.getInstance(context).isKidModeActive();
        List<SearchResultItem> filtered = new ArrayList<>();
        for (SearchResultItem it : toDisplay) {
            if (isKid && it.itemType != SearchResultItem.TYPE_SKELETON) {
                String text = ((it.title != null ? it.title : "") + " " + (it.subtitle != null ? it.subtitle : "")).toLowerCase();
                if (text.contains("18+") || text.contains("sex") || text.contains("kinh dị") || text.contains("horror") || text.contains("adult")) {
                    continue;
                }
            }
            filtered.add(it);
        }
        toDisplay = filtered;

        boolean loading = isCurrentFilterLoading();
        if (loading) {
            if (toDisplay.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    toDisplay.add(SearchResultItem.createSkeleton());
                }
            } else {
                toDisplay.add(SearchResultItem.createSkeleton());
                toDisplay.add(SearchResultItem.createSkeleton());
            }
        }

        boolean hasRealItems = false;
        for (SearchResultItem it : toDisplay) {
            if (it.itemType != SearchResultItem.TYPE_SKELETON) {
                hasRealItems = true;
                break;
            }
        }

        int focusedPos = -1;
        boolean isRecyclerFocused = false;
        if (searchHorizontalRecycler != null) {
            View focusedChild = searchHorizontalRecycler.findFocus();
            if (focusedChild != null) {
                isRecyclerFocused = true;
                View itemView = searchHorizontalRecycler.findContainingItemView(focusedChild);
                if (itemView != null) {
                    focusedPos = searchHorizontalRecycler.getChildAdapterPosition(itemView);
                }
            }
        }

        boolean isOverlayFocused = overlayView != null && overlayView.hasFocus();

        if (isRecyclerFocused && searchCategoryPills != null) {
            searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }

        mainAdapter.setItems(toDisplay);

        int realCount = 0;
        for (SearchResultItem it : toDisplay) {
            if (it.itemType != SearchResultItem.TYPE_SKELETON) {
                realCount++;
            }
        }

        if (toDisplay.isEmpty()) {
            if (searchCategoryPills != null) {
                searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (searchResultHeaderTitle != null) {
                searchResultHeaderTitle.setText("Chưa có kết quả");
            }
            if (!loading) {
                View target = getFirstVisibleBottomButton();
                if (target != null) target.requestFocus();
            }
        } else {
            if (searchResultHeaderTitle != null) {
                if (loading && realCount == 0) {
                    searchResultHeaderTitle.setText("Đang tìm: " + (currentQuery != null ? currentQuery : ""));
                } else if (currentQuery != null && !currentQuery.trim().isEmpty()) {
                    searchResultHeaderTitle.setText("Kết quả cho \"" + currentQuery + "\" (" + realCount + ")");
                } else {
                    searchResultHeaderTitle.setText("Kết quả (" + realCount + ")");
                }
            }

            if (hasRealItems && !hasFocusedRealResult) {
                hasFocusedRealResult = true;
                focusRecycler(searchHorizontalRecycler);
                if (searchCategoryPills != null) {
                    searchCategoryPills.post(() -> searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS));
                }
            } else if (hasRealItems && focusedPos >= 0) {
                final int keepPos = Math.min(focusedPos, toDisplay.size() - 1);
                searchHorizontalRecycler.post(() -> {
                    RecyclerView.ViewHolder vh = searchHorizontalRecycler.findViewHolderForAdapterPosition(keepPos);
                    if (vh != null && vh.itemView != null) {
                        View card = vh.itemView.findViewById(R.id.searchCard);
                        if (card != null && !card.isFocused()) card.requestFocus();
                    }
                    if (searchCategoryPills != null) {
                        searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                    }
                });
            } else if (!isOverlayFocused) {
                if (hasRealItems) {
                    focusRecycler(searchHorizontalRecycler);
                } else if (pillFilterAll != null) {
                    pillFilterAll.requestFocus();
                }
                if (searchCategoryPills != null) {
                    searchCategoryPills.post(() -> searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS));
                }
            } else {
                if (searchCategoryPills != null) {
                    searchCategoryPills.post(() -> searchCategoryPills.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS));
                }
            }
        }
    }

    public void startVoiceRecognition() {
        AssistantSpeechSpeaker.getInstance(context).stop();
        Activity act = (activeActivityRef != null) ? activeActivityRef.get() : MainActivity.activeInstance;
        if (act != null && act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.REQUEST_CODE_AUDIO);
            if (voiceListeningPrompt != null) {
                voiceListeningPrompt.setText("Chưa cấp quyền mic");
                voiceListeningPrompt.setTextColor(0xFFFF453A);
            }
            if (voiceListeningSubtitle != null) {
                voiceListeningSubtitle.setText("Bấm để thử lại");
            }
            if (btnListeningRetry != null) {
                btnListeningRetry.setVisibility(View.VISIBLE);
                btnListeningRetry.requestFocus();
            }
            scheduleAutoDismiss(12_000L);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (voiceListeningPrompt != null) {
                voiceListeningPrompt.setText("Không hỗ trợ giọng nói");
                voiceListeningPrompt.setTextColor(0xFFFF453A);
            }
            scheduleAutoDismiss(6_000L);
            return;
        }

        scheduleAutoDismiss(15_000L);

        mainHandler.post(() -> {
            clearResults();
            if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.GONE);
            if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.VISIBLE);
            if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.VISIBLE);
            if (btnListeningRetry != null) btnListeningRetry.setVisibility(View.GONE);

            if (voiceListeningPrompt != null) {
                voiceListeningPrompt.setText("Tìm kiếm...");
                voiceListeningPrompt.setTextColor(0x99FFFFFF);
            }
            if (voiceListeningSubtitle != null) {
                voiceListeningSubtitle.setText("Thử nói: Batman, Hoạt hình, Ca nhạc...");
            }
            smoothedRmsScale = 1.0f;
            if (voiceBubbleContainer != null) {
                voiceBubbleContainer.setScaleX(1.0f);
                voiceBubbleContainer.setScaleY(1.0f);
            }

            try {
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.stopListening();
                        speechRecognizer.cancel();
                        speechRecognizer.destroy();
                    } catch (Exception ignored) {}
                    speechRecognizer = null;
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        if (voiceListeningPrompt != null) {
                            voiceListeningPrompt.setText("Tìm kiếm...");
                            voiceListeningPrompt.setTextColor(0x99FFFFFF);
                        }
                    }

                    @Override public void onBeginningOfSpeech() {
                        mainHandler.removeCallbacks(autoDismissRunnable);
                        if (voiceListeningPrompt != null) {
                            voiceListeningPrompt.setText("Đang lắng nghe...");
                            voiceListeningPrompt.setTextColor(0xCCFFFFFF);
                        }
                    }

                    @Override public void onRmsChanged(float rmsdB) {
                        if (voiceBubbleContainer != null) {
                            float normalized = Math.max(0f, Math.min((rmsdB + 2f) / 12f, 1f));
                            float targetScale = 1.0f + normalized * 0.28f;
                            smoothedRmsScale = smoothedRmsScale + 0.25f * (targetScale - smoothedRmsScale);
                            voiceBubbleContainer.animate()
                                    .scaleX(smoothedRmsScale)
                                    .scaleY(smoothedRmsScale)
                                    .setDuration(75)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                    .start();
                        }
                    }

                    @Override public void onBufferReceived(byte[] buffer) {}

                    @Override public void onEndOfSpeech() {
                        if (searchResultsBottomPanel != null && searchResultsBottomPanel.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        startVoiceBubblePulse();
                        if (voiceListeningSubtitle != null) {
                            voiceListeningSubtitle.setText("Đang tìm kiếm...");
                            voiceListeningSubtitle.setTextColor(0xFFE4E4E7);
                        }
                        if (voiceListeningPrompt != null) {
                            voiceListeningPrompt.setText("Đang xử lý...");
                            voiceListeningPrompt.setTextColor(0xCCFFFFFF);
                        }
                    }

                    @Override public void onError(int error) {
                        stopVoiceBubblePulse();
                        try {
                            if (speechRecognizer != null) {
                                speechRecognizer.cancel();
                                speechRecognizer.destroy();
                                speechRecognizer = null;
                            }
                        } catch (Exception ignored) {}
                        if (searchResultsBottomPanel != null && searchResultsBottomPanel.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        if (voiceBubbleContainer != null) {
                            voiceBubbleContainer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
                        }
                        if (voiceListeningPrompt != null) {
                            voiceListeningPrompt.setText("Chưa nhận diện được");
                            voiceListeningPrompt.setTextColor(0xCCFFFFFF);
                        }
                        if (voiceListeningSubtitle != null) {
                            voiceListeningSubtitle.setText("Bấm \"Nói lại\" để thử lại");
                        }
                        if (btnListeningRetry != null) {
                            btnListeningRetry.setVisibility(View.VISIBLE);
                            btnListeningRetry.requestFocus();
                        }
                        scheduleAutoDismiss(10_000L);
                    }

                    @Override public void onResults(Bundle results) {
                        stopVoiceBubblePulse();
                        try {
                            if (speechRecognizer != null) {
                                speechRecognizer.cancel();
                                speechRecognizer.destroy();
                                speechRecognizer = null;
                            }
                        } catch (Exception ignored) {}
                        if (searchResultsBottomPanel != null && searchResultsBottomPanel.getVisibility() == View.VISIBLE) {
                            return;
                        }
                        if (voiceBubbleContainer != null) {
                            voiceBubbleContainer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
                        }
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String voiceText = matches.get(0);
                            if (voiceListeningPrompt != null) {
                                voiceListeningPrompt.setText(voiceText);
                                voiceListeningPrompt.setTextColor(0xFFFFFFFF);
                            }
                            executeUniversalSearch(voiceText, true);
                        } else {
                            if (voiceListeningPrompt != null) {
                                voiceListeningPrompt.setText("Chưa nhận diện được");
                                voiceListeningPrompt.setTextColor(0xCCFFFFFF);
                            }
                            if (btnListeningRetry != null) {
                                btnListeningRetry.setVisibility(View.VISIBLE);
                                btnListeningRetry.requestFocus();
                            }
                        }
                    }

                    @Override public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String partial = matches.get(0);
                            if (partial != null && !partial.trim().isEmpty()) {
                                if (voiceListeningPrompt != null) {
                                    voiceListeningPrompt.setText(partial);
                                    voiceListeningPrompt.setTextColor(0x80FFFFFF);
                                }
                            }
                        }
                    }

                    @Override public void onEvent(int eventType, Bundle params) {}
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói để tìm kiếm");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                Log.w(TAG, "SpeechRecognizer failed: " + e.getMessage());
            }
        });
    }

    private void clearResults() {
        cachedMovies.clear();
        cachedVideos.clear();
        cachedNews.clear();
        if (mainAdapter != null) mainAdapter.setItems(new ArrayList<>());
    }

    public void executeUniversalSearch(String query) {
        executeUniversalSearch(query, true);
    }

    public void executeUniversalSearch(String query, boolean autoFocus) {
        if (query == null || query.trim().isEmpty()) return;
        this.currentQuery = query.trim();

        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Exception ignored) {}
        stopVoiceBubblePulse();

        if (searchBottomQueryText != null) {
            searchBottomQueryText.setText(currentQuery);
        }

                this.hasFocusedRealResult = false;

        AppLockManager lockManager = AppLockManager.getInstance(context);
        if (lockManager.isKidModeActive() && isRestrictedQueryInKidMode(currentQuery)) {
            this.moviesLoading = false;
            this.videosLoading = false;
            this.webLoading = false;
            clearResults();
            if (searchResultsBottomPanel != null) {
                searchResultsBottomPanel.setVisibility(View.VISIBLE);
            }
            if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
            if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
            if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
            if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);
            if (assistantCardContainer != null) {
                assistantCardContainer.removeAllViews();
                assistantCardContainer.setVisibility(View.GONE);
            }
            if (searchResultHeaderTitle != null) {
                searchResultHeaderTitle.setText("Nội dung bị giới hạn");
            }
            updateDisplayedItemsImmediately();
            updateBottomActionPills("");
            AssistantSpeechSpeaker.getInstance(context).speak("Nội dung này bị giới hạn trong chế độ trẻ em.");
            return;
        }

        if (processAssistantQuery(currentQuery, autoFocus)) {
            return;
        }

        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }

        this.moviesLoading = true;
        this.videosLoading = true;
        this.webLoading = true;

        clearResults();

        // Switch to bottom panel immediately with skeleton cards
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) {
            boolean wasGone = searchResultsBottomPanel.getVisibility() != View.VISIBLE;
            searchResultsBottomPanel.setVisibility(View.VISIBLE);
            if (wasGone) {
                searchResultsBottomPanel.setAlpha(0f);
                searchResultsBottomPanel.setTranslationY(40f);
                searchResultsBottomPanel.animate().alpha(1f).translationY(0f).setDuration(240).start();
            }
        }
        updateFilterPillsUI();
        updateDisplayedItemsImmediately();

        boolean nuvioEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_NUVIO_ENABLED, true);
        boolean stremioEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_STREMIO_ENABLED, true);
        boolean ytEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_YOUTUBE_ENABLED, true);
        boolean csEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_CLOUDSTREAM_ENABLED, true);
        boolean webEnabled = prefs.getBoolean(SearchSettingsDrawer.KEY_BRAVE_ENABLED, true);

        updateBottomActionPills(currentQuery);

        if (stremioEnabled || nuvioEnabled || csEnabled) {
            fetchTmdbMoviesAndSeries(currentQuery, autoFocus);
        } else {
            moviesLoading = false;
            updateDisplayedItems();
        }

        if (ytEnabled) {
            fetchYoutubeVideos(currentQuery, autoFocus);
        } else {
            videosLoading = false;
            updateDisplayedItems();
        }

        if (webEnabled) {
            fetchBraveNewsProxy(currentQuery, autoFocus);
        } else {
            webLoading = false;
            updateDisplayedItems();
        }
    }

    private boolean isRestrictedQueryInKidMode(String q) {
        if (q == null) return false;
        String lower = q.toLowerCase();
        String[] restricted = {
            "18+", "sex", "người lớn", "kinh dị", "bạo lực", "ma", "kill", "porn", "adult", "horror"
        };
        for (String r : restricted) {
            if (lower.contains(r)) return true;
        }
        return false;
    }

    private void fetchTmdbMoviesAndSeries(String query, boolean autoFocus) {
        SEARCH_EXECUTOR.execute(() -> {
            List<SearchResultItem> movieItems = new ArrayList<>();
            float density = context.getResources().getDisplayMetrics().density;
            PackageManager pm = context.getPackageManager();

            boolean hasNuvio = false;
            try { pm.getPackageInfo("com.nuvio.tv", 0); hasNuvio = true; } catch (Exception ignored) {}

            boolean hasStremio = false;
            try { pm.getPackageInfo("com.stremio.one", 0); hasStremio = true; } catch (Exception ignored) {}

            boolean hasCloudStream = false;
            try { pm.getPackageInfo("com.lagradost.cloudstream3", 0); hasCloudStream = true; } catch (Exception ignored) {}

            Drawable nuvioIcon = AppIconCleaner.getCleanAppIcon(context, "com.nuvio.tv", density);
            Drawable stremioIcon = AppIconCleaner.getCleanAppIcon(context, "com.stremio.one", density);
            Drawable csIcon = AppIconCleaner.getCleanAppIcon(context, "com.lagradost.cloudstream3", density);

            Drawable preferredIcon = hasNuvio ? nuvioIcon : (hasStremio ? stremioIcon : (hasCloudStream ? csIcon : null));

            HttpURLConnection conn = null;
            try {
                String encQuery = URLEncoder.encode(query, "UTF-8");
                String tmdbToken = NativeSecurityBridge.getTmdbTokenSafe(context);
                String reqUrl = "https://api.themoviedb.org/3/search/multi?query=" + encQuery + "&language=vi-VN&include_adult=false";
                URL url = new URL(reqUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
                if (tmdbToken != null && !tmdbToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + tmdbToken);
                }

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    byte[] buf = new byte[8192];
                    StringBuilder sb = new StringBuilder();
                    int read;
                    int totalRead = 0;
                    final int MAX_STREAM_BYTES = 262144; // 256KB cap
                    while ((read = in.read(buf)) != -1 && totalRead < MAX_STREAM_BYTES) {
                        sb.append(new String(buf, 0, read, "UTF-8"));
                        totalRead += read;
                    }
                    in.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("results")) {
                        JSONArray results = json.getJSONArray("results");
                        for (int i = 0; i < results.length() && movieItems.size() < 8; i++) {
                            JSONObject item = results.getJSONObject(i);
                            String mediaType = item.optString("media_type", "");
                            if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) {
                                continue;
                            }

                            int tmdbId = item.optInt("id", 0);
                            if (tmdbId <= 0) continue;

                            String title = item.optString("title", item.optString("name", ""));
                            if (title.isEmpty()) continue;

                            String backdropPath = item.optString("backdrop_path", "");
                            String posterPath = item.optString("poster_path", "");
                            String imageUrl = "";
                            if (!backdropPath.isEmpty()) {
                                imageUrl = "https://image.tmdb.org/t/p/w780" + backdropPath;
                            } else if (!posterPath.isEmpty()) {
                                imageUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
                            }

                            String releaseDate = item.optString("release_date", item.optString("first_air_date", ""));
                            String year = "";
                            if (releaseDate.length() >= 4) {
                                year = releaseDate.substring(0, 4);
                            }

                            double voteAvg = item.optDouble("vote_average", 0.0);
                            String badge = (voteAvg > 0) ? String.format(java.util.Locale.US, "%.1f", voteAvg) : null;
                            boolean isTv = "tv".equals(mediaType);
                            String subtitle = (year.isEmpty() ? "" : year + " • ") + (isTv ? "Phim bộ" : "Phim điện ảnh");

                            Intent launchIntent;
                            Runnable clickRunnable;

                            if (hasNuvio) {
                                String nuvioRouteType = isTv ? "series" : "movie";
                                String deepLink = "nuvio://detail/" + nuvioRouteType + "/tmdb:" + tmdbId;
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
                                intent.setPackage("com.nuvio.tv");
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                launchIntent = intent;
                                clickRunnable = () -> {
                                    try {
                                        context.startActivity(intent);
                                        dismiss();
                                    } catch (Exception e) {
                                        try {
                                            Intent fallback = new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse("nuvio:///search?search=" + encQuery));
                                            fallback.setPackage("com.nuvio.tv");
                                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(fallback);
                                            dismiss();
                                        } catch (Exception ignored) {}
                                    }
                                };
                            } else if (hasStremio) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("stremio:///search?search=" + Uri.encode(title)));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                launchIntent = intent;
                                clickRunnable = () -> {
                                    try {
                                        context.startActivity(intent);
                                        dismiss();
                                    } catch (Exception ignored) {}
                                };
                            } else if (hasCloudStream) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("cloudstream:///search?query=" + Uri.encode(title)));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                launchIntent = intent;
                                clickRunnable = () -> {
                                    try {
                                        context.startActivity(intent);
                                        dismiss();
                                    } catch (Exception ignored) {}
                                };
                            } else {
                                launchIntent = null;
                                clickRunnable = null;
                            }

                            movieItems.add(new SearchResultItem(
                                SearchResultItem.TYPE_MOVIE,
                                title,
                                subtitle,
                                badge,
                                preferredIcon,
                                imageUrl,
                                launchIntent,
                                clickRunnable
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "TMDB search failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }

            if (movieItems.isEmpty()) {
                fetchCinemetaMovies(query, autoFocus);
                return;
            }

            mainHandler.post(() -> {
                moviesLoading = false;
                cachedMovies.clear();
                cachedMovies.addAll(movieItems);
                updateDisplayedItems();
            });
        });
    }

    private void fetchCinemetaMovies(String query, boolean autoFocus) {
        SEARCH_EXECUTOR.execute(() -> {
            List<SearchResultItem> movieItems = new ArrayList<>();
            float density = context.getResources().getDisplayMetrics().density;
            Drawable stremioIcon = AppIconCleaner.getCleanAppIcon(context, "com.stremio.one", density);

            HttpURLConnection conn = null;
            try {
                String encQuery = URLEncoder.encode(query, "UTF-8");
                URL url = new URL("https://v3-cinemeta.strem.io/catalog/movie/top/search=" + encQuery + ".json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    byte[] buf = new byte[8192];
                    StringBuilder sb = new StringBuilder();
                    int read;
                    int totalRead = 0;
                    final int MAX_STREAM_BYTES = 262144; // 256KB cap
                    while ((read = in.read(buf)) != -1 && totalRead < MAX_STREAM_BYTES) {
                        sb.append(new String(buf, 0, read, "UTF-8"));
                        totalRead += read;
                    }
                    in.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("metas")) {
                        JSONArray metas = json.getJSONArray("metas");
                        for (int i = 0; i < Math.min(metas.length(), 6); i++) {
                            JSONObject meta = metas.getJSONObject(i);
                            String id = meta.optString("id", "");
                            String name = meta.optString("name", "");
                            String poster = meta.optString("poster", "");
                            String year = meta.optString("releaseInfo", meta.optString("year", ""));
                            String type = meta.optString("type", "movie");

                            Intent stremioIntent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("stremio:///detail/" + type + "/" + id));
                            stremioIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            movieItems.add(new SearchResultItem(
                                SearchResultItem.TYPE_MOVIE,
                                name,
                                year.isEmpty() ? "Phim điện ảnh" : "Năm " + year,
                                null,
                                stremioIcon,
                                poster,
                                stremioIntent,
                                () -> {
                                    try {
                                        context.startActivity(stremioIntent);
                                        dismiss();
                                    } catch (Exception e) {
                                        try {
                                            Intent fallback = new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse("stremio:///search?search=" + encQuery));
                                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(fallback);
                                            dismiss();
                                        } catch (Exception ignored) {}
                                    }
                                }
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Cinemeta fetch failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }

            mainHandler.post(() -> {
                moviesLoading = false;
                cachedMovies.clear();
                cachedMovies.addAll(movieItems);
                updateDisplayedItems();
            });
        });
    }

    private void fetchYoutubeVideos(String query, boolean autoFocus) {
        SEARCH_EXECUTOR.execute(() -> {
            PackageManager pm = context.getPackageManager();
            float density = context.getResources().getDisplayMetrics().density;

            String detectedYtPkg = "com.google.android.youtube.tv";
            String ytLabel = "YouTube";
            try {
                pm.getPackageInfo("com.liskovsoft.videomanager", 0);
                detectedYtPkg = "com.liskovsoft.videomanager";
                ytLabel = "SmartTube";
            } catch (Exception ignored) {
                try {
                    pm.getPackageInfo("com.teamsmart.videomanager.tv", 0);
                    detectedYtPkg = "com.teamsmart.videomanager.tv";
                    ytLabel = "SmartTube";
                } catch (Exception ignored2) {
                    try {
                        pm.getPackageInfo("com.liskovsoft.smarttubetv.beta", 0);
                        detectedYtPkg = "com.liskovsoft.smarttubetv.beta";
                        ytLabel = "SmartTube";
                    } catch (Exception ignored3) {}
                }
            }

            Drawable ytIcon = AppIconCleaner.getCleanAppIcon(context, detectedYtPkg, density);

            List<SearchResultItem> ytItems = fetchYoutubeInnertubeVideos(query, detectedYtPkg, ytLabel, ytIcon);
            if (ytItems.isEmpty()) {
                ytItems = fetchYoutubeSuggestionsFallback(query, detectedYtPkg, ytLabel, ytIcon);
            }

            final List<SearchResultItem> finalItems = ytItems;
            mainHandler.post(() -> {
                videosLoading = false;
                cachedVideos.clear();
                cachedVideos.addAll(finalItems);
                updateDisplayedItems();
            });
        });
    }

    private List<SearchResultItem> fetchYoutubeInnertubeVideos(String query, String detectedYtPkg, String ytLabel, Drawable ytIcon) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/search?prettyPrint=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", com.next.tv.launcher.util.NetworkConstants.DEFAULT_USER_AGENT);
            conn.setDoOutput(true);

            JSONObject clientObj = new JSONObject();
            clientObj.put("hl", "vi");
            clientObj.put("gl", "VN");
            clientObj.put("clientName", "WEB");
            clientObj.put("clientVersion", "2.20240101.01.00");

            JSONObject contextObj = new JSONObject();
            contextObj.put("client", clientObj);

            JSONObject payload = new JSONObject();
            payload.put("context", contextObj);
            payload.put("query", query);

            byte[] outBytes = payload.toString().getBytes("UTF-8");
            conn.getOutputStream().write(outBytes);

            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                byte[] buf = new byte[16384];
                StringBuilder sb = new StringBuilder();
                int read;
                int totalRead = 0;
                final int MAX_STREAM_BYTES = 262144; // 256KB
                while ((read = in.read(buf)) != -1 && totalRead < MAX_STREAM_BYTES) {
                    sb.append(new String(buf, 0, read, "UTF-8"));
                    totalRead += read;
                }
                in.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject contents = root.optJSONObject("contents");
                if (contents != null) {
                    JSONObject twoCol = contents.optJSONObject("twoColumnSearchResultsRenderer");
                    if (twoCol != null) {
                        JSONObject primary = twoCol.optJSONObject("primaryContents");
                        if (primary != null) {
                            JSONObject sectionList = primary.optJSONObject("sectionListRenderer");
                            if (sectionList != null) {
                                JSONArray secContents = sectionList.optJSONArray("contents");
                                if (secContents != null) {
                                    for (int i = 0; i < secContents.length(); i++) {
                                        JSONObject secItem = secContents.getJSONObject(i);
                                        JSONObject itemSec = secItem.optJSONObject("itemSectionRenderer");
                                        if (itemSec != null) {
                                            JSONArray itemContents = itemSec.optJSONArray("contents");
                                            if (itemContents != null) {
                                                for (int j = 0; j < itemContents.length(); j++) {
                                                    JSONObject c = itemContents.getJSONObject(j);
                                                    JSONObject videoRenderer = c.optJSONObject("videoRenderer");
                                                    if (videoRenderer != null) {
                                                        String videoId = videoRenderer.optString("videoId", "");
                                                        if (videoId.isEmpty()) continue;

                                                        String vTitle = "";
                                                        JSONObject titleObj = videoRenderer.optJSONObject("title");
                                                        if (titleObj != null) {
                                                            JSONArray runs = titleObj.optJSONArray("runs");
                                                            if (runs != null && runs.length() > 0) {
                                                                vTitle = runs.getJSONObject(0).optString("text", "");
                                                            }
                                                        }

                                                        String channel = "";
                                                        JSONObject byline = videoRenderer.optJSONObject("longBylineText");
                                                        if (byline == null) byline = videoRenderer.optJSONObject("shortBylineText");
                                                        if (byline != null) {
                                                            JSONArray runs = byline.optJSONArray("runs");
                                                            if (runs != null && runs.length() > 0) {
                                                                channel = runs.getJSONObject(0).optString("text", "");
                                                            }
                                                        }

                                                        String duration = "";
                                                        JSONObject lengthObj = videoRenderer.optJSONObject("lengthText");
                                                        if (lengthObj != null) {
                                                            duration = lengthObj.optString("simpleText", "");
                                                        }

                                                        String views = "";
                                                        JSONObject viewObj = videoRenderer.optJSONObject("viewCountText");
                                                        if (viewObj != null) {
                                                            views = viewObj.optString("simpleText", "");
                                                        }

                                                        String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
                                                        String subtitle = (channel.isEmpty() ? ytLabel : channel)
                                                                + (views.isEmpty() ? "" : " • " + views);

                                                        Intent ytIntent = new Intent(Intent.ACTION_VIEW,
                                                                Uri.parse("https://www.youtube.com/watch?v=" + videoId));
                                                        ytIntent.setPackage(detectedYtPkg);
                                                        ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                                        items.add(new SearchResultItem(
                                                            SearchResultItem.TYPE_YOUTUBE,
                                                            vTitle,
                                                            subtitle,
                                                            duration.isEmpty() ? null : duration,
                                                            ytIcon,
                                                            thumbUrl,
                                                            ytIntent,
                                                            () -> {
                                                                try {
                                                                    context.startActivity(ytIntent);
                                                                    dismiss();
                                                                } catch (Exception e) {
                                                                    try {
                                                                        Intent webYt = new Intent(Intent.ACTION_VIEW,
                                                                                Uri.parse("https://www.youtube.com/watch?v=" + videoId));
                                                                        webYt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                        context.startActivity(webYt);
                                                                        dismiss();
                                                                    } catch (Exception ignored) {}
                                                                }
                                                            }
                                                        ));

                                                        if (items.size() >= 8) break;
                                                    }
                                                }
                                            }
                                        }
                                        if (items.size() >= 8) break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "Innertube search failed: " + e.getMessage());
        }
        return items;
    }

    private List<SearchResultItem> fetchYoutubeSuggestionsFallback(String query, String detectedYtPkg, String ytLabel, Drawable ytIcon) {
        List<SearchResultItem> ytItems = new ArrayList<>();
        try {
            String encQuery = URLEncoder.encode(query, "UTF-8");
            URL url = new URL("https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + encQuery);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", com.next.tv.launcher.util.NetworkConstants.DEFAULT_USER_AGENT);

            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int read;
                int totalRead = 0;
                final int MAX_STREAM_BYTES = 262144; // 256KB
                while ((read = in.read(buf)) != -1 && totalRead < MAX_STREAM_BYTES) {
                    sb.append(new String(buf, 0, read, "UTF-8"));
                    totalRead += read;
                }
                in.close();

                String respStr = sb.toString();
                int startIdx = respStr.indexOf('[');
                int endIdx = respStr.lastIndexOf(']');
                if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                    String jsonPart = respStr.substring(startIdx, endIdx + 1);
                    JSONArray rootArray = new JSONArray(jsonPart);
                    if (rootArray.length() > 1) {
                        JSONArray items = rootArray.getJSONArray(1);
                        for (int i = 0; i < Math.min(items.length(), 6); i++) {
                            JSONArray suggestion = items.getJSONArray(i);
                            String suggestText = suggestion.getString(0);
                            String iconUrl = null;
                            if (suggestion.length() > 3 && suggestion.get(3) instanceof JSONObject) {
                                JSONObject extra = suggestion.getJSONObject(3);
                                iconUrl = extra.optString("zai", null);
                            }

                            Intent ytIntent = new Intent(Intent.ACTION_SEARCH);
                            ytIntent.setPackage(detectedYtPkg);
                            ytIntent.putExtra("query", suggestText);
                            ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            String targetQuery = suggestText;
                            ytItems.add(new SearchResultItem(
                                SearchResultItem.TYPE_YOUTUBE,
                                suggestText,
                                "Mở " + ytLabel,
                                null,
                                ytIcon,
                                iconUrl,
                                ytIntent,
                                () -> {
                                    try {
                                        context.startActivity(ytIntent);
                                        dismiss();
                                    } catch (Exception e) {
                                        Intent webYt = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(targetQuery)));
                                        webYt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        context.startActivity(webYt);
                                        dismiss();
                                    }
                                }
                            ));
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "YouTube suggest failed: " + e.getMessage());
        }

        if (ytItems.isEmpty()) {
            Intent ytIntent = new Intent(Intent.ACTION_SEARCH);
            ytIntent.setPackage(detectedYtPkg);
            ytIntent.putExtra("query", query);
            ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ytItems.add(new SearchResultItem(
                SearchResultItem.TYPE_YOUTUBE,
                ytLabel,
                "Tìm kiếm: \"" + query + "\"",
                "Video",
                ytIcon,
                null,
                ytIntent,
                () -> {
                    try {
                        context.startActivity(ytIntent);
                        dismiss();
                    } catch (Exception e) {
                        Intent webYt = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                        webYt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(webYt);
                        dismiss();
                    }
                }
            ));
        }
        return ytItems;
    }

    private void fetchBraveNewsProxy(String query, boolean autoFocus) {
        SEARCH_EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                long now = System.currentTimeMillis();
                NativeSecurityBridge.RequestAuthHeaders auth =
                    NativeSecurityBridge.signRequest(context, query, now);

                String baseUrl = NativeSecurityBridge.getProxyBaseUrl(context);
                if (baseUrl == null || baseUrl.isEmpty()) {
                    return;
                }

                String reqUrl = baseUrl + "/v1/search/web?q=" + URLEncoder.encode(query, "UTF-8");
                URL url = new URL(reqUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                conn.setRequestProperty("User-Agent", com.next.tv.launcher.util.NetworkConstants.DEFAULT_USER_AGENT);
                conn.setRequestProperty("X-Client-Timestamp", auth.timestamp);
                conn.setRequestProperty("X-Apk-Signature", auth.apkSignature);
                conn.setRequestProperty("X-Client-Sign", auth.signature);

                int respCode = conn.getResponseCode();
                if (respCode == 200) {
                    InputStream in = conn.getInputStream();
                    byte[] buf = new byte[8192];
                    StringBuilder sb = new StringBuilder();
                    int read;
                    int totalRead = 0;
                    final int MAX_STREAM_BYTES = 262144; // 256KB cap
                    while ((read = in.read(buf)) != -1 && totalRead < MAX_STREAM_BYTES) {
                        sb.append(new String(buf, 0, read, "UTF-8"));
                        totalRead += read;
                    }
                    in.close();

                    JSONObject json = new JSONObject(sb.toString());
                    List<SearchResultItem> newsItems = new ArrayList<>();

                    JSONArray results = null;
                    if (json.has("results")) {
                        results = json.getJSONArray("results");
                    } else if (json.has("web") && json.getJSONObject("web").has("results")) {
                        results = json.getJSONObject("web").getJSONArray("results");
                    } else if (json.has("news") && json.getJSONObject("news").has("results")) {
                        results = json.getJSONObject("news").getJSONArray("results");
                    }

                    if (results != null) {
                        for (int i = 0; i < Math.min(results.length(), 6); i++) {
                            JSONObject item = results.getJSONObject(i);
                            String rawTitle = item.optString("title", "");
                            String itemUrl = item.optString("url", "");
                            String rawDesc = item.optString("description", "");

                            String cleanTitle = Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_LEGACY).toString().trim();
                            String cleanDesc = Html.fromHtml(rawDesc, Html.FROM_HTML_MODE_LEGACY).toString().trim();

                            String host = "";
                            try {
                                if (itemUrl != null && !itemUrl.isEmpty()) {
                                    Uri u = Uri.parse(itemUrl);
                                    host = u.getHost();
                                    if (host != null && host.startsWith("www.")) {
                                        host = host.substring(4);
                                    }
                                }
                            } catch (Exception ignored) {}
                            if (host == null || host.isEmpty()) {
                                host = "Mạng";
                            }

                            String thumbUrl = null;
                            if (item.has("thumbnail")) {
                                JSONObject th = item.optJSONObject("thumbnail");
                                if (th != null) thumbUrl = th.optString("src", null);
                            }
                            if (thumbUrl == null && item.has("profile")) {
                                JSONObject pr = item.optJSONObject("profile");
                                if (pr != null) thumbUrl = pr.optString("img", null);
                            }

                            newsItems.add(new SearchResultItem(
                                SearchResultItem.TYPE_WEB,
                                cleanTitle,
                                cleanDesc,
                                host,
                                null,
                                thumbUrl,
                                null,
                                () -> showReaderDialog(cleanTitle, cleanDesc, itemUrl)
                            ));
                        }
                    }

                    mainHandler.post(() -> {
                        webLoading = false;
                        cachedNews.clear();
                        cachedNews.addAll(newsItems);
                        updateDisplayedItems();
                    });
                } else {
                    mainHandler.post(() -> {
                        webLoading = false;
                        updateDisplayedItems();
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Proxy fetch failed: " + e.getMessage());
                mainHandler.post(() -> {
                    webLoading = false;
                    updateDisplayedItems();
                });
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void focusRecycler(RecyclerView rv) {
        if (rv == null) return;
        rv.post(() -> {
            if (rv.getAdapter() == null || rv.getAdapter().getItemCount() == 0) return;
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(0);
            if (vh != null && vh.itemView != null) {
                View card = vh.itemView.findViewById(R.id.searchCard);
                if (card != null && card.isFocusable()) {
                    card.requestFocus();
                    return;
                }
                vh.itemView.requestFocus();
            } else {
                rv.scrollToPosition(0);
                rv.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(0);
                    if (holder != null && holder.itemView != null) {
                        View card = holder.itemView.findViewById(R.id.searchCard);
                        if (card != null && card.isFocusable()) card.requestFocus();
                        else holder.itemView.requestFocus();
                    }
                }, 50);
            }
        });
    }

    private void showReaderDialog(String title, String desc, String url) {
        if (overlayView == null) return;
        try {
            Context ctx = overlayView.getContext();
            Context dialogContext = (ctx instanceof Activity) ? ctx
                    : new android.view.ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert);

            AlertDialog dialog = new AlertDialog.Builder(dialogContext)
                .setTitle(title)
                .setMessage(desc)
                .setPositiveButton("Mở trình duyệt", (d, w) -> {
                    if (url != null && !url.isEmpty()) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(i);
                            dismiss();
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Đóng", null)
                .create();

            if (!(ctx instanceof Activity) && dialog.getWindow() != null) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            }
            dialog.show();
        } catch (Exception e) {
            if (url != null && !url.isEmpty()) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                    dismiss();
                } catch (Exception ignored) {}
            }
        }
    }

    public void stopVoiceRecognition() {
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                }
            } catch (Exception ignored) {}
        });
    }

    public synchronized void dismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable);
        stopVoiceBubblePulse();
        AssistantSpeechSpeaker.getInstance(context).stop();
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }
        if (!isShowing) return;
        isShowing = false;

        if (searchDismissBackdrop != null) {
            searchDismissBackdrop.setBackgroundColor(0x00000000);
        }

        if (backgroundContainerRef != null) {
            ViewGroup bg = backgroundContainerRef.get();
            if (bg != null) {
                bg.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (previousBackgroundFocusRef != null) {
                View prev = previousBackgroundFocusRef.get();
                if (prev != null && prev.isAttachedToWindow()) {
                    prev.requestFocus();
                }
            }
            backgroundContainerRef = null;
            previousBackgroundFocusRef = null;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Exception ignored) {}

        if (overlayView != null) {
            if (searchResultsBottomPanel != null && searchResultsBottomPanel.getVisibility() == View.VISIBLE) {
                searchResultsBottomPanel.animate().alpha(0f).translationY(60f).setDuration(160)
                    .withEndAction(this::removeOverlayView).start();
            } else if (searchListeningContainer != null && searchListeningContainer.getVisibility() == View.VISIBLE) {
                searchListeningContainer.animate().alpha(0f).translationY(30f).setDuration(160)
                    .withEndAction(this::removeOverlayView).start();
            } else {
                removeOverlayView();
            }
        }
    }

    public synchronized void dismissImmediate() {
        mainHandler.removeCallbacks(autoDismissRunnable);
        stopVoiceBubblePulse();
        AssistantSpeechSpeaker.getInstance(context).stop();
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }
        if (!isShowing) return;
        isShowing = false;
        if (backgroundContainerRef != null) {
            ViewGroup bg = backgroundContainerRef.get();
            if (bg != null) {
                bg.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (previousBackgroundFocusRef != null) {
                View prev = previousBackgroundFocusRef.get();
                if (prev != null && prev.isAttachedToWindow()) {
                    prev.requestFocus();
                }
            }
            backgroundContainerRef = null;
            previousBackgroundFocusRef = null;
        }
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Exception ignored) {}
        removeOverlayView();
    }

    private void removeOverlayView() {
        if (homeKeyReceiver != null) {
            try {
                context.unregisterReceiver(homeKeyReceiver);
            } catch (Exception ignored) {}
            homeKeyReceiver = null;
        }
        try {
            if (overlayView != null) {
                if (windowManager != null) {
                    try {
                        windowManager.removeViewImmediate(overlayView);
                    } catch (Exception ignored) {}
                    windowManager = null;
                } else if (overlayView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlayView.getParent()).removeView(overlayView);
                }
            }
        } catch (Exception ignored) {}
        overlayView = null;
        activeActivityRef = null;
        previousBackgroundFocusRef = null;
        backgroundContainerRef = null;
    }

    // -------------------------------------------------------------
    // Native Voice Assistant Dispatching & Smart Card Execution
    // -------------------------------------------------------------
    private boolean processAssistantQuery(String query, boolean autoFocus) {
        if (query == null || query.trim().isEmpty()) return false;
        if (addonGatekeeper == null) addonGatekeeper = new WidgetAddonGatekeeper(context);
        if (actionExecutor == null) actionExecutor = new TvAssistantActionExecutor(context);

        TvAssistantDispatcher.ParsedIntent intent = TvAssistantDispatcher.dispatch(query);
        if (intent.type == TvAssistantDispatcher.INTENT_MEDIA_FALLBACK) {
            return false;
        }

        // 1. YouTube Targeted Video Search
        if (intent.type == TvAssistantDispatcher.INTENT_YOUTUBE_VIDEO_SEARCH) {
            executeYouTubeVideoAssistantSearch(intent.cleanQuery, autoFocus);
            return true;
        }

        // 2. Direct Movie Search on Nuvio / Stremio / CloudStream
        if (intent.type == TvAssistantDispatcher.INTENT_MOVIE_DIRECT_SEARCH) {
            executeMovieDirectAssistantSearch(intent.cleanQuery, intent.targetApp, autoFocus);
            return true;
        }

        // 3. Smart Info Queries requiring Widget Addons (Weather, Gold, News, Sports)
        if (intent.type == TvAssistantDispatcher.INTENT_WEATHER ||
            intent.type == TvAssistantDispatcher.INTENT_GOLD ||
            intent.type == TvAssistantDispatcher.INTENT_NEWS ||
            intent.type == TvAssistantDispatcher.INTENT_SPORTS) {

            WidgetAddonGatekeeper.RequirementResult req = addonGatekeeper.checkRequirement(intent.type);
            if (!req.isReady()) {
                showAssistantAddonUpsellCard(intent, req);
                return true;
            }

            if (intent.type == TvAssistantDispatcher.INTENT_WEATHER) {
                showAssistantWeatherCard(intent.targetCity);
                return true;
            } else if (intent.type == TvAssistantDispatcher.INTENT_GOLD) {
                showAssistantGoldCard();
                return true;
            } else if (intent.type == TvAssistantDispatcher.INTENT_NEWS) {
                List<NewsDataManager.NewsItem> cached = new NewsDataManager(context).getCachedNews();
                if (cached != null && !cached.isEmpty()) {
                    displayAssistantNews(cached);
                    return true;
                }
                setFilter(FILTER_WEB);
                AssistantSpeechSpeaker.getInstance(context).speak("Dưới đây là các tin thời sự mới nhất hôm nay.");
                return false;
            } else if (intent.type == TvAssistantDispatcher.INTENT_SPORTS) {
                List<FootballDataManager.FootballMatchItem> matches = FootballDataManager.getInstance(context).getCachedMatches();
                if (matches != null && !matches.isEmpty()) {
                    displayAssistantSports(matches);
                    return true;
                }
                setFilter(FILTER_WEB);
                AssistantSpeechSpeaker.getInstance(context).speak("Dưới đây là thông tin thể thao cập nhật mới nhất.");
                return false;
            }
        }

        // 4. System Action Execution (Volume, Bluetooth, App Launch)
        if (intent.type == TvAssistantDispatcher.INTENT_VOLUME) {
            TvAssistantActionExecutor.SystemActionResult res = actionExecutor.executeVolume(intent.volumeAction, intent.volumePercent);
            AssistantSpeechSpeaker.getInstance(context).speak(res.feedbackMessage);
            if (voiceListeningPrompt != null) voiceListeningPrompt.setText(res.feedbackMessage);
            scheduleAutoDismiss(1800L);
            return true;
        }
        if (intent.type == TvAssistantDispatcher.INTENT_BLUETOOTH) {
            TvAssistantActionExecutor.SystemActionResult res = actionExecutor.executeBluetooth(intent.bluetoothAction);
            AssistantSpeechSpeaker.getInstance(context).speak(res.feedbackMessage);
            if (voiceListeningPrompt != null) voiceListeningPrompt.setText(res.feedbackMessage);
            scheduleAutoDismiss(1800L);
            return true;
        }
        if (intent.type == TvAssistantDispatcher.INTENT_APP_LAUNCH) {
            TvAssistantActionExecutor.SystemActionResult res = actionExecutor.executeLaunchApp(intent.cleanQuery);
            AssistantSpeechSpeaker.getInstance(context).speak(res.feedbackMessage);
            if (voiceListeningPrompt != null) voiceListeningPrompt.setText(res.feedbackMessage);
            if (res.success) {
                mainHandler.postDelayed(this::dismissImmediate, 400L);
            } else {
                scheduleAutoDismiss(2500L);
            }
            return true;
        }

        return false;
    }

    private void executeMovieDirectAssistantSearch(String cleanQuery, String targetApp, boolean autoFocus) {
        if (cleanQuery == null || cleanQuery.trim().isEmpty()) return;
        this.currentQuery = cleanQuery.trim();
        if (searchBottomQueryText != null) searchBottomQueryText.setText(currentQuery);
        if (voiceListeningPrompt != null) voiceListeningPrompt.setText(currentQuery);

        PackageManager pm = context.getPackageManager();
        AppLockManager lockManager = AppLockManager.getInstance(context);

        if (targetApp != null) {
            String pkg = null;
            Uri uri = null;
            if ("nuvio".equalsIgnoreCase(targetApp)) {
                pkg = "com.nuvio.tv";
                uri = Uri.parse("nuvio:///search?search=" + Uri.encode(cleanQuery));
            } else if ("stremio".equalsIgnoreCase(targetApp)) {
                pkg = "com.stremio.one";
                uri = Uri.parse("stremio:///search?search=" + Uri.encode(cleanQuery));
            } else if ("cloudstream".equalsIgnoreCase(targetApp)) {
                pkg = "com.lagradost.cloudstream3";
                uri = Uri.parse("cloudstream:///search?query=" + Uri.encode(cleanQuery));
            }

            if (pkg != null) {
                boolean installed = false;
                try {
                    pm.getPackageInfo(pkg, 0);
                    installed = true;
                } catch (Exception ignored) {}

                if (installed) {
                    final Intent directIntent = new Intent(Intent.ACTION_VIEW, uri);
                    if ("com.nuvio.tv".equals(pkg)) directIntent.setPackage(pkg);
                    directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (lockManager.isAppLocked(pkg)) {
                        Activity act = (activeActivityRef != null) ? activeActivityRef.get() : MainActivity.activeInstance;
                        Context dialogCtx = act != null ? act : context;
                        new PinLockDialog(dialogCtx, "Khóa ứng dụng", "Nhập mã để mở " + targetApp, new PinLockDialog.PinResultListener() {
                            @Override
                            public void onSuccess() {
                                try {
                                    context.startActivity(directIntent);
                                    dismiss();
                                } catch (Exception ignored) {}
                            }
                            @Override public void onCancel() {}
                        }).show();
                        AssistantSpeechSpeaker.getInstance(context).speak("Đang mở tìm kiếm phim trên " + targetApp + ".");
                        return;
                    } else {
                        try {
                            context.startActivity(directIntent);
                            dismiss();
                            AssistantSpeechSpeaker.getInstance(context).speak("Đang mở tìm kiếm phim " + cleanQuery + " trên " + targetApp + ".");
                            return;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // If app not installed or no app specified: filter only movies tab on overlay
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);

        if (searchResultsBottomPanel != null) {
            searchResultsBottomPanel.setVisibility(View.VISIBLE);
            searchResultsBottomPanel.setAlpha(1f);
            searchResultsBottomPanel.setTranslationY(0f);
        }

        setFilter(FILTER_MOVIES);
        updateFilterPillsUI();

        this.moviesLoading = true;
        this.videosLoading = false;
        this.webLoading = false;
        this.hasFocusedRealResult = false;

        clearResults();
        updateDisplayedItemsImmediately();
        updateBottomActionPills(currentQuery);

        AssistantSpeechSpeaker.getInstance(context).speak("Đang tìm phim " + currentQuery + ".");
        fetchTmdbMoviesAndSeries(currentQuery, autoFocus);
    }

    private void displayAssistantNews(List<NewsDataManager.NewsItem> list) {
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.VISIBLE);
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }

        List<SearchResultItem> items = new ArrayList<>();
        for (NewsDataManager.NewsItem ni : list) {
            items.add(new SearchResultItem(
                SearchResultItem.TYPE_WEB,
                ni.title,
                ni.source + " • " + NewsDataManager.formatTimeAgo(ni.pubDate),
                ni.category,
                null,
                ni.imageUrl,
                null,
                () -> showReaderDialog(ni.title, ni.source + " - " + ni.category, ni.link)
            ));
        }

        clearResults();
        cachedNews.addAll(items);
        setFilter(FILTER_WEB);
        this.moviesLoading = false;
        this.videosLoading = false;
        this.webLoading = false;
        this.hasFocusedRealResult = false;
        updateDisplayedItemsImmediately();
        updateBottomActionPills("");
        AssistantSpeechSpeaker.getInstance(context).speak("Dưới đây là các tin thời sự mới nhất hôm nay.");
    }

    private void displayAssistantSports(List<FootballDataManager.FootballMatchItem> list) {
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.VISIBLE);
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }

        List<SearchResultItem> items = new ArrayList<>();
        for (FootballDataManager.FootballMatchItem m : list) {
            String title = m.homeTeamName + " " + (m.homeScore != null ? m.homeScore : "")
                    + " - " + (m.awayScore != null ? m.awayScore : "") + " " + m.awayTeamName;
            String subtitle = m.leagueName + " • " + m.statusDetail;
            String badge = m.isLive ? "Trực tiếp" : ("FT".equalsIgnoreCase(m.statusDetail) ? "Kết thúc" : "Sắp đấu");
            String iconUrl = m.homeLogoUrl != null && !m.homeLogoUrl.isEmpty() ? m.homeLogoUrl : m.awayLogoUrl;

            items.add(new SearchResultItem(
                SearchResultItem.TYPE_WEB,
                title,
                subtitle,
                badge,
                null,
                iconUrl,
                null,
                () -> showReaderDialog(title, subtitle + "\n" + badge, "")
            ));
        }

        clearResults();
        cachedNews.addAll(items);
        setFilter(FILTER_WEB);
        this.moviesLoading = false;
        this.videosLoading = false;
        this.webLoading = false;
        this.hasFocusedRealResult = false;
        updateDisplayedItemsImmediately();
        updateBottomActionPills("");
        AssistantSpeechSpeaker.getInstance(context).speak("Dưới đây là thông tin thể thao cập nhật mới nhất.");
    }

    private void executeYouTubeVideoAssistantSearch(String cleanQuery, boolean autoFocus) {
        if (cleanQuery == null || cleanQuery.trim().isEmpty()) return;
        this.currentQuery = cleanQuery.trim();
        if (searchBottomQueryText != null) searchBottomQueryText.setText(currentQuery);
        if (voiceListeningPrompt != null) voiceListeningPrompt.setText(currentQuery);

        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (assistantCardContainer != null) {
            assistantCardContainer.removeAllViews();
            assistantCardContainer.setVisibility(View.GONE);
        }
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.VISIBLE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.VISIBLE);

        if (searchResultsBottomPanel != null) {
            searchResultsBottomPanel.setVisibility(View.VISIBLE);
            searchResultsBottomPanel.setAlpha(1f);
            searchResultsBottomPanel.setTranslationY(0f);
        }

        setFilter(FILTER_VIDEOS);
        updateFilterPillsUI();

        this.moviesLoading = false;
        this.videosLoading = true;
        this.webLoading = false;
        this.hasFocusedRealResult = false;

        clearResults();
        updateDisplayedItemsImmediately();
        updateBottomActionPills(currentQuery);

        AssistantSpeechSpeaker.getInstance(context).speak("Đang tìm video " + currentQuery + " trên YouTube.");
        fetchYoutubeVideos(currentQuery, autoFocus);
    }

    private void showAssistantWeatherCard(String targetCity) {
        if (overlayView == null) return;
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.VISIBLE);
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.GONE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.GONE);
        if (searchResultHeaderTitle != null) searchResultHeaderTitle.setText("Thời tiết");

        if (assistantCardContainer == null) return;
        assistantCardContainer.removeAllViews();
        assistantCardContainer.setVisibility(View.VISIBLE);

        LayoutInflater.from(context).inflate(R.layout.item_assistant_weather_card, assistantCardContainer, true);
        View card = assistantCardContainer.findViewById(R.id.cardAssistantWeather);
        TextView tvTemp = assistantCardContainer.findViewById(R.id.weatherCardTemp);
        TextView tvCity = assistantCardContainer.findViewById(R.id.weatherCardCity);
        TextView tvDesc = assistantCardContainer.findViewById(R.id.weatherCardDesc);
        ImageView ivIcon = assistantCardContainer.findViewById(R.id.weatherCardIcon);
        ViewGroup forecastContainer = assistantCardContainer.findViewById(R.id.weatherCardForecastContainer);

        if (card != null) {
            card.setOnClickListener(v -> {
                try {
                    Intent weatherIntent = new Intent(context, com.next.tv.launcher.weather.WeatherDetailActivity.class);
                    weatherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(weatherIntent);
                    dismiss();
                } catch (Exception ignored) {}
            });
            card.post(() -> card.requestFocus());
        }

        WeatherDataManager wm = new WeatherDataManager(context);
        TvAssistantDispatcher.CityCoordinate coord = (targetCity != null && !targetCity.isEmpty())
                ? TvAssistantDispatcher.getCityCoordinate(targetCity) : null;

        if (coord != null) {
            // Specific city requested
            if (tvTemp != null) tvTemp.setText("--°C");
            if (tvCity != null) tvCity.setText(coord.cityName + " • Hôm nay");
            if (tvDesc != null) tvDesc.setText("Đang tải dữ liệu thời tiết...");

            final String requestedCity = coord.cityName;
            final double lat = coord.lat;
            final double lon = coord.lon;

            SEARCH_EXECUTOR.execute(() -> {
                try {
                    WeatherDataManager.WeatherDetailInfo info = wm.fetchDetailSync(lat, lon, requestedCity);
                    mainHandler.post(() -> {
                        if (assistantCardContainer != null && assistantCardContainer.getVisibility() == View.VISIBLE) {
                            renderWeatherCardData(info, requestedCity, tvTemp, tvCity, tvDesc, ivIcon, forecastContainer);
                            AssistantSpeechSpeaker.getInstance(context).speak("Thời tiết hôm nay tại " + requestedCity + " " + Math.round(info.currentTemp) + " độ C, " + info.description + ".");
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (tvDesc != null) tvDesc.setText("Không thể lấy dữ liệu thời tiết cho " + requestedCity);
                        AssistantSpeechSpeaker.getInstance(context).speak("Không thể tải dữ liệu thời tiết cho " + requestedCity + ".");
                    });
                }
            });
        } else {
            // Current / default location
            WeatherDataManager.WeatherDetailInfo detail = wm.getCachedDetailWeather();
            String displayCity = (detail != null && detail.city != null && !detail.city.isEmpty())
                    ? detail.city : wm.getSavedCityName();

            if (detail != null) {
                renderWeatherCardData(detail, displayCity, tvTemp, tvCity, tvDesc, ivIcon, forecastContainer);
                AssistantSpeechSpeaker.getInstance(context).speak("Thời tiết hôm nay tại " + displayCity + " " + Math.round(detail.currentTemp) + " độ C, " + detail.description + ".");
            } else {
                if (tvTemp != null) tvTemp.setText("--°C");
                if (tvCity != null) tvCity.setText(displayCity + " • Hôm nay");
                if (tvDesc != null) tvDesc.setText("Đang tải dữ liệu thời tiết...");

                final double lat = wm.getSavedLatitude();
                final double lon = wm.getSavedLongitude();
                final String defaultCity = displayCity;

                SEARCH_EXECUTOR.execute(() -> {
                    try {
                        WeatherDataManager.WeatherDetailInfo info = wm.fetchDetailSync(lat, lon, defaultCity);
                        mainHandler.post(() -> {
                            if (assistantCardContainer != null && assistantCardContainer.getVisibility() == View.VISIBLE) {
                                renderWeatherCardData(info, defaultCity, tvTemp, tvCity, tvDesc, ivIcon, forecastContainer);
                                AssistantSpeechSpeaker.getInstance(context).speak("Thời tiết hôm nay tại " + defaultCity + " " + Math.round(info.currentTemp) + " độ C, " + info.description + ".");
                            }
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            if (tvDesc != null) tvDesc.setText("Không thể lấy dữ liệu thời tiết lúc này");
                            AssistantSpeechSpeaker.getInstance(context).speak("Không thể tải thông tin thời tiết.");
                        });
                    }
                });
            }
        }
    }

    private void renderWeatherCardData(WeatherDataManager.WeatherDetailInfo detail, String displayCity,
                                       TextView tvTemp, TextView tvCity, TextView tvDesc,
                                       ImageView ivIcon, ViewGroup forecastContainer) {
        if (detail == null) return;
        if (tvTemp != null) tvTemp.setText(Math.round(detail.currentTemp) + "°C");
        if (tvCity != null) tvCity.setText(displayCity + " • Hôm nay");
        if (tvDesc != null) tvDesc.setText(detail.description + " • Độ ẩm " + detail.humidity + "% • Gió " + Math.round(detail.windSpeed) + " km/h");
        if (ivIcon != null) ivIcon.setImageResource(getAssistantWeatherIconRes(detail.weatherCode));

        if (forecastContainer != null && detail.dailyForecast != null) {
            forecastContainer.removeAllViews();
            float density = context.getResources().getDisplayMetrics().density;
            int count = Math.min(5, detail.dailyForecast.size());
            for (int i = 0; i < count; i++) {
                WeatherDataManager.DetailDayForecast day = detail.dailyForecast.get(i);
                android.widget.LinearLayout dayCol = new android.widget.LinearLayout(context);
                dayCol.setOrientation(android.widget.LinearLayout.VERTICAL);
                dayCol.setGravity(android.view.Gravity.CENTER);
                dayCol.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
                dayCol.setBackgroundResource(R.drawable.search_pill_btn_bg);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                if (i > 0) { lp.setMarginStart((int) (8 * density)); lp.leftMargin = (int) (8 * density); }
                dayCol.setLayoutParams(lp);

                TextView tvDayName = new TextView(context);
                tvDayName.setText(day.dayName);
                tvDayName.setTextColor(0xFFFFFFFF);
                tvDayName.setTextSize(12f);
                tvDayName.setGravity(android.view.Gravity.CENTER);
                dayCol.addView(tvDayName);

                ImageView ivDay = new ImageView(context);
                ivDay.setImageResource(getAssistantWeatherIconRes(day.weatherCode));
                android.widget.LinearLayout.LayoutParams imgLp = new android.widget.LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density));
                imgLp.topMargin = (int) (4 * density);
                imgLp.bottomMargin = (int) (4 * density);
                ivDay.setLayoutParams(imgLp);
                dayCol.addView(ivDay);

                TextView tvDayTemp = new TextView(context);
                tvDayTemp.setText(day.minTemp + "°/" + day.maxTemp + "°");
                tvDayTemp.setTextColor(0xFFA1A1AA);
                tvDayTemp.setTextSize(11f);
                tvDayTemp.setGravity(android.view.Gravity.CENTER);
                dayCol.addView(tvDayTemp);

                forecastContainer.addView(dayCol);
            }
        }
    }

    private void showAssistantGoldCard() {
        if (overlayView == null) return;
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.VISIBLE);
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.GONE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.GONE);
        if (searchResultHeaderTitle != null) searchResultHeaderTitle.setText("Giá vàng");

        if (assistantCardContainer == null) return;
        assistantCardContainer.removeAllViews();
        assistantCardContainer.setVisibility(View.VISIBLE);

        LayoutInflater.from(context).inflate(R.layout.item_assistant_gold_card, assistantCardContainer, true);
        View card = assistantCardContainer.findViewById(R.id.cardAssistantGold);
        TextView tvSjcPrice = assistantCardContainer.findViewById(R.id.goldSjcPrice);
        TextView tvSjcSub = assistantCardContainer.findViewById(R.id.goldSjcSub);
        TextView tvRingPrice = assistantCardContainer.findViewById(R.id.goldRingPrice);
        TextView tvRingSub = assistantCardContainer.findViewById(R.id.goldRingSub);
        TextView tvWorldPrice = assistantCardContainer.findViewById(R.id.goldWorldPrice);
        TextView tvWorldSub = assistantCardContainer.findViewById(R.id.goldWorldSub);

        if (card != null) {
            card.post(() -> card.requestFocus());
        }

        JSONObject cachedBatch = WidgetProtocolEngine.getInstance(context).getCachedBatchJson();
        JSONObject cachedGold = cachedBatch != null ? cachedBatch.optJSONObject("gold") : null;
        if (cachedGold != null) {
            renderGoldCardData(cachedGold, tvSjcPrice, tvSjcSub, tvRingPrice, tvRingSub, tvWorldPrice, tvWorldSub, true);
        } else {
            if (tvSjcPrice != null) tvSjcPrice.setText("Đang tải...");
            if (tvSjcSub != null) tvSjcSub.setText("Đang lấy giá vàng trực tiếp...");
            if (tvRingPrice != null) tvRingPrice.setText("Đang tải...");
            if (tvRingSub != null) tvRingSub.setText("Đang lấy giá vàng nhẫn...");
            if (tvWorldPrice != null) tvWorldPrice.setText("Đang tải...");
            if (tvWorldSub != null) tvWorldSub.setText("Đang cập nhật sàn thế giới...");
        }

        SEARCH_EXECUTOR.execute(() -> {
            JSONObject freshWidgets = WidgetProtocolEngine.getInstance(context).fetchBatchSync(null);
            JSONObject freshGold = freshWidgets != null ? freshWidgets.optJSONObject("gold") : null;
            if (freshGold != null) {
                mainHandler.post(() -> {
                    if (assistantCardContainer != null && assistantCardContainer.getVisibility() == View.VISIBLE) {
                        renderGoldCardData(freshGold, tvSjcPrice, tvSjcSub, tvRingPrice, tvRingSub, tvWorldPrice, tvWorldSub, cachedGold == null);
                    }
                });
            } else if (cachedGold == null) {
                mainHandler.post(() -> {
                    if (tvSjcSub != null) tvSjcSub.setText("Không thể lấy dữ liệu giá vàng lúc này");
                    AssistantSpeechSpeaker.getInstance(context).speak("Không thể tải thông tin giá vàng vào lúc này.");
                });
            }
        });
    }

    private void renderGoldCardData(JSONObject goldObj, TextView tvSjcPrice, TextView tvSjcSub,
                                    TextView tvRingPrice, TextView tvRingSub,
                                    TextView tvWorldPrice, TextView tvWorldSub,
                                    boolean shouldSpeak) {
        double sjcSell = goldObj.optDouble("rawTemp", 0.0);
        double sjcBuy = 0.0;
        double ringSell = 0.0;
        double ringBuy = 0.0;
        int worldPrice = 0;

        JSONArray subs = goldObj.optJSONArray("subItems");
        if (subs != null) {
            for (int i = 0; i < subs.length(); i++) {
                JSONObject sub = subs.optJSONObject(i);
                if (sub == null) continue;
                String title = sub.optString("title", "");
                double temp = sub.optDouble("temp", 0.0);
                if (title.contains("SJC Mua")) {
                    sjcBuy = temp;
                } else if (title.contains("SJC Bán")) {
                    sjcSell = temp;
                } else if (title.contains("Nhẫn") && title.contains("Mua")) {
                    ringBuy = temp;
                } else if (title.contains("Nhẫn") && title.contains("Bán")) {
                    ringSell = temp;
                } else if (title.contains("Thế Giới") || title.contains("World")) {
                    worldPrice = (int) Math.round(temp);
                }
            }
        }

        if (sjcSell > 0) {
            if (tvSjcPrice != null) tvSjcPrice.setText(sjcSell + " tr/lượng");
            if (tvSjcSub != null) tvSjcSub.setText("Mua: " + (sjcBuy > 0 ? sjcBuy : sjcSell - 2) + " tr • Bán: " + sjcSell + " tr");
        }
        if (ringSell > 0) {
            if (tvRingPrice != null) tvRingPrice.setText(ringSell + " tr/lượng");
            if (tvRingSub != null) tvRingSub.setText("Mua: " + (ringBuy > 0 ? ringBuy : ringSell - 1.5) + " tr • Bán: " + ringSell + " tr");
        } else {
            if (tvRingPrice != null) tvRingPrice.setText("-- tr/lượng");
            if (tvRingSub != null) tvRingSub.setText("Chưa có dữ liệu vàng nhẫn");
        }
        if (worldPrice > 0) {
            if (tvWorldPrice != null) tvWorldPrice.setText(worldPrice + " USD/oz");
            if (tvWorldSub != null) tvWorldSub.setText("Biến động thị trường 24h");
        } else {
            if (tvWorldPrice != null) tvWorldPrice.setText("-- USD/oz");
            if (tvWorldSub != null) tvWorldSub.setText("Thị trường quốc tế");
        }

        if (shouldSpeak && sjcSell > 0) {
            String speech = "Giá vàng SJC hôm nay bán ra " + sjcSell + " triệu đồng một lượng"
                    + (sjcBuy > 0 ? ", mua vào " + sjcBuy + " triệu." : ".");
            AssistantSpeechSpeaker.getInstance(context).speak(speech);
        }
    }

    private void showAssistantAddonUpsellCard(TvAssistantDispatcher.ParsedIntent intent, WidgetAddonGatekeeper.RequirementResult req) {
        if (overlayView == null) return;
        if (searchListeningContainer != null) searchListeningContainer.setVisibility(View.GONE);
        if (searchBottomScrim != null) searchBottomScrim.setVisibility(View.GONE);
        if (searchResultsBottomPanel != null) searchResultsBottomPanel.setVisibility(View.VISIBLE);
        if (searchCategoryPills != null) searchCategoryPills.setVisibility(View.GONE);
        if (searchHorizontalRecycler != null) searchHorizontalRecycler.setVisibility(View.GONE);
        if (searchResultHeaderTitle != null) searchResultHeaderTitle.setText("Không tìm thấy kết quả");

        if (assistantCardContainer == null) return;
        assistantCardContainer.removeAllViews();
        assistantCardContainer.setVisibility(View.VISIBLE);

        LayoutInflater.from(context).inflate(R.layout.item_assistant_addon_upsell_card, assistantCardContainer, true);
        TextView tvTitle = assistantCardContainer.findViewById(R.id.tvUpsellTitle);
        TextView tvDesc = assistantCardContainer.findViewById(R.id.tvUpsellDesc);
        TextView btnInstall = assistantCardContainer.findViewById(R.id.btnInstallAddon);

        if (tvTitle != null) tvTitle.setText("Cần cài đặt tiện ích " + req.displayName);
        if (tvDesc != null) tvDesc.setText("Bạn cần cài đặt tiện ích " + req.displayName + " từ Cửa hàng tiện ích để sử dụng tính năng này.");

        String btnText = (req.status == WidgetAddonGatekeeper.STATUS_DISABLED ? "Bật tiện ích " + req.displayName : "Cài đặt tiện ích " + req.displayName);
        if (btnInstall != null) {
            btnInstall.setText(btnText);
            btnInstall.setOnClickListener(v -> {
                btnInstall.setText("Đang cài đặt...");
                btnInstall.setEnabled(false);
                addonGatekeeper.installOrEnableAddon(req.addonId, (success, message) -> mainHandler.post(() -> {
                    if (success) {
                        btnInstall.setText("Đã kích hoạt");
                        AssistantSpeechSpeaker.getInstance(context).speak("Đã cài đặt tiện ích " + req.displayName + ".");
                        mainHandler.postDelayed(() -> {
                            if (intent.type == TvAssistantDispatcher.INTENT_WEATHER) {
                                showAssistantWeatherCard(intent.targetCity);
                            } else if (intent.type == TvAssistantDispatcher.INTENT_GOLD) {
                                showAssistantGoldCard();
                            } else {
                                executeUniversalSearch(currentQuery, true);
                            }
                        }, 400L);
                    } else {
                        btnInstall.setEnabled(true);
                        btnInstall.setText("Thử lại");
                    }
                }));
            });
            btnInstall.post(() -> btnInstall.requestFocus());
        }

        AssistantSpeechSpeaker.getInstance(context).speak("Bạn chưa cài tiện ích " + req.displayName + ". Bấm chọn để cài đặt.");
    }

    private int getAssistantWeatherIconRes(int code) {
        if (code == 0) return R.drawable.ic_weather_sun;
        if (code >= 1 && code <= 3) return R.drawable.ic_weather_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_weather_cloud;
        if (code >= 51 && code <= 67) return R.drawable.ic_weather_rain;
        if (code >= 71 && code <= 77) return R.drawable.ic_weather_rain;
        if (code >= 80 && code <= 82) return R.drawable.ic_weather_rain;
        if (code >= 95 && code <= 99) return R.drawable.ic_weather_bolt;
        return R.drawable.ic_weather_cloud;
    }
}
