;(function () {
 'use strict';
 if (location.protocol !== 'https:' || !/(^|\.)(sf-express|douyin|jinritemai|snssdk|toutiao)\.com$/i.test(location.hostname)) return;
 if (window.HandyPickupProviderTheme) return;
 var dark=!!window.__HC_PICKUP_DARK__;
 function update(){
  if (!document.documentElement) return;
  document.documentElement.setAttribute('data-hc-pickup-theme',dark?'dark':'light');
  if (!document.getElementById('hc-pickup-provider-theme')) {
   var style=document.createElement('style');style.id='hc-pickup-provider-theme';
   style.textContent=`
html[data-hc-pickup-theme]{--hc-pickup-bg:#f5f6f8;--hc-pickup-surface:#fff;--hc-pickup-ink:#242b37;--hc-pickup-input:#edf0f5;color-scheme:light}
html[data-hc-pickup-theme="dark"]{--hc-pickup-bg:#0f1218;--hc-pickup-surface:#181c24;--hc-pickup-ink:#e6eaf2;--hc-pickup-input:#232936;color-scheme:dark}
html[data-hc-pickup-theme],html[data-hc-pickup-theme] :is(body,#__nuxt,#__layout,#app,#root){background-color:var(--hc-pickup-bg)!important;color:var(--hc-pickup-ink)!important;min-width:0!important;max-width:100vw!important}
html[data-hc-pickup-theme] :is(.login-wrap .main-box,#douyin_login_comp_flat_panel,#douyin_login_comp_h5_panel){background:var(--hc-pickup-surface)!important;color:var(--hc-pickup-ink)!important;max-width:94vw!important}
html[data-hc-pickup-theme] :is(#login-panel-new,#douyin-login-new-id){background:var(--hc-pickup-bg)!important}
html[data-hc-pickup-theme] #douyin_login_comp_flat_panel_title{color:var(--hc-pickup-ink)!important}
html[data-hc-pickup-theme] input:not([type="checkbox"]):not([type="radio"]){color:var(--hc-pickup-ink)!important;background-color:var(--hc-pickup-input)!important}
html[data-hc-pickup-theme] input::placeholder{color:#8792a5!important}
html[data-hc-pickup-theme] .login-wrap .main-box{width:min(94vw,400px)!important;min-width:0!important;max-height:90vh!important;overflow-y:auto!important}
`;
   (document.head||document.documentElement).appendChild(style);
  }
 }
 window.HandyPickupProviderTheme={setDark:function(value){dark=!!value;update();}};
 document.addEventListener('DOMContentLoaded',update,{once:true});update();
})();
