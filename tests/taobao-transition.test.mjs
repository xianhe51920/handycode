import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';
const html=readFileSync(new URL('../app/src/main/assets/taobao.html',import.meta.url),'utf8');
const controller=readFileSync(new URL('../app/src/main/assets/refresh-controller.js',import.meta.url),'utf8');
const motion=readFileSync(new URL('../app/src/main/assets/pickup-motion.js',import.meta.url),'utf8');
const main=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];
const {window,document}=parseHTML(html);
let time=0,seq=0,detailClicks=0,manageClicks=0,latestDetailHandler=0,clickedDetailHandler=0;const tasks=new Map(),docs={},reloads={identity:0,parcels:0},animations=[];
const computed=e=>({display:e.style.display||'block',visibility:'visible',position:'static',overflowY:'visible',getPropertyValue:p=>e.style.getPropertyValue(p)||''});
window.getComputedStyle=computed;window.matchMedia=()=>({matches:false});
window.HTMLElement.prototype.getBoundingClientRect=function(){return {height:this.tagName==='NAV'?30:parseFloat(this.style.height)||this.expectedHeight||(this.id==='identity-content'?220:200)};};
window.HTMLElement.prototype.animate=function(frames,options){animations.push({node:this,frames,options});};
function source(part,label='fixture'){
 const {document:doc}=parseHTML(part==='identity'
  ? `<html><body><div class="identity--fixture"><p>${label}</p><div class="have-authenticated">已认证</div><img class="width-origin--fixture" src="data:image/png;base64,test-only" /><div class="code--fixture">DEMO-CODE-TEXT</div></div><a href="/phone-manage"><span>号码管理</span></a></body></html>`
  : `<html><body><main><nav><span>待取件</span><span>追踪中</span><span>已签收</span><span>已退回</span></nav><p>${label}取件码 TEST-123</p><figure class="holder--fixture"><img alt="包裹缩略图"><div class="thumbnail--fixture"><svg></svg><div class="text--fixture">看包裹</div></div></figure><button>查看详情</button><button>查看地址</button><div><svg></svg>求助</div><div class="position--fixture">点击 能看包裹图 <img></div><div class="container--fixture"><i></i><div class="package--fixture">还有包裹未显示？查询取件码</div></div><div class="operation--fixture"><i></i><div>好友代取</div></div></main></body></html>`);
 Object.defineProperties(doc,{readyState:{value:'complete'},URL:{value:'https://example.test/'+part}});
 doc.defaultView.getComputedStyle=computed;
 if(part==='identity'){
  Object.defineProperties(doc.querySelector('img'),{complete:{value:true},naturalWidth:{value:600}});
  doc.querySelector('a').addEventListener('click',()=>manageClicks++);
 }
 else {
  doc.querySelector('main').insertAdjacentHTML('beforeend','<div class="stations--fixture"><div class="containerWrapper--fixture"><div class="line--fixture" style="border-left:1px dashed rgb(229, 232, 236);border-left-color:rgb(229, 232, 236)"></div><div data-role="station"><div class="station--fixture"><svg><path fill="#11192D" d="M0 0h10v10H0z"></path></svg></div></div></div></div>');
  const version=++latestDetailHandler;doc.querySelector('button').addEventListener('click',()=>{detailClicks++;clickedDetailHandler=version;});
 }
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
runInNewContext(controller,context);runInNewContext(motion,context);runInNewContext(main,context);
const logout = document.querySelector('#hc-logout');
assert.equal(logout.previousElementSibling.querySelector('.hc-heading h2').textContent,'待取包裹');
assert.equal(logout.hidden,true,'Native authentication must reveal the logout action');
assert.equal(logout.getAttribute('href'),'https://handycode.invalid/actions/logout');
function advance(ms){const end=time+ms;for(;;){const next=[...tasks].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!next)break;time=next[1].at;tasks.delete(next[0]);next[1].fn();}time=end;}
advance(1200);
const identity=document.getElementById('identity-content'),parcels=document.getElementById('parcels-content');
assert.equal(identity.dataset.ready,'true');assert.equal(parcels.dataset.ready,'true');
assert.ok(identity.querySelector('img'));
const barcodeStyle = identity.querySelector('[data-hc-barcode]').getAttribute('style');
window.HandyCodeSetDark(true);
assert.equal(identity.querySelector('[data-hc-barcode]').getAttribute('style'),barcodeStyle,'Dark mode keeps the barcode dimensions and quiet zone');
window.HandyCodeSetDark(false);
assert.equal(identity.querySelector('[data-hc-barcode]').getAttribute('style'),barcodeStyle,'Light mode retains identical barcode geometry');
assert.doesNotMatch(identity.textContent,/已认证|DEMO-CODE-TEXT/);
const phoneManage=document.querySelector('[data-phone-manage]');
assert.equal(phoneManage.hidden,false);
phoneManage.click();
assert.equal(manageClicks,1,'Parcel heading delegates to the live official phone management control');
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
assert.ok(parcels.textContent.includes('取件码 TEST-123'));
assert.doesNotMatch(parcels.textContent,/查看地址|求助|看包裹|还有包裹未显示|好友代取/);
assert.equal(parcels.querySelector('figure').children.length,1,'The thumbnail remains and the entire badge is removed');
assert.ok(parcels.querySelector('figure img[alt="包裹缩略图"]'));
const stationIcon=parcels.querySelector('[data-hc-station-icon]');
assert.ok(stationIcon?.querySelector('svg'),'The station glyph is tagged for theme styling');
const guide=parcels.querySelector('[style*="--hc-parcel-guide"]');
assert.equal(guide.style.getPropertyValue('border-left-color'),'var(--hc-parcel-guide, rgb(229, 232, 236))');
window.HandyCodeSetDark(true);
assert.ok(stationIcon.querySelector('svg path[fill="#11192D"]'),'Theme styling keeps the official vector intact');
window.HandyCodeSetDark(false);
assert.equal(guide.style.getPropertyValue('border-left-color'),'var(--hc-parcel-guide, rgb(229, 232, 236))','Light mode restores the original guide color');
assert.equal(parcels.querySelector('.hc-pickup-outgoing'),null,'Passive updates do not replay the skeleton exit');
assert.equal(parcelButton.disabled,false);assert.equal(identity.firstElementChild,identityBefore);
const emptyDoc=source('parcels','empty-result');
emptyDoc.querySelector('p').outerHTML='<div class="empty--fixture"><div class="title--fixture"><div class="inner--fixture" style="display:flex;width:280px">您的<strong style="display:flex;width:95px;padding-left:4px">2个手机号<svg></svg></strong>都未查到包裹</div></div><div class="action--fixture"><button>绑定手机号</button><button>添加包裹</button></div></div>';
docs.parcels=emptyDoc;
document.getElementById('parcels-source').dispatchEvent(new window.Event('load'));advance(300);
const count=parcels.querySelector('strong');
assert.equal(count.style.display,'inline','Empty-state phone count stays in the sentence flow');
assert.equal(count.style.width,'auto');
assert.equal(count.style.getPropertyValue('padding'),'0');
assert.equal(count.querySelector('svg'),null);
const emptyPanel=parcels.querySelector('.hc-parcel-empty');
assert.ok(emptyPanel,'Empty result uses its own centered presentation');
assert.equal(emptyPanel.getAttribute('role'),'status');
assert.equal(emptyPanel.parentElement.style.getPropertyValue('padding'),'0','Do not inherit source top/bottom spacers');
assert.equal(emptyPanel.querySelector('[data-hc-empty-title]').style.getPropertyValue('margin'),'0');
assert.ok(parcels.textContent.includes('您的2个手机号都未查到包裹'));
assert.doesNotMatch(parcels.textContent,/绑定手机号|添加包裹/,'Empty-state actions move to the card heading');
assert.equal(document.querySelector('[data-add-package]').previousElementSibling,null);
assert.equal(document.querySelector('[data-add-package]').nextElementSibling,phoneManage);
console.log('PASS: Taobao section skeleton, measured height, content transition, independent controls, stale-document guard and stable other subtree');
