import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script = readFileSync(new URL('../app/src/main/assets/pdd-layout.js', import.meta.url), 'utf8');
const motion = readFileSync(new URL('../app/src/main/assets/pickup-motion.js', import.meta.url), 'utf8');
// Synthetic official-shaped DOM: deliberately rotated, with unrelated content
// and live source buttons. No real account or barcode data belongs in fixtures.
function fixture({path='/mdkd/package', subframe=false, parcels='empty', delayedSource=false}={}) {
 const {window, document} = parseHTML(`<html><head><meta name="viewport" content="width=375,initial-scale=1"></head><body><div id="__next">
 <div class="example_identification-code-container__hash">
  <div class="identification-code-tips">自动更新 <span>切换灯条身份码</span></div>
  <div style="transform:rotate(90deg)">
   <img class="identification-code-img" src="data:image/png;base64,fixture" />
   <div class="identification-code-content-code">fixture-code</div>
   <div class="identification-code-mobile-info">已绑定常用手机号：测试手机号 <span>更换</span></div>
   <div class="identification-code-content-text">请勿分享</div>
  </div>
 </div>
 ${parcels === 'empty' ? '<div class="empty-package_package-none-container__hash"><p>暂无快递</p><button>绑定手机号</button><button>添加快递</button></div>' : '<div class="example_package-list-wrapper__hash"><div class="package-list-picking" style="overflow-y:auto;height:50px"><button>待取件详情</button></div><div>已取件记录</div></div>'}
 <div class="recommendations">推荐商品广告</div><div class="rm-tab-bar-tab">个人中心</div>
 </div></body></html>`);
 const image = document.querySelector('img');
 Object.defineProperties(image, {complete:{value:true,writable:true},naturalWidth:{value:900,writable:true},currentSrc:{get(){return this.src;}}});
 const location={origin:'https://m.pinduoduo.net',pathname:path,reload(){reloads++;}};
 const phases=[], timers=[], intervals=[], refreshCalls=[], pending={}, observers=[]; let reloads=0, clicks=0, backs=0, timerId=0;
 document.querySelectorAll('#__next button, #__next span').forEach(e=>e.addEventListener('click',()=>clicks++));
 const context={window,document,location,history:{back(){backs++;location.pathname='/mdkd/package';}},console:{info:v=>phases.push(v)},
  getComputedStyle:e=>({display:e.style.display || 'block',visibility:'visible',position:e.style.position || 'static',overflowY:e.style.overflowY || 'visible',getPropertyValue:p=>e.style.getPropertyValue(p)||''}),
  MutationObserver:class{constructor(callback){this.callback=callback;observers.push(this);}observe(){this.active=true;}disconnect(){this.active=false;}},
  setTimeout(fn,delay){const id=++timerId;timers.push({fn,delay,id});return id;},clearTimeout(id){const timer=timers.find(task=>task.id===id);if(timer)timer.cancelled=true;},setInterval(fn,delay){intervals.push({fn,delay});return intervals.length;},clearInterval(){}};
 // LinkeDOM exposes assigned window globals through a shared proxy.
 delete window.HandyPddLayout;
 window.matchMedia=()=>({matches:false});
 window.HandyPddRefresh={run(part){refreshCalls.push(part);return new Promise((resolve,reject)=>{pending[part]={resolve,reject};});}};
 window.top=subframe?{}:window;
 const source=document.getElementById('__next');
 if(delayedSource)source.remove();
 runInNewContext(motion + '\n' + script,context);
 return {window,document,image,context,phases,refreshCalls,intervals,
  mutate(target){for(const observer of observers)if(observer.active)observer.callback([{target,type:'characterData'}]);},
  flushProjection(){const job=timers.find(task=>task.delay===160&&!task.cancelled);if(job){job.cancelled=true;job.fn();}},
  mountSource(){document.body.appendChild(source);for(const observer of [...observers])if(observer.active)observer.callback();},
  expireBootWait(){for(const task of timers)if(task.delay===15000&&!task.cancelled)task.fn();},
  async finishRefresh(part,error=false){await new Promise(resolve=>setImmediate(resolve));error?pending[part].reject(new Error('test-failure')):pending[part].resolve();await new Promise(resolve=>setImmediate(resolve));const scheduled=timers.splice(0);for(const task of scheduled)if(task.delay===40)task.fn();},
  get reloads(){return reloads;},get clicks(){return clicks;},get backs(){return backs;}};
}
let f=fixture();
const shell=f.document.querySelector('#hc-pdd-shell');
assert.equal(shell.querySelectorAll('.hc-pdd-card').length,2);
assert.match(f.document.querySelector('meta[name="viewport"]').content,/width=device-width/,'Cards use the same viewport scale as the native loading cover');
const logout = shell.querySelector('#hc-logout');
assert.equal(logout.previousElementSibling.querySelector('.hc-pdd-heading h2').textContent,'待取包裹');
assert.equal(logout.hidden,false,'The authenticated logout action follows both cards');
assert.equal(logout.getAttribute('href'),'https://handycode.invalid/actions/logout');
assert.ok(f.document.querySelector('#__next').hasAttribute('data-hc-pdd-source'));
assert.ok(!shell.textContent.includes('推荐商品广告'));
assert.ok(!shell.textContent.includes('个人中心'));
assert.equal(shell.querySelector('.hc-pdd-barcode').src,f.image.src,'Official image is reused unchanged');
assert.equal(shell.querySelectorAll('[style*="rotate"]').length,0,'Projection has no source rotation');
assert.equal(shell.querySelector('.hc-pdd-phone').textContent,'测试手机号');
assert.doesNotMatch(shell.querySelector('#hc-pdd-identity').textContent,/fixture-code|请勿分享|已绑定常用手机号/);
assert.equal(shell.querySelectorAll('#hc-pdd-parcels button').length,2);
assert.ok(f.phases.includes('__HANDYCODE_PDD_LAYOUT__:cards'));
assert.equal(f.phases.filter(value=>value==='__HANDYCODE_PDD_AUTH__:signed-in').length,1);
f.window.HandyPddLayout.update();
assert.equal(f.phases.filter(value=>value==='__HANDYCODE_PDD_AUTH__:signed-in').length,1,'Only a fixed auth state is reported once; no account values');
shell.querySelector('#hc-pdd-parcels button').click();
assert.equal(f.clicks,1,'Official source handler still receives clicks');
assert.equal(shell.hidden,true,'Official forms open in an explicit source workflow');
assert.equal(f.document.querySelector('meta[name="viewport"]').content,'width=375,initial-scale=1','Restore the official viewport for original workflows');
assert.equal(f.window.HandyPddLayout.back(),true);
assert.match(f.document.querySelector('meta[name="viewport"]').content,/width=device-width/,'Returning to cards restores the device viewport');
assert.equal(shell.hidden,false);
assert.equal(f.window.HandyPddLayout.back(),false);
f.context.location.pathname='/mdkd/cellPhoneList';
f.window.HandyPddLayout.update();
assert.equal(shell.hidden,true,'Official client navigation reveals its page');
f.window.HandyPddLayout.back();
assert.equal(f.backs,1,'Return restores the official package route first');
f.window.HandyPddLayout.back();
shell.querySelector('.hc-pdd-tips span').click();
assert.equal(f.clicks,2);
assert.equal(shell.hidden,false,'Code style switch stays in combined view');
f.image.src='data:image/png;base64,updated-fixture';
f.window.HandyPddLayout.update();
assert.equal(shell.querySelector('.hc-pdd-barcode').src,f.image.src);
const identityMount=shell.querySelector('#hc-pdd-identity'), animations=[];
identityMount.getBoundingClientRect=()=>({height:identityMount.textContent.includes('taller-fixture')?340:310});
identityMount.animate=(frames,options)=>animations.push({frames,options});
f.document.querySelector('.identification-code-mobile-info').firstChild.nodeValue='已绑定常用手机号：taller-fixture ';
f.window.HandyPddLayout.update();
assert.equal(animations.length,1,'Exceptional height changes are animated');
assert.equal(animations[0].frames[0].height,'310px');
assert.equal(animations[0].frames[1].height,'340px');
f.image.complete=false;
f.window.HandyPddLayout.update();
assert.equal(shell.querySelector('.hc-pdd-barcode'),null,'Stale barcode is removed while official image reloads');
assert.ok(shell.querySelector('#hc-pdd-identity .hc-pdd-placeholder'));
f.image.complete=true;
f.window.HandyPddLayout.update();
shell.querySelector('[data-hc-pdd-refresh]').click();
shell.querySelector('[data-hc-pdd-refresh]').click();
const parcelsBefore=shell.querySelector('#hc-pdd-parcels').firstElementChild;
assert.equal(shell.querySelectorAll('[data-hc-pdd-refresh]')[1].disabled,false,'Other card remains usable');
assert.equal(shell.querySelector('.hc-pdd-barcode'),null,'Refresh immediately removes the old code');
f.window.HandyPddLayout.update();
assert.equal(shell.querySelector('.hc-pdd-barcode'),null,'Polling cannot restore stale content during the request');
assert.equal(shell.querySelectorAll('#hc-pdd-identity .hc-load-dots span').length,3);
await f.finishRefresh('identity');
assert.deepEqual(f.refreshCalls,['identity'],'Only the requested callback runs and repeat taps coalesce');
assert.equal(f.reloads,0,'A section refresh never reloads the document');
assert.equal(shell.querySelector('#hc-pdd-parcels').firstElementChild,parcelsBefore,'Identity refresh preserves the exact other DOM subtree');
const barcodeBefore=shell.querySelector('.hc-pdd-barcode');
shell.querySelectorAll('[data-hc-pdd-refresh]')[1].click();
assert.equal(shell.querySelector('.hc-pdd-barcode'),barcodeBefore);
await f.finishRefresh('parcels');
assert.deepEqual(f.refreshCalls,['identity','parcels']);
assert.equal(shell.querySelector('.hc-pdd-barcode'),barcodeBefore,'Parcel refresh preserves the identity image and its handlers');
const lastSuccessfulParcels=shell.querySelector('#hc-pdd-parcels').firstElementChild;
shell.querySelectorAll('[data-hc-pdd-refresh]')[1].click();
await f.finishRefresh('parcels',true);
f.window.HandyPddLayout.update();
assert.ok(shell.querySelector('#hc-pdd-parcels .hc-pdd-wait'),'Failure stays local and is not overwritten by old source data');
assert.equal(shell.querySelector('#hc-pdd-parcels').firstElementChild,lastSuccessfulParcels,'A failure restores the last successful parcel view');
assert.equal(shell.querySelector('.hc-pdd-barcode'),barcodeBefore);
assert.equal(shell.querySelectorAll('[data-hc-pdd-refresh]')[1].textContent,'重试');
shell.querySelectorAll('[data-hc-pdd-refresh]')[1].click();
await f.finishRefresh('parcels');
assert.ok(!shell.querySelector('#hc-pdd-parcels .hc-pdd-wait'),'Failed section can retry successfully');
f=fixture({parcels:'list'});
assert.ok(f.document.querySelector('#hc-pdd-parcels').textContent.includes('待取件详情'));
assert.ok(!f.document.querySelector('#hc-pdd-parcels').textContent.includes('已取件记录'));
assert.equal(f.document.querySelector('#hc-pdd-parcels [style*="height:50px"]'),null,'Inner scrolling is flattened');
f.document.querySelector('#hc-pdd-parcels button').click();
assert.equal(f.clicks,0,'Populated parcel details do not forward to the official view');
assert.equal(f.document.querySelector('#hc-pdd-shell').hidden,false,'Populated parcel taps stay on the combined cards');
const pending = f.document.querySelector('.package-list-picking');
pending.insertAdjacentHTML('beforeend', '<div class="package-tags"><span class="goods-return-tag">支持退换货</span></div><div class="package-location-phone">导航 电话</div><div class="package-pick-disable-tip">该代收点仅支持自助取件</div><div class="package-item-fill"><img alt="checkbox"></div><div class="package-code"><div><span>取件码</span> TEST-456</div><div><img alt="menu"></div></div><div class="package-shipping-name">测试快递<span class="package-picture-check">照片</span></div><div class="package-item-operate">全选 找人代取 通知站长取件</div>');
f.window.HandyPddLayout.update();
const updatedParcels = f.document.getElementById('hc-pdd-parcels');
assert.match(updatedParcels.textContent,/TEST-456.*测试快递/);
assert.doesNotMatch(updatedParcels.textContent,/支持退换货|导航|电话|仅支持自助取件|照片|全选|找人代取|通知站长取件/);
assert.equal(updatedParcels.querySelector('img[alt="checkbox"],img[alt="menu"]'),null);
const phoneFlow=fixture();
const phoneViewport=phoneFlow.document.querySelector('meta[name="viewport"]');
phoneViewport.content='width=375,initial-scale=0.96';
phoneFlow.mutate(phoneViewport);
const phoneShell=phoneFlow.document.querySelector('#hc-pdd-shell');
phoneShell.querySelector('.hc-pdd-phone-manage').click();
assert.equal(phoneFlow.clicks,1,'Phone management still invokes the official action');
assert.equal(phoneShell.hidden,false,'The source identity page must not flash during navigation');
phoneFlow.context.location.pathname='/mdkd/cellPhoneList';
phoneFlow.window.HandyPddLayout.update();
assert.equal(phoneShell.hidden,false,'Changing the route alone must not expose the previous DOM');
phoneFlow.document.querySelector('[class*="identification-code-container"]').remove();
phoneFlow.mutate(phoneFlow.document.getElementById('__next'));
assert.equal(phoneShell.hidden,true,'The official phone page can appear after the old DOM is gone');
assert.equal(phoneViewport.content,'width=375,initial-scale=0.96','Restore viewport settings written late by the official page');
// Background updates keep concrete, visible nodes; there is no persistent
// opacity-zero backwards-fill CSS to reveal an empty card on the next swipe.
f=fixture();
const retained=f.document.querySelector('#hc-pdd-identity').firstElementChild;
f.window.HandyPddLayout.update();
assert.equal(f.document.querySelector('#hc-pdd-identity').firstElementChild,retained);
const entryEffects=[];
const create=f.document.createElement.bind(f.document);
f.document.createElement=function(tag){
 const node=create(tag);
 node.animate=(frames,options)=>{const effect={frames,options,cancel(){this.cancelled=true;this.oncancel?.();}};entryEffects.push(effect);return effect;};
 return node;
};
f.document.querySelector('.identification-code-content-code').textContent='passive-update';
f.window.HandyPddLayout.update();
assert.equal(entryEffects.length,0,'Passive data updates do not replay the entry fade');
f.image.complete=false;f.window.HandyPddLayout.update();
f.image.complete=true;f.window.HandyPddLayout.update();
assert.equal(entryEffects.length,1,'Newly ready visible content still fades in');
Object.defineProperty(f.document,'hidden',{value:true,writable:true,configurable:true});
f.document.dispatchEvent(new f.window.Event('visibilitychange'));
assert.equal(entryEffects[0].cancelled,true,'Hiding cancels the entry effect and restores base opacity');
f.image.complete=false;f.window.HandyPddLayout.update();
f.image.complete=true;f.window.HandyPddLayout.update();
assert.equal(entryEffects.length,1,'Background completion has no suspended transparent entry frame');
assert.ok(f.document.querySelector('.hc-pdd-barcode'));
assert.ok(!script.includes('hc-pdd-reveal .22s ease-out both'));
for(const options of [{path:'/login.html'},{subframe:true}]) {
 f=fixture(options);
 assert.equal(f.document.querySelector('#hc-pdd-shell'),null,'Login and subframes remain untouched');
}
f=fixture({delayedSource:true});
assert.equal(f.document.querySelector('#hc-pdd-shell'),null,'Root is intentionally absent at early bundle execution');
f.mountSource();
assert.ok(f.document.querySelector('#hc-pdd-shell'),'A late official root initializes without a manual reload');
assert.ok(f.document.querySelector('.hc-pdd-barcode'));
f=fixture({delayedSource:true});
f.expireBootWait();
assert.ok(f.phases.includes('__HANDYCODE_PDD_LAYOUT__:unavailable'));
f.mountSource();
assert.ok(f.document.querySelector('.hc-pdd-barcode'),'Even a root arriving after the warning still recovers without reload');

