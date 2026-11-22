import UIKit

final class MainViewController: UIViewController, UIGestureRecognizerDelegate {
    private static let tabNames = ["菜鸟", "淘宝", "拼多多", "京东"]
    private static let tabKeys = ["cainiao", "taobao", "pinduoduo", "jd"]
    private static let keyThemeMode = "theme_mode"
    private static let keyLastTab = "last_tab"
    private static let keyAppBuildStamp = "app_build_stamp"
    private static let keyTaobaoLoginRequired = "taobao_login_required"
    private static let prewarmFallbackDelay: TimeInterval = 2.4
    private static let prewarmStagger: TimeInterval = 0.8

    private let header = UIView()
    private let titleViewport = UIView()
    private var titleRows: [UIView] = []
    private var titleLabels: [UILabel] = []
    private let themeToggle = ThemeToggleButton()
    private let viewport = UIView()
    private let bottomBar = LiquidGlassBar(titles: tabNames)

    private let cainiaoPage = CainiaoPageView()
    private let taobaoPage = TaobaoPageView()
    private let pddPage = PddPageView()
    private let jdPage = JdPageView()
    private lazy var pages: [UIView] = [cainiaoPage, taobaoPage, pddPage, jdPage]

    private var switchMotion: SwitchMotion!
    private var selected = 1
    private var dark = false
    private var opened = [false, false, false, false]
    private var signedIn = [false, false, false, false]

    private var dragStart: CGFloat = 0
    private var pageDragging = false
    private var displayLink: CADisplayLink?
    private var animStartPos: CGFloat = 0
    private var animTargetPos: CGFloat = 0
    private var animStartTime: TimeInterval = 0
    private var animDuration: TimeInterval = 0

    private var savedBrightness: CGFloat?
    private var scanBoosted = false
    private var scanTimer: Timer?
    private var prewarmTimer: Timer?
    private var prewarmArmed = false

    override func viewDidLoad() {
        super.viewDidLoad()
        WebAssets.preload()
        loadInitialPreferences()
        buildUI()
        wireCallbacks()
        applyTheme(animated: false)
        completeSelection(selected)

        NotificationCenter.default.addObserver(self, selector: #selector(appWillResignActive),
                                               name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appDidBecomeActive),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        restoreBrightness()
        scanTimer?.invalidate()
        prewarmTimer?.invalidate()
        displayLink?.invalidate()
    }

    private func loadInitialPreferences() {
        let defaults = UserDefaults.standard
        let reset = ProcessInfo.processInfo.arguments.contains("-ui_test_reset")
        if reset {
            defaults.removeObject(forKey: Self.keyThemeMode)
            defaults.removeObject(forKey: Self.keyLastTab)
            defaults.removeObject(forKey: Self.keyTaobaoLoginRequired)
        } else {
            detectAppUpgrade(defaults)
        }
        let loginRequired = defaults.bool(forKey: Self.keyTaobaoLoginRequired)
        cainiaoPage.setLoginRequired(loginRequired)
        taobaoPage.assumeLoggedOut = loginRequired
        let storedTheme = defaults.integer(forKey: Self.keyThemeMode)
        if storedTheme == AppThemeMode.dark.rawValue {
            dark = true
        } else if storedTheme == AppThemeMode.light.rawValue {
            dark = false
        } else {
            dark = AppTheme.systemDefault(for: traitCollection) == .dark
        }
        if let savedTab = defaults.string(forKey: Self.keyLastTab),
           let idx = Self.tabKeys.firstIndex(of: savedTab) {
            selected = idx
        } else {
            selected = 1
        }
        switchMotion = SwitchMotion(initial: selected, pageCount: pages.count)
    }

