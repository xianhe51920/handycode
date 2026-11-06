import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const java = readFileSync(new URL('../app/src/main/java/com/fairycrane/handycode/ScanCodeVisibility.java', import.meta.url), 'utf8');
const expression = java.slice(java.indexOf('SCRIPT =') + 8, java.indexOf(';\n    private'));
const script = [...expression.matchAll(/"(?:[^"\\]|\\.)*"/g)].map(m => JSON.parse(m[0])).join('');
function visible(options = {}) {
 const parent = { parentElement: null, style: {display:'block', visibility:'visible', opacity:'1'} };
 const code = {tagName:'IMG',complete:true,naturalWidth:200,parentElement:parent,
   style:{display:'block',visibility:'visible',opacity:'1'},
   getBoundingClientRect:()=>({width:200,height:82,left:20,top:40,right:220,bottom:122})};
 if(options.unloaded)code.naturalWidth=0;
 if(options.hidden)parent.style.display='none';
 if(options.faded)parent.style.opacity='0';
 if(options.offscreen)code.getBoundingClientRect=()=>({width:200,height:82,left:20,top:-50,right:220,bottom:32});
 const root={querySelectorAll:()=>options.noCode?[]:[code]};
 return vm.runInNewContext(script,{document:{getElementById:()=>options.noRoot?null:root},
   getComputedStyle:n=>n.style,innerWidth:360,innerHeight:640});
}
assert.equal(visible(),true);
for(const key of ['unloaded','hidden','faded','offscreen','noCode','noRoot'])assert.equal(visible({[key]:true}),false,key);
console.log('Scan-code visibility: 7 cases passed');
