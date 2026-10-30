/**
 * Spatial Navigation engine for Android TV System WebView.
 * Implements Android FocusFinder geometry (13 * major^2 + minor^2)
 * and CSSOM outline highlight for 10-foot TV experience.
 */
(function() {
    'use strict';

    if (window.__spatialNav) return;

    var FOCUS_CLASS = 'tv-spatial-focus-ring-atv';
    var SELECTOR = 'a[href], button, input:not([type="hidden"]), select, textarea, summary, ' +
                   '[tabindex]:not([tabindex="-1"]), [role="button"], [role="link"], ' +
                   '[role="menuitem"], [role="tab"], [role="checkbox"], [role="radio"], ' +
                   'video, audio';

    var currentFocus = null;

    // Inject CSS focus ring
    function injectStyle() {
        var style = document.createElement('style');
        style.type = 'text/css';
        style.innerHTML = '.' + FOCUS_CLASS + ' {' +
            'outline: 3.5px solid #FFFFFF !important;' +
            'outline-offset: 3px !important;' +
            'border-radius: 6px !important;' +
            'box-shadow: 0 0 20px rgba(255,255,255,0.7) !important;' +
            'transition: outline 0.12s ease !important;' +
            '}';
        (document.head || document.documentElement).appendChild(style);
    }
    injectStyle();

    function isVisible(el, rect) {
        if (!el || rect.width < 10 || rect.height < 10) return false;
        var style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) <= 0.05) return false;
        if (el.disabled || el.getAttribute('aria-hidden') === 'true') return false;
        return true;
    }

    function getCandidates() {
        var nodes = document.querySelectorAll(SELECTOR);
        var list = [];
        var vpW = window.innerWidth || document.documentElement.clientWidth;
        var vpH = window.innerHeight || document.documentElement.clientHeight;

        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            var rect = el.getBoundingClientRect();
            if (isVisible(el, rect)) {
                list.push({
                    el: el,
                    rect: {
                        left: rect.left,
                        top: rect.top,
                        right: rect.right,
                        bottom: rect.bottom,
                        width: rect.width,
                        height: rect.height,
                        cx: rect.left + rect.width / 2,
                        cy: rect.top + rect.height / 2
                    }
                });
            }
        }
        return list;
    }

    function isBeyond(dir, src, cand) {
        if (dir === 'up') return cand.bottom <= src.top + 2;
        if (dir === 'down') return cand.top >= src.bottom - 2;
        if (dir === 'left') return cand.right <= src.left + 2;
        if (dir === 'right') return cand.left >= src.right - 2;
        return false;
    }

    function isInBeam(dir, src, cand) {
        if (dir === 'up' || dir === 'down') {
            return cand.right > src.left && cand.left < src.right;
        }
        if (dir === 'left' || dir === 'right') {
            return cand.bottom > src.top && cand.top < src.bottom;
        }
        return false;
    }

    function majorDistance(dir, src, cand) {
        if (dir === 'up') return Math.max(0, src.top - cand.bottom);
        if (dir === 'down') return Math.max(0, cand.top - src.bottom);
        if (dir === 'left') return Math.max(0, src.left - cand.right);
        if (dir === 'right') return Math.max(0, cand.left - src.right);
        return 0;
    }

    function minorDistance(dir, src, cand) {
        if (dir === 'up' || dir === 'down') {
            return Math.abs(src.cx - cand.cx);
        }
        return Math.abs(src.cy - cand.cy);
    }

    function score(dir, src, cand) {
        var major = majorDistance(dir, src, cand);
        var minor = minorDistance(dir, src, cand);
        var beam = isInBeam(dir, src, cand) ? 0 : 1;
        // 13 * major^2 + minor^2 (FocusFinder metric)
        return (beam * 500000) + (13 * major * major) + (minor * minor);
    }

    function setFocus(el) {
        if (currentFocus) {
            currentFocus.classList.remove(FOCUS_CLASS);
        }
        currentFocus = el;
        if (currentFocus) {
            currentFocus.classList.add(FOCUS_CLASS);
            try { currentFocus.focus({ preventScroll: true }); } catch (e) { currentFocus.focus(); }
            currentFocus.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
        }
    }

    window.__spatialNav = {
        init: function() {
            if (currentFocus && document.body.contains(currentFocus)) return;
            var list = getCandidates();
            if (list.length > 0) {
                // Focus first visible item on screen
                var vpW = window.innerWidth;
                var vpH = window.innerHeight;
                for (var i = 0; i < list.length; i++) {
                    var r = list[i].rect;
                    if (r.top >= 0 && r.top < vpH && r.left >= 0 && r.left < vpW) {
                        setFocus(list[i].el);
                        return;
                    }
                }
                setFocus(list[0].el);
            }
        },

        move: function(dir) {
            if (!currentFocus || !document.body.contains(currentFocus)) {
                this.init();
                return true;
            }

            var srcRect = currentFocus.getBoundingClientRect();
            var src = {
                left: srcRect.left,
                top: srcRect.top,
                right: srcRect.right,
                bottom: srcRect.bottom,
                width: srcRect.width,
                height: srcRect.height,
                cx: srcRect.left + srcRect.width / 2,
                cy: srcRect.top + srcRect.height / 2
            };

            var candidates = getCandidates();
            var bestCand = null;
            var bestScore = Infinity;

            for (var i = 0; i < candidates.length; i++) {
                var c = candidates[i];
                if (c.el === currentFocus) continue;
                if (!isBeyond(dir, src, c.rect)) continue;

                var s = score(dir, src, c.rect);
                if (s < bestScore) {
                    bestScore = s;
                    bestCand = c;
                }
            }

            if (bestCand) {
                setFocus(bestCand.el);
                return true;
            } else {
                // No element found in direction, scroll the page
                var scrollAmount = Math.round(window.innerHeight * 0.45);
                if (dir === 'up') window.scrollBy({ top: -scrollAmount, behavior: 'smooth' });
                else if (dir === 'down') window.scrollBy({ top: scrollAmount, behavior: 'smooth' });
                else if (dir === 'left') window.scrollBy({ left: -200, behavior: 'smooth' });
                else if (dir === 'right') window.scrollBy({ left: 200, behavior: 'smooth' });
                return false;
            }
        },

        activate: function() {
            if (currentFocus) {
                currentFocus.click();
                var tag = currentFocus.tagName.toLowerCase();
                if (tag === 'input' || tag === 'textarea') {
                    currentFocus.focus();
                }
                return true;
            }
            return false;
        }
    };

    // Auto-init on load
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        window.__spatialNav.init();
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            window.__spatialNav.init();
        });
    }
})();
