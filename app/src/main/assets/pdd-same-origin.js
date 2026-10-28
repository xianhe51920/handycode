;(function () {
 'use strict';
 // Run only in the official package page, before its application bundle.
 if (location.origin !== 'https://m.pinduoduo.net' || !location.pathname.startsWith('/mdkd/')) return;
 if (window.__handyPddSameOrigin) return;
 window.__handyPddSameOrigin = true;
 function route(input) {
  try {
   var target = new URL(String(input), location.href);
   if (target.origin !== 'https://api.pinduoduo.com' || target.username || target.password || !target.pathname.startsWith('/api/')) return input;
   return location.origin + '/proxy/api' + target.pathname + target.search + target.hash;
  } catch (error) { return input; }
 }
 var originalOpen = XMLHttpRequest.prototype.open;
 XMLHttpRequest.prototype.open = function () {
  var args = Array.prototype.slice.call(arguments);
  if (args.length > 1) args[1] = route(args[1]);
  return originalOpen.apply(this, args);
 };
 if (typeof window.fetch === 'function') {
  var originalFetch = window.fetch;
  window.fetch = function (input, init) {
   if (typeof Request !== 'undefined' && input instanceof Request) {
    var mapped = route(input.url);
    if (mapped !== input.url) input = new Request(mapped, input);
   } else input = route(input);
   return originalFetch.call(this, input, init);
  };
 }
 // No credentials are read, copied or logged. The browser manages cookies
 // normally on the existing official same-origin /proxy/api endpoint.
})();
