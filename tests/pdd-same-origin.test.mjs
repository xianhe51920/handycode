import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext,Script} from 'node:vm';
const source=readFileSync(new URL('../app/src/main/assets/pdd-same-origin.js',import.meta.url),'utf8');
new Script(source);
function fixture(href='https://m.pinduoduo.net/mdkd/package?tab=ID_CODE'){
 const opened=[],fetched=[];
 function Xhr(){}
 Xhr.prototype.open=function(...args){opened.push(args);return 'opened'};
 const location=new URL(href);
 const context={location,URL,Request,XMLHttpRequest:Xhr};
 context.window=context;
 context.fetch=function(input,init){fetched.push({input,init});return Promise.resolve('fetched')};
 runInNewContext(source,context);
 return {context,opened,fetched,xhr:new Xhr()};
}
let f=fixture();
const payload={dummy:'body-preserved'};
assert.equal(f.xhr.open('POST','https://api.pinduoduo.com/api/express_trackbox/generate_identification_code?scene=1',true),'opened');
assert.equal(f.opened[0][1],'https://m.pinduoduo.net/proxy/api/api/express_trackbox/generate_identification_code?scene=1');
assert.equal(f.opened[0][0],'POST');assert.equal(f.opened[0][2],true);
await f.context.fetch('https://api.pinduoduo.com/api/server/_stm',{method:'GET'});
assert.equal(f.fetched[0].input,'https://m.pinduoduo.net/proxy/api/api/server/_stm');
const init={method:'POST',body:JSON.stringify(payload),credentials:'include'};
await f.context.fetch('https://api.pinduoduo.com/api/apoc/example',init);
assert.equal(f.fetched[1].init,init,'Request options, body and credentials policy remain untouched');
const originalRequest=new Request('https://api.pinduoduo.com/api/example',{method:'POST',body:'test-only',headers:{'Content-Type':'text/plain'}});
await f.context.fetch(originalRequest);
const mapped=f.fetched[2].input;
assert.equal(mapped.url,'https://m.pinduoduo.net/proxy/api/api/example');
assert.equal(mapped.method,'POST');
assert.equal(originalRequest.bodyUsed,false,'Routing does not read a request body');
assert.equal(await mapped.text(),'test-only');
assert.equal(mapped.headers.get('Content-Type'),'text/plain');
for(const unchanged of ['https://api.pinduoduo.com.evil.example/api/x','https://evil.example/api/x','http://api.pinduoduo.com/api/x','https://api.pinduoduo.com:444/api/x','https://name@api.pinduoduo.com/api/x','https://api.pinduoduo.com/not-api/x','/proxy/api/login','https://static.pddpic.com/assets/js/login.js']){
 f.xhr.open('GET',unchanged);
 assert.equal(f.opened.at(-1)[1],unchanged);
}
const patchedOpen=f.context.XMLHttpRequest.prototype.open;
runInNewContext(source,f.context);
assert.equal(f.context.XMLHttpRequest.prototype.open,patchedOpen,'Re-injection does not wrap twice');
const chunkModules = {
 41027(module, exports, require) {
  require.d(exports, {
   By: () => () => Promise.resolve(undefined),
   J7: () => () => ({ locationId: undefined, getLocationId: () => Promise.resolve(null) })
  });
 }
};
f.context.webpackChunk_N_E.push([[2888], chunkModules]);
const exported = {};
const req = id => ({});
req.d = (target, def) => Object.keys(def).forEach(k => Object.defineProperty(target, k, { enumerable: true, get: def[k] }));
chunkModules[41027]({}, exported, req);
const j7Result = exported.J7('12345', true);
assert.equal(j7Result.locationId, '2559577576138866706', 'Non-native WebView add-package receives fallback locationId');
assert.equal(await j7Result.getLocationId(), '2559577576138866706');
assert.equal(await exported.By(), '2559577576138866706');

const calls = [];
const httpModules = {
 83101(module, exports, require) {
  require.d(exports, {
   Z: () => ({
    get(url, params) {
     calls.push({ method: 'GET', url, params });
     if (url === '/api/express_pyramid/dsd/in/cabinet/station/info') {
      return Promise.resolve({ success: true, result: { longitude: '104.691', latitude: '28.832' } });
     }
     return Promise.resolve({});
    },
    post(url, data) {
     calls.push({ method: 'POST', url, data });
     if (url === '/api/ptolemeaus/location/report') {
      return Promise.resolve({ success: true, result: { location_id: 'loc_from_station_coords' } });
     }
     return Promise.resolve({ success: true, result: {} });
    }
   })
  });
 }
};
f.context.webpackChunk_N_E.push([[9999], httpModules]);
const httpExported = {};
httpModules[83101]({}, httpExported, req);
await httpExported.Z.post('/api/express_trackbox/dsd/manual/add/package/v2', {
 station_code: 'ST_TEST_01',
 search_text: '1234567890',
 location_id: '2559577576138866706',
 entry_source: '4'
});
assert.equal(calls.find(c => c.url === '/api/express_pyramid/dsd/in/cabinet/station/info')?.params.station_code, 'ST_TEST_01');
assert.equal(calls.find(c => c.url === '/api/ptolemeaus/location/report')?.data.location.lng, '104.691');
assert.equal(calls.find(c => c.url === '/api/express_trackbox/dsd/manual/add/package/v2')?.data.location_id, 'loc_from_station_coords');
assert.equal(exported.J7('12345', true).locationId, 'loc_from_station_coords', 'Resolved station location_id updates fallback for module 41027');

