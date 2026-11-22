import UIKit
import WebKit

final class JdIdentityCardView: UIView, WKNavigationDelegate, WKScriptMessageHandler {
    private let heading = UIView()
    private let titleLabel = UILabel()
    private let refreshButton = UIButton(type: .system)
    private let divider = UIView()
    private let contentBox = UIView()
    private let noticeLabel = UILabel()
    private let skeleton = PickupLoadingView(singleCard: true)

    private var web: WKWebView?
    private var dark = false
    private var active = false
    private var prewarming = false
    private var loading = true
    private var signedIn = false
    private var loginRequired = false
    private var loginOpen = false
    private var errorText = ""
    private var generation = 0
    private var deadline: TimeInterval = 0
    private var pollTimer: Timer?
    private var contentHeight: CGFloat = 220

    private var script = ""
    private var loginTheme = ""

    var onChanged: (() -> Void)?
    var isSignedIn: Bool { signedIn }
    var isLoading: Bool { loading }
    var wantsLogin: Bool { loginOpen }

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        layer.cornerRadius = 16
        layer.borderWidth = 1
        clipsToBounds = true

        script = WebAssets.readText("jd-identity.js") ?? ""
        loginTheme = [
            WebAssets.readText("login-agreements.js"),
            WebAssets.readText("jd-login-theme.js")
        ].compactMap { $0 }.joined(separator: "\n")

        titleLabel.text = "身份码"
        titleLabel.font = .systemFont(ofSize: 16, weight: .medium)
        heading.addSubview(titleLabel)

