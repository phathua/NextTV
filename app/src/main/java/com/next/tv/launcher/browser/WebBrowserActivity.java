package com.next.tv.launcher.browser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.next.tv.launcher.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Dedicated ultra-lightweight Android TV Web Browser Activity.
 * Employs System WebView with hardware acceleration, HTML5 fullscreen video playback,
 * network-layer ad blocking, and D-pad spatial navigation over HTML card/button elements.
 */
public class WebBrowserActivity extends Activity {
    private static final String TAG = "TvHome.WebBrowser";

    private WebView webView;
    private FrameLayout customViewContainer;
    private ProgressBar progressBar;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static String cachedSpatialNavScript = null;
    private static String cachedReaderModeScript = null;
    private boolean isReaderMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_web_browser);

        isReaderMode = getIntent().getBooleanExtra("reader_mode", false);

        AdBlockEngine.init(this);
        loadSpatialNavScript();
        if (isReaderMode) {
            loadReaderModeScript();
        }

        webView = findViewById(R.id.webView);
        customViewContainer = findViewById(R.id.customViewContainer);
        progressBar = findViewById(R.id.browserProgressBar);

        setupWebView();

        Uri dataUri = getIntent().getData();
        String targetUrl = (dataUri != null) ? dataUri.toString() : getIntent().getStringExtra("url");
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = "https://tuoitre.vn";
        }
        webView.loadUrl(targetUrl);
    }

    private void loadSpatialNavScript() {
        if (cachedSpatialNavScript != null) return;
        try {
            InputStream is = getAssets().open("browser/spatialnav.js");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            cachedSpatialNavScript = sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed reading spatialnav.js: " + e.getMessage());
        }
    }

    private void loadReaderModeScript() {
        if (cachedReaderModeScript != null) return;
        try {
            InputStream is = getAssets().open("browser/readermode.js");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            cachedReaderModeScript = sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed reading readermode.js: " + e.getMessage());
        }
    }

    private void setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Modern TV desktop/mobile user agent that gets responsive web designs
        String defaultUa = settings.getUserAgentString();
        settings.setUserAgentString(defaultUa + " CleanYourTV/1.0");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    if (newProgress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                customViewContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                customViewContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                customView.requestFocus();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                customViewContainer.removeView(customView);
                customViewContainer.setVisibility(View.GONE);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                webView.requestFocus();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse adBlockResp = AdBlockEngine.intercept(request);
                if (adBlockResp != null) return adBlockResp;
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectSpatialNavigation(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isReaderMode) {
                    injectReaderMode(view);
                } else {
                    injectSpatialNavigation(view);
                    view.postDelayed(() -> {
                        if (view != null) {
                            view.evaluateJavascript("window.__spatialNav && window.__spatialNav.init();", null);
                        }
                    }, 300);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.w(TAG, "WebView render process gone. Recreating view to avoid crash.");
                if (webView != null) {
                    ViewGroup parent = (ViewGroup) webView.getParent();
                    if (parent != null) {
                        parent.removeView(webView);
                        webView.destroy();
                        webView = null;
                    }
                }
                finish();
                return true;
            }
        });
    }

    private void injectSpatialNavigation(WebView view) {
        if (view == null || cachedSpatialNavScript == null) return;
        view.evaluateJavascript("javascript:(function() {" + cachedSpatialNavScript + "})();", null);
    }

    private void injectReaderMode(WebView view) {
        if (view == null || cachedReaderModeScript == null) return;
        view.evaluateJavascript("javascript:(function() {" + cachedReaderModeScript + "})();", null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();

        if (customView != null) {
            // Fullscreen video playback mode: let video controls handle keys or exit on BACK
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView != null && webView.getWebChromeClient() != null) {
                    onBackPressed();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (isReaderMode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (webView != null) webView.evaluateJavascript("window.scrollBy({ top: -320, behavior: 'smooth' });", null);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (webView != null) webView.evaluateJavascript("window.scrollBy({ top: 320, behavior: 'smooth' });", null);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    finish();
                    return true;
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                webView.evaluateJavascript("window.__spatialNav && window.__spatialNav.move('up');", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                webView.evaluateJavascript("window.__spatialNav && window.__spatialNav.move('down');", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                webView.evaluateJavascript("window.__spatialNav && window.__spatialNav.move('left');", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                webView.evaluateJavascript("window.__spatialNav && window.__spatialNav.move('right');", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                webView.evaluateJavascript("window.__spatialNav && window.__spatialNav.activate();", null);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                finish();
                return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            if (webView != null && webView.getWebChromeClient() != null) {
                // Trigger WebChromeClient custom view hide
                WebChromeClient client = new WebChromeClient();
                client.onHideCustomView();
            }
            if (customViewContainer != null) {
                customViewContainer.setVisibility(View.GONE);
                customViewContainer.removeAllViews();
            }
            customView = null;
            if (webView != null) webView.setVisibility(View.VISIBLE);
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
