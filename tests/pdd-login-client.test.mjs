import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script=readFileSync(new URL('../app/src/main/assets/pdd-login-client.js',import.meta.url),'utf8');
function fixture({path='/login.html',origin='https://m.pinduoduo.net',subframe=false}={}){
 const {window,document}=parseHTML('<html><body><main><button id="app"><img/><span>打开拼多多APP</span></button><button id="phone">手机登录</button><input type="tel"/></main></body></html>');
 delete window.HandyPddLoginClient;
 window.top=subframe?{}:window;
 const listeners=[];
 document.addEventListener=(name,callback,capture)=>listeners.push({name,callback,capture});
 const location={origin,pathname:path,href:'unchanged'};
 const context={window,document,location};runInNewContext(script,context);
 function click(selector){
  const event={target:document.querySelector(selector),preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;}};
  listeners.filter(item=>item.name==='click').forEach(item=>item.callback(event));return event;
 }
 return {window,document,location,context,listeners,click};
}
let f=fixture();
assert.equal(f.listeners.length,1);
assert.equal(f.listeners[0].capture,true,'Intercept before the official delegated app-launch handler');
runInNewContext(script,f.context);
assert.equal(f.listeners.length,1,'Repeated commits never register duplicate handlers');
for(const target of ['#app','#app span','#app img']){
 const event=f.click(target);
 assert.equal(event.prevented,true);assert.equal(event.stopped,true);
 assert.equal(f.location.href,'pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE');
}
for(const target of ['#phone','input','main','body']){
 f.location.href='unchanged';const event=f.click(target);
 assert.equal(f.location.href,'unchanged');assert.equal(event.prevented,undefined);
}
f.document.querySelector('#app').outerHTML='<div id="later"><img/><span>\ue001 打开 拼多多 App </span></div>';
assert.equal(f.click('#later img').prevented,true,'Late-mounted React button and icon-font prefix work');
f.location.pathname='/psnl_verification.html';f.location.href='unchanged';
assert.equal(f.click('#later').prevented,undefined,'A later SPA verification route is not changed');
assert.equal(f.location.href,'unchanged');
for(const options of [{path:'/mdkd/package'},{path:'/psnl_verification.html'},{origin:'https://m.pinduoduo.net.evil.example'},{subframe:true}]){
 f=fixture(options);assert.equal(f.listeners.length,0);
}
console.log('PASS: login-only app button routing, nested icons, capture, late mount, phone/form preservation and scope');
