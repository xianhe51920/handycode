import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const script = readFileSync(new URL('../app/src/main/assets/login-ready.js', import.meta.url), 'utf8');
const library = readFileSync(new URL('../app/src/main/assets/official-identity.js', import.meta.url), 'utf8');
function ready({ merged = false, loginVisible = false, identity = false, code = false, complete = false, width = 0 } = {}) {
    const root = { querySelector(selector) { return selector.includes('code--') ? (code ? { childNodes: [{}] } : null) : { complete, naturalWidth: width }; } };
    const document = {
        getElementById() { return merged ? {} : null; },
        querySelectorAll(selector) { return selector === 'iframe' ? [] : loginVisible ? [{ getClientRects() { return [{}]; } }] : []; },
        querySelector() { return identity ? root : null; }
    };
    return runInNewContext(library + '\n' + script, { document });
}
assert.equal(ready(), false, 'Fresh data / no authenticated official output must retain the original page');
assert.equal(ready({ loginVisible: true, identity: true, code: true, complete: true, width: 400 }), false, 'A visible login form must not be overwritten');
assert.equal(ready({ identity: true }), false, 'An identity placeholder is not a completed login');
assert.equal(ready({ identity: true, code: true, width: 400 }), false, 'Wait for the real barcode image to finish loading');
assert.equal(ready({ identity: true, code: true, complete: true }), false, 'A broken barcode image must not trigger merging');
assert.equal(ready({ identity: true, code: true, complete: true, width: 400 }), true, 'Merge only after official identity output is usable');
assert.equal(ready({ merged: true }), true, 'An already merged document must not be rewritten');
console.log('PASS: 7 first-login / merge-gate cases');
