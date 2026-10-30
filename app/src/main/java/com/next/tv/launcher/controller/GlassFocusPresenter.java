package com.next.tv.launcher.controller;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.AppAdapter;
import com.next.tv.launcher.Config;
import com.next.tv.launcher.Festival;
import com.next.tv.launcher.GlassView;
import com.next.tv.launcher.Motion;
import com.next.tv.launcher.R;

/**
 * Manages the floating glass focus indicator and its spring physics.
 * Single source of truth for focus frame position, dimensions and accent coloring.
 */
public class GlassFocusPresenter {

    public static final int REORDER_ACCENT = 0xFF6EC1FF;

    private final ViewGroup root;
    private final GlassView glass;
    private final float density;
    private final boolean animations;
    private boolean reducedMotion;
    private final boolean advancedEffects;
    private final boolean appGlow;
    private final boolean cinematic;
    private final boolean cinematicWhiteFocus;
    private final int columns;

    private SpringAnimation springX;
    private SpringAnimation springY;
    private float glassTargetX;
    private float glassTargetY;
    private int glassCardW;
    private boolean glassShown;
    private int glassAccent;

    private boolean reordering;
    private boolean festive;
    private Festival festival = Festival.NONE;

    private final Rect tileRect = new Rect();

    public GlassFocusPresenter(ViewGroup root, GlassView glass, Config cfg, float density,
                               boolean animations, boolean reducedMotion, boolean advancedEffects,
                               int columns) {
        this.root = root;
        this.glass = glass;
        this.density = density;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        this.advancedEffects = advancedEffects;
        this.columns = columns;

        this.appGlow = cfg.appGlow;
        this.cinematic = cfg.cinematicHome;
        this.cinematicWhiteFocus = cfg.cinematicWhiteFocus;

        setupSprings();
    }

    private void setupSprings() {
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        updateSpringPhysics();
    }

