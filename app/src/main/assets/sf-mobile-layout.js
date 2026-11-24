;(function () {
 'use strict';
 if (window.top !== window || location.origin !== 'https://www.sf-express.com'
     || !/^\/chn\/sc\/waybill\/list\/?$/.test(location.pathname)) return;
 if (document.getElementById('hc-sf-mobile-layout')) return;
 var style = document.createElement('style');
 style.id = 'hc-sf-mobile-layout';
 style.textContent = `
@media (max-width: 800px) {
 html, body, #__nuxt, #__layout, #app, #chn {
  width: 100% !important;
  min-width: 0 !important;
 }
 .sidebar-layout, .sidebar-layout > .right {
  box-sizing: border-box;
  width: 100% !important;
  min-width: 0 !important;
  margin-inline: 0 !important;
 }
 .sidebar-layout { display: block !important; }
 .sidebar-layout > .right { padding: 8px !important; }
 .sidebar-layout > .left, .common-header .menu-wrap { display: none !important; }
 .common-header, .common-header .header-wrap {
  box-sizing: border-box;
  height: auto !important;
  min-height: 44px;
  min-width: 0 !important;
  max-width: 100%;
 }
 .common-header .header-wrap { padding: 6px 10px !important; gap: 8px; }
 .common-header .header-wrap .home-icon { margin-right: 0 !important; }
 .common-header .header-wrap .users {
  flex: 1;
  min-width: 0;
  height: auto !important;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px 10px;
 }
 .common-header .header-wrap .users :is(.login, .phone, .accessibility) { margin-inline: 0 !important; }
 .waybill, .waybill .waybill-list, .waybill .account-waybill-section,
 .waybill .search-input-list, .waybill .shipping-search, .waybill .screen-wrap {
  box-sizing: border-box;
  min-width: 0 !important;
  max-width: 100% !important;
 }
 .waybill .title-wrap { padding: 8px 10px !important; height: auto !important; }
 .waybill .title-wrap .title { flex-wrap: wrap; gap: 4px; font-size: 17px !important; }
 .waybill .search-input-list { padding: 10px !important; }
 .waybill .shipping-search {
  display: flex !important;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  width: 100% !important;
 }
 .waybill .shipping-search .input-wrap { flex: 1 1 155px; width: auto !important; min-width: 0 !important; }
 .waybill .shipping-search input { box-sizing: border-box; min-width: 0; max-width: 100%; }
 .waybill .shipping-search .search-icon {
  flex: 0 0 auto;
  width: auto !important;
  min-width: 64px;
  margin: 0 !important;
  padding-inline: 12px;
 }
 .waybill .bill-tab { display: flex; flex-wrap: wrap; }
 .waybill .screen-wrap .option-item, .waybill .screen-wrap .el-range-editor { max-width: 100%; box-sizing: border-box; }
 body :has(> section.friend-links) { flex-wrap: wrap !important; max-width: 100%; }
 .friend-link-group { flex-wrap: wrap; }
 .pop-wrap .main-box, .alert-wrap .alert-container, .dialog-wrap .dialog-container {
  box-sizing: border-box;
  max-width: calc(100vw - 24px) !important;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
 }
}
`;
 (document.head || document.documentElement).appendChild(style);
})();
