(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)jd\.com$/i.test(location.hostname) || window.top !== window) return null;
 if (!window.HandyJdOrders) {
  var state={captured:false,orders:[],revision:0};
  function clean(value,max){return typeof value==='string'?value.slice(0,max):'';}
  function code(text) {
   var s=String(text||'');
   var m=s.match(/(?:取件码|提货码|取货码|凭码|自提码|取件密码|开箱码|钥匙码)(?:为|是)?[:：\s【\[（(]*([A-Za-z0-9-]{3,16})(?![A-Za-z0-9-])/);
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
  function jdLink(raw){
   if(typeof raw!=='string'||!raw)return '';
   try{
    var u=new URL(raw,location.href);
    if(u.protocol==='https:'&&/(^|\.)jd\.com$/i.test(u.hostname))return u.href.slice(0,500);
   }catch(ignore){}
   return '';
  }
  function jdImage(raw){
   if(typeof raw!=='string'||!raw)return '';
   try{
    var u=new URL(raw.indexOf('//')===0?'https:'+raw:raw,location.href);
    if(u.protocol==='https:'&&/(^|\.)(360buyimg|jd)\.com$/i.test(u.hostname))return u.href.slice(0,500);
   }catch(ignore){}
   return '';
  }
  function extractStation(detail){
   var s=String(detail||'').replace(/\s+/g,' ').trim();
   var loc=s.match(/【([^】]{2,40}?(?:驿站|自提点|代收点|快递柜|自提柜|丰巢|门卫|超市|菜鸟)[^】]{0,20})】/);
   return loc?loc[1]:'';
  }
  function shortSummary(detail,tip){
   var s=String(detail||'').replace(/\s+/g,' ').trim();
   var loc=extractStation(s);
   if(loc)return '已派送至 '+loc+(tip?' · '+tip:'');
   var brief=s.replace(/如有疑问请电联.*$/,'').replace(/投诉电话.*$/,'').replace(/有事呼叫我.*$/,'').trim();
   if(brief.length>48)brief=brief.slice(0,48)+'…';
   return brief||(tip||'');
  }
  function consume(root) {
   var list=root&&root.body&&Array.isArray(root.body.orderList)?root.body.orderList:(root&&Array.isArray(root.orderList)?root.orderList:null);
   if(!list)return;
   var rows=[],seen=Object.create(null);
   list.slice(0,100).forEach(function(o){
    if(!o||!o.orderId||seen[o.orderId])return;
    var status=clean(o.orderStatusInfo&&o.orderStatusInfo.orderStatusName,100);
    if(/取消|关闭|退款/.test(status))return;
    var p=o.progressInfo||{},text=clean(p.content,1500),tip=clean(p.tip,500),title=clean(p.title,200),sub=clean(p.subContent,500);
    var pText=buildPromptText(o.promptInfo);
    var detailText=text||pText||tip||sub;
    var goods=Array.isArray(o.wareInfoList)?o.wareInfoList[0]:null;
    var name=clean(goods&&goods.wareName,180)||clean(o.wareName,180)||'京东订单';
    var image=jdImage(goods&&(goods.imageUrl||goods.imgUrl||goods.imgPath||goods.picUrl));
    var extracted=code(text+' '+pText+' '+status+' '+tip+' '+title+' '+sub);
    var done=/完成|签收|已取/.test(status);
    var arrived=!!extracted||(!done&&(/待取|待自提|待提货/.test(status)||/已派送至|已投递|已放入|已存放在|已到达.*(?:驿站|自提点|代收点|快递柜|自提柜)|请您?凭(?:取件码|提货码|取货码|自提码)/.test(detailText)));
    var link=jdLink(p.progressLink)||jdLink(o.orderDetailLink);
    var station=extractStation(detailText);
    rows.push({id:String(o.orderId).slice(0,80),name:name,image:image,station:station,
     status:status,detail:shortSummary(detailText,tip),code:extracted,arrived:arrived,link:link});
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
   var problem='',empty=false;
   var notice=!login&&document.querySelector('[class*="index-module__errorCon"]');
   if(notice&&notice.getClientRects().length){
    var message=(notice.innerText||'').slice(0,500).replace(/\s/g,'');
    empty=/(?:暂无|还没有|没有|未找到|暂未查询到)(?:符合条件的|相关的?|任何)?(?:订单|包裹)|(?:订单|包裹)空空如也/.test(message)
     && !/异常|失败|繁忙|通道|重试|再试/.test(message);
    if(!empty&&/大促异常火爆|快速通道|服务繁忙/.test(message))problem='busy';
    else if(!empty&&/异常|失败|出错|错误|无法加载|重新加载|重试|再试/.test(message))problem='unavailable';
   }
   var rows=state.captured?state.orders:domOrders();
   return {href:location.href,login:login,problem:problem,ready:!login&&!problem&&(state.captured||rows.length>0||empty),orders:login||problem||empty?[]:rows};
  }};
 }
 return window.HandyJdOrders.snapshot();
})()