    public void updateSpringPhysics() {
        if (springX == null || springY == null) return;
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                Motion.reduce(s);
            } else {
                s.setSpring(new SpringForce().setStiffness(advancedEffects ? 500f : 440f).setDampingRatio(1f));
            }
        }
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        updateSpringPhysics();
    }

    /**
     * Single Source of Truth for accent color calculation.
     * Priority: Reorder > Festival > Ambient/Cinematic > White (0)
     */
    public int resolveAccent(int position, AppAdapter adapter) {
        if (reordering) return REORDER_ACCENT;
        if (festive && festival != null) return festival.accentColor();
        boolean coloured = (appGlow || (cinematic && !cinematicWhiteFocus && position >= 0 && position < columns)) && !festive;
        return (coloured && adapter != null && position >= 0) ? adapter.ambientColorAt(position) : 0;
    }

    public void setReorderMode(boolean active, AppAdapter adapter, int currentPos) {
        this.reordering = active;
        int accent = resolveAccent(currentPos, adapter);
        if (accent != glassAccent) {
            glassAccent = accent;
            glass.setAccent(accent);
        }
    }

    public void setFestive(boolean festive, Festival festival) {
        this.festive = festive;
        this.festival = festival != null ? festival : Festival.NONE;
        if (festive && !reordering && festival != null) {
            glass.setAccent(festival.accentColor());
            glassAccent = festival.accentColor();
        }
    }

    public boolean isDescendantOf(View parent, View child) {
        if (parent == null || child == null) return false;
        ViewParent p = child.getParent();
        while (p != null) {
            if (p == parent) return true;
            p = p.getParent();
        }
        return false;
    }

    public void syncGlassWithFocusedView(View focused, boolean animate, AppAdapter adapter,
                                         RecyclerView grid, ScrollView mainScroll, View clockBlock) {
        syncGlassWithTarget(focused, animate, adapter, grid, mainScroll, clockBlock);
    }

    public void syncGlassWithTarget(View focused, boolean animate, AppAdapter adapter,
                                    RecyclerView grid, ScrollView mainScroll, View clockBlock) {
        if (glass == null || focused == null) return;
        int id = focused.getId();
        boolean isCard = (id == R.id.card || id == R.id.wallpaperCard);
        boolean isPill = (id == R.id.btnMoveSection || id == R.id.btnToggleSection);
        if (!isCard && !isPill) {
            if (glassShown) {
                glass.animate().alpha(0f).setDuration(120).start();
                glassShown = false;
            }
            return;
        }

        if (!focused.isShown() || focused.getWidth() == 0 || focused.getHeight() == 0) {
            View f = focused;
            focused.post(() -> syncGlassWithTarget(f, animate, adapter, grid, mainScroll, clockBlock));
            return;
        }

        if (!isDescendantOf(root, focused)) return;
        Rect r = tileRect;
        r.set(0, 0, focused.getWidth(), focused.getHeight());
        root.offsetDescendantRectToMyCoords(focused, r);

        if (mainScroll != null && isDescendantOf(mainScroll, focused)) {
            int headerBottom = (clockBlock != null && clockBlock.getVisibility() == View.VISIBLE) ? clockBlock.getBottom() : mainScroll.getTop();
            if (r.bottom <= headerBottom || r.top >= root.getHeight()) {
                if (glassShown) {
                    glass.animate().alpha(0f).setDuration(120).start();
                    glassShown = false;
                }
                return;
            }
        }

        float pad = 6f * density;
        float scale = adapter != null ? adapter.focusScale() : 1f;
        float w = focused.getWidth() * scale + 2 * pad;
        float h = focused.getHeight() * scale + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float targetTransY = (advancedEffects && isCard) ? -4f * density : 0f;
        float currentTransY = focused.getTranslationY();
        float y = r.exactCenterY() - h / 2f + (targetTransY - currentTransY);
        Log.e("TvHome.Glass", "syncGlassWithTarget: focused=" + (focused.getClass().getSimpleName() + " id=" + (focused.getId() > 0 ? root.getResources().getResourceEntryName(focused.getId()) : focused.getId()))
                + " w=" + focused.getWidth() + " h=" + focused.getHeight()
                + " r=" + r + " x=" + x + " y=" + y + " shown=" + glassShown);
        float radius = isPill ? (focused.getHeight() / 2f * scale) : (AppAdapter.cornerRadius(focused.getWidth(), density) * scale);

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h || focused.getWidth() != glassCardW) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
        }
        glass.setRadius(radius);
        glassCardW = focused.getWidth();
        glassTargetX = x;
        glassTargetY = y;
        View containing = (grid != null) ? grid.findContainingItemView(focused) : null;
        int gridPos = containing != null ? grid.getChildAdapterPosition(containing) : -1;
        int accent = resolveAccent(gridPos, adapter);
        if (accent != glassAccent) {
            glassAccent = accent;
            glass.setAccent(accent);
        }

        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (animations && !reducedMotion) glass.animate().alpha(1f).setDuration(180).start();
            else glass.setAlpha(1f);
            glassShown = true;
        } else if (!animate || !animations) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
    }

    public void focusGlassOnCard(View card, AppAdapter adapter, RecyclerView grid, ScrollView mainScroll, View clockBlock) {
        if (card == null || glass == null) return;
        syncGlassWithTarget(card, true, adapter, grid, mainScroll, clockBlock);
    }

    public void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().cancel();
        if (animations && !reducedMotion) glass.animate().alpha(0f).setDuration(160).start();
        else glass.setAlpha(0f);
    }

    public void resetVisibilityOnStart() {
        glassShown = false;
        if (springX != null) springX.cancel();
        if (springY != null) springY.cancel();
        if (glass != null) {
            glass.animate().cancel();
            glass.setAlpha(0f);
        }
    }

    public void onScrollChanged(View focused, ScrollView mainScroll, View clockBlock) {
        if (!glassShown || focused == null) return;
        int id = focused.getId();
        if (id != R.id.card && id != R.id.wallpaperCard && id != R.id.btnMoveSection && id != R.id.btnToggleSection) {
            if (glassShown) {
                glass.animate().alpha(0f).setDuration(120).start();
                glassShown = false;
            }
            return;
        }
        Rect r = tileRect;
        r.set(0, 0, focused.getWidth(), focused.getHeight());
        root.offsetDescendantRectToMyCoords(focused, r);

        int headerBottom = (clockBlock != null && clockBlock.getVisibility() == View.VISIBLE) ? clockBlock.getBottom() : (mainScroll != null ? mainScroll.getTop() : 0);
        if (r.bottom <= headerBottom || r.top >= root.getHeight()) {
            if (glassShown) {
                glass.animate().alpha(0f).setDuration(120).start();
                glassShown = false;
            }
            return;
        }

        float h = glass.getHeight();
        boolean isCard = (id == R.id.card || id == R.id.wallpaperCard);
        float targetTransY = (advancedEffects && isCard) ? -4f * density : 0f;
        float currentTransY = focused.getTranslationY();
        float targetY = r.exactCenterY() - h / 2f + (targetTransY - currentTransY);
        glassTargetY = targetY;
        glass.setTranslationY(targetY);
        if (springY.isRunning()) {
            springY.animateToFinalPosition(targetY);
        }
        float targetX = r.exactCenterX() - glass.getWidth() / 2f;
        if (Math.abs(glass.getTranslationX() - targetX) > 2f) {
            glass.setTranslationX(targetX);
            glassTargetX = targetX;
            if (springX.isRunning()) {
                springX.animateToFinalPosition(targetX);
            }
        }
    }

    public Rect alignGlassToCard(int position, View card, AppAdapter adapter, ScrollView mainScroll, View clockBlock) {
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);

        if (mainScroll != null && isDescendantOf(mainScroll, card)) {
            int headerBottom = (clockBlock != null && clockBlock.getVisibility() == View.VISIBLE) ? clockBlock.getBottom() : mainScroll.getTop();
            if (r.bottom <= headerBottom || r.top >= root.getHeight()) {
                if (glassShown) {
                    glass.animate().alpha(0f).setDuration(120).start();
                    glassShown = false;
                }
                return null;
            }
        }

        float pad = 6f * density;
        float scale = adapter.focusScale();
        float w = card.getWidth() * scale + 2 * pad;
        float h = card.getHeight() * scale + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float targetTransY = advancedEffects ? -4f * density : 0f;
        float currentTransY = card.getTranslationY();
        float y = r.exactCenterY() - h / 2f + (targetTransY - currentTransY);
        Log.e("TvHome.Glass", "onTileFocused: pos=" + position + " w=" + card.getWidth() + " h=" + card.getHeight()
                + " r=" + r + " x=" + x + " y=" + y + " shown=" + glassShown);

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h || card.getWidth() != glassCardW) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * scale);
        }
        glassCardW = card.getWidth();
        glassTargetX = x;
        glassTargetY = y;

        int accent = resolveAccent(position, adapter);
        if (accent != glassAccent) {
            glassAccent = accent;
            glass.setAccent(accent);
        }

        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (animations && !reducedMotion) glass.animate().alpha(1f).setDuration(180).start();
            else glass.setAlpha(1f);
            glassShown = true;
        } else if (!animations) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        return r;
    }

    public boolean shouldRefit(int cardWidth, int cardHeight, float scale, float pad) {
        int w = (int) (cardWidth * scale + 2 * pad);
        int h = (int) (cardHeight * scale + 2 * pad);
        return (!glassShown || cardWidth != glassCardW || glass.getWidth() != w || glass.getHeight() != h);
    }

    public void cancelAnimations() {
        if (springX != null) springX.cancel();
        if (springY != null) springY.cancel();
        if (glass != null) glass.animate().cancel();
    }

    public void onScrolled(int dx, int dy) {
        if (!glassShown) return;
        glassTargetX -= dx;
        if (glass != null) glass.setTranslationX(glass.getTranslationX() - dx);
    }

    public boolean isGlassShown() {
        return glassShown;
    }

    public float getGlassTargetX() {
        return glassTargetX;
    }

    public float getGlassTargetY() {
        return glassTargetY;
    }

    public int getGlassCardW() {
        return glassCardW;
    }

    public int getGlassAccent() {
        return glassAccent;
    }
}
