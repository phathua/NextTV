package com.next.tv.launcher.controller;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.next.tv.launcher.apps.AppAdapter;
import com.next.tv.launcher.apps.DockSlots;
import com.next.tv.launcher.festive.Festival;
import com.next.tv.launcher.apps.Order;
import com.next.tv.launcher.R;
import com.next.tv.launcher.lock.AppLockManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages tile reordering and dock editing workflows.
 */
public class AppReorderController {

    public interface Host {
        Context getContext();
        AppAdapter getAdapter();
        RecyclerView getGrid();
        AppLockManager getAppLockManager();
        GlassFocusPresenter getGlassFocusPresenter();
        View getBtnReorder();
        List<String> getDockApps();
        boolean isFillDockLeavesSlots();
        boolean isRtlAppGrid();
        int getColumns();
        boolean isFestive();
        Festival getFestival();
        void reloadTiles();
        void focusTile(int pos);
        void onLibraryRequested();
        int getFocusedTilePosition();
        void keepScroll(Runnable change);
        View getCurrentFocus();
    }

    private final Host host;

    private boolean reordering;
    private boolean dockEditing;
    private String movingKey;
    private List<String> orderAtStart;
    private Set<String> dockInSession;
    private int dockedAtStart;
    private int dockEditSlot = -1;
    private String dockEditAppKey;

    public AppReorderController(Host host) {
        this.host = host;
    }

    public boolean isReordering() {
        return reordering;
    }

    public boolean isDockEditing() {
        return dockEditing;
    }

    public String getMovingKey() {
        return movingKey;
    }

    public void toggleReorder(int lastFocused) {
        AppLockManager lockMgr = host.getAppLockManager();
        if (lockMgr != null && lockMgr.isKidModeActive()) {
            Toast.makeText(host.getContext(), "Đang bật Chế độ trẻ em", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reordering) {
            endReorder();
            return;
        }
        if (!beginReorder()) return;
        AppAdapter adapter = host.getAdapter();
        int maxPos = adapter != null ? adapter.movableCount() - 1 : 0;
        host.focusTile(Math.max(0, Math.min(lastFocused, maxPos)));
        Toast.makeText(host.getContext(), R.string.reorder_hint, Toast.LENGTH_LONG).show();
    }

    public boolean beginReorder() {
        AppAdapter adapter = host.getAdapter();
        if (adapter == null || adapter.movableCount() < 2) return false;
        dockInSession = new HashSet<>();
        dockedAtStart = adapter.dockFilled();
        for (int i = 0; i < Math.min(dockedAtStart, adapter.movableCount()); i++) {
            dockInSession.add(adapter.keyAt(i));
        }
        adapter.setReordering(true);
        if (adapter.hasEmptySlots()) host.reloadTiles();
        reordering = true;
        orderAtStart = adapter.movableKeys();
        GlassFocusPresenter gfp = host.getGlassFocusPresenter();
        if (gfp != null) {
            gfp.setReorderMode(true, adapter, host.getFocusedTilePosition());
        }
        View btnReorder = host.getBtnReorder();
        if (btnReorder != null) btnReorder.setActivated(true);
        return true;
    }

    public void startDockEdit() {
        if (reordering) endReorder();
        if (!beginReorder()) return;
        dockEditing = true;
        dockEditSlot = -1;
        host.focusTile(0);
        Toast.makeText(host.getContext(), R.string.dock_edit_hint, Toast.LENGTH_LONG).show();
    }

    public void startDockEditFor(String key, boolean inDock) {
        if (reordering) endReorder();
        if (!beginReorder()) return;
        dockEditing = true;
        AppAdapter adapter = host.getAdapter();
        int pos = adapter != null ? adapter.positionOf(key) : -1;
        int n = adapter != null ? adapter.movableCount() : 0;
        int cols = host.getColumns();
        if (pos < 0) {
            endReorder();
        } else if (inDock) {
            dockEditSlot = pos;
            dimOthers();
            host.focusTile(Math.min(cols, n - 1));
            Toast.makeText(host.getContext(), R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show();
        } else {
            dockEditAppKey = key;
            host.focusTile(0);
            Toast.makeText(host.getContext(), R.string.dock_edit_pick_place, Toast.LENGTH_LONG).show();
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (!reordering) return false;
        if (dockEditing) return dispatchDockEdit(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (movingKey != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveBy(code);
                    return true;
                default:
                    if (ok || back) {
                        if (!down) drop();
                        return true;
                    }
                    return false;
            }
        }
        if (back) {
            if (!down) endReorder();
            return true;
        }
        int pos = host.getFocusedTilePosition();
        View focus = host.getCurrentFocus();
        if (ok && focus != null && focus.getId() == R.id.btnLibrary) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                endReorder();
                host.onLibraryRequested();
            }
            return true;
        }
        RecyclerView grid = host.getGrid();
        if (ok && grid != null && grid.hasFocus()) {
            if (!down && pos >= 0) pickUp(pos);
            return true;
        }
        return false;
    }

    public boolean dispatchDockEdit(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) {
                if (dockEditAppKey != null) {
                    endReorder();
                } else if (dockEditSlot >= 0) {
                    int slot = dockEditSlot;
                    dockEditSlot = -1;
                    dimOthers();
                    host.focusTile(slot);
                } else {
                    endReorder();
                }
            }
            return true;
        }
        int pos = host.getFocusedTilePosition();
        if (ok && pos >= 0) {
            if (!down) {
                AppAdapter adapter = host.getAdapter();
                int n = adapter != null ? adapter.movableCount() : 0;
                int cols = host.getColumns();
                if (dockEditAppKey != null) {
                    if (pos < Math.min(cols, n)) {
                        int from = adapter != null ? adapter.positionOf(dockEditAppKey) : -1;
                        if (from >= 0) swapIntoDock(pos, from);
                        else endReorder();
                    }
                } else if (dockEditSlot < 0) {
                    if (pos < Math.min(cols, n)) {
                        dockEditSlot = pos;
                        dimOthers();
                        host.focusTile(Math.min(cols, n - 1));
                        Toast.makeText(host.getContext(), R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show();
                    }
                } else if (pos < n) {
                    swapIntoDock(dockEditSlot, pos);
                }
            }
            return true;
        }
        return false;
    }

