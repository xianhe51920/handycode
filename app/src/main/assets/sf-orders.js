(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)sf-express\.com$/i.test(location.hostname) || window.top !== window) return null;

 function adaptSfMobile(){
  try{
   if(!document.head)return;
   var vp=document.querySelector('meta[name="viewport"]');
   if(!vp){vp=document.createElement('meta');vp.name='viewport';document.head.appendChild(vp);}
   var targetVp='width=device-width,initial-scale=1,maximum-scale=2,user-scalable=yes';
   if(vp.getAttribute('content')!==targetVp)vp.setAttribute('content',targetVp);
   if(!document.getElementById('hc-sf-mobile-style')){
    var st=document.createElement('style');
    st.id='hc-sf-mobile-style';
    st.textContent=
     '.sidebar-layout > .left,.common-header .menu-wrap,.float-bar,[class*="float-bar"],.footer,[class*="footer-wrap"],[class*="cookie"],.guide-operate{display:none!important}'+
     'html,body,#__nuxt,#__layout,#app,#chn,.sidebar-layout,.sidebar-layout > .right,.waybill,.common-header,.header-wrap{width:100%!important;min-width:0!important;max-width:100vw!important;box-sizing:border-box!important;overflow-x:hidden!important}'+
     '.common-header .header-wrap{display:flex!important;justify-content:space-between!important;align-items:center!important;padding:0 12px!important}'+
     '.sidebar-layout{display:block!important;padding:8px!important;margin:0!important}'+
     '.sidebar-layout > .right{padding:0!important;margin:0!important}'+
     '.pop-wrap.login-wrap{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;z-index:9999!important}'+
     '.pop-wrap.login-wrap .main-box{max-width:92vw!important;width:92vw!important;min-width:0!important;max-height:88vh!important;overflow-y:auto!important;box-sizing:border-box!important}';
    document.head.appendChild(st);
   }
  }catch(ignore){}
 }

 adaptSfMobile();

 try {
  var savedUser = typeof localStorage !== 'undefined' ? localStorage.getItem('hc_sf_login_user') : null;
  if (savedUser && !/(?:^|;\s*)loginUser=/.test(document.cookie || '')) {
   var exp = new Date(Date.now() + 30 * 24 * 3600 * 1000).toUTCString();
   document.cookie = 'loginUser=' + encodeURIComponent(savedUser) + '; path=/; domain=.sf-express.com; expires=' + exp + '; Secure; SameSite=Lax';
   document.cookie = 'loginUser=' + encodeURIComponent(savedUser) + '; path=/; expires=' + exp + '; Secure; SameSite=Lax';
  }
 } catch(ignore) {}

 if (!window.HandySfOrders) {
  var state = {
   captured: false,
   waybillVerified: false,
   authRejected: false,
   sessionProbed: false,
   login: false,
   loginSuccessAt: 0,
   receiveSwitched: false,
   orders: [],
   byId: Object.create(null),
   probed: Object.create(null),
   revision: 0
  };

  function clean(value, max) { return typeof value === 'string' ? value.slice(0, max) : ''; }
  function defer(fn, ms) { if (typeof setTimeout === 'function') setTimeout(fn, ms); }

  function wasRecentlyLoggedIn() {
   try {
    var t = parseInt(sessionStorage.getItem('hc_sf_logged') || '0', 10);
    if (t > 0 && (Date.now() - t < 60000)) return true;
   } catch(ignore) {}
   return state.loginSuccessAt > 0 && (Date.now() - state.loginSuccessAt < 60000);
  }

  function hasLoginCookie() {
   try {
    var raw = document.cookie || '';
    var m = raw.match(/(?:^|;\s*)loginUser=([^;]*)/);
    if (m && m[1]) {
     var v = decodeURIComponent(m[1]).trim();
     return Boolean(v && v !== 'null' && v !== 'undefined' && v !== 'false' && v !== '""' && v !== "''" && v !== '0');
    }
   } catch(ignore) {}
   return false;
  }

  function getVisibleLoginModal() {
   try {
    var modals = document.querySelectorAll('.pop-wrap.login-wrap,.pop-wrap,[class*="login-modal"],[class*="loginModal"],[class*="login-dialog"]');
    for (var i = 0; i < modals.length; i++) {
     var el = modals[i];
     if (!el || !el.getClientRects || el.getClientRects().length === 0) continue;
     if ((el.offsetWidth || 0) < 80 || (el.offsetHeight || 0) < 80) continue;
     var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
     if (cs && (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0')) continue;
     return el;
    }
   } catch(ignore) {}
   return null;
  }

  function preferSmsTabInModal(modal) {
   if (!modal || modal.getAttribute('data-hc-tab-init') === '1') return;
   try {
    var tabs = modal.querySelectorAll('.tab, .tab-item, [class*="tab"]');
    for (var i = 0; i < tabs.length; i++) {
     var t = (tabs[i].innerText || '').trim();
     if (t === '账号登录' || t === '短信登录' || t === '验证码登录') {
      modal.setAttribute('data-hc-tab-init', '1');
      var target = tabs[i];
      defer(function () { try { target.click(); } catch(e) {} }, 120);
      return;
     }
    }
   } catch(ignore) {}
  }

  function openSfLoginModal() {
   try {
    var existing = getVisibleLoginModal();
    if (existing) {
     preferSmsTabInModal(existing);
     return true;
    }
    var nuxt = window.$nuxt;
    if (nuxt && typeof nuxt.$loginShow === 'function') {
     try {
      nuxt.$loginShow({});
      defer(function () { preferSmsTabInModal(getVisibleLoginModal()); }, 350);
      return true;
     } catch(e) {}
    }
    var targets = document.querySelectorAll('button.empty-box-btn, .empty-box-btn, span.login, .users .login, .users.cursor-point, [class*="login"]');
    for (var i = 0; i < targets.length; i++) {
     var el = targets[i];
     var txt = (el.innerText || '').trim();
     if (el.tagName === 'BUTTON' || el.tagName === 'SPAN' || el.tagName === 'A' || el.classList.contains('cursor-point') || el.classList.contains('login') || el.classList.contains('empty-box-btn')) {
      if (/登录/.test(txt) && !/扫码|说明|协议|政策/.test(txt)) {
       el.click();
       defer(function () { preferSmsTabInModal(getVisibleLoginModal()); }, 400);
       return true;
      }
     }
    }
   } catch(ignore) {}
   return false;
  }

  if (document.addEventListener) {
   document.addEventListener('click', function (ev) {
    try {
     var el = ev && ev.target;
     while (el && el !== document.body) {
      var txt = clean((el.innerText || '').trim(), 30);
      if (/登录\/注册|立即登录\/注册/.test(txt)) {
       defer(function () { preferSmsTabInModal(getVisibleLoginModal()); }, 400);
       break;
      }
      el = el.parentElement;
     }
    } catch(ignore) {}
   }, true);
  }

  function markFreshLogin() {
   state.loginSuccessAt = Date.now();
   try { sessionStorage.setItem('hc_sf_logged', Date.now().toString()); } catch(e) {}
   try {
    var raw = document.cookie || '';
    var m = raw.match(/(?:^|;\s*)loginUser=([^;]*)/);
    if (m && m[1]) {
     var u = decodeURIComponent(m[1]).trim();
     if (u && typeof localStorage !== 'undefined') localStorage.setItem('hc_sf_login_user', u);
    }
   } catch(e) {}
   state.authRejected = false;
   state.waybillVerified = true;
   state.captured = true;
   state.login = false;
  }

  function autoSwitchToReceiveTab() {
   if (state.receiveSwitched || state.login || (!state.waybillVerified && !wasRecentlyLoggedIn())) return;
   try {
    var tabs = document.querySelectorAll('.tab-item,.el-tabs__item,[class*="tab"]');
    for (var i = 0; i < tabs.length; i++) {
     var t = (tabs[i].innerText || '').trim();
     if (t === '我收的' || t.indexOf('我收的') === 0) {
      state.receiveSwitched = true;
      var targetTab = tabs[i];
      defer(function () { try { targetTab.click(); } catch(e) {} }, 500);
      break;
     }
    }
   } catch(ignore) {}
  }

  function code(text) {
   var s = String(text || '');
   var m = s.match(/(?:取件码|提货码|取货码|凭码|自提码|取件密码|开箱码|钥匙码)[:：\s【\[（]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
   if (m) return m[1];
   var m2 = s.match(/(?:请凭|凭)[:：\s【\[（]*([A-Za-z0-9-]{4,14})[】\]）\s]*(?:至|到|前往|取件)/);
   if (m2) return m2[1];
   var shelf = s.match(/(?:^|[^\dA-Za-z-])([A-Za-z0-9]{1,4}-\d{1,3}-\d{2,6})(?![A-Za-z0-9-])/);
   if (shelf && !/^20\d\d-\d{1,2}-\d{1,2}$/.test(shelf[1])) return shelf[1];
   return '';
  }

  function upsert(id, name, status, detail, c) {
   if (!id) return;
   id = String(id).slice(0, 80);
   var item = state.byId[id];
   if (!item) {
    item = { id: id, name: name || ('顺丰快件 ' + id.slice(-4)), status: status || '运送中', detail: detail || ('运单号：' + id), code: c || '' };
    state.byId[id] = item;
    state.orders.push(item);
   } else {
    if (name) item.name = name;
    if (status) item.status = status;
    if (detail) item.detail = detail;
    if (c) item.code = c;
   }
   state.revision++;
  }

  function probeRoute(id) {
   if (!id || state.probed[id] || typeof window.fetch !== 'function') return;
   state.probed[id] = true;
   var safe = encodeURIComponent(id);
   window.fetch('/sf-service-core-web/service/waybillRoute/' + safe + '/routes', { credentials: 'include' }).catch(function () {});
   window.fetch('/sf-service-core-web/service/waybill/' + safe + '/detail', { credentials: 'include' }).catch(function () {});
  }

  function consumeRoutes(waybillNo, routes) {
   if (!waybillNo || !Array.isArray(routes) || routes.length === 0) return;
   var latest = '', foundCode = '', status = '';
   for (var i = routes.length - 1; i >= 0; i--) {
    var r = routes[i];
    if (!r) continue;
    var remark = clean(r.remark || r.acceptAddress || r.routeRemark || r.desc || '', 500);
    if (!latest && remark) latest = remark;
    if (!status) status = clean(r.opCodeDesc || r.statusName || r.scanType || '', 40);
    var c = code(remark);
    if (c) { foundCode = c; latest = remark; break; }
   }
   if (/已签收|已派送成功|快件已签收/.test(latest + ' ' + status) && !foundCode) return;
   upsert(waybillNo, null, status || (foundCode ? '待取件' : null), latest, foundCode);
  }

  function consume(root, urlPath) {
   if (!root || typeof root !== 'object') return;
   var path = String(urlPath || '');
   var res = root.result || root.obj || root.data;

   // 1. QR status check
   if (/qrcode\/loginCheck/i.test(path)) {
    if ((root.success === true || String(root.code) === '0') && res && String(res.status) === '3') {
     markFreshLogin();
    }
    return;
   }

   // 2. SMS / Password / Account Login
   if (/\/(smsLogin|pwdLogin|accountLogin)(\b|\/|\?|$)/i.test(path)) {
    if (root.success === true || String(root.code) === '0') {
     markFreshLogin();
    }
    return;
   }

   // 3. Auth rejection (session expired / logged out on server)
   if (root.success === false || (root.code !== undefined && String(root.code) !== '0' && String(root.code) !== '200') || Number(root.errorCode) === 401) {
    var msg = String(root.message || root.errorMessage || root.msg || root.detailMessage || root.errorCode || '');
    var codeNum = Number(root.code);
    if (codeNum === 121 || Number(root.errorCode) === 401 || /未登录|登录失效|会话过期|重新登录|未授权|Unauthorized|auto login failed/i.test(msg)) {
     if (!wasRecentlyLoggedIn()) {
      state.authRejected = true;
      state.waybillVerified = false;
      state.captured = false;
      state.login = true;
      state.orders = [];
      state.byId = Object.create(null);
      try { sessionStorage.removeItem('hc_sf_logged'); } catch(e) {}
      try { if (typeof localStorage !== 'undefined') localStorage.removeItem('hc_sf_login_user'); } catch(e) {}
     }
    }
    return;
   }

   // 4. Successful waybill / route response
   if ((root.success === true || String(root.code) === '0') && /waybillList|waybillRoute|waybill\/|member-multiple-account\/list/i.test(path)) {
    state.authRejected = false;
    state.waybillVerified = true;
    state.captured = true;
    state.login = false;
   }

   if (!res) return;
   var list = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : (Array.isArray(res.list) ? res.list : (Array.isArray(res.records) ? res.records : null)));
   if (list && (/waybillList|waybill/i.test(path) || (list[0] && (list[0].waybillno || list[0].waybillNo)))) {
    state.authRejected = false;
    state.waybillVerified = true;
    state.captured = true;
    state.login = false;
    list.slice(0, 30).forEach(function (w, idx) {
     if (!w) return;
     var id = clean(w.waybillno || w.waybillNo || w.id || '', 80);
     if (!id) return;
     var st = clean(w.waybillStatusText || w.statusText || w.waybillStatus || '', 60);
     var from = clean(w.originateCityName || w.consignerCity || '', 40);
     var to = clean(w.destinationCityName || w.addresseeCity || '', 40);
     var routeTitle = (from && to) ? ('顺丰 ' + from + ' → ' + to) : ('顺丰快件 ' + id.slice(-4));
     var remark = clean(w.latestRoute || w.remark || w.pickupAddr || '', 500);
     var c = code((w.pickupCode ? '取件码' + w.pickupCode : '') + ' ' + remark + ' ' + st);
     upsert(id, routeTitle, st || '待查收', remark || ('运单号：' + id), c);
     if (idx < 12 && !/已签收|已派送成功|完成/.test(st)) probeRoute(id);
    });
    return;
   }

   if (Array.isArray(res.routes)) {
    var m = path.match(/waybillRoute\/([^\/]+)\/routes/i);
    consumeRoutes(m ? decodeURIComponent(m[1]) : (res.waybillNo || res.id || 'SF'), res.routes);
   } else if (Array.isArray(res.waybillRoutes)) {
    res.waybillRoutes.forEach(function (wr) { if (wr) consumeRoutes(wr.id || wr.waybillNo, wr.routes); });
   } else if (res.pickupAddr || res.pickupCode || res.waybillNo || res.waybillno) {
    var wid = clean(res.waybillNo || res.waybillno || '', 80);
    var m2 = path.match(/waybill\/([^\/]+)\/detail/i);
    if (!wid && m2) wid = decodeURIComponent(m2[1]);
    var addr = clean(res.pickupAddr || res.remark || '', 500);
    var c2 = clean(res.pickupCode || '', 20) || code(addr);
    if (wid && (addr || c2)) upsert(wid, null, c2 ? '待取件' : null, addr, c2);
   }
  }

  function response(text, urlPath) {
   if (typeof text !== 'string' || text.length > 2097152 || text.charAt(0) !== '{') return;
   try { consume(JSON.parse(text), urlPath); } catch(ignore) {}
  }

  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
   this.addEventListener('load', function () {
    try {
     var url = new URL(this.responseURL, location.href);
     if (url.protocol !== 'https:' || !/(^|\.)sf-express\.com$/i.test(url.hostname)) return;
     if (this.responseType === 'json') consume(this.response, url.pathname);
     else if (!this.responseType || this.responseType === 'text') response(this.responseText, url.pathname);
    } catch(ignore) {}
   });
   return send.apply(this, arguments);
  };

  var fetchOriginal = window.fetch;
  if (fetchOriginal) {
   window.fetch = function () {
    var promise = fetchOriginal.apply(this, arguments);
    promise.then(function (res) {
     try {
      var url = new URL(res.url, location.href);
      if (url.protocol === 'https:' && /(^|\.)sf-express\.com$/i.test(url.hostname)) {
       res.clone().text().then(function (t) { response(t, url.pathname); }).catch(function () {});
      }
     } catch(ignore) {}
    }).catch(function () {});
    return promise;
   };
  }

  function domOrders() {
   var nodes = document.querySelectorAll('[class*="waybill"],[class*="route"],li'), rows = [], seen = Object.create(null);
   for (var i = 0; i < nodes.length && rows.length < 40; i++) {
    var text = (nodes[i].innerText || '').slice(0, 1500);
    var c = code(text);
    if (!c) continue;
    var wm = text.match(/SF[0-9]{10,15}/i);
    var id = wm ? wm[0].toUpperCase() : ('sf_dom_' + c);
    if (seen[id]) continue;
    seen[id] = true;
    rows.push({ id: id, name: wm ? ('顺丰快件 ' + id.slice(-4)) : '顺丰待取快件', status: '待取件', detail: text.replace(/\s+/g, ' ').slice(0, 400), code: c });
   }
   return rows;
  }

  window.HandySfOrders = {
   openLogin: openSfLoginModal,
   snapshot: function () {
    adaptSfMobile();
    var modal = getVisibleLoginModal();
    if (modal) {
     preferSmsTabInModal(modal);
     return { href: location.href, login: true, ready: false, orders: [] };
    }
    if (/\/login/i.test(location.pathname)) {
     return { href: location.href, login: true, ready: false, orders: [] };
    }
    if (state.authRejected && !wasRecentlyLoggedIn()) {
     return { href: location.href, login: true, ready: false, orders: [] };
    }

    var domRows = domOrders();
    var rows = state.orders.length > 0 ? state.orders : domRows;

    if (wasRecentlyLoggedIn() || state.waybillVerified || state.captured || rows.length > 0) {
     autoSwitchToReceiveTab();
     return { href: location.href, login: false, ready: true, orders: rows };
    }

    var emptyBtn = document.querySelector('.empty-box-btn, [class*="empty"] button');
    var isLoggedOutBtn = Boolean(emptyBtn && /\u767b\u5f55/.test(emptyBtn.innerText || ''));
    var headerLogin = document.querySelector('.common-header .login, .header-wrap [class*="login"], span.login');
    var isLoggedOutHeader = Boolean(headerLogin && /\u767b\u5f55/.test(headerLogin.innerText || ''));
    if ((isLoggedOutBtn || isLoggedOutHeader) && !wasRecentlyLoggedIn() && !hasLoginCookie()) {
     state.login = true;
     return { href: location.href, login: true, ready: false, orders: [] };
    }

    if (hasLoginCookie()) {
     try {
      var raw = document.cookie || '';
      var m = raw.match(/(?:^|;\s*)loginUser=([^;]*)/);
      if (m && m[1]) {
       var u = decodeURIComponent(m[1]).trim();
       if (u && typeof localStorage !== 'undefined') localStorage.setItem('hc_sf_login_user', u);
      }
     } catch(e) {}
     return { href: location.href, login: false, ready: false, orders: [] };
    }

    if ((isLoggedOutBtn || isLoggedOutHeader) && !wasRecentlyLoggedIn()) {
     state.login = true;
     return { href: location.href, login: true, ready: false, orders: [] };
    }

    return { href: location.href, login: false, ready: false, orders: [] };
   }
  };
 }
 return window.HandySfOrders.snapshot();
})()