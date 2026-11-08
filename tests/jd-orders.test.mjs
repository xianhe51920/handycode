import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../app/src/main/assets/jd-orders.js',import.meta.url),'utf8');
const sfSource=readFileSync(new URL('../app/src/main/assets/sf-orders.js',import.meta.url),'utf8');
const dySource=readFileSync(new URL('../app/src/main/assets/dy-orders.js',import.meta.url),'utf8');
function fixture(href='https://trade.m.jd.com/order/orderlist_jdm.shtml?sceneval=2',script=source){
 function Xhr(){this.listeners=[];this.responseURL=href;this.responseType='';this._rawResponseText='';}
 Object.defineProperty(Xhr.prototype,'responseText',{
  configurable:true,
  get(){return this._rawResponseText;},
  set(v){this._rawResponseText=v;}
 });
 Xhr.prototype.open=function(method,url){this.openedMethod=method;this.openedUrl=url;};
 Xhr.prototype.send=function(){return 'original-send';};
 Xhr.prototype.addEventListener=function(type,fn){this.listeners.push(fn);};
 const document={querySelectorAll(){return [];},querySelector(){return null;},body:{innerText:''}};
 Object.defineProperty(document,'cookie',{get(){throw Error('Must not read credentials');}});
 const ctx={URL,location:new URL(href),document,XMLHttpRequest:Xhr};ctx.window=ctx;ctx.top=ctx;
 const result=vm.runInNewContext(script,ctx);return {ctx,result,Xhr};
}
let f=fixture();assert.equal(f.result.ready,false);
assert.equal(typeof f.ctx.chrome,'object','window.chrome populated for JD h5st wc=1');
const xhr=new f.Xhr();xhr.responseURL='https://api.m.jd.com/client.action';assert.equal(xhr.send(),'original-send');
xhr.responseText=JSON.stringify({body:{orderList:[
 {orderId:'one',wareInfoList:[{wareName:'正常订单'}],orderStatusInfo:{orderStatusName:'待取件'},progressInfo:{content:'已放入驿站，取件码：A12-345'}},
 {orderId:'one',progressInfo:{content:'取件码：重复'}},
 {orderId:'two',orderStatusInfo:{orderStatusName:'已取消'},progressInfo:{content:'取件码：123456'}},
 {orderId:'three',progressInfo:{content:'配送中'}},
 {orderId:'four',progressInfo:{tip:'自提码:XYZ987'}},
 {orderId:'five',wareInfoList:[{wareName:'驿站订单'}],orderStatusInfo:{orderStatusName:'待收货'},promptInfo:{content:'已到达驿站，取件码 %%% 请及时取件',variableList:['9-3-4096']}}
]}});
xhr.listeners.forEach(fn=>fn.call(xhr));
let s=f.ctx.HandyJdOrders.snapshot();
assert.equal(s.ready,true);assert.equal(s.orders.length,4);
assert.equal(s.orders[0].code,'A12-345');assert.equal(s.orders[1].code,'');assert.equal(s.orders[2].code,'XYZ987');assert.equal(s.orders[3].code,'9-3-4096');

// JD window.orderListData Taro bridge + 302 login expiry + 601 risk error + MY_HOME GetOrderFloorInfo fallback
const jdTaro=fixture();
jdTaro.ctx.orderListData=JSON.stringify([[{orderId:'taro1',wareInfoList:[{wareName:'Taro商品'}],orderStatusInfo:{orderStatusName:'待取件'},progressInfo:{content:'取件码：778899'}}]]);
let taroSnap=jdTaro.ctx.HandyJdOrders.snapshot();
assert.equal(taroSnap.ready,true);assert.equal(taroSnap.orders[0].code,'778899');
const jd302=new jdTaro.Xhr();jd302.responseURL='https://api.m.jd.com/client.action?functionId=order_list_m';jd302.send();
jd302.responseText=JSON.stringify({code:'0',body:{errorCode:'302',loginUrl:'https://plogin.m.jd.com/login/login'}});
jd302.listeners.forEach(fn=>fn.call(jd302));
assert.equal(jdTaro.ctx.HandyJdOrders.snapshot().login,true);

