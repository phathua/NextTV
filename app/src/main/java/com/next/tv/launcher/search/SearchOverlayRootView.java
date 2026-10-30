package com.next.tv.launcher.search;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SearchOverlayRootView extends FrameLayout {

    private Runnable dismissListener;

    public SearchOverlayRootView(@NonNull Context context) {
        super(context);
    }

    public SearchOverlayRootView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchOverlayRootView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDismissListener(Runnable listener) {
        this.dismissListener = listener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (dismissListener != null) {
                    dismissListener.run();
                } else {
                    GlobalSearchOverlayManager manager = GlobalSearchOverlayManager.getInstance(getContext());
                    if (manager != null && manager.isShowing()) {
                        manager.dismiss();
                    }
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (focused != null && isDescendantOfThis(focused)) {
            View next = super.focusSearch(focused, direction);
            if (next == null || !isDescendantOfThis(next)) {
                // Strictly trap focus inside search overlay; never let focus escape to background launcher
                return focused;
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