    private func detectAppUpgrade(_ defaults: UserDefaults) {
        let info = Bundle.main.infoDictionary
        let shortVer = (info?["CFBundleShortVersionString"] as? String) ?? ""
        let buildVer = (info?["CFBundleVersion"] as? String) ?? ""
        var mtime = 0
        if let execPath = Bundle.main.executablePath,
           let attrs = try? FileManager.default.attributesOfItem(atPath: execPath),
           let modDate = attrs[.modificationDate] as? Date {
            mtime = Int(modDate.timeIntervalSince1970)
        }
        let currentStamp = "\(shortVer)-\(buildVer)-\(mtime)"
        if let prevStamp = defaults.string(forKey: Self.keyAppBuildStamp), prevStamp != currentStamp {
            SiteDataClearer.shared.clearHttpCache()
            taobaoPage.markUpgradeFreshLoad()
            defaults.set(false, forKey: Self.keyTaobaoLoginRequired)
        }
        defaults.set(currentStamp, forKey: Self.keyAppBuildStamp)
    }

    private func buildUI() {
        view.addSubview(header)
        titleViewport.clipsToBounds = true
        header.addSubview(titleViewport)

        for (i, name) in Self.tabNames.enumerated() {
            let row = UIView()
            let icon = UIImageView(image: PlatformIcons.icon(for: i))
            icon.contentMode = .scaleAspectFit
            icon.layer.cornerRadius = 6
            icon.clipsToBounds = true
            row.addSubview(icon)
            icon.frame = CGRect(x: 0, y: 6, width: 28, height: 28)

            let label = UILabel()
            label.text = name
            label.font = .systemFont(ofSize: 20, weight: .semibold)
            label.frame = CGRect(x: 38, y: 0, width: 160, height: 40)
            row.addSubview(label)

            titleViewport.addSubview(row)
            titleRows.append(row)
            titleLabels.append(label)
        }

        themeToggle.onToggle = { [weak self] nextDark in
            guard let self else { return }
            self.dark = nextDark
            UserDefaults.standard.set(nextDark ? AppThemeMode.dark.rawValue : AppThemeMode.light.rawValue,
                                      forKey: Self.keyThemeMode)
            self.applyTheme(animated: true)
        }
        header.addSubview(themeToggle)

        viewport.clipsToBounds = true
        view.addSubview(viewport)
        for page in pages {
            page.isHidden = true
            viewport.addSubview(page)
        }

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePagePan(_:)))
        pan.delegate = self
        viewport.addGestureRecognizer(pan)

        bottomBar.onSelect = { [weak self] index in
            self?.selectPage(index)
        }
        bottomBar.onScrubBegan = { [weak self] in
            guard let self else { return }
            self.cancelAnimation()
            self.pageDragging = true
            self.dragStart = self.switchMotion.position
        }
        bottomBar.onScrubChanged = { [weak self] delta in
            guard let self, self.pageDragging else { return }
            self.switchMotion.scrub(start: self.dragStart, delta: delta, stepWidth: self.bottomBar.slotSpan)
            self.renderSwitchPosition()
        }
        bottomBar.onScrubEnded = { [weak self] vx, dx, cancelled in
            guard let self, self.pageDragging else { return }
            self.pageDragging = false
            let target = self.switchMotion.target(cancelled: cancelled, velocityPt: -vx, distancePt: -dx)
            self.animateSwitch(to: target)
        }
        view.addSubview(bottomBar)
    }

    private func setTaobaoSignedIn(_ signed: Bool) {
        signedIn[1] = signed
        let required = !signed
        if cainiaoPage.isLoginRequired != required {
            UserDefaults.standard.set(required, forKey: Self.keyTaobaoLoginRequired)
            cainiaoPage.setLoginRequired(required)
        }
        taobaoPage.assumeLoggedOut = required
    }

    private func wireCallbacks() {
        cainiaoPage.onOpenTaobao = { [weak self] in
            self?.selectPage(1)
        }
        cainiaoPage.onLoginRequired = { [weak self] in
            self?.setTaobaoSignedIn(false)
        }
        cainiaoPage.onSettled = { [weak self] in
            self?.onTabSettled(0)
        }
        taobaoPage.onSessionChanged = { [weak self] signed in
            self?.setTaobaoSignedIn(signed)
        }
        taobaoPage.onSettled = { [weak self] in
            self?.onTabSettled(1)
        }
        taobaoPage.onRequestLogout = { [weak self] in
            self?.confirmLogout(page: 1)
        }
        taobaoPage.onRequestResetSite = { [weak self] in
            self?.confirmResetTaobaoSiteData()
        }
        taobaoPage.onOpenTaobaoApp = {
            if let url = URL(string: UrlPolicy.taobaoEntry) {
                UIApplication.shared.open(url, options: [:])
            }
        }
        pddPage.onSessionChanged = { [weak self] signed in
            self?.signedIn[2] = signed
        }
        pddPage.onSettled = { [weak self] in
            self?.onTabSettled(2)
        }
        pddPage.onRequestLogout = { [weak self] in
            self?.confirmLogout(page: 2)
        }
        pddPage.onOpenClient = {
            if let url = URL(string: UrlPolicy.pddIdentityAppLink) {
                UIApplication.shared.open(url, options: [:])
            }
        }
        jdPage.onSessionChanged = { [weak self] signed in
            self?.signedIn[3] = signed
        }
        jdPage.onSettled = { [weak self] in
            self?.onTabSettled(3)
        }
        jdPage.onRequestLogout = { [weak self] in
            self?.confirmLogout(page: 3)
        }
    }

    private func applyTheme(animated: Bool) {
        view.backgroundColor = AppTheme.bg(dark)
        header.backgroundColor = AppTheme.bg(dark)
        for label in titleLabels { label.textColor = AppTheme.ink(dark) }
        themeToggle.setDark(dark, animated: animated)
        bottomBar.setDark(dark)
        cainiaoPage.setDark(dark)
        taobaoPage.setDark(dark)
        pddPage.setDark(dark)
        jdPage.setDark(dark)
        setNeedsStatusBarAppearanceUpdate()
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        dark ? .lightContent : .darkContent
    }

    func selectPage(_ index: Int) {
        guard !SiteDataClearer.shared.isBusy else { return }
        let target = max(0, min(pages.count - 1, index))
        animateSwitch(to: target)
    }

    private func cancelAnimation() {
        displayLink?.invalidate()
        displayLink = nil
    }

    private func animateSwitch(to target: Int) {
        cancelAnimation()
        pageDragging = false
        if abs(switchMotion.position - CGFloat(target)) < 0.001 {
            completeSelection(target)
            return
        }
        animStartPos = switchMotion.position
        animTargetPos = CGFloat(target)
        animStartTime = CACurrentMediaTime()
        animDuration = SwitchMotion.duration(distance: animTargetPos - animStartPos)
        let link = CADisplayLink(target: self, selector: #selector(stepAnimation))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func stepAnimation() {
        let elapsed = CACurrentMediaTime() - animStartTime
        let progress = min(1.0, elapsed / max(0.01, animDuration))
        let eased = 1.0 - pow(1.0 - progress, 3.0)
        switchMotion.setPosition(animStartPos + (animTargetPos - animStartPos) * CGFloat(eased))
        renderSwitchPosition()
        if progress >= 1.0 {
            cancelAnimation()
            completeSelection(Int(round(animTargetPos)))
        }
    }

    private func renderSwitchPosition() {
        let pos = switchMotion.position
        let moving = pageDragging || displayLink != nil || abs(pos - round(pos)) > 0.001
        let width = viewport.bounds.width
        let titleH = max(40, titleViewport.bounds.height)
        let nearest = Int(round(pos))

        for (i, page) in pages.enumerated() {
            let offsetX = switchMotion.offset(page: i, extent: width)
            page.frame = CGRect(x: offsetX, y: 0, width: width, height: viewport.bounds.height)
            let visible = opened[i] || (moving && width > 0 && abs(CGFloat(i) - pos) <= 1.25) || i == nearest
            page.isHidden = !visible

            let titleY = switchMotion.offset(page: i, extent: titleH)
            titleRows[i].frame = CGRect(x: 0, y: titleY, width: titleViewport.bounds.width, height: titleH)
            titleRows[i].alpha = max(0, 1 - abs(CGFloat(i) - pos))
        }
        bottomBar.setPosition(pos, selected: selected)
        if moving { restoreBrightness() }
    }

    private func completeSelection(_ target: Int) {
        selected = target
        UserDefaults.standard.set(Self.tabKeys[target], forKey: Self.keyLastTab)
        switchMotion.commit(target)
        renderSwitchPosition()
        view.endEditing(true)

        if selected != 0 { cainiaoPage.pausePage() }
        if selected != 1 { taobaoPage.pausePage() }
        if selected != 2 { pddPage.idlePage() }
        if selected != 3 { jdPage.pausePage() }

        opened[selected] = true
        switch selected {
        case 0: cainiaoPage.showPage()
        case 1: taobaoPage.showPage()
        case 2: pddPage.showPage()
        case 3: jdPage.showPage()
        default: break
        }
        armPrewarmFallback()
        scheduleScanCheck()
    }

    private func armPrewarmFallback() {
        guard ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil else { return }
        guard !prewarmArmed, !SiteDataClearer.shared.isBusy else { return }
        prewarmArmed = true
        prewarmTimer?.invalidate()
        prewarmTimer = Timer.scheduledTimer(withTimeInterval: Self.prewarmFallbackDelay, repeats: false) { [weak self] _ in
            self?.prewarmNextTab()
        }
    }

    private func onTabSettled(_ tab: Int) {
        guard ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil else { return }
        guard tab == selected, !SiteDataClearer.shared.isBusy,
              UIApplication.shared.applicationState != .background else { return }
        prewarmArmed = true
        prewarmTimer?.invalidate()
        prewarmTimer = Timer.scheduledTimer(withTimeInterval: 0.15, repeats: false) { [weak self] _ in
            self?.prewarmNextTab()
        }
    }

    private func prewarmNextTab() {
        guard !SiteDataClearer.shared.isBusy,
              UIApplication.shared.applicationState != .background else { return }
        for tab in [1, 2, 0, 3] {
            if tab == selected || opened[tab] { continue }
            if (tab == 0 || tab == 1) && cainiaoPage.isLoginRequired { continue }
            opened[tab] = true
            renderSwitchPosition()
            switch tab {
            case 0: cainiaoPage.prewarmPage()
            case 1: taobaoPage.prewarmPage()
            case 2: pddPage.prewarmPage()
            case 3: jdPage.prewarmPage()
            default: break
            }
            prewarmTimer?.invalidate()
            prewarmTimer = Timer.scheduledTimer(withTimeInterval: Self.prewarmStagger, repeats: false) { [weak self] _ in
                self?.prewarmNextTab()
            }
            return
        }
    }

    private func confirmLogout(page: Int) {
        let name = Self.tabNames[page]
        let alert = UIAlertController(title: "退出\(name)登录？",
                                      message: "只清除当前平台在应用内的登录态，不影响其他平台。",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "退出登录", style: .destructive) { [weak self] _ in
            guard let self else { return }
            self.prewarmTimer?.invalidate()
            self.prewarmArmed = false
            self.opened[page] = false
            self.signedIn[page] = false
            SiteDataClearer.shared.clear(page: page) {
                if page == 1 {
                    self.opened[0] = false
                    self.setTaobaoSignedIn(false)
                    self.taobaoPage.resetSessionView()
                    if self.selected == 1 {
                        self.opened[1] = true
                        self.taobaoPage.showPage()
                    }
                } else if page == 2 {
                    self.pddPage.resetSessionView()
                    if self.selected == 2 {
                        self.opened[2] = true
                        self.pddPage.showPage()
                    }
                } else if page == 3 {
                    self.jdPage.resetSessionView()
                    if self.selected == 3 {
                        self.opened[3] = true
                        self.jdPage.showPage()
                    }
                }
            }
        })
        present(alert, animated: true)
    }

    private func confirmResetTaobaoSiteData() {
        guard !SiteDataClearer.shared.isBusy else { return }
        let alert = UIAlertController(
            title: "重置淘宝登录态？",
            message: "只清除应用内淘宝和菜鸟的站点缓存与登录态，不影响拼多多和京东。清除后将自动重新加载淘宝页面。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "清除并重试", style: .destructive) { [weak self] _ in
            guard let self else { return }
            self.prewarmTimer?.invalidate()
            self.prewarmArmed = false
            self.opened[1] = false
            self.opened[0] = false
            self.setTaobaoSignedIn(false)
            self.taobaoPage.markUpgradeFreshLoad()
            SiteDataClearer.shared.clear(page: 1) {
                self.taobaoPage.resetSessionView()
                if self.selected == 1 {
                    self.opened[1] = true
                    self.taobaoPage.showPage()
                }
            }
        })
        present(alert, animated: true)
    }

    private func scheduleScanCheck() {
        scanTimer?.invalidate()
        scanTimer = Timer.scheduledTimer(withTimeInterval: 0.7, repeats: true) { [weak self] _ in
            self?.pollScanBrightness()
        }
    }

    private func pollScanBrightness() {
        guard UIApplication.shared.applicationState == .active,
              !pageDragging, displayLink == nil else {
            restoreBrightness()
            return
        }
        let completion: (Bool) -> Void = { [weak self] visible in
            guard let self else { return }
            if visible { self.boostBrightness() }
            else { self.restoreBrightness() }
        }
        switch selected {
        case 0: cainiaoPage.checkVisibleScanCode(completion)
        case 1: taobaoPage.checkVisibleScanCode(completion)
        case 2: pddPage.checkVisibleScanCode(completion)
        case 3: jdPage.checkVisibleScanCode(completion)
        default: restoreBrightness()
        }
    }

    private func boostBrightness() {
        guard !scanBoosted else { return }
        savedBrightness = UIScreen.main.brightness
        scanBoosted = true
        if UIScreen.main.brightness < 0.85 {
            UIScreen.main.brightness = 0.85
        }
    }

    private func restoreBrightness() {
        guard scanBoosted else { return }
        if let saved = savedBrightness {
            UIScreen.main.brightness = saved
        }
        savedBrightness = nil
        scanBoosted = false
    }

    @objc private func appWillResignActive() {
        restoreBrightness()
        prewarmTimer?.invalidate()
        cainiaoPage.pausePage()
        taobaoPage.pausePage()
        pddPage.idlePage()
        jdPage.pausePage()
    }

    @objc private func appDidBecomeActive() {
        if !SiteDataClearer.shared.isBusy {
            switch selected {
            case 0: cainiaoPage.showPage()
            case 1: taobaoPage.showPage()
            case 2: pddPage.showPage()
            case 3: jdPage.showPage()
            default: break
            }
            if !prewarmArmed { armPrewarmFallback() }
            else {
                prewarmTimer?.invalidate()
                prewarmTimer = Timer.scheduledTimer(withTimeInterval: Self.prewarmStagger, repeats: false) { [weak self] _ in
                    self?.prewarmNextTab()
                }
            }
        }
        pollScanBrightness()
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return true }
        let v = pan.velocity(in: viewport)
        return abs(v.x) > abs(v.y) * 1.3
    }

    @objc private func handlePagePan(_ pan: UIPanGestureRecognizer) {
        switch pan.state {
        case .began:
            cancelAnimation()
            pageDragging = true
            dragStart = switchMotion.position
        case .changed:
            let delta = pan.translation(in: viewport).x
            switchMotion.drag(start: dragStart, delta: delta, width: viewport.bounds.width)
            renderSwitchPosition()
        case .ended, .cancelled:
            pageDragging = false
            let vx = pan.velocity(in: viewport).x
            let dx = pan.translation(in: viewport).x
            let target = switchMotion.target(cancelled: pan.state == .cancelled, velocityPt: vx, distancePt: dx)
            animateSwitch(to: target)
        default:
            break
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let safe = view.safeAreaInsets
        let w = view.bounds.width
        let h = view.bounds.height
        let headerH: CGFloat = 56
        let barH: CGFloat = 64

        header.frame = CGRect(x: 0, y: safe.top, width: w, height: headerH)
        titleViewport.frame = CGRect(x: 16, y: 8, width: w - 112, height: 40)
        themeToggle.frame = CGRect(x: w - 86, y: 6, width: 74, height: 44)
        bottomBar.frame = CGRect(x: 0, y: h - safe.bottom - barH, width: w, height: barH)
        viewport.frame = CGRect(x: 0, y: header.frame.maxY, width: w, height: max(120, bottomBar.frame.minY - header.frame.maxY))
        renderSwitchPosition()
    }
}