const jd601=fixture();
const xhr601=new jd601.Xhr();xhr601.responseURL='https://api.m.jd.com/client.action?functionId=order_list_m';xhr601.send();
xhr601.responseText=JSON.stringify({code:'0',body:{errorCode:'601',errorReason:'大促异常火爆，已优先为您接入快速通道'}});
xhr601.listeners.forEach(fn=>fn.call(xhr601));
const snap601=jd601.ctx.HandyJdOrders.snapshot();
assert.equal(snap601.ready,false);assert.equal(snap601.login,false);assert.equal(snap601.needFallback,true);assert.ok(snap601.error.includes('大促异常火爆'));

const jdMyHome=fixture('https://my.m.jd.com/index.html?sceneval=2&sourceType=home_m');
const xhrFloor=new jdMyHome.Xhr();xhrFloor.responseURL='https://api.m.jd.com/api?functionId=GetOrderFloorInfo';xhrFloor.send();
xhrFloor.responseText=JSON.stringify({body:{errorCode:0,orderFloorInfos:[
 {orderId:'9901',type:1,orderStatusText:'等待付款',deliveryInfo:'取件码：999999'},
 {orderId:'9902',type:2,orderStatusText:'待自提',goodsCountText:'共2件',deliveryInfo:'快件已存放在京东快递驿站，请凭取件码 5-8-2046 领取'}
]}});
xhrFloor.listeners.forEach(fn=>fn.call(xhrFloor));
const snapFloor=jdMyHome.ctx.HandyJdOrders.snapshot();
assert.equal(snapFloor.ready,true);assert.equal(snapFloor.needFallback,false);assert.equal(snapFloor.orders.length,1);
assert.equal(snapFloor.orders[0].code,'5-8-2046');

const foreign=new f.Xhr();foreign.responseURL='https://jd.com.evil.invalid/api';foreign.send();
foreign.responseText='{"body":{"orderList":[]}}';foreign.listeners.forEach(fn=>fn.call(foreign));
assert.equal(f.ctx.HandyJdOrders.snapshot().orders.length,4,'Ignore non-JD responses');
f.ctx.location=new URL('https://plogin.m.jd.com/login/login');
s=f.ctx.HandyJdOrders.snapshot();assert.equal(s.login,true);assert.equal(s.orders.length,0);
assert.equal(fixture('https://evil.invalid/').result,null);
assert.equal(fixture('http://trade.m.jd.com/').result,null);

