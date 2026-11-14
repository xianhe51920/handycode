// Experimental SF observer: normal official WebView session, no credential copies.
;(function () {
 'use strict';
 if (window.top !== window || location.protocol !== 'https:' || !/(^|\.)sf-express\.com$/i.test(location.hostname)) return null;
 if (!window.HandySfOrders) {
  var state={captured:false,login:false,byId:Object.create(null)};
  function code(text) {
   var m=String(text||'').match(/(?:取件码|提货码|取货码|自提码|开箱码)[:：\s【\[（(]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
   return m && !/^1[3-9]\d{9}$/.test(m[1]) ? m[1] : '';
  }
  function put(id,name,status,detail,pickup) {
   if (!id) return;
   id=String(id).slice(0,80);var old=state.byId[id]||{};
   state.byId[id]={id:id,name:String(name||old.name||'顺丰待取快件').slice(0,100),status:String(status||old.status||'待取件').slice(0,60),detail:String(detail||old.detail||'').slice(0,400),code:pickup||old.code||''};
   if (/已签收|已派送成功|取消|退回|退款成功/.test(state.byId[id].status)) delete state.byId[id];
  }
  function consume(root,urlStr) {
   try {
    var url=new URL(urlStr,location.href);
    if (url.protocol!=='https:' || url.origin!==location.origin || !root || typeof root!=='object') return;
    if (/\/user\/smsLogin/.test(url.pathname) && root.success===true && String(root.code)==='0') {state.login=false;state.captured=false;return;}
    if (!/\/sf-service-core-web\/service\/(?:waybill|waybillRoute|waybillList)/i.test(url.pathname)) return;
    if (root.success===false || Number(root.code)===121 || Number(root.errorCode)===401) {
     if (/未登录|登录失效|会话过期|重新登录|未授权|Unauthorized/i.test(String(root.message||root.errorMessage||root.msg||'')) || Number(root.errorCode)===401 || Number(root.code)===121) {state.login=true;state.captured=false;state.byId=Object.create(null);}
     return;
    }
    if (root.success!==true && ![0,'0',200,'200'].includes(root.code)) return;
    var data=root.obj||root.data||root.result;
    var rows=Array.isArray(data)?data:data&&(data.data||data.list||data.records);
    if (Array.isArray(rows) && /waybillList|\/waybill(?:\/|$)/i.test(url.pathname)) {
     state.captured=true;state.login=false;
     var previous=state.byId;state.byId=Object.create(null);
     rows.slice(0,40).forEach(function(w){
      if (!w || typeof w!=='object') return;
      var id=w.waybillno||w.waybillNo||w.id;if (!id) return;
      var status=w.waybillStatusText||w.statusText||w.waybillStatus||'';
      var detail=w.latestRoute||w.remark||w.pickupAddr||'';
      state.byId[String(id)]=previous[String(id)]||{};
      put(id,'顺丰快件 '+String(id).slice(-4),status,detail,code((w.pickupCode?'取件码 '+w.pickupCode:'')+' '+detail+' '+status));
     });
    }
    if (data && Array.isArray(data.routes)) {
     var match=url.pathname.match(/waybillRoute\/([^/]+)\/routes/i),id=match?decodeURIComponent(match[1]):data.waybillNo;
     data.routes.slice(0,40).forEach(function(route){var text=String(route.remark||route.routeRemark||route.opDesc||route.content||''),pickup=code(text);if(pickup)put(id,null,'待取件',text,pickup);});
    }
    if (data && (data.pickupCode || data.pickupAddr)) put(data.waybillNo||data.waybillno,null,data.waybillStatusText,String(data.pickupAddr||data.remark||''),code((data.pickupCode?'取件码 '+data.pickupCode:'')+' '+(data.pickupAddr||data.remark||'')));
   } catch (_) {}
  }
  function response(text,url) {if(typeof text!=='string'||text.length>2097152)return;try{consume(JSON.parse(text),url);}catch(_){}}
  var send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send=function(){this.addEventListener('load',function(){try{if(this.responseType==='json')consume(this.response,this.responseURL);else if(!this.responseType||this.responseType==='text')response(this.responseText,this.responseURL);}catch(_){}});return send.apply(this,arguments);};
  var fetchOriginal=window.fetch;
  if(fetchOriginal)window.fetch=function(){var p=fetchOriginal.apply(this,arguments);p.then(function(r){try{r.clone().text().then(function(t){response(t,r.url);}).catch(function(){});}catch(_){}}).catch(function(){});return p;};
  function visible(n){return n&&n.getClientRects().length&&getComputedStyle(n).visibility!=='hidden';}
  function openLogin(){
   if(window.$nuxt&&typeof window.$nuxt.$loginShow==='function'){window.$nuxt.$loginShow({});return true;}
   var buttons=document.querySelectorAll('.empty-box-btn,.common-header .login,.header-wrap .login');
   for(var i=0;i<buttons.length;i++)if(visible(buttons[i])&&/登录/.test(buttons[i].textContent||'')){buttons[i].click();return true;}return false;
  }
  function snapshot(){
   if(Array.from(document.querySelectorAll('.pop-wrap.login-wrap,[class*="login-modal"]')).some(visible)||/\/login/i.test(location.pathname))return{href:location.href,login:true,ready:false,orders:[]};
   var rows=Object.keys(state.byId).map(function(id){return state.byId[id];});
   if(!state.captured&&!rows.length&&Array.from(document.querySelectorAll('.empty-box-btn,.common-header .login,.header-wrap .login')).some(function(n){return visible(n)&&/登录/.test(n.textContent||'');}))state.login=true;
   return{href:location.href,login:state.login,ready:!state.login&&state.captured,orders:state.login?[]:rows};
  }
  window.HandySfOrders={openLogin:openLogin,snapshot:snapshot};
 }
 return window.HandySfOrders.snapshot();
})();
