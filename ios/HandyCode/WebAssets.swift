import Foundation
import WebKit

enum WebAssets {
    private static let cacheLock = NSLock()
    private static var textCache: [String: String] = [:]
    private static let preloadFiles = [
        "official-identity.js", "taobao-login-chrome.js", "phone-theme.js",
        "login-ready.js", "taobao.html", "refresh-controller.js", "pickup-motion.js",
        "login-agreements.js", "taobao-login-theme.js", "cainiao-layout.js",
        "pdd-same-origin.js", "pdd-section-refresh.js", "pdd-layout.js",
        "pdd-login-client.js", "pdd-login-notice.js", "jd-orders.js",
        "jd-identity.js", "jd-login-theme.js"
    ]

    static func preload() {
        DispatchQueue.global(qos: .utility).async {
            for name in preloadFiles {
                _ = readText(name)
            }
        }
    }

    static func readText(_ fileName: String) -> String? {
        cacheLock.lock()
        if let cached = textCache[fileName] {
            cacheLock.unlock()
            return cached
        }
        cacheLock.unlock()

        let name = (fileName as NSString).deletingPathExtension
        let ext = (fileName as NSString).pathExtension
        guard let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "assets")
                ?? Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "WebAssets")
                ?? Bundle.main.url(forResource: name, withExtension: ext),
              let content = try? String(contentsOf: url, encoding: .utf8) else {
            return nil
        }

        cacheLock.lock()
        textCache[fileName] = content
        cacheLock.unlock()
        return content
    }

    static let consoleBridgeScript = """
    (function(){
      if(window.__hcConsoleBridged)return;
      window.__hcConsoleBridged=true;
      var handler=window.webkit&&window.webkit.messageHandlers&&window.webkit.messageHandlers.hcLog;
      if(!handler)return;
      ['log','info','warn'].forEach(function(level){
        var orig=console[level];
        console[level]=function(){
          try{
            if(arguments.length===1&&typeof arguments[0]==='string'&&arguments[0].indexOf('__HANDYCODE_')===0){
              handler.postMessage(arguments[0]);
            }
          }catch(e){}
          return orig&&orig.apply(console,arguments);
        };
      });
    })();
    """

    static func addConsoleBridge(to controller: WKUserContentController, handler: WKScriptMessageHandler) {
        controller.add(handler, name: "hcLog")
        controller.addUserScript(WKUserScript(source: consoleBridgeScript, injectionTime: .atDocumentStart, forMainFrameOnly: false))
    }

    static func configuredWebView(frame: CGRect = .zero,
                                  messageHandler: WKScriptMessageHandler? = nil,
                                  documentStartScripts: [String] = []) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.defaultWebpagePreferences.preferredContentMode = .mobile
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        let controller = WKUserContentController()
        if let messageHandler {
            addConsoleBridge(to: controller, handler: messageHandler)
        }
        for script in documentStartScripts {
            controller.addUserScript(WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        }
        config.userContentController = controller
        let web = WKWebView(frame: frame, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.allowsBackForwardNavigationGestures = false
        return web
    }
}