// SF Express waybill list + route pickup code extraction + stale cookie rejection + login modal & QR/SMS transitions
const sf=fixture('https://www.sf-express.com/chn/sc/waybill/list',sfSource);
assert.equal(sf.result.ready,false);
// QR polling status "1" (unscanned) must NEVER prematurely mark ready=true!
const sfQrPending=new sf.Xhr();sfQrPending.responseURL='https://www.sf-express.com/sf-service-owf-web/service/qrcode/loginCheck';sfQrPending.send();
sfQrPending.responseText=JSON.stringify({success:true,code:'0',result:{status:'1'}});
sfQrPending.listeners.forEach(fn=>fn.call(sfQrPending));
assert.equal(sf.ctx.HandySfOrders.snapshot().ready,false);
// Waybill list + route pickup code extraction
const sfXhr=new sf.Xhr();sfXhr.responseURL='https://www.sf-express.com/sf-service-core-web/service/waybillList/getPaginatedWaybillList';sfXhr.send();
sfXhr.responseText=JSON.stringify({success:true,result:{data:[{waybillno:'SF1234567890123',waybillStatusText:'派送中',originateCityName:'深圳',destinationCityName:'北京'}]}});
sfXhr.listeners.forEach(fn=>fn.call(sfXhr));
const sfRoute=new sf.Xhr();sfRoute.responseURL='https://www.sf-express.com/sf-service-core-web/service/waybillRoute/SF1234567890123/routes';sfRoute.send();
sfRoute.responseText=JSON.stringify({success:true,result:{routes:[{remark:'快件已存放在丰巢智能柜，请凭取件码【881234】取件'}]}});
sfRoute.listeners.forEach(fn=>fn.call(sfRoute));
const sfSnap=sf.ctx.HandySfOrders.snapshot();
assert.equal(sfSnap.ready,true);assert.equal(sfSnap.orders.length,1);assert.equal(sfSnap.orders[0].code,'881234');
// SF code 121 ("用户未登录") marks login:true and ready:false without breaking session cookies
sf.ctx.$nuxt={$loginShowCalled:0,$loginShow(){this.$loginShowCalled++;}};
const sf121=new sf.Xhr();sf121.responseURL='https://www.sf-express.com/sf-service-core-web/service/waybillList/getPaginatedWaybillList';sf121.send();
sf121.responseText=JSON.stringify({success:false,code:121,message:'用户未登录'});
sf121.listeners.forEach(fn=>fn.call(sf121));
const sfLoggedOut=sf.ctx.HandySfOrders.snapshot();
assert.equal(sfLoggedOut.login,true);assert.equal(sfLoggedOut.ready,false);assert.equal(sfLoggedOut.orders.length,0);
assert.equal(sf.ctx.HandySfOrders.openLogin(),true);
assert.equal(sf.ctx.$nuxt.$loginShowCalled,1);
// Fresh login via /smsLogin (code 0) or QR confirmed (status 3) marks login:false, ready:true
const sfSms=new sf.Xhr();sfSms.responseURL='https://www.sf-express.com/sf-service-core-web/service/user/smsLogin';sfSms.send();
sfSms.responseText=JSON.stringify({success:true,code:'0'});
sfSms.listeners.forEach(fn=>fn.call(sfSms));
const sfJustLoggedIn=sf.ctx.HandySfOrders.snapshot();
assert.equal(sfJustLoggedIn.login,false);assert.equal(sfJustLoggedIn.ready,true);

// SF anonymous OWFSESSION cookie must NOT block login:true when DOM has login button
function sfDomFixture(domButtons = [], initialCookie = 'OWFSESSION=guest_session_123; HWWAFSESID=sec_999', fetchFn = null) {
 let cookies = initialCookie;
 const storage = Object.create(null);
 const localStorage = {
  getItem(k) { return storage[k] || null; },
  setItem(k, v) { storage[k] = String(v); },
  removeItem(k) { delete storage[k]; }
 };
 function Xhr(){this.listeners=[];this.responseURL='https://www.sf-express.com/chn/sc/waybill/list';this.responseType='';this._rawResponseText='';}
 Xhr.prototype.open=function(m,u){this.openedMethod=m;this.openedUrl=u;};
 Xhr.prototype.send=function(){return 'ok';};
 Xhr.prototype.addEventListener=function(t,fn){this.listeners.push(fn);};
 const document = {
  querySelectorAll(sel) {
   if (sel.includes('.empty-box-btn') || sel.includes('button')) return domButtons;
   return [];
  },
  querySelector(sel) {
   if ((sel.includes('.empty-box-btn') || sel.includes('button') || sel.includes('.login')) && domButtons.length > 0) {
    return domButtons[0];
   }
   return null;
  },
  body: { innerText: '' },
  head: { appendChild() {} },
  getElementById() { return null; }
 };
 Object.defineProperty(document, 'cookie', {
  get() { return cookies; },
  set(v) { cookies = cookies ? (cookies + '; ' + v) : v; }
 });
 const ctx = {
  URL,
  location: new URL('https://www.sf-express.com/chn/sc/waybill/list'),
  document,
  localStorage,
  XMLHttpRequest: Xhr,
  sessionStorage: { getItem(){return null;}, setItem(){}, removeItem(){} }
 };
 if (fetchFn) ctx.fetch = fetchFn;
 ctx.window = ctx; ctx.top = ctx;
 const result = vm.runInNewContext(sfSource, ctx);
 return { ctx, result, Xhr, localStorage };
}

