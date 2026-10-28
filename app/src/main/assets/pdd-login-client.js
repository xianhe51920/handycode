;(function () {
 'use strict';
 function loginPage() {
  return window.top === window && location.origin === 'https://m.pinduoduo.net' && location.pathname === '/login.html';
 }
 if (!loginPage() || window.HandyPddLoginClient) return;
 window.HandyPddLoginClient = true;
 document.addEventListener('click', function (event) {
  if (!loginPage()) return;
  var node = event.target;
  if (node && node.nodeType === 3) node = node.parentElement;
  for (var depth = 0; node && node !== document.body && depth < 6; depth++, node = node.parentElement) {
   // Only the official app-launch button, including its nested label/icon.
   // Delegation also covers the button being mounted later by React.
   var text = (node.textContent || '').replace(/[\s\u200b]/g, '').replace(/^[\ue000-\uf8ff]+/, '');
   if (!/^打开拼多多app$/i.test(text)) continue;
   event.preventDefault();
   event.stopImmediatePropagation();
   location.href = 'pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE';
   return;
  }
 }, true);
})();
