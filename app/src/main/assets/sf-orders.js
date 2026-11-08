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

  function getSavedUser() {
   try {
    return typeof localStorage !== 'undefined' ? localStorage.getItem('hc_sf_login_user') : null;
   } catch(e) { return null; }
  }

  function hasRememberMe() {
   try {
    var raw = document.cookie || '';
    return /(?:^|;\s*)remember-me=/.test(raw);
   } catch(e) { return false; }
  }

  var autoLoginState = {
   inFlight: false,
   attempted: 0,
   lastAttemptAt: 0
  };

  function md5(string) {
   function rotateLeft(lValue, iShiftBits) {
    return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
   }
   function addUnsigned(lX, lY) {
    var lX4 = lX & 0x40000000, lY4 = lX & 0x40000000;
    var lX8 = lX & 0x80000000, lY8 = lY & 0x80000000;
    var lResult = (lX & 0x3fffffff) + (lY & 0x3fffffff);
    if (lX4 & lY4) return lResult ^ 0x80000000 ^ lX8 ^ lY8;
    if (lX4 | lY4) return (lResult & 0x40000000) ? (lResult ^ 0xc0000000 ^ lX8 ^ lY8) : (lResult ^ 0x40000000 ^ lX8 ^ lY8);
    return lResult ^ lX8 ^ lY8;
   }
   function F(x, y, z) { return (x & y) | (~x & z); }
   function G(x, y, z) { return (x & z) | (y & ~z); }
   function H(x, y, z) { return x ^ y ^ z; }
   function I(x, y, z) { return y ^ (x | ~z); }
   function FF(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
   }
   function GG(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
   }
   function HH(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
   }
   function II(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
   }
   function convertToWordArray(str) {
    var len = str.length;
    var words = Array(((len + 8 - ((len + 8) % 64)) / 64 + 1) * 16);
    for (var i = 0; i < words.length; i++) words[i] = 0;
    for (var j = 0; j < len; j++) words[j >> 2] |= (str.charCodeAt(j) & 0xff) << ((j % 4) * 8);
    words[len >> 2] |= 0x80 << ((len % 4) * 8);
    words[words.length - 2] = len << 3;
    words[words.length - 1] = len >>> 29;
    return words;
   }
   function wordToHex(lValue) {
    var str = '';
    for (var i = 0; i <= 3; i++) {
     var b = (lValue >>> (i * 8)) & 255;
     str += (b < 16 ? '0' : '') + b.toString(16);
    }
    return str;
   }
   var x = convertToWordArray(string);
   var a = 0x67452301, b = 0xefcdab89, c = 0x98badcfe, d = 0x10325476;
   var S11 = 7, S12 = 12, S13 = 17, S14 = 22;
   var S21 = 5, S22 = 9, S23 = 14, S24 = 20;
   var S31 = 4, S32 = 11, S33 = 16, S34 = 23;
   var S41 = 6, S42 = 10, S43 = 15, S44 = 21;
   for (var k = 0; k < x.length; k += 16) {
    var AA = a, BB = b, CC = c, DD = d;
    a = FF(a, b, c, d, x[k + 0], S11, 0xd76aa478);
    d = FF(d, a, b, c, x[k + 1], S12, 0xe8c7b756);
    c = FF(c, d, a, b, x[k + 2], S13, 0x242070db);
    b = FF(b, c, d, a, x[k + 3], S14, 0xc1bdceee);
    a = FF(a, b, c, d, x[k + 4], S11, 0xf57c0faf);
    d = FF(d, a, b, c, x[k + 5], S12, 0x4787c62a);
    c = FF(c, d, a, b, x[k + 6], S13, 0xa8304613);
    b = FF(b, c, d, a, x[k + 7], S14, 0xfd469501);
    a = FF(a, b, c, d, x[k + 8], S11, 0x698098d8);
    d = FF(d, a, b, c, x[k + 9], S12, 0x8b44f7af);
    c = FF(c, d, a, b, x[k + 10], S13, 0xffff5bb1);
    b = FF(b, c, d, a, x[k + 11], S14, 0x895cd7be);
    a = FF(a, b, c, d, x[k + 12], S11, 0x6b901122);
    d = FF(d, a, b, c, x[k + 13], S12, 0xfd987193);
    c = FF(c, d, a, b, x[k + 14], S13, 0xa679438e);
    b = FF(b, c, d, a, x[k + 15], S14, 0x49b40821);
    a = GG(a, b, c, d, x[k + 1], S21, 0xf61e2562);
    d = GG(d, a, b, c, x[k + 6], S22, 0xc040b340);
    c = GG(c, d, a, b, x[k + 11], S23, 0x265e5a51);
    b = GG(b, c, d, a, x[k + 0], S24, 0xe9b6c7aa);
    a = GG(a, b, c, d, x[k + 5], S21, 0xd62f105d);
    d = GG(d, a, b, c, x[k + 10], S22, 0x2441453);
    c = GG(c, d, a, b, x[k + 15], S23, 0xd8a1e681);
    b = GG(b, c, d, a, x[k + 4], S24, 0xe7d3fbc8);
    a = GG(a, b, c, d, x[k + 9], S21, 0x21e1cde6);
    d = GG(d, a, b, c, x[k + 14], S22, 0xc33707d6);
    c = GG(c, d, a, b, x[k + 3], S23, 0xf4d50d87);
    b = GG(b, c, d, a, x[k + 8], S24, 0x455a14ed);
    a = GG(a, b, c, d, x[k + 13], S21, 0xa9e3e905);
    d = GG(d, a, b, c, x[k + 2], S22, 0xfcefa3f8);
    c = GG(c, d, a, b, x[k + 7], S23, 0x676f02d9);
    b = GG(b, c, d, a, x[k + 12], S24, 0x8d2a4c8a);
    a = HH(a, b, c, d, x[k + 5], S31, 0xfffa3942);
    d = HH(d, a, b, c, x[k + 8], S32, 0x8771f681);
    c = HH(c, d, a, b, x[k + 11], S33, 0x6d9d6122);
    b = HH(b, c, d, a, x[k + 14], S34, 0xfde5380c);
    a = HH(a, b, c, d, x[k + 1], S31, 0xa4beea44);
    d = HH(d, a, b, c, x[k + 4], S32, 0x4bdecfa9);
    c = HH(c, d, a, b, x[k + 7], S33, 0xf6bb4b60);
    b = HH(b, c, d, a, x[k + 10], S34, 0xbebfbc70);
    a = HH(a, b, c, d, x[k + 13], S31, 0x289b7ec6);
    d = HH(d, a, b, c, x[k + 0], S32, 0xeaa127fa);
    c = HH(c, d, a, b, x[k + 3], S33, 0xd4ef3085);
    b = HH(b, c, d, a, x[k + 6], S34, 0x4881d05);
    a = HH(a, b, c, d, x[k + 9], S31, 0xd9d4d039);
    d = HH(d, a, b, c, x[k + 12], S32, 0xe6db99e5);
    c = HH(c, d, a, b, x[k + 15], S33, 0x1fa27cf8);
    b = HH(b, c, d, a, x[k + 2], S34, 0xc4ac5665);
    a = II(a, b, c, d, x[k + 0], S41, 0xf4292244);
    d = II(d, a, b, c, x[k + 7], S42, 0x432aff97);
    c = II(c, d, a, b, x[k + 14], S43, 0xab9423a7);
    b = II(b, c, d, a, x[k + 5], S44, 0xfc93a039);
    a = II(a, b, c, d, x[k + 12], S41, 0x655b59c3);
    d = II(d, a, b, c, x[k + 3], S42, 0x8f0ccc92);
    c = II(c, d, a, b, x[k + 10], S43, 0xffeff47d);
    b = II(b, c, d, a, x[k + 1], S44, 0x85845dd1);
    a = II(a, b, c, d, x[k + 8], S41, 0x6fa87e4f);
    d = II(d, a, b, c, x[k + 15], S42, 0xfe2ce6e0);
    c = II(c, d, a, b, x[k + 6], S43, 0xa3014314);
    b = II(b, c, d, a, x[k + 13], S44, 0x4e0811a1);
    a = II(a, b, c, d, x[k + 4], S41, 0xf7537e82);
    d = II(d, a, b, c, x[k + 11], S42, 0xbd3af235);
    c = II(c, d, a, b, x[k + 2], S43, 0x2ad7d2bb);
    b = II(b, c, d, a, x[k + 9], S44, 0xeb86d391);
    a = addUnsigned(a, AA);
    b = addUnsigned(b, BB);
    c = addUnsigned(c, CC);
    d = addUnsigned(d, DD);
   }
   return (wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d)).toLowerCase();
  }

  function triggerWaybillRefresh() {
   try {
    if (window['$nuxt'] && typeof window['$nuxt'].refresh === 'function') {
     window['$nuxt'].refresh();
     return;
    }
   } catch(e) {}
   try {
    autoSwitchToReceiveTab();
   } catch(e) {}
  }

  function trySfAutoLogin(onComplete) {
   if (autoLoginState.inFlight) {
    if (typeof onComplete === 'function') onComplete(false);
    return;
   }
   var now = Date.now();
   if (now - autoLoginState.lastAttemptAt < 3000) {
    if (typeof onComplete === 'function') onComplete(false);
    return;
   }
   autoLoginState.inFlight = true;
   autoLoginState.attempted++;
   autoLoginState.lastAttemptAt = now;

   try {
    var appId = 'huJk1ARWMV5C';
    var appSecret = '9b1d6e3ec6b4ec82635f32799d680fe1';
    var chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    var nonce = '';
    for (var i = 0; i < 6; i++) {
     nonce += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    var timestamp = Date.now();
    var signStr = 'appId=' + appId + '&appSecret=' + appSecret + '&nonce=' + nonce + '&timestamp=' + timestamp;
    var sign = md5(signStr);

    var fetchFn = window.fetchOriginal || window.fetch;
    if (typeof fetchFn !== 'function') {
     autoLoginState.inFlight = false;
     if (typeof onComplete === 'function') onComplete(false);
     return;
    }

    fetchFn('/sf-service-core-web/service/user/autoLogin', {
     method: 'POST',
     headers: {
      'Accept': 'application/json, text/plain, */*',
      'Content-Type': 'application/json;charset=UTF-8',
      'appId': appId,
      'nonce': nonce,
      'timestamp': String(timestamp),
      'sign': sign
     },
     credentials: 'include'
    }).then(function(res) {
     return res.json();
    }).then(function(data) {
     autoLoginState.inFlight = false;
     if (data && (data.code === 0 || data.code === '0') && String(data.result) !== '2') {
      markFreshLogin();
      triggerWaybillRefresh();
      if (typeof onComplete === 'function') onComplete(true);
     } else {
      if (typeof onComplete === 'function') onComplete(false);
     }
    }).catch(function() {
     autoLoginState.inFlight = false;
     if (typeof onComplete === 'function') onComplete(false);
    });
   } catch(e) {
    autoLoginState.inFlight = false;
    if (typeof onComplete === 'function') onComplete(false);
   }
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
     if (wasRecentlyLoggedIn()) return;
     if (hasRememberMe() && autoLoginState.attempted < 3) {
      trySfAutoLogin(function (ok) {
       if (!ok && !hasRememberMe()) {
        state.authRejected = true;
        state.waybillVerified = false;
        state.captured = false;
        state.login = true;
        state.orders = [];
        state.byId = Object.create(null);
        try { sessionStorage.removeItem('hc_sf_logged'); } catch(e) {}
        try { if (typeof localStorage !== 'undefined') localStorage.removeItem('hc_sf_login_user'); } catch(e) {}
       }
      });
      return;
     }
     state.authRejected = true;
     state.waybillVerified = false;
     state.captured = false;
     state.login = true;
     state.orders = [];
     state.byId = Object.create(null);
     try { sessionStorage.removeItem('hc_sf_logged'); } catch(e) {}
     try { if (typeof localStorage !== 'undefined') localStorage.removeItem('hc_sf_login_user'); } catch(e) {}
     return;
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
   tryAutoLogin: trySfAutoLogin,
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

    if (autoLoginState.inFlight) {
     return { href: location.href, login: false, ready: false, orders: [] };
    }

    var emptyBtn = document.querySelector('.empty-box-btn, [class*="empty"] button');
    var isLoggedOutBtn = Boolean(emptyBtn && /\u767b\u5f55/.test(emptyBtn.innerText || ''));
    var headerLogin = document.querySelector('.common-header .login, .header-wrap [class*="login"], span.login');
    var isLoggedOutHeader = Boolean(headerLogin && /\u767b\u5f55/.test(headerLogin.innerText || ''));
    if ((isLoggedOutBtn || isLoggedOutHeader) && !wasRecentlyLoggedIn() && !hasLoginCookie()) {
     if (hasRememberMe() && autoLoginState.attempted < 2) {
      trySfAutoLogin();
      return { href: location.href, login: false, ready: false, orders: [] };
     }
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
     if (hasRememberMe() && !state.waybillVerified && autoLoginState.attempted < 2) {
      trySfAutoLogin();
     }
     return { href: location.href, login: false, ready: false, orders: [] };
    }

    if ((isLoggedOutBtn || isLoggedOutHeader) && !wasRecentlyLoggedIn()) {
     if (hasRememberMe() && autoLoginState.attempted < 2) {
      trySfAutoLogin();
      return { href: location.href, login: false, ready: false, orders: [] };
     }
     state.login = true;
     return { href: location.href, login: true, ready: false, orders: [] };
    }

    return { href: location.href, login: false, ready: false, orders: [] };
   }
  };

  if (hasRememberMe() && !state.waybillVerified) {
   defer(function () {
    trySfAutoLogin();
   }, 300);
  }
 }
 return window.HandySfOrders.snapshot();
})()