// 1. Anonymous visitor with OWFSESSION and empty-box "\u767b\u5f55/\u6ce8\u518c" button MUST report login:true
const sfGuest = sfDomFixture([{ innerText: '\u767b\u5f55/\u6ce8\u518c', tagName: 'BUTTON', classList: { contains: (c) => c === 'empty-box-btn' } }]);
const guestSnap = sfGuest.ctx.HandySfOrders.snapshot();
assert.equal(guestSnap.login, true, 'OWFSESSION must not cause false-positive logged-in state');
assert.equal(guestSnap.ready, false);

// 2. LocalStorage loginUser restoration across app restarts
const sfPersisted = sfDomFixture([], '');
sfPersisted.ctx.localStorage.setItem('hc_sf_login_user', '13912345678');
// Re-running or initializing sfSource restores document.cookie
vm.runInNewContext(sfSource, sfPersisted.ctx);
assert.ok(sfPersisted.ctx.document.cookie.includes('loginUser=13912345678'), 'Saved loginUser restored to cookie');

// 3. When code 121 arrives, localStorage key is deleted
const sfRejectXhr = new sfPersisted.Xhr();
sfRejectXhr.responseURL = 'https://www.sf-express.com/sf-service-core-web/service/waybillList/getPaginatedWaybillList';
sfRejectXhr.send();
sfRejectXhr.responseText = JSON.stringify({ success: false, code: 121, message: '\u7528\u6237\u672a\u767b\u5f55' });
sfRejectXhr.listeners.forEach(fn => fn.call(sfRejectXhr));
assert.equal(sfPersisted.ctx.localStorage.getItem('hc_sf_login_user'), null, 'code 121 removes persisted loginUser');
assert.equal(sfPersisted.ctx.HandySfOrders.snapshot().login, true);

// 4. When remember-me cookie is present, code 121 attempts autoLogin and preserves session
let autoLoginCalled = false;
const mockFetch = function(url, opts) {
 if (url.includes('autoLogin')) {
  autoLoginCalled = true;
  assert.equal(opts.method, 'POST');
  assert.ok(opts.headers.appId === 'huJk1ARWMV5C');
  assert.ok(opts.headers.sign && opts.headers.sign.length === 32);
  return Promise.resolve({
   json: () => Promise.resolve({ success: true, code: 0, result: { id: 'u1' } })
  });
 }
 return Promise.reject(new Error('Unknown url: ' + url));
};
const sfAuto = sfDomFixture([], 'remember-me=persistent_token_123; loginUser=13912345678', mockFetch);
assert.ok(autoLoginCalled, 'autoLogin was proactively attempted on load with remember-me');

// Douyin official web login (www.douyin.com/user/self) + QR/SMS/Profile detection
const dyLogin=fixture('https://www.douyin.com/user/self',dySource);
assert.equal(dyLogin.result.login,true);assert.equal(dyLogin.result.ready,false);
// Unconfirmed QR poll must stay login:true
const dyQrWait=new dyLogin.Xhr();dyQrWait.responseURL='https://sso.douyin.com/passport/web/check_qrconnect/?aid=6383';dyQrWait.send();
dyQrWait.responseText=JSON.stringify({message:'success',data:{status:'new'}});
dyQrWait.listeners.forEach(fn=>fn.call(dyQrWait));
assert.equal(dyLogin.ctx.HandyDyOrders.snapshot().login,true);
// SMS login success emits ssoDone:true (without prematurely claiming order ready:true)
const dySmsOk=new dyLogin.Xhr();dySmsOk.responseURL='https://sso.douyin.com/passport/web/sms_login/?aid=6383';dySmsOk.send();
dySmsOk.responseText=JSON.stringify({message:'success',error_code:0,data:{user_id_str:'10001'}});
dySmsOk.listeners.forEach(fn=>fn.call(dySmsOk));
const dyAfterSms=dyLogin.ctx.HandyDyOrders.snapshot();
assert.equal(dyAfterSms.login,false);assert.equal(dyAfterSms.ssoDone,true);assert.equal(dyAfterSms.ready,false);
// Logged-in profile DOM fallback (抖音号：xxx) also emits ssoDone:true
const dyProfile=fixture('https://www.douyin.com/user/self',dySource);
dyProfile.ctx.document.body.innerText='昵称\n抖音号：handy_user_01\n获赞 12';
assert.equal(dyProfile.ctx.HandyDyOrders.snapshot().ssoDone,true);

