;(function () {
 'use strict';
 var authOrigins = ['https://login.m.taobao.com', 'https://login.taobao.com'];
 var hosts = ['https://pages-fast.m.taobao.com', 'https://pages.m.taobao.com'];
 var auth = authOrigins.indexOf(location.origin) !== -1;
 if (!auth && hosts.indexOf(location.origin) === -1) return;
 if (window.HandyCodeTaobaoLoginTheme) {
  window.HandyCodeTaobaoLoginTheme.setDark(!!window.__HC_TAOBAO_DARK__);
  return;
 }
 var dark = !!window.__HC_TAOBAO_DARK__, queued = false, readyPending = false, readyReported = false;
 var frameThemes = new WeakMap();
 var css = `
html[data-hc-taobao-login] {
 color-scheme:light;
 --hc-login-bg:#fff;--hc-login-field:#f2f5f7;--hc-login-ink:#20252d;
 --hc-login-muted:#687281;--hc-login-line:#dfe4ea;--hc-login-accent:#c64c00;
}
html[data-hc-taobao-login="dark"] {
 color-scheme:dark;
 --hc-login-bg:#0f1218;--hc-login-field:#202630;--hc-login-ink:#e6eaf2;
 --hc-login-muted:#a7b0be;--hc-login-line:#3e4654;--hc-login-accent:#ffae7b;
}
html[data-hc-taobao-login],html[data-hc-taobao-login] :is(body,#root,#login,#login-content,.login-content,.login-block,.sms-container,.login-view,.login-view-sms,.login-view-password) {
 background-color:var(--hc-login-bg)!important;color:var(--hc-login-ink)!important;
}
html[data-hc-taobao-login] #login .fm-field .fm-text,
html[data-hc-taobao-login] :is(.fm-text,.sim-input-container .tel-box-container .view-input,.sim-input-tel,.check-input) {
 background-color:var(--hc-login-field)!important;color:var(--hc-login-ink)!important;
 border-color:var(--hc-login-line)!important;caret-color:var(--hc-login-accent);
}
html[data-hc-taobao-login] #login .fm-field .fm-text:focus,
html[data-hc-taobao-login] :is(.fm-text:focus,.sim-input-container .tel-box-container .view-input.focus) {
 border-color:var(--hc-login-accent)!important;box-shadow:0 0 0 2px color-mix(in srgb,var(--hc-login-accent) 20%,transparent)!important;
}
html[data-hc-taobao-login] input::placeholder {color:var(--hc-login-muted)!important;opacity:1}
html[data-hc-taobao-login] input:-webkit-autofill {-webkit-text-fill-color:var(--hc-login-ink)!important;box-shadow:0 0 0 1000px var(--hc-login-field) inset}
html[data-hc-taobao-login] :is(.fm-label,.phone-code-select,.phone-code,.sms-subtitle,.sub-number,.sms-send-tip-number,.sim-input-container .tel-box-container .sim-input-tel) {color:var(--hc-login-ink)!important}
html[data-hc-taobao-login] .native-phone-code-select-wrap::before,
html[data-hc-taobao-login] .native-phone-code-select-arrow {color:var(--hc-login-ink)!important}
html[data-hc-taobao-login] .native-phone-code-select option {background:var(--hc-login-field)!important;color:var(--hc-login-ink)!important}
html[data-hc-taobao-login] #login :is(.password-login-link,.password-login-link a,.sms-login-link,.sms-login-link a,.sms-container .switch-login-btn) {background-color:var(--hc-login-field)!important;color:var(--hc-login-ink)!important}
html[data-hc-taobao-login] :is(.fm-agreement,.fm-optional-agreement,.sms-subtitle .sub-text,.sms-send-tip-first,.sms-send-tip-last,.sms-send-tip,.login-blocks,.login-error-dialog-msg) {color:var(--hc-login-muted)!important}
html[data-hc-taobao-login] :is(.fm-agreement a,.fm-optional-agreement a,.switch-login-btn,.login-links a,.password-login-link,.sms-login-link,.fm-field-sms .send-btn a) {color:var(--hc-login-accent)!important}
html[data-hc-taobao-login="dark"] #thirdpart-login a {background-color:var(--hc-login-field)!important;color:var(--hc-login-ink)!important;border-color:var(--hc-login-line)!important}
html[data-hc-taobao-login] .sms-container .code-button {background:var(--hc-login-bg)!important;color:var(--hc-login-accent)!important;border-color:var(--hc-login-accent)!important}
html[data-hc-taobao-login] :is(.login-dialog,.login-error-dialog,.login-check-dialog,.phone-code-selector) {background-color:var(--hc-login-field)!important;color:var(--hc-login-ink)!important}
html[data-hc-taobao-login] :is(.login-dialog-title,.login-dialog-body,.login-error-dialog-title) {color:var(--hc-login-ink)!important}
/* The primary orange CTA, disabled state, agreement checkbox, challenge images,
   and the hidden OTP input retain the official behavior and geometry. */
`;
 function reportReady() {
  if (!auth || readyPending || readyReported) return;
  var control = Array.from(document.querySelectorAll('input[type="tel"],input[type="password"],input[type="text"],.sms-container,.login-error-dialog'))
   .some(function(n){return n.getClientRects && n.getClientRects().length && getComputedStyle(n).visibility !== 'hidden';});
  if (!control || !document.getElementById('hc-taobao-login-theme')) return;
  readyPending = true;
  // Covered WebViews may suspend RAF. The native visual-state callback handles
  // the paint barrier after this DOM/style readiness signal.
  setTimeout(function(){
   readyPending = false;
   if (readyReported) return;
   readyReported = true;
   console.info('__HANDYCODE_TAOBAO_LOGIN__:ready');
  }, 0);
 }
 function forward() {
  document.querySelectorAll('iframe').forEach(function (frame) {
   try {
    var origin = new URL(frame.src, location.href).origin;
    if (authOrigins.indexOf(origin) !== -1 && frame.contentWindow) {
     frame.contentWindow.postMessage({type:'hc-taobao-login-theme',dark:dark}, origin);
    }
   } catch (_) {}
  });
 }
 function update() {
  queued = false;
  var root = document.documentElement;
  if (!root) return;
  if (auth) {
   root.setAttribute('data-hc-taobao-login', dark ? 'dark' : 'light');
   if (!document.getElementById('hc-taobao-login-theme')) {
    var style = document.createElement('style');style.id='hc-taobao-login-theme';style.textContent=css;
    (document.head || root).appendChild(style);
   }
   reportReady();
  } else {
   // The parent and a not-yet-painted iframe must also start in the app theme.
   root.setAttribute('data-hc-taobao-host', dark ? 'dark' : 'light');
   var base = document.getElementById('hc-taobao-host-theme');
   if (!base) {
    base = document.createElement('style');base.id='hc-taobao-host-theme';
    base.textContent='html[data-hc-taobao-host="dark"],html[data-hc-taobao-host="dark"] body,html[data-hc-taobao-host="dark"] iframe{background-color:#0f1218!important;color-scheme:dark}';
    (document.head || root).appendChild(base);
   }
  }
  forward();
 }
 function queue() { if (!queued) {queued=true;setTimeout(update,0);} }
 window.HandyCodeTaobaoLoginTheme = {
  setDark:function (value) {dark=!!value;update();},
  isSettled:function (value) {
   if (dark !== !!value) return false;
   return Array.from(document.querySelectorAll('iframe')).every(function(frame){
    try {
     if (authOrigins.indexOf(new URL(frame.src,location.href).origin) === -1 || !frame.getClientRects().length) return true;
     var style=getComputedStyle(frame);
     if (style.display==='none'||style.visibility==='hidden') return true;
     return frameThemes.get(frame.contentWindow) === dark;
    } catch (_) {return true;}
   });
  }
 };
 window.addEventListener('message', function (event) {
  if (!auth && authOrigins.indexOf(event.origin)!==-1 && event.data
      && event.data.type==='hc-taobao-theme-painted' && typeof event.data.dark==='boolean') {
   if (Array.from(document.querySelectorAll('iframe')).some(function(f){return f.contentWindow===event.source;})) frameThemes.set(event.source,event.data.dark);
   return;
  }
  // Accept only the embedding official page / auth frame, never a peer frame.
  if (!auth || window.parent === window || event.source !== window.parent
      || hosts.concat(authOrigins).indexOf(event.origin) === -1) return;
  var data = event.data;
  if (data && data.type === 'hc-taobao-login-theme' && typeof data.dark === 'boolean') {
   window.HandyCodeTaobaoLoginTheme.setDark(data.dark);
   window.parent.postMessage({type:'hc-taobao-theme-painted',dark:dark},event.origin);
  }
 });
 document.addEventListener('load', queue, true);
 document.addEventListener('DOMContentLoaded', update, {once:true});
 // Observe structure only: typing and switching theme do not cause a loop.
 new MutationObserver(function(){if (!document.getElementById(auth?'hc-taobao-login-theme':'hc-taobao-host-theme')) update();else queue();}).observe(document, {childList:true,subtree:true});
 update();
})();
