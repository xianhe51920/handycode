import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script = readFileSync(new URL('../app/src/main/assets/phone-theme.js', import.meta.url), 'utf8');
function fixture(hostname, pathname) {
 const {window, document} = parseHTML('<html><head></head><body><input type="tel" value="fixture"><button disabled>验证并绑定</button></body></html>');
 delete window.HandyCodePhoneTheme;
 const context = {window, document, location:{hostname, pathname},MutationObserver:window.MutationObserver};
 runInNewContext(script, context);
 return {window, document, context};
}
for (const [host, path, provider] of [
 ['m.pinduoduo.net', '/mdkd/cellPhoneList', 'pdd'],
 ['pages-fast.m.taobao.com', '/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/phone-manage', 'taobao'],
 ['pages-fast.m.taobao.com', '/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/add-friend-phone-ssr', 'taobao'],
 ['pages-fast.m.taobao.com', '/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search-ssr', 'taobao'],
 ['m.pinduoduo.net', '/mdkd/add-package', 'pdd'],
]) {
 const {window, document, context} = fixture(host, path);
 const input = document.querySelector('input');
 const button = document.querySelector('button');
 let clicks = 0;
 button.addEventListener('click', () => clicks++);
 window.HandyCodePhoneTheme.setDark(true);
 const style = document.getElementById('hc-phone-theme');
 assert.equal(document.documentElement.dataset.hcPhoneProvider, provider);
 assert.equal(document.documentElement.dataset.hcPhoneTheme, 'dark');
 assert.ok(style.textContent.includes('.bind-new-cell-phone-content-item'));
 assert.ok(style.textContent.includes('[class^="input-wrap--"]'));
 assert.ok(style.textContent.includes('input::placeholder'));
 runInNewContext(script, context);
 window.HandyCodePhoneTheme.setDark(false);
 assert.equal(document.documentElement.dataset.hcPhoneTheme, 'light');
 assert.equal(document.querySelectorAll('#hc-phone-theme').length, 1);
 assert.equal(document.querySelector('input'), input);
 assert.equal(input.value, 'fixture');
 assert.equal(input.getAttribute('style'), null);
 assert.equal(button.disabled, true);
 button.click();
 assert.equal(clicks, 1, 'Official handlers survive theme changes');
 context.location.pathname = '/unrelated';
 window.HandyCodePhoneTheme.setDark(true);
 assert.equal(document.documentElement.getAttribute('data-hc-phone-theme'), null);
}
for (const [host, path] of [
 ['m.pinduoduo.net.evil.example', '/mdkd/cellPhoneList'],
 ['pages-fast.m.taobao.com', '/unrelated/phone-manage'],
 ['m.pinduoduo.net', '/login.html'],
]) {
 const {window, document} = fixture(host, path);
 window.HandyCodePhoneTheme.setDark(true);
 assert.equal(document.getElementById('hc-phone-theme'), null);
 assert.equal(document.documentElement.getAttribute('data-hc-phone-theme'), null);
}
{
 const {window,document}=fixture('pages-fast.m.taobao.com','/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search-ssr');
 document.body.innerHTML='<div class="school-search--fixture"><div class="header--fixture"><input placeholder="输入运单号查询取件码"></div><div><input type="file"><p>图片识别运单号</p></div><div class="history-pkg--fixture">追踪介绍</div><div class="rowFeeds--fixture">推荐商品</div><div class="result--fixture">查询结果</div></div>';
 const input=document.querySelector('input');let inputEvents=0;input.addEventListener('input',()=>inputEvents++);
 window.HandyCodePhoneTheme.setDark(true);
 assert.equal(document.documentElement.dataset.hcPackageForm,'taobao');
 assert.equal(document.querySelectorAll('[data-hc-package-extra]').length,3);
 assert.equal(input.closest('[data-hc-package-extra]'),null);
 assert.equal(document.querySelector('.result--fixture').hasAttribute('data-hc-package-extra'),false);
 window.HandyCodePhoneTheme.setDark(false);
 assert.equal(document.querySelector('input'),input);
 input.dispatchEvent(new window.Event('input'));assert.equal(inputEvents,1);
}
{
 const {window,document}=fixture('m.pinduoduo.net','/mdkd/add-package');
 document.body.innerHTML='<div class="index_add-package-package__fixture"><textarea placeholder="完整手机号/取件码/完整运单号">WAYBILL-FIXTURE</textarea><div class="submit-btn">添加</div></div>';
 const input=document.querySelector('textarea');
 window.HandyCodePhoneTheme.setDark(true);
 assert.equal(input.placeholder,'请输入完整运单号');
 assert.equal(input.value,'WAYBILL-FIXTURE');
 window.HandyCodePhoneTheme.setDark(false);
 assert.equal(document.querySelector('textarea'),input);
 assert.equal(input.value,'WAYBILL-FIXTURE');
 assert.equal(document.documentElement.dataset.hcPackageForm,'pdd');
}
console.log('PASS: scoped phone forms, reversible themes, idempotence, unchanged inputs, disabled state and official handlers');
