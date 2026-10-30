package com.next.tv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviePosterAdapter extends RecyclerView.Adapter<MoviePosterAdapter.ViewHolder> {
    private static final String TAG = "TvHome.MovieAdapter";

    public interface OnMovieFocusListener {
        void onMovieFocused(TvRecommendationReader.MovieItem item, View view);
    }

    private final Context context;
    private final List<TvRecommendationReader.MovieItem> items = new ArrayList<>();
    private final OnMovieFocusListener focusListener;
    private boolean isPortrait;
    private final boolean advancedEffects;
    private final float focusScale;
    private final float density;

    public MoviePosterAdapter(Context context, boolean isPortrait, boolean advancedEffects, float focusScale, OnMovieFocusListener focusListener) {
        this.context = context;
        this.isPortrait = isPortrait;
        this.focusListener = focusListener;
        this.advancedEffects = advancedEffects;
        this.focusScale = focusScale;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    public void setPortrait(boolean portrait) {
        if (this.isPortrait != portrait) {
            this.isPortrait = portrait;
            notifyDataSetChanged();
        }
    }

    public boolean isPortrait() {
        return isPortrait;
    }

    @Override
    public int getItemViewType(int position) {
        return isPortrait ? 1 : 0;
    }

    public void setItems(List<TvRecommendationReader.MovieItem> newItems) {
        if (newItems == null && items.isEmpty()) return;
        if (newItems != null && newItems.size() == items.size()) {
            boolean identical = true;
            for (int i = 0; i < newItems.size(); i++) {
                TvRecommendationReader.MovieItem a = items.get(i);
                TvRecommendationReader.MovieItem b = newItems.get(i);
                if (a.id != b.id || (a.posterArtUri != null && !a.posterArtUri.equals(b.posterArtUri))) {
                    identical = false;
                    break;
                }
            }
            if (identical) return;
        }
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    private Drawable getAppIcon(String packageName) {
        return AppIconCleaner.getCleanAppIcon(context, packageName, density);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == 1) ? R.layout.item_movie_poster_portrait : R.layout.item_movie_poster;
        View v = LayoutInflater.from(context).inflate(layoutRes, parent, false);
        return new ViewHolder(v, density);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TvRecommendationReader.MovieItem item = items.get(position);
        holder.title.setText(item.title);

        // Episode badge pill (top-left of thumbnail, e.g. S1E6)
        String badgeText = item.getEpisodeBadgeText();
        if (badgeText != null && !badgeText.isEmpty()) {
            holder.episodeBadge.setText(badgeText);
            holder.episodeBadge.setVisibility(View.VISIBLE);
        } else {
            holder.episodeBadge.setVisibility(View.GONE);
        }

        // Subtitle (only show if clean episode title is present, NEVER app name)
        String sub = item.getSubtitle();
        if (sub != null && !sub.isEmpty()) {
            holder.subtitle.setText(sub);
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.subtitle.setText("");
            holder.subtitle.setVisibility(View.INVISIBLE);
        }

        // Dynamic clean transparent app logo mark
        Drawable appIcon = getAppIcon(item.packageName);
        if (appIcon != null) {
            holder.sourceIcon.setImageDrawable(appIcon);
            holder.sourceIcon.setVisibility(View.VISIBLE);
        } else {
            holder.sourceIcon.setVisibility(View.GONE);
        }

        // Progress bar
        if (item.progressPercent > 0) {
            holder.progressTrack.setVisibility(View.VISIBLE);
            holder.progressFill.setVisibility(View.VISIBLE);
            holder.progressFill.post(() -> {
                int totalWidth = holder.card.getWidth();
                if (totalWidth <= 0) totalWidth = (int) ((isPortrait ? 110 : 180) * density);
                int trackWidth = Math.max(10, totalWidth - (int) (16 * density));
                int fillWidth = (trackWidth * item.progressPercent) / 100;
                ViewGroup.LayoutParams lp = holder.progressFill.getLayoutParams();
                lp.width = Math.max(fillWidth, (int) (4 * density));
                holder.progressFill.setLayoutParams(lp);
            });
        } else {
            holder.progressTrack.setVisibility(View.GONE);
            holder.progressFill.setVisibility(View.GONE);
        }

        // Load poster via FastPosterLoader with automatic aspect ratio callback
        FastPosterLoader.getInstance().load(item.posterArtUri, holder.poster, 0, isPortrait, (url, detectedPortrait) -> {
            if (!isPortrait && detectedPortrait && position == 0) {
                setPortrait(true);
            }
        });

        // Click on card to launch video or live channel stream
        holder.card.setOnClickListener(v -> {
            try {
                Intent intent = null;
                if (item.intentUriString != null && !item.intentUriString.isEmpty()) {
                    intent = Intent.parseUri(item.intentUriString, Intent.URI_INTENT_SCHEME);
                    if (item.packageName != null && !item.packageName.isEmpty()) {
                        intent.setPackage(item.packageName);
                    }
                }
                if (intent == null && item.packageName != null && !item.packageName.isEmpty()) {
                    intent = context.getPackageManager().getLaunchIntentForPackage(item.packageName);
                }
                if (intent == null) {
                    intent = context.getPackageManager().getLaunchIntentForPackage(TvRecommendationReader.NUVIO_PACKAGE);
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed launching intent: " + e.getMessage());
            }

            // Fallback: If no app can handle it, prompt and launch Play Store
            String targetPkg = item.packageName != null && !item.packageName.isEmpty() ? item.packageName : LiveTvChannelProvider.PKG_XEMTV;
            Toast.makeText(context, "Cần cài đặt ứng dụng", Toast.LENGTH_SHORT).show();
            try {
                Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + targetPkg));
                storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(storeIntent);
            } catch (Exception ex) {
                try {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + targetPkg));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(webIntent);
                } catch (Exception ignored) {}
            }
        });

        // Focus animation precisely matching CleanYourTV tile physics
        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            float s = hasFocus ? focusScale : 1.0f;
            v.animate().scaleX(s).scaleY(s)
                    .translationY(hasFocus && advancedEffects ? -4f * density : 0f)
                    .translationZ(hasFocus && advancedEffects ? 8f * density : 0f)
                    .setDuration(advancedEffects ? (hasFocus ? 220 : 170) : (hasFocus ? 260 : 180))
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
            if (hasFocus) {
                View parentItem = (View) v.getParent();
                if (parentItem != null && parentItem.getParent() instanceof RecyclerView) {
                    RecyclerView rv = (RecyclerView) parentItem.getParent();
                    int pad = (int) (6 * density);
                    rv.requestChildRectangleOnScreen(parentItem,
                            new Rect(0, -pad, parentItem.getWidth(), parentItem.getHeight() + pad), false);
                }
                if (focusListener != null) {
                    focusListener.onMovieFocused(item, v);
                }
                v.post(() -> {
                    if (v.isFocused() && focusListener != null) {
                        focusListener.onMovieFocused(item, v);
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView poster;
        final ImageView sourceIcon;
        final TextView episodeBadge;
        final View progressTrack;
        final View progressFill;
        final TextView title;
        final TextView subtitle;

        ViewHolder(View v, float density) {
            super(v);
            card = v.findViewById(R.id.card);
            poster = v.findViewById(R.id.moviePoster);
            sourceIcon = v.findViewById(R.id.sourceIcon);
            episodeBadge = v.findViewById(R.id.episodeBadge);
            progressTrack = v.findViewById(R.id.progressTrack);
            progressFill = v.findViewById(R.id.progressFill);
            title = v.findViewById(R.id.movieTitle);
            subtitle = v.findViewById(R.id.movieSubtitle);

            // Exact corner radius matching AppAdapter and GlassView
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float r = AppAdapter.cornerRadius(view.getWidth(), density);
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                }
            });
            card.setClipToOutline(true);
        }
    }
}
