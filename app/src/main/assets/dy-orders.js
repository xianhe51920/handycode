(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)(jinritemai|snssdk|douyin|toutiao)\.com$/i.test(location.hostname) || window.top !== window) return null;
 function isDouyinWebHost(){
  return /(^|\.)douyin\.com$/i.test(location.hostname);
 }
 function adaptDyWebLogin(){
  if(!isDouyinWebHost())return;
  try{
   if(document.head){
    var vp=document.querySelector('meta[name="viewport"]');
    if(!vp){vp=document.createElement('meta');vp.name='viewport';document.head.appendChild(vp);}
    var targetVp='width=device-width,initial-scale=1,maximum-scale=2,user-scalable=yes';
    if(vp.getAttribute('content')!==targetVp)vp.setAttribute('content',targetVp);
    if(!document.getElementById('hc-dy-login-style')){
     var st=document.createElement('style');
     st.id='hc-dy-login-style';
     st.textContent=
      'html,body{min-width:0!important;width:100vw!important;max-width:100vw!important;overflow-x:hidden!important;background:#161823!important}'+
      'video{opacity:0!important;pointer-events:none!important}'+
      'aside,nav,[class*="side-nav"],[class*="SideNav"],[class*="navigation"],[class*="left-nav"],[class*="leftBar"],[class*="left-bar"],[class*="guide"],[data-e2e*="nav"],#douyin-navigation,[id*="douyin-navigation"],[class*="douyin-navigation"],#douyin_header,header,[class*="header-ui"],[class*="douyin-header"],[class*="nav-wrapper"],[class*="sidebar"],[id*="sidebar"]{display:none!important;width:0!important;max-width:0!important;height:0!important;overflow:hidden!important;visibility:hidden!important;pointer-events:none!important}'+'[class*="layout"],[class*="container"],[class*="main"]{margin-left:0!important;padding-left:0!important;width:100vw!important;max-width:100vw!important}'+
      '#root,#dark,[class*="LebMor1Z"],main,[role="main"]{margin:0!important;padding:0!important;width:100vw!important;max-width:100vw!important;min-width:0!important;overflow-x:hidden!important}'+
      '#login-panel-new,#douyin-login-new-id{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;display:flex!important;align-items:center!important;justify-content:center!important;z-index:5000!important;background:rgba(22,24,35,0.98)!important;margin:0!important;padding:0!important;overflow-y:auto!important}'+
      '#douyin-login-new-id > div{width:100%!important;display:flex!important;justify-content:center!important;align-items:center!important;margin:0 auto!important}'+
      '#douyin_login_comp_flat_panel{width:92vw!important;max-width:360px!important;min-width:0!important;height:auto!important;max-height:88vh!important;overflow-y:auto!important;overflow-x:hidden!important;border-radius:16px!important;box-sizing:border-box!important;position:relative!important;left:auto!important;top:auto!important;margin:0 auto!important;transform:none!important;background:#242630!important;box-shadow:0 8px 32px rgba(0,0,0,0.6)!important}'+
      '#douyin_login_comp_flat_panel_picture{display:none!important}'+
      '#douyin_login_comp_flat_panel > div,#douyin_login_comp_flat_panel_header{width:100%!important;min-width:0!important;height:auto!important;box-sizing:border-box!important}'+
      '#douyin_login_comp_flat_panel_title{width:100%!important;font-size:16px!important;text-align:center!important;color:#ffffff!important;padding:16px 12px 0!important;white-space:normal!important}'+
      '#douyin_login_landing_flat_container{display:flex!important;flex-direction:column-reverse!important;align-items:center!important;width:100%!important;min-width:0!important;height:auto!important;padding:10px 16px 20px!important;box-sizing:border-box!important;gap:16px!important}'+
      '#douyin_login_landing_flat_container > div{width:100%!important;max-width:320px!important;min-width:0!important;margin:0 auto!important}'+
      '#douyin_login_comp_h5_panel{max-width:92vw!important;margin:0 auto!important}'+
      '[id*="captcha"],[class*="secsdk_captcha"],[class*="captcha_verify"]{z-index:2147483647!important}';
     document.head.appendChild(st);
    }
   }
   try {
    var sidebars = document.querySelectorAll('aside, nav, [class*="nav"], [class*="side"], [class*="guide"]');
    for (var s = 0; s < sidebars.length; s++) {
     var sb = sidebars[s];
     var sbt = (sb.innerText || '').trim();
     if (/精选.*推荐|推荐.*精选|AI抖音/.test(sbt)) {
      sb.style.setProperty('display', 'none', 'important');
      sb.style.setProperty('width', '0px', 'important');
      sb.style.setProperty('visibility', 'hidden', 'important');
     }
    }
   } catch(e) {}
   var videos=document.querySelectorAll('video');
   for(var i=0;i<videos.length;i++){
    try{videos[i].muted=true;if(!videos[i].paused)videos[i].pause();}catch(e){}
   }
   var curPanel=document.getElementById('login-panel-new');
   while(curPanel&&curPanel!==document.body){
    if(curPanel.style){
     curPanel.style.setProperty('margin-left','0px','important');
     curPanel.style.setProperty('padding-left','0px','important');
     curPanel.style.setProperty('transform','none','important');
    }
    curPanel=curPanel.parentElement;
   }
   var h5Panel=document.getElementById('douyin_login_comp_h5_panel');
   if(h5Panel){
    var vw=Math.min(window.innerWidth||390,document.documentElement.clientWidth||390);
    var naturalW=566;
    if(vw>0&&vw<naturalW+24){
     var scale=Math.max(0.55,Math.min(1,(vw*0.93)/naturalW));
     h5Panel.style.setProperty('transform','scale('+scale.toFixed(3)+')','important');
     h5Panel.style.setProperty('transform-origin','center center','important');
     h5Panel.style.setProperty('margin','0 auto','important');
    }
   }else{
    var loginBtns=document.querySelectorAll('button,p,span,div');
    for(var j=0;j<loginBtns.length;j++){
     var b=loginBtns[j];
     if(b.children&&b.children.length===0&&(b.innerText||'').trim()==='登录'){
      if(!b.getAttribute('data-hc-clicked')){
       b.setAttribute('data-hc-clicked','1');
       try{b.click();}catch(e){}
      }
      break;
     }
    }
   }
  }catch(ignore){}
 }
 adaptDyWebLogin();

 if (!window.HandyDyOrders) {
  var state={captured:false,authExpired:false,login:false,needSso:false,ssoSuccess:false,error:'',orders:[],byId:Object.create(null),probed:Object.create(null),revision:0};
  function clean(value,max){return typeof value==='string'?value.slice(0,max):'';}
  function code(text) {
   var s=String(text||'');
   var m=s.match(/(?:取件码|提货码|取货码|凭码|自提码|取件密码|开箱码|钥匙码)[:：\s【\[（(]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
   if(m)return m[1];
   var shelf=s.match(/(?:^|[^\dA-Za-z-])([A-Za-z0-9]{1,4}-\d{1,3}-\d{2,6})(?![A-Za-z0-9-])/);
   if(shelf&&!/^20\d\d-\d{1,2}-\d{1,2}$/.test(shelf[1]))return shelf[1];
   return '';
  }
  function hasLocalDyCookie(){
   try{
    return /(?:^|;\s*)(?:sessionid|sessionid_ss|sid_tt|sid_ucp_v1|ssid_ucp_v1|sid_guard)=([^;]+)/.test(document.cookie||'');
   }catch(ignore){
    return false;
   }
  }
  function markSsoLoginSuccess(){
   if(state.ssoSuccess)return;
   state.ssoSuccess=true;
   state.authExpired=false;
   state.error='';
   state.login=false;
   state.needSso=false;
   try{console.info('__HANDY_DY_LOGIN_SUCCESS__');}catch(ignore){}
  }
  function upsert(id,name,status,detail,c){
   if(!id)return;
   id=String(id).slice(0,80);
   var item=state.byId[id];
   if(!item){
    item={id:id,name:name||'抖音订单',status:status||'运输中',detail:detail||'',code:c||''};
    state.byId[id]=item;state.orders.push(item);
   }else{
    if(name)item.name=name;
    if(status)item.status=status;
    if(detail)item.detail=detail;
    if(c)item.code=c;
   }
   state.revision++;
  }
  function probeExpress(id){
   if(!id||state.probed[id]||typeof window.fetch!=='function')return;
   state.probed[id]=true;
   window.fetch('https://ec.snssdk.com/logistics/ajaxindex?enter_from=order_list&id='+encodeURIComponent(id),{credentials:'include'}).catch(function(){});
  }
  function isLoginModalVisibleOnDouyin(){
   if(!isDouyinWebHost())return false;
   var p=document.querySelector?document.querySelector('#douyin_login_comp_h5_panel,#douyin_login_comp_flat_panel,#login-panel-new'):null;
   if(!p||!p.getClientRects||p.getClientRects().length===0)return false;
   var r=p.getBoundingClientRect();
   return r.width>60&&r.height>60;
  }
  function isLoggedProfileVisibleOnDouyin(){
   if(!isDouyinWebHost()||isLoginModalVisibleOnDouyin())return false;
   var uInfo=document.querySelector('[data-e2e="user-info"]');
   var txt=clean((uInfo?uInfo.innerText:(document.body&&document.body.innerText))||'',4000);
   return /抖音号[：:]\s*[A-Za-z0-9_.-]{3,}/.test(txt);
  }
  function consume(root,urlStr){
   if(!root||typeof root!=='object')return;
   var u=String(urlStr||'');
   if(/\/passport\/web\/check_qrconnect/i.test(u)){
    var qrData=root.data&&typeof root.data==='object'?root.data:root;
    if((root.message==='success'||Number(root.error_code)===0)&&qrData&&qrData.status==='confirmed'){
     markSsoLoginSuccess();
    }
    return;
   }
   if(/\/(sms_login|sms_login_continue|quick_login|account_login|user\/login)(\/|\?|$)/i.test(u)){
    var lData=root.data&&typeof root.data==='object'?root.data:null;
    if(root.message==='success'||Number(root.error_code)===0||(lData&&Number(lData.error_code)===0&&root.message!=='error')){
     markSsoLoginSuccess();
    }
    return;
   }
   if(/\/aweme\/v1\/web\/user\/profile\/self/i.test(u)){
    if(Number(root.status_code)===0&&root.user&&(root.user.uid||root.user.sec_uid)){
     markSsoLoginSuccess();
    }
    return;
   }
   var codeStr=String(root.code!==undefined?root.code:'');
   var msgStr=String(root.msg||root.message||'');
   if(codeStr==='10001010A'||/用户未登录|请登录后重试|请先登录/.test(msgStr)){
    state.authExpired=true;
    state.captured=false;
    state.ssoSuccess=false;
    state.error='';
    state.login=true;
    state.needSso=true;
    state.orders=[];
    state.byId=Object.create(null);
    return;
   }
   var isAux=/\/order\/ordercount|\/index\/getNewAbUiParams|\/hotsoon\/find\/banners|\/passport\//i.test(u);
   var isOrderPage=/\/order\/page/i.test(u);
   if((typeof root.status_code==='number'&&root.status_code!==0)||(typeof root.st==='number'&&root.st!==0)||(root.code!==undefined&&codeStr!=='0')){
    if(isOrderPage&&!state.captured){
     state.error=clean(msgStr||'抖音订单加载失败，请点击右上角「刷新网页」或重新登录重试。',120);
    }else if(!isAux&&!state.captured&&!state.ssoSuccess&&!hasLocalDyCookie()){
     state.login=true;state.needSso=true;
    }
    return;
   }
   if(isAux)return;
   var orderList=Array.isArray(root.data)?root.data:(root.data&&Array.isArray(root.data.list||root.data.orders)?(root.data.list||root.data.orders):null);
   if(orderList){
    state.authExpired=false;
    state.error='';
    state.captured=true;state.login=false;state.needSso=false;
    orderList.slice(0,30).forEach(function(entry,idx){
     var o=(entry&&entry.order_info)||entry;
     if(!o)return;
     var id=clean(String(o.order_id||o.shop_order_id||o.id||''),80);
     if(!id)return;
     var status=clean(String(o.status_desc||o.order_status_desc||(o.status_info&&o.status_info.text)||''),60);
     if(/取消|关闭|退款成功/.test(status))return;
     var sub=(Array.isArray(o.sub_orders)&&o.sub_orders[0])||(Array.isArray(o.product_item)&&o.product_item[0])||(Array.isArray(o.sku_specs)&&o.sku_specs[0])||o;
     var name=clean(String(sub.product_name||sub.sku_name||o.product_name||o.shop_name||'抖音商品'),120);
     var logCtx='';
     if(o.logistics&&Array.isArray(o.logistics.data)&&o.logistics.data[0]){
      var ld=o.logistics.data[0];
      logCtx=clean((ld.title?ld.title+' ':'')+(ld.context||''),400);
      if(!status&&ld.title)status=clean(String(ld.title),60);
     }
     var detail=clean(String(logCtx||o.logistics_text||o.express_text||(o.logistics_info&&o.logistics_info.desc)||''),400);
     var c=code((o.pick_code?'取件码:'+o.pick_code:'')+' '+detail+' '+status);
     upsert(id,name,status||'待收货',detail,c);
     if(idx<12&&(o.logistics||Number(o.final_status)===3||Number(o.order_status)===3||/发货|运输|派送|待收货|驿站|代收/.test(status+' '+detail)))probeExpress(id);
    });
    return;
   }
   var exp=root.express_info||root.data||root;
   var pick=exp.pick_info||(root.data&&root.data.pick_info)||root.pick_info;
   var routes=Array.isArray(exp.data)?exp.data:(Array.isArray(root.data)?root.data:null);
   if(pick||routes){
    var m=u.match(/[?&]id=([^&#]+)/);
    var oid=m?decodeURIComponent(m[1]):clean(String(exp.order_id||exp.trackingno||'dy_exp'),80);
    var pCode=pick?clean(String(pick.pick_code||''),30):'';
    var pSite=pick?clean(String(pick.pick_site||''),120):'';
    var pAddr=pick?clean(String(pick.pick_address||''),300):'';
    var latest='';
    if(routes&&routes.length){
     for(var i=0;i<routes.length;i++){
      var ctx=clean(routes[i]&&(routes[i].context||routes[i].detail)||'',500);
      if(!latest&&ctx)latest=ctx;
      if(!pCode)pCode=code(ctx);
     }
    }
    var fullDetail=[pSite,pAddr,latest].filter(Boolean).join(' · ');
    if(pCode||fullDetail){
     state.authExpired=false;
     state.error='';
     state.captured=true;state.login=false;state.needSso=false;
     upsert(oid,null,pSite||(pCode?'待取件':null),fullDetail,pCode);
    }
   }
  }
  function response(text,urlStr){
   if(typeof text!=='string'||text.length>2097152)return;
   var trimmed=text.trim();
   if(trimmed.charAt(0)!=='{')return;
   try{consume(JSON.parse(trimmed),urlStr);}catch(ignore){}
  }
  var send=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send=function(){
   this.addEventListener('load',function(){
    try{
     var rawUrl=this.responseURL||location.href;
     var url=new URL(rawUrl,location.href);
     if(url.protocol!=='https:'||!/(^|\.)(jinritemai|snssdk|douyin|toutiao)\.com$/i.test(url.hostname))return;
     if(this.responseType==='json')consume(this.response,url.href);
     else if(!this.responseType||this.responseType==='text')response(this.responseText,url.href);
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
     if(url.protocol==='https:'&&/(^|\.)(jinritemai|snssdk|douyin|toutiao)\.com$/i.test(url.hostname))
      res.clone().text().then(function(t){response(t,url.href);}).catch(function(){});
    }catch(ignore){}
   }).catch(function(){});
   return promise;
  };
  function domOrders(){
   if(isDouyinWebHost())return [];
   var nodes=document.querySelectorAll('[class*="order"],[class*="express"],[class*="logistics"],li'),rows=[],seen=Object.create(null);
   for(var i=0;i<nodes.length&&rows.length<40;i++){
    var text=(nodes[i].innerText||'').slice(0,1500),c=code(text);
    if(!c||/取消|关闭|退款成功/.test(text))continue;
    if(seen[c])continue;
    seen[c]=true;
    rows.push({id:'dy_'+c,name:'抖音待取包裹',status:'待取件',detail:text.replace(/\s+/g,' ').slice(0,400),code:c});
   }
   return rows;
  }
  window.HandyDyOrders={
   ingest:response,
   snapshot:function(){
    adaptDyWebLogin();
    var isLoginHost=isDouyinWebHost()||(/(^|\.)(snssdk|toutiao)\.com$/i.test(location.hostname)&&/\/passport\/|\/login/i.test(location.pathname));
    if(isLoginHost){
     if(state.ssoSuccess||isLoggedProfileVisibleOnDouyin()){
      markSsoLoginSuccess();
      return {href:location.href,login:false,needSso:false,ssoDone:true,ready:false,orders:[]};
     }
     return {href:location.href,login:true,needSso:false,ready:false,orders:[]};
    }
    if(state.authExpired){
     return {href:location.href,login:true,needSso:true,ready:false,orders:[]};
    }
    var hasCookie=hasLocalDyCookie();
    var domRows=domOrders();
    var rows=state.orders.length>0?state.orders:domRows;
    var bodyText=clean((document.body&&document.body.innerText)||'',4000);
    if(!state.captured&&!hasCookie&&rows.length===0&&/点击重试|请在抖音内打开|请先登录|你还未登录/.test(bodyText)){
     state.login=true;state.needSso=true;
    }
    var login=state.login&&!state.captured&&rows.length===0;
    if(!login&&!state.captured&&rows.length===0&&state.error){
     return {href:location.href,login:false,needSso:false,ready:false,error:state.error,orders:[]};
    }
    return {href:location.href,login:login,needSso:login&&state.needSso,ready:!login&&(state.captured||rows.length>0),orders:login?[]:rows};
   }
  };
 }
 return window.HandyDyOrders.snapshot();
})()