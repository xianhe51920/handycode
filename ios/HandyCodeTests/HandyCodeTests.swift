import XCTest
import WebKit
@testable import HandyCode

final class HandyCodeTests: XCTestCase {
    func testUrlPolicyTaobaoAndPddAllowlists() {
        XCTAssertTrue(UrlPolicy.isAllowed(UrlPolicy.taobaoEntry))
        XCTAssertTrue(UrlPolicy.isAllowed(UrlPolicy.cainiaoEntry))
        XCTAssertTrue(UrlPolicy.isAllowed("https://login.taobao.com/member/login.jhtml"))
        XCTAssertFalse(UrlPolicy.isAllowed("http://pages-fast.m.taobao.com/"))
        XCTAssertFalse(UrlPolicy.isAllowed("https://taobao.com.evil.invalid/"))
        XCTAssertFalse(UrlPolicy.isAllowed("javascript:alert(1)"))

        XCTAssertTrue(UrlPolicy.isPddAllowed(UrlPolicy.pddEntry))
        XCTAssertTrue(UrlPolicy.isPddPackagePage(UrlPolicy.pddEntry))
        XCTAssertTrue(UrlPolicy.isPddAddPackagePage("https://m.pinduoduo.net/mdkd/add-package"))
        XCTAssertTrue(UrlPolicy.isPddPhonePage("https://m.pinduoduo.net/mdkd/cellPhoneList"))
        XCTAssertTrue(UrlPolicy.isPddLoginPage("https://m.pinduoduo.net/login.html"))
        XCTAssertTrue(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/framework-4556c45dd113b893.js"))
        XCTAssertTrue(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/css/9f1e2d3c4b5a6789.css"))
        XCTAssertTrue(UrlPolicy.isPddNumericChunk("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/8173-914a2baa3f71f059.js"))
        XCTAssertTrue(UrlPolicy.isPddNumericChunk("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/5685-81ae63372e8a5082.js"))
        XCTAssertFalse(UrlPolicy.isPddNumericChunk("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/framework-4556c45dd113b893.js"))
        XCTAssertFalse(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/_app-21fd7eb3eb3caac5.js"))
        XCTAssertFalse(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/1235-91966cd87947ff3c.js"))
        XCTAssertFalse(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/8173-914a2baa3f71f059.js"))
        XCTAssertFalse(UrlPolicy.isPddImmutableStaticAsset("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/5685-81ae63372e8a5082.js"))
        XCTAssertFalse(UrlPolicy.isPddAllowed("https://api.pinduoduo.com/api/test"))
        XCTAssertFalse(UrlPolicy.isPddAllowed("https://m.pinduoduo.net.evil.invalid/"))

        XCTAssertTrue(UrlPolicy.opensPddIdentity(
            current: "https://m.pinduoduo.net/login.html",
            target: UrlPolicy.pddIdentityAppLink,
            mainFrame: true,
            gesture: true
        ))
        XCTAssertFalse(UrlPolicy.opensPddIdentity(
            current: UrlPolicy.pddEntry,
            target: UrlPolicy.pddIdentityAppLink,
            mainFrame: true,
            gesture: true
        ))

        XCTAssertTrue(UrlPolicy.isTaobaoIdentityEntry(UrlPolicy.taobaoEntry))
        XCTAssertTrue(UrlPolicy.isTaobaoIdentityEntry(UrlPolicy.taobaoEntry + "/"))
        XCTAssertTrue(UrlPolicy.isTaobaoIdentityEntry(UrlPolicy.taobaoEntry + "?spm=test"))
        XCTAssertTrue(UrlPolicy.isTaobaoIdentityEntry(
            UrlPolicy.taobaoEntry.replacingOccurrences(of: "pages-fast.m.taobao.com", with: "pages.m.taobao.com")
        ))
        XCTAssertFalse(UrlPolicy.isTaobaoIdentityEntry(UrlPolicy.taobaoParcelsEntry))
        XCTAssertFalse(UrlPolicy.isTaobaoIdentityEntry("https://login.m.taobao.com/login.htm"))
        XCTAssertFalse(UrlPolicy.isTaobaoIdentityEntry("https://pages-fast.m.taobao.com.evil.invalid/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"))

        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://login.m.taobao.com/login.htm"))
        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://passport.taobao.com/ac/password_find.htm"))
        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://login.tmall.com/"))
        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://aq.taobao.com/durex/validate"))
        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://sec.taobao.com/query.htm"))
        XCTAssertTrue(UrlPolicy.isTaobaoAuthUrl("https://h5api.m.taobao.com/h5/mtop.test/1.0/_____tmd_____/punish"))
        XCTAssertFalse(UrlPolicy.isTaobaoAuthUrl(UrlPolicy.taobaoEntry))
        XCTAssertFalse(UrlPolicy.isTaobaoAuthUrl(UrlPolicy.taobaoParcelsEntry))
        XCTAssertFalse(UrlPolicy.isTaobaoAuthUrl("https://login.taobao.com.evil.invalid/"))
    }

    func testSiteDataClearerClearsHttpCacheWithoutError() {
        let exp = expectation(description: "clearHttpCache finishes on main queue")
        SiteDataClearer.shared.clearHttpCache {
            XCTAssertTrue(Thread.isMainThread)
            exp.fulfill()
        }
        wait(for: [exp], timeout: 10.0)
    }

    func testJdUrlPolicyAndImageAllowlist() {
        XCTAssertTrue(JdUrlPolicy.allowed(JdUrlPolicy.orders))
        XCTAssertTrue(JdUrlPolicy.allowed(JdUrlPolicy.identity))
        XCTAssertTrue(JdUrlPolicy.isIdentity(JdUrlPolicy.identity))
        XCTAssertTrue(JdUrlPolicy.isLoginHost(JdUrlPolicy.login))
        XCTAssertFalse(JdUrlPolicy.allowed("http://trade.m.jd.com/order/orderlist_jdm.shtml"))
        XCTAssertFalse(JdUrlPolicy.allowed("https://jd.com.evil.invalid/"))
        XCTAssertFalse(JdUrlPolicy.isIdentity("https://convenient-h5.jd.com/other"))

        XCTAssertTrue(JdUrlPolicy.allowedImage("https://img10.360buyimg.com/n2/jfs/t1/test.jpg"))
        XCTAssertFalse(JdUrlPolicy.allowedImage("http://img10.360buyimg.com/n2/jfs/t1/test.jpg"))
        XCTAssertFalse(JdUrlPolicy.allowedImage("https://evil.invalid/test.jpg"))
    }

    func testLogoutSitesIsolation() {
        XCTAssertTrue(LogoutSites.matches(host: "login.taobao.com", domains: LogoutSites.domains(for: 1)))
        XCTAssertTrue(LogoutSites.matches(host: "page.cainiao.com", domains: LogoutSites.domains(for: 1)))
        XCTAssertFalse(LogoutSites.matches(host: "m.pinduoduo.net", domains: LogoutSites.domains(for: 1)))
        XCTAssertTrue(LogoutSites.matches(host: "m.pinduoduo.net", domains: LogoutSites.domains(for: 2)))
        XCTAssertFalse(LogoutSites.matches(host: "jd.com", domains: LogoutSites.domains(for: 2)))
        XCTAssertTrue(LogoutSites.matches(host: "trade.m.jd.com", domains: LogoutSites.domains(for: 3)))
    }

    func testSwitchMotionThresholdAndVelocity() {
        let motion = SwitchMotion(initial: 1, pageCount: 4)
        motion.drag(start: 1, delta: -100, width: 400)
        XCTAssertEqual(motion.target(cancelled: false), 1)
        motion.drag(start: 1, delta: -210, width: 400)
        XCTAssertEqual(motion.target(cancelled: false), 2)
        XCTAssertEqual(motion.target(cancelled: true), 1)

        motion.drag(start: 1, delta: -40, width: 400)
        XCTAssertEqual(motion.target(cancelled: false, velocityPt: -700, distancePt: -40), 2)
        motion.drag(start: 1, delta: 40, width: 400)
        XCTAssertEqual(motion.target(cancelled: false, velocityPt: 700, distancePt: 40), 0)
    }

    func testBundledWebAssetsExist() {
        for file in [
            "taobao.html", "official-identity.js", "refresh-controller.js",
            "pickup-motion.js", "login-ready.js", "taobao-login-chrome.js",
            "taobao-login-theme.js", "phone-theme.js", "cainiao-layout.js",
            "pdd-same-origin.js", "pdd-section-refresh.js", "pdd-layout.js",
            "pdd-login-client.js", "pdd-login-notice.js", "login-agreements.js",
            "jd-identity.js", "jd-login-theme.js", "jd-orders.js",
            "glass-theme-switch.js"
        ] {
            let text = WebAssets.readText(file)
            XCTAssertNotNil(text, "Missing bundled asset: \(file)")
            XCTAssertFalse(text?.isEmpty ?? true, "Empty bundled asset: \(file)")
        }
        XCTAssertNotNil(PlatformIcons.themeImage(dark: false))
        XCTAssertNotNil(PlatformIcons.themeImage(dark: true))
        XCTAssertNotNil(PlatformIcons.icon(for: 0))
        XCTAssertNotNil(PlatformIcons.icon(for: 1))
        XCTAssertNotNil(PlatformIcons.icon(for: 2))
        XCTAssertNotNil(PlatformIcons.icon(for: 3))
    }

    func testWKWebViewExecutesBundledScriptsAndConsoleBridge() {
        let exp = expectation(description: "WKWebView bridge executes")
        let bridgeSpy = ConsoleBridgeSpy { message in
            if message == "__HANDYCODE_PHASE__:test" {
                exp.fulfill()
            }
        }
        let navSpy = NavigationSpy()
        let web = WebAssets.configuredWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 480), messageHandler: bridgeSpy)
        let hostWin = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first }
            .first
        hostWin?.addSubview(web)
        defer { web.removeFromSuperview() }
        web.navigationDelegate = navSpy
        navSpy.onFinish = {
            web.evaluateJavaScript(ScanCodeVisibility.script) { result, error in
                XCTAssertNil(error)
                XCTAssertEqual(result as? Bool, true)
                web.evaluateJavaScript("console.log('__HANDYCODE_PHASE__:test');", completionHandler: nil)
            }
        }
        navSpy.onFail = { error in
            XCTFail("WKWebView navigation failed: \(error.localizedDescription)")
        }
        web.loadHTMLString("<html><body><div id='identity-content'><div data-hc-barcode style='width:200px;height:60px'></div></div></body></html>",
                           baseURL: URL(string: "https://handycode.invalid/"))
        withExtendedLifetime((web, bridgeSpy, navSpy)) {
            wait(for: [exp], timeout: 35.0)
        }
    }

    func testJdOrdersScriptExtractsFixtureParcelInWKWebView() {
        let exp = expectation(description: "jd-orders.js parses fixture orders in WKWebView")
        let jdOrdersScript = WebAssets.readText("jd-orders.js") ?? ""
        XCTAssertFalse(jdOrdersScript.isEmpty)
        let navSpy = NavigationSpy()
        let web = WebAssets.configuredWebView(frame: CGRect(x: 0, y: 0, width: 375, height: 667),
                                              documentStartScripts: [jdOrdersScript])
        let hostWin = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first }
            .first
        hostWin?.addSubview(web)
        defer { web.removeFromSuperview() }
        web.navigationDelegate = navSpy
        navSpy.onFail = { error in
            XCTFail("JD fixture navigation failed: \(error.localizedDescription)")
        }
        let html = """
        <html><body>
        <ul>
          <li class="order-item">
            <a href="https://trade.m.jd.com/order/detail?orderId=9001">测试商品包裹</a>
            <span>【菜鸟驿站测试点】取件码：A12-345</span>
          </li>
        </ul>
        </body></html>
        """
        navSpy.onFinish = {
            web.evaluateJavaScript(jdOrdersScript) { result, error in
                XCTAssertNil(error)
                let dict = result as? [String: Any]
                XCTAssertEqual(dict?["login"] as? Bool, false)
                XCTAssertEqual(dict?["ready"] as? Bool, true)
                let orders = dict?["orders"] as? [[String: Any]]
                XCTAssertEqual(orders?.count, 1)
                XCTAssertEqual(orders?.first?["id"] as? String, "9001")
                XCTAssertEqual(orders?.first?["code"] as? String, "A12-345")
                exp.fulfill()
            }
        }
        web.loadHTMLString(html, baseURL: URL(string: "https://trade.m.jd.com/order/orderlist_jdm.shtml"))
        withExtendedLifetime((web, navSpy)) {
            wait(for: [exp], timeout: 35.0)
        }
    }

    func testPddSameOriginAndRefreshFallbackInWKWebView() {
        let exp = expectation(description: "PDD dynamic chunk hooks and refresh fallback execute in WKWebView")
        let refreshScript = WebAssets.readText("pdd-section-refresh.js") ?? ""
        let sameOriginScript = WebAssets.readText("pdd-same-origin.js") ?? ""
        XCTAssertFalse(refreshScript.isEmpty)
        XCTAssertFalse(sameOriginScript.isEmpty)
        let bridgeSpy = ConsoleBridgeSpy { message in
            if message == "__HANDYCODE_PDD_TEST__:PASS" {
                exp.fulfill()
            } else if message.hasPrefix("__HANDYCODE_PDD_TEST__:FAIL:") {
                XCTFail(message)
                exp.fulfill()
            }
        }
        let navSpy = NavigationSpy()
        let web = WebAssets.configuredWebView(
            frame: CGRect(x: 0, y: 0, width: 375, height: 667),
            messageHandler: bridgeSpy
        )
        let hostWin = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.windows.first }
            .first
        hostWin?.addSubview(web)
        defer { web.removeFromSuperview() }
        web.navigationDelegate = navSpy
        navSpy.onFail = { error in
            XCTFail("PDD fixture navigation failed: \(error.localizedDescription)")
            exp.fulfill()
        }
        navSpy.onFinish = {
            let js = """
            \(sameOriginScript)
            \(refreshScript)
            (function(){
              try {
                var calls = [];
                var mods = {
                  82469: function(m, e, r) {
                    r.d(e, { By: function(){ return function(){ return Promise.resolve(null); }; }, J7: function(){ return function(){ return { locationId: undefined, getLocationId: function(){ return Promise.resolve(null); } }; }; } });
                  },
                  96133: function(m, e, r) {
                    var BACKEND_API = 'https://api.pinduoduo.com', isApiRequest = true;
                    r.d(e, { Z: function(){ return {
                      get: function(){ return Promise.resolve({ success: true, result: { longitude: '104.7', latitude: '28.9' } }); },
                      post: function(url, data){
                        calls.push({ url: url, data: data, tag: BACKEND_API, ok: isApiRequest });
                        if (url === '/api/ptolemeaus/location/report') return Promise.resolve({ success: true, result: { location_id: 'ios_loc_1' } });
                        return Promise.resolve({ success: true, result: {} });
                      }
                    }; } });
                  },
                  40616: function(m, e, r) { r.d(e, { p: function(){ return function(){}; } }); },
                  17984: function(m, e, r) { r.d(e, { hg: function(){ return function(name, fn){ return function(){ return function(d, g){ return fn(null, { dispatch: d, getState: g }); }; }; }; } }); },
                  12479: function(m, e, r) { r.d(e, { TL: function(){ return function(){ var d = function(a){ return typeof a === 'function' ? a(d, function(){ return {}; }) : a; }; return d; }; } }); }
                };
                var pageMods = {
                  67441: function(m, e, r) {
                    var b = r(40616);
                    var cls = 'identification-code-container';
                    function generateIdentificationCode(p) { return r(96133).Z.post('/api/express_trackbox/generate_identification_code', p); }
                    b.p(function(){ return generateIdentificationCode({ cls: cls }); });
                  },
                  65867: function(m, e, r) {
                    var Et = r(17984), p = r(12479);
                    var Ht = Et.hg('package/refreshPackagePage', function(){ return r(96133).Z.post('/api/express_trackbox/station/package', { pick_up_mode: 1 }); });
                    var d = p.TL();
                    e.init = function(){ return d(Ht()); };
                  }
                };
                window.webpackChunk_N_E.push([[1], mods]);
                window.webpackChunk_N_E.push([[2], pageMods]);
                function req(id) {
                  var out = {}, mod = mods[id] || pageMods[id];
                  var r = function(x){ return req(x); };
                  r.d = function(t, def){ Object.keys(def).forEach(function(k){ Object.defineProperty(t, k, { enumerable: true, get: def[k] }); }); };
                  mod({}, out, r);
                  return out;
                }
                var loc = req(82469), http = req(96133);
                req(67441); req(65867);
                http.Z.post('/api/express_trackbox/dsd/manual/add/package/v2', { station_code: 'ST_IOS', entry_source: '4' })
                  .then(function(){
                    if (loc.J7().locationId !== 'ios_loc_1') throw new Error('location_id mismatch');
                    return Promise.all([window.HandyPddRefresh.run('identity'), window.HandyPddRefresh.run('parcels')]);
                  })
                  .then(function(){
                    var hasId = calls.some(function(c){ return c.url === '/api/express_trackbox/generate_identification_code'; });
                    var hasPa = calls.some(function(c){ return c.url === '/api/express_trackbox/station/package'; });
                    if (!hasId || !hasPa) throw new Error('missing refresh posts');
                    console.log('__HANDYCODE_TEST__:PASS'.replace('TEST', 'PDD_TEST'));
                  })
                  .catch(function(err){ console.log('__HANDYCODE_PDD_TEST__:FAIL:' + (err && err.message ? err.message : err)); });
              } catch (err) {
                console.log('__HANDYCODE_PDD_TEST__:FAIL:' + (err && err.message ? err.message : err));
              }
            })();
            """
            web.evaluateJavaScript(js) { _, error in
                if let error {
                    XCTFail("evaluateJavaScript failed: \(error.localizedDescription)")
                    exp.fulfill()
                }
            }
        }
        web.loadHTMLString("<html><body></body></html>", baseURL: URL(string: "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE"))
        withExtendedLifetime((web, bridgeSpy, navSpy)) {
            wait(for: [exp], timeout: 35.0)
        }
    }

