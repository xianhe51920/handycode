import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';
const source=readFileSync(new URL('../app/src/main/assets/login-agreements.js',import.meta.url),'utf8');
function fixture(origin,container,embedded=false,path='/login.html') {
 const {document}=parseHTML(`<html><body><div class="${container}"><input id="check" type="checkbox"><a id="legal" href="https://example.invalid"><span>隐私政策</span></a><span role="checkbox" id="custom"></span><span class="link" id="router">服务条款</span></div><input type="tel" value="fixture"><button disabled>登录</button><a id="other">账号密码登录</a></body></html>`);
 const handlers=[];document.addEventListener=(name,fn,capture)=>handlers.push({name,fn,capture});
 const window={};window.top=embedded?{}:window;
 const context={window,document,location:{origin,pathname:path}};runInNewContext(source,context);
 function click(selector){let e={target:document.querySelector(selector),preventDefault(){this.blocked=true},stopImmediatePropagation(){this.stopped=true}};handlers.forEach(h=>h.fn(e));return e}
 return {document,handlers,context,click};
}
for(const [origin,container,embedded] of [['https://login.m.taobao.com','fm-agreement',true],['https://login.taobao.com','fm-optional-agreement',false],['https://m.pinduoduo.net','agreement-wrap',false],['https://plogin.m.jd.com','policy_tip',false]]) {
 const f=fixture(origin,container,embedded);runInNewContext(source,f.context);assert.equal(f.handlers.length,1);assert.equal(f.handlers[0].capture,true);
 for(const sel of ['#legal','#legal span','#router']){const e=f.click(sel);assert.equal(e.blocked,true);assert.equal(e.stopped,true)}
 for(const sel of ['#check','#custom','button','#other','input[type=tel]'])assert.equal(f.click(sel).blocked,undefined);
 assert.equal(f.document.querySelector('#check').checked,undefined);assert.equal(f.document.querySelector('button').disabled,true);assert.equal(f.document.querySelector('input[type=tel]').value,'fixture');
 f.document.querySelector('#legal').innerHTML='<b>新验证码页协议</b>';assert.equal(f.click('#legal b').blocked,true,'Late-rendered OTP links are covered');
}
for(const args of [['https://evil.example','agreement-wrap'],['https://m.pinduoduo.net.evil.example','agreement-wrap'],['https://m.pinduoduo.net','agreement-wrap',false,'/mdkd/package'],['https://m.pinduoduo.net','agreement-wrap',true]])assert.equal(fixture(...args).handlers.length,0);
console.log('PASS: login agreement navigation blocked; checkboxes, official form state, other controls and page scope preserved');