// Douyin order center: error response must NOT fake ready:true; valid st:0 + logistics extracts pickup codes; 10001010A triggers needSso
const dy=fixture('https://haohuo.jinritemai.com/views/neworder/center',dySource);
assert.equal(dy.result.ready,false);
dy.ctx.HandyDyOrders.ingest(JSON.stringify({st:10005,code:'10005',msg:'抖音订单同步失败，请点击右上角「刷新网页」重试',data:null}),'https://ec.snssdk.com/order/page?tab=all&page=0');
const dyErrSnap=dy.ctx.HandyDyOrders.snapshot();
assert.equal(dyErrSnap.ready,false);assert.equal(dyErrSnap.login,false);assert.ok(dyErrSnap.error.includes('抖音订单同步失败'));
const dyXhr=new dy.Xhr();dyXhr.responseURL='https://ec.snssdk.com/order/page?tab=all&page=0';dyXhr.send();
dyXhr.responseText='\t'+JSON.stringify({st:0,data:[{order_id:'DY9001',sub_orders:[{product_name:'抖音测试商品'}],logistics:{data:[{title:'待收货',context:'包裹已到达驿站'}]}}]} );
dyXhr.listeners.forEach(fn=>fn.call(dyXhr));
const dyExp=new dy.Xhr();dyExp.responseURL='https://ec.snssdk.com/logistics/ajaxindex?enter_from=order_list&id=DY9001';dyExp.send();
dyExp.responseText=JSON.stringify({status_code:0,pick_info:{pick_code:'6-2-2048',pick_site:'菜鸟驿站',pick_address:'朝阳路1号'}});
dyExp.listeners.forEach(fn=>fn.call(dyExp));
dy.ctx.HandyDyOrders.ingest(JSON.stringify({st:0,data:[{order_id:'DY9002',final_status:3,product_name:'直通注入商品'}]}),'https://ec.snssdk.com/order/page?tab=delivery&page=0');
dy.ctx.HandyDyOrders.ingest(JSON.stringify({status_code:0,pick_info:{pick_code:'8-1-1024',pick_site:'兔喜生活',pick_address:'海淀路2号'}}),'https://ec.snssdk.com/logistics/ajaxindex?enter_from=order_list&id=DY9002');
const dySnap=dy.ctx.HandyDyOrders.snapshot();
assert.equal(dySnap.orders.length,2);
assert.equal(dySnap.orders[1].code,'8-1-1024');
assert.equal(dySnap.ready,true);assert.equal(dySnap.orders[0].code,'6-2-2048');
dy.ctx.HandyDyOrders.ingest('\t{"code":"10001010A","data":null,"msg":"用户未登录，请登录后重试"}','https://ec.snssdk.com/order/page?tab=0&page=0');
const dyExpired=dy.ctx.HandyDyOrders.snapshot();
assert.equal(dyExpired.login,true);assert.equal(dyExpired.needSso,true);assert.equal(dyExpired.ready,false);assert.equal(dyExpired.orders.length,0);
console.log('JD, SF and Douyin order capture, extraction, cancellation, deduplication, login and origin checks passed');
