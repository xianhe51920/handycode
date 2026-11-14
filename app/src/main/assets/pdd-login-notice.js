;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://m.pinduoduo.net' || location.pathname !== '/login.html') return;
 if (window.HandyPddLoginNotice) {window.HandyPddLoginNotice.update();return;}
 var queued=false;
 var message='拼多多网页登录与官方 App 登录可能互相挤下线。建议使用小号登录，再到「待取包裹 → 添加手机号」验证并添加大号的收件手机号，即可查看大号包裹。';
 var css=`
#hc-pdd-login-notice-style {display:none}
#login .main.hc-pdd-login-surface {height:auto!important;min-height:100%}
#login .main.hc-pdd-login-surface .login-footer {height:auto!important;min-height:0}
#login .container.hc-pdd-login-surface {overflow-y:auto!important}
.hc-pdd-login-notice {display:block;box-sizing:border-box;position:relative;margin:20px auto 24px;padding:0 24px;max-width:540px;font:400 12px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;text-align:left;color:#747b86;white-space:normal;word-break:normal;overflow-wrap:anywhere}
html[data-hc-theme="dark"] .hc-pdd-login-notice {color:#a7b0be}
`;
 function update() {
  queued=false;
  if (!document.documentElement || location.pathname !== '/login.html') return;
  if (!document.getElementById('hc-pdd-login-notice-style')) {
   var style=document.createElement('style');style.id='hc-pdd-login-notice-style';style.textContent=css;
   (document.head||document.documentElement).appendChild(style);
  }
  // The landing page and the official phone/SMS form are separate surfaces.
  // Add plain in-flow text under each, without moving or replacing form nodes.
  document.querySelectorAll('#login > .main,#login > .container').forEach(function (surface) {
   surface.classList.add('hc-pdd-login-surface');
   if (surface.querySelector('.hc-pdd-login-notice')) return;
   var note=document.createElement('p');note.className='hc-pdd-login-notice';note.textContent=message;
   surface.appendChild(note);
  });
 }
 new MutationObserver(function(records) {
  if (queued || !records.some(function(r){return !r.target.closest || !r.target.closest('.hc-pdd-login-notice,#hc-pdd-login-notice-style');})) return;
  queued=true;setTimeout(update,0);
 }).observe(document,{childList:true,subtree:true});
 window.HandyPddLoginNotice={update:update};update();
})();
