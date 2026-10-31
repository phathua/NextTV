package com.next.tv.launcher;

import com.next.tv.launcher.apps.AppAdapter;
import com.next.tv.launcher.apps.AppLibraryDialog;
import com.next.tv.launcher.apps.CardFeature;
import com.next.tv.launcher.apps.Cards;
import com.next.tv.launcher.apps.LibraryApps;
import com.next.tv.launcher.apps.Order;
import com.next.tv.launcher.apps.TileRefresh;
import com.next.tv.launcher.calendar.RaceWeekend;
import com.next.tv.launcher.calendar.Shamsi;
import com.next.tv.launcher.calendar.VietnameseLunarCalendar;
import com.next.tv.launcher.clock.ClockActivity;
import com.next.tv.launcher.festive.Festival;
import com.next.tv.launcher.rec.MoviePosterAdapter;
import com.next.tv.launcher.rec.SectionConfig;
import com.next.tv.launcher.rec.TvRecommendationReader;
import com.next.tv.launcher.service.HomeKeyService;
import com.next.tv.launcher.service.ServiceRepair;
import com.next.tv.launcher.settings.InputsRequestActivity;
import com.next.tv.launcher.settings.LauncherSettingsActivity;
import com.next.tv.launcher.settings.QuickSettingsActivity;
import com.next.tv.launcher.settings.SystemSettings;
import com.next.tv.launcher.util.FastPosterLoader;
import com.next.tv.launcher.utility.UtilitySectionAdapter;
import com.next.tv.launcher.view.GlassView;
import com.next.tv.launcher.view.HorizontalSectionRecyclerView;
import com.next.tv.launcher.view.Motion;
import com.next.tv.launcher.view.SkyView;
import com.next.tv.launcher.view.TopFade;
import com.next.tv.launcher.view.VpnBadge;
import com.next.tv.launcher.wallpaper.WallpaperPlayer;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ChangedPackages;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import com.next.tv.launcher.search.GlobalSearchOverlayManager;
import com.next.tv.launcher.search.SearchSettingsDrawer;
import com.next.tv.launcher.controller.AppReorderController;
import com.next.tv.launcher.controller.BackgroundTaskExecutor;
import com.next.tv.launcher.controller.FestiveSkinController;
import com.next.tv.launcher.controller.GlassFocusPresenter;
import com.next.tv.launcher.controller.HeroAmbientPresenter;
import com.next.tv.launcher.controller.RaceWeekendPresenter;
import com.next.tv.launcher.controller.SectionManagementPresenter;
import com.next.tv.launcher.controller.SystemStatsMonitor;
import com.next.tv.launcher.controller.TvInputController;
import com.next.tv.launcher.controller.TvRecommendationPresenter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.SoundEffectConstants;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.database.ContentObserver;

