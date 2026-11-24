(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)sf-express\.com$/i.test(location.hostname) || window.top !== window) return null;

 function visible(el) {
  if (!el || !el.getClientRects().length) return false;
  var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
  return !cs || (cs.display !== 'none' && cs.visibility !== 'hidden' && cs.visibility !== 'collapse' && cs.opacity !== '0');
 }

 function hasAccountHeader() {
  // The official header renders .user-info only in its signed-in branch.
  var account = document.querySelector('.common-header .users .user-info .mysf');
  return visible(account) && !!(account.textContent || '').trim();
 }

 function getVisibleLoginModal() {
  try {
   var modals = document.querySelectorAll('.pop-wrap.login-wrap,[class*="login-modal"],[class*="loginModal"],[class*="login-dialog"]');
   for (var i = 0; i < modals.length; i++) {
    var el = modals[i];
    if (visible(el) && el.offsetWidth >= 80 && el.offsetHeight >= 80) return el;
   }
  } catch(ignore) {}
  return null;
 }
 function loginSnapshot(login) {
  if (!login) {
   var loginButton = document.querySelector('.empty-box-btn, .common-header .login');
   var bodyText = (document.body && (document.body.innerText || document.body.textContent)) || '';
   login = visible(document.querySelector('.common-header .users .login'))
    || /登录\s*[/／]\s*注册/.test(loginButton && (loginButton.innerText || loginButton.textContent) || '')
    || /登录\s*[/／]\s*注册会员可查询更多运单|立即登录\s*[/／]\s*注册/.test(bodyText);
  }
  return {href:location.href, login:login, ready:false, orders:[]};
 }

 function signedInSnapshot(ready, orders) {
  return {href:location.href, authenticated:true, login:false, ready:ready, orders:orders};
 }

 var loginOpen = !!getVisibleLoginModal() || /\/login/i.test(location.pathname);
 if (loginOpen || !hasAccountHeader()) {
  var hadReader = !!window.HandySfOrders;
  if (hadReader) {
   window.HandySfOrders.stop();
   delete window.HandySfOrders;
  }
  return loginSnapshot(loginOpen || hadReader);
 }

 // The header confirms login on other official pages too. Read orders only on the list.
 var onOrdersPage = /^\/chn\/sc\/waybill\/list\/?$/.test(location.pathname);
 if (!window.HandySfOrders && !onOrdersPage) return signedInSnapshot(false, []);
 if (!window.HandySfOrders) {
  var state = {authenticated:true, listReady:false, receiveSwitched:false, orders:[],
   byId:Object.create(null), probed:Object.create(null)};

  function clean(value, max) { return typeof value === 'string' ? value.slice(0, max) : ''; }

  function accepted(result) {
   return result && result.success !== false
    && (result.success === true || result.code === 0 || result.code === '0' || result.code === 200 || result.code === '200');
  }

  function autoSwitchToReceiveTab() {
   if (state.receiveSwitched || !state.authenticated || getVisibleLoginModal()) return;
   try {
    var tabs = document.querySelectorAll('.bill-tab > span, .tab-item, .el-tabs__item');
    for (var i = 0; i < tabs.length; i++) {
     var t = (tabs[i].innerText || '').trim();
     if (t === '\u6211\u6536\u7684' || t.indexOf('\u6211\u6536\u7684') === 0) {
      state.receiveSwitched = true;
      if (!tabs[i].classList || !tabs[i].classList.contains('active')) {
        tabs[i].click();
       } else if (!state.listReady && window.$nuxt && typeof window.$nuxt.refresh === 'function') {
        Promise.resolve(window.$nuxt.refresh()).catch(function () {});
       }
      break;
     }
    }
   } catch(ignore) {}
  }

  function code(text) {
   var s = String(text || '');
   var m = s.match(/(?:\u53d6\u4ef6\u7801|\u63d0\u8d27\u7801|\u53d6\u8d27\u7801|\u51ed\u7801|\u81ea\u63d0\u7801|\u53d6\u4ef6\u5bc6\u7801|\u5f00\u7bb1\u7801|\u94a5\u5319\u7801)[:\uff1a\s\u3010\[\uff08]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
   if (m) return m[1];
   var m2 = s.match(/(?:\u8bf7\u51ed|\u51ed)[:\uff1a\s\u3010\[\uff08]*([A-Za-z0-9-]{4,14})[\u3011\]\uff09\s]*(?:\u81f3|\u5230|\u524d\u5f80|\u53d6\u4ef6)/);
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
    item = { id: id, name: name || ('\u987a\u4e30\u5feb\u4ef6 ' + id.slice(-4)), status: status || '\u8fd0\u9001\u4e2d', detail: detail || ('\u8fd0\u5355\u53f7\uff1a' + id), code: c || '' };
    state.byId[id] = item;
    state.orders.push(item);
   } else {
    if (name) item.name = name;
    if (status) item.status = status;
    if (detail) item.detail = detail;
    if (c) item.code = c;
   }
  }

  function probeRoute(id) {
   if (!state.authenticated || !id || state.probed[id] || typeof window.fetch !== 'function') return;
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
   if (/\u5df2\u7b7e\u6536|\u5df2\u6d3e\u9001\u6210\u529f|\u5feb\u4ef6\u5df2\u7b7e\u6536/.test(latest + ' ' + status) && !foundCode) return;
   upsert(waybillNo, null, status || (foundCode ? '\u5f85\u53d6\u4ef6' : null), latest, foundCode);
  }

  function consume(root, path) {
   if (!root || typeof root !== 'object' || !state.authenticated
       || !path.startsWith('/sf-service-core-web/service/')) return;
   var res = root.result || root.obj || root.data;

   var isList = /\/waybillList\/getPaginatedWaybillList\/?$/i.test(path);
   if (isList && !accepted(root)) {
    var msg = String(root.message || root.errorMessage || root.msg || '');
    if (Number(root.code) === 121 || Number(root.code) === 401 || Number(root.errorCode) === 401
        || /未登录|登录失效|会话过期|重新登录|未授权|Unauthorized/i.test(msg)) {
     rejectSession();
    }
    return;
   }
   if (!accepted(root) || !res) return;
   var list = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : Array.isArray(res.list) ? res.list : res.records);
   if (isList && Array.isArray(list)) {
    state.listReady = true;
    list.slice(0, 30).forEach(function (w, idx) {
     if (!w) return;
     var id = clean(w.waybillno || w.waybillNo || w.id || '', 80);
     if (!id) return;
     var st = clean(w.waybillStatusText || w.statusText || w.waybillStatus || '', 60);
     var from = clean(w.originateCityName || w.consignerCity || '', 40);
     var to = clean(w.destinationCityName || w.addresseeCity || '', 40);
     var routeTitle = (from && to) ? ('\u987a\u4e30 ' + from + ' \u2192 ' + to) : ('\u987a\u4e30\u5feb\u4ef6 ' + id.slice(-4));
     var remark = clean(w.latestRoute || w.remark || w.pickupAddr || '', 500);
     var c = code((w.pickupCode ? '\u53d6\u4ef6\u7801' + w.pickupCode : '') + ' ' + remark + ' ' + st);
     upsert(id, routeTitle, st || '\u5f85\u67e5\u6536', remark || ('\u8fd0\u5355\u53f7\uff1a' + id), c);
     if (idx < 12 && !/\u5df2\u7b7e\u6536|\u5df2\u6d3e\u9001\u6210\u529f|\u5b8c\u6210/.test(st)) probeRoute(id);
    });
    return;
   }

   if (!/\/waybill(?:Route)?\//i.test(path)) return;
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
    if (wid && (addr || c2)) upsert(wid, null, c2 ? '\u5f85\u53d6\u4ef6' : null, addr, c2);
   }
  }

  function rejectSession() {
   state.authenticated = false;
   state.listReady = false;
   state.orders = [];
   if (XMLHttpRequest.prototype.send === observedSend) XMLHttpRequest.prototype.send = originalSend;
   if (window.fetch === observedFetch) window.fetch = originalFetch;
  }

  function response(text, path) {
   if (typeof text !== 'string' || text.length > 2097152 || text.trimStart().charAt(0) !== '{') return;
   try { consume(JSON.parse(text), path); } catch (ignored) {}
  }

  function orderPath(address) {
   try {
    var url = new URL(address, location.href);
    if (url.origin === location.origin
        && /^\/sf-service-core-web\/service\/waybill(?:List|Route)?\//i.test(url.pathname)) return url.pathname;
   } catch (ignored) {}
   return '';
  }

  var originalSend = XMLHttpRequest.prototype.send;
  function observedSend() {
   this.addEventListener('load', function () {
    var path = orderPath(this.responseURL);
    if (!path || !state.authenticated) return;
    try {
     if (this.responseType === 'json') consume(this.response, path);
     else if (!this.responseType || this.responseType === 'text') response(this.responseText, path);
    } catch (ignored) {}
   });
   return originalSend.apply(this, arguments);
  }
  XMLHttpRequest.prototype.send = observedSend;

  var originalFetch = window.fetch;
  function observedFetch() {
   var promise = originalFetch.apply(this, arguments);
   promise.then(function (res) {
    var path = orderPath(res.url);
    if (path && state.authenticated) {
     res.clone().text().then(function (text) { response(text, path); }).catch(function () {});
    }
   }).catch(function () {});
   return promise;
  }
  if (typeof originalFetch === 'function') window.fetch = observedFetch;

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
    rows.push({ id: id, name: wm ? ('\u987a\u4e30\u5feb\u4ef6 ' + id.slice(-4)) : '\u987a\u4e30\u5f85\u53d6\u5feb\u4ef6', status: '\u5f85\u53d6\u4ef6', detail: text.replace(/\s+/g, ' ').slice(0, 400), code: c });
   }
   return rows;
  }

  window.HandySfOrders = {
   stop: rejectSession,
   snapshot: function (readOrders) {
    if (!state.authenticated) return loginSnapshot(true);
    if (!readOrders) return signedInSnapshot(false, []);
    autoSwitchToReceiveTab();
    var rows = state.listReady ? state.orders : domOrders();
    return signedInSnapshot(state.listReady || rows.length > 0, rows);
   }
  };
 }
 return window.HandySfOrders.snapshot(onOrdersPage);
})()
