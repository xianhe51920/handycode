;(function () {
 'use strict';
 function loginPage() {
  return location.origin === 'https://login.m.taobao.com' || location.origin === 'https://login.taobao.com'
   || window.top === window && (location.origin === 'https://plogin.m.jd.com'
    || location.origin === 'https://m.pinduoduo.net' && location.pathname === '/login.html');
 }
 if (!loginPage() || window.HandyCodeLoginAgreements) return;
 window.HandyCodeLoginAgreements = true;
 document.addEventListener('click', function (event) {
  if (!loginPage()) return;
  var target = event.target;
  if (target && target.nodeType === 3) target = target.parentElement;
  if (!target || !target.closest || target.closest('input,[role="checkbox"]')) return;
  var link = target.closest('a,[role="link"],.link,.policy_tip-link');
  if (!link || link.querySelector('input,[role="checkbox"]')) return;
  var consent = link.closest('.fm-agreement,.fm-optional-agreement,.agreement-wrap,.agreement-container,.agreement,.policy_tip,.policy-tip,.protocol-notice');
  if (!consent && !/(协议|隐私|服务条款|法律声明)/.test(link.textContent || '')) return;
  // Keep the official checkbox, its checked state and login validation intact.
  // Capture also stops React/router handlers, including nested link text/icons.
  event.preventDefault();
  event.stopImmediatePropagation();
 }, true);
})();
