import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const script = readFileSync(new URL('../app/src/main/assets/glass-theme-switch.js', import.meta.url), 'utf8');
let now = 0, scheduled;
const events = [];
const canvasListeners = {};
const noop = () => {};
const drawing = new Proxy({filter:'none'}, {get:(target, name) => name in target ? target[name] : noop});
const gpu = new Proxy({
 getShaderParameter:() => true,
 getProgramParameter:() => true,
 createShader:() => ({}),
 createProgram:() => ({}),
 createBuffer:() => ({}),
 createTexture:() => ({}),
 getUniformLocation:() => ({}),
 getAttribLocation:() => 0,
}, {get:(target,name) => name in target ? target[name] : noop});
const canvas = {style:{},getContext:() => gpu,addEventListener:(name,callback)=>{canvasListeners[name]=callback;}};
const document = {getElementById:() => canvas,createElement:() => ({getContext:() => drawing})};
const window = {
 innerWidth:74,innerHeight:44,devicePixelRatio:1,HandyCodeGlassInitialDark:true,addEventListener:noop,
 HandyCodeGlassBridge:{
  ready:() => events.push(['ready']),
  choose:dark => events.push(['choose',dark]),
  blend:light => events.push(['blend',light]),
  settled:() => events.push(['settled']),
  unavailable:() => events.push(['unavailable']),
 },
};
runInNewContext(script, {window,document,performance:{now:() => now},
 requestAnimationFrame:callback => {scheduled=callback;return 1;}});
function advance(frames) {
 for (let frame=0;frame<frames&&scheduled;frame++) {
  const callback=scheduled;
  scheduled=null;
  now+=1000/60;
  callback(now);
 }
}
advance(5);
assert.equal(events.filter(event=>event[0]==='ready').length,1);
window.HandyCodeGlassInput(0,.25,.5);
window.HandyCodeGlassInput(2,.5,.5);
advance(12);
assert.ok(events.some(event=>event[0]==='blend'&&event[1]>.15&&event[1]<.85),'Dragging while held previews an intermediate theme');
assert.equal(events.filter(event=>event[0]==='choose').length,0,'A held preview must not commit settings');
window.HandyCodeGlassInput(2,.25,.5);
advance(8);
assert.equal(events.filter(event=>event[0]==='blend').at(-1)[1],0,'Dragging back restores the original palette');
window.HandyCodeGlassInput(3,.25,.5);
advance(180);
assert.ok(events.some(event=>event[0]==='settled'),'Cancel settles and ends preview');
assert.equal(events.filter(event=>event[0]==='choose').length,0);
window.HandyCodeGlassInput(0,.25,.5);
window.HandyCodeGlassInput(2,.78,.5);
window.HandyCodeGlassInput(1,.78,.5);
advance(180);
assert.deepEqual(events.find(event=>event[0]==='choose'),['choose',false]);
assert.equal(events.filter(event=>event[0]==='blend').at(-1)[1],1);
assert.equal(scheduled,null,'Idle switch stops rendering');
canvasListeners.webglcontextlost();
assert.equal(events.at(-1)[0],'unavailable','GPU context loss requests native renderer recovery');
const noGpuEvents=[];
runInNewContext(script,{window:{HandyCodeGlassBridge:{unavailable:()=>noGpuEvents.push('unavailable')}},
 document:{getElementById:()=>({getContext:()=>null})}});
assert.deepEqual(noGpuEvents,['unavailable'],'Unavailable WebGL requests the same glass-image fallback');
console.log('PASS: held glass-theme preview, reversal, cancellation, commit and idle rendering');
