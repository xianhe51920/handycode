(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)(jd|jingdong|jdpay|jdh|jkcsjd)\.com$/i.test(location.hostname) || window.top !== window) return null;
 try {
  if (!window.chrome) {
   window.chrome = { runtime: {}, app: { isInstalled: false }, csi: function () { return {}; }, loadTimes: function () { return {}; } };
  }
 } catch (ignore) {}
 try {
  if (typeof navigator !== 'undefined' && navigator.webdriver) {
   Object.defineProperty(navigator, 'webdriver', { get: function () { return false; }, configurable: true });
  }
 } catch (ignore) {}
 function purgeStaleRiskStorage(force) {
  try {
   if (!window.localStorage) return;
   if (!force && window.sessionStorage) {
    if (window.sessionStorage.getItem('hc_jd_tk_clean_50')) return;
    window.sessionStorage.setItem('hc_jd_tk_clean_50', '1');
   }
   var keys = [];
   for (var i = 0; i < window.localStorage.length; i++) {
    var k = window.localStorage.key(i);
    if (k && /^(WQ_dy_tk|WQ_vk|_gia|gia_|jd_h5st|ParamsSign)/i.test(k)) keys.push(k);
   }
   for (var j = 0; j < keys.length; j++) window.localStorage.removeItem(keys[j]);
  } catch (ignore) {}
 }
 purgeStaleRiskStorage(false);
 if (!window.HandyJdOrders) {
  var state={captured:false,login:false,error:'',needFallback:false,myUserLoadedAt:0,orders:[],lastWindowRaw:'',revision:0};
  function clean(value,max){return typeof value==='string'?value.slice(0,max):'';}
  function code(text) {
   var s=String(text||'');
   var m=s.match(/(?:取件码|提货码|取货码|凭码|自提码|取件密码|开箱码|钥匙码)[:：\s【\[（(]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
   if(m)return m[1];
   var m2=s.match(/(?:请凭|凭)[:：\s【\[（(]*([A-Za-z0-9-]{4,14})[】\]）)\s]*(?:至|到|前往|取件|领取)/);
   if(m2)return m2[1];
   var shelf=s.match(/(?:^|[^\dA-Za-z-])([A-Za-z0-9]{1,4}-\d{1,3}-\d{2,6})(?![A-Za-z0-9-])/);
   if(shelf&&!/^20\d\d-\d{1,2}-\d{1,2}$/.test(shelf[1]))return shelf[1];
   return '';
  }
  function buildPromptText(promptInfo){
   if(!promptInfo||typeof promptInfo!=='object')return '';
   var raw=clean(promptInfo.content,600);
   var vars=Array.isArray(promptInfo.variableList)?promptInfo.variableList:[];
   if(!raw&&!vars.length)return '';
   var idx=0;
   var merged=raw.replace(/%%%/g,function(){
    var v=idx<vars.length?clean(String(vars[idx++]||''),80):'';
    return ' '+v+' ';
   });
   if(idx<vars.length){
    for(var i=idx;i<vars.length;i++)merged+=' '+clean(String(vars[i]||''),80);
   }
   return merged.trim();
  }
  function consumeOrderArray(orderList){
   if(!Array.isArray(orderList))return;
   var rows=[],seen=Object.create(null);
   orderList.slice(0,100).forEach(function(o){
    if(!o||!o.orderId||seen[o.orderId])return;
    var status=clean(o.orderStatusInfo&&o.orderStatusInfo.orderStatusName,100);
    if(/取消|关闭|退款/.test(status))return;
    var p=o.progressInfo||{},text=clean(p.content,1500),tip=clean(p.tip,500),pText=buildPromptText(o.promptInfo);
    var detailText=text||pText||tip;
    var goods=Array.isArray(o.wareInfoList)?o.wareInfoList[0]:null;
    rows.push({id:String(o.orderId).slice(0,80),name:clean(goods&&goods.wareName,180)||'京东订单',
     status:status,detail:detailText,code:code(text+' '+status+' '+tip+' '+pText)});
    seen[o.orderId]=true;
   });
   state.orders=rows;state.captured=true;state.login=false;state.error='';state.needFallback=false;state.revision++;
  }
  function consumeFloorArray(floorList){
   if(!Array.isArray(floorList))return;
   var rows=[],seen=Object.create(null);
   floorList.slice(0,100).forEach(function(f,idx){
    if(!f||String(f.type)==='1')return;
    var id=String(f.orderId||('jd_floor_'+idx)).slice(0,80);
    if(seen[id])return;
    var status=clean(f.orderStatusText||'',100)||'待收货';
    if(/取消|关闭|退款/.test(status))return;
    var delivery=clean(f.deliveryInfo||'',1500);
    var title=clean(f.wareName||f.goodsCountText||'',180);
    rows.push({id:id,name:title?('京东包裹 '+title):'京东待收包裹',
     status:status,detail:delivery,code:code(delivery+' '+status)});
    seen[id]=true;
   });
   state.orders=rows;state.captured=true;state.login=false;state.error='';state.needFallback=false;state.revision++;
  }
  function consume(root) {
   if(!root||typeof root!=='object')return;
   var b=root.body&&typeof root.body==='object'?root.body:root;
   var errCode=b&&b.errorCode!==undefined?String(b.errorCode):'';
   var retCode=b&&b.retcode!==undefined?String(b.retcode):(root.retcode!==undefined?String(root.retcode):'');
   var errReason=clean(String((b&&(b.errorReason||b.errReason||b.errMsg||b.msg))||root.echo||root.msg||''),160);
   if(String(root.code)==='3'||root.notLogin===true||errCode==='302'||retCode==='1001'||/未登录|请先登录/.test(errReason)){
    state.login=true;state.captured=false;state.error='';state.needFallback=false;state.orders=[];return;
   }
   if(b&&Array.isArray(b.orderList)){
    consumeOrderArray(b.orderList);
    return;
   }
   if(b&&Array.isArray(b.orderFloorInfos)){
    consumeFloorArray(b.orderFloorInfos);
    return;
   }
   if(retCode==='0'){
    if(!state.myUserLoadedAt)state.myUserLoadedAt=Date.now();
    state.login=false;state.error='';state.needFallback=false;
    var d=(b&&b.data)||root.data;
    if(d&&d.orderInfo&&String(d.orderInfo.orderCountStatus)==='0'&&Number(d.orderInfo.receiveCount||0)===0&&!state.captured){
     state.orders=[];state.captured=true;
    }
    return;
   }
   if(errCode&&errCode!=='0'){
    if(errCode==='601'||/火爆|快速通道/.test(errReason)){
     purgeStaleRiskStorage(true);
     state.needFallback=true;
    }
    state.error=errReason||'京东页面访问受限，请点击重新加载';
    return;
   }
  }
  function response(text) {
   if(typeof text!=='string'||text.length>2097152)return;
   if(text.indexOf('"orderList"')<0&&text.indexOf('"orderFloorInfos"')<0&&text.indexOf('"retcode"')<0&&text.indexOf('"errorCode"')<0&&text.indexOf('"notLogin"')<0&&text.indexOf('"code":"3"')<0)return;
   try{consume(JSON.parse(text));}catch(ignore){}
  }
  function syncWindowOrderListData(){
   try{
    var raw=window.orderListData;
    if(typeof raw!=='string'||raw.length<3||raw===state.lastWindowRaw)return;
    var parsed=JSON.parse(raw);
    if(!Array.isArray(parsed))return;
    var flat=[];
    for(var i=0;i<parsed.length;i++){
     var item=parsed[i];
     if(Array.isArray(item)){
      for(var j=0;j<item.length;j++)if(item[j]&&item[j].orderId)flat.push(item[j]);
     }else if(item&&item.orderId){
      flat.push(item);
     }
    }
    if(flat.length>0){
     state.lastWindowRaw=raw;
     consumeOrderArray(flat);
    }
   }catch(ignore){}
  }
  var send=XMLHttpRequest.prototype.send;
  var wrappedSend=function(){
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
  try{Object.defineProperty(wrappedSend,'toString',{configurable:true,value:function(){return send.toString();}});}catch(ignore){}
  XMLHttpRequest.prototype.send=wrappedSend;
  var fetchOriginal=window.fetch;
  if(fetchOriginal){
   var wrappedFetch=function(){
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
   try{Object.defineProperty(wrappedFetch,'toString',{configurable:true,value:function(){return fetchOriginal.toString();}});}catch(ignore){}
   window.fetch=wrappedFetch;
  }
  // Fallback only extracts bounded order/logistics blocks; never reads login form values.
  function domOrders(){
   var nodes=document.querySelectorAll('a[href*="orderId"],a[href*="orderid"],[data-id],[class*="my_logistics_item"]'),rows=[],seen=Object.create(null);
   for(var i=0;i<nodes.length&&rows.length<50;i++){
    var node=nodes[i],id='';
    if(node.getAttribute&&node.getAttribute('data-id')){
     id=node.getAttribute('data-id');
    }else if(node.href){
     var url;
     try{url=new URL(node.href,location.href);}catch(ignore){continue;}
     if(!/(^|\.)jd\.com$/i.test(url.hostname))continue;
     id=url.searchParams.get('orderId')||url.searchParams.get('orderid')||'';
    }
    var block=node.closest?node.closest('li,[data-id],[class*="order_box"],[class*="order-item"],[class*="orderItem"],[class*="order_item"],[class*="my_logistics_item"]'):node;
    if(!block)block=node;
    var text=(block.innerText||'').slice(0,2000),value=code(text);
    if(!value||/取消|关闭|退款/.test(text))continue;
    if(!id)id='jd_dom_'+value;
    if(seen[id])continue;
    seen[id]=true;rows.push({id:id.slice(0,80),name:'京东待取订单',status:'待取件',detail:text.replace(/\s+/g,' ').slice(0,500),code:value});
   }
   return rows;
  }
  window.HandyJdOrders={snapshot:function(){
   syncWindowOrderListData();
   var urlLogin=/(^|\.)plogin\.m\.jd\.com$/i.test(location.hostname)||/\/login(?:\/|\.|\?|$)|\/degrade\/index\.html/i.test(location.pathname);
   var bodyText=clean((document.body&&document.body.innerText)||'',4000);
   var domLogin=!urlLogin&&/欢迎逛京东/.test(bodyText)&&/立即登录/.test(bodyText);
   var domEmptySuccess=/暂时没有相关订单|暂无相关订单/.test(bodyText);
   if(domEmptySuccess&&!state.captured){
    state.captured=true;state.login=false;state.error='';state.needFallback=false;
   }
   if(state.myUserLoadedAt>0&&!state.captured&&(Date.now()-state.myUserLoadedAt>2200)){
    state.captured=true;
   }
   var domHot=!state.captured&&!domEmptySuccess&&/大促异常火爆/.test(bodyText);
   if(domHot)state.needFallback=true;
   var domErr=(!state.captured&&!domEmptySuccess&&/大促异常火爆|网络请求失败/.test(bodyText))?'京东接口繁忙或网络受限，请点页面「重新加载」或右上角「刷新网页」重试。':'';
   var login=urlLogin||state.login||domLogin;
   var needFallback=Boolean(!login&&(state.needFallback||domHot)&&!/(^|\.)my\.m\.jd\.com$/i.test(location.hostname));
   var err=login?'':(state.error||domErr);
   var rows=state.captured?state.orders:domOrders();
   return {href:location.href,login:login,error:err,needFallback:needFallback,ready:!login&&!err&&(state.captured||rows.length>0),orders:login?[]:rows};
  }};
 }
 return window.HandyJdOrders.snapshot();
})()