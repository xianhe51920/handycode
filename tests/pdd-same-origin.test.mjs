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

for(const page of ['https://m.pinduoduo.net/login.html','https://m.pinduoduo.net/psnl_verification.html','https://other.example/mdkd/package']){
 f=fixture(page);f.xhr.open('GET','https://api.pinduoduo.com/api/x');
 assert.equal(f.opened[0][1],'https://api.pinduoduo.com/api/x','Official login and verification pages are not modified');
 assert.equal(f.context.__handyPddSameOrigin,undefined);
}
console.log('PASS: PDD same-origin routing, request preservation, scope guards and idempotence');