// Dynamic module IDs (like 2026-11-22 build 82469 / 96133) + runtime refresh fallback
const refreshScript = readFileSync(new URL('../app/src/main/assets/pdd-section-refresh.js', import.meta.url), 'utf8');
const dynTimers = new Map(); let dynSeq = 0;
const dynLocation = new URL('https://m.pinduoduo.net/mdkd/package?tab=ID_CODE');
function DynXhr() {}
DynXhr.prototype.open = function () {};
const dynCtx = {
 location: dynLocation,
 URL,
 Request,
 XMLHttpRequest: DynXhr,
 setTimeout: fn => { dynTimers.set(++dynSeq, fn); return dynSeq; },
 clearTimeout: id => dynTimers.delete(id)
};
dynCtx.window = dynCtx;
dynCtx.top = dynCtx;
runInNewContext(refreshScript + '\n' + source, dynCtx);
const dynCalls = [];
const dynModules = {
 82469(module, exports, require) {
  require.d(exports, {
   By: () => () => Promise.resolve(undefined),
   J7: () => () => ({ locationId: undefined, getLocationId: () => Promise.resolve(null) })
  });
 },
 96133(module, exports, require) {
  const BACKEND_API = 'https://api.pinduoduo.com'; const isApiRequest = true;
  require.d(exports, {
   Z: () => ({
    get(url, params) { return Promise.resolve({ success: true, result: { longitude: '104.7', latitude: '28.9' } }); },
    post: function (url, data) {
     dynCalls.push({ url, data, tag: BACKEND_API, isApiRequest });
     if (url === '/api/ptolemeaus/location/report') return Promise.resolve({ success: true, result: { location_id: 'dyn_loc_1' } });
     if (url === '/api/express_trackbox/generate_identification_code') return Promise.resolve({ success: true, result: { identification_code: 'ID123' } });
     if (url === '/api/express_trackbox/station/package') return Promise.resolve({ success: true, result: { combine_list: [] } });
     return Promise.resolve({ success: true });
    }
   })
  });
 },
 40616(module, exports, require) { require.d(exports, { p: () => () => {} }); },
 17984(module, exports, require) { require.d(exports, { hg: () => (name, fn) => () => (dispatch, getState) => fn(null, { dispatch, getState }) }); },
 12479(module, exports, require) {
  require.d(exports, {
   TL: () => () => {
    const dispatch = action => typeof action === 'function' ? action(dispatch, () => ({})) : action;
    return dispatch;
   }
  });
 }
};
const dynPageModules = {
 67441(module, exports, require) {
  const b = require(40616), N = { GL: { generateIdentificationCode: t => require(96133).Z.post('/api/express_trackbox/generate_identification_code', t) } };
  const cls = 'identification-code-container';
  b.p(function () { return N.GL.generateIdentificationCode({ entry_source: cls }); });
 },
 65867(module, exports, require) {
  const Et = require(17984), p = require(12479);
  const Ht = Et.hg('package/refreshPackagePage', function () { return require(96133).Z.post('/api/express_trackbox/station/package', { pick_up_mode: 1 }); });
  const dispatch = p.TL();
  exports.run = () => dispatch(Ht());
 }
};
dynCtx.webpackChunk_N_E.push([[1], dynModules]);
dynCtx.webpackChunk_N_E.push([[2], dynPageModules]);
const dynReq = id => {
 const out = {};
 const mod = dynModules[id] || dynPageModules[id];
 const r = x => dynReq(x);
 r.d = (t, def) => Object.keys(def).forEach(k => Object.defineProperty(t, k, { enumerable: true, get: def[k] }));
 mod({}, out, r);
 return out;
};
const dynLoc = dynReq(82469);
const dynHttp = dynReq(96133);
await dynHttp.Z.post('/api/express_trackbox/dsd/manual/add/package/v2', { station_code: 'ST_DYN', entry_source: '4' });
assert.equal(dynCalls.find(c => c.url === '/api/express_trackbox/dsd/manual/add/package/v2')?.data.location_id, 'dyn_loc_1');
assert.equal(dynLoc.J7().locationId, 'dyn_loc_1');
dynReq(67441);
dynReq(65867);
await dynCtx.HandyPddRefresh.run('identity');
await dynCtx.HandyPddRefresh.run('parcels');
assert.ok(dynCalls.some(c => c.url === '/api/express_trackbox/generate_identification_code'));
assert.ok(dynCalls.some(c => c.url === '/api/express_trackbox/station/package'));

for(const page of ['https://m.pinduoduo.net/login.html','https://m.pinduoduo.net/psnl_verification.html','https://other.example/mdkd/package']){
 f=fixture(page);f.xhr.open('GET','https://api.pinduoduo.com/api/x');
 assert.equal(f.opened[0][1],'https://api.pinduoduo.com/api/x','Official login and verification pages are not modified');
 assert.equal(f.context.__handyPddSameOrigin,undefined);
}
console.log('PASS: PDD same-origin routing, request preservation, scope guards and idempotence');
