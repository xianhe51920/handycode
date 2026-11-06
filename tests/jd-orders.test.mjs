import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../app/src/main/assets/jd-orders.js',import.meta.url),'utf8');
function fixture(href='https://trade.m.jd.com/order/orderlist_jdm.shtml'){
 function Xhr(){this.listeners=[];this.responseURL='https://api.m.jd.com/client.action';this.responseType='';}
 Xhr.prototype.send=function(){return 'original-send';};
 Xhr.prototype.addEventListener=function(type,fn){this.listeners.push(fn);};
 const document={querySelectorAll(selector){assert.ok(selector.startsWith('a['));return [];}};
 Object.defineProperty(document,'cookie',{get(){throw Error('Must not read credentials');}});
 const ctx={URL,location:new URL(href),document,XMLHttpRequest:Xhr};ctx.window=ctx;ctx.top=ctx;
 const result=vm.runInNewContext(source,ctx);return {ctx,result,Xhr};
}
let f=fixture();assert.equal(f.result.ready,false);
const xhr=new f.Xhr();assert.equal(xhr.send(),'original-send');
xhr.responseText=JSON.stringify({body:{orderList:[
 {orderId:'one',wareInfoList:[{wareName:'正常订单'}],orderStatusInfo:{orderStatusName:'待取件'},progressInfo:{content:'已放入驿站，取件码：A12-345'}},
 {orderId:'one',progressInfo:{content:'取件码：重复'}},
 {orderId:'two',orderStatusInfo:{orderStatusName:'已取消'},progressInfo:{content:'取件码：123456'}},
 {orderId:'three',progressInfo:{content:'配送中'}},
 {orderId:'four',progressInfo:{tip:'自提码:XYZ987'}}
]}});
xhr.listeners.forEach(fn=>fn.call(xhr));
let s=f.ctx.HandyJdOrders.snapshot();
assert.equal(s.ready,true);assert.equal(s.orders.length,3);
assert.equal(s.orders[0].code,'A12-345');assert.equal(s.orders[1].code,'');assert.equal(s.orders[2].code,'XYZ987');
const foreign=new f.Xhr();foreign.responseURL='https://jd.com.evil.invalid/api';foreign.send();
foreign.responseText='{"body":{"orderList":[]}}';foreign.listeners.forEach(fn=>fn.call(foreign));
assert.equal(f.ctx.HandyJdOrders.snapshot().orders.length,3,'Ignore non-JD responses');
f.ctx.location=new URL('https://plogin.m.jd.com/login/login');
s=f.ctx.HandyJdOrders.snapshot();assert.equal(s.login,true);assert.equal(s.orders.length,0);
assert.equal(fixture('https://evil.invalid/').result,null);
assert.equal(fixture('http://trade.m.jd.com/').result,null);
console.log('JD order capture, extraction, cancellation, deduplication, login and origin checks passed');
