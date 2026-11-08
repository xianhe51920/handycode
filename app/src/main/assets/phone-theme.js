(function () {
 'use strict';
 if (window.HandyCodePhoneTheme) return;
 var dark = false;
 var css = `
html[data-hc-phone-theme="dark"] {
 color-scheme:dark;
 background:#0f1218!important;
 color:#eceff4!important;
 --end-color-text:#eceff4;
 --end-color-text-secondary:#b3bdcc;
 --end-color-text-weak:#949eb0;
 --end-color-fill:#949eb0;
 --end-color-border:#303744;
 --bg-primary:#232936;
}
html[data-hc-phone-theme="dark"] body {background:#0f1218!important;color:#eceff4!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] #ice-container {color:#eceff4}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="phone-manage--"],[class^="add-friend-phone--"],[class^="card--"],[class^="button-group--"]) {background-color:#181c24!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="card--"] [class^="title--"],[class^="phone--"],[class^="card-title--"],[class^="label--"]) {color:#eceff4!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="content--"],[class^="edit--"],[class^="delete--"],[class^="footer-info--"]) {color:#b3bdcc!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="input-wrap--"],[class^="phone-item--"] [class^="info--"]) {background:#232936!important;color:#b3bdcc!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="container--hIneu"],[class^="fixed-bottom--"]) {background:#181c24!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] [class^="container--hIneu"] img {opacity:.18}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] [class^="header-concent--"] {color:#eceff4!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="header-grey--"],[class^="tip--"]) {color:#b3bdcc!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] input {background:transparent!important;color:#eceff4!important;caret-color:#ff8e6b;--end-input-color:#eceff4;--end-input-placeholder-color:#949eb0}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] input::placeholder {color:#949eb0!important;opacity:1}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] [class^="card--"] [class^="text--"] {color:#b3bdcc!important}
html[data-hc-phone-provider="taobao"][data-hc-phone-theme="dark"] :is([class^="button-group--"] .hyper-button-color-primary-fill-solid,[class^="label-required--"]) {--primary-color:#ff784e}

html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] [class*="cell-phone-list-container__"] {background:#0f1218!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-info,.cell-phone-list-item,.cell-phone-button-wrapper,.event-modal-view-container-center,.event-modal-content,.bind-new-cell-phone-wrapper,.modify-cell-phone-wrapper) {background:#181c24!important;color:#eceff4!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-title,.cell-phone-list-item-label-value,.cell-phone-list-item-actions,.bind-new-cell-phone-content-title,.modify-cell-phone-content-title) {color:#eceff4!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-item-actions .default-container,.cell-phone-list-item-actions .selected) {color:#eceff4!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-desc,.bind-new-cell-phone-content-desc,.bind-new-contract-wrapper,.bind-new-contract-item-fill,.modify-cell-phone-content-desc) {color:#949eb0!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-item-operate span,.modify-cell-phone-content-alias-item) {background:#232936!important;color:#eceff4!important;border-color:#465164!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.cell-phone-list-item-divider,.bind-new-cell-phone-content-item,.bind-new-cell-phone-content-tips,.modify-cell-phone-content-item) {border-color:#303744!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] .cell-phone-list-item-divider {background:#303744!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] :is(.bind-new-cell-phone-content-item,.modify-cell-phone-content-item) input {background:transparent!important;color:#eceff4!important;caret-color:#ff8e6b}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] input::placeholder {color:#949eb0!important;opacity:1}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] .cell-phone-button-tip {background:#3c3022!important;color:#ffd19a!important}
html[data-hc-phone-provider="pdd"][data-hc-phone-theme="dark"] .bind-new-contract-item-content {color:#79b9e8!important}
`;
 function setDark(value) {
  dark = !!value;
  var root = document.documentElement;
  if (!root) return;
  var provider = '';
  if (location.hostname === 'm.pinduoduo.net' && location.pathname === '/mdkd/cellPhoneList') provider = 'pdd';
  if (location.hostname === 'pages-fast.m.taobao.com'
      && /^\/wow\/z\/uniapp\/1011717\/last-mile-fe\/end-collect-platform\/(?:phone-manage(?:-xy)?(?:-ssr)?|add-friend-phone(?:-ssr)?)$/.test(location.pathname)) provider = 'taobao';
  if (!provider) {
   root.removeAttribute('data-hc-phone-provider');
   root.removeAttribute('data-hc-phone-theme');
   return;
  }
  var style = document.getElementById('hc-phone-theme');
  if (!style) {
   style = document.createElement('style'); style.id = 'hc-phone-theme'; style.textContent = css;
   (document.head || root).appendChild(style);
  }
  root.setAttribute('data-hc-phone-provider', provider);
  root.setAttribute('data-hc-phone-theme', dark ? 'dark' : 'light');
 }
 window.HandyCodePhoneTheme = {setDark:setDark};
 window.addEventListener('popstate', function () { setDark(dark); });
})();