    func testJdPageViewThemeAndLayout() {
        let jd = JdPageView(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        jd.setDark(false)
        jd.layoutIfNeeded()
        jd.setDark(true)
        jd.layoutIfNeeded()
        XCTAssertGreaterThan(jd.subviews.count, 0)
    }

    func testSceneDelegateConfigurationResolvesDelegateClass() {
        let scenes = Bundle.main.object(forInfoDictionaryKey: "UIApplicationSceneManifest") as? [String: Any]
        let configs = scenes?["UISceneConfigurations"] as? [String: Any]
        let appConfigs = configs?["UIWindowSceneSessionRoleApplication"] as? [[String: Any]]
        let className = appConfigs?.first?["UISceneDelegateClassName"] as? String
        XCTAssertNotNil(className)
        XCTAssertNotNil(NSClassFromString(className ?? ""))
    }

    func testMainViewControllerInstantiatesAllFourPages() {
        let prevTab = UserDefaults.standard.string(forKey: "last_tab")
        defer {
            if let prevTab { UserDefaults.standard.set(prevTab, forKey: "last_tab") }
            else { UserDefaults.standard.removeObject(forKey: "last_tab") }
        }
        let vc = MainViewController()
        vc.loadViewIfNeeded()
        vc.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        vc.view.layoutIfNeeded()
        for tab in 0..<4 {
            vc.selectPage(tab)
            vc.view.layoutIfNeeded()
        }
        XCTAssertEqual(vc.view.subviews.count, 3)
    }

    func testPreloadAndPrewarmDoNotCrash() {
        WebAssets.preload()
        let cainiao = CainiaoPageView(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let taobao = TaobaoPageView(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let pdd = PddPageView(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        let jd = JdPageView(frame: CGRect(x: 0, y: 0, width: 390, height: 700))
        cainiao.prewarmPage()
        taobao.prewarmPage()
        pdd.prewarmPage()
        jd.prewarmPage()
        cainiao.pausePage()
        taobao.pausePage()
        pdd.idlePage()
        jd.pausePage()
    }
}

private final class NavigationSpy: NSObject, WKNavigationDelegate {
    var onFinish: (() -> Void)?
    var onFail: ((Error) -> Void)?
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        onFinish?()
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        onFail?(error)
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        onFail?(error)
    }
}

private final class ConsoleBridgeSpy: NSObject, WKScriptMessageHandler {
    private let onMessage: (String) -> Void
    init(_ onMessage: @escaping (String) -> Void) { self.onMessage = onMessage }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if let str = message.body as? String { onMessage(str) }
    }
}
