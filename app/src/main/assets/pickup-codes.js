(function () {
 'use strict';
 if (window.top !== window || location.protocol !== 'https:') return null;
 var tokens = /^[A-Za-z0-9][A-Za-z0-9-]{1,23}$/;
 var results = [];
 function add(value) {
  var code = value.trim().replace(/^[:：\s【\[（(]+|[\s】\]）)]+$/g, '');
  if (results.length < 80 && tokens.test(code) && !/^20\d\d-\d{1,2}-\d{1,2}$/.test(code)) {
   results.push({code:code});
  }
 }

 if (location.origin === 'https://m.pinduoduo.net' && location.pathname === '/mdkd/package') {
  var pdd = document.getElementById('hc-pdd-parcels');
  var source = document.querySelector('[data-hc-pdd-source]');
  if (!pdd || pdd.dataset.ready !== 'true' || !source) return null;
  source.querySelectorAll('.package-list-picking .package-code-value').forEach(function (value) {
   add(value.textContent || '');
  });
  return results;
 }

 var mount = document.getElementById('parcels-content');
 var frame = document.getElementById('parcels-source');
 if (!document.getElementById('handycode-merged') || !mount || mount.dataset.ready !== 'true' || !frame) return null;
 function labeledCode(text) {
  var match = text.match(/(?:取件码|提货码|取货码|自提码)[:：\s【\[（(]*([A-Za-z0-9][A-Za-z0-9-]{1,23})(?![A-Za-z0-9-])/);
  return match && /\d/.test(match[1]) ? match[1] : '';
 }
 try {
  var doc = frame.contentDocument;
  var url = doc && new URL(doc.URL);
  if (!url || url.origin !== 'https://pages-fast.m.taobao.com' || url.origin !== location.origin
      || !/\/(?:m-end-school-tab\/home|end-collect-platform\/school-search)\/?$/.test(url.pathname)) return null;
  var host = doc.getElementById('ice-container') || doc.body;
  if (!host) return null;
  var cards = host.querySelectorAll('[id^="package-"]');
  if (!cards.length) cards = host.querySelectorAll('[class*="package--"]');
  cards.forEach(function (card) {
   if (card.closest('#fcc-snapshot-placeholder')) return;
   var value = card.querySelector('[class*="CHARACTER--"]');
   if (value && tokens.test(value.textContent.trim())) {
    add(value.textContent);
    return;
   }
   var title = card.querySelector('[class*="mainLine--"]');
   add(labeledCode((title || card).textContent || ''));
  });
  if (!results.length) {
   // Read the smallest labeled match in the rendered pending-parcel card.
   mount.querySelectorAll('div,li,p,span').forEach(function (node) {
    var text = node.textContent || '';
    if (text.length > 180) return;
    var code = labeledCode(text);
    if (!code) return;
    var repeated = Array.from(node.children).some(function (child) {
     return labeledCode(child.textContent || '') === code;
    });
    if (!repeated) add(code);
   });
  }
  return results;
 } catch (unavailable) {
  return null;
 }
})()
