package com.next.tv.launcher.search;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.apps.AppIconCleaner;
import com.next.tv.launcher.util.FastPosterLoader;
import com.next.tv.launcher.R;
import com.next.tv.launcher.lock.AppLockManager;
import com.next.tv.launcher.lock.PinLockDialog;

import java.util.ArrayList;
import java.util.List;

public final class SearchCardAdapter extends RecyclerView.Adapter<SearchCardAdapter.BaseViewHolder> {
    public interface NavigationListener {
        void onNavigateUpFromCard();
        void onNavigateDownFromCard();
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<SearchResultItem> items = new ArrayList<>();
    private final float density;
    private final int defaultItemType;
    private NavigationListener navigationListener;
    private long displayItemsTime = 0;

    private Drawable cachedStremioIcon;
    private Drawable cachedYoutubeIcon;

    public SearchCardAdapter(Context context) {
        this(context, SearchResultItem.TYPE_MOVIE);
    }

    public SearchCardAdapter(Context context, int defaultItemType) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.defaultItemType = defaultItemType;
        setHasStableIds(true);

        loadSourceIcons();
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < items.size()) {
            SearchResultItem item = items.get(position);
            if (item.itemType == SearchResultItem.TYPE_SKELETON) {
                return -(position + 1L);
            }
            long h = item.itemType;
            if (item.title != null) h = 31 * h + item.title.hashCode();
            if (item.subtitle != null) h = 31 * h + item.subtitle.hashCode();
            if (item.iconUrl != null) h = 31 * h + item.iconUrl.hashCode();
            return h;
        }
        return RecyclerView.NO_ID;
    }

    private void loadSourceIcons() {
        cachedStremioIcon = AppIconCleaner.getCleanAppIcon(context, "com.stremio.one", density);
        cachedYoutubeIcon = AppIconCleaner.getCleanAppIcon(context, "com.google.android.youtube.tv", density);
        if (cachedYoutubeIcon == null) {
            cachedYoutubeIcon = AppIconCleaner.getCleanAppIcon(context, "com.liskovsoft.videomanager", density);
        }
        if (cachedYoutubeIcon == null) {
            cachedYoutubeIcon = AppIconCleaner.getCleanAppIcon(context, "com.teamsmart.videomanager.tv", density);
        }
        if (cachedYoutubeIcon == null) {
            cachedYoutubeIcon = AppIconCleaner.getCleanAppIcon(context, "com.liskovsoft.smarttubetv.beta", density);
        }
    }

    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }

    public void setItems(List<SearchResultItem> newItems) {
        this.displayItemsTime = android.os.SystemClock.uptimeMillis();
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public long getDisplayItemsTime() {
        return displayItemsTime;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= 0 && position < items.size()) {
            SearchResultItem item = items.get(position);
            return item.itemType != 0 ? item.itemType : defaultItemType;
        }
        return defaultItemType;
    }

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SearchResultItem.TYPE_SKELETON) {
            View v = inflater.inflate(R.layout.item_search_skeleton_card, parent, false);
            return new SkeletonViewHolder(v, density, this);
        } else if (viewType == SearchResultItem.TYPE_YOUTUBE) {
            View v = inflater.inflate(R.layout.item_search_youtube_card, parent, false);
            return new YoutubeViewHolder(v, density, navigationListener, cachedYoutubeIcon, this);
        } else if (viewType == SearchResultItem.TYPE_WEB) {
            View v = inflater.inflate(R.layout.item_search_web_card, parent, false);
            return new WebViewHolder(v, density, navigationListener, this);
        } else {
            View v = inflater.inflate(R.layout.item_search_movie_card, parent, false);
            return new MovieViewHolder(v, density, navigationListener, cachedStremioIcon, this);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        SearchResultItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    abstract static class BaseViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final TextView title;
        final TextView subtitle;
        final TextView badge;
        final ImageView sourceIcon;
        final float density;
        final NavigationListener navListener;
        final SearchCardAdapter adapter;

        BaseViewHolder(@NonNull View itemView, float density, NavigationListener navListener, SearchCardAdapter adapter) {
            super(itemView);
            this.density = density;
            this.navListener = navListener;
            this.adapter = adapter;
            itemView.setFocusable(false);
            itemView.setFocusableInTouchMode(false);
            this.card = itemView.findViewById(R.id.searchCard);
            this.title = itemView.findViewById(R.id.searchCardTitle);
            this.subtitle = itemView.findViewById(R.id.searchCardSubtitle);
            this.badge = itemView.findViewById(R.id.searchCardBadge);
            this.sourceIcon = itemView.findViewById(R.id.searchCardSourceIcon);
        }

        abstract void bind(SearchResultItem item);

        void setupCommonInteractions(SearchResultItem item, float targetScale) {
            if (title != null) title.setText(item.title != null ? item.title : "");
            if (subtitle != null) subtitle.setText(item.subtitle != null ? item.subtitle : "");
            if (badge != null) {
                if (item.badge != null && !item.badge.trim().isEmpty()) {
                    badge.setText(item.badge.trim());
                    badge.setVisibility(View.VISIBLE);
                } else {
                    badge.setVisibility(View.GONE);
                }
            }

            if (card != null) {
                card.setFocusable(true);
                card.setFocusableInTouchMode(true);
                card.setOnClickListener(v -> {
                    if (adapter != null && android.os.SystemClock.uptimeMillis() - adapter.getDisplayItemsTime() < 400) {
                        return;
                    }
                    String targetPkg = item.actionIntent != null ? item.actionIntent.getPackage() : null;
                    AppLockManager lockManager = AppLockManager.getInstance(v.getContext());
                    Runnable doLaunch = () -> {
                        if (item.onClick != null) {
                            item.onClick.run();
                        } else if (item.actionIntent != null) {
                            try {
                                v.getContext().startActivity(item.actionIntent);
                            } catch (Exception ignored) {}
                        }
                    };
                    if (targetPkg != null && lockManager.isAppLocked(targetPkg)) {
                        new PinLockDialog(v.getContext(), "Khóa ứng dụng", "Nhập mã để mở app", new PinLockDialog.PinResultListener() {
                            @Override
                            public void onSuccess() {
                                doLaunch.run();
                            }

                            @Override
                            public void onCancel() {}
                        }).show();
                    } else {
                        doLaunch.run();
                    }
                });

                card.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (navListener != null) {
                                navListener.onNavigateUpFromCard();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (navListener != null) {
                                navListener.onNavigateDownFromCard();
                                return true;
                            }
                        }
                    }
                    return false;
                });

                card.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate().scaleX(targetScale).scaleY(targetScale)
                                .translationZ(6f * density).setDuration(160).start();
                        if (title != null) title.setTextColor(0xFFFFFFFF);
                        if (subtitle != null) subtitle.setTextColor(0xFFE4E4E7);
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f)
                                .translationZ(0f).setDuration(120).start();
                        if (title != null) title.setTextColor(0xFFF4F4F5);
                        if (subtitle != null) subtitle.setTextColor(0xFFA1A1AA);
                    }
                });
            }
        }
    }

    static final class SkeletonViewHolder extends BaseViewHolder {
        private final View shimmer;
        private ObjectAnimator animator;

        SkeletonViewHolder(@NonNull View itemView, float density, SearchCardAdapter adapter) {
            super(itemView, density, null, adapter);
            shimmer = itemView.findViewById(R.id.skeletonShimmer);
            if (card != null) {
                card.setFocusable(false);
                card.setFocusableInTouchMode(false);
            }
            itemView.setFocusable(false);
            itemView.setFocusableInTouchMode(false);
        }

        @Override
        void bind(SearchResultItem item) {
            if (card != null) {
                card.setFocusable(false);
                card.setFocusableInTouchMode(false);
            }
            if (animator != null) animator.cancel();
            if (shimmer != null) {
                animator = ObjectAnimator.ofFloat(shimmer, "alpha", 0.35f, 0.85f);
                animator.setDuration(750);
                animator.setRepeatMode(ValueAnimator.REVERSE);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.start();
            }
        }
    }

    static final class MovieViewHolder extends BaseViewHolder {
        final ImageView poster;
        final ImageView placeholder;
        final Drawable defaultSourceIcon;

        MovieViewHolder(@NonNull View itemView, float density, NavigationListener navListener, Drawable defaultSourceIcon, SearchCardAdapter adapter) {
            super(itemView, density, navListener, adapter);
            this.defaultSourceIcon = defaultSourceIcon;
            poster = itemView.findViewById(R.id.searchCardPoster);
            placeholder = itemView.findViewById(R.id.searchCardPlaceholder);

            if (poster != null) {
                poster.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10f * density);
                    }
                });
                poster.setClipToOutline(true);
            }
        }

        @Override
        void bind(SearchResultItem item) {
            setupCommonInteractions(item, 1.08f);

            if (sourceIcon != null) {
                if (item.iconDrawable != null) {
                    sourceIcon.setImageDrawable(item.iconDrawable);
                    sourceIcon.setVisibility(View.VISIBLE);
                } else if (defaultSourceIcon != null) {
                    sourceIcon.setImageDrawable(defaultSourceIcon);
                    sourceIcon.setVisibility(View.VISIBLE);
                } else {
                    sourceIcon.setVisibility(View.GONE);
                }
            }

            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                if (poster != null) {
                    poster.setVisibility(View.VISIBLE);
                    FastPosterLoader.getInstance().load(Uri.parse(item.iconUrl), poster, 0);
                }
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            } else {
                if (poster != null) {
                    poster.setImageDrawable(null);
                    poster.setVisibility(View.INVISIBLE);
                }
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        }
    }

    static final class YoutubeViewHolder extends BaseViewHolder {
        final ImageView thumbnail;
        final ImageView placeholder;
        final Drawable defaultSourceIcon;

        YoutubeViewHolder(@NonNull View itemView, float density, NavigationListener navListener, Drawable defaultSourceIcon, SearchCardAdapter adapter) {
            super(itemView, density, navListener, adapter);
            this.defaultSourceIcon = defaultSourceIcon;
            thumbnail = itemView.findViewById(R.id.searchCardThumbnail);
            placeholder = itemView.findViewById(R.id.searchCardPlaceholder);

            if (thumbnail != null) {
                thumbnail.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10f * density);
                    }
                });
                thumbnail.setClipToOutline(true);
            }
        }

        @Override
        void bind(SearchResultItem item) {
            setupCommonInteractions(item, 1.08f);

            if (sourceIcon != null) {
                if (item.iconDrawable != null) {
                    sourceIcon.setImageDrawable(item.iconDrawable);
                    sourceIcon.setVisibility(View.VISIBLE);
                } else if (defaultSourceIcon != null) {
                    sourceIcon.setImageDrawable(defaultSourceIcon);
                    sourceIcon.setVisibility(View.VISIBLE);
                } else {
                    sourceIcon.setVisibility(View.GONE);
                }
            }

            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                if (thumbnail != null) {
                    thumbnail.setVisibility(View.VISIBLE);
                    FastPosterLoader.getInstance().load(Uri.parse(item.iconUrl), thumbnail, 0);
                }
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            } else {
                if (thumbnail != null) {
                    thumbnail.setImageDrawable(null);
                    thumbnail.setVisibility(View.INVISIBLE);
                }
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        }
    }

    static final class WebViewHolder extends BaseViewHolder {
        final ImageView thumbnail;
        final ImageView placeholder;

        WebViewHolder(@NonNull View itemView, float density, NavigationListener navListener, SearchCardAdapter adapter) {
            super(itemView, density, navListener, adapter);
            thumbnail = itemView.findViewById(R.id.searchCardThumbnail);
            placeholder = itemView.findViewById(R.id.searchCardPlaceholder);

            if (thumbnail != null) {
                thumbnail.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10f * density);
                    }
                });
                thumbnail.setClipToOutline(true);
            }
        }

        @Override
        void bind(SearchResultItem item) {
            setupCommonInteractions(item, 1.08f);

            if (sourceIcon != null) {
                sourceIcon.setImageResource(R.drawable.ic_globe_placeholder);
                sourceIcon.setVisibility(View.VISIBLE);
            }

            if (item.iconUrl != null && !item.iconUrl.isEmpty()) {
                if (thumbnail != null) {
                    thumbnail.setVisibility(View.VISIBLE);
                    FastPosterLoader.getInstance().load(Uri.parse(item.iconUrl), thumbnail, 0);
                }
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            } else {
                if (thumbnail != null) {
                    thumbnail.setImageDrawable(null);
                    thumbnail.setVisibility(View.INVISIBLE);
                }
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        }
    }
}
