package com.fairycrane.handycode;

/** Checks the displayed projection only; no identity data leaves the WebView. */
final class ScanCodeVisibility {
    static final String SCRIPT = "(function(){var root=document.getElementById('identity-content')||document.getElementById('hc-pdd-identity');"
        + "if(!root)return false;var codes=root.querySelectorAll('img[class*=width-origin--],img[class*=width-rotate--],#identity-content img,.hc-pdd-barcode,.hc-pdd-qr');"
        + "return Array.prototype.some.call(codes,function(n){if(n.tagName==='IMG'&&(!n.complete||!n.naturalWidth))return false;"
        + "for(var p=n;p;p=p.parentElement){var s=getComputedStyle(p);if(s.display==='none'||s.visibility!=='visible'||Number(s.opacity)===0)return false;}"
        + "var r=n.getBoundingClientRect();return r.width>40&&r.height>20&&r.top>=0&&r.left>=0&&r.bottom<=innerHeight&&r.right<=innerWidth;});})()";
    private ScanCodeVisibility() {}
}