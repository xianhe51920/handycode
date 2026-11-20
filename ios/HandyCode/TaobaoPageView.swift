import UIKit
import WebKit

final class TaobaoPageView: UIView, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    private let cover = PickupLoadingView(singleCard: false)
    private let errorPanel = UIView()
    private let errorTitle = UILabel()
    private let errorDetail = UILabel()
    private let openIdentityButton = UIButton(type: .system)
    private let retryButton = UIButton(type: .system)
    private let resetSiteButton = UIButton(type: .system)
    private let inlineBackBar = UIView()
    private let inlineBackButton = UIButton(type: .system)

    private var web: WKWebView?
    private var inlineWeb: WKWebView?
    private var dark = false
    private var active = false
    private var combinedDisplayed = false
    private var loginVisible = false
    private var loginThemeReady = false
    private var needsFreshLoad = false
    private var failed = false
    private var generation = 0
    private var pollTimer: Timer?
    private var loginRevealFallback: DispatchWorkItem?
    private var deadline: TimeInterval = 0

    private var combinedMarkup = ""
    private var officialIdentityScript = ""
    private var combinedGateScript = ""
    private var taobaoLoginChromeScript = ""
    private var taobaoLoginThemeScript = ""
    private var phoneThemeScript = ""
    private var loginAgreementsScript = ""

    var onSessionChanged: ((Bool) -> Void)?
    var onRequestLogout: (() -> Void)?
    var onRequestResetSite: (() -> Void)?
    var onOpenTaobaoApp: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    deinit {
        loginRevealFallback?.cancel()
        pollTimer?.invalidate()
        web?.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
        inlineWeb?.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
    }

    private func setup() {
        officialIdentityScript = WebAssets.readText("official-identity.js") ?? ""
        combinedGateScript = (WebAssets.readText("login-ready.js") ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        taobaoLoginChromeScript = WebAssets.readText("taobao-login-chrome.js") ?? ""
        taobaoLoginThemeScript = WebAssets.readText("taobao-login-theme.js") ?? ""
        phoneThemeScript = WebAssets.readText("phone-theme.js") ?? ""
        loginAgreementsScript = WebAssets.readText("login-agreements.js") ?? ""

        if let rawHtml = WebAssets.readText("taobao.html"),
           let refreshCtrl = WebAssets.readText("refresh-controller.js"),
           let pickupMotion = WebAssets.readText("pickup-motion.js") {
            let escapedIdentity = escapeHtml(UrlPolicy.taobaoEntry)
            let escapedAddPkg = escapeHtml(UrlPolicy.taobaoAddPackageEntry)
            let escapedParcels = escapeHtml(UrlPolicy.taobaoParcelsEntry)
            combinedMarkup = rawHtml
                .replacingOccurrences(of: "@@REFRESH_CONTROLLER@@", with: refreshCtrl)
                .replacingOccurrences(of: "@@OFFICIAL_IDENTITY@@", with: officialIdentityScript)
                .replacingOccurrences(of: "@@PICKUP_MOTION@@", with: pickupMotion)
                .replacingOccurrences(of: "@@IDENTITY_URL@@", with: escapedIdentity)
                .replacingOccurrences(of: "@@ADD_PACKAGE_URL@@", with: escapedAddPkg)
                .replacingOccurrences(of: "@@PARCELS_URL@@", with: escapedParcels)
        }

        addSubview(cover)

        errorPanel.isHidden = true
        errorTitle.font = .systemFont(ofSize: 16, weight: .semibold)
        errorTitle.textAlignment = .center
        errorTitle.text = "取件页面暂未就绪"
        errorPanel.addSubview(errorTitle)

        errorDetail.font = .systemFont(ofSize: 13)
        errorDetail.textAlignment = .center
        errorDetail.numberOfLines = 0
        errorPanel.addSubview(errorDetail)

        openIdentityButton.setTitle("打开淘宝身份码", for: .normal)
        openIdentityButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
        openIdentityButton.layer.cornerRadius = 10
        openIdentityButton.layer.borderWidth = 1
        openIdentityButton.addTarget(self, action: #selector(handleOpenApp), for: .touchUpInside)
        errorPanel.addSubview(openIdentityButton)

        retryButton.setTitle("重新打开网页", for: .normal)
        retryButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
        retryButton.layer.cornerRadius = 10
        retryButton.setTitleColor(.white, for: .normal)
        retryButton.addTarget(self, action: #selector(reloadTaobao), for: .touchUpInside)
        errorPanel.addSubview(retryButton)

        resetSiteButton.setTitle("清除淘宝缓存并重新登录", for: .normal)
        resetSiteButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
        resetSiteButton.layer.cornerRadius = 10
        resetSiteButton.layer.borderWidth = 1
        resetSiteButton.addTarget(self, action: #selector(handleResetSite), for: .touchUpInside)
        errorPanel.addSubview(resetSiteButton)
        addSubview(errorPanel)

        inlineBackBar.isHidden = true
        inlineBackButton.setTitle("‹ 返回取件页", for: .normal)
        inlineBackButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        inlineBackButton.contentHorizontalAlignment = .leading
        inlineBackButton.addTarget(self, action: #selector(closeInline), for: .touchUpInside)
        inlineBackBar.addSubview(inlineBackButton)
        addSubview(inlineBackBar)

        setDark(false)
    }

    private func escapeHtml(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = AppTheme.bg(dark)
        cover.setDark(dark)
        errorPanel.backgroundColor = AppTheme.bg(dark)
        errorTitle.textColor = AppTheme.ink(dark)
        errorDetail.textColor = AppTheme.muted(dark)
        openIdentityButton.setTitleColor(AppTheme.ink(dark), for: .normal)
        openIdentityButton.layer.borderColor = AppTheme.border(dark).cgColor
        retryButton.backgroundColor = AppTheme.accent
        resetSiteButton.setTitleColor(AppTheme.ink(dark), for: .normal)
        resetSiteButton.layer.borderColor = AppTheme.border(dark).cgColor
        inlineBackBar.backgroundColor = AppTheme.surface(dark)
        inlineBackButton.setTitleColor(AppTheme.accentText(dark), for: .normal)

        let d = dark ? "true" : "false"
        if let web {
            web.evaluateJavaScript("""
                (function(d){
                  if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(d);
                  if(window.HandyCodeTaobaoLoginTheme)window.HandyCodeTaobaoLoginTheme.setDark(d);
                })(\(d));
                """, completionHandler: nil)
            if !phoneThemeScript.isEmpty {
                web.evaluateJavaScript(phoneThemeScript + "\nHandyCodePhoneTheme.setDark(\(d));", completionHandler: nil)
            }
        }
        if let inlineWeb, !phoneThemeScript.isEmpty {
            inlineWeb.evaluateJavaScript(phoneThemeScript + "\nHandyCodePhoneTheme.setDark(\(d));", completionHandler: nil)
        }
    }

    func markUpgradeFreshLoad() {
        needsFreshLoad = true
    }

    func showPage() {
        active = true
        if combinedMarkup.isEmpty {
            showError(title: "无法读取本地取件页面", detail: "请重新安装应用后重试。", showAppOption: false)
            return
        }
        if web == nil {
            createWeb()
        }
        cover.setAnimating(!cover.isHidden)
        startPollTimer()
    }

    func pausePage() {
        active = false
        pollTimer?.invalidate()
        pollTimer = nil
        loginRevealFallback?.cancel()
        loginRevealFallback = nil
        cover.setAnimating(false)
    }

    func resetSessionView() {
        pausePage()
        closeInline()
        generation += 1
        combinedDisplayed = false
        loginVisible = false
        loginThemeReady = false
        failed = false
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.uiDelegate = nil
            web.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            web.removeFromSuperview()
            self.web = nil
        }
        errorPanel.isHidden = true
        cover.isHidden = false
    }

    func refreshAfterExternalLogin() {
        guard let web else { showPage(); return }
        web.evaluateJavaScript("(function(){if(typeof window.HandyCodeRefresh==='function'){window.HandyCodeRefresh();return true;}return false;})()") { [weak self] handled, _ in
            if (handled as? Bool) != true {
                self?.reloadTaobao()
            }
        }
    }

    func checkVisibleScanCode(_ completion: @escaping (Bool) -> Void) {
        guard let web, combinedDisplayed, cover.isHidden, inlineWeb == nil else {
            completion(false)
            return
        }
        web.evaluateJavaScript(ScanCodeVisibility.script) { result, _ in
            completion((result as? Bool) ?? false)
        }
    }

    @discardableResult
    func goBack() -> Bool {
        if let inlineWeb {
            if inlineWeb.canGoBack {
                inlineWeb.goBack()
            } else {
                closeInline()
            }
            return true
        }
        if let web, !combinedDisplayed, web.canGoBack {
            web.goBack()
            return true
        }
        return false
    }

    private func makeEntryRequest() -> URLRequest? {
        guard let url = URL(string: UrlPolicy.taobaoEntry) else { return nil }
        let policy: URLRequest.CachePolicy = needsFreshLoad ? .reloadIgnoringLocalCacheData : .useProtocolCachePolicy
        return URLRequest(url: url, cachePolicy: policy, timeoutInterval: 30)
    }

    private func createWeb() {
        var scripts: [String] = []
        if !taobaoLoginThemeScript.isEmpty {
            scripts.append("window.__HC_TAOBAO_DARK__=\(dark ? "true" : "false");\n" + taobaoLoginThemeScript)
        }
        if !loginAgreementsScript.isEmpty {
            scripts.append(loginAgreementsScript)
        }
        let web = WebAssets.configuredWebView(frame: bounds, messageHandler: self, documentStartScripts: scripts)
        web.navigationDelegate = self
        web.uiDelegate = self
        insertSubview(web, at: 0)
        self.web = web
        beginLoading()
        if let request = makeEntryRequest() {
            web.load(request)
        }
    }

    private func beginLoading() {
        failed = false
        combinedDisplayed = false
        loginVisible = false
        loginThemeReady = false
        loginRevealFallback?.cancel()
        loginRevealFallback = nil
        deadline = CACurrentMediaTime() + 30
        errorPanel.isHidden = true
        cover.isHidden = false
        cover.setAnimating(active)
    }

    @objc private func reloadTaobao() {
        closeInline()
        needsFreshLoad = true
        generation += 1
        let expectedGen = generation
        loginRevealFallback?.cancel()
        loginRevealFallback = nil
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.uiDelegate = nil
            web.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            web.removeFromSuperview()
            self.web = nil
        }
        failed = false
        combinedDisplayed = false
        loginVisible = false
        loginThemeReady = false
        errorPanel.isHidden = true
        cover.isHidden = false
        cover.setAnimating(active)
        SiteDataClearer.shared.clearHttpCache { [weak self] in
            guard let self, self.generation == expectedGen, self.active else { return }
            if self.web == nil {
                self.createWeb()
            }
            self.startPollTimer()
        }
    }

    @objc private func handleResetSite() {
        onRequestResetSite?()
    }

    @objc private func handleOpenApp() {
        onOpenTaobaoApp?()
    }

    @objc private func closeInline() {
        if let inlineWeb {
            inlineWeb.stopLoading()
            inlineWeb.navigationDelegate = nil
            inlineWeb.uiDelegate = nil
            inlineWeb.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            inlineWeb.removeFromSuperview()
            self.inlineWeb = nil
        }
        inlineBackBar.isHidden = true
        setNeedsLayout()
    }

    private func showError(title: String, detail: String, showAppOption: Bool) {
        failed = true
        pollTimer?.invalidate()
        loginRevealFallback?.cancel()
        loginRevealFallback = nil
        cover.isHidden = true
        cover.setAnimating(false)
        errorTitle.text = title
        errorDetail.text = detail
        openIdentityButton.isHidden = !showAppOption
        resetSiteButton.isHidden = combinedMarkup.isEmpty
        errorPanel.isHidden = false
        setNeedsLayout()
    }

    private func startPollTimer() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.55, repeats: true) { [weak self] _ in
            self?.checkState()
        }
    }

    private func checkState() {
        guard active, let web, !failed, !combinedDisplayed else { return }
        if !loginVisible && CACurrentMediaTime() > deadline {
            showError(
                title: "淘宝取件信息暂未加载完成",
                detail: "官方网页暂未准备就绪，请检查网络后点“重新打开网页”重试；若持续超时可清除淘宝缓存重新登录。",
                showAppOption: true
            )
            return
        }
        let currentURL = web.url?.absoluteString
        if UrlPolicy.isTaobaoAuthUrl(currentURL) {
            handleLoginDetected()
            return
        }
        guard sameIdentityPage(currentURL), !officialIdentityScript.isEmpty else { return }
        let expectedGen = generation
        let probe = officialIdentityScript + "\n(function(){HandyCodeOfficialIdentity.watch(document);if(HandyCodeOfficialIdentity.ready(document))return 'ready';return HandyCodeOfficialIdentity.recover(document,Date.now());})()"
        web.evaluateJavaScript(probe) { [weak self] result, _ in
            guard let self, self.generation == expectedGen, !self.combinedDisplayed else { return }
            let status = result as? String
            if status == "ready" {
                self.applyCombined(web: web, expectedGen: expectedGen)
            } else if status == "login" {
                self.handleLoginDetected()
            } else {
                self.installObserverAndTheme(web)
            }
        }
    }

    private func installObserverAndTheme(_ view: WKWebView) {
        if !taobaoLoginChromeScript.isEmpty {
            view.evaluateJavaScript(taobaoLoginChromeScript, completionHandler: nil)
        }
        if !officialIdentityScript.isEmpty {
            view.evaluateJavaScript(officialIdentityScript + "\nHandyCodeOfficialIdentity.watch(document);", completionHandler: nil)
        }
        if !taobaoLoginThemeScript.isEmpty {
            view.evaluateJavaScript("window.__HC_TAOBAO_DARK__=\(dark ? "true" : "false");\n" + taobaoLoginThemeScript, completionHandler: nil)
        }
    }

    private func handleLoginDetected() {
        guard !failed && !combinedDisplayed else { return }
        if !loginVisible {
            loginVisible = true
            onSessionChanged?(false)
            loginRevealFallback?.cancel()
            let fallback = DispatchWorkItem { [weak self] in
                guard let self, self.loginVisible, !self.failed, !self.combinedDisplayed else { return }
                self.commitRevealLogin()
            }
            loginRevealFallback = fallback
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.5, execute: fallback)
        }
        if loginThemeReady || taobaoLoginThemeScript.isEmpty {
            commitRevealLogin()
        }
    }

    private func commitRevealLogin() {
        loginRevealFallback?.cancel()
        loginRevealFallback = nil
        guard !failed && !combinedDisplayed else { return }
        if let web, !taobaoLoginChromeScript.isEmpty {
            web.evaluateJavaScript(taobaoLoginChromeScript, completionHandler: nil)
        }
        cover.isHidden = true
        cover.setAnimating(false)
        errorPanel.isHidden = true
    }

    private func applyCombined(web: WKWebView, expectedGen: Int) {
        let themedHtml = combinedMarkup.replacingOccurrences(
            of: "<html lang=\"zh-CN\">",
            with: "<html lang=\"zh-CN\" data-hc-theme=\"\(dark ? "dark" : "light")\">"
        )
        guard let encoded = try? JSONSerialization.data(withJSONObject: [themedHtml]),
              let arrayStr = String(data: encoded, encoding: .utf8),
              arrayStr.count >= 2 else { return }
        let quotedHtml = String(arrayStr.dropFirst().dropLast())
        let script = officialIdentityScript + """
        \n(function(){
          if(document.getElementById('handycode-merged'))return true;
          if(!(\(combinedGateScript)))return false;
          document.open();document.write(\(quotedHtml));document.close();
          if(typeof window.HandyCodeSetDark==='function')window.HandyCodeSetDark(\(dark ? "true" : "false"));
          return true;
        })()
        """
        web.evaluateJavaScript(script) { [weak self] result, _ in
            guard let self, self.generation == expectedGen else { return }
            if (result as? Bool) == true {
                self.loginRevealFallback?.cancel()
                self.loginRevealFallback = nil
                self.combinedDisplayed = true
                self.loginVisible = false
                self.cover.isHidden = true
                self.cover.setAnimating(false)
                self.errorPanel.isHidden = true
                self.onSessionChanged?(true)
            }
        }
    }

    private func sameIdentityPage(_ raw: String?) -> Bool {
        UrlPolicy.isTaobaoIdentityEntry(raw)
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let target = navigationAction.request.url?.absoluteString
        let mainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        if target == UrlPolicy.localLogoutAction {
            if mainFrame && webView == web && combinedDisplayed {
                DispatchQueue.main.async { [weak self] in
                    self?.onRequestLogout?()
                }
            }
            decisionHandler(.cancel)
            return
        }
        if UrlPolicy.isAllowed(target) {
            decisionHandler(.allow)
        } else {
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView,
                 createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction,
                 windowFeatures: WKWindowFeatures) -> WKWebView? {
        guard let url = navigationAction.request.url?.absoluteString, UrlPolicy.isAllowed(url) else { return nil }
        if UrlPolicy.isTaobaoPhonePage(url) || UrlPolicy.isTaobaoAddPhonePage(url) || UrlPolicy.isTaobaoAddPackagePage(url) {
            closeInline()
            let sub = WKWebView(frame: bounds, configuration: configuration)
            sub.navigationDelegate = self
            sub.uiDelegate = self
            sub.isOpaque = false
            sub.backgroundColor = AppTheme.bg(dark)
            if !phoneThemeScript.isEmpty {
                let d = dark ? "true" : "false"
                sub.configuration.userContentController.addUserScript(
                    WKUserScript(source: phoneThemeScript + "\nHandyCodePhoneTheme.setDark(\(d));",
                                 injectionTime: .atDocumentEnd, forMainFrameOnly: true)
                )
            }
            inlineWeb = sub
            inlineBackBar.isHidden = false
            insertSubview(sub, belowSubview: inlineBackBar)
            setNeedsLayout()
            return sub
        }
        webView.load(navigationAction.request)
        return nil
    }

    func webViewDidClose(_ webView: WKWebView) {
        if webView == inlineWeb {
            closeInline()
            refreshAfterExternalLogin()
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        if webView == web {
            generation += 1
            if !combinedDisplayed { beginLoading() }
        }
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        if webView == web, !failed {
            installObserverAndTheme(webView)
            if UrlPolicy.isTaobaoAuthUrl(webView.url?.absoluteString) {
                handleLoginDetected()
            } else {
                checkState()
            }
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if webView == inlineWeb, !phoneThemeScript.isEmpty {
            webView.evaluateJavaScript(phoneThemeScript + "\nHandyCodePhoneTheme.setDark(\(dark ? "true" : "false"));", completionHandler: nil)
            return
        }
        if webView == web {
            if needsFreshLoad {
                needsFreshLoad = false
            }
            installObserverAndTheme(webView)
            if !failed && UrlPolicy.isTaobaoAuthUrl(webView.url?.absoluteString) {
                handleLoginDetected()
            } else {
                checkState()
            }
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        if webView == web && !combinedDisplayed {
            showError(title: "无法打开淘宝页面", detail: "请检查网络后重试；若覆盖安装后持续异常，可清除淘宝缓存重新登录。", showAppOption: true)
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        if webView == web && !combinedDisplayed {
            showError(title: "无法打开淘宝页面", detail: "请检查网络后重试；若覆盖安装后持续异常，可清除淘宝缓存重新登录。", showAppOption: true)
        }
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let value = message.body as? String else { return }
        if value == "__HANDYCODE_TAOBAO_LOGIN__:ready" {
            loginThemeReady = true
            if !combinedDisplayed && !failed {
                loginVisible = true
                onSessionChanged?(false)
                commitRevealLogin()
            }
        } else if value == "__HANDYCODE_PHASE__:login" {
            handleLoginDetected()
        } else if value == "__HANDYCODE_PHASE__:ready" || value == "__HANDYCODE_PHASE__:merged" {
            onSessionChanged?(true)
            checkState()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        web?.frame = bounds
        cover.frame = bounds
        errorPanel.frame = bounds
        errorTitle.frame = CGRect(x: 24, y: bounds.midY - 125, width: bounds.width - 48, height: 24)
        errorDetail.frame = CGRect(x: 24, y: errorTitle.frame.maxY + 10, width: bounds.width - 48, height: 54)
        openIdentityButton.frame = CGRect(x: 28, y: errorDetail.frame.maxY + 16, width: bounds.width - 56, height: 46)
        let retryY = openIdentityButton.isHidden ? errorDetail.frame.maxY + 16 : openIdentityButton.frame.maxY + 10
        retryButton.frame = CGRect(x: 28, y: retryY, width: bounds.width - 56, height: 46)
        resetSiteButton.frame = CGRect(x: 28, y: retryButton.frame.maxY + 10, width: bounds.width - 56, height: 46)

        if !inlineBackBar.isHidden {
            inlineBackBar.frame = CGRect(x: 0, y: 0, width: bounds.width, height: 40)
            inlineBackButton.frame = CGRect(x: 12, y: 0, width: 140, height: 40)
            inlineWeb?.frame = CGRect(x: 0, y: 40, width: bounds.width, height: bounds.height - 40)
        }
    }
}
