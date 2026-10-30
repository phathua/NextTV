(function() {
    if (window.__readerModeApplied) return;
    window.__readerModeApplied = true;

    function extractReaderContent() {
        // 1. Extract Title
        var titleEl = document.querySelector('h1') || document.querySelector('meta[property="og:title"]');
        var titleText = titleEl ? (titleEl.innerText || titleEl.getAttribute('content') || document.title) : document.title;

        // 2. Extract Lead Image
        var leadImg = document.querySelector('meta[property="og:image"]');
        var leadImgUrl = leadImg ? leadImg.getAttribute('content') : null;

        // 3. Find Best Content Candidate
        var candidates = document.querySelectorAll('article, main, .detail-content, .fck_detail, .content-detail, .article-body, .main-content, .post-content, .detail-main');
        var contentEl = null;
        for (var i = 0; i < candidates.length; i++) {
            var c = candidates[i];
            if (c.innerText && c.innerText.length > 200) {
                contentEl = c;
                break;
            }
        }
        if (!contentEl) {
            contentEl = document.querySelector('body');
        }

        // 4. Clone Content & Purge Clutter Elements
        var cloned = contentEl.cloneNode(true);
        var garbageSelectors = [
            'header', 'footer', 'nav', 'aside', 'script', 'style', 'iframe', 'noscript',
            '.header', '.footer', '.sidebar', '.menu', '.ads', '.advertisement',
            '[class*="banner"]', '[id*="banner"]', '[class*="social"]', '[class*="comment"]',
            '[class*="relate"]', '[class*="box-category"]', '[class*="bread-crumb"]',
            '[class*="cookie"]', '[class*="popup"]', '[class*="modal"]'
        ];
        garbageSelectors.forEach(function(sel) {
            var els = cloned.querySelectorAll(sel);
            els.forEach(function(el) { el.remove(); });
        });

        // 5. Build TV 10-foot Ergonomic DOM
        var readerRoot = document.createElement('div');
        readerRoot.id = 'tv-reader-root';

        var titleNode = document.createElement('h1');
        titleNode.className = 'tv-reader-title';
        titleNode.innerText = titleText.trim();
        readerRoot.appendChild(titleNode);

        if (leadImgUrl) {
            var imgWrap = document.createElement('div');
            imgWrap.className = 'tv-reader-hero-img';
            var imgNode = document.createElement('img');
            imgNode.src = leadImgUrl;
            imgWrap.appendChild(imgNode);
            readerRoot.appendChild(imgWrap);
        }

        var bodyWrap = document.createElement('div');
        bodyWrap.className = 'tv-reader-body';
        
        // Extract all readable paragraphs and legitimate images
        var paragraphs = cloned.querySelectorAll('p, h2, h3, img, figure');
        if (paragraphs.length > 0) {
            paragraphs.forEach(function(p) {
                var tag = p.tagName.toLowerCase();
                if (tag === 'p') {
                    var text = p.innerText.trim();
                    if (text.length > 15) {
                        var pNode = document.createElement('p');
                        pNode.innerText = text;
                        bodyWrap.appendChild(pNode);
                    }
                } else if (tag === 'h2' || tag === 'h3') {
                    var hNode = document.createElement(tag);
                    hNode.innerText = p.innerText.trim();
                    bodyWrap.appendChild(hNode);
                } else if (tag === 'img' && p.src) {
                    var iNode = document.createElement('img');
                    iNode.src = p.src;
                    iNode.loading = 'lazy';
                    bodyWrap.appendChild(iNode);
                } else if (tag === 'figure') {
                    bodyWrap.appendChild(p.cloneNode(true));
                }
            });
        } else {
            bodyWrap.innerHTML = cloned.innerHTML;
        }
        readerRoot.appendChild(bodyWrap);

        // 6. Inject Strict TV 10-foot CSS Styles
        var style = document.createElement('style');
        style.innerHTML = `
            html, body {
                background-color: #09090b !important;
                color: #e4e4e7 !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow-x: hidden !important;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif !important;
            }
            body > *:not(#tv-reader-root) {
                display: none !important;
            }
            #tv-reader-root {
                display: block !important;
                max-width: 960px !important;
                margin: 0 auto !important;
                padding: 48px 64px 120px 64px !important;
                box-sizing: border-box !important;
                line-height: 1.7 !important;
            }
            .tv-reader-title {
                color: #ffffff !important;
                font-size: 34px !important;
                font-weight: 700 !important;
                line-height: 1.35 !important;
                margin: 0 0 24px 0 !important;
                letter-spacing: -0.4px !important;
            }
            .tv-reader-hero-img img, .tv-reader-body img {
                max-width: 100% !important;
                height: auto !important;
                border-radius: 14px !important;
                margin: 20px 0 !important;
                display: block !important;
                box-shadow: 0 6px 20px rgba(0,0,0,0.5) !important;
            }
            .tv-reader-body p {
                font-size: 22px !important;
                color: #e4e4e7 !important;
                margin-bottom: 24px !important;
                letter-spacing: 0.1px !important;
            }
            .tv-reader-body h2, .tv-reader-body h3 {
                color: #f4f4f5 !important;
                font-size: 26px !important;
                margin: 36px 0 16px 0 !important;
            }
        `;
        document.head.appendChild(style);
        document.body.appendChild(readerRoot);
    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        extractReaderContent();
    } else {
        window.addEventListener('DOMContentLoaded', extractReaderContent);
    }
})();
