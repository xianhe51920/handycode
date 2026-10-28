;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://m.pinduoduo.net' || location.pathname !== '/mdkd/package') return;
 if (window.HandyPddLayout) return;
 var source, shell, backBar, sourceMode = false, queued = false, touching = false, lastTouch = 0;
 var refreshes = {}, failedRefresh = {};
 var reveals = new Set();
 var authenticated = false;
 var bootObserver, bootTimeout;
 var links = new WeakMap(), signatures = {}, started = Date.now(), triedPackage = false, lastPhase;
 var props = ['display','box-sizing','color','background-color','background-image','background-size','background-position','background-repeat','font-family','font-size','font-weight','line-height','letter-spacing','text-align','white-space','word-break','padding-top','padding-right','padding-bottom','padding-left','margin-top','margin-right','margin-bottom','margin-left','border-top','border-right','border-bottom','border-left','border-radius','box-shadow','opacity','align-items','align-self','justify-content','flex-direction','flex-wrap','flex-grow','flex-shrink','gap','width','height','min-width','min-height','max-width','max-height','position','top','right','bottom','left','transform'];
 function phase(value) { if (lastPhase !== value) { lastPhase = value; console.info('__HANDYCODE_PDD_LAYOUT__:' + value); } }
 function css(node, values) { Object.keys(values).forEach(function (key) { node.style.setProperty(key, values[key], 'important'); }); }
 function placeholder(identity) {
  return identity
   ? '<div class="hc-pdd-placeholder hc-pdd-identity-placeholder" aria-label="正在加载身份码"><i></i><i></i><i></i><i></i><i></i></div>'
   : '<div class="hc-pdd-placeholder hc-pdd-parcels-placeholder" aria-label="正在加载待取包裹"><i></i><i></i><i></i></div>';
 }
 function present(mount, content) {
  var entering = !mount.dataset.ready;
  var before = mount.getBoundingClientRect().height;
  mount.style.height = '';
  mount.replaceChildren(content); mount.dataset.ready = 'true';
  var after = mount.getBoundingClientRect().height;
  // Normal barcode and placeholder have identical reserved space. Animate
  // exceptional height changes (large fonts, QR mode or a growing parcel list).
  var animate = !document.hidden && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (entering && animate && content.animate) {
   // Opacity belongs only to a short, cancellable entry effect. Never leave
   // retained content dependent on a paused CSS animation's backwards fill.
   var reveal = content.animate([{opacity:0},{opacity:1}], {duration:220,easing:'ease-out'});
   reveals.add(reveal);
   reveal.onfinish = function () { reveals.delete(reveal); };
   reveal.oncancel = function () { reveals.delete(reveal); };
  }
  if (animate && before > 0 && Math.abs(after - before) > 1 && mount.animate) {
   mount.animate([{height:before + 'px'},{height:after + 'px'}], {duration:240,easing:'cubic-bezier(.2,.7,.2,1)'});
  }
 }
 function clone(node, budget) {
  if (--budget.count < 0) throw new Error('projection-too-large');
  if (node.nodeType === 3) return document.createTextNode(node.nodeValue);
  if (node.nodeType !== 1 || /^(SCRIPT|STYLE|LINK|IFRAME|FORM|INPUT|TEXTAREA|SELECT|OBJECT|EMBED|NOSCRIPT)$/.test(node.tagName)) return null;
  var computed = getComputedStyle(node);
  if (computed.display === 'none' || computed.visibility === 'hidden') return null;
  var copy = node.cloneNode(false);
  Array.from(copy.attributes).forEach(function (attribute) {
   if (/^on/i.test(attribute.name) || /^(srcdoc|formaction|id|class)$/i.test(attribute.name)) copy.removeAttribute(attribute.name);
  });
  if (copy.tagName === 'A') { copy.removeAttribute('href'); copy.removeAttribute('target'); }
  if (copy.tagName === 'IMG' && node.currentSrc) copy.src = node.currentSrc;
  props.forEach(function (name) { copy.style.setProperty(name, computed.getPropertyValue(name), 'important'); });
  css(copy, {'max-width':'100%','box-sizing':'border-box','overflow':'visible','touch-action':'pan-y'});
  if (/^(fixed|sticky)$/.test(computed.position) || /auto|scroll/.test(computed.overflowY)) css(copy, {position:'static',height:'auto','max-height':'none',transform:'none'});
  Array.from(node.childNodes).forEach(function (child) { var result = clone(child, budget); if (result) copy.appendChild(result); });
  links.set(copy, node);
  return copy;
 }
 function copyPart(node, className) {
  if (!node) return null;
  var result = clone(node, {count:1000});
  if (result) {
   result.className = className;
   css(result, {position:'static',transform:'none',width:'100%',height:'auto','min-height':'0',margin:'0',padding:'0','text-align':'center'});
  }
  return result;
 }
 function identityRoot() { return source.querySelector('[class*="identification-code-container"]'); }
 function pendingRoots() {
  var empty = source.querySelector('[class*="package-none-container"]');
  if (empty) return [empty];
  return Array.from(source.querySelectorAll('[class*="package-list-wrapper"] .package-list-picking'));
 }
 function identityReady(root) {
  if (!root) return false;
  var image = root.querySelector('.identification-code-img');
  var qr = root.querySelector('.identification-qr-code-svg');
  return !!(image && image.complete && image.naturalWidth > 0 || qr);
 }
 function canUpdate(part, signature) {
  if (refreshes[part] && refreshes[part].loading) return false;
  if (failedRefresh[part] && failedRefresh[part] === signature) return false;
  delete failedRefresh[part]; return true;
 }
 function updateIdentity() {
  var root = identityRoot(), mount = document.getElementById('hc-pdd-identity');
  if (!canUpdate('identity', root ? root.outerHTML : 'missing')) return false;
  if (!identityReady(root)) {
   if (mount.dataset.ready) {
    delete mount.dataset.ready; delete signatures.identity;
    mount.innerHTML = placeholder(true);
   }
   return false;
  }
  var signature = root.outerHTML;
  if (signatures.identity === signature && mount.dataset.ready) return true;
  var content = document.createElement('div');
  content.className = 'hc-pdd-code';
  var phone = copyPart(root.querySelector('.identification-code-mobile-info'), 'hc-pdd-phone');
  if (phone) content.appendChild(phone);
  var image = root.querySelector('.identification-code-img');
  if (image) {
   // Reuse the official barcode image unchanged. Only the surrounding layout
   // is horizontal; no barcode data is decoded or regenerated.
   var barcode = document.createElement('img');
   barcode.className = 'hc-pdd-barcode';
   barcode.src = image.currentSrc || image.src;
   barcode.alt = '拼多多官方身份码';
   content.appendChild(barcode);
  } else {
   var qr = root.querySelector('.identification-qr-code-svg').cloneNode(true);
   qr.classList.add('hc-pdd-qr');
   css(qr, {width:'180px',height:'180px',transform:'none',display:'block',margin:'0 auto'});
   content.appendChild(qr);
  }
  var value = root.querySelector('.identification-code-content-code');
  if (value) {
   var code = document.createElement('div'); code.className = 'hc-pdd-value';
   code.textContent = value.textContent; content.appendChild(code);
  }
  var warning = root.querySelector('.identification-code-content-text');
  if (warning) {
   var note = document.createElement('div'); note.className = 'hc-pdd-warning';
   note.textContent = warning.textContent; content.appendChild(note);
  }
  var tips = copyPart(root.querySelector('.identification-code-tips'), 'hc-pdd-tips');
  if (tips) content.appendChild(tips);
  present(mount, content); signatures.identity = signature;
  return true;
 }
 function updatePending() {
  var roots = pendingRoots(), mount = document.getElementById('hc-pdd-parcels');
  if (!canUpdate('parcels', roots.map(function (node) { return node.outerHTML; }).join('') || 'missing')) return false;
  if (!roots.length || !roots.some(function (node) { return node.textContent.trim(); })) return false;
  var signature = roots.map(function (node) { return node.outerHTML; }).join('');
  if (signatures.parcels === signature && mount.dataset.ready) return true;
  var content = document.createElement('div');
  roots.forEach(function (node) {
   var copy = clone(node, {count:6000});
   if (!copy) return;
   css(copy, {position:'static',transform:'none',width:'100%',height:'auto','min-height':'0','max-height':'none',margin:'0'});
   content.appendChild(copy);
  });
  if (!content.childElementCount) return false;
  present(mount, content); signatures.parcels = signature;
  return true;
 }
 function sourceTab(label) {
  var walker = document.createTreeWalker(source, 4), node;
  while ((node = walker.nextNode())) if (node.nodeValue.trim() === label) {
   var target = node.parentElement.closest('.rm-tab-bar-tab');
   if (target) return target;
  }
  return null;
 }
 function update() {
  if (!shell || sourceMode) return;
  if (location.pathname !== '/mdkd/package') { workflow(true); return; }
  if (touching || Date.now() - lastTouch < 800) { queue(); return; }
  try {
   var identity = updateIdentity(), parcels = updatePending();
   if (!authenticated && (identity || parcels)) {
    authenticated = true;
    console.info('__HANDYCODE_PDD_AUTH__:signed-in');
   }
   // Official TabBar normally pre-renders adjacent pages. Initialize the
   // package pane once if an official update makes it lazy instead.
   if (identity && !parcels && !triedPackage && Date.now() - started > 1500) {
    var tab = sourceTab('取快递');
    if (tab) {
     triedPackage = true; tab.click();
     setTimeout(function () { var id = sourceTab('身份码'); if (id) id.click(); queue(); }, 250);
    }
   }
   if (identity && parcels) phase('cards');
   if (Date.now() - started > 15000) ['identity','parcels'].forEach(function (type) {
    var mount = document.getElementById('hc-pdd-' + type);
    if (!mount.dataset.ready && !refreshes[type] && !failedRefresh[type] && !mount.querySelector('.hc-pdd-recover')) {
     mount.innerHTML = '<p class="hc-pdd-wait">官方内容暂未就绪，请刷新或完成官方页面提示。</p>';
     var recover = document.createElement('button'); recover.className = 'hc-pdd-recover';
     recover.textContent = '打开官方页面'; recover.addEventListener('click', function () { workflow(true); });
     mount.appendChild(recover);
    }
   });
  } catch (error) { phase('unavailable'); }
 }
 function queue() {
  if (queued) return;
  queued = true;
  setTimeout(function () { queued = false; update(); }, 160);
 }
 function refresh(part) {
  if (refreshes[part] || !shell || sourceMode) return;
  var mount = document.getElementById('hc-pdd-' + part);
  var button = shell.querySelector('[data-hc-pdd-refresh="' + part + '"]');
  if (!mount || !button) return;
  var job = {loading:true}, originalHeight = mount.getBoundingClientRect().height;
  refreshes[part] = job; delete failedRefresh[part];
  button.disabled = true; button.textContent = '刷新中';
  // Keep this card's exact current space while its official request runs.
  if (originalHeight > 0) mount.style.height = originalHeight + 'px';
  delete mount.dataset.ready; delete signatures[part];
  mount.innerHTML = placeholder(part === 'identity');
  function finish(error) {
   if (refreshes[part] !== job) return;
   delete refreshes[part]; button.disabled = false; button.textContent = error ? '重试' : '刷新';
   if (error) {
    var root = identityRoot();
    failedRefresh[part] = part === 'identity' ? (root ? root.outerHTML : 'missing') : (pendingRoots().map(function (node) { return node.outerHTML; }).join('') || 'missing');
    mount.innerHTML = '<p class="hc-pdd-wait">刷新未完成，请点本卡片右上角重试。</p>';
   }
  }
  var service = window.HandyPddRefresh;
  Promise.resolve().then(function () {
   if (!service) throw new Error('refresh-unavailable');
   return service.run(part);
  }).then(function () {
   job.loading = false;
   var until = Date.now() + 1500;
   // Official canvas conversion and React commit can follow the HTTP result.
   function settle() {
    if (refreshes[part] !== job) return;
    var ready = part === 'identity' ? updateIdentity() : updatePending();
    if (ready) finish(false);
    else if (Date.now() >= until) finish(true);
    else { if (originalHeight > 0) mount.style.height = originalHeight + 'px'; setTimeout(settle, 40); }
   }
   setTimeout(settle, 40);
  }).catch(function () { finish(true); });
 }
 function workflow(show) {
  sourceMode = show;
  document.documentElement.classList.toggle('hc-pdd-source-mode', show);
  shell.hidden = show; backBar.hidden = !show;
  phase(show ? 'source' : 'cards');
  if (!show) queue();
 }
 function back() {
  if (!sourceMode) return false;
  // Next.js may navigate official forms without replacing the document.
  // Return to the actual package route before showing the combined cards.
  if (location.pathname !== '/mdkd/package') { history.back(); return true; }
  workflow(false); return true;
 }
 function clicked(event) {
  var node = event.target, original;
  while (node && node !== shell && !(original = links.get(node))) node = node.parentNode;
  if (!original || !original.isConnected) return;
  event.preventDefault();
  // Switch-code controls update in place. Other official actions temporarily
  // reveal the original page so its forms and dialogs remain usable.
  if (!original.closest('.identification-code-tips')) workflow(true);
  original.click(); queue();
 }
 function boot() {
  if (shell) return;
  source = document.getElementById('__next');
  if (!source || !document.body) {
   // The injected bundle can execute before Next's root is inserted. Keep
   // waiting for that document's root instead of permanently missing boot.
   if (!bootObserver) {
    bootObserver = new MutationObserver(boot);
    bootObserver.observe(document.documentElement || document, {subtree:true,childList:true});
    bootTimeout = setTimeout(function () { if (!shell) phase('unavailable'); }, 15000);
    window.addEventListener('pagehide', function () { bootObserver.disconnect(); clearTimeout(bootTimeout); }, {once:true});
   }
   return;
  }
  if (bootObserver) { bootObserver.disconnect(); clearTimeout(bootTimeout); }
  source.setAttribute('data-hc-pdd-source', '');
  var style = document.createElement('style');
  style.textContent = `
html,body{height:auto!important;min-height:100%!important;overflow-x:hidden!important;overflow-y:auto!important}
body{margin:0!important;padding:12px 10px 24px!important;background:#f5f6f8!important}
[data-hc-pdd-source]{position:fixed!important;left:-100000px!important;top:0!important;width:100vw!important;height:100vh!important;transform:translateZ(0)!important;pointer-events:none!important}
#hc-pdd-shell{color:#242b37;font:14px/1.5 system-ui,sans-serif;overflow:visible;touch-action:pan-y}
#hc-pdd-shell *{box-sizing:border-box}
.hc-pdd-card{margin-bottom:14px;border:1px solid #e9ecf0;border-radius:16px;background:#fff;overflow:visible}
.hc-pdd-heading{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #f0f2f5}
.hc-pdd-heading h2{margin:0;font-size:16px;font-weight:650}
.hc-pdd-heading button{border:0;background:none;color:#d4542d;padding:5px 0;font:12px system-ui,sans-serif}
.hc-pdd-content{padding:16px 10px;min-width:0;overflow:visible}
#hc-pdd-identity{min-height:310px}
#hc-pdd-parcels{min-height:140px}
.hc-pdd-content[data-ready] > div{opacity:1}
.hc-pdd-code{display:flex;flex-direction:column;align-items:center;gap:12px;padding:4px 8px 8px}
.hc-pdd-phone{font-size:14px!important;text-align:center!important}
.hc-pdd-barcode{display:block!important;width:100%!important;max-width:100%!important;height:100px!important;object-fit:contain;transform:none!important;margin:8px 0}
.hc-pdd-value{width:100%;text-align:center;font-size:14px;overflow-wrap:anywhere}
.hc-pdd-warning,.hc-pdd-tips{font-size:11px!important;color:#89919d!important;text-align:center!important}
.hc-pdd-placeholder{display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:10px 8px;overflow:hidden}
.hc-pdd-identity-placeholder{height:278px}
.hc-pdd-parcels-placeholder{height:108px}
.hc-pdd-placeholder i{position:relative;display:block;flex-shrink:0;width:70%;height:14px;border-radius:7px;overflow:hidden;background:#edf0f4}
.hc-pdd-identity-placeholder i:first-child{height:18px}
.hc-pdd-identity-placeholder i:nth-child(2){height:100px;width:100%}
.hc-pdd-placeholder i::after{position:absolute;inset:0;content:'';background:linear-gradient(90deg,transparent,rgba(255,255,255,.7),transparent);transform:translateX(-100%);animation:hc-pdd-shimmer 1.4s linear infinite;will-change:transform}
.hc-pdd-parcels-placeholder i:last-child{height:22px;width:52%}
@keyframes hc-pdd-shimmer{to{transform:translateX(100%)}}
@media(prefers-reduced-motion:reduce){.hc-pdd-placeholder i::after{animation:none}}
.hc-pdd-wait{padding:20px 8px;color:#89919d;text-align:center;font-size:12px}
#hc-pdd-back{position:fixed;left:0;right:0;top:0;height:44px;z-index:2147483647;background:#fff;border-bottom:1px solid #e9ecf0;display:flex;align-items:center;padding:0 14px}
#hc-pdd-back button{background:none;border:0;color:#d4542d;font:14px system-ui,sans-serif}
#hc-pdd-shell[hidden],#hc-pdd-back[hidden]{display:none!important}
html.hc-pdd-source-mode,html.hc-pdd-source-mode body{overflow:hidden!important}
.hc-pdd-source-mode [data-hc-pdd-source]{left:0!important;top:44px!important;height:calc(100vh - 44px)!important;pointer-events:auto!important}
`;
  document.head.appendChild(style);
  shell = document.createElement('main'); shell.id = 'hc-pdd-shell';
  shell.innerHTML = '<section class="hc-pdd-card"><div class="hc-pdd-heading"><h2>身份码</h2><button type="button" data-hc-pdd-refresh="identity">刷新</button></div><div class="hc-pdd-content" id="hc-pdd-identity">' + placeholder(true) + '</div></section><section class="hc-pdd-card"><div class="hc-pdd-heading"><h2>待取包裹</h2><button type="button" data-hc-pdd-refresh="parcels">刷新</button></div><div class="hc-pdd-content" id="hc-pdd-parcels">' + placeholder(false) + '</div></section>';
  document.body.appendChild(shell);
  backBar = document.createElement('div'); backBar.id = 'hc-pdd-back'; backBar.hidden = true;
  backBar.innerHTML = '<button type="button">‹ 返回取件页</button>';
  backBar.querySelector('button').addEventListener('click', back);
  document.body.appendChild(backBar);
  shell.addEventListener('click', clicked);
  shell.querySelectorAll('[data-hc-pdd-refresh]').forEach(function (button) {
   button.addEventListener('click', function () {
    refresh(button.getAttribute('data-hc-pdd-refresh'));
   });
  });
  shell.addEventListener('touchstart', function () { touching = true; }, {passive:true});
  function endTouch() { touching = false; lastTouch = Date.now(); queue(); }
  shell.addEventListener('touchend', endTouch, {passive:true});
  shell.addEventListener('touchcancel', endTouch, {passive:true});
  var observer = new MutationObserver(queue);
  observer.observe(source, {subtree:true,childList:true,characterData:true,attributes:true});
  source.addEventListener('load', queue, true);
  window.addEventListener('popstate', function () {
   if (sourceMode && location.pathname === '/mdkd/package') workflow(false);
  });
  var timer = setInterval(function () { if (!document.hidden) update(); }, 1000);
  document.addEventListener('visibilitychange', function () {
   if (document.hidden) { reveals.forEach(function (animation) { animation.cancel(); }); reveals.clear(); }
   else queue();
  });
  window.addEventListener('pagehide', function () { observer.disconnect(); clearInterval(timer); });
  phase('loading'); update();
 }
 window.HandyPddLayout = {update:update, back:back};
 if (document.body) boot(); else document.addEventListener('DOMContentLoaded', boot, {once:true});
})();
