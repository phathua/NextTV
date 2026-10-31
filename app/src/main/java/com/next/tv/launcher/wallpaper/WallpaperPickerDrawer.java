package com.next.tv.launcher.wallpaper;

import com.next.tv.launcher.utility.UtilitySectionAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.util.FastPosterLoader;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;
import com.next.tv.launcher.security.NativeSecurityBridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WallpaperPickerDrawer {
    private static final String TAG = "TvHome.WallpaperPicker";
    public static final String PREF_ACTIVE_WALLPAPER = "active_wallpaper_url";
    public static final String PREF_ROTATION_INTERVAL = "rotation_interval_minutes";

    public static final class WallpaperItem {
        public final long id;
        public final String title;
        public final String author;
        public final String thumbUrl;
        public final String fullUrl;

        public WallpaperItem(long id, String title, String author, String thumbUrl, String fullUrl) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.thumbUrl = thumbUrl;
            this.fullUrl = fullUrl;
        }

        public WallpaperItem(String title, String author, String thumbUrl, String fullUrl) {
            this(fullUrl.hashCode(), title, author, thumbUrl, fullUrl);
        }
    }

    public interface OnWallpaperPreviewListener {
        void onWallpaperPreview(String url, android.graphics.Bitmap bitmap);
        void onWallpaperCancelled();
        void onWallpaperSaved(File file);
    }

    private final Activity activity;
    private final OnWallpaperPreviewListener previewListener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View drawerView;
    private View drawerContent;
    private View wallpaperDrawerRoot;
    private View wallpaperHeaderRow;
    private RecyclerView wallpaperRecyclerView;
    private WallpaperAdapter adapter;

    private TextView tabCollection;
    private TextView tabPexels;
    private TextView tabUnsplash;
    private TextView tabPixabay;
    private FrameLayout btnHeaderFullscreen;
    private TextView currentActiveTab;

    private boolean isShowing = false;
    private boolean isFullscreenPreview = false;
    private boolean isSaved = false;
    private String currentSelectedUrl = null;
    private int currentFocusedPosition = 0;
    private View triggerView;
    private Runnable pendingTabSelectRunnable = null;
    private Runnable pendingPreviewRunnable = null;

    private int activeRequestToken = 0;

    // Strict 18+ / NSFW Keywords Blacklist (Strict scenery-only filter)
    private static final List<String> NSFW_KEYWORDS = Arrays.asList(
            "woman", "women", "girl", "girls", "lady", "ladies", "model", "portrait",
            "sexy", "bikini", "lingerie", "nude", "nudity", "nsfw", "sensual", "body",
            "person", "people", "fashion", "boudoir", "erotic", "cleavage", "underwear",
            "female", "male", "man", "men", "boy", "face", "selfie", "posing", "curves"
    );

    // Scenery-focused search queries
    private static final String[] TOPIC_QUERIES = {
            "nature+mountains+scenery",
            "space+nebula+galaxy",
            "cyberpunk+city+scenery",
            "abstract+geometric+dark",
            "architecture+skyline+night",
            "dark+amoled+landscape",
            "anime+landscape+scenery"
    };
    private int topicIndex = 0;

    public WallpaperPickerDrawer(Activity activity, OnWallpaperPreviewListener listener) {
        this.activity = activity;
        this.previewListener = listener;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean isFullscreenPreview() {
        return isFullscreenPreview;
    }

    public boolean onBackPressed() {
        if (isFullscreenPreview) {
            exitFullscreenPreview();
            return true;
        }
        if (isShowing) {
            dismiss();
            return true;
        }
        return false;
    }

    public View getDrawerView() {
        return drawerView;
    }

    public void show() {
        show(null);
    }

    public void show(View trigger) {
        if (isShowing) return;
        this.triggerView = trigger != null ? trigger : activity.getCurrentFocus();

        isSaved = false;
        currentSelectedUrl = null;
        currentFocusedPosition = 0;

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).blockMainContentFocus();
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        drawerView = inflater.inflate(R.layout.drawer_wallpaper_picker, null);
        wallpaperDrawerRoot = drawerView.findViewById(R.id.wallpaperDrawerRoot);
        drawerContent = drawerView.findViewById(R.id.wallpaperDrawerContent);
        wallpaperHeaderRow = drawerView.findViewById(R.id.wallpaperHeaderRow);
        wallpaperRecyclerView = drawerView.findViewById(R.id.wallpaperRecyclerView);

        if (drawerView instanceof ViewGroup) {
            ((ViewGroup) drawerView).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }

        tabCollection = drawerView.findViewById(R.id.tabCollection);
        tabPexels = drawerView.findViewById(R.id.tabPexels);
        tabUnsplash = drawerView.findViewById(R.id.tabUnsplash);
        tabPixabay = drawerView.findViewById(R.id.tabPixabay);
        btnHeaderFullscreen = drawerView.findViewById(R.id.btnHeaderFullscreen);

        if (btnHeaderFullscreen != null) {
            btnHeaderFullscreen.setOnClickListener(v -> enterFullscreenPreview());
            btnHeaderFullscreen.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().scaleX(hasFocus ? 1.08f : 1.0f).scaleY(hasFocus ? 1.08f : 1.0f).setDuration(150).start();
            });
            btnHeaderFullscreen.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return true; // Trap focus
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (tabPixabay != null) tabPixabay.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        restoreCardFocus();
                        return true;
                    }
                }
                return false;
            });
        }

        // Custom LayoutManager to prevent focus loss during rapid horizontal scrolling
        LinearLayoutManager lm = new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public View onInterceptFocusSearch(@NonNull View focused, int direction) {
                if (direction == View.FOCUS_RIGHT) {
                    int pos = getPosition(focused);
                    if (pos < getItemCount() - 1) {
                        View next = findViewByPosition(pos + 1);
                        if (next == null) {
                            scrollToPosition(pos + 1);
                            return focused; // keep focus while scrolling
                        }
                    }
                }
                return super.onInterceptFocusSearch(focused, direction);
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
                parent.scrollBy(dx, 0);
                return true;
            }
        };

        wallpaperRecyclerView.setLayoutManager(lm);
        wallpaperRecyclerView.setItemAnimator(null);
        wallpaperRecyclerView.setHasFixedSize(true);
        adapter = new WallpaperAdapter();
        wallpaperRecyclerView.setAdapter(adapter);

        setupTabs();

        drawerView.setFocusableInTouchMode(true);
        drawerView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (isFullscreenPreview) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        exitFullscreenPreview();
                        return true;
                    }
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });

        activity.addContentView(drawerView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        isShowing = true;
        isFullscreenPreview = false;
        drawerContent.setTranslationY(400f);
        drawerContent.animate().translationY(0f).setDuration(220).start();

        loadCollectionTab();
        tabCollection.requestFocus();
    }

    public void dismiss() {
        if (!isShowing || drawerView == null) return;
        isShowing = false;
        isFullscreenPreview = false;
        activeRequestToken++;

        if (activity instanceof MainActivity) {
            MainActivity ma = (MainActivity) activity;
            ma.unblockMainContentFocus();
        }

        View target = triggerView;
        if (target != null && target.isShown()) {
            target.requestFocus();
            target.post(() -> {
                target.requestFocus();
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).focusGlassOnCard(target);
                }
            });
        } else if (activity instanceof MainActivity) {
            ((MainActivity) activity).focusUtilityType(com.next.tv.launcher.utility.UtilitySectionAdapter.TYPE_WALLPAPER);
        }

        if (!isSaved && previewListener != null) {
            previewListener.onWallpaperCancelled();
        }

        drawerContent.animate().translationY(400f).setDuration(180).withEndAction(() -> {
            if (drawerView != null && drawerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) drawerView.getParent()).removeView(drawerView);
            }
            drawerView = null;
        }).start();
    }

    public void enterFullscreenPreview() {
        if (isFullscreenPreview) return;
        isFullscreenPreview = true;

        // Requirement 2: Do NOT hide home screen sections (keep mainScroll, clockBlock, topButtons visible)
        // Only hide dark overlay scrim
        if (wallpaperDrawerRoot != null) {
            wallpaperDrawerRoot.setBackgroundColor(Color.TRANSPARENT);
        }

        // Hide header row and slide drawer into a sleek floating strip of thumbnails at bottom
        if (wallpaperHeaderRow != null) {
            wallpaperHeaderRow.setVisibility(View.GONE);
        }

        if (drawerContent != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            drawerContent.setBackgroundResource(0);
            drawerContent.animate().translationY(14 * density).alpha(0.92f).setDuration(180).start();
        }

        restoreCardFocus();
    }

    public void exitFullscreenPreview() {
        if (!isFullscreenPreview) return;
        isFullscreenPreview = false;

        // Restore dim scrim
        if (wallpaperDrawerRoot != null) {
            wallpaperDrawerRoot.setBackgroundColor(0xA0000000);
        }

        // Restore header row and drawer background
        if (wallpaperHeaderRow != null) {
            wallpaperHeaderRow.setVisibility(View.VISIBLE);
        }

        if (drawerContent != null) {
            drawerContent.setBackgroundResource(R.drawable.search_bottom_sheet_bg);
            drawerContent.animate().translationY(0f).alpha(1.0f).setDuration(180).start();
        }

        restoreCardFocus();
    }

    private void restoreCardFocus() {
        if (wallpaperRecyclerView == null) return;
        wallpaperRecyclerView.post(() -> {
            RecyclerView.ViewHolder vh = wallpaperRecyclerView.findViewHolderForAdapterPosition(currentFocusedPosition);
            if (vh != null && vh.itemView != null) {
                vh.itemView.requestFocus();
            } else {
                wallpaperRecyclerView.scrollToPosition(currentFocusedPosition);
                wallpaperRecyclerView.post(() -> {
                    RecyclerView.ViewHolder vh2 = wallpaperRecyclerView.findViewHolderForAdapterPosition(currentFocusedPosition);
                    if (vh2 != null && vh2.itemView != null) {
                        vh2.itemView.requestFocus();
                    } else if (wallpaperRecyclerView.getChildCount() > 0) {
                        wallpaperRecyclerView.getChildAt(0).requestFocus();
                    }
                });
            }
        });
    }

    private void setupTabs() {
        setupTab(tabCollection, this::loadCollectionTab);
        setupTab(tabPexels, this::loadPexelsTab);
        setupTab(tabUnsplash, this::loadUnsplashTab);
        setupTab(tabPixabay, this::loadPixabayTab);
    }

    private void setupTab(TextView tab, Runnable onSelect) {
        if (tab == null) return;
        tab.setOnClickListener(v -> onSelect.run());

        tab.setOnFocusChangeListener((v, hasFocus) -> {
            stylePill(tab, tab.isSelected());
            if (hasFocus) {
                // Auto-select tab on focus with 120ms debounce
                if (pendingTabSelectRunnable != null) {
                    handler.removeCallbacks(pendingTabSelectRunnable);
                }
                pendingTabSelectRunnable = () -> {
                    if (tab.isFocused()) {
                        onSelect.run();
                    }
                };
                handler.postDelayed(pendingTabSelectRunnable, 120);
            }
        });

        tab.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_HOME) {
                    dismiss();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return true; // Trap focus at top
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && tab == tabCollection) {
                    return true; // Trap focus on first tab
                }
                // Allow tabPixabay to move right to btnHeaderFullscreen seamlessly
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    restoreCardFocus();
                    return true;
                }
            }
            return false;
        });
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

    private void highlightTab(TextView activeTab) {
        currentActiveTab = activeTab;
        stylePill(tabCollection, activeTab == tabCollection);
        stylePill(tabPexels, activeTab == tabPexels);
        stylePill(tabUnsplash, activeTab == tabUnsplash);
        stylePill(tabPixabay, activeTab == tabPixabay);
    }

    private String getNextTopicQuery() {
        String q = TOPIC_QUERIES[topicIndex % TOPIC_QUERIES.length];
        topicIndex++;
        return q;
    }

    private static String cleanText(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) return "";
        String val = obj.optString(key, "").trim();
        return ("null".equalsIgnoreCase(val) || val.isEmpty()) ? "" : val;
    }

    /**
     * Strict 18+ / NSFW Text filter. Rejects portraits and sensual terms.
     */
    private static boolean isSafeWallpaperText(String title, String tags) {
        String combined = ((title != null ? title : "") + " " + (tags != null ? tags : "")).toLowerCase();
        for (String bad : NSFW_KEYWORDS) {
            if (combined.contains(bad)) {
                return false;
            }
        }
        return true;
    }

    private void loadCollectionTab() {
        final int token = ++activeRequestToken;
        highlightTab(tabCollection);
        if (adapter != null) adapter.clearItems();

        List<WallpaperItem> list = new ArrayList<>();

        // Slot 0: Trống (reset về màu tối nền sạch thuần khiết)
        list.add(new WallpaperItem("Trống", "Màu tối mặc định", "none", "none"));

        list.add(new WallpaperItem("Bắc cực quang", "Thiên nhiên",
                "assets://wallpapers/01-aurora.webp",
                "assets://wallpapers/01-aurora.webp"));
        list.add(new WallpaperItem("Đỉnh hoàng hôn", "Phong cảnh",
                "assets://wallpapers/02-dusk-peaks.webp",
                "assets://wallpapers/02-dusk-peaks.webp"));
        list.add(new WallpaperItem("Hồ ánh trăng", "Tĩnh lặng",
                "assets://wallpapers/03-moon-lake.webp",
                "assets://wallpapers/03-moon-lake.webp"));
        list.add(new WallpaperItem("Đồi chè xanh", "Việt Nam",
                "assets://wallpapers/04-tea-hills.webp",
                "assets://wallpapers/04-tea-hills.webp"));
        list.add(new WallpaperItem("Rừng thu vàng", "Mùa thu",
                "assets://wallpapers/05-ruska.webp",
                "assets://wallpapers/05-ruska.webp"));
        list.add(new WallpaperItem("Khinh khí cầu", "Du lịch",
                "assets://wallpapers/06-balloon-sunrise.webp",
                "assets://wallpapers/06-balloon-sunrise.webp"));

        if (token == activeRequestToken && isShowing) {
            adapter.setItems(list);
        }
    }

    private void loadPexelsTab() {
        final int token = ++activeRequestToken;
        highlightTab(tabPexels);
        if (adapter != null) adapter.clearItems();

        final String query = getNextTopicQuery();
        new Thread(() -> {
            String apiKey = NativeSecurityBridge.getPexelsKeySafe(activity);
            List<WallpaperItem> items = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.pexels.com/v1/search?query=" + query + "&per_page=16&orientation=landscape");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", apiKey);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    String json = readStream(conn.getInputStream());
                    JSONObject obj = new JSONObject(json);
                    JSONArray photos = obj.optJSONArray("photos");
                    if (photos != null) {
                        for (int i = 0; i < photos.length(); i++) {
                            JSONObject p = photos.getJSONObject(i);
                            String photographer = cleanText(p, "photographer");
                            if (photographer.isEmpty()) photographer = "Pexels";
                            String alt = cleanText(p, "alt");

                            // Strict NSFW filter
                            if (!isSafeWallpaperText(alt, photographer)) continue;

                            JSONObject src = p.optJSONObject("src");
                            if (src != null) {
                                String thumb = cleanText(src, "medium");
                                String full = cleanText(src, "landscape");
                                if (full.isEmpty()) full = cleanText(src, "large");
                                if (full.isEmpty()) full = cleanText(src, "large2x");
                                items.add(new WallpaperItem(p.optLong("id", full.hashCode()), alt, photographer, thumb, full));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Pexels fetch failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }

            handler.post(() -> {
                if (token != activeRequestToken || !isShowing) return;
                if (!items.isEmpty()) {
                    adapter.setItems(items);
                } else {
                    Toast.makeText(activity, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void loadUnsplashTab() {
        final int token = ++activeRequestToken;
        highlightTab(tabUnsplash);
        if (adapter != null) adapter.clearItems();

        final String query = getNextTopicQuery();
        new Thread(() -> {
            String apiKey = NativeSecurityBridge.getUnsplashKeySafe(activity);
            List<WallpaperItem> items = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                int screenW = dm.widthPixels > 0 ? dm.widthPixels : 1920;
                int screenH = dm.heightPixels > 0 ? dm.heightPixels : 1080;
                // Strict SafeSearch: &content_filter=high
                URL url = new URL("https://api.unsplash.com/search/photos?query=" + query + "&per_page=16&orientation=landscape&content_filter=high&w=" + screenW + "&h=" + screenH + "&fit=crop");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Client-ID " + apiKey);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    String json = readStream(conn.getInputStream());
                    JSONObject obj = new JSONObject(json);
                    JSONArray results = obj.optJSONArray("results");
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject r = results.getJSONObject(i);
                            JSONObject user = r.optJSONObject("user");
                            String author = user != null ? cleanText(user, "name") : "Unsplash";
                            if (author.isEmpty()) author = "Unsplash";

                            String desc = cleanText(r, "description");
                            if (desc.isEmpty()) desc = cleanText(r, "alt_description");
                            if (desc.length() > 32) desc = desc.substring(0, 32);

                            // Strict NSFW filter
                            if (!isSafeWallpaperText(desc, author)) continue;

                            JSONObject urls = r.optJSONObject("urls");
                            if (urls != null) {
                                String thumb = cleanText(urls, "small");
                                String full = cleanText(urls, "regular");
                                items.add(new WallpaperItem(r.optString("id", full).hashCode(), desc, author, thumb, full));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Unsplash fetch failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }

            handler.post(() -> {
                if (token != activeRequestToken || !isShowing) return;
                if (!items.isEmpty()) {
                    adapter.setItems(items);
                } else {
                    Toast.makeText(activity, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void loadPixabayTab() {
        final int token = ++activeRequestToken;
        highlightTab(tabPixabay);
        if (adapter != null) adapter.clearItems();

        final String query = getNextTopicQuery();
        new Thread(() -> {
            String apiKey = NativeSecurityBridge.getPixabayKeySafe(activity);
            List<WallpaperItem> items = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                // Strict SafeSearch: &safesearch=true&category=backgrounds,nature,science,places,buildings
                URL url = new URL("https://pixabay.com/api/?key=" + apiKey + "&q=" + query + "&image_type=photo&orientation=horizontal&safesearch=true&category=backgrounds,nature,places,buildings&per_page=16");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    String json = readStream(conn.getInputStream());
                    JSONObject obj = new JSONObject(json);
                    JSONArray hits = obj.optJSONArray("hits");
                    if (hits != null) {
                        for (int i = 0; i < hits.length(); i++) {
                            JSONObject h = hits.getJSONObject(i);
                            String author = cleanText(h, "user");
                            if (author.isEmpty()) author = "Pixabay";

                            String tags = cleanText(h, "tags");
                            if (tags.contains(",")) tags = tags.split(",")[0].trim();

                            // Strict NSFW filter
                            if (!isSafeWallpaperText(tags, author)) continue;

                            String thumb = cleanText(h, "webformatURL");
                            String full = cleanText(h, "webformatURL");
                            if (full.isEmpty()) full = cleanText(h, "largeImageURL");
                            items.add(new WallpaperItem(h.optLong("id", full.hashCode()), tags, author, thumb, full));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Pixabay fetch failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }

            handler.post(() -> {
                if (token != activeRequestToken || !isShowing) return;
                if (!items.isEmpty()) {
                    adapter.setItems(items);
                } else {
                    Toast.makeText(activity, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void saveCurrentWallpaper() {
        if (currentSelectedUrl == null || currentSelectedUrl.isEmpty()) {
            Toast.makeText(activity, "Chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sp = activity.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE);

        if ("none".equals(currentSelectedUrl)) {
            File custom = new File(activity.getFilesDir(), "wallpaper_custom.jpg");
            if (custom.exists()) custom.delete();
            sp.edit().putString(PREF_ACTIVE_WALLPAPER, "none").apply();
            isSaved = true;
            if (previewListener != null) {
                previewListener.onWallpaperSaved(null);
            }
            Toast.makeText(activity, "Đã xóa hình nền", Toast.LENGTH_SHORT).show();
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }

        Toast.makeText(activity, "Đang lưu hình nền...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            InputStream in = null;
            HttpURLConnection conn = null;
            try {
                if (currentSelectedUrl.startsWith("assets://")) {
                    String assetPath = currentSelectedUrl.substring("assets://".length());
                    in = activity.getAssets().open(assetPath);
                } else if (currentSelectedUrl.startsWith("file:///android_asset/")) {
                    String assetPath = currentSelectedUrl.substring("file:///android_asset/".length());
                    in = activity.getAssets().open(assetPath);
                } else {
                    URL url = new URL(currentSelectedUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    if (conn.getResponseCode() != 200) {
                        throw new Exception("HTTP " + conn.getResponseCode());
                    }
                    in = conn.getInputStream();
                }

                byte[] data = readAllBytes(in);
                File customFile = new File(activity.getFilesDir(), "wallpaper_custom.jpg");
                try (FileOutputStream fos = new FileOutputStream(customFile)) {
                    fos.write(data);
                    fos.flush();
                }

                sp.edit().putString(PREF_ACTIVE_WALLPAPER, currentSelectedUrl).apply();
                isSaved = true;

                handler.post(() -> {
                    Toast.makeText(activity, "Đã đổi hình nền", Toast.LENGTH_SHORT).show();
                    if (previewListener != null) {
                        previewListener.onWallpaperSaved(customFile);
                    }
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
                return;
            } catch (Exception e) {
                Log.e(TAG, "Save wallpaper error: " + e.getMessage());
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
            handler.post(() -> Toast.makeText(activity, "Lưu thất bại", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] b = new byte[8192];
        while ((nRead = is.read(b, 0, b.length)) != -1) {
            buffer.write(b, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    final class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.WallpaperViewHolder> {
        private final List<WallpaperItem> items = new ArrayList<>();

        WallpaperAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if (position >= 0 && position < items.size()) {
                return items.get(position).id;
            }
            return RecyclerView.NO_ID;
        }

        void clearItems() {
            int count = items.size();
            items.clear();
            if (count > 0) {
                notifyItemRangeRemoved(0, count);
            }
        }

        void setItems(List<WallpaperItem> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
            if (!items.isEmpty()) {
                currentFocusedPosition = Math.min(currentFocusedPosition, items.size() - 1);
                currentSelectedUrl = items.get(currentFocusedPosition).fullUrl;
            }
        }

        void appendItems(List<WallpaperItem> moreItems) {
            if (moreItems == null || moreItems.isEmpty()) return;
            int startPos = items.size();
            items.addAll(moreItems);
            notifyItemRangeInserted(startPos, moreItems.size());
        }

        @NonNull
        @Override
        public WallpaperViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallpaper_card, parent, false);
            return new WallpaperViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull WallpaperViewHolder holder, int position) {
            WallpaperItem item = items.get(position);

            if (item.title == null || item.title.trim().isEmpty() || "null".equalsIgnoreCase(item.title.trim())) {
                holder.title.setVisibility(View.GONE);
            } else {
                holder.title.setVisibility(View.VISIBLE);
                holder.title.setText(item.title);
            }

            if (item.author == null || item.author.trim().isEmpty() || "null".equalsIgnoreCase(item.author.trim())) {
                holder.author.setVisibility(View.GONE);
            } else {
                holder.author.setVisibility(View.VISIBLE);
                holder.author.setText(item.author);
            }

            // Sync focus border state immediately on bind
            boolean isFocused = holder.itemView.hasFocus();
            if (holder.focusStroke != null) {
                holder.focusStroke.setVisibility(isFocused ? View.VISIBLE : View.GONE);
            }
            holder.itemView.setScaleX(isFocused ? 1.05f : 1.0f);
            holder.itemView.setScaleY(isFocused ? 1.05f : 1.0f);

            SharedPreferences sp = activity.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE);
            String activeUrl = sp.getString(PREF_ACTIVE_WALLPAPER, "none");

            boolean isActive = false;
            if ("none".equals(item.fullUrl)) {
                File custom = new File(activity.getFilesDir(), "wallpaper_custom.jpg");
                isActive = "none".equals(activeUrl) || !custom.exists();
            } else if (item.fullUrl != null && item.fullUrl.equals(activeUrl)) {
                isActive = true;
            }

            if (holder.imgActiveCheck != null) {
                holder.imgActiveCheck.setVisibility(isActive ? View.VISIBLE : View.GONE);
            }

            if ("none".equals(item.thumbUrl)) {
                holder.thumb.setImageDrawable(null);
                holder.thumb.setBackgroundColor(0xFF09090B);
            } else if (item.thumbUrl != null && !item.thumbUrl.isEmpty()) {
                holder.thumb.setBackground(null);
                FastPosterLoader.getInstance().load(Uri.parse(item.thumbUrl), holder.thumb, 0);
            }

            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    currentFocusedPosition = holder.getBindingAdapterPosition();
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(160).start();
                    if (holder.focusStroke != null) {
                        holder.focusStroke.setVisibility(View.VISIBLE);
                    }
                    currentSelectedUrl = item.fullUrl;

                    // Debounce preview update to avoid in-flight race condition on fast scrolling
                    if (pendingPreviewRunnable != null) {
                        handler.removeCallbacks(pendingPreviewRunnable);
                    }
                    pendingPreviewRunnable = () -> {
                        if (previewListener != null) {
                            if ("none".equals(item.fullUrl)) {
                                previewListener.onWallpaperPreview(null, null);
                            } else {
                                previewListener.onWallpaperPreview(item.fullUrl, null);
                            }
                        }
                    };
                    handler.postDelayed(pendingPreviewRunnable, 80);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
                    if (holder.focusStroke != null) {
                        holder.focusStroke.setVisibility(View.GONE);
                    }
                }
            });

            holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_HOME) {
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        if (isFullscreenPreview) {
                            exitFullscreenPreview();
                            return true;
                        }
                        dismiss();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (isFullscreenPreview) {
                            exitFullscreenPreview();
                            return true;
                        }
                        if (currentActiveTab != null) {
                            currentActiveTab.requestFocus();
                        } else if (tabCollection != null) {
                            tabCollection.requestFocus();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return true; // Trap focus at bottom
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == 0) {
                        return true; // Trap focus on first card
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == items.size() - 1) {
                        return true; // Trap focus on last card
                    }
                }
                return false;
            });

            // Short press: apply wallpaper directly
            holder.itemView.setOnClickListener(v -> saveCurrentWallpaper());

            // Long press: enter fullscreen preview
            holder.itemView.setOnLongClickListener(v -> {
                enterFullscreenPreview();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class WallpaperViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView author;
            final View focusStroke;
            final ImageView imgActiveCheck;

            WallpaperViewHolder(View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.wallpaperThumb);
                title = itemView.findViewById(R.id.wallpaperTitle);
                author = itemView.findViewById(R.id.wallpaperAuthor);
                focusStroke = itemView.findViewById(R.id.cardFocusStroke);
                imgActiveCheck = itemView.findViewById(R.id.imgActiveCheck);
            }
        }
    }
}