    public void swapIntoDock(int slot, int pos) {
        AppAdapter adapter = host.getAdapter();
        if (adapter == null) return;
        String slotKey = adapter.keyAt(slot);
        int cols = host.getColumns();
        if (pos != slot) {
            host.keepScroll(() -> {
                adapter.move(pos, slot);
                adapter.move(adapter.positionOf(slotKey), pos);
            });
            if (dockInSession != null) {
                dockInSession.add(adapter.keyAt(slot));
                if (pos >= cols) dockInSession.remove(slotKey);
            }
        }
        endReorder();
        refocusAfterLayout(slot);
    }

    public void pickUp(int pos) {
        AppAdapter adapter = host.getAdapter();
        if (adapter == null || pos >= adapter.movableCount()) return;
        movingKey = adapter.keyAt(pos);
        dimOthers();
    }

    public void drop() {
        movingKey = null;
        dimOthers();
    }

    public void dimOthers() {
        RecyclerView grid = host.getGrid();
        if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            item.setAlpha(tileAlpha(grid.getChildAdapterPosition(item)));
        }
    }

    public float tileAlpha(int pos) {
        AppAdapter adapter = host.getAdapter();
        if (movingKey != null && adapter != null) {
            return pos != RecyclerView.NO_POSITION && movingKey.equals(adapter.keyAt(pos)) ? 1f : 0.45f;
        }
        if (dockEditSlot >= 0 && pos >= 0 && pos < host.getColumns() && pos != dockEditSlot) return 0.45f;
        return 1f;
    }

    public void endReorder() {
        if (!reordering) return;
        dockEditing = false;
        dockEditSlot = -1;
        dockEditAppKey = null;
        drop();
        reordering = false;
        AppAdapter adapter = host.getAdapter();
        if (adapter != null) adapter.setReordering(false);
        GlassFocusPresenter gfp = host.getGlassFocusPresenter();
        if (gfp != null) {
            gfp.setReorderMode(false, adapter, host.getFocusedTilePosition());
        }
        View btnReorder = host.getBtnReorder();
        if (btnReorder != null) btnReorder.setActivated(false);
        if (adapter == null) return;
        List<String> keys = adapter.movableKeys();
        List<String> before = orderAtStart;
        orderAtStart = null;
        int docked = -1;
        int cols = host.getColumns();
        if (host.isFillDockLeavesSlots()) {
            docked = DockSlots.dockCount(keys, cols, dockInSession);
            keys = DockSlots.dockFirst(keys, cols, dockInSession);
        }
        dockInSession = null;
        if (!keys.equals(before) || (host.isFillDockLeavesSlots() && docked != dockedAtStart)) {
            List<String> dockApps = host.getDockApps();
            Order order = new Order(Order.dockKey(dockApps), keys, docked);
            Context ctx = host.getContext();
            if (Order.save(Order.file(ctx), order)) {
                adapter.setSavedOrder(Order.ranks(order, dockApps), Order.docked(order, dockApps));
            } else {
                Toast.makeText(ctx, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            }
        }
        if (host.isFillDockLeavesSlots()) host.reloadTiles();
    }

    public void moveBy(int keyCode) {
        AppAdapter adapter = host.getAdapter();
        if (adapter == null) return;
        int from = adapter.positionOf(movingKey);
        int n = adapter.movableCount();
        if (from < 0 || from >= n) {
            drop();
            return;
        }
        int to;
        boolean rtl = host.isRtlAppGrid();
        int cols = host.getColumns();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) to = rtl ? from + 1 : from - 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) to = rtl ? from - 1 : from + 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) to = from - cols;
        else if (from + cols < n) to = from + cols;
        else to = from / cols < (n - 1) / cols ? n - 1 : from;
        if (to < 0 || to >= n || to == from) return;
        host.keepScroll(() -> adapter.move(from, to));
        if (dockInSession != null) {
            if (to < cols) dockInSession.add(movingKey);
            else dockInSession.remove(movingKey);
        }
        refocusAfterLayout(to);
    }

    public void refocusAfterLayout(int pos) {
        RecyclerView grid = host.getGrid();
        if (grid == null) return;
        grid.postOnAnimation(new Runnable() {
            int frames;

            @Override
            public void run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested()) && ++frames < 10) {
                    grid.postOnAnimation(this);
                    return;
                }
                host.focusTile(pos);
                dimOthers();
            }
        });
    }
}
