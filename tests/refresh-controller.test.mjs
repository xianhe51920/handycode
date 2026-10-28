import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext, Script } from 'node:vm';
const controllerScript = readFileSync(new URL('../app/src/main/assets/refresh-controller.js', import.meta.url), 'utf8');
const officialScript = readFileSync(new URL('../app/src/main/assets/official-identity.js', import.meta.url), 'utf8');
const html = readFileSync(new URL('../app/src/main/assets/taobao.html', import.meta.url), 'utf8').replace('@@REFRESH_CONTROLLER@@', controllerScript).replace('@@OFFICIAL_IDENTITY@@', officialScript);
for (const match of html.matchAll(/<script>([\s\S]*?)<\/script>/g)) new Script(match[1]);
const scope = {}; runInNewContext(controllerScript, scope);
function fixture() {
    let clock = 0, id = 0, projects = 0, reloads = 0, timeouts = 0, busy = false, ready = false, settled = true;
    const tasks = new Map();
    const advance = (ms) => { const end = clock + ms; for (;;) { const next = [...tasks].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0]; if (!next) break; clock = next[1].at; tasks.delete(next[0]); next[1].fn(); } clock = end; };
    const controller = scope.createHandyRefreshController({
        setTimeout(fn, delay) { tasks.set(++id, {fn,at:clock+delay}); return id; },
        clearTimeout(key) { tasks.delete(key); }, now:()=>clock,
        project() { projects++; if (ready) controller.finish(); },
        reload() { reloads++; }, settled:()=>settled,
        busy(value) { busy=value; }, timeout() { timeouts++; }
    });
    return { controller, advance, stats:()=>({projects,reloads,timeouts,busy}), ready:()=>{ready=true;}, loading:()=>{settled=false;} };
}
let f=fixture();
for(let i=0;i<50;i++){f.controller.queue();f.advance(10);}
assert.ok(f.stats().projects>=4,'Continuous mutations must not starve projection');
f=fixture();f.controller.start();f.ready();f.advance(100);
assert.equal(f.stats().busy,false,'Cached/already-loaded documents are projected without waiting for another load event');
f=fixture();f.controller.refresh();for(let i=0;i<10;i++)f.controller.refresh();
assert.equal(f.stats().reloads,1,'Repeated refresh taps must not restart the same in-flight load');
f.ready();f.advance(300);assert.equal(f.stats().busy,false,'Late readiness automatically completes refresh');
f=fixture();f.controller.start();f.advance(9000);assert.equal(f.stats().reloads,1,'A settled unready source is retried automatically');
f=fixture();f.loading();f.controller.start();f.advance(25000);assert.equal(f.stats().reloads,0,'A still-loading source is never interrupted');
f=fixture();f.controller.start();f.advance(40000);assert.equal(f.stats().timeouts,1);assert.equal(f.stats().busy,false);assert.ok(f.stats().reloads<=2,'Retries and timeouts are bounded');
f=fixture();f.controller.start();f.controller.dispose();f.advance(40000);assert.equal(f.stats().projects,0,'Disposed page timers cannot update a later page');
console.log('PASS: 8 refresh scheduling / recovery cases + assembled HTML script syntax');
