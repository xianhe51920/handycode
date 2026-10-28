import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';
const html=readFileSync(new URL('../app/src/main/assets/taobao.html',import.meta.url),'utf8');
const controller=readFileSync(new URL('../app/src/main/assets/refresh-controller.js',import.meta.url),'utf8');
const main=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];
const {window,document}=parseHTML(html);
let time=0,seq=0,detailClicks=0,latestDetailHandler=0,clickedDetailHandler=0;const tasks=new Map(),docs={},reloads={identity:0,parcels:0},animations=[];
const computed=e=>({display:e.style.display||'block',visibility:'visible',position:'static',overflowY:'visible',getPropertyValue:p=>e.style.getPropertyValue(p)||''});
window.getComputedStyle=computed;window.matchMedia=()=>({matches:false});
window.HTMLElement.prototype.getBoundingClientRect=function(){return {height:this.tagName==='NAV'?30:parseFloat(this.style.height)||this.expectedHeight||(this.id==='identity-content'?220:200)};};
window.HTMLElement.prototype.animate=function(frames,options){animations.push({node:this,frames,options});};
function source(part,label='fixture'){
 const {document:doc}=parseHTML(part==='identity'
  ? `<html><body><div class="identity--fixture"><p>${label}</p><img class="width-origin--fixture" src="data:image/png;base64,test-only" /></div></body></html>`
  : `<html><body><main><nav><span>待取件</span><span>追踪中</span><span>已签收</span><span>已退回</span></nav><p>${label}暂无包裹</p><button>查看详情</button></main></body></html>`);
 Object.defineProperties(doc,{readyState:{value:'complete'},URL:{value:'https://example.test/'+part}});
 doc.defaultView.getComputedStyle=computed;
 if(part==='identity')Object.defineProperties(doc.querySelector('img'),{complete:{value:true},naturalWidth:{value:600}});
 else {const version=++latestDetailHandler;doc.querySelector('button').addEventListener('click',()=>{detailClicks++;clickedDetailHandler=version;});}
 return doc;
}
for(const part of ['identity','parcels']){
 docs[part]=source(part);
 const frame=document.getElementById(part+'-source');
 Object.defineProperties(frame,{contentDocument:{get:()=>docs[part]},src:{set(){reloads[part]++;}}});
}
const context={window,document,Date:{now:()=>time},WeakMap,MutationObserver:class{observe(){}disconnect(){}},
 setTimeout(fn,delay){tasks.set(++seq,{fn,at:time+delay});return seq;},clearTimeout:id=>tasks.delete(id),setInterval(){},
 HandyCodeOfficialIdentity:{refreshControl:()=>false,recover(){}}};
runInNewContext(controller,context);runInNewContext(main,context);
function advance(ms){const end=time+ms;for(;;){const next=[...tasks].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!next)break;time=next[1].at;tasks.delete(next[0]);next[1].fn();}time=end;}
advance(1200);
const identity=document.getElementById('identity-content'),parcels=document.getElementById('parcels-content');
assert.equal(identity.dataset.ready,'true');assert.equal(parcels.dataset.ready,'true');
const parcelBefore=parcels.firstElementChild;
const idButton=document.querySelector('[data-refresh="identity"]'),parcelButton=document.querySelector('[data-refresh="parcels"]');
document.getElementById('parcels-source').dispatchEvent(new window.Event('load'));
assert.equal(parcels.firstElementChild,parcelBefore,'Late load of the same projected document must not restart its skeleton');
assert.equal(parcelButton.disabled,false);
idButton.click();idButton.click();
assert.deepEqual(reloads,{identity:1,parcels:0});
assert.ok(identity.querySelector('.hc-section-placeholder'),'Clicked card immediately shows animated skeleton');
assert.equal(identity.style.height,'220px','Skeleton retains the measured card height');
assert.equal(parcels.firstElementChild,parcelBefore);assert.equal(parcelButton.disabled,false);
advance(300);assert.ok(identity.querySelector('.hc-section-placeholder'),'Old iframe cannot restore old code during navigation');
identity.expectedHeight=260;docs.identity=source('identity','new-fixture');advance(300);
assert.equal(identity.dataset.ready,'true');assert.equal(identity.style.height,'');
assert.ok(identity.textContent.includes('new-fixture'));
assert.ok(animations.some(a=>a.node===identity&&a.frames[0].height==='220px'&&a.frames[1].height==='260px'));
assert.equal(parcels.firstElementChild,parcelBefore);assert.equal(idButton.disabled,false);
const identityBefore=identity.firstElementChild;
parcelButton.click();assert.ok(parcels.querySelector('.hc-section-placeholder'));
assert.equal(identity.firstElementChild,identityBefore);assert.equal(idButton.disabled,false);
docs.parcels=source('parcels','updated-parcels');advance(500);
assert.equal(parcels.dataset.ready,'true');assert.ok(parcels.textContent.includes('updated-parcels'));
assert.equal(identity.firstElementChild,identityBefore);
assert.deepEqual(reloads,{identity:1,parcels:1});
const stableParcels=parcels.firstElementChild;
for(let i=0;i<5;i++){
 // New Document wrappers and late resources reproduce the old repeated
 // controller.start -> skeleton -> finish loop after one manual refresh.
 docs.parcels=source('parcels','updated-parcels');
 document.getElementById('parcels-source').dispatchEvent(new window.Event('load'));
 assert.equal(parcelButton.disabled,false,'Late loads must never re-enter refreshing');
 assert.equal(parcels.firstElementChild,stableParcels);
 advance(250);
 assert.equal(parcelButton.disabled,false);
 assert.equal(parcels.firstElementChild,stableParcels,'Equivalent visible data keeps its existing DOM and animation');
 docs.parcels.querySelector('main').setAttribute('data-report-tick',String(i));
 docs.parcels.dispatchEvent(new window.Event('load'));
 advance(150);
 assert.equal(parcels.firstElementChild,stableParcels,'Source exposure metadata does not replace visible content');
}
stableParcels.querySelector('button').click();
assert.equal(detailClicks,1,'Preserved projection keeps current official source handlers');
assert.equal(clickedDetailHandler,latestDetailHandler,'Preserved buttons invoke the latest live source, not a detached earlier document');
docs.parcels=source('parcels','passive-new-result');
document.getElementById('parcels-source').dispatchEvent(new window.Event('load'));advance(300);
assert.ok(parcels.textContent.includes('passive-new-result'),'Real passive data changes still reach the card');
assert.equal(parcels.querySelector('.hc-section-enter'),null,'Passive updates do not replay the entry fade');
assert.equal(parcelButton.disabled,false);assert.equal(identity.firstElementChild,identityBefore);
console.log('PASS: Taobao section skeleton, measured height, content transition, independent controls, stale-document guard and stable other subtree');
