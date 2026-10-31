package com.next.tv.launcher.controller;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.next.tv.launcher.apps.AppAdapter;
import com.next.tv.launcher.Config;
import com.next.tv.launcher.view.HeroArt;
import com.next.tv.launcher.apps.LastOpened;
import com.next.tv.launcher.view.Motion;
import com.next.tv.launcher.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the top hero shelf and ambient glow backdrops.
 */
public class HeroAmbientPresenter {

    private final Context context;
    private final View hero;
    private final TextView heroName;
    private final TextView heroMeta;
    private final LinearLayout heroDots;
    private final ImageView heroArt;
    private final View ambient;
    private final View heroScrim;

    private final boolean cinematic;
    private final boolean ambientGlow;
    private final String glowStyle;
    private final boolean heroDotsShown;
    private final float heroArtAlpha;
    private final boolean animations;
    private boolean reducedMotion;
    private final boolean advancedEffects;
    private final boolean rtlAppGrid;
    private final float density;
    private final int columns;

    private final Map<String, Bitmap> heroArtCache = new HashMap<>();
    private String heroArtKey;
    private int ambientColor = -1;
    private ValueAnimator ambientAnimator;
    private boolean heroShown;

    public HeroAmbientPresenter(Context context, View hero, TextView heroName, TextView heroMeta,
                                LinearLayout heroDots, ImageView heroArt, View ambient, View heroScrim,
                                Config cfg, float density, boolean animations, boolean reducedMotion,
                                boolean advancedEffects, boolean rtlAppGrid, int columns) {
        this.context = context;
        this.hero = hero;
        this.heroName = heroName;
        this.heroMeta = heroMeta;
        this.heroDots = heroDots;
        this.heroArt = heroArt;
        this.ambient = ambient;
        this.heroScrim = heroScrim;
        this.density = density;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        this.advancedEffects = advancedEffects;
        this.rtlAppGrid = rtlAppGrid;
        this.columns = columns;

        this.cinematic = cfg.cinematicHome;
        this.glowStyle = cfg.glowStyle;
        this.ambientGlow = cfg.ambientGlow;
        this.heroDotsShown = cfg.cinematicDots;
        this.heroArtAlpha = cfg.cinematicArt / 100f;

        if (this.ambient != null) {
            this.ambient.forceHasOverlappingRendering(false);
            if (!this.ambientGlow) this.ambient.setVisibility(View.GONE);
        }
        if (this.hero != null) {
            this.hero.forceHasOverlappingRendering(false);
            this.hero.setVisibility(View.GONE);
        }
        if (this.cinematic) {
            setUpCinematicShelf(cfg.cinematicScrim);
        }
    }

