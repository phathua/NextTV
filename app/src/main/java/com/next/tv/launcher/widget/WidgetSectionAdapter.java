package com.next.tv.launcher.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.FastPosterLoader;
import com.next.tv.launcher.MainActivity;
import com.next.tv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Unified adapter rendering real-time Apple tvOS styled widgets.
 */
public final class WidgetSectionAdapter extends RecyclerView.Adapter<WidgetSectionAdapter.WidgetViewHolder> {
    public static final int TYPE_WEATHER = 0;
    public static final int TYPE_NEWS = 1;
    public static final int TYPE_USAGE = 2;
    public static final int TYPE_MEDIA = 3;
    public static final int TYPE_YOUTUBE = 4;
    public static final int TYPE_FOOTBALL = 5;
    public static final int TYPE_GOLD = 6;
    public static final int TYPE_CRYPTO = 7;
    public static final int TYPE_VIETNAM_CALENDAR = 8;

    public static final String PAYLOAD_DATA_UPDATE = "payload_data_update";

    public interface OnWidgetFocusListener {
        void onWidgetFocused(View cardView);
    }

    public interface OnWidgetClickListener {
        void onWidgetClicked(int type, Object data);
    }

    public interface OnWidgetKeyListener {
        boolean onWidgetKey(View card, int keyCode, KeyEvent event);
    }

    public interface OnWidgetListChangedListener {
        void onWidgetListChanged(int activeCount);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final float density;
    private final boolean advancedEffects;
    private final float focusScale;
    private final OnWidgetFocusListener focusListener;
    private final OnWidgetClickListener clickListener;
    private final OnWidgetKeyListener keyListener;
    private final WidgetConfigManager configManager;
    private RecyclerView attachedRecyclerView;
    private PopupWindow currentContextMenu;

    private boolean isMoveMode = false;
    private int currentMovePos = -1;
    private org.json.JSONObject goldData;
    private org.json.JSONObject cryptoData;
    private org.json.JSONObject calendarData;
    private final java.util.Map<Integer, org.json.JSONObject> customAddonsData = new java.util.concurrent.ConcurrentHashMap<>();
    private int originalMovePos = -1;
    private List<Integer> originalOrderBackup = null;
    private android.animation.ObjectAnimator pulseAnimator = null;
    private View movingCardView = null;
    private OnWidgetListChangedListener listChangedListener;

    private WeatherDataManager.WeatherInfo weatherInfo;
    private List<NewsDataManager.NewsItem> newsList = new ArrayList<>();
    private List<YouTubeTrendingManager.YouTubeVideoItem> youtubeList = new ArrayList<>();
    private List<FootballDataManager.FootballMatchItem> footballList = new ArrayList<>();
    private UsageStatsTracker.DailyUsageInfo usageInfo;
    private MediaSessionTracker.MediaPlaybackInfo playbackInfo;

    private int currentNewsIndex = 0;
    private int currentYouTubeIndex = 0;
    private int currentFootballIndex = 0;
    private List<Integer> activeTypes = new ArrayList<>();

    public WidgetSectionAdapter(Context context, boolean advancedEffects, float focusScale,
                                OnWidgetFocusListener focusListener, OnWidgetClickListener clickListener,
                                OnWidgetKeyListener keyListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.advancedEffects = advancedEffects;
        this.focusScale = focusScale > 1.0f ? focusScale : 1.05f;
        this.focusListener = focusListener;
        this.clickListener = clickListener;
        this.keyListener = keyListener;
        this.configManager = WidgetConfigManager.getInstance(context);
        rebuildActiveTypes();
    }

    public void setOnWidgetListChangedListener(OnWidgetListChangedListener listener) {
        this.listChangedListener = listener;
        if (listener != null) listener.onWidgetListChanged(activeTypes.size());
    }

    public void rebuildActiveTypes() {
        boolean mediaPlaying = (playbackInfo != null && playbackInfo.isPlaying);
        this.activeTypes = configManager.getActiveWidgetTypes(mediaPlaying);
        android.util.Log.i("TvHome.Widget", "rebuildActiveTypes: count=" + activeTypes.size() + ", types=" + activeTypes);
        notifyDataSetChanged();
        if (listChangedListener != null) {
            listChangedListener.onWidgetListChanged(activeTypes.size());
        }
    }

    public void setWeatherData(WeatherDataManager.WeatherInfo info) {
        this.weatherInfo = info;
        int idx = activeTypes.indexOf(TYPE_WEATHER);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
    }

    public void setNewsData(List<NewsDataManager.NewsItem> news) {
        this.newsList = (news != null) ? news : new ArrayList<>();
        int idx = activeTypes.indexOf(TYPE_NEWS);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
    }

    public void setYouTubeData(List<YouTubeTrendingManager.YouTubeVideoItem> videos) {
        this.youtubeList = (videos != null) ? videos : new ArrayList<>();
        int idx = activeTypes.indexOf(TYPE_YOUTUBE);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
    }

    public void setUsageData(UsageStatsTracker.DailyUsageInfo info) {
        this.usageInfo = info;
        int idx = activeTypes.indexOf(TYPE_USAGE);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
    }

    public void setMediaData(MediaSessionTracker.MediaPlaybackInfo info) {
        boolean wasPlaying = (playbackInfo != null && playbackInfo.isPlaying);
        this.playbackInfo = info;
        boolean nowPlaying = (playbackInfo != null && playbackInfo.isPlaying);
        if (wasPlaying != nowPlaying) {
            rebuildActiveTypes();
        } else if (nowPlaying) {
            int idx = activeTypes.indexOf(TYPE_MEDIA);
            if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
        }
    }

    public void setFootballData(List<FootballDataManager.FootballMatchItem> matches) {
        this.footballList = (matches != null) ? matches : new ArrayList<>();
        this.currentFootballIndex = 0;
        int idx = activeTypes.indexOf(TYPE_FOOTBALL);
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
    }

    public void setCustomAddonData(int type, org.json.JSONObject data) {
        if (data != null) {
            customAddonsData.put(type, data);
            int idx = activeTypes.indexOf(type);
            if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
        }
    }

    public void setCustomAddonData(String addonId, org.json.JSONObject data) {
        if (addonId != null && data != null) {
            int dynType = WidgetAddonManager.getWidgetTypeForId(addonId);
            setCustomAddonData(dynType, data);
        }
    }

    /**
     * Applies normalized NWP-1 Batch Feed JSON directly from WidgetProtocolEngine.
     */
    public void applyBatchProtocolJson(org.json.JSONObject widgetsObj) {
        if (widgetsObj == null) return;

        // 1. Weather (hero_metric)
        org.json.JSONObject wObj = widgetsObj.optJSONObject("weather");
        if (wObj != null) {
            List<WeatherDataManager.DayForecast> forecasts = new ArrayList<>();
            org.json.JSONArray subArr = wObj.optJSONArray("subItems");
            if (subArr != null) {
                for (int i = 0; i < subArr.length(); i++) {
                    org.json.JSONObject d = subArr.optJSONObject(i);
                    if (d != null) {
                        forecasts.add(new WeatherDataManager.DayForecast(
                                d.optString("label", ""),
                                d.optInt("iconCode", 1),
                                d.optInt("minTemp", 24),
                                d.optInt("maxTemp", 30)
                        ));
                    }
                }
            }
            setWeatherData(new WeatherDataManager.WeatherInfo(
                    wObj.optString("headerLeft", "Việt Nam"),
                    wObj.optDouble("rawTemp", 28.0),
                    wObj.optInt("humidity", 70),
                    wObj.optString("headerRight", "Nhiều mây"),
                    wObj.optInt("iconCode", 1),
                    forecasts
            ));
        }

        // 2. Sports (scoreboard)
        org.json.JSONObject sObj = widgetsObj.optJSONObject("sports");
        if (sObj != null) {
            org.json.JSONArray items = sObj.optJSONArray("items");
            if (items != null) {
                List<FootballDataManager.FootballMatchItem> matches = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject m = items.optJSONObject(i);
                    if (m != null) {
                        matches.add(new FootballDataManager.FootballMatchItem(
                                m.optString("id", ""),
                                m.optString("sportType", "soccer"),
                                m.optString("league", "Thể thao"),
                                m.optString("homeName", "Đội nhà"),
                                m.optString("homeShort", "HOME"),
                                m.optString("homeLogo", ""),
                                m.optString("homeScore", "0"),
                                m.optString("awayName", "Đội khách"),
                                m.optString("awayShort", "AWAY"),
                                m.optString("awayLogo", ""),
                                m.optString("awayScore", "0"),
                                m.optString("statusState", "pre"),
                                m.optString("statusText", "VS"),
                                m.optBoolean("isLive", false)
                        ));
                    }
                }
                setFootballData(matches);
            }
        }

