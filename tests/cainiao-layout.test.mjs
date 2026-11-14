import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const asset = name => readFileSync(new URL('../app/src/main/assets/' + name, import.meta.url), 'utf8');
const {window, document} = parseHTML(`<html><head></head><body><div id="root">
 <div class="modal-auth-auth-modal-body-fixture"><button>同意</button></div>
 <div class="home-name-fixture">138****0000<span>其他信息</span></div>
 <div class="info-bottom-root-fixture">点击放大，<span>20分钟</span>后自动刷新</div>
 <div class="home-bottom-fixture">手机号不对？前往小程序设置页</div>
 <img id="auth-code-id" style="opacity:0" src="data:image/png;base64,fixture">
 </div></body></html>`);
delete window.HandyCodeCainiaoLayout;
const modal = document.querySelector('[class*=modal-auth]');
const agreement = modal.querySelector('button');
let clicks = 0;
agreement.addEventListener('click', () => clicks++);
window.HTMLElement.prototype.getClientRects = function () { return this.hidden ? [] : [{}]; };
window.HTMLElement.prototype.getBoundingClientRect = () => ({width:320,height:120,top:0,bottom:120});
Object.defineProperties(window.HTMLImageElement.prototype, {
 complete:{get(){return Boolean(this.getAttribute('src'));},configurable:true},
 naturalWidth:{get(){return this.getAttribute('src') ? 600 : 0;},configurable:true},
});
window.matchMedia = () => ({matches:true});
window.innerWidth = 360;
window.getComputedStyle = () => ({paddingTop:'16',paddingBottom:'16'});
const geometryMessages = [];
let login = false;
const context = {window, document, location:new URL('https://page.cainiao.com/cn-app-web/identity-code/index.html#/'),
 HandyCodeOfficialIdentity:{loginVisible:() => login},
 getComputedStyle:element => ({display:element.hidden?'none':'block',visibility:'visible',opacity:element.style.opacity||'1'}),
 console:{log:message=>geometryMessages.push(message)}};
runInNewContext(asset('pickup-motion.js'), context);
runInNewContext(asset('cainiao-layout.js'), context);
const layout = window.HandyCodeCainiaoLayout;
assert.equal(layout.update(true), 'workflow');
assert.equal(document.querySelector('[class*=modal-auth]'), modal);
assert.equal(modal.querySelector('button'), agreement);
assert.equal(clicks, 0, 'Official agreement is left for the user');
assert.equal(document.getElementById('hc-cainiao-card'), null);

modal.hidden = true;
const source = document.getElementById('auth-code-id');
const root = document.getElementById('root');
assert.equal(layout.update(true), 'loading', 'A placeholder image cannot become the displayed code');
source.style.opacity = '1';
assert.equal(layout.update(true), 'ready');
const projection = document.querySelector('#hc-cainiao-card [data-hc-barcode]');
const phone = document.querySelector('.hc-cainiao-phone');
const expiry = document.querySelector('.hc-cainiao-expiry');
assert.equal(phone.textContent, '138****0000');
assert.equal(expiry.textContent, '20分钟后自动刷新');
assert.ok(!document.getElementById('hc-cainiao-card').textContent.includes('手机号不对'));
assert.equal(projection.getAttribute('src'), source.getAttribute('src'));
assert.equal(projection.getAttribute('aria-label'), '放大身份码');
assert.equal(root.hasAttribute('inert'), true);
assert.equal(document.getElementById('auth-code-id'), source);
const geometry = JSON.parse(geometryMessages.at(-1).split('__HANDYCODE_CAINIAO_GEOMETRY__:')[1]);
assert.deepEqual(Object.keys(geometry).sort(), ['body','expanded','heading','refresh','top','width'], 'Only layout dimensions and UI state leave the page');
assert.equal(geometry.width,360);
assert.equal(geometry.body,120);

source.src = 'data:image/png;base64,renewed-fixture';
document.querySelector('[class^=info-bottom-root-]').innerHTML = '点击放大，<span>35秒</span>后自动刷新';
document.querySelector('[class^=home-name-]').textContent = '未脱敏信息';
assert.equal(layout.update(false), 'ready');
assert.equal(document.querySelector('#hc-cainiao-card [data-hc-barcode]'), projection);
assert.equal(projection.getAttribute('src'), source.getAttribute('src'));
assert.equal(document.documentElement.dataset.hcCainiaoTheme, 'light');
assert.equal(phone.hidden, true);
assert.equal(expiry.textContent, '35秒后自动刷新');

source.style.opacity = '0';
assert.equal(layout.update(false), 'loading');
assert.equal(projection.getAttribute('src'), null, 'Do not retain the old code while loading');
assert.equal(document.getElementById('hc-cainiao-card').hidden, true);
assert.equal(root.hasAttribute('inert'), true, 'Pending refresh does not expose the raw source');
source.style.opacity = '1';
assert.equal(layout.update(false, true), 'ready');
assert.equal(projection.getAttribute('aria-expanded'), 'true', 'Restore expanded layout before exposing a refreshed card');
assert.ok(geometryMessages.every(message => !message.includes('138****0000') && !message.includes('base64')), 'Layout reporting contains no identity values');
login = true;
assert.equal(layout.update(false), 'login', 'Expired login is distinct from official Cainiao authorization');
assert.equal(root.hasAttribute('inert'), true, 'The source stays concealed when login expires');
console.log('PASS: official authorization preservation, live barcode projection, renewal, theme and expired-code handling');
