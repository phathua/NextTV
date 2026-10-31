package com.next.tv.launcher.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.next.tv.launcher.MainActivity;

/**
 * Lightweight zero-UI trampoline activity configured with Theme.NoDisplay.
 * Intercepts system-wide Search and Voice Assistant intents (ASSIST, WEB_SEARCH, VOICE_COMMAND, etc.)
 * without pausing the foreground application (YouTube, Netflix, etc.) by finishing immediately in onCreate().
 */
public class SearchTrampolineActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                if (MainActivity.activeInstance != null && !MainActivity.activeInstance.isFinishing() && !MainActivity.activeInstance.isDestroyed() && (!canDrawOverlay || MainActivity.isResumed)) {
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
        } catch (Exception ignored) {
        } finally {
            finish();
        }
    }
}
