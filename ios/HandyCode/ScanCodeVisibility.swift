import Foundation

enum ScanCodeVisibility {
    static let script = """
    (function(){
      var root=document.getElementById('identity-content')||document.getElementById('hc-pdd-identity')||document.getElementById('hc-jd-identity');
      if(!root)return false;
      var codes=root.querySelectorAll('[data-hc-barcode],.hc-pdd-barcode,.hc-pdd-qr,.hc-jd-barcode');
      return Array.prototype.some.call(codes,function(n){
        if(n.tagName==='IMG'&&(!n.complete||!n.naturalWidth))return false;
        for(var p=n;p;p=p.parentElement){
          var s=getComputedStyle(p);
          if(s.display==='none'||s.visibility!=='visible'||Number(s.opacity)===0)return false;
        }
        var r=n.getBoundingClientRect();
        return r.width>40&&r.height>20&&r.top>=0&&r.left>=0&&r.bottom<=innerHeight&&r.right<=innerWidth;
      });
    })()
    """
}
