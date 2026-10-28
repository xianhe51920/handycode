import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const script = readFileSync(new URL('../app/src/main/assets/official-identity.js', import.meta.url), 'utf8');
function fixture({ inputType=null, focused=false, invisible=false, hidden=false, disabled=false, buttonPresent=true, rootPresent=true, frameUrl=null, frameHidden=false, frameOffscreen=false }={}) {
    const context={URL};runInNewContext(script,context);
    let clicks=0, output=false;
    const button={textContent:'刷新',disabled,getClientRects:()=>[{}],getAttribute:()=>null,click(){clicks++;},closest(){return this;}};
    const input={type:inputType,getClientRects:()=>hidden?[]:[{}]};
    const bar={querySelectorAll:()=>buttonPresent?[button]:[]};
    const root={querySelector(selector){if(selector.includes('refresh--'))return bar;if(selector.includes('code--'))return output?{childNodes:[{}]}:null;return output?{complete:true,naturalWidth:400}:null;}};
    const frame={getAttribute:()=>frameUrl,getClientRects:()=>[{}],getBoundingClientRect:()=>({width:360,height:625,left:frameOffscreen?-100000:0,right:frameOffscreen?-99640:360,top:0,bottom:625}),parentElement:frameHidden?{hiddenLoginContainer:true,parentElement:null}:null};
    let frames=frameUrl===null?[]:[frame];
    const doc={getElementById:()=>null,querySelectorAll:selector=>selector==='iframe'?frames:inputType?[input]:[],querySelector:()=>rootPresent?root:null,activeElement:focused?input:null,defaultView:{innerWidth:360,innerHeight:625,getComputedStyle:element=>({display:element.hiddenLoginContainer||element===input&&hidden?'none':'block',visibility:'visible',opacity:element===input&&invisible?'0':'1'})}};
    return { api:context.HandyCodeOfficialIdentity,context,doc,clicks:()=>clicks,authenticated(){output=true;},removeInput(){doc.querySelectorAll=selector=>selector==='iframe'?frames:[];doc.activeElement=null;},removeFrame(){frames=[];} };
}
for(const type of ['tel','number','text','password']){const f=fixture({inputType:type});assert.equal(f.api.recover(f.doc,2000),'login');f.api.recover(f.doc,10000);assert.equal(f.clicks(),0,`${type} entry must not be refreshed while typing`);}
let f=fixture({inputType:'number',focused:true,invisible:true});assert.equal(f.api.recover(f.doc,0),'login','Custom OTP inputs can be visually transparent while focused');
f=fixture({inputType:'tel',focused:true,hidden:true});f.authenticated();assert.equal(f.api.ready(f.doc),true,'A removed/hidden login overlay must not leave recovery blocked by stale focus');
f=fixture();assert.equal(f.api.recover(f.doc,0),'waiting');assert.equal(f.api.recover(f.doc,1000),'waiting');assert.equal(f.api.recover(f.doc,1300),'triggered');assert.equal(f.clicks(),1,'The blank official page must invoke its own Refresh control');
f.api.recover(f.doc,2000);assert.equal(f.clicks(),1,'Do not hammer the original button');f.api.recover(f.doc,7300);f.api.recover(f.doc,13300);assert.equal(f.api.recover(f.doc,19300),'exhausted');assert.equal(f.clicks(),3,'Recovery is bounded');
f=fixture();f.api.recover(f.doc,0);f.api.recover(f.doc,1300);f.authenticated();assert.equal(f.api.recover(f.doc,1400),'ready');assert.equal(f.clicks(),1,'Stop as soon as the official barcode is ready');
f=fixture({disabled:true});f.api.recover(f.doc,0);f.api.recover(f.doc,9000);assert.equal(f.clicks(),0,'Disabled official controls remain untouched');
f=fixture({buttonPresent:false});f.api.recover(f.doc,0);f.api.recover(f.doc,9000);assert.equal(f.clicks(),0,'Never substitute the Zoom or other button');
f=fixture({inputType:'tel'});f.api.recover(f.doc,10000);f.removeInput();assert.equal(f.api.recover(f.doc,11000),'waiting');assert.equal(f.api.recover(f.doc,12300),'triggered','Resume automatically once login fields disappear');
f=fixture({frameUrl:'https://login.m.taobao.com/login.htm'});for(const time of [0,2000,8000,16000,30000])assert.equal(f.api.recover(f.doc,time),'login');assert.equal(f.clicks(),0,'A cross-origin login iframe must block recovery before its inputs appear');
f.removeFrame();assert.equal(f.api.recover(f.doc,31000),'waiting');assert.equal(f.api.recover(f.doc,32300),'triggered','Resume after the login iframe actually closes');
f=fixture({frameUrl:'about:blank'});assert.equal(f.api.recover(f.doc,2000),'login','A full-size login iframe still being initialized must not be refreshed');
f=fixture({frameUrl:'https://login.m.taobao.com/login.htm',frameHidden:true});assert.equal(f.api.recover(f.doc,0),'waiting');assert.equal(f.api.recover(f.doc,1300),'triggered','A closed login overlay must not block output forever');
f=fixture({frameUrl:'https://login.m.taobao.com/login.htm',frameOffscreen:true});assert.equal(f.api.recover(f.doc,0),'waiting','Off-screen source documents are not a visible login modal');
f=fixture();const phases=[],jobs=[],observers=[];
f.doc.documentElement={};f.context.console={info:value=>phases.push(value)};f.context.setTimeout=fn=>{jobs.push(fn);};
f.context.MutationObserver=class {constructor(callback){this.callback=callback;observers.push(this);}observe(){}disconnect(){this.disconnected=true;}};
f.api.watch(f.doc);assert.deepEqual(phases,['__HANDYCODE_PHASE__:loading']);
observers[0].callback();observers[0].callback();assert.equal(jobs.length,1,'Batch repeated DOM changes into one presentation notification');jobs.shift()();assert.equal(phases.length,1,'Do not reopen the same loading state repeatedly');
f.authenticated();observers[0].callback();jobs.shift()();assert.equal(phases.at(-1),'__HANDYCODE_PHASE__:ready');
f.doc.getElementById=()=>({});observers[0].callback();jobs.shift()();assert.equal(phases.at(-1),'__HANDYCODE_PHASE__:merged');assert.equal(observers[0].disconnected,true,'Stop observing presentation after the merged page is installed');
console.log('PASS: official empty-code recovery, phone/OTP guards, cooldown, bounded attempts and completion cases');
