package com.next.tv.launcher.search;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public final class SearchResultItem {
    public static final int TYPE_MOVIE = 1;
    public static final int TYPE_YOUTUBE = 2;
    public static final int TYPE_WEB = 3;
    public static final int TYPE_SKELETON = 99;

    public static SearchResultItem createSkeleton() {
        return new SearchResultItem(TYPE_SKELETON, "", "", null, null, null, null, null);
    }

    public final int itemType;
    public final String title;
    public final String subtitle;
    public final String badge;
    public final Drawable iconDrawable;
    public final String iconUrl;
    public final Intent actionIntent;
    public final Runnable onClick;

    public SearchResultItem(int itemType, String title, String subtitle, String badge,
                            Drawable iconDrawable, String iconUrl,
                            Intent actionIntent, Runnable onClick) {
        this.itemType = itemType;
        this.title = title;
        this.subtitle = subtitle;
        this.badge = badge;
        this.iconDrawable = iconDrawable;
        this.iconUrl = iconUrl;
        this.actionIntent = actionIntent;
        this.onClick = onClick;
    }

    public SearchResultItem(String title, String subtitle, String badge,
                            Drawable iconDrawable, String iconUrl,
                            Intent actionIntent, Runnable onClick) {
        this(TYPE_MOVIE, title, subtitle, badge, iconDrawable, iconUrl, actionIntent, onClick);
    }

    public int getType() {
        return itemType;
    }
}
