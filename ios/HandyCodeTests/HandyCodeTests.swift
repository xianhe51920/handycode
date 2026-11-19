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
        XCTAssertTrue(UrlPolicy.isPddLoginPage("https://m.pinduoduo.net/login.html"))
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
        XCTAssertEqual(motion.target(cancelled: false, velocityPt: 700, distancePt: 40), 0)
    }

    func testBundledWebAssetsExist() {
        for file in [
            "taobao.html", "official-identity.js", "refresh-controller.js",
            "pickup-motion.js", "login-ready.js", "taobao-login-chrome.js",
            "taobao-login-theme.js", "phone-theme.js", "cainiao-layout.js",
            "pdd-same-origin.js", "pdd-section-refresh.js", "pdd-layout.js",
            "pdd-login-client.js", "pdd-login-notice.js", "login-agreements.js",
            "jd-identity.js", "jd-login-theme.js", "jd-orders.js"
        ] {
            let text = WebAssets.readText(file)
            XCTAssertNotNil(text, "Missing bundled asset: \(file)")
            XCTAssertFalse(text?.isEmpty ?? true, "Empty bundled asset: \(file)")
        }
    }

    func testWKWebViewExecutesBundledScriptsAndConsoleBridge() {
        let exp = expectation(description: "WKWebView bridge executes")
        let bridgeSpy = ConsoleBridgeSpy { message in
            if message == "__HANDYCODE_PHASE__:test" {
                exp.fulfill()
            }
        }
        let web = WebAssets.configuredWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 480), messageHandler: bridgeSpy)
        web.loadHTMLString("<html><body><div id='identity-content'><div data-hc-barcode style='width:200px;height:60px'></div></div></body></html>",
                           baseURL: URL(string: "https://handycode.invalid/"))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            web.evaluateJavaScript(ScanCodeVisibility.script) { result, error in
                XCTAssertNil(error)
                XCTAssertEqual(result as? Bool, true)
                web.evaluateJavaScript("console.log('__HANDYCODE_PHASE__:test');", completionHandler: nil)
            }
        }
        wait(for: [exp], timeout: 5.0)
    }
}

private final class ConsoleBridgeSpy: NSObject, WKScriptMessageHandler {
    private let onMessage: (String) -> Void
    init(_ onMessage: @escaping (String) -> Void) { self.onMessage = onMessage }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if let str = message.body as? String { onMessage(str) }
    }
}
