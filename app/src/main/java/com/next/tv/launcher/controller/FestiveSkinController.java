package com.next.tv.launcher.controller;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.next.tv.launcher.BellDrawable;
import com.next.tv.launcher.DiyaDrawable;
import com.next.tv.launcher.Festival;
import com.next.tv.launcher.MarigoldDrawable;
import com.next.tv.launcher.PetalShowerView;

import java.time.LocalDate;

/**
 * Controller for seasonal festive skins (Diwali, Dussehra, etc.).
 * Manages animated pill drawables (Diya, Marigold, Bell) and petal shower animations.
 */
public final class FestiveSkinController {

    private final Activity activity;
    private Festival festival;
    private boolean festive;

    private BellDrawable bell;
    private MarigoldDrawable marigold;
    private DiyaDrawable diya;

    private static LocalDate lastPetalShower;

    public FestiveSkinController(Activity activity, Festival festival) {
        this.activity = activity;
        this.festival = festival != null ? festival : Festival.NONE;
        this.festive = this.festival.active(LocalDate.now());
    }

    public void updateFestival(Festival festival) {
        this.festival = festival != null ? festival : Festival.NONE;
        this.festive = this.festival.active(LocalDate.now());
    }

    public boolean isFestive() {
        return festive;
    }

    public Festival getFestival() {
        return festival;
    }

    public int getAccentColor() {
        return festival.accentColor();
    }

    public boolean checkDateChanged() {
        boolean activeNow = festival.active(LocalDate.now());
        if (activeNow != festive) {
            festive = activeNow;
            return true;
        }
        return false;
    }

    public void applyFestiveSkin(TextView inputs, TextView settings, TextView btnWifi, float density, boolean reducedMotion) {
        if (!festive) return;

        bell = new BellDrawable(density);
        marigold = new MarigoldDrawable(density);
        diya = new DiyaDrawable(density);

        bell.setReducedMotion(reducedMotion);
        marigold.setReducedMotion(reducedMotion);
        diya.setReducedMotion(reducedMotion);

        if (inputs != null) {
            setFestiveIcon(inputs, bell);
            addFocusAction(inputs, bell::ring);
        }
        if (btnWifi != null) {
            setFestiveIcon(btnWifi, diya);
        }
        if (settings != null) {
            setFestiveIcon(settings, marigold);
            addFocusAction(settings, marigold::turn);
        }
    }

    public void setReducedMotion(boolean enabled) {
        if (bell != null) bell.setReducedMotion(enabled);
        if (marigold != null) marigold.setReducedMotion(enabled);
        if (diya != null) diya.setReducedMotion(enabled);
    }

    public void startDiya() {
        if (festive && diya != null) {
            diya.start();
        }
    }

    public void stopDiya() {
        if (diya != null) {
            diya.stop();
        }
    }

    public void updateDiyaState(boolean steady, int flame) {
        if (festive && diya != null) {
            diya.setSteady(steady);
            diya.setFlame(flame);
        }
    }

    public void checkAndPlayDailyPetalShower(ViewGroup root, Handler handler, boolean reducedMotion) {
        if (!festive || reducedMotion || root == null || handler == null) return;
        LocalDate today = LocalDate.now();
        if (!today.equals(lastPetalShower)) {
            lastPetalShower = today;
            handler.postDelayed(() -> playPetalShower(root), 450);
        }
    }

    public void playPetalShower(ViewGroup root) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || root == null) return;
        PetalShowerView shower = new PetalShowerView(activity);
        root.addView(shower, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shower.play(60, 2200, () -> root.removeView(shower));
    }

    private static void setFestiveIcon(TextView pill, Drawable icon) {
        pill.setCompoundDrawableTintList(null);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    private static void addFocusAction(View view, Runnable action) {
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            if (focused) action.run();
        });
    }
}
