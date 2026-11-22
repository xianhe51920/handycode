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
 var fallbackLocationId = '2559577576138866706';
 var stationLocationCache = Object.create(null);
 var stationMeta = Object.create(null);
 var geocodeWaiters = Object.create(null);
 window.__hcOnStationCoords = function (code, lng, lat) {
  var list = geocodeWaiters[code];
  delete geocodeWaiters[code];
  var coords = lng && lat ? { longitude: lng, latitude: lat } : null;
  if (Array.isArray(list)) {
   list.forEach(function (fn) { try { fn(coords); } catch (e) {} });
  }
 };
 function requestNativeGeocode(stationCode) {
  var meta = stationMeta[stationCode];
  if (!meta || (!meta.detail && !meta.name)) return Promise.resolve(null);
  return new Promise(function (resolve) {
   var done = false;
   var timer = setTimeout(function () {
    if (!done) { done = true; resolve(null); }
   }, 2500);
   geocodeWaiters[stationCode] = geocodeWaiters[stationCode] || [];
   geocodeWaiters[stationCode].push(function (coords) {
    if (!done) {
     done = true;
     clearTimeout(timer);
     resolve(coords);
    }
   });
   console.info('__HANDYCODE_PDD_GEOCODE__:' + stationCode + '\t' + (meta.detail || '') + '\t' + (meta.name || ''));
  });
 }
 var LOCATION_CHECKED_URLS = {
  '/api/express_trackbox/dsd/manual/add/package/v2': true,
  '/api/express_trackbox/dsd/manual/add/multi/package': true,
  '/api/express_trackbox/dsd/manual/add/package': true,
  '/api/express_trackbox/express/pickcode/search': true,
  '/api/express_trackbox/express/lighting/package': true
 };
 function buildLocationReport(lng, lat) {
  return {
   payload: { scene: 'mdkd_scan_position' },
   location: {
    lng: String(lng),
    lat: String(lat),
    coordinate_type: 1,
    accuracy: '15.0'
   },
   location_extra_info: {
    app_version: 75800,
    os_version: 34,
    model: 'Android',
    platform: 'Android',
    longitude: Number(lng),
    latitude: Number(lat),
    horizontal_accuracy: 15.0,
    vertical_accuracy: 15.0,
    altitude: 50.0,
    speed: 0.0,
    provider: 'fused',
    location_time: Date.now(),
    is_cache: false
   }
  };
 }
 function recordStationList(res) {
  var result = res && res.result;
  if (!result) return;
  var list = Array.isArray(result.station_history_list) ? result.station_history_list.slice() : [];
  if (result.default_station) list.unshift(result.default_station);
  list.forEach(function (item) {
   if (item && item.code) {
    stationMeta[item.code] = {
     name: item.name || '',
     detail: item.detail || ''
    };
    try {
     var dec = decodeURIComponent(item.code);
     stationMeta[dec] = stationMeta[item.code];
    } catch (e) {}
   }
  });
 }
 function resolveStationLocationId(client, origGet, origPost, stationCode, config) {
  if (!stationCode) return Promise.resolve(fallbackLocationId);
  var cached = stationLocationCache[stationCode];
  if (cached && cached.id && Date.now() - cached.ts < 240000) {
   return Promise.resolve(cached.id);
  }
  if (cached && cached.pending) return cached.pending;
  var silent = { toast: { fail: false } };
  var decodedCode = stationCode;
  try { decodedCode = decodeURIComponent(stationCode); } catch (e) {}
  var pending = Promise.resolve().then(function () {
   return origGet.call(client, '/api/express_pyramid/dsd/in/cabinet/station/info', {
    station_code: decodedCode,
    entry_source: 0
   }, silent, config);
  }).then(function (info) {
   var res = info && info.result;
   if (res && res.longitude && res.latitude) return res;
   return requestNativeGeocode(stationCode);
  }).then(function (coords) {
   if (!coords || !coords.longitude || !coords.latitude) return fallbackLocationId;
   return origPost.call(client, '/api/ptolemeaus/location/report', buildLocationReport(coords.longitude, coords.latitude), silent, config).then(function (rep) {
    var locId = rep && rep.result && rep.result.location_id;
    if (locId) {
     fallbackLocationId = String(locId);
     stationLocationCache[stationCode] = { id: fallbackLocationId, ts: Date.now() };
     stationLocationCache[decodedCode] = stationLocationCache[stationCode];
     return fallbackLocationId;
    }
    return fallbackLocationId;
   });
  }).catch(function () {
   return fallbackLocationId;
  }).then(function (finalId) {
   if (!stationLocationCache[stationCode] || stationLocationCache[stationCode].pending) {
    stationLocationCache[stationCode] = { id: finalId, ts: Date.now() };
   }
   return finalId;
  });
  stationLocationCache[stationCode] = { pending: pending, ts: Date.now() };
  return pending;
 }
 function wrapLocationModule(modules) {
  if (!modules || typeof modules !== 'object' || typeof modules[41027] !== 'function' || modules[41027].__hcWrapped) return;
  var originalFactory = modules[41027];
  var wrapped = function (module, exports, require) {
   var origD = require && require.d;
   if (typeof origD === 'function') {
    var proxyRequire = function (id) { return require(id); };
    Object.keys(require).forEach(function (k) { proxyRequire[k] = require[k]; });
    proxyRequire.d = function (target, definition) {
     if (definition && typeof definition.J7 === 'function') {
      var getJ7 = definition.J7;
      var getBy = definition.By;
      definition = Object.assign({}, definition, {
       J7: function () {
        var origJ7 = getJ7();
        return function () {
         var res = origJ7.apply(this, arguments);
         if (res && !res.locationId) {
          var origGet = res.getLocationId;
          res = Object.assign({}, res, {
           locationId: fallbackLocationId,
           getLocationId: function () {
            return Promise.resolve(origGet ? origGet.apply(this, arguments) : null).then(function (v) {
             return v || fallbackLocationId;
            });
           }
          });
         }
         return res;
        };
       },
       By: typeof getBy === 'function' ? function () {
        var origBy = getBy();
        return function () {
         return Promise.resolve(origBy.apply(this, arguments)).then(function (v) {
          return v || fallbackLocationId;
         });
        };
       } : getBy
      });
     }
     return origD.call(require, target, definition);
    };
    return originalFactory.call(this, module, exports, proxyRequire);
   }
   return originalFactory.call(this, module, exports, require);
  };
  wrapped.__hcWrapped = true;
  modules[41027] = wrapped;
 }
 function wrapHttpModule(modules) {
  if (!modules || typeof modules !== 'object' || typeof modules[83101] !== 'function' || modules[83101].__hcWrapped) return;
  var originalFactory = modules[83101];
  var wrapped = function (module, exports, require) {
   var origD = require && require.d;
   if (typeof origD === 'function') {
    var proxyRequire = function (id) { return require(id); };
    Object.keys(require).forEach(function (k) { proxyRequire[k] = require[k]; });
    proxyRequire.d = function (target, definition) {
     if (definition && typeof definition.Z === 'function') {
      var getZ = definition.Z;
      definition = Object.assign({}, definition, {
       Z: function () {
        var client = getZ();
        if (client && typeof client.post === 'function' && typeof client.get === 'function' && !client.__hcPostWrapped) {
         var origGet = client.get;
         var origPost = client.post;
         client.post = function (url, data, options, config) {
          var self = this;
          if (url === '/api/express_trackbox/add/package/v2/station/history/info') {
           return origPost.call(this, url, data, options, config).then(function (res) {
            try {
             recordStationList(res);
             var st = res && res.result && res.result.default_station;
             if (st && st.code) {
              resolveStationLocationId(self, origGet, origPost, st.code, config);
             }
            } catch (e) {}
            return res;
           });
          }
          if (LOCATION_CHECKED_URLS[url] && data && typeof data === 'object' && data.station_code) {
           return resolveStationLocationId(self, origGet, origPost, data.station_code, config).then(function (locId) {
            var nextData = Object.assign({}, data, { location_id: locId || data.location_id || fallbackLocationId });
            return origPost.call(self, url, nextData, options, config);
           });
          }
          return origPost.call(this, url, data, options, config);
         };
         client.__hcPostWrapped = true;
        }
        return client;
       }
      });
     }
     return origD.call(require, target, definition);
    };
    return originalFactory.call(this, module, exports, proxyRequire);
   }
   return originalFactory.call(this, module, exports, require);
  };
  wrapped.__hcWrapped = true;
  modules[83101] = wrapped;
 }
 var chunkQueue = window.webpackChunk_N_E = window.webpackChunk_N_E || [];
 if (Array.isArray(chunkQueue)) {
  chunkQueue.forEach(function (entry) {
   if (entry && entry[1]) {
    wrapLocationModule(entry[1]);
    wrapHttpModule(entry[1]);
   }
  });
  var origPush = chunkQueue.push;
  chunkQueue.push = function (entry) {
   if (entry && entry[1]) {
    wrapLocationModule(entry[1]);
    wrapHttpModule(entry[1]);
   }
   return origPush.apply(this, arguments);
  };
 }
 // No credentials are read, copied or logged. The browser manages cookies
 // normally on the existing official same-origin /proxy/api endpoint.
})();
