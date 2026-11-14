import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script=readFileSync(new URL('../app/src/main/assets/taobao-login-theme.js',import.meta.url),'utf8');
function fixture(origin='https://login.m.taobao.com',embedded=false){
 const {document,window:dom}=parseHTML('<html><head></head><body class="mobile"><div id="login"><input class="fm-text" type="tel" value="fixture"><input type="checkbox" checked><button class="fm-button" disabled>获取验证码</button></div></body></html>');
 const events={},tasks=[];const parent={postMessage:()=>{}};
 document.querySelectorAll('input,.sms-container,.login-error-dialog').forEach(n=>n.getClientRects=()=>[]);
 const window={__HC_TAOBAO_DARK__:true,addEventListener:(name,fn)=>events[name]=fn};
 window.parent=embedded?parent:window;
 const context={window,document,location:{origin,href:origin+'/login.htm'},URL,MutationObserver:dom.MutationObserver,setTimeout:fn=>tasks.push(fn)};
 runInNewContext(script,context);
 return {window,document,events,parent,context,tasks};
}
let f=fixture();
assert.equal(f.document.documentElement.getAttribute('data-hc-taobao-login'),'dark');
let css=f.document.getElementById('hc-taobao-login-theme').textContent;
assert.match(css,/\.view-input/,'OTP visible digit boxes use the same theme');
assert.match(css,/\.sms-send-tip-number/);assert.match(css,/input::placeholder/);
const input=f.document.querySelector('input'),button=f.document.querySelector('button');
f.window.HandyCodeTaobaoLoginTheme.setDark(false);
assert.equal(f.document.documentElement.getAttribute('data-hc-taobao-login'),'light');
assert.equal(f.document.querySelector('input'),input);assert.equal(input.value,'fixture');
assert.equal(button.disabled,true);assert.equal(f.document.querySelector('[type=checkbox]').hasAttribute('checked'),true);
// The official app switches screens and can rebuild head after the hook ran.
f.document.querySelector('#login').innerHTML='<div class="sms-container"><div class="sim-input-container"><div class="tel-box-container"><div class="view-input sms-login-input focus">1</div></div></div><input type="tel" value="otp-fixture"></div>';
f.document.head.innerHTML='';
f.window.HandyCodeTaobaoLoginTheme.setDark(true);
assert.equal(f.document.querySelector('.view-input').textContent,'1');
assert.equal(f.document.querySelector('input').value,'otp-fixture');
runInNewContext(script,f.context);
assert.equal(f.document.querySelectorAll('#hc-taobao-login-theme').length,1);

f=fixture('https://login.m.taobao.com',true);
const send=(origin,source,dark)=>f.events.message({origin,source,data:{type:'hc-taobao-login-theme',dark}});
send('https://evil.example',f.parent,false);
send('https://pages-fast.m.taobao.com',{},false);
send('https://pages-fast.m.taobao.com',f.parent,'false');
assert.equal(f.document.documentElement.getAttribute('data-hc-taobao-login'),'dark');
send('https://pages-fast.m.taobao.com',f.parent,false);
assert.equal(f.document.documentElement.getAttribute('data-hc-taobao-login'),'light');

f=fixture('https://pages-fast.m.taobao.com');
const frame=f.document.createElement('iframe');frame.src='https://login.m.taobao.com/login.htm';
const sent=[];frame.contentWindow={postMessage:(message,origin)=>sent.push({message,origin})};f.document.body.append(frame);
const other=f.document.createElement('iframe');other.src='https://login.m.taobao.com.evil.example/';other.contentWindow={postMessage:()=>assert.fail('untrusted frame')};f.document.body.append(other);
f.window.HandyCodeTaobaoLoginTheme.setDark(false);
assert.deepEqual(JSON.parse(JSON.stringify(sent)),[{message:{type:'hc-taobao-login-theme',dark:false},origin:'https://login.m.taobao.com'}]);
assert.equal(f.document.getElementById('hc-taobao-login-theme'),null,'Identity host only forwards theme; card geometry is unaffected');
frame.getClientRects=()=>[{}];other.getClientRects=()=>[{}];
f.context.getComputedStyle=()=>({display:'block',visibility:'visible'});
assert.equal(f.window.HandyCodeTaobaoLoginTheme.isSettled(false),false,'Parent must wait for the login iframe theme acknowledgement');
f.events.message({origin:'https://evil.example',source:frame.contentWindow,data:{type:'hc-taobao-theme-painted',dark:false}});
assert.equal(f.window.HandyCodeTaobaoLoginTheme.isSettled(false),false);
f.events.message({origin:'https://login.m.taobao.com',source:frame.contentWindow,data:{type:'hc-taobao-theme-painted',dark:false}});
assert.equal(f.window.HandyCodeTaobaoLoginTheme.isSettled(false),true);
f.window.HandyCodeTaobaoLoginTheme.setDark(true);
assert.equal(f.window.HandyCodeTaobaoLoginTheme.isSettled(true),false,'An old acknowledgement cannot release the next transition');
assert.match(f.document.getElementById('hc-taobao-host-theme').textContent,/iframe\{background-color:#0f1218/,'Parent and initial iframe do not paint a white canvas');
f=fixture();
const frames=[],phases=[];
f.context.setTimeout=fn=>frames.push(fn);
f.context.getComputedStyle=()=>({visibility:'visible'});
f.context.console={info:value=>phases.push(value)};
f.document.querySelector('input[type=tel]').getClientRects=()=>[{}];
f.window.HandyCodeTaobaoLoginTheme.setDark(true);
assert.equal(phases.length,0,'Do not reveal the login frame before painted theme');
frames.shift()();assert.deepEqual(phases,['__HANDYCODE_TAOBAO_LOGIN__:ready']);
f.window.HandyCodeTaobaoLoginTheme.setDark(false);assert.equal(phases.length,1,'Theme changes never restart the readiness flow');
for(const origin of ['http://login.m.taobao.com','https://login.m.taobao.com.evil.example','https://m.pinduoduo.net']){
 assert.equal(fixture(origin).window.HandyCodeTaobaoLoginTheme,undefined);
}
console.log('PASS: official phone/OTP theme, reversible styles, form state, frame targeting and trusted theme messages');
