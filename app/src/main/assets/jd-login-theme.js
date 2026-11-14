;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://plogin.m.jd.com' || window.HandyCodeJdLoginTheme) return;
 var root = document.documentElement;
 if (!root) return;

 var css = `
html[data-hc-jd-login-dark] {
 color-scheme:dark;
 --hc-jd-bg:#0f1218;
 --hc-jd-surface:#181c24;
 --hc-jd-input:#232936;
 --hc-jd-ink:#eceff4;
 --hc-jd-muted:#aeb8c8;
 --hc-jd-line:#3d4656;
 --hc-jd-link:#8dc2ff;
 --hc-jd-error:#ff858a;
}
html[data-hc-jd-login-dark],
html[data-hc-jd-login-dark] :is(body,#app,.page,.page>.header,#header,.virHeader,.code-box,.quick-login .txt-otherLogin) {
 background-color:var(--hc-jd-bg)!important;
 color:var(--hc-jd-ink)!important;
}
html[data-hc-jd-login-dark] :is(.input-container,.container .content .base .item,.container .content .group .item) {
 background-color:var(--hc-jd-surface)!important;
 border-color:var(--hc-jd-line)!important;
}
html[data-hc-jd-login-dark] :is(.acc-input,.container .content .group .item input) {
 background-color:var(--hc-jd-surface)!important;
 color:var(--hc-jd-ink)!important;
 caret-color:var(--hc-jd-link);
}
html[data-hc-jd-login-dark] input::placeholder {color:var(--hc-jd-muted)!important;opacity:1}
html[data-hc-jd-login-dark] input:-webkit-autofill {-webkit-text-fill-color:var(--hc-jd-ink)!important;box-shadow:0 0 0 1000px var(--hc-jd-surface) inset}
html[data-hc-jd-login-dark] :is(.text-btn,.quick-login,.policy_tip-checkbox) {border-color:var(--hc-jd-line)!important}
html[data-hc-jd-login-dark] button.text-btn {background:transparent!important;color:var(--hc-jd-link)!important}
html[data-hc-jd-login-dark] button.text-btn:is(:disabled,.timer:not(.active)) {color:var(--hc-jd-muted)!important}
html[data-hc-jd-login-dark] .policy_tip-checkbox:not(:checked) {background-color:var(--hc-jd-input)!important}
html[data-hc-jd-login-dark] :is(.quick-btn,.quick-login .txt-otherLogin,.quick-login .quick-type a,.policy_tip,.custom-holder,.container .tag) {
 color:var(--hc-jd-muted)!important;
}
html[data-hc-jd-login-dark] :is(.policy_tip a,.protocol-notice a,.content .link-tip a) {color:var(--hc-jd-link)!important}
html[data-hc-jd-login-dark] :is(.notice,.error-tip,.spec-color) {color:var(--hc-jd-error)!important}
html[data-hc-jd-login-dark] :is(.alert-body,.dialog-body,.protocol-wrap,.modal-content) {
 background-color:var(--hc-jd-surface)!important;
 color:var(--hc-jd-ink)!important;
}
html[data-hc-jd-login-dark] :is(.alert-des,.dialog-title,.dialog-des,.dialog-content,.content .important-tip,.protocol-btn:not(.agreement)) {
 color:var(--hc-jd-ink)!important;
}
html[data-hc-jd-login-dark] :is(.dialog-cancel,.modal-footer a.btn:first-child) {
 background:var(--hc-jd-input)!important;
 border-color:var(--hc-jd-line)!important;
 color:var(--hc-jd-ink)!important;
}
html[data-hc-jd-login-dark] #captcha_modal {
 background-color:var(--hc-jd-surface)!important;
 color:var(--hc-jd-ink)!important;
}
html[data-hc-jd-login-dark] #captcha_modal .captcha_header {color:var(--hc-jd-ink)!important}
html[data-hc-jd-login-dark] #captcha_modal .captcha_header .opt img {filter:brightness(0) invert(.9)}
html[data-hc-jd-login-dark] #captcha_modal :is(.slideTip,.beforeSlide,.sliding) {background-color:var(--hc-jd-input)!important}
html[data-hc-jd-login-dark] #captcha_modal :is(.slideTip,.beforeSlide)::after {background:transparent!important}
html[data-hc-jd-login-dark] #captcha_modal :is(.slider_container,.captcha_footer .tip_text,.jcap_complaint_entry_text) {
 color:var(--hc-jd-muted)!important;
}
`;

 window.HandyCodeJdLoginTheme = {setDark:function (dark) {
  if (dark && !document.getElementById('hc-jd-login-theme')) {
   var style = document.createElement('style');
   style.id = 'hc-jd-login-theme';
   style.textContent = css;
   (document.head || root).appendChild(style);
  }
  root.toggleAttribute('data-hc-jd-login-dark', !!dark);
 }};

 var popup = null;
 var reported;
 function reportVerification(records) {
  var next = document.getElementById('captcha_modal');
  if (next && next === popup && records && !records.some(function (record) { return record.target.contains(next); })) return;
  popup = next;
  var visible = !!(popup && popup.getClientRects().length && getComputedStyle(popup).visibility === 'visible');
  if (visible === reported) return;
  reported = visible;
  console.info('__HANDYCODE_JD_VERIFICATION__:' + (visible ? 'open' : 'closed'));
 }
 new MutationObserver(reportVerification).observe(root, {
  subtree:true, childList:true, attributes:true, attributeFilter:['class','style','hidden','aria-hidden']
 });
 reportVerification();
})();
