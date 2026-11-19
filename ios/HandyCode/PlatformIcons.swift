import UIKit
import WebKit

enum PlatformIcons {
    static func icon(for index: Int) -> UIImage? {
        switch index {
        case 0: return loadImage("ic_cainiao_official", ext: "webp") ?? makeBadge(text: "菜", color: UIColor(argb: 0xFF0078FF))
        case 1: return loadImage("ic_taobao", ext: "png") ?? makeBadge(text: "淘", color: UIColor(argb: 0xFFFF5000))
        case 2: return loadImage("ic_pinduoduo", ext: "png") ?? makeBadge(text: "拼", color: UIColor(argb: 0xFFE02E24))
        case 3: return makeBadge(text: "JD", color: UIColor(argb: 0xFFE1251B))
        default: return nil
        }
    }

    static func themeImage(dark: Bool) -> UIImage? {
        let name = dark ? "glass-theme-dark" : "glass-theme-light"
        guard let url = Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "assets")
                ?? Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "WebAssets")
                ?? Bundle.main.url(forResource: name, withExtension: "png") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    private static func loadImage(_ name: String, ext: String) -> UIImage? {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "drawable-nodpi")
                ?? Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "PlatformIcons")
                ?? Bundle.main.url(forResource: name, withExtension: ext) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    private static func makeBadge(text: String, color: UIColor) -> UIImage {
        let size = CGSize(width: 28, height: 28)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let rect = CGRect(origin: .zero, size: size)
            color.setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: 6).fill()
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: text.count > 1 ? 13 : 15, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            let strSize = (text as NSString).size(withAttributes: attrs)
            (text as NSString).draw(
                at: CGPoint(x: (size.width - strSize.width) / 2, y: (size.height - strSize.height) / 2),
                withAttributes: attrs
            )
        }
    }

    static func stationIcon(color: UIColor) -> UIImage {
        let size = CGSize(width: 16, height: 16)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let cg = ctx.cgContext
            cg.setStrokeColor(color.cgColor)
            cg.setLineWidth(1.3)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            let base = UIBezierPath()
            base.move(to: CGPoint(x: 2, y: 7))
            base.addLine(to: CGPoint(x: 2, y: 13.5))
            base.addLine(to: CGPoint(x: 14, y: 13.5))
            base.addLine(to: CGPoint(x: 14, y: 7))
            base.stroke()

            let roof = UIBezierPath()
            roof.move(to: CGPoint(x: 1, y: 6))
            roof.addLine(to: CGPoint(x: 3, y: 2.5))
            roof.addLine(to: CGPoint(x: 13, y: 2.5))
            roof.addLine(to: CGPoint(x: 15, y: 6))
            roof.addLine(to: CGPoint(x: 15, y: 7))
            roof.addCurve(to: CGPoint(x: 11.5, y: 7), controlPoint1: CGPoint(x: 13.9, y: 8.1), controlPoint2: CGPoint(x: 12.6, y: 8.1))
            roof.addCurve(to: CGPoint(x: 8, y: 7), controlPoint1: CGPoint(x: 10.4, y: 8.1), controlPoint2: CGPoint(x: 9.1, y: 8.1))
            roof.addCurve(to: CGPoint(x: 4.5, y: 7), controlPoint1: CGPoint(x: 6.9, y: 8.1), controlPoint2: CGPoint(x: 5.6, y: 8.1))
            roof.addCurve(to: CGPoint(x: 1, y: 7), controlPoint1: CGPoint(x: 3.4, y: 8.1), controlPoint2: CGPoint(x: 2.1, y: 8.1))
            roof.close()
            roof.stroke()
        }
    }
}

final class ThemeToggleButton: UIControl {
    private let backupView = UIImageView()
    private var renderer: WKWebView?
    private var rendererReady = false
    private var dark = false
    var onToggle: ((Bool) -> Void)?

    private final class WeakGlassHandler: NSObject, WKScriptMessageHandler {
        weak var owner: ThemeToggleButton?
        init(_ owner: ThemeToggleButton) { self.owner = owner }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            owner?.handleGlassMessage(message.body as? String ?? "")
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityIdentifier = "theme_toggle"

        backupView.contentMode = .scaleAspectFit
        backupView.isUserInteractionEnabled = false
        addSubview(backupView)

        setupRenderer()
        addTarget(self, action: #selector(handleTap), for: .touchUpInside)
        setDark(false, animated: false)
    }

    required init?(coder: NSCoder) { nil }

    deinit {
        renderer?.configuration.userContentController.removeScriptMessageHandler(forName: "hcGlass")
    }

    private func setupRenderer() {
        guard let script = WebAssets.readText("glass-theme-switch.js"), !script.isEmpty else { return }
        let config = WKWebViewConfiguration()
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        let controller = WKUserContentController()
        controller.add(WeakGlassHandler(self), name: "hcGlass")
        config.userContentController = controller

        let web = WKWebView(frame: bounds, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.isScrollEnabled = false
        web.isUserInteractionEnabled = false
        web.alpha = 0
        addSubview(web)
        renderer = web

        let html = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'">
        <style>html,body{margin:0;overflow:hidden;background:transparent}canvas{display:block;width:100vw;height:100vh;touch-action:none}</style>
        </head><body><canvas id="gl"></canvas><script>
        window.HandyCodeGlassInitialDark=\(dark ? "true" : "false");
        window.HandyCodeGlassBridge={
          ready:function(){try{window.webkit.messageHandlers.hcGlass.postMessage('ready');}catch(e){}},
          unavailable:function(){try{window.webkit.messageHandlers.hcGlass.postMessage('unavailable');}catch(e){}},
          choose:function(){},blend:function(){},settled:function(){}
        };
        try{\(script)}catch(e){window.HandyCodeGlassBridge.unavailable();}
        </script></body></html>
        """
        web.loadHTMLString(html, baseURL: nil)
    }

    private func handleGlassMessage(_ body: String) {
        if body == "ready" {
            rendererReady = true
            let js = "window.HandyCodeSetGlassDark&&window.HandyCodeSetGlassDark(\(dark ? "true" : "false"));"
            renderer?.evaluateJavaScript(js, completionHandler: nil)
            UIView.animate(withDuration: 0.16) {
                self.renderer?.alpha = 1
            }
        } else if body == "unavailable" {
            rendererReady = false
            renderer?.alpha = 0
        }
    }

    func setDark(_ dark: Bool, animated: Bool) {
        self.dark = dark
        accessibilityLabel = dark ? "切换外观模式，当前为深色" : "切换外观模式，当前为浅色"
        let nextImage = PlatformIcons.themeImage(dark: dark)
        if animated {
            UIView.transition(with: backupView, duration: 0.22, options: .transitionCrossDissolve) {
                self.backupView.image = nextImage
            }
        } else {
            backupView.image = nextImage
        }
        if rendererReady {
            let js = "window.HandyCodeSetGlassDark&&window.HandyCodeSetGlassDark(\(dark ? "true" : "false"));"
            renderer?.evaluateJavaScript(js, completionHandler: nil)
        }
    }

    @objc private func handleTap() {
        onToggle?(!dark)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        backupView.frame = bounds
        renderer?.frame = bounds
    }
}