f=fixture({parcels:'list'});
const identityRoot=f.document.querySelector('[class*="identification-code-container"]');
const parcelRoot=f.document.querySelector('.package-list-picking');
const scans={identity:0,parcels:0};
for(const [part,root] of [['identity',identityRoot],['parcels',parcelRoot]]){
 let proto=Object.getPrototypeOf(root);
 while(proto&&!Object.getOwnPropertyDescriptor(proto,'outerHTML'))proto=Object.getPrototypeOf(proto);
 const read=Object.getOwnPropertyDescriptor(proto,'outerHTML').get;
 Object.defineProperty(root,'outerHTML',{get(){scans[part]++;return read.call(this);}});
}
f.mutate(f.document.querySelector('.recommendations').firstChild);
f.flushProjection();
assert.deepEqual(scans,{identity:0,parcels:0},'Unrelated updates do not serialize either card');
parcelRoot.querySelector('button').textContent='新到包裹';
f.mutate(parcelRoot.querySelector('button').firstChild);
f.flushProjection();
assert.deepEqual(scans,{identity:0,parcels:1},'A parcel update serializes only its own section once');
assert.match(f.document.getElementById('hc-pdd-parcels').textContent,/新到包裹/);
f.window.HandyPddLayout.setActive(false);
parcelRoot.querySelector('button').textContent='后台到件';
f.mutate(parcelRoot.querySelector('button').firstChild);
f.intervals[0].fn();
f.flushProjection();
assert.deepEqual(scans,{identity:0,parcels:1},'An inactive tab does no projection work');
f.window.HandyPddLayout.setActive(true);
f.flushProjection();
assert.match(f.document.getElementById('hc-pdd-parcels').textContent,/后台到件/);
assert.ok(f.intervals[0].delay>=15000,'The foreground fallback is low frequency');
const managePhone=f.document.querySelector('.hc-pdd-phone-manage');
assert.equal(managePhone.textContent,'添加手机号');
assert.equal(managePhone.closest('.hc-pdd-heading').querySelector('h2').textContent,'待取包裹');
assert.equal(f.document.querySelector('.hc-pdd-phone').textContent.includes('更换'),false);
managePhone.click();
assert.equal(f.clicks,1,'The phone shortcut forwards the official action even with parcels present');
assert.equal(f.document.getElementById('hc-pdd-shell').hidden,false,'Keep the current cards until the official route is ready');
console.log('PDD layout DOM checks passed: two cards, official barcode, updates, independent actions, scope and late-root startup recovery');
