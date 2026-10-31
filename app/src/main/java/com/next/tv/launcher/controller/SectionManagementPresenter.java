package com.next.tv.launcher.controller;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.R;
import com.next.tv.launcher.rec.SectionConfig;
import com.next.tv.launcher.rec.TvRecommendationReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presenter for Section Management mode (editing visibility, reordering vertical sections).
 */
public final class SectionManagementPresenter {

    public interface Host {
        void focusGlassOnCard(View view);
        void scrollSectionFullyIntoView(View view);
        void onExitToGrid();
    }

    private final Context context;
    private final LinearLayout movieSectionsContainer;
    private final SectionConfig sectionConfig;
    private final Host host;
    private final float density;

    private boolean isSectionManagementMode;
    private View reorderingSectionView;

    public SectionManagementPresenter(Context context,
                                      LinearLayout movieSectionsContainer,
                                      SectionConfig sectionConfig,
                                      Host host,
                                      float density) {
        this.context = context;
        this.movieSectionsContainer = movieSectionsContainer;
        this.sectionConfig = sectionConfig;
        this.host = host;
        this.density = density;
    }

    public boolean isSectionManagementMode() {
        return isSectionManagementMode;
    }

    public View getReorderingSectionView() {
        return reorderingSectionView;
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!isSectionManagementMode) return false;
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) {
                if (reorderingSectionView != null) {
                    finishReorderingSection();
                } else {
                    exitSectionManagement(null);
                }
            }
            return true;
        }
        return false;
    }

    public void enterSectionManagement(View triggeredSection) {
        if (isSectionManagementMode || movieSectionsContainer == null) return;
        isSectionManagementMode = true;

        for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
            View child = movieSectionsContainer.getChildAt(i);
            TvRecommendationReader.MovieSection sec = (TvRecommendationReader.MovieSection) child.getTag();
            if (sec == null) continue;

            View leftActions = child.findViewById(R.id.leftActionsPanel);
            View content = child.findViewById(R.id.sectionContent);
            TextView titleView = child.findViewById(R.id.sectionTitle);
            TextView btnToggle = child.findViewById(R.id.btnToggleSection);
            TextView btnMove = child.findViewById(R.id.btnMoveSection);

            child.setVisibility(View.VISIBLE);
            boolean enabled = sectionConfig.isSectionEnabled(sec.id);
            if (enabled) {
                child.setAlpha(1.0f);
                if (titleView != null) titleView.setText(sec.title);
                if (btnToggle != null) btnToggle.setText("Ẩn");
            } else {
                child.setAlpha(0.35f);
                if (titleView != null) titleView.setText(sec.title + " [ĐÃ ẨN]");
                if (btnToggle != null) btnToggle.setText("Hiện");
            }

            if (child == triggeredSection) {
                if (btnMove != null) {
                    btnMove.setFocusable(true);
                    btnMove.setFocusableInTouchMode(true);
                    btnMove.setText("Di chuyển");
                }
                if (btnToggle != null) {
                    btnToggle.setFocusable(true);
                    btnToggle.setFocusableInTouchMode(true);
                }
                if (leftActions != null) leftActions.setVisibility(View.VISIBLE);
                if (content != null) {
                    content.animate().translationX(140f * density).setDuration(180).start();
                }
            } else {
                if (btnMove != null) {
                    btnMove.setFocusable(false);
                    btnMove.setFocusableInTouchMode(false);
                }
                if (btnToggle != null) {
                    btnToggle.setFocusable(false);
                    btnToggle.setFocusableInTouchMode(false);
                }
                if (leftActions != null) leftActions.setVisibility(View.GONE);
                if (content != null) {
                    content.setTranslationX(0f);
                }
            }
        }

        if (triggeredSection != null) {
            View rv = triggeredSection.findViewById(R.id.sectionRecyclerView);
            if (rv != null) {
                View cur = rv.findFocus();
                if (cur != null) cur.clearFocus();
            }
            View btnMove = triggeredSection.findViewById(R.id.btnMoveSection);
            if (btnMove != null) {
                btnMove.setFocusable(true);
                btnMove.setFocusableInTouchMode(true);
                btnMove.requestFocus();
                btnMove.post(() -> {
                    btnMove.requestFocus();
                    if (host != null) host.focusGlassOnCard(btnMove);
                });
            }
            if (host != null) host.scrollSectionFullyIntoView(triggeredSection);
        }
    }

    public void switchManagementSection(View fromSec, View toSec, boolean focusMoveBtn) {
        if (fromSec != null && fromSec != toSec) {
            View fromActions = fromSec.findViewById(R.id.leftActionsPanel);
            View fromContent = fromSec.findViewById(R.id.sectionContent);
            View fromMove = fromSec.findViewById(R.id.btnMoveSection);
            View fromToggle = fromSec.findViewById(R.id.btnToggleSection);
            if (fromMove != null) {
                fromMove.setFocusable(false);
                fromMove.setFocusableInTouchMode(false);
            }
            if (fromToggle != null) {
                fromToggle.setFocusable(false);
                fromToggle.setFocusableInTouchMode(false);
            }
            if (fromActions != null) fromActions.setVisibility(View.GONE);
            if (fromContent != null) fromContent.animate().translationX(0f).setDuration(160).start();
        }
        if (toSec != null) {
            View toActions = toSec.findViewById(R.id.leftActionsPanel);
            View toContent = toSec.findViewById(R.id.sectionContent);
            View toMove = toSec.findViewById(R.id.btnMoveSection);
            View toToggle = toSec.findViewById(R.id.btnToggleSection);
            if (toMove != null) {
                toMove.setFocusable(true);
                toMove.setFocusableInTouchMode(true);
            }
            if (toToggle != null) {
                toToggle.setFocusable(true);
                toToggle.setFocusableInTouchMode(true);
            }
            if (toActions != null) toActions.setVisibility(View.VISIBLE);
            if (toContent != null) toContent.animate().translationX(140f * density).setDuration(160).start();
            View targetBtn = toSec.findViewById(focusMoveBtn ? R.id.btnMoveSection : R.id.btnToggleSection);
            if (targetBtn != null) {
                targetBtn.requestFocus();
                if (host != null) host.focusGlassOnCard(targetBtn);
            }
            if (host != null) host.scrollSectionFullyIntoView(toSec);
        }
    }

    public void exitSectionManagement(View targetSection) {
        if (!isSectionManagementMode || movieSectionsContainer == null) return;
        finishReorderingSection();
        isSectionManagementMode = false;

        for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
            View child = movieSectionsContainer.getChildAt(i);
            TvRecommendationReader.MovieSection sec = (TvRecommendationReader.MovieSection) child.getTag();
            if (sec == null) continue;

            View leftActions = child.findViewById(R.id.leftActionsPanel);
            View content = child.findViewById(R.id.sectionContent);
            TextView titleView = child.findViewById(R.id.sectionTitle);
            View btnMove = child.findViewById(R.id.btnMoveSection);
            View btnToggle = child.findViewById(R.id.btnToggleSection);

            if (btnMove != null) {
                btnMove.setFocusable(false);
                btnMove.setFocusableInTouchMode(false);
            }
            if (btnToggle != null) {
                btnToggle.setFocusable(false);
                btnToggle.setFocusableInTouchMode(false);
            }
            if (leftActions != null) leftActions.setVisibility(View.GONE);
            if (content != null) {
                content.animate().translationX(0f).setDuration(180).start();
            }
            if (titleView != null) {
                titleView.setText(sec.title);
            }

            boolean enabled = sectionConfig.isSectionEnabled(sec.id);
            if (enabled) {
                child.setVisibility(View.VISIBLE);
                child.setAlpha(1.0f);
            } else {
                child.setVisibility(View.GONE);
            }
        }

        if (targetSection != null && targetSection.getVisibility() == View.VISIBLE) {
            if (host != null) host.scrollSectionFullyIntoView(targetSection);
            androidx.recyclerview.widget.RecyclerView rv = targetSection.findViewById(R.id.sectionRecyclerView);
            if (rv != null) {
                if (rv.getChildCount() > 0) {
                    View card = rv.getChildAt(0).findViewById(R.id.card);
                    if (card != null) card.requestFocus();
                    else rv.getChildAt(0).requestFocus();
                } else rv.requestFocus();
            }
        } else {
            boolean focused = false;
            for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
                View child = movieSectionsContainer.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    androidx.recyclerview.widget.RecyclerView rv = child.findViewById(R.id.sectionRecyclerView);
                    if (rv != null && rv.getChildCount() > 0) {
                        View card = rv.getChildAt(0).findViewById(R.id.card);
                        if (card != null) {
                            card.requestFocus();
                            focused = true;
                            break;
                        }
                    }
                }
            }
            if (!focused && host != null) {
                host.onExitToGrid();
            }
        }
    }

    public void startReorderingSection(View secView, TextView btnMove) {
        reorderingSectionView = secView;
        if (btnMove != null) {
            btnMove.setText("Đang đổi");
        }
    }

    public void finishReorderingSection() {
        if (reorderingSectionView != null) {
            TextView btnMove = reorderingSectionView.findViewById(R.id.btnMoveSection);
            if (btnMove != null) btnMove.setText("Di chuyển");
            reorderingSectionView = null;
            saveSectionOrderFromContainer();
        }
    }

    public void moveSectionUp(View secView) {
        if (movieSectionsContainer == null || secView == null) return;
        int idx = movieSectionsContainer.indexOfChild(secView);
        if (idx > 0) {
            movieSectionsContainer.removeView(secView);
            movieSectionsContainer.addView(secView, idx - 1);
            secView.requestFocus();
            View btnMove = secView.findViewById(R.id.btnMoveSection);
            if (btnMove != null) {
                btnMove.requestFocus();
                if (host != null) host.focusGlassOnCard(btnMove);
            }
            if (host != null) host.scrollSectionFullyIntoView(secView);
            saveSectionOrderFromContainer();
        }
    }

    public void moveSectionDown(View secView) {
        if (movieSectionsContainer == null || secView == null) return;
        int idx = movieSectionsContainer.indexOfChild(secView);
        if (idx < movieSectionsContainer.getChildCount() - 1) {
            movieSectionsContainer.removeView(secView);
            movieSectionsContainer.addView(secView, idx + 1);
            secView.requestFocus();
            View btnMove = secView.findViewById(R.id.btnMoveSection);
            if (btnMove != null) {
                btnMove.requestFocus();
                if (host != null) host.focusGlassOnCard(btnMove);
            }
            if (host != null) host.scrollSectionFullyIntoView(secView);
            saveSectionOrderFromContainer();
        }
    }

    public void saveSectionOrderFromContainer() {
        if (movieSectionsContainer == null || sectionConfig == null) return;
        List<String> order = new ArrayList<>();
        for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
            View child = movieSectionsContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof TvRecommendationReader.MovieSection) {
                order.add(((TvRecommendationReader.MovieSection) tag).id);
            }
        }
        sectionConfig.saveSectionOrder(order);
    }

    public List<TvRecommendationReader.MovieSection> getMovieSections() {
        List<TvRecommendationReader.MovieSection> list = new ArrayList<>();
        if (movieSectionsContainer != null) {
            for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
                View child = movieSectionsContainer.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof TvRecommendationReader.MovieSection) {
                    list.add((TvRecommendationReader.MovieSection) tag);
                }
            }
        }
        return list;
    }

    public void applySectionOrderFromConfig() {
        if (movieSectionsContainer == null || movieSectionsContainer.getChildCount() <= 1 || sectionConfig == null) return;
        List<String> order = sectionConfig.getSectionOrder();
        if (order == null || order.isEmpty()) return;

        List<View> children = new ArrayList<>();
        for (int i = 0; i < movieSectionsContainer.getChildCount(); i++) {
            children.add(movieSectionsContainer.getChildAt(i));
        }

        Collections.sort(children, (a, b) -> {
            Object tagA = a.getTag();
            Object tagB = b.getTag();
            String idA = tagA instanceof TvRecommendationReader.MovieSection ? ((TvRecommendationReader.MovieSection) tagA).id : "";
            String idB = tagB instanceof TvRecommendationReader.MovieSection ? ((TvRecommendationReader.MovieSection) tagB).id : "";
            int ia = order.indexOf(idA);
            int ib = order.indexOf(idB);
            if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
            if (ia >= 0) return -1;
            if (ib >= 0) return 1;
            return 0;
        });

        movieSectionsContainer.removeAllViews();
        for (View c : children) {
            Object tag = c.getTag();
            if (tag instanceof TvRecommendationReader.MovieSection) {
                String secId = ((TvRecommendationReader.MovieSection) tag).id;
                boolean isEnabled = sectionConfig.isSectionEnabled(secId);
                c.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                c.setAlpha(isEnabled ? 1.0f : 0.35f);
            }
            movieSectionsContainer.addView(c);
        }
    }
}
