;(function (global) {
 'use strict';
 if (!/(^|\.)(taobao|tmall|alipay|cainiao|alibaba)\.com$/.test(location.hostname)) return;
 if (global.HandyCodeTaobaoLoginChrome) { global.HandyCodeTaobaoLoginChrome.update(); return; }
 var queued = false;
 function set(node, name, value) {
  if (node.style.getPropertyValue(name) !== value || node.style.getPropertyPriority(name) !== 'important') {
   node.style.setProperty(name, value, 'important');
  }
 }
 function update() {
  queued = false;
  if (!document.body) return;
  var walker = document.createTreeWalker(document.body, 4), text, headers = new Set();
  while ((text = walker.nextNode())) {
   if (!text.parentElement || /^(SCRIPT|STYLE|TEXTAREA)$/.test(text.parentElement.tagName)
       || (text.nodeValue || '').replace(/\s/g, '') !== '您需要登录才能继续访问') continue;
   for (var header = text.parentElement, depth = 0; header && header !== document.body && depth < 4; header = header.parentElement, depth++) {
    var caption = (header.textContent || '').replace(/\s/g, '');
    if (caption !== '您需要登录才能继续访问关闭' || header.querySelector('iframe,input,textarea,form,[contenteditable]')) continue;
    var parent = header.parentElement;
    if (!parent || parent === document.body) continue;
    var frames = Array.from(parent.children).filter(function (child) { return child.tagName === 'IFRAME'; });
    if (frames.length !== 1) continue;
    if (headers.has(header)) break;
    headers.add(header);
    set(header, 'display', 'none');
    // Keep the exact official frame and its login/OTP state. Remove only the
    // space reserved for the outer 52px caption, regardless of its box model.
    var frame = frames[0];
    set(frame, 'top', '0px'); set(frame, 'bottom', '0px'); set(frame, 'height', '100%');
    set(frame, 'padding-top', '0px'); set(frame, 'margin-top', '0px');
    break;
   }
  }
 }
 function queue() { if (!queued) { queued = true; setTimeout(update, 0); } }
 global.HandyCodeTaobaoLoginChrome = {update:update};
 if (document.documentElement) new MutationObserver(queue).observe(document.documentElement, {
  subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['style']
 });
 update();
})(window);
