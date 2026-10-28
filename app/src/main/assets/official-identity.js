(function (global) {
    'use strict';
    if (global.HandyCodeOfficialIdentity) return;
    var attempts = new WeakMap();
    function authenticationFrameVisible(doc) {
        var frames = doc.querySelectorAll('iframe');
        for (var i = 0; i < frames.length; i++) {
            var frame = frames[i];
            if (!frame.getClientRects().length || !frame.getBoundingClientRect) continue;
            var rect = frame.getBoundingClientRect(), view = doc.defaultView;
            if (rect.width <= 0 || rect.height <= 0 || rect.right <= 0 || rect.bottom <= 0
                    || view && (rect.left >= view.innerWidth || rect.top >= view.innerHeight)) continue;
            var hidden = false;
            for (var node = frame; node; node = node.parentElement) {
                var style = view && view.getComputedStyle ? view.getComputedStyle(node) : {};
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') { hidden = true; break; }
            }
            if (hidden) continue;
            var raw = frame.getAttribute ? frame.getAttribute('src') || '' : frame.src || '';
            if (!raw || raw === 'about:blank') {
                if (view && rect.width >= view.innerWidth * 0.8 && rect.height >= view.innerHeight * 0.5) return true;
                continue;
            }
            try {
                var url = new URL(raw, doc.baseURI || undefined);
                if (/(^|\.)(taobao|tmall|alipay|cainiao)\.com$/.test(url.hostname)
                        && (/^(login|passport)\./.test(url.hostname) || /(^|\/)login(\.|\/|$)/.test(url.pathname))) return true;
            } catch (ignore) {}
        }
        return false;
    }
    function loginVisible(doc) {
        // Login is hosted in a cross-origin iframe. Its input fields do not
        // appear in the parent document, including while the iframe is loading.
        if (authenticationFrameVisible(doc)) return true;
        var controls = doc.querySelectorAll('input,textarea,[contenteditable="true"],[role="textbox"]');
        for (var i = 0; i < controls.length; i++) {
            var item = controls[i], type = (item.type || '').toLowerCase();
            if (/^(hidden|checkbox|radio|button|submit|reset|image|range)$/.test(type)) continue;
            var style = doc.defaultView && doc.defaultView.getComputedStyle ? doc.defaultView.getComputedStyle(item) : {};
            if (!item.getClientRects().length || style.display === 'none' || style.visibility === 'hidden') continue;
            if (doc.activeElement === item || style.opacity !== '0') return true;
        }
        return false;
    }
    function ready(doc) {
        if (doc.getElementById('handycode-merged')) return true;
        if (loginVisible(doc)) return false;
        var root = doc.querySelector('[class*="identity--"]');
        if (!root) return false;
        var value = root.querySelector('[class*="code--"]');
        var image = root.querySelector('img[class*="width-origin--"],img[class*="width-rotate--"]');
        return Boolean(value && value.childNodes.length > 0 && image && image.complete && image.naturalWidth > 0);
    }
    function refreshControl(doc) {
        var root = doc.querySelector('[class*="identity--"]');
        var bar = root && root.querySelector('[class*="refresh--"]');
        if (!bar) return null;
        var nodes = bar.querySelectorAll('button,[role="button"],span,div');
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            if (node.textContent.trim() !== '刷新' || !node.getClientRects().length) continue;
            var button = node.closest ? node.closest('button,[role="button"]') || node : node;
            if (!button.disabled && (!button.getAttribute || button.getAttribute('aria-disabled') !== 'true')
                    && typeof button.click === 'function') return button;
        }
        return null;
    }
    function recover(doc, now) {
        if (ready(doc)) { attempts.delete(doc); return 'ready'; }
        if (loginVisible(doc)) { attempts.delete(doc); return 'login'; }
        var button = refreshControl(doc);
        if (!button) return 'waiting';
        var state = attempts.get(doc);
        if (!state) { state = { since: now, last: null, count: 0 }; attempts.set(doc, state); }
        if (state.count >= 3) return 'exhausted';
        // Let login/React settle first, then invoke the same official control
        // the user showed in their screenshot. Never call a private API.
        if (now - state.since < 1200 || state.last !== null && now - state.last < 6000) return 'waiting';
        state.last = now; state.count++;
        button.click();
        return 'triggered';
    }
    function phase(doc) {
        if (doc.getElementById('handycode-merged')) return 'merged';
        if (loginVisible(doc)) return 'login';
        return ready(doc) ? 'ready' : 'loading';
    }
    var watchers = new WeakMap();
    function watch(doc) {
        if (!doc.documentElement || !global.MutationObserver) return;
        var previous = watchers.get(doc);
        if (previous && previous.root === doc.documentElement) return;
        if (previous) previous.observer.disconnect();
        var state = { root: doc.documentElement, last: null, queued: false };
        function notify() {
            state.queued = false;
            var current = phase(doc);
            if (current !== state.last) {
                state.last = current;
                // Only a fixed phase enum crosses to the native presentation
                // layer. No page text, URLs, form values or account data.
                console.info('__HANDYCODE_PHASE__:' + current);
            }
            if (current === 'merged') state.observer.disconnect();
        }
        state.observer = new MutationObserver(function () {
            if (!state.queued) { state.queued = true; setTimeout(notify, 0); }
        });
        state.observer.observe(doc.documentElement, { subtree: true, childList: true, attributes: true });
        watchers.set(doc, state);
        notify();
    }
    global.HandyCodeOfficialIdentity = { loginVisible: loginVisible, ready: ready,
        refreshControl: refreshControl, recover: recover, authenticationFrameVisible: authenticationFrameVisible,
        phase: phase, watch: watch };
})(globalThis);
