;(function () {
    'use strict';
    if (window.HandyCodeCainiaoLayout || location.origin !== 'https://page.cainiao.com'
            || location.pathname !== '/cn-app-web/identity-code/index.html') return;

    var sheet = document.createElement('style');
    sheet.textContent = `
html[data-hc-cainiao-card],html[data-hc-cainiao-card] body{margin:0;background:#f5f6f8;overflow-x:hidden}
html[data-hc-cainiao-card] #root{position:fixed!important;left:0!important;top:0!important;width:100vw!important;transform:translateX(-200vw)!important;pointer-events:none!important}
#hc-cainiao-card[hidden]{display:none!important}
#hc-cainiao-card{box-sizing:border-box;margin:12px 10px 24px;border:1px solid #e9ecf0;border-radius:16px;background:#fff;color:#242b37;font:14px/1.5 system-ui,sans-serif}
#hc-cainiao-card header{box-sizing:border-box;height:53px;display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #f0f2f5}
#hc-cainiao-card h2{margin:0;font-size:16px;font-weight:650}
#hc-cainiao-card button{padding:5px 0;color:#536071;background:none;border:0;font:inherit;font-size:12px}
#hc-cainiao-card #identity-content{box-sizing:border-box;position:relative;min-width:0;height:var(--hc-identity-height,202px);padding:16px;display:flex;align-items:center;justify-content:center}
#hc-cainiao-card .hc-cainiao-scan{display:flex;flex-direction:column;width:100%;gap:12px;text-align:center}
#hc-cainiao-card .hc-cainiao-phone{font-size:16px;font-weight:550}
#hc-cainiao-card .hc-cainiao-expiry{font-size:12px;color:#697386}
#hc-cainiao-card .hc-cainiao-code{box-sizing:border-box;padding:8px;background:#fff;border-radius:8px}
#hc-cainiao-card [data-hc-barcode]{display:block;width:100%;height:86px;object-fit:contain;max-width:100%;margin:auto;cursor:pointer;touch-action:pan-y}
html[data-hc-cainiao-card][data-hc-cainiao-theme="dark"],html[data-hc-cainiao-card][data-hc-cainiao-theme="dark"] body{background:#0f1218}
html[data-hc-cainiao-theme="dark"] #hc-cainiao-card{background:#181c24;border-color:#262c36;color:#eceff4}
html[data-hc-cainiao-theme="dark"] #hc-cainiao-card header{border-bottom-color:#232833}
html[data-hc-cainiao-theme="dark"] #hc-cainiao-card button{color:#ff8e6b}
html[data-hc-cainiao-theme="dark"] #hc-cainiao-card .hc-cainiao-expiry{color:#949eb0}
`;
    (document.head || document.documentElement).appendChild(sheet);

    var card, barcode, phone, expiry, sourceRoot, zoom, lastGeometry = '';
    function reportGeometry(force) {
        if (!card || card.hidden) return;
        var data = {width:window.innerWidth,top:card.getBoundingClientRect().top,
            body:card.querySelector('#identity-content').getBoundingClientRect().height,
            heading:card.querySelector('header').getBoundingClientRect().height,
            expanded:barcode.getAttribute('aria-expanded') === 'true',refresh:!!force};
        var next = JSON.stringify(data);
        if (force || next !== lastGeometry) {
            lastGeometry = next;
            console.log('__HANDYCODE_CAINIAO_GEOMETRY__:' + next);
        }
    }
    function visible(node) {
        if (!node || !node.getClientRects().length) return false;
        var style = getComputedStyle(node);
        return style.display !== 'none' && style.visibility === 'visible' && Number(style.opacity) > 0;
    }

    function clearCard() {
        if (card) {
            if (!card.hidden) HandyPickupMotion.reset(card.querySelector('#identity-content'));
            card.hidden = true;
            barcode.removeAttribute('src');
        }
    }

    function showSource() {
        clearCard();
        document.documentElement.removeAttribute('data-hc-cainiao-card');
        if (sourceRoot) sourceRoot.removeAttribute('inert');
    }

    function updateText(element, selector, pattern) {
        var original = sourceRoot.querySelector(selector);
        var match = original && original.textContent.match(pattern);
        var text = match ? match[0] : '';
        if (element.textContent !== text) element.textContent = text;
        element.hidden = !text;
    }

    function mount() {
        card = document.createElement('section');
        card.id = 'hc-cainiao-card';
        card.hidden = true;
        card.innerHTML = '<header><h2>身份码</h2><button type="button">刷新</button></header>'
            + '<div id="identity-content"><div class="hc-cainiao-scan"><div class="hc-cainiao-phone" hidden></div>'
            + '<div class="hc-cainiao-code"><img data-hc-barcode alt="菜鸟身份码"></div>'
            + '<div class="hc-cainiao-expiry" hidden></div></div></div>';
        card.querySelector('button').addEventListener('click', function () {
            reportGeometry(true);
            this.disabled = true;
            location.reload();
        });
        barcode = card.querySelector('[data-hc-barcode]');
        phone = card.querySelector('.hc-cainiao-phone');
        expiry = card.querySelector('.hc-cainiao-expiry');
        document.body.appendChild(card);
        var content = card.querySelector('#identity-content');
        zoom = HandyPickupMotion.bindZoom(content, '[data-hc-barcode]');
    }

    window.HandyCodeCainiaoLayout = {
        restoreZoom: function (expanded) {
            if (!card || card.hidden) return;
            zoom.setExpanded(expanded);
            reportGeometry(false);
        },
        update: function (dark, restoreExpanded) {
            if (!document.body) return 'loading';
            document.documentElement.setAttribute('data-hc-cainiao-theme', dark ? 'dark' : 'light');
            if (HandyCodeOfficialIdentity.loginVisible(document)) {
                clearCard();
                return 'login';
            }
            if (visible(document.querySelector('[class*="modal-auth-auth-modal-body-"]'))) {
                showSource();
                return 'workflow';
            }
            var code = document.getElementById('auth-code-id');
            if (!visible(code) || !code.complete || !code.naturalWidth) {
                if (visible(document.querySelector('[class^="bind-phone-root-"]'))) {
                    showSource();
                    return 'phone';
                }
                clearCard();
                return 'loading';
            }
            sourceRoot = document.getElementById('root');
            if (!sourceRoot || !sourceRoot.contains(code)) return 'loading';
            if (!card) mount();
            var image = code.currentSrc || code.src;
            if (barcode.getAttribute('src') !== image) barcode.src = image;
            if (!barcode.complete || !barcode.naturalWidth) return 'loading';
            updateText(phone, '[class^="home-name-"]', /\b1\d{2}[*＊•]{4}\d{4}\b/);
            updateText(expiry, '[class^="info-bottom-root-"]', /\d+(?:分钟|秒)后自动刷新/);
            if (card.hidden) {
                card.hidden = false;
                var content = card.querySelector('#identity-content');
                HandyPickupMotion.replace(content, content.firstElementChild);
                if (restoreExpanded) zoom.setExpanded(true);
            }
            document.documentElement.setAttribute('data-hc-cainiao-card', '');
            sourceRoot.setAttribute('inert', '');
            reportGeometry(false);
            return 'ready';
        }
    };
})();
