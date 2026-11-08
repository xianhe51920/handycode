;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://m.pinduoduo.net' || location.pathname !== '/mdkd/package') return;
 if (window.HandyPddLayout) return;
 var source, shell, backBar, sourceMode = false, openingPhoneManagement = false, touching = false, lastTouch = 0;
 var active = true, pendingParts = 0, projectionTimer = null, sourceObserver;
 var IDENTITY = 1, PARCELS = 2, BOTH = IDENTITY | PARCELS;
 var identitySelector = '[class*="identification-code-container"]';
 var parcelsSelector = '[class*="package-list-wrapper"],[class*="package-none-container"]';
 var refreshes = {}, failedRefresh = {};
 var authenticated = false;
 var bootObserver, bootTimeout;
 var links = new WeakMap(), savedStyles = new WeakMap(), signatures = {}, started = Date.now(), triedPackage = false, lastPhase, darkOverride = null;
 var props = ['display','box-sizing','color','background-color','background-image','background-size','background-position','background-repeat','font-family','font-size','font-weight','line-height','letter-spacing','text-align','white-space','word-break','padding-top','padding-right','padding-bottom','padding-left','margin-top','margin-right','margin-bottom','margin-left','border-top','border-right','border-bottom','border-left','border-radius','box-shadow','opacity','align-items','align-self','justify-content','flex-direction','flex-wrap','flex-grow','flex-shrink','gap','width','height','min-width','min-height','max-width','max-height','position','top','right','bottom','left','transform'];
 var parcelProps = ['flex-basis','object-fit','overflow-x','overflow-y','text-overflow','-webkit-line-clamp','-webkit-box-orient','grid-template-columns','grid-template-rows','grid-column','grid-row','column-gap','row-gap'];
 var responsiveSize = /^(?:width|height|min-width|min-height|max-width|max-height|flex-basis|grid-template-columns|grid-template-rows)$/;
 function isDark() {
  if (darkOverride !== null) return darkOverride;
  if (typeof window.__HC_DARK__ === 'boolean') return window.__HC_DARK__;
  var attr = document.documentElement && document.documentElement.getAttribute('data-hc-theme');
  if (attr === 'dark') return true;
  if (attr === 'light') return false;
  return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
 }
 function parseRgb(val) {
  if (!val || val === 'transparent' || val === 'none') return null;
  var m = val.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([0-9.]+))?/i);
  if (m) {
   if (m[4] !== undefined && parseFloat(m[4]) === 0) return null;
   return [+m[1], +m[2], +m[3]];
  }
  var h = val.match(/#([0-9a-f]{3,8})\b/i);
  if (h) {
   var s = h[1];
   if (s.length === 3 || s.length === 4) return [parseInt(s[0]+s[0],16), parseInt(s[1]+s[1],16), parseInt(s[2]+s[2],16)];
   if (s.length >= 6) return [parseInt(s.slice(0,2),16), parseInt(s.slice(2,4),16), parseInt(s.slice(4,6),16)];
  }
  return null;
 }
 function adaptTree(root, dark) {
  if (!root) return;
  var walker = document.createTreeWalker(root, 1), node = root;
  while (node) {
   var rec = savedStyles.get(node);
   if (!rec) {
    rec = {
     color: node.style.getPropertyValue('color'),
     bg: node.style.getPropertyValue('background-color'),
     bgImg: node.style.getPropertyValue('background-image')
    };
    savedStyles.set(node, rec);
   }
   if (dark) {
    if (!node.classList || (!node.classList.contains('hc-pdd-barcode') && !node.classList.contains('hc-pdd-qr'))) {
     var bg = parseRgb(rec.bg);
     if (bg && bg[0] > 180 && bg[1] > 180 && bg[2] > 180) {
      node.style.setProperty('background-color', bg[0] < 242 ? 'rgba(255,255,255,0.06)' : 'transparent', 'important');
     }
     if (rec.bgImg && /gradient/i.test(rec.bgImg)) {
      node.style.setProperty('background-image', 'none', 'important');
     }
     var fg = parseRgb(rec.color);
     if (fg) {
      if (fg[0] < 105 && fg[1] < 105 && fg[2] < 120) node.style.setProperty('color', '#e6eaf2', 'important');
      else if (fg[0] < 175 && fg[1] < 175 && fg[2] < 185) node.style.setProperty('color', '#9aa4b5', 'important');
     }
    }
   } else {
    if (rec.color) node.style.setProperty('color', rec.color, 'important'); else node.style.removeProperty('color');
    if (rec.bg) node.style.setProperty('background-color', rec.bg, 'important'); else node.style.removeProperty('background-color');
    if (rec.bgImg) node.style.setProperty('background-image', rec.bgImg, 'important'); else node.style.removeProperty('background-image');
   }
   node = walker.nextNode();
  }
 }
 function setDark(dark) {
  darkOverride = !!dark;
  window.__HC_DARK__ = darkOverride;
  var bg = darkOverride ? '#0f1218' : '#f5f6f8';
  if (document.documentElement) {
   document.documentElement.setAttribute('data-hc-theme', darkOverride ? 'dark' : 'light');
   document.documentElement.style.setProperty('background', bg, 'important');
   document.documentElement.style.setProperty('background-color', bg, 'important');
  }
  if (document.body) {
   document.body.style.setProperty('background', bg, 'important');
   document.body.style.setProperty('background-color', bg, 'important');
  }
  ['identity', 'parcels'].forEach(function (part) {
   var mount = document.getElementById('hc-pdd-' + part);
   if (mount && mount.firstElementChild) adaptTree(mount.firstElementChild, darkOverride);
  });
  return darkOverride;
 }
 window.HandyCodeSetDark = setDark;
 function phase(value) { if (lastPhase !== value) { lastPhase = value; console.info('__HANDYCODE_PDD_LAYOUT__:' + value); } }
 function css(node, values) { Object.keys(values).forEach(function (key) { node.style.setProperty(key, values[key], 'important'); }); }
 function placeholder(identity) {
  return identity
   ? '<div class="hc-pdd-placeholder hc-pdd-identity-placeholder" aria-label="正在加载身份码"><i></i><i></i><i></i></div>'
   : '<div class="hc-pdd-placeholder hc-pdd-parcels-placeholder" aria-label="正在加载待取包裹"><i></i><i></i><i></i></div>';
 }
 function hideParcelExtra(node) {
  if (node.matches('.package-location-phone,.package-item-fill,.package-picture-check,.package-item-operate,.package-pick-disable-tip,.goods-return-tag')) return true;
  if (node.matches('.package-tags') && node.childElementCount > 0 &&
      Array.from(node.children).every(function (tag) { return tag.matches('.goods-return-tag'); })) return true;
  // The menu is the icon-only sibling of the label and value, not the pickup code itself.
  return node.matches('.package-code > div') && !node.textContent.trim() &&
    node.childElementCount === 1 && node.firstElementChild.tagName === 'IMG';
 }
 function spaceParcelRow(source, copy) {
  if (source.matches('.package-name-address-phone-info,.package-name-address,.package-items,[class*="package-item-wrap__"],[class*="package-item__"],.package-item-details-wrapper')) {
   css(copy, {display:'block',width:'100%',height:'auto','min-width':'0','min-height':'0',margin:'0',padding:'0'});
  }
  if (source.matches('.package-name-address,.package-items')) {
   css(copy, {display:'grid',gap:source.matches('.package-items')?'clamp(8px, 2.5vw, 12px)':'2px'});
  }
  if (source.matches('.package-name-address-phone-info')) {
   copy.className = 'hc-pdd-station-heading';
   css(copy, {position:'relative','padding-left':'22px'});
  }
  if (source.matches('.package-items')) css(copy, {'padding-left':'22px'});
  if (source.matches('.package-item-details')) {
   css(copy, {display:'grid','grid-template-columns':'clamp(36px, 14vw, 51px) minmax(0, 1fr)',
    'align-items':'center',gap:'clamp(4px, 1.25vw, 6px)',width:'100%',height:'auto','min-height':'0',margin:'0',padding:'0'});
  }
  if (source.matches('.package-picture')) {
   css(copy, {width:'100%',height:'auto','aspect-ratio':'1','min-width':'0','min-height':'0',margin:'0'});
  }
  if (source.matches('.package-picture > img')) {
   css(copy, {display:'block',width:'100%',height:'auto','aspect-ratio':'1','object-fit':'contain','border-radius':'6px'});
  }
  if (source.matches('.package-desc')) {
   // The official flex container centers its children. Give the projected
   // grid a full-width text track so visible text starts beside the photo.
   css(copy, {display:'grid','grid-template-columns':'minmax(0, 1fr)','justify-content':'start',
    'justify-items':'stretch','text-align':'start',gap:'2px',width:'auto',height:'auto',
    'min-width':'0','min-height':'0',margin:'0',padding:'0'});
  }
  if (source.matches('.name-container,.address-desc,.package-code,.package-code > div,.package-shipping-name,.package-shipping-name-text')) {
   css(copy, {display:'block',width:'auto','max-width':'100%',height:'auto','min-width':'0','min-height':'0',
    margin:'0',padding:'0','white-space':'normal','overflow-wrap':'anywhere','text-overflow':'clip','-webkit-line-clamp':'unset'});
  }
  if (source.matches('.station-name,.package-code-label,.package-code-value,.package-shipping-name-text span')) {
   css(copy, {'white-space':'normal','overflow-wrap':'anywhere'});
  }
  if (source.matches('.station-name')) {
   css(copy, {color:'#11192d','font-family':'"PingFang SC", -apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, "Segoe UI", Arial, Roboto, sans-serif',
    'font-size':'12px','font-weight':'600','line-height':'1.2'});
  }
  if (source.matches('.package-code-value')) {
   css(copy, {color:'#11192d','font-family':'system-ui, sans-serif','font-size':'19.5px',
    'font-weight':'400','line-height':'1.1','margin-left':'0'});
  }
  if (source.matches('.package-code-label') && source.parentElement.querySelector('.package-code-value')) {
   css(copy, {position:'absolute',width:'1px',height:'1px',padding:'0',margin:'-1px',
    overflow:'hidden','clip-path':'inset(50%)','white-space':'nowrap'});
  }
  if (source.matches('.address-desc,.package-shipping-name-text,.package-shipping-name-text span')) {
   css(copy, {color:'#5f6878','font-family':'"PingFang SC", sans-serif','font-size':'10.2px',
    'font-weight':'400','line-height':'1.25'});
  }
  if (source.matches('.package-shipping-name-text')) {
   css(copy, {display:'flex','flex-wrap':'wrap','align-items':'baseline','justify-content':'start',gap:'2px 6px'});
  }
  if (source.matches('.package-shipping-name-text > *')) {
   css(copy, {display:'block',flex:'0 1 auto',width:'auto','min-width':'0','max-width':'100%',
    margin:'0','white-space':'normal','overflow-wrap':'anywhere'});
  }
 }
 function clone(node, budget, parcel) {
  if (--budget.count < 0) throw new Error('projection-too-large');
  if (node.nodeType === 3) return document.createTextNode(node.nodeValue);
  if (node.nodeType !== 1 || /^(SCRIPT|STYLE|LINK|IFRAME|FORM|INPUT|TEXTAREA|SELECT|OBJECT|EMBED|NOSCRIPT)$/.test(node.tagName)) return null;
  if (parcel && hideParcelExtra(node)) return null;
  var computed = getComputedStyle(node);
  if (computed.display === 'none' || computed.visibility === 'hidden') return null;
  var copy = node.cloneNode(false);
  Array.from(copy.attributes).forEach(function (attribute) {
   if (/^on/i.test(attribute.name) || /^(srcdoc|formaction|id|class)$/i.test(attribute.name)) copy.removeAttribute(attribute.name);
  });
  if (copy.tagName === 'A') { copy.removeAttribute('href'); copy.removeAttribute('target'); }
  if (copy.tagName === 'IMG' && node.currentSrc) copy.src = node.currentSrc;
  var sizing = parcel && node.computedStyleMap && node.computedStyleMap();
  (parcel ? props.concat(parcelProps) : props).forEach(function (name) {
   var original = sizing && responsiveSize.test(name) && sizing.get(name);
   copy.style.setProperty(name, original ? String(original) : computed.getPropertyValue(name), 'important');
  });
  css(copy, {'touch-action':'pan-y'});
  if (!parcel) css(copy, {'max-width':'100%','box-sizing':'border-box','overflow':'visible'});
  if (/^(fixed|sticky)$/.test(computed.position) || /auto|scroll/.test(computed.overflowY)) css(copy, {position:'static',height:'auto','max-height':'none',transform:'none',overflow:'visible'});
  if (parcel) {
   spaceParcelRow(node, copy);
   if (copy.tagName === 'IMG' && computed.objectFit === 'fill') css(copy, {'object-fit':'contain'});
  }
  Array.from(node.childNodes).forEach(function (child) { var result = clone(child, budget, parcel); if (result) copy.appendChild(result); });
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
 function identityRoot() { return source.querySelector(identitySelector); }
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
  if (failedRefresh[part] && failedRefresh[part] === signature) return false;
  delete failedRefresh[part]; return true;
 }
 function changePhoneControl(root) {
  if (!root) return null;
  return Array.from(root.querySelectorAll('span,button,a')).find(function (control) {
   return control.textContent.trim() === '更换';
  });
 }
 function updateIdentity() {
  if (refreshes.identity && refreshes.identity.loading) return false;
  var root = identityRoot(), mount = document.getElementById('hc-pdd-identity');
  var signature = root ? root.outerHTML : 'missing';
  if (!canUpdate('identity', signature)) return false;
  var manage = shell.querySelector('.hc-pdd-phone-manage');
  if (!identityReady(root)) {
   manage.hidden = true;
   if (mount.dataset.ready) {
    HandyPickupMotion.reset(mount);
    delete mount.dataset.ready; delete signatures.identity;
    mount.innerHTML = placeholder(true);
   }
   return false;
  }
  var phoneRoot = root.querySelector('.identification-code-mobile-info');
  var change = changePhoneControl(phoneRoot);
  manage.hidden = !change;
  if (change) links.set(manage, change);
  if (signatures.identity === signature && mount.dataset.ready) return true;
  var content = document.createElement('div');
  content.className = 'hc-pdd-code';
  var phone = copyPart(phoneRoot, 'hc-pdd-phone');
  if (phone) {
   var projectedChange = changePhoneControl(phone);
   if (projectedChange) projectedChange.remove();
   phone.textContent = phone.textContent.replace(/^\s*已绑定常用手机号\s*[:：]\s*/, '').trim();
   content.appendChild(phone);
  }
  var image = root.querySelector('.identification-code-img');
  if (image) {
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
  var tips = copyPart(root.querySelector('.identification-code-tips'), 'hc-pdd-tips');
  if (tips) {
   [tips].concat(Array.from(tips.querySelectorAll('*'))).forEach(function (node) {
    css(node, {'font-size':'12px','line-height':'1.45'});
   });
   content.appendChild(tips);
  }
  if (isDark()) adaptTree(content, true);
  HandyPickupMotion.replace(mount, content); signatures.identity = signature;
  return true;
 }
 function updatePending() {
  if (refreshes.parcels && refreshes.parcels.loading) return false;
  var roots = pendingRoots(), mount = document.getElementById('hc-pdd-parcels');
  var signature = roots.map(function (node) { return node.outerHTML; }).join('') || 'missing';
  if (!canUpdate('parcels', signature)) return false;
  if (signatures.parcels === signature && mount.dataset.ready) return true;
  if (!roots.length || !roots.some(function (node) { return node.textContent.trim(); })) return false;
  var content = document.createElement('div');
  roots.forEach(function (node) {
   var copy = clone(node, {count:6000}, true);
   if (!copy) return;
   css(copy, {position:'static',transform:'none',width:'100%',height:'auto','min-height':'0','max-height':'none',margin:'0','box-sizing':'border-box'});
   if (node.matches('.package-list-picking')) {
    content.className = 'hc-pdd-parcel-groups';
    copy.className = 'hc-pdd-parcel-group';
    css(copy, {display:'grid',position:'relative',gap:'clamp(6px, 2vw, 10px)',padding:'0'});
   }
   content.appendChild(copy);
  });
  if (!content.childElementCount) return false;
  if (isDark()) adaptTree(content, true);
  HandyPickupMotion.replace(mount, content); signatures.parcels = signature;
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
 function update(parts) {
  if (!shell || sourceMode) return;
  if (location.pathname !== '/mdkd/package') {
   if (!openingPhoneManagement || !identityRoot()) workflow(true);
   return;
  }
  if (parts === undefined) parts = BOTH;
  try {
   var identity = parts & IDENTITY ? updateIdentity() : document.getElementById('hc-pdd-identity').dataset.ready;
   var parcels = parts & PARCELS ? updatePending() : document.getElementById('hc-pdd-parcels').dataset.ready;
   if (!authenticated && (identity || parcels)) {
    authenticated = true;
    console.info('__HANDYCODE_PDD_AUTH__:signed-in');
   }
   if (identity && !parcels && !triedPackage && Date.now() - started > 1500) {
    var tab = sourceTab('取快递');
    if (tab) {
     triedPackage = true; tab.click();
     setTimeout(function () { var id = sourceTab('身份码'); if (id) id.click(); queue(BOTH); }, 250);
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
 function queue(parts) {
  pendingParts |= parts;
  if (!pendingParts || projectionTimer !== null || touching || !active || document.hidden || sourceMode) return;
  projectionTimer = setTimeout(function () {
   projectionTimer = null;
   if (touching || !active || document.hidden || sourceMode) return;
   if (Date.now() - lastTouch < 800) { queue(0); return; }
   var work = pendingParts;
   pendingParts = 0;
   update(work);
  }, Math.max(160, 800 - (Date.now() - lastTouch)));
 }
 function affectedParts(node, subtree) {
  var el = node.nodeType === 1 ? node : node.parentElement;
  if (!el) return 0;
  var identity = el.closest(identitySelector) || subtree && el.querySelector(identitySelector);
  var parcels = el.closest(parcelsSelector) || subtree && el.querySelector(parcelsSelector);
  return (identity ? IDENTITY : 0) | (parcels ? PARCELS : 0);
 }
 function sourceChanged(records) {
  if (openingPhoneManagement && location.pathname !== '/mdkd/package' && !identityRoot()) {
   workflow(true);
   return;
  }
  var parts = 0;
  for (var i = 0; i < records.length && parts !== BOTH; i++) {
   var record = records[i];
   parts |= affectedParts(record.target, false);
   if (record.type === 'childList') {
    Array.from(record.addedNodes).concat(Array.from(record.removedNodes)).forEach(function (node) {
     parts |= affectedParts(node, true);
    });
   }
  }
  queue(parts);
 }
 function observeSource() {
  sourceObserver.observe(source, {subtree:true,childList:true,characterData:true,attributes:true});
 }
 function checkMissingCards() {
  var missing = document.getElementById('hc-pdd-identity').dataset.ready ? 0 : IDENTITY;
  if (!document.getElementById('hc-pdd-parcels').dataset.ready) missing |= PARCELS;
  if (missing) queue(missing);
 }
 function setActive(value) {
  if (active === value) return;
  active = value;
  if (!sourceObserver) return;
  if (active) {
   observeSource();
   queue(BOTH);
  } else {
   sourceObserver.disconnect();
   clearTimeout(projectionTimer);
   projectionTimer = null;
   touching = false;
  }
 }
 function refresh(part) {
  if (refreshes[part] || !shell || sourceMode) return;
  var mount = document.getElementById('hc-pdd-' + part);
  var button = shell.querySelector('[data-hc-pdd-refresh="' + part + '"]');
  if (!mount || !button) return;
  var previous = part === 'parcels' && mount.dataset.ready ? mount.firstElementChild : null;
  var job = {loading:true}, originalHeight = mount.getBoundingClientRect().height;
  HandyPickupMotion.reset(mount);
  refreshes[part] = job; delete failedRefresh[part];
  button.disabled = true; button.textContent = '刷新中';
  if (originalHeight > 0) mount.style.height = originalHeight + 'px';
  delete mount.dataset.ready; delete signatures[part];
  mount.innerHTML = placeholder(part === 'identity');
  function finish(error) {
   if (refreshes[part] !== job) return;
   delete refreshes[part]; button.disabled = false; button.textContent = error ? '重试' : '刷新';
   if (error) {
    if (part === 'identity') {
     var root = identityRoot();
     failedRefresh[part] = root ? root.outerHTML : 'missing';
    } else {
     failedRefresh[part] = pendingRoots().map(function (node) { return node.outerHTML; }).join('') || 'missing';
     mount.style.height = '';
    }
    if (previous) {
     mount.replaceChildren(previous);
     mount.dataset.ready = 'true';
     var notice = document.createElement('p');
     notice.className = 'hc-pdd-wait hc-pdd-refresh-notice';
     notice.textContent = '刷新未完成，当前显示上次结果。';
     mount.appendChild(notice);
    } else {
     mount.innerHTML = '<p class="hc-pdd-wait">刷新未完成，请点本卡片右上角重试。</p>';
    }
   }
  }
  var service = window.HandyPddRefresh;
  Promise.resolve().then(function () {
   if (!service) throw new Error('refresh-unavailable');
   return service.run(part);
  }).then(function () {
   job.loading = false;
   var until = Date.now() + 1500;
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
  openingPhoneManagement = false;
  sourceMode = show;
  document.documentElement.classList.toggle('hc-pdd-source-mode', show);
  shell.hidden = show; backBar.hidden = !show;
  phase(show ? 'source' : 'cards');
  if (!show) queue(BOTH);
 }
 function back() {
  if (!sourceMode) return false;
  if (location.pathname !== '/mdkd/package') { history.back(); return true; }
  workflow(false); return true;
 }
 function clicked(event) {
  var node = event.target, original;
  if (node.closest('.hc-pdd-parcel-groups')) { event.preventDefault(); return; }
  if (node.closest('#hc-pdd-identity') && !node.closest('.hc-pdd-tips')) return;
  while (node && node !== shell && !(original = links.get(node))) node = node.parentNode;
  if (!original || !original.isConnected) return;
  event.preventDefault();
  if (node.matches('.hc-pdd-phone-manage')) {
   // Keep the combined page visible until the official route is ready.
   openingPhoneManagement = true;
   original.click(); queue(BOTH);
   return;
  }
  // Switch-code controls update in place. Other official actions temporarily
  // reveal the original page so its forms and dialogs remain usable.
  if (!original.closest('.identification-code-tips')) workflow(true);
  original.click(); queue(affectedParts(original, false));
 }
 function boot() {
  if (shell) return;
  source = document.getElementById('__next');
  if (!source || !document.body) {
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
html{height:auto!important;min-height:100vh!important;background:#f5f6f8!important;overflow-x:hidden!important;overflow-y:auto!important}
body{display:block!important;height:auto!important;min-height:100vh!important;margin:0!important;padding:12px 10px 24px!important;background:#f5f6f8!important;overflow-x:hidden!important;overflow-y:auto!important;transition:background-color .2s}
html:not(.hc-pdd-source-mode) body>:not(#hc-pdd-shell):not(#hc-pdd-back):not([data-hc-pdd-source]):not(script):not(style){display:none!important}
[data-hc-pdd-source]{position:fixed!important;left:-100000px!important;top:0!important;width:100vw!important;height:100vh!important;transform:translateZ(0)!important;pointer-events:none!important}
#hc-pdd-shell{--hc-action-ink:#536071;color:#242b37;font:14px/1.5 system-ui,sans-serif;overflow:visible;touch-action:pan-y}
#hc-pdd-shell *{box-sizing:border-box}
.hc-pdd-card{margin-bottom:14px;border:1px solid #e9ecf0;border-radius:16px;background:#fff;overflow:visible;transition:background-color .2s,border-color .2s}
.hc-pdd-heading{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #f0f2f5}
.hc-pdd-heading h2{margin:0;font-size:16px;font-weight:650}
.hc-pdd-heading button{border:0;background:none;color:var(--hc-action-ink);padding:5px 0;font:inherit;font-size:12px}
.hc-pdd-heading button:disabled{color:#8a929f}
.hc-pdd-heading-actions{display:flex;align-items:center;gap:12px;white-space:nowrap}
.hc-pdd-phone-manage:not([hidden]) + button{position:relative}
.hc-pdd-phone-manage:not([hidden]) + button::before{content:'';position:absolute;left:-6px;top:50%;height:10px;border-left:1px solid #e1e5eb;transform:translateY(-50%)}
.hc-pdd-content{position:relative;padding:16px 10px;min-width:0;overflow:visible}
#hc-pdd-identity{padding:10px}
#hc-pdd-parcels{min-height:140px;padding:10px}
.hc-pdd-parcel-groups{display:grid;gap:clamp(18px, 6vw, 28px);padding:8px clamp(6px, 3.5vw, 14px);min-width:0}
.hc-pdd-parcel-group{min-width:0}
.hc-pdd-parcel-group::before{content:'';position:absolute;left:7px;top:18px;bottom:6px;border-left:1px solid var(--hc-parcel-guide,#e9ecf0);pointer-events:none}
.hc-pdd-station-heading::before{content:'';position:absolute;left:0;top:0;width:15px;height:16px;background-color:var(--hc-station-ink,#242b37);-webkit-mask:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='none' stroke='black' stroke-width='1.3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M2 7v6.5h12V7M1 6l2-3.5h10L15 6v1c-1.1 1.1-2.4 1.1-3.5 0C10.4 8.1 9.1 8.1 8 7 6.9 8.1 5.6 8.1 4.5 7 3.4 8.1 2.1 8.1 1 7z'/%3E%3C/svg%3E") center/14px no-repeat;pointer-events:none}
.hc-pdd-content[data-ready] > div{opacity:1}
.hc-pdd-code{display:flex;flex-direction:column;align-items:center;gap:7px;padding:2px 8px}
.hc-pdd-phone{font-size:14px!important;text-align:center!important}
.hc-pdd-barcode{display:block!important;width:100%!important;max-width:100%!important;height:100px!important;object-fit:contain;transform:none!important}
.hc-pdd-placeholder{display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:10px 8px;overflow:hidden}
.hc-pdd-identity-placeholder{height:100%;justify-content:center;gap:12px;padding:0 8px}
.hc-pdd-parcels-placeholder{height:100%;justify-content:center;gap:10px}
.hc-pdd-placeholder i{position:relative;display:block;flex-shrink:0;width:70%;height:14px;border-radius:7px;overflow:hidden;background:#edf0f4}
.hc-pdd-identity-placeholder i:first-child{height:16px;width:32%}
.hc-pdd-identity-placeholder i:nth-child(2){height:100px;width:100%}
.hc-pdd-identity-placeholder i{flex-shrink:1}
.hc-pdd-placeholder i::after{position:absolute;inset:0;content:'';background:linear-gradient(90deg,transparent,rgba(255,255,255,.7),transparent);transform:translateX(-100%);animation:hc-pdd-shimmer 1.4s linear infinite;will-change:transform}
.hc-pdd-parcels-placeholder i{flex-shrink:1;width:94%}
.hc-pdd-parcels-placeholder i:last-child{height:22px;width:78%}
@keyframes hc-pdd-shimmer{to{transform:translateX(100%)}}
@media(prefers-reduced-motion:reduce){.hc-pdd-placeholder i::after{animation:none}}
.hc-pdd-wait{padding:20px 8px;color:#89919d;text-align:center;font-size:12px}
.hc-pdd-refresh-notice{margin:8px 0 0;padding:4px 8px;color:#5f6878}
#hc-pdd-back{position:fixed;left:0;right:0;top:0;height:44px;z-index:2147483647;background:#fff;border-bottom:1px solid #e9ecf0;display:flex;align-items:center;padding:0 14px}
#hc-pdd-back button{background:none;border:0;color:#536071;font:14px system-ui,sans-serif}
html[data-hc-theme="dark"],html[data-hc-theme="dark"] body,html[data-hc-theme="dark"] #hc-pdd-shell{background:#0f1218!important;background-color:#0f1218!important;color-scheme:dark}
html[data-hc-theme="dark"] #hc-pdd-shell{color:#e6eaf2}
html[data-hc-theme="dark"] .hc-pdd-card{--hc-card-surface:#181c24;--hc-parcel-guide:#465164;--hc-station-ink:#b3b3b3;background:#181c24;border-color:#262c36}
html[data-hc-theme="dark"] .hc-pdd-heading{border-bottom-color:#232833;color:#e6eaf2}
html[data-hc-theme="dark"] .hc-pdd-heading button,html[data-hc-theme="dark"] #hc-pdd-back button{color:#ff855e}
html[data-hc-theme="dark"] .hc-pdd-barcode,html[data-hc-theme="dark"] .hc-pdd-qr{background:#fff!important;padding:8px 10px!important;border-radius:10px!important}
html[data-hc-theme="dark"] .hc-pdd-placeholder i{background:#232936}
html[data-hc-theme="dark"] .hc-pdd-placeholder i::after{background:linear-gradient(90deg,transparent,rgba(255,255,255,.15),transparent)}
html[data-hc-theme="dark"] .hc-pdd-wait,html[data-hc-theme="dark"] .hc-pdd-warning,html[data-hc-theme="dark"] .hc-pdd-tips{color:#8e98aa!important}
html[data-hc-theme="dark"] #hc-pdd-back{background:#181c24;border-bottom-color:#262c36}
@media(prefers-color-scheme:dark){
html:not([data-hc-theme="light"]),html:not([data-hc-theme="light"]) body,html:not([data-hc-theme="light"]) #hc-pdd-shell,html:not([data-hc-theme="light"]).hc-pdd-source-mode [data-hc-pdd-source],html:not([data-hc-theme="light"]).hc-pdd-source-mode [data-hc-pdd-source] [class*="container"]{background:#0f1218!important;background-color:#0f1218!important;color-scheme:dark}
html:not([data-hc-theme="light"]) #hc-pdd-shell{color:#e6eaf2}
html:not([data-hc-theme="light"]) .hc-pdd-card{--hc-card-surface:#181c24;--hc-parcel-guide:#465164;--hc-station-ink:#b3b3b3;background:#181c24;border-color:#262c36}
html:not([data-hc-theme="light"]) .hc-pdd-heading{border-bottom-color:#232833;color:#e6eaf2}
html:not([data-hc-theme="light"]) .hc-pdd-heading button,html:not([data-hc-theme="light"]) #hc-pdd-back button{color:#ff855e}
html:not([data-hc-theme="light"]) .hc-pdd-barcode,html:not([data-hc-theme="light"]) .hc-pdd-qr{background:#fff!important;padding:8px 10px!important;border-radius:10px!important}
html:not([data-hc-theme="light"]) .hc-pdd-placeholder i{background:#232936}
html:not([data-hc-theme="light"]) .hc-pdd-placeholder i::after{background:linear-gradient(90deg,transparent,rgba(255,255,255,.15),transparent)}
html:not([data-hc-theme="light"]) .hc-pdd-wait,html:not([data-hc-theme="light"]) .hc-pdd-warning,html:not([data-hc-theme="light"]) .hc-pdd-tips{color:#8e98aa!important}
html:not([data-hc-theme="light"]) #hc-pdd-back{background:#181c24;border-bottom-color:#262c36}
}
#hc-pdd-shell[hidden],#hc-pdd-back[hidden]{display:none!important}
html.hc-pdd-source-mode,html.hc-pdd-source-mode body{overflow:hidden!important}
.hc-pdd-source-mode [data-hc-pdd-source]{left:0!important;top:44px!important;height:calc(100vh - 44px)!important;pointer-events:auto!important}
`;
  document.head.appendChild(style);
  shell = document.createElement('main'); shell.id = 'hc-pdd-shell';
  shell.innerHTML = '<section class="hc-pdd-card"><div class="hc-pdd-heading"><h2>身份码</h2><button type="button" data-hc-pdd-refresh="identity">刷新</button></div><div class="hc-pdd-content" id="hc-pdd-identity">' + placeholder(true) + '</div></section><section class="hc-pdd-card"><div class="hc-pdd-heading"><h2>待取包裹</h2><div class="hc-pdd-heading-actions"><button type="button" class="hc-pdd-phone-manage" hidden>添加手机号</button><button type="button" data-hc-pdd-refresh="parcels">刷新</button></div></div><div class="hc-pdd-content" id="hc-pdd-parcels">' + placeholder(false) + '</div></section>';
  document.body.appendChild(shell);
  document.getElementById('hc-pdd-parcels').style.height = '140px';
  HandyPickupMotion.bindZoom(document.getElementById('hc-pdd-identity'), '.hc-pdd-barcode,.hc-pdd-qr');
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
  function endTouch() { touching = false; lastTouch = Date.now(); queue(0); }
  shell.addEventListener('touchend', endTouch, {passive:true});
  shell.addEventListener('touchcancel', endTouch, {passive:true});
  sourceObserver = new MutationObserver(sourceChanged);
  if (active) observeSource();
  source.addEventListener('load', function (event) { queue(affectedParts(event.target, false)); }, true);
  window.addEventListener('popstate', function () {
   if (sourceMode && location.pathname === '/mdkd/package') workflow(false);
  });
  // Keep the initial lazy-tab and timeout checks independent of the slow fallback.
  var lazyCheck = setTimeout(checkMissingCards, 1600);
  var startupCheck = setTimeout(checkMissingCards, 15100);
  var timer = setInterval(function () { if (active && !document.hidden && !sourceMode) queue(BOTH); }, 20000);
  document.addEventListener('visibilitychange', function () {
   if (document.hidden) shell.querySelectorAll('.hc-pdd-content').forEach(HandyPickupMotion.cancel);
   else queue(BOTH);
  });
  window.addEventListener('pagehide', function () {
   sourceObserver.disconnect(); clearInterval(timer); clearTimeout(lazyCheck); clearTimeout(startupCheck);
   clearTimeout(projectionTimer);
  });
  setDark(isDark());
  phase('loading'); update();
 }
 window.HandyPddLayout = {update:update, back:back, setActive:setActive, setDark:setDark};
 if (document.body) boot(); else document.addEventListener('DOMContentLoaded', boot, {once:true});
})();
