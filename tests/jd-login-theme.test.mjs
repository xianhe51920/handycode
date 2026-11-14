import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {parseHTML} from 'linkedom';

const script = readFileSync(new URL('../app/src/main/assets/jd-login-theme.js', import.meta.url), 'utf8');
const markup = '<html><head></head><body><input type="tel" value="fixture"><button disabled>获取验证码</button></body></html>';
const {document, window: domWindow} = parseHTML(markup);
const window = {};
window.top = window;
const verificationEvents = [];
const context = {
 window, document, location: {origin: 'https://plogin.m.jd.com'},
 MutationObserver: domWindow.MutationObserver,
 getComputedStyle: node => ({visibility: node.style.visibility || 'visible'}),
 console: {info: value => verificationEvents.push(value)},
};
const input = document.querySelector('input');
const button = document.querySelector('button');
const root = document.documentElement;
runInNewContext(script, context);
window.HandyCodeJdLoginTheme.setDark(false);
assert.equal(root.hasAttribute('data-hc-jd-login-dark'), false);
assert.equal(document.getElementById('hc-jd-login-theme'), null);
window.HandyCodeJdLoginTheme.setDark(true);
assert.equal(root.hasAttribute('data-hc-jd-login-dark'), true);
const style = document.getElementById('hc-jd-login-theme');
assert.match(style.textContent, /input::placeholder/);
assert.match(style.textContent, /\.dialog-body/);
assert.match(style.textContent, /#captcha_modal/);
runInNewContext(script, context);
window.HandyCodeJdLoginTheme.setDark(false);
assert.equal(root.hasAttribute('data-hc-jd-login-dark'), false);
window.HandyCodeJdLoginTheme.setDark(true);
assert.equal(document.querySelectorAll('#hc-jd-login-theme').length, 1);
assert.equal(document.getElementById('hc-jd-login-theme'), style);
assert.equal(document.querySelector('input'), input);
assert.equal(input.value, 'fixture');
assert.equal(button.disabled, true);

const popup = document.createElement('div');
popup.id = 'captcha_modal';
popup.getClientRects = () => popup.hidden ? [] : [{}];
document.body.append(popup);
await Promise.resolve();
popup.hidden = true;
await Promise.resolve();
popup.hidden = false;
await Promise.resolve();
popup.remove();
await Promise.resolve();
assert.deepEqual(verificationEvents, ['closed', 'open', 'closed', 'open', 'closed']
 .map(state => '__HANDYCODE_JD_VERIFICATION__:' + state));

for (const [origin, frame] of [
 ['http://plogin.m.jd.com', false],
 ['https://trade.m.jd.com', false],
 ['https://plogin.m.jd.com.example', false],
 ['https://plogin.m.jd.com', true],
]) {
 const frameWindow = {};
 frameWindow.top = frame ? {} : frameWindow;
 runInNewContext(script, {window: frameWindow, document: parseHTML(markup).document, location: {origin}});
 assert.equal(frameWindow.HandyCodeJdLoginTheme, undefined);
}
console.log('PASS: JD login theme scoping, reversible styles, form preservation and verification visibility');
