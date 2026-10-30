package com.next.tv.launcher.browser;

import android.app.Activity;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.next.tv.launcher.FastPosterLoader;
import com.next.tv.launcher.R;

/**
 * 100% Native Pure Java 16:9 Split-Pane Reader Mode Activity for Android TV.
 * Zero WebView dependencies, saving ~100MB+ of Chromium sandbox RAM.
 * Left Pane: Fixed meta & 16:9 hero image (38% width).
 * Right Pane: Scrollable 10-foot editorial typography (62% width, max 560dp line width).
 */
public class NativeReaderActivity extends Activity {
    private static final String TAG = "TvHome.NativeReader";

    private ScrollView scrollView;
    private TextView tvSourceBadge;
    private TextView tvTitle;
    private ImageView ivLeadImage;
    private TextView tvSapo;
    private LinearLayout blocksContainer;
    private View loadingContainer;
    private TextView tvLoadingText;

    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_native_reader);

        density = getResources().getDisplayMetrics().density;

        scrollView = findViewById(R.id.readerScrollView);
        tvSourceBadge = findViewById(R.id.readerSourceBadge);
        tvTitle = findViewById(R.id.readerTitle);
        ivLeadImage = findViewById(R.id.readerLeadImage);
        tvSapo = findViewById(R.id.readerSapo);
        blocksContainer = findViewById(R.id.readerBlocksContainer);
        loadingContainer = findViewById(R.id.readerLoadingContainer);
        tvLoadingText = findViewById(R.id.readerLoadingText);

        final float cornerRadiusPx = 14f * density;
        ViewOutlineProvider roundOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        };
        ivLeadImage.setOutlineProvider(roundOutline);
        ivLeadImage.setClipToOutline(true);

        String url = getIntent().getStringExtra("url");
        String initialTitle = getIntent().getStringExtra("title");
        String source = getIntent().getStringExtra("source");

        if (source != null && !source.isEmpty()) {
            tvSourceBadge.setText(source);
        } else {
            tvSourceBadge.setText("Tin tức");
        }

        if (initialTitle != null && !initialTitle.isEmpty()) {
            tvTitle.setText(initialTitle);
        }

        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Liên kết không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadingContainer.setVisibility(View.VISIBLE);

        ArticleExtractor.fetchAsync(url, initialTitle, source, new ArticleExtractor.Callback() {
            @Override
            public void onSuccess(ArticleExtractor.ArticleContent article) {
                runOnUiThread(() -> populateArticle(article));
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    loadingContainer.setVisibility(View.GONE);
                    if (tvTitle.getText().length() == 0 && initialTitle != null) {
                        tvTitle.setText(initialTitle);
                    }
                    TextView errorView = new TextView(NativeReaderActivity.this);
                    errorView.setText("Không thể trích xuất nội dung bài viết này.");
                    errorView.setTextColor(0xFF71717A);
                    errorView.setTextSize(16f);
                    errorView.setPadding(0, (int) (24 * density), 0, 0);
                    blocksContainer.addView(errorView);
                });
            }
        });

        scrollView.requestFocus();
    }

    private void populateArticle(ArticleExtractor.ArticleContent article) {
        if (isFinishing() || isDestroyed()) return;

        loadingContainer.setVisibility(View.GONE);

        if (article.title != null && !article.title.isEmpty()) {
            tvTitle.setText(article.title);
        }

        if (article.source != null && !article.source.isEmpty()) {
            tvSourceBadge.setText(article.source);
        }

        if (article.leadImageUrl != null && !article.leadImageUrl.isEmpty()) {
            ivLeadImage.setVisibility(View.VISIBLE);
            FastPosterLoader.getInstance().load(Uri.parse(article.leadImageUrl), ivLeadImage, 0);
        } else {
            ivLeadImage.setVisibility(View.GONE);
        }

        if (article.sapo != null && !article.sapo.isEmpty()) {
            tvSapo.setText(article.sapo);
            tvSapo.setVisibility(View.VISIBLE);
        } else {
            tvSapo.setVisibility(View.GONE);
        }

        blocksContainer.removeAllViews();

        final float cornerRadiusPx = 12f * density;
        ViewOutlineProvider imgOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        };

        for (ArticleExtractor.ArticleBlock block : article.blocks) {
            if (block.type == ArticleExtractor.ArticleBlock.TYPE_TEXT) {
                TextView tv = new TextView(this);
                tv.setText(block.content);
                tv.setTextColor(0xFFF4F4F5); // Neutral Zinc 100
                tv.setTextSize(18f);
                tv.setLineSpacing(4f * density, 1.45f);
                tv.setPadding(0, 0, 0, (int) (16 * density));
                blocksContainer.addView(tv);
            } else if (block.type == ArticleExtractor.ArticleBlock.TYPE_HEADING) {
                TextView heading = new TextView(this);
                heading.setText(block.content);
                heading.setTextColor(0xFFFFFFFF);
                heading.setTextSize(20f);
                heading.setPadding(0, (int) (14 * density), 0, (int) (8 * density));
                blocksContainer.addView(heading);
            } else if (block.type == ArticleExtractor.ArticleBlock.TYPE_IMAGE) {
                ImageView iv = new ImageView(this);
                iv.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * density)));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setOutlineProvider(imgOutline);
                iv.setClipToOutline(true);
                FastPosterLoader.getInstance().load(Uri.parse(block.content), iv, 0);

                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
                lp.setMargins(0, (int) (10 * density), 0, (int) (6 * density));
                blocksContainer.addView(iv);

                if (block.caption != null && !block.caption.isEmpty()) {
                    TextView cap = new TextView(this);
                    cap.setText(block.caption);
                    cap.setTextColor(0xFFA1A1AA);
                    cap.setTextSize(13f);
                    cap.setPadding(0, 0, 0, (int) (14 * density));
                    blocksContainer.addView(cap);
                }
            }
        }

        scrollView.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        int scrollStep = Math.round(280 * density);
        int pageStep = Math.round(520 * density);

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (scrollView != null) scrollView.smoothScrollBy(0, scrollStep);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (scrollView != null) scrollView.smoothScrollBy(0, -scrollStep);
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (scrollView != null) scrollView.smoothScrollBy(0, pageStep);
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (scrollView != null) scrollView.smoothScrollBy(0, -pageStep);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (scrollView != null && scrollView.getScrollY() <= 10) {
                    finish();
                    return true;
                } else if (scrollView != null) {
                    scrollView.smoothScrollBy(0, -pageStep);
                    return true;
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (ivLeadImage != null) {
            ivLeadImage.setImageDrawable(null);
        }
        if (blocksContainer != null) {
            blocksContainer.removeAllViews();
        }
        super.onDestroy();
    }
}
