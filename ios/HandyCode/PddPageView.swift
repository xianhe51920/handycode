import UIKit
import WebKit
import CoreLocation

final class PddPageView: UIView, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    private let cover = PickupLoadingView(singleCard: false)
    private let actionsBar = UIView()
    private let statusLabel = UILabel()
    private let retryButton = UIButton(type: .system)
    private let clientButton = UIButton(type: .system)

    private var web: WKWebView?
    private var dark = false
    private var active = false
    private var prewarming = false
    private var pageCommitted = false
    private var sawLogin = false
    private var returnedAfterLogin = false
    private var layoutWorkflow = false
    private var navigationSeq = 0
    private var timeoutWork: DispatchWorkItem?

    private var compatibilityScript = ""
    private var phoneThemeScript = ""
    private var loginClientScript = ""

    var onOpenClient: (() -> Void)?
    var onSessionChanged: ((Bool) -> Void)?
    var onRequestLogout: (() -> Void)?
    var onSettled: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        compatibilityScript = [
            WebAssets.readText("pdd-same-origin.js"),
            WebAssets.readText("pdd-section-refresh.js"),
            WebAssets.readText("pickup-motion.js"),
            WebAssets.readText("pdd-layout.js")
        ].compactMap { $0 }.joined(separator: "\n")
        phoneThemeScript = WebAssets.readText("phone-theme.js") ?? ""
        loginClientScript = [
            WebAssets.readText("login-agreements.js"),
            WebAssets.readText("pdd-login-client.js"),
            WebAssets.readText("pdd-login-notice.js")
        ].compactMap { $0 }.joined(separator: "\n")

        actionsBar.isHidden = true
        statusLabel.font = .systemFont(ofSize: 12)
        statusLabel.text = "正在打开官方身份码网页…"
        actionsBar.addSubview(statusLabel)

        retryButton.setTitle("重试", for: .normal)
        retryButton.titleLabel?.font = .systemFont(ofSize: 12, weight: .medium)
        retryButton.addTarget(self, action: #selector(reload), for: .touchUpInside)
        actionsBar.addSubview(retryButton)

        clientButton.setTitle("打开客户端", for: .normal)
        clientButton.titleLabel?.font = .systemFont(ofSize: 12, weight: .medium)
        clientButton.addTarget(self, action: #selector(handleOpenClient), for: .touchUpInside)
        actionsBar.addSubview(clientButton)
        addSubview(actionsBar)

        addSubview(cover)
        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = AppTheme.bg(dark)
        cover.setDark(dark)
        actionsBar.backgroundColor = AppTheme.surface(dark)
        statusLabel.textColor = AppTheme.muted(dark)
        retryButton.setTitleColor(AppTheme.accentText(dark), for: .normal)
        clientButton.setTitleColor(AppTheme.accentText(dark), for: .normal)
        if let web { syncDark(web) }
    }

    func showPage() {
        active = true
        prewarming = false
        setProjectionActive(true)
        if web == nil {
            createWeb()
        } else if let web {
            syncDark(web)
        }
        cover.setAnimating(!cover.isHidden)
    }

    func prewarmPage() {
        if active || prewarming || web != nil { return }
        prewarming = true
        createWeb()
        cover.setAnimating(false)
        setProjectionActive(true)
    }

    func idlePage() {
        active = false
        prewarming = false
        cover.setAnimating(false)
        setProjectionActive(false)
    }

    private func setProjectionActive(_ enabled: Bool) {
        guard let web, UrlPolicy.isPddPackagePage(web.url?.absoluteString) else { return }
        web.evaluateJavaScript("window.HandyPddLayout&&window.HandyPddLayout.setActive(\(enabled ? "true" : "false"));", completionHandler: nil)
    }

    func resetSessionView() {
        idlePage()
        timeoutWork?.cancel()
        navigationSeq += 1
        sawLogin = false
        returnedAfterLogin = false
        layoutWorkflow = false
        pageCommitted = false
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.uiDelegate = nil
            web.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            web.removeFromSuperview()
            self.web = nil
        }
        actionsBar.isHidden = true
        cover.isHidden = false
        cover.alpha = 1
    }

    func checkVisibleScanCode(_ completion: @escaping (Bool) -> Void) {
        guard let web, !layoutWorkflow, cover.isHidden, pageCommitted else {
            completion(false)
            return
        }
        web.evaluateJavaScript(ScanCodeVisibility.script) { result, _ in
            completion((result as? Bool) ?? false)
        }
    }

    @discardableResult
    func goBack() -> Bool {
        guard let web else { return false }
        if layoutWorkflow {
            web.evaluateJavaScript("window.HandyPddLayout&&window.HandyPddLayout.back();", completionHandler: nil)
            return true
        }
        guard web.canGoBack else { return false }
        web.goBack()
        return true
    }

    private func createWeb() {
        var scripts: [String] = []
        if !compatibilityScript.isEmpty {
            scripts.append("window.__HC_DARK__=\(dark ? "true" : "false");\n" + compatibilityScript)
        }
        if !loginClientScript.isEmpty {
            scripts.append(loginClientScript)
        }
        let web = WebAssets.configuredWebView(frame: bounds, messageHandler: self, documentStartScripts: scripts)
        web.navigationDelegate = self
        web.uiDelegate = self
        insertSubview(web, at: 0)
        self.web = web
        showCover()
        if let url = URL(string: UrlPolicy.pddEntry) {
            web.load(URLRequest(url: url))
        }
    }

    @objc private func reload() {
        sawLogin = false
        returnedAfterLogin = false
        guard let web else { showPage(); return }
        showCover()
        actionsBar.isHidden = true
        if let url = URL(string: UrlPolicy.pddEntry) {
            web.load(URLRequest(url: url))
        }
    }

    @objc private func handleOpenClient() {
        onOpenClient?()
    }

    private func showCover() {
        cover.alpha = 1
        cover.isHidden = false
        cover.setAnimating(active)
    }

    private func hideCover() {
        guard !cover.isHidden else { return }
        if !active {
            cover.isHidden = true
            cover.setAnimating(false)
            cover.alpha = 1
            return
        }
        UIView.animate(withDuration: 0.22, animations: {
            self.cover.alpha = 0
        }, completion: { _ in
            self.cover.isHidden = true
            self.cover.setAnimating(false)
            self.cover.alpha = 1
        })
    }

    private func syncDark(_ view: WKWebView) {
        let d = dark ? "true" : "false"
        var script = """
        (function(d){
          window.__HC_DARK__=d;
          var root=document.documentElement;
          var bg=d?'#0f1218':'#f5f6f8';
          if(root){
            root.setAttribute('data-hc-theme',d?'dark':'light');
            root.style.setProperty('background',bg,'important');
            root.style.setProperty('background-color',bg,'important');
          }
          if(document.body){
            document.body.style.setProperty('background',bg,'important');
            document.body.style.setProperty('background-color',bg,'important');
          }
          if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(d);
        })(\(d));
        """
        if !phoneThemeScript.isEmpty {
            script += "\n" + phoneThemeScript + "\nHandyCodePhoneTheme.setDark(\(d));"
        }
        view.evaluateJavaScript(script, completionHandler: nil)
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let target = navigationAction.request.url?.absoluteString
        let current = webView.url?.absoluteString
        let mainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        if target == UrlPolicy.localLogoutAction {
            if mainFrame && UrlPolicy.isPddPackagePage(current) && !layoutWorkflow {
                DispatchQueue.main.async { [weak self] in
                    self?.onRequestLogout?()
                }
            }
            decisionHandler(.cancel)
            return
        }
        if UrlPolicy.opensPddIdentity(current: current, target: target, mainFrame: mainFrame, gesture: true) {
            onOpenClient?()
            decisionHandler(.cancel)
            return
        }
        if !mainFrame || UrlPolicy.isPddAllowed(target) {
            if mainFrame, UrlPolicy.isPddLoginPage(target) {
                sawLogin = true
                onSessionChanged?(false)
            }
            decisionHandler(.allow)
        } else {
            actionsBar.isHidden = false
            statusLabel.text = "网页请求打开其他地址，请使用官方登录页"
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        navigationSeq += 1
        let expected = navigationSeq
        pageCommitted = false
        layoutWorkflow = false
        showCover()
        actionsBar.isHidden = true
        let url = webView.url?.absoluteString
        if UrlPolicy.isPddLoginPage(url) {
            sawLogin = true
            onSessionChanged?(false)
        }
        timeoutWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.navigationSeq == expected, !self.cover.isHidden else { return }
            self.actionsBar.isHidden = false
            self.statusLabel.text = "页面暂未就绪，请点重试"
            if self.prewarming && !self.active { self.idlePage() }
            self.onSettled?()
        }
        timeoutWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 18, execute: work)
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        pageCommitted = true
        actionsBar.isHidden = true
        if UrlPolicy.isPddLoginPage(webView.url?.absoluteString) {
            sawLogin = true
            onSessionChanged?(false)
        }
        syncDark(webView)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        pageCommitted = true
        syncDark(webView)
        let url = webView.url?.absoluteString
        if UrlPolicy.isPddLoginPage(url) {
            sawLogin = true
            onSessionChanged?(false)
        }
        if let components = UrlPolicy.httpsComponents(url), components.path == "/index.html",
           sawLogin, !returnedAfterLogin, let entry = URL(string: UrlPolicy.pddEntry) {
            returnedAfterLogin = true
            webView.load(URLRequest(url: entry))
            return
        }
        if !loginClientScript.isEmpty {
            webView.evaluateJavaScript(loginClientScript, completionHandler: nil)
        }
        if !UrlPolicy.isPddPackagePage(url) {
            timeoutWork?.cancel()
            hideCover()
            if prewarming && !active { idlePage() }
            onSettled?()
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        actionsBar.isHidden = false
        statusLabel.text = "网页未打开，请检查网络后重试"
        hideCover()
        if prewarming && !active { idlePage() }
        onSettled?()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        actionsBar.isHidden = false
        statusLabel.text = "网页未打开，请检查网络后重试"
        hideCover()
        if prewarming && !active { idlePage() }
        onSettled?()
    }

    private func resolveStationCoords(_ rawPayload: String) {
        guard let data = rawPayload.data(using: .utf8),
              let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let code = payload["code"] as? String, !code.isEmpty else { return }
        let name = ((payload["name"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let detail = ((payload["detail"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let queries = Array(NSOrderedSet(array: [
            name,
            "\(name) \(detail)".trimmingCharacters(in: .whitespacesAndNewlines),
            "\(detail) \(name)".trimmingCharacters(in: .whitespacesAndNewlines),
            detail
        ].filter { !$0.isEmpty })) as? [String] ?? []

        func finish(_ lng: Double, _ lat: Double) {
            DispatchQueue.main.async { [weak self] in
                guard let self, let web = self.web,
                      let encoded = try? JSONSerialization.data(withJSONObject: [code]),
                      let arr = String(data: encoded, encoding: .utf8),
                      arr.count >= 2 else { return }
                let quotedCode = String(arr.dropFirst().dropLast())
                web.evaluateJavaScript("window.__hcOnStationCoords&&window.__hcOnStationCoords(\(quotedCode),\(lng),\(lat));", completionHandler: nil)
            }
        }

        func tryNext(_ index: Int) {
            guard index < queries.count else { finish(0, 0); return }
            CLGeocoder().geocodeAddressString(queries[index]) { placemarks, _ in
                if let loc = placemarks?.first?.location {
                    finish(loc.coordinate.longitude, loc.coordinate.latitude)
                } else {
                    tryNext(index + 1)
                }
            }
        }
        tryNext(0)
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let value = message.body as? String else { return }
        let geocodePrefix = "__HANDYCODE_PDD_GEOCODE__:"
        if value.hasPrefix(geocodePrefix) {
            resolveStationCoords(String(value.dropFirst(geocodePrefix.count)))
            return
        }
        if value == "__HANDYCODE_PDD_AUTH__:signed-in" {
            onSessionChanged?(true)
            return
        }
        let prefix = "__HANDYCODE_PDD_LAYOUT__:"
        guard value.hasPrefix(prefix), let web else { return }
        let phase = String(value.dropFirst(prefix.count))
        layoutWorkflow = (phase == "source")
        syncDark(web)
        if phase == "cards" || phase == "source" {
            timeoutWork?.cancel()
            actionsBar.isHidden = true
            hideCover()
            if phase == "cards" {
                onSessionChanged?(true)
                if prewarming && !active { idlePage() }
                onSettled?()
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let barHeight: CGFloat = actionsBar.isHidden ? 0 : 40
        actionsBar.frame = CGRect(x: 0, y: 0, width: bounds.width, height: barHeight)
        if !actionsBar.isHidden {
            clientButton.frame = CGRect(x: bounds.width - 82, y: 4, width: 72, height: 32)
            retryButton.frame = CGRect(x: clientButton.frame.minX - 54, y: 4, width: 48, height: 32)
            statusLabel.frame = CGRect(x: 12, y: 4, width: retryButton.frame.minX - 18, height: 32)
        }
        let contentFrame = CGRect(x: 0, y: barHeight, width: bounds.width, height: bounds.height - barHeight)
        web?.frame = contentFrame
        cover.frame = contentFrame
    }
}
