import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const source = readFileSync(new URL('../app/src/main/assets/jd-identity.js',import.meta.url),'utf8');
function fixture(href='https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou') {
 const {window,document} = parseHTML('<html><head></head><body><div id="root"><div class="qr-box plus"><div class="content-right"><div>每20分钟自动更新</div><div class="refresh">刷新</div></div><div class="font-18">138****0000</div><div class="qr"><svg viewBox="0 0 600 160">'+Array.from({length:50},(_,i)=>`<rect x="${i*10}" width="3" height="140"/>`).join('')+'</svg></div></div></div></body></html>');
 window.top = window;
 window.HTMLElement.prototype.getBoundingClientRect=()=>({width:320,height:202});
 Object.defineProperties(window.HTMLElement.prototype,{offsetWidth:{get:()=>288,configurable:true},offsetHeight:{get:()=>172,configurable:true}});
 function Xhr() {this.listeners=[];this.status=200;this.responseType='';}
 Xhr.prototype.open=function(method,url){this.url=url;return 'open';};
 Xhr.prototype.send=function(){return 'send';};
 Xhr.prototype.addEventListener=function(type,fn){assert.equal(type,'loadend');this.listeners.push(fn);};
 let refreshes=0, logs=[];
 document.querySelector('.refresh').addEventListener('click',()=>refreshes++);
 const ctx={window,document,XMLHttpRequest:Xhr,URL,location:new URL(href),console:{log:v=>logs.push(v)},
  requestAnimationFrame:f=>{f(0);return 1;},cancelAnimationFrame:()=>{},matchMedia:()=>({matches:true}),WeakMap};
 const result=runInNewContext(source,ctx);
 return {window,document,ctx,result,Xhr,logs,refreshes:()=>refreshes};
}
const f=fixture(), layout=f.window.HandyJdIdentity;
assert.equal(f.result.ready,true);
assert.equal(f.document.querySelector('.hc-jd-phone').textContent,'138****0000');
assert.equal(f.document.querySelector('#root').getAttribute('aria-hidden'),'true');
assert.equal(f.document.querySelector('.hc-jd-barcode svg').outerHTML,f.document.querySelector('#root svg').outerHTML);
assert.ok(!('identityCode' in f.result)&&!('phone' in f.result),'Native layer receives readiness and size only');
assert.ok(f.logs.every(x=>/^__HANDYCODE_JD_IDENTITY_HEIGHT__:\d+$/.test(x)),'No identity data in diagnostics');
layout.setDark(true);assert.equal(f.document.documentElement.hasAttribute('data-hc-jd-dark'),true);
layout.setDark(false);assert.equal(f.document.documentElement.hasAttribute('data-hc-jd-dark'),false);
const original=f.document.querySelector('#root svg');
layout.refresh();layout.refresh();assert.equal(f.refreshes(),1,'Reuse official refresh, coalesce repeated clicks');
assert.equal(layout.snapshot().ready,false,'Hide previous barcode until refresh completes');
function complete(url,body,status=200) {
 const xhr=new f.Xhr();assert.equal(xhr.open('POST',url),'open');assert.equal(xhr.send(),'send');
 xhr.status=status;xhr.responseText=JSON.stringify(body);xhr.listeners.forEach(fn=>fn.call(xhr));return xhr;
}
complete('https://lop-proxy.jd.com/generateIdentityCode',{errorCode:0,data:{identityCode:'fixture'}});
assert.equal(layout.snapshot().ready,true);
original.querySelector('rect').setAttribute('width','5');
layout.snapshot();assert.equal(f.document.querySelector('.hc-jd-barcode rect').getAttribute('width'),'5','Reflect official renewal');
assert.equal(f.document.querySelector('#root svg'),original,'Do not remove the live Vue SVG or its timer');
complete('https://lop-proxy.jd.com/generateIdentityCode',{errorCode:7},200);
assert.equal(layout.snapshot().failed,true);assert.equal(layout.snapshot().ready,false,'Do not show stale code after failed renewal');
complete('https://lop-proxy.jd.com.evil.invalid/generateIdentityCode',{code:1,data:{identityCode:'fixture'}});
assert.equal(layout.snapshot().failed,true,'Other hosts cannot clear a failure');
complete('https://lop-proxy.jd.com/other',{code:1,data:{identityCode:'fixture'}});
assert.equal(layout.snapshot().failed,true,'Unrelated order requests cannot settle identity refresh');
complete('https://lop-proxy.jd.com/generateIdentityCode',{code:1,data:{identityCode:'fixture'}});
assert.equal(layout.snapshot().ready,true);
f.document.querySelector('.hc-jd-barcode').click();
assert.equal(f.document.querySelector('.hc-jd-barcode').getAttribute('aria-expanded'),'true');
f.document.querySelector('.hc-jd-barcode').click();
assert.equal(f.document.querySelector('.hc-jd-barcode').getAttribute('aria-expanded'),'false');
assert.equal(runInNewContext(source,f.ctx).ready,true,'Projection is installed once');
assert.equal(f.document.querySelectorAll('#hc-jd-identity').length,1);
for (const href of ['http://convenient-h5.jd.com/identityCode','https://convenient-h5.jd.com.evil.invalid/identityCode','https://convenient-h5.jd.com/other'])
 assert.equal(fixture(href).result,null,'Restrict projection to exact official identity route');
console.log('JD live SVG, official refresh, renewal failure, projection scope, zoom and theme checks passed');
