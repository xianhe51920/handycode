(function () {
 'use strict';
 if (location.protocol !== 'https:' || window.top !== window) return null;
 var host = location.hostname, path = location.pathname;
 var ordersHost = host === 'www.jdl.com' || host === 'i.jdl.com';
 var loginHost = host === 'sso.jdl.com' || host === 'passport.jdl.com'
  || host === 'passport.jd.com' || host === 'plogin.m.jd.com';
 function snapshot(login, ready, orders) {
  return {href:location.href, login:login, ready:ready, orders:orders};
 }
 if (loginHost || ordersHost && /^\/login(?:\/|$)/.test(path)) return snapshot(true, false, []);
 if (!ordersHost || !/^\/search\/orderList\/?$/.test(path)) return null;

 var loginState;
 try { loginState = sessionStorage.getItem('isLogin'); } catch (ignored) {}
 if (loginState === '0') return snapshot(true, false, []);
 var root = document.querySelector('.order-list-page');
 var page = root && root.__vue__;
 if (!page || !Array.isArray(page.recvOrderList) || page.loading || !/^\d+$/.test(String(page.recvTotal))) {
  return snapshot(false, false, []);
 }
 // The official page reports zero rows on some request failures as well.
 if (loginState !== '1' && !page.recvOrderList.length) return snapshot(false, false, []);

 var tabs = root.querySelectorAll('.order-list-header .tab-bar .tab-item');
 var receiveTab = tabs.length === 2 ? tabs[1] : null;
 if (receiveTab && !root.hasAttribute('data-hc-jdl-receive-prepared')) {
  root.setAttribute('data-hc-jdl-receive-prepared', '');
  var useLargerPage = page.pageNum === 1 && Number(page.recvTotal) > page.pageSize
   && Array.isArray(page.pageSizesOptions) && page.pageSizesOptions.includes(50) && page.pageSize < 50;
  if (useLargerPage) page.pageSize = 50;
  if (useLargerPage || !receiveTab.classList.contains('active')) {
   receiveTab.click(); // The official handler selects the receive tab and fetches its page.
   return snapshot(false, false, []);
  }
 }

 var rows = [], seen = new Set();
 page.recvOrderList.forEach(function (order, index) {
  if (!order || !order.shipmentInfo) return;
  var shipment = order.shipmentInfo, rawCode = shipment.receiveCode;
  // pickupCode is for handing a parcel to the courier; signVerifyCode is for signing.
  if (typeof rawCode !== 'string' && typeof rawCode !== 'number') return;
  var code = String(rawCode).trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9-]{1,23}$/.test(code)) return;
  var base = order.baseOrderInfo || {};
  var status = String((base.cusBaseInfo || {}).orderStatusName || '').trim();
  if (/^(?:已签收|已取件|已领取|签收成功|已完成|已取消|已关闭|已拒收|已退回)$/.test(status)) return;
  var waybill = String(base.customOrderNo || '').trim();
  var key = waybill && waybill + ':' + code;
  if (key && seen.has(key)) return;
  if (key) seen.add(key);
  var station = String(shipment.receiveStationName || (shipment.endStation || {}).stationName || '').trim();
  rows.push({
   id:'jdl_' + index,
   name:station.slice(0, 80) || '京东快递待取包裹',
   status:'待取件',
   detail:waybill ? '运单尾号 ' + waybill.slice(-4) : '',
   code:code
  });
 });
 return snapshot(false, true, rows);
})()