        // 3. News (news_carousel)
        org.json.JSONObject nObj = widgetsObj.optJSONObject("news");
        if (nObj != null) {
            org.json.JSONArray items = nObj.optJSONArray("items");
            if (items != null) {
                List<NewsDataManager.NewsItem> news = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject n = items.optJSONObject(i);
                    if (n != null) {
                        news.add(new NewsDataManager.NewsItem(
                                n.optString("title", ""),
                                n.optString("link", ""),
                                n.optString("imageUrl", ""),
                                n.optString("pubDate", ""),
                                n.optString("source", "Tin mới"),
                                n.optString("category", "TIN NÓNG")
                        ));
                    }
                }
                setNewsData(news);
            }
        }

        // 4. YouTube (media_banner)
        org.json.JSONObject yObj = widgetsObj.optJSONObject("youtube");
        if (yObj != null) {
            org.json.JSONArray items = yObj.optJSONArray("items");
            if (items != null) {
                List<YouTubeTrendingManager.YouTubeVideoItem> videos = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject v = items.optJSONObject(i);
                    if (v != null) {
                        videos.add(new YouTubeTrendingManager.YouTubeVideoItem(
                                v.optString("id", ""),
                                v.optString("title", ""),
                                v.optString("channelTitle", ""),
                                v.optString("durationText", ""),
                                v.optString("viewCountText", ""),
                                v.optString("thumbnailUrl", ""),
                                v.optString("categoryLabel", "Thịnh hành")
                        ));
                    }
                }
                setYouTubeData(videos);
            }
        }

        // 5. Gold (hero_metric)
        org.json.JSONObject gObj = widgetsObj.optJSONObject("gold");
        if (gObj != null) {
            this.goldData = gObj;
            int idx = activeTypes.indexOf(TYPE_GOLD);
            if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
        }

        // 6. Crypto (hero_metric)
        org.json.JSONObject cObj = widgetsObj.optJSONObject("crypto");
        if (cObj != null) {
            this.cryptoData = cObj;
            int idx = activeTypes.indexOf(TYPE_CRYPTO);
            if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
        }

        // 7. Vietnam Calendar (hero_metric)
        org.json.JSONObject calObj = widgetsObj.optJSONObject("vietnam_calendar");
        if (calObj != null) {
            this.calendarData = calObj;
            int idx = activeTypes.indexOf(TYPE_VIETNAM_CALENDAR);
            if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
        }

        // 8. Custom dynamic external addons
        java.util.Iterator<String> keys = widgetsObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("weather".equals(key) || "sports".equals(key) || "news".equals(key)
                    || "youtube".equals(key) || "gold".equals(key) || "crypto".equals(key)
                    || "vietnam_calendar".equals(key)) {
                continue;
            }
            org.json.JSONObject customObj = widgetsObj.optJSONObject(key);
            if (customObj != null) {
                int dynType = WidgetAddonManager.getWidgetTypeForId(key);
                customAddonsData.put(dynType, customObj);
                int idx = activeTypes.indexOf(dynType);
                if (idx >= 0) notifyItemChanged(idx, PAYLOAD_DATA_UPDATE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return activeTypes.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= 0 && position < activeTypes.size()) {
            return activeTypes.get(position);
        }
        return TYPE_WEATHER;
    }

    @NonNull
    @Override
    public WidgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_widget_card, parent, false);
        return new WidgetViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull WidgetViewHolder holder, int position) {
        holder.bind(getItemViewType(position), false);
        if (isMoveMode) {
            holder.itemView.setAlpha(position == currentMovePos ? 1.0f : 0.35f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull WidgetViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_DATA_UPDATE)) {
            // Partial Bind: In-place text and image updates, zero flicker, preserves focus glass
            holder.bind(getItemViewType(position), true);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull WidgetViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.stopCarousels();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (this.attachedRecyclerView == recyclerView) {
            this.attachedRecyclerView = null;
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull WidgetViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (isMoveMode) {
            int pos = holder.getAdapterPosition();
            holder.itemView.setAlpha(pos == currentMovePos ? 1.0f : 0.35f);
            holder.stopCarousels();
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.startCarousels();
        }
    }

    public boolean dismissContextMenu() {
        if (currentContextMenu != null && currentContextMenu.isShowing()) {
            currentContextMenu.dismiss();
            currentContextMenu = null;
            return true;
        }
        return false;
    }

    public boolean isMoveMode() {
        return isMoveMode;
    }

    private void stopAllCarousels() {
        if (attachedRecyclerView == null) return;
        for (int i = 0; i < attachedRecyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = attachedRecyclerView.getChildViewHolder(attachedRecyclerView.getChildAt(i));
            if (vh instanceof WidgetViewHolder) {
                ((WidgetViewHolder) vh).stopCarousels();
            }
        }
    }

    private void resumeAllCarousels() {
        if (attachedRecyclerView == null) return;
        for (int i = 0; i < attachedRecyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = attachedRecyclerView.getChildViewHolder(attachedRecyclerView.getChildAt(i));
            if (vh instanceof WidgetViewHolder) {
                ((WidgetViewHolder) vh).startCarousels();
            }
        }
    }

    public void enterMoveMode(int pos, View card) {
        if (pos < 0 || pos >= activeTypes.size()) return;
        isMoveMode = true;
        currentMovePos = pos;
        originalMovePos = pos;
        originalOrderBackup = new ArrayList<>(activeTypes);
        movingCardView = card;

        stopAllCarousels();
        dimSurroundingWidgets(true);
        startPulsing(card);
        if (card != null) card.requestFocus();
    }

    private void dimSurroundingWidgets(boolean dim) {
        if (attachedRecyclerView == null) return;
        for (int i = 0; i < attachedRecyclerView.getChildCount(); i++) {
            View child = attachedRecyclerView.getChildAt(i);
            if (child != null) {
                int p = attachedRecyclerView.getChildAdapterPosition(child);
                if (dim) {
                    if (p != currentMovePos) {
                        child.animate().alpha(0.35f).setDuration(180).start();
                    } else {
                        child.setAlpha(1.0f);
                    }
                } else {
                    child.animate().alpha(1.0f).setDuration(180).start();
                }
            }
        }
    }

    private void startPulsing(View view) {
        stopPulsing();
        if (view == null) return;
        pulseAnimator = android.animation.ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.65f);
        pulseAnimator.setDuration(550);
        pulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulseAnimator.start();
    }

    private void stopPulsing() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (movingCardView != null) {
            movingCardView.setAlpha(1.0f);
        }
    }

    private void moveCurrentWidget(int delta) {
        if (!isMoveMode || currentMovePos < 0 || activeTypes == null) return;
        int targetPos = currentMovePos + delta;
        if (targetPos < 0 || targetPos >= activeTypes.size()) return;

        Collections.swap(activeTypes, currentMovePos, targetPos);
        notifyItemMoved(currentMovePos, targetPos);
        currentMovePos = targetPos;

        dimSurroundingWidgets(true);
        if (attachedRecyclerView != null) {
            attachedRecyclerView.scrollToPosition(currentMovePos);
            attachedRecyclerView.post(() -> {
                RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(currentMovePos);
                if (vh != null && vh.itemView != null) {
                    movingCardView = vh.itemView;
                    startPulsing(movingCardView);
                    movingCardView.requestFocus();
                }
            });
        }
    }

    private void confirmMoveMode() {
        if (!isMoveMode) return;
        configManager.saveWidgetOrder(activeTypes);
        Toast.makeText(context, "Đã lưu vị trí", Toast.LENGTH_SHORT).show();
        exitMoveMode(true);
    }

    public boolean handleMoveBack() {
        if (!isMoveMode) return false;
        if (currentMovePos != originalMovePos && originalOrderBackup != null) {
            activeTypes = new ArrayList<>(originalOrderBackup);
            notifyDataSetChanged();
            currentMovePos = originalMovePos;
            dimSurroundingWidgets(true);
            if (attachedRecyclerView != null) {
                attachedRecyclerView.scrollToPosition(currentMovePos);
                attachedRecyclerView.post(() -> {
                    RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(currentMovePos);
                    if (vh != null && vh.itemView != null) {
                        movingCardView = vh.itemView;
                        startPulsing(movingCardView);
                        movingCardView.requestFocus();
                    }
                });
            }
            Toast.makeText(context, "Đã hoàn tác vị trí", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            exitMoveMode(false);
            return true;
        }
    }

    public void exitMoveMode(boolean save) {
        if (!isMoveMode) return;
        stopPulsing();
        dimSurroundingWidgets(false);
        resumeAllCarousels();
        if (!save && originalOrderBackup != null && currentMovePos != originalMovePos) {
            activeTypes = new ArrayList<>(originalOrderBackup);
            notifyDataSetChanged();
        }
        isMoveMode = false;
        currentMovePos = -1;
        originalMovePos = -1;
        originalOrderBackup = null;
        if (movingCardView != null) {
            movingCardView.setAlpha(1.0f);
            movingCardView.requestFocus();
            movingCardView = null;
        }
    }

    public void cancelMoveMode() {
        exitMoveMode(false);
    }

    public void showWidgetContextMenu(int pos) {
        showWidgetContextMenu(pos, null);
    }

    public void showWidgetContextMenu(int pos, View anchorCard) {
        if (com.next.tv.launcher.lock.AppLockManager.getInstance(context).isKidModeActive()) {
            Toast.makeText(context, "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pos < 0 || pos >= activeTypes.size()) return;
        int type = activeTypes.get(pos);

        dismissContextMenu();

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (activeTypes.size() > 1) {
            options.add("Di chuyển");
            actions.add(() -> {
                enterMoveMode(pos, anchorCard);
            });
        }

        if (type == TYPE_FOOTBALL) {
            options.add("Chọn môn thể thao");
            actions.add(() -> {
                showSportPickerMenu(anchorCard);
            });
        }

        if (activeTypes.size() > 1) {
            options.add("Ẩn tiện ích");
            actions.add(() -> {
                int oldSize = activeTypes.size();
                int targetPos = (pos < oldSize - 1) ? pos : pos - 1;

                if (type == TYPE_WEATHER) configManager.setWeatherEnabled(false);
                else if (type == TYPE_NEWS) configManager.setNewsEnabled(false);
                else if (type == TYPE_YOUTUBE) configManager.setYouTubeEnabled(false);
                else if (type == TYPE_USAGE) configManager.setUsageEnabled(false);
                else if (type == TYPE_MEDIA) configManager.setMediaEnabled(false);
                else if (type == TYPE_FOOTBALL) configManager.setFootballEnabled(false);
                else {
                    String addonId = WidgetAddonManager.getInstance(context).getIdForWidgetType(type);
                    if (addonId != null) WidgetAddonManager.getInstance(context).toggleAddon(addonId, false);
                }

                rebuildActiveTypes();

                if (attachedRecyclerView != null && targetPos >= 0 && targetPos < activeTypes.size()) {
                    final int fTarget = targetPos;
                    attachedRecyclerView.post(() -> {
                        RecyclerView.ViewHolder vh = attachedRecyclerView.findViewHolderForAdapterPosition(fTarget);
                        if (vh != null && vh.itemView != null) {
                            vh.itemView.requestFocus();
                        } else {
                            attachedRecyclerView.scrollToPosition(fTarget);
                            attachedRecyclerView.postDelayed(() -> {
                                RecyclerView.ViewHolder vh2 = attachedRecyclerView.findViewHolderForAdapterPosition(fTarget);
                                if (vh2 != null && vh2.itemView != null) {
                                    vh2.itemView.requestFocus();
                                }
                            }, 100);
                        }
                    });
                }
            });
        }

        options.add("Cài đặt tiện ích");
        actions.add(() -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).showWidgetManagerDrawer();
            }
        });

        View panel = inflater.inflate(R.layout.popup_inputs, null);
        LinearLayout rows = panel.findViewById(R.id.rows);

        PopupWindow popup = new PopupWindow(panel, (int) (200 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        currentContextMenu = popup;
        popup.setOnDismissListener(() -> {
            if (currentContextMenu == popup) currentContextMenu = null;
        });

        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.item_input, rows, false);
            row.findViewById(R.id.icon).setVisibility(View.GONE);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            row.findViewById(R.id.status).setVisibility(View.GONE);
            TextView rowTitle = row.findViewById(R.id.title);
            rowTitle.setText(options.get(i));

            row.setOnClickListener(v -> {
                popup.dismiss();
                actions.get(index).run();
            });

            rows.addView(row);
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (200 * density), panel.getMeasuredWidth()));

        if (anchorCard != null && anchorCard.isAttachedToWindow()) {
            panel.setAlpha(0f);
            panel.setPivotY(0f);
            panel.setScaleY(0.92f);
            panel.setTranslationY(-8 * density);
            popup.showAsDropDown(anchorCard, 0, (int) (10 * density), Gravity.START);
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } else {
            popup.showAtLocation(panel, Gravity.CENTER, 0, 0);
        }

        if (rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    private void showSportPickerMenu(View anchorCard) {
        FootballDataManager fdm = FootballDataManager.getInstance(context);
        String current = fdm.getSelectedSportType();

        String[] sports = new String[]{
                "Bóng đá",
                "Bóng rổ (NBA)",
                "Quần vợt (ATP)",
                "Bóng chày (MLB)",
                "Bóng bầu dục (NFL)",
                "Đua xe F1",
                "Khúc côn cầu (NHL)"
        };
        String[] keys = new String[]{
                FootballDataManager.SPORT_SOCCER,
                FootballDataManager.SPORT_BASKETBALL,
                FootballDataManager.SPORT_TENNIS,
                FootballDataManager.SPORT_BASEBALL,
                FootballDataManager.SPORT_NFL,
                FootballDataManager.SPORT_F1,
                FootballDataManager.SPORT_NHL
        };
        int[] icons = new int[]{
                R.drawable.ic_sport_football,
                R.drawable.ic_sport_basketball,
                R.drawable.ic_sport_tennis,
                R.drawable.ic_sport_baseball,
                R.drawable.ic_sport_nfl,
                R.drawable.ic_sport_f1,
                R.drawable.ic_sport_nhl
        };

        View panel = inflater.inflate(R.layout.popup_sports, null);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int maxPopupHeight = (int) (310 * density);
        PopupWindow popup = new PopupWindow(panel, (int) (230 * density), maxPopupHeight, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        currentContextMenu = popup;
        popup.setOnDismissListener(() -> {
            if (currentContextMenu == popup) currentContextMenu = null;
        });

        for (int i = 0; i < sports.length; i++) {
            final String selectedKey = keys[i];
            View row = inflater.inflate(R.layout.item_input, rows, false);
            ImageView iconView = row.findViewById(R.id.icon);
            iconView.setImageResource(icons[i]);
            iconView.setVisibility(View.VISIBLE);

            View dot = row.findViewById(R.id.dot);
            if (dot != null) dot.setVisibility(View.GONE);

            ImageView checkIcon = row.findViewById(R.id.checkIcon);
            if (checkIcon != null) {
                checkIcon.setVisibility(selectedKey.equals(current) ? View.VISIBLE : View.GONE);
            }

            row.findViewById(R.id.status).setVisibility(View.GONE);
            TextView rowTitle = row.findViewById(R.id.title);
            rowTitle.setText(sports[i]);

            row.setOnClickListener(v -> {
                popup.dismiss();
                fdm.setSelectedSportType(selectedKey);
                // Trigger transition animation and background fetch
                if (anchorCard != null) {
                    anchorCard.animate().alpha(0.3f).scaleX(0.95f).scaleY(0.95f).setDuration(200).withEndAction(() -> {
                        anchorCard.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                    }).start();
                }
                if (clickListener != null) {
                    clickListener.onWidgetClicked(TYPE_FOOTBALL, "change_sport:" + selectedKey);
                }
            });

            rows.addView(row);
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (220 * density), panel.getMeasuredWidth()));

        if (anchorCard != null && anchorCard.isAttachedToWindow()) {
            panel.setAlpha(0f);
            panel.setPivotY(0f);
            panel.setScaleY(0.92f);
            panel.setTranslationY(-8 * density);
            popup.showAsDropDown(anchorCard, 0, (int) (10 * density), Gravity.START);
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } else {
            popup.showAtLocation(panel, Gravity.CENTER, 0, 0);
        }

        int focusedIndex = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(current)) {
                focusedIndex = i;
                break;
            }
        }
        if (rows.getChildCount() > focusedIndex) {
            final int fIdx = focusedIndex;
            rows.post(() -> {
                View target = rows.getChildAt(fIdx);
                if (target != null) {
                    target.requestFocus();
                    if (panel instanceof ScrollView) {
                        ((ScrollView) panel).smoothScrollTo(0, target.getTop());
                    }
                }
            });
        }
    }

    final class WidgetViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView bgImage;
        final View bgScrim;
        final TextView category;
        final TextView meta;

        final LinearLayout weatherHeaderContainer;
        final TextView weatherLocationText;
        final ImageView weatherConditionIcon;
        final TextView weatherConditionText;
        final TextView weatherHumidityPill;
        final LinearLayout weatherForecastContainer;
        final View forecastDay1, forecastDay2, forecastDay3;
        final TextView forecastDay1Name, forecastDay2Name, forecastDay3Name;
        final ImageView forecastDay1Icon, forecastDay2Icon, forecastDay3Icon;
        final TextView forecastDay1Temp, forecastDay2Temp, forecastDay3Temp;

        final RelativeLayout newsHeaderContainer;
        final TextView newsBadgeCategory;
        final TextView newsBadgeText;

        final RelativeLayout youtubeHeaderContainer;
        final TextView youtubeBadgeText;
        final TextView youtubeDurationBadge;

        final RelativeLayout musicHeaderContainer;
        final ImageView musicAppIcon;
        final TextView musicBadgeText;
        final LinearLayout musicEqualizerContainer;
        final View eqBar1, eqBar2, eqBar3;

        final TextView heroMetric;
        final TextView heroTitle;

        final LinearLayout usageAvatarsContainer;
        final ImageView avatarApp1, avatarApp2, avatarApp3;
        final LinearLayout usageBarContainer;
        final View barSegment1, barSegment2, barSegment3;
        final LinearLayout usageLegendContainer;
        final TextView usageLegendText;

        final ImageView mediaCenterAppIcon;
        final RelativeLayout mediaBottomContainer;
        final ImageView mediaAlbumArt;
        final ImageView mediaAppIconFallback;
        final TextView mediaTrackTitle;
        final TextView mediaTrackArtist;
        final FrameLayout mediaPlayPauseBtn;
        final ImageView mediaPlayPauseIcon;

        final TextView subText;
        final TextView extra;

        // Football / Sports Widget Views
        final RelativeLayout footballContainer;
        final LinearLayout footballScoreboardContainer;
        final View footballLiveDot;
        final TextView footballStatusBadge;
        final ImageView footballSportIcon;
        final TextView footballLeagueBadge;
        final ImageView footballHomeLogo;
        final TextView footballHomeName;
        final TextView footballScoreText;
        final ImageView footballAwayLogo;
        final TextView footballAwayName;
        final TextView footballBottomHint;

        WidgetViewHolder(View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            bgImage = itemView.findViewById(R.id.widgetBgImage);
            bgScrim = itemView.findViewById(R.id.widgetBgScrim);
            category = itemView.findViewById(R.id.widgetCategory);
            meta = itemView.findViewById(R.id.widgetMeta);

            weatherHeaderContainer = itemView.findViewById(R.id.weatherHeaderContainer);
            weatherLocationText = itemView.findViewById(R.id.weatherLocationText);
            weatherConditionIcon = itemView.findViewById(R.id.weatherConditionIcon);
            weatherConditionText = itemView.findViewById(R.id.weatherConditionText);
            weatherHumidityPill = itemView.findViewById(R.id.weatherHumidityPill);
            weatherForecastContainer = itemView.findViewById(R.id.weatherForecastContainer);
            forecastDay1 = itemView.findViewById(R.id.forecastDay1);
            forecastDay1Name = itemView.findViewById(R.id.forecastDay1Name);
            forecastDay1Icon = itemView.findViewById(R.id.forecastDay1Icon);
            forecastDay1Temp = itemView.findViewById(R.id.forecastDay1Temp);
            forecastDay2 = itemView.findViewById(R.id.forecastDay2);
            forecastDay2Name = itemView.findViewById(R.id.forecastDay2Name);
            forecastDay2Icon = itemView.findViewById(R.id.forecastDay2Icon);
            forecastDay2Temp = itemView.findViewById(R.id.forecastDay2Temp);
            forecastDay3 = itemView.findViewById(R.id.forecastDay3);
            forecastDay3Name = itemView.findViewById(R.id.forecastDay3Name);
            forecastDay3Icon = itemView.findViewById(R.id.forecastDay3Icon);
            forecastDay3Temp = itemView.findViewById(R.id.forecastDay3Temp);

            newsHeaderContainer = itemView.findViewById(R.id.newsHeaderContainer);
            newsBadgeCategory = itemView.findViewById(R.id.newsBadgeCategory);
            newsBadgeText = itemView.findViewById(R.id.newsBadgeText);

            youtubeHeaderContainer = itemView.findViewById(R.id.youtubeHeaderContainer);
            youtubeBadgeText = itemView.findViewById(R.id.youtubeBadgeText);
            youtubeDurationBadge = itemView.findViewById(R.id.youtubeDurationBadge);

            musicHeaderContainer = itemView.findViewById(R.id.musicHeaderContainer);
            musicAppIcon = itemView.findViewById(R.id.musicAppIcon);
            musicBadgeText = itemView.findViewById(R.id.musicBadgeText);
            musicEqualizerContainer = itemView.findViewById(R.id.musicEqualizerContainer);
            eqBar1 = itemView.findViewById(R.id.eqBar1);
            eqBar2 = itemView.findViewById(R.id.eqBar2);
            eqBar3 = itemView.findViewById(R.id.eqBar3);

            mediaCenterAppIcon = itemView.findViewById(R.id.mediaCenterAppIcon);
            mediaBottomContainer = itemView.findViewById(R.id.mediaBottomContainer);
            mediaAlbumArt = itemView.findViewById(R.id.mediaAlbumArt);
            mediaAppIconFallback = itemView.findViewById(R.id.mediaAppIconFallback);
            mediaTrackTitle = itemView.findViewById(R.id.mediaTrackTitle);
            mediaTrackArtist = itemView.findViewById(R.id.mediaTrackArtist);
            mediaPlayPauseBtn = itemView.findViewById(R.id.mediaPlayPauseBtn);
            mediaPlayPauseIcon = itemView.findViewById(R.id.mediaPlayPauseIcon);

            heroMetric = itemView.findViewById(R.id.widgetHeroMetric);
            heroTitle = itemView.findViewById(R.id.widgetHeroTitle);

            usageAvatarsContainer = itemView.findViewById(R.id.usageAvatarsContainer);
            avatarApp1 = itemView.findViewById(R.id.avatarApp1);
            avatarApp2 = itemView.findViewById(R.id.avatarApp2);
            avatarApp3 = itemView.findViewById(R.id.avatarApp3);

            usageBarContainer = itemView.findViewById(R.id.usageBarContainer);
            barSegment1 = itemView.findViewById(R.id.barSegment1);
            barSegment2 = itemView.findViewById(R.id.barSegment2);
            barSegment3 = itemView.findViewById(R.id.barSegment3);
            usageLegendContainer = itemView.findViewById(R.id.usageLegendContainer);
            usageLegendText = itemView.findViewById(R.id.usageLegendText);

            subText = itemView.findViewById(R.id.widgetSubText);
            extra = itemView.findViewById(R.id.widgetExtra);

            footballContainer = itemView.findViewById(R.id.footballContainer);
            footballScoreboardContainer = itemView.findViewById(R.id.footballScoreboardContainer);
            footballLiveDot = itemView.findViewById(R.id.footballLiveDot);
            footballStatusBadge = itemView.findViewById(R.id.footballStatusBadge);
            footballSportIcon = itemView.findViewById(R.id.footballSportIcon);
            footballLeagueBadge = itemView.findViewById(R.id.footballLeagueBadge);
            footballHomeLogo = itemView.findViewById(R.id.footballHomeLogo);
            footballHomeName = itemView.findViewById(R.id.footballHomeName);
            footballScoreText = itemView.findViewById(R.id.footballScoreText);
            footballAwayLogo = itemView.findViewById(R.id.footballAwayLogo);
            footballAwayName = itemView.findViewById(R.id.footballAwayName);
            footballBottomHint = itemView.findViewById(R.id.footballBottomHint);

            final float cornerRadiusPx = 20f * density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            card.setClipToOutline(true);

            ViewOutlineProvider circleOutline = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };
            if (avatarApp1 != null) {
                avatarApp1.setOutlineProvider(circleOutline);
                avatarApp1.setClipToOutline(true);
            }
            if (avatarApp2 != null) {
                avatarApp2.setOutlineProvider(circleOutline);
                avatarApp2.setClipToOutline(true);
            }
            if (avatarApp3 != null) {
                avatarApp3.setOutlineProvider(circleOutline);
                avatarApp3.setClipToOutline(true);
            }

            if (usageBarContainer != null) {
                usageBarContainer.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 3f * density);
                    }
                });
                usageBarContainer.setClipToOutline(true);
            }

            card.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    if (advancedEffects) {
                        v.animate().scaleX(focusScale).scaleY(focusScale).translationY(-4f * density).setDuration(160).start();
                    }
                    if (focusListener != null) {
                        focusListener.onWidgetFocused(v);
                    }
                } else {
                    if (advancedEffects) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(140).start();
                    }
                }
            });

            card.setOnClickListener(v -> {
                if (isMoveMode) {
                    confirmMoveMode();
                    return;
                }
                if (clickListener != null) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        int type = getItemViewType();
                        Object data = null;
                        if (type == TYPE_WEATHER) data = weatherInfo;
                        else if (type == TYPE_NEWS) data = (!newsList.isEmpty()) ? newsList.get(currentNewsIndex % newsList.size()) : null;
                        else if (type == TYPE_YOUTUBE) data = (!youtubeList.isEmpty()) ? youtubeList.get(currentYouTubeIndex % youtubeList.size()) : null;
                        else if (type == TYPE_USAGE) data = usageInfo;
                        else if (type == TYPE_MEDIA) data = playbackInfo;
                        else if (type == TYPE_FOOTBALL) data = (!footballList.isEmpty()) ? footballList.get(currentFootballIndex % footballList.size()) : null;
                        clickListener.onWidgetClicked(type, data);
                    }
                }
            });

            card.setOnLongClickListener(v -> {
                if (isMoveMode) return true;
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    showWidgetContextMenu(pos, card);
                    return true;
                }
                return false;
            });

            card.setOnKeyListener((v, keyCode, event) -> {
                if (isMoveMode) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            moveCurrentWidget(-1);
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            moveCurrentWidget(1);
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                            confirmMoveMode();
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                            handleMoveBack();
                            return true;
                        }
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            return true;
                        }
                    }
                    return true;
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == 82) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            showWidgetContextMenu(pos, card);
                            return true;
                        }
                    }
                }
                if (keyListener != null) {
                    return keyListener.onWidgetKey(v, keyCode, event);
                }
                return false;
            });
        }

        private final Runnable newsCarouselRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMoveMode || !card.isAttachedToWindow() || newsList.isEmpty()) return;
                currentNewsIndex = (currentNewsIndex + 1) % newsList.size();
                NewsDataManager.NewsItem nextItem = newsList.get(currentNewsIndex);

                heroTitle.animate()
                        .alpha(0f)
                        .translationY(-6f * density)
                        .setDuration(220)
                        .withEndAction(() -> {
                            heroTitle.setText(nextItem.title);
                            if (subText != null) {
                                subText.setText(NewsDataManager.formatTimeAgo(nextItem.pubDate));
                                subText.setVisibility(View.VISIBLE);
                            }
                            if (newsBadgeText != null) {
                                newsBadgeText.setText(nextItem.source != null ? nextItem.source : "Tin tức");
                            }
                            if (newsBadgeCategory != null) {
                                newsBadgeCategory.setText(nextItem.category != null ? nextItem.category.toUpperCase(Locale.getDefault()) : "TIN NÓNG");
                            }
                            heroTitle.setTranslationY(6f * density);
                            heroTitle.animate()
                                    .alpha(1f)
                                    .translationY(0f)
                                    .setDuration(260)
                                    .start();
                        }).start();

                if (nextItem.imageUrl != null && !nextItem.imageUrl.isEmpty()) {
                    bgImage.animate().alpha(0.2f).setDuration(200).withEndAction(() -> {
                        FastPosterLoader.getInstance().load(Uri.parse(nextItem.imageUrl), bgImage, 0);
                        bgImage.setVisibility(View.VISIBLE);
                        bgImage.animate().alpha(0.65f).setDuration(300).start();
                    }).start();
                } else {
                    bgImage.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        bgImage.setVisibility(View.GONE);
                    }).start();
                }

                int wordCount = nextItem.title != null ? nextItem.title.trim().split("\\s+").length : 10;
                long nextDelay = Math.max(5000L, Math.min(10000L, 3500L + wordCount * 220L));
                card.postDelayed(this, nextDelay);
            }
        };

        private final Runnable youtubeCarouselRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMoveMode || !card.isAttachedToWindow() || youtubeList.isEmpty()) return;
                currentYouTubeIndex = (currentYouTubeIndex + 1) % youtubeList.size();
                YouTubeTrendingManager.YouTubeVideoItem nextVideo = youtubeList.get(currentYouTubeIndex);

                if (subText != null) {
                    subText.animate().alpha(0.15f).setDuration(220).start();
                }
                heroTitle.animate()
                        .alpha(0.15f)
                        .translationY(-6f * density)
                        .setDuration(220)
                        .withEndAction(() -> {
                            heroTitle.setText(nextVideo.title);
                            if (subText != null) {
                                subText.setText(nextVideo.channelTitle + " · " + nextVideo.viewCountText);
                                subText.animate().alpha(1f).setDuration(260).start();
                            }
                            if (youtubeBadgeText != null) {
                                youtubeBadgeText.setText(nextVideo.categoryLabel != null ? nextVideo.categoryLabel : "Thịnh hành");
                            }
                            if (youtubeDurationBadge != null) {
                                youtubeDurationBadge.setText(nextVideo.durationText);
                            }
                            heroTitle.setTranslationY(6f * density);
                            heroTitle.animate()
                                    .alpha(1f)
                                    .translationY(0f)
                                    .setDuration(260)
                                    .start();
                        }).start();

                if (nextVideo.thumbnailUrl != null && !nextVideo.thumbnailUrl.isEmpty()) {
                    bgImage.animate().alpha(0.2f).setDuration(200).withEndAction(() -> {
                        FastPosterLoader.getInstance().load(Uri.parse(nextVideo.thumbnailUrl), bgImage, 0);
                        bgImage.setVisibility(View.VISIBLE);
                        bgImage.animate().alpha(0.65f).setDuration(300).start();
                    }).start();
                }
                card.postDelayed(this, 9000L); // 9-second loop
            }
        };

        private final Runnable footballCarouselRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMoveMode || !card.isAttachedToWindow() || footballList.isEmpty()) return;
                currentFootballIndex = (currentFootballIndex + 1) % footballList.size();
                FootballDataManager.FootballMatchItem nextMatch = footballList.get(currentFootballIndex);

                footballScoreboardContainer.animate()
                        .alpha(0.2f)
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(220)
                        .withEndAction(() -> {
                            updateFootballScoreboardUI(nextMatch);
                            footballScoreboardContainer.animate()
                                    .alpha(1f)
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(260)
                                    .start();
                        }).start();

                card.postDelayed(this, 6500L);
            }
        };

        private void updateFootballScoreboardUI(FootballDataManager.FootballMatchItem match) {
            if (footballHomeName != null) footballHomeName.setText(match.homeTeamShort != null && !match.homeTeamShort.isEmpty() ? match.homeTeamShort : match.homeTeamName);
            if (footballAwayName != null) footballAwayName.setText(match.awayTeamShort != null && !match.awayTeamShort.isEmpty() ? match.awayTeamShort : match.awayTeamName);

            if (footballScoreText != null) {
                if ("in".equalsIgnoreCase(match.statusState) || "post".equalsIgnoreCase(match.statusState)) {
                    footballScoreText.setText(match.homeScore + " - " + match.awayScore);
                } else {
                    footballScoreText.setText("VS");
                }
            }

            if (footballHomeLogo != null) {
                if (match.homeLogoUrl != null && !match.homeLogoUrl.isEmpty()) {
                    FastPosterLoader.getInstance().load(Uri.parse(match.homeLogoUrl), footballHomeLogo, 0);
                } else {
                    footballHomeLogo.setImageDrawable(null);
                }
            }
            if (footballAwayLogo != null) {
                if (match.awayLogoUrl != null && !match.awayLogoUrl.isEmpty()) {
                    FastPosterLoader.getInstance().load(Uri.parse(match.awayLogoUrl), footballAwayLogo, 0);
                } else {
                    footballAwayLogo.setImageDrawable(null);
                }
            }

            if (footballLiveDot != null) {
                footballLiveDot.setBackgroundResource(match.isLive ? R.drawable.badge_football_live_dot : R.drawable.badge_football_gray_dot);
            }

            if (footballStatusBadge != null) {
                if (match.isLive) {
                    footballStatusBadge.setText("Trực tiếp · " + (match.statusDetail != null && !match.statusDetail.isEmpty() ? match.statusDetail : "LIVE"));
                    footballStatusBadge.setTextColor(Color.parseColor("#FF453A"));
                } else if ("post".equalsIgnoreCase(match.statusState)) {
                    footballStatusBadge.setText("Kết thúc");
                    footballStatusBadge.setTextColor(Color.parseColor("#A1A1AA"));
                } else {
                    footballStatusBadge.setText(match.statusDetail != null && !match.statusDetail.isEmpty() ? match.statusDetail : "Sắp diễn ra");
                    footballStatusBadge.setTextColor(Color.parseColor("#E4E4E7"));
                }
            }

            if (footballLeagueBadge != null) {
                footballLeagueBadge.setText(match.leagueName != null ? match.leagueName : "Thể thao");
            }

            if (footballSportIcon != null) {
                int iconRes = R.drawable.ic_sport_football;
                if (FootballDataManager.SPORT_BASKETBALL.equals(match.sportType)) iconRes = R.drawable.ic_sport_basketball;
                else if (FootballDataManager.SPORT_TENNIS.equals(match.sportType)) iconRes = R.drawable.ic_sport_tennis;
                else if (FootballDataManager.SPORT_BASEBALL.equals(match.sportType)) iconRes = R.drawable.ic_sport_baseball;
                else if (FootballDataManager.SPORT_NFL.equals(match.sportType)) iconRes = R.drawable.ic_sport_nfl;
                else if (FootballDataManager.SPORT_F1.equals(match.sportType)) iconRes = R.drawable.ic_sport_f1;
                else if (FootballDataManager.SPORT_NHL.equals(match.sportType)) iconRes = R.drawable.ic_sport_nhl;
                footballSportIcon.setImageResource(iconRes);
            }

            if (footballBottomHint != null) {
                footballBottomHint.setText(match.homeTeamName + " vs " + match.awayTeamName);
            }
        }

        private int eqStep = 0;
        private final Runnable equalizerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!card.isAttachedToWindow() || playbackInfo == null || !playbackInfo.isPlaying) {
                    return;
                }
                eqStep = (eqStep + 1) % 4;
                float h1 = (eqStep == 0) ? 0.9f : ((eqStep == 1) ? 0.4f : ((eqStep == 2) ? 0.7f : 0.3f));
                float h2 = (eqStep == 0) ? 0.3f : ((eqStep == 1) ? 0.8f : ((eqStep == 2) ? 0.4f : 1.0f));
                float h3 = (eqStep == 0) ? 0.7f : ((eqStep == 1) ? 0.3f : ((eqStep == 2) ? 0.9f : 0.5f));
                if (eqBar1 != null) eqBar1.setScaleY(h1);
                if (eqBar2 != null) eqBar2.setScaleY(h2);
                if (eqBar3 != null) eqBar3.setScaleY(h3);
                card.postDelayed(this, 250L);
            }
        };

        void stopCarousels() {
            card.removeCallbacks(newsCarouselRunnable);
            card.removeCallbacks(youtubeCarouselRunnable);
            card.removeCallbacks(footballCarouselRunnable);
            card.removeCallbacks(equalizerRunnable);
        }

        void startCarousels() {
            stopCarousels();
            int type = getItemViewType();
            if (type == TYPE_NEWS && newsList.size() > 1) {
                card.postDelayed(newsCarouselRunnable, 6000L);
            } else if (type == TYPE_YOUTUBE && youtubeList.size() > 1) {
                card.postDelayed(youtubeCarouselRunnable, 8000L);
            } else if (type == TYPE_FOOTBALL && footballList.size() > 1) {
                card.postDelayed(footballCarouselRunnable, 6500L);
            } else if (type == TYPE_MEDIA && playbackInfo != null && playbackInfo.isPlaying) {
                card.post(equalizerRunnable);
            }
        }

        private void resetViews() {
            category.setVisibility(View.GONE);
            meta.setVisibility(View.GONE);
            weatherHeaderContainer.setVisibility(View.GONE);
            if (weatherConditionIcon != null) weatherConditionIcon.setVisibility(View.GONE);
            weatherHumidityPill.setVisibility(View.GONE);
            if (weatherForecastContainer != null) weatherForecastContainer.setVisibility(View.GONE);
            newsHeaderContainer.setVisibility(View.GONE);
            youtubeHeaderContainer.setVisibility(View.GONE);
            musicHeaderContainer.setVisibility(View.GONE);
            musicEqualizerContainer.setVisibility(View.GONE);
            heroMetric.setVisibility(View.GONE);
            heroTitle.setVisibility(View.GONE);
            usageAvatarsContainer.setVisibility(View.GONE);
            usageBarContainer.setVisibility(View.GONE);
            usageLegendContainer.setVisibility(View.GONE);
            subText.setVisibility(View.GONE);
            extra.setVisibility(View.GONE);
            if (mediaCenterAppIcon != null) mediaCenterAppIcon.setVisibility(View.GONE);
            if (mediaBottomContainer != null) mediaBottomContainer.setVisibility(View.GONE);
            if (footballContainer != null) footballContainer.setVisibility(View.GONE);
            bgImage.setVisibility(View.GONE);
            bgScrim.setVisibility(View.GONE);
        }

        void bind(int type) {
            bind(type, false);
        }

        void bind(int type, boolean isPartialUpdate) {
            if (!isPartialUpdate) {
                stopCarousels();
                resetViews();
            } else {
                if (type == TYPE_NEWS) card.removeCallbacks(newsCarouselRunnable);
                else if (type == TYPE_YOUTUBE) card.removeCallbacks(youtubeCarouselRunnable);
                else if (type == TYPE_FOOTBALL) card.removeCallbacks(footballCarouselRunnable);
            }

            if (type == TYPE_WEATHER) {
                weatherHeaderContainer.setVisibility(View.VISIBLE);
                weatherHumidityPill.setVisibility(View.VISIBLE);
                heroMetric.setTextSize(46f);
                heroMetric.setVisibility(View.VISIBLE);

                int bgRes = R.drawable.bg_weather_clear;
                if (weatherInfo != null) {
                    String desc = weatherInfo.description != null ? weatherInfo.description.toLowerCase() : "";
                    if (desc.contains("mưa") || desc.contains("rain") || desc.contains("dông") || desc.contains("bão")) {
                        bgRes = R.drawable.bg_weather_rain;
                    } else if (desc.contains("mây") || desc.contains("cloud") || desc.contains("âm u")) {
                        bgRes = R.drawable.bg_weather_clouds;
                    } else {
                        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                        if (hour >= 18 || hour < 5) bgRes = R.drawable.bg_weather_night;
                        else if (hour >= 16) bgRes = R.drawable.bg_weather_dusk;
                        else bgRes = R.drawable.bg_weather_clear;
                    }
                } else {
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    if (hour >= 18 || hour < 5) bgRes = R.drawable.bg_weather_night;
                    else if (hour >= 16) bgRes = R.drawable.bg_weather_dusk;
                    else bgRes = R.drawable.bg_weather_clear;
                }

                bgImage.setImageResource(bgRes);
                bgImage.setAlpha(0.70f);
                bgImage.setVisibility(View.VISIBLE);
                bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                bgScrim.setVisibility(View.VISIBLE);

                if (weatherInfo != null) {
                    weatherLocationText.setText(weatherInfo.city);
                    weatherConditionText.setText(weatherInfo.description);
                    heroMetric.setText(Math.round(weatherInfo.temperature) + "°");
                    weatherHumidityPill.setText("Độ ẩm " + weatherInfo.humidity + "%");
                    if (weatherConditionIcon != null) {
                        weatherConditionIcon.setImageResource(getWeatherIconRes(weatherInfo.weatherCode));
                        weatherConditionIcon.setVisibility(View.VISIBLE);
                    }
                    if (weatherForecastContainer != null && weatherInfo.dailyForecast != null && !weatherInfo.dailyForecast.isEmpty()) {
                        List<WeatherDataManager.DayForecast> dfList = weatherInfo.dailyForecast;
                        if (dfList.size() >= 1 && forecastDay1 != null) {
                            WeatherDataManager.DayForecast df1 = dfList.get(0);
                            forecastDay1Name.setText(df1.dayName);
                            forecastDay1Icon.setImageResource(getWeatherIconRes(df1.weatherCode));
                            forecastDay1Temp.setText(df1.maxTemp + "°");
                            forecastDay1.setVisibility(View.VISIBLE);
                        }
                        if (dfList.size() >= 2 && forecastDay2 != null) {
                            WeatherDataManager.DayForecast df2 = dfList.get(1);
                            forecastDay2Name.setText(df2.dayName);
                            forecastDay2Icon.setImageResource(getWeatherIconRes(df2.weatherCode));
                            forecastDay2Temp.setText(df2.maxTemp + "°");
                            forecastDay2.setVisibility(View.VISIBLE);
                        }
                        if (dfList.size() >= 3 && forecastDay3 != null) {
                            WeatherDataManager.DayForecast df3 = dfList.get(2);
                            forecastDay3Name.setText(df3.dayName);
                            forecastDay3Icon.setImageResource(getWeatherIconRes(df3.weatherCode));
                            forecastDay3Temp.setText(df3.maxTemp + "°");
                            forecastDay3.setVisibility(View.VISIBLE);
                        }
                        weatherForecastContainer.setVisibility(View.VISIBLE);
                    }
                } else {
                    weatherLocationText.setText("Thời tiết");
                    weatherConditionText.setText("Đang cập nhật");
                    heroMetric.setText("--°");
                    weatherHumidityPill.setText("Đang tải");
                }
            } else if (type == TYPE_YOUTUBE) {
                youtubeHeaderContainer.setVisibility(View.VISIBLE);
                heroTitle.setVisibility(View.VISIBLE);
                subText.setVisibility(View.VISIBLE);

                YouTubeTrendingManager.YouTubeVideoItem video = (!youtubeList.isEmpty())
                        ? youtubeList.get(currentYouTubeIndex % youtubeList.size()) : null;
                if (video != null) {
                    heroTitle.setText(video.title);
                    subText.setText(video.channelTitle + " · " + video.viewCountText);
                    if (youtubeBadgeText != null) {
                        youtubeBadgeText.setText(video.categoryLabel != null ? video.categoryLabel : "Thịnh hành");
                    }
                    if (youtubeDurationBadge != null) {
                        youtubeDurationBadge.setText(video.durationText);
                    }
                    if (video.thumbnailUrl != null && !video.thumbnailUrl.isEmpty()) {
                        bgImage.setAlpha(0.65f);
                        bgImage.setVisibility(View.VISIBLE);
                        bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                        bgScrim.setVisibility(View.VISIBLE);
                        FastPosterLoader.getInstance().load(Uri.parse(video.thumbnailUrl), bgImage, 0);
                    }
                    if (youtubeList.size() > 1) {
                        card.postDelayed(youtubeCarouselRunnable, 8500L);
                    }
                } else {
                    heroTitle.setText("Đang tải video thịnh hành...");
                    bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                    bgScrim.setVisibility(View.VISIBLE);
                }
            } else if (type == TYPE_NEWS) {
                newsHeaderContainer.setVisibility(View.VISIBLE);
                heroTitle.setVisibility(View.VISIBLE);

                NewsDataManager.NewsItem item = (!newsList.isEmpty()) ? newsList.get(currentNewsIndex % newsList.size()) : null;
                if (item != null) {
                    heroTitle.setText(item.title);
                    if (subText != null) {
                        subText.setText(NewsDataManager.formatTimeAgo(item.pubDate));
                        subText.setVisibility(View.VISIBLE);
                    }
                    if (newsBadgeText != null) {
                        newsBadgeText.setText(item.source != null ? item.source : "Tin tức");
                    }
                    if (newsBadgeCategory != null) {
                        newsBadgeCategory.setText(item.category != null ? item.category.toUpperCase(Locale.getDefault()) : "TIN NÓNG");
                    }
                    if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                        bgImage.setAlpha(0.65f);
                        bgImage.setVisibility(View.VISIBLE);
                        bgScrim.setBackgroundResource(R.drawable.widget_scrim_news);
                        bgScrim.setVisibility(View.VISIBLE);
                        FastPosterLoader.getInstance().load(Uri.parse(item.imageUrl), bgImage, 0);
                    } else {
                        bgScrim.setBackgroundResource(R.drawable.widget_scrim_news);
                        bgScrim.setVisibility(View.VISIBLE);
                    }
                    if (newsList.size() > 1) {
                        int wordCount = item.title != null ? item.title.trim().split("\\s+").length : 10;
                        long delay = Math.max(5000L, Math.min(10000L, 3500L + wordCount * 220L));
                        card.postDelayed(newsCarouselRunnable, delay);
                    }
                } else {
                    heroTitle.setText(R.string.widget_news_loading);
                    bgScrim.setBackgroundResource(R.drawable.widget_scrim_news);
                    bgScrim.setVisibility(View.VISIBLE);
                }
            } else if (type == TYPE_USAGE) {
                category.setVisibility(View.VISIBLE);
                category.setText("THỜI GIAN XEM");
                meta.setVisibility(View.VISIBLE);
                meta.setText("Hôm nay");
                heroMetric.setTextSize(32f);
                heroMetric.setVisibility(View.VISIBLE);

                if (usageInfo != null && usageInfo.hasPermission) {
                    heroMetric.setText(usageInfo.formatTotalHoursMinutes());
                    if (usageInfo.topApps != null && !usageInfo.topApps.isEmpty()) {
                        usageAvatarsContainer.setVisibility(View.VISIBLE);
                        usageBarContainer.setVisibility(View.VISIBLE);
                        usageLegendContainer.setVisibility(View.VISIBLE);

                        ImageView[] avatars = new ImageView[]{avatarApp1, avatarApp2, avatarApp3};
                        View[] bars = new View[]{barSegment1, barSegment2, barSegment3};
                        int[] dotColors = new int[]{Color.parseColor("#FF453A"), Color.parseColor("#0A84FF"), Color.parseColor("#30D158")};

                        SpannableStringBuilder legend = new SpannableStringBuilder();
                        for (int i = 0; i < 3; i++) {
                            if (i < usageInfo.topApps.size()) {
                                UsageStatsTracker.AppUsageItem app = usageInfo.topApps.get(i);
                                avatars[i].setVisibility(View.VISIBLE);
                                if (app.icon != null) {
                                    avatars[i].setImageDrawable(app.icon);
                                } else {
                                    avatars[i].setImageResource(R.mipmap.ic_launcher);
                                }

                                bars[i].setVisibility(View.VISIBLE);
                                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bars[i].getLayoutParams();
                                lp.weight = Math.max(0.12f, app.percentage);
                                bars[i].setLayoutParams(lp);

                                if (i < 2) {
                                    if (legend.length() > 0) legend.append("   ");
                                    int dotStart = legend.length();
                                    legend.append("●");
                                    legend.setSpan(new ForegroundColorSpan(dotColors[i]), dotStart, dotStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    legend.append(" ").append(app.appName).append(" (").append(formatAppTime(app.foregroundMillis)).append(")");
                                }
                            } else {
                                avatars[i].setVisibility(View.GONE);
                                bars[i].setVisibility(View.GONE);
                            }
                        }
                        usageLegendText.setText(legend);
                    }
                } else {
                    heroMetric.setText("Bật thống kê");
                }
            } else if (type == TYPE_MEDIA) {
                musicHeaderContainer.setVisibility(View.VISIBLE);
                musicEqualizerContainer.setVisibility(View.VISIBLE);
                if (mediaBottomContainer != null) mediaBottomContainer.setVisibility(View.VISIBLE);

                if (playbackInfo != null && playbackInfo.isPlaying) {
                    String trackTitle = playbackInfo.title != null ? playbackInfo.title : "Đang phát nhạc";
                    String trackArtist = playbackInfo.artist != null ? playbackInfo.artist : "";
                    if (mediaTrackTitle != null) mediaTrackTitle.setText(trackTitle);
                    if (mediaTrackArtist != null) mediaTrackArtist.setText(trackArtist);

                    Drawable appIcon = null;
                    if (playbackInfo.appPackage != null) {
                        try {
                            PackageManager pm = context.getPackageManager();
                            appIcon = pm.getApplicationIcon(playbackInfo.appPackage);
                            musicAppIcon.setImageDrawable(appIcon);
                        } catch (Exception ignored) {
                            musicAppIcon.setImageResource(R.drawable.ic_music_note);
                        }
                    } else {
                        musicAppIcon.setImageResource(R.drawable.ic_music_note);
                    }

                    boolean hasArt = false;
                    if (playbackInfo.albumArt != null) {
                        hasArt = true;
                        if (mediaAlbumArt != null) {
                            mediaAlbumArt.setImageBitmap(playbackInfo.albumArt);
                            mediaAlbumArt.setVisibility(View.VISIBLE);
                        }
                        if (mediaAppIconFallback != null) mediaAppIconFallback.setVisibility(View.GONE);
                        bgImage.setImageBitmap(playbackInfo.albumArt);
                        bgImage.setAlpha(0.40f);
                        bgImage.setVisibility(View.VISIBLE);
                        bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                        bgScrim.setVisibility(View.VISIBLE);
                    } else if (playbackInfo.albumArtUri != null && !playbackInfo.albumArtUri.isEmpty()) {
                        hasArt = true;
                        if (mediaAlbumArt != null) {
                            mediaAlbumArt.setVisibility(View.VISIBLE);
                            FastPosterLoader.getInstance().load(Uri.parse(playbackInfo.albumArtUri), mediaAlbumArt, 0);
                        }
                        if (mediaAppIconFallback != null) mediaAppIconFallback.setVisibility(View.GONE);
                        bgImage.setAlpha(0.40f);
                        bgImage.setVisibility(View.VISIBLE);
                        bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                        bgScrim.setVisibility(View.VISIBLE);
                        FastPosterLoader.getInstance().load(Uri.parse(playbackInfo.albumArtUri), bgImage, 0);
                    }

                    if (!hasArt) {
                        // Fallback: subtle gradient backdrop + 44dp app icon in center and thumbnail fallback
                        if (mediaAlbumArt != null) mediaAlbumArt.setVisibility(View.GONE);
                        if (mediaAppIconFallback != null) {
                            if (appIcon != null) {
                                mediaAppIconFallback.setImageDrawable(appIcon);
                            } else {
                                mediaAppIconFallback.setImageResource(R.drawable.ic_music_note);
                            }
                            mediaAppIconFallback.setVisibility(View.VISIBLE);
                        }
                        if (mediaCenterAppIcon != null) {
                            if (appIcon != null) {
                                mediaCenterAppIcon.setImageDrawable(appIcon);
                            } else {
                                mediaCenterAppIcon.setImageResource(R.drawable.ic_music_note);
                            }
                            mediaCenterAppIcon.setVisibility(View.VISIBLE);
                        }
                        bgImage.setVisibility(View.GONE);
                        bgScrim.setBackgroundResource(R.drawable.bg_media_card_gradient);
                        bgScrim.setVisibility(View.VISIBLE);
                    }

                    if (mediaPlayPauseIcon != null) {
                        mediaPlayPauseIcon.setImageResource(R.drawable.ic_pause_white);
                    }

                    if (eqBar1 != null) eqBar1.setPivotY(12f * density);
                    if (eqBar2 != null) eqBar2.setPivotY(12f * density);
                    if (eqBar3 != null) eqBar3.setPivotY(12f * density);
                    card.post(equalizerRunnable);
                } else {
                    if (mediaTrackTitle != null) mediaTrackTitle.setText("Âm thanh");
                    if (mediaTrackArtist != null) mediaTrackArtist.setText("Tạm dừng");
                    if (mediaPlayPauseIcon != null) {
                        mediaPlayPauseIcon.setImageResource(R.drawable.ic_play_white);
                    }
                    bgScrim.setBackgroundResource(R.drawable.bg_media_card_gradient);
                    bgScrim.setVisibility(View.VISIBLE);
                }
            } else if (type == TYPE_FOOTBALL) {
                if (footballContainer != null) footballContainer.setVisibility(View.VISIBLE);
                bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
                bgScrim.setVisibility(View.VISIBLE);

                if (!footballList.isEmpty()) {
                    FootballDataManager.FootballMatchItem item = footballList.get(currentFootballIndex % footballList.size());
                    updateFootballScoreboardUI(item);
                    if (footballList.size() > 1) {
                        card.postDelayed(footballCarouselRunnable, 6500L);
                    }
                } else {
                    if (footballHomeName != null) footballHomeName.setText("Đang tải...");
                    if (footballAwayName != null) footballAwayName.setText("Thể thao");
                    if (footballScoreText != null) footballScoreText.setText("--");
                    if (footballStatusBadge != null) {
                        footballStatusBadge.setText("Chờ dữ liệu");
                        footballStatusBadge.setTextColor(Color.parseColor("#A1A1AA"));
                    }
                    if (footballLiveDot != null) footballLiveDot.setBackgroundResource(R.drawable.badge_football_gray_dot);
                    if (footballLeagueBadge != null) footballLeagueBadge.setText("Trực tiếp");
                    if (footballSportIcon != null) footballSportIcon.setImageResource(R.drawable.ic_sport_football);
                    if (footballBottomHint != null) footballBottomHint.setText("Đang cập nhật lịch thi đấu...");
                }
            } else if (type == TYPE_GOLD) {
                renderHeroMetricCard("Vàng SJC 9999", goldData, R.drawable.bg_weather_dusk, "Giá vàng trong nước");
            } else if (type == TYPE_CRYPTO) {
                renderHeroMetricCard("Bitcoin & Crypto", cryptoData, R.drawable.bg_weather_night, "Thị trường tiền số");
            } else if (type == TYPE_VIETNAM_CALENDAR) {
                renderHeroMetricCard("Lịch Âm Dương", calendarData, R.drawable.bg_weather_clear, "Âm lịch Việt Nam");
            } else if (type >= 100) {
                org.json.JSONObject customData = customAddonsData.get(type);
                WidgetAddon addon = WidgetAddonManager.getInstance(context).getAddonForWidgetType(type);
                String customTitle = (addon != null && !addon.getName().isEmpty()) ? addon.getName() : "Tiện ích mở rộng";
                String customDesc = (addon != null && !addon.getDescription().isEmpty()) ? addon.getDescription() : "Tiện ích mở rộng";
                renderHeroMetricCard(customTitle, customData, R.drawable.bg_weather_clouds, customDesc);
            }
        }

        private void renderHeroMetricCard(String defaultCategory, org.json.JSONObject data, int bgRes, String defaultSub) {
            weatherHeaderContainer.setVisibility(View.VISIBLE);
            weatherHumidityPill.setVisibility(View.GONE);
            heroMetric.setTextSize(34f);
            heroMetric.setVisibility(View.VISIBLE);

            bgImage.setImageResource(bgRes);
            bgImage.setAlpha(0.65f);
            bgImage.setVisibility(View.VISIBLE);
            bgScrim.setBackgroundResource(R.drawable.widget_scrim_gradient);
            bgScrim.setVisibility(View.VISIBLE);

            if (data != null) {
                String headerLeft = data.optString("headerLeft", defaultCategory);
                String headerRight = data.optString("headerRight", "");
                weatherLocationText.setText(headerLeft);
                weatherConditionText.setText(headerRight);

                double rawVal = data.optDouble("rawTemp", 0.0);
                String heroUnit = data.optString("heroUnit", "");
                if (rawVal > 0) {
                    if (rawVal >= 1000) {
                        heroMetric.setText(String.format(java.util.Locale.US, "%,.0f %s", rawVal, heroUnit).trim());
                    } else {
                        heroMetric.setText(String.format(java.util.Locale.US, "%.1f %s", rawVal, heroUnit).trim());
                    }
                } else {
                    String heroText = data.optString("heroMetric", heroUnit);
                    heroMetric.setText(heroText.isEmpty() ? defaultSub : heroText);
                }

                org.json.JSONArray subArr = data.optJSONArray("subItems");
                if (subArr != null && subArr.length() > 0 && weatherForecastContainer != null) {
                    weatherForecastContainer.setVisibility(View.VISIBLE);
                    if (subArr.length() > 0 && forecastDay1 != null) {
                        org.json.JSONObject s0 = subArr.optJSONObject(0);
                        if (s0 != null) {
                            forecastDay1Name.setText(s0.optString("label", ""));
                            forecastDay1Icon.setVisibility(View.GONE);
                            double v = s0.optDouble("temp", 0);
                            forecastDay1Temp.setText(v >= 1000 ? String.format(java.util.Locale.US, "%,.0f", v) : (v > 0 ? String.valueOf(v) : s0.optString("value", "--")));
                            forecastDay1.setVisibility(View.VISIBLE);
                        }
                    }
                    if (subArr.length() > 1 && forecastDay2 != null) {
                        org.json.JSONObject s1 = subArr.optJSONObject(1);
                        if (s1 != null) {
                            forecastDay2Name.setText(s1.optString("label", ""));
                            forecastDay2Icon.setVisibility(View.GONE);
                            double v = s1.optDouble("temp", 0);
                            forecastDay2Temp.setText(v >= 1000 ? String.format(java.util.Locale.US, "%,.0f", v) : (v > 0 ? String.valueOf(v) : s1.optString("value", "--")));
                            forecastDay2.setVisibility(View.VISIBLE);
                        }
                    }
                    if (subArr.length() > 2 && forecastDay3 != null) {
                        org.json.JSONObject s2 = subArr.optJSONObject(2);
                        if (s2 != null) {
                            forecastDay3Name.setText(s2.optString("label", ""));
                            forecastDay3Icon.setVisibility(View.GONE);
                            double v = s2.optDouble("temp", 0);
                            forecastDay3Temp.setText(v >= 1000 ? String.format(java.util.Locale.US, "%,.0f", v) : (v > 0 ? String.valueOf(v) : s2.optString("value", "--")));
                            forecastDay3.setVisibility(View.VISIBLE);
                        }
                    }
                } else if (weatherForecastContainer != null) {
                    weatherForecastContainer.setVisibility(View.GONE);
                }
            } else {
                weatherLocationText.setText(defaultCategory);
                weatherConditionText.setText("Đang cập nhật");
                heroMetric.setText("--");
                if (weatherForecastContainer != null) {
                    weatherForecastContainer.setVisibility(View.GONE);
                }
            }
        }

        private String formatAppTime(long millis) {
            long totalMinutes = millis / (60 * 1000L);
            long hours = totalMinutes / 60;
            long mins = totalMinutes % 60;
            if (hours > 0) {
                return hours + "h " + mins + "m";
            } else {
                return mins + "m";
            }
        }

        private static int getWeatherIconRes(int weatherCode) {
            if (weatherCode == 0) return R.drawable.ic_weather_sun;
            if (weatherCode >= 1 && weatherCode <= 3) return R.drawable.ic_weather_cloud;
            if (weatherCode == 45 || weatherCode == 48) return R.drawable.ic_weather_cloud;
            if (weatherCode >= 51 && weatherCode <= 67) return R.drawable.ic_weather_rain;
            if (weatherCode >= 71 && weatherCode <= 77) return R.drawable.ic_weather_rain;
            if (weatherCode >= 80 && weatherCode <= 82) return R.drawable.ic_weather_rain;
            if (weatherCode >= 95 && weatherCode <= 99) return R.drawable.ic_weather_bolt;
            return R.drawable.ic_weather_cloud;
        }
    }
}