    public void setUpCinematicShelf(int scrimPercent) {
        if (heroName != null) {
            heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f);
            heroName.setLetterSpacing(-0.025f);
        }
        if (heroMeta != null) heroMeta.setVisibility(View.INVISIBLE);
        if (heroDots != null) heroDots.setVisibility(heroDotsShown ? View.VISIBLE : View.GONE);
        if (heroArt != null) {
            heroArt.setVisibility(heroArtAlpha > 0f ? View.VISIBLE : View.GONE);
            heroArt.forceHasOverlappingRendering(false);
        }
        if (scrimPercent <= 0 || heroScrim == null) return;
        Drawable shade = scrimDrawable();
        shade.setAlpha(scrimPercent * 255 / 100);
        heroScrim.setBackground(shade);
        heroScrim.setVisibility(View.VISIBLE);
    }

    public Drawable scrimDrawable() {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        boolean rtl = context.getResources().getBoolean(R.bool.rtl_menu_bar);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(rtl ? w : 0, 0, rtl ? 0 : w, 0,
                new int[]{0xD1060406, 0x59060406, 0x00060406}, new float[]{0f, 0.42f, 0.7f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x00060406, 0x00060406, 0xDB060406, 0xFF060406}, new float[]{0f, 0.28f, 0.58f, 0.76f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable scrim = new BitmapDrawable(context.getResources(), bmp);
        scrim.setGravity(Gravity.FILL);
        return scrim;
    }

    public void showHero(int position, float dockTopInRoot, AppAdapter adapter) {
        if (hero == null || hero.getVisibility() == View.GONE || adapter == null) return;
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - hero.getHeight();
        boolean changed = heroName != null && !label.contentEquals(heroName.getText());
        if (cinematic && heroArtAlpha > 0f) showHeroArt(position, adapter);

        if (!animations || reducedMotion) {
            hero.animate().cancel();
            if (ambient != null) ambient.animate().cancel();
            fillHero(position, label, adapter);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.setAlpha(1f);
            if (ambientGlow && ambient != null) {
                setAmbient(color);
                ambient.setAlpha(1f);
            }
            heroShown = true;
            return;
        }

        if (!heroShown) {
            stopAmbientAnimation();
            hero.animate().cancel();
            if (ambient != null) ambient.animate().cancel();
            fillHero(position, label, adapter);
            hero.setY(y);
            hero.setTranslationX(0f);
            hero.animate().alpha(1f).setDuration(260).start();
            if (ambientGlow && ambient != null) {
                setAmbient(color);
                ambient.animate().alpha(1f).setDuration(600).start();
            }
            heroShown = true;
        } else if (changed) {
            if (advancedEffects) hero.animate().cancel();
            hero.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                fillHero(position, label, adapter);
                hero.setY(y);
                hero.setTranslationX(12 * density);
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (ambientGlow && advancedEffects) {
                transitionAmbient(color);
            } else if (ambientGlow && ambient != null && color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            hero.setY(y);
        }
    }

    public void fillHero(int position, String label, AppAdapter adapter) {
        if (heroName != null) heroName.setText(label);
        if (!cinematic) return;
        updateHeroMeta(position, adapter);
        updateHeroDots(position, adapter);
    }

    public void showHeroArt(int position, AppAdapter adapter) {
        if (heroArt == null || adapter == null) return;
        String key = adapter.keyAt(position);
        if (key == null) return;
        heroArt.animate().cancel();
        if (key.equals(heroArtKey)) {
            fadeHeroArt(420);
            return;
        }
        Bitmap art = heroArtCache.get(key);
        if (art == null) {
            art = HeroArt.from(adapter.artworkAt(position));
            if (art != null) heroArtCache.put(key, art);
        }
        heroArtKey = key;
        final Bitmap next = art;
        if (!animations || reducedMotion || heroArt.getAlpha() == 0f) {
            heroArt.setImageBitmap(next);
            fadeHeroArt(600);
            return;
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            heroArt.setImageBitmap(next);
            heroArt.animate().alpha(heroArtAlpha).setDuration(420).start();
        }).start();
    }

    public void fadeHeroArt(int ms) {
        if (heroArt == null) return;
        if (!animations) heroArt.setAlpha(heroArtAlpha);
        else heroArt.animate().alpha(heroArtAlpha).setDuration(reducedMotion ? Math.min(ms, Motion.REDUCED_MS) : ms).start();
    }

    public void updateHeroMeta(int position, AppAdapter adapter) {
        if (heroMeta == null || adapter == null) return;
        long when = LastOpened.get(context, adapter.packageAt(position));
        if (when <= 0) {
            heroMeta.setVisibility(View.INVISIBLE);
            return;
        }
        String rel = LastOpened.describe(context, when);
        String text = context.getString(R.string.last_opened, rel);
        SpannableString line = new SpannableString(text);
        int at = text.indexOf(rel);
        if (at >= 0) line.setSpan(new ForegroundColorSpan(0xFFFFFFFF), at, at + rel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroMeta.setText(line);
        heroMeta.setVisibility(View.VISIBLE);
    }

    public void updateHeroDots(int position, AppAdapter adapter) {
        if (!heroDotsShown || heroDots == null || adapter == null) return;
        int count = Math.min(adapter.dockFilled(), columns);
        heroDots.setVisibility(count > 1 ? View.VISIBLE : View.INVISIBLE);
        if (heroDots.getChildCount() != count) {
            heroDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(context);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(3 * density);
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
                if (i > 0) lp.leftMargin = (int) (6 * density);
                heroDots.addView(dot, lp);
            }
        }
        for (int i = 0; i < count; i++) {
            View dot = heroDots.getChildAt(rtlAppGrid ? count - 1 - i : i);
            boolean on = i == position;
            ((GradientDrawable) dot.getBackground()).setColor(on ? 0xFFFFFFFF : 0x4DFFFFFF);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            int w = (int) ((on ? 20 : 6) * density);
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
    }

    public void setAmbient(int color) {
        if (ambient == null || (color == ambientColor && ambient.getBackground() != null)) return;
        ambientColor = color;
        BitmapDrawable glow = (BitmapDrawable) ambient.getBackground();
        if (glow == null) {
            int w = 480;
            int h = 270;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (cinematic) {
                boolean rtl = context.getResources().getBoolean(R.bool.rtl_menu_bar);
                paint.setShader(new RadialGradient(
                        w * (rtl ? 0.20f : 0.80f), h * 0.06f, w * 0.78f,
                        new int[]{0xA6FFFFFF, 0x47FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
            } else if (Config.GLOW_HORIZON.equals(glowStyle)) {
                paint.setShader(new LinearGradient(0, 0, 0, h * 0.75f,
                        new int[]{0x66FFFFFF, 0x24FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.4f, 1f},
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new RadialGradient(
                        w * 0.5f, -h * 0.25f, w * 0.55f,
                        new int[]{0x40FFFFFF, 0x00FFFFFF},
                        null,
                        Shader.TileMode.CLAMP));
            } else {
                paint.setShader(new RadialGradient(
                        w * 0.32f, h * 0.42f, w * 0.62f,
                        new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, w, h, paint);
            glow = new BitmapDrawable(context.getResources(), bmp);
            glow.setGravity(Gravity.FILL);
            ambient.setBackground(glow);
        }
        glow.setTint(0xFF000000 | color);
    }

    public void transitionAmbient(int color) {
        if (ambient == null) return;
        stopAmbientAnimation();
        ambient.animate().cancel();
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start();
            return;
        }
        ambient.animate().alpha(1f).setDuration(180).start();
        ambientAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), ambientColor, color);
        ambientAnimator.setDuration(360);
        ambientAnimator.addUpdateListener(animation -> setAmbient((int) animation.getAnimatedValue()));
        ambientAnimator.start();
    }

    public void stopAmbientAnimation() {
        if (ambientAnimator != null) {
            ambientAnimator.cancel();
            ambientAnimator = null;
        }
    }

    public void showGlowOnly(int position, AppAdapter adapter) {
        if (adapter == null) return;
        int color = adapter.ambientColorAt(position);
        stopAmbientAnimation();
        if (hero != null) {
            hero.animate().cancel();
            hero.setAlpha(0f);
        }
        if (ambient != null) ambient.animate().cancel();
        if (heroArt != null) {
            heroArt.animate().cancel();
            heroArt.setAlpha(0f);
        }
        heroShown = false;
        if (!ambientGlow || ambient == null) return;
        if (!animations || reducedMotion) {
            setAmbient(color);
            ambient.setAlpha(1f);
            return;
        }
        if (color == ambientColor && ambient.getBackground() != null) {
            ambient.animate().alpha(1f).setDuration(300).start();
            return;
        }
        ambient.animate().alpha(0.35f).setDuration(ambient.getAlpha() > 0.35f ? 200 : 0).withEndAction(() -> {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(500).start();
        }).start();
    }

    public void hideHero() {
        if (!heroShown) return;
        stopAmbientAnimation();
        if (hero != null) hero.animate().cancel();
        if (ambient != null) ambient.animate().cancel();
        if (heroArt != null) heroArt.animate().cancel();
        heroShown = false;
        if (animations && !reducedMotion) {
            if (hero != null) hero.animate().alpha(0f).setDuration(160).start();
            if (ambientGlow && ambient != null) ambient.animate().alpha(0f).setDuration(300).start();
            if (heroArt != null) heroArt.animate().alpha(0f).setDuration(300).start();
        } else {
            if (hero != null) hero.setAlpha(0f);
            if (ambient != null) ambient.setAlpha(0f);
            if (heroArt != null) heroArt.setAlpha(0f);
        }
    }

    public void cancelAnimations() {
        stopAmbientAnimation();
        if (hero != null) hero.animate().cancel();
        if (ambient != null) ambient.animate().cancel();
        if (heroArt != null) heroArt.animate().cancel();
    }

    public void resetVisibilityOnStart() {
        heroShown = false;
        if (hero != null) hero.setAlpha(0f);
        if (ambient != null) ambient.setAlpha(0f);
        if (heroArt != null) {
            heroArt.animate().cancel();
            heroArt.setAlpha(0f);
        }
    }

    public void clearCache() {
        heroArtCache.clear();
        heroArtKey = null;
        stopAmbientAnimation();
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    public boolean isHeroShown() {
        return heroShown;
    }
}