        refreshButton.setTitle("刷新", for: .normal)
        refreshButton.titleLabel?.font = .systemFont(ofSize: 12)
        refreshButton.contentHorizontalAlignment = .trailing
        refreshButton.addTarget(self, action: #selector(reload), for: .touchUpInside)
        heading.addSubview(refreshButton)
        addSubview(heading)
        addSubview(divider)

        contentBox.addSubview(skeleton)
        noticeLabel.font = .systemFont(ofSize: 14)
        noticeLabel.textAlignment = .center
        noticeLabel.numberOfLines = 0
        noticeLabel.isUserInteractionEnabled = true
        noticeLabel.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleNoticeTap)))
        contentBox.addSubview(noticeLabel)
        addSubview(contentBox)
        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = loginOpen ? AppTheme.bg(dark) : AppTheme.surface(dark)
        layer.cornerRadius = loginOpen ? 0 : 16
        layer.borderWidth = loginOpen ? 0 : 1
        layer.borderColor = AppTheme.border(dark).cgColor
        titleLabel.textColor = AppTheme.ink(dark)
        divider.backgroundColor = AppTheme.divider(dark)
        noticeLabel.textColor = AppTheme.muted(dark)
        skeleton.setDark(dark)
        if let web {
            web.evaluateJavaScript("""
                window.HandyJdIdentity&&window.HandyJdIdentity.setDark(\(dark ? "true" : "false"));
                window.HandyCodeJdLoginTheme&&window.HandyCodeJdLoginTheme.setDark(\(dark ? "true" : "false"));
                """, completionHandler: nil)
        }
        present()
    }

    func resumePage() {
        prewarming = false
        active = true
        if web == nil && !script.isEmpty { createWeb() }
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.sample()
        }
        present()
    }

    func prewarmPage() {
        if web != nil || script.isEmpty { return }
        prewarming = true
        active = true
        createWeb()
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            self?.sample()
        }
        present()
    }

    func pausePage() {
        active = false
        prewarming = false
        pollTimer?.invalidate()
        pollTimer = nil
        skeleton.setAnimating(false)
    }

    func retryAfterSharedLogin() {
        if loginRequired || !errorText.isEmpty || loading {
            reload()
        }
    }

    func resetSessionView() {
        pausePage()
        generation += 1
        signedIn = false
        loginRequired = false
        loginOpen = false
        loading = true
        errorText = ""
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.configuration.userContentController.removeScriptMessageHandler(forName: "hcLog")
            web.removeFromSuperview()
            self.web = nil
        }
        present()
    }

    func checkVisibleScanCode(_ completion: @escaping (Bool) -> Void) {
        guard let web, signedIn, !loginOpen, !loading else {
            completion(false)
            return
        }
        web.evaluateJavaScript(ScanCodeVisibility.script) { result, _ in
            completion((result as? Bool) ?? false)
        }
    }

    var preferredHeight: CGFloat {
        53 + 1 + contentHeight
    }

    private func createWeb() {
        var docScripts: [String] = []
        if !loginTheme.isEmpty {
            docScripts.append(loginTheme + "\nwindow.HandyCodeJdLoginTheme&&window.HandyCodeJdLoginTheme.setDark(\(dark ? "true" : "false"));")
        }
        let web = WebAssets.configuredWebView(frame: contentBox.bounds, messageHandler: self, documentStartScripts: docScripts)
        web.navigationDelegate = self
        contentBox.insertSubview(web, at: 0)
        self.web = web
        beginLoading()
        if let url = URL(string: JdUrlPolicy.identity) {
            web.load(URLRequest(url: url))
        }
    }

    private func beginLoading() {
        loading = true
        loginRequired = false
        errorText = ""
        deadline = CACurrentMediaTime() + 18
        present()
    }

    @objc func reload() {
        guard let web else { resumePage(); return }
        generation += 1
        beginLoading()
        if let url = URL(string: JdUrlPolicy.identity) {
            web.load(URLRequest(url: url))
        }
    }

    @objc private func handleNoticeTap() {
        if loginRequired {
            loginOpen = true
            setDark(dark)
            onChanged?()
        } else {
            reload()
        }
    }

    private func sample() {
        guard active, let web else { return }
        if loading && !loginRequired && CACurrentMediaTime() > deadline {
            loading = false
            errorText = "京东身份码加载超时，请点击重试"
            present()
            onChanged?()
            return
        }
        let currentURL = web.url?.absoluteString
        if JdUrlPolicy.isLoginHost(currentURL) {
            loading = false
            loginRequired = true
            signedIn = false
            present()
            onChanged?()
            return
        }
        guard JdUrlPolicy.isIdentity(currentURL), !script.isEmpty else { return }
        let expected = generation
        let js = script + "\nwindow.HandyJdIdentity&&window.HandyJdIdentity.setDark(\(dark ? "true" : "false"));\nwindow.HandyJdIdentity?window.HandyJdIdentity.snapshot():null"
        web.evaluateJavaScript(js) { [weak self] result, _ in
            guard let self, self.generation == expected, let dict = result as? [String: Any] else { return }
            if let h = dict["height"] as? CGFloat, h >= 180, h <= 560, abs(h - self.contentHeight) > 1 {
                self.contentHeight = h
                self.onChanged?()
            }
            if (dict["ready"] as? Bool) == true {
                self.loading = false
                self.loginRequired = false
                self.loginOpen = false
                self.signedIn = true
                self.errorText = ""
                self.present()
                self.onChanged?()
            } else if (dict["failed"] as? Bool) == true {
                self.loading = false
                self.errorText = "京东身份码暂时不可用，请点击重试"
                self.present()
                self.onChanged?()
            }
        }
    }

    private func present() {
        refreshButton.setTitle(loading ? "刷新中" : "刷新", for: .normal)
        refreshButton.isEnabled = !loading
        refreshButton.setTitleColor(loading ? AppTheme.muted(dark) : AppTheme.accentText(dark), for: .normal)
        skeleton.isHidden = !loading || loginOpen
        skeleton.setAnimating(active && loading && !loginOpen && !prewarming)
        let showNotice = !loading && !signedIn && !loginOpen
        noticeLabel.isHidden = !showNotice
        if showNotice {
            noticeLabel.text = loginRequired ? "请在京东官方页面登录身份码\n点击继续登录" : errorText
        }
        web?.isHidden = !(signedIn || loginOpen)
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let target = navigationAction.request.url?.absoluteString
        decisionHandler(JdUrlPolicy.allowed(target) ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        sample()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        sample()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        loading = false
        errorText = "京东身份码暂时无法连接，请点击重试"
        present()
        onChanged?()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        loading = false
        errorText = "京东身份码暂时无法连接，请点击重试"
        present()
        onChanged?()
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let text = message.body as? String, text.hasPrefix("__HANDYCODE_JD_IDENTITY_HEIGHT__:") else { return }
        if let value = Double(text.dropFirst("__HANDYCODE_JD_IDENTITY_HEIGHT__:".count)), value >= 180, value <= 560 {
            contentHeight = CGFloat(value)
            onChanged?()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        heading.frame = CGRect(x: 0, y: 0, width: bounds.width, height: 53)
        titleLabel.frame = CGRect(x: 14, y: 12, width: bounds.width - 96, height: 29)
        refreshButton.frame = CGRect(x: bounds.width - 78, y: 12, width: 64, height: 29)
        divider.frame = CGRect(x: 0, y: 53, width: bounds.width, height: 1)
        let boxHeight = loginOpen ? max(220, bounds.height - 54) : contentHeight
        contentBox.frame = CGRect(x: 0, y: 54, width: bounds.width, height: boxHeight)
        skeleton.frame = contentBox.bounds
        noticeLabel.frame = contentBox.bounds.insetBy(dx: 20, dy: 20)
        web?.frame = contentBox.bounds
    }
}

final class JdPageView: UIView, WKNavigationDelegate {
    private let scrollView = UIScrollView()
    private let identityCard = JdIdentityCardView()
    private let ordersCard = UIView()
    private let ordersHeading = UIView()
    private let ordersTitle = UILabel()
    private let ordersRefresh = UIButton(type: .system)
    private let ordersDivider = UIView()
    private let ordersBody = UIStackView()
    private let skeleton = PickupLoadingView(singleCard: true)
    private let logoutButton = UIButton(type: .system)

    private var web: WKWebView?
    private var dark = false
    private var paused = true
    private var prewarming = false
    private var loading = true
    private var loginVisible = false
    private var orderSignedIn = false
    private var identitySignedIn = false
    private var generation = 0
    private var deadline: TimeInterval = 0
    private var pollTimer: Timer?
    private var orders: [[String: Any]] = []
    private var hasSnapshot = false
    private var errorMessage = ""
    private var isBusyError = false

    private var ordersScript = ""
    private var loginThemeScript = ""
    private var imageCache: [String: UIImage] = [:]

    var onSessionChanged: ((Bool) -> Void)?
    var onRequestLogout: (() -> Void)?
    var onSettled: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        ordersScript = WebAssets.readText("jd-orders.js") ?? ""
        loginThemeScript = [
            WebAssets.readText("login-agreements.js"),
            WebAssets.readText("jd-login-theme.js")
        ].compactMap { $0 }.joined(separator: "\n")

        addSubview(scrollView)
        identityCard.onChanged = { [weak self] in
            guard let self else { return }
            let was = self.identitySignedIn
            self.identitySignedIn = self.identityCard.isSignedIn
            self.onSessionChanged?(self.orderSignedIn || self.identitySignedIn)
            if !was && self.identitySignedIn && self.loginVisible {
                self.reload()
            }
            self.updatePresentation()
            self.checkSettled()
        }
        scrollView.addSubview(identityCard)

        ordersCard.layer.cornerRadius = 16
        ordersCard.layer.borderWidth = 1
        ordersCard.clipsToBounds = true
        scrollView.addSubview(ordersCard)

        ordersTitle.text = "待取包裹"
        ordersTitle.font = .systemFont(ofSize: 16, weight: .medium)
        ordersHeading.addSubview(ordersTitle)

        ordersRefresh.setTitle("刷新", for: .normal)
        ordersRefresh.titleLabel?.font = .systemFont(ofSize: 12)
        ordersRefresh.contentHorizontalAlignment = .trailing
        ordersRefresh.addTarget(self, action: #selector(reload), for: .touchUpInside)
        ordersHeading.addSubview(ordersRefresh)
        ordersCard.addSubview(ordersHeading)
        ordersCard.addSubview(ordersDivider)

        ordersBody.axis = .vertical
        ordersBody.spacing = 16
        ordersCard.addSubview(ordersBody)
        ordersCard.addSubview(skeleton)

        logoutButton.setTitle("退出登录", for: .normal)
        logoutButton.titleLabel?.font = .systemFont(ofSize: 14)
        logoutButton.layer.cornerRadius = 10
        logoutButton.layer.borderWidth = 1
        logoutButton.isHidden = true
        logoutButton.addTarget(self, action: #selector(handleLogout), for: .touchUpInside)
        scrollView.addSubview(logoutButton)

        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = AppTheme.bg(dark)
        scrollView.backgroundColor = AppTheme.bg(dark)
        identityCard.setDark(dark)
        ordersCard.backgroundColor = AppTheme.surface(dark)
        ordersCard.layer.borderColor = AppTheme.border(dark).cgColor
        ordersTitle.textColor = AppTheme.ink(dark)
        ordersDivider.backgroundColor = AppTheme.divider(dark)
        skeleton.setDark(dark)
        logoutButton.layer.borderColor = UIColor(argb: dark ? 0xFF58657A : 0xFFB9C3D0).cgColor
        logoutButton.setTitleColor(UIColor(argb: dark ? 0xFFBCC6D6 : 0xFF536071), for: .normal)
        if let web, JdUrlPolicy.isLoginHost(web.url?.absoluteString) {
            web.evaluateJavaScript("window.HandyCodeJdLoginTheme&&window.HandyCodeJdLoginTheme.setDark(\(dark ? "true" : "false"));", completionHandler: nil)
        }
        renderBody()
    }

    func showPage() {
        prewarming = false
        paused = false
        identityCard.resumePage()
        if web == nil && !ordersScript.isEmpty { createWeb() }
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: true) { [weak self] _ in
            self?.sample()
        }
        sample()
        updatePresentation()
    }

    func prewarmPage() {
        guard web == nil, !ordersScript.isEmpty, paused else { return }
        prewarming = true
        paused = false
        identityCard.prewarmPage()
        createWeb()
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: true) { [weak self] _ in
            self?.sample()
        }
        sample()
        updatePresentation()
    }

    func pausePage() {
        paused = true
        prewarming = false
        pollTimer?.invalidate()
        pollTimer = nil
        skeleton.setAnimating(false)
        identityCard.pausePage()
    }

    private func checkSettled() {
        guard !loading, (!identityCard.isLoading || loginVisible) else { return }
        if prewarming && !paused { pausePage() }
        onSettled?()
    }

    func resetSessionView() {
        pausePage()
        identityCard.resetSessionView()
        orderSignedIn = false
        identitySignedIn = false
        loginVisible = false
        loading = true
        hasSnapshot = false
        orders = []
        errorMessage = ""
        isBusyError = false
        generation += 1
        if let web {
            web.stopLoading()
            web.navigationDelegate = nil
            web.removeFromSuperview()
            self.web = nil
        }
        renderBody()
        updatePresentation()
    }

    func checkVisibleScanCode(_ completion: @escaping (Bool) -> Void) {
        if loginVisible {
            completion(false)
        } else {
            identityCard.checkVisibleScanCode(completion)
        }
    }

    @discardableResult
    func goBack() -> Bool {
        if loginVisible, let web, web.canGoBack {
            web.goBack()
            return true
        }
        return false
    }

    private func createWeb() {
        let scripts = [ordersScript, loginThemeScript].filter { !$0.isEmpty }
        let web = WebAssets.configuredWebView(frame: bounds, documentStartScripts: scripts)
        web.navigationDelegate = self
        web.isHidden = true
        insertSubview(web, at: 0)
        self.web = web
        beginLoading()
        if let url = URL(string: JdUrlPolicy.orders) {
            web.load(URLRequest(url: url))
        }
    }

    private func beginLoading() {
        loading = true
        loginVisible = false
        errorMessage = ""
        isBusyError = false
        deadline = CACurrentMediaTime() + 18
        renderBody()
        updatePresentation()
    }

    @objc private func reload() {
        guard let web else { showPage(); return }
        generation += 1
        beginLoading()
        if let url = URL(string: JdUrlPolicy.orders) {
            web.load(URLRequest(url: url))
        }
    }

    @objc private func handleLogout() {
        onRequestLogout?()
    }

    private func sample() {
        guard !paused, let web, !ordersScript.isEmpty else { return }
        if loading && !loginVisible && CACurrentMediaTime() > deadline {
            loading = false
            errorMessage = "读取订单超时，请稍后重试。"
            isBusyError = false
            renderBody()
            updatePresentation()
            checkSettled()
            return
        }
        let url = web.url?.absoluteString
        guard JdUrlPolicy.allowed(url) else { return }
        let expected = generation
        let probe = (JdUrlPolicy.isLoginHost(url) ? loginThemeScript + "\nwindow.HandyCodeJdLoginTheme&&window.HandyCodeJdLoginTheme.setDark(\(dark ? "true" : "false"));\n" : "") + ordersScript
        web.evaluateJavaScript(probe) { [weak self] result, _ in
            guard let self, self.generation == expected, let data = result as? [String: Any] else { return }
            if (data["login"] as? Bool) == true {
                self.setOrderSignedIn(false)
                self.loginVisible = true
                self.loading = false
                self.updatePresentation()
                self.checkSettled()
                return
            }
            let problem = (data["problem"] as? String) ?? ""
            let ready = (data["ready"] as? Bool) ?? false
            guard ready || !problem.isEmpty else { return }
            if !problem.isEmpty {
                self.loading = false
                self.loginVisible = false
                self.isBusyError = (problem == "busy")
                self.errorMessage = self.isBusyError ? "京东订单页面当前繁忙，请稍后点刷新重试。" : "京东官方订单页暂时无法加载，请稍后重试。"
                self.renderBody()
                self.updatePresentation()
                self.checkSettled()
                return
            }
            self.setOrderSignedIn(true)
            self.hasSnapshot = true
            self.orders = (data["orders"] as? [[String: Any]]) ?? []
            self.loading = false
            self.loginVisible = false
            self.errorMessage = ""
            self.isBusyError = false
            self.renderBody()
            self.updatePresentation()
            self.checkSettled()
        }
    }

    private func setOrderSignedIn(_ value: Bool) {
        let was = orderSignedIn
        orderSignedIn = value
        onSessionChanged?(orderSignedIn || identitySignedIn)
        if value && !was {
            identityCard.retryAfterSharedLogin()
        }
    }

    private func updatePresentation() {
        let identityLogin = identityCard.wantsLogin
        let showWeb = web != nil && loginVisible && !identityLogin
        web?.isHidden = !showWeb
        scrollView.isHidden = showWeb || identityLogin
        logoutButton.isHidden = showWeb || identityLogin || !(orderSignedIn || identitySignedIn)
        skeleton.setAnimating(!prewarming && loading && !paused && !showWeb)
        setNeedsLayout()
    }

    private func renderBody() {
        ordersRefresh.setTitle(loading ? "刷新中" : "刷新", for: .normal)
        ordersRefresh.isEnabled = !loading
        ordersRefresh.setTitleColor(loading ? AppTheme.muted(dark) : AppTheme.accentText(dark), for: .normal)
        ordersBody.arrangedSubviews.forEach {
            ordersBody.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        skeleton.isHidden = !loading
        if loading {
            setNeedsLayout()
            return
        }

        let visibleOrders = orders.filter { order in
            let code = (order["code"] as? String) ?? ""
            let status = (order["status"] as? String) ?? ""
            let arrived = (order["arrived"] as? Bool) == true
                || status.contains("待取") || status.contains("待自提") || status.contains("待提货")
            return !code.isEmpty || arrived
        }

        if visibleOrders.isEmpty {
            let empty = UILabel()
            empty.text = hasSnapshot ? "暂无待取包裹" : "取件信息暂未更新"
            empty.font = .systemFont(ofSize: 14)
            empty.textColor = AppTheme.ink(dark)
            empty.textAlignment = .center
            ordersBody.addArrangedSubview(empty)

            let explanation: String
            if isBusyError {
                explanation = "京东订单页面当前繁忙，请稍后点刷新重试。"
            } else if hasSnapshot {
                explanation = errorMessage.isEmpty ? "包裹到站后，可在这里查看取件码。" : "上次查询暂无待取包裹，本次刷新未完成，请稍后重试。"
            } else {
                explanation = errorMessage
            }
            if !explanation.isEmpty {
                let detail = UILabel()
                detail.text = explanation
                detail.font = .systemFont(ofSize: 12)
                detail.textColor = AppTheme.muted(dark)
                detail.textAlignment = .center
                detail.numberOfLines = 0
                ordersBody.addArrangedSubview(detail)
            }
        } else {
            for order in visibleOrders {
                ordersBody.addArrangedSubview(makeParcelRow(order))
            }
        }
        setNeedsLayout()
    }

    private func makeParcelRow(_ order: [String: Any]) -> UIView {
        let box = UIStackView()
        box.axis = .vertical
        box.spacing = 4

        let station = (order["station"] as? String) ?? ""
        let productName = (order["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "京东订单"
        let codeValue = (order["code"] as? String) ?? ""
        let statusValue = (order["status"] as? String) ?? ""
        let imageUrl = (order["image"] as? String) ?? ""
        let orderId = (order["id"] as? String) ?? ""
        let orderLink = (order["link"] as? String) ?? ""

        let stationRow = UIStackView()
        stationRow.axis = .horizontal
        stationRow.alignment = .center
        stationRow.spacing = 8
        let iconView = UIImageView(image: PlatformIcons.stationIcon(color: UIColor(argb: dark ? 0xFFB3B3B3 : 0xFF242B37)))
        iconView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            iconView.widthAnchor.constraint(equalToConstant: 15),
            iconView.heightAnchor.constraint(equalToConstant: 16)
        ])
        let stationLabel = UILabel()
        stationLabel.text = station.isEmpty ? productName : station
        stationLabel.font = .systemFont(ofSize: 13, weight: .medium)
        stationLabel.textColor = AppTheme.ink(dark)
        stationLabel.lineBreakMode = .byTruncatingTail
        stationRow.addArrangedSubview(iconView)
        stationRow.addArrangedSubview(stationLabel)
        box.addArrangedSubview(stationRow)

        let timelineRow = UIStackView()
        timelineRow.axis = .horizontal
        timelineRow.spacing = 15

        let guideContainer = UIView()
        guideContainer.translatesAutoresizingMaskIntoConstraints = false
        let guide = UIView()
        guide.backgroundColor = dark ? UIColor(argb: 0xFF465164) : AppTheme.divider(dark)
        guide.translatesAutoresizingMaskIntoConstraints = false
        guideContainer.addSubview(guide)
        NSLayoutConstraint.activate([
            guideContainer.widthAnchor.constraint(equalToConstant: 9),
            guide.widthAnchor.constraint(equalToConstant: 1),
            guide.trailingAnchor.constraint(equalTo: guideContainer.trailingAnchor),
            guide.topAnchor.constraint(equalTo: guideContainer.topAnchor, constant: 2),
            guide.bottomAnchor.constraint(equalTo: guideContainer.bottomAnchor, constant: -2)
        ])
        timelineRow.addArrangedSubview(guideContainer)

        let innerCol = UIStackView()
        innerCol.axis = .vertical
        innerCol.spacing = 6

        let subLabel = UILabel()
        subLabel.text = station.isEmpty ? statusValue : station
        subLabel.font = .systemFont(ofSize: 11)
        subLabel.textColor = AppTheme.muted(dark)
        innerCol.addArrangedSubview(subLabel)

        let contentRow = UIStackView()
        contentRow.axis = .horizontal
        contentRow.alignment = .center
        contentRow.spacing = 10

        if JdUrlPolicy.allowedImage(imageUrl) {
            let thumb = UIImageView()
            thumb.contentMode = .scaleAspectFill
            thumb.clipsToBounds = true
            thumb.layer.cornerRadius = 6
            thumb.backgroundColor = UIColor(argb: dark ? 0xFF222834 : 0xFFEDF1F7)
            thumb.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                thumb.widthAnchor.constraint(equalToConstant: 58),
                thumb.heightAnchor.constraint(equalToConstant: 58)
            ])
            contentRow.addArrangedSubview(thumb)
            loadThumb(urlString: imageUrl, into: thumb)
        }

        let rightCol = UIStackView()
        rightCol.axis = .vertical
        rightCol.spacing = 3

        let codeLabel = UILabel()
        codeLabel.text = codeValue.isEmpty ? "网页端暂无取件码" : codeValue
        codeLabel.font = .systemFont(ofSize: codeValue.isEmpty ? 19 : 20)
        codeLabel.textColor = AppTheme.ink(dark)
        rightCol.addArrangedSubview(codeLabel)

        let shipLabel = UILabel()
        shipLabel.text = productName
        shipLabel.font = .systemFont(ofSize: 11)
        shipLabel.textColor = AppTheme.muted(dark)
        rightCol.addArrangedSubview(shipLabel)

        if !orderId.isEmpty || !orderLink.isEmpty {
            let linkButton = UIButton(type: .system)
            linkButton.setTitle("在京东 App 查看物流 ›", for: .normal)
            linkButton.titleLabel?.font = .systemFont(ofSize: 11)
            linkButton.setTitleColor(AppTheme.accentText(dark), for: .normal)
            linkButton.contentHorizontalAlignment = .leading
            linkButton.addAction(UIAction { [weak self] _ in
                self?.openJdAppLogistics(orderId: orderId, orderLink: orderLink)
            }, for: .touchUpInside)
            rightCol.addArrangedSubview(linkButton)
        }

        contentRow.addArrangedSubview(rightCol)
        innerCol.addArrangedSubview(contentRow)
        timelineRow.addArrangedSubview(innerCol)
        box.addArrangedSubview(timelineRow)
        return box
    }

    private func loadThumb(urlString: String, into imageView: UIImageView) {
        if let cached = imageCache[urlString] {
            imageView.image = cached
            return
        }
        guard let url = URL(string: urlString) else { return }
        URLSession.shared.dataTask(with: url) { [weak self, weak imageView] data, response, _ in
            guard let self, let imageView,
                  let http = response as? HTTPURLResponse, http.statusCode == 200,
                  let data, let img = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                if self.imageCache.count >= 20 { self.imageCache.removeAll() }
                self.imageCache[urlString] = img
                imageView.image = img
            }
        }.resume()
    }

    private func openJdAppLogistics(orderId: String, orderLink: String) {
        var params: [String: String] = ["category": "jump"]
        if !orderId.isEmpty {
            params["des"] = "orderTrack"
            params["orderId"] = orderId
        } else if JdUrlPolicy.allowed(orderLink) {
            params["des"] = "m"
            params["url"] = orderLink
        } else {
            params["des"] = "orderlist"
        }
        guard let jsonData = try? JSONSerialization.data(withJSONObject: params),
              let jsonStr = String(data: jsonData, encoding: .utf8) else { return }
        var components = URLComponents(string: "openapp.jdmobile://virtual")
        components?.queryItems = [URLQueryItem(name: "params", value: jsonStr)]
        guard let url = components?.url else { return }
        UIApplication.shared.open(url, options: [:])
    }

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let target = navigationAction.request.url?.absoluteString
        decisionHandler(JdUrlPolicy.allowed(target) ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        sample()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        sample()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        loading = false
        loginVisible = false
        errorMessage = "京东页面暂时无法连接，请重试。"
        isBusyError = false
        renderBody()
        updatePresentation()
        checkSettled()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        loading = false
        loginVisible = false
        errorMessage = "京东页面暂时无法连接，请重试。"
        isBusyError = false
        renderBody()
        updatePresentation()
        checkSettled()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let h = bounds.height
        web?.frame = bounds
        scrollView.frame = bounds
        guard w > 40 else { return }

        let cardW = w - 20
        let idH = identityCard.preferredHeight
        identityCard.frame = CGRect(x: 10, y: 12, width: cardW, height: idH)

        ordersHeading.frame = CGRect(x: 0, y: 0, width: cardW, height: 53)
        ordersTitle.frame = CGRect(x: 14, y: 12, width: cardW - 96, height: 29)
        ordersRefresh.frame = CGRect(x: cardW - 78, y: 12, width: 64, height: 29)
        ordersDivider.frame = CGRect(x: 0, y: 53, width: cardW, height: 1)

        let bodyWidth = cardW - 28
        let targetSize = CGSize(width: bodyWidth, height: UIView.layoutFittingCompressedSize.height)
        let measured = loading ? 120 : max(112, ordersBody.systemLayoutSizeFitting(targetSize, withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height)
        ordersBody.frame = CGRect(x: 14, y: 66, width: bodyWidth, height: measured)
        skeleton.frame = CGRect(x: 0, y: 54, width: cardW, height: measured + 26)
        let ordersH = 54 + measured + 26
        ordersCard.frame = CGRect(x: 10, y: identityCard.frame.maxY + 14, width: cardW, height: ordersH)

        logoutButton.frame = CGRect(x: (w - 280) / 2, y: ordersCard.frame.maxY + 16, width: 280, height: 44)
        let bottomMax = logoutButton.isHidden ? ordersCard.frame.maxY : logoutButton.frame.maxY
        scrollView.contentSize = CGSize(width: w, height: max(h, bottomMax + 24))
    }
}
