;(function (global) {
 'use strict';
 if (global.HandyPickupMotion) return;
 var transitions = new WeakMap(), zooms = new WeakMap();
 function reduced() { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; }
 function cancel(mount) {
  var running = transitions.get(mount);
  if (!running) return;
  transitions.delete(mount);
  running.forEach(function (animation) { animation.cancel(); });
  var ghost = mount.querySelector('.hc-pickup-outgoing');
  if (ghost) ghost.remove();
 }
 function replace(mount, content) {
  var before = mount.getBoundingClientRect().height;
  var placeholder = mount.firstElementChild;
  var leaving = placeholder && placeholder.matches('.hc-section-placeholder,.hc-pdd-placeholder');
  var placeholderHeight = leaving ? placeholder.getBoundingClientRect().height : 0;
  var zoom = zooms.get(mount);
  if (zoom) zoom.detach(true);
  cancel(mount);
  mount.style.height = '';
  mount.replaceChildren(content);
  mount.dataset.ready = 'true';
  if (zoom) zoom.attach(content);
  var after = mount.getBoundingClientRect().height;
  if (document.hidden || reduced()) return;
  var running = [];
  function animate(node, frames, options, done) {
   if (!node.animate) return false;
   var motion = node.animate(frames, options);
   if (!motion || typeof motion.cancel !== 'function') return;
   running.push(motion);
   motion.onfinish = motion.oncancel = function () {
    if (done) done();
    var index = running.indexOf(motion);
    if (index >= 0) running.splice(index, 1);
    if (!running.length && transitions.get(mount) === running) transitions.delete(mount);
   };
   return true;
  }
  if (leaving && content.animate) {
   // The old skeleton is decorative and leaves over the real content.
   placeholder.classList.add('hc-pickup-outgoing');
   placeholder.setAttribute('aria-hidden', 'true');
   placeholder.style.cssText += ';position:absolute;top:10px;left:10px;right:10px;height:' + placeholderHeight +
    'px;pointer-events:none;background:white';
   mount.appendChild(placeholder);
   if (!animate(placeholder, [{opacity:1},{opacity:0}], {duration:220,easing:'ease-out'}, function () { placeholder.remove(); })) placeholder.remove();
   animate(content, [{opacity:0},{opacity:1}], {duration:280,easing:'ease-out'});
  }
  if (mount.animate && before > 0 && Math.abs(after - before) > 1) {
   animate(mount, [{height:before+'px',clipPath:'inset(0)'},{height:after+'px',clipPath:'inset(0)'}],
    {duration:300,easing:'cubic-bezier(.25,.65,.25,1)'});
  }
  if (running.length) transitions.set(mount, running);
 }
 function bindZoom(mount, imageSelector) {
  var node, image, progress = 0, expanded = false, frame;
  function draw(value) {
   progress = value;
   if (!node) return;
   if (value === 0) {
    node.style.transform = '';
    mount.style.height = '';
    mount.style.overflow = '';
    return;
   }
   var style = window.getComputedStyle(mount);
   var padding = parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
   var width = node.offsetWidth, height = node.offsetHeight;
   if (!width || !height) return;
   // The WebView ends at the native tab bar. Leave room for the gap
   // between cards so the next card starts at that edge when expanded.
   var bounds = mount.getBoundingClientRect();
   var next = mount.parentElement.nextElementSibling;
   var gap = next ? next.getBoundingClientRect().top - bounds.bottom : 0;
   var available = Math.max(height, window.innerHeight - bounds.top - gap - padding);
   var expandedScale = Math.min(width / height, available / width);
   var angle = Math.PI * value / 2, sine = Math.sin(angle), cosine = Math.cos(angle);
   var rotatedHeight = width * sine + height * cosine;
   var scale = Math.min(1 + value * (expandedScale - 1),
    width / (width * cosine + height * sine), available / rotatedHeight);
   var box = Math.min(available, scale * rotatedHeight + value * (available - width * expandedScale));
   mount.style.height = box + padding + 'px';
   mount.style.overflow = 'clip';
   node.style.setProperty('transform', 'translateY(' + ((box - height) / 2) + 'px) rotate(' +
    (90 * value) + 'deg) scale(' + scale + ')', 'important');
  }
  function stop() { if (frame) window.cancelAnimationFrame(frame); frame = null; }
  function updateImage() {
   if (!image) return;
   image.setAttribute('aria-label', expanded ? '还原身份码' : '放大身份码');
   image.setAttribute('aria-expanded', String(expanded));
  }
  function attach(content) {
   node = content;
   image = content.querySelector(imageSelector);
   if (image) { image.setAttribute('role', 'button'); image.setAttribute('tabindex', '0'); }
   updateImage();
   if (expanded) draw(1);
  }
  function detach(keepExpanded) {
   stop();
   if (node && progress) draw(0);
   node = image = null;
   if (!keepExpanded) expanded = false;
  }
  zooms.set(mount, {attach:attach,detach:detach});
  function toggle() {
   if (!image) return;
   cancel(mount); stop();
   expanded = !expanded; updateImage();
   var from = progress, to = expanded ? 1 : 0, started;
   if (reduced()) { draw(to); return; }
   function step(now) {
    if (started === undefined) started = now;
    var t = Math.min(1, (now - started) / 360);
    draw(from + (to - from) * (t * t * (3 - 2 * t)));
    frame = t < 1 ? window.requestAnimationFrame(step) : null;
   }
   frame = window.requestAnimationFrame(step);
  }
  mount.addEventListener('click', function (event) {
   if (node && node.contains(event.target) && !event.target.closest('.hc-pdd-tips,a,button')) toggle();
  });
  mount.addEventListener('keydown', function (event) {
   if (event.target === image && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); toggle(); }
  });
  window.addEventListener('resize', function () { if (progress) draw(progress); });
 }
 function reset(mount) {
  cancel(mount);
  var zoom = zooms.get(mount);
  if (zoom) zoom.detach(false);
 }
 global.HandyPickupMotion = {replace:replace,bindZoom:bindZoom,reset:reset,cancel:cancel};
})(globalThis);
