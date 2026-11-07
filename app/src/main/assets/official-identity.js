(function (global) {
    'use strict';
    if (global.HandyCodeOfficialIdentity) return;
    var attempts = new WeakMap();
    var styleProps = ['display','box-sizing','color','background-color','background-image','background-size','background-position','background-repeat','font-family','font-size','font-weight','font-style','line-height','letter-spacing','text-align','text-decoration','white-space','word-break','padding-top','padding-right','padding-bottom','padding-left','margin-top','margin-right','margin-bottom','margin-left','border-top','border-right','border-bottom','border-left','border-radius','box-shadow','opacity','vertical-align','align-items','align-self','justify-content','flex-direction','flex-wrap','flex-grow','flex-shrink','flex-basis','gap','grid-template-columns','grid-template-rows','object-fit','overflow-wrap','width','height','min-width','min-height','max-width','max-height','position','top','right','bottom','left','transform'];
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
    function cloneIdentityNode(source, doc, budget) {
        if (--budget.count < 0) return null;
        if (source.nodeType === 3) return doc.createTextNode(source.nodeValue);
        if (source.nodeType !== 1 || /^(SCRIPT|STYLE|LINK|IFRAME|OBJECT|EMBED|FORM|INPUT|TEXTAREA|SELECT|NOSCRIPT)$/.test(source.tagName)) return null;
        var css = doc.defaultView.getComputedStyle(source);
        if (css.display === 'none' || css.visibility === 'hidden') return null;
        var copy;
        if (source.tagName === 'CANVAS') {
            copy = doc.createElement('img');
            try { copy.src = source.toDataURL(); } catch (e) { return null; }
        } else {
            copy = source.cloneNode(false);
            var origClass = source.className || '';
            Array.from(copy.attributes).forEach(function (a) {
                if (/^(on|data-)/i.test(a.name) || /^(srcdoc|formaction)$/i.test(a.name)) copy.removeAttribute(a.name);
            });
            if (copy.namespaceURI !== 'http://www.w3.org/2000/svg') {
                copy.removeAttribute('id');
                copy.removeAttribute('class');
            }
            if (copy.tagName === 'IMG') {
                if (source.currentSrc) copy.src = source.currentSrc;
                if (/width-origin--|width-rotate--/.test(origClass)) {
                    copy.className = 'width-origin--';
                }
            }
            if (copy.tagName === 'A') { copy.removeAttribute('href'); copy.removeAttribute('target'); }
            Array.from(source.childNodes).forEach(function (child) {
                var r = cloneIdentityNode(child, doc, budget);
                if (r) copy.appendChild(r);
            });
        }
        styleProps.forEach(function (name) { copy.style.setProperty(name, css.getPropertyValue(name), 'important'); });
        copy.style.setProperty('box-sizing', 'border-box', 'important');
        copy.style.setProperty('max-width', '100%', 'important');
        copy.style.setProperty('overflow', 'visible', 'important');
        copy.style.setProperty('touch-action', 'pan-y', 'important');
        if (copy.tagName === 'IMG' && copy.className === 'width-origin--') {
            [['display','block'],['width','100%'],['height','82px'],['object-fit','fill'],['transform','none'],['margin','8px 0']].forEach(function (p) {
                copy.style.setProperty(p[0], p[1], 'important');
            });
        }
        copy._hcOrigClass = source.className || '';
        return copy;
    }
    function captureInitial(doc) {
        try {
            var root = doc.querySelector('[class*="wraper--q4Mag5Cd"]') || doc.querySelector('[class*="identity--"]');
            if (!root) return '';
            var fresh = cloneIdentityNode(root, doc, { count: 2000 });
            if (!fresh) return '';
            [['position','static'],['width','100%'],['height','auto'],['min-height','0'],['max-height','none'],['margin','0'],['transform','none'],['display','flex'],['flex-direction','column'],['align-items','center'],['justify-content','center'],['text-align','center'],['padding','16px 8px'],['gap','12px']].forEach(function (p) {
                fresh.style.setProperty(p[0], p[1], 'important');
            });
            Array.from(fresh.children).forEach(function (child) {
                [['position','static'],['transform','none'],['left','auto'],['right','auto'],['margin','0'],['text-align','center'],['align-self','center']].forEach(function (p) {
                    child.style.setProperty(p[0], p[1], 'important');
                });
                if (child.tagName !== 'IMG' && child.tagName !== 'CANVAS') {
                    [['width','100%'],['height','auto'],['min-height','0'],['padding','0']].forEach(function (p) {
                        child.style.setProperty(p[0], p[1], 'important');
                    });
                    if (/have-authenticated/.test(child._hcOrigClass || '')) {
                        [['display','inline-flex'],['width','auto'],['align-items','center'],['justify-content','center']].forEach(function (p) {
                            child.style.setProperty(p[0], p[1], 'important');
                        });
                    }
                }
            });
            return fresh.outerHTML;
        } catch (ignore) {
            return '';
        }
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
                console.info('__HANDYCODE_PHASE__:' + current);
            }
            if (current === 'merged') state.observer.disconnect();
        }
        function scheduleNotify() {
            if (!state.queued) { state.queued = true; setTimeout(notify, 0); }
        }
        state.observer = new MutationObserver(scheduleNotify);
        state.observer.observe(doc.documentElement, { subtree: true, childList: true, attributes: true });
        if (typeof doc.addEventListener === 'function') doc.addEventListener('load', scheduleNotify, true);
        watchers.set(doc, state);
        notify();
    }
    global.HandyCodeOfficialIdentity = { loginVisible: loginVisible, ready: ready,
        refreshControl: refreshControl, recover: recover, authenticationFrameVisible: authenticationFrameVisible,
        captureInitial: captureInitial, phase: phase, watch: watch };
})(globalThis);