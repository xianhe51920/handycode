;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://convenient-h5.jd.com' || location.pathname !== '/identityCode') return null;
 if (window.HandyJdIdentity) return window.HandyJdIdentity.snapshot();
 if (!document.body || !document.getElementById('root')) return null;
 var root = document.documentElement, source = document.getElementById('root');
 var style = document.createElement('style');
 style.textContent = `
html,body {margin:0!important;padding:0!important;min-height:0!important;background:transparent!important;overflow:hidden!important}
#root {position:fixed!important;left:-20000px!important;top:0!important;width:100vw!important;pointer-events:none!important;visibility:hidden!important}
#hc-jd-identity {box-sizing:border-box;min-height:clamp(202px,calc(270px - 20vw),226px);padding:14px 16px 16px;color:#242b37;font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;overflow:hidden;position:relative;display:flex;align-items:center;justify-content:center}
#hc-jd-identity * {box-sizing:border-box}
#hc-jd-content {width:100%;text-align:center;transform-origin:50% 50%;will-change:transform}
.hc-jd-phone {font-size:16px;line-height:24px;margin:0 0 12px}
.hc-jd-barcode {display:block;width:100%;padding:10px;background:#fff;border-radius:10px;outline:none}
.hc-jd-barcode svg {display:block;width:100%;height:86px}
.hc-jd-expiry {font-size:12px;line-height:18px;color:#697386;margin:12px 0 0}
html[data-hc-jd-dark] #hc-jd-identity {color:#eceff4}
html[data-hc-jd-dark] .hc-jd-expiry {color:#949eb0}
@media(prefers-reduced-motion:reduce) {#hc-jd-content {will-change:auto}}
`;
 document.head.appendChild(style);
 var mount = document.createElement('main'); mount.id = 'hc-jd-identity';
 var content = document.createElement('div'); content.id = 'hc-jd-content';
 var phone = document.createElement('p'); phone.className = 'hc-jd-phone';
 var barcode = document.createElement('div'); barcode.className = 'hc-jd-barcode';
 barcode.tabIndex = 0; barcode.setAttribute('role','button');
 var expiry = document.createElement('p'); expiry.className = 'hc-jd-expiry';
 content.append(phone,barcode,expiry); mount.appendChild(content); document.body.appendChild(mount);
 source.setAttribute('aria-hidden','true');
 var signature = '', ready = false, refreshing = false, failed = false;
 // Observe completion of this one official request without changing its URL,
 // headers, body or credentials. Never export the response or identity value.
 var xhrOpen = XMLHttpRequest.prototype.open, xhrSend = XMLHttpRequest.prototype.send;
 var requests = new WeakMap();
 XMLHttpRequest.prototype.open = function (method,url) {
  var own = false;
  try { var u = new URL(url,location.href); own = u.origin === 'https://lop-proxy.jd.com' && u.pathname === '/generateIdentityCode'; } catch (invalid) {}
  requests.set(this,own);
  return xhrOpen.apply(this,arguments);
 };
 XMLHttpRequest.prototype.send = function () {
  if (requests.get(this)) {
   refreshing = true; failed = false;
   this.addEventListener('loadend',function () {
    var response;
    try { response = this.responseType === 'json' ? this.response : JSON.parse(this.responseText); } catch (invalid) {}
    var data = response && response.data;
    var success = this.status >= 200 && this.status < 300 && response
     && (Number(response.errorCode) === 0 || Number(response.code) === 1)
     && data && typeof data.identityCode === 'string' && data.identityCode.length > 0;
    failed = !success; refreshing = false;
   },{once:true});
  }
  return xhrSend.apply(this,arguments);
 };
 var expanded = false, progress = 0, available = 450, frame = 0, lastHeight = 0;
 function height() {
  var h = Math.ceil(mount.getBoundingClientRect().height);
  if (h > 0 && h !== lastHeight) { lastHeight = h; console.log('__HANDYCODE_JD_IDENTITY_HEIGHT__:' + h); }
  return h;
 }
 function draw(value) {
  progress = value;
  var width = content.offsetWidth, natural = content.offsetHeight;
  if (!width || !natural) return;
  if (!value) { content.style.transform = ''; mount.style.height = ''; mount.style.alignItems = ''; }
  else {
   var target = Math.max(natural,available - 30), angle = Math.PI * value / 2;
   var scaleEnd = Math.min(width / natural,target / width);
   var rotated = width * Math.sin(angle) + natural * Math.cos(angle);
   var scale = Math.min(1 + value * (scaleEnd - 1),width / (width * Math.cos(angle) + natural * Math.sin(angle)),target / rotated);
   var base = Math.max(natural,Math.max(202,Math.min(226,270-.2*(window.innerWidth||340)))-30);
   var box = Math.max(scale*rotated,base+(target-base)*value);
   mount.style.height = (box + 30) + 'px';
   mount.style.alignItems = 'flex-start';
   content.style.transform = 'translateY(' + ((box-natural)/2) + 'px) rotate(' + (90*value) + 'deg) scale(' + scale + ')';
  }
  height();
 }
 function toggle() {
  if (!ready) return;
  cancelAnimationFrame(frame); expanded = !expanded;
  barcode.setAttribute('aria-expanded',String(expanded));
  barcode.setAttribute('aria-label',expanded ? '还原身份码' : '放大身份码');
  var from = progress, to = expanded ? 1 : 0, start;
  if (matchMedia('(prefers-reduced-motion:reduce)').matches) { draw(to); return; }
  function step(now) { if (start === undefined) start = now; var t = Math.min(1,(now-start)/360); draw(from+(to-from)*t*t*(3-2*t)); if (t<1) frame=requestAnimationFrame(step); }
  frame = requestAnimationFrame(step);
 }
 content.addEventListener('click',toggle);
 barcode.addEventListener('keydown',function (e) { if(e.key==='Enter'||e.key===' ') {e.preventDefault();toggle();} });
 function snapshot() {
  // Read the live official SVG only. The source Vue component retains its API,
  // login, validation, expiration timer and native refresh handler.
  var svg = source.querySelector('.qr svg'), p = source.querySelector('.font-18');
  var busy = !!source.querySelector('.van-loading');
  var valid = svg && svg.querySelectorAll('rect').length > 10 && p && p.textContent.trim();
  if (valid && !busy) {
   var next = svg.outerHTML + p.textContent;
   if (next !== signature) {
    signature = next; phone.textContent = p.textContent.trim().replace(/(1\d{2})\d{4}(\d{4})/g,'$1****$2');
    barcode.replaceChildren(svg.cloneNode(true)); ready = true; draw(progress);
   }
   var note = source.querySelector('.content-right > div');
   expiry.textContent = note && /分钟.*更新/.test(note.textContent) ? note.textContent.trim() : '每20分钟自动更新';
  }
  height();
  return {href:location.href,ready:!!ready && !busy && !failed && !refreshing,refreshing:refreshing || busy,failed:failed,height:lastHeight};
 }
 function refresh() {
  if (refreshing) return;
  var button = source.querySelector('.refresh');
  if (!button) return;
  refreshing = true; failed = false; button.click();
 }
 window.HandyJdIdentity = {snapshot:snapshot,refresh:refresh,setDark:function (v) {root.toggleAttribute('data-hc-jd-dark',!!v);},setAvailable:function (v) {available=Math.max(180,Number(v)||450);if(progress)draw(progress);}};
 window.addEventListener('resize',function () {draw(progress);});
 barcode.setAttribute('aria-label','放大身份码');
 return snapshot();
})();
