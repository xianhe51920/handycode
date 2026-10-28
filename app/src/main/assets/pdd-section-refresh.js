;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://m.pinduoduo.net' || location.pathname !== '/mdkd/package' || window.HandyPddRefresh) return;
 var callbacks = Object.create(null), requests = Object.create(null);
 function valid(part) { return part === 'identity' || part === 'parcels'; }
 window.HandyPddRefresh = {
  register: function (part, callback) {
   if (!valid(part) || typeof callback !== 'function') return function () {};
   callbacks[part] = callback;
   return function () { if (callbacks[part] === callback) delete callbacks[part]; };
  },
  report: function (part, success) {
   if (requests[part]) requests[part].success = success === true;
  },
  run: function (part) {
   if (!valid(part) || !callbacks[part]) return Promise.reject(new Error('refresh-unavailable'));
   if (requests[part]) return requests[part].promise;
   var job = {success:null}, callback = callbacks[part], timer;
   requests[part] = job;
   var deadline = new Promise(function (resolve, reject) {
    timer = setTimeout(function () { reject(new Error('refresh-timeout')); }, 12000);
   });
   job.promise = Promise.race([deadline, Promise.resolve().then(function () { return callback(); }).then(function () {
    if (job.success !== true) throw new Error('refresh-failed');
   })]).finally(function () {
    clearTimeout(timer);
    if (requests[part] === job) delete requests[part];
   });
   return job.promise;
  }
 };
 // Only official callbacks and boolean completion flags are registered here.
 // No API parameters, business results, Cookie values or account data cross it.
})();
