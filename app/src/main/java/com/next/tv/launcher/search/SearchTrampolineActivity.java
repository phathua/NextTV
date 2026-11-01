package com.next.tv.launcher.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import com.next.tv.launcher.MainActivity;

/**
 * Chuyển yêu cầu tìm kiếm và chờ người dùng đóng màn hình cấp quyền nếu cần.
 */
public class SearchTrampolineActivity extends Activity {
    private static final int REQUEST_OVERLAY = 401;
    private boolean waitingForOverlay;
    private String pendingQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.getBoolean("waitingForOverlay")) {
            waitingForOverlay = true;
            pendingQuery = savedInstanceState.getString("pendingQuery");
            return;
        }

        try {
            Intent intent = getIntent();
            GlobalSearchOverlayManager manager = GlobalSearchOverlayManager.getInstance(getApplicationContext());
            if (manager != null && manager.isMicOverrideEnabled()) {
                String query = null;
                if (intent != null) {
                    query = intent.getStringExtra("query");
                    if (query == null || query.trim().isEmpty()) {
                        query = intent.getStringExtra(SearchManager.QUERY);
                    }
                }

                final String finalQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

                boolean canDrawOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
                if (!canDrawOverlay && !MainActivity.isResumed) {
                    pendingQuery = finalQuery;
                    requestOverlayPermission();
                    return;
                }
                if (MainActivity.activeInstance != null && !MainActivity.activeInstance.isFinishing() && !MainActivity.activeInstance.isDestroyed() && MainActivity.isResumed) {
                    MainActivity.activeInstance.runOnUiThread(() -> {
                        if (finalQuery != null) {
                            MainActivity.activeInstance.showSearchOverlay(false);
                            manager.executeUniversalSearch(finalQuery, true);
                        } else {
                            MainActivity.activeInstance.showSearchOverlay(true);
                        }
                    });
                } else if (canDrawOverlay) {
                    if (finalQuery != null) {
                        manager.show(getApplicationContext(), false);
                        manager.executeUniversalSearch(finalQuery, true);
                    } else {
                        manager.show(getApplicationContext(), true);
                    }
                } else {
                    Intent mainIntent = new Intent(this, MainActivity.class);
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mainIntent.setAction(Intent.ACTION_SEARCH);
                    if (finalQuery != null) {
                        mainIntent.putExtra(SearchManager.QUERY, finalQuery);
                    }
                    startActivity(mainIntent);
                }
            }
        } catch (Exception error) {
            Log.e("SearchTrampoline", "Không thể mở tìm kiếm", error);
        } finally {
            if (!waitingForOverlay) {
                finish();
            }
        }
    }

    private void requestOverlayPermission() {
        waitingForOverlay = true;
        try {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQUEST_OVERLAY);
        } catch (Exception firstError) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), REQUEST_OVERLAY);
            } catch (Exception error) {
                waitingForOverlay = false;
                Log.e("SearchTrampoline", "Không thể mở màn hình cấp quyền hiển thị đè", error);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("waitingForOverlay", waitingForOverlay);
        outState.putString("pendingQuery", pendingQuery);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OVERLAY || !waitingForOverlay) {
            return;
        }
        waitingForOverlay = false;
        try {
            // Kiểm tra quyền thực tế, vì màn hình cài đặt không trả kết quả cấp quyền.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                GlobalSearchOverlayManager manager = GlobalSearchOverlayManager.getInstance(getApplicationContext());
                if (manager != null && manager.isMicOverrideEnabled()) {
                    manager.show(getApplicationContext(), pendingQuery == null);
                    if (pendingQuery != null) {
                        manager.executeUniversalSearch(pendingQuery, true);
                    }
                }
            }
        } catch (Exception error) {
            Log.e("SearchTrampoline", "Không thể tiếp tục tìm kiếm sau khi cấp quyền", error);
        } finally {
            finish();
        }
    }
}
