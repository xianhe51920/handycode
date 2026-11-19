import UIKit
import WebKit

final class CainiaoPageView: UIView, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    private let cover = PickupLoadingView(singleCard: true)
    private let errorPanel = UIView()
    private let errorLabel = UILabel()
    private let retryButton = UIButton(type: .system)
    private let loginPanel = UIView()
    private let loginLogo = UIImageView()
    private let loginHint = UILabel()
    private let loginButton = UIButton(type: .system)

    private var web: WKWebView?
    private var dark = false
    private var active = false
    private var phase = "loading"
    private var inspectTimer: Timer?
    private var generation = 0
    private var deadline: TimeInterval = 0
    private var bootstrap = ""

    var onOpenTaobao: (() -> Void)?
    var onLoginRequired: (() -> Void)?

    var isLoginRequired: Bool { phase == "login-required" }

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        bootstrap = [
            WebAssets.readText("official-identity.js"),
            WebAssets.readText("pickup-motion.js"),
            WebAssets.readText("cainiao-layout.js")
        ].compactMap { $0 }.joined(separator: "\n")

        addSubview(cover)

        errorPanel.isHidden = true
        errorLabel.font = .systemFont(ofSize: 14)
        errorLabel.textAlignment = .center
        errorLabel.numberOfLines = 0
        errorPanel.addSubview(errorLabel)
        retryButton.setTitle("重新打开网页", for: .normal)
        retryButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        retryButton.addTarget(self, action: #selector(reload), for: .touchUpInside)
        errorPanel.addSubview(retryButton)
        addSubview(errorPanel)

        loginPanel.isHidden = true
        loginLogo.image = PlatformIcons.icon(for: 0)
        loginLogo.contentMode = .scaleAspectFit
        loginPanel.addSubview(loginLogo)

        loginHint.text = "如果需要使用菜鸟身份码，\n请先登录淘宝"
        loginHint.font = .systemFont(ofSize: 16)
        loginHint.textAlignment = .center
        loginHint.numberOfLines = 0
        loginPanel.addSubview(loginHint)

        loginButton.setTitle("前往淘宝登录", for: .normal)
        loginButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        loginButton.setTitleColor(.white, for: .normal)
        loginButton.layer.cornerRadius = 8
        loginButton.addTarget(self, action: #selector(handleOpenTaobao), for: .touchUpInside)
        loginPanel.addSubview(loginButton)
        addSubview(loginPanel)

        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = AppTheme.bg(dark)
        cover.setDark(dark)
        errorPanel.backgroundColor = AppTheme.bg(dark)
        errorLabel.textColor = AppTheme.ink(dark)
        retryButton.setTitleColor(AppTheme.accentText(dark), for: .normal)
        loginPanel.backgroundColor = AppTheme.bg(dark)
        loginHint.textColor = AppTheme.ink(dark)
        loginButton.backgroundColor = UIColor(argb: dark ? 0xFF1B71DF : 0xFF0668DE)
        if active { inspect() }
    }

    func setLoginRequired(_ required: Bool) {
        if required == isLoginRequired { return }
        if required {
            let wasActive = active
            resetSessionView()
            active = wasActive
            phase = "login-required"
            cover.isHidden = true
            loginPanel.isHidden = false
        } else {
            beginLoading()
            if active { showPage() }
        }
    }

    func showPage() {
        active = true
        if isLoginRequired { return }
        if bootstrap.isEmpty {
            fail("菜鸟页面资源读取失败，请重新安装。")
            return
        }
        if web == nil { createWeb() }
        cover.setAnimating(!cover.isHidden)
        inspect()
        startInspectTimer()
    }

    func pausePage() {
        active = false
        inspectTimer?.invalidate()
        inspectTimer = nil
        cover.setAnimating(false)
    }

    func resetSessionView() {
        pausePage()
        generation += 1
        phase = "loading"
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.uiDelegate = nil
            web.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            web.removeFromSuperview()
            self.web = nil
        }
        errorPanel.isHidden = true
        loginPanel.isHidden = true
        cover.isHidden = false
    }

    func checkVisibleScanCode(_ completion: @escaping (Bool) -> Void) {
        guard let web, phase == "ready", cover.isHidden else {
            completion(false)
            return
        }
        web.evaluateJavaScript(ScanCodeVisibility.script) { result, _ in
            completion((result as? Bool) ?? false)
        }
    }

    private func createWeb() {
        let web = WebAssets.configuredWebView(frame: bounds, messageHandler: self)
        web.navigationDelegate = self
        web.uiDelegate = self
        insertSubview(web, at: 0)
        self.web = web
        beginLoading()
        if let url = URL(string: UrlPolicy.cainiaoEntry) {
            web.load(URLRequest(url: url))
        }
    }

    private func beginLoading() {
        phase = "loading"
        deadline = CACurrentMediaTime() + 18
        errorPanel.isHidden = true
        loginPanel.isHidden = true
        cover.isHidden = false
        cover.setAnimating(active)
    }

    private func fail(_ message: String) {
        phase = "error"
        inspectTimer?.invalidate()
        cover.isHidden = true
        cover.setAnimating(false)
        loginPanel.isHidden = true
        errorLabel.text = message
        errorPanel.isHidden = false
    }

    @objc private func reload() {
        if let web {
            beginLoading()
            if let url = URL(string: UrlPolicy.cainiaoEntry) {
                web.load(URLRequest(url: url))
            }
        } else {
            showPage()
        }
    }

    @objc private func handleOpenTaobao() {
        onOpenTaobao?()
    }

    private func startInspectTimer() {
        inspectTimer?.invalidate()
        inspectTimer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: true) { [weak self] _ in
            self?.inspect()
        }
    }

    private func inspect() {
        guard active, let web, !isLoginRequired, !bootstrap.isEmpty else { return }
        if phase == "loading", CACurrentMediaTime() > deadline {
            fail("菜鸟身份码加载超时，请重试。")
            return
        }
        let currentURL = web.url?.absoluteString
        if UrlPolicy.isCainiaoLoginPage(currentURL) {
            setLoginRequired(true)
            onLoginRequired?()
            return
        }
        guard UrlPolicy.isCainiaoEntry(currentURL) else { return }
        let script = bootstrap + "\n(window.HandyCodeCainiaoLayout ? window.HandyCodeCainiaoLayout.update(\(dark ? "true" : "false"), false) : 'loading')"
        let expectedGen = generation
        web.evaluateJavaScript(script) { [weak self] result, _ in
            guard let self, self.generation == expectedGen, let status = result as? String else { return }
            if status == "login" {
                self.setLoginRequired(true)
                self.onLoginRequired?()
            } else if status == "ready" {
                self.phase = "ready"
                self.cover.isHidden = true
                self.cover.setAnimating(false)
                self.errorPanel.isHidden = true
                self.loginPanel.isHidden = true
            }
        }
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let target = navigationAction.request.url?.absoluteString
        if UrlPolicy.isAllowed(target) {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        generation += 1
        beginLoading()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        inspect()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        fail("菜鸟页面暂时无法连接，请重试。")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        fail("菜鸟页面暂时无法连接，请重试。")
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let text = message.body as? String, text.hasPrefix("__HANDYCODE_CAINIAO_GEOMETRY__:") else { return }
        let jsonStr = String(text.dropFirst("__HANDYCODE_CAINIAO_GEOMETRY__:".count))
        if let data = jsonStr.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           (obj["refresh"] as? Bool) == true {
            beginLoading()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        web?.frame = bounds
        cover.frame = bounds
        errorPanel.frame = bounds
        errorLabel.frame = CGRect(x: 24, y: bounds.midY - 60, width: bounds.width - 48, height: 60)
        retryButton.frame = CGRect(x: (bounds.width - 140) / 2, y: bounds.midY + 12, width: 140, height: 40)

        loginPanel.frame = bounds
        let midY = bounds.midY
        loginLogo.frame = CGRect(x: (bounds.width - 88) / 2, y: midY - 120, width: 88, height: 88)
        loginHint.frame = CGRect(x: 24, y: midY - 16, width: bounds.width - 48, height: 50)
        loginButton.frame = CGRect(x: 28, y: midY + 52, width: bounds.width - 56, height: 48)
    }
}
