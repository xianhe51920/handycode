import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';
const script=readFileSync(new URL('../app/src/main/assets/pdd-login-notice.js',import.meta.url),'utf8');
function fixture({origin='https://m.pinduoduo.net',path='/login.html',embedded=false}={}){
 const {document,window:dom}=parseHTML('<html><head></head><body><div id="login"><div class="main"><div class="login-box"><button id="phone">手机登录</button></div><div class="login-footer">官方说明</div></div><div class="container"><form class="login-ui-form"><input value="fixture"><button disabled>登录</button></form></div></div></body></html>');
 const window={};window.top=embedded?{}:window;
 const context={window,document,location:{origin,pathname:path},MutationObserver:dom.MutationObserver,setTimeout:()=>{}};
 runInNewContext(script,context);return{window,document,context};
}
let f=fixture();
const input=f.document.querySelector('input'),form=f.document.querySelector('form');
for(const sel of ['#login > .main','#login > .container']){
 const surface=f.document.querySelector(sel);assert.equal(surface.lastElementChild.className,'hc-pdd-login-notice');
 assert.match(surface.lastElementChild.textContent,/小号登录/);assert.match(surface.lastElementChild.textContent,/添加手机号/);
}
runInNewContext(script,f.context);assert.equal(f.document.querySelectorAll('.hc-pdd-login-notice').length,2);
assert.equal(f.document.querySelector('input'),input);assert.equal(input.value,'fixture');
assert.equal(f.document.querySelector('form'),form);assert.equal(f.document.querySelector('form button').disabled,true);
assert.match(f.document.querySelector('style').textContent,/html\[data-hc-theme="dark"\]/);
f.document.querySelector('.container').innerHTML='<div class="phone-bind-ui"><input type="tel"></div>';
f.window.HandyPddLoginNotice.update();
assert.equal(f.document.querySelector('.container').lastElementChild.className,'hc-pdd-login-notice');
assert.equal(f.document.querySelectorAll('.hc-pdd-login-notice').length,2);
for(const opt of [{origin:'https://m.pinduoduo.net.evil.example'},{path:'/mdkd/package'},{embedded:true}]){
 assert.equal(fixture(opt).document.querySelector('.hc-pdd-login-notice'),null);
}
const java=readFileSync(new URL('../app/src/main/java/com/fairycrane/handycode/PddPage.java',import.meta.url),'utf8');
assert.doesNotMatch(java,/loginChoice|webLoginAccepted|继续网页登录/,'Native blocking login gate is removed');
console.log('PASS: in-flow login notice, dynamic phone form, idempotence, form state, theme and page scope; no native login gate');
