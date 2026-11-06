(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)jd\.com$/i.test(location.hostname) || window.top !== window) return null;
 if (!window.HandyJdOrders) {
  var state={captured:false,orders:[],revision:0};
  function clean(value,max){return typeof value==='string'?value.slice(0,max):'';}
  function code(text) {
   var m=String(text).match(/(?:取件码|提货码|取货码|凭码|自提码)[:：\s]*([A-Za-z0-9-]{3,12})(?![A-Za-z0-9-])/);
   return m?m[1]:'';
  }
  function consume(root) {
   if(!root||!root.body||!Array.isArray(root.body.orderList))return;
   var rows=[],seen=Object.create(null);
   root.body.orderList.slice(0,100).forEach(function(o){
    if(!o||!o.orderId||seen[o.orderId])return;
    var status=clean(o.orderStatusInfo&&o.orderStatusInfo.orderStatusName,100);
    if(/取消|关闭|退款/.test(status))return;
    var p=o.progressInfo||{},text=clean(p.content,1500),tip=clean(p.tip,500);
    var goods=Array.isArray(o.wareInfoList)?o.wareInfoList[0]:null;
    rows.push({id:String(o.orderId).slice(0,80),name:clean(goods&&goods.wareName,180)||'京东订单',
     status:status,detail:text,code:code(text+' '+status+' '+tip)});
    seen[o.orderId]=true;
   });
   state.orders=rows;state.captured=true;state.revision++;
  }
  function response(text) {
   if(typeof text!=='string'||text.length>2097152||text.indexOf('"orderList"')<0)return;
   try{consume(JSON.parse(text));}catch(ignore){}
  }
  var send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send=function(){
   this.addEventListener('load',function(){
    try{
     var url=new URL(this.responseURL,location.href);
     if(url.protocol!=='https:'||!/(^|\.)jd\.com$/i.test(url.hostname))return;
     if(this.responseType==='json')consume(this.response);
     else if(!this.responseType||this.responseType==='text')response(this.responseText);
    }catch(ignore){}
   });
   return send.apply(this,arguments);
  };
  var fetchOriginal=window.fetch;
  if(fetchOriginal)window.fetch=function(){
   var promise=fetchOriginal.apply(this,arguments);
   promise.then(function(res){
    try{
     var url=new URL(res.url,location.href);
     if(url.protocol==='https:'&&/(^|\.)jd\.com$/i.test(url.hostname))
      res.clone().text().then(response).catch(function(){});
    }catch(ignore){}
   }).catch(function(){});
   return promise;
  };
  // Fallback only extracts bounded order blocks; never reads login form values.
  function domOrders(){
   var nodes=document.querySelectorAll('a[href*="orderId"],a[href*="orderid"]'),rows=[],seen=Object.create(null);
   for(var i=0;i<nodes.length&&rows.length<50;i++){
    var link=nodes[i],url;
    try{url=new URL(link.href,location.href);}catch(ignore){continue;}
    if(!/(^|\.)jd\.com$/i.test(url.hostname))continue;
    var id=url.searchParams.get('orderId')||url.searchParams.get('orderid');
    if(!id||seen[id])continue;
    var block=link.closest('li,[class*="order-item"],[class*="orderItem"],[class*="order_item"]');
    if(!block)continue;
    var text=(block.innerText||'').slice(0,2000),value=code(text);
    if(!value||/取消|关闭|退款/.test(text))continue;
    seen[id]=true;rows.push({id:id.slice(0,80),name:'京东待取订单',status:'待取件',detail:text.slice(0,500),code:value});
   }
   return rows;
  }
  window.HandyJdOrders={snapshot:function(){
   var login=/(^|\.)plogin\.m\.jd\.com$/i.test(location.hostname)||/\/login(?:\/|$)/i.test(location.pathname);
   var rows=state.captured?state.orders:domOrders();
   return {href:location.href,login:login,ready:!login&&(state.captured||rows.length>0),orders:login?[]:rows};
  }};
 }
 return window.HandyJdOrders.snapshot();
})()