import com.next.tv.launcher.widget.WeatherDataManager;
import com.next.tv.launcher.widget.NewsDataManager;
import com.next.tv.launcher.widget.YouTubeTrendingManager;
import com.next.tv.launcher.widget.UsageStatsTracker;
import com.next.tv.launcher.widget.MediaSessionTracker;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.KidTimeQuotaTracker;
import com.next.tv.launcher.lock.KidTimeUpDialog;
import com.next.tv.launcher.lock.PinLockDialog;
import com.next.tv.launcher.lock.RemoteLockServer;
import com.next.tv.launcher.remote.KeyRemapManager;
import com.next.tv.launcher.remote.RemoteControlDrawer;
import com.next.tv.launcher.remote.RemoteFileDrawer;
import com.next.tv.launcher.wallpaper.WallpaperPickerDrawer;
import com.next.tv.launcher.widget.WidgetSectionAdapter;
import com.next.tv.launcher.clean.BackgroundAppCleaner;
import com.next.tv.launcher.utility.AdvancedSettingsDrawer;
import com.next.tv.launcher.widget.WidgetManagerDrawer;
import com.next.tv.launcher.widget.WidgetConfigManager;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private static final int LAUNCHER_SETTINGS_REQUEST = 42;
    /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
    public static final String ACTION_LAUNCHER_SETTINGS = "com.next.tv.launcher.action.LAUNCHER_SETTINGS";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    public static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private boolean rtlAppGrid;
    private static final long MINUTE_MS = 60_000L;
    /**
     * 2025-01-01. TVs without a clock battery boot at the epoch (08:00 in China) and keep it until network
     * time arrives; the clock, date and greeting stay hidden before then.
     */
    private static final long TIME_VALID_AFTER_MS = 1_735_689_600_000L;
    private String liveTvPkg;
    /** Launcher settings' change count this Home was built with (see onResume). */
    private long settingsRevision;
    /** Idle time on Home before ClockActivity; 0 is off (config.xml, home_idle_clock_minutes). */
    private long idleClockMs;
    private final Runnable showIdleClock = () -> startActivity(new Intent(this, ClockActivity.class));
    private String liveTvAction;
    private String liveTvInputId;
    /** The app library is on (config.xml, app_library): only then can the tile menu take an app off Home. */
    private boolean appLibraryEnabled;
    /** The same opt-in menu is available from either hold-OK or the remote's Menu key. */
    private boolean tileMenuEnabled;
    private boolean tileMenuJustTriggered;
    private String inputAction;
    private String inputUriScheme;
    private String sourcePanelService;
    private String sourcePanelAction;
    private String sourcePanelExtra;
    private boolean coocaaSources;

    private AppAdapter adapter;
    private RecyclerView grid;
    private ScrollView mainScroll;
    private HorizontalSectionRecyclerView widgetRecyclerView;
    private TextView widgetSectionTitle;
    private WidgetSectionAdapter widgetAdapter;
    private com.next.tv.launcher.onboarding.OnboardingDialog onboardingDialog;
    private WeatherDataManager weatherManager;
    private NewsDataManager newsManager;
    private YouTubeTrendingManager youtubeManager;
    private UsageStatsTracker usageTracker;
    private MediaSessionTracker mediaTracker;
    private com.next.tv.launcher.widget.FootballDataManager footballManager;
    private AppLockManager appLockManager;
    private RemoteLockServer remoteLockServer;
    private HorizontalSectionRecyclerView utilityRecyclerView;
    private TextView utilitySectionTitle;
    private UtilitySectionAdapter utilityAdapter;
    private RemoteControlDrawer remoteControlDrawer;
    private RemoteFileDrawer remoteFileDrawer;
    private RecyclerView.RecycledViewPool sharedPool;
    private RecyclerView.RecycledViewPool moviePool;
    private LinearLayout movieSectionsContainer;
    private SectionConfig sectionConfig;
    private BackgroundTaskExecutor bgExecutor;
    private Handler recHandler;
    private SystemStatsMonitor statsMonitor;
    private RaceWeekendPresenter racePresenter;
    private FestiveSkinController festiveController;
    private TvInputController inputController;
    private TvRecommendationPresenter recPresenter;
    private SectionManagementPresenter sectionPresenter;
    private GlassFocusPresenter glassFocusPresenter;
    private HeroAmbientPresenter heroAmbientPresenter;
    private AppReorderController appReorderController;
    public static final int REORDER_ACCENT = GlassFocusPresenter.REORDER_ACCENT;
    private FrameLayout root;
    private WallpaperPlayer wallpaperPlayer;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    private View hero;
    private TextView heroMeta;
    private LinearLayout heroDots;
    private ImageView heroArt;
    private boolean animations;
    private boolean advancedEffects;
    private TextView greeting;
    private View clockBlock;
    private View btnInputs;
    private TextView btnSettings;
    private View btnBluetooth;
    private View btnGlobalSearch;
    private View appsSectionTitle;
    private GlobalSearchOverlayManager searchOverlayManager;
    private SearchSettingsDrawer searchSettingsDrawer;
    private AdvancedSettingsDrawer advancedSettingsDrawer;
    private WidgetManagerDrawer widgetManagerDrawer;
    private com.next.tv.launcher.clean.MemoryCleanerDrawer memoryCleanerDrawer;
    private WallpaperPickerDrawer wallpaperPickerDrawer;
    private com.next.tv.launcher.widget.WidgetAddonManager.OnAddonsChangedListener widgetAddonListener;
    private int currentUserMode = -1;
    public static boolean isResumed = false;
    public static MainActivity activeInstance = null;
    public static final int REQUEST_CODE_AUDIO = 2001;
    public static final String PERMISSION_READ_TV = "android.permission.READ_TV_LISTINGS";
    public static final String PERMISSION_READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA";
    public static final int REQUEST_CODE_EPG = 103;
    /** config show_inputs_pill: false keeps the Inputs pill hidden even when the TV has inputs. */
    private boolean showInputsPill = true;
    private TextView btnWifi;
    /** The network pill's icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private int wifiIcon;
    /** The pills have been filled in once; later resumes refresh them after the first frame. */
    private boolean pillsShown;
    private TextView shamsiDate;
    private TextView lunarDate;
    private TextView stats;
    private List<RaceWeekend.Race> raceCalendar;
    private View racePanel;
    private TextView raceKicker, raceHeadline, raceSessions;
    /** config.xml show_stats, overridable by a theme. Off: the line is gone and nothing is read. */
    private boolean showStats;
    private boolean shamsi;
    /** greeting_name: said after the greeting, which is shown in capitals. */
    private String greetingName = "";
    /** Optional time-of-day backdrop (config.xml, sky_intensity); 0 keeps it hidden. */
    private SkyView sky;
    private String vpnApp;
    private final Rect revealRect = new Rect();
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private int[] settingsKeycodes = new int[0];
    private boolean settingsKeyPanel;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** config.xml dock_row: the first row is a separate dock (band, parked low, top shelf) rather than plain grid. */
    private boolean dockRow;
    /** The highlight springs past its target (config.xml, focus_overshoot); false goes straight there. */
    private boolean focusOvershoot;
    /** Festive skin from the theme (see Festival); applied while it is active today. */
    private Festival festival = Festival.NONE;
    private boolean festive;
    private int greetingColor;
    /** boot_to_live_tv: open the Live TV app on the first Home of each boot. */
    private boolean bootToLiveTv;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private boolean settingsRecreationPending;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** Between onStop and onStart: Home was fully covered by an app, not just by the translucent quick panel. */
    private boolean stopped;
    /** Between onStart and onStop: Home is (at least partly) on screen. */
    private boolean started;
    /** Rebuild the tiles on the way back to Home only when apps changed meanwhile (TileRefresh). */
    private final TileRefresh tileRefresh = new TileRefresh();
    /** PackageManager's change counter at the last rebuild, to catch a package broadcast that never arrived. */
    private int packageSequence;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;
    private View btnReorder;
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private List<String> dockApps;
    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private boolean fillDockLeavesSlots;
    private AppLibraryDialog appLibrary;
    private boolean appLibrarySelection;
    private boolean libraryFocusPending;
    private RecyclerView.OnScrollListener pendingFocusScroll;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            // In the background this only marks the tiles stale; onResume rebuilds them once.
            if (!tileRefresh.onPackagesChanged()) return;
            reloadApps(false);
            if (appLibrary != null) appLibrary.refresh();
        }
    };
    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClockVisibility();
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateRace();
            // The pill follows the network callbacks and each return to Home; only the diya's flame follows the
            // signal level, so only it needs this minute's binder calls (about 7 ms on the UI thread).
            if (festive) updateWifiLabel();
            sky.update();
            wallpaperPlayer.update();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        appLibrarySelection = cfg.appLibrarySelection;
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this);
        settingsRevision = settingsRevision();
        applyViewInputsHandler(cfg.handleViewInputs);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
        COLUMNS = cfg.columns;
        animations = cfg.animations;
        advancedEffects = cfg.animations && cfg.advancedEffects;
        reducedMotion = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean("reduced_motion", cfg.reducedMotion);
        dockRow = false;
        focusOvershoot = cfg.focusOvershoot;
        festival = cfg.festival;
        festiveController = new FestiveSkinController(this, festival);
        festive = festiveController.isFestive();
        liveTvPkg = cfg.liveTvPackage;
        idleClockMs = cfg.homeIdleClockMinutes * MINUTE_MS;
        bootToLiveTv = cfg.bootToLiveTv;
        liveTvAction = cfg.liveTvAction;
        liveTvInputId = cfg.liveTvInputId;
        appLibraryEnabled = cfg.appLibrary;
        tileMenuEnabled = cfg.tileMenu;
        inputAction = cfg.inputAction;
        inputUriScheme = cfg.inputUriScheme;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;
        sourcePanelExtra = cfg.sourcePanelExtra;
        coocaaSources = cfg.coocaaSources;

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        // root fills the window with an opaque colour (Config keeps background_color opaque), so the window's own
        // black under it is a second full-screen fill on every frame for nothing. The starting window still uses
        // the theme's background, so launch looks the same.
        getWindow().setBackgroundDrawable(null);
        wallpaperPlayer = new WallpaperPlayer(this, root, cfg);
        sky = findViewById(R.id.sky);
        sky.setIntensity(cfg.skyIntensity);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        hero = findViewById(R.id.hero);
        heroMeta = findViewById(R.id.heroMeta);
        heroDots = findViewById(R.id.heroDots);
        heroArt = findViewById(R.id.heroArt);
        View heroScrim = findViewById(R.id.heroScrim);
        rtlAppGrid = getResources().getBoolean(R.bool.rtl_app_grid);
        heroAmbientPresenter = new HeroAmbientPresenter(this, hero, heroName, heroMeta,
                heroDots, heroArt, ambient, heroScrim, cfg, density, animations, reducedMotion,
                advancedEffects, rtlAppGrid, COLUMNS);
        glassFocusPresenter = new GlassFocusPresenter(root, glass, cfg, density,
                animations, reducedMotion, advancedEffects, COLUMNS);
        if (festive) glassFocusPresenter.setFestive(true, festival);

        greeting = findViewById(R.id.greeting);
        clockBlock = findViewById(R.id.leftBlock);
        greetingColor = greeting.getCurrentTextColor();
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            // Center Hebrew greetings over the numeric time, independent of a visible AM/PM label.
            findViewById(R.id.clock).addOnLayoutChangeListener((v, l, t, right, bottom, ol, ot, or, ob) -> {
                ViewGroup.LayoutParams params = greeting.getLayoutParams();
                if (params.width != right - l) {
                    params.width = right - l;
                    greeting.setLayoutParams(params);
                }
                greeting.setTranslationX(l);
            });
        }
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        lunarDate = findViewById(R.id.lunarDate);
        stats = findViewById(R.id.stats);
        raceCalendar = cfg.raceCalendar;
        racePanel = findViewById(R.id.racePanel);
        raceKicker = findViewById(R.id.raceKicker);
        raceHeadline = findViewById(R.id.raceHeadline);
        raceSessions = findViewById(R.id.raceSessions);
        showStats = cfg.showStats;
        statsMonitor = new SystemStatsMonitor(this, handler, stats, showStats);
        racePresenter = new RaceWeekendPresenter(this, racePanel, raceKicker, raceHeadline, raceSessions, raceCalendar);
        shamsi = cfg.shamsiDate;
        greetingName = cfg.greetingName;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        appsSectionTitle = findViewById(R.id.appsSectionTitle);
        sharedPool = new RecyclerView.RecycledViewPool();
        sharedPool.setMaxRecycledViews(0, 15);
        moviePool = new RecyclerView.RecycledViewPool();
        moviePool.setMaxRecycledViews(0, 20);
        grid.setRecycledViewPool(sharedPool);
        grid.setItemViewCacheSize(4);
        grid.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            protected boolean isLayoutRTL() {
                return rtlAppGrid;
            }

            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                int left = child.getLeft() + rect.left;
                int right = left + rect.width();
                int limitRight = getWidth() - getPaddingRight();
                int dx = 0;
                if (left < getPaddingLeft()) dx = left - getPaddingLeft();
                else if (right > limitRight) dx = right - limitRight;
                if (dx == 0) return false;
                if (immediate || reducedMotion || !animations) parent.scrollBy(dx, 0);
                else parent.smoothScrollBy(dx, 0);
                return true;
            }
        });
        grid.setHasFixedSize(false);
        grid.setItemAnimator(null);
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View v) {
                v.setAlpha(tileAlpha(grid.getChildAdapterPosition(v)));
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View v) {
            }
        });
        if (cfg.fadeRows) TopFade.attach(grid, 48 * density);
        if (animations && !reducedMotion) {
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        dockApps = cfg.dockApps;
        fillDockLeavesSlots = !cfg.fillDock;
        Order savedOrder = Order.load(Order.file(this));
        adapter.setSavedOrder(Order.ranks(savedOrder, dockApps), Order.docked(savedOrder, dockApps));
        grid.setAdapter(adapter);

        mainScroll = findViewById(R.id.mainScroll);
        if (mainScroll != null) {
            mainScroll.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRect(0, 0, view.getWidth(), view.getHeight());
                }
            });
            mainScroll.setClipToOutline(true);
            mainScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (glassFocusPresenter != null) {
                    glassFocusPresenter.onScrollChanged(getCurrentFocus(), mainScroll, clockBlock);
                }
            });
        }
        movieSectionsContainer = findViewById(R.id.movieSectionsContainer);
        sectionConfig = new SectionConfig(this);
        bgExecutor = new BackgroundTaskExecutor("rec-worker");
        recHandler = bgExecutor.getHandler();
        recPresenter = new TvRecommendationPresenter(this, bgExecutor, handler, this::updateMovieSections);
        sectionPresenter = new SectionManagementPresenter(this, movieSectionsContainer, sectionConfig, new SectionManagementPresenter.Host() {
            @Override
            public void focusGlassOnCard(View view) {
                MainActivity.this.focusGlassOnCard(view);
            }

            @Override
            public void scrollSectionFullyIntoView(View view) {
                MainActivity.this.scrollSectionFullyIntoView(view);
            }

            @Override
            public void onExitToGrid() {
                focusTile(Math.min(lastFocused, adapter.appCount() - 1));
            }
        }, density);

        int savedUserMode = getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getInt("user_mode", com.next.tv.launcher.onboarding.OnboardingDialog.MODE_FULL);
        this.currentUserMode = savedUserMode;
        setupWidgetSection();
        appLockManager = AppLockManager.getInstance(this);
        if (savedUserMode != com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            remoteLockServer = new RemoteLockServer(this);
            remoteLockServer.start();
        }
        setupUtilitySection();

        grid.post(this::refitGlass);

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (glassFocusPresenter != null) glassFocusPresenter.onScrolled(dx, dy);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    syncGlassWithFocusedView(false);
                }
            }
        });

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            Log.e("TvHome.Glass", "onGlobalFocusChange: old=" + (oldFocus != null ? (oldFocus.getClass().getSimpleName() + " id=" + (oldFocus.getId() > 0 ? getResources().getResourceEntryName(oldFocus.getId()) : oldFocus.getId())) : "null")
                    + " new=" + (newFocus != null ? (newFocus.getClass().getSimpleName() + " id=" + (newFocus.getId() > 0 ? getResources().getResourceEntryName(newFocus.getId()) : newFocus.getId())) : "null"));
            if (newFocus == null) {
                if (glassFocusPresenter != null) glassFocusPresenter.hideGlass();
                return;
            }
            int id = newFocus.getId();
            if (id == R.id.card || id == R.id.btnMoveSection || id == R.id.btnToggleSection) {
                View containingItem = (grid != null) ? grid.findContainingItemView(newFocus) : null;
                if (containingItem != null) {
                    int pos = grid.getChildAdapterPosition(containingItem);
                    if (pos != RecyclerView.NO_POSITION) {
                        onTileFocused(pos, newFocus);
                    } else {
                        syncGlassWithTarget(newFocus, true);
                    }
                } else {
                    syncGlassWithTarget(newFocus, true);
                }
                newFocus.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                newFocus.postDelayed(() -> syncGlassWithFocusedView(false), 280);
            } else {
                if (glassFocusPresenter != null) glassFocusPresenter.hideGlass();
            }
        });

        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        btnBluetooth = findViewById(R.id.btnBluetooth);
        btnInputs.setOnClickListener(v -> {
            if (appLockManager != null && appLockManager.isKidModeActive()) {
                Toast.makeText(this, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
                return;
            }
            showInputs();
        });

        searchOverlayManager = GlobalSearchOverlayManager.getInstance(this);
        btnGlobalSearch = findViewById(R.id.btn_global_search);
        if (btnGlobalSearch != null) {
            btnGlobalSearch.setOnClickListener(v -> {
                if (searchOverlayManager != null) searchOverlayManager.show(MainActivity.this, true);
            });
            btnGlobalSearch.setOnLongClickListener(v -> {
                if (appLockManager != null && appLockManager.isKidModeActive()) {
                    new PinLockDialog(this, PinLockDialog.MODE_VERIFY, "Mã PIN phụ huynh", "Nhập mã để mở cài đặt", new PinLockDialog.PinResultListener() {
                        @Override
                        public void onSuccess() {
                            ensureSearchSettingsDrawer().show();
                        }

                        @Override
                        public void onCancel() {}
                    }).show();
                } else {
                    ensureSearchSettingsDrawer().show();
                }
                return true;
            });
        }
        handleSearchIntent(getIntent());
        btnWifi.setOnClickListener(v -> {
            if (appLockManager != null && appLockManager.isKidModeActive()) {
                Toast.makeText(this, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
                return;
            }
            if (com.next.tv.launcher.BuildConfig.USE_MANAGE_STORAGE) {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 102);
                }
            }
            openNetworkPill();
        });
        btnWifi.setOnLongClickListener(v -> {
            if (appLockManager != null && appLockManager.isKidModeActive()) {
                Toast.makeText(this, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
                return true;
            }
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        launcherSettingsButton = cfg.launcherSettingsButton;
        btnSettings.setOnClickListener(v -> {
            if (appLockManager != null && appLockManager.isKidModeActive()) {
                new PinLockDialog(this, PinLockDialog.MODE_VERIFY, "Tắt chế độ trẻ em", "Nhập mã PIN phụ huynh", new PinLockDialog.PinResultListener() {
                    @Override
                    public void onSuccess() {
                        appLockManager.setKidModeActive(false);
                        updateKidModeUiState();
                        reloadApps(false);
                        Toast.makeText(MainActivity.this, "Đã tắt Chế độ trẻ em", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancel() {}
                }).show();
                return;
            }
            if (launcherSettingsButton) startActivityForResult(
                    new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
            else open(systemIntent(Settings.ACTION_SETTINGS));
        });
        updateKidModeUiState();

        if (btnBluetooth != null) {
            btnBluetooth.setOnClickListener(v -> {
                if (appLockManager != null && appLockManager.isKidModeActive()) {
                    Toast.makeText(this, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
                    return;
                }
                SystemSettings.openBluetooth(this);
            });
        }

        btnReorder = findViewById(R.id.btnReorder);
        appReorderController = new AppReorderController(new AppReorderController.Host() {
            @Override public Context getContext() { return MainActivity.this; }
            @Override public AppAdapter getAdapter() { return adapter; }
            @Override public RecyclerView getGrid() { return grid; }
            @Override public AppLockManager getAppLockManager() { return appLockManager; }
            @Override public GlassFocusPresenter getGlassFocusPresenter() { return glassFocusPresenter; }
            @Override public View getBtnReorder() { return btnReorder; }
            @Override public List<String> getDockApps() { return dockApps; }
            @Override public boolean isFillDockLeavesSlots() { return fillDockLeavesSlots; }
            @Override public boolean isRtlAppGrid() { return rtlAppGrid; }
            @Override public int getColumns() { return COLUMNS; }
            @Override public boolean isFestive() { return festive; }
            @Override public Festival getFestival() { return festival; }
            @Override public void reloadTiles() { MainActivity.this.reloadTiles(); }
            @Override public void focusTile(int pos) { MainActivity.this.focusTile(pos); }
            @Override public void onLibraryRequested() { MainActivity.this.onLibraryRequested(); }
            @Override public int getFocusedTilePosition() { return MainActivity.this.focusedTilePosition(); }
            @Override public void keepScroll(Runnable change) { MainActivity.this.keepScroll(change); }
            @Override public View getCurrentFocus() { return MainActivity.this.getCurrentFocus(); }
        });
        btnReorder.setOnClickListener(v -> toggleReorder());
        btnReorder.setVisibility(cfg.reorderButton ? View.VISIBLE : View.GONE);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        showInputsPill = cfg.showInputsPill;
        showInputsMenuRow = cfg.showInputsPill;
        btnLibraryTop = findViewById(R.id.btnLibraryTop);
        btnLibraryTop.setOnClickListener(v -> {
            onLibraryRequested();
            libraryFromTopBar = true;   // closing it goes back to this button, not to the pill after the last row
        });
        btnLibraryTop.setVisibility(cfg.libraryButton && cfg.appLibrary ? View.VISIBLE : View.GONE);
        iconOnlyButtons = cfg.iconOnlyButtons;
        topMenu = cfg.topMenu;
        wifiIconOnly = iconOnlyButtons && cfg.wifiLabelWidth <= 0;
        if (cfg.wifiStatusColor) {
            wifiPlainTint = btnWifi.getCompoundDrawableTintList();
            wifiOnlineTint = getColorStateList(R.color.pill_icon_online);
        }
        if (cfg.topMenu) {
            // One gear: Inputs and Wi-Fi move into its dropdown. The Wi-Fi pill stays hidden but keeps its
            // label up to date, which the menu's Wi-Fi row shows.
            showInputsPill = false;
            btnInputs.setVisibility(View.GONE);
            btnWifi.setVisibility(View.GONE);
            ((TextView) btnSettings).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
            makeIconOnly((TextView) btnSettings);
            btnSettings.setOnClickListener(v -> showTopMenu());
            // The lone gear sits at the header's middle, not on its bottom line like the row of pills.
            LinearLayout topButtons = findViewById(R.id.topButtons);
            RelativeLayout.LayoutParams tlp = (RelativeLayout.LayoutParams) topButtons.getLayoutParams();
            tlp.addRule(RelativeLayout.ALIGN_TOP, R.id.leftBlock);
            tlp.bottomMargin = 0;
            topButtons.setLayoutParams(tlp);
            topButtons.setGravity(Gravity.CENTER_VERTICAL);
        }
        if (iconOnlyButtons) {
            if (cfg.wifiLabelWidth > 0) btnWifi.setMaxWidth(Math.round(cfg.wifiLabelWidth * density));
            else makeIconOnly(btnWifi);
            makeIconOnly((TextView) btnSettings);
        }
        if (cfg.iconOnlyInputs) makeIconOnly((TextView) btnInputs);
        btnSettings.setOnLongClickListener(v -> {
            showSettingsMenu();
            return true;
        });

        inputController = new TvInputController(this, handler, new TvInputController.PopupListener() {
            @Override
            public void onPopupOpened(PopupWindow popup) {
                openPopup = popup;
            }

            @Override
            public void onPopupDismissed(PopupWindow popup) {
                if (openPopup == popup) openPopup = null;
            }
        });
        inputController.setConfig(showInputsPill, showInputsMenuRow, topMenu, bootToLiveTv, coocaaSources, reducedMotion,
                liveTvPkg, liveTvAction, liveTvInputId, sourcePanelService, sourcePanelAction, sourcePanelExtra, inputAction, inputUriScheme);
        inputController.setOnInputsChangedCallback(this::updateInputsButton);

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        boolean rtlMenuBar = getResources().getBoolean(R.bool.rtl_menu_bar);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (widgetRecyclerView != null && widgetRecyclerView.getVisibility() == View.VISIBLE
                        && widgetAdapter != null && widgetAdapter.getItemCount() > 0) {
                    focusFirstVisibleWidget();
                    return true;
                }
                if (adapter != null && adapter.appCount() > 0) {
                    focusTile(lastFocused % COLUMNS);
                    return true;
                }
                if (focusFirstVisibleMovieSection()) return true;
                if (utilityRecyclerView != null && utilityRecyclerView.getVisibility() == View.VISIBLE) {
                    focusFirstVisibleUtility();
                    return true;
                }
                return true;
            }
            int firstButton = btnInputs.getVisibility() == View.VISIBLE ? R.id.btnInputs : firstTopButton().getId();
            int leftButton = rtlMenuBar ? R.id.btnSettings : firstButton;
            int rightButton = rtlMenuBar ? firstButton : R.id.btnSettings;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == leftButton;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == rightButton;
        };
        btnInputs.setOnKeyListener(pillKeys);
        if (btnGlobalSearch != null) btnGlobalSearch.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        if (btnBluetooth != null) btnBluetooth.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnReorder.setOnKeyListener(pillKeys);
        btnLibraryTop.setOnKeyListener(pillKeys);
        if (advancedEffects) {
            for (View button : new View[]{btnInputs, btnGlobalSearch, btnWifi, btnBluetooth, btnReorder, btnLibraryTop, btnSettings}) {
                if (button == null) continue;
                button.setOnFocusChangeListener((view, focused) -> view.animate()
                        .scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                        .setDuration(reducedMotion ? Motion.REDUCED_MS : 160).start());
            }
        }
        if (festiveController != null && festiveController.isFestive()) {
            festiveController.applyFestiveSkin((TextView) btnInputs, (TextView) btnSettings, btnWifi, density, reducedMotion);
        }

        // Hide the glass when focus leaves the tiles or movie posters or section action buttons.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && (newF.getId() == R.id.card
                    || newF.getId() == R.id.btnMoveSection || newF.getId() == R.id.btnToggleSection);
            if (!onTile) hideGlass();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        // Registered for the activity's whole life, not only while it is started: an app installed or updated while
        // another app is in front must still mark the tiles stale, or Home would have to rebuild on every return.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (inputController != null) inputController.register(handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
        // Not after recreate(): the launch intent survives it and would open the settings again.
        if (savedInstanceState == null && !openLauncherSettingsIfAsked(getIntent())) openInputsIfAsked(getIntent());
        handleCustomActions(getIntent());

        applyUserMode(currentUserMode);

        if (com.next.tv.launcher.onboarding.OnboardingDialog.shouldShow(this)) {
            onboardingDialog = new com.next.tv.launcher.onboarding.OnboardingDialog(this, () -> {
                focusFirstAppTile();
                checkEpgPermission();
            });
            onboardingDialog.show();
        }
    }

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written
     * only when the state differs, so an ordinary start does not touch the package manager.
     */
    private void applyViewInputsHandler(boolean on) {
        ComponentName handler = new ComponentName(this, InputsRequestActivity.class);
        PackageManager pm = getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        if (pm.getComponentEnabledSetting(handler) != want) {
            pm.setComponentEnabledSetting(handler, want, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * VIEW_INPUTS, forwarded by InputsRequestActivity while handle_view_inputs is on: open the Inputs menu once
     * the pill is on screen. The intent is replaced so a later recreate() does not
     * open it again. Nothing to do when the TV has no inputs to list (the pill is hidden then).
     * top_menu hides the pill, so the list hangs under the gear there, as in showInputs().
     */
    private boolean openInputsIfAsked(Intent intent) {
        if (intent == null || !ACTION_VIEW_INPUTS.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        View anchor = topMenu ? findViewById(R.id.btnSettings) : btnInputs;
        if (anchor.getVisibility() != View.VISIBLE) return true;
        if (anchor.isLaidOut()) {
            anchor.requestFocus();
            showInputs();
            return true;
        }
        // Cold start: the popup anchors to the pill (or the gear), so wait for the first layout.
        anchor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                anchor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                anchor.post(() -> {
                    if (isFinishing() || anchor.getVisibility() != View.VISIBLE) return;
                    anchor.requestFocus();
                    showInputs();
                });
            }
        });
        return true;
    }

    public void triggerFocusedContextMenu() {
        if (widgetRecyclerView != null && widgetRecyclerView.hasFocus()) {
            View focusedChild = widgetRecyclerView.getFocusedChild();
            if (focusedChild != null) {
                int pos = widgetRecyclerView.getChildAdapterPosition(focusedChild);
                if (pos != RecyclerView.NO_POSITION && widgetAdapter != null) {
                    widgetAdapter.showWidgetContextMenu(pos, focusedChild);
                    return;
                }
            }
        }
        int position = focusedTilePosition();
        if (position >= 0 && position < adapter.appCount()
                && !getPackageName().equals(adapter.packageAt(position))) {
            onTileLongPress(position, getCurrentFocus());
        }
    }

    private boolean handleCustomActions(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if ("com.next.tv.launcher.action.OPEN_WALLPAPER".equals(action)) {
            showWallpaperPicker();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_REMOTE_CONTROL".equals(action)) {
            showRemoteControlDrawer();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_REMOTE_FILE".equals(action)) {
            showRemoteFileDrawer();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_HIDDEN_APPS".equals(action)) {
            showHiddenAppsManagerDialog();
            return true;
        }
        if ("com.next.tv.launcher.action.SET_USER_MODE".equals(action)) {
            int mode = intent.getIntExtra("mode", com.next.tv.launcher.onboarding.OnboardingDialog.MODE_FULL);
            applyUserMode(mode);
            return true;
        }
        if ("com.next.tv.launcher.action.SET_ONBOARDING_COMPLETED".equals(action)) {
            com.next.tv.launcher.onboarding.OnboardingDialog.setCompleted(this);
            if (onboardingDialog != null && onboardingDialog.isShowing()) {
                onboardingDialog.dismiss();
            }
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_ONBOARDING".equals(action)) {
            new com.next.tv.launcher.onboarding.OnboardingDialog(this, () -> focusFirstAppTile()).show();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_MEMORY_DRAWER".equals(action)) {
            if (kidTimeUpDialog != null && kidTimeUpDialog.isShowing()) {
                kidTimeUpDialog.dismiss();
            }
            showMemoryCleanerDrawer(null);
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_WIDGETS_SETTINGS".equals(action)) {
            showWidgetManagerDrawer();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_ADVANCED_SETTINGS".equals(action)) {
            showAdvancedSettingsDrawer();
            return true;
        }
        if ("com.next.tv.launcher.action.OPEN_KID_MODE".equals(action)) {
            showKidModeControlDrawer(null);
            return true;
        }
        if ("com.next.tv.launcher.action.TEST_SMART_CLEAN".equals(action)) {
            String testMode = intent.getStringExtra("mode");
            int maxCached = intent.getIntExtra("max_cached", -1);
            boolean forceClean = intent.getBooleanExtra("force_clean", true);

            com.next.tv.launcher.clean.SmartRamConfigManager cfg = com.next.tv.launcher.clean.SmartRamConfigManager.getInstance(this);
            if (testMode != null) {
                cfg.setMode(testMode);
            }
            if (maxCached > 0) {
                cfg.setMaxCachedProcesses(maxCached);
            }
            com.next.tv.launcher.clean.SmartRamGovernor.applyKernelLimits(this);
            com.next.tv.launcher.clean.SmartRamGovernor.cleanRamSmartAsync(this, true, forceClean, res -> {
                android.util.Log.i("TvHome.SmartRAM", "TEST_SMART_CLEAN executed: killed=" + res.killedCount + ", freed=" + res.freedMb + "MB, RAM=" + res.ramUsedPercentBefore + "%->" + res.ramUsedPercentAfter + "%");
                android.util.Log.i("TvHome.SmartRAM", "Killed apps: " + res.killedPackages);
                android.util.Log.i("TvHome.SmartRAM", "Protected apps: " + res.protectedPackages);
            });
            return true;
        }
        return false;
    }

    private boolean openLauncherSettingsIfAsked(Intent intent) {
        if (intent == null || !ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
        return true;
    }

    private boolean handleSearchIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if ("com.next.tv.launcher.action.GLOBAL_SEARCH".equals(action)
                || Intent.ACTION_ASSIST.equals(action)
                || Intent.ACTION_VOICE_COMMAND.equals(action)
                || Intent.ACTION_SEARCH.equals(action)
                || Intent.ACTION_SEARCH_LONG_PRESS.equals(action)
                || "android.speech.action.WEB_SEARCH".equals(action)
                || "android.speech.action.VOICE_SEARCH_HANDS_FREE".equals(action)
                || "android.search.action.GLOBAL_SEARCH".equals(action)) {
            if ("com.next.tv.launcher.action.GLOBAL_SEARCH".equals(action)
                    || (searchOverlayManager != null && searchOverlayManager.isMicOverrideEnabled())) {
                String q = intent.getStringExtra("query");
                if (q == null || q.trim().isEmpty()) {
                    q = intent.getStringExtra(android.app.SearchManager.QUERY);
                }
                if (q != null && !q.trim().isEmpty()) {
                    searchOverlayManager.show(MainActivity.this, false);
                    searchOverlayManager.executeUniversalSearch(q.trim(), true);
                } else {
                    searchOverlayManager.show(MainActivity.this, true);
                }
                return true;
            }
        }
        if ("com.next.tv.launcher.action.SEARCH_SETTINGS".equals(action)) {
            if (appLockManager != null && appLockManager.isKidModeActive()) {
                new PinLockDialog(this, PinLockDialog.MODE_VERIFY, "Mã PIN phụ huynh", "Nhập mã để mở cài đặt", new PinLockDialog.PinResultListener() {
                    @Override
                    public void onSuccess() {
                        ensureSearchSettingsDrawer().show();
                    }

                    @Override
                    public void onCancel() {}
                }).show();
            } else {
                ensureSearchSettingsDrawer().show();
            }
            return true;
        }
        return false;
    }

    public void focusFirstAppTile() {
        if (mainScroll != null) {
            mainScroll.scrollTo(0, 0);
        }
        if (widgetRecyclerView != null && widgetRecyclerView.getVisibility() == View.VISIBLE
                && widgetAdapter != null && widgetAdapter.getItemCount() > 0) {
            currentFocusedSection = widgetRecyclerView;
            widgetRecyclerView.scrollToPosition(0);
            focusFirstVisibleWidget();
            if (mainScroll != null) mainScroll.post(() -> mainScroll.smoothScrollTo(0, 0));
            return;
        }
        currentFocusedSection = grid;
        lastFocused = 0;
        if (adapter != null && adapter.appCount() > 0) {
            lastFocusedKey = adapter.keyAt(0);
            grid.scrollToPosition(0);
            grid.postDelayed(() -> {
                if (mainScroll != null) mainScroll.scrollTo(0, 0);
                RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
                if (vh != null) {
                    View card = vh.itemView.findViewById(R.id.card);
                    if (card != null) {
                        if (card.isFocused()) {
                            onTileFocused(0, card);
                        } else {
                            card.requestFocus();
                        }
                        if (mainScroll != null) mainScroll.post(() -> mainScroll.smoothScrollTo(0, 0));
                        card.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                        return;
                    }
                }
                focusTile(0);
                if (mainScroll != null) mainScroll.post(() -> mainScroll.smoothScrollTo(0, 0));
            }, 60);
        }
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (searchOverlayManager != null && searchOverlayManager.isShowing()) {
                searchOverlayManager.dismissImmediate();
            }
            if (searchSettingsDrawer != null && searchSettingsDrawer.isShowing()) {
                searchSettingsDrawer.dismiss();
            }
            if (wallpaperPickerDrawer != null && wallpaperPickerDrawer.isShowing()) {
                wallpaperPickerDrawer.dismiss();
            }
            if (remoteControlDrawer != null && remoteControlDrawer.isShowing()) {
                remoteControlDrawer.dismiss();
            }
            if (remoteFileDrawer != null && remoteFileDrawer.isShowing()) {
                remoteFileDrawer.dismiss();
            }
            if (advancedSettingsDrawer != null && advancedSettingsDrawer.isShowing()) {
                advancedSettingsDrawer.dismiss();
            }
            if (widgetManagerDrawer != null && widgetManagerDrawer.isShowing()) {
                widgetManagerDrawer.dismiss();
            }
            if (memoryCleanerDrawer != null && memoryCleanerDrawer.isShowing()) {
                memoryCleanerDrawer.dismiss();
            }
            if (kidModeControlDrawer != null && kidModeControlDrawer.isShowing()) {
                kidModeControlDrawer.dismiss();
            }
            if (hiddenAppsDrawer != null && hiddenAppsDrawer.isShowing()) {
                hiddenAppsDrawer.dismiss();
            }
            if (widgetAdapter != null) {
                widgetAdapter.dismissContextMenu();
                widgetAdapter.cancelMoveMode();
            }
            checkKidModeTimeLimit();
        }
        if (handleSearchIntent(intent)) return;
        if (handleCustomActions(intent)) return;
        if (openLauncherSettingsIfAsked(intent)) return;
        if (openInputsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        focusFirstAppTile();
        if (stopped) {
            tileRefresh.onHomeAgain();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
        started = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timeReceiver, timeFilter);
        }
        updateClockVisibility();
        tileRefresh.onStart();
        if (usageTracker != null && widgetAdapter != null) {
            widgetAdapter.setUsageData(usageTracker.getTodayUsage());
        }
        if (mediaTracker != null && widgetAdapter != null) {
            widgetAdapter.setMediaData(mediaTracker.getPlaybackInfo());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        activeInstance = this;
        updateKidModeUiState();
        checkKidModeTimeLimit();
        handler.removeCallbacks(kidTimeCheckRunnable);
        handler.postDelayed(kidTimeCheckRunnable, 30_000);
        // A festival that started or ended while Home was in the background: rebuild with or without it.
        if (festiveController != null && festiveController.checkDateChanged()) {
            recreate();
            return;
        }
        // Something changed in Launcher settings: rebuild with it, however the settings were left. Only Back
        // delivered their RESULT_OK; Home (the remote's button) clears them off this task and the result comes
        // back cancelled, so a new wallpaper or layout waited for the next process start.
        if (Config.settingsChanged(settingsRevision, settingsRevision())) {
            recreate();
            return;
        }
        restartIdleClock();
        promptsPaused = false;
        if (grid.getAdapter() == null) grid.setAdapter(adapter);   // dropped by onTrimMemory
        switch (tileRefresh.onResume(packagesChangedSinceRebuild())) {
            case REBUILD:
                tilesReady = false;
                reloadApps(true);
                break;
            case REFOCUS:
                // The same tiles: only the focus goes back, as after a rebuild, without replaying the entrance.
                tilesReady = false;
                reloadApps(true, false, false);
                break;
            default:
                break;
        }
        if (openLiveTvAfterBoot()) return;
        updateGreeting();
        updateRace();
        // The network and Inputs pills keep their state while Home is in the background, and callbacks update them
        // when the network or the inputs change, so this refresh only catches anything missed. Its binder calls
        // (about 7 ms on a TCL C755, most of it WifiManager.getConnectionInfo) would hold up the first frame back
        // on Home, so after the first resume it runs once that frame is drawn.
        if (pillsShown) {
            refreshPillsAfterNextDraw();
        } else {
            updateWifiLabel();
            updateInputsButton();
            pillsShown = true;
        }
        if (currentUserMode != com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            triggerSmartRamCronOnResume();
            if (showStats && statsMonitor != null) statsMonitor.start();
            if (festiveController != null) {
                festiveController.startDiya();
                festiveController.checkAndPlayDailyPetalShower(root, handler, reducedMotion);
            }
            registerTvObserver();
            if (!com.next.tv.launcher.onboarding.OnboardingDialog.shouldShow(this)) {
                checkEpgPermission();
            }
            loadRecommendations();
        }
        sky.update();
        wallpaperPlayer.start();
        handler.postDelayed(minuteTick, MINUTE_MS);

        if (weatherManager != null && widgetAdapter != null) {
            WeatherDataManager.WeatherInfo latestWeather = weatherManager.getCachedWeather();
            if (latestWeather != null) {
                widgetAdapter.setWeatherData(latestWeather);
            }
        }
        if (searchSettingsDrawer != null) {
            searchSettingsDrawer.refreshPermissions();
        }

        // Restore focus to current focused view / section if mainScroll stole focus
        if (mainScroll != null && mainScroll.hasFocus()) {
            unblockMainContentFocus();
            if (currentFocusedSection != null && currentFocusedSection.isShown()) {
                currentFocusedSection.post(() -> {
                    if (currentFocusedSection == utilityRecyclerView) {
                        focusFirstVisibleUtility();
                    } else if (currentFocusedSection == widgetRecyclerView) {
                        focusFirstVisibleWidget();
                    } else if (currentFocusedSection == grid) {
                        focusTile(lastFocused);
                    } else {
                        currentFocusedSection.requestFocus();
                    }
                });
            } else {
                focusFirstAppTile();
            }
        }
    }

    private void checkEpgPermission() {
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> needed = new ArrayList<>();
            if (checkSelfPermission(PERMISSION_READ_TV) != PackageManager.PERMISSION_GRANTED) {
                needed.add(PERMISSION_READ_TV);
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), REQUEST_CODE_EPG);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_AUDIO) {
            if (searchSettingsDrawer != null) {
                searchSettingsDrawer.refreshPermissions();
            }
        } else if (requestCode == 102) {
            updateWifiLabel();
        } else if (requestCode == REQUEST_CODE_EPG) {
            if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) return;
            boolean anyGranted = false;
            for (int res : grantResults) {
                if (res == PackageManager.PERMISSION_GRANTED) {
                    anyGranted = true;
                    break;
                }
            }
            if (anyGranted) {
                TvRecommendationReader.wakeUpEpgServices(this);
                loadRecommendations();
                scheduleRecommendationRetries();
            }
        }
    }

    /** Launcher settings' change count (Config.SETTINGS_REVISION_KEY); onResume rebuilds Home when it moved. */
    private long settingsRevision() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong(Config.SETTINGS_REVISION_KEY, 0);
    }

    /**
     * Only "Reorder tiles" in Launcher settings still answers with a result: Home rebuilds (the saved choices are
     * reloaded) and then enters reorder mode. Every other change is picked up in onResume through the change count.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == RESULT_OK
                && data != null && data.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) {
            getIntent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true);
            // The old activity must not consume the pending action, or rebuild a second time in onResume.
            settingsRecreationPending = true;
            settingsRevision = settingsRevision();
            recreate();
        }
    }

    private void startPendingSettingsReorder() {
        if (settingsRecreationPending || !tilesReady || !hasWindowFocus() || isFinishing()
                || !getIntent().getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) return;
        getIntent().removeExtra(LauncherSettingsActivity.EXTRA_REORDER);
        toggleReorder();
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        reloadApps(animate, true);
    }

    /** @param rebuild false keeps the tiles as they are and only puts focus back (TileRefresh.Action.REFOCUS). */
    private void reloadApps(boolean animate, boolean rebuild) {
        reloadApps(animate, rebuild, true);
    }

    /**
     * @param entrance false puts focus back as an animated reload would, without replaying the tiles' entrance:
     *                 on a return to the same tiles it would only mark a change that didn't happen.
     */
    private void reloadApps(boolean animate, boolean rebuild, boolean entrance) {
        cancelPendingFocusScroll();
        RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
        final boolean restoreLibrary = adapter.isLibraryAt(adapter.appCount()) && footer != null
                && footer.itemView.findViewById(R.id.btnLibrary) != null
                && footer.itemView.findViewById(R.id.btnLibrary).hasFocus();
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = appLibrary == null && (animate || grid.hasFocus() || currentFocusedSection == grid || lastFocusedKey != null);
        if (restoreFocus) {
            if (glassFocusPresenter != null) glassFocusPresenter.resetVisibilityOnStart();
            if (heroAmbientPresenter != null) heroAmbientPresenter.resetVisibilityOnStart();
        }
        if (rebuild) reloadTiles();
        if (appsSectionTitle != null) {
            appsSectionTitle.setVisibility(adapter.appCount() > 0 ? View.VISIBLE : View.GONE);
        }
        if (grid != null) {
            grid.setVisibility(adapter.appCount() > 0 ? View.VISIBLE : View.GONE);
        }
        if (animate && entrance && animations && !reducedMotion) {
            grid.scheduleLayoutAnimation();
            // A rebuild redraws the grid anyway; without one the entrance needs a draw to start.
            if (!rebuild) grid.invalidate();
        }

        if (restoreFocus) {
            // Keep a focused footer at the end even when the app count changes.
            if (restoreLibrary) grid.post(this::focusLibrary);
            else restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.appCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            startPendingSettingsReorder();
            showCardPrompt();
        }
    }

    /** True when PackageManager reports apps changed since the last rebuild (one binder call). */
    private boolean packagesChangedSinceRebuild() {
        ChangedPackages changed = getPackageManager().getChangedPackages(packageSequence);
        return changed != null && !changed.getPackageNames().isEmpty();
    }

    private int currentPackageSequence(int since) {
        ChangedPackages changed = getPackageManager().getChangedPackages(since);
        return changed != null ? changed.getSequenceNumber() : since;
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    public void reloadTiles() {
        if (appReorderController != null && appReorderController.isReordering()) appReorderController.endReorder();   // an app installed or removed while reordering: keep the order so far
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        // An updated app may bring new artwork; the backdrop is cheap to make again.
        if (heroAmbientPresenter != null) heroAmbientPresenter.clearCache();
        // One LEANBACK query for both the tiles and the card checks.
        final List<ResolveInfo> leanback = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps,
                () -> Cards.load(Cards.storeFile(this)), leanback), none) : none;
        adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none), leanback);
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        packageSequence = currentPackageSequence(packageSequence);
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final boolean returnToLibrary = libraryFocusPending;
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            if (returnToLibrary || libraryFocusPending) focusLibrary();
            else focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            startPendingSettingsReorder();
            showCardPrompt();
        });
    }

    /** Any key on Home restarts the countdown to the idle clock. */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        restartIdleClock();
    }

    private void restartIdleClock() {
        root.removeCallbacks(showIdleClock);
        if (idleClockMs > 0) root.postDelayed(showIdleClock, idleClockMs);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        root.removeCallbacks(showIdleClock);
        endReorder();   // Home or another app while reordering keeps the order so far
        wallpaperPlayer.stop();
        if (heroAmbientPresenter != null) heroAmbientPresenter.cancelAnimations();
        if (glassFocusPresenter != null) glassFocusPresenter.cancelAnimations();
        handler.removeCallbacks(minuteTick);
        if (statsMonitor != null) statsMonitor.stop();
        handler.removeCallbacks(smartRamPeriodicRunnable);
        if (festiveController != null) festiveController.stopDiya();
        cancelPendingFocusScroll();
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (appLibrary != null) appLibrary.close();
        if (openPopup != null) openPopup.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        unregisterTvObserver();
        FastPosterLoader.getInstance().clear();
        unregisterReceiver(timeReceiver);
        handler.removeCallbacks(clockCheck);
        handler.removeCallbacks(kidTimeCheckRunnable);
        stopped = true;
        tileRefresh.onStop();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (searchOverlayManager != null && searchOverlayManager.isShowing()) {
            searchOverlayManager.dismissImmediate();
        }
        if (searchSettingsDrawer != null && searchSettingsDrawer.isShowing()) {
            searchSettingsDrawer.dismiss();
        }
    }

    /**
     * Another app needs the memory while Home is in the background: let go of every tile's artwork, and
     * rebuild it when Home comes back. Not at UI_HIDDEN, the ordinary trip to an app, where keeping the
     * tiles is what makes the return instant.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        FastPosterLoader.getInstance().clear();
        // RUNNING_LOW and CRITICAL also arrive while Home is visible when an accessibility service keeps the
        // process in the foreground; never blank the screen the owner is looking at.
        boolean due = level >= TRIM_MEMORY_BACKGROUND
                || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL;
        if (!due || started) return;
        adapter.clear();
        // Detach the adapter too: with Home stopped no layout pass runs, so the tile views (and the bitmaps
        // they hold) would stay attached until Home comes back. setAdapter(null) recycles them now.
        grid.setAdapter(null);
        grid.getRecycledViewPool().clear();
        if (heroAmbientPresenter != null) heroAmbientPresenter.clearCache();
        tileRefresh.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            startPendingSettingsReorder();
            if (libraryFocusPending && appLibrary == null) focusLibrary();
            showCardPrompt();
            checkKidModeTimeLimit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (remoteControlDrawer != null && remoteControlDrawer.isShowing()) {
            remoteControlDrawer.dismiss();
        }
        if (remoteFileDrawer != null && remoteFileDrawer.isShowing()) {
            remoteFileDrawer.dismiss();
        }
        if (widgetManagerDrawer != null && widgetManagerDrawer.isShowing()) {
            widgetManagerDrawer.dismiss();
        }
        if (advancedSettingsDrawer != null && advancedSettingsDrawer.isShowing()) {
            advancedSettingsDrawer.dismiss();
        }
        if (memoryCleanerDrawer != null && memoryCleanerDrawer.isShowing()) {
            memoryCleanerDrawer.dismiss();
        }
        if (remoteLockServer != null) {
            remoteLockServer.stop();
            remoteLockServer = null;
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (widgetAddonListener != null) {
            com.next.tv.launcher.widget.WidgetAddonManager.getInstance(this).removeListener(widgetAddonListener);
            widgetAddonListener = null;
        }
        unregisterReceiver(packageReceiver);
        wallpaperPlayer.close();
        if (bgExecutor != null) {
            bgExecutor.quit();
            bgExecutor = null;
            recHandler = null;
        }
        FastPosterLoader.getInstance().clear();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (inputController != null) inputController.unregister();
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    /**
     * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) opens the same
     * thing HomeKeyService would. While that service runs it consumes the key first; without it (not enabled,
     * or switched off by a force-stop) the stock launcher that used to handle the key is gone, and the button did
     * nothing even on Home.
     */
    private boolean handleSettingsKey(KeyEvent event) {
        for (int code : settingsKeycodes) {
            if (event.getKeyCode() != code) continue;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                open(settingsKeyPanel ? new Intent(this, QuickSettingsActivity.class)
                        : systemIntent(Settings.ACTION_SETTINGS));
            }
            return true;
        }
        return false;
    }

    // ---- reorder ---------------------------------------------------------------------------------

    /**
     * Reorder mode (top-bar button). While it is on, every key comes here first:
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    private boolean isVoiceOrSearchKey(KeyEvent event) {
        if (event == null) return false;

        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        return keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
                || keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_ASSIST
                || keyCode == 231 || keyCode == 84 || keyCode == 219 || keyCode == 284
                || scanCode == 217 || scanCode == 0x0221;
    }

    public void showSearchOverlay(boolean startVoice) {
        if (searchOverlayManager != null && searchOverlayManager.isMicOverrideEnabled()) {
            searchOverlayManager.show(MainActivity.this, startVoice);
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (searchOverlayManager != null && searchOverlayManager.isMicOverrideEnabled()) {
            searchOverlayManager.show(this, true);
            return true;
        }
        return super.onSearchRequested();
    }

    @Override
    public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
        if (searchOverlayManager != null && searchOverlayManager.isMicOverrideEnabled()) {
            searchOverlayManager.show(this, true);
            return true;
        }
        return super.onSearchRequested(searchEvent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (widgetAdapter != null && widgetAdapter.isMoveMode()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    widgetAdapter.handleMoveBack();
                }
                return true;
            }
        }
        if (wallpaperPickerDrawer != null && wallpaperPickerDrawer.isShowing()) {
            if (wallpaperPickerDrawer.isFullscreenPreview()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                        wallpaperPickerDrawer.exitFullscreenPreview();
                    }
                    return true;
                }
                // Allow DPAD_LEFT, DPAD_RIGHT, DPAD_UP, DPAD_DOWN, and ENTER/DPAD_CENTER to pass through
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    wallpaperPickerDrawer.dismiss();
                }
                return true;
            }
        }

        if (remoteControlDrawer != null && remoteControlDrawer.isShowing()) {
            if (remoteControlDrawer.handleBackIfListening(event)) {
                return true;
            }
            if (KeyRemapManager.getInstance(this).handleKeyEventIfListening(event)) {
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    remoteControlDrawer.dismiss();
                }
                return true;
            }
        }

        if (remoteFileDrawer != null && remoteFileDrawer.isShowing()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    remoteFileDrawer.dismiss();
                }
                return true;
            }
        }

        if (hiddenAppsDrawer != null && hiddenAppsDrawer.isShowing()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    hiddenAppsDrawer.dismiss();
                }
                return true;
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE || event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (searchSettingsDrawer != null && searchSettingsDrawer.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    searchSettingsDrawer.dismiss();
                }
                return true;
            }
            if (advancedSettingsDrawer != null && advancedSettingsDrawer.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                        advancedSettingsDrawer.dismiss();
                    } else if (advancedSettingsDrawer.isSubPanelOpen()) {
                        advancedSettingsDrawer.closeSubPanel();
                    } else {
                        advancedSettingsDrawer.dismiss();
                    }
                }
                return true;
            }
            if (widgetManagerDrawer != null && widgetManagerDrawer.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                        widgetManagerDrawer.dismiss();
                    } else {
                        widgetManagerDrawer.handleBackPress();
                    }
                }
                return true;
            }
            if (memoryCleanerDrawer != null && memoryCleanerDrawer.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    memoryCleanerDrawer.dismiss();
                }
                return true;
            }
            if (searchOverlayManager != null && searchOverlayManager.isShowing()) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    searchOverlayManager.dismiss();
                }
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
                if (widgetAdapter != null) {
                    widgetAdapter.dismissContextMenu();
                    widgetAdapter.cancelMoveMode();
                }
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    focusFirstAppTile();
                }
                return true;
            }
        }

        int keyCode = event.getKeyCode();
        KeyRemapManager.RemapAction remapAction = KeyRemapManager.getInstance(this).getAction(event.getScanCode(), keyCode);
        if (remapAction != null && remapAction.type != KeyRemapManager.TYPE_NONE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                KeyRemapManager.getInstance(this).execute(this, remapAction);
            }
            return true;
        }

        if (isVoiceOrSearchKey(event) && searchOverlayManager != null && searchOverlayManager.isMicOverrideEnabled()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                searchOverlayManager.show(MainActivity.this, true);
            }
            return true;
        }

        if (btnGlobalSearch != null && btnGlobalSearch.isFocused() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            if (event.isLongPress() || event.getRepeatCount() >= 4) {
                ensureSearchSettingsDrawer().show();
                return true;
            }
        }

        if (handleSettingsKey(event)) return true;
        if (tileMenuEnabled && !isReordering() && openPopup == null && cardPrompt == null) {
            int code = event.getKeyCode();
            boolean isOk = (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER);
            if (isOk) {
                int position = focusedTilePosition();
                if (position >= 0 && position < adapter.appCount()
                        && !getPackageName().equals(adapter.packageAt(position))) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() == 0) {
                            event.startTracking();
                        } else if (event.getRepeatCount() >= 5) {
                            tileMenuJustTriggered = true;
                            onTileLongPress(position, getCurrentFocus());
                            return true;
                        }
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        if (tileMenuJustTriggered) {
                            tileMenuJustTriggered = false;
                            return true;
                        }
                    }
                }
            } else if (code == KeyEvent.KEYCODE_MENU) {
                if (widgetRecyclerView != null && widgetRecyclerView.hasFocus()) {
                    View focusedChild = widgetRecyclerView.getFocusedChild();
                    if (focusedChild != null) {
                        int pos = widgetRecyclerView.getChildAdapterPosition(focusedChild);
                        if (pos != RecyclerView.NO_POSITION && widgetAdapter != null) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                widgetAdapter.showWidgetContextMenu(pos, focusedChild);
                            }
                            return true;
                        }
                    }
                }
                int position = focusedTilePosition();
                if (position >= 0 && position < adapter.appCount()
                        && !getPackageName().equals(adapter.packageAt(position))) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                        onTileLongPress(position, getCurrentFocus());
                    }
                    return true;
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.isLongPress() || event.getRepeatCount() >= 4) {
                if (widgetRecyclerView != null && widgetRecyclerView.hasFocus()) {
                    View focusedChild = widgetRecyclerView.getFocusedChild();
                    if (focusedChild != null) {
                        int pos = widgetRecyclerView.getChildAdapterPosition(focusedChild);
                        if (pos != RecyclerView.NO_POSITION && widgetAdapter != null) {
                            widgetAdapter.showWidgetContextMenu(pos, focusedChild);
                            return true;
                        }
                    }
                }
            }
        }
        if (sectionPresenter != null && sectionPresenter.handleKeyEvent(event)) {
            return true;
        }
        if (appReorderController != null && appReorderController.isReordering()) {
            if (appReorderController.handleKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    public int focusedTilePosition() {
        View f = getCurrentFocus();
        if (f == null || f.getId() != R.id.card || !(f.getParent() instanceof View)) return -1;
        return grid.getChildAdapterPosition((View) f.getParent());
    }

    public void toggleReorder() {
        if (appReorderController != null) appReorderController.toggleReorder(lastFocused);
    }

    public boolean beginReorder() {
        return appReorderController != null && appReorderController.beginReorder();
    }

    public void startDockEdit() {
        if (appReorderController != null) appReorderController.startDockEdit();
    }

    public void startDockEditFor(String key, boolean inDock) {
        if (appReorderController != null) appReorderController.startDockEditFor(key, inDock);
    }

    public void endReorder() {
        if (appReorderController != null) appReorderController.endReorder();
    }

    public boolean isReordering() {
        return appReorderController != null && appReorderController.isReordering();
    }

    public float tileAlpha(int pos) {
        return appReorderController != null ? appReorderController.tileAlpha(pos) : 1f;
    }

    public boolean isDockEditing() {
        return appReorderController != null && appReorderController.isDockEditing();
    }

    public void pickUp(int pos) {
        if (appReorderController != null) appReorderController.pickUp(pos);
    }

    public void keepScroll(Runnable change) {
        LinearLayoutManager lm = (LinearLayoutManager) grid.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        View firstView = first != RecyclerView.NO_POSITION ? lm.findViewByPosition(first) : null;
        int offset = firstView != null ? lm.getDecoratedLeft(firstView) - grid.getPaddingLeft() : 0;
        change.run();
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset);
    }

    @Override
    public void onBackPressed() {
        if (wallpaperPickerDrawer != null && wallpaperPickerDrawer.onBackPressed()) {
            return;
        }
        if (remoteControlDrawer != null && remoteControlDrawer.isShowing()) {
            remoteControlDrawer.dismiss();
            return;
        }
        if (remoteFileDrawer != null && remoteFileDrawer.isShowing()) {
            remoteFileDrawer.dismiss();
            return;
        }
        if (searchSettingsDrawer != null && searchSettingsDrawer.isShowing()) {
            searchSettingsDrawer.dismiss();
            return;
        }
        if (advancedSettingsDrawer != null && advancedSettingsDrawer.isShowing()) {
            if (advancedSettingsDrawer.isSubPanelOpen()) {
                advancedSettingsDrawer.closeSubPanel();
                return;
            }
            advancedSettingsDrawer.dismiss();
            return;
        }
        if (widgetManagerDrawer != null && widgetManagerDrawer.isShowing()) {
            widgetManagerDrawer.handleBackPress();
            return;
        }
        if (memoryCleanerDrawer != null && memoryCleanerDrawer.isShowing()) {
            if (memoryCleanerDrawer.isSubPanelOpen()) {
                memoryCleanerDrawer.closeSubPanel();
                return;
            }
            memoryCleanerDrawer.dismiss();
            return;
        }
        if (searchOverlayManager != null && searchOverlayManager.isShowing()) {
            searchOverlayManager.dismiss();
            return;
        }
        if (sectionPresenter != null && sectionPresenter.isSectionManagementMode()) {
            if (sectionPresenter.getReorderingSectionView() != null) {
                sectionPresenter.finishReorderingSection();
            } else {
                sectionPresenter.exitSectionManagement(null);
            }
            return;
        }
        if (mainScroll != null && (mainScroll.getScrollY() > 0
                || (movieSectionsContainer != null && movieSectionsContainer.hasFocus())
                || (utilityRecyclerView != null && utilityRecyclerView.hasFocus()))) {
            focusFirstAppTile();
            return;
        }
        // Home screen at top: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onLibraryRequested() {
        if (appLibrary != null) appLibrary.close();
        if (openPopup != null) openPopup.dismiss();
        libraryFocusPending = false;
        libraryFromTopBar = false;
        appLibrary = new AppLibraryDialog(this, animations, reducedMotion, appLibrarySelection, adapter::containsPackage, () -> reloadApps(false), () -> {
            appLibrary = null;
            openPopup = null;
            if (libraryFromTopBar) {
                libraryFromTopBar = false;
                btnLibraryTop.requestFocus();
                return;
            }
            libraryFocusPending = true;
            grid.post(() -> {
                if (libraryFocusPending && appLibrary == null && hasWindowFocus() && !isFinishing()) focusLibrary();
            });
        });
        openPopup = appLibrary.popup;
        appLibrary.show();
    }

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glass
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.getWidth() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }

        if (mainScroll != null && mainScroll.getScrollY() > 0) {
            boolean hasWidgets = (widgetRecyclerView != null && widgetRecyclerView.getVisibility() == View.VISIBLE
                    && widgetAdapter != null && widgetAdapter.getItemCount() > 0);
            if (!hasWidgets) {
                mainScroll.smoothScrollTo(0, 0);
                card.postDelayed(() -> syncGlassWithFocusedView(false), 120);
            } else {
                View targetHeader = (appsSectionTitle != null && appsSectionTitle.getVisibility() == View.VISIBLE) ? appsSectionTitle : grid;
                int topInScroll = getSectionTopInScroll(targetHeader != null ? targetHeader : grid);
                int targetScrollY = Math.max(0, topInScroll - (int) (24 * density));
                if (mainScroll.getScrollY() > targetScrollY) {
                    mainScroll.smoothScrollTo(0, targetScrollY);
                    card.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                }
            }
        }

        if (glassFocusPresenter == null) return;
        Rect r = glassFocusPresenter.alignGlassToCard(position, card, adapter, mainScroll, clockBlock);
        if (r == null) return;

        View item = (View) card.getParent();
        if (dockRow && position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (!animations || reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            if (heroAmbientPresenter != null) {
                heroAmbientPresenter.showHero(position, scroll ? r.top - dy : r.top, adapter);
            }
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            if (heroAmbientPresenter != null) {
                heroAmbientPresenter.showGlowOnly(position, adapter);
            }
            // Scroll just enough so the whole glass (not only the tile) is visible.
            float pad = 6f * density;
            revealRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, revealRect, reducedMotion);
        }
    }

    /** Refits the glass to the focused holder once the grid layout and tile dimensions settle. */
    private void refitGlass() {
        if (isDestroyed() || adapter.appCount() == 0 || libraryFocusPending) return;
        int pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() <= 0 || !card.isFocused()) return;
        float pad = 6f * density;
        float scale = adapter.focusScale();
        if (glassFocusPresenter != null && glassFocusPresenter.shouldRefit(card.getWidth(), card.getHeight(), scale, pad)) {
            onTileFocused(pos, card);
        }
    }

    public void syncGlassWithFocusedView(boolean animate) {
        if (glassFocusPresenter != null) {
            glassFocusPresenter.syncGlassWithFocusedView(getCurrentFocus(), animate, adapter, grid, mainScroll, clockBlock);
        }
    }

    public void syncGlassWithTarget(View focused, boolean animate) {
        if (glassFocusPresenter != null) {
            glassFocusPresenter.syncGlassWithTarget(focused, animate, adapter, grid, mainScroll, clockBlock);
        }
    }

    public void focusGlassOnCard(View card) {
        if (card != null && card.getId() == R.id.utilityCardRoot) {
            hideGlass();
            return;
        }
        if (glassFocusPresenter != null) {
            glassFocusPresenter.focusGlassOnCard(card, adapter, grid, mainScroll, clockBlock);
        }
    }

    public void hideGlass() {
        if (glassFocusPresenter != null) {
            glassFocusPresenter.hideGlass();
        }
    }

    private void hideHero() {
        if (heroAmbientPresenter != null) {
            heroAmbientPresenter.hideHero();
        }
    }

    // ---- optional system stats: handled by SystemStatsMonitor ------------------------------------

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.appCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (position > 0) {
                focusTile(position - 1);
                return true;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (position < count - 1) {
                focusTile(position + 1);
                return true;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (widgetRecyclerView != null && widgetRecyclerView.getVisibility() == View.VISIBLE
                    && widgetAdapter != null && widgetAdapter.getItemCount() > 0) {
                focusFirstVisibleWidget();
                if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
                return true;
            }
            firstTopButton().requestFocus();
            if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (focusFirstVisibleMovieSection()) return true;
            if (utilityRecyclerView != null && utilityRecyclerView.getVisibility() == View.VISIBLE) {
                focusFirstVisibleUtility();
                return true;
            }
            return true;
        }
        return false;
    }

    @Override public void onLibraryFocused() {
        libraryFocusPending = false;
        hideGlass();
        hideHero();
    }

    /** The first top-bar button after Inputs that is shown: Search, the network pill, Reorder, then Settings (always there). */
    private View firstTopButton() {
        if (btnGlobalSearch != null && btnGlobalSearch.getVisibility() == View.VISIBLE) return btnGlobalSearch;
        if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) return btnWifi;
        if (btnReorder != null && btnReorder.getVisibility() == View.VISIBLE) return btnReorder;
        return findViewById(R.id.btnSettings);
    }

    @Override public void onLibraryUp() {
        if (adapter.appCount() == 0) { firstTopButton().requestFocus(); return; }
        int lastRow = ((adapter.appCount() - 1) / COLUMNS) * COLUMNS;
        focusTile(Math.min(lastRow + lastFocused % COLUMNS, adapter.appCount() - 1));
    }

    private void focusLibrary() {
        cancelPendingFocusScroll();
        int position = adapter.appCount();
        if (!adapter.isLibraryAt(position)) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View button = holder.itemView.findViewById(R.id.btnLibrary);
            Rect visible = new Rect();
            if (button.getGlobalVisibleRect(visible) && visible.height() >= button.getHeight()) {
                if (button.requestFocus()) libraryFocusPending = false;
                return;
            }
        }
        if (!animations) {
            grid.scrollToPosition(position);
            grid.post(() -> {
                if (appLibrary != null || !hasWindowFocus()) return;
                RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
                if (footer != null && adapter.isLibraryAt(adapter.appCount())
                        && footer.itemView.findViewById(R.id.btnLibrary).requestFocus()) libraryFocusPending = false;
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder footer = rv.findViewHolderForAdapterPosition(position);
                if (footer != null && footer.itemView.findViewById(R.id.btnLibrary).requestFocus())
                    libraryFocusPending = false;
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(position);
    }

    private void cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll);
            pendingFocusScroll = null;
            grid.stopScroll();
        }
    }

    private void focusTile(int position) {
        libraryFocusPending = false;
        cancelPendingFocusScroll();
        if (adapter.appCount() == 0) {
            if (adapter.isLibraryAt(0)) focusLibrary();
            else firstTopButton().requestFocus();
            return;
        }
        final int pos = adapter.focusablePosition(Math.max(0, Math.min(position, adapter.appCount() - 1)));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            card.postDelayed(() -> syncGlassWithFocusedView(false), 120);
            card.postDelayed(() -> syncGlassWithFocusedView(false), 280);
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (!animations || pos == 0) {
            grid.scrollToPosition(pos);
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : grid.getChildAt(0);
                if (target != null) {
                    if (target.isFocused()) onTileFocused(pos, target);
                    else target.requestFocus();
                    target.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                    target.postDelayed(() -> syncGlassWithFocusedView(false), 280);
                } else {
                    grid.postDelayed(() -> {
                        RecyclerView.ViewHolder retry = grid.findViewHolderForAdapterPosition(pos);
                        View t = retry != null ? retry.itemView.findViewById(R.id.card) : grid.getChildAt(0);
                        if (t != null) {
                            if (t.isFocused()) onTileFocused(pos, t);
                            else t.requestFocus();
                            t.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                            t.postDelayed(() -> syncGlassWithFocusedView(false), 280);
                        }
                    }, 50);
                }
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) {
                    if (target.isFocused()) onTileFocused(pos, target);
                    else target.requestFocus();
                    target.postDelayed(() -> syncGlassWithFocusedView(false), 120);
                    target.postDelayed(() -> syncGlassWithFocusedView(false), 280);
                }
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /** Some TVs (Coocaa OS) set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private final Runnable clockCheck = this::updateClockVisibility;

    private void updateClockVisibility() {
        boolean valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS;
        clockBlock.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);
        handler.removeCallbacks(clockCheck);
        if (valid) {
            updateGreeting();
        } else {
            handler.postDelayed(clockCheck, 1_000L);
        }
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        // Locale-specific day parts (app language on Android 13+).
        // Indonesian: Pagi 00-10, Siang 10-15, Sore 15-18, Malam 18-24.
        // Spain (es-ES) and Catalan: "Buenas tardes" into the evening; "Buenas noches" from 21.
        // Latin American Spanish (es-MX, es-AR, …) keeps the default hours: evening strings are also
        // «Buenas noches», and there «Buenas noches» from ~17 matches local use better than Spain's 21.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if ("vi".equals(lang)) {
            if (hour >= 5 && hour < 11) res = R.string.good_morning;
            else if (hour >= 11 && hour < 14) res = R.string.good_noon;
            else if (hour >= 14 && hour < 18) res = R.string.good_afternoon;
            else if (hour >= 18 && hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        } else if ("in".equals(lang) || "id".equals(lang)) {
            if (hour < 10) res = R.string.good_morning;
            else if (hour < 15) res = R.string.good_afternoon;
            else if (hour < 18) res = R.string.good_evening;
            else res = R.string.good_night;
        } else if (("es".equals(lang) && "ES".equals(country)) || "ca".equals(lang)) {
            if (hour < 6) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 21) res = R.string.good_afternoon;
            else res = R.string.good_night;
        } else {
            if (hour < 5) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 17) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        }
        String festiveGreeting = festive ? festival.greetingText(getResources()) : "";
        if (!festiveGreeting.isEmpty()) {
            greeting.setText(festiveGreeting);
            greeting.setTextColor(liftTowardsWhite(festival.accentColor(), 0.25f));
        } else {
            greeting.setText(greetingName.isEmpty() ? getString(res)
                    : getString(R.string.greeting_with_name, getString(res), greetingName));
            greeting.setTextColor(greetingColor);
        }
        if (shamsi) shamsiDate.setText(Shamsi.today(getResources()));
        if (lunarDate != null) {
            if ("vi".equals(lang) || "VN".equals(country)) {
                lunarDate.setVisibility(View.VISIBLE);
                VietnameseLunarCalendar.LunarDate ld = VietnameseLunarCalendar.today();
                lunarDate.setText(ld.formatWithCanChi());
            } else {
                lunarDate.setVisibility(View.GONE);
            }
        }
    }

    // ---- race-weekend panel: handled by RaceWeekendPresenter ---------------------------------------

    private void updateRace() {
        if (racePresenter != null) {
            racePresenter.updateRace();
        }
    }

    private void refreshPillsAfterNextDraw() {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                // Posted from inside the frame's traversal, so it runs after that frame is drawn. A post straight
                // from onResume would run first: the frame is only scheduled after onResume returns.
                handler.post(() -> {
                    if (isDestroyed()) return;
                    updateWifiLabel();
                    updateInputsButton();
                });
                return true;
            }
        });
        root.invalidate();   // a resume that changes nothing on screen still draws once, so the refresh runs
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        // The diya's flame: 0 is out (offline), 1 to 4 follow the Wi-Fi signal, Ethernet burns steady at 4.
        int flame = 0;
        boolean steady = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
                flame = 4;
                steady = true;
            } else if (wifi) {
                String foundSsid = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && cm != null) {
                    Network activeNet = cm.getActiveNetwork();
                    if (activeNet != null) {
                        NetworkCapabilities actCaps = cm.getNetworkCapabilities(activeNet);
                        if (actCaps != null && actCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            android.net.TransportInfo ti = actCaps.getTransportInfo();
                            if (ti instanceof WifiInfo) {
                                WifiInfo wi = (WifiInfo) ti;
                                String s = wi.getSSID();
                                if (s != null) {
                                    s = s.replace("\"", "").trim();
                                    if (!s.isEmpty() && !s.contains("unknown") && !s.equals("0x")) {
                                        foundSsid = s;
                                    }
                                }
                            }
                        }
                    }
                }
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                flame = info != null ? Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(info.getRssi(), 5))) : 3;
                if (foundSsid == null && info != null) {
                    String ssid = info.getSSID();
                    if (ssid != null) {
                        ssid = ssid.replace("\"", "").trim();
                        if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) {
                            foundSsid = ssid;
                        }
                    }
                }
                if (foundSsid != null) {
                    text = foundSsid;
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The Wi-Fi icon and its
            // gap are counted by hand: until the pill has a resolved layout direction (the first call, from
            // onResume, runs before that) getTotalPaddingStart/End leave the compound drawables out, which
            // left the name about 31dp too much room and the badge cut off.
            TextPaint paint = btnWifi.getPaint();
            Drawable[] compound = btnWifi.getCompoundDrawablesRelative();
            int iconRoom = compound[0] != null ? compound[0].getIntrinsicWidth() + btnWifi.getCompoundDrawablePadding() : 0;
            float room = btnWifi.getMaxWidth() - btnWifi.getPaddingStart() - btnWifi.getPaddingEnd() - iconRoom
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            String name = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            // A long badge ("VPN отключён") can leave room for a stub like "MT…", which says nothing and, after
            // rounding, can push the badge itself out of the pill. Show the badge alone then. On Ethernet the
            // icon already names the link, and "Ethernet" cut to "Ethe…" or not depended on the font size (#961),
            // so Ethernet always shows the icon and the badge alone.
            // The space stays even then: a line holding only the span gets no font metrics and collapses.
            boolean stub = name.length() < text.length() && name.length() < 5;
            text = stub || icon == R.drawable.ic_ethernet ? "" : name;
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!TextUtils.equals(btnWifi.getText(), label)) btnWifi.setText(label);
        } else if (!TextUtils.equals(btnWifi.getText(), text)) {
            btnWifi.setText(text);
        }
        if (wifiIconOnly) {
            btnWifi.setContentDescription(btnWifi.getText());
            btnWifi.setText(null);
        }
        if (festive && festiveController != null) {
            if (online && flame == 0) flame = 3;   // online some other way (cellular, USB tethering)
            festiveController.updateDiyaState(steady, flame);
        } else {
            if (icon != wifiIcon) {
                wifiIcon = icon;
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            }
            ColorStateList tint = online ? wifiOnlineTint : wifiPlainTint;
            if (wifiOnlineTint != null && btnWifi.getCompoundDrawableTintList() != tint) {
                btnWifi.setCompoundDrawableTintList(tint);
            }
        }
    }

    /** config.xml icon_only_buttons. */
    private boolean iconOnlyButtons;
    /** config.xml top_menu: Inputs, Wi-Fi and Settings folded into one gear. */
    private boolean topMenu;
    /** show_inputs_pill as configured: top_menu clears showInputsPill, so its Inputs row reads this. */
    private boolean showInputsMenuRow = true;
    private boolean launcherSettingsButton;
    /** icon_only_buttons without wifi_label_width: the Wi-Fi pill shows no network name. */
    private boolean wifiIconOnly;
    /** config.xml wifi_status_color: the Wi-Fi icon's green while online and its usual pill colours; null while off. */
    private ColorStateList wifiOnlineTint, wifiPlainTint;
    /** The app library button in the top bar (library_button). */
    private View btnLibraryTop;
    /** The open library came from that button: closing it returns there instead of scrolling to the grid's end. */
    private boolean libraryFromTopBar;

    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    private void makeIconOnly(TextView pill) {
        pill.setContentDescription(pill.getText());
        pill.setText(null);
        pill.setMaxWidth(Integer.MAX_VALUE);
        int pad = Math.round(10 * density);
        pill.setPaddingRelative(pad, 0, pad, 0);
        pill.setCompoundDrawablePadding(0);
        pill.getLayoutParams().width = Math.round(42 * density);
    }

    public void updateKidModeUiState() {
        boolean kidActive = (appLockManager != null && appLockManager.isKidModeActive());
        if (btnSettings != null) {
            if (kidActive) {
                btnSettings.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_exit_door, 0, 0, 0);
                btnSettings.setText("");
                btnSettings.setContentDescription("Thoát Chế độ trẻ em");
                makeIconOnly(btnSettings);
            } else {
                if (launcherSettingsButton) {
                    btnSettings.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_home_settings_pill, 0, 0, 0);
                } else {
                    btnSettings.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
                }
                btnSettings.setText(R.string.settings);
                btnSettings.setContentDescription(getString(R.string.settings));
                makeIconOnly(btnSettings);
            }
        }
        if (utilityAdapter != null) {
            utilityAdapter.notifyDataSetChanged();
        }
    }

    // ---- festive skin ------------------------------------------------------------------------------

    // ---- festive skin: handled by FestiveSkinController --------------------------------------------

    private static int liftTowardsWhite(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /** Every settings entry shares the standard intent and the vendor fallback (SystemSettings). */
    private Intent systemIntent(String standardAction) {
        return SystemSettings.intent(this, standardAction);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- TV inputs and Live TV: handled by TvInputController -----------------------------------------

    private void updateInputsButton() {
        if (inputController != null) inputController.updateInputsButton(btnInputs);
    }

    private boolean openLiveTvAfterBoot() {
        return inputController != null && inputController.openLiveTvAfterBoot();
    }

    private void showInputs() {
        if (inputController != null) {
            View anchor = findViewById(topMenu ? R.id.btnSettings : R.id.btnInputs);
            inputController.showInputs(anchor, root);
        }
    }

    private PopupWindow glassPopup(View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
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

    // ---- launcher settings and module cards -------------------------------------------------------

    /** top_menu: the gear's dropdown, standing in for the Inputs, Wi-Fi and Settings pills. */
    private void showTopMenu() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (220 * density));
        // Inputs opens its own list once this one has closed.
        if (showInputsMenuRow) addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, () -> root.post(this::showInputs));
        addInputRow(rows, popup, R.drawable.ic_wifi, getString(R.string.quick_network), -1, this::openNetworkPill);
        CharSequence network = wifiIconOnly ? btnWifi.getContentDescription() : btnWifi.getText();
        if (!TextUtils.isEmpty(network)) {
            TextView status = rows.getChildAt(rows.getChildCount() - 1).findViewById(R.id.status);
            status.setText(network);
            status.setVisibility(View.VISIBLE);
        }
        addInputRow(rows, popup, R.drawable.ic_swap_vert, getString(R.string.reorder), -1, this::toggleReorder);
        if (launcherSettingsButton) {
            addInputRow(rows, popup, R.drawable.ic_home_settings_pill, getString(R.string.launcher_settings), -1,
                    () -> startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST));
        } else {
            addInputRow(rows, popup, R.drawable.ic_settings, getString(R.string.settings), -1,
                    () -> open(systemIntent(Settings.ACTION_SETTINGS)));
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (220 * density), Math.min(panel.getMeasuredWidth(), (int) (360 * density))));
        dropIn(popup, panel, anchor, getResources().getBoolean(R.bool.rtl_menu_bar) ? Gravity.START : Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    /** The header opens launcher settings directly; hold OK keeps the extra home actions available. */
    private void showSettingsMenu() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (200 * density));
        if (!launcherSettingsButton) addSettingsMenuRow(rows, popup, R.string.launcher_settings, () -> startActivityForResult(
                new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST));
        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, this::showModuleCards);
        if (dockRow) addSettingsMenuRow(rows, popup, R.string.dock_edit, this::startDockEdit);
        addSettingsMenuRow(rows, popup, R.string.reorder, this::toggleReorder);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * density), panel.getMeasuredWidth()));
        // Hebrew puts Settings at the left of the header: grow the menu inward from it.
        dropIn(popup, panel, anchor, getResources().getBoolean(R.bool.rtl_menu_bar)
                ? Gravity.START : Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    /**
     * Hold OK on a tile: a glass menu next to it. Move starts reorder mode with this tile already picked up;
     * Remove from Home takes the shortcut off (only with the app library on, where one press adds it back);
     * App info opens the TV's page for the app, which is what holding OK used to do.
     */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (tileMenuEnabled && !isReordering() && openPopup == null && cardPrompt == null) {
                int position = focusedTilePosition();
                if (position >= 0 && position < adapter.appCount()
                        && !getPackageName().equals(adapter.packageAt(position))) {
                    tileMenuJustTriggered = true;
                    onTileLongPress(position, getCurrentFocus());
                    return true;
                }
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void onTileLongPress(int position, View card) {
        if (appLockManager != null && appLockManager.isKidModeActive()) {
            Toast.makeText(this, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isReordering()) return;
        String key = adapter.keyAt(position);
        String pkg = adapter.packageAt(position);
        // Empty dock slots and the Default-home tile carry the launcher's own package: no menu for them.
        if (key == null || pkg == null || pkg.equals(getPackageName())) return;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (200 * density));
        if (dockRow && position < adapter.movableCount()) {
            boolean inDock = position < COLUMNS;
            addSettingsMenuRow(rows, popup, inDock ? R.string.tile_replace_in_dock : R.string.tile_add_to_dock,
                    () -> startDockEditFor(key, inDock));
        }
        if (position < adapter.movableCount()) addSettingsMenuRow(rows, popup, R.string.tile_move, () -> {
            if (!beginReorder()) return;
            int at = adapter.positionOf(key);   // beginReorder can rebuild the tiles
            if (at < 0) return;
            focusTile(at);
            pickUp(at);
            Toast.makeText(this, R.string.reorder_hint, Toast.LENGTH_LONG).show();
        });
        if (appLibraryEnabled) addSettingsMenuRow(rows, popup, R.string.library_remove_shortcut, () -> {
            if (LibraryApps.save(this, pkg, false)) reloadApps(false);
            else Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show();
        });
        boolean isSystem = false;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Exception ignored) {}

        if (!isSystem && !appLockManager.isKidModeActive()) {
            addSettingsMenuRow(rows, popup, R.string.tile_uninstall, () -> {
                try {
                    Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
                    startActivity(uninstall);
                } catch (Exception e) {
                    try {
                        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
                        startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            });
        }
        addSettingsMenuRow(rows, popup, R.string.app_info, () -> open(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));

        boolean isLocked = appLockManager.isAppLocked(pkg);
        addSettingsMenuRow(rows, popup, isLocked ? R.string.unlock_app : R.string.lock_app, () -> {
            new PinLockDialog(this, PinLockDialog.MODE_VERIFY,
                    isLocked ? "Mở khóa app" : "Khóa app",
                    isLocked ? "Nhập mã để mở khóa" : "Nhập mã để khóa",
                    new PinLockDialog.PinResultListener() {
                @Override
                public void onSuccess() {
                    appLockManager.setAppLocked(pkg, !isLocked);
                    reloadApps(false);
                    Toast.makeText(MainActivity.this, !isLocked ? "Đã khóa app" : "Đã mở khóa", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancel() {}
            }).show();
        });

        // Tier 1: Casual UI Hide (Direct, no PIN required)
        addSettingsMenuRow(rows, popup, R.string.hide_app, () -> {
            appLockManager.setCasualHidden(pkg, true);
            reloadApps(false);
            Toast.makeText(MainActivity.this, "Đã ẩn ứng dụng", Toast.LENGTH_SHORT).show();
        });
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * density), panel.getMeasuredWidth()));
        // Dock tiles sit near the bottom of the screen: open the menu above the tile when it would not fit below.
        int[] at = new int[2];
        card.getLocationInWindow(at);
        int gap = (int) (10 * density), height = panel.getMeasuredHeight();
        if (at[1] + card.getHeight() + gap + height <= root.getHeight()) {
            dropIn(popup, panel, card, Gravity.START);
        } else {
            panel.setAlpha(0f);
            panel.setPivotY(height);
            panel.setScaleY(0.92f);
            panel.setTranslationY(8 * density);
            popup.showAtLocation(root, Gravity.NO_GRAVITY, at[0], Math.max(0, at[1] - gap - height));
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
        rows.getChildAt(0).requestFocus();
    }

    private void addSettingsMenuRow(LinearLayout rows, PopupWindow popup, int title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        // The OK-up after opening the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                popup.dismiss();
                action.run();
            }
            return true;
        });
        rows.addView(row);
    }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()
                || isReordering()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        // Hebrew places Settings on the left: align the outer panel inward, like its parent menu.
        dropIn(popup, panel, anchor, getResources().getBoolean(R.bool.rtl_menu_bar)
                ? Gravity.START : Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }

    // ---- TV Recommendations: handled by TvRecommendationPresenter ----------------------------------

    private void scheduleRecommendationRetries() {
        if (recPresenter != null) recPresenter.scheduleRecommendationRetries();
    }

    private void registerTvObserver() {
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) return;
        if (recPresenter != null) recPresenter.registerTvObserver();
    }

    private void unregisterTvObserver() {
        if (recPresenter != null) recPresenter.unregisterTvObserver();
    }

    private void loadRecommendations() {
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) return;
        if (recPresenter != null) recPresenter.loadRecommendations();
    }

    private View currentFocusedSection = null;

    private void ensureSectionVisible(View secView, boolean isFirstSection) {
        if (mainScroll == null || secView == null) return;
        if (currentFocusedSection == secView) {
            // Already in this section; avoid redundant vertical scroll when moving horizontally
            return;
        }
        currentFocusedSection = secView;

        if (isFirstSection) {
            if (mainScroll.getScrollY() != 0) {
                mainScroll.smoothScrollTo(0, 0);
            }
            return;
        }

        secView.post(() -> {
            if (mainScroll == null || mainScroll.getChildCount() == 0) return;
            int topInScroll = getSectionTopInScroll(secView);
            int targetY = Math.max(0, topInScroll - (int) (14 * density));
            int maxScroll = Math.max(0, mainScroll.getChildAt(0).getHeight() - mainScroll.getHeight());
            targetY = Math.min(targetY, maxScroll);

            if (Math.abs(mainScroll.getScrollY() - targetY) > (int) (4 * density)) {
                mainScroll.smoothScrollTo(0, targetY);
            }
            secView.postDelayed(() -> syncGlassWithFocusedView(false), 80);
            secView.postDelayed(() -> syncGlassWithFocusedView(false), 240);
        });
    }

    private void scrollSectionFullyIntoView(View secView) {
        if (mainScroll == null || secView == null) return;
        currentFocusedSection = secView;
        secView.post(() -> {
            if (mainScroll == null) return;
            Rect rect = new Rect(0, 0, secView.getWidth(), secView.getHeight() + (int) (28 * density));
            mainScroll.offsetDescendantRectToMyCoords(secView, rect);
            int scrollBottom = mainScroll.getScrollY() + mainScroll.getHeight() - mainScroll.getPaddingBottom();
            if (rect.bottom > scrollBottom) {
                mainScroll.smoothScrollBy(0, rect.bottom - scrollBottom);
            } else if (rect.top < mainScroll.getScrollY() + mainScroll.getPaddingTop()) {
                mainScroll.smoothScrollBy(0, rect.top - (mainScroll.getScrollY() + mainScroll.getPaddingTop()));
            }
            secView.postDelayed(() -> syncGlassWithFocusedView(false), 80);
            secView.postDelayed(() -> syncGlassWithFocusedView(false), 240);
        });
    }

    private int getSectionTopInScroll(View target) {
        int top = 0;
        View curr = target;
        while (curr != null && curr != mainScroll) {
            top += curr.getTop();
            ViewParent p = curr.getParent();
            curr = (p instanceof View) ? (View) p : null;
        }
        return top;
    }

    private void focusSectionCard(RecyclerView targetRv, View sectionWrapper) {
        if (targetRv == null || sectionWrapper == null) return;
        ensureSectionVisible(sectionWrapper, false);
        targetRv.scrollToPosition(0);
        targetRv.post(() -> {
            View card = null;
            if (targetRv.getChildCount() > 0) {
                for (int i = 0; i < targetRv.getChildCount(); i++) {
                    View ch = targetRv.getChildAt(i);
                    View c = (ch.getId() == R.id.card) ? ch : ch.findViewById(R.id.card);
                    if (c != null && c.getVisibility() == View.VISIBLE) {
                        card = c;
                        break;
                    }
                }
            }
            if (card == null) {
                RecyclerView.ViewHolder vh = targetRv.findViewHolderForAdapterPosition(0);
                if (vh != null) {
                    card = (vh.itemView.getId() == R.id.card) ? vh.itemView : vh.itemView.findViewById(R.id.card);
                }
            }
            if (card != null) {
                card.requestFocus();
                focusGlassOnCard(card);
                card.post(() -> syncGlassWithFocusedView(false));
                card.postDelayed(() -> syncGlassWithFocusedView(false), 200);
            } else {
                targetRv.requestFocus();
            }
        });
    }

    private void setupWidgetSection() {
        widgetRecyclerView = findViewById(R.id.widgetRecyclerView);
        widgetSectionTitle = findViewById(R.id.widgetSectionTitle);
        weatherManager = new WeatherDataManager(this);
        newsManager = new NewsDataManager(this);
        youtubeManager = new YouTubeTrendingManager(this);
        usageTracker = new UsageStatsTracker(this);
        mediaTracker = new MediaSessionTracker(this);
        footballManager = com.next.tv.launcher.widget.FootballDataManager.getInstance(this);

        if (widgetRecyclerView == null) return;

        widgetAdapter = new WidgetSectionAdapter(this, advancedEffects, adapter.focusScale(),
                cardView -> {
                    focusGlassOnCard(cardView);
                    if (mainScroll != null && mainScroll.getScrollY() != 0) {
                        mainScroll.smoothScrollTo(0, 0);
                    }
                },
                (type, data) -> handleWidgetClick(type, data),
                (card, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        firstTopButton().requestFocus();
                        if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (adapter != null && adapter.appCount() > 0) {
                            focusTile(lastFocused % COLUMNS);
                            return true;
                        }
                        if (focusFirstVisibleMovieSection()) return true;
                        if (utilityRecyclerView != null && utilityRecyclerView.getVisibility() == View.VISIBLE) {
                            focusFirstVisibleUtility();
                            return true;
                        }
                        return true;
                    }
                    return false;
                }
        );

        widgetRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                int left = child.getLeft() + rect.left;
                int right = left + rect.width();
                int limitRight = getWidth() - getPaddingRight();
                int dx = 0;
                if (left < getPaddingLeft()) dx = left - getPaddingLeft();
                else if (right > limitRight) dx = right - limitRight;
                if (dx == 0) return false;
                if (immediate || reducedMotion || !animations) parent.scrollBy(dx, 0);
                else parent.smoothScrollBy(dx, 0);
                return true;
            }
        });

        widgetRecyclerView.setHasFixedSize(false);
        widgetRecyclerView.setItemAnimator(null);
        widgetAdapter.setOnWidgetListChangedListener(count -> {
            if (widgetSectionTitle != null) {
                widgetSectionTitle.setVisibility(View.GONE);
            }
        });
        widgetRecyclerView.setAdapter(widgetAdapter);
        widgetRecyclerView.setOnVerticalNavListener(direction -> {
            Log.d("TvHome.Nav", "widget onVerticalNav direction=" + direction + " apps=" + (adapter != null ? adapter.appCount() : -1));
            if (direction == View.FOCUS_UP) {
                firstTopButton().requestFocus();
                if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
                return true;
            }
            if (direction == View.FOCUS_DOWN) {
                if (adapter != null && adapter.appCount() > 0) {
                    currentFocusedSection = grid;
                    grid.requestFocus();
                    focusTile(Math.min(lastFocused, adapter.appCount() - 1));
                    if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
                    return true;
                }
                if (focusFirstVisibleMovieSection()) {
                    return true;
                }
                if (utilityRecyclerView != null && utilityRecyclerView.getVisibility() == View.VISIBLE) {
                    Log.d("TvHome.Nav", "focusing utility");
                    focusFirstVisibleUtility();
                    return true;
                }
                return true;
            }
            return false;
        });

        widgetRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (glassFocusPresenter != null) glassFocusPresenter.onScrolled(dx, dy);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    syncGlassWithFocusedView(false);
                }
            }
        });

        // Instant local cache binding (0 ms cold start)
        org.json.JSONObject cachedBatch = com.next.tv.launcher.widget.WidgetProtocolEngine.getInstance(this).getCachedBatchJson();
        if (cachedBatch != null) {
            widgetAdapter.applyBatchProtocolJson(cachedBatch);
        } else {
            WeatherDataManager.WeatherInfo cachedWeather = weatherManager.getCachedWeather();
            if (cachedWeather != null) widgetAdapter.setWeatherData(cachedWeather);
            List<YouTubeTrendingManager.YouTubeVideoItem> cachedVideos = youtubeManager.getCachedVideos();
            if (cachedVideos != null && !cachedVideos.isEmpty()) widgetAdapter.setYouTubeData(cachedVideos);
            List<NewsDataManager.NewsItem> cachedNews = newsManager.getCachedNews();
            if (cachedNews != null && !cachedNews.isEmpty()) widgetAdapter.setNewsData(cachedNews);
            List<com.next.tv.launcher.widget.FootballDataManager.FootballMatchItem> cachedSports = footballManager.getCachedMatches();
            if (cachedSports != null && !cachedSports.isEmpty()) widgetAdapter.setFootballData(cachedSports);
        }
        widgetAdapter.setUsageData(usageTracker.getTodayUsage());
        widgetAdapter.setMediaData(mediaTracker.getPlaybackInfo());

        if (widgetAddonListener == null) {
            widgetAddonListener = () -> {
                if (widgetAdapter != null) {
                    widgetAdapter.rebuildActiveTypes();
                    refreshWidgetsBackground();
                }
            };
            com.next.tv.launcher.widget.WidgetAddonManager.getInstance(this).addListener(widgetAddonListener);
        }

        refreshWidgetsBackground();
    }

    private boolean focusFirstVisibleMovieSection() {
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            return false;
        }
        if (movieSectionsContainer != null && movieSectionsContainer.getVisibility() == View.VISIBLE
                && movieSectionsContainer.getChildCount() > 0) {
            for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
                View sec = movieSectionsContainer.getChildAt(i);
                if (sec.getVisibility() != View.VISIBLE) continue;
                RecyclerView rv = sec.findViewById(R.id.sectionRecyclerView);
                if (rv != null && rv.getAdapter() != null && rv.getAdapter().getItemCount() > 0) {
                    focusSectionCard(rv, sec);
                    return true;
                }
            }
        }
        return false;
    }

    private void focusFirstVisibleWidget() {
        if (widgetRecyclerView == null) return;
        int childCount = widgetRecyclerView.getChildCount();
        if (childCount > 0) {
            View firstChild = widgetRecyclerView.getChildAt(0);
            View card = firstChild.findViewById(R.id.card);
            if (card != null) {
                card.requestFocus();
                focusGlassOnCard(card);
                return;
            }
        }
        widgetRecyclerView.requestFocus();
    }

    private void focusFirstVisibleUtility() {
        if (utilityRecyclerView == null || utilityRecyclerView.getVisibility() != View.VISIBLE) return;
        ensureSectionVisible(utilityRecyclerView, false);
        int count = utilityRecyclerView.getChildCount();
        if (count > 0) {
            View child = utilityRecyclerView.getChildAt(0);
            if (child != null) {
                child.requestFocus();
                hideGlass();
                return;
            }
        }
        utilityRecyclerView.scrollToPosition(0);
        utilityRecyclerView.post(() -> {
            int c = utilityRecyclerView.getChildCount();
            if (c > 0) {
                View child = utilityRecyclerView.getChildAt(0);
                if (child != null) {
                    child.requestFocus();
                    hideGlass();
                }
            } else {
                utilityRecyclerView.requestFocus();
            }
        });
    }

    public void focusUtilityType(int type) {
        if (utilityAdapter != null) {
            int pos = utilityAdapter.getPositionForType(type);
            if (pos >= 0) {
                focusUtilityItem(pos);
            }
        }
    }

    public void focusUtilityItem(int position) {
        if (utilityRecyclerView == null) return;
        ensureSectionVisible(utilityRecyclerView, false);
        RecyclerView.ViewHolder vh = utilityRecyclerView.findViewHolderForAdapterPosition(position);
        if (vh != null && vh.itemView != null) {
            vh.itemView.requestFocus();
            hideGlass();
        } else {
            utilityRecyclerView.scrollToPosition(position);
            utilityRecyclerView.postDelayed(() -> {
                RecyclerView.ViewHolder vhDelayed = utilityRecyclerView.findViewHolderForAdapterPosition(position);
                if (vhDelayed != null && vhDelayed.itemView != null) {
                    vhDelayed.itemView.requestFocus();
                    hideGlass();
                } else {
                    utilityRecyclerView.requestFocus();
                }
            }, 60);
        }
    }

    private void refreshWidgetsBackground() {
        if (recHandler == null) return;
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) return;
        String currentSport = (footballManager != null) ? footballManager.getSelectedSportType() : "soccer";
        com.next.tv.launcher.widget.WidgetProtocolEngine.getInstance(this).fetchBatchAsync(currentSport, widgetsObj -> {
            if (widgetAdapter != null && widgetsObj != null) {
                widgetAdapter.applyBatchProtocolJson(widgetsObj);
            }
            // Fallback to native YouTubeTrendingManager if worker batch does not contain valid videos
            org.json.JSONObject yObj = (widgetsObj != null) ? widgetsObj.optJSONObject("youtube") : null;
            org.json.JSONArray yItems = (yObj != null) ? yObj.optJSONArray("items") : null;
            if (yItems == null || yItems.length() == 0) {
                if (youtubeManager != null && recHandler != null) {
                    recHandler.post(() -> {
                        List<YouTubeTrendingManager.YouTubeVideoItem> videos = youtubeManager.fetchSync();
                        if (videos != null && !videos.isEmpty() && widgetAdapter != null) {
                            runOnUiThread(() -> widgetAdapter.setYouTubeData(videos));
                        }
                    });
                }
            }
        });
    }

    private void handleWidgetClick(int type, Object data) {
        if (type == WidgetSectionAdapter.TYPE_WEATHER) {
            try {
                Intent weatherIntent = new Intent(this, com.next.tv.launcher.weather.WeatherDetailActivity.class);
                weatherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(weatherIntent);
            } catch (Exception e) {
                if (data instanceof WeatherDataManager.WeatherInfo) {
                    WeatherDataManager.WeatherInfo info = (WeatherDataManager.WeatherInfo) data;
                    Toast.makeText(this, info.city + ": " + Math.round(info.temperature) + "°C", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (type == WidgetSectionAdapter.TYPE_YOUTUBE) {
            if (data instanceof YouTubeTrendingManager.YouTubeVideoItem) {
                YouTubeTrendingManager.YouTubeVideoItem item = (YouTubeTrendingManager.YouTubeVideoItem) data;
                launchYouTubeVideo(item.videoId);
            }
        } else if (type == WidgetSectionAdapter.TYPE_NEWS) {
            if (data instanceof NewsDataManager.NewsItem) {
                NewsDataManager.NewsItem item = (NewsDataManager.NewsItem) data;
                if (item.link != null && !item.link.isEmpty()) {
                    try {
                        Intent readerIntent = new Intent(this, com.next.tv.launcher.browser.NativeReaderActivity.class);
                        readerIntent.putExtra("url", item.link);
                        readerIntent.putExtra("title", item.title);
                        readerIntent.putExtra("source", item.source);
                        readerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(readerIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, item.title, Toast.LENGTH_LONG).show();
                    }
                }
            }
        } else if (type == WidgetSectionAdapter.TYPE_USAGE) {
            try {
                startActivity(UsageStatsTracker.getPermissionIntent());
            } catch (Exception e) {
                Toast.makeText(this, "Cài đặt theo dõi", Toast.LENGTH_SHORT).show();
            }
        } else if (type == WidgetSectionAdapter.TYPE_MEDIA) {
            if (data instanceof MediaSessionTracker.MediaPlaybackInfo) {
                MediaSessionTracker.MediaPlaybackInfo info = (MediaSessionTracker.MediaPlaybackInfo) data;
                if (info.isPlaying && info.appPackage != null) {
                    Intent launchIntent = getPackageManager().getLeanbackLaunchIntentForPackage(info.appPackage);
                    if (launchIntent == null) {
                        launchIntent = getPackageManager().getLaunchIntentForPackage(info.appPackage);
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                } else {
                    Toast.makeText(this, "Chưa phát nhạc", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (type == WidgetSectionAdapter.TYPE_FOOTBALL) {
            if (data instanceof String && ((String) data).startsWith("change_sport:")) {
                String newSport = ((String) data).substring("change_sport:".length());
                if (footballManager != null) {
                    footballManager.setSelectedSportType(newSport);
                }
                com.next.tv.launcher.widget.WidgetProtocolEngine.getInstance(this).fetchBatchAsync(newSport, widgetsObj -> {
                    if (widgetAdapter != null && widgetsObj != null) {
                        widgetAdapter.applyBatchProtocolJson(widgetsObj);
                    }
                });
            } else if (data instanceof com.next.tv.launcher.widget.FootballDataManager.FootballMatchItem) {
                com.next.tv.launcher.widget.FootballDataManager.FootballMatchItem match = (com.next.tv.launcher.widget.FootballDataManager.FootballMatchItem) data;
                String scoreStr = ("in".equalsIgnoreCase(match.statusState) || "post".equalsIgnoreCase(match.statusState)) ? (match.homeScore + " - " + match.awayScore) : "Chưa đấu";
                Toast.makeText(this, match.homeTeamName + " vs " + match.awayTeamName + " (" + scoreStr + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchYouTubeVideo(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;

        PackageManager pm = getPackageManager();
        String targetPkg = null;
        String[] candidates = {
                "com.teamsmart.videomanager.tv",
                "com.liskovsoft.videomanager",
                "com.liskovsoft.smarttubetv.beta",
                "com.google.android.youtube.tv",
                "com.google.android.youtube",
                "app.revanced.android.youtube"
        };

        for (String pkg : candidates) {
            try {
                pm.getPackageInfo(pkg, 0);
                targetPkg = pkg;
                break;
            } catch (Exception ignored) {}
        }

        String webUrl = "https://www.youtube.com/watch?v=" + videoId;

        if (targetPkg == null) {
            try {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                List<ResolveInfo> resolves = pm.queryIntentActivities(viewIntent, 0);
                if (resolves != null) {
                    for (ResolveInfo ri : resolves) {
                        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            String p = ri.activityInfo.packageName.toLowerCase();
                            if (p.contains("youtube") || p.contains("smarttube") || p.contains("videomanager")) {
                                targetPkg = ri.activityInfo.packageName;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (targetPkg != null) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                intent.setPackage(targetPkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception e) {
                Log.w("TvHome", "Failed to launch native YouTube app " + targetPkg + ": " + e.getMessage());
            }
        }

        // Fallback: Khi không có app YouTube nào cài trên TV, mới mở WebBrowserActivity (WebView)
        try {
            Intent browserIntent = new Intent(this, com.next.tv.launcher.browser.WebBrowserActivity.class);
            browserIntent.setData(Uri.parse(webUrl));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được video", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupUtilitySection() {
        utilitySectionTitle = findViewById(R.id.utilitySectionTitle);
        if (utilitySectionTitle != null) {
            utilitySectionTitle.setFocusable(false);
            utilitySectionTitle.setClickable(false);
        }

        utilityRecyclerView = findViewById(R.id.utilityRecyclerView);
        if (utilityRecyclerView == null) return;
        utilityRecyclerView.setItemViewCacheSize(4);
        utilityRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        utilityRecyclerView.setOnVerticalNavListener(direction -> {
            if (direction == View.FOCUS_UP) {
                if (movieSectionsContainer != null && movieSectionsContainer.getChildCount() > 0) {
                    for (int i = movieSectionsContainer.getChildCount() - 1; i >= 0; i--) {
                        View sec = movieSectionsContainer.getChildAt(i);
                        if (sec.getVisibility() == View.VISIBLE) {
                            RecyclerView rv = sec.findViewById(R.id.sectionRecyclerView);
                            if (rv != null) {
                                focusSectionCard(rv, sec);
                                return true;
                            }
                        }
                    }
                }
                currentFocusedSection = grid;
                grid.requestFocus();
                focusTile(Math.min(lastFocused, adapter.appCount() - 1));
                return true;
            }
            return true;
        });
        utilityAdapter = new UtilitySectionAdapter(this,
                cardView -> {
                    hideGlass();
                    ensureSectionVisible(utilityRecyclerView, false);
                },
                this::showHiddenAppsManagerDialog,
                this::showRemoteControlDrawer,
                this::showRemoteFileDrawer,
                this::showWallpaperPicker,
                this::showWidgetManagerDrawer,
                this::showAdvancedSettingsDrawer);
        utilityAdapter.setOnKidModeRequestedListener(this::showKidModeControlDrawer);
        utilityAdapter.setOnMemoryCleanerRequestedListener(this::showMemoryCleanerDrawer);
        utilityAdapter.setMode(currentUserMode);
        utilityRecyclerView.setAdapter(utilityAdapter);
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean enabled) {
        this.reducedMotion = enabled;
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("reduced_motion", enabled)
                .apply();

        if (glassFocusPresenter != null) glassFocusPresenter.setReducedMotion(enabled);
        if (heroAmbientPresenter != null) heroAmbientPresenter.setReducedMotion(enabled);

        // Update festive skin animations
        if (festiveController != null) festiveController.setReducedMotion(enabled);

        // Update grid layout animation
        if (grid != null) {
            if (animations && !enabled) {
                grid.setLayoutAnimation(new LayoutAnimationController(
                        AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
            } else {
                grid.setLayoutAnimation(null);
            }
        }

        // Invalidate adapter if active
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private SearchSettingsDrawer ensureSearchSettingsDrawer() {
        if (searchSettingsDrawer == null) {
            searchSettingsDrawer = new SearchSettingsDrawer(this);
        }
        return searchSettingsDrawer;
    }

    public void showAdvancedSettingsDrawer() {
        showAdvancedSettingsDrawer(null);
    }

    public void showAdvancedSettingsDrawer(View triggerView) {
        if (advancedSettingsDrawer == null) {
            advancedSettingsDrawer = new AdvancedSettingsDrawer(this, this::setReducedMotion);
        }
        advancedSettingsDrawer.show(triggerView);
    }

    public void showWidgetManagerDrawer() {
        showWidgetManagerDrawer(null);
    }

    public void showWidgetManagerDrawer(View triggerView) {
        if (widgetManagerDrawer == null) {
            widgetManagerDrawer = new WidgetManagerDrawer(this);
        }
        widgetManagerDrawer.show(triggerView);
    }

    public void showMemoryCleanerDrawer(View triggerView) {
        if (memoryCleanerDrawer == null) {
            memoryCleanerDrawer = new com.next.tv.launcher.clean.MemoryCleanerDrawer(this);
        }
        memoryCleanerDrawer.show(triggerView);
    }

    private final Runnable smartRamPeriodicRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isResumed) return;
            com.next.tv.launcher.clean.SmartRamConfigManager config = com.next.tv.launcher.clean.SmartRamConfigManager.getInstance(MainActivity.this);
            int interval = config.getCronIntervalMinutes();
            if (interval >= 5) {
                com.next.tv.launcher.clean.SmartRamGovernor.cleanRamSmartAsync(MainActivity.this, false, false, res -> {
                    if (utilityAdapter != null) {
                        utilityAdapter.notifyTypeChanged(UtilitySectionAdapter.TYPE_CLEAN_RAM);
                    }
                });
                handler.postDelayed(this, interval * 60_000L);
            }
        }
    };

    private void triggerSmartRamCronOnResume() {
        handler.removeCallbacks(smartRamPeriodicRunnable);
        handler.postDelayed(() -> {
            com.next.tv.launcher.clean.SmartRamConfigManager config = com.next.tv.launcher.clean.SmartRamConfigManager.getInstance(this);
            int interval = config.getCronIntervalMinutes();
            if (interval > 0) {
                com.next.tv.launcher.clean.SmartRamGovernor.cleanRamSmartAsync(this, false, false, res -> {
                    if (utilityAdapter != null) {
                        utilityAdapter.notifyTypeChanged(UtilitySectionAdapter.TYPE_CLEAN_RAM);
                    }
                });
                if (interval >= 5) {
                    handler.postDelayed(smartRamPeriodicRunnable, interval * 60_000L);
                }
            }
        }, 4000);
    }

    public void applyUserMode(int mode) {
        this.currentUserMode = mode;
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putInt("user_mode", mode).apply();

        // Bug 2 Fix: Apply reduced motion to presenters, ambient, and layout animations
        setReducedMotion(mode != com.next.tv.launcher.onboarding.OnboardingDialog.MODE_FULL);

        if (mode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            WidgetConfigManager.getInstance(this).disableAllWidgets();
            if (widgetRecyclerView != null) widgetRecyclerView.setVisibility(View.GONE);
            if (widgetSectionTitle != null) widgetSectionTitle.setVisibility(View.GONE);
            if (sectionConfig != null) {
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_CONTINUE_WATCHING, false);
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_LIVE_TV, false);
            }
            if (movieSectionsContainer != null) {
                movieSectionsContainer.removeAllViews();
                movieSectionsContainer.setVisibility(View.GONE);
            }
            if (recPresenter != null) {
                recPresenter.unregisterTvObserver();
            }
            handler.removeCallbacks(smartRamPeriodicRunnable);
            if (ambient != null) ambient.setVisibility(View.GONE);
            if (sky != null) sky.setIntensity(0);
            // Bug 6 Fix: stop RemoteLockServer socket in Super Light mode
            if (remoteLockServer != null) {
                remoteLockServer.stop();
                remoteLockServer = null;
            }
            if (utilityAdapter != null) {
                utilityAdapter.setMode(mode);
            }
        } else if (mode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_BALANCED) {
            WidgetConfigManager.getInstance(this).enableEssentialWidgetsOnly();
            if (widgetRecyclerView != null) widgetRecyclerView.setVisibility(View.VISIBLE);
            if (sectionConfig != null) {
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_CONTINUE_WATCHING, true);
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_LIVE_TV, false); // Balanced: keep Continue Watching, skip heavy EPG scan
            }
            if (movieSectionsContainer != null) {
                movieSectionsContainer.setVisibility(View.VISIBLE);
            }
            if (ambient != null) ambient.setVisibility(View.VISIBLE);
            // Bug 6 Fix: ensure RemoteLockServer is running in Balanced mode
            if (remoteLockServer == null) {
                remoteLockServer = new RemoteLockServer(this);
                remoteLockServer.start();
            }
            if (utilityAdapter != null) {
                utilityAdapter.setMode(mode);
            }
            loadRecommendations();
        } else { // MODE_FULL
            WidgetConfigManager.getInstance(this).enableAllWidgets();
            if (widgetRecyclerView != null) widgetRecyclerView.setVisibility(View.VISIBLE);
            if (sectionConfig != null) {
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_CONTINUE_WATCHING, true);
                sectionConfig.setSectionEnabled(SectionConfig.SECTION_LIVE_TV, true);
            }
            if (movieSectionsContainer != null) {
                movieSectionsContainer.setVisibility(View.VISIBLE);
            }
            if (ambient != null) ambient.setVisibility(View.VISIBLE);
            if (recPresenter != null) {
                recPresenter.registerTvObserver();
            }
            // Bug 6 Fix: ensure RemoteLockServer is running in Full mode
            if (remoteLockServer == null) {
                remoteLockServer = new RemoteLockServer(this);
                remoteLockServer.start();
            }
            if (utilityAdapter != null) {
                utilityAdapter.setMode(mode);
            }
            loadRecommendations();
        }
    }

    public AppAdapter getAdapter() {
        return adapter;
    }

    public List<String> getDockApps() {
        return dockApps;
    }

    public void previewWallpaper(String assetPath) {
        if (wallpaperPlayer == null) return;
        if (assetPath == null || assetPath.isEmpty()) {
            wallpaperPlayer.getImageView().setImageDrawable(new ColorDrawable(0xFF09090B));
        } else {
            FastPosterLoader.getInstance().load(Uri.parse("file:///android_asset/" + assetPath), wallpaperPlayer.getImageView(), 0);
        }
    }

    public void saveWallpaperChoice(String assetPath) {
        File customFile = new File(getFilesDir(), "wallpaper_custom.jpg");
        if (assetPath == null || assetPath.isEmpty()) {
            if (customFile.exists()) customFile.delete();
            if (wallpaperPlayer != null) wallpaperPlayer.reload();
            return;
        }
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(customFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            if (wallpaperPlayer != null) {
                wallpaperPlayer.loadCustomWallpaperFile(customFile);
            }
        } catch (Exception ignored) {}
    }

    public void setHomeContentVisible(boolean visible) {
        float alpha = visible ? 1.0f : 0.0f;
        if (mainScroll != null) {
            mainScroll.animate().alpha(alpha).setDuration(180).start();
        }
        if (clockBlock != null) {
            clockBlock.animate().alpha(alpha).setDuration(180).start();
        }
        View topButtons = findViewById(R.id.topButtons);
        if (topButtons != null) {
            topButtons.animate().alpha(alpha).setDuration(180).start();
        }
        if (glass != null) {
            glass.animate().alpha(alpha).setDuration(180).start();
        }
    }

    public void blockMainContentFocus() {
        if (mainScroll != null) {
            mainScroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        View topButtons = findViewById(R.id.topButtons);
        if (topButtons instanceof ViewGroup) {
            ((ViewGroup) topButtons).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    public void unblockMainContentFocus() {
        if (mainScroll != null) {
            mainScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        View topButtons = findViewById(R.id.topButtons);
        if (topButtons instanceof ViewGroup) {
            ((ViewGroup) topButtons).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    public void showRemoteControlDrawer() {
        showRemoteControlDrawer(null);
    }

    public void showRemoteControlDrawer(View triggerView) {
        if (remoteControlDrawer == null) {
            remoteControlDrawer = new RemoteControlDrawer(this, () -> {
                if (utilityAdapter != null) utilityAdapter.notifyTypeChanged(UtilitySectionAdapter.TYPE_REMOTE_CONTROL);
            });
        }
        remoteControlDrawer.show(triggerView);
    }

    public void showRemoteFileDrawer() {
        showRemoteFileDrawer(null);
    }

    public void showRemoteFileDrawer(View triggerView) {
        if (remoteFileDrawer == null) {
            remoteFileDrawer = new RemoteFileDrawer(this, () -> {
                if (utilityAdapter != null) utilityAdapter.notifyTypeChanged(UtilitySectionAdapter.TYPE_REMOTE_FILE);
            });
        }
        remoteFileDrawer.show(triggerView);
    }

    public void showWallpaperPicker() {
        showWallpaperPicker(null);
    }

    public void showWallpaperPicker(View triggerView) {
        if (wallpaperPickerDrawer == null) {
            wallpaperPickerDrawer = new WallpaperPickerDrawer(this, new WallpaperPickerDrawer.OnWallpaperPreviewListener() {
                @Override
                public void onWallpaperPreview(String url, Bitmap bitmap) {
                    if (wallpaperPlayer != null) {
                        if (url != null) {
                            FastPosterLoader.getInstance().load(Uri.parse(url), wallpaperPlayer.getImageView(), 0);
                        } else {
                            // Blank / no wallpaper
                            wallpaperPlayer.getImageView().setImageDrawable(null);
                            wallpaperPlayer.getImageView().setBackgroundColor(0xFF09090B);
                        }
                    }
                }

                @Override
                public void onWallpaperSaved(File file) {
                    if (wallpaperPlayer != null) {
                        if (file != null) {
                            wallpaperPlayer.loadCustomWallpaperFile(file);
                        } else {
                            // Cleared / Blank wallpaper
                            wallpaperPlayer.getImageView().setImageDrawable(null);
                            wallpaperPlayer.getImageView().setBackgroundColor(0xFF09090B);
                        }
                    }
                }

                @Override
                public void onWallpaperCancelled() {
                    if (wallpaperPlayer != null) {
                        wallpaperPlayer.reload();
                    }
                }
            });
        }
        wallpaperPickerDrawer.show(triggerView);
    }

    private com.next.tv.launcher.lock.HiddenAppsDrawer hiddenAppsDrawer;
    private com.next.tv.launcher.lock.KidModeControlDrawer kidModeControlDrawer;

    private void showKidModeControlDrawer(View triggerView) {
        if (kidModeControlDrawer == null) {
            kidModeControlDrawer = new com.next.tv.launcher.lock.KidModeControlDrawer(this, () -> {
                updateKidModeUiState();
                reloadApps(false);
                if (utilityAdapter != null) utilityAdapter.notifyTypeChanged(UtilitySectionAdapter.TYPE_KID_MODE);
            });
        }
        kidModeControlDrawer.show(triggerView);
    }

    private void showHiddenAppsManagerDialog() {
        showHiddenAppsManagerDialog(null);
    }

    private void showHiddenAppsManagerDialog(View triggerView) {
        if (hiddenAppsDrawer == null) {
            hiddenAppsDrawer = new com.next.tv.launcher.lock.HiddenAppsDrawer(this, () -> reloadApps(false));
        }
        hiddenAppsDrawer.show(triggerView);
    }

    private KidTimeUpDialog kidTimeUpDialog;

    private final Runnable kidTimeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (isResumed && appLockManager != null && appLockManager.isKidModeActive()) {
                checkKidModeTimeLimit();
                handler.postDelayed(this, 30_000);
            }
        }
    };

    private void checkKidModeTimeLimit() {
        if (appLockManager == null || !appLockManager.isKidModeActive()) return;
        KidTimeQuotaTracker tracker = KidTimeQuotaTracker.getInstance(this);
        long usedMinutes = (usageTracker != null && usageTracker.getTodayUsage() != null)
                ? (usageTracker.getTodayUsage().totalForegroundMillis / 60_000L) : 0L;

        if (tracker.isCurfewActiveNow()) {
            showKidTimeUpScreen(true);
            return;
        }

        if (tracker.isQuotaExpired(usedMinutes)) {
            showKidTimeUpScreen(false);
            return;
        }

        if (tracker.shouldShow5MinWarning(usedMinutes)) {
            Toast.makeText(this, "Còn 5 phút xem TV", Toast.LENGTH_LONG).show();
        }
    }

    private void showKidTimeUpScreen(boolean isCurfew) {
        if (kidTimeUpDialog != null && kidTimeUpDialog.isShowing()) return;
        kidTimeUpDialog = new KidTimeUpDialog(this, isCurfew, () -> {
            updateKidModeUiState();
            reloadApps(false);
        });
        kidTimeUpDialog.show();
    }

    private void updateMovieSections(List<TvRecommendationReader.MovieSection> sections) {
        if (movieSectionsContainer == null) return;
        if (isSectionManagementMode()) return;
        if (currentUserMode == com.next.tv.launcher.onboarding.OnboardingDialog.MODE_SUPER_LIGHT) {
            movieSectionsContainer.removeAllViews();
            movieSectionsContainer.setVisibility(View.GONE);
            return;
        }
        if (sections == null || sections.isEmpty()) {
            movieSectionsContainer.removeAllViews();
            return;
        }

        List<String> order = sectionConfig.getSectionOrder();
        if (order != null && !order.isEmpty() && sections.size() > 1) {
            final int watchNextIdx = order.indexOf(SectionConfig.SECTION_CONTINUE_WATCHING);
            Collections.sort(sections, (a, b) -> {
                int ia = order.indexOf(a.id);
                int ib = order.indexOf(b.id);
                if (ia < 0 && SectionConfig.SECTION_LIVE_TV.equals(a.id)) ia = watchNextIdx >= 0 ? watchNextIdx + 1 : 0;
                if (ib < 0 && SectionConfig.SECTION_LIVE_TV.equals(b.id)) ib = watchNextIdx >= 0 ? watchNextIdx + 1 : 0;
                if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
                if (ia >= 0) return -1;
                if (ib >= 0) return 1;
                return 0;
            });
        }

        List<TvRecommendationReader.MovieSection> validSections = new ArrayList<>();
        for (TvRecommendationReader.MovieSection sec : sections) {
            if (!sec.items.isEmpty()) validSections.add(sec);
        }
        for (TvRecommendationReader.MovieSection sec : validSections) {
            Log.e("TvHome.RecReader", "validSection: id=" + sec.id + " title=" + sec.title + " items=" + sec.items.size() + " enabled=" + sectionConfig.isSectionEnabled(sec.id));
        }

        // Record active focus before section diffing to prevent jumping to top
        final View currentFocus = getCurrentFocus();
        final boolean wasUtilityFocused = utilityRecyclerView != null && utilityRecyclerView.hasFocus();
        final int previousScrollY = mainScroll != null ? mainScroll.getScrollY() : 0;

        // In-place diffing: avoid tearing down views when channel layout matches
        boolean canReuse = (movieSectionsContainer.getChildCount() == validSections.size());
        if (canReuse) {
            for (int i = 0; i < validSections.size(); i++) {
                View child = movieSectionsContainer.getChildAt(i);
                Object tag = child.getTag();
                if (!(tag instanceof TvRecommendationReader.MovieSection)
                        || !((TvRecommendationReader.MovieSection) tag).id.equals(validSections.get(i).id)) {
                    canReuse = false;
                    break;
                }
            }
        }

        if (canReuse) {
            for (int i = 0; i < validSections.size(); i++) {
                TvRecommendationReader.MovieSection sec = validSections.get(i);
                View secView = movieSectionsContainer.getChildAt(i);
                secView.setTag(sec);
                TextView titleView = secView.findViewById(R.id.sectionTitle);
                if (titleView != null) titleView.setText(sec.title);
                HorizontalSectionRecyclerView rv = secView.findViewById(R.id.sectionRecyclerView);
            if (moviePool != null) {
                rv.setRecycledViewPool(moviePool);
            }
            rv.setItemViewCacheSize(4);
                if (rv != null && rv.getAdapter() instanceof MoviePosterAdapter) {
                    ((MoviePosterAdapter) rv.getAdapter()).setItems(sec.items);
                }
                boolean isEnabled = sectionConfig.isSectionEnabled(sec.id);
                if (!isEnabled) {
                    secView.setVisibility(View.GONE);
                    secView.setAlpha(0.35f);
                    if (titleView != null) titleView.setText(sec.title + " [ĐÃ ẨN]");
                } else {
                    secView.setVisibility(View.VISIBLE);
                    secView.setAlpha(1.0f);
                }
            }
            return;
        }

        movieSectionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < validSections.size(); i++) {
            TvRecommendationReader.MovieSection sec = validSections.get(i);

            View secView = inflater.inflate(R.layout.layout_movie_section, movieSectionsContainer, false);
            secView.setTag(sec);
            TextView titleView = secView.findViewById(R.id.sectionTitle);
            titleView.setText(sec.title);

            TextView btnMoveSection = secView.findViewById(R.id.btnMoveSection);
            TextView btnToggleSection = secView.findViewById(R.id.btnToggleSection);
            if (btnMoveSection != null) btnMoveSection.setFocusable(false);
            if (btnToggleSection != null) btnToggleSection.setFocusable(false);

            boolean isEnabled = sectionConfig.isSectionEnabled(sec.id);
            if (!isEnabled) {
                secView.setVisibility(View.GONE);
                secView.setAlpha(0.35f);
                titleView.setText(sec.title + " [ĐÃ ẨN]");
                btnToggleSection.setText("Hiện");
            } else {
                secView.setVisibility(View.VISIBLE);
                secView.setAlpha(1.0f);
                btnToggleSection.setText("Ẩn");
            }

            View.OnFocusChangeListener actionFocusListener = (v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f)
                        .setDuration(reducedMotion ? Motion.REDUCED_MS : 150).start();
                if (hasFocus) {
                    focusGlassOnCard(v);
                }
            };
            btnMoveSection.setOnFocusChangeListener(actionFocusListener);
            btnToggleSection.setOnFocusChangeListener(actionFocusListener);

            btnMoveSection.setOnClickListener(v -> {
                if (getReorderingSectionView() == secView) {
                    finishReorderingSection();
                } else {
                    if (getReorderingSectionView() != null) finishReorderingSection();
                    startReorderingSection(secView, btnMoveSection);
                }
            });

            btnToggleSection.setOnClickListener(v -> {
                boolean currentlyEnabled = sectionConfig.isSectionEnabled(sec.id);
                boolean newState = !currentlyEnabled;
                sectionConfig.setSectionEnabled(sec.id, newState);
                if (newState) {
                    secView.animate().alpha(1.0f).setDuration(150).start();
                    titleView.setText(sec.title);
                    btnToggleSection.setText("Ẩn");
                } else {
                    secView.animate().alpha(0.35f).setDuration(150).start();
                    titleView.setText(sec.title + " [ĐÃ ẨN]");
                    btnToggleSection.setText("Hiện");
                }
            });

            btnMoveSection.setOnKeyListener((v, keyCode, event) -> {
                if (getReorderingSectionView() == secView) {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        moveSectionUp(secView);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        moveSectionDown(secView);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return true;
                    }
                    return false;
                } else {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        exitSectionManagement(secView);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (btnToggleSection != null) btnToggleSection.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        int idx = movieSectionsContainer.indexOfChild(secView);
                        if (idx > 0) {
                            switchManagementSection(secView, movieSectionsContainer.getChildAt(idx - 1), false);
                        }
                        return true;
                    }
                    return false;
                }
            });

            btnToggleSection.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    exitSectionManagement(secView);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (btnMoveSection != null) btnMoveSection.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    int idx = movieSectionsContainer.indexOfChild(secView);
                    if (idx < movieSectionsContainer.getChildCount() - 1) {
                        switchManagementSection(secView, movieSectionsContainer.getChildAt(idx + 1), true);
                    }
                    return true;
                }
                return false;
            });

            HorizontalSectionRecyclerView rv = secView.findViewById(R.id.sectionRecyclerView);
            if (moviePool != null) {
                rv.setRecycledViewPool(moviePool);
            }
            rv.setItemViewCacheSize(4);
            rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) {
                @Override
                public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                             @NonNull Rect rect, boolean immediate,
                                                             boolean focusedChildVisible) {
                    int left = child.getLeft() + rect.left;
                    int right = left + rect.width();
                    int limitRight = getWidth() - getPaddingRight();
                    int dx = 0;
                    if (left < getPaddingLeft()) dx = left - getPaddingLeft();
                    else if (right > limitRight) dx = right - limitRight;
                    if (dx == 0) return false;
                    if (immediate || reducedMotion || !animations) parent.scrollBy(dx, 0);
                    else parent.smoothScrollBy(dx, 0);
                    return true;
                }
            });
            rv.setHasFixedSize(false);
            rv.setItemAnimator(null);

            rv.setOnLeftEdgeListener(focusedChild -> {
                enterSectionManagement(secView);
                return true;
            });

            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (glassFocusPresenter != null) glassFocusPresenter.onScrolled(dx, dy);
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        syncGlassWithFocusedView(false);
                    }
                }
            });

            MoviePosterAdapter posterAdapter = new MoviePosterAdapter(this, sec.isPortrait, advancedEffects, adapter.focusScale(), (item, v) -> {
                focusGlassOnCard(v);
                ensureSectionVisible(secView, false);
            });
            posterAdapter.setItems(sec.items);
            rv.setAdapter(posterAdapter);

            rv.setOnVerticalNavListener(direction -> {
                if (isSectionManagementMode()) return true;
                if (direction == View.FOCUS_UP) {
                    int curIdx = movieSectionsContainer.indexOfChild(secView);
                    View prevSec = null;
                    for (int p = curIdx - 1; p >= 0; p--) {
                        View c = movieSectionsContainer.getChildAt(p);
                        if (c.getVisibility() == View.VISIBLE) {
                            prevSec = c;
                            break;
                        }
                    }
                    if (prevSec != null) {
                        RecyclerView prevRv = prevSec.findViewById(R.id.sectionRecyclerView);
                        if (prevRv != null) {
                            focusSectionCard(prevRv, prevSec);
                            return true;
                        }
                    } else {
                        currentFocusedSection = grid;
                        grid.requestFocus();
                        focusTile(Math.min(lastFocused, adapter.appCount() - 1));
                        if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
                        return true;
                    }
                }
                if (direction == View.FOCUS_DOWN) {
                    int curIdx = movieSectionsContainer.indexOfChild(secView);
                    View nextSec = null;
                    for (int n = curIdx + 1; n < movieSectionsContainer.getChildCount(); n++) {
                        View c = movieSectionsContainer.getChildAt(n);
                        if (c.getVisibility() == View.VISIBLE) {
                            nextSec = c;
                            break;
                        }
                    }
                    if (nextSec != null) {
                        RecyclerView nextRv = nextSec.findViewById(R.id.sectionRecyclerView);
                        if (nextRv != null) {
                            focusSectionCard(nextRv, nextSec);
                            return true;
                        }
                    } else if (utilityRecyclerView != null && utilityRecyclerView.getVisibility() == View.VISIBLE) {
                        focusFirstVisibleUtility();
                        return true;
                    }
                    return true;
                }
                return false;
            });

            movieSectionsContainer.addView(secView);
        }

        // Restore focus and scroll if user was in bottom sections
        if (wasUtilityFocused) {
            if (utilityRecyclerView != null) {
                utilityRecyclerView.post(() -> {
                    focusFirstVisibleUtility();
                    if (mainScroll != null && previousScrollY > 0) {
                        mainScroll.scrollTo(0, previousScrollY);
                    }
                });
            }
        } else if (currentFocus != null && currentFocus.isShown()) {
            currentFocus.post(() -> {
                currentFocus.requestFocus();
                if (mainScroll != null && previousScrollY > 0) {
                    mainScroll.scrollTo(0, previousScrollY);
                }
            });
        }
    }

    // ---- Section Management: handled by SectionManagementPresenter ----------------------------------

    private boolean isSectionManagementMode() {
        return sectionPresenter != null && sectionPresenter.isSectionManagementMode();
    }

    private View getReorderingSectionView() {
        return sectionPresenter != null ? sectionPresenter.getReorderingSectionView() : null;
    }

    private void enterSectionManagement(View triggeredSection) {
        if (sectionPresenter != null) sectionPresenter.enterSectionManagement(triggeredSection);
    }

    private void switchManagementSection(View fromSec, View toSec, boolean focusMoveBtn) {
        if (sectionPresenter != null) sectionPresenter.switchManagementSection(fromSec, toSec, focusMoveBtn);
    }

    private void exitSectionManagement(View targetSection) {
        if (sectionPresenter != null) sectionPresenter.exitSectionManagement(targetSection);
    }

    private void startReorderingSection(View secView, TextView btnMove) {
        if (sectionPresenter != null) sectionPresenter.startReorderingSection(secView, btnMove);
    }

    private void finishReorderingSection() {
        if (sectionPresenter != null) sectionPresenter.finishReorderingSection();
    }

    private void moveSectionUp(View secView) {
        if (sectionPresenter != null) sectionPresenter.moveSectionUp(secView);
    }

    private void moveSectionDown(View secView) {
        if (sectionPresenter != null) sectionPresenter.moveSectionDown(secView);
    }

    private void saveSectionOrderFromContainer() {
        if (sectionPresenter != null) sectionPresenter.saveSectionOrderFromContainer();
    }

    public List<TvRecommendationReader.MovieSection> getMovieSections() {
        return sectionPresenter != null ? sectionPresenter.getMovieSections() : Collections.emptyList();
    }

    public void applySectionOrderFromConfig() {
        if (sectionPresenter != null) sectionPresenter.applySectionOrderFromConfig();
    }
}
