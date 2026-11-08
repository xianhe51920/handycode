import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script = readFileSync(new URL('../app/src/main/assets/phone-theme.js', import.meta.url), 'utf8');
function fixture(hostname, pathname) {
 const {window, document} = parseHTML('<html><head></head><body><input type="tel" value="fixture"><button disabled>验证并绑定</button></body></html>');
 delete window.HandyCodePhoneTheme;
 const context = {window, document, location:{hostname, pathname}};
 runInNewContext(script, context);
 return {window, document, context};
}
for (const [host, path, provider] of [
 ['m.pinduoduo.net', '/mdkd/cellPhoneList', 'pdd'],
 ['pages-fast.m.taobao.com', '/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/phone-manage', 'taobao'],
 ['pages-fast.m.taobao.com', '/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/add-friend-phone-ssr', 'taobao'],
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
console.log('PASS: scoped phone forms, reversible themes, idempotence, unchanged inputs, disabled state and official handlers');
