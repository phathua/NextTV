package com.next.tv.launcher.controller;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;

import com.next.tv.launcher.rec.TvRecommendationReader;

import java.util.List;

/**
 * Presenter for querying TV recommendations / Leanback channels in the background.
 * Uses the shared BackgroundTaskExecutor to run IO without creating extra HandlerThreads.
 */
public final class TvRecommendationPresenter {

    public interface OnRecommendationsLoadedListener {
        void onRecommendationsLoaded(List<TvRecommendationReader.MovieSection> sections);
    }

    private final Context context;
    private final BackgroundTaskExecutor bgExecutor;
    private final Handler mainHandler;
    private final OnRecommendationsLoadedListener listener;

    private ContentObserver tvContentObserver;
    private int recRetryCount = 0;

    private final Runnable loadRecsDebouncedRunnable = this::loadRecommendations;
    private final Runnable recRetryRunnable = this::loadRecommendations;

    public TvRecommendationPresenter(Context context,
                                     BackgroundTaskExecutor bgExecutor,
                                     Handler mainHandler,
                                     OnRecommendationsLoadedListener listener) {
        this.context = context.getApplicationContext();
        this.bgExecutor = bgExecutor;
        this.mainHandler = mainHandler;
        this.listener = listener;
    }

    public void scheduleRecommendationRetries() {
        mainHandler.removeCallbacks(recRetryRunnable);
        recRetryCount = 0;
        mainHandler.postDelayed(recRetryRunnable, 1500);
    }

    public void registerTvObserver() {
        if (tvContentObserver == null) {
            tvContentObserver = new ContentObserver(mainHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    mainHandler.removeCallbacks(loadRecsDebouncedRunnable);
                    mainHandler.postDelayed(loadRecsDebouncedRunnable, 1500);
                }
            };
            try {
                context.getContentResolver().registerContentObserver(
                        TvRecommendationReader.Contract.WATCH_NEXT_CONTENT_URI, true, tvContentObserver);
                context.getContentResolver().registerContentObserver(
                        TvRecommendationReader.Contract.CHANNELS_CONTENT_URI, true, tvContentObserver);
                context.getContentResolver().registerContentObserver(
                        TvRecommendationReader.Contract.PREVIEW_PROGRAMS_CONTENT_URI, true, tvContentObserver);
            } catch (Exception ignored) {
            }
        }
    }

    public void unregisterTvObserver() {
        if (tvContentObserver != null) {
            mainHandler.removeCallbacks(loadRecsDebouncedRunnable);
            mainHandler.removeCallbacks(recRetryRunnable);
            try {
                context.getContentResolver().unregisterContentObserver(tvContentObserver);
            } catch (Exception ignored) {
            }
            tvContentObserver = null;
        }
    }

    public void loadRecommendations() {
        if (bgExecutor == null) return;
        bgExecutor.post(() -> {
            List<TvRecommendationReader.MovieSection> sections =
                    TvRecommendationReader.queryMovieSections(context);
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onRecommendationsLoaded(sections);
                }
                if ((sections == null || sections.size() <= 1) && recRetryCount < 3) {
                    recRetryCount++;
                    mainHandler.removeCallbacks(recRetryRunnable);
                    mainHandler.postDelayed(recRetryRunnable, recRetryCount == 1 ? 1500 : 3500);
                } else if (sections != null && sections.size() > 1) {
                    recRetryCount = 0;
                }
            });
        });
    }
}
