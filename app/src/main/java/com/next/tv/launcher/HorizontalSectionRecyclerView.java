package com.next.tv.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A horizontal RecyclerView that traps DPAD_LEFT and DPAD_RIGHT within the row,
 * intercepts vertical D-pad navigation to provide deterministic ladder routing,
 * dispatches left-edge navigation events to allow section management drawers,
 * and enables edge fading when content extends off-screen.
 */
public class HorizontalSectionRecyclerView extends RecyclerView {

    public interface OnLeftEdgeListener {
        boolean onLeftEdge(View focusedChild);
    }

    public interface OnVerticalNavListener {
        boolean onVerticalNav(int direction);
    }

    private OnLeftEdgeListener leftEdgeListener;
    private OnVerticalNavListener verticalNavListener;

    public HorizontalSectionRecyclerView(@NonNull Context context) {
        super(context);
        init();
    }

    public HorizontalSectionRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HorizontalSectionRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setHasFixedSize(false);
        setItemAnimator(null);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setItemViewCacheSize(10);
        if (getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) getLayoutManager()).setInitialPrefetchItemCount(8);
        }
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength((int) (48 * getResources().getDisplayMetrics().density));
    }

    @Override
    public void setLayoutManager(@Nullable LayoutManager layout) {
        super.setLayoutManager(layout);
        if (layout instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layout).setInitialPrefetchItemCount(8);
        }
    }

    public void setOnLeftEdgeListener(OnLeftEdgeListener listener) {
        this.leftEdgeListener = listener;
    }

    public void setOnVerticalNavListener(OnVerticalNavListener listener) {
        this.verticalNavListener = listener;
    }

    @Override
    public float getLeftFadingEdgeStrength() {
        return canScrollHorizontally(-1) ? 1.0f : 0.0f;
    }

    @Override
    public float getRightFadingEdgeStrength() {
        return canScrollHorizontally(1) ? 1.0f : 0.0f;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        android.util.Log.d("TvHome.Nav", "HSRV.dispatchKeyEvent code=" + event.getKeyCode() + " act=" + event.getAction() + " listener=" + (verticalNavListener != null));
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 1. Intercept Vertical Navigation (DPAD_UP / DPAD_DOWN)
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && verticalNavListener != null) {
            if (action == KeyEvent.ACTION_DOWN) {
                verticalNavListener.onVerticalNav(View.FOCUS_UP);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && verticalNavListener != null) {
            if (action == KeyEvent.ACTION_DOWN) {
                verticalNavListener.onVerticalNav(View.FOCUS_DOWN);
            }
            return true;
        }

        // 2. Intercept DPAD_RIGHT to prevent escaping to Top Bar (Wi-Fi / Bluetooth)
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            View focused = findFocus();
            if (focused != null) {
                View itemView = findContainingItemView(focused);
                Adapter<?> adapter = getAdapter();
                if (itemView != null && adapter != null) {
                    int pos = getChildAdapterPosition(itemView);
                    // Boundary lock at the last card: consume both DOWN and UP
                    if (pos == adapter.getItemCount() - 1) {
                        return true;
                    }
                    if (pos >= 0 && pos < adapter.getItemCount() - 1) {
                        ViewHolder nextHolder = findViewHolderForAdapterPosition(pos + 1);
                        if (nextHolder == null || nextHolder.itemView.getParent() == null) {
                            if (action == KeyEvent.ACTION_DOWN) {
                                scrollToPosition(pos + 1);
                                post(() -> {
                                    ViewHolder vh = findViewHolderForAdapterPosition(pos + 1);
                                    if (vh != null) {
                                        View c = vh.itemView.findViewById(R.id.card);
                                        if (c == null) c = vh.itemView.findViewById(R.id.searchCard);
                                        if (c != null && c.isFocusable()) c.requestFocus();
                                        else vh.itemView.requestFocus();
                                    }
                                });
                            }
                            return true;
                        }
                    }
                }
            }
        }

        // 3. Intercept DPAD_LEFT: scroll back safely or open management drawer on single press at pos 0
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            View focused = findFocus();
            if (focused != null) {
                View itemView = findContainingItemView(focused);
                Adapter<?> adapter = getAdapter();
                if (itemView != null && adapter != null) {
                    int pos = getChildAdapterPosition(itemView);
                    if (pos > 0) {
                        ViewHolder prevHolder = findViewHolderForAdapterPosition(pos - 1);
                        if (prevHolder == null || prevHolder.itemView.getParent() == null) {
                            if (action == KeyEvent.ACTION_DOWN) {
                                scrollToPosition(pos - 1);
                                post(() -> {
                                    ViewHolder vh = findViewHolderForAdapterPosition(pos - 1);
                                    if (vh != null) {
                                        View c = vh.itemView.findViewById(R.id.card);
                                        if (c == null) c = vh.itemView.findViewById(R.id.searchCard);
                                        if (c != null && c.isFocusable()) c.requestFocus();
                                        else vh.itemView.requestFocus();
                                    }
                                });
                            }
                            return true;
                        }
                    } else if (pos == 0) {
                        // At the first item: ONLY trigger leftEdgeListener on ACTION_DOWN with repeatCount == 0!
                        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                            if (leftEdgeListener != null && leftEdgeListener.onLeftEdge(itemView)) {
                                return true;
                            }
                        } else if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
                            // User is holding DPAD_LEFT from somewhere further down the row:
                            // Do NOT trigger management drawer while key is held! Just trap focus at 0.
                            return true;
                        } else if (action == KeyEvent.ACTION_UP) {
                            return true;
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT) {
            View next = super.focusSearch(focused, direction);
            if (next == null || !isDescendantOfThis(next)) {
                // Keep horizontal focus strictly trapped within this row; never escape to top bar
                return focused;
            }
            View card = next.findViewById(R.id.card);
            if (card == null) card = next.findViewById(R.id.searchCard);
            if (card != null && card.isFocusable()) {
                return card;
            }
            return next;
        }
        return super.focusSearch(focused, direction);
    }

    private boolean isDescendantOfThis(View view) {
        for (ViewParent p = view.getParent(); p != null; p = p.getParent()) {
            if (p == this) return true;
        }
        return false;
    }
}